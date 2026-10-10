package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.verdant.HeartOfTheWild;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import javax.annotation.Nullable;

/**
 * Verdant Court T3 experimental: the World Tree Walker, grown at the Heart of the Wild. Reuses the Court production
 * item body (VerdantT2Prod's buttons) but gates on Tier 3 instead of Tier 2 (Tier 3 implies Tier 2). It walks out of
 * the lab rather than spawning inside - it would not fit through the hollow trunk.
 */
public class WorldTreeWalkerProd extends VerdantT2Prod {

    public final static String itemName = "World Tree Walker";
    public final static ResourceCost cost = ResourceCosts.WORLD_TREE_WALKER;

    public WorldTreeWalkerProd() {
        super(itemName, "world_tree_walker_unit", "world_tree_walker", 3, cost, () -> EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), false);
    }

    /** No Tier 3, no experimental: checked on the server when production starts, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return HeartOfTheWild.tier3Error(pp);
    }
}
