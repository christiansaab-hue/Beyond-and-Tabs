package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Sunforged Kingdom (Royal Architect): <b>Holy Bell</b>. The architect rings a consecrated bell: every enemy unit
 * within {@link #RADIUS} blocks glows for {@link #REVEAL_TICKS} ticks, and the fog of war lifts around each of
 * them for the caster for the same time (the same server-side reveal RoN uses for archers firing out of the fog).
 * BAR's radar pulse, fantasy-first: it answers stealth and flanks around the base, it deals no damage.
 * {@link #CD_SECONDS} s cooldown.
 */
public class HolyBell extends Ability {

    public static final int CD_SECONDS = 40;
    public static final float RADIUS = 24f;
    public static final int REVEAL_TICKS = 6 * 20;

    public HolyBell() {
        // one click, one bell: a box-selected group of architects must not burn every cooldown on the same pulse
        super(UnitAction.HOLY_BELL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Holy Bell",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bell.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.HOLY_BELL),
            null,
            List.of(
                FormattedCharSequence.forward("Holy Bell  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Reveals every enemy within " + (int) RADIUS + " blocks for "
                    + REVEAL_TICKS / 20 + " s: they glow and the fog lifts around them.", Style.EMPTY),
                FormattedCharSequence.forward("Nothing hides from the bell.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        ring(sl, self, unitUsing.getOwnerName());
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Rings the bell: marks and reveals every hostile unit in range and returns them. Public for the game test. */
    public static List<LivingEntity> ring(ServerLevel sl, LivingEntity self, String owner) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = RADIUS * RADIUS;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl || le.distanceToSqr(self) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;   // neutral creeps aren't hiding from anyone
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, REVEAL_TICKS, 0, false, false));
            // lifts the caster's fog around the enemy (capped sight radius, expires by itself)
            FogOfWarServerEvents.revealRangedUnit(le.getId(), owner, REVEAL_TICKS);
            sl.sendParticles(ParticleTypes.END_ROD, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            out.add(le);
        }
        // the toll: a bright ring at the edge of the pulse so both sides can see how far it reached
        for (int i = 0; i < 40; i++) {
            double a = i * Mth.TWO_PI / 40;
            sl.sendParticles(ParticleTypes.END_ROD, self.getX() + Math.cos(a) * RADIUS, self.getY() + 0.5,
                self.getZ() + Math.sin(a) * RADIUS, 1, 0, 0.05, 0, 0.0);
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 4f, 0.8f);
        sl.playSound(null, self.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 3f, 1f);
        return out;
    }
}
