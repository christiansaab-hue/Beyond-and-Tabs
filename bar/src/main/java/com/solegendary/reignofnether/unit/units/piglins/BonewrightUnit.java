package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.TotemOfThePack;
import com.solegendary.reignofnether.keybinds.Keybindings;
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

    // GruntUnit used to hand out its shared static set (fixed: it clones now), and adding to that gave every Grunt the
    // totem: the Bonewright keeps its own. No initializer on purpose - GruntUnit's constructor fills it through
    // updateAbilityButtons() before this class' field initializers would run, and one would wipe it again.
    private Abilities bonewrightAbilities;

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        // the type's own ability is added only when missing: a re-run (client cooldown sync) keeps the previous one
        // and any commander abilities added at runtime, instead of wiping them
        Abilities prev = bonewrightAbilities;
        bonewrightAbilities = Abilities.cloneKeepingExtras(GruntUnit.ABILITIES, prev);
        boolean has = false;
        for (var a : bonewrightAbilities.get())
            if (a instanceof TotemOfThePack)
                has = true;
        if (!has)
            bonewrightAbilities.add(new TotemOfThePack(), Keybindings.abilitySlot1);
    }

    @Override
    public Abilities getAbilities() {
        if (bonewrightAbilities == null)
            updateAbilityButtons();
        return bonewrightAbilities;
    }

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
