package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Elder Treant: the golem model in old, dark bark with a heavy moss beard (the Sentinel Treant's
 * layout, 128x128), drawn {@link ElderTreantUnit#SCALE}x so it towers over the Sentinels from the RTS camera.
 */
@OnlyIn(Dist.CLIENT)
public class ElderTreantRenderer extends IronGolemRenderer {
    private static final ResourceLocation ELDER_TREANT_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/elder_treant_unit.png");

    public ElderTreantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= ElderTreantUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return ELDER_TREANT_UNIT;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(ElderTreantUnit.SCALE, ElderTreantUnit.SCALE, ElderTreantUnit.SCALE);
    }
}
