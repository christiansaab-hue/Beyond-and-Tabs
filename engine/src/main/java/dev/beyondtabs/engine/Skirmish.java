package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lays out a skirmish: start positions around a circle, metal spots (a ring at every base, a contested cluster in the
 * middle and an expansion pair on the way there), one commander per player, and an AI for every AI player.
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
                double a = k * Math.PI / 4 + .3; float r = 13 + (k % 2) * 6;
                addSpot(w, bx + (float) Math.cos(a) * r, bz + (float) Math.sin(a) * r);
            }
            float mx = bx + (cx - bx) * .55f, mz = bz + (cz - bz) * .55f, px = -(cz - bz) / radius * 5, pz = (cx - bx) / radius * 5;
            addSpot(w, mx + px, mz + pz); addSpot(w, mx - px, mz - pz);
            UnitDef cmd = commanderOf(pl.race());
            w.spawn(t.id, cmd, bx + (cx - bx) / radius * 4, bz + (cz - bz) / radius * 4, (float) Math.atan2(cx - bx, cz - bz));
            if (pl.ai() != null) w.ais.add(new Ai(w, t.id, pl.ai()));
            out.add(new Start(t.id, bx, bz));
        }
        for (int k = 0; k < 4; k++) { double a = k * Math.PI / 2 + Math.PI / 4; addSpot(w, cx + (float) Math.cos(a) * 8, cz + (float) Math.sin(a) * 8); }
        return out;
    }

    /** Steps between two positions around the ring (1 = neighbours). */
    static float ring(int a, int b, int n) { int d = Math.abs(a - b); return Math.min(d, n - d); }

    static UnitDef commanderOf(String race) {
        for (UnitDef d : UnitDef.ALL) if (d.race().equals(race) && d.role().equals("commander")) return d;
        throw new IllegalArgumentException("race " + race + " has no commander");
    }

    /** Moves a point off deep water, towards the middle of the map, so nobody starts in a lake. */
    static float[] dry(World w, float x, float z, float cx, float cz) {
        for (int i = 0; i < 20 && w.terrain.waterDepth(x, z) > .6f; i++) { x += (cx - x) * .1f; z += (cz - z) * .1f; }
        return new float[]{x, z};
    }

    static void addSpot(World w, float x, float z) {
        if (w.terrain.waterDepth(x, z) > .6f) return;
        for (float[] s : w.metalSpots) if (Math.abs(s[0] - x) < 3 && Math.abs(s[1] - z) < 3) return;
        w.metalSpots.add(new float[]{x, z});
    }
}
