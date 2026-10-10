package com.solegendary.reignofnether.bot;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the skirmish bots: "/bot add <faction> [easy|medium|hard]" drops an opponent on the caller's position (stand
 * where its base should be, ideally a map start position), then each bot thinks once a second. Brains are rebuilt
 * from world state for any saved bot after a reload, so bots survive restarts. "/bot remove" defeats them all.
 */
public class BotServerEvents {

    public static final Map<String, BotPlayer> brains = new ConcurrentHashMap<>();
    /** The most recent exception each bot's think step threw (logged and swallowed in play; game tests read it). */
    public static final Map<String, Throwable> thinkFailures = new ConcurrentHashMap<>();
    static int botNumber = 1;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        long gameTime = level.getGameTime();
        synchronized (PlayerServerEvents.rtsPlayers) {
            for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers) {
                if (!rtsPlayer.isBot())
                    continue;
                BotPlayer brain = brains.get(rtsPlayer.name);
                if (brain == null) {   // a bot loaded from a save: rebuild its brain where its things are
                    BlockPos home = findHome(level, rtsPlayer.name);
                    if (home == null)
                        continue;
                    // (a faction with no bot kit gets a brain whose think() does nothing, rather than a guessed kit)
                    brain = new BotPlayer(rtsPlayer.name, rtsPlayer.faction, BotPlayer.Difficulty.MEDIUM, home);
                    brains.put(rtsPlayer.name, brain);
                }
                if ((gameTime + brain.name.hashCode() & 0xffff) % 20 == 0) {
                    try {
                        brain.think(level, gameTime);
                    } catch (Exception e) {
                        thinkFailures.put(brain.name, e);
                        com.solegendary.reignofnether.ReignOfNether.LOGGER.error("[Bot] {} think failed", brain.name, e);
                    }
                }
            }
        }
    }

    static BlockPos findHome(ServerLevel level, String name) {
        for (var bp : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings())
            if (name.equals(bp.ownerName) && !bp.isDestroyedServerside)
                return bp.originPos;
        for (var le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
            if (le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && name.equals(u.getOwnerName()))
                return le.blockPosition();
        return null;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        brains.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bot")
            .then(Commands.literal("add")
                .then(Commands.argument("faction", StringArgumentType.word())
                    .executes(c -> addBot(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "faction"), "medium"))
                    .then(Commands.argument("difficulty", StringArgumentType.word())
                        .executes(c -> addBot(c.getSource().getPlayerOrException(),
                            StringArgumentType.getString(c, "faction"), StringArgumentType.getString(c, "difficulty"))))))
            .then(Commands.literal("remove").executes(c -> removeBots(c.getSource().getPlayerOrException()))));
    }

    /** Skirmish lobby entry point: faction/difficulty by name, spawn ring in blocks from the caller. */
    public static int addBot(ServerPlayer caller, String factionName, String difficultyName, int minR, int maxR) {
        return addBotImpl(caller, factionName, difficultyName, minR, maxR, 0);
    }

    /** As above with a team colour (PlayerPalette map colour id; 0 = first free colour). */
    public static int addBot(ServerPlayer caller, String factionName, String difficultyName, int minR, int maxR, int colorMapId) {
        return addBotImpl(caller, factionName, difficultyName, minR, maxR, colorMapId);
    }

    /** The name of the bot created by the most recent addBot call (the lobby uses it to form teams). */
    public static String lastBotName = null;

    static int addBot(ServerPlayer caller, String factionName, String difficultyName) {
        boolean far = difficultyName.equalsIgnoreCase("far");
        return addBotImpl(caller, factionName, difficultyName, far ? 300 : 180, far ? 420 : 260, 0);
    }

    static int addBotImpl(ServerPlayer caller, String factionName, String difficultyName, int minR, int maxR, int colorMapId) {
        Faction faction = switch (factionName.toLowerCase()) {
            case "monsters", "monster" -> Factions.MONSTERS;
            case "villagers", "villager" -> Factions.VILLAGERS;
            case "piglins", "piglin" -> Factions.PIGLINS;
            default -> null;
        };
        if (faction == null || BotPlayer.kitFor(faction) == null) {   // (kitFor: never hand a bot a faction it has no kit for)
            caller.sendSystemMessage(Component.literal("Bots can play villagers, monsters or piglins."));
            return 0;
        }
        BotPlayer.Difficulty difficulty = switch (difficultyName.toLowerCase()) {
            case "easy" -> BotPlayer.Difficulty.EASY;
            case "hard" -> BotPlayer.Difficulty.HARD;
            default -> BotPlayer.Difficulty.MEDIUM;   // "medium" and "far" both land here
        };
        String name = "Bot" + botNumber++ + " (" + difficulty.name().toLowerCase() + ")";
        lastBotName = name;
        Vec3 pos = findBotStart(caller, minR, maxR);
        PlayerServerEvents.startRTSBot(name, pos, faction, colorMapId);
        brains.put(name, new BotPlayer(name, faction, difficulty, BlockPos.containing(pos)));
        caller.sendSystemMessage(Component.literal(name + " joined as " + factionName + " about "
                + (int) pos.distanceTo(caller.position()) + " blocks away - scout it before it scouts you."));
        return 1;
    }

    /**
     * A base site for a new bot: far from the caller and from every existing RTS player, on dry, fairly flat ground.
     * Searches rings of 180-260 blocks in 24 directions and keeps the best-scoring spot.
     */
    static Vec3 findBotStart(ServerPlayer caller) { return findBotStart(caller, 180, 260); }

    static Vec3 findBotStart(ServerPlayer caller, int minR, int maxR) {
        ServerLevel level = caller.serverLevel();
        Vec3 from = caller.position();
        Vec3 best = from.add(minR, 0, 0);
        double bestScore = -1e18;
        for (int r = minR; r <= maxR; r += 40) {
            for (int k = 0; k < 24; k++) {
                double a = Math.PI * 2 * k / 24;
                int x = (int) (from.x + Math.cos(a) * r);
                int z = (int) (from.z + Math.sin(a) * r);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                if (!level.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty())
                    continue;   // water
                int minY = y, maxY = y;
                boolean wet = false;
                for (int dx = -12; dx <= 12; dx += 12)
                    for (int dz = -12; dz <= 12; dz += 12) {
                        int yy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x + dx, z + dz);
                        minY = Math.min(minY, yy);
                        maxY = Math.max(maxY, yy);
                        if (!level.getBlockState(new BlockPos(x + dx, yy - 1, z + dz)).getFluidState().isEmpty())
                            wet = true;
                    }
                if (wet || maxY - minY > 10)
                    continue;
                double nearest = 1e9;
                synchronized (PlayerServerEvents.rtsPlayers) {
                    for (RTSPlayer other : PlayerServerEvents.rtsPlayers) {
                        BlockPos home = findHome(level, other.name);
                        if (home != null)
                            nearest = Math.min(nearest, home.distSqr(new BlockPos(x, y, z)));
                    }
                }
                double score = Math.min(nearest, 300 * 300) - (maxY - minY) * 500;
                if (score > bestScore) {
                    bestScore = score;
                    best = new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return best;
    }

    static int removeBots(ServerPlayer caller) {
        int n = 0;
        synchronized (PlayerServerEvents.rtsPlayers) {
            for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers.stream().toList())
                if (rtsPlayer.isBot()) {
                    PlayerServerEvents.defeat(rtsPlayer.name, "server.reignofnether.defeat_bot_removed");
                    brains.remove(rtsPlayer.name);
                    n++;
                }
        }
        caller.sendSystemMessage(Component.literal("Removed " + n + " bot(s)."));
        return n;
    }
}
