package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Verdant Court T2 cavalry: an antlered elk ridden into the flank, with a leaping charge.
 * Trained at the Circle of Elders once Tier 2 is researched (VerdantT2Prod).
 */
public class StagLancerProd extends VerdantT2Prod {

    public final static String itemName = "Stag Lancer";
    public final static ResourceCost cost = ResourceCosts.STAG_LANCER;

    public StagLancerProd() {
        super(itemName, "stag_lancer_unit", "stag_lancer", 2, cost, () -> EntityRegistrar.STAG_LANCER_UNIT.get(), true);
    }
}
