package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The skirmish lobby's "Start Battle": one packet carrying every choice. The server starts the sender as an RTS
 * player with a readied start (their colour, capitol foundations already laid, commander and workers beside it),
 * pins the arena size and metal richness for the battlefield that forms a few seconds later, and calls in each
 * configured bot at the chosen spawn distance.
 *
 * Faction codes: 0 Kingdom (villagers), 1 Fallen (monsters), 2 Gilded Legion (piglins), 3 random.
 * Arena: 0 small, 1 medium, 2 large, 3 huge, 4 random.  Metal: 0 lean, 1 normal, 2 rich, 3 random.
 * Spawn distance: 0 close, 1 normal, 2 far.  Difficulty: 0 easy, 1 medium, 2 hard.
 */
public class SkirmishServerboundPacket {

    /** @param colorMapId PlayerPalette map colour id, or 0 for "first free colour" */
    public record BotSpec(int faction, int difficulty, int colorMapId) { }

    private final int faction;
    private final int colorMapId;
    private final List<BotSpec> bots;
    private final int arena;
    private final int metal;
    private final int spawnDistance;

    public SkirmishServerboundPacket(int faction, int colorMapId, List<BotSpec> bots, int arena, int metal, int spawnDistance) {
        this.faction = faction;
        this.colorMapId = colorMapId;
        this.bots = bots;
        this.arena = arena;
        this.metal = metal;
        this.spawnDistance = spawnDistance;
    }

    public static void send(int faction, int colorMapId, List<BotSpec> bots, int arena, int metal, int spawnDistance) {
        PacketHandler.INSTANCE.sendToServer(new SkirmishServerboundPacket(faction, colorMapId, bots, arena, metal, spawnDistance));
    }

    public SkirmishServerboundPacket(FriendlyByteBuf buf) {
        faction = buf.readByte();
        colorMapId = buf.readInt();
        int n = buf.readByte();
        bots = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            bots.add(new BotSpec(buf.readByte(), buf.readByte(), buf.readInt()));
        arena = buf.readByte();
        metal = buf.readByte();
        spawnDistance = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(faction);
        buf.writeInt(colorMapId);
        buf.writeByte(bots.size());
        for (BotSpec b : bots) {
            buf.writeByte(b.faction());
            buf.writeByte(b.difficulty());
            buf.writeInt(b.colorMapId());
        }
        buf.writeByte(arena);
        buf.writeByte(metal);
        buf.writeByte(spawnDistance);
    }

    static Faction factionOf(int code, Random rng) {
        return switch (code) {
            case 0 -> Factions.VILLAGERS;
            case 1 -> Factions.MONSTERS;
            case 2 -> Factions.PIGLINS;
            default -> List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS).get(rng.nextInt(3));
        };
    }

    static String factionName(Faction f) {
        if (f.equals(Factions.MONSTERS)) return "monsters";
        if (f.equals(Factions.PIGLINS)) return "piglins";
        return "villagers";
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;
            Random rng = new Random();
            Faction mine = factionOf(faction, rng);

            // arena + metal for the battlefield that forms once everyone is in
            BattlefieldSetup.arenaRadiusOverride = switch (arena) {
                case 0 -> 230;
                case 1 -> 320;
                case 2 -> 420;
                case 3 -> 560;
                default -> -1;
            };
            BattlefieldSetup.richnessOverride = switch (metal) {
                case 0 -> 0.65f;
                case 1 -> 1.0f;
                case 2 -> 1.5f;
                default -> -1f;
            };
            int minR = switch (spawnDistance) { case 0 -> 140; case 2 -> 300; default -> 180; };
            int maxR = switch (spawnDistance) { case 0 -> 200; case 2 -> 420; default -> 260; };

            // the player: readied start (colorMapId != 0) lays the capitol foundations and seats the commander.
            // In the RTS view the player entity floats at camera height, so snap the start to the ground.
            int gx = player.getBlockX(), gz = player.getBlockZ();
            // the start position is the GROUND block (building origins put blocks at origin.y+1, and the start
            // block of the classic flow sits in the ground too); the surface heightmap is one above that
            int gy = com.solegendary.reignofnether.resources.MetalPatches.solidTop(player.serverLevel(), gx, gz);
            Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
            PlayerServerEvents.startRTS(player.getId(), pos, mine, colorMapId != 0 ? colorMapId : 1);

            int added = 0;
            for (BotSpec b : bots) {
                String diff = switch (b.difficulty()) { case 0 -> "easy"; case 2 -> "hard"; default -> "medium"; };
                added += BotServerEvents.addBot(player, factionName(factionOf(b.faction(), rng)), diff, minR, maxR, b.colorMapId());
            }
            ReignOfNether.LOGGER.info("[Skirmish] {} starts as {} with {} bot(s); arena {}, metal {}, spawn {}",
                player.getName().getString(), factionName(mine), added, arena, metal, spawnDistance);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
