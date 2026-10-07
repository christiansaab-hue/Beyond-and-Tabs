package dev.beyondtabs.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** Server -> client state for one tick. Ragdoll parts are sent as offsets (1/256 block) from the unit's position. */
public final class Snapshot {
    public long tick; public final List<U> units = new ArrayList<>(); public final List<float[]> projectiles = new ArrayList<>();

    public static final class U {
        public int id; public byte team; public short def; public boolean alive, knocked;
        public float x, z, yaw, walkPhase, walkAmount, attack, hp; public float[] parts;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarLong(tick); b.writeVarInt(units.size());
        for (U u : units) {
            b.writeVarInt(u.id); b.writeByte(u.team); b.writeShort(u.def);
            b.writeByte((u.alive ? 1 : 0) | (u.knocked ? 2 : 0) | (u.parts != null ? 4 : 0));
            b.writeFloat(u.x); b.writeFloat(u.z); b.writeFloat(u.yaw); b.writeFloat(u.walkPhase);
            b.writeByte((int) (u.walkAmount * 255)); b.writeFloat(u.attack); b.writeByte((int) (Math.max(0, u.hp) * 255));
            if (u.parts != null) {
                b.writeByte(u.parts.length / 3);
                float y0 = u.parts[1];
                b.writeFloat(y0);
                for (int i = 0; i < u.parts.length; i += 3) {
                    b.writeShort(clamp((u.parts[i] - u.x) * 256)); b.writeShort(clamp((u.parts[i + 1] - y0) * 256)); b.writeShort(clamp((u.parts[i + 2] - u.z) * 256));
                }
            }
        }
        b.writeVarInt(projectiles.size());
        for (float[] p : projectiles) for (float f : p) b.writeFloat(f);
    }

    static short clamp(float v) { return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v))); }

    public static Snapshot decode(FriendlyByteBuf b) {
        Snapshot s = new Snapshot(); s.tick = b.readVarLong(); int n = b.readVarInt();
        for (int k = 0; k < n; k++) {
            U u = new U(); u.id = b.readVarInt(); u.team = b.readByte(); u.def = b.readShort();
            int f = b.readByte(); u.alive = (f & 1) != 0; u.knocked = (f & 2) != 0;
            u.x = b.readFloat(); u.z = b.readFloat(); u.yaw = b.readFloat(); u.walkPhase = b.readFloat();
            u.walkAmount = (b.readByte() & 255) / 255f; u.attack = b.readFloat(); u.hp = (b.readByte() & 255) / 255f;
            if ((f & 4) != 0) {
                int pc = b.readByte() & 255; float y0 = b.readFloat(); u.parts = new float[pc * 3];
                for (int i = 0; i < pc * 3; i += 3) { u.parts[i] = u.x + b.readShort() / 256f; u.parts[i + 1] = y0 + b.readShort() / 256f; u.parts[i + 2] = u.z + b.readShort() / 256f; }
            }
            s.units.add(u);
        }
        int pn = b.readVarInt();
        for (int k = 0; k < pn; k++) { float[] p = new float[6]; for (int i = 0; i < 6; i++) p[i] = b.readFloat(); s.projectiles.add(p); }
        return s;
    }
}
