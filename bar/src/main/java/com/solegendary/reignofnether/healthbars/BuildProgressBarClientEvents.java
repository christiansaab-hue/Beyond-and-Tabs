package com.solegendary.reignofnether.healthbars;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * BAR-style world-space progress over buildings: a small bar plus "ETA m:ss" above every unfinished construction
 * site, and a bar for the item at the front of a production building's queue. The ETA comes from the rate progress
 * actually advanced over the last few seconds (smoothed), so it accounts for build power, stalls and helpers joining.
 * Drawn with {@link HealthBarClientEvents}' bar renderer so it matches the unit health bars. Hidden in strategic
 * view and beyond normal camera range so a big base stays readable and cheap.
 */
@OnlyIn(Dist.CLIENT)
public class BuildProgressBarClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    private static final int SAMPLE_TICKS = 10;          // progress is sampled twice a second
    private static final float RATE_SMOOTHING = 0.3f;    // EMA weight of the newest sample
    private static final float FIRST_PERSON_RANGE = 48;
    private static final int MAX_BARS = 32;              // per frame cap, in case a huge base is all on screen
    private static final float BAR_WIDTH = 40;

    /** Smoothed progress rate of one building (construction) or of its current production item. */
    private static final class Eta {
        float lastFrac = -1;
        float rate = 0;         // fraction per second
        Object item = null;     // the ActiveProduction being timed (production); resets the estimate when it changes
        int seen = 0;
        String text = "ETA --:--";   // rebuilt at each sample, not every frame
    }

    private static final Map<BuildingPlacement, Eta> BUILD_ETAS = new IdentityHashMap<>();
    private static final Map<BuildingPlacement, Eta> PROD_ETAS = new IdentityHashMap<>();
    private static int tickCounter = 0;

    // ------------------------------------------------------------------ sampling

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.isPaused())
            return;
        if (++tickCounter % SAMPLE_TICKS != 0)
            return;
        int stamp = tickCounter;
        float dt = SAMPLE_TICKS / 20f;
        for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
            if (!b.isBuilt) {
                int total = b.getBlocksTotal();
                if (total > 0)
                    sample(BUILD_ETAS.computeIfAbsent(b, k -> new Eta()), (float) b.getBlocksPlaced() / total, null, dt, stamp);
            } else if (b instanceof ProductionPlacement pp && !pp.productionQueue.isEmpty()) {
                ActiveProduction ap = pp.productionQueue.get(0);
                float frac = productionFrac(pp, ap);
                if (frac >= 0)
                    sample(PROD_ETAS.computeIfAbsent(b, k -> new Eta()), frac, ap, dt, stamp);
            }
        }
        prune(BUILD_ETAS, stamp);
        prune(PROD_ETAS, stamp);
    }

    private static void sample(Eta e, float frac, Object item, float dt, int stamp) {
        e.seen = stamp;
        if (e.item != item || e.lastFrac < 0 || frac < e.lastFrac - 1e-4f) {
            // new item, first sample, or progress went backwards (site damaged): start the estimate over
            e.item = item;
            e.lastFrac = frac;
            e.rate = 0;
            e.text = etaText(frac, 0);
            return;
        }
        float r = (frac - e.lastFrac) / dt;
        e.rate = e.rate <= 0 ? r : e.rate + (r - e.rate) * RATE_SMOOTHING;
        e.lastFrac = frac;
        e.text = etaText(frac, e.rate);
    }

    private static void prune(Map<BuildingPlacement, Eta> map, int stamp) {
        for (Iterator<Map.Entry<BuildingPlacement, Eta>> it = map.entrySet().iterator(); it.hasNext(); )
            if (it.next().getValue().seen != stamp)
                it.remove();
    }

    private static float productionFrac(ProductionPlacement pp, ActiveProduction ap) {
        try {
            float total = ap.item.getCost(true, pp.ownerName).ticks;
            if (total <= 0)
                return -1;
            return Math.max(0, Math.min(1, 1 - ap.ticksLeft / total));
        } catch (Exception e) {
            return -1;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        BUILD_ETAS.clear();
        PROD_ETAS.clear();
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MC.level == null || MC.player == null)
            return;
        if (StrategicViewClientEvents.isStrategicView() || (BUILD_ETAS.isEmpty() && PROD_ETAS.isEmpty()))
            return;
        try {
            render(evt.getPoseStack(), evt.getCamera());
        } catch (Exception ignored) {
            // a cosmetic overlay must never break the frame
        }
    }

    private static void render(PoseStack pose, Camera camera) {
        Vec3 cam = camera.getPosition();
        boolean ortho = OrthoviewClientEvents.isEnabled();
        Vector3f look = camera.getLookVector();
        float zoom = OrthoviewClientEvents.getZoom();
        String me = MC.player.getName().getString();
        HealthBarClientEvents.RenderMode mode = ortho ? HealthBarClientEvents.RenderMode.IN_WORLD_ORTHOVIEW
                : HealthBarClientEvents.RenderMode.IN_WORLD_FIRST_PERSON;
        // bars grow a little with orthoview zoom so they stay legible when zoomed out, without ever getting big
        float scale = ortho ? 0.025f * Math.max(0.8f, Math.min(1.8f, zoom / 30f)) : 0.02f;
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        Font font = MC.font;
        int drawn = 0;

        List<BuildingPlacement> buildings = BuildingClientEvents.getBuildings();
        for (int i = 0; i < buildings.size() && drawn < MAX_BARS; i++) {
            BuildingPlacement b = buildings.get(i);
            Eta build = b.isBuilt ? null : BUILD_ETAS.get(b);
            Eta prod = b.isBuilt ? PROD_ETAS.get(b) : null;
            if (build == null && prod == null)
                continue;
            boolean friendly = AlliancesClient.isAlliedOrOwned(me, b.ownerName);
            if (prod != null && !friendly)
                continue;   // an enemy's production queue is private
            double x = (b.minCorner.getX() + b.maxCorner.getX() + 1) / 2.0;
            double y = b.maxCorner.getY() + 1.6;
            double z = (b.minCorner.getZ() + b.maxCorner.getZ() + 1) / 2.0;
            if (!inRange(x - cam.x, y - cam.y, z - cam.z, ortho, look, zoom))
                continue;
            if (!friendly && FogOfWarClientEvents.isEnabled() && !FogOfWarClientEvents.isInBrightChunk(b.centrePos))
                continue;

            float frac;
            Eta eta;
            float r, g, bl;
            if (build != null) {
                frac = b.getBlocksTotal() > 0 ? (float) b.getBlocksPlaced() / b.getBlocksTotal() : 0;
                eta = build;
                r = 1f; g = 0.78f; bl = 0.25f;   // construction: warm gold
            } else {
                frac = prod.lastFrac;
                ProductionPlacement pp = (ProductionPlacement) b;
                if (!pp.productionQueue.isEmpty()) {
                    float f = productionFrac(pp, pp.productionQueue.get(0));
                    if (f >= 0) frac = f;
                }
                eta = prod;
                r = 0.35f; g = 0.75f; bl = 1f;   // production: cool blue
            }

            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            pose.scale(-scale, -scale, scale);

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            HealthBarClientEvents.renderProgress(pose, frac, 0, 0, BAR_WIDTH, mode, r, g, bl);

            String text = eta.text;
            float tw = font.width(text);
            font.drawInBatch(text, -tw / 2f, -10, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0x60000000, 0xF000F0);
            pose.popPose();
            drawn++;
        }
        if (drawn > 0) {
            buffers.endBatch();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    /** Same notion of "on screen" as the battle effects: near the line of sight in orthoview, a radius otherwise. */
    private static boolean inRange(double dx, double dy, double dz, boolean ortho, Vector3f look, float zoom) {
        if (ortho) {
            double along = dx * look.x() + dy * look.y() + dz * look.z();
            double px = dx - look.x() * along, py = dy - look.y() * along, pz = dz - look.z() * along;
            double r = zoom * 1.2 + 12;
            return px * px + py * py + pz * pz < r * r;
        }
        return dx * dx + dy * dy + dz * dz < FIRST_PERSON_RANGE * FIRST_PERSON_RANGE;
    }

    /** "ETA m:ss" from the smoothed rate, "ETA --:--" while stalled or before a rate is known. */
    static String etaText(float frac, float ratePerSecond) {
        if (ratePerSecond <= 1e-5f || frac >= 1f)
            return "ETA --:--";
        int secs = (int) Math.ceil((1f - frac) / ratePerSecond);
        if (secs > 5999)
            return "ETA --:--";
        int m = secs / 60, s = secs % 60;
        return "ETA " + m + ":" + (s < 10 ? "0" : "") + s;
    }
}
