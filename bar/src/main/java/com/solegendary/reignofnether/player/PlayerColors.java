package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialStage;
import com.solegendary.reignofnether.unit.Relationship;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.List;

import static com.solegendary.reignofnether.unit.UnitClientEvents.getPlayerToPlayerRelationship;
import static com.solegendary.reignofnether.util.MiscUtil.fcs;

public class PlayerColors {
    /**
     * Indicates which color mode is used. This affects unit/buildings outlines, as well as some UI elements.
     * @return true if using player colors, false if using relation colors
     */
    public static boolean isUsingPlayerColors() {
        if (!PlayerClientEvents.isRTSPlayer())
            return true;
        return ReignOfNetherClientConfigs.USE_PLAYER_COLORS.get();
    }

    /**
     * Switch color mode between Player Colors and Relation Colors.
     */
    public static void toggleColorMode() {
        ReignOfNetherClientConfigs.USE_PLAYER_COLORS.set(!ReignOfNetherClientConfigs.USE_PLAYER_COLORS.get());
    }

    private static final HashMap<Integer, PlayerColor> mappedColors = new HashMap<Integer, PlayerColor>();

    public static class PlayerColor {
        private static int colourCount = 0;
        public final int mapColorId;
        public final int id;
        public final String name;
        public final int hexCode;
        public final ResourceLocation blockTexture;
        public final ResourceLocation bedIcon;

        public PlayerColor(int mapColorId, int hexCode, String name, ResourceLocation blockTexture, ResourceLocation bedIcon) {
            this.mapColorId = mapColorId;
            this.id = colourCount++;
            this.name = name;
            this.hexCode = hexCode;
            this.blockTexture = blockTexture;
            this.bedIcon = bedIcon;
                        if (this.mapColorId != -1) {
                mappedColors.put(mapColorId, this);
            }
        }

        public static PlayerColor fromName(int mapColorId, int hexCode, String name) {
            return new PlayerColor(
                    mapColorId,
                    hexCode,
                    name,
                    ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/block/rts_start_block_" + name + ".png"),
                    ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/beds/" + name + ".png"));
        }
    }

    // colors available for players to choose - the palette lives in PlayerPalette (server-safe); each entry here
    // gets its start-block texture and bed icon. Index == PlayerColor.id == position in PlayerPalette.ENTRIES.
    private static final PlayerColor[] PALETTE = buildPalette();

    private static PlayerColor[] buildPalette() {
        PlayerColor[] out = new PlayerColor[PlayerPalette.COUNT];
        for (int i = 0; i < PlayerPalette.COUNT; i++) {
            PlayerPalette.Entry e = PlayerPalette.ENTRIES.get(i);
            out[i] = PlayerColor.fromName(e.mapColorId(), e.hex(), e.name());
        }
        return out;
    }

    private static PlayerColor named(String name) {
        for (PlayerColor c : PALETTE)
            if (c.name.equals(name))
                return c;
        return PALETTE[0];
    }

    public static final PlayerColor COLOR_RED = named("red");
    public static final PlayerColor COLOR_BLUE = named("blue");
    public static final PlayerColor COLOR_YELLOW = named("yellow");
    public static final PlayerColor COLOR_LIME = named("lime");
    public static final PlayerColor COLOR_GRAY = named("gray");
    public static final PlayerColor COLOR_BLACK = named("black");
    public static final PlayerColor COLOR_WHITE = named("white");
    public static final int PLAYER_COLOR_COUNT = PlayerColor.colourCount;

