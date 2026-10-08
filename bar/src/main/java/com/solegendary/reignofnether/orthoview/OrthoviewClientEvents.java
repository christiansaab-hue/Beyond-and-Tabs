package com.solegendary.reignofnether.orthoview;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientEvents;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.guiscreen.TopdownGuiServerboundPacket;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.startpos.StartPosClientEvents;
import com.solegendary.reignofnether.startpos.StartPosServerboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialStage;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

/**
 * Handler that implements and manages hotkeys for the orthographic camera.
 *
 * BAR-style camera (Beyond and Tabs): all continuous camera motion is driven once per rendered frame
 * from real frame time (exponential smoothing), so it feels the same at 30 or 300 fps:
 * - WASD / arrow keys pan (speed scales with zoom), plus screen-edge panning
 * - mouse wheel zooms toward the cursor (the ground under the cursor stays under the cursor)
 * - Shift+wheel and Shift+W/S tilt (pitch), Shift+A/D and Ctrl+wheel swing the view around (yaw)
 * - middle-mouse (or Alt+left) drag grabs the map
 * - zoom range extends out to whatever the loaded render distance can fill (up to ZOOM_MAX_HARD)
 *
 * @author SoLegendary, adapted from Mineshot by Nico Bergemann <barracuda415 at yahoo.de>
 */
public class OrthoviewClientEvents {

    public static final int CHAT_Y_OFFSET = -55; // shift chat up by this much when in RTS cam to make room for the

    public enum LeafHideMethod {
        NONE, AROUND_UNITS_AND_CURSOR, // requires threaded video option
        ALL
    }

    public static boolean shouldHideLeaves() {
        return hideLeavesMethod != LeafHideMethod.NONE;
    }

    public static LeafHideMethod hideLeavesMethod = LeafHideMethod.NONE;
    public static int enabledCount = 0;
    public static boolean enabled = false;
    private static boolean cameraMovingByMouse = false; // excludes edgepanning

    private static final Minecraft MC = Minecraft.getInstance();
    private static final float ZOOM_MIN = 10;
    // the old RoN maximum; the dynamic maximum never drops below this
    private static final float ZOOM_MAX = 90;
    // absolute maximum for BAR-sized battles; the real limit is whatever the render distance can fill
    public static final float ZOOM_MAX_HARD = 200;
    private static final float CAMROTY_MAX = -12; // shallowest tilt (towards a side-on view)
    private static final float CAMROTY_MIN = -90; // straight down
    private static final float CAMROT_MOUSE_SENSITIVITY = 0.12f;

    private static final float ZOOM_DEFAULT = 30;
    private static final float CAMROTX_DEFAULT = 135;
    private static final float CAMROTY_DEFAULT = -45;

    // BAR-feel tuning (all per second of real time)
    private static final float PAN_SPEED_PER_ZOOM = 1.25f;   // pan speed = this * zoom * sensitivity (blocks/s)
    private static final float PAN_SMOOTHING = 11f;          // velocity response rate (1/s)
    private static final float ZOOM_SMOOTHING = 14f;
    private static final float ROT_SMOOTHING = 14f;
    private static final float ZOOM_SCROLL_FACTOR = 1.15f;   // per wheel notch
    private static final float ZOOM_KEY_RATE = 2.2f;         // e-folds per second while +/- held
    private static final float YAW_KEY_SPEED = 110f;         // degrees per second (Shift+A/D)
    private static final float PITCH_KEY_SPEED = 70f;        // degrees per second (Shift+W/S)
    private static final float YAW_STEP_SCROLL = 15f;        // degrees per notch (Ctrl+wheel)
    private static final float PITCH_STEP_SCROLL = 5f;       // degrees per notch (Shift+wheel)
    private static final int EDGE_PAN_MARGIN = 1;            // window pixels

    private static final int FORCE_PAN_TICKS_DEFAULT = 20;
    private static int forcePanTicksLeft = 0;
    private static float forcePanTargetX = 0;
    private static float forcePanTargetZ = 0;
    private static float forcePanOriginalX = 0;
    private static float forcePanOriginalZ = 0;
    private static float forcePanOriginalZoom = 0;
    private static float forceZoom = 0;

    private static int cameraLockTicksLeft = 0;
    private static boolean cameraLocked = false;

    private static float zoom = 30; // = number of blocks in view height (higher == zoomed out)
    private static float camRotX = 135; // left/right - should start northeast (towards -Z,+X)
    private static float camRotY = -45; // up/down
    private static float camRotAdjX = 0;
    private static float camRotAdjY = 0;
    private static float mouseRightDownX = 0;
    private static float mouseRightDownY = 0;
    private static float mouseLeftDownX = 0;
    private static float mouseLeftDownY = 0;
    public static final float MAX_PAN_SENSITIVITY = 3.0f;

