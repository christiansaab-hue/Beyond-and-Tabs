package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought support: raises tidepools anywhere. Trained at the Slipway. */
public class TidePriestProd extends TideProd {

    public final static String itemName = "Tide Priest";
    public final static ResourceCost cost = ResourceCosts.TIDE_PRIEST;

    public TidePriestProd() {
        super(itemName, "tide_priest_unit", "tide_priest", 3, cost, () -> EntityRegistrar.TIDE_PRIEST_UNIT.get(), true);
    }
}
