package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * BAR's storage (metal/energy storage): a small 3x3 strongroom that raises its owner's caps by
 * {@link #METAL_STORAGE} metal and {@link #ENERGY_STORAGE} energy. Answers lovish's playtest where both bars sat at
 * the cap from the first minute and income was wasted all game: storage lets a player bank a float for a push
 * instead of throwing it away. Cheap and fragile-ish; when one dies, whatever it held above the new cap is lost
 * (EconomyServerEvents.onStorageLost) and bursts out of the ruin.
 * Faction faces: Sunforged Treasury, Gravebound Ossuary Vault, Horde Plunder Pit, Verdant Seed Cache,
 * Tidewrought Bilge Hold.
 */
public class StorageVault extends Building {

    public final static String buildingName = "Storage Vault";
    public final static String structureName = "storage_vault";
    public final static ResourceCost cost = ResourceCosts.STORAGE_VAULT;
    public static final float METAL_STORAGE = 2000f;
    public static final float ENERGY_STORAGE = 3000f;

    public StorageVault() {
        this("");
    }

    /** variant: "" (Sunforged), "_dark" (Gravebound), "_nether" (Horde), "_verdant" (Verdant Court) or "_tide" (Tidewrought). */
    public StorageVault(String variant) {
        super(structureName + variant, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.BARREL;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/barrel_top.png");

        this.metalStorage = METAL_STORAGE;
        this.energyStorage = ENERGY_STORAGE;

        // sturdier than a converter (30) but well short of a stockpile (95): raids that reach it should hurt
        this.explodeChance = 0.4f;
        this.maxHealth = 60d;
    }

    @Override
    public void destroy(ServerLevel serverLevel, BuildingPlacement placement) {
        // a site that never finished gave no storage, so it has nothing to lose
        if (!placement.isBuilt || placement.ownerName == null || placement.ownerName.isEmpty())
            return;
        float[] lost = EconomyServerEvents.onStorageLost(placement.ownerName, METAL_STORAGE, ENERGY_STORAGE);
        // the resource burst: what it held sprays out of the ruin (ingots for metal, sparks for energy), scaled by
        // the loss and capped so a full vault dying in a big fight stays cheap
        double x = placement.centrePos.getX() + 0.5, y = placement.centrePos.getY() + 1.5, z = placement.centrePos.getZ() + 0.5;
        int metalCount = Math.min(30, (int) (lost[0] / 40));
        int energyCount = Math.min(40, (int) (lost[1] / 40));
        if (metalCount > 0)
            serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_INGOT)),
                x, y, z, metalCount, 0.6, 0.4, 0.6, 0.25);
        if (energyCount > 0) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, energyCount, 0.8, 0.6, 0.8, 0.4);
            serverLevel.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/barrel_top.png"),
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("buildings.reignofnether.storage_vault").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.storage_vault.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.storage_vault.tooltip2").getVisualOrderText()
                ),
                this
        );
    }
}
