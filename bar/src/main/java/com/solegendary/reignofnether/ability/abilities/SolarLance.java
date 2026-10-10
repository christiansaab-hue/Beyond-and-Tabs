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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sun Colossus: <b>Solar Lance</b> (design-factions.md). Aim at a spot: the colossus gathers light for
 * {@link #CHARGE_TICKS} ticks (a column of sparks marks the lane, so the enemy can scatter), then a beam sweeps a
 * {@link #LENGTH}-block, 5-wide lane: {@link #DAMAGE} to every hostile unit in it. 45 s cooldown. Telegraphed on
 * purpose - the T3 rules forbid instant wins.
 */
public class SolarLance extends Ability {

    public static final int CD_SECONDS = 45;
    public static final int LENGTH = 20;
    public static final float HALF_WIDTH = 2.5f;
    public static final float DAMAGE = 40f;
    public static final int CHARGE_TICKS = 30;

    public SolarLance() {
        super(UnitAction.SOLAR_LANCE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Solar Lance",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/spectral_arrow.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SOLAR_LANCE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SOLAR_LANCE),
            null,
            List.of(
                FormattedCharSequence.forward("Solar Lance  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("After a 1.5 s charge, a " + LENGTH + "-block beam deals " + (int) DAMAGE
                    + " to every enemy in its lane.", Style.EMPTY),
                FormattedCharSequence.forward("The charge is visible - nimble enemies can dodge it.", Style.EMPTY.withItalic(true))
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
        Vec3 from = self.position().add(0, 1.5, 0);
        Vec3 aim = Vec3.atCenterOf(targetBp).subtract(from);
        Vec3 flat = new Vec3(aim.x, 0, aim.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 to = from.add(dir.scale(LENGTH));
        // the telegraph: sparks along the lane and a rising hum
        for (int i = 1; i <= LENGTH; i += 2) {
            Vec3 at = from.add(dir.scale(i));
            sl.sendParticles(ParticleTypes.WAX_ON, at.x, at.y - 1.2, at.z, 2, 0.6, 0.05, 0.6, 0);
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 1.2f);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        String owner = unitUsing.getOwnerName();
        sl.getServer().tell(new TickTask(sl.getServer().getTickCount() + CHARGE_TICKS, () -> fire(sl, self, owner, from, to, dir)));
    }

    /** The beam itself. Public for the game test. */
    public static int fire(ServerLevel sl, LivingEntity self, String owner, Vec3 from, Vec3 to, Vec3 dir) {
        if (!self.isAlive())
            return 0;   // a colossus killed mid-charge never fires
        int hit = 0;
        for (LivingEntity le : CommanderDGun.hits(self, owner, from, to, HALF_WIDTH)) {
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            hit++;
        }
        for (int i = 1; i <= LENGTH * 2; i++) {
            Vec3 at = from.add(dir.scale(i * 0.5));
            sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.01);
            if (i % 3 == 0)
                sl.sendParticles(ParticleTypes.FLAME, at.x, at.y - 1, at.z, 2, 0.8, 0.1, 0.8, 0.01);
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4f, 0.7f);
        return hit;
    }
}
