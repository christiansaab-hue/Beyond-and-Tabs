package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.piglin.Piglin;

import javax.annotation.Nullable;

/**
 * T2 constructor - the <b>Bonewright</b> (BAR's T2 constructor; design-factions.md). Trained at the T2 lab, builds at
 * double speed with twice the health of a worker, and is the only worker that can raise the T3 lab.
 */
public class BonewrightUnit extends GruntUnit {

    public static final float BUILD_POWER = 2.0f;

    public BonewrightUnit(EntityType<? extends Piglin> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.BONEWRIGHT; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GruntUnit.createAttributes().add(Attributes.MAX_HEALTH, GruntUnit.maxHealth * 2);
    }
}
