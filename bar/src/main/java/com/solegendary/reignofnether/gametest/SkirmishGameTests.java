package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlockData;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
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
            near.forEach(net.minecraft.world.entity.Entity::discard);
            worker.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(res);
            helper.succeed();
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
            if (enemy.isAlive() && enemy.getHealth() >= enemy.getMaxHealth())
                helper.fail("D-gun did not hurt the enemy on its line");
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
        var dmgAttr = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
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
            if (!com.solegendary.reignofnether.unit.FormationServerEvents.isInFormation(bow))
                helper.fail("crossbow with two guards beside it is not in formation");
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
            if (com.solegendary.reignofnether.ability.abilities.RaiseDead.raise(level, lich, owner) != 0)
                helper.fail("raised a Ghoul with no population room");
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

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }
}
