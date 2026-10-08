package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket.Entry;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket.Field;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Server side of UnitBatchSyncClientboundPacket: the periodic (1/s) unit sync, batched and delta-compressed.
 *
 * Old behaviour (UnitServerEvents.onWorldTick, every 20 ticks, for EVERY unit): one SYNC_STATS packet per player
 * that could see it, plus one SYNC_RESOURCES, one anchor packet, one worker packet and one inventory packet sent
 * to ALL players - up to (players + 4) packets per unit per second, each also costing the client a linear scan of
 * its unit list. With 2 players x 200 units that was ~2400 packets/s, ~O(n^2) client work.
 *
 * Now: one packet (split every MAX_ENTRIES_PER_PACKET units) per player per sync, containing only the fields that
 * changed since that player last received them. Correctness guards for the delta:
 *  - per-player snapshots, so fog-gated stats are re-sent when a unit becomes visible again, and a newly joined
 *    player (empty snapshot) gets everything;
 *  - a player starting/stopping to track a unit (client entity (un)loaded, which resets client-only state) drops
 *    that unit's snapshot for that player (UnitServerEvents.onStartTracking/onStopTracking);
 *  - any out-of-band sync packet for a field (eg. the immediate resource sync on pickup) invalidates that field,
 *    so the next batch re-sends the authoritative value;
 *  - every unit is force-refreshed in full every FULL_REFRESH_SYNCS syncs (staggered by unit id), as a safety net.
 * Server-thread only.
 */
public final class UnitSyncBatcher {
    private UnitSyncBatcher() {}

    public static final int FULL_REFRESH_SYNCS = 10;
    private static final int MAX_ENTRIES_PER_PACKET = 128;
    // position deltas smaller than this (squared blocks) aren't worth re-sending; the client only uses the synced
    // position for units it hasn't loaded (minimap / virtual units)
    private static final double POS_EPSILON_SQR = 0.05 * 0.05;

    // what a given player last received for a given unit; validMask = which fields are known on the client
    private static final class Snap {
        int validMask = 0;
        final Entry last;
        Snap(int id) { this.last = new Entry(id); }
    }

    private static final Map<UUID, Int2ObjectOpenHashMap<Snap>> SNAPSHOTS = new HashMap<>();
    private static int syncCounter = 0;

    // ---- invalidation hooks ----

    public static void invalidate(int unitId, Field field) {
        for (Int2ObjectOpenHashMap<Snap> m : SNAPSHOTS.values()) {
            Snap s = m.get(unitId);
            if (s != null) s.validMask &= ~field.bit;
        }
    }

    public static void invalidateForPlayer(UUID player, int unitId) {
        Int2ObjectOpenHashMap<Snap> m = SNAPSHOTS.get(player);
        if (m != null) m.remove(unitId);
    }

    public static void onUnitRemoved(int unitId) {
        for (Int2ObjectOpenHashMap<Snap> m : SNAPSHOTS.values())
            m.remove(unitId);
    }

    public static void onPlayerLeft(UUID player) {
        SNAPSHOTS.remove(player);
    }

    public static void clear() {
        SNAPSHOTS.clear();
        syncCounter = 0;
    }

    // ---- the sync ----

    public static void sync(List<ServerPlayer> players, List<LivingEntity> units) {
        syncCounter++;
        if (players.isEmpty() || units.isEmpty())
            return;

        // 1. current state of every unit, computed once (not once per player)
        ArrayList<Entry> current = new ArrayList<>(units.size());
        for (LivingEntity le : units)
            if (le instanceof Unit unit)
                current.add(capture(le, unit));

        // drop snapshots of players who are gone
        if (SNAPSHOTS.size() > players.size()) {
            Set<UUID> online = new HashSet<>();
            for (ServerPlayer p : players) online.add(p.getUUID());
            SNAPSHOTS.keySet().removeIf(uuid -> !online.contains(uuid));
        }

        // 2. per player: diff against what they have, send in as few packets as possible
        for (ServerPlayer player : players) {
            Int2ObjectOpenHashMap<Snap> snaps = SNAPSHOTS.computeIfAbsent(player.getUUID(), k -> new Int2ObjectOpenHashMap<>());
            ArrayList<Entry> out = new ArrayList<>();
            for (Entry cur : current) {
                Snap snap = snaps.get(cur.id);
                if (snap == null) {
                    snap = new Snap(cur.id);
                    snaps.put(cur.id, snap);
                }
                if (Math.floorMod(cur.id, FULL_REFRESH_SYNCS) == Math.floorMod(syncCounter, FULL_REFRESH_SYNCS))
                    snap.validMask = 0;

                Entry e = new Entry(cur.id);
                // STATS: only while the unit is visible to this player (same fog gate as the old SYNC_STATS)
                if (cur.has(Field.STATS) &&
                        FogOfWarServerEvents.isBlockVisibleFor(player, (int) Math.floor(cur.x), (int) Math.floor(cur.z)) &&
                        (!Field.STATS.in(snap.validMask) || statsChanged(snap.last, cur))) {
                    copyStats(cur, e);
                    copyStats(cur, snap.last);
                    snap.validMask |= Field.STATS.bit;
                }
                if (cur.has(Field.RESOURCES) &&
                        (!Field.RESOURCES.in(snap.validMask) || resourcesChanged(snap.last, cur))) {
                    copyResources(cur, e);
                    copyResources(cur, snap.last);
                    snap.validMask |= Field.RESOURCES.bit;
                }
                if (cur.has(Field.ANCHOR) &&
                        (!Field.ANCHOR.in(snap.validMask) || !Objects.equals(snap.last.anchor, cur.anchor))) {
                    e.mask |= Field.ANCHOR.bit;
                    e.anchor = cur.anchor;
                    snap.last.anchor = cur.anchor;
                    snap.validMask |= Field.ANCHOR.bit;
                }
                if (cur.has(Field.VETERAN) && !Field.VETERAN.in(snap.validMask)) {
                    e.mask |= Field.VETERAN.bit;
                    snap.validMask |= Field.VETERAN.bit;
                }
                if (cur.has(Field.WORKER) &&
                        (!Field.WORKER.in(snap.validMask) || workerChanged(snap.last, cur))) {
                    copyWorker(cur, e);
                    copyWorker(cur, snap.last);
                    snap.validMask |= Field.WORKER.bit;
                }
                if (cur.has(Field.INVENTORY) &&
                        (!Field.INVENTORY.in(snap.validMask) || !itemsEqual(snap.last.items, cur.items))) {
                    e.mask |= Field.INVENTORY.bit;
                    e.items = cur.items; // immutable copy made in capture(), safe to share
                    snap.last.items = cur.items;
                    snap.validMask |= Field.INVENTORY.bit;
                }
                if (e.mask != 0) {
                    out.add(e);
                    if (out.size() >= MAX_ENTRIES_PER_PACKET) {
                        send(player, out);
                        out = new ArrayList<>();
                    }
                }
            }
            if (!out.isEmpty())
                send(player, out);
        }
    }

