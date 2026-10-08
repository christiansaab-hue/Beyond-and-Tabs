package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.TechDef;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Computer opponent (design/ai.json). Plays by the same rules as a player: no resource cheats, no map hacks beyond
 * what its units can see (sight radius). Planners run on their own clocks:
 * economy (extractors on free spots, energy to keep the 1:8 metal:energy cost ratio, storage when floating),
 * build order and tech (tech center, upgrades, T2/T3 research and factories), production (counter-composition from
 * scouted enemies), army (stage, attack when strong enough, defend the base), micro (pull back badly hurt units).
 */
public final class Ai {
    int stageSlot;
    public enum Difficulty { EASY, NORMAL, HARD }

    final World w; public final int team; public final Difficulty diff; final Random rng;
    float baseX, baseZ, clock;
    float nextEco, nextProd, nextArmy, nextMicro, nextTech;
    final Map<String, Integer> seenEnemyRoles = new HashMap<>();
    int waves; float lastAttack = -999;
    final float decision, attackRatio, expandRange0;
    static final float SIGHT = 28;

    public Ai(World w, int team, Difficulty diff) {
        this.w = w; this.team = team; this.diff = diff; this.rng = new Random(w.rng.nextLong() ^ team);
        decision = switch (diff) { case EASY -> 4f; case NORMAL -> 2f; case HARD -> 1f; };
        attackRatio = switch (diff) { case EASY -> 2.2f; case NORMAL -> 1.5f; case HARD -> 1.2f; };
        expandRange0 = switch (diff) { case EASY -> 26; case NORMAL -> 34; case HARD -> 42; };
        Unit c = commander();
        if (c != null) { baseX = c.x; baseZ = c.z; }
    }

    Team me() { return w.teams.get(team); }
    /** This race's building for a role (economy roles by effect, factories by tier): works for any faction. */
    final Map<String, BuildingDef> defs = new HashMap<>();
    BuildingDef def(String role) {
        return defs.computeIfAbsent(role, r -> {
            for (BuildingDef d : BuildingDef.ALL) {
                if (!d.race().equals(me().race)) continue;
                boolean ok = switch (r) {
                    case "extractor" -> d.effect().equals("metal_per_s");
                    case "energy" -> d.effect().equals("energy_per_s") && d.tier() == 1;
                    case "advEnergy" -> d.effect().equals("energy_per_s") && d.tier() >= 3;
                    case "storage" -> d.effect().equals("storage");
                    case "tech" -> d.kind().equals("tech");
                    case "tower" -> d.effect().equals("ranged_dps");
                    case "converter" -> d.effect().equals("energy_to_metal_per_s");
                    case "barracks" -> d.kind().equals("factory") && d.tier() == 1;
                    case "assist" -> d.effect().equals("assist_power");
                    default -> false;
                };
                if (ok) return d;
            }
            return null;
        });
    }
    /** This race's tier-n research (e.g. kd_tech_t2). */
    String techId(int tier) {
        for (TechDef td : TechDef.ALL) if (td.race().equals(me().race) && td.id().endsWith("_tech_t" + tier)) return td.id();
        return "none";
    }
    Unit commander() { for (Unit u : w.units) if (u.alive && u.team == team && "commander".equals(u.def.role())) return u; return null; }

    public void tick() {
        if (me().defeated || w.winner >= 0) return;
        clock += World.DT;
        if (clock >= nextMicro) { nextMicro = clock + .5f; micro(); }
        if (clock >= nextEco) { nextEco = clock + decision; economy(); }
        if (clock >= nextTech) { nextTech = clock + decision * 2.5f; tech(); }
        if (clock >= nextProd) { nextProd = clock + decision; production(); }
        if (clock >= nextArmy) { nextArmy = clock + decision * 1.5f; scout(); army(); }
    }

