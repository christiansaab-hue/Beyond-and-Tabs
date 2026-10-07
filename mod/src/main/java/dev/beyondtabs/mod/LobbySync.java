package dev.beyondtabs.mod;

import net.minecraft.network.FriendlyByteBuf;

/** Server -> client: the lobby as it is now; `open` asks the client to show the lobby screen. */
public final class LobbySync {
    public final Lobby lobby; public final boolean open;
    public LobbySync(Lobby lobby, boolean open) { this.lobby = lobby; this.open = open; }
    public void encode(FriendlyByteBuf b) { b.writeBoolean(open); lobby.encode(b); }
    public static LobbySync decode(FriendlyByteBuf b) { boolean o = b.readBoolean(); return new LobbySync(Lobby.decode(b), o); }
}
