package dev.beyondtabs.mod;

import com.mojang.datafixers.util.Pair;
import dev.beyondtabs.engine.Ai;
import dev.beyondtabs.engine.Skirmish;
import dev.beyondtabs.engine.gen.RaceDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/** Server side of the skirmish lobby: who may change what, and turning the lobby into a running match. */
public final class LobbyServer {
    private LobbyServer() { }

    static final Map<ServerLevel, Lobby> LOBBIES = new HashMap<>();

    static Lobby of(ServerLevel level) { return LOBBIES.computeIfAbsent(level, l -> new Lobby()); }

    static void broadcast(ServerLevel level, Lobby lb) {
        for (ServerPlayer p : level.players()) Network.sendLobby(p, lb, false);
    }

    /** Opens the lobby for a player: they become host if there is none, and take the first open seat. */
    static void open(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Lobby lb = of(level);
        if (lb.host == null || level.getServer().getPlayerList().getPlayer(lb.host) == null) lb.host = p.getUUID();
        if (!lb.started && lb.slotOf(p.getUUID()) < 0)
            for (int i = 0; i < Lobby.SLOTS; i++) if (lb.slots[i].type == Lobby.OPEN) { lb.seat(p.getUUID(), p.getGameProfile().getName(), i); break; }
        Network.sendLobby(p, lb, true);
        broadcast(level, lb);
    }

    static void handle(ServerPlayer p, LobbyAction a) {
        ServerLevel level = p.serverLevel();
        Lobby lb = of(level);
        if (a.op == LobbyAction.Op.REQUEST) { open(p); return; }
        boolean host = p.getUUID().equals(lb.host) || p.hasPermissions(2);
        if (a.op == LobbyAction.Op.RETURN) {
            Match m = ServerEvents.MATCHES.get(level);
            if (!host && (m == null || m.world.winner < 0)) { p.displayClientMessage(Component.literal("Only the host can end a match early."), true); return; }
            if (m != null) { TacticsStore.save(m.world); m.structures.clearAll(); ServerEvents.MATCHES.remove(level); }
            lb.started = false;
            for (ServerPlayer q : level.players()) Network.sendLobby(q, lb, true);
            return;
        }
        if (lb.started) return;   // the setup is locked while a match is running
        int me = lb.slotOf(p.getUUID());
        switch (a.op) {
            case JOIN -> { if (!lb.seat(p.getUUID(), p.getGameProfile().getName(), a.slot)) return; }
            case LEAVE -> lb.unseat(p.getUUID());
            case MAP -> { if (!host) return; lb.map = Math.floorMod(a.value, Lobby.MAPS.length); }
            case SIZE -> { if (!host) return; lb.size = Math.floorMod(a.value, Lobby.SIZE_NAMES.length); }
            case SET -> { if (!set(lb, a, host, me)) return; }
            case START -> {
                if (!host) return;
                if (lb.activeCount() < 2) { p.displayClientMessage(Component.literal("Add at least two players or AIs."), true); return; }
                start(p, lb);
            }
            default -> { return; }
        }
        broadcast(level, lb);
    }

    /** One field of one slot. The host may change anything; a seated player may change their own faction, colour, team and start. */
    static boolean set(Lobby lb, LobbyAction a, boolean host, int me) {
        if (a.slot < 0 || a.slot >= Lobby.SLOTS) return false;
        Lobby.Slot s = lb.slots[a.slot];
        boolean own = a.slot == me;
        switch (a.field) {
            case TYPE -> {
                if (!host || a.value == Lobby.HUMAN) return false;
                if (a.value != Lobby.OPEN && a.value != Lobby.CLOSED && a.value != Lobby.AI) return false;
                if (s.type == Lobby.HUMAN) { s.player = null; s.name = ""; }
                s.type = a.value;
                if (s.type == Lobby.AI && !lb.colorFree(s.color, a.slot)) s.color = lb.nextColor(s.color, 1, a.slot);
            }
            case AI -> { if (!host || s.type != Lobby.AI) return false; s.ai = Math.floorMod(a.value, Lobby.AI_NAMES.length); }
            case RACE -> {
                if (!(host || own)) return false;
                int n = Lobby.playable().size();
                if (a.value < -1 || a.value >= n) return false;
                s.race = a.value;
            }
            case COLOR -> {
                if (!(host || own)) return false;
                if (a.value < 0 || a.value >= Lobby.COLORS.length || !lb.colorFree(a.value, a.slot)) return false;
                s.color = a.value;
            }
            case TEAM -> { if (!(host || own)) return false; s.team = Math.floorMod(a.value, Lobby.TEAMS + 1); }
            case SPAWN -> {
                if (!(host || own)) return false;
                if (a.value < -1 || a.value >= Lobby.POSITIONS || !lb.spawnFree(a.value, a.slot)) return false;
                s.spawn = a.value;
            }
        }
        return true;
    }

    static final List<ResourceKey<Biome>> BIOMES = List.of(Biomes.PLAINS, Biomes.PLAINS, Biomes.DESERT, Biomes.SNOWY_PLAINS, Biomes.FOREST, Biomes.SAVANNA, Biomes.BADLANDS, Biomes.MEADOW);

