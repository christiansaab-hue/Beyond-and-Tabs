package dev.beyondtabs.mod.client;

import dev.beyondtabs.mod.Lobby;
import dev.beyondtabs.mod.LobbySync;
import net.minecraft.client.Minecraft;

/** The lobby as last sent by the server, and what the client does when a battle starts or ends. */
public final class ClientLobby {
    private ClientLobby() { }

    public static volatile Lobby lobby;

    public static void accept(LobbySync msg) {
        Minecraft mc = Minecraft.getInstance();
        boolean wasStarted = lobby != null && lobby.started;
        lobby = msg.lobby;
        Lobby lb = msg.lobby;
        if (lb.started && !wasStarted) {   // the battle begins: seated humans go straight to the RTS view
            if (mc.screen instanceof LobbyScreen) mc.setScreen(null);
            int me = mc.player == null ? -1 : lb.slotOf(mc.player.getUUID());
            if (me >= 0 && lb.slots[me].type == Lobby.HUMAN) {
                ClientMatch.focusCommander = true;
                if (!RtsCamera.active) RtsClient.toggle();
            }
            return;
        }
        if (!lb.started && wasStarted) {   // back from a battle
            if (RtsCamera.active) RtsClient.toggle();
            ClientMatch.clear();
            mc.setScreen(new LobbyScreen());
            return;
        }
        if (msg.open && !(mc.screen instanceof LobbyScreen)) mc.setScreen(new LobbyScreen());
    }

    public static void clear() { lobby = null; }
}
