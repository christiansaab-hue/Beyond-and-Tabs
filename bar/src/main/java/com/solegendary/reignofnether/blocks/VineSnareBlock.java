package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Verdant Court <b>Vine Snare</b> (design/verdant_court_plan.md, slice 2): a hidden trap a Seedshaper plants on open
 * ground (PlantVineSnare). The block itself draws nothing, has no shape and no collision, so it cannot be seen,
 * clicked or bumped into; its owner and the owner's allies see a mossy tangle drawn by {@link VineSnareRenderer}
 * from the owner name the {@link VineSnareBlockEntity} syncs. The first enemy unit that walks into it is rooted for
 * {@link #ROOT_TICKS} (Slowness VII, the Thornbow / Crypt Tide root) and the snare is spent. Friendly and neutral
 * units, and flyers, pass over it untouched.
 *
 * Triggering rides vanilla's entity-inside-block check (Entity.checkInsideBlocks, run as an entity moves), so the
 * snare costs nothing while nobody stands in it: no ticking block entity, no scans.
 */
public class VineSnareBlock extends BaseEntityBlock {

    /** 3 s rooted. */
    public static final int ROOT_TICKS = 60;
    public static final int ROOT_AMPLIFIER = 6;   // -105% speed: cannot walk

    public VineSnareBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new VineSnareBlockEntity(pos, state);
    }

    // drawn (for friends only) by the block entity renderer; the block model is never used
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // no outline and nothing to click: an enemy hovering the spot learns nothing
    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof LivingEntity le) || !le.isAlive())
            return;
        if (!(level.getBlockEntity(pos) instanceof VineSnareBlockEntity snare) || !isEnemyOf(snare.getOwner(), entity))
            return;
        spring(sl, pos, le);
    }

    /** True if the entity is a walking unit hostile to the snare's owner (not theirs, not an ally's, not neutral). */
    public static boolean isEnemyOf(String owner, Entity entity) {
        if (!(entity instanceof Unit u) || owner == null || owner.isBlank())
            return false;
        if (u.isFlyingUnit())
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }

    /** Roots the victim and spends the snare. */
    static void spring(ServerLevel sl, BlockPos pos, LivingEntity victim) {
        sl.removeBlock(pos, false);   // first: a second unit in the same tick finds air
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, ROOT_AMPLIFIER));
        if (victim instanceof Mob mob)
            mob.getNavigation().stop();
        victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
        sl.playSound(null, pos, SoundEvents.CAVE_VINES_PLACE, SoundSource.NEUTRAL, 1.4f, 0.7f);
        sl.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.NEUTRAL, 1.0f, 0.6f);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState()),
                pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 14, 0.35, 0.3, 0.35, 0.05);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, victim.getX(), victim.getY() + 0.5, victim.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
    }
}
