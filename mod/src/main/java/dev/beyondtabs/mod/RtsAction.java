package dev.beyondtabs.mod;

import net.minecraft.network.FriendlyByteBuf;

/** Client -> server: one RTS command. The server checks that the player owns every unit or building involved. */
public final class RtsAction {
    public enum Kind { MODE, ORDER, BUILD, ENQUEUE, DEQUEUE, REPEAT, RALLY, UPGRADE, RESEARCH, PAUSE, SPEED }

    public Kind kind = Kind.ORDER;
    public int orderType;           // Order.Type ordinal for ORDER
    public boolean queue;           // shift held
    public boolean on;              // MODE: entering / leaving the RTS view
    public float x, z, radius;
    /** ORDER: end of a right-drag line (NaN = plain click). */
    public float x2 = Float.NaN, z2 = Float.NaN;
    public int targetUnit = -1, targetBuilding = -1, defIndex = -1, count = 1;
    public int[] ids = new int[0];  // units (ORDER/BUILD) or the building (factory actions use targetBuilding)

    public void encode(FriendlyByteBuf b) {
        b.writeEnum(kind); b.writeVarInt(orderType); b.writeBoolean(queue); b.writeBoolean(on);
        b.writeFloat(x); b.writeFloat(z); b.writeFloat(radius); b.writeFloat(x2); b.writeFloat(z2);
        b.writeVarInt(targetUnit + 1); b.writeVarInt(targetBuilding + 1); b.writeVarInt(defIndex + 1); b.writeVarInt(count);
        b.writeVarIntArray(ids);
    }

    public static RtsAction decode(FriendlyByteBuf b) {
        RtsAction a = new RtsAction();
        a.kind = b.readEnum(Kind.class); a.orderType = b.readVarInt(); a.queue = b.readBoolean(); a.on = b.readBoolean();
        a.x = b.readFloat(); a.z = b.readFloat(); a.radius = b.readFloat(); a.x2 = b.readFloat(); a.z2 = b.readFloat();
        a.targetUnit = b.readVarInt() - 1; a.targetBuilding = b.readVarInt() - 1; a.defIndex = b.readVarInt() - 1; a.count = Math.min(20, Math.max(1, b.readVarInt()));
        a.ids = b.readVarIntArray(4096);
        return a;
    }
}
