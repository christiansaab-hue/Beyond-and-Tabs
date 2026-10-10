package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * T2 constructor of the Verdant Court: builds twice as fast with twice a Seedshaper's health, and is the only Court
 * builder that can raise a Tier 3 lab. Its power, Awaken Thicket, wakes a temporary treant.
 * Trained at the Circle of Elders once Tier 2 is researched (VerdantT2Prod).
 */
public class ElderDruidProd extends VerdantT2Prod {

    public final static String itemName = "Elder Druid";
    public final static ResourceCost cost = ResourceCosts.ELDER_DRUID;

    public ElderDruidProd() {
        super(itemName, "elder_druid_unit", "elder_druid", 3, cost, () -> EntityRegistrar.ELDER_DRUID_UNIT.get(), false);
    }
}
