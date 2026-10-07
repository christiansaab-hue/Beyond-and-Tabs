package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.EconomyDef;
import dev.beyondtabs.engine.gen.TechDef;
import java.util.HashSet;
import java.util.Set;

/** A player's (or AI's) resources and research. BAR flow economy: income per second, storage caps, proportional drain. */
public final class Team {
    public final int id; public final String race;
    public double metal, energy, metalStorage, energyStorage;
    public double metalIncome, energyIncome;          // this tick's production rates (per second)
    public double metalSpend, energySpend;             // this tick's actual spending rates
    double wantMetal, wantEnergy;                      // requested spend this tick, before the ratio
    public double efficiency = 1;                       // fraction of requested spending that could be paid last tick
    public final Set<String> researched = new HashSet<>();
    public int supplyUsed; public int supplyCap;
    /** Lost its commander (BAR rule: the commander is your life). */
    public boolean defeated;
    /** What this army has learned about fighting each kind of enemy. */
    public final Tactics tactics;

    public Team(int id, String race) {
        this.id = id; this.race = race; this.tactics = new Tactics(id * 7919L + 17);
        metal = num("start_metal"); energy = num("start_energy");
        metalStorage = energyStorage = num("base_storage");
        supplyCap = (int) num("supply_cap");
    }
    static double num(String id) { return Double.parseDouble(EconomyDef.byId(id).value()); }
    public boolean has(String techId) { return techId == null || techId.equals("none") || researched.contains(techId); }
    public boolean tierUnlocked(int tier) {
        if (tier <= 1) return true;
        for (TechDef t : TechDef.ALL)
            if (t.race().equals(race) && t.tier() == tier && t.id().endsWith("_tech_t" + tier)) return researched.contains(t.id());
        return false;
    }
}
