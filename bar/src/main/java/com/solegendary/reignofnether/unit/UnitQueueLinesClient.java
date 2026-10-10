package com.solegendary.reignofnether.unit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.keybinds.Keybindings;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws BAR's command queue: for every selected unit (and, while Shift is held, every own unit) a chain of lines
 * from the unit through each order it has queued, with a small diamond at each waypoint. Colours follow BAR:
 * move green, attack red, build/repair yellow, reclaim cyan. The data comes from the server (UnitQueueSync) because
 * the shift-queue only exists there.
 *
 * Runs every frame, so it allocates nothing: positions are read straight out of the synced int arrays and the
 * vertices are written directly, and the total number of segments is capped for huge selections.
 */
public class UnitQueueLinesClient {

    static final Minecraft MC = Minecraft.getInstance();
    static final int MAX_SEGMENTS = 600;   // lines + marker strokes per frame, across all units
    static final float LIFT = 0.08f;       // just above the ground so the lines don't z-fight the blocks
    static final float MARKER = 0.3f;

    static final float[][] COLOURS = {
        { 0.25f, 1.0f, 0.25f },   // move
        { 1.0f, 0.2f, 0.2f },     // attack
        { 1.0f, 0.9f, 0.2f },     // build / repair
        { 0.2f, 0.95f, 1.0f },    // reclaim
    };

    // unit id -> [type, x, y, z, entityId] * n, replaced wholesale by each packet
    private static Int2ObjectOpenHashMap<int[]> queues = new Int2ObjectOpenHashMap<>();

    private static int segmentsLeft;

    public static void apply(int[] data) {
        Int2ObjectOpenHashMap<int[]> next = new Int2ObjectOpenHashMap<>();
        int i = 0;
        while (i + 1 < data.length) {
            int unitId = data[i++];
            int n = data[i++];
            int len = n * UnitQueueSync.INTS_PER_ENTRY;
            if (n < 0 || i + len > data.length)
                break;   // malformed; keep what parsed
            int[] entries = new int[len];
            System.arraycopy(data, i, entries, 0, len);
            i += len;
            next.put(unitId, entries);
        }
        queues = next;
    }

    public static void clear() {
        queues = new Int2ObjectOpenHashMap<>();
    }

    public static void render(PoseStack pose, float partialTick) {
        if (queues.isEmpty() || MC.level == null)
            return;
        Entity cam = MC.getCameraEntity();
        if (cam == null)
            return;
        List<LivingEntity> selected = UnitClientEvents.getSelectedUnits();
        boolean showAll = Keybindings.shiftMod.isDown();
        if (selected.isEmpty() && !showAll)
            return;

        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.getX(), -(cam.getY() + cam.getEyeHeight()), -cam.getZ());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        segmentsLeft = MAX_SEGMENTS;

        if (showAll) {
            // Shift: every own unit's queue (the server only sends our own), one pass, no per-unit lookups
            var it = queues.int2ObjectEntrySet().fastIterator();
            while (it.hasNext() && segmentsLeft > 0) {
                Int2ObjectMap.Entry<int[]> e = it.next();
                Entity ent = MC.level.getEntity(e.getIntKey());
                if (ent instanceof LivingEntity le)
                    drawUnit(vc, m, n, le, e.getValue(), 0.7f, partialTick);
            }
        } else {
            for (int s = 0; s < selected.size() && segmentsLeft > 0; s++) {
                LivingEntity le = selected.get(s);
                int[] q = queues.get(le.getId());
                if (q != null)
                    drawUnit(vc, m, n, le, q, 0.85f, partialTick);
            }
        }
        pose.popPose();
    }

    static void drawUnit(VertexConsumer vc, Matrix4f m, Matrix3f n, LivingEntity le, int[] q, float a, float pt) {
        float lx = (float) Mth.lerp(pt, le.xo, le.getX());
        float ly = (float) Mth.lerp(pt, le.yo, le.getY()) + LIFT;
        float lz = (float) Mth.lerp(pt, le.zo, le.getZ());
        for (int i = 0; i + UnitQueueSync.INTS_PER_ENTRY <= q.length && segmentsLeft > 0; i += UnitQueueSync.INTS_PER_ENTRY) {
            int type = q[i];
            float x, y, z;
            Entity target = q[i + 4] >= 0 ? MC.level.getEntity(q[i + 4]) : null;
            if (target != null) {
                x = (float) target.getX();
                y = (float) target.getY() + LIFT;
                z = (float) target.getZ();
            } else if (q[i + 4] >= 0 && q[i + 1] == 0 && q[i + 2] == 0 && q[i + 3] == 0) {
                continue;   // a moving target the client can't see: nothing sensible to draw
            } else {
                x = q[i + 1] + 0.5f;
                y = q[i + 2] + 1f + LIFT;
                z = q[i + 3] + 0.5f;
            }
            float[] c = COLOURS[type >= 0 && type < COLOURS.length ? type : 0];
            line(vc, m, n, lx, ly, lz, x, y, z, c[0], c[1], c[2], a);
            // waypoint diamond
            line(vc, m, n, x - MARKER, y, z, x, y, z - MARKER, c[0], c[1], c[2], a);
            line(vc, m, n, x, y, z - MARKER, x + MARKER, y, z, c[0], c[1], c[2], a);
            line(vc, m, n, x + MARKER, y, z, x, y, z + MARKER, c[0], c[1], c[2], a);
            line(vc, m, n, x, y, z + MARKER, x - MARKER, y, z, c[0], c[1], c[2], a);
            lx = x;
            ly = y;
            lz = z;
        }
    }

    static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float z1,
                     float x2, float y2, float z2, float r, float g, float b, float a) {
        segmentsLeft--;
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f)
            return;
        // the lines shader widens each segment along its normal, so the normal must be the segment's direction
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
    }
}
