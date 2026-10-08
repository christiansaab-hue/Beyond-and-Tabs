package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.EconomyDef;
import dev.beyondtabs.engine.gen.PerfDef;
import dev.beyondtabs.engine.gen.TechDef;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * The headless RTS simulation. Imports nothing from Minecraft: Minecraft supplies the terrain and the camera, sends
 * orders in and draws what comes out. Steps at 20 ticks per second.
 */
public final class World {
    public static final float DT = 1f / 20f;
    static final float PROJECTILE_GRAVITY = -20f;

    public final Terrain terrain;
    public final List<Team> teams = new ArrayList<>();
    public final List<Unit> units = new ArrayList<>();
    public final List<Building> buildings = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    /**
     * Cosmetic events for the client's effects (muzzle flashes, beams, impacts, blasts, deaths). The game layer drains
     * this after each snapshot; the sim never reads it. Each: {kind, x, y, z, x2, y2, z2, size} plus the visual name.
     */
    public final List<Fx> fx = new ArrayList<>();
    public record Fx(int kind, String visual, float x, float y, float z, float x2, float y2, float z2, float size) { }
    public static final int FX_SHOT = 0, FX_HIT = 1, FX_BLAST = 2, FX_DEATH = 3, FX_FALL = 4;
    void fx(int kind, String visual, float x, float y, float z, float x2, float y2, float z2, float size) {
        if (fx.size() < 4096) fx.add(new Fx(kind, visual, x, y, z, x2, y2, z2, size));
    }
    public final List<float[]> metalSpots = new ArrayList<>();
    public final List<Ai> ais = new ArrayList<>();
    final SpatialHash hash;
    final Random rng;
    /** Supplies the rig for a unit (Minecraft side: the one read from the player's TABS install). */
    /** Supplies a unit's numbers (Minecraft side: read from the player's TABS install). */
    public Function<UnitDef, UnitStats> stats = UnitStats::fallback;
    /** Optional balance telemetry per unit type: [built, metal-value of damage dealt to units, deaths]. Null = off. */
    public java.util.Map<String, double[]> telemetry;
    void tele(String id, int k, double v) { if (telemetry != null) telemetry.computeIfAbsent(id, x -> new double[3])[k] += v; }
    public Function<UnitDef, Rig> rigs = d -> ("humanoid".equals(d.body()) || "large".equals(d.body())) ? Rig.humanoidDefault() : blobFor(d);
    int nextId = 1; public long tick; public float time;
    /** Team id of the winner once every other team is defeated, else -1. */
    public int winner = -1;

    // camera(s) for LOD; several in multiplayer (nearest one counts)
    public final List<float[]> cameras = new ArrayList<>();
    float lodNear, lodMid; final float lodNearCfg, lodMidCfg, lodFar, iconZoom, physicsBudgetMs, sleepVel;
    final int substepsNear;
    // stats
    public double lastTickMs, lastPhysicsMs; public int activeNear, activeMid, sleepingBodies;
    /** Combat quality counters (benches): knockdowns, melee swings that connected / missed, dodges, abilities used. */
    public int knockdowns, meleeHits, meleeWhiffs, dodges;

    public World(Terrain terrain, long seed) {
        this.terrain = terrain; this.rng = new Random(seed);
        hash = new SpatialHash((float) pd("spatial_hash_cell"), 4096);
        lodNear = lodNearCfg = (float) pd("lod_near_distance"); lodMid = lodMidCfg = (float) pd("lod_mid_distance");
        lodFar = (float) pd("lod_far_distance"); iconZoom = (float) pd("icon_zoom_distance");
        physicsBudgetMs = (float) pd("physics_budget_ms"); sleepVel = (float) pd("sleep_velocity");
        substepsNear = (int) pd("physics_substeps_near");
    }
    static double pd(String id) { return Double.parseDouble(PerfDef.byId(id).value()); }
    static double eco(String id) { return Double.parseDouble(EconomyDef.byId(id).value()); }
    static Rig blobFor(UnitDef d) {
        var b = dev.beyondtabs.engine.gen.BodyDef.byId(d.body());
        return Rig.blob(b.particles(), (float) b.radius() * 2.2f, (float) b.radius() * 1.6f, (float) b.mass() * 60f);
    }

    /** Same team, or teams in the same alliance. */
    public boolean ally(int a, int b) {
        if (a == b) return true;
        if (a < 0 || b < 0 || a >= teams.size() || b >= teams.size()) return false;
        int x = teams.get(a).alliance; return x >= 0 && x == teams.get(b).alliance;
    }

    public Team addTeam(String race) { Team t = new Team(teams.size(), race); teams.add(t); return t; }

    public Unit spawn(int team, UnitDef def, float x, float z, float yaw) {
        Unit u = new Unit(nextId++, team, def, stats.apply(def), rigs.apply(def), x, z, yaw);
        tele(def.id(), 0, 1);
        u.ragdoll.pose(x, terrain.groundY(x, z), z, yaw, 0, 0, -1); u.ragdoll.snapToPose();
        units.add(u); teams.get(team).supplyUsed += def.supply();
        return u;
    }

    public Building place(int team, BuildingDef def, float x, float z, boolean prebuilt) {
        Building b = new Building(nextId++, team, def, x, z, prebuilt);
        buildings.add(b); recomputeStorage(teams.get(team)); return b;
    }

    /** Validates and places a construction site; null if not allowed (tech, metal spot). */
    public Building startConstruction(int team, BuildingDef def, float x, float z) {
        Team t = teams.get(team);
        if (!def.race().equals(t.race) || !t.has(def.requiresTech())) return null;
        if (def.effect().equals("metal_per_s")) {
            float[] spot = nearestFreeSpot(x, z, 3f);
            if (spot == null) return null;
            x = spot[0]; z = spot[1];
        }
        String[] f = def.footprint().split("x"); float hw = Integer.parseInt(f[0].trim()) / 2f, hh = Integer.parseInt(f[1].trim()) / 2f;
        for (Building o : buildings)   // no overlapping footprints (keep a 1-block lane so units can pass)
            if (o.alive && Math.abs(o.x - x) < o.hw + hw + gap(o.def, def) && Math.abs(o.z - z) < o.hh + hh + gap(o.def, def)) return null;
        return place(team, def, x, z, false);
    }

    /** True if a building of this kind could be placed here right now (used for the placement ghost). */
    public boolean canPlace(int team, BuildingDef def, float x, float z) {
        Team t = teams.get(team);
        if (!def.race().equals(t.race) || !t.has(def.requiresTech())) return false;
        if (def.effect().equals("metal_per_s") && nearestFreeSpot(x, z, 3f) == null) return false;
        String[] f = def.footprint().split("x"); float hw = Integer.parseInt(f[0].trim()) / 2f, hh = Integer.parseInt(f[1].trim()) / 2f;
        for (Building o : buildings) if (o.alive && Math.abs(o.x - x) < o.hw + hw + gap(o.def, def) && Math.abs(o.z - z) < o.hh + hh + gap(o.def, def)) return false;
        // no building on water: centre, corners and edge midpoints of the footprint must be dry
        for (float fx = -1; fx <= 1; fx++) for (float fz = -1; fz <= 1; fz++) if (terrain.waterDepth(x + fx * hw, z + fz * hh) > .3f) return false;
        return true;
    }

