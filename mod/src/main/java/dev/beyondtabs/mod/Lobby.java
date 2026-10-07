package dev.beyondtabs.mod;

import dev.beyondtabs.engine.gen.RaceDef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The pre-game skirmish setup (Generals / Tiberium Wars style): up to 8 slots, each open, closed, a human or an AI,
 * with a faction, a colour, an alliance and a start position; plus the battlefield (biome) and its size.
 * Lives on the server per dimension; every change is broadcast to everyone in it.
 */
public final class Lobby {
    public static final int SLOTS = 8, POSITIONS = 8;
    public static final int OPEN = 0, CLOSED = 1, HUMAN = 2, AI = 3;
    public static final String[] AI_NAMES = {"Easy AI", "Normal AI", "Hard AI"};
    public static final String[] MAPS = {"here", "plains", "desert", "snowy", "forest", "savanna", "badlands", "meadow"};
    public static final String[] MAP_NAMES = {"Right here", "Green Plains", "Scorched Desert", "Frozen Tundra", "Old Forest", "Savanna", "Badlands Canyons", "Mountain Meadow"};
    public static final String[] SIZE_NAMES = {"Small", "Medium", "Large"};
    public static final float[] SIZE_RADIUS = {45, 62, 80};
    public static final int[] COLORS = {0x3B6FD8, 0xD23F36, 0x4FB04A, 0xE0B83A, 0xE07A2E, 0x9A5AD8, 0x3AB8C8, 0xE05AA0, 0xE8E8E8, 0x3A3A3A, 0x8A5A3A, 0x9AD83A};
    public static final String[] COLOR_NAMES = {"Blue", "Red", "Green", "Gold", "Orange", "Purple", "Teal", "Pink", "White", "Black", "Brown", "Lime"};
    /** Alliance 0 = none (free-for-all), 1-4 = teams. */
    public static final int TEAMS = 4;

    public static final class Slot {
        public int type = CLOSED, ai = 1, race = -1, color, team, spawn = -1;   // race: index into playable(), -1 = random
        public UUID player; public String name = "";
        boolean active() { return type == HUMAN || type == AI; }
    }

    public final Slot[] slots = new Slot[SLOTS];
    public int map, size = 1;
    public boolean started;
    public UUID host;

    public Lobby() {
        for (int i = 0; i < SLOTS; i++) { slots[i] = new Slot(); slots[i].color = i; slots[i].type = i < 4 ? OPEN : CLOSED; }
        slots[1].type = AI;
    }

    /** Races that can be picked right now. */
    public static List<RaceDef> playable() {
        List<RaceDef> l = new ArrayList<>();
        for (RaceDef r : RaceDef.ALL) if (r.firstPlayable()) l.add(r);
        return l;
    }

    public int slotOf(UUID p) { for (int i = 0; i < SLOTS; i++) if (p != null && p.equals(slots[i].player)) return i; return -1; }

    public int activeCount() { int n = 0; for (Slot s : slots) if (s.active()) n++; return n; }

    public boolean colorFree(int color, int except) {
        for (int i = 0; i < SLOTS; i++) if (i != except && slots[i].active() && slots[i].color == color) return false;
        return true;
    }

    public boolean spawnFree(int spawn, int except) {
        if (spawn < 0) return true;
        for (int i = 0; i < SLOTS; i++) if (i != except && slots[i].active() && slots[i].spawn == spawn) return false;
        return true;
    }

    /** The next colour after `from` (step +-1) that nobody else uses. */
    public int nextColor(int from, int step, int slot) {
        for (int k = 1; k <= COLORS.length; k++) {
            int c = Math.floorMod(from + step * k, COLORS.length);
            if (colorFree(c, slot)) return c;
        }
        return from;
    }

    /** Puts a player into a slot (leaving any other slot); false if the slot isn't open. */
    public boolean seat(UUID p, String name, int i) {
        if (i < 0 || i >= SLOTS || slots[i].type != OPEN) return false;
        int old = slotOf(p);
        if (old >= 0) { slots[old].type = OPEN; slots[old].player = null; slots[old].name = ""; }
        Slot s = slots[i]; s.type = HUMAN; s.player = p; s.name = name;
        if (!colorFree(s.color, i)) s.color = nextColor(s.color, 1, i);
        return true;
    }

    public void unseat(UUID p) {
        int i = slotOf(p);
        if (i >= 0) { slots[i].type = OPEN; slots[i].player = null; slots[i].name = ""; }
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(map); b.writeVarInt(size); b.writeBoolean(started);
        b.writeBoolean(host != null); if (host != null) b.writeUUID(host);
        for (Slot s : slots) {
            b.writeVarInt(s.type); b.writeVarInt(s.ai); b.writeVarInt(s.race + 1); b.writeVarInt(s.color); b.writeVarInt(s.team); b.writeVarInt(s.spawn + 1);
            b.writeBoolean(s.player != null); if (s.player != null) b.writeUUID(s.player);
            b.writeUtf(s.name, 64);
        }
    }

    public static Lobby decode(FriendlyByteBuf b) {
        Lobby l = new Lobby();
        l.map = b.readVarInt(); l.size = b.readVarInt(); l.started = b.readBoolean();
        if (b.readBoolean()) l.host = b.readUUID();
        for (Slot s : l.slots) {
            s.type = b.readVarInt(); s.ai = b.readVarInt(); s.race = b.readVarInt() - 1; s.color = b.readVarInt(); s.team = b.readVarInt(); s.spawn = b.readVarInt() - 1;
            s.player = b.readBoolean() ? b.readUUID() : null;
            s.name = b.readUtf(64);
        }
        return l;
    }
}
