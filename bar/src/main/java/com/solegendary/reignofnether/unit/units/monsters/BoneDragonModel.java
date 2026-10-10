package com.solegendary.reignofnether.unit.units.monsters;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Client only. The vanilla phantom's skeleton (wings, tail, head) driven by our own animation, so it can belong to
 * a non-Phantom entity (PhantomModel's own animation calls Phantom-only methods).
 */
public class BoneDragonModel extends EntityModel<BoneDragonUnit> {

    private final ModelPart root, body, leftWing, leftTip, rightWing, rightTip, tail, tailTip;

    public BoneDragonModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.leftWing = body.getChild("left_wing_base");
        this.leftTip = leftWing.getChild("left_wing_tip");
        this.rightWing = body.getChild("right_wing_base");
        this.rightTip = rightWing.getChild("right_wing_tip");
        this.tail = body.getChild("tail_base");
        this.tailTip = tail.getChild("tail_tip");
    }

    @Override
    public void setupAnim(BoneDragonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // a slow, heavy wingbeat (half the phantom's speed - it is much bigger)
        float f = (entity.getId() * 3 + ageInTicks) * 3.7f * Mth.DEG_TO_RAD;
        leftWing.zRot = Mth.cos(f) * 16f * Mth.DEG_TO_RAD;
        leftTip.zRot = Mth.cos(f) * 16f * Mth.DEG_TO_RAD;
        rightWing.zRot = -leftWing.zRot;
        rightTip.zRot = -leftTip.zRot;
        tail.xRot = -(5f + Mth.cos(f * 2f) * 5f) * Mth.DEG_TO_RAD;
        tailTip.xRot = -(5f + Mth.cos(f * 2f) * 5f) * Mth.DEG_TO_RAD;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
