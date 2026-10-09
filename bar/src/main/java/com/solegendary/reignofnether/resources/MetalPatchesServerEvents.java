package com.solegendary.reignofnether.resources;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Keeps every client's copy of the metal patch map up to date (minimap mex spots survive relogs and reloads). */
public class MetalPatchesServerEvents {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity().level() instanceof ServerLevel serverLevel)
            MetalPatches.syncToClients(serverLevel);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        MetalPatches.forgetAll();
    }
}
