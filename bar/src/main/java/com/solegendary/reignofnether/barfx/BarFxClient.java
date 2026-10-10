package com.solegendary.reignofnether.barfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BAR-style battle effects, client side: thin bright beams, muzzle flashes, rocket smoke trails, very bright short
 * bloom-like flashes on impact followed by sparks and lingering dark smoke, shockwave rings, tumbling debris coloured
 * like the ground, scorch marks and craters that accumulate and fade slowly, and a little camera shake on big blasts.
 * Ported from Beyond and Tabs' original client Fx system. Driven by {@link BarFxClientboundPacket} events plus trails
 * sampled from projectile entities the client already knows about.
 */
@OnlyIn(Dist.CLIENT)
public final class BarFxClient {
    private BarFxClient() { }

    private static final Minecraft MC = Minecraft.getInstance();

    // particle kinds
    static final int FLASH = 0, SMOKE = 1, SPARK = 2, DEBRIS = 3, FIRE = 4, RING = 5, BEAM = 6, DECAL = 7, EMITTER = 8;
    static final int MAX = 4000, MAX_DECALS = 260, MAX_DEBRIS = 900;

    static final class P {
        int kind, color, color2;
        float x, y, z, vx, vy, vz, x2, y2, z2, born, life, size, grow, spin, alpha = 1;
        float ax, ay, az = 1;          // debris spin axis
        float ground = Float.NaN;      // debris: ground height under it
        int groundKey = Integer.MIN_VALUE;
        float rate, acc;               // emitters
        boolean crater;
    }

    static final List<P> live = new ArrayList<>(), decals = new ArrayList<>();
    static int debrisCount = 0;
    static final Random R = new Random();
    private static final P SCRATCH = new P();

    static float clock = 0, trailAcc = 0;
    static long lastNanos = -1;
    static ClientLevel lastLevel = null;

    // camera, captured every frame
    static float camX, camY, camZ, lookX, lookY = -1, lookZ;
    static float leftX = 1, leftY, leftZ, upX, upY = 1, upZ;
    static boolean ortho = false;
    static float zoom = 30;

    // camera shake
    static float shakeAmt = 0;

    static float rnd(float a, float b) { return a + R.nextFloat() * (b - a); }

    static P add(int kind, float x, float y, float z, float life, float size, int color) {
        if (kind == DECAL) {
            P p = new P();
            init(p, kind, x, y, z, life, size, color);
            if (decals.size() >= MAX_DECALS) decals.remove(0);
            decals.add(p);
            return p;
        }
        if (live.size() >= MAX || (kind == DEBRIS && debrisCount >= MAX_DEBRIS)) {
            init(SCRATCH, kind, x, y, z, life, size, color); // dropped: callers may still set fields on it
            return SCRATCH;
        }
        P p = new P();
        init(p, kind, x, y, z, life, size, color);
        live.add(p);
        if (kind == DEBRIS) debrisCount++;
        return p;
    }

    private static void init(P p, int kind, float x, float y, float z, float life, float size, int color) {
        p.kind = kind; p.x = x; p.y = y; p.z = z; p.born = clock; p.life = Math.max(.01f, life); p.size = size; p.color = color;
        p.vx = p.vy = p.vz = 0; p.grow = 1; p.spin = 0; p.alpha = 1; p.ground = Float.NaN; p.groundKey = Integer.MIN_VALUE;
    }

    public static void clear() {
        live.clear(); decals.clear(); debrisCount = 0; shakeAmt = 0;
    }

    // ------------------------------------------------------------------ events from the server

    public static void ingest(List<BarFx.Event> events) {
        if (MC.level == null || events == null) return;
        try {
            boolean busy = live.size() > MAX * 3 / 4;
            for (BarFx.Event e : events) {
                if (e == null || !visible(e.x, e.y, e.z)) continue;
                switch (e.type) {
                    case BarFx.SHOT -> { if (!busy || R.nextInt(3) == 0) shot(e.kind, e.x, e.y, e.z, e.a, e.b, e.c); }
                    case BarFx.IMPACT -> { if (!busy || R.nextInt(2) == 0) hit(e.kind, e.flags, e.x, e.y, e.z, e.a, e.c, e.b); }
                    case BarFx.EXPLOSION -> explosion(e.kind, e.x, e.y, e.z, e.a);
                    case BarFx.DEATH -> death(e.kind, e.x, e.y, e.z, e.a, e.b);
                    case BarFx.BUILDING_PART -> buildingPart(e.x, e.y, e.z, (int) e.a);
                    case BarFx.COLLAPSE -> collapse(e.x, e.y, e.z, e.a, e.b);
                    default -> { }
                }
            }
        } catch (Exception ignored) { }
    }

    /** On screen-ish and not hidden by fog of war. */
    static boolean visible(float x, float y, float z) {
        if (!near(x, y, z, 24)) return false;
        try {
            return FogOfWarClientEvents.isInBrightChunk(BlockPos.containing(x, y, z));
        } catch (Exception ex) {
            return true;
        }
    }

    static boolean near(float x, float y, float z, float margin) {
        float dx = x - camX, dy = y - camY, dz = z - camZ;
        if (ortho) {
            // distance from the line of sight through the middle of the screen
            float along = dx * lookX + dy * lookY + dz * lookZ;
            float px = dx - lookX * along, py = dy - lookY * along, pz = dz - lookZ * along;
            float r = zoom * 1.2f + 16 + margin;
            return px * px + py * py + pz * pz < r * r;
        }
        float r = 128 + margin;
        return dx * dx + dy * dy + dz * dz < r * r;
    }

