package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.unit.UnitClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client side of the commander ability: the custom name can arrive after the entity joins, so sweep once a second. */
public class CommanderClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() % 20 != 0)
            return;
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (CommanderServerEvents.looksLikeCommander(le))
                CommanderServerEvents.ensureAbility(le);
    }
}
