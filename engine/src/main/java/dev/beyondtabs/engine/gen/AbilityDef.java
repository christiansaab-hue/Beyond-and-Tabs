// GENERATED from design/abilities.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record AbilityDef(String id, String name, String kind, String trigger, double cooldown, double range, double power, double radius, double duration, String description) {
    public static final AbilityDef NONE = new AbilityDef("none", "None", "none", "passive", 0.0, 0.0, 0.0, 0.0, 0.0, "No special ability.");
    public static final AbilityDef CHARGE = new AbilityDef("charge", "Charge", "charge", "auto", 10.0, 12.0, 2.5, 1.2, 2.0, "Sprints at a target 4..range blocks away at 2.3x speed; the impact deals power x weapon damage with heavy knockback and bowls over anyone in the way.");
    public static final AbilityDef TRAMPLE = new AbilityDef("trample", "Trample", "trample", "passive", 0.5, 0.0, 60.0, 1.8, 0.0, "While moving, enemies under foot take power damage and are thrown aside every cooldown seconds.");
    public static final AbilityDef WHIRLWIND = new AbilityDef("whirlwind", "Whirlwind", "whirlwind", "auto", 9.0, 2.8, 0.6, 2.4, 2.0, "With 2+ enemies close, spins for duration seconds hitting everyone in radius for power x weapon damage every 0.25 s.");
    public static final AbilityDef LEAP = new AbilityDef("leap", "Leap", "leap", "auto", 9.0, 10.0, 1.5, 2.2, 0.7, "Leaps onto a target 3..range blocks away and lands with an area hit (power x weapon damage).");
    public static final AbilityDef DODGE = new AbilityDef("dodge", "Dodge", "dodge", "reactive", 2.5, 0.0, 0.4, 1.2, 0.0, "When struck, power chance to sidestep radius blocks and take no damage.");
    public static final AbilityDef RIPOSTE = new AbilityDef("riposte", "Riposte", "riposte", "reactive", 3.0, 0.0, 0.45, 1.0, 0.0, "Like dodge, but answers with an instant counter-attack.");
    public static final AbilityDef SHIELD_BLOCK = new AbilityDef("shield_block", "Shield block", "shield_block", "passive", 0.0, 0.0, 0.55, 0.0, 0.0, "Hits from the front lose power of their damage and most of their knockback; 3+ shield allies close by form a wall (+15%).");
    public static final AbilityDef SHIELD_BASH = new AbilityDef("shield_bash", "Shield bash", "shield_bash", "auto", 6.0, 1.8, 10.0, 0.0, 1.0, "Slams an adjacent enemy: power knockback, small damage, stuns for duration seconds.");
    public static final AbilityDef BRACE = new AbilityDef("brace", "Brace", "brace", "auto", 0.0, 0.0, 2.5, 0.0, 0.0, "Plants the weapon against cavalry, large units and chargers: power x damage against them and charges break on the point.");
    public static final AbilityDef HEADBUTT = new AbilityDef("headbutt", "Headbutt", "headbutt", "auto", 5.0, 1.6, 1.6, 0.0, 1.2, "A strike that stuns the target for duration seconds and deals power x damage.");
    public static final AbilityDef FROST = new AbilityDef("frost", "Frost arrows", "frost", "on_hit", 0.0, 0.0, 0.5, 0.0, 3.0, "Hits slow the target to power x speed for duration seconds.");
    public static final AbilityDef POISON = new AbilityDef("poison", "Poison arrows", "poison", "on_hit", 0.0, 0.0, 8.0, 0.0, 5.0, "Hits poison the target for power damage per second for duration seconds.");
    public static final AbilityDef BURN = new AbilityDef("burn", "Burn", "poison", "on_hit", 0.0, 0.0, 14.0, 0.0, 3.0, "Flames keep burning: power damage per second for duration seconds.");
    public static final AbilityDef SHADOWSTEP = new AbilityDef("shadowstep", "Shadowstep", "shadowstep", "auto", 8.0, 14.0, 1.6, 0.0, 0.0, "Vanishes and reappears behind a target 4..range away; the next strike deals power x damage. Prefers archers and casters.");
    public static final AbilityDef CLEAVE = new AbilityDef("cleave", "Cleave", "cleave", "on_hit", 0.0, 0.0, 0.6, 0.0, 0.0, "Each melee strike also hits other enemies in front for power x damage.");
    public static final AbilityDef WAR_CRY = new AbilityDef("war_cry", "War cry", "war_cry", "auto", 14.0, 10.0, 1.3, 8.0, 6.0, "With 3+ enemies near, allies in radius deal power x damage and keep their footing for duration seconds.");
    public static final AbilityDef INSPIRE = new AbilityDef("inspire", "Inspiring song", "war_cry", "auto", 4.0, 30.0, 1.15, 7.0, 4.5, "Plays on loop: allies in radius deal power x damage and keep their footing.");
    public static final AbilityDef RALLY = new AbilityDef("rally", "Rally", "rally", "passive", 1.0, 0.0, 1.1, 8.0, 1.5, "Commander aura: allies in radius stand firmer and deal power x damage.");
    public static final AbilityDef HEAL_PULSE = new AbilityDef("heal_pulse", "Healing light", "heal_pulse", "auto", 6.0, 10.0, 60.0, 5.0, 0.0, "Heals every ally in radius by power when someone near is hurt.");
    public static final AbilityDef CHAIN_LIGHTNING = new AbilityDef("chain_lightning", "Chain lightning", "chain", "on_hit", 0.0, 7.0, 0.6, 0.0, 2.0, "Bolts jump to up to duration more enemies within range for power x damage.");
    public static final AbilityDef PUSH_WAVE = new AbilityDef("push_wave", "Force push", "push_wave", "auto", 7.0, 4.0, 22.0, 4.0, 0.0, "With 2+ enemies in front, pushes everything in a cone away with power knockback.");
    public static final AbilityDef VOLLEY = new AbilityDef("volley", "Volley", "volley", "auto", 12.0, 0.0, 3.0, 0.0, 0.0, "The next shot looses power arrows at once.");
    public static final AbilityDef PIERCE = new AbilityDef("pierce", "Piercing bolt", "pierce", "on_hit", 0.0, 0.0, 3.0, 0.0, 0.0, "Bolts pass through up to power units.");
    public static final List<AbilityDef> ALL = List.of(NONE, CHARGE, TRAMPLE, WHIRLWIND, LEAP, DODGE, RIPOSTE, SHIELD_BLOCK, SHIELD_BASH, BRACE, HEADBUTT, FROST, POISON, BURN, SHADOWSTEP, CLEAVE, WAR_CRY, INSPIRE, RALLY, HEAL_PULSE, CHAIN_LIGHTNING, PUSH_WAVE, VOLLEY, PIERCE);
    private static final Map<String, AbilityDef> BY_ID = new LinkedHashMap<>();
    static { for (AbilityDef x : ALL) BY_ID.put(x.id(), x); }
    public static AbilityDef byId(String id) { AbilityDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown abilities id " + id); return x; }
}
