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

    /** What each faction uses for each job. Piglins need nether ground, so bots can't play them yet. */
    record Kit(Building capitol, Building house, Building farm, Building extractor, Building wind, Building armyBuilding,
               ProductionItem worker, List<ProductionItem> army) { }

    static Kit kitFor(Faction faction) {
        if (faction.equals(Factions.MONSTERS))
            return new Kit(Buildings.MAUSOLEUM, Buildings.HAUNTED_HOUSE, Buildings.PUMPKIN_FARM,
                    Buildings.METAL_EXTRACTOR_MONSTERS, Buildings.WIND_GENERATOR_MONSTERS, Buildings.GRAVEYARD,
                    ProductionItems.ZOMBIE_VILLAGER, List.of(ProductionItems.ZOMBIE, ProductionItems.SKELETON));
        return new Kit(Buildings.TOWN_CENTRE, Buildings.VILLAGER_HOUSE, Buildings.WHEAT_FARM,
                Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.WIND_GENERATOR_VILLAGERS, Buildings.BARRACKS,
                ProductionItems.VILLAGER, List.of(ProductionItems.VINDICATOR, ProductionItems.PILLAGER));
    }

    public final String name;
    public final Faction faction;
    public final Difficulty difficulty;
    final Kit kit;
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

        // 1) a capitol above all else
        if (capitol == null) {
            if (!workers.isEmpty())
                placeNear(level, kit.capitol(), home, workers, 3);
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
        }

        // 5) train fighters whenever an army building is free
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() == kit.armyBuilding() && bp instanceof ProductionPlacement pp && pp.productionQueue.size() < 2)
                pp.startProductionItem(kit.army().get(rng.nextInt(kit.army().size())));

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
            }
        }
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
                BlockPos surface = MiscUtil.getHighestNonAirBlock(level, new BlockPos(x, 0, z)).above();
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
