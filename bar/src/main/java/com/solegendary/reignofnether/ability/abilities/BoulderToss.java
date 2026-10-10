package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant Court Elder Treant: the <b>boulder</b> it hurls every {@code ElderTreantUnit.BOULDER_COOLDOWN_TICKS}
 * (design/verdant_court_plan.md, slice 4). A passive, not a button: the treant throws at its target (or the nearest
 * enemy in range) by itself. The boulder flies a {@link #FLIGHT_TICKS}-tick arc drawn with mossy-stone particles - no
 * projectile entity, so a whole grove of treants costs no extra ticking mobs - and where it lands every enemy unit
 * within {@link #RADIUS} blocks takes {@link #DAMAGE}. Friends and allies are never hurt; the landing spot is fixed at
 * the throw, so a quick unit can step out of it. Registered as an event class (the server tick flies the boulders).
 */
public final class BoulderToss {
    private BoulderToss() { }

    public static final int FLIGHT_TICKS = 16;
    public static final float RADIUS = 3.0f;
    public static final float DAMAGE = 18f;
    static final double APEX = 5.0;
    static final int FX_EVERY_TICKS = 2;

    /** One boulder in the air. */
    static class Boulder {
        final ServerLevel level;
        final LivingEntity thrower;
        final String owner;
        final Vec3 from, to;
        int age = 0;

        Boulder(ServerLevel level, LivingEntity thrower, String owner, Vec3 from, Vec3 to) {
            this.level = level;
            this.thrower = thrower;
            this.owner = owner;
            this.from = from;
            this.to = to;
        }
    }

    static final List<Boulder> BOULDERS = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused

    /** Throws a boulder from {@code thrower} at {@code at}; it lands {@link #FLIGHT_TICKS} ticks later. */
    public static void hurl(ServerLevel sl, LivingEntity thrower, String owner, Vec3 at) {
        Vec3 from = thrower.position().add(0, thrower.getBbHeight() * 0.9, 0);
        BOULDERS.add(new Boulder(sl, thrower, owner, from, at));
        thrower.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        sl.playSound(null, thrower.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.playSound(null, thrower.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1.5f, 0.6f);
    }

    /**
     * The landing: every enemy unit of {@code owner} within {@link #RADIUS} of {@code at} takes {@link #DAMAGE}
     * (friends and allies are skipped). Returns the units hit. Public for the game test.
     */
    public static List<LivingEntity> impact(ServerLevel sl, LivingEntity thrower, String owner, Vec3 at) {
        List<LivingEntity> hit = new ArrayList<>();
        double r2 = RADIUS * RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, at.x, at.z, RADIUS + 1.5, scratch)) {
            if (!le.isAlive() || le == thrower || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double dx = le.getX() - at.x, dz = le.getZ() - at.z;
            if (dx * dx + dz * dz > r2 || Math.abs(le.getY() - at.y) > 3)
                continue;
            hit.add(le);
        }
        // hurt after the scan: damage can kill and remove units from the grid list being walked
        for (LivingEntity le : hit)
            // indirect magic with the treant as the source: kill credit and retaliation, but not RoN's melee rewrite
            le.hurt(sl.damageSources().indirectMagic(thrower, thrower), DAMAGE);
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        sl.sendParticles(stone, at.x, at.y + 0.3, at.z, 40, RADIUS * 0.35, 0.2, RADIUS * 0.35, 0.2);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.2f, 0.7f);
        return hit;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || BOULDERS.isEmpty())
            return;
        Iterator<Boulder> it = BOULDERS.iterator();
        while (it.hasNext()) {
            Boulder b = it.next();
            if (b.level.getServer() != evt.getServer())
                continue;
            b.age++;
            if (b.age >= FLIGHT_TICKS) {
                it.remove();
                impact(b.level, b.thrower, b.owner, b.to);
                continue;
            }
            if (b.age % FX_EVERY_TICKS == 0) {
                double t = (double) b.age / FLIGHT_TICKS;
                double x = b.from.x + (b.to.x - b.from.x) * t;
                double z = b.from.z + (b.to.z - b.from.z) * t;
                double y = b.from.y + (b.to.y - b.from.y) * t + 4 * APEX * t * (1 - t);
                BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                b.level.sendParticles(stone, x, y, z, 4, 0.15, 0.15, 0.15, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        BOULDERS.clear();
    }
}
