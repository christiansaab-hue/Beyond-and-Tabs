package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Minecraft blocks as the physics ground. Per column: the top solid block (leaves ignored), and if water sits on it,
 * the riverbed below plus the water depth. Cached; cleared periodically and whenever blocks change.
 */
public final class LevelTerrain implements Terrain {
    private final ServerLevel level;
    private static final int SIZE = 1 << 14, MASK = SIZE - 1;
    private final long[] keys = new long[SIZE]; private final float[] ground = new float[SIZE], water = new float[SIZE];
    private final boolean[] used = new boolean[SIZE];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public LevelTerrain(ServerLevel level) { this.level = level; }

    public void invalidate() { java.util.Arrays.fill(used, false); }

    private int slot(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        long k = ((long) bx << 32) ^ (bz & 0xffffffffL);
        int h = (int) (k ^ (k >>> 29) ^ (k >>> 13)) & MASK;
        if (used[h] && keys[h] == k) return h;
        float top, depth = 0;
        if (!level.hasChunk(bx >> 4, bz >> 4)) top = level.getSeaLevel();
        else {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            int surface = y, minY = level.getMinBuildHeight();
            while (y > minY && !level.getFluidState(pos.set(bx, y - 1, bz)).isEmpty()) y--;   // walk down through water
            top = y; depth = surface - y;
        }
        keys[h] = k; ground[h] = top; water[h] = depth; used[h] = true;
        return h;
    }

    @Override public float groundY(float x, float z) { return ground[slot(x, z)]; }
    @Override public float waterDepth(float x, float z) { return water[slot(x, z)]; }
}
