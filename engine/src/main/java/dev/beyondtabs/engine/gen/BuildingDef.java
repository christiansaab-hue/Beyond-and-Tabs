// GENERATED from design/buildings.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record BuildingDef(String id, String race, String kind, int tier, int levels, int hp, int metal, int energy, int buildWork, String footprint, String produces, String effect, String valuePerLevel, String requiresTech, int blastRadius, int blastDamage) {
    public static final BuildingDef AW_METAL_EXTRACTOR = new BuildingDef("aw_metal_extractor", "ancient_world", "economy", 1, 3, 800, 50, 500, 15, "3x3", "none", "metal_per_s", "2.0,3.5,5.5", "none", 0, 0);
    public static final BuildingDef AW_ENERGY_GEN = new BuildingDef("aw_energy_gen", "ancient_world", "economy", 1, 3, 800, 35, 0, 12, "4x4", "none", "energy_per_s", "25,40,60", "none", 0, 0);
    public static final BuildingDef AW_STORAGE = new BuildingDef("aw_storage", "ancient_world", "economy", 1, 2, 1500, 100, 1000, 20, "5x5", "none", "storage", "1000,3000", "none", 0, 0);
    public static final BuildingDef AW_CONVERTER = new BuildingDef("aw_converter", "ancient_world", "economy", 2, 2, 800, 150, 2000, 40, "4x4", "none", "energy_to_metal_per_s", "1.0,2.0", "aw_tech_t2", 0, 0);
    public static final BuildingDef AW_TECH_CENTER = new BuildingDef("aw_tech_center", "ancient_world", "tech", 1, 3, 2500, 300, 3000, 60, "7x7", "none", "unlock_tier", "1,2,3", "none", 0, 0);
    public static final BuildingDef AW_WATCHTOWER = new BuildingDef("aw_watchtower", "ancient_world", "defense", 1, 3, 1200, 80, 800, 18, "3x3", "none", "ranged_dps", "25,45,80", "none", 0, 0);
    public static final BuildingDef AW_WALL = new BuildingDef("aw_wall", "ancient_world", "defense", 1, 2, 400, 5, 0, 1, "1x1", "none", "hp", "400,1200", "none", 0, 0);
    public static final BuildingDef KD_METAL_EXTRACTOR = new BuildingDef("kd_metal_extractor", "kingdoms", "economy", 1, 3, 800, 50, 500, 15, "3x3", "none", "metal_per_s", "2.0,3.5,5.5", "none", 0, 0);
    public static final BuildingDef KD_ENERGY_GEN = new BuildingDef("kd_energy_gen", "kingdoms", "economy", 1, 3, 800, 35, 0, 12, "5x5", "none", "energy_per_s", "25,40,60", "none", 0, 0);
    public static final BuildingDef KD_STORAGE = new BuildingDef("kd_storage", "kingdoms", "economy", 1, 2, 1500, 100, 1000, 20, "5x5", "none", "storage", "1000,3000", "none", 0, 0);
    public static final BuildingDef KD_CONVERTER = new BuildingDef("kd_converter", "kingdoms", "economy", 2, 2, 800, 150, 2000, 40, "4x4", "none", "energy_to_metal_per_s", "1.0,2.0", "kd_tech_t2", 0, 0);
    public static final BuildingDef KD_TECH_CENTER = new BuildingDef("kd_tech_center", "kingdoms", "tech", 1, 3, 2500, 300, 3000, 60, "7x7", "none", "unlock_tier", "1,2,3", "none", 0, 0);
    public static final BuildingDef KD_WATCHTOWER = new BuildingDef("kd_watchtower", "kingdoms", "defense", 1, 3, 1200, 80, 800, 18, "3x3", "none", "ranged_dps", "25,45,80", "none", 0, 0);
    public static final BuildingDef KD_WALL = new BuildingDef("kd_wall", "kingdoms", "defense", 1, 2, 400, 5, 0, 1, "1x1", "none", "hp", "400,1200", "none", 0, 0);
    public static final BuildingDef AW_BARRACKS = new BuildingDef("aw_barracks", "ancient_world", "factory", 1, 3, 3000, 150, 1500, 40, "7x7", "units_t1", "build_power", "10,15,22", "none", 0, 0);
    public static final BuildingDef AW_WAR_LODGE = new BuildingDef("aw_war_lodge", "ancient_world", "factory", 2, 3, 4500, 600, 6000, 90, "9x9", "units_t2", "build_power", "20,30,45", "aw_tech_t2", 0, 0);
    public static final BuildingDef AW_HALL_OF_LEGENDS = new BuildingDef("aw_hall_of_legends", "ancient_world", "factory", 3, 2, 7000, 1500, 15000, 180, "11x11", "units_t3", "build_power", "40,60", "aw_tech_t3", 0, 0);
    public static final BuildingDef AW_SIEGE_YARD = new BuildingDef("aw_siege_yard", "ancient_world", "factory", 3, 2, 7000, 1500, 15000, 180, "11x11", "units_t3_siege", "build_power", "40,60", "aw_tech_t3", 0, 0);
    public static final BuildingDef KD_BARRACKS = new BuildingDef("kd_barracks", "kingdoms", "factory", 1, 3, 3000, 150, 1500, 40, "7x7", "units_t1", "build_power", "10,15,22", "none", 0, 0);
    public static final BuildingDef KD_KEEP_WORKSHOP = new BuildingDef("kd_keep_workshop", "kingdoms", "factory", 2, 3, 4500, 600, 6000, 90, "9x9", "units_t2", "build_power", "20,30,45", "kd_tech_t2", 0, 0);
    public static final BuildingDef KD_ROYAL_COURT = new BuildingDef("kd_royal_court", "kingdoms", "factory", 3, 2, 7000, 1500, 15000, 180, "11x11", "units_t3", "build_power", "40,60", "kd_tech_t3", 0, 0);
    public static final BuildingDef KD_SIEGE_WORKS = new BuildingDef("kd_siege_works", "kingdoms", "factory", 3, 2, 7000, 1500, 15000, 180, "11x11", "units_t3_siege", "build_power", "40,60", "kd_tech_t3", 0, 0);
    public static final BuildingDef AW_HEARTFORGE = new BuildingDef("aw_heartforge", "ancient_world", "economy", 3, 2, 3500, 900, 9000, 150, "6x6", "none", "energy_per_s", "700,1000", "aw_tech_t3", 10, 1200);
    public static final BuildingDef KD_CRUCIBLE = new BuildingDef("kd_crucible", "kingdoms", "economy", 3, 2, 3500, 900, 9000, 150, "6x6", "none", "energy_per_s", "700,1000", "kd_tech_t3", 10, 1200);
    public static final BuildingDef AW_DRUM_CIRCLE = new BuildingDef("aw_drum_circle", "ancient_world", "support", 2, 2, 700, 120, 1500, 25, "4x4", "none", "assist_power", "15,25", "aw_tech_t2", 0, 0);
    public static final BuildingDef KD_CRANE = new BuildingDef("kd_crane", "kingdoms", "support", 2, 2, 700, 120, 1500, 25, "4x4", "none", "assist_power", "15,25", "kd_tech_t2", 0, 0);
    public static final BuildingDef SF_METAL_EXTRACTOR = new BuildingDef("sf_metal_extractor", "starforge", "economy", 1, 3, 800, 50, 500, 15, "3x3", "none", "metal_per_s", "2.0,3.5,5.5", "none", 0, 0);
    public static final BuildingDef SF_ENERGY_GEN = new BuildingDef("sf_energy_gen", "starforge", "economy", 1, 3, 700, 35, 0, 12, "4x4", "none", "energy_per_s", "25,40,60", "none", 0, 0);
    public static final BuildingDef SF_STORAGE = new BuildingDef("sf_storage", "starforge", "economy", 1, 2, 1500, 100, 1000, 20, "5x5", "none", "storage", "1000,3000", "none", 0, 0);
    public static final BuildingDef SF_CONVERTER = new BuildingDef("sf_converter", "starforge", "economy", 2, 2, 800, 150, 2000, 40, "4x4", "none", "energy_to_metal_per_s", "1.0,2.0", "sf_tech_t2", 0, 0);
    public static final BuildingDef SF_TECH_CENTER = new BuildingDef("sf_tech_center", "starforge", "tech", 1, 3, 2500, 300, 3000, 60, "7x7", "none", "unlock_tier", "1,2,3", "none", 0, 0);
    public static final BuildingDef SF_WATCHTOWER = new BuildingDef("sf_watchtower", "starforge", "defense", 1, 3, 1300, 90, 900, 18, "3x3", "none", "ranged_dps", "25,45,80", "none", 0, 0);
    public static final BuildingDef SF_WALL = new BuildingDef("sf_wall", "starforge", "defense", 1, 2, 450, 5, 0, 1, "1x1", "none", "hp", "450,1300", "none", 0, 0);
    public static final BuildingDef SF_BARRACKS = new BuildingDef("sf_barracks", "starforge", "factory", 1, 3, 3000, 150, 1500, 40, "7x7", "units_t1", "build_power", "10,15,22", "none", 0, 0);
    public static final BuildingDef SF_MECH_BAY = new BuildingDef("sf_mech_bay", "starforge", "factory", 2, 3, 4500, 600, 6000, 90, "9x9", "units_t2", "build_power", "20,30,45", "sf_tech_t2", 0, 0);
    public static final BuildingDef SF_TITAN_FOUNDRY = new BuildingDef("sf_titan_foundry", "starforge", "factory", 3, 2, 7000, 1500, 15000, 180, "11x11", "units_t3", "build_power", "40,60", "sf_tech_t3", 0, 0);
    public static final BuildingDef SF_ARTILLERY_FOUNDRY = new BuildingDef("sf_artillery_foundry", "starforge", "factory", 3, 2, 7000, 1500, 15000, 180, "11x11", "units_t3_siege", "build_power", "40,60", "sf_tech_t3", 0, 0);
    public static final BuildingDef SF_FUSION_CORE = new BuildingDef("sf_fusion_core", "starforge", "economy", 3, 2, 3500, 900, 9000, 150, "6x6", "none", "energy_per_s", "700,1000", "sf_tech_t3", 12, 1600);
    public static final BuildingDef SF_DRONE_PAD = new BuildingDef("sf_drone_pad", "starforge", "support", 2, 2, 700, 120, 1500, 25, "4x4", "none", "assist_power", "15,25", "sf_tech_t2", 0, 0);
    public static final List<BuildingDef> ALL = List.of(AW_METAL_EXTRACTOR, AW_ENERGY_GEN, AW_STORAGE, AW_CONVERTER, AW_TECH_CENTER, AW_WATCHTOWER, AW_WALL, KD_METAL_EXTRACTOR, KD_ENERGY_GEN, KD_STORAGE, KD_CONVERTER, KD_TECH_CENTER, KD_WATCHTOWER, KD_WALL, AW_BARRACKS, AW_WAR_LODGE, AW_HALL_OF_LEGENDS, AW_SIEGE_YARD, KD_BARRACKS, KD_KEEP_WORKSHOP, KD_ROYAL_COURT, KD_SIEGE_WORKS, AW_HEARTFORGE, KD_CRUCIBLE, AW_DRUM_CIRCLE, KD_CRANE, SF_METAL_EXTRACTOR, SF_ENERGY_GEN, SF_STORAGE, SF_CONVERTER, SF_TECH_CENTER, SF_WATCHTOWER, SF_WALL, SF_BARRACKS, SF_MECH_BAY, SF_TITAN_FOUNDRY, SF_ARTILLERY_FOUNDRY, SF_FUSION_CORE, SF_DRONE_PAD);
    private static final Map<String, BuildingDef> BY_ID = new LinkedHashMap<>();
    static { for (BuildingDef x : ALL) BY_ID.put(x.id(), x); }
    public static BuildingDef byId(String id) { BuildingDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown buildings id " + id); return x; }
}
