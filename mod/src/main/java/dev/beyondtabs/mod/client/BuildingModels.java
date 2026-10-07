package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.gen.BuildingDef;

/**
 * Race-specific low-poly building models in the same toy style as the units. Local frame: x across the footprint
 * (0..w), z deep (0..d), y up from the ground, the front (door, rally side) faces +z. Every level adds visible detail.
 * Static geometry goes to `s` (cached on the GPU once built); animated bits (flags, sails, fire, smoke) go to `a`
 * and are drawn every frame. Parts are authored bottom-up so construction reveals them in a sensible order.
 *
 * Ancient World = Tribal / Viking / Greek. Kingdoms = Medieval / Dynasty / Renaissance.
 */
final class BuildingModels {
    private BuildingModels() { }

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

    // ------------------------------------------------------------------ shared pieces

    static void pad(Mesh s, float w, float d, int c) { s.box(-.2f, -.4f, -.2f, w + .2f, .05f, d + .2f, c); }

    static void post(Mesh s, float x, float z, float y0, float y1, float r, int c) { s.cyl(x, z, y0, y1, r, r * .9f, 6, c); }

    static void log(Mesh s, float x0, float y0, float z0, float x1, float y1, float z1, float r, int c) { s.prismL(x0, y0, z0, x1, y1, z1, r, r, 6, c); }

    static void flagPole(Mesh s, Mesh a, float x, float z, float y0, float h, int team, float t, float fw) {
        post(s, x, z, y0, y0 + h, .07f, Look.WOOD_DARK);
        s.ball(x, y0 + h + .08f, z, .1f, .1f, .1f, 5, 3, Look.GOLD);
        a.flag(x + .05f, y0 + h - .05f, z, fw, fw * .6f, t * 3 + x * .7f + z, team);
    }

    static void fire(Mesh a, float x, float y, float z, float r, float t) {
        for (int i = 0; i < 3; i++) {
            float ph = t * 9 + i * 2.1f, f = .75f + .25f * (float) Math.sin(ph);
            float ox = (float) Math.cos(i * 2.1f) * r * .35f, oz = (float) Math.sin(i * 2.1f) * r * .35f;
            a.cone(x + ox, z + oz, y, y + r * 2.2f * f, r * .55f, 5, i == 0 ? 0xFFE07A : Look.FIRE);
        }
    }

    static void smoke(Mesh a, float x, float y, float z, float t, int c) {
        for (int i = 0; i < 3; i++) {
            float u = ((t * .35f + i / 3f) % 1f), r = .25f + u * .45f;
            a.ball(x + u * .6f, y + u * 2.6f, z - u * .3f, r, r * .8f, r, 5, 3, Look.mix(c, 0xFFFFFF, u * .5f));
        }
    }

    static void timber(Mesh s, float x0, float z0, float x1, float z1, float y0, float y1, int beam) {
        float o = .06f, xm = (x0 + x1) / 2, zm = (z0 + z1) / 2;
        for (float x : new float[]{x0, xm, x1}) {
            s.box(x - .12f, y0, z1 - .04f, x + .12f, y1, z1 + o, beam);
            s.box(x - .12f, y0, z0 - o, x + .12f, y1, z0 + .04f, beam);
        }
        s.box(x0 - o, y0, zm - .12f, x0 + .04f, y1, zm + .12f, beam);
        s.box(x1 - .04f, y0, zm - .12f, x1 + o, y1, zm + .12f, beam);
        s.box(x0 - o, y1 - .2f, z1 - .04f, x1 + o, y1, z1 + o, beam);
        s.box(x0 - o, y1 - .2f, z0 - o, x1 + o, y1, z0 + .04f, beam);
        s.box(x0 - o, (y0 + y1) / 2 - .08f, z1 - .04f, x1 + o, (y0 + y1) / 2 + .08f, z1 + o, beam);
        // diagonal braces on the front
        s.prismL(x0 + .2f, y0 + .2f, z1 + o, xm - .2f, y1 - .3f, z1 + o, .07f, .07f, 4, beam);
        s.prismL(x1 - .2f, y0 + .2f, z1 + o, xm + .2f, y1 - .3f, z1 + o, .07f, .07f, 4, beam);
    }

    static void crenels(Mesh s, float x0, float z0, float x1, float z1, float y, float h, int c) {
        s.box(x0 - .15f, y - .2f, z0 - .15f, x1 + .15f, y, z1 + .15f, Look.darker(c, .1f));
        for (float x = x0; x < x1 - .3f; x += 1.0f) { s.box(x, y, z0 - .15f, x + .55f, y + h, z0 + .35f, c); s.box(x, y, z1 - .35f, x + .55f, y + h, z1 + .15f, c); }
        for (float z = z0 + 1.0f; z < z1 - .8f; z += 1.0f) { s.box(x0 - .15f, y, z, x0 + .35f, y + h, z + .55f, c); s.box(x1 - .35f, y, z, x1 + .15f, y + h, z + .55f, c); }
    }

