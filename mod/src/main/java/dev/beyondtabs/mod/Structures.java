package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Buildings in the world. Two styles (/bt style):
 * <ul>
 * <li><b>blocks</b> (default, Reign of Nether style): each building is a real Minecraft block structure from its race's
 * plan (see {@link Architecture}). Blocks rise bottom-up as construction progresses (10% steps) inside a scaffolding
 * frame; an upgrade swaps in the next level's plan; a destroyed building collapses to rubble. Banners take the team
 * colour (team 0 blue, team 1 red).</li>
 * <li><b>models</b>: the client draws smooth low-poly models instead (client.BuildingModels); the server only levels
 * the site.</li>
 * </ul>
 * Either way the ground under a building stays the original terrain for the physics, so ragdolls don't climb walls.
 */
public final class Structures {
    private final Match match;
    private final Map<Integer, State> states = new HashMap<>();
    /** Original ground height of every column a building covers (the physics keeps using it). */
    final Map<Long, Float> originalGround = new HashMap<>();
    private boolean blocks = true;

    static final class State {
        int bucket = -1, level = -1, baseY; boolean scaffolded, prepared;
        final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        final List<BlockPos> scaffold = new ArrayList<>();
    }

    Structures(Match match) { this.match = match; }

    public boolean blocks() { return blocks; }

    /** Switches style; going to models removes the placed blocks (sites stay levelled). */
    public void setBlocks(boolean on) {
        if (on == blocks) return;
        blocks = on;
        for (State st : states.values()) {
            if (!on) { clear(st); }
            st.bucket = -1; st.level = -1;
        }
        match.terrain.invalidate();
    }

