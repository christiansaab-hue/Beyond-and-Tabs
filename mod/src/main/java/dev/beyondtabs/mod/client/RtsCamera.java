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
    public static float focusX, focusZ, dist = 26, yaw, pitch = 58;   // yaw/pitch in degrees, Minecraft convention
    static float targetDist = 26, smoothX, smoothZ;
    /** Player tilt on top of the zoom-based pitch (BAR: Alt+wheel / Alt+middle-drag). Negative = toward the horizon. */
    public static float tilt, targetTilt;
    static final float MIN_PITCH = 14, MAX_PITCH = 89;
    static Marker eye;
    /** Camera shake from nearby blasts (blocks of jitter), decaying quickly. */
    static float shake; static long lastNanos;
    static void kick(float a) { shake = Math.min(1.2f, shake + a); }
    static final float MIN_DIST = 7, MAX_DIST = 150;   // far end stays under the clouds and inside the fog

    static float ground(float x, float z) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 64 : mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    static final net.minecraft.core.BlockPos.MutableBlockPos FP = new net.minecraft.core.BlockPos.MutableBlockPos();

    /** Where units stand: the ground, or in water the riverbed but never deeper than LevelTerrain.WADE (they swim). */
    static float floor(float x, float z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 64;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), y = surface, min = surface - 8;
        while (y > min && !mc.level.getFluidState(FP.set(bx, y - 1, bz)).isEmpty()) y--;
        return Math.max(y, surface - dev.beyondtabs.mod.LevelTerrain.WADE);
    }

    static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        focusX = smoothX = (float) mc.player.getX(); focusZ = smoothZ = (float) mc.player.getZ();
        yaw = mc.player.getYRot(); dist = targetDist = 26;
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

    /** Tilt toward the horizon (positive degrees) or back toward top-down. */
    static void tiltBy(float deg) { targetTilt -= deg; }
    static void resetView() { targetTilt = 0; }

    static void zoom(double wheel) { zoomAt(wheel, null); }

    /** As far out as the loaded world reaches (render distance), never past MAX_DIST. */
    static float maxDist() {
        int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        return Math.max(40, Math.min(MAX_DIST, chunks * 16 * .85f));
    }

    /** BAR-style: zoom toward the point under the cursor (that ground point stays under the cursor). */
    static void zoomAt(double wheel, float[] cursorGround) {
        float old = targetDist;
        targetDist = Math.max(MIN_DIST, Math.min(maxDist(), targetDist * (wheel > 0 ? 0.84f : 1.19f)));
        if (cursorGround != null && wheel > 0) {
            float k = 1 - targetDist / old;
            focusX += (cursorGround[0] - focusX) * k; focusZ += (cursorGround[2] - focusZ) * k;
        }
    }

    /** Moves the focus by (right, forward) blocks in the view's frame (middle-drag grab). */
    static void move(float right, float forward) {
        double yr = Math.toRadians(yaw);
        float fx = (float) -Math.sin(yr), fz = (float) Math.cos(yr), rx = (float) -Math.cos(yr), rz = (float) -Math.sin(yr);
        focusX += fx * forward + rx * right; focusZ += fz * forward + rz * right;
        smoothX += fx * forward + rx * right; smoothZ += fz * forward + rz * right;   // grabbing follows the hand exactly
    }

    /** Every frame: smooth zoom/pan and place the eye. */
    static void update(float partial) {
        if (eye == null) return;
        long nowN = System.nanoTime(); float dt = lastNanos == 0 ? 1 : Math.min(.1f, (nowN - lastNanos) / 1e9f); lastNanos = nowN;
        float zk = 1 - (float) Math.exp(-dt * 12), pk = 1 - (float) Math.exp(-dt * 18);   // same feel at any frame rate
        dist += (targetDist - dist) * zk;
        smoothX += (focusX - smoothX) * pk; smoothZ += (focusZ - smoothZ) * pk;
        float t = (float) ((Math.log(dist) - Math.log(MIN_DIST)) / (Math.log(MAX_DIST) - Math.log(MIN_DIST)));
        tilt += (targetTilt - tilt) * zk;
        float base = 56 + 30 * t * t;   // BAR: an angled view up close that turns to near top-down as you zoom out
        pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, base + tilt));
        targetTilt = Math.max(MIN_PITCH - base, Math.min(MAX_PITCH - base, targetTilt));
        double[] p = eyePos();
        if (shake > .002f) {
            float tt = nowN / 1e9f, j = shake * (.6f + dist / 120f);
            p[0] += Math.sin(tt * 53) * j * .5; p[1] += Math.sin(tt * 61 + 1) * j * .35; p[2] += Math.cos(tt * 47) * j * .5;
            shake *= (float) Math.pow(.015, dt);
        } else shake = 0;
        eye.setPos(p[0], p[1], p[2]); eye.xo = eye.xOld = p[0]; eye.yo = eye.yOld = p[1]; eye.zo = eye.zOld = p[2];
        eye.setYRot(yaw); eye.yRotO = yaw; eye.setXRot(pitch); eye.xRotO = pitch;
    }

    static double[] eyePos() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double gy = ground(smoothX, smoothZ);
        double ex = smoothX - fx * dist, ey = gy - fy * dist, ez = smoothZ - fz * dist;
        ey = Math.max(ey, ground((float) ex, (float) ez) + 2.5);   // a low, tilted view never dips into hills
        if (ey > 182) { double k = (182 - gy) / Math.max(1, ey - gy); ex = smoothX - fx * dist * k; ez = smoothZ - fz * dist * k; ey = 182; }   // below the clouds
        return new double[]{ex, ey, ez};
    }

    /** Pans the focus relative to the view (dx right, dz forward), in blocks scaled by zoom. */
    static void pan(float right, float forward, float dt) {
        double yr = Math.toRadians(yaw);
        float speed = (6 + dist * 1.25f) * dt;   // BAR: scroll speed grows with height
        float fx = (float) -Math.sin(yr), fz = (float) Math.cos(yr), rx = (float) -Math.cos(yr), rz = (float) -Math.sin(yr);
        focusX += (fx * forward + rx * right) * speed; focusZ += (fz * forward + rz * right) * speed;
    }

    static boolean strategic() { return dist > 95; }
}
