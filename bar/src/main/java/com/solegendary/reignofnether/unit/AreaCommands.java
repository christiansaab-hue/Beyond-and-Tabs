package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's area commands for workers: drag a circle and every wreck (area reclaim) or every damaged / unfinished own
 * building (area repair) inside it is queued on the selected workers. Targets are dealt out round-robin, each worker
 * taking the target nearest to where its previous one left it, so a group fans out over the field and each walks a
 * short nearest-first circuit instead of all of them trailing through the same heaps.
 *
 * The first target of each worker is issued at once (replacing what it was doing, unless the command was
 * shift-queued) and the rest go into the normal shift-queue, so the queue lines show the whole circuit and a later
 * plain order cancels it like any other queue.
 */
public class AreaCommands {

    public static final int MODE_AUTO = 0;     // reclaim if the circle holds any wreck, else repair
    public static final int MODE_RECLAIM = 1;
    public static final int MODE_REPAIR = 2;

    public static final int MAX_RADIUS = 40;
    public static final int MAX_TARGETS = 64;   // a huge circle over a battlefield still makes a sane queue
    static final int MAX_DY = 12;              // ignore heaps on a cliff far above/below the circle

    record Target(double x, double y, double z, int entityId, BlockPos orderPos) {}

    /**
     * Queues the area command; returns how many targets were handed out (0 if the circle held nothing).
     * Server side; also called by the game test.
     */
    public static int issue(ServerLevel level, String owner, int mode, BlockPos centre, int radius,
                            int[] unitIds, boolean shift) {
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        List<LivingEntity> workers = new ArrayList<>();
        for (int id : unitIds) {
            Entity e = level.getEntity(id);
            if (e instanceof WorkerUnit && e instanceof Unit u && e.isAlive() &&
                    (u.getOwnerName().equals(owner) || AlliancesServerEvents.canControlAlly(owner, u.getOwnerName())))
                workers.add((LivingEntity) e);
        }
        if (workers.isEmpty())
            return 0;

        UnitAction action = UnitAction.RECLAIM;
        List<Target> targets = mode == MODE_REPAIR ? List.of() : wrecksIn(level, centre, radius);
        if (targets.isEmpty() && mode != MODE_RECLAIM) {
            action = UnitAction.BUILD_REPAIR;
            targets = repairsIn(owner, centre, radius);
        }
        if (targets.isEmpty())
            return 0;

        List<List<Integer>> plan = assign(workers, targets);
        int handed = 0;
        for (int w = 0; w < workers.size(); w++) {
            List<Integer> mine = plan.get(w);
            if (mine.isEmpty())
                continue;
            int workerId = workers.get(w).getId();
            int[] ids = new int[] { workerId };
            for (int i = 0; i < mine.size(); i++) {
                Target t = targets.get(mine.get(i));
                if (i == 0 && !shift) {
                    // run the first order right now instead of via the fast queue: the slow queue is ticked
                    // before the fast one, so an idle worker would otherwise pop target #2 first
                    UnitServerEvents.clearQueuedActions(workerId);
                    new UnitActionItem(owner, action, t.entityId(), ids, t.orderPos(), t.orderPos()).action(level);
                } else {
                    UnitServerEvents.addActionItem(owner, action, t.entityId(), ids, t.orderPos(), t.orderPos(), true);
                }
                handed++;
            }
        }
        return handed;
    }

    static boolean inCircle(BlockPos centre, int radius, double x, double y, double z) {
        double dx = x - (centre.getX() + 0.5), dz = z - (centre.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) radius * radius && Math.abs(y - centre.getY()) <= MAX_DY;
    }

    static List<Target> wrecksIn(ServerLevel level, BlockPos centre, int radius) {
        List<Target> out = new ArrayList<>();
        for (Entity w : WreckServerEvents.getWrecks()) {
            if (w.isRemoved() || w.level() != level || !inCircle(centre, radius, w.getX(), w.getY(), w.getZ()))
                continue;
            out.add(new Target(w.getX(), w.getY(), w.getZ(), w.getId(), w.blockPosition()));
            if (out.size() >= MAX_TARGETS)
                break;
        }
        return out;
    }

    static List<Target> repairsIn(String owner, BlockPos centre, int radius) {
        List<Target> out = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.isDestroyedServerside || !owner.equals(b.ownerName))
                continue;
            BlockPos c = b.centrePos;
            if (!inCircle(centre, radius, c.getX() + 0.5, c.getY(), c.getZ() + 0.5)
                    || !BuildingUtils.isBuildingBuildable(false, b))
                continue;
            // BUILD_REPAIR finds its building by origin (BuildingUtils.findBuilding)
            out.add(new Target(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, -1, b.originPos));
            if (out.size() >= MAX_TARGETS)
                break;
        }
        return out;
    }

    /**
     * Round-robin nearest-neighbour split: worker after worker takes the free target nearest to its chain end (its
     * own position, then its last pick). Returns, per worker, indexes into {@code targets} in visiting order.
     */
    static List<List<Integer>> assign(List<LivingEntity> workers, List<Target> targets) {
        int nw = workers.size();
        List<List<Integer>> plan = new ArrayList<>(nw);
        double[] ex = new double[nw], ey = new double[nw], ez = new double[nw];
        for (int w = 0; w < nw; w++) {
            plan.add(new ArrayList<>());
            ex[w] = workers.get(w).getX();
            ey[w] = workers.get(w).getY();
            ez[w] = workers.get(w).getZ();
        }
        boolean[] taken = new boolean[targets.size()];
        int left = targets.size();
        int w = 0;
        while (left > 0) {
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int t = 0; t < targets.size(); t++) {
                if (taken[t])
                    continue;
                Target tg = targets.get(t);
                double dx = tg.x() - ex[w], dy = tg.y() - ey[w], dz = tg.z() - ez[w];
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = t;
                }
            }
            taken[best] = true;
            left--;
            plan.get(w).add(best);
            Target tg = targets.get(best);
            ex[w] = tg.x();
            ey[w] = tg.y();
            ez[w] = tg.z();
            w = (w + 1) % nw;
        }
        return plan;
    }
}
