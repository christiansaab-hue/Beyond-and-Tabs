package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared body of every Tidewrought production item (design/tidewrought_plan.md, slice 1): the usual unit buttons built
 * from one lang key ({@code entity.reignofnether.<id>} plus {@code .tooltip1..n}) and the portrait in
 * {@code textures/mobheads/<mobhead>.png}. The Verdant Court's T1 items are each an 80-line copy of the same
 * boilerplate; this is that boilerplate once (VerdantT2Prod does the same for the Court's T2, with a Tier 2 gate).
 * Each subclass keeps its own {@code itemName} constant, which EntityRegistrar's production switch needs as a case label.
 */
public abstract class TideProd extends ProductionItem implements IUnitProductionItem {

    private final String name;
    private final String langKey;      // entity.reignofnether.<x>_unit
    private final int tooltipLines;
    private final ResourceLocation icon;
    private final ResourceCost unitCost;

    protected TideProd(String name, String entityId, String mobhead, int tooltipLines, ResourceCost cost,
                       Supplier<? extends EntityType<? extends Unit>> type, boolean spawnIndoors) {
        super(cost);
        this.name = name;
        this.langKey = "entity.reignofnether." + entityId;
        this.tooltipLines = tooltipLines;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + mobhead + ".png");
        this.unitCost = cost;
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                placement.produceUnit((ServerLevel) level, type.get(), placement.ownerName, spawnIndoors);
        };
    }

    public String getItemName() {
        return name;
    }

    private List<FormattedCharSequence> description() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = 1; i <= tooltipLines; i++)
            lines.add(Component.translatable(langKey + ".tooltip" + i).getVisualOrderText());
        return lines;
    }

    public UnitSpawnButton getPlaceButton() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(description());
        return new UnitSpawnButton(name, icon, lines);
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(langKey).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText());
        lines.add(ResourceCosts.getFormattedCost(unitCost));
        lines.add(ResourceCosts.getFormattedPopAndTime(unitCost));
        lines.add(FormattedCharSequence.forward("", Style.EMPTY));
        lines.addAll(description());
        return new StartProductionButton(name, icon, hotkey,
            () -> false,
            () -> canProduce(prodBuilding),
            lines,
            this);
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(name, icon, prodBuilding, this, first);
    }
}
