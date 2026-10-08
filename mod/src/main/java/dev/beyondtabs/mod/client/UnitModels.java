package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.UnitDef;

/**
 * Chunky low-poly soldiers skinned onto the ragdoll particles: big round heads with dot eyes, egg-shaped bodies in
 * team colours, stubby limbs, faction headgear and a weapon that follows the hands. Non-humanoid bodies (mounts,
 * war machines, boats, balloons, the dragon) are built around their particle hull. All original art, all one style.
 */
final class UnitModels {
    private UnitModels() { }

    static final float UPX = 0, UPY = 1, UPZ = 0;

    /** Per-unit drawing context (reused; the renderer is single-threaded). */
    static final class Ctx {
        float[] p; int team; float fx, fz, rx, rz;   // forward / right (horizontal, unit length)
        int sides, headSl, headSt; boolean near, mid; float t; float scale = 1;
        UnitDef d; String faction = ""; String key = "";
        int shirt, trousers, skin;
        float x(int i) { return p[i * 3]; } float y(int i) { return p[i * 3 + 1]; } float z(int i) { return p[i * 3 + 2]; }
    }

    static String faction(String tabsKey) {
        if (tabsKey == null) return "";
        String[] s = tabsKey.split("_");
        return s.length > 1 ? s[1] : "";
    }

    // ---------------------------------------------------------------- humanoids

    static void humanoid(Mesh m, Ctx c) {
        float[] p = c.p;
        // facing from the shoulder line, so a ragdoll that spins round faces where its body faces
        float sx = c.x(Rig.SHOULDER_R) - c.x(Rig.SHOULDER_L), sz = c.z(Rig.SHOULDER_R) - c.z(Rig.SHOULDER_L), sl = (float) Math.sqrt(sx * sx + sz * sz);
        if (sl > 1e-3f) { c.rx = sx / sl; c.rz = sz / sl; c.fx = -c.rz; c.fz = c.rx; }
        float k = c.scale; int sd = c.sides;
        boolean commander = "commander".equals(c.d.role()), hero = "hero".equals(c.d.role());

        // legs: trousers to the knee, then boots
        limb(m, c, Rig.LEG_L, Rig.KNEE_L, .15f * k, .125f * k, c.trousers);
        limb(m, c, Rig.LEG_R, Rig.KNEE_R, .15f * k, .125f * k, c.trousers);
        limb(m, c, Rig.KNEE_L, Rig.FOOT_L, .125f * k, .105f * k, c.trousers);
        limb(m, c, Rig.KNEE_R, Rig.FOOT_R, .125f * k, .105f * k, c.trousers);
        foot(m, c, Rig.FOOT_L, k); foot(m, c, Rig.FOOT_R, k);
        if (c.near) for (int[] lg : new int[][]{{Rig.KNEE_L, Rig.FOOT_L}, {Rig.KNEE_R, Rig.FOOT_R}}) {   // boot cuffs
            float t0 = .45f, bx = c.x(lg[0]) + (c.x(lg[1]) - c.x(lg[0])) * t0, by = c.y(lg[0]) + (c.y(lg[1]) - c.y(lg[0])) * t0, bz = c.z(lg[0]) + (c.z(lg[1]) - c.z(lg[0])) * t0;
            m.prismW(bx, by, bz, c.x(lg[1]), c.y(lg[1]) + .02f * k, c.z(lg[1]), .125f * k, .12f * k, sd, Look.LEATHER);
            m.prismW(bx, by - .02f * k, bz, bx, by + .05f * k, bz, .14f * k, .14f * k, sd, Look.darker(Look.LEATHER, .25f));
        }

        // body: an egg from the hips to the neck
        float hx = c.x(Rig.HIP), hy = c.y(Rig.HIP), hz = c.z(Rig.HIP);
        float tx = c.x(Rig.TORSO), ty = c.y(Rig.TORSO), tz = c.z(Rig.TORSO);
        float nx = c.x(Rig.NECK), ny = c.y(Rig.NECK), nz = c.z(Rig.NECK);
        float dx = hx - tx, dy = hy - ty, dz = hz - tz;
        m.prismW(hx + dx * .35f, hy + dy * .35f, hz + dz * .35f, hx, hy, hz, .2f * k, .34f * k, sd, c.shirt, c.rx, 0, c.rz, .85f);
        m.prismW(hx, hy, hz, tx, ty, tz, .34f * k, .37f * k, sd, c.shirt, c.rx, 0, c.rz, .85f);
        m.prismW(tx, ty, tz, nx, ny - .02f * k, nz, .37f * k, .2f * k, sd, c.shirt, c.rx, 0, c.rz, .85f);
        // tunic skirt flaring over the thighs
        m.prismW(hx, hy + .02f * k, hz, hx, hy - .3f * k, hz, .33f * k, .38f * k, sd, Look.darker(c.shirt, .12f), c.rx, 0, c.rz, .85f);
        if (c.near) {   // belt with a buckle, collar, chest emblem
            float bx = hx + (tx - hx) * .25f, by = hy + (ty - hy) * .25f, bz = hz + (tz - hz) * .25f;
            m.prismW(bx, by - .04f * k, bz, bx, by + .05f * k, bz, .36f * k, .36f * k, sd, Look.LEATHER, c.rx, 0, c.rz, .87f);
            float fx = bx + c.fx * .32f * k, fz = bz + c.fz * .32f * k;
            m.prismW(fx - c.fx * .02f * k, by, fz - c.fz * .02f * k, fx + c.fx * .03f * k, by, fz + c.fz * .03f * k, .07f * k, .07f * k, 4, Look.GOLD);
            m.prismW(nx, ny - .08f * k, nz, nx, ny + .02f * k, nz, .23f * k, .2f * k, sd, Look.darker(c.shirt, .25f), c.rx, 0, c.rz, .9f);
            float ex2 = tx + c.fx * .33f * k, ez2 = tz + c.fz * .33f * k;
            m.prismW(ex2, ty + .02f * k, ez2, ex2 + c.fx * .03f * k, ty + .02f * k, ez2 + c.fz * .03f * k, .1f * k, .1f * k, 6, Look.lighter(c.shirt, .35f));
        }
        if (hero || commander) cape(m, c, k, Look.darker(c.shirt, .25f));

        // arms: sleeves, bare forearms, round fists
        limb(m, c, Rig.SHOULDER_L, Rig.ELBOW_L, .10f * k, .085f * k, c.shirt);
        limb(m, c, Rig.SHOULDER_R, Rig.ELBOW_R, .10f * k, .085f * k, c.shirt);
        limb(m, c, Rig.ELBOW_L, Rig.HAND_L, .08f * k, .07f * k, c.skin);
        limb(m, c, Rig.ELBOW_R, Rig.HAND_R, .08f * k, .07f * k, c.skin);
        float fist = ("melee_push".equals(c.d.weaponClass()) ? .12f : .09f) * k;
        m.ballW(c.x(Rig.HAND_L), c.y(Rig.HAND_L), c.z(Rig.HAND_L), fist, fist, fist, sd, 5, c.skin, 1, 0);
        m.ballW(c.x(Rig.HAND_R), c.y(Rig.HAND_R), c.z(Rig.HAND_R), fist, fist, fist, sd, 5, c.skin, 1, 0);
        if (c.d.tier() >= 2 && c.near) {   // pauldrons for veterans
            for (int s : new int[]{Rig.SHOULDER_L, Rig.SHOULDER_R})
                m.ballW(c.x(s), c.y(s) + .02f * k, c.z(s), .13f * k, .09f * k, .13f * k, 6, 3, Look.IRON, c.fz, c.fx);
        }

        // head: big and round, dot eyes, a hint of cheek
        float ex = c.x(Rig.HEAD), ey = c.y(Rig.HEAD) + .04f * k, ez = c.z(Rig.HEAD), hr = .3f * k;
        m.prismW(nx, ny - .03f * k, nz, ex, ey - hr * .7f, ez, .09f * k, .09f * k, 4, c.skin);
        m.ballW(ex, ey, ez, hr, hr * .96f, hr, c.headSl, c.headSt, c.skin, c.fz, c.fx);
        float fwd = hr * .93f, side = hr * .36f;
        for (int s = -1; s <= 1; s += 2) {
            float px = ex + c.fx * fwd + c.rx * side * s, pz = ez + c.fz * fwd + c.rz * side * s;
            m.ballW(px, ey + hr * .1f, pz, .05f * k, .065f * k, .04f * k, 8, 5, Look.EYE, c.fz, c.fx);
            if (c.near) m.ballW(px + c.fx * .03f * k - c.rx * .015f * k, ey + hr * .17f, pz + c.fz * .03f * k - c.rz * .015f * k, .016f * k, .016f * k, .012f * k, 5, 3, Look.EYE_SHINE, c.fz, c.fx);
            if (c.near) {
                float bx = ex + c.fx * hr * .82f + c.rx * hr * .55f * s, bz = ez + c.fz * hr * .82f + c.rz * hr * .55f * s;
                m.ballW(bx, ey - hr * .22f, bz, .055f * k, .03f * k, .04f * k, 6, 3, Look.mix(c.skin, Look.BLUSH, .6f), c.fz, c.fx);
            }
        }
        headgear(m, c, ex, ey, ez, hr, commander);
        if ("STARFORGE".equals(c.faction)) armour(m, c, k);
        Weapons.draw(m, c);
    }