    /** A weapon fires from (x,y,z) at (x2,y2,z2). */
    static void shot(int kind, float x, float y, float z, float x2, float y2, float z2) {
        float dx = x2 - x, dy = y2 - y, dz = z2 - z, l = Math.max(.01f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        float ux = dx / l, uy = dy / l, uz = dz / l, mx = x + ux * .3f, my = y + uy * .3f, mz = z + uz * .3f;
        switch (kind) {
            case BarFx.K_SONIC -> {
                beam(x, y, z, x2, y2, z2, .4f, .22f, 0x6FE6FF);
                beam(x, y, z, x2, y2, z2, .25f, .1f, 0xD8FFFF);
                flash(mx, my, mz, .9f, .14f, 0x9FF0FF);
                flash(x2, y2, z2, 1.2f, .18f, 0xBFF6FF);
                sparks(x2, y2, z2, 12, 5f, 0x9FF0FF, ux, uz);
                ring(x2, groundOr(x2, y2, z2), z2, 2.4f, .4f, 0x9FF0FF);
                // no shake: repeated attacks (a Bone Colossus on a building) shook the screen nonstop
            }
            case BarFx.K_ARROW -> {
                flash(mx, my, mz, .25f, .06f, 0xFFE0A0);
                P p = smoke(mx, my, mz, .12f, .7f, 0xE8E4DC, .35f); p.vx = ux * .5f; p.vz = uz * .5f; p.vy = .2f;
            }
            case BarFx.K_FIREBALL -> {
                flash(mx, my, mz, .5f, .1f, 0xFFA040);
                for (int i = 0; i < 2; i++) { P p = smoke(mx, my, mz, .2f, 1.2f, 0x6A625A, .45f); p.vy = .4f; }
            }
            case BarFx.K_BIG_FIREBALL -> {
                flash(mx, my, mz, 1f, .16f, 0xFF9030);
                for (int i = 0; i < 5; i++) {
                    P p = smoke(mx, my, mz, .35f, 2f, 0x4A443E, .55f);
                    p.vx = ux * rnd(.5f, 1.5f) + rnd(-.4f, .4f); p.vz = uz * rnd(.5f, 1.5f) + rnd(-.4f, .4f); p.vy = rnd(.2f, .6f);
                }
            }
            case BarFx.K_MAGIC -> flash(mx, my, mz, .55f, .14f, 0xC8A0FF);
            case BarFx.K_ROCKET, BarFx.K_TNT -> {
                flash(mx, my, mz, .45f, .1f, 0xFFC070);
                for (int i = 0; i < 4; i++) {
                    P p = smoke(x, y, z, .25f, 1.4f, 0xEDEDED, .5f);
                    p.vx = -ux * rnd(.5f, 1.5f) + rnd(-.5f, .5f); p.vz = -uz * rnd(.5f, 1.5f) + rnd(-.5f, .5f); p.vy = rnd(0, .4f);
                }
            }
            case BarFx.K_THROWN, BarFx.K_POTION -> { P p = smoke(mx, my, mz, .12f, .6f, 0xE8E4DC, .3f); p.vy = .2f; }
            default -> { }
        }
    }

    /** Something lands. where: ground / unit / building; (dx,dz) its horizontal heading; size ~1 normal. */
    static void hit(int kind, int where, float x, float y, float z, float dx, float dz, float size) {
        size = Math.max(.3f, Math.min(size, 3f));
        switch (kind) {
            case BarFx.K_SONIC -> { }
            case BarFx.K_FIREBALL -> {
                flash(x, y, z, .8f, .12f, 0xFFB050);
                for (int i = 0; i < 3; i++) { P f = add(FIRE, x + rnd(-.3f, .3f), y + rnd(0, .3f), z + rnd(-.3f, .3f), rnd(.3f, .55f), rnd(.2f, .35f), 0xFF9A30); f.vy = rnd(.6f, 1.4f); f.grow = 1.4f; }
                sparks(x, y, z, 6, 3.5f, 0xFFD890, -dx, -dz);
                for (int i = 0; i < 2; i++) { P p = smoke(x, y + .2f, z, .25f, 2.2f, 0x3A3632, .55f); p.vy = .5f; p.grow = 1.8f; }
                if (where == BarFx.AT_GROUND) scorch(x, y, z, .55f, 30, 0x16120E);
            }
            case BarFx.K_BIG_FIREBALL, BarFx.K_TNT, BarFx.K_ROCKET -> flash(x, y, z, .9f, .1f, 0xFFD080);
            case BarFx.K_MAGIC -> {
                flash(x, y, z, .9f, .15f, 0xD8B8FF);
                sparks(x, y, z, 8, 3.5f, 0xD8B8FF, -dx, -dz);
                ring(x, groundOr(x, y, z), z, 1.3f, .3f, 0xC8A0FF);
            }
            case BarFx.K_POTION -> {
                for (int i = 0; i < 4; i++) { P p = smoke(x + rnd(-.4f, .4f), y, z + rnd(-.4f, .4f), .3f, 1.2f, 0xB890D8, .4f); p.vy = .3f; p.grow = 2f; }
            }
            case BarFx.K_MELEE -> {
                flash(x, y, z, .28f * size, .05f, 0xFFF4D8);
                sparks(x, y, z, where == BarFx.AT_BUILDING ? 3 : 4, 2.5f, 0xFFF0C0, dx, dz);
                if (where == BarFx.AT_BUILDING) dust(x, y - .4f, z, .35f, 0xB8A890);
            }
            case BarFx.K_HEAVY_MELEE -> {
                flash(x, y, z, .55f * size, .08f, 0xFFF0D0);
                sparks(x, y, z, 8, 3.5f, 0xFFE8B0, dx, dz);
                float g = groundY(x, y, z);
                int gc = groundColor(x, g, z);
                for (int i = 0; i < 3; i++) dust(x + rnd(-.5f, .5f), g, z + rnd(-.5f, .5f), .35f, gc);
                chunks(x, g + .1f, z, 2, 2.5f, gc, 2.5f);
                // no shake on hits - with many units it never stops
            }
            default -> {   // arrows, thrown things
                if (where == BarFx.AT_GROUND) {
                    int gc = groundColor(x, y, z);
                    dust(x, y, z, .25f, gc);
                    chunks(x, y, z, 1, 1.5f, gc, 1.8f);
                    sparks(x, y, z, 1, 1.5f, 0xFFE8C0, -dx, -dz);
                } else if (where == BarFx.AT_BUILDING) {
                    dust(x, y, z, .25f, 0xB8A890);
                    sparks(x, y, z, 2, 2f, 0xFFE8C0, -dx, -dz);
                } else {
                    flash(x, y, z, .22f, .05f, 0xFFF0D0);
                    sparks(x, y, z, 2, 1.8f, 0xFFE8C0, dx, dz);
                }
            }
        }
    }

    static void explosion(int kind, float x, float y, float z, float r) {
        boolean energy = kind == BarFx.K_MAGIC;
        int core = energy ? 0xD8B8FF : (kind == BarFx.K_FIREBALL || kind == BarFx.K_BIG_FIREBALL ? 0xFFA040 : 0xFFC870);
        float g = groundY(x, y, z);
        boolean onGround = y - g < 2.5f;
        int ground = groundColor(x, g, z);
        // the very bright, very short bloom first
        flash(x, y + .3f, z, .8f + r * .55f, .2f, 0xFFFFFF);
        flash(x, y + .3f, z, .6f + r * .9f, .32f, core);
        int fires = (int) (3 + r * 2);
        for (int i = 0; i < fires; i++) {
            P f = add(FIRE, x + rnd(-.4f, .4f) * r, y + rnd(0, .4f) * r, z + rnd(-.4f, .4f) * r, rnd(.35f, .7f),
                    rnd(.25f, .45f) * (1 + r * .3f), energy ? core : (R.nextBoolean() ? 0xFF9A30 : 0xFFC860));
            f.vy = rnd(.8f, 2f); f.grow = 1.4f;
        }
        sparks(x, y, z, (int) (6 + r * 6), 4 + r * 2, energy ? core : 0xFFD890, 0, 0);
        if (onGround) chunks(x, g + .2f, z, (int) (2 + r * 3), 3 + r * 1.5f, ground, 4f);
        int smokes = (int) (3 + r * 2.5f);
        for (int i = 0; i < smokes; i++) {
            P p = smoke(x + rnd(-.5f, .5f) * r, y + rnd(0, .5f) * r, z + rnd(-.5f, .5f) * r, rnd(.3f, .55f) * (1 + r * .25f),
                    rnd(3f, 5.5f), energy ? 0x5A6058 : 0x3A3632, .7f);
            p.vy = rnd(.3f, .9f); p.vx = rnd(-.3f, .3f); p.vz = rnd(-.3f, .3f); p.grow = 1.8f;
        }
        if (r >= 1.8f) ring(x, onGround ? g : y, z, r * 1.5f, .35f, energy ? core : 0xFFE0B0);
        if (r >= 2.5f) emitter(x, onGround ? g + .3f : y, z, 3 + r, .2f, r * .3f, 0x2E2A26);
        if (onGround) {
            scorch(x, g, z, .5f + r * .55f, 40, 0x16120E);
            if (r >= 2) crater(x, g, z, r * .45f, ground);
        }
        if (r >= 2.5f) shake(r * .12f, x, y, z);   // only real blasts (creepers, commander death), not small pops
    }

    /** A unit falls. Constructs burst into sparks and leave a smoking wreck; living things leave a puff of dust. */
    static void death(int kind, float x, float y, float z, float w, float h) {
        float scale = Math.max(.4f, Math.min(4, Math.max(w, h * .6f)));
        float cy = y + h * .5f;
        switch (kind) {
            case BarFx.D_CONSTRUCT -> {
                flash(x, cy, z, .7f * scale, .16f, 0xFFD890);
                flash(x, cy, z, .4f * scale, .08f, 0xFFFFFF);
                sparks(x, cy, z, (int) (8 * scale), 4f, 0xFFD890, 0, 0);
                chunks(x, cy, z, (int) (4 + 3 * scale), 3.5f, 0x8C8C90, 10f);
                for (int i = 0; i < 4; i++) { P p = smoke(x, cy, z, .3f * scale, 3f, 0x3A3A3E, .6f); p.vy = .6f; p.grow = 1.8f; }
                emitter(x, y + .3f, z, 4 + scale * 2, .3f, .25f * scale, 0x34322F);
                scorch(x, groundY(x, y + .5f, z), z, .5f + scale * .5f, 35, 0x16120E);
                // (no shake on unit deaths: a big fight would never stop shaking)
            }
            case BarFx.D_BONE -> {
                chunks(x, cy, z, 5, 2.5f, 0xE8E4D0, 5f);
                dust(x, y, z, .3f * scale, 0xD8D4C4);
            }
            case BarFx.D_SLIME -> {
                chunks(x, cy, z, (int) (3 + 2 * scale), 3f, 0x7FCF5F, 3f);
                P p = smoke(x, cy, z, .35f * scale, 1.2f, 0xA8E890, .4f); p.vy = .3f; p.grow = 2f;
            }
            default -> {
                int c = groundColor(x, y, z);
                int n = scale > 1.4f ? 6 : 3;
                for (int i = 0; i < n; i++) dust(x + rnd(-.4f, .4f) * scale, y, z + rnd(-.4f, .4f) * scale, .25f * scale, c);
                P p = smoke(x, cy, z, .2f * scale, 1.6f, 0x4A4440, .35f); p.vy = .4f; p.grow = 1.8f;
                // (no shake for big-unit deaths either)
            }
        }
    }

    /** One block of a building is knocked out: a burst of rubble that stays around for a while. */
    static void buildingPart(float x, float y, float z, int col) {
        flash(x, y, z, .5f, .1f, 0xFFD8A0);
        chunks(x, y, z, 3, 3f, col, rnd(7, 12));
        sparks(x, y, z, 3, 3f, 0xFFE0A0, 0, 0);
        dust(x, y, z, .4f, col);
        P p = smoke(x, y, z, .3f, 2.5f, 0x4A4440, .45f); p.vy = .5f; p.grow = 1.8f;
    }

    /** A whole building comes down: fire, rubble, a dust cloud, a shockwave, smoke that lingers. */
    static void collapse(float x, float y, float z, float half, float height) {
        half = Math.min(half, 12); height = Math.min(height, 24);
        float g = groundY(x, y + 1, z);
        flash(x, g + 1.5f, z, 2 + half * .7f, .25f, 0xFFFFFF);
        flash(x, g + 1.5f, z, 1.5f + half * .9f, .45f, 0xFFB060);
        ring(x, g, z, half * 2.2f, .7f, 0xFFE0B0);
        for (int i = 0; i < 6 + half * 2; i++) {
            P f = add(FIRE, x + rnd(-half, half), g + rnd(.5f, Math.max(1, height * .6f)), z + rnd(-half, half), rnd(.5f, 1.1f), rnd(.4f, .8f), 0xFF9A30);
            f.vy = rnd(1, 2.5f); f.grow = 1.3f;
        }
        sparks(x, g + 1, z, (int) (20 + half * 4), 8, 0xFFE0A0, 0, 0);
        int ground = groundColor(x, g, z);
        chunks(x, g + 1, z, (int) (8 + half * 4), 5, 0x8A7A68, 18f);
        chunks(x, g + .5f, z, (int) (4 + half * 2), 4, ground, 12f);
        for (int i = 0; i < 8 + half * 3; i++) {
            double a = R.nextDouble() * Math.PI * 2; float rr = half * rnd(.4f, 1.1f);
            P p = smoke(x + (float) Math.cos(a) * rr, g + rnd(0, 1.5f), z + (float) Math.sin(a) * rr, rnd(.5f, 1f), rnd(3.5f, 6f),
                    i % 3 == 0 ? 0x3A3632 : 0xA89C8A, .65f);
            p.vx = (float) Math.cos(a) * rnd(.3f, 1.2f); p.vz = (float) Math.sin(a) * rnd(.3f, 1.2f); p.vy = rnd(.2f, .8f); p.grow = 1.6f;
        }
        int emitters = 1 + (int) (half / 3);
        for (int i = 0; i < emitters; i++)
            emitter(x + rnd(-half, half) * .6f, g + .5f, z + rnd(-half, half) * .6f, rnd(10, 16), .25f, .6f, 0x2E2A26);
        scorch(x, g, z, half * 1.3f, 60, 0x1A1610);
        shake(.25f + half * .04f, x, g, z);
    }

    // ------------------------------------------------------------------ building blocks

    static P flash(float x, float y, float z, float size, float life, int c) { return add(FLASH, x, y, z, life, size, c); }

    static P smoke(float x, float y, float z, float size, float life, int c, float alpha) {
        P p = add(SMOKE, x, y, z, life, size, c); p.alpha = alpha; p.grow = 1.2f; p.spin = rnd(0, 6.28f); return p;
    }

    static P dust(float x, float y, float z, float size, int c) {
        P p = smoke(x, y + .15f, z, size, rnd(.9f, 1.5f), lighter(c, .25f), .55f);
        p.vx = rnd(-.4f, .4f); p.vz = rnd(-.4f, .4f); p.vy = rnd(.3f, .7f); p.grow = 1.8f; return p;
    }

    static void sparks(float x, float y, float z, int n, float speed, int c, float bx, float bz) {
        n = Math.min(n, 120);
        for (int i = 0; i < n; i++) {
            P p = add(SPARK, x, y, z, rnd(.18f, .45f), rnd(.025f, .045f), c);
            double a = R.nextDouble() * Math.PI * 2; float s = speed * rnd(.4f, 1f);
            p.vx = (float) Math.cos(a) * s + bx * speed * .6f; p.vz = (float) Math.sin(a) * s + bz * speed * .6f; p.vy = rnd(.3f, 1.2f) * speed;
        }
    }

    /** Tumbling chunks; they bounce, settle and stay for 'rest' seconds before shrinking away. */
    static void chunks(float x, float y, float z, int n, float speed, int c, float rest) {
        n = Math.min(n, 80);
        for (int i = 0; i < n; i++) {
            P p = add(DEBRIS, x, y + .1f, z, rest * rnd(.7f, 1.2f), rnd(.08f, .22f), mix(c, i % 2 == 0 ? 0x000000 : 0xFFFFFF, rnd(0, .2f)));
            double a = R.nextDouble() * Math.PI * 2; float s = speed * rnd(.3f, .8f);
            p.vx = (float) Math.cos(a) * s; p.vz = (float) Math.sin(a) * s; p.vy = rnd(.6f, 1.3f) * speed; p.spin = rnd(-12, 12);
            float ax = rnd(-1, 1), ay = rnd(-1, 1), az = rnd(-1, 1), l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (l < .01f) { ax = 0; ay = 1; az = 0; l = 1; }
            p.ax = ax / l; p.ay = ay / l; p.az = az / l;
        }
    }

    static void beam(float x, float y, float z, float x2, float y2, float z2, float life, float width, int c) {
        P p = add(BEAM, x, y, z, life, width, c); p.x2 = x2; p.y2 = y2; p.z2 = z2;
    }

    static void ring(float x, float y, float z, float radius, float life, int c) { add(RING, x, y, z, life, radius, c); }

    /** Invisible source of smoke puffs: lingering smoke over wrecks and craters. */
    static void emitter(float x, float y, float z, float life, float interval, float size, int c) {
        P p = add(EMITTER, x, y, z, life, Math.max(.15f, size), c); p.rate = Math.max(.05f, interval);
    }

    static void scorch(float x, float y, float z, float r, float life, int c) {
        float g = groundY(x, y + .5f, z);
        if (Math.abs(g - y) > 2) return;
        P p = add(DECAL, x, g + .015f + (decals.size() % 16) * .0015f, z, life, r, c); p.alpha = .6f; p.spin = rnd(0, 6.28f);
    }

    static void crater(float x, float y, float z, float r, int groundCol) {
        float g = groundY(x, y + .5f, z);
        if (Math.abs(g - y) > 2) return;
        P p = add(DECAL, x, g + .04f, z, 60, r, 0x050403); p.alpha = .75f; p.spin = rnd(0, 6.28f);
        p.crater = true; p.color2 = lighter(groundCol, .15f);
    }

    static float groundOr(float x, float y, float z) {
        float g = groundY(x, y + .5f, z);
        return Math.abs(g - y) < 3 ? g : y;
    }

    static void shake(float amount, float x, float y, float z) {
        float fx, fz;
        if (ortho && lookY < -.1f) {
            float t = (y - camY) / lookY;
            fx = camX + lookX * t; fz = camZ + lookZ * t;
        } else { fx = camX; fz = camZ; }
        float d = (float) Math.hypot(x - fx, z - fz);
        float range = ortho ? 30 + zoom : 40;
        float a = amount * .5f * Math.max(0, 1 - d / range);
        if (a > 0) shakeAmt = Math.min(.35f, shakeAmt + a);   // was 1.2: a base dying shook the screen nonstop
    }

    /** Current camera shake offset in blocks (orthographic view) - horizontal. */
    public static float shakeX() {
        if (shakeAmt < .002f) return 0;
        return shakeAmt * .5f * ((float) Math.sin(clock * 53.0) + .6f * (float) Math.sin(clock * 31.7 + 1.3));
    }

    /** Current camera shake offset in blocks (orthographic view) - vertical. */
    public static float shakeY() {
        if (shakeAmt < .002f) return 0;
        return shakeAmt * .5f * ((float) Math.sin(clock * 47.3 + 2.1) + .6f * (float) Math.sin(clock * 27.9 + .4));
    }

    // ------------------------------------------------------------------ ground

    private static final BlockPos.MutableBlockPos MPOS = new BlockPos.MutableBlockPos();

    /** Top of the first solid block at or below y (within 12 blocks), else y - 1. */
    static float groundY(float x, float y, float z) {
        ClientLevel level = MC.level;
        if (level == null) return y - 1;
        try {
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y + .5f);
            for (int i = 0; i < 12; i++) {
                MPOS.set(bx, by - i, bz);
                BlockState st = level.getBlockState(MPOS);
                if (st.isAir()) continue;
                VoxelShape s = st.getCollisionShape(level, MPOS);
                if (s.isEmpty()) {
                    if (!st.getFluidState().isEmpty()) return by - i + .9f;
                    continue;
                }
                return by - i + (float) s.max(Direction.Axis.Y);
            }
        } catch (Exception ignored) { }
        return y - 1;
    }