    static void crenelRing(Mesh s, float x, float z, float r, float y, int n, int c) {
        s.cyl(x, z, y - .25f, y, r + .1f, r + .15f, 10, Look.darker(c, .1f));
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n; float px = x + (float) Math.cos(a) * r, pz = z + (float) Math.sin(a) * r;
            s.box(px - .22f, y, pz - .22f, px + .22f, y + .5f, pz + .22f, c);
        }
    }

    static void column(Mesh s, float x, float z, float y0, float h, float r, int c) {
        s.box(x - r * 1.3f, y0, z - r * 1.3f, x + r * 1.3f, y0 + .18f, z + r * 1.3f, c);
        s.cyl(x, z, y0 + .18f, y0 + h - .2f, r, r * .88f, 8, Look.lighter(c, .08f));
        s.box(x - r * 1.35f, y0 + h - .2f, z - r * 1.35f, x + r * 1.35f, y0 + h, z + r * 1.35f, c);
    }

    static void barrel(Mesh s, float x, float z, float y, float r) {
        s.cyl(x, z, y, y + r * 2.4f, r, r * 1.05f, 7, Look.WOOD);
        s.cyl(x, z, y + r * .5f, y + r * .7f, r * 1.07f, r * 1.07f, 7, Look.IRON_DARK);
        s.cyl(x, z, y + r * 1.7f, y + r * 1.9f, r * 1.07f, r * 1.07f, 7, Look.IRON_DARK);
    }

    static void crate(Mesh s, float x, float z, float y, float h) {
        s.box(x - h / 2, y, z - h / 2, x + h / 2, y + h, z + h / 2, Look.WOOD_LIGHT);
        s.box(x - h / 2 - .02f, y + h * .4f, z - h / 2 - .02f, x + h / 2 + .02f, y + h * .6f, z + h / 2 + .02f, Look.WOOD_DARK);
    }

    static void door(Mesh s, float x, float z, float w, float h, int frame) {
        s.box(x - w / 2 - .12f, 0, z - .02f, x + w / 2 + .12f, h + .12f, z + .1f, frame);
        s.box(x - w / 2, 0, z + .05f, x + w / 2, h, z + .12f, 0x3A2A20);
    }

    static void dummy(Mesh s, float x, float z, int team) {
        post(s, x, z, 0, 1.6f, .07f, Look.WOOD_DARK);
        s.prismL(x - .5f, 1.25f, z, x + .5f, 1.25f, z, .06f, .06f, 4, Look.WOOD_DARK);
        s.ball(x, 1.15f, z, .3f, .4f, .3f, 6, 4, Look.THATCH);
        s.ball(x, 1.75f, z, .22f, .22f, .22f, 6, 3, Look.CLOTH);
    }

    static void rocks(Mesh s, float x, float z, float r, int n, int c, int seed) {
        for (int i = 0; i < n; i++) {
            float a = (seed * 7 + i * 2.39f), rr = r * (.35f + .25f * (float) Math.abs(Math.sin(seed + i * 1.7)));
            float px = x + (float) Math.cos(a) * r * .55f, pz = z + (float) Math.sin(a) * r * .55f;
            s.ball(px, rr * .35f, pz, rr, rr * .75f, rr * .9f, 5, 3, Look.mix(c, 0, (i % 3) * .08f));
        }
    }

    static void crystals(Mesh s, float x, float z, float r, int n, int c) {
        for (int i = 0; i < n; i++) {
            float a = i * 2.4f, px = x + (float) Math.cos(a) * r * .4f, pz = z + (float) Math.sin(a) * r * .4f, h = r * (.9f + (i % 3) * .35f);
            s.prismL(px, 0, pz, px + (float) Math.cos(a) * h * .25f, h, pz + (float) Math.sin(a) * h * .25f, r * .22f, 0, 5, i % 2 == 0 ? c : Look.lighter(c, .3f));
        }
    }

    static void fence(Mesh s, float x0, float z0, float x1, float z1, float gapX0, float gapX1) {
        float step = 1.5f;
        for (float x = x0; x <= x1 + .01f; x += step) for (float z : new float[]{z0, z1}) {
            if (z == z1 && x > gapX0 && x < gapX1) continue;
            post(s, x, z, 0, 1.1f, .08f, Look.WOOD_DARK);
        }
        for (float z = z0 + step; z < z1 - .01f; z += step) for (float x : new float[]{x0, x1}) post(s, x, z, 0, 1.1f, .08f, Look.WOOD_DARK);
        log(s, x0, .8f, z0, x1, .8f, z0, .05f, Look.WOOD);
        log(s, x0, .8f, z0, x0, .8f, z1, .05f, Look.WOOD); log(s, x1, .8f, z0, x1, .8f, z1, .05f, Look.WOOD);
        log(s, x0, .8f, z1, gapX0, .8f, z1, .05f, Look.WOOD); log(s, gapX1, .8f, z1, x1, .8f, z1, .05f, Look.WOOD);
    }

    // ------------------------------------------------------------------ Ancient World

    static void ancient(Mesh s, Mesh a, String kind, float w, float d, int L, int team, float t) {
        float cx = w / 2, cz = d / 2;
        switch (kind) {
            case "metal_extractor" -> {
                pad(s, w, d, Look.DIRT);
                s.cyl(cx, cz, 0, .65f, 1.15f, 1.1f, 8, Look.STONE);
                s.disc(cx, .66f, cz, .85f, 8, 0x2A2420);
                if (L >= 2) { rocks(s, .5f, d - .5f, .9f, 3, Look.STONE_DARK, 3); crystals(s, .55f, d - .55f, .5f, 4, Look.ORE); }
                log(s, .25f, 0, cz, cx, 2.5f, cz, .09f, Look.WOOD); log(s, w - .25f, 0, cz, cx, 2.5f, cz, .09f, Look.WOOD);
                log(s, .5f, 2.3f, cz, w - .5f, 2.3f, cz, .1f, Look.WOOD_DARK);
                s.cyl(cx, cz, 1.0f, 2.3f, .02f, .02f, 4, Look.ROPE);
                if (L >= 3) { for (float x : new float[]{.2f, w - .2f}) for (float z : new float[]{.2f, d - .2f}) post(s, x, z, 0, 2.9f, .08f, Look.WOOD_DARK); s.hip(0, 0, w, d, 2.9f, .9f, .25f, .3f, Look.THATCH); }
                float bob = (float) Math.sin(t * 1.3f) * .5f;
                a.cyl(cx, cz, 1.15f + bob, 1.5f + bob, .2f, .24f, 6, Look.WOOD_LIGHT);
            }
            case "energy_gen" -> {
                pad(s, w, d, Look.DIRT);
                s.cyl(cx, cz - .3f, 0, 1.7f, 1.45f, 1.4f, 10, 0xD9A877);
                door(s, cx, cz - .3f + 1.38f, .7f, 1.2f, Look.WOOD_DARK);
                s.cone(cx, cz - .3f, 1.55f, 3.4f + L * .25f, 1.85f, 10, Look.THATCH);
                if (L >= 2) s.cyl(cx, cz - .3f, 1.55f, 1.9f, 1.9f, 1.75f, 10, Look.THATCH_DARK);
                s.cyl(cx, cz - .3f, 3.2f + L * .25f, 3.6f + L * .25f, .18f, .12f, 6, Look.WOOD_DARK);
                s.cyl(w - .7f, d - .6f, 0, .35f, .45f, .5f, 7, Look.STONE);
                if (L >= 3) { post(s, .5f, d - .5f, 0, 2.4f, .14f, Look.WOOD); s.ball(.5f, 2.55f, d - .5f, .22f, .26f, .22f, 6, 3, Look.BONE); }
                fire(a, w - .7f, .35f, d - .6f, .3f + L * .04f, t);
                smoke(a, cx, 3.6f + L * .25f, cz - .3f, t, 0x9C9C9C);
            }
            case "converter" -> {
                pad(s, w, d, Look.DIRT);
                s.ball(cx, 0, cz - .2f, 1.6f, 2.0f, 1.6f, 9, 5, Look.CLAY);
                s.box(cx - .4f, 0, cz + 1.3f, cx + .4f, .8f, cz + 1.55f, 0x3A2420);
                s.cyl(cx + .7f, cz - 1.0f, 1.2f, 2.6f, .3f, .25f, 6, Look.darker(Look.CLAY, .15f));
                if (L >= 2) s.cyl(cx - .8f, cz - .9f, 1.0f, 2.3f, .25f, .2f, 6, Look.darker(Look.CLAY, .15f));
                log(s, .3f, .15f, d - .4f, 1.6f, .15f, d - .4f, .14f, Look.WOOD); log(s, .3f, .15f, d - .75f, 1.6f, .15f, d - .75f, .14f, Look.WOOD);
                log(s, .45f, .4f, d - .58f, 1.45f, .4f, d - .58f, .14f, Look.WOOD_LIGHT);
                a.box(cx - .3f, .1f, cz + 1.5f, cx + .3f, .6f + .1f * (float) Math.sin(t * 6), cz + 1.56f, Look.FIRE);
                smoke(a, cx + .7f, 2.6f, cz - 1.0f, t, 0xA89C8C);
            }
            case "storage" -> {
                pad(s, w, d, Look.DIRT);
                s.box(.6f, 0, .8f, w - .6f, 1.7f, d - 1.4f, Look.WOOD);
                s.gable(.6f, .8f, w - .6f, d - 1.4f, 1.7f, 1.5f, true, .35f, Look.THATCH, Look.WOOD_LIGHT);
                door(s, cx, d - 1.4f, .9f, 1.3f, Look.WOOD_DARK);
                barrel(s, .9f, d - .6f, 0, .3f); barrel(s, 1.6f, d - .5f, 0, .3f); crate(s, w - 1f, d - .6f, 0, .7f);
                if (L >= 2) { crate(s, w - 1.1f, d - .6f, .7f, .55f); barrel(s, 1.25f, d - .55f, .75f, .28f); s.box(w - .6f, 0, 1.2f, w + .1f, 1.1f, d - 1.8f, Look.WOOD_LIGHT); s.hip(w - .6f, 1.2f, w + .1f, d - 1.8f, 1.1f, .35f, .15f, .1f, Look.THATCH_DARK); }
            }
            case "tech_center" -> {   // Greek temple
                s.box(-.1f, -.4f, -.1f, w + .1f, .3f, d + .1f, Look.STONE_DARK);
                s.box(.3f, .3f, .3f, w - .3f, .6f, d - .3f, Look.STONE);
                s.box(1.6f, .6f, 1.4f, w - 1.6f, 3.1f, d - 1.6f, Look.PLASTER);
                door(s, cx, d - 1.6f, 1.0f, 1.8f, Look.STONE);
                float y0 = .6f, ch = 2.7f;
                for (float x = .9f; x <= w - .8f; x += (w - 1.8f) / 3) { column(s, x, d - .8f, y0, ch, .24f, Look.PLASTER); column(s, x, .8f, y0, ch, .24f, Look.PLASTER); }
                if (L >= 2) for (float z = .8f + (d - 1.6f) / 3; z < d - 1.2f; z += (d - 1.6f) / 3) { column(s, .9f, z, y0, ch, .24f, Look.PLASTER); column(s, w - .9f, z, y0, ch, .24f, Look.PLASTER); }
                s.box(.45f, y0 + ch, .45f, w - .45f, y0 + ch + .45f, d - .45f, Look.STONE);
                s.box(.45f, y0 + ch + .1f, d - .5f, w - .45f, y0 + ch + .3f, d - .4f, team);
                s.gable(.45f, .45f, w - .45f, d - .45f, y0 + ch + .45f, 1.25f, false, .25f, Look.TILE_RED, Look.PLASTER);
                if (L >= 3) {   // a golden statue on the steps and braziers
                    s.box(cx - .45f, .6f, d + .05f, cx + .45f, 1.2f, d + .9f, Look.STONE);
                    s.prismL(cx, 1.2f, d + .45f, cx, 2.1f, d + .45f, .25f, .2f, 6, Look.GOLD);
                    s.ball(cx, 2.35f, d + .45f, .22f, .22f, .22f, 6, 3, Look.GOLD);
                    s.prismL(cx + .2f, 1.9f, d + .45f, cx + .45f, 2.6f, d + .55f, .06f, .05f, 4, Look.GOLD);
                }
                if (L >= 2) for (float x : new float[]{.5f, w - .5f}) { s.cyl(x, d + .3f, 0, .9f, .08f, .1f, 5, Look.IRON_DARK); s.cyl(x, d + .3f, .9f, 1.1f, .3f, .22f, 6, Look.IRON_DARK); fire(a, x, 1.1f, d + .3f, .2f, t); }
            }
            case "watchtower" -> {
                pad(s, w, d, Look.DIRT);
                float h = 3.6f + L * .7f;
                for (float x : new float[]{.35f, w - .35f}) for (float z : new float[]{.35f, d - .35f}) log(s, x, 0, z, x + (cx - x) * .15f, h, z + (cz - z) * .15f, .12f, Look.WOOD);
                log(s, .5f, h * .45f, .5f, w - .5f, h * .45f, d - .5f, .06f, Look.WOOD_DARK); log(s, w - .5f, h * .45f, .5f, .5f, h * .45f, d - .5f, .06f, Look.WOOD_DARK);
                s.box(.1f, h, .1f, w - .1f, h + .25f, d - .1f, Look.WOOD_LIGHT);
                for (float x : new float[]{.15f, w - .15f}) for (float z : new float[]{.15f, d - .15f}) post(s, x, z, h + .25f, h + 1.7f, .06f, Look.WOOD_DARK);
                s.box(.1f, h + .25f, .1f, w - .1f, h + .75f, .2f, Look.WOOD); s.box(.1f, h + .25f, d - .2f, w - .1f, h + .75f, d - .1f, Look.WOOD);
                s.box(.1f, h + .25f, .1f, .2f, h + .75f, d - .1f, Look.WOOD); s.box(w - .2f, h + .25f, .1f, w - .1f, h + .75f, d - .1f, Look.WOOD);
                s.cone(cx, cz, h + 1.7f, h + 3.0f, 2.0f, 8, Look.THATCH);
                for (float y = .3f; y < h; y += .45f) s.box(cx - .35f, y, d - .32f, cx + .35f, y + .07f, d - .24f, Look.WOOD_DARK);   // ladder
                if (L >= 3) flagPole(s, a, cx, cz, h + 2.9f, 1.0f, team, t, .8f);
            }
            case "wall" -> {
                s.box(-.05f, -.3f, -.05f, 1.05f, .1f, 1.05f, Look.DIRT);
                if (L >= 2) s.box(-.02f, 0, .1f, 1.02f, .7f, .9f, Look.STONE);
                for (float x : new float[]{.2f, .5f, .8f}) {
                    float h = 2.1f + (x == .5f ? .25f : 0);
                    s.cyl(x, .5f, 0, h, .17f, .16f, 6, Look.WOOD);
                    s.cone(x, .5f, h, h + .35f, .16f, 6, Look.WOOD_LIGHT);
                }
                log(s, 0, 1.4f, .32f, 1, 1.4f, .32f, .05f, Look.ROPE);
            }
            case "barracks" -> {   // Viking longhouse
                pad(s, w, d, Look.DIRT);
                if (L >= 3) s.box(.8f, 0, .8f, w - .8f, .4f, d - .9f, Look.STONE);
                s.box(1.1f, 0, 1f, w - 1.1f, 2.0f, d - 1.1f, Look.WOOD);
                for (float z = 1.4f; z < d - 1.2f; z += .9f) { s.box(1.0f, 0, z, 1.12f, 2.0f, z + .12f, Look.WOOD_DARK); s.box(w - 1.12f, 0, z, w - 1.0f, 2.0f, z + .12f, Look.WOOD_DARK); }
                door(s, cx, d - 1.1f, 1.0f, 1.5f, Look.WOOD_DARK);
                s.gable(1.1f, 1f, w - 1.1f, d - 1.1f, 2.0f, 2.3f, false, .45f, Look.THATCH, Look.WOOD_LIGHT);
                for (float z : new float[]{.75f, d - .85f}) {   // crossed gable beams
                    log(s, cx - .2f, 3.9f, z, cx + .7f, 4.9f, z, .08f, Look.WOOD_DARK);
                    log(s, cx + .2f, 3.9f, z, cx - .7f, 4.9f, z, .08f, Look.WOOD_DARK);
                }
                for (float z = 1.6f; z < d - 1.4f; z += 1.6f) for (int sd = -1; sd <= 1; sd += 2) {
                    float x = sd < 0 ? 1.0f : w - 1.0f;
                    s.prismL(x, 1.1f, z, x + sd * .08f, 1.1f, z, .38f, .38f, 8, (int) (z * 10) % 2 == 0 ? team : Look.CLOTH);
                }
                if (L >= 2) { dummy(s, .55f, d - .5f, team); post(s, w - .5f, d - .5f, 0, 1.2f, .06f, Look.WOOD_DARK); log(s, w - .6f, 1.0f, d - .5f, w - .1f, 1.0f, d - .5f, .04f, Look.WOOD_DARK); }
                flagPole(s, a, w - .5f, .5f, 0, 3.2f + L * .4f, team, t, 1.0f);
            }
            case "war_lodge" -> {
                pad(s, w, d, Look.DIRT);
                fence(s, .2f, .2f, w - .2f, d - .2f, cx - 1.2f, cx + 1.2f);
                s.box(1.6f, 0, .8f, w - 1.6f, 2.6f, d - 3.0f, Look.WOOD);
                for (float x = 1.9f; x < w - 1.7f; x += 1.0f) s.box(x, 0, d - 3.06f, x + .14f, 2.6f, d - 2.94f, Look.WOOD_DARK);
                door(s, cx, d - 3.0f, 1.2f, 1.7f, Look.WOOD_DARK);
                s.gable(1.6f, .8f, w - 1.6f, d - 3.0f, 2.6f, 2.6f, true, .5f, Look.THATCH, Look.WOOD_LIGHT);
                if (L >= 2) for (float x : new float[]{1.3f, w - 1.3f}) {   // carved prows at the gable ends
                    log(s, x, 4.6f, (d - 2.2f) / 2, x + (x < cx ? -.6f : .6f), 5.6f, (d - 2.2f) / 2, .12f, Look.WOOD_DARK);
                    s.ball(x + (x < cx ? -.7f : .7f), 5.7f, (d - 2.2f) / 2, .18f, .14f, .26f, 5, 3, Look.WOOD_DARK);
                }
                s.cyl(cx, d - 1.4f, 0, .25f, .6f, .65f, 8, Look.STONE); fire(a, cx, .25f, d - 1.4f, .3f, t);
                dummy(s, 1.2f, d - 1.3f, team); dummy(s, w - 1.2f, d - 1.3f, team);
                if (L >= 3) { for (float x : new float[]{.6f, 1.6f}) for (float z : new float[]{.6f, 1.6f}) post(s, x, z, 0, 3.4f, .09f, Look.WOOD); s.box(.4f, 3.4f, .4f, 1.8f, 3.6f, 1.8f, Look.WOOD_LIGHT); s.cone(1.1f, 1.1f, 3.6f, 4.6f, 1.1f, 6, Look.THATCH); }
                flagPole(s, a, cx - 1.4f, d - .2f, 0, 3.2f, team, t, 1.0f); flagPole(s, a, cx + 1.4f, d - .2f, 0, 3.2f, team, t, 1.0f);
            }
            case "hall_of_legends" -> {
                s.box(-.1f, -.4f, -.1f, w + .1f, .35f, d + .1f, Look.STONE_DARK);
                s.box(.4f, .35f, .4f, w - .4f, .7f, d - .4f, Look.STONE);
                s.box(2.2f, .7f, 1.2f, w - 2.2f, 4.2f, d - 3.0f, Look.STONE);
                for (float x = 2.6f; x < w - 2.3f; x += 1.2f) s.box(x, .7f, d - 3.06f, x + .25f, 4.2f, d - 2.95f, Look.STONE_DARK);
                door(s, cx, d - 3.0f, 1.4f, 2.4f, Look.GOLD);
                for (float x = 1.2f; x <= w - 1.1f; x += (w - 2.4f) / 5) column(s, x, d - 1.1f, .7f, 3.7f, .3f, Look.PLASTER);
                s.box(.9f, 4.4f, d - 1.6f, w - .9f, 4.9f, d - .6f, Look.STONE);
                s.box(.9f, 4.55f, d - .65f, w - .9f, 4.75f, d - .55f, Look.GOLD);
                s.gable(.9f, .9f, w - .9f, d - .6f, 4.9f, 2.6f, false, .4f, Look.THATCH_DARK, Look.STONE);
                s.prismL(cx, 7.4f, .5f, cx, 7.4f, d - .1f, .1f, .1f, 4, Look.GOLD);
                // the hero statue
                s.box(cx - .7f, .7f, d - .4f + .2f, cx + .7f, 1.5f, d + .9f, Look.STONE);
                s.prismL(cx, 1.5f, d + .35f, cx, 3.0f, d + .35f, .4f, .33f, 7, Look.GOLD);
                s.ball(cx, 3.35f, d + .35f, .32f, .32f, .32f, 6, 4, Look.GOLD);
                s.prismL(cx + .3f, 2.8f, d + .35f, cx + .6f, 4.0f, d + .5f, .09f, .07f, 4, Look.GOLD);
                for (float x : new float[]{.8f, w - .8f}) { s.cyl(x, d + .3f, 0, 1.2f, .1f, .12f, 5, Look.IRON_DARK); s.cyl(x, d + .3f, 1.2f, 1.45f, .38f, .28f, 7, Look.IRON_DARK); fire(a, x, 1.45f, d + .3f, .27f, t); }
                if (L >= 2) for (float x : new float[]{.9f, w - .9f}) { s.cyl(x, .9f, 0, 5.5f, .9f, .8f, 8, Look.STONE); s.cone(x, .9f, 5.5f, 7.0f, 1.15f, 8, Look.THATCH_DARK); flagPole(s, a, x, .9f, 6.9f, 1.2f, team, t, 1.0f); }
            }
            case "siege_yard" -> {
                pad(s, w, d, Look.DIRT);
                fence(s, .2f, .2f, w - .2f, d - .2f, cx - 2.0f, cx + 2.0f);
                s.box(.8f, 0, .8f, 4.6f, 2.3f, 3.6f, Look.WOOD);
                s.gable(.8f, .8f, 4.6f, 3.6f, 2.3f, 1.3f, true, .3f, Look.THATCH, Look.WOOD_LIGHT);
                door(s, 2.7f, 3.6f, 1.4f, 1.6f, Look.WOOD_DARK);
                crane(s, a, w - 2.5f, 2.5f, 5.5f, t);
                // a half-built bolt thrower
                s.box(cx - 1.0f, .3f, cz + .5f, cx + 1.0f, .5f, cz + 2.0f, Look.WOOD);
                for (float x : new float[]{cx - 1.1f, cx + 1.1f}) for (float z : new float[]{cz + .7f, cz + 1.8f}) s.prismL(x, .35f, z, x + (x < cx ? -.06f : .06f), .35f, z, .35f, .35f, 8, Look.WOOD_DARK);
                log(s, cx, .9f, cz + .3f, cx, .9f, cz + 2.4f, .1f, Look.WOOD_LIGHT);
                for (int i = 0; i < 3; i++) log(s, 1.0f, .2f + i * .35f, d - 2.0f + (i % 2) * .2f, 3.8f, .2f + i * .35f, d - 2.0f + (i % 2) * .2f, .17f, i % 2 == 0 ? Look.WOOD : Look.WOOD_LIGHT);
                if (L >= 2) { crane(s, a, 2.5f, d - 4.0f, 4.5f, t + 1.7f); crate(s, w - 1.3f, d - 1.3f, 0, .8f); crate(s, w - 2.2f, d - 1.2f, 0, .7f); }
                flagPole(s, a, w - .6f, d - .6f, 0, 3.5f, team, t, 1.0f);
            }
            default -> generic(s, a, w, d, L, team, t);
        }
    }

    static void crane(Mesh s, Mesh a, float x, float z, float h, float t) {
        log(s, x - 1.0f, 0, z, x, h, z, .12f, Look.WOOD); log(s, x + 1.0f, 0, z, x, h, z, .12f, Look.WOOD);
        log(s, x, 0, z - 1.0f, x, h, z, .1f, Look.WOOD_DARK);
        float sw = (float) Math.sin(t * .4f) * .6f, bx = x + (float) Math.cos(sw) * 2.4f, bz = z + (float) Math.sin(sw) * 2.4f;
        a.prismL(x - (bx - x) * .3f, h - .1f, z - (bz - z) * .3f, bx, h + .5f, bz, .09f, .07f, 4, Look.WOOD_LIGHT);
        a.prismL(bx, h + .45f, bz, bx, h - 1.6f, bz, .02f, .02f, 4, Look.ROPE);
        a.box(bx - .25f, h - 2.0f, bz - .25f, bx + .25f, h - 1.6f, bz + .25f, Look.STONE_DARK);
    }

    // ------------------------------------------------------------------ Kingdoms

    static void kingdoms(Mesh s, Mesh a, String kind, float w, float d, int L, int team, float t) {
        float cx = w / 2, cz = d / 2;
        int flag = Look.STONE_COOL_DARK;
        switch (kind) {
            case "metal_extractor" -> {
                pad(s, w, d, flag);
                s.hip(.1f, .1f, w - .1f, d - .8f, 0, 1.9f, 0, .5f, Look.STONE_COOL_DARK);
                rocks(s, cx, 1.0f, 1.2f, 4, Look.STONE_COOL, 5);
                s.box(cx - .65f, 0, d - 1.1f, cx + .65f, 1.5f, d - .7f, Look.WOOD_DARK);
                s.box(cx - .45f, 0, d - .75f, cx + .45f, 1.3f, d - .68f, 0x241C18);
                for (float x : new float[]{cx - .3f, cx + .3f}) s.box(x - .04f, .02f, d - .7f, x + .04f, .08f, d + .1f, Look.IRON_DARK);
                s.box(cx - .35f, .15f, d - .45f, cx + .35f, .55f, d - .05f, Look.WOOD);
                crystals(s, cx, d - .25f, .3f, 3, Look.ORE);
                if (L >= 2) { post(s, .3f, .3f, 0, 2.6f, .08f, Look.WOOD_DARK); post(s, w - .3f, .3f, 0, 2.6f, .08f, Look.WOOD_DARK); log(s, .3f, 2.5f, .3f, w - .3f, 2.5f, .3f, .08f, Look.WOOD_DARK); }
                if (L >= 3) { s.hip(0, 0, w, .9f, 2.6f, .6f, .15f, .1f, Look.SLATE); flagPole(s, a, w - .3f, .3f, 2.6f, 1.0f, team, t, .7f); }
            }
            case "energy_gen" -> {   // windmill
                pad(s, w, d, flag);
                float h = 3.6f + L * .6f;
                s.cyl(cx, cz, 0, h, 1.6f, 1.15f, 8, Look.PLASTER);
                s.cyl(cx, cz, 0, .5f, 1.68f, 1.62f, 8, Look.STONE_COOL);
                door(s, cx, cz + 1.53f, .7f, 1.3f, Look.WOOD_DARK);
                s.box(cx - .25f, h * .55f, cz + 1.3f, cx + .25f, h * .55f + .45f, cz + 1.36f, 0x3A3A4A);
                s.cone(cx, cz, h, h + 1.5f, 1.45f, 8, Look.SLATE);
                s.prismL(cx, h - .3f, cz + 1.0f, cx, h - .3f, cz + 1.7f, .14f, .12f, 6, Look.WOOD_DARK);
                float rot = t * (.9f + L * .25f);
                for (int i = 0; i < 4; i++) {
                    float ang = rot + i * (float) Math.PI / 2, ux = (float) Math.cos(ang), uy = (float) Math.sin(ang), hy = h - .3f, hz = cz + 1.75f;
                    a.prismL(cx, hy, hz, cx + ux * 2.6f, hy + uy * 2.6f, hz, .05f, .04f, 4, Look.WOOD_DARK);
                    float px = -uy, py = ux;
                    a.quadSail(cx + ux * .6f, hy + uy * .6f, cx + ux * 2.5f, hy + uy * 2.5f, px * .55f, py * .55f, hz + .03f, i % 2 == 0 ? Look.CLOTH : team);
                }
            }
            case "converter" -> {   // alchemist's workshop
                pad(s, w, d, flag);
                s.box(.5f, 0, .6f, w - .5f, 2.2f, d - .8f, Look.PLASTER);
                timber(s, .5f, .6f, w - .5f, d - .8f, 0, 2.2f, Look.WOOD_DARK);
                door(s, cx - .5f, d - .8f, .7f, 1.3f, Look.WOOD_DARK);
                s.box(cx + .35f, 1.0f, d - .78f, cx + 1.0f, 1.5f, d - .72f, 0x9FC6E8);
                s.gable(.5f, .6f, w - .5f, d - .8f, 2.2f, 1.4f, true, .3f, Look.TILE_RED, Look.PLASTER);
                s.box(w - 1.2f, 2.0f, .9f, w - .7f, 4.0f, 1.4f, Look.STONE_COOL);
                if (L >= 2) { s.cyl(.6f, d - .3f, 0, .5f, .25f, .25f, 6, 0x6E4ABE); s.cyl(1.2f, d - .3f, 0, .4f, .2f, .2f, 6, 0x4AAE7E); }
                smoke(a, w - .95f, 4.0f, 1.15f, t, 0xB28CE0);
            }
            case "storage" -> {   // granary barn
                pad(s, w, d, flag);
                s.box(.5f, 0, .7f, w - .5f, 2.0f, d - 1.0f, Look.WOOD);
                for (float x = .7f; x < w - .5f; x += .6f) s.box(x, 0, d - 1.05f, x + .08f, 2.0f, d - .95f, Look.WOOD_DARK);
                s.box(cx - .8f, 0, d - .98f, cx + .8f, 1.5f, d - .92f, 0x8A4A30);
                s.prismL(cx - .8f, .1f, d - .9f, cx + .8f, 1.4f, d - .9f, .06f, .06f, 4, Look.PLASTER);
                s.prismL(cx + .8f, .1f, d - .9f, cx - .8f, 1.4f, d - .9f, .06f, .06f, 4, Look.PLASTER);
                s.gable(.5f, .7f, w - .5f, d - 1.0f, 2.0f, 1.7f, true, .35f, Look.SLATE, Look.WOOD);
                for (float x : new float[]{1.0f, 1.6f}) s.ball(x, .3f, d - .4f, .3f, .32f, .26f, 6, 3, Look.CLOTH);
                if (L >= 2) { s.cyl(w - .2f, 1.0f, 0, 2.6f, .75f, .75f, 8, Look.PLASTER); s.cone(w - .2f, 1.0f, 2.6f, 3.7f, .9f, 8, Look.SLATE); }
            }
            case "tech_center" -> {   // domed academy
                pad(s, w, d, flag);
                s.box(.3f, 0, .3f, w - .3f, .4f, d - .3f, Look.STONE_COOL);
                s.box(1f, .4f, 1f, w - 1f, 3.4f, d - 1.3f, Look.PLASTER);
                s.box(.9f, 3.4f, .9f, w - .9f, 3.75f, d - 1.2f, Look.STONE_COOL);
                for (float x = 1.6f; x < w - 1.2f; x += 1.2f) s.box(x, 1.4f, d - 1.33f, x + .5f, 2.6f, d - 1.27f, 0x9FC6E8);
                for (float x : new float[]{cx - 1.0f, cx + 1.0f}) column(s, x, d - .6f, .4f, 2.9f, .22f, Look.PLASTER);
                s.box(cx - 1.4f, 3.3f, d - 1.3f, cx + 1.4f, 3.6f, d - .3f, Look.STONE_COOL);
                s.gable(cx - 1.4f, d - 1.3f, cx + 1.4f, d - .3f, 3.6f, .8f, false, .1f, Look.TILE_RED, Look.PLASTER);
                door(s, cx, d - 1.3f, .9f, 1.8f, Look.STONE_COOL);
                s.cyl(cx, cz - .2f, 3.75f, 4.3f, 2.0f, 2.0f, 10, Look.PLASTER);
                s.ball(cx, 4.3f, cz - .2f, 2.0f, 1.9f, 2.0f, 10, 4, 0x77AE9A);
                s.cyl(cx, cz - .2f, 6.1f, 6.8f, .35f, .3f, 6, Look.PLASTER);
                s.ball(cx, 7.0f, cz - .2f, .22f, .22f, .22f, 6, 3, Look.GOLD);
                if (L >= 2) for (float x : new float[]{.1f, w - 1.6f}) { s.box(x, 0, 1.6f, x + 1.5f, 2.2f, d - 2.0f, Look.PLASTER); s.hip(x, 1.6f, x + 1.5f, d - 2.0f, 2.2f, .7f, .15f, .3f, Look.TILE_RED); }
                if (L >= 3) for (float x : new float[]{1.3f, w - 1.3f}) { s.cyl(x, .9f, 0, 4.6f, .45f, .4f, 8, Look.PLASTER); s.ball(x, 4.6f, .9f, .5f, .6f, .5f, 8, 4, 0x77AE9A); }
                flagPole(s, a, cx, cz - .2f, 7.2f, 1.0f, team, t, .8f);
            }
            case "watchtower" -> {
                pad(s, w, d, flag);
                float h = 4.5f + L * .9f;
                s.cyl(cx, cz, 0, h, 1.35f, 1.15f, 8, Look.STONE_COOL);
                s.cyl(cx, cz, 0, .6f, 1.45f, 1.4f, 8, Look.STONE_COOL_DARK);
                for (float y = 1.6f; y < h - .5f; y += 1.6f) s.box(cx - .12f, y, cz + 1.05f, cx + .12f, y + .6f, cz + 1.25f, 0x24242C);
                door(s, cx, cz + 1.3f, .6f, 1.2f, Look.WOOD_DARK);
                crenelRing(s, cx, cz, 1.3f, h + .2f, 8, Look.STONE_COOL);
                if (L >= 2) { post(s, cx, cz, h, h + 1.5f, .1f, Look.WOOD_DARK); s.cone(cx, cz, h + 1.3f, h + 2.6f, 1.2f, 8, Look.SLATE); flagPole(s, a, cx, cz, h + 2.5f, .9f, team, t, .8f); }
                else flagPole(s, a, cx, cz, h + .2f, 1.4f, team, t, .8f);
            }
            case "wall" -> {
                s.box(-.05f, -.3f, -.05f, 1.05f, .1f, 1.05f, flag);
                float h = 2.0f + L * .4f;
                s.box(0, 0, .1f, 1, h, .9f, Look.STONE_COOL);
                s.box(-.03f, 0, .07f, 1.03f, .5f, .93f, Look.STONE_COOL_DARK);
                s.box(.1f, h, .1f, .55f, h + .45f, .9f, Look.STONE_COOL);
                if (L >= 2) s.box(-.02f, h * .6f, .88f, 1.02f, h * .6f + .15f, .93f, team);
            }
            case "barracks" -> {   // Tudor hall
                pad(s, w, d, flag);
                float h1 = 2.4f, h2 = L >= 2 ? 4.0f : h1;
                s.box(1.0f, 0, 1.0f, w - 1.0f, h1, d - 1.2f, Look.PLASTER);
                timber(s, 1.0f, 1.0f, w - 1.0f, d - 1.2f, 0, h1, Look.WOOD_DARK);
                door(s, cx, d - 1.2f, 1.0f, 1.6f, Look.WOOD_DARK);
                for (float x : new float[]{1.7f, w - 1.7f}) s.box(x - .35f, 1.0f, d - 1.12f, x + .35f, 1.7f, d - 1.06f, 0x9FC6E8);
                if (L >= 2) {   // jettied upper floor
                    s.box(.8f, h1, .8f, w - .8f, h2, d - .95f, Look.PLASTER);
                    timber(s, .8f, .8f, w - .8f, d - .95f, h1, h2, Look.WOOD_DARK);
                }
                s.gable(.8f, .8f, w - .8f, d - .95f, h2, 2.0f, true, .35f, Look.TILE_RED, Look.PLASTER);
                s.box(w - 1.8f, h2 + .8f, 1.3f, w - 1.2f, h2 + 2.6f, 1.9f, Look.STONE_COOL);
                if (L >= 3) { s.box(cx - .6f, h2 + .1f, d - 1.6f, cx + .6f, h2 + 1.0f, d - .9f, Look.PLASTER); s.gable(cx - .6f, d - 1.9f, cx + .6f, d - .9f, h2 + 1.0f, .6f, false, .1f, Look.TILE_RED, Look.PLASTER); }
                dummy(s, .5f, d - .5f, team);
                flagPole(s, a, w - .5f, d - .5f, 0, 3.4f + L * .4f, team, t, 1.0f);
                smoke(a, w - 1.5f, h2 + 2.6f, 1.6f, t, 0xA0A0A0);
            }
            case "keep_workshop" -> {   // castle keep
                pad(s, w, d, flag);
                float h = 4.6f + L * .8f;
                s.box(1.8f, 0, 1.8f, w - 1.8f, h, d - 1.8f, Look.STONE_COOL);
                s.box(1.7f, 0, 1.7f, w - 1.7f, .7f, d - 1.7f, Look.STONE_COOL_DARK);
                crenels(s, 1.8f, 1.8f, w - 1.8f, d - 1.8f, h, .5f, Look.STONE_COOL);
                s.box(cx - .8f, 0, d - 1.86f, cx + .8f, 2.0f, d - 1.7f, 0x24242C);
                s.box(cx - 1.0f, 2.0f, d - 1.9f, cx + 1.0f, 2.3f, d - 1.7f, Look.STONE_COOL_DARK);
                for (float y = 2.6f; y < h - .6f; y += 1.4f) for (float x : new float[]{cx - 1.2f, cx + 1.2f}) s.box(x - .12f, y, d - 1.86f, x + .12f, y + .6f, d - 1.75f, 0x24242C);
                for (float x : new float[]{1.6f, w - 1.6f}) for (float z : new float[]{1.6f, d - 1.6f}) {
                    s.cyl(x, z, 0, h + 1.2f, 1.0f, .9f, 8, Look.STONE_COOL);
                    if (L >= 2) s.cone(x, z, h + 1.2f, h + 2.8f, 1.15f, 8, Look.SLATE);
                    else crenelRing(s, x, z, .9f, h + 1.4f, 6, Look.STONE_COOL);
                }
                for (float x : new float[]{cx - 1.3f, cx + 1.3f}) s.box(x - .35f, h - 2.4f, d - 1.75f, x + .35f, h - .3f, d - 1.68f, team);
                flagPole(s, a, cx, cz, h, 2.0f, team, t, 1.2f);
            }
            case "royal_court" -> {   // dynasty pagoda court
                s.box(-.1f, -.4f, -.1f, w + .1f, .5f, d + .1f, Look.STONE_COOL_DARK);
                s.box(.8f, .5f, .8f, w - .8f, .9f, d - .8f, Look.STONE_COOL);
                for (float x = 2.3f; x <= w - 2.2f; x += (w - 4.6f) / 4) for (float z : new float[]{2.3f, d - 2.3f}) post(s, x, z, .9f, 3.3f, .17f, 0xB8402C);
                for (float z = 2.3f + (d - 4.6f) / 4; z < d - 2.4f; z += (d - 4.6f) / 4) for (float x : new float[]{2.3f, w - 2.3f}) post(s, x, z, .9f, 3.3f, .17f, 0xB8402C);
                s.box(2.6f, .9f, 2.6f, w - 2.6f, 3.3f, d - 2.6f, Look.PLASTER);
                door(s, cx, d - 2.6f, 1.2f, 2.0f, 0xB8402C);
                pagodaRoof(s, 1.6f, 1.6f, w - 1.6f, d - 1.6f, 3.3f, 1.1f);
                s.box(3.4f, 4.3f, 3.4f, w - 3.4f, 5.9f, d - 3.4f, 0xB8402C);
                pagodaRoof(s, 2.6f, 2.6f, w - 2.6f, d - 2.6f, 5.9f, .9f);
                float top = 6.8f;
                if (L >= 2) { s.box(4.2f, 6.8f, 4.2f, w - 4.2f, 8.0f, d - 4.2f, 0xB8402C); pagodaRoof(s, 3.5f, 3.5f, w - 3.5f, d - 3.5f, 8.0f, .8f); top = 8.8f; }
                s.cyl(cx, cz, top, top + .9f, .12f, .05f, 6, Look.GOLD);
                s.ball(cx, top + .25f, cz, .25f, .25f, .25f, 6, 3, Look.GOLD);
                for (float x : new float[]{1.0f, w - 1.0f}) { post(s, x, d - .4f, .5f, 1.6f, .08f, Look.STONE_COOL_DARK); s.ball(x, 1.85f, d - .4f, .3f, .35f, .3f, 6, 3, 0xE85C3C); a.ball(x, 1.85f, d - .4f, .16f, .18f + .02f * (float) Math.sin(t * 5 + x), .16f, 5, 3, 0xFFE08A); }
                flagPole(s, a, .7f, .7f, .5f, 4.0f, team, t, 1.2f); flagPole(s, a, w - .7f, .7f, .5f, 4.0f, team, t, 1.2f);
            }
            case "siege_works" -> {
                pad(s, w, d, flag);
                s.box(.2f, 0, .2f, w - .2f, 1.6f, .9f, Look.STONE_COOL);
                s.box(.2f, 0, .2f, .9f, 1.6f, d - .2f, Look.STONE_COOL);
                s.box(1.0f, 0, 1.0f, 5.2f, 2.6f, 4.0f, Look.PLASTER);
                timber(s, 1.0f, 1.0f, 5.2f, 4.0f, 0, 2.6f, Look.WOOD_DARK);
                s.gable(1.0f, 1.0f, 5.2f, 4.0f, 2.6f, 1.5f, true, .3f, Look.TILE_RED, Look.PLASTER);
                door(s, 3.1f, 4.0f, 1.6f, 1.9f, Look.WOOD_DARK);
                crane(s, a, w - 2.4f, 2.6f, 6.0f, t);
                // catapult frame in progress
                s.box(cx - .9f, .3f, cz + 1.2f, cx + .9f, .5f, cz + 3.2f, Look.WOOD);
                for (float x : new float[]{cx - 1.0f, cx + 1.0f}) for (float z : new float[]{cz + 1.4f, cz + 3.0f}) s.prismL(x, .35f, z, x + (x < cx ? -.06f : .06f), .35f, z, .35f, .35f, 8, Look.WOOD_DARK);
                for (float x : new float[]{cx - .7f, cx + .7f}) log(s, x, .5f, cz + 2.0f, x, 1.8f, cz + 1.8f, .08f, Look.WOOD_DARK);
                log(s, cx, .6f, cz + 3.0f, cx, 2.2f, cz + 1.4f, .08f, Look.WOOD_LIGHT);
                for (int i = 0; i < 3; i++) s.ball(w - 1.5f + (i % 2) * .5f, .35f + (i / 2) * .5f, d - 1.3f, .32f, .3f, .32f, 6, 3, Look.STONE_COOL_DARK);
                if (L >= 2) { crane(s, a, 2.5f, d - 3.0f, 5.0f, t + 2.1f); for (float x = 1.6f; x < w - 1.0f; x += 1.6f) s.box(x, 1.6f, .2f, x + .7f, 2.1f, .9f, Look.STONE_COOL); }
                flagPole(s, a, w - .6f, d - .6f, 0, 3.6f, team, t, 1.0f);
            }
            default -> generic(s, a, w, d, L, team, t);
        }
    }

    /** Wide, flared two-step roof (dynasty). */
    static void pagodaRoof(Mesh s, float x0, float z0, float x1, float z1, float y, float h) {
        s.hip(x0, z0, x1, z1, y, h * .25f, .9f, .9f, Look.darker(Look.SLATE, .25f));
        s.hip(x0, z0, x1, z1, y + h * .25f, h * .75f, 0, Math.min(x1 - x0, z1 - z0) * .32f, Look.darker(Look.SLATE, .1f));
    }

    static void generic(Mesh s, Mesh a, float w, float d, int L, int team, float t) {
        pad(s, w, d, Look.STONE_DARK);
        float h = 1.6f + L * .6f;
        s.box(.3f, 0, .3f, w - .3f, h, d - .3f, Look.PLASTER);
        s.hip(.3f, .3f, w - .3f, d - .3f, h, Math.min(w, d) * .35f, .2f, .3f, team);
        flagPole(s, a, .3f, .3f, h, 1.4f, team, t, .7f);
    }
}
