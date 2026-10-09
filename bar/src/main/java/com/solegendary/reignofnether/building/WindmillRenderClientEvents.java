package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The windmill's sails: every built Wind Generator carries a horizontal stripped spruce log as its rotor hub, and
 * this renderer spins four cloth blades around it, each mill with its own phase so a wind farm doesn't march in
 * lockstep. Pure client-side rendering - no block updates, no server cost.
 */
public class WindmillRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final float SPIN_RADS_PER_TICK = 0.045f;  // a lazy ~8s per turn
    private static final float BLADE_LENGTH = 2.9f;
    private static final float BLADE_WIDTH = 0.52f;
    private static final float BLADE_THICKNESS = 0.14f;
    private static final double MAX_RENDER_DIST_SQR = 220 * 220;

    /** The hub of a windmill placement: its horizontal stripped spruce log, or null. */
    private static BuildingBlock findHub(BuildingPlacement placement) {
        for (BuildingBlock bb : placement.getBlocks()) {
            BlockState bs = bb.getBlockState();
            if (bs.is(Blocks.STRIPPED_SPRUCE_LOG) && bs.hasProperty(BlockStateProperties.AXIS)
                    && bs.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y)
                return bb;
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        if (MC.level == null || MC.cameraEntity == null)
            return;

        PoseStack matrix = evt.getPoseStack();
        Entity cam = MC.cameraEntity;
        BlockRenderDispatcher renderer = MC.getBlockRenderer();
        BlockState bladeCloth = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState bladeArm = Blocks.SPRUCE_PLANKS.defaultBlockState();
        float time = MC.level.getGameTime() + evt.getPartialTick();
        boolean renderedAny = false;

        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (!(placement.getBuilding() instanceof WindGenerator)
                    || !placement.isBuilt || !placement.isExploredClientside)
                continue;
            BuildingBlock hubBlock = findHub(placement);
            if (hubBlock == null)
                continue;
            BlockPos hub = hubBlock.getBlockPos();
            if (cam.distanceToSqr(hub.getX(), hub.getY(), hub.getZ()) > MAX_RENDER_DIST_SQR)
                continue;

            Direction.Axis axis = hubBlock.getBlockState().getValue(BlockStateProperties.AXIS);

            // rotor plane sits just outside the hub's outward face (away from the tower centre)
            BlockPos centre = BuildingUtils.getCentrePos(placement.getBlocks());
            double outward;
            if (axis == Direction.Axis.Z)
                outward = hub.getZ() + 0.5 >= centre.getZ() + 0.5 ? 0.68 : -0.68;
            else
                outward = hub.getX() + 0.5 >= centre.getX() + 0.5 ? 0.68 : -0.68;

            float phase = (hub.hashCode() & 255) / 255f * Mth.TWO_PI;
            float angle = time * SPIN_RADS_PER_TICK + phase;
            int light = LevelRenderer.getLightColor(MC.level, hub.above());

            matrix.pushPose();
            matrix.translate(
                hub.getX() + 0.5 + (axis == Direction.Axis.X ? outward : 0) - cam.getX(),
                hub.getY() + 0.5 - cam.getY(),
                hub.getZ() + 0.5 + (axis == Direction.Axis.Z ? outward : 0) - cam.getZ());

            for (int i = 0; i < 4; i++) {
                matrix.pushPose();
                if (axis == Direction.Axis.Z)
                    matrix.mulPose(com.mojang.math.Axis.ZP.rotation(angle + i * Mth.HALF_PI));
                else
                    matrix.mulPose(com.mojang.math.Axis.XP.rotation(angle + i * Mth.HALF_PI));

                // the cloth sail: a long box reaching out from the hub along local +Y
                matrix.pushPose();
                if (axis == Direction.Axis.Z) {
                    matrix.translate(-BLADE_WIDTH / 2, 0.35, -BLADE_THICKNESS / 2);
                    matrix.scale(BLADE_WIDTH, BLADE_LENGTH, BLADE_THICKNESS);
                } else {
                    matrix.translate(-BLADE_THICKNESS / 2, 0.35, -BLADE_WIDTH / 2);
                    matrix.scale(BLADE_THICKNESS, BLADE_LENGTH, BLADE_WIDTH);
                }
                renderer.renderSingleBlock(bladeCloth, matrix, MC.renderBuffers().bufferSource(),
                    light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                matrix.popPose();

                // the wooden spar running up the middle of the sail
                matrix.pushPose();
                if (axis == Direction.Axis.Z) {
                    matrix.translate(-0.09, 0.0, -BLADE_THICKNESS / 2 - 0.02);
                    matrix.scale(0.18f, BLADE_LENGTH + 0.4f, BLADE_THICKNESS + 0.04f);
                } else {
                    matrix.translate(-BLADE_THICKNESS / 2 - 0.02, 0.0, -0.09);
                    matrix.scale(BLADE_THICKNESS + 0.04f, BLADE_LENGTH + 0.4f, 0.18f);
                }
                renderer.renderSingleBlock(bladeArm, matrix, MC.renderBuffers().bufferSource(),
                    light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                matrix.popPose();

                matrix.popPose();
            }
            matrix.popPose();
            renderedAny = true;
        }
        if (renderedAny)
            MC.renderBuffers().bufferSource().endBatch();
    }
}
