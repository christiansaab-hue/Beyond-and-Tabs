package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sunforged Kingdom (Evoker): <b>Bastion Aegis</b> - BAR's plasma-shield idea, fantasy-first. The caster raises a
 * translucent dome of light, {@link #RADIUS} blocks across, where it stands, for {@link #DURATION_TICKS} ticks. While
 * it holds, projectile damage to friendly units inside is soaked by the dome, up to {@link #CAPACITY} in total; each
 * soaked hit sends a ripple of light from the point of impact. Melee and spells pass through - it answers artillery
 * and archer balls, not a brawl. {@link #CD_SECONDS} s cooldown.
 * <p>
 * The dome is anchored (not carried by the caster) so its edge stays a fixed line on the field that both sides can
 * read. Its shimmer is cheap: a handful of END_ROD particles every few ticks, ~30 per second per dome.
 * Registered as an event class: the {@link LivingHurtEvent} is where the soak happens (it runs after RoN's own
 * projectile damage rewrite, so it sees the real number), and the server tick draws and expires the domes.
 */
public class BastionAegis extends Ability {

    public static final int CD_SECONDS = 50;
    public static final float RADIUS = 6f;
    public static final int DURATION_TICKS = 10 * 20;
    public static final float CAPACITY = 200f;
    static final int FX_EVERY_TICKS = 4;
    static final int FX_PARTICLES = 6;

    /** One live dome. Public for the game test. */
    public static class Dome {
        public final ServerLevel level;
        public final Vec3 centre;
        public final String owner;
        public final long expiresAt;
        public float remaining = CAPACITY;

        Dome(ServerLevel level, Vec3 centre, String owner, long expiresAt) {
            this.level = level;
            this.centre = centre;
            this.owner = owner;
            this.expiresAt = expiresAt;
        }

        boolean covers(LivingEntity le) {
            return le.level() == level && le.distanceToSqr(centre) <= RADIUS * RADIUS;
        }
    }

    // a handful at most at any time (one per Evoker cast, 10 s each); server thread only
    static final List<Dome> DOMES = new ArrayList<>();

    public BastionAegis() {
        // one click, one dome: a box-selected group of Evokers must not burn every cooldown on stacked domes
        super(UnitAction.BASTION_AEGIS, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Bastion Aegis",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/shield.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.BASTION_AEGIS),
            null,
            List.of(
                FormattedCharSequence.forward("Bastion Aegis  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Raises a dome of light (radius " + (int) RADIUS + ") for " + DURATION_TICKS / 20
                    + " s that soaks up to " + (int) CAPACITY + " projectile damage to friendly units inside.", Style.EMPTY),
                FormattedCharSequence.forward("Melee and spells pass through it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        raise(sl, self, unitUsing.getOwnerName(), self.position());
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Raises a dome at the given spot. Public for the game test. */
    public static Dome raise(ServerLevel sl, LivingEntity self, String owner, Vec3 centre) {
        Dome d = new Dome(sl, centre, owner, sl.getGameTime() + DURATION_TICKS);
        DOMES.add(d);
        // a bright ring at the base on cast, so the edge reads before the shimmer fills in
        for (int i = 0; i < 32; i++) {
            double a = i * Mth.TWO_PI / 32;
            sl.sendParticles(ParticleTypes.END_ROD, centre.x + Math.cos(a) * RADIUS, centre.y + 0.2, centre.z + Math.sin(a) * RADIUS,
                1, 0, 0.05, 0, 0.0);
        }
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2.5f, 1.5f);
        return d;
    }

    /** Takes a dome down early (the game test cleans up after itself with this). */
    public static void dispel(Dome d) {
        DOMES.remove(d);
    }

    /**
     * How much of a hit gets through. Soaks projectile damage to a friendly unit inside a live dome (the first dome
     * that covers it), draining that dome. Public for the game test.
     */
    public static float absorb(LivingEntity target, DamageSource src, float amount) {
        if (DOMES.isEmpty() || amount <= 0 || !src.is(DamageTypeTags.IS_PROJECTILE) || !(target instanceof Unit u))
            return amount;
        String o = u.getOwnerName();
        if (o == null || !(target.level() instanceof ServerLevel sl))
            return amount;
        long now = sl.getGameTime();
        for (Dome d : DOMES) {
            if (d.remaining <= 0 || now >= d.expiresAt || !d.covers(target))
                continue;
            if (!d.owner.equals(o) && !AlliancesServerEvents.isAllied(d.owner, o))
                continue;
            float soaked = Math.min(amount, d.remaining);
            d.remaining -= soaked;
            ripple(sl, d, target, src);
            return amount - soaked;
        }
        return amount;
    }

    static void ripple(ServerLevel sl, Dome d, LivingEntity target, DamageSource src) {
        // on the dome's skin, on the side the shot came from (the projectile itself, else the shooter, else above)
        Entity from = src.getDirectEntity() != null ? src.getDirectEntity() : src.getEntity();
        Vec3 dir = from != null ? from.position().subtract(d.centre) : new Vec3(0, 1, 0);
        if (dir.lengthSqr() < 0.01)
            dir = target.position().subtract(d.centre).add(0, 1, 0);
        if (dir.y < 0)
            dir = new Vec3(dir.x, 0, dir.z);   // the dome has no floor: clamp to the ground ring
        Vec3 at = d.centre.add(dir.normalize().scale(RADIUS));
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.04);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.1);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 1.5f, 1.4f);
        if (d.remaining <= 0)
            sl.playSound(null, BlockPos.containing(d.centre), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 2f, 0.8f);
    }

    // after RoN's own hurt handlers so the soak is on the final number
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (DOMES.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        float left = absorb(evt.getEntity(), evt.getSource(), evt.getAmount());
        if (left <= 0)
            evt.setCanceled(true);
        else
            evt.setAmount(left);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || DOMES.isEmpty())
            return;
        Iterator<Dome> it = DOMES.iterator();
        while (it.hasNext()) {
            Dome d = it.next();
            long now = d.level.getGameTime();
            if (now >= d.expiresAt || d.remaining <= 0 || d.level.getServer() != evt.getServer()) {
                if (now >= d.expiresAt && d.remaining > 0)
                    d.level.playSound(null, BlockPos.containing(d.centre), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.5f, 1.5f);
                it.remove();
                continue;
            }
            if (now % FX_EVERY_TICKS != 0)
                continue;
            // random points on the hemisphere: uniform in height gives uniform area on a sphere
            for (int i = 0; i < FX_PARTICLES; i++) {
                double y = d.level.random.nextDouble();
                double a = d.level.random.nextDouble() * Mth.TWO_PI;
                double r = Math.sqrt(1 - y * y);
                d.level.sendParticles(ParticleTypes.END_ROD, d.centre.x + Math.cos(a) * r * RADIUS, d.centre.y + y * RADIUS,
                    d.centre.z + Math.sin(a) * r * RADIUS, 1, 0, 0, 0, 0.0);
            }
        }
    }
}
