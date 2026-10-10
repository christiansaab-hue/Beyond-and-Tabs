package com.solegendary.reignofnether.unit.pathfinding;

import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.tide.TidesServerEvents;
import com.solegendary.reignofnether.items.UnitItems;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.StriderUnit;
import com.solegendary.reignofnether.unit.units.villagers.ScoutCatUnit;
import com.solegendary.reignofnether.unit.units.villagers.ScoutDogUnit;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Drowned;

public enum MobilityClass {
    // TIDAL (Tidewrought, design/tidewrought_plan.md 3.5): land 1.0, tidepool 1.0, real water 1.5 - amphibious walkers.
    // Footprint is independent of the class (ChunkSnapshot.footprintRadius), so wide tidal units reuse it.
    HUMANOID, AQUATIC, LARGE, FIRE_IMMUNE, TIDAL;

    public static MobilityClass of(Unit unit) {
        // first: a tidal unit stays tidal whatever its body is (a Drowned or WaterAnimal body would read AQUATIC)
        if (TidesServerEvents.isTidal((net.minecraft.world.entity.Entity) unit)) return TIDAL;
        boolean hasMagmaBoots = unit instanceof UnitInventory inv && inv.isHolding(UnitItems.MAGMA_WALKER_BOOTS);
        if (unit instanceof StriderUnit || hasMagmaBoots) return FIRE_IMMUNE;
        if (!(unit instanceof Mob mob)) return HUMANOID;
        boolean hasFrostBoots = unit instanceof UnitInventory inv && inv.isHolding(UnitItems.FROST_WALKER_BOOTS);
        if (mob instanceof Drowned || mob instanceof ScoutDogUnit || mob instanceof ScoutCatUnit || mob instanceof WaterAnimal || hasFrostBoots) return AQUATIC;
        // Wider than a full block spans multiple cells and needs a multi-cell footprint; narrower fits one cell.
        if (mob.getBbWidth() > 1.0f) return LARGE;
        return HUMANOID;
    }

    public float costFor(MobilityClass mob, byte kind) {
        if (kind == WalkabilityBuilder.KIND_LAVA && mob == FIRE_IMMUNE)
            return 1.0f;
        switch (kind) {
            case WalkabilityBuilder.KIND_LAND: return 1.0f;
            case WalkabilityBuilder.KIND_WATER:
                if (this == AQUATIC) return 3.0f;
                if (this == TIDAL) return 1.5f;
                return 25.0f;
            // a mild wading preference to go round a pool, never a wall
            case WalkabilityBuilder.KIND_TIDEPOOL: return this == TIDAL ? 1.0f : 1.5f;
            case WalkabilityBuilder.KIND_FIRE: return mob == FIRE_IMMUNE ? 1.0f : PathfinderConfig.FIRE_AVOID_COST;
            case WalkabilityBuilder.KIND_SLIME: return PathfinderConfig.SLIME_AVOID_COST;
            default: return Float.POSITIVE_INFINITY;
        }
    }

    // Land/water cost is the same for every unit of a class, but fire cost is per-unit (depends on fire immunity
    // + the unit's DAMAGE_FIRE malus), so A* injects it per request.
    public float costFor(MobilityClass mob, byte kind, float fireCost) {
        if (kind == WalkabilityBuilder.KIND_LAVA && mob == FIRE_IMMUNE)
            return 1.0f;
        if (kind == WalkabilityBuilder.KIND_FIRE) {
            if (mob == FIRE_IMMUNE)
                return 1.0f;
            return fireCost;
        }
        return costFor(mob, kind);
    }
}
