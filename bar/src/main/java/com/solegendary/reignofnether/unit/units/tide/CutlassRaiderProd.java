package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought raider: quick melee with an Ambush strike out of the water. Trained at the Slipway. */
public class CutlassRaiderProd extends TideProd {

    public final static String itemName = "Cutlass Raider";
    public final static ResourceCost cost = ResourceCosts.CUTLASS_RAIDER;

    public CutlassRaiderProd() {
        super(itemName, "cutlass_raider_unit", "cutlass_raider", 3, cost, () -> EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), true);
    }
}
