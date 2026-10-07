package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws every engine building as a Minecraft block structure in its race's style. Blocks appear bottom-up as construction
 * progresses (10% steps), the structure grows with each level, and a destroyed building collapses to rubble. The ground
 * under a structure stays the original terrain for the physics, so ragdolls never "climb" a wall they bump into.
 */
public final class Structures {
    private final Match match;
    private final Map<Integer, State> states = new HashMap<>();
    /** Original ground height of every column a structure covers (the physics keeps using it). */
    final Map<Long, Float> originalGround = new HashMap<>();

    static final class State { int bucket = -1, level = -1; final List<BlockPos> placed = new ArrayList<>(); int baseY; }

    Structures(Match match) { this.match = match; }

    record Palette(BlockState base, BlockState wall, BlockState roof, BlockState accent, BlockState light, BlockState pillar) { }

    static Palette palette(String race) {
        return switch (race) {
            case "kingdoms" -> new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(),
                    Blocks.BLUE_TERRACOTTA.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState());
            case "gunpowder" -> new Palette(Blocks.BRICKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.BLACK_WOOL.defaultBlockState(),
                    Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.LANTERN.defaultBlockState(), Blocks.DARK_OAK_LOG.defaultBlockState());
            case "fantasy" -> new Palette(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.PURPLE_WOOL.defaultBlockState(),
                    Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.SOUL_LANTERN.defaultBlockState(), Blocks.CRIMSON_STEM.defaultBlockState());
            case "neon" -> new Palette(Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.CYAN_CONCRETE.defaultBlockState(),
                    Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.CYAN_CONCRETE.defaultBlockState());
            default -> new Palette(Blocks.COBBLESTONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(),
                    Blocks.BONE_BLOCK.defaultBlockState(), Blocks.TORCH.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState());
        };
    }

    record Part(int dx, int dy, int dz, BlockState state) { }

    /** Full shape of a building at a level, ordered bottom-up so partial construction looks right. */
    static List<Part> shape(Building b, int level) {
        Palette p = palette(b.def.race());
        List<Part> out = new ArrayList<>();
        int w = b.fw, d = b.fh;
        switch (b.def.effect()) {
            case "metal_per_s" -> {
                ring(out, w, d, 0, p.base); out.add(new Part(1, 0, 1, Blocks.IRON_BLOCK.defaultBlockState()));
                out.add(new Part(1, 1, 1, Blocks.HOPPER.defaultBlockState()));
                corners(out, w, d, 1, p.accent);
                if (level >= 2) out.add(new Part(1, 2, 1, Blocks.IRON_BARS.defaultBlockState()));
                if (level >= 3) { out.add(new Part(1, 3, 1, Blocks.GOLD_BLOCK.defaultBlockState())); corners(out, w, d, 2, p.accent); }
            }
            case "energy_per_s" -> {
                ring(out, w, d, 0, p.base);
                out.add(new Part(1, 0, 1, b.def.race().equals("ancient_world") ? Blocks.CAMPFIRE.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState()));
                for (int y = 1; y <= level; y++) corners(out, w, d, y, p.pillar);
                corners(out, w, d, level + 1, p.light);
            }
            case "storage" -> { floor(out, w, d, 0, p.base); for (int y = 1; y <= level + 1; y++) ring(out, w, d, y, Blocks.BARREL.defaultBlockState()); }
            case "energy_to_metal_per_s" -> {
                floor(out, w, d, 0, p.base); ring(out, w, d, 1, Blocks.FURNACE.defaultBlockState()); out.add(new Part(1, 1, 1, Blocks.BLAST_FURNACE.defaultBlockState()));
                if (level >= 2) ring(out, w, d, 2, p.accent);
            }
            case "unlock_tier" -> {
                floor(out, w, d, 0, p.base);
                for (int y = 1; y <= 2 + level; y++) walls(out, w, d, y, y % 2 == 0 ? p.accent : p.wall, true);
                corners(out, w, d, 3 + level, p.light);
                out.add(new Part(w / 2, 1, d / 2, Blocks.ENCHANTING_TABLE.defaultBlockState()));
                if (level >= 2) { out.add(new Part(w / 2 - 1, 1, d / 2, Blocks.BOOKSHELF.defaultBlockState())); out.add(new Part(w / 2 + 1, 1, d / 2, Blocks.BOOKSHELF.defaultBlockState())); }
                if (level >= 3) out.add(new Part(w / 2, 2, d / 2, Blocks.AMETHYST_CLUSTER.defaultBlockState()));
            }
            case "ranged_dps" -> {
                int h = 3 + level;
                for (int y = 0; y < h; y++) corners(out, w, d, y, p.pillar);
                floor(out, w, d, h, p.wall); corners(out, w, d, h + 1, p.light);
            }
            case "hp" -> { for (int y = 0; y < 1 + level; y++) out.add(new Part(0, y, 0, p.base)); }
            case "build_power" -> {
                int h = 3 + b.def.tier();
                floor(out, w, d, 0, p.base);
                for (int y = 1; y <= h; y++) {
                    walls(out, w, d, y, y == h ? p.accent : p.wall, false);
                    corners(out, w, d, y, p.pillar);
                }
                // doorway toward +z (the default rally side), 3 wide, 3 high
                out.removeIf(q -> q.dz == d - 1 && Math.abs(q.dx - w / 2) <= 1 && q.dy >= 1 && q.dy <= 3);
                floor(out, w, d, h + 1, p.roof);
                for (int l = 2; l <= level; l++) corners(out, w, d, h + l, p.light);
            }
            default -> floor(out, w, d, 0, p.base);
        }
        out.sort((a, c) -> Integer.compare(a.dy, c.dy));
        return out;
    }

    static void floor(List<Part> o, int w, int d, int y, BlockState s) { for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) o.add(new Part(x, y, z, s)); }
    static void ring(List<Part> o, int w, int d, int y, BlockState s) { for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (x == 0 || z == 0 || x == w - 1 || z == d - 1) o.add(new Part(x, y, z, s)); }
    static void walls(List<Part> o, int w, int d, int y, BlockState s, boolean windows) {
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++)
            if ((x == 0 || z == 0 || x == w - 1 || z == d - 1) && !(windows && y == 2 && (x == w / 2 || z == d / 2))) o.add(new Part(x, y, z, s));
    }
    static void corners(List<Part> o, int w, int d, int y, BlockState s) {
        o.add(new Part(0, y, 0, s)); if (w > 1) o.add(new Part(w - 1, y, 0, s));
        if (d > 1) o.add(new Part(0, y, d - 1, s)); if (w > 1 && d > 1) o.add(new Part(w - 1, y, d - 1, s));
    }

    /** Called every server tick (cheap: only buildings whose 10% step or level changed are touched). */
    public void update() {
        boolean changed = false;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Building b : match.world.buildings) {
            seen.add(b.id);
            State st = states.computeIfAbsent(b.id, k -> new State());
            int bucket = b.upgrading ? 10 : (int) Math.floor(Math.min(1, b.progress) * 10);
            if (!b.alive) continue;
            if (bucket == st.bucket && b.level == st.level) continue;
            if (st.level < 0) prepareSite(b, st);
            int level = b.upgrading ? b.level : b.level;
            List<Part> parts = shape(b, level);
            int n = (int) Math.ceil(parts.size() * (b.done() || b.upgrading ? 1.0 : Math.max(.05, bucket / 10.0)));
            int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
            for (int i = 0; i < n; i++) {
                Part q = parts.get(i);
                BlockPos pos = new BlockPos(x0 + q.dx, st.baseY + q.dy, z0 + q.dz);
                if (match.level.getBlockState(pos) != q.state) { match.level.setBlock(pos, q.state, 3); st.placed.add(pos); }
            }
            st.bucket = bucket; st.level = b.level; changed = true;
        }
        // buildings that died or were removed: collapse to rubble
        for (var it = states.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Building b = find(e.getKey());
            if (b != null && b.alive) continue;
            for (BlockPos pos : e.getValue().placed)
                match.level.setBlock(pos, pos.getY() == e.getValue().baseY && (pos.getX() + pos.getZ()) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            it.remove(); changed = true;
        }
        if (changed) match.terrain.invalidate();
    }

    Building find(int id) { for (Building b : match.world.buildings) if (b.id == id) return b; return null; }

    /** Levels the site: foundation under low columns, clears plants/terrain above, remembers the original ground. */
    void prepareSite(Building b, State st) {
        int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
        st.baseY = (int) Math.floor(match.terrain.groundY(b.x, b.z));
        BlockState base = palette(b.def.race()).base;
        for (int x = 0; x < b.fw; x++) for (int z = 0; z < b.fh; z++) {
            int bx = x0 + x, bz = z0 + z;
            float g = match.terrain.groundY(bx + .5f, bz + .5f);
            originalGround.put(((long) bx << 32) ^ (bz & 0xffffffffL), g);
            for (int y = (int) g; y < st.baseY; y++) { BlockPos p = new BlockPos(bx, y, bz); match.level.setBlock(p, base, 3); }
            for (int y = st.baseY; y < st.baseY + 10; y++) { BlockPos p = new BlockPos(bx, y, bz); if (!match.level.getBlockState(p).isAir()) match.level.setBlock(p, Blocks.AIR.defaultBlockState(), 3); }
        }
    }
}
