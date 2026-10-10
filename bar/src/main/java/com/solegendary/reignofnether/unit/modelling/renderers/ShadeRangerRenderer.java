package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Verdant Court Shade Ranger: the skeleton's archer model in a dusk-grey hooded cloak (64x32, the skeleton
 * layout). While cloaked it draws nothing at all for anyone it is invisible to - vanilla would still draw its livery
 * armour and bow on an invisible body - and its owner gets vanilla's translucent ghost (ShadeRangerUnit.isInvisibleTo).
 */
@OnlyIn(Dist.CLIENT)
public class ShadeRangerRenderer extends SkeletonRenderer {
    private static final ResourceLocation SHADE_RANGER_UNIT = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/shade_ranger_unit.png");

    public ShadeRangerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AbstractSkeleton entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (entity.isInvisible() && mc.player != null && entity.isInvisibleTo(mc.player))
            return;
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractSkeleton entity) {
        return SHADE_RANGER_UNIT;
    }
}
