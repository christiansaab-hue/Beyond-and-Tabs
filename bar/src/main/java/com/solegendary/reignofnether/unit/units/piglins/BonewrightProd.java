package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class BonewrightProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Bonewright";
    public final static ResourceCost cost = ResourceCosts.BONEWRIGHT;

    public BonewrightProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                placement.produceUnit((ServerLevel) level, EntityRegistrar.BONEWRIGHT_UNIT.get(), placement.ownerName, false);
        };
    }

    public String getItemName() {
        return BonewrightProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/grunt.png"),
                List.of(
                        Component.translatable("entity.reignofnether.bonewright_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.bonewright_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.bonewright_unit.tooltip2").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        List<FormattedCharSequence> tooltipLines = new ArrayList<>(List.of(
            Component.translatable("entity.reignofnether.bonewright_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
            ResourceCosts.getFormattedCost(cost),
            ResourceCosts.getFormattedPopAndTime(cost),
            FormattedCharSequence.forward("", Style.EMPTY),
            Component.translatable("entity.reignofnether.bonewright_unit.tooltip1").getVisualOrderText(),
            Component.translatable("entity.reignofnether.bonewright_unit.tooltip2").getVisualOrderText()
        ));

        return new StartProductionButton(
            BonewrightProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/grunt.png"),
            hotkey,
            () -> false,
            () -> true,
            tooltipLines,
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            BonewrightProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/grunt.png"),
            prodBuilding,
            this,
            first
        );
    }
}
