package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BAR wrecks and reclaim. A unit worth at least {@link #MIN_COST} metal leaves a wreck where it falls holding
 * {@link #WRECK_SHARE} of its metal cost. Any player's workers standing within {@link #RECLAIM_RANGE} blocks of a
 * wreck strip it for metal at {@link #RECLAIM_PER_POWER} metal/s per point of build power (a commander, with
 * triple build power, reclaims three times as fast; Gravebound workers reclaim x2, Horde x1.5). Wrecks belong to nobody: reclaiming the enemy's dead is the
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
    public static final double CHAIN_RANGE = 10.0;

    private static final List<Entity> wrecks = new ArrayList<>();
    // explicit RECLAIM orders: worker entity id -> the wreck it was told to strip. While an entry is live the
    // worker counts as busy (WorkerUnit.isIdle), so shift-queued orders behind it wait their turn. Server only.
    private static final Map<Integer, Entity> reclaimTargets = new HashMap<>();
    // tickReclaim's per-call spatial bucket of the wrecks (cell lists are reused between calls)
    private static final Long2ObjectOpenHashMap<ArrayList<Entity>> CELLS = new Long2ObjectOpenHashMap<>();

    static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public static List<Entity> getWrecks() {
        return wrecks;
    }

    public static boolean isWreck(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    public static float metalOf(Entity wreck) {
        return wreck.getPersistentData().getFloat(KEY_METAL);
    }

    public static void setReclaimTarget(LivingEntity worker, Entity wreck) {
        reclaimTargets.put(worker.getId(), wreck);
    }

    public static Entity getReclaimTarget(LivingEntity worker) {
        Entity w = reclaimTargets.get(worker.getId());
        return w == null || w.isRemoved() ? null : w;
    }

    public static void clearReclaimTarget(Entity worker) {
        if (!reclaimTargets.isEmpty())
            reclaimTargets.remove(worker.getId());
    }

    /** True while a worker still has a live wreck to strip (keeps it out of the idle pool). Server side only. */
    public static boolean isReclaiming(Entity worker) {
        if (reclaimTargets.isEmpty() || worker.level().isClientSide())
            return false;
        Entity w = reclaimTargets.get(worker.getId());
        return w != null && !w.isRemoved();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity le = evt.getEntity();
        if (le.level().isClientSide() || !(le.level() instanceof ServerLevel level) || !(le instanceof Unit unit))
            return;
        if (com.solegendary.reignofnether.unit.DragonRaiseServerEvents.isRisen(le))
            return;   // a Bone Dragon's risen skeleton was free - its death must not pay out metal
        if (com.solegendary.reignofnether.ability.abilities.TotemOfThePack.isSpectral(le))
            return;   // so was a Totem of the Pack's spectral wolf
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
        reclaimTargets.clear();
        CELLS.clear();
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
        if (wrecks.isEmpty()) {
            reclaimTargets.clear();
            return;
        }
        long now = level.getGameTime();
        for (int i = wrecks.size() - 1; i >= 0; i--) {   // backwards: remove() takes the wreck out of the list
            Entity w = wrecks.get(i);
            long born = w.getPersistentData().getLong(KEY_BORN);
            if (born > now)   // a reload rewinds nothing, but a /time set could; restart its clock
                w.getPersistentData().putLong(KEY_BORN, now);
            else if (now - born > LIFETIME_TICKS)
                remove(level, w, false);
        }
        // bucket the wrecks by 16x16 cell once, so each worker only looks at the one to four cells its reclaim range
        // touches instead of every wreck on the map (workers x wrecks was the cost of this method at 8v8)
        if (CELLS.size() > 4 * MAX_WRECKS)   // empty cells of old battlefields: start over now and then
            CELLS.clear();
        for (ArrayList<Entity> cell : CELLS.values())
            cell.clear();
        for (Entity w : wrecks) {
            long k = cellKey(Mth.floor(w.getX()) >> 4, Mth.floor(w.getZ()) >> 4);
            ArrayList<Entity> cell = CELLS.get(k);
            if (cell == null) {
                cell = new ArrayList<>();
                CELLS.put(k, cell);
            }
            cell.add(w);
        }
        // drop orders whose wreck is gone (decayed, raised, stripped by someone else) or whose worker died
        reclaimTargets.values().removeIf(Entity::isRemoved);
        double r2 = RECLAIM_RANGE * RECLAIM_RANGE;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof WorkerUnit worker) || !(le instanceof Unit u) || !le.isAlive() || le.level() != level)
                continue;
            Entity nearest = null;
            double best = r2;
            Entity ordered = reclaimTargets.isEmpty() ? null : reclaimTargets.get(le.getId());
            if (ordered != null) {
                double d = ordered.distanceToSqr(le);
                if (d <= r2) {
                    // an explicit order strips ITS wreck first, not whatever heap happens to be closer
                    best = -1;
                    nearest = ordered;
                } else if (u.getMoveGoal().getMoveTarget() == null && ((net.minecraft.world.entity.Mob) le).getNavigation().isDone()) {
                    // stopped short (crowded heap, blocked path): walk on instead of standing there busy forever
                    u.setMoveTarget(ordered.blockPosition());
                }
            }
            if (best >= 0) {
                int x0 = Mth.floor(le.getX() - RECLAIM_RANGE) >> 4, x1 = Mth.floor(le.getX() + RECLAIM_RANGE) >> 4;
                int z0 = Mth.floor(le.getZ() - RECLAIM_RANGE) >> 4, z1 = Mth.floor(le.getZ() + RECLAIM_RANGE) >> 4;
                for (int cx = x0; cx <= x1; cx++)
                    for (int cz = z0; cz <= z1; cz++) {
                        ArrayList<Entity> cell = CELLS.get(cellKey(cx, cz));
                        if (cell == null)
                            continue;
                        for (int i = 0, n = cell.size(); i < n; i++) {
                            Entity w = cell.get(i);
                            if (w.isRemoved())   // emptied earlier in this pass
                                continue;
                            double d = w.distanceToSqr(le);
                            if (d <= best) {
                                best = d;
                                nearest = w;
                            }
                        }
                    }
            }
            if (nearest == null)
                continue;
            float want = Math.min(metalOf(nearest), worker.getBuildPower() * RECLAIM_PER_POWER * factionReclaim(u) * seconds);
            float got = EconomyServerEvents.addReclaimedMetal(u.getOwnerName(), want);
            if (got <= 0)
                continue;
            float left = metalOf(nearest) - got;
            nearest.getPersistentData().putFloat(KEY_METAL, left);
            level.sendParticles(ParticleTypes.CRIT, nearest.getX(), nearest.getY() + 0.3, nearest.getZ(), 3, 0.3, 0.1, 0.3, 0.05);
            // nanolathe beam to the wreck; reclaim steps once a second, so the beam lives a full step (no gaps)
            com.solegendary.reignofnether.barfx.BarFx.nano(le, nearest.getX(), nearest.getY() + 0.4, nearest.getZ(),
                com.solegendary.reignofnether.barfx.BarFx.NANO_INTERVAL, 22);
            if (left <= 0.01f) {
                remove(level, nearest, true);
                chainToNextWreck(nearest, le, u);
            }
        }
    }

    /**
     * BAR's area-reclaim habit, without a new order: a worker that empties a wreck walks on to the next wreck within
     * {@link #CHAIN_RANGE} blocks of it, so parking a worker in a field of wrecks clears the whole field. Only an
     * idle worker chains, so it never overrides an order its owner gave meanwhile.
     */
    static void chainToNextWreck(Entity emptied, LivingEntity worker, Unit unit) {
        // a worker with orders queued behind this one (area reclaim, shift-queue) follows those instead; a plain
        // MOVE here would also wipe that queue
        if (!unit.isIdle() || UnitServerEvents.hasQueuedActions(worker.getId()))
            return;
        Entity next = null;
        double best = CHAIN_RANGE * CHAIN_RANGE;
        for (Entity w : wrecks) {
            if (w.isRemoved())
                continue;
            double d = w.distanceToSqr(emptied);
            if (d < best) {
                best = d;
                next = w;
            }
        }
        if (next != null)
            UnitServerEvents.addActionItem(unit.getOwnerName(), com.solegendary.reignofnether.unit.UnitAction.MOVE, -1,
                new int[] { worker.getId() }, next.blockPosition(), next.blockPosition());
    }

    /** design-factions.md: the Gravebound Gravedigger reclaims double; the Horde's Clan Builder half again. */
    public static float factionReclaim(Unit unit) {
        Faction f = Factions.getFaction(unit);
        if (f != null && f.equals(Factions.MONSTERS))
            return 2f;
        if (f != null && f.equals(Factions.PIGLINS))
            return 1.5f;
        return 1f;
    }

    /** Takes metal off a wreck (Raise Dead); an emptied wreck is removed. */
    public static void drain(ServerLevel level, Entity w, float amount) {
        float left = metalOf(w) - amount;
        w.getPersistentData().putFloat(KEY_METAL, Math.max(0, left));
        if (left <= 0.01f)
            remove(level, w, false);
    }

    static void remove(ServerLevel level, Entity w, boolean reclaimed) {
        if (reclaimed)
            level.playSound(null, w.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.6f);
        w.discard();
        wrecks.remove(w);
        reclaimTargets.values().removeIf(t -> t == w);
    }
}
