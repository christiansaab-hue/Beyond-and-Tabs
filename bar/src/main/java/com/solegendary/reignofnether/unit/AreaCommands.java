package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * Combat units (attackers that are not workers) in the same selection get BAR's area attack instead: every visible
 * enemy unit and building inside the circle is dealt out the same way, so a mixed selection reclaims / repairs with
 * its workers while its fighters clear the field. An empty circle sends the fighters on an attack-move to the centre.
 */
public class AreaCommands {

    public static final int MODE_AUTO = 0;     // reclaim if the circle holds any wreck, else repair
    public static final int MODE_RECLAIM = 1;
    public static final int MODE_REPAIR = 2;
    // the two worker-only modes above leave fighters alone; AUTO and ATTACK send the fighters in
    public static final int MODE_ATTACK = 3;

    public static final int MAX_RADIUS = 40;
    public static final int MAX_TARGETS = 64;   // a huge circle over a battlefield still makes a sane queue
    static final int MAX_DY = 12;              // ignore heaps on a cliff far above/below the circle
    // a fighter that finishes its own share moves on to the rest of the circle, but its queue stops here: the slow
    // queue is scanned every tick, so hundreds of fighters x 64 targets each would be a needless per-tick cost
    static final int MAX_FIGHTER_QUEUE = 12;

    record Target(double x, double y, double z, int entityId, BlockPos orderPos) {}

