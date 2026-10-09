package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Beyond and Tabs: tidy fields, wild edges. When a readied match starts, the ground round each start position and
 * straight lanes from each start position to the map centre are cleared of trees, bushes, tall grass, flowers and
 * snow (no drops, no block updates). Forests everywhere else stay, so the world still looks like Minecraft while the
 * places you build and fight on read cleanly. Controlled by the gamerule clearStartAreas (default true).
 */
public final class StartAreaClearing {
    private StartAreaClearing() { }

    public static final float BASE_RADIUS = 28f;
    public static final float LANE_WIDTH = 7f;
    /** Lanes longer than this are cut short so a far-off start position cannot load half the world. */
    public static final float MAX_LANE_LENGTH = 512f;

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static boolean isEnabled(ServerLevel level) {
        return level != null && level.getGameRules().getBoolean(GameRuleRegistrar.CLEAR_START_AREAS);
    }

    /** Clears a disc round each position and a lane from each position to their centroid (the map centre). */
    public static void clearStartAreas(ServerLevel level, List<BlockPos> positions) {
        if (level == null || positions.isEmpty() || !isEnabled(level))
            return;
        try {
            float cx = 0, cz = 0;
            for (BlockPos p : positions) {
                cx += p.getX() + 0.5f;
                cz += p.getZ() + 0.5f;
            }
            cx /= positions.size();
            cz /= positions.size();
            for (BlockPos p : positions)
                disc(level, p.getX() + 0.5f, p.getZ() + 0.5f, BASE_RADIUS);
            if (positions.size() > 1) {
                for (BlockPos p : positions) {
                    float x0 = p.getX() + 0.5f, z0 = p.getZ() + 0.5f;
                    float dx = cx - x0, dz = cz - z0;
                    float len = (float) Math.hypot(dx, dz);
                    if (len > MAX_LANE_LENGTH) {
                        dx *= MAX_LANE_LENGTH / len;
                        dz *= MAX_LANE_LENGTH / len;
                    }
                    lane(level, x0, z0, x0 + dx, z0 + dz, LANE_WIDTH);
                }
            }
            stampMetalPatches(level, positions, cx, cz);
            com.solegendary.reignofnether.resources.MetalPatches.syncToClients(level);
            ReignOfNether.LOGGER.info("[StartAreaClearing] cleared {} start areas and lanes to [{}, {}]", positions.size(), cx, cz);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[StartAreaClearing] failed to clear start areas", e);
        }
    }

    /**
     * BAR-style map economy: three metal patches round every base, one forward patch along each lane towards the
     * middle, and a contested cluster in the centre. Extractors only work on these (see MetalPatches).
     */
    static void stampMetalPatches(ServerLevel level, List<BlockPos> positions, float cx, float cz) {
        java.util.Random rng = new java.util.Random(positions.hashCode() * 31L + positions.size());
        for (BlockPos p : positions) {
            float x0 = p.getX() + 0.5f, z0 = p.getZ() + 0.5f;
            float dx = cx - x0, dz = cz - z0, len = (float) Math.hypot(dx, dz);
            double toCentre = len < 1e-3f ? 0 : Math.atan2(dz, dx);
            for (int k = -1; k <= 1; k++) {   // home ring, symmetric about the lane
                double a = toCentre + Math.PI + k * (Math.PI / 2.2);   // behind and beside the base
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                        (int) (x0 + Math.cos(a) * 17), (int) (z0 + Math.sin(a) * 17));
            }
            if (len > 40) {
                // near expansion on the lane, worth walking out for
                float k = Math.min(0.35f, 70f / Math.max(1f, len));
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) (x0 + dx * k), (int) (z0 + dz * k));
                // flank expansions: off to each side, farther out, richer (2 patches) on one random side -
                // bases that want them have to stretch, and denying them actually hurts
                float px = -dz / len, pz = dx / len;
                int richSide = rng.nextBoolean() ? 1 : -1;
                for (int side = -1; side <= 1; side += 2) {
                    float ex = x0 + dx * 0.5f + px * side * len * 0.55f;
                    float ez = z0 + dz * 0.5f + pz * side * len * 0.55f;
                    com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) ex, (int) ez);
                    if (side == richSide)
                        com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) (ex + px * side * 9), (int) (ez + pz * side * 9));
                }
                // a far backfield patch behind the base: safe but slow to saturate
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                        (int) (x0 - dx / len * 42), (int) (z0 - dz / len * 42));
            }
        }
        if (positions.size() > 1) {
            for (int i = 0; i < 4; i++) {   // the contested middle
                double a = i * Math.PI / 2 + Math.PI / 4;
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                        (int) (cx + Math.cos(a) * 9), (int) (cz + Math.sin(a) * 9));
            }
            // a wider contested ring between the middle and the bases
            float ringR = 0;
            for (BlockPos p : positions)
                ringR = Math.max(ringR, (float) Math.hypot(p.getX() + 0.5f - cx, p.getZ() + 0.5f - cz));
            if (ringR > 60)
                for (int i = 0; i < positions.size() * 2; i++) {
                    double a = Math.PI * 2 * i / (positions.size() * 2) + Math.PI / positions.size();
                    com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                            (int) (cx + Math.cos(a) * ringR * 0.5f), (int) (cz + Math.sin(a) * ringR * 0.5f));
                }
        }
    }

    static boolean clutter(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty())
            return false;
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS))
            return true;
        Block b = s.getBlock();
        if (b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING || b == Blocks.CACTUS || b == Blocks.SUGAR_CANE
                || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.COCOA || b == Blocks.BEE_NEST
                || b == Blocks.MUSHROOM_STEM || b == Blocks.BROWN_MUSHROOM_BLOCK || b == Blocks.RED_MUSHROOM_BLOCK
                || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM
                || b == Blocks.VINE || b == Blocks.MOSS_CARPET || b == Blocks.AZALEA || b == Blocks.FLOWERING_AZALEA
                || b == Blocks.SNOW || b == Blocks.HANGING_ROOTS || b == Blocks.MANGROVE_ROOTS
                || b == Blocks.PUMPKIN || b == Blocks.MELON || b == Blocks.DEAD_BUSH)
            return true;
        return s.canBeReplaced(); // grass, ferns, tall grass, snow layers...
    }

    /** Clears one column from the top down to the ground. */
    static void column(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int min = Math.max(level.getMinBuildHeight(), top - 40);
        for (int y = top; y >= min; y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.isAir())
                continue;
            if (!clutter(s))
                break; // reached the ground (or a building, water, etc.)
            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** A cleared disc of radius r round (cx, cz). */
    static void disc(ServerLevel level, float cx, float cz, float r) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.ceil(cx + r);
        int z0 = (int) Math.floor(cz - r), z1 = (int) Math.ceil(cz + r);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                float dx = x + .5f - cx, dz = z + .5f - cz;
                if (dx * dx + dz * dz <= r * r)
                    column(level, p, x, z);
            }
        }
    }

    /** A cleared lane w blocks wide from (x0, z0) to (x1, z1). */
    static void lane(ServerLevel level, float x0, float z0, float x1, float z1, float w) {
        float len = (float) Math.hypot(x1 - x0, z1 - z0);
        for (float t = 0; t <= len; t += w * .5f) {
            float k = len < 1e-3f ? 0 : t / len;
            disc(level, x0 + (x1 - x0) * k, z0 + (z1 - z0) * k, w / 2);
        }
    }
}
