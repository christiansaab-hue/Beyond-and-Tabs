package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.player.MatchStatsClientboundPacket.MatchStatRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

// Receives the end-of-match scoreboard from the server and opens MatchEndScreen.
// The data is cached so the popup can be dismissed and reopened, and survives until
// the next match end or logout.
//
// This was fully commented out in the fork. The screen itself worked; the problems were around it: it opened on the
// same tick as the "You are victorious!" title (hiding it), the server could NPE encoding a row whose faction was
// null, and rows indexed scores[] without a bounds check. Now the popup waits until the title has played
// (TITLE_HOLD_TICKS), the rows are null-safe (MatchStatRow), and chat/minimap are hidden for the whole moment.
public class MatchEndClientEvents {

    // vanilla Gui.setTitle default timing is 10 fade-in + 70 stay + 20 fade-out ticks
    private static final int TITLE_HOLD_TICKS = 100;

    private static long gameDurationTicks = 0;
    private static List<MatchStatRow> rows = new ArrayList<>();
    private static int openDelayTicks = -1;   // >0 while waiting for the title to finish, then the screen opens
    private static int titleHoldTicks = 0;    // >0 while a victory/defeat title is on screen

    public static long getGameDurationTicks() {
        return gameDurationTicks;
    }

    public static List<MatchStatRow> getRows() {
        return rows;
    }

    public static boolean hasResults() {
        return !rows.isEmpty();
    }

    // called on the client main thread from the packet handler
    public static void receive(long ticks, List<MatchStatRow> newRows) {
        gameDurationTicks = ticks;
        rows = newRows != null ? newRows : new ArrayList<>();
        openDelayTicks = TITLE_HOLD_TICKS;
        titleHoldTicks = Math.max(titleHoldTicks, TITLE_HOLD_TICKS);
    }

    // PlayerClientEvents.victory/defeat: the big title is up - keep chat and the minimap off it
    public static void onTitleShown() {
        titleHoldTicks = TITLE_HOLD_TICKS;
    }

    // true from the victory/defeat title until the results popup is dismissed
    public static boolean isMatchEndMoment() {
        return titleHoldTicks > 0 || openDelayTicks > 0 || Minecraft.getInstance().screen instanceof MatchEndScreen;
    }

    // the enlarged chat log covered the "You are victorious!" title in playtests; still show it while typing
    public static boolean shouldHideChat() {
        return isMatchEndMoment() && !(Minecraft.getInstance().screen instanceof ChatScreen);
    }

    public static void dismiss() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchEndScreen) {
            // TopdownGuiClientEvents reopens the RTS screen a few ticks after no screen is open
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) return;
        if (titleHoldTicks > 0) titleHoldTicks -= 1;
        if (openDelayTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (openDelayTicks > 1) {
            openDelayTicks -= 1;
            return;
        }
        // don't yank the player out of the pause menu or chat - wait until they're back on the RTS view
        if (mc.screen != null && !(mc.screen instanceof TopdownGui)) return;
        openDelayTicks = -1;
        if (hasResults())
            mc.setScreen(new MatchEndScreen());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        gameDurationTicks = 0;
        rows = new ArrayList<>();
        openDelayTicks = -1;
        titleHoldTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchEndScreen) {
            mc.setScreen(null);
        }
    }
}
