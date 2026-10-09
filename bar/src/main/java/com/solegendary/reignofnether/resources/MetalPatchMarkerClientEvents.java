package com.solegendary.reignofnether.resources;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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

/**
 * BAR's metal-spot markers: every FREE metal patch carries a small spinning, bobbing raw-iron gem floating above
 * it, drawn fullbright so it reads at the Fallen's midnight as well as at noon, and scaled up in strategic zoom
 * so expansions can be planned from the map. Taken patches (a building on them) lose their gem. Client-only.
 */
public class MetalPatchMarkerClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    private static final double MAX_DIST_SQR = 260 * 260;
    private static final int FULLBRIGHT = LightTexture.pack(15, 15);

    static boolean isTaken(BlockPos patch) {
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (Math.abs(b.centrePos.getX() - patch.getX()) <= 4 && Math.abs(b.centrePos.getZ() - patch.getZ()) <= 4)
                return true;
        return false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (MC.level == null || MC.cameraEntity == null || !OrthoviewClientEvents.isEnabled())
            return;
        if (MetalPatchesClient.getPatches().isEmpty())
            return;

        PoseStack matrix = evt.getPoseStack();
        Entity cam = MC.cameraEntity;
        BlockRenderDispatcher renderer = MC.getBlockRenderer();
        BlockState gem = Blocks.RAW_IRON_BLOCK.defaultBlockState();
        float time = MC.level.getGameTime() + evt.getPartialTick();
        boolean strategic = StrategicViewClientEvents.isStrategicView();
        float size = strategic ? 2.2f : 0.5f;
        boolean renderedAny = false;

        for (BlockPos patch : MetalPatchesClient.getPatches()) {
            if (cam.distanceToSqr(patch.getX(), patch.getY(), patch.getZ()) > MAX_DIST_SQR)
                continue;
            if (isTaken(patch))
                continue;
            float phase = (patch.hashCode() & 255) / 255f * Mth.TWO_PI;
            float bob = strategic ? 0 : Mth.sin(time * 0.08f + phase) * 0.12f;

            matrix.pushPose();
            matrix.translate(patch.getX() + 0.5 - cam.getX(),
                patch.getY() + 1.9 + bob - cam.getY(),
                patch.getZ() + 0.5 - cam.getZ());
            matrix.mulPose(com.mojang.math.Axis.YP.rotation(time * 0.03f + phase));
            matrix.mulPose(com.mojang.math.Axis.XP.rotation(Mth.QUARTER_PI));
            matrix.mulPose(com.mojang.math.Axis.ZP.rotation(Mth.QUARTER_PI));
            matrix.translate(-size / 2, -size / 2, -size / 2);
            matrix.scale(size, size, size);
            renderer.renderSingleBlock(gem, matrix, MC.renderBuffers().bufferSource(),
                FULLBRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            matrix.popPose();
            renderedAny = true;
        }
        if (renderedAny)
            MC.renderBuffers().bufferSource().endBatch();
    }
}
