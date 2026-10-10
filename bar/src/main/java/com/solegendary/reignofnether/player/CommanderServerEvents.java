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
        boolean hasSignature = false, hasDGun = false, hasRaise = false, hasDrums = false, hasSortie = false;
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
        }
        if (!hasSignature)
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

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        Entity entity = evt.getEntity();
        if (!isCommander(entity) || entity.level().isClientSide())
            return;
        if (!entity.level().getGameRules().getBoolean(GameRuleRegistrar.COMMANDER_DEFEAT))
            return;
        if (!(entity instanceof Unit unit))
            return;
        String owner = unit.getOwnerName();
        if (owner == null || owner.isEmpty() || !PlayerServerEvents.isRTSPlayer(owner))
            return;
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.commander_fallen", true, owner);
        if (hasLivingAlly(owner)) {
            // team game: BAR's commander blast - a big explosion that wrecks whatever stood next to it - and the
            // player fights on with the army and buildings they have left
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel level)
                level.explode(entity, entity.getX(), entity.getY() + 0.5, entity.getZ(), 5.0f,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
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
