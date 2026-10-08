package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.RaceDef;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayList;
import java.util.List;

/**
 * Faction army battles: each faction's tier-n army at equal metal, composed the way the AI does it (45% ranged by
 * value, the rest split evenly over its front-line units), front line ahead of the ranged. Isolates combined-arms
 * strength from economy and AI behaviour. usage: ArmyBattle [tier=1] [budget=3000] [seeds=12]
 */
public final class ArmyBattle {
    static java.util.Map<String, Double> override;   // unit id -> tune (tuning mode)

    public static void main(String[] a) {
        if (a.length > 0 && a[0].equals("tune")) { tune(a.length > 1 ? Integer.parseInt(a[1]) : 8); return; }
        int tier = a.length > 0 ? Integer.parseInt(a[0]) : 1;
        float budget = a.length > 1 ? Float.parseFloat(a[1]) : 3000;
        int seeds = a.length > 2 ? Integer.parseInt(a[2]) : 12;
        List<String> races = new ArrayList<>();
        for (RaceDef r : RaceDef.ALL) if (r.firstPlayable()) races.add(r.id());
        for (int i = 0; i < races.size(); i++) for (int j = i + 1; j < races.size(); j++) {
            double score = 0; int wins = 0;
            for (int s = 0; s < seeds; s++) {
                boolean swap = s % 2 == 1;
                double r = battle(swap ? races.get(j) : races.get(i), swap ? races.get(i) : races.get(j), tier, budget, s);
                if (swap) r = -r;
                score += r; if (r > 0) wins++;
            }
            System.out.printf("tier %d  %-14s vs %-14s  %s wins %d/%d, mean margin %+.2f%n", tier, races.get(i), races.get(j), races.get(i), wins, seeds, score / seeds);
        }
    }

    static List<UnitDef> army(String race, int tier, float budget) {
        List<UnitDef> ranged = new ArrayList<>(), front = new ArrayList<>();
        for (UnitDef d : UnitDef.ALL) {
            if (!d.race().equals(race) || d.tier() > tier || d.tier() < 1) continue;
            String r = d.role();
            if (r.equals("builder") || r.equals("commander") || r.equals("support") || r.equals("siege") || d.body().startsWith("flyer")) continue;
            if (d.tier() != tier && tier > 1 && d.tier() < tier - 1) continue;
            (r.equals("ranged") ? ranged : front).add(d);
        }
        List<UnitDef> out = new ArrayList<>();
        float rb = budget * .45f, fb = budget - rb;
        for (UnitDef d : ranged) for (float v = 0; v + d.metal() <= rb / ranged.size() + d.metal() * .5f; v += d.metal()) out.add(d);
        for (UnitDef d : front) for (float v = 0; v + d.metal() <= fb / front.size() + d.metal() * .5f; v += d.metal()) out.add(d);
        return out;
    }

    /** Returns value left A - value left B as a fraction of each side's army (positive: A won). */
    static double battle(String ra, String rb, int tier, float budget, int seed) {
        World w = new World(Terrain.FLAT, seed + 31);
        w.addTeam(ra); w.addTeam(rb);
        if (override != null) { var o = override; w.stats = d -> BalanceTune.scaled(UnitStats.fallback(d), o.getOrDefault(d.id(), d.tune()) / d.tune()); }
        double[] total = new double[2];
        List<UnitDef>[] armies = new List[]{army(ra, tier, budget), army(rb, tier, budget)};
        for (int side = 0; side < 2; side++) {
            int fi = 0, ri = 0;
            for (UnitDef d : armies[side]) {
                boolean rng = d.role().equals("ranged");
                int i = rng ? ri++ : fi++;
                float px = (i % 14) * 1.5f - 10, row = (i / 14) * 1.5f + (rng ? 6 : 0);
                float z = side == 0 ? -14 - row : 14 + row;
                Unit u = w.spawn(side, d, px, z, side == 0 ? 0 : (float) Math.PI);
                w.order(u, Order.attackMove(px, side == 0 ? 40 : -40), false);
                total[side] += d.metal();
            }
        }
        int t = 0; while (t++ < 20 * 150 && w.aliveCount(0) > 0 && w.aliveCount(1) > 0) w.tick();
        double[] left = new double[2];
        for (Unit u : w.units) if (u.alive) left[u.team] += u.hp / u.maxHp * u.def.metal();
        return left[0] / total[0] - left[1] / total[1];
    }

    /** Faction-tier multipliers on top of the per-unit tunes, until every faction's mixed army breaks even per tier. */
    static void tune(int rounds) {
        List<String> races = new ArrayList<>();
        for (RaceDef r : RaceDef.ALL) if (r.firstPlayable()) races.add(r.id());
        override = new java.util.HashMap<>();
        for (UnitDef d : UnitDef.ALL) override.put(d.id(), d.tune());
        for (int round = 1; round <= rounds; round++) {
            double worst = 0;
            for (int tier = 1; tier <= 3; tier++) {
                double[] mean = new double[races.size()]; int[] n = new int[races.size()];
                for (int i = 0; i < races.size(); i++) for (int j = i + 1; j < races.size(); j++) for (int s = 0; s < 8; s++) {
                    boolean sw = s % 2 == 1;
                    double r = battle(sw ? races.get(j) : races.get(i), sw ? races.get(i) : races.get(j), tier, 3000, s + round * 100);
                    if (sw) r = -r;
                    mean[i] += r; n[i]++; mean[j] -= r; n[j]++;
                }
                for (int i = 0; i < races.size(); i++) {
                    double m = mean[i] / Math.max(1, n[i]); worst = Math.max(worst, Math.abs(m));
                    double f = Math.exp(-.5 / Math.sqrt(round) * m);
                    for (UnitDef d : UnitDef.ALL) if (d.race().equals(races.get(i)) && d.tier() == tier && !d.role().equals("builder") && !d.role().equals("commander"))
                        override.put(d.id(), Math.max(.35, Math.min(3.2, override.get(d.id()) * f)));
                    System.out.printf("  round %d tier %d %-14s mean %+.2f%n", round, tier, races.get(i), m);
                }
            }
            System.out.printf("round %d worst faction-tier mean %+.2f%n", round, worst);
        }
        for (UnitDef d : UnitDef.ALL) if (Math.abs(override.get(d.id()) - d.tune()) > 1e-4) System.out.printf("TUNE %s %.3f%n", d.id(), override.get(d.id()));
    }
}
