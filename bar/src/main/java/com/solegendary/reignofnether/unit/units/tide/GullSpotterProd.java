package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought scout: a fast, unarmed gull with long sight. Trained at the Wreck Harbour. */
public class GullSpotterProd extends TideProd {

    public final static String itemName = "Gull Spotter";
    public final static ResourceCost cost = ResourceCosts.GULL_SPOTTER;

    public GullSpotterProd() {
        super(itemName, "gull_spotter_unit", "gull_spotter", 2, cost, () -> EntityRegistrar.GULL_SPOTTER_UNIT.get(), true);
    }
}
