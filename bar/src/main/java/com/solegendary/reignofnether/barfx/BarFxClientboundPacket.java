package com.solegendary.reignofnether.barfx;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One tick's worth of battle effect events for one player. */
public class BarFxClientboundPacket {
    private static final int MAX_EVENTS = 512;

    final List<BarFx.Event> events;

    public BarFxClientboundPacket(List<BarFx.Event> events) {
        this.events = events;
    }

    public BarFxClientboundPacket(FriendlyByteBuf buffer) {
        int n = Math.min(buffer.readVarInt(), MAX_EVENTS);
        this.events = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byte type = buffer.readByte(), kind = buffer.readByte(), flags = buffer.readByte();
            float x = buffer.readFloat(), y = buffer.readFloat(), z = buffer.readFloat();
            float a = buffer.readFloat(), b = buffer.readFloat(), c = buffer.readFloat();
            events.add(new BarFx.Event(type, kind, flags, x, y, z, a, b, c));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        int n = Math.min(events.size(), MAX_EVENTS);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            BarFx.Event e = events.get(i);
            buffer.writeByte(e.type);
            buffer.writeByte(e.kind);
            buffer.writeByte(e.flags);
            buffer.writeFloat(e.x);
            buffer.writeFloat(e.y);
            buffer.writeFloat(e.z);
            buffer.writeFloat(e.a);
            buffer.writeFloat(e.b);
            buffer.writeFloat(e.c);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BarFxClient.ingest(events))
        );
        ctx.get().setPacketHandled(true);
        return true;
    }
}
