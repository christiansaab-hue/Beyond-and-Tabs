package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Buildings are drawn as low-poly models on the client (see client.BuildingModels); the server only levels the site
 * once when a building is placed: fills dips with dirt (grass on top), clears plants and terrain above the pad, and
 * remembers the original ground so the physics keeps using it.
 */
public final class Structures {
    private final Match match;
    private final Set<Integer> prepared = new HashSet<>();
    /** Original ground height of every column a building covers (the physics keeps using it). */
    final Map<Long, Float> originalGround = new HashMap<>();

    Structures(Match match) { this.match = match; }

    /** Called every few server ticks; levels new sites. */
    public void update() {
        boolean changed = false;
        for (Building b : match.world.buildings) {
            if (!b.alive || prepared.contains(b.id)) continue;
            prepareSite(b); prepared.add(b.id); changed = true;
        }
        if (prepared.size() > match.world.buildings.size() + 256) {
            Set<Integer> live = new HashSet<>(); for (Building b : match.world.buildings) live.add(b.id);
            prepared.retainAll(live);
        }
        if (changed) match.terrain.invalidate();
    }

    void prepareSite(Building b) {
        int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
        int baseY = (int) Math.floor(match.terrain.groundY(b.x, b.z));
        BlockState dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= b.fw; x++) for (int z = -1; z <= b.fh; z++) {
            int bx = x0 + x, bz = z0 + z;
            boolean inside = x >= 0 && z >= 0 && x < b.fw && z < b.fh;
            float g = match.terrain.groundY(bx + .5f, bz + .5f);
            if (inside) originalGround.put(((long) bx << 32) ^ (bz & 0xffffffffL), g);
            if (!inside && Math.abs(g - baseY) > 2) continue;   // the one-block margin only smooths small steps
            for (int y = (int) g; y < baseY; y++) match.level.setBlock(new BlockPos(bx, y, bz), y == baseY - 1 ? grass : dirt, 3);
            for (int y = baseY; y < baseY + 16; y++) {
                BlockPos p = new BlockPos(bx, y, bz);
                if (!match.level.getBlockState(p).isAir()) match.level.setBlock(p, air, 3);
            }
        }
    }
}
