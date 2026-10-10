package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.abilities.Rootquake;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court T3 experimental - the <b>World Tree Walker</b>: a whole elder tree uprooted and walking, on the
 * Sentinel Treant's golem body (as the Sun Colossus is on the Iron Golem's) drawn {@link #SCALE}x with a bark, moss and
 * leaf-canopy skin (WorldTreeWalkerRenderer). It is a support titan rather than a killer:
 * <ul>
 * <li>its <b>shade</b> heals friendly Verdant units within {@link #SHADE_RADIUS} blocks by {@link #SHADE_HEAL} HP a
 * second (never itself, never enemies);</li>
 * <li>its fists <b>slam</b>: a hit also jars enemies round the target for {@link #SLAM_SPLASH} of the damage;</li>
 * <li>it sheds leaves and blossom as it walks (cosmetic for now - see {@link #trail});</li>
 * <li><b>Rootquake</b> ({@link Rootquake}): a telegraphed ring that roots and hurts every enemy within 8 blocks.</li>
 * </ul>
 * T3 rules (design-factions.md): ~5x the Elder Treant's cost; clear weakness - <b>fire does double damage</b>, it is
 * the slowest unit on the field and its direct damage is weak for its price, so focused Blazes or fire arrows, or
 * an equal-cost T2 army that kites it, beat it; Rootquake is telegraphed, no instant win. Arrival announced to all.
 */
public class WorldTreeWalkerUnit extends SentinelTreantUnit {

    final static public float attackDamage = 12.0f;
    final static public float attacksPerSecond = 0.3f;
    final static public float maxHealth = 1400.0f;
    final static public float movementSpeed = 0.13f;
    final static public float attackRange = 4;
    final static public float aggroRange = 12;
    final static public float rangedDamageResist = 0.3f;
    public static final float SCALE = 2.5f;

    public static final float SHADE_RADIUS = 6f;
    public static final float SHADE_HEAL = 2f;          // per second, per unit in the shade
    public static final float FIRE_MULTIPLIER = 2f;     // the weakness: a burning tree
    public static final float SLAM_RADIUS = 2.5f;
    public static final float SLAM_SPLASH = 0.5f;
    static final int TRAIL_EVERY_TICKS = 10;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)
    private double trailX, trailZ;

    public WorldTreeWalkerUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.WORLD_TREE_WALKER; }

    /** IronGolemUnit rebuilds its abilities from the shared static set here; add Rootquake on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a instanceof Rootquake)
                return;
        getAbilities().add(new Rootquake());
    }

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
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 1.0);
    }

    /** The weakness: every kind of fire (burning, fire blocks, lava, fireballs) hits it twice as hard. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE))
            amount *= FIRE_MULTIPLIER;
        return super.hurt(source, amount);
    }

    /** The slam: a landed blow also jars the enemies standing round the target. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            String owner = getOwnerName();
            float splash = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * SLAM_SPLASH;
            if (owner != null && !owner.isEmpty()) {
                double r2 = SLAM_RADIUS * SLAM_RADIUS;
                for (LivingEntity le : UnitGrid.near(sl, target.getX(), target.getZ(), SLAM_RADIUS, new ArrayList<>())) {
                    if (le == target || le == this || !le.isAlive() || !isFoe(owner, le) || le.distanceToSqr(target) > r2)
                        continue;
                    le.hurt(sl.damageSources().mobAttack(this), splash);
                }
            }
            BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
            sl.sendParticles(soil, target.getX(), target.getY() + 0.1, target.getZ(), 16, 0.9, 0.1, 0.9, 0.1);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.world_tree_walker_unit"));
        }
        // stagger the once-a-second shade by entity id so a handful of walkers don't all query on one tick
        if ((tickCount + getId()) % 20 == 0)
            shade(sl);
        if (tickCount % TRAIL_EVERY_TICKS == 0)
            trail(sl);
    }

    /**
     * Heals every hurt friendly Verdant unit within {@link #SHADE_RADIUS} by {@link #SHADE_HEAL}; returns how many it
     * healed. Called once a second. Public for the game test.
     */
    public int shade(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        double r2 = SHADE_RADIUS * SHADE_RADIUS;
        int n = 0;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), SHADE_RADIUS, scratch)) {
            if (le == this || !le.isAlive() || le.getHealth() >= le.getMaxHealth() || !(le instanceof Unit u))
                continue;
            if (le.distanceToSqr(this) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || (!owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o)))
                continue;
            if (!Factions.VERDANT_COURT.equals(Factions.getFaction(u)))
                continue;   // the shade is the Court's own: an allied Kingdom's soldier gets nothing from it
            le.heal(SHADE_HEAL);
            n++;
            if ((tickCount & 1) == 0)   // a leaf now and then on the healed, not a particle storm
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), 1, 0.25, 0.3, 0.25, 0.0);
        }
        return n;
    }

    /**
     * The foliage it sheds as it walks: azalea leaves and blossom at its feet, only while it is actually moving.
     * TODO(verdant living terrain): when the Thicket block lands, plant a short-lived Thicket patch here instead of
     * particles alone (rate-limited, on the walker's own footprint, never inside a building).
     */
    void trail(ServerLevel sl) {
        double dx = getX() - trailX, dz = getZ() - trailZ;
        if (dx * dx + dz * dz < 0.25)
            return;
        trailX = getX();
        trailZ = getZ();
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        double spread = getBbWidth() * 0.4;
        sl.sendParticles(leaves, getX(), getY() + 0.2, getZ(), 6, spread, 0.1, spread, 0.05);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + getBbHeight() * 0.85, getZ(), 2, spread, 0.3, spread, 0.0);
    }

    static boolean isFoe(String owner, LivingEntity le) {
        if (!(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }
}
