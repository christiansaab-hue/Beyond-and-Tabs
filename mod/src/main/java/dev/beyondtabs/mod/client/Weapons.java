package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.Rig;

/** Chunky held weapons and shields that follow the ragdoll's hands. */
final class Weapons {
    private Weapons() { }

    static void draw(Mesh m, UnitModels.Ctx c) {
        float k = c.scale;
        float hx = c.x(Rig.HAND_R), hy = c.y(Rig.HAND_R), hz = c.z(Rig.HAND_R);
        float ex = c.x(Rig.ELBOW_R), ey = c.y(Rig.ELBOW_R), ez = c.z(Rig.ELBOW_R);
        float lx = c.x(Rig.HAND_L), ly = c.y(Rig.HAND_L), lz = c.z(Rig.HAND_L);
        // forearm direction
        float ax = hx - ex, ay = hy - ey, az = hz - ez, al = len(ax, ay, az); ax /= al; ay /= al; az /= al;
        // blades point out of the fist: mostly up, a little along the forearm and forward
        float[] up = norm(ax * .55f + c.fx * .35f, ay * .55f + .85f, az * .55f + c.fz * .35f);
        float[] fwd = norm(c.fx, .12f, c.fz);
        String w = c.d.weaponClass(), key = c.key;
        switch (w) {
            case "melee_light" -> {
                if (key.contains("CLUB")) club(m, c, hx, hy, hz, up, .8f * k, .13f * k, Look.WOOD);
                else if (key.contains("PITCHFORK")) fork(m, c, hx, hy, hz, norm(c.fx, .6f, c.fz), k);
                else if (key.contains("HALFLING")) sword(m, c, hx, hy, hz, up, .35f * k, k, Look.IRON);
                else if (key.contains("FENCER")) rapier(m, c, hx, hy, hz, fwd, k);
                else if (key.contains("PAINTER")) brush(m, c, hx, hy, hz, up, k);
                else if (key.contains("HEADBUTTER") || key.contains("BRAWLER")) { }
                else sword(m, c, hx, hy, hz, up, .7f * k, k, Look.IRON);
            }
            case "melee_shield" -> {
                sword(m, c, hx, hy, hz, up, .65f * k, k, Look.IRON);
                shield(m, c, lx, ly, lz, k, key.contains("ROMAN"), key.contains("HOPLITE") ? 0xC9973F : -1);
            }
            case "melee_heavy" -> {
                if (key.contains("CHIEFTAIN")) club(m, c, hx, hy, hz, up, 1.15f * k, .2f * k, Look.BONE);
                else if (key.contains("SAMURAI")) sword(m, c, hx, hy, hz, up, 1.1f * k, k * .8f, 0xDDE2E8);
                else if (key.contains("JARL")) axe(m, c, hx, hy, hz, up, 1.2f * k, k * 1.3f);
                else if (key.contains("KING")) sword(m, c, hx, hy, hz, up, 1.0f * k, k * 1.1f, Look.GOLD);
                else {
                    sword(m, c, hx, hy, hz, up, 1.25f * k, k * 1.2f, Look.IRON);
                    if (key.contains("VALKYRIE")) wings(m, c, k);
                }
            }
            case "melee_dual" -> {
                axe(m, c, hx, hy, hz, up, .7f * k, k);
                float bx = lx - c.x(Rig.ELBOW_L), by = ly - c.y(Rig.ELBOW_L), bz = lz - c.z(Rig.ELBOW_L), bl = len(bx, by, bz);
                axe(m, c, lx, ly, lz, norm(bx / bl * .55f + c.fx * .35f, by / bl * .55f + .85f, bz / bl * .55f + c.fz * .35f), .7f * k, k);
            }
            case "melee_reach" -> {
                float[] d = norm(c.fx + ax * .2f, .18f + ay * .2f, c.fz + az * .2f);
                float L = (key.contains("SARISSA") ? 3.0f : 2.2f) * k;
                stick(m, hx, hy, hz, d, -.7f * k, L, .035f * k, Look.WOOD, c.sides);
                float tx = hx + d[0] * L, ty = hy + d[1] * L, tz = hz + d[2] * L;
                m.prismW(tx, ty, tz, tx + d[0] * .28f * k, ty + d[1] * .28f * k, tz + d[2] * .28f * k, .07f * k, 0, 4, Look.IRON, c.rx, 0, c.rz, .3f);
                if (key.contains("HALBERD")) {
                    float bx = tx - d[0] * .2f * k, by = ty - d[1] * .2f * k, bz = tz - d[2] * .2f * k;
                    m.prismW(bx, by, bz, bx + c.rx * .3f * k, by - .05f * k, bz + c.rz * .3f * k, .16f * k, .1f * k, 4, Look.IRON, d[0], d[1], d[2], .2f);
                }
            }
            case "melee_fast" -> sword(m, c, hx, hy, hz, up, (key.contains("NINJA") ? .75f : .35f) * k, k * .8f, key.contains("NINJA") ? 0xDDE2E8 : Look.IRON);
            case "melee_staff" -> {
                float[] d = norm(c.rx + c.fx * .4f, .35f, c.rz + c.fz * .4f);
                stick(m, hx, hy, hz, d, -1.0f * k, 1.0f * k, .04f * k, 0xB8402C, c.sides);
                for (int s = -1; s <= 1; s += 2) stick(m, hx + d[0] * s * .9f * k, hy + d[1] * s * .9f * k, hz + d[2] * s * .9f * k, d, -.1f * k, .1f * k, .055f * k, Look.GOLD, c.sides);
            }
            case "thrown" -> {
                float[] d = norm(c.fx, .25f, c.fz);
                stick(m, hx, hy + .1f * k, hz, d, -.8f * k, .8f * k, .03f * k, Look.WOOD_LIGHT, c.sides);
                float tx = hx + d[0] * .8f * k, ty = hy + .1f * k + d[1] * .8f * k, tz = hz + d[2] * .8f * k;
                m.prismW(tx, ty, tz, tx + d[0] * .2f * k, ty + d[1] * .2f * k, tz + d[2] * .2f * k, .05f * k, 0, 4, Look.STONE_DARK);
            }
            case "thrown_heavy" -> {
                float bx = (hx + lx) / 2, by = Math.max(hy, ly) + .15f * k, bz = (hz + lz) / 2;
                m.ballW(bx, by, bz, .34f * k, .3f * k, .32f * k, 6, 4, Look.STONE_DARK, c.fz, c.fx);
            }
            case "bow", "bow_slow", "bow_poison", "rocket_volley" -> {
                int col = key.contains("ICE") ? 0xA9DDF0 : key.contains("SNAKE") ? 0x6FA35A : key.contains("FIREWORK") ? 0xB8402C : Look.WOOD;
                bow(m, c, lx, ly, lz, k * (w.equals("bow_slow") ? 1.2f : 1f), col);
                quiver(m, c, k, w.equals("rocket_volley") ? Look.TILE_RED : Look.LEATHER);
            }
            case "musket" -> {
                float[] d = norm(c.fx, .06f, c.fz);
                stick(m, hx, hy, hz, d, -.45f * k, 0, .06f * k, Look.WOOD, 4);
                stick(m, hx, hy + .03f * k, hz, d, 0, 1.0f * k, .035f * k, Look.IRON_DARK, c.sides);
            }
            case "magic_aoe", "magic_lightning", "heal" -> {
                float[] d = norm(c.fx * .15f, 1, c.fz * .15f);
                stick(m, hx, hy, hz, d, -.9f * k, .7f * k, .035f * k, Look.WOOD, c.sides);
                int orb = w.equals("heal") ? 0xA8F08A : w.equals("magic_lightning") ? Look.GLOW : Look.BONE;
                float pulse = 1 + (float) Math.sin(c.t * 4) * .1f;
                m.ballW(hx + d[0] * .8f * k, hy + d[1] * .8f * k, hz + d[2] * .8f * k, .13f * k * pulse, .13f * k * pulse, .13f * k * pulse, 6, 4, orb, c.fz, c.fx);
            }
            case "buff_aura" -> {   // a lute across the chest
                float bx = c.x(Rig.TORSO) + c.fx * .3f * k, by = c.y(Rig.TORSO) - .05f * k, bz = c.z(Rig.TORSO) + c.fz * .3f * k;
                m.ballW(bx - c.rx * .1f * k, by, bz - c.rz * .1f * k, .2f * k, .22f * k, .1f * k, 6, 3, Look.WOOD_LIGHT, c.fz, c.fx);
                m.prismW(bx, by + .05f * k, bz, bx + c.rx * .55f * k, by + .35f * k, bz + c.rz * .55f * k, .04f * k, .035f * k, 4, Look.WOOD_DARK);
            }
            default -> { }
        }
    }

