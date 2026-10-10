package com.solegendary.reignofnether.building.buildings.placements;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Storm Oak's strike (building/buildings/verdant/StormOak.java). Server side only: once built (and while its owner
 * has Tier 2) the oak looks for enemy units within {@link #RANGE} blocks of its trunk every {@link #SCAN_TICKS} ticks
 * and, when it finds any, calls one bolt and waits {@link #COOLDOWN_TICKS} before the next.
 * <p>
 * Target choice "prefers groups": every candidate scores the number of other enemy units within {@link #GROUP_RADIUS}
 * of it, the highest score wins and distance breaks ties - one bolt cannot splash, but aiming at the knot keeps it
 * landing where the push is thickest. Cloaked (invisible) units are never picked, nor friends, allies or ownerless
 * mobs.
 * <p>
 * The bolt is drawn with particles - a jagged line of electric sparks from the lightning rod down to the target, a
 * flash and a puff where it lands - and the damage is indirect magic (armour does not soak it, fire never starts:
 * no LightningBolt entity, so no fire spread, no charged creepers, no pig-to-piglin). Cost at 8v8: one grid query per
 * oak every half second while idle and one O(n^2) score over the few units in range per strike (every 6 s).
 */
public class StormOakPlacement extends BuildingPlacement {
    public static final int RANGE = 22;
    public static final float DAMAGE = 25f;
    public static final int COOLDOWN_TICKS = 120;   // 6 s
    public static final int SCAN_TICKS = 10;        // idle re-scan: twice a second is plenty for a 6 s weapon
    public static final double GROUP_RADIUS = 4.0;
    static final int BOLT_SEGMENTS = 9;

    /** Ticks until the next bolt may fall (0 = ready). Not saved: a reloaded oak is ready at once, like a fresh one. */
    public int cooldown = 0;

    private final List<LivingEntity> scratch = new ArrayList<>();     // grid query results, reused
    private final List<LivingEntity> foes = new ArrayList<>();

    public StormOakPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                             ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (tickLevel instanceof ServerLevel sl)
            tickStrike(sl);
    }

    /** One server tick of the weapon: counts the cooldown down, then strikes when ready. Public for the game test. */
    public void tickStrike(ServerLevel sl) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!isBuilt || isDestroyedServerside || tickAge % SCAN_TICKS != 0)
            return;
        strike(sl, null);
    }

    /**
     * Calls a bolt now if the oak is ready and an enemy is in range: hurts the chosen unit and starts the cooldown.
     * Returns the unit struck, or null (on cooldown, unbuilt, dormant without Tier 2, or nobody to hit).
     * {@code onlyFoe} (null in play) limits targets to one enemy owner: game tests run side by side in one world, so a
     * test passes its own foe's name and never strikes another test's units.
     */
    @Nullable
    public LivingEntity strike(ServerLevel sl, @Nullable String onlyFoe) {
        if (cooldown > 0 || !isBuilt || isDestroyedServerside || ownerName == null || ownerName.isBlank())
            return null;
        LivingEntity target = pickTarget(sl, onlyFoe);
        if (target == null)
            return null;
        // dormant without Tier 2 (the build button is the client gate; this is the server's, as the Circle's)
        if (!ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_2))
            return null;
        cooldown = COOLDOWN_TICKS;
        drawBolt(sl, target);
        // indirect magic with no source entity: a building has none; armour does not soak it and nothing catches fire
        target.hurt(sl.damageSources().indirectMagic(null, null), DAMAGE);
        return target;
    }

    /** The enemy unit in range standing among the most other enemies (nearest on a tie), or null. */
    @Nullable
    public LivingEntity pickTarget(ServerLevel sl, @Nullable String onlyFoe) {
        double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5, cy = centrePos.getY();
        double r2 = (double) RANGE * RANGE;
        foes.clear();
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, RANGE, scratch)) {
            if (!le.isAlive() || le.level() != sl || le.isInvisible() || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isBlank() || ownerName.equals(o) || AlliancesServerEvents.isAllied(ownerName, o)
                    || (onlyFoe != null && !onlyFoe.equals(o)))
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz > r2 || Math.abs(le.getY() - cy) > RANGE)
                continue;
            foes.add(le);
        }
        LivingEntity best = null;
        int bestCount = -1;
        double bestDist = Double.MAX_VALUE, g2 = GROUP_RADIUS * GROUP_RADIUS;
        for (int i = 0, n = foes.size(); i < n; i++) {
            LivingEntity a = foes.get(i);
            int count = 0;
            for (int j = 0; j < n; j++)
                if (j != i && a.distanceToSqr(foes.get(j)) <= g2)
                    count++;
            double dx = a.getX() - cx, dz = a.getZ() - cz, d = dx * dx + dz * dz;
            if (count > bestCount || (count == bestCount && d < bestDist)) {
                best = a;
                bestCount = count;
                bestDist = d;
            }
        }
        foes.clear();
        return best;
    }

    /** The bolt's look: a jagged spark line from the rod above the crown down to the target, a flash and a thunderclap. */
    void drawBolt(ServerLevel sl, LivingEntity target) {
        RandomSource rng = sl.getRandom();
        double x0 = centrePos.getX() + 0.5, y0 = maxCorner.getY() + 1.0, z0 = centrePos.getZ() + 0.5;
        double x1 = target.getX(), y1 = target.getY() + target.getBbHeight() * 0.6, z1 = target.getZ();
        double px = x0, py = y0, pz = z0;
        for (int i = 1; i <= BOLT_SEGMENTS; i++) {
            double t = (double) i / BOLT_SEGMENTS;
            // jitter shrinks toward both ends so the bolt leaves the rod and meets the target cleanly
            double jag = (i == BOLT_SEGMENTS) ? 0 : 1.1 * Math.sin(Math.PI * t);
            double nx = x0 + (x1 - x0) * t + (rng.nextDouble() - 0.5) * jag;
            double ny = y0 + (y1 - y0) * t + (rng.nextDouble() - 0.5) * jag * 0.5;
            double nz = z0 + (z1 - z0) * t + (rng.nextDouble() - 0.5) * jag;
            // fill each segment with sparks about every 0.8 blocks (each is one small packet per nearby player)
            double len = Math.sqrt((nx - px) * (nx - px) + (ny - py) * (ny - py) + (nz - pz) * (nz - pz));
            int steps = Math.max(1, (int) (len / 0.8));
            for (int k = 0; k < steps; k++) {
                double s = (double) k / steps;
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, px + (nx - px) * s, py + (ny - py) * s, pz + (nz - pz) * s,
                    1, 0.02, 0.02, 0.02, 0.0);
            }
            px = nx; py = ny; pz = nz;
        }
        sl.sendParticles(ParticleTypes.FLASH, x1, y1, z1, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD, x1, y1, z1, 8, 0.3, 0.4, 0.3, 0.05);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x0, y0, z0, 6, 0.2, 0.2, 0.2, 0.1);
        sl.playSound(null, BlockPos.containing(x1, y1, z1), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 1.4f, 1.2f);
        sl.playSound(null, BlockPos.containing(x0, y0, z0), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.35f, 1.6f);
    }
}
