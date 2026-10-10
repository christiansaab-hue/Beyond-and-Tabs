package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Gravebound (Wraith): <b>Crypt Tide</b>. Aim at the ground within {@link #RANGE} blocks: a ring of soul-light
 * marks the {@link #RADIUS}-block circle for {@link #TELEGRAPH_TICKS} ticks (BAR rule: show the danger before it
 * lands, so the enemy can step out), then bone hands - vanilla evoker fangs, visual only - erupt around and under
 * every enemy inside. Each enemy caught is rooted for {@link #ROOT_TICKS} ticks (slowness so heavy it cannot walk)
 * and takes {@link #DAMAGE}. Friends inside are left alone. {@link #CD_SECONDS} s cooldown: it sets up a fight
 * (pins a column for your melee to catch), it doesn't win one alone.
 */
public class CryptTide extends Ability {

    public static final int CD_SECONDS = 40;
    public static final int RANGE = 14;
    public static final float RADIUS = 5f;
    public static final float DAMAGE = 8f;
    public static final int TELEGRAPH_TICKS = 20;
    public static final int ROOT_TICKS = 40;
    /** Slowness VII: -105% speed, i.e. rooted in place (the brief asks for "big slowness"). */
    public static final int ROOT_AMPLIFIER = 6;
    /** Fangs with this tag only look the part - EvokerFangsMixin skips their damage so Crypt Tide's own applies once. */
    public static final String VISUAL_FANG_TAG = "bt_visual_fang";
    static final int RING_FANGS = 16;
    static final int MAX_FANGS_UNDER_ENEMIES = 12;   // caps the entity spawns when a blob of 40 zerglings gets caught

    public CryptTide() {
        super(UnitAction.CRYPT_TIDE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.showRadiusCircle = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Crypt Tide",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bone.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.CRYPT_TIDE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.CRYPT_TIDE),
            null,
            List.of(
                FormattedCharSequence.forward("Crypt Tide  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("After a 1 s soul-ring warning, bone hands erupt in a " + (int) RADIUS
                    + "-block circle: enemies are rooted for " + ROOT_TICKS / 20 + " s and take " + (int) DAMAGE + ".", Style.EMPTY),
                FormattedCharSequence.forward("The ring is visible - quick enemies can step out.", Style.EMPTY.withItalic(true))
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
        BlockPos limited = MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), targetBp, RANGE);
        int gy = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, limited.getX(), limited.getZ());
        Vec3 centre = new Vec3(limited.getX() + 0.5, gy, limited.getZ() + 0.5);
        String owner = unitUsing.getOwnerName();

        // the telegraph: the ring now and again halfway through, so it reads as a pulse rather than a puff
        telegraph(sl, centre);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 3f, 0.6f);
        int now = sl.getServer().getTickCount();
        sl.getServer().tell(new TickTask(now + TELEGRAPH_TICKS / 2, () -> telegraph(sl, centre)));
        sl.getServer().tell(new TickTask(now + TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // a wraith killed mid-cast never finishes the spell
                erupt(sl, self, owner, centre);
        }));

        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 c) {
        for (int i = 0; i < 28; i++) {
            double a = i * Mth.TWO_PI / 28;
            sl.sendParticles(ParticleTypes.SOUL, c.x + Math.cos(a) * RADIUS, c.y + 0.15, c.z + Math.sin(a) * RADIUS,
                1, 0.05, 0.02, 0.05, 0.01);
        }
        sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 0.2, c.z, 6, RADIUS * 0.4, 0.05, RADIUS * 0.4, 0.01);
    }

    /**
     * The eruption: roots and hurts every hostile unit within {@link #RADIUS} of the centre (flat distance, with a
     * height window), spawns the bone hands, and returns how many enemies were caught. Public for the game test.
     */
    public static int erupt(ServerLevel sl, LivingEntity self, String owner, Vec3 centre) {
        int hit = 0, fangsUnder = 0;
        double r2 = RADIUS * RADIUS;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            String o = u.getOwnerName();
            if (owner.equals(o) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            if (dx * dx + dz * dz > r2 || Math.abs(le.getY() - centre.y) > 4)
                continue;
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, ROOT_AMPLIFIER));
            hit++;
            if (fangsUnder++ < MAX_FANGS_UNDER_ENEMIES)
                spawnFang(sl, self, le.getX(), le.getY(), le.getZ(), 0);
        }
        for (int i = 0; i < RING_FANGS; i++) {
            double a = i * Mth.TWO_PI / RING_FANGS;
            double x = centre.x + Math.cos(a) * RADIUS, z = centre.z + Math.sin(a) * RADIUS;
            spawnFang(sl, self, x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)), z, i % 4);
        }
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x, centre.y + 0.3, centre.z, 20, RADIUS * 0.5, 0.2, RADIUS * 0.5, 0.02);
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, centre, (float) RADIUS);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 2f, 0.5f);
        return hit;
    }

    static void spawnFang(ServerLevel sl, LivingEntity owner, double x, double y, double z, int warmup) {
        EvokerFangs fang = new EvokerFangs(sl, x, y, z, (float) (sl.random.nextFloat() * Mth.TWO_PI), warmup, owner);
        fang.addTag(VISUAL_FANG_TAG);
        sl.addFreshEntity(fang);
    }
}
