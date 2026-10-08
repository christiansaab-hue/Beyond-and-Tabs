package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * Minecraft's own kit on our blocky soldiers: armour layers from the vanilla armour textures (leather dyed in the
 * team colour, chainmail, iron, gold, diamond, netherite) and real held items drawn by the game's item renderer
 * (swords, axes, tools, bows, crossbows, rods). Because these are the game's textures, any resource pack the player
 * uses (Faithful and friends) restyles the armies too. Units without an entry keep their hand-made weapon models
 * (Starforge guns, polearms, lutes, staves...).
 */
final class Gear {
    private Gear() { }

    /** Armour per slot ('-' none, L leather, C chainmail, I iron, G gold, D diamond, N netherite): helmet, chest, legs, boots. */
    record Kit(String armour, Item right, Item left, boolean shield, boolean bow) { }

    static final Map<String, Kit> KITS = new HashMap<>();
    static void kit(String id, String armour, Item right) { KITS.put(id, new Kit(armour, right, null, false, false)); }
    static void kit(String id, String armour, Item right, Item left, boolean shield) { KITS.put(id, new Kit(armour, right, left, shield, false)); }
    static void bow(String id, String armour, Item bow) { KITS.put(id, new Kit(armour, null, bow, false, true)); }

    static {
        // Ancient World (tribes, vikings, Greeks and Romans)
        kit("aw_builder_t1", "L---", Items.IRON_HOE);
        kit("aw_protector", "-L--", Items.STONE_SWORD, null, true);
        kit("aw_spear_thrower", "L---", null);
        kit("aw_headbutter", "I---", null);
        kit("aw_hoplite", "GG--", Items.GOLDEN_SWORD, null, true);
        kit("aw_brawler", "-L--", null);
        kit("aw_bone_mage", "----", Items.BONE);
        bow("aw_ice_archer", "CL--", Items.BOW);
        kit("aw_berserker", "----", Items.IRON_AXE, Items.IRON_AXE, false);
        kit("aw_shield_bearer", "II--", Items.IRON_SWORD, null, true);
        kit("aw_sarissa", "GL--", null);
        bow("aw_snake_archer", "LL--", Items.BOW);
        kit("aw_valkyrie", "ICC-", Items.DIAMOND_SWORD);
        kit("aw_jarl", "IILL", Items.IRON_AXE);
        // Kingdoms (medieval, Renaissance, Asia)
        kit("kd_commander", "-GG-", Items.GOLDEN_SWORD);
        kit("kd_builder_t1", "----", Items.STONE_AXE);
        kit("kd_squire", "CLL-", Items.IRON_SWORD, null, true);
        bow("kd_archer", "LL--", Items.BOW);
        kit("kd_fencer", "-L--", Items.IRON_SWORD);
        kit("kd_painter", "----", Items.BRUSH);
        kit("kd_samurai", "-IL-", Items.IRON_SWORD);
        kit("kd_ninja", "----", Items.IRON_SWORD);
        kit("kd_halberd", "ICL-", null);
        kit("kd_musketeer", "LL--", null);
        kit("kd_knight", "IIII", Items.DIAMOND_SWORD, null, true);
        bow("kd_firework_archer", "-L--", Items.CROSSBOW);
        kit("kd_monkey_king", "G---", null);
        kit("kd_healer", "----", Items.BLAZE_ROD);
    }

    static Kit of(UnitDef d) { return KITS.get(d.id()); }

    // ---------------------------------------------------------------- armour

    static final String[] MAT = {"leather", "chainmail", "iron", "gold", "diamond", "netherite"};
    static final Map<String, RenderType> TYPES = new HashMap<>();

    static int matIndex(char c) { return switch (c) { case 'L' -> 0; case 'C' -> 1; case 'I' -> 2; case 'G' -> 3; case 'D' -> 4; case 'N' -> 5; default -> -1; }; }

    static RenderType armourType(int mat, int layer) {
        return TYPES.computeIfAbsent(MAT[mat] + layer, k -> RenderType.armorCutoutNoCull(new ResourceLocation("textures/models/armor/" + MAT[mat] + "_layer_" + layer + ".png")));
    }

