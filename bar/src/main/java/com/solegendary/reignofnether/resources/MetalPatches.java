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
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int bx = x + d[0], bz = z + d[1];
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            p.set(bx, top, bz);
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty())
                continue;   // not in rivers or lakes
            // the surface may be a flower or grass; walk down to something solid
            int min = Math.max(level.getMinBuildHeight(), top - 6);
            while (p.getY() > min && !level.getBlockState(p).isSolidRender(level, p))
                p.move(0, -1, 0);
            level.setBlock(p, PATCH_BLOCK.defaultBlockState(), FLAGS);
        }
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
