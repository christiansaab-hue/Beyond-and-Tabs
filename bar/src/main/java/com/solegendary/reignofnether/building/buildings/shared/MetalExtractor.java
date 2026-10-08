package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * BAR's metal extractor: the backbone of the economy. May only be placed on a metal patch (MetalPatches), gives a
 * steady metal income, and taking map control means taking more patches. One shared structure; each faction
 * registers its own instance.
 */
public class MetalExtractor extends Building {

    public final static String buildingName = "Metal Extractor";
    public final static String structureName = "metal_extractor";
    public final static ResourceCost cost = ResourceCosts.METAL_EXTRACTOR;

    public MetalExtractor() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.RAW_IRON_BLOCK;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/raw_iron_block.png");

        this.metalIncome = 2.0f;

        this.explodeChance = 0.2f;
        this.maxHealth = 40d;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/raw_iron_block.png"),
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("buildings.reignofnether.metal_extractor").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip1").withStyle(MyRenderer.iconStyle).getVisualOrderText(),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip2").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip3").getVisualOrderText()
                ),
                this
        );
    }
}