    /** Every armour render type (fixed buffers, so units don't flush each other's batches). */
    static Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> buffers() {
        Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> out = new java.util.LinkedHashMap<>();
        out.put(BlockyUnits.TYPE, new com.mojang.blaze3d.vertex.BufferBuilder(1 << 19));
        for (int mi = 0; mi < MAT.length; mi++) for (int l = 1; l <= 2; l++) out.put(armourType(mi, l), new com.mojang.blaze3d.vertex.BufferBuilder(1 << 16));
        return out;
    }

    // ---------------------------------------------------------------- held items

    static final Map<Item, ItemStack> STACKS = new HashMap<>();
    static ItemStack stack(Item i) { return STACKS.computeIfAbsent(i, ItemStack::new); }

    /**
     * Draws the kit's held items in the drawn hands (q: particles with the arms where BlockyUnits drew them).
     * Flat item models are turned about the blade so their face looks toward the camera.
     */
    static void items(Kit kit, UnitModels.Ctx c, float[] q, Matrix4f view, MultiBufferSource buf, int light, float camX, float camY, float camZ) {
        float k = c.scale;
        if (kit.right() != null) {
            float[] h = at(q, Rig.HAND_R), e = at(q, Rig.ELBOW_R);
            float[] a = Weapons.norm(h[0] - e[0], h[1] - e[1], h[2] - e[2]);
            float[] up = Weapons.norm(a[0] * .55f + c.fx * .35f, a[1] * .55f + .85f, a[2] * .55f + c.fz * .35f);
            draw(stack(kit.right()), h, up, .2f, .2f, .9f * k, view, buf, light, camX, camY, camZ);
        }
        if (kit.left() != null) {
            float[] h = at(q, Rig.HAND_L), e = at(q, Rig.ELBOW_L);
            float[] a = Weapons.norm(h[0] - e[0], h[1] - e[1], h[2] - e[2]);
            if (kit.bow()) {   // bow held upright in front, grip in the middle of the model
                float[] up = Weapons.norm(c.fx * .25f, 1, c.fz * .25f);
                draw(stack(kit.left()), h, up, .5f, .5f, .95f * k, view, buf, light, camX, camY, camZ);
            } else {
                float[] up = Weapons.norm(a[0] * .55f + c.fx * .35f, a[1] * .55f + .85f, a[2] * .55f + c.fz * .35f);
                draw(stack(kit.left()), h, up, .2f, .2f, .9f * k, view, buf, light, camX, camY, camZ);
            }
        }
    }

    static float[] at(float[] q, int i) { return new float[]{q[i * 3], q[i * 3 + 1], q[i * 3 + 2]}; }

    /** An item model with its diagonal along `up`, the point (gx, gy) of the model (0..1) in the hand. */
    static void draw(ItemStack st, float[] hand, float[] up, float gx, float gy, float size, Matrix4f view, MultiBufferSource buf, int light,
                     float camX, float camY, float camZ) {
        float[] toCam = {camX - hand[0], camY - hand[1], camZ - hand[2]};
        float[] n = BlockyUnits.orth(toCam[0], toCam[1], toCam[2], up, 1, 0, 0);   // face the camera, turning about the blade
        float[] p = BlockyUnits.cross(n, up);
        float s = .70710678f;
        float[] X = {(up[0] + p[0]) * s, (up[1] + p[1]) * s, (up[2] + p[2]) * s}, Y = {(up[0] - p[0]) * s, (up[1] - p[1]) * s, (up[2] - p[2]) * s};
        if (BlockyUnits.dot(BlockyUnits.cross(X, Y), n) < 0) { float[] t = X; X = Y; Y = t; }   // keep it right-handed
        PoseStack ps = new PoseStack();
        ps.last().pose().set(view);
        Matrix4f basis = new Matrix4f(X[0] * size, X[1] * size, X[2] * size, 0, Y[0] * size, Y[1] * size, Y[2] * size, 0,
                n[0] * size, n[1] * size, n[2] * size, 0, hand[0], hand[1], hand[2], 1);
        ps.last().pose().mul(basis);
        ps.last().normal().set(MatchRenderer.NORMAL).mul(new org.joml.Matrix3f(X[0], X[1], X[2], Y[0], Y[1], Y[2], n[0], n[1], n[2]));
        ps.translate(-gx, -gy, -.5f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(st, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
    }
}
