package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Ambient life for the base: any building actively producing something lets off chimney smoke, so at a glance a
 * working town looks like a working town. Client-side particles only - nothing synced, nothing simulated.
 */
public class BuildingAmbienceClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static final Random random = new Random();
    private static final double MAX_DIST_SQR = 180 * 180;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.player == null || MC.isPaused())
            return;
        if (MC.level.getGameTime() % 8 != 0)
            return;
        // chimney smoke is invisible at strategic zoom; don't pay for the particles (zoom-out performance)
        if (com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.isStrategicView())
            return;

        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (!placement.isBuilt || !placement.isExploredClientside)
                continue;
            if (!(placement instanceof ProductionPlacement pp) || pp.productionQueue.isEmpty())
                continue;
            BlockPos centre = BuildingUtils.getCentrePos(placement.getBlocks());
            if (MC.player.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) > MAX_DIST_SQR)
                continue;
            // one puff near the top of the building, drifting up
            double x = centre.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            double z = centre.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            double y = placement.originPos.getY() + getHeight(placement) + 0.4;
            MC.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                0, 0.06 + random.nextDouble() * 0.03, 0);
        }
    }

    private static int getHeight(BuildingPlacement placement) {
        int max = 0;
        for (BuildingBlock bb : placement.getBlocks())
            max = Math.max(max, bb.getBlockPos().getY() - placement.originPos.getY());
        return max;
    }
}
