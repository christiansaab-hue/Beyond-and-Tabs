package com.solegendary.reignofnether.unit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
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
 */
public class AreaCommandClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static final int MIN_RADIUS = 2;
    static final int CIRCLE_SEGMENTS = 48;

    private static BlockPos centre = null;
    private static int radius = 0;

    public static boolean isActive() {
        return centre != null;
    }

    static boolean hasSelectedWorker() {
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (le instanceof WorkerUnit && le instanceof Unit)
                return true;
        return false;
    }

    /**
     * Called from UnitClientEvents.onMouseDrag for an active right-drag. Returns true when the drag is an area
     * command (so the formation-move drag must not run).
     */
    static boolean onRightDrag() {
        if (centre == null) {
            if (!Keybindings.ctrlMod.isDown() || !hasSelectedWorker() || CursorClientEvents.getRightClickStartBp() == null)
                return false;
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
                if (le instanceof WorkerUnit && le instanceof Unit)
                    ids.add(le.getId());
            if (ids.isEmpty())
                return;
            int[] arr = new int[ids.size()];
            for (int i = 0; i < arr.length; i++)
                arr[i] = ids.get(i);
            boolean shift = Keybindings.shiftMod.isDown();
            PacketHandler.INSTANCE.sendToServer(
                new AreaCommandServerboundPacket(AreaCommands.MODE_AUTO, centre, radius, arr, shift));
            HudClientEvents.showTemporaryMessage("Area reclaim / repair (radius " + radius + ")" + (shift ? " queued" : ""));
        } finally {
            cancel();
        }
    }

    static void cancel() {
        centre = null;
        radius = 0;
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
        // cyan like the reclaim queue lines
        float px = cx + radius, pz = cz;
        for (int i = 1; i <= CIRCLE_SEGMENTS; i++) {
            double ang = Math.PI * 2 * i / CIRCLE_SEGMENTS;
            float x = cx + (float) (Math.cos(ang) * radius), z = cz + (float) (Math.sin(ang) * radius);
            UnitQueueLinesClient.line(vc, m, n, px, cy, pz, x, cy, z, 0.2f, 0.95f, 1.0f, 0.9f);
            px = x;
            pz = z;
        }
        UnitQueueLinesClient.line(vc, m, n, cx - 0.4f, cy, cz, cx + 0.4f, cy, cz, 0.2f, 0.95f, 1.0f, 0.9f);
        UnitQueueLinesClient.line(vc, m, n, cx, cy, cz - 0.4f, cx, cy, cz + 0.4f, 0.2f, 0.95f, 1.0f, 0.9f);
        pose.popPose();
    }
}
