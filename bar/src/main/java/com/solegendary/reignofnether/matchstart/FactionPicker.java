package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The faction dropdown shared by the match-start lobby and the skirmish setup screen. A row of one tile per faction
 * stopped fitting at six; this lists each faction on its own line (icon, name, one-line flavour tagline) and scrolls
 * once the list is taller than the screen, so 7-10 factions fit at GUI scale 3. Preview factions are greyed, say
 * "coming soon" on hover and can't be picked; "Random" is always the last entry.
 *
 * Mouse: click an entry, wheel to scroll, click outside to close. Keyboard: Up/Down (skipping previews), Home/End,
 * Enter/Space to pick, Escape to close. The owning screen forwards its input here first while it is open.
 */
public class FactionPicker {

    public record Entry(Faction faction, boolean selectable) { }

    static final ResourceLocation RANDOM_ICON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/question_mark.png");

    static final int ENTRY_H = 24;
    static final int ICON = 16;
    static final int PAD = 2;
    static final int MAX_W = 230;

    private static final int BG = 0xF0101216;
    private static final int EDGE = 0xFF3A3F46;
    private static final int ACCENT = 0xFFE6C76A;
    private static final int HOVER = 0x40FFFFFF;
    private static final int SELECTED = 0x50E6C76A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFB0B8C0;
    private static final int TEXT_GREY = 0xFF6A6E74;

    private List<Entry> entries = List.of();
    private Faction selected = null;
    private Consumer<Faction> onPick = null;
    private boolean open = false;
    private int x, y, w, h;
    private int scroll = 0;      // in entries
    private int highlight = -1;  // keyboard cursor, index into entries
    private int lastHovered = -1; // the row under the mouse last frame, so the hover tick fires once per row change

    /**
     * Every faction the lobby can show, in registry order (so new factions append and the old order stays): the live
     * ones, then any preview (greyed), then Random. Neutral, None and anything else unplayable stay out.
     */
    public static List<Entry> lobbyEntries() {
        List<Entry> list = new ArrayList<>();
        for (Faction f : com.solegendary.reignofnether.api.ReignOfNetherRegistries.FACTIONS) {
            if (f == Factions.RANDOM)
                continue;
            if (Factions.isLive(f))
                list.add(new Entry(f, true));
            else if (f.preview)
                list.add(new Entry(f, false));
        }
        list.add(new Entry(Factions.RANDOM, true));
        return list;
    }

    /**
     * The skirmish screen's list: every faction that has a packet code (SkirmishServerboundPacket.CODE_PATHS), in code
     * order with Random last, as the old cycler had it. A coded faction that isn't live yet shows greyed.
     */
    public static List<Entry> skirmishEntries() {
        List<Entry> list = new ArrayList<>();
        for (int code = 0; code < SkirmishServerboundPacket.codeCount(); code++) {
            if (code == SkirmishServerboundPacket.RANDOM_CODE)
                continue;
            Faction f = SkirmishServerboundPacket.factionForCode(code);
            if (f == Factions.NONE)
                continue;
            if (Factions.isLive(f))
                list.add(new Entry(f, true));
            else if (f.preview)
                list.add(new Entry(f, false));
        }
        list.add(new Entry(Factions.RANDOM, true));
        return list;
    }

    public static ResourceLocation iconOf(Faction f) {
        return f == Factions.RANDOM ? RANDOM_ICON : f.icon;
    }

    /** The one-line flavour text, or "" for a faction without a lang entry. */
    public static String tagline(Faction f) {
        String key = "matchstart.reignofnether.faction_tagline." + f.getName();
        return I18n.exists(key) ? I18n.get(key) : "";
    }

    public static void renderIcon(GuiGraphics g, Faction f, int x, int y, int size, boolean greyed) {
        if (greyed)
            g.setColor(0.45f, 0.45f, 0.45f, 1f);
        MyRenderer.renderIcon(g, iconOf(f), x, y, size);
        if (greyed)
            g.setColor(1f, 1f, 1f, 1f);
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        onPick = null;
    }

    /**
     * Opens below the anchor rectangle (above it when there is no room below), right-aligned to it and clamped to the
     * screen. Height is capped to the screen; the rest scrolls.
     */
    public void open(int ax1, int ay1, int ax2, int ay2, int screenW, int screenH,
                     List<Entry> entries, Faction selected, Consumer<Faction> onPick) {
        this.entries = entries;
        this.selected = selected;
        this.onPick = onPick;
        this.open = true;
        this.w = Math.min(MAX_W, screenW - 8);
        int fullH = entries.size() * ENTRY_H + PAD * 2;
        this.h = Math.min(fullH, screenH - 8);
        this.x = Math.max(4, Math.min(ax2 - w, screenW - w - 4));
        if (ay2 + 2 + h <= screenH - 4)
            this.y = ay2 + 2;
        else if (ay1 - 2 - h >= 4)
            this.y = ay1 - 2 - h;
        else
            this.y = Math.max(4, screenH - 4 - h);
        this.highlight = indexOf(selected);
        if (highlight < 0 || !entries.get(highlight).selectable())
            highlight = step(-1, 1);
        this.scroll = 0;
        this.lastHovered = -1;
        ensureVisible(highlight);
        LobbySounds.open();
    }

