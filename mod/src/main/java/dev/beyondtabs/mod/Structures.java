package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
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
    private final java.util.Random fxRng = new java.util.Random();

    static final class State {
        int bucket = -1, level = -1, damage = -1, baseY; boolean scaffolded, prepared;
        final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        final List<BlockPos> scaffold = new ArrayList<>();
        /** Ground blocks (y = -1 in the plan: paths, floors, farmland) we replaced, with what was there before. */
        final Map<BlockPos, BlockState> ground = new HashMap<>();
        float smokeClock;
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
        for (BlockPos pos : st.placed.keySet()) match.level.setBlock(pos, st.ground.getOrDefault(pos, Blocks.AIR.defaultBlockState()), 3);
        st.scaffold.clear(); st.placed.clear(); st.ground.clear(); st.scaffolded = false;
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

    /** Wall banners in DyeColor order (white, orange, magenta, light blue, yellow, lime, pink, gray, light gray, cyan, purple, blue, brown, green, red, black). */
    static final Block[] WALL_BANNERS = {Blocks.WHITE_WALL_BANNER, Blocks.ORANGE_WALL_BANNER, Blocks.MAGENTA_WALL_BANNER, Blocks.LIGHT_BLUE_WALL_BANNER,
            Blocks.YELLOW_WALL_BANNER, Blocks.LIME_WALL_BANNER, Blocks.PINK_WALL_BANNER, Blocks.GRAY_WALL_BANNER, Blocks.LIGHT_GRAY_WALL_BANNER,
            Blocks.CYAN_WALL_BANNER, Blocks.PURPLE_WALL_BANNER, Blocks.BLUE_WALL_BANNER, Blocks.BROWN_WALL_BANNER, Blocks.GREEN_WALL_BANNER,
            Blocks.RED_WALL_BANNER, Blocks.BLACK_WALL_BANNER};

    /** The banner colour closest to an RGB team colour. */
    static Block bannerFor(int rgb) {
        int best = 0; long bd = Long.MAX_VALUE;
        for (DyeColor c : DyeColor.values()) {
            int t = c.getTextColor();
            long dr = ((t >> 16) & 255) - ((rgb >> 16) & 255), dg = ((t >> 8) & 255) - ((rgb >> 8) & 255), db = (t & 255) - (rgb & 255);
            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bd) { bd = d; best = c.ordinal(); }
        }
        return WALL_BANNERS[best];
    }

    /** Team-coloured version of a banner (every banner in a plan takes the team's lobby colour). */
    BlockState teamBanner(BlockState s, int team) {
        if (!(s.getBlock() instanceof WallBannerBlock)) return s;
        return bannerFor(match.colorOf(team)).defaultBlockState().setValue(WallBannerBlock.FACING, s.getValue(WallBannerBlock.FACING));
    }

    /**
     * Called every 5 server ticks. Construction places blocks continuously bottom-up (2% steps) with placing sounds
     * and dust; damage knocks blocks out (top-weighted, repairs put them back) and makes the building smoke and burn;
     * destruction collapses it to rubble, and a power plant that detonates goes up in a real-looking explosion.
     */
    public void update() {
        boolean changed = false;
        for (Building b : match.world.buildings) {
            if (!b.alive) continue;
            State st = states.computeIfAbsent(b.id, k -> new State());
            if (!st.prepared) { prepareSite(b, st); st.prepared = true; changed = true; }
            if (!blocks) continue;
            int bucket = b.upgrading ? 50 : (int) Math.floor(Math.min(1, b.progress) * 50);
            float hpFrac = b.maxHp() <= 0 ? 1 : Math.max(0, b.hp / b.maxHp());
            int damage = b.done() ? (int) Math.floor(Math.max(0, .8f - hpFrac) * 20) : 0;   // 0..16, from 80% hp down
            effects(b, st, hpFrac);
            if (bucket == st.bucket && b.level == st.level && damage == st.damage) continue;
            int x0 = (int) Math.floor(b.x - b.hw), z0 = (int) Math.floor(b.z - b.hh);
            List<Architecture.Part> parts = Architecture.plan(b, b.level);
            Map<BlockPos, BlockState> want = new LinkedHashMap<>();
            for (Architecture.Part q : parts) want.put(new BlockPos(x0 + q.x(), st.baseY + q.y(), z0 + q.z()), teamBanner(q.s(), b.team));
            // a level change: blocks the new plan no longer has go away (ground goes back to what it was)
            if (st.level >= 0 && b.level != st.level)
                for (var e : new ArrayList<>(st.placed.entrySet()))
                    if (!want.containsKey(e.getKey())) { match.level.setBlock(e.getKey(), st.ground.getOrDefault(e.getKey(), Blocks.AIR.defaultBlockState()), 3); st.placed.remove(e.getKey()); }
            boolean done = b.done() || b.upgrading;
            int n = (int) Math.ceil(want.size() * (done ? 1.0 : Math.max(.04, bucket / 50.0)));
            // battle damage: a top-weighted, stable selection of blocks is knocked out
            java.util.Set<BlockPos> broken = brokenSet(want, st.baseY, damage);
            int i = 0, fx = 0;
            for (var e : want.entrySet()) {
                if (i++ >= n) break;
                BlockPos pos = e.getKey();
                BlockState cur = match.level.getBlockState(pos), target = broken.contains(pos) ? Blocks.AIR.defaultBlockState() : e.getValue();
                if (cur == target) continue;
                if (pos.getY() < st.baseY && !st.ground.containsKey(pos)) st.ground.put(pos, cur);
                if (target.isAir() && !cur.isAir() && fx < 3) { match.level.levelEvent(2001, pos, Block.getId(cur)); fx++; }   // break particles + sound
                match.level.setBlock(pos, target, 3);
                if (!target.isAir()) {
                    st.placed.put(pos, target);
                    if (!done && fx < 3 && st.bucket >= 0) { placeFx(pos, target); fx++; }
                }
            }
            // let fences, walls, panes, stairs and bars connect to their new neighbours
            for (BlockPos pos : st.placed.keySet()) {
                BlockState cur = match.level.getBlockState(pos), upd = Block.updateFromNeighbourShapes(cur, match.level, pos);
                if (upd != cur) match.level.setBlock(pos, upd, 2);
            }
            scaffolding(b, st, want, done);
            st.bucket = bucket; st.level = b.level; st.damage = damage; changed = true;
        }
        // buildings that died or were removed: collapse to rubble (a detonating power plant goes up first)
        for (var it = states.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Building b = find(e.getKey());
            if (b != null && b.alive) continue;
            State st = e.getValue();
            if (b != null && blocks) collapseFx(b, st);
            for (BlockPos pos : st.scaffold) match.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos pos : st.placed.keySet()) {
                if (st.ground.containsKey(pos)) { match.level.setBlock(pos, Math.floorMod(pos.getX() * 7 + pos.getZ() * 3, 3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : st.ground.get(pos), 3); continue; }
                int r = Math.floorMod(pos.getX() * 31 + pos.getZ() * 17, 5);
                BlockState rubble = pos.getY() == st.baseY ? (r == 0 ? Blocks.GRAVEL.defaultBlockState() : r == 1 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState())
                        : pos.getY() == st.baseY + 1 && r == 2 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.AIR.defaultBlockState();
                match.level.setBlock(pos, rubble, 3);
            }
            it.remove(); changed = true;
        }
        if (changed) match.terrain.invalidate();
    }

    /** Blocks knocked out at a damage step (0..16): top-weighted, stable, never the ground layer. */
    static java.util.Set<BlockPos> brokenSet(Map<BlockPos, BlockState> want, int baseY, int damage) {
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        if (damage <= 0) return out;
        int hi = baseY; for (BlockPos p : want.keySet()) hi = Math.max(hi, p.getY());
        final int top = hi;
        List<BlockPos> cand = new ArrayList<>();
        for (BlockPos p : want.keySet()) if (p.getY() > baseY) cand.add(p);
        cand.sort((a, c) -> Double.compare(score(c, baseY, top), score(a, baseY, top)));
        int k = Math.min(cand.size(), (int) (cand.size() * damage * .025));   // up to 40% of the building at 0 hp
        for (int i = 0; i < k; i++) out.add(cand.get(i));
        return out;
    }

    static double score(BlockPos p, int baseY, int top) {
        double h = (p.getY() - baseY) / (double) Math.max(1, top - baseY);
        return h * .6 + (Math.floorMod(p.getX() * 73856093 ^ p.getY() * 19349663 ^ p.getZ() * 83492791, 1000) / 1000.0) * .4;
    }

    void placeFx(BlockPos pos, BlockState s) {
        var sound = s.getSoundType();
        match.level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, .5f, sound.getPitch() * .9f);
        match.level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, s),
                pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 6, .3, .3, .3, .05);
    }

    /** Smoke from damaged buildings, flames when nearly destroyed, chimney-like puffs from the roofline. */
    void effects(Building b, State st, float hpFrac) {
        if (!b.done() || hpFrac > .55f || st.placed.isEmpty()) return;
        if ((st.smokeClock += .25f) < (hpFrac < .25f ? .25f : .75f)) return;
        st.smokeClock = 0;
        int top = st.baseY; for (BlockPos p : st.placed.keySet()) top = Math.max(top, p.getY());
        double x = b.x + (fxRng.nextFloat() - .5f) * b.fw * .8, z = b.z + (fxRng.nextFloat() - .5f) * b.fh * .8;
        match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, x, top + 1, z, 1, .2, .1, .2, .01);
        if (hpFrac < .25f) {
            match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, x, top - 1, z, 6, .6, .6, .6, .02);
            match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, x, top, z, 1, .3, .3, .3, 0);
        }
    }

    void collapseFx(Building b, State st) {
        double y = st.baseY + 2;
        int cnt = 0;
        for (BlockPos p : st.placed.keySet()) if (cnt++ % 9 == 0 && cnt < 120) match.level.levelEvent(2001, p, Block.getId(st.placed.get(p)));
        match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, b.x, y, b.z, 40, b.hw * .6, 2, b.hh * .6, .05);
        match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, b.x, y + 2, b.z, 12, b.hw * .5, 1, b.hh * .5, .02);
        match.level.playSound(null, BlockPos.containing(b.x, y, b.z), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.BLOCKS, b.detonated ? 4f : 1.2f, b.detonated ? .6f : .9f);
        if (b.detonated) {   // advanced power plant: a fireball, a shock ring and a column of smoke
            match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, b.x, y + 1, b.z, 3, 1.5, 1, 1.5, 0);
            match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, b.x, y + 1, b.z, 160, b.def.blastRadius() * .4, 1.5, b.def.blastRadius() * .4, .25);
            match.level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, b.x, y + 4, b.z, 80, 2, 4, 2, .05);
        }
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
            // gardens need soil: inside the footprint the top layer becomes grass whatever the biome had there
            BlockPos top = new BlockPos(bx, st.baseY - 1, bz);
            if (inside && !match.level.getBlockState(top).is(net.minecraft.tags.BlockTags.DIRT)) match.level.setBlock(top, grass, 3);
            for (int y = st.baseY; y < st.baseY + 16; y++) {
                BlockPos p = new BlockPos(bx, y, bz);
                if (!match.level.getBlockState(p).isAir()) match.level.setBlock(p, air, 3);
            }
        }
    }
}
