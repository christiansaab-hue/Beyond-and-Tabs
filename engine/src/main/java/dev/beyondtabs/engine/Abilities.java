package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.AbilityDef;
import java.util.HashSet;

/**
 * TABS-style unit abilities (see design/abilities.json): charges, leaps, whirlwinds, shield bashes, dodges, war cries,
 * healing light, force pushes, shadowsteps, volleys, and on-hit effects (frost, poison, burn, chain lightning, pierce).
 * All numbers come from the sheet; the behaviour of each kind lives here.
 */
final class Abilities {
    private Abilities() { }

    static final float DT = World.DT;

    /** Status effects ticking on a unit: slow, poison/burn, buffs, stun. */
    static void effects(World w, Unit u) {
        if (u.slowFor > 0 && (u.slowFor -= DT) <= 0) u.slowMul = 1;
        if (u.buffFor > 0 && (u.buffFor -= DT) <= 0) { u.buffMul = 1; u.buffStab = 0; }
        if (u.poisonFor > 0) {
            u.poisonFor -= DT;
            w.damage(u, u.poisonDps * DT, 0, u.x, u.z, Rig.TORSO, u.poisonSrc);
        }
        if (u.abilityCd > 0) u.abilityCd -= DT;
    }

    static boolean ready(Unit u) { return u.abilityCd <= 0; }

