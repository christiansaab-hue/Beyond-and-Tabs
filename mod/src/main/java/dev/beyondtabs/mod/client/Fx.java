package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.mod.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Battle effects in the spirit of BAR: instant laser beams, muzzle flashes and gun smoke, rocket smoke trails,
 * bright additive flashes on impact, sparks, flying chunks of the ground, smoke that hangs around, scorch marks and
 * craters that fade slowly, shockwave rings and a bit of camera shake on big blasts. Purely client-side: the server
 * sends one small event per shot / hit / blast (World.Fx) and everything here is spawned from those.
 */
final class Fx {
    // particle kinds
    static final int FLASH = 0, SMOKE = 1, SPARK = 2, DEBRIS = 3, FIRE = 4, RING = 5, BEAM = 6, DECAL = 7, BOLT = 8;
    static final int MAX = 4000, MAX_DECALS = 260;

    static final class P {
        int kind, color; float x, y, z, vx, vy, vz, x2, y2, z2, born, life, size, grow, spin, alpha = 1;
    }

    static final List<P> live = new ArrayList<>(), decals = new ArrayList<>();
    static final Random R = new Random();
    static final long T0 = System.nanoTime();
    static float lastFrame = -1, trailAcc; static boolean emitTrail;

    static float now() { return (System.nanoTime() - T0) / 1e9f; }
    static float rnd(float a, float b) { return a + R.nextFloat() * (b - a); }

    static P add(int kind, float x, float y, float z, float life, float size, int color) {
        P p = new P(); p.kind = kind; p.x = x; p.y = y; p.z = z; p.born = now(); p.life = life; p.size = size; p.color = color;
        if (kind == DECAL) { if (decals.size() >= MAX_DECALS) decals.remove(0); decals.add(p); }
        else { if (live.size() >= MAX) live.remove(0); live.add(p); }
        return p;
    }

    static void clear() { live.clear(); decals.clear(); }

    // ------------------------------------------------------------------ events from the server

    static void ingest(Snapshot s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean seeAll = live.size() < MAX * 3 / 4;
        for (float[] e : s.fx) {
            int kind = (int) e[0], look = (int) e[1];
            float x = e[2], y = e[3], z = e[4], x2 = e[5], y2 = e[6], z2 = e[7], size = e[8];
            if (!near(x, z, 160)) continue;   // nobody is looking there
            String v = look >= 0 && look < Snapshot.VISUALS.size() ? Snapshot.VISUALS.get(look) : "melee";
            switch (kind) {
                case 0 -> { if (seeAll || R.nextInt(3) == 0) shot(v, x, y, z, x2, y2, z2, (int) size); }
                case 1 -> hit(v, x, y, z, x2, (int) y2, z2, size);
                case 2 -> blast(x, y, z, size);
                case 3 -> death(look, x, y, z, x2, z2, size);
                case 4 -> fall(x, y, z, x2);
                default -> { }
            }
        }
    }

    static boolean near(float x, float z, float r) {
        float dx = x - RtsCamera.focusX, dz = z - RtsCamera.focusZ;
        return !RtsCamera.active || dx * dx + dz * dz < (r + RtsCamera.dist) * (r + RtsCamera.dist);
    }

