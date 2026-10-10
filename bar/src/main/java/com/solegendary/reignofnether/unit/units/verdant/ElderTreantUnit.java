package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.abilities.BoulderToss;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court T2 super-tank - the <b>Elder Treant</b> (design/verdant_court_plan.md, slice 4): an ancient oak on the
 * Sentinel Treant's golem body, drawn larger and darker (ElderTreantRenderer). Very tough and very slow; besides its
 * fists it hurls a mossy boulder every {@link #BOULDER_COOLDOWN_TICKS} ticks at its target or the nearest enemy within
 * {@link #BOULDER_RANGE} blocks ({@link BoulderToss}: an area hit that spares friends). Weakness: slow enough to kite,
 * and the boulder's landing spot is fixed at the throw, so quick units step out of it.
 */
public class ElderTreantUnit extends SentinelTreantUnit {

    final static public float attackDamage = 14.0f;
    final static public float attacksPerSecond = 0.35f;
    final static public float maxHealth = 520.0f;
    final static public float movementSpeed = 0.16f;
    final static public float attackRange = 3;
    final static public float aggroRange = 14;
    final static public float rangedDamageResist = 0.5f;
    public static final float SCALE = 1.45f;

    public static final int BOULDER_COOLDOWN_TICKS = 8 * 20;
    public static final float BOULDER_RANGE = 14f;
    public static final float BOULDER_MIN_RANGE = 2.5f;   // closer than this the fists do the work
    static final int SCAN_EVERY_TICKS = 10;

    private int boulderCooldown = BOULDER_COOLDOWN_TICKS / 2;   // the first one comes quicker after it walks out
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public ElderTreantUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.ELDER_TREANT; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 0.5);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        if (boulderCooldown > 0) {
            boulderCooldown--;
            return;
        }
        // ready: look for a mark a couple of times a second, not every tick (one grid query per look)
        if (tickCount % SCAN_EVERY_TICKS != 0)
            return;
        LivingEntity mark = boulderTarget(sl);
        if (mark == null)
            return;
        BoulderToss.hurl(sl, this, getOwnerName(), mark.position());
        boulderCooldown = BOULDER_COOLDOWN_TICKS;
    }

    /**
     * Its current attack target if in boulder range, else the nearest visible enemy unit in range; null if none.
     * Public for the game test.
     */
    @Nullable
    public LivingEntity boulderTarget(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return null;
        double max2 = BOULDER_RANGE * BOULDER_RANGE, min2 = BOULDER_MIN_RANGE * BOULDER_MIN_RANGE;
        LivingEntity t = getTargetGoal() != null ? getTargetGoal().getTarget() : null;
        if (t != null && t.isAlive() && isEnemy(owner, t)) {
            double d = t.distanceToSqr(this);
            if (d <= max2 && d >= min2)
                return t;
        }
        LivingEntity best = null;
        double bestD = max2;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), BOULDER_RANGE, scratch)) {
            if (le == this || !le.isAlive() || !isEnemy(owner, le) || ShadeRangerUnit.isCloaked(le))
                continue;
            double d = le.distanceToSqr(this);
            if (d < bestD && d >= min2) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    static boolean isEnemy(String owner, LivingEntity le) {
        if (!(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }
}