    private int indexOf(Faction f) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).faction() == f)
                return i;
        return -1;
    }

    private int visibleRows() {
        return Math.max(1, (h - PAD * 2) / ENTRY_H);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - visibleRows());
    }

    private void ensureVisible(int idx) {
        if (idx < 0)
            return;
        if (idx < scroll)
            scroll = idx;
        else if (idx >= scroll + visibleRows())
            scroll = idx - visibleRows() + 1;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Next selectable index from {@code from} in direction {@code dir}, or {@code from} if there is none. */
    private int step(int from, int dir) {
        for (int i = from + dir; i >= 0 && i < entries.size(); i += dir)
            if (entries.get(i).selectable())
                return i;
        return from;
    }

    private int entryAt(double mx, double my) {
        if (mx < x || mx >= x + w || my < y + PAD || my >= y + h - PAD)
            return -1;
        int idx = scroll + (int) ((my - y - PAD) / ENTRY_H);
        return idx >= 0 && idx < entries.size() ? idx : -1;
    }

    private void pick(int idx) {
        if (idx < 0 || idx >= entries.size())
            return;
        if (!entries.get(idx).selectable()) {
            LobbySounds.denied();   // a "coming soon" preview
            return;
        }
        Faction f = entries.get(idx).faction();
        // re-picking the current faction clears it in the lobby (and is a no-op in skirmish): no fanfare for that
        if (f == selected)
            LobbySounds.close();
        else
            LobbySounds.faction(f);
        Consumer<Faction> cb = onPick;
        close();
        if (cb != null)
            cb.accept(f);
    }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        if (!open)
            return;
        // above every widget and item in the owning screen
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, BG);

        int hovered = entryAt(mx, my);
        if (hovered != lastHovered) {
            if (hovered >= 0 && entries.get(hovered).selectable())
                LobbySounds.hover();
            lastHovered = hovered;
        }
        int rows = visibleRows();
        boolean scrolls = maxScroll() > 0;
        int textRight = x + w - (scrolls ? 8 : 4);
        g.enableScissor(x, y + PAD, x + w, y + h - PAD);
        for (int r = 0; r < rows + 1; r++) {
            int idx = scroll + r;
            if (idx >= entries.size())
                break;
            Entry e = entries.get(idx);
            int ey = y + PAD + r * ENTRY_H;
            if (e.faction() == selected)
                g.fill(x + 1, ey, x + w - 1, ey + ENTRY_H - 1, SELECTED);
            if (e.selectable() && (idx == hovered || (hovered < 0 && idx == highlight)))
                g.fill(x + 1, ey, x + w - 1, ey + ENTRY_H - 1, HOVER);
            if (e.faction() == selected)
                g.fill(x + 1, ey, x + 3, ey + ENTRY_H - 1, ACCENT);

            boolean grey = !e.selectable();
            renderIcon(g, e.faction(), x + 6, ey + (ENTRY_H - ICON) / 2, ICON, grey);
            int tx = x + 6 + ICON + 6;
            int maxTw = textRight - tx;
            String name = MiscUtil.getFactionName(e.faction());
            g.drawString(font, font.plainSubstrByWidth(name, maxTw), tx, ey + 3, grey ? TEXT_GREY : TEXT, false);
            String tag = grey ? I18n.get("matchstart.reignofnether.faction_coming_soon") : tagline(e.faction());
            if (!tag.isEmpty())
                g.drawString(font, font.plainSubstrByWidth(tag, maxTw), tx, ey + 13, grey ? TEXT_GREY : TEXT_DIM, false);
        }
        g.disableScissor();

        if (scrolls) {
            int trackX = x + w - 4;
            g.fill(trackX, y + PAD, trackX + 2, y + h - PAD, 0x40FFFFFF);
            int trackH = h - PAD * 2;
            int thumbH = Math.max(10, trackH * rows / entries.size());
            int thumbY = y + PAD + (trackH - thumbH) * scroll / maxScroll();
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ACCENT);
        }

        if (hovered >= 0 && !entries.get(hovered).selectable()) {
            Faction f = entries.get(hovered).faction();
            g.renderComponentTooltip(font, List.of(Component.literal(MiscUtil.getFactionName(f)),
                    Component.translatable("matchstart.reignofnether.faction_coming_soon")), mx, my);
        }
        g.pose().popPose();
    }

    /** While open, every click is ours: an entry picks, anything else closes. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open)
            return false;
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            if (button == 0)
                pick(entryAt(mx, my));   // a greyed preview entry does nothing and keeps the list open
            return true;
        }
        close();
        LobbySounds.close();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!open)
            return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        return true;
    }

    /** Returns true when the key was used; while open every key is swallowed so the screen behind doesn't react. */
    public boolean keyPressed(int keyCode) {
        if (!open)
            return false;
        int before = highlight;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> highlight = step(highlight < 0 ? entries.size() : highlight, -1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> highlight = step(highlight, 1);
            case GLFW.GLFW_KEY_HOME -> highlight = step(-1, 1);
            case GLFW.GLFW_KEY_END -> highlight = step(entries.size(), -1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> pick(highlight);
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                LobbySounds.close();
            }
            default -> { }
        }
        if (open && highlight != before)
            LobbySounds.hover();
        ensureVisible(highlight);
        return true;
    }
}
