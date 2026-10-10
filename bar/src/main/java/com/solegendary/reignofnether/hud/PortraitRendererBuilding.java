package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.building.buildings.monsters.SculkCatalyst;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.healthbars.HealthBarClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.util.LanguageUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Quaternionf;

// Renders a Building's portrait including an animated block, name, healthbar, list of stats and UI frames

public class PortraitRendererBuilding {
    public int frameWidth = 60;
    public int frameHeight = 60;
    public int xBlock = 31;
    public int yBlock = 25;

    public PortraitRendererBuilding() {
    }

    // Render the portrait including:
    // - background frame
    // - block model representing the building
    // - healthbar
    // - building name
    // Must be called from DrawScreenEvent
    public RectZone render(GuiGraphics guiGraphics, int x, int y, BuildingPlacement building) {
        return render(guiGraphics, x, y, building, Integer.MAX_VALUE);
    }

    // maxNameWidth: pixels available for the title line (the command panel's width) - long names like
    // "Conversion Pylon (+2 metal/s)" shrink then ellipsize at a word boundary instead of running over
    // the panel edge or neighbouring widgets
    public RectZone render(GuiGraphics guiGraphics, int x, int y, BuildingPlacement building, int maxNameWidth) {
        Relationship rs = BuildingClientEvents.getPlayerToBuildingRelationship(building);

        String name = "";
        if (building.getBuilding() instanceof CustomBuilding customBuilding) {
            name = customBuilding.name;
        } else {
            ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(building.getBuilding());
            if (key != null) {
                name = LanguageUtil.getTranslation("buildings." + key.getNamespace() + "." + key.getPath());
            }
        }

        if (building.getUpgradeLevel() > 0)
            name = building.getUpgradedName();

        if (!building.isBuilt)
            name += " (" + (int) (building.getBlocksPlacedPercent() * 100) + "%)";

        if (rs != Relationship.OWNED && !building.ownerName.isBlank())
            name += " (" + building.ownerName + ")";

        // BAR-style income readout for economy buildings (extractors, generators, farms, houses)
        if (building.isBuilt) {
            float mIn = building.getMetalIncome();
            float eIn = building.getEnergyIncome();
            if (mIn > 0 || eIn > 0) {
                StringBuilder sb = new StringBuilder(" (");
                if (mIn > 0)
                    sb.append("+").append(trimFloat(mIn)).append(" ").append(I18n.get("hud.reignofnether.metal_per_s"));
                if (mIn > 0 && eIn > 0)
                    sb.append(", ");
                if (eIn > 0)
                    sb.append("+").append(trimFloat(eIn)).append(" ").append(I18n.get("hud.reignofnether.energy_per_s"));
                name += sb.append(")").toString();
            }
        }

        if (building instanceof SculkCatalystPlacement sc && building.getBuilding().hasActiveAddon(NightSourceAddon.class) && building.isBuilt)
            name += " (" + I18n.get("hud.buildings.reignofnether.sculk_catalyst.range", building.getBuilding().getActiveAddon(NightSourceAddon.class).getNightRange(building), SculkCatalyst.MAX_NIGHT_RANGE) + ")";

        // draw name
        drawFittedName(guiGraphics, name, x+4, y-9, maxNameWidth);
        int bgCol = PlayerColors.getPlayerPortraitDisplayColorHex(building.ownerName);
        MyRenderer.renderFrameWithBg(guiGraphics, x, y,
                frameWidth,
                frameHeight,
                bgCol);

        drawBlockOnScreen(x, y, building.getBuilding().portraitBlock, 5.5f);

        // draw health bar and write min/max hp
        HealthBarClientEvents.renderForBuilding(guiGraphics.pose(), building,
                x+(frameWidth/2f), y+frameHeight-15,
                frameWidth-9, HealthBarClientEvents.RenderMode.GUI_PORTRAIT);

        guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                building.getHealth() + "/" + building.getMaxHealth(),
                x+(frameWidth/2), y+frameHeight-13,
                0xFFFFFFFF
        );

        return RectZone.getZoneByLW(x, y, frameWidth, frameHeight);
    }

    private static final float NAME_SHRINK_SCALE = 0.8f;

    // Draws the title within maxWidth: full size if it fits, else scaled down, else scaled down and cut at the
    // last whole word with an ellipsis ("Conversion Pylon (+2 metal/s, ...)" -> "Conversion Pylon..."), only
    // falling back to a mid-word cut when even the first word is wider than the space. Allocates only when
    // the name is too long, which is a single string on the HUD - not a hot path.
    static void drawFittedName(GuiGraphics guiGraphics, String name, int x, int y, int maxWidth) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int w = font.width(name);
        if (w <= maxWidth) {
            guiGraphics.drawString(font, name, x, y, 0xFFFFFFFF);
            return;
        }
        // in scaled space the available width grows by 1/scale
        int scaledMax = (int) (maxWidth / NAME_SHRINK_SCALE);
        String text = name;
        if (w > scaledMax) {
            final String ellipsis = "..."; // MC font periods are 2px wide, cheaper than a unifont glyph
            String cut = name;
            while (true) {
                int sp = cut.lastIndexOf(' ');
                if (sp <= 0)
                    break;
                cut = cut.substring(0, sp);
                // don't leave a dangling "(" or "," before the ellipsis
                String trimmed = cut.replaceAll("[\\s(,]+$", "");
                if (font.width(trimmed + ellipsis) <= scaledMax) {
                    text = trimmed + ellipsis;
                    break;
                }
            }
            if (font.width(text) > scaledMax) // a single word too long for the space: cut mid-word as last resort
                text = font.plainSubstrByWidth(name, Math.max(0, scaledMax - font.width(ellipsis))) + ellipsis;
        }
        guiGraphics.pose().pushPose();
        // shift down a pixel so the smaller text stays vertically centred on the title line
        guiGraphics.pose().translate(x, y + 1, 0);
        guiGraphics.pose().scale(NAME_SHRINK_SCALE, NAME_SHRINK_SCALE, 1.0f);
        guiGraphics.drawString(font, text, 0, 0, 0xFFFFFFFF);
        guiGraphics.pose().popPose();
    }

    /** "2.0" -> "2", "1.5" -> "1.5" */
    private static String trimFloat(float f) {
        return f == Math.floor(f) ? String.valueOf((int) f) : String.valueOf(f);
    }

    public void drawBlockOnScreen(int x, int y, Block block, float blockScale) {
        ItemStack item = new ItemStack(block);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.translate(x+xBlock, y+yBlock, 100.0F);
        poseStack.scale(blockScale, -blockScale, blockScale);
        RenderSystem.applyModelViewMatrix();

        float angle = (System.currentTimeMillis() / 100) % 360;
        Quaternionf quaternion = Axis.XP.rotationDegrees(25);
        Quaternionf quaternion2 = Axis.YP.rotationDegrees(angle);
        quaternion.mul(quaternion2);
        PoseStack blockPoseStack = new PoseStack();
        blockPoseStack.pushPose();
        blockPoseStack.mulPose(quaternion);
        blockPoseStack.scale(8, 8, 8);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        Minecraft.getInstance().getItemRenderer().renderStatic(
                item, ItemDisplayContext.FIXED,
                15728880, OverlayTexture.NO_OVERLAY,
                blockPoseStack, bufferSource, null, 0);
        bufferSource.endBatch();

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
