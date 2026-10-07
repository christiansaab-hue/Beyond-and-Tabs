package dev.beyondtabs.mod;

import net.minecraft.network.FriendlyByteBuf;

/** Client -> server: a change to the skirmish lobby. The server checks who may change what. */
public final class LobbyAction {
    public enum Op { REQUEST, JOIN, LEAVE, SET, MAP, SIZE, START, RETURN }
    public enum Field { TYPE, AI, RACE, COLOR, TEAM, SPAWN }

    public Op op = Op.REQUEST;
    public int slot = -1, value;
    public Field field = Field.TYPE;

    public LobbyAction() { }
    public LobbyAction(Op op) { this.op = op; }
    public LobbyAction(Op op, int slot, Field field, int value) { this.op = op; this.slot = slot; this.field = field; this.value = value; }

    public void encode(FriendlyByteBuf b) { b.writeEnum(op); b.writeVarInt(slot + 1); b.writeEnum(field); b.writeVarInt(value + 1); }

    public static LobbyAction decode(FriendlyByteBuf b) {
        LobbyAction a = new LobbyAction();
        a.op = b.readEnum(Op.class); a.slot = b.readVarInt() - 1; a.field = b.readEnum(Field.class); a.value = b.readVarInt() - 1;
        return a;
    }
}
