package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

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
 * BAR's guard order: a unit told to guard something sticks with it and does its job for it.
 * - Fighters guarding anything escort it and attack any enemy that comes near it (then fall back to its side).
 * - Workers guarding a building keep it repaired; guarding a worker, they assist whatever it is building.
 * - Guarding dissolves when the target dies or the guard is given any other order.
 */
public class GuardServerEvents {

    /** What one unit is guarding: another unit (entityId >= 0) or a building (buildingPos != null). */
    record GuardTarget(int entityId, BlockPos buildingPos) { }

    static final Map<Integer, GuardTarget> guards = new ConcurrentHashMap<>();

    private static final double PROTECT_RANGE_SQR = 18 * 18;  // engage enemies this close to the ward
    private static final double ESCORT_GAP_SQR = 7 * 7;       // close back up when further than this

    public static void setGuardUnit(Unit guard, LivingEntity target) {
        LivingEntity le = (LivingEntity) guard;
        if (le.getId() == target.getId())
            return;
        guards.put(le.getId(), new GuardTarget(target.getId(), null));
        guard.setFollowTarget(target);
    }

    public static void setGuardBuilding(Unit guard, BuildingPlacement building) {
        LivingEntity le = (LivingEntity) guard;
        guards.put(le.getId(), new GuardTarget(-1, building.originPos));
        guard.setMoveTarget(building.centrePos);
    }

    public static void clear(int unitId) {
        guards.remove(unitId);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        guards.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || guards.isEmpty())
            return;
        if (evt.getServer().getTickCount() % 10 != 0)
            return;
        ServerLevel level = evt.getServer().overworld();

        Iterator<Map.Entry<Integer, GuardTarget>> it = guards.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, GuardTarget> entry = it.next();
            Entity guardEntity = level.getEntity(entry.getKey());
            if (!(guardEntity instanceof Unit guard) || !(guardEntity instanceof LivingEntity guardLe)
                    || !guardLe.isAlive()) {
                it.remove();
                continue;
            }
            GuardTarget target = entry.getValue();

            // resolve the ward: a living unit, or a standing building
            LivingEntity wardLe = null;
            BuildingPlacement wardBuilding = null;
            if (target.entityId() >= 0) {
                Entity e = level.getEntity(target.entityId());
                if (e instanceof LivingEntity le && le.isAlive())
                    wardLe = le;
            } else if (target.buildingPos() != null) {
                wardBuilding = BuildingUtils.findBuilding(false, target.buildingPos());
                if (wardBuilding != null && wardBuilding.isDestroyedServerside)
                    wardBuilding = null;
            }
            if (wardLe == null && wardBuilding == null) {
                it.remove();   // the ward is gone; stand down where we are
                continue;
            }
            BlockPos anchor = wardLe != null ? wardLe.blockPosition() : wardBuilding.centrePos;

            // fighters: engage anything hostile that threatens the ward
            if (guard instanceof AttackerUnit attacker) {
                LivingEntity current = guard.getTargetGoal() != null ? guard.getTargetGoal().getTarget() : null;
                boolean engaged = current != null && current.isAlive()
                        && current.blockPosition().distSqr(anchor) <= PROTECT_RANGE_SQR * 2;
                if (engaged)
                    continue;   // already fighting close to the ward; let it finish
                LivingEntity threat = nearestThreat(guard, anchor);
                if (threat != null) {
                    attacker.setUnitAttackTarget(threat);
                    continue;
                }
            }

            // workers: keep the ward's building work going
            if (guard instanceof WorkerUnit worker && worker.getBuildRepairGoal() != null) {
                BuildingPlacement job = null;
                if (wardBuilding != null
                        && (!wardBuilding.isBuilt || wardBuilding.getHealth() < wardBuilding.getMaxHealth())) {
                    job = wardBuilding;
                } else if (wardLe instanceof WorkerUnit wardWorker && wardWorker.getBuildRepairGoal() != null) {
                    job = wardWorker.getBuildRepairGoal().getBuildingTarget();
                }
                if (job != null && !job.isDestroyedServerside) {
                    if (worker.getBuildRepairGoal().getBuildingTarget() != job)
                        worker.getBuildRepairGoal().setBuildingTarget(job);
                    continue;
                }
            }

            // escort: close the gap back up when idle and drifting
            if (guard.isIdle() && guardLe.blockPosition().distSqr(anchor) > ESCORT_GAP_SQR) {
                if (wardLe != null)
                    guard.setFollowTarget(wardLe);
                else
                    guard.setMoveTarget(anchor);
            }
        }
    }

    /** The closest hostile unit within protecting range of the anchor. */
    private static LivingEntity nearestThreat(Unit guard, BlockPos anchor) {
        LivingEntity best = null;
        double bestD = PROTECT_RANGE_SQR;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!le.isAlive() || !(le instanceof Unit))
                continue;
            if (UnitServerEvents.getUnitToEntityRelationship(guard, le) != Relationship.HOSTILE)
                continue;
            double d = le.blockPosition().distSqr(anchor);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }
}
