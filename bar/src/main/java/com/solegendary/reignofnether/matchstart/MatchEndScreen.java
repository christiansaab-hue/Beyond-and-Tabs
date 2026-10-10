package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.player.MatchStatsClientboundPacket.MatchStatRow;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// End-of-match stats popup. A compact, centered panel (battlefield stays visible behind
// it) that groups players by team into WINNER / LOSER sections, showing each player's
// cumulative match totals plus a per-team party total. Opened by MatchEndClientEvents.
// An 8v8 (16 rows + awards) does not fit at GUI scale 2-3 on 1080p, so the panel first switches to compact rows
// (small heads, party total and resources on one line) and, if even that is too tall, scrolls with the mouse wheel.
public class MatchEndScreen extends Screen {

    // score array indices (order of RTSPlayerScoresEnum.values())
    private static final int SCORE_BUILDINGS = RTSPlayerScoresEnum.TOTAL_BUILDINGS_CONSTRUCTED.ordinal();
    private static final int SCORE_UNITS     = RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED.ordinal();
    private static final int SCORE_MILITARY  = RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED.ordinal();
    private static final int SCORE_RESOURCES = RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED.ordinal();

    private static final int BG_PANEL    = 0x40000000; // light overlay so the dirt shows through
    private static final int BG_ROW_SELF = 0x40FFFFFF;
    private static final int ACCENT      = 0xFFE6C76A;
    private static final int WIN_COL     = 0xFF6CE26C;
    private static final int LOSE_COL    = 0xFFE05A5A;
    private static final int TEXT_NORMAL = 0xFFFFFFFF;
    private static final int TEXT_DIM    = 0xFFB0B8C0;
    private static final int DIVIDER     = 0x40FFFFFF;

    private static final int PANEL_W = 420;
    private static final int PAD = 12;
    private static final int HEADER_H = 30;
    private static final int TEAM_HEADER_H = 18;
    private static final int ROW_H = 20;
    private static final int LINE_H = 12;
    private static final int TEAM_GAP = 10;
    private static final int HEAD = 16;
    // compact layout
    private static final int HEADER_H_C = 24;
    private static final int TEAM_HEADER_H_C = 12;
    private static final int ROW_H_C = 11;
    private static final int TEAM_GAP_C = 5;
    private static final int HEAD_C = 9;
    private static final int SCROLL_STEP = 24;

    // column x-offsets (right edge) from the panel's left content edge
    private static final int COL_UNITS = 230;
    private static final int COL_MIL    = 300;
    private static final int COL_BLDG   = 380;

    private static class Team {
        boolean winner;
        final List<MatchStatRow> members = new ArrayList<>();
        long units, military, buildings, resources;
    }

    private final List<Team> teams = new ArrayList<>();
    // BAR-style award lines ("Most damage dealt: X (12,345)"), precomputed once - only awards someone actually earned
    private final List<String> awards = new ArrayList<>();
    private int panelL, panelT, panelW, panelH;
    private boolean compact = false;
    private int contentH = 0;   // full height of everything inside the panel; > panelH means it scrolls
    private int scroll = 0;

    public MatchEndScreen() {
        super(Component.translatable("matchend.reignofnether.title"));
        buildTeams();
    }

    private void buildTeams() {
        teams.clear();
        Map<Integer, Team> byTeamId = new LinkedHashMap<>();
        for (MatchStatRow row : MatchEndClientEvents.getRows()) {
            Team t = byTeamId.computeIfAbsent(row.teamId, k -> new Team());
            t.winner = row.winner; // all members of a team share a result
            t.members.add(row);
            t.units += row.score(SCORE_UNITS);
            t.military += row.score(SCORE_MILITARY);
            t.buildings += row.score(SCORE_BUILDINGS);
            t.resources += row.score(SCORE_RESOURCES);
        }
        buildAwards();
        // winners first
        for (Team t : byTeamId.values()) if (t.winner) teams.add(t);
        for (Team t : byTeamId.values()) if (!t.winner) teams.add(t);
    }

