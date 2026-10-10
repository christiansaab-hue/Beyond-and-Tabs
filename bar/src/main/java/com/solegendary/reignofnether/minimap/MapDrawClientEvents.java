package com.solegendary.reignofnether.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.MyEditBox;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

/**
 * BAR map drawing for allies:
 * <ul>
 *   <li><b>Alt + left-drag on the minimap</b> draws a short line. In the world it is <b>Ctrl + Alt + left-drag</b>,
 *   because plain Alt + left-drag already grabs and pans the map there (OrthoviewClientEvents) and Alt + right-drag
 *   rotates the camera.</li>
 *   <li><b>Alt + double-click</b> (minimap or world) drops a labelled ping: a small text box opens at the cursor,
 *   Enter places the label (max 24 characters), Esc or clicking away cancels.</li>
 * </ul>
 * Drawings are sent to the server, which rate-limits them (MapDrawRules) and relays them to the sender and their
 * allies; they show on the minimap and in the world for 20 seconds. The player's own line is previewed locally
 * while it is being drawn.
 */
public class MapDrawClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    static final int DOUBLE_CLICK_MS = 350;
    static final int DOUBLE_CLICK_PX = 4;
    static final int MAX_DRAWINGS = 256;   // across all allies; the oldest go first if a team floods the map
    static final float MINIMAP_SAMPLE_BLOCKS = 3f;
    static final float WORLD_SAMPLE_BLOCKS = 1.5f;
    static final float LIFT = 0.25f;

    static final class Drawing {
        final byte kind;
        final int[] xs, ys, zs;
        final String label;
        final int rgb;
        int ticksLeft = MapDrawRules.LIFETIME_TICKS;

        Drawing(byte kind, int[] xs, int[] ys, int[] zs, String label, int rgb) {
            this.kind = kind;
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
            this.label = label;
            this.rgb = rgb;
        }

        float alpha() { return Math.min(1f, ticksLeft / 40f); }   // fade over the last 2 s
    }

    private static final List<Drawing> drawings = new ArrayList<>();
    // the client's own copy of the limiter, so the player gets told instead of the server silently dropping lines
    private static final MapDrawRules clientLimiter = new MapDrawRules();

    // stroke being drawn right now (fixed arrays: no allocation while dragging)
    private static boolean drawing = false;
    private static boolean drawingOnMinimap = false;
    private static final int[] curXs = new int[MapDrawRules.MAX_POINTS];
    private static final int[] curZs = new int[MapDrawRules.MAX_POINTS];
    private static int curN = 0;

    // double-click tracking
    private static long lastAltClickMs = 0;
    private static double lastAltClickX = -100, lastAltClickY = -100;

    // label text box
    private static MyEditBox labelBox = null;
    private static int labelX, labelZ;
    private static boolean closeLabelBox = false;

    /** While true the camera's Alt + left-drag map grab must stay off (we own the left button). */
    public static boolean isDrawing() {
        return drawing;
    }

    // ------------------------------------------------------------------ network in

    public static void receive(String sender, byte kind, int[] xs, int[] zs, String label) {
        if (MC.level == null || xs.length == 0 || xs.length != zs.length)
            return;
        // ground heights once on arrival, so the world pass never queries the heightmap per frame
        int[] ys = new int[xs.length];
        for (int i = 0; i < xs.length; i++)
            ys[i] = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, xs[i], zs[i]);
        if (drawings.size() >= MAX_DRAWINGS)
            drawings.remove(0);
        drawings.add(new Drawing(kind, xs, ys, zs, label, PlayerColors.getPlayerDisplayColorHex(sender) & 0xFFFFFF));
    }

    // ------------------------------------------------------------------ input

    private static boolean inputActive() {
        return OrthoviewClientEvents.isEnabled() && !OrthoviewClientEvents.isCameraLocked()
            && MC.screen instanceof TopdownGui && MC.level != null && MC.player != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!inputActive() || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || !Keybindings.altMod.isDown())
            return;
        if (labelBox != null && labelBox.isMouseOver(evt.getMouseX(), evt.getMouseY()))
            return;
        if (HudClientEvents.isMouseOverAnyButton())
            return;
        double mx = evt.getMouseX(), my = evt.getMouseY();
        boolean onMinimap = MinimapClientEvents.isPointInsideMinimap(mx, my);
        if (!onMinimap && HudClientEvents.isMouseOverAnyButtonOrHud())
            return;

        long now = System.currentTimeMillis();
        boolean doubleClick = now - lastAltClickMs < DOUBLE_CLICK_MS
            && Math.abs(mx - lastAltClickX) <= DOUBLE_CLICK_PX && Math.abs(my - lastAltClickY) <= DOUBLE_CLICK_PX;
        if (doubleClick) {
            lastAltClickMs = 0;
            drawing = false;
            BlockPos bp = onMinimap ? MinimapClientEvents.minimapScreenToWorld((float) mx, (float) my)
                : CursorClientEvents.getPreselectedBlockPos();
            if (bp != null)
                openLabelBox(bp.getX(), bp.getZ(), (int) mx, (int) my);
            evt.setCanceled(true);
            return;
        }
        lastAltClickMs = now;
        lastAltClickX = mx;
        lastAltClickY = my;

        // in the world plain Alt + left is the camera's map grab, so a world line needs Ctrl as well
        if (onMinimap || Keybindings.ctrlMod.isDown()) {
            BlockPos bp = onMinimap ? MinimapClientEvents.minimapScreenToWorld((float) mx, (float) my)
                : CursorClientEvents.getPreselectedBlockPos();
            if (bp == null)
                return;
            drawing = true;
            drawingOnMinimap = onMinimap;
            curN = 0;
            addPoint(bp.getX(), bp.getZ(), 0);
            evt.setCanceled(true);   // no minimap teleport / marker / selection for this click
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (!drawing || evt.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        BlockPos bp = drawingOnMinimap
            ? MinimapClientEvents.minimapScreenToWorld((float) evt.getMouseX(), (float) evt.getMouseY())
            : CursorClientEvents.getPreselectedBlockPos();
        if (bp != null)
            addPoint(bp.getX(), bp.getZ(), drawingOnMinimap ? MINIMAP_SAMPLE_BLOCKS : WORLD_SAMPLE_BLOCKS);
        evt.setCanceled(true);   // otherwise dragging on the minimap also drags the camera there
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre evt) {
        if (!drawing || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        drawing = false;
        if (curN < 2 || MC.player == null)
            return;   // just a click (maybe the first half of a double-click)
        if (!clientLimiter.tryConsume(MC.player.getUUID(), System.currentTimeMillis())) {
            HudClientEvents.showTempMessageI18n("hud.map.reignofnether.draw_rate_limited");
            return;
        }
        int[] xs = new int[curN], zs = new int[curN];
        System.arraycopy(curXs, 0, xs, 0, curN);
        System.arraycopy(curZs, 0, zs, 0, curN);
        MapDrawServerboundPacket.sendStroke(xs, zs);
    }

    private static void addPoint(int x, int z, float minStep) {
        if (curN >= MapDrawRules.MAX_POINTS)
            return;   // a short line: once the points run out, the rest of the drag is ignored
        if (curN > 0) {
            float dx = x - curXs[curN - 1], dz = z - curZs[curN - 1];
            if (dx * dx + dz * dz < minStep * minStep)
                return;
            if (dx * dx + dz * dz > MapDrawRules.MAX_SEGMENT_BLOCKS * MapDrawRules.MAX_SEGMENT_BLOCKS)
                return;   // cursor jumped off the map/terrain: the server would refuse the stroke
        }
        curXs[curN] = x;
        curZs[curN] = z;
        curN++;
    }

    // ------------------------------------------------------------------ label box

    private static void openLabelBox(int x, int z, int screenX, int screenY) {
        closeLabelNow();
        labelX = x;
        labelZ = z;
        int sw = MC.getWindow().getGuiScaledWidth(), sh = MC.getWindow().getGuiScaledHeight();
        int w = 120, h = 14;
        int bx = Mth.clamp(screenX - w / 2, 2, sw - w - 2);
        int by = Mth.clamp(screenY - h - 8, 2, sh - h - 2);
        labelBox = new MyEditBox.Builder(bx, by, w, h)
            .maxLength(MapDrawRules.MAX_LABEL_CHARS)
            .tooltipLines(List.of(fcs(I18n.get("hud.map.reignofnether.label_hint"))))
            // clicking elsewhere defocuses the box: treat that as cancel. Called while TextInputClientEvents is
            // iterating its boxes, so only flag the removal here and do it on the next tick
            .onDefocus(v -> closeLabelBox = true)
            .build();
        labelBox.setFocused(true);
        TextInputClientEvents.registerEditBox(labelBox);
    }

    private static void closeLabelNow() {
        if (labelBox != null) {
            labelBox.setFocused(false);   // unfocused first so deregistering doesn't fire onDefocus
            TextInputClientEvents.deregisterEditBox(labelBox);
            labelBox = null;
        }
        closeLabelBox = false;
    }

    // runs before TextInputClientEvents (KeyPressed.Post) and the screen, so Enter/Esc never reach either
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre evt) {
        if (labelBox == null || !labelBox.isFocused())
            return;
        int key = evt.getKeyCode();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String text = MapDrawRules.sanitiseLabel(labelBox.getValue());
            if (!text.isEmpty() && MC.player != null) {
                if (clientLimiter.tryConsume(MC.player.getUUID(), System.currentTimeMillis()))
                    MapDrawServerboundPacket.sendLabel(labelX, labelZ, text);
                else
                    HudClientEvents.showTempMessageI18n("hud.map.reignofnether.draw_rate_limited");
            }
            closeLabelNow();
            evt.setCanceled(true);
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeLabelNow();
            evt.setCanceled(true);   // don't let Esc also close the RTS screen
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (closeLabelBox || (labelBox != null && !(MC.screen instanceof TopdownGui)))
            closeLabelNow();
        for (int i = drawings.size() - 1; i >= 0; i--)
            if (--drawings.get(i).ticksLeft <= 0)
                drawings.remove(i);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        drawings.clear();
        drawing = false;
        closeLabelNow();
    }

    // ------------------------------------------------------------------ minimap

    /** Plots every live stroke (and the one being drawn) into the minimap overlay; called from its tick update. */
    static void drawOnMinimap() {
        for (int d = 0; d < drawings.size(); d++) {
            Drawing dr = drawings.get(d);
            int a = Mth.clamp((int) (dr.alpha() * 255f), 60, 255);
            if (dr.kind == MapDrawRules.KIND_STROKE) {
                for (int i = 1; i < dr.xs.length; i++)
                    MinimapClientEvents.plotOverlayLine(dr.xs[i - 1], dr.zs[i - 1], dr.xs[i], dr.zs[i], dr.rgb, a);
            } else {
                MinimapClientEvents.plotOverlayLine(dr.xs[0] - 2, dr.zs[0], dr.xs[0] + 2, dr.zs[0], dr.rgb, a);
                MinimapClientEvents.plotOverlayLine(dr.xs[0], dr.zs[0] - 2, dr.xs[0], dr.zs[0] + 2, dr.rgb, a);
            }
        }
        if (drawing && MC.player != null) {
            int rgb = PlayerColors.getPlayerDisplayColorHex(MC.player.getName().getString()) & 0xFFFFFF;
            for (int i = 1; i < curN; i++)
                MinimapClientEvents.plotOverlayLine(curXs[i - 1], curZs[i - 1], curXs[i], curZs[i], rgb, 200);
        }
    }

    /** Label text next to its ping on the minimap; drawn after the map texture each frame. */
    static void renderMinimapLabels(GuiGraphics gg) {
        if (drawings.isEmpty())
            return;
        Font font = MC.font;
        int sw = MC.getWindow().getGuiScaledWidth();
        for (int d = 0; d < drawings.size(); d++) {
            Drawing dr = drawings.get(d);
            if (dr.kind != MapDrawRules.KIND_LABEL || !MinimapClientEvents.isWorldXZinsideMap(dr.xs[0], dr.zs[0]))
                continue;
            Vec2 p = MinimapClientEvents.worldPosToMinimapScreen(dr.xs[0], dr.zs[0]);
            if (!MinimapClientEvents.isPointInsideMinimap(p.x, p.y))
                continue;
            int a = Mth.clamp((int) (dr.alpha() * 255f), 30, 255);
            int w = font.width(dr.label);
            // text sits up-left of the ping and is kept on screen; the minimap is in the bottom-right corner
            int tx = Mth.clamp((int) p.x - w / 2, 2, sw - w - 2);
            int ty = (int) p.y - 12;
            gg.pose().pushPose();
            gg.pose().translate(0, 0, 300);
            gg.fill(tx - 2, ty - 1, tx + w + 2, ty + 9, (Math.min(a, 0xA0) << 24));
            gg.drawString(font, dr.label, tx, ty, (a << 24) | dr.rgb, true);
            gg.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ world

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MC.level == null)
            return;
        boolean preview = drawing && !drawingOnMinimap && curN >= 2;
        if (drawings.isEmpty() && !preview)
            return;
        try {
            renderWorld(evt.getPoseStack(), evt.getCamera(), preview);
        } catch (Exception ignored) {
            // a cosmetic overlay must never break the frame
        }
    }

    private static void renderWorld(PoseStack pose, Camera camera, boolean preview) {
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();

        // lines first, through terrain (no depth test) so a line over a hill stays readable, like BAR's
        VertexConsumer vc = buffers.getBuffer(MyRenderer.LINES_NO_DEPTH_TEST);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int d = 0; d < drawings.size(); d++) {
            Drawing dr = drawings.get(d);
            float r = ((dr.rgb >> 16) & 0xFF) / 255f, g = ((dr.rgb >> 8) & 0xFF) / 255f, b = (dr.rgb & 0xFF) / 255f;
            float a = dr.alpha();
            if (dr.kind == MapDrawRules.KIND_STROKE) {
                for (int i = 1; i < dr.xs.length; i++)
                    line(vc, m, n, dr.xs[i - 1] + 0.5f, dr.ys[i - 1] + LIFT, dr.zs[i - 1] + 0.5f,
                        dr.xs[i] + 0.5f, dr.ys[i] + LIFT, dr.zs[i] + 0.5f, r, g, b, a);
            } else {
                // a short pin from the ground up to where the label floats
                float x = dr.xs[0] + 0.5f, y = dr.ys[0], z = dr.zs[0] + 0.5f;
                line(vc, m, n, x, y, z, x, y + 3f, z, r, g, b, a);
            }
        }
        if (preview && MC.player != null) {
            int rgb = PlayerColors.getPlayerDisplayColorHex(MC.player.getName().getString());
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
            int prevY = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, curXs[0], curZs[0]);
            for (int i = 1; i < curN; i++) {
                int y = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, curXs[i], curZs[i]);
                line(vc, m, n, curXs[i - 1] + 0.5f, prevY + LIFT, curZs[i - 1] + 0.5f,
                    curXs[i] + 0.5f, y + LIFT, curZs[i] + 0.5f, r, g, b, 0.8f);
                prevY = y;
            }
        }
        pose.popPose();
        buffers.endBatch(MyRenderer.LINES_NO_DEPTH_TEST);

        // label text: camera-facing, see-through, scaled up a little with orthoview zoom so it stays legible
        Font font = MC.font;
        float scale = OrthoviewClientEvents.isEnabled()
            ? 0.03f * Math.max(0.8f, Math.min(2.5f, OrthoviewClientEvents.getZoom() / 30f)) : 0.025f;
        boolean anyText = false;
        for (int d = 0; d < drawings.size(); d++) {
            Drawing dr = drawings.get(d);
            if (dr.kind != MapDrawRules.KIND_LABEL)
                continue;
            anyText = true;
            pose.pushPose();
            pose.translate(dr.xs[0] + 0.5 - cam.x, dr.ys[0] + 3.4 - cam.y, dr.zs[0] + 0.5 - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            pose.scale(-scale, -scale, scale);
            int a = Mth.clamp((int) (dr.alpha() * 255f), 30, 255);
            float tw = font.width(dr.label);
            font.drawInBatch(dr.label, -tw / 2f, 0, (a << 24) | dr.rgb, false, pose.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, (Math.min(a, 0x90) << 24), 0xF000F0);
            pose.popPose();
        }
        if (anyText) {
            RenderSystem.enableBlend();
            buffers.endBatch();
            RenderSystem.disableBlend();
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float z1,
                             float x2, float y2, float z2, float r, float g, float b, float a) {
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