    static void limb(Mesh m, Ctx c, int a, int b, float ra, float rb, int col) {
        m.prismW(c.x(a), c.y(a), c.z(a), c.x(b), c.y(b), c.z(b), ra, rb, c.sides, col);
    }

    static void foot(Mesh m, Ctx c, int f, float k) {
        float x = c.x(f), y = c.y(f), z = c.z(f);
        m.prismW(x - c.fx * .06f * k, y, z - c.fz * .06f * k, x + c.fx * .17f * k, y, z + c.fz * .17f * k, .085f * k, .075f * k, 4, Look.LEATHER, c.rx, 0, c.rz, .6f);
    }

    static void cape(Mesh m, Ctx c, float k, int col) {
        float lx = c.x(Rig.SHOULDER_L) - c.fx * .2f * k, ly = c.y(Rig.SHOULDER_L), lz = c.z(Rig.SHOULDER_L) - c.fz * .2f * k;
        float rx = c.x(Rig.SHOULDER_R) - c.fx * .2f * k, ry = c.y(Rig.SHOULDER_R), rz = c.z(Rig.SHOULDER_R) - c.fz * .2f * k;
        float sway = (float) Math.sin(c.t * 3) * .08f * k, back = .35f * k + sway;
        float bly = c.y(Rig.KNEE_L) + .1f * k, bry = c.y(Rig.KNEE_R) + .1f * k;
        m.quad4(lx, ly, lz, rx, ry, rz, rx - c.fx * back - c.rx * .08f * k, bry, rz - c.fz * back - c.rz * .08f * k,
                lx - c.fx * back + c.rx * .08f * k, bly, lz - c.fz * back + c.rz * .08f * k, col);
    }

