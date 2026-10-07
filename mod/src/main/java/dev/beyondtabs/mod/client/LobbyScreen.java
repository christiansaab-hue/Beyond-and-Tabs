package dev.beyondtabs.mod.client;

import dev.beyondtabs.engine.gen.RaceDef;
import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.Lobby;
import dev.beyondtabs.mod.LobbyAction;
import dev.beyondtabs.mod.Network;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Skirmish setup, Generals / Tiberium Wars style: eight slots (player, faction, colour, team, start position), the
 * battlefield and its size, and a map preview where you can click your start position. Left-click a field to step
 * forward, right-click to step back. The host sets up AIs and the map; everyone picks their own faction and colour.
 */
public final class LobbyScreen extends Screen {
    static final int BG = 0xE0101418, PANEL = 0xF0161C22, ROW = 0xFF1E262E, ROW_ME = 0xFF243A2C, EDGE = 0xFF3A4652, GOLD = 0xFFE2B43C,
            TXT = 0xFFE8E2D4, DIM = 0xFF9AA4AE, OFF = 0xFF5A646E;

    record Hit(int x, int y, int w, int h, Runnable left, Runnable right, String tip) { }
    final List<Hit> hits = new ArrayList<>();

    public LobbyScreen() { super(Component.literal("Beyond & TABS - Battle Setup")); }

    @Override public boolean isPauseScreen() { return false; }

    static ResourceLocation emblem(String race) { return new ResourceLocation(BeyondTabs.MODID, "textures/gui/emblem_" + race + ".png"); }

    static void send(LobbyAction.Op op, int slot, LobbyAction.Field f, int v) { Network.send(new LobbyAction(op, slot, f, v)); }

