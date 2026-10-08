package dev.beyondtabs.mod;

import static dev.beyondtabs.mod.Architecture.*;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BRONZEBORN CLANS — tribes, the ancient Greeks and the Norse. Every building follows a real type:
 * <ul>
 * <li>mine: Bronze Age copper pit (Great Orme, Wales) with timber shoring and a hide-basket hoist</li>
 * <li>energy: Iron Age roundhouse (Butser Farm) — wattle-and-daub ring, conical thatch, smoke through the apex</li>
 * <li>storage: Norwegian stabbur — storehouse raised on posts with an overhanging loft and a flowering sod roof</li>
 * <li>converter: bowl furnace and clay-domed smelting kiln with bellows and ingot moulds</li>
 * <li>tech: Doric temple (Temple of Hephaestus) — stepped stylobate, triglyph frieze, terracotta roof</li>
 * <li>watchtower: stilted tribal lookout with woven railings, bone trophies and a thatched hat</li>
 * <li>wall: sharpened timber palisade</li>
 * <li>barracks: Trelleborg longhouse — bowed walls, raking outer posts, crossed-prow gables</li>
 * <li>war lodge: Borgund-style stave hall — tiered tarred shingle roofs and dragon-head gables</li>
 * <li>hall of legends: peripteral temple (the Parthenon) fronted by a bronze colossus</li>
 * <li>siege yard: helepolis (siege tower) on the stocks beside a bolt-thrower workshop</li>
 * <li>heartforge (advanced energy): Hephaestus' forge in a basalt mound, magma veins and a bronze anvil</li>
 * <li>drum circle (assist): trilithon stone ring round a fire with great war drums</li>
 * </ul>
 * Palette: spruce and dark oak, mossy cobble, mud brick and thatch, quartz and sandstone, bronze (waxed copper).
 */
final class ArchBronze {
    private ArchBronze() { }