    static void headgear(Mesh m, Ctx c, float x, float y, float z, float r, boolean commander) {
        String f = c.faction, key = c.key;
        int sd = Math.max(6, c.sides);
        if ("STARFORGE".equals(f)) { sciHelmet(m, c, x, y, z, r, commander); return; }
        if (commander) {   // crown: gold band with points
            m.prismW(x, y + r * .55f, z, x, y + r * .85f, z, r * .62f, r * .66f, 8, Look.GOLD);
            if (c.near) for (int i = 0; i < 5; i++) {
                double a = i * 2 * Math.PI / 5; float px = x + (float) Math.cos(a) * r * .55f, pz = z + (float) Math.sin(a) * r * .55f;
                m.prismW(px, y + r * .85f, pz, px, y + r * 1.12f, pz, r * .1f, 0, 4, Look.GOLD);
            }
            return;
        }
        switch (f) {
            case "TRIBAL" -> {   // headband with feathers
                m.prismW(x, y + r * .25f, z, x, y + r * .45f, z, r * 1.01f, r * .97f, sd, Look.CLAY);
                if (c.near) {
                    float bx = x - c.fx * r * .8f, bz = z - c.fz * r * .8f;
                    m.prismW(bx, y + r * .4f, bz, bx - c.fx * r * .2f, y + r * 1.3f, bz - c.fz * r * .2f, r * .14f, .01f, 4, Look.BONE, c.rx, 0, c.rz, .3f);
                    m.prismW(bx + c.rx * r * .2f, y + r * .4f, bz + c.rz * r * .2f, bx + c.rx * r * .35f - c.fx * r * .2f, y + r * 1.1f, bz + c.rz * r * .35f - c.fz * r * .2f, r * .12f, .01f, 4, Look.TILE_RED, c.rx, 0, c.rz, .3f);
                }
            }
            case "FARMER" -> {   // straw hat
                m.prismW(x, y + r * .45f, z, x, y + r * .55f, z, r * 1.6f, r * 1.5f, sd, Look.THATCH);
                m.prismW(x, y + r * .55f, z, x, y + r * 1.0f, z, r * .75f, r * .6f, sd, Look.THATCH_DARK);
            }
            case "ANCIENT" -> {   // bronze helmet with a crest
                m.ballW(x, y + r * .12f, z, r * 1.06f, r * .95f, r * 1.06f, c.headSl, c.headSt, 0xC9973F, c.fz, c.fx);
                m.prismW(x - c.fx * r * .9f, y + r * 1.05f, z - c.fz * r * .9f, x + c.fx * r * .5f, y + r * 1.05f, z + c.fz * r * .5f, r * .28f, r * .2f, 4, Look.TILE_RED, 0, 1, 0, .25f);
            }
            case "VIKING" -> {   // iron cap with horns
                m.ballW(x, y + r * .15f, z, r * 1.05f, r * .9f, r * 1.05f, c.headSl, c.headSt, Look.IRON, c.fz, c.fx);
                if (c.near) for (int s = -1; s <= 1; s += 2) {
                    float bx = x + c.rx * r * .85f * s, bz = z + c.rz * r * .85f * s;
                    float mx = x + c.rx * r * 1.35f * s, mz = z + c.rz * r * 1.35f * s;
                    m.prismW(bx, y + r * .45f, bz, mx, y + r * .75f, mz, r * .16f, r * .1f, 5, Look.BONE);
                    m.prismW(mx, y + r * .75f, mz, mx + c.fx * r * .1f, y + r * 1.25f, mz + c.fz * r * .1f, r * .1f, 0, 5, Look.BONE);
                }
            }
            case "MEDIEVAL" -> {
                if (key.contains("PRIEST") || key.contains("BANJO")) {   // soft hood / cap
                    m.ballW(x, y + r * .25f, z, r * 1.05f, r * .85f, r * 1.05f, c.headSl, c.headSt, Look.darker(c.shirt, .15f), c.fz, c.fx);
                } else {   // kettle helmet
                    m.ballW(x, y + r * .2f, z, r * 1.05f, r * .9f, r * 1.05f, c.headSl, c.headSt, Look.IRON, c.fz, c.fx);
                    m.prismW(x, y + r * .25f, z, x, y + r * .33f, z, r * 1.4f, r * 1.35f, sd, Look.IRON_DARK);
                }
            }
            case "ASIA" -> {
                if (key.contains("NINJA")) m.ballW(x, y + r * .05f, z, r * 1.05f, r * 1.0f, r * 1.05f, c.headSl, c.headSt, 0x2B2B33, c.fz, c.fx);
                else if (key.contains("SAMURAI")) {
                    m.ballW(x, y + r * .2f, z, r * 1.07f, r * .9f, r * 1.07f, c.headSl, c.headSt, 0x3A3A44, c.fz, c.fx);
                    m.prismW(x + c.fx * r * .7f, y + r * .7f, z + c.fz * r * .7f, x + c.fx * r * .9f, y + r * 1.3f, z + c.fz * r * .9f, r * .3f, r * .02f, 4, Look.GOLD, c.rx, 0, c.rz, .2f);
                } else if (key.contains("MONK")) { /* shaved head */ }
                else m.prismW(x, y + r * .4f, z, x, y + r * 1.2f, z, r * 1.7f, 0, sd, Look.THATCH);   // conical hat
            }
            case "RENAISSANCE" -> {   // beret with a feather
                m.ballW(x - c.rx * r * .15f, y + r * .65f, z - c.rz * r * .15f, r * 1.05f, r * .35f, r * 1.05f, c.headSl, c.headSt, Look.darker(c.shirt, .35f), c.fz, c.fx);
                if (c.near) m.prismW(x + c.rx * r * .5f, y + r * .8f, z + c.rz * r * .5f, x + c.rx * r * .7f - c.fx * r * .7f, y + r * 1.5f, z + c.rz * r * .7f - c.fz * r * .7f, r * .14f, .01f, 4, Look.CLOTH, c.rx, 0, c.rz, .3f);
            }
            default -> { }
        }
    }

    /** Starforge helmet: a rounded shell, a glowing visor band, a crest for officers, an antenna for walkers. */
    static void sciHelmet(Mesh m, Ctx c, float x, float y, float z, float r, boolean commander) {
        boolean mech = "large".equals(c.d.body());
        int shell = mech ? 0xC9CED6 : 0xE8EBEF, visor = commander ? 0xFFC86A : 0x6FE3FF;
        m.ballW(x, y + r * .05f, z, r * 1.08f, r * 1.04f, r * 1.08f, c.headSl, c.headSt, shell, c.fz, c.fx);
        float vx = x + c.fx * r * .78f, vz = z + c.fz * r * .78f;
        boolean g = m.glow; m.glow = true;
        m.prismW(vx - c.rx * r * .62f, y + r * .12f, vz - c.rz * r * .62f, vx + c.rx * r * .62f, y + r * .12f, vz + c.rz * r * .62f, r * .2f, r * .2f, 4, visor, c.fx, 0, c.fz, .45f);
        m.glow = g;
        if (commander) m.prismW(x - c.fx * r * .8f, y + r * 1.05f, z - c.fz * r * .8f, x + c.fx * r * .5f, y + r * 1.1f, z + c.fz * r * .5f, r * .2f, r * .12f, 4, Look.GOLD, 0, 1, 0, .25f);
        if (c.near) m.prismW(x - c.rx * r * .8f, y + r * .4f, z - c.rz * r * .8f, x - c.rx * r * .9f, y + r * (mech ? 1.9f : 1.4f), z - c.rz * r * .9f, r * .05f, r * .03f, 4, Look.IRON_DARK);
        if (c.near) { m.glow = true; m.ballW(x - c.rx * r * .9f, y + r * (mech ? 1.95f : 1.45f), z - c.rz * r * .9f, r * .07f, r * .07f, r * .07f, 5, 3, 0xFF5050, 1, 0); m.glow = g; }
    }

    /** Starforge plating: chest plate with a team stripe, shoulder pads and knee guards (heavier on walkers). */
    static void armour(Mesh m, Ctx c, float k) {
        boolean mech = "large".equals(c.d.body());
        int plate = mech ? 0xC9CED6 : 0xEDEFF2;
        float tx = c.x(Rig.TORSO), ty = c.y(Rig.TORSO), tz = c.z(Rig.TORSO);
        float px = tx + c.fx * .3f * k, pz = tz + c.fz * .3f * k;
        m.prismW(px - c.fx * .06f * k, ty + .12f * k, pz - c.fz * .06f * k, px + c.fx * .05f * k, ty + .12f * k, pz + c.fz * .05f * k, .3f * k, .26f * k, 6, plate, c.rx, 0, c.rz, 1.1f);
        m.prismW(px + c.fx * .04f * k, ty + .1f * k, pz + c.fz * .04f * k, px + c.fx * .07f * k, ty + .1f * k, pz + c.fz * .07f * k, .07f * k, .07f * k, 4, c.shirt, c.rx, 0, c.rz, 3f);
        for (int s2 : new int[]{Rig.SHOULDER_L, Rig.SHOULDER_R})
            m.ballW(c.x(s2), c.y(s2) + .03f * k, c.z(s2), (mech ? .17f : .14f) * k, .09f * k, (mech ? .17f : .14f) * k, 6, 3, plate, c.fz, c.fx);
        for (int kn : new int[]{Rig.KNEE_L, Rig.KNEE_R})
            m.ballW(c.x(kn) + c.fx * .06f * k, c.y(kn), c.z(kn) + c.fz * .06f * k, .1f * k, .1f * k, .08f * k, 6, 3, plate, c.fz, c.fx);
        if (mech) {   // backpack reactor glowing between the shoulders
            float bx = tx - c.fx * .35f * k, bz = tz - c.fz * .35f * k;
            m.prismW(bx, ty - .1f * k, bz, bx, ty + .35f * k, bz, .2f * k, .2f * k, 4, 0x5A606B);
            boolean g = m.glow; m.glow = true; m.ballW(bx - c.fx * .2f * k, ty + .12f * k, bz - c.fz * .2f * k, .08f * k, .12f * k, .08f * k, 6, 3, 0x6FE3FF, c.fz, c.fx); m.glow = g;
        }
    }