    static float len(float x, float y, float z) { return Math.max(1e-5f, (float) Math.sqrt(x * x + y * y + z * z)); }
    static float[] norm(float x, float y, float z) { float l = len(x, y, z); return new float[]{x / l, y / l, z / l}; }

    static void stick(Mesh m, float x, float y, float z, float[] d, float a, float b, float r, int col, int sides) {
        m.prismW(x + d[0] * a, y + d[1] * a, z + d[2] * a, x + d[0] * b, y + d[1] * b, z + d[2] * b, r, r, Math.max(4, Math.min(6, sides)), col);
    }

    static void sword(Mesh m, UnitModels.Ctx c, float x, float y, float z, float[] d, float L, float k, int blade) {
        stick(m, x, y, z, d, -.14f * k, .02f * k, .03f * k, Look.LEATHER, 4);
        float gx = x + d[0] * .05f * k, gy = y + d[1] * .05f * k, gz = z + d[2] * .05f * k;
        m.prismW(gx - c.rx * .13f * k, gy, gz - c.rz * .13f * k, gx + c.rx * .13f * k, gy, gz + c.rz * .13f * k, .03f * k, .03f * k, 4, Look.darker(Look.GOLD, .2f));
        m.prismW(gx, gy, gz, x + d[0] * L, y + d[1] * L, z + d[2] * L, .06f * k, .012f * k, 4, blade, c.rx, 0, c.rz, .28f);
    }

