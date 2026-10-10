package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Each faction's Commander signature ability (design doc: claude/design-factions.md). One class, one face per faction:
 * <ul>
 *   <li><b>Sunforged Kingdom - Rally Standard</b>: allies within 12 blocks gain Resistance I and Speed I for 20 s.</li>
 *   <li><b>Ironhide Horde - War Horn</b>: allies within 16 blocks gain Speed II and Strength I for 10 s.</li>
 *   <li><b>Gravebound - Dread</b>: enemies within 12 blocks are Weakened and Slowed for 8 s.</li>
 *   <li><b>Verdant Court - Wildstride</b>: allies within 14 blocks gain Speed II and Jump Boost for 12 s - the
 *       Court wins by being somewhere else first.</li>
 *   <li><b>Tidewrought - Riptide</b>: a {@link #RIPTIDE_RANGE}-block cone of surf ({@link #RIPTIDE_HALF_ANGLE_DEG}
 *       degrees either side) hits enemies for {@link #RIPTIDE_DAMAGE} and throws them back
 *       ({@link #RIPTIDE_KNOCKBACK}); an enemy standing in water or on a tidepool is thrown twice as hard. Aimed at
 *       the spot given (the bot passes the nearest foe), else at the commander's target, else at the nearest enemy in
 *       reach, else straight ahead - so the instant button always faces the fight.</li>
 * </ul>
 * Instant cast, no resource cost, 45 s cooldown. Commanders get it in {@code CommanderAbilities}.
 */
public class CommanderAbility extends Ability {

    public static final int CD_SECONDS = 45;

    public CommanderAbility() {
        super(UnitAction.COMMANDER_ABILITY, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
    }

    /**
     * NONE: the faction has no signature designed yet (FactionTraits) - CommanderServerEvents does not grant the
     * ability, and should one exist anyway its button is hidden and it does nothing (it used to be Rally Standard).
     */
    public enum Kind { RALLY, WAR_HORN, DREAD, WILDSTRIDE, RIPTIDE, NONE }

    /** Riptide (Tidewrought): reach and half-angle of the cone, its damage and knockback (doubled on wet targets). */
    public static final float RIPTIDE_RANGE = 7f;
    public static final float RIPTIDE_HALF_ANGLE_DEG = 40f;
    public static final float RIPTIDE_DAMAGE = 4f;
    public static final double RIPTIDE_KNOCKBACK = 1.4;
    public static final double RIPTIDE_WET_MULTIPLIER = 2.0;

    public static Kind kindFor(Unit unit) {
        return FactionTraits.of(Factions.getFaction(unit)).commanderAbility;
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
            case WILDSTRIDE -> {
                title = "Wildstride";
                line1 = "Allies within 14 blocks: Speed II and Jump Boost for 12 s.";
                line2 = "The forest moves, and the Court moves with it.";
                icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/azalea_top.png");
            }
            case RIPTIDE -> {
                title = "Riptide";
                line1 = "A " + (int) RIPTIDE_RANGE + "-block cone of surf: " + (int) RIPTIDE_DAMAGE
                    + " damage and a hard shove, twice as hard on foes in water or tidepools.";
                line2 = "The sea goes where the Admiral points.";
                icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/trident.png");
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
            () -> kind == Kind.NONE,
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
        if (kind == Kind.NONE)
            return;
        if (kind == Kind.RIPTIDE) {
            riptide(sl, self, owner, targetBp);
            this.setToMaxCooldown(unitUsing);
            com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
            return;
        }
        double radius = kind == Kind.WAR_HORN ? 16 : kind == Kind.WILDSTRIDE ? 14 : 12;
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
                case WILDSTRIDE -> {
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 1));
                    le.addEffect(new MobEffectInstance(MobEffects.JUMP, 240, 0));   // I, not II: higher hops upset pathing
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
            case WILDSTRIDE -> sl.playSound(null, self.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 3f, 0.7f);
            default -> sl.playSound(null, self.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2f, 0.9f);
        }
        var particle = switch (kind) {
            case WAR_HORN -> ParticleTypes.ANGRY_VILLAGER;
            case DREAD -> ParticleTypes.SOUL;
            case WILDSTRIDE -> ParticleTypes.HAPPY_VILLAGER;
            default -> ParticleTypes.END_ROD;
        };
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            sl.sendParticles(particle, self.getX() + Math.cos(a) * radius * 0.6, self.getY() + 1,
                self.getZ() + Math.sin(a) * radius * 0.6, 1, 0, 0.1, 0, 0);
        }
        this.setToMaxCooldown(unitUsing);
    }

    /**
     * Riptide: picks its facing (see the class comment), then hurts and throws back every enemy unit in the cone.
     * Returns the units it hit. Public for the game test and the bot.
     */
    public static List<LivingEntity> riptide(ServerLevel sl, LivingEntity self, String owner, BlockPos aim) {
        Vec3 dir = riptideFacing(sl, self, owner, aim);
        List<LivingEntity> hit = new ArrayList<>();
        double cosHalf = Math.cos(Math.toRadians(RIPTIDE_HALF_ANGLE_DEG));
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), RIPTIDE_RANGE + 2, new ArrayList<>())) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl || !isEnemy(owner, u))
                continue;
            if (Math.abs(le.getY() - self.getY()) > 4)
                continue;
            double dx = le.getX() - self.getX(), dz = le.getZ() - self.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist - le.getBbWidth() / 2 > RIPTIDE_RANGE)
                continue;
            if (dist > 1.0 && (dx * dir.x + dz * dir.z) / dist < cosHalf)
                continue;
            hit.add(le);
        }
        for (LivingEntity le : hit) {
            // wet is read before the hit: the shove must not depend on where the damage knockback put it
            boolean wet = com.solegendary.reignofnether.tide.TidesServerEvents.isWet(le);
            le.hurt(sl.damageSources().indirectMagic(self, self), RIPTIDE_DAMAGE);
            if (!le.isAlive())
                continue;
            double dx = le.getX() - self.getX(), dz = le.getZ() - self.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            // pushed away from the Admiral (or along the cone for a unit standing on top of it)
            double px = len > 0.3 ? dx / len : dir.x, pz = len > 0.3 ? dz / len : dir.z;
            // LivingEntity.knockback pushes against (x, z) and respects knockback resistance (treants, golems)
            le.knockback(RIPTIDE_KNOCKBACK * (wet ? RIPTIDE_WET_MULTIPLIER : 1.0), -px, -pz);
            le.hurtMarked = true;
        }
        // the look: a fan of splashes and bubbles along the cone, and the roar of a breaking wave
        double half = Math.toRadians(RIPTIDE_HALF_ANGLE_DEG);
        for (int ray = -2; ray <= 2; ray++) {
            double a = half * ray / 2.0, cos = Math.cos(a), sin = Math.sin(a);
            double rx = dir.x * cos - dir.z * sin, rz = dir.x * sin + dir.z * cos;
            for (int i = 1; i <= (int) RIPTIDE_RANGE; i += 2)
                sl.sendParticles(ParticleTypes.SPLASH, self.getX() + rx * i, self.getY() + 0.6, self.getZ() + rz * i,
                    4, 0.25, 0.2, 0.25, 0.1);
        }
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, self.getX() + dir.x * 2, self.getY() + 0.5, self.getZ() + dir.z * 2,
            10, 0.6, 0.3, 0.6, 0.05);
        sl.playSound(null, self.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.HOSTILE, 2.5f, 0.9f);
        sl.playSound(null, self.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.5f, 0.7f);
        return hit;
    }

    static boolean isEnemy(String owner, Unit u) {
        String o = u.getOwnerName();
        return o != null && !o.equals(owner) && !AlliancesServerEvents.isAllied(owner, o);
    }

    /** The flat unit direction Riptide faces: the aimed spot, else the commander's target, else the nearest foe, else ahead. */
    static Vec3 riptideFacing(ServerLevel sl, LivingEntity self, String owner, BlockPos aim) {
        Vec3 to = null;
        if (aim != null && aim.distToCenterSqr(self.getX(), aim.getY() + 0.5, self.getZ()) > 2.25)
            to = Vec3.atCenterOf(aim);
        if (to == null && self instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null)
            to = mob.getTarget().position();
        if (to == null) {
            double best = (RIPTIDE_RANGE + 1) * (RIPTIDE_RANGE + 1);
            for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), RIPTIDE_RANGE + 1, new ArrayList<>())) {
                if (le == self || !le.isAlive() || !(le instanceof Unit u) || !isEnemy(owner, u))
                    continue;
                double d = le.distanceToSqr(self);
                if (d < best) {
                    best = d;
                    to = le.position();
                }
            }
        }
        Vec3 flat = to == null ? Vec3.ZERO : new Vec3(to.x - self.getX(), 0, to.z - self.getZ());
        if (flat.lengthSqr() < 0.01)
            flat = self.getLookAngle().multiply(1, 0, 1);
        return flat.lengthSqr() < 0.0001 ? new Vec3(1, 0, 0) : flat.normalize();
    }
}
