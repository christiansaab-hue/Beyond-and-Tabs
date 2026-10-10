package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bombard Crew's mortar shell: a dark ball (drawn as a fire charge by ThrownItemRenderer) on a high ballistic arc
 * that bursts on whatever it reaches first. The burst hurts every enemy unit within {@link #SPLASH_RADIUS} (full damage
 * at the centre, half at the rim), dents enemy buildings it reaches the way an explosion does (destroyRandomBlocks, the
 * same path the Ghast's fireballs use) and never breaks terrain or touches its own side's units or buildings - there is
 * no level.explode, only particles and a sound.
 * <p>
 * {@link #aimAt} solves vanilla's throwable physics exactly (each tick: move, then velocity x0.99 and -0.03 gravity) for
 * a flight time that grows with distance, so the shell lands on the aimed spot without per-tick steering. A fuse
 * ({@link #fuseTicks}) bursts it if it somehow never hits anything. Friendly units are passed through
 * ({@link #canHitEntity}). Cost: one ordinary entity per shell in flight, one grid query on impact.
 */
public class BombardShell extends ThrowableItemProjectile {

    public static final float SPLASH_RADIUS = 2.5f;
    /** Building damage per shell, as a multiple of the unit damage (artillery: buildings are what it is for). */
    public static final float BUILDING_DAMAGE_MULT = 1.5f;

    private float damage = 9f;
    private String ownerName = "";
    private int fuseTicks = 80;

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results (server thread)

    public BombardShell(EntityType<? extends BombardShell> type, Level level) {
        super(type, level);
    }

    public BombardShell(Level level, LivingEntity shooter, float damage) {
        super(EntityRegistrar.BOMBARD_SHELL.get(), shooter, level);
        this.damage = damage;
        if (shooter instanceof Unit u && u.getOwnerName() != null)
            this.ownerName = u.getOwnerName();
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    public float getDamage() { return damage; }
    public String getShellOwnerName() { return ownerName; }

    /**
     * Flight time in ticks for a shot of this flat length: a lob, not a line drive (about a 3-block apex at 11 blocks,
     * 6 at the full 22). Long enough that a quick unit can step out of the splash - the counterplay to artillery.
     */
    static int flightTicks(double flat) {
        return (int) Math.max(20, Math.min(44, 20 + flat));
    }

    /** Sets the velocity that brings the shell from where it is now to (x, y, z) under vanilla throwable physics. */
    public void aimAt(double x, double y, double z) {
        double dx = x - getX(), dy = y - getY(), dz = z - getZ();
        int t = flightTicks(Math.sqrt(dx * dx + dz * dz));
        double drag = 0.99, g = getGravity();
        // sum of drag^k for k < t: how far a unit velocity carries in t ticks
        double s = (1 - Math.pow(drag, t)) / (1 - drag);
        double vy = (dy + g / (1 - drag) * (t - s)) / s;
        setDeltaMovement(dx / s, vy, dz / s);
        fuseTicks = t + 40;
    }

    /** Never collides with its own side's units (the crew fires over its own line). */
    @Override
    protected boolean canHitEntity(@NotNull Entity e) {
        if (!super.canHitEntity(e))
            return false;
        return !(e instanceof Unit u) || isEnemy(u.getOwnerName());
    }

    boolean isEnemy(String other) {
        if (other == null || other.isBlank())
            return true;   // neutral wildlife and unowned units stop a shell too
        return !other.equals(ownerName) && !AlliancesServerEvents.isAllied(ownerName, other);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && isAlive() && tickCount > fuseTicks)
            burst();
    }

    @Override
    protected void onHit(@NotNull HitResult result) {
        if (!level().isClientSide() && isAlive())
            burst();
    }

    /** The burst at the shell's position. Returns how many units it hurt. Public for the game test. */
    public int burst() {
        if (!(level() instanceof ServerLevel sl)) {
            discard();
            return 0;
        }
        double x = getX(), y = getY(), z = getZ();
        Entity shooter = getOwner();
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity le : UnitGrid.near(sl, x, z, SPLASH_RADIUS + 1, scratch)) {
            if (!le.isAlive() || !(le instanceof Unit u) || le.level() != sl || !isEnemy(u.getOwnerName()))
                continue;
            if (Math.abs(le.getY() + le.getBbHeight() / 2 - y) > SPLASH_RADIUS + 1)
                continue;
            double dx = le.getX() - x, dz = le.getZ() - z;
            if (dx * dx + dz * dz <= SPLASH_RADIUS * SPLASH_RADIUS)
                hit.add(le);
        }
        // an explosion source, not a mob attack: RoN rewrites mob-attack damage to the attacker's melee damage
        var src = sl.damageSources().explosion(this, shooter);
        for (LivingEntity le : hit) {
            double d = Math.sqrt((le.getX() - x) * (le.getX() - x) + (le.getZ() - z) * (le.getZ() - z));
            float falloff = (float) (1.0 - 0.5 * Math.min(1.0, d / SPLASH_RADIUS));
            le.hurt(src, damage * falloff);
        }
        // enemy buildings in reach take an explosion's dent (no terrain is touched)
        List<BuildingPlacement> dented = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.getLevel() != sl || !b.isAttackable() || b.ownerName == null || !isEnemy(b.ownerName))
                continue;
            double cx = Math.max(b.minCorner.getX(), Math.min(x, b.maxCorner.getX() + 1));
            double cz = Math.max(b.minCorner.getZ(), Math.min(z, b.maxCorner.getZ() + 1));
            if ((cx - x) * (cx - x) + (cz - z) * (cz - z) <= SPLASH_RADIUS * SPLASH_RADIUS
                    && y + SPLASH_RADIUS >= b.minCorner.getY() && y - SPLASH_RADIUS <= b.maxCorner.getY() + 1)
                dented.add(b);
        }
        for (BuildingPlacement b : dented) {
            if (shooter instanceof Mob mob)
                b.lastAttacker = mob;
            b.destroyRandomBlocks(damage * BUILDING_DAMAGE_MULT);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.3, z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 6, SPLASH_RADIUS * 0.4, 0.2, SPLASH_RADIUS * 0.4, 0.02);
        sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6f, 1.2f);
        discard();
        return hit.size();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BombardDamage", damage);
        tag.putString("BombardOwner", ownerName);
        tag.putInt("BombardFuse", fuseTicks);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("BombardDamage");
        ownerName = tag.getString("BombardOwner");
        fuseTicks = tag.contains("BombardFuse") ? tag.getInt("BombardFuse") : 80;
    }
}