    static int groundColor(float x, float y, float z) {
        ClientLevel level = MC.level;
        if (level == null) return 0x7A6A55;
        try {
            float g = groundY(x, y + .5f, z);
            BlockPos pos = BlockPos.containing(x, g - .5f, z);
            BlockState st = level.getBlockState(pos);
            int c = st.getMapColor(level, pos).col;
            return c == 0 ? 0x7A6A55 : c;
        } catch (Exception e) {
            return 0x7A6A55;
        }
    }

    // ------------------------------------------------------------------ trails sampled from projectiles

    static void trails(float partialTick) {
        ClientLevel level = MC.level;
        if (level == null) return;
        boolean busy = live.size() > MAX * 3 / 4;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Projectile) || e.isRemoved()) continue;
            Vec3 v = e.getDeltaMovement();
            double sp2 = v.lengthSqr();
            if (sp2 < .02) continue;
            Vec3 pos = e.getPosition(partialTick);
            float x = (float) pos.x, y = (float) pos.y + e.getBbHeight() * .5f, z = (float) pos.z;
            if (!near(x, y, z, 0)) continue;
            float dx = (float) v.x, dy = (float) v.y, dz = (float) v.z;
            switch (BarFx.kindOf(e)) {
                case BarFx.K_ARROW -> beam(x - dx * .9f, y - dy * .9f, z - dz * .9f, x, y, z, .09f, .03f, 0xFFE6B0);
                case BarFx.K_ROCKET, BarFx.K_TNT -> {
                    if (busy && R.nextBoolean()) break;
                    P p = smoke(x - dx * .5f, y - dy * .5f, z - dz * .5f, .14f, rnd(1.2f, 1.8f), 0xEDEDED, .55f); p.vy = .15f; p.grow = 2.2f;
                    flash(x - dx * .3f, y - dy * .3f, z - dz * .3f, .18f, .05f, 0xFFB050);
                }
                case BarFx.K_FIREBALL -> {
                    P f = add(FIRE, x, y, z, .25f, .18f, 0xFF9A30); f.grow = 1.5f;
                    if (!busy && R.nextInt(3) == 0) { P p = smoke(x, y, z, .12f, .9f, 0x5A544E, .4f); p.grow = 2f; }
                }
                case BarFx.K_BIG_FIREBALL -> {
                    P f = add(FIRE, x, y, z, .35f, .4f, 0xFF8A20); f.grow = 1.5f;
                    if (!busy) { P p = smoke(x - dx * .5f, y - dy * .5f, z - dz * .5f, .3f, 1.8f, 0x3A3632, .5f); p.grow = 2f; p.vy = .2f; }
                }
                case BarFx.K_MAGIC -> flash(x, y, z, .25f, .15f, 0xC8A0FF);
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ frame: step + draw

    public static void onRenderFrame(PoseStack poseStack, Camera camera, float partialTick) {
        ClientLevel level = MC.level;
        if (level == null) { if (!live.isEmpty() || !decals.isEmpty()) clear(); lastLevel = null; return; }
        if (level != lastLevel) { clear(); lastLevel = level; }

        long nanos = System.nanoTime();
        float dt = lastNanos < 0 ? 0 : Math.min(.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        if (MC.isPaused()) dt = 0;
        clock += dt;
        shakeAmt *= (float) Math.exp(-dt * 5);

        // camera
        Vec3 cp = camera.getPosition();
        camX = (float) cp.x; camY = (float) cp.y; camZ = (float) cp.z;
        Vector3f look = camera.getLookVector(), left = camera.getLeftVector(), up = camera.getUpVector();
        lookX = look.x(); lookY = look.y(); lookZ = look.z();
        leftX = left.x(); leftY = left.y(); leftZ = left.z();
        upX = up.x(); upY = up.y(); upZ = up.z();
        ortho = OrthoviewClientEvents.isEnabled();
        zoom = OrthoviewClientEvents.getZoom();

        trailAcc += dt;
        if (trailAcc >= 1 / 30f) {
            trailAcc = 0;
            trails(partialTick);
        }

        // step and expire
        int w = 0;
        debrisCount = 0;
        for (int i = 0; i < live.size(); i++) {
            P p = live.get(i);
            if (clock - p.born >= p.life) continue;
            step(p, dt);
            if (p.kind == DEBRIS) debrisCount++;
            live.set(w++, p);
        }
        for (int i = live.size() - 1; i >= w; i--) live.remove(i);
        int wd = 0;
        for (int i = 0; i < decals.size(); i++) {
            P p = decals.get(i);
            if (clock - p.born < p.life) decals.set(wd++, p);
        }
        for (int i = decals.size() - 1; i >= wd; i--) decals.remove(i);

        if (live.isEmpty() && decals.isEmpty()) return;

        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        Matrix4f mat = poseStack.last().pose();

        // 1. scorch marks and craters
        if (!decals.isEmpty()) {
            begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
            for (P p : decals) if (near(p.x, p.y, p.z, p.size)) drawDecal(p, clock - p.born);
            buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);
        }
        // 2. debris
        begin(buffers, BarFxRenderTypes.SOLID, mat);
        for (P p : live) if (p.kind == DEBRIS && near(p.x, p.y, p.z, 1)) drawDebris(p, (clock - p.born) / p.life);
        buffers.endBatch(BarFxRenderTypes.SOLID);
        // 3. smoke
        begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (P p : live) if (p.kind == SMOKE && near(p.x, p.y, p.z, 4)) drawSmoke(p, (clock - p.born) / p.life);
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);
        // 4. light: flashes, fire, sparks, beams, rings
        begin(buffers, BarFxRenderTypes.ADDITIVE, mat);
        for (P p : live) {
            if (p.kind == SMOKE || p.kind == DEBRIS || p.kind == EMITTER) continue;
            if (p.kind != BEAM && !near(p.x, p.y, p.z, p.size + 2)) continue;
            drawLight(p, (clock - p.born) / p.life);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        vc = null;
    }

    static void step(P p, float dt) {
        switch (p.kind) {
            case SPARK -> { p.vy -= 18 * dt; p.vx *= 1 - 1.5f * dt; p.vz *= 1 - 1.5f * dt; }
            case DEBRIS -> {
                if (p.vx == 0 && p.vy == 0 && p.vz == 0) return; // settled
                p.vy -= 22 * dt;
                int key = ((int) Math.floor(p.x) * 73856093) ^ ((int) Math.floor(p.z) * 19349663);
                if (key != p.groundKey || Float.isNaN(p.ground)) { p.groundKey = key; p.ground = groundY(p.x, p.y + .5f, p.z); }
                float g = p.ground;
                if (p.y + p.vy * dt < g + p.size * .5f && p.vy < 0) {
                    p.vy *= -.3f; p.vx *= .5f; p.vz *= .5f; p.spin *= .5f;
                    p.y = g + p.size * .5f;
                    if (Math.abs(p.vy) < .6f) { p.vx = p.vy = p.vz = 0; return; }
                }
            }
            case SMOKE -> { p.vx *= 1 - .6f * dt; p.vz *= 1 - .6f * dt; p.vy *= 1 - .3f * dt; p.vx += .25f * dt; }
            case FIRE -> { p.vx *= 1 - 3 * dt; p.vz *= 1 - 3 * dt; p.vy *= 1 - 1.5f * dt; }
            case EMITTER -> {
                p.acc += dt;
                if (p.acc >= p.rate && live.size() < MAX * 3 / 4) {
                    p.acc = 0;
                    float k = (clock - p.born) / p.life;
                    P s = smoke(p.x + rnd(-.3f, .3f), p.y, p.z + rnd(-.3f, .3f), p.size * rnd(.8f, 1.2f) * (1 - k * .5f), rnd(3f, 5f), p.color, .5f * (1 - k * .6f));
                    s.vy = rnd(.6f, 1.2f); s.vx = rnd(-.15f, .15f); s.vz = rnd(-.15f, .15f); s.grow = 2f;
                    if (k < .4f && R.nextInt(3) == 0) { P f = add(FIRE, p.x + rnd(-.3f, .3f), p.y, p.z + rnd(-.3f, .3f), .4f, p.size * .6f, 0xFF8A30); f.vy = 1; f.grow = 1.3f; }
                }
                return;
            }
            default -> { }
        }
        p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
    }

    // ------------------------------------------------------------------ drawing primitives

    static VertexConsumer vc;
    static Matrix4f mat;

    static void begin(MultiBufferSource.BufferSource buffers, RenderType type, Matrix4f m) {
        vc = buffers.getBuffer(type);
        mat = m;
    }

    static void v(float x, float y, float z, int rgb, float a) {
        vc.vertex(mat, x - camX, y - camY, z - camZ)
                .color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a)))
                .endVertex();
    }

    /** Camera-facing soft disc: centre colour/alpha fading to rimAlpha at the edge. */
    static void softDisc(float x, float y, float z, float r, int c, float a, int rim, float rimA, int seg, float spin, float rag) {
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= seg; i++) {
            double ang = spin + i * Math.PI * 2 / seg;
            float rr = r * (rag > 0 ? 1 - rag * (float) Math.abs(Math.sin(spin * 5 + (i % seg) * 2.3)) : 1);
            float cs = (float) Math.cos(ang) * rr, sn = (float) Math.sin(ang) * rr;
            float qx = x + leftX * cs + upX * sn, qy = y + leftY * cs + upY * sn, qz = z + leftZ * cs + upZ * sn;
            if (i > 0) {
                v(x, y, z, c, a);
                v(px, py, pz, rim, rimA);
                v(qx, qy, qz, rim, rimA);
                v(x, y, z, c, a);
            }
            px = qx; py = qy; pz = qz;
        }
    }

    /** Soft camera-facing ribbon from p1 to p2: bright along its middle, fading to nothing at the sides. */
    static void streak(float x1, float y1, float z1, float x2, float y2, float z2, float w, int c, float a1, float a2) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        // side = d x look
        float sx = dy * lookZ - dz * lookY, sy = dz * lookX - dx * lookZ, sz = dx * lookY - dy * lookX;
        if (!ortho) {   // perspective: use direction to camera instead of look
            float tx = camX - x1, ty = camY - y1, tz = camZ - z1;
            sx = dy * tz - dz * ty; sy = dz * tx - dx * tz; sz = dx * ty - dy * tx;
        }
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) { sx = leftX; sy = leftY; sz = leftZ; l = 1; }
        sx = sx / l * w; sy = sy / l * w; sz = sz / l * w;
        v(x1 - sx, y1 - sy, z1 - sz, c, 0); v(x1, y1, z1, c, a1); v(x2, y2, z2, c, a2); v(x2 - sx, y2 - sy, z2 - sz, c, 0);
        v(x1, y1, z1, c, a1); v(x1 + sx, y1 + sy, z1 + sz, c, 0); v(x2 + sx, y2 + sy, z2 + sz, c, 0); v(x2, y2, z2, c, a2);
    }

    /** Flat soft ring on the ground. */
    static void flatRing(float x, float y, float z, float r, float w, int c, float a, int seg) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ri = Math.max(0, r - w), ro = r + w;
            v(x + c0 * ri, y, z + s0 * ri, c, 0); v(x + c1 * ri, y, z + s1 * ri, c, 0);
            v(x + c1 * r, y, z + s1 * r, c, a); v(x + c0 * r, y, z + s0 * r, c, a);
            v(x + c0 * r, y, z + s0 * r, c, a); v(x + c1 * r, y, z + s1 * r, c, a);
            v(x + c1 * ro, y, z + s1 * ro, c, 0); v(x + c0 * ro, y, z + s0 * ro, c, 0);
        }
    }

    static void drawLight(P p, float k) {
        switch (p.kind) {
            case FLASH -> {
                float r = p.size * (.55f + .45f * (float) Math.sqrt(k));
                float a = (1 - k) * (1 - k);
                if (p.size > 1.2f) softDisc(p.x, p.y, p.z, r * 2.2f, p.color, a * .3f, p.color, 0, 12, 0, 0);
                softDisc(p.x, p.y, p.z, r, p.color, a, p.color, 0, 12, 0, 0);
                softDisc(p.x, p.y, p.z, r * .45f, 0xFFFFFF, a, 0xFFFFFF, 0, 10, 0, 0);
            }
            case FIRE -> {
                float r = p.size * (1 + (p.grow - 1) * k);
                int c = mix(p.color, 0x802010, k * .7f);
                softDisc(p.x, p.y, p.z, r, c, (1 - k) * .9f, c, 0, 8, p.born * 7, .25f);
            }
            case SPARK -> {
                float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                float vl = Math.max(.01f, sp), len = .08f + .035f * sp;
                streak(p.x - p.vx / vl * len, p.y - p.vy / vl * len, p.z - p.vz / vl * len, p.x, p.y, p.z, p.size, p.color, (1 - k) * .4f, 1 - k);
            }
            case RING -> {
                float r = p.size * (float) Math.sqrt(k), w = Math.max(.2f, p.size * .14f * (1 - k));
                flatRing(p.x, p.y + .15f, p.z, r, w, p.color, (1 - k) * .85f, 32);
            }
            case BEAM -> {
                float a = 1 - k, w = p.size * (1 - k * .6f);
                streak(p.x, p.y, p.z, p.x2, p.y2, p.z2, w * 2.4f, p.color, a * .8f, a * .8f);
                streak(p.x, p.y, p.z, p.x2, p.y2, p.z2, w * .6f, 0xFFFFFF, a, a);
            }
            default -> { }
        }
    }

    static void drawSmoke(P p, float k) {
        float r = p.size * (1 + (p.grow - 1) * (float) Math.sqrt(k)) * (1 + k);
        float a = p.alpha * (k < .1f ? k / .1f : 1) * (1 - k) * (1 - k * .3f);
        if (a < .01f) return;
        int c = mix(p.color, 0x8A8680, k * .4f);
        softDisc(p.x, p.y, p.z, r, lighter(c, .06f), a, c, 0, 10, p.spin + k * .8f, .2f);
    }

    private static final Quaternionf Q = new Quaternionf();
    private static final Vector3f[] CORNERS = new Vector3f[8];
    static { for (int i = 0; i < 8; i++) CORNERS[i] = new Vector3f(); }
    private static final int[][] FACES = { {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3} };

    static void drawDebris(P p, float k) {
        float s = p.size * .5f * (k > .85f ? (1 - k) / .15f : 1);
        if (s < .005f) return;
        float age = k * p.life;
        // spin slows to a stop once settled
        float ang = p.spin * Math.min(age, 2.5f);
        Q.identity().rotateAxis(ang, p.ax, p.ay, p.az);
        float hx = s, hy = s * .7f, hz = s * .85f;
        for (int i = 0; i < 8; i++) {
            CORNERS[i].set((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz);
            Q.transform(CORNERS[i]);
        }
        for (int[] f : FACES) {
            Vector3f a = CORNERS[f[0]], b = CORNERS[f[1]], c = CORNERS[f[2]];
            float ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z, wx = c.x - a.x, wy = c.y - a.y, wz = c.z - a.z;
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-6f) continue;
            nx /= nl; ny /= nl; nz /= nl;
            // make the normal point outwards
            float cx = (a.x + c.x) * .5f, cy = (a.y + c.y) * .5f, cz = (a.z + c.z) * .5f;
            if (nx * cx + ny * cy + nz * cz < 0) { nx = -nx; ny = -ny; nz = -nz; }
            float shade = Math.max(.45f, Math.min(1.05f, .7f + .3f * ny + .1f * nx - .05f * nz));
            int col = scale(p.color, shade);
            for (int idx : f) {
                Vector3f q = CORNERS[idx];
                v(p.x + q.x, p.y + q.y, p.z + q.z, col, 1);
            }
        }
    }

    /** Scorch marks and craters: a ragged dark disc flat on the ground that fades out over its last third. */
    static void drawDecal(P p, float age) {
        float fade = Math.min(1, (p.life - age) / (p.life * .35f)), grow = Math.min(1, age / .15f);
        float a = p.alpha * fade;
        if (a < .01f) return;
        int n = 14;
        float r = p.size * grow;
        for (int i = 0; i < n; i++) {
            double a0 = p.spin + i * Math.PI * 2 / n, a1 = p.spin + (i + 1) * Math.PI * 2 / n;
            float r0 = r * (.7f + .3f * (float) Math.abs(Math.sin(p.spin * 7 + i * 2.3)));
            float r1 = r * (.7f + .3f * (float) Math.abs(Math.sin(p.spin * 7 + ((i + 1) % n) * 2.3)));
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            // solid-ish inner part
            v(p.x, p.y, p.z, p.color, a);
            v(p.x + c0 * r0 * .75f, p.y, p.z + s0 * r0 * .75f, p.color, a * .85f);
            v(p.x + c1 * r1 * .75f, p.y, p.z + s1 * r1 * .75f, p.color, a * .85f);
            v(p.x, p.y, p.z, p.color, a);
            // soft ragged fringe
            v(p.x + c0 * r0 * .75f, p.y, p.z + s0 * r0 * .75f, p.color, a * .85f);
            v(p.x + c0 * r0 * 1.15f, p.y, p.z + s0 * r0 * 1.15f, p.color, 0);
            v(p.x + c1 * r1 * 1.15f, p.y, p.z + s1 * r1 * 1.15f, p.color, 0);
            v(p.x + c1 * r1 * .75f, p.y, p.z + s1 * r1 * .75f, p.color, a * .85f);
        }
        if (p.crater) flatRing(p.x, p.y + .005f, p.z, r * 1.05f, r * .25f, p.color2, a * .5f, 20);
    }

    // ------------------------------------------------------------------ colours

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int lighter(int c, float t) { return mix(c, 0xFFFFFF, t); }

    static int scale(int c, float s) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * s));
        int g = Math.min(255, (int) (((c >> 8) & 255) * s));
        int b = Math.min(255, (int) ((c & 255) * s));
        return (r << 16) | (g << 8) | b;
    }
}
