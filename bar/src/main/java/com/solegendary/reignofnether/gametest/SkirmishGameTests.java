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

    /** Stamping a patch must leave ground an extractor can sit on, for every faction's extractor and windmill. */
    @GameTest(template = ARENA)
    public static void extractor_and_windmill_fit_on_a_stamped_patch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));   // on the grass layer of the arena
        MetalPatches.forgetAll();
        MetalPatches.stamp(level, centre.getX(), centre.getZ());
        if (!level.getBlockState(centre).is(MetalPatches.PATCH_BLOCK)) {
            // stamp() reads the surface heightmap; whichever y it chose, the patch block must be there
            boolean found = false;
            for (int y = centre.getY() - 3; y <= centre.getY() + 3 && !found; y++)
                found = level.getBlockState(new BlockPos(centre.getX(), y, centre.getZ())).is(MetalPatches.PATCH_BLOCK);
            if (!found)
                helper.fail("stamp() did not leave a patch block at " + centre);
        }
        int patchY = patchY(level, centre);
        BlockPos origin = new BlockPos(centre.getX() - 2, patchY + 1, centre.getZ() - 2);
        for (Building extractor : List.of(Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.METAL_EXTRACTOR_MONSTERS,
                Buildings.METAL_EXTRACTOR_PIGLINS)) {
            String err = BuildingValidators.getPlacementValidityError(level, extractor, origin, "tester",
                Rotation.NONE, false, false, true);
            if (err != null && !err.equals("building.reignofnether.must_be_nether"))
                helper.fail(extractor.structureName + " refused on a fresh patch: " + err);
        }
        BlockPos windOrigin = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Building wind : List.of(Buildings.WIND_GENERATOR_VILLAGERS, Buildings.WIND_GENERATOR_MONSTERS)) {
            String err = BuildingValidators.getPlacementValidityError(level, wind, windOrigin, "tester",
                Rotation.NONE, false, false, true);
            if (err != null)
                helper.fail(wind.structureName + " refused on flat grass: " + err);
        }
        MetalPatches.forgetAll();
        helper.succeed();
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

    static int patchY(ServerLevel level, BlockPos centre) {
        for (int y = centre.getY() + 3; y >= centre.getY() - 3; y--)
            if (level.getBlockState(new BlockPos(centre.getX(), y, centre.getZ())).is(MetalPatches.PATCH_BLOCK))
                return y;
        return centre.getY();
    }

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }
}
