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
 * after a reload. With the commanderDefeat gamerule on (default), losing your commander loses you the match -
 * BAR's win condition - on top of Reign of Nether's capitol rule.
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
        PlayerServerEvents.defeat(owner, "server.reignofnether.defeat_commander");
    }
}