    // smoothing targets; the visible values above chase these every frame
    private static float targetZoom = ZOOM_DEFAULT;
    private static float targetCamRotX = CAMROTX_DEFAULT;
    private static float targetCamRotY = CAMROTY_DEFAULT;
    private static boolean zoomAnchorAtCursor = false;
    private static float panVelRight = 0; // blocks/s, camera-relative ground plane
    private static float panVelFwd = 0;
    private static long lastFrameNanos = 0;
    private static boolean grabbing = false;
    private static double grabLastX = 0;
    private static double grabLastY = 0;
    private static float lastGroundY = 64;

    // by default orthoview players stay at BASE_Y, but can be raised to as high as MAX_Y if they are clipping terrain
    public static double orthoviewPlayerBaseY = 100;
    public static double orthoviewPlayerMaxY = 160;
    private static double minOrthoviewY = 0;

    public static void setMinOrthoviewY(double value) {
        minOrthoviewY = value;
        if (MC.level != null && MC.player != null && MC.player.getY() < value + 15 && MC.gameMode != null &&
            (MC.gameMode.getPlayerMode() == GameType.CREATIVE ||
            MC.gameMode.getPlayerMode() == GameType.SPECTATOR) && isEnabled()) {
            MC.player.move(MoverType.SELF, new Vec3(0, minOrthoviewY - MC.player.getY() + 15, 0));
        }
    }

    public static float getPanSensitivityMult() {
        return (float) ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.get() / 10f;
    }
    public static void adjustPanSensitivityMult(boolean increase) {
        if (increase && Math.round(getPanSensitivityMult() * 10) < (MAX_PAN_SENSITIVITY * 10))
            ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.set(ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.get() + 1);
        else if (!increase && Math.round(getPanSensitivityMult() * 10) > 1)
            ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.set(ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.get() - 1);
    }

    public static void updateOrthoviewY() {
        if (MC.player != null && MC.level != null) {
            BlockPos playerPos = MC.player.blockPosition();
            int radius = 10; // Defines the area around the player to sample heights
            int sumHeights = 0;
            int count = 0;

            // Iterate through a square area around the player
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int blockX = playerPos.getX() + x;
                    int blockZ = playerPos.getZ() + z;
                    int height = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    sumHeights += height;
                    count++;
                }
            }
            // Calculate the average height
            int avgHeight = count > 0 ? sumHeights / count : playerPos.getY();
            lastGroundY = avgHeight;

            // Update ORTHOVIEW values based on the average height
            orthoviewPlayerBaseY = Math.max(avgHeight + 30, minOrthoviewY);
            orthoviewPlayerMaxY = avgHeight + 100;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isCameraMovingByMouse() {
        return cameraMovingByMouse;
    }

    public static float getZoom() {
        return zoom;
    }

    public static float getCamRotX() {
        return -camRotX - camRotAdjX;
    }

    public static float getCamRotY() {
        return -camRotY - camRotAdjY;
    }

    public static boolean isCameraLocked() {
        return cameraLockTicksLeft > 0 || cameraLocked;
    }

    public static void lockCam() {
        cameraLocked = true;
    }

    public static void unlockCam() {
        cameraLocked = false;
    }

    private static void reset() {
        targetZoom = ZOOM_DEFAULT;
        zoomAnchorAtCursor = false;
        // rotate back the short way round
        targetCamRotX = camRotX + Mth.wrapDegrees(CAMROTX_DEFAULT - camRotX);
        targetCamRotY = CAMROTY_DEFAULT;
    }

    public static void rotateCam(float x, float y) {
        if (isCameraLocked()) {
            return;
        }
        // applied instantly (used when committing a mouse-drag rotation), so shift the target with it
        camRotX += x;
        targetCamRotX += x;
        normaliseYaw();
    }

    private static void normaliseYaw() {
        while (camRotX >= 360) {
            camRotX -= 360;
            targetCamRotX -= 360;
        }
        while (camRotX <= -360) {
            camRotX += 360;
            targetCamRotX += 360;
        }
    }

    public static void zoomCam(float zoomAdj) {
        if (isCameraLocked()) {
            return;
        }
        targetZoom = Mth.clamp(targetZoom + zoomAdj, ZOOM_MIN, getZoomMax());
        zoomAnchorAtCursor = false;
    }

