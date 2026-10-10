package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Vindicator;

import javax.annotation.Nullable;

/**
 * T2 constructor - the <b>Royal Architect</b> (BAR's T2 constructor; design-factions.md). Trained at the T2 lab, builds at
 * double speed with twice the health of a worker, and is the only worker that can raise the T3 lab.
 */
public class RoyalArchitectUnit extends VillagerUnit {

    public static final float BUILD_POWER = 2.0f;

    public RoyalArchitectUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.ROYAL_ARCHITECT; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return VillagerUnit.createAttributes().add(Attributes.MAX_HEALTH, VillagerUnit.maxHealth * 2);
    }
}
