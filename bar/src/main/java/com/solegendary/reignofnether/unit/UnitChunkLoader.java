package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Keeps the chunks around RTS units loaded (and entity-ticking) so units keep acting when no player is near them.
 *
 * Previously every unit added 25 FORCED region tickets EVERY TICK (Unit.tick) and never removed them, plus each
 * unit owned its own Forge forced chunk (UnitServerEvents) that was never released on death. At 200+ units that
 * is ~5000+ ticket operations per tick and an ever-growing set of permanently loaded chunks (every chunk any unit
 * ever walked past stayed entity-ticking for the rest of the session).
 *
 * Now a single global, deduplicated set is recomputed about once per second (see UnitServerEvents.onServerTick):
 *  - region tickets (same level/radius as before: 5x5 chunks around each unit) are added only for chunks that
 *    newly need them, and removed once no unit has needed the chunk for LINGER_TICKS;
 *  - the Forge "ticking" forced chunk (forceTicks = random ticks without players nearby) is kept for every chunk
 *    that currently contains a unit, exactly like the old per-unit forced chunk, but deduplicated and released
 *    when the last unit leaves that chunk.
 * Server-thread only.
 */
public final class UnitChunkLoader {
    private UnitChunkLoader() {}

    // Own ticket type so our add/remove can never collide with other FORCED tickets (eg. WraithSnowBlockEntity).
    // No lifespan: tickets live until we remove them.
    private static final TicketType<ChunkPos> UNIT_TICKET =
            TicketType.create("reignofnether_unit", Comparator.comparingLong(ChunkPos::toLong));

    // Same as the old Unit.tick loop: a 2-chunk square radius around each unit, ticket distance 2 (level 31 =
    // entity ticking for every chunk in the 5x5).
    private static final int RADIUS = 2;
    private static final int TICKET_DISTANCE = 2;

    // Chunks a unit has left stay loaded this long, so units oscillating over a chunk border (or a group passing
    // through) don't churn chunk load/unload, and paths through recently visited terrain still see loaded chunks.
    public static final int LINGER_TICKS = 20 * 30;

    // Owner of the deduplicated Forge forced chunks. A fixed UUID lets the loading-validation callback below find
    // them again when the world is loaded.
    public static final UUID FORGE_OWNER =
            UUID.nameUUIDFromBytes("reignofnether:unit_chunk_loader".getBytes(StandardCharsets.UTF_8));

    private static final class LevelState {
        // chunk -> last game tick a unit needed it
        final Long2LongOpenHashMap regionChunks = new Long2LongOpenHashMap();
        // chunks that currently hold a Forge ticking-forced ticket
        final LongOpenHashSet forgeChunks = new LongOpenHashSet();
        // forge chunks restored from the save, kept unconditionally until this game tick so the units inside them
        // have time to load (entities load a little after their chunk) before we decide they're no longer needed
        final Long2LongOpenHashMap forgeGraceUntil = new Long2LongOpenHashMap();
    }

    // How long chunks restored from a save are kept regardless of whether a unit has shown up in them yet.
    private static final int RELOAD_GRACE_TICKS = 20 * 60;

    // Unit chunks found in the save by the validation callback, per level, waiting for that level's first update.
    // Written during world load (server thread), consumed by updateLevel.
    private static final Map<ResourceKey<Level>, LongOpenHashSet> RELOADED_CHUNKS = new HashMap<>();

    private static final Map<ResourceKey<Level>, LevelState> STATES = new HashMap<>();

