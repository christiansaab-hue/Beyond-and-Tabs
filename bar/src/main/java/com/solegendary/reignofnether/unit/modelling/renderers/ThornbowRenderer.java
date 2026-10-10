package com.solegendary.reignofnether.unit.modelling.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Thornbow: the skeleton's slim archer model (with its bow-draw animation) wearing an elven skin -
 * pale face, leaf hood, bark-brown tunic (64x32, the skeleton layout). Liveries add the green leather hood and coat.
 */
@OnlyIn(Dist.CLIENT)
public class ThornbowRenderer extends SkeletonRenderer {
    private static final ResourceLocation THORNBOW_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/thornbow_unit.png");

    public ThornbowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractSkeleton entity) {
        return THORNBOW_UNIT;
    }
}