    public static final PlayerColor COLOR_OWNED = new PlayerColor(-1, 0x33FF33, "owned", ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/lime_wool.png"), COLOR_LIME.bedIcon);
    public static final PlayerColor COLOR_FRIENDLY = new PlayerColor(-1, 0x3333FF, "friendly", ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/blue_wool.png"), COLOR_BLUE.bedIcon);
    public static final PlayerColor COLOR_NEUTRAL = new PlayerColor(-1, 0xFFFF19, "neutral", ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/yellow_wool.png"), COLOR_YELLOW.bedIcon);
    public static final PlayerColor COLOR_HOSTILE = new PlayerColor(-1, 0xFF3333, "hostile", ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/red_wool.png"), COLOR_RED.bedIcon);
    public static final int PLAYER_COLOR_TOTAL_COUNT = PlayerColor.colourCount;

    public static final PlayerColor[] colors = buildAll();

    private static PlayerColor[] buildAll() {
        PlayerColor[] out = new PlayerColor[PLAYER_COLOR_TOTAL_COUNT];
        System.arraycopy(PALETTE, 0, out, 0, PALETTE.length);
        out[PALETTE.length] = COLOR_OWNED;
        out[PALETTE.length + 1] = COLOR_FRIENDLY;
        out[PALETTE.length + 2] = COLOR_NEUTRAL;
        out[PALETTE.length + 3] = COLOR_HOSTILE;
        return out;
    }

    /** The palette colour for a map colour id, or null if it is not a player colour. */
    public static PlayerColor byMapColorId(int mapColorId) {
        return mappedColors.get(mapColorId);
    }

    private static HashMap<String, Integer> playerColorId = new HashMap<>();
    public static int getPlayerColorId(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            // fallback color - white
            return COLOR_WHITE.id;
        }

        // check for a cached color id
        var cachedColorId = playerColorId.getOrDefault(playerName, -1);
        if (cachedColorId != -1) {
            return cachedColorId;
        }

        // find the colorId for the player and cache it
        var mapColorId = PlayerClientEvents.getPlayerMapColorId(playerName);
        if (mapColorId > 0 && mappedColors.containsKey(mapColorId)) {
            var color = mappedColors.get(mapColorId);
            playerColorId.put(playerName, color.id); // only cache a real id
            return color.id;
        }

        var playerId = PlayerClientEvents.getPlayerIndex(playerName);
        if (playerId != null) {
            playerId = playerId % PLAYER_COLOR_COUNT;
            playerColorId.put(playerName, playerId); // only cache a real id
            return playerId;
        }

        // fallback color - white
        return COLOR_WHITE.id;
    }

    public static void reset() {
        playerColorId.clear();
    }

    public static ResourceLocation getColorIcon(int colorIndex) {
        return colors[colorIndex % PLAYER_COLOR_TOTAL_COUNT].blockTexture;
    }

    public static int getColorHex(int colorIndex) {
        return colors[colorIndex % PLAYER_COLOR_TOTAL_COUNT].hexCode;
    }

    public static int getPlayerColorHex(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return COLOR_WHITE.hexCode;
        }

        int colorId = getPlayerColorId(playerName);
        return colors[colorId].hexCode;
    }

    public static ResourceLocation getPlayerColorIcon(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return COLOR_WHITE.blockTexture;
        }

        int colorId = getPlayerColorId(playerName);
        return colors[colorId].blockTexture;
    }

    public static ResourceLocation getPlayerColorBedIcon(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return COLOR_WHITE.bedIcon;
        }

        int colorId = getPlayerColorId(playerName);
        return colors[colorId].bedIcon;
    }

    public static int getPlayerDisplayColorHex(String playerName) {
        if (PlayerColors.isUsingPlayerColors()) {
            return PlayerColors.getPlayerColorHex(playerName);
        }

        // fall back on alliance color
        return getPlayerAllianceColorHex(playerName);
    }

    public static int getPlayerAllianceColorHex(String playerName) {
        Relationship unitRs = getPlayerToPlayerRelationship(playerName);
        return switch (unitRs) {
            case OWNED -> colors[ReignOfNetherClientConfigs.PLAYER_COLOR_SELF.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
            case FRIENDLY ->
                    colors[ReignOfNetherClientConfigs.PLAYER_COLOR_ALLY.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
            case NEUTRAL ->
                    colors[ReignOfNetherClientConfigs.PLAYER_COLOR_NEUTRAL.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
            case HOSTILE ->
                    colors[ReignOfNetherClientConfigs.PLAYER_COLOR_ENEMY.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
        };
    }

    public static int getPlayerPortraitDisplayColorHex(String playerName) {
        if (PlayerColors.isUsingPlayerColors()) {
            return 0x90000000 | PlayerColors.getPlayerColorHex(playerName);
        }

        // fall back on alliance color
        Relationship unitRs = getPlayerToPlayerRelationship(playerName);
        return switch (unitRs) {
            case OWNED -> 0x90000000;
            case FRIENDLY ->
                    0x90000000 | colors[ReignOfNetherClientConfigs.PLAYER_COLOR_ALLY.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
            case NEUTRAL ->
                    0x90000000 | colors[ReignOfNetherClientConfigs.PLAYER_COLOR_NEUTRAL.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
            case HOSTILE ->
                    0x90000000 | colors[ReignOfNetherClientConfigs.PLAYER_COLOR_ENEMY.get() % PLAYER_COLOR_TOTAL_COUNT].hexCode;
        };
    }

    public static Button getToggleTeamColorsButton() {
        return new Button(
                "Toggle alliance colours",
                14,
                isUsingPlayerColors()
                        ? ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/toggle_color_mode_players.png")
                        : ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/toggle_color_mode_relations.png"),
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame.png"),
                null,
                () -> false,
                () -> !TutorialClientEvents.isAtOrPastStage(TutorialStage.MINIMAP_CLICK) || !MinimapClientEvents.isLargeMap(),
                PlayerClientEvents::isRTSPlayer,
                PlayerColors::toggleColorMode,
                null,
                List.of(
                        fcs(I18n.get("hud.orthoview.reignofnether.using_player_team_color"), isUsingPlayerColors()),
                        fcs(I18n.get("hud.orthoview.reignofnether.using_relation_color"), !isUsingPlayerColors())
                )
        );
    }
}
