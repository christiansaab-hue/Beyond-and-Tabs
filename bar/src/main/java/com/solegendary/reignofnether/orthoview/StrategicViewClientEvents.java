package com.solegendary.reignofnether.orthoview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.monsters.Dungeon;
import com.solegendary.reignofnether.building.buildings.monsters.Stronghold;
import com.solegendary.reignofnether.building.buildings.neutral.CapturableBeacon;
import com.solegendary.reignofnether.building.buildings.piglins.FlameSanctuary;
import com.solegendary.reignofnether.building.buildings.piglins.Fortress;
import com.solegendary.reignofnether.building.buildings.shared.AbstractStockpile;
import com.solegendary.reignofnether.building.buildings.shared.EnergyConverter;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.building.buildings.villagers.ArcaneTower;
import com.solegendary.reignofnether.building.buildings.villagers.Castle;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.startpos.CapturePointsClient;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * BAR-style strategic zoom (Beyond and Tabs).
 *
 * Past STRATEGIC_ZOOM_ENTER every visible unit is drawn as a team-coloured icon whose shape shows its role
 * (circle = worker/builder, square = melee, triangle = ranged, diamond = hero/commander) and buildings as
 * team-coloured footprint plates. Unit models (and with them their per-unit health bars, which are drawn from
 * RenderLivingEvent.Post) are skipped, which is what makes huge battles cheap to look at.
 *
 * Each plate also carries a small role glyph (factory, T2/T3 lab, mex with its tier, wind, converter, defence,
 * capitol, capture site) so a base doesn't read as a sheet of identical tiles; see drawGlyph.
 *
 * Also draws, at any zoom: attack range rings for selected units, and team-coloured selection rings under
 * selected units (normal zoom only; the icons replace them in strategic zoom).
 */