    /** A weapon fires from (x,y,z) at the aim point (x2,y2,z2). */
    static void shot(String v, float x, float y, float z, float x2, float y2, float z2, int count) {
        float dx = x2 - x, dy = y2 - y, dz = z2 - z, l = Math.max(.01f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        float ux = dx / l, uy = dy / l, uz = dz / l, mx = x + ux * .5f, my = y + uy * .5f, mz = z + uz * .5f;
        switch (v) {
            case "laser" -> {
                beam(x, y, z, x2, y2, z2, .13f, .05f, 0x6FE6FF);
                flash(mx, my, mz, .35f, .08f, 0x9FF0FF);
            }
            case "rail" -> {
                beam(x, y, z, x2, y2, z2, .5f, .09f, 0xD8EEFF);
                flash(mx, my, mz, .7f, .12f, 0xFFFFFF);
                for (int i = 0; i < 4; i++) smoke(mx + ux * i, my + uy * i, mz + uz * i, .25f, 1.2f, 0xC9D6E0, .35f).vy = .2f;
            }
            case "lightning" -> {
                float px = x, py = y + 1.5f, pz = z;
                for (int i = 1; i <= 6; i++) {
                    float k = i / 6f, jx = i < 6 ? rnd(-.6f, .6f) : 0, jy = i < 6 ? rnd(-.4f, .6f) : 0, jz = i < 6 ? rnd(-.6f, .6f) : 0;
                    float nx = x + dx * k + jx, ny = y + 1.5f + (dy - 1.5f) * k + jy, nz = z + dz * k + jz;
                    beam(px, py, pz, nx, ny, nz, .22f, .07f, 0xB8C8FF); px = nx; py = ny; pz = nz;
                }
            }
            case "bullet" -> {
                flash(mx, my, mz, .3f, .07f, 0xFFD27A);
                for (int i = 0; i < 3; i++) { P p = smoke(mx + ux * i * .25f, my, mz + uz * i * .25f, .2f, 1.6f, 0xE4E0D8, .45f); p.vx = ux * .6f; p.vz = uz * .6f; p.vy = .25f; }
            }
            case "cannonball" -> {
                flash(mx, my, mz, .7f, .12f, 0xFFB050);
                for (int i = 0; i < 7; i++) { P p = smoke(mx, my, mz, .35f, 2.5f, 0xD8D2C8, .55f); p.vx = ux * rnd(.5f, 2.5f) + rnd(-.4f, .4f); p.vz = uz * rnd(.5f, 2.5f) + rnd(-.4f, .4f); p.vy = rnd(.1f, .5f); }
            }
            case "missile", "firework" -> {
                flash(mx, my, mz, .45f, .1f, 0xFFC070);
                for (int i = 0; i < 3 + count; i++) { P p = smoke(x, y, z, .3f, 1.4f, 0xE8E8E8, .5f); p.vx = -ux * rnd(.5f, 1.5f) + rnd(-.5f, .5f); p.vz = -uz * rnd(.5f, 1.5f) + rnd(-.5f, .5f); p.vy = rnd(0, .4f); }
            }
            case "plasma" -> flash(mx, my, mz, .45f, .1f, 0x9CFF7A);
            case "flame" -> { P f = add(FIRE, mx, my, mz, .3f, .25f, 0xFF9A30); f.vx = ux * 4; f.vy = uy * 4; f.vz = uz * 4; f.grow = 1.2f; }
            case "boulder" -> { for (int i = 0; i < 3; i++) smoke(x, y - 1, z, .4f, 1f, 0xB8A890, .4f).vy = .3f; }
            default -> { }
        }
    }

    /** A projectile lands: where = 0 ground, 1 unit, 2 building; (dx,dz) its horizontal velocity; aoe its blast radius. */
    static void hit(String v, float x, float y, float z, float dx, int where, float dz, float aoe) {
        int ground = groundColor(x, y, z);
        if (aoe > 0) {
            int core = switch (v) { case "plasma" -> 0xB8FF9A; case "bone_orb" -> 0xD8C8FF; case "lightning" -> 0xC8D8FF; case "flame" -> 0xFF8A30; default -> 0xFFC870; };
            boolean energy = v.equals("plasma") || v.equals("bone_orb") || v.equals("lightning");
            explosion(x, y + .3f, z, aoe, core, energy, where != 1 ? ground : 0x6A5A4A, v.equals("flame"));
            return;
        }
        float hl = Math.max(.01f, (float) Math.hypot(dx, dz)), ux = dx / hl, uz = dz / hl;
        switch (v) {
            case "laser" -> {
                flash(x, y, z, .45f, .1f, 0x9FF0FF);
                sparks(x, y, z, 5, 3f, 0x9FF0FF, -ux, -uz);
                if (where == 0) scorch(x, y, z, .35f, 6, 0x101418);
            }
            case "rail" -> {
                flash(x, y, z, .9f, .14f, 0xFFFFFF);
                sparks(x, y, z, 12, 6f, 0xE0F0FF, ux, uz);
                if (where != 1) { chunks(x, y, z, 4, 3f, ground); scorch(x, y, z, .6f, 12, 0x14161A); }
                ring(x, y + .1f, z, 1.6f, .3f, 0xD8EEFF);
            }
            case "bullet" -> {
                sparks(x, y, z, 3, 3f, 0xFFD890, -ux, -uz);
                if (where != 1) { dust(x, y, z, .3f, ground); chunks(x, y, z, 1, 2f, ground); }
            }
            case "melee" -> {
                sparks(x, y, z, where == 2 ? 3 : 4, 2.5f, 0xFFF0C0, ux, uz);
                if (where == 2) dust(x, y - .6f, z, .35f, 0xB8A890);
            }
            default -> {   // arrows, spears, bolts
                if (where == 0) { dust(x, y, z, .3f, ground); chunks(x, y, z, 1, 1.5f, ground); }
                else if (where == 2) dust(x, y, z, .3f, 0xB8A890);
                else sparks(x, y, z, 1, 1.5f, 0xFFE8C0, ux, uz);
            }
        }
    }

    static void explosion(float x, float y, float z, float r, int core, boolean energy, int ground, boolean fireOnly) {
        flash(x, y, z, .8f + r * .55f, .2f, 0xFFFFFF);
        flash(x, y, z, .6f + r * .8f, .32f, core);
        int fires = (int) (3 + r * 2);
        for (int i = 0; i < fires; i++) {
            P f = add(FIRE, x + rnd(-.4f, .4f) * r, y + rnd(0, .4f) * r, z + rnd(-.4f, .4f) * r, rnd(.35f, .7f), rnd(.25f, .45f) * (1 + r * .3f), energy ? core : (R.nextBoolean() ? 0xFF9A30 : 0xFFC860));
            f.vy = rnd(.8f, 2f); f.grow = 1.4f;
        }
        if (fireOnly) return;
        sparks(x, y, z, (int) (6 + r * 6), 4 + r * 2, energy ? core : 0xFFD890, 0, 0);
        chunks(x, y, z, (int) (2 + r * 3), 3 + r * 1.5f, ground);
        int smokes = (int) (3 + r * 2.5f);
        for (int i = 0; i < smokes; i++) {
            P p = smoke(x + rnd(-.5f, .5f) * r, y + rnd(0, .5f) * r, z + rnd(-.5f, .5f) * r, rnd(.3f, .55f) * (1 + r * .25f), rnd(3f, 5.5f), energy ? 0x5A6058 : 0x3A3632, .7f);
            p.vy = rnd(.3f, .9f); p.vx = rnd(-.3f, .3f); p.vz = rnd(-.3f, .3f); p.grow = 1.8f;
        }
        if (r >= 1.8f) ring(x, y - .2f, z, r * 1.4f, .35f, energy ? core : 0xFFE0B0);
        scorch(x, y - .3f, z, .5f + r * .55f, 25, 0x16120E);
        if (r >= 2) crater(x, y - .3f, z, r * .45f);
        shake(r * .12f, x, z);
    }

    /** An advanced power plant goes up (BAR's AFUS). */
    static void blast(float x, float y, float z, float r) {
        flash(x, y + 2, z, r * .45f, .45f, 0xFFFFFF);
        flash(x, y + 2, z, r * .6f, .9f, 0xFFD080);
        ring(x, y + .2f, z, r * 1.1f, .7f, 0xFFF0D0);
        ring(x, y + .4f, z, r * .7f, 1.1f, 0xFFB060);
        for (int i = 0; i < 18; i++) {
            P f = add(FIRE, x + rnd(-3, 3), y + rnd(0, 4), z + rnd(-3, 3), rnd(.8f, 1.6f), rnd(.9f, 1.8f), R.nextBoolean() ? 0xFF8A30 : 0xFFD070);
            f.vy = rnd(2, 6); f.grow = 1.6f;
        }
        sparks(x, y + 1, z, 70, 14, 0xFFE0A0, 0, 0);
        chunks(x, y + .5f, z, 30, 9, groundColor(x, y, z));
        for (int i = 0; i < 34; i++) {   // the column and cap of the mushroom
            boolean cap = i >= 14; float h = cap ? rnd(8, 12) : i * .6f;
            P p = smoke(x + rnd(-1, 1) * (cap ? 4 : 1.2f), y + h, z + rnd(-1, 1) * (cap ? 4 : 1.2f), cap ? rnd(1.4f, 2.4f) : rnd(.9f, 1.4f), rnd(7, 10), 0x34302C, .75f);
            p.vy = cap ? rnd(.5f, 1.2f) : rnd(.8f, 2f); p.grow = 1.5f;
        }
        scorch(x, y, z, r * .55f, 60, 0x0E0C0A);
        crater(x, y, z, r * .3f);
        shake(1.6f, x, z);
    }

    /** A unit falls. Machines burst into sparks and smoke; living things leave a puff of dust. */
    static void death(int def, float x, float y, float z, float kx, float kz, float scale) {
        UnitDef d = def >= 0 && def < UnitDef.ALL.size() ? UnitDef.ALL.get(def) : null;
        if (d == null) return;
        if (d.race().equals("starforge")) {
            flash(x, y + scale, z, .5f * scale, .14f, 0xFFD890);
            sparks(x, y + scale, z, 6, 3f, 0xFFD890, 0, 0);
            for (int i = 0; i < 3; i++) { P p = smoke(x, y + scale, z, .3f * scale, 3f, 0x3A3A3E, .6f); p.vy = .6f; }
        } else {
            int c = groundColor(x, y, z);
            for (int i = 0; i < (scale > 1.4f ? 6 : 2); i++) { P p = dust(x + rnd(-.4f, .4f) * scale, y, z + rnd(-.4f, .4f) * scale, .25f * scale, c); p.vx += kx * .5f; p.vz += kz * .5f; }
        }
        if (scale > 1.4f) shake(.12f, x, z);
    }

    /** A building is destroyed: fire, chunks and a dust cloud (the client models also sink into rubble). */
    static void fall(float x, float y, float z, float half) {
        flash(x, y + 1.5f, z, 1 + half * .6f, .3f, 0xFFB060);
        for (int i = 0; i < 6 + half * 2; i++) {
            P f = add(FIRE, x + rnd(-half, half), y + rnd(.5f, 2.5f), z + rnd(-half, half), rnd(.5f, 1.1f), rnd(.4f, .8f), 0xFF9A30);
            f.vy = rnd(1, 2.5f); f.grow = 1.3f;
        }
        chunks(x, y + 1, z, (int) (6 + half * 3), 5, 0x8A7A68);
        for (int i = 0; i < 8 + half * 3; i++) {
            double a = R.nextDouble() * Math.PI * 2; float rr = half * rnd(.4f, 1.1f);
            P p = smoke(x + (float) Math.cos(a) * rr, y + rnd(0, 1.5f), z + (float) Math.sin(a) * rr, rnd(.5f, 1f), rnd(3.5f, 6f), i % 3 == 0 ? 0x3A3632 : 0xA89C8A, .65f);
            p.vx = (float) Math.cos(a) * rnd(.3f, 1.2f); p.vz = (float) Math.sin(a) * rnd(.3f, 1.2f); p.vy = rnd(.2f, .8f); p.grow = 1.6f;
        }
        scorch(x, y, z, half * 1.2f, 40, 0x1A1610);
        shake(.25f + half * .04f, x, z);
    }

    // ------------------------------------------------------------------ building blocks

    static P flash(float x, float y, float z, float size, float life, int c) { return add(FLASH, x, y, z, life, size, c); }

    static P smoke(float x, float y, float z, float size, float life, int c, float alpha) {
        P p = add(SMOKE, x, y, z, life, size, c); p.alpha = alpha; p.grow = 1.2f; p.spin = rnd(0, 6.28f); return p;
    }

    static P dust(float x, float y, float z, float size, int c) {
        P p = smoke(x, y + .15f, z, size, rnd(.9f, 1.5f), Look.lighter(c, .25f), .55f);
        p.vx = rnd(-.4f, .4f); p.vz = rnd(-.4f, .4f); p.vy = rnd(.3f, .7f); p.grow = 1.8f; return p;
    }

    static void sparks(float x, float y, float z, int n, float speed, int c, float bx, float bz) {
        for (int i = 0; i < n; i++) {
            P p = add(SPARK, x, y, z, rnd(.18f, .45f), rnd(.02f, .035f), c);
            double a = R.nextDouble() * Math.PI * 2; float s = speed * rnd(.4f, 1f);
            p.vx = (float) Math.cos(a) * s + bx * speed * .6f; p.vz = (float) Math.sin(a) * s + bz * speed * .6f; p.vy = rnd(.3f, 1.2f) * speed;
        }
    }

    static void chunks(float x, float y, float z, int n, float speed, int c) {
        for (int i = 0; i < n; i++) {
            P p = add(DEBRIS, x, y + .1f, z, rnd(1.6f, 3f), rnd(.08f, .2f), Look.mix(c, i % 2 == 0 ? 0x000000 : 0xFFFFFF, rnd(0, .2f)));
            double a = R.nextDouble() * Math.PI * 2; float s = speed * rnd(.3f, .8f);
            p.vx = (float) Math.cos(a) * s; p.vz = (float) Math.sin(a) * s; p.vy = rnd(.6f, 1.3f) * speed; p.spin = rnd(-12, 12);
        }
    }

    static void beam(float x, float y, float z, float x2, float y2, float z2, float life, float width, int c) {
        P p = add(BEAM, x, y, z, life, width, c); p.x2 = x2; p.y2 = y2; p.z2 = z2;
    }

    static void ring(float x, float y, float z, float radius, float life, int c) { add(RING, x, y, z, life, radius, c); }

    static void scorch(float x, float y, float z, float r, float life, int c) {
        P p = add(DECAL, x, RtsCamera.ground(x, z) + .02f + R.nextFloat() * .01f, z, life, r, c); p.alpha = .55f; p.spin = rnd(0, 6.28f);
    }

    static void crater(float x, float y, float z, float r) {
        P p = add(DECAL, x, RtsCamera.ground(x, z) + .035f, z, 30, r, 0x050403); p.alpha = .7f; p.spin = rnd(0, 6.28f);
    }

    static void shake(float amount, float x, float z) {
        if (!RtsCamera.active) return;
        float d = (float) Math.hypot(x - RtsCamera.focusX, z - RtsCamera.focusZ);
        RtsCamera.kick(amount * Math.max(0, 1 - d / (40 + RtsCamera.dist)));
    }

    static int groundColor(float x, float y, float z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0x7A6A55;
        BlockPos pos = BlockPos.containing(x, RtsCamera.ground(x, z) - .5f, z);
        BlockState st = mc.level.getBlockState(pos);
        int c = st.getMapColor(mc.level, pos).col;
        return c == 0 ? 0x7A6A55 : c;
    }

    /** Rockets and missiles leave white smoke behind them; flames and plasma leave a faint glow. */
    static void trail(String v, float x, float y, float z, float dx, float dy, float dz) {
        if (!emitTrail) return;
        switch (v) {
            case "missile", "firework" -> {
                P p = smoke(x - dx * .5f, y - dy * .5f, z - dz * .5f, .14f, rnd(1.2f, 1.8f), 0xEDEDED, .55f); p.vy = .15f; p.grow = 2.2f;
                flash(x - dx * .5f, y - dy * .5f, z - dz * .5f, .16f, .05f, 0xFFB050);
            }
            case "plasma" -> flash(x, y, z, .22f, .12f, 0x9CFF7A);
            case "flame" -> { P f = add(FIRE, x, y, z, .25f, .18f, 0xFF9A30); f.grow = 1.5f; }
            case "cannonball", "boulder" -> { if (R.nextInt(3) == 0) { P p = smoke(x, y, z, .1f, .6f, 0xD8D2C8, .3f); p.grow = 1.5f; } }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Steps and draws everything. m: opaque / see-through mesh; add: additive (light) mesh. */
    static void render(Mesh m, Mesh add, float t) {
        float now = now(), dt = lastFrame < 0 ? 0 : Math.min(.1f, now - lastFrame); lastFrame = now;
        trailAcc += dt; emitTrail = trailAcc >= .035f; if (emitTrail) trailAcc = 0;
        for (int i = live.size() - 1; i >= 0; i--) {
            P p = live.get(i);
            float age = now - p.born;
            if (age >= p.life) { live.remove(i); continue; }
            step(p, dt);
            draw(p, age / p.life, m, add);
        }
        for (int i = decals.size() - 1; i >= 0; i--) {
            P p = decals.get(i); float age = now - p.born;
            if (age >= p.life) { decals.remove(i); continue; }
            drawDecal(p, age, m);
        }
    }

    static void step(P p, float dt) {
        switch (p.kind) {
            case SPARK -> { p.vy -= 18 * dt; p.vx *= 1 - 1.5f * dt; p.vz *= 1 - 1.5f * dt; }
            case DEBRIS -> {
                p.vy -= 22 * dt;
                float g = RtsCamera.ground(p.x, p.z);
                if (p.y + p.vy * dt < g + p.size * .5f && p.vy < 0) { p.vy *= -.3f; p.vx *= .5f; p.vz *= .5f; p.spin *= .5f; if (Math.abs(p.vy) < .6f) p.vy = 0; p.y = g + p.size * .5f; }
            }
            case SMOKE -> { p.vx *= 1 - .6f * dt; p.vz *= 1 - .6f * dt; p.vy *= 1 - .3f * dt; p.vx += .25f * dt; }   // a light breeze
            case FIRE -> { p.vx *= 1 - 3 * dt; p.vz *= 1 - 3 * dt; p.vy *= 1 - 1.5f * dt; }
            default -> { }
        }
        p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
    }

    static void draw(P p, float k, Mesh m, Mesh add) {
        switch (p.kind) {
            case FLASH -> {
                float r = p.size * (.55f + .45f * (float) Math.sqrt(k));
                add.alpha = (1 - k) * (1 - k);
                add.ballW(p.x, p.y, p.z, r, r, r, 8, 5, p.color, 1, 0);
                add.ballW(p.x, p.y, p.z, r * .5f, r * .5f, r * .5f, 6, 4, 0xFFFFFF, 1, 0);
            }
            case FIRE -> {
                float r = p.size * (1 + (p.grow - 1) * k);
                add.alpha = (1 - k) * .9f;
                add.ballW(p.x, p.y, p.z, r, r * .9f, r, 6, 4, Look.mix(p.color, 0x802010, k * .7f), 1, 0);
            }
            case SPARK -> {
                float l = .06f + .03f * (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                float vl = Math.max(.01f, (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz));
                add.alpha = 1 - k;
                add.prismW(p.x - p.vx / vl * l, p.y - p.vy / vl * l, p.z - p.vz / vl * l, p.x, p.y, p.z, p.size, p.size * .5f, 3, p.color);
            }
            case SMOKE -> {
                float r = p.size * (1 + (p.grow - 1) * (float) Math.sqrt(k)) * (1 + k);
                m.alpha = p.alpha * (k < .1f ? k / .1f : 1) * (1 - k) * (1 - k * .3f);
                if (m.alpha < .01f) return;
                m.ballW(p.x, p.y, p.z, r, r * .8f, r, 6, 4, Look.mix(p.color, 0x8A8680, k * .4f), (float) Math.cos(p.spin), (float) Math.sin(p.spin));
                m.alpha = 1;
            }
            case DEBRIS -> {
                float s = p.size;
                float c = (float) Math.cos(p.spin * k * p.life), sn = (float) Math.sin(p.spin * k * p.life);
                m.reset(); m.frame(p.x, p.y, p.z, p.spin * k * p.life, 1); m.alpha = k > .8f ? (1 - k) / .2f : 1;
                m.box(-s * .5f, -s * .5f, -s * .5f, s * .5f, s * .5f * (.6f + .4f * Math.abs(sn)), s * .5f * (.6f + .4f * Math.abs(c)), p.color);
                m.reset();
            }
            case RING -> {
                float r = p.size * (float) Math.sqrt(k), w = Math.max(.15f, p.size * .12f * (1 - k));
                add.alpha = (1 - k) * .8f;
                int n = 24;
                for (int i = 0; i < n; i++) {
                    double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    add.quad4(p.x + c0 * r, p.y + .15f, p.z + s0 * r, p.x + c1 * r, p.y + .15f, p.z + s1 * r,
                            p.x + c1 * (r + w), p.y + .3f, p.z + s1 * (r + w), p.x + c0 * (r + w), p.y + .3f, p.z + s0 * (r + w), p.color);
                }
            }
            case BEAM -> {
                add.alpha = 1 - k;
                float w = p.size * (1 - k * .6f);
                add.prismW(p.x, p.y, p.z, p.x2, p.y2, p.z2, w * 2.2f, w * 2.2f, 5, p.color);
                add.prismW(p.x, p.y, p.z, p.x2, p.y2, p.z2, w * .7f, w * .7f, 4, 0xFFFFFF);
            }
            default -> { }
        }
        add.alpha = 1; m.alpha = 1;
    }

    /** Scorch marks and craters: a ragged dark disc flat on the ground that fades out over its last third. */
    static void drawDecal(P p, float age, Mesh m) {
        float fade = Math.min(1, (p.life - age) / (p.life * .35f)), grow = Math.min(1, age / .15f);
        m.alpha = p.alpha * fade; if (m.alpha < .01f) { m.alpha = 1; return; }
        m.glow = true;
        int n = 10; float r = p.size * grow;
        for (int i = 0; i < n; i++) {
            double a0 = p.spin + i * Math.PI * 2 / n, a1 = p.spin + (i + 1) * Math.PI * 2 / n;
            float r0 = r * (.75f + .25f * (float) Math.abs(Math.sin(p.spin * 7 + i * 2.3))), r1 = r * (.75f + .25f * (float) Math.abs(Math.sin(p.spin * 7 + (i + 1) * 2.3)));
            m.quad4(p.x, p.y, p.z, p.x + (float) Math.cos(a1) * r1, p.y, p.z + (float) Math.sin(a1) * r1,
                    p.x + (float) Math.cos(a0) * r0, p.y, p.z + (float) Math.sin(a0) * r0, p.x, p.y, p.z, p.color);
        }
        m.glow = false; m.alpha = 1;
    }
}