    // ------------------------------------------------------------------ helpers
    List<Building> mine(String role) {
        List<Building> l = new ArrayList<>(); BuildingDef d = def(role);
        if (d != null) for (Building b : w.buildings) if (b.alive && b.team == team && b.def == d) l.add(b);
        return l;
    }
    List<Building> underConstruction() {
        List<Building> l = new ArrayList<>();
        for (Building b : w.buildings) if (b.alive && b.team == team && b.progress < 1 && !b.upgrading) l.add(b);
        return l;
    }
    List<Unit> builders() {
        List<Unit> l = new ArrayList<>();
        for (Unit u : w.units) if (u.alive && u.team == team && ("builder".equals(u.def.role()) || "commander".equals(u.def.role()))) l.add(u);
        return l;
    }
    List<Unit> troops() {
        List<Unit> l = new ArrayList<>();
        for (Unit u : w.units) if (u.alive && u.team == team && !"builder".equals(u.def.role()) && !"commander".equals(u.def.role())) l.add(u);
        return l;
    }
    static boolean isRanged(UnitDef d) { return "ranged".equals(d.role()) || "siege".equals(d.role()); }
    static float value(Unit u) { return u.def.metal() * (u.hp / u.maxHp); }

    /** Finds a free spot for a building near the base, spiralling outwards. */
    Building site(BuildingDef def, float nearX, float nearZ) {
        // the spiral is turned to face the rival, so every start position builds the same base layout (fair mirrors)
        if (Float.isNaN(facing)) { scout(); facing = Float.isNaN(enemyX) ? 0 : (float) Math.atan2(enemyZ - baseZ, enemyX - baseX); }
        for (int r = 6; r <= 40; r += 3)
            for (int k = 0; k < 12; k++) {
                double a = facing + k * Math.PI / 6 + r * .37;
                float x = nearX + (float) Math.cos(a) * r, z = nearZ + (float) Math.sin(a) * r;
                if (w.canPlace(team, def, x, z) && roomy(def, x, z, 2)) return w.startConstruction(team, def, x, z);
            }
        // a crowded base: settle for the normal lane rather than not building at all
        for (int r = 6; r <= 52; r += 3)
            for (int k = 0; k < 12; k++) {
                double a = facing + k * Math.PI / 6 + r * .37;
                float x = nearX + (float) Math.cos(a) * r, z = nearZ + (float) Math.sin(a) * r;
                if (w.canPlace(team, def, x, z) && roomy(def, x, z, 1)) return w.startConstruction(team, def, x, z);
            }
        return null;
    }

    /** Streets between buildings: at least `gap` blocks to any other non-wall building (walls and extractors may hug). */
    boolean roomy(BuildingDef def, float x, float z, float gap) {
        String[] f = def.footprint().split("x"); float hw = Integer.parseInt(f[0].trim()) / 2f, hh = Integer.parseInt(f[1].trim()) / 2f;
        for (Building o : w.buildings) {
            if (!o.alive || o.def.footprint().equals("1x1")) continue;
            if (Math.abs(o.x - x) < o.hw + hw + gap && Math.abs(o.z - z) < o.hh + hh + gap) return false;
        }
        return true;
    }

    /** Sends the least busy builder to a construction site (commander helps only near the base). */
    void assign(Building b) {
        Unit best = null; float bd = Float.MAX_VALUE;
        for (Unit u : builders()) {
            boolean busy = !u.orders.isEmpty() && u.orders.peek().type() == Order.Type.BUILD;
            float d = World.dist(u.x, u.z, b.x, b.z) + (busy ? 60 : 0) + ("commander".equals(u.def.role()) ? 15 : 0);
            if (d < bd) { bd = d; best = u; }
        }
        if (best != null) w.order(best, Order.build(b), !best.orders.isEmpty() && best.orders.peek().type() == Order.Type.BUILD);
    }