    float[] nearestFreeSpot(float x, float z, float within) {
        float[] best = null; float bd = within * within;
        outer:
        for (float[] s : metalSpots) {
            float d = (s[0] - x) * (s[0] - x) + (s[1] - z) * (s[1] - z);
            if (d > bd) continue;
            for (Building b : buildings) if (b.alive && b.def.effect().equals("metal_per_s") && Math.abs(b.x - s[0]) < .5f && Math.abs(b.z - s[1]) < .5f) continue outer;
            best = s; bd = d;
        }
        return best;
    }

    public void order(Unit u, Order o, boolean queue) {
        if (!queue) { u.orders.clear(); u.target = null; }
        if (o.type() != Order.Type.STOP) u.orders.add(o);
    }

    public boolean enqueue(Building factory, UnitDef def) {
        Team t = teams.get(factory.team);
        if (!def.factory().equals(factory.def.id()) || !t.tierUnlocked(def.tier())) return false;
        factory.queue.add(def); return true;
    }

    public boolean research(Building center, TechDef tech) {
        Team t = teams.get(center.team);
        if (!tech.researchedAt().equals(center.def.id()) || center.level < tech.minBuildingLevel() || !center.done()
                || !t.has(tech.requires()) || t.researched.contains(tech.id()) || center.researching != null) return false;
        center.researching = tech; center.researchLeft = tech.seconds(); return true;
    }

    public boolean upgrade(Building b) {
        if (!b.canUpgrade()) return false;
        b.upgrading = true; b.progress = 0; return true;
    }

    // ------------------------------------------------------------------------------------------------------------------
    int[] order = new int[256]; final Random orderRng = new Random(0x5EED);

