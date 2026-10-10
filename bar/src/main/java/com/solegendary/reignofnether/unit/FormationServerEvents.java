package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sunforged Kingdom signature (design-factions.md): <b>Formation</b>. A Sunforged ranged unit standing within
 * {@link #RADIUS} blocks of at least {@link #MIN_GUARDS} of its owner's Sunforged melee fighters (the commander
 * counts) is "in formation": its attacks hit for +{@link #BONUS} (a modifier on RoN's attack attribute). Ranged behind a line of halberds and
 * paladins is the Kingdom's whole game; caught alone in the open, its crossbows are ordinary.
 *
 * The full design also gives formations +4 range around a banner-bearer; that waits for the banner-bearer unit.
 * Cost: recomputed once a second over Sunforged fighters only; modifiers change only when the state flips.
 */
public class FormationServerEvents {

    public static final double RADIUS = 6.0;
    public static final int MIN_GUARDS = 2;
    public static final float BONUS = 0.25f;

    private static final Set<Integer> inFormation = new HashSet<>();

    public static boolean isInFormation(LivingEntity le) {
        return inFormation.contains(le.getId());
    }

    static boolean isKingdom(Unit u) {
        Faction f = Factions.getFaction(u);
        return f != null && f.equals(Factions.VILLAGERS);
    }

    static boolean isGuard(LivingEntity le) {
        return le instanceof AttackerUnit && !(le instanceof RangedAttackerUnit) && !(le instanceof WorkerUnit)
            || com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % 20 != 0)
            return;
        update(level);
    }

    /** Recomputes who is in formation. Public for the game test. */
    public static void update(ServerLevel level) {
        Set<Integer> was = new HashSet<>(inFormation);
        inFormation.clear();
        List<LivingEntity> ranged = new ArrayList<>(), guards = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le.level() != level || !le.isAlive() || !(le instanceof Unit u) || !isKingdom(u))
                continue;
            if (le instanceof RangedAttackerUnit && !(le instanceof WorkerUnit))
                ranged.add(le);
            else if (isGuard(le))
                guards.add(le);
        }
        double r2 = RADIUS * RADIUS;
        for (LivingEntity shooter : ranged) {
            String owner = ((Unit) shooter).getOwnerName();
            int n = 0;
            for (LivingEntity g : guards) {
                if (owner.equals(((Unit) g).getOwnerName()) && g.distanceToSqr(shooter) <= r2 && ++n >= MIN_GUARDS)
                    break;
            }
            if (n >= MIN_GUARDS) {
                inFormation.add(shooter.getId());
                // a quiet glint over the shooter so the player can see the formation is live
                level.sendParticles(ParticleTypes.WAX_OFF, shooter.getX(), shooter.getY() + shooter.getBbHeight() + 0.3,
                    shooter.getZ(), 1, 0.1, 0.05, 0.1, 0);
            }
        }
        // the bonus rides on RoN's own attack attribute, which is what unit projectiles deal (LivingEntityMixin)
        for (LivingEntity shooter : ranged) {
            boolean on = inFormation.contains(shooter.getId());
            if (on != was.contains(shooter.getId()))
                setBonus(shooter, on);
        }
        for (Integer id : was)
            if (!inFormation.contains(id) && level.getEntity(id) instanceof LivingEntity gone)
                setBonus(gone, false);
    }

    static final java.util.UUID MOD = java.util.UUID.fromString("d4e2a3f5-6b7c-4d8e-9fa0-1b2c3d4e5f01");

    static void setBonus(LivingEntity le, boolean on) {
        var attr = le.getAttribute(com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get());
        if (attr == null)
            return;
        if (attr.getModifier(MOD) != null)
            attr.removeModifier(MOD);
        if (on)
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(MOD,
                "bt_formation", BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        inFormation.clear();
    }
}
