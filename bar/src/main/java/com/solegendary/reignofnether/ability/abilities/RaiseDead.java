package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gravebound Lich Regent: <b>Raise Dead</b> (design-factions.md, "every wreck is an army"). Up to {@link #MAX_RAISED}
 * wrecks within {@link #RADIUS} blocks rise as Ghouls (zombie units) under the commander's owner. Never free: each
 * Ghoul costs its normal metal, paid from the wreck first and the pool for any remainder, and it still needs
 * population room. Wreck metal beyond a Ghoul's cost stays on the wreck. 30 s cooldown, instant cast.
 */
public class RaiseDead extends Ability {

    public static final int CD_SECONDS = 30;
    public static final double RADIUS = 10;
    public static final int MAX_RAISED = 3;

    public RaiseDead() {
        super(UnitAction.COMMANDER_RAISE_DEAD, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Raise Dead",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/rotten_flesh.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.COMMANDER_RAISE_DEAD),
            null,
            List.of(
                FormattedCharSequence.forward("Raise Dead  (commander, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Up to " + MAX_RAISED + " wrecks within " + (int) RADIUS
                    + " blocks rise as Ghouls. Each costs its metal - wreck first, then your pool.", Style.EMPTY),
                FormattedCharSequence.forward("Every wreck is an army.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        String owner = unitUsing.getOwnerName();
        int raised = raise(sl, self, owner);
        if (raised > 0) {
            sl.playSound(null, self.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.5f, 0.6f);
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Raises what it can and returns how many rose. Public for the game test. */
    public static int raise(ServerLevel level, LivingEntity self, String owner) {
        float cost = ResourceCosts.ZOMBIE.metal();
        int popEach = Math.max(1, ResourceCosts.ZOMBIE.population);
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        List<Entity> near = new ArrayList<>();
        for (Entity w : WreckServerEvents.getWrecks())
            if (!w.isRemoved() && w.distanceToSqr(self) <= RADIUS * RADIUS)
                near.add(w);
        near.sort(Comparator.comparingDouble(w -> w.distanceToSqr(self)));
        int raised = 0;
        for (Entity w : near) {
            if (raised >= MAX_RAISED)
                break;
            int pop = UnitServerEvents.getCurrentPopulation(owner);
            int cap = BuildingServerEvents.getTotalPopulationSupply(owner);
            if (pop + popEach > cap)
                break;
            float fromWreck = Math.min(WreckServerEvents.metalOf(w), cost);
            float fromPool = cost - fromWreck;
            if (fromPool > 0 && (pool == null || pool.getMetal() < fromPool))
                continue;
            if (fromPool > 0)
                pool.addMetal(-fromPool);
            WreckServerEvents.drain(level, w, fromWreck);
            Entity ghoul = UnitServerEvents.spawnMob(EntityRegistrar.ZOMBIE_UNIT.get(), level, w.blockPosition().below(), owner);   // spawnMob adds one block
            if (ghoul != null) {
                raised++;
                level.sendParticles(ParticleTypes.SOUL, w.getX(), w.getY() + 0.5, w.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
            }
        }
        return raised;
    }
}
