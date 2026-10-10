package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.FormationServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.UUID;

/**
 * Sunforged Kingdom (the Lord Marshal commander): <b>Sunrise Sortie</b>. Aim a direction: the Marshal and up to
 * {@link #MAX_FOLLOWERS} of its owner's melee fighters within {@link #GATHER_RADIUS} blocks charge
 * {@link #LENGTH} blocks in a straight line over {@link #DURATION_TICKS} ticks, leaving a trail of flame and
 * sun-motes. Every enemy a charger runs through is flung aside and takes {@link #DAMAGE} (once per charge).
 *
 * Movement goes through {@code Entity.move} one small step a tick, so block collision is the vanilla one - a charger
 * stops at a wall instead of being pushed into it - and a charger whose next step would leave the world border or
 * walk off a drop of more than {@link #MAX_DROP} blocks simply stops there. Synergy with Formation: a charger that
 * ends the sortie shoulder to shoulder (formation rule: {@link FormationServerEvents#MIN_GUARDS}+ fellow chargers
 * within {@link FormationServerEvents#RADIUS}), and every Sunforged shooter already in formation by the Marshal,
 * gets {@link #FORMATION_SPEED_TICKS} ticks of +{@link #FORMATION_SPEED} speed to press the gap.
 * {@link #CD_SECONDS} s cooldown. Registered as an event class (the server tick drives the charges).
 */
public class SunriseSortie extends Ability {

    public static final int CD_SECONDS = 50;
    public static final int LENGTH = 12;
    public static final int DURATION_TICKS = 30;
    public static final int MAX_FOLLOWERS = 6;
    public static final float GATHER_RADIUS = 8f;
    public static final float DAMAGE = 10f;
    public static final double HIT_REACH = 1.2;
    public static final int MAX_DROP = 3;
    public static final double FORMATION_SPEED = 0.25;
    public static final int FORMATION_SPEED_TICKS = 20;
    static final double STEP = LENGTH / (double) DURATION_TICKS;
    static final UUID SPEED_MOD = UUID.fromString("b7d1c0de-2a5e-4c11-9f0a-7a3d0e1f5b01");
    static final String KEY_UNTIL = "bt_sortie_speed_until";

    /** One sortie in flight. Public for the game test. */
    public static class Charge {
        final ServerLevel level;
        final LivingEntity caster;
        final String owner;
        final Vec3 dir, side;
        public final List<LivingEntity> riders = new ArrayList<>();
        final boolean[] stopped;
        final Set<Integer> hit = new HashSet<>();
        int age = 0;

        Charge(ServerLevel level, LivingEntity caster, String owner, Vec3 dir, List<LivingEntity> riders) {
            this.level = level;
            this.caster = caster;
            this.owner = owner;
            this.dir = dir;
            this.side = new Vec3(-dir.z, 0, dir.x);
            this.riders.addAll(riders);
            this.stopped = new boolean[riders.size()];
        }

        public boolean isDone() {
            return age >= DURATION_TICKS;
        }
    }

    static final List<Charge> CHARGES = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused

