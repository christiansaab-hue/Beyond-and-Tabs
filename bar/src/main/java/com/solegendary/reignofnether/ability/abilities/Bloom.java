package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Verdant Court (Bloom Priestess): <b>Bloom</b>. The priestess calls the meadow into flower round her: every friendly
 * unit within {@link #RADIUS} blocks is healed {@link #HEAL} HP at once and cleansed like her pulse, under a burst of
 * cherry petals and spore blossoms. {@link #CD_SECONDS} s cooldown. A single grid query per cast (the priestess'
 * {@link BloomPriestessUnit#mend}), no lingering state.
 */
public class Bloom extends Ability {

    public static final int CD_SECONDS = 20;
    public static final float HEAL = 12f;
    public static final float RADIUS = 8f;

    public Bloom() {
        super(UnitAction.BLOOM, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Bloom",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/pink_petals.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.BLOOM),
            null,
            List.of(
                FormattedCharSequence.forward("Bloom  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Heals every friendly unit within " + (int) RADIUS + " blocks for "
                    + (int) HEAL + " HP and cleanses their slows, poison, wither and weakness.", Style.EMPTY),
                FormattedCharSequence.forward("The meadow answers her call.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof BloomPriestessUnit priestess) || !(level instanceof ServerLevel sl))
            return;
        burst(sl, priestess);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(priestess.getId(), this.action, this.cooldownMax);
    }

    /** The burst itself, without the cooldown. Returns how many friends it touched. Public for the game test. */
    public static int burst(ServerLevel sl, BloomPriestessUnit priestess) {
        int n = priestess.mend(sl, RADIUS, HEAL, true);
        LivingEntity self = priestess;
        // a ring of petals at the edge and a flurry over the priestess: readable from a zoomed-out camera
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            sl.sendParticles(ParticleTypes.CHERRY_LEAVES, self.getX() + Math.cos(a) * RADIUS * 0.8, self.getY() + 0.6,
                self.getZ() + Math.sin(a) * RADIUS * 0.8, 2, 0.4, 0.3, 0.4, 0.0);
        }
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, self.getX(), self.getY() + 1.5, self.getZ(), 30, 2.5, 1.0, 2.5, 0.0);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, self.getX(), self.getY() + 1.0, self.getZ(), 12, 1.5, 0.6, 1.5, 0.0);
        sl.playSound(null, self.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2.0f, 1.3f);
        sl.playSound(null, self.blockPosition(), SoundEvents.CHERRY_LEAVES_PLACE, SoundSource.NEUTRAL, 1.5f, 0.9f);
        return n;
    }
}
