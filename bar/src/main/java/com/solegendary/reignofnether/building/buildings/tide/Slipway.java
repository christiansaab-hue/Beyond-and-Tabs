package com.solegendary.reignofnether.building.buildings.tide;

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
 * Tidewrought T1 lab - the <b>Slipway</b> (design/tidewrought_plan.md, slice 1): a timber launching ramp with a half-built
 * hull on its cradle and a cargo crane (slipway.nbt, tools/gen_structures.py) that trains the faction's T1 army:
 * Cutlass Raiders, Reef Guards, Bombard Crews and Tide Priests. Needs a finished Wreck Harbour, as the Barracks needs a
 * Town Centre.
 */
public class Slipway extends ProductionBuilding {

    public final static String buildingName = "Slipway";
    public final static String structureName = "slipway";
    public final static ResourceCost cost = ResourceCosts.SLIPWAY;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/spruce_boat.png");

    public Slipway() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.SPRUCE_PLANKS;
        this.icon = ICON;

        this.startingBlockTypes.add(Blocks.SMOOTH_SANDSTONE);
        this.startingBlockTypes.add(Blocks.PRISMARINE_BRICKS);

        this.explodeChance = 0.2f;
        this.maxHealth = 150d;

        this.productions.add(ProductionItems.CUTLASS_RAIDER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.REEF_GUARD, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.BOMBARD_CREW, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.TIDE_PRIEST, Keybindings.abilitySlot4);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ICON,
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == Buildings.SLIPWAY,
                () -> false,
                () -> BuildingClientEvents.hasFinishedBuilding(Buildings.WRECK_HARBOUR) ||
                        ResearchClient.hasCheat("modifythephasevariance"),
                List.of(
                        Component.translatable("buildings.reignofnether.slipway").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.slipway.tooltip1").getVisualOrderText()
                ),
                this
        );
    }
}