    /**
     * Active abilities the unit decides to use this tick. Returns true while the ability is steering the unit
     * (charging, leaping, spinning), so the normal movement code stays out of the way.
     */
    static boolean act(World w, Unit u, Unit t) {
        AbilityDef a = u.ability;
        float d = t == null ? 1e9f : World.dist(u.x, u.z, t.x, t.z);
        switch (a.kind()) {
            case "charge" -> {
                if (u.charging) return charge(w, u, a);
                if (t != null && ready(u) && d > 4 && d < a.range() && !t.knocked) {
                    u.charging = true; u.abilityT = (float) a.duration(); u.abilityTarget = t; u.bowled = new HashSet<>();
                    return charge(w, u, a);
                }
            }
            case "whirlwind" -> {
                if (u.spinning) {
                    u.abilityT -= DT; u.yaw += 14 * DT;
                    if (((int) (u.abilityT / .25f)) != u.abilityState) {
                        u.abilityState = (int) (u.abilityT / .25f);
                        w.areaDamage(u.team, u.x, u.z, (float) a.radius(), (float) (u.weapon.damage() * a.power() * u.buffMul), 3, u.x, u.z, u);
                    }
                    if (t != null && d > u.radius + t.radius + .5f) { u.vx = (t.x - u.x) / d * u.speed * .6f; u.vz = (t.z - u.z) / d * u.speed * .6f; } else { u.vx *= .7f; u.vz *= .7f; }
                    u.x += u.vx * DT; u.z += u.vz * DT; u.walkAmount = .6f; u.walkPhase += DT * 6;
                    if (u.abilityT <= 0) { u.spinning = false; u.abilityCd = (float) a.cooldown(); }
                    return true;
                }
                if (ready(u) && countEnemies(w, u, u.x, u.z, (float) a.range(), false) >= 2) { u.spinning = true; u.abilityT = (float) a.duration(); u.abilityState = -1; return true; }
            }
            case "leap" -> {
                if (u.leaping) {
                    u.abilityT -= DT;
                    float k = 1 - Math.max(0, u.abilityT) / (float) a.duration();
                    u.x = u.ax0 + (u.ax1 - u.ax0) * k; u.z = u.az0 + (u.az1 - u.az0) * k; u.vx = u.vz = 0;
                    if (u.abilityT <= 0) {
                        u.leaping = false; u.abilityCd = (float) a.cooldown();
                        w.areaDamage(u.team, u.x, u.z, (float) a.radius(), (float) (u.weapon.damage() * a.power() * u.buffMul), 9, u.x, u.z, u);
                    }
                    return true;
                }
                if (t != null && ready(u) && d > 3 && d < a.range()) {
                    u.leaping = true; u.abilityT = (float) a.duration();
                    u.ax0 = u.x; u.az0 = u.z; float back = (u.radius + t.radius) / d;
                    u.ax1 = t.x - (t.x - u.x) * back; u.az1 = t.z - (t.z - u.z) * back;
                    w.face(u, t.x, t.z); u.yaw = (float) Math.atan2(t.x - u.x, t.z - u.z);
                    u.ragdoll.impulseAll((t.x - u.x) / d * 6, 55, (t.z - u.z) / d * 6, DT, 0);
                    return true;
                }
            }
            case "shield_bash" -> {
                if (t != null && ready(u) && u.attackAnim < 0 && d < a.range() + u.radius + t.radius) {
                    w.face(u, t.x, t.z);
                    w.damage(t, (float) (u.weapon.damage() * .5f * u.buffMul), (float) a.power(), u.x, u.z, Rig.TORSO, u);
                    t.stunFor = Math.max(t.stunFor, (float) a.duration());
                    u.abilityCd = (float) a.cooldown(); u.attackAnim = 0; u.hitDealt = true; u.cooldown = (float) u.weapon.cooldown() * .5f;
                }
            }
            case "shadowstep" -> {
                if (t != null && ready(u) && d > 4 && d < a.range()) {
                    float fx = (float) Math.sin(t.yaw), fz = (float) Math.cos(t.yaw), back = t.radius + u.radius + .4f;
                    u.x = t.x - fx * back; u.z = t.z - fz * back; u.vx = u.vz = 0;
                    u.yaw = (float) Math.atan2(t.x - u.x, t.z - u.z);
                    u.ragdoll.pose(u.x, w.terrain.groundY(u.x, u.z), u.z, u.yaw, 0, 0, -1); u.ragdoll.snapToPose();
                    u.nextStrikeMul = (float) a.power(); u.cooldown = 0; u.abilityCd = (float) a.cooldown();
                }
            }
            case "war_cry" -> {
                boolean song = a.id().equals("inspire");
                if (ready(u) && countEnemies(w, u, u.x, u.z, (float) a.range(), false) >= (song ? 1 : 3)) {
                    w.hash.query(u.x, u.z, (float) a.radius(), o -> {
                        if (o.team != u.team || !o.alive) return;
                        o.buffFor = Math.max(o.buffFor, (float) a.duration()); o.buffMul = Math.max(o.buffMul, (float) a.power()); o.buffStab = Math.max(o.buffStab, .5f);
                    });
                    u.abilityCd = (float) a.cooldown();
                }
            }
            case "heal_pulse" -> {
                if (ready(u) && woundedNear(w, u, (float) a.radius())) {
                    w.hash.query(u.x, u.z, (float) a.radius(), o -> { if (o.team == u.team && o.alive) o.hp = Math.min(o.maxHp, o.hp + (float) a.power()); });
                    u.abilityCd = (float) a.cooldown();
                }
            }
            case "push_wave" -> {
                if (ready(u) && countEnemies(w, u, u.x, u.z, (float) a.range(), true) >= 2) {
                    float fx = (float) Math.sin(u.yaw), fz = (float) Math.cos(u.yaw);
                    w.hash.query(u.x, u.z, (float) a.radius(), o -> {
                        if (o.team == u.team || !o.alive) return;
                        float dx = o.x - u.x, dz = o.z - u.z, l = Math.max(.01f, (float) Math.sqrt(dx * dx + dz * dz));
                        if ((dx * fx + dz * fz) / l < .3f) return;
                        w.damage(o, 15 * u.buffMul, (float) a.power(), u.x, u.z, Rig.TORSO, u);
                    });
                    u.abilityCd = (float) a.cooldown(); u.attackAnim = 0; u.hitDealt = true;
                }
            }
            case "volley" -> { if (ready(u) && t != null && u.cooldown <= 0 && d < u.range) { u.volleyShots = (int) a.power(); u.abilityCd = (float) a.cooldown(); } }
            case "brace" -> u.bracing = t != null && d < u.range + 4 && (t.cls.equals("cavalry") || t.cls.equals("large") || t.charging) && facing(u, t) > .4f;
            default -> { }
        }
        return false;
    }

