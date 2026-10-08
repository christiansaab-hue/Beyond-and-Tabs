package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lays out a skirmish: start positions around a circle, metal spots (a ring at every base, flank and forward
 * expansions, contested fields between neighbours and in the middle), one commander per player, and an AI for every AI player.
 * Works for 2 to 8 players, free-for-all or in alliances.
 */
public final class Skirmish {
    private Skirmish() { }

    /** position: 0..positions-1 around the circle, or -1 to be placed automatically. ai: null for a human. */
    public record Player(String race, int alliance, int position, Ai.Difficulty ai) { }

    /** Result: the team id and start point of every player, in the order given. */
    public record Start(int team, float x, float z) { }

    public static List<Start> setup(World w, float cx, float cz, float radius, int positions, List<Player> players, long seed) {
        Random rng = new Random(seed);
        int n = players.size();
        positions = Math.max(positions, n);
        boolean[] taken = new boolean[positions];
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            int p = players.get(i).position();
            if (p >= 0 && p < positions && !taken[p]) { pos[i] = p; taken[p] = true; } else pos[i] = -1;
        }
        for (int i = 0; i < n; i++) {   // automatic: the free position furthest from everyone already placed
            if (pos[i] >= 0) continue;
            int best = -1; float bestD = -1;
            for (int p = 0; p < positions; p++) {
                if (taken[p]) continue;
                float d = Float.MAX_VALUE;
                for (int q = 0; q < positions; q++) if (taken[q]) d = Math.min(d, ring(p, q, positions));
                if (d == Float.MAX_VALUE) d = rng.nextFloat();   // nobody placed yet: any position
                if (d > bestD) { bestD = d; best = p; }
            }
            pos[i] = best; taken[best] = true;
        }
        List<Start> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Player pl = players.get(i);
            Team t = w.addTeam(pl.race()); t.alliance = pl.alliance();
            double ang = 2 * Math.PI * pos[i] / positions;
            float bx = cx + (float) Math.sin(ang) * radius, bz = cz + (float) Math.cos(ang) * radius;
            float[] base = dry(w, bx, bz, cx, cz);
            bx = base[0]; bz = base[1];
            // metal: a ring around the base (two distances), then an expansion pair towards the middle
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4 + .3 - ang; float r = 13 + (k % 2) * 6;   // rotated with the base: every start is a mirror image
                addSpot(w, bx + (float) Math.cos(a) * r, bz + (float) Math.sin(a) * r);
            }
            // BAR-style map economy: safe metal at home, then fields worth fighting for further out
            float ux = (cx - bx) / radius, uz = (cz - bz) / radius, sx = -uz, sz = ux;   // towards the middle, and sideways
            for (float side : new float[]{-1, 1}) {
                cluster(w, bx + ux * radius * .3f + sx * side * radius * .32f, bz + uz * radius * .3f + sz * side * radius * .32f, 3, 4.5f, ang);   // flank expansions
                cluster(w, bx + ux * radius * .55f + sx * side * 6, bz + uz * radius * .55f + sz * side * 6, 2, 3.5f, ang);              // forward pair
            }
            UnitDef cmd = commanderOf(pl.race());
            w.spawn(t.id, cmd, bx + (cx - bx) / radius * 4, bz + (cz - bz) / radius * 4, (float) Math.atan2(cx - bx, cz - bz));
            if (pl.ai() != null) w.ais.add(new Ai(w, t.id, pl.ai()));
            out.add(new Start(t.id, bx, bz));
        }
        for (int k = 0; k < 6; k++) { double a = k * Math.PI / 3 + Math.PI / 6; addSpot(w, cx + (float) Math.cos(a) * 9, cz + (float) Math.sin(a) * 9); }   // the contested middle
        for (int p = 0; p < positions; p++) {   // between every two neighbouring starts: a field both sides will want
            double a = 2 * Math.PI * (p + .5) / positions;
            cluster(w, cx + (float) Math.sin(a) * radius * .78f, cz + (float) Math.cos(a) * radius * .78f, 4, 5, a);
        }
        return out;
    }

    /** Steps between two positions around the ring (1 = neighbours). */
    static float ring(int a, int b, int n) { int d = Math.abs(a - b); return Math.min(d, n - d); }

    static UnitDef commanderOf(String race) {
        for (UnitDef d : UnitDef.ALL) if (d.race().equals(race) && d.role().equals("commander")) return d;
        throw new IllegalArgumentException("race " + race + " has no commander");
    }

    /** Moves a point off deep water, towards the middle of the map, so nobody starts in a lake. */
    /** Nearest spot to (x, z) where the whole base footprint area is dry land (spiral search), else nudged inland. */
    static float[] dry(World w, float x, float z, float cx, float cz) {
        if (dryArea(w, x, z)) return new float[]{x, z};
        for (int r = 4; r <= 40; r += 4)
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                float px = x + (float) Math.cos(a) * r, pz = z + (float) Math.sin(a) * r;
                if (dryArea(w, px, pz)) return new float[]{px, pz};
            }
        for (int i = 0; i < 20 && w.terrain.waterDepth(x, z) > .6f; i++) { x += (cx - x) * .1f; z += (cz - z) * .1f; }
        return new float[]{x, z};
    }

    /** No water within 14 blocks (the core of a base). */
    static boolean dryArea(World w, float x, float z) {
        for (int dx = -14; dx <= 14; dx += 7) for (int dz = -14; dz <= 14; dz += 7) if (w.terrain.waterDepth(x + dx, z + dz) > .2f) return false;
        return true;
    }

    /** n spots around (x, z), spaced `r` apart. */
    static void cluster(World w, float x, float z, int n, float r, double rot) {
        if (n == 1) { addSpot(w, x, z); return; }
        for (int i = 0; i < n; i++) { double a = rot + i * 2 * Math.PI / n; addSpot(w, x + (float) Math.cos(a) * r * .6f, z + (float) Math.sin(a) * r * .6f); }
    }

    static void addSpot(World w, float x, float z) {
        if (w.terrain.waterDepth(x, z) > .2f) {   // extractors only on dry land: move to the nearest shore instead of losing the spot
            float[] d = null;
            search:
            for (int r = 2; r <= 12; r += 2) for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6; float px = x + (float) Math.cos(a) * r, pz = z + (float) Math.sin(a) * r;
                if (w.terrain.waterDepth(px, pz) <= .2f) { d = new float[]{px, pz}; break search; }
            }
            if (d == null) return;
            x = d[0]; z = d[1];
        }
        for (float[] s : w.metalSpots) if (Math.abs(s[0] - x) < 3 && Math.abs(s[1] - z) < 3) return;
        w.metalSpots.add(new float[]{x, z});
    }
}