    // ------------------------------------------------------------------ economy
    void economy() {
        Team t = me();
        List<Building> sites = underConstruction();
        // keep every site staffed; idle builders help the nearest site
        for (Building s : sites) {
            boolean staffed = false;
            for (Unit u : builders()) for (Order o : u.orders) if (o.building() == s) staffed = true;
            if (!staffed) assign(s);
        }
        for (Unit u : builders()) if (u.orders.isEmpty() && !sites.isEmpty()) {
            Building s = sites.get(rng.nextInt(sites.size())); w.order(u, Order.build(s), false);
        }
        int maxSites = Math.max(1, builders().size());
        if (sites.size() >= maxSites) return;
        double mInc = t.metalIncome, eInc = t.energyIncome;
        // 1) extractors on free metal spots within reach (reach grows over time)
        float reach = expandRange0 + clock * .08f;
        float[] spot = null; float best = reach * reach;
        for (float[] s : w.metalSpots) {
            float d = (s[0] - baseX) * (s[0] - baseX) + (s[1] - baseZ) * (s[1] - baseZ);
            if (d < best && w.canPlace(team, def("extractor"), s[0], s[1])) { best = d; spot = s; }
        }
        // 2) energy keeps pace with metal (units cost ~8x more energy than metal)
        boolean energyShort = eInc < mInc * 9 || t.energy < t.energyStorage * .15;
        Building placed = null;
        boolean t3 = t.has(techId(3));
        if (energyShort && t3 && def("advEnergy") != null && mine("advEnergy").size() < 2 && t.metal > def("advEnergy").metal() * .4) {
            // big plants go to the far side of the base from the enemy: they explode when destroyed
            float ax = baseX, az = baseZ;
            if (!Float.isNaN(enemyX)) { float dx = baseX - enemyX, dz = baseZ - enemyZ, l = Math.max(1, (float) Math.hypot(dx, dz)); ax += dx / l * 14; az += dz / l * 14; }
            placed = site(def("advEnergy"), ax, az);
        }
        else if (energyShort && t.metal > 30) placed = site(def("energy"), baseX, baseZ);
        else if (spot != null && t.metal > 40) placed = w.startConstruction(team, def("extractor"), spot[0], spot[1]);
        else if (mine("barracks").isEmpty() && t.metal > 120) placed = site(def("barracks"), baseX, baseZ);
        else if (t.metal >= t.metalStorage * .95 && mine("storage").size() < 2) placed = site(def("storage"), baseX, baseZ);
        else if (mine("tech").isEmpty() && mInc >= 7) placed = site(def("tech"), baseX, baseZ);
        else if (mine("barracks").size() < 2 && mInc >= 14) placed = site(def("barracks"), baseX, baseZ);
        else if (diff != Difficulty.EASY && mine("tower").size() < (diff == Difficulty.HARD ? 4 : 2) && clock > 120 && t.metal > 150)
            placed = site(def("tower"), baseX + (rng.nextFloat() - .5f) * 20, baseZ + (rng.nextFloat() - .5f) * 20);
        else if (def("assist") != null && t.has(def("assist").requiresTech()) && diff != Difficulty.EASY
                && mine("assist").size() < (diff == Difficulty.HARD ? 3 : 2) && t.metal > 250 && t.efficiency > .95) {
            // assist camps go next to the busiest factory
            Building f = null; for (Building x : w.buildings) if (x.alive && x.team == team && x.done() && x.def.kind().equals("factory") && (f == null || x.def.tier() > f.def.tier())) f = x;
            if (f != null) placed = site(def("assist"), f.x, f.z);
        }
        else if (t.has(techId(2)) && def("converter") != null && mine("converter").size() < 2 && t.energy > t.energyStorage * .9)
            placed = site(def("converter"), baseX, baseZ);
        if (placed != null) assign(placed);
        // 3) floating metal: upgrade extractors and generators
        if (t.metal > t.metalStorage * .8 && t.efficiency > .99) {
            for (Building b : w.buildings)
                if (b.alive && b.team == team && b.canUpgrade() && (b.def.effect().equals("metal_per_s") || b.def.effect().equals("energy_per_s"))) { w.upgrade(b); break; }
        }
    }

    // ------------------------------------------------------------------ tech
    void tech() {
        Team t = me();
        double mInc = t.metalIncome;
        for (Building c : mine("tech")) {
            if (!c.done()) continue;
            boolean wantT2 = mInc >= (diff == Difficulty.HARD ? 10 : 14) && clock > 150;
            boolean wantT3 = mInc >= (diff == Difficulty.HARD ? 22 : 30) && t.has(techId(2));
            if (c.level == 1 && wantT2) w.upgrade(c);
            else if (c.level == 2 && wantT3 && t.has(techId(2))) w.upgrade(c);
            for (TechDef td : TechDef.ALL) {
                if (!td.race().equals(t.race) || t.researched.contains(td.id())) continue;
                if (td.tier() == 2 && !wantT2 && td.id().endsWith("_t2")) continue;
                if (td.tier() == 3 && !wantT3) continue;
                if (w.research(c, td)) break;
            }
        }
        // build the next factory tier once unlocked
        for (BuildingDef d : BuildingDef.ALL) {
            if (!d.race().equals(t.race) || !d.kind().equals("factory") || d.tier() < 2 || !t.has(d.requiresTech())) continue;
            boolean have = false; for (Building b : w.buildings) if (b.alive && b.team == team && b.def == d) have = true;
            if (!have && underConstruction().size() < builders().size() && t.metal > d.metal() * .3) { Building b = site(d, baseX, baseZ); if (b != null) assign(b); break; }
        }
    }

