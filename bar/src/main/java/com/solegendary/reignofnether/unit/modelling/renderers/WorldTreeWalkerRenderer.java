package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court World Tree Walker: the golem model in pale ancient bark under a thick leaf-and-blossom canopy
 * (head and shoulders), moss down the limbs (the Sentinel Treant's 128x128 layout), drawn {@link WorldTreeWalkerUnit#SCALE}x
 * so it reads as a walking tree from the RTS camera.
 */
@OnlyIn(Dist.CLIENT)
public class WorldTreeWalkerRenderer extends IronGolemRenderer {
    private static final ResourceLocation WORLD_TREE_WALKER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/world_tree_walker_unit.png");

    public WorldTreeWalkerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius *= WorldTreeWalkerUnit.SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem entity) {
        return WORLD_TREE_WALKER_UNIT;
    }

    @Override
    protected void scale(IronGolem entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(WorldTreeWalkerUnit.SCALE, WorldTreeWalkerUnit.SCALE, WorldTreeWalkerUnit.SCALE);
    }
}
