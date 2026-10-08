package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Race-specific architecture for every building, as block plans: the shared detail kit lives here, each faction's
 * buildings in its own file ({@link ArchCrown}, {@link ArchBronze}, {@link ArchStar}).
 * <p>
 * Coordinates: x across (0..w-1), z deep (0..d-1), y up from the ground (y = -1 is the ground layer itself, used for
 * paths and farmland); the front (door, rally side) faces +z. Every level adds visible detail.
 * <p>
 * Each building is modelled on a real historical (or, for Starforge, real engineering) type, and built the way good
 * Minecraft builders work: walls set in from the footprint so roofs overhang, framing and string courses for depth,
 * windows with shutters, sills and flower boxes, real doors, chimneys, porches and towers that break the box, mixed
 * block palettes so no wall is one flat texture, and life around the building (paths, gardens, stores, lamps).
 */
final class Architecture {
    record Part(int x, int y, int z, BlockState s) { }

    /** A plan being drawn. Later writes replace earlier ones at the same spot. */
    static final class Plan {
        final int w, d; final int seed; final java.util.LinkedHashMap<Long, Part> parts = new java.util.LinkedHashMap<>();
        Plan(int w, int d, int seed) { this.w = w; this.d = d; this.seed = seed; }
        static long key(int x, int y, int z) { return ((long) x & 0xfff) | (((long) (y + 1) & 0xfff) << 12) | (((long) z & 0xfff) << 24); }
        boolean in(int x, int z) { return x >= 0 && z >= 0 && x < w && z < d; }
        Plan set(int x, int y, int z, BlockState s) {
            if (!in(x, z) || y < -1) return this;
            parts.put(key(x, y, z), new Part(x, y, z, s)); return this;
        }
        BlockState get(int x, int y, int z) { Part p = parts.get(key(x, y, z)); return p == null ? null : p.s(); }
        boolean has(int x, int y, int z) { return parts.containsKey(key(x, y, z)); }
        /** Sets only where nothing has been placed yet. */
        Plan add(int x, int y, int z, BlockState s) { if (!has(x, y, z)) set(x, y, z, s); return this; }
        Plan clear(int x, int y, int z) { parts.remove(key(x, y, z)); return this; }
        Plan fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
            return this;
        }
        Plan clearBox(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) clear(x, y, z);
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
        /** Filled disc (or ring when hollow) of radius r centred on (cx+.5, cz+.5) — rounded towers, domes, wheels. */
        Plan disc(double cx, double cz, double r, int y, BlockState s, boolean hollow) {
            for (int x = (int) Math.floor(cx - r - 1); x <= cx + r + 1; x++) for (int z = (int) Math.floor(cz - r - 1); z <= cz + r + 1; z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd <= r + .35 && (!hollow || dd > r - .75)) set(x, y, z, s);
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
        /** Stable per-position pseudo-random in [0, n). */
        int rnd(int x, int y, int z, int n) { return Math.floorMod((x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * 668265263), 1 << 20) % n; }
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
    static BlockState leaves(Block b) { return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true); }
    static BlockState ripe(Block crop) { return crop.defaultBlockState().setValue(CropBlock.AGE, 7); }
    static BlockState waterCauldron() { return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3); }
    static BlockState trapdoor(Block b, Direction facing, boolean open, boolean top) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, open).setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }
    static BlockState grindstone() { return Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR); }
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    // ---------------------------------------------------------------- entry
    static List<Part> plan(Building b, int level) {
        Plan p = new Plan(b.fw, b.fh, b.id);
        String id = b.def.id(), race = b.def.race(), kind = id.substring(id.indexOf('_') + 1);
        switch (race) {
            case "ancient_world" -> ArchBronze.build(p, kind, level);
            case "kingdoms" -> ArchCrown.build(p, kind, level);
            case "starforge" -> ArchStar.build(p, kind, level);
            default -> Structures.generic(p, b, level);
        }
        texturize(p);
        return p.ordered();
    }

    // =================================================================================================================
    // TEXTURE: no wall is one flat block. Deterministic per building, weighted toward wear low down.
    // =================================================================================================================
    static void texturize(Plan p) {
        for (var e : p.parts.entrySet()) {
            Part q = e.getValue(); BlockState st = q.s(); Block blk = st.getBlock();
            int h = p.rnd(q.x(), q.y(), q.z(), 100); boolean low = q.y() <= 1;
            Block to = null;
            if (blk == Blocks.STONE_BRICKS) to = h < 8 ? Blocks.CRACKED_STONE_BRICKS : h < (low ? 22 : 13) ? Blocks.MOSSY_STONE_BRICKS : null;
            else if (blk == Blocks.COBBLESTONE) to = h < (low ? 25 : 10) ? Blocks.MOSSY_COBBLESTONE : h < (low ? 35 : 22) ? Blocks.ANDESITE : h < 30 ? Blocks.STONE : null;
            else if (blk == Blocks.STONE_BRICK_STAIRS) to = h < (low ? 20 : 8) ? Blocks.MOSSY_STONE_BRICK_STAIRS : null;
            else if (blk == Blocks.COBBLESTONE_STAIRS) to = h < 18 ? Blocks.MOSSY_COBBLESTONE_STAIRS : null;
            else if (blk == Blocks.STONE_BRICK_WALL) to = h < 15 ? Blocks.MOSSY_STONE_BRICK_WALL : null;
            else if (blk == Blocks.COBBLESTONE_WALL) to = h < 20 ? Blocks.MOSSY_COBBLESTONE_WALL : null;
            else if (blk == Blocks.DEEPSLATE_TILES) to = h < 12 ? Blocks.CRACKED_DEEPSLATE_TILES : null;
            else if (blk == Blocks.DEEPSLATE_TILE_STAIRS) to = h < 9 ? Blocks.COBBLED_DEEPSLATE_STAIRS : null;
            else if (blk == Blocks.MUD_BRICKS) to = h < 12 ? Blocks.PACKED_MUD : null;
            else if (blk == Blocks.SMOOTH_SANDSTONE) to = h < 10 ? Blocks.SANDSTONE : null;
            else if (blk == Blocks.QUARTZ_BRICKS) to = h < 8 ? Blocks.CHISELED_QUARTZ_BLOCK : null;
            else if (blk == Blocks.CALCITE) to = h < 10 ? Blocks.DIORITE : null;
            else if (blk == Blocks.POLISHED_BLACKSTONE_BRICKS) to = h < 15 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : null;
            else if (blk == Blocks.DIRT_PATH && q.y() == -1) to = h < 15 ? Blocks.COARSE_DIRT : h < 22 ? Blocks.GRAVEL : null;
            if (to != null) e.setValue(new Part(q.x(), q.y(), q.z(), to.withPropertiesOf(st)));
        }
    }

    // =================================================================================================================
    // DETAIL KIT
    // =================================================================================================================
    static final int FRONT = 0, BACK = 1, RIGHT = 2, LEFT = 3;

    static Direction out(int face) {
        return switch (face) { case FRONT -> S; case BACK -> N; case RIGHT -> E; default -> W; };
    }

    static Direction opp(Direction d) { return d.getOpposite(); }

    /** A block on a wall face: u along the wall, n blocks outward from the wall plane. */
    static void at(Plan p, int face, int plane, int u, int y, int n, BlockState s) {
        switch (face) {
            case FRONT -> p.set(u, y, plane + n, s);
            case BACK -> p.set(u, y, plane - n, s);
            case RIGHT -> p.set(plane + n, y, u, s);
            default -> p.set(plane - n, y, u, s);
        }
    }

    /** A window h blocks tall with open shutters either side and a sill below. */
    static void window(Plan p, int face, int plane, int u, int y, int h, Block pane, Block shutter) {
        for (int k = 0; k < h; k++) at(p, face, plane, u, y + k, 0, s(pane));
        if (shutter == null) return;
        Direction o = out(face);
        for (int k = 0; k < h; k++) { at(p, face, plane, u - 1, y + k, 1, trapdoor(shutter, o, true, false)); at(p, face, plane, u + 1, y + k, 1, trapdoor(shutter, o, true, false)); }
        at(p, face, plane, u, y - 1, 1, trapdoor(shutter, o, false, true));
    }

    /** Window with a flower box: a top-half trapdoor shelf carrying a potted flower, shutters either side. */
    static void flowerWindow(Plan p, int face, int plane, int u, int y, Block pane, Block shutter, Block pot) {
        window(p, face, plane, u, y, 1, pane, shutter);
        at(p, face, plane, u, y - 1, 1, trapdoor(shutter, out(face), false, true));
        at(p, face, plane, u, y, 1, s(pot));
    }

    /** A real door; optional awning (upside-down stairs) and lanterns either side. */
    static void door(Plan p, int face, int plane, int u, int y, Block door, Block awning, Block lantern) {
        Direction o = out(face);
        at(p, face, plane, u, y, 0, door.defaultBlockState().setValue(DoorBlock.FACING, o).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        at(p, face, plane, u, y + 1, 0, door.defaultBlockState().setValue(DoorBlock.FACING, o).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        if (awning != null) for (int k = -1; k <= 1; k++) at(p, face, plane, u + k, y + 2, 1, stairTop(awning, opp(o)));
        if (lantern != null && awning != null) { at(p, face, plane, u - 1, y + 1, 1, hanging(lantern)); at(p, face, plane, u + 1, y + 1, 1, hanging(lantern)); }
    }

    /** Double doors (two leaves). */
    static void doubleDoor(Plan p, int face, int plane, int u, int y, Block door) {
        Direction o = out(face);
        for (int k = 0; k <= 1; k++) {
            var hinge = k == 0 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
            BlockState lo = door.defaultBlockState().setValue(DoorBlock.FACING, o).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            at(p, face, plane, u + k, y, 0, lo);
            at(p, face, plane, u + k, y + 1, 0, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    /** Infill walls with log corner posts, posts every `step` blocks and a top plate. */
    static void frameWalls(Plan p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState infill, Block log, int step) {
        p.walls(x0, y0, z0, x1, y1, z1, infill);
        for (int x = x0; x <= x1; x += Math.max(1, Math.min(step, x1 - x0))) { p.pillar(x, z0, y0, y1, log(log, Direction.Axis.Y)); p.pillar(x, z1, y0, y1, log(log, Direction.Axis.Y)); }
        for (int z = z0; z <= z1; z += Math.max(1, Math.min(step, z1 - z0))) { p.pillar(x0, z, y0, y1, log(log, Direction.Axis.Y)); p.pillar(x1, z, y0, y1, log(log, Direction.Axis.Y)); }
        p.corners(x0, z0, x1, z1, y0, y1, log(log, Direction.Axis.Y));
        for (int x = x0 + 1; x < x1; x++) { p.set(x, y1, z0, log(log, Direction.Axis.X)); p.set(x, y1, z1, log(log, Direction.Axis.X)); }
        for (int z = z0 + 1; z < z1; z++) { p.set(x0, y1, z, log(log, Direction.Axis.Z)); p.set(x1, y1, z, log(log, Direction.Axis.Z)); }
    }

    /** Half-timbering: frame plus diagonal-ish braces (stairs) in the panels next to the corners. */
    static void tudor(Plan p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState infill, Block log, Block braceStairs) {
        frameWalls(p, x0, y0, z0, x1, y1, z1, infill, log, 3);
        if (y1 - y0 < 2) return;
        // braces: an upside-down stair under the top plate beside each corner post reads as a knee brace
        for (int[] c : new int[][]{{x0 + 1, z0, 0}, {x1 - 1, z0, 1}, {x0 + 1, z1, 0}, {x1 - 1, z1, 1}})
            p.set(c[0], y1 - 1, c[1], stairTop(braceStairs, c[2] == 0 ? W : E));
        for (int[] c : new int[][]{{x0, z0 + 1, 0}, {x0, z1 - 1, 1}, {x1, z0 + 1, 0}, {x1, z1 - 1, 1}})
            p.set(c[0], y1 - 1, c[1], stairTop(braceStairs, c[2] == 0 ? N : S));
    }

    /** Stone walls: a base course, corner quoins and (if tall enough) a string course. */
    static void stoneWalls(Plan p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState main, BlockState trim) {
        p.walls(x0, y0, z0, x1, y1, z1, main);
        p.walls(x0, y0, z0, x1, y0, z1, trim);
        if (y1 - y0 >= 4) p.walls(x0, y0 + (y1 - y0) / 2 + 1, z0, x1, y0 + (y1 - y0) / 2 + 1, z1, trim);
        p.corners(x0, z0, x1, z1, y0, y1, trim);
    }

    /** Upside-down stairs all round the outside of a wall box: an overhanging ledge (needs walls inset by 1). */
    static void cornice(Plan p, int x0, int z0, int x1, int z1, int y, Block stairs) {
        for (int x = x0; x <= x1; x++) { p.set(x, y, z0 - 1, stairTop(stairs, S)); p.set(x, y, z1 + 1, stairTop(stairs, N)); }
        for (int z = z0; z <= z1; z++) { p.set(x0 - 1, y, z, stairTop(stairs, E)); p.set(x1 + 1, y, z, stairTop(stairs, W)); }
    }

    /** Plinth: right-way-up stairs hugging the base of the walls on the outside (needs walls inset by 1). */
    static void plinth(Plan p, int x0, int z0, int x1, int z1, int y, Block stairs) {
        for (int x = x0; x <= x1; x++) { p.add(x, y, z0 - 1, stair(stairs, S)); p.add(x, y, z1 + 1, stair(stairs, N)); }
        for (int z = z0; z <= z1; z++) { p.add(x0 - 1, y, z, stair(stairs, E)); p.add(x1 + 1, y, z, stair(stairs, W)); }
    }

    /**
     * Gable roof over [rx0..rx1] x [rz0..rz1] (overhang included), ridge along x; gable ends filled on the wall planes
     * wx0 / wx1. The ridge gets a slab cap and the eaves an upside-down-stair fascia so the roof has thickness.
     */
    static int roofX(Plan p, int rx0, int rx1, int rz0, int rz1, int y, Block stairs, BlockState ridge, int wx0, int wx1, BlockState fill) {
        int l = 0;
        for (; rz0 + l <= rz1 - l; l++) {
            int lz = rz0 + l, hz = rz1 - l;
            for (int x = rx0; x <= rx1; x++) {
                if (lz == hz) p.set(x, y + l, lz, ridge);
                else { p.set(x, y + l, lz, stair(stairs, S)); p.set(x, y + l, hz, stair(stairs, N)); }
            }
            for (int z = lz + 1; z < hz; z++) { p.set(wx0, y + l, z, fill); p.set(wx1, y + l, z, fill); }
            if (lz + 1 == hz) { for (int x = rx0; x <= rx1; x++) { p.set(x, y + l + 1, lz, ridge); p.set(x, y + l + 1, hz, ridge); } l++; break; }
        }
        return y + l;   // first y above the ridge
    }

    /** Gable roof with the ridge along z (front gable facing +z). */
    static int roofZ(Plan p, int rx0, int rx1, int rz0, int rz1, int y, Block stairs, BlockState ridge, int wz0, int wz1, BlockState fill) {
        int l = 0;
        for (; rx0 + l <= rx1 - l; l++) {
            int lx = rx0 + l, hx = rx1 - l;
            for (int z = rz0; z <= rz1; z++) {
                if (lx == hx) p.set(lx, y + l, z, ridge);
                else { p.set(lx, y + l, z, stair(stairs, E)); p.set(hx, y + l, z, stair(stairs, W)); }
            }
            for (int x = lx + 1; x < hx; x++) { p.set(x, y + l, wz0, fill); p.set(x, y + l, wz1, fill); }
            if (lx + 1 == hx) { for (int z = rz0; z <= rz1; z++) { p.set(lx, y + l + 1, z, ridge); p.set(hx, y + l + 1, z, ridge); } l++; break; }
        }
        return y + l;
    }

    /** Gable-end trim: upside-down stairs under the verge of an x-ridge roof's gables (bargeboards). */
    static void bargeX(Plan p, int x, int rz0, int rz1, int y, Block stairs, boolean east) {
        for (int l = 0; rz0 + l < rz1 - l; l++) { p.add(x, y + l - 1, rz0 + l, stairTop(stairs, N)); p.add(x, y + l - 1, rz1 - l, stairTop(stairs, S)); }
    }

    static void chimney(Plan p, int x, int z, int y0, int y1, BlockState body, BlockState cap) {
        p.pillar(x, z, y0, y1, body);
        if (cap != null) p.set(x, y1 + 1, z, cap);
        p.set(x, y1 + (cap != null ? 2 : 1), z, s(Blocks.CAMPFIRE));
    }

    /** Banner on a wall, hanging on the outside (recoloured to the team colour when placed). */
    static void wallBanner(Plan p, int face, int plane, int u, int y) { at(p, face, plane, u, y, 1, banner(Blocks.WHITE_WALL_BANNER, out(face))); }

    // ---------------------------------------------------------------- life around buildings
    static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.RED_TULIP, Blocks.LILY_OF_THE_VALLEY};
    static final Block[] POTS = {Blocks.POTTED_RED_TULIP, Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_ALLIUM};

    static Block pot(Plan p, int x, int z) { return POTS[p.rnd(x, 7, z, POTS.length)]; }

    /** A flower on grass (only where nothing else stands). */
    static void flower(Plan p, int x, int z) { if (!p.has(x, 0, z) && !p.has(x, -1, z)) p.set(x, 0, z, s(FLOWERS[p.rnd(x, 0, z, FLOWERS.length)])); }

    /** Scatter flowers and grass tufts over free ground cells with the given chance (percent). */
    static void garden(Plan p, int x0, int z0, int x1, int z1, int chance) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (p.has(x, 0, z) || p.has(x, -1, z)) continue;
            int r = p.rnd(x, 3, z, 100);
            if (r < chance) p.set(x, 0, z, s(FLOWERS[p.rnd(x, 0, z, FLOWERS.length)]));
            else if (r < chance * 2) p.set(x, 0, z, s(Blocks.GRASS));
        }
    }

    /** A leafy bush (one or two blocks), azalea flowers mixed in. */
    static void bush(Plan p, int x, int z, int h) {
        for (int y = 0; y < h; y++) p.add(x, y, z, leaves(p.rnd(x, y, z, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : y == 0 ? Blocks.OAK_LEAVES : Blocks.AZALEA_LEAVES));
    }

    /** Ground path (dirt path, texturized with coarse dirt and gravel). */
    static void path(Plan p, int x0, int z0, int x1, int z1) { p.fill(x0, -1, z0, x1, -1, z1, s(Blocks.DIRT_PATH)); }

    /** Lamp post: fence pole with a lantern on top. */
    static void lampPost(Plan p, int x, int z, int h, Block fence, Block lantern) { p.pillar(x, z, 0, h - 1, s(fence)); p.set(x, h, z, s(lantern)); }

    /**
     * Dome (or cone) over centre (cx+.5, cz+.5): one ring per radius; cells left uncovered by the next ring become
     * stairs rising toward the centre, so the outline curves instead of stepping. Returns the first y above the top.
     */
    static int dome(Plan p, double cx, double cz, double[] radii, int y0, Block stairs, BlockState body) {
        for (int i = 0; i < radii.length; i++) {
            double r = radii[i], next = i + 1 < radii.length ? radii[i + 1] : -1;
            int y = y0 + i;
            for (int x = (int) Math.floor(cx - r - 1); x <= cx + r + 1; x++) for (int z = (int) Math.floor(cz - r - 1); z <= cz + r + 1; z++) {
                double dx = x - cx, dz = z - cz, dd = Math.hypot(dx, dz);
                if (dd > r + .35) continue;
                if (next >= 0 && dd <= next + .35) { p.set(x, y, z, body); continue; }
                Direction toward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? W : E) : (dz > 0 ? N : S);
                p.set(x, y, z, stairs == null ? body : stair(stairs, toward));
            }
        }
        return y0 + radii.length;
    }

    /**
     * East-Asian hip roof with a curved profile: a flat slab eave ring, then 45-degree stairs up to a ridge line, and
     * upturned corners. Covers [x0..x1] x [z0..z1] from y; returns the first y above the ridge.
     */
    static int curvedHip(Plan p, int x0, int x1, int z0, int z1, int y, Block stairs, Block slabB, BlockState ridge, BlockState cornerUp) {
        for (int x = x0; x <= x1; x++) { p.set(x, y, z0, slab(slabB, false)); p.set(x, y, z1, slab(slabB, false)); }
        for (int z = z0; z <= z1; z++) { p.set(x0, y, z, slab(slabB, false)); p.set(x1, y, z, slab(slabB, false)); }
        if (cornerUp != null) for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) p.set(c[0], y, c[1], cornerUp);
        int ax0 = x0 + 1, ax1 = x1 - 1, az0 = z0 + 1, az1 = z1 - 1, yy = y;
        while (az1 - az0 >= 1 && ax1 - ax0 >= 1) {
            for (int x = ax0; x <= ax1; x++) { p.set(x, yy, az0, stair(stairs, S)); p.set(x, yy, az1, stair(stairs, N)); }
            if (ax1 - ax0 > az1 - az0) for (int z = az0 + 1; z < az1; z++) { p.set(ax0, yy, z, stair(stairs, E)); p.set(ax1, yy, z, stair(stairs, W)); }
            ax0 += ax1 - ax0 > az1 - az0 ? 1 : 0; ax1 -= ax1 - ax0 > az1 - az0 ? 1 : 0;
            az0++; az1--; yy++;
        }
        for (int x = ax0; x <= ax1; x++) for (int z = az0; z <= az1; z++) p.set(x, yy, z, ridge);
        return yy + 1;
    }

    /** Stack of crates/barrels/hay: a little store pile. */
    static void goods(Plan p, int x, int z, int kind) {
        switch (Math.floorMod(kind, 4)) {
            case 0 -> { p.add(x, 0, z, s(Blocks.BARREL)); p.add(x, 1, z, s(Blocks.BARREL)); }
            case 1 -> { p.add(x, 0, z, log(Blocks.HAY_BLOCK, Direction.Axis.Y)); }
            case 2 -> { p.add(x, 0, z, s(Blocks.BARREL)); p.add(x, 1, z, s(Blocks.COMPOSTER)); }
            default -> { p.add(x, 0, z, log(Blocks.SPRUCE_LOG, Direction.Axis.X)); }
        }
    }

    /** A row of ripe crops on farmland. */
    static void crops(Plan p, int x0, int z0, int x1, int z1, Block crop) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) { p.set(x, -1, z, s(Blocks.FARMLAND)); p.set(x, 0, z, ripe(crop)); }
    }

    /** Woodpile: horizontal logs stacked two high along x. */
    static void woodpile(Plan p, int x0, int x1, int z, Block log) {
        for (int x = x0; x <= x1; x++) { p.set(x, 0, z, log(log, Direction.Axis.Z)); if ((x - x0) % 2 == 0) p.set(x, 1, z, log(log, Direction.Axis.Z)); }
    }
}
