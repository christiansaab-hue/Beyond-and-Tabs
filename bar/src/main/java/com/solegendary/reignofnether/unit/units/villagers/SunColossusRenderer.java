package com.solegendary.reignofnether.unit.units.villagers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.world.entity.animal.IronGolem;

/** Client only: the Sun Colossus is the iron golem model at {@link SunColossusUnit#SCALE} (placeholder look). */
public class SunColossusRenderer extends IronGolemRenderer {

    public SunColossusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= SunColossusUnit.SCALE;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SunColossusUnit.SCALE, SunColossusUnit.SCALE, SunColossusUnit.SCALE);
    }
}
