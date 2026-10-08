// GENERATED from design/weapon_classes.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record WeaponDef(String id, String kind, double range, int damage, double cooldown, double aoe, double knockback, String projectile, double gravity, int count, double speed) {
    public static final WeaponDef MELEE_LIGHT = new WeaponDef("melee_light", "melee", 1.6, 40, 0.9, 0.0, 2.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_SHIELD = new WeaponDef("melee_shield", "melee", 1.5, 30, 1.0, 0.0, 1.5, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_HEAVY = new WeaponDef("melee_heavy", "melee", 2.0, 120, 1.4, 0.0, 6.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_DUAL = new WeaponDef("melee_dual", "melee", 1.6, 45, 0.5, 0.0, 2.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_REACH = new WeaponDef("melee_reach", "melee", 3.2, 70, 1.3, 0.0, 3.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_FAST = new WeaponDef("melee_fast", "melee", 1.6, 60, 0.45, 0.0, 1.5, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_PUSH = new WeaponDef("melee_push", "melee", 1.8, 25, 1.2, 0.0, 14.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_CHARGE = new WeaponDef("melee_charge", "melee", 2.2, 200, 2.0, 1.5, 18.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_CREW = new WeaponDef("melee_crew", "melee", 3.0, 90, 1.0, 2.0, 6.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef MELEE_STAFF = new WeaponDef("melee_staff", "melee", 2.6, 150, 0.8, 1.5, 10.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef TRAMPLE = new WeaponDef("trample", "melee", 3.0, 180, 1.5, 2.5, 25.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef LANCE_CHARGE = new WeaponDef("lance_charge", "melee", 3.0, 300, 3.0, 0.0, 20.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef THROWN = new WeaponDef("thrown", "ranged", 20.0, 55, 2.5, 0.0, 3.0, "spear", 20.0, 1, 24.0);
    public static final WeaponDef THROWN_HEAVY = new WeaponDef("thrown_heavy", "ranged", 12.0, 80, 4.5, 1.5, 8.0, "boulder", 20.0, 1, 20.0);
    public static final WeaponDef BOW = new WeaponDef("bow", "ranged", 20.0, 45, 2.0, 0.0, 1.0, "arrow", 20.0, 1, 32.0);
    public static final WeaponDef BOW_SLOW = new WeaponDef("bow_slow", "ranged", 20.0, 40, 2.2, 0.0, 1.0, "ice_arrow", 20.0, 1, 32.0);
    public static final WeaponDef BOW_POISON = new WeaponDef("bow_poison", "ranged", 20.0, 35, 2.0, 0.0, 1.0, "snake_arrow", 20.0, 1, 32.0);
    public static final WeaponDef BOW_AIR = new WeaponDef("bow_air", "ranged", 18.0, 40, 2.0, 0.0, 1.0, "arrow", 20.0, 1, 32.0);
    public static final WeaponDef MUSKET = new WeaponDef("musket", "ranged", 26.0, 140, 5.0, 0.0, 5.0, "bullet", 20.0, 1, 90.0);
    public static final WeaponDef ROCKET_VOLLEY = new WeaponDef("rocket_volley", "ranged", 30.0, 60, 6.0, 2.0, 6.0, "firework", 20.0, 6, 26.0);
    public static final WeaponDef SIEGE_BOLT = new WeaponDef("siege_bolt", "ranged", 34.0, 400, 6.0, 0.0, 20.0, "bolt", 20.0, 1, 45.0);
    public static final WeaponDef SIEGE_BOULDER = new WeaponDef("siege_boulder", "ranged", 36.0, 300, 7.0, 3.0, 25.0, "boulder", 20.0, 1, 20.0);
    public static final WeaponDef CANNON_RING = new WeaponDef("cannon_ring", "ranged", 16.0, 250, 4.0, 2.5, 22.0, "cannonball", 20.0, 1, 20.0);
    public static final WeaponDef MAGIC_AOE = new WeaponDef("magic_aoe", "ranged", 16.0, 80, 3.0, 2.5, 8.0, "bone_orb", 20.0, 1, 18.0);
    public static final WeaponDef MAGIC_LIGHTNING = new WeaponDef("magic_lightning", "ranged", 24.0, 350, 4.0, 3.0, 15.0, "lightning", 20.0, 1, 200.0);
    public static final WeaponDef FIRE_BREATH = new WeaponDef("fire_breath", "ranged", 10.0, 40, 0.25, 2.0, 2.0, "flame", 20.0, 1, 14.0);
    public static final WeaponDef HEAL = new WeaponDef("heal", "support", 10.0, -40, 1.5, 3.0, 0.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef BUFF_AURA = new WeaponDef("buff_aura", "support", 8.0, 0, 1.0, 8.0, 0.0, "none", 0.0, 1, 0.0);
    public static final WeaponDef LASER_RIFLE = new WeaponDef("laser_rifle", "ranged", 20.0, 26, 1.1, 0.0, 1.0, "laser", 0.1, 1, 140.0);
    public static final WeaponDef PLASMA = new WeaponDef("plasma", "ranged", 18.0, 120, 2.8, 1.8, 7.0, "plasma", 4.0, 1, 34.0);
    public static final WeaponDef RAIL = new WeaponDef("rail", "ranged", 36.0, 160, 6.0, 0.0, 12.0, "rail", 0.1, 1, 400.0);
    public static final WeaponDef MISSILE_POD = new WeaponDef("missile_pod", "ranged", 30.0, 45, 5.5, 1.8, 5.0, "missile", 6.0, 3, 30.0);
    public static final WeaponDef PLASMA_CANNON = new WeaponDef("plasma_cannon", "ranged", 26.0, 380, 5.0, 3.0, 22.0, "plasma", 4.0, 1, 36.0);
    public static final WeaponDef ION_MORTAR = new WeaponDef("ion_mortar", "ranged", 40.0, 200, 7.5, 3.5, 20.0, "plasma", 20.0, 1, 22.0);
    public static final WeaponDef ENERGY_BLADE = new WeaponDef("energy_blade", "melee", 1.8, 50, 0.75, 0.0, 3.0, "none", 0.0, 1, 0.0);
    public static final List<WeaponDef> ALL = List.of(MELEE_LIGHT, MELEE_SHIELD, MELEE_HEAVY, MELEE_DUAL, MELEE_REACH, MELEE_FAST, MELEE_PUSH, MELEE_CHARGE, MELEE_CREW, MELEE_STAFF, TRAMPLE, LANCE_CHARGE, THROWN, THROWN_HEAVY, BOW, BOW_SLOW, BOW_POISON, BOW_AIR, MUSKET, ROCKET_VOLLEY, SIEGE_BOLT, SIEGE_BOULDER, CANNON_RING, MAGIC_AOE, MAGIC_LIGHTNING, FIRE_BREATH, HEAL, BUFF_AURA, LASER_RIFLE, PLASMA, RAIL, MISSILE_POD, PLASMA_CANNON, ION_MORTAR, ENERGY_BLADE);
    private static final Map<String, WeaponDef> BY_ID = new LinkedHashMap<>();
    static { for (WeaponDef x : ALL) BY_ID.put(x.id(), x); }
    public static WeaponDef byId(String id) { WeaponDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown weapon_classes id " + id); return x; }
}
