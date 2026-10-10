package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant Court Stag Lancer: <b>Leaping Charge</b> (design/verdant_court_plan.md, slice 4 - "Stag Lancer, leap").
 * Aim a spot or an enemy up to {@link #LENGTH} blocks away: the stag bounds there in a {@link #DURATION_TICKS}-tick arc
 * (clearing low walls and the front rank), and where it lands every enemy within {@link #RADIUS} blocks takes
 * {@link #DAMAGE} and is thrown back. Leaping at an enemy unit makes it the Lancer's attack target on landing, so it is
 * a flank-opener onto artillery and archers. Every selected Lancer leaps. {@link #CD_SECONDS} s cooldown.
 * <p>
 * Movement is scripted like Leaf Dash (one {@code Entity.move} step a tick with vanilla block collision, a parabolic
 * lift on top), so it can't tunnel through a wall: hitting one ends the leap early and it lands there. One grid query
 * per leap, at the landing. Registered as an event class (the server tick drives the leaps).
 */
public class StagLeap extends Ability {

    public static final int CD_SECONDS = 14;
    public static final int LENGTH = 10;
    public static final int DURATION_TICKS = 10;
    public static final double APEX = 2.5;
    public static final float DAMAGE = 10f;
    public static final float RADIUS = 2.5f;

    /** One leap in flight. Public for the game test. */
    public static class Leap {
        final ServerLevel level;
        final LivingEntity rider;
        final String owner;
        final Vec3 dir;
        final double step;
        final LivingEntity target;
        int age = 0;
        boolean stopped = false;
        boolean landed = false;

        Leap(ServerLevel level, LivingEntity rider, String owner, Vec3 dir, double length, LivingEntity target) {
            this.level = level;
            this.rider = rider;
            this.owner = owner;
            this.dir = dir;
            this.step = length / DURATION_TICKS;
            this.target = target;
        }

        public boolean isDone() {
            return landed;
        }
    }

    static final List<Leap> LEAPS = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused

    public StagLeap() {
        super(UnitAction.STAG_LEAP, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, RADIUS, true, false);
        this.showRangeLine = true;
        this.showRadiusCircle = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Leaping Charge",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/rabbit_foot.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.STAG_LEAP,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.STAG_LEAP),
            null,
            List.of(
                FormattedCharSequence.forward("Leaping Charge  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Bound up to " + LENGTH + " blocks over the front line. Enemies within "
                    + RADIUS + " blocks of the landing take " + (int) DAMAGE + " and are thrown back.", Style.EMPTY),
                FormattedCharSequence.forward("Antlers first.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity == null)
            return;
        start(level, unitUsing, targetEntity.position(), targetEntity);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        start(level, unitUsing, Vec3.atBottomCenterOf(targetBp), null);
    }

    void start(Level level, Unit unitUsing, Vec3 at, LivingEntity target) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (begin(sl, self, unitUsing.getOwnerName(), at, target) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Starts a leap toward {@code at}; null if the spot is on top of the rider. Public for the game test. */
    public static Leap begin(ServerLevel sl, LivingEntity self, String owner, Vec3 at, LivingEntity target) {
        Vec3 flat = new Vec3(at.x - self.getX(), 0, at.z - self.getZ());
        double dist = flat.length();
        if (dist < 0.5)
            return null;
        Vec3 dir = flat.scale(1 / dist);
        // land just short of a target unit (on top of it would stack the two bodies), on the spot otherwise
        double length = Math.min(LENGTH, Math.max(1.5, target != null ? dist - 1.2 : dist));
        if (self instanceof Unit u)
            Unit.fullResetBehaviours(u);   // the leap is an order: the AI must not drag the stag back mid-air
        self.setYRot((float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f);
        self.setYHeadRot(self.getYRot());
        Leap l = new Leap(sl, self, owner, dir, length, target);
        LEAPS.add(l);
        sl.playSound(null, self.blockPosition(), SoundEvents.GOAT_LONG_JUMP, SoundSource.NEUTRAL, 2f, 0.8f);
        return l;
    }

    /** One tick of a leap. Public for the game test. */
    public static void step(Leap l) {
        if (l.landed)
            return;
        LivingEntity le = l.rider;
        ServerLevel sl = l.level;
        if (!le.isAlive() || le.level() != sl) {
            l.landed = true;
            return;
        }
        if (!l.stopped && l.age < DURATION_TICKS) {
            if (le instanceof Mob mob)
                mob.getNavigation().stop();
            // a parabola over DURATION_TICKS: up while t < 0.5, down after; each tick moves by its slope
            double t0 = (double) l.age / DURATION_TICKS, t1 = (double) (l.age + 1) / DURATION_TICKS;
            double dy = 4 * APEX * (t1 * (1 - t1) - t0 * (1 - t0));
            double dx = l.dir.x * l.step, dz = l.dir.z * l.step;
            double x0 = le.getX(), z0 = le.getZ();
            le.move(MoverType.SELF, new Vec3(dx, dy, dz));
            double moved2 = (le.getX() - x0) * (le.getX() - x0) + (le.getZ() - z0) * (le.getZ() - z0);
            if (moved2 < l.step * l.step * 0.25)
                l.stopped = true;   // a wall: drop where it hit
            le.setDeltaMovement(0, 0, 0);
            le.fallDistance = 0;
            sl.sendParticles(ParticleTypes.CHERRY_LEAVES, le.getX(), le.getY() + 0.8, le.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
            l.age++;
            return;
        }
        // falling the rest of the way (a short drop, or a leap cut short by a wall): land once grounded
        le.fallDistance = 0;
        if (le.onGround() || l.age > DURATION_TICKS + 20) {
            land(l);
            l.landed = true;
        } else {
            le.move(MoverType.SELF, new Vec3(0, -0.5, 0));
            l.age++;
        }
    }

    /** The landing: every enemy unit within RADIUS takes DAMAGE and is knocked away. Returns how many were hit. */
    public static int land(Leap l) {
        LivingEntity rider = l.rider;
        ServerLevel sl = l.level;
        int hits = 0;
        for (LivingEntity le : UnitGrid.near(sl, rider.getX(), rider.getZ(), RADIUS + 1.5, scratch)) {
            if (!le.isAlive() || le == rider || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (l.owner.equals(o) || AlliancesServerEvents.isAllied(l.owner, o))
                continue;
            double ddx = le.getX() - rider.getX(), ddz = le.getZ() - rider.getZ();
            double reach = RADIUS + le.getBbWidth() / 2;
            if (ddx * ddx + ddz * ddz > reach * reach || Math.abs(le.getY() - rider.getY()) > 3)
                continue;
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            if (le.hurt(sl.damageSources().indirectMagic(rider, rider), DAMAGE))
                hits++;
            le.knockback(0.8, -ddx, -ddz);
        }
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
        sl.sendParticles(dirt, rider.getX(), rider.getY() + 0.1, rider.getZ(), 24, RADIUS * 0.4, 0.1, RADIUS * 0.4, 0.15);
        sl.playSound(null, rider.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 2f, 0.8f);
        LivingEntity t = l.target;
        if (t != null && t.isAlive() && rider.isAlive() && rider instanceof AttackerUnit au && t instanceof Unit tu) {
            String o = tu.getOwnerName();
            if (!l.owner.equals(o) && !AlliancesServerEvents.isAllied(l.owner, o))
                au.setUnitAttackTarget(t);
        }
        return hits;
    }

    /** Takes a leap out of the server tick (the game test drives its own). */
    public static void cancel(Leap l) {
        LEAPS.remove(l);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || LEAPS.isEmpty())
            return;
        Iterator<Leap> it = LEAPS.iterator();
        while (it.hasNext()) {
            Leap l = it.next();
            if (l.level.getServer() != evt.getServer())
                continue;
            step(l);
            if (l.isDone())
                it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        LEAPS.clear();
    }
}
