package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.config.ResourceCostConfigEntry;

import java.util.HashMap;

// defined here because we need to be able to access in both
// static (for ProductionItems) and nonstatic (for getCurrentPopulation) contexts
// and we can't declare static getters in the Unit interface

public class ResourceCost {
    public static final HashMap<String, ResourceCost> ENTRIES = new HashMap<>();
    public static final int TICKS_PER_SECOND = 20;

    public int food;
    public int wood;
    public int ore;
    public int emerald = 0;
    public int ticks;
    public int population; // for a building, indicates supply, for a unit, indicates usage
    public String id;

    private ResourceCost(int food, int wood, int ore, int ticks, int population) { // units
        this.food = food;
        this.wood = wood;
        this.ore = ore;
        this.ticks = ticks * TICKS_PER_SECOND;
        this.population = population;
    }

    private ResourceCost(int emeralds) { // units
        this.food = 0;
        this.wood = 0;
        this.ore = 0;
        this.ticks = 0;
        this.population = 0;
        this.emerald = emeralds;
    }

    public ResourceCost(String modid, String id) {
        //Constructor for static configurable ResourceCosts
        this.id = modid + "." + id;
        ENTRIES.put(this.id, this);
    }

    public static ResourceCost Unit(int food, int wood, int ore, int seconds, int population) { // buildings
        return new ResourceCost(food, wood, ore, seconds, population);
    }
    public static ResourceCost Research(int food, int wood, int ore, int seconds) { // buildings
        return new ResourceCost(food, wood, ore, seconds, 0);
    }
    public static ResourceCost Building(int food, int wood, int ore, int supply) { // buildings
        return new ResourceCost(food, wood, ore, 0, supply);
    }
    public static ResourceCost Enchantment(int food, int wood, int ore) { // buildings
        return new ResourceCost(food, wood, ore, 0, 0);
    }
    public static ResourceCost Emeralds(int emeralds) { // buildings
        return new ResourceCost(0);
    }
    public void bakeValues(ResourceCostConfigEntry rcce) {
        // BAR economy: the config still holds Reign of Nether's food/wood/ore prices; convert them to
        // Metal (stored in the ore field) and Energy (stored in the wood field). Food is no longer a cost.
        this.ore = toMetal(rcce.getFood(), rcce.getWood(), rcce.getOre());
        this.wood = toEnergy(rcce.getFood(), rcce.getWood(), rcce.getOre());
        this.food = 0;
        this.ticks = rcce.getSeconds() * TICKS_PER_SECOND;
        this.population = rcce.getPopulation();
    }

    // ---- BAR economy conversion (Reign of Nether food/wood/ore -> Metal/Energy) ----
    // metal  = ore + food*0.6 + wood*0.3, rounded to the nearest 5
    // energy = wood*1.5 + food*0.8,       rounded to the nearest 10
    // After conversion a ResourceCost's ore field means Metal, its wood field means Energy and food is 0.
    // Emerald costs (item shops) are untouched.
    public static int toMetal(int food, int wood, int ore) {
        return roundTo(ore + food * 0.6f + wood * 0.3f, 5);
    }
    public static int toEnergy(int food, int wood, int ore) {
        return roundTo(wood * 1.5f + food * 0.8f, 10);
    }
    private static int roundTo(float value, int step) {
        return Math.round(value / step) * step;
    }

    // convenience accessors that make the BAR meaning of the reused fields explicit
    public int metal() { return ore; }
    public int energy() { return wood; }
}
