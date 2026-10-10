package com.solegendary.reignofnether.building.buildings.verdant;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.buildings.placements.StormOakPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Verdant Court T2 defence - the <b>Storm Oak</b> (design/verdant_court_plan.md, slice 4 / polish): a gnarled old oak
 * with a lightning rod in its crown (storm_oak.nbt, tools/gen_structures.py) that calls a bolt down on one enemy unit
 * every {@link StormOakPlacement#COOLDOWN_TICKS} ticks within {@link StormOakPlacement#RANGE} blocks, preferring the
 * one standing in the thickest knot of enemies. The bolt is particles plus indirect-magic damage - never a real
 * lightning entity, so it starts no fires and charges no creepers (see {@link StormOakPlacement}).
 * <p>
 * Built by Seedshapers and Elder Druids; the button needs Tier 2 and a finished Circle of Elders, and the oak itself
 * stays dormant on the server while its owner lacks Tier 2 (a scenario, a cheat, a research reset), like the Circle's
 * own produce gate. Balance: a Watchtower (100m/75e, 240 HP) needs up to three garrisoned units to fight; the Storm Oak
 * fights alone for about one archer's damage that never misses, so it costs more and sits in the T2 tier.
 */
public class StormOak extends Building implements RangeIndicatorAddon {

    public final static String buildingName = "Storm Oak";
    public final static String structureName = "storm_oak";
    public final static ResourceCost cost = ResourceCosts.STORM_OAK;

    public StormOak() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.LIGHTNING_ROD;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dark_oak_log.png");

        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.MOSSY_STONE_BRICKS);

        this.buildTimeModifier = 0.8f;
        this.explodeChance = 0.2f;
        this.maxHealth = 360d;

        setActiveAddon(RangeIndicatorAddon.class, this, true);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new StormOakPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dark_oak_log.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.STORM_OAK,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.CIRCLE_OF_ELDERS) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.storm_oak").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.storm_oak.tooltip1",
                    (int) StormOakPlacement.DAMAGE, StormOakPlacement.COOLDOWN_TICKS / 20, StormOakPlacement.RANGE).getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.storm_oak.tooltip2").getVisualOrderText()
            ),
            this
        );
    }

    @Override
    public int getRange(BuildingPlacement placement) {
        return StormOakPlacement.RANGE;
    }

    @Override
    public void updateHighlightBps(BuildingPlacement placement) {
        if (!placement.level.isClientSide())
            return;
        placement.getDataStorage().getData(RangeIndicatorAddon.HIGHLIGHT_BPS_CACHE).clear();
        placement.getDataStorage().getData(RangeIndicatorAddon.HIGHLIGHT_BPS_CACHE).addAll(MiscUtil.getRangeIndicatorCircleBlocks(placement.centrePos,
                getRange(placement) - BlockClientEvents.VISIBLE_BORDER_ADJ, placement.level));
    }

    @Override
    public boolean showOnlyWhenSelected(BuildingPlacement placement) {
        return true;
    }
}
