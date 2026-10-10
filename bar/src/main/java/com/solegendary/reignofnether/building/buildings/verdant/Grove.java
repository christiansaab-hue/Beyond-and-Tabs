package com.solegendary.reignofnether.building.buildings.verdant;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Verdant Court T1 lab - the <b>Grove</b> (design/verdant_court_plan.md, slice 1): a training glade under a living arch
 * (grove.nbt, tools/gen_structures.py) that trains the Court's T1 army: Leafblades, Thornbows and Sentinel Treants.
 * Needs a finished Heartwood Hall, as the Barracks needs a Town Centre.
 */
public class Grove extends ProductionBuilding {

    public final static String buildingName = "Grove";
    public final static String structureName = "grove";
    public final static ResourceCost cost = ResourceCosts.GROVE;

    public Grove() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.FLOWERING_AZALEA;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/target_side.png");

        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.MOSSY_STONE_BRICKS);

        this.explodeChance = 0.2f;
        this.maxHealth = 150d;

        this.productions.add(ProductionItems.LEAFBLADE, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.THORNBOW, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.SENTINEL_TREANT, Keybindings.abilitySlot3);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/target_side.png"),
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == Buildings.GROVE,
                () -> false,
                () -> BuildingClientEvents.hasFinishedBuilding(Buildings.HEARTWOOD_HALL) ||
                        ResearchClient.hasCheat("modifythephasevariance"),
                List.of(
                        Component.translatable("buildings.reignofnether.grove").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.grove.tooltip1").getVisualOrderText()
                ),
                this
        );
    }
}
