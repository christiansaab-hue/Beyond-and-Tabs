package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Verdant Court Leafblade: the Halberdier's renderer (and attack animation) with its own leaf-green texture. */
@OnlyIn(Dist.CLIENT)
public class LeafbladeRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation LEAFBLADE_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/leafblade_unit.png");

    public LeafbladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return LEAFBLADE_UNIT;
    }
}
