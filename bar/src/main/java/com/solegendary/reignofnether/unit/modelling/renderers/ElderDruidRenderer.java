package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.verdant.SeedshaperUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Verdant Court Elder Druid: the Seedshaper's druid body in a silver-white elder's robe with a moss mantle. */
@OnlyIn(Dist.CLIENT)
public class ElderDruidRenderer extends SeedshaperRenderer {
    private static final ResourceLocation ELDER_DRUID_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/elder_druid_unit.png");

    public ElderDruidRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SeedshaperUnit unit) {
        return ELDER_DRUID_UNIT;
    }
}