    public SunriseSortie() {
        super(UnitAction.SUNRISE_SORTIE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Sunrise Sortie",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/golden_horse_armor.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SUNRISE_SORTIE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SUNRISE_SORTIE),
            null,
            List.of(
                FormattedCharSequence.forward("Sunrise Sortie  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("The Lord Marshal and up to " + MAX_FOLLOWERS + " nearby melee units charge "
                    + LENGTH + " blocks: enemies in the way are flung aside and take " + (int) DAMAGE + ".", Style.EMPTY),
                FormattedCharSequence.forward("Chargers ending in formation run +" + (int) (FORMATION_SPEED * 100)
                    + "% faster for " + FORMATION_SPEED_TICKS / 20 + " s.", Style.EMPTY.withItalic(true))
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
        Vec3 flat = new Vec3(targetBp.getX() + 0.5 - self.getX(), 0, targetBp.getZ() + 0.5 - self.getZ());
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1) : flat;
        if (dir.lengthSqr() < 1.0E-4)
            return;
        begin(sl, self, unitUsing.getOwnerName(), dir.normalize());
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static boolean isMeleeFighter(LivingEntity le) {
        return le instanceof AttackerUnit && !(le instanceof RangedAttackerUnit) && !(le instanceof WorkerUnit);
    }

    /**
     * Gathers the riders (the caster plus its owner's nearest melee fighters) and starts the charge along the flat
     * unit vector {@code dir}. Public for the game test, which then drives it with {@link #step}.
     */
    public static Charge begin(ServerLevel sl, LivingEntity self, String owner, Vec3 dir) {
        List<LivingEntity> riders = new ArrayList<>();
        riders.add(self);
        // nearest first: the escort is whoever stands right by the Marshal, not a random pick from the cell
        List<LivingEntity> cand = new ArrayList<>();
        double r2 = GATHER_RADIUS * GATHER_RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), GATHER_RADIUS, scratch)) {
            if (le == self || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || !isMeleeFighter(le)
                    || !owner.equals(u.getOwnerName()) || le.distanceToSqr(self) > r2)
                continue;
            cand.add(le);
        }
        cand.sort((a, b) -> Double.compare(a.distanceToSqr(self), b.distanceToSqr(self)));
        for (int i = 0; i < cand.size() && i < MAX_FOLLOWERS; i++)
            riders.add(cand.get(i));
        for (LivingEntity le : riders) {
            // a sortie is an order: drop whatever each rider was doing so its AI doesn't drag it back mid-charge
            if (le instanceof Unit u)
                Unit.fullResetBehaviours(u);
            le.setYRot((float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f);
            le.setYHeadRot(le.getYRot());
        }
        Charge c = new Charge(sl, self, owner, dir, riders);
        CHARGES.add(c);
        sl.playSound(null, self.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2.5f, 1.3f);
        sl.playSound(null, self.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 2f, 0.8f);
        return c;
    }

    /** One tick of the charge: every rider still running takes a step and tramples what it touches. Public for the game test. */
    public static void step(Charge c) {
        if (c.isDone())
            return;
        ServerLevel sl = c.level;
        double dx = c.dir.x * STEP, dz = c.dir.z * STEP;
        for (int i = 0; i < c.riders.size(); i++) {
            LivingEntity le = c.riders.get(i);
            if (c.stopped[i] || !le.isAlive() || le.level() != sl) {
                c.stopped[i] = true;
                continue;
            }
            if (!canStepTo(sl, le, le.getX() + dx, le.getY(), le.getZ() + dz)) {
                c.stopped[i] = true;   // drop ahead or the world border: hold the ground already taken
                continue;
            }
            if (le instanceof Mob mob)
                mob.getNavigation().stop();
            double x0 = le.getX(), z0 = le.getZ();
            AABB stepUp = le.getBoundingBox().move(dx, 1.0, dz);
            le.move(MoverType.SELF, new Vec3(dx, le.onGround() ? -0.08 : -0.4, dz));
            double moved2 = (le.getX() - x0) * (le.getX() - x0) + (le.getZ() - z0) * (le.getZ() - z0);
            if (moved2 < STEP * STEP * 0.25) {
                // walked into a block: hop a one-block ledge if the space above it is clear, otherwise stop at the wall
                if (sl.noCollision(le, stepUp)) {
                    le.setPos(le.getX(), le.getY() + 1.0, le.getZ());
                    le.move(MoverType.SELF, new Vec3(dx, 0, dz));
                } else {
                    c.stopped[i] = true;
                }
            }
            le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            trample(c, le);
            // one particle call per rider per tick, alternating, so seven riders cost ~7 packets a tick
            if ((c.age + i) % 2 == 0)
                sl.sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + 0.2, le.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
            else
                sl.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + 0.6, le.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
        }
        c.age++;
        if (c.isDone())
            finish(c);
    }