    // the furthest zoom at which the whole view is still inside the loaded render distance
    public static float getZoomMax() {
        if (MC.player == null || MC.level == null)
            return ZOOM_MAX;
        double renderRadius = (MC.options.getEffectiveRenderDistance() - 1) * 16.0;
        double pitch = Math.toRadians(Mth.clamp(getCamRotY(), 5f, 90f));
        double sinP = Math.sin(pitch);
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        double height = Math.max(0, cam.y - lastGroundY);
        // horizontal distance from the camera to the ground point at the centre of the screen
        double d0 = pitch >= Math.toRadians(89.5) ? 0 : height / Math.tan(pitch);
        double aspect = (double) MC.getWindow().getScreenWidth() / Math.max(1, MC.getWindow().getScreenHeight());
        double u = 1 / (2 * sinP); // half ground depth per unit of zoom
        double v = aspect / 2;     // half ground width per unit of zoom
        // solve (d0 + u*H)^2 + (v*H)^2 = R^2 for the far corners of the view
        double a = u * u + v * v;
        double b = 2 * d0 * u;
        double c = d0 * d0 - renderRadius * renderRadius;
        double disc = b * b - 4 * a * c;
        double h = disc < 0 ? 0 : (-b + Math.sqrt(disc)) / (2 * a);
        return (float) Mth.clamp(h, ZOOM_MAX, ZOOM_MAX_HARD);
    }

    public static void panCam(float x, float y, float z) { // pan camera relative to rotation
        if (MC.player != null) {
            Vec2 XZRotated = MyMath.rotateCoords(x, z, -camRotX - camRotAdjX);
            moveCamera(XZRotated.x, y, XZRotated.y);
        }
    }

    // moves the camera entity and shifts its previous-position by the same amount so the move shows this frame
    // instead of being interpolated over the next tick (tick-based moves still interpolate normally)
    private static void moveCamera(double dx, double dy, double dz) {
        Player player = MC.player;
        if (player == null)
            return;
        double x0 = player.getX();
        double y0 = player.getY();
        double z0 = player.getZ();
        player.move(MoverType.SELF, new Vec3(dx, dy, dz));
        clampPlayerToWorldBorder();
        player.xo += player.getX() - x0;
        player.yo += player.getY() - y0;
        player.zo += player.getZ() - z0;
    }

    // camera basis vectors (same convention as vanilla Camera; right = -left)
    private static Vec3 getForwardVector() {
        double yaw = Math.toRadians(getCamRotX());
        double pitch = Math.toRadians(getCamRotY());
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }
    private static Vec3 getRightVector() {
        double yaw = Math.toRadians(getCamRotX());
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }
    private static Vec3 getUpVector() {
        return getRightVector().cross(getForwardVector());
    }

    // moves the camera by an offset in the view plane, converted into an equivalent horizontal move
    // (sliding along the view direction doesn't change an orthographic image) so the camera height is kept
    private static void moveInViewPlane(Vec3 offset) {
        Vec3 f = getForwardVector();
        if (Math.abs(f.y) > 1e-3)
            offset = offset.subtract(f.scale(offset.y / f.y));
        moveCamera(offset.x, 0, offset.z);
    }

    // changes camera height without shifting the image on screen
    private static void moveVerticalKeepingView(double dy) {
        Vec3 f = getForwardVector();
        if (Math.abs(f.y) > 1e-3) {
            double t = dy / f.y;
            moveCamera(f.x * t, dy, f.z * t);
        } else {
            moveCamera(0, dy, 0);
        }
    }

    // spectator skips vanilla border collision, so snap back inside on every move
    private static void clampPlayerToWorldBorder() {
        if (MC.player == null || MC.level == null) return;
        WorldBorder border = MC.level.getWorldBorder();
        double margin = 1.0D;
        double minX = border.getMinX() + margin;
        double maxX = border.getMaxX() - margin;
        double minZ = border.getMinZ() + margin;
        double maxZ = border.getMaxZ() - margin;
        if (maxX <= minX || maxZ <= minZ) return;
        double x = MC.player.getX();
        double z = MC.player.getZ();
        double cx = Mth.clamp(x, minX, maxX);
        double cz = Mth.clamp(z, minZ, maxZ);
        if (cx != x || cz != z)
            MC.player.setPos(cx, MC.player.getY(), cz);
    }

    public static void forceMoveCam(int x, int z, int cameraLockTicks) {
        forceMoveCam(x, z, cameraLockTicks, FORCE_PAN_TICKS_DEFAULT, (int) ZOOM_DEFAULT);
    }

    // lock the camera and move it towards a location, remain locked for cameraLockTicks
    public static void forceMoveCam(int x, int z, int cameraLockTicks, int forcePanTicks, int zoomLevel) {
        if (MC.player != null && OrthoviewClientEvents.isEnabled()) {
            forcePanTicksLeft = forcePanTicks;
            forcePanTargetX = x;
            forcePanTargetZ = z;
            cameraLockTicksLeft = forcePanTicks + cameraLockTicks;
            forcePanOriginalX = MC.player.getOnPos().getX();
            forcePanOriginalZ = MC.player.getOnPos().getZ();
            forceZoom = zoomLevel == 0 ? zoom : zoomLevel;
            forcePanOriginalZoom = zoom;
        }
    }

