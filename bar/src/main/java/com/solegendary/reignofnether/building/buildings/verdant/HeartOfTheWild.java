package com.solegendary.reignofnether.building.buildings.verdant;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Verdant Court T3 lab - the <b>Heart of the Wild</b>: a giant hollow elder tree, a living cathedral round a froglight
 * heart (heart_of_the_wild.nbt, tools/gen_structures.py). It grows the Court's experimental, the World Tree Walker.
 * Like the Castle, Stronghold and Fortress it needs Tier 3 research (at the Heartwood Hall), the faction's T2 lab
 * (the Circle of Elders) and a T2 constructor - the Elder Druid - among the selected builders; the server enforces
 * the last one too ({@code T2Workers.isT3Lab} / {@code buildersMayPlace}).
 * <p>
 * The build button is the client-side Tier 3 gate; the server checks Tier 3 again whenever this lab starts its
 * experimental ({@link #tier3Error}), so a lab that exists without the research still cannot grow one.
 */
public class HeartOfTheWild extends ProductionBuilding {

    public final static String buildingName = "Heart of the Wild";
    public final static String structureName = "heart_of_the_wild";
    public final static ResourceCost cost = ResourceCosts.HEART_OF_THE_WILD;
    public final static String NEEDS_TIER_3 = "hud.reignofnether.needs_tier_3";

    public HeartOfTheWild() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.FLOWERING_AZALEA_LEAVES;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_leaves.png");

        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.MOSSY_STONE_BRICKS);

        this.buildTimeModifier = 0.5f;   // as the other T3 labs
        this.maxHealth = 800d;

        this.productions.add(ProductionItems.WORLD_TREE_WALKER, Keybindings.abilitySlot4);   // Court T3 experimental
    }

    /** Server and client: null if this lab's owner has Tier 3, else the message to show (the experimental's gate). */
    @Nullable
    public static String tier3Error(ProductionPlacement pp) {
        if (pp == null)
            return NEEDS_TIER_3;
        boolean has = pp.level.isClientSide()
            ? ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
            : ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_TIER_3);
        return has ? null : NEEDS_TIER_3;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_leaves.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.HEART_OF_THE_WILD,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.CIRCLE_OF_ELDERS)
                    && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)
                    && com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker())   // only a T2 constructor can raise the T3 lab
                || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.heart_of_the_wild").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.heart_of_the_wild.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.heart_of_the_wild.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
