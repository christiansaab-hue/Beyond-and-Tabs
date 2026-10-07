// GENERATED from design/tech.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record TechDef(String id, String race, int tier, String researchedAt, int minBuildingLevel, int metal, int energy, int seconds, String requires, String unlocks) {
    public static final TechDef AW_TECH_T2 = new TechDef("aw_tech_t2", "ancient_world", 2, "aw_tech_center", 2, 800, 8000, 60, "none", "aw_war_lodge,aw_converter,aw_builder_t2");
    public static final TechDef AW_TECH_T3 = new TechDef("aw_tech_t3", "ancient_world", 3, "aw_tech_center", 3, 2500, 25000, 120, "aw_tech_t2", "aw_hall_of_legends,aw_siege_yard");
    public static final TechDef AW_ARMOR_1 = new TechDef("aw_armor_1", "ancient_world", 2, "aw_tech_center", 2, 400, 4000, 45, "aw_tech_t2", "mod:hp+10%");
    public static final TechDef AW_WEAPONS_1 = new TechDef("aw_weapons_1", "ancient_world", 2, "aw_tech_center", 2, 400, 4000, 45, "aw_tech_t2", "mod:damage+10%");
    public static final TechDef AW_BALANCE_TRAINING = new TechDef("aw_balance_training", "ancient_world", 3, "aw_tech_center", 3, 1000, 10000, 60, "aw_tech_t3", "mod:ragdoll_recovery+40%");
    public static final TechDef KD_TECH_T2 = new TechDef("kd_tech_t2", "kingdoms", 2, "kd_tech_center", 2, 800, 8000, 60, "none", "kd_keep_workshop,kd_converter,kd_builder_t2");
    public static final TechDef KD_TECH_T3 = new TechDef("kd_tech_t3", "kingdoms", 3, "kd_tech_center", 3, 2500, 25000, 120, "kd_tech_t2", "kd_royal_court,kd_siege_works");
    public static final TechDef KD_ARMOR_1 = new TechDef("kd_armor_1", "kingdoms", 2, "kd_tech_center", 2, 400, 4000, 45, "kd_tech_t2", "mod:hp+10%");
    public static final TechDef KD_WEAPONS_1 = new TechDef("kd_weapons_1", "kingdoms", 2, "kd_tech_center", 2, 400, 4000, 45, "kd_tech_t2", "mod:damage+10%");
    public static final TechDef KD_BALANCE_TRAINING = new TechDef("kd_balance_training", "kingdoms", 3, "kd_tech_center", 3, 1000, 10000, 60, "kd_tech_t3", "mod:ragdoll_recovery+40%");
    public static final List<TechDef> ALL = List.of(AW_TECH_T2, AW_TECH_T3, AW_ARMOR_1, AW_WEAPONS_1, AW_BALANCE_TRAINING, KD_TECH_T2, KD_TECH_T3, KD_ARMOR_1, KD_WEAPONS_1, KD_BALANCE_TRAINING);
    private static final Map<String, TechDef> BY_ID = new LinkedHashMap<>();
    static { for (TechDef x : ALL) BY_ID.put(x.id(), x); }
    public static TechDef byId(String id) { TechDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown tech id " + id); return x; }
}
