package com.solegendary.reignofnether.tide;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.solegendary.reignofnether.blocks.TidepoolBlock;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.resources.MetalPatches;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tidewrought <b>tidepools</b> - the registry, caps and counterplay of the Tides core (design/tidewrought_plan.md
 * section 3). A tidepool is a disc of {@link TidepoolBlock} cells (temporary shallow "water" with no fluid physics)
 * that lives for a fixed time and then dries up. Units and buildings that use pools build on this API; nothing here
 * knows about Tide Priests, the Harbourmaster or the Leviathan.
 *
 * <h2>API (server thread only)</h2>
 * <pre>
 *   Pool p = TidepoolServerEvents.raise(level, owner, centre, radius, lifetimeTicks);   // null = refused
 *   Pool t = TidepoolServerEvents.raiseTrail(level, owner, leviathan, centre, 1, 200);  // trail pools, own cap
 *   p.isActive(); p.cellCount(); p.ticksLeft(); p.positions();                           // read-only handle
 *   TidepoolServerEvents.drain(p);                    // dries in DRAIN_TICKS (constructor reclaim does this)
 *   TidepoolServerEvents.evaporate(level, at, r);     // fire attacks: removes the cells they hit, returns count
 *   TidepoolServerEvents.poolAt(level, pos);          // the live pool owning that cell (or null)
 *   TidepoolServerEvents.isTidepool(level, pos);      // cell check (also TidesServerEvents.isWet for units)
 *   TidepoolServerEvents.clearOwner(owner) / clearAll();
 *   TidepoolServerEvents.lastRefusal();               // lang key why the last raise returned null (HUD message)
 * </pre>
 * <ul>
 *   <li><b>Placement</b> ({@link #raise}): a disc of radius 0-{@link #MAX_RADIUS} (r1 = 5 cells, r2 = 13, r3 = 29).
 *       Per column the cell is the first <b>air</b> block, scanning down from centre.y+2 to centre.y-2, that sits on
 *       a floor with a sturdy top face. It never replaces anything (grass, flowers, snow, crops, another pool), never
 *       goes on a metal patch, and never inside a building's bounding box. A raise that finds no valid cell, or would
 *       push the level over {@link #MAX_CELLS_PER_LEVEL}, is refused (null + {@link #lastRefusal()}).</li>
 *   <li><b>Caps</b>: {@link #POOLS_PER_PLAYER} live pools per owner (all raisers share it) - raising one more evicts
 *       the owner's oldest at once. Trail pools ({@link #raiseTrail}) are counted per source entity
 *       ({@link #TRAIL_POOLS_PER_SOURCE}) and never evict an owner's normal pools. {@link #MAX_CELLS_PER_LEVEL} cells
 *       per level in total (eviction frees cells first, so a capped player can always re-raise).</li>
 *   <li><b>Expiry</b>: checked once a second ({@link #EXPIRY_PASS_TICKS}), not per tick. A cell is only removed while
 *       it is still a tidepool (a building or block may have replaced it). Each cell also carries a scheduled block
 *       tick at lifetime + {@link #FAILSAFE_MARGIN_TICKS}; block ticks are saved with the chunk, so a cell whose pool
 *       this (unsaved) registry forgot - restart, or chunk unloaded at expiry - still dries up.</li>
 *   <li><b>Counterplay</b>: a constructor's RECLAIM order on a pool (single or area reclaim) walks it there and
 *       {@link #drain}s the whole pool in {@link #DRAIN_TICKS} once within {@link #DRAIN_REACH} blocks. Fire or lava
 *       next to a cell evaporates it (TidepoolBlock), fire abilities call {@link #evaporate}. Placing any building or
 *       block over a pool fills it (cells are replaceable / soft for building validation).</li>
 *   <li><b>Match lifecycle</b>: {@link #clearOwner} on defeat, {@link #clearAll} on /rts-reset. Nothing is saved.</li>
 * </ul>
 * Fog of war: pools are ordinary blocks, so they ride RoN's fog-filtered chunk/block updates - an enemy only sees a
 * pool raised where they have vision, and one that dries inside a dark chunk stays drawn until that chunk is seen
 * again (the same "last seen" behaviour as buildings). No packets of its own.
 * <p>
 * Debug: {@code /rts-tidepool <radius> [seconds]} (op) raises a pool at the caller's feet, owned by the caller.
 */
public class TidepoolServerEvents {

    public static final int DEFAULT_LIFETIME_TICKS = 30 * 20;
    public static final int MAX_RADIUS = 4;
    public static final int POOLS_PER_PLAYER = 4;
    public static final int TRAIL_POOLS_PER_SOURCE = 6;
    public static final int MAX_CELLS_PER_LEVEL = 1200;
    public static final int EXPIRY_PASS_TICKS = 20;
    public static final int FAILSAFE_MARGIN_TICKS = 5 * 20;
    public static final int DRAIN_TICKS = 2 * 20;
    public static final double DRAIN_REACH = 4.0;
    /** A drain order whose worker never reaches the pool is dropped after this. */
    public static final int DRAIN_ORDER_TIMEOUT_TICKS = 30 * 20;
    /** How far above/below the aimed y a column looks for the surface. */
    public static final int SURFACE_SEARCH = 2;

    public static final String REFUSE_NO_GROUND = "tide.reignofnether.no_ground";
    public static final String REFUSE_BUDGET = "tide.reignofnether.too_many_tidepools";

    /** Read-only handle to one raised pool. Holding it after expiry is fine: {@link #isActive()} turns false. */
    public static final class Pool {
        public final int id;
        public final String owner;
        public final ServerLevel level;
        public final BlockPos centre;
        public final int radius;
        /** -1 for a normal pool, else the entity id of the trail source (Leviathan). */
        public final int trailSource;
        final long[] cells;
        long expireTick;
        boolean draining = false;
        boolean removed = false;

        Pool(int id, String owner, ServerLevel level, BlockPos centre, int radius, int trailSource, long[] cells, long expireTick) {
            this.id = id;
            this.owner = owner;
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.trailSource = trailSource;
            this.cells = cells;
            this.expireTick = expireTick;
        }

        public boolean isActive() { return !removed; }
        public boolean isDraining() { return draining && !removed; }
        public boolean isTrail() { return trailSource >= 0; }
        /** Cells raised (some may since have been filled or evaporated). */
        public int cellCount() { return cells.length; }
        public long expireTick() { return expireTick; }
        public long ticksLeft() { return removed ? 0 : Math.max(0, expireTick - level.getGameTime()); }

        /** The raised cell positions (allocates; not for hot paths). */
        public List<BlockPos> positions() {
            List<BlockPos> out = new ArrayList<>(cells.length);
            for (long c : cells)
                out.add(BlockPos.of(c));
            return out;
        }
    }

    private record DrainOrder(Pool pool, long deadline) { }

    /** Every live pool, oldest first (so the first of an owner's is the one to evict). */
    private static final List<Pool> POOLS = new ArrayList<>();
    /** Per level: cell -> the pool that raised it. Its size is the level's cell budget use. */
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Pool>> CELLS = new HashMap<>();
    /** Worker entity id -> the pool it was ordered to drain. */
    private static final Int2ObjectOpenHashMap<DrainOrder> DRAIN_ORDERS = new Int2ObjectOpenHashMap<>();
    private static int nextId = 1;
    private static String lastRefusal = null;

    // reused (server thread only)
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

    // ------------------------------------------------------------------ raise

    /** Raises a normal pool; see the class comment for the rules. Returns null if refused. */
    public static Pool raise(ServerLevel level, String owner, BlockPos centre, int radius, int lifetimeTicks) {
        return raise(level, owner, centre, radius, lifetimeTicks, -1);
    }

    /**
     * Raises a trail pool for {@code source} (the Leviathan's wake): capped at {@link #TRAIL_POOLS_PER_SOURCE} per
     * source, oldest evicted, and never counted against or evicting the owner's normal pools.
     */
    public static Pool raiseTrail(ServerLevel level, String owner, Entity source, BlockPos centre, int radius, int lifetimeTicks) {
        if (source == null)
            return null;
        return raise(level, owner, centre, radius, lifetimeTicks, source.getId());
    }

    static Pool raise(ServerLevel level, String owner, BlockPos centre, int radius, int lifetimeTicks, int trailSource) {
        lastRefusal = null;
        if (level == null || centre == null || owner == null || owner.isBlank() || lifetimeTicks <= 0) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        radius = Math.max(0, Math.min(MAX_RADIUS, radius));
        LongArrayList found = collectCells(level, centre, radius);
        if (found.isEmpty()) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        // the pool this raise would push out: the owner's oldest (or the source's oldest trail pool) at the cap
        Pool evict = null;
        int count = 0;
        for (Pool p : POOLS) {
            if (!p.owner.equals(owner) || p.trailSource != trailSource)
                continue;
            if (evict == null)
                evict = p;
            count++;
        }
        int cap = trailSource >= 0 ? TRAIL_POOLS_PER_SOURCE : POOLS_PER_PLAYER;
        if (count < cap)
            evict = null;
        int freed = evict != null && evict.level == level ? liveCellsOf(evict) : 0;
        if (cellsIn(level) - freed + found.size() > MAX_CELLS_PER_LEVEL) {
            lastRefusal = REFUSE_BUDGET;
            return null;
        }
        if (evict != null)
            remove(evict);

        long expire = level.getGameTime() + lifetimeTicks;
        TidepoolBlock block = BlockRegistrar.TIDEPOOL.get();
        BlockState state = block.defaultBlockState();
        LongArrayList placed = new LongArrayList(found.size());
        for (int i = 0; i < found.size(); i++) {
            long c = found.getLong(i);
            cursor.set(c);
            if (!level.setBlock(cursor, state, Block.UPDATE_ALL) || !level.getBlockState(cursor).is(block))
                continue;   // refused, or fire right beside it evaporated it at once (TidepoolBlock.onPlace)
            level.scheduleTick(cursor.immutable(), block, lifetimeTicks + FAILSAFE_MARGIN_TICKS);
            placed.add(c);
        }
        if (placed.isEmpty()) {
            lastRefusal = REFUSE_NO_GROUND;
            return null;
        }
        Pool pool = new Pool(nextId++, owner, level, centre.immutable(), radius, trailSource, placed.toLongArray(), expire);
        POOLS.add(pool);
        Long2ObjectOpenHashMap<Pool> map = cellMap(level);
        for (long c : pool.cells)
            map.put(c, pool);
        level.sendParticles(ParticleTypes.SPLASH, centre.getX() + 0.5, centre.getY() + 0.2, centre.getZ() + 0.5,
                6 + radius * 6, radius * 0.6 + 0.3, 0.1, radius * 0.6 + 0.3, 0.1);
        return pool;
    }

    /** The cells a raise at this spot would fill (empty if none is valid). Public for the game test / ability previews. */
    public static LongArrayList collectCells(ServerLevel level, BlockPos centre, int radius) {
        LongArrayList out = new LongArrayList();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2)
                    continue;
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                for (int y = centre.getY() + SURFACE_SEARCH; y >= centre.getY() - SURFACE_SEARCH; y--) {
                    cursor.set(x, y, z);
                    if (!level.isLoaded(cursor))
                        break;
                    BlockState at = level.getBlockState(cursor);
                    if (!at.isAir())
                        continue;
                    below.set(x, y - 1, z);
                    BlockState floor = level.getBlockState(below);
                    if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty())
                        continue;
                    // this column's surface: take it or leave the column, never look further down under it
                    if (!floor.is(MetalPatches.PATCH_BLOCK) && !BuildingUtils.isPosInsideAnyBuilding(false, cursor))
                        out.add(cursor.asLong());
                    break;
                }
            }
        return out;
    }

    // ------------------------------------------------------------------ removal

    /** Removes a pool now: every cell it raised that is still a tidepool turns back to air. */
    static void remove(Pool pool) {
        if (pool.removed)
            return;
        pool.removed = true;
        POOLS.remove(pool);
        Long2ObjectOpenHashMap<Pool> map = CELLS.get(pool.level.dimension());
        for (long c : pool.cells) {
            Pool owner = map == null ? null : map.get(c);
            if (owner != null && owner != pool)
                continue;   // another pool has since raised this cell
            if (owner != null)
                map.remove(c);
            cursor.set(c);
            // an unloaded cell is left to its scheduled fail-safe tick
            if (pool.level.isLoaded(cursor) && pool.level.getBlockState(cursor).getBlock() instanceof TidepoolBlock)
                pool.level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Drains a pool: it dries up within {@link #DRAIN_TICKS} (on the next expiry passes). Returns false if it was
     * already gone. What a constructor's reclaim order on a pool ends in; free, no resources.
     */
    public static boolean drain(Pool pool) {
        if (pool == null || pool.removed)
            return false;
        pool.draining = true;
        pool.expireTick = Math.min(pool.expireTick, pool.level.getGameTime() + DRAIN_TICKS);
        pool.level.sendParticles(ParticleTypes.BUBBLE_POP, pool.centre.getX() + 0.5, pool.centre.getY() + 0.2,
                pool.centre.getZ() + 0.5, 10 + pool.radius * 8, pool.radius * 0.6 + 0.3, 0.05, pool.radius * 0.6 + 0.3, 0.02);
        return true;
    }

    /** Fire dries one cell at once (steam + fizz). Called by TidepoolBlock when fire or lava touches it. */
    public static void evaporateCell(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Pool> map = CELLS.get(level.dimension());
        if (map != null)
            map.remove(pos.asLong());
        if (level.getBlockState(pos).getBlock() instanceof TidepoolBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);   // vanilla fizz sound + smoke, one event
        }
    }

    /**
     * Fire-typed attacks: evaporates every tidepool cell within {@code radius} blocks (horizontally, and
     * +-{@link #SURFACE_SEARCH} vertically) of {@code at}. Returns the number of cells dried.
     */
    public static int evaporate(ServerLevel level, BlockPos at, int radius) {
        Long2ObjectOpenHashMap<Pool> map = CELLS.get(level.dimension());
        if (map == null || map.isEmpty())
            return 0;   // no pool anywhere: no block reads at all
        int n = 0, r2 = radius * radius;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2)
                    continue;
                for (int dy = -SURFACE_SEARCH; dy <= SURFACE_SEARCH; dy++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.getBlockState(p).getBlock() instanceof TidepoolBlock) {
                        evaporateCell(level, p.immutable());
                        n++;
                    }
                }
            }
        return n;
    }

    /** Removes all of an owner's pools now (player defeated). */
    public static void clearOwner(String owner) {
        if (owner == null)
            return;
        for (Pool p : new ArrayList<>(POOLS))
            if (p.owner.equals(owner))
                remove(p);
    }

    /** Removes every pool now (match reset). */
    public static void clearAll() {
        for (Pool p : new ArrayList<>(POOLS))
            remove(p);
        DRAIN_ORDERS.clear();
    }

    // ------------------------------------------------------------------ queries

    /** The live pool that raised this exact cell, or null. */
    public static Pool poolAt(Level level, BlockPos pos) {
        Long2ObjectOpenHashMap<Pool> map = CELLS.get(level.dimension());
        if (map == null)
            return null;
        Pool p = map.get(pos.asLong());
        return p == null || p.removed ? null : p;
    }

    /** Like {@link #poolAt} but also accepts the floor block under a cell or the block above it (a click). */
    public static Pool poolNear(Level level, BlockPos pos) {
        Pool p = poolAt(level, pos);
        if (p == null)
            p = poolAt(level, pos.above());
        if (p == null)
            p = poolAt(level, pos.below());
        return p;
    }

    public static boolean isTidepool(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof TidepoolBlock;
    }

    /** Live pools of an owner, oldest first (allocates). Trail pools included. */
    public static List<Pool> poolsOf(String owner) {
        List<Pool> out = new ArrayList<>();
        for (Pool p : POOLS)
            if (p.owner.equals(owner))
                out.add(p);
        return out;
    }

    /** Live, non-draining pools whose disc touches the circle (area reclaim). Allocates. */
    public static List<Pool> poolsIn(Level level, BlockPos centre, int radius, int maxDy) {
        List<Pool> out = new ArrayList<>();
        for (Pool p : POOLS) {
            if (p.level != level || p.draining || Math.abs(p.centre.getY() - centre.getY()) > maxDy)
                continue;
            double dx = p.centre.getX() - centre.getX(), dz = p.centre.getZ() - centre.getZ();
            double reach = radius + p.radius;
            if (dx * dx + dz * dz <= reach * reach)
                out.add(p);
        }
        return out;
    }

    /** Cells currently registered in this level (the {@link #MAX_CELLS_PER_LEVEL} budget use). */
    public static int cellsIn(Level level) {
        Long2ObjectOpenHashMap<Pool> map = CELLS.get(level.dimension());
        return map == null ? 0 : map.size();
    }

    /** Lang key of why the last {@link #raise} returned null, or null if it succeeded. */
    public static String lastRefusal() {
        return lastRefusal;
    }

    /** For TidepoolBlock's fail-safe tick: ticks until this cell's pool expires, 0 if no live pool owns it. */
    public static long ticksLeftForCell(ServerLevel level, BlockPos pos) {
        Pool p = poolAt(level, pos);
        return p == null ? 0 : p.ticksLeft();
    }

    static int liveCellsOf(Pool pool) {
        Long2ObjectOpenHashMap<Pool> map = CELLS.get(pool.level.dimension());
        if (map == null)
            return 0;
        int n = 0;
        for (long c : pool.cells)
            if (map.get(c) == pool)
                n++;
        return n;
    }

    private static Long2ObjectOpenHashMap<Pool> cellMap(Level level) {
        return CELLS.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
    }

    // ------------------------------------------------------------------ drain orders (constructor reclaim)

    /**
     * A worker's RECLAIM order landed on (or next to) a pool: drain it once the worker is within
     * {@link #DRAIN_REACH}. Returns true if there was a pool there (the caller then walks the worker to it).
     */
    public static boolean orderDrain(ServerLevel level, LivingEntity worker, BlockPos clicked) {
        Pool pool = clicked == null ? null : poolNear(level, clicked);
        if (pool == null || worker == null)
            return false;
        if (inDrainReach(worker, pool))
            drain(pool);
        else
            DRAIN_ORDERS.put(worker.getId(), new DrainOrder(pool, level.getGameTime() + DRAIN_ORDER_TIMEOUT_TICKS));
        return true;
    }

    /** Is the worker ordered to drain a pool right now (keeps it from counting as idle elsewhere, if wanted). */
    public static boolean isDraining(Entity worker) {
        return worker != null && DRAIN_ORDERS.containsKey(worker.getId());
    }

    static boolean inDrainReach(Entity worker, Pool pool) {
        double dx = worker.getX() - (pool.centre.getX() + 0.5), dz = worker.getZ() - (pool.centre.getZ() + 0.5);
        double reach = pool.radius + DRAIN_REACH;
        return worker.level() == pool.level && Math.abs(worker.getY() - pool.centre.getY()) <= 4
                && dx * dx + dz * dz <= reach * reach;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (evt.getServer().getTickCount() % EXPIRY_PASS_TICKS != 0)
            return;
        if (POOLS.isEmpty() && DRAIN_ORDERS.isEmpty())
            return;
        for (ServerLevel level : evt.getServer().getAllLevels())
            tickExpiry(level);
    }

    /** One expiry + drain-order pass for a level. Public for the game test (which can't wait for the cadence). */
    public static void tickExpiry(ServerLevel level) {
        long now = level.getGameTime();
        if (!DRAIN_ORDERS.isEmpty()) {
            var it = DRAIN_ORDERS.int2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                DrainOrder o = e.getValue();
                if (o.pool.level != level)
                    continue;
                Entity worker = level.getEntity(e.getIntKey());
                if (o.pool.removed || o.pool.draining || worker == null || !worker.isAlive() || now > o.deadline) {
                    it.remove();
                } else if (inDrainReach(worker, o.pool)) {
                    drain(o.pool);
                    it.remove();
                }
            }
        }
        for (int i = POOLS.size() - 1; i >= 0; i--) {
            Pool p = POOLS.get(i);
            if (p.level == level && now >= p.expireTick)
                remove(p);   // removes index i only: earlier indices are unaffected
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        // blocks stay; their saved fail-safe ticks dry them up when the world is next loaded
        POOLS.clear();
        CELLS.clear();
        DRAIN_ORDERS.clear();
        lastRefusal = null;
    }

    // ------------------------------------------------------------------ debug command

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-tidepool")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                .executes(ctx -> raiseFromCommand(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                        DEFAULT_LIFETIME_TICKS / 20))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                    .executes(ctx -> raiseFromCommand(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    static int raiseFromCommand(CommandSourceStack src, int radius, int seconds) {
        String owner = src.getEntity() != null ? src.getEntity().getName().getString() : "server";
        Pool p = raise(src.getLevel(), owner, BlockPos.containing(src.getPosition()), radius, seconds * 20);
        if (p == null) {
            src.sendFailure(Component.translatable(lastRefusal == null ? REFUSE_NO_GROUND : lastRefusal));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Raised tidepool #" + p.id + " (" + p.cellCount() + " cells, "
                + seconds + " s)"), false);
        return 1;
    }
}