    static final BlockState COBBLE = s(Blocks.COBBLESTONE), PLANK = s(Blocks.SPRUCE_PLANKS), TAR = s(Blocks.DARK_OAK_PLANKS),
            MUD = s(Blocks.MUD_BRICKS), DAUB = s(Blocks.PACKED_MUD), BRONZE = s(Blocks.WAXED_EXPOSED_CUT_COPPER), BRONZE_BLOCK = s(Blocks.WAXED_EXPOSED_COPPER);
    static final BlockState SPRUCE_Y = log(Blocks.SPRUCE_LOG, Direction.Axis.Y), STRIP_Y = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);

    static void build(Plan p, String kind, int lv) {
        switch (kind) {
            case "metal_extractor" -> copperPit(p, lv);
            case "energy_gen" -> roundhouse(p, lv);
            case "storage" -> stabbur(p, lv);
            case "converter" -> kiln(p, lv);
            case "tech_center" -> doric(p, lv);
            case "watchtower" -> lookout(p, lv);
            case "wall" -> palisade(p, lv);
            case "barracks" -> longhouse(p, lv);
            case "war_lodge" -> staveHall(p, lv);
            case "hall_of_legends" -> parthenon(p, lv);
            case "siege_yard" -> siegeYard(p, lv);
            case "heartforge" -> heartforge(p, lv);
            case "drum_circle" -> drumCircle(p, lv);
            default -> p.fill(0, 0, 0, p.w - 1, 0, p.d - 1, COBBLE);
        }
    }

    // ---------------------------------------------------------------- copper pit (3x3)
    static void copperPit(Plan p, int lv) {
        p.fill(0, -1, 0, 2, -1, 2, s(Blocks.COARSE_DIRT));
        p.set(1, -1, 1, s(Blocks.COPPER_ORE)).set(0, -1, 1, s(Blocks.GRAVEL));
        // shoring frame: four posts, cross beams, a hide-basket hoist on a rope
        p.corners(0, 0, 2, 2, 0, 2, STRIP_Y);
        p.fill(0, 3, 0, 2, 3, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X)).fill(0, 3, 2, 2, 3, 2, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        p.set(1, 3, 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)).set(1, 2, 1, s(Blocks.CHAIN)).set(1, 1, 1, s(Blocks.CHAIN)).set(1, 0, 1, s(Blocks.BARREL));
        // spoil heap and ore pile, bone tools, a skull ward on a post
        p.set(1, 0, 0, s(Blocks.RAW_COPPER_BLOCK)).set(0, 0, 1, s(Blocks.COPPER_ORE)).set(2, 0, 1, s(Blocks.COBBLESTONE));
        p.set(1, 0, 2, s(Blocks.BONE_BLOCK)).set(1, 4, 1, s(Blocks.SKELETON_SKULL));
        if (lv >= 2) { p.set(2, 1, 1, s(Blocks.RAW_COPPER_BLOCK)); p.set(0, 4, 0, s(Blocks.CAMPFIRE)); }
        if (lv >= 3) { p.set(0, 1, 1, BRONZE_BLOCK); p.set(2, 4, 2, s(Blocks.LANTERN)); }
    }

    // ---------------------------------------------------------------- roundhouse (4x4)
    static void roundhouse(Plan p, int lv) {
        double c = 1.5;
        p.disc(c, c, 1.9, -1, s(Blocks.PACKED_MUD), false);
        // wattle-and-daub ring with timber posts, a porch door to the front
        for (int y = 0; y <= 1; y++) p.disc(c, c, 1.9, y, DAUB, true);
        for (int[] q : new int[][]{{0, 1}, {3, 2}, {1, 0}, {2, 3}}) p.pillar(q[0], q[1], 0, 1, STRIP_Y);
        door(p, FRONT, 3, 1, 0, Blocks.SPRUCE_DOOR, null, null);
        p.set(2, 0, 3, STRIP_Y).set(2, 1, 3, STRIP_Y);
        // conical thatch with a spruce eave ring and the hearth smoke rising through the apex
        int top = dome(p, c, c, new double[]{2.2, 1.6, 1.0, 0.5}, 2, Blocks.BAMBOO_STAIRS, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
        p.set(1, top - 1, 1, s(Blocks.CAMPFIRE));
        p.set(2, 0, 1, s(Blocks.CAMPFIRE));
        // life: woodpile, a drying rack, hides
        p.set(0, 0, 3, log(Blocks.SPRUCE_LOG, Direction.Axis.X)).set(3, 0, 0, s(Blocks.BARREL));
        if (lv >= 2) { p.set(0, 1, 3, log(Blocks.SPRUCE_LOG, Direction.Axis.X)); p.set(3, 1, 0, s(Blocks.SKELETON_SKULL)); }
        if (lv >= 3) { p.set(0, 0, 0, s(Blocks.HAY_BLOCK)); garden(p, 0, 0, 3, 3, 25); }
    }

    // ---------------------------------------------------------------- stabbur (5x5)
    static void stabbur(Plan p, int lv) {
        // stone pads and timber legs: the store stands clear of the ground (rats, damp)
        for (int x : new int[]{1, 3}) for (int z : new int[]{1, 3}) { p.set(x, 0, z, s(Blocks.COBBLESTONE)); p.set(x, 1, z, STRIP_Y); }
        // ground store
        p.fill(1, 2, 1, 3, 2, 3, PLANK);
        frameWalls(p, 1, 3, 1, 3, 4, 3, s(Blocks.STRIPPED_SPRUCE_LOG), Blocks.SPRUCE_LOG, 2);
        door(p, FRONT, 3, 2, 3, Blocks.SPRUCE_DOOR, null, null);
        // loft overhanging on every side, carried on upside-down stair brackets
        p.fill(0, 5, 0, 4, 5, 4, TAR);
        for (int x = 0; x <= 4; x++) { p.set(x, 4, 0, stairTop(Blocks.DARK_OAK_STAIRS, S)); p.set(x, 4, 4, stairTop(Blocks.DARK_OAK_STAIRS, N)); }
        for (int z = 1; z <= 3; z++) { p.set(0, 4, z, stairTop(Blocks.DARK_OAK_STAIRS, E)); p.set(4, 4, z, stairTop(Blocks.DARK_OAK_STAIRS, W)); }
        frameWalls(p, 0, 6, 0, 4, 6, 4, PLANK, Blocks.DARK_OAK_LOG, 2);
        window(p, FRONT, 4, 2, 6, 1, Blocks.GLASS_PANE, null);
        // sod roof: stepped turf, grass on the crest, flowers growing in it; carved bargeboards
        for (int l = 0; l < 3; l++) {
            for (int z = 0; z <= 4; z++) { p.set(l, 7 + l, z, s(l == 2 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK)); p.set(4 - l, 7 + l, z, s(l == 2 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK)); }
        }
        for (int z = 0; z <= 4; z += 2) p.set(1 + (z % 4 == 0 ? 0 : 2), 9, z, s(FLOWERS[p.rnd(1, 9, z, FLOWERS.length)]));
        p.set(2, 10, 1, s(Blocks.GRASS)).set(2, 10, 3, s(FLOWERS[p.rnd(2, 10, 3, FLOWERS.length)]));
        for (int z : new int[]{0, 4}) { p.set(1, 8, z, PLANK); p.set(3, 8, z, PLANK); p.set(2, 8, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z)); p.set(2, 9, z, PLANK); }
        // steps, sacks, a cart of logs
        p.set(2, 0, 4, stair(Blocks.SPRUCE_STAIRS, N)).set(2, 1, 4, stair(Blocks.SPRUCE_STAIRS, N));
        p.set(0, 0, 4, s(Blocks.BARREL)).set(4, 0, 4, log(Blocks.HAY_BLOCK, Direction.Axis.Y)).set(0, 0, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.X)).set(4, 0, 0, s(Blocks.COMPOSTER));
        if (lv >= 2) { p.set(4, 1, 4, s(Blocks.HAY_BLOCK)); wallBanner(p, LEFT, 0, 2, 6); garden(p, 0, 0, 4, 4, 20); }
    }

    // ---------------------------------------------------------------- smelting kiln (4x4)
    static void kiln(Plan p, int lv) {
        p.fill(0, -1, 0, 3, -1, 3, s(Blocks.PACKED_MUD));
        // clay dome over a bowl furnace; a glowing mouth on the front
        for (int y = 0; y <= 1; y++) p.disc(1.5, 1.2, 1.6, y, MUD, true);
        int top = dome(p, 1.5, 1.2, new double[]{1.6, 1.1, 0.5}, 2, Blocks.MUD_BRICK_STAIRS, MUD);
        p.set(1, 0, 2, s(Blocks.MAGMA_BLOCK)).set(2, 0, 2, s(Blocks.BLAST_FURNACE)).set(1, 1, 2, stairTop(Blocks.MUD_BRICK_STAIRS, S));
        chimney(p, 2, 0, 2, 4 + lv, s(Blocks.BRICKS), null);
        // bellows (barrel and composter), ingot moulds of bronze, a quench trough
        p.set(0, 0, 3, s(Blocks.BARREL)).set(3, 0, 3, waterCauldron()).set(2, 0, 3, BRONZE).set(3, 0, 1, s(Blocks.ANVIL));
        p.set(0, 0, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Z)).set(0, 1, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        if (lv >= 2) { p.set(3, 0, 2, BRONZE_BLOCK).set(3, 1, 2, s(Blocks.LIGHTNING_ROD)); }
    }

    // ---------------------------------------------------------------- Doric temple (7x7)
    static void doric(Plan p, int lv) {
        BlockState marble = s(Blocks.SMOOTH_QUARTZ);
        // three-step stylobate
        p.fill(0, 0, 0, 6, 0, 6, marble);
        for (int x = 0; x <= 6; x++) p.set(x, 0, 6, stair(Blocks.SMOOTH_QUARTZ_STAIRS, N));
        p.fill(1, 1, 0, 5, 1, 5, s(Blocks.QUARTZ_BRICKS));
        for (int x = 1; x <= 5; x++) p.set(x, 1, 5, stair(Blocks.QUARTZ_STAIRS, N));
        int colH = 4 + lv;
        // fluted columns with bases and capitals, front and back porticos and side peristyle
        for (int x = 1; x <= 5; x += 2) for (int z : new int[]{0, 4}) {
            p.set(x, 2, z, s(Blocks.CHISELED_QUARTZ_BLOCK)); p.pillar(x, z, 3, colH, s(Blocks.QUARTZ_PILLAR)); p.set(x, colH + 1, z, s(Blocks.CHISELED_QUARTZ_BLOCK));
        }
        for (int z = 2; z <= 2; z++) for (int x : new int[]{1, 5}) { p.set(x, 2, z, s(Blocks.CHISELED_QUARTZ_BLOCK)); p.pillar(x, z, 3, colH, s(Blocks.QUARTZ_PILLAR)); p.set(x, colH + 1, z, s(Blocks.CHISELED_QUARTZ_BLOCK)); }
        // cella with a bronze door and a glowing hearth of Hestia inside
        stoneWalls(p, 2, 2, 1, 4, colH, 3, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE));
        doubleDoor(p, FRONT, 3, 3, 2, Blocks.SPRUCE_DOOR);
        // entablature: architrave, triglyph frieze (alternating chiselled and cut sandstone), cornice
        p.fill(1, colH + 2, 0, 5, colH + 2, 4, s(Blocks.CUT_SANDSTONE));
        for (int x = 1; x <= 5; x++) { p.set(x, colH + 3, 4, s(x % 2 == 1 ? Blocks.CHISELED_SANDSTONE : Blocks.CUT_SANDSTONE)); p.set(x, colH + 3, 0, s(x % 2 == 1 ? Blocks.CHISELED_SANDSTONE : Blocks.CUT_SANDSTONE)); }
        for (int z = 1; z <= 3; z++) { p.set(1, colH + 3, z, s(Blocks.CUT_SANDSTONE)); p.set(5, colH + 3, z, s(Blocks.CUT_SANDSTONE)); }
        for (int x = 0; x <= 6; x++) { p.set(x, colH + 3, 5, stairTop(Blocks.SMOOTH_SANDSTONE_STAIRS, N)); }
        for (int z = 0; z <= 4; z++) { p.set(0, colH + 3, z, stairTop(Blocks.SMOOTH_SANDSTONE_STAIRS, E)); p.set(6, colH + 3, z, stairTop(Blocks.SMOOTH_SANDSTONE_STAIRS, W)); }
        // terracotta-tiled pediment roof, gold acroteria
        int top = roofZ(p, 0, 6, 0, 5, colH + 4, Blocks.RED_SANDSTONE_STAIRS, s(Blocks.CUT_RED_SANDSTONE), 0, 5, s(Blocks.SMOOTH_SANDSTONE));
        p.set(3, colH + 5, 5, s(Blocks.GOLD_BLOCK));
        p.set(3, top, 5, s(Blocks.GOLD_BLOCK)).set(0, colH + 4, 5, s(Blocks.GOLD_BLOCK)).set(6, colH + 4, 5, s(Blocks.GOLD_BLOCK));
        // altar of burnt offering before the steps, braziers on bronze tripods
        p.set(3, 0, 6, s(Blocks.CHISELED_QUARTZ_BLOCK)).set(3, 1, 6, s(Blocks.CAMPFIRE));
        if (lv >= 2) for (int x : new int[]{0, 6}) { p.set(x, 1, 6, s(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB)); p.set(x, 2, 6, s(Blocks.CAMPFIRE)); }
        if (lv >= 3) for (int x : new int[]{0, 6}) { p.pillar(x, 0, 1, 3, leaves(Blocks.SPRUCE_LEAVES)); p.set(x, 4, 0, leaves(Blocks.SPRUCE_LEAVES)); }
    }

    // ---------------------------------------------------------------- lookout (3x3)
    static void lookout(Plan p, int lv) {
        int h = 3 + lv;
        p.corners(0, 0, 2, 2, 0, h, SPRUCE_Y);
        p.set(0, 0, 0, s(Blocks.MOSSY_COBBLESTONE)).set(2, 0, 2, s(Blocks.MOSSY_COBBLESTONE));
        // cross-bracing and a ladder up the back
        for (int y = 1; y < h; y += 2) { p.set(1, y, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X)); p.set(0, y + 1, 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)); p.set(2, y + 1, 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)); }
        // platform, woven railing (trapdoors), hide shades, thatched hat
        p.fill(0, h, 0, 2, h, 2, PLANK);
        for (int[] q : new int[][]{{1, 0, BACK}, {1, 2, FRONT}, {0, 1, LEFT}, {2, 1, RIGHT}}) p.set(q[0], h + 1, q[1], trapdoor(Blocks.SPRUCE_TRAPDOOR, out(q[2]), true, false));
        p.corners(0, 0, 2, 2, h + 1, h + 2, SPRUCE_Y);
        p.pyramid(0, 2, 0, 2, h + 3, Blocks.SPRUCE_STAIRS, s(Blocks.HAY_BLOCK));
        p.set(1, h + 5, 1, s(Blocks.SKELETON_SKULL));
        p.set(1, h + 2, 1, hanging(Blocks.LANTERN));
        p.set(0, h + 3, 0, s(Blocks.BONE_BLOCK)).set(2, h + 3, 2, s(Blocks.BONE_BLOCK));
        if (lv >= 2) wallBanner(p, FRONT, 2, 1, h - 1);
        if (lv >= 3) { p.set(0, h + 1, 2, s(Blocks.SKELETON_SKULL)); p.set(2, h + 1, 0, s(Blocks.SKELETON_SKULL)); }
    }

    static void palisade(Plan p, int lv) {
        if (lv >= 2) p.set(0, 0, 0, s(Blocks.MOSSY_COBBLESTONE));
        int y0 = lv >= 2 ? 1 : 0;
        p.pillar(0, 0, y0, y0 + 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y)).set(0, y0 + 3, 0, s(Blocks.SPRUCE_FENCE));
    }

    // ---------------------------------------------------------------- longhouse (7x7): Trelleborg
    static void longhouse(Plan p, int lv) {
        int h = 3;
        // bowed walls: narrower at the gable ends than amidships (the boat-shaped Trelleborg plan)
        int[] lo = {2, 1, 1, 1, 1, 1, 2}, hi = {4, 5, 5, 5, 5, 5, 4};
        for (int z = 0; z <= 5; z++) {
            for (int y = 0; y <= h; y++) {
                BlockState wall = y == 0 ? COBBLE : (y == h ? log(Blocks.DARK_OAK_LOG, Direction.Axis.Z) : PLANK);
                p.set(lo[z], y, z, wall); p.set(hi[z], y, z, wall);
            }
        }
        for (int x = 2; x <= 4; x++) for (int y = 0; y <= h; y++) { p.set(x, y, 0, y == 0 ? COBBLE : PLANK); p.set(x, y, 5, y == 0 ? COBBLE : PLANK); }
        for (int z = 1; z <= 4; z += 3) { p.pillar(lo[z], z, 1, h, SPRUCE_Y); p.pillar(hi[z], z, 1, h, SPRUCE_Y); }
        // raking outer posts leaning on the walls (the Trelleborg buttress posts)
        for (int z = 1; z <= 4; z++) { p.set(0, 1, z, stair(Blocks.SPRUCE_STAIRS, E)); p.set(6, 1, z, stair(Blocks.SPRUCE_STAIRS, W)); }
        door(p, FRONT, 5, 3, 1, Blocks.SPRUCE_DOOR, null, Blocks.LANTERN);
        window(p, LEFT, 1, 2, 2, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR); window(p, RIGHT, 5, 3, 2, 1, Blocks.GLASS_PANE, Blocks.SPRUCE_TRAPDOOR);
        // steep shingle roof right down over the walls, ridge along z, overhanging the gables
        int top = roofZ(p, 0, 6, 0, 6, h + 1, Blocks.DARK_OAK_STAIRS, s(Blocks.STRIPPED_DARK_OAK_LOG), 1, 5, PLANK);
        // crossed prow boards and dragon heads at both gables
        for (int z : new int[]{0, 6}) { p.set(3, top, z, s(Blocks.DARK_OAK_FENCE)); p.set(2, top, z, stair(Blocks.DARK_OAK_STAIRS, E)); p.set(4, top, z, stair(Blocks.DARK_OAK_STAIRS, W)); p.set(3, top + 1, z, s(Blocks.SKELETON_SKULL)); }
        // round shields (team banners) along the eaves, hearth smoke, a yard with a woodpile
        wallBanner(p, FRONT, 5, 2, 3); wallBanner(p, FRONT, 5, 4, 3);
        chimney(p, 3, 2, h + 1, top, COBBLE, null);
        p.set(0, 0, 6, s(Blocks.BARREL)).set(6, 0, 6, log(Blocks.HAY_BLOCK, Direction.Axis.Y)).set(6, 1, 6, s(Blocks.CARVED_PUMPKIN));
        path(p, 3, 6, 3, 6);
        if (lv >= 2) { woodpile(p, 0, 0, 0, Blocks.SPRUCE_LOG); p.set(6, 0, 0, s(Blocks.BARREL)); p.set(0, 0, 5, grindstone()); }
        if (lv >= 3) { p.set(1, 0, 6, s(Blocks.TARGET)); garden(p, 0, 0, 6, 6, 12); }
    }

    // ---------------------------------------------------------------- stave hall (9x9): Borgund
    static void staveHall(Plan p, int lv) {
        BlockState tarY = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
        // stone sill and a covered ambulatory (svalgang) round the hall
        p.fill(0, 0, 0, 8, 0, 8, COBBLE);
        p.walls(0, 1, 0, 8, 1, 8, s(Blocks.DARK_OAK_FENCE));
        for (int x = 0; x <= 8; x += 2) { p.pillar(x, 0, 1, 2, tarY); p.pillar(x, 8, 1, 2, tarY); }
        for (int z = 2; z <= 6; z += 2) { p.pillar(0, z, 1, 2, tarY); p.pillar(8, z, 1, 2, tarY); }
        p.clearBox(3, 1, 8, 5, 1, 8);
        p.pyramid(0, 8, 0, 8, 3, Blocks.DARK_OAK_STAIRS, TAR);
        // the nave: tall tarred stave walls rising through the first roof
        frameWalls(p, 2, 1, 2, 6, 7, 6, TAR, Blocks.DARK_OAK_LOG, 2);
        doubleDoor(p, FRONT, 6, 3, 1, Blocks.DARK_OAK_DOOR); p.set(4, 1, 6, s(Blocks.DARK_OAK_PLANKS));
        for (int x : new int[]{3, 5}) window(p, FRONT, 6, x, 5, 1, Blocks.GLASS_PANE, null);
        // second roof tier
        p.pyramid(1, 7, 1, 7, 8, Blocks.DARK_OAK_STAIRS, TAR);
        // the tower: a stave lantern with louvres, a third roof and a tall spire
        frameWalls(p, 3, 9, 3, 5, 11, 5, TAR, Blocks.DARK_OAK_LOG, 2);
        for (int[] q : new int[][]{{4, 3, BACK}, {4, 5, FRONT}, {3, 4, LEFT}, {5, 4, RIGHT}}) p.set(q[0], 10, q[1], trapdoor(Blocks.DARK_OAK_TRAPDOOR, out(q[2]), true, false));
        p.pyramid(2, 6, 2, 6, 12, Blocks.DARK_OAK_STAIRS, TAR);
        p.pillar(4, 4, 14, 16, s(Blocks.DARK_OAK_FENCE));
        // dragon heads (carved prows) on the gable corners of every tier
        for (int[] q : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) p.set(q[0], 3, q[1], s(Blocks.SKELETON_SKULL));
        for (int[] q : new int[][]{{1, 1}, {7, 1}, {1, 7}, {7, 7}}) { p.set(q[0], 8, q[1], log(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); p.set(q[0], 9, q[1], s(Blocks.SKELETON_SKULL)); }
        // shields along the gallery, braziers at the door
        for (int x : new int[]{2, 6}) wallBanner(p, FRONT, 6, x, 6);
        p.set(2, 1, 8, s(Blocks.CAMPFIRE)).set(6, 1, 8, s(Blocks.CAMPFIRE));
        if (lv >= 2) { p.set(4, 17, 4, s(Blocks.LIGHTNING_ROD)); p.set(4, 7, 7, hanging(Blocks.LANTERN)); }
        if (lv >= 3) for (int x : new int[]{2, 6}) { wallBanner(p, LEFT, 2, x, 6); wallBanner(p, RIGHT, 6, x, 6); }
    }

    // ---------------------------------------------------------------- Parthenon + colossus (11x11)
    static void parthenon(Plan p, int lv) {
        BlockState marble = s(Blocks.SMOOTH_QUARTZ);
        int d0 = 0, d1 = 7, colH = 4 + lv * 2;
        p.fill(0, 0, d0, 10, 0, d1 + 1, marble);
        for (int x = 0; x <= 10; x++) p.set(x, 0, d1 + 1, stair(Blocks.SMOOTH_QUARTZ_STAIRS, N));
        p.fill(1, 1, d0, 9, 1, d1, s(Blocks.QUARTZ_BRICKS));
        for (int x = 1; x <= 9; x++) p.set(x, 1, d1, stair(Blocks.QUARTZ_STAIRS, N));
        // peristyle: columns all round
        for (int x = 1; x <= 9; x += 2) for (int z : new int[]{d0, d1 - 1}) col(p, x, z, colH);
        for (int z = d0 + 2; z <= d1 - 3; z += 2) for (int x : new int[]{1, 9}) col(p, x, z, colH);
        // cella with bronze doors; the treasury glows through
        stoneWalls(p, 3, 2, d0 + 2, 7, colH, d1 - 2, s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE));
        doubleDoor(p, FRONT, d1 - 2, 5, 2, Blocks.SPRUCE_DOOR);
        p.set(5, 4, d1 - 2, s(Blocks.GOLD_BLOCK));
        // entablature with frieze, then a terracotta roof and gilded pediment
        p.fill(1, colH + 2, d0, 9, colH + 2, d1 - 1, s(Blocks.CUT_SANDSTONE));
        for (int x = 1; x <= 9; x++) p.set(x, colH + 3, d1 - 1, s(x % 2 == 1 ? Blocks.CHISELED_SANDSTONE : Blocks.CUT_SANDSTONE));
        for (int x = 0; x <= 10; x++) p.set(x, colH + 3, d1, stairTop(Blocks.SMOOTH_SANDSTONE_STAIRS, N));
        int top = roofZ(p, 0, 10, d0, d1, colH + 4, Blocks.RED_SANDSTONE_STAIRS, s(Blocks.CUT_RED_SANDSTONE), d0, d1, s(Blocks.SMOOTH_SANDSTONE));
        for (int x = 3; x <= 7; x++) p.set(x, colH + 5, d1, s(x == 5 ? Blocks.GOLD_BLOCK : Blocks.CHISELED_SANDSTONE));
        p.set(5, top, d1, s(Blocks.GOLD_BLOCK)).set(5, top, d0, s(Blocks.GOLD_BLOCK));
        // the bronze colossus in the forecourt: plinth, legs, cuirass, shield and raised spear
        int sx = 5, sz = 9;
        p.fill(sx - 1, 0, sz, sx + 1, 1, sz + 1, s(Blocks.CHISELED_QUARTZ_BLOCK));
        p.set(sx - 1, 2, sz, BRONZE_BLOCK).set(sx + 1, 2, sz, BRONZE_BLOCK).set(sx - 1, 3, sz, BRONZE_BLOCK).set(sx + 1, 3, sz, BRONZE_BLOCK);
        p.fill(sx - 1, 4, sz, sx + 1, 6, sz, BRONZE_BLOCK);
        p.set(sx - 2, 5, sz, BRONZE).set(sx + 2, 5, sz, BRONZE).set(sx - 2, 4, sz + 1, s(Blocks.WAXED_OXIDIZED_COPPER)).set(sx - 2, 5, sz + 1, s(Blocks.WAXED_OXIDIZED_COPPER));
        p.set(sx, 7, sz, s(Blocks.WAXED_EXPOSED_COPPER)).set(sx, 8, sz, s(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB));
        p.pillar(sx + 2, sz, 6, 9, s(Blocks.LIGHTNING_ROD));
        // braziers and cypresses
        for (int x : new int[]{1, 9}) { p.set(x, 0, 10, s(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB)); p.set(x, 1, 10, s(Blocks.CAMPFIRE)); }
        for (int x : new int[]{0, 10}) { p.pillar(x, 9, 0, 2, leaves(Blocks.SPRUCE_LEAVES)); p.set(x, 3, 9, leaves(Blocks.SPRUCE_LEAVES)); }
    }

    static void col(Plan p, int x, int z, int h) {
        p.set(x, 2, z, s(Blocks.CHISELED_QUARTZ_BLOCK)); p.pillar(x, z, 3, h, s(Blocks.QUARTZ_PILLAR)); p.set(x, h + 1, z, s(Blocks.CHISELED_QUARTZ_BLOCK));
    }

    // ---------------------------------------------------------------- siege yard (11x11)
    static void siegeYard(Plan p, int lv) {
        p.fill(0, -1, 0, 10, -1, 10, s(Blocks.COARSE_DIRT));
        // sharpened palisade with a gate
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
            boolean edge = x == 0 || z == 0 || x == 10 || z == 10;
            if (!edge || (z == 10 && Math.abs(x - 5) <= 1)) continue;
            int hh = 2 + p.rnd(x, 0, z, 2);
            p.pillar(x, z, 0, hh, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y)); p.set(x, hh + 1, z, s(Blocks.SPRUCE_FENCE));
        }
        for (int x : new int[]{4, 6}) { p.pillar(x, 10, 0, 4, SPRUCE_Y); p.set(x, 5, 10, s(Blocks.SKELETON_SKULL)); }
        p.fill(4, 4, 10, 6, 4, 10, log(Blocks.SPRUCE_LOG, Direction.Axis.X)); wallBanner(p, FRONT, 10, 5, 4);
        path(p, 4, 6, 6, 10);
        // the helepolis: a three-storey wheeled siege tower clad in hides, under construction
        int tx = 1, tz = 1;
        p.corners(tx, tz, tx + 3, tz + 3, 0, 9, SPRUCE_Y);
        for (int y = 1; y <= 8; y++) for (int i = 1; i <= 2; i++) {
            BlockState hide = y % 3 == 0 ? log(Blocks.SPRUCE_LOG, Direction.Axis.X) : s(Blocks.BROWN_WOOL);
            if (y <= 6) { p.set(tx + i, y, tz + 3, hide); p.set(tx + 3, y, tz + i, y % 3 == 0 ? log(Blocks.SPRUCE_LOG, Direction.Axis.Z) : s(Blocks.BROWN_WOOL)); }
        }
        p.set(tx + 1, 4, tz + 3, trapdoor(Blocks.SPRUCE_TRAPDOOR, S, true, false)).set(tx + 2, 4, tz + 3, trapdoor(Blocks.SPRUCE_TRAPDOOR, S, true, false));
        for (int x = tx; x <= tx + 3; x += 3) { p.set(x, 0, tz + 1, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X)); p.set(x, 0, tz + 2, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X)); }
        p.set(tx + 4, 0, tz + 1, s(Blocks.SCAFFOLDING)).set(tx + 4, 1, tz + 1, s(Blocks.SCAFFOLDING)).set(tx + 4, 2, tz + 1, s(Blocks.SCAFFOLDING));
        p.fill(tx, 10, tz, tx + 3, 10, tz + 3, slab(Blocks.SPRUCE_SLAB, false));
        // bolt-thrower workshop: a shed with a ballista on the bench
        frameWalls(p, 6, 0, 1, 9, 2, 4, PLANK, Blocks.SPRUCE_LOG, 3);
        p.clearBox(7, 0, 4, 8, 1, 4);
        roofX(p, 6, 9, 0, 5, 3, Blocks.SPRUCE_STAIRS, slab(Blocks.SPRUCE_SLAB, false), 6, 9, PLANK);
        p.set(7, 0, 2, s(Blocks.SMITHING_TABLE)).set(8, 0, 2, s(Blocks.ANVIL));
        int bx = 7, bz = 7;
        p.fill(bx - 1, 0, bz, bx + 1, 0, bz + 1, PLANK).set(bx, 1, bz, s(Blocks.SPRUCE_FENCE)).set(bx, 2, bz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        p.set(bx - 1, 2, bz, stair(Blocks.SPRUCE_STAIRS, E)).set(bx + 1, 2, bz, stair(Blocks.SPRUCE_STAIRS, W)).set(bx, 2, bz + 1, s(Blocks.LIGHTNING_ROD));
        // timber stacks and stone shot
        woodpile(p, 1, 3, 8, Blocks.SPRUCE_LOG); p.set(1, 0, 6, s(Blocks.COBBLESTONE)).set(2, 0, 6, s(Blocks.COBBLESTONE)).set(1, 1, 6, s(Blocks.COBBLESTONE));
        if (lv >= 2) { p.set(9, 0, 9, s(Blocks.TARGET)); p.set(9, 0, 6, s(Blocks.CAMPFIRE)); p.set(tx + 1, 9, tz + 1, s(Blocks.LANTERN)); }
    }

    // ---------------------------------------------------------------- heartforge (6x6): Hephaestus' forge
    static void heartforge(Plan p, int lv) {
        BlockState basalt = log(Blocks.BASALT, Direction.Axis.Y), black = s(Blocks.BLACKSTONE), polished = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
        // basalt mound with magma veins
        p.fill(0, -1, 0, 5, -1, 5, s(Blocks.MAGMA_BLOCK));
        for (int y = 0; y <= 2; y++) p.disc(2.5, 2.5, 2.8 - y * .45, y, y == 0 ? black : basalt, false);
        for (int[] v : new int[][]{{0, 0, 2}, {1, 0, 4}, {4, 0, 1}, {5, 0, 3}, {1, 1, 1}, {4, 1, 4}, {2, 2, 4}}) p.set(v[0], v[1], v[2], s(Blocks.MAGMA_BLOCK));
        // the forge mouth: a polished-blackstone arch over a lava cauldron, a great bronze anvil before it
        p.fill(1, 0, 4, 4, 3, 5, polished);
        p.clearBox(2, 0, 5, 3, 1, 5);
        p.set(2, 0, 5, s(Blocks.LAVA_CAULDRON)).set(3, 0, 5, s(Blocks.LAVA_CAULDRON));
        p.set(2, 2, 5, stairTop(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, E)).set(3, 2, 5, stairTop(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, W));
        p.set(2, 3, 5, s(Blocks.GILDED_BLACKSTONE)).set(3, 3, 5, s(Blocks.GILDED_BLACKSTONE));
        p.set(0, 0, 5, BRONZE_BLOCK).set(0, 1, 5, s(Blocks.ANVIL)).set(5, 0, 5, s(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB));
        // three smoking stacks from the crater, the centre one the tallest
        chimney(p, 2, 2, 3, 6 + lv, polished, s(Blocks.POLISHED_BLACKSTONE_WALL));
        chimney(p, 1, 1, 3, 4 + lv, polished, null);
        chimney(p, 4, 2, 3, 5 + lv, polished, null);
        // bronze pipes and the heart: a beating glow at the crater rim
        p.set(3, 3, 2, s(Blocks.SHROOMLIGHT)).set(3, 3, 3, s(Blocks.MAGMA_BLOCK)).set(2, 3, 3, s(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS));
        p.set(5, 1, 0, s(Blocks.CHAIN)).set(5, 0, 0, BRONZE);
        if (lv >= 2) { p.set(0, 2, 0, s(Blocks.SKELETON_SKULL)); p.set(5, 2, 5, s(Blocks.CAMPFIRE)); p.set(4, 3, 4, s(Blocks.GILDED_BLACKSTONE)); }
    }

    // ---------------------------------------------------------------- drum circle (4x4)
    static void drumCircle(Plan p, int lv) {
        p.fill(0, -1, 0, 3, -1, 3, s(Blocks.COARSE_DIRT));
        // trilithons: standing stones with lintels on two sides of a fire pit
        for (int[] q : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) p.pillar(q[0], q[1], 0, 2, s(p.rnd(q[0], 0, q[1], 2) == 0 ? Blocks.STONE : Blocks.ANDESITE));
        p.fill(0, 3, 0, 3, 3, 0, s(Blocks.STONE)).fill(0, 3, 3, 0, 3, 3, s(Blocks.ANDESITE)).set(3, 3, 3, s(Blocks.ANDESITE));
        p.set(1, 3, 3, slab(Blocks.STONE_SLAB, false));
        // the great drums (barrels skinned with hide) and the fire
        p.set(1, 0, 1, s(Blocks.CAMPFIRE)).set(2, 0, 2, s(Blocks.CAMPFIRE));
        p.set(1, 0, 2, s(Blocks.BARREL)).set(1, 1, 2, s(Blocks.WHITE_CARPET)).set(2, 0, 1, s(Blocks.BARREL)).set(2, 1, 1, s(Blocks.WHITE_CARPET));
        p.set(0, 0, 1, s(Blocks.NOTE_BLOCK)).set(3, 0, 2, s(Blocks.NOTE_BLOCK));
        // bone horns and banners on the stones
        p.set(0, 4, 0, s(Blocks.SKELETON_SKULL)).set(3, 4, 0, s(Blocks.BONE_BLOCK));
        wallBanner(p, FRONT, 0, 0, 2);
        if (lv >= 2) { p.set(3, 4, 3, s(Blocks.SKELETON_SKULL)); p.set(0, 4, 3, s(Blocks.CAMPFIRE)); wallBanner(p, FRONT, 0, 3, 2); }
    }
}
