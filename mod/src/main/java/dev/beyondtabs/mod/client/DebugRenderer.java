package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beyondtabs.engine.Ragdoll;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * In-world drawing: units as ragdoll skeletons (until the TABS meshes replace them), projectiles, and the RTS overlays:
 * metal spots, selection rings, building outlines, the placement ghost, rally lines and order pings.
 * Red = enemy, blue = you (in a match), yellow = knocked down, grey = dead.
 */
@Mod.EventBusSubscriber(modid = BeyondTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DebugRenderer {
    static final int[] HUMAN_BONES = {0, 1, 1, 2, 2, 3, 1, 4, 4, 5, 5, 6, 1, 7, 7, 8, 8, 9, 0, 10, 10, 11, 11, 12, 0, 13, 13, 14, 14, 15, 4, 7, 10, 13};
    static float pingX, pingY, pingZ; static long pingAt; static boolean pingAttack;

    static void pingAt(float x, float y, float z, boolean attack) { pingX = x; pingY = y; pingZ = z; pingAt = System.currentTimeMillis(); pingAttack = attack; }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Snapshot s = ClientMatch.cur;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null) return;
        float a = ClientMatch.alpha();
        PoseStack ps = e.getPoseStack(); Vec3 cam = e.getCamera().getPosition();
        ps.pushPose(); ps.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        boolean strategic = RtsCamera.active && RtsCamera.strategic();
        boolean inMatch = s.myTeam >= 0;
        for (Snapshot.U u : s.units) {
            float r, g, b;
            if (!u.alive) { r = g = b = .45f; } else if (u.knocked) { r = 1; g = .85f; b = .1f; }
            else if (inMatch ? u.team != s.myTeam : u.team == 0) { r = .9f; g = .25f; b = .2f; } else { r = .25f; g = .45f; b = 1f; }
            if (strategic) continue;   // icons are drawn by the HUD when zoomed out
            Snapshot.U p = ClientMatch.prevById.get(u.id);
            float[] pts = u.parts;
            if (pts != null && p != null && p.parts != null && p.parts.length == pts.length) {
                float[] lerp = new float[pts.length];
                for (int i = 0; i < pts.length; i++) lerp[i] = p.parts[i] + (pts[i] - p.parts[i]) * a;
                pts = lerp;
            }
            if (pts == null) {   // far unit: pose it locally from the sent animation state
                float[] xz = ClientMatch.pos(u);
                Ragdoll body = ClientMatch.poseBody(u);
                body.pose(xz[0], RtsCamera.ground(xz[0], xz[1]), xz[1], u.yaw, u.walkPhase, u.walkAmount, u.attack);
                pts = new float[body.rig.n * 3];
                for (int i = 0; i < body.rig.n; i++) { pts[i * 3] = body.tx[i]; pts[i * 3 + 1] = body.ty[i]; pts[i * 3 + 2] = body.tz[i]; }
            }
            int n = pts.length / 3;
            if (n == Rig.HUMANOID_PARTICLES) {
                for (int i = 0; i < HUMAN_BONES.length; i += 2) line(vc, pose, pts, HUMAN_BONES[i], HUMAN_BONES[i + 1], r, g, b);
                float hx = pts[Rig.HEAD * 3], hy = pts[Rig.HEAD * 3 + 1], hz = pts[Rig.HEAD * 3 + 2], k = .15f;
                seg(vc, pose, hx - k, hy, hz, hx + k, hy, hz, r, g, b); seg(vc, pose, hx, hy - k, hz, hx, hy + k, hz, r, g, b); seg(vc, pose, hx, hy, hz - k, hx, hy, hz + k, r, g, b);
            } else for (int i = 0; i + 1 < n; i++) line(vc, pose, pts, i, i + 1, r, g, b);
            if (u.alive && u.hp < .999f && !RtsCamera.active) {
                float hx = pts[0], hy = pts[1] + 1.3f, hz = pts[2];
                seg(vc, pose, hx - .4f, hy, hz, hx - .4f + .8f * u.hp, hy, hz, .2f, 1f, .2f);
            }
            if (RtsCamera.active && RtsScreen.selected.contains(u.id) && u.alive) {
                float[] xz = ClientMatch.pos(u);
                circle(vc, pose, xz[0], RtsCamera.ground(xz[0], xz[1]) + .05f, xz[1], .6f, .3f, 1f, .4f);
            }
        }
        if (!strategic) for (float[] pr : s.projectiles) {
            float x = pr[0] + pr[3] * a / 20f, y = pr[1] + pr[4] * a / 20f, z = pr[2] + pr[5] * a / 20f, l = .04f;
            seg(vc, pose, x, y, z, x - pr[3] * l, y - pr[4] * l, z - pr[5] * l, 1f, 1f, 1f);
        }
        if (RtsCamera.active) overlays(vc, pose, s);
        buf.endBatch(RenderType.lines());
        ps.popPose();
    }

    static void overlays(VertexConsumer vc, PoseStack.Pose pose, Snapshot s) {
        float[] spots = ClientMatch.metalSpots;
        for (int i = 0; i + 1 < spots.length; i += 2) {
            float x = spots[i], z = spots[i + 1], y = RtsCamera.ground(x, z) + .08f;
            circle(vc, pose, x, y, z, 1.4f, .75f, .8f, .9f);
            seg(vc, pose, x - .5f, y, z, x + .5f, y, z, .75f, .8f, .9f); seg(vc, pose, x, y, z - .5f, x, y, z + .5f, .75f, .8f, .9f);
        }
        for (Snapshot.B b : s.buildings) {
            BuildingDef d = BuildingDef.ALL.get(b.def); String[] f = d.footprint().split("x");
            float hw = Integer.parseInt(f[0]) / 2f, hh = Integer.parseInt(f[1]) / 2f, y = RtsCamera.ground(b.x, b.z) + .06f;
            boolean sel = b.id == RtsScreen.selectedBuilding, mine = b.team == s.myTeam;
            if (sel || b.progress < 1) rect(vc, pose, b.x, y, b.z, hw + .1f, hh + .1f, sel ? 1 : .9f, sel ? 1 : .8f, sel ? 1 : .3f);
            if (sel && mine && d.kind().equals("factory")) {
                float ry = RtsCamera.ground(b.rallyX, b.rallyZ) + .1f;
                seg(vc, pose, b.x, y + .5f, b.z, b.rallyX, ry, b.rallyZ, .4f, 1f, .5f); circle(vc, pose, b.rallyX, ry, b.rallyZ, .5f, .4f, 1f, .5f);
            }
            // health bar above damaged buildings
            if (b.hp < .999f) {
                float top = y + 4, l = hw;
                seg(vc, pose, b.x - l, top, b.z, b.x - l + 2 * l * b.hp, top, b.z, mine ? .3f : 1f, mine ? 1f : .3f, .3f);
            }
        }
        if (RtsScreen.placing != null && RtsScreen.ghost != null) {
            BuildingDef d = RtsScreen.placing; String[] f = d.footprint().split("x");
            float x = RtsScreen.ghost[0], z = RtsScreen.ghost[1], y = RtsCamera.ground(x, z) + .1f;
            float r = RtsScreen.ghostValid ? .3f : 1f, g = RtsScreen.ghostValid ? 1f : .3f;
            for (int k = 0; k < 3; k++) rect(vc, pose, x, y + k, z, Integer.parseInt(f[0]) / 2f, Integer.parseInt(f[1]) / 2f, r, g, .3f);
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
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            seg(vc, pose, x + (float) Math.cos(a0) * rad, y, z + (float) Math.sin(a0) * rad, x + (float) Math.cos(a1) * rad, y, z + (float) Math.sin(a1) * rad, r, g, b);
        }
    }

    static void line(VertexConsumer vc, PoseStack.Pose pose, float[] p, int i, int j, float r, float g, float b) {
        seg(vc, pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], p[j * 3], p[j * 3 + 1], p[j * 3 + 2], r, g, b);
    }

    static void seg(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1, l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) return;
        nx /= l; ny /= l; nz /= l;
        vc.vertex(pose.pose(), x1, y1, z1).color(r, g, b, 1f).normal(pose.normal(), nx, ny, nz).endVertex();
        vc.vertex(pose.pose(), x2, y2, z2).color(r, g, b, 1f).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        ClientMatch.clear();
        if (RtsCamera.active) RtsCamera.exit();
    }
}
