package com.solegendary.reignofnether.unit.modelling.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Moonwell Bearer: vanilla's witch body (and its held lantern) in the Court's own skin - a moon-silver
 * robe over moss, a belt of little lanterns, the pointed hat recut as a moss cowl with a silver brim and a moon at the
 * tip (64x128, the witch layout; drawn by tools/gen_verdant_skins.py). It used to wear the plain vanilla witch skin.
 */
@OnlyIn(Dist.CLIENT)
public class MoonwellBearerRenderer extends WitchRenderer {
    private static final ResourceLocation MOONWELL_BEARER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/moonwell_bearer_unit.png");

    public MoonwellBearerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Witch entity) {
        return MOONWELL_BEARER_UNIT;
    }
}
