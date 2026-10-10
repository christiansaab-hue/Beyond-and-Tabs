package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Bombard Crew: PillagerUnitRenderer's illager body in a gunner's slate coat, powder belt and brass cap. */
@OnlyIn(Dist.CLIENT)
public class BombardCrewRenderer extends AbstractVillagerUnitRenderer<BombardCrewUnit> {
    private static final ResourceLocation BOMBARD_CREW_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/bombard_crew_unit.png");

    public BombardCrewRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    public ResourceLocation getTextureLocation(BombardCrewUnit unit) {
        return BOMBARD_CREW_UNIT;
    }
}
