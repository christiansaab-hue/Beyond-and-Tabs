package com.solegendary.reignofnether.unit;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A shared spatial bucket of every live server-side unit, keyed by 8x8 column cell. At 8v8 several
 * systems ask "which units are within r blocks of here?" for many points (formation guards per crossbow, capture
 * sites, bot power checks, guard threats), and each used to scan {@link UnitServerEvents#getAllUnits()} - hundreds
 * of units times hundreds of queries. The grid turns that into a look at the few cells the radius touches.
 *
 * It is rebuilt lazily: the first query in a server tick (or after a unit joined or left) re-buckets all units in
 * one O(n) pass; later queries in the same tick reuse it. Cell lists are kept and cleared rather than reallocated,
 * so a rebuild allocates nothing in steady state. Queries only pick candidates - callers still do their exact
 * distance test against the live position. Server thread only.
 */
public final class UnitGrid {
    private UnitGrid() { }

    // 8-block cells: a dense 8v8 brawl puts dozens of units in a chunk, and the typical query radius (6-14) then
    // touches 3-4 cells a side instead of pulling in whole 16x16 chunks of bystanders
    static final int SHIFT = 3;
    // a unit moves well under a block per tick, but queries pad by this much so a unit that crossed a cell edge since
    // the rebuild is still found
    static final double PAD = 2.0;

    private static final Long2ObjectOpenHashMap<ArrayList<LivingEntity>> CELLS = new Long2ObjectOpenHashMap<>();
    private static ServerLevel builtFor = null;
    private static long builtAt = Long.MIN_VALUE;
    private static int builtMod = -1, builtSize = -1;
    private static int rebuilds = 0;

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static void ensure(ServerLevel level) {
        List<LivingEntity> all = UnitServerEvents.getAllUnits();
        long now = level.getGameTime();
        if (builtFor == level && builtAt == now && builtMod == UnitServerEvents.getAllUnitsModCount() && builtSize == all.size())
            return;
        // clear in place; drop the map entirely now and then so cells of long-gone fights don't pile up
        if (++rebuilds % 1200 == 0) {
            CELLS.clear();
        } else {
            for (ArrayList<LivingEntity> cell : CELLS.values())
                cell.clear();
        }
        for (int i = 0, n = all.size(); i < n; i++) {
            LivingEntity le = all.get(i);
            if (le.level() != level || !le.isAlive())
                continue;
            long k = key(((int) Math.floor(le.getX())) >> SHIFT, ((int) Math.floor(le.getZ())) >> SHIFT);
            ArrayList<LivingEntity> cell = CELLS.get(k);
            if (cell == null) {
                cell = new ArrayList<>();
                CELLS.put(k, cell);
            }
            cell.add(le);
        }
        builtFor = level;
        builtAt = now;
        builtMod = UnitServerEvents.getAllUnitsModCount();
        builtSize = all.size();
    }

    /**
     * Fills {@code out} (cleared first) with the live units in {@code level} whose cell overlaps the square of
     * half-size {@code r} around (x, z). A superset of the units within r: callers filter by exact distance.
     */
    public static List<LivingEntity> near(ServerLevel level, double x, double z, double r, List<LivingEntity> out) {
        return inBox(level, x - r, z - r, x + r, z + r, out);
    }

    /** Same as {@link #near}, for an axis-aligned box (min/max X and Z). */
    public static List<LivingEntity> inBox(ServerLevel level, double minX, double minZ, double maxX, double maxZ,
                                           List<LivingEntity> out) {
        out.clear();
        ensure(level);
        int x0 = ((int) Math.floor(minX - PAD)) >> SHIFT, x1 = ((int) Math.floor(maxX + PAD)) >> SHIFT;
        int z0 = ((int) Math.floor(minZ - PAD)) >> SHIFT, z1 = ((int) Math.floor(maxZ + PAD)) >> SHIFT;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++) {
                ArrayList<LivingEntity> cell = CELLS.get(key(cx, cz));
                if (cell != null)
                    out.addAll(cell);
            }
        return out;
    }

    /** Forces a rebuild on the next query (the stress benchmark times the rebuild on its own). */
    public static void invalidate() {
        builtAt = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        CELLS.clear();
        builtFor = null;
        builtAt = Long.MIN_VALUE;
    }
}
