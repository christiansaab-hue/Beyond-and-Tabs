package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.*;
import java.util.Arrays;

/** Plain-Java benches: no Minecraft, no Gradle. Each prints measurements and fails loudly when a check breaks. */
public final class Bench {
    static int failures = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  PASS " : "  FAIL ") + what); if (!ok) failures++; }

    public static void main(String[] a) {
        ragdollStands(); ragdollKnockAndRecover(); wading(); battle(60, true); battle(200, false); battle(300, true); economy(); siege(); aiMatch(Ai.Difficulty.NORMAL, Ai.Difficulty.NORMAL); aiMatch(Ai.Difficulty.HARD, Ai.Difficulty.EASY);
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
        World w = new World(Terrain.FLAT, 2); w.addTeam("ancient_world"); w.addTeam("kingdoms");
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
        World w = new World(Terrain.FLAT, 3); w.addTeam("ancient_world"); w.addTeam("kingdoms");
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
        check(w.aliveCount(0) == 0 || w.aliveCount(1) == 0 || ticks == ms.length, "battle ran");
        check(avg < 25, "average tick fits in a 50 ms server tick with headroom (< 25 ms)");
        check(maxKnocked > 0, "units got knocked over by hits");
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
