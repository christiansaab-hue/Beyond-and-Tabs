package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The commander's D-gun (BAR's signature commander weapon), one per faction with the same rules:
 * aim at a spot, and everything hostile along a {@link #RANGE}-block line from the commander toward it takes
 * {@link #DAMAGE} - enough to delete any T1 unit. Enemy commanders only take {@link #COMMANDER_DAMAGE}, so
 * commander duels stay a fight, not a coin flip. Costs {@link #ENERGY_COST} energy per shot (refused, with the
 * usual "not enough" warning, when the pool can't pay) and has a {@link #CD_SECONDS} s cooldown.
 * <ul>
 *   <li>Sunforged Kingdom - <b>Sunfire Decree</b>: a lance of sunlight.</li>
 *   <li>Ironhide Horde - <b>Bloodstorm</b>: a hurled greataxe that cleaves the line.</li>
 *   <li>Gravebound - <b>Soulreaper</b>: a sweep of soul-fire.</li>
 * </ul>
 * Friendly units are never hit (BAR's D-gun does hit friends - kept off here until lovish says otherwise).
 */
public class CommanderDGun extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 14;
    public static final float HALF_WIDTH = 1.5f;
    public static final float DAMAGE = 80f;
    public static final float COMMANDER_DAMAGE = 25f;
    public static final int ENERGY_COST = 150;

    public CommanderDGun() {
        super(UnitAction.COMMANDER_DGUN, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    enum Kind { SUNFIRE, BLOODSTORM, SOULREAPER }

    static Kind kindFor(Unit unit) {
        Faction f = Factions.getFaction(unit);
        if (f != null && f.equals(Factions.PIGLINS))
            return Kind.BLOODSTORM;
        if (f != null && f.equals(Factions.MONSTERS))
            return Kind.SOULREAPER;
        return Kind.SUNFIRE;
    }

    static String titleFor(Kind kind) {
        return switch (kind) {
            case BLOODSTORM -> "Bloodstorm";
            case SOULREAPER -> "Soulreaper";
            default -> "Sunfire Decree";
        };
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        Kind kind = kindFor(unit);
        String title = titleFor(kind);
        String flavour = switch (kind) {
            case BLOODSTORM -> "The Warlord's greataxe, thrown through the line.";
            case SOULREAPER -> "The Lich Regent's scythe of soul-fire.";
            default -> "The Lord Marshal's lance of sunlight.";
        };
        ResourceLocation icon = switch (kind) {
            case BLOODSTORM -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_axe.png");
            case SOULREAPER -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/netherite_hoe.png");
            default -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/blaze_rod.png");
        };
        return new AbilityButton(title, icon, hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.COMMANDER_DGUN,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.COMMANDER_DGUN),
            null,
            List.of(
                FormattedCharSequence.forward(title + "  (commander D-gun)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown, " + RANGE
                    + "-block line. Destroys light units outright.", Style.EMPTY),
                FormattedCharSequence.forward(flavour, Style.EMPTY.withItalic(true))
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
        String owner = unitUsing.getOwnerName();
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null || pool.getEnergy() < ENERGY_COST) {
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, false, true, true);
            return;
        }
        pool.addEnergy(-ENERGY_COST);

        Vec3 from = self.position().add(0, 1, 0);
        Vec3 aim = Vec3.atCenterOf(targetBp).subtract(from);
        Vec3 flat = new Vec3(aim.x, 0, aim.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 to = from.add(dir.scale(RANGE));

        for (LivingEntity le : hits(self, owner, from, to))
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self),
                CommanderServerEvents.isCommander(le) ? COMMANDER_DAMAGE : DAMAGE);

        Kind kind = kindFor(unitUsing);
        ParticleOptions p = switch (kind) {
            case BLOODSTORM -> ParticleTypes.SWEEP_ATTACK;
            case SOULREAPER -> ParticleTypes.SOUL_FIRE_FLAME;
            default -> ParticleTypes.END_ROD;
        };
        for (int i = 1; i <= RANGE * 2; i++) {
            Vec3 at = from.add(dir.scale(i * 0.5));
            sl.sendParticles(p, at.x, at.y, at.z, kind == Kind.BLOODSTORM && i % 3 != 0 ? 0 : 2, 0.15, 0.15, 0.15, 0.01);
            if (kind == Kind.SUNFIRE && i % 2 == 0)
                sl.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        switch (kind) {
            case BLOODSTORM -> sl.playSound(null, self.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.6f);
            case SOULREAPER -> sl.playSound(null, self.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 3f, 0.7f);
            default -> sl.playSound(null, self.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 1.4f);
        }
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Every hostile unit within {@link #HALF_WIDTH} blocks of the segment from-to. Public for the game test. */
    public static List<LivingEntity> hits(LivingEntity self, String owner, Vec3 from, Vec3 to) {
        return hits(self, owner, from, to, HALF_WIDTH);
    }

    /** Same, with a custom half-width (the Sun Colossus' Solar Lance is wider). */
    public static List<LivingEntity> hits(LivingEntity self, String owner, Vec3 from, Vec3 to, float halfWidth) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSqr();
        // candidates from the grid cells under the line's bounding box (padded for wide units), not every unit
        List<LivingEntity> candidates = UnitServerEvents.getAllUnits();
        if (self.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            double pad = halfWidth + 2;
            candidates = com.solegendary.reignofnether.unit.UnitGrid.inBox(sl, Math.min(from.x, to.x) - pad,
                Math.min(from.z, to.z) - pad, Math.max(from.x, to.x) + pad, Math.max(from.z, to.z) + pad, new ArrayList<>());
        }
        for (LivingEntity le : candidates) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != self.level())
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            // measured flat (the line hugs the ground over hills), with a generous height window
            if (Math.abs(le.getY() + le.getBbHeight() / 2 - from.y) > 4)
                continue;
            Vec3 c = new Vec3(le.getX(), from.y, le.getZ());
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, c.subtract(from).dot(seg) / len2));
            double reach = halfWidth + le.getBbWidth() / 2;
            if (c.distanceToSqr(from.add(seg.scale(t))) <= reach * reach)
                out.add(le);
        }
        return out;
    }
}
