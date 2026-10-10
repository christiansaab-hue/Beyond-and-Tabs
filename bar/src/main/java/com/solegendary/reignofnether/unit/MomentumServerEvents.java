package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ironhide Horde signature (design-factions.md): <b>Momentum</b>. A Horde fighter moving in a straight line builds
 * momentum (0 to 1 over ~2.5 s of a steady charge); at full momentum it hits for +50% damage with extra knockback.
 * Turning sharply or stopping bleeds it off, and landing a blow spends it - so the Horde wants to charge in, not
 * stand and trade, and kiting them works. Workers don't build momentum.
 *
 * Cost: sampled every {@link #SAMPLE_TICKS} ticks (not per tick), one map entry per Horde fighter, and the
 * attribute modifier is only rewritten when the bonus changes by a visible step.
 */
public class MomentumServerEvents {

    public static final int SAMPLE_TICKS = 10;
    public static final float GAIN = 0.2f;            // per sample of steady movement: 5 samples = 2.5 s to full
    public static final float TURN_LOSS = 0.35f;
    public static final double MIN_STEP = 0.6;        // blocks per sample (~1.2 blocks/s) counts as moving
    public static final double STRAIGHT_DOT = 0.85;   // cos ~32 degrees
    public static final double MAX_DAMAGE_BONUS = 0.5;
    public static final double MAX_KNOCKBACK = 1.0;

    static final UUID DMG_MOD = UUID.fromString("b7e4c1a2-3d5f-4e6a-9b8c-1d2e3f4a5b01");
    static final UUID KB_MOD = UUID.fromString("b7e4c1a2-3d5f-4e6a-9b8c-1d2e3f4a5b02");

    static class State {
        Vec3 last;
        Vec3 dir = Vec3.ZERO;
        float momentum = 0;
        float applied = -1;
    }

    private static final Map<Integer, State> states = new HashMap<>();

    public static float getMomentum(LivingEntity le) {
        State s = states.get(le.getId());
        return s == null ? 0 : s.momentum;
    }

    static boolean isHordeFighter(LivingEntity le) {
        if (!(le instanceof Unit u) || le instanceof WorkerUnit)
            return false;
        return FactionTraits.of(Factions.getFaction(u)).momentum;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % SAMPLE_TICKS != 0)
            return;
        sampleAll(level);
    }

    /** One momentum sample for every Horde fighter. Public for the game test. */
    public static void sampleAll(ServerLevel level) {
        states.keySet().removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le.level() != level || !le.isAlive() || !isHordeFighter(le))
                continue;
            sample(level, le);
        }
    }

    static void sample(ServerLevel level, LivingEntity le) {
        State s = states.computeIfAbsent(le.getId(), k -> new State());
        Vec3 now = le.position();
        if (s.last != null) {
            Vec3 step = new Vec3(now.x - s.last.x, 0, now.z - s.last.z);
            double len = step.length();
            if (len < MIN_STEP) {
                s.momentum = 0;
                s.dir = Vec3.ZERO;
            } else {
                Vec3 d = step.scale(1 / len);
                if (s.dir == Vec3.ZERO || d.dot(s.dir) >= STRAIGHT_DOT)
                    s.momentum = Math.min(1f, s.momentum + GAIN);
                else
                    s.momentum = Math.max(0f, s.momentum - TURN_LOSS);
                s.dir = d;
            }
        }
        s.last = now;
        apply(le, s);
        if (s.momentum >= 1f)   // a charge you can see from the RTS camera: dust kicked up at the feet
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, now.x, now.y + 0.1, now.z, 1, 0.2, 0, 0.2, 0.005);
    }

    static void apply(LivingEntity le, State s) {
        // rewrite modifiers only on visible steps (every 0.2 of momentum) to keep attribute churn low
        float stepped = Math.round(s.momentum * 5) / 5f;
        if (stepped == s.applied)
            return;
        s.applied = stepped;
        // unit damage is computed from RoN's own attack attribute (LivingEntityMixin.actuallyHurt), not vanilla's
        setModifier(le.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get()), DMG_MOD, "horde_momentum_dmg",
            MAX_DAMAGE_BONUS * stepped, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setModifier(le.getAttribute(Attributes.ATTACK_KNOCKBACK), KB_MOD, "horde_momentum_kb",
            MAX_KNOCKBACK * stepped, AttributeModifier.Operation.ADDITION);
    }

    static void setModifier(AttributeInstance attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        if (attr == null)
            return;
        if (attr.getModifier(id) != null)
            attr.removeModifier(id);
        if (amount > 0)
            attr.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /**
     * Landing a blow spends the charge. LivingDamageEvent, not LivingHurtEvent: RoN computes unit damage itself
     * and skips the hurt event, but still fires this one after the damage (with the bonus) was worked out.
     */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent evt) {
        if (!(evt.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide())
            return;
        State s = states.get(attacker.getId());
        if (s == null || s.momentum <= 0)
            return;
        s.momentum = 0;
        s.dir = Vec3.ZERO;
        apply(attacker, s);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        states.clear();
    }
}