    /**
     * Queues the area command; returns how many targets were handed out (0 if the circle held nothing).
     * Server side; also called by the game test.
     */
    public static int issue(ServerLevel level, String owner, int mode, BlockPos centre, int radius,
                            int[] unitIds, boolean shift) {
        radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        List<LivingEntity> workers = new ArrayList<>();
        List<LivingEntity> fighters = new ArrayList<>();
        for (int id : unitIds) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Unit u) || !e.isAlive() ||
                    !(u.getOwnerName().equals(owner) || AlliancesServerEvents.canControlAlly(owner, u.getOwnerName())))
                continue;
            if (e instanceof WorkerUnit)
                workers.add((LivingEntity) e);
            else if (e instanceof AttackerUnit)
                fighters.add((LivingEntity) e);
        }
        int handed = 0;
        if (!workers.isEmpty() && mode != MODE_ATTACK)
            handed += issueWorkers(level, owner, mode, centre, radius, workers, shift);
        if (!fighters.isEmpty() && mode != MODE_RECLAIM && mode != MODE_REPAIR)
            handed += issueAttack(level, owner, centre, radius, fighters, shift);
        return handed;
    }

    /** Area reclaim / repair for the workers of the selection. */
    static int issueWorkers(ServerLevel level, String owner, int mode, BlockPos centre, int radius,
                            List<LivingEntity> workers, boolean shift) {

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

    /**
     * Area attack for the fighters of the selection: each takes its round-robin share of the enemies in the circle
     * (nearest-first), then continues nearest-first over the rest so nobody idles while the circle still holds
     * enemies. Returns how many attack orders were handed out; 0 means the circle held no visible enemy and the
     * fighters were sent on an attack-move to its centre instead.
     */
    static int issueAttack(ServerLevel level, String owner, BlockPos centre, int radius,
                           List<LivingEntity> fighters, boolean shift) {
        List<Target> targets = enemiesIn(level, owner, centre, radius);
        if (targets.isEmpty()) {
            int[] ids = new int[fighters.size()];
            for (int i = 0; i < ids.length; i++)
                ids[i] = fighters.get(i).getId();
            if (shift) {
                UnitServerEvents.addActionItem(owner, UnitAction.ATTACK_MOVE, -1, ids, centre, centre, true);
            } else {
                for (int id : ids)
                    UnitServerEvents.clearQueuedActions(id);
                new UnitActionItem(owner, UnitAction.ATTACK_MOVE, -1, ids, centre, centre).action(level);
            }
            return 0;
        }
        List<List<Integer>> plan = attackPlan(fighters, targets);
        int handed = 0;
        for (int f = 0; f < fighters.size(); f++) {
            List<Integer> mine = plan.get(f);
            int fighterId = fighters.get(f).getId();
            int[] ids = new int[] { fighterId };
            for (int i = 0; i < mine.size(); i++) {
                Target t = targets.get(mine.get(i));
                // units are attacked by entity id, buildings by a block position inside them
                UnitAction action = t.entityId() >= 0 ? UnitAction.ATTACK : UnitAction.ATTACK_BUILDING;
                if (i == 0 && !shift) {
                    // same as the workers: start the first order now so the slow queue can't pop #2 first
                    UnitServerEvents.clearQueuedActions(fighterId);
                    new UnitActionItem(owner, action, t.entityId(), ids, t.orderPos(), t.orderPos()).action(level);
                } else {
                    UnitServerEvents.addActionItem(owner, action, t.entityId(), ids, t.orderPos(), t.orderPos(), true);
                }
                handed++;
            }
        }
        return handed;
    }

    /** Fog of war applies: only what the commanding player (with their team's vision) can see is a target. */
    static boolean visibleTo(ServerPlayer player, double x, double z) {
        return player == null || FogOfWarServerEvents.isBlockVisibleFor(player, (int) Math.floor(x), (int) Math.floor(z));
    }

    static boolean isEnemyOwner(String owner, String other) {
        return other != null && !other.isBlank() && !owner.equals(other) && !AlliancesServerEvents.isAllied(owner, other);
    }

    static List<Target> enemiesIn(ServerLevel level, String owner, BlockPos centre, int radius) {
        // a bot or the game test has no player of that name: no fog filter then
        ServerPlayer player = level.getServer() == null ? null : level.getServer().getPlayerList().getPlayerByName(owner);
        List<Target> out = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le.isRemoved() || !le.isAlive() || le.level() != level || !(le instanceof Unit u)
                    || !isEnemyOwner(owner, u.getOwnerName())
                    || !inCircle(centre, radius, le.getX(), le.getY(), le.getZ())
                    || !visibleTo(player, le.getX(), le.getZ()))
                continue;
            out.add(new Target(le.getX(), le.getY(), le.getZ(), le.getId(), le.blockPosition()));
            if (out.size() >= MAX_TARGETS)
                return out;
        }
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.isDestroyedServerside || !isEnemyOwner(owner, b.ownerName))
                continue;
            BlockPos c = b.centrePos;
            if (!inCircle(centre, radius, c.getX() + 0.5, c.getY(), c.getZ() + 0.5)
                    || !visibleTo(player, c.getX() + 0.5, c.getZ() + 0.5))
                continue;
            // ATTACK_BUILDING resolves the building from its origin (BuildingUtils.findBuilding)
            out.add(new Target(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, -1, b.originPos));
            if (out.size() >= MAX_TARGETS)
                break;
        }
        return out;
    }

    /**
     * Per fighter: its round-robin share from {@link #assign}, then a nearest-neighbour walk over the targets it
     * doesn't hold yet, up to {@link #MAX_FIGHTER_QUEUE} orders in total (its own share is never cut, or a target
     * would be dropped). With more fighters than enemies, the spare fighters just walk the enemies nearest-first.
     */
    static List<List<Integer>> attackPlan(List<LivingEntity> fighters, List<Target> targets) {
        List<List<Integer>> plan = assign(fighters, targets);
        boolean[] mine = new boolean[targets.size()];
        for (int f = 0; f < fighters.size(); f++) {
            List<Integer> list = plan.get(f);
            if (list.size() >= MAX_FIGHTER_QUEUE || list.size() >= targets.size())
                continue;
            Arrays.fill(mine, false);
            for (int t : list)
                mine[t] = true;
            double x, y, z;
            if (list.isEmpty()) {
                LivingEntity le = fighters.get(f);
                x = le.getX();
                y = le.getY();
                z = le.getZ();
            } else {
                Target last = targets.get(list.get(list.size() - 1));
                x = last.x();
                y = last.y();
                z = last.z();
            }
            while (list.size() < MAX_FIGHTER_QUEUE && list.size() < targets.size()) {
                int best = -1;
                double bestD = Double.MAX_VALUE;
                for (int t = 0; t < targets.size(); t++) {
                    if (mine[t])
                        continue;
                    Target tg = targets.get(t);
                    double dx = tg.x() - x, dy = tg.y() - y, dz = tg.z() - z;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = t;
                    }
                }
                mine[best] = true;
                list.add(best);
                Target tg = targets.get(best);
                x = tg.x();
                y = tg.y();
                z = tg.z();
            }
        }
        return plan;
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
