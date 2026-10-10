package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought line infantry: a shield-bearer that heals fast in the water. Trained at the Slipway. */
public class ReefGuardProd extends TideProd {

    public final static String itemName = "Reef Guard";
    public final static ResourceCost cost = ResourceCosts.REEF_GUARD;

    public ReefGuardProd() {
        super(itemName, "reef_guard_unit", "reef_guard", 3, cost, () -> EntityRegistrar.REEF_GUARD_UNIT.get(), true);
    }
}
