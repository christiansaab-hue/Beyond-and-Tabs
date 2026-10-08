package com.solegendary.reignofnether.building.buildings.shared;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.StockpilePlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

public abstract class AbstractStockpile extends ProductionBuilding {

    public final static ResourceCost cost = ResourceCosts.STOCKPILE;
    public AbstractStockpile(String structureName) {
        super(structureName, cost, false);
        // BAR economy: stockpiles are storage buildings (+500 metal/energy storage) with a trickle of metal
        this.metalIncome = 0.5f;
        this.metalStorage = 500f;
        this.energyStorage = 500f;
        this.portraitBlock = Blocks.CHEST;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/chest.png");

        this.canAcceptResources = true;
        this.canSetRallyPoint = false;

        this.productions.add(ProductionItems.RESEARCH_RESOURCE_CAPACITY, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.RESEARCH_ITEM_BACKPACKS, Keybindings.abilitySlot2);

        this.maxHealth = 95d;
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new StockpilePlacement(this, level, pos, rotation, ownerName, getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), isCapitol);
    }
}
