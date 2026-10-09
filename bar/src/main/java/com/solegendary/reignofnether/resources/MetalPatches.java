package com.solegendary.reignofnether.resources;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * BAR-style metal patches: plus-shaped stamps of raw iron set flush into the ground at match start (a ring at every
 * base, a forward patch along each lane, a contested cluster in the middle). Metal extractors may only be placed on
 * a patch, so expanding across the map is how a player grows their metal income. The patch is real blocks: visible,
 * saved with the world, and needing no extra sync.
 */
public final class MetalPatches {
    private MetalPatches() { }

    public static final Block PATCH_BLOCK = Blocks.RAW_IRON_BLOCK;
    /** Patch centres (per dimension); bots read these to know where to expand, clients get them for the minimap. */
    private static final java.util.Map<String, java.util.List<BlockPos>> STAMPED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> LOADED_DIMS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static java.util.List<BlockPos> getPatches(ServerLevel level) {
        ensureLoaded(level);
        return STAMPED.getOrDefault(level.dimension().location().toString(), java.util.List.of());
    }

    /** First access after a world (re)load: pull the stamped patches back out of the save. */
    private static void ensureLoaded(ServerLevel level) {
        String dim = level.dimension().location().toString();
        if (!LOADED_DIMS.add(dim))
            return;
        java.util.List<BlockPos> list = STAMPED.computeIfAbsent(dim,
            k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
        for (MetalPatchesSaveData.Entry entry : MetalPatchesSaveData.getInstance(level).entries)
            if (entry.dimension().equals(dim) && !list.contains(entry.pos()))
                list.add(entry.pos());
    }

    /** Match reset: dig the patches back out of the ground, forget them, and clear every minimap. */
    public static void clearAll(ServerLevel level) {
        ensureLoaded(level);
        String dim = level.dimension().location().toString();
        java.util.List<BlockPos> list = STAMPED.getOrDefault(dim, java.util.List.of());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (BlockPos centre : new java.util.ArrayList<>(list)) {
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int bx = centre.getX() + d[0], bz = centre.getZ() + d[1];
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
                for (int y = top + 1; y >= top - 6; y--) {
                    p.set(bx, y, bz);
                    if (level.getBlockState(p).is(PATCH_BLOCK))
                        level.setBlock(p, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), FLAGS);
                }
            }
        }
        STAMPED.remove(dim);
        LOADED_DIMS.add(dim);   // stays "loaded": now deliberately empty
        MetalPatchesSaveData saveData = MetalPatchesSaveData.getInstance(level);
        saveData.entries.removeIf(e -> e.dimension().equals(dim));
        saveData.setDirty();
        syncToClients(level);
    }

    /** Sends the full patch list of this level to every client (minimap mex spots). */
    public static void syncToClients(ServerLevel level) {
        com.solegendary.reignofnether.registrars.PacketHandler.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.ALL.noArg(),
            new MetalPatchesClientboundPacket(new java.util.ArrayList<>(getPatches(level))));
    }

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Stamps one plus-shaped patch flush into the terrain surface at (x, z). Skips water. */
    public static void stamp(ServerLevel level, int x, int z) {
        ensureLoaded(level);
        String dim = level.dimension().location().toString();
        BlockPos centre = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
        STAMPED.computeIfAbsent(dim, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(centre);
        MetalPatchesSaveData saveData = MetalPatchesSaveData.getInstance(level);
        saveData.entries.add(new MetalPatchesSaveData.Entry(dim, centre));
        saveData.setDirty();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // level a 7x7 pad around the patch to the centre's ground height, so the 5x5 extractor always has flat
        // ground to sit on (patches on a slope or among tree roots were silently unplaceable)
        int padY = solidTop(level, x, z);
        if (padY > level.getMinBuildHeight()) {
            BlockState fill = level.getBlockState(p.set(x, padY, z));
            if (fill.isAir() || !fill.getFluidState().isEmpty() || fill.is(PATCH_BLOCK))
                fill = Blocks.DIRT.defaultBlockState();
            BlockState cap = level.getBiome(p).is(net.minecraft.tags.BiomeTags.IS_NETHER)
                ? Blocks.NETHERRACK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    int bx = x + dx, bz = z + dz;
                    if (!level.getBlockState(p.set(bx, padY, bz)).getFluidState().isEmpty()
                            || !level.getBlockState(p.set(bx, padY + 1, bz)).getFluidState().isEmpty())
                        continue;   // leave rivers and lakes alone
                    // clear everything above the pad (grass, flowers, logs, leaves - up to 8 high for trees)
                    for (int y = padY + 1; y <= padY + 8; y++)
                        if (!level.getBlockState(p.set(bx, y, bz)).isAir())
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                    // raise low ground up to the pad
                    for (int y = padY - 4; y < padY; y++)
                        if (!level.getBlockState(p.set(bx, y, bz)).isSolidRender(level, p))
                            level.setBlock(p, fill, FLAGS);
                    level.setBlock(p.set(bx, padY, bz), cap, FLAGS);
                }
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                level.setBlock(p.set(x + d[0], padY, z + d[1]), PATCH_BLOCK.defaultBlockState(), FLAGS);
        }
    }

    /** The y of the topmost solid, non-fluid block at (x, z), or min build height if there is none. */
    static int solidTop(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int min = Math.max(level.getMinBuildHeight(), top - 12);
        p.set(x, top, z);
        while (p.getY() > min && (!level.getBlockState(p).isSolidRender(level, p)
                || !level.getBlockState(p).getFluidState().isEmpty()))
            p.move(0, -1, 0);
        return p.getY();
    }

    /**
     * Whether a building placed with this origin touches a metal patch: any raw iron block within its rough
     * footprint, at or just below the origin height.
     */
    public static boolean isOnPatch(Level level, BlockPos originPos, int sizeX, int sizeZ) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= sizeX; x++)
            for (int z = -1; z <= sizeZ; z++)
                for (int y = 1; y >= -3; y--) {
                    p.set(originPos.getX() + x, originPos.getY() + y, originPos.getZ() + z);
                    if (level.getBlockState(p).is(PATCH_BLOCK))
                        return true;
                }
        return false;
    }
}
