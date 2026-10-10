package com.solegendary.reignofnether.minimap;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A player drew on the map: either a short line (kind STROKE, a polyline of world XZ points) or a labelled ping
 * (kind LABEL, one point plus up to 24 characters). The server rate-limits it per player and relays it to the sender
 * and their allies only, like {@link MapMarkerServerboundPacket}.
 */
public class MapDrawServerboundPacket {
    private final byte kind;
    private final int[] xs;
    private final int[] zs;
    private final String label;

    public static void sendStroke(int[] xs, int[] zs) {
        PacketHandler.INSTANCE.sendToServer(new MapDrawServerboundPacket(MapDrawRules.KIND_STROKE, xs, zs, ""));
    }

    public static void sendLabel(int x, int z, String label) {
        PacketHandler.INSTANCE.sendToServer(new MapDrawServerboundPacket(MapDrawRules.KIND_LABEL,
            new int[] { x }, new int[] { z }, label));
    }

    public MapDrawServerboundPacket(byte kind, int[] xs, int[] zs, String label) {
        this.kind = kind;
        this.xs = xs;
        this.zs = zs;
        this.label = label;
    }

    public MapDrawServerboundPacket(FriendlyByteBuf buffer) {
        this.kind = buffer.readByte();
        int n = buffer.readVarInt();
        if (n < 0 || n > MapDrawRules.MAX_POINTS)   // only a modified client sends this; refuse before allocating
            throw new DecoderException("map drawing with " + n + " points");
        this.xs = new int[n];
        this.zs = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buffer.readInt();
            zs[i] = buffer.readInt();
        }
        this.label = buffer.readUtf(MapDrawRules.MAX_LABEL_CHARS * 4);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(kind);
        buffer.writeVarInt(xs.length);
        for (int i = 0; i < xs.length; i++) {
            buffer.writeInt(xs[i]);
            buffer.writeInt(zs[i]);
        }
        buffer.writeUtf(label, MapDrawRules.MAX_LABEL_CHARS * 4);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getServer() == null)
                return;
            String text = "";
            if (kind == MapDrawRules.KIND_STROKE) {
                if (!MapDrawRules.isValidStroke(xs, zs))
                    return;
            } else if (kind == MapDrawRules.KIND_LABEL) {
                text = MapDrawRules.sanitiseLabel(label);
                if (xs.length != 1 || text.isEmpty())
                    return;
            } else {
                return;
            }
            if (!MapDrawRules.SERVER.tryConsume(player.getUUID(), System.currentTimeMillis()))
                return;   // over 10 drawings in 5 s: drop silently (the client warns its own player)

            MinecraftServer server = player.getServer();
            PlayerList playerList = server.getPlayerList();
            String sender = player.getName().getString();
            List<String> online = new ArrayList<>();
            for (ServerPlayer p : playerList.getPlayers())
                online.add(p.getName().getString());

            MapDrawClientboundPacket out = new MapDrawClientboundPacket(sender, kind, xs, zs, text);
            for (String name : MapDrawRules.recipients(sender, online, AlliancesServerEvents::isAllied)) {
                ServerPlayer target = playerList.getPlayerByName(name);
                if (target == null)
                    continue;
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target), out);
                if (kind == MapDrawRules.KIND_LABEL)   // labels ping like markers; lines stay quiet (they come in bursts)
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target),
                        new SoundClientboundPacket(SoundAction.ALLY, BlockPos.ZERO, "", 1.0f, -1));
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
