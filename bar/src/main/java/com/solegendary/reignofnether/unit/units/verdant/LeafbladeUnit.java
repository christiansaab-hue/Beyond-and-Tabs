package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.abilities.LeafDash;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

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
 * Verdant Court raider - the <b>Leafblade</b> (design/verdant_court_plan.md, slice 1): an elven blade-dancer in leaf
 * green (its own texture on the illager body, LeafbladeRenderer). Lighter and faster than the Halberdier it is built
 * on, it trades health for reach: {@link LeafDash} carries it {@link LeafDash#LENGTH} blocks onto a target, nicking
 * whatever it passes. Extends VindicatorUnit for the melee brain and the attack animation sync (the Sun Colossus
 * extends the Iron Golem the same way); the Kingdom's vindicator research and enchantments only ever apply to units
 * their owner's Sunforged buildings made, so they never reach a Leafblade.
 */
public class LeafbladeUnit extends VindicatorUnit {

    final static public float attackDamage = 5.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float maxHealth = 50.0f;
    final static public float movementSpeed = 0.32f;
    final static public float attackRange = 2;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.1f;

    public LeafbladeUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.LEAFBLADE; }

    // one dash per Leafblade, kept across refreshes so its cooldown (keyed by the ability) survives them. No
    // initializer on purpose: the first refresh runs inside VindicatorUnit's constructor, before field initializers
    private LeafDash dash;

    /** VindicatorUnit rebuilds its abilities from its shared static set here; add this unit's dash on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (dash == null)
            dash = new LeafDash();
        if (!getAbilities().get().contains(dash))
            getAbilities().add(dash, com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
    }

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

    // an elf, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // a slim blade instead of the Halberdier's axe; visual only (damage is RoN's attack attribute)
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }
}
