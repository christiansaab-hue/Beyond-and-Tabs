package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.gen.BuildingDef;

/**
 * Race-specific building models. Local frame: x across the footprint (0..w), z deep (0..d), y up from the ground,
 * the front (door, rally side) faces +z. Every level adds visible detail.
 * Static geometry goes to `s` (cached on the GPU once built); animated bits (flags, sails, fire, smoke, crane) go to
 * `a` and are drawn every frame. Parts are authored bottom-up so construction reveals them in a sensible order.
 *
 * The detail kit below (stone courses and quoins, plank seams, timber framing, tiled roofs with ridge caps and barge
 * boards, glowing windows, arched doors, lanterns, banners) is shared so every building has the same finish.
 *
 * Ancient World = Tribal / Viking / Greek. Kingdoms = Medieval / Dynasty / Renaissance.
 */
final class BuildingModels {
    private BuildingModels() { }

    static final int FRONT = 0, BACK = 1, RIGHT = 2, LEFT = 3;

    static int[] size(BuildingDef d) {
        String[] f = d.footprint().split("x");
        return new int[]{Integer.parseInt(f[0]), Integer.parseInt(f[1])};
    }

    static void build(Mesh s, Mesh a, BuildingDef d, int L, int team, float t) {
        int[] sz = size(d); float w = sz[0], dd = sz[1];
        String kind = d.id().substring(d.id().indexOf('_') + 1);
        switch (d.race()) {
            case "ancient_world" -> ancient(s, a, kind, w, dd, L, team, t);
            case "kingdoms" -> kingdoms(s, a, kind, w, dd, L, team, t);
            default -> generic(s, a, w, dd, L, team, t);
        }
    }

    // ================================================================== detail kit

    /** A box on one face of a wall: u along the wall, v up, n outward from the plane `p`. */
    static void boxF(Mesh s, int face, float p, float u0, float v0, float n0, float u1, float v1, float n1, int c) {
        switch (face) {
            case FRONT -> s.box(u0, v0, p + n0, u1, v1, p + n1, c);
            case BACK -> s.box(u0, v0, p - n1, u1, v1, p - n0, c);
            case RIGHT -> s.box(p + n0, v0, u0, p + n1, v1, u1, c);
            default -> s.box(p - n1, v0, u0, p - n0, v1, u1, c);
        }
    }

    /** Ground pad with a bevelled lip. */
    static void pad(Mesh s, float w, float d, int c) {
        s.box(-.25f, -.5f, -.25f, w + .25f, .02f, d + .25f, Look.darker(c, .08f));
        s.hip(-.25f, -.25f, w + .25f, d + .25f, .02f, .06f, 0, .12f, c);
    }

    /** Raised stone plinth with a bevelled top edge. */
    static void plinth(Mesh s, float x0, float z0, float x1, float z1, float h, int c) {
        s.box(x0, -.4f, z0, x1, h - .1f, z1, Look.darker(c, .1f));
        s.hip(x0, z0, x1, z1, h - .1f, .1f, 0, .1f, c);
    }

    /** Dressed stone walls: plinth, courses, corner quoins and a cornice. */
    static void stoneWalls(Mesh s, float x0, float z0, float x1, float z1, float y0, float y1, int c) {
        s.box(x0 - .12f, y0, z0 - .12f, x1 + .12f, y0 + .4f, z1 + .12f, Look.darker(c, .18f));
        s.box(x0, y0, z0, x1, y1, z1, c);
        for (float y = y0 + 1.1f; y < y1 - .3f; y += 1.1f) s.box(x0 - .03f, y, z0 - .03f, x1 + .03f, y + .08f, z1 + .03f, Look.darker(c, .12f));
        int q = Look.lighter(c, .1f); int k = 0;
        for (float y = y0 + .4f; y < y1 - .3f; y += .42f, k++) {
            float a = k % 2 == 0 ? .55f : .3f, b = k % 2 == 0 ? .3f : .55f, yy = y + .36f;
            s.box(x0 - .06f, y, z0 - .06f, x0 + a, yy, z0 + b, q); s.box(x1 - a, y, z0 - .06f, x1 + .06f, yy, z0 + b, q);
            s.box(x0 - .06f, y, z1 - b, x0 + a, yy, z1 + .06f, q); s.box(x1 - a, y, z1 - b, x1 + .06f, yy, z1 + .06f, q);
        }
        s.box(x0 - .14f, y1 - .2f, z0 - .14f, x1 + .14f, y1, z1 + .14f, q);
    }

    /** Plank walls: sill beam, vertical seams, heavy corner posts and a top plate. */
    static void plankWalls(Mesh s, float x0, float z0, float x1, float z1, float y0, float y1, int c) {
        s.box(x0 - .08f, y0, z0 - .08f, x1 + .08f, y0 + .3f, z1 + .08f, Look.darker(c, .3f));
        s.box(x0, y0, z0, x1, y1, z1, c);
        int seam = Look.darker(c, .25f);
        for (float x = x0 + .45f; x < x1 - .2f; x += .45f) { s.box(x - .025f, y0 + .3f, z1, x + .025f, y1, z1 + .02f, seam); s.box(x - .025f, y0 + .3f, z0 - .02f, x + .025f, y1, z0, seam); }
        for (float z = z0 + .45f; z < z1 - .2f; z += .45f) { s.box(x1, y0 + .3f, z - .025f, x1 + .02f, y1, z + .025f, seam); s.box(x0 - .02f, y0 + .3f, z - .025f, x0, y1, z + .025f, seam); }
        int post = Look.darker(c, .35f);
        for (float x : new float[]{x0, x1}) for (float z : new float[]{z0, z1}) s.box(x - .14f, y0, z - .14f, x + .14f, y1 + .1f, z + .14f, post);
        s.box(x0 - .1f, y1 - .18f, z0 - .1f, x1 + .1f, y1, z1 + .1f, post);
    }

    /** Plaster walls on a stone plinth with a dark timber frame. */
    static void plasterWalls(Mesh s, float x0, float z0, float x1, float z1, float y0, float y1, int beam) {
        s.box(x0 - .1f, y0, z0 - .1f, x1 + .1f, y0 + .45f, z1 + .1f, Look.STONE_COOL_DARK);
        s.box(x0, y0, z0, x1, y1, z1, Look.PLASTER);
        timber(s, x0, z0, x1, z1, y0 + .45f, y1, beam);
    }

    static void timber(Mesh s, float x0, float z0, float x1, float z1, float y0, float y1, int beam) {
        float o = .05f, xm = (x0 + x1) / 2, zm = (z0 + z1) / 2, ym = (y0 + y1) / 2;
        for (float x : new float[]{x0, (x0 + xm) / 2, xm, (xm + x1) / 2, x1}) {
            s.box(x - .1f, y0, z1 - .03f, x + .1f, y1, z1 + o, beam);
            s.box(x - .1f, y0, z0 - o, x + .1f, y1, z0 + .03f, beam);
        }
        for (float z : new float[]{z0, zm, z1}) { s.box(x0 - o, y0, z - .1f, x0 + .03f, y1, z + .1f, beam); s.box(x1 - .03f, y0, z - .1f, x1 + o, y1, z + .1f, beam); }
        s.box(x0 - o, y1 - .18f, z0 - o, x1 + o, y1, z1 + o, beam);
        s.box(x0 - o, ym - .07f, z1 - .03f, x1 + o, ym + .07f, z1 + o, beam); s.box(x0 - o, ym - .07f, z0 - o, x1 + o, ym + .07f, z0 + .03f, beam);
        s.box(x0 - o, ym - .07f, z0, x0 + .03f, ym + .07f, z1, beam); s.box(x1 - .03f, ym - .07f, z0, x1 + o, ym + .07f, z1, beam);
        s.prismL(x0 + .15f, y0 + .1f, z1 + o, (x0 + xm) / 2 - .05f, ym - .1f, z1 + o, .06f, .06f, 4, beam);
        s.prismL(x1 - .15f, y0 + .1f, z1 + o, (xm + x1) / 2 + .05f, ym - .1f, z1 + o, .06f, .06f, 4, beam);
    }

    /** Gable roof with tile courses, a ridge cap and barge boards. */
    static void roof(Mesh s, float x0, float z0, float x1, float z1, float y, float h, boolean alongX, float over, int c, int end) {
        s.gable(x0, z0, x1, z1, y, h, alongX, over, c, end);
        int line = Look.darker(c, .22f), cap = Look.darker(c, .3f), board = Look.WOOD_DARK;
        if (alongX) {
            float zm = (z0 + z1) / 2, xa = x0 - over * .5f, xb = x1 + over * .5f;
            float[][] eaves = {{z0 - over, -1}, {z1 + over, 1}};
            for (float[] e : eaves) {
                int n = Math.max(2, (int) (Math.hypot(zm - e[0], h) / .42f));
                for (int i = 1; i < n; i++) {
                    float t = (float) i / n, zz = e[0] + (zm - e[0]) * t, yy = y + h * t + .03f;
                    s.prismL(xa, yy, zz, xb, yy, zz, .035f, .035f, 4, line);
                }
                s.prismL(xa - .05f, y + .02f, e[0], xb + .05f, y + .02f, e[0], .07f, .07f, 4, board);
                for (float xx : new float[]{xa - .02f, xb + .02f}) s.prismL(xx, y, e[0], xx, y + h + .05f, zm, .07f, .07f, 4, board);
            }
            s.prismL(xa - .1f, y + h + .02f, zm, xb + .1f, y + h + .02f, zm, .12f, .12f, 6, cap);
        } else {
            float xm = (x0 + x1) / 2, za = z0 - over * .5f, zb = z1 + over * .5f;
            float[] eaves = {x0 - over, x1 + over};
            for (float ex : eaves) {
                int n = Math.max(2, (int) (Math.hypot(xm - ex, h) / .42f));
                for (int i = 1; i < n; i++) {
                    float t = (float) i / n, xx = ex + (xm - ex) * t, yy = y + h * t + .03f;
                    s.prismL(xx, yy, za, xx, yy, zb, .035f, .035f, 4, line);
                }
                s.prismL(ex, y + .02f, za - .05f, ex, y + .02f, zb + .05f, .07f, .07f, 4, board);
                for (float zz : new float[]{za - .02f, zb + .02f}) s.prismL(ex, y, zz, xm, y + h + .05f, zz, .07f, .07f, 4, board);
            }
            s.prismL(xm, y + h + .02f, za - .1f, xm, y + h + .02f, zb + .1f, .12f, .12f, 6, cap);
        }
    }

