package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.UUID;

/**
 * BAR-style commanders. Every player (and bot) starts with one: their first worker made into a named, tough,
 * hard-hitting builder with triple build power. The tag rides in entity NBT, so a commander is still a commander
 * after a reload. With the commanderDefeat gamerule on (default): in a 1v1 (or once you have no allies left)
 * losing your commander loses you the match; in team games the commander goes out in BAR's commander blast
 * and you keep playing with what you have left.
 */
public class CommanderServerEvents {

    public static final String TAG = "rts_commander";
    static final UUID HP_MOD = UUID.fromString("5a6c2a4e-9c1f-4b7e-8f21-0b6c7d7a0c01");
    static final UUID DMG_MOD = UUID.fromString("5a6c2a4e-9c1f-4b7e-8f21-0b6c7d7a0c02");

    public static boolean isCommander(Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    /** Turns a freshly spawned worker into its owner's commander. */
    public static void makeCommander(Entity entity) {
        if (!(entity instanceof LivingEntity le))
            return;
        entity.addTag(TAG);
        ensureAbility(entity);
        entity.setCustomName(Component.translatable("unit.reignofnether.commander"));
        entity.setCustomNameVisible(true);
        AttributeInstance hp = le.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getModifier(HP_MOD) == null) {
            hp.addPermanentModifier(new AttributeModifier(HP_MOD, "commander_hp", 80.0, AttributeModifier.Operation.ADDITION));
            le.setHealth(le.getMaxHealth());
        }
        AttributeInstance dmg = le.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && dmg.getModifier(DMG_MOD) == null)
            dmg.addPermanentModifier(new AttributeModifier(DMG_MOD, "commander_dmg", 6.0, AttributeModifier.Operation.ADDITION));
        // unit damage really comes from RoN's own attack attribute (LivingEntityMixin.actuallyHurt)
        AttributeInstance ronDmg = le.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get());
        if (ronDmg != null && ronDmg.getModifier(DMG_MOD) == null)
            ronDmg.addPermanentModifier(new AttributeModifier(DMG_MOD, "commander_dmg", 6.0, AttributeModifier.Operation.ADDITION));
    }

    /** Gives a commander its faction's signature ability and D-gun once (both sides: the client needs it for the button). */
    public static void ensureAbility(Entity entity) {
        if (!(entity instanceof Unit unit) || unit.getAbilities() == null)
            return;
        boolean hasSignature = false, hasDGun = false, hasRaise = false, hasDrums = false, hasSortie = false, hasOvergrowth = false;
        for (com.solegendary.reignofnether.ability.Ability a : unit.getAbilities().get()) {
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility)
                hasSignature = true;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                hasDGun = true;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.RaiseDead)
                hasRaise = true;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.WarDrums)
                hasDrums = true;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.SunriseSortie)
                hasSortie = true;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.Overgrowth)
                hasOvergrowth = true;
        }
        // factions without a designed signature (FactionTraits: Kind.NONE) get none rather than the Kingdom's Rally
        if (!hasSignature && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(unit)
                != com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.NONE)
            unit.getAbilities().add(new com.solegendary.reignofnether.ability.abilities.CommanderAbility());
        if (!hasDGun)
            unit.getAbilities().add(new com.solegendary.reignofnether.ability.abilities.CommanderDGun());
        var faction = com.solegendary.reignofnether.faction.Factions.getFaction(unit);
        if (faction != null && faction.equals(com.solegendary.reignofnether.faction.Factions.MONSTERS) && !hasRaise)
            unit.getAbilities().add(new com.solegendary.reignofnether.ability.abilities.RaiseDead());
        // the Horde's faction power rides on the Warlord: the drummer leads from the front
        if (faction != null && faction.equals(com.solegendary.reignofnether.faction.Factions.PIGLINS) && !hasDrums)
            unit.getAbilities().add(new com.solegendary.reignofnether.ability.abilities.WarDrums());
        // and the Kingdom's on the Lord Marshal: the sortie is led by the commander at its head
        if (faction != null && faction.equals(com.solegendary.reignofnether.faction.Factions.VILLAGERS) && !hasSortie)
            unit.getAbilities().add(new com.solegendary.reignofnether.ability.abilities.SunriseSortie());
        // and the Court's Grove Warden grows the battlefield itself: Overgrowth, alongside Wildstride and Thornburst
        if (faction != null && faction.equals(com.solegendary.reignofnether.faction.Factions.VERDANT_COURT) && !hasOvergrowth)
            unit.getAbilities().add(new com.solegendary.reignofnether.ability.abilities.Overgrowth());
    }

    /** Client side: tags don't sync, but the commander's translated name does - that's how the client knows. */
    public static boolean looksLikeCommander(Entity entity) {
        return entity.getCustomName() != null
            && entity.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
            && "unit.reignofnether.commander".equals(tc.getKey());
    }

    /** Commanders reloaded from a save get their ability back. */
    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent evt) {
        Entity e = evt.getEntity();
        if (evt.getLevel().isClientSide() ? looksLikeCommander(e) : isCommander(e))
            ensureAbility(e);
    }

    // BAR's commander blast: a 1 s telegraph, then a big blast that hurts everything around, friend and foe
    public static final float COM_BLAST_RADIUS = 8f;
    public static final float COM_BLAST_DAMAGE = 60f;
    /** Hit points taken off each building the blast reaches (moderate: a dent, not a demolition). */
    public static final float COM_BLAST_BUILDING_DAMAGE = 100f;
    public static final int COM_BLAST_DELAY_TICKS = 20;
    static final int COM_BLAST_PULSE_TICKS = 5;

    /**
     * Arms the commander blast where the commander fell: a rising column of particles pulses while it charges (with a
     * charge-up sound), then the blast hits every unit within {@link #COM_BLAST_RADIUS}. Positions are captured now -
     * the corpse is gone by the time it goes off. The work is a handful of scheduled tasks per commander death, so
     * there is nothing per-tick to pay for at 8v8 scale.
     */
    public static void armComBlast(net.minecraft.server.level.ServerLevel level, LivingEntity commander) {
        final double x = commander.getX(), y = commander.getY(), z = commander.getZ();
        level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
            net.minecraft.sounds.SoundSource.HOSTILE, 4f, 0.5f);
        for (int t = 0; t < COM_BLAST_DELAY_TICKS; t += COM_BLAST_PULSE_TICKS) {
            final int pulse = t / COM_BLAST_PULSE_TICKS;
            com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(t, () -> telegraphPulse(level, x, y, z, pulse));
        }
        com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(COM_BLAST_DELAY_TICKS,
            () -> comBlast(level, x, y, z));
    }

    /** One pulse of the charge-up: the column climbs higher each pulse, and the pitch rises with it. */
    static void telegraphPulse(net.minecraft.server.level.ServerLevel level, double x, double y, double z, int pulse) {
        int height = 3 + pulse * 2;
        for (int h = 0; h < height; h++) {
            double py = y + 0.3 + h * 0.6;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, py, z, 2, 0.12, 0.1, 0.12, 0.02);
            if (h % 2 == 0)
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, x, py, z, 1, 0.2, 0.1, 0.2, 0.01);
        }
        level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
            net.minecraft.sounds.SoundSource.HOSTILE, 3f, 0.5f + pulse * 0.25f);
    }

    /**
     * The blast itself. Every living unit within the radius takes {@link #COM_BLAST_DAMAGE} - its own side's included,
     * as in BAR, so a commander walked into the enemy base is a weapon and one left in your own is a liability.
     * An explosion damage source (not a mob attack, which RoN would rewrite into the attacker's melee damage) with no
     * attacker; there is no level.explode, so no terrain is broken and no extra camera shake is added.
     */
    public static void comBlast(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        final float r = COM_BLAST_RADIUS;
        List<LivingEntity> candidates = com.solegendary.reignofnether.unit.UnitGrid.inBox(level, x - r, z - r, x + r, z + r,
            new java.util.ArrayList<>());
        // collected first: the hurt calls can kill units and change the unit lists under us
        List<LivingEntity> hit = new java.util.ArrayList<>();
        for (LivingEntity le : candidates) {
            if (!le.isAlive() || !(le instanceof Unit) || le.level() != level)
                continue;
            double dx = le.getX() - x, dz = le.getZ() - z;
            // measured flat to the unit's centre, with a height window (hills, fliers low over the blast)
            if (dx * dx + dz * dz > r * r || Math.abs(le.getY() + le.getBbHeight() / 2 - (y + 1)) > r)
                continue;
            hit.add(le);
        }
        var src = level.damageSources().explosion(null, null);
        for (LivingEntity le : hit)
            le.hurt(src, COM_BLAST_DAMAGE);

        // buildings in reach take a moderate dent through RoN's own block-by-block building damage (building blocks
        // only - the ground around is left alone)
        List<com.solegendary.reignofnether.building.BuildingPlacement> dented = new java.util.ArrayList<>();
        for (var b : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings()) {
            if (b.getLevel() != level || !b.isAttackable())
                continue;
            // closest point of the building's footprint to the blast centre
            double cx = Math.max(b.minCorner.getX(), Math.min(x, b.maxCorner.getX() + 1));
            double cz = Math.max(b.minCorner.getZ(), Math.min(z, b.maxCorner.getZ() + 1));
            if ((cx - x) * (cx - x) + (cz - z) * (cz - z) <= r * r
                    && y + r >= b.minCorner.getY() && y - r <= b.maxCorner.getY() + 1)
                dented.add(b);
        }
        for (var b : dented)
            b.destroyRandomBlocks(COM_BLAST_BUILDING_DAMAGE);

        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(x, y, z);
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(level, at, r);
        // a building collapse's worth of fire, rubble and dust with its big white flash, minus the collapse's shake
        com.solegendary.reignofnether.barfx.BarFx.collapse(level, at, r / 2, 6, false);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
            net.minecraft.sounds.SoundSource.HOSTILE, 6f, 0.6f);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        Entity entity = evt.getEntity();
        if (!isCommander(entity) || entity.level().isClientSide())
            return;
        // every commander goes out in the blast (BAR), whether or not losing it also loses its owner the match
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl && entity instanceof LivingEntity le)
            armComBlast(sl, le);
        if (!entity.level().getGameRules().getBoolean(GameRuleRegistrar.COMMANDER_DEFEAT))
            return;
        if (!(entity instanceof Unit unit))
            return;
        String owner = unit.getOwnerName();
        if (owner == null || owner.isEmpty() || !PlayerServerEvents.isRTSPlayer(owner))
            return;
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.commander_fallen", true, owner);
        if (hasLivingAlly(owner)) {
            // team game: the commander blast (armed above) and the player fights on with what they have left
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.commander_fallen_team", false, owner);
            return;
        }
        // 1v1 (or no allies left): losing the commander loses the match
        PlayerServerEvents.defeat(owner, "server.reignofnether.defeat_commander");
    }

    /** Whether another RTS player (human or bot) still in the match is allied with this owner. */
    static boolean hasLivingAlly(String owner) {
        synchronized (PlayerServerEvents.rtsPlayers) {
            for (RTSPlayer other : PlayerServerEvents.rtsPlayers)
                if (!other.name.equals(owner)
                        && com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(owner, other.name))
                    return true;
        }
        return false;
    }
}