    // ---------------------------------------------------------------- Starforge machines
    static void hoverbike(Mesh m, Ctx c, float x, float g, float z, float k) {
        float hy = g + .55f * k + (float) Math.sin(c.t * 3 + x) * .05f * k;
        m.ballW(x, hy, z, .32f * k, .2f * k, .95f * k, c.headSl, c.headSt, 0xE8EBEF, c.fz, c.fx);
        m.ballW(wx(c, x, .1f * k, 0), hy + .12f * k, wz(c, z, .1f * k, 0), .26f * k, .1f * k, .6f * k, c.headSl, c.headSt, c.shirt, c.fz, c.fx);
        boolean gl = m.glow; m.glow = true;
        for (int s = -1; s <= 1; s += 2) m.ballW(wx(c, x, -.7f * k, s * .25f * k), hy - .05f * k, wz(c, z, -.7f * k, s * .25f * k), .08f * k, .08f * k, .08f * k, 5, 3, 0x6FE3FF, 1, 0);
        m.glow = gl;
        float ry = hy + .2f * k;
        m.prismW(x, ry, z, x, ry + .5f * k, z, .24f * k, .18f * k, c.sides, c.shirt, c.rx, 0, c.rz, .85f);
        sciHelmet(m, c, x, ry + .72f * k, z, .22f * k, false);
        float lx = wx(c, x, -.1f * k, .25f * k), lz = wz(c, z, -.1f * k, .25f * k);
        m.prismW(lx, ry + .3f * k, lz, wx(c, x, 2.2f * k, .3f * k), ry + .45f * k, wz(c, z, 2.2f * k, .3f * k), .06f * k, .015f * k, c.sides, 0xC9CED6);
        m.glow = true; m.prismW(wx(c, x, 2.0f * k, .3f * k), ry + .44f * k, wz(c, z, 2.0f * k, .3f * k), wx(c, x, 2.25f * k, .3f * k), ry + .45f * k, wz(c, z, 2.25f * k, .3f * k), .04f * k, 0, 4, 0x6FE3FF); m.glow = gl;
    }

    static void gunship(Mesh m, Ctx c, float x, float y, float z, float k) {
        float hy = y + 1.2f * k + (float) Math.sin(c.t * 2 + x) * .08f * k;
        m.ballW(x, hy, z, .45f * k, .25f * k, .8f * k, c.headSl, c.headSt, 0xE8EBEF, c.fz, c.fx);
        m.ballW(wx(c, x, .45f * k, 0), hy + .05f * k, wz(c, z, .45f * k, 0), .2f * k, .14f * k, .25f * k, 6, 4, 0x2A3B52, c.fz, c.fx);
        for (int s = -1; s <= 1; s += 2) for (int f = -1; f <= 1; f += 2) {   // four ducted rotors on arms
            float ax = wx(c, x, f * .45f * k, s * .75f * k), az = wz(c, z, f * .45f * k, s * .75f * k);
            m.prismW(x, hy, z, ax, hy + .05f * k, az, .05f * k, .05f * k, 4, Look.IRON_DARK);
            m.prismW(ax, hy, az, ax, hy + .1f * k, az, .32f * k, .32f * k, 8, c.shirt);
            float spin = c.t * 25;
            m.prismW(ax + (float) Math.cos(spin) * .28f * k, hy + .12f * k, az + (float) Math.sin(spin) * .28f * k, ax - (float) Math.cos(spin) * .28f * k, hy + .12f * k, az - (float) Math.sin(spin) * .28f * k, .03f * k, .03f * k, 4, Look.IRON_DARK);
        }
        for (int s = -1; s <= 1; s += 2) m.prismW(wx(c, x, .2f * k, s * .3f * k), hy - .2f * k, wz(c, z, .2f * k, s * .3f * k), wx(c, x, .8f * k, s * .3f * k), hy - .2f * k, wz(c, z, .8f * k, s * .3f * k), .05f * k, .04f * k, 5, Look.IRON_DARK);
        boolean gl = m.glow; m.glow = true; m.ballW(x, hy - .22f * k, z, .1f * k, .06f * k, .1f * k, 6, 3, 0xFF5050, 1, 0); m.glow = gl;
    }

    static void rover(Mesh m, Ctx c, float x, float g, float z, float k) {
        float L = .6f * k, W = .42f * k;
        box(m, c, x, g + .45f * k, z, L, .2f * k, W, 0xE8EBEF);
        box(m, c, wx(c, x, .2f * k, 0), g + .75f * k, wz(c, z, .2f * k, 0), L * .45f, .15f * k, W * .8f, 0x2A3B52);
        for (int a = -1; a <= 1; a += 2) for (int s = -1; s <= 1; s += 2) wheel(m, c, wx(c, x, a * L * .65f, s * (W + .05f * k)), g + .22f * k, wz(c, z, a * L * .65f, s * (W + .05f * k)), .22f * k);
        // the construction arm with a glowing nano-emitter
        float bx = wx(c, x, -.3f * k, 0), bz = wz(c, z, -.3f * k, 0), ex = wx(c, x, .3f * k, .2f * k), ez = wz(c, z, .3f * k, .2f * k);
        m.prismW(bx, g + .65f * k, bz, bx, g + 1.3f * k, bz, .06f * k, .05f * k, 4, Look.IRON_DARK);
        m.prismW(bx, g + 1.3f * k, bz, ex, g + 1.45f * k, ez, .05f * k, .04f * k, 4, 0xFFC83A);
        boolean gl = m.glow; m.glow = true; m.ballW(ex, g + 1.45f * k, ez, .07f * k, .07f * k, .07f * k, 5, 3, 0x6FE3FF, 1, 0); m.glow = gl;
        box(m, c, x, g + .6f * k, z, L * .3f, .03f * k, W * 1.02f, c.shirt);
    }

