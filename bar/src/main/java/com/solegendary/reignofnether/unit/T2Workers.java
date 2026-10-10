package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** BAR-style T2 constructors: only they can raise a faction's T3 lab (Castle / Fortress / Stronghold). */
public final class T2Workers {
    private T2Workers() { }

    public static boolean isT2Worker(Entity e) {
        return e instanceof com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit
            || e instanceof com.solegendary.reignofnether.unit.units.monsters.EmbalmerUnit
            || e instanceof com.solegendary.reignofnether.unit.units.piglins.BonewrightUnit
            || e instanceof com.solegendary.reignofnether.unit.units.verdant.ElderDruidUnit;
    }

    /** The T3 labs, which only a T2 constructor may place. */
    public static boolean isT3Lab(Building b) {
        return b == Buildings.CASTLE || b == Buildings.STRONGHOLD || b == Buildings.FORTRESS;
    }

    /**
     * Server side: may these builders place this building? Anything but a T3 lab always may; a T3 lab needs a T2
     * constructor among the builders (the server-side twin of the build button's selectedHasT2Worker check).
     */
    public static boolean buildersMayPlace(ServerLevel level, Building b, int[] builderUnitIds) {
        if (!isT3Lab(b))
            return true;
        if (level == null || builderUnitIds == null)
            return false;
        for (int id : builderUnitIds)
            if (isT2Worker(level.getEntity(id)))
                return true;
        return false;
    }

    /** Client side: is a T2 constructor among the selected units (the build menu belongs to the selection)? */
    public static boolean selectedHasT2Worker() {
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (isT2Worker(le))
                return true;
        return false;
    }
}
