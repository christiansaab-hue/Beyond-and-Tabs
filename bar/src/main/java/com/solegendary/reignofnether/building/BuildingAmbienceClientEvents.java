package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Ambient life for the base. Any building actively producing something shows it, in its faction's idiom:
 *   - the Kingdom: chimney smoke drifting from the roof
 *   - the Fallen: soul flames guttering at the eaves
 *   - the Gilded Legion: flame and rising embers
 * Smithy-type buildings (blacksmiths, forges, laboratories) also throw sparks while working.
 * Client-side particles only - nothing synced, nothing simulated - and none at all in strategic zoom.
 */
public class BuildingAmbienceClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static final Random random = new Random();
    private static final double MAX_DIST_SQR = 180 * 180;

    /** placement -> {centre, roof height}; recomputed if the building changes shape. */
    private record Shape(int blockCount, BlockPos centre, int height) { }
    private static final Map<BuildingPlacement, Shape> shapes = new WeakHashMap<>();

    private static Shape shapeOf(BuildingPlacement placement) {
        int count = placement.getBlocks().size();
        Shape s = shapes.get(placement);
        if (s != null && s.blockCount() == count)
            return s;
        int max = 0;
        for (BuildingBlock bb : placement.getBlocks())
            max = Math.max(max, bb.getBlockPos().getY() - placement.originPos.getY());
        s = new Shape(count, BuildingUtils.getCentrePos(placement.getBlocks()), max);
        shapes.put(placement, s);
        return s;
    }

    private static boolean isSmithy(BuildingPlacement placement) {
        String n = placement.getBuilding().structureName;
        return n != null && (n.contains("blacksmith") || n.contains("forge") || n.contains("laboratory")
            || n.contains("fortress") || n.contains("bastion"));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.player == null || MC.isPaused())
            return;
        if (MC.level.getGameTime() % 8 != 0)
            return;
        // particles are invisible at strategic zoom; don't pay for them (zoom-out performance)
        if (StrategicViewClientEvents.isStrategicView())
            return;

        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (!placement.isBuilt || !placement.isExploredClientside)
                continue;
            if (!(placement instanceof ProductionPlacement pp) || pp.productionQueue.isEmpty())
                continue;
            Shape shape = shapeOf(placement);
            BlockPos centre = shape.centre();
            if (MC.player.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) > MAX_DIST_SQR)
                continue;

            double x = centre.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            double z = centre.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            double y = placement.originPos.getY() + shape.height() + 0.4;

            switch (FactionTraits.of(placement.getBuilding().getFaction()).ambience) {
                case SOUL_FLAMES -> {
                    // soul flames flicker around the eaves, a little lower than chimney height
                    MC.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y - 0.6, z, 0, 0.02, 0);
                    if (random.nextInt(3) == 0)
                        MC.level.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0.03, 0);
                }
                case FORGE_FIRE -> {
                    MC.level.addParticle(ParticleTypes.FLAME, x, y - 0.4, z, 0, 0.02, 0);
                    if (random.nextInt(2) == 0)
                        MC.level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
                }
                case CHIMNEY_SMOKE -> MC.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                    0, 0.06 + random.nextDouble() * 0.03, 0);
                // Verdant: spores drifting off the living roof, the odd green glint
                case SPORES -> {
                    MC.level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0, 0.01, 0);
                    if (random.nextInt(3) == 0)
                        MC.level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y - 0.3, z, 0, 0.02, 0);
                }
                default -> { }   // no faction entry: no faction-flavoured ambience
            }

            // working smithies throw sparks out of the doorway level
            if (isSmithy(placement) && random.nextInt(2) == 0) {
                ParticleOptions spark = random.nextBoolean() ? ParticleTypes.CRIT : ParticleTypes.SMALL_FLAME;
                double sx = centre.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.5;
                double sz = centre.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.5;
                double sy = placement.originPos.getY() + 1.5;
                for (int i = 0; i < 3; i++)
                    MC.level.addParticle(spark, sx, sy, sz,
                        (random.nextDouble() - 0.5) * 0.2, 0.12 + random.nextDouble() * 0.1,
                        (random.nextDouble() - 0.5) * 0.2);
            }
        }
    }
}
