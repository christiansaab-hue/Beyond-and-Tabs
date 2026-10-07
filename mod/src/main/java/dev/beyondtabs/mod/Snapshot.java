package dev.beyondtabs.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client state for one tick, for one player: every unit (ragdoll parts as 1/256-block offsets near the player),
 * every building, projectiles, and the player's own team economy and research.
 */
public final class Snapshot {
    public long tick;
    public int myTeam = -1, winner = -1;
    public String myRace = "";
    public float metal, metalMax, metalIncome, metalSpend, energy, energyMax, energyIncome, energySpend, efficiency;
    public int supplyUsed, supplyCap;
    public final List<String> researched = new ArrayList<>();
    public final List<U> units = new ArrayList<>();
    public final List<B> buildings = new ArrayList<>();
    public final List<float[]> projectiles = new ArrayList<>();
    public float[] metalSpots = new float[0];    // x,z pairs (sent every second)
    public boolean hasSpots;

    public static final class U {
        public int id; public byte team; public short def; public boolean alive, knocked;
        public float x, z, yaw, walkPhase, walkAmount, attack, hp; public float[] parts;
        public byte orders;   // queued order count (for the selection panel)
    }

    public static final class B {
        public int id; public byte team; public short def; public byte level; public boolean upgrading, repeat;
        public float x, z, progress, hp, rallyX, rallyZ, produceFrac, researchFrac;
        public short producing = -1, researching = -1; public short[] queue = new short[0];
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarLong(tick); b.writeVarInt(myTeam + 1); b.writeVarInt(winner + 1); b.writeUtf(myRace);
        for (float f : new float[]{metal, metalMax, metalIncome, metalSpend, energy, energyMax, energyIncome, energySpend, efficiency}) b.writeFloat(f);
        b.writeVarInt(supplyUsed); b.writeVarInt(supplyCap);
        b.writeVarInt(researched.size()); for (String r : researched) b.writeUtf(r);
        b.writeVarInt(units.size());
        for (U u : units) {
            b.writeVarInt(u.id); b.writeByte(u.team); b.writeShort(u.def);
            b.writeByte((u.alive ? 1 : 0) | (u.knocked ? 2 : 0) | (u.parts != null ? 4 : 0));
            b.writeFloat(u.x); b.writeFloat(u.z); b.writeFloat(u.yaw); b.writeFloat(u.walkPhase);
            b.writeByte((int) (u.walkAmount * 255)); b.writeFloat(u.attack); b.writeByte((int) (Math.max(0, Math.min(1, u.hp)) * 255)); b.writeByte(u.orders);
            if (u.parts != null) {
                b.writeByte(u.parts.length / 3);
                float y0 = u.parts[1];
                b.writeFloat(y0);
                for (int i = 0; i < u.parts.length; i += 3) {
                    b.writeShort(clamp((u.parts[i] - u.x) * 256)); b.writeShort(clamp((u.parts[i + 1] - y0) * 256)); b.writeShort(clamp((u.parts[i + 2] - u.z) * 256));
                }
            }
        }
        b.writeVarInt(buildings.size());
        for (B x : buildings) {
            b.writeVarInt(x.id); b.writeByte(x.team); b.writeShort(x.def); b.writeByte(x.level);
            b.writeByte((x.upgrading ? 1 : 0) | (x.repeat ? 2 : 0));
            for (float f : new float[]{x.x, x.z, x.progress, x.hp, x.rallyX, x.rallyZ, x.produceFrac, x.researchFrac}) b.writeFloat(f);
            b.writeShort(x.producing); b.writeShort(x.researching);
            b.writeVarInt(x.queue.length); for (short q : x.queue) b.writeShort(q);
        }
        b.writeVarInt(projectiles.size());
        for (float[] p : projectiles) for (float f : p) b.writeFloat(f);
        b.writeBoolean(hasSpots);
        if (hasSpots) { b.writeVarInt(metalSpots.length); for (float f : metalSpots) b.writeFloat(f); }
    }

    static short clamp(float v) { return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v))); }

    public static Snapshot decode(FriendlyByteBuf b) {
        Snapshot s = new Snapshot(); s.tick = b.readVarLong(); s.myTeam = b.readVarInt() - 1; s.winner = b.readVarInt() - 1; s.myRace = b.readUtf();
        s.metal = b.readFloat(); s.metalMax = b.readFloat(); s.metalIncome = b.readFloat(); s.metalSpend = b.readFloat();
        s.energy = b.readFloat(); s.energyMax = b.readFloat(); s.energyIncome = b.readFloat(); s.energySpend = b.readFloat(); s.efficiency = b.readFloat();
        s.supplyUsed = b.readVarInt(); s.supplyCap = b.readVarInt();
        int rn = b.readVarInt(); for (int i = 0; i < rn; i++) s.researched.add(b.readUtf());
        int n = b.readVarInt();
        for (int k = 0; k < n; k++) {
            U u = new U(); u.id = b.readVarInt(); u.team = b.readByte(); u.def = b.readShort();
            int f = b.readByte(); u.alive = (f & 1) != 0; u.knocked = (f & 2) != 0;
            u.x = b.readFloat(); u.z = b.readFloat(); u.yaw = b.readFloat(); u.walkPhase = b.readFloat();
            u.walkAmount = (b.readByte() & 255) / 255f; u.attack = b.readFloat(); u.hp = (b.readByte() & 255) / 255f; u.orders = b.readByte();
            if ((f & 4) != 0) {
                int pc = b.readByte() & 255; float y0 = b.readFloat(); u.parts = new float[pc * 3];
                for (int i = 0; i < pc * 3; i += 3) { u.parts[i] = u.x + b.readShort() / 256f; u.parts[i + 1] = y0 + b.readShort() / 256f; u.parts[i + 2] = u.z + b.readShort() / 256f; }
            }
            s.units.add(u);
        }
        int bn = b.readVarInt();
        for (int k = 0; k < bn; k++) {
            B x = new B(); x.id = b.readVarInt(); x.team = b.readByte(); x.def = b.readShort(); x.level = b.readByte();
            int f = b.readByte(); x.upgrading = (f & 1) != 0; x.repeat = (f & 2) != 0;
            x.x = b.readFloat(); x.z = b.readFloat(); x.progress = b.readFloat(); x.hp = b.readFloat(); x.rallyX = b.readFloat(); x.rallyZ = b.readFloat();
            x.produceFrac = b.readFloat(); x.researchFrac = b.readFloat(); x.producing = b.readShort(); x.researching = b.readShort();
            int qn = b.readVarInt(); x.queue = new short[qn]; for (int i = 0; i < qn; i++) x.queue[i] = b.readShort();
            s.buildings.add(x);
        }
        int pn = b.readVarInt();
        for (int k = 0; k < pn; k++) { float[] p = new float[6]; for (int i = 0; i < 6; i++) p[i] = b.readFloat(); s.projectiles.add(p); }
        s.hasSpots = b.readBoolean();
        if (s.hasSpots) { int sn = b.readVarInt(); s.metalSpots = new float[sn]; for (int i = 0; i < sn; i++) s.metalSpots[i] = b.readFloat(); }
        return s;
    }
}
