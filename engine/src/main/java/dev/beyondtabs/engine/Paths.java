package dev.beyondtabs.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Pathfinding around buildings. Units walk straight when nothing is in the way (the common case, free); only when a
 * building footprint blocks the straight line is an 8-direction A* run on a half-block grid around the obstacles, and the
 * resulting waypoints are smoothed. The search area is bounded, so a path costs well under a millisecond.
 */
final class Paths {
    static final float CELL = .5f; static final int MAX_NODES = 12000;

    /** True if the segment (ax,az)-(bx,bz), widened by r, crosses any alive building except `ignore`. */
    static boolean blocked(List<Building> buildings, float ax, float az, float bx, float bz, float r, Building ignore) {
        for (Building b : buildings) {
            if (!b.alive || b == ignore) continue;
            if (segRect(ax, az, bx, bz, b.x - b.hw - r, b.z - b.hh - r, b.x + b.hw + r, b.z + b.hh + r)) return true;
        }
        return false;
    }

    static boolean segRect(float ax, float az, float bx, float bz, float x0, float z0, float x1, float z1) {
        float t0 = 0, t1 = 1, dx = bx - ax, dz = bz - az;
        float[] p = {-dx, dx, -dz, dz}, q = {ax - x0, x1 - ax, az - z0, z1 - az};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return false; continue; }
            float t = q[i] / p[i];
            if (p[i] < 0) { if (t > t1) return false; if (t > t0) t0 = t; } else { if (t < t0) return false; if (t < t1) t1 = t; }
        }
        return true;
    }

    /** Waypoints from (sx,sz) to (gx,gz) avoiding buildings, or null if no path within the search area. */
    static float[] find(List<Building> buildings, float sx, float sz, float gx, float gz, float r, Building ignore) {
        float minX = Math.min(sx, gx) - 16, minZ = Math.min(sz, gz) - 16, maxX = Math.max(sx, gx) + 16, maxZ = Math.max(sz, gz) + 16;
        int w = (int) Math.ceil((maxX - minX) / CELL), h = (int) Math.ceil((maxZ - minZ) / CELL);
        if ((long) w * h > 400_000) return null;
        boolean[] solid = new boolean[w * h];
        for (Building b : buildings) {
            if (!b.alive || b == ignore) continue;
            int x0 = Math.max(0, (int) ((b.x - b.hw - r - minX) / CELL)), x1 = Math.min(w - 1, (int) ((b.x + b.hw + r - minX) / CELL));
            int z0 = Math.max(0, (int) ((b.z - b.hh - r - minZ) / CELL)), z1 = Math.min(h - 1, (int) ((b.z + b.hh + r - minZ) / CELL));
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) solid[z * w + x] = true;
        }
        int s = idx(sx, sz, minX, minZ, w, h), g = idx(gx, gz, minX, minZ, w, h);
        if (s < 0 || g < 0) return null;
        solid[s] = false;
        if (solid[g]) g = nearestFree(solid, g, w, h);
        if (g < 0) return null;
        float[] cost = new float[w * h]; Arrays.fill(cost, Float.MAX_VALUE); int[] from = new int[w * h]; Arrays.fill(from, -1);
        PriorityQueue<float[]> open = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        cost[s] = 0; open.add(new float[]{0, s});
        int gxC = g % w, gzC = g / w, expanded = 0;
        int[] DX = {1, -1, 0, 0, 1, 1, -1, -1}, DZ = {0, 0, 1, -1, 1, -1, 1, -1};
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            int c = (int) open.poll()[1];
            if (c == g) break;
            int cx = c % w, cz = c / w;
            for (int k = 0; k < 8; k++) {
                int nx = cx + DX[k], nz = cz + DZ[k];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                int n = nz * w + nx;
                if (solid[n] || (k >= 4 && (solid[cz * w + nx] || solid[nz * w + cx]))) continue;   // no corner cutting
                float nc = cost[c] + (k < 4 ? 1f : 1.4142f);
                if (nc < cost[n]) {
                    cost[n] = nc; from[n] = c;
                    float hx = Math.abs(nx - gxC), hz = Math.abs(nz - gzC);
                    open.add(new float[]{nc + Math.max(hx, hz) + .4142f * Math.min(hx, hz), n});
                }
            }
        }
        if (from[g] < 0 && g != s) return null;
        List<float[]> pts = new ArrayList<>();
        for (int c = g; c != -1; c = from[c]) pts.add(new float[]{minX + (c % w + .5f) * CELL, minZ + (c / w + .5f) * CELL});
        java.util.Collections.reverse(pts);
        pts.set(pts.size() - 1, new float[]{gx, gz});
        // string-pull: keep only the waypoints needed to stay clear of buildings
        List<float[]> out = new ArrayList<>(); float[] cur = {sx, sz}; int i = 0;
        while (i < pts.size()) {
            int j = pts.size() - 1;
            while (j > i && blocked(buildings, cur[0], cur[1], pts.get(j)[0], pts.get(j)[1], r * .9f, ignore)) j--;
            cur = pts.get(j); out.add(cur); i = j + 1;
        }
        float[] flat = new float[out.size() * 2];
        for (int k = 0; k < out.size(); k++) { flat[k * 2] = out.get(k)[0]; flat[k * 2 + 1] = out.get(k)[1]; }
        return flat;
    }

    static int idx(float x, float z, float minX, float minZ, int w, int h) {
        int cx = (int) ((x - minX) / CELL), cz = (int) ((z - minZ) / CELL);
        return cx < 0 || cz < 0 || cx >= w || cz >= h ? -1 : cz * w + cx;
    }
    static int nearestFree(boolean[] solid, int g, int w, int h) {
        int gx = g % w, gz = g / w;
        for (int rad = 1; rad < 40; rad++)
            for (int dz = -rad; dz <= rad; dz++) for (int dx = -rad; dx <= rad; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) continue;
                int x = gx + dx, z = gz + dz;
                if (x >= 0 && z >= 0 && x < w && z < h && !solid[z * w + x]) return z * w + x;
            }
        return -1;
    }
}
