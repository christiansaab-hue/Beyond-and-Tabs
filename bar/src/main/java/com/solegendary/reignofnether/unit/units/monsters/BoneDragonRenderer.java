package com.solegendary.reignofnether.unit.units.monsters;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Client only: the Bone Dragon - a phantom-bodied wyrm at {@link BoneDragonUnit#SCALE}x (placeholder texture). */
public class BoneDragonRenderer extends MobRenderer<BoneDragonUnit, BoneDragonModel> {

    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/entity/phantom.png");

    public BoneDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BoneDragonModel(ctx.bakeLayer(ModelLayers.PHANTOM)), 0.75f * BoneDragonUnit.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(BoneDragonUnit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(BoneDragonUnit entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(BoneDragonUnit.SCALE, BoneDragonUnit.SCALE, BoneDragonUnit.SCALE);
        poseStack.translate(0.0, 1.3125, 0.1875);   // same offset vanilla applies to the phantom model
    }

    @Override
    protected void setupRotations(BoneDragonUnit entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }
}