    /** Hipped roof with ridges down its corners and a finial. */
    static void hipRoof(Mesh s, float x0, float z0, float x1, float z1, float y, float h, float over, float inset, int c, int finial) {
        s.hip(x0, z0, x1, z1, y, h, over, inset, c);
        int ridge = Look.darker(c, .3f);
        float a0 = x0 - over, b0 = z0 - over, a1 = x1 + over, b1 = z1 + over;
        float ti = Math.min(inset, Math.min((a1 - a0) / 2, (b1 - b0) / 2));
        float[][] cs = {{a0, b0, a0 + ti, b0 + ti}, {a1, b0, a1 - ti, b0 + ti}, {a0, b1, a0 + ti, b1 - ti}, {a1, b1, a1 - ti, b1 - ti}};
        for (float[] q : cs) s.prismL(q[0], y + .02f, q[1], q[2], y + h + .02f, q[3], .08f, .07f, 4, ridge);
        s.prismL(a0, y, b0, a1, y, b0, .06f, .06f, 4, ridge); s.prismL(a0, y, b1, a1, y, b1, .06f, .06f, 4, ridge);
        s.prismL(a0, y, b0, a0, y, b1, .06f, .06f, 4, ridge); s.prismL(a1, y, b0, a1, y, b1, .06f, .06f, 4, ridge);
        if (finial >= 0) { float cx = (x0 + x1) / 2, cz = (z0 + z1) / 2; s.cyl(cx, cz, y + h - .05f, y + h + .45f, .06f, .04f, 6, finial); s.ball(cx, y + h + .5f, cz, .1f, .1f, .1f, 8, 5, finial); }
    }

    /** Conical roof with a rim and a finial. */
    static void coneRoof(Mesh s, float x, float z, float y, float h, float r, int sides, int c, int finial) {
        s.cyl(x, z, y - .12f, y, r * 1.02f, r, sides, Look.darker(c, .3f));
        s.cone(x, z, y, y + h, r, sides, c);
        s.cone(x, z, y + h * .35f, y + h * .4f + .05f, r * .68f, sides, Look.darker(c, .15f));
        if (finial >= 0) { s.cyl(x, z, y + h - .1f, y + h + .4f, .05f, .03f, 6, finial); s.ball(x, y + h + .45f, z, .09f, .09f, .09f, 8, 5, finial); }
    }

    /** A glowing window with frame, cross mullions and a sill, on one face. */
    static void window(Mesh s, int face, float p, float u, float y, float w, float h, int frame) {
        boxF(s, face, p, u - w / 2 - .09f, y - .09f, -.02f, u + w / 2 + .09f, y + h + .09f, .05f, frame);
        s.glow = true; boxF(s, face, p, u - w / 2, y, .05f, u + w / 2, y + h, .065f, Look.WINDOW); s.glow = false;
        boxF(s, face, p, u - .03f, y, .06f, u + .03f, y + h, .09f, frame);
        boxF(s, face, p, u - w / 2, y + h * .55f - .03f, .06f, u + w / 2, y + h * .55f + .03f, .09f, frame);
        boxF(s, face, p, u - w / 2 - .14f, y - .16f, -.02f, u + w / 2 + .14f, y - .07f, .16f, Look.darker(frame, .1f));
    }

    /** Arched or square plank door with iron bands, on the front (+z) face at depth z. */
    static void door(Mesh s, float x, float z, float w, float h, int frame, boolean arch) {
        int planks = 0x4A3020;
        if (arch) s.prismL(x, h, z - .04f, x, h, z + .1f, w / 2 + .14f, w / 2 + .14f, 12, frame);
        s.box(x - w / 2 - .12f, 0, z - .03f, x + w / 2 + .12f, h + (arch ? 0 : .14f), z + .1f, frame);
        s.box(x - w / 2, 0, z + .1f, x + w / 2, h, z + .13f, planks);
        if (arch) s.prismL(x, h, z + .1f, x, h, z + .13f, w / 2, w / 2, 12, planks);
        for (float u = x - w / 2 + w / 4; u < x + w / 2 - .05f; u += w / 4) s.box(u - .015f, 0, z + .13f, u + .015f, h, z + .145f, Look.darker(planks, .3f));
        for (float y : new float[]{h * .25f, h * .72f}) s.box(x - w / 2, y, z + .13f, x + w / 2, y + .07f, z + .16f, Look.IRON_DARK);
        s.ball(x + w / 4, h * .5f, z + .17f, .05f, .05f, .03f, 6, 4, Look.IRON);
        s.box(x - w / 2 - .25f, -.05f, z + .1f, x + w / 2 + .25f, .1f, z + .55f, Look.STONE_DARK);
    }

    /** Wall lantern: bracket, iron cage, glowing core. */
    static void lantern(Mesh s, float x, float y, float z, float outX, float outZ) {
        s.prismL(x, y + .3f, z, x + outX * .3f, y + .3f, z + outZ * .3f, .025f, .025f, 4, Look.IRON_DARK);
        float lx = x + outX * .3f, lz = z + outZ * .3f;
        s.box(lx - .1f, y - .05f, lz - .1f, lx + .1f, y + .2f, lz + .1f, Look.IRON_DARK);
        s.glow = true; s.box(lx - .07f, y - .02f, lz - .07f, lx + .07f, y + .17f, lz + .07f, Look.WINDOW); s.glow = false;
        s.hip(lx - .12f, lz - .12f, lx + .12f, lz + .12f, y + .2f, .1f, 0, .12f, Look.IRON_DARK);
    }

    /** Team light: a glowing gem in an iron mount (reads at any zoom). */
    static void teamGem(Mesh s, float x, float y, float z, int team) {
        s.cyl(x, z, y - .12f, y, .12f, .1f, 6, Look.IRON_DARK);
        s.glow = true; s.ball(x, y + .08f, z, .1f, .13f, .1f, 8, 5, Look.lighter(team, .25f)); s.glow = false;
    }

    static void post(Mesh s, float x, float z, float y0, float y1, float r, int c) { s.cyl(x, z, y0, y1, r, r * .9f, 8, c); }

    static void log(Mesh s, float x0, float y0, float z0, float x1, float y1, float z1, float r, int c) { s.prismL(x0, y0, z0, x1, y1, z1, r, r, 8, c); }

    static void flagPole(Mesh s, Mesh a, float x, float z, float y0, float h, int team, float t, float fw) {
        post(s, x, z, y0, y0 + h, .06f, Look.WOOD_DARK);
        s.ball(x, y0 + h + .08f, z, .1f, .1f, .1f, 8, 5, Look.GOLD);
        a.flag(x + .05f, y0 + h - .05f, z, fw, fw * .6f, t * 3 + x * .7f + z, team);
    }

    static void fire(Mesh a, float x, float y, float z, float r, float t) {
        a.glow = true;
        for (int i = 0; i < 4; i++) {
            float ph = t * 9 + i * 2.1f, f = .75f + .25f * (float) Math.sin(ph);
            float ox = (float) Math.cos(i * 1.6f) * r * .35f, oz = (float) Math.sin(i * 1.6f) * r * .35f;
            a.cone(x + ox, z + oz, y, y + r * 2.2f * f, r * .5f, 6, i == 0 ? 0xFFE07A : Look.FIRE);
        }
        a.glow = false;
    }

    static void smoke(Mesh a, float x, float y, float z, float t, int c) {
        float al = a.alpha;
        for (int i = 0; i < 4; i++) {
            float u = ((t * .3f + i / 4f) % 1f), r = .22f + u * .5f;
            a.alpha = al * (1 - u) * .8f;
            a.ball(x + u * .7f, y + u * 2.8f, z - u * .35f, r, r * .85f, r, 8, 5, Look.mix(c, 0xFFFFFF, u * .4f));
        }
        a.alpha = al;
    }

    static void crenels(Mesh s, float x0, float z0, float x1, float z1, float y, float h, int c) {
        s.box(x0 - .18f, y - .25f, z0 - .18f, x1 + .18f, y, z1 + .18f, Look.darker(c, .12f));
        for (float x = x0; x < x1 - .3f; x += 1.0f) { merlon(s, x, y, z0 - .18f, x + .55f, h, z0 + .3f, c); merlon(s, x, y, z1 - .3f, x + .55f, h, z1 + .18f, c); }
        for (float z = z0 + 1.0f; z < z1 - .8f; z += 1.0f) { merlon(s, x0 - .18f, y, z, x0 + .3f, h, z + .55f, c); merlon(s, x1 - .3f, y, z, x1 + .18f, h, z + .55f, c); }
    }

    static void merlon(Mesh s, float x0, float y, float z0, float x1, float h, float z1, int c) {
        s.box(x0, y, z0, x1, y + h - .08f, z1, c);
        s.hip(x0, z0, x1, z1, y + h - .08f, .08f, 0, .06f, Look.lighter(c, .08f));
    }