    public static void forceMoveCam(String playerName, Vec3i pos, int cameraLockTicks, int forcePanTicks, int zoomLevel) {
        if (MC.player != null && MC.player.getName().getString().equals(playerName)) {
            forceMoveCam(pos.getX(), pos.getZ(), cameraLockTicks, forcePanTicks, zoomLevel);
        }
    }

    public static void forceMoveCam(Vec3i pos, int cameraLockTicks) {
        forceMoveCam(pos.getX(), pos.getZ(), cameraLockTicks);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }

        long windowHandle = MC.getWindow().getWindow();
        int cursorMode;

        try {
            cursorMode = GLFW.glfwGetInputMode(windowHandle, GLFW.GLFW_CURSOR);
        } catch (NullPointerException | IllegalStateException e) {
            cursorMode = GLFW.GLFW_CURSOR_NORMAL;
        }

        boolean grabbed = cursorMode == GLFW.GLFW_CURSOR_DISABLED;
        if (MC.screen == null && !grabbed && MC.isWindowActive()) {
            MC.mouseHandler.releaseMouse();
            MC.mouseHandler.grabMouse();
        }

        if (isEnabled() && MC.gameMode != null && (
            MC.gameMode.getPlayerMode() == GameType.ADVENTURE || MC.gameMode.getPlayerMode() == GameType.SURVIVAL
        )) {
            toggleEnable();
        }

        if (cameraLockTicksLeft > 0) {
            cameraLockTicksLeft -= 1;
        }

        if (!OrthoviewClientEvents.isEnabled() || MC.player == null || MC.level == null) {
            forcePanTicksLeft = 0;
            return;
        }

        // keep the camera above the terrain; moves are made along the view direction so the image doesn't jump
        if (MiscUtil.isGroundBlock(MC.level, MC.player.blockPosition().offset(0, -5, 0))
            && MC.player.getOnPos().getY() <= orthoviewPlayerMaxY) {
            moveVerticalKeepingView(1f);
        }
        if (!MiscUtil.isGroundBlock(MC.level, MC.player.blockPosition().offset(0, -6, 0))
            && MC.player.getOnPos().getY() >= orthoviewPlayerBaseY) {
            moveVerticalKeepingView(-1f);
        }

