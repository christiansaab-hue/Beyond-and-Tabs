package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Each faction's Commander signature ability (design doc: claude/design-factions.md). One class, three faces:
 * <ul>
 *   <li><b>Sunforged Kingdom - Rally Standard</b>: allies within 12 blocks gain Resistance I and Speed I for 20 s.</li>
 *   <li><b>Ironhide Horde - War Horn</b>: allies within 16 blocks gain Speed II and Strength I for 10 s.</li>
 *   <li><b>Gravebound - Dread</b>: enemies within 12 blocks are Weakened and Slowed for 8 s.</li>
 * </ul>
 * Instant cast, no resource cost, 45 s cooldown. Commanders get it in {@code CommanderAbilities}.
 */
public class CommanderAbility extends Ability {

    public static final int CD_SECONDS = 45;

    public CommanderAbility() {
        super(UnitAction.COMMANDER_ABILITY, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
    }

    enum Kind { RALLY, WAR_HORN, DREAD }

    static Kind kindFor(Unit unit) {
        Faction f = Factions.getFaction(unit);
        if (f != null && f.equals(Factions.PIGLINS))
            return Kind.WAR_HORN;
        if (f != null && f.equals(Factions.MONSTERS))
            return Kind.DREAD;
        return Kind.RALLY;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        Kind kind = kindFor(unit);
        String title, line1, line2;
        ResourceLocation icon;
        switch (kind) {
            case WAR_HORN -> {
                title = "War Horn";
                line1 = "Allies within 16 blocks: Speed II and Strength I for 10 s.";
                line2 = "The Horde's charge starts here.";
                icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/goat_horn.png");
            }
            case DREAD -> {
                title = "Dread";
                line1 = "Enemies within 12 blocks: Weakness and Slowness for 8 s.";
                line2 = "The dead do not fear; the living should.";
                icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/echo_shard.png");
            }
            default -> {
                title = "Rally Standard";
                line1 = "Allies within 12 blocks: Resistance I and Speed I for 20 s.";
                line2 = "Hold the line under the sun banner.";
                icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/white_banner.png");
            }
        }
        return new AbilityButton(title, icon, hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.COMMANDER_ABILITY),
            null,
            List.of(
                FormattedCharSequence.forward(title + "  (commander, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(line1, Style.EMPTY),
                FormattedCharSequence.forward(line2, Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        String owner = unitUsing.getOwnerName();
        Kind kind = kindFor(unitUsing);
        double radius = kind == Kind.WAR_HORN ? 16 : 12;
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !le.isAlive() || le.distanceToSqr(self) > radius * radius)
                continue;
            boolean friendly = owner.equals(u.getOwnerName()) || AlliancesServerEvents.isAllied(owner, u.getOwnerName());
            if (kind == Kind.DREAD ? !friendly : friendly)
                targets.add(le);
        }
        for (LivingEntity le : targets) {
            switch (kind) {
                case WAR_HORN -> {
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
                    le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
                }
                case DREAD -> {
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0));
                }
                default -> {
                    le.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
                }
            }
        }
        // the moment must read from the RTS camera: a sound everyone nearby hears and a ring of particles
        switch (kind) {
            case WAR_HORN -> sl.playSound(null, self.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1f);
            case DREAD -> sl.playSound(null, self.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2f, 0.8f);
            default -> sl.playSound(null, self.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2f, 0.9f);
        }
        var particle = switch (kind) {
            case WAR_HORN -> ParticleTypes.ANGRY_VILLAGER;
            case DREAD -> ParticleTypes.SOUL;
            default -> ParticleTypes.END_ROD;
        };
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            sl.sendParticles(particle, self.getX() + Math.cos(a) * radius * 0.6, self.getY() + 1,
                self.getZ() + Math.sin(a) * radius * 0.6, 1, 0, 0.1, 0, 0);
        }
        this.setToMaxCooldown(unitUsing);
    }
}
