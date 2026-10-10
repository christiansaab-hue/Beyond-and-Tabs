package com.solegendary.reignofnether.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.alliance.AlliancesClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws a {@link VineSnareBlock} - a moss patch with a low thorny bush - for its owner and the owner's allies only.
 * Everyone else draws nothing: the block has no model and no outline, so the trap is invisible to the enemy.
 */
@OnlyIn(Dist.CLIENT)
public class VineSnareRenderer implements BlockEntityRenderer<VineSnareBlockEntity> {

    private static final BlockState MOSS = Blocks.MOSS_CARPET.defaultBlockState();
    private static final BlockState THORNS = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1);

    public VineSnareRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(VineSnareBlockEntity snare, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !AlliancesClient.isAlliedOrOwned(player.getName().getString(), snare.getOwner()))
            return;
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        dispatcher.renderSingleBlock(MOSS, poseStack, bufferSource, packedLight, packedOverlay, ModelData.EMPTY, null);
        poseStack.pushPose();
        poseStack.translate(0.2, 0, 0.2);
        poseStack.scale(0.6f, 0.45f, 0.6f);
        dispatcher.renderSingleBlock(THORNS, poseStack, bufferSource, packedLight, packedOverlay, ModelData.EMPTY, null);
        poseStack.popPose();
    }
}
