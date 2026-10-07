package dev.beyondtabs.mod.client;

import net.minecraft.client.Minecraft;

/** Screen <-> world for the RTS camera (same eye, rotation and 70° vertical FOV that the world is rendered with). */
final class Proj {
    static final double FOV = 70;
    double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, aspect; int gw, gh;

    static Proj now() {
        Proj p = new Proj(); Minecraft mc = Minecraft.getInstance();
        double[] e = RtsCamera.eyePos(); p.ex = e[0]; p.ey = e[1]; p.ez = e[2];
        double yr = Math.toRadians(RtsCamera.yaw), pr = Math.toRadians(RtsCamera.pitch);
        p.fx = -Math.sin(yr) * Math.cos(pr); p.fy = -Math.sin(pr); p.fz = Math.cos(yr) * Math.cos(pr);
        p.rx = -Math.cos(yr); p.rz = -Math.sin(yr);
        // up = right x forward
        p.ux = 0 * p.fz - p.rz * p.fy; p.uy = p.rz * p.fx - p.rx * p.fz; p.uz = p.rx * p.fy - 0 * p.fx;
        p.gw = mc.getWindow().getGuiScaledWidth(); p.gh = mc.getWindow().getGuiScaledHeight();
        p.aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        p.tanHalf = Math.tan(Math.toRadians(FOV / 2));
        return p;
    }

    /** World point to GUI coordinates, or null if behind the camera. */
    float[] toScreen(double x, double y, double z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < .1) return null;
        double cx = dx * rx + dz * rz, cy = dx * ux + dy * uy + dz * uz;
        double nx = cx / cz / (tanHalf * aspect), ny = cy / cz / tanHalf;
        return new float[]{(float) ((nx + 1) / 2 * gw), (float) ((1 - ny) / 2 * gh)};
    }

    /** GUI coordinates to the point on the ground under the cursor (ray-marched against the terrain), or null. */
    float[] toGround(double mx, double my) {
        double nx = mx / gw * 2 - 1, ny = 1 - my / gh * 2;
        double dx = fx + rx * nx * tanHalf * aspect + ux * ny * tanHalf, dy = fy + uy * ny * tanHalf, dz = fz + rz * nx * tanHalf * aspect + uz * ny * tanHalf;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz); dx /= l; dy /= l; dz /= l;
        double step = Math.max(.25, RtsCamera.dist / 200);
        double px = ex, py = ey, pz = ez;
        for (int i = 0; i < 4000; i++) {
            double gy = RtsCamera.ground((float) px, (float) pz);
            if (py <= gy) {   // refine between the last two samples
                double lo = 0, hi = step;
                for (int k = 0; k < 8; k++) { double mid = (lo + hi) / 2; double qx = px - dx * mid, qy = py - dy * mid, qz = pz - dz * mid;
                    if (qy <= RtsCamera.ground((float) qx, (float) qz)) lo = mid; else hi = mid; }
                return new float[]{(float) (px - dx * lo), (float) gy, (float) (pz - dz * lo)};
            }
            px += dx * step; py += dy * step; pz += dz * step;
        }
        return null;
    }
}
