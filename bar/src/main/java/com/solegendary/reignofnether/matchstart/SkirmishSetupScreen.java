package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.player.PlayerColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The skirmish lobby (BAR-style): pick your faction and colour, how many bots and what each one plays, and the
 * map rolls - arena size, metal richness, spawn distance. Choices persist for the session so a rematch is one click.
 */
public class SkirmishSetupScreen extends Screen {

    /** Everything the lobby decides; codes match SkirmishServerboundPacket. */
    public static class Settings {
        public int faction = 0;          // 0 Kingdom, 1 Fallen, 2 Gilded Legion, 3 random
        public int colour = 1;           // index into PlayerColors.colors (0..PLAYER_COLOR_COUNT-1), or -1 random
        public int botCount = 1;
        public int[] botFaction = {3, 3, 3};
        public int[] botDifficulty = {1, 1, 1};
        public int[] botColour = {-1, -1, -1};   // palette index, or -1 auto (first colour nobody has)
        public int arena = 4;            // 0 small .. 3 huge, 4 random
        public int metal = 3;            // 0 lean, 1 normal, 2 rich, 3 random
        public int spawnDistance = 1;    // 0 close, 1 normal, 2 far

        public int colorMapId() {
            int idx = colour >= 0 ? colour : (int) (Math.random() * PlayerColors.PLAYER_COLOR_COUNT);
            return PlayerColors.colors[idx % PlayerColors.PLAYER_COLOR_COUNT].mapColorId;
        }

        /** A bot's requested colour as a map colour id, 0 for auto; a bot never shares the player's colour. */
        public int botColorMapId(int i) {
            int idx = botColour[i];
            if (idx < 0 || idx == colour)
                return 0;
            return PlayerColors.colors[idx % PlayerColors.PLAYER_COLOR_COUNT].mapColorId;
        }
    }

    static final Settings settings = new Settings();   // remembered between lobbies

    static final List<String> FACTIONS = List.of("The Kingdom", "The Fallen", "The Gilded Legion", "Random");
    static final List<String> DIFFICULTIES = List.of("Easy", "Medium", "Hard");
    static final List<String> ARENAS = List.of("Small", "Medium", "Large", "Huge", "Random");
    static final List<String> METALS = List.of("Lean", "Normal", "Rich", "Random");
    static final List<String> DISTANCES = List.of("Close", "Normal", "Far");

    final Screen parent;
    static final int SWATCH = 15, GAP = 2, PER_ROW = 12;
    int paletteX, paletteY;

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
        int w = 260, half = 127, x = width / 2 - w / 2;
        int y = Math.max(28, height / 2 - 120);
        final int step = 24;

        // --- you ---
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(FACTIONS.get(i)))
                .withValues(range(4)).withInitialValue(settings.faction)
                .create(x, y, w, 20, Component.literal("Your faction"), (b, v) -> settings.faction = v));
        y += step;
        // your colour: a palette of swatches (click one), plus a Random button
        paletteX = x;
        paletteY = y + 12;
        addRenderableWidget(Button.builder(Component.literal("Random"), b -> settings.colour = -1)
                .bounds(x + w - 50, paletteY, 50, SWATCH * 2 + GAP).build());
        y = paletteY + SWATCH * 2 + GAP + 8;

        // --- opponents ---
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(String.valueOf(i)))
                .withValues(List.of(1, 2, 3)).withInitialValue(settings.botCount)
                .create(x, y, w, 20, Component.literal("Opponents"), (b, v) -> { settings.botCount = v; rebuild(); }));
        y += step;
        for (int i = 0; i < settings.botCount; i++) {
            final int idx = i;
            int third = (w - 8) / 3;
            addRenderableWidget(CycleButton.<Integer>builder(f -> Component.literal(FACTIONS.get(f)))
                    .withValues(range(4)).withInitialValue(settings.botFaction[i])
                    .create(x, y, third + 20, 20, Component.literal("Bot " + (i + 1)), (b, v) -> settings.botFaction[idx] = v));
            addRenderableWidget(CycleButton.<Integer>builder(d -> Component.literal(DIFFICULTIES.get(d)))
                    .withValues(range(3)).withInitialValue(settings.botDifficulty[i])
                    .create(x + third + 24, y, third - 14, 20, Component.literal("Skill"), (b, v) -> settings.botDifficulty[idx] = v));
            List<Integer> botColours = new java.util.ArrayList<>();
            botColours.add(-1);
            botColours.addAll(range(PlayerColors.PLAYER_COLOR_COUNT));
            addRenderableWidget(CycleButton.<Integer>builder(c -> c < 0 ? Component.literal("Auto") : colourName(c))
                    .withValues(botColours).withInitialValue(settings.botColour[i])
                    .create(x + w - third + 2, y, third - 2, 20, Component.literal("Colour"), (b, v) -> settings.botColour[idx] = v));
            y += step;
        }

        // --- the map ---
        y += 6;
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(ARENAS.get(i)))
                .withValues(range(5)).withInitialValue(settings.arena)
                .create(x, y, w, 20, Component.literal("Arena size"), (b, v) -> settings.arena = v));
        y += step;
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(METALS.get(i)))
                .withValues(range(4)).withInitialValue(settings.metal)
                .create(x, y, w, 20, Component.literal("Metal"), (b, v) -> settings.metal = v));
        y += step;
        addRenderableWidget(CycleButton.<Integer>builder(i -> Component.literal(DISTANCES.get(i)))
                .withValues(range(3)).withInitialValue(settings.spawnDistance)
                .create(x, y, w, 20, Component.literal("Spawn distance"), (b, v) -> settings.spawnDistance = v));
        y += step + 8;

        addRenderableWidget(Button.builder(Component.literal("Start Battle"),
                b -> QuickBattle.launch(this, settings)).bounds(x, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                b -> Minecraft.getInstance().setScreen(parent)).bounds(x + w - half, y, half, 20).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < PlayerColors.PLAYER_COLOR_COUNT; i++) {
                int sx = paletteX + (i % PER_ROW) * (SWATCH + GAP);
                int sy = paletteY + (i / PER_ROW) * (SWATCH + GAP);
                if (mx >= sx && mx < sx + SWATCH && my >= sy && my < sy + SWATCH) {
                    settings.colour = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        renderBackground(gg);
        int top = Math.max(28, height / 2 - 120);
        gg.drawCenteredString(font, "SKIRMISH", width / 2, top - 20, 0xFFD27A);
        super.render(gg, mx, my, pt);
        // your colour: the palette grid, selected swatch framed in white, hovered one framed in grey
        for (int i = 0; i < PlayerColors.PLAYER_COLOR_COUNT; i++) {
            int sx = paletteX + (i % PER_ROW) * (SWATCH + GAP);
            int sy = paletteY + (i / PER_ROW) * (SWATCH + GAP);
            boolean hover = mx >= sx && mx < sx + SWATCH && my >= sy && my < sy + SWATCH;
            int frame = i == settings.colour ? 0xFFFFFFFF : hover ? 0xFFAAAAAA : 0xFF000000;
            gg.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, frame);
            gg.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | PlayerColors.colors[i].hexCode);
        }
        String picked = settings.colour >= 0 ? colourName(settings.colour).getString() : "Random";
        gg.drawString(font, "Your colour: " + picked, paletteX, paletteY - 11, 0xE0E0E0);
        gg.drawCenteredString(font, "A fresh arena every battle: lanes, metal patches and the boundary wall form in the first seconds.",
                width / 2, height - 24, 0x9A9A9A);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
