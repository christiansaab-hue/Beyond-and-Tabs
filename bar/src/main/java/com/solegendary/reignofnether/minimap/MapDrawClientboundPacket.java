package com.solegendary.reignofnether.minimap;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Relays a map drawing (line or labelled ping) from a player to themselves and their allies. */
public class MapDrawClientboundPacket {
    private final String sender;
    private final byte kind;
    private final int[] xs;
    private final int[] zs;
    private final String label;

    public MapDrawClientboundPacket(String sender, byte kind, int[] xs, int[] zs, String label) {
        this.sender = sender;
        this.kind = kind;
        this.xs = xs;
        this.zs = zs;
        this.label = label;
    }

    public MapDrawClientboundPacket(FriendlyByteBuf buffer) {
        this.sender = buffer.readUtf();
        this.kind = buffer.readByte();
        int n = Math.max(0, Math.min(MapDrawRules.MAX_POINTS, buffer.readVarInt()));
        this.xs = new int[n];
        this.zs = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = buffer.readInt();
            zs[i] = buffer.readInt();
        }
        this.label = buffer.readUtf(MapDrawRules.MAX_LABEL_CHARS * 4);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(sender);
        buffer.writeByte(kind);
        buffer.writeVarInt(xs.length);
        for (int i = 0; i < xs.length; i++) {
            buffer.writeInt(xs[i]);
            buffer.writeInt(zs[i]);
        }
        buffer.writeUtf(label, MapDrawRules.MAX_LABEL_CHARS * 4);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            MapDrawClientEvents.receive(sender, kind, xs, zs, label)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
