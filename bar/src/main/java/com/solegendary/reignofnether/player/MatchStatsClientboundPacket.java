package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.matchstart.MatchEndClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

// Sent to all clients once a match ends, carrying the final scoreboard so the
// end-of-match stats screen (MatchEndScreen) can be rendered. Scores otherwise
// only exist server-side, so this is the only way the client learns them.
public class MatchStatsClientboundPacket {

    // one results-table row per player that took part in the match
    public static class MatchStatRow {
        public final String name;
        public final Faction faction;
        public final boolean winner;
        public final int teamId; // startPosColorId - players sharing it are on the same team
        public final int[] scores; // ordered as RTSPlayerScoresEnum.values()
        // BAR-style award totals (RTSPlayer.damageDealt / metalProduced / metalReclaimed, rounded)
        public final int damageDealt;
        public final int metalProduced;
        public final int metalReclaimed;
        // results-screen graphs (MatchHistory): one point every historyIntervalTicks from the match start
        public final int historyIntervalTicks;
        public final float[] metalHistory, energyHistory, armyHistory;

        public MatchStatRow(String name, Faction faction, boolean winner, int teamId, int[] scores) {
            this(name, faction, winner, teamId, scores, 0, 0, 0);
        }

        public MatchStatRow(String name, Faction faction, boolean winner, int teamId, int[] scores,
                            int damageDealt, int metalProduced, int metalReclaimed) {
            this(name, faction, winner, teamId, scores, damageDealt, metalProduced, metalReclaimed,
                    MatchHistory.SAMPLE_TICKS, null, null, null);
        }

        public MatchStatRow(String name, Faction faction, boolean winner, int teamId, int[] scores,
                            int damageDealt, int metalProduced, int metalReclaimed, int historyIntervalTicks,
                            float[] metalHistory, float[] energyHistory, float[] armyHistory) {
            this.name = name;
            this.faction = faction != null ? faction : Factions.NONE; // encode() dereferences faction.key
            this.winner = winner;
            this.teamId = teamId;
            this.scores = scores != null ? scores : new int[0];
            this.damageDealt = damageDealt;
            this.metalProduced = metalProduced;
            this.metalReclaimed = metalReclaimed;
            this.historyIntervalTicks = Math.max(1, historyIntervalTicks);
            this.metalHistory = metalHistory != null ? metalHistory : new float[0];
            this.energyHistory = energyHistory != null ? energyHistory : new float[0];
            this.armyHistory = armyHistory != null ? armyHistory : new float[0];
        }

        // bounds-checked: a row from an older/newer score enum must not crash the results screen
        public int score(int index) {
            return index >= 0 && index < scores.length ? scores[index] : 0;
        }
    }

    private final long gameDurationTicks;
    private final List<MatchStatRow> rows;

    public static void broadcast(long gameDurationTicks, List<MatchStatRow> rows) {
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new MatchStatsClientboundPacket(gameDurationTicks, rows));
    }

    public MatchStatsClientboundPacket(long gameDurationTicks, List<MatchStatRow> rows) {
        this.gameDurationTicks = gameDurationTicks;
        this.rows = rows;
    }

    public MatchStatsClientboundPacket(FriendlyByteBuf buffer) {
        this.gameDurationTicks = buffer.readLong();
        int n = buffer.readInt();
        this.rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buffer.readUtf();
            Faction faction = Factions.getFaction(buffer.readResourceLocation());
            boolean winner = buffer.readBoolean();
            int teamId = buffer.readVarInt();
            int[] scores = buffer.readVarIntArray();
            int damage = buffer.readVarInt();
            int metalMade = buffer.readVarInt();
            int metalReclaimed = buffer.readVarInt();
            int interval = buffer.readVarInt();
            float[] metal = readHistory(buffer), energy = readHistory(buffer), army = readHistory(buffer);
            this.rows.add(new MatchStatRow(name, faction, winner, teamId, scores, damage, metalMade, metalReclaimed,
                    interval, metal, energy, army));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(gameDurationTicks);
        buffer.writeInt(rows.size());
        for (MatchStatRow row : rows) {
            buffer.writeUtf(row.name);
            buffer.writeResourceLocation(row.faction.key);
            buffer.writeBoolean(row.winner);
            buffer.writeVarInt(row.teamId);
            buffer.writeVarIntArray(row.scores);
            buffer.writeVarInt(Math.max(0, row.damageDealt));
            buffer.writeVarInt(Math.max(0, row.metalProduced));
            buffer.writeVarInt(Math.max(0, row.metalReclaimed));
            buffer.writeVarInt(row.historyIntervalTicks);
            writeHistory(buffer, row.metalHistory);
            writeHistory(buffer, row.energyHistory);
            writeHistory(buffer, row.armyHistory);
        }
    }

    // bounded both ways: a hostile/garbled length can't make the client allocate a huge array
    private static void writeHistory(FriendlyByteBuf buffer, float[] h) {
        int n = Math.min(h.length, MatchHistory.MAX_SAMPLES);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++)
            buffer.writeFloat(h[i]);
    }

    private static float[] readHistory(FriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        if (n < 0 || n > MatchHistory.MAX_SAMPLES)
            throw new IllegalArgumentException("bad match history length " + n);
        float[] h = new float[n];
        for (int i = 0; i < n; i++)
            h[i] = buffer.readFloat();
        return h;
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> {
                        MatchEndClientEvents.receive(gameDurationTicks, rows);
                        success.set(true);
                    });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
