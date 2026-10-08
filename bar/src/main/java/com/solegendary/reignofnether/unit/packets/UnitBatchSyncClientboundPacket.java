package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.items.ItemClientboundPacket;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * One packet per player per unit-sync (every UnitServerEvents.UNIT_SYNC_TICKS_MAX ticks) carrying everything the
 * old per-unit packets carried, but only for the fields that changed since that player last received them (see
 * UnitSyncBatcher). Replaces, for the periodic sync only:
 *  - UnitSyncClientboundPacket SYNC_STATS (per player, fog-gated), SYNC_RESOURCES, SYNC_ANCHOR_POS /
 *    REMOVE_ANCHOR_POS, MAKE_VILLAGER_VETERAN
 *  - UnitSyncWorkerClientBoundPacket
 *  - ItemClientboundPacket (inventory sync)
 * Those packets are still used for the immediate, event-driven syncs elsewhere in the code.
 *
 * Each entry is a unit id, a bitmask of Fields, then the payload of each present field in Field order. The client
 * applies them to exactly the same state the old packets updated (UnitClientEvents.applyBatchSync).
 */
public class UnitBatchSyncClientboundPacket {

    // Bit flags of what an entry carries. Order = wire order of the payloads.
    public enum Field {
        STATS,      // health, absorption, position, owner, population (fog-gated per player)
        RESOURCES,  // carried food/wood/ore/emerald
        ANCHOR,     // anchor pos or none
        VETERAN,    // villager became a veteran (one-way flag, no payload)
        WORKER,     // builder/gatherer animation + gather target state
        INVENTORY;  // UnitInventory contents

        public final int bit = 1 << ordinal();

        public boolean in(int mask) { return (mask & bit) != 0; }
    }

    public static final class Entry {
        public final int id;
        public int mask = 0;
        // STATS
        public float health;
        public float absorb;
        public double x, y, z;
        public int population;
        public String ownerName = "";
        // RESOURCES
        public int food, wood, ore, emerald;
        // ANCHOR
        @Nullable public BlockPos anchor;
        // WORKER
        public boolean isBuilding;
        public boolean isGathering;
        public ResourceName gatherName = ResourceName.NONE;
        @Nullable public BlockPos gatherPos;
        public int gatherTicks;
        // INVENTORY
        public List<ItemStack> items = List.of();

        public Entry(int id) { this.id = id; }

        public boolean has(Field f) { return f.in(mask); }
    }

    private final List<Entry> entries;

    public UnitBatchSyncClientboundPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> getEntries() { return entries; }

    public UnitBatchSyncClientboundPacket(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Entry e = new Entry(buf.readVarInt());
            e.mask = buf.readVarInt();
            if (e.has(Field.STATS)) {
                e.health = buf.readFloat();
                e.absorb = buf.readFloat();
                e.x = buf.readDouble();
                e.y = buf.readDouble();
                e.z = buf.readDouble();
                e.population = buf.readVarInt();
                e.ownerName = buf.readUtf();
            }
            if (e.has(Field.RESOURCES)) {
                e.food = buf.readVarInt();
                e.wood = buf.readVarInt();
                e.ore = buf.readVarInt();
                e.emerald = buf.readVarInt();
            }
            if (e.has(Field.ANCHOR)) {
                e.anchor = buf.readBoolean() ? buf.readBlockPos() : null;
            }
            if (e.has(Field.WORKER)) {
                e.isBuilding = buf.readBoolean();
                e.isGathering = buf.readBoolean();
                e.gatherName = buf.readEnum(ResourceName.class);
                e.gatherPos = buf.readBoolean() ? buf.readBlockPos() : null;
                e.gatherTicks = buf.readVarInt();
            }
            if (e.has(Field.INVENTORY)) {
                e.items = buf.readList(ItemClientboundPacket::readStack);
            }
            entries.add(e);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeVarInt(e.id);
            buf.writeVarInt(e.mask);
            if (e.has(Field.STATS)) {
                buf.writeFloat(e.health);
                buf.writeFloat(e.absorb);
                buf.writeDouble(e.x);
                buf.writeDouble(e.y);
                buf.writeDouble(e.z);
                buf.writeVarInt(e.population);
                buf.writeUtf(e.ownerName);
            }
            if (e.has(Field.RESOURCES)) {
                // resources can legitimately be negative in edge cases; varint handles it (5 bytes)
                buf.writeVarInt(e.food);
                buf.writeVarInt(e.wood);
                buf.writeVarInt(e.ore);
                buf.writeVarInt(e.emerald);
            }
            if (e.has(Field.ANCHOR)) {
                buf.writeBoolean(e.anchor != null);
                if (e.anchor != null)
                    buf.writeBlockPos(e.anchor);
            }
            if (e.has(Field.WORKER)) {
                buf.writeBoolean(e.isBuilding);
                buf.writeBoolean(e.isGathering);
                buf.writeEnum(e.gatherName);
                buf.writeBoolean(e.gatherPos != null);
                if (e.gatherPos != null)
                    buf.writeBlockPos(e.gatherPos);
                buf.writeVarInt(e.gatherTicks);
            }
            if (e.has(Field.INVENTORY)) {
                buf.writeCollection(e.items, ItemClientboundPacket::writeStack);
            }
        }
    }

    // client-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                UnitClientEvents.applyBatchSync(this.entries);
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