    static void rapier(Mesh m, UnitModels.Ctx c, float x, float y, float z, float[] d, float k) {
        m.ballW(x + d[0] * .05f * k, y + d[1] * .05f * k, z + d[2] * .05f * k, .07f * k, .07f * k, .07f * k, 5, 3, Look.GOLD, c.fz, c.fx);
        stick(m, x, y, z, d, 0, 1.05f * k, .014f * k, 0xDDE2E8, 4);
    }

    static void club(Mesh m, UnitModels.Ctx c, float x, float y, float z, float[] d, float L, float r, int col) {
        m.prismW(x - d[0] * .12f * L, y - d[1] * .12f * L, z - d[2] * .12f * L, x + d[0] * L, y + d[1] * L, z + d[2] * L, r * .4f, r, Math.max(5, Math.min(7, c.sides)), col);
    }

    static void axe(Mesh m, UnitModels.Ctx c, float x, float y, float z, float[] d, float L, float k) {
        stick(m, x, y, z, d, -.15f * k, L, .035f * k, Look.WOOD, c.sides);
        float bx = x + d[0] * L * .85f, by = y + d[1] * L * .85f, bz = z + d[2] * L * .85f;
        m.prismW(bx, by, bz, bx + c.fx * .28f * k, by - .03f * k, bz + c.fz * .28f * k, .1f * k, .17f * k, 4, Look.IRON, d[0], d[1], d[2], .2f);
    }

    static void fork(Mesh m, UnitModels.Ctx c, float x, float y, float z, float[] d, float k) {
        stick(m, x, y, z, d, -.7f * k, 1.2f * k, .03f * k, Look.WOOD_LIGHT, c.sides);
        float tx = x + d[0] * 1.2f * k, ty = y + d[1] * 1.2f * k, tz = z + d[2] * 1.2f * k;
        for (int s = -1; s <= 1; s++) {
            float ox = tx + c.rx * s * .09f * k, oz = tz + c.rz * s * .09f * k;
            m.prismW(ox, ty, oz, ox + d[0] * .3f * k, ty + d[1] * .3f * k, oz + d[2] * .3f * k, .015f * k, .005f * k, 4, Look.IRON);
        }
    }

    static void brush(Mesh m, UnitModels.Ctx c, float x, float y, float z, float[] d, float k) {
        stick(m, x, y, z, d, -.1f * k, .45f * k, .025f * k, Look.WOOD_DARK, 4);
        m.prismW(x + d[0] * .45f * k, y + d[1] * .45f * k, z + d[2] * .45f * k, x + d[0] * .62f * k, y + d[1] * .62f * k, z + d[2] * .62f * k, .05f * k, .01f * k, 4, Look.TILE_RED);
    }

