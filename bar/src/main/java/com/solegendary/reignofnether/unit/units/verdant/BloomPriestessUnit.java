package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.Bloom;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court T2 support - the <b>Bloom Priestess</b>: an elven priestess in rose and white (her own texture on the
 * illager body, BloomPriestessRenderer), arms folded in prayer. She does not fight. Every {@link #PULSE_TICKS}
 * ticks she heals every friendly unit within {@link #RADIUS} blocks by {@link #PULSE_HEAL} HP (her own and allies',
 * never a foe; herself too - she is the one the enemy dives) and cleanses them of the debuffs in {@link #VANILLA_CLEANSED}:
 * slows and roots, poison, wither, weakness and glowing. Her ability, {@link Bloom}, is a big burst of the same.
 * <p>
 * Where the T1 Moonwell Bearer mends the few most hurt quickly, the Priestess tops up the whole blob slowly and keeps
 * it moving: the answer to Vine-Snare-style roots, Wither Skeletons and poison spiders.
 * <p>
 * A thin copy of the Moonwell Bearer's unit plumbing on the Seedshaper's illager body (Vindicator), not a subclass of
 * either: SeedshaperUnit is a worker and attacker, and the bot finds Moonwell Bearers by class. Cost at 8v8: one grid
 * query every 4 s per priestess, staggered by entity id; healing and cleansing send no packets of their own.
 */
public class BloomPriestessUnit extends Vindicator implements Unit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new Bloom(), Keybindings.abilitySlot1);
    }

    //region
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
    }
    Object2ObjectArrayMap<Ability, Float> cooldowns = Unit.createCooldownMap();
    Object2ObjectArrayMap<Ability, Integer> charges = new Object2ObjectArrayMap<>();
    @Override public Object2ObjectArrayMap<Ability, Float> getAbilityCooldowns() { return cooldowns; }
    @Override public boolean hasAutocast(Ability ability) { return autocast == ability; }
    @Override public void setAutocast(Ability autocast) { this.autocast = autocast; }
    @Override public Object2ObjectArrayMap<Ability, Integer> getAbilityCharges() { return charges; }

    Ability autocast;

    private int eatingTicksLeft = 0;
    public void setEatingTicksLeft(int amount) { eatingTicksLeft = amount; }
    public int getEatingTicksLeft() { return eatingTicksLeft; }
    private BlockPos anchorPos = new BlockPos(0,0,0);
    public void setAnchor(BlockPos bp) { anchorPos = bp; }
    public BlockPos getAnchor() { return anchorPos; }

    private final ArrayList<Checkpoint> checkpoints = new ArrayList<>();
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; };

    GarrisonGoal garrisonGoal;
    public GarrisonGoal getGarrisonGoal() { return garrisonGoal; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

    public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;};
    public MoveToTargetBlockGoal getMoveGoal() {return moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public ReturnResourcesGoal getReturnResourcesGoal() {return null;}
    public int getMaxResources() {return 0;}

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(BloomPriestessUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(BloomPriestessUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(BloomPriestessUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.BLOOM_PRIESTESS;}

    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float maxHealth = 45.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.26f;   // keeps up with the T2 line, not with Stag Lancers
    final static public double magicDamageResist = 0.3d;

    /** Ticks between two mass-heal pulses. */
    public static final int PULSE_TICKS = 4 * 20;
    /** Healing per friend per pulse, and its reach (blocks). */
    public static final float PULSE_HEAL = 3.0f;
    public static final float RADIUS = 7f;

    /**
     * The debuffs a pulse (or Bloom) lifts: vanilla slowness - the Vine Snare's root is Slowness VII - and RoN's minor
     * slow, poison, wither, weakness, mining fatigue (attack-speed debuffs) and glowing. Stuns, fear and infections
     * are left alone: those are other factions' signature counters, not "damage over time" for a healer to undo.
     */
    static final MobEffect[] VANILLA_CLEANSED = {
        MobEffects.MOVEMENT_SLOWDOWN, MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS,
        MobEffects.DIG_SLOWDOWN, MobEffects.GLOWING
    };

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    // pulse scratch: the grid query results (server thread only, one pulse at a time)
    private static final List<LivingEntity> scratch = new ArrayList<>();

    public BloomPriestessUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), magicDamageResist);
    }

    // a living elf, not an illager: healing heals and raid/bad-omen logic leaves her alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override
    public boolean isLeftHanded() { return false; }
    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        if (this.level() instanceof ServerLevel sl && isAlive() && (tickCount + getId()) % PULSE_TICKS == 0)
            pulse(sl);
    }

    /** One mass-heal pulse: {@link #PULSE_HEAL} to every friend in {@link #RADIUS}, plus the cleanse. Public for the game test. */
    public int pulse(ServerLevel sl) {
        int n = mend(sl, RADIUS, PULSE_HEAL, false);
        if (n > 0)
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 1.6, getZ(), 6, 1.2, 0.4, 1.2, 0.0);
        return n;
    }

    /**
     * Heals every friendly unit within {@code r} blocks (this priestess included) by {@code amount} and strips the
     * {@link #VANILLA_CLEANSED} debuffs (and RoN's minor slow) from them. Returns how many friends it healed or cleansed.
     * Shared by the pulse and {@link Bloom}.
     */
    public int mend(ServerLevel sl, double r, float amount, boolean big) {
        String owner = getOwnerName();
        if (owner == null || owner.isBlank())
            return 0;
        MobEffect minorSlow = MobEffectRegistrar.MINOR_MOVEMENT_SLOWDOWN.get();
        int n = 0;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), r, scratch)) {
            if (!le.isAlive() || !(le instanceof Unit u) || le.distanceToSqr(this) > r * r)
                continue;
            String o = u.getOwnerName();
            if (o == null || (!owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o)))
                continue;
            boolean touched = false;
            if (le.getHealth() < le.getMaxHealth()) {
                le.heal(amount);
                touched = true;
            }
            for (MobEffect e : VANILLA_CLEANSED)
                if (le.hasEffect(e)) {
                    le.removeEffect(e);
                    touched = true;
                }
            if (le.hasEffect(minorSlow)) {
                le.removeEffect(minorSlow);
                touched = true;
            }
            if (!touched)
                continue;
            n++;
            sl.sendParticles(big ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.HAPPY_VILLAGER,
                    le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), big ? 6 : 2, 0.3, 0.3, 0.3, 0.01);
        }
        return n;
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            String command = this.getOnDeathCommand();
            if (command != null && !command.isEmpty()) {
                CommandSourceStack source;
                source = serverLevel.getServer()
                    .createCommandSourceStack()
                    .withEntity(this)
                    .withPosition(this.position())
                    .withLevel(serverLevel)
                    .withPermission(2);
                serverLevel.getServer().getCommands().performPrefixedCommand(source, command);
            }
        }
        super.remove(pReason);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        this.addUnitSaveData(pCompound);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.garrisonGoal = new GarrisonGoal(this);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(2, garrisonGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;   // no vindicator axe, no "Johnny"
    }
}
