package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.RaceDef;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Balance tournament: hard AIs play complete 1v1 matches for every pair of playable factions (and mirrors as a
 * control), both start positions, many seeds, in parallel. Prints win rates per matchup and per faction, average
 * match length and stalemates. The goal is every cross-faction matchup inside 45..55%.
 * <p>
 * usage: Tournament [matchesPerPairing=24] [minutesCap=25]
 */
public final class Tournament {
    record Result(String a, String b, String winner, float seconds) { }
    static final java.util.Map<String, double[]> TELE = new java.util.concurrent.ConcurrentHashMap<>();
    /** race@position -> [games, metal income @3min, @5min, units @5min, buildings @5min] */
    static final java.util.Map<String, double[]> ECO = new java.util.concurrent.ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 24;
        int cap = args.length > 1 ? Integer.parseInt(args[1]) : 25;
        List<String> races = new ArrayList<>();
        for (RaceDef r : RaceDef.ALL) if (r.firstPlayable()) races.add(r.id());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
        List<Future<Result>> jobs = new ArrayList<>();
        for (int i = 0; i < races.size(); i++) for (int j = i; j < races.size(); j++) {
            String a = races.get(i), b = races.get(j);
            for (int s = 0; s < n; s++) {
                final long seed = 1000L * (i * 10 + j) + s; final boolean swap = s % 2 == 1;
                jobs.add(pool.submit(() -> play(swap ? b : a, swap ? a : b, seed, cap)));
            }
        }
        List<Result> all = new ArrayList<>();
        for (Future<Result> f : jobs) all.add(f.get());
        pool.shutdown();

        System.out.printf("%n%-16s %-16s %6s %6s %6s %8s%n", "faction A", "faction B", "A win", "B win", "draw", "avg min");
        double worst = 0;
        for (int i = 0; i < races.size(); i++) for (int j = i; j < races.size(); j++) {
            String a = races.get(i), b = races.get(j);
            int wa = 0, wb = 0, dr = 0; float len = 0, cnt = 0;
            for (Result r : all) {
                boolean match = (r.a.equals(a) && r.b.equals(b)) || (r.a.equals(b) && r.b.equals(a));
                if (!match) continue;
                cnt++; len += r.seconds;
                if (r.winner == null) dr++;
                else if (a.equals(b)) { if (r.winner.equals("first")) wa++; else wb++; }
                else if (r.winner.equals(a)) wa++; else wb++;
            }
            double pa = (wa + wb) == 0 ? .5 : wa / (double) (wa + wb);
            if (!a.equals(b)) worst = Math.max(worst, Math.abs(pa - .5));
            System.out.printf("%-16s %-16s %5.0f%% %5.0f%% %6d %8.1f%s%n", a, b, pa * 100, (1 - pa) * 100, dr, len / Math.max(1, cnt) / 60,
                    a.equals(b) ? "   (mirror: first start position vs second)" : "");
        }
        System.out.println();
        for (String r : races) {
            int w = 0, g = 0;
            for (Result x : all) if (!x.a.equals(x.b) && (x.a.equals(r) || x.b.equals(r)) && x.winner != null) { g++; if (x.winner.equals(r)) w++; }
            System.out.printf("%-16s overall %5.1f%% of %d decided cross-faction games%n", r, g == 0 ? 50 : 100.0 * w / g, g);
        }
        System.out.printf("%n%-20s %6s %8s %8s %8s %8s%n", "race@position", "games", "m/s 3min", "m/s 5min", "units 5", "bldg 5");
        ECO.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> { double[] v = e.getValue(); double g = Math.max(1, v[0]);
            System.out.printf("%-20s %6.0f %8.1f %8.1f %8.1f %8.1f%n", e.getKey(), v[0], v[1] / g, v[2] / g, v[3] / g, v[4] / g); });
        System.out.printf("%n%-22s %7s %10s %7s %8s%n", "unit", "built", "dmg value", "deaths", "dmg/metal");
        TELE.entrySet().stream().filter(e -> e.getValue()[0] > 0).sorted((x, y) -> x.getKey().compareTo(y.getKey())).forEach(e -> {
            var d = dev.beyondtabs.engine.gen.UnitDef.byId(e.getKey()); double[] v = e.getValue();
            System.out.printf("%-22s %7.0f %10.0f %7.0f %8.2f%n", e.getKey(), v[0], v[1], v[2], v[1] / Math.max(1, v[0] * Math.max(1, d.metal())));
        });
        System.out.printf("%nworst cross-faction imbalance: %.1f%% from even%n", worst * 100);
    }

    static Result play(String a, String b, long seed, int capMinutes) {
        World w = new World(Terrain.FLAT, seed); w.telemetry = new java.util.HashMap<>();
        List<Skirmish.Player> ps = List.of(new Skirmish.Player(a, -1, 0, Ai.Difficulty.HARD), new Skirmish.Player(b, -1, 1, Ai.Difficulty.HARD));
        Skirmish.setup(w, 0, 0, 60, 2, ps, seed);
        int t = 0;
        while (w.winner < 0 && t < 20 * 60 * capMinutes) {
            w.tick(); t++;
            if (t == 20 * 180 || t == 20 * 300) for (Team tm : w.teams) {
                double[] e = ECO.computeIfAbsent(tm.race + "@" + tm.id, k -> new double[5]);
                synchronized (e) {
                    if (t == 20 * 180) { e[0]++; e[1] += tm.metalIncome; }
                    else { e[2] += tm.metalIncome; e[3] += w.aliveCount(tm.id); e[4] += w.buildings.stream().filter(q -> q.alive && q.team == tm.id).count(); }
                }
            }
        }
        synchronized (TELE) { w.telemetry.forEach((k, v) -> { double[] o = TELE.computeIfAbsent(k, x -> new double[3]); for (int i = 0; i < 3; i++) o[i] += v[i]; }); }
        String winner = w.winner < 0 ? null : a.equals(b) ? (w.winner == 0 ? "first" : "second") : w.teams.get(w.winner).race;
        return new Result(a, b, winner, t / 20f);
    }
}
