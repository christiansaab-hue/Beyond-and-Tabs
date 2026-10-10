package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court anti-swarm - the <b>Hive Keeper</b> (design/verdant_court_plan.md, slice 2): an elven beekeeper in
 * honey and moss (its own texture on the illager body, HiveKeeperRenderer) with a smoker-staff it barely fights with.
 * Its weapon is the hive: whenever an enemy unit comes within {@link #RELEASE_RANGE} blocks it releases
 * {@link #SWARM_SIZE} bees (RoN's BeeUnit, summoned, owned by the keeper's player, population-free and leaving no
 * wreck) that fly at the nearest enemies, sting, poison for {@link #POISON_TICKS} ticks and die after
 * {@link #BEE_LIFETIME_TICKS}. Then {@link #SWARM_COOLDOWN_TICKS} to refill the hive. Many weak stings spread over
 * the closest targets: good against a crowd of cheap units, poor against one armoured giant.
 *
 * Extends VindicatorUnit for the melee brain and attack animation (as the Leafblade does). Cost at 8v8: one grid query
 * a second per keeper (staggered by id), only while the hive is full; at most {@link #SWARM_SIZE} live bees per keeper.
 */
public class HiveKeeperUnit extends VindicatorUnit {

    final static public float attackDamage = 3.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 45.0f;
    final static public float movementSpeed = 0.27f;
    final static public float attackRange = 2;
    final static public float aggroRange = 10;

    public static final int SWARM_SIZE = 3;
    public static final double RELEASE_RANGE = 10;
    public static final int SWARM_COOLDOWN_TICKS = 12 * 20;
    public static final int BEE_LIFETIME_TICKS = 8 * 20;
    public static final int POISON_TICKS = 60;
    static final int SCAN_TICKS = 20;

    /** The live bees of the current swarm (pruned as they die; never more than {@link #SWARM_SIZE}). */
    private final List<BeeUnit> bees = new ArrayList<>(SWARM_SIZE);
    private int cooldown = 0;

    private static final List<LivingEntity> scratch = new ArrayList<>();       // grid query results, reused
    private static final LivingEntity[] nearest = new LivingEntity[SWARM_SIZE];

    public HiveKeeperUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.HIVE_KEEPER; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // an elf, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // a smoking torch for calming the hive instead of the Halberdier's axe; visual only
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CAMPFIRE));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        if (cooldown > 0)
            cooldown--;
        if (!bees.isEmpty() && (tickCount + getId()) % SCAN_TICKS == 0)
            bees.removeIf(b -> b.isRemoved() || !b.isAlive());
        if (cooldown == 0 && bees.isEmpty() && (tickCount + getId()) % SCAN_TICKS == 0 && findNearestEnemies(sl) > 0)
            releaseSwarm(sl);
    }

    /** Fills {@link #nearest} with up to {@link #SWARM_SIZE} enemy units within reach, closest first; returns how many. */
    int findNearestEnemies(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isBlank())
            return 0;
        int n = 0;
        double r2 = RELEASE_RANGE * RELEASE_RANGE;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), RELEASE_RANGE, scratch)) {
            if (!le.isAlive() || le == this || !(le instanceof Unit u) || le instanceof BeeUnit)
                continue;
            double d = le.distanceToSqr(this);
            if (d > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isBlank() || owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            int at = n < SWARM_SIZE ? n++ : SWARM_SIZE;
            while (at > 0 && nearest[at - 1].distanceToSqr(this) > d) {
                if (at < SWARM_SIZE)
                    nearest[at] = nearest[at - 1];
                at--;
            }
            if (at < SWARM_SIZE)
                nearest[at] = le;
        }
        return n;
    }

    /**
     * Opens the hive: {@link #SWARM_SIZE} bees, spread over the nearest enemies found by the last scan (scanning now
     * if needed). Returns the bees released. Public for the game test.
     */
    public List<BeeUnit> releaseSwarm(ServerLevel sl) {
        int targets = findNearestEnemies(sl);
        List<BeeUnit> out = new ArrayList<>(SWARM_SIZE);
        if (targets == 0)
            return out;
        for (int i = 0; i < SWARM_SIZE; i++) {
            BeeUnit bee = EntityRegistrar.BEE_UNIT.get().create(sl);
            if (bee == null)
                continue;
            double a = i * (Math.PI * 2 / SWARM_SIZE);
            bee.moveTo(getX() + Math.cos(a) * 0.6, getY() + 1.6, getZ() + Math.sin(a) * 0.6, getYRot(), 0);
            bee.setOwnerName(getOwnerName());
            bee.setIsSummoned(true);
            bee.lifetimeTicks = BEE_LIFETIME_TICKS;
            bee.stingPoisonTicks = POISON_TICKS;
            sl.addFreshEntity(bee);
            bee.setUnitAttackTarget(nearest[i % targets]);
            bees.add(bee);
            out.add(bee);
        }
        for (int i = 0; i < SWARM_SIZE; i++)
            nearest[i] = null;
        cooldown = SWARM_COOLDOWN_TICKS;
        sl.playSound(null, blockPosition(), SoundEvents.BEEHIVE_EXIT, SoundSource.NEUTRAL, 1.2f, 1.0f);
        sl.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 1.4, getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        return out;
    }
}
