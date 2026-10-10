package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * War Mammoth: <b>Trample</b> (design-factions.md). The titan lunges forward: every hostile unit in a 10-block,
 * 3-wide lane ahead of it takes {@link #DAMAGE} and is flung aside, and the mammoth gets a short burst of speed.
 * 60 s cooldown, so it opens a fight rather than winning it.
 */
public class Trample extends Ability {

    public static final int CD_SECONDS = 60;
    public static final double LENGTH = 10, HALF_WIDTH = 2.5;
    public static final float DAMAGE = 20f;

    public Trample() {
        super(UnitAction.TRAMPLE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Trample",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/leather.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.TRAMPLE),
            null,
            List.of(
                FormattedCharSequence.forward("Trample  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Lunges forward: enemies in a " + (int) LENGTH + "-block lane ahead take "
                    + (int) DAMAGE + " damage and are flung aside.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        String owner = unitUsing.getOwnerName();
        Vec3 fwd = Vec3.directionFromRotation(0, self.getYRot()).normalize();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 origin = self.position();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != level)
                continue;
            String o = u.getOwnerName();
            if (owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            Vec3 rel = le.position().subtract(origin);
            double along = rel.dot(fwd), across = rel.dot(right);
            if (along < 0 || along > LENGTH || Math.abs(across) > HALF_WIDTH || Math.abs(rel.y) > 4)
                continue;
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            Vec3 push = right.scale(across >= 0 ? 1 : -1).add(fwd.scale(0.5)).normalize().scale(1.4);
            le.push(push.x, 0.45, push.z);
            le.hurtMarked = true;
        }
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2));
        sl.playSound(null, self.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.6f);
        for (int i = 1; i <= LENGTH; i++) {
            Vec3 at = origin.add(fwd.scale(i));
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.2, at.z, 2, 1.0, 0.1, 1.0, 0.01);
        }
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }
}
