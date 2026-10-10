package com.solegendary.reignofnether.hud.playerdisplay;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.RectZone;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerPanelClientboundPacket;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's player list: a compact top-right panel with one line per player in the match - you, then your allies, then
 * the enemies. Names are in the player's colour (team colours when that mode is on) with the faction icon in front.
 * Allied lines also show metal / energy income and the commander's health; enemy lines only the name and whether
 * they are still in the game. Fed every 2 s by PlayerPanelServerEvents, which leaves enemy economy off the wire.
 *
 * F4 toggles it (and F1 hides it with the rest of the HUD). The minimap sits bottom-right below it: when an 8v8
 * roster would run into it the panel lists what fits plus a "+N more" line, and in the strategic view (where the
 * map overview matters most) it steps aside entirely instead.
 */
public class PlayerPanelClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    static final int PANEL_W = 160;
    static final int ROW_H = 11;
    static final int TOP = 38;            // under the economy bar (EconomyBarRenderer.PANEL_H = 34)
    // clear of the right-edge button columns (help / diplomacy / chat at -28, beacon / gamerules at -56)
    static final int RIGHT_MARGIN = 60;
    static final int PAD = 3;
    static final int BG = 0xA0101216;
    static final int METAL_COL = 0xFFB4BEC8, ENERGY_COL = 0xFFF0C83C;   // as on the economy bar

    public static boolean enabled = true;

    /** A synced line plus its pre-formatted strings, so a frame draws without building any. */
    private record Line(PlayerPanelClientboundPacket.Entry e, ResourceLocation icon, String metal, String energy) {}

    private static List<Line> lines = List.of();
    private static int allyCount = 0;   // lines [0, allyCount) are you + allies
    private static RectZone zone = null;

    public static void sync(List<PlayerPanelClientboundPacket.Entry> entries) {
        List<Line> out = new ArrayList<>(entries.size());
        int allies = 0;
        for (var e : entries) {
            Faction f = Factions.getFaction(e.factionKey().isEmpty() ? null : ResourceLocation.tryParse(e.factionKey()));
            ResourceLocation icon = f == null || f == Factions.NONE ? null : f.icon;
            out.add(new Line(e, icon, "+" + fmt(e.metalIncome()), "+" + fmt(e.energyIncome())));
            if (e.hasAllyData())
                allies++;
        }
        lines = out;
        allyCount = allies;
    }

    public static void clear() {
        lines = List.of();
        allyCount = 0;
        zone = null;
    }

    static String fmt(float v) {
        if (v >= 1000)
            return String.format("%.1fk", v / 1000f);
        if (v >= 100)
            return String.valueOf(Math.round(v));
        return String.format("%.1f", v);
    }

    public static boolean isMouseOverHud(int mouseX, int mouseY) {
        return zone != null && zone.isMouseOver(mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onInput(InputEvent.Key evt) {
        if (evt.getAction() != GLFW.GLFW_PRESS || !OrthoviewClientEvents.isEnabled()
                || TextInputClientEvents.isAnyInputFocused())
            return;
        if (evt.getKey() == Keybindings.getFnum(4).getKey())
            enabled = !enabled;
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        zone = null;
        if (!enabled || !HudClientEvents.enabled || lines.isEmpty() || !OrthoviewClientEvents.isEnabled()
                || !(evt.getScreen() instanceof TopdownGui) || MC.level == null)
            return;
        int sw = MC.getWindow().getGuiScaledWidth();
        int sh = MC.getWindow().getGuiScaledHeight();
        int x = sw - RIGHT_MARGIN - PANEL_W;
        int y = TOP;
        boolean split = allyCount > 0 && allyCount < lines.size();
        int fullH = PAD * 2 + lines.size() * ROW_H + (split ? 3 : 0);

        // the minimap is anchored bottom-right and spans the panel's x range (diamond or square, both fit in 2R)
        int mapTop = sh - MinimapClientEvents.getMapGuiRadius() * 2 - MinimapClientEvents.CORNER_OFFSET
            - MinimapClientEvents.BG_OFFSET - 2;
        int shown = lines.size();
        if (y + fullH > mapTop) {
            if (StrategicViewClientEvents.isStrategicView())
                return;
            // keep a line free for "+N more"
            shown = Math.max(1, (mapTop - y - PAD * 2 - (split ? 3 : 0)) / ROW_H - 1);
            if (shown >= lines.size())
                shown = lines.size();
        }
        boolean more = shown < lines.size();
        int h = PAD * 2 + (shown + (more ? 1 : 0)) * ROW_H + (split && shown > allyCount ? 3 : 0);

        GuiGraphics gg = evt.getGuiGraphics();
        Font font = MC.font;
        gg.fill(x, y, x + PANEL_W, y + h, BG);
        zone = RectZone.getZoneByLW(x, y, PANEL_W, h);

        int ry = y + PAD;
        for (int i = 0; i < shown; i++) {
            if (split && i == allyCount) {
                gg.fill(x + 2, ry, x + PANEL_W - 2, ry + 1, 0x60FFFFFF);   // team | enemies
                ry += 3;
            }
            renderLine(gg, font, lines.get(i), x, ry, i == 0 && lines.get(i).e().hasAllyData());
            ry += ROW_H;
        }
        if (more)
            gg.drawString(font, "+" + (lines.size() - shown) + " more (F4 hides)", x + 4, ry + 1, 0xFFAAAAAA);
    }

    private static void renderLine(GuiGraphics gg, Font font, Line line, int x, int y, boolean self) {
        var e = line.e();
        if (self)
            gg.fill(x + 1, y - 1, x + PANEL_W - 1, y + ROW_H - 1, 0x30FFFFFF);
        if (line.icon() != null)
            MyRenderer.renderIcon(gg, line.icon(), x + 3, y, 8);
        int nameColour = e.alive() ? 0xFF000000 | PlayerColors.getPlayerDisplayColorHex(e.name()) : 0xFF707070;
        int nameW = e.hasAllyData() ? 54 : PANEL_W - 14 - 40;
        String name = font.width(e.name()) > nameW ? font.plainSubstrByWidth(e.name(), nameW - 4) + ".." : e.name();
        gg.drawString(font, name, x + 13, y + 1, nameColour);
        if (!e.alive()) {
            gg.fill(x + 13, y + 4, x + 13 + font.width(name), y + 5, 0xFF909090);   // struck through
            String s = "defeated";
            gg.drawString(font, s, x + PANEL_W - 4 - font.width(s), y + 1, 0xFF905050);
            return;
        }
        if (!e.hasAllyData()) {
            String s = "alive";
            gg.drawString(font, s, x + PANEL_W - 4 - font.width(s), y + 1, 0xFF60C060);
            return;
        }
        gg.drawString(font, line.metal(), x + 70, y + 1, METAL_COL);
        gg.drawString(font, line.energy(), x + 100, y + 1, ENERGY_COL);
        // commander health: a small bar, grey when the commander is gone
        int bx = x + 130, bw = PANEL_W - 4 - 130, by = y + 3;
        gg.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF000000);
        if (e.commanderHp() < 0) {
            gg.fill(bx, by, bx + bw, by + 3, 0xFF404040);
        } else {
            float hp = Math.min(1f, e.commanderHp());
            int col = hp > 0.6f ? 0xFF40D040 : hp > 0.3f ? 0xFFE0C030 : 0xFFE04030;
            gg.fill(bx, by, bx + Math.max(1, Math.round(bw * hp)), by + 3, col);
        }
    }
}