    private static void send(ServerPlayer player, List<Entry> entries) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new UnitBatchSyncClientboundPacket(entries));
    }

    // Mirrors exactly what the old per-unit packets read from the unit.
    private static Entry capture(LivingEntity le, Unit unit) {
        Entry e = new Entry(le.getId());
        // SYNC_STATS
        e.mask |= Field.STATS.bit;
        e.health = le.getHealth();
        e.absorb = le.getAbsorptionAmount();
        e.x = le.getX();
        e.y = le.getY();
        e.z = le.getZ();
        e.population = unit.getCost().population;
        e.ownerName = unit.getOwnerName();
        // SYNC_RESOURCES
        Resources res = Resources.getTotalResourcesFromItems(unit.getItems());
        e.mask |= Field.RESOURCES.bit;
        e.food = res.food;
        e.wood = res.wood;
        e.ore = res.ore;
        e.emerald = res.emerald;
        // SYNC_ANCHOR_POS / REMOVE_ANCHOR_POS
        e.mask |= Field.ANCHOR.bit;
        e.anchor = unit.getAnchor();
        // MAKE_VILLAGER_VETERAN
        if (le instanceof VillagerUnit vUnit && vUnit.isVeteran())
            e.mask |= Field.VETERAN.bit;
        // UnitSyncWorkerClientBoundPacket
        if (le instanceof WorkerUnit wUnit) {
            e.mask |= Field.WORKER.bit;
            e.isBuilding = wUnit.getBuildRepairGoal().isBuilding();
            e.isGathering = wUnit.getGatherResourceGoal().isGathering();
            ResourceName rn = wUnit.getGatherResourceGoal().getTargetResourceName();
            e.gatherName = rn == null ? ResourceName.NONE : rn;
            e.gatherPos = wUnit.getGatherResourceGoal().getGatherTarget();
            e.gatherTicks = wUnit.getGatherResourceGoal().getGatherTicksLeft();
        }
        // ItemClientboundPacket.syncInventory
        if (le instanceof UnitInventory inv) {
            e.mask |= Field.INVENTORY.bit;
            List<ItemStack> items = inv.getAllItems();
            ArrayList<ItemStack> copy = new ArrayList<>(items.size());
            for (ItemStack stack : items) // copy so later server-side mutation can't race the encode
                copy.add(stack.copy());
            e.items = Collections.unmodifiableList(copy);
        }
        return e;
    }

    private static boolean statsChanged(Entry a, Entry b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return a.health != b.health || a.absorb != b.absorb || a.population != b.population ||
                !a.ownerName.equals(b.ownerName) || (dx * dx + dy * dy + dz * dz) > POS_EPSILON_SQR;
    }

    private static void copyStats(Entry from, Entry to) {
        to.mask |= Field.STATS.bit;
        to.health = from.health;
        to.absorb = from.absorb;
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.population = from.population;
        to.ownerName = from.ownerName;
    }

    private static boolean resourcesChanged(Entry a, Entry b) {
        return a.food != b.food || a.wood != b.wood || a.ore != b.ore || a.emerald != b.emerald;
    }

    private static void copyResources(Entry from, Entry to) {
        to.mask |= Field.RESOURCES.bit;
        to.food = from.food;
        to.wood = from.wood;
        to.ore = from.ore;
        to.emerald = from.emerald;
    }

    private static boolean workerChanged(Entry a, Entry b) {
        return a.isBuilding != b.isBuilding || a.isGathering != b.isGathering || a.gatherName != b.gatherName ||
                !Objects.equals(a.gatherPos, b.gatherPos) || a.gatherTicks != b.gatherTicks;
    }

    private static void copyWorker(Entry from, Entry to) {
        to.mask |= Field.WORKER.bit;
        to.isBuilding = from.isBuilding;
        to.isGathering = from.isGathering;
        to.gatherName = from.gatherName;
        to.gatherPos = from.gatherPos;
        to.gatherTicks = from.gatherTicks;
    }

    private static boolean itemsEqual(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
}
