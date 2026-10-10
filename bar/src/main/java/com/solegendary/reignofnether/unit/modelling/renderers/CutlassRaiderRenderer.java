package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Cutlass Raider: the Halberdier's renderer (and attack animation) in a corsair's sash, tricorne and striped shirt. */
@OnlyIn(Dist.CLIENT)
public class CutlassRaiderRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/cutlass_raider_unit.png");

    public CutlassRaiderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return TEXTURE;
    }
}
