package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Verdant Court Hive Keeper: the Halberdier's renderer (and attack animation) in honey-and-moss beekeeper's robes. */
@OnlyIn(Dist.CLIENT)
public class HiveKeeperRenderer extends VindicatorUnitRenderer {
    private static final ResourceLocation HIVE_KEEPER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/hive_keeper_unit.png");

    public HiveKeeperRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(VindicatorUnit unit) {
        return HIVE_KEEPER_UNIT;
    }
}