    // ------------------------------------------------------------------ production
    void production() {
        Team t = me();
        int builderCount = 0; for (Unit u : builders()) if ("builder".equals(u.def.role())) builderCount++;
        int wantBuilders = diff == Difficulty.EASY ? 2 : diff == Difficulty.NORMAL ? 3 : 5;
        int enemyRanged = seenEnemyRoles.getOrDefault("ranged", 0) + seenEnemyRoles.getOrDefault("siege", 0);
        int enemyMelee = seenEnemyRoles.getOrDefault("inf", 0) + seenEnemyRoles.getOrDefault("hero", 0) + seenEnemyRoles.getOrDefault("cavalry", 0);
        for (Building f : w.buildings) {
            if (!f.alive || f.team != team || !f.done() || !f.def.kind().equals("factory")) continue;
            if (f.queue.size() >= 2) continue;
            f.repeat = false;
            List<UnitDef> options = new ArrayList<>();
            for (UnitDef d : UnitDef.ALL) if (d.factory().equals(f.def.id()) && t.tierUnlocked(d.tier())) options.add(d);
            if (options.isEmpty()) continue;
            UnitDef pick = null;
            if (builderCount < wantBuilders) for (UnitDef d : options) if ("builder".equals(d.role())) { pick = d; builderCount++; break; }
            if (pick == null) {
                // composition by value, the same rule for every faction: aim for a ranged share (45%, shifted by what we
                // have scouted: more ranged against melee-heavy enemies, more front line against ranged), then pick
                // within that class, favouring higher tiers and affordable units
                float rangedV = 0, frontV = 0;
                for (Unit u : troops()) { if (isRanged(u.def)) rangedV += u.def.metal(); else frontV += u.def.metal(); }
                for (Building q : w.buildings) if (q.alive && q.team == team) for (UnitDef qd : q.queue) { if (isRanged(qd)) rangedV += qd.metal(); else frontV += qd.metal(); }
                float target = .45f + (enemyMelee > enemyRanged * 1.5f ? .1f : 0) - (enemyRanged > enemyMelee * 1.5f ? .1f : 0);
                boolean wantRanged = rangedV < (rangedV + frontV) * target;
                float best = -1;
                for (UnitDef d : options) {
                    if ("builder".equals(d.role())) continue;
                    boolean r = isRanged(d);
                    float score = 1 + rng.nextFloat() * .8f + d.tier() * .5f;
                    if (r == wantRanged) score += 1.5f;
                    if ("support".equals(d.role())) score -= .5f;
                    if (d.metal() > t.metalIncome * 40 + t.metal) score -= 2;   // too expensive for now
                    if (score > best) { best = score; pick = d; }
                }
            }
            if (pick != null) { w.enqueue(f, pick); f.rallyX = stageX(); f.rallyZ = stageZ(); }
        }
    }

    // ------------------------------------------------------------------ scouting, army
    float enemyX = Float.NaN, enemyZ, facing = Float.NaN;
    void scout() {
        seenEnemyRoles.clear();
        for (Unit e : w.units) {
            if (!e.alive || w.ally(e.team, team)) continue;
            boolean seen = false;
            for (Unit m : w.units) if (m.alive && m.team == team && World.dist(m.x, m.z, e.x, e.z) < SIGHT) { seen = true; break; }
            if (!seen) for (Building b : w.buildings) if (b.alive && b.team == team && b.distTo(e.x, e.z) < SIGHT) { seen = true; break; }
            if (seen) seenEnemyRoles.merge(e.def.role(), 1, Integer::sum);
        }
        // where is the enemy? The nearest living enemy commander (the AI knows start locations, like a player does in
        // BAR); re-chosen every time so that in a free-for-all it moves on once a rival is beaten.
        float best = Float.MAX_VALUE; enemyX = Float.NaN;
        for (Unit e : w.units) if (e.alive && !w.ally(e.team, team) && "commander".equals(e.def.role())) {
            float d = World.dist(baseX, baseZ, e.x, e.z);
            if (d < best) { best = d; enemyX = e.x; enemyZ = e.z; }
        }
    }
    float stageX() { return Float.isNaN(enemyX) ? baseX : baseX + (enemyX - baseX) * .2f; }
    float stageZ() { return Float.isNaN(enemyX) ? baseZ : baseZ + (enemyZ - baseZ) * .2f; }

