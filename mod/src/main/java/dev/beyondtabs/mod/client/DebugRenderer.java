package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beyondtabs.engine.Ragdoll;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Prototype renderer: draws every unit's ragdoll as a skeleton (bones as lines) so the physics can be checked against real
 * Minecraft terrain. Red = Ancient World, blue = Kingdoms, yellow = knocked down, grey = dead. TABS meshes replace this.
 */
@Mod.EventBusSubscriber(modid = BeyondTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DebugRenderer {
    static final int[] HUMAN_BONES = {0, 1, 1, 2, 2, 3, 1, 4, 4, 5, 5, 6, 1, 7, 7, 8, 8, 9, 0, 10, 10, 11, 11, 12, 0, 13, 13, 14, 14, 15, 4, 7, 10, 13};

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Snapshot s = ClientMatch.cur;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null) return;
        float a = Math.min(1f, (System.nanoTime() - ClientMatch.curAtNanos) / 50_000_000f);
        PoseStack ps = e.getPoseStack(); Vec3 cam = e.getCamera().getPosition();
        ps.pushPose(); ps.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        for (Snapshot.U u : s.units) {
            float r, g, b;
            if (!u.alive) { r = g = b = .45f; } else if (u.knocked) { r = 1; g = .85f; b = .1f; }
            else if (u.team == 0) { r = .9f; g = .25f; b = .2f; } else { r = .25f; g = .45f; b = 1f; }
            Snapshot.U p = ClientMatch.prevById.get(u.id);
            float[] pts = u.parts;
            if (pts != null && p != null && p.parts != null && p.parts.length == pts.length) {
                float[] lerp = new float[pts.length];
                for (int i = 0; i < pts.length; i++) lerp[i] = p.parts[i] + (pts[i] - p.parts[i]) * a;
                pts = lerp;
            }
            if (pts == null) {   // far unit: pose it locally from the sent animation state
                float x = p != null ? p.x + (u.x - p.x) * a : u.x, z = p != null ? p.z + (u.z - p.z) * a : u.z;
                Ragdoll body = ClientMatch.poseBody(u);
                float gy = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                body.pose(x, gy, z, u.yaw, u.walkPhase, u.walkAmount, u.attack);
                pts = new float[body.rig.n * 3];
                for (int i = 0; i < body.rig.n; i++) { pts[i * 3] = body.tx[i]; pts[i * 3 + 1] = body.ty[i]; pts[i * 3 + 2] = body.tz[i]; }
            }
            int n = pts.length / 3;
            if (n == Rig.HUMANOID_PARTICLES) {
                for (int i = 0; i < HUMAN_BONES.length; i += 2) line(vc, pose, pts, HUMAN_BONES[i], HUMAN_BONES[i + 1], r, g, b);
                // head as a small cross
                float hx = pts[Rig.HEAD * 3], hy = pts[Rig.HEAD * 3 + 1], hz = pts[Rig.HEAD * 3 + 2], k = .15f;
                seg(vc, pose, hx - k, hy, hz, hx + k, hy, hz, r, g, b); seg(vc, pose, hx, hy - k, hz, hx, hy + k, hz, r, g, b); seg(vc, pose, hx, hy, hz - k, hx, hy, hz + k, r, g, b);
            } else for (int i = 0; i + 1 < n; i++) line(vc, pose, pts, i, i + 1, r, g, b);
            if (u.alive && u.hp < .999f) {   // health bar
                float hx = pts[0], hy = pts[1] + 1.3f, hz = pts[2];
                seg(vc, pose, hx - .4f, hy, hz, hx - .4f + .8f * u.hp, hy, hz, .2f, 1f, .2f);
            }
        }
        for (float[] pr : s.projectiles) {
            float x = pr[0] + pr[3] * a / 20f, y = pr[1] + pr[4] * a / 20f, z = pr[2] + pr[5] * a / 20f;
            float l = .04f;
            seg(vc, pose, x, y, z, x - pr[3] * l, y - pr[4] * l, z - pr[5] * l, 1f, 1f, 1f);
        }
        buf.endBatch(RenderType.lines());
        ps.popPose();
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
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) { ClientMatch.clear(); }
}
