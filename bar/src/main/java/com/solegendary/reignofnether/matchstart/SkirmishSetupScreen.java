package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The skirmish lobby, laid out like Beyond All Reason's battle room: a navigation column on the left, the two
 * teams in the middle (every player and AI is a row with its colour, name, faction and skill; "Add AI" fills a
 * team, "Join" moves you), and on the right a map preview with the match settings. Choices persist for the
 * session so a rematch is one click. Teams are alliances: everyone on your team is your ally in the match.
 */
public class SkirmishSetupScreen extends Screen {
    // factions are chosen by packet code (SkirmishServerboundPacket.CODE_PATHS: 3 stays "Random", the Court took 4,
    // the Tidewrought 5) through the shared FactionPicker dropdown, which lists every coded faction
    static final List<String> DIFFICULTIES = List.of("Easy", "Medium", "Hard");
    static final List<String> ARENAS = List.of("Small", "Medium", "Large", "Huge", "Random");
    static final List<String> METALS = List.of("Lean", "Normal", "Rich", "Random");
    static final List<String> DISTANCES = List.of("Close", "Normal", "Far");
    static final int MAX_PER_TEAM = 8;

    /** One AI slot in a team. */
    public static class Bot {
        public int faction = 3, difficulty = 1, colour = -1, team;
        Bot(int team) { this.team = team; }
    }

    /** Everything the lobby decides; codes match SkirmishServerboundPacket. */
    public static class Settings {
        public int faction = 0;          // 0 Sunforged (villagers), 1 Gravebound (monsters), 2 Ironhide (piglins), 3 random, 4 Verdant, 5 Tidewrought
        public int colour = 1;           // index into PlayerColors.colors (0..PLAYER_COLOR_COUNT-1), or -1 random
        public int team = 0;             // 0 = Team 1, 1 = Team 2
        public final List<Bot> bots = new ArrayList<>();
        public int arena = 4;            // 0 small .. 3 huge, 4 random
        public int metal = 3;            // 0 lean, 1 normal, 2 rich, 3 random
        public int spawnDistance = 1;    // 0 close, 1 normal, 2 far

        Settings() {
            bots.add(new Bot(1));        // a 1v1 by default
        }

        public int colorMapId() {
            int idx = colour >= 0 ? colour : (int) (Math.random() * PlayerColors.PLAYER_COLOR_COUNT);
            return PlayerColors.colors[idx % PlayerColors.PLAYER_COLOR_COUNT].mapColorId;
        }

        /** A bot's requested colour as a map colour id, 0 for auto; a bot never shares the player's colour. */
        public int botColorMapId(Bot b) {
            if (b.colour < 0 || b.colour == colour)
                return 0;
            return PlayerColors.colors[b.colour % PlayerColors.PLAYER_COLOR_COUNT].mapColorId;
        }

        int teamSize(int team) {
            int n = this.team == team ? 1 : 0;
            for (Bot b : bots)
                if (b.team == team)
                    n++;
            return n;
        }
    }

    static final Settings settings = new Settings();   // remembered between lobbies

    static final int BG = 0xE0101216, PANEL = 0xC0181B20, EDGE = 0xFF3A3F46, GOLD = 0xFFD27A, DIM = 0x9A9A9A;
    static final int ROW_H = 22, SWATCH = 12, PAL_SWATCH = 15, PAL_GAP = 2, PAL_PER_ROW = 8;

    final Screen parent;
    // layout computed in init()
    int navX, navW, midX, midW, rightX, rightW, top, bottom;
    int team1Y, team2Y;
    /** Rows whose colour swatch can be clicked: x, y of the swatch and the bot (or PLAYER). */
    final List<Object[]> swatches = new ArrayList<>();
    /** The row whose colour palette popup is open: a Bot, or {@code PLAYER} for the player, or null. */
    Object paletteFor = null;
    static final Object PLAYER = new Object();
    int palX, palY;
    final FactionPicker picker = new FactionPicker();

    public SkirmishSetupScreen(Screen parent) {
        super(Component.translatable("quickbattle.reignofnether.title"));
        this.parent = parent;
    }

