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
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ironhide Horde (the Warlord commander): <b>War-Drums</b>. The Warlord beats the war drums: every friendly unit
 * within {@link #RADIUS} blocks at the moment of the cast marches {@link #SPEED_BONUS}x faster and shrugs off
 * knockback for {@link #DURATION_TICKS} ticks. Snapshot, not an aura - the drummer can't be kited to drag the
 * buff around, and it costs one scan per cast instead of one per tick. Readable at a glance: a basedrum beat every
 * second, notes over every drummed unit, campfire smoke rising from the Warlord. {@link #CD_SECONDS} s cooldown.
 */
public class WarDrums extends Ability {

    public static final int CD_SECONDS = 45;
    public static final float RADIUS = 16f;
    public static final int DURATION_TICKS = 8 * 20;
    public static final double SPEED_BONUS = 0.20;
    public static final double KNOCKBACK_RESIST = 0.6;
    public static final UUID SPEED_MOD = UUID.fromString("b7d1c0de-2a5e-4c11-9f0a-7a3d0e1f5a01");
    public static final UUID KB_MOD = UUID.fromString("b7d1c0de-2a5e-4c11-9f0a-7a3d0e1f5a02");
    /** Game time the buff runs out, per unit: a later drum from an ally refreshes it, and the earlier one's expiry leaves it be. */
    static final String KEY_UNTIL = "bt_wardrums_until";

    public WarDrums() {
        super(UnitAction.WAR_DRUMS, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("War-Drums",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/note_block.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.WAR_DRUMS),
            null,
            List.of(
                FormattedCharSequence.forward("War-Drums  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Friendly units within " + (int) RADIUS + " blocks get +"
                    + (int) (SPEED_BONUS * 100) + "% move speed and knockback resistance for " + DURATION_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("The Horde marches to the beat.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        List<LivingEntity> drummed = rally(sl, self, unitUsing.getOwnerName());
        // the beat: one drum (and a puff of notes over every drummed unit) per second for the whole buff
        int now = sl.getServer().getTickCount();
        for (int beat = 1; beat < DURATION_TICKS / 20; beat++) {
            final int b = beat;
            sl.getServer().tell(new TickTask(now + beat * 20, () -> beatFx(sl, self, drummed, b)));
        }
        sl.getServer().tell(new TickTask(now + DURATION_TICKS + 1, () -> expire(sl, drummed)));
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Applies the buff to every friendly unit in range (the drummer included) and returns them. Public for the game test. */
    public static List<LivingEntity> rally(ServerLevel sl, LivingEntity self, String owner) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = RADIUS * RADIUS;
        long until = sl.getGameTime() + DURATION_TICKS;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!le.isAlive() || !(le instanceof Unit u) || le.level() != sl || le.distanceToSqr(self) > r2)
                continue;
            String o = u.getOwnerName();
            if (!owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o))
                continue;
            apply(le.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MOD, "war_drums_speed", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL);
            apply(le.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KB_MOD, "war_drums_kb", KNOCKBACK_RESIST, AttributeModifier.Operation.ADDITION);
            le.getPersistentData().putLong(KEY_UNTIL, until);
            out.add(le);
        }
        beatFx(sl, self, out, 0);
        return out;
    }

    static void apply(AttributeInstance attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        if (attr == null)
            return;
        // transient: a save mid-buff must not leave a unit permanently fast
        if (attr.getModifier(id) == null)
            attr.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    static void beatFx(ServerLevel sl, LivingEntity self, List<LivingEntity> drummed, int beat) {
        if (self.isAlive()) {
            sl.playSound(null, self.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 3f, beat % 2 == 0 ? 0.6f : 0.75f);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, self.getX(), self.getY() + self.getBbHeight(), self.getZ(),
                2, 0.3, 0.1, 0.3, 0.01);
        }
        for (LivingEntity le : drummed)
            if (le.isAlive())
                // count 0 = the x offset picks the note colour: a warm red-orange for the Horde
                sl.sendParticles(ParticleTypes.NOTE, le.getX(), le.getY() + le.getBbHeight() + 0.4, le.getZ(), 0, 0.2, 0, 0, 1);
    }

    static void expire(ServerLevel sl, List<LivingEntity> drummed) {
        long now = sl.getGameTime();
        for (LivingEntity le : drummed) {
            if (le.getPersistentData().getLong(KEY_UNTIL) > now)
                continue;   // re-drummed since: that cast's expiry clears it
            AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null)
                speed.removeModifier(SPEED_MOD);
            AttributeInstance kb = le.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null)
                kb.removeModifier(KB_MOD);
            le.getPersistentData().remove(KEY_UNTIL);
        }
    }

    /** Whether the drums' speed bonus is on this unit right now. Public for the game test. */
    public static boolean isDrummed(LivingEntity le) {
        AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(SPEED_MOD) != null;
    }
}
