package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Sunforged Kingdom T3 experimental - the <b>Sun Colossus</b> (design-factions.md): a gilded paladin-statue built
 * on the Iron Golem's body and brain, drawn at {@link #SCALE}x. T3 rules: ~4.5x the Siege Ox's cost, weakness =
 * very slow and nothing to hit flyers with, loses to a focused equal-cost army, Solar Lance is telegraphed.
 * Arrival announced to every player. Placeholder look: the iron golem model, scaled.
 */
public class SunColossusUnit extends IronGolemUnit {

    final static public float attackDamage = 22.0f;
    final static public float attacksPerSecond = 0.35f;
    final static public float maxHealth = 1000.0f;
    final static public float movementSpeed = 0.16f;
    final static public float attackRange = 4;
    final static public float aggroRange = 12;
    public static final float SCALE = 2.2f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public SunColossusUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SUN_COLOSSUS; }

    /** IronGolemUnit rebuilds its abilities from the shared static set here; add the Colossus' own on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance)
                return;
        getAbilities().add(new com.solegendary.reignofnether.ability.abilities.SolarLance());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.2)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.sun_colossus_unit"));
        }
    }
}