    public void tick() {
        if (fx.size() > 3000) fx.clear();   // nobody is draining (headless sims, benches)
        long t0 = System.nanoTime();
        tick++; time += DT;
        hash.clear();
        for (Unit u : units) if (u.alive) hash.add(u);
        // fairness: who acts first each tick is shuffled (deterministically), so no team gets a first-mover edge
        if (!ais.isEmpty()) { int k = orderRng.nextInt(ais.size()); for (int i = 0; i < ais.size(); i++) ais.get((i + k) % ais.size()).tick(); }
        economy();
        for (Unit u : units) { u.attackersPrev = u.attackers; u.attackers = 0; }
        int n = units.size();
        if (order.length < n) order = new int[Math.max(n, order.length * 2)];
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = n - 1; i > 0; i--) { int j = orderRng.nextInt(i + 1), x = order[i]; order[i] = order[j]; order[j] = x; }
        for (int i = 0; i < n; i++) { Unit u = units.get(order[i]); if (u.alive) think(u); }
        separation();
        towers();
        projectiles();
        checkDefeat();
        long p0 = System.nanoTime();
        physics();
        lastPhysicsMs = (System.nanoTime() - p0) / 1e6;
        adaptLod();
        cleanup();
        lastTickMs = (System.nanoTime() - t0) / 1e6;
    }

    // ---------------- economy (BAR flow model) ----------------
    record Job(Object owner, double rateM, double rateE, double workPerSec, java.util.function.DoubleConsumer apply) { }

    void economy() {
        for (Team t : teams) { t.metalIncome = 0; t.energyIncome = 0; t.wantMetal = 0; t.wantEnergy = 0; }
        for (Unit u : units)
            if (u.alive && "commander".equals(u.def.role())) {
                Team t = teams.get(u.team); t.metalIncome += eco("commander_metal_income"); t.energyIncome += eco("commander_energy_income");
            }
        List<List<Job>> jobs = new ArrayList<>();
        for (Team ignored : teams) jobs.add(new ArrayList<>());
        for (Building b : buildings) {
            if (!b.alive) continue;
            Team t = teams.get(b.team);
            if (b.done()) {
                switch (b.def.effect()) {
                    case "metal_per_s" -> t.metalIncome += b.value();
                    case "energy_per_s" -> t.energyIncome += b.value();
                    case "energy_to_metal_per_s" -> {   // converter: 70 energy -> 1 metal, only when energy is plentiful
                        if (t.energy > t.energyStorage * .6) { t.metalIncome += b.value(); t.energyIncome -= b.value() * 70; }
                    }
                    default -> { }
                }
            }
            // factory production
            if (b.done() && b.def.kind().equals("factory")) {
                if (b.producing == null && !b.queue.isEmpty()) {
                    UnitDef next = b.queue.peek();
                    if (t.supplyUsed + next.supply() <= t.supplyCap) { b.producing = b.queue.poll(); b.produceWork = 0; if (b.repeat) b.queue.add(b.producing); }
                }
                if (b.producing != null) {
                    UnitDef d = b.producing; double bp = b.value();
                    jobs.get(b.team).add(new Job(b, d.metal() * bp / d.buildWork(), d.energy() * bp / d.buildWork(), bp, r -> {
                        b.produceWork += bp * r * DT;
                        if (b.produceWork >= d.buildWork()) finishUnit(b, d);
                    }));
                }
            }
            // research
            if (b.done() && b.researching != null) {
                TechDef tech = b.researching;
                jobs.get(b.team).add(new Job(b, tech.metal() / (double) tech.seconds(), tech.energy() / (double) tech.seconds(), 1, r -> {
                    b.researchLeft -= r * DT;
                    if (b.researchLeft <= 0) { t.researched.add(tech.id()); b.researching = null; }
                }));
            }
            // self-upgrade (BAR-like morph): fixed 20 build power
            if (b.upgrading) {
                double up = eco("upgrade_cost_per_level"), work = b.def.buildWork() * up * 2;
                jobs.get(b.team).add(new Job(b, b.def.metal() * up * 20 / work, b.def.energy() * up * 20 / work, 20, r -> {
                    b.progress += (float) (20 * r * DT / work);
                    if (b.progress >= 1) { b.progress = 1; b.upgrading = false; b.level++; b.hp = b.maxHp(); recomputeStorage(t); }
                }));
            }
        }
        // assist buildings (BAR construction turrets): lend build power to the nearest friendly construction site, else a
        // producing factory, else repair a damaged building, all within ASSIST_RANGE of the footprint edge
        for (Building a : buildings) {
            if (!a.alive || !a.done() || !"assist_power".equals(a.def.effect())) { a.assistTargetId = -1; continue; }
            Building tgt = assistTarget(a);
            a.assistTargetId = tgt == null ? -1 : tgt.id;
            if (tgt == null) continue;
            double bp = a.value();
            if (tgt.progress < 1 && !tgt.upgrading) {
                BuildingDef d = tgt.def;
                jobs.get(a.team).add(new Job(a, d.metal() * bp / d.buildWork(), d.energy() * bp / d.buildWork(), bp, r -> {
                    if (!tgt.alive || tgt.progress >= 1) return;
                    float step = (float) (bp * r * DT / d.buildWork());
                    tgt.progress += step; tgt.hp = Math.min(tgt.maxHp(), tgt.hp + step * tgt.maxHp() * .9f);
                    if (tgt.progress >= 1) { tgt.progress = 1; recomputeStorage(teams.get(tgt.team)); }
                }));
            } else if (tgt.producing != null) {
                UnitDef d = tgt.producing;
                jobs.get(a.team).add(new Job(a, d.metal() * bp / d.buildWork(), d.energy() * bp / d.buildWork(), bp, r -> {
                    if (tgt.producing != d || !tgt.alive) return;
                    tgt.produceWork += bp * r * DT;
                    if (tgt.produceWork >= d.buildWork()) finishUnit(tgt, d);
                }));
            } else tgt.hp = Math.min(tgt.maxHp(), tgt.hp + (float) (bp * ASSIST_REPAIR * DT));   // repairs are free, as in BAR
        }
        // construction by builders/commanders with a BUILD order in range
        for (Unit u : units) {
            if (!u.alive || u.knocked || u.orders.isEmpty()) continue;
            Order o = u.orders.peek();
            if (o.type() != Order.Type.BUILD) continue;
            Building b = o.building();
            boolean repair = b.alive && b.done() && b.hp < b.maxHp() - .5f;
            if (!b.alive || (b.progress >= 1 && !repair)) { u.orders.poll(); continue; }
            if (b.distTo(u.x, u.z) > 2.5f) continue;
            double bp = "commander".equals(u.def.role()) ? eco("commander_build_power") : eco("builder_build_power") * Math.max(1, u.def.tier());
            if (repair) { b.hp = Math.min(b.maxHp(), b.hp + (float) (bp * ASSIST_REPAIR * DT)); continue; }   // repairs are free, as in BAR
            BuildingDef d = b.def;
            jobs.get(u.team).add(new Job(u, d.metal() * bp / d.buildWork(), d.energy() * bp / d.buildWork(), bp, r -> {
                float step = (float) (bp * r * DT / d.buildWork());
                b.progress += step; b.hp = Math.min(b.maxHp(), b.hp + step * b.maxHp() * .9f);
                if (b.progress >= 1) { b.progress = 1; recomputeStorage(teams.get(b.team)); }
            }));
        }
        for (Team t : teams) {
            List<Job> js = jobs.get(t.id);
            for (Job j : js) { t.wantMetal += j.rateM; t.wantEnergy += j.rateE; }
            double haveM = t.metal + t.metalIncome * DT, haveE = t.energy + t.energyIncome * DT;
            double needM = t.wantMetal * DT, needE = t.wantEnergy * DT;
            double r = 1;
            if (needM > 1e-9) r = Math.min(r, Math.max(0, haveM) / needM);
            if (needE > 1e-9) r = Math.min(r, Math.max(0, haveE) / needE);
            t.efficiency = js.isEmpty() ? 1 : r;
            for (Job j : js) j.apply.accept(r);
            t.metalSpend = t.wantMetal * r; t.energySpend = t.wantEnergy * r;
            t.metal = Math.min(t.metalStorage, Math.max(0, haveM - needM * r));
            t.energy = Math.min(t.energyStorage, Math.max(0, haveE - needE * r));
        }
    }

    void recomputeStorage(Team t) {
        double base = eco("base_storage"), m = base, e = base;
        for (Building b : buildings) if (b.alive && b.team == t.id && b.done() && b.def.effect().equals("storage")) { m += b.value(); e += b.value(); }
        t.metalStorage = m; t.energyStorage = e;
    }

    void finishUnit(Building b, UnitDef d) {
        b.producing = null; b.produceWork = 0;
        float ang = (float) Math.atan2(b.rallyX - b.x, b.rallyZ - b.z);
        float sx = b.x + (float) Math.sin(ang) * 3.5f, sz = b.z + (float) Math.cos(ang) * 3.5f;
        Unit u = spawn(b.team, d, sx, sz, ang);
        float[] slot = Formation.spread(b.rallyX, b.rallyZ, b.rallyN++ % 60, Math.max(1.5f, u.radius * 2 + .6f));   // fan out round the rally point
        order(u, Order.move(slot[0], slot[1]), false);
    }

    // ---------------- unit behaviour ----------------
    void think(Unit u) {
        Ragdoll r = u.ragdoll;
        Abilities.effects(this, u);
        if (!u.alive) return;
        // knockdown state machine: balance is drained by hits and recovers over time. Only a real hit can trip a unit
        // up: being jostled by friends or stepping off a ledge never does.
        boolean recentlyHit = time - u.lastHit < .8f;
        if (!u.knocked && !u.leaping && (r.balance < .3f || (recentlyHit && u.lod < 2 && r.poseError() > .9f * r.scale))) {
            u.knocked = true; r.down = true; u.downFor = 0; knockdowns++;
            cancelAbility(u);
        }
        if (u.knocked) {
            u.downFor += DT;
            if (u.lod < 2) { u.x += (r.hipX() - u.x) * .3f; u.z += (r.hipZ() - u.z) * .3f; }
            u.vx *= .85f; u.vz *= .85f; u.x += u.vx * DT; u.z += u.vz * DT;
            if (r.down && r.balance > .55f && u.downFor > .6f) r.down = false;          // start getting up
            if (!r.down && (u.lod == 2 || r.poseError() < .45f * r.scale)) u.knocked = false;
            u.walkAmount = 0; u.attackAnim = -1;
            return;
        }
        if (u.stunFor > 0) {   // dazed: stands there
            cancelAbility(u);
            u.stunFor -= DT; u.vx *= .8f; u.vz *= .8f; u.x += u.vx * DT; u.z += u.vz * DT; u.walkAmount = 0; u.attackAnim = -1;
            return;
        }
        Abilities.passive(this, u);
        if (u.cooldown > 0) u.cooldown -= DT;
        // attack animation in progress
        if (u.attackAnim >= 0) {
            u.attackAnim += DT / .45f;
            if (!u.hitDealt && u.attackAnim >= .55f) { u.hitDealt = true; strike(u); }
            if (u.attackAnim >= 1) u.attackAnim = -1;
        }
        Order o = u.orders.peek();
        if (o != null && o.type() == Order.Type.ATTACK) {
            if (o.building() != null) {
                if (!o.building().alive) { u.orders.poll(); o = u.orders.peek(); u.targetB = null; } else { u.target = null; u.targetB = o.building(); }
            } else if (o.target() == null || !o.target().alive) { u.orders.poll(); o = u.orders.peek(); } else u.target = o.target();
        }
        if (u.targetB != null && !u.targetB.alive) u.targetB = null;
        boolean builder = "builder".equals(u.def.role()) && (o == null || o.type() != Order.Type.ATTACK);   // builders don't fight unless ordered
        if (builder) { u.target = null; u.targetB = null; }
        boolean autoTarget = !builder && (o == null || o.type() == Order.Type.ATTACK_MOVE || o.type() == Order.Type.PATROL
                || o.type() == Order.Type.GUARD || o.type() == Order.Type.AREA_ATTACK);
        if (u.target != null && !u.target.alive) u.target = null;
        if (autoTarget && (u.retargetIn -= DT) <= 0) {
            u.retargetIn = .5f + rng.nextFloat() * .2f;
            float acquire = u.range + (o == null ? 8f : 14f);
            if (o != null && o.type() == Order.Type.AREA_ATTACK) acquire = o.radius();
            float cx = o != null && o.type() == Order.Type.AREA_ATTACK ? o.x() : u.x, cz = o != null && o.type() == Order.Type.AREA_ATTACK ? o.z() : u.z;
            u.target = u.isSupport() ? woundedAlly(u, acquire) : pickTarget(u, cx, cz, acquire);
            u.targetB = u.target == null && !u.isSupport() ? nearestEnemyBuilding(u.team, cx, cz, acquire) : null;
        }
        if (u.target != null) u.target.attackers++;
        track(u);
        if (Abilities.act(this, u, u.target != null && !u.isSupport() ? u.target : null)) { u.inCombat = true; return; }
        float goalX = u.x, goalZ = u.z; boolean move = false; float stopAt = .6f;
        if (u.target == null && u.targetB != null) {
            Building b = u.targetB; float d = b.distTo(u.x, u.z), reach = u.range + u.radius;
            if (d > reach * .9f) { goalX = b.x; goalZ = b.z; move = true; stopAt = 0; }
            else {
                face(u, b.x, b.z);
                if (u.cooldown <= 0 && u.attackAnim < 0) { u.attackAnim = 0; u.hitDealt = false; u.cooldown = (float) u.weapon.cooldown(); }
            }
        } else if (u.target != null) {
            float[] g = engage(u, u.target);
            if (g != null) { goalX = g[0]; goalZ = g[1]; move = true; stopAt = g[2]; }
        } else if (o != null) {
            switch (o.type()) {
                case MOVE, ATTACK_MOVE, PATROL, AREA_ATTACK -> { goalX = o.x(); goalZ = o.z(); move = true; }
                case GUARD -> {
                    if (o.target() == null || !o.target().alive) { u.orders.poll(); break; }
                    goalX = o.target().x; goalZ = o.target().z; move = dist(u.x, u.z, goalX, goalZ) > 4; stopAt = 3;
                }
                case BUILD -> {
                    Building b = o.building();
                    if (b == null || !b.alive || b.progress >= 1) { u.orders.poll(); break; }
                    goalX = b.x; goalZ = b.z; move = b.distTo(u.x, u.z) > 1.5f; stopAt = 0;
                }
                default -> u.orders.poll();
            }
            if (move && o.type() != Order.Type.GUARD && o.type() != Order.Type.BUILD && dist(u.x, u.z, goalX, goalZ) < 1.2f) {
                Order done = u.orders.poll();
                if (done.type() == Order.Type.PATROL) u.orders.add(done);   // patrol loops through its points
                move = false;
            }
        }
        if (move && !buildings.isEmpty()) {   // route around buildings when one is in the way
            Building ignore = u.targetB != null && u.target == null ? u.targetB : (o != null && o.type() == Order.Type.BUILD ? o.building() : null);
            float[] wp = steer(u, goalX, goalZ, ignore);
            goalX = wp[0]; goalZ = wp[1];
            if (wp[2] > 0) stopAt = 0;
        } else u.path = null;
        float dx = goalX - u.x, dz = goalZ - u.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        float speed = u.speed * (u.slowFor > 0 ? u.slowMul : 1);
        if (u.attackAnim >= 0) speed *= u.isMelee() && u.target != null ? .55f : .3f;   // melee lunges into the swing
        if (u.bracing) speed = 0;
        float wd = terrain.waterDepth(u.x, u.z);
        if (wd > .3f) speed *= Math.max(.25f, 1f - wd * .45f);   // wading, TABS units walk along the bottom
        float wantVx = 0, wantVz = 0;
        if (move && d > stopAt && !(u.targetB != null && u.target == null && u.targetB.distTo(u.x, u.z) <= (u.range + u.radius) * .9f)) { wantVx = dx / d * speed; wantVz = dz / d * speed; }
        u.vx += (wantVx - u.vx) * .25f; u.vz += (wantVz - u.vz) * .25f;
        u.x += u.vx * DT; u.z += u.vz * DT;
        float sp = (float) Math.sqrt(u.vx * u.vx + u.vz * u.vz);
        if (sp > .2f && (u.target == null || !u.inCombat)) face(u, u.x + u.vx, u.z + u.vz);
        u.walkAmount = Math.min(1, sp / Math.max(.1f, u.speed));
        u.walkPhase += sp * DT * 3.2f / Math.max(.5f, u.ragdoll.scale);
    }

    /**
     * Fighting a unit in the current style. Faces and attacks when in reach; otherwise returns where to go
     * {x, z, stopAt} (null = stand still).
     */
    float[] engage(Unit u, Unit t) {
        float d = dist(u.x, u.z, t.x, t.z), reach = u.range + u.radius + t.radius;
        boolean melee = u.isMelee(), support = u.isSupport();
        u.inCombat = d < reach + 3;
        if (support) {   // healers follow whoever they're patching up
            if (d > reach * .9f) return new float[]{t.x, t.z, reach * .8f};
            faceFast(u, t.x, t.z); swing(u); return null;
        }
        Combat.Style st = u.style;
        if (st == Combat.Style.HOLD && !t.isMelee()) st = Combat.Style.AGGRESSIVE;   // holding still under arrows is pointless
        float dx = (t.x - u.x) / Math.max(.01f, d), dz = (t.z - u.z) / Math.max(.01f, d);
        switch (st) {
            case KITE -> {
                Unit threat = nearestMeleeThreat(u, Math.max(3.5f, u.range * .4f));
                if (threat != null && u.cooldown > .2f) {   // back off while reloading
                    float ex = u.x - threat.x, ez = u.z - threat.z, el = Math.max(.01f, (float) Math.sqrt(ex * ex + ez * ez));
                    return new float[]{u.x + ex / el * 3, u.z + ez / el * 3, 0};
                }
            }
            case HOLD -> {
                if (Float.isNaN(u.anchorX)) { u.anchorX = u.x; u.anchorZ = u.z; }
                if (melee && d < reach * .45f && u.range > 2.5f) return new float[]{u.x - dx * 1.2f, u.z - dz * 1.2f, 0};   // keep them at the tip
                if (d > reach * .95f) {
                    if (dist(u.anchorX, u.anchorZ, t.x, t.z) < reach + 2.5f) return new float[]{t.x, t.z, reach * .85f};
                    faceFast(u, t.x, t.z);
                    return dist(u.x, u.z, u.anchorX, u.anchorZ) > .8f ? new float[]{u.anchorX, u.anchorZ, 0} : null;
                }
            }
            case SKIRMISH -> {
                if (melee && u.cooldown > u.weapon.cooldown() * .35f && u.attackAnim < 0 && d < reach + 1)
                    return new float[]{u.x - dx * 2, u.z - dz * 2, 0};
            }
            case FLANK -> {
                if (d > reach + 2.5f) {
                    float side = (u.id & 1) == 0 ? 1 : -1, off = Math.min(3.5f, d * .45f);
                    return new float[]{t.x - dz * side * off, t.z + dx * side * off, 0};
                }
            }
            default -> { }
        }
        if (d > reach * (melee ? .9f : .95f)) return new float[]{t.x, t.z, reach * (melee ? .78f : .85f)};
        faceFast(u, t.x, t.z);
        swing(u);
        if (melee && u.attackAnim >= 0 && d > reach * .7f) return new float[]{t.x, t.z, reach * .6f};   // step into the blow
        return null;
    }

    void swing(Unit u) {
        if (u.cooldown <= 0 && u.attackAnim < 0) { u.attackAnim = 0; u.hitDealt = false; u.cooldown = (float) u.weapon.cooldown(); }
    }

    Unit nearestMeleeThreat(Unit u, float r) {
        bestFound = null; bestScore = r * r;
        hash.query(u.x, u.z, r, o -> {
            if (ally(o.team, u.team) || !o.alive || !o.isMelee() || o.knocked) return;
            float d = (o.x - u.x) * (o.x - u.x) + (o.z - u.z) * (o.z - u.z);
            if (d < bestScore) { bestScore = d; bestFound = o; }
        });
        return bestFound;
    }

    /** Target choice: near, wounded, not already swarmed, a threat to us, and what our style and ability favour. */
    Unit pickTarget(Unit u, float cx, float cz, float r) {
        bestFound = null; bestScore = 1e9f;
        final Unit cur = u.target;
        final boolean melee = u.isMelee();
        final String ak = u.ability.kind();
        hash.query(cx, cz, r, o -> {
            if (ally(o.team, u.team) || !o.alive) return;
            float d = dist(u.x, u.z, o.x, o.z), s = d, hpf = o.hp / o.maxHp;
            if (o == cur) s -= 1.5f;                                   // don't flip-flop
            if (o.knocked) s += 1f;
            if (melee && o.attackersPrev > 2) s += (o.attackersPrev - 2) * 1.6f;
            s -= (1 - hpf) * 2.5f;
            if (o.target == u) s -= 1.5f;
            if (!melee && d > u.range) s += 4;
            switch (u.style) {
                case FLANK -> { if (o.cls.equals("ranged") || o.cls.equals("support") || o.cls.equals("siege")) s -= 5; }
                case FOCUS -> s -= o.attackersPrev * .8f + (1 - hpf) * 3;
                default -> { }
            }
            if (ak.equals("brace") && (o.cls.equals("cavalry") || o.cls.equals("large"))) s -= 3;
            if (ak.equals("shadowstep") && (o.cls.equals("ranged") || o.cls.equals("support"))) s -= 4;
            if (s < bestScore) { bestScore = s; bestFound = o; }
        });
        return bestFound;
    }

    /** Opens and closes engagements and lets the team's tactics pick this unit's style. */
    void track(Unit u) {
        Unit t = u.target;
        if (t != null && !u.isSupport()) {
            u.noTargetFor = 0;
            // holding a line nobody attacks is a stalemate: after a few quiet seconds, go to them (and remember that it didn't work)
            if (u.eng != null && u.style == Combat.Style.HOLD && time - u.eng.start > 5 && u.eng.dealt + u.eng.taken < 1) {
                teams.get(u.team).tactics.penalize(u, u.eng.enemyCls, Combat.Style.HOLD, -.1f);
                u.style = Combat.Style.AGGRESSIVE; u.anchorX = Float.NaN;
            }
            if (u.eng == null || !u.eng.enemyCls.equals(t.cls) || time - u.eng.start > 12) {
                closeEngagement(u, false);
                u.style = teams.get(u.team).tactics.choose(u, t.cls);
                u.eng = new Combat.Engagement(t.cls, u.style, time, t.maxHp);
                u.anchorX = Float.NaN;
            }
        } else {
            u.inCombat = false; u.bracing = false;
            if (u.eng != null && (u.noTargetFor += DT) > 2) closeEngagement(u, false);
        }
    }

    void closeEngagement(Unit u, boolean died) {
        if (u.eng == null) return;
        teams.get(u.team).tactics.learn(u, u.eng, died);
        u.eng = null; u.anchorX = Float.NaN;
    }

    void faceFast(Unit u, float tx, float tz) {
        float want = (float) Math.atan2(tx - u.x, tz - u.z), diff = want - u.yaw;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        u.yaw += diff * .5f;
    }

    /** Returns {x, z, isWaypoint} — the goal itself if the way is clear, else the next waypoint of an A* path. */
    float[] steer(Unit u, float gx, float gz, Building ignore) {
        u.pathAge += DT;
        boolean goalMoved = Float.isNaN(u.pathGx) || Math.abs(u.pathGx - gx) + Math.abs(u.pathGz - gz) > 2f;
        if (u.path == null || goalMoved || u.pathAge > 3f) {
            if (!Paths.blocked(buildings, u.x, u.z, gx, gz, u.radius, ignore)) { u.path = null; u.pathGx = Float.NaN; return new float[]{gx, gz, 0}; }
            u.path = Paths.find(buildings, u.x, u.z, gx, gz, u.radius + .1f, ignore); u.pathIdx = 0; u.pathGx = gx; u.pathGz = gz; u.pathAge = 0;
            if (u.path == null) return new float[]{gx, gz, 0};
        }
        while (u.pathIdx < u.path.length / 2 - 1 && dist(u.x, u.z, u.path[u.pathIdx * 2], u.path[u.pathIdx * 2 + 1]) < .7f) u.pathIdx++;
        if (u.pathIdx >= u.path.length / 2 - 1) { u.path = null; return new float[]{gx, gz, 0}; }
        return new float[]{u.path[u.pathIdx * 2], u.path[u.pathIdx * 2 + 1], 1};
    }

    void face(Unit u, float tx, float tz) {
        float want = (float) Math.atan2(tx - u.x, tz - u.z), diff = want - u.yaw;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        u.yaw += diff * .25f;
    }

    Unit bestFound; float bestScore;
    Unit nearestEnemy(Unit u, float cx, float cz, float r) {
        bestFound = null; bestScore = r * r;
        hash.query(cx, cz, r, o -> {
            if (ally(o.team, u.team) || !o.alive) return;
            float d = (o.x - cx) * (o.x - cx) + (o.z - cz) * (o.z - cz);
            if (d < bestScore) { bestScore = d; bestFound = o; }
        });
        return bestFound;
    }
    Unit woundedAlly(Unit u, float r) {
        bestFound = null; bestScore = .95f;
        hash.query(u.x, u.z, r, o -> {
            if (!ally(o.team, u.team) || !o.alive || o == u) return;
            float f = o.hp / o.maxHp;
            if (f < bestScore) { bestScore = f; bestFound = o; }
        });
        return bestFound;
    }

    void strike(Unit u) {
        Unit t = u.target;
        if (t == null && u.targetB != null && u.targetB.alive) { strikeBuilding(u, u.targetB); return; }
        if (t == null || !t.alive) return;
        var w = u.weapon;
        switch (w.kind()) {
            case "melee" -> {
                if (dist(u.x, u.z, t.x, t.z) > u.range + u.radius + t.radius + .6f) { meleeWhiffs++; return; }   // whiffed: target moved away
                meleeHits++;
                fx(FX_HIT, "melee", (u.x + t.x) * .5f, terrain.groundY(t.x, t.z) + 1.1f * t.ragdoll.scale, (u.z + t.z) * .5f, t.x - u.x, 1, t.z - u.z, (float) w.aoe());
                float dmg = (float) w.damage() * Abilities.strikeMul(this, u, t);
                if (w.aoe() > 0) areaDamage(u.team, t.x, t.z, (float) w.aoe(), dmg, (float) w.knockback(), u.x, u.z, u);
                else damage(t, dmg, (float) w.knockback(), u.x, u.z, Rig.TORSO, u);
                Abilities.afterMelee(this, u, t, dmg);
            }
            case "support" -> {
                if (w.damage() < 0) {
                    float heal = (float) -w.damage();
                    hash.query(t.x, t.z, (float) w.aoe(), o -> { if (ally(o.team, u.team) && o.alive) o.hp = Math.min(o.maxHp, o.hp + heal); });
                }
            }
            default -> launch(u, t);
        }
    }

    void strikeBuilding(Unit u, Building b) {
        var w = u.weapon;
        if ("melee".equals(w.kind())) { if (b.distTo(u.x, u.z) <= u.range + u.radius + .6f) { damageBuilding(b, (float) w.damage()); fx(FX_HIT, "melee", u.x, terrain.groundY(u.x, u.z) + 1f, u.z, b.x - u.x, 2, b.z - u.z, 0); } }
        else if (!"support".equals(w.kind())) launchAt(u, b.x, b.z, terrain.groundY(b.x, b.z) + 1.5f, 0, 0);
    }

    static final float ASSIST_RANGE = 14, ASSIST_REPAIR = 2;

    /** What an assist building helps: keeps its current target while valid, else construction > production > repair. */
    Building assistTarget(Building a) {
        Building cur = null;
        for (Building b : buildings) if (b.id == a.assistTargetId) cur = b;
        if (cur != null && assistValid(a, cur)) return cur;
        Building best = null; float bs = Float.MAX_VALUE;
        for (Building b : buildings) {
            if (b == a || !assistValid(a, b)) continue;
            float d = b.distTo(a.x, a.z);
            float s = (b.progress < 1 && !b.upgrading) ? d : b.producing != null ? 100 + d : 200 + d;
            if (s < bs) { bs = s; best = b; }
        }
        return best;
    }

    boolean assistValid(Building a, Building b) {
        if (!b.alive || b.team != a.team || b.distTo(a.x, a.z) > ASSIST_RANGE + a.hw) return false;
        return (b.progress < 1 && !b.upgrading) || (b.done() && b.producing != null) || (b.done() && b.hp < b.maxHp() - 1);
    }

    /** Advanced power plants detonate when destroyed (BAR's AFUS): damage falls off with distance and hits everyone. */
    void detonate(Building b) {
        float r = b.def.blastRadius(), dmg = b.def.blastDamage();
        if (r <= 0 || dmg <= 0) return;
        b.detonated = true;
        fx(FX_BLAST, "", b.x, terrain.groundY(b.x, b.z), b.z, 0, 0, 0, r);
        for (Unit o : units) {
            if (!o.alive) continue;
            float d = dist(b.x, b.z, o.x, o.z);
            if (d > r + o.radius) continue;
            float f = 1f - .7f * Math.min(1, d / r);
            damage(o, dmg * f, 25 * f, b.x, b.z, Rig.HIP, null);
        }
        for (Building o : new ArrayList<>(buildings)) {
            if (o == b || !o.alive) continue;
            float d = o.distTo(b.x, b.z);
            if (d <= r) damageBuilding(o, dmg * .6f * (1f - .7f * Math.min(1, d / r)));
        }
    }

    public void damageBuilding(Building b, float dmg) {
        if (!b.alive) return;
        b.hp -= dmg;
        if (dmg > 0 && b.team < teams.size()) teams.get(b.team).alert(time, b.x, b.z, true);
        if (b.hp <= 0) { b.alive = false; b.hp = 0; recomputeStorage(teams.get(b.team)); fx(FX_FALL, b.def.id(), b.x, terrain.groundY(b.x, b.z), b.z, b.hw, 0, 0, b.team); detonate(b); }
    }

    Building nearestEnemyBuilding(int team, float cx, float cz, float r) {
        Building best = null; float bd = r;
        for (Building b : buildings) {
            if (!b.alive || ally(b.team, team)) continue;
            float d = b.distTo(cx, cz);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    /** Watchtowers (effect ranged_dps) shoot arrows at the nearest enemy unit in range. */
    void towers() {
        for (Building b : buildings) {
            if (!b.alive || !b.done() || !"ranged_dps".equals(b.def.effect())) continue;
            if ((b.cooldown -= DT) > 0) continue;
            Unit t = null; float best = 18f * 18f;
            for (Unit u : units) {
                if (!u.alive || ally(u.team, b.team)) continue;
                float d = (u.x - b.x) * (u.x - b.x) + (u.z - b.z) * (u.z - b.z);
                if (d < best) { best = d; t = u; }
            }
            if (t == null) continue;
            b.cooldown = 1.2f;
            float sy = terrain.groundY(b.x, b.z) + 5f, ty = terrain.groundY(t.x, t.z) + 1.1f, g = PROJECTILE_GRAVITY, spd = 32;
            float d = dist(b.x, b.z, t.x, t.z), T = Math.max(.05f, d / spd);
            float vx = (t.x + t.vx * T - b.x) / T, vz = (t.z + t.vz * T - b.z) / T, vy = (ty - sy - .5f * g * T * T) / T;
            projectiles.add(new Projectile(null, b.team, b.x, sy, b.z, vx, vy, vz, (float) b.value() * 1.2f, 0, 1.5f, g, "arrow"));
        }
    }

    void checkDefeat() {
        if (winner >= 0 || teams.size() < 2) return;
        for (Team t : teams) {
            if (t.defeated) continue;
            boolean hadCommander = false, alive = false;
            for (Unit u : units) if ("commander".equals(u.def.role()) && u.team == t.id) { hadCommander = true; if (u.alive) alive = true; }
            if (hadCommander && !alive) {   // commander ends: the rest of that army falls with it (BAR rule)
                t.defeated = true;
                for (Unit u : units) if (u.alive && u.team == t.id) die(u, 0, 0);
                for (Building b : buildings) if (b.alive && b.team == t.id) { b.alive = false; b.hp = 0; }
            }
        }
        int left = -1; boolean several = false;
        for (Team t : teams) if (!t.defeated) { if (left >= 0 && !ally(left, t.id)) several = true; if (left < 0) left = t.id; }
        if (left >= 0 && !several) winner = left;   // one team, or one alliance, still standing
    }

    static float projectileSpeed(dev.beyondtabs.engine.gen.WeaponDef w) {
        if ("lightning".equals(w.projectile())) return 200;
        return w.speed() > 0 ? (float) w.speed() : projectileSpeed(w.projectile());
    }
    static float projectileSpeed(String visual) {
        return switch (visual) {
            case "bullet" -> 90; case "bolt" -> 45; case "arrow", "ice_arrow", "snake_arrow" -> 32; case "spear" -> 24;
            case "boulder", "cannonball" -> 20; case "firework" -> 26; case "lightning" -> 200; case "flame" -> 14; case "bone_orb" -> 18;
            default -> 30;
        };
    }

    void launch(Unit u, Unit t) { launchAt(u, t.x, t.z, terrain.groundY(t.x, t.z) + 1.1f * t.ragdoll.scale, t.vx, t.vz); }

    void launchAt(Unit u, float txp, float tzp, float ty, float tvx, float tvz) {
        var w = u.weapon; String vis = w.projectile();
        float sx = u.x, sz = u.z, sy = terrain.groundY(u.x, u.z) + 1.5f * u.ragdoll.scale;
        float spd = projectileSpeed(w), g = w.gravity() > 0 ? -(float) w.gravity() : PROJECTILE_GRAVITY;
        float d = dist(sx, sz, txp, tzp), T = Math.max(.05f, d / spd);
        float lx = txp + tvx * T, lz = tzp + tvz * T;                      // lead the target
        float spread = .03f * d;
        lx += (rng.nextFloat() - .5f) * spread; lz += (rng.nextFloat() - .5f) * spread;
        float vx = (lx - sx) / T, vz = (lz - sz) / T, vy = (ty - sy - .5f * g * T * T) / T;
        int volley = Math.max(Math.max(1, w.count()), u.volleyShots); u.volleyShots = 0;
        float dmg = (float) w.damage() * Abilities.strikeMul(this, u, u.target);
        fx(FX_SHOT, vis, sx, sy, sz, lx, ty, lz, volley);
        for (int i = 0; i < volley; i++) {
            float j = volley > 1 ? (rng.nextFloat() - .5f) * 3 : 0;
            projectiles.add(new Projectile(u, sx, sy, sz, vx + j, vy, vz + j * .7f, dmg, (float) w.aoe(), (float) w.knockback(), g, vis));
        }
    }

    void projectiles() {
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile p = projectiles.get(i);
            p.vy += p.gravity * DT;
            p.x += p.vx * DT; p.y += p.vy * DT; p.z += p.vz * DT; p.life -= DT;
            boolean hitGround = p.y <= terrain.groundY(p.x, p.z);
            Unit hit = null;
            if (!hitGround) {
                bestFound = null; bestScore = 1e9f;
                hash.query(p.x, p.z, 1.6f, o -> {
                    if (ally(o.team, p.team) || !o.alive || o == p.lastHit) return;
                    float oy = terrain.groundY(o.x, o.z) + 1f * o.ragdoll.scale;
                    float dx = o.x - p.x, dy = oy - p.y, dz = o.z - p.z, rr = o.radius + .35f;
                    float d2 = dx * dx + dz * dz;
                    if (d2 < rr * rr && Math.abs(dy) < 1.1f * o.ragdoll.scale && d2 < bestScore) { bestScore = d2; bestFound = o; }
                });
                hit = bestFound;
            }
            Building hitB = null;
            if (hit == null) for (Building b : buildings)
                if (b.alive && !ally(b.team, p.team) && b.contains(p.x, p.z, .2f) && p.y < terrain.groundY(b.x, b.z) + 3.5f) { hitB = b; break; }
            if (hit != null || hitB != null || hitGround || p.life <= 0) {
                float ox = p.x - p.vx * .1f, oz = p.z - p.vz * .1f;
                boolean keep = false;
                fx(FX_HIT, p.visual, p.x, Math.max(p.y, terrain.groundY(p.x, p.z)), p.z, p.vx, hit != null ? 1 : hitB != null ? 2 : 0, p.vz, p.aoe);
                if (p.aoe > 0) {
                    areaDamage(p.team, p.x, p.z, p.aoe, p.damage, p.knockback, ox, oz, p.owner);
                    for (Building b : buildings) if (b.alive && !ally(b.team, p.team) && b.distTo(p.x, p.z) < p.aoe) damageBuilding(b, p.damage);
                    if (hit != null) Abilities.onProjectileHit(this, p, hit);
                }
                else if (hit != null) { damage(hit, p.damage, p.knockback, ox, oz, Rig.TORSO, p.owner); keep = Abilities.onProjectileHit(this, p, hit); }
                else if (hitB != null) damageBuilding(hitB, p.damage);
                p.dead = !keep || hitGround || p.life <= 0;
            }
        }
        projectiles.removeIf(p -> p.dead);
    }

    void areaDamage(int team, float x, float z, float r, float dmg, float kb, float fromX, float fromZ) { areaDamage(team, x, z, r, dmg, kb, fromX, fromZ, null); }

    void areaDamage(int team, float x, float z, float r, float dmg, float kb, float fromX, float fromZ, Unit src) {
        areaHit = true;
        hash.query(x, z, r, o -> {
            if (ally(o.team, team) || !o.alive) return;
            float d = dist(x, z, o.x, o.z);
            if (d > r + o.radius) return;
            float f = 1f - .6f * Math.min(1, d / r);
            damage(o, dmg * f, kb * f, x == fromX && z == fromZ ? fromX : x, z, Rig.HIP, src);
        });
        areaHit = false;
    }

    boolean areaHit, noReactions;

    /** Ends any ability that is steering the unit (knocked down or stunned mid-move). */
    void cancelAbility(Unit u) {
        if (u.charging || u.spinning || u.leaping) u.abilityCd = Math.max(u.abilityCd, (float) u.ability.cooldown() * .5f);
        u.charging = u.spinning = u.leaping = false; u.bracing = false; u.inCombat = false;
    }

    /** Building footprints keep a half-block lane between them so units can pass, except walls, which join up. */
    static float gap(dev.beyondtabs.engine.gen.BuildingDef a, dev.beyondtabs.engine.gen.BuildingDef b) {
        return a.footprint().equals("1x1") && b.footprint().equals("1x1") ? 0 : .5f;
    }

    /** Applies damage plus a physical knockback: the ragdoll is shoved, balance drops, the controller is pushed. */
    public void damage(Unit t, float dmg, float knockback, float fromX, float fromZ, int part) { damage(t, dmg, knockback, fromX, fromZ, part, null); }

    public void damage(Unit t, float dmg, float knockback, float fromX, float fromZ, int part, Unit src) {
        if (!t.alive) return;
        if (!noReactions && src != null && !ally(src.team, t.team) && (dmg > 0 || knockback > 0)) {
            float[] r = Abilities.onDamaged(this, t, src, dmg, knockback, fromX, fromZ, areaHit);
            if (r == null) { dodges++; return; }
            dmg = r[0]; knockback = r[1];
        }
        float before = t.hp;
        if (telemetry != null && src != null && !ally(src.team, t.team) && dmg > 0) tele(src.def.id(), 1, Math.min(dmg, Math.max(0, t.hp)) / t.maxHp * t.def.metal());
        t.hp -= dmg;
        if (dmg > 0 && src != null && t.team < teams.size()) teams.get(t.team).alert(time, t.x, t.z, false);
        if (dmg > 0) {
            float dealt = Math.min(before, dmg);
            if (t.eng != null) t.eng.taken += dealt;
            if (src != null && src.eng != null && !ally(src.team, t.team)) src.eng.dealt += dealt;
        }
        float dx = t.x - fromX, dz = t.z - fromZ, d = Math.max(.01f, (float) Math.sqrt(dx * dx + dz * dz));
        dx /= d; dz /= d;
        float massK = 50f / (t.ragdoll.totalMass * t.ragdoll.scale * t.stats.mass());   // humanoid ~ 1.0
        float shove = knockback * massK;
        // footing: units set for a fight, bracing or under a war cry are much harder to topple
        float resist = Math.min(.75f, (t.inCombat ? .25f : 0) + (t.bracing ? .4f : 0) + t.buffStab * .6f);
        if (knockback > 0) {
            t.ragdoll.impulse(Math.min(part, t.ragdoll.rig.n - 1), dx * shove * 40f * (1 - resist * .5f), shove * 18f, dz * shove * 40f * (1 - resist * .5f), DT);
            t.ragdoll.balance = Math.max(0, t.ragdoll.balance - shove * .22f * (1 - resist));
            t.vx += dx * shove * .8f * (1 - resist * .5f); t.vz += dz * shove * .8f * (1 - resist * .5f);
            t.lastHit = time;
        }
        if (t.hp <= 0 && t.alive) {
            if (src != null && src.eng != null && !ally(src.team, t.team)) src.eng.kills++;
            die(t, dx * shove, dz * shove);
        }
    }

    void die(Unit t, float kx, float kz) {
        closeEngagement(t, true);
        t.charging = t.spinning = t.leaping = t.bracing = false;
        fx(FX_DEATH, t.def.id(), t.x, terrain.groundY(t.x, t.z), t.z, kx, 0, kz, t.ragdoll.scale);
        t.alive = false; t.hp = 0; t.ragdoll.limp = true; t.ragdoll.down = true; t.ragdoll.sleeping = false; t.knocked = true;
        t.ragdoll.impulseAll(kx * 30, 40, kz * 30, DT, 1);
        teams.get(t.team).supplyUsed -= t.def.supply();
        tele(t.def.id(), 2, 1);
    }

    void separation() {
        for (Building b : buildings) {
            if (!b.alive) continue;
            for (Unit u : units) {
                if (!u.alive || !b.contains(u.x, u.z, u.radius)) continue;
                float px = (u.x - b.x) / (b.hw + u.radius), pz = (u.z - b.z) / (b.hh + u.radius);   // push out along the nearer side
                if (Math.abs(px) > Math.abs(pz)) u.x = b.x + Math.signum(px == 0 ? 1 : px) * (b.hw + u.radius);
                else u.z = b.z + Math.signum(pz == 0 ? 1 : pz) * (b.hh + u.radius);
            }
        }
        for (Unit u : units) {
            if (!u.alive) continue;
            hash.query(u.x, u.z, u.radius + 1.5f, o -> {
                if (o == u || !o.alive || o.id < u.id) return;
                float dx = o.x - u.x, dz = o.z - u.z, min = u.radius + o.radius, d2 = dx * dx + dz * dz;
                if (d2 >= min * min || d2 < 1e-6f) return;
                float d = (float) Math.sqrt(d2), push = (min - d) * .5f;
                float mu = u.stats.mass(), mo = o.stats.mass(), wu = mo / (mu + mo), wo = mu / (mu + mo);
                dx /= d; dz /= d;
                u.x -= dx * push * wu; u.z -= dz * push * wu; o.x += dx * push * wo; o.z += dz * push * wo;
            });
        }
    }

    // ---------------- physics with LOD ----------------
    void physics() {
        activeNear = activeMid = sleepingBodies = 0;
        boolean iconView = false;
        for (float[] c : cameras) if (c[1] - terrain.groundY(c[0], c[2]) > iconZoom) iconView = true;
        for (Unit u : units) {
            float cd = cameraDistance(u.x, u.z);
            u.lod = iconView && cameras.size() == 1 ? 2 : cd < lodNear ? 0 : cd < lodMid ? 1 : 2;
            Ragdoll r = u.ragdoll;
            float recovery = .35f;
            if (u.alive) {
                r.stance = (u.inCombat || u.bracing ? 1f : 0f) + u.buffStab;
                float atk = u.attackAnim;
                r.pose(u.x, terrain.groundY(u.x, u.z), u.z, u.yaw, u.walkPhase, u.walkAmount, atk);
            }
            if (u.lod == 2) {                               // far: no simulation, body follows its pose (or lies still)
                if (u.alive) { r.balance = Math.min(1, r.balance + recovery * DT); if (!u.knocked) r.snapToPose(); }
                continue;
            }
            if (u.lod == 1 && ((tick + u.id) & 1) == 1) continue;   // mid: half rate
            boolean ran = r.step(u.lod == 1 ? DT * 2 : DT, terrain, u.lod == 0 ? substepsNear : 1, recovery);
            if (!ran) sleepingBodies++; else if (u.lod == 0) activeNear++; else activeMid++;
        }
    }

    float cameraDistance(float x, float z) {
        if (cameras.isEmpty()) return 0;
        float best = Float.MAX_VALUE;
        for (float[] c : cameras) {
            float dx = c[0] - x, dz = c[2] - z, dy = Math.max(0, c[1] - terrain.groundY(x, z));
            best = Math.min(best, (float) Math.sqrt(dx * dx + dz * dz + dy * dy));
        }
        return best;
    }

    void adaptLod() {
        if (lastPhysicsMs > physicsBudgetMs) { lodNear = Math.max(12, lodNear * .9f); lodMid = Math.max(lodNear + 8, lodMid * .9f); }
        else if (lastPhysicsMs < physicsBudgetMs * .5) { lodNear = Math.min(lodNearCfg, lodNear * 1.03f); lodMid = Math.min(lodMidCfg, lodMid * 1.03f); }
    }
    public float lodNear() { return lodNear; }

    void cleanup() {
        for (Unit u : units) if (!u.alive) u.deadFor += DT;
        // corpses stay 20 s (they are still physics props), then go
        units.removeIf(u -> !u.alive && (u.deadFor > 20 || (u.ragdoll.sleeping && u.deadFor > 8)));
        buildings.removeIf(b -> !b.alive);
    }

    static float dist(float ax, float az, float bx, float bz) { float dx = ax - bx, dz = az - bz; return (float) Math.sqrt(dx * dx + dz * dz); }

    public long aliveCount(int team) { return units.stream().filter(u -> u.alive && u.team == team).count(); }
}
