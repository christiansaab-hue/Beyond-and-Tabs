package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.resources.EconomyClientEvents.ClientEconomy;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * BAR's top bar: metal on the left, energy on the right, each a wide fill bar with "current / storage" above it and
 * the income, expense and net per second at its end; a small population block sits between them and a build-speed
 * warning hangs under the bar while the player is stalling. Centred at the top of the screen, like BAR.
 */
public final class EconomyBarRenderer {
    private EconomyBarRenderer() { }

    static final int METAL_FILL = 0xFFB4BEC8, METAL_DIM = 0xFF2A2E33;
    static final int ENERGY_FILL = 0xFFF0C83C, ENERGY_DIM = 0xFF33301E;
    static final int BG = 0xC0101216, PANEL_H = 34, POP_W = 58;
    static final int PANEL_W_MAX = 210, PANEL_W_MIN = 130;

    public record Layout(int left, int right, int bottom) { }

    /**
     * @return the bar's layout, so callers can keep other HUD elements clear of it
     */
    public static Layout render(GuiGraphics gg, Resources resources, ClientEconomy eco, String owner,
                                int screenWidth, int mouseX, int mouseY, List<RectZone> zones) {
        return render(gg, resources, eco, owner, screenWidth, mouseX, mouseY, zones, 0);
    }

    /** @param reservedLeft pixels at the left edge to stay clear of (the control-group buttons) */
    public static Layout render(GuiGraphics gg, Resources resources, ClientEconomy eco, String owner,
                                int screenWidth, int mouseX, int mouseY, List<RectZone> zones, int reservedLeft) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int panelW = Math.max(PANEL_W_MIN, Math.min(PANEL_W_MAX, (screenWidth - 200 - POP_W) / 2));
        int totalW = panelW * 2 + POP_W;
        int left = screenWidth / 2 - totalW / 2;
        if (left < reservedLeft) {   // slide right past the control groups; shrink the panels if that runs off-screen
            left = reservedLeft;
            int room = screenWidth - 40 - left;   // leave the right-edge buttons alone
            if (totalW > room) {
                panelW = Math.max(90, (room - POP_W) / 2);
                totalW = panelW * 2 + POP_W;
            }
        }
        int top = 0;

        // one dark strip for the whole bar
        gg.fill(left, top, left + totalW, top + PANEL_H, BG);
        gg.fill(left, top + PANEL_H, left + totalW, top + PANEL_H + 1, 0xFF3A3F46);
        zones.add(RectZone.getZoneByLW(left, top, totalW, PANEL_H + 1));

        renderResource(gg, font, left, top, panelW, true, resources.ore, eco.metalStorage, eco.metalIncome, eco.metalExpense);
        renderResource(gg, font, left + panelW + POP_W, top, panelW, false, resources.wood, eco.energyStorage, eco.energyIncome, eco.energyExpense);

        // population block in the middle: bed icon in the player's colour, "used/supply", worker count under it
        int px = left + panelW;
        gg.fill(px, top + 3, px + 1, top + PANEL_H - 3, 0xFF3A3F46);
        gg.fill(px + POP_W - 1, top + 3, px + POP_W, top + PANEL_H - 3, 0xFF3A3F46);
        ResourceLocation bed = PlayerColors.getPlayerColorBedIcon(owner);
        MyRenderer.renderIcon(gg, bed, px + 4, top + 4, 12);
        int pop = UnitClientEvents.getCurrentPopulation(owner);
        int supply = BuildingClientEvents.getTotalPopulationSupply(owner);
        String popStr = pop + "/" + supply;
        gg.drawString(font, popStr, px + 19, top + 4, pop >= supply ? 0xFF7A6A : 0xFFFFFF);
        int workers = (int) UnitClientEvents.getAllUnits().stream()
            .filter(u -> u instanceof WorkerUnit && u instanceof Unit un && owner.equals(un.getOwnerName())).count();
        String wStr = workers + " workers";
        gg.drawString(font, wStr, px + POP_W / 2 - font.width(wStr) / 2, top + 18, 0xA8B0B8);

        int bottom = top + PANEL_H + 1;
        // stall warning under the bar
        if (eco.stall < 0.99f) {
            String stallStr = "Build speed " + Math.round(eco.stall * 100) + "%  -  short on "
                + (shortOnMetal(resources, eco) ? "metal" : "energy");
            int w = font.width(stallStr) + 10;
            int sx = screenWidth / 2 - w / 2;
            gg.fill(sx, bottom, sx + w, bottom + 12, 0xC0401010);
            gg.drawString(font, stallStr, sx + 5, bottom + 2, eco.stall < 0.5f ? 0xFF6A6A : 0xFFD24A);
            zones.add(RectZone.getZoneByLW(sx, bottom, w, 12));
            bottom += 12;
        }

