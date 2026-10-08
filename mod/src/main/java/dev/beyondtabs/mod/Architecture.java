package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Race-specific architecture for every building, as block plans. Coordinates: x across (0..w-1), z deep (0..d-1),
 * y up from the ground; the front (door, rally side) faces +z. Every level adds visible detail.
 * Ancient World = Tribal / Viking / Greek. Kingdoms = Medieval / Dynasty / Renaissance.
 */
final class Architecture {
    record Part(int x, int y, int z, BlockState s) { }

    /** A plan being drawn. Later writes replace earlier ones at the same spot. */
    static final class Plan {
        final int w, d; final java.util.LinkedHashMap<Long, Part> parts = new java.util.LinkedHashMap<>();
        Plan(int w, int d) { this.w = w; this.d = d; }
        static long key(int x, int y, int z) { return ((long) x & 0xfff) | (((long) y & 0xfff) << 12) | (((long) z & 0xfff) << 24); }
        Plan set(int x, int y, int z, BlockState s) {
            if (x < 0 || z < 0 || x >= w || z >= d || y < 0) return this;
            parts.put(key(x, y, z), new Part(x, y, z, s)); return this;
        }
        Plan clear(int x, int y, int z) { parts.remove(key(x, y, z)); return this; }
        Plan fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
            return this;
        }
        Plan clearBox(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) clear(x, y, z);
            return this;
        }
        /** Hollow walls of a box (no floor or ceiling). */
        Plan walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
                if (x == x0 || x == x1 || z == z0 || z == z1) set(x, y, z, s);
            return this;
        }
        Plan pillar(int x, int z, int y0, int y1, BlockState s) { for (int y = y0; y <= y1; y++) set(x, y, z, s); return this; }
        Plan corners(int x0, int z0, int x1, int z1, int y0, int y1, BlockState s) {
            pillar(x0, z0, y0, y1, s); pillar(x1, z0, y0, y1, s); pillar(x0, z1, y0, y1, s); pillar(x1, z1, y0, y1, s); return this;
        }
        /** Gable roof whose ridge runs along z (slopes face west/east). */
        Plan gableZ(int x0, int x1, int z0, int z1, int y, Block stairs, BlockState ridge, BlockState gableFill) {
            int layer = 0;
            for (int lx = x0, rx = x1; lx <= rx; lx++, rx--, layer++) {
                for (int z = z0; z <= z1; z++) {
                    if (lx == rx) set(lx, y + layer, z, ridge);
                    else { set(lx, y + layer, z, stair(stairs, Direction.EAST)); set(rx, y + layer, z, stair(stairs, Direction.WEST)); }
                }
                for (int x = lx + 1; x < rx; x++) { set(x, y + layer, z0, gableFill); set(x, y + layer, z1, gableFill); }
            }
            return this;
        }
        /** Gable roof whose ridge runs along x (slopes face north/south). */
        Plan gableX(int x0, int x1, int z0, int z1, int y, Block stairs, BlockState ridge, BlockState gableFill) {
            int layer = 0;
            for (int lz = z0, rz = z1; lz <= rz; lz++, rz--, layer++) {
                for (int x = x0; x <= x1; x++) {
                    if (lz == rz) set(x, y + layer, lz, ridge);
                    else { set(x, y + layer, lz, stair(stairs, Direction.SOUTH)); set(x, y + layer, rz, stair(stairs, Direction.NORTH)); }
                }
                for (int z = lz + 1; z < rz; z++) { set(x0, y + layer, z, gableFill); set(x1, y + layer, z, gableFill); }
            }
            return this;
        }
        /** Hipped / pyramid roof shrinking one block per layer, capped with `cap`. */
        Plan pyramid(int x0, int x1, int z0, int z1, int y, Block stairs, BlockState cap) {
            int layer = 0;
            while (x0 <= x1 && z0 <= z1) {
                if (x0 == x1 || z0 == z1) { fill(x0, y + layer, z0, x1, y + layer, z1, cap); break; }
                for (int x = x0; x <= x1; x++) { set(x, y + layer, z0, stair(stairs, Direction.SOUTH)); set(x, y + layer, z1, stair(stairs, Direction.NORTH)); }
                for (int z = z0 + 1; z < z1; z++) { set(x0, y + layer, z, stair(stairs, Direction.EAST)); set(x1, y + layer, z, stair(stairs, Direction.WEST)); }
                x0++; x1--; z0++; z1--; layer++;
            }
            return this;
        }
        /** Crenellations: alternating merlons on top of a wall ring. */
        Plan crenel(int x0, int z0, int x1, int z1, int y, BlockState s) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
                if ((x == x0 || x == x1 || z == z0 || z == z1) && ((x + z) % 2 == 0)) set(x, y, z, s);
            return this;
        }
        List<Part> ordered() { List<Part> l = new ArrayList<>(parts.values()); l.sort((a, b) -> Integer.compare(a.y, b.y)); return l; }
    }

    // ---------------------------------------------------------------- block helpers
    static BlockState s(Block b) { return b.defaultBlockState(); }
    static BlockState stair(Block b, Direction facing) { return b.defaultBlockState().setValue(StairBlock.FACING, facing); }
    static BlockState stairTop(Block b, Direction facing) { return stair(b, facing).setValue(StairBlock.HALF, Half.TOP); }
    static BlockState slab(Block b, boolean top) { return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM); }
    static BlockState log(Block b, Direction.Axis a) { return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a); }
    static BlockState banner(Block wallBanner, Direction facing) { return wallBanner.defaultBlockState().setValue(WallBannerBlock.FACING, facing); }
    static BlockState torch(Direction facing) { return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing); }
    static BlockState hanging(Block lantern) { return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true); }
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // ---------------------------------------------------------------- entry
    static List<Part> plan(Building b, int level) {
        Plan p = new Plan(b.fw, b.fh);
        String id = b.def.id(), race = b.def.race(), kind = id.substring(id.indexOf('_') + 1);
        if (race.equals("ancient_world")) ancient(p, kind, level);
        else if (race.equals("kingdoms")) kingdoms(p, kind, level);
        else Structures.generic(p, b, level);
        weather(p, b.id);
        return p.ordered();
    }

    /** Wear and texture: a sprinkle of cracked and mossy blocks so walls don't look freshly printed (stable per building). */
    static void weather(Plan p, int seed) {
        for (var e : p.parts.entrySet()) {
            Part q = e.getValue(); Block blk = q.s().getBlock();
            int h = Math.floorMod((q.x() * 73856093) ^ (q.y() * 19349663) ^ (q.z() * 83492791) ^ (seed * 31), 23);
            Block to = null;
            if (blk == Blocks.STONE_BRICKS) to = h == 0 || h == 7 ? Blocks.CRACKED_STONE_BRICKS : h == 3 ? Blocks.MOSSY_STONE_BRICKS : null;
            else if (blk == Blocks.COBBLESTONE) to = h % 5 == 0 ? Blocks.MOSSY_COBBLESTONE : null;
            else if (blk == Blocks.MUD_BRICKS) to = h == 4 ? Blocks.PACKED_MUD : null;
            else if (blk == Blocks.SMOOTH_SANDSTONE) to = h == 2 ? Blocks.SANDSTONE : null;
            else if (blk == Blocks.QUARTZ_BRICKS) to = h == 5 ? Blocks.CHISELED_QUARTZ_BLOCK : null;
            if (to != null) e.setValue(new Part(q.x(), q.y(), q.z(), to.defaultBlockState()));
        }
    }

    // =================================================================================================================
    // ANCIENT WORLD — Tribal, Viking, Greek
    // =================================================================================================================
    static void ancient(Plan p, String kind, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        switch (kind) {
            case "metal_extractor" -> {   // stone-lined pit with a timber headframe and a hanging ore bucket
                p.walls(0, 0, 0, w - 1, 0, d - 1, s(Blocks.MOSSY_COBBLESTONE)).set(cx, 0, cz, s(Blocks.IRON_ORE));
                p.set(0, 1, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)).set(w - 1, 1, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Y))
                 .set(0, 1, d - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)).set(w - 1, 1, d - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                p.set(0, 2, cz, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)).set(w - 1, 2, cz, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                p.fill(0, 3, cz, w - 1, 3, cz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
                p.set(cx, 2, cz, s(Blocks.CHAIN)).set(cx, 1, cz, s(Blocks.CAULDRON));
                if (lv >= 2) { p.set(cx, 4, cz, s(Blocks.SKELETON_SKULL)); p.set(0, 1, cz, s(Blocks.BARREL)); p.set(w - 1, 1, cz, s(Blocks.BARREL)); }
                if (lv >= 3) { p.set(cx, 0, cz, s(Blocks.RAW_IRON_BLOCK)); p.set(0, 4, cz, s(Blocks.CAMPFIRE)); p.set(w - 1, 4, cz, s(Blocks.CAMPFIRE)); }
            }
            case "energy_gen" -> {        // bonfire shrine: stone ring, central fire, bone totems with skulls
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.PACKED_MUD));
                p.walls(0, 1, 0, w - 1, 1, d - 1, s(Blocks.COBBLESTONE_WALL)).clearBox(1, 1, d - 1, w - 2, 1, d - 1);
                p.fill(1, 1, 1, w - 2, 1, d - 2, s(Blocks.CAMPFIRE));
                for (int y = 1; y <= 1 + lv; y++) p.corners(0, 0, w - 1, d - 1, y, y, s(Blocks.BONE_BLOCK));
                p.corners(0, 0, w - 1, d - 1, 2 + lv, 2 + lv, s(Blocks.SKELETON_SKULL));
                if (lv >= 2) p.set(1, 1, 1, s(Blocks.SOUL_CAMPFIRE)).set(w - 2, 1, d - 2, s(Blocks.SOUL_CAMPFIRE));
            }
            case "storage" -> {           // thatched granary on stilts: hay, barrels, a hay roof
                p.corners(0, 0, w - 1, d - 1, 0, 2, log(Blocks.OAK_LOG, Direction.Axis.Y));
                p.fill(0, 1, 0, w - 1, 1, d - 1, s(Blocks.OAK_PLANKS));
                p.fill(1, 2, 1, w - 2, 2 + lv - 1, d - 2, s(Blocks.HAY_BLOCK)).fill(1, 2, d - 2, w - 2, 2, d - 2, s(Blocks.BARREL));
                p.walls(0, 2, 0, w - 1, 2 + lv, d - 1, s(Blocks.OAK_FENCE));
                p.pyramid(0, w - 1, 0, d - 1, 3 + lv, Blocks.OAK_STAIRS, s(Blocks.HAY_BLOCK));
            }
            case "converter" -> {         // mud-brick kiln with a chimney
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.MUD_BRICKS));
                p.walls(0, 1, 0, w - 1, 2, d - 1, s(Blocks.MUD_BRICKS)).set(1, 1, d - 1, s(Blocks.BLAST_FURNACE)).set(w - 2, 1, d - 1, s(Blocks.FURNACE));
                p.fill(1, 1, 1, w - 2, 1, d - 2, s(Blocks.MAGMA_BLOCK));
                p.pyramid(0, w - 1, 0, d - 1, 3, Blocks.MUD_BRICK_STAIRS, s(Blocks.MUD_BRICKS));
                p.pillar(w - 1, 0, 3, 4 + lv, s(Blocks.BRICKS)).set(w - 1, 5 + lv, 0, s(Blocks.CAMPFIRE));
            }
            case "tech_center" -> {       // standing-stone circle around a shaman's altar
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.MOSS_BLOCK)).fill(1, 0, 1, w - 2, 0, d - 2, s(Blocks.COARSE_DIRT));
                int[][] stones = {{1, 0}, {w - 2, 0}, {0, cz}, {w - 1, cz}, {1, d - 1}, {w - 2, d - 1}, {cx, 0}};
                int k = 0;
                for (int[] st : stones) { int h = 2 + (k++ % 3) + (lv - 1); p.pillar(st[0], st[1], 1, h, s(k % 2 == 0 ? Blocks.STONE : Blocks.MOSSY_COBBLESTONE)); }
                p.fill(1, 3 + lv, 0, w - 2, 3 + lv, 0, s(Blocks.STONE_SLAB));   // lintel over the back stones
                p.set(cx, 1, cz, s(Blocks.ENCHANTING_TABLE)).set(cx - 1, 1, cz, s(Blocks.CANDLE)).set(cx + 1, 1, cz, s(Blocks.CANDLE));
                if (lv >= 2) { p.set(cx - 1, 1, cz - 1, s(Blocks.BOOKSHELF)).set(cx + 1, 1, cz - 1, s(Blocks.BOOKSHELF)).set(cx, 1, cz - 1, s(Blocks.LECTERN)); }
                if (lv >= 3) { p.set(cx - 1, 2, cz - 1, s(Blocks.AMETHYST_CLUSTER)); p.set(cx - 2, 1, cz + 1, s(Blocks.AMETHYST_BLOCK)).set(cx + 2, 1, cz + 1, s(Blocks.AMETHYST_BLOCK)); }
            }
            case "watchtower" -> {        // stilt lookout with a thatched roof
                int h = 3 + lv;
                p.corners(0, 0, w - 1, d - 1, 0, h, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                p.fill(0, h, 0, w - 1, h, d - 1, s(Blocks.SPRUCE_PLANKS)).walls(0, h + 1, 0, w - 1, h + 1, d - 1, s(Blocks.SPRUCE_FENCE));
                p.corners(0, 0, w - 1, d - 1, h + 1, h + 2, s(Blocks.SPRUCE_FENCE));
                p.pyramid(0, w - 1, 0, d - 1, h + 3, Blocks.SPRUCE_STAIRS, s(Blocks.HAY_BLOCK));
                p.pillar(cx, cz, 1, h - 1, s(Blocks.CHAIN));
                p.set(cx, h + 1, cz, s(Blocks.TARGET));
            }
            case "wall" -> { for (int y = 0; y <= lv; y++) p.set(0, y, 0, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y)); p.set(0, lv + 1, 0, s(Blocks.OAK_FENCE)); }
            case "barracks" -> longhouse(p, lv, false);
            case "war_lodge" -> longhouse(p, lv, true);
            case "hall_of_legends" -> greekTemple(p, lv);
            case "siege_yard" -> {        // palisaded timber yard with a log crane and stacked timber
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.COARSE_DIRT));
                p.walls(0, 1, 0, w - 1, 3, d - 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)).walls(0, 4, 0, w - 1, 4, d - 1, s(Blocks.SPRUCE_FENCE));
                p.clearBox(cx - 1, 1, d - 1, cx + 1, 4, d - 1);
                p.pillar(2, 2, 1, 6, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)).fill(2, 7, 2, 6, 7, 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X)).pillar(6, 2, 5, 6, s(Blocks.CHAIN));
                p.fill(w - 4, 1, 1, w - 2, 1, 3, log(Blocks.OAK_LOG, Direction.Axis.X)).fill(w - 4, 2, 1, w - 2, 2, 2, log(Blocks.OAK_LOG, Direction.Axis.X));
                p.fill(1, 1, d - 4, 3, 2, d - 2, s(Blocks.HAY_BLOCK));
                if (lv >= 2) p.corners(0, 0, w - 1, d - 1, 5, 5, s(Blocks.SKELETON_SKULL));
            }
            default -> p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.COBBLESTONE));
        }
    }

    /** Viking longhouse: stone footing, spruce log frame, plank walls, steep dark-oak gable roof, prow heads, shields. */
    static void longhouse(Plan p, int lv, boolean big) {
        int w = p.w, d = p.d, cx = w / 2, h = big ? 4 : 3;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.COBBLESTONE));
        p.walls(1, 1, 0, w - 2, h, d - 1, s(Blocks.SPRUCE_PLANKS));
        for (int z = 0; z < d; z += 2) { p.pillar(1, z, 1, h, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)); p.pillar(w - 2, z, 1, h, log(Blocks.SPRUCE_LOG, Direction.Axis.Y)); }
        p.corners(1, 0, w - 2, d - 1, 1, h, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        p.clearBox(cx - 1, 1, d - 1, cx + 1, 3, d - 1);                       // great door
        p.set(cx - 2, 1, d - 1, s(Blocks.LANTERN)).set(cx + 2, 1, d - 1, s(Blocks.LANTERN));
        p.gableZ(0, w - 1, 0, d - 1, h + 1, Blocks.DARK_OAK_STAIRS, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z), s(Blocks.SPRUCE_PLANKS));
        int ridge = h + 1 + (w - 1) / 2;
        p.set(cx, ridge + 1, 0, s(Blocks.BONE_BLOCK)).set(cx, ridge + 1, d - 1, s(Blocks.BONE_BLOCK));   // prow posts
        p.set(cx, ridge + 2, d - 1, s(Blocks.SKELETON_SKULL));
        p.fill(cx - 1, 1, 2, cx + 1, 1, d - 3, s(Blocks.CAMPFIRE)).clearBox(cx - 1, 1, 3, cx + 1, 1, d - 4); p.set(cx, 1, d / 2, s(Blocks.CAMPFIRE));   // hearth
        for (int z = 2; z < d - 1; z += 3) { p.set(0, 2, z, banner(Blocks.RED_WALL_BANNER, Direction.WEST)); p.set(w - 1, 2, z, banner(Blocks.RED_WALL_BANNER, Direction.EAST)); }
        if (big) for (int z = 3; z < d - 1; z += 3) { p.set(0, 3, z, banner(Blocks.WHITE_WALL_BANNER, Direction.WEST)); p.set(w - 1, 3, z, banner(Blocks.WHITE_WALL_BANNER, Direction.EAST)); }
        for (int l = 2; l <= lv; l++) { p.set(cx, ridge + 1, 1 + (l - 2) * 2, s(Blocks.SKELETON_SKULL)); p.set(cx, ridge + 1, d - 2 - (l - 2) * 2, s(Blocks.SKELETON_SKULL)); }
    }

    /** Greek temple: stepped quartz base, columns, cella, sandstone pediment. */
    static void greekTemple(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2, colH = 4 + lv;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.SMOOTH_QUARTZ)).fill(1, 1, 1, w - 2, 1, d - 2, s(Blocks.QUARTZ_BRICKS));
        for (int x = 1; x <= w - 2; x += 2) { p.pillar(x, 1, 2, colH, s(Blocks.QUARTZ_PILLAR)); p.pillar(x, d - 2, 2, colH, s(Blocks.QUARTZ_PILLAR)); }
        for (int z = 3; z <= d - 4; z += 2) { p.pillar(1, z, 2, colH, s(Blocks.QUARTZ_PILLAR)); p.pillar(w - 2, z, 2, colH, s(Blocks.QUARTZ_PILLAR)); }
        p.walls(3, 2, 3, w - 4, colH, d - 4, s(Blocks.SMOOTH_SANDSTONE)).clearBox(cx - 1, 2, d - 4, cx + 1, 4, d - 4);   // cella with doorway
        p.fill(1, colH + 1, 1, w - 2, colH + 1, d - 2, s(Blocks.CUT_SANDSTONE));                                     // entablature
        p.gableZ(1, w - 2, 1, d - 2, colH + 2, Blocks.SMOOTH_SANDSTONE_STAIRS, s(Blocks.CHISELED_SANDSTONE), s(Blocks.SMOOTH_SANDSTONE));
        p.set(cx, 2, d / 2, s(Blocks.GOLD_BLOCK)).set(cx, 3, d / 2, s(Blocks.LIGHTNING_ROD));                       // Zeus' altar
        p.set(cx - 2, 1, d - 1, s(Blocks.SOUL_LANTERN)).set(cx + 2, 1, d - 1, s(Blocks.SOUL_LANTERN));
        if (lv >= 2) p.fill(0, 0, d - 1, w - 1, 0, d - 1, s(Blocks.SMOOTH_QUARTZ_STAIRS));
    }

    // =================================================================================================================
    // KINGDOMS — Medieval, Dynasty, Renaissance
    // =================================================================================================================
    static void kingdoms(Plan p, String kind, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        switch (kind) {
            case "metal_extractor" -> {   // stone mine house with a winch
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.STONE_BRICKS)).set(cx, 0, cz, s(Blocks.IRON_BLOCK));
                p.corners(0, 0, w - 1, d - 1, 1, 2, s(Blocks.STONE_BRICK_WALL));
                p.set(cx, 1, cz, s(Blocks.STONECUTTER)).set(cx, 2, cz, s(Blocks.CHAIN));
                p.pyramid(0, w - 1, 0, d - 1, 3, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
                p.set(cx, 2, d - 1, hanging(Blocks.LANTERN));
                if (lv >= 2) p.set(0, 1, cz, s(Blocks.ANVIL)).set(w - 1, 1, cz, s(Blocks.SMITHING_TABLE));
                if (lv >= 3) p.set(cx, 5, cz, s(Blocks.GOLD_BLOCK));
            }
            case "energy_gen" -> {        // windmill: stone tower, timber cap, sails
                int h = 3 + lv;
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.COBBLESTONE));
                p.walls(0, 1, 0, w - 1, h, d - 1, s(Blocks.STONE_BRICKS)).clearBox(1, 1, d - 1, 2, 2, d - 1);
                p.set(0, 2, 1, s(Blocks.GLASS_PANE)).set(w - 1, 2, 1, s(Blocks.GLASS_PANE));
                p.pyramid(0, w - 1, 0, d - 1, h + 1, Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
                int hub = h; p.set(cx, hub, d - 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
                for (int k = 1; k <= Math.min(3, w - 1); k++) {   // four sail arms of fence + wool, kept inside the footprint
                    p.set(cx, hub + k, d - 1, s(Blocks.SPRUCE_FENCE)); p.set(cx, hub - k, d - 1, s(Blocks.SPRUCE_FENCE));
                    p.set(Math.min(w - 1, cx + k), hub, d - 1, s(Blocks.SPRUCE_FENCE)); p.set(Math.max(0, cx - k), hub, d - 1, s(Blocks.SPRUCE_FENCE));
                }
                p.set(cx + 1, hub + 2, d - 1, s(Blocks.WHITE_WOOL)).set(cx - 1, hub - 2, d - 1, s(Blocks.WHITE_WOOL));
                p.set(cx + 2, hub - 1, d - 1, s(Blocks.WHITE_WOOL)).set(cx - 2, hub + 1, d - 1, s(Blocks.WHITE_WOOL));
                p.set(cx, 3, d - 1, hanging(Blocks.LANTERN));
            }
            case "storage" -> {           // timber warehouse with crates
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.STONE_BRICKS));
                timberWalls(p, 0, 1, 0, w - 1, 2 + lv, d - 1);
                p.clearBox(cx - 1, 1, d - 1, cx + 1, 2, d - 1);
                p.fill(1, 1, 1, w - 2, 1, 2, s(Blocks.BARREL)).fill(1, 2, 1, w - 2, 2, 1, s(Blocks.CHEST));
                p.gableX(0, w - 1, 0, d - 1, 3 + lv, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), s(Blocks.WHITE_TERRACOTTA));
                p.set(cx, 3, d - 1, s(Blocks.LANTERN));
            }
            case "converter" -> {         // smithy: forge, anvil, brick chimney
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.POLISHED_ANDESITE));
                p.walls(0, 1, 0, w - 1, 2, d - 1, s(Blocks.STONE_BRICKS)).clearBox(1, 1, d - 1, w - 2, 2, d - 1);
                p.set(1, 1, 1, s(Blocks.BLAST_FURNACE)).set(2, 1, 1, s(Blocks.ANVIL)).set(w - 2, 1, 1, s(Blocks.SMITHING_TABLE));
                p.fill(0, 3, 0, w - 1, 3, d - 1, slab(Blocks.SPRUCE_SLAB, false));
                p.pillar(0, 0, 3, 5 + lv, s(Blocks.BRICKS)).set(0, 6 + lv, 0, s(Blocks.CAMPFIRE));
                p.set(w - 1, 2, d - 1, s(Blocks.LANTERN));
            }
            case "tech_center" -> {       // stained-glass chapel library with a spire
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.STONE_BRICKS));
                p.walls(0, 1, 0, w - 1, 4, d - 2, s(Blocks.STONE_BRICKS));
                for (int x = 1; x < w - 1; x += 2) { p.set(x, 2, 0, s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE)).set(x, 3, 0, s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE)); }
                for (int z = 1; z < d - 2; z += 2) { p.set(0, 2, z, s(Blocks.BLUE_STAINED_GLASS_PANE)).set(w - 1, 2, z, s(Blocks.BLUE_STAINED_GLASS_PANE)); }
                p.clearBox(cx - 1, 1, d - 2, cx + 1, 3, d - 2);
                p.gableZ(0, w - 1, 0, d - 1, 5, Blocks.STONE_BRICK_STAIRS, s(Blocks.CHISELED_STONE_BRICKS), s(Blocks.STONE_BRICKS));
                int top = 5 + (w - 1) / 2;
                p.pillar(cx, d - 2, top, top + 1 + lv, s(Blocks.STONE_BRICK_WALL)).set(cx, top + 2 + lv, d - 2, s(Blocks.LIGHTNING_ROD));   // spire
                p.fill(1, 1, 1, w - 2, 1, 1, s(Blocks.BOOKSHELF)).set(cx, 1, cz, s(Blocks.ENCHANTING_TABLE));
                if (lv >= 2) p.fill(1, 2, 1, w - 2, 2, 1, s(Blocks.BOOKSHELF)).set(cx - 2, 1, cz, s(Blocks.LECTERN));
                if (lv >= 3) p.set(cx, 3, 1, s(Blocks.LANTERN)).set(cx + 2, 1, cz, s(Blocks.BREWING_STAND));
                p.set(cx - 2, 2, d - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH)).set(cx + 2, 2, d - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
            }
            case "watchtower" -> {        // round-ish castle tower with crenellations and arrow slits
                int h = 4 + lv;
                p.walls(0, 0, 0, w - 1, h, d - 1, s(Blocks.STONE_BRICKS)).set(cx, 0, cz, s(Blocks.STONE_BRICKS));
                for (int y = 1; y < h; y++) p.set(cx, y, cz, s(Blocks.LADDER));
                p.set(cx, 2, d - 1, s(Blocks.IRON_BARS)).set(cx, h - 1, 0, s(Blocks.IRON_BARS));
                p.fill(0, h + 1, 0, w - 1, h + 1, d - 1, slab(Blocks.STONE_BRICK_SLAB, false)).crenel(0, 0, w - 1, d - 1, h + 2, s(Blocks.STONE_BRICKS));
                p.set(cx, h + 2, cz, s(Blocks.LANTERN));
            }
            case "wall" -> { for (int y = 0; y <= lv; y++) p.set(0, y, 0, s(y == lv ? Blocks.STONE_BRICK_WALL : Blocks.STONE_BRICKS)); }
            case "barracks" -> tudorHall(p, lv);
            case "keep_workshop" -> keep(p, lv);
            case "royal_court" -> pagoda(p, lv);
            case "siege_works" -> {       // walled workshop yard with a crane and a trebuchet frame
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.POLISHED_ANDESITE));
                p.walls(0, 1, 0, w - 1, 3, d - 1, s(Blocks.STONE_BRICKS)).crenel(0, 0, w - 1, d - 1, 4, s(Blocks.STONE_BRICKS));
                p.clearBox(cx - 1, 1, d - 1, cx + 1, 4, d - 1);
                p.pillar(2, 2, 1, 7, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)).fill(2, 8, 2, 6, 8, 2, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)).pillar(6, 2, 6, 7, s(Blocks.CHAIN));
                p.pillar(w - 3, cz, 1, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)).pillar(w - 5, cz, 1, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y))
                 .fill(w - 5, 6, cz, w - 3, 6, cz, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
                p.set(1, 1, d - 3, s(Blocks.ANVIL)).set(2, 1, d - 3, s(Blocks.SMITHING_TABLE)).set(3, 1, d - 3, s(Blocks.CAULDRON));
                if (lv >= 2) p.corners(0, 0, w - 1, d - 1, 5, 5, s(Blocks.LANTERN));
            }
            default -> p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.STONE_BRICKS));
        }
    }

    /** White plaster walls with a dark-oak timber frame. */
    static void timberWalls(Plan p, int x0, int y0, int z0, int x1, int y1, int z1) {
        p.walls(x0, y0, z0, x1, y1, z1, s(Blocks.WHITE_TERRACOTTA));
        for (int x = x0; x <= x1; x += 2) { p.pillar(x, z0, y0, y1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); p.pillar(x, z1, y0, y1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); }
        for (int z = z0; z <= z1; z += 2) { p.pillar(x0, z, y0, y1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); p.pillar(x1, z, y0, y1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); }
        for (int x = x0; x <= x1; x++) { p.set(x, y1, z0, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)); p.set(x, y1, z1, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)); }
        for (int z = z0; z <= z1; z++) { p.set(x0, y1, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z)); p.set(x1, y1, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z)); }
    }

    /** Tudor barracks hall: stone plinth, timber-framed plaster, deepslate-tile gable roof, blue banners. */
    static void tudorHall(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.STONE_BRICKS));
        p.walls(0, 1, 0, w - 1, 1, d - 2, s(Blocks.COBBLESTONE));
        timberWalls(p, 0, 2, 0, w - 1, 4, d - 2);
        p.clearBox(cx - 1, 1, d - 2, cx + 1, 3, d - 2);
        for (int x = 1; x < w - 1; x += 2) if (Math.abs(x - cx) > 1) p.set(x, 3, d - 2, s(Blocks.GLASS_PANE));
        p.pillar(0, d - 1, 1, 4, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)).pillar(w - 1, d - 1, 1, 4, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));   // porch posts
        p.gableZ(0, w - 1, 0, d - 1, 5, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), s(Blocks.WHITE_TERRACOTTA));
        p.set(cx - 2, 4, d - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH)).set(cx + 2, 4, d - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
        p.fill(1, 1, 1, w - 2, 1, 1, s(Blocks.BARREL)).set(cx, 2, 1, s(Blocks.LANTERN));
        p.set(1, 1, 3, s(Blocks.HAY_BLOCK)).set(1, 2, 3, s(Blocks.CARVED_PUMPKIN));   // training dummy
        if (lv >= 2) p.pillar(w - 2, 0, 5, 8, s(Blocks.BRICKS)).set(w - 2, 9, 0, s(Blocks.CAMPFIRE));
        if (lv >= 3) p.set(cx, 5 + (w - 1) / 2 + 1, d - 1, s(Blocks.GOLD_BLOCK));
    }

    /** Stone keep: thick walls, corner towers, crenellations, portcullis gate. */
    static void keep(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2, h = 5;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.STONE_BRICKS));
        p.walls(1, 1, 1, w - 2, h, d - 2, s(Blocks.STONE_BRICKS));
        for (int y = 1; y <= h; y++) for (int x = 1; x <= w - 2; x++) if ((x * 7 + y * 3) % 5 == 0) { p.set(x, y, 1, s(Blocks.CRACKED_STONE_BRICKS)); p.set(x, y, d - 2, s(Blocks.MOSSY_STONE_BRICKS)); }
        p.corners(0, 0, w - 1, d - 1, 0, h + 2, s(Blocks.STONE_BRICKS)).corners(0, 0, w - 1, d - 1, h + 3, h + 3, s(Blocks.STONE_BRICK_WALL));
        p.crenel(1, 1, w - 2, d - 2, h + 1, s(Blocks.STONE_BRICKS));
        p.clearBox(cx - 1, 1, d - 2, cx + 1, 3, d - 2).fill(cx - 1, 4, d - 2, cx + 1, 4, d - 2, s(Blocks.IRON_BARS));   // gate with portcullis above
        p.fill(2, h, 2, w - 3, h, d - 3, s(Blocks.SPRUCE_PLANKS));
        p.set(cx - 2, 3, d - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH)).set(cx + 2, 3, d - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
        p.corners(0, 0, w - 1, d - 1, h + 4, h + 4, s(Blocks.LANTERN));
        for (int l = 2; l <= lv; l++) p.pillar(cx, 2, h + 1, h + 1 + 2 * l, s(Blocks.STONE_BRICKS));   // central keep tower grows
        if (lv >= 2) p.set(cx, h + 2 + 2 * lv, 2, s(Blocks.LANTERN));
    }

    /** Two-tier Dynasty pagoda palace: red pillars, dark timber, upturned deepslate-tile roofs, gold finial. */
    static void pagoda(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.POLISHED_ANDESITE)).fill(1, 1, 1, w - 2, 1, d - 2, s(Blocks.SMOOTH_STONE));
        p.walls(2, 2, 2, w - 3, 4, d - 3, s(Blocks.DARK_OAK_PLANKS));
        for (int x = 2; x <= w - 3; x += 2) { p.pillar(x, 2, 2, 4, s(Blocks.RED_TERRACOTTA)); p.pillar(x, d - 3, 2, 4, s(Blocks.RED_TERRACOTTA)); }
        for (int z = 2; z <= d - 3; z += 2) { p.pillar(2, z, 2, 4, s(Blocks.RED_TERRACOTTA)); p.pillar(w - 3, z, 2, 4, s(Blocks.RED_TERRACOTTA)); }
        p.clearBox(cx - 1, 2, d - 3, cx + 1, 3, d - 3);
        // lower roof with eaves over the full footprint
        p.fill(0, 5, 0, w - 1, 5, d - 1, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
        p.corners(0, 0, w - 1, d - 1, 6, 6, s(Blocks.DEEPSLATE_TILE_SLAB));      // upturned corners
        // upper storey + roof
        p.walls(3, 6, 3, w - 4, 7 + (lv >= 2 ? 1 : 0), d - 4, s(Blocks.RED_TERRACOTTA));
        int ry = 8 + (lv >= 2 ? 1 : 0);
        p.pyramid(2, w - 3, 2, d - 3, ry, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
        p.corners(2, 2, w - 3, d - 3, ry + 1, ry + 1, s(Blocks.DEEPSLATE_TILE_SLAB));
        int peak = ry + Math.min(w - 4, d - 4) / 2;
        p.set(cx, peak + 1, cz, s(Blocks.GOLD_BLOCK)).set(cx, peak + 2, cz, s(Blocks.LIGHTNING_ROD));
        p.corners(1, 1, w - 2, d - 2, 4, 4, hanging(Blocks.LANTERN));
        p.set(cx, 2, cz, s(Blocks.GOLD_BLOCK)).set(cx - 1, 2, cz, s(Blocks.CHISELED_STONE_BRICKS)).set(cx + 1, 2, cz, s(Blocks.CHISELED_STONE_BRICKS));
        if (lv >= 2) p.set(cx - 3, 1, d - 1, s(Blocks.BELL)).set(cx + 3, 1, d - 1, s(Blocks.BELL));
    }
}
