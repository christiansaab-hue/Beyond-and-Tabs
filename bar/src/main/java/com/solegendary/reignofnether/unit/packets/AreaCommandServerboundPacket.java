package com.solegendary.reignofnether.unit.packets;

import com.solegendary.reignofnether.unit.AreaCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// area reclaim / repair / attack circle dragged by the client (see AreaCommandClientEvents); the server picks the targets
public class AreaCommandServerboundPacket {

    private final int mode;
    private final BlockPos centre;
    private final int radius;
    private final int[] unitIds;
    private final boolean shift;

    public AreaCommandServerboundPacket(int mode, BlockPos centre, int radius, int[] unitIds, boolean shift) {
        this.mode = mode;
        this.centre = centre;
        this.radius = radius;
        this.unitIds = unitIds;
        this.shift = shift;
    }

    public AreaCommandServerboundPacket(FriendlyByteBuf buffer) {
        this.mode = buffer.readByte();
        this.centre = buffer.readBlockPos();
        this.radius = buffer.readVarInt();
        this.unitIds = buffer.readVarIntArray(512);
        this.shift = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(this.mode);
        buffer.writeBlockPos(this.centre);
        buffer.writeVarInt(this.radius);
        buffer.writeVarIntArray(this.unitIds);
        buffer.writeBoolean(this.shift);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.level() instanceof ServerLevel))
                return;
            // the owner is always the sender: AreaCommands only moves units that player may control
            ServerLevel level = player.getServer() != null ? player.getServer().overworld() : (ServerLevel) player.level();
            AreaCommands.issue(level, player.getName().getString(), mode, centre, radius, unitIds, shift);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
