package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.beyondtabs.engine.Ragdoll;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.Snapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the battle: low-poly units skinned on their ragdolls, buildings (finished ones cached on the GPU, ones under
 * construction rising part by part inside scaffolding with a pale blueprint of the rest), ruins, ore outcrops on metal
 * spots, projectiles, soft contact shadows, and the RTS overlays (selection, rally lines, placement preview, pings).
 */
@Mod.EventBusSubscriber(modid = BeyondTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MatchRenderer {
    static float pingX, pingY, pingZ; static long pingAt; static boolean pingAttack;
    static void pingAt(float x, float y, float z, boolean attack) { pingX = x; pingY = y; pingZ = z; pingAt = System.currentTimeMillis(); pingAttack = attack; }

    static final Mesh M = new Mesh(), SINK = new Mesh(), GHOST = new Mesh();
    static final UnitModels.Ctx CTX = new UnitModels.Ctx();
    static final Map<String, VertexBuffer> cache = new HashMap<>();
    static final Map<String, float[]> measures = new HashMap<>();   // def|level -> {parts, height}
    static final int SHADOW = 0x000000;
    static final MultiBufferSource.BufferSource SOLID_SRC = MultiBufferSource.immediate(new BufferBuilder(1 << 20));
    static final MultiBufferSource.BufferSource TRANS_SRC = MultiBufferSource.immediate(new BufferBuilder(1 << 18));
    static final float INK_BUILDING = .035f;

    record Ruin(short def, int level, int team, float x, float z, long at, int id) { }
    static final List<Ruin> ruins = new ArrayList<>();

    static float time() { return (System.currentTimeMillis() % 3_600_000L) / 1000f; }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Snapshot s = ClientMatch.cur;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null) return;
        float a = ClientMatch.alpha(), t = time();
        PoseStack ps = e.getPoseStack(); Vec3 cam = e.getCamera().getPosition();
        Frustum fr = e.getFrustum();
        boolean strategic = RtsCamera.active && RtsCamera.strategic();
        ps.pushPose(); ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f pose = ps.last().pose();

        // 1) finished buildings straight from their GPU buffers
        RenderType quads = BTRender.SOLID;
        List<Snapshot.B> building = new ArrayList<>();
        quads.setupRenderState();
        boolean models = !s.blockStyle;
        if (models) for (Snapshot.B b : s.buildings) {
            BuildingDef d = BuildingDef.ALL.get(b.def); int[] sz = BuildingModels.size(d);
            float gy = RtsCamera.ground(b.x, b.z), x0 = b.x - sz[0] / 2f, z0 = b.z - sz[1] / 2f;
            float h = measure(d, b.level)[1];
            if (fr != null && !fr.isVisible(new AABB(x0 - 1, gy - 1, z0 - 1, x0 + sz[0] + 1, gy + h + 2, z0 + sz[1] + 1))) continue;
            if (b.progress < 1) { building.add(b); continue; }
            VertexBuffer vb = vbo(d, b.level, Look.team(b.team, s.myTeam));
            ps.pushPose(); ps.translate(x0, gy, z0);
            vb.bind(); vb.drawWithShader(ps.last().pose(), e.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
            ps.popPose();
        }
        quads.clearRenderState();

        // 2) everything that moves, in one batch
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = SOLID_SRC.getBuffer(BTRender.SOLID);
        Mesh m = M.to(vc, pose); m.vt = TRANS_SRC.getBuffer(BTRender.TRANSLUCENT);
        float camX = (float) cam.x, camY = (float) cam.y, camZ = (float) cam.z;

        if (models) for (Snapshot.B b : s.buildings) {   // animated parts + soft shadows of all buildings
            BuildingDef d = BuildingDef.ALL.get(b.def); int[] sz = BuildingModels.size(d);
            float gy = RtsCamera.ground(b.x, b.z), x0 = b.x - sz[0] / 2f, z0 = b.z - sz[1] / 2f, h = measure(d, b.level)[1];
            if (fr != null && !fr.isVisible(new AABB(x0 - 3, gy - 1, z0 - 3, x0 + sz[0] + 3, gy + h + 2, z0 + sz[1] + 3))) continue;
            buildingShadow(m, x0, gy, z0, sz[0], sz[1], h * Math.min(1, b.progress));
            if (b.progress >= 1) {
                m.reset(); m.frame(x0, gy, z0, 0, 1); m.ground(gy, 1.2f, .22f); m.ow = INK_BUILDING;
                BuildingModels.build(SINK.reset(), m, d, b.level, Look.team(b.team, s.myTeam), t);
            }
        }
        List<Runnable> ghosts = new ArrayList<>();
        if (models) { for (Snapshot.B b : building) construction(m, b, s, t, ghosts); ruins(m, s, t); }
        else blockAccents(m, s, t);
        if (!strategic) {
            spots(m, s);
            units(m, s, a, t, fr, camX, camY, camZ);
            projectiles(m, s, a);
        }
        if (RtsCamera.active && RtsScreen.placing != null && RtsScreen.ghost != null) placementPreview(m, s, t, ghosts);
        for (Runnable g : ghosts) g.run();
        SOLID_SRC.endBatch();
        TRANS_SRC.endBatch();

        // 3) thin overlay lines
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        PoseStack.Pose lp = ps.last();
        if (RtsCamera.active) overlays(lines, lp, s);
        buf.endBatch(RenderType.lines());
        ps.popPose();
    }

    // ------------------------------------------------------------------ buildings

    static float[] measure(BuildingDef d, int level) {
        return measures.computeIfAbsent(d.id() + "|" + level, k -> {
            Mesh c = new Mesh(); c.reset(); c.vc = null;
            BuildingModels.build(c, SINK.reset(), d, level, 0, 0);
            return new float[]{c.part, Math.max(1, c.maxY)};
        });
    }

    static VertexBuffer vbo(BuildingDef d, int level, int team) {
        return cache.computeIfAbsent(d.id() + "|" + level + "|" + team, k -> {
            BufferBuilder bb = new BufferBuilder(1 << 16);
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Mesh m = new Mesh().to(bb, new Matrix4f()); m.reset(); m.ground(0, 1.2f, .22f); m.ow = INK_BUILDING;
            BuildingModels.build(m, SINK.reset(), d, level, team, 0);
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind(); vb.upload(bb.end()); VertexBuffer.unbind();
            return vb;
        });
    }

    static void buildingShadow(Mesh m, float x0, float gy, float z0, float w, float d, float h) {
        float ox = -Look.SUN_X / Look.SUN_Y * h * .45f, oz = -Look.SUN_Z / Look.SUN_Y * h * .45f, y = gy + .07f;
        m.reset(); m.alpha = .2f;
        m.quad4(x0 - .3f, y, z0 - .3f, x0 + w + .3f, y, z0 - .3f, x0 + w + .3f + ox, y, z0 + d + .3f + oz, x0 - .3f + ox, y, z0 + d + .3f + oz, SHADOW);
        m.alpha = 1;
    }

    /** A building going up: finished parts, scaffolding round it, and a pale blueprint of what's still to come. */
    static void construction(Mesh m, Snapshot.B b, Snapshot s, float t, List<Runnable> ghosts) {
        BuildingDef d = BuildingDef.ALL.get(b.def); int[] sz = BuildingModels.size(d);
        float gy = RtsCamera.ground(b.x, b.z), x0 = b.x - sz[0] / 2f, z0 = b.z - sz[1] / 2f;
        float[] me = measure(d, b.level); int team = Look.team(b.team, s.myTeam);
        int limit = (int) (me[0] * Math.max(0, Math.min(1, b.progress)));
        m.reset(); m.frame(x0, gy, z0, 0, 1); m.ground(gy, 1.2f, .22f); m.limit = limit; m.ow = INK_BUILDING;
        BuildingModels.build(m, SINK.reset(), d, b.level, team, t);
        // scaffolding
        float h = me[1] * Math.min(1, b.progress + .15f);
        m.reset(); m.frame(x0, gy, z0, 0, 1); m.ground(gy, 1.2f, .22f);
        float w = sz[0], dd = sz[1];
        if (w >= 3) {
            for (float x : new float[]{-.3f, w + .3f}) for (float z : new float[]{-.3f, dd + .3f}) m.cyl(x, z, 0, h, .07f, .06f, 4, Look.WOOD_LIGHT);
            for (float y = 1.2f; y < h; y += 1.4f) {
                m.prismL(-.3f, y, -.3f, w + .3f, y, -.3f, .05f, .05f, 4, Look.WOOD_LIGHT); m.prismL(-.3f, y, dd + .3f, w + .3f, y, dd + .3f, .05f, .05f, 4, Look.WOOD_LIGHT);
                m.prismL(-.3f, y, -.3f, -.3f, y, dd + .3f, .05f, .05f, 4, Look.WOOD_LIGHT); m.prismL(w + .3f, y, -.3f, w + .3f, y, dd + .3f, .05f, .05f, 4, Look.WOOD_LIGHT);
            }
        }
        ghosts.add(() -> {
            GHOST.vc = m.vc; GHOST.vt = m.vt; GHOST.m = m.m;
            GHOST.reset(); GHOST.frame(x0, gy, z0, 0, 1); GHOST.from = limit; GHOST.alpha = .22f; GHOST.tint = 0xBFE4FF; GHOST.tintAmt = .65f;
            BuildingModels.build(GHOST, SINK.reset(), d, b.level, team, t);
        });
    }

    /** Block-style buildings get a floating team gem over the door so ownership reads at a glance. */
    static void blockAccents(Mesh m, Snapshot s, float t) {
        for (Snapshot.B b : s.buildings) {
            BuildingDef d = BuildingDef.ALL.get(b.def); int[] sz = BuildingModels.size(d);
            if (sz[0] < 3) continue;
            float gy = RtsCamera.ground(b.x, b.z), y = gy + measure(d, b.level)[1] * Math.min(1, b.progress) + 1.2f + (float) Math.sin(t * 2 + b.id) * .15f;
            m.reset(); m.glow = true; m.ow = .03f;
            m.ballW(b.x, y, b.z, .22f, .32f, .22f, 8, 6, Look.lighter(Look.team(b.team, s.myTeam), .2f), (float) Math.cos(t), (float) Math.sin(t));
            m.glow = false;
        }
    }

    static void placementPreview(Mesh m, Snapshot s, float t, List<Runnable> ghosts) {
        BuildingDef d = RtsScreen.placing; int[] sz = BuildingModels.size(d);
        float x0 = RtsScreen.ghost[0] - sz[0] / 2f, z0 = RtsScreen.ghost[1] - sz[1] / 2f, gy = RtsCamera.ground(RtsScreen.ghost[0], RtsScreen.ghost[1]);
        boolean ok = RtsScreen.ghostValid;
        ghosts.add(() -> {
            GHOST.vc = m.vc; GHOST.vt = m.vt; GHOST.m = m.m;
            GHOST.reset(); GHOST.frame(x0, gy, z0, 0, 1); GHOST.alpha = .45f; GHOST.tint = ok ? 0x7CFF8C : 0xFF6A5A; GHOST.tintAmt = .45f;
            BuildingModels.build(GHOST, SINK.reset(), d, 1, Look.team(s.myTeam, s.myTeam), t);
        });
    }

    /** Fallen buildings sink in a cloud of dust, then leave rubble for a while. */
    static void ruins(Mesh m, Snapshot s, float t) {
        long now = System.currentTimeMillis();
        ruins.removeIf(r -> now - r.at > 45_000);
        for (Ruin r : ruins) {
            BuildingDef d = BuildingDef.ALL.get(r.def); int[] sz = BuildingModels.size(d);
            float gy = RtsCamera.ground(r.x, r.z), x0 = r.x - sz[0] / 2f, z0 = r.z - sz[1] / 2f, age = (now - r.at) / 1000f;
            float h = measure(d, r.level)[1];
            if (age < 2.5f) {
                float k = age / 2.5f;
                m.reset(); m.frame(x0, gy - h * k * k * .9f, z0, 0, 1); m.tint = 0x5A5048; m.tintAmt = .25f + .4f * k;
                BuildingModels.build(m, SINK.reset(), d, r.level, Look.team(r.team, s.myTeam), t);
                m.reset(); m.alpha = .5f * (1 - k);
                for (int i = 0; i < 6; i++) {
                    double ang = i * 1.05 + r.id; float rr = (sz[0] * .5f + 1) * (.6f + k);
                    float px = r.x + (float) Math.cos(ang) * rr, pz = r.z + (float) Math.sin(ang) * rr, br = .6f + 1.4f * k;
                    m.ballW(px, gy + .3f + k * 1.5f, pz, br, br * .7f, br, 6, 3, 0xBFB2A0, 1, 0);
                }
                m.alpha = 1;
            }
            m.reset(); m.frame(x0, gy, z0, 0, 1); m.ground(gy, 1f, .25f);
            m.alpha = age > 40 ? Math.max(0, 1 - (age - 40) / 5) : 1;
            int n = Math.max(3, sz[0] * sz[1] / 5);
            for (int i = 0; i < n; i++) {
                float u = (float) Math.abs(Math.sin(r.id * 12.9898 + i * 78.233)) , v = (float) Math.abs(Math.sin(r.id * 4.1414 + i * 17.17));
                float rr = .25f + .35f * (float) Math.abs(Math.sin(i * 3.3 + r.id));
                m.ball(u * sz[0], rr * .3f, v * sz[1], rr * 1.2f, rr * .7f, rr, 5, 3, i % 3 == 0 ? Look.WOOD_DARK : i % 3 == 1 ? Look.STONE_DARK : Look.STONE);
            }
            m.alpha = 1;
        }
    }

    // ------------------------------------------------------------------ world props

    /** Ore outcrops on the metal spots that aren't built on yet. */
    static void spots(Mesh m, Snapshot s) {
        float[] spots = ClientMatch.metalSpots;
        outer:
        for (int i = 0; i + 1 < spots.length; i += 2) {
            float x = spots[i], z = spots[i + 1];
            for (Snapshot.B b : s.buildings) if (Math.abs(b.x - x) < 1.6f && Math.abs(b.z - z) < 1.6f) continue outer;
            float gy = RtsCamera.ground(x, z);
            m.reset(); m.frame(x, gy, z, (x * 3 + z) % 6.28f, 1); m.ground(gy, .8f, .2f); m.ow = .03f;
            BuildingModels.rocks(m, 0, 0, 1.3f, 4, Look.STONE_DARK, (int) (x * 7 + z));
            BuildingModels.crystals(m, .1f, .05f, .55f, 5, Look.ORE);
        }
    }

    static void projectiles(Mesh m, Snapshot s, float a) {
        m.reset();
        for (float[] pr : s.projectiles) {
            float x = pr[0] + pr[3] * a / 20f, y = pr[1] + pr[4] * a / 20f, z = pr[2] + pr[5] * a / 20f;
            float vl = (float) Math.sqrt(pr[3] * pr[3] + pr[4] * pr[4] + pr[5] * pr[5]);
            if (vl < 1e-3f) continue;
            float dx = pr[3] / vl, dy = pr[4] / vl, dz = pr[5] / vl;
            m.prismW(x - dx * .7f, y - dy * .7f, z - dz * .7f, x, y, z, .025f, .025f, 4, Look.WOOD_LIGHT);
            m.prismW(x, y, z, x + dx * .14f, y + dy * .14f, z + dz * .14f, .045f, 0, 4, Look.IRON_DARK);
            m.prismW(x - dx * .7f, y - dy * .7f, z - dz * .7f, x - dx * .5f, y - dy * .5f, z - dz * .5f, .07f, .03f, 4, Look.CLOTH, 0, 1, 0, .15f);
        }
    }

    // ------------------------------------------------------------------ units

    static void units(Mesh m, Snapshot s, float a, float t, Frustum fr, float camX, float camY, float camZ) {
        UnitModels.Ctx c = CTX; c.t = t;
        for (Snapshot.U u : s.units) {
            float[] xz = ClientMatch.pos(u);
            float gy = RtsCamera.ground(xz[0], xz[1]);
            UnitDef d = UnitDef.ALL.get(Math.max(0, u.def));
            float k = (float) d.scale();
            if (fr != null && !fr.isVisible(new AABB(xz[0] - 2 * k - 1, gy - 2, xz[1] - 2 * k - 1, xz[0] + 2 * k + 1, gy + 3 * k + 2, xz[1] + 2 * k + 1))) continue;
            float[] pts = parts(u, a);
            float ddx = xz[0] - camX, ddy = gy - camY, ddz = xz[1] - camZ, dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            c.near = dist < 32; boolean mid = dist < 75;
            c.sides = c.near ? 10 : mid ? 6 : 4; c.headSl = c.near ? 14 : mid ? 9 : 6; c.headSt = c.near ? 9 : mid ? 6 : 4;
            c.p = pts; c.d = d; c.team = u.team; c.scale = k; c.key = d.tabsKey(); c.faction = UnitModels.faction(d.tabsKey());
            int team = Look.team(u.team, s.myTeam);
            int skin = Look.mix(Look.SKIN, Look.SKIN_DARK, (u.id * 37 % 10) / 14f);
            c.shirt = team; c.trousers = Look.mix(Look.darker(team, .35f), Look.LEATHER, .45f); c.skin = skin;
            if (!u.alive) { c.shirt = Look.darker(Look.grey(c.shirt, .6f), .15f); c.trousers = Look.darker(Look.grey(c.trousers, .6f), .15f); c.skin = Look.darker(Look.grey(skin, .5f), .1f); }
            // contact shadow
            m.reset(); m.alpha = u.alive ? .3f : .18f;
            float sr = ("humanoid".equals(d.body()) || "large".equals(d.body()) ? .42f : .75f) * k * (d.body().contains("large") || d.body().contains("quadruped") ? 1.8f : 1f);
            float sox = -Look.SUN_X * .25f * k, soz = -Look.SUN_Z * .25f * k;
            boolean fly = d.body().startsWith("flyer");
            m.frame(xz[0] + (fly ? 0 : sox), gy, xz[1] + (fly ? 0 : soz), 0, 1);
            m.disc(0, .06f, 0, fly ? sr * .8f : sr, 10, SHADOW);
            m.reset(); m.ground(gy, .5f * k, .18f); m.ow = c.near ? .022f * k : mid ? .035f : 0;
            if (pts.length / 3 == Rig.HUMANOID_PARTICLES) UnitModels.humanoid(m, c);
            else UnitModels.hull(m, c, gy, u.yaw, u.walkPhase, u.walkAmount);
            if (u.knocked && u.alive && c.near) {   // dizzy stars
                float hx = pts.length / 3 == Rig.HUMANOID_PARTICLES ? pts[Rig.HEAD * 3] : xz[0], hz = pts.length / 3 == Rig.HUMANOID_PARTICLES ? pts[Rig.HEAD * 3 + 2] : xz[1];
                float hy = (pts.length / 3 == Rig.HUMANOID_PARTICLES ? pts[Rig.HEAD * 3 + 1] : gy + 1) + .45f * k;
                for (int i = 0; i < 3; i++) {
                    double ang = t * 4 + i * 2.09;
                    m.ballW(hx + (float) Math.cos(ang) * .35f * k, hy, hz + (float) Math.sin(ang) * .35f * k, .06f, .06f, .06f, 4, 2, 0xFFE45A, 1, 0);
                }
            }
        }
    }

    /** The unit's particle positions this frame: interpolated from the server, or posed locally for far units. */
    static float[] parts(Snapshot.U u, float a) {
        float[] pts = u.parts;
        Snapshot.U p = ClientMatch.prevById.get(u.id);
        if (pts != null && p != null && p.parts != null && p.parts.length == pts.length) {
            float[] lerp = new float[pts.length];
            for (int i = 0; i < pts.length; i++) lerp[i] = p.parts[i] + (pts[i] - p.parts[i]) * a;
            return lerp;
        }
        if (pts != null) return pts;
        float[] xz = ClientMatch.pos(u);
        Ragdoll body = ClientMatch.poseBody(u);
        body.pose(xz[0], RtsCamera.ground(xz[0], xz[1]), xz[1], u.yaw, u.walkPhase, u.walkAmount, u.attack);
        float[] out = new float[body.rig.n * 3];
        for (int i = 0; i < body.rig.n; i++) { out[i * 3] = body.tx[i]; out[i * 3 + 1] = body.ty[i]; out[i * 3 + 2] = body.tz[i]; }
        return out;
    }

    // ------------------------------------------------------------------ overlays (lines)

    static void overlays(VertexConsumer vc, PoseStack.Pose pose, Snapshot s) {
        float[] spots = ClientMatch.metalSpots;
        boolean placingExtractor = RtsScreen.placing != null && RtsScreen.placing.id().endsWith("metal_extractor");
        if (placingExtractor || RtsCamera.strategic())
            for (int i = 0; i + 1 < spots.length; i += 2) {
                float x = spots[i], z = spots[i + 1], y = RtsCamera.ground(x, z) + .08f;
                circle(vc, pose, x, y, z, 1.6f, .75f, .85f, .95f);
            }
        for (Snapshot.U u : s.units) {
            if (!u.alive) continue;
            boolean sel = RtsScreen.selected.contains(u.id);
            float[] xz = ClientMatch.pos(u); float gy = RtsCamera.ground(xz[0], xz[1]);
            UnitDef d = UnitDef.ALL.get(Math.max(0, u.def)); float k = (float) d.scale();
            if (sel) circle(vc, pose, xz[0], gy + .07f, xz[1], .6f * k, .3f, 1f, .4f);
        }
        for (Snapshot.B b : s.buildings) {
            BuildingDef d = BuildingDef.ALL.get(b.def); int[] sz = BuildingModels.size(d);
            float hw = sz[0] / 2f, hh = sz[1] / 2f, y = RtsCamera.ground(b.x, b.z) + .1f;
            boolean sel = b.id == RtsScreen.selectedBuilding, mine = b.team == s.myTeam;
            if (sel) rect(vc, pose, b.x, y, b.z, hw + .35f, hh + .35f, 1, 1, 1);
            if (sel && mine && d.kind().equals("factory")) {
                float ry = RtsCamera.ground(b.rallyX, b.rallyZ) + .1f;
                seg(vc, pose, b.x, y + .5f, b.z, b.rallyX, ry, b.rallyZ, .4f, 1f, .5f); circle(vc, pose, b.rallyX, ry, b.rallyZ, .5f, .4f, 1f, .5f);
            }
            if (b.hp < .999f || sel) {
                float top = y + measure(d, b.level)[1] + .6f, l = hw;
                seg(vc, pose, b.x - l, top, b.z, b.x + l, top, b.z, .15f, .15f, .15f);
                seg(vc, pose, b.x - l, top + .02f, b.z, b.x - l + 2 * l * b.hp, top + .02f, b.z, mine ? .3f : 1f, mine ? 1f : .3f, .3f);
            }
        }
        long age = System.currentTimeMillis() - pingAt;
        if (age < 600) {
            float t = age / 600f;
            circle(vc, pose, pingX, pingY + .1f, pingZ, .3f + 1.2f * t, pingAttack ? 1f : .3f, pingAttack ? .3f : 1f, .3f);
        }
    }

    static void rect(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float hw, float hh, float r, float g, float b) {
        seg(vc, pose, x - hw, y, z - hh, x + hw, y, z - hh, r, g, b); seg(vc, pose, x + hw, y, z - hh, x + hw, y, z + hh, r, g, b);
        seg(vc, pose, x + hw, y, z + hh, x - hw, y, z + hh, r, g, b); seg(vc, pose, x - hw, y, z + hh, x - hw, y, z - hh, r, g, b);
    }

    static void circle(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float rad, float r, float g, float b) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            seg(vc, pose, x + (float) Math.cos(a0) * rad, y, z + (float) Math.sin(a0) * rad, x + (float) Math.cos(a1) * rad, y, z + (float) Math.sin(a1) * rad, r, g, b);
        }
    }

    static void seg(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1, l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) return;
        nx /= l; ny /= l; nz /= l;
        vc.vertex(pose.pose(), x1, y1, z1).color(r, g, b, 1f).normal(pose.normal(), nx, ny, nz).endVertex();
        vc.vertex(pose.pose(), x2, y2, z2).color(r, g, b, 1f).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    /** Remembers buildings that vanished between snapshots so they can collapse. */
    static void noteRuins(Snapshot prev, Snapshot cur) {
        if (prev == null) return;
        long t = System.currentTimeMillis();
        ruins.removeIf(r -> t - r.at > 45_000);
        java.util.Set<Integer> standing = new java.util.HashSet<>();
        for (Snapshot.B b : cur.buildings) standing.add(b.id);
        for (Snapshot.B b : prev.buildings)
            if (!standing.contains(b.id) && b.progress > .3f) ruins.add(new Ruin(b.def, b.level, b.team, b.x, b.z, t, b.id));
    }

    static void clearCache() {
        for (VertexBuffer v : cache.values()) v.close();
        cache.clear(); ruins.clear();
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        ClientMatch.clear();
        ClientLobby.clear();
        clearCache();
        if (RtsCamera.active) RtsCamera.exit();
    }
}
