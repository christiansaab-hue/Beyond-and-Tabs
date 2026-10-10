package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Verdant Court T2 constructor - the <b>Elder Druid</b> (BAR's T2 constructor; design/verdant_court_plan.md, slice 4).
 * Trained at the Circle of Elders, builds at double speed with twice a Seedshaper's health (like the Royal Architect,
 * Embalmer and Bonewright), and is the only Court worker that may raise a T3 lab (T2Workers). Its own power is
 * {@link AwakenThicket}: a Sentinel-Treant-like ally wakes at a chosen spot for 30 s.
 */
public class ElderDruidUnit extends SeedshaperUnit {

    public static final float BUILD_POWER = 2.0f;

    // SeedshaperUnit clones its shared static set, so adding to that would hand every Seedshaper the power: the Elder
    // Druid keeps its own. No initializer on purpose - SeedshaperUnit's constructor fills it through
    // updateAbilityButtons() before this class' field initializers would run, and one would wipe it again.
    private Abilities druidAbilities;

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        // added only when missing: a re-run (client cooldown sync) keeps the previous one and any commander abilities
        Abilities prev = druidAbilities;
        druidAbilities = Abilities.cloneKeepingExtras(SeedshaperUnit.ABILITIES, prev);
        boolean has = false;
        for (var a : druidAbilities.get())
            if (a instanceof AwakenThicket)
                has = true;
        if (!has)
            druidAbilities.add(new AwakenThicket(), Keybindings.abilitySlot1);
    }

    @Override
    public Abilities getAbilities() {
        if (druidAbilities == null)
            updateAbilityButtons();
        return druidAbilities;
    }

    public ElderDruidUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.ELDER_DRUID; }

    @Override
    public float getBuildPower() {
        return com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(this) ? COMMANDER_BUILD_POWER : BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return SeedshaperUnit.createAttributes().add(Attributes.MAX_HEALTH, SeedshaperUnit.maxHealth * 2);
    }
}
