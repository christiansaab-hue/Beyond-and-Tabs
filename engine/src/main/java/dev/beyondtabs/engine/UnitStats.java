package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.BodyDef;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.engine.gen.WeaponDef;

/**
 * A unit's physical and combat numbers. In the mod these come from the player's own TABS install at runtime (the unit's
 * blueprint and weapon); {@link #fallback} gives neutral values from our own sheets for benches and unreadable units.
 */
public record UnitStats(float hp, float mass, float speedMult, float size, WeaponDef weapon) {
    public static UnitStats fallback(UnitDef d) {
        BodyDef b = BodyDef.byId(d.physicsProfile());
        // Without TABS numbers every unit of a body type would be identical, so a 400-metal hero would be no better than
        // a 35-metal recruit. Scale health and hit strength with cost instead (Lanchester's square law: an army's strength
        // goes with count^2 x health x damage, so ~cost^0.8 on each keeps big units worth their price without making
        // them unbeatable).
        float m = (float) Math.max(.5, Math.min(8, Math.pow(d.metal() / referenceCost(d.physicsProfile()), .8)));
        WeaponDef w = WeaponDef.byId(d.weaponClass());
        // splash and volley weapons already hit many targets; their per-hit damage grows more slowly
        float dm = w.aoe() > 0 || w.count() > 1 ? (float) Math.sqrt(m) : m;
        WeaponDef scaled = new WeaponDef(w.id(), w.kind(), w.range(), Math.round(w.damage() * dm), w.cooldown(), w.aoe(), w.knockback(), w.projectile(), w.gravity(), w.count(), w.speed());
        return new UnitStats((float) b.hpBase() * m, (float) b.mass(), 1f, 1f, scaled);
    }

    /** Typical cost of a unit with this body: 50 metal for foot soldiers, else the cheapest unit using it. */
    static double referenceCost(String body) {
        if (body.equals("humanoid")) return 50;
        double min = Double.MAX_VALUE;
        for (UnitDef u : UnitDef.ALL) if (u.physicsProfile().equals(body) && u.metal() > 0) min = Math.min(min, u.metal());
        return min == Double.MAX_VALUE ? 50 : min;
    }
}