public class StrategicViewClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    // icons must take over BEFORE vanilla entity culling starts hiding far mobs, or there's a dead zoom window
    // where units are invisible but icons haven't kicked in yet (the "units going invisible" bug)
    public static final float STRATEGIC_ZOOM_ENTER = 70f;
    public static final float STRATEGIC_ZOOM_EXIT = 62f; // hysteresis so it doesn't flicker at the threshold
    private static final int MAX_RANGE_RINGS = 64;
    private static final int RING_SEGMENTS = 48;

    public static boolean rangeRingsEnabled = true;
    public static boolean selectionPlatesEnabled = true;

    private static boolean strategic = false;

    public static boolean isStrategicView() {
        return strategic;
    }

    private static void updateState() {
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null) {
            strategic = false;
            return;
        }
        float zoom = OrthoviewClientEvents.getZoom();
        if (strategic && zoom < STRATEGIC_ZOOM_EXIT)
            strategic = false;
        else if (!strategic && zoom > STRATEGIC_ZOOM_ENTER)
            strategic = true;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        updateState();
        if (strategic)
            syncHudSelectedEntity();
    }

    // HudClientEvents picks hudSelectedEntity inside RenderLivingEvent.Post, which no longer fires for units whose
    // model we skip, so mirror that logic here every frame while in strategic view.
    private static void syncHudSelectedEntity() {
        LivingEntity hudEntity = HudClientEvents.hudSelectedEntity;
        if (hudEntity != null && hudEntity.isRemoved())
            HudClientEvents.setHudSelectedEntity(null);

        ArrayList<LivingEntity> units = UnitClientEvents.getSortedSelectedUnits();
        if (units.isEmpty()) {
            HudClientEvents.setHudSelectedEntity(null);
        } else if (HudClientEvents.hudSelectedEntity == null || units.size() == 1 ||
                !units.contains(HudClientEvents.hudSelectedEntity)) {
            HudClientEvents.setHudSelectedEntity(units.get(0));
        }
        if (HudClientEvents.hudSelectedEntity == null) {
            HudClientEvents.portraitRendererUnit.model = null;
            HudClientEvents.portraitRendererUnit.renderer = null;
        }
    }

    // skip full unit models in strategic view (the HUD-selected unit still renders so its portrait keeps working)
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<? extends LivingEntity, ? extends EntityModel<?>> evt) {
        if (!strategic)
            return;
        LivingEntity entity = evt.getEntity();
        if (entity instanceof Unit && entity != HudClientEvents.hudSelectedEntity)
            evt.setCanceled(true);
    }

    // ------------------------------------------------------------------------------------------------------------
    // strategic icon overlay (drawn as part of the in-game GUI, so it sits under the RTS HUD)
    // ------------------------------------------------------------------------------------------------------------

    private static Camera cam;
    private static Vec3 camPos;
    private static Vector3f camLeft;
    private static Vector3f camUp;
    private static float guiW;
    private static float guiH;
    private static float pixelsPerBlock;

    private static float projX(double x, double y, double z) {
        double rx = x - camPos.x, ry = y - camPos.y, rz = z - camPos.z;
        double right = -(rx * camLeft.x() + ry * camLeft.y() + rz * camLeft.z());
        return (float) (guiW / 2 + right * pixelsPerBlock);
    }

    private static float projY(double x, double y, double z) {
        double rx = x - camPos.x, ry = y - camPos.y, rz = z - camPos.z;
        double up = rx * camUp.x() + ry * camUp.y() + rz * camUp.z();
        return (float) (guiH / 2 - up * pixelsPerBlock);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre evt) {
        if (!strategic || MC.level == null || MC.player == null || !OrthoviewClientEvents.isEnabled())
            return;
        try {
            drawStrategicOverlay(evt.getGuiGraphics(), evt.getPartialTick());
        } catch (Exception e) {
            // never let an overlay bug take down the frame
            e.printStackTrace();
        }
    }

    /** Commanders are tagged serverside, but tags don't sync - their translatable custom name does. */
    private static boolean isCommander(LivingEntity entity) {
        return entity.hasCustomName()
                && entity.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && "unit.reignofnether.commander".equals(tc.getKey());
    }

    private static int teamColour(LivingEntity entity) {
        if (entity instanceof Unit unit && PlayerClientEvents.isRTSPlayer(unit.getOwnerName()))
            return PlayerColors.getPlayerDisplayColorHex(unit.getOwnerName()) & 0xFFFFFF;
        return PlayerColors.COLOR_GRAY.hexCode & 0xFFFFFF;
    }

    private static void drawStrategicOverlay(GuiGraphics gg, float partialTick) {
        cam = MC.gameRenderer.getMainCamera();
        camPos = cam.getPosition();
        camLeft = cam.getLeftVector();
        camUp = cam.getUpVector();
        guiW = MC.getWindow().getGuiScaledWidth();
        guiH = MC.getWindow().getGuiScaledHeight();
        pixelsPerBlock = guiH / OrthoviewClientEvents.getZoom();

        gg.flush();
        Matrix4f mat = gg.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // ---- buildings: team-coloured footprint plates ----
        List<BuildingPlacement> selectedBuildings = BuildingClientEvents.getSelectedBuildings();
        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            if (!building.isExploredClientside || building.getBuilding() instanceof AbstractBridge)
                continue;
            double y = building.minCorner.getY() + 1;
            double x0 = building.minCorner.getX(), z0 = building.minCorner.getZ();
            double x1 = building.maxCorner.getX() + 1, z1 = building.maxCorner.getZ() + 1;
            float[] xs = { projX(x0, y, z0), projX(x1, y, z0), projX(x1, y, z1), projX(x0, y, z1) };
            float[] ys = { projY(x0, y, z0), projY(x1, y, z0), projY(x1, y, z1), projY(x0, y, z1) };
            if (offScreen(xs, ys))
                continue;
            int rgb = PlayerColors.getPlayerDisplayColorHex(building.ownerName) & 0xFFFFFF;
            if (!FogOfWarClientEvents.isBuildingInBrightChunk(building))
                rgb = darken(rgb, 0.5f);
            boolean selected = selectedBuildings.contains(building);
            quad(bb, mat, xs, ys, (0x70 << 24) | rgb);
            int outline = selected ? 0xF0FFFFFF : (0xD0 << 24) | darken(rgb, 0.45f);
            float w = selected ? 1.5f : 1f;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                line(bb, mat, xs[i], ys[i], xs[j], ys[j], w, outline);
            }
            // role glyph in the middle of the plate, so a base reads as factories / eco / defences at a glance
            Role role = roleOf(building.getBuilding());
            if (role != Role.NONE) {
                float plate = plateSize(xs, ys);
                if (plate >= MIN_GLYPH_PLATE) {
                    float gcx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25f;
                    float gcy = (ys[0] + ys[1] + ys[2] + ys[3]) * 0.25f;
                    float gs = Mth.clamp(plate * 0.3f, 3f, 8f);
                    int tier = role == Role.MEX ? mexTier(building) : 0;
                    drawGlyph(bb, mat, role, tier, gcx, gcy, gs, true, 0);
                    drawGlyph(bb, mat, role, tier, gcx, gcy, gs, false, rgb);
                }
            }
        }

        // ---- capture sites (vanilla marker entities serverside, synced as a list): owner-tinted plate + flag ----
        for (CapturePointsClient.Site site : CapturePointsClient.getSites()) {
            double y = site.y();
            double x0 = site.x() - 2, z0 = site.z() - 2, x1 = site.x() + 3, z1 = site.z() + 3;
            float ax = projX(x0, y, z0), bx = projX(x1, y, z0), cx = projX(x1, y, z1), dx = projX(x0, y, z1);
            float ay = projY(x0, y, z0), by = projY(x1, y, z0), cy = projY(x1, y, z1), dy = projY(x0, y, z1);
            float gcx = (ax + bx + cx + dx) * 0.25f, gcy = (ay + by + cy + dy) * 0.25f;
            if (gcx < -20 || gcx > guiW + 20 || gcy < -20 || gcy > guiH + 20)
                continue;
            int rgb = site.owner().isEmpty() ? 0xB4B4B4 : PlayerColors.getPlayerDisplayColorHex(site.owner()) & 0xFFFFFF;
            int plateCol = (0x60 << 24) | rgb;
            tri(bb, mat, ax, ay, bx, by, cx, cy, plateCol);
            tri(bb, mat, ax, ay, cx, cy, dx, dy, plateCol);
            int edge = (0xD0 << 24) | darken(rgb, 0.45f);
            line(bb, mat, ax, ay, bx, by, 1f, edge);
            line(bb, mat, bx, by, cx, cy, 1f, edge);
            line(bb, mat, cx, cy, dx, dy, 1f, edge);
            line(bb, mat, dx, dy, ax, ay, 1f, edge);
            float plate = Math.min((float) Math.hypot(bx - ax, by - ay), (float) Math.hypot(dx - ax, dy - ay));
            float gs = Mth.clamp(plate * 0.35f, 3.5f, 8f);
            drawGlyph(bb, mat, Role.CAPTURE, 0, gcx, gcy, gs, true, 0);
            drawGlyph(bb, mat, Role.CAPTURE, 0, gcx, gcy, gs, false, rgb);
        }

        // ---- units: role icons ----
        // reused every frame (open-addressing sets: clear() keeps the table, no per-entry nodes), so a big
        // selection doesn't allocate two hash sets and a list on every strategic-view frame
        Set<LivingEntity> selected = SELECTED_SCRATCH;
        Set<LivingEntity> preselected = PRESELECTED_SCRATCH;
        List<LivingEntity> drawLast = DRAW_LAST_SCRATCH;
        selected.clear();
        preselected.clear();
        drawLast.clear();
        selected.addAll(UnitClientEvents.getSelectedUnits());
        preselected.addAll(UnitClientEvents.getPreselectedUnits());
        for (LivingEntity entity : UnitClientEvents.getAllUnits()) {
            if (selected.contains(entity) || preselected.contains(entity))
                drawLast.add(entity);
            else
                drawUnitIcon(bb, mat, entity, partialTick, false, false);
        }
        for (LivingEntity entity : drawLast)
            drawUnitIcon(bb, mat, entity, partialTick, selected.contains(entity), preselected.contains(entity));
        selected.clear();   // don't hold on to entities between frames (or after leaving the world)
        preselected.clear();
        drawLast.clear();

        BufferUploader.drawWithShader(bb.end());

        // building icons on top of their plates, once the plate is big enough to carry one - only for buildings
        // without a role glyph (houses, farms, markets...), which would otherwise be anonymous
        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            if (!building.isExploredClientside || building.getBuilding() instanceof AbstractBridge)
                continue;
            if (roleOf(building.getBuilding()) != Role.NONE)
                continue;
            net.minecraft.resources.ResourceLocation icon = building.getBuilding().icon;
            if (icon == null)
                continue;
            double y = building.minCorner.getY() + 1;
            double cx = (building.minCorner.getX() + building.maxCorner.getX() + 1) / 2.0;
            double cz = (building.minCorner.getZ() + building.maxCorner.getZ() + 1) / 2.0;
            float sx = projX(cx, y, cz);
            float sy = projY(cx, y, cz);
            float plate = Math.min(Math.abs(projX(building.maxCorner.getX() + 1, y, cz) - projX(building.minCorner.getX(), y, cz)),
                                   Math.abs(projY(cx, y, building.maxCorner.getZ() + 1) - projY(cx, y, building.minCorner.getZ())));
            int size = (int) Mth.clamp(plate * 0.7f, 6f, 16f);
            if (plate < 8 || sx < -20 || sx > guiW + 20 || sy < -20 || sy > guiH + 20)
                continue;
            gg.blit(icon, (int) (sx - size / 2f), (int) (sy - size / 2f), size, size, 0, 0, 16, 16, 16, 16);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------------------------------
    // building role glyphs
    // ------------------------------------------------------------------------------------------------------------

    private enum Role { NONE, CAPITOL, FACTORY, LAB2, LAB3, MEX, WIND, CONVERTER, DEFENCE, CAPTURE }

    private static final float MIN_GLYPH_PLATE = 7f;   // px; below this the plate itself is all you can read
    private static final int GLYPH_SHADOW = 0xC8000000;
    private static final int GLYPH_WHITE = 0xF2FFFFFF;

    // a building's role never changes, and Building objects are per-type singletons, so classify once
    private static final IdentityHashMap<Building, Role> roleCache = new IdentityHashMap<>();
    // a mex's tier is read from its blocks (see MetalExtractor.getUpgradeLevel), so cache it and re-read once a second
    private static final IdentityHashMap<BuildingPlacement, Integer> mexTierCache = new IdentityHashMap<>();
    private static long mexTierCacheTime = Long.MIN_VALUE;

    private static Role roleOf(Building b) {
        Role r = roleCache.get(b);
        if (r == null) {
            if (roleCache.size() > 512)   // custom buildings could in theory mint many types; keep it bounded
                roleCache.clear();
            r = classify(b);
            roleCache.put(b, r);
        }
        return r;
    }

    private static Role classify(Building b) {
        if (b.isCapitol)
            return Role.CAPITOL;
        if (b.capturable || b instanceof CapturableBeacon)
            return Role.CAPTURE;
        if (b instanceof MetalExtractor)
            return Role.MEX;
        if (b instanceof WindGenerator)
            return Role.WIND;
        if (b instanceof EnergyConverter)
            return Role.CONVERTER;
        if (b instanceof Castle || b instanceof Stronghold || b instanceof Fortress)   // T3 labs (also garrisonable)
            return Role.LAB3;
        if (b instanceof ArcaneTower || b instanceof Dungeon || b instanceof FlameSanctuary)
            return Role.LAB2;
        if (b instanceof GarrisonableBuildingAddon)   // watchtowers, bastion
            return Role.DEFENCE;
        if (b instanceof ProductionBuilding pb && !(b instanceof AbstractStockpile))
            for (ProductionItem item : pb.productions.get())
                if (item instanceof IUnitProductionItem)
                    return Role.FACTORY;
        return Role.NONE;
    }

    private static int mexTier(BuildingPlacement building) {
        long now = MC.level != null ? MC.level.getGameTime() : 0;
        if (now - mexTierCacheTime >= 20 || now < mexTierCacheTime) {
            mexTierCache.clear();
            mexTierCacheTime = now;
        }
        Integer t = mexTierCache.get(building);
        if (t == null) {
            t = building.getUpgradeLevel() > 0 ? 1 : 0;
            mexTierCache.put(building, t);
        }
        return t;
    }

    private static float plateSize(float[] xs, float[] ys) {
        float a = (float) Math.hypot(xs[1] - xs[0], ys[1] - ys[0]);
        float b = (float) Math.hypot(xs[3] - xs[0], ys[3] - ys[0]);
        return Math.min(a, b);
    }

    /**
     * Draws one role glyph centred on (cx, cy), half-size s. Called twice per building: first as a dark shadow
     * (fatter strokes) and then in colour, which keeps it crisp on any team tint. Plain triangles in the same batch
     * as the plates, so 200+ buildings cost a few thousand vertices and no texture binds.
     *   capitol   gold star                    factory   crossed swords (army)
     *   T2/T3 lab two / three bars on a disc   mex       disc with a rim: iron (T1) or copper-gold (T2)
     *   wind      three-blade rotor            converter lightning bolt
     *   defence   crosshair                    capture   flag in the owner's colour
     */
    private static void drawGlyph(BufferBuilder bb, Matrix4f mat, Role role, int tier, float cx, float cy, float s,
                                  boolean shadow, int ownerRgb) {
        float grow = shadow ? 1.1f : 0f;   // shadow pass: everything a little bigger
        float lw = Math.max(1f, s * 0.32f) + grow;
        int white = shadow ? GLYPH_SHADOW : GLYPH_WHITE;
        switch (role) {
            case CAPITOL -> drawShape(bb, mat, Shape.STAR, cx, cy, s * 1.15f + grow, shadow ? GLYPH_SHADOW : 0xFFFFC83C);
            case FACTORY -> {
                float d = s * 0.85f;
                line(bb, mat, cx - d, cy - d, cx + d, cy + d, lw, white);
                line(bb, mat, cx + d, cy - d, cx - d, cy + d, lw, white);
                // hilts
                float h = s * 0.45f;
                line(bb, mat, cx - d - h * 0.2f, cy + d - h, cx - d + h, cy + d + h * 0.2f, lw * 0.8f, white);
                line(bb, mat, cx + d + h * 0.2f, cy + d - h, cx + d - h, cy + d + h * 0.2f, lw * 0.8f, white);
            }
            case LAB2, LAB3 -> {
                drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * 1.05f + grow, shadow ? GLYPH_SHADOW : 0xE0281E3C);
                if (!shadow) {
                    int bars = role == Role.LAB3 ? 3 : 2;
                    int col = role == Role.LAB3 ? 0xFFFF8CFF : 0xFFB4DCFF;   // T3 magenta, T2 pale blue
                    float bw = s * 0.22f, gap = s * 0.2f;
                    float total = bars * bw + (bars - 1) * gap;
                    float x = cx - total / 2;
                    for (int i = 0; i < bars; i++) {
                        rect(bb, mat, x, cy - s * 0.6f, x + bw, cy + s * 0.6f, col);
                        x += bw + gap;
                    }
                }
            }
            case MEX -> {
                if (shadow) {
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s + grow, GLYPH_SHADOW);
                } else {
                    int rim = tier > 0 ? 0xFFE8A040 : 0xFFA8A8B0;   // T2 copper-gold, T1 iron
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s, rim);
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * (tier > 0 ? 0.55f : 0.68f), 0xFF34343C);
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * 0.25f, rim);
                }
            }
            case WIND -> {
                int col = shadow ? GLYPH_SHADOW : 0xFFFFF0A0;
                float r = s * 1.05f + grow;
                for (int i = 0; i < 3; i++) {
                    double a = -Math.PI / 2 + Math.PI * 2 * i / 3;
                    float tx = cx + (float) Math.cos(a) * r, ty = cy + (float) Math.sin(a) * r;
                    float wx = (float) Math.cos(a + Math.PI / 2) * r * 0.32f;
                    float wy = (float) Math.sin(a + Math.PI / 2) * r * 0.32f;
                    tri(bb, mat, cx, cy, tx + wx, ty + wy, tx, ty, col);
                }
                drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * 0.25f + grow, col);
            }
            case CONVERTER -> {
                int col = shadow ? GLYPH_SHADOW : 0xFFFFE040;
                float g = grow;
                // a two-piece lightning bolt
                tri(bb, mat, cx + s * 0.35f + g, cy - s * 1.1f - g, cx - s * 0.65f - g, cy + s * 0.15f + g, cx + s * 0.1f, cy + s * 0.05f, col);
                tri(bb, mat, cx - s * 0.35f - g, cy + s * 1.1f + g, cx + s * 0.65f + g, cy - s * 0.15f - g, cx - s * 0.1f, cy - s * 0.05f, col);
            }
            case DEFENCE -> {
                int col = shadow ? GLYPH_SHADOW : 0xFFFF6E5A;
                float r = s * 0.8f;
                float thin = lw * 0.75f;
                ringLines(bb, mat, cx, cy, r, thin, col);
                line(bb, mat, cx - s * 1.15f, cy, cx - s * 0.35f, cy, thin, col);
                line(bb, mat, cx + s * 0.35f, cy, cx + s * 1.15f, cy, thin, col);
                line(bb, mat, cx, cy - s * 1.15f, cx, cy - s * 0.35f, thin, col);
                line(bb, mat, cx, cy + s * 0.35f, cx, cy + s * 1.15f, thin, col);
            }
            case CAPTURE -> {
                float px = cx - s * 0.55f;
                line(bb, mat, px, cy - s * 1.1f, px, cy + s * 1.1f, Math.max(1f, s * 0.2f) + grow, shadow ? GLYPH_SHADOW : GLYPH_WHITE);
                int col = shadow ? GLYPH_SHADOW : 0xFF000000 | brighten(ownerRgb, 0.15f);
                tri(bb, mat, px - grow * 0.5f, cy - s * 1.1f - grow, px + s * 1.25f + grow, cy - s * 0.55f, px - grow * 0.5f, cy + grow, col);
            }
            default -> { }
        }
    }

    private static void ringLines(BufferBuilder bb, Matrix4f mat, float cx, float cy, float r, float w, int argb) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            line(bb, mat, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, w, argb);
        }
    }

    private static boolean offScreen(float[] xs, float[] ys) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return maxX < -8 || minX > guiW + 8 || maxY < -8 || minY > guiH + 8;
    }

    private static void drawUnitIcon(BufferBuilder bb, Matrix4f mat, LivingEntity entity, float partialTick,
                                     boolean isSelected, boolean isPreselected) {
        if (!entity.isAlive() || entity.isRemoved() || entity.isPassenger())
            return;
        if (MC.level != null && !MC.level.getWorldBorder().isWithinBounds(entity.getOnPos()))
            return;
        if (!FogOfWarClientEvents.isInBrightChunk(entity))
            return;
        if (entity.isInvisible() && UnitClientEvents.getPlayerToEntityRelationship(entity) != Relationship.OWNED)
            return;

        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
        float sx = projX(x, y, z);
        float sy = projY(x, y, z);
        if (sx < -10 || sx > guiW + 10 || sy < -10 || sy > guiH + 10)
            return;

        float r = Mth.clamp(2.2f + entity.getBbWidth() * 1.5f, 3f, 7f);
        if (entity instanceof HeroUnit)
            r += 1f;
        boolean commander = isCommander(entity);
        if (commander)
            r += 2.5f;
        int fill = 0xFF000000 | teamColour(entity);
        int outline;
        float outlineW;
        if (isSelected) {
            outline = 0xFFFFFFFF;
            outlineW = 1.6f;
        } else if (isPreselected) {
            outline = 0xFFC8C8C8;
            outlineW = 1.2f;
        } else {
            outline = 0xE0101010;
            outlineW = 0.9f;
        }
        if (isSelected)
            fill = 0xFF000000 | brighten(fill & 0xFFFFFF, 0.25f);

        Shape shape;
        if (commander)
            shape = Shape.STAR;
        else if (entity instanceof HeroUnit)
            shape = Shape.DIAMOND;
        else if (entity instanceof WorkerUnit)
            shape = Shape.CIRCLE;
        else if (entity instanceof RangedAttackerUnit)
            shape = Shape.TRIANGLE;
        else if (entity instanceof AttackerUnit)
            shape = Shape.SQUARE;
        else
            shape = Shape.CIRCLE;

        if (commander)
            drawShape(bb, mat, Shape.CIRCLE, sx, sy, r + outlineW + 2f, isSelected ? 0xFFFFFFFF : 0xFFFFC83C);   // gold ring: the player's life
        drawShape(bb, mat, shape, sx, sy, r + outlineW, outline);
        drawShape(bb, mat, shape, sx, sy, r, fill);

        // compact health bar under damaged units (replaces the per-unit world health bars)
        float hp = entity.getHealth() / Math.max(1f, entity.getMaxHealth());
        if (hp < 0.999f) {
            float bw = r * 2 + 2;
            float bx = sx - bw / 2;
            float by = sy + r + outlineW + 1;
            rect(bb, mat, bx - 0.5f, by - 0.5f, bx + bw + 0.5f, by + 2f, 0xC0000000);
            int hpCol = hp > 0.6f ? 0xFF3CD23C : hp > 0.3f ? 0xFFE6C832 : 0xFFE03C32;
            rect(bb, mat, bx, by, bx + bw * Mth.clamp(hp, 0f, 1f), by + 1.5f, hpCol);
        }
    }

    private enum Shape { SQUARE, TRIANGLE, CIRCLE, DIAMOND, STAR }

    private static final float[] STAR_X = new float[10], STAR_Y = new float[10];
    private static final Set<LivingEntity> SELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static final Set<LivingEntity> PRESELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static final List<LivingEntity> DRAW_LAST_SCRATCH = new ArrayList<>();

    private static void drawShape(BufferBuilder bb, Matrix4f mat, Shape shape, float cx, float cy, float r, int argb) {
        switch (shape) {
            case SQUARE -> rect(bb, mat, cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f, argb);
            case DIAMOND -> {
                tri(bb, mat, cx, cy - r, cx + r, cy, cx - r, cy, argb);
                tri(bb, mat, cx - r, cy, cx + r, cy, cx, cy + r, argb);
            }
            case TRIANGLE -> tri(bb, mat, cx, cy - r * 1.1f, cx + r * 1.05f, cy + r * 0.8f, cx - r * 1.05f, cy + r * 0.8f, argb);
            case STAR -> {   // five points, two fans
                int n = 10;
                float[] px = STAR_X, py = STAR_Y;   // scratch: no arrays per star per frame
                for (int i = 0; i < n; i++) {
                    double a = -Math.PI / 2 + Math.PI * i / 5;
                    float rr = (i % 2 == 0) ? r : r * 0.45f;
                    px[i] = cx + (float) Math.cos(a) * rr;
                    py[i] = cy + (float) Math.sin(a) * rr;
                }
                for (int i = 0; i < n; i++)
                    tri(bb, mat, cx, cy, px[i], py[i], px[(i + 1) % n], py[(i + 1) % n], argb);
            }
            case CIRCLE -> {
                int n = 14;
                float r2 = r * 0.92f;
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2 * i / n;
                    double a1 = Math.PI * 2 * (i + 1) / n;
                    tri(bb, mat, cx, cy,
                            cx + (float) Math.cos(a0) * r2, cy + (float) Math.sin(a0) * r2,
                            cx + (float) Math.cos(a1) * r2, cy + (float) Math.sin(a1) * r2, argb);
                }
            }
        }
    }

    private static void vtx(BufferBuilder bb, Matrix4f mat, float x, float y, int argb) {
        bb.vertex(mat, x, y, 0).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    private static void tri(BufferBuilder bb, Matrix4f mat, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        vtx(bb, mat, x1, y1, argb);
        vtx(bb, mat, x2, y2, argb);
        vtx(bb, mat, x3, y3, argb);
    }

    private static void rect(BufferBuilder bb, Matrix4f mat, float x0, float y0, float x1, float y1, int argb) {
        tri(bb, mat, x0, y0, x1, y0, x1, y1, argb);
        tri(bb, mat, x0, y0, x1, y1, x0, y1, argb);
    }

    private static void quad(BufferBuilder bb, Matrix4f mat, float[] xs, float[] ys, int argb) {
        tri(bb, mat, xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], argb);
        tri(bb, mat, xs[0], ys[0], xs[2], ys[2], xs[3], ys[3], argb);
    }

    private static void line(BufferBuilder bb, Matrix4f mat, float x0, float y0, float x1, float y1, float w, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f)
            return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        // two triangles straight into the buffer (no temp arrays: this runs for every plate edge and glyph stroke)
        tri(bb, mat, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, argb);
        tri(bb, mat, x0 + nx, y0 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    private static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int brighten(int rgb, float f) {
        int r = (int) Mth.lerp(f, (rgb >> 16) & 0xFF, 255);
        int g = (int) Mth.lerp(f, (rgb >> 8) & 0xFF, 255);
        int b = (int) Mth.lerp(f, rgb & 0xFF, 255);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------------------------------------------------
    // in-world readability extras: range rings and selection plates
    // ------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null)
            return;
        if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS && selectionPlatesEnabled && !strategic)
            drawSelectionPlates(evt.getPoseStack(), evt.getPartialTick());
        else if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && rangeRingsEnabled)
            drawRangeRings(evt.getPoseStack(), evt.getPartialTick());
    }

    private static void drawSelectionPlates(PoseStack poseStack, float partialTick) {
        List<LivingEntity> selectedUnits = UnitClientEvents.getSelectedUnits();
        if (selectedUnits.isEmpty())
            return;
        Vec3 cp = MC.gameRenderer.getMainCamera().getPosition();
        RenderType type = MyRenderer.LINES_UNDER_ENTITIES;
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(type);
        for (LivingEntity entity : selectedUnits) {
            if (!FogOfWarClientEvents.isInBrightChunk(entity) || entity.isPassenger())
                continue;
            int rgb = teamColour(entity);
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
            double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - cp.x;
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + 0.05 - cp.y;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - cp.z;
            double radius = Math.max(0.5, entity.getBbWidth() * 0.75) + 0.15;
            ring(poseStack, vc, x, y, z, radius, 24, r, g, b, 0.95f);
            ring(poseStack, vc, x, y, z, radius - 0.07, 24, r, g, b, 0.95f);
            ring(poseStack, vc, x, y, z, radius + 0.07, 24, 1f, 1f, 1f, 0.6f);
        }
        MC.renderBuffers().bufferSource().endBatch(type);
    }

    private static void drawRangeRings(PoseStack poseStack, float partialTick) {
        List<LivingEntity> selectedUnits = UnitClientEvents.getSelectedUnits();
        if (selectedUnits.isEmpty())
            return;
        Vec3 cp = MC.gameRenderer.getMainCamera().getPosition();
        RenderType type = MyRenderer.LINES_NO_DEPTH_TEST;
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(type);
        int drawn = 0;
        for (LivingEntity entity : selectedUnits) {
            if (drawn >= MAX_RANGE_RINGS)
                break;
            if (!(entity instanceof AttackerUnit attacker) || !FogOfWarClientEvents.isInBrightChunk(entity))
                continue;
            float range = attacker.getAttackRange();
            if (range < 2.5f)
                continue;
            double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - cp.x;
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + 0.15 - cp.y;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - cp.z;
            ring(poseStack, vc, x, y, z, range, RING_SEGMENTS, 1f, 0.35f, 0.3f, 0.7f);
            drawn++;
        }
        MC.renderBuffers().bufferSource().endBatch(type);
    }

    private static void ring(PoseStack poseStack, VertexConsumer vc, double cx, double cy, double cz, double radius,
                             int segments, float r, float g, float b, float a) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = (float) (cx + Math.cos(a0) * radius), z0 = (float) (cz + Math.sin(a0) * radius);
            float x1 = (float) (cx + Math.cos(a1) * radius), z1 = (float) (cz + Math.sin(a1) * radius);
            float nx = x1 - x0, nz = z1 - z0;
            float len = (float) Math.sqrt(nx * nx + nz * nz);
            if (len > 0) {
                nx /= len;
                nz /= len;
            }
            vc.vertex(pose, x0, (float) cy, z0).color(r, g, b, a).normal(normal, nx, 0, nz).endVertex();
            vc.vertex(pose, x1, (float) cy, z1).color(r, g, b, a).normal(normal, nx, 0, nz).endVertex();
        }
    }
}