        // BAR "wasting" warning: only TRUE waste - the server measures what overflowed with every ally full too.
        // Overflow that went to allies is not lost, so it gets a quiet "sharing" note instead
        boolean metalWaste = updateWaste(true, eco.metalWasted);
        boolean energyWaste = updateWaste(false, eco.energyWasted);
        boolean metalShare = !metalWaste && eco.metalShared > 0.05f;
        boolean energyShare = !energyWaste && eco.energyShared > 0.05f;
        if (metalWaste || energyWaste || metalShare || energyShare) {
            if (metalWaste)
                renderWasteLabel(gg, font, zones, left + 4, bottom, true, metalWasteRate);
            else if (metalShare)
                renderShareLabel(gg, font, zones, left + 4, bottom, true, eco.metalShared);
            if (energyWaste)
                renderWasteLabel(gg, font, zones, left + totalW - 4, bottom, false, energyWasteRate);
            else if (energyShare)
                renderShareLabel(gg, font, zones, left + totalW - 4, bottom, false, eco.energyShared);
            bottom += 12;
        }

        // tooltips
        if (mouseY >= top && mouseY < top + PANEL_H) {
            List<FormattedCharSequence> tip = null;
            if (mouseX >= left && mouseX < left + panelW)
                tip = List.of(line("Metal  " + resources.ore + " / " + Math.round(eco.metalStorage)),
                    line("+" + fmt(eco.metalIncome) + "/s from extractors, -" + fmt(eco.metalExpense) + "/s on builds"),
                    line("Build more extractors on metal patches to grow income."));
            else if (mouseX >= left + panelW + POP_W && mouseX < left + totalW) {
                // energyExpense includes the converters' drain; split it out so a bar pinned at 50% makes sense
                float onBuilds = Math.max(0, eco.energyExpense - eco.energyConverted);
                if (eco.conversionCapacity > 0)
                    tip = List.of(line("Energy  " + resources.wood + " / " + Math.round(eco.energyStorage)),
                        line("+" + fmt(eco.energyIncome) + "/s from generators, -" + fmt(onBuilds) + "/s on builds"),
                        line("-" + fmt(eco.energyConverted) + "/s into converters (max " + fmt(eco.conversionCapacity)
                            + "/s) -> +" + fmt(eco.metalConverted) + " metal/s"),
                        line("Converters only use energy above "
                            + Math.round(EconomyServerEvents.CONVERSION_THRESHOLD * 100) + "% storage ("
                            + Math.round(eco.energyStorage * EconomyServerEvents.CONVERSION_THRESHOLD) + "),"),
                        line("so energy sitting there means they're running, not that you're short."));
                else
                    tip = List.of(line("Energy  " + resources.wood + " / " + Math.round(eco.energyStorage)),
                        line("+" + fmt(eco.energyIncome) + "/s from generators, -" + fmt(onBuilds) + "/s on builds"),
                        line("Wind generators are cheap; keep them behind the base."));
            }
            else if (mouseX >= px && mouseX < px + POP_W)
                tip = List.of(line("Population " + popStr + "  (" + workers + " workers)"),
                    line("Build houses or more capitols to raise supply."));
            // deferred: HudClientEvents draws exactly one HUD tooltip per frame, and a hovered button wins
            if (tip != null)
                HudClientEvents.deferTooltip(tip, mouseX + 6, mouseY + 12);
        }
        return new Layout(left, left + totalW, bottom);
    }

    private static void renderResource(GuiGraphics gg, Font font, int x, int y, int w, boolean metal,
                                       int amount, float storage, float income, float expense) {
        int fillCol = metal ? METAL_FILL : ENERGY_FILL;
        int dimCol = metal ? METAL_DIM : ENERGY_DIM;
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("minecraft",
            metal ? "textures/item/iron_ingot.png" : "textures/item/redstone.png");
        MyRenderer.renderIcon(gg, icon, x + 5, y + 4, 12);

        int textX = x + 21;
        int rightX = x + w - 6;
        // amount (bright) / storage (dim)
        String amt = String.valueOf(amount);
        String cap = " / " + Math.round(storage);
        gg.drawString(font, amt, textX, y + 4, 0xFFFFFF);
        gg.drawString(font, cap, textX + font.width(amt), y + 4, 0x9AA0A6);

        // net at the far right of the top line, income/expense pair under it at the bar's end
        float net = income - expense;
        String netStr = (net >= 0 ? "+" : "-") + fmt(Math.abs(net));
        gg.drawString(font, netStr, rightX - font.width(netStr), y + 4, net >= 0 ? 0x55FF55 : 0xFF5555);

        // the fill bar
        int barY = y + 16, barH = 7;
        float fill = storage > 0 ? Math.max(0, Math.min(1, amount / storage)) : 0;
        gg.fill(textX, barY, rightX, barY + barH, dimCol);
        gg.fill(textX, barY, textX + Math.round((rightX - textX) * fill), barY + barH, fillCol);
        // storage-full tick: BAR wastes income at full storage, so make the cap visible
        if (fill >= 0.999f)
            gg.fill(rightX - 1, barY - 1, rightX + 1, barY + barH + 1, 0xFFFFFFFF);

        String inc = "+" + fmt(income), exp = "-" + fmt(expense);
        int ex = rightX - font.width(exp);
        int ix = ex - 5 - font.width(inc);
        gg.drawString(font, inc, ix, y + 24, 0x55FF55, false);
        gg.drawString(font, exp, ex, y + 24, 0xFF5555, false);
    }

    // Hysteresis so the label doesn't flicker as the server's 1 s waste average bounces around zero: show after
    // WASTE_SHOW_MS of continuous waste, keep for WASTE_HOLD_MS after it stops. The shown rate is smoothed.
    private static final long WASTE_SHOW_MS = 1000, WASTE_HOLD_MS = 1500;
    private static long metalWasteSince = -1, energyWasteSince = -1;
    private static long metalWasteLast = -1, energyWasteLast = -1;
    private static float metalWasteRate = 0, energyWasteRate = 0;

    /** @param net per second truly wasted (overflow nobody on the team had room for), from the server */
    private static boolean updateWaste(boolean metal, float net) {
        long now = System.currentTimeMillis();
        boolean atCap = net > 0.05f;
        long since = metal ? metalWasteSince : energyWasteSince;
        long last = metal ? metalWasteLast : energyWasteLast;
        float rate = metal ? metalWasteRate : energyWasteRate;
        if (atCap) {
            if (since < 0)
                since = now;
            last = now;
            rate = rate <= 0 ? net : rate + (net - rate) * 0.1f;
        } else if (last < 0 || now - last > WASTE_HOLD_MS) {
            since = -1;
            rate = 0;
        }
        if (metal) { metalWasteSince = since; metalWasteLast = last; metalWasteRate = rate; }
        else { energyWasteSince = since; energyWasteLast = last; energyWasteRate = rate; }
        return since >= 0 && now - since >= WASTE_SHOW_MS;
    }

    // "WASTING +X/s" in the resource's bar colour, pulsing slowly (~1.2 s) on a dark chip so it stays readable;
    // anchored at the bar's left edge for metal and right edge for energy, under the bar/stall warning
    private static void renderWasteLabel(GuiGraphics gg, Font font, List<RectZone> zones, int anchorX, int y,
                                         boolean metal, float rate) {
        String s = "WASTING +" + fmt(rate) + "/s";
        int w = font.width(s) + 8;
        int x = metal ? anchorX : anchorX - w;
        double phase = (System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2;
        int alpha = 0x90 + (int) (0x6F * (0.5 + 0.5 * Math.sin(phase)));
        int col = ((metal ? METAL_FILL : ENERGY_FILL) & 0x00FFFFFF) | (alpha << 24);
        gg.fill(x, y, x + w, y + 12, 0xC0101216);
        gg.fill(x, y + 11, x + w, y + 12, col);
        gg.drawString(font, s, x + 4, y + 2, col);
        zones.add(RectZone.getZoneByLW(x, y, w, 12));
    }

    // "sharing +X/s": full storage, but the overflow is going to allies with room (BAR), so nothing is lost.
    // Small and steady (no pulse) - it's information, not a warning
    private static void renderShareLabel(GuiGraphics gg, Font font, List<RectZone> zones, int anchorX, int y,
                                         boolean metal, float rate) {
        String s = "sharing +" + fmt(rate) + "/s";
        int w = font.width(s) + 8;
        int x = metal ? anchorX : anchorX - w;
        gg.fill(x, y, x + w, y + 12, 0xA0101216);
        gg.drawString(font, s, x + 4, y + 2, 0x8FD18F);
        zones.add(RectZone.getZoneByLW(x, y, w, 12));
    }

    static String fmt(float perSecond) {
        if (perSecond < 10f)
            return String.valueOf(Math.round(perSecond * 10f) / 10f);
        return String.valueOf(Math.round(perSecond));
    }

    static FormattedCharSequence line(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /**
     * Which pool is causing the stall. Comparing raw expenses was wrong (energy numbers are always bigger), so
     * this compares how full each pool is: the emptier one (relative to its storage) is the one you're short on.
     */
    static boolean shortOnMetal(Resources resources, ClientEconomy eco) {
        float metalFill = resources.ore / Math.max(1f, eco.metalStorage);
        float energyFill = resources.wood / Math.max(1f, eco.energyStorage);
        return metalFill <= energyFill;
    }
}
