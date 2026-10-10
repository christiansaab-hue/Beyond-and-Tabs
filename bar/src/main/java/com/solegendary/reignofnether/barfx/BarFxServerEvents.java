package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Turns generic gameplay events (projectiles spawning and landing, melee hits, deaths, explosions) into BarFx events.
 * Common code: registered for the dedicated server and for the integrated server (via the client registrar).
 * Each handler is wrapped so that an unexpected entity can never break gameplay.
 */
public class BarFxServerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || !(evt.level instanceof ServerLevel sl))
            return;
        try {
            BarFx.flush(sl);
        } catch (Exception ignored) { }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        BarFx.clearAll();
    }

    // ------------------------------------------------------------------ shots

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || evt.loadedFromDisk())
            return;
        try {
            if (!(evt.getEntity() instanceof Projectile p) || p.tickCount > 1)
                return;
            Entity owner = p.getOwner();
            if (!(owner instanceof LivingEntity shooter))
                return;
            byte kind = BarFx.kindOf(p);
            Vec3 from = p.position();
            Vec3 to = null;
            if (shooter instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive())
                to = mob.getTarget().getBoundingBox().getCenter();
            if (to == null) {
                Vec3 v = p.getDeltaMovement();
                if (v.lengthSqr() < 1e-4)
                    v = shooter.getLookAngle();
                to = from.add(v.normalize().scale(8));
            }
            BarFx.shot(evt.getLevel(), from, to, kind);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ impacts

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent evt) {
        try {
            Projectile p = evt.getProjectile();
            if (p == null || p.level().isClientSide())
                return;
            HitResult hit = evt.getRayTraceResult();
            if (hit == null || hit.getType() == HitResult.Type.MISS)
                return;
            byte where = BarFx.AT_GROUND;
            Vec3 pos = hit.getLocation();
            if (hit instanceof EntityHitResult ehr) {
                Entity target = ehr.getEntity();
                if (target instanceof ArmorStand)
                    where = BarFx.AT_BUILDING;
                else {
                    where = BarFx.AT_UNIT;
                    // land on the near surface of the target rather than its feet
                    Vec3 c = target.getBoundingBox().getCenter();
                    pos = new Vec3(c.x, Math.max(pos.y, target.getY() + target.getBbHeight() * .4), c.z);
                }
            } else if (hit instanceof BlockHitResult bhr) {
                try {
                    if (BuildingUtils.findBuilding(false, bhr.getBlockPos()) != null)
                        where = BarFx.AT_BUILDING;
                } catch (Exception ignored) { }
            }
            byte kind = BarFx.kindOf(p);
            BarFx.impact(p.level(), pos, p.getDeltaMovement(), kind, where, 1);
        } catch (Exception ignored) { }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        try {
            LivingEntity victim = evt.getEntity();
            if (victim == null || victim.level().isClientSide() || victim instanceof ArmorStand)
                return;
            DamageSource src = evt.getSource();
            if (src == null)
                return;
            Entity direct = src.getDirectEntity();
            if (!(direct instanceof LivingEntity attacker) || direct == victim)
                return;
            if (src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypeTags.IS_EXPLOSION) ||
                src.is(DamageTypes.SONIC_BOOM) || src.is(DamageTypeTags.IS_FIRE))
                return;
            Vec3 c = victim.getBoundingBox().getCenter();
            Vec3 dir = c.subtract(attacker.position());
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            if (flat.lengthSqr() > 1e-4)
                c = c.subtract(flat.normalize().scale(victim.getBbWidth() * .45));
            boolean heavy = attacker.getBbWidth() * attacker.getBbHeight() > 2.5f || evt.getAmount() >= 12;
            BarFx.impact(victim.level(), c, dir, heavy ? BarFx.K_HEAVY_MELEE : BarFx.K_MELEE, BarFx.AT_UNIT,
                    Math.min(3, .6f + evt.getAmount() / 10f));
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ deaths

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        try {
            LivingEntity e = evt.getEntity();
            if (e == null || e.level().isClientSide() || e instanceof ArmorStand || evt.isCanceled())
                return;
            byte kind = BarFx.D_FLESH;
            if (e instanceof AbstractGolem || e instanceof Blaze || e instanceof Ghast || e instanceof Vex ||
                e instanceof WitherBoss)
                kind = BarFx.D_CONSTRUCT;
            else if (e instanceof AbstractSkeleton)
                kind = BarFx.D_BONE;
            else if (e instanceof Slime)
                kind = BarFx.D_SLIME;
            if (e instanceof Creeper)
                return; // creepers report their own explosion
            // RTS units get faction-tinted debris and a blast sized by what they cost (T1 puff .. T3 blast)
            byte faction = BarFx.F_NONE;
            int tier = 0;
            if (e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u) {
                var cost = u.getCost();
                tier = BarFx.tierOf(cost == null ? 0 : cost.metal());
                var f = com.solegendary.reignofnether.faction.Factions.getFaction(u);
                if (f != null && f.equals(com.solegendary.reignofnether.faction.Factions.VILLAGERS))
                    faction = BarFx.F_SUNFORGED;
                else if (f != null && f.equals(com.solegendary.reignofnether.faction.Factions.MONSTERS))
                    faction = BarFx.F_GRAVEBOUND;
                else if (f != null && f.equals(com.solegendary.reignofnether.faction.Factions.PIGLINS))
                    faction = BarFx.F_HORDE;
            }
            BarFx.death(e.level(), e.position(), kind, e.getBbWidth(), e.getBbHeight(), faction, tier);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ explosions

    private static Field radiusField = null;
    private static boolean radiusFieldSearched = false;

    /** Explosion.radius is private; it is the only float field, so find it by type (mapping-independent). */
    static float radiusOf(Explosion exp, ExplosionEvent.Detonate evt) {
        if (!radiusFieldSearched) {
            radiusFieldSearched = true;
            try {
                for (Field f : Explosion.class.getDeclaredFields()) {
                    if (f.getType() == float.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        radiusField = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                radiusField = null;
            }
        }
        if (radiusField != null) {
            try {
                float r = radiusField.getFloat(exp);
                if (r > 0 && Float.isFinite(r))
                    return r;
            } catch (Throwable ignored) { }
        }
        // fall back: how far the blast reached into blocks
        Vec3 c = exp.getPosition();
        double max = 0;
        for (var bp : evt.getAffectedBlocks())
            max = Math.max(max, Math.sqrt(bp.distToCenterSqr(c)));
        return max > 0 ? (float) max : 3;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate evt) {
        try {
            if (evt.getLevel().isClientSide())
                return;
            Explosion exp = evt.getExplosion();
            if (exp == null)
                return;
            Entity src = exp.getExploder();
            byte kind = BarFx.K_OTHER;
            if (src instanceof Fireball)
                kind = BarFx.kindOf(src);
            else if (src instanceof PrimedTnt || src instanceof Creeper)
                kind = BarFx.K_TNT;
            else if (src != null)
                kind = BarFx.kindOf(src);
            BarFx.explosion(evt.getLevel(), exp.getPosition(), radiusOf(exp, evt), kind);
        } catch (Exception ignored) { }
    }
}