    static List<Integer> range(int n) {
        return IntStream.range(0, n).boxed().toList();
    }

    static Component colourName(int idx) {
        if (idx < 0)
            return Component.literal("Random");
        String n = PlayerColors.colors[idx].name.replace('_', ' ');
        return Component.literal(Character.toUpperCase(n.charAt(0)) + n.substring(1));
    }

    @Override
    protected void init() {
        swatches.clear();
        factionIcons.clear();
        picker.close();
        int totalW = Math.min(width - 16, 620);
        int left = width / 2 - totalW / 2;
        navW = 84;
        rightW = 150;
        midW = totalW - navW - rightW - 16;
        navX = left;
        midX = navX + navW + 8;
        rightX = midX + midW + 8;
        top = Math.max(26, height / 2 - 150);
        bottom = Math.min(height - 12, top + 300);

        // --- left navigation (BAR's side menu) ---
        int y = top + 4;
        Button skirmish = addRenderableWidget(Button.builder(Component.literal("Skirmish"), b -> { })
            .bounds(navX + 4, y, navW - 8, 20).build());
        skirmish.active = false;
        y += 24;
        Button mp = addRenderableWidget(Button.builder(Component.literal("Multiplayer"), b -> { })
            .bounds(navX + 4, y, navW - 8, 20).build());
        mp.active = false;
        mp.setTooltip(Tooltip.create(Component.literal("Coming once the single-player game is done")));
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Load Game"),
            b -> Minecraft.getInstance().setScreen(new SelectWorldScreen(this)))
            .bounds(navX + 4, y, navW - 8, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(parent))
            .bounds(navX + 4, bottom - 24, navW - 8, 20).build());

        // --- the two teams ---
        int ty = top + 4;
        team1Y = ty;
        ty = buildTeam(0, ty);
        ty += 6;
        team2Y = ty;
        buildTeam(1, ty);

        // --- right column: map preview (drawn in render) then the settings ---
        int ry = top + 4 + 96;
        int rw = rightW - 8;
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(ARENAS.get(i)))
            .withValues(range(5)).withInitialValue(settings.arena)
            .create(rightX + 4, ry, rw, 20, Component.literal("Map"), (b, v) -> settings.arena = v));
        ry += 22;
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(METALS.get(i)))
            .withValues(range(4)).withInitialValue(settings.metal)
            .create(rightX + 4, ry, rw, 20, Component.literal("Metal"), (b, v) -> settings.metal = v));
        ry += 22;
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(DISTANCES.get(i)))
            .withValues(range(3)).withInitialValue(settings.spawnDistance)
            .create(rightX + 4, ry, rw, 20, Component.literal("Spawns"), (b, v) -> settings.spawnDistance = v));

        addRenderableWidget(Button.builder(Component.literal("Start Battle"), b -> QuickBattle.launch(this, settings))
            .bounds(rightX + 4, bottom - 24, rw, 20).build());
    }

    /** Lays out one team box: header with Add AI / Join, then a row per member. Returns the y below it. */
    int buildTeam(int team, int y) {
        int x = midX + 4, w = midW - 8;
        boolean full = settings.teamSize(team) >= MAX_PER_TEAM;
        Button add = addRenderableWidget(Button.builder(Component.literal("Add AI"), b -> {
            settings.bots.add(new Bot(team));
            rebuild();
        }).bounds(x + w - 110, y, 52, 16).build());
        add.active = !full;
        Button join = addRenderableWidget(Button.builder(Component.literal("Join"), b -> {
            settings.team = team;
            rebuild();
        }).bounds(x + w - 54, y, 54, 16).build());
        join.active = settings.team != team && !full;
        y += 20;

        if (settings.team == team) {
            swatches.add(new Object[]{ x + 4, y + 5, PLAYER });
            addFactionButton(x, w, y, settings.faction, v -> settings.faction = v);
            y += ROW_H;
        }
        List<Bot> teamBots = settings.bots.stream().filter(b -> b.team == team).toList();
        for (Bot bot : teamBots) {
            swatches.add(new Object[]{ x + 4, y + 5, bot });
            addFactionButton(x, w, y, bot.faction, v -> bot.faction = v);
            addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(DIFFICULTIES.get(i)))
                .withValues(range(3)).withInitialValue(bot.difficulty)
                .create(x + w - 68, y, 50, ROW_H - 2, Component.literal(""), (b, v) -> bot.difficulty = v));
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                settings.bots.remove(bot);
                rebuild();
            }).bounds(x + w - 16, y, 16, ROW_H - 2).build());
            y += ROW_H;
        }
        if (settings.team != team && teamBots.isEmpty())
            y += 12;
        return y + 4;
    }

    // the faction button sits left of the difficulty cycler; up to 104 px (the old cycler was 80) so full names fit
    static final int FACTION_BTN_MAX_W = 104, FACTION_BTN_MIN_W = 60;

    /** The display faction for a code; an unknown code shows as Random, which is what the server makes of it. */
    static Faction factionForCode(int code) {
        Faction f = SkirmishServerboundPacket.factionForCode(code);
        return f == Factions.NONE ? Factions.RANDOM : f;
    }

    /**
     * A row's faction choice: a button showing the faction (icon drawn in render) that opens the faction list. Enter
     * on the focused button opens it too, so the screen stays usable from the keyboard (Tab, Enter, arrows, Enter).
     */
    void addFactionButton(int rowX, int rowW, int by, int code, java.util.function.IntConsumer set) {
        // right edge stays left of the difficulty cycler (x + w - 68); narrower rows (GUI scale 3 on small screens)
        // shrink the button before it can run into the row's name
        int bw = Math.max(FACTION_BTN_MIN_W, Math.min(FACTION_BTN_MAX_W, rowW - 72 - 90));
        int bx = rowX + rowW - 72 - bw;
        Faction shown = factionForCode(code);
        addRenderableWidget(Button.builder(Component.literal(MiscUtil.getFactionName(shown)), b -> {
            paletteFor = null;
            picker.open(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), width, height,
                FactionPicker.skirmishEntries(), shown, f -> {
                    int c = SkirmishServerboundPacket.codeOf(f);
                    set.accept(c >= 0 ? c : SkirmishServerboundPacket.RANDOM_CODE);
                    rebuild();
                    // keep keyboard focus on this row's button after the rebuild
                    for (var child : children())
                        if (child instanceof Button nb && nb.getX() == bx && nb.getY() == by)
                            setFocused(nb);
                });
        }).bounds(bx, by, bw, ROW_H - 2).build());
        factionIcons.add(new Object[]{ bx, by, shown });
    }

    /** Faction icons drawn on the left of each faction button: x, y of the button and the faction. */
    final List<Object[]> factionIcons = new ArrayList<>();

    void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        // while the faction list is open nothing beneath it may light up or show a tooltip
        int realMx = mx, realMy = my;
        if (picker.isOpen()) {
            mx = -10000;
            my = -10000;
        }
        renderBackground(gg);
        gg.fill(navX - 4, top - 4, rightX + rightW + 4, bottom + 4, BG);
        gg.fill(navX - 4, top - 5, rightX + rightW + 4, top - 4, EDGE);
        gg.fill(navX - 4, bottom + 4, rightX + rightW + 4, bottom + 5, EDGE);
        gg.drawString(font, "SKIRMISH", navX, top - 16, GOLD);
        gg.drawString(font, "Teams are alliances - everyone on your team fights with you. Click a swatch to pick a colour.",
            navX, bottom + 8, DIM, false);

        gg.fill(navX, top, navX + navW, bottom, PANEL);
        gg.fill(midX, top, midX + midW, bottom, PANEL);
        gg.fill(rightX, top, rightX + rightW, bottom, PANEL);

        renderTeam(gg, 0, team1Y);
        renderTeam(gg, 1, team2Y);
        renderMapPreview(gg, rightX + 4, top + 4, rightW - 8, 92);

        super.render(gg, mx, my, pt);

        // each faction button's icon, just left of it (vanilla buttons centre their label, so outside keeps it clear)
        for (Object[] fi : factionIcons)
            FactionPicker.renderIcon(gg, (Faction) fi[2], (Integer) fi[0] - 14, (Integer) fi[1] + (ROW_H - 2 - 12) / 2, 12, false);

        if (paletteFor != null) {
            int cols = PAL_PER_ROW, rows = (PlayerColors.PLAYER_COLOR_COUNT + cols - 1) / cols;
            int pw = cols * (PAL_SWATCH + PAL_GAP) + 6, ph = rows * (PAL_SWATCH + PAL_GAP) + 30;
            gg.fill(palX - 1, palY - 1, palX + pw + 1, palY + ph + 1, EDGE);
            gg.fill(palX, palY, palX + pw, palY + ph, 0xF0101216);
            gg.drawString(font, "Pick a colour", palX + 4, palY + 4, 0xE0E0E0, false);
            int current = paletteFor == PLAYER ? settings.colour : ((Bot) paletteFor).colour;
            for (int i = 0; i < PlayerColors.PLAYER_COLOR_COUNT; i++) {
                int sx = palX + 3 + (i % cols) * (PAL_SWATCH + PAL_GAP);
                int sy = palY + 16 + (i / cols) * (PAL_SWATCH + PAL_GAP);
                boolean hover = mx >= sx && mx < sx + PAL_SWATCH && my >= sy && my < sy + PAL_SWATCH;
                int frame = i == current ? 0xFFFFFFFF : hover ? 0xFFAAAAAA : 0xFF000000;
                gg.fill(sx - 1, sy - 1, sx + PAL_SWATCH + 1, sy + PAL_SWATCH + 1, frame);
                gg.fill(sx, sy, sx + PAL_SWATCH, sy + PAL_SWATCH, 0xFF000000 | PlayerColors.colors[i].hexCode);
            }
            gg.drawString(font, "right-click: random/auto", palX + 4, palY + ph - 11, DIM, false);
        }

        picker.render(gg, font, realMx, realMy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (picker.mouseScrolled(mx, my, delta))
            return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // the open list swallows every key, Escape included (it closes the list, not the screen)
        if (picker.keyPressed(keyCode))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    void renderTeam(GuiGraphics gg, int team, int y) {
        int x = midX + 4, w = midW - 8;
        String title = "Team " + (team + 1) + "   " + settings.teamSize(team) + "/" + MAX_PER_TEAM;
        gg.drawString(font, title, x, y + 4, GOLD, false);
        gg.fill(x, y + 17, x + w, y + 18, EDGE);
        y += 20;
        if (settings.team == team) {
            renderRow(gg, x, y, w, settings.colour, Minecraft.getInstance().getUser().getName() + "  (you)", true);
            y += ROW_H;
        }
        for (Bot bot : settings.bots)
            if (bot.team == team) {
                renderRow(gg, x, y, w, bot.colour, "AI", false);
                y += ROW_H;
            }
    }

    void renderRow(GuiGraphics gg, int x, int y, int w, int colour, String name, boolean me) {
        gg.fill(x, y, x + w, y + ROW_H - 2, me ? 0x40FFD27A : 0x30000000);
        int sx = x + 4, sy = y + 5;
        gg.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 0xFF000000);
        if (colour >= 0) {
            gg.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | PlayerColors.colors[colour].hexCode);
        } else {
            gg.fill(sx, sy, sx + 6, sy + 6, 0xFFA12722);
            gg.fill(sx + 6, sy, sx + 12, sy + 6, 0xFF35399D);
            gg.fill(sx, sy + 6, sx + 6, sy + 12, 0xFFF8C627);
            gg.fill(sx + 6, sy + 6, sx + 12, sy + 12, 0xFF70B919);
        }
        gg.drawString(font, name, x + 22, y + 6, 0xFFFFFF, false);
    }

    /** A diagram of the match: the ring wall, a base dot per player, patches as specks. */
    void renderMapPreview(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, 0xFF1E3A1E);
        gg.fill(x, y, x + w, y + 1, EDGE);
        gg.fill(x, y + h - 1, x + w, y + h, EDGE);
        gg.fill(x, y, x + 1, y + h, EDGE);
        gg.fill(x + w - 1, y, x + w, y + h, EDGE);
        int cx = x + w / 2, cy = y + h / 2 - 4;
        float scale = switch (settings.arena) { case 0 -> 0.55f; case 1 -> 0.7f; case 2 -> 0.85f; case 3 -> 1f; default -> 0.8f; };
        int r = (int) (Math.min(w, h) / 2 * 0.9f * scale);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            int px = cx + (int) (Math.cos(a) * r), py = cy + (int) (Math.sin(a) * r * 0.78f);
            gg.fill(px, py, px + 2, py + 2, 0xFF8A8A7A);
        }
        drawBases(gg, cx, cy, r, Math.PI, 0);
        drawBases(gg, cx, cy, r, 0, 1);
        int patches = switch (settings.metal) { case 0 -> 6; case 1 -> 10; case 2 -> 16; default -> 10; };
        java.util.Random rng = new java.util.Random(settings.arena * 31L + settings.metal);
        for (int i = 0; i < patches; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = 0.2 + rng.nextDouble() * 0.6;
            int px = cx + (int) (Math.cos(a) * r * d), py = cy + (int) (Math.sin(a) * r * 0.78f * d);
            gg.fill(px, py, px + 1, py + 1, 0xFFC9B48A);
        }
        String label = ARENAS.get(settings.arena) + " - " + METALS.get(settings.metal) + " metal";
        gg.drawString(font, label, x + 3, y + h - 10, 0xC8D0C8, false);
    }

    void drawBases(GuiGraphics gg, int cx, int cy, int r, double centreAngle, int team) {
        List<Integer> colours = new ArrayList<>();
        if (settings.team == team)
            colours.add(settings.colour);
        for (Bot b : settings.bots)
            if (b.team == team)
                colours.add(b.colour);
        int n = colours.size();
        if (n == 0)
            return;
        double spread = Math.min(Math.PI * 0.8, 0.35 * n);
        for (int idx = 0; idx < n; idx++) {
            int c = colours.get(idx);
            double a = centreAngle + (n == 1 ? 0 : -spread / 2 + spread * idx / (n - 1));
            int px = cx + (int) (Math.cos(a) * r * 0.72), py = cy + (int) (Math.sin(a) * r * 0.78f * 0.72);
            int col = c >= 0 ? 0xFF000000 | PlayerColors.colors[c].hexCode : 0xFFFFFFFF;
            gg.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
            gg.fill(px - 1, py - 1, px + 2, py + 2, col);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker.mouseClicked(mx, my, button))
            return true;
        if (paletteFor != null) {
            int cols = PAL_PER_ROW;
            if (button == 1) {   // right-click anywhere in the popup: back to random/auto
                if (paletteFor == PLAYER) settings.colour = -1; else ((Bot) paletteFor).colour = -1;
                paletteFor = null;
                return true;
            }
            for (int i = 0; i < PlayerColors.PLAYER_COLOR_COUNT; i++) {
                int sx = palX + 3 + (i % cols) * (PAL_SWATCH + PAL_GAP);
                int sy = palY + 16 + (i / cols) * (PAL_SWATCH + PAL_GAP);
                if (mx >= sx && mx < sx + PAL_SWATCH && my >= sy && my < sy + PAL_SWATCH) {
                    if (paletteFor == PLAYER) settings.colour = i; else ((Bot) paletteFor).colour = i;
                    paletteFor = null;
                    return true;
                }
            }
            paletteFor = null;   // clicked outside: close
            return true;
        }
        if (button == 0) {
            for (Object[] s : swatches) {
                int sx = (Integer) s[0], sy = (Integer) s[1];
                if (mx >= sx - 2 && mx < sx + SWATCH + 2 && my >= sy - 2 && my < sy + SWATCH + 2) {
                    paletteFor = s[2];
                    palX = Math.min(sx + SWATCH + 6, width - (PAL_PER_ROW * (PAL_SWATCH + PAL_GAP) + 10));
                    palY = Math.max(4, Math.min(sy - 10, height - 90));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
