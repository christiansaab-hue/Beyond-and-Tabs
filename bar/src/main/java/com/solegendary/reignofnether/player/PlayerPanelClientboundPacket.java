package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.hud.playerdisplay.PlayerPanelClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * BAR's player list: the match roster for the top-right player panel, sent every 2 s by PlayerPanelServerEvents.
 * Each recipient gets its own copy: income and commander health are only filled in for the recipient and its
 * allies, so an enemy's economy never reaches the wire. An empty roster clears the panel (match over / reset).
 */
public class PlayerPanelClientboundPacket {

    public static final int FLAG_ALIVE = 1;
    public static final int FLAG_ALLY_DATA = 2;
    static final int MAX_ENTRIES = 64;

    public record Entry(String name, String factionKey, boolean alive, boolean hasAllyData,
                        float metalIncome, float energyIncome, float commanderHp) {}

    public final List<Entry> entries;

    public PlayerPanelClientboundPacket(List<Entry> entries) {
        this.entries = entries;
    }

    // a static decoder rather than a second constructor, so the List constructor is never ambiguous
    public static PlayerPanelClientboundPacket decode(FriendlyByteBuf buffer) {
        int n = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buffer.readUtf(64);
            String faction = buffer.readUtf(128);
            int flags = buffer.readByte();
            boolean ally = (flags & FLAG_ALLY_DATA) != 0;
            float metal = ally ? buffer.readFloat() : 0;
            float energy = ally ? buffer.readFloat() : 0;
            float hp = ally ? buffer.readFloat() : -1;
            entries.add(new Entry(name, faction, (flags & FLAG_ALIVE) != 0, ally, metal, energy, hp));
        }
        return new PlayerPanelClientboundPacket(entries);
    }

    public void encode(FriendlyByteBuf buffer) {
        int n = Math.min(entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buffer.writeUtf(e.name(), 64);
            buffer.writeUtf(e.factionKey(), 128);
            buffer.writeByte((e.alive() ? FLAG_ALIVE : 0) | (e.hasAllyData() ? FLAG_ALLY_DATA : 0));
            if (e.hasAllyData()) {
                buffer.writeFloat(e.metalIncome());
                buffer.writeFloat(e.energyIncome());
                buffer.writeFloat(e.commanderHp());
            }
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayerPanelClientEvents.sync(this.entries)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
