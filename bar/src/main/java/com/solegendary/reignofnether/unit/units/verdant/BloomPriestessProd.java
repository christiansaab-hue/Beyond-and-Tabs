package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Verdant Court T2 support: mass heal and cleanse pulses, and the Bloom burst (BloomPriestessUnit). Does not fight.
 * Trained at the Circle of Elders once Tier 2 is researched (VerdantT2Prod).
 */
public class BloomPriestessProd extends VerdantT2Prod {

    public final static String itemName = "Bloom Priestess";
    public final static ResourceCost cost = ResourceCosts.BLOOM_PRIESTESS;

    public BloomPriestessProd() {
        super(itemName, "bloom_priestess_unit", "bloom_priestess", 3, cost, () -> EntityRegistrar.BLOOM_PRIESTESS_UNIT.get(), true);
    }
}
