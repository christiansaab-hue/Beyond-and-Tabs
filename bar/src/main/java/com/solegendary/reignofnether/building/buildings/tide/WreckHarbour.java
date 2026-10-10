package com.solegendary.reignofnether.building.buildings.tide;

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
 * Tidewrought capitol - the <b>Wreck Harbour</b> (design/tidewrought_plan.md, slice 1): a beached galleon hull of
 * spruce and mangrove planks on a prismarine keel, waxed-copper fittings, a broken mast with a torn teal sail, dead
 * coral and dried kelp on a sandstone apron (wreck_harbour.nbt, tools/gen_structures.py). Trains Shipwrights and Gull
 * Spotters and researches Tier 2 in the same slots as the other capitols. Tier 2 only unlocks the extractor refit
 * until the Drydock (slice 3) exists, and Tier 3 is not offered at all - the Verdant Court's slice-1 rule: no research
 * that leads nowhere.
 */
public class WreckHarbour extends ProductionBuilding {

    public final static String buildingName = "Wreck Harbour";
    public final static String structureName = "wreck_harbour";
    public final static ResourceCost cost = ResourceCosts.WRECK_HARBOUR;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dark_prismarine.png");

    public WreckHarbour() {
        super(structureName, cost, true);
        this.name = buildingName;
        this.portraitBlock = Blocks.STRIPPED_MANGROVE_LOG;
        this.icon = ICON;

        this.maxHealth = 400d;
        this.buildTimeModifier = 0.3f; // about a minute with 3 workers, like the other capitols
        this.canAcceptResources = true;

        this.startingBlockTypes.add(Blocks.SMOOTH_SANDSTONE);
        this.startingBlockTypes.add(Blocks.PRISMARINE_BRICKS);

        this.productions.add(ProductionItems.SHIPWRIGHT, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.GULL_SPOTTER, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.RESEARCH_TIER_2, Keybindings.abilitySlot9);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.WRECK_HARBOUR,
            () -> false,
            () -> true,
            List.of(Component.translatable("buildings.reignofnether.wreck_harbour").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedPop(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.wreck_harbour.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.wreck_harbour.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
