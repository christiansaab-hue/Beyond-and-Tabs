package com.solegendary.reignofnether.orthoview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.solegendary.reignofnether.building.BuildingClientEvents;
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
import java.util.HashSet;
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
 * Also draws, at any zoom: attack range rings for selected units, and team-coloured selection rings under
 * selected units (normal zoom only; the icons replace them in strategic zoom).
 */
public class StrategicViewClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public static final float STRATEGIC_ZOOM_ENTER = 100f;
    public static final float STRATEGIC_ZOOM_EXIT = 92f; // hysteresis so it doesn't flicker at the threshold
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
        }

        // ---- units: role icons ----
        Set<LivingEntity> selected = new HashSet<>(UnitClientEvents.getSelectedUnits());
        Set<LivingEntity> preselected = new HashSet<>(UnitClientEvents.getPreselectedUnits());
        List<LivingEntity> drawLast = new ArrayList<>();
        for (LivingEntity entity : UnitClientEvents.getAllUnits()) {
            if (selected.contains(entity) || preselected.contains(entity))
                drawLast.add(entity);
            else
                drawUnitIcon(bb, mat, entity, partialTick, false, false);
        }
        for (LivingEntity entity : drawLast)
            drawUnitIcon(bb, mat, entity, partialTick, selected.contains(entity), preselected.contains(entity));

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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
        if (entity instanceof HeroUnit)
            shape = Shape.DIAMOND;
        else if (entity instanceof WorkerUnit)
            shape = Shape.CIRCLE;
        else if (entity instanceof RangedAttackerUnit)
            shape = Shape.TRIANGLE;
        else if (entity instanceof AttackerUnit)
            shape = Shape.SQUARE;
        else
            shape = Shape.CIRCLE;

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

    private enum Shape { SQUARE, TRIANGLE, CIRCLE, DIAMOND }

    private static void drawShape(BufferBuilder bb, Matrix4f mat, Shape shape, float cx, float cy, float r, int argb) {
        switch (shape) {
            case SQUARE -> rect(bb, mat, cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f, argb);
            case DIAMOND -> {
                tri(bb, mat, cx, cy - r, cx + r, cy, cx - r, cy, argb);
                tri(bb, mat, cx - r, cy, cx + r, cy, cx, cy + r, argb);
            }
            case TRIANGLE -> tri(bb, mat, cx, cy - r * 1.1f, cx + r * 1.05f, cy + r * 0.8f, cx - r * 1.05f, cy + r * 0.8f, argb);
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
        float[] xs = { x0 + nx, x1 + nx, x1 - nx, x0 - nx };
        float[] ys = { y0 + ny, y1 + ny, y1 - ny, y0 - ny };
        quad(bb, mat, xs, ys, argb);
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
