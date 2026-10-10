package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Server-side index building -> workers whose BuildRepairGoal targets it. BuildingPlacement.getBuilders() runs for
 * every building on every server tick, and used to scan every unit each time: at 8v8 (200+ buildings, 400 units)
 * that was ~80k unit checks and 200 list allocations per tick just to find that most buildings have no builders.
 *
 * The index is exact, not a periodic snapshot: it is rebuilt (one pass over all units) only when some worker's
 * building target changed (BuildRepairGoal's generation counter) or a unit joined or left allUnits, so a builder is
 * seen by the very next getBuilders() call, as before. Whether a candidate is in range right now (isBuilding()) is
 * still checked live on every call, because that changes as workers walk. Server thread only.
 */
public final class BuilderIndex {
    private BuilderIndex() { }

    private static final IdentityHashMap<BuildingPlacement, ArrayList<WorkerUnit>> BY_TARGET = new IdentityHashMap<>();
    private static int builtGen = Integer.MIN_VALUE, builtMod = -1, builtSize = -1;

    private static void ensure() {
        List<LivingEntity> all = UnitServerEvents.getAllUnits();
        int gen = BuildRepairGoal.getTargetGeneration();
        if (gen == builtGen && builtMod == UnitServerEvents.getAllUnitsModCount() && builtSize == all.size())
            return;
        BY_TARGET.clear();
        for (int i = 0, n = all.size(); i < n; i++) {
            if (!(all.get(i) instanceof WorkerUnit wu))
                continue;
            BuildRepairGoal goal = wu.getBuildRepairGoal();
            BuildingPlacement target = goal == null ? null : goal.getBuildingTarget();
            if (target != null)
                BY_TARGET.computeIfAbsent(target, k -> new ArrayList<>(4)).add(wu);
        }
        builtGen = gen;
        builtMod = UnitServerEvents.getAllUnitsModCount();
        builtSize = all.size();
    }

    /** The workers actively building {@code building} right now, in allUnits order (a fresh list the caller owns). */
    public static ArrayList<WorkerUnit> buildersOf(BuildingPlacement building) {
        ensure();
        ArrayList<WorkerUnit> candidates = BY_TARGET.get(building);
        if (candidates == null)
            return new ArrayList<>(0);
        ArrayList<WorkerUnit> out = new ArrayList<>(candidates.size());
        for (WorkerUnit wu : candidates) {
            BuildRepairGoal goal = wu.getBuildRepairGoal();
            // re-check the target too: a goal object swapped without a generation bump must not leave a stale entry
            if (goal != null && goal.getBuildingTarget() == building && goal.isBuilding())
                out.add(wu);
        }
        return out;
    }

    /** Forces a rebuild on the next call (the stress benchmark pays one per pass, like a tick where a unit died). */
    public static void invalidate() {
        builtGen = Integer.MIN_VALUE;
    }
}