        if (forcePanTicksLeft > 0) {
            float xDiff = (forcePanTargetX - forcePanOriginalX) / FORCE_PAN_TICKS_DEFAULT;
            float zDiff = (forcePanTargetZ - forcePanOriginalZ) / FORCE_PAN_TICKS_DEFAULT;
            float zoomDiff = (forceZoom - forcePanOriginalZoom) / FORCE_PAN_TICKS_DEFAULT;
            zoom += zoomDiff;
            targetZoom = zoom;
            zoomAnchorAtCursor = false;
            MC.player.move(MoverType.SELF, new Vec3(xDiff, 0, zDiff));
            clampPlayerToWorldBorder();
            forcePanTicksLeft -= 1;
        }
        updateOrthoviewY();
    }

    private static boolean isRawKeyDown(int key) {
        return GLFW.glfwGetKey(MC.getWindow().getWindow(), key) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        return Keybindings.shiftMod.isDown() || isRawKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isCtrlDown() {
        return Keybindings.ctrlMod.isDown() || isRawKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static float smoothingAlpha(float rate, float dt) {
        return 1f - (float) Math.exp(-rate * dt);
    }

    // all continuous camera motion happens here, once per rendered frame, before the world is drawn
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0f, 0.1f);

        if (!enabled || MC.player == null || MC.level == null) {
            panVelRight = 0;
            panVelFwd = 0;
            grabbing = false;
            return;
        }
        updateCamera(dt);
    }

    private static void updateCamera(float dt) {
        Player player = MC.player;
        if (player == null)
            return;

        long window = MC.getWindow().getWindow();
        boolean inRtsScreen = MC.screen instanceof TopdownGui;
        boolean locked = isCameraLocked();
        boolean windowActive = MC.isWindowActive();
        boolean keysActive = inRtsScreen && windowActive && !locked && !TextInputClientEvents.isAnyInputFocused();
        boolean shift = isShiftDown();
        boolean ctrl = isCtrlDown();
        boolean alt = Keybindings.altMod.isDown();

        // ---- keyboard ----
        float desiredRight = 0;
        float desiredFwd = 0;
        if (keysActive && !alt && !ctrl) {
            boolean up = Keybindings.panUp.isDown() || Keybindings.panPlusZ.isDown();
            boolean down = Keybindings.panDown.isDown() || Keybindings.panMinusZ.isDown();
            boolean left = Keybindings.panLeft.isDown() || Keybindings.panPlusX.isDown();
            boolean right = Keybindings.panRight.isDown() || Keybindings.panMinusX.isDown();
            if (shift) {
                // Shift+A/D swing the view around, Shift+W/S tilt it (W towards side-on, S towards top-down)
                if (left) targetCamRotX += YAW_KEY_SPEED * dt;
                if (right) targetCamRotX -= YAW_KEY_SPEED * dt;
                if (up) targetCamRotY += PITCH_KEY_SPEED * dt;
                if (down) targetCamRotY -= PITCH_KEY_SPEED * dt;
            } else {
                desiredRight = (right ? 1 : 0) - (left ? 1 : 0);
                desiredFwd = (up ? 1 : 0) - (down ? 1 : 0);
            }
        }
        if (keysActive) {
            if (Keybindings.zoomIn.isDown()) {
                targetZoom *= (float) Math.exp(-ZOOM_KEY_RATE * dt);
                zoomAnchorAtCursor = false;
            }
            if (Keybindings.zoomOut.isDown()) {
                targetZoom *= (float) Math.exp(ZOOM_KEY_RATE * dt);
                zoomAnchorAtCursor = false;
            }
        }

        // ---- grab-drag the map (middle mouse, or Alt + left mouse) ----
        boolean mmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        boolean altLmb = alt && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double cursorX = MC.mouseHandler.xpos();
        double cursorY = MC.mouseHandler.ypos();
        int winW = Math.max(1, MC.getWindow().getScreenWidth());
        int winH = Math.max(1, MC.getWindow().getScreenHeight());
        if (inRtsScreen && !locked && windowActive && (mmb || altLmb)) {
            if (grabbing) {
                double dx = cursorX - grabLastX;
                double dy = cursorY - grabLastY;
                if (dx != 0 || dy != 0) {
                    double blocksPerPixel = zoom / winH;
                    // the world follows the cursor: move the camera the opposite way
                    moveInViewPlane(getRightVector().scale(-dx * blocksPerPixel)
                            .add(getUpVector().scale(dy * blocksPerPixel)));
                }
            }
            grabbing = true;
            cameraMovingByMouse = true;
            grabLastX = cursorX;
            grabLastY = cursorY;
            panVelRight = 0;
            panVelFwd = 0;
        } else if (grabbing) {
            grabbing = false;
            cameraMovingByMouse = false;
        }

        // ---- edge panning ----
        if (inRtsScreen && windowActive && !locked && !alt && !grabbing) {
            if (desiredRight == 0) {
                if (cursorX <= EDGE_PAN_MARGIN) desiredRight = -1;
                else if (cursorX >= winW - EDGE_PAN_MARGIN) desiredRight = 1;
            }
            if (desiredFwd == 0) {
                if (cursorY <= EDGE_PAN_MARGIN) desiredFwd = 1;
                else if (cursorY >= winH - EDGE_PAN_MARGIN) desiredFwd = -1;
            }
        }
        if (desiredRight < 0) TutorialClientEvents.pannedLeft = true;
        if (desiredRight > 0) TutorialClientEvents.pannedRight = true;
        if (desiredFwd > 0) TutorialClientEvents.pannedUp = true;
        if (desiredFwd < 0) TutorialClientEvents.pannedDown = true;
        if (desiredRight != 0 && desiredFwd != 0) {
            desiredRight *= 0.7071f;
            desiredFwd *= 0.7071f;
        }

        // ---- rotation smoothing ----
        targetCamRotY = Mth.clamp(targetCamRotY, CAMROTY_MIN, CAMROTY_MAX);
        float rotA = smoothingAlpha(ROT_SMOOTHING, dt);
        camRotX += (targetCamRotX - camRotX) * rotA;
        camRotY += (targetCamRotY - camRotY) * rotA;
        if (Math.abs(targetCamRotX - camRotX) < 0.01f) camRotX = targetCamRotX;
        if (Math.abs(targetCamRotY - camRotY) < 0.01f) camRotY = targetCamRotY;
        normaliseYaw();

        // ---- zoom smoothing (log space), anchored at the cursor for wheel zoom ----
        targetZoom = Mth.clamp(targetZoom, ZOOM_MIN, getZoomMax());
        if (forcePanTicksLeft <= 0 && zoom != targetZoom) {
            float zoomA = smoothingAlpha(ZOOM_SMOOTHING, dt);
            float newZoom = (float) Math.exp(Math.log(zoom) + (Math.log(targetZoom) - Math.log(zoom)) * zoomA);
            if (Math.abs(newZoom - targetZoom) < 0.01f)
                newZoom = targetZoom;
            float dz = newZoom - zoom;
            if (zoomAnchorAtCursor && inRtsScreen && dz != 0) {
                // keep the point under the cursor fixed: the cursor's view-plane offset scales with zoom
                double ndcX = cursorX / winW * 2 - 1;
                double ndcY = 1 - cursorY / winH * 2;
                double aspect = (double) winW / winH;
                Vec3 offset = getRightVector().scale(ndcX * aspect * dz / 2)
                        .add(getUpVector().scale(ndcY * dz / 2));
                moveInViewPlane(offset.scale(-1));
            }
            zoom = newZoom;
        }

        // ---- pan (velocity smoothed, speed scales with zoom) ----
        float speed = PAN_SPEED_PER_ZOOM * zoom * getPanSensitivityMult();
        float panA = smoothingAlpha(PAN_SMOOTHING, dt);
        panVelRight += (desiredRight * speed - panVelRight) * panA;
        panVelFwd += (desiredFwd * speed - panVelFwd) * panA;
        if (Math.abs(panVelRight) < 0.01f && desiredRight == 0) panVelRight = 0;
        if (Math.abs(panVelFwd) < 0.01f && desiredFwd == 0) panVelFwd = 0;
        if ((panVelRight != 0 || panVelFwd != 0) && !locked) {
            double yaw = Math.toRadians(getCamRotX());
            Vec3 fwdH = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 move = getRightVector().scale(panVelRight * dt).add(fwdH.scale(panVelFwd * dt));
            moveCamera(move.x, 0, move.z);
        }

        // note that we treat x and y rot as horizontal and vertical, but MC treats it the other way around...
        player.setXRot(getCamRotY());
        player.setYRot(getCamRotX());
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
    }

    public static void toggleEnable() {
        if (MC.level == null || MC.player == null) {
            return;
        }

        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            RangeIndicatorAddon ria;
            if ((ria = building.getBuilding().getActiveAddon(RangeIndicatorAddon.class)) != null)
                ria.updateHighlightBps(building);
        }

        enabled = !enabled;

        if (enabled) {
            MC.options.tutorialStep = TutorialSteps.NONE;
            MC.getTutorial().stop();
            enabledCount += 1;
            PlayerServerboundPacket.enableOrthoview();
            MinimapClientEvents.setMapCentre(MC.player.getX(), MC.player.getZ());
            PlayerServerboundPacket.teleportPlayer(MC.player.getX(), orthoviewPlayerBaseY, MC.player.getZ());
            TopdownGuiServerboundPacket.openTopdownGui(MC.player.getId());
            MC.options.cloudStatus().set(CloudStatus.OFF);
            MC.options.hideGui = false; // for some reason, when gui is hidden, shape rendering goes whack
            MC.options.setCameraType(CameraType.FIRST_PERSON);
            switchToEasyIfPeaceful();
        } else {
            PlayerServerboundPacket.disableOrthoview();
            TopdownGuiServerboundPacket.closeTopdownGui(MC.player.getId());
            if (StartPosClientEvents.hasReservedPos()) {
                StartPosClientEvents.selectedFaction = Factions.NONE;
                StartPosServerboundPacket.unreservePos(StartPosClientEvents.getPos().pos);
            }
        }
        TutorialClientEvents.updateStage();
    }

    public static void tryToSetCamera(String playerName, boolean value) {
        if (MC.player != null && MC.player.getName().getString().equals(playerName) && value != enabled) {
            tryToToggleEnable();
        }
    }

    public static void centreCameraOnPosForPlayer(String playerName, BlockPos bp) {
        if (MC.player != null && MC.player.getName().getString().equals(playerName))
            centreCameraOnPos(new Vec3(bp.getX(), bp.getY(), bp.getZ()));
    }

    public static void centreCameraOnPos(BlockPos bp) {
        centreCameraOnPos(new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5));
    }

    // moves the camera to the position such that x,z is at the centre of the screen
    // (exact: the centre-of-screen ray runs along the view direction through the camera, so slide back along it
    // to the camera's current height)
    public static void centreCameraOnPos(Vec3 pos) {
        if (MC.player == null) {
            return;
        }
        MinimapClientEvents.setMapCentre(pos.x, pos.z);
        Vec3 f = getForwardVector();
        double eyeY = MC.player.getEyeY();
        double s = Math.abs(f.y) < 1e-3 ? 0 : (eyeY - pos.y) / f.y;
        panVelRight = 0;
        panVelFwd = 0;
        PlayerServerboundPacket.teleportPlayer(pos.x + f.x * s, MC.player.getY(), pos.z + f.z * s);
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent evt) {
        if (isEnabled()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent evt) {
        if (isEnabled()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    // can't use ScreenEvent.KeyboardKeyPressedEvent as that only happens when a screen is up
    public static void onInput(InputEvent.Key evt) {
        // Prevent repeated key actions
        if (evt.getAction() == GLFW.GLFW_PRESS) {

            if (evt.getKey() == Keybindings.getFnum(12).getKey()) {
                tryToToggleEnable();
            }
            else if (evt.getKey() == Keybindings.reset.getKey()) {
                reset();
            }
        }
    }

    // rotates the camera to fixed points at -135, -45, 45 and 135 degrees (animated by the frame smoothing)
    public static void fixedRotateCam(boolean clockwise) {
        if (isCameraLocked())
            return;
        camRotX += camRotAdjX;
        targetCamRotX += camRotAdjX;
        camRotAdjX = 0;
        float current = -targetCamRotX;
        int roundedRotX = (Math.round(current / 90.0f) * 90) + 45;
        float target;

        if (roundedRotX > current && clockwise)
            target = roundedRotX;
        else if (roundedRotX < current && !clockwise)
            target = roundedRotX - 90;
        else if (clockwise)
            target = roundedRotX;
        else
            target = roundedRotX - 90;

        if (target == current && clockwise)
            target += 90;
        else if (target == current)
            target -= 90;

        targetCamRotX = -target;
    }

    public static void tryToToggleEnable() {
        if (!OrthoviewClientEvents.isCameraLocked() && MC.gameMode != null) {
            if (MC.player != null && (
                    MC.gameMode.getPlayerMode() == GameType.ADVENTURE
                            || MC.gameMode.getPlayerMode() == GameType.SURVIVAL
            )) {
                MC.player.sendSystemMessage(Component.literal(I18n.get("hud.orthoview.reignofnether.ortho_error")));
            } else {
                toggleEnable();
            }
        }
    }

    public static Button getLeavesHidingButton() {
        return new Button("Hide Leaves Method",
                14,
                switch(hideLeavesMethod) {
                    case NONE -> ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/leaves.png");
                    case AROUND_UNITS_AND_CURSOR -> ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/lime_stained_glass.png");
                    case ALL -> ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/glass.png");
                },
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame.png"),
                null,
                () -> false,
                () -> !TutorialClientEvents.isAtOrPastStage(TutorialStage.MINIMAP_CLICK) || !MinimapClientEvents.isLargeMap(),
                () -> true,
                () -> {
                    FogOfWarClientEvents.resetFogChunks();
                    UnitClientEvents.windowUpdateTicks = 0;
                    if (hideLeavesMethod == LeafHideMethod.NONE) {
                        hideLeavesMethod = LeafHideMethod.AROUND_UNITS_AND_CURSOR;
                    } else if (hideLeavesMethod == LeafHideMethod.AROUND_UNITS_AND_CURSOR) {
                        hideLeavesMethod = LeafHideMethod.ALL;
                    } else if (hideLeavesMethod == LeafHideMethod.ALL) {
                        hideLeavesMethod = LeafHideMethod.NONE;
                    }
                },
                null,
                List.of(
                        fcs(I18n.get("hud.orthoview.reignofnether.hiding_leaves_around"), hideLeavesMethod == LeafHideMethod.AROUND_UNITS_AND_CURSOR),
                        fcs(I18n.get("hud.orthoview.reignofnether.hiding_leaves_all"), hideLeavesMethod == LeafHideMethod.ALL),
                        fcs(I18n.get("hud.orthoview.reignofnether.disabled_hiding_leaves"), hideLeavesMethod == LeafHideMethod.NONE)
                )
        );
    }

    // Method to switch difficulty to Easy if it is currently set to Peaceful
    private static void switchToEasyIfPeaceful() {
        Minecraft minecraft = Minecraft.getInstance();

        // Ensure this only runs in single-player mode
        if (minecraft.getSingleplayerServer() != null) {
            Difficulty currentDifficulty = minecraft.level.getDifficulty();

            // If the current difficulty is Peaceful, switch to Easy
            if (currentDifficulty == Difficulty.PEACEFUL) {
                minecraft.getSingleplayerServer().setDifficulty(Difficulty.EASY, true);
                HudClientEvents.showTemporaryMessage(
                    "RTS units cannot spawn in Peaceful. Your difficulty has been set to Easy.");
            }
        }
    }

    // mouse wheel: zoom toward the cursor; Shift+wheel tilts; Ctrl+wheel rotates.
    // While placing a building the plain wheel still rotates the building (handled in BuildingClientEvents).
    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre evt) {
        if (!enabled || isCameraLocked() || !(evt.getScreen() instanceof TopdownGui)) {
            return;
        }
        double delta = evt.getScrollDelta();
        if (delta == 0)
            return;
        boolean placing = BuildingClientEvents.getBuildingToPlace() != null;

        if (isShiftDown()) {
            targetCamRotY = Mth.clamp(targetCamRotY + (float) Math.signum(delta) * PITCH_STEP_SCROLL,
                    CAMROTY_MIN, CAMROTY_MAX);
            if (placing)
                evt.setCanceled(true);
            return;
        }
        if (isCtrlDown()) {
            targetCamRotX += (float) Math.signum(delta) * YAW_STEP_SCROLL;
            if (placing)
                evt.setCanceled(true);
            return;
        }
        if (placing || CustomBuildingClientEvents.showCommandsMenu)
            return;

        targetZoom = Mth.clamp(targetZoom * (float) Math.pow(ZOOM_SCROLL_FACTOR, -delta), ZOOM_MIN, getZoomMax());
        zoomAnchorAtCursor = true;
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render evt) {
        if (!enabled || !(evt.getScreen() instanceof TopdownGui)) {
            return;
        }
        // GLFW coords seem to be 2x vanilla coords, but use only them for consistency
        // since we need to use glfwSetCursorPos
        long glfwWindow = MC.getWindow().getWindow();
        int glfwWinWidth = MC.getWindow().getScreenWidth();
        int glfwWinHeight = MC.getWindow().getScreenHeight();
        double cursorX = MC.mouseHandler.xpos();
        double cursorY = MC.mouseHandler.ypos();

        // lock mouse inside window (edge panning itself happens per frame in updateCamera)
        if (cursorX >= glfwWinWidth) {
            GLFW.glfwSetCursorPos(glfwWindow, glfwWinWidth, cursorY);
        }
        if (cursorY >= glfwWinHeight) {
            GLFW.glfwSetCursorPos(glfwWindow, cursorX, glfwWinHeight);
        }
        if (cursorX <= 0) {
            GLFW.glfwSetCursorPos(glfwWindow, 0, cursorY);
        }
        if (cursorY <= 0) {
            GLFW.glfwSetCursorPos(glfwWindow, cursorX, 0);
        }
    }

    // prevents stuff like fire and water effects being shown on your HUD
    @SubscribeEvent
    public static void onRenderBlockOverlay(RenderBlockScreenEffectEvent evt) {
        if (enabled) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Post evt) {
        if (!enabled || isCameraLocked()) {
            return;
        }

        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            mouseLeftDownX = (float) evt.getMouseX();
            mouseLeftDownY = (float) evt.getMouseY();
        } else if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            mouseRightDownX = (float) evt.getMouseX();
            mouseRightDownY = (float) evt.getMouseY();
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased evt) {
        if (!enabled || isCameraLocked()) {
            return;
        }

        // stop treating the rotation as adjustments and add them to the base amount
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            cameraMovingByMouse = false;
        }
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            cameraMovingByMouse = false;
            rotateCam(camRotAdjX, camRotAdjY);
            camRotAdjX = 0;
            camRotAdjY = 0;
        }
    }

    // map dragging (middle mouse / Alt+left) is polled per frame in updateCamera; Alt+right drag still rotates
    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (!enabled || isCameraLocked()) {
            return;
        }
        if (evt.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_2 && Keybindings.altMod.isDown()) {
            cameraMovingByMouse = true;
            camRotAdjX = (float) (evt.getMouseX() - mouseRightDownX) * CAMROT_MOUSE_SENSITIVITY;
        }
    }

    // don't let orthoview players see other orthoview players or themselves
    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre evt) {
        if (enabled && (evt.getEntity().isSpectator() || evt.getEntity().isCreative())) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFovModifier(ViewportEvent.ComputeFov evt) {
        if (enabled) {
            evt.setFOV(180);
        }
    }

    // distance fog is meaningless for a top-down orthographic camera and whites out the edges of the view
    // when zoomed out, so push it beyond anything visible (fluids keep their own fog)
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog evt) {
        if (!enabled || evt.getType() != FogType.NONE) {
            return;
        }
        evt.setNearPlaneDistance(10000f);
        evt.setFarPlaneDistance(12000f);
        evt.setCanceled(true);
    }

    // OrthoViewMixin uses this to generate a customisation orthographic view to replace the usual view
    // shamelessly copied from ImmersivePortals 1.16
    public static Matrix4f getOrthographicProjection() {
        int width = MC.getWindow().getScreenWidth();
        int height = MC.getWindow().getScreenHeight();

        float near = -3000;
        float far = 3000;

        float zoomFinal = zoom;

        float wView = (zoomFinal / height) * width;
        float left = -wView / 2;
        float rgt = wView / 2;

        float top = zoomFinal / 2;
        float bot = -zoomFinal / 2;

        // BarFx camera shake: nudge the view a little on big nearby blasts
        float shakeScale = Math.max(.5f, zoomFinal / 30f);
        float shX = com.solegendary.reignofnether.barfx.BarFxClient.shakeX() * shakeScale;
        float shY = com.solegendary.reignofnether.barfx.BarFxClient.shakeY() * shakeScale;
        left += shX; rgt += shX; top += shY; bot += shY;

        Matrix4f m1 = new Matrix4f(2.0f / (rgt - left), 0, 0,
                -(rgt + left) / (rgt - left), 0,
                2.0f / (top - bot), 0,
                -(top + bot) / (top - bot), 0, 0, -2.0f / (far - near), -(far + near) / (far - near), 0, 0, 0, 1);

        return m1;
    }
}
