package dev.beyondtabs.mod;

import dev.beyondtabs.engine.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;

/** One running RTS match in a Minecraft dimension: the engine world, who plays which team, and the building blocks. */
public final class Match {
    public final ServerLevel level; public final LevelTerrain terrain; public final World world;
    public final Map<UUID, Integer> playerTeams = new HashMap<>();
    /** Game mode each player had before entering the RTS view, restored when they leave it. */
    public final Map<UUID, GameType> previousMode = new HashMap<>();
    public final Structures structures;

    public Match(ServerLevel level) {
        this.level = level; this.terrain = new LevelTerrain(level);
        this.world = new World(terrain, level.getSeed());
        this.structures = new Structures(this);
        this.terrain.overrides = structures.originalGround;
    }

    public int teamOf(UUID player) { return playerTeams.getOrDefault(player, -1); }
}