    static void shield(Mesh m, UnitModels.Ctx c, float x, float y, float z, float k, boolean rect, int rim) {
        float cx = x + c.fx * .12f * k, cz = z + c.fz * .12f * k, cy = y + .1f * k;
        if (rect) {
            m.prismW(cx - c.fx * .04f * k, cy, cz - c.fz * .04f * k, cx + c.fx * .04f * k, cy, cz + c.fz * .04f * k, .5f * k, .5f * k, 4, c.shirt, 0, 1, 0, .7f);
            m.ballW(cx + c.fx * .06f * k, cy, cz + c.fz * .06f * k, .07f * k, .07f * k, .07f * k, 4, 2, Look.GOLD, c.fz, c.fx);
        } else {
            int col = rim >= 0 ? rim : c.shirt;
            m.prismW(cx - c.fx * .04f * k, cy, cz - c.fz * .04f * k, cx + c.fx * .04f * k, cy, cz + c.fz * .04f * k, .4f * k, .4f * k, 10, col);
            if (c.near) m.prismW(cx + c.fx * .04f * k, cy, cz + c.fz * .04f * k, cx + c.fx * .09f * k, cy, cz + c.fz * .09f * k, .1f * k, .06f * k, 6, rim >= 0 ? c.shirt : Look.IRON);
        }
    }

    static void bow(Mesh m, UnitModels.Ctx c, float x, float y, float z, float k, int col) {
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= 4; i++) {
            double a = -Math.PI / 2.6 + i * (Math.PI / 1.3) / 4;
            float u = (float) Math.sin(a) * .62f * k, f = ((float) Math.cos(a) - .6f) * .3f * k;
            float qx = x + c.fx * f, qy = y + u, qz = z + c.fz * f;
            if (i > 0) m.prismW(px, py, pz, qx, qy, qz, .035f * k, .035f * k, 4, col);
            px = qx; py = qy; pz = qz;
        }
        float bx = x - c.fx * .1f * k, bz = z - c.fz * .1f * k;
        m.prismW(bx, y - .53f * k, bz, bx, y + .53f * k, bz, .008f * k, .008f * k, 4, Look.CLOTH);
    }

    static void quiver(Mesh m, UnitModels.Ctx c, float k, int col) {
        float bx = c.x(Rig.TORSO) - c.fx * .3f * k, by = c.y(Rig.TORSO), bz = c.z(Rig.TORSO) - c.fz * .3f * k;
        m.prismW(bx - c.rx * .1f * k, by - .3f * k, bz - c.rz * .1f * k, bx + c.rx * .1f * k, by + .3f * k, bz + c.rz * .1f * k, .08f * k, .09f * k, 6, col);
        if (c.near) for (int i = -1; i <= 1; i++)
            m.prismW(bx + c.rx * (.1f + i * .03f) * k, by + .3f * k, bz + c.rz * (.1f + i * .03f) * k, bx + c.rx * (.14f + i * .03f) * k, by + .45f * k, bz + c.rz * (.14f + i * .03f) * k, .025f * k, .005f * k, 4, Look.CLOTH);
    }

    static void wings(Mesh m, UnitModels.Ctx c, float k) {
        float bx = c.x(Rig.TORSO) - c.fx * .25f * k, by = c.y(Rig.TORSO) + .15f * k, bz = c.z(Rig.TORSO) - c.fz * .25f * k;
        float flap = (float) Math.sin(c.t * 5) * .25f * k;
        for (int s = -1; s <= 1; s += 2)
            m.quad4(bx, by, bz, bx + c.rx * s * 1.1f * k - c.fx * .3f * k, by + .5f * k + flap, bz + c.rz * s * 1.1f * k - c.fz * .3f * k,
                    bx + c.rx * s * .9f * k - c.fx * .4f * k, by - .4f * k + flap, bz + c.rz * s * .9f * k - c.fz * .4f * k,
                    bx - c.fx * .1f * k, by - .5f * k, bz - c.fz * .1f * k, 0xF4F2EE);
    }
}