    /** Whether the ground ahead holds (no drop deeper than {@link #MAX_DROP}) and stays inside the world border. */
    static boolean canStepTo(ServerLevel sl, LivingEntity le, double x, double y, double z) {
        if (!sl.getWorldBorder().isWithinBounds(x, z))
            return false;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y + 0.5), Mth.floor(z));
        for (int i = 0; i <= MAX_DROP + 1; i++) {
            if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty())
                return true;
            bp.move(0, -1, 0);
        }
        return false;
    }

    static void trample(Charge c, LivingEntity rider) {
        ServerLevel sl = c.level;
        for (LivingEntity le : UnitGrid.near(sl, rider.getX(), rider.getZ(), HIT_REACH + 1.5, scratch)) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u) || c.hit.contains(le.getId()))
                continue;
            String o = u.getOwnerName();
            if (c.owner.equals(o) || AlliancesServerEvents.isAllied(c.owner, o))
                continue;
            double ddx = le.getX() - rider.getX(), ddz = le.getZ() - rider.getZ();
            double reach = HIT_REACH + le.getBbWidth() / 2;
            if (ddx * ddx + ddz * ddz > reach * reach || Math.abs(le.getY() - rider.getY()) > 2.5)
                continue;
            c.hit.add(le.getId());
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(rider, c.caster.isAlive() ? c.caster : rider), DAMAGE);
            double across = ddx * c.side.x + ddz * c.side.z;
            Vec3 push = c.side.scale(across >= 0 ? 1 : -1).add(c.dir.scale(0.4)).normalize().scale(1.1);
            le.push(push.x, 0.35, push.z);
            le.hurtMarked = true;
            sl.sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /** The sortie's end: the Formation synergy. */
    static void finish(Charge c) {
        ServerLevel sl = c.level;
        List<LivingEntity> sped = new ArrayList<>();
        double fr2 = FormationServerEvents.RADIUS * FormationServerEvents.RADIUS;
        for (LivingEntity le : c.riders) {
            if (!le.isAlive())
                continue;
            int mates = 0;
            for (LivingEntity other : c.riders)
                if (other != le && other.isAlive() && other.distanceToSqr(le) <= fr2)
                    mates++;
            if (mates >= FormationServerEvents.MIN_GUARDS || FormationServerEvents.isInFormation(le))
                sped.add(le);
        }
        // the shooters already in formation by the Marshal follow the breach too
        if (c.caster.isAlive())
            for (LivingEntity le : UnitGrid.near(sl, c.caster.getX(), c.caster.getZ(), GATHER_RADIUS, scratch))
                if (le.isAlive() && le instanceof Unit u && c.owner.equals(u.getOwnerName())
                        && FormationServerEvents.isInFormation(le) && !sped.contains(le))
                    sped.add(le);
        if (sped.isEmpty())
            return;
        long until = sl.getGameTime() + FORMATION_SPEED_TICKS;
        for (LivingEntity le : sped) {
            AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
            // transient: a save mid-buff must not leave a unit permanently fast
            if (speed != null && speed.getModifier(SPEED_MOD) == null)
                speed.addTransientModifier(new AttributeModifier(SPEED_MOD, "sunrise_sortie_speed", FORMATION_SPEED,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
            le.getPersistentData().putLong(KEY_UNTIL, until);
        }
        sl.getServer().tell(new TickTask(sl.getServer().getTickCount() + FORMATION_SPEED_TICKS + 1, () -> {
            long now = sl.getGameTime();
            for (LivingEntity le : sped) {
                if (le.getPersistentData().getLong(KEY_UNTIL) > now)
                    continue;   // a later sortie refreshed it: that one's expiry clears it
                AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null)
                    speed.removeModifier(SPEED_MOD);
                le.getPersistentData().remove(KEY_UNTIL);
            }
        }));
    }

    /** Whether the Formation speed burst is on this unit right now. Public for the game test. */
    public static boolean hasFormationBurst(LivingEntity le) {
        AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(SPEED_MOD) != null;
    }

    /** Takes a charge out of the server tick (the game test drives its own). */
    public static void cancel(Charge c) {
        CHARGES.remove(c);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || CHARGES.isEmpty())
            return;
        Iterator<Charge> it = CHARGES.iterator();
        while (it.hasNext()) {
            Charge c = it.next();
            if (c.level.getServer() != evt.getServer())
                continue;
            step(c);
            if (c.isDone())
                it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        CHARGES.clear();
    }
}
