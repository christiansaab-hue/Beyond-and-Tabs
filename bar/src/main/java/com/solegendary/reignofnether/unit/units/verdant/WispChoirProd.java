package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Verdant Court T2 anti-air: three wisps that shred flyers and barely scratch the ground (WispChoirUnit).
 * Trained at the Circle of Elders once Tier 2 is researched (VerdantT2Prod).
 */
public class WispChoirProd extends VerdantT2Prod {

    public final static String itemName = "Wisp Choir";
    public final static ResourceCost cost = ResourceCosts.WISP_CHOIR;

    public WispChoirProd() {
        super(itemName, "wisp_choir_unit", "wisp_choir", 2, cost, () -> EntityRegistrar.WISP_CHOIR_UNIT.get(), true);
    }
}
