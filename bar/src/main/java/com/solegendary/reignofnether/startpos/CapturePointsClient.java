package com.solegendary.reignofnether.startpos;

import java.util.List;

/**
 * Clientside copy of the capture sites (position, kind, owner). The sites themselves are vanilla marker entities,
 * which never reach clients, so the server syncs this small list instead; the strategic view reads it to draw a
 * flag glyph on each site.
 */
public final class CapturePointsClient {
    private CapturePointsClient() { }

    public record Site(int x, int y, int z, int kind, String owner) { }

    // replaced wholesale on every sync, so readers on the render thread can iterate without locking
    private static volatile List<Site> sites = List.of();

    public static List<Site> getSites() {
        return sites;
    }

    public static void sync(List<Site> newSites) {
        sites = List.copyOf(newSites);
    }

    public static void clear() {
        sites = List.of();
    }
}
