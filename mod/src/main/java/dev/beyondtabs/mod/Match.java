package dev.beyondtabs.mod;

import dev.beyondtabs.engine.World;
import net.minecraft.server.level.ServerLevel;

/** One running RTS match in a Minecraft dimension. */
public final class Match {
    public final ServerLevel level; public final LevelTerrain terrain; public final World world;
    public Match(ServerLevel level) {
        this.level = level; this.terrain = new LevelTerrain(level);
        this.world = new World(terrain, level.getSeed());
    }
}