    private void buildAwards() {
        awards.clear();
        addAward("matchend.reignofnether.award_damage", r -> r.damageDealt);
        addAward("matchend.reignofnether.award_metal", r -> r.metalProduced);
        addAward("matchend.reignofnether.award_reclaim", r -> r.metalReclaimed);
    }

    private void addAward(String key, java.util.function.ToIntFunction<MatchStatRow> stat) {
        MatchStatRow best = null;
        for (MatchStatRow row : MatchEndClientEvents.getRows())
            if (stat.applyAsInt(row) > 0 && (best == null || stat.applyAsInt(row) > stat.applyAsInt(best)))
                best = row;
        if (best != null)
            awards.add(Component.translatable(key, best.name, String.format("%,d", stat.applyAsInt(best))).getString());
    }

    @Override
    protected void init() {
        panelW = Math.min(PANEL_W, this.width - 8);
        int maxH = this.height - 8;
        compact = contentHeight(false) > maxH;
        contentH = contentHeight(compact);
        panelH = Math.min(contentH, maxH);
        scroll = Math.max(0, Math.min(scroll, contentH - panelH));
        panelL = (this.width - panelW) / 2;
        panelT = (this.height - panelH) / 2;

        // [X] close button, top-right corner of the panel
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(panelL + panelW - 26, panelT + 5, 20, 20).build());
    }

    private int contentHeight(boolean c) {
        int h = PAD + (c ? HEADER_H_C : HEADER_H) + LINE_H; // header + column-header row
        for (Team t : teams)   // team header, rows, party total (+ resources line unless compact), gap
            h += (c ? TEAM_HEADER_H_C : TEAM_HEADER_H) + t.members.size() * (c ? ROW_H_C : ROW_H)
                + (c ? LINE_H : LINE_H * 2) + (c ? TEAM_GAP_C : TEAM_GAP);
        if (!awards.isEmpty())
            h += LINE_H + 4 + awards.size() * LINE_H; // "Awards" header + divider + one line each
        return h + PAD;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = contentH - panelH;
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta) * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // tiled dirt, but only inside the popup - the battlefield stays visible around it
        g.setColor(0.25F, 0.25F, 0.25F, 1.0F);
        g.blit(BACKGROUND_LOCATION, panelL, panelT, 0, panelL, panelT, panelW, panelH, 32, 32);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        MyRenderer.renderFrameWithBg(g, panelL, panelT, panelW, panelH, BG_PANEL);

        int cl = panelL + PAD;               // content left
        int cr = panelL + panelW - PAD;      // content right
        int y = panelT + PAD - scroll;
        int head = compact ? HEAD_C : HEAD;
        int rowH = compact ? ROW_H_C : ROW_H;
        int nameX = cl + head + 4 + head + 4;
        boolean scrolls = contentH > panelH;
        if (scrolls)
            g.enableScissor(panelL + 1, panelT + 1, panelL + panelW - 1, panelT + panelH - 1);

        // header: title + duration
        int headerY = compact ? y : y + 4;
        g.drawString(font, Component.translatable("matchend.reignofnether.title"), cl, headerY, ACCENT, true);
        String dur = TimeUtils.getTimeStrFromTicks(MatchEndClientEvents.getGameDurationTicks());
        g.drawString(font, dur, cr - 34 - font.width(dur), headerY, TEXT_DIM, true);
        y += (compact ? HEADER_H_C : HEADER_H) - 6;
        g.fill(cl, y, cr, y + 1, DIVIDER);
        y += 4;

        // column headers
        drawColHeader(g, "matchend.reignofnether.col_units", cl + COL_UNITS, y);
        drawColHeader(g, "matchend.reignofnether.col_military", cl + COL_MIL, y);
        drawColHeader(g, "matchend.reignofnether.col_buildings", cl + COL_BLDG, y);
        y += LINE_H;

        Minecraft mc = Minecraft.getInstance();
        String localName = mc.player != null ? mc.player.getName().getString() : "";

        for (Team t : teams) {
            // team header: WINNER / LOSER
            Component label = Component.translatable(t.winner ? "matchend.reignofnether.winner" : "matchend.reignofnether.loser");
            g.drawString(font, label, cl, compact ? y + 2 : y + 4, t.winner ? WIN_COL : LOSE_COL, true);
            y += compact ? TEAM_HEADER_H_C : TEAM_HEADER_H;

            for (MatchStatRow row : t.members) {
                if (row.name.equals(localName))
                    g.fill(cl - 2, y - 1, cr + 2, y + head + 1, BG_ROW_SELF);

                // player head
                ResourceLocation skin = MyRenderer.getPlayerSkinRl(row.name);
                g.blit(skin, cl, y, head, head, 8.0f, 8.0f, 8, 8, 64, 64);
                g.blit(skin, cl, y, head, head, 40.0f, 8.0f, 8, 8, 64, 64);

                // faction icon
                ResourceLocation fIcon = row.faction.icon;
                if (fIcon != null)
                    MyRenderer.renderIcon(g, fIcon, cl + head + 4, y, head);

                int textY = y + (head - font.lineHeight) / 2 + (compact ? 1 : 0);
                g.drawString(font, row.name, nameX, textY, TEXT_NORMAL, true);
                drawNum(g, row.score(SCORE_UNITS), cl + COL_UNITS, textY, TEXT_NORMAL);
                drawNum(g, row.score(SCORE_MILITARY), cl + COL_MIL, textY, TEXT_NORMAL);
                drawNum(g, row.score(SCORE_BUILDINGS), cl + COL_BLDG, textY, TEXT_NORMAL);
                y += rowH;
            }

            // party total line
            String totalLabel = Component.translatable("matchend.reignofnether.party_total").getString();
            g.drawString(font, totalLabel, nameX, y, TEXT_DIM, true);
            drawNum(g, t.units, cl + COL_UNITS, y, ACCENT);
            drawNum(g, t.military, cl + COL_MIL, y, ACCENT);
            drawNum(g, t.buildings, cl + COL_BLDG, y, ACCENT);
            // party resource total: own line, or after the total label when compact
            String res = Component.translatable("matchend.reignofnether.resources", String.format("%,d", t.resources)).getString();
            if (compact) {
                g.drawString(font, res, nameX + font.width(totalLabel) + 8, y, TEXT_DIM, true);
                y += LINE_H + TEAM_GAP_C;
            } else {
                y += LINE_H;
                g.drawString(font, res, nameX, y, TEXT_DIM, true);
                y += LINE_H + TEAM_GAP;
            }
        }

        if (!awards.isEmpty()) {
            g.fill(cl, y, cr, y + 1, DIVIDER);
            y += 4;
            g.drawString(font, Component.translatable("matchend.reignofnether.awards"), cl, y, ACCENT, true);
            y += LINE_H;
            for (String award : awards) {
                g.drawString(font, award, cl + 8, y, TEXT_NORMAL, true);
                y += LINE_H;
            }
        }

        if (scrolls) {
            g.disableScissor();
            // scrollbar along the right edge
            int trackT = panelT + 30, trackB = panelT + panelH - 4, trackH = trackB - trackT;
            int thumbH = Math.max(12, trackH * panelH / contentH);
            int thumbT = trackT + (trackH - thumbH) * scroll / Math.max(1, contentH - panelH);
            g.fill(panelL + panelW - 5, trackT, panelL + panelW - 3, trackB, 0x40FFFFFF);
            g.fill(panelL + panelW - 5, thumbT, panelL + panelW - 3, thumbT + thumbH, 0xC0FFFFFF);
        }

        super.render(g, mouseX, mouseY, partialTick); // renders the [X] button
    }

    private void drawNum(GuiGraphics g, long value, int rightX, int y, int color) {
        String s = String.format("%,d", value);
        g.drawString(font, s, rightX - font.width(s), y, color, true);
    }

    private void drawColHeader(GuiGraphics g, String key, int rightX, int y) {
        String s = Component.translatable(key).getString();
        g.drawString(font, s, rightX - font.width(s), y, TEXT_DIM, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        MatchEndClientEvents.dismiss();
    }
}
