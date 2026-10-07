package dev.beyondtabs.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The free top-down camera: a focus point on the ground, a distance (zoom), a fixed-ish pitch that steepens as you zoom
 * out, and a yaw you can turn. Drives an invisible client-only marker entity that Minecraft renders the world from.
 */
public final class RtsCamera {
    public static boolean active;
    public static float focusX, focusZ, dist = 32, yaw, pitch = 55;   // yaw/pitch in degrees, Minecraft convention
    static float targetDist = 32, smoothX, smoothZ;
    static Marker eye;
    static final float MIN_DIST = 6, MAX_DIST = 260;

    static float ground(float x, float z) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 64 : mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        focusX = smoothX = (float) mc.player.getX(); focusZ = smoothZ = (float) mc.player.getZ();
        yaw = mc.player.getYRot(); dist = targetDist = 32;
        eye = new Marker(EntityType.MARKER, mc.level);
        update(1f);
        mc.setCameraEntity(eye);
        active = true;
    }

    static void exit() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        eye = null;
    }

    static void zoom(double wheel) { targetDist = Math.max(MIN_DIST, Math.min(MAX_DIST, targetDist * (wheel > 0 ? 0.85f : 1.18f))); }

    /** Every frame: smooth zoom/pan and place the eye. */
    static void update(float partial) {
        if (eye == null) return;
        dist += (targetDist - dist) * .25f;
        smoothX += (focusX - smoothX) * .5f; smoothZ += (focusZ - smoothZ) * .5f;
        float t = (float) ((Math.log(dist) - Math.log(MIN_DIST)) / (Math.log(MAX_DIST) - Math.log(MIN_DIST)));
        pitch = 48 + 30 * t;     // closer = more perspective, far = nearly straight down (strategic view)
        double[] p = eyePos();
        eye.setPos(p[0], p[1], p[2]); eye.xo = eye.xOld = p[0]; eye.yo = eye.yOld = p[1]; eye.zo = eye.zOld = p[2];
        eye.setYRot(yaw); eye.yRotO = yaw; eye.setXRot(pitch); eye.xRotO = pitch;
    }

    static double[] eyePos() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double gy = ground(smoothX, smoothZ);
        return new double[]{smoothX - fx * dist, gy - fy * dist, smoothZ - fz * dist};
    }

    /** Pans the focus relative to the view (dx right, dz forward), in blocks scaled by zoom. */
    static void pan(float right, float forward, float dt) {
        double yr = Math.toRadians(yaw);
        float speed = (8 + dist * 1.1f) * dt;
        float fx = (float) -Math.sin(yr), fz = (float) Math.cos(yr), rx = (float) -Math.cos(yr), rz = (float) -Math.sin(yr);
        focusX += (fx * forward + rx * right) * speed; focusZ += (fz * forward + rz * right) * speed;
    }

    static boolean strategic() { return dist > 70; }
}
