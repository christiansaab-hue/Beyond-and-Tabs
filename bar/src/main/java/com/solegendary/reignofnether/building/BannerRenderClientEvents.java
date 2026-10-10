package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * War banners: every finished production building (and every capitol) flies its faction's colours from a pole on
 * its highest point, the cloth rippling in segments. The Sunforged Kingdom flies white and gold, the Gravebound
 * soul-blue on black, the Ironhide Horde rust red trimmed in bone. Client-side boxes only; skipped entirely in strategic zoom.
 */
public class BannerRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final int SEGMENTS = 5;
    private static final float SEG_LEN = 0.32f;
    private static final float CLOTH_HEIGHT = 0.9f;
    private static final float POLE_HEIGHT = 2.4f;
    private static final double MAX_RENDER_DIST_SQR = 160 * 160;

    /** placement -> its banner mount (top block), recomputed only if the building changes shape (upgrades). */
    private record Mount(int blockCount, BlockPos pos) { }
    private static final Map<BuildingPlacement, Mount> mounts = new WeakHashMap<>();

    private static BlockPos mountFor(BuildingPlacement placement) {
        int count = placement.getBlocks().size();
        Mount cached = mounts.get(placement);
        if (cached != null && cached.blockCount() == count)
            return cached.pos();
        BlockPos pos = computeMount(placement);
        mounts.put(placement, new Mount(count, pos));
        return pos;
    }

    private static BlockPos computeMount(BuildingPlacement p) {
        {
            BlockPos best = null;
            for (BuildingBlock bb : p.getBlocks()) {
                if (bb.getBlockState().isAir())
                    continue;
                BlockPos bp = bb.getBlockPos();
                // highest block; ties broken towards a corner so the pole doesn't skewer the roof ridge centre
                if (best == null || bp.getY() > best.getY()
                        || (bp.getY() == best.getY() && bp.getX() + bp.getZ() < best.getX() + best.getZ()))
                    best = bp;
            }
            return best;
        }
    }

    private static boolean fliesBanner(BuildingPlacement placement) {
        if (!placement.isBuilt || !placement.isExploredClientside)
            return false;
        if (placement.getBuilding() instanceof MetalExtractor)
            return false;
        return placement.getBuilding().isCapitol || placement instanceof ProductionPlacement;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (MC.level == null || MC.cameraEntity == null || StrategicViewClientEvents.isStrategicView())
            return;

        PoseStack matrix = evt.getPoseStack();
        Entity cam = MC.cameraEntity;
        BlockRenderDispatcher renderer = MC.getBlockRenderer();
        float time = MC.level.getGameTime() + evt.getPartialTick();
        BlockState pole = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        boolean renderedAny = false;

        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (!fliesBanner(placement))
                continue;
            BlockPos mount = mountFor(placement);
            if (mount == null || cam.distanceToSqr(mount.getX(), mount.getY(), mount.getZ()) > MAX_RENDER_DIST_SQR)
                continue;

            // colours per faction in FactionTraits (Sunforged white/gold, Gravebound soul-blue/black, Horde red/bone,
            // Verdant green/silver); factions without an entry fly no banner rather than the Kingdom's
            FactionTraits traits = FactionTraits.of(placement.getBuilding().getFaction());
            BlockState cloth = traits.bannerCloth;
            BlockState trim = traits.bannerTrim;
            if (cloth == null || trim == null)
                continue;
            int light = LevelRenderer.getLightColor(MC.level, mount.above(2));
            float phase = (mount.hashCode() & 255) / 255f * Mth.TWO_PI;

            matrix.pushPose();
            matrix.translate(mount.getX() + 0.5 - cam.getX(), mount.getY() + 1.0 - cam.getY(),
                mount.getZ() + 0.5 - cam.getZ());

            // the pole
            matrix.pushPose();
            matrix.translate(-0.06, 0, -0.06);
            matrix.scale(0.12f, POLE_HEIGHT, 0.12f);
            renderer.renderSingleBlock(pole, matrix, MC.renderBuffers().bufferSource(),
                light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            matrix.popPose();

            // the cloth: segments trailing off the pole, each bending a little further in the wind
            float yaw = Mth.sin(time * 0.013f + phase) * 0.35f;   // slow drift of the wind direction
            matrix.translate(0, POLE_HEIGHT - CLOTH_HEIGHT - 0.05, 0);
            matrix.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
            float x = 0.06f;
            for (int i = 0; i < SEGMENTS; i++) {
                float wave = Mth.sin(time * 0.22f - i * 0.9f + phase) * 0.09f * (i + 1);
                float droop = i * 0.03f;
                matrix.pushPose();
                matrix.translate(x, -droop, wave - 0.02);
                matrix.scale(SEG_LEN + 0.01f, CLOTH_HEIGHT, 0.04f);
                renderer.renderSingleBlock(i == SEGMENTS - 1 ? trim : cloth, matrix,
                    MC.renderBuffers().bufferSource(), light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                matrix.popPose();
                x += SEG_LEN;
            }
            matrix.popPose();
            renderedAny = true;
        }
        if (renderedAny)
            MC.renderBuffers().bufferSource().endBatch();
    }
}
