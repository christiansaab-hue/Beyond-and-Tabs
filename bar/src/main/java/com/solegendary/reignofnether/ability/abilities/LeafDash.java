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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Verdant Court Leafblade: <b>Leaf Dash</b> (design/verdant_court_plan.md, slice 1 - the raider's "short dash").
 * Aim a spot or an enemy: the Leafblade darts up to {@link #LENGTH} blocks toward it in {@link #DURATION_TICKS} ticks,
 * trailing leaves, and every enemy it brushes past takes {@link #DAMAGE} once. Dashing at an enemy unit makes it that
 * Leafblade's attack target when the dash ends, so the dash is a gap-closer onto archers and workers. Every selected
 * Leafblade dashes (not one click, one use). {@link #CD_SECONDS} s cooldown.
 *
 * Movement is the Sunrise Sortie's: one {@code Entity.move} step a tick with vanilla block collision, a one-block
 * ledge is hopped, a wall or a drop of more than {@link SunriseSortie#MAX_DROP} blocks ends the dash early. A dash
 * lives a handful of ticks and costs one grid query a tick, so a whole raiding party is cheap at 8v8 scale.
 * Registered as an event class (the server tick drives the dashes).
 */
public class LeafDash extends Ability {

    public static final int CD_SECONDS = 12;
    public static final int LENGTH = 8;
    public static final int DURATION_TICKS = 6;
    public static final float DAMAGE = 4f;
    public static final double HIT_REACH = 1.0;

    /** One dash in flight. Public for the game test. */
    public static class Dash {
        final ServerLevel level;
        final LivingEntity rider;
        final String owner;
        final Vec3 dir;
        final double step;
        final LivingEntity target;
        final Set<Integer> hit = new HashSet<>();
        int age = 0;
        boolean stopped = false;

        Dash(ServerLevel level, LivingEntity rider, String owner, Vec3 dir, double length, LivingEntity target) {
            this.level = level;
            this.rider = rider;
            this.owner = owner;
            this.dir = dir;
            this.step = length / DURATION_TICKS;
            this.target = target;
        }

        public boolean isDone() {
            return stopped || age >= DURATION_TICKS;
        }
    }

    static final List<Dash> DASHES = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused

    public LeafDash() {
        super(UnitAction.LEAF_DASH, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, 0, true, false);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Leaf Dash",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/feather.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.LEAF_DASH,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.LEAF_DASH),
            null,
            List.of(
                FormattedCharSequence.forward("Leaf Dash  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Dart up to " + LENGTH + " blocks: enemies brushed past take "
                    + (int) DAMAGE + ". Dash at an enemy to strike it on arrival.", Style.EMPTY),
                FormattedCharSequence.forward("Gone before the arrows land.", Style.EMPTY.withItalic(true))
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

    /**
     * Starts a dash toward {@code at} (it stops a block short of a target point closer than {@link #LENGTH}); null if
     * the spot is on top of the rider. Public for the game test, which then drives it with {@link #step}.
     */
    public static Dash begin(ServerLevel sl, LivingEntity self, String owner, Vec3 at, LivingEntity target) {
        Vec3 flat = new Vec3(at.x - self.getX(), 0, at.z - self.getZ());
        double dist = flat.length();
        if (dist < 0.5)
            return null;
        Vec3 dir = flat.scale(1 / dist);
        double length = Math.min(LENGTH, Math.max(1.5, dist - 1.0));
        if (self instanceof Unit u)
            Unit.fullResetBehaviours(u);   // the dash is an order: the AI must not drag the rider back mid-dart
        self.setYRot((float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f);
        self.setYHeadRot(self.getYRot());
        Dash d = new Dash(sl, self, owner, dir, length, target);
        DASHES.add(d);
        sl.playSound(null, self.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1.5f, 1.4f);
        sl.playSound(null, self.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8f, 1.6f);
        return d;
    }

    /** One tick of a dash. Public for the game test. */
    public static void step(Dash d) {
        if (d.isDone())
            return;
        LivingEntity le = d.rider;
        ServerLevel sl = d.level;
        if (!le.isAlive() || le.level() != sl) {
            d.stopped = true;
            return;
        }
        double dx = d.dir.x * d.step, dz = d.dir.z * d.step;
        if (!SunriseSortie.canStepTo(sl, le, le.getX() + dx, le.getY(), le.getZ() + dz)) {
            d.stopped = true;
            finish(d);
            return;
        }
        if (le instanceof Mob mob)
            mob.getNavigation().stop();
        double x0 = le.getX(), z0 = le.getZ();
        AABB stepUp = le.getBoundingBox().move(dx, 1.0, dz);
        le.move(MoverType.SELF, new Vec3(dx, le.onGround() ? -0.08 : -0.4, dz));
        double moved2 = (le.getX() - x0) * (le.getX() - x0) + (le.getZ() - z0) * (le.getZ() - z0);
        if (moved2 < d.step * d.step * 0.25) {
            if (sl.noCollision(le, stepUp)) {
                le.setPos(le.getX(), le.getY() + 1.0, le.getZ());
                le.move(MoverType.SELF, new Vec3(dx, 0, dz));
            } else {
                d.stopped = true;
            }
        }
        le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
        brush(d);
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, le.getX(), le.getY() + 0.6, le.getZ(), 2, 0.25, 0.2, 0.25, 0.0);
        d.age++;
        if (d.isDone())
            finish(d);
    }

    static void brush(Dash d) {
        LivingEntity rider = d.rider;
        ServerLevel sl = d.level;
        for (LivingEntity le : UnitGrid.near(sl, rider.getX(), rider.getZ(), HIT_REACH + 1.5, scratch)) {
            if (!le.isAlive() || le == rider || le.level() != sl || !(le instanceof Unit u) || d.hit.contains(le.getId()))
                continue;
            String o = u.getOwnerName();
            if (d.owner.equals(o) || AlliancesServerEvents.isAllied(d.owner, o))
                continue;
            double ddx = le.getX() - rider.getX(), ddz = le.getZ() - rider.getZ();
            double reach = HIT_REACH + le.getBbWidth() / 2;
            if (ddx * ddx + ddz * ddz > reach * reach || Math.abs(le.getY() - rider.getY()) > 2.5)
                continue;
            d.hit.add(le.getId());
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(rider, rider), DAMAGE);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** Arrival: strike the enemy the dash was aimed at. */
    static void finish(Dash d) {
        LivingEntity t = d.target;
        if (t == null || !t.isAlive() || !d.rider.isAlive() || !(d.rider instanceof AttackerUnit au) || !(t instanceof Unit tu))
            return;
        String o = tu.getOwnerName();
        if (d.owner.equals(o) || AlliancesServerEvents.isAllied(d.owner, o))
            return;
        au.setUnitAttackTarget(t);
    }

    /** Takes a dash out of the server tick (the game test drives its own). */
    public static void cancel(Dash d) {
        DASHES.remove(d);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || DASHES.isEmpty())
            return;
        Iterator<Dash> it = DASHES.iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            if (d.level.getServer() != evt.getServer())
                continue;
            step(d);
            if (d.isDone())
                it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        DASHES.clear();
    }
}
