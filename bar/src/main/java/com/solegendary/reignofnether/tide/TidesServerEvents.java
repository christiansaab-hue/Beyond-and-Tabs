package com.solegendary.reignofnether.tide;

import com.solegendary.reignofnether.blocks.TidepoolBlock;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Tidewrought <b>Tides</b> buff (design/tidewrought_plan.md 3.3): a tidal unit standing on a tidepool or in real water
 * is {@link #SPEED_BONUS} faster and regenerates {@link #REGEN_PER_PASS} HP every {@link #PASS_TICKS} ticks (2 HP/s).
 * <p>
 * Who is tidal ({@link #isTidal}): a unit whose faction's {@link FactionTraits#tidal} is set (the Tidewrought), or any
 * unit carrying the {@link #TIDAL_TAG} entity tag (summons borrowed from another faction, and the game tests - the
 * faction has no units yet). The same check makes MobilityClass pick TIDAL, so pathing and the buff never disagree.
 * <p>
 * Cost at 8v8: one pass every {@link #PASS_TICKS} ticks over the existing unit list - for a non-tidal unit that is
 * one faction-traits lookup, for a tidal one a single block-state read at its feet. The speed modifier (fixed UUID,
 * transient) is only added or removed when a unit steps on or off the water, never rewritten while it stays. No
 * packets: speed syncs through attributes, health through vanilla.
 */
public class TidesServerEvents {

    public static final int PASS_TICKS = 10;
    /** +20% movement speed while wet (MULTIPLY_TOTAL, so it scales whatever else is on the unit). */
    public static final double SPEED_BONUS = 0.20;
    /** 1 HP per pass = 2 HP/s. Unit-specific regen (Reef Guard) is a later slice's business. */
    public static final float REGEN_PER_PASS = 1f;
    /** Entity tag that makes any unit tidal (see class comment). */
    public static final String TIDAL_TAG = "bt_tidal";

    public static final UUID SPEED_MOD = UUID.fromString("5d1e7a0c-9b3f-4c62-8e15-7a2b3c4d5e61");

    /** Units that currently carry the speed modifier. */
    private static IntOpenHashSet wet = new IntOpenHashSet();
    private static IntOpenHashSet nextWet = new IntOpenHashSet();
    private static final BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();

    /** Is this a tidal unit (Tidewrought faction trait, or the {@link #TIDAL_TAG} tag)? */
    public static boolean isTidal(Entity e) {
        if (!(e instanceof Unit u))
            return false;
        return e.getTags().contains(TIDAL_TAG) || FactionTraits.of(Factions.getFaction(u)).tidal;
    }

    /** Is the entity standing on a tidepool or in real water (source, flowing or waterlogged) right now? */
    public static boolean isWet(LivingEntity le) {
        feet.set(le.getX(), le.getY() + 0.1, le.getZ());
        BlockState bs = le.level().getBlockState(feet);
        return bs.getBlock() instanceof TidepoolBlock || bs.getFluidState().is(FluidTags.WATER);
    }

    /** Does this unit carry the Tides speed modifier right now? */
    public static boolean hasSpeedBonus(LivingEntity le) {
        AttributeInstance attr = le.getAttribute(Attributes.MOVEMENT_SPEED);
        return attr != null && attr.getModifier(SPEED_MOD) != null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (evt.getServer().getTickCount() % PASS_TICKS != 0)
            return;
        pass(evt.getServer());
    }

    /** One Tides pass over every unit on the server. */
    public static void pass(MinecraftServer server) {
        nextWet.clear();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le.level().isClientSide() || le.level().getServer() != server)
                continue;
            // the common case at 8v8 (no Tidewrought in the match): one traits lookup, nothing else
            if (!wet.contains(le.getId()) && !isTidal(le))
                continue;
            if (update(le))
                nextWet.add(le.getId());
        }
        // units that had the bonus but weren't visited (dead, discarded, unloaded) just drop out: the modifier is
        // transient and goes with the entity
        IntOpenHashSet t = wet;
        wet = nextWet;
        nextWet = t;
        nextWet.clear();
    }

    /**
     * Applies the Tides state to one unit: speed modifier on/off only on a change, regen while wet. Returns whether
     * the unit is wet and tidal (carries the bonus). Public for the game test (units it never adds to the level).
     */
    public static boolean update(LivingEntity le) {
        boolean tidal = le.isAlive() && isTidal(le);
        boolean nowWet = tidal && isWet(le);
        AttributeInstance attr = le.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            boolean has = attr.getModifier(SPEED_MOD) != null;
            if (nowWet && !has)
                attr.addTransientModifier(new AttributeModifier(SPEED_MOD, "tides_speed", SPEED_BONUS,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            else if (!nowWet && has)
                attr.removeModifier(SPEED_MOD);
        }
        if (nowWet && le.getHealth() < le.getMaxHealth())
            le.heal(REGEN_PER_PASS);
        return nowWet;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        wet.clear();
        nextWet.clear();
    }
}
