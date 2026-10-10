package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.blocks.ThicketBlock;
import com.solegendary.reignofnether.blocks.ThicketBlockEntity;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court commander (the Grove Warden): <b>Overgrowth</b> (design-factions: the Warden's third power, slice 3).
 * Aim a spot within {@link #RANGE} blocks: thickets burst up fully grown on every free ground block within
 * {@link #RADIUS}, and every enemy ground unit inside is rooted for {@link #ROOT_TICKS} (Slowness VII, the Vine
 * Snare root). Friends are untouched and get instant cover. {@link #CD_SECONDS} s cooldown, no resource cost.
 * <p>
 * Overgrowth may grow past the Seedshapers' planting cap but not past {@link ThicketBlockEntity#MAX_TOTAL} thickets
 * a player: a Warden that keeps casting on fresh ground eventually only roots. Granted to Verdant commanders by
 * CommanderServerEvents.ensureAbility, alongside Wildstride and Thornburst.
 */
public class Overgrowth extends Ability {

    public static final int CD_SECONDS = 60;
    public static final int RANGE = 16;
    public static final int RADIUS = 6;
    public static final int ROOT_TICKS = 40;
    public static final int ROOT_AMPLIFIER = 6;   // -105% speed: cannot walk
    /** Ground up to this far above or below the target still grows (a hillside, not a cliff top). */
    static final int MAX_DY = 4;

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public Overgrowth() {
        super(UnitAction.OVERGROWTH, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Overgrowth",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/azalea_leaves.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.OVERGROWTH,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.OVERGROWTH),
            null,
            List.of(
                FormattedCharSequence.forward("Overgrowth  (commander, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Thickets burst up on free ground within " + RADIUS + " blocks of a spot up to "
                    + RANGE + " away; enemies inside are rooted for " + ROOT_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("The ground itself takes the Court's side.", Style.EMPTY.withItalic(true))
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
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        BlockPos at = MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), targetBp, RANGE);
        at = new BlockPos(at.getX(), targetBp.getY(), at.getZ());
        cast(sl, unitUsing.getOwnerName(), at);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * Grows the ring and roots the enemies around {@code centre} (the clicked ground block) for {@code owner}.
     * Returns how many enemy units were rooted. Public for the bot and the game test.
     */
    public static int cast(ServerLevel sl, String owner, BlockPos centre) {
        if (owner == null || owner.isBlank())
            return 0;
        // thickets: nearest-first, so a cast near the hard cap still covers its middle
        int room = ThicketBlockEntity.MAX_TOTAL - ThicketBlockEntity.countOwned(sl, owner);
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
                if (dx * dx + dz * dz <= RADIUS * RADIUS)
                    cells.add(new BlockPos(dx, 0, dz));
        cells.sort((a, b) -> Integer.compare(a.getX() * a.getX() + a.getZ() * a.getZ(), b.getX() * b.getX() + b.getZ() * b.getZ()));
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        int grown = 0;
        for (BlockPos c : cells) {
            if (grown >= room)
                break;
            BlockPos s = PlantThicket.openSpot(sl, centre.getX() + c.getX(), centre.getZ() + c.getZ(), centre.getY() + 1, MAX_DY);
            if (s != null && ThicketBlock.place(sl, s, owner, true)) {
                grown++;
                if (grown % 4 == 0)   // a burst of leaves across the ring without one particle packet per block
                    sl.sendParticles(leaves, s.getX() + 0.5, s.getY() + 0.8, s.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
            }
        }
        // the root: enemy ground units within the radius (flat distance, a height window for hills)
        double cx = centre.getX() + 0.5, cz = centre.getZ() + 0.5;
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, RADIUS, scratch)) {
            if (!le.isAlive() || le.level() != sl || !ThicketBlock.isEnemyOf(owner, le))
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() - (centre.getY() + 1)) > MAX_DY + 1)
                continue;
            hit.add(le);
        }
        for (LivingEntity le : hit) {
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, ROOT_AMPLIFIER));
            if (le instanceof Mob mob)
                mob.getNavigation().stop();
            le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, le.getX(), le.getY() + 0.5, le.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
        }
        BlockPos sound = centre.above();
        sl.playSound(null, sound, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 3f, 0.5f);
        sl.playSound(null, sound, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.NEUTRAL, 3f, 0.6f);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, centre.getY() + 1.5, cz, 20, RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.0);
        return hit.size();
    }
}
