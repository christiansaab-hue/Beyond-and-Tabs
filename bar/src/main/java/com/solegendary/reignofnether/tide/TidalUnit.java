package com.solegendary.reignofnether.tide;

/**
 * Optional per-unit hooks into the Tides pass (TidesServerEvents), for the Tidewrought units whose kit leans on water
 * (design/tidewrought_plan.md slice 1). Every tidal unit already gets the faction-wide buff; a unit implements this
 * only to add to it. Both hooks are called from the 10-tick pass the unit is visited in anyway, so they cost nothing
 * extra per tick and send no packets.
 */
public interface TidalUnit {

    /** Extra HP healed per Tides pass while wet, on top of {@link TidesServerEvents#REGEN_PER_PASS}. */
    default float tidesRegenBonus() {
        return 0f;
    }

    /**
     * The unit just stepped out of water or off a tidepool (seen by the pass; up to {@link TidesServerEvents#PASS_TICKS}
     * ticks late). {@code gameTime} is the level's game time of that pass.
     */
    default void onLeftWater(long gameTime) { }
}
