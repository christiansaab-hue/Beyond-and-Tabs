package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * BAR veterancy: a unit earns experience for every enemy unit it kills, worth (victim's metal cost / its own metal
 * cost) - so a cheap unit that kills something expensive ranks up at once, and a heavy farming cheap units barely
 * moves. Each whole point of experience is a rank, up to {@link #MAX_RANK}: +{@link #HP_PER_RANK} max health and
 * +{@link #DMG_PER_RANK} melee damage per rank. Experience lives in the entity's persistent data and the modifiers
 * are permanent attribute modifiers, so veterans stay veterans across a save and reload.
 */
public class VeterancyServerEvents {

    public static final int MAX_RANK = 3;
    public static final double HP_PER_RANK = 0.10;
    public static final double DMG_PER_RANK = 0.10;
    static final String KEY_XP = "bt_veteran_xp";
    static final UUID HP_MOD = UUID.fromString("c3d1f2e4-5a6b-4c7d-8e9f-0a1b2c3d4e01");
    static final UUID DMG_MOD = UUID.fromString("c3d1f2e4-5a6b-4c7d-8e9f-0a1b2c3d4e02");

    public static float getXp(LivingEntity le) {
        return le.getPersistentData().getFloat(KEY_XP);
    }

    public static int getRank(LivingEntity le) {
        return Math.min(MAX_RANK, (int) Math.floor(getXp(le)));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide() || !(victim instanceof Unit v)
                || !(evt.getSource().getEntity() instanceof LivingEntity killer) || !(killer instanceof Unit k)
                || !killer.isAlive())
            return;
        String ko = k.getOwnerName(), vo = v.getOwnerName();
        if (ko == null || ko.equals(vo) || AlliancesServerEvents.isAllied(ko, vo))
            return;
        ResourceCost vc = v.getCost(), kc = k.getCost();
        float victimMetal = vc == null ? 0 : vc.metal();
        if (victimMetal <= 0)
            return;
        float ownMetal = Math.max(10f, kc == null ? 10f : kc.metal());
        award(killer, victimMetal / ownMetal);
    }

    /** Adds experience and applies any new rank. Public for the game test. */
    public static void award(LivingEntity unit, float xp) {
        int before = getRank(unit);
        unit.getPersistentData().putFloat(KEY_XP, getXp(unit) + xp);
        int after = getRank(unit);
        if (after <= before)
            return;
        float hpFrac = unit.getHealth() / unit.getMaxHealth();
        setModifier(unit.getAttribute(Attributes.MAX_HEALTH), HP_MOD, "bt_veteran_hp", HP_PER_RANK * after);
        setModifier(unit.getAttribute(Attributes.ATTACK_DAMAGE), DMG_MOD, "bt_veteran_dmg", DMG_PER_RANK * after);
        setModifier(unit.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()), DMG_MOD, "bt_veteran_dmg", DMG_PER_RANK * after);   // what units really deal
        unit.setHealth(unit.getMaxHealth() * hpFrac);
        if (unit.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, unit.getX(), unit.getY() + unit.getBbHeight() + 0.3,
                unit.getZ(), 6 * after, 0.3, 0.2, 0.3, 0.02);
            sl.playSound(null, unit.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5f, 1.2f + 0.2f * after);
        }
    }

    static void setModifier(AttributeInstance attr, UUID id, String name, double amount) {
        if (attr == null)
            return;
        if (attr.getModifier(id) != null)
            attr.removeModifier(id);
        attr.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
