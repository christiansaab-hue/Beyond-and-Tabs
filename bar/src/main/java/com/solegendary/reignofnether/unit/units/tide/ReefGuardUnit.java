package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tide.TidalUnit;
import com.solegendary.reignofnether.tide.TidesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Tidewrought line infantry - the <b>Reef Guard</b> (design/tidewrought_plan.md, slice 1): a brass-and-coral armoured
 * marine with a trident and a shield on the illager body (ReefGuardRenderer). It holds the line: tougher than the
 * Halberdier and the shield soaks arrows ({@link #rangedDamageResist}), and in water or on a tidepool it regenerates
 * 3 HP/s instead of the faction's 2 ({@link #tidesRegenBonus}) - park it in a Tide Priest's pool and it outlasts
 * anything its cost. The shield is passive in slice 1; a raise/lower toggle like the Brute's (ToggleShield, which is
 * written for the Brute class) is a later slice's business. Extends VindicatorUnit for the melee brain and attack sync.
 */
public class ReefGuardUnit extends VindicatorUnit implements TidalUnit {

    final static public float attackDamage = 5.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 80.0f;
    final static public float movementSpeed = 0.25f;
    final static public float attackRange = 2;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.45f;   // the shield (Halberdier 0.2, Brute with raised shield ~0.6)

    /** 0.5 HP more per Tides pass = 3 HP/s while wet (TidesServerEvents gives every tidal unit 2 HP/s). */
    public static final float WET_REGEN_BONUS = 0.5f;

    public ReefGuardUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.REEF_GUARD; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override
    public float tidesRegenBonus() {
        return WET_REGEN_BONUS;
    }

    /** HP/s while wet, for the tooltip and the game test. */
    public static float wetRegenPerSecond() {
        return (TidesServerEvents.REGEN_PER_PASS + WET_REGEN_BONUS) * 20f / TidesServerEvents.PASS_TICKS;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;   // amphibious (design/tidewrought_plan.md section 6)
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.SHIELD_BLOCK;
    }

    // a trident and a shield; visual only (damage is RoN's attack attribute, the shield is the ranged resist above)
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }
}