    static boolean charge(World w, Unit u, AbilityDef a) {
        Unit t = u.abilityTarget;
        u.abilityT -= DT;
        if (t == null || !t.alive || u.abilityT <= 0) { u.charging = false; u.abilityCd = (float) a.cooldown() * .5f; return false; }
        float dx = t.x - u.x, dz = t.z - u.z, d = Math.max(.01f, (float) Math.sqrt(dx * dx + dz * dz));
        float sp = u.speed * 2.3f * (u.slowFor > 0 ? u.slowMul : 1);
        u.vx = dx / d * sp; u.vz = dz / d * sp; u.x += u.vx * DT; u.z += u.vz * DT;
        u.yaw = (float) Math.atan2(dx, dz); u.walkAmount = 1; u.walkPhase += sp * DT * 3.2f / Math.max(.5f, u.ragdoll.scale);
        // bowl over anyone in the way
        w.hash.query(u.x, u.z, u.radius + (float) a.radius(), o -> {
            if (o.team == u.team || !o.alive || o == t || u.bowled.contains(o.id)) return;
            u.bowled.add(o.id);
            w.damage(o, (float) u.weapon.damage() * .3f, 9, u.x, u.z, Rig.HIP, u);
        });
        if (d < u.radius + t.radius + u.range * .6f) {
            u.charging = false; u.abilityCd = (float) a.cooldown();
            if (t.bracing && facing(t, u) > .3f) {   // ran onto the spears
                w.damage(u, (float) (t.weapon.damage() * t.ability.power()), 14, t.x, t.z, Rig.TORSO, t);
                return true;
            }
            w.damage(t, (float) (u.weapon.damage() * a.power() * u.buffMul), (float) u.weapon.knockback() * 1.6f, u.x - dx / d, u.z - dz / d, Rig.TORSO, u);
            u.cooldown = (float) u.weapon.cooldown();
        }
        return true;
    }

    /** Passive abilities: commander rally aura, trampling. */
    static void passive(World w, Unit u) {
        AbilityDef a = u.ability;
        switch (a.kind()) {
            case "rally" -> {
                if (!ready(u)) return;
                u.abilityCd = (float) a.cooldown();
                w.hash.query(u.x, u.z, (float) a.radius(), o -> {
                    if (o.team != u.team || !o.alive) return;
                    o.buffFor = Math.max(o.buffFor, (float) a.duration()); o.buffMul = Math.max(o.buffMul, (float) a.power()); o.buffStab = Math.max(o.buffStab, .35f);
                });
            }
            case "trample" -> {
                float sp = (float) Math.sqrt(u.vx * u.vx + u.vz * u.vz);
                if (!ready(u) || sp < u.speed * .4f) return;
                u.abilityCd = (float) a.cooldown();
                w.hash.query(u.x, u.z, (float) a.radius() * u.ragdoll.scale, o -> {
                    if (o.team == u.team || !o.alive || o.cls.equals("large") || o.cls.equals("cavalry")) return;
                    w.damage(o, (float) a.power(), 12, u.x, u.z, Rig.HIP, u);
                });
            }
            default -> { }
        }
    }

    /** Damage multiplier for a strike (buffs, ambush, brace against riders, headbutt) and its side effects. */
    static float strikeMul(World w, Unit u, Unit t) {
        float m = u.buffMul * u.nextStrikeMul;
        u.nextStrikeMul = 1;
        AbilityDef a = u.ability;
        if (t != null) {
            if (a.kind().equals("brace") && (t.cls.equals("cavalry") || t.cls.equals("large") || t.charging)) { m *= (float) a.power(); if (t.charging) { t.charging = false; t.abilityCd = (float) t.ability.cooldown(); } }
            if (a.kind().equals("headbutt") && ready(u)) { m *= (float) a.power(); t.stunFor = Math.max(t.stunFor, (float) a.duration()); u.abilityCd = (float) a.cooldown(); }
        }
        return m;
    }

