package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A skirmish opponent. Each bot owns an RTSPlayer (PlayerServerEvents.startRTSBot gives it workers and resources)
 * and this brain plays the match for it: a capitol, workers, metal extractors on the map's metal patches, wind
 * generators, houses ahead of the population cap, an army building, then waves of fighters at the nearest enemy.
 * Decisions are re-derived from world state every second, so it recovers cleanly after a reload.
 */
public class BotPlayer {

    public enum Difficulty { EASY, MEDIUM, HARD }

    /**
     * What each faction uses for each job. T2 lives in its own lab (needs Tier 2 research): it trains the T2 army
     * and the T2 constructor, the only worker that may raise the T3 lab.
     */
    public record Kit(Building capitol, Building house, Building farm, Building extractor, Building wind, Building armyBuilding,
               Building tower, ProductionItem worker, List<ProductionItem> army,
               Building t2Lab, ProductionItem t2Worker, List<ProductionItem> t2Army) { }

    public static Kit kitFor(Faction faction) {
        if (faction.equals(Factions.PIGLINS))
            return new Kit(Buildings.CENTRAL_PORTAL, Buildings.PORTAL_POCKET, Buildings.NETHERWART_FARM,
                    Buildings.METAL_EXTRACTOR_PIGLINS, Buildings.WIND_GENERATOR_PIGLINS, Buildings.BASTION,
                    null, ProductionItems.GRUNT, List.of(ProductionItems.BRUTE, ProductionItems.HEADHUNTER),
                    Buildings.FLAME_SANCTUARY, ProductionItems.BONEWRIGHT,
                    List.of(ProductionItems.BLAZE, ProductionItems.WITHER_SKELETON, ProductionItems.MAGMA_CUBE, ProductionItems.GHAST));
        if (faction.equals(Factions.MONSTERS))
            return new Kit(Buildings.MAUSOLEUM, Buildings.HAUNTED_HOUSE, Buildings.PUMPKIN_FARM,
                    Buildings.METAL_EXTRACTOR_MONSTERS, Buildings.WIND_GENERATOR_MONSTERS, Buildings.GRAVEYARD,
                    Buildings.DARK_WATCHTOWER, ProductionItems.ZOMBIE_VILLAGER, List.of(ProductionItems.ZOMBIE, ProductionItems.SKELETON),
                    Buildings.DUNGEON, ProductionItems.EMBALMER,
                    List.of(ProductionItems.CREEPER, ProductionItems.WRAITH, ProductionItems.WARDEN));
        return new Kit(Buildings.TOWN_CENTRE, Buildings.VILLAGER_HOUSE, Buildings.WHEAT_FARM,
                Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.WIND_GENERATOR_VILLAGERS, Buildings.BARRACKS,
                Buildings.WATCHTOWER, ProductionItems.VILLAGER, List.of(ProductionItems.VINDICATOR, ProductionItems.PILLAGER),
                Buildings.ARCANE_TOWER, ProductionItems.ROYAL_ARCHITECT,
                List.of(ProductionItems.WITCH, ProductionItems.IRON_GOLEM, ProductionItems.RAVAGER));
    }

    public final String name;
    public final Faction faction;
    public final Difficulty difficulty;
    final Kit kit;
    int capitolFailures = 0;
    final Random rng = new Random();
    BlockPos home;
    long lastAttackAt = 0;
    long startedAt = -1;

    public BotPlayer(String name, Faction faction, Difficulty difficulty, BlockPos home) {
        this.name = name;
        this.faction = faction;
        this.difficulty = difficulty;
        this.kit = kitFor(faction);
        this.home = home;
    }

    // playtest (Oct 10): two hard bots with 9 workers and one build site at a time were crushed by a player with
    // 80+ workers. Workers now grow with the clock (BAR players keep making constructors), and harder bots run
    // several build sites at once.
    long minutesIn = 0;
    int targetWorkers() {
        int base = switch (difficulty) { case EASY -> 5; case MEDIUM -> 7; case HARD -> 9; };
        int cap = switch (difficulty) { case EASY -> 10; case MEDIUM -> 18; case HARD -> 28; };
        int perMinute = switch (difficulty) { case EASY -> 0; case MEDIUM -> 1; case HARD -> 2; };
        return (int) Math.min(cap, base + minutesIn * perMinute / 2);
    }
    int maxConcurrentSites() {
        int base = switch (difficulty) { case EASY -> 1; case MEDIUM -> 2; case HARD -> 3; };
        return base + (int) (minutesIn / 8);
    }
    int attackArmySize() { return switch (difficulty) { case EASY -> 10; case MEDIUM -> 14; case HARD -> 18; } + (int) Math.min(20, minutesIn / 2); }
    long attackCooldownTicks() { return switch (difficulty) { case EASY -> 20 * 240; case MEDIUM -> 20 * 170; case HARD -> 20 * 120; }; }

