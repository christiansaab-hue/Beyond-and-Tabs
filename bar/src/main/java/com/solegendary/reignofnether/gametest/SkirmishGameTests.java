package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlockData;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless smoke tests that CI runs on a real dedicated server ({@code ./gradlew runGameTestServer}). They cover the
 * class of bug that only shows up in-game: a building the server cannot load, a validator that refuses a freshly
 * stamped metal patch, state leaking between matches. Each test runs inside the {@code gametest_arena} template
 * (a 16x16 grass pad); template coordinates are relative to the arena's corner.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SkirmishGameTests {

    static final String ARENA = "gametest_arena";

    /** Every registered building's blueprint must load on the SERVER (data/), not only on the client (assets/). */
    @GameTest(template = ARENA)
    public static void every_building_blueprint_loads_on_the_server(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (b instanceof AbstractBridge || b.structureName == null || !seen.add(b.structureName))
                continue;
            if (BuildingBlockData.getBuildingNbt(b.structureName, level.getServer().getResourceManager()) == null)
                missing.add(b.structureName);
        }
        if (!missing.isEmpty())
            helper.fail("blueprints missing from data/reignofnether/structures: " + missing);
        helper.succeed();
    }

    /**
     * Stamping a patch must leave ground an extractor can sit on, for every faction's extractor and windmill.
     * The game-test arena is carved out of generated terrain, so the test builds its own grass platform high
     * above the terrain (y=200) where the heightmaps and the real ground agree, just like an open field.
     */
    @GameTest(template = ARENA)
    public static void extractor_and_windmill_fit_on_a_stamped_patch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        MetalPatches.forgetAll();
        MetalPatches.stamp(level, cx, cz);
        BlockPos centre = new BlockPos(cx, y, cz);
        if (!level.getBlockState(centre).is(MetalPatches.PATCH_BLOCK))
            helper.fail("stamp() did not leave a patch block at " + centre + " (solidTop says "
                + MetalPatches.solidTop(level, cx, cz) + ")");
        BlockPos origin = new BlockPos(cx - 2, y, cz - 2);   // origin = ground block
        for (Building extractor : List.of(Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.METAL_EXTRACTOR_MONSTERS,
                Buildings.METAL_EXTRACTOR_PIGLINS)) {
            String err = BuildingValidators.getPlacementValidityError(level, extractor, origin, "tester",
                Rotation.NONE, false, false, true);
            if (err != null)   // the Legion's extractor is exempt from the nether-terrain rule on purpose
                helper.fail(extractor.structureName + " refused on a fresh patch: " + err);
        }
        BlockPos windOrigin = new BlockPos(cx + 5, y, cz + 5);
        for (Building wind : List.of(Buildings.WIND_GENERATOR_VILLAGERS, Buildings.WIND_GENERATOR_MONSTERS,
                Buildings.WIND_GENERATOR_PIGLINS)) {
            String err = BuildingValidators.getPlacementValidityError(level, wind, windOrigin, "tester",
                Rotation.NONE, false, false, true);
            if (err != null)
                helper.fail(wind.structureName + " refused on flat grass: " + err);
        }
        MetalPatches.forgetAll();
        helper.succeed();
    }

    /**
     * A placed extractor or windmill must survive its first ticks as a construction site. Both name no starting
     * block types, so RoN used to queue nothing, see zero blocks placed and destroy the site at once (the windmill
     * that exploded the moment it was placed). Places one of each for real and checks they are still standing.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void placed_extractor_and_windmill_survive_as_sites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 210;
        int cx = base.getX() + 40, cz = base.getZ() + 40;   // away from the other platform test
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        MetalPatches.forgetAll();
        MetalPatches.stamp(level, cx, cz);
        String owner = "gametest_owner";
        var mex = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.METAL_EXTRACTOR_VILLAGERS, new BlockPos(cx - 2, y, cz - 2), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        var wind = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.WIND_GENERATOR_VILLAGERS, new BlockPos(cx + 5, y, cz + 5), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        var conv = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.ENERGY_CONVERTER_VILLAGERS, new BlockPos(cx - 7, y, cz + 5), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        if (mex == null || wind == null || conv == null)
            helper.fail("placeBuilding returned null (mex " + mex + ", wind " + wind + ", converter " + conv + ")");
        helper.runAfterDelay(40, () -> {
            for (var placement : List.of(mex, wind, conv)) {
                if (placement.isDestroyedServerside
                        || !com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(placement))
                    helper.fail(placement.getBuilding().structureName + " was destroyed right after being placed");
            }
            MetalPatches.forgetAll();
            helper.succeed();
        });
    }

    /** A commander carries its faction's signature ability, and the Horde's War Horn buffs an ally in range. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_war_horn_buffs_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var commander = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var ally = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (commander == null || ally == null) {
            helper.fail("could not create grunt units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ally.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        commander.setOwnerName("gametest_horde");
        ally.setOwnerName("gametest_horde");
        level.addFreshEntity(commander);
        level.addFreshEntity(ally);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        com.solegendary.reignofnether.ability.abilities.CommanderAbility horn = null;
        for (var a : commander.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility ca)
                horn = ca;
        if (horn == null) {
            helper.fail("commander has no CommanderAbility");
            return;
        }
        final var ability = horn;
        helper.runAfterDelay(5, () -> {
            ability.use(level, commander, commander.blockPosition());
            if (!ally.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST))
                helper.fail("War Horn did not give the ally Strength");
            if (ability.isOffCooldown(commander))
                helper.fail("War Horn did not go on cooldown");
            commander.discard();
            ally.discard();
            helper.succeed();
        });
    }

    /** Liveries are visual only: a dressed unit wears its faction kit but gains no armour (balance pillar). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void liveries_give_zero_armour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (soldier == null) {
            helper.fail("could not create a vindicator unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(9, 2, 9));
        soldier.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        soldier.setOwnerName("gametest_kingdom");
        double baseArmour = soldier.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        level.addFreshEntity(soldier);
        helper.runAfterDelay(90, () -> {
            if (soldier.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty())
                helper.fail("unit was never dressed");
            double armour = soldier.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (armour > baseArmour + 0.001)
                helper.fail("livery added armour: " + baseArmour + " -> " + armour);
            soldier.discard();
            helper.succeed();
        });
    }

    /** The colour palette is what the lobby, packets and saves agree on: ids unique, nonzero, textures named. */
    @GameTest(template = ARENA)
    public static void colour_palette_is_consistent(GameTestHelper helper) {
        Set<Integer> ids = new HashSet<>();
        for (PlayerPalette.Entry e : PlayerPalette.ENTRIES) {
            if (e.mapColorId() <= 0 || !ids.add(e.mapColorId()))
                helper.fail("palette colour " + e.name() + " has a bad or duplicate map colour id " + e.mapColorId());
            if (e.name().isBlank() || !e.name().equals(e.name().toLowerCase()))
                helper.fail("palette colour name must be a lowercase texture key: " + e.name());
        }
        if (PlayerPalette.firstFree(ids) == 0)
            helper.fail("firstFree returned 0 with a full palette");
        helper.succeed();
    }

    /** BAR reclaim: a fallen unit leaves a wreck, and a worker standing at it strips metal into its owner's pool. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void wrecks_drop_and_workers_reclaim_metal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_reclaimer";
        com.solegendary.reignofnether.resources.Resources pool = null;
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null) {
            pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        }
        pool.ore = 0;
        pool.oreFrac = 0;
        final var res = pool;

        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (soldier == null || worker == null) {
            helper.fail("could not create units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 12));
        soldier.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        soldier.setOwnerName("gametest_fallen");
        worker.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        worker.setOwnerName(owner);
        level.addFreshEntity(soldier);
        level.addFreshEntity(worker);
        helper.runAfterDelay(5, () -> soldier.kill());
        helper.runAfterDelay(15, () -> {
            var near = com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks().stream()
                .filter(w -> !w.isRemoved() && w.distanceToSqr(worker) < 16).toList();
            if (near.isEmpty()) {
                helper.fail("the fallen vindicator left no wreck");
                return;
            }
            float before = com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(near.get(0));
            com.solegendary.reignofnether.resources.WreckServerEvents.tickReclaim(level, 2f);
            float after = near.get(0).isRemoved() ? 0
                : com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(near.get(0));
            if (res.getMetal() <= 0)
                helper.fail("worker at the wreck reclaimed no metal (wreck " + before + " -> " + after + ")");
            if (after >= before)
                helper.fail("wreck did not lose metal: " + before + " -> " + after);
            if (com.solegendary.reignofnether.barfx.BarFx.lastNanoTime(worker) < 0)
                helper.fail("reclaiming worker sent no nanolathe beam");
            near.forEach(net.minecraft.world.entity.Entity::discard);
            worker.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(res);
            helper.succeed();
        });
    }

    /**
     * Area reclaim: a circle over four wrecks and two workers deals the wrecks out nearest-first, two each, the first
     * started at once (worker busy on it) and the second shift-queued; a wreck outside the circle is left alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void area_reclaim_splits_wrecks_between_workers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_area_reclaim";
        var a = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var b = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (a == null || b == null) {
            helper.fail("could not create workers");
            return;
        }
        BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
        int y = centre.getY(), z = centre.getZ();
        a.moveTo(helper.absolutePos(new BlockPos(2, 2, 8)).getX() + 0.5, y, z + 0.5, 0, 0);
        b.moveTo(helper.absolutePos(new BlockPos(13, 2, 8)).getX() + 0.5, y, z + 0.5, 0, 0);
        a.setOwnerName(owner);
        b.setOwnerName(owner);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        List<net.minecraft.world.entity.Entity> wrecks = new ArrayList<>();
        for (int x : new int[] { 4, 6, 9, 11 })   // A is nearest 4 then 6; B nearest 11 then 9
            wrecks.add(com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level,
                helper.absolutePos(new BlockPos(x, 2, 8)).getX() + 0.5, y, z + 0.5, 100f, null));
        var outside = com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level,
            centre.getX() + 0.5, y, helper.absolutePos(new BlockPos(8, 2, 15)).getZ() + 0.5, 100f, null);
        helper.runAfterDelay(3, () -> {
            try {
                if (wrecks.contains(null) || outside == null) {
                    helper.fail("test wrecks did not spawn");
                    return;
                }
                int handed = com.solegendary.reignofnether.unit.AreaCommands.issue(level, owner,
                    com.solegendary.reignofnether.unit.AreaCommands.MODE_RECLAIM, centre, 6,
                    new int[] { a.getId(), b.getId() }, false);
                if (handed != 4)
                    helper.fail("expected the 4 wrecks inside the circle to be handed out, got " + handed);
                var ta = com.solegendary.reignofnether.resources.WreckServerEvents.getReclaimTarget(a);
                var tb = com.solegendary.reignofnether.resources.WreckServerEvents.getReclaimTarget(b);
                if (ta != wrecks.get(0) || tb != wrecks.get(3))
                    helper.fail("each worker should start on the wreck nearest to it");
                if (com.solegendary.reignofnether.unit.interfaces.WorkerUnit.isIdle(a))
                    helper.fail("a worker with a reclaim order counts as idle (its queue would skip ahead)");
                var qa = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(a.getId());
                var qb = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(b.getId());
                if (qa.size() != 1 || qa.get(0).getTargetUnitId() != wrecks.get(1).getId()
                        || qa.get(0).getAction() != com.solegendary.reignofnether.unit.UnitAction.RECLAIM)
                    helper.fail("worker A should have its second-nearest wreck queued, queue size " + qa.size());
                if (qb.size() != 1 || qb.get(0).getTargetUnitId() != wrecks.get(2).getId())
                    helper.fail("worker B should have its second-nearest wreck queued, queue size " + qb.size());
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(a.getId());
                com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(b.getId());
                com.solegendary.reignofnether.resources.WreckServerEvents.clearReclaimTarget(a);
                com.solegendary.reignofnether.resources.WreckServerEvents.clearReclaimTarget(b);
                for (var w : wrecks)
                    if (w != null)
                        w.discard();
                if (outside != null)
                    outside.discard();
                a.discard();
                b.discard();
            }
        });
    }

    /**
     * Area attack: a circle over four enemy units and two fighters deals the enemies out nearest-first (each fighter
     * starts on the one nearest to it), then each fighter queues the rest of the circle so it doesn't idle; an own
     * unit inside the circle and an enemy outside it are never targeted.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void area_attack_splits_enemies_between_fighters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_area_attack";
        String enemy = "gametest_area_attack_foe";
        var type = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        List<com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit> all = new ArrayList<>();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
        int y = centre.getY(), z = centre.getZ();
        // fighters at x=2 and x=13, enemies at 4, 6, 9, 11, an own unit at 7 and an enemy outside the circle
        int[][] spots = { { 2, 8 }, { 13, 8 }, { 4, 8 }, { 6, 8 }, { 9, 8 }, { 11, 8 }, { 7, 8 }, { 8, 15 } };
        for (int i = 0; i < spots.length; i++) {
            var u = type.create(level);
            if (u == null) {
                helper.fail("could not create units");
                return;
            }
            BlockPos p = helper.absolutePos(new BlockPos(spots[i][0], 2, spots[i][1]));
            u.moveTo(p.getX() + 0.5, y, p.getZ() + 0.5, 0, 0);
            u.setOwnerName(i < 2 || i == 6 ? owner : enemy);
            level.addFreshEntity(u);
            all.add(u);
        }
        var a = all.get(0);
        var b = all.get(1);
        helper.runAfterDelay(3, () -> {
            try {
                int handed = com.solegendary.reignofnether.unit.AreaCommands.issue(level, owner,
                    com.solegendary.reignofnether.unit.AreaCommands.MODE_AUTO, centre, 6,
                    new int[] { a.getId(), b.getId() }, false);
                // 4 enemies, each fighter: its 2-target share + the other 2 as follow-ups
                if (handed != 8)
                    helper.fail("expected 8 attack orders (4 enemies x 2 fighters), got " + handed);
                if (a.getTargetGoal().getTarget() != all.get(2) || b.getTargetGoal().getTarget() != all.get(5))
                    helper.fail("each fighter should start on the enemy nearest to it");
                var qa = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(a.getId());
                var qb = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(b.getId());
                if (qa.size() != 3 || qa.get(0).getTargetUnitId() != all.get(3).getId()
                        || qa.get(0).getAction() != com.solegendary.reignofnether.unit.UnitAction.ATTACK)
                    helper.fail("fighter A should queue its second-nearest enemy next, queue size " + qa.size());
                if (qb.size() != 3 || qb.get(0).getTargetUnitId() != all.get(4).getId())
                    helper.fail("fighter B should queue its second-nearest enemy next, queue size " + qb.size());
                for (var q : List.of(qa, qb))
                    for (var item : q)
                        if (item.getTargetUnitId() == all.get(6).getId() || item.getTargetUnitId() == all.get(7).getId())
                            helper.fail("an own unit or an enemy outside the circle was targeted");
                helper.succeed();
            } finally {
                for (var u : all) {
                    com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(u.getId());
                    u.discard();
                }
            }
        });
    }

    /**
     * Player panel: the roster each player receives lists itself, then its allies, then enemies, and only its own
     * and its allies' lines carry income / commander health (an enemy's economy must never be sent).
     */
    @GameTest(template = ARENA)
    public static void player_panel_hides_enemy_economy(GameTestHelper helper) {
        String me = "gametest_panel_me", ally = "gametest_panel_ally", foe = "gametest_panel_foe";
        List<com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row> rows = List.of(
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(foe, "", true, 9, 90, 0.5f),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(ally, "", true, 3, 30, 1f),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(me, "", true, 2, 20, 0.25f));
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally);
        try {
            var list = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(me, rows);
            if (list.size() != 3 || !list.get(0).name().equals(me) || !list.get(1).name().equals(ally)
                    || !list.get(2).name().equals(foe))
                helper.fail("expected order me, ally, foe");
            else if (!list.get(0).hasAllyData() || !list.get(1).hasAllyData() || list.get(1).metalIncome() != 3)
                helper.fail("own and allied lines should carry income");
            else if (list.get(2).hasAllyData() || list.get(2).metalIncome() != 0 || list.get(2).commanderHp() >= 0)
                helper.fail("the enemy line leaked economy / commander data");
            else
                helper.succeed();
        } finally {
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally);
        }
    }

    /**
     * Nanolathe feedback: a worker standing at its own construction site sends NANO beam events while the site is
     * unfinished. Checks the per-worker record BarFx keeps for its rate limit, so it never depends on global counts.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void builders_send_nano_beams(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 230;
        int cx = base.getX() - 40, cz = base.getZ() + 40;   // away from the other platform tests
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        String owner = "gametest_nano";
        BlockPos origin = new BlockPos(cx, y, cz);
        var site = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.WIND_GENERATOR_VILLAGERS, origin, Rotation.NONE, owner, new int[0], false, false, true, true);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (site == null || worker == null) {
            helper.fail("could not set up the site (" + site + ") or the worker (" + worker + ")");
            return;
        }
        worker.moveTo(cx - 0.5, y + 1, cz + 0.5, 0, 0);   // just outside the footprint, within build range
        worker.setOwnerName(owner);
        level.addFreshEntity(worker);
        worker.getBuildRepairGoal().setBuildingTarget(site);
        helper.succeedWhen(() -> {
            if (com.solegendary.reignofnether.barfx.BarFx.lastNanoTime(worker) < 0)
                helper.fail("worker at its site sent no nano beam (building " + worker.getBuildRepairGoal().isBuilding()
                    + ", placed " + site.getBlocksPlaced() + "/" + site.getBlocksTotal() + ")");
            worker.discard();
        });
    }

    /** The commander D-gun: hits the enemy on the line, spares the friend beside it, charges energy, cools down. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_dgun_hits_the_line_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_dgunner";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(500);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var commander = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var enemy = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var offline = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (commander == null || enemy == null || friend == null || offline == null) {
            helper.fail("could not create grunt units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        enemy.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);     // on the line (+x)
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);    // on the line, but ours
        offline.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 6.5, 0, 0);   // enemy, well off the line
        commander.setOwnerName(owner);
        friend.setOwnerName(owner);
        enemy.setOwnerName("gametest_target");
        offline.setOwnerName("gametest_target");
        for (var e : List.of(commander, enemy, friend, offline))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        com.solegendary.reignofnether.ability.abilities.CommanderDGun found = null;
        for (var a : commander.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun d)
                found = d;
        if (found == null) {
            helper.fail("commander has no D-gun");
            return;
        }
        final var dgun = found;
        helper.runAfterDelay(5, () -> {
            float friendHp = friend.getHealth(), offHp = offline.getHealth();
            dgun.use(level, commander, BlockPos.containing(at.getX() + 12, at.getY(), at.getZ()));
            if (enemy.isAlive() && enemy.getMaxHealth() - enemy.getHealth() < 40)
                helper.fail("D-gun barely hurt the enemy on its line: " + enemy.getHealth() + "/" + enemy.getMaxHealth());
            if (friend.getHealth() < friendHp)
                helper.fail("D-gun hurt a friendly unit");
            if (offline.getHealth() < offHp)
                helper.fail("D-gun hurt a unit far off its line");
            if (pool.getEnergy() > 500 - com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST + 0.01)
                helper.fail("D-gun did not charge energy: " + pool.getEnergy());
            if (dgun.isOffCooldown(commander))
                helper.fail("D-gun did not go on cooldown");
            for (var e : List.of(commander, enemy, friend, offline))
                e.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.succeed();
        });
    }

    /** Horde Momentum: a straight charge builds the damage bonus, and stopping drops it. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void horde_momentum_builds_on_a_straight_charge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var grunt = com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get().create(level);
        if (grunt == null) {
            helper.fail("could not create a brute unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 8));
        grunt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        grunt.setOwnerName("gametest_horde_charge");
        level.addFreshEntity(grunt);
        var dmgAttr = com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get();
        helper.runAfterDelay(2, () -> {
            double base = grunt.getAttributeValue(dmgAttr);
            com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);
            for (int i = 1; i <= 6; i++) {
                grunt.teleportTo(grunt.getX() + 1.0, grunt.getY(), grunt.getZ());
                com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);
            }
            float m = com.solegendary.reignofnether.unit.MomentumServerEvents.getMomentum(grunt);
            if (m < 0.99f)
                helper.fail("six straight steps built only " + m + " momentum");
            if (grunt.getAttributeValue(dmgAttr) <= base + 0.001)
                helper.fail("full momentum gave no damage bonus (" + base + ")");
            com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);   // standing still
            if (com.solegendary.reignofnether.unit.MomentumServerEvents.getMomentum(grunt) > 0)
                helper.fail("momentum did not drop when the grunt stopped");
            if (Math.abs(grunt.getAttributeValue(dmgAttr) - base) > 0.001)
                helper.fail("damage bonus stuck after stopping");
            grunt.discard();
            helper.succeed();
        });
    }

    /** Sunforged Formation: a crossbow with two melee guards beside it is in formation; alone it is not. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sunforged_formation_needs_two_guards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_kingdom_line";
        var bow = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var lone = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var g1 = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var g2 = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (bow == null || lone == null || g1 == null || g2 == null) {
            helper.fail("could not create kingdom units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(12, 2, 3));
        bow.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        g1.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        g2.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        lone.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 12.5, 0, 0);
        for (var e : List.of(bow, lone, g1, g2)) {
            e.setOwnerName(owner);
            level.addFreshEntity(e);
        }
        helper.runAfterDelay(3, () -> {
            com.solegendary.reignofnether.unit.FormationServerEvents.update(level);
            var ronAttack = com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get();
            if (!com.solegendary.reignofnether.unit.FormationServerEvents.isInFormation(bow))
                helper.fail("crossbow with two guards beside it is not in formation");
            if (bow.getAttributeValue(ronAttack) <= lone.getAttributeValue(ronAttack) + 0.001)
                helper.fail("formation gave the crossbow no attack bonus");
            if (com.solegendary.reignofnether.unit.FormationServerEvents.isInFormation(lone))
                helper.fail("a lone crossbow counts as in formation");
            for (var e : List.of(bow, lone, g1, g2))
                e.discard();
            helper.succeed();
        });
    }

    /** Raise Dead: needs population room; with it, a wreck rises as a Ghoul and pays from the wreck first. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void raise_dead_turns_wrecks_into_ghouls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_lich";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var lich = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (lich == null) {
            helper.fail("could not create a zombie unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(13, 2, 13));
        lich.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        lich.setOwnerName(owner);
        level.addFreshEntity(lich);
        var wreck = com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level, at.getX() - 1.5,
            at.getY(), at.getZ() + 0.5, 200f, com.solegendary.reignofnether.faction.Factions.MONSTERS);
        helper.runAfterDelay(3, () -> {
            if (wreck == null || wreck.isRemoved()) {
                helper.fail("test wreck did not spawn");
                return;
            }
            int cap = com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation;   // no supply now: test the unit cap
            com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = 0;
            int none = com.solegendary.reignofnether.ability.abilities.RaiseDead.raise(level, lich, owner);
            com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = cap;
            if (none != 0)
                helper.fail("raised a Ghoul past the unit cap");
            com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(owner, "foodforthought");
            int popBefore = com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(owner);
            int raised = com.solegendary.reignofnether.ability.abilities.RaiseDead.raise(level, lich, owner);
            com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(owner, "foodforthought");
            if (raised != 1)
                helper.fail("expected one Ghoul from one wreck, got " + raised);
            float left = com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(wreck);
            if (left > 200f - com.solegendary.reignofnether.resources.ResourceCosts.ZOMBIE.metal() + 0.01f)
                helper.fail("the Ghoul was not paid from the wreck (" + left + " left)");
            if (pool.getMetal() < -0.01f || pool.getMetal() > 0.01f)
                helper.fail("pool paid although the wreck covered the cost: " + pool.getMetal());
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, lich.getBoundingBox().inflate(6)))
                if (e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName()))
                    e.discard();
            wreck.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.succeed();
        });
    }

    /** Energy converters only burn energy above half storage, at 50 energy per metal. */
    @GameTest(template = ARENA)
    public static void energy_converter_turns_spare_energy_into_metal(GameTestHelper helper) {
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy("gametest_alchemist");
        eco.conversionCapacity = 50f;
        var res = new com.solegendary.reignofnether.resources.Resources("gametest_alchemist", 0, 0, 0);
        res.addEnergy(900);
        for (int i = 0; i < 20; i++)
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(res, eco);
        if (Math.abs(res.getMetal() - 1f) > 0.05f || Math.abs(res.getEnergy() - 850f) > 1f)
            helper.fail("one second of one converter: expected ~1 metal / 850 energy, got " + res.getMetal()
                + " / " + res.getEnergy());
        var low = new com.solegendary.reignofnether.resources.Resources("gametest_alchemist", 0, 0, 0);
        low.addEnergy(400);
        com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(low, eco);
        if (low.getMetal() > 0)
            helper.fail("converter ran below half energy storage");
        eco.conversionCapacity = 0;
        helper.succeed();
    }

    /** Veterancy: killing an equal-cost enemy is one rank (+10% max health); ranks cap at 3. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void veterans_rank_up_on_kills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var vet = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (vet == null || foe == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 14));
        vet.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        vet.setOwnerName("gametest_veteran");
        foe.setOwnerName("gametest_victim");
        level.addFreshEntity(vet);
        level.addFreshEntity(foe);
        helper.runAfterDelay(3, () -> {
            double baseHp = vet.getMaxHealth();
            boolean hurt = foe.hurt(level.damageSources().indirectMagic(vet, vet), 10000f);   // mobAttack is rewritten to melee damage
            if (!foe.isDeadOrDying()) {   // the real kill path didn't land: report why, then test the rule directly
                helper.fail("foe survived a 10000 hit (hurt=" + hurt + ", hp=" + foe.getHealth() + ", invulnerable="
                    + foe.isInvulnerable() + ", invTime=" + foe.invulnerableTime + ")");
                return;
            }
            if (com.solegendary.reignofnether.unit.VeterancyServerEvents.getRank(vet) != 1)
                helper.fail("one equal-cost kill should be rank 1, got xp "
                    + com.solegendary.reignofnether.unit.VeterancyServerEvents.getXp(vet) + " (vet alive=" + vet.isAlive()
                    + ", vet cost=" + (vet.getCost() == null ? "null" : vet.getCost().metal()) + ", foe cost="
                    + (foe.getCost() == null ? "null" : foe.getCost().metal()) + ")");
            if (vet.getMaxHealth() < baseHp * 1.09)
                helper.fail("rank 1 gave no health: " + baseHp + " -> " + vet.getMaxHealth());
            com.solegendary.reignofnether.unit.VeterancyServerEvents.award(vet, 10f);
            if (com.solegendary.reignofnether.unit.VeterancyServerEvents.getRank(vet) != 3)
                helper.fail("rank should cap at 3");
            vet.discard();
            foe.discard();
            helper.succeed();
        });
    }

    /** The Horde's War Mammoth exists, belongs to the Horde, and obeys T3 rule (a): 4-6x a T2 heavy's cost. */
    @GameTest(template = ARENA)
    public static void war_mammoth_is_a_proper_t3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var mammoth = com.solegendary.reignofnether.registrars.EntityRegistrar.WAR_MAMMOTH_UNIT.get().create(level);
        if (mammoth == null) {
            helper.fail("could not create the War Mammoth");
            return;
        }
        var f = com.solegendary.reignofnether.faction.Factions.getFaction(mammoth);
        if (f == null || !f.equals(com.solegendary.reignofnether.faction.Factions.PIGLINS))
            helper.fail("War Mammoth is not a Horde unit: " + f);
        float ratio = (float) com.solegendary.reignofnether.resources.ResourceCosts.WAR_MAMMOTH.metal()
            / com.solegendary.reignofnether.resources.ResourceCosts.RAVAGER.metal();
        if (ratio < 4f || ratio > 6f)
            helper.fail("T3 rule (a): War Mammoth should cost 4-6x the Siege Ox, is " + ratio + "x");
        if (mammoth.getMaxHealth() < 1000)
            helper.fail("War Mammoth health " + mammoth.getMaxHealth());
        if (mammoth.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Trample))
            helper.fail("War Mammoth has no Trample");
        var ox = com.solegendary.reignofnether.registrars.EntityRegistrar.RAVAGER_UNIT.get().create(level);
        if (ox != null && ox.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Trample))
            helper.fail("Trample leaked onto the ordinary Siege Ox");
        if (ox != null)
            ox.discard();
        mammoth.discard();
        helper.succeed();
    }

    /** The Sun Colossus is a Sunforged T3 with the Solar Lance, and the lance hits only hostiles in its lane. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sun_colossus_lance_hits_its_lane(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var col = com.solegendary.reignofnether.registrars.EntityRegistrar.SUN_COLOSSUS_UNIT.get().create(level);
        var golem = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_GOLEM_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (col == null || golem == null || foe == null) {
            helper.fail("could not create units");
            return;
        }
        var f = com.solegendary.reignofnether.faction.Factions.getFaction(col);
        if (f == null || !f.equals(com.solegendary.reignofnether.faction.Factions.VILLAGERS))
            helper.fail("Sun Colossus is not Sunforged: " + f);
        if (col.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance))
            helper.fail("Sun Colossus has no Solar Lance");
        if (golem.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance))
            helper.fail("Solar Lance leaked onto the ordinary Iron Golem");
        golem.discard();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 10));
        col.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 9.5, at.getY(), at.getZ() + 0.5, 0, 0);
        col.setOwnerName("gametest_sun");
        foe.setOwnerName("gametest_shade");
        level.addFreshEntity(col);
        level.addFreshEntity(foe);
        helper.runAfterDelay(3, () -> {
            var from = col.position().add(0, 1.5, 0);
            var dir = new net.minecraft.world.phys.Vec3(1, 0, 0);
            int hit = com.solegendary.reignofnether.ability.abilities.SolarLance.fire(level, col, "gametest_sun",
                from, from.add(dir.scale(com.solegendary.reignofnether.ability.abilities.SolarLance.LENGTH)), dir);
            if (hit < 1 || (foe.isAlive() && foe.getMaxHealth() - foe.getHealth() < 30))
                helper.fail("Solar Lance did not land on the enemy in its lane (hit " + hit + ", hp " + foe.getHealth() + ")");
            col.discard();
            foe.discard();
            helper.succeed();
        });
    }

    /** The Gravebound Bone Dragon exists, is Gravebound, flies (a Ghast brain), and follows T3 rule (a). */
    @GameTest(template = ARENA)
    public static void bone_dragon_is_a_proper_t3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        if (dragon == null) {
            helper.fail("could not create the Bone Dragon");
            return;
        }
        var f = com.solegendary.reignofnether.faction.Factions.getFaction(dragon);
        if (f == null || !f.equals(com.solegendary.reignofnether.faction.Factions.MONSTERS))
            helper.fail("Bone Dragon is not Gravebound: " + f);
        if (!(dragon instanceof net.minecraft.world.entity.FlyingMob))
            helper.fail("Bone Dragon does not fly");
        float ratio = (float) com.solegendary.reignofnether.resources.ResourceCosts.BONE_DRAGON.metal()
            / com.solegendary.reignofnether.resources.ResourceCosts.RAVAGER.metal();
        if (ratio < 4f || ratio > 6f)
            helper.fail("T3 rule (a): Bone Dragon should cost 4-6x the Siege Ox, is " + ratio + "x");
        if (dragon.getMaxHealth() < 800)
            helper.fail("Bone Dragon health " + dragon.getMaxHealth());
        dragon.discard();
        helper.succeed();
    }

    /** Bone Dragon kills rise: needs population room, leaves no wreck, crumbles on time. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void dragon_risen_skeletons_are_free_and_temporary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wyrm";
        BlockPos at = helper.absolutePos(new BlockPos(14, 2, 2));
        int cap = com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation;   // no supply now: test the unit cap
        com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = 0;
        var none = com.solegendary.reignofnether.unit.DragonRaiseServerEvents.raise(level, owner, at);
        com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = cap;
        if (none != null)
            helper.fail("raised a skeleton past the unit cap");
        com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(owner, "foodforthought");
        var risen = com.solegendary.reignofnether.unit.DragonRaiseServerEvents.raise(level, owner, at);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(owner, "foodforthought");
        if (risen == null) {
            helper.fail("no skeleton rose with population room");
            return;
        }
        var where = risen.position();   // tests run in parallel: look for a wreck HERE, not at the global count
        ((net.minecraft.world.entity.LivingEntity) risen).kill();
        helper.runAfterDelay(2, () -> {
            if (com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks().stream()
                    .anyMatch(w -> !w.isRemoved() && w.position().distanceToSqr(where) < 4))
                helper.fail("a risen skeleton left a wreck (free metal)");
            helper.succeed();
        });
    }

    /** T2 constructors: one per faction, double build power and health, recognised as T2 workers. */
    @GameTest(template = ARENA)
    public static void t2_constructors_build_faster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = List.of(com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.EMBALMER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BONEWRIGHT_UNIT.get());
        var factions = List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
            com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.PIGLINS);
        for (int i = 0; i < types.size(); i++) {
            var e = types.get(i).create(level);
            if (e == null) {
                helper.fail("could not create T2 constructor " + i);
                return;
            }
            if (!(((Object) e) instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit w) || w.getBuildPower() < 1.99f)
                helper.fail(e.getType() + " is not a double-speed worker");
            if (!com.solegendary.reignofnether.unit.T2Workers.isT2Worker(e))
                helper.fail(e.getType() + " not recognised as a T2 worker");
            if (!factions.get(i).equals(com.solegendary.reignofnether.faction.Factions.getFaction((com.solegendary.reignofnether.unit.interfaces.Unit) e)))
                helper.fail(e.getType() + " has the wrong faction");
            if (((net.minecraft.world.entity.LivingEntity) e).getMaxHealth() < 49)
                helper.fail(e.getType() + " health " + ((net.minecraft.world.entity.LivingEntity) e).getMaxHealth());
            e.discard();
        }
        helper.succeed();
    }

    /** Capture points: an uncontested side takes a site over 20 s; an Ancient Mine then pays its owner metal. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void capture_point_is_taken_and_pays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        // straight above this test's own arena: an offset column (it was -60/+60) can land over another test's arena,
        // and a stone pad at y=220 there broke extractor_and_windmill_fit_on_a_stamped_patch's heightmap (solidTop)
        int cx = base.getX(), cz = base.getZ(), y = 220;
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.STONE.defaultBlockState(), 3);
        var point = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, cx, cz,
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.MINE);
        String owner = "gametest_captor";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (point == null || unit == null) {
            helper.fail("could not set up the capture point");
            return;
        }
        unit.moveTo(cx + 2.5, y + 1, cz + 0.5, 0, 0);
        unit.setOwnerName(owner);
        level.addFreshEntity(unit);
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < 25; i++)
                com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 1f);
            if (!owner.equals(com.solegendary.reignofnether.startpos.CapturePointServerEvents.ownerOf(point)))
                helper.fail("25 uncontested seconds did not capture the site (progress "
                    + com.solegendary.reignofnether.startpos.CapturePointServerEvents.progressOf(point) + ")");
            if (pool.getMetal() <= 0)
                helper.fail("the captured Ancient Mine paid no metal");
            unit.discard();
            point.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.succeed();
        });
    }

    static final int SOAK_TICKS = 20 * 60 * 4;   // four game-minutes of bot-vs-bot play

    /**
     * Soak: a Sunforged (villagers) bot and a Gravebound (monsters) bot play each other headless for four
     * game-minutes. Each must grow (lay buildings or train units) and neither brain may throw. The bases sit
     * ~1500 blocks from the test grid on dry ground so their battlefield (lanes, patches, ring wall <= 520 blocks)
     * never reaches the other tests' arenas.
     */
    @GameTest(template = ARENA, timeoutTicks = SOAK_TICKS + 600)
    public static void bots_play_each_other_without_errors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String kingdom = "gametest_soak_kingdom", grave = "gametest_soak_grave";
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        net.minecraft.world.phys.Vec3[] starts = soakStarts(level, origin.getX() + 1500, origin.getZ());
        if (starts == null) {
            helper.fail("no dry ground for two bot bases near x=" + (origin.getX() + 1500));
            return;
        }
        var bots = com.solegendary.reignofnether.bot.BotServerEvents.brains;
        var failures = com.solegendary.reignofnether.bot.BotServerEvents.thinkFailures;
        List<String> names = List.of(kingdom, grave);
        var factions = List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
            com.solegendary.reignofnether.faction.Factions.MONSTERS);
        for (int i = 0; i < 2; i++) {
            failures.remove(names.get(i));
            com.solegendary.reignofnether.player.PlayerServerEvents.startRTSBot(level, names.get(i), starts[i],
                factions.get(i), 0);
            bots.put(names.get(i), new com.solegendary.reignofnether.bot.BotPlayer(names.get(i), factions.get(i),
                com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM, BlockPos.containing(starts[i])));
        }
        for (String n : names)
            if (!com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(n))
                helper.fail("startRTSBot did not add " + n + " to the match");
        int[] startUnits = { soakUnits(kingdom).size(), soakUnits(grave).size() };
        int[] maxBuildings = new int[2], maxUnits = new int[2];
        Runnable sample = () -> {
            for (int i = 0; i < 2; i++) {
                maxBuildings[i] = Math.max(maxBuildings[i], soakBuildings(names.get(i)).size());
                maxUnits[i] = Math.max(maxUnits[i], soakUnits(names.get(i)).size());
            }
        };
        // a fresh Runnable per sample: GameTestInfo keys its delayed tasks by the Runnable itself, so scheduling the
        // same instance 239 times kept only the last one (the "max" counts were really just the final state)
        for (int t = 20; t < SOAK_TICKS; t += 20)
            helper.runAfterDelay(t, new Runnable() {
                @Override
                public void run() {
                    sample.run();
                }
            });
        // how the soak sides lose units (cause, killer, place, time; commander flagged) - a failure says why a side
        // stalled. Capped, and unregistered when the test ends.
        long t0 = level.getGameTime();
        List<String> deaths = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> onDeath = evt -> {
            if (evt.getEntity().level() != level || deaths.size() >= 6
                    || !(evt.getEntity() instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    || !names.contains(u.getOwnerName()))
                return;
            var src = evt.getSource();
            var killer = src.getEntity();
            deaths.add(u.getOwnerName().replace("gametest_soak_", "")
                + (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(evt.getEntity()) ? " COMMANDER " : " ")
                + net.minecraft.world.entity.EntityType.getKey(evt.getEntity().getType()).getPath()
                + " t" + (level.getGameTime() - t0) + " " + src.getMsgId()
                + (killer != null ? " by " + net.minecraft.world.entity.EntityType.getKey(killer.getType())
                    + (killer instanceof com.solegendary.reignofnether.unit.interfaces.Unit ku ? "(" + ku.getOwnerName() + ")" : "") : "")
                + " at " + evt.getEntity().blockPosition().toShortString());
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
            false, net.minecraftforge.event.entity.living.LivingDeathEvent.class, onDeath);
        helper.runAfterDelay(SOAK_TICKS, () -> {
            sample.run();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(onDeath);
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String n = names.get(i);
                Throwable err = failures.get(n);
                if (err != null)
                    problems.add(n + " think threw " + err);
                // capitol + at least two more: a bot that stops after its capitol has stalled (the old bad-patch bug)
                if (maxBuildings[i] < 3 || maxUnits[i] <= startUnits[i])
                    problems.add(n + " stalled (buildings " + maxBuildings[i] + ", units "
                        + startUnits[i] + " -> " + maxUnits[i] + (com.solegendary.reignofnether.player.PlayerServerEvents
                            .isRTSPlayer(n) ? "" : ", defeated") + ")");
            }
            if (!problems.isEmpty() && !deaths.isEmpty())
                problems.add("deaths " + deaths);
            ReignOfNether.LOGGER.info("[Soak] after {} ticks: {} buildings {} units {} (start {}), {} buildings {} units {} (start {})",
                SOAK_TICKS, kingdom, maxBuildings[0], maxUnits[0], startUnits[0], grave, maxBuildings[1], maxUnits[1],
                startUnits[1]);
            // clean up: leave the match, forget the brains, drop the armies (buildings stay, ownerless and far away)
            List<net.minecraft.world.entity.LivingEntity> leftovers = new ArrayList<>();
            for (String n : names)
                leftovers.addAll(soakUnits(n));
            for (String n : names) {
                com.solegendary.reignofnether.player.PlayerServerEvents.defeat(n, "gametest finished");
                bots.remove(n);
                failures.remove(n);
            }
            leftovers.forEach(net.minecraft.world.entity.Entity::discard);
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            helper.succeed();
        });
    }

    /** Two dry, fairly flat base sites 160 blocks apart, searched eastward from (x, z); null if none found. */
    static net.minecraft.world.phys.Vec3[] soakStarts(ServerLevel level, int x, int z) {
        // the seed is random per CI run, so one line of x can be all ocean: try a few parallel lines before giving up
        for (int bz : new int[] { z, z + 480, z - 480, z + 960, z - 960, z + 1440, z - 1440, z + 1920 })
            for (int step = 0; step < 16; step++) {
                int bx = x + step * 96;
                var a = soakDrySpot(level, bx, bz);
                if (a == null)
                    continue;
                var b = soakDrySpot(level, bx, bz + 160);
                if (b != null)
                    return new net.minecraft.world.phys.Vec3[] { a, b };
            }
        return null;
    }

    static net.minecraft.world.phys.Vec3 soakDrySpot(ServerLevel level, int x, int z) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int dx = -12; dx <= 12; dx += 12)
            for (int dz = -12; dz <= 12; dz += 12) {
                level.getChunk((x + dx) >> 4, (z + dz) >> 4);   // generate it, or the heightmap reads the void (forests: ignore leaves, or tree tops read as 10-block cliffs)
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                if (!level.getBlockState(new BlockPos(x + dx, y - 1, z + dz)).getFluidState().isEmpty())
                    return null;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        if (maxY - minY > 10)
            return null;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5);
    }

    static List<net.minecraft.world.entity.LivingEntity> soakUnits(String owner) {
        List<net.minecraft.world.entity.LivingEntity> out = new ArrayList<>();
        for (var le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
            if (le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName())
                    && le.isAlive())
                out.add(le);
        return out;
    }

    static List<com.solegendary.reignofnether.building.BuildingPlacement> soakBuildings(String owner) {
        List<com.solegendary.reignofnether.building.BuildingPlacement> out = new ArrayList<>();
        for (var bp : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings())
            if (owner.equals(bp.ownerName) && !bp.isDestroyedServerside)
                out.add(bp);
        return out;
    }

    /** Each faction's bot kit points at its real T2 lab, and that lab trains the kit's T2 constructor and T2 army. */
    @GameTest(template = ARENA)
    public static void bot_kits_use_their_t2_lab(GameTestHelper helper) {
        for (var f : List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
                com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.PIGLINS)) {
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(f);
            if (!(kit.t2Lab() instanceof com.solegendary.reignofnether.building.production.ProductionBuilding lab)) {
                helper.fail(f.getName() + ": T2 lab " + kit.t2Lab() + " is not a production building");
                return;
            }
            if (com.solegendary.reignofnether.unit.T2Workers.isT3Lab(lab))
                helper.fail(f.getName() + ": the T2 lab is a T3 lab");
            var items = lab.productions.get();
            if (!items.contains(kit.t2Worker()))
                helper.fail(f.getName() + ": " + lab.structureName + " does not train the kit's T2 constructor");
            for (var item : kit.t2Army())
                if (!items.contains(item))
                    helper.fail(f.getName() + ": " + lab.structureName + " does not train " + item);
        }
        helper.succeed();
    }

    /** The server refuses a T3 lab placed by T1 workers only; a T2 constructor among the builders passes. */
    @GameTest(template = ARENA)
    public static void t3_lab_needs_a_t2_builder_on_the_server(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 2, 8));
        var t1 = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var t2 = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        if (t1 == null || t2 == null) {
            helper.fail("could not create the workers");
            return;
        }
        t1.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        t2.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
        level.addFreshEntity(t1);
        level.addFreshEntity(t2);
        int[] t1Only = { t1.getId() }, withT2 = { t1.getId(), t2.getId() };
        for (Building lab : List.of(Buildings.CASTLE, Buildings.STRONGHOLD, Buildings.FORTRESS)) {
            if (com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, t1Only))
                helper.fail(lab.structureName + " may be placed by a T1 worker alone");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, withT2))
                helper.fail(lab.structureName + " refused with a T2 constructor among the builders");
        }
        if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, Buildings.ARCANE_TOWER, t1Only))
            helper.fail("a T2 lab must not need a T2 constructor");
        // the real placement path: a T1-only Castle is refused before it ever reaches the terrain checks
        String owner = "gametest_t3_owner";
        BlockPos pos = base.offset(80, 0, -80);
        var placed = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(Buildings.CASTLE, pos,
            Rotation.NONE, owner, t1Only, false, false, false, true);
        if (placed != null) {
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placed);
            helper.fail("placeBuilding accepted a Castle from a T1 worker");
        }
        t1.discard();
        t2.discard();
        helper.succeed();
    }

    /** Ground column for the slope test: dirt under a grass top at topY, solid down to bottomY. */
    private static void groundColumn(ServerLevel level, int x, int z, int bottomY, int topY) {
        for (int y = bottomY; y < topY; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, topY, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
    }

    /**
     * BAR forgives gentle slopes: a 1-block step under a (3x3) wind generator footprint must be accepted from either
     * side - terrain poking into the bottom layer from the low side, a 1-block gap from the high side - and a tuft of
     * grass in the way doesn't matter. A 4-block cliff must still be refused from the top (hanging over the drop)
     * and from the bottom (clipping the cliff face). Origin = the ground block, the footprint is x/z in [o, o+2].
     */
    @GameTest(template = ARENA)
    public static void wind_generator_accepts_a_step_but_not_a_cliff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int g = 245;
        int cx = base.getX() - 40, cz = base.getZ() - 40;   // away from the other platform tests
        Building wind = Buildings.WIND_GENERATOR_VILLAGERS;

        // 1) step, placed on the LOW side: columns x+1..x+2 are one block higher (inside the bottom layer)
        int ax = cx, az = cz;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, ax + dx, az + dz, g - 3, dx >= 1 ? g + 1 : g);
        level.setBlock(new BlockPos(ax, g + 1, az + 1), Blocks.GRASS.defaultBlockState(), 2);
        String err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(ax, g, az), "tester",
            Rotation.NONE, false, false, true);
        if (err != null)
            helper.fail("1-block step refused from the low side: " + err);

        // 2) step, placed on the HIGH side: only column x is high, x+1..x+2 have a 1-block gap under them
        int bx = cx + 12, bz = cz;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, bx + dx, bz + dz, g - 3, dx <= 0 ? g + 1 : g);
        err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(bx, g + 1, bz), "tester",
            Rotation.NONE, false, false, true);
        if (err != null)
            helper.fail("1-block step refused from the high side: " + err);

        // 3) 4-block cliff, placed on TOP: column x+2 hangs over a 4-block drop
        int ccx = cx, ccz = cz + 12;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, ccx + dx, ccz + dz, g - 7, dx <= 1 ? g : g - 4);
        err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(ccx, g, ccz), "tester",
            Rotation.NONE, false, false, true);
        if (err == null)
            helper.fail("wind generator accepted hanging over a 4-block cliff");

        // 4) the same cliff from the BOTTOM: the footprint would be buried 4 blocks into the cliff face
        int dxo = cx + 12, dzo = cz + 12;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, dxo + dx, dzo + dz, g - 7, dx >= 1 ? g : g - 4);
        err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(dxo, g - 4, dzo), "tester",
            Rotation.NONE, false, false, true);
        if (err == null)
            helper.fail("wind generator accepted at the foot of a 4-block cliff");

        helper.succeed();
    }

    /** Crypt Tide: the eruption roots and hurts an enemy inside the ring, and leaves a friend inside it alone. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void crypt_tide_roots_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_crypt_tide";
        var wraith = com.solegendary.reignofnether.registrars.EntityRegistrar.WRAITH_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (wraith == null || foe == null || friend == null) {
            helper.fail("could not create crypt tide units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        wraith.moveTo(at.getX() - 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        wraith.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_crypt_tide_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(wraith, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            var centre = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, foe.getY(), at.getZ() + 0.5);
            int hit = com.solegendary.reignofnether.ability.abilities.CryptTide.erupt(level, wraith, owner, centre);
            if (hit < 1)
                helper.fail("Crypt Tide caught no enemy");
            if (!foe.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                helper.fail("Crypt Tide did not root the enemy");
            if (foe.isAlive() && foe.getHealth() >= foeHp)
                helper.fail("Crypt Tide did not hurt the enemy");
            if (friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                helper.fail("Crypt Tide rooted a friendly unit");
            if (friend.getHealth() < friendHp)
                helper.fail("Crypt Tide hurt a friendly unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(wraith, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /** War-Drums: the speed modifier goes on the drummer and its friends nearby, never on an enemy beside them. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void war_drums_speed_friends_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_war_drums";
        var drummer = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (drummer == null || friend == null || foe == null) {
            helper.fail("could not create war drums units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        drummer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 4.5, 0, 0);
        drummer.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_war_drums_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(drummer, friend, foe))
            level.addFreshEntity(e);
        var speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
        helper.runAfterDelay(3, () -> {
            double friendBase = friend.getAttributeValue(speed), foeBase = foe.getAttributeValue(speed);
            var drummed = com.solegendary.reignofnether.ability.abilities.WarDrums.rally(level, drummer, owner);
            if (!drummed.contains(friend) || !com.solegendary.reignofnether.ability.abilities.WarDrums.isDrummed(friend))
                helper.fail("War-Drums did not reach a friendly unit 4 blocks away");
            if (!com.solegendary.reignofnether.ability.abilities.WarDrums.isDrummed(drummer))
                helper.fail("War-Drums did not buff the drummer itself");
            if (friend.getAttributeValue(speed) <= friendBase + 0.0001)
                helper.fail("War-Drums gave the friend no speed: " + friendBase + " -> " + friend.getAttributeValue(speed));
            if (drummed.contains(foe) || com.solegendary.reignofnether.ability.abilities.WarDrums.isDrummed(foe)
                    || foe.getAttributeValue(speed) > foeBase + 0.0001)
                helper.fail("War-Drums buffed an enemy unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(drummer, friend, foe))
                e.discard();
            helper.succeed();
        });
    }

    /** Bastion Aegis: a projectile hit on a friend inside the dome is soaked by the dome, not the friend. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bastion_aegis_soaks_a_projectile_for_a_friend(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bastion_aegis";
        var evoker = com.solegendary.reignofnether.registrars.EntityRegistrar.EVOKER_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (evoker == null || friend == null) {
            helper.fail("could not create aegis units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(11, 2, 11));
        evoker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        evoker.setOwnerName(owner);
        friend.setOwnerName(owner);
        level.addFreshEntity(evoker);
        level.addFreshEntity(friend);
        helper.runAfterDelay(3, () -> {
            var dome = com.solegendary.reignofnether.ability.abilities.BastionAegis.raise(level, evoker, owner, evoker.position());
            float hp = friend.getHealth();
            // a thrown projectile with no shooter: plain projectile damage, untouched by RoN's unit damage rewrite
            friend.hurt(level.damageSources().thrown(null, null), 10f);
            if (friend.getHealth() < hp - 0.01f)
                helper.fail("the dome let a projectile hit through: " + hp + " -> " + friend.getHealth());
            if (dome.remaining > com.solegendary.reignofnether.ability.abilities.BastionAegis.CAPACITY - 9.99f)
                helper.fail("the dome did not pay for the hit: " + dome.remaining + " left");
            com.solegendary.reignofnether.ability.abilities.BastionAegis.dispel(dome);
            evoker.discard();
            friend.discard();
            helper.succeed();
        });
    }

    /** Totem of the Pack: a friend dying by the totem calls up a spectral wolf for the owner; an enemy dying there doesn't. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void totem_of_the_pack_answers_friends_not_foes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pack_totem";
        var bonewright = com.solegendary.reignofnether.registrars.EntityRegistrar.BONEWRIGHT_UNIT.get().create(level);
        var grunt = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (bonewright == null || grunt == null || friend == null || foe == null) {
            helper.fail("could not create totem units");
            return;
        }
        // the totem lives on the Bonewright's own ability set, not on the shared Grunt one
        if (bonewright.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.TotemOfThePack))
            helper.fail("the Bonewright has no Totem of the Pack");
        if (grunt.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.TotemOfThePack))
            helper.fail("a plain Grunt got the Bonewright's totem");
        grunt.discard();
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 12));
        bonewright.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 2.5, 0, 0);
        bonewright.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_pack_totem_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(bonewright, friend, foe))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            var totem = com.solegendary.reignofnether.ability.abilities.TotemOfThePack.plant(level, bonewright, owner, bonewright.position());
            var wolf = com.solegendary.reignofnether.ability.abilities.TotemOfThePack.answerDeath(level, friend);
            var none = com.solegendary.reignofnether.ability.abilities.TotemOfThePack.answerDeath(level, foe);
            if (wolf == null)
                helper.fail("no spectral wolf rose for a friend dying by the totem");
            else if (!(wolf instanceof com.solegendary.reignofnether.unit.interfaces.Unit wu) || !owner.equals(wu.getOwnerName())
                    || !com.solegendary.reignofnether.ability.abilities.TotemOfThePack.isSpectral(wolf))
                helper.fail("the wolf is not a spectral wolf of the totem's owner");
            if (none != null) {
                none.discard();
                helper.fail("an enemy dying by the totem called up a wolf");
            }
            if (wolf != null)
                wolf.discard();
            com.solegendary.reignofnether.ability.abilities.TotemOfThePack.dispel(totem);
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(bonewright, friend, foe))
                e.discard();
            helper.succeed();
        });
    }

    /** Magma Rupture: the eruption burns and hurts an enemy on the crack, and leaves a friend on it alone. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void magma_rupture_burns_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_magma_rupture";
        var blaze = com.solegendary.reignofnether.registrars.EntityRegistrar.BLAZE_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (blaze == null || foe == null || friend == null) {
            helper.fail("could not create magma rupture units");
            return;
        }
        if (blaze.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.MagmaRupture))
            helper.fail("the Blaze has no Magma Rupture");
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 6));
        blaze.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0, 0);
        blaze.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_magma_rupture_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(blaze, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            var from = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, foe.getY(), at.getZ() + 0.5);
            var to = from.add(com.solegendary.reignofnether.ability.abilities.MagmaRupture.LENGTH, 0, 0);
            int hit = com.solegendary.reignofnether.ability.abilities.MagmaRupture.erupt(level, blaze, owner, from, to);
            if (hit < 1)
                helper.fail("Magma Rupture caught no enemy");
            if (foe.getRemainingFireTicks() <= 0)
                helper.fail("Magma Rupture did not set the enemy on fire");
            if (foe.isAlive() && foe.getHealth() >= foeHp)
                helper.fail("Magma Rupture did not hurt the enemy");
            if (friend.getRemainingFireTicks() > 0 || friend.getHealth() < friendHp)
                helper.fail("Magma Rupture burned a friendly unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(blaze, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /** Holy Bell: enemies in range glow, friends in range don't. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void holy_bell_reveals_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_holy_bell";
        var architect = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (architect == null || foe == null || friend == null) {
            helper.fail("could not create holy bell units");
            return;
        }
        if (architect.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.HolyBell))
            helper.fail("the Royal Architect has no Holy Bell");
        BlockPos at = helper.absolutePos(new BlockPos(13, 2, 13));
        architect.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 9.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 4.5, 0, 0);
        architect.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_holy_bell_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(architect, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            var revealed = com.solegendary.reignofnether.ability.abilities.HolyBell.ring(level, architect, owner);
            if (!revealed.contains(foe) || !foe.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                helper.fail("Holy Bell did not reveal an enemy 10 blocks away");
            if (revealed.contains(friend) || friend.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                helper.fail("Holy Bell marked a friendly unit");
            if (architect.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                helper.fail("Holy Bell marked the ringer");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(architect, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /** Holy Bell: the fog lifts around a rung-out enemy for the caster AND the caster's ally, never for the foe. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void holy_bell_reveals_to_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bell_ally_me", ally = "gametest_bell_ally_ally", foeOwner = "gametest_bell_ally_foe";
        var architect = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (architect == null || foe == null) {
            helper.fail("could not create holy bell units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(13, 2, 13));
        architect.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 7.5, at.getY(), at.getZ() + 0.5, 0, 0);
        architect.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(architect);
        level.addFreshEntity(foe);
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(owner, ally);
        helper.runAfterDelay(3, () -> {
            try {
                com.solegendary.reignofnether.ability.abilities.HolyBell.ring(level, architect, owner);
                if (!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isUnitRevealedTo(foe.getId(), owner))
                    helper.fail("Holy Bell did not lift the caster's fog around the enemy");
                else if (!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isUnitRevealedTo(foe.getId(), ally))
                    helper.fail("Holy Bell did not lift the ally's fog around the enemy");
                else if (com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isUnitRevealedTo(foe.getId(), foeOwner))
                    helper.fail("Holy Bell revealed the enemy to its own owner");
                else
                    helper.succeed();
            } finally {
                com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(owner, ally);
                architect.discard();
                foe.discard();
            }
        });
    }

    /** Withering Fog: a second in the cloud hurts and weakens an enemy inside, and leaves a friend inside alone. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void withering_fog_withers_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_withering_fog";
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get().create(level);
        if (dragon == null || foe == null || friend == null) {
            helper.fail("could not create withering fog units");
            return;
        }
        if (dragon.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog))
            helper.fail("the Bone Dragon has no Withering Fog");
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 3));
        dragon.moveTo(at.getX() + 0.5, at.getY() + 4, at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 10.5, at.getY(), at.getZ() + 0.5, 0, 0);
        dragon.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_withering_fog_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(dragon, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            var from = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, foe.getY() + 0.5, at.getZ() + 0.5);
            var to = from.add(com.solegendary.reignofnether.ability.abilities.WitheringFog.LENGTH, 0, 0);
            var cloud = com.solegendary.reignofnether.ability.abilities.WitheringFog.breathe(level, dragon, owner, from, to);
            // one pulse by hand: the test must not depend on the server tick lining up with the cloud's second
            int hit = com.solegendary.reignofnether.ability.abilities.WitheringFog.pulse(cloud);
            com.solegendary.reignofnether.ability.abilities.WitheringFog.dispel(cloud);
            if (hit < 1)
                helper.fail("Withering Fog touched no enemy");
            if (!foe.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS))
                helper.fail("Withering Fog did not weaken the enemy");
            if (foe.isAlive() && foe.getHealth() >= foeHp)
                helper.fail("Withering Fog did not hurt the enemy");
            if (friend.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS) || friend.getHealth() < friendHp)
                helper.fail("Withering Fog hurt a friendly unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(dragon, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /**
     * Sunrise Sortie: the Lord Marshal (and its melee escort) charges forward along the aimed line, hurts the enemy
     * it runs through, and leaves a friend standing on the path alone. The charge is stepped by hand.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sunrise_sortie_charges_through_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_sunrise_sortie";
        var marshal = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var escort = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (marshal == null || escort == null || friend == null || foe == null) {
            helper.fail("could not create sunrise sortie units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 7));
        marshal.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        escort.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
        marshal.setOwnerName(owner);
        escort.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_sunrise_sortie_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(marshal, escort, friend, foe))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(marshal);
        if (marshal.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SunriseSortie))
            helper.fail("the Sunforged commander has no Sunrise Sortie");
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            double x0 = marshal.getX();
            var charge = com.solegendary.reignofnether.ability.abilities.SunriseSortie.begin(level, marshal, owner,
                new net.minecraft.world.phys.Vec3(1, 0, 0));
            // stepped by hand: the test must not depend on the server tick running the charge
            com.solegendary.reignofnether.ability.abilities.SunriseSortie.cancel(charge);
            for (int i = 0; i < com.solegendary.reignofnether.ability.abilities.SunriseSortie.DURATION_TICKS; i++)
                com.solegendary.reignofnether.ability.abilities.SunriseSortie.step(charge);
            try {
                if (!charge.riders.contains(escort))
                    helper.fail("the melee escort did not join the sortie");
                if (charge.riders.contains(friend))
                    helper.fail("a ranged unit was swept into the melee sortie");
                if (marshal.getX() - x0 < 8)
                    helper.fail("the Marshal only charged " + (marshal.getX() - x0) + " blocks");
                if (foe.isAlive() && foe.getHealth() >= foeHp)
                    helper.fail("Sunrise Sortie did not hurt the enemy on its path");
                if (friend.getHealth() < friendHp)
                    helper.fail("Sunrise Sortie hurt a friendly unit on its path");
            } finally {
                for (var e : List.<net.minecraft.world.entity.LivingEntity>of(marshal, escort, friend, foe))
                    e.discard();
            }
            helper.succeed();
        });
    }

    /** Soul Wisps: Gravebound workers by the Embalmer reclaim faster, the wisps lash an enemy in reach and never a friend. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void soul_wisps_boost_reclaim_and_hit_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_soul_wisps";
        var embalmer = com.solegendary.reignofnether.registrars.EntityRegistrar.EMBALMER_UNIT.get().create(level);
        var digger = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get().create(level);
        var farDigger = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (embalmer == null || digger == null || farDigger == null || friend == null || foe == null) {
            helper.fail("could not create soul wisps units");
            return;
        }
        // the wisps live on the Embalmer's own ability set, not on the shared Gravedigger one
        if (embalmer.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SoulWisps))
            helper.fail("the Embalmer has no Soul Wisps");
        if (digger.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SoulWisps))
            helper.fail("a plain Gravedigger got the Embalmer's wisps");
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        embalmer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        digger.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        farDigger.moveTo(at.getX() + 10.5, at.getY(), at.getZ() + 10.5, 0, 0);
        // the friend stands closer than the foe: a pulse that took the nearest unit instead of the nearest enemy fails
        friend.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 4.5, 0, 0);
        embalmer.setOwnerName(owner);
        digger.setOwnerName(owner);
        farDigger.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_soul_wisps_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(embalmer, digger, farDigger, friend, foe))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float before = com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(digger);
            float foeHp = foe.getHealth(), friendHp = friend.getHealth(), diggerHp = digger.getHealth();
            var w = com.solegendary.reignofnether.ability.abilities.SoulWisps.summon(level, embalmer, owner);
            try {
                if (com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(digger) <= before)
                    helper.fail("Soul Wisps did not speed up a Gravebound worker's reclaim");
                if (com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(embalmer) <= 1f)
                    helper.fail("Soul Wisps did not speed up the Embalmer's own reclaim");
                if (com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(farDigger) != 1f)
                    helper.fail("Soul Wisps sped up a worker far out of reach");
                var hit = com.solegendary.reignofnether.ability.abilities.SoulWisps.pulse(w);
                if (hit != foe)
                    helper.fail("the wisps did not lash the enemy in reach");
                if (foe.isAlive() && foe.getHealth() >= foeHp)
                    helper.fail("the wisps did not hurt the enemy");
                if (friend.getHealth() < friendHp || digger.getHealth() < diggerHp)
                    helper.fail("the wisps hurt a friendly unit");
            } finally {
                com.solegendary.reignofnether.ability.abilities.SoulWisps.dispel(w);
                for (var e : List.<net.minecraft.world.entity.LivingEntity>of(embalmer, digger, farDigger, friend, foe))
                    e.discard();
            }
            helper.succeed();
        });
    }

    /**
     * Map drawing (Alt-drag lines / labels): the server allows 10 drawings per 5 s per player and relays each one to
     * the sender and their allies only. Uses its own limiter instance and unique names, so it can't collide with
     * other tests or real players.
     */
    @GameTest(template = ARENA)
    public static void map_drawing_is_rate_limited_and_ally_only(GameTestHelper helper) {
        var rules = new com.solegendary.reignofnether.minimap.MapDrawRules();
        java.util.UUID p = java.util.UUID.randomUUID(), other = java.util.UUID.randomUUID();
        long t0 = 1_000_000L;
        for (int i = 0; i < com.solegendary.reignofnether.minimap.MapDrawRules.MAX_STROKES; i++)
            if (!rules.tryConsume(p, t0 + i * 10L))
                helper.fail("drawing " + (i + 1) + " of 10 inside the window was refused");
        if (rules.tryConsume(p, t0 + 200))
            helper.fail("an 11th drawing inside 5 s was allowed");
        if (!rules.tryConsume(other, t0 + 200))
            helper.fail("one player's spam used up another player's budget");
        if (!rules.tryConsume(p, t0 + com.solegendary.reignofnether.minimap.MapDrawRules.WINDOW_MS + 1))
            helper.fail("drawing still refused after the 5 s window slid past the first stroke");

        String a = "gt_draw_" + p.toString().substring(0, 8), b = a + "_ally", c = a + "_enemy";
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(a, b);
        try {
            Set<String> to = com.solegendary.reignofnether.minimap.MapDrawRules.recipients(a, List.of(c, b, a),
                com.solegendary.reignofnether.alliance.AlliancesServerEvents::isAllied);
            if (!to.contains(a) || !to.contains(b))
                helper.fail("drawing not relayed to the sender and their ally: " + to);
            if (to.contains(c))
                helper.fail("drawing leaked to an enemy: " + to);
        } finally {
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(a, b);
        }

        if (com.solegendary.reignofnether.minimap.MapDrawRules.isValidStroke(new int[] { 0 }, new int[] { 0 }))
            helper.fail("a one-point stroke was accepted");
        if (com.solegendary.reignofnether.minimap.MapDrawRules.isValidStroke(new int[] { 0, 500 }, new int[] { 0, 0 }))
            helper.fail("a 500-block segment was accepted");
        String label = com.solegendary.reignofnether.minimap.MapDrawRules.sanitiseLabel("§cattack here now please hurry!!");
        if (label.length() > com.solegendary.reignofnether.minimap.MapDrawRules.MAX_LABEL_CHARS || label.contains("§"))
            helper.fail("label not sanitised: '" + label + "'");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 8v8 stress benchmark

    static final int STRESS_OWNERS = 8, STRESS_PER_OWNER = 50, STRESS_WRECKS = 40, STRESS_POINTS = 18;
    static final int STRESS_WARMUP = 10, STRESS_ITERATIONS = 40;
    /** Average budget for one pass of every system below, in ms. Most of them run once a second, so this is roomy. */
    static final double STRESS_BUDGET_MS = 8.0;

    /**
     * 8v8 scale: 400 units for eight test-only owners (three Sunforged, three Horde, two Gravebound armies whose
     * clusters overlap, so there are fights everywhere), {@link #STRESS_WRECKS} wrecks and {@link #STRESS_POINTS}
     * capture sites. Every scaling-sensitive server system is then run directly, {@link #STRESS_ITERATIONS} times,
     * and timed. The per-system averages are logged ("[Stress]") so a regression shows up in the CI log even while
     * the total stays under budget. Placed ~3000 blocks west of the test grid (the soak test goes east), spawned,
     * measured and removed inside one callback, so the units never tick and no other test ever sees them.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void stress_8v8_server_systems_stay_in_budget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        int baseX = origin.getX() - 3000, baseZ = origin.getZ();
        final double y = 150;   // floating in the air: no terrain dependence; these units are never ticked
        // eight overlapping 24x24 clusters on a 4x2 grid 20 blocks apart; their chunks are force-loaded meanwhile
        List<long[]> chunks = new ArrayList<>();
        for (int o = 0; o < STRESS_OWNERS; o++) {
            int cx = baseX + (o % 4) * 20, cz = baseZ + (o / 4) * 20;
            for (int dx = -16; dx <= 16; dx += 16)
                for (int dz = -16; dz <= 16; dz += 16) {
                    long[] c = { (cx + dx) >> 4, (cz + dz) >> 4 };
                    if (chunks.stream().noneMatch(k -> k[0] == c[0] && k[1] == c[1]))
                        chunks.add(c);
                }
        }
        for (long[] c : chunks)
            level.setChunkForced((int) c[0], (int) c[1], true);

        helper.runAfterDelay(40, () -> {
            var rng = new java.util.Random(8);
            List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
            List<com.solegendary.reignofnether.resources.Resources> pools = new ArrayList<>();
            try {
                for (int o = 0; o < STRESS_OWNERS; o++) {
                    String owner = "gametest_stress_" + o;
                    var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
                    com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
                    pools.add(pool);
                    int cx = baseX + (o % 4) * 20, cz = baseZ + (o / 4) * 20;
                    for (int i = 0; i < STRESS_PER_OWNER; i++) {
                        var e = stressUnitType(o, i).create(level);
                        if (e == null)
                            continue;
                        e.moveTo(cx + rng.nextDouble() * 24 - 12, y, cz + rng.nextDouble() * 24 - 12, rng.nextFloat() * 360, 0);
                        ((com.solegendary.reignofnether.unit.interfaces.Unit) e).setOwnerName(owner);
                        level.addFreshEntity(e);
                        spawned.add(e);
                    }
                }
                for (int i = 0; i < STRESS_WRECKS; i++) {
                    var w = com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level,
                        baseX - 12 + rng.nextDouble() * 84, y, baseZ - 12 + rng.nextDouble() * 44, 100000f, null);
                    if (w != null)
                        spawned.add(w);
                }
                for (int i = 0; i < STRESS_POINTS; i++) {
                    var m = net.minecraft.world.entity.EntityType.MARKER.create(level);
                    if (m == null)
                        continue;
                    m.moveTo(baseX - 12 + rng.nextDouble() * 84, y, baseZ - 12 + rng.nextDouble() * 44);
                    m.addTag(com.solegendary.reignofnether.startpos.CapturePointServerEvents.TAG);
                    level.addFreshEntity(m);
                    spawned.add(m);
                }

                // the real code paths (budgeted), then the pre-grid/pre-index ways as a logged reference only
                String[] names = { "unit grid rebuild", "wreck reclaim", "momentum", "formation", "capture points",
                    "queue lines", "player panel", "builder lookup x200", "bot enemy scans x64",
                    "REF builder lookup x200 (old scan)", "REF bot enemy scans x64 (old scan)" };
                int budgeted = 9;
                var buildings = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
                List<net.minecraft.world.entity.LivingEntity> scan = new ArrayList<>();
                Runnable[] systems = {
                    () -> {   // paid once per tick by the first grid query; forced here so every pass pays it
                        com.solegendary.reignofnether.unit.UnitGrid.invalidate();
                        com.solegendary.reignofnether.unit.UnitGrid.near(level, baseX, baseZ, 1, scan);
                    },
                    () -> com.solegendary.reignofnether.resources.WreckServerEvents.tickReclaim(level, 0.05f),
                    () -> com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level),
                    () -> com.solegendary.reignofnether.unit.FormationServerEvents.update(level),
                    () -> com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 0.05f),
                    com.solegendary.reignofnether.unit.UnitQueueSync::buildPayloads,
                    com.solegendary.reignofnether.player.PlayerPanelServerEvents::buildRows,
                    () -> {   // every building asks for its builders every tick; one index rebuild per tick (a death)
                        com.solegendary.reignofnether.building.BuilderIndex.invalidate();
                        int n = 0;
                        for (int b = 0; b < 200; b++)
                            n += com.solegendary.reignofnether.building.BuilderIndex.buildersOf(
                                buildings.isEmpty() ? null : buildings.get(b % buildings.size())).size();
                        stressSink += n;
                    },
                    () -> stressGridEnemyScans(level, 64, 12, scan),
                    () -> stressNaiveBuilderScans(200),
                    () -> stressNaiveEnemyScans(64, 12),
                };
                long[] total = new long[systems.length];
                for (int it = 0; it < STRESS_WARMUP + STRESS_ITERATIONS; it++) {
                    for (var pool : pools)   // keep reclaim paying (full storage would skip its work)
                        pool.ore = 0;
                    for (int s = 0; s < systems.length; s++) {
                        long t0 = System.nanoTime();
                        systems[s].run();
                        long dt = System.nanoTime() - t0;
                        if (it >= STRESS_WARMUP)
                            total[s] += dt;
                    }
                }
                // the grid must find exactly what the full scan finds
                int viaGrid = stressGridEnemyScans(level, 64, 12, scan), viaScan = stressNaiveEnemyScans(64, 12);
                if (viaGrid != viaScan)
                    helper.fail("UnitGrid found " + viaGrid + " enemies in reach, the full scan " + viaScan);
                double sum = 0;
                StringBuilder sb = new StringBuilder();
                for (int s = 0; s < systems.length; s++) {
                    double ms = total[s] / 1e6 / STRESS_ITERATIONS;
                    if (s < budgeted)
                        sum += ms;
                    sb.append(String.format(java.util.Locale.ROOT, "%s=%.3fms ", names[s], ms));
                }
                int units = 0;
                for (var e : spawned)
                    if (e instanceof net.minecraft.world.entity.LivingEntity)
                        units++;
                ReignOfNether.LOGGER.info("[Stress] {} units, {} wrecks, {} sites, avg per pass over {} passes: {}| total={}ms (budget {}ms)",
                    units, STRESS_WRECKS, STRESS_POINTS, STRESS_ITERATIONS, sb,
                    String.format(java.util.Locale.ROOT, "%.3f", sum), STRESS_BUDGET_MS);
                if (units < STRESS_OWNERS * STRESS_PER_OWNER * 9 / 10)
                    helper.fail("only " + units + " stress units spawned");
                else if (sum > STRESS_BUDGET_MS)
                    helper.fail(String.format(java.util.Locale.ROOT, "8v8 server systems took %.2f ms per pass (budget %.1f ms): %s",
                        sum, STRESS_BUDGET_MS, sb));
            } finally {
                for (var e : spawned)
                    e.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.removeAll(pools);
                // let the systems drop their now-removed entries (wreck list, capture sites, momentum/formation state)
                com.solegendary.reignofnether.resources.WreckServerEvents.tickReclaim(level, 0f);
                com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 0f);
                com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);
                com.solegendary.reignofnether.unit.FormationServerEvents.update(level);
                for (long[] c : chunks)
                    level.setChunkForced((int) c[0], (int) c[1], false);
            }
            helper.succeed();
        });
    }

    /** Owners 0-2 Sunforged, 3-5 Horde, 6-7 Gravebound; per owner 10 workers, then melee and ranged fighters. */
    static net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> stressUnitType(int owner, int i) {
        int kind = i < 10 ? 0 : i % 2 == 0 ? 1 : 2;   // worker, melee, ranged
        int faction = owner < 3 ? 0 : owner < 6 ? 1 : 2;
        switch (faction * 3 + kind) {
            case 0: return com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
            case 1: return com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
            case 2: return com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get();
            case 3: return com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get();
            case 4: return com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get();
            case 5: return com.solegendary.reignofnether.registrars.EntityRegistrar.HEADHUNTER_UNIT.get();
            case 6: return com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get();
            case 7: return com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get();
            default: return com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get();
        }
    }

    static int stressSink;   // keeps the JIT from discarding the reference scans below

    /** The pre-index BuildingPlacement.getBuilders body, run once per building as every tick did: the old cost. */
    static void stressNaiveBuilderScans(int buildings) {
        int n = 0;
        for (int b = 0; b < buildings; b++) {
            List<com.solegendary.reignofnether.unit.interfaces.WorkerUnit> builders = new ArrayList<>();
            for (var le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
                if (le instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit wu) {
                    var goal = wu.getBuildRepairGoal();
                    if (goal != null && goal.getBuildingTarget() != null && goal.isBuilding())
                        builders.add(wu);
                }
            n += builders.size();
        }
        stressSink += n;
    }

    /** The same check as {@link #stressNaiveEnemyScans}, the way BotPlayer does it now: through UnitGrid. */
    static int stressGridEnemyScans(ServerLevel level, int casters, double range,
                                     List<net.minecraft.world.entity.LivingEntity> scan) {
        var all = com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits();
        int n = 0;
        for (int c = 0; c < casters && c < all.size(); c++) {
            var le = all.get(all.size() - 1 - (c * 5) % all.size());
            if (!(le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u))
                continue;
            String name = u.getOwnerName();
            for (var other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), range, scan)) {
                if (!(other instanceof com.solegendary.reignofnether.unit.interfaces.Unit ou) || !other.isAlive()
                        || other.distanceToSqr(le) > range * range)
                    continue;
                String o = ou.getOwnerName();
                if (o != null && !o.equals(name)
                        && !com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(name, o))
                    n++;
            }
        }
        stressSink += n;
        return n;
    }

    /** A bot's "three enemies within reach?" check over every unit, for {@code casters} units (the old way). */
    static int stressNaiveEnemyScans(int casters, double range) {
        var all = com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits();
        int n = 0;
        for (int c = 0; c < casters && c < all.size(); c++) {
            var le = all.get(all.size() - 1 - (c * 5) % all.size());
            if (!(le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u))
                continue;
            String name = u.getOwnerName();
            for (var other : all) {
                if (!(other instanceof com.solegendary.reignofnether.unit.interfaces.Unit ou) || !other.isAlive()
                        || other.distanceToSqr(le) > range * range)
                    continue;
                String o = ou.getOwnerName();
                if (o != null && !o.equals(name)
                        && !com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(name, o))
                    n++;
            }
        }
        stressSink += n;
        return n;
    }

    /**
     * Results-screen graphs: each sample records the owner's metal/energy income and army value (metal cost of their
     * living units), the sample count grows one per call, and a long match stays capped with a doubled interval.
     * Uses its own owner name and a private RTSPlayer, so parallel tests can't change its numbers.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void match_history_samples_income_and_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_historian";
        var a = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var b = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (a == null || b == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        b.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        a.setOwnerName(owner);
        b.setOwnerName(owner);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
        helper.runAfterDelay(3, () -> {
            eco.metalIncome = 3f;
            eco.energyIncome = 25f;
            var player = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(owner,
                com.solegendary.reignofnether.faction.Factions.VILLAGERS);
            var hist = player.history;
            var players = List.of(player);
            for (int i = 1; i <= 3; i++) {
                com.solegendary.reignofnether.player.MatchHistory.sampleAll(players);
                if (hist.size() != i)
                    helper.fail("sample " + i + " not recorded, size " + hist.size());
            }
            float expectedArmy = a.getCost().metal() + b.getCost().metal();
            if (Math.abs(hist.metal[2] - 3f) > 0.01f || Math.abs(hist.energy[2] - 25f) > 0.01f)
                helper.fail("income sample wrong: " + hist.metal[2] + " metal/s, " + hist.energy[2] + " energy/s");
            if (expectedArmy <= 0 || Math.abs(hist.army[2] - expectedArmy) > 0.5f)
                helper.fail("army value " + hist.army[2] + ", expected " + expectedArmy);

            // a dead unit no longer counts
            a.discard();
            com.solegendary.reignofnether.player.MatchHistory.sampleAll(players);
            if (Math.abs(hist.army[3] - b.getCost().metal()) > 0.5f)
                helper.fail("army value after a loss " + hist.army[3] + ", expected " + b.getCost().metal());

            // a very long match: capped, interval doubled, the first sample kept
            for (int i = 0; i < 500; i++)
                hist.offer(1f, 2f, 3f);
            int max = com.solegendary.reignofnether.player.MatchHistory.MAX_SAMPLES;
            if (hist.size() > max || hist.size() < max / 2)
                helper.fail("history not capped: " + hist.size() + " samples");
            if (hist.getIntervalTicks() <= com.solegendary.reignofnether.player.MatchHistory.SAMPLE_TICKS)
                helper.fail("interval did not grow on compaction: " + hist.getIntervalTicks());
            if (Math.abs(hist.metal[0] - 3f) > 0.01f)
                helper.fail("compaction lost the first sample: " + hist.metal[0]);
            // the timeline must still span the whole match (samples * interval ~ calls * SAMPLE_TICKS)
            long span = (long) (hist.size() - 1) * hist.getIntervalTicks();
            long real = (long) (504 - 1) * com.solegendary.reignofnether.player.MatchHistory.SAMPLE_TICKS;
            if (span > real || span < real - hist.getIntervalTicks())
                helper.fail("compacted timeline " + span + " ticks does not match " + real);

            eco.metalIncome = 0;
            eco.energyIncome = 0;
            b.discard();
            helper.succeed();
        });
    }

    /**
     * Announcing a faction must not disturb the three live ones: each is still playable with a capitol, a worker and a
     * bot kit that agree, and the Verdant Court preview is registered (so the lobby can show it) but kept out of every
     * list a player, the title screen, survival or random could pick from. Static registration only, no world state.
     */
    @GameTest(template = ARENA)
    public static void preview_faction_leaves_the_three_live_factions_intact(GameTestHelper helper) {
        var live = List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
            com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.PIGLINS);
        for (var f : live) {
            if (!com.solegendary.reignofnether.faction.Factions.isLive(f)) {
                helper.fail(f.getName() + " is no longer a live faction");
                return;
            }
            if (com.solegendary.reignofnether.faction.Factions.getFaction(f.key) != f)
                helper.fail(f.getName() + " does not resolve by its key");
            if (!com.solegendary.reignofnether.faction.Factions.PLAYABLE_FACTIONS.contains(f.key)
                    || !com.solegendary.reignofnether.faction.Factions.CLASSIC_FACTIONS.contains(f.key))
                helper.fail(f.getName() + " dropped out of the playable / classic lists");
            Building capitol = ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding);
            if (capitol == null)
                helper.fail(f.getName() + ": capitol " + f.capitolBuilding + " is not a registered building");
            if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(f.workerEntityType) == null)
                helper.fail(f.getName() + ": worker " + f.workerEntityType + " is not a registered entity");
            if (com.solegendary.reignofnether.bot.BotPlayer.kitFor(f).capitol() != capitol)
                helper.fail(f.getName() + ": the bot kit builds a different capitol than the faction starts with");
        }
        var verdant = com.solegendary.reignofnether.faction.Factions.VERDANT_COURT;
        if (verdant == null || com.solegendary.reignofnether.faction.Factions.getFaction(verdant.key) != verdant) {
            helper.fail("Verdant Court preview is not registered");
            return;
        }
        if (!verdant.preview || com.solegendary.reignofnether.faction.Factions.isLive(verdant))
            helper.fail("Verdant Court must stay a preview until its units exist");
        if (com.solegendary.reignofnether.faction.Factions.PLAYABLE_FACTIONS.contains(verdant.key)
                || com.solegendary.reignofnether.faction.Factions.CLASSIC_FACTIONS.contains(verdant.key)
                || com.solegendary.reignofnether.faction.Factions.SURVIVAL_FACTIONS.contains(verdant.key))
            helper.fail("Verdant Court leaked into a pickable faction list");
        // registered after the originals, so their registry ids (Factions.getFaction(int)) are unchanged
        if (ReignOfNetherRegistries.FACTIONS.getId(verdant)
                < ReignOfNetherRegistries.FACTIONS.getId(com.solegendary.reignofnether.faction.Factions.NONE))
            helper.fail("Verdant Court was registered before the original factions and shifted their ids");
        helper.succeed();
    }

    /**
     * Faction look/mechanics come from one FactionTraits entry per faction. Each live faction and the Verdant preview
     * must have its own entry, Verdant must not borrow Sunforged's D-gun, wreck or scaffold (the old "otherwise
     * villagers" fall-through, design/verdant_court_plan.md), and a faction without an entry gets the neutral one.
     * Static data only, no world state.
     */
    @GameTest(template = ARENA)
    public static void every_faction_has_its_own_traits_and_verdant_is_not_sunforged(GameTestHelper helper) {
        var factions = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT);
        for (var f : factions)
            if (!FactionTraits.hasExplicit(f))
                helper.fail(f.getName() + " has no explicit FactionTraits entry");
        var sun = FactionTraits.of(Factions.VILLAGERS);
        var grave = FactionTraits.of(Factions.MONSTERS);
        var horde = FactionTraits.of(Factions.PIGLINS);
        var verdant = FactionTraits.of(Factions.VERDANT_COURT);
        var neutral = FactionTraits.NEUTRAL;
        if (verdant.dgunKind == sun.dgunKind)
            helper.fail("Verdant Court uses the Sunforged D-gun");
        if (com.solegendary.reignofnether.resources.WreckServerEvents.lookFor(Factions.VERDANT_COURT)
                == com.solegendary.reignofnether.resources.WreckServerEvents.lookFor(Factions.VILLAGERS))
            helper.fail("Verdant Court leaves Sunforged wrecks");
        if (verdant.scaffoldPole == sun.scaffoldPole || verdant.scaffoldRail == sun.scaffoldRail
                || verdant.scaffoldDecor == sun.scaffoldDecor)
            helper.fail("Verdant Court builds with Sunforged scaffold materials");
        // behaviours Verdant has not designed yet stay off
        if (verdant.formation || verdant.momentum
                || verdant.commanderAbility != com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.NONE
                || com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.VERDANT_COURT) != null)
            helper.fail("Verdant Court picked up an undesigned behaviour (formation/momentum/signature/bot kit)");
        // the live three keep what the old if/else chains gave them
        if (sun.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SUNFIRE
                || grave.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SOULREAPER
                || horde.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BLOODSTORM)
            helper.fail("a live faction's D-gun kind changed");
        if (!sun.formation || grave.formation || horde.formation || !horde.momentum || sun.momentum || grave.momentum)
            helper.fail("Formation must stay Sunforged-only and Momentum Horde-only");
        if (sun.reclaimMultiplier != 1f || grave.reclaimMultiplier != 2f || horde.reclaimMultiplier != 1.5f)
            helper.fail("a live faction's reclaim multiplier changed");
        if (sun.wreckBlock != Blocks.IRON_BLOCK.defaultBlockState() || grave.wreckBlock != Blocks.BONE_BLOCK.defaultBlockState()
                || horde.wreckBlock != Blocks.EXPOSED_CUT_COPPER.defaultBlockState())
            helper.fail("a live faction's wreck look changed");
        for (var f : List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS))
            if (com.solegendary.reignofnether.bot.BotPlayer.kitFor(f) == null)
                helper.fail(f.getName() + " lost its bot kit");
        // no entry -> neutral, never Sunforged
        if (FactionTraits.of(Factions.NEUTRAL) != neutral || FactionTraits.of(null) != neutral)
            helper.fail("a faction without an entry did not get the neutral traits");
        if (neutral.dgunKind == sun.dgunKind || neutral.formation || neutral.livery != null || neutral.wreckBlock == sun.wreckBlock)
            helper.fail("the neutral traits fall back to Sunforged behaviour");
        helper.succeed();
    }

    /** A Horde commander's D-gun / War-Drums must stay on the commander: Grunts used to share one static ability set. */
    @GameTest(template = ARENA)
    public static void commander_abilities_do_not_leak_to_plain_grunts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var commander = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var plain = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (commander == null || plain == null) {
            helper.fail("could not create grunts");
            return;
        }
        com.solegendary.reignofnether.player.CommanderServerEvents.ensureAbility(commander);
        boolean commanderHas = false;
        for (var a : commander.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                commanderHas = true;
        if (!commanderHas)
            helper.fail("the commander grunt did not get its D-gun");
        for (var a : plain.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                helper.fail("a plain grunt got the commander's D-gun");
        commander.discard();
        plain.discard();
        helper.succeed();
    }

    /** Alex's Mobs (and Citadel under it) must be loaded with our mod: its entity types are registered and spawnable. */
    @GameTest(template = ARENA)
    public static void alexs_mobs_is_loaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES;
        for (String path : new String[] {"grizzly_bear", "elephant"}) {
            ResourceLocation id = com.solegendary.reignofnether.compat.AlexsMobsCompat.id(path);
            // containsKey, not getValue: the entity registry is defaulted and returns a pig for unknown ids
            if (!types.containsKey(id)) {
                helper.fail("Alex's Mobs entity type not registered: " + id);
                return;
            }
            net.minecraft.world.entity.Entity e = types.getValue(id).create(level);
            if (e == null) {
                helper.fail("could not create " + id);
                return;
            }
            e.discard();
        }
        // the compile-linked reference must point at the same registered type as the name lookup
        if (com.solegendary.reignofnether.compat.AlexsMobsCompat.grizzlyBear()
                != types.getValue(com.solegendary.reignofnether.compat.AlexsMobsCompat.id("grizzly_bear")))
            helper.fail("AMEntityRegistry.GRIZZLY_BEAR differs from the registry entry");
        helper.succeed();
    }

    /**
     * Ability-leak audit: for EVERY unit type the mod registers, an ability added at runtime to one instance (what
     * CommanderServerEvents.ensureAbility and the T3 faction powers do) must not show up on a sibling made before or
     * after it - i.e. getAbilities() must never hand out the type's shared static set.
     */
    @GameTest(template = ARENA)
    public static void runtime_abilities_never_leak_between_siblings_of_any_unit_type(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (var type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !ReignOfNether.MOD_ID.equals(key.getNamespace()))
                continue;
            net.minecraft.world.entity.Entity a = null, b = null, c = null;
            try {
                a = type.create(level);
                b = type.create(level);
                if (!(a instanceof com.solegendary.reignofnether.unit.interfaces.Unit ua)
                        || !(b instanceof com.solegendary.reignofnether.unit.interfaces.Unit ub)
                        || ua.getAbilities() == null || ub.getAbilities() == null)
                    continue;
                var marker = new com.solegendary.reignofnether.ability.abilities.CommanderDGun();
                ua.getAbilities().add(marker);
                c = type.create(level);
                checked++;
                if (ub.getAbilities().get().contains(marker))
                    leaks.add(key.getPath() + " (existing sibling)");
                if (c instanceof com.solegendary.reignofnether.unit.interfaces.Unit uc && uc.getAbilities() != null
                        && uc.getAbilities().get().contains(marker))
                    leaks.add(key.getPath() + " (new sibling)");
                if (!ua.getAbilities().get().contains(marker))
                    leaks.add(key.getPath() + " (getAbilities() returns a fresh copy each call: runtime adds are lost)");
            } catch (Exception e) {
                // a type that cannot be built off-world is not what this test is about; it shows up elsewhere
            } finally {
                for (var e : new net.minecraft.world.entity.Entity[] {a, b, c})
                    if (e != null)
                        e.discard();
            }
        }
        if (checked < 20)
            helper.fail("only " + checked + " unit types were checked - the registry scan is broken");
        if (!leaks.isEmpty())
            helper.fail("runtime abilities leak between unit instances: " + leaks);
        helper.succeed();
    }

    /**
     * Every worker type that can be (or carry) a commander keeps the commander's runtime abilities - same instances,
     * so their cooldowns too - when updateAbilityButtons() re-clones from the static set (the client does that after
     * every cooldown sync), and its siblings never get them.
     */
    @GameTest(template = ARENA)
    public static void commander_abilities_survive_ability_button_refresh(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<net.minecraft.world.entity.EntityType<?>> types = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.EMBALMER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BONEWRIGHT_UNIT.get());
        List<String> problems = new ArrayList<>();
        for (var type : types) {
            var commander = type.create(level);
            var sibling = type.create(level);
            if (!(commander instanceof com.solegendary.reignofnether.unit.interfaces.Unit cu)
                    || !(sibling instanceof com.solegendary.reignofnether.unit.interfaces.Unit su)) {
                problems.add(type.getDescriptionId() + " could not be created");
                continue;
            }
            com.solegendary.reignofnether.player.CommanderServerEvents.ensureAbility(commander);
            com.solegendary.reignofnether.ability.Ability dgun = null;
            for (var a : cu.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                    dgun = a;
            int before = cu.getAbilities().get().size();
            cu.updateAbilityButtons();
            cu.updateAbilityButtons();
            if (dgun == null)
                problems.add(type.getDescriptionId() + ": no D-gun after ensureAbility");
            else if (!cu.getAbilities().get().contains(dgun))
                problems.add(type.getDescriptionId() + ": updateAbilityButtons() wiped the commander's D-gun");
            if (cu.getAbilities().get().size() != before)
                problems.add(type.getDescriptionId() + ": ability count changed on refresh " + before + " -> "
                    + cu.getAbilities().get().size());
            for (var a : su.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                    problems.add(type.getDescriptionId() + ": a plain sibling got the commander's D-gun");
            commander.discard();
            sibling.discard();
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        helper.succeed();
    }

    /**
     * BAR's commander blast: when a commander dies, nothing happens for the 1 s telegraph, then friend and foe within
     * 8 blocks take the blast, and a unit outside it does not.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_death_blast_hits_friend_and_foe_after_the_telegraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var reg = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get();
        var commander = reg.create(level);
        var friend = reg.create(level);
        var foe = reg.create(level);
        var outside = reg.create(level);
        if (commander == null || friend == null || foe == null || outside == null) {
            helper.fail("could not create grunt units");
            return;
        }
        // the commander stands in the middle of the 16x16 arena so the 8-block blast stays inside it (tests run in
        // parallel next to each other); the outsider sits in a corner, ~9.9 blocks away
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 3.5, 0, 0);
        outside.moveTo(at.getX() + 7.5, at.getY(), at.getZ() + 7.5, 0, 0);
        commander.setOwnerName("gametest_comblast");
        friend.setOwnerName("gametest_comblast");
        foe.setOwnerName("gametest_comblast_foe");
        outside.setOwnerName("gametest_comblast_foe");
        for (var e : List.of(commander, friend, foe, outside))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        final float[] hp = new float[3];
        final int delay = com.solegendary.reignofnether.player.CommanderServerEvents.COM_BLAST_DELAY_TICKS;
        helper.runAfterDelay(5, () -> {
            hp[0] = friend.getHealth();
            hp[1] = foe.getHealth();
            hp[2] = outside.getHealth();
            commander.kill();
        });
        // half-way through the telegraph: nobody hurt yet
        helper.runAfterDelay(5 + delay / 2, () -> {
            if (friend.getHealth() < hp[0] || foe.getHealth() < hp[1])
                helper.fail("the commander blast went off before its telegraph finished");
        });
        helper.runAfterDelay(5 + delay + 5, () -> {
            // 60 damage: either dead or well hurt (worker scuffles do a few points at most)
            if (friend.isAlive() && hp[0] - friend.getHealth() < 30)
                helper.fail("the commander blast did not hurt the friendly unit: " + friend.getHealth() + " (was " + hp[0] + ")");
            if (foe.isAlive() && hp[1] - foe.getHealth() < 30)
                helper.fail("the commander blast did not hurt the enemy unit: " + foe.getHealth() + " (was " + hp[1] + ")");
            if (!outside.isAlive() || outside.getHealth() < hp[2])
                helper.fail("the commander blast hurt a unit outside its radius");
            for (var e : List.of(commander, friend, foe, outside))
                e.discard();
            helper.succeed();
        });
    }

    /**
     * Wildlife never hunts a commander: neutralAggro points every wild hunter (Alex's Mobs roadrunners, rattlesnakes...)
     * at the closest unit, and a commander killed by one lost a 1v1 to a bird (the flaky bot soak). A wild wolf may
     * still go for an ordinary unit, and an enemy unit may still target the commander.
     */
    @GameTest(template = ARENA)
    public static void wildlife_does_not_hunt_commanders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var reg = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get();
        var commander = reg.create(level);
        var grunt = reg.create(level);
        var foe = reg.create(level);
        var wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
        if (commander == null || grunt == null || foe == null || wolf == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        grunt.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        wolf.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        commander.setOwnerName("gametest_wild_cmdr");
        grunt.setOwnerName("gametest_wild_cmdr");
        foe.setOwnerName("gametest_wild_foe");
        for (var e : List.<net.minecraft.world.entity.Entity>of(commander, grunt, foe, wolf))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        wolf.setTarget(commander);
        net.minecraft.world.entity.LivingEntity wolfOnCommander = wolf.getTarget();
        wolf.setTarget(grunt);
        net.minecraft.world.entity.LivingEntity wolfOnGrunt = wolf.getTarget();
        foe.setTarget(commander);
        net.minecraft.world.entity.LivingEntity foeOnCommander = foe.getTarget();
        for (var e : List.<net.minecraft.world.entity.Entity>of(commander, grunt, foe, wolf))
            e.discard();
        if (wolfOnCommander == commander)
            helper.fail("a wild wolf was allowed to target a commander");
        else if (wolfOnGrunt != grunt)
            helper.fail("a wild wolf could not target an ordinary unit (the guard is too broad)");
        else if (foeOnCommander != commander)
            helper.fail("an enemy unit could not target the commander");
        else
            helper.succeed();
    }

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }
}
