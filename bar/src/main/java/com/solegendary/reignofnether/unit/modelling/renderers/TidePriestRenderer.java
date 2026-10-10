package com.solegendary.reignofnether.unit.modelling.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Witch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tidewrought Tide Priest: vanilla's witch body (and her held conch) in kelp-green and teal robes with a shell
 * belt, the pointed hat recut as a tall kelp mitre with a brass band (64x128, the witch layout; tools/gen_tide_skins.py).
 */
@OnlyIn(Dist.CLIENT)
public class TidePriestRenderer extends WitchRenderer {
    private static final ResourceLocation TIDE_PRIEST_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/tide_priest_unit.png");

    public TidePriestRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Witch entity) {
        return TIDE_PRIEST_UNIT;
    }
}