    // ------------------------------------------------------------------ the think step, about once a second

    public void think(ServerLevel level, long gameTime) {
        if (startedAt < 0)
            startedAt = gameTime;
        minutesIn = (gameTime - startedAt) / (20 * 60);
        List<LivingEntity> mine = new ArrayList<>(), workers = new ArrayList<>(), army = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !name.equals(u.getOwnerName()) || !le.isAlive())
                continue;
            mine.add(le);
            if (le instanceof WorkerUnit) workers.add(le);
            else if (le instanceof AttackerUnit) army.add(le);
        }
        List<BuildingPlacement> buildings = new ArrayList<>();
        BuildingPlacement capitol = null;
        int unbuilt = 0;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (!name.equals(bp.ownerName) || bp.isDestroyedServerside)
                continue;
            buildings.add(bp);
            if (bp.getBuilding().isCapitol)
                capitol = bp;
            if (!bp.isBuilt)
                unbuilt++;
        }
        if (capitol != null)
            home = capitol.originPos;

        // 1) a capitol above all else. A player's readied start lays its capitol regardless of terrain; a bot gets
        // the same treatment if the ground round its home refuses every site (hills, trees, water), instead of
        // idling for the whole match
        if (capitol == null) {
            if (!workers.isEmpty() && !placeNear(level, kit.capitol(), home, workers, 3)) {
                capitolFailures++;
                if (capitolFailures == 1 || capitolFailures % 20 == 0) {
                    BlockPos surface = MiscUtil.getHighestNonAirBlock(level, home);
                    String why = BuildingValidators.getPlacementValidityError(level, kit.capitol(), surface, name,
                        Rotation.NONE, false, false, true);
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("[Bot] {} cannot place its capitol near {}: {}",
                        name, home, why);
                }
                if (capitolFailures >= 3) {
                    BlockPos surface = MiscUtil.getHighestNonAirBlock(level, home);
                    int take = Math.min(3, workers.size());
                    int[] ids = new int[take];
                    for (int i = 0; i < take; i++)
                        ids[i] = workers.get(i).getId();
                    BuildingServerEvents.placeBuilding(kit.capitol(), surface, Rotation.NONE, name, ids,
                        false, false, true, true);
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.info("[Bot] {} laid its capitol by decree at {}", name, surface);
                }
            }
            return;
        }

        // 2) workers from the capitol
        trainWorkers(capitol, workers.size());

        // 3) build sites need builders: any unbuilt or damaged building gets the nearest idle worker
        assignBuilders(level, buildings, workers);

        // 3b) reclaim: one idle worker walks to the nearest wreck in our territory (BAR players do this by habit)
        sendReclaimer(workers);

        // 4) expand the economy (a few sites at a time - more for harder bots - so the flow economy isn't buried)
        long minutes = (gameTime - startedAt) / (20 * 60);
        boolean tier2 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        if (unbuilt < maxConcurrentSites()) {
            int extractors = count(buildings, kit.extractor()), winds = count(buildings, kit.wind());
            int farms = count(buildings, kit.farm()), houses = count(buildings, kit.house());
            int armyBuildings = count(buildings, kit.armyBuilding());
            int pop = UnitServerEvents.getCurrentPopulation(name);
            int popCap = BuildingServerEvents.getTotalPopulationSupply(name);
            BlockPos patch = extractors < 2 + minutes ? freePatch(level) : null;
            // Each candidate build is tried in priority order and a failed placement FALLS THROUGH to the next one
            // (the soak test caught bots sitting on 850 metal forever because one bad patch blocked every build).
            boolean done = workers.isEmpty();
            // the T2 lab first once Tier 2 is researched (after the first army building, like a player)
            if (!done && tier2 && armyBuildings >= 1 && count(buildings, kit.t2Lab()) < 1)
                done = placeNear(level, kit.t2Lab(), layoutSlot(Layout.ARMY, armyBuildings + 1), workers, 2);
            if (!done && patch != null) {
                done = placeExtractor(level, patch, workers);
                if (!done)
                    badPatches.put(patch, gameTime);   // retry it later, not every think
            }
            if (!done && winds < 2 + minutes && winds <= extractors * 2 + 2)
                done = placeNear(level, kit.wind(), layoutSlot(Layout.WIND, winds), workers, 1);
            if (!done && energyFull() && count(buildings, converterFor()) < 1 + (int) (minutes / 6))
                done = placeNear(level, converterFor(), layoutSlot(Layout.CONVERTER, count(buildings, converterFor())), workers, 1);
            if (!done && farms < 1)
                done = placeNear(level, kit.farm(), layoutSlot(Layout.FARM, farms), workers, 1);
            // (no houses: there is no supply any more, like BAR)
            if (!done && armyBuildings < 1 + (int) (minutes / 4) && minutes >= 1)
                done = placeNear(level, kit.armyBuilding(), layoutSlot(Layout.ARMY, armyBuildings), workers, 2);
            if (!done && kit.tower() != null && count(buildings, kit.tower()) < 1 + (int) (minutes / 5) && minutes >= 2) {
                // a tower between home and the nearest threat
                BlockPos threat = nearestEnemyBuilding(home);
                BlockPos want = home;
                if (threat != null) {
                    double dx = threat.getX() - home.getX(), dz = threat.getZ() - home.getZ();
                    double len = Math.max(1, Math.hypot(dx, dz));
                    want = home.offset((int) (dx / len * 20), 0, (int) (dz / len * 20));
                }
                placeNear(level, kit.tower(), want, workers, 1);
            }
        }

        // 4b) tech: research tier 2 from ~5 minutes, then refit home extractors one at a time
        if (!tier2 && minutes >= 5 && capitol instanceof ProductionPlacement cp && cp.productionQueue.isEmpty())
            cp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        if (tier2) {
            boolean upgrading = false;
            for (BuildingPlacement bp : buildings)
                if (bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                        && bp instanceof ProductionPlacement pp && !pp.productionQueue.isEmpty())
                    upgrading = true;
            if (!upgrading)
                for (BuildingPlacement bp : buildings)
                    if (bp.isBuilt && bp.getUpgradeLevel() == 0 && bp.originPos.distSqr(home) < 45 * 45
                            && bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                            && bp instanceof ProductionPlacement pp) {
                        pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_EXTRACTOR);
                        break;
                    }
        }

        // 4c) the commander's signature ability when a fight is on near it (same rules as a player: cooldown)
        useCommanderAbility(level, mine);
        useFactionPowers(level, mine);

        // 5) train fighters: the repeat queue keeps army buildings running once seeded
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() == kit.armyBuilding() && bp instanceof ProductionPlacement pp) {
                pp.setRepeatQueue(true);
                if (pp.productionQueue.size() < 2)
                    pp.startProductionItem(kit.army().get(rng.nextInt(kit.army().size())));
            }

        // 5a) the T2 lab: T2 constructors first (1, hard 2), then keeps T2 units coming. No repeat queue here, or
        // it would loop the constructor too
        int t2Workers = 0;
        for (LivingEntity w : workers)
            if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(w))
                t2Workers++;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() == kit.t2Lab() && bp instanceof ProductionPlacement pp) {
                if (t2Workers < targetT2Workers())
                    queueT2Worker(pp);
                if (pp.productionQueue.size() < 2)
                    pp.startProductionItem(kit.t2Army().get(rng.nextInt(kit.t2Army().size())));
            }

        // 5b) late game: medium/hard bots raise their faction's T3 building and field experimentals
        fieldExperimentals(level, buildings, workers, minutes);

        // 6) defend: an enemy near the base pulls the whole army home
        BlockPos intruder = nearestEnemyUnit(level, home, 45);
        if (intruder != null) {
            order(army, UnitAction.ATTACK_MOVE, intruder);
            return;
        }

        // 7) attack in waves
        if (army.size() >= attackArmySize() && gameTime - lastAttackAt > attackCooldownTicks()) {
            BlockPos target = nearestEnemyBuilding(home);
            if (target != null) {
                lastAttackAt = gameTime;
                order(army, UnitAction.ATTACK_MOVE, target);
                return;
            }
        }

        // 7b) take the nearest capturable site we don't hold (from minute 4): send a few idle fighters to stand on it
        if (minutes >= 4 && claimSite(army))
            return;

        // 8) between waves, sweep the territory (from minute 3, so early armies stay home to defend)
        if (minutes >= 3)
            sweepTerritory(army, buildings);
    }

    // ------------------------------------------------------------------ helpers

    /** Sends up to 4 idle fighters to the nearest capturable site this bot (and its allies) don't own. */
    boolean claimSite(List<LivingEntity> army) {
        if (home == null)
            return false;
        net.minecraft.world.entity.Entity best = null;
        double bestD = 130 * 130;
        for (var p : com.solegendary.reignofnether.startpos.CapturePointServerEvents.getPoints()) {
            String o = com.solegendary.reignofnether.startpos.CapturePointServerEvents.ownerOf(p);
            if (p.isRemoved() || name.equals(o) || (!o.isEmpty() && AlliancesServerEvents.isAllied(name, o)))
                continue;
            double d = p.blockPosition().distSqr(home);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null)
            return false;
        List<LivingEntity> idle = new ArrayList<>();
        for (LivingEntity le : army) {
            if (le.distanceToSqr(best) < 36)
                return false;   // already holding it
            if (le instanceof Unit u && u.isIdle() && idle.size() < 4)
                idle.add(le);
        }
        if (idle.size() < 2)
            return false;
        order(idle, UnitAction.ATTACK_MOVE, best.blockPosition());
        return true;
    }

    int targetT2Workers() { return difficulty == Difficulty.HARD ? 2 : 1; }

    /** Queues one T2 constructor at the T2 lab unless one is already in its queue. */
    void queueT2Worker(ProductionPlacement lab) {
        for (var item : lab.productionQueue)
            if (item.item == kit.t2Worker())
                return;
        lab.startProductionItem(kit.t2Worker());
    }

    void trainWorkers(BuildingPlacement capitol, int workerCount) {
        if (workerCount >= targetWorkers() || !(capitol instanceof ProductionPlacement pp) || !capitol.isBuilt)
            return;
        if (pp.productionQueue.size() < 1)
            pp.startProductionItem(kit.worker());
    }

    /** Unbuilt and damaged buildings each get the nearest worker that isn't already building. */
    void assignBuilders(ServerLevel level, List<BuildingPlacement> buildings, List<LivingEntity> workers) {
        List<LivingEntity> free = new ArrayList<>();
        for (LivingEntity w : workers)
            if (w instanceof WorkerUnit wu && (wu.getBuildRepairGoal() == null || wu.getBuildRepairGoal().getBuildingTarget() == null))
                free.add(w);
        for (BuildingPlacement bp : buildings) {
            if (bp.isBuilt || free.isEmpty())
                continue;
            free.sort((a, b) -> Double.compare(a.blockPosition().distSqr(bp.originPos), b.blockPosition().distSqr(bp.originPos)));
            int take = Math.min(bp.getBuilding().isCapitol ? 3 : 2, free.size());
            int[] ids = new int[take];
            for (int i = 0; i < take; i++)
                ids[i] = free.remove(0).getId();
            UnitServerEvents.addActionItem(name, UnitAction.BUILD_REPAIR, -1, ids, bp.originPos, bp.originPos);
        }
    }

    /** Reused result list for UnitGrid queries (the think step runs on the server thread, one bot at a time). */
    final List<LivingEntity> scan = new ArrayList<>();

    /** Patches whose extractor placement failed, with the game time of the failure: skipped for two minutes. */
    final java.util.Map<BlockPos, Long> badPatches = new java.util.HashMap<>();

    /**
     * An extractor over a patch: the 5x5 footprint may sit anywhere that still covers the patch block, and the
     * ground may be a block off the recorded centre (terrain edits, a stamp next to a river), so try those first.
     */
    boolean placeExtractor(ServerLevel level, BlockPos patch, List<LivingEntity> workers) {
        level.getChunk(patch.getX() >> 4, patch.getZ() >> 4);
        for (int dy : new int[] { 0, -1, 1 })
            for (int[] o : new int[][] { {-2, -2}, {-1, -2}, {-2, -1}, {-3, -2}, {-2, -3}, {-1, -1}, {-3, -3}, {-1, -3}, {-3, -1} }) {
                BlockPos origin = new BlockPos(patch.getX() + o[0], patch.getY() + dy, patch.getZ() + o[1]);
                if (!BuildingValidators.isPlacementValid(level, kit.extractor(), origin, name, Rotation.NONE, false, false, true))
                    continue;
                workers.sort((u, v) -> Double.compare(u.blockPosition().distSqr(origin), v.blockPosition().distSqr(origin)));
                return BuildingServerEvents.placeBuilding(kit.extractor(), origin, Rotation.NONE, name,
                        new int[] { workers.get(0).getId() }, false, false, false, true) != null;
            }
        return false;
    }

    /** The nearest stamped metal patch with no building on it yet. */
    BlockPos freePatch(ServerLevel level) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : MetalPatches.getPatches(level)) {
            // cheap tests first: the building scans below are patches x buildings (twice), so skip them for any
            // patch that could not beat the best one anyway - in any patch order that leaves only a handful of scans
            double d = p.distSqr(home);
            if (d >= bestD)
                continue;
            Long failed = badPatches.get(p);
            if (failed != null && level.getGameTime() - failed < 20 * 120)
                continue;
            boolean taken = false;
            for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
                if (!bp.isDestroyedServerside && bp.originPos.distSqr(p) < 10 * 10) {
                    taken = true;
                    break;
                }
            if (taken)
                continue;
            // don't send workers to die: skip patches deep in enemy territory (near their buildings)
            BlockPos enemy = nearestEnemyBuildingAny(p);
            if (enemy != null && enemy.distSqr(p) < 45 * 45 && enemy.distSqr(p) < d)
                continue;
            bestD = d;
            best = p;
        }
        return best;
    }

    /** Fires the commander's ability once three or more enemy units are within its reach. */
    void useCommanderAbility(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le) || !(le instanceof Unit u))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility) || !a.isOffCooldown(u))
                    continue;
                int enemies = 0;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), 14, scan)) {
                    if (!(other instanceof Unit ou) || !other.isAlive() || other.distanceToSqr(le) > 14 * 14)
                        continue;
                    String o = ou.getOwnerName();
                    if (o != null && !o.equals(name) && !AlliancesServerEvents.isAllied(name, o))
                        enemies++;
                }
                if (enemies >= 3)
                    a.use(level, u, le.blockPosition());
            }
            fireDGun(level, le, u);
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.RaiseDead && a.isOffCooldown(u))
                    a.use(level, u, le.blockPosition());   // does nothing (and keeps its cooldown) without wrecks in reach
        }
    }

    /**
     * Faction powers (Crypt Tide, War-Drums, Bastion Aegis, Totem of the Pack, Magma Rupture, Holy Bell, Withering
     * Fog) on any unit that carries one: same rule as the commander's signature - only once three or more enemy
     * units are within 12 blocks. The ground-targeted ones (Crypt Tide, Magma Rupture, Withering Fog) are aimed at
     * the nearest of them; the rest are cast where the unit stands. The enemy scan only runs for a unit with a
     * power off cooldown, so an idle army costs nothing here.
     */
    void useFactionPowers(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!(le instanceof Unit u) || u.getAbilities() == null)
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                boolean aimed = a instanceof com.solegendary.reignofnether.ability.abilities.CryptTide
                        || a instanceof com.solegendary.reignofnether.ability.abilities.MagmaRupture
                        || a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog;
                if (!aimed && !(a instanceof com.solegendary.reignofnether.ability.abilities.WarDrums)
                        && !(a instanceof com.solegendary.reignofnether.ability.abilities.BastionAegis)
                        && !(a instanceof com.solegendary.reignofnether.ability.abilities.TotemOfThePack)
                        && !(a instanceof com.solegendary.reignofnether.ability.abilities.HolyBell))
                    continue;
                if (!a.isOffCooldown(u))
                    continue;
                boolean flyer = a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog;
                int enemies = 0;
                LivingEntity nearest = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), 12, scan)) {
                    if (!(other instanceof Unit ou) || !other.isAlive())
                        continue;
                    // the Bone Dragon flies high over the fight: measure its scan flat, or it never sees one
                    double d = flyer ? Math.pow(other.getX() - le.getX(), 2) + Math.pow(other.getZ() - le.getZ(), 2)
                            : other.distanceToSqr(le);
                    if (d > 12 * 12)
                        continue;
                    String o = ou.getOwnerName();
                    if (o == null || o.equals(name) || AlliancesServerEvents.isAllied(name, o))
                        continue;
                    enemies++;
                    if (d < best) {
                        best = d;
                        nearest = other;
                    }
                }
                if (enemies >= 3)
                    a.use(level, u, aimed ? nearest.blockPosition() : le.blockPosition());
            }
        }
    }

    /**
     * The D-gun at the nearest enemy within 12 blocks, aimed through it - but only with an energy reserve left
     * over (300+), so a bot never stalls its own economy to shoot. Hard bots save less, easy bots never shoot.
     */
    void fireDGun(ServerLevel level, LivingEntity commander, Unit u) {
        if (difficulty == Difficulty.EASY)
            return;
        for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
            if (!(a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun) || !a.isOffCooldown(u))
                continue;
            float reserve = difficulty == Difficulty.HARD ? 150 : 300;
            boolean canPay = false;
            for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
                if (r.ownerName.equals(name))
                    canPay = r.getEnergy() >= com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST + reserve;
            if (!canPay)
                return;
            LivingEntity target = null;
            double best = 12 * 12;
            for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, commander.getX(), commander.getZ(), 12, scan)) {
                if (!(other instanceof Unit ou) || !other.isAlive())
                    continue;
                String o = ou.getOwnerName();
                if (o == null || o.equals(name) || AlliancesServerEvents.isAllied(name, o))
                    continue;
                double d = other.distanceToSqr(commander);
                if (d < best) {
                    best = d;
                    target = other;
                }
            }
            if (target != null)
                a.use(level, u, target.blockPosition());
            return;
        }
    }

    /** Tries to place a building near the wanted position, spiralling outward over valid ground. */
    boolean placeNear(ServerLevel level, Building building, BlockPos want, List<LivingEntity> workers, int builders) {
        for (int ring = 0; ring <= 8; ring++) {
            for (int attempt = 0; attempt < (ring == 0 ? 1 : 8); attempt++) {
                double a = attempt * Math.PI / 4 + ring;
                int x = want.getX() + (int) (Math.cos(a) * ring * 4);
                int z = want.getZ() + (int) (Math.sin(a) * ring * 4);
                level.getChunk(x >> 4, z >> 4);   // an unloaded column reads as y=-64 (every bot capitol failed its first try)
                BlockPos surface = MiscUtil.getHighestNonAirBlock(level, new BlockPos(x, 0, z));   // the ground block: structures sit at origin.y+1
                if (BuildingValidators.isPlacementValid(level, building, surface, name, Rotation.NONE, false, false, true)) {
                    workers.sort((u, v) -> Double.compare(u.blockPosition().distSqr(surface), v.blockPosition().distSqr(surface)));
                    int take = Math.min(builders, workers.size());
                    int[] ids = new int[take];
                    for (int i = 0; i < take; i++)
                        ids[i] = workers.get(i).getId();
                    return BuildingServerEvents.placeBuilding(building, surface, Rotation.NONE, name, ids, false, false, false, true) != null;
                }
            }
        }
        return false;
    }

    int count(List<BuildingPlacement> buildings, Building b) {
        int n = 0;
        for (BuildingPlacement bp : buildings)
            if (bp.getBuilding() == b)
                n++;
        return n;
    }

    enum Layout { WIND, CONVERTER, FARM, HOUSE, ARMY }

    /**
     * A tidy BAR-style base instead of buildings dropped at random: "back" is away from the nearest enemy.
     * Wind generators fill rows of six behind the capitol (4-block pitch for their 3x3), converters sit at the
     * far end of the wind field, farms and houses line the two flanks, army buildings face the enemy in a row.
     * placeNear still spirals out from the slot when the ground there is bad, so this never blocks a build.
     */
    BlockPos layoutSlot(Layout kind, int index) {
        BlockPos threat = nearestEnemyBuilding(home);
        double bx = 0, bz = 1;   // default "back" = +z
        if (threat != null) {
            double dx = home.getX() - threat.getX(), dz = home.getZ() - threat.getZ();
            double len = Math.hypot(dx, dz);
            if (len > 1) { bx = dx / len; bz = dz / len; }
        }
        double px = -bz, pz = bx;   // perpendicular ("right")
        double back, side;
        switch (kind) {
            case WIND -> { back = 14 + (index / 6) * 4; side = (index % 6 - 2.5) * 4; }
            case CONVERTER -> { back = 14 + 4 * 3 + 2; side = (index % 2 == 0 ? -1 : 1) * (14 + (index / 2) * 4); }
            case FARM -> { back = 4 + index * 12; side = 27; }
            case HOUSE -> { back = (index / 2) * 8 - 4; side = (index % 2 == 0 ? -1 : 1) * 15 * (index % 4 < 2 ? 1 : -1); }
            default -> { back = -12; side = (index % 3 - 1) * 11; }
        }
        return home.offset((int) Math.round(bx * back + px * side), 0, (int) Math.round(bz * back + pz * side));
    }

    /** The faction's T3 building and the experimental it makes. */
    Building t3Building() {
        if (faction.equals(Factions.PIGLINS))
            return Buildings.FORTRESS;
        if (faction.equals(Factions.MONSTERS))
            return Buildings.STRONGHOLD;
        return Buildings.CASTLE;
    }

    ProductionItem t3Unit() {
        if (faction.equals(Factions.PIGLINS))
            return com.solegendary.reignofnether.building.production.ProductionItems.WAR_MAMMOTH;
        if (faction.equals(Factions.MONSTERS))
            return com.solegendary.reignofnether.building.production.ProductionItems.BONE_DRAGON;
        return com.solegendary.reignofnether.building.production.ProductionItems.SUN_COLOSSUS;
    }

    /**
     * BAR bots go experimental late: from minute 14 (hard 12) a medium/hard bot has a T2 constructor build its T3
     * lab near home, then keeps one experimental in production whenever it has 20 population free. Easy bots never do.
     */
    void fieldExperimentals(ServerLevel level, List<BuildingPlacement> buildings, List<LivingEntity> workers, long minutes) {
        if (difficulty == Difficulty.EASY || minutes < (difficulty == Difficulty.HARD ? 12 : 14))
            return;
        // Tier 3 research first (after Tier 2), at the capitol - same gate as a player
        boolean hasT2 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        boolean hasT3 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
        if (!hasT3) {
            BuildingPlacement cap = null;
            for (BuildingPlacement bp : buildings)
                if (bp.getBuilding().isCapitol && bp.isBuilt)
                    cap = bp;
            if (hasT2 && cap instanceof ProductionPlacement cp && cp.productionQueue.isEmpty())
                cp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            return;
        }
        BuildingPlacement t3 = null;
        for (BuildingPlacement bp : buildings)
            if (bp.getBuilding() == t3Building())
                t3 = bp;
        if (t3 == null) {
            // only a T2 constructor may raise the T3 lab (the server enforces it): the nearest one to home places
            // it, and other workers join the site through assignBuilders. None yet - train one at the T2 lab
            LivingEntity builder = null;
            for (LivingEntity w : workers)
                if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(w)
                        && (builder == null || w.blockPosition().distSqr(home) < builder.blockPosition().distSqr(home)))
                    builder = w;
            if (builder != null) {
                placeNear(level, t3Building(), layoutSlot(Layout.ARMY, 3), new ArrayList<>(List.of(builder)), 1);
            } else {
                for (BuildingPlacement bp : buildings)
                    if (bp.isBuilt && bp.getBuilding() == kit.t2Lab() && bp instanceof ProductionPlacement lab)
                        queueT2Worker(lab);
            }
            return;
        }
        if (!t3.isBuilt || !(t3 instanceof ProductionPlacement pp))
            return;
        int pop = UnitServerEvents.getCurrentPopulation(name);
        int popCap = BuildingServerEvents.getTotalPopulationSupply(name);
        boolean queued = false;
        for (var item : pp.productionQueue)
            if (item.item == t3Unit())
                queued = true;
        if (!queued && popCap - pop >= 20)
            pp.startProductionItem(t3Unit());
    }

    Building converterFor() {
        if (faction.equals(Factions.PIGLINS))
            return Buildings.ENERGY_CONVERTER_PIGLINS;
        if (faction.equals(Factions.MONSTERS))
            return Buildings.ENERGY_CONVERTER_MONSTERS;
        return Buildings.ENERGY_CONVERTER_VILLAGERS;
    }

    /** Energy over 80% of storage: the bot is floating energy, so a converter pays (what BAR players do). */
    boolean energyFull() {
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(name);
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                return r.getEnergy() > eco.energyStorage * 0.8f;
        return false;
    }

    void sendReclaimer(List<LivingEntity> workers) {
        if (home == null)
            return;
        net.minecraft.world.entity.Entity wreck = null;
        double best = 40 * 40;
        for (var w : com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks()) {
            if (w.isRemoved())
                continue;
            double d = w.blockPosition().distSqr(home);
            if (d < best) {
                best = d;
                wreck = w;
            }
        }
        if (wreck == null)
            return;
        for (LivingEntity le : workers) {
            if (le.distanceToSqr(wreck) < 9)
                return;   // someone is already on it
        }
        for (LivingEntity le : workers) {
            if (le instanceof Unit u && u.isIdle()
                    && !com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le)) {
                order(List.of(le), UnitAction.MOVE, wreck.blockPosition());
                return;
            }
        }
    }

    void order(List<LivingEntity> units, UnitAction action, BlockPos target) {
        if (units.isEmpty())
            return;
        int[] ids = new int[units.size()];
        for (int i = 0; i < units.size(); i++)
            ids[i] = units.get(i).getId();
        UnitServerEvents.addActionItem(name, action, -1, ids, target, target);
    }

    BlockPos nearestEnemyBuilding(BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos bestCapitol = null;
        double bestCapitolD = Double.MAX_VALUE;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || bp.ownerName.isEmpty()
                    || bp.ownerName.equals(name) || AlliancesServerEvents.isAllied(name, bp.ownerName)
                    || !PlayerServerEvents.isRTSPlayer(bp.ownerName))
                continue;
            double d = bp.originPos.distSqr(from);
            if (d < bestD) { bestD = d; best = bp.originPos; }
            if (bp.getBuilding().isCapitol && d < bestCapitolD) { bestCapitolD = d; bestCapitol = bp.originPos; }
        }
        return bestCapitol != null ? bestCapitol : best;
    }

    /** Nearest enemy building of any type (no capitol preference), or null. */
    BlockPos nearestEnemyBuildingAny(BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || bp.ownerName.isEmpty()
                    || bp.ownerName.equals(name) || AlliancesServerEvents.isAllied(name, bp.ownerName))
                continue;
            double d = bp.originPos.distSqr(from);
            if (d < bestD) { bestD = d; best = bp.originPos; }
        }
        return best;
    }

    /**
     * Between waves the army sweeps its territory: idle fighters patrol from wherever they stand out to the
     * bot's farthest extractor and back, so raids on expansions meet resistance. Any attack or defence order
     * cancels the patrol; it resumes once the army is idle again.
     */
    void sweepTerritory(List<LivingEntity> army, List<BuildingPlacement> buildings) {
        BlockPos farthest = null;
        double farD = 0;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor) {
                double d = bp.originPos.distSqr(home);
                if (d > farD) { farD = d; farthest = bp.originPos; }
            }
        if (farthest == null || farD < 30 * 30)
            return;   // nothing out there worth sweeping
        List<LivingEntity> idle = new ArrayList<>();
        for (LivingEntity le : army)
            if (le instanceof Unit u && u.isIdle()
                    && !com.solegendary.reignofnether.unit.PatrolServerEvents.isPatrolling(le.getId()))
                idle.add(le);
        if (idle.size() >= 3)
            order(idle, UnitAction.PATROL, farthest);
    }

    BlockPos nearestEnemyUnit(ServerLevel level, BlockPos from, double range) {
        BlockPos best = null;
        double bestD = range * range;
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(level, from.getX() + 0.5, from.getZ() + 0.5, range + 1, scan)) {
            if (!(le instanceof Unit u) || !le.isAlive())
                continue;
            String owner = u.getOwnerName();
            if (owner == null || owner.isEmpty() || owner.equals(name) || AlliancesServerEvents.isAllied(name, owner))
                continue;
            double d = le.blockPosition().distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = le.blockPosition();
            }
        }
        return best;
    }
}