    /** Share of water columns in the battlefield disc (sampled every 12 blocks; surface above the sea floor = water). */
    static float waterFraction(ServerLevel level, int cx, int cz, float r) {
        int water = 0, n = 0; float rr = r + 18;
        for (int dx = (int) -rr; dx <= rr; dx += 12) for (int dz = (int) -rr; dz <= rr; dz += 12) {
            if (dx * dx + dz * dz > rr * rr) continue;
            int x = cx + dx, z = cz + dz; n++;
            if (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) > level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z)) water++;
        }
        return n == 0 ? 0 : water / (float) n;
    }

    /** The driest battlefield near the chosen spot: spiral outwards, take the first nearly dry one (else the driest seen). */
    static BlockPos dryCenter(ServerLevel level, BlockPos start, float r) {
        BlockPos best = start; float bestW = waterFraction(level, start.getX(), start.getZ(), r);
        for (int ring = 1; ring <= 6 && bestW > .04f; ring++)
            for (int k = 0; k < 8 && bestW > .04f; k++) {
                double a = k * Math.PI / 4 + ring * .4;
                int x = start.getX() + (int) (Math.cos(a) * ring * 96), z = start.getZ() + (int) (Math.sin(a) * ring * 96);
                float wf = waterFraction(level, x, z, r);
                if (wf < bestW) { bestW = wf; best = new BlockPos(x, start.getY(), z); }
            }
        return best;
    }

    /** Builds the match: finds the battlefield, lays out the bases, seats the players and starts the AIs. */
    static void start(ServerPlayer hostPlayer, Lobby lb) {
        ServerLevel level = hostPlayer.serverLevel();
        BlockPos center = hostPlayer.blockPosition();
        if (lb.map > 0) {
            hostPlayer.displayClientMessage(Component.literal("Looking for " + Lobby.MAP_NAMES[lb.map] + "..."), true);
            ResourceKey<Biome> want = BIOMES.get(lb.map);
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(want), center, 3200, 32, 64);
            if (found != null) center = found.getFirst();
            else hostPlayer.sendSystemMessage(Component.literal("No " + Lobby.MAP_NAMES[lb.map] + " nearby; fighting right here instead."));
        }
        float radius = Lobby.SIZE_RADIUS[Math.max(0, Math.min(2, lb.size))];
        center = dryCenter(level, center, radius);
        Match m = new Match(level);
        ServerEvents.replace(level, m);
        Random rng = new Random(level.getGameTime() ^ center.asLong());
        List<RaceDef> races = Lobby.playable();
        List<Skirmish.Player> players = new ArrayList<>();
        List<Integer> slotOfPlayer = new ArrayList<>();
        for (int i = 0; i < Lobby.SLOTS; i++) {
            Lobby.Slot s = lb.slots[i];
            if (!s.active()) continue;
            String race = (s.race >= 0 && s.race < races.size() ? races.get(s.race) : races.get(rng.nextInt(races.size()))).id();
            Ai.Difficulty ai = s.type == Lobby.AI ? Ai.Difficulty.values()[Math.max(0, Math.min(2, s.ai))] : null;
            players.add(new Skirmish.Player(race, s.team > 0 ? s.team - 1 : -1, s.spawn, ai));
            slotOfPlayer.add(i);
        }
        List<Skirmish.Start> starts = Skirmish.setup(m.world, center.getX() + .5f, center.getZ() + .5f, radius, Lobby.POSITIONS, players, rng.nextLong());
        // tidy the fields we build and fight on, leave the forests elsewhere
        float cx = center.getX() + .5f, cz = center.getZ() + .5f;
        Clearing.disc(level, cx, cz, 14);
        for (Skirmish.Start st : starts) { Clearing.disc(level, st.x(), st.z(), 28); Clearing.lane(level, st.x(), st.z(), cx, cz, 7); }
        for (float[] sp : m.world.metalSpots) Clearing.disc(level, sp[0], sp[1], 2.5f);
        m.terrain.invalidate();
        for (int k = 0; k < starts.size(); k++) {
            Lobby.Slot s = lb.slots[slotOfPlayer.get(k)];
            Skirmish.Start st = starts.get(k);
            m.teamColors.put(st.team(), Lobby.COLORS[s.color]);
            if (s.type == Lobby.HUMAN && s.player != null) {
                m.playerTeams.put(s.player, st.team());
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(s.player);
                if (p != null) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(st.x()), (int) Math.floor(st.z()));
                    p.teleportTo(level, st.x(), y + 25, st.z(), p.getYRot(), 60);
                }
            }
        }
        TacticsStore.load(m.world);
        // a calm battlefield: midday, no monsters wandering in
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, level.getServer());   // rain and storms roll through (existing worlds too)
        lb.started = true;
        int humans = 0; for (Lobby.Slot s : lb.slots) if (s.type == Lobby.HUMAN) humans++;
        for (ServerPlayer q : level.players()) q.sendSystemMessage(Component.literal(String.format("Battle begins: %d armies on %s (%s). %s",
                starts.size(), Lobby.MAP_NAMES[lb.map], Lobby.SIZE_NAMES[lb.size], humans == 0 ? "Press V to watch." : "Good luck, commander.")));
    }
}
