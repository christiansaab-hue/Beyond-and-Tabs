package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.blocks.VineSnareBlockEntity;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant Court Seedshaper: <b>Vine Snare</b> (design/verdant_court_plan.md, slice 2). Click open ground: the
 * Seedshaper walks there (if it is not already within {@link #PLACE_REACH}) and plants a hidden {@code VineSnareBlock}
 * for {@link ResourceCosts#VINE_SNARE} - paid on planting, so a cancelled errand costs nothing. The first enemy unit
 * to walk onto it is rooted for 3 s and the snare is spent. At most {@link #MAX_PER_PLAYER} live snares per player:
 * enough to seed a choke, not to carpet the map (and each one is a block entity, so the cap is also a perf bound).
 *
 * Errands in progress are kept in a short list ticked by the server: at most one per Seedshaper, each a distance
 * check a tick; an errand is dropped when the Seedshaper dies, is given another move order, or after
 * {@link #ERRAND_TIMEOUT_TICKS}. Registered as an event class.
 */
public class PlantVineSnare extends Ability {

    public static final int CD_SECONDS = 2;
    public static final float PLACE_REACH = 3.5f;
    public static final int MAX_PER_PLAYER = 6;
    public static final int ERRAND_TIMEOUT_TICKS = 30 * 20;

    record Errand(ServerLevel level, LivingEntity worker, BlockPos spot, long deadline) { }

    static final List<Errand> ERRANDS = new ArrayList<>();

    public PlantVineSnare() {
        super(UnitAction.PLANT_VINE_SNARE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, PLACE_REACH, 0, false, true);
        this.showRangeCircle = false;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        ResourceCost cost = ResourceCosts.VINE_SNARE;
        return new AbilityButton("Vine Snare",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/cave_vines.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.PLANT_VINE_SNARE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.PLANT_VINE_SNARE),
            null,
            List.of(
                FormattedCharSequence.forward("Vine Snare", Style.EMPTY.withBold(true)),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("Plant a hidden snare: the first enemy to step on it is rooted for 3 s.", Style.EMPTY),
                FormattedCharSequence.forward("Invisible to the enemy. Up to " + MAX_PER_PLAYER + " at once.", Style.EMPTY),
                FormattedCharSequence.forward("The forest does not forgive trespass.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity worker) || targetBp == null)
            return;
        BlockPos spot = findSpot(sl, targetBp);
        if (spot == null)
            return;
        ERRANDS.removeIf(e -> e.worker() == worker);   // a new click replaces this Seedshaper's last errand
        if (inReach(worker, spot)) {
            plantBy(sl, unitUsing, spot);
            return;
        }
        unitUsing.setMoveTarget(spot);
        ERRANDS.add(new Errand(sl, worker, spot, sl.getGameTime() + ERRAND_TIMEOUT_TICKS));
    }

    static boolean inReach(LivingEntity worker, BlockPos spot) {
        return worker.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) <= PLACE_REACH * PLACE_REACH;
    }

    /** Plants for the worker's owner (charging the cost) and starts the cooldown. */
    void plantBy(ServerLevel sl, Unit unit, BlockPos spot) {
        if (plant(sl, unit.getOwnerName(), spot, true)) {
            setToMaxCooldown(unit);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5,
                    1, 0.2, 0.1, 0.2, 0);   // one mote: the planting is meant to be hard to spot
        }
    }

    /**
     * The air block a snare would occupy for a click on {@code target}: the clicked block itself if it is open and
     * stands on solid ground, else the block above it. Null if neither works or the spot is inside a building.
     */
    public static BlockPos findSpot(ServerLevel level, BlockPos target) {
        for (BlockPos p : new BlockPos[] { target.above(), target }) {
            BlockState here = level.getBlockState(p);
            if (!here.isAir() && !here.canBeReplaced())
                continue;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP))
                continue;
            if (BuildingUtils.findBuilding(false, p) != null)
                return null;
            return p;
        }
        return null;
    }

    /**
     * Places a snare owned by {@code owner} at {@code spot} (an open block on solid ground, see {@link #findSpot}).
     * With {@code charge}, the owner pays {@link ResourceCosts#VINE_SNARE} first and is warned if short. Refused over
     * the per-player cap. Public for the bot and the game test.
     */
    public static boolean plant(ServerLevel level, String owner, BlockPos spot, boolean charge) {
        if (owner == null || owner.isBlank() || spot == null)
            return false;
        BlockState here = level.getBlockState(spot);
        if (!here.isAir() && !here.canBeReplaced())
            return false;
        if (VineSnareBlockEntity.countOwned(level, owner) >= MAX_PER_PLAYER) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(owner);
            if (p != null)
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Vine Snare: at most " + MAX_PER_PLAYER + " at once"), true);
            return false;
        }
        if (charge) {
            ResourceCost cost = ResourceCosts.VINE_SNARE;
            Resources pool = null;
            for (Resources r : ResourcesServerEvents.resourcesList)
                if (r.ownerName.equals(owner))
                    pool = r;
            if (pool == null || pool.ore < cost.ore || pool.wood < cost.wood) {
                // (the flags say which resource the owner HAS enough of; wood = energy, ore = metal)
                ResourcesClientboundPacket.warnInsufficientResources(owner, true,
                        pool != null && pool.wood >= cost.wood, pool != null && pool.ore >= cost.ore, true);
                return false;
            }
            ResourcesServerEvents.addSubtractResources(new Resources(owner, 0, -cost.wood, -cost.ore));
        }
        level.setBlock(spot, BlockRegistrar.VINE_SNARE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(spot) instanceof VineSnareBlockEntity be)
            be.setOwner(owner);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ERRANDS.isEmpty())
            return;
        Iterator<Errand> it = ERRANDS.iterator();
        while (it.hasNext()) {
            Errand e = it.next();
            if (e.level().getServer() != evt.getServer())
                continue;
            LivingEntity w = e.worker();
            if (!w.isAlive() || w.level() != e.level() || e.level().getGameTime() > e.deadline() || !(w instanceof Unit u)) {
                it.remove();
                continue;
            }
            BlockPos mt = u.getMoveGoal() == null ? null : u.getMoveGoal().getMoveTarget();
            if (mt != null && !mt.equals(e.spot())) {   // given another order on the way
                it.remove();
                continue;
            }
            if (inReach(w, e.spot())) {
                it.remove();
                for (Ability a : u.getAbilities().get())
                    if (a instanceof PlantVineSnare pvs) {
                        BlockPos spot = findSpot(e.level(), e.spot().below());
                        if (spot != null) {
                            u.setMoveTarget(null);
                            pvs.plantBy(e.level(), u, spot);
                        }
                        break;
                    }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ERRANDS.clear();
        VineSnareBlockEntity.clearAll();
    }
}
