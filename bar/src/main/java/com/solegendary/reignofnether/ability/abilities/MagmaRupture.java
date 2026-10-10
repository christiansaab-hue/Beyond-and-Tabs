package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ironhide Horde (Blaze): <b>Magma Rupture</b>. Aim at the ground: a {@link #LENGTH}-block crack opens from beneath
 * the Blaze toward the target. For {@link #TELEGRAPH_TICKS} ticks smoke and lava drips mark the crack (BAR rule:
 * the danger is shown before it lands), then it erupts - {@link #DAMAGE} and {@link #BURN_SECONDS} s of fire to every
 * enemy standing on it. The line is fixed at cast time, so what the enemy sees is exactly what gets hit.
 * {@link #CD_SECONDS} s cooldown: a zoning tool against a clumped column, not a sniper shot.
 */
public class MagmaRupture extends Ability {

    public static final int CD_SECONDS = 35;
    public static final int LENGTH = 12;
    public static final float HALF_WIDTH = 1.25f;
    public static final float DAMAGE = 12f;
    public static final int BURN_SECONDS = 3;
    public static final int TELEGRAPH_TICKS = 20;

    public MagmaRupture() {
        super(UnitAction.MAGMA_RUPTURE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Magma Rupture",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/magma_cream.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.MAGMA_RUPTURE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.MAGMA_RUPTURE),
            null,
            List.of(
                FormattedCharSequence.forward("Magma Rupture  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Cracks the ground in a " + LENGTH + "-block line. After 1 s it erupts: "
                    + (int) DAMAGE + " damage and " + BURN_SECONDS + " s of fire to enemies on it.", Style.EMPTY),
                FormattedCharSequence.forward("The smoking crack is visible - enemies can step off it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        // the crack runs along the ground under the (hovering) Blaze, not at its height
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, self.getBlockX(), self.getBlockZ());
        Vec3 from = new Vec3(self.getX(), gy, self.getZ());
        Vec3 flat = new Vec3(targetBp.getX() + 0.5 - from.x, 0, targetBp.getZ() + 0.5 - from.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 to = from.add(dir.scale(LENGTH));
        String owner = unitUsing.getOwnerName();

        // the telegraph twice (now and halfway), so the crack reads as a line that is about to go rather than a puff
        telegraph(sl, from, dir);
        sl.playSound(null, BlockPos.containing(from), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 3f, 0.7f);
        int now = sl.getServer().getTickCount();
        sl.getServer().tell(new TickTask(now + TELEGRAPH_TICKS / 2, () -> telegraph(sl, from, dir)));
        sl.getServer().tell(new TickTask(now + TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // a Blaze killed mid-cast never finishes the spell
                erupt(sl, self, owner, from, to);
        }));

        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 from, Vec3 dir) {
        // ~2 particles per block: enough to read the line, cheap enough for a dozen Blazes casting at once
        for (int i = 1; i <= LENGTH; i++) {
            Vec3 at = from.add(dir.scale(i));
            sl.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 1, 0.3, 0.02, 0.3, 0.01);
            if (i % 2 == 0)
                sl.sendParticles(ParticleTypes.DRIPPING_LAVA, at.x, at.y + 0.6, at.z, 1, 0.3, 0.1, 0.3, 0);
        }
    }

    /**
     * The eruption: {@link #DAMAGE} plus {@link #BURN_SECONDS} s of fire to every hostile unit within
     * {@link #HALF_WIDTH} of the line. Returns how many were caught. Public for the game test.
     */
    public static int erupt(ServerLevel sl, LivingEntity self, String owner, Vec3 from, Vec3 to) {
        int hit = 0;
        for (LivingEntity le : CommanderDGun.hits(self, owner, from, to, HALF_WIDTH)) {
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            le.setSecondsOnFire(BURN_SECONDS);
            hit++;
        }
        Vec3 seg = to.subtract(from);
        for (int i = 1; i <= LENGTH; i++) {
            Vec3 at = from.add(seg.scale(i / (double) LENGTH));
            sl.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 2, 0.3, 0.1, 0.3, 0);
            sl.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 2, 0.3, 0.4, 0.3, 0.03);
        }
        Vec3 mid = from.add(seg.scale(0.5));
        sl.playSound(null, BlockPos.containing(mid), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.6f);
        sl.playSound(null, BlockPos.containing(mid), SoundEvents.LAVA_POP, SoundSource.HOSTILE, 3f, 0.8f);
        return hit;
    }
}
