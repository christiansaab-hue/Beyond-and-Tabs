package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The Verdant Court Wisp Choir: vanilla's allay body, tinted a pale spring green and drawn translucent and full-bright
 * three times, small, circling a shared centre and bobbing out of step - one unit that reads as a little choir of
 * forest spirits. Only the allay's model and texture are borrowed (AllayModel's own animation wants an Allay), so the
 * wings are flapped here by hand. No allocation per frame: the model is built once, the three copies are pose pushes.
 */
@OnlyIn(Dist.CLIENT)
public class WispChoirRenderer extends MobRenderer<WispChoirUnit, WispChoirRenderer.ChoirModel> {
    private static final ResourceLocation ALLAY = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/allay/allay.png");

    public WispChoirRenderer(EntityRendererProvider.Context context) {
        super(context, new ChoirModel(new AllayModel(context.bakeLayer(ModelLayers.ALLAY))), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(WispChoirUnit unit) {
        return ALLAY;
    }

    // spirits glow: never darker than full light, like the allay itself
    @Override
    protected int getBlockLightLevel(WispChoirUnit unit, BlockPos pos) {
        return 15;
    }

    /** Draws the wrapped allay model three times per call, orbiting and bobbing by the entity's age. */
    public static class ChoirModel extends EntityModel<WispChoirUnit> {
        static final int WISPS = 3;
        static final float SCALE = 0.62f, ORBIT = 0.42f;
        static final float R = 0.78f, G = 1.0f, B = 0.72f, ALPHA = 0.85f;

        private final AllayModel allay;
        @Nullable private final ModelPart rightWing, leftWing;
        private float age = 0;

        ChoirModel(AllayModel allay) {
            super(RenderType::entityTranslucent);
            this.allay = allay;
            ModelPart body = child(allay.root(), "body");
            this.rightWing = body == null ? null : child(body, "right_wing");
            this.leftWing = body == null ? null : child(body, "left_wing");
        }

        @Nullable
        private static ModelPart child(ModelPart part, String name) {
            try {
                return part.getChild(name);
            } catch (java.util.NoSuchElementException e) {
                return null;   // a changed vanilla layer only costs the wing flap
            }
        }

        @Override
        public void setupAnim(WispChoirUnit unit, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.age = ageInTicks;
            // the allay's hover flap (AllayModel.setupAnim), fixed at its idle amplitude
            float flap = Mth.cos(ageInTicks * 20f * ((float) Math.PI / 180f) + limbSwing) * (float) Math.PI * 0.15f;
            if (rightWing != null) {
                rightWing.xRot = 0.43633232f;
                rightWing.yRot = -0.7853982f + flap;
            }
            if (leftWing != null) {
                leftWing.xRot = 0.43633232f;
                leftWing.yRot = 0.7853982f - flap;
            }
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
            for (int i = 0; i < WISPS; i++) {
                float phase = age * 0.06f + i * ((float) Math.PI * 2f / WISPS);
                pose.pushPose();
                // model space is flipped (y down) by LivingEntityRenderer; +y moves the wisp toward the feet
                pose.translate(Mth.cos(phase) * ORBIT, 0.3f + Mth.sin(age * 0.11f + i * 2.1f) * 0.09f, Mth.sin(phase) * ORBIT);
                pose.scale(SCALE, SCALE, SCALE);
                allay.renderToBuffer(pose, buffer, LightTexture.FULL_BRIGHT, overlay, r * R, g * G, b * B, a * ALPHA);
                pose.popPose();
            }
        }
    }
}
