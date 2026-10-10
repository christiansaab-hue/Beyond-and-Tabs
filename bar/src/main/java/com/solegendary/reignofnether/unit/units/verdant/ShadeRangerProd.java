package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Verdant Court T2 sniper: long range, heavy single shots, cloaked while it stands still and holds its fire.
 * Trained at the Circle of Elders once Tier 2 is researched (VerdantT2Prod).
 */
public class ShadeRangerProd extends VerdantT2Prod {

    public final static String itemName = "Shade Ranger";
    public final static ResourceCost cost = ResourceCosts.SHADE_RANGER;

    public ShadeRangerProd() {
        super(itemName, "shade_ranger_unit", "shade_ranger", 2, cost, () -> EntityRegistrar.SHADE_RANGER_UNIT.get(), true);
    }
}
