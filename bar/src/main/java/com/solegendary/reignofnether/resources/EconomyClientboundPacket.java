package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

// syncs one player's BAR flow economy state (metal/energy amounts, storage, income, expense, stall) to clients
// sent ~4 times per second by EconomyServerEvents
public class EconomyClientboundPacket {

    public enum EconomyAction {
        SYNC // replace the economy state of ownerName
    }

    EconomyAction action;
    public String ownerName;
    public int metal;
    public int energy;
    public float metalStorage;
    public float energyStorage;
    public float metalIncome;
    public float energyIncome;
    public float metalExpense;
    public float energyExpense;
    public float stall;
    public float energyConverted; // energy/s drained by converters (part of energyExpense)
    public float metalConverted;  // metal/s made by converters (part of metalIncome)
    public float conversionCapacity; // energy/s the converters could drain at most

    public static void sync(Resources res, EconomyServerEvents.PlayerEconomy eco) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new EconomyClientboundPacket(EconomyAction.SYNC,
            res.ownerName, res.ore, res.wood,
            eco.metalStorage, eco.energyStorage,
            eco.metalIncome + eco.metalConverted, eco.energyIncome,
            eco.metalExpense, eco.energyExpense,
            eco.stall, eco.energyConverted, eco.metalConverted, eco.conversionCapacity
        ));
    }

    public EconomyClientboundPacket(EconomyAction action, String ownerName, int metal, int energy,
                                    float metalStorage, float energyStorage,
                                    float metalIncome, float energyIncome,
                                    float metalExpense, float energyExpense, float stall,
                                    float energyConverted, float metalConverted, float conversionCapacity) {
        this.action = action;
        this.ownerName = ownerName;
        this.metal = metal;
        this.energy = energy;
        this.metalStorage = metalStorage;
        this.energyStorage = energyStorage;
        this.metalIncome = metalIncome;
        this.energyIncome = energyIncome;
        this.metalExpense = metalExpense;
        this.energyExpense = energyExpense;
        this.stall = stall;
        this.energyConverted = energyConverted;
        this.metalConverted = metalConverted;
        this.conversionCapacity = conversionCapacity;
    }

    public EconomyClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(EconomyAction.class);
        this.ownerName = buffer.readUtf();
        this.metal = buffer.readInt();
        this.energy = buffer.readInt();
        this.metalStorage = buffer.readFloat();
        this.energyStorage = buffer.readFloat();
        this.metalIncome = buffer.readFloat();
        this.energyIncome = buffer.readFloat();
        this.metalExpense = buffer.readFloat();
        this.energyExpense = buffer.readFloat();
        this.stall = buffer.readFloat();
        this.energyConverted = buffer.readFloat();
        this.metalConverted = buffer.readFloat();
        this.conversionCapacity = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeUtf(this.ownerName);
        buffer.writeInt(this.metal);
        buffer.writeInt(this.energy);
        buffer.writeFloat(this.metalStorage);
        buffer.writeFloat(this.energyStorage);
        buffer.writeFloat(this.metalIncome);
        buffer.writeFloat(this.energyIncome);
        buffer.writeFloat(this.metalExpense);
        buffer.writeFloat(this.energyExpense);
        buffer.writeFloat(this.stall);
        buffer.writeFloat(this.energyConverted);
        buffer.writeFloat(this.metalConverted);
        buffer.writeFloat(this.conversionCapacity);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (this.action == EconomyAction.SYNC)
                    EconomyClientEvents.sync(this);
                success.set(true);
            });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
