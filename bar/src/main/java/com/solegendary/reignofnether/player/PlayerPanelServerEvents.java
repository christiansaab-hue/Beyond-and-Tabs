package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds the top-right player panel (BAR's player list, see PlayerPanelClientEvents). Every 2 s each connected player
 * gets the match roster: alive RTS players plus the ones already defeated (PlayerServerEvents moves them to
 * postGameRtsPlayers). Metal / energy income and commander health go only to the player itself and its allies.
 *
 * 2 s is plenty for a glance-at panel and keeps it to one tiny packet per player per 40 ticks even at 8v8; the
 * commander scan is a single pass over all units at that cadence.
 */
public class PlayerPanelServerEvents {

    static final int SYNC_TICKS = 40;
    private static int ticks = 0;
    private static boolean sentEmpty = true;   // nothing to clear before the first match

    /** One roster line before it is filtered for a recipient. */
    public record Row(String name, String factionKey, boolean alive, float metalIncome, float energyIncome,
                      float commanderHp) {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ++ticks < SYNC_TICKS)
            return;
        ticks = 0;
        List<ServerPlayer> players = evt.getServer().getPlayerList().getPlayers();
        List<Row> rows = buildRows();
        if (rows.isEmpty()) {
            if (!sentEmpty) {   // the match just ended or was reset: clear every panel once
                List<PlayerPanelClientboundPacket.Entry> none = List.of();
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPanelClientboundPacket(none));
            }
            sentEmpty = true;
            return;
        }
        sentEmpty = false;
        for (ServerPlayer sp : players)
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                new PlayerPanelClientboundPacket(entriesFor(sp.getName().getString(), rows)));
    }

    public static List<Row> buildRows() {
        List<RTSPlayer> alive = new ArrayList<>();
        List<RTSPlayer> gone = new ArrayList<>();
        synchronized (PlayerServerEvents.rtsPlayers) {
            alive.addAll(PlayerServerEvents.rtsPlayers);
        }
        synchronized (PlayerServerEvents.postGameRtsPlayers) {
            gone.addAll(PlayerServerEvents.postGameRtsPlayers);
        }
        if (alive.isEmpty())   // postGame players linger until the next reset; no live match means no panel
            return List.of();

        // healthiest commander per owner, one pass over all units
        Map<String, Float> commanderHp = new HashMap<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !le.isAlive() || !CommanderServerEvents.isCommander(le))
                continue;
            float hp = le.getMaxHealth() > 0 ? le.getHealth() / le.getMaxHealth() : 0;
            commanderHp.merge(u.getOwnerName(), hp, Math::max);
        }
        List<Row> rows = new ArrayList<>(alive.size() + gone.size());
        for (RTSPlayer p : alive)
            rows.add(row(p, true, commanderHp));
        for (RTSPlayer p : gone) {
            boolean dup = false;   // a winner can sit in both lists for the last tick of a match
            for (Row r : rows)
                if (r.name().equals(p.name)) {
                    dup = true;
                    break;
                }
            if (!dup)
                rows.add(row(p, false, commanderHp));
        }
        return rows;
    }

    private static Row row(RTSPlayer p, boolean alive, Map<String, Float> commanderHp) {
        ResourceLocation key = p.faction == null ? null : Factions.getKey(p.faction);
        float metal = 0, energy = 0;
        if (alive) {
            EconomyServerEvents.PlayerEconomy eco = EconomyServerEvents.getEconomy(p.name);
            metal = eco.metalIncome + eco.metalConverted;   // same "income" the economy bar shows
            energy = eco.energyIncome;
        }
        return new Row(p.name, key == null ? "" : key.toString(), alive, metal, energy,
            alive ? commanderHp.getOrDefault(p.name, -1f) : -1f);
    }

    /**
     * The roster as {@code recipient} may see it: itself first, then its allies, then everyone else; economy and
     * commander data only on its own and its allies' lines. Public for the game test.
     */
    public static List<PlayerPanelClientboundPacket.Entry> entriesFor(String recipient, List<Row> rows) {
        List<PlayerPanelClientboundPacket.Entry> self = new ArrayList<>(1);
        List<PlayerPanelClientboundPacket.Entry> allies = new ArrayList<>();
        List<PlayerPanelClientboundPacket.Entry> enemies = new ArrayList<>();
        for (Row r : rows) {
            boolean me = r.name().equals(recipient);
            boolean ally = !me && AlliancesServerEvents.isAllied(recipient, r.name());
            boolean data = me || ally;
            var e = new PlayerPanelClientboundPacket.Entry(r.name(), r.factionKey(), r.alive(), data,
                data ? r.metalIncome() : 0, data ? r.energyIncome() : 0, data ? r.commanderHp() : -1);
            (me ? self : ally ? allies : enemies).add(e);
        }
        // alive before defeated within each group, so the lines that matter stay on top
        allies.sort((a, b) -> Boolean.compare(b.alive(), a.alive()));
        enemies.sort((a, b) -> Boolean.compare(b.alive(), a.alive()));
        self.addAll(allies);
        self.addAll(enemies);
        return self;
    }
}
