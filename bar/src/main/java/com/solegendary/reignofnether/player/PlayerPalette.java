package com.solegendary.reignofnether.player;

import net.minecraft.world.level.material.MapColor;

import java.util.Collection;
import java.util.List;

/**
 * The player colour palette (BAR-style: 24 team colours, white and black included), safe to use on the server.
 * Order is the display order; the map colour id is what travels in packets and saves as a player's
 * {@code startPosColorId}. {@link PlayerColors} wraps these with textures on the client.
 */
public final class PlayerPalette {
    private PlayerPalette() { }

    public record Entry(int mapColorId, int hex, String name) { }

    public static final List<Entry> ENTRIES = List.of(
        // the classic twelve
        new Entry(MapColor.COLOR_RED.id, 0xA12722, "red"),
        new Entry(MapColor.COLOR_BLUE.id, 0x35399D, "blue"),
        new Entry(MapColor.COLOR_CYAN.id, 0x158991, "cyan"),
        new Entry(MapColor.COLOR_PURPLE.id, 0x792AAC, "purple"),
        new Entry(MapColor.COLOR_YELLOW.id, 0xF8C627, "yellow"),
        new Entry(MapColor.COLOR_ORANGE.id, 0xF07613, "orange"),
        new Entry(MapColor.COLOR_LIGHT_GREEN.id, 0x70B919, "lime"),
        new Entry(MapColor.COLOR_PINK.id, 0xED8DAC, "pink"),
        new Entry(MapColor.COLOR_MAGENTA.id, 0xBD44B3, "magenta"),
        new Entry(MapColor.COLOR_LIGHT_BLUE.id, 0x3AAFD9, "light_blue"),
        new Entry(MapColor.COLOR_GREEN.id, 0x546D1B, "green"),
        new Entry(MapColor.COLOR_BROWN.id, 0x724728, "brown"),
        // monochromes
        new Entry(MapColor.SNOW.id, 0xE9ECEC, "white"),
        new Entry(MapColor.COLOR_LIGHT_GRAY.id, 0x8E8E86, "light_gray"),
        new Entry(MapColor.COLOR_GRAY.id, 0x3E4447, "gray"),
        new Entry(MapColor.COLOR_BLACK.id, 0x141519, "black"),
        // the new set
        new Entry(MapColor.WARPED_NYLIUM.id, 0x1C8C7E, "teal"),
        new Entry(MapColor.GOLD.id, 0xD9A41E, "gold"),
        new Entry(MapColor.CRIMSON_NYLIUM.id, 0x8E1B30, "crimson"),
        new Entry(MapColor.TERRACOTTA_BLUE.id, 0x1E2C6B, "navy"),
        new Entry(MapColor.EMERALD.id, 0x17A854, "emerald"),
        new Entry(MapColor.TERRACOTTA_PURPLE.id, 0xB39BE6, "lavender"),
        new Entry(MapColor.TERRACOTTA_ORANGE.id, 0xFF6F5E, "coral"),
        new Entry(MapColor.ICE.id, 0xA9E4FF, "ice")
    );

    public static final int COUNT = ENTRIES.size();

    public static Entry byMapColorId(int mapColorId) {
        for (Entry e : ENTRIES)
            if (e.mapColorId == mapColorId)
                return e;
        return null;
    }

    public static boolean isPaletteColor(int mapColorId) {
        return byMapColorId(mapColorId) != null;
    }

    /**
     * A palette colour nobody in {@code taken} is using (bots join with this so every side reads differently on
     * the map); spread out from the hues already in play, falling back to the first free entry.
     */
    public static int firstFree(Collection<Integer> taken) {
        // prefer strongly contrasting picks first: the classic twelve, then the rest
        for (Entry e : ENTRIES)
            if (!taken.contains(e.mapColorId))
                return e.mapColorId;
        return ENTRIES.get(0).mapColorId;
    }
}
