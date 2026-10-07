package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.TechDef;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.ArrayDeque;

public final class Building {
    public final int id; public final int team; public final BuildingDef def; public final float x, z;
    public int level = 1; public float hp; public boolean alive = true;
    /** 0..1 while being constructed or upgraded; 1 = done. */
    public float progress; public boolean upgrading;
    public final ArrayDeque<UnitDef> queue = new ArrayDeque<>(); public boolean repeat; public float rallyX, rallyZ;
    public UnitDef producing; public float produceWork;
    public TechDef researching; public float researchLeft;

    Building(int id, int team, BuildingDef def, float x, float z, boolean prebuilt) {
        this.id = id; this.team = team; this.def = def; this.x = x; this.z = z; this.hp = def.hp();
        this.progress = prebuilt ? 1 : 0; rallyX = x; rallyZ = z + 6;
    }
    public boolean done() { return progress >= 1 && !upgrading; }
    /** The sheet's per-level value (income, build power, dps...). */
    public double value() {
        String[] v = def.valuePerLevel().split(",");
        return Double.parseDouble(v[Math.min(level, v.length) - 1].trim());
    }
    public boolean canUpgrade() { return done() && level < def.levels(); }
}
