package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.unit.UnitQueueLinesClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// the owner's units' current + shift-queued orders (see UnitQueueSync); sent only when they change
public class UnitQueueClientboundPacket {

    // blocks of [unitId, n, n * (type, x, y, z, entityId)]
    private final int[] data;

    private UnitQueueClientboundPacket(int[] data) {
        this.data = data;
    }

    public static UnitQueueClientboundPacket of(int[] data) {
        return new UnitQueueClientboundPacket(data);
    }

    // a static decoder rather than a FriendlyByteBuf constructor (as CapturePointsClientboundPacket does)
    public static UnitQueueClientboundPacket decode(FriendlyByteBuf buffer) {
        return new UnitQueueClientboundPacket(buffer.readVarIntArray());
    }

    public void encode(FriendlyByteBuf buffer) {
        // varints: ids, counts and coordinates are mostly small; entity id -1 costs 5 bytes, still fine
        buffer.writeVarIntArray(this.data);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnitQueueLinesClient.apply(this.data)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
