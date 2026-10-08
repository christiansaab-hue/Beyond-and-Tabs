package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BAR's patrol: a unit given a patrol order walks between where it stood and the ordered point until told otherwise.
 * Fighters sweep the route with attack-move (engaging everything on the way, then resuming); workers on patrol act
 * as a repair crew - any unfinished or damaged friendly building near the route pulls them in, then they carry on.
 * Any other order cancels the patrol for that unit.
 */
public class PatrolServerEvents {

    /** unitId -> {endA, endB, nextIndex (0/1)}. */
    record Route(BlockPos a, BlockPos b, int next) { }
    static final Map<Integer, Route> routes = new ConcurrentHashMap<>();

    public static void setPatrol(Unit unit, BlockPos target) {
        LivingEntity le = (LivingEntity) unit;
        routes.put(le.getId(), new Route(le.blockPosition(), target, 1));
        sendLeg(unit, target);
    }

    public static void clear(int unitId) {
        routes.remove(unitId);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        routes.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || routes.isEmpty())
            return;
        if (evt.getServer().getTickCount() % 10 != 0)
            return;
        ServerLevel level = evt.getServer().overworld();
        Iterator<Map.Entry<Integer, Route>> it = routes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Route> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Unit unit) || !(entity instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }
            if (!unit.isIdle())
                continue;
            Route r = entry.getValue();
            // workers: pull in to any friendly site or damaged building near the route before walking on
            if (unit instanceof WorkerUnit workerUnit) {
                BuildingPlacement job = nearestJob(unit, le);
                if (job != null) {
                    workerUnit.getBuildRepairGoal().setBuildingTarget(job);
                    continue;
                }
            }
            BlockPos target = r.next() == 0 ? r.a() : r.b();
            entry.setValue(new Route(r.a(), r.b(), 1 - r.next()));
            sendLeg(unit, target);
        }
    }

    /** One leg of the patrol: attack-move for fighters, a plain move for everyone else. */
    static void sendLeg(Unit unit, BlockPos target) {
        if (unit instanceof AttackerUnit attackerUnit) {
            MiscUtil.addUnitCheckpoint(unit, target, false);
            attackerUnit.setAttackMoveTarget(target);
        } else {
            unit.setMoveTarget(target);
        }
    }

    static BuildingPlacement nearestJob(Unit unit, LivingEntity le) {
        BuildingPlacement best = null;
        double bestD = 28 * 28;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || !bp.ownerName.equals(unit.getOwnerName()))
                continue;
            boolean needsWork = !bp.isBuilt || bp.getHealth() < bp.getMaxHealth();
            if (!needsWork)
                continue;
            double d = le.blockPosition().distSqr(bp.originPos);
            if (d < bestD) {
                bestD = d;
                best = bp;
            }
        }
        return best;
    }
}
