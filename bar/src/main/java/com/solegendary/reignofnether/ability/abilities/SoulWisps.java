package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gravebound (Embalmer): <b>Soul Wisps</b>. Three soul-wisps circle the Embalmer for {@link #DURATION_TICKS} ticks.
 * While they do, the Embalmer and every Gravebound worker of its owner (or an ally) within {@link #RADIUS} blocks
 * reclaim wrecks {@link #RECLAIM_BONUS}x as fast (read by WreckServerEvents through {@link #reclaimMultiplier}),
 * and once a second the wisps lash the nearest enemy unit within {@link #RADIUS} for {@link #DAMAGE_DAY}
 * ({@link #DAMAGE_NIGHT} at night - the Gravebound own the dark). Particles only, no entities: three wisps per
 * Embalmer must not cost three ticking mobs. {@link #CD_SECONDS} s cooldown, so it can be up two-thirds of the time
 * on a reclaim field. Registered as an event class (the server tick drives the wisps).
 */
public class SoulWisps extends Ability {

    public static final int CD_SECONDS = 45;
    public static final int DURATION_TICKS = 30 * 20;
    public static final float RADIUS = 6f;
    public static final float RECLAIM_BONUS = 1.5f;
    public static final float DAMAGE_DAY = 2f;
    public static final float DAMAGE_NIGHT = 4f;
    static final int FX_EVERY_TICKS = 2;

    /** One Embalmer's wisps. Public for the game test. */
    public static class Wisps {
        final ServerLevel level;
        final LivingEntity caster;
        final String owner;
        final long born, expiresAt;

        Wisps(ServerLevel level, LivingEntity caster, String owner, long born) {
            this.level = level;
            this.caster = caster;
            this.owner = owner;
            this.born = born;
            this.expiresAt = born + DURATION_TICKS;
        }
    }

    static final List<Wisps> ACTIVE = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused

    public SoulWisps() {
        super(UnitAction.SOUL_WISPS, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Soul Wisps",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/phantom_membrane.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.SOUL_WISPS),
            null,
            List.of(
                FormattedCharSequence.forward("Soul Wisps  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("For " + DURATION_TICKS / 20 + " s, Gravebound workers within " + (int) RADIUS
                    + " blocks reclaim " + (int) ((RECLAIM_BONUS - 1) * 100) + "% faster, and the wisps hit one nearby enemy each second for "
                    + (int) DAMAGE_DAY + " (" + (int) DAMAGE_NIGHT + " at night).", Style.EMPTY),
                FormattedCharSequence.forward("The dead do the lifting.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        summon(sl, self, unitUsing.getOwnerName());
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Calls up the wisps around {@code self} and returns them. Public for the game test. */
    public static Wisps summon(ServerLevel sl, LivingEntity self, String owner) {
        // a recast while the old wisps still circle replaces them rather than stacking a second damage tick
        ACTIVE.removeIf(w -> w.caster == self);
        Wisps w = new Wisps(sl, self, owner, sl.getGameTime());
        ACTIVE.add(w);
        sl.playSound(null, self.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2f, 0.8f);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, self.getX(), self.getY() + 1, self.getZ(), 8, 0.6, 0.4, 0.6, 0.02);
        return w;
    }

    /**
     * How much faster this worker reclaims right now: {@link #RECLAIM_BONUS} for a Gravebound worker within
     * {@link #RADIUS} of a friendly Embalmer's wisps (the Embalmer included), else 1. Free while no wisps are up.
     */
    public static float reclaimMultiplier(LivingEntity worker) {
        if (ACTIVE.isEmpty() || !(worker instanceof WorkerUnit) || !(worker instanceof Unit u))
            return 1f;
        Faction f = Factions.getFaction(u);
        if (f == null || !f.equals(Factions.MONSTERS))
            return 1f;
        String owner = u.getOwnerName();
        double r2 = RADIUS * RADIUS;
        for (int i = 0, n = ACTIVE.size(); i < n; i++) {
            Wisps w = ACTIVE.get(i);
            if (!w.caster.isAlive() || w.caster.level() != worker.level() || w.caster.distanceToSqr(worker) > r2)
                continue;
            if (w.owner.equals(owner) || AlliancesServerEvents.isAllied(w.owner, owner))
                return RECLAIM_BONUS;
        }
        return 1f;
    }

    /** One second of the wisps: lashes the nearest enemy unit in range and returns it (null if none). Public for the game test. */
    public static LivingEntity pulse(Wisps w) {
        ServerLevel sl = w.level;
        LivingEntity self = w.caster, target = null;
        double best = RADIUS * RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), RADIUS, scratch)) {
            if (le == self || !le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (w.owner.equals(o) || AlliancesServerEvents.isAllied(w.owner, o))
                continue;
            double d = le.distanceToSqr(self);
            if (d <= best) {
                best = d;
                target = le;
            }
        }
        if (target == null)
            return null;
        // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
        target.hurt(sl.damageSources().indirectMagic(self, self), TimeUtils.isDay(sl) ? DAMAGE_DAY : DAMAGE_NIGHT);
        sl.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
            4, 0.2, 0.3, 0.2, 0.02);
        return target;
    }

    /** Sends the wisps away early (the game test cleans up after itself with this). */
    public static void dispel(Wisps w) {
        ACTIVE.remove(w);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ACTIVE.isEmpty())
            return;
        Iterator<Wisps> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Wisps w = it.next();
            if (w.level.getServer() != evt.getServer())
                continue;
            long now = w.level.getGameTime();
            if (now >= w.expiresAt || !w.caster.isAlive() || w.caster.isRemoved()) {
                it.remove();
                continue;
            }
            long age = now - w.born;
            if (age > 0 && age % 20 == 0)
                pulse(w);
            if (age % FX_EVERY_TICKS == 0) {
                // three wisps on a bobbing orbit: one soul-flame each per 2 ticks, a faint soul trail every other time
                LivingEntity c = w.caster;
                for (int i = 0; i < 3; i++) {
                    float a = age * 0.15f + i * Mth.TWO_PI / 3;
                    double x = c.getX() + Mth.cos(a) * 1.3, z = c.getZ() + Mth.sin(a) * 1.3;
                    double y = c.getY() + 1.4 + Mth.sin(age * 0.2f + i) * 0.25;
                    w.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0);
                    if (age % (FX_EVERY_TICKS * 2) == 0)
                        w.level.sendParticles(ParticleTypes.SOUL, x, y - 0.1, z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ACTIVE.clear();
    }
}
