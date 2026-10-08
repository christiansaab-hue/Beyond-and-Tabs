package dev.beyondtabs.mod;

import static dev.beyondtabs.mod.Architecture.*;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SOVEREIGN CROWN — medieval Europe, the Chinese dynasties and the Renaissance. Every building follows a real type:
 * <ul>
 * <li>mine: Agricola's windlass shaft (De Re Metallica, 1556) under a little shingled hood, ore cart rails</li>
 * <li>energy: Dutch tower mill with a reefing stage and lattice sails</li>
 * <li>storage: tithe-barn granary raised on staddle stones against rats</li>
 * <li>converter: assay smithy / bloomery with a bellows hearth and brick stack</li>
 * <li>tech: Gothic chapel-library with buttresses, lancet windows and a spire</li>
 * <li>watchtower: Norman tower with timber hoarding (brattice) under a slate cone</li>
 * <li>barracks: jettied Tudor guildhall (Little Moreton Hall)</li>
 * <li>keep workshop: Norman keep (the White Tower) with corner turrets and a forebuilding</li>
 * <li>royal court: Ming palace hall (Hall of Supreme Harmony): marble terrace, red columns, double eaves</li>
 * <li>siege works: Venetian Arsenale gate yard with a trebuchet on the stocks</li>
 * <li>crucible (advanced energy): Brunelleschi-style copper dome over an alchemist's athanor</li>
 * <li>crane (assist): treadwheel crane after the Gdańsk Żuraw</li>
 * </ul>
 * Palette: cobble and stone-brick footings, dark-oak framing with calcite plaster, slate (deepslate tile) roofs,
 * spruce trim; Ming red (mangrove, red terracotta) and gold for the court; weathered copper for the crucible.
 */
final class ArchCrown {
    private ArchCrown() { }

