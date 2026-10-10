package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Verdant Court T2 super-tank: a slow ancient oak that hurls a boulder into the enemy every few seconds.
 * Trained at the Circle of Elders once Tier 2 is researched (VerdantT2Prod).
 */
public class ElderTreantProd extends VerdantT2Prod {

    public final static String itemName = "Elder Treant";
    public final static ResourceCost cost = ResourceCosts.ELDER_TREANT;

    public ElderTreantProd() {
        super(itemName, "elder_treant_unit", "elder_treant", 2, cost, () -> EntityRegistrar.ELDER_TREANT_UNIT.get(), true);
    }
}
