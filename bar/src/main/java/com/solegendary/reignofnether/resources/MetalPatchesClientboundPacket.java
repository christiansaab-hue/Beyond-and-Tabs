package com.solegendary.reignofnether.resources;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Syncs the full list of metal patch centres to clients, so the minimap can show every mex spot on the map. */
public class MetalPatchesClientboundPacket {

    private final List<BlockPos> patches;

    public MetalPatchesClientboundPacket(List<BlockPos> patches) {
        this.patches = patches;
    }

    public MetalPatchesClientboundPacket(FriendlyByteBuf buffer) {
        int count = buffer.readInt();
        this.patches = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            this.patches.add(buffer.readBlockPos());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(patches.size());
        for (BlockPos pos : patches)
            buffer.writeBlockPos(pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                MetalPatchesClient.sync(patches);
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
