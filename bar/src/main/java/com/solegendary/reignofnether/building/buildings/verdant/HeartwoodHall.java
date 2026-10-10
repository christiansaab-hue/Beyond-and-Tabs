package com.solegendary.reignofnether.building.buildings.verdant;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Verdant Court capitol - the <b>Heartwood Hall</b> (design/verdant_court_plan.md, slice 1): a living-wood pavilion
 * round a great oak (heartwood_hall.nbt, tools/gen_structures.py). Trains Seedshapers and Fox Couriers and researches
 * Tier 2 (which unlocks the extractor refit; the Court's T2 lab is a later slice). Tier 3 is not offered: the Court has
 * nothing to unlock with it yet, and a button that only drains the economy would be a trap.
 */
public class HeartwoodHall extends ProductionBuilding {

    public final static String buildingName = "Heartwood Hall";
    public final static String structureName = "heartwood_hall";
    public final static ResourceCost cost = ResourceCosts.HEARTWOOD_HALL;

    public HeartwoodHall() {
        super(structureName, cost, true);
        this.name = buildingName;
        this.portraitBlock = Blocks.OAK_LOG;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_leaves.png");

        this.maxHealth = 400d;
        this.buildTimeModifier = 0.3f; // about a minute with 3 workers, like the other capitols
        this.canAcceptResources = true;

        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.MOSSY_STONE_BRICKS);

        this.productions.add(ProductionItems.SEEDSHAPER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.FOX_COURIER, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.RESEARCH_TIER_2, Keybindings.abilitySlot9);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_leaves.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.HEARTWOOD_HALL,
            () -> false,
            () -> true,
            List.of(Component.translatable("buildings.reignofnether.heartwood_hall").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedPop(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.heartwood_hall.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.heartwood_hall.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