    UUID me() { return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        hits.clear();
        renderBackground(g);
        Lobby lb = ClientLobby.lobby;
        int pw = Math.min(width - 16, 520), ph = Math.min(height - 16, 300), x0 = (width - pw) / 2, y0 = (height - ph) / 2;
        g.fill(x0, y0, x0 + pw, y0 + ph, PANEL); outline(g, x0, y0, pw, ph, EDGE);
        g.fill(x0, y0, x0 + pw, y0 + 20, 0xFF0C1014); g.hLine(x0, x0 + pw - 1, y0 + 20, GOLD);
        g.drawString(font, "BEYOND & TABS  —  BATTLE SETUP", x0 + 8, y0 + 6, GOLD);
        if (lb == null) { g.drawCenteredString(font, "Waiting for the server...", width / 2, height / 2, DIM); super.render(g, mx, my, partial); return; }
        UUID me = me();
        boolean host = me != null && me.equals(lb.host);
        int mySlot = lb.slotOf(me);
        String hostName = "-";
        for (Lobby.Slot s : lb.slots) if (s.player != null && s.player.equals(lb.host)) hostName = s.name;
        g.drawString(font, "Host: " + hostName + (host ? " (you)" : ""), x0 + pw - 8 - font.width("Host: " + hostName + (host ? " (you)" : "")), y0 + 6, DIM);

        // ---- slots
        int tx = x0 + 8, ty = y0 + 28, rowH = 22;
        int cPlayer = tx, cFaction = tx + 128, cColor = tx + 262, cTeam = tx + 300, cSpawn = tx + 334, tableW = 362;
        g.drawString(font, "Player", cPlayer + 4, ty, DIM); g.drawString(font, "Faction", cFaction + 4, ty, DIM);
        g.drawString(font, "Colour", cColor, ty, DIM); g.drawString(font, "Team", cTeam + 2, ty, DIM); g.drawString(font, "Start", cSpawn, ty, DIM);
        ty += 11;
        List<RaceDef> races = Lobby.playable();
        for (int i = 0; i < Lobby.SLOTS; i++) {
            Lobby.Slot s = lb.slots[i]; final int slot = i;
            int y = ty + i * rowH;
            boolean mine = i == mySlot, active = s.type == Lobby.HUMAN || s.type == Lobby.AI, canEdit = !lb.started && (host || mine);
            g.fill(tx, y, tx + tableW, y + rowH - 2, mine ? ROW_ME : ROW);
            // player / slot type
            String who = switch (s.type) {
                case Lobby.HUMAN -> s.name + (s.player != null && s.player.equals(lb.host) ? "  ★" : "");
                case Lobby.AI -> Lobby.AI_NAMES[Math.max(0, Math.min(2, s.ai))];
                case Lobby.OPEN -> "Open";
                default -> "Closed";
            };
            int whoColor = s.type == Lobby.CLOSED ? OFF : s.type == Lobby.OPEN ? DIM : TXT;
            g.drawString(font, who, cPlayer + 4, y + 6, whoColor);
            if (!lb.started && host && s.type != Lobby.HUMAN)
                hit(cPlayer, y, 124, rowH - 2, () -> cycleType(slot, s, 1), () -> cycleType(slot, s, -1), "Click: Open -> Easy/Normal/Hard AI -> Closed");
            if (!lb.started && s.type == Lobby.OPEN && !mine) {
                int bx = cPlayer + 90; smallButton(g, bx, y + 3, 32, 14, "Join", mx, my);
                hit(bx, y + 3, 32, 14, () -> send(LobbyAction.Op.JOIN, slot, LobbyAction.Field.TYPE, 0), null, "Take this seat");
            }
            if (!active) continue;
            // faction
            String raceId = s.race >= 0 && s.race < races.size() ? races.get(s.race).id() : null;
            if (raceId != null) g.blit(emblem(raceId), cFaction + 2, y + 2, 16, 16, 0, 0, 64, 64, 64, 64);
            else { g.fill(cFaction + 2, y + 2, cFaction + 18, y + 18, 0xFF2A323A); g.drawCenteredString(font, "?", cFaction + 10, y + 6, GOLD); }
            String rn = raceId == null ? "Random" : races.get(s.race).name();
            g.drawString(font, rn, cFaction + 22, y + 6, canEdit ? TXT : DIM);
            if (canEdit) {
                int n = races.size();
                hit(cFaction, y, 130, rowH - 2, () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.RACE, Math.floorMod(s.race + 2, n + 1) - 1),
                        () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.RACE, Math.floorMod(s.race, n + 1) - 1),
                        raceId == null ? "Random faction" : races.get(s.race).name() + "\n" + races.get(s.race).tagline());
            }
            // colour
            int col = 0xFF000000 | Lobby.COLORS[Math.max(0, Math.min(Lobby.COLORS.length - 1, s.color))];
            g.fill(cColor + 2, y + 3, cColor + 30, y + 17, col); outline(g, cColor + 2, y + 3, 28, 14, 0xFF000000);
            if (canEdit) hit(cColor, y, 34, rowH - 2, () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.COLOR, lb.nextColor(s.color, 1, slot)),
                    () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.COLOR, lb.nextColor(s.color, -1, slot)), Lobby.COLOR_NAMES[Math.max(0, Math.min(Lobby.COLOR_NAMES.length - 1, s.color))]);
            // team
            g.drawCenteredString(font, s.team == 0 ? "-" : String.valueOf(s.team), cTeam + 14, y + 6, s.team == 0 ? DIM : GOLD);
            if (canEdit) hit(cTeam, y, 30, rowH - 2, () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.TEAM, s.team + 1),
                    () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.TEAM, s.team - 1), "Same number = allies. '-' = everyone for themselves");
            // start position
            g.drawCenteredString(font, s.spawn < 0 ? "?" : String.valueOf(s.spawn + 1), cSpawn + 12, y + 6, s.spawn < 0 ? DIM : TXT);
            if (canEdit) hit(cSpawn, y, 28, rowH - 2, () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.SPAWN, nextSpawn(lb, slot, s.spawn, 1)),
                    () -> send(LobbyAction.Op.SET, slot, LobbyAction.Field.SPAWN, nextSpawn(lb, slot, s.spawn, -1)), "Start position ('?' = random). You can also click the map.");
        }

        // ---- battlefield
        int mxp = x0 + tableW + 20, mw = pw - tableW - 28, my0 = y0 + 28;
        g.drawString(font, "Battlefield", mxp, my0, DIM);
        int msz = Math.min(mw, 120), mcx = mxp + mw / 2, mcy = my0 + 14 + msz / 2;
        g.fill(mcx - msz / 2, mcy - msz / 2, mcx + msz / 2, mcy + msz / 2, mapColor(lb.map)); outline(g, mcx - msz / 2, mcy - msz / 2, msz, msz, EDGE);
        for (int p = 0; p < Lobby.POSITIONS; p++) {
            double a = 2 * Math.PI * p / Lobby.POSITIONS;
            int px = mcx + (int) (Math.sin(a) * msz * .38), py = mcy + (int) (Math.cos(a) * msz * .38);
            int owner = -1; for (int i = 0; i < Lobby.SLOTS; i++) if (lb.slots[i].spawn == p && (lb.slots[i].type == Lobby.HUMAN || lb.slots[i].type == Lobby.AI)) owner = i;
            int c = owner >= 0 ? 0xFF000000 | Lobby.COLORS[lb.slots[owner].color] : 0xFF2A323A;
            g.fill(px - 7, py - 7, px + 7, py + 7, c); outline(g, px - 7, py - 7, 14, 14, owner == mySlot && owner >= 0 ? 0xFFFFFFFF : 0xFF000000);
            g.drawCenteredString(font, String.valueOf(p + 1), px, py - 3, owner >= 0 ? 0xFFFFFFFF : DIM);
            final int pos = p;
            if (!lb.started && mySlot >= 0) hit(px - 7, py - 7, 14, 14, () -> send(LobbyAction.Op.SET, mySlot, LobbyAction.Field.SPAWN, pos),
                    () -> send(LobbyAction.Op.SET, mySlot, LobbyAction.Field.SPAWN, -1), "Start here (right-click: random)");
        }
        g.fill(mcx - 3, mcy - 3, mcx + 3, mcy + 3, 0xFFB8C4D0);   // contested middle
        int by = mcy + msz / 2 + 6;
        field(g, mxp, by, mw, "Map: " + Lobby.MAP_NAMES[lb.map], host && !lb.started,
                () -> Network.send(new LobbyAction(LobbyAction.Op.MAP, -1, LobbyAction.Field.TYPE, lb.map + 1)),
                () -> Network.send(new LobbyAction(LobbyAction.Op.MAP, -1, LobbyAction.Field.TYPE, lb.map - 1)), mx, my,
                lb.map == 0 ? "Fight where the host is standing" : "The battle moves to the nearest " + Lobby.MAP_NAMES[lb.map]);
        field(g, mxp, by + 20, mw, "Size: " + Lobby.SIZE_NAMES[lb.size], host && !lb.started,
                () -> Network.send(new LobbyAction(LobbyAction.Op.SIZE, -1, LobbyAction.Field.TYPE, lb.size + 1)),
                () -> Network.send(new LobbyAction(LobbyAction.Op.SIZE, -1, LobbyAction.Field.TYPE, lb.size - 1)), mx, my,
                String.format("Bases %d blocks from the middle", (int) Lobby.SIZE_RADIUS[lb.size]));
        int players = lb.activeCount(), humans = 0; for (Lobby.Slot s : lb.slots) if (s.type == Lobby.HUMAN) humans++;
        g.drawString(font, players + " armies, " + humans + " human" + (humans == 1 ? "" : "s"), mxp, by + 44, DIM);

        // ---- bottom bar
        int bby = y0 + ph - 26;
        g.hLine(x0, x0 + pw - 1, bby - 6, EDGE);
        if (lb.started) {
            g.drawString(font, "A battle is in progress.", x0 + 10, bby + 4, GOLD);
            bigButton(g, x0 + pw - 250, bby, 120, 18, "Watch / play (V)", true, mx, my, () -> { onClose(); RtsClient.toggle(); });
            bigButton(g, x0 + pw - 124, bby, 116, 18, "Back to lobby", host, mx, my, () -> Network.send(new LobbyAction(LobbyAction.Op.RETURN)));
        } else {
            g.drawString(font, "Left-click to change, right-click to go back.", x0 + 10, bby + 4, DIM);
            if (mySlot >= 0) bigButton(g, x0 + pw - 330, bby, 80, 18, "Spectate", true, mx, my, () -> Network.send(new LobbyAction(LobbyAction.Op.LEAVE)));
            bigButton(g, x0 + pw - 246, bby, 70, 18, "Close", true, mx, my, this::onClose);
            bigButton(g, x0 + pw - 172, bby, 164, 18, host ? (players >= 2 ? "START BATTLE" : "Add an opponent") : "Waiting for host", host && players >= 2, mx, my,
                    () -> Network.send(new LobbyAction(LobbyAction.Op.START)));
        }
        // tooltip
        for (Hit h : hits) if (h.tip != null && mx >= h.x && my >= h.y && mx < h.x + h.w && my < h.y + h.h) { tooltip(g, h.tip, mx, my); break; }
        super.render(g, mx, my, partial);
    }

    /** Open -> Easy AI -> Normal AI -> Hard AI -> Closed -> Open (host only). */
    void cycleType(int slot, Lobby.Slot s, int step) {
        int state = s.type == Lobby.OPEN ? 0 : s.type == Lobby.AI ? 1 + Math.max(0, Math.min(2, s.ai)) : 4;
        int next = Math.floorMod(state + step, 5);
        if (next == 0) send(LobbyAction.Op.SET, slot, LobbyAction.Field.TYPE, Lobby.OPEN);
        else if (next == 4) send(LobbyAction.Op.SET, slot, LobbyAction.Field.TYPE, Lobby.CLOSED);
        else {
            if (s.type != Lobby.AI) send(LobbyAction.Op.SET, slot, LobbyAction.Field.TYPE, Lobby.AI);
            send(LobbyAction.Op.SET, slot, LobbyAction.Field.AI, next - 1);
        }
    }

    static int nextSpawn(Lobby lb, int slot, int from, int step) {
        for (int k = 1; k <= Lobby.POSITIONS + 1; k++) {
            int p = Math.floorMod(from + 1 + step * k, Lobby.POSITIONS + 1) - 1;   // -1 (random) .. 7
            if (lb.spawnFree(p, slot)) return p;
        }
        return from;
    }

    static int mapColor(int map) {
        return switch (map) {
            case 2 -> 0xFF9A8A5A; case 3 -> 0xFFB8C4D0; case 4 -> 0xFF2E4A26; case 5 -> 0xFF8A8A44; case 6 -> 0xFF8A4A2E; case 7 -> 0xFF4E7A3A;
            default -> 0xFF3E6A30;
        };
    }

    void hit(int x, int y, int w, int h, Runnable left, Runnable right, String tip) { hits.add(new Hit(x, y, w, h, left, right, tip)); }

    void field(GuiGraphics g, int x, int y, int w, String label, boolean enabled, Runnable left, Runnable right, int mx, int my, String tip) {
        boolean over = mx >= x && my >= y && mx < x + w && my < y + 16;
        g.fill(x, y, x + w, y + 16, enabled && over ? 0xFF2E3A46 : 0xFF222A32); outline(g, x, y, w, 16, EDGE);
        g.drawString(font, (enabled ? "◀ " : "") + label + (enabled ? " ▶" : ""), x + 5, y + 4, enabled ? TXT : DIM);
        hit(x, y, w, 16, enabled ? left : null, enabled ? right : null, tip);
    }

    void smallButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my) {
        boolean over = mx >= x && my >= y && mx < x + w && my < y + h;
        g.fill(x, y, x + w, y + h, over ? 0xFF3E6A48 : 0xFF2A4030); outline(g, x, y, w, h, 0xFF7CDC7C);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TXT);
    }

    void bigButton(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, int mx, int my, Runnable r) {
        boolean over = enabled && mx >= x && my >= y && mx < x + w && my < y + h;
        g.fill(x, y, x + w, y + h, !enabled ? 0xFF20262C : over ? 0xFF5A4A1E : 0xFF3A3220); outline(g, x, y, w, h, enabled ? GOLD : EDGE);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFE6A0 : OFF);
        if (enabled) hit(x, y, w, h, r, null, null);
    }

    void tooltip(GuiGraphics g, String tip, int mx, int my) {
        String[] lines = tip.split("\n");
        int tw = 0; for (String l : lines) tw = Math.max(tw, font.width(l));
        int h = lines.length * 10 + 6, x = Math.min(mx + 10, width - tw - 14), y = Math.max(2, my - h - 4);
        g.fill(x, y, x + tw + 10, y + h, 0xF0101418); outline(g, x, y, tw + 10, h, EDGE);
        for (int i = 0; i < lines.length; i++) g.drawString(font, lines[i], x + 5, y + 4 + i * 10, i == 0 ? TXT : DIM);
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int c) { g.hLine(x, x + w - 1, y, c); g.hLine(x, x + w - 1, y + h - 1, c); g.vLine(x, y, y + h - 1, c); g.vLine(x + w - 1, y, y + h - 1, c); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {   // last drawn on top
            Hit h = hits.get(i);
            if (mx >= h.x && my >= h.y && mx < h.x + h.w && my < h.y + h.h) {
                Runnable r = button == 1 ? h.right : button == 0 ? h.left : null;
                if (r != null) { r.run(); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
