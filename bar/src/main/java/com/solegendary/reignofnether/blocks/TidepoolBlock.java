package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.tide.TidepoolServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Tidewrought <b>tidepool</b> cell (design/tidewrought_plan.md section 3): a 2/16-high sheet of teal, water-looking
 * surface. It is deliberately NOT vanilla water - no fluid state, so it never spreads, never makes units swim or drown,
 * never turns lava to obsidian or hydrates farmland, and can't be picked up with a bucket.
 * <ul>
 *   <li>No collision, no occlusion, replaceable: everyone walks through it and any building or placed block simply
 *       overwrites it (BuildingValidators.isSoftBlock accepts it; levelFootprint clears it).</li>
 *   <li>Only ever placed by {@link TidepoolServerEvents} (no block item, no loot), which owns lifetime, caps and the
 *       wet buff. The block itself only knows two things: fire/lava next to it evaporates it, and a scheduled
 *       fail-safe tick (saved with the chunk) removes it even if the server restarted and forgot the pool.</li>
 *   <li>Burning entities that wade in are put out (the "wet" effect fire has to beat).</li>
 * </ul>
 * Costs nothing per tick: everything rides vanilla callbacks (neighbour update, scheduled tick, entity-inside).
 */
public class TidepoolBlock extends Block {

    static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public TidepoolBlock(Properties props) {
        super(props);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;   // outline / clicking only
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;   // a thin sheet must never darken the ground under it
    }

    // neighbouring cells of one pool draw as one sheet: no inner side faces
    @Override
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacent, @NotNull Direction dir) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, dir);
    }

    // ---------------------------------------------------------------- fire evaporates it

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && fireNextTo(level, pos))
            TidepoolServerEvents.evaporateCell(sl, pos);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block,
                                @NotNull BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        // removed right here rather than via a scheduled tick: the fail-safe tick is already queued at this position
        // and vanilla drops a second tick for the same position and block
        if (level instanceof ServerLevel sl && fireNextTo(level, pos))
            TidepoolServerEvents.evaporateCell(sl, pos);
    }

    static boolean fireNextTo(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof BaseFireBlock || n.is(Blocks.LAVA) || n.is(Blocks.MAGMA_BLOCK))
                return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- fail-safe expiry

    /**
     * The fail-safe scheduled when the cell was raised (lifetime + a margin). Scheduled block ticks are saved with the
     * chunk, so this also runs after a restart or when the chunk was unloaded at expiry - the pool registry is not
     * saved, and a cell it no longer knows is simply dried up. A cell of a pool that is still live is re-armed.
     */
    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        long left = TidepoolServerEvents.ticksLeftForCell(level, pos);
        if (left > 0) {
            level.scheduleTick(pos, this, (int) Math.min(Integer.MAX_VALUE, left + TidepoolServerEvents.FAILSAFE_MARGIN_TICKS));
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    // ---------------------------------------------------------------- wet

    @Override
    public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
        if (!level.isClientSide() && entity.isOnFire())
            entity.clearFire();
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        // a bubble now and then (client only, no random ticks): reads as water from the RTS camera
        if (random.nextInt(14) == 0)
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + random.nextDouble(), pos.getY() + 0.14,
                    pos.getZ() + random.nextDouble(), 0, 0.01, 0);
    }
}