    /** After a melee hit: cleave the others in front. */
    static void afterMelee(World w, Unit u, Unit t, float dmg) {
        if (!u.ability.kind().equals("cleave")) return;
        float fx = (float) Math.sin(u.yaw), fz = (float) Math.cos(u.yaw), reach = u.range + u.radius + .8f, share = dmg * (float) u.ability.power();
        w.hash.query(u.x, u.z, reach + 1, o -> {
            if (o == t || o.team == u.team || !o.alive) return;
            float dx = o.x - u.x, dz = o.z - u.z, l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l > reach + o.radius || (dx * fx + dz * fz) / Math.max(.01f, l) < .3f) return;
            w.damage(o, share, (float) u.weapon.knockback() * .6f, u.x, u.z, Rig.TORSO, u);
        });
    }

    /** On-hit effects of projectiles. Returns true if the projectile keeps flying (pierce). */
    static boolean onProjectileHit(World w, Projectile p, Unit hit) {
        Unit src = p.owner; if (src == null) return false;
        AbilityDef a = src.ability;
        switch (a.kind()) {
            case "frost" -> { hit.slowFor = Math.max(hit.slowFor, (float) a.duration()); hit.slowMul = Math.min(hit.slowMul, (float) a.power()); }
            case "poison" -> { hit.poisonFor = Math.max(hit.poisonFor, (float) a.duration()); hit.poisonDps = Math.max(hit.poisonDps, (float) a.power()); hit.poisonSrc = src; }
            case "chain" -> {
                int[] left = {(int) a.duration()};
                java.util.List<Unit> near = new java.util.ArrayList<>();
                w.hash.query(hit.x, hit.z, (float) a.range(), o -> { if (o != hit && o.team != src.team && o.alive) near.add(o); });
                near.sort((x, y) -> Float.compare(World.dist(x.x, x.z, hit.x, hit.z), World.dist(y.x, y.z, hit.x, hit.z)));
                for (Unit o : near) { if (left[0]-- <= 0) break; w.damage(o, p.damage * (float) a.power(), p.knockback * .5f, hit.x, hit.z, Rig.TORSO, src); }
            }
            case "pierce" -> { if (p.pierced < (int) a.power()) { p.pierced++; p.lastHit = hit; return true; } }
            default -> { }
        }
        return false;
    }

    /**
     * Defensive reactions to an incoming hit. Returns {damage, knockback} after shields, or null when dodged.
     */
    static float[] onDamaged(World w, Unit t, Unit src, float dmg, float kb, float fromX, float fromZ, boolean area) {
        AbilityDef a = t.ability;
        if (t.stunFor > 0 || t.knocked || !t.alive) return new float[]{dmg, kb};
        switch (a.kind()) {
            case "shield_block" -> {
                if (frontal(t, fromX, fromZ) > .2f) {
                    float p = (float) a.power();
                    if (countShieldAllies(w, t) >= 3) p = Math.min(.85f, p * 1.15f);
                    return new float[]{dmg * (1 - p), kb * .3f};
                }
            }
            case "dodge", "riposte" -> {
                if (!area && src != null && dmg > 0 && ready(t) && w.rng.nextFloat() < a.power()) {
                    float dx = t.x - fromX, dz = t.z - fromZ, l = Math.max(.01f, (float) Math.sqrt(dx * dx + dz * dz)), side = w.rng.nextBoolean() ? 1 : -1;
                    t.x += -dz / l * side * (float) a.radius(); t.z += dx / l * side * (float) a.radius();
                    t.ragdoll.impulse(Rig.HIP, -dz / l * side * 40, 0, dx / l * side * 40, DT);
                    t.abilityCd = (float) a.cooldown();
                    if (a.kind().equals("riposte") && src.alive && World.dist(t.x, t.z, src.x, src.z) < t.range + t.radius + src.radius + .8f) {
                        t.target = src; t.cooldown = 0; t.attackAnim = 0; t.hitDealt = false; t.nextStrikeMul = 1.3f;
                    }
                    return null;
                }
            }
            default -> { }
        }
        return new float[]{dmg, kb};
    }

    static float frontal(Unit t, float fromX, float fromZ) {
        float dx = fromX - t.x, dz = fromZ - t.z, l = Math.max(.01f, (float) Math.sqrt(dx * dx + dz * dz));
        return (dx * (float) Math.sin(t.yaw) + dz * (float) Math.cos(t.yaw)) / l;
    }

    /** How much `u` is facing `o` (1 = straight at it). */
    static float facing(Unit u, Unit o) { return frontal(u, o.x, o.z); }

    static int cnt;
    static int countEnemies(World w, Unit u, float x, float z, float r, boolean inFront) {
        cnt = 0;
        float fx = (float) Math.sin(u.yaw), fz = (float) Math.cos(u.yaw);
        w.hash.query(x, z, r, o -> {
            if (o.team == u.team || !o.alive) return;
            if (inFront) { float dx = o.x - u.x, dz = o.z - u.z, l = Math.max(.01f, (float) Math.sqrt(dx * dx + dz * dz)); if ((dx * fx + dz * fz) / l < .3f) return; }
            cnt++;
        });
        return cnt;
    }

    static int countShieldAllies(World w, Unit t) {
        cnt = 0;
        w.hash.query(t.x, t.z, 2.2f, o -> { if (o != t && o.team == t.team && o.alive && o.ability.kind().equals("shield_block")) cnt++; });
        return cnt;
    }

    static boolean found;
    static boolean woundedNear(World w, Unit u, float r) {
        found = false;
        w.hash.query(u.x, u.z, r, o -> { if (o.team == u.team && o.alive && o.hp < o.maxHp * .85f) found = true; });
        return found;
    }
}
