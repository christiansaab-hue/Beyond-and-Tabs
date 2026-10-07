package dev.beyondtabs.mod.client;

/**
 * The shared art direction. One warm sun, a cool sky and a warm ground bounce; shadow sides drift towards blue, lit
 * sides towards warm white; every model gets a thin ink outline. Colours are rich but not pastel, so units and
 * buildings sit well on Minecraft's textured ground and still read at RTS zoom.
 */
final class Look {
    private Look() { }

    /** Sun from the upper left-front, like a late-morning battlefield. */
    static final float SUN_X, SUN_Y, SUN_Z;
    static {
        float x = -.5f, y = .75f, z = .42f, l = (float) Math.sqrt(x * x + y * y + z * z);
        SUN_X = x / l; SUN_Y = y / l; SUN_Z = z / l;
    }

    // ---- palette (0xRRGGBB) ----
    static final int INK = 0x16141A;
    static final int TEAM_BLUE = 0x3B6FD8, TEAM_RED = 0xD23F36, TEAM_GREY = 0x8A8A8A;
    static final int SKIN = 0xF4CDA6, SKIN_DARK = 0xD3A27A, EYE = 0x17131A, EYE_SHINE = 0xFFFFFF, BLUSH = 0xE0907C;
    static final int WOOD = 0x8A5C38, WOOD_DARK = 0x5A3A24, WOOD_LIGHT = 0xB7875A, THATCH = 0xCFA45A, THATCH_DARK = 0xA07C3E;
    static final int STONE = 0xB9AE98, STONE_DARK = 0x857B6A, STONE_COOL = 0xA4A9B4, STONE_COOL_DARK = 0x6E7380;
    static final int PLASTER = 0xEAE0CA, SLATE = 0x45506E, TILE_RED = 0xA4442F, CLAY = 0xB8693A, GOLD = 0xE2B43C;
    static final int IRON = 0x8A919C, IRON_DARK = 0x4D525B, LEATHER = 0x7A4E31, CLOTH = 0xE4D6B8, BONE = 0xEDE3CB;
    static final int ROPE = 0xC0A472, DIRT = 0x7A5C40, FIRE = 0xFF9A2E, GLOW = 0x9FE6FF, GREEN = 0x5FAF4A, ORE = 0x6FD0E6;
    static final int WINDOW = 0xFFC66A, BRONZE = 0xB07A36, COPPER = 0x5FA08A, MARBLE = 0xEDE8DE;

    static final int[] TEAMS = {TEAM_BLUE, TEAM_RED, 0x4FB04A, 0xE0B83A, 0x9A5AD8, 0x3AB8C8};

    /** Team colours are the same for everyone (team 0 blue, team 1 red, ...), matching the banners on block buildings. */
    static int team(int team, int myTeam) { return team < 0 ? TEAM_GREY : TEAMS[team % TEAMS.length]; }
    static int teamArgb(int team) { return 0xFF000000 | team(team, -1); }

    static final int SHADOW_TINT = 0x2E3A58, LIT_TINT = 0xFFF1D8;

    /** Lit colour of a surface with normal n; `contact` (0..1) darkens near the ground. */
    static int shade(int c, float nx, float ny, float nz, float contact) {
        float d = nx * SUN_X + ny * SUN_Y + nz * SUN_Z;
        float diff = Math.max(0, (d + .2f) / 1.2f);
        float sky = .5f + .5f * ny;
        float l = (.44f + .52f * diff + .16f * sky) * contact;
        int lit = mix(c, LIT_TINT, Math.max(0, d) * .10f);
        int col = mix(lit, SHADOW_TINT, (1 - diff) * .20f);
        int r = Math.min(255, Math.round(((col >> 16) & 255) * l)), g = Math.min(255, Math.round(((col >> 8) & 255) * l)), b = Math.min(255, Math.round((col & 255) * l));
        return (r << 16) | (g << 8) | b;
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
    /** Small deterministic colour variation so repeated parts don't look copy-pasted. */
    static int vary(int c, int seed, float amt) { float v = (float) Math.sin(seed * 12.9898) * 43758.5453f; v -= Math.floor(v); return v < .5f ? darker(c, (.5f - v) * amt * 2) : lighter(c, (v - .5f) * amt * 2); }
}