    static void plasmaTank(Mesh m, Ctx c, float x, float g, float z, float k) {
        float L = 1.0f * k, W = .65f * k;
        for (int s = -1; s <= 1; s += 2) box(m, c, wx(c, x, 0, s * W * .8f), g + .25f * k, wz(c, z, 0, s * W * .8f), L, .2f * k, .22f * k, 0x3A3F48);   // tracks
        box(m, c, x, g + .55f * k, z, L * .85f, .2f * k, W * .75f, 0xE8EBEF);
        m.ballW(x, g + .85f * k, z, .45f * k, .22f * k, .5f * k, c.headSl, c.headSt, 0xC9CED6, c.fz, c.fx);
        m.prismW(wx(c, x, .2f * k, 0), g + .9f * k, wz(c, z, .2f * k, 0), wx(c, x, 1.5f * k, 0), g + .95f * k, wz(c, z, 1.5f * k, 0), .1f * k, .08f * k, 6, 0x5A606B);
        boolean gl = m.glow; m.glow = true;
        for (int i = 0; i < 3; i++) { float a = .55f + i * .3f; m.prismW(wx(c, x, a * k, 0), g + .92f * k, wz(c, z, a * k, 0), wx(c, x, (a + .08f) * k, 0), g + .92f * k, wz(c, z, (a + .08f) * k, 0), .12f * k, .12f * k, 6, 0x9CFF7A); }
        m.glow = gl;
        box(m, c, x, g + .66f * k, z, L * .86f, .03f * k, W * .76f, c.shirt);
    }

    static void ionArtillery(Mesh m, Ctx c, float x, float g, float z, float k) {
        float L = .8f * k, W = .55f * k;
        box(m, c, x, g + .4f * k, z, L, .14f * k, W, 0xE8EBEF);
        for (int a = -1; a <= 1; a += 2) for (int s = -1; s <= 1; s += 2) wheel(m, c, wx(c, x, a * L * .65f, s * (W + .06f * k)), g + .28f * k, wz(c, z, a * L * .65f, s * (W + .06f * k)), .28f * k);
        float py = g + .7f * k;
        m.prismW(x, g + .5f * k, z, x, py, z, .2f * k, .16f * k, 6, 0x5A606B);
        m.prismW(wx(c, x, -.3f * k, 0), py, wz(c, z, -.3f * k, 0), wx(c, x, 1.0f * k, 0), py + .9f * k, wz(c, z, 1.0f * k, 0), .14f * k, .1f * k, 6, 0xC9CED6);
        boolean gl = m.glow; m.glow = true;
        for (int i = 0; i < 4; i++) { float t = .1f + i * .22f; m.prismW(wx(c, x, (-.3f + t * 1.3f) * k, 0), py + t * .9f * k, wz(c, z, (-.3f + t * 1.3f) * k, 0), wx(c, x, (-.25f + t * 1.3f) * k, 0), py + (t + .04f) * .9f * k, wz(c, z, (-.25f + t * 1.3f) * k, 0), .17f * k, .17f * k, 6, 0x6FE3FF); }
        m.glow = gl;
    }

    // ---------------------------------------------------------------- other bodies

    /** Mounts, war machines, boats, balloons and dragons, built around the particle hull. */
    static void hull(Mesh m, Ctx c, float groundY, float yaw, float walkPhase, float walk) {
        float[] p = c.p; int n = p.length / 3;
        float cx = 0, cy = 0, cz = 0, top = -1e9f;
        for (int i = 0; i < n; i++) { cx += p[i * 3]; cy += p[i * 3 + 1]; cz += p[i * 3 + 2]; top = Math.max(top, p[i * 3 + 1]); }
        cx /= n; cy /= n; cz /= n;
        // forward: front half minus back half of the chain (the rig runs back -> front)
        float fx = 0, fz = 0;
        for (int i = 0; i < n; i++) { float w = i < n / 2 ? -1 : 1; fx += p[i * 3] * w; fz += p[i * 3 + 2] * w; }
        float fl = (float) Math.sqrt(fx * fx + fz * fz);
        if (fl < 1e-3f) { fx = (float) Math.sin(yaw); fz = (float) Math.cos(yaw); } else { fx /= fl; fz /= fl; }
        c.fx = fx; c.fz = fz; c.rx = fz; c.rz = -fx;
        float k = c.scale, g = groundY;
        String body = c.d.body(), key = c.key;
        switch (body) {
            case "quadruped_large" -> mammoth(m, c, cx, g, cz, k, walkPhase, walk);
            case "mounted" -> { if (key.contains("HOVER")) hoverbike(m, c, cx, g, cz, k); else horse(m, c, cx, g, cz, k, walkPhase, walk); }
            case "flyer" -> { if (key.contains("GUNSHIP")) gunship(m, c, cx, cy, cz, k); else balloon(m, c, cx, cy, cz, k); }
            case "flyer_large" -> dragon(m, c, cx, cy, cz, k);
            case "vehicle_large" -> { if (key.contains("BOAT")) longship(m, c, cx, g, cz, k); else if (key.contains("STARFORGE")) plasmaTank(m, c, cx, g, cz, k); else tank(m, c, cx, g, cz, k); }
            case "siege" -> { if (key.contains("STARFORGE")) ionArtillery(m, c, cx, g, cz, k); else siege(m, c, cx, g, cz, k); }
            default -> { if (key.contains("ROVER")) rover(m, c, cx, g, cz, k); else cart(m, c, cx, g, cz, k); }
        }
    }

    // helpers in the unit's frame: a = along forward, s = to the right, y = up
    static float wx(Ctx c, float x, float a, float s) { return x + c.fx * a + c.rx * s; }
    static float wz(Ctx c, float z, float a, float s) { return z + c.fz * a + c.rz * s; }

    static void legs4(Mesh m, Ctx c, float x, float g, float z, float a, float s, float top, float r, float phase, float walk, int col, int hoof) {
        float[][] pos = {{a, s, 0}, {a, -s, (float) Math.PI}, {-a, s, (float) Math.PI}, {-a, -s, 0}};
        for (float[] q : pos) {
            float sw = (float) Math.sin(phase + q[2]) * .35f * walk * top;
            float hx = wx(c, x, q[0], q[1]), hz = wz(c, z, q[0], q[1]);
            float kx = wx(c, x, q[0] + sw * .3f, q[1]), kz = wz(c, z, q[0] + sw * .3f, q[1]);
            float fx = wx(c, x, q[0] + sw, q[1]), fz = wz(c, z, q[0] + sw, q[1]);
            float lift = Math.max(0, (float) Math.cos(phase + q[2])) * .15f * walk * top;
            m.prismW(hx, g + top, hz, kx, g + top * .5f + lift, kz, r, r * .85f, c.sides, col);
            m.prismW(kx, g + top * .5f + lift, kz, fx, g + lift, fz, r * .85f, r * .8f, c.sides, col);
            m.prismW(fx, g + lift, fz, fx, g + lift + r * .6f, fz, r * .95f, r * .9f, c.sides, hoof);
        }
    }