    void clear(State st) {
        for (BlockPos pos : st.scaffold) match.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos pos : st.placed.keySet()) match.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        st.scaffold.clear(); st.placed.clear(); st.scaffolded = false;
    }

    /** Simple palette structures for races that don't have hand-designed architecture yet. */
    static void generic(Architecture.Plan p, Building b, int level) {
        BlockState base, wall, roof, light;
        switch (b.def.race()) {
            case "gunpowder" -> { base = Blocks.BRICKS.defaultBlockState(); wall = Blocks.DARK_OAK_PLANKS.defaultBlockState(); roof = Blocks.BLACK_WOOL.defaultBlockState(); light = Blocks.LANTERN.defaultBlockState(); }
            case "fantasy" -> { base = Blocks.DEEPSLATE_BRICKS.defaultBlockState(); wall = Blocks.PURPUR_BLOCK.defaultBlockState(); roof = Blocks.PURPLE_WOOL.defaultBlockState(); light = Blocks.SOUL_LANTERN.defaultBlockState(); }
            default -> { base = Blocks.POLISHED_BLACKSTONE.defaultBlockState(); wall = Blocks.BLACK_CONCRETE.defaultBlockState(); roof = Blocks.CYAN_CONCRETE.defaultBlockState(); light = Blocks.SEA_LANTERN.defaultBlockState(); }
        }
        int w = p.w, d = p.d, h = 2 + level;
        p.fill(0, 0, 0, w - 1, 0, d - 1, base).walls(0, 1, 0, w - 1, h, d - 1, wall).fill(0, h + 1, 0, w - 1, h + 1, d - 1, roof);
        p.corners(0, 0, w - 1, d - 1, h + 2, h + 2, light);
        if (w >= 5) p.clearBox(w / 2 - 1, 1, d - 1, w / 2 + 1, 2, d - 1);
    }

    /** Team-coloured version of a banner (every banner in a plan is recoloured). */
    static BlockState teamBanner(BlockState s, int team) {
        if (!(s.getBlock() instanceof WallBannerBlock)) return s;
        Block b = team == 0 ? Blocks.BLUE_WALL_BANNER : team == 1 ? Blocks.RED_WALL_BANNER : Blocks.WHITE_WALL_BANNER;
        return b.defaultBlockState().setValue(WallBannerBlock.FACING, s.getValue(WallBannerBlock.FACING));
    }

    /** Called every few server ticks; only buildings whose 10% step, level or state changed are touched. */
    public void update() {
        boolean changed = false;
        for (Building b : match.world.buildings) {
            if (!b.alive) continue;
            State st = states.computeIfAbsent(b.id, k -> new State());
            if (!st.prepared) { prepareSite(b, st); st.prepared = true; changed = true; }
            if (!blocks) continue;
            int bucket = b.upgrading ? 10 : (int) Math.floor(Math.min(1, b.progress) * 10);
            if (bucket == st.bucket && b.level == st.level) continue;
            int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
            List<Architecture.Part> parts = Architecture.plan(b, b.level);
            Map<BlockPos, BlockState> want = new LinkedHashMap<>();
            for (Architecture.Part q : parts) want.put(new BlockPos(x0 + q.x(), st.baseY + q.y(), z0 + q.z()), teamBanner(q.s(), b.team));
            // a level change: blocks the new plan no longer has go away (e.g. a roof that moved up)
            if (st.level >= 0 && b.level != st.level)
                for (var e : new ArrayList<>(st.placed.entrySet()))
                    if (!want.containsKey(e.getKey())) { match.level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), 3); st.placed.remove(e.getKey()); }
            boolean done = b.done() || b.upgrading;
            int n = (int) Math.ceil(want.size() * (done ? 1.0 : Math.max(.05, bucket / 10.0)));
            int i = 0;
            for (var e : want.entrySet()) {
                if (i++ >= n) break;
                BlockState cur = match.level.getBlockState(e.getKey());
                if (cur != e.getValue()) { match.level.setBlock(e.getKey(), e.getValue(), 3); st.placed.put(e.getKey(), e.getValue()); }
            }
            // let fences, walls, panes and bars connect to their new neighbours
            for (BlockPos pos : st.placed.keySet()) {
                BlockState cur = match.level.getBlockState(pos), upd = Block.updateFromNeighbourShapes(cur, match.level, pos);
                if (upd != cur) match.level.setBlock(pos, upd, 2);
            }
            scaffolding(b, st, want, done);
            st.bucket = bucket; st.level = b.level; changed = true;
        }
        // buildings that died or were removed: collapse to rubble
        for (var it = states.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Building b = find(e.getKey());
            if (b != null && b.alive) continue;
            State st = e.getValue();
            for (BlockPos pos : st.scaffold) match.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos pos : st.placed.keySet()) {
                int r = Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 5);
                BlockState rubble = pos.getY() == st.baseY ? (r == 0 ? Blocks.GRAVEL.defaultBlockState() : r == 1 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState())
                        : pos.getY() == st.baseY + 1 && r == 2 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.AIR.defaultBlockState();
                match.level.setBlock(pos, rubble, 3);
            }
            it.remove(); changed = true;
        }
        if (changed) match.terrain.invalidate();
    }

    /** Scaffolding at the corners while a building is going up; removed once it is finished. */
    void scaffolding(Building b, State st, Map<BlockPos, BlockState> want, boolean done) {
        if (done) {
            for (BlockPos pos : st.scaffold) if (!want.containsKey(pos)) match.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            st.scaffold.clear(); st.scaffolded = false; return;
        }
        if (st.scaffolded || b.fw < 3 || b.fh < 3) return;
        int top = 0; for (BlockPos p : want.keySet()) top = Math.max(top, p.getY() - st.baseY);
        int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
        int[][] corners = {{x0, z0}, {x0 + b.fw - 1, z0}, {x0, z0 + b.fh - 1}, {x0 + b.fw - 1, z0 + b.fh - 1}};
        for (int[] c : corners) for (int y = 0; y <= Math.min(top, 12); y++) {
            BlockPos pos = new BlockPos(c[0], st.baseY + y, c[1]);
            if (match.level.getBlockState(pos).isAir()) { match.level.setBlock(pos, Blocks.SCAFFOLDING.defaultBlockState(), 3); st.scaffold.add(pos); }
        }
        st.scaffolded = true;
    }

    Building find(int id) { for (Building b : match.world.buildings) if (b.id == id) return b; return null; }

    /** Levels the site once: fills dips (grass on top), clears plants and terrain above, remembers the original ground. */
    void prepareSite(Building b, State st) {
        int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
        st.baseY = (int) Math.floor(match.terrain.groundY(b.x, b.z));
        BlockState dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= b.fw; x++) for (int z = -1; z <= b.fh; z++) {
            int bx = x0 + x, bz = z0 + z;
            boolean inside = x >= 0 && z >= 0 && x < b.fw && z < b.fh;
            float g = match.terrain.groundY(bx + .5f, bz + .5f);
            if (inside) originalGround.put(((long) bx << 32) ^ (bz & 0xffffffffL), g);
            if (!inside && Math.abs(g - st.baseY) > 2) continue;   // the one-block margin only smooths small steps
            for (int y = (int) g; y < st.baseY; y++) match.level.setBlock(new BlockPos(bx, y, bz), y == st.baseY - 1 ? grass : dirt, 3);
            for (int y = st.baseY; y < st.baseY + 16; y++) {
                BlockPos p = new BlockPos(bx, y, bz);
                if (!match.level.getBlockState(p).isAir()) match.level.setBlock(p, air, 3);
            }
        }
    }
}
