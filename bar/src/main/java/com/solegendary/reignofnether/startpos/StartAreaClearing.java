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

    /**
     * This match's metal richness, rolled at battlefield formation: ~0.6 (lean maps, every patch a war)
     * to ~1.6 (metal everywhere, army sizes explode). Scales patch counts, not patch income.
     */
    public static float mexRichness = 1f;

    public static boolean isEnabled(ServerLevel level) {
        return level != null && level.getGameRules().getBoolean(GameRuleRegistrar.CLEAR_START_AREAS);
    }

    /** Clears a disc round each position and a lane from each position to their centroid (the map centre). */
    public static void clearStartAreas(ServerLevel level, List<BlockPos> positions) {
        if (level == null || positions.isEmpty() || !isEnabled(level))
            return;
        try {
            refreshProtected();
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
        for (BlockPos p : positions)
            stampBasePatches(level, p, cx, cz, rng);
        if (positions.size() > 1) {
            for (int i = 0; i < 6; i++) {   // the contested middle (more mexes, lovish Oct 10)
                double a = i * Math.PI / 3 + Math.PI / 6;
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                        (int) (cx + Math.cos(a) * 9), (int) (cz + Math.sin(a) * 9));
            }
            // a wider contested ring between the middle and the bases
            float ringR = 0;
            for (BlockPos p : positions)
                ringR = Math.max(ringR, (float) Math.hypot(p.getX() + 0.5f - cx, p.getZ() + 0.5f - cz));
            // capturable neutral sites between the bases (lovish, Oct 10)
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.stampFor(level, positions, cx, cz, ringR);
            if (ringR > 60) {
                int ringCount = Math.max(4, Math.round(positions.size() * 3 * mexRichness));
                for (int i = 0; i < ringCount; i++) {
                    double a = Math.PI * 2 * i / ringCount + Math.PI / positions.size();
                    com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                            (int) (cx + Math.cos(a) * ringR * 0.5f), (int) (cz + Math.sin(a) * ringR * 0.5f));
                }
            }
        }
    }

    /** One base's worth of patches: home ring behind/beside it, lane expansion, flanks, backfield. */
    static void stampBasePatches(ServerLevel level, BlockPos p, float cx, float cz, java.util.Random rng) {
        float x0 = p.getX() + 0.5f, z0 = p.getZ() + 0.5f;
        float dx = cx - x0, dz = cz - z0, len = (float) Math.hypot(dx, dz);
        double toCentre = len < 1e-3f ? 0 : Math.atan2(dz, dx);
        int homePatches = mexRichness >= 1.3f ? 6 : 5;   // more mexes (lovish Oct 10); rich maps get a 6th
        for (int k = 0; k < homePatches; k++) {   // home ring, symmetric about the lane
            double a = toCentre + Math.PI + (k - (homePatches - 1) / 2.0) * (Math.PI / 2.2);
            com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                    (int) (x0 + Math.cos(a) * 18), (int) (z0 + Math.sin(a) * 18));
        }
        if (len > 40) {
            // near expansion on the lane, worth walking out for
            float k = Math.min(0.35f, 70f / Math.max(1f, len));
            com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) (x0 + dx * k), (int) (z0 + dz * k));
            // a second, farther lane patch halfway to the middle
            if (len > 80)
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) (x0 + dx * 0.5f), (int) (z0 + dz * 0.5f));
            // flank expansions: off to each side, farther out, richer (2 patches) on one random side -
            // bases that want them have to stretch, and denying them actually hurts
            float px = -dz / len, pz = dx / len;
            int richSide = rng.nextBoolean() ? 1 : -1;
            for (int side = -1; side <= 1; side += 2) {
                float ex = x0 + dx * 0.5f + px * side * len * 0.55f;
                float ez = z0 + dz * 0.5f + pz * side * len * 0.55f;
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) ex, (int) ez);
                // both flanks are pairs now; the rich side gets a third
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) (ex + px * side * 9), (int) (ez + pz * side * 9));
                if (side == richSide)
                    com.solegendary.reignofnether.resources.MetalPatches.stamp(level, (int) (ex + dx / len * 9), (int) (ez + dz / len * 9));
            }
            // a far backfield patch behind the base: safe but slow to saturate (lean maps may not get one)
            com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                    (int) (x0 - dx / len * 42), (int) (z0 - dz / len * 42));
            if (mexRichness >= 0.8f || rng.nextBoolean())   // and a second one off to the side
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                        (int) (x0 - dx / len * 38 - dz / len * 14), (int) (z0 - dz / len * 38 + dx / len * 14));
            // metal-everywhere maps scatter bonus patches off the lane
            if (mexRichness >= 1.15f) {
                double a = toCentre + (rng.nextBoolean() ? 1 : -1) * (Math.PI / 3);
                double r2 = len * (0.3 + rng.nextFloat() * 0.3);
                com.solegendary.reignofnether.resources.MetalPatches.stamp(level,
                        (int) (x0 + Math.cos(a) * r2), (int) (z0 + Math.sin(a) * r2));
            }
        }
    }

    /** A base joining an already-formed battlefield: clear its ground, cut its lane, stamp its patches. */
    public static void addLateBase(ServerLevel level, BlockPos p, float cx, float cz) {
        if (level == null || !isEnabled(level))
            return;
        try {
            refreshProtected();
            float x0 = p.getX() + 0.5f, z0 = p.getZ() + 0.5f;
            disc(level, x0, z0, BASE_RADIUS);
            float dx = cx - x0, dz = cz - z0;
            float len = (float) Math.hypot(dx, dz);
            if (len > MAX_LANE_LENGTH) {
                dx *= MAX_LANE_LENGTH / len;
                dz *= MAX_LANE_LENGTH / len;
            }
            if (len > 1)
                lane(level, x0, z0, x0 + dx, z0 + dz, LANE_WIDTH);
            stampBasePatches(level, p, cx, cz, new java.util.Random(p.asLong()));
            com.solegendary.reignofnether.resources.MetalPatches.syncToClients(level);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[StartAreaClearing] failed to add late base", e);
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

    /**
     * Buildings standing when the battlefield forms (capitol foundations laid by a readied start) must survive the
     * clearing: their roofs are logs and leaves to the clutter test, and losing them destroyed the capitol -
     * "lost all their buildings" five seconds into the match. Bounding boxes, one block of margin, refreshed
     * whenever a clearing pass starts.
     */
    private static final java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> PROTECTED = new java.util.ArrayList<>();

    static void refreshProtected() {
        PROTECTED.clear();
        for (com.solegendary.reignofnether.building.BuildingPlacement b
                : new java.util.ArrayList<>(com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings())) {
            if (b.minCorner == null || b.maxCorner == null)
                continue;
            PROTECTED.add(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                b.minCorner.getX() - 1, b.minCorner.getY() - 2, b.minCorner.getZ() - 1,
                b.maxCorner.getX() + 1, b.maxCorner.getY() + 1, b.maxCorner.getZ() + 1));
        }
    }

    public static boolean isProtected(int x, int y, int z) {
        for (net.minecraft.world.level.levelgen.structure.BoundingBox box : PROTECTED)
            if (box.isInside(x, y, z))
                return true;
        return false;
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
            if (isProtected(x, y, z))
                continue;   // part of a standing building (logs/leaves in a roof are not clutter)
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
