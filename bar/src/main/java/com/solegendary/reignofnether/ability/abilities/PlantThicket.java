package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.blocks.ThicketBlock;
import com.solegendary.reignofnether.blocks.ThicketBlockEntity;
import com.solegendary.reignofnether.blocks.VineSnareBlock;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant Court Seedshaper: <b>Plant Thicket</b> (design/verdant_court_plan.md, slice 3 - Living Terrain). Click open
 * ground: the Seedshaper walks there (if not already within {@link #PLACE_REACH}) and grows a 3x3 patch of
 * {@link ThicketBlock}s for {@link ResourceCosts#THICKET}, paid on planting. The bushes sprout and take 4 s to grow
 * to full height; only grown ones give cover. A player holds at most {@link ThicketBlockEntity#MAX_PLANTED} planted
 * thickets (a patch near the cap plants only what fits) - each one is a block entity, so the cap is also the perf bound.
 *
 * Errands work like the Vine Snare's: a short list ticked by the server, at most one per Seedshaper, dropped when the
 * Seedshaper dies, is given another move order, or after {@link #ERRAND_TIMEOUT_TICKS}. Registered as an event class.
 */
public class PlantThicket extends Ability {

    public static final int CD_SECONDS = 5;
    public static final float PLACE_REACH = 4f;
    public static final int ERRAND_TIMEOUT_TICKS = 30 * 20;
    /** Ground within this many blocks of the clicked height counts: a patch drapes over a gentle slope, not a cliff. */
    static final int PATCH_MAX_DY = 2;

    record Errand(ServerLevel level, LivingEntity worker, BlockPos spot, long deadline) { }

    static final List<Errand> ERRANDS = new ArrayList<>();

    public PlantThicket() {
        super(UnitAction.PLANT_THICKET, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, PLACE_REACH, 1.5f, false, true);
        this.showRangeCircle = false;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        ResourceCost cost = ResourceCosts.THICKET;
        return new AbilityButton("Plant Thicket",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_leaves.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.PLANT_THICKET,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.PLANT_THICKET),
            null,
            List.of(
                FormattedCharSequence.forward("Plant Thicket", Style.EMPTY.withBold(true)),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("Grow a 3x3 thicket (4 s). Your units inside are hidden from enemies", Style.EMPTY),
                FormattedCharSequence.forward("until one comes within 4 blocks or they attack. Slows enemies.", Style.EMPTY),
                FormattedCharSequence.forward("Burns fast; melee cuts it. Up to " + ThicketBlockEntity.MAX_PLANTED + " blocks.", Style.EMPTY),
                FormattedCharSequence.forward("Where the Court walks, the forest follows.", Style.EMPTY.withItalic(true))
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
        BlockPos spot = openSpot(sl, targetBp.getX(), targetBp.getZ(), targetBp.getY() + 1, PATCH_MAX_DY);
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

    void plantBy(ServerLevel sl, Unit unit, BlockPos centre) {
        if (plantPatch(sl, unit.getOwnerName(), centre, true) > 0)
            setToMaxCooldown(unit);
    }

    /**
     * The open spot a thicket would take in column (x, z): the highest block within {@code maxDy} of {@code refY}
     * that is air or a replaceable plant, dry, on a sturdy top face and not inside a building. Null otherwise. Never
     * takes a Vine Snare's or another thicket's spot, and never grows at a water's edge.
     */
    public static BlockPos openSpot(ServerLevel level, int x, int z, int refY, int maxDy) {
        // scanned down from the top of the window, not read off the heightmap: a ceiling, a canopy or a structure far
        // above (game-test arenas sit under one) must not hide the ground the player actually clicked
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, refY + maxDy, z);
        for (; p.getY() >= refY - maxDy; p.move(Direction.DOWN)) {
            BlockState here = level.getBlockState(p);
            if (here.getBlock() instanceof ThicketBlock || here.getBlock() instanceof VineSnareBlock)
                return null;   // already planted
            if ((!here.isAir() && !here.canBeReplaced()) || !here.getFluidState().isEmpty())
                continue;
            BlockPos below = p.below();
            if (!level.getFluidState(below).isEmpty())
                return null;   // water's edge: no bushes on the surface of a lake
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                continue;
            BlockPos spot = p.immutable();
            return BuildingUtils.findBuilding(false, spot) != null ? null : spot;
        }
        return null;
    }

    /**
     * Grows a 3x3 patch centred on {@code centre} for {@code owner} (sprouts that grow over 4 s). Plants only what
     * fits under {@link ThicketBlockEntity#MAX_PLANTED}; with {@code charge}, the owner pays
     * {@link ResourceCosts#THICKET} once if at least one bush fits (and is warned if short). Returns the number
     * planted. Public for the bot and the game test.
     */
    public static int plantPatch(ServerLevel level, String owner, BlockPos centre, boolean charge) {
        if (owner == null || owner.isBlank() || centre == null)
            return 0;
        int room = ThicketBlockEntity.MAX_PLANTED - ThicketBlockEntity.countOwned(level, owner);
        if (room <= 0) {
            tell(level, owner, "Thickets: at most " + ThicketBlockEntity.MAX_PLANTED + " blocks at once");
            return 0;
        }
        List<BlockPos> spots = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos s = openSpot(level, centre.getX() + dx, centre.getZ() + dz, centre.getY(), PATCH_MAX_DY);
                if (s != null)
                    spots.add(s);
            }
        if (spots.isEmpty())
            return 0;
        // the centre first, so a patch near the cap still covers the spot that was clicked
        spots.sort((a, b) -> Double.compare(a.distSqr(centre), b.distSqr(centre)));
        if (charge && !pay(owner, ResourceCosts.THICKET))
            return 0;
        int n = 0;
        for (BlockPos s : spots) {
            if (n >= room)
                break;
            if (ThicketBlock.place(level, s, owner, false))
                n++;
        }
        if (n > 0)
            level.playSound(null, centre, SoundEvents.AZALEA_PLACE, SoundSource.BLOCKS, 1.5f, 0.8f);
        return n;
    }

    /** Takes {@code cost} from the owner's pool, or warns them and returns false if they can't afford it. */
    static boolean pay(String owner, ResourceCost cost) {
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
        return true;
    }

    static void tell(ServerLevel level, String owner, String msg) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayerByName(owner);
        if (p != null)
            p.displayClientMessage(Component.literal(msg), true);
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
                    if (a instanceof PlantThicket pt) {
                        u.setMoveTarget(null);
                        pt.plantBy(e.level(), u, e.spot());
                        break;
                    }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ERRANDS.clear();
        ThicketBlockEntity.clearAll();
    }
}
