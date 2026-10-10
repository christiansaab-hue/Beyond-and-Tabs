package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought constructor (the first one is the Admiral). Trained at the Wreck Harbour. */
public class ShipwrightProd extends TideProd {

    public final static String itemName = "Shipwright";
    public final static ResourceCost cost = ResourceCosts.SHIPWRIGHT;

    public ShipwrightProd() {
        super(itemName, "shipwright_unit", "shipwright", 3, cost, () -> EntityRegistrar.SHIPWRIGHT_UNIT.get(), true);
    }
}
