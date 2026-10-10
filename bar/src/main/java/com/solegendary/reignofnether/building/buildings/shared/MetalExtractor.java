package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * BAR's metal extractor: the backbone of the economy. May only be placed on a metal patch (MetalPatches), gives a
 * steady metal income, and taking map control means taking more patches. One shared structure; each faction
 * registers its own instance.
 */
public class MetalExtractor extends com.solegendary.reignofnether.building.production.ProductionBuilding {

    public final static String buildingName = "Metal Extractor";
    public final static String structureName = "metal_extractor";
    public final static String upgradedStructureName = "metal_extractor_t2";
    /** Metal per second at tier 2 (tier 1 is metalIncome). */
    public final static float T2_METAL_INCOME = 7.0f;
    public final static ResourceCost cost = ResourceCosts.METAL_EXTRACTOR;

    /** "" (Kingdom), "_dark" (The Fallen), "_nether" (The Gilded Legion), "_verdant" (Verdant Court) or "_tide" (Tidewrought); picks the faction's structure skin. */
    public final String variant;

    public MetalExtractor() {
        this("");
    }

    public MetalExtractor(String variant) {
        super(structureName + variant, cost, false);
        this.variant = variant;
        this.name = buildingName;
        this.portraitBlock = Blocks.RAW_IRON_BLOCK;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/raw_iron_block.png");

        this.metalIncome = 2.0f;

        this.explodeChance = 0.2f;
        this.maxHealth = 40d;
        this.canSetRallyPoint = false;

        this.productions.add(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_EXTRACTOR,
                com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
    }

    /** Tier 2 is read from the placed blocks (the copper tower), so it survives saves for free. */
    @Override
    public int getUpgradeLevel(com.solegendary.reignofnether.building.BuildingPlacement placement) {
        for (com.solegendary.reignofnether.building.BuildingBlock block : placement.getBlocks())
            if (block.getBlockState().getBlock() == Blocks.COPPER_BLOCK || block.getBlockState().getBlock() == Blocks.CUT_COPPER)
                return 1;
        return 0;
    }

    @Override
    public String getUpgradedStructureName(int upgradeLevel) {
        return (upgradeLevel > 0 ? upgradedStructureName : structureName) + variant;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/raw_iron_block.png"),
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("buildings.reignofnether.metal_extractor").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip2").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip3").getVisualOrderText()
                ),
                this
        );
    }
}
