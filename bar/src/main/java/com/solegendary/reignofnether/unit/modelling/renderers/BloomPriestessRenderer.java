package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Bloom Priestess: the Court's illager body (as the Seedshaper and Elder Druid) in a rose-and-white
 * robe with a petal-dotted hem, a recolour of the Elder Druid's robe. A non-fighter, so its arms stay folded.
 */
@OnlyIn(Dist.CLIENT)
public class BloomPriestessRenderer extends AbstractVillagerUnitRenderer<BloomPriestessUnit> {
    private static final ResourceLocation BLOOM_PRIESTESS_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/bloom_priestess_unit.png");

    public BloomPriestessRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(BloomPriestessUnit unit) {
        return BLOOM_PRIESTESS_UNIT;
    }
}
