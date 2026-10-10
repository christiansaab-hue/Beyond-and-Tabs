package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Bone Dragon (design-factions.md): units it kills rise as skeletons under its owner for {@link #LIFETIME_TICKS},
 * then crumble. Risen skeletons leave no wreck (no free metal), need population room, and at most
 * {@link #MAX_RISEN} stand at once per owner so a dragon over a big fight can't snowball.
 */
public class DragonRaiseServerEvents {

    public static final String TAG = "bt_risen";
    static final String KEY_EXPIRE = "bt_risen_expire";
    public static final int LIFETIME_TICKS = 20 * 20;
    public static final int MAX_RISEN = 8;

    private static final List<Entity> risen = new ArrayList<>();

    public static boolean isRisen(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(victim instanceof Unit v) || isRisen(victim))
            return;
        if (!(evt.getSource().getEntity() instanceof BoneDragonUnit dragon) || !dragon.isAlive())
            return;
        String owner = dragon.getOwnerName();
        if (owner == null || owner.equals(v.getOwnerName()) || AlliancesServerEvents.isAllied(owner, v.getOwnerName()))
            return;
        raise(level, owner, victim.blockPosition());
    }

    /** Raises one skeleton for {@code owner} at {@code at} if the limits allow. Public for the game test. */
    public static Entity raise(ServerLevel level, String owner, BlockPos at) {
        risen.removeIf(Entity::isRemoved);
        long mine = risen.stream().filter(e -> e instanceof Unit u && owner.equals(u.getOwnerName())).count();
        if (mine >= MAX_RISEN)
            return null;
        if (UnitServerEvents.getCurrentPopulation(owner) + 1 > BuildingServerEvents.getTotalPopulationSupply(owner))
            return null;
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.SKELETON_UNIT.get(), level, at.below(), owner);
        if (e == null)
            return null;
        e.addTag(TAG);
        e.getPersistentData().putLong(KEY_EXPIRE, level.getGameTime() + LIFETIME_TICKS);
        risen.add(e);
        level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        return e;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % 20 != 0 || risen.isEmpty())
            return;
        long now = level.getGameTime();
        for (Entity e : new ArrayList<>(risen)) {
            if (e.isRemoved()) {
                risen.remove(e);
            } else if (now >= e.getPersistentData().getLong(KEY_EXPIRE)) {
                level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + 0.8, e.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
                e.discard();   // crumbles: no death event, so no wreck and no kill credit
                risen.remove(e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        risen.clear();
    }
}
