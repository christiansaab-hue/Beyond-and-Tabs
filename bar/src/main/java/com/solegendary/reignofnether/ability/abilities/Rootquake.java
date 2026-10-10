package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * World Tree Walker: <b>Rootquake</b>. The walker drives its roots into the ground: for {@link #TELEGRAPH_TICKS} ticks
 * a ring of upturned soil marks the {@link #RADIUS}-block circle round where it stood (so the enemy can step out),
 * then roots burst up inside it - every hostile unit there takes {@link #DAMAGE} and is rooted (Slowness VII, as the
 * Thornbow roots) for {@link #ROOT_TICKS} ticks. Friends are never touched. {@link #CD_SECONDS} s cooldown, and
 * telegraphed on purpose: the T3 rules forbid instant wins.
 */
public class Rootquake extends Ability {

    public static final int CD_SECONDS = 40;
    public static final int TELEGRAPH_TICKS = 20;
    public static final float RADIUS = 8f;
    public static final float DAMAGE = 20f;
    public static final int ROOT_TICKS = 3 * 20;
    public static final int ROOT_AMPLIFIER = 6;   // -105% speed: cannot walk

    public Rootquake() {
        super(UnitAction.ROOTQUAKE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Rootquake",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/rooted_dirt.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.ROOTQUAKE),
            null,
            List.of(
                FormattedCharSequence.forward("Rootquake  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("After a 1 s warning, roots burst up within " + (int) RADIUS + " blocks: enemies take "
                    + (int) DAMAGE + " damage and are rooted for " + ROOT_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("The ground cracks first - quick enemies step out.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        // the circle is fixed where the walker stands now, so the telegraph tells the truth
        Vec3 centre = self.position();
        BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        for (int i = 0; i < 32; i++) {
            double a = i * (Math.PI * 2 / 32);
            sl.sendParticles(soil, centre.x + Math.cos(a) * RADIUS, centre.y + 0.1, centre.z + Math.sin(a) * RADIUS,
                3, 0.2, 0.05, 0.2, 0.05);
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 3f, 0.5f);
        sl.playSound(null, self.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.5f, 1.4f);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        String owner = unitUsing.getOwnerName();
        sl.getServer().tell(new TickTask(sl.getServer().getTickCount() + TELEGRAPH_TICKS, () -> quake(sl, self, owner, centre)));
    }

    /**
     * The eruption: damages and roots every hostile unit within {@link #RADIUS} of {@code centre} and returns them.
     * A walker killed during the warning never erupts. Public for the game test.
     */
    public static List<LivingEntity> quake(ServerLevel sl, LivingEntity self, String owner, Vec3 centre) {
        List<LivingEntity> hit = new ArrayList<>();
        if (!self.isAlive() || owner == null || owner.isEmpty())
            return hit;
        double r2 = RADIUS * RADIUS;
        // UnitGrid hands back a shared candidate list; copy the hits out before hurting anything (a death can rebucket)
        for (LivingEntity le : UnitGrid.near(sl, centre.x, centre.z, RADIUS, new ArrayList<>())) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            if (dx * dx + dz * dz > r2 || Math.abs(le.getY() - centre.y) > 4)
                continue;
            hit.add(le);
        }
        BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.defaultBlockState());
        for (LivingEntity le : hit) {
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, ROOT_AMPLIFIER));
            sl.sendParticles(roots, le.getX(), le.getY() + 0.3, le.getZ(), 12, 0.4, 0.4, 0.4, 0.1);
        }
        // roots erupting across the circle (a handful of bursts, not one per block)
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2 / 12), d = (i % 3 + 1) * RADIUS / 3.2;
            sl.sendParticles(roots, centre.x + Math.cos(a) * d, centre.y + 0.4, centre.z + Math.sin(a) * d, 6, 0.5, 0.4, 0.5, 0.15);
        }
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.5f);
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, centre, 3f);
        return hit;
    }
}
