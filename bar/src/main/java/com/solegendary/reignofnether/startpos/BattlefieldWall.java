package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The battlefield boundary: when a match forms, a great ring wall rises around all the start positions - stone
 * brick ramparts with crenellations and watchtowers, following the terrain like the Great Wall. It bounds the
 * arena (no more wandering off hunting for the enemy) without giving anyone's base away.
 *
 * Built a few columns per tick so even a 2km wall never causes a lag spike; from the ground it reads as the wall
 * rising out of the earth over the opening half minute of the match.
 */
public class BattlefieldWall {

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static final int WALL_HEIGHT = 7;        // rampart body above ground
    static final int TOWER_EVERY = 48;       // a watchtower every N columns
    static final int COLUMNS_PER_TICK = 40;  // build speed (~30-60s for a full ring)
    static final int MARGIN = 90;            // breathing room beyond the farthest base
    static final int MIN_RADIUS = 150;
    static final int MAX_RADIUS = 420;

    /** One column of wall still to be built. */
    record Column(int x, int z, double angle, boolean tower, boolean crenel) { }

    static final ArrayDeque<Column> queue = new ArrayDeque<>();
    static ServerLevel buildLevel = null;
    static final Random random = new Random();

    /** Plans the ring and starts the per-tick build. Safe to call once per match. */
    public static void build(ServerLevel level, List<BlockPos> positions) {
        if (level == null || positions.isEmpty())
            return;
        double cx = 0, cz = 0;
        for (BlockPos p : positions) {
            cx += p.getX() + 0.5;
            cz += p.getZ() + 0.5;
        }
        cx /= positions.size();
        cz /= positions.size();
        double radius = 0;
        for (BlockPos p : positions)
            radius = Math.max(radius, Math.hypot(p.getX() + 0.5 - cx, p.getZ() + 0.5 - cz));
        radius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, radius + MARGIN));

        int steps = (int) Math.ceil(Math.PI * 2 * radius);
        Set<Long> seen = new HashSet<>();
        int col = 0;
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) Math.round(cx + Math.cos(a) * radius);
            int z = (int) Math.round(cz + Math.sin(a) * radius);
            if (!seen.add(((long) x << 32) ^ (z & 0xffffffffL)))
                continue;
            queue.add(new Column(x, z, a, col % TOWER_EVERY == 0, col % 2 == 0));
            col++;
        }
        buildLevel = level;
        ReignOfNether.LOGGER.info("[BattlefieldWall] raising a ring wall: centre [{}, {}], radius {}, {} columns",
            (int) cx, (int) cz, (int) radius, col);
    }

    public static boolean isBuilding() {
        return !queue.isEmpty();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        queue.clear();
        buildLevel = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || queue.isEmpty() || buildLevel == null)
            return;
        for (int n = 0; n < COLUMNS_PER_TICK && !queue.isEmpty(); n++)
            buildColumn(buildLevel, queue.poll());
        if (queue.isEmpty())
            ReignOfNether.LOGGER.info("[BattlefieldWall] the wall is complete");
    }

    static BlockState body() {
        int r = random.nextInt(100);
        if (r < 10) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (r < 18) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    static void buildColumn(ServerLevel level, Column c) {
        // the wall runs 2 thick: this column and one block towards the ring centre
        int ix = (int) Math.round(c.x() - Math.cos(c.angle()));
        int iz = (int) Math.round(c.z() - Math.sin(c.angle()));

        if (c.tower()) {
            buildTower(level, c.x(), c.z());
            return;
        }
        int outerBase = groundY(level, c.x(), c.z());
        int innerBase = groundY(level, ix, iz);
        int top = Math.max(outerBase, innerBase) + WALL_HEIGHT;

        // outer face: body + crenellation on alternating columns
        fillColumn(level, c.x(), c.z(), outerBase - 2, top);
        if (c.crenel())
            level.setBlock(new BlockPos(c.x(), top + 1, c.z()),
                Blocks.STONE_BRICK_WALL.defaultBlockState(), FLAGS);

        // inner face: body + walkway slab
        if (ix != c.x() || iz != c.z()) {
            fillColumn(level, ix, iz, innerBase - 2, top);
            level.setBlock(new BlockPos(ix, top + 1, iz),
                Blocks.STONE_BRICK_SLAB.defaultBlockState(), FLAGS);
        }
    }

    static void buildTower(ServerLevel level, int tx, int tz) {
        int base = groundY(level, tx, tz);
        int top = base + WALL_HEIGHT + 4;
        for (int x = tx - 1; x <= tx + 1; x++)
            for (int z = tz - 1; z <= tz + 1; z++) {
                boolean edge = x == tx - 1 || x == tx + 1 || z == tz - 1 || z == tz + 1;
                fillColumn(level, x, z, base - 2, top);
                // corner crenellations and a lit beacon so towers read at night
                if (edge && (x != tx && z != tz))
                    level.setBlock(new BlockPos(x, top + 1, z),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), FLAGS);
            }
        level.setBlock(new BlockPos(tx, top + 1, tz), Blocks.CAMPFIRE.defaultBlockState(), FLAGS);
    }

    static void fillColumn(ServerLevel level, int x, int z, int from, int to) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int min = Math.max(level.getMinBuildHeight() + 1, from);
        for (int y = min; y <= to; y++)
            level.setBlock(p.set(x, y, z), body(), FLAGS);
    }

    /** The ground the wall founds on at (x, z): top solid-ish surface, water surface over rivers and lakes. */
    static int groundY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }
}
