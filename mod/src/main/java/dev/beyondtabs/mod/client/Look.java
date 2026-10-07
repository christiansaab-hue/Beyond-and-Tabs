package dev.beyondtabs.mod.client;

/**
 * The shared art direction: one soft sun, one ambient, pastel flat colours. Units, buildings, props and shadows all
 * shade through here so they read as one low-poly toy world sitting on the Minecraft terrain.
 */
final class Look {
    private Look() { }

    /** Sun from the upper left-front, like a late-morning battlefield. */
    static final float SUN_X, SUN_Y, SUN_Z;
    static {
        float x = -.45f, y = .82f, z = .36f, l = (float) Math.sqrt(x * x + y * y + z * z);
        SUN_X = x / l; SUN_Y = y / l; SUN_Z = z / l;
    }

    // ---- palette (0xRRGGBB) ----
    static final int TEAM_BLUE = 0x4A7FE0, TEAM_RED = 0xE0524A, TEAM_GREY = 0x9A9A9A;
    static final int SKIN = 0xF1C9A1, SKIN_DARK = 0xC99872, EYE = 0x1E1A1A, BLUSH = 0xE8A08E;
    static final int WOOD = 0x9C6B43, WOOD_DARK = 0x6E4A2E, WOOD_LIGHT = 0xC49466, THATCH = 0xDDB66A, THATCH_DARK = 0xB88E4A;
    static final int STONE = 0xC9C1AE, STONE_DARK = 0x9C9483, STONE_COOL = 0xB6BAC4, STONE_COOL_DARK = 0x8C909C;
    static final int PLASTER = 0xF0E7D3, SLATE = 0x5F6F9C, TILE_RED = 0xB9553D, CLAY = 0xC8763A, GOLD = 0xE8C24A;
    static final int IRON = 0x8E949C, IRON_DARK = 0x5C6168, LEATHER = 0x8A5A3A, CLOTH = 0xE8DCC0, BONE = 0xEFE6CF;
    static final int ROPE = 0xC9AE7C, DIRT = 0x8D6E4E, FIRE = 0xFFA43A, GLOW = 0x9FE6FF, GREEN = 0x6FBF5A, ORE = 0x7FC8D8;

    static int team(int team, int myTeam) {
        if (team < 0) return TEAM_GREY;
        if (myTeam < 0) return team == 0 ? TEAM_RED : TEAM_BLUE;
        return team == myTeam ? TEAM_BLUE : TEAM_RED;
    }

    /** Light factor for a face normal: soft ambient + sun + a little sky fill from above. */
    static float light(float nx, float ny, float nz) {
        float d = nx * SUN_X + ny * SUN_Y + nz * SUN_Z;
        float l = .60f + .40f * Math.max(0, d) + .07f * Math.max(0, ny) - .06f * Math.max(0, -ny);
        return Math.min(1.08f, l);
    }

    static float r(int c) { return ((c >> 16) & 255) / 255f; }
    static float g(int c) { return ((c >> 8) & 255) / 255f; }
    static float b(int c) { return (c & 255) / 255f; }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int darker(int c, float f) { return mix(c, 0, f); }
    static int lighter(int c, float f) { return mix(c, 0xFFFFFF, f); }
    static int grey(int c, float f) { int l = Math.round(((c >> 16) & 255) * .3f + ((c >> 8) & 255) * .59f + (c & 255) * .11f); return mix(c, (l << 16) | (l << 8) | l, f); }
}
