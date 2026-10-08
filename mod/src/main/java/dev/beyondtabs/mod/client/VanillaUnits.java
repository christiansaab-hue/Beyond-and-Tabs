package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.Snapshot;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Living soldiers drawn like Minecraft mobs: the game's own player model and animation rules (walk swing, sword swipe,
 * bow draw), each unit with its own skin (sliced from our atlas), the outer layer dyed in the team colour, vanilla
 * armour layers and held items. Only the dead switch to the ragdoll (BlockyUnits), so they still fall and tumble.
 */
final class VanillaUnits {
    private VanillaUnits() { }

    static PlayerModel<LivingEntity> model;
    static HumanoidModel<LivingEntity> inner, outer;
    static final ResourceLocation[] SKINS = new ResourceLocation[128];
    static boolean failed;

    static boolean ready() {
        if (model != null) return true;
        if (failed) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            inner = new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
            outer = new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
            try (InputStream in = mc.getResourceManager().getResource(BlockyUnits.ATLAS).orElseThrow().open(); NativeImage atlas = NativeImage.read(in)) {
                for (int i = 0; i < Math.min(SKINS.length, UnitDef.ALL.size()); i++) {
                    int sx = (i % 16) * 64, sy = (i / 16) * 64;
                    if (sy + 64 > atlas.getHeight()) break;
                    NativeImage img = new NativeImage(64, 64, true);
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setPixelRGBA(x, y, atlas.getPixelRGBA(sx + x, sy + y));
                    SKINS[i] = mc.getTextureManager().register(BeyondTabs.MODID + "_unit_" + i, new DynamicTexture(img));
                }
            }
            return true;
        } catch (Exception e) {
            failed = true; model = null;
            BeyondTabs.LOG.warn("Vanilla unit models unavailable, falling back to blocky ragdolls", e);
            return false;
        }
    }

    static final ItemStack SHIELD = new ItemStack(Items.SHIELD);

    /**
     * Draws a living humanoid unit at (x, gy, z) facing yaw (radians, facing = (sin, cos)). Returns hand / elbow /
     * shoulder world positions written into a copy of c.p (for the hand-made weapon models), or null if not drawn.
     */
    static float[] draw(Snapshot.U u, UnitDef d, UnitModels.Ctx c, float x, float gy, float z, float yaw, int team, int light,
                        Matrix4f view, Matrix3f normal, MultiBufferSource buf) {
        if (!ready()) return null;
        int def = Math.max(0, u.def);
        if (def >= SKINS.length || SKINS[def] == null) return null;
        Gear.Kit kit = Gear.of(d);
        float k = c.scale;
        pose(u, d, kit);

        PoseStack ps = new PoseStack();
        ps.last().pose().set(view); ps.last().normal().set(normal);
        place(ps, x, gy, z, yaw, k);

        // skin, then the team-dyed outer layer
        VertexConsumerHolder.of(buf, RenderType.entityCutoutNoCull(SKINS[def]));
        model.setAllVisible(true);
        model.jacket.visible = model.leftSleeve.visible = model.rightSleeve.visible = model.leftPants.visible = model.rightPants.visible = false;
        model.renderToBuffer(ps, VertexConsumerHolder.vc, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        model.setAllVisible(false);
        model.jacket.visible = model.leftSleeve.visible = model.rightSleeve.visible = model.leftPants.visible = model.rightPants.visible = true;
        float tr = ((team >> 16) & 255) / 255f, tg = ((team >> 8) & 255) / 255f, tb = (team & 255) / 255f;
        model.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(SKINS[def])), light, OverlayTexture.NO_OVERLAY, tr, tg, tb, 1);
        model.setAllVisible(true);

        // armour
        if (kit != null && (c.near || c.mid)) armour(ps, buf, kit.armour(), tr, tg, tb, light);

        // held items
        if (kit != null && (c.near || c.mid)) {
            if (kit.right() != null) hand(ps, buf, new ItemStack(kit.right()), false, light);
            if (kit.bow() && kit.left() != null) hand(ps, buf, new ItemStack(kit.left()), false, light);
            else if (kit.left() != null) hand(ps, buf, new ItemStack(kit.left()), true, light);
            if (kit.shield()) hand(ps, buf, SHIELD, true, light);
        }

        if (c.p == null) return null;   // townsfolk: nothing follows their hands
        // where the hands ended up, in world space (hand-made weapons follow them)
        PoseStack w = new PoseStack();
        place(w, x, gy, z, yaw, k);
        float[] q = c.p.clone();
        put(q, w, model.rightArm, Rig.SHOULDER_R, -1, -1); put(q, w, model.rightArm, Rig.ELBOW_R, -1, 4); put(q, w, model.rightArm, Rig.HAND_R, -1, 10);
        put(q, w, model.leftArm, Rig.SHOULDER_L, 1, -1); put(q, w, model.leftArm, Rig.ELBOW_L, 1, 4); put(q, w, model.leftArm, Rig.HAND_L, 1, 10);
        put(q, w, model.head, Rig.HEAD, 0, -4); put(q, w, model.body, Rig.NECK, 0, 0); put(q, w, model.body, Rig.TORSO, 0, 6); put(q, w, model.body, Rig.HIP, 0, 12);
        return q;
    }

    static final UnitModels.Ctx FOLK = new UnitModels.Ctx();
    static final Snapshot.U FOLK_U = new Snapshot.U();
    static final java.util.Map<String, UnitDef> BUILDERS = new java.util.HashMap<>();

    /** The race's first builder (its skin and tools dress the townsfolk). */
    static UnitDef builderOf(String race) {
        return BUILDERS.computeIfAbsent(race, r -> {
            for (UnitDef d : UnitDef.ALL) if (d.race().equals(r) && d.role().equals("builder") && d.body().equals("humanoid")) return d;
            return null;
        });
    }

    /**
     * A cosmetic worker in the race's builder look: walking (legs and arms swinging) or working (tool swings),
     * carrying the builder's own tool. Returns false if the model isn't available (caller draws something else).
     */
    static boolean folk(String race, float x, float gy, float z, float fx, float fz, float phase, boolean walking, float work, int team, int light,
                        Matrix4f view, Matrix3f normal, MultiBufferSource buf) {
        UnitDef d = builderOf(race);
        if (d == null) return false;
        Snapshot.U u = FOLK_U;
        u.def = (short) UnitDef.ALL.indexOf(d); u.id = (int) (phase * 7); u.alive = true;
        u.walkPhase = phase; u.walkAmount = walking ? 1 : 0; u.attack = work > 0 ? work : -1;
        UnitModels.Ctx c = FOLK; c.scale = (float) d.scale() * .95f; c.near = true; c.mid = true; c.p = null;
        return draw(u, d, c, x, gy, z, (float) Math.atan2(fx, fz), team, light, view, normal, buf) != null || model != null;
    }

    /** Same frame vanilla uses for a mob: turned to face, flipped, scaled, feet on the ground. */
    static void place(PoseStack ps, float x, float gy, float z, float yaw, float k) {
        ps.translate(x, gy, z);
        ps.mulPose(Axis.YP.rotationDegrees(180f + (float) Math.toDegrees(yaw)));
        ps.scale(-1, -1, 1);
        float s = .9f * k;
        ps.scale(s, s, s);
        ps.translate(0, -1.501f, 0);
    }

    static void put(float[] q, PoseStack w, ModelPart part, int idx, float px, float py) {
        w.pushPose();
        part.translateAndRotate(w);
        Vector3f v = w.last().pose().transformPosition(new Vector3f(px / 16f, py / 16f, 0));
        w.popPose();
        q[idx * 3] = v.x; q[idx * 3 + 1] = v.y; q[idx * 3 + 2] = v.z;
    }

    static boolean ranged(String wc) {
        return switch (wc) { case "bow", "bow_slow", "bow_poison", "bow_air", "musket", "rocket_volley", "laser_rifle", "rail", "plasma", "magic_lightning", "magic_aoe", "thrown" -> true; default -> false; };
    }

    /** Vanilla's walk, swing and aim poses, driven by the server's walk phase / amount and attack progress. */
    static void pose(Snapshot.U u, UnitDef d, Gear.Kit kit) {
        PlayerModel<LivingEntity> m = model;
        for (ModelPart p : new ModelPart[]{m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) { p.xRot = 0; p.yRot = 0; p.zRot = 0; }
        m.rightArm.x = -5; m.rightArm.y = 2; m.rightArm.z = 0; m.leftArm.x = 5; m.leftArm.y = 2; m.leftArm.z = 0;
        m.crouching = false;
        float t = MatchRenderer.time(), ph = u.walkPhase, amt = Math.min(1, u.walkAmount);
        m.rightArm.xRot = (float) Math.cos(ph + Math.PI) * amt;
        m.leftArm.xRot = (float) Math.cos(ph) * amt;
        m.rightLeg.xRot = (float) Math.cos(ph) * 1.4f * amt;
        m.leftLeg.xRot = (float) Math.cos(ph + Math.PI) * 1.4f * amt;
        // idle breathing
        float bob = t * 1.4f + u.id;
        m.rightArm.zRot = (float) Math.cos(bob) * .05f + .05f; m.leftArm.zRot = -(float) Math.cos(bob) * .05f - .05f;
        m.rightArm.xRot += (float) Math.sin(bob * .9f) * .04f; m.leftArm.xRot -= (float) Math.sin(bob * .9f) * .04f;
        boolean holdsR = kit != null && (kit.right() != null || kit.bow()), holdsL = kit != null && (kit.shield() || (kit.left() != null && !kit.bow()));
        if (holdsR || kit == null) m.rightArm.xRot = m.rightArm.xRot * .5f - (float) Math.PI / 10;
        if (holdsL) m.leftArm.xRot = m.leftArm.xRot * .5f - (float) Math.PI / 10;
        if (kit != null && kit.shield()) { m.leftArm.xRot = m.leftArm.xRot * .5f - .94f; m.leftArm.yRot = (float) Math.PI / 6; }
        float a = u.attack;
        String wc = d.weaponClass();
        if (ranged(wc) && a >= 0) {   // aim: both arms forward (bow draw / rifle)
            m.rightArm.yRot = -.1f; m.leftArm.yRot = .5f;
            m.rightArm.xRot = -(float) Math.PI / 2; m.leftArm.xRot = -(float) Math.PI / 2;
        } else if (a >= 0 && a <= 1) {   // vanilla's sword swipe
            float f = a;
            m.body.yRot = (float) Math.sin(Math.sqrt(f) * Math.PI * 2) * .2f;
            m.rightArm.z = (float) Math.sin(m.body.yRot) * 5; m.rightArm.x = -(float) Math.cos(m.body.yRot) * 5;
            m.leftArm.z = -(float) Math.sin(m.body.yRot) * 5; m.leftArm.x = (float) Math.cos(m.body.yRot) * 5;
            m.rightArm.yRot += m.body.yRot; m.leftArm.yRot += m.body.yRot; m.leftArm.xRot += m.body.yRot;
            float g = 1 - f; g = g * g * g * g; g = 1 - g;
            float h = (float) Math.sin(g * Math.PI), i = (float) Math.sin(f * Math.PI) * -(m.head.xRot - .7f) * .75f;
            m.rightArm.xRot -= h * 1.2f + i; m.rightArm.yRot += m.body.yRot * 2; m.rightArm.zRot += (float) Math.sin(f * Math.PI) * -.4f;
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body); m.leftSleeve.copyFrom(m.leftArm); m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg); m.rightPants.copyFrom(m.rightLeg);
    }

    static void armour(PoseStack ps, MultiBufferSource buf, String kit, float tr, float tg, float tb, int light) {
        if (kit == null) return;
        for (int slot = 0; slot < 4; slot++) {
            int mat = Gear.matIndex(kit.charAt(slot));
            if (mat < 0) continue;
            HumanoidModel<LivingEntity> a = slot == 2 ? inner : outer;
            model.copyPropertiesTo(a);
            a.setAllVisible(false);
            switch (slot) {
                case 0 -> { a.head.visible = true; a.hat.visible = true; }
                case 1 -> { a.body.visible = true; a.rightArm.visible = true; a.leftArm.visible = true; }
                case 2 -> { a.body.visible = true; a.rightLeg.visible = true; a.leftLeg.visible = true; }
                default -> { a.rightLeg.visible = true; a.leftLeg.visible = true; }
            }
            float r = mat == 0 ? tr : 1, g = mat == 0 ? tg : 1, b = mat == 0 ? tb : 1;
            a.renderToBuffer(ps, buf.getBuffer(Gear.armourType(mat, slot == 2 ? 2 : 1)), light, OverlayTexture.NO_OVERLAY, r, g, b, 1);
        }
    }

    /** Vanilla's held-item transform (ItemInHandLayer). */
    static void hand(PoseStack ps, MultiBufferSource buf, ItemStack st, boolean left, int light) {
        ps.pushPose();
        model.translateToHand(left ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90)); ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.translate((left ? -1 : 1) / 16f, .125f, -.625f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(st, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
        ps.popPose();
    }

    /** Small holder so the first render call reads clearly. */
    static final class VertexConsumerHolder {
        static com.mojang.blaze3d.vertex.VertexConsumer vc;
        static void of(MultiBufferSource b, RenderType t) { vc = b.getBuffer(t); }
    }
}
