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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sunforged Kingdom signature (design-factions.md): <b>Formation</b>. A Sunforged ranged unit standing within
 * {@link #RADIUS} blocks of at least {@link #MIN_GUARDS} of its owner's Sunforged melee fighters (the commander
 * counts) is "in formation": its projectiles hit for +{@link #BONUS}. Ranged behind a line of halberds and
 * paladins is the Kingdom's whole game; caught alone in the open, its crossbows are ordinary.
 *
 * The full design also gives formations +4 range around a banner-bearer; that waits for the banner-bearer unit.
 * Cost: recomputed once a second over Sunforged fighters only; the hurt hook is a set lookup.
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
        if (ranged.isEmpty() || guards.size() < MIN_GUARDS)
            return;
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
    }

    /** Runs late so it multiplies whatever the unit's own rules settled on. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent evt) {
        var src = evt.getSource();
        if (src.getEntity() == null || src.getDirectEntity() == null || src.getDirectEntity() == src.getEntity())
            return;   // only projectiles: the shooter is the cause, the arrow/bolt is the direct entity
        if (inFormation.contains(src.getEntity().getId()))
            evt.setAmount(evt.getAmount() * (1 + BONUS));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        inFormation.clear();
    }
}
