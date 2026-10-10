package com.solegendary.reignofnether.minimap;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * The rules for BAR-style map drawing (Alt-drag lines and Alt-double-click labels shared with allies), kept free of
 * networking and Minecraft classes so a game test can check them directly:
 * <ul>
 *   <li>a sliding-window rate limit per player ({@link #MAX_STROKES} strokes or labels per {@link #WINDOW_MS})</li>
 *   <li>who receives a drawing: the sender and their allies, never enemies or spectating strangers</li>
 *   <li>stroke and label validation (point count, segment length, label length and characters)</li>
 * </ul>
 * The limiter is an instance, not static state: in single player the client and the integrated server share one
 * JVM, and the client keeps its own copy (to warn the player early) that must not eat into the server's budget.
 */
public class MapDrawRules {

    public static final int MAX_STROKES = 10;
    public static final long WINDOW_MS = 5000;
    public static final int MAX_POINTS = 32;          // a stroke is a short polyline, not a painting
    public static final int MAX_SEGMENT_BLOCKS = 96;  // the client samples every few blocks; anything longer is junk
    public static final int MAX_LABEL_CHARS = 24;
    public static final int LIFETIME_TICKS = 20 * 20; // drawings fade after 20 s

    public static final byte KIND_STROKE = 0;
    public static final byte KIND_LABEL = 1;

    // static server instance; the client creates its own
    public static final MapDrawRules SERVER = new MapDrawRules();

    private final Map<UUID, ArrayDeque<Long>> history = new HashMap<>();

    /** @return true (and records the use) if this player may draw again now; false if they're over the limit */
    public synchronized boolean tryConsume(UUID player, long nowMs) {
        ArrayDeque<Long> times = history.computeIfAbsent(player, k -> new ArrayDeque<>(MAX_STROKES));
        while (!times.isEmpty() && nowMs - times.peekFirst() >= WINDOW_MS)
            times.pollFirst();
        if (times.size() >= MAX_STROKES)
            return false;
        times.addLast(nowMs);
        // keep the map from growing with every player who ever drew: drop idle entries now and then
        if (history.size() > 64) {
            Iterator<ArrayDeque<Long>> it = history.values().iterator();
            while (it.hasNext()) {
                ArrayDeque<Long> d = it.next();
                if (d.isEmpty() || nowMs - d.peekLast() >= WINDOW_MS)
                    it.remove();
            }
        }
        return true;
    }

    /** The sender plus every online player allied with them, in a stable order. */
    public static Set<String> recipients(String sender, Collection<String> online, BiPredicate<String, String> isAllied) {
        Set<String> out = new LinkedHashSet<>();
        out.add(sender);
        for (String name : online)
            if (!name.equals(sender) && isAllied.test(sender, name))
                out.add(name);
        return out;
    }

    /** A stroke is valid with 2..MAX_POINTS points and no absurdly long segment. */
    public static boolean isValidStroke(int[] xs, int[] zs) {
        if (xs == null || zs == null || xs.length != zs.length || xs.length < 2 || xs.length > MAX_POINTS)
            return false;
        for (int i = 1; i < xs.length; i++) {
            long dx = (long) xs[i] - xs[i - 1], dz = (long) zs[i] - zs[i - 1];
            if (dx * dx + dz * dz > (long) MAX_SEGMENT_BLOCKS * MAX_SEGMENT_BLOCKS)
                return false;
        }
        return true;
    }

    /** Trims, strips formatting codes and control characters and caps the length. Returns "" when nothing is left. */
    public static String sanitiseLabel(String raw) {
        if (raw == null)
            return "";
        StringBuilder sb = new StringBuilder(MAX_LABEL_CHARS);
        for (int i = 0; i < raw.length() && sb.length() < MAX_LABEL_CHARS; i++) {
            char c = raw.charAt(i);
            if (c == '§') {   // Minecraft formatting code: drop it and the code character after it
                i++;
                continue;
            }
            if (Character.isISOControl(c))
                continue;
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
