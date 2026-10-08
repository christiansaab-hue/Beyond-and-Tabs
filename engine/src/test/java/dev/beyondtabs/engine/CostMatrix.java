package dev.beyondtabs.engine;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.*;
/** Equal-metal fights between every non-builder unit and a reference mix; prints value-for-money per unit. */
public final class CostMatrix {
    public static void main(String[] a) {
        List<UnitDef> all = new ArrayList<>();
        for (UnitDef d : UnitDef.ALL) if (!d.role().equals("builder") && !d.role().equals("commander") && d.metal() > 0) all.add(d);
        UnitDef[] refs = {UnitDef.KD_SQUIRE, UnitDef.AW_CLUBBER, UnitDef.KD_ARCHER, UnitDef.AW_SPEAR_THROWER};
        float budget = 1200;
        Map<String, Float> score = new TreeMap<>();
        for (UnitDef x : all) {
            float total = 0; int n = 0;
            for (UnitDef ref : refs) {
                if (ref == x) continue;
                for (int seed = 0; seed < 2; seed++) {
                    World w = new World(Terrain.FLAT, seed + 7); w.addTeam(x.race()); w.addTeam(ref.race().equals(x.race()) ? (x.race().equals("kingdoms") ? "ancient_world" : "kingdoms") : ref.race());
                    int cx = Math.max(1, Math.round(budget / x.metal())), cr = Math.max(1, Math.round(budget / ref.metal()));
                    cx = Math.min(cx, 40); cr = Math.min(cr, 40);
                    for (int i = 0; i < cx; i++) { float px = (i % 8) * 2f - 7; Unit u = w.spawn(0, x, px, -16 - (i / 8) * 2f, 0); w.order(u, Order.attackMove(px, 30), false); }
                    for (int i = 0; i < cr; i++) { float px = (i % 8) * 2f - 7; Unit u = w.spawn(1, ref, px, 16 + (i / 8) * 2f, (float) Math.PI); w.order(u, Order.attackMove(px, -30), false); }
                    int t = 0; while (t++ < 20 * 120 && w.aliveCount(0) > 0 && w.aliveCount(1) > 0) w.tick();
                    float ha = 0, hb = 0;
                    for (Unit u : w.units) if (u.alive) { if (u.team == 0) ha += u.hp / u.maxHp * x.metal(); else hb += u.hp / u.maxHp * ref.metal(); }
                    total += (ha - hb) / budget; n++;
                }
            }
            score.put(String.format("%-22s %4dm", x.id(), x.metal()), total / n);
        }
        score.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e -> System.out.printf("%s  %+.2f%n", e.getKey(), e.getValue()));
    }
}
