package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.time.TimeUtils;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court support - the <b>Moonwell Bearer</b> (design/verdant_court_plan.md, slice 2): a druid carrying a
 * lantern of moonwell water on the witch's body (vanilla WitchRenderer; the lantern sits in its crossed arms). It does
 * not fight: once a second it mends the {@link #MAX_TARGETS} most hurt friendly units within {@link #RADIUS} blocks
 * (its own and allies', never a foe, never itself), and at night - the moon is its well - it heals harder and reaches
 * further ({@link #NIGHT_HEAL}, {@link #NIGHT_RADIUS}).
 *
 * A thin copy of WitchUnit rather than a subclass: WitchUnit is found by instanceof for its potion abilities and HUD
 * (ThrownPotionMixin, UnitClientEvents, ThrowLingeringRegenPotion), none of which the Bearer has.
 *
 * Cost at 8v8: one pulse a second per Bearer, staggered by entity id so a group never pulses on the same tick, each a
 * single UnitGrid query into a shared scratch list; healing sends no packets of its own (health is synced by vanilla).
 */
public class MoonwellBearerUnit extends Witch implements Unit {
    public static final Abilities ABILITIES = new Abilities();

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
            SynchedEntityData.defineId(MoonwellBearerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(MoonwellBearerUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(MoonwellBearerUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.MOONWELL_BEARER;}

    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float maxHealth = 36.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.27f;   // keeps up with Thornbows, not with Leafblades
    final static public double magicDamageResist = 0.3d;

    /** Ticks between two healing pulses. */
    public static final int PULSE_TICKS = 20;
    /** Healing per target per pulse, and its reach (blocks), by day and by night. */
    public static final float DAY_HEAL = 2.0f, NIGHT_HEAL = 3.5f;
    public static final double RADIUS = 6, NIGHT_RADIUS = 8;
    /** At most this many friends are mended per pulse: the most hurt (lowest health fraction) first. */
    public static final int MAX_TARGETS = 3;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    // pulse scratch: the grid query results and the chosen targets (server thread only, one pulse at a time)
    private static final List<LivingEntity> scratch = new ArrayList<>();
    private static final LivingEntity[] chosen = new LivingEntity[MAX_TARGETS];

    public MoonwellBearerUnit(EntityType<? extends Witch> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
        this.setDropChance(EquipmentSlot.MAINHAND, 0);   // the lantern is scenery, not loot
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

    // a druid, not a raider: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        if (this.level() instanceof ServerLevel sl && isAlive() && (tickCount + getId()) % PULSE_TICKS == 0)
            pulse(sl);
    }

    /** True while the Bearer's moon is up (TimeUtils' strict day, the same clock as the Gravebound's night). */
    public boolean isNightPower() {
        return !TimeUtils.isDay(level());
    }

    /**
     * One healing pulse: picks up to {@link #MAX_TARGETS} hurt friendly units in reach (lowest health fraction first)
     * and heals each. Returns how many it healed. Public for the game test.
     */
    public int pulse(ServerLevel sl) {
        boolean night = isNightPower();
        double r = night ? NIGHT_RADIUS : RADIUS;
        float amount = night ? NIGHT_HEAL : DAY_HEAL;
        String owner = getOwnerName();
        if (owner == null || owner.isBlank())
            return 0;
        int n = 0;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), r, scratch)) {
            if (le == this || !le.isAlive() || le.getHealth() >= le.getMaxHealth() || !(le instanceof Unit u))
                continue;
            if (le.distanceToSqr(this) > r * r)
                continue;
            String o = u.getOwnerName();
            if (o == null || (!owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o)))
                continue;
            // insertion into the small "most hurt" array (keeps MAX_TARGETS, no sorting or allocation)
            float frac = le.getHealth() / le.getMaxHealth();
            int at = n < MAX_TARGETS ? n++ : MAX_TARGETS;
            while (at > 0 && chosen[at - 1].getHealth() / chosen[at - 1].getMaxHealth() > frac) {
                if (at < MAX_TARGETS)
                    chosen[at] = chosen[at - 1];
                at--;
            }
            if (at < MAX_TARGETS)
                chosen[at] = le;
        }
        for (int i = 0; i < n; i++) {
            LivingEntity le = chosen[i];
            le.heal(amount);
            sl.sendParticles(night ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
                    le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), night ? 2 : 3, 0.25, 0.3, 0.25, 0.01);
            chosen[i] = null;
        }
        if (n > 0)
            sl.sendParticles(ParticleTypes.GLOW, getX(), getY() + 1.2, getZ(), 2, 0.2, 0.2, 0.2, 0.0);
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
    public void aiStep() {
        super.aiStep();

        // vanilla witches swap a potion into their hand to drink it; the Bearer only ever holds its moonwell lantern
        // (visual: the healing above is its whole job). Same clean-up as WitchUnit, with the lantern put back
        if (!this.level().isClientSide() && !this.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.SOUL_LANTERN)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SOUL_LANTERN));
            this.setUsingItem(false);
            AttributeInstance attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(Witch.SPEED_MODIFIER_DRINKING);
                this.getEntityData().set(Witch.DATA_USING_ITEM, false);
            }
        }
    }
}