    static final BlockState COBBLE = s(Blocks.COBBLESTONE), BRICK = s(Blocks.STONE_BRICKS), PLASTER = s(Blocks.CALCITE),
            SPRUCE = s(Blocks.SPRUCE_PLANKS), DARK = s(Blocks.DARK_OAK_PLANKS);
    static final BlockState DARK_Y = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y), SPRUCE_Y = log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
    static final Block SLATE = Blocks.DEEPSLATE_TILE_STAIRS;
    static final BlockState SLATE_RIDGE = slab(Blocks.DEEPSLATE_TILE_SLAB, false);

    static void build(Plan p, String kind, int lv) {
        switch (kind) {
            case "metal_extractor" -> mine(p, lv);
            case "energy_gen" -> windmill(p, lv);
            case "storage" -> granary(p, lv);
            case "converter" -> smithy(p, lv);
            case "tech_center" -> chapel(p, lv);
            case "watchtower" -> tower(p, lv);
            case "wall" -> wall(p, lv);
            case "barracks" -> guildhall(p, lv);
            case "keep_workshop" -> keep(p, lv);
            case "royal_court" -> palace(p, lv);
            case "siege_works" -> arsenal(p, lv);
            case "crucible" -> crucible(p, lv);
            case "crane" -> crane(p, lv);
            default -> p.fill(0, 0, 0, p.w - 1, 0, p.d - 1, COBBLE);
        }
    }

    // ---------------------------------------------------------------- mine (3x3)
    static void mine(Plan p, int lv) {
        // shaft collar: stone curb round a dark shaft mouth
        p.set(1, -1, 1, s(Blocks.COAL_BLOCK));
        for (int[] c : new int[][]{{0, 1}, {2, 1}}) p.set(c[0], 0, c[1], stair(Blocks.STONE_BRICK_STAIRS, c[0] == 0 ? E : W));
        p.set(1, 0, 0, stair(Blocks.STONE_BRICK_STAIRS, S));
        // windlass: two posts, a log axle with a crank, rope (chain) and bucket over the shaft
        p.pillar(0, 0, 0, 2, SPRUCE_Y).pillar(2, 0, 0, 2, SPRUCE_Y);
        p.fill(0, 3, 0, 2, 3, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        p.set(1, 2, 0, s(Blocks.CHAIN));
        // shingled hood over the windlass
        for (int x = 0; x <= 2; x++) { p.set(x, 4, 0, stair(Blocks.SPRUCE_STAIRS, S)); p.set(x, 4, 1, slab(Blocks.SPRUCE_SLAB, false)); }
        p.set(1, 3, 1, hanging(Blocks.LANTERN));
        // ore pile, cart track out the front, tools
        p.set(0, 0, 2, s(lv >= 3 ? Blocks.RAW_IRON_BLOCK : Blocks.IRON_ORE)).set(1, 0, 2, s(Blocks.RAIL)).set(1, -1, 2, s(Blocks.GRAVEL));
        p.set(2, 0, 2, s(Blocks.BARREL));
        if (lv >= 2) { p.set(2, 1, 2, s(Blocks.LANTERN)); p.set(0, 1, 2, s(Blocks.IRON_ORE)); }
        if (lv >= 3) { p.set(1, 5, 1, s(Blocks.LIGHTNING_ROD)); p.set(0, 0, 1, s(Blocks.RAW_IRON_BLOCK)); }
    }

    // ---------------------------------------------------------------- windmill (5x5): Dutch tower mill
    static void windmill(Plan p, int lv) {
        int top = 6 + lv;   // the mill grows a storey per level
        // octagonal stone base (corners chamfered) then a timber body
        stoneWalls(p, 1, 0, 0, 3, 2, 3, COBBLE, BRICK);
        p.walls(0, 0, 1, 4, 2, 2, COBBLE);
        p.clear(0, 0, 0).clear(4, 0, 0);
        door(p, FRONT, 3, 2, 0, Blocks.SPRUCE_DOOR, null, null);
        window(p, LEFT, 0, 1, 1, 1, Blocks.GLASS_PANE, null);
        window(p, RIGHT, 4, 2, 1, 1, Blocks.GLASS_PANE, null);
        // reefing stage: a plank gallery with railings at y=3, braced under
        p.fill(0, 3, 0, 4, 3, 4, slab(Blocks.SPRUCE_SLAB, true));
        for (int x = 0; x <= 4; x++) { p.set(x, 4, 4, s(Blocks.SPRUCE_FENCE)); }
        for (int z = 0; z <= 3; z++) { p.set(0, 4, z, s(Blocks.SPRUCE_FENCE)); p.set(4, 4, z, s(Blocks.SPRUCE_FENCE)); }
        p.set(0, 2, 4, stairTop(Blocks.SPRUCE_STAIRS, N)).set(4, 2, 4, stairTop(Blocks.SPRUCE_STAIRS, N));
        p.clear(2, 4, 4);
        // timber body, shingle-boarded, with stripped corner posts
        frameWalls(p, 1, 4, 0, 3, top, 2, SPRUCE, Blocks.STRIPPED_SPRUCE_LOG, 2);
        window(p, FRONT, 2, 2, top - 1, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR);
        // boat-shaped cap
        p.pyramid(0, 4, 0, 3, top + 1, Blocks.DARK_OAK_STAIRS, s(Blocks.DARK_OAK_PLANKS));
        p.set(2, top + 3, 1, s(Blocks.LIGHTNING_ROD));
        // windshaft out of the cap, hub, and four lattice sails in a pinwheel
        int hy = top, hz = 3; p.set(2, hy, hz, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z)); p.set(2, hy, 4, s(Blocks.DARK_OAK_FENCE));
        int arm = Math.min(3, hy - 1);
        for (int k = 1; k <= arm; k++) {
            p.set(2, hy + k, 4, s(Blocks.SPRUCE_FENCE)); p.set(3, hy + k, 4, trapdoor(Blocks.BIRCH_TRAPDOOR, S, true, false));
            p.set(2, hy - k, 4, s(Blocks.SPRUCE_FENCE)); p.set(1, hy - k, 4, trapdoor(Blocks.BIRCH_TRAPDOOR, S, true, false));
        }
        for (int k = 1; k <= 2; k++) {
            p.set(2 - k, hy, 4, s(Blocks.SPRUCE_FENCE)); p.set(2 - k, hy + 1, 4, trapdoor(Blocks.BIRCH_TRAPDOOR, S, true, false));
            p.set(2 + k, hy, 4, s(Blocks.SPRUCE_FENCE)); p.set(2 + k, hy - 1, 4, trapdoor(Blocks.BIRCH_TRAPDOOR, S, true, false));
        }
        p.set(2, hy, 4, s(Blocks.DARK_OAK_PLANKS));
        // sacks of flour and a path to the door
        goods(p, 0, 4, 1); p.set(4, 0, 4, s(Blocks.BARREL)); path(p, 2, 4, 2, 4);
        if (lv >= 2) { p.set(4, 1, 4, s(Blocks.COMPOSTER)); garden(p, 0, 0, 4, 4, 18); }
    }

    // ---------------------------------------------------------------- granary (5x5): on staddle stones
    static void granary(Plan p, int lv) {
        for (int x : new int[]{0, 2, 4}) for (int z : new int[]{1, 3}) { p.set(x, 0, z, s(Blocks.COBBLESTONE_WALL)); p.set(x, 1, z, slab(Blocks.SMOOTH_STONE_SLAB, true)); }
        p.fill(0, 2, 1, 4, 2, 3, SPRUCE);
        frameWalls(p, 0, 3, 1, 4, 4, 3, SPRUCE, Blocks.DARK_OAK_LOG, 2);
        doubleDoor(p, FRONT, 3, 2, 3, Blocks.SPRUCE_DOOR);
        // steps up to the threshold
        p.set(0, 0, 4, stair(Blocks.SPRUCE_STAIRS, E)).set(1, 0, 4, SPRUCE).set(1, 1, 4, stair(Blocks.SPRUCE_STAIRS, E)).set(2, 1, 4, slab(Blocks.SPRUCE_SLAB, true)).set(3, 1, 4, slab(Blocks.SPRUCE_SLAB, true));
        // steep thatch-and-slate roof, ridge along x, gables boarded
        int top = roofX(p, 0, 4, 0, 4, 5, SLATE, SLATE_RIDGE, 0, 4, SPRUCE);
        window(p, LEFT, 0, 2, 5, 1, Blocks.GLASS_PANE, null);
        window(p, RIGHT, 4, 2, 5, 1, Blocks.GLASS_PANE, null);
        // hoist beam with chain and a hay bale on the hook
        p.set(4, 6, 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        // stores: hay, sacks, barrels under the raised floor
        goods(p, 1, 2, 1); goods(p, 3, 2, 0); p.set(1, 0, 0, s(Blocks.COMPOSTER)).set(3, 0, 0, log(Blocks.HAY_BLOCK, Direction.Axis.X));
        p.set(4, 0, 4, s(Blocks.BARREL)).set(4, 1, 4, s(Blocks.LANTERN));
        if (lv >= 2) { p.set(0, 0, 0, log(Blocks.HAY_BLOCK, Direction.Axis.Y)).set(0, 1, 0, log(Blocks.HAY_BLOCK, Direction.Axis.Y)); p.set(2, top, 2, s(Blocks.LIGHTNING_ROD)); }
    }

    // ---------------------------------------------------------------- smithy (4x4): bloomery hearth
    static void smithy(Plan p, int lv) {
        p.fill(0, -1, 0, 3, -1, 3, s(Blocks.COARSE_DIRT));
        // back: stone hearth wall with a brick stack
        p.fill(0, 0, 0, 3, 2, 0, COBBLE);
        p.set(1, 0, 0, s(Blocks.BLAST_FURNACE)).set(2, 0, 0, s(Blocks.MAGMA_BLOCK)).set(2, 1, 0, stairTop(Blocks.STONE_BRICK_STAIRS, S));
        chimney(p, 3, 0, 3, 6 + lv, s(Blocks.BRICKS), s(Blocks.BRICK_WALL));
        p.pillar(3, 0, 0, 2, s(Blocks.BRICKS));
        // open-fronted timber shed on posts, lean-to roof sloping to the front
        p.pillar(0, 3, 0, 2, SPRUCE_Y).pillar(3, 3, 0, 2, SPRUCE_Y);
        p.fill(0, 3, 1, 0, 2, 2, SPRUCE).fill(3, 1, 1, 3, 2, 2, SPRUCE);
        for (int x = 0; x <= 3; x++) { p.set(x, 3, 3, stair(Blocks.SPRUCE_STAIRS, S)); p.set(x, 3, 2, SPRUCE); p.set(x, 4, 1, stair(Blocks.SPRUCE_STAIRS, S)); p.set(x, 4, 0, SPRUCE); p.set(x, 5, 0, slab(Blocks.SPRUCE_SLAB, false)); }
        p.set(3, 4, 0, s(Blocks.BRICKS)).set(3, 5, 0, s(Blocks.BRICKS));
        // the work floor: anvil, quench tub, grindstone bench, tools
        p.set(1, 0, 2, s(Blocks.ANVIL)).set(2, 0, 1, waterCauldron()).set(0, 0, 1, s(Blocks.SMITHING_TABLE));
        p.set(1, 2, 1, hanging(Blocks.LANTERN));
        p.set(0, 0, 3, s(Blocks.CHAIN)).set(3, 0, 3, s(Blocks.BARREL));
        if (lv >= 2) { p.set(1, 1, 0, s(Blocks.FURNACE)); p.set(3, 1, 3, s(Blocks.IRON_BLOCK)); p.set(2, 0, 3, log(Blocks.SPRUCE_LOG, Direction.Axis.X)); }
    }

    // ---------------------------------------------------------------- chapel-library (7x7): Gothic
    static void chapel(Plan p, int lv) {
        int h = 4 + lv;
        p.fill(1, 0, 1, 5, 0, 5, s(Blocks.STONE_BRICKS));
        stoneWalls(p, 1, 1, 1, 5, h, 5, s(Blocks.STONE_BRICKS), s(Blocks.POLISHED_ANDESITE));
        // buttresses stepping out from the side walls
        for (int z : new int[]{2, 4}) for (int x : new int[]{0, 6}) {
            p.pillar(x, z, 0, h - 2, s(Blocks.STONE_BRICKS));
            p.set(x, h - 1, z, stair(Blocks.STONE_BRICK_STAIRS, x == 0 ? E : W));
        }
        // tall lancet windows of stained glass between the buttresses
        for (int z : new int[]{3}) { window(p, LEFT, 1, z, 2, h - 2, Blocks.BLUE_STAINED_GLASS_PANE, null); window(p, RIGHT, 5, z, 2, h - 2, Blocks.BLUE_STAINED_GLASS_PANE, null); }
        // west front: pointed doorway, rose window, gabled front
        doubleDoor(p, FRONT, 5, 2, 1, Blocks.DARK_OAK_DOOR);
        p.set(3, 3, 5, s(Blocks.CHISELED_STONE_BRICKS)).set(2, 3, 5, stairTop(Blocks.STONE_BRICK_STAIRS, E)).set(4, 3, 5, stairTop(Blocks.STONE_BRICK_STAIRS, W));
        p.set(3, h, 5, s(Blocks.RED_STAINED_GLASS_PANE));
        plinth(p, 1, 1, 5, 5, 0, Blocks.STONE_BRICK_STAIRS);
        p.clear(2, 0, 6).clear(3, 0, 6);
        path(p, 2, 6, 3, 6);
        // steep slate roof (ridge along z), gable ends in stone
        int top = roofZ(p, 0, 6, 0, 6, h + 1, SLATE, SLATE_RIDGE, 1, 5, s(Blocks.STONE_BRICKS));
        // bell tower over the back with a spire
        int bt = top + 1;
        p.fill(2, h + 1, 1, 4, bt + 2, 3, s(Blocks.STONE_BRICKS));
        p.clearBox(3, bt, 1, 3, bt + 1, 3).clearBox(2, bt, 2, 4, bt + 1, 2);
        p.set(3, bt, 2, s(Blocks.BELL));
        p.pyramid(2, 4, 1, 3, bt + 3, SLATE, s(Blocks.DEEPSLATE_TILE_WALL));
        p.set(3, bt + 5, 2, s(Blocks.LIGHTNING_ROD));
        // library: lecterns and shelves glimpsed through the door
        p.set(2, 1, 2, s(Blocks.BOOKSHELF)).set(4, 1, 2, s(Blocks.BOOKSHELF)).set(3, 1, 3, s(Blocks.LECTERN));
        // churchyard: yews (spruce bushes), flowers, a lamp
        p.add(0, 0, 6, leaves(Blocks.SPRUCE_LEAVES)).add(0, 1, 6, leaves(Blocks.SPRUCE_LEAVES)).add(6, 0, 6, leaves(Blocks.SPRUCE_LEAVES));
        garden(p, 0, 0, 6, 6, 10);
        if (lv >= 2) lampPost(p, 5, 6, 2, Blocks.DARK_OAK_FENCE, Blocks.LANTERN);
        if (lv >= 3) { wallBanner(p, FRONT, 5, 1, h - 1); wallBanner(p, FRONT, 5, 5, h - 1); }
    }

    // ---------------------------------------------------------------- watchtower (3x3): Norman tower + hoarding
    static void tower(Plan p, int lv) {
        int h = 4 + lv;
        stoneWalls(p, 0, 0, 0, 2, h, 2, COBBLE, BRICK);
        p.set(1, 0, 2, s(Blocks.STONE_BRICKS));
        for (int y = 2; y < h; y += 2) { p.set(1, y, 2, s(Blocks.IRON_BARS)); p.set(0, y + 1, 1, s(Blocks.IRON_BARS)); p.set(2, y + 1, 1, s(Blocks.IRON_BARS)); }
        door(p, FRONT, 2, 1, 0, Blocks.DARK_OAK_DOOR, null, null);
        // timber hoarding: a dark-oak gallery with shuttered loopholes
        p.fill(0, h + 1, 0, 2, h + 1, 2, s(Blocks.DARK_OAK_PLANKS));
        p.walls(0, h + 2, 0, 2, h + 3, 2, s(Blocks.DARK_OAK_PLANKS));
        p.corners(0, 0, 2, 2, h + 2, h + 3, s(Blocks.STRIPPED_DARK_OAK_LOG));
        for (int[] c : new int[][]{{1, 0, BACK}, {1, 2, FRONT}, {0, 1, LEFT}, {2, 1, RIGHT}}) {
            Direction o = out(c[2]); p.set(c[0], h + 2, c[1], trapdoor(Blocks.SPRUCE_TRAPDOOR, o, true, false));
        }
        p.set(1, h + 3, 2, s(Blocks.LANTERN));
        // slate cone
        p.pyramid(0, 2, 0, 2, h + 4, SLATE, s(Blocks.DEEPSLATE_TILE_WALL));
        p.set(1, h + 6, 1, s(Blocks.LIGHTNING_ROD));
        if (lv >= 2) wallBanner(p, FRONT, 2, 1, h);
    }

    static void wall(Plan p, int lv) {
        p.set(0, 0, 0, s(Blocks.STONE_BRICKS));
        for (int y = 1; y <= lv; y++) p.set(0, y, 0, COBBLE);
        p.set(0, lv + 1, 0, s(Blocks.STONE_BRICK_WALL));
    }

    // ---------------------------------------------------------------- barracks (7x7): jettied Tudor guildhall
    static void guildhall(Plan p, int lv) {
        // ground storey: stone, inset; upper storeys jetty out over it on every side
        stoneWalls(p, 1, 0, 1, 5, 2, 4, COBBLE, BRICK);
        door(p, FRONT, 4, 3, 0, Blocks.DARK_OAK_DOOR, null, null);
        window(p, FRONT, 4, 1, 1, 1, Blocks.GLASS_PANE, null); window(p, FRONT, 4, 5, 1, 1, Blocks.GLASS_PANE, null);
        window(p, LEFT, 1, 2, 1, 1, Blocks.GLASS_PANE, null); window(p, RIGHT, 5, 3, 1, 1, Blocks.GLASS_PANE, null);
        // jetty: joist ring with knee braces
        for (int x = 0; x <= 6; x++) { p.set(x, 3, 0, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X)); p.set(x, 3, 5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X)); }
        for (int z = 1; z <= 4; z++) { p.set(0, 3, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z)); p.set(6, 3, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z)); }
        p.fill(1, 3, 1, 5, 3, 4, DARK);
        for (int x : new int[]{1, 5}) p.set(x, 2, 5, stairTop(Blocks.DARK_OAK_STAIRS, N));
        for (int z : new int[]{1, 4}) { p.set(0, 2, z, stairTop(Blocks.DARK_OAK_STAIRS, E)); p.set(6, 2, z, stairTop(Blocks.DARK_OAK_STAIRS, W)); }
        p.set(2, 2, 5, hanging(Blocks.LANTERN)).set(4, 2, 5, hanging(Blocks.LANTERN));
        // upper storey: half-timbered plaster
        tudor(p, 0, 4, 0, 6, 6, 5, PLASTER, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
        flowerWindow(p, FRONT, 5, 2, 5, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR, pot(p, 2, 5));
        flowerWindow(p, FRONT, 5, 4, 5, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR, pot(p, 4, 5));
        window(p, LEFT, 0, 2, 5, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR); window(p, LEFT, 0, 4, 5, 1, Blocks.GLASS_PANE, null);
        window(p, RIGHT, 6, 2, 5, 1, Blocks.GLASS_PANE, Blocks.DARK_OAK_TRAPDOOR); window(p, RIGHT, 6, 4, 5, 1, Blocks.GLASS_PANE, null);
        // gable to the street (ridge along z), oriel window in the gable, beam ends
        int top = roofZ(p, 0, 6, 0, 6, 7, SLATE, SLATE_RIDGE, 0, 5, PLASTER);
        p.set(3, 7, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)).set(2, 8, 5, s(Blocks.GLASS_PANE)).set(4, 8, 5, s(Blocks.GLASS_PANE)).set(3, 8, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        p.set(3, 9, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        chimney(p, 5, 1, 7, top + 1, s(Blocks.BRICKS), s(Blocks.BRICK_WALL));
        wallBanner(p, FRONT, 5, 3, 6);
        // yard: woodpile under the jetty, training post, barrels, flowers
        woodpile(p, 0, 0, 1, Blocks.SPRUCE_LOG); p.set(0, 0, 2, s(Blocks.BARREL)).set(0, 0, 4, s(Blocks.BARREL));
        p.set(6, 0, 1, s(Blocks.HAY_BLOCK)).set(6, 0, 2, s(Blocks.HAY_BLOCK)).set(6, 1, 1, s(Blocks.HAY_BLOCK));
        p.set(6, 0, 4, grindstone());
        path(p, 3, 5, 3, 6);
        p.set(0, 0, 6, s(Blocks.SPRUCE_FENCE)).set(0, 1, 6, s(Blocks.CARVED_PUMPKIN)).set(6, 0, 6, s(Blocks.TARGET));
        garden(p, 1, 6, 5, 6, 30);
        if (lv >= 2) { bush(p, 1, 6, 1); bush(p, 5, 6, 1); }
        if (lv >= 3) { p.set(3, top + 1, 3, s(Blocks.LIGHTNING_ROD)); wallBanner(p, LEFT, 0, 3, 6); wallBanner(p, RIGHT, 6, 3, 6); }
    }

    // ---------------------------------------------------------------- keep (9x9): Norman keep
    static void keep(Plan p, int lv) {
        int h = 6 + lv * 2;
        // battered plinth and the keep body
        p.fill(1, 0, 1, 7, 0, 6, BRICK);
        plinth(p, 1, 1, 7, 6, 0, Blocks.STONE_BRICK_STAIRS);
        stoneWalls(p, 1, 1, 1, 7, h, 6, BRICK, s(Blocks.POLISHED_ANDESITE));
        // pilaster strips (Romanesque buttresses) on the long faces
        for (int x : new int[]{3, 5}) { p.pillar(x, 0, 1, h - 1, s(Blocks.POLISHED_ANDESITE)); p.pillar(x, 7, 1, h - 1, s(Blocks.POLISHED_ANDESITE)); }
        // round-headed paired windows each storey
        for (int y = 3; y < h - 1; y += 3) for (int x : new int[]{2, 6}) { window(p, FRONT, 6, x, y, 2, Blocks.GLASS_PANE, null); window(p, BACK, 1, x, y, 2, Blocks.GLASS_PANE, null); }
        for (int y = 3; y < h - 1; y += 3) { window(p, LEFT, 1, 3, y, 2, Blocks.GLASS_PANE, null); window(p, RIGHT, 7, 4, y, 2, Blocks.GLASS_PANE, null); }
        // forebuilding: a covered stair up the front to a first-floor door
        p.fill(2, 0, 7, 6, 3, 7, BRICK);
        for (int k = 0; k < 3; k++) p.set(6 - k, k, 8, stair(Blocks.STONE_BRICK_STAIRS, W));
        p.fill(3, 3, 8, 6, 3, 8, slab(Blocks.STONE_BRICK_SLAB, true));
        door(p, FRONT, 7, 4, 4, Blocks.DARK_OAK_DOOR, null, null);
        p.walls(2, 4, 7, 6, 5, 8, BRICK); p.clearBox(3, 4, 8, 5, 5, 8); p.fill(2, 6, 7, 6, 6, 8, slab(Blocks.STONE_BRICK_SLAB, false));
        p.crenel(2, 7, 6, 8, 6, s(Blocks.STONE_BRICK_WALL));
        // corner turrets rising above the parapet, with slate caps
        int[][] tc = {{0, 0}, {7, 0}, {0, 5}, {7, 5}};
        for (int[] c : tc) {
            p.fill(c[0], 0, c[1], c[0] + 1, h + 3, c[1] + 1, BRICK);
            p.set(c[0], h + 1, c[1], s(Blocks.IRON_BARS));
            p.pyramid(c[0], c[0] + 1, c[1], c[1] + 1, h + 4, SLATE, s(Blocks.DEEPSLATE_TILE_WALL));
            p.set(c[0], h + 2, c[1] + (c[1] == 0 ? 0 : 1), s(Blocks.LANTERN));
        }
        // parapet with merlons and a roof behind
        cornice(p, 2, 1, 6, 5, h + 1, Blocks.STONE_BRICK_STAIRS);
        p.fill(2, h + 1, 2, 6, h + 1, 5, s(Blocks.SMOOTH_STONE));
        p.crenel(1, 1, 7, 6, h + 2, s(Blocks.STONE_BRICK_WALL));
        // workshop smoke and the royal standard
        chimney(p, 4, 3, h + 2, h + 4, s(Blocks.BRICKS), null);
        p.pillar(5, 4, h + 2, h + 6, s(Blocks.DARK_OAK_FENCE));
        wallBanner(p, FRONT, 6, 3, h - 1); wallBanner(p, FRONT, 6, 5, h - 1);
        // bailey: smithing under a lean-to, barrels, lamps
        p.set(0, 0, 8, s(Blocks.ANVIL)).set(1, 0, 8, s(Blocks.BARREL)).set(8, 0, 8, s(Blocks.BARREL)).set(8, 1, 8, s(Blocks.LANTERN));
        p.set(0, 0, 7, s(Blocks.SMITHING_TABLE)).set(8, 0, 7, grindstone());
        path(p, 7, 8, 8, 8);
        if (lv >= 2) for (int x : new int[]{2, 6}) wallBanner(p, LEFT, 1, x == 2 ? 2 : 4, h - 1);
    }

    // ---------------------------------------------------------------- royal court (11x11): Ming palace hall
    static void palace(Plan p, int lv) {
        BlockState marble = s(Blocks.SMOOTH_QUARTZ), red = s(Blocks.RED_TERRACOTTA);
        // xumizuo terrace: two marble tiers with balustrades and a central stair
        p.fill(0, 0, 0, 10, 0, 10, marble);
        p.fill(1, 1, 1, 9, 1, 9, s(Blocks.QUARTZ_BRICKS));
        for (int x = 0; x <= 10; x++) if (Math.abs(x - 5) > 1) { p.set(x, 1, 0, s(Blocks.DIORITE_WALL)); p.set(x, 1, 10, s(Blocks.DIORITE_WALL)); }
        for (int z = 1; z <= 9; z++) { p.set(0, 1, z, s(Blocks.DIORITE_WALL)); p.set(10, 1, z, s(Blocks.DIORITE_WALL)); }
        for (int x = 4; x <= 6; x++) { p.set(x, 0, 10, stair(Blocks.SMOOTH_QUARTZ_STAIRS, N)); p.set(x, 1, 9, stair(Blocks.QUARTZ_STAIRS, N)); }
        // hall: red columns on the veranda, latticed walls behind
        int h = 4 + lv;
        p.walls(2, 2, 2, 8, h, 7, s(Blocks.MANGROVE_PLANKS));
        for (int x = 2; x <= 8; x++) for (int y = 3; y < h; y++) if (x % 2 == 1) p.set(x, y, 7, trapdoor(Blocks.MANGROVE_TRAPDOOR, S, true, false));
        doubleDoor(p, FRONT, 7, 4, 2, Blocks.MANGROVE_DOOR);
        for (int x = 2; x <= 8; x += 2) { p.pillar(x, 8, 2, h, red); p.pillar(x, 1, 2, h, red); }
        for (int z = 2; z <= 7; z += 5) { p.pillar(1, z, 2, h, red); p.pillar(9, z, 2, h, red); }
        // dougong bracket band: gold-trimmed stairs under the eaves
        for (int x = 1; x <= 9; x++) { p.set(x, h + 1, 9, stairTop(Blocks.SPRUCE_STAIRS, N)); p.set(x, h + 1, 0, stairTop(Blocks.SPRUCE_STAIRS, S)); }
        for (int z = 1; z <= 8; z++) { p.set(0, h + 1, z, stairTop(Blocks.SPRUCE_STAIRS, E)); p.set(10, h + 1, z, stairTop(Blocks.SPRUCE_STAIRS, W)); }
        p.fill(1, h + 1, 1, 9, h + 1, 8, s(Blocks.SPRUCE_PLANKS));
        for (int x = 1; x <= 9; x += 2) { p.set(x, h + 1, 9, s(Blocks.RED_TERRACOTTA)); }
        // lower eave: imperial yellow glazed tiles on a curved hip, corners kicked up
        Block glaze = Blocks.BAMBOO_MOSAIC_STAIRS;
        curvedHip(p, 0, 10, 0, 9, h + 2, glaze, Blocks.BAMBOO_MOSAIC_SLAB, s(Blocks.BAMBOO_MOSAIC), s(Blocks.GOLD_BLOCK));
        // upper storey (double eaves): red-and-lattice clerestory under a second, higher curved roof
        int u = h + 4;
        p.walls(3, h + 3, 3, 7, u + 1, 6, s(Blocks.MANGROVE_PLANKS));
        for (int x = 3; x <= 7; x++) for (int y = u; y <= u + 1; y++) p.set(x, y, 6, x % 2 == 0 ? red : trapdoor(Blocks.MANGROVE_TRAPDOOR, S, true, false));
        p.corners(3, 3, 7, 6, h + 3, u + 1, red);
        for (int x = 3; x <= 7; x++) { p.set(x, u + 2, 7, stairTop(Blocks.SPRUCE_STAIRS, N)); p.set(x, u + 2, 2, stairTop(Blocks.SPRUCE_STAIRS, S)); }
        int top = curvedHip(p, 1, 9, 1, 8, u + 2, glaze, Blocks.BAMBOO_MOSAIC_SLAB, s(Blocks.GOLD_BLOCK), s(Blocks.GOLD_BLOCK));
        // ridge beasts (chiwen) at both ends of the main ridge
        p.set(3, top, 4, s(Blocks.GOLD_BLOCK)).set(7, top, 4, s(Blocks.GOLD_BLOCK)).set(3, top + 1, 4, s(Blocks.LIGHTNING_ROD)).set(7, top + 1, 4, s(Blocks.LIGHTNING_ROD));
        // lanterns, guardian lions, bronze incense burners, flowering trees
        for (int x : new int[]{3, 7}) p.set(x, h, 8, hanging(Blocks.LANTERN));
        p.set(3, 2, 9, s(Blocks.CHISELED_QUARTZ_BLOCK)).set(3, 3, 9, s(Blocks.GOLD_BLOCK)).set(7, 2, 9, s(Blocks.CHISELED_QUARTZ_BLOCK)).set(7, 3, 9, s(Blocks.GOLD_BLOCK));
        p.set(1, 2, 9, s(Blocks.CAULDRON)).set(9, 2, 9, s(Blocks.CAULDRON));
        if (lv >= 2) { for (int[] c : new int[][]{{1, 1}, {9, 1}}) { p.set(c[0], 2, c[1], log(Blocks.CHERRY_LOG, Direction.Axis.Y)); p.fill(c[0] - 1, 3, c[1] - 1, c[0] + 1, 3, c[1] + 1, leaves(Blocks.CHERRY_LEAVES)); p.set(c[0], 4, c[1], leaves(Blocks.CHERRY_LEAVES)); } }
        wallBanner(p, FRONT, 7, 3, h - 1); wallBanner(p, FRONT, 7, 7, h - 1);
    }

    // ---------------------------------------------------------------- siege works (11x11): Arsenale yard
    static void arsenal(Plan p, int lv) {
        p.fill(0, -1, 0, 10, -1, 10, s(Blocks.COARSE_DIRT));
        // curtain wall round the yard with a gatehouse on the front
        stoneWalls(p, 0, 0, 0, 10, 3, 10, COBBLE, BRICK);
        p.crenel(0, 0, 10, 10, 4, s(Blocks.STONE_BRICK_WALL));
        // gatehouse: twin towers either side of the gate, portcullis (iron bars) raised
        for (int x : new int[]{2, 7}) { p.fill(x, 0, 9, x + 1, 6, 10, BRICK); p.crenel(x, 9, x + 1, 10, 7, s(Blocks.STONE_BRICK_WALL)); p.set(x, 5, 10, s(Blocks.IRON_BARS)); }
        p.clearBox(4, 0, 10, 6, 3, 10); p.fill(4, 4, 9, 6, 5, 10, BRICK); p.set(5, 4, 10, s(Blocks.CHISELED_STONE_BRICKS));
        for (int x = 4; x <= 6; x++) p.set(x, 3, 10, s(Blocks.IRON_BARS));
        p.crenel(4, 9, 6, 10, 6, s(Blocks.STONE_BRICK_WALL));
        wallBanner(p, FRONT, 10, 5, 3);
        path(p, 4, 6, 6, 10);
        // workshop hall along the back wall: timber over stone, shingle roof
        stoneWalls(p, 1, 0, 1, 9, 1, 3, COBBLE, BRICK);
        frameWalls(p, 1, 2, 1, 9, 3, 3, SPRUCE, Blocks.DARK_OAK_LOG, 2);
        p.clearBox(3, 0, 3, 7, 3, 3);
        for (int x = 3; x <= 7; x += 2) p.pillar(x, 3, 0, 3, DARK_Y);
        roofX(p, 1, 9, 0, 4, 4, Blocks.SPRUCE_STAIRS, slab(Blocks.SPRUCE_SLAB, false), 1, 9, SPRUCE);
        chimney(p, 8, 2, 4, 8, s(Blocks.BRICKS), null);
        p.set(2, 0, 2, s(Blocks.ANVIL)).set(4, 0, 2, s(Blocks.SMITHING_TABLE)).set(6, 0, 2, s(Blocks.BLAST_FURNACE)).set(8, 0, 2, grindstone());
        // trebuchet on the stocks: A-frame, throwing arm, counterweight box
        int tx = 5, tz = 6;
        for (int x : new int[]{tx - 1, tx + 1}) { p.set(x, 0, tz - 1, stair(Blocks.DARK_OAK_STAIRS, S)); p.set(x, 0, tz + 1, stair(Blocks.DARK_OAK_STAIRS, N)); p.pillar(x, tz, 0, 3, DARK_Y); }
        p.fill(tx - 1, 4, tz, tx + 1, 4, tz, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        for (int k = 0; k <= 3; k++) p.set(tx, 4 + k, tz - k + 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        p.set(tx, 5, tz + 1, s(Blocks.COBBLESTONE)).set(tx, 3, tz + 1, s(Blocks.CHAIN)).set(tx, 2, tz + 1, s(Blocks.STONE_BRICKS));
        p.set(tx, 7, tz - 3, s(Blocks.CHAIN));
        // timber stacks, shot, barrels
        woodpile(p, 1, 3, 8, Blocks.SPRUCE_LOG); woodpile(p, 7, 9, 8, Blocks.DARK_OAK_LOG);
        p.set(1, 0, 5, s(Blocks.COBBLESTONE)).set(2, 0, 5, s(Blocks.COBBLESTONE)).set(1, 1, 5, s(Blocks.COBBLESTONE)).set(9, 0, 5, s(Blocks.BARREL)).set(9, 1, 5, s(Blocks.BARREL));
        if (lv >= 2) { for (int[] c : new int[][]{{0, 0}, {10, 0}}) p.set(c[0], 5, c[1], s(Blocks.LANTERN)); p.set(9, 0, 6, s(Blocks.TARGET)); }
    }

    // ---------------------------------------------------------------- crucible (6x6): alchemist's dome
    static void crucible(Plan p, int lv) {
        BlockState copper = s(Blocks.WAXED_WEATHERED_CUT_COPPER), copperOld = s(Blocks.WAXED_OXIDIZED_CUT_COPPER);
        // octagonal drum of stone brick on a plinth
        p.disc(2.5, 2.5, 2.6, 0, BRICK, false);
        for (int y = 1; y <= 4; y++) p.disc(2.5, 2.5, 2.6, y, y == 1 ? s(Blocks.POLISHED_ANDESITE) : BRICK, true);
        // tall round-headed windows glowing from the athanor inside
        for (int[] c : new int[][]{{2, 0}, {3, 5}, {0, 2}, {5, 3}}) { p.set(c[0], 2, c[1], s(Blocks.ORANGE_STAINED_GLASS_PANE)); p.set(c[0], 3, c[1], s(Blocks.ORANGE_STAINED_GLASS_PANE)); }
        doubleDoor(p, FRONT, 5, 2, 1, Blocks.SPRUCE_DOOR);
        p.set(2, 3, 5, s(Blocks.CHISELED_STONE_BRICKS)).set(3, 3, 5, s(Blocks.CHISELED_STONE_BRICKS));
        // cornice and the ribbed copper dome on its drum, lantern (cupola) on top
        p.disc(2.5, 2.5, 2.6, 5, s(Blocks.POLISHED_ANDESITE), true);
        int dt = dome(p, 2.5, 2.5, new double[]{2.6, 2.0, 1.4, 0.8}, 6, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, copper);
        for (int[] c : new int[][]{{2, 0}, {3, 5}, {0, 3}, {5, 2}}) p.set(c[0], 6, c[1], copperOld);   // ribs
        p.fill(2, dt, 2, 3, dt, 3, s(Blocks.POLISHED_ANDESITE));
        p.corners(2, 2, 3, 3, dt + 1, dt + 1, s(Blocks.ANDESITE_WALL));
        p.fill(2, dt + 2, 2, 3, dt + 2, 3, s(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB));
        p.set(2, dt + 1, 2, s(Blocks.LANTERN)).set(3, dt + 1, 3, s(Blocks.LANTERN));
        p.set(2, dt + 3, 3, s(Blocks.LIGHTNING_ROD));
        // the athanor: a crucible of light in the middle, alembics around
        p.set(2, 1, 2, s(Blocks.LAVA_CAULDRON)).set(3, 1, 3, s(Blocks.SHROOMLIGHT)).set(2, 1, 3, s(Blocks.BREWING_STAND)).set(3, 1, 2, s(Blocks.BREWING_STAND));
        // vents on the dome, chains and an amethyst still outside
        chimney(p, 0, 0, 1, 7 + lv, s(Blocks.BRICKS), null);
        p.set(5, 0, 5, s(Blocks.AMETHYST_BLOCK)).set(5, 1, 5, s(Blocks.AMETHYST_CLUSTER));
        p.set(0, 0, 5, s(Blocks.CAULDRON)).set(5, 0, 0, s(Blocks.BARREL));
        if (lv >= 2) { p.set(5, 1, 0, s(Blocks.BREWING_STAND)); chimney(p, 5, 1, 4, 9, s(Blocks.BRICKS), null); }
    }

    // ---------------------------------------------------------------- crane (4x4): treadwheel crane
    static void crane(Plan p, int lv) {
        p.fill(0, 0, 0, 3, 0, 3, slab(Blocks.STONE_BRICK_SLAB, false));
        p.fill(0, 0, 0, 3, 0, 0, BRICK);
        // the great treadwheel standing in the x-y plane, men walk inside it to wind the rope
        int[][] ring = {{1, 1}, {2, 1}, {0, 2}, {3, 2}, {0, 3}, {3, 3}, {1, 4}, {2, 4}};
        for (int[] c : ring) p.set(c[0], c[1], 1, s(Blocks.SPRUCE_PLANKS));
        p.set(1, 2, 1, s(Blocks.SPRUCE_FENCE)).set(2, 3, 1, s(Blocks.SPRUCE_FENCE));
        p.set(1, 3, 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)).set(2, 2, 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        // A-frames either side carrying the axle, braced to the base
        for (int x : new int[]{0, 3}) { p.pillar(x, 0, 1, 5, DARK_Y); p.set(x, 1, 1, stair(Blocks.DARK_OAK_STAIRS, S)); }
        p.fill(0, 6, 0, 3, 6, 0, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        p.set(1, 5, 0, stairTop(Blocks.DARK_OAK_STAIRS, E)).set(2, 5, 0, stairTop(Blocks.DARK_OAK_STAIRS, W));
        for (int x = 0; x <= 3; x++) p.set(x, 7, 0, slab(Blocks.SPRUCE_SLAB, false));
        // jib reaching out over the front, rope and a stone block on the hook
        p.fill(2, 6, 1, 2, 6, 3, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)); p.set(2, 6, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)); p.set(2, 5, 3, s(Blocks.CHAIN)).set(2, 4, 3, s(Blocks.CHAIN)).set(2, 3, 3, s(Blocks.CHAIN)).set(2, 2, 3, s(Blocks.STONE_BRICKS));
        p.set(2, 5, 2, stairTop(Blocks.SPRUCE_STAIRS, N));
        // building stores: dressed stone, timber, a mason's bench
        p.set(0, 1, 3, s(Blocks.STONE_BRICKS)).set(0, 2, 3, slab(Blocks.STONE_BRICK_SLAB, false)).set(3, 1, 3, s(Blocks.STONECUTTER));
        p.set(3, 1, 0, s(Blocks.BARREL));
        if (lv >= 2) { p.set(0, 1, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X)).set(1, 1, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X)); p.set(3, 2, 3, s(Blocks.LANTERN)); wallBanner(p, FRONT, 2, 3, 4); }
    }
}
