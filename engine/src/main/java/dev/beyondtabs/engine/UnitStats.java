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
        return new UnitStats((float) b.hpBase(), (float) b.mass(), 1f, 1f, WeaponDef.byId(d.weaponClass()));
    }
}
