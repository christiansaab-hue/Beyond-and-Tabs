package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

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
 * Tidewrought Tide Priest: <b>Raise Tidepool</b> (design/tidewrought_plan.md 3.6 - the faction's core mechanic). Aim a
 * spot within {@link #RANGE} blocks: a disc of shallow tidepool (radius {@link #RADIUS}, 13 cells) rises there for
 * {@link TidepoolServerEvents#DEFAULT_LIFETIME_TICKS} ticks, on which Tidewrought units are faster and regenerate
 * (TidesServerEvents). Costs {@link #ENERGY_COST} energy, {@link #CD_SECONDS} s cooldown.
 * <p>
 * All the rules live in the registry ({@link TidepoolServerEvents#raise}): open ground only, never inside a building or
 * on a metal patch, {@link TidepoolServerEvents#POOLS_PER_PLAYER} pools per player (a fifth evicts the oldest) and a
 * per-level cell budget. A refused raise costs nothing and starts no cooldown; the owner sees why on the HUD.
 */
public class RaiseTidepool extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 10;
    public static final int RADIUS = 2;
    public static final int ENERGY_COST = 40;

    public RaiseTidepool() {
        super(UnitAction.RAISE_TIDEPOOL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Raise Tidepool",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/nautilus_shell.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.RAISE_TIDEPOOL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.RAISE_TIDEPOOL),
            null,
            List.of(
                FormattedCharSequence.forward("Raise Tidepool  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Shallow water rises on open ground up to " + RANGE + " blocks away for "
                    + TidepoolServerEvents.DEFAULT_LIFETIME_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Tidewrought on it are faster and heal. Enemies can drain it.", Style.EMPTY),
                FormattedCharSequence.forward("They bring the sea with them.", Style.EMPTY.withItalic(true))
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
        if (cast(sl, unitUsing, at) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Raises the pool round {@code centre} (the clicked ground block) for the priest's owner, paying the energy.
     * Returns the pool, or null when it was refused (no energy, no open ground, over the budget) - nothing is paid
     * then. Public for the bot and the game test (no cooldown handling here).
     */
    public static TidepoolServerEvents.Pool cast(ServerLevel sl, Unit priest, BlockPos centre) {
        String owner = priest.getOwnerName();
        if (owner == null || owner.isBlank() || !(priest instanceof LivingEntity self))
            return null;
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null || pool.getEnergy() < ENERGY_COST) {
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, false, true, true);
            return null;
        }
        // the cells sit in the first air block above the floor; aim one up so a click on the ground itself counts
        TidepoolServerEvents.Pool p = TidepoolServerEvents.raise(sl, owner, centre.above(), RADIUS,
            TidepoolServerEvents.DEFAULT_LIFETIME_TICKS);
        if (p == null) {
            var player = sl.getServer().getPlayerList().getPlayerByName(owner);
            if (player != null)
                HudClientboundPacket.showTempMessageI18n(player, TidepoolServerEvents.lastRefusal());
            return null;
        }
        pool.addEnergy(-ENERGY_COST);
        // the moment reads from the RTS camera: a conch call at the priest, a splash over the new pool
        sl.playSound(null, self.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.NEUTRAL, 2f, 1.2f);
        sl.playSound(null, p.centre, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 2f, 0.8f);
        sl.sendParticles(ParticleTypes.SPLASH, p.centre.getX() + 0.5, p.centre.getY() + 0.3, p.centre.getZ() + 0.5,
            30, RADIUS * 0.6, 0.1, RADIUS * 0.6, 0.2);
        sl.sendParticles(ParticleTypes.NAUTILUS, self.getX(), self.getY() + 1.6, self.getZ(), 6, 0.3, 0.3, 0.3, 0.5);
        return p;
    }
}