    static void crenelRing(Mesh s, float x, float z, float r, float y, int n, int c) {
        s.cyl(x, z, y - .3f, y, r + .12f, r + .2f, 16, Look.darker(c, .12f));
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n; float px = x + (float) Math.cos(a) * r, pz = z + (float) Math.sin(a) * r;
            merlon(s, px - .2f, y, pz - .2f, px + .2f, .5f, pz + .2f, c);
        }
    }

    /** Fluted column with base and capital. */
    static void column(Mesh s, float x, float z, float y0, float h, float r, int c) {
        s.box(x - r * 1.35f, y0, z - r * 1.35f, x + r * 1.35f, y0 + .14f, z + r * 1.35f, Look.darker(c, .08f));
        s.cyl(x, z, y0 + .14f, y0 + .3f, r * 1.2f, r * 1.05f, 12, c);
        s.cyl(x, z, y0 + .3f, y0 + h - .32f, r, r * .86f, 12, Look.lighter(c, .06f));
        s.cyl(x, z, y0 + h - .32f, y0 + h - .16f, r * .9f, r * 1.25f, 12, c);
        s.box(x - r * 1.4f, y0 + h - .16f, z - r * 1.4f, x + r * 1.4f, y0 + h, z + r * 1.4f, Look.darker(c, .05f));
    }

    static void barrel(Mesh s, float x, float z, float y, float r) {
        s.cyl(x, z, y, y + r * 1.2f, r * .92f, r * 1.05f, 10, Look.vary(Look.WOOD, (int) (x * 13 + z), .1f));
        s.cyl(x, z, y + r * 1.2f, y + r * 2.4f, r * 1.05f, r * .92f, 10, Look.vary(Look.WOOD, (int) (x * 13 + z), .1f));
        for (float k : new float[]{.35f, 2.05f}) s.cyl(x, z, y + r * k, y + r * (k + .15f), r * 1.07f, r * 1.07f, 10, Look.IRON_DARK);
    }

    static void crate(Mesh s, float x, float z, float y, float h) {
        s.box(x - h / 2, y, z - h / 2, x + h / 2, y + h, z + h / 2, Look.WOOD_LIGHT);
        int e = Look.WOOD_DARK;
        s.box(x - h / 2 - .02f, y, z - h / 2 - .02f, x + h / 2 + .02f, y + .08f, z + h / 2 + .02f, e);
        s.box(x - h / 2 - .02f, y + h - .08f, z - h / 2 - .02f, x + h / 2 + .02f, y + h, z + h / 2 + .02f, e);
        s.prismL(x - h / 2, y + .08f, z + h / 2 + .02f, x + h / 2, y + h - .08f, z + h / 2 + .02f, .035f, .035f, 4, e);
    }

    static void dummy(Mesh s, float x, float z, int team) {
        post(s, x, z, 0, 1.6f, .07f, Look.WOOD_DARK);
        s.prismL(x - .5f, 1.25f, z, x + .5f, 1.25f, z, .06f, .06f, 6, Look.WOOD_DARK);
        s.ball(x, 1.15f, z, .3f, .4f, .3f, 10, 6, Look.THATCH);
        s.ball(x, 1.75f, z, .22f, .22f, .22f, 10, 6, Look.CLOTH);
        s.prismL(x, 1.2f, z + .28f, x, 1.2f, z + .31f, .2f, .2f, 10, team);
    }

    static void rocks(Mesh s, float x, float z, float r, int n, int c, int seed) {
        for (int i = 0; i < n; i++) {
            float a = (seed * 7 + i * 2.39f), rr = r * (.35f + .25f * (float) Math.abs(Math.sin(seed + i * 1.7)));
            float px = x + (float) Math.cos(a) * r * .55f, pz = z + (float) Math.sin(a) * r * .55f;
            s.ball(px, rr * .35f, pz, rr, rr * .75f, rr * .9f, 7, 4, Look.vary(c, seed + i, .15f));
        }
    }

    static void crystals(Mesh s, float x, float z, float r, int n, int c) {
        for (int i = 0; i < n; i++) {
            float a = i * 2.4f, px = x + (float) Math.cos(a) * r * .4f, pz = z + (float) Math.sin(a) * r * .4f, h = r * (.9f + (i % 3) * .35f);
            s.glow = i % 2 == 0;
            s.prismL(px, 0, pz, px + (float) Math.cos(a) * h * .25f, h, pz + (float) Math.sin(a) * h * .25f, r * .22f, 0, 5, i % 2 == 0 ? c : Look.lighter(c, .3f));
            s.glow = false;
        }
    }

    static void fence(Mesh s, float x0, float z0, float x1, float z1, float gapX0, float gapX1) {
        float step = 1.5f;
        for (float x = x0; x <= x1 + .01f; x += step) for (float z : new float[]{z0, z1}) {
            if (z == z1 && x > gapX0 && x < gapX1) continue;
            post(s, x, z, 0, 1.1f, .08f, Look.WOOD_DARK); s.cone(x, z, 1.1f, 1.3f, .08f, 6, Look.WOOD_DARK);
        }
        for (float z = z0 + step; z < z1 - .01f; z += step) for (float x : new float[]{x0, x1}) { post(s, x, z, 0, 1.1f, .08f, Look.WOOD_DARK); s.cone(x, z, 1.1f, 1.3f, .08f, 6, Look.WOOD_DARK); }
        for (float y : new float[]{.45f, .85f}) {
            log(s, x0, y, z0, x1, y, z0, .045f, Look.WOOD);
            log(s, x0, y, z0, x0, y, z1, .045f, Look.WOOD); log(s, x1, y, z0, x1, y, z1, .045f, Look.WOOD);
            log(s, x0, y, z1, gapX0, y, z1, .045f, Look.WOOD); log(s, gapX1, y, z1, x1, y, z1, .045f, Look.WOOD);
        }
    }

    static void crane(Mesh s, Mesh a, float x, float z, float h, float t) {
        log(s, x - 1.0f, 0, z, x, h, z, .12f, Look.WOOD); log(s, x + 1.0f, 0, z, x, h, z, .12f, Look.WOOD);
        log(s, x, 0, z - 1.0f, x, h, z, .1f, Look.WOOD_DARK);
        log(s, x - .55f, h * .45f, z, x + .55f, h * .45f, z, .06f, Look.WOOD_DARK);
        s.cyl(x, z, h - .2f, h + .1f, .2f, .2f, 8, Look.IRON_DARK);
        float sw = (float) Math.sin(t * .4f) * .6f, bx = x + (float) Math.cos(sw) * 2.4f, bz = z + (float) Math.sin(sw) * 2.4f;
        a.prismL(x - (bx - x) * .3f, h - .1f, z - (bz - z) * .3f, bx, h + .5f, bz, .09f, .07f, 6, Look.WOOD_LIGHT);
        a.prismL(bx, h + .45f, bz, bx, h - 1.6f, bz, .02f, .02f, 4, Look.ROPE);
        a.box(bx - .25f, h - 2.0f, bz - .25f, bx + .25f, h - 1.6f, bz + .25f, Look.STONE_DARK);
    }

    // ================================================================== Ancient World

    static void ancient(Mesh s, Mesh a, String kind, float w, float d, int L, int team, float t) {
        float cx = w / 2, cz = d / 2;
        switch (kind) {
            case "metal_extractor" -> {
                pad(s, w, d, Look.DIRT);
                s.cyl(cx, cz, 0, .7f, 1.15f, 1.08f, 14, Look.STONE);
                s.cyl(cx, cz, .7f, .85f, 1.18f, 1.18f, 14, Look.STONE_DARK);
                s.disc(cx, .86f, cz, .82f, 14, 0x221C18);
                s.glow = true; s.disc(cx, .3f, cz, .5f, 10, Look.ORE); s.glow = false;
                if (L >= 2) { rocks(s, .5f, d - .5f, .9f, 3, Look.STONE_DARK, 3); crystals(s, .55f, d - .55f, .5f, 4, Look.ORE); }
                log(s, .25f, 0, cz, cx, 2.5f, cz, .09f, Look.WOOD); log(s, w - .25f, 0, cz, cx, 2.5f, cz, .09f, Look.WOOD);
                log(s, .5f, 2.3f, cz, w - .5f, 2.3f, cz, .1f, Look.WOOD_DARK);
                s.prismL(cx - .25f, 2.3f, cz, cx + .25f, 2.3f, cz, .22f, .22f, 10, Look.WOOD);
                s.cyl(cx, cz, 1.0f, 2.1f, .02f, .02f, 4, Look.ROPE);
                if (L >= 3) { for (float x : new float[]{.2f, w - .2f}) for (float z : new float[]{.2f, d - .2f}) post(s, x, z, 0, 2.9f, .08f, Look.WOOD_DARK); hipRoof(s, 0, 0, w, d, 2.9f, .9f, .25f, .3f, Look.THATCH, Look.BONE); }
                teamGem(s, w - .3f, .8f, d - .3f, team);
                float bob = (float) Math.sin(t * 1.3f) * .5f;
                a.cyl(cx, cz, 1.15f + bob, 1.5f + bob, .2f, .24f, 10, Look.WOOD_LIGHT);
            }
            case "energy_gen" -> {   // round hut with a forge fire
                pad(s, w, d, Look.DIRT);
                float hz = cz - .3f;
                s.cyl(hz == 0 ? cx : cx, hz, 0, .35f, 1.55f, 1.5f, 16, Look.STONE_DARK);
                s.cyl(cx, hz, .35f, 1.7f, 1.45f, 1.4f, 16, 0xC99A6A);
                for (int i = 0; i < 8; i++) { double an = i * Math.PI / 4; s.prismL(cx + (float) Math.cos(an) * 1.45f, .35f, hz + (float) Math.sin(an) * 1.45f, cx + (float) Math.cos(an) * 1.41f, 1.75f, hz + (float) Math.sin(an) * 1.41f, .08f, .07f, 6, Look.WOOD_DARK); }
                door(s, cx, hz + 1.3f, .7f, 1.2f, Look.WOOD_DARK, false);
                coneRoof(s, cx, hz, 1.6f, 1.8f + L * .25f, 1.95f, 16, Look.THATCH, Look.BONE);
                if (L >= 2) s.cyl(cx, hz, 1.6f, 1.95f, 1.98f, 1.8f, 16, Look.THATCH_DARK);
                s.cyl(w - .7f, d - .6f, 0, .35f, .45f, .5f, 10, Look.STONE);
                s.cyl(w - .7f, d - .6f, .3f, .4f, .52f, .52f, 10, Look.STONE_DARK);
                if (L >= 3) { post(s, .5f, d - .5f, 0, 2.4f, .14f, Look.WOOD); s.ball(.5f, 2.55f, d - .5f, .22f, .26f, .22f, 10, 6, Look.BONE); }
                teamGem(s, .5f, .5f, .5f, team);
                fire(a, w - .7f, .4f, d - .6f, .3f + L * .04f, t);
                smoke(a, cx, 3.6f + L * .25f, hz, t, 0x8C8C8C);
            }
            case "converter" -> {   // clay kiln
                pad(s, w, d, Look.DIRT);
                s.cyl(cx, cz - .2f, 0, .4f, 1.75f, 1.7f, 16, Look.STONE_DARK);
                s.ball(cx, .4f, cz - .2f, 1.6f, 1.8f, 1.6f, 16, 10, Look.CLAY);
                for (int i = 0; i < 3; i++) s.cyl(cx, cz - .2f, .9f + i * .5f, .96f + i * .5f, 1.56f - i * .18f, 1.5f - i * .2f, 16, Look.darker(Look.CLAY, .15f));
                s.prismL(cx, .4f, cz + 1.2f, cx, .4f, cz + 1.55f, .5f, .5f, 12, Look.STONE_DARK);
                s.glow = true; s.prismL(cx, .4f, cz + 1.4f, cx, .4f, cz + 1.57f, .36f, .36f, 12, Look.FIRE); s.glow = false;
                s.cyl(cx + .7f, cz - 1.0f, 1.3f, 2.8f, .3f, .25f, 10, Look.darker(Look.CLAY, .15f));
                s.cyl(cx + .7f, cz - 1.0f, 2.8f, 2.95f, .33f, .33f, 10, Look.STONE_DARK);
                if (L >= 2) { s.cyl(cx - .8f, cz - .9f, 1.1f, 2.5f, .25f, .2f, 10, Look.darker(Look.CLAY, .15f)); s.cyl(cx - .8f, cz - .9f, 2.5f, 2.62f, .27f, .27f, 10, Look.STONE_DARK); }
                for (int i = 0; i < 3; i++) log(s, .3f, .15f + (i == 2 ? .25f : 0), d - .4f - (i % 2) * .32f - (i == 2 ? .16f : 0), 1.6f, .15f + (i == 2 ? .25f : 0), d - .4f - (i % 2) * .32f - (i == 2 ? .16f : 0), .13f, Look.vary(Look.WOOD, i, .15f));
                teamGem(s, w - .4f, .5f, d - .4f, team);
                smoke(a, cx + .7f, 2.95f, cz - 1.0f, t, 0x9A8C80);
            }
            case "storage" -> {   // timber store with barrels
                pad(s, w, d, Look.DIRT);
                plankWalls(s, .6f, .8f, w - .6f, d - 1.4f, 0, 1.7f, Look.WOOD);
                roof(s, .6f, .8f, w - .6f, d - 1.4f, 1.7f, 1.5f, true, .35f, Look.THATCH, Look.WOOD_LIGHT);
                door(s, cx, d - 1.4f, .9f, 1.3f, Look.WOOD_DARK, false);
                barrel(s, .9f, d - .6f, 0, .3f); barrel(s, 1.6f, d - .5f, 0, .3f); crate(s, w - 1f, d - .6f, 0, .7f);
                teamGem(s, w - .45f, 1.9f, d - 1.3f, team);
                if (L >= 2) { crate(s, w - 1.1f, d - .6f, .7f, .55f); barrel(s, 1.25f, d - .55f, .75f, .28f); plankWalls(s, w - .6f, 1.2f, w + .1f, d - 1.8f, 0, 1.1f, Look.WOOD_LIGHT); hipRoof(s, w - .6f, 1.2f, w + .1f, d - 1.8f, 1.1f, .35f, .15f, .1f, Look.THATCH_DARK, -1); }
            }
            case "tech_center" -> {   // Greek temple
                s.box(-.15f, -.4f, -.15f, w + .15f, .25f, d + .15f, Look.STONE_DARK);
                s.box(.15f, .25f, .15f, w - .15f, .45f, d - .15f, Look.STONE);
                s.box(.35f, .45f, .35f, w - .35f, .65f, d - .35f, Look.MARBLE);
                stoneWalls(s, 1.6f, 1.4f, w - 1.6f, d - 1.6f, .65f, 3.15f, Look.MARBLE);
                door(s, cx, d - 1.6f, 1.0f, 1.7f, Look.STONE, true);
                float y0 = .65f, ch = 2.65f;
                for (float x = .9f; x <= w - .8f; x += (w - 1.8f) / 3) { column(s, x, d - .8f, y0, ch, .24f, Look.MARBLE); column(s, x, .8f, y0, ch, .24f, Look.MARBLE); }
                if (L >= 2) for (float z = .8f + (d - 1.6f) / 3; z < d - 1.2f; z += (d - 1.6f) / 3) { column(s, .9f, z, y0, ch, .24f, Look.MARBLE); column(s, w - .9f, z, y0, ch, .24f, Look.MARBLE); }
                s.box(.45f, y0 + ch, .45f, w - .45f, y0 + ch + .25f, d - .45f, Look.MARBLE);
                s.box(.4f, y0 + ch + .25f, .4f, w - .4f, y0 + ch + .5f, d - .4f, Look.STONE);
                s.glow = true; s.box(.4f, y0 + ch + .32f, d - .42f, w - .4f, y0 + ch + .42f, d - .38f, team); s.glow = false;
                for (float x = .7f; x < w - .5f; x += .45f) s.box(x, y0 + ch + .05f, d - .5f, x + .2f, y0 + ch + .22f, d - .42f, Look.STONE_DARK);
                roof(s, .45f, .45f, w - .45f, d - .45f, y0 + ch + .5f, 1.25f, false, .25f, Look.TILE_RED, Look.MARBLE);
                s.ball(cx, y0 + ch + 1.1f, d - .4f, .3f, .3f, .08f, 10, 6, Look.GOLD);
                if (L >= 3) {   // golden statue on the steps
                    s.box(cx - .45f, .25f, d + .05f, cx + .45f, 1.15f, d + .9f, Look.MARBLE);
                    s.prismL(cx, 1.15f, d + .45f, cx, 2.1f, d + .45f, .26f, .2f, 10, Look.GOLD);
                    s.ball(cx, 2.36f, d + .45f, .2f, .22f, .2f, 10, 6, Look.GOLD);
                    s.prismL(cx + .2f, 1.9f, d + .45f, cx + .45f, 2.65f, d + .55f, .06f, .05f, 6, Look.GOLD);
                }
                if (L >= 2) for (float x : new float[]{.5f, w - .5f}) { s.cyl(x, d + .3f, 0, .9f, .08f, .1f, 8, Look.BRONZE); s.cyl(x, d + .3f, .9f, 1.1f, .3f, .22f, 10, Look.BRONZE); fire(a, x, 1.1f, d + .3f, .2f, t); }
            }
            case "watchtower" -> {
                pad(s, w, d, Look.DIRT);
                float h = 3.6f + L * .7f;
                s.cyl(cx, cz, 0, .5f, 1.3f, 1.25f, 12, Look.STONE_DARK);
                for (float x : new float[]{.35f, w - .35f}) for (float z : new float[]{.35f, d - .35f}) log(s, x, 0, z, x + (cx - x) * .15f, h, z + (cz - z) * .15f, .13f, Look.WOOD);
                for (float y : new float[]{h * .35f, h * .7f}) { log(s, .45f, y - .5f, .45f, w - .45f, y + .2f, .45f, .06f, Look.WOOD_DARK); log(s, w - .45f, y - .5f, d - .45f, .45f, y + .2f, d - .45f, .06f, Look.WOOD_DARK); }
                s.box(.05f, h, .05f, w - .05f, h + .22f, d - .05f, Look.WOOD_LIGHT);
                for (float x : new float[]{.15f, w - .15f}) for (float z : new float[]{.15f, d - .15f}) post(s, x, z, h + .22f, h + 1.7f, .07f, Look.WOOD_DARK);
                for (float y : new float[]{h + .5f, h + .8f}) { log(s, .15f, y, .15f, w - .15f, y, .15f, .04f, Look.WOOD); log(s, .15f, y, d - .15f, w - .15f, y, d - .15f, .04f, Look.WOOD); log(s, .15f, y, .15f, .15f, y, d - .15f, .04f, Look.WOOD); log(s, w - .15f, y, .15f, w - .15f, y, d - .15f, .04f, Look.WOOD); }
                coneRoof(s, cx, cz, h + 1.7f, 1.4f, 2.0f, 12, Look.THATCH, Look.BONE);
                for (float y = .3f; y < h; y += .45f) s.box(cx - .35f, y, d - .32f, cx + .35f, y + .07f, d - .24f, Look.WOOD_DARK);
                lantern(s, cx, h + 1.2f, cz, 0, 0);
                teamGem(s, cx, h + .3f, d - .1f, team);
                if (L >= 3) flagPole(s, a, cx, cz, h + 3.0f, 1.0f, team, t, .8f);
            }
            case "wall" -> {
                s.box(-.05f, -.3f, -.05f, 1.05f, .1f, 1.05f, Look.DIRT);
                if (L >= 2) { s.box(-.02f, 0, .1f, 1.02f, .7f, .9f, Look.STONE); s.box(-.04f, .6f, .08f, 1.04f, .72f, .92f, Look.STONE_DARK); }
                for (float x : new float[]{.17f, .5f, .83f}) {
                    float h = 2.1f + (x == .5f ? .25f : 0);
                    s.cyl(x, .5f, 0, h, .17f, .15f, 8, Look.vary(Look.WOOD, (int) (x * 10), .12f));
                    s.cone(x, .5f, h, h + .4f, .15f, 8, Look.WOOD_LIGHT);
                }
                log(s, 0, 1.4f, .32f, 1, 1.4f, .32f, .04f, Look.ROPE); log(s, 0, .8f, .32f, 1, .8f, .32f, .04f, Look.ROPE);
            }
            case "barracks" -> {   // Viking longhouse
                pad(s, w, d, Look.DIRT);
                if (L >= 3) plinth(s, .8f, .8f, w - .8f, d - .9f, .4f, Look.STONE);
                float y0 = L >= 3 ? .4f : 0;
                plankWalls(s, 1.1f, 1f, w - 1.1f, d - 1.1f, y0, y0 + 2.0f, Look.WOOD);
                door(s, cx, d - 1.1f, 1.0f, 1.5f, Look.WOOD_DARK, false);
                window(s, RIGHT, w - 1.1f, cz - .8f, y0 + .9f, .5f, .55f, Look.WOOD_DARK); window(s, LEFT, 1.1f, cz - .8f, y0 + .9f, .5f, .55f, Look.WOOD_DARK);
                roof(s, 1.1f, 1f, w - 1.1f, d - 1.1f, y0 + 2.0f, 2.3f, false, .45f, Look.THATCH, Look.WOOD_LIGHT);
                for (float z : new float[]{.72f, d - .82f}) {   // crossed gable beams with carved ends
                    log(s, cx - .2f, y0 + 3.9f, z, cx + .7f, y0 + 4.9f, z, .08f, Look.WOOD_DARK); log(s, cx + .2f, y0 + 3.9f, z, cx - .7f, y0 + 4.9f, z, .08f, Look.WOOD_DARK);
                    s.ball(cx + .75f, y0 + 4.95f, z, .12f, .1f, .1f, 8, 5, Look.WOOD_DARK); s.ball(cx - .75f, y0 + 4.95f, z, .12f, .1f, .1f, 8, 5, Look.WOOD_DARK);
                }
                for (float z = 1.6f; z < d - 1.4f; z += 1.6f) for (int sd = -1; sd <= 1; sd += 2) {
                    float x = sd < 0 ? .98f : w - .98f;
                    int col = (int) (z * 10) % 2 == 0 ? team : Look.CLOTH;
                    s.prismL(x, y0 + 1.1f, z, x + sd * .08f, y0 + 1.1f, z, .38f, .38f, 14, col);
                    s.prismL(x + sd * .08f, y0 + 1.1f, z, x + sd * .14f, y0 + 1.1f, z, .1f, .07f, 8, Look.IRON);
                }
                lantern(s, cx - 1.0f, y0 + 1.6f, d - 1.1f, 0, 1); lantern(s, cx + 1.0f, y0 + 1.6f, d - 1.1f, 0, 1);
                if (L >= 2) { dummy(s, .55f, d - .5f, team); post(s, w - .5f, d - .5f, 0, 1.2f, .06f, Look.WOOD_DARK); log(s, w - .6f, 1.0f, d - .5f, w - .1f, 1.0f, d - .5f, .04f, Look.WOOD_DARK); }
                flagPole(s, a, w - .5f, .5f, 0, 3.2f + L * .4f, team, t, 1.0f);
            }
            case "war_lodge" -> {
                pad(s, w, d, Look.DIRT);
                fence(s, .2f, .2f, w - .2f, d - .2f, cx - 1.2f, cx + 1.2f);
                plinth(s, 1.4f, .6f, w - 1.4f, d - 2.8f, .35f, Look.STONE);
                plankWalls(s, 1.6f, .8f, w - 1.6f, d - 3.0f, .35f, 2.9f, Look.WOOD);
                door(s, cx, d - 3.0f, 1.2f, 1.9f, Look.WOOD_DARK, false);
                for (float x : new float[]{2.4f, w - 2.4f}) window(s, FRONT, d - 3.0f, x, 1.4f, .5f, .6f, Look.WOOD_DARK);
                roof(s, 1.6f, .8f, w - 1.6f, d - 3.0f, 2.9f, 2.6f, true, .5f, Look.THATCH, Look.WOOD_LIGHT);
                if (L >= 2) for (float x : new float[]{1.3f, w - 1.3f}) {   // carved prows at the gable ends
                    float zm = (d - 2.2f) / 2;
                    log(s, x, 4.9f, zm, x + (x < cx ? -.6f : .6f), 5.9f, zm, .12f, Look.WOOD_DARK);
                    s.ball(x + (x < cx ? -.7f : .7f), 6.0f, zm, .18f, .14f, .26f, 8, 5, Look.WOOD_DARK);
                    s.glow = true; s.ball(x + (x < cx ? -.8f : .8f), 6.05f, zm + .12f, .04f, .04f, .04f, 6, 4, team); s.glow = false;
                }
                s.cyl(cx, d - 1.4f, 0, .25f, .6f, .65f, 12, Look.STONE); fire(a, cx, .25f, d - 1.4f, .3f, t);
                dummy(s, 1.2f, d - 1.3f, team); dummy(s, w - 1.2f, d - 1.3f, team);
                lantern(s, cx - 1.1f, 2.0f, d - 3.0f, 0, 1); lantern(s, cx + 1.1f, 2.0f, d - 3.0f, 0, 1);
                if (L >= 3) { for (float x : new float[]{.6f, 1.6f}) for (float z : new float[]{.6f, 1.6f}) post(s, x, z, 0, 3.4f, .09f, Look.WOOD); s.box(.4f, 3.4f, .4f, 1.8f, 3.6f, 1.8f, Look.WOOD_LIGHT); coneRoof(s, 1.1f, 1.1f, 3.6f, 1.0f, 1.1f, 10, Look.THATCH, Look.BONE); }
                flagPole(s, a, cx - 1.4f, d - .2f, 0, 3.2f, team, t, 1.0f); flagPole(s, a, cx + 1.4f, d - .2f, 0, 3.2f, team, t, 1.0f);
            }
            case "hall_of_legends" -> {
                s.box(-.15f, -.4f, -.15f, w + .15f, .3f, d + .15f, Look.STONE_DARK);
                s.box(.2f, .3f, .2f, w - .2f, .55f, d - .2f, Look.STONE);
                s.box(.45f, .55f, .45f, w - .45f, .8f, d - .45f, Look.MARBLE);
                stoneWalls(s, 2.2f, 1.2f, w - 2.2f, d - 3.0f, .8f, 4.4f, Look.STONE);
                door(s, cx, d - 3.0f, 1.4f, 2.3f, Look.MARBLE, true);
                for (float x : new float[]{3.0f, w - 3.0f}) window(s, FRONT, d - 3.0f, x, 2.0f, .55f, 1.0f, Look.MARBLE);
                for (float x = 1.2f; x <= w - 1.1f; x += (w - 2.4f) / 5) column(s, x, d - 1.1f, .8f, 3.7f, .3f, Look.MARBLE);
                s.box(.9f, 4.5f, d - 1.6f, w - .9f, 4.8f, d - .6f, Look.MARBLE);
                s.box(.85f, 4.8f, .85f, w - .85f, 5.1f, d - .55f, Look.STONE);
                s.glow = true; s.box(.85f, 4.88f, d - .57f, w - .85f, 4.98f, d - .52f, team); s.glow = false;
                roof(s, .9f, .9f, w - .9f, d - .6f, 5.1f, 2.6f, false, .4f, Look.THATCH_DARK, Look.MARBLE);
                s.prismL(cx, 7.75f, .5f, cx, 7.75f, d - .1f, .1f, .1f, 8, Look.GOLD);
                s.box(cx - .7f, .55f, d - .2f, cx + .7f, 1.5f, d + .9f, Look.MARBLE);   // hero statue
                s.prismL(cx, 1.5f, d + .35f, cx, 3.0f, d + .35f, .4f, .32f, 12, Look.GOLD);
                s.ball(cx, 3.35f, d + .35f, .3f, .32f, .3f, 12, 8, Look.GOLD);
                s.prismL(cx + .3f, 2.8f, d + .35f, cx + .62f, 4.1f, d + .5f, .09f, .07f, 8, Look.GOLD);
                s.prismL(cx - .3f, 2.6f, d + .35f, cx - .55f, 2.0f, d + .55f, .32f, .25f, 12, team);
                for (float x : new float[]{.8f, w - .8f}) { s.cyl(x, d + .3f, 0, 1.2f, .1f, .12f, 8, Look.BRONZE); s.cyl(x, d + .3f, 1.2f, 1.45f, .38f, .28f, 12, Look.BRONZE); fire(a, x, 1.45f, d + .3f, .27f, t); }
                if (L >= 2) for (float x : new float[]{.9f, w - .9f}) { s.cyl(x, .9f, 0, 5.5f, .9f, .8f, 14, Look.STONE); s.cyl(x, .9f, 5.3f, 5.5f, .95f, .95f, 14, Look.STONE_DARK); coneRoof(s, x, .9f, 5.5f, 1.6f, 1.15f, 14, Look.THATCH_DARK, Look.GOLD); flagPole(s, a, x, .9f, 7.5f, 1.0f, team, t, 1.0f); }
            }
            case "siege_yard" -> {
                pad(s, w, d, Look.DIRT);
                fence(s, .2f, .2f, w - .2f, d - .2f, cx - 2.0f, cx + 2.0f);
                plankWalls(s, .8f, .8f, 4.6f, 3.6f, 0, 2.3f, Look.WOOD);
                roof(s, .8f, .8f, 4.6f, 3.6f, 2.3f, 1.3f, true, .3f, Look.THATCH, Look.WOOD_LIGHT);
                door(s, 2.7f, 3.6f, 1.4f, 1.6f, Look.WOOD_DARK, false);
                crane(s, a, w - 2.5f, 2.5f, 5.5f, t);
                s.box(cx - 1.0f, .3f, cz + .5f, cx + 1.0f, .5f, cz + 2.0f, Look.WOOD);
                for (float x : new float[]{cx - 1.1f, cx + 1.1f}) for (float z : new float[]{cz + .7f, cz + 1.8f}) { s.prismL(x, .35f, z, x + (x < cx ? -.08f : .08f), .35f, z, .35f, .35f, 12, Look.WOOD_DARK); s.prismL(x, .35f, z, x + (x < cx ? -.1f : .1f), .35f, z, .1f, .1f, 8, Look.IRON_DARK); }
                log(s, cx, .9f, cz + .3f, cx, .9f, cz + 2.4f, .1f, Look.WOOD_LIGHT);
                for (int i = 0; i < 5; i++) log(s, 1.0f, .2f + (i / 2) * .33f, d - 2.0f + (i % 2) * .36f + (i / 2 == 1 ? .18f : 0), 3.8f, .2f + (i / 2) * .33f, d - 2.0f + (i % 2) * .36f + (i / 2 == 1 ? .18f : 0), .17f, Look.vary(Look.WOOD, i, .15f));
                teamGem(s, .8f, 2.6f, 3.6f, team);
                if (L >= 2) { crane(s, a, 2.5f, d - 4.0f, 4.5f, t + 1.7f); crate(s, w - 1.3f, d - 1.3f, 0, .8f); crate(s, w - 2.2f, d - 1.2f, 0, .7f); }
                flagPole(s, a, w - .6f, d - .6f, 0, 3.5f, team, t, 1.0f);
            }
            default -> generic(s, a, w, d, L, team, t);
        }
    }

    // ================================================================== Kingdoms

    static void kingdoms(Mesh s, Mesh a, String kind, float w, float d, int L, int team, float t) {
        float cx = w / 2, cz = d / 2;
        int flag = Look.STONE_COOL_DARK;
        switch (kind) {
            case "metal_extractor" -> {   // mine entrance
                pad(s, w, d, flag);
                s.hip(.1f, .1f, w - .1f, d - .8f, 0, 1.9f, 0, .5f, Look.STONE_COOL_DARK);
                rocks(s, cx, 1.0f, 1.2f, 4, Look.STONE_COOL, 5);
                s.box(cx - .7f, 0, d - 1.15f, cx + .7f, 1.6f, d - .7f, Look.WOOD_DARK);
                s.box(cx - .5f, 0, d - .75f, cx + .5f, 1.35f, d - .68f, 0x1E1A18);
                s.box(cx - .85f, 1.45f, d - .78f, cx + .85f, 1.65f, d - .6f, Look.WOOD);
                for (float x : new float[]{cx - .3f, cx + .3f}) s.box(x - .04f, .02f, d - .7f, x + .04f, .08f, d + .15f, Look.IRON_DARK);
                for (float z = d - .6f; z < d + .15f; z += .25f) s.box(cx - .42f, .0f, z, cx + .42f, .05f, z + .1f, Look.WOOD_DARK);
                s.box(cx - .35f, .2f, d - .45f, cx + .35f, .55f, d - .05f, Look.WOOD);
                crystals(s, cx, d - .25f, .3f, 3, Look.ORE);
                lantern(s, cx + .7f, 1.1f, d - .7f, 0, 1);
                teamGem(s, cx - .7f, 1.75f, d - .7f, team);
                if (L >= 2) { post(s, .3f, .3f, 0, 2.6f, .08f, Look.WOOD_DARK); post(s, w - .3f, .3f, 0, 2.6f, .08f, Look.WOOD_DARK); log(s, .3f, 2.5f, .3f, w - .3f, 2.5f, .3f, .08f, Look.WOOD_DARK); s.prismL(cx, 2.5f, .1f, cx, 2.5f, .5f, .3f, .3f, 12, Look.WOOD); }
                if (L >= 3) { hipRoof(s, 0, 0, w, .9f, 2.6f, .6f, .15f, .1f, Look.SLATE, -1); flagPole(s, a, w - .3f, .3f, 3.2f, 1.0f, team, t, .7f); }
            }
            case "energy_gen" -> {   // windmill
                pad(s, w, d, flag);
                float h = 3.6f + L * .6f;
                s.cyl(cx, cz, 0, .6f, 1.72f, 1.66f, 16, Look.STONE_COOL);
                s.cyl(cx, cz, .6f, h, 1.6f, 1.15f, 16, Look.PLASTER);
                for (float y = 1.4f; y < h - .3f; y += 1.3f) s.cyl(cx, cz, y, y + .1f, 1.6f - (y / h) * .45f + .03f, 1.6f - ((y + .1f) / h) * .45f + .03f, 16, Look.WOOD_DARK);
                door(s, cx, cz + 1.55f, .7f, 1.3f, Look.WOOD_DARK, true);
                window(s, FRONT, cz + 1.28f, cx, h * .58f, .32f, .45f, Look.WOOD_DARK);
                coneRoof(s, cx, cz, h, 1.5f, 1.45f, 16, Look.SLATE, Look.GOLD);
                s.prismL(cx, h - .3f, cz + 1.0f, cx, h - .3f, cz + 1.75f, .14f, .12f, 10, Look.WOOD_DARK);
                s.ball(cx, h - .3f, cz + 1.8f, .2f, .2f, .2f, 10, 6, Look.IRON_DARK);
                teamGem(s, cx, h + .1f, cz + 1.2f, team);
                float rot = t * (.9f + L * .25f);
                for (int i = 0; i < 4; i++) {
                    float ang = rot + i * (float) Math.PI / 2, ux = (float) Math.cos(ang), uy = (float) Math.sin(ang), hy = h - .3f, hz = cz + 1.85f;
                    a.prismL(cx, hy, hz, cx + ux * 2.6f, hy + uy * 2.6f, hz, .05f, .04f, 4, Look.WOOD_DARK);
                    float px = -uy, py = ux;
                    for (int k = 0; k < 4; k++) { float f0 = .6f + k * .48f; a.prismL(cx + ux * f0, hy + uy * f0, hz, cx + ux * f0 + px * .55f, hy + uy * f0 + py * .55f, hz, .02f, .02f, 4, Look.WOOD); }
                    a.quadSail(cx + ux * .6f, hy + uy * .6f, cx + ux * 2.5f, hy + uy * 2.5f, px * .55f, py * .55f, hz + .03f, i % 2 == 0 ? Look.CLOTH : team);
                }
            }
            case "converter" -> {   // alchemist's workshop
                pad(s, w, d, flag);
                plasterWalls(s, .5f, .6f, w - .5f, d - .8f, 0, 2.2f, Look.WOOD_DARK);
                door(s, cx - .55f, d - .8f, .7f, 1.3f, Look.WOOD_DARK, false);
                window(s, FRONT, d - .8f, cx + .75f, 1.0f, .5f, .55f, Look.WOOD_DARK);
                roof(s, .5f, .6f, w - .5f, d - .8f, 2.2f, 1.4f, true, .3f, Look.TILE_RED, Look.PLASTER);
                s.box(w - 1.25f, 2.0f, .85f, w - .65f, 4.1f, 1.45f, Look.STONE_COOL);
                s.box(w - 1.33f, 4.1f, .77f, w - .57f, 4.25f, 1.53f, Look.STONE_COOL_DARK);
                if (L >= 2) { s.cyl(.6f, d - .3f, 0, .5f, .25f, .25f, 10, 0x6E4ABE); s.glow = true; s.ball(.6f, .6f, d - .3f, .12f, .12f, .12f, 8, 5, 0xC7A2FF); s.glow = false; s.cyl(1.2f, d - .3f, 0, .4f, .2f, .2f, 10, 0x4AAE7E); }
                lantern(s, cx - 1.05f, 1.5f, d - .8f, 0, 1);
                teamGem(s, .6f, 2.4f, d - .6f, team);
                smoke(a, w - .95f, 4.25f, 1.15f, t, 0xB28CE0);
            }
            case "storage" -> {   // granary barn
                pad(s, w, d, flag);
                plankWalls(s, .5f, .7f, w - .5f, d - 1.0f, 0, 2.0f, Look.WOOD);
                s.box(cx - .8f, 0, d - .98f, cx + .8f, 1.5f, d - .92f, 0x7A3A26);
                s.prismL(cx - .8f, .1f, d - .9f, cx + .8f, 1.4f, d - .9f, .06f, .06f, 4, Look.PLASTER);
                s.prismL(cx + .8f, .1f, d - .9f, cx - .8f, 1.4f, d - .9f, .06f, .06f, 4, Look.PLASTER);
                s.box(cx - .9f, 1.5f, d - 1.0f, cx + .9f, 1.62f, d - .88f, Look.PLASTER);
                roof(s, .5f, .7f, w - .5f, d - 1.0f, 2.0f, 1.7f, true, .35f, Look.SLATE, Look.WOOD);
                window(s, FRONT, d - 1.0f, cx, 2.35f, .4f, .4f, Look.WOOD_DARK);
                for (float x : new float[]{1.0f, 1.6f}) s.ball(x, .3f, d - .4f, .3f, .32f, .26f, 10, 6, Look.CLOTH);
                lantern(s, cx + 1.1f, 1.4f, d - 1.0f, 0, 1);
                teamGem(s, w - .4f, 2.2f, d - .9f, team);
                if (L >= 2) { s.cyl(w - .2f, 1.0f, 0, 2.6f, .75f, .75f, 16, Look.PLASTER); s.cyl(w - .2f, 1.0f, 1.2f, 1.3f, .78f, .78f, 16, Look.WOOD_DARK); coneRoof(s, w - .2f, 1.0f, 2.6f, 1.1f, .9f, 16, Look.SLATE, Look.GOLD); }
            }
            case "tech_center" -> {   // domed academy
                pad(s, w, d, flag);
                plinth(s, .3f, .3f, w - .3f, d - .3f, .4f, Look.STONE_COOL);
                stoneWalls(s, 1f, 1f, w - 1f, d - 1.3f, .4f, 3.45f, Look.PLASTER);
                for (float x = 1.7f; x < w - 1.2f; x += 1.2f) if (Math.abs(x - cx) > .8f) window(s, FRONT, d - 1.3f, x + .25f, 1.4f, .5f, 1.1f, Look.STONE_COOL);
                for (float z = 1.8f; z < d - 1.6f; z += 1.3f) { window(s, RIGHT, w - 1f, z, 1.4f, .45f, 1.0f, Look.STONE_COOL); window(s, LEFT, 1f, z, 1.4f, .45f, 1.0f, Look.STONE_COOL); }
                for (float x : new float[]{cx - 1.0f, cx + 1.0f}) column(s, x, d - .6f, .4f, 2.9f, .22f, Look.MARBLE);
                s.box(cx - 1.45f, 3.3f, d - 1.3f, cx + 1.45f, 3.6f, d - .3f, Look.STONE_COOL);
                roof(s, cx - 1.4f, d - 1.3f, cx + 1.4f, d - .3f, 3.6f, .8f, false, .1f, Look.TILE_RED, Look.PLASTER);
                door(s, cx, d - 1.3f, .9f, 1.7f, Look.STONE_COOL, true);
                s.cyl(cx, cz - .2f, 3.45f, 4.3f, 2.05f, 2.0f, 20, Look.PLASTER);
                for (int i = 0; i < 8; i++) { double an = i * Math.PI / 4; s.box(cx + (float) Math.cos(an) * 2.0f - .12f, 3.5f, cz - .2f + (float) Math.sin(an) * 2.0f - .12f, cx + (float) Math.cos(an) * 2.0f + .12f, 4.3f, cz - .2f + (float) Math.sin(an) * 2.0f + .12f, Look.STONE_COOL); }
                s.ball(cx, 4.3f, cz - .2f, 2.0f, 1.9f, 2.0f, 20, 10, Look.COPPER);
                for (int i = 0; i < 8; i++) { double an = i * Math.PI / 4; s.prismL(cx + (float) Math.cos(an) * 2.0f, 4.35f, cz - .2f + (float) Math.sin(an) * 2.0f, cx + (float) Math.cos(an) * .3f, 6.05f, cz - .2f + (float) Math.sin(an) * .3f, .06f, .05f, 6, Look.darker(Look.COPPER, .3f)); }
                s.cyl(cx, cz - .2f, 6.0f, 6.8f, .38f, .32f, 12, Look.PLASTER);
                s.glow = true; s.cyl(cx, cz - .2f, 6.2f, 6.6f, .39f, .34f, 12, Look.WINDOW); s.glow = false;
                s.cyl(cx, cz - .2f, 6.8f, 6.95f, .45f, .45f, 12, Look.STONE_COOL);
                s.ball(cx, 7.1f, cz - .2f, .22f, .22f, .22f, 10, 6, Look.GOLD);
                teamGem(s, cx, 3.75f, d - .25f, team);
                if (L >= 2) for (float x : new float[]{.1f, w - 1.6f}) { plasterWalls(s, x, 1.6f, x + 1.5f, d - 2.0f, 0, 2.2f, Look.WOOD_DARK); hipRoof(s, x, 1.6f, x + 1.5f, d - 2.0f, 2.2f, .7f, .15f, .3f, Look.TILE_RED, -1); }
                if (L >= 3) for (float x : new float[]{1.3f, w - 1.3f}) { s.cyl(x, .9f, 0, 4.6f, .45f, .4f, 12, Look.PLASTER); s.ball(x, 4.6f, .9f, .48f, .6f, .48f, 12, 7, Look.COPPER); s.ball(x, 5.25f, .9f, .1f, .1f, .1f, 8, 5, Look.GOLD); }
                flagPole(s, a, cx, cz - .2f, 7.2f, 1.0f, team, t, .8f);
            }
            case "watchtower" -> {
                pad(s, w, d, flag);
                float h = 4.5f + L * .9f;
                s.cyl(cx, cz, 0, .7f, 1.48f, 1.42f, 16, Look.STONE_COOL_DARK);
                s.cyl(cx, cz, .7f, h, 1.35f, 1.15f, 16, Look.STONE_COOL);
                for (float y = 1.6f; y < h - .4f; y += 1.0f) s.cyl(cx, cz, y, y + .07f, 1.35f - (y / h) * .2f + .03f, 1.35f - ((y + .07f) / h) * .2f + .03f, 16, Look.STONE_COOL_DARK);
                for (float y = 1.9f; y < h - .6f; y += 1.6f) { window(s, FRONT, cz + 1.18f, cx, y, .18f, .55f, Look.STONE_COOL_DARK); window(s, RIGHT, cx + 1.18f, cz, y + .5f, .18f, .55f, Look.STONE_COOL_DARK); }
                door(s, cx, cz + 1.3f, .6f, 1.2f, Look.STONE_COOL, true);
                for (int i = 0; i < 10; i++) { double an = i * Math.PI / 5; s.box(cx + (float) Math.cos(an) * 1.3f - .1f, h - .45f, cz + (float) Math.sin(an) * 1.3f - .1f, cx + (float) Math.cos(an) * 1.3f + .1f, h, cz + (float) Math.sin(an) * 1.3f + .1f, Look.STONE_COOL_DARK); }
                crenelRing(s, cx, cz, 1.32f, h + .25f, 10, Look.STONE_COOL);
                teamGem(s, cx + .9f, h + .3f, cz + .9f, team);
                if (L >= 2) { post(s, cx, cz, h, h + 1.5f, .1f, Look.WOOD_DARK); coneRoof(s, cx, cz, h + 1.4f, 1.3f, 1.25f, 16, Look.SLATE, Look.GOLD); flagPole(s, a, cx, cz, h + 3.1f, .9f, team, t, .8f); }
                else flagPole(s, a, cx, cz, h + .25f, 1.4f, team, t, .8f);
            }
            case "wall" -> {
                s.box(-.05f, -.3f, -.05f, 1.05f, .1f, 1.05f, flag);
                float h = 2.0f + L * .4f;
                s.box(-.04f, 0, .06f, 1.04f, .45f, .94f, Look.STONE_COOL_DARK);
                s.box(0, .45f, .1f, 1, h, .9f, Look.vary(Look.STONE_COOL, 7, .05f));
                for (float y = .9f; y < h - .2f; y += .55f) s.box(-.01f, y, .08f, 1.01f, y + .05f, .92f, Look.darker(Look.STONE_COOL, .12f));
                merlon(s, .08f, h, .1f, .52f, .5f, .9f, Look.STONE_COOL);
                if (L >= 2) { s.glow = true; s.box(-.02f, h * .62f, .9f, 1.02f, h * .62f + .1f, .93f, team); s.glow = false; }
            }
            case "barracks" -> {   // Tudor hall
                pad(s, w, d, flag);
                float h1 = 2.4f, h2 = L >= 2 ? 4.0f : h1;
                plasterWalls(s, 1.0f, 1.0f, w - 1.0f, d - 1.2f, 0, h1, Look.WOOD_DARK);
                door(s, cx, d - 1.2f, 1.0f, 1.6f, Look.WOOD_DARK, true);
                for (float x : new float[]{1.75f, w - 1.75f}) window(s, FRONT, d - 1.2f, x, 1.0f, .55f, .7f, Look.WOOD_DARK);
                for (float z = 1.8f; z < d - 1.6f; z += 1.4f) { window(s, RIGHT, w - 1.0f, z, 1.0f, .5f, .65f, Look.WOOD_DARK); window(s, LEFT, 1.0f, z, 1.0f, .5f, .65f, Look.WOOD_DARK); }
                if (L >= 2) {   // jettied upper floor
                    s.box(.75f, h1 - .05f, .75f, w - .75f, h1 + .12f, d - .9f, Look.WOOD_DARK);
                    s.box(.8f, h1 + .12f, .8f, w - .8f, h2, d - .95f, Look.PLASTER);
                    timber(s, .8f, .8f, w - .8f, d - .95f, h1 + .12f, h2, Look.WOOD_DARK);
                    for (float x : new float[]{1.6f, cx, w - 1.6f}) window(s, FRONT, d - .95f, x, h1 + .55f, .5f, .6f, Look.WOOD_DARK);
                }
                roof(s, .8f, .8f, w - .8f, d - .95f, h2, 2.0f, true, .35f, Look.TILE_RED, Look.PLASTER);
                s.box(w - 1.8f, h2 + .8f, 1.3f, w - 1.2f, h2 + 2.6f, 1.9f, Look.STONE_COOL);
                s.box(w - 1.88f, h2 + 2.6f, 1.22f, w - 1.12f, h2 + 2.75f, 1.98f, Look.STONE_COOL_DARK);
                if (L >= 3) { plasterWalls(s, cx - .6f, d - 1.6f, cx + .6f, d - .9f, h2 + .1f, h2 + 1.0f, Look.WOOD_DARK); window(s, FRONT, d - .9f, cx, h2 + .3f, .4f, .45f, Look.WOOD_DARK); roof(s, cx - .6f, d - 1.9f, cx + .6f, d - .9f, h2 + 1.0f, .6f, false, .1f, Look.TILE_RED, Look.PLASTER); }
                lantern(s, cx - .95f, 1.5f, d - 1.2f, 0, 1); lantern(s, cx + .95f, 1.5f, d - 1.2f, 0, 1);
                s.banner(cx, h1 - .1f, d - 1.1f, .8f, 1.0f, team, Look.GOLD);
                dummy(s, .5f, d - .5f, team);
                flagPole(s, a, w - .5f, d - .5f, 0, 3.4f + L * .4f, team, t, 1.0f);
                smoke(a, w - 1.5f, h2 + 2.75f, 1.6f, t, 0x9A9A9A);
            }
            case "keep_workshop" -> {   // castle keep
                pad(s, w, d, flag);
                float h = 4.6f + L * .8f;
                stoneWalls(s, 1.8f, 1.8f, w - 1.8f, d - 1.8f, 0, h, Look.STONE_COOL);
                crenels(s, 1.8f, 1.8f, w - 1.8f, d - 1.8f, h, .55f, Look.STONE_COOL);
                door(s, cx, d - 1.8f, 1.5f, 1.9f, Look.STONE_COOL_DARK, true);
                s.box(cx - .9f, 2.1f, d - 1.8f, cx + .9f, 2.4f, d - 1.55f, Look.STONE_COOL_DARK);
                for (float y = 2.9f; y < h - .8f; y += 1.5f) for (float x : new float[]{cx - 1.2f, cx + 1.2f}) window(s, FRONT, d - 1.8f, x, y, .22f, .6f, Look.STONE_COOL_DARK);
                for (float x : new float[]{1.6f, w - 1.6f}) for (float z : new float[]{1.6f, d - 1.6f}) {
                    s.cyl(x, z, 0, .7f, 1.1f, 1.05f, 16, Look.STONE_COOL_DARK);
                    s.cyl(x, z, .7f, h + 1.2f, 1.0f, .9f, 16, Look.STONE_COOL);
                    s.cyl(x, z, h + .9f, h + 1.25f, 1.08f, 1.08f, 16, Look.STONE_COOL_DARK);
                    window(s, FRONT, z + .88f, x, h - .6f, .16f, .5f, Look.STONE_COOL_DARK);
                    if (L >= 2) coneRoof(s, x, z, h + 1.25f, 1.7f, 1.15f, 16, Look.SLATE, Look.GOLD);
                    else crenelRing(s, x, z, .92f, h + 1.45f, 8, Look.STONE_COOL);
                }
                for (float x : new float[]{cx - 1.3f, cx + 1.3f}) s.banner(x, h - .6f, d - 1.75f, .6f, 1.8f, team, Look.GOLD);
                lantern(s, cx - 1.0f, 1.4f, d - 1.8f, 0, 1); lantern(s, cx + 1.0f, 1.4f, d - 1.8f, 0, 1);
                flagPole(s, a, cx, cz, h, 2.0f, team, t, 1.2f);
            }
            case "royal_court" -> {   // dynasty pagoda court
                s.box(-.15f, -.4f, -.15f, w + .15f, .3f, d + .15f, Look.STONE_COOL_DARK);
                plinth(s, .3f, .3f, w - .3f, d - .3f, .55f, Look.STONE_COOL);
                plinth(s, .8f, .8f, w - .8f, d - .8f, .9f, Look.MARBLE);
                for (float x = cx - 1.2f; x <= cx + 1.21f; x += .4f) s.box(x, .3f, d - .8f, x + .4f, .3f + (x - cx + 1.6f) * 0 + .55f, d - .3f, Look.STONE_COOL);
                for (float x = 2.3f; x <= w - 2.2f; x += (w - 4.6f) / 4) for (float z : new float[]{2.3f, d - 2.3f}) { post(s, x, z, .9f, 3.3f, .17f, 0xA8382A); s.cyl(x, z, .9f, 1.1f, .24f, .22f, 8, Look.STONE_COOL_DARK); }
                for (float z = 2.3f + (d - 4.6f) / 4; z < d - 2.4f; z += (d - 4.6f) / 4) for (float x : new float[]{2.3f, w - 2.3f}) post(s, x, z, .9f, 3.3f, .17f, 0xA8382A);
                s.box(2.6f, .9f, 2.6f, w - 2.6f, 3.3f, d - 2.6f, Look.PLASTER);
                for (float x = 3.2f; x < w - 3.0f; x += .9f) if (Math.abs(x + .3f - cx) > .9f) window(s, FRONT, d - 2.6f, x + .3f, 1.5f, .5f, 1.2f, 0xA8382A);
                door(s, cx, d - 2.6f, 1.2f, 2.0f, 0xA8382A, false);
                s.box(2.0f, 3.1f, 2.0f, w - 2.0f, 3.3f, d - 2.0f, 0xA8382A);
                pagodaRoof(s, 1.6f, 1.6f, w - 1.6f, d - 1.6f, 3.3f, 1.1f);
                s.box(3.4f, 4.3f, 3.4f, w - 3.4f, 5.9f, d - 3.4f, 0xA8382A);
                for (float x = 3.8f; x < w - 3.6f; x += .8f) window(s, FRONT, d - 3.4f, x + .2f, 4.6f, .4f, .8f, Look.GOLD);
                pagodaRoof(s, 2.6f, 2.6f, w - 2.6f, d - 2.6f, 5.9f, .9f);
                float top = 6.8f;
                if (L >= 2) { s.box(4.2f, 6.8f, 4.2f, w - 4.2f, 8.0f, d - 4.2f, 0xA8382A); pagodaRoof(s, 3.5f, 3.5f, w - 3.5f, d - 3.5f, 8.0f, .8f); top = 8.8f; }
                for (int i = 0; i < 5; i++) s.cyl(cx, cz, top + i * .2f, top + i * .2f + .12f, .2f - i * .025f, .2f - i * .025f, 10, Look.GOLD);
                s.cyl(cx, cz, top, top + 1.3f, .05f, .03f, 8, Look.GOLD);
                s.ball(cx, top + 1.35f, cz, .14f, .18f, .14f, 10, 6, Look.GOLD);
                for (float x : new float[]{1.0f, w - 1.0f}) {
                    s.cyl(x, d - .5f, .3f, .9f, .25f, .2f, 8, Look.STONE_COOL_DARK); s.cyl(x, d - .5f, .9f, 1.6f, .08f, .08f, 8, Look.STONE_COOL_DARK);
                    s.hip(x - .3f, d - .8f, x + .3f, d - .2f, 1.9f, .3f, .1f, .3f, Look.STONE_COOL_DARK);
                    s.glow = true; s.box(x - .18f, 1.55f, d - .68f, x + .18f, 1.9f, d - .32f, Look.WINDOW); s.glow = false;
                }
                for (float x : new float[]{cx - 1.0f, cx + 1.0f}) s.banner(x, 3.0f, d - 2.55f, .5f, 1.4f, team, Look.GOLD);
                teamGem(s, cx, 3.45f, d - 1.95f, team);
                flagPole(s, a, .7f, .7f, .55f, 4.0f, team, t, 1.2f); flagPole(s, a, w - .7f, .7f, .55f, 4.0f, team, t, 1.2f);
            }
            case "siege_works" -> {
                pad(s, w, d, flag);
                stoneWalls(s, .2f, .2f, w - .2f, .9f, 0, 1.8f, Look.STONE_COOL);
                stoneWalls(s, .2f, .9f, .9f, d - .2f, 0, 1.8f, Look.STONE_COOL);
                plasterWalls(s, 1.2f, 1.1f, 5.2f, 4.0f, 0, 2.6f, Look.WOOD_DARK);
                roof(s, 1.2f, 1.1f, 5.2f, 4.0f, 2.6f, 1.5f, true, .3f, Look.TILE_RED, Look.PLASTER);
                door(s, 3.2f, 4.0f, 1.6f, 1.9f, Look.WOOD_DARK, false);
                window(s, RIGHT, 5.2f, 2.5f, 1.2f, .5f, .6f, Look.WOOD_DARK);
                crane(s, a, w - 2.4f, 2.6f, 6.0f, t);
                s.box(cx - .9f, .3f, cz + 1.2f, cx + .9f, .5f, cz + 3.2f, Look.WOOD);
                for (float x : new float[]{cx - 1.0f, cx + 1.0f}) for (float z : new float[]{cz + 1.4f, cz + 3.0f}) { s.prismL(x, .35f, z, x + (x < cx ? -.08f : .08f), .35f, z, .35f, .35f, 12, Look.WOOD_DARK); s.prismL(x, .35f, z, x + (x < cx ? -.1f : .1f), .35f, z, .1f, .1f, 8, Look.IRON_DARK); }
                for (float x : new float[]{cx - .7f, cx + .7f}) log(s, x, .5f, cz + 2.0f, x, 1.8f, cz + 1.8f, .08f, Look.WOOD_DARK);
                log(s, cx - .7f, 1.75f, cz + 1.8f, cx + .7f, 1.75f, cz + 1.8f, .07f, Look.WOOD_DARK);
                log(s, cx, .6f, cz + 3.0f, cx, 2.2f, cz + 1.4f, .08f, Look.WOOD_LIGHT);
                for (int i = 0; i < 3; i++) s.ball(w - 1.5f + (i % 2) * .5f, .35f + (i / 2) * .5f, d - 1.3f, .32f, .3f, .32f, 10, 6, Look.STONE_COOL_DARK);
                lantern(s, 2.0f, 1.8f, 4.0f, 0, 1);
                teamGem(s, .55f, 2.0f, .55f, team);
                if (L >= 2) { crane(s, a, 2.5f, d - 3.0f, 5.0f, t + 2.1f); for (float x = 1.6f; x < w - 1.0f; x += 1.6f) merlon(s, x, 1.8f, .2f, x + .7f, .55f, .9f, Look.STONE_COOL); }
                flagPole(s, a, w - .6f, d - .6f, 0, 3.6f, team, t, 1.0f);
            }
            default -> generic(s, a, w, d, L, team, t);
        }
    }

    /** Wide, flared two-step roof (dynasty) with gold ridge ends. */
    static void pagodaRoof(Mesh s, float x0, float z0, float x1, float z1, float y, float h) {
        s.hip(x0, z0, x1, z1, y, h * .22f, .9f, .9f, Look.darker(Look.SLATE, .25f));
        hipRoof(s, x0, z0, x1, z1, y + h * .22f, h * .78f, 0, Math.min(x1 - x0, z1 - z0) * .32f, Look.darker(Look.SLATE, .05f), -1);
        for (float[] c : new float[][]{{x0 - .9f, z0 - .9f}, {x1 + .9f, z0 - .9f}, {x0 - .9f, z1 + .9f}, {x1 + .9f, z1 + .9f}})
            s.prismL(c[0], y + h * .22f, c[1], c[0] + Math.signum(c[0] - (x0 + x1) / 2) * .25f, y + h * .22f + .45f, c[1] + Math.signum(c[1] - (z0 + z1) / 2) * .25f, .08f, .03f, 6, Look.GOLD);
    }

    static void generic(Mesh s, Mesh a, float w, float d, int L, int team, float t) {
        pad(s, w, d, Look.STONE_DARK);
        float h = 1.6f + L * .6f;
        stoneWalls(s, .3f, .3f, w - .3f, d - .3f, 0, h, Look.PLASTER);
        hipRoof(s, .3f, .3f, w - .3f, d - .3f, h, Math.min(w, d) * .35f, .2f, .3f, team, Look.GOLD);
        flagPole(s, a, .3f, .3f, h, 1.4f, team, t, .7f);
    }
}
