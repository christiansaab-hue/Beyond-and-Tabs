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

    static final Map<String, BotPlayer> brains = new ConcurrentHashMap<>();
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
                    brain = new BotPlayer(rtsPlayer.name, rtsPlayer.faction, BotPlayer.Difficulty.MEDIUM, home);
                    brains.put(rtsPlayer.name, brain);
                }
                if ((gameTime + brain.name.hashCode() & 0xffff) % 20 == 0) {
                    try {
                        brain.think(level, gameTime);
                    } catch (Exception e) {
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

    static int addBot(ServerPlayer caller, String factionName, String difficultyName) {
        Faction faction = switch (factionName.toLowerCase()) {
            case "monsters", "monster" -> Factions.MONSTERS;
            case "villagers", "villager" -> Factions.VILLAGERS;
            default -> null;
        };
        if (faction == null) {
            caller.sendSystemMessage(Component.literal("Bots can play villagers or monsters (piglins need nether ground)."));
            return 0;
        }
        BotPlayer.Difficulty difficulty = switch (difficultyName.toLowerCase()) {
            case "easy" -> BotPlayer.Difficulty.EASY;
            case "hard" -> BotPlayer.Difficulty.HARD;
            default -> BotPlayer.Difficulty.MEDIUM;
        };
        String name = "Bot" + botNumber++ + " (" + difficultyName.toLowerCase() + ")";
        Vec3 pos = caller.position();
        PlayerServerEvents.startRTSBot(name, pos, faction);
        brains.put(name, new BotPlayer(name, faction, difficulty, BlockPos.containing(pos)));
        caller.sendSystemMessage(Component.literal(name + " joined as " + factionName + " at your position - give it room and watch it build."));
        return 1;
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
