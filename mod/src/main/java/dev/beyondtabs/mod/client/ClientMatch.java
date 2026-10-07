package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.Ragdoll;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.BodyDef;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.mod.Snapshot;
import java.util.HashMap;
import java.util.Map;

/** Latest two snapshots (for interpolation) plus client-side pose bodies for units sent without ragdoll detail. */
public final class ClientMatch {
    public static volatile Snapshot prev, cur; public static volatile long curAtNanos;
    static final Map<Integer, Snapshot.U> prevById = new HashMap<>();
    static final Map<Integer, Ragdoll> poseBodies = new HashMap<>();
    static float[] metalSpots = new float[0];

    public static void accept(Snapshot s) {
        prevById.clear();
        if (cur != null) for (Snapshot.U u : cur.units) prevById.put(u.id, u);
        if (s.hasSpots) metalSpots = s.metalSpots;
        prev = cur; cur = s; curAtNanos = System.nanoTime();
        if (poseBodies.size() > s.units.size() * 2 + 64) poseBodies.keySet().retainAll(s.units.stream().map(u -> u.id).toList());
        RtsScreen.pruneSelection(s);
    }

    /** Interpolation factor between the previous and current snapshot (snapshots arrive every 50 ms). */
    static float alpha() { return Math.min(1f, (System.nanoTime() - curAtNanos) / 50_000_000f); }

    static float[] pos(Snapshot.U u) {
        Snapshot.U p = prevById.get(u.id); float a = alpha();
        return p == null ? new float[]{u.x, u.z} : new float[]{p.x + (u.x - p.x) * a, p.z + (u.z - p.z) * a};
    }

    static Ragdoll poseBody(Snapshot.U u) {
        return poseBodies.computeIfAbsent(u.id, id -> {
            UnitDef d = UnitDef.ALL.get(Math.max(0, u.def));
            BodyDef b = BodyDef.byId(d.body());
            Rig rig = "humanoid".equals(d.body()) ? Rig.humanoidDefault() : Rig.blob(b.particles(), (float) b.radius() * 2.2f, (float) b.radius() * 1.6f, (float) b.mass() * 60f);
            return new Ragdoll(rig, (float) d.scale());
        });
    }

    public static void clear() { prev = cur = null; prevById.clear(); poseBodies.clear(); metalSpots = new float[0]; }
}
