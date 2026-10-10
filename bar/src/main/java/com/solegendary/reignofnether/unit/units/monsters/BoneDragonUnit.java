package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Gravebound T3 experimental - the <b>Bone Dragon</b> (design-factions.md): a skeletal wyrm that flies over walls
 * and lines and breathes soul-fire. Built on the Ghast unit's flying RTS brain (movement, targeting, ranged
 * attack), drawn by {@code BoneDragonRenderer} as a huge phantom-bodied wyrm. T3 rules: ~4.5x the Siege Ox's
 * cost; weakness = it flies low and has no armour, so anti-air and ranged units shred it; no instant-win tricks.
 * Arrival announced to every player. Not yet: kills rising as skeletons for 20 s.
 */
public class BoneDragonUnit extends GhastUnit {

    final static public float attackDamage = 20.0f;
    final static public float attacksPerSecond = 0.3f;
    final static public float maxHealth = 900.0f;
    final static public float movementSpeed = 0.2f;
    final static public float attackRange = 22;
    final static public float aggroRange = 22;
    public static final float SCALE = 3.5f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public BoneDragonUnit(EntityType<? extends Ghast> entityType, Level level) {
        super(entityType, level);
    }

    /** GhastUnit rebuilds its abilities from the shared static set here; add this unit's own faction power on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog)
                return;
        getAbilities().add(new com.solegendary.reignofnether.ability.abilities.WitheringFog());
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.BONE_DRAGON; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.ARMOR, 0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 26)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0.3f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.bone_dragon_unit"));
        }
    }
}