    void army() {
        List<Unit> army = troops();
        float mine = 0; for (Unit u : army) mine += value(u);
        // defend: enemies near our buildings pull the whole army back
        Unit intruder = null;
        for (Unit e : w.units) {
            if (!e.alive || w.ally(e.team, team)) continue;
            for (Building b : w.buildings) if (b.alive && b.team == team && b.distTo(e.x, e.z) < 18) { intruder = e; break; }
            if (intruder != null) break;
        }
        if (intruder != null) {
            float[][] slots = Formation.block(army, intruder.x, intruder.z);
            for (int i = 0; i < army.size(); i++) w.order(army.get(i), Order.attackMove(slots[i][0], slots[i][1]), false);
            return;
        }
        if (Float.isNaN(enemyX)) return;
        // estimate the enemy army near their base from what we have seen (fallback: count everything, a cautious guess)
        float theirs = 0;
        for (Unit e : w.units) if (e.alive && !w.ally(e.team, team) && !"commander".equals(e.def.role()) && !"builder".equals(e.def.role())
                && World.dist(e.x, e.z, enemyX, enemyZ) < 70) theirs += value(e);   // only the rival we are about to hit
        float needed = Math.max(250 + waves * 120, theirs * attackRatio);
        boolean attacking = clock - lastAttack < 60;
        if (mine >= needed && !attacking) {
            lastAttack = clock; waves++;
            float tx = enemyX, tz = enemyZ;
            Building target = null; float bd = Float.MAX_VALUE;   // nearest enemy building to our base
            for (Building b : w.buildings) if (b.alive && !w.ally(b.team, team) && World.dist(b.x, b.z, enemyX, enemyZ) < 70) { float d = World.dist(baseX, baseZ, b.x, b.z); if (d < bd) { bd = d; target = b; } }
            if (target != null) { tx = target.x; tz = target.z; }
            float[][] slots = Formation.block(army, tx, tz);   // march as a block, not a ball
            for (int i = 0; i < army.size(); i++) {
                Unit u = army.get(i); float ox = slots[i][0] - tx, oz = slots[i][1] - tz;
                w.order(u, Order.attackMove(slots[i][0], slots[i][1]), false); w.order(u, Order.attackMove(enemyX + ox, enemyZ + oz), true);
            }
        } else if (!attacking) {
            int k = 0;
            for (Unit u : army) if (u.orders.isEmpty() && World.dist(u.x, u.z, stageX(), stageZ()) > 12) {
                float[] p = Formation.spread(stageX(), stageZ(), stageSlot++ % 90, Math.max(1.5f, u.radius * 2 + .6f)); k++;
                w.order(u, Order.move(p[0], p[1]), false);
            }
        }
    }

    // ------------------------------------------------------------------ micro
    void micro() {
        if (diff == Difficulty.EASY) return;
        for (Unit u : w.units) {
            if (!u.alive || u.team != team || "builder".equals(u.def.role())) continue;
            if ("commander".equals(u.def.role())) {   // keep the commander out of fights it can't win
                if (u.hp < u.maxHp * .5f && World.dist(u.x, u.z, baseX, baseZ) > 6) w.order(u, Order.move(baseX, baseZ), false);
                continue;
            }
            if (diff == Difficulty.HARD && u.hp < u.maxHp * .25f && u.target != null && World.dist(u.x, u.z, baseX, baseZ) > 20)
                w.order(u, Order.move(stageX(), stageZ()), false);   // pull back badly hurt units
        }
    }
}
