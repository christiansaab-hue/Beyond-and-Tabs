package com.solegendary.reignofnether.unit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.AreaCommandServerboundPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * BAR's area reclaim / area repair, same feel as the area mex (PatchQuickBuildClientEvents) but with a circle:
 * with workers selected, hold Ctrl and right-drag from the centre outwards. On release every wreck inside the circle
 * is queued on the selected workers (split between them, nearest first); a circle with no wreck in it repairs every
 * damaged or unfinished own building inside instead. Hold Shift as well to append to the current queue.
 * The circle is drawn while dragging. Target selection happens on the server (AreaCommands), which knows the wrecks.
 *
 * Combat units in the selection turn the same circle into an area attack (every visible enemy unit and building
 * inside, split between them nearest first). The circle is red for fighters, cyan for workers, and both for a mixed
 * selection, where the workers reclaim / repair and the fighters attack.
 */
public class AreaCommandClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static final int MIN_RADIUS = 2;
    static final int CIRCLE_SEGMENTS = 48;

    private static BlockPos centre = null;
    private static int radius = 0;
    // what the selection holds, sampled when the drag starts (drives the circle colour and the hud message)
    private static boolean dragHasWorkers = false;
    private static boolean dragHasFighters = false;

    public static boolean isActive() {
        return centre != null;
    }

    // the server sorts the ids the same way (AreaCommands.issue): workers reclaim / repair, other attackers attack
    static boolean isWorker(LivingEntity le) {
        return le instanceof WorkerUnit && le instanceof Unit;
    }

    static boolean isFighter(LivingEntity le) {
        return !(le instanceof WorkerUnit) && le instanceof AttackerUnit && le instanceof Unit;
    }

    /**
     * Called from UnitClientEvents.onMouseDrag for an active right-drag. Returns true when the drag is an area
     * command (so the formation-move drag must not run).
     */
    static boolean onRightDrag() {
        if (centre == null) {
            if (!Keybindings.ctrlMod.isDown() || CursorClientEvents.getRightClickStartBp() == null)
                return false;
            boolean w = false, f = false;
            for (LivingEntity le : UnitClientEvents.getSelectedUnits()) {
                w |= isWorker(le);
                f |= isFighter(le);
            }
            if (!w && !f)
                return false;
            dragHasWorkers = w;
            dragHasFighters = f;
            centre = CursorClientEvents.getRightClickStartBp();
        }
        BlockPos cur = CursorClientEvents.getPreselectedBlockPos();
        if (cur != null) {
            double dx = cur.getX() - centre.getX(), dz = cur.getZ() - centre.getZ();
            radius = Mth.clamp((int) Math.round(Math.sqrt(dx * dx + dz * dz)), MIN_RADIUS, AreaCommands.MAX_RADIUS);
        }
        return true;
    }

    /** Called on right-button release while active: sends the command and ends the drag. */
    static void onRightRelease() {
        if (centre == null)
            return;
        try {
            if (MC.player == null)
                return;
            ArrayList<Integer> ids = new ArrayList<>();
            for (LivingEntity le : UnitClientEvents.getSelectedUnits())
                if (isWorker(le) || isFighter(le))
                    ids.add(le.getId());
            if (ids.isEmpty())
                return;
            int[] arr = new int[ids.size()];
            for (int i = 0; i < arr.length; i++)
                arr[i] = ids.get(i);
            boolean shift = Keybindings.shiftMod.isDown();
            PacketHandler.INSTANCE.sendToServer(
                new AreaCommandServerboundPacket(AreaCommands.MODE_AUTO, centre, radius, arr, shift));
            String what = dragHasWorkers && dragHasFighters ? "Area attack + reclaim / repair"
                : dragHasFighters ? "Area attack" : "Area reclaim / repair";
            HudClientEvents.showTemporaryMessage(what + " (radius " + radius + ")" + (shift ? " queued" : ""));
        } finally {
            cancel();
        }
    }

    static void cancel() {
        centre = null;
        radius = 0;
        dragHasWorkers = false;
        dragHasFighters = false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || centre == null)
            return;
        if (!OrthoviewClientEvents.isEnabled() || !CursorClientEvents.isRightDragActive()) {
            cancel();   // released outside the screen, or the view changed: never leave a stale circle
            return;
        }
        Entity cam = MC.getCameraEntity();
        if (cam == null)
            return;
        PoseStack pose = evt.getPoseStack();
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.getX(), -(cam.getY() + cam.getEyeHeight()), -cam.getZ());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float cx = centre.getX() + 0.5f, cy = centre.getY() + 1.1f, cz = centre.getZ() + 0.5f;
        // red for an attack circle, cyan (like the reclaim queue lines) for workers; a mixed selection draws both
        if (dragHasFighters)
            drawCircle(vc, m, n, cx, cy, cz, radius, 1.0f, 0.25f, 0.2f);
        if (dragHasWorkers)
            drawCircle(vc, m, n, cx, cy, cz, dragHasFighters ? radius - 0.35f : radius, 0.2f, 0.95f, 1.0f);
        float r = dragHasFighters ? 1.0f : 0.2f, g = dragHasFighters ? 0.25f : 0.95f, b = dragHasFighters ? 0.2f : 1.0f;
        UnitQueueLinesClient.line(vc, m, n, cx - 0.4f, cy, cz, cx + 0.4f, cy, cz, r, g, b, 0.9f);
        UnitQueueLinesClient.line(vc, m, n, cx, cy, cz - 0.4f, cx, cy, cz + 0.4f, r, g, b, 0.9f);
        pose.popPose();
    }

    private static void drawCircle(VertexConsumer vc, Matrix4f m, Matrix3f n, float cx, float cy, float cz, float rad,
                                   float r, float g, float b) {
        float px = cx + rad, pz = cz;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double ang = Math.PI * 2 * i / CIRCLE_SEGMENTS;
            float x = cx + (float) (Math.cos(ang) * rad), z = cz + (float) (Math.sin(ang) * rad);
            UnitQueueLinesClient.line(vc, m, n, px, cy, pz, x, cy, z, r, g, b, 0.9f);
            px = x;
            pz = z;
        }
    }
}
