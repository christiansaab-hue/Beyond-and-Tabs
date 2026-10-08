package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.mod.BeyondTabs;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Minecraft-style blocky soldiers. Each unit wears a pixel-art skin in the 64x64 player layout (tools/gen_skins.py,
 * packed into one atlas, slot = the unit's index in UnitDef.ALL). The cubes ride the ragdoll: head on the head
 * particle, body from hip to neck, arms and legs split at elbows and knees (upper half of the limb texture above the
 * joint, lower half below), so units still flop and tumble. The outer layer is drawn half a pixel larger: the hat
 * area as is, the jacket / sleeves / trousers tinted with the team colour. Lit by the world like any mob.
 */
final class BlockyUnits {
    private BlockyUnits() { }

    static final ResourceLocation ATLAS = new ResourceLocation(BeyondTabs.MODID, "textures/unit/atlas.png");
    static final RenderType TYPE = RenderType.entityCutoutNoCull(ATLAS);
    static final float AW = 1024, AH = 512;

    // part layouts (pixels): u, v, w, h, d
    static final int[] HEAD = {0, 0, 8, 8, 8}, HAT = {32, 0, 8, 8, 8}, BODY = {16, 16, 8, 12, 4}, JACKET = {16, 32, 8, 12, 4},
            ARM_R = {40, 16, 4, 12, 4}, SLEEVE_R = {40, 32, 4, 12, 4}, ARM_L = {32, 48, 4, 12, 4}, SLEEVE_L = {48, 48, 4, 12, 4},
            LEG_R = {0, 16, 4, 12, 4}, PANTS_R = {0, 32, 4, 12, 4}, LEG_L = {16, 48, 4, 12, 4}, PANTS_L = {0, 48, 4, 12, 4};

    static VertexConsumer vc; static Matrix4f m; static Matrix3f n; static int light, slotU, slotV;
    static float cr = 1, cg = 1, cb = 1;

    /** Draws a humanoid. Returns the particle array with the hands/elbows moved to where the drawn arms are (for weapons). */
    static float[] humanoid(VertexConsumer out, Matrix4f pose, Matrix3f normal, UnitModels.Ctx c, int slot, int lightCoords, boolean dead) {
        vc = out; m = pose; n = normal; light = lightCoords;
        slotU = (slot % 16) * 64; slotV = (slot / 16) * 64;
        float[] p = c.p;
        float k = c.scale, px = .0525f * k;
        // body frame
        float hx = c.x(Rig.HIP), hy = c.y(Rig.HIP), hz = c.z(Rig.HIP), nx = c.x(Rig.NECK), ny = c.y(Rig.NECK), nz = c.z(Rig.NECK);
        float[] up = norm(nx - hx, ny - hy, nz - hz);
        float[] right = orth(c.x(Rig.SHOULDER_R) - c.x(Rig.SHOULDER_L), c.y(Rig.SHOULDER_R) - c.y(Rig.SHOULDER_L), c.z(Rig.SHOULDER_R) - c.z(Rig.SHOULDER_L), up, c.rx, 0, c.rz);
        int tc = c.shirt; float tr = ((tc >> 16) & 255) / 255f, tg = ((tc >> 8) & 255) / 255f, tb = (tc & 255) / 255f;
        if (dead) { tr *= .6f; tg *= .6f; tb *= .6f; }
        float base = dead ? .6f : 1f;

        // torso: from the hip to the neck, 8 x 12 x 4 pixels
        float bl = dist(hx, hy, hz, nx, ny, nz);
        float[] bc = {(hx + nx) / 2, (hy + ny) / 2, (hz + nz) / 2};
        float bh = Math.max(bl, 12 * px * .8f);
        color(base, base, base); box(bc, right, up, 8 * px, bh, 4 * px, BODY, 0, 12, true, true);
        color(tr, tg, tb); box(bc, right, up, 8 * px + px, bh + px, 4 * px + px, JACKET, 0, 12, true, true);

        // head: 8 px cube on the head particle, turned with the neck
        float ex = c.x(Rig.HEAD), ey = c.y(Rig.HEAD), ez = c.z(Rig.HEAD);
        float[] hup = norm(ex - nx, ey - ny, ez - nz);
        float[] hright = orth(right[0], right[1], right[2], hup, c.rx, 0, c.rz);
        float[] hc = {nx + hup[0] * 4 * px, ny + hup[1] * 4 * px, nz + hup[2] * 4 * px};
        color(base, base, base); box(hc, hright, hup, 8 * px, 8 * px, 8 * px, HEAD, 0, 8, true, true);
        box(hc, hright, hup, 9 * px, 9 * px, 9 * px, HAT, 0, 8, true, true);

        // arms and legs: pushed out so they hang beside the 8-pixel body, split at the joints
        float[] q = p.clone();
        limbs(q, c, right, Rig.SHOULDER_R, Rig.ELBOW_R, Rig.HAND_R, +1, 6 * px, ARM_R, SLEEVE_R, px, tr, tg, tb, base, 1.5f * px);
        limbs(q, c, right, Rig.SHOULDER_L, Rig.ELBOW_L, Rig.HAND_L, -1, 6 * px, ARM_L, SLEEVE_L, px, tr, tg, tb, base, 1.5f * px);
        limbs(q, c, right, Rig.LEG_R, Rig.KNEE_R, Rig.FOOT_R, +1, 2 * px, LEG_R, PANTS_R, px, tr, tg, tb, base, 0);
        limbs(q, c, right, Rig.LEG_L, Rig.KNEE_L, Rig.FOOT_L, -1, 2 * px, LEG_L, PANTS_L, px, tr, tg, tb, base, 0);
        return q;
    }

    /** A two-segment limb (upper / lower half of the texture), offset sideways from the body axis. */
    static void limbs(float[] q, UnitModels.Ctx c, float[] right, int a, int b, int e, int side, float out, int[] uv, int[] ov,
                      float px, float tr, float tg, float tb, float base, float topExtra) {
        float jx = c.x(a), jy = c.y(a), jz = c.z(a);
        // how far the joint already sits from the body axis along "right"
        float hx = c.x(Rig.TORSO), hy = c.y(Rig.TORSO), hz = c.z(Rig.TORSO);
        float already = (jx - hx) * right[0] + (jy - hy) * right[1] + (jz - hz) * right[2];
        float push = side * out - already;
        float ox = right[0] * push, oy = right[1] * push, oz = right[2] * push;
        float[] A = {jx + ox, jy + oy, jz + oz}, B = {c.x(b) + ox, c.y(b) + oy, c.z(b) + oz}, E = {c.x(e) + ox, c.y(e) + oy, c.z(e) + oz};
        q[b * 3] = B[0]; q[b * 3 + 1] = B[1]; q[b * 3 + 2] = B[2]; q[e * 3] = E[0]; q[e * 3 + 1] = E[1]; q[e * 3 + 2] = E[2];
        segment(A, B, right, uv, ov, 0, 6, true, false, px, tr, tg, tb, base, topExtra);
        segment(B, E, right, uv, ov, 6, 12, false, true, px, tr, tg, tb, base, 0);
    }

    static void segment(float[] from, float[] to, float[] right, int[] uv, int[] ov, int r0, int r1, boolean top, boolean bottom,
                        float px, float tr, float tg, float tb, float base, float extra) {
        float[] up = norm(from[0] - to[0], from[1] - to[1], from[2] - to[2]);
        float[] r = orth(right[0], right[1], right[2], up, right[0], 0, right[2]);
        float len = dist(from[0], from[1], from[2], to[0], to[1], to[2]) + extra + px * .5f;
        float[] cc = {(from[0] + to[0]) / 2 + up[0] * extra / 2, (from[1] + to[1]) / 2 + up[1] * extra / 2, (from[2] + to[2]) / 2 + up[2] * extra / 2};
        color(base, base, base); box(cc, r, up, 4 * px, len, 4 * px, uv, r0, r1, top, bottom);
        color(tr, tg, tb); box(cc, r, up, 5 * px, len + px * .5f, 5 * px, ov, r0, r1, top, bottom);
    }

    static void color(float r, float g, float b) { cr = r; cg = g; cb = b; }

    /**
     * A textured box: centre, right / up axes (forward = right x up), sizes in blocks, the part's skin layout and
     * which rows of its side faces to use (for split limbs).
     */
    static void box(float[] c, float[] R, float[] U, float w, float h, float d, int[] uv, int r0, int r1, boolean top, boolean bottom) {
        float[] F = cross(R, U);
        float hw = w / 2, hh = h / 2, hd = d / 2;
        int u = uv[0], v = uv[1], pw = uv[2], ph = uv[3], pd = uv[4];
        float v0 = v + pd + r0 * ph / 12f, v1 = v + pd + r1 * ph / 12f;   // rows of the side faces (12 = full height)
        if (ph == 8) { v0 = v + pd; v1 = v + pd + 8; }
        // corners as (xs, ys, zs) in the box frame; faces listed TL, TR, BR, BL in image order
        face(c, R, U, F, hw, hh, hd, new int[][]{{1, 1, 1}, {-1, 1, 1}, {-1, -1, 1}, {1, -1, 1}}, u + pd, v0, u + pd + pw, v1, F);                        // front
        face(c, R, U, F, hw, hh, hd, new int[][]{{-1, 1, -1}, {1, 1, -1}, {1, -1, -1}, {-1, -1, -1}}, u + pd + pw + pd, v0, u + pd + pw + pd + pw, v1, neg(F));   // back
        face(c, R, U, F, hw, hh, hd, new int[][]{{1, 1, -1}, {1, 1, 1}, {1, -1, 1}, {1, -1, -1}}, u, v0, u + pd, v1, R);                                     // right
        face(c, R, U, F, hw, hh, hd, new int[][]{{-1, 1, 1}, {-1, 1, -1}, {-1, -1, -1}, {-1, -1, 1}}, u + pd + pw, v0, u + pd + pw + pd, v1, neg(R));      // left
        if (top) face(c, R, U, F, hw, hh, hd, new int[][]{{1, 1, -1}, {-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}}, u + pd, v, u + pd + pw, v + pd, U);
        if (bottom) face(c, R, U, F, hw, hh, hd, new int[][]{{1, -1, 1}, {-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}}, u + pd + pw, v, u + pd + pw + pw, v + pd, neg(U));
    }

    static void face(float[] c, float[] R, float[] U, float[] F, float hw, float hh, float hd, int[][] k, float u0, float v0, float u1, float v1, float[] nrm) {
        float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            float x = c[0] + R[0] * k[i][0] * hw + U[0] * k[i][1] * hh + F[0] * k[i][2] * hd;
            float y = c[1] + R[1] * k[i][0] * hw + U[1] * k[i][1] * hh + F[1] * k[i][2] * hd;
            float z = c[2] + R[2] * k[i][0] * hw + U[2] * k[i][1] * hh + F[2] * k[i][2] * hd;
            vc.vertex(m, x, y, z).color(cr, cg, cb, 1f).uv((slotU + uvs[i][0]) / AW, (slotV + uvs[i][1]) / AH)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nrm[0], nrm[1], nrm[2]).endVertex();
        }
    }

    // ---------------------------------------------------------------- vector helpers
    static float dist(float ax, float ay, float az, float bx, float by, float bz) { float x = ax - bx, y = ay - by, z = az - bz; return (float) Math.sqrt(x * x + y * y + z * z); }
    static float[] norm(float x, float y, float z) { float l = (float) Math.sqrt(x * x + y * y + z * z); return l < 1e-6f ? new float[]{0, 1, 0} : new float[]{x / l, y / l, z / l}; }
    static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static float[] neg(float[] a) { return new float[]{-a[0], -a[1], -a[2]}; }

    /** (x,y,z) made perpendicular to `up` and normalised; falls back to (fx,fy,fz) if degenerate. */
    static float[] orth(float x, float y, float z, float[] up, float fx, float fy, float fz) {
        float d = x * up[0] + y * up[1] + z * up[2];
        x -= up[0] * d; y -= up[1] * d; z -= up[2] * d;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-4f) { d = fx * up[0] + fy * up[1] + fz * up[2]; x = fx - up[0] * d; y = fy - up[1] * d; z = fz - up[2] * d; l = (float) Math.sqrt(x * x + y * y + z * z); }
        if (l < 1e-6f) return new float[]{1, 0, 0};
        return new float[]{x / l, y / l, z / l};
    }
}
