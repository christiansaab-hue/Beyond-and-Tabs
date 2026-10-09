package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR wrecks and reclaim. A unit worth at least {@link #MIN_COST} metal leaves a wreck where it falls holding
 * {@link #WRECK_SHARE} of its metal cost. Any player's workers standing within {@link #RECLAIM_RANGE} blocks of a
 * wreck strip it for metal at {@link #RECLAIM_PER_POWER} metal/s per point of build power (a commander, with
 * triple build power, reclaims three times as fast). Wrecks belong to nobody: reclaiming the enemy's dead is the
 * point, exactly like BAR.
 *
 * A wreck is a vanilla block-display entity (no collision, no pathing impact, saved with the world) carrying its
 * metal in its persistent data, so nothing here needs its own packets or save file. The look follows the dead
 * unit's faction: Sunforged leave broken iron, Gravebound leave bone, the Horde leaves rusted copper.
 * They decay after {@link #LIFETIME_TICKS} and at most {@link #MAX_WRECKS} exist (oldest go first), so a long
 * 8v8 cannot pile them up into a frame-rate problem.
 */
public class WreckServerEvents {

    public static final String TAG = "bt_wreck";
    static final String KEY_METAL = "bt_wreck_metal";
    static final String KEY_BORN = "bt_wreck_born";

    public static final int MIN_COST = 30;
    public static final float WRECK_SHARE = 0.5f;
    public static final double RECLAIM_RANGE = 3.0;
    public static final float RECLAIM_PER_POWER = 5f;   // metal per second per point of build power
    public static final int LIFETIME_TICKS = 20 * 60 * 5;
    public static final int MAX_WRECKS = 150;

    private static final List<Entity> wrecks = new ArrayList<>();

    public static List<Entity> getWrecks() {
        return wrecks;
    }

    public static boolean isWreck(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    public static float metalOf(Entity wreck) {
        return wreck.getPersistentData().getFloat(KEY_METAL);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity le = evt.getEntity();
        if (le.level().isClientSide() || !(le.level() instanceof ServerLevel level) || !(le instanceof Unit unit))
            return;
        ResourceCost cost = unit.getCost();
        if (cost == null || cost.metal() < MIN_COST)
            return;
        spawnWreck(level, le.getX(), le.getY(), le.getZ(), cost.metal() * WRECK_SHARE, Factions.getFaction(unit));
    }

    public static Entity spawnWreck(ServerLevel level, double x, double y, double z, float metal, Faction faction) {
        Entity display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null)
            return null;
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.put("block_state", NbtUtils.writeBlockState(lookFor(faction)));
        // a flattened, slightly sunken heap centred on the death spot
        CompoundTag tf = new CompoundTag();
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tf.put("translation", floats(-0.45f, -0.05f, -0.45f));
        tf.put("scale", floats(0.9f, 0.35f, 0.9f));
        tag.put("transformation", tf);
        display.load(tag);
        display.moveTo(x, y, z, level.random.nextFloat() * 360f, 0);
        display.addTag(TAG);
        display.getPersistentData().putFloat(KEY_METAL, metal);
        display.getPersistentData().putLong(KEY_BORN, level.getGameTime());
        level.addFreshEntity(display);   // onJoin registers it
        return display;
    }

    static BlockState lookFor(Faction faction) {
        if (faction != null && faction.equals(Factions.MONSTERS))
            return Blocks.BONE_BLOCK.defaultBlockState();
        if (faction != null && faction.equals(Factions.PIGLINS))
            return Blocks.EXPOSED_CUT_COPPER.defaultBlockState();
        return Blocks.IRON_BLOCK.defaultBlockState();
    }

    static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v)
            list.add(FloatTag.valueOf(f));
        return list;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !isWreck(evt.getEntity()))
            return;
        if (!wrecks.contains(evt.getEntity()))
            wrecks.add(evt.getEntity());
        while (wrecks.size() > MAX_WRECKS)
            wrecks.remove(0).discard();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        wrecks.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % 20 != 0)
            return;
        tickReclaim(level, 1f);
    }

    /** One reclaim step covering {@code seconds} of work. Public for the game test. */
    public static void tickReclaim(ServerLevel level, float seconds) {
        wrecks.removeIf(Entity::isRemoved);
        if (wrecks.isEmpty())
            return;
        long now = level.getGameTime();
        for (Entity w : new ArrayList<>(wrecks)) {
            long born = w.getPersistentData().getLong(KEY_BORN);
            if (born > now)   // a reload rewinds nothing, but a /time set could; restart its clock
                w.getPersistentData().putLong(KEY_BORN, now);
            else if (now - born > LIFETIME_TICKS)
                remove(level, w, false);
        }
        double r2 = RECLAIM_RANGE * RECLAIM_RANGE;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof WorkerUnit worker) || !(le instanceof Unit u) || !le.isAlive() || le.level() != level)
                continue;
            Entity nearest = null;
            double best = r2;
            for (Entity w : wrecks) {
                if (w.isRemoved())
                    continue;
                double d = w.distanceToSqr(le);
                if (d <= best) {
                    best = d;
                    nearest = w;
                }
            }
            if (nearest == null)
                continue;
            float want = Math.min(metalOf(nearest), worker.getBuildPower() * RECLAIM_PER_POWER * seconds);
            float got = EconomyServerEvents.addReclaimedMetal(u.getOwnerName(), want);
            if (got <= 0)
                continue;
            float left = metalOf(nearest) - got;
            nearest.getPersistentData().putFloat(KEY_METAL, left);
            level.sendParticles(ParticleTypes.CRIT, nearest.getX(), nearest.getY() + 0.3, nearest.getZ(), 3, 0.3, 0.1, 0.3, 0.05);
            if (left <= 0.01f)
                remove(level, nearest, true);
        }
    }

    static void remove(ServerLevel level, Entity w, boolean reclaimed) {
        if (reclaimed)
            level.playSound(null, w.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.6f);
        w.discard();
        wrecks.remove(w);
    }
}
