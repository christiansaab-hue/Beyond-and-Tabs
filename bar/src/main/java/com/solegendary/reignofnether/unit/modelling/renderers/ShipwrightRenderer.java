package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Tidewrought Shipwright (and the Admiral): the worker illager body in a teal smock and brass-buckled apron. */
@OnlyIn(Dist.CLIENT)
public class ShipwrightRenderer extends AbstractVillagerUnitRenderer<ShipwrightUnit> {
    private static final ResourceLocation SHIPWRIGHT_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/shipwright_unit.png");

    public ShipwrightRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<ShipwrightUnit, VillagerUnitModel<ShipwrightUnit>>(this, context.getItemInHandRenderer()) {
            public void render(PoseStack pose, MultiBufferSource mbs, int light, ShipwrightUnit unit, float a, float b, float c, float d, float e, float f) {
                // tools only while working or fighting, like the other factions' workers
                if ((unit.getBuildRepairGoal() != null && unit.getBuildRepairGoal().isBuilding()) ||
                    (unit.getGatherResourceGoal() != null && unit.getGatherResourceGoal().isGathering()) ||
                    (unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null)) {
                    super.render(pose, mbs, light, unit, a, b, c, d, e, f);
                }
            }
        });
    }

    public ResourceLocation getTextureLocation(ShipwrightUnit unit) {
        return SHIPWRIGHT_UNIT;
    }
}
