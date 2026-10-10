package com.solegendary.reignofnether.unit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** BAR-style T2 constructors: only they can raise a faction's T3 lab (Castle / Fortress / Stronghold). */
public final class T2Workers {
    private T2Workers() { }

    public static boolean isT2Worker(Entity e) {
        return e instanceof com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit
            || e instanceof com.solegendary.reignofnether.unit.units.monsters.EmbalmerUnit
            || e instanceof com.solegendary.reignofnether.unit.units.piglins.BonewrightUnit;
    }

    /** Client side: is a T2 constructor among the selected units (the build menu belongs to the selection)? */
    public static boolean selectedHasT2Worker() {
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (isT2Worker(le))
                return true;
        return false;
    }
}
