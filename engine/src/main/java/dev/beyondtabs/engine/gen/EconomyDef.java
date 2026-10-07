// GENERATED from design/economy.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record EconomyDef(String id, String value, String unit, String source) {
    public static final EconomyDef START_METAL = new EconomyDef("start_metal", "1000", "metal", "BAR start resources");
    public static final EconomyDef START_ENERGY = new EconomyDef("start_energy", "1000", "energy", "BAR start resources");
    public static final EconomyDef COMMANDER_METAL_INCOME = new EconomyDef("commander_metal_income", "2.0", "metal/s", "BAR commander");
    public static final EconomyDef COMMANDER_ENERGY_INCOME = new EconomyDef("commander_energy_income", "25", "energy/s", "BAR commander");
    public static final EconomyDef BASE_STORAGE = new EconomyDef("base_storage", "1000", "each", "BAR commander storage");
    public static final EconomyDef COMMANDER_BUILD_POWER = new EconomyDef("commander_build_power", "30", "work/s", "BAR commander");
    public static final EconomyDef BUILDER_BUILD_POWER = new EconomyDef("builder_build_power", "10", "work/s per builder tier", "design");
    public static final EconomyDef BUILD_DRAIN_RULE = new EconomyDef("build_drain_rule", "proportional", "rule", "BAR: spending scales with build power; if short, all builds slow proportionally");
    public static final EconomyDef RECLAIM_FRACTION = new EconomyDef("reclaim_fraction", "0.5", "of metal cost", "BAR wreck reclaim");
    public static final EconomyDef UNIT_COST_METAL_FACTOR = new EconomyDef("unit_cost_metal_factor", "0.5", "x tabs_cost", "design");
    public static final EconomyDef UNIT_COST_ENERGY_FACTOR = new EconomyDef("unit_cost_energy_factor", "4", "x tabs_cost", "design");
    public static final EconomyDef UNIT_WORK_FACTOR = new EconomyDef("unit_work_factor", "0.25", "x tabs_cost", "design");
    public static final EconomyDef UPGRADE_COST_PER_LEVEL = new EconomyDef("upgrade_cost_per_level", "0.6", "x base cost", "design");
    public static final EconomyDef METAL_SPOTS_PER_PLAYER = new EconomyDef("metal_spots_per_player", "8", "spots", "map generator");
    public static final EconomyDef SUPPLY_CAP = new EconomyDef("supply_cap", "200", "supply", "performance sheet");
    public static final List<EconomyDef> ALL = List.of(START_METAL, START_ENERGY, COMMANDER_METAL_INCOME, COMMANDER_ENERGY_INCOME, BASE_STORAGE, COMMANDER_BUILD_POWER, BUILDER_BUILD_POWER, BUILD_DRAIN_RULE, RECLAIM_FRACTION, UNIT_COST_METAL_FACTOR, UNIT_COST_ENERGY_FACTOR, UNIT_WORK_FACTOR, UPGRADE_COST_PER_LEVEL, METAL_SPOTS_PER_PLAYER, SUPPLY_CAP);
    private static final Map<String, EconomyDef> BY_ID = new LinkedHashMap<>();
    static { for (EconomyDef x : ALL) BY_ID.put(x.id(), x); }
    public static EconomyDef byId(String id) { EconomyDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown economy id " + id); return x; }
}
