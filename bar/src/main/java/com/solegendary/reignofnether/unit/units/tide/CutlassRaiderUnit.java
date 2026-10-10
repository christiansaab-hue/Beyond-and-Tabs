package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tide.TidalUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Tidewrought raider - the <b>Cutlass Raider</b> (design/tidewrought_plan.md, slice 1): a corsair with a cutlass on
 * the illager body (its own skin, CutlassRaiderRenderer), quick and light like the Verdant Leafblade it is costed
 * against. <b>Ambush</b>: stepping out of water or off a tidepool primes its blade - the first hit landed within
 * {@link #AMBUSH_TICKS} deals {@link #AMBUSH_BONUS} more. The tactic is to wade up a tidepool (or a river) and come
 * out swinging.
 * <p>
 * Cost: one timestamp field. The step out of the water is seen by the Tides pass the unit already gets
 * (TidesServerEvents, via {@link TidalUnit#onLeftWater}); the bonus is read where RoN computes melee damage
 * ({@link #getUnitAttackDamage}) and spent in {@link #doHurtTarget}. No per-tick work, no packets.
 * Extends VindicatorUnit for the melee brain and attack-animation sync, as the Leafblade does.
 */
public class CutlassRaiderUnit extends VindicatorUnit implements TidalUnit {

    final static public float attackDamage = 5.5f;
    final static public float attacksPerSecond = 0.6f;
    final static public float maxHealth = 55.0f;
    final static public float movementSpeed = 0.31f;
    final static public float attackRange = 2;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.1f;

    /** How long after leaving the water the Ambush stays primed. */
    public static final int AMBUSH_TICKS = 3 * 20;
    /** Extra damage on the Ambush hit (+40%). */
    public static final float AMBUSH_BONUS = 0.4f;

    /** Game time until which the next hit is an Ambush (0 = not primed). */
    private long ambushUntil = 0;

    public CutlassRaiderUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.CUTLASS_RAIDER; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Is the Ambush primed right now (left the water less than {@link #AMBUSH_TICKS} ago, no hit since)? */
    public boolean isAmbushPrimed() {
        return ambushUntil > 0 && level().getGameTime() <= ambushUntil;
    }

    /** Primes the Ambush (the Tides pass calls this; public for the game test). */
    @Override
    public void onLeftWater(long gameTime) {
        ambushUntil = gameTime + AMBUSH_TICKS;
    }

    // read by LivingEntityMixin.actuallyHurt when the blow lands, so the bonus rides RoN's own damage rules (armour,
    // resistances, crits) instead of a second hurt() call that the target's invulnerability frames would swallow
    @Override
    public float getUnitAttackDamage() {
        float dmg = super.getUnitAttackDamage();
        return isAmbushPrimed() ? dmg * (1 + AMBUSH_BONUS) : dmg;
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity pEntity) {
        boolean primed = isAmbushPrimed();
        boolean hurt = super.doHurtTarget(pEntity);
        if (hurt && primed) {
            ambushUntil = 0;   // one Ambush per trip out of the water
            if (level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.SPLASH, pEntity.getX(), pEntity.getY() + pEntity.getBbHeight() * 0.6,
                        pEntity.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        }
        return hurt;
    }

    // a corsair, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;   // amphibious (design/tidewrought_plan.md section 6)
    }

    // a cutlass (an iron sword) instead of the Halberdier's axe; visual only (damage is RoN's attack attribute)
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }
}
