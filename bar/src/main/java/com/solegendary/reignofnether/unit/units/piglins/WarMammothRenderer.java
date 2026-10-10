package com.solegendary.reignofnether.unit.units.piglins;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.world.entity.monster.Ravager;

/** Client only: the War Mammoth is the ravager model at {@link WarMammothUnit#SCALE} (placeholder look). */
public class WarMammothRenderer extends RavagerRenderer {

    public WarMammothRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius *= WarMammothUnit.SCALE;
    }

    @Override
    protected void scale(Ravager entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(WarMammothUnit.SCALE, WarMammothUnit.SCALE, WarMammothUnit.SCALE);
    }
}