    // Called once from the mod constructor. Forge persists forced-chunk tickets in the world save, and those
    // tickets are what reloads far-away units (and so puts them back in UnitServerEvents.allUnits) when a world is
    // opened again, so they must survive a restart. On load we:
    //  - keep our own deduplicated tickets and remember their chunks;
    //  - convert legacy entity-keyed tickets (one per unit ever spawned in an old save, never released on death)
    //    into remembered chunks and drop the legacy tickets; the first update re-forces them under FORGE_OWNER.
    // Every remembered chunk gets RELOAD_GRACE_TICKS before it can be released, so its units have time to load.
    // Block-keyed tickets (buildings) are left untouched.
    public static void registerForgeValidationCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(ReignOfNether.MOD_ID, (level, ticketHelper) -> {
            LongOpenHashSet chunks = new LongOpenHashSet();
            for (Map.Entry<UUID, Pair<LongSet, LongSet>> e : new ArrayList<>(ticketHelper.getEntityTickets().entrySet())) {
                chunks.addAll(e.getValue().getFirst());
                chunks.addAll(e.getValue().getSecond());
                if (!FORGE_OWNER.equals(e.getKey()))
                    ticketHelper.removeAllTickets(e.getKey());
            }
            if (!chunks.isEmpty())
                RELOADED_CHUNKS.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).addAll(chunks);
        });
    }

    /**
     * Loads and holds the 5x5 chunks around a position right now, through the same tickets the per-second update
     * uses, so units spawned there (a bot's starting workers, far from any player) land in loaded, entity-ticking
     * chunks instead of being unloaded - and "leaving the level" - before the next update picks them up.
     */
    public static void holdArea(ServerLevel level, BlockPos pos) {
        LevelState state = STATES.computeIfAbsent(level.dimension(), k -> new LevelState());
        ServerChunkCache cache = level.getChunkSource();
        long now = level.getGameTime();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                long chunk = ChunkPos.asLong(cx + dx, cz + dz);
                ChunkPos cp = new ChunkPos(chunk);
                if (!state.regionChunks.containsKey(chunk))
                    cache.addRegionTicket(UNIT_TICKET, cp, TICKET_DISTANCE, cp);
                state.regionChunks.put(chunk, now);
                level.getChunk(cp.x, cp.z);   // synchronous load/generate so the terrain exists before spawning
            }
    }

    public static void update(MinecraftServer server, Collection<LivingEntity> units) {
        // group the needed chunks by level
        Map<ResourceKey<Level>, LongOpenHashSet> neededRegion = new HashMap<>();
        Map<ResourceKey<Level>, LongOpenHashSet> neededForge = new HashMap<>();
        for (LivingEntity le : units) {
            if (!(le.level() instanceof ServerLevel sl) || !le.isAlive())
                continue;
            int cx = le.getBlockX() >> 4;
            int cz = le.getBlockZ() >> 4;
            LongOpenHashSet region = neededRegion.computeIfAbsent(sl.dimension(), k -> new LongOpenHashSet());
            for (int dx = -RADIUS; dx <= RADIUS; dx++)
                for (int dz = -RADIUS; dz <= RADIUS; dz++)
                    region.add(ChunkPos.asLong(cx + dx, cz + dz));
            neededForge.computeIfAbsent(sl.dimension(), k -> new LongOpenHashSet()).add(ChunkPos.asLong(cx, cz));
        }
        // also visit levels that no longer have units, so their tickets still age out
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            if (!neededRegion.containsKey(key) && !STATES.containsKey(key) && !RELOADED_CHUNKS.containsKey(key))
                continue;
            updateLevel(level,
                    neededRegion.getOrDefault(key, new LongOpenHashSet()),
                    neededForge.getOrDefault(key, new LongOpenHashSet()));
        }
    }

    private static void updateLevel(ServerLevel level, LongOpenHashSet neededRegion, LongOpenHashSet neededForge) {
        LevelState state = STATES.computeIfAbsent(level.dimension(), k -> new LevelState());
        ServerChunkCache cache = level.getChunkSource();
        long now = level.getGameTime();

        // adopt unit chunks restored from the save (see registerForgeValidationCallback)
        LongOpenHashSet reloaded = RELOADED_CHUNKS.remove(level.dimension());
        if (reloaded != null) {
            LongIterator rit = reloaded.iterator();
            while (rit.hasNext()) {
                long chunk = rit.nextLong();
                // FORGE_OWNER tickets were kept by the callback; legacy ones were dropped, so (re)force. Forcing an
                // already-forced chunk for the same owner is a no-op in ForgeChunkManager.
                if (state.forgeChunks.add(chunk))
                    forceForge(level, chunk, true);
                state.forgeGraceUntil.put(chunk, now + RELOAD_GRACE_TICKS);
            }
        }

        // region tickets: add new, refresh still-needed
        LongIterator it = neededRegion.iterator();
        while (it.hasNext()) {
            long chunk = it.nextLong();
            if (!state.regionChunks.containsKey(chunk)) {
                ChunkPos cp = new ChunkPos(chunk);
                cache.addRegionTicket(UNIT_TICKET, cp, TICKET_DISTANCE, cp);
            }
            state.regionChunks.put(chunk, now);
        }
        // region tickets: drop ones nobody has needed for LINGER_TICKS
        ObjectIterator<Long2LongMap.Entry> eit = state.regionChunks.long2LongEntrySet().fastIterator();
        while (eit.hasNext()) {
            Long2LongMap.Entry e = eit.next();
            if (now - e.getLongValue() > LINGER_TICKS) {
                ChunkPos cp = new ChunkPos(e.getLongKey());
                cache.removeRegionTicket(UNIT_TICKET, cp, TICKET_DISTANCE, cp);
                eit.remove();
            }
        }

        // forge ticking-forced chunks: exactly the chunks that currently contain a unit
        it = neededForge.iterator();
        while (it.hasNext()) {
            long chunk = it.nextLong();
            if (state.forgeChunks.add(chunk))
                forceForge(level, chunk, true);
        }
        it = state.forgeChunks.iterator();
        while (it.hasNext()) {
            long chunk = it.nextLong();
            if (state.forgeGraceUntil.containsKey(chunk)) {
                if (now < state.forgeGraceUntil.get(chunk))
                    continue;
                state.forgeGraceUntil.remove(chunk);
            }
            if (!neededForge.contains(chunk)) {
                forceForge(level, chunk, false);
                it.remove();
            }
        }
    }

    private static void forceForge(ServerLevel level, long chunk, boolean add) {
        ForgeChunkManager.forceChunk(level, ReignOfNether.MOD_ID, FORGE_OWNER,
                ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, true);
    }

    public static int getLoadedRegionChunkCount() {
        int n = 0;
        for (LevelState s : STATES.values()) n += s.regionChunks.size();
        return n;
    }

    // Server stopping: tickets die with the server; just forget our bookkeeping so a new world in the same JVM
    // (singleplayer) starts clean.
    public static void clear() {
        STATES.clear();
        RELOADED_CHUNKS.clear();
    }
}
