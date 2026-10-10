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
 * BAR's energy converter (metal maker): while its owner's energy is above half of storage, each converter turns
 * up to {@link #CONVERSION} energy/s into metal at {@code EconomyServerEvents.CONVERSION_RATIO} energy per metal.
 * Faction faces: Sunforged alchemist's crucible (lava), Gravebound soul furnace, Horde smelter pit (magma).
 * Fragile and explodes readily - BAR players know to keep converters away from the front.
 */
public class EnergyConverter extends Building {

    public final static String buildingName = "Energy Converter";
    public final static String structureName = "energy_converter";
    public final static ResourceCost cost = ResourceCosts.ENERGY_CONVERTER;
    public static final float CONVERSION = 50f;

    public EnergyConverter() {
        this("");
    }

    /** variant: "" (Sunforged crucible), "_dark" (Gravebound soul furnace) or "_nether" (Horde smelter). */
    public EnergyConverter(String variant) {
        super(structureName + variant, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.BLAST_FURNACE;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/blast_furnace_front_on.png");

        this.energyConversion = CONVERSION;

        this.explodeChance = 0.5f;
        this.maxHealth = 30d;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/blast_furnace_front_on.png"),
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("buildings.reignofnether.energy_converter").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.energy_converter.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.energy_converter.tooltip2").getVisualOrderText()
                ),
                this
        );
    }
}
