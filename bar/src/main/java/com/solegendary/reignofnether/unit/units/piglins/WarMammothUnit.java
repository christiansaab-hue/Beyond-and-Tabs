package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Ironhide Horde T3 experimental - the <b>War Mammoth</b> (design-factions.md). A titan built on the Siege Ox's
 * body and brain, drawn at 1.7x scale. Follows the four T3 rules: costs ~4.5x a T2 heavy (the Siege Ox), has a
 * clear weakness (very slow, melee only - no answer to flyers or kiting artillery), loses to an equal-cost focused
 * T2 army, and has no instant-win tricks. Its arrival is announced to every player.
 *
 * Not done yet (design): the archer platform crew and the 60 s Trample charge; fire panic. Placeholder look: the
 * ravager model, scaled.
 */
public class WarMammothUnit extends RavagerUnit {

    final static public float attackDamage = 18.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 1100.0f;
    final static public float movementSpeed = 0.17f;
    final static public float attackRange = 3;
    final static public float aggroRange = 12;
    public static final float SCALE = 1.7f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public WarMammothUnit(EntityType<? extends Ravager> entityType, Level level) {
        super(entityType, level);
    }

    /** RavagerUnit rebuilds its ability list from the shared static set here; add the Mammoth's own on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.Trample)
                return;
        getAbilities().add(new com.solegendary.reignofnether.ability.abilities.Trample());
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.WAR_MAMMOTH; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.war_mammoth_unit"));
        }
    }
}