    static void mammoth(Mesh m, Ctx c, float x, float g, float z, float k, float ph, float walk) {
        int fur = 0x8A5E3C, furDark = 0x6E4A2E;
        float h = .9f * k, L = .95f * k, W = .62f * k;
        legs4(m, c, x, g, z, L * .55f, W * .55f, h * 1.15f, .2f * k, ph, walk, furDark, Look.STONE_DARK);
        m.ballW(x, g + h * 1.5f, z, W, h * .75f, L, c.headSl, c.headSt, fur, c.fz, c.fx);
        m.prismW(wx(c, x, -L * .2f, 0), g + h * 2.1f, wz(c, z, -L * .2f, 0), wx(c, x, L * .5f, 0), g + h * 2.05f, wz(c, z, L * .5f, 0), W * .75f, W * .7f, c.sides, c.shirt, c.rx, 0, c.rz, .25f);
        float hx = wx(c, x, L * 1.05f, 0), hz = wz(c, z, L * 1.05f, 0), hy = g + h * 1.85f;
        m.ballW(hx, hy, hz, W * .6f, h * .5f, W * .55f, c.headSl, c.headSt, fur, c.fz, c.fx);
        float tx = wx(c, x, L * 1.45f, 0), tz = wz(c, z, L * 1.45f, 0);
        m.prismW(hx, hy - h * .1f, hz, tx, hy - h * .6f, tz, .14f * k, .1f * k, c.sides, furDark);
        m.prismW(tx, hy - h * .6f, tz, wx(c, x, L * 1.5f, 0), g + .25f * k, wz(c, z, L * 1.5f, 0), .1f * k, .08f * k, c.sides, furDark);
        for (int s = -1; s <= 1; s += 2) {
            float bx = wx(c, x, L * 1.25f, s * W * .3f), bz = wz(c, z, L * 1.25f, s * W * .3f);
            float mx = wx(c, x, L * 1.7f, s * W * .45f), mz = wz(c, z, L * 1.7f, s * W * .45f);
            m.prismW(bx, hy - h * .45f, bz, mx, hy - h * .55f, mz, .08f * k, .06f * k, c.sides, Look.BONE);
            m.prismW(mx, hy - h * .55f, mz, wx(c, x, L * 1.9f, s * W * .3f), hy - h * .1f, wz(c, z, L * 1.9f, s * W * .3f), .06f * k, .01f, c.sides, Look.BONE);
            m.ballW(wx(c, x, L * .95f, s * W * .6f), hy, wz(c, z, L * .95f, s * W * .6f), .06f * k, h * .38f, W * .35f, 5, 3, furDark, c.fz, c.fx);
        }
    }

    static void horse(Mesh m, Ctx c, float x, float g, float z, float k, float ph, float walk) {
        int coat = 0xE8E2D6, mane = 0x6E5A48;
        float h = .62f * k, L = .65f * k, W = .28f * k;
        legs4(m, c, x, g, z, L * .6f, W * .7f, h * 1.25f, .08f * k, ph, walk, coat, 0x3A3030);
        m.ballW(x, g + h * 1.55f, z, W, h * .45f, L, c.headSl, c.headSt, coat, c.fz, c.fx);
        // caparison in team colour
        m.ballW(x, g + h * 1.5f, z, W * 1.12f, h * .38f, L * .8f, c.headSl, c.headSt, c.shirt, c.fz, c.fx);
        float nx = wx(c, x, L * .8f, 0), nz = wz(c, z, L * .8f, 0), hx = wx(c, x, L * 1.15f, 0), hz = wz(c, z, L * 1.15f, 0);
        m.prismW(nx, g + h * 1.7f, nz, hx, g + h * 2.45f, hz, .16f * k, .12f * k, c.sides, coat);
        m.prismW(hx, g + h * 2.45f, hz, wx(c, x, L * 1.5f, 0), g + h * 2.2f, wz(c, z, L * 1.5f, 0), .13f * k, .09f * k, c.sides, coat);
        m.prismW(wx(c, x, L * .75f, 0), g + h * 1.95f, wz(c, z, L * .75f, 0), hx, g + h * 2.65f, hz, .05f * k, .04f * k, 4, mane, c.rx, 0, c.rz, .3f);
        m.prismW(wx(c, x, -L, 0), g + h * 1.7f, wz(c, z, -L, 0), wx(c, x, -L * 1.25f, 0), g + h * .9f, wz(c, z, -L * 1.25f, 0), .07f * k, .03f * k, 4, mane);
        // rider (upper body only, sitting)
        float ry = g + h * 2f;
        m.prismW(x, ry, z, x, ry + .55f * k, z, .26f * k, .2f * k, c.sides, c.shirt, c.rx, 0, c.rz, .85f);
        m.ballW(x, ry + .78f * k, z, .24f * k, .23f * k, .24f * k, c.headSl, c.headSt, c.skin, c.fz, c.fx);
        m.ballW(x, ry + .82f * k, z, .26f * k, .22f * k, .26f * k, c.headSl, c.headSt, Look.IRON, c.fz, c.fx);
        float lx = wx(c, x, -.2f * k, .25f * k), lz = wz(c, z, -.2f * k, .25f * k);
        m.prismW(lx, ry + .35f * k, lz, wx(c, x, 2.4f * k, .3f * k), ry + .6f * k, wz(c, z, 2.4f * k, .3f * k), .07f * k, .02f * k, c.sides, Look.WOOD_LIGHT);
        float sx = wx(c, x, .05f * k, -.3f * k), sz = wz(c, z, .05f * k, -.3f * k);
        m.prismW(sx, ry + .35f * k, sz, sx + c.rx * -.06f * k, ry + .35f * k, sz + c.rz * -.06f * k, .3f * k, .3f * k, 8, c.shirt);
    }

