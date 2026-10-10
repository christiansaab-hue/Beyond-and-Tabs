package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Sentinel Treant: the iron golem model in bark and moss (128x128, the golem layout), a touch
 * taller so it reads as the Court's wall from the RTS camera. The vanilla crack overlay doubles as split bark.
 */
@OnlyIn(Dist.CLIENT)
public class SentinelTreantRenderer extends IronGolemRenderer {
    private static final ResourceLocation SENTINEL_TREANT_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/sentinel_treant_unit.png");
    static final float SCALE = 1.1f;

    public SentinelTreantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return SENTINEL_TREANT_UNIT;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}
