package dev.beyondtabs.engine;

/** The ground the simulation stands on. In Minecraft this is backed by the world's blocks; in benches by a function. */
public interface Terrain {
    /** Height of the walkable surface (top of the highest solid block) at x,z, in blocks. */
    float groundY(float x, float z);

    /** Depth of water above the ground at x,z (0 = dry). Units wade slowly; ragdolls float a little. */
    default float waterDepth(float x, float z) { return 0f; }

    Terrain FLAT = (x, z) -> 0f;
}
