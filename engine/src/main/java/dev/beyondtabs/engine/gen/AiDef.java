// GENERATED from design/ai.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record AiDef(String id, String behaviour, double runsEveryS) {
    public static final AiDef ECONOMY_PLANNER = new AiDef("economy_planner", "Keep metal/energy income ratio near 1:10; build extractors on free spots, energy when energy-starved, storage when overflowing", 2.0);
    public static final AiDef BUILD_ORDER = new AiDef("build_order", "Opening: 2 extractors, 1 energy, barracks, builder, 2 extractors, energy; then scale factories with income", 1.0);
    public static final AiDef TECH_PLANNER = new AiDef("tech_planner", "Go T2 when income >= 15 metal/s or enemy has T2; T3 at >= 40 metal/s", 5.0);
    public static final AiDef ARMY_COMPOSER = new AiDef("army_composer", "Counter enemy composition seen by scouts (ranged vs melee-heavy, siege vs defenses)", 5.0);
    public static final AiDef SCOUTING = new AiDef("scouting", "Keep one fast unit scouting enemy expansions; update threat map", 3.0);
    public static final AiDef THREAT_MAP = new AiDef("threat_map", "Grid of enemy strength by cell, decays over time; used for attacks and defense", 1.0);
    public static final AiDef ATTACK_PLANNER = new AiDef("attack_planner", "Attack when own army value > 1.3x estimated enemy at target; prefer weak extractors", 3.0);
    public static final AiDef MICRO = new AiDef("micro", "Pull back units under 30% hp, focus fire, keep ranged behind melee, kite with ranged", 0.5);
    public static final AiDef DEFENSE = new AiDef("defense", "Respond to raids with nearest group; build watchtowers at contested spots", 1.0);
    public static final AiDef DIFFICULTY = new AiDef("difficulty", "Easy/Normal/Hard/Brutal scale APM, reaction delay and planner accuracy; never cheats on resources", 0.0);
    public static final List<AiDef> ALL = List.of(ECONOMY_PLANNER, BUILD_ORDER, TECH_PLANNER, ARMY_COMPOSER, SCOUTING, THREAT_MAP, ATTACK_PLANNER, MICRO, DEFENSE, DIFFICULTY);
    private static final Map<String, AiDef> BY_ID = new LinkedHashMap<>();
    static { for (AiDef x : ALL) BY_ID.put(x.id(), x); }
    public static AiDef byId(String id) { AiDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown ai id " + id); return x; }
}
