package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Tidewrought artillery: arcing mortar shells with splash, useless up close. Trained at the Slipway. */
public class BombardCrewProd extends TideProd {

    public final static String itemName = "Bombard Crew";
    public final static ResourceCost cost = ResourceCosts.BOMBARD_CREW;

    public BombardCrewProd() {
        super(itemName, "bombard_crew_unit", "bombard_crew", 3, cost, () -> EntityRegistrar.BOMBARD_CREW_UNIT.get(), true);
    }
}
