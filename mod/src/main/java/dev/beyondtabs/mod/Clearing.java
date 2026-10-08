package dev.beyondtabs.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Tidy fields, wild edges: before a match the bases, the metal spots and the lanes between the bases are cleared of
 * trees, bushes, tall grass and flowers (no drops, no block updates). Forests everywhere else stay, so the world
 * still looks like Minecraft while the places you build and fight on read cleanly.
 */
final class Clearing {
    private Clearing() { }

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static boolean clutter(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)) return true;
        Block b = s.getBlock();
        if (b == Blocks.BAMBOO || b == Blocks.CACTUS || b == Blocks.SUGAR_CANE || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.COCOA
                || b == Blocks.BEE_NEST || b == Blocks.MUSHROOM_STEM || b == Blocks.BROWN_MUSHROOM_BLOCK || b == Blocks.RED_MUSHROOM_BLOCK
                || b == Blocks.VINE || b == Blocks.MOSS_CARPET || b == Blocks.AZALEA || b == Blocks.FLOWERING_AZALEA
                || b == Blocks.POINTED_DRIPSTONE || b == Blocks.BIG_DRIPLEAF || b == Blocks.BIG_DRIPLEAF_STEM || b == Blocks.SMALL_DRIPLEAF
                || b == Blocks.HANGING_ROOTS || b == Blocks.MANGROVE_ROOTS || b == Blocks.PUMPKIN || b == Blocks.MELON) return true;
        return s.canBeReplaced();   // grass, ferns, dead bushes, snow layers...
    }

    /** Clears one column from the top down to the ground. */
    static void column(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), min = Math.max(level.getMinBuildHeight(), top - 40);
        for (int y = top; y >= min; y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.isAir()) continue;
            if (!clutter(s)) break;   // reached the ground
            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** A cleared disc of radius r round (cx, cz). */
    static void disc(ServerLevel level, float cx, float cz, float r) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.ceil(cx + r), z0 = (int) Math.floor(cz - r), z1 = (int) Math.ceil(cz + r);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            float dx = x + .5f - cx, dz = z + .5f - cz;
            if (dx * dx + dz * dz <= r * r) column(level, p, x, z);
        }
    }

    /** A cleared lane `w` blocks wide from (x0, z0) to (x1, z1). */
    static void lane(ServerLevel level, float x0, float z0, float x1, float z1, float w) {
        float len = (float) Math.hypot(x1 - x0, z1 - z0);
        for (float t = 0; t <= len; t += w * .5f) {
            float k = len < 1e-3f ? 0 : t / len;
            disc(level, x0 + (x1 - x0) * k, z0 + (z1 - z0) * k, w / 2);
        }
    }
}
