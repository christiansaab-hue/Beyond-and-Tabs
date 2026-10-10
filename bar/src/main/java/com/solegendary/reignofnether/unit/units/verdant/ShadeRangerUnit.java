package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court T2 sniper - the <b>Shade Ranger</b> (design/verdant_court_plan.md, slice 4): an elven marksman in a
 * dusk-grey cloak on the Thornbow's archer frame (ShadeRangerRenderer). Long reach and one heavy, slow, flat shot
 * instead of rooting thorns.
 * <p>
 * <b>Cloak:</b> after {@link #CLOAK_DELAY_TICKS} ticks standing still with no attack target it turns invisible (the
 * vanilla invisible flag, synced for free): enemy players don't render it, its health bar, strategic icon and minimap
 * dot (StrategicViewClientEvents / MinimapClientEvents check isInvisible), and enemy units stop picking it as a target
 * (MiscUtil.findClosestAttackableEntity asks {@link #isCloaked}); whoever was already shooting at it loses it when it
 * fades. Its owner still sees it as a ghost ({@link #isInvisibleTo}). Moving, taking a target or firing reveals it at
 * once ({@link #reveal}) - so every shot gives its position away, and it must sit still again to vanish.
 */
public class ShadeRangerUnit extends ThornbowUnit {

    final static public float attackDamage = 16.0f;
    final static public float attacksPerSecond = 0.25f;
    final static public float maxHealth = 30.0f;
    final static public float movementSpeed = 0.25f;
    final static public float attackRange = 24.0F;
    final static public float aggroRange = 24;
    final static public int sightRange = 26;

    public static final int CLOAK_DELAY_TICKS = 3 * 20;
    static final double MOVE_EPSILON_SQR = 0.02 * 0.02;   // per tick: anything less is standing still (idle sway)
    static final float SHOT_VELOCITY = 3.0f;              // a flat, fast shot: the Thornbow's is 1.6

    private int stillTicks = 0;
    private double lastX = Double.NaN, lastZ = Double.NaN;
    private long revealedUntil = 0;   // game time; a Holy Bell reveal keeps the cloak off until then
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public ShadeRangerUnit(EntityType<? extends Skeleton> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SHADE_RANGER; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), sightRange)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Is this entity a cloaked Shade Ranger (enemies must not see or target it)? */
    public static boolean isCloaked(Entity e) {
        return e instanceof ShadeRangerUnit sr && sr.isAlive() && sr.isInvisible();
    }

    @Override
    protected boolean rootsOnHit() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl))
            return;
        double dx = getX() - lastX, dz = getZ() - lastZ;
        boolean moved = Double.isNaN(lastX) || dx * dx + dz * dz > MOVE_EPSILON_SQR;
        lastX = getX();
        lastZ = getZ();
        boolean engaged = getTargetGoal() != null && getTargetGoal().getTarget() != null;
        if (moved || engaged || isPassenger())
            stillTicks = 0;
        else if (stillTicks < CLOAK_DELAY_TICKS)
            stillTicks++;
        // re-asserted every tick: vanilla resets the invisible flag whenever a potion effect changes
        boolean want = (stillTicks >= CLOAK_DELAY_TICKS
                || com.solegendary.reignofnether.blocks.ThicketCover.isHidden(this))   // thicket cover wins too
                && sl.getGameTime() >= revealedUntil;                                   // ...but not the bell
        if (want != isInvisible()) {
            setInvisible(want);
            if (want)
                onCloak(sl);
        }
    }

    /** Drops its cover now (it moved, took a target or fired). Public for the game test. */
    public void reveal() {
        stillTicks = 0;
        com.solegendary.reignofnether.blocks.ThicketCover.reveal(this);   // a shot from a thicket gives it away too
        if (isInvisible()) {
            setInvisible(false);
            if (level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 6, 0.25, 0.4, 0.25, 0.01);
        }
    }

    /** Drops its cover now and keeps it from re-cloaking for {@code ticks} (Holy Bell, via Concealment.revealFor). */
    public void revealFor(int ticks) {
        revealedUntil = Math.max(revealedUntil, level().getGameTime() + ticks);
        reveal();
    }

    /** Just faded: anyone already shooting at it loses the target (one grid query, only on the transition). */
    void onCloak(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 8, 0.25, 0.5, 0.25, 0.01);
        playSound(SoundEvents.AZALEA_LEAVES_STEP, 0.6f, 0.6f);
        String owner = getOwnerName();
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), 40, scratch)) {
            if (!(le instanceof Unit u) || u.getTargetGoal() == null || u.getTargetGoal().getTarget() != this)
                continue;
            String o = u.getOwnerName();
            if (owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            u.getTargetGoal().setTarget(null);
        }
    }

    /**
     * Its owner still sees a ghost of it (LivingEntityRenderer draws an entity that is invisible but not invisible to
     * the viewer translucent); everyone else sees nothing. Read on the client for the local player.
     */
    @Override
    public boolean isInvisibleTo(Player player) {
        if (!isInvisible())
            return false;
        if (player != null && player.getName().getString().equals(getOwnerName()))
            return false;
        return super.isInvisibleTo(player);
    }

    // the Thornbow's shot, flatter and faster to carry 24 blocks, and every shot gives the ranger away
    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        reveal();
        ItemStack itemstack = this.getProjectile(this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this,
                (item) -> item instanceof BowItem
        )));
        AbstractArrow abstractarrow = this.getArrow(itemstack, velocity);
        if (this.getMainHandItem().getItem() instanceof BowItem) {
            abstractarrow = ((BowItem)this.getMainHandItem().getItem()).customArrow(abstractarrow);
        }
        double d0 = pTarget.getX() - this.getX();
        double d1 = pTarget.getY(0.3333333333333333) - abstractarrow.getY();
        double d2 = pTarget.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        if (pTarget.getEyeHeight() <= 1.0f)
            d1 -= (1.0f - pTarget.getEyeHeight());

        // drop compensation for the faster arrow (vanilla's 0.2 per block is tuned for 1.6)
        abstractarrow.shoot(d0, d1 + d3 * 0.08, d2, SHOT_VELOCITY, 0);
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 2.0F, 0.7F);
        this.level().addFreshEntity(abstractarrow);

        if (!level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
        getMainHandItem().setDamageValue(0);
    }
}