    static void cart(Mesh m, Ctx c, float x, float g, float z, float k) {
        float L = .55f * k, W = .38f * k;
        box(m, c, x, g + .35f * k, z, L, .22f * k, W, Look.WOOD);
        boolean hay = c.key.contains("HAY");
        if (hay) m.ballW(x, g + .7f * k, z, W * .95f, .3f * k, L * .9f, 6, 3, Look.THATCH, c.fz, c.fx);
        else m.ballW(x, g + .58f * k, z, W * .7f, .14f * k, L * .6f, 5, 2, Look.STONE_DARK, c.fz, c.fx);
        for (int s = -1; s <= 1; s += 2) wheel(m, c, wx(c, x, L * .2f, s * (W + .05f * k)), g + .25f * k, wz(c, z, L * .2f, s * (W + .05f * k)), .25f * k);
        // the farmer pushing it
        float bx = wx(c, x, -L - .35f * k, 0), bz = wz(c, z, -L - .35f * k, 0);
        m.prismW(bx, g, bz, bx, g + .7f * k, bz, .14f * k, .2f * k, c.sides, c.trousers);
        m.prismW(bx, g + .7f * k, bz, bx, g + 1.2f * k, bz, .25f * k, .18f * k, c.sides, c.shirt);
        m.ballW(bx, g + 1.42f * k, bz, .24f * k, .23f * k, .24f * k, c.headSl, c.headSt, c.skin, c.fz, c.fx);
        m.prismW(bx, g + 1.55f * k, bz, bx, g + 1.62f * k, bz, .38f * k, .36f * k, c.sides, Look.THATCH);
    }

    static void siege(Mesh m, Ctx c, float x, float g, float z, float k) {
        float L = .8f * k, W = .55f * k;
        box(m, c, x, g + .45f * k, z, L, .12f * k, W, Look.WOOD);
        for (int a = -1; a <= 1; a += 2) for (int s = -1; s <= 1; s += 2)
            wheel(m, c, wx(c, x, a * L * .65f, s * (W + .06f * k)), g + .3f * k, wz(c, z, a * L * .65f, s * (W + .06f * k)), .3f * k);
        String key = c.key;
        if (key.contains("BALLISTA")) {
            float px = x, pz = z, y = g + .95f * k;
            m.prismW(px, g + .55f * k, pz, px, y, pz, .1f * k, .08f * k, 4, Look.WOOD_DARK);
            m.prismW(wx(c, px, -.5f * k, 0), y, wz(c, pz, -.5f * k, 0), wx(c, px, .7f * k, 0), y, wz(c, pz, .7f * k, 0), .09f * k, .09f * k, 4, Look.WOOD);
            for (int s = -1; s <= 1; s += 2)
                m.prismW(wx(c, px, .55f * k, 0), y, wz(c, pz, .55f * k, 0), wx(c, px, .35f * k, s * .8f * k), y + .05f * k, wz(c, pz, .35f * k, s * .8f * k), .07f * k, .05f * k, 4, Look.WOOD_DARK);
            m.prismW(wx(c, px, -.3f * k, 0), y + .1f * k, wz(c, pz, -.3f * k, 0), wx(c, px, .95f * k, 0), y + .1f * k, wz(c, pz, .95f * k, 0), .03f * k, .03f * k, 4, Look.IRON);
        } else if (key.contains("HWACHA")) {
            for (int r = 0; r < 4; r++) for (int s = -2; s <= 2; s++) {
                float px = wx(c, x, .1f * k + r * .06f * k, s * .14f * k), pz = wz(c, z, .1f * k + r * .06f * k, s * .14f * k), py = g + .65f * k + r * .14f * k;
                m.prismW(px, py, pz, px + c.fx * .5f * k, py + .28f * k, pz + c.fz * .5f * k, .04f * k, .04f * k, 4, r % 2 == 0 ? Look.TILE_RED : Look.WOOD_LIGHT);
            }
        } else {   // catapult: frame and throwing arm
            for (int s = -1; s <= 1; s += 2)
                m.prismW(wx(c, x, 0, s * W * .7f), g + .55f * k, wz(c, z, 0, s * W * .7f), wx(c, x, .15f * k, s * W * .5f), g + 1.25f * k, wz(c, z, .15f * k, s * W * .5f), .07f * k, .06f * k, 4, Look.WOOD_DARK);
            float sw = (float) Math.max(0, Math.sin(c.t * 1.2)) * .9f;
            float ax = wx(c, x, -.9f * k + sw * .6f * k, 0), az = wz(c, z, -.9f * k + sw * .6f * k, 0);
            m.prismW(wx(c, x, .3f * k, 0), g + .6f * k, wz(c, z, .3f * k, 0), ax, g + .7f * k + sw * 1.1f * k, az, .07f * k, .05f * k, 4, Look.WOOD);
            m.ballW(ax, g + .8f * k + sw * 1.1f * k, az, .2f * k, .1f * k, .2f * k, 6, 2, Look.WOOD_DARK, c.fz, c.fx);
            m.prismW(wx(c, x, .15f * k, -W * .5f), g + 1.2f * k, wz(c, z, .15f * k, -W * .5f), wx(c, x, .15f * k, W * .5f), g + 1.2f * k, wz(c, z, .15f * k, W * .5f), .06f * k, .06f * k, 4, c.shirt);
        }
    }

    static void longship(Mesh m, Ctx c, float x, float g, float z, float k) {
        float L = 1.5f * k, W = .55f * k, y = g + .15f * k;
        m.prismW(wx(c, x, -L, 0), y + .5f * k, wz(c, z, -L, 0), wx(c, x, 0, 0), y + .25f * k, wz(c, z, 0, 0), .12f * k, W, 8, Look.WOOD, c.rx, 0, c.rz, .55f);
        m.prismW(wx(c, x, 0, 0), y + .25f * k, wz(c, z, 0, 0), wx(c, x, L, 0), y + .5f * k, wz(c, z, L, 0), W, .12f * k, 8, Look.WOOD, c.rx, 0, c.rz, .55f);
        m.prismW(wx(c, x, L, 0), y + .5f * k, wz(c, z, L, 0), wx(c, x, L * 1.15f, 0), y + 1.1f * k, wz(c, z, L * 1.15f, 0), .1f * k, .06f * k, 4, Look.WOOD_DARK);
        for (int i = -2; i <= 2; i++) for (int s = -1; s <= 1; s += 2) {   // shields along the gunwale
            float px = wx(c, x, i * .45f * k, s * W * .9f), pz = wz(c, z, i * .45f * k, s * W * .9f);
            m.prismW(px, y + .45f * k, pz, px + c.rx * s * .05f * k, y + .45f * k, pz + c.rz * s * .05f * k, .18f * k, .18f * k, 6, (i & 1) == 0 ? c.shirt : Look.CLOTH);
        }
        m.prismW(x, y + .3f * k, z, x, y + 2.6f * k, z, .06f * k, .05f * k, 4, Look.WOOD_DARK);
        float bil = (float) Math.sin(c.t * 1.7) * .1f * k;
        for (int st = 0; st < 3; st++) {
            float y0 = y + 2.4f * k - st * .55f * k, y1 = y0 - .55f * k;
            m.quad4(wx(c, x, .05f * k + bil, -1f * k), y0, wz(c, z, .05f * k + bil, -1f * k), wx(c, x, .05f * k + bil, 1f * k), y0, wz(c, z, .05f * k + bil, 1f * k),
                    wx(c, x, .15f * k + bil * 1.6f, 1f * k), y1, wz(c, z, .15f * k + bil * 1.6f, 1f * k), wx(c, x, .15f * k + bil * 1.6f, -1f * k), y1, wz(c, z, .15f * k + bil * 1.6f, -1f * k), st % 2 == 0 ? c.shirt : Look.CLOTH);
        }
    }

