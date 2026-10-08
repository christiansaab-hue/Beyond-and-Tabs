package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.engine.gen.WeaponDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Per-unit balance tuning by equal-cost duels. Every combat unit (not builders, commanders, support or siege) fights
 * every other combat unit of its tier from every faction, with the same metal on both sides, both seeds. A unit's
 * score is its average result against that whole pool; its `tune` (health x damage) is nudged until that average is
 * close to even. Counters survive (a unit can still beat some and lose to others), but no unit is simply better value.
 * Melee units are not scored against fliers they cannot reach.
 * <p>
 * usage: BalanceTune [rounds=4] [budget=1500] -> prints "TUNE id value" lines for tools/apply_tune.py
 */
public final class BalanceTune {
    static final Map<String, Double> TUNE = new HashMap<>();

    public static void main(String[] a) throws Exception {
        int rounds = a.length > 0 ? Integer.parseInt(a[0]) : 4;
        float budget = a.length > 1 ? Float.parseFloat(a[1]) : 1500;
        List<UnitDef> pool = new ArrayList<>();
        for (UnitDef d : UnitDef.ALL) {
            String r = d.role();
            if (r.equals("builder") || r.equals("commander") || r.equals("support") || r.equals("siege") || d.metal() <= 0) continue;
            pool.add(d); TUNE.put(d.id(), d.tune());
        }
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        for (int round = 1; round <= rounds; round++) {
            Map<String, double[]> sum = new HashMap<>();
            List<Future<double[]>> fs = new ArrayList<>(); List<UnitDef[]> pairs = new ArrayList<>();
            Map<String, Double> snap = new HashMap<>(TUNE);
            for (UnitDef x : pool) for (UnitDef y : pool) {
                if (x == y || x.tier() != y.tier() || x.id().compareTo(y.id()) > 0 || unreachable(x, y)) continue;
                pairs.add(new UnitDef[]{x, y});
                fs.add(ex.submit(() -> { double s = 0; for (int seed = 0; seed < 2; seed++) s += duel(x, y, budget, seed, snap); return new double[]{s / 2}; }));
            }
            for (int i = 0; i < fs.size(); i++) {
                double s = fs.get(i).get()[0]; UnitDef x = pairs.get(i)[0], y = pairs.get(i)[1];
                sum.computeIfAbsent(x.id(), k -> new double[2])[0] += s; sum.get(x.id())[1]++;
                sum.computeIfAbsent(y.id(), k -> new double[2])[0] -= s; sum.get(y.id())[1]++;
            }
            double worst = 0;
            for (UnitDef d : pool) {
                double[] v = sum.get(d.id()); if (v == null || v[1] == 0) continue;
                double mean = v[0] / v[1]; worst = Math.max(worst, Math.abs(mean));
                TUNE.put(d.id(), Math.max(.4, Math.min(3.0, TUNE.get(d.id()) * Math.exp(-.6 / Math.sqrt(round) * mean))));   // damped steps
                if (round == rounds) System.out.printf("  %-22s t%d %-8s mean %+.2f%n", d.id(), d.tier(), d.role(), mean);
            }
            System.out.printf("round %d: %d duels, worst unit mean %+.2f%n", round, fs.size(), worst);
        }
        ex.shutdown();
        for (UnitDef d : pool) System.out.printf("TUNE %s %.3f%n", d.id(), TUNE.get(d.id()));
    }

    static boolean flyer(UnitDef d) { return d.body().startsWith("flyer"); }
    static boolean melee(UnitDef d) { return "melee".equals(WeaponDef.byId(d.weaponClass()).kind()); }
    static boolean unreachable(UnitDef x, UnitDef y) { return (melee(x) && flyer(y)) || (melee(y) && flyer(x)); }

    /** Equal-metal fight; returns (value left A - value left B) / budget in [-1, 1]. */
    static double duel(UnitDef x, UnitDef y, float budget, int seed, Map<String, Double> tune) {
        World w = new World(Terrain.FLAT, seed + 7);
        w.addTeam(x.race()); w.addTeam(y.race());
        w.stats = d -> scaled(UnitStats.fallback(d), tune.getOrDefault(d.id(), d.tune()) / d.tune());
        int cx = Math.min(120, Math.max(1, Math.round(budget / x.metal()))), cy = Math.min(120, Math.max(1, Math.round(budget / y.metal())));
        float vx = cx * x.metal(), vy = cy * y.metal();
        for (int i = 0; i < cx; i++) { float px = (i % 12) * 1.6f - 9; Unit u = w.spawn(0, x, px, -16 - (i / 12) * 1.6f, 0); w.order(u, Order.attackMove(px, 30), false); }
        for (int i = 0; i < cy; i++) { float px = (i % 12) * 1.6f - 9; Unit u = w.spawn(1, y, px, 16 + (i / 12) * 1.6f, (float) Math.PI); w.order(u, Order.attackMove(px, -30), false); }
        int t = 0; while (t++ < 20 * 120 && w.aliveCount(0) > 0 && w.aliveCount(1) > 0) w.tick();
        double ha = 0, hb = 0;
        for (Unit u : w.units) if (u.alive) { if (u.team == 0) ha += u.hp / u.maxHp * x.metal(); else hb += u.hp / u.maxHp * y.metal(); }
        return ha / vx - hb / vy;   // fraction of each side's army left (budgets can differ when counts are capped)
    }

    static UnitStats scaled(UnitStats s, double k) {
        WeaponDef w = s.weapon();
        WeaponDef w2 = new WeaponDef(w.id(), w.kind(), w.range(), (int) Math.round(w.damage() * k), w.cooldown(), w.aoe(), w.knockback(), w.projectile(), w.gravity(), w.count(), w.speed());
        return new UnitStats((float) (s.hp() * k), s.mass(), s.speedMult(), s.size(), w2);
    }
}
