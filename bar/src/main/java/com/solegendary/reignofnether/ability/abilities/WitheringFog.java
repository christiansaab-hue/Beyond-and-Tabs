package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gravebound (Bone Dragon): <b>Withering Fog</b>. Aim at the ground: the dragon breathes a {@link #LENGTH}-block line
 * of grave-fog from beneath it toward the target. The fog lingers for {@link #DURATION_TICKS} ticks; every second it
 * deals {@link #DAMAGE_PER_SECOND} and applies Weakness to each enemy inside. The first tick lands a full second
 * after the breath, so the cloud is its own telegraph. Area denial for a flyer that can't hold ground itself.
 * {@link #CD_SECONDS} s cooldown.
 * <p>
 * Cheap on purpose: a cloud is a line segment, not an entity, scanned once per second; it draws a few SQUID_INK and
 * SOUL particles every {@link #FX_EVERY_TICKS} ticks. Registered as an event class (the server tick runs the clouds).
 */
public class WitheringFog extends Ability {

    public static final int CD_SECONDS = 30;
    public static final int RANGE = 22;
    public static final int LENGTH = 14;
    public static final float HALF_WIDTH = 2f;
    public static final float DAMAGE_PER_SECOND = 3f;
    public static final int DURATION_TICKS = 8 * 20;
    public static final int WEAKNESS_TICKS = 40;
    static final int FX_EVERY_TICKS = 5;

    /** One lingering cloud. Public for the game test. */
    public static class Cloud {
        public final ServerLevel level;
        public final LivingEntity caster;
        public final String owner;
        public final Vec3 from, to;
        final long born, expiresAt;

        Cloud(ServerLevel level, LivingEntity caster, String owner, Vec3 from, Vec3 to, long born) {
            this.level = level;
            this.caster = caster;
            this.owner = owner;
            this.from = from;
            this.to = to;
            this.born = born;
            this.expiresAt = born + DURATION_TICKS;
        }
    }

    // a handful at most (one per dragon every 30 s, 8 s each); server thread only
    static final List<Cloud> CLOUDS = new ArrayList<>();

    public WitheringFog() {
        super(UnitAction.WITHERING_FOG, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Withering Fog",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/ink_sac.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.WITHERING_FOG,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.WITHERING_FOG),
            null,
            List.of(
                FormattedCharSequence.forward("Withering Fog  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Breathes a " + LENGTH + "-block line of grave-fog that lingers "
                    + DURATION_TICKS / 20 + " s: enemies inside take " + (int) DAMAGE_PER_SECOND
                    + " damage per second and are weakened.", Style.EMPTY),
                FormattedCharSequence.forward("Walk out of the fog to escape it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        // the fog settles on the ground under the flying dragon and runs toward the target
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, self.getBlockX(), self.getBlockZ());
        Vec3 from = new Vec3(self.getX(), gy + 0.5, self.getZ());
        Vec3 flat = new Vec3(targetBp.getX() + 0.5 - from.x, 0, targetBp.getZ() + 0.5 - from.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 to = from.add(dir.scale(LENGTH));
        breathe(sl, self, unitUsing.getOwnerName(), from, to);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Lays a cloud along the line and returns it. Public for the game test. */
    public static Cloud breathe(ServerLevel sl, LivingEntity self, String owner, Vec3 from, Vec3 to) {
        Cloud c = new Cloud(sl, self, owner, from, to, sl.getGameTime());
        CLOUDS.add(c);
        // the breath itself: a sheet of soul-fire from the jaws down onto the line
        Vec3 mouth = self.position().add(0, self.getBbHeight() * 0.6, 0);
        for (int i = 0; i <= LENGTH; i += 2) {
            Vec3 at = from.add(to.subtract(from).scale(i / (double) LENGTH));
            Vec3 mid = mouth.add(at.subtract(mouth).scale(0.6));
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 2, 0.6, 0.1, 0.6, 0.01);
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 4f, 0.6f);
        return c;
    }

    /**
     * One second of the cloud: {@link #DAMAGE_PER_SECOND} and Weakness to every hostile unit within
     * {@link #HALF_WIDTH} of the line. Returns how many it touched. Public for the game test.
     */
    public static int pulse(Cloud c) {
        int hit = 0;
        ServerLevel sl = c.level;
        for (LivingEntity le : CommanderDGun.hits(c.caster, c.owner, c.from, c.to, HALF_WIDTH)) {
            // credited to the dragon while it lives (its kills rise as skeletons); a dead dragon's fog still bites
            le.hurt(c.caster.isAlive() ? sl.damageSources().indirectMagic(c.caster, c.caster) : sl.damageSources().magic(),
                DAMAGE_PER_SECOND);
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0));
            hit++;
        }
        return hit;
    }

    /** Clears a cloud early (the game test cleans up after itself with this). */
    public static void dispel(Cloud c) {
        CLOUDS.remove(c);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || CLOUDS.isEmpty())
            return;
        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud c = it.next();
            if (c.level.getServer() != evt.getServer())
                continue;
            long now = c.level.getGameTime();
            if (now >= c.expiresAt) {
                it.remove();
                continue;
            }
            long age = now - c.born;
            if (age > 0 && age % 20 == 0)
                pulse(c);
            if (age % FX_EVERY_TICKS == 0) {
                // sparse on purpose: 4 ink + 2 soul per 5 ticks = ~24 particles/s for a 14-block cloud
                Vec3 seg = c.to.subtract(c.from);
                for (int i = 0; i < 6; i++) {
                    Vec3 at = c.from.add(seg.scale(c.level.random.nextDouble()));
                    c.level.sendParticles(i < 4 ? ParticleTypes.SQUID_INK : ParticleTypes.SOUL,
                        at.x, at.y + c.level.random.nextDouble() * 0.8, at.z, 1, HALF_WIDTH * 0.4, 0.1, HALF_WIDTH * 0.4, 0.0);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        CLOUDS.clear();
    }
}