    static void tank(Mesh m, Ctx c, float x, float g, float z, float k) {
        float R = 1.1f * k;
        m.prismW(x, g + .1f * k, z, x, g + .55f * k, z, R, R * 1.02f, 10, Look.WOOD_DARK);
        m.prismW(x, g + .55f * k, z, x, g + 1.6f * k, z, R * 1.02f, .15f * k, 10, Look.WOOD);
        for (int i = 0; i < 10; i += 2) {   // cannon ports
            double a = i * Math.PI / 5; float ox = (float) Math.cos(a), oz = (float) Math.sin(a);
            m.prismW(x + ox * R * .8f, g + .45f * k, z + oz * R * .8f, x + ox * R * 1.2f, g + .45f * k, z + oz * R * 1.2f, .07f * k, .07f * k, 6, Look.IRON_DARK);
        }
        m.prismW(x, g + 1.55f * k, z, x, g + 1.9f * k, z, .12f * k, .1f * k, 6, Look.WOOD_DARK);
        m.prismW(x, g + 1.9f * k, z, x + c.rx * .5f * k, g + 1.75f * k, z + c.rz * .5f * k, .12f * k, .02f * k, 4, c.shirt, 0, 1, 0, .2f);
    }

    static void balloon(Mesh m, Ctx c, float x, float y, float z, float k) {
        float bob = (float) Math.sin(c.t * 1.5 + x) * .1f;
        float by = y + 1.1f * k + bob;
        m.ballW(x, by, z, .85f * k, 1.0f * k, .85f * k, c.headSl + 2, 5, c.shirt, c.fz, c.fx);
        m.ballW(x, by, z, .87f * k, .45f * k, .87f * k, c.headSl + 2, 3, Look.CLOTH, c.fz, c.fx);
        float ky = by - 1.5f * k;
        for (int s = 0; s < 4; s++) {
            float a = (float) (s * Math.PI / 2 + Math.PI / 4), ox = (float) Math.cos(a) * .3f * k, oz = (float) Math.sin(a) * .3f * k;
            m.prismW(x + ox, ky + .3f * k, z + oz, x + ox * 1.8f, by - .75f * k, z + oz * 1.8f, .015f * k, .015f * k, 4, Look.ROPE);
        }
        m.prismW(x, ky - .05f * k, z, x, ky + .32f * k, z, .32f * k, .35f * k, 6, Look.WOOD_LIGHT);
        m.ballW(x, ky + .55f * k, z, .2f * k, .2f * k, .2f * k, c.headSl, c.headSt, c.skin, c.fz, c.fx);
        m.prismW(x + c.fx * .2f * k, ky + .7f * k, z + c.fz * .2f * k, x + c.fx * .2f * k, ky + .1f * k, z + c.fz * .2f * k, .05f * k, .05f * k, 4, Look.WOOD, c.rx, 0, c.rz, .4f);
    }

    static void dragon(Mesh m, Ctx c, float x, float y, float z, float k) {
        int scale = 0xC9473C, belly = 0xF0C060;
        float wave = c.t * 3, L = 1.6f * k, hy = y + .4f * k;
        float px = 0, py = 0, pz = 0;
        int segs = 7;
        for (int i = 0; i <= segs; i++) {   // serpentine body, head at the front
            float u = (float) i / segs, a = (u - .5f) * 2 * L;
            float side = (float) Math.sin(wave - u * 5) * .35f * k, up = (float) Math.cos(wave * .5f - u * 4) * .15f * k;
            float qx = wx(c, x, a, side), qy = hy + up, qz = wz(c, z, a, side);
            if (i > 0) {
                float r0 = (.12f + .3f * (float) Math.sin(Math.PI * (i - 1) / segs)) * k, r1 = (.12f + .3f * (float) Math.sin(Math.PI * i / segs)) * k;
                m.prismW(px, py, pz, qx, qy, qz, r0, r1, c.sides, scale);
                if (c.near && i % 2 == 0) m.prismW(qx, qy + r1 * .8f, qz, qx, qy + r1 * 1.5f, qz, r1 * .3f, 0, 4, belly);
            }
            px = qx; py = qy; pz = qz;
        }
        m.ballW(px + c.fx * .25f * k, py + .1f * k, pz + c.fz * .25f * k, .32f * k, .25f * k, .45f * k, c.headSl, c.headSt, scale, c.fz, c.fx);
        for (int s = -1; s <= 1; s += 2) {
            m.prismW(px + c.rx * s * .15f * k, py + .25f * k, pz + c.rz * s * .15f * k, px + c.rx * s * .3f * k - c.fx * .4f * k, py + .65f * k, pz + c.rz * s * .3f * k - c.fz * .4f * k, .07f * k, 0, 4, belly);
            m.ballW(px + c.fx * .5f * k + c.rx * s * .14f * k, py + .2f * k, pz + c.fz * .5f * k + c.rz * s * .14f * k, .05f * k, .06f * k, .05f * k, 4, 2, Look.GOLD, c.fz, c.fx);
            float flap = (float) Math.sin(wave * 1.3f) * .9f * k;
            float sx = wx(c, x, .3f * k, s * .3f * k), sz = wz(c, z, .3f * k, s * .3f * k);
            float tx = wx(c, x, -.1f * k, s * 2f * k), tz = wz(c, z, -.1f * k, s * 2f * k);
            m.quad4(sx, hy + .3f * k, sz, tx, hy + .3f * k + flap, tz, wx(c, x, -1f * k, s * 1.4f * k), hy + .2f * k + flap * .6f, wz(c, z, -1f * k, s * 1.4f * k),
                    wx(c, x, -.6f * k, s * .3f * k), hy + .25f * k, wz(c, z, -.6f * k, s * .3f * k), Look.darker(scale, .2f));
        }
    }

    static void box(Mesh m, Ctx c, float x, float y, float z, float a, float h, float s, int col) {
        m.prismW(wx(c, x, -a, 0), y, wz(c, z, -a, 0), wx(c, x, a, 0), y, wz(c, z, a, 0), s * 1.414f, s * 1.414f, 4, col, c.rx, 0, c.rz, h / s);
    }

    static void wheel(Mesh m, Ctx c, float x, float y, float z, float r) {
        m.prismW(x - c.rx * .05f, y, z - c.rz * .05f, x + c.rx * .05f, y, z + c.rz * .05f, r, r, 8, Look.WOOD_DARK);
    }
}
