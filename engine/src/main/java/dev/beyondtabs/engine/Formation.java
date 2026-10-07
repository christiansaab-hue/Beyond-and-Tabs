package dev.beyondtabs.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where each unit of a group should stand when moved together (BAR-style): shields and spears in the front rank,
 * infantry and cavalry behind them, archers, casters and siege at the back; slots are handed out left to right in
 * the order units already stand, so nobody crosses through the group (no tripping over friends).
 * Either a block facing the move direction, or a line drawn by right-dragging.
 */
public final class Formation {
    private Formation() { }

    static int rank(Unit u) {
        return switch (u.cls) {
            case "shield" -> 0; case "reach" -> 1; case "infantry", "large" -> 2; case "cavalry" -> 3;
            case "ranged", "flyer" -> 4; case "support" -> 5; default -> 6;
        };
    }

    /** Targets for a block formation centred on (tx, tz). Returns {x, z} per unit in the given order. */
    public static float[][] block(List<Unit> units, float tx, float tz) {
        int n = units.size();
        float[][] out = new float[n][];
        if (n == 0) return out;
        float cx = 0, cz = 0; for (Unit u : units) { cx += u.x; cz += u.z; } cx /= n; cz /= n;
        float fx = tx - cx, fz = tz - cz, fl = (float) Math.sqrt(fx * fx + fz * fz);
        if (fl < 2) { float yaw = units.get(0).yaw; fx = (float) Math.sin(yaw); fz = (float) Math.cos(yaw); } else { fx /= fl; fz /= fl; }
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 2.2)));
        float sp = spacing(units);
        return place(units, cx, cz, tx, tz, fx, fz, cols, sp, sp, true);
    }

    /** Targets along a line from (x0,z0) to (x1,z1): the front rank stands on the line, the rest behind it. */
    public static float[][] line(List<Unit> units, float x0, float z0, float x1, float z1) {
        int n = units.size();
        if (n == 0) return new float[0][];
        float cx = 0, cz = 0; for (Unit u : units) { cx += u.x; cz += u.z; } cx /= n; cz /= n;
        float lx = x1 - x0, lz = z1 - z0, len = Math.max(.01f, (float) Math.sqrt(lx * lx + lz * lz));
        float fx = lz / len, fz = -lx / len;                                  // perpendicular to the line...
        float mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        if ((mx - cx) * fx + (mz - cz) * fz < 0) { fx = -fx; fz = -fz; }      // ...facing away from where the group is
        float sp = spacing(units);
        int cols = Math.max(1, Math.min(n, (int) (len / sp) + 1));
        float colSp = cols > 1 ? Math.max(sp, len / (cols - 1)) : sp;   // spread over the whole line
        return place(units, cx, cz, mx, mz, fx, fz, cols, sp, colSp, false);
    }

    static float spacing(List<Unit> units) {
        float r = 0; for (Unit u : units) r = Math.max(r, u.radius);
        return Math.max(1.3f, r * 2 + .55f);
    }

    /** Ranks of `cols` units facing (fx, fz); centred on the target, or (line) with the front rank on it. */
    static float[][] place(List<Unit> units, float cx, float cz, float tx, float tz, float fx, float fz, int cols, float rowSp, float colSp, boolean centred) {
        int n = units.size();
        float rx = fz, rz = -fx;   // to the right of the facing
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> rank(units.get(i))));
        float[][] out = new float[n][];
        int rows = (n + cols - 1) / cols;
        for (int r = 0; r < rows; r++) {
            int from = r * cols, to = Math.min(n, from + cols), k = to - from;
            List<Integer> row = new ArrayList<>(order.subList(from, to));
            final float frx = rx, frz = rz;
            row.sort(Comparator.comparingDouble(i -> (units.get(i).x - cx) * frx + (units.get(i).z - cz) * frz));   // keep left-to-right order
            float back = -(centred ? r - (rows - 1) / 2f : r) * rowSp;
            for (int j = 0; j < k; j++) {
                float side = (j - (k - 1) / 2f) * colSp;
                out[row.get(j)] = new float[]{tx + rx * side + fx * back, tz + rz * side + fz * back};
            }
        }
        return out;
    }
}
