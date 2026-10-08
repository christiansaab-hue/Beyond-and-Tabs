package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Plain-Java benches: no Minecraft, no Gradle. Each prints measurements and fails loudly when a check breaks. */
public final class Bench {
    static int failures = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  PASS " : "  FAIL ") + what); if (!ok) failures++; }

    public static void main(String[] a) {
        ragdollStands(); ragdollKnockAndRecover(); wading(); battle(60, true); battle(200, false); battle(300, true); economy(); siege(); aiMatch(Ai.Difficulty.NORMAL, Ai.Difficulty.NORMAL); aiMatch(Ai.Difficulty.HARD, Ai.Difficulty.EASY);
        abilities(); learning(); formations(); multiAi(false); multiAi(true);
        System.out.println(failures == 0 ? "\nALL BENCHES PASS" : "\n" + failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void ragdollStands() {
        System.out.println("ragdoll stands still for 10 s");
        World w = new World(Terrain.FLAT, 1); w.addTeam("ancient_world");
        Unit u = w.spawn(0, UnitDef.AW_CLUBBER, 0, 0, 0); w.cameras.add(new float[]{0, 5, -5});
        for (int i = 0; i < 200; i++) w.tick();
        float headY = u.ragdoll.y[Rig.HEAD], rest = u.ragdoll.ty[Rig.HEAD];
        System.out.printf("  head y %.2f (pose %.2f), hip y %.2f, balance %.2f%n", headY, rest, u.ragdoll.hipY(), u.ragdoll.balance);
        check(Math.abs(headY - rest) < .25f, "head stays near its pose height");
        check(!u.knocked, "unit is not knocked down");
    }

    static void ragdollKnockAndRecover() {
        System.out.println("heavy hit knocks a unit down, it gets back up");
        World w = new World(Terrain.FLAT, 2); w.addTeam("ancient_world"); w.addTeam("kingdoms"); w.cleanCombat = false;   // classic physics brawl
        Unit u = w.spawn(0, UnitDef.AW_CLUBBER, 0, 0, 0); w.cameras.add(new float[]{0, 5, -5});
        for (int i = 0; i < 20; i++) w.tick();
        w.damage(u, 1, 25f, -2, 0, Rig.TORSO);   // big shove from the west, little damage
        float minHead = 9; boolean wasDown = false; int upTick = -1;
        for (int i = 0; i < 200; i++) {
            w.tick(); minHead = Math.min(minHead, u.ragdoll.y[Rig.HEAD]);
            if (u.knocked) wasDown = true; else if (wasDown && upTick < 0) upTick = i;
        }
        System.out.printf("  lowest head y %.2f, moved to x=%.2f, back up after %.1f s%n", minHead, u.x, upTick / 20f);
        check(wasDown, "unit was knocked down"); check(minHead < 1.0f, "head went down toward the ground");
        check(upTick > 0, "unit stood back up"); check(u.x > .5f, "shove moved it away from the hit");
    }

    static void wading() {
        System.out.println("units wade through water at reduced speed, along the bottom");
        Terrain lake = new Terrain() {
            public float groundY(float x, float z) { return x > 10 && x < 30 ? -1.5f : 0f; }
            public float waterDepth(float x, float z) { return x > 10 && x < 30 ? 1.5f : 0f; }
        };
        World w = new World(lake, 5); w.addTeam("ancient_world");
        Unit u = w.spawn(0, UnitDef.AW_CLUBBER, 0, 0, 0); w.cameras.add(new float[]{20, 5, -5});
        w.order(u, Order.move(40, 0), false);
        int t = 0; float dryTime = 0, wetTime = 0;
        while (u.x < 39 && t < 20 * 60) { w.tick(); t++; if (u.x > 12 && u.x < 28) wetTime += World.DT; else if (u.x < 10) dryTime += World.DT; }
        System.out.printf("  10 dry blocks in %.1f s, 16 water blocks in %.1f s, hip y in water %.2f%n", dryTime, wetTime, u.ragdoll.hipY());
        check(u.x >= 39, "unit crossed the lake");
        check(wetTime / 16f > dryTime / 10f * 1.4f, "slower in water");
    }

    static void battle(int perSide, boolean camNear) {
        System.out.printf("battle %d vs %d (camera %s)%n", perSide, perSide, camNear ? "near the middle" : "zoomed out");
        World w = new World(Terrain.FLAT, 3); w.addTeam("ancient_world"); w.addTeam("kingdoms"); w.cleanCombat = false;   // classic physics brawl
        UnitDef[] aw = {UnitDef.AW_CLUBBER, UnitDef.AW_CLUBBER, UnitDef.AW_PROTECTOR, UnitDef.AW_SPEAR_THROWER, UnitDef.AW_BERSERKER, UnitDef.AW_STONER};
        UnitDef[] kd = {UnitDef.KD_SQUIRE, UnitDef.KD_SQUIRE, UnitDef.KD_ARCHER, UnitDef.KD_FENCER, UnitDef.KD_HEALER, UnitDef.KD_SAMURAI};
        int cols = (int) Math.ceil(Math.sqrt(perSide));
        for (int i = 0; i < perSide; i++) {
            float x = (i % cols - cols / 2f) * 1.6f, zz = (i / cols) * 1.6f;
            Unit u1 = w.spawn(0, aw[i % aw.length], x, -25 - zz, 0); w.order(u1, Order.attackMove(x, 40), false);
            Unit u2 = w.spawn(1, kd[i % kd.length], x, 25 + zz, (float) Math.PI); w.order(u2, Order.attackMove(x, -40), false);
        }
        w.cameras.add(camNear ? new float[]{0, 18, -10} : new float[]{0, 200, 0});
        double[] ms = new double[20 * 120]; int ticks = 0; int maxKnocked = 0; long proj = 0;
        while (ticks < ms.length && w.aliveCount(0) > 0 && w.aliveCount(1) > 0) {
            w.tick(); ms[ticks++] = w.lastTickMs; proj = Math.max(proj, w.projectiles.size());
            maxKnocked = (int) Math.max(maxKnocked, w.units.stream().filter(u -> u.alive && u.knocked).count());
        }
        double[] m = Arrays.copyOf(ms, ticks); Arrays.sort(m);
        double avg = Arrays.stream(m).average().orElse(0);
        System.out.printf("  %.1f s simulated, avg %.2f ms/tick, p99 %.2f ms, max %.2f ms | survivors %d vs %d | max knocked at once %d | max projectiles %d | lodNear %.0f%n",
                ticks / 20f, avg, m[(int) (m.length * .99)], m[m.length - 1], w.aliveCount(0), w.aliveCount(1), maxKnocked, proj, w.lodNear());
        System.out.printf("  combat: %d knockdowns (%.2f per unit), melee hit rate %.0f%% (%d hits, %d whiffs), %d dodges%n",
                w.knockdowns, w.knockdowns / (2f * perSide), 100f * w.meleeHits / Math.max(1, w.meleeHits + w.meleeWhiffs), w.meleeHits, w.meleeWhiffs, w.dodges);
        check(w.meleeHits > 3 * w.meleeWhiffs, "melee swings mostly connect (hit rate > 75%)");
        check(w.aliveCount(0) == 0 || w.aliveCount(1) == 0 || ticks == ms.length, "battle ran");
        check(avg < 25, "average tick fits in a 50 ms server tick with headroom (< 25 ms)");
        check(maxKnocked > 0, "units got knocked over by hits");
    }

    /** Every ability fires in a mixed brawl. */
    static void abilities() {
        System.out.println("abilities: every unit type with an ability fights a mixed enemy army");
        World w = new World(Terrain.FLAT, 11); w.addTeam("ancient_world"); w.addTeam("kingdoms");
        java.util.List<UnitDef> aw = new java.util.ArrayList<>(), kd = new java.util.ArrayList<>();
        for (UnitDef d : UnitDef.ALL) if (!d.role().equals("builder")) (d.race().equals("ancient_world") ? aw : kd).add(d);
        int i = 0;
        for (UnitDef d : aw) for (int k = 0; k < 3; k++) { Unit u = w.spawn(0, d, (i++ % 12) * 2f - 12, -20 - (i / 12) * 2f, 0); w.order(u, Order.attackMove(u.x, 30), false); }
        i = 0;
        for (UnitDef d : kd) for (int k = 0; k < 3; k++) { Unit u = w.spawn(1, d, (i++ % 12) * 2f - 12, 20 + (i / 12) * 2f, (float) Math.PI); w.order(u, Order.attackMove(u.x, -30), false); }
        w.cameras.add(new float[]{0, 18, 0});
        java.util.Map<String, Integer> used = new java.util.TreeMap<>();
        int t = 0;
        while (t++ < 20 * 90 && w.aliveCount(0) > 0 && w.aliveCount(1) > 0) {
            w.tick();
            for (Unit u : w.units) if (u.alive && (u.charging || u.leaping || u.spinning || u.bracing || u.abilityCd > 0)) used.merge(u.ability.id(), 1, Integer::sum);
        }
        for (Unit u : w.units) if (u.slowFor > 0 || u.poisonFor > 0) used.merge("(effects seen)", 1, Integer::sum);
        System.out.printf("  %.0f s, survivors %d vs %d, dodges %d | ability-ticks %s%n", t / 20f, w.aliveCount(0), w.aliveCount(1), w.dodges, used);
        int kinds = 0; for (String k : used.keySet()) if (!k.startsWith("(")) kinds++;
        check(kinds >= 12, "at least 12 different abilities were used");
    }

    /**
     * Learning: the same matchups are fought over and over. One side keeps its combat memory between rounds (as a
     * saved army would), the other starts fresh each time. The learning side should settle on styles that work.
     */
    static void learning() {
        System.out.println("learning: 16 rounds of mirror skirmishes; team A remembers what worked, team B starts fresh each round");
        UnitDef[] aw = {UnitDef.KD_SQUIRE, UnitDef.KD_ARCHER, UnitDef.KD_FENCER, UnitDef.KD_HALBERD, UnitDef.AW_CLUBBER, UnitDef.KD_JOUSTER};
        UnitDef[] kd = aw;
        String memory = "";
        float early = 0, late = 0;
        for (int round = 0; round < 16; round++) {
            World w = new World(Terrain.FLAT, 100 + round); w.addTeam("ancient_world"); w.addTeam("kingdoms");
            w.teams.get(0).tactics.load(memory);
            for (int i = 0; i < 24; i++) {
                float x = (i % 8) * 1.8f - 7;
                Unit a = w.spawn(0, aw[i % aw.length], x, -18 - (i / 8) * 1.8f, 0); w.order(a, Order.attackMove(x, 30), false);
                Unit b = w.spawn(1, kd[i % kd.length], x, 18 + (i / 8) * 1.8f, (float) Math.PI); w.order(b, Order.attackMove(x, -30), false);
            }
            w.cameras.add(new float[]{0, 18, 0});
            int t = 0;
            while (t++ < 20 * 120 && w.aliveCount(0) > 0 && w.aliveCount(1) > 0) w.tick();
            for (Unit u : w.units) if (u.alive) w.closeEngagement(u, false);
            memory = w.teams.get(0).tactics.save();
            float hpA = 0, hpB = 0;
            for (Unit u : w.units) if (u.alive) { if (u.team == 0) hpA += u.hp / u.maxHp; else hpB += u.hp / u.maxHp; }
            float score = (hpA - hpB) / 24f;
            if (round < 4) early += score / 4; if (round >= 12) late += score / 4;
            System.out.printf("  round %2d: %4.0f s, survivors %2d vs %2d, health margin %+.2f%n", round + 1, t / 20f, w.aliveCount(0), w.aliveCount(1), score);
            if (round == 15) { System.out.println("  learned (team A):"); w.teams.get(0).tactics.summary().forEach((k, v) -> System.out.println("    " + k + " -> " + v)); }
        }
        System.out.printf("  average health margin of the learning side: first 4 rounds %+.2f, last 4 rounds %+.2f%n", early, late);
        check(!memory.isEmpty(), "tactics were learned and saved");
        check(late > early, "the learning side does better after learning");
    }

    /** Group moves: front-liners end up ahead of archers, a line order spreads along the line, walls join up. */
    static void formations() {
        System.out.println("formations: a mixed group moved as a block and along a line; walls placed side by side");
        World w = new World(Terrain.FLAT, 5); w.addTeam("kingdoms"); w.addTeam("ancient_world");
        java.util.List<Unit> g = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) g.add(w.spawn(0, i % 2 == 0 ? UnitDef.KD_ARCHER : UnitDef.KD_SQUIRE, (i % 4) * 1.5f, (i / 4) * 1.5f, 0));
        float[][] t = Formation.block(g, 0, 30);   // moving towards +z
        float front = 0, back = 0;
        for (int i = 0; i < g.size(); i++) { if (g.get(i).cls.equals("shield")) front += t[i][1] / 6; else back += t[i][1] / 6; }
        float minGap = 1e9f;
        for (int i = 0; i < t.length; i++) for (int j = i + 1; j < t.length; j++) minGap = Math.min(minGap, (float) Math.hypot(t[i][0] - t[j][0], t[i][1] - t[j][1]));
        System.out.printf("  block: shields avg z %.1f, archers avg z %.1f, closest slots %.2f blocks%n", front, back, minGap);
        check(front > back, "shields stand in front of archers");
        check(minGap >= 1.29f, "no two units share a spot");
        float[][] l = Formation.line(g, -10, 20, 10, 20);
        float onLine = 0; for (int i = 0; i < g.size(); i++) if (Math.abs(l[i][1] - 20) < .01f) onLine++;
        float spanX = 0; for (float[] q : l) spanX = Math.max(spanX, Math.abs(q[0]));
        System.out.printf("  line: %d units on the line itself, spread to x = +-%.1f%n", (int) onLine, spanX);
        check(onLine >= 10 && spanX > 8, "a line order spreads the group along the line");
        Building a = w.startConstruction(0, BuildingDef.KD_WALL, 50.5f, 50.5f), b = w.startConstruction(0, BuildingDef.KD_WALL, 51.5f, 50.5f);
        check(a != null && b != null, "walls can be placed next to each other");
        check(w.startConstruction(0, BuildingDef.KD_BARRACKS, 55.5f, 50.5f) == null, "other buildings still keep a lane free");
    }

    /** Four AIs on one map: free-for-all, or two alliances of two. */
    static void multiAi(boolean teams) {
        System.out.println("four AIs, " + (teams ? "2 vs 2 alliances" : "free-for-all") + ", radius 70");
        World w = new World(Terrain.FLAT, teams ? 21 : 22);
        List<Skirmish.Player> ps = new ArrayList<>();
        String[] races = {"ancient_world", "kingdoms", "ancient_world", "kingdoms"};
        for (int i = 0; i < 4; i++) ps.add(new Skirmish.Player(races[i], teams ? i % 2 : -1, -1, i % 2 == 0 ? Ai.Difficulty.NORMAL : Ai.Difficulty.HARD));
        List<Skirmish.Start> st = Skirmish.setup(w, 0, 0, 70, 8, ps, 3);
        float minD = Float.MAX_VALUE;
        for (int i = 0; i < st.size(); i++) for (int j = i + 1; j < st.size(); j++) minD = Math.min(minD, World.dist(st.get(i).x(), st.get(i).z(), st.get(j).x(), st.get(j).z()));
        w.cameras.add(new float[]{0, 80, 0});
        int t = 0; double total = 0; int peak = 0; String order = "";
        boolean[] gone = new boolean[4];
        while (w.winner < 0 && t < 20 * 60 * 30) {
            w.tick(); t++; total += w.lastTickMs; peak = Math.max(peak, w.units.size());
            for (int k = 0; k < 4; k++) if (!gone[k] && w.teams.get(k).defeated) { gone[k] = true; order += String.format(" team %d out at %.0fs;", k, t / 20f); }
        }
        System.out.printf("  bases at least %.0f blocks apart | %s | winner %s after %.0f s | peak %d units, avg %.2f ms/tick%n",
                minD, order.isEmpty() ? "nobody out" : order, w.winner < 0 ? "none" : "team " + w.winner + (teams ? " (alliance " + w.teams.get(w.winner).alliance + ")" : ""), t / 20f, peak, total / t);
        check(minD > 40, "start positions spread out");
        check(w.winner >= 0, "the match ended with a winner");
        if (teams) { int a = w.teams.get(w.winner).alliance; check(w.teams.stream().filter(x -> !x.defeated).allMatch(x -> x.alliance == a), "only one alliance is left standing"); }
        else check(w.teams.stream().filter(x -> !x.defeated).count() == 1, "exactly one army is left in a free-for-all");
        for (Team tm : w.teams) if (tm.defeated) check(w.aliveCount(tm.id) == 0 && w.buildings.stream().noneMatch(b -> b.alive && b.team == tm.id), "a defeated army falls with its commander (team " + tm.id + ")");
    }

    static void siege() {
        System.out.println("siege: an army attacks a base (barracks + watchtower + commander); towers shoot, buildings fall, commander death ends the game");
        World w = new World(Terrain.FLAT, 6); w.addTeam("ancient_world"); w.addTeam("kingdoms");
        Building barracks = w.place(1, BuildingDef.KD_BARRACKS, 0, 40, true);
        Building tower = w.place(1, BuildingDef.KD_WATCHTOWER, 6, 34, true);
        Unit kdCmd = w.spawn(1, UnitDef.KD_COMMANDER, 0, 75, 0);
        Unit awCmd = w.spawn(0, UnitDef.AW_COMMANDER, 0, -40, 0);
        int start = 30;
        for (int i = 0; i < start; i++) { Unit u = w.spawn(0, i % 3 == 0 ? UnitDef.AW_SPEAR_THROWER : UnitDef.AW_CLUBBER, (i % 6 - 3) * 1.6f, (i / 6) * 1.6f, 0); w.order(u, Order.attackMove(0, 80), false); }
        w.cameras.add(new float[]{0, 20, 20});
        int t = 0; boolean towerFell = false, towerFired = false, barracksFell = false;
        while (w.winner < 0 && t < 20 * 240) {
            w.tick(); t++; if (!tower.alive) towerFell = true; if (!barracks.alive) barracksFell = true;
            for (Projectile p : w.projectiles) if (p.owner == null && p.team == 1) towerFired = true;
        }
        long awLeft = w.aliveCount(0) - 1;
        System.out.printf("  %.1f s: barracks %s, tower %s, KD commander %s, AW units left %d/%d, winner %d%n", t / 20f,
                barracks.alive ? "standing" : "destroyed", tower.alive ? "standing" : "destroyed", kdCmd.alive ? "alive" : "dead", awLeft, start, w.winner);
        check(barracksFell && towerFell, "army destroyed the barracks and the watchtower");
        check(towerFired, "the watchtower shot at the attackers");
        check(w.winner == 0 && w.teams.get(1).defeated, "killing the commander won the game for Ancient World");
    }

    static void aiMatch(Ai.Difficulty a, Ai.Difficulty b) {
        System.out.printf("AI vs AI: Ancient World (%s) vs Kingdoms (%s), 120 blocks apart%n", a, b);
        World w = new World(Terrain.FLAT, 7); w.addTeam("ancient_world"); w.addTeam("kingdoms");
        float[][] bases = {{0, 0}, {0, 120}};
        for (float[] base : bases) for (int i = 0; i < 8; i++) { double ang = i * Math.PI / 4 + .3; float r = 13 + (i % 2) * 6;
            w.metalSpots.add(new float[]{base[0] + (float) Math.cos(ang) * r, base[1] + (float) Math.sin(ang) * r}); }
        for (int i = 0; i < 4; i++) w.metalSpots.add(new float[]{(i - 1.5f) * 14, 60});
        w.spawn(0, UnitDef.AW_COMMANDER, 0, 4, 0); w.spawn(1, UnitDef.KD_COMMANDER, 0, 116, (float) Math.PI);
        w.ais.add(new Ai(w, 0, a)); w.ais.add(new Ai(w, 1, b));
        w.cameras.add(new float[]{0, 60, 60});
        double total = 0, worst = 0; int t = 0, maxUnits = 0, maxBuildings = 0;
        while (w.winner < 0 && t < 20 * 60 * 25) {
            w.tick(); t++; total += w.lastTickMs; worst = Math.max(worst, w.lastTickMs);
            maxUnits = Math.max(maxUnits, (int) (w.aliveCount(0) + w.aliveCount(1))); maxBuildings = Math.max(maxBuildings, w.buildings.size());
            if (t % (20 * 180) == 0) System.out.printf("  %4.0fs  AW: %3d units %2d bldg %.0f m/s tech %s | KD: %3d units %2d bldg %.0f m/s tech %s%n", t / 20f,
                    w.aliveCount(0), w.buildings.stream().filter(x -> x.team == 0).count(), w.teams.get(0).metalIncome, w.teams.get(0).researched,
                    w.aliveCount(1), w.buildings.stream().filter(x -> x.team == 1).count(), w.teams.get(1).metalIncome, w.teams.get(1).researched);
        }
        System.out.printf("  result after %.0f s: winner %s | peak %d units, %d buildings | avg %.2f ms/tick, worst %.1f ms%n", t / 20f,
                w.winner < 0 ? "none" : w.teams.get(w.winner).race, maxUnits, maxBuildings, total / t, worst);
        check(maxBuildings >= 12, "both AIs built bases");
        check(w.teams.stream().anyMatch(x -> x.researched.stream().anyMatch(r -> r.endsWith("_t2"))), "an AI reached tech 2");
        check(w.winner >= 0, "the match ended with a winner");
        check(total / t < 10, "AI match runs fast (avg < 10 ms/tick)");
    }

    static void economy() {
        System.out.println("economy: commander builds an economy, a barracks, researches T2 and trains units");
        World w = new World(Terrain.FLAT, 4); Team t = w.addTeam("ancient_world");
        for (int i = 0; i < 8; i++) w.metalSpots.add(new float[]{(float) Math.cos(i) * 12, (float) Math.sin(i) * 12});
        Unit cmd = w.spawn(0, UnitDef.AW_COMMANDER, 0, 0, 0);
        java.util.List<Building> plan = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) plan.add(w.startConstruction(0, BuildingDef.AW_METAL_EXTRACTOR, w.metalSpots.get(i)[0], w.metalSpots.get(i)[1]));
        plan.add(w.startConstruction(0, BuildingDef.AW_ENERGY_GEN, 5, -5));
        plan.add(w.startConstruction(0, BuildingDef.AW_ENERGY_GEN, -5, -5));
        for (int i = 0; i < 4; i++) plan.add(w.startConstruction(0, BuildingDef.AW_ENERGY_GEN, -14 + i * 5, -11));
        Building barracks = w.startConstruction(0, BuildingDef.AW_BARRACKS, 2, 20); plan.add(barracks);
        Building tech = w.startConstruction(0, BuildingDef.AW_TECH_CENTER, 14, -12); plan.add(tech);
        check(w.startConstruction(0, BuildingDef.AW_STORAGE, 2, 20) == null, "overlapping placement refused");
        check(plan.stream().allMatch(b -> b != null), "all construction sites placed (extractors snapped to metal spots)");
        check(w.startConstruction(0, BuildingDef.AW_WAR_LODGE, 20, 20) == null, "T2 factory refused before T2 research");
        for (Building b : plan) w.order(cmd, Order.build(b), true);
        int t1 = 0;
        for (int i = 0; i < 20 * 600; i++) {
            w.tick();
            if (barracks.done() && barracks.queue.isEmpty() && barracks.producing == null && t1 == 0) {
                barracks.repeat = true; w.enqueue(barracks, UnitDef.AW_CLUBBER); w.enqueue(barracks, UnitDef.AW_BUILDER_T1); t1 = i;
            }
            if (tech.done() && tech.level == 1 && !tech.upgrading) w.upgrade(tech);
            if (tech.done() && tech.level >= 2) w.research(tech, TechDef.AW_TECH_T2);
            if (i % (20 * 60) == 0)
                System.out.printf("  t=%3ds metal %6.0f/%5.0f (+%.1f/s, spend %.1f) energy %6.0f/%5.0f (+%.0f/s) eff %.2f units %d built %d/%d tech %s%n",
                        i / 20, t.metal, t.metalStorage, t.metalIncome, t.metalSpend, t.energy, t.energyStorage, t.energyIncome, t.efficiency,
                        w.aliveCount(0), plan.stream().filter(Building::done).count(), plan.size(), t.researched);
        }
        if (!plan.stream().allMatch(Building::done)) {
            for (Building b : plan) System.out.printf("    %s at (%.0f,%.0f) progress %.2f%n", b.def.id(), b.x, b.z, b.progress);
            System.out.printf("    commander at (%.1f,%.1f), orders %d, first %s%n", cmd.x, cmd.z, cmd.orders.size(), cmd.orders.peek() == null ? "-" : cmd.orders.peek().building().def.id());
        }
        check(plan.stream().allMatch(Building::done), "commander finished every building");
        check(t.metalIncome >= 2 + 4 * 2.0 - 1e-6, "income = commander + 4 extractors");
        check(w.aliveCount(0) > 5, "barracks trained units on repeat");
        check(t.researched.contains("aw_tech_t2"), "T2 researched at an upgraded tech center");
        check(w.startConstruction(0, BuildingDef.AW_WAR_LODGE, 20, 20) != null, "T2 factory allowed after research");
    }
}
