package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Verdant Court <b>Thicket</b> - the Living Terrain signature (design/verdant_court_plan.md, slice 3). A dense azalea
 * bush about 1.4 blocks tall (the mature model overhangs into the block above) that a Seedshaper plants in 3x3
 * patches (PlantThicket) and the Grove Warden raises in a wide ring (Overgrowth).
 * <ul>
 *   <li>No collision: units walk through it. Enemies of its owner are slowed by {@link #ENEMY_SLOW} while inside.</li>
 *   <li>Cover: a mature thicket hides its owner's and allies' units standing in it from enemy targeting, rendering
 *       and minimap (ThicketCover runs the bookkeeping a few times a second).</li>
 *   <li>Burns: fire next to it (or a burning unit walking in) sets it alight within {@link #BURN_DELAY_TICKS}, and
 *       the fire then catches the neighbouring thickets - a patch goes up in about a second. Vanilla fire spread
 *       also treats it as very flammable.</li>
 *   <li>Cut: an enemy melee unit pushing through hacks at it, one stroke a second per block; {@link #HITS_TO_CUT}
 *       strokes clear it. Ranged units can't cut - they have to burn it or walk around.</li>
 *   <li>It never regrows: once burned or cut it is gone.</li>
 * </ul>
 * All of it rides vanilla callbacks (entity-inside-block, neighbour updates, scheduled ticks): an untouched thicket
 * costs nothing per tick, so a few hundred of them at 8v8 are fine.
 */
public class ThicketBlock extends BaseEntityBlock {

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MATURE = 3;
    /** Planted at age 0, mature after 3 stages: 4 s of visible growth. */
    public static final int GROW_STAGE_TICKS = 27;
    public static final int BURN_DELAY_TICKS = 10;
    public static final int HITS_TO_CUT = 3;
    public static final int CUT_INTERVAL_TICKS = 20;
    /** Enemies keep 70% of their speed inside (vanilla's stuck-in-block multiplier, like a berry bush). */
    public static final double ENEMY_SLOW = 0.3;
    static final Vec3 SLOW = new Vec3(1 - ENEMY_SLOW, 1.0, 1 - ENEMY_SLOW);

    // outline only (there is no collision); grows with the bush so a sapling-sized one is hard to click
    static final VoxelShape[] SHAPES = {
            Block.box(4, 0, 4, 12, 6, 12),
            Block.box(2, 0, 2, 14, 11, 14),
            Block.box(1, 0, 1, 15, 16, 15),
            Block.box(1, 0, 1, 15, 16, 15)
    };

    public ThicketBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ThicketBlockEntity(pos, state);
    }

    // BaseEntityBlock defaults to INVISIBLE: the bush is an ordinary block model
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    public static boolean isMature(BlockState state) {
        return state.getBlock() instanceof ThicketBlock && state.getValue(AGE) >= MATURE;
    }

    // ---------------------------------------------------------------- growth and fire (scheduled ticks)

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level.isClientSide())
            return;
        if (state.getValue(AGE) < MATURE)
            level.scheduleTick(pos, this, GROW_STAGE_TICKS);
        if (fireNextTo(level, pos))
            level.scheduleTick(pos, this, BURN_DELAY_TICKS);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block,
                                @NotNull BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        // fire placed beside it (or a neighbouring thicket just caught): catch in half a second. scheduleTick ignores
        // a duplicate, so a growth tick already queued just runs the fire check first
        if (!level.isClientSide() && fireNextTo(level, pos))
            level.scheduleTick(pos, this, BURN_DELAY_TICKS);
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (fireNextTo(level, pos)) {
            ignite(level, pos);
            return;
        }
        int age = state.getValue(AGE);
        if (age < MATURE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), 2);   // no neighbour update: it is the same bush
            if (age + 1 < MATURE)
                level.scheduleTick(pos, this, GROW_STAGE_TICKS);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4 + age * 0.3, pos.getZ() + 0.5,
                    1, 0.3, 0.2, 0.3, 0);
        }
    }

    static boolean fireNextTo(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof BaseFireBlock || n.is(Blocks.LAVA) || n.is(Blocks.MAGMA_BLOCK))
                return true;
        }
        return false;
    }

    /** Replaces the thicket with fire (which its neighbours then catch from, via neighborChanged). */
    public static void ignite(Level level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof ThicketBlock))
            return;
        level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
    }

    // vanilla fire spread: about as eager as wool, so even without our neighbour hook a fire on the map eats it
    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 100;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    // ---------------------------------------------------------------- units inside

    @Override
    public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
        // server only: unit movement is server-driven, and the owner lives on the server's block entity
        if (!(level instanceof ServerLevel sl) || !(entity instanceof LivingEntity le) || !le.isAlive())
            return;
        if (le.isOnFire()) {
            ignite(level, pos);   // a burning unit walking in sets the bush alight, friend or foe
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ThicketBlockEntity thicket) || !isEnemyOf(thicket.getOwner(), entity))
            return;
        entity.makeStuckInBlock(state, SLOW);
        if (entity instanceof AttackerUnit && !(entity instanceof RangedAttackerUnit))
            cut(sl, pos, thicket, le);
    }

    /** True if the entity is a ground unit hostile to the thicket's owner (not theirs, not an ally's, not neutral). */
    public static boolean isEnemyOf(String owner, Entity entity) {
        if (!(entity instanceof Unit u) || owner == null || owner.isBlank() || u.isFlyingUnit())
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }

    /** One stroke of a melee unit's blade, at most once a second per block; the third clears it. */
    static void cut(ServerLevel sl, BlockPos pos, ThicketBlockEntity thicket, LivingEntity cutter) {
        long now = sl.getGameTime();
        if (now - thicket.lastCutAt < CUT_INTERVAL_TICKS)
            return;
        thicket.lastCutAt = now;
        thicket.hits++;
        if (cutter instanceof Mob mob)
            mob.swing(InteractionHand.MAIN_HAND);
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AZALEA_LEAVES.defaultBlockState());
        sl.sendParticles(leaves, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
        if (thicket.hits >= HITS_TO_CUT) {
            sl.destroyBlock(pos, false);
        } else {
            sl.playSound(null, pos, SoundEvents.AZALEA_LEAVES_HIT, SoundSource.BLOCKS, 1f, 0.8f);
        }
    }

    /**
     * Places a thicket owned by {@code owner} (grown at once with {@code mature}, else from a sprout) on an open spot.
     * No cap or cost check here - see PlantThicket / Overgrowth. Public for the abilities and the game test.
     */
    public static boolean place(Level level, BlockPos spot, String owner, boolean mature) {
        BlockState state = BlockRegistrar.THICKET.get().defaultBlockState().setValue(AGE, mature ? MATURE : 0);
        if (!level.setBlock(spot, state, 3))
            return false;
        if (level.getBlockEntity(spot) instanceof ThicketBlockEntity be)
            be.setOwner(owner);
        return true;
    }
}
