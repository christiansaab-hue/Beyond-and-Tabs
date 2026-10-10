package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.resources.EconomyClientEvents.ClientEconomy;
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
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int panelW = Math.max(PANEL_W_MIN, Math.min(PANEL_W_MAX, (screenWidth - 200 - POP_W) / 2));
        int totalW = panelW * 2 + POP_W;
        int left = screenWidth / 2 - totalW / 2;
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

        // tooltips
        if (mouseY >= top && mouseY < top + PANEL_H) {
            List<FormattedCharSequence> tip = null;
            if (mouseX >= left && mouseX < left + panelW)
                tip = List.of(line("Metal  " + resources.ore + " / " + Math.round(eco.metalStorage)),
                    line("+" + fmt(eco.metalIncome) + "/s from extractors, -" + fmt(eco.metalExpense) + "/s on builds"),
                    line("Build more extractors on metal patches to grow income."));
            else if (mouseX >= left + panelW + POP_W && mouseX < left + totalW)
                tip = List.of(line("Energy  " + resources.wood + " / " + Math.round(eco.energyStorage)),
                    line("+" + fmt(eco.energyIncome) + "/s from generators, -" + fmt(eco.energyExpense) + "/s on builds"),
                    line("Wind generators are cheap; keep them behind the base."));
            else if (mouseX >= px && mouseX < px + POP_W)
                tip = List.of(line("Population " + popStr + "  (" + workers + " workers)"),
                    line("Build houses or more capitols to raise supply."));
            if (tip != null)
                MyRenderer.renderTooltip(gg, tip, mouseX + 6, mouseY + 12);
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
