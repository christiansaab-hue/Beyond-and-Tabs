package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.SoulWisps;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Vindicator;

import javax.annotation.Nullable;

/**
 * T2 constructor - the <b>Embalmer</b> (BAR's T2 constructor; design-factions.md). Trained at the T2 lab, builds at
 * double speed with twice the health of a worker, and is the only worker that can raise the T3 lab.
 */
public class EmbalmerUnit extends ZombieVillagerUnit {

    public static final float BUILD_POWER = 2.0f;

    // ZombieVillagerUnit clones its shared static set, so adding to that would give every Gravedigger the wisps: the
    // Embalmer keeps its own. No initializer on purpose - ZombieVillagerUnit's constructor fills it through
    // updateAbilityButtons() before this class' field initializers would run, and one would wipe it again.
    private Abilities embalmerAbilities;

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        embalmerAbilities = ZombieVillagerUnit.ABILITIES.clone();
        embalmerAbilities.add(new SoulWisps(), Keybindings.abilitySlot1);
    }

    @Override
    public Abilities getAbilities() {
        if (embalmerAbilities == null)
            updateAbilityButtons();
        return embalmerAbilities;
    }

    public EmbalmerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.EMBALMER; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return ZombieVillagerUnit.createAttributes().add(Attributes.MAX_HEALTH, ZombieVillagerUnit.maxHealth * 2);
    }
}
