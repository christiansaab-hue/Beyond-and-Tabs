package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.Ragdoll;
import dev.beyondtabs.engine.Rig;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.mod.Snapshot;
import java.util.HashMap;
import java.util.Map;

/** Latest two snapshots (for interpolation) plus client-side pose bodies for units sent without ragdoll detail. */
public final class ClientMatch {
    public static volatile Snapshot prev, cur; public static volatile long curAtNanos;
    static final Map<Integer, Snapshot.U> prevById = new HashMap<>();
    static final Map<Integer, Ragdoll> poseBodies = new HashMap<>();

    public static void accept(Snapshot s) {
        prevById.clear();
        if (cur != null) for (Snapshot.U u : cur.units) prevById.put(u.id, u);
        prev = cur; cur = s; curAtNanos = System.nanoTime();
        if (poseBodies.size() > s.units.size() * 2 + 64) poseBodies.keySet().retainAll(s.units.stream().map(u -> u.id).toList());
    }

    static Ragdoll poseBody(Snapshot.U u) {
        return poseBodies.computeIfAbsent(u.id, id -> {
            UnitDef d = UnitDef.ALL.get(Math.max(0, u.def));
            Rig rig = "humanoid".equals(d.body()) ? Rig.humanoidDefault() : Rig.blob(6, 1.5f, 1.2f, 100);
            return new Ragdoll(rig, (float) (d.size() * d.scale()));
        });
    }

    public static void clear() { prev = cur = null; prevById.clear(); poseBodies.clear(); }
}
