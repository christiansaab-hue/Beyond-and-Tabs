package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Cover in Verdant thickets: which units are hidden right now. A unit is hidden while it stands in a grown
 * {@link ThicketBlock} owned by its owner or an ally, no enemy unit is within {@link #REVEAL_RADIUS} blocks, and it
 * hasn't attacked in the last {@link #REVEAL_TICKS} ticks (having a target or hitting something counts).
 * <p>
 * Hiding reuses the Shade Ranger's cloak plumbing rather than adding its own: a hidden unit gets the vanilla
 * invisible flag (synced for free), so enemy clients don't draw it (FogOfWarClientEvents), its health bar, strategic
 * icon or minimap dot; its owner and allies still see a translucent ghost (EntityMixin.isInvisibleTo). Enemy target
 * searches skip it (MiscUtil.findClosestAttackableEntity asks {@link #isHidden}), and whoever was already shooting at
 * it loses it the moment it goes under.
 * <p>
 * Cost: one pass every {@link #PASS_TICKS} ticks over the unit list - a block-state read per unit; only units actually
 * standing in a thicket pay for a block-entity lookup and one small UnitGrid query. Nothing per frame, no packets of
 * its own. Registered as an event class.
 */
public class ThicketCover {

    public static final int PASS_TICKS = 5;
    public static final double REVEAL_RADIUS = 4;
    public static final int REVEAL_TICKS = 3 * 20;
    /** How far an enemy that was already targeting a unit can be when it goes under (its aggro reach, roughly). */
    static final double DROP_TARGET_RADIUS = 32;

    static final Int2ObjectOpenHashMap<LivingEntity> HIDDEN = new Int2ObjectOpenHashMap<>();
    /** Unit id -> game time until which it can't hide (it attacked). Pruned each pass. */
    static final Int2LongOpenHashMap REVEALED_UNTIL = new Int2LongOpenHashMap();

    // reused by every pass (server thread only)
    private static final Int2ObjectOpenHashMap<LivingEntity> next = new Int2ObjectOpenHashMap<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();
    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** Is this unit hidden in a thicket right now (server side)? Enemies must not see or target it. */
    public static boolean isHidden(Entity e) {
        return e != null && !HIDDEN.isEmpty() && HIDDEN.get(e.getId()) == e && e.isAlive();
    }

    public static int hiddenCount() {
        return HIDDEN.size();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (evt.getServer().getTickCount() % PASS_TICKS != 0)
            return;
        refresh(evt.getServer());
    }

    /** One cover pass over every unit on the server. Public for the game test (which can't wait for the cadence). */
    public static void refresh(MinecraftServer server) {
        if (ThicketBlockEntity.LIVE.isEmpty() && HIDDEN.isEmpty()) {
            // no thickets anywhere (most games without a Verdant player): nothing to do. Reveals recorded meanwhile
            // (Shade Ranger shots, Holy Bell) can't matter without a bush to hide in - drop them so they don't pile up
            if (!REVEALED_UNTIL.isEmpty())
                REVEALED_UNTIL.clear();
            return;
        }
        next.clear();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !le.isAlive() || !(le.level() instanceof ServerLevel sl) || sl.getServer() != server)
                continue;
            if (canHide(sl, le, u))
                next.put(le.getId(), le);
        }
        // went under: invisible, and enemies already on it lose the target
        for (Int2ObjectMap.Entry<LivingEntity> e : next.int2ObjectEntrySet()) {
            LivingEntity le = e.getValue();
            if (!HIDDEN.containsKey(e.getIntKey())) {
                le.setInvisible(true);
                onHide((ServerLevel) le.level(), le);
            } else if (!le.isInvisible()) {
                le.setInvisible(true);   // vanilla clears the flag whenever a potion effect changes: re-assert it
            }
        }
        // came out (left the bush, an enemy got close, it attacked, it died)
        for (Int2ObjectMap.Entry<LivingEntity> e : HIDDEN.int2ObjectEntrySet())
            if (!next.containsKey(e.getIntKey()))
                unhide(e.getValue());
        HIDDEN.clear();
        HIDDEN.putAll(next);
        next.clear();
        if (!REVEALED_UNTIL.isEmpty()) {
            long now = server.overworld().getGameTime();
            var it = REVEALED_UNTIL.int2LongEntrySet().iterator();
            while (it.hasNext())
                if (it.next().getLongValue() < now)
                    it.remove();
        }
    }

    static boolean canHide(ServerLevel sl, LivingEntity le, Unit u) {
        cursor.set(le.getX(), le.getY() + 0.1, le.getZ());
        BlockState bs = sl.getBlockState(cursor);
        if (!ThicketBlock.isMature(bs))
            return false;
        String owner = u.getOwnerName();
        if (owner == null || owner.isBlank())
            return false;
        if (!(sl.getBlockEntity(cursor) instanceof ThicketBlockEntity thicket))
            return false;
        String to = thicket.getOwner();
        if (!owner.equals(to) && !AlliancesServerEvents.isAllied(owner, to))
            return false;
        long now = sl.getGameTime();
        if (isEngaged(u)) {
            extendReveal(le.getId(), now + REVEAL_TICKS);
            return false;
        }
        if (REVEALED_UNTIL.containsKey(le.getId()) && REVEALED_UNTIL.get(le.getId()) > now)
            return false;
        // an enemy right on top of the bush sees in
        for (LivingEntity other : UnitGrid.near(sl, le.getX(), le.getZ(), REVEAL_RADIUS, scratch)) {
            if (other == le || !other.isAlive() || !(other instanceof Unit ou))
                continue;
            String o = ou.getOwnerName();
            if (o == null || o.isBlank() || owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            if (other.distanceToSqr(le) <= REVEAL_RADIUS * REVEAL_RADIUS)
                return false;
        }
        return true;
    }

    /** Fighting gives a unit away: it has a unit target, or is hitting a building. */
    static boolean isEngaged(Unit u) {
        if (u.getTargetGoal() != null && u.getTargetGoal().getTarget() != null)
            return true;
        return u instanceof AttackerUnit au && AttackerUnit.isAttackingBuilding(au);
    }

    static void onHide(ServerLevel sl, LivingEntity le) {
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, le.getX(), le.getY() + 1, le.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
        String owner = ((Unit) le).getOwnerName();
        for (LivingEntity other : UnitGrid.near(sl, le.getX(), le.getZ(), DROP_TARGET_RADIUS, scratch)) {
            if (!(other instanceof Unit ou) || ou.getTargetGoal() == null || ou.getTargetGoal().getTarget() != le)
                continue;
            String o = ou.getOwnerName();
            if (owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            ou.getTargetGoal().setTarget(null);
        }
    }

    static void unhide(LivingEntity le) {
        // a Shade Ranger keeps its own cloak: its tick decides its flag (and re-cloaks it if it is still standing still)
        if (!(le instanceof ShadeRangerUnit))
            le.setInvisible(false);
    }

    /** Drops a unit's cover now, for {@link #REVEAL_TICKS} (it attacked). */
    public static void reveal(LivingEntity le) {
        revealFor(le, REVEAL_TICKS);
    }

    /** Drops a unit's cover now and keeps it out of any thicket for {@code ticks} (Holy Bell: Concealment.revealFor). */
    public static void revealFor(LivingEntity le, int ticks) {
        extendReveal(le.getId(), le.level().getGameTime() + ticks);
        if (HIDDEN.remove(le.getId()) != null)
            unhide(le);
    }

    // never shortens a reveal: a 3 s "it fired" must not cut a 6 s Holy Bell reveal short
    private static void extendReveal(int id, long until) {
        if (!REVEALED_UNTIL.containsKey(id) || REVEALED_UNTIL.get(id) < until)
            REVEALED_UNTIL.put(id, until);
    }

    // a hit landed by a unit in a thicket (melee or a projectile it fired) gives it away at once, not at the next pass
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (HIDDEN.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        DamageSource src = evt.getSource();
        if (src.getEntity() instanceof LivingEntity attacker && HIDDEN.containsKey(attacker.getId()))
            reveal(attacker);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        HIDDEN.clear();
        REVEALED_UNTIL.clear();
        next.clear();
    }
}
