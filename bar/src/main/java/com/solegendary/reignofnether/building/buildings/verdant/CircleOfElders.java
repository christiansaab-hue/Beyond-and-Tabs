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
 * Verdant Court T2 lab - the <b>Circle of Elders</b> (design/verdant_court_plan.md, slice 4): a ring of six living elder
 * trees round a moonlit well (circle_of_elders.nbt, tools/gen_structures.py). Trains the Court's T2 army - Stag Lancers,
 * Shade Rangers and Elder Treants - and the Elder Druid, its T2 constructor. Like the Arcane Tower, Dungeon and Flame
 * Sanctuary it needs Tier 2 research (at the Heartwood Hall) and the faction's T1 lab, the Grove.
 * <p>
 * The build button is the client-side gate; the server checks Tier 2 again whenever this lab starts an item
 * ({@link #tier2Error}, read by every Court T2 production item), so a lab that exists without the research - a
 * scenario, a cheat, a save from before a research reset - still cannot train T2.
 */
public class CircleOfElders extends ProductionBuilding {

    public final static String buildingName = "Circle of Elders";
    public final static String structureName = "circle_of_elders";
    public final static ResourceCost cost = ResourceCosts.CIRCLE_OF_ELDERS;
    public final static String NEEDS_TIER_2 = "hud.reignofnether.needs_tier_2";

    public CircleOfElders() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.FLOWERING_AZALEA_LEAVES;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/verdant_froglight_side.png");

        this.startingBlockTypes.add(Blocks.MOSS_BLOCK);
        this.startingBlockTypes.add(Blocks.MOSSY_STONE_BRICKS);

        this.buildTimeModifier = 0.7f;   // as the other T2 labs
        this.explodeChance = 0.2f;
        this.maxHealth = 340d;

        this.productions.add(ProductionItems.STAG_LANCER, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.SHADE_RANGER, Keybindings.abilitySlot2);
        this.productions.add(ProductionItems.ELDER_TREANT, Keybindings.abilitySlot3);
        this.productions.add(ProductionItems.ELDER_DRUID, Keybindings.abilitySlot4);   // T2 constructor
        this.productions.add(ProductionItems.WISP_CHOIR, Keybindings.abilitySlot5);       // anti-air
        this.productions.add(ProductionItems.BLOOM_PRIESTESS, Keybindings.abilitySlot6);  // support
    }

    /** Server and client: null if this lab's owner has Tier 2, else the message to show (the T2 items' produce gate). */
    @Nullable
    public static String tier2Error(ProductionPlacement pp) {
        if (pp == null)
            return NEEDS_TIER_2;
        boolean has = pp.level.isClientSide()
            ? ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)
            : ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_TIER_2);
        return has ? null : NEEDS_TIER_2;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/verdant_froglight_side.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.CIRCLE_OF_ELDERS,
            () -> false,
            () -> (BuildingClientEvents.hasFinishedBuilding(Buildings.GROVE) && ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_2)) ||   // T2 lab: needs Tier 2
                    ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.circle_of_elders").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.circle_of_elders.tooltip1").getVisualOrderText(),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.circle_of_elders.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
