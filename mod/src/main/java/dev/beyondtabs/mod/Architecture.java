package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Race-specific architecture for every building, as block plans. Coordinates: x across (0..w-1), z deep (0..d-1),
 * y up from the ground; the front (door, rally side) faces +z. Every level adds visible detail.
 * Built the way good Minecraft builders do it: walls set in from the footprint so roofs overhang them, log or stone
 * framing and string courses for depth, windows with shutters and sills, real doors under little awnings, chimneys,
 * porches and towers that break the box shape, and a weathering pass for texture.
 * Bronzeborn Clans = Tribal / Viking / Greek. Sovereign Crown = Medieval / Dynasty / Renaissance.
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
    // DETAIL KIT
    // =================================================================================================================
    static final int FRONT = 0, BACK = 1, RIGHT = 2, LEFT = 3;

    static Direction out(int face) {
        return switch (face) { case FRONT -> Direction.SOUTH; case BACK -> Direction.NORTH; case RIGHT -> Direction.EAST; default -> Direction.WEST; };
    }

    static Direction opp(Direction d) {
        return switch (d) { case NORTH -> Direction.SOUTH; case SOUTH -> Direction.NORTH; case EAST -> Direction.WEST; case WEST -> Direction.EAST; case UP -> Direction.DOWN; default -> Direction.UP; };
    }

    /** A block on a wall face: u along the wall, n blocks outward from the wall plane. */
    static void at(Plan p, int face, int plane, int u, int y, int n, BlockState s) {
        switch (face) {
            case FRONT -> p.set(u, y, plane + n, s);
            case BACK -> p.set(u, y, plane - n, s);
            case RIGHT -> p.set(plane + n, y, u, s);
            default -> p.set(plane - n, y, u, s);
        }
    }

    static BlockState trapdoor(Block b, Direction facing, boolean open, boolean top) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, open).setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    /** A window h blocks tall with open shutters either side and a sill below. */
    static void window(Plan p, int face, int plane, int u, int y, int h, Block pane, Block shutter) {
        for (int k = 0; k < h; k++) at(p, face, plane, u, y + k, 0, s(pane));
        if (shutter == null) return;
        Direction o = out(face);
        for (int k = 0; k < h; k++) { at(p, face, plane, u - 1, y + k, 1, trapdoor(shutter, o, true, false)); at(p, face, plane, u + 1, y + k, 1, trapdoor(shutter, o, true, false)); }
        at(p, face, plane, u, y - 1, 1, trapdoor(shutter, o, false, true));
    }

    /** A real door with an awning above and lanterns either side. */
    static void door(Plan p, int face, int plane, int u, int y, Block door, Block awning, Block lantern) {
        Direction o = out(face);
        at(p, face, plane, u, y, 0, door.defaultBlockState().setValue(DoorBlock.FACING, o).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        at(p, face, plane, u, y + 1, 0, door.defaultBlockState().setValue(DoorBlock.FACING, o).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        if (awning != null) for (int k = -1; k <= 1; k++) at(p, face, plane, u + k, y + 2, 1, stairTop(awning, opp(o)));
        if (lantern != null && awning != null) { at(p, face, plane, u - 1, y + 1, 1, hanging(lantern)); at(p, face, plane, u + 1, y + 1, 1, hanging(lantern)); }
    }

    /** Infill walls with log corner posts, posts every third block and a top plate. */
    static void frameWalls(Plan p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState infill, Block log) {
        p.walls(x0, y0, z0, x1, y1, z1, infill);
        for (int x = x0; x <= x1; x += Math.max(1, Math.min(3, x1 - x0))) { p.pillar(x, z0, y0, y1, log(log, Direction.Axis.Y)); p.pillar(x, z1, y0, y1, log(log, Direction.Axis.Y)); }
        for (int z = z0; z <= z1; z += Math.max(1, Math.min(3, z1 - z0))) { p.pillar(x0, z, y0, y1, log(log, Direction.Axis.Y)); p.pillar(x1, z, y0, y1, log(log, Direction.Axis.Y)); }
        p.corners(x0, z0, x1, z1, y0, y1, log(log, Direction.Axis.Y));
        for (int x = x0 + 1; x < x1; x++) { p.set(x, y1, z0, log(log, Direction.Axis.X)); p.set(x, y1, z1, log(log, Direction.Axis.X)); }
        for (int z = z0 + 1; z < z1; z++) { p.set(x0, y1, z, log(log, Direction.Axis.Z)); p.set(x1, y1, z, log(log, Direction.Axis.Z)); }
    }

    /** Stone walls: a base course, corner quoins and (if tall enough) a string course. */
    static void stoneWalls(Plan p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState main, BlockState trim) {
        p.walls(x0, y0, z0, x1, y1, z1, main);
        p.walls(x0, y0, z0, x1, y0, z1, trim);
        if (y1 - y0 >= 4) p.walls(x0, y0 + (y1 - y0) / 2 + 1, z0, x1, y0 + (y1 - y0) / 2 + 1, z1, trim);
        p.corners(x0, z0, x1, z1, y0, y1, trim);
    }

    /** Upside-down stairs all round the outside of a wall box: an overhanging ledge that casts a shadow line. */
    static void cornice(Plan p, int x0, int z0, int x1, int z1, int y, Block stairs) {
        for (int x = x0; x <= x1; x++) { p.set(x, y, z0 - 1, stairTop(stairs, Direction.SOUTH)); p.set(x, y, z1 + 1, stairTop(stairs, Direction.NORTH)); }
        for (int z = z0; z <= z1; z++) { p.set(x0 - 1, y, z, stairTop(stairs, Direction.EAST)); p.set(x1 + 1, y, z, stairTop(stairs, Direction.WEST)); }
    }

    /**
     * Gable roof over [rx0..rx1] x [rz0..rz1] (overhang included), ridge along x; gable ends filled on the wall planes
     * wx0 / wx1 between the walls' z range.
     */
    static void roofX(Plan p, int rx0, int rx1, int rz0, int rz1, int y, Block stairs, BlockState ridge, int wx0, int wx1, BlockState fill) {
        for (int l = 0; rz0 + l <= rz1 - l; l++) {
            int lz = rz0 + l, hz = rz1 - l;
            for (int x = rx0; x <= rx1; x++) {
                if (lz == hz) p.set(x, y + l, lz, ridge);
                else { p.set(x, y + l, lz, stair(stairs, Direction.SOUTH)); p.set(x, y + l, hz, stair(stairs, Direction.NORTH)); }
            }
            for (int z = lz + 1; z < hz; z++) { p.set(wx0, y + l, z, fill); p.set(wx1, y + l, z, fill); }
        }
    }

    /** Gable roof with the ridge along z (front gable facing +z). */
    static void roofZ(Plan p, int rx0, int rx1, int rz0, int rz1, int y, Block stairs, BlockState ridge, int wz0, int wz1, BlockState fill) {
        for (int l = 0; rx0 + l <= rx1 - l; l++) {
            int lx = rx0 + l, hx = rx1 - l;
            for (int z = rz0; z <= rz1; z++) {
                if (lx == hx) p.set(lx, y + l, z, ridge);
                else { p.set(lx, y + l, z, stair(stairs, Direction.EAST)); p.set(hx, y + l, z, stair(stairs, Direction.WEST)); }
            }
            for (int x = lx + 1; x < hx; x++) { p.set(x, y + l, wz0, fill); p.set(x, y + l, wz1, fill); }
        }
    }

    static void chimney(Plan p, int x, int z, int y0, int y1, BlockState body) {
        p.pillar(x, z, y0, y1, body);
        p.set(x, y1 + 1, z, s(Blocks.CAMPFIRE));
    }

    /** Banner on a wall, hanging on the outside. */
    static void wallBanner(Plan p, int face, int plane, int u, int y) { at(p, face, plane, u, y, 1, banner(Blocks.WHITE_WALL_BANNER, out(face))); }

    // =================================================================================================================
    // BRONZEBORN CLANS — Tribal, Viking, Greek
    // =================================================================================================================
    static void ancient(Plan p, String kind, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        BlockState spruceY = log(Blocks.SPRUCE_LOG, Direction.Axis.Y), planks = s(Blocks.SPRUCE_PLANKS), cobble = s(Blocks.COBBLESTONE);
        switch (kind) {
            case "metal_extractor" -> {   // stone-lined shaft with a timber headframe and a hanging ore bucket
                p.walls(0, 0, 0, w - 1, 0, d - 1, s(Blocks.MOSSY_COBBLESTONE)).set(cx, 0, cz, s(lv >= 3 ? Blocks.RAW_IRON_BLOCK : Blocks.IRON_ORE));
                p.set(0, 1, 0, s(Blocks.COBBLESTONE_WALL)).set(w - 1, 1, 0, s(Blocks.COBBLESTONE_WALL)).set(0, 1, d - 1, s(Blocks.COBBLESTONE_WALL)).set(w - 1, 1, d - 1, s(Blocks.COBBLESTONE_WALL));
                p.pillar(0, cz, 1, 3, spruceY).pillar(w - 1, cz, 1, 3, spruceY);
                p.fill(0, 4, cz, w - 1, 4, cz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
                p.set(cx, 3, cz, s(Blocks.CHAIN)).set(cx, 2, cz, s(Blocks.CHAIN)).set(cx, 1, cz, s(Blocks.CAULDRON));
                p.set(0, 5, cz, s(Blocks.SKELETON_SKULL));
                if (lv >= 2) { p.set(0, 1, cz - 1, s(Blocks.BARREL)); p.set(w - 1, 1, cz + 1, s(Blocks.BARREL)); }
                if (lv >= 3) { p.set(w - 1, 5, cz, s(Blocks.LANTERN)); }
            }
            case "energy_gen" -> {        // round-cornered mud-brick forge hut, thatch hip roof, chimney and a fire pit out front
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.PACKED_MUD));
                p.walls(0, 1, 0, w - 1, 2, d - 2, s(Blocks.MUD_BRICKS));
                p.corners(0, 0, w - 1, d - 2, 1, 2, spruceY);
                p.clear(cx, 1, d - 2).clear(cx, 2, d - 2).clear(cx - 1, 1, d - 2);
                p.fill(1, 1, 1, w - 2, 1, d - 3, s(Blocks.MAGMA_BLOCK));
                p.pyramid(0, w - 1, 0, d - 2, 3, Blocks.SPRUCE_STAIRS, s(Blocks.HAY_BLOCK));
                chimney(p, w - 1, 0, 3, 4 + lv, s(Blocks.BRICKS));
                p.set(cx, 0, d - 1, s(Blocks.CAMPFIRE)).set(cx - 1, 0, d - 1, s(Blocks.COBBLESTONE_WALL));
                for (int k = 1; k <= lv; k++) { p.set(0, 2 + k, d - 1, s(Blocks.BONE_BLOCK)); }
                p.set(0, 3 + lv, d - 1, s(Blocks.SKELETON_SKULL));
            }
            case "storage" -> {           // timber store with a steep thatch-and-spruce gable, hay and barrels
                p.fill(1, 0, 1, w - 2, 0, d - 2, cobble);
                frameWalls(p, 1, 1, 1, w - 2, 3, d - 2, planks, Blocks.SPRUCE_LOG);
                door(p, FRONT, d - 2, cx, 1, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_STAIRS, Blocks.LANTERN);
                window(p, RIGHT, w - 2, cz, 2, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR);
                window(p, LEFT, 1, cz, 2, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR);
                roofX(p, 0, w - 1, 0, d - 1, 4, Blocks.SPRUCE_STAIRS, s(Blocks.HAY_BLOCK), 1, w - 2, planks);
                p.set(0, 0, d - 1, s(Blocks.HAY_BLOCK)).set(w - 1, 0, d - 1, s(Blocks.BARREL)).set(w - 1, 0, 0, s(Blocks.BARREL));
                if (lv >= 2) { p.set(0, 0, 0, s(Blocks.HAY_BLOCK)).set(0, 1, 0, s(Blocks.HAY_BLOCK)).set(w - 1, 1, 0, s(Blocks.BARREL)); }
            }
            case "converter" -> {         // mud-brick beehive kiln with a tall chimney
                // rounded dome: cut corners on the lower courses, stairs stepping in on each side, a slab cap
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.PACKED_MUD));
                p.walls(0, 1, 0, w - 1, 2, d - 2, s(Blocks.MUD_BRICKS));
                for (int y = 1; y <= 2; y++) { p.clear(0, y, 0).clear(w - 1, y, 0).clear(0, y, d - 2).clear(w - 1, y, d - 2); }
                for (int x = 1; x < w - 1; x++) { p.set(x, 3, 0, stair(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH)); p.set(x, 3, d - 2, stair(Blocks.MUD_BRICK_STAIRS, Direction.NORTH)); }
                for (int z = 1; z < d - 2; z++) { p.set(0, 3, z, stair(Blocks.MUD_BRICK_STAIRS, Direction.EAST)); p.set(w - 1, 3, z, stair(Blocks.MUD_BRICK_STAIRS, Direction.WEST)); }
                p.fill(1, 3, 1, w - 2, 3, d - 3, s(Blocks.MUD_BRICKS)).fill(1, 4, 1, w - 2, 4, d - 3, slab(Blocks.MUD_BRICK_SLAB, false));
                // glowing firing mouth on the front, a fuel stack and a work bench in the open yard
                p.set(cx, 1, d - 2, s(Blocks.MAGMA_BLOCK)).set(cx - 1, 1, d - 2, s(Blocks.BLAST_FURNACE)).set(cx, 2, d - 2, stairTop(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH));
                p.fill(0, 1, d - 1, 0, 1, d - 1, log(Blocks.OAK_LOG, Direction.Axis.Z)).set(w - 1, 1, d - 1, s(Blocks.CAULDRON));
                chimney(p, w - 2, 1, 5, 5 + lv, s(Blocks.BRICKS));
                if (lv >= 2) { p.set(0, 2, d - 1, log(Blocks.OAK_LOG, Direction.Axis.Z)); p.set(w - 1, 1, 0, s(Blocks.ANVIL)); }
            }
            case "tech_center" -> greekTemple(p, lv, false);
            case "watchtower" -> {        // timber lookout on stilts: braced posts, railing, thatch roof, lantern
                int h = 3 + lv;
                p.corners(0, 0, w - 1, d - 1, 0, h, spruceY);
                p.set(cx, h / 2, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X)).set(cx, h / 2, d - 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
                p.set(0, h / 2, cz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)).set(w - 1, h / 2, cz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
                p.fill(0, h, 0, w - 1, h, d - 1, planks).corners(0, 0, w - 1, d - 1, h, h, spruceY);
                p.walls(0, h + 1, 0, w - 1, h + 1, d - 1, s(Blocks.SPRUCE_FENCE));
                p.corners(0, 0, w - 1, d - 1, h + 1, h + 2, spruceY);
                p.pyramid(0, w - 1, 0, d - 1, h + 3, Blocks.SPRUCE_STAIRS, s(Blocks.HAY_BLOCK));
                p.set(cx, h + 2, cz, hanging(Blocks.LANTERN));
                if (lv >= 3) p.set(cx, h + 5, cz, s(Blocks.SKELETON_SKULL));
            }
            case "wall" -> {
                if (lv >= 2) p.set(0, 0, 0, cobble);
                int y0 = lv >= 2 ? 1 : 0;
                p.pillar(0, 0, y0, y0 + 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y)).set(0, y0 + 3, 0, s(Blocks.SPRUCE_FENCE));
            }
            case "barracks" -> longhouse(p, lv, false);
            case "war_lodge" -> longhouse(p, lv, true);
            case "hall_of_legends" -> greekTemple(p, lv, true);
            case "siege_yard" -> {        // palisaded timber yard: workshop shed, crane, a half-built bolt thrower, timber stacks
                p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.COARSE_DIRT));
                for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    if (!edge || (z == d - 1 && Math.abs(x - cx) <= 1)) continue;
                    p.pillar(x, z, 1, 2 + ((x + z) % 2), log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                }
                p.set(cx - 2, 3, d - 1, s(Blocks.SKELETON_SKULL)).set(cx + 2, 3, d - 1, s(Blocks.SKELETON_SKULL));
                // workshop shed (back left) with a lean-to roof
                frameWalls(p, 1, 1, 1, 5, 3, 4, planks, Blocks.SPRUCE_LOG);
                p.clearBox(2, 1, 4, 4, 2, 4);
                for (int x = 0; x <= 6; x++) { p.set(x, 4, 0, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH)); p.set(x, 4, 1, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH)); p.set(x, 4, 5, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH)); }
                p.fill(0, 5, 2, 6, 5, 4, slab(Blocks.SPRUCE_SLAB, false));
                p.set(2, 1, 2, s(Blocks.SMITHING_TABLE)).set(3, 1, 2, s(Blocks.ANVIL)).set(4, 1, 2, s(Blocks.BARREL));
                // crane
                p.pillar(w - 3, 2, 1, 7, spruceY).fill(w - 7, 8, 2, w - 2, 8, 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
                p.pillar(w - 7, 2, 5, 7, s(Blocks.CHAIN)).set(w - 7, 4, 2, s(Blocks.COBBLESTONE));
                // bolt thrower frame
                p.fill(cx - 1, 1, cz + 1, cx + 1, 1, cz + 3, planks).set(cx, 2, cz + 2, s(Blocks.SPRUCE_FENCE)).set(cx, 3, cz + 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
                p.set(cx - 2, 2, cz + 2, s(Blocks.SPRUCE_FENCE)).set(cx + 2, 2, cz + 2, s(Blocks.SPRUCE_FENCE));
                // timber stacks
                p.fill(1, 1, d - 4, 3, 1, d - 2, log(Blocks.SPRUCE_LOG, Direction.Axis.X)).fill(1, 2, d - 3, 3, 2, d - 2, log(Blocks.OAK_LOG, Direction.Axis.X));
                if (lv >= 2) { p.fill(w - 4, 1, d - 4, w - 2, 1, d - 2, s(Blocks.HAY_BLOCK)); p.set(w - 3, 2, d - 3, s(Blocks.TARGET)); }
            }
            default -> p.fill(0, 0, 0, w - 1, 0, d - 1, cobble);
        }
    }

    /** Viking longhouse: stone footing, log-framed plank walls, steep dark roof overhanging all round, carved prows, shields. */
    static void longhouse(Plan p, int lv, boolean big) {
        int w = p.w, d = p.d, cx = w / 2;
        int h = big ? 4 : 3;
        int x0 = 1, x1 = w - 2, z0 = 1, z1 = d - (big ? 4 : 2);
        BlockState planks = s(Blocks.SPRUCE_PLANKS);
        p.fill(x0, 0, z0, x1, 0, z1, s(Blocks.COBBLESTONE));
        frameWalls(p, x0, 1, z0, x1, h, z1, planks, Blocks.SPRUCE_LOG);
        p.walls(x0, 1, z0, x1, 1, z1, s(Blocks.COBBLESTONE)); p.corners(x0, z0, x1, z1, 1, 1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        door(p, FRONT, z1, cx, 1, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_STAIRS, Blocks.LANTERN);
        if (big) { window(p, FRONT, z1, cx - 2, 2, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR); window(p, FRONT, z1, cx + 2, 2, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR); }
        for (int z = z0 + 1; z < z1; z += 2) { window(p, LEFT, x0, z, 2, 1, Blocks.GLASS_PANE, null); window(p, RIGHT, x1, z, 2, 1, Blocks.GLASS_PANE, null); }
        // steep roof with the ridge along z, overhanging the walls on every side
        roofZ(p, x0 - 1, x1 + 1, z0 - 1, z1 + 1, h + 1, Blocks.DARK_OAK_STAIRS, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z), z0, z1, planks);
        int ridge = h + 1 + (x1 - x0 + 2) / 2;
        // carved prow posts crossing at both gable peaks
        for (int z : new int[]{z0 - 1, z1 + 1}) { p.set(cx, ridge + 1, z, s(Blocks.BONE_BLOCK)); p.set(cx, ridge + 2, z, s(Blocks.SKELETON_SKULL)); }
        // round shields hung along the walls (team banners), plus a hearth glow inside
        for (int z = z0 + 1; z < z1; z += 2) { wallBanner(p, LEFT, x0, z, h); wallBanner(p, RIGHT, x1, z, h); }
        p.set(cx, 1, (z0 + z1) / 2, s(Blocks.CAMPFIRE));
        if (big) {   // yard in front: fence, fire pit, practice target
            for (int x = 0; x < w; x++) if (Math.abs(x - cx) > 1) p.set(x, 0, d - 1, s(Blocks.SPRUCE_FENCE));
            for (int z = z1 + 1; z < d; z++) { p.set(0, 0, z, s(Blocks.SPRUCE_FENCE)); p.set(w - 1, 0, z, s(Blocks.SPRUCE_FENCE)); }
            p.set(1, 0, d - 2, s(Blocks.CAMPFIRE)).set(w - 2, 0, d - 2, s(Blocks.TARGET)).set(w - 2, 1, d - 2, s(Blocks.CARVED_PUMPKIN));
        }
        if (lv >= 2) { p.set(0, 0, 0, s(Blocks.HAY_BLOCK)).set(0, 1, 0, s(Blocks.CARVED_PUMPKIN)); p.set(w - 1, 0, 0, s(Blocks.BARREL)); }
        if (lv >= 3) { chimney(p, x1 - 1, z0, h + 1, ridge, s(Blocks.COBBLESTONE)); p.set(x0, h + 1, z1 + 1, s(Blocks.LANTERN)); }
    }

    /** Greek temple: stepped quartz stylobate, column rows, sandstone cella, entablature with cornice, pediment roof. */
    static void greekTemple(Plan p, int lv, boolean great) {
        int w = p.w, d = p.d, cx = w / 2;
        int colH = great ? 4 + lv : 3 + lv;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.SMOOTH_QUARTZ));
        for (int x = 0; x < w; x++) p.set(x, 0, d - 1, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH));   // front steps
        p.fill(1, 1, 1, w - 2, 1, d - 2, s(Blocks.QUARTZ_BRICKS));
        for (int x = 1; x <= w - 2; x += 2) { p.pillar(x, 1, 2, colH, s(Blocks.QUARTZ_PILLAR)); p.pillar(x, d - 2, 2, colH, s(Blocks.QUARTZ_PILLAR)); }
        for (int z = 3; z <= d - 4; z += 2) { p.pillar(1, z, 2, colH, s(Blocks.QUARTZ_PILLAR)); p.pillar(w - 2, z, 2, colH, s(Blocks.QUARTZ_PILLAR)); }
        stoneWalls(p, 3, 2, 3, w - 4, colH, d - 4, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE));
        door(p, FRONT, d - 4, cx, 2, Blocks.SPRUCE_DOOR, null, null);
        p.fill(1, colH + 1, 1, w - 2, colH + 1, d - 2, s(Blocks.CUT_SANDSTONE));                                           // architrave
        for (int x = 1; x <= w - 2; x++) { p.set(x, colH + 1, 0, stairTop(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.SOUTH)); p.set(x, colH + 1, d - 1, stairTop(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.NORTH)); }
        roofZ(p, 0, w - 1, 0, d - 1, colH + 2, Blocks.SMOOTH_SANDSTONE_STAIRS, s(Blocks.CHISELED_SANDSTONE), 1, d - 2, s(Blocks.SMOOTH_SANDSTONE));
        p.set(cx, colH + 2, d - 2, s(Blocks.GOLD_BLOCK));                                                                  // pediment boss
        p.set(cx, 2, d / 2, s(Blocks.GOLD_BLOCK)).set(cx, 3, d / 2, s(Blocks.LIGHTNING_ROD));                             // altar inside
        if (lv >= 2) for (int x : new int[]{0, w - 1}) { p.set(x, 1, d - 1, s(Blocks.COBBLESTONE_WALL)); p.set(x, 2, d - 1, s(Blocks.CAMPFIRE)); }   // braziers
        if (great) {   // a gold hero on a plinth before the steps, and corner towers at the top level
            p.set(cx, 1, d - 1, s(Blocks.CHISELED_QUARTZ_BLOCK)).set(cx, 2, d - 1, s(Blocks.GOLD_BLOCK)).set(cx, 3, d - 1, s(Blocks.GOLD_BLOCK)).set(cx, 4, d - 1, s(Blocks.LIGHTNING_ROD));
            if (lv >= 2) for (int x : new int[]{1, w - 2}) { p.pillar(x, 1, colH + 2, colH + 4, s(Blocks.QUARTZ_PILLAR)); p.set(x, colH + 5, 1, s(Blocks.SOUL_LANTERN)); }
        }
    }

    // =================================================================================================================
    // SOVEREIGN CROWN — Medieval, Dynasty, Renaissance
    // =================================================================================================================
    static void kingdoms(Plan p, String kind, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        BlockState bricks = s(Blocks.STONE_BRICKS), trim = s(Blocks.POLISHED_ANDESITE), plaster = s(Blocks.WHITE_TERRACOTTA);
        switch (kind) {
            case "metal_extractor" -> {   // stone mine head with a tiled roof, ore cart and lantern
                p.fill(0, 0, 0, w - 1, 0, d - 1, bricks).set(cx, 0, cz, s(lv >= 3 ? Blocks.IRON_BLOCK : Blocks.IRON_ORE));
                p.corners(0, 0, w - 1, d - 1, 1, 2, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
                p.walls(0, 1, 0, w - 1, 1, 0, s(Blocks.STONE_BRICK_WALL));
                p.set(cx, 1, cz, s(Blocks.STONECUTTER)).set(cx, 2, cz, s(Blocks.CHAIN));
                p.pyramid(0, w - 1, 0, d - 1, 3, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
                p.set(cx, 2, d - 1, hanging(Blocks.LANTERN));
                if (lv >= 2) { p.set(0, 1, cz, s(Blocks.ANVIL)); p.set(w - 1, 1, cz, s(Blocks.BARREL)); }
            }
            case "energy_gen" -> {        // windmill: stone tower, tiled cap, cross of sails on the front
                int h = 3 + lv;
                p.fill(0, 0, 0, w - 1, 0, d - 2, s(Blocks.COBBLESTONE));
                stoneWalls(p, 0, 1, 0, w - 1, h, d - 2, bricks, trim);
                door(p, FRONT, d - 2, 1, 1, Blocks.SPRUCE_DOOR, null, null);
                window(p, LEFT, 0, 1, 3, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR);
                p.pyramid(0, w - 1, 0, d - 2, h + 1, Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
                int hub = h, hx = w - 2; p.set(hx, hub, d - 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
                for (int k = 1; k <= 2; k++) {   // four arms of the sail cross, in the plane in front of the tower
                    p.set(hx, hub + k, d - 1, s(Blocks.SPRUCE_FENCE)); p.set(hx, hub - k, d - 1, s(Blocks.SPRUCE_FENCE));
                    p.set(Math.min(w - 1, hx + k), hub, d - 1, s(Blocks.SPRUCE_FENCE)); p.set(hx - k, hub, d - 1, s(Blocks.SPRUCE_FENCE));
                }
                p.set(hx - 1, hub + 2, d - 1, s(Blocks.WHITE_WOOL)).set(hx + 1, hub - 2, d - 1, s(Blocks.WHITE_WOOL)).set(hx - 2, hub - 1, d - 1, s(Blocks.WHITE_WOOL));
            }
            case "storage" -> {           // granary: timber-framed plaster under a steep slate roof, sacks and barrels
                p.fill(1, 0, 1, w - 2, 0, d - 2, bricks);
                frameWalls(p, 1, 1, 1, w - 2, 3, d - 2, plaster, Blocks.DARK_OAK_LOG);
                door(p, FRONT, d - 2, cx, 1, Blocks.DARK_OAK_DOOR, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.LANTERN);
                window(p, RIGHT, w - 2, cz, 2, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR);
                window(p, LEFT, 1, cz, 2, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR);
                roofX(p, 0, w - 1, 0, d - 1, 4, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), 1, w - 2, plaster);
                p.set(0, 0, d - 1, s(Blocks.BARREL)).set(w - 1, 0, d - 1, s(Blocks.HAY_BLOCK));
                if (lv >= 2) { p.set(w - 1, 0, 0, s(Blocks.BARREL)).set(w - 1, 1, 0, s(Blocks.BARREL)).set(0, 0, 0, s(Blocks.HAY_BLOCK)); }
            }
            case "converter" -> {         // smithy: stone base, timber upper storey, tall brick chimney, forge in the open front
                p.fill(0, 0, 0, w - 1, 0, d - 1, trim);
                p.walls(0, 1, 0, w - 1, 1, d - 2, bricks);
                frameWalls(p, 0, 2, 0, w - 1, 3, d - 2, plaster, Blocks.DARK_OAK_LOG);
                p.clearBox(1, 1, d - 2, w - 2, 2, d - 2);
                p.set(1, 1, 1, s(Blocks.BLAST_FURNACE)).set(2, 1, 1, s(Blocks.ANVIL)).set(w - 2, 1, d - 2, s(Blocks.SMITHING_TABLE));
                p.pyramid(0, w - 1, 0, d - 1, 4, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
                chimney(p, 0, 0, 4, 5 + lv, s(Blocks.BRICKS));
                p.set(w - 1, 2, d - 1, hanging(Blocks.LANTERN));
            }
            case "tech_center" -> {       // stone chapel library: buttressed nave, tall windows, steep roof, bell tower with spire
                p.fill(1, 0, 1, w - 2, 0, d - 1, trim);
                stoneWalls(p, 1, 1, 2, w - 2, 4, d - 2, bricks, trim);
                for (int z = 3; z < d - 2; z += 2) { p.set(0, 1, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST)); p.set(w - 1, 1, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST)); }   // buttresses
                for (int z = 3; z < d - 2; z += 2) { window(p, LEFT, 1, z, 2, 2, Blocks.BLUE_STAINED_GLASS_PANE, null); window(p, RIGHT, w - 2, z, 2, 2, Blocks.BLUE_STAINED_GLASS_PANE, null); }
                door(p, FRONT, d - 2, cx, 1, Blocks.DARK_OAK_DOOR, Blocks.STONE_BRICK_STAIRS, Blocks.LANTERN);
                window(p, FRONT, d - 2, cx, 4, 1, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, null);
                roofZ(p, 0, w - 1, 1, d - 1, 5, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), 2, d - 2, bricks);
                int top = 5 + (w - 1) / 2;
                p.fill(cx - 1, 1, 0, cx + 1, top + 1, 1, bricks);   // bell tower at the back
                p.clear(cx, top, 1).set(cx, top, 1, s(Blocks.BELL));
                p.pyramid(cx - 1, cx + 1, 0, 1, top + 2, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
                p.pillar(cx, 0, top + 3, top + 3 + lv, s(Blocks.STONE_BRICK_WALL)).set(cx, top + 4 + lv, 0, s(Blocks.LIGHTNING_ROD));
                p.fill(2, 1, 3, w - 3, 1, 3, s(Blocks.BOOKSHELF)).set(cx, 1, cz, s(Blocks.ENCHANTING_TABLE));
                if (lv >= 2) p.set(cx - 1, 1, cz + 1, s(Blocks.LECTERN));
                if (lv >= 3) p.set(cx + 1, 1, cz + 1, s(Blocks.BREWING_STAND));
            }
            case "watchtower" -> {        // round-ish stone tower: arrow slits, machicolated crenellations, slate cap
                int h = 4 + lv;
                stoneWalls(p, 0, 0, 0, w - 1, h, d - 1, bricks, trim);
                p.set(cx, 2, d - 1, s(Blocks.IRON_BARS)).set(cx, h - 1, 0, s(Blocks.IRON_BARS)).set(0, h - 1, cz, s(Blocks.IRON_BARS)).set(w - 1, 3, cz, s(Blocks.IRON_BARS));
                p.fill(0, h + 1, 0, w - 1, h + 1, d - 1, slab(Blocks.STONE_BRICK_SLAB, false));
                p.crenel(0, 0, w - 1, d - 1, h + 2, bricks);
                if (lv >= 2) { p.corners(0, 0, w - 1, d - 1, h + 2, h + 3, s(Blocks.DARK_OAK_FENCE)); p.pyramid(0, w - 1, 0, d - 1, h + 4, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES)); p.set(cx, h + 3, cz, hanging(Blocks.LANTERN)); }
                else p.set(cx, h + 2, cz, s(Blocks.LANTERN));
            }
            case "wall" -> {
                for (int y = 0; y <= lv; y++) p.set(0, y, 0, y == 0 ? trim : bricks);
                p.set(0, lv + 1, 0, s(Blocks.STONE_BRICK_WALL));
            }
            case "barracks" -> tudorHall(p, lv);
            case "keep_workshop" -> keep(p, lv);
            case "royal_court" -> pagoda(p, lv);
            case "siege_works" -> {       // walled yard: gatehouse, timber workshop, crane, trebuchet frame
                p.fill(0, 0, 0, w - 1, 0, d - 1, trim);
                stoneWalls(p, 0, 1, 0, w - 1, 3, d - 1, bricks, trim);
                p.crenel(0, 0, w - 1, d - 1, 4, bricks);
                p.clearBox(cx - 1, 1, d - 1, cx + 1, 3, d - 1).fill(cx - 1, 3, d - 1, cx + 1, 3, d - 1, s(Blocks.IRON_BARS));
                p.corners(cx - 2, d - 1, cx + 2, d - 1, 1, 5, bricks);
                frameWalls(p, 1, 1, 1, 5, 3, 4, plaster, Blocks.DARK_OAK_LOG);
                door(p, FRONT, 4, 3, 1, Blocks.DARK_OAK_DOOR, null, null);
                roofX(p, 1, 5, 1, 5, 4, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), 1, 5, plaster);
                p.pillar(w - 3, 2, 1, 7, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)).fill(w - 7, 8, 2, w - 2, 8, 2, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
                p.pillar(w - 7, 2, 6, 7, s(Blocks.CHAIN)).set(w - 7, 5, 2, s(Blocks.COBBLESTONE));
                p.pillar(w - 3, cz + 1, 1, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)).pillar(w - 5, cz + 1, 1, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y))
                 .fill(w - 5, 6, cz + 1, w - 3, 6, cz + 1, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X)).set(w - 4, 5, cz + 1, s(Blocks.CHAIN));
                p.set(2, 1, d - 3, s(Blocks.ANVIL)).set(3, 1, d - 3, s(Blocks.SMITHING_TABLE)).set(4, 1, d - 3, s(Blocks.CAULDRON));
                if (lv >= 2) p.corners(cx - 2, d - 1, cx + 2, d - 1, 6, 6, s(Blocks.LANTERN));
            }
            default -> p.fill(0, 0, 0, w - 1, 0, d - 1, bricks);
        }
    }

    /** Tudor hall: stone plinth, timber-framed plaster, jettied upper floor, steep slate roof, chimney, banners. */
    static void tudorHall(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        BlockState plaster = s(Blocks.WHITE_TERRACOTTA);
        p.fill(1, 0, 1, w - 2, 0, d - 2, s(Blocks.STONE_BRICKS));
        p.walls(1, 1, 1, w - 2, 1, d - 2, s(Blocks.COBBLESTONE));
        frameWalls(p, 1, 2, 1, w - 2, 3, d - 2, plaster, Blocks.DARK_OAK_LOG);
        door(p, FRONT, d - 2, cx, 1, Blocks.DARK_OAK_DOOR, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.LANTERN);
        window(p, FRONT, d - 2, cx - 2, 2, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR); window(p, FRONT, d - 2, cx + 2, 2, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR);
        window(p, LEFT, 1, cz, 2, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR); window(p, RIGHT, w - 2, cz, 2, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR);
        int roofY = 4;
        if (lv >= 2) {   // jettied upper floor overhanging the ground floor on every side
            cornice(p, 1, 1, w - 2, d - 2, 3, Blocks.DARK_OAK_STAIRS);
            frameWalls(p, 0, 4, 0, w - 1, 5, d - 1, plaster, Blocks.DARK_OAK_LOG);
            for (int x = 2; x < w - 2; x += 2) window(p, FRONT, d - 1, x, 5, 1, Blocks.GLASS_PANE, null);
            roofY = 6;
        }
        roofX(p, 0, w - 1, 0, d - 1, roofY, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), lv >= 2 ? 0 : 1, lv >= 2 ? w - 1 : w - 2, plaster);
        chimney(p, w - 2, 1, 2, roofY + (d - 1) / 2 + 1, s(Blocks.BRICKS));
        wallBanner(p, FRONT, d - 2, cx - 1, 3); wallBanner(p, FRONT, d - 2, cx + 1, 3);
        p.set(0, 0, d - 1, s(Blocks.HAY_BLOCK)).set(0, 1, d - 1, s(Blocks.CARVED_PUMPKIN));   // training dummy
        if (lv >= 3) { p.set(cx, roofY + (d - 1) / 2 + 1, 0, s(Blocks.GOLD_BLOCK)); p.set(w - 1, 0, d - 1, s(Blocks.BARREL)); }
    }

    /** Stone keep: thick walls with string course, corner towers, machicolations, portcullis gate, banners. */
    static void keep(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2, h = 4 + lv;
        BlockState bricks = s(Blocks.STONE_BRICKS), trim = s(Blocks.POLISHED_ANDESITE);
        p.fill(0, 0, 0, w - 1, 0, d - 1, trim);
        stoneWalls(p, 1, 1, 1, w - 2, h, d - 2, bricks, trim);
        cornice(p, 1, 1, w - 2, d - 2, h, Blocks.STONE_BRICK_STAIRS);
        p.crenel(0, 0, w - 1, d - 1, h + 1, bricks);
        for (int[] c : new int[][]{{0, 0}, {w - 2, 0}, {0, d - 2}, {w - 2, d - 2}}) {   // 2x2 corner towers, taller than the walls
            p.fill(c[0], 1, c[1], c[0] + 1, h + 2, c[1] + 1, bricks);
            p.set(c[0], h + 3, c[1], bricks).set(c[0] + 1, h + 3, c[1] + 1, bricks);
            if (lv >= 2) p.set(c[0], h + 4, c[1], s(Blocks.LANTERN));
        }
        p.clearBox(cx - 1, 1, d - 2, cx + 1, 3, d - 2).fill(cx - 1, 3, d - 2, cx + 1, 3, d - 2, s(Blocks.IRON_BARS));
        for (int y = 3; y < h - 1; y += 2) for (int x : new int[]{cx - 3, cx + 3}) p.set(x, y, d - 2, s(Blocks.IRON_BARS));   // arrow slits
        wallBanner(p, FRONT, d - 2, cx - 2, h - 1); wallBanner(p, FRONT, d - 2, cx + 2, h - 1);
        p.fill(2, h, 2, w - 3, h, d - 3, s(Blocks.SPRUCE_PLANKS));
        if (lv >= 2) { p.fill(cx - 1, h + 1, cx - 1, cx + 1, h + 3, cx + 1, bricks); p.crenel(cx - 1, cx - 1, cx + 1, cx + 1, h + 4, bricks); }   // central donjon
        if (lv >= 3) p.set(cx, h + 4, cx, s(Blocks.LANTERN));
    }

    /** Two-tier Dynasty pagoda palace: stone terrace, red columns, dark timber walls, flared tile roofs, gold finial. */
    static void pagoda(Plan p, int lv) {
        int w = p.w, d = p.d, cx = w / 2, cz = d / 2;
        p.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.POLISHED_ANDESITE));
        for (int x = cx - 2; x <= cx + 2; x++) p.set(x, 0, d - 1, stair(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH));
        p.fill(1, 1, 1, w - 2, 1, d - 2, s(Blocks.SMOOTH_STONE));
        for (int x = 1; x < w - 1; x++) { p.set(x, 1, d - 2, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH)); }
        p.walls(2, 2, 2, w - 3, 4, d - 3, s(Blocks.DARK_OAK_PLANKS));
        for (int x = 2; x <= w - 3; x += 2) { p.pillar(x, 2, 2, 4, s(Blocks.RED_TERRACOTTA)); p.pillar(x, d - 3, 2, 4, s(Blocks.RED_TERRACOTTA)); }
        for (int z = 2; z <= d - 3; z += 2) { p.pillar(2, z, 2, 4, s(Blocks.RED_TERRACOTTA)); p.pillar(w - 3, z, 2, 4, s(Blocks.RED_TERRACOTTA)); }
        door(p, FRONT, d - 3, cx, 2, Blocks.DARK_OAK_DOOR, null, null);
        for (int x = 3; x < w - 3; x += 2) if (Math.abs(x - cx) > 1) window(p, FRONT, d - 3, x, 3, 1, Blocks.GLASS_PANE, null);
        // lower roof: tiles out to the terrace edge, corners turned up
        p.pyramid(0, w - 1, 0, d - 1, 5, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) p.set(c[0], 6, c[1], slab(Blocks.DEEPSLATE_TILE_SLAB, false));
        // upper storey and roof
        int uy = 7;
        p.walls(3, uy, 3, w - 4, uy + 1 + (lv >= 2 ? 1 : 0), d - 4, s(Blocks.RED_TERRACOTTA));
        window(p, FRONT, d - 4, cx, uy + 1, 1, Blocks.GLASS_PANE, null);
        int ry = uy + 2 + (lv >= 2 ? 1 : 0);
        p.pyramid(2, w - 3, 2, d - 3, ry, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES));
        for (int[] c : new int[][]{{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}}) p.set(c[0], ry + 1, c[1], slab(Blocks.DEEPSLATE_TILE_SLAB, false));
        int peak = ry + Math.min(w - 4, d - 4) / 2;
        p.set(cx, peak + 1, cz, s(Blocks.GOLD_BLOCK)).set(cx, peak + 2, cz, s(Blocks.LIGHTNING_ROD));
        p.corners(1, 1, w - 2, d - 2, 4, 4, hanging(Blocks.LANTERN));
        wallBanner(p, FRONT, d - 3, cx - 2, 4); wallBanner(p, FRONT, d - 3, cx + 2, 4);
        if (lv >= 2) p.set(cx - 3, 2, d - 1, s(Blocks.BELL)).set(cx + 3, 2, d - 1, s(Blocks.BELL));
    }
}
