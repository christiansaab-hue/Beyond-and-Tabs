package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Reef Guard: the Halberdier's renderer (and attack animation) in brass plate studded with coral. */
@OnlyIn(Dist.CLIENT)
public class ReefGuardRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/reef_guard_unit.png");

    public ReefGuardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return TEXTURE;
    }
}
