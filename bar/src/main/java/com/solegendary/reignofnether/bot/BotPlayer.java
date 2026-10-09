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

    /** What each faction uses for each job. */
    record Kit(Building capitol, Building house, Building farm, Building extractor, Building wind, Building armyBuilding,
               Building tower, ProductionItem worker, List<ProductionItem> army) { }

    static Kit kitFor(Faction faction) {
        if (faction.equals(Factions.PIGLINS))
            return new Kit(Buildings.CENTRAL_PORTAL, Buildings.PORTAL_POCKET, Buildings.NETHERWART_FARM,
                    Buildings.METAL_EXTRACTOR_PIGLINS, Buildings.WIND_GENERATOR_PIGLINS, Buildings.BASTION,
                    null, ProductionItems.GRUNT, List.of(ProductionItems.BRUTE, ProductionItems.HEADHUNTER));
        if (faction.equals(Factions.MONSTERS))
            return new Kit(Buildings.MAUSOLEUM, Buildings.HAUNTED_HOUSE, Buildings.PUMPKIN_FARM,
                    Buildings.METAL_EXTRACTOR_MONSTERS, Buildings.WIND_GENERATOR_MONSTERS, Buildings.GRAVEYARD,
                    Buildings.DARK_WATCHTOWER, ProductionItems.ZOMBIE_VILLAGER, List.of(ProductionItems.ZOMBIE, ProductionItems.SKELETON));
        return new Kit(Buildings.TOWN_CENTRE, Buildings.VILLAGER_HOUSE, Buildings.WHEAT_FARM,
                Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.WIND_GENERATOR_VILLAGERS, Buildings.BARRACKS,
                Buildings.WATCHTOWER, ProductionItems.VILLAGER, List.of(ProductionItems.VINDICATOR, ProductionItems.PILLAGER));
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

    int targetWorkers() { return switch (difficulty) { case EASY -> 5; case MEDIUM -> 7; case HARD -> 9; }; }
    int attackArmySize() { return switch (difficulty) { case EASY -> 10; case MEDIUM -> 14; case HARD -> 18; }; }
    long attackCooldownTicks() { return switch (difficulty) { case EASY -> 20 * 240; case MEDIUM -> 20 * 170; case HARD -> 20 * 120; }; }

    // ------------------------------------------------------------------ the think step, about once a second

    public void think(ServerLevel level, long gameTime) {
        if (startedAt < 0)
            startedAt = gameTime;
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

        // 4) expand the economy (one site at a time so the flow economy isn't buried in stall)
        long minutes = (gameTime - startedAt) / (20 * 60);
        if (unbuilt <= 1) {
            int extractors = count(buildings, kit.extractor()), winds = count(buildings, kit.wind());
            int farms = count(buildings, kit.farm()), houses = count(buildings, kit.house());
            int armyBuildings = count(buildings, kit.armyBuilding());
            int pop = UnitServerEvents.getCurrentPopulation(name);
            int popCap = BuildingServerEvents.getTotalPopulationSupply(name);
            BlockPos patch = extractors < 2 + minutes ? freePatch(level) : null;
            if (patch != null && !workers.isEmpty())
                placeNear(level, kit.extractor(), patch.offset(-2, 0, -2), workers, 1);
            else if (winds < 2 + minutes && winds <= extractors * 2 && !workers.isEmpty())
                placeNear(level, kit.wind(), home.offset(rng.nextInt(25) - 12, 0, rng.nextInt(25) - 12), workers, 1);
            else if (farms < 1 && !workers.isEmpty())
                placeNear(level, kit.farm(), home.offset(rng.nextInt(21) - 10, 0, rng.nextInt(21) - 10), workers, 1);
            else if (popCap - pop < 4 && !workers.isEmpty())
                placeNear(level, kit.house(), home.offset(rng.nextInt(29) - 14, 0, rng.nextInt(29) - 14), workers, 1);
            else if (armyBuildings < 1 + (int) (minutes / 4) && minutes >= 1 && !workers.isEmpty())
                placeNear(level, kit.armyBuilding(), home.offset(rng.nextInt(25) - 12, 0, rng.nextInt(25) - 12), workers, 2);
            else if (kit.tower() != null && count(buildings, kit.tower()) < 1 + (int) (minutes / 5) && minutes >= 2 && !workers.isEmpty()) {
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
        boolean tier2 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
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

        // 5) train fighters: the repeat queue keeps army buildings running once seeded
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() == kit.armyBuilding() && bp instanceof ProductionPlacement pp) {
                pp.setRepeatQueue(true);
                if (pp.productionQueue.size() < 2)
                    pp.startProductionItem(kit.army().get(rng.nextInt(kit.army().size())));
            }

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

        // 8) between waves, sweep the territory (from minute 3, so early armies stay home to defend)
        if (minutes >= 3)
            sweepTerritory(army, buildings);
    }

    // ------------------------------------------------------------------ helpers

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

    /** The nearest stamped metal patch with no building on it yet. */
    BlockPos freePatch(ServerLevel level) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : MetalPatches.getPatches(level)) {
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
            if (enemy != null && enemy.distSqr(p) < 45 * 45 && enemy.distSqr(p) < p.distSqr(home))
                continue;
            double d = p.distSqr(home);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Tries to place a building near the wanted position, spiralling outward over valid ground. */
    boolean placeNear(ServerLevel level, Building building, BlockPos want, List<LivingEntity> workers, int builders) {
        for (int ring = 0; ring <= 8; ring++) {
            for (int attempt = 0; attempt < (ring == 0 ? 1 : 8); attempt++) {
                double a = attempt * Math.PI / 4 + ring;
                int x = want.getX() + (int) (Math.cos(a) * ring * 4);
                int z = want.getZ() + (int) (Math.sin(a) * ring * 4);
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
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
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
