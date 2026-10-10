
package com.solegendary.reignofnether.attackwarnings;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AttackWarningClientboundPacket {

    private final String attackedPlayerName;
    private final BlockPos attackPos;
    private final boolean isCommander; // lets allies ping their teammate's commander in orange on the minimap

    public static void sendWarning(String attackedPlayerName, BlockPos attackPos, boolean isCommander) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
            new AttackWarningClientboundPacket(
                attackedPlayerName,
                attackPos,
                isCommander
            ));
    }

    // packet-handler functions
    public AttackWarningClientboundPacket(
        String attackedPlayerName,
        BlockPos attackPos,
        boolean isCommander
    ) {
        this.attackedPlayerName = attackedPlayerName;
        this.attackPos = attackPos;
        this.isCommander = isCommander;
    }

    public AttackWarningClientboundPacket(FriendlyByteBuf buffer) {
        this.attackedPlayerName = buffer.readUtf();
        this.attackPos = buffer.readBlockPos();
        this.isCommander = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.attackedPlayerName);
        buffer.writeBlockPos(this.attackPos);
        buffer.writeBoolean(this.isCommander);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            AttackWarningClientEvents.checkAndTriggerAttackWarning(attackedPlayerName, attackPos, isCommander);
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
