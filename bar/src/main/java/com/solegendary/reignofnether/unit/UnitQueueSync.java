package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitQueueClientboundPacket;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BAR-style command queue lines. The shift-queue lives only on the server (UnitServerEvents' slow queue), so the
 * client cannot draw a unit's waypoints by itself. Twice a second this builds, per player, the current order plus
 * the queued orders of that player's own units, and sends it to that player only, and only when it changed since
 * the last packet (a hash compare), so an army standing still costs nothing on the wire.
 *
 * An entry is {type, x, y, z, entityId}: type is one of the TYPE_ constants (the client colours by it) and
 * entityId (or -1) lets the client draw to a moving target such as a unit being attacked.
 */
public class UnitQueueSync {

    public static final int TYPE_MOVE = 0;
    public static final int TYPE_ATTACK = 1;
    public static final int TYPE_BUILD = 2;
    public static final int TYPE_RECLAIM = 3;
    public static final int INTS_PER_ENTRY = 5;

    static final int SYNC_TICKS = 10;
    static final int MAX_ENTRIES_PER_UNIT = 16;
    static final int MAX_UNITS_PER_PLAYER = 400;

    private static int ticks = 0;
    // last sent payload hash per player; a missing key means "send the next non-empty state"
    private static final Map<UUID, Long> lastHash = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (++ticks < SYNC_TICKS)
            return;
        ticks = 0;
        List<ServerPlayer> players = evt.getServer().getPlayerList().getPlayers();
        if (players.isEmpty())
            return;

        Map<String, IntArrayList> byOwner = buildPayloads();

        for (ServerPlayer player : players) {
            IntArrayList data = byOwner.get(player.getName().getString());
            long hash = 1;
            if (data != null)
                for (int i = 0; i < data.size(); i++)
                    hash = hash * 31 + data.getInt(i);
            Long last = lastHash.get(player.getUUID());
            if (last != null && last == hash)
                continue;
            if (last == null && data == null)
                continue;   // nothing queued and nothing shown on that client yet
            lastHash.put(player.getUUID(), hash);
            int[] payload = data == null ? new int[0] : data.toIntArray();
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), UnitQueueClientboundPacket.of(payload));
        }
    }

    /**
     * The queue-line payload of every owner with at least one ordered unit: [unitId, n, entries...] blocks. One pass
     * over the slow queue and one over all units, so it stays linear at 8v8. Public for the stress benchmark.
     */
    public static Map<String, IntArrayList> buildPayloads() {
        // one pass over the slow queue: unit id -> its queued items, oldest first
        Int2ObjectOpenHashMap<List<UnitActionItem>> queued = new Int2ObjectOpenHashMap<>();
        List<UnitActionItem> slow = UnitServerEvents.getUnitActionSlowQueue();
        synchronized (slow) {
            for (UnitActionItem uai : slow) {
                if (uai.getUnitIds().length == 0)
                    continue;
                int id = uai.getUnitIds()[0];
                List<UnitActionItem> list = queued.get(id);
                if (list == null) {
                    list = new ArrayList<>();
                    queued.put(id, list);
                }
                list.add(uai);
            }
        }

        // one pass over all units, grouped by owner: [unitId, n, entries...] blocks
        Map<String, IntArrayList> byOwner = new HashMap<>();
        Map<String, int[]> unitCounts = new HashMap<>();
        IntArrayList scratch = new IntArrayList();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit unit) || !le.isAlive())
                continue;
            scratch.clear();
            collect(le, unit, queued.get(le.getId()), scratch);
            if (scratch.isEmpty())
                continue;
            String owner = unit.getOwnerName();
            int[] count = unitCounts.computeIfAbsent(owner, k -> new int[1]);
            if (count[0] >= MAX_UNITS_PER_PLAYER)
                continue;
            count[0]++;
            IntArrayList out = byOwner.computeIfAbsent(owner, k -> new IntArrayList());
            out.add(le.getId());
            out.add(scratch.size() / INTS_PER_ENTRY);
            out.addAll(scratch);
        }

        return byOwner;
    }

    /** The unit's current order (if it has a fixed target) followed by its shift-queued orders. */
    static void collect(LivingEntity le, Unit unit, List<UnitActionItem> queue, IntArrayList out) {
        // current order
        Entity reclaim = le instanceof WorkerUnit ? WreckServerEvents.getReclaimTarget(le) : null;
        BuildingPlacement building = le instanceof WorkerUnit wu && wu.getBuildRepairGoal() != null
            ? wu.getBuildRepairGoal().getBuildingTarget() : null;
        if (reclaim != null) {
            put(out, TYPE_RECLAIM, reclaim.blockPosition(), reclaim.getId());
        } else if (building != null) {
            put(out, TYPE_BUILD, building.centrePos, -1);
        } else if (le instanceof AttackerUnit au && au.getAttackMoveTarget() != null) {
            put(out, TYPE_ATTACK, au.getAttackMoveTarget(), -1);
        } else if (unit.getMoveGoal() != null && unit.getMoveGoal().getMoveTarget() != null) {
            put(out, TYPE_MOVE, unit.getMoveGoal().getMoveTarget(), -1);
        } else if (queue != null && unit.getTargetGoal() != null && unit.getTargetGoal().getTarget() != null) {
            // an attack target counts only ahead of queued orders: idle units auto-acquire targets all the time,
            // and syncing those would churn packets without the player having ordered anything
            // no position: the target moves, and a moving position would change the hash (and send) every sync;
            // the client draws to the entity itself and skips the entry if it can't see it
            put(out, TYPE_ATTACK, BlockPos.ZERO, unit.getTargetGoal().getTarget().getId());
        }
        if (queue == null)
            return;
        for (UnitActionItem uai : queue) {
            if (out.size() >= MAX_ENTRIES_PER_UNIT * INTS_PER_ENTRY)
                break;
            int type = typeOf(uai.getAction());
            if (type < 0)
                continue;
            BlockPos pos = uai.getPreselectedBlockPos();
            int eid = -1;
            if (uai.getAction() == UnitAction.BUILD_REPAIR) {
                BuildingPlacement b = BuildingUtils.findBuilding(false, pos);
                if (b != null)
                    pos = b.centrePos;
            } else if (uai.getAction() == UnitAction.ATTACK || uai.getAction() == UnitAction.FOLLOW
                    || uai.getAction() == UnitAction.RECLAIM) {
                eid = uai.getTargetUnitId();   // position stays the order's (stable hash); client follows the entity
            }
            if (pos == null || (eid < 0 && pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0))
                continue;
            put(out, type, pos, eid);
        }
    }

    static int typeOf(UnitAction action) {
        return switch (action) {
            case MOVE, PATROL, FOLLOW, GARRISON, GUARD -> TYPE_MOVE;
            case ATTACK, ATTACK_MOVE, ATTACK_BUILDING -> TYPE_ATTACK;
            case BUILD_REPAIR, FARM -> TYPE_BUILD;
            case RECLAIM -> TYPE_RECLAIM;
            default -> -1;
        };
    }

    static void put(IntArrayList out, int type, BlockPos pos, int eid) {
        out.add(type);
        out.add(pos.getX());
        out.add(pos.getY());
        out.add(pos.getZ());
        out.add(eid);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent evt) {
        // a sentinel no real hash will match: always send on the next sync, even an empty state, so a client that
        // kept lines from its previous session (stale entity ids) gets them cleared
        lastHash.put(evt.getEntity().getUUID(), Long.MIN_VALUE);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        lastHash.clear();
        ticks = 0;
    }
}
