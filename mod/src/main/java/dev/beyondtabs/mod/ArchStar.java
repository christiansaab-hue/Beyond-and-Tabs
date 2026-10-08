package dev.beyondtabs.mod;

import static dev.beyondtabs.mod.Architecture.*;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * STARFORGE DOMINION — human sci-fi industry. Every building follows real engineering, pushed a little further:
 * <ul>
 * <li>mine: drilling derrick — lattice tower, drill string down to a glowing bore</li>
 * <li>energy: photovoltaic array — tilted panel rows on frames, an inverter cabinet</li>
 * <li>storage: container port — stacked shipping containers under a gantry crane</li>
 * <li>converter: matter refinery — glass process tanks, pipe racks, a control desk</li>
 * <li>tech: uplink observatory — Arecibo-style dish with a suspended receiver</li>
 * <li>watchtower: pulse turret on a pylon, twin barrels and a sensor dome</li>
 * <li>wall: barrier pylon with an energy emitter</li>
 * <li>barracks: prefab drop barracks — modular hall, hangar door, floodlights, roof antenna</li>
 * <li>mech bay: open hangar with a walker mech in its gantry, sparks flying</li>
 * <li>titan foundry: assembly hall flanked by hyperbolic cooling towers venting steam</li>
 * <li>artillery foundry: vehicle yard with a heavy gun on a turntable</li>
 * <li>fusion core: ITER-style tokamak — a glass torus round a beacon solenoid, magnets and coolant</li>
 * <li>drone pad: landing pad with docking posts and a hovering drone</li>
 * </ul>
 * Palette: white and light-grey concrete panels, polished deepslate, iron, light-blue glass, sea-lantern lighting,
 * yellow-and-black hazard striping.
 */
final class ArchStar {
    private ArchStar() { }

    static final BlockState WHITE = s(Blocks.WHITE_CONCRETE), PANEL = s(Blocks.SMOOTH_QUARTZ), LIGHT = s(Blocks.LIGHT_GRAY_CONCRETE),
            GREY = s(Blocks.GRAY_CONCRETE), DARK = s(Blocks.POLISHED_DEEPSLATE), IRON = s(Blocks.IRON_BLOCK), BARS = s(Blocks.IRON_BARS),
            GLASS = s(Blocks.LIGHT_BLUE_STAINED_GLASS), PANE = s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), LAMP = s(Blocks.SEA_LANTERN),
            YELLOW = s(Blocks.YELLOW_CONCRETE), BLACK = s(Blocks.BLACK_CONCRETE);

    static BlockState rod(Block b, Direction d) { return b.defaultBlockState().setValue(BlockStateProperties.FACING, d); }

    static void build(Plan p, String kind, int lv) {
        switch (kind) {
            case "metal_extractor" -> derrick(p, lv);
            case "energy_gen" -> solar(p, lv);
            case "storage" -> depot(p, lv);
            case "converter" -> refinery(p, lv);
            case "tech_center" -> observatory(p, lv);
            case "watchtower" -> turret(p, lv);
            case "wall" -> pylon(p, lv);
            case "barracks" -> dropBarracks(p, lv);
            case "mech_bay" -> mechBay(p, lv);
            case "titan_foundry" -> titanFoundry(p, lv);
            case "artillery_foundry" -> artilleryFoundry(p, lv);
            case "fusion_core" -> tokamak(p, lv);
            case "drone_pad" -> dronePad(p, lv);
            default -> p.fill(0, 0, 0, p.w - 1, 0, p.d - 1, LIGHT);
        }
    }

    /** Yellow/black hazard stripes on the ground along a row. */
    static void hazard(Plan p, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) p.set(x, -1, z, (x + z) % 2 == 0 ? YELLOW : BLACK);
    }

    // ---------------------------------------------------------------- derrick (3x3)
    static void derrick(Plan p, int lv) {
        p.fill(0, -1, 0, 2, -1, 2, DARK);
        p.set(1, -1, 1, s(Blocks.MAGMA_BLOCK));
        p.corners(0, 0, 2, 2, 0, 0, IRON);
        p.set(1, 0, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false)).set(0, 0, 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false)).set(2, 0, 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
        // lattice legs and cross-bracing (iron bars join into a truss)
        int h = 3 + lv;
        p.corners(0, 0, 2, 2, 1, h, BARS);
        for (int y = 2; y <= h; y += 2) { p.walls(0, y, 0, 2, y, 2, BARS); }
        // crown block, drill string and the bit at the bore
        p.fill(0, h + 1, 0, 2, h + 1, 2, slab(Blocks.SMOOTH_STONE_SLAB, false));
        p.set(1, h + 1, 1, IRON);
        for (int y = 1; y <= h; y++) p.set(1, y, 1, s(Blocks.CHAIN));
        p.set(1, 0, 1, rod(Blocks.LIGHTNING_ROD, Direction.DOWN));
        p.set(1, h + 2, 1, rod(Blocks.LIGHTNING_ROD, Direction.UP)).set(0, h + 2, 0, rod(Blocks.END_ROD, Direction.UP));
        // ore skip and a pipe out the front
        p.set(1, 0, 2, s(Blocks.HOPPER)).set(2, 0, 2, s(lv >= 3 ? Blocks.RAW_IRON_BLOCK : Blocks.IRON_ORE));
        if (lv >= 2) { p.set(2, h + 2, 2, LAMP); p.set(0, 1, 2, s(Blocks.OBSERVER)); }
    }

    // ---------------------------------------------------------------- solar array (4x4)
    static void solar(Plan p, int lv) {
        p.fill(0, -1, 0, 3, -1, 3, s(Blocks.GRAVEL));
        // two rows of tilted panels: the back row a step higher, on light frames
        for (int x = 0; x <= 3; x++) {
            p.set(x, 0, 3, s(Blocks.IRON_BARS)); p.set(x, 1, 3, s(Blocks.DAYLIGHT_DETECTOR)); p.set(x, 1, 2, s(Blocks.DAYLIGHT_DETECTOR));
            p.set(x, 0, 2, x % 3 == 0 ? BARS : AIR);
            p.set(x, 1, 1, x % 3 == 0 ? BARS : AIR); p.set(x, 2, 1, s(Blocks.DAYLIGHT_DETECTOR)); p.set(x, 2, 0, s(Blocks.DAYLIGHT_DETECTOR));
            p.set(x, 0, 1, x % 3 == 0 ? IRON : AIR); p.set(x, 0, 0, x % 3 == 0 ? IRON : AIR); p.set(x, 1, 0, x % 3 == 0 ? BARS : AIR);
        }
        for (int x = 0; x <= 3; x++) for (int z = 0; z <= 3; z++) if (p.get(x, 0, z) == AIR) p.clear(x, 0, z);
        for (int x = 0; x <= 3; x++) for (int z = 0; z <= 1; z++) if (p.get(x, 1, z) == AIR) p.clear(x, 1, z);
        // inverter cabinet under the high row, status light
        p.set(1, 0, 0, LIGHT).set(2, 0, 0, s(Blocks.CYAN_TERRACOTTA)).set(1, 0, 1, s(Blocks.CHAIN)).set(2, 1, 0, s(Blocks.DAYLIGHT_DETECTOR));
        if (lv >= 2) { p.set(0, 3, 0, rod(Blocks.END_ROD, Direction.UP)); p.set(3, 3, 0, rod(Blocks.LIGHTNING_ROD, Direction.UP)); }
        if (lv >= 3) { p.set(1, 0, 2, LAMP); }
    }

    // ---------------------------------------------------------------- container depot (5x5)
    static void depot(Plan p, int lv) {
        p.fill(0, -1, 0, 4, -1, 4, s(Blocks.SMOOTH_STONE));
        hazard(p, 0, 4, 4, 4);
        // stacked shipping containers (ribbed sides, door ends of iron trapdoors)
        Block[][] rows = {{Blocks.ORANGE_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA}, {Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA}};
        for (int z = 0; z <= 1; z++) for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 2; x++) p.set(x, y, z, s(rows[z][y]));
            p.set(3, y, z, trapdoor(Blocks.IRON_TRAPDOOR, E, true, false));
        }
        for (int x = 0; x <= 2; x++) p.set(x, 0, 3, s(Blocks.GREEN_TERRACOTTA));
        p.set(3, 0, 3, trapdoor(Blocks.IRON_TRAPDOOR, E, true, false));
        if (lv >= 2) { for (int x = 0; x <= 2; x++) p.set(x, 2, 0, s(Blocks.LIGHT_GRAY_TERRACOTTA)); p.set(3, 2, 0, trapdoor(Blocks.IRON_TRAPDOOR, E, true, false)); }
        // gantry crane straddling the stacks
        p.pillar(4, 0, 0, 4, BARS).pillar(4, 4, 0, 4, BARS).pillar(0, 4, 0, 4, BARS);
        p.set(4, 0, 0, IRON).set(4, 0, 4, IRON).set(0, 0, 4, IRON);
        p.fill(4, 5, 0, 4, 5, 4, slab(Blocks.SMOOTH_STONE_SLAB, false)).fill(0, 5, 4, 4, 5, 4, slab(Blocks.SMOOTH_STONE_SLAB, false));
        p.set(4, 4, 2, s(Blocks.CHAIN)).set(4, 3, 2, s(Blocks.CHAIN)).set(4, 2, 2, s(Blocks.OBSERVER));
        p.set(4, 6, 4, s(Blocks.SEA_LANTERN)).set(0, 6, 4, rod(Blocks.END_ROD, Direction.UP));
        p.set(2, 0, 4, s(Blocks.LIGHT_GRAY_SHULKER_BOX)).set(1, 0, 4, s(Blocks.CYAN_SHULKER_BOX));
    }

    // ---------------------------------------------------------------- refinery (4x4)
    static void refinery(Plan p, int lv) {
        p.fill(0, -1, 0, 3, -1, 3, DARK);
        // two glass process tanks with iron caps, glowing cores
        for (int[] t : new int[][]{{0, 0}, {2, 1}}) {
            int tx = t[0], tz = t[1], h = t[0] == 0 ? 4 : 3;
            p.fill(tx, 0, tz, tx + 1, 0, tz + 1, IRON);
            p.fill(tx, 1, tz, tx + 1, h - 1, tz + 1, s(tx == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.PURPLE_STAINED_GLASS));
            p.fill(tx, h, tz, tx + 1, h, tz + 1, slab(Blocks.SMOOTH_STONE_SLAB, false));
        }
        p.set(0, 1, 0, LAMP).set(3, 1, 2, s(Blocks.AMETHYST_BLOCK));
        // pipe rack between the tanks and down to the yard
        p.fill(2, 3, 0, 3, 3, 0, BARS).set(1, 2, 2, BARS).set(1, 3, 2, BARS);
        p.set(0, 0, 3, LIGHT).set(0, 1, 3, s(Blocks.OBSERVER)).set(1, 0, 3, s(Blocks.CHAIN));
        p.set(3, 0, 0, s(Blocks.HOPPER)).set(3, 0, 3, s(Blocks.CAULDRON));
        if (lv >= 2) { p.set(1, 5, 1, rod(Blocks.LIGHTNING_ROD, Direction.UP)); p.set(3, 4, 2, rod(Blocks.END_ROD, Direction.UP)); }
    }

    // ---------------------------------------------------------------- uplink observatory (7x7)
    static void observatory(Plan p, int lv) {
        p.fill(0, -1, 0, 6, -1, 6, LIGHT);
        // control building under the dish
        p.walls(1, 0, 1, 5, 1, 5, WHITE);
        for (int x = 2; x <= 4; x++) { p.set(x, 1, 5, PANE); p.set(x, 1, 1, PANE); }
        for (int z = 2; z <= 4; z++) { p.set(1, 1, z, PANE); p.set(5, 1, z, PANE); }
        doubleDoor(p, FRONT, 5, 2, 0, Blocks.IRON_DOOR); p.set(4, 0, 5, WHITE);
        p.set(3, 0, 3, s(Blocks.ENCHANTING_TABLE)).set(2, 0, 2, s(Blocks.LECTERN)).set(4, 0, 2, s(Blocks.OBSERVER));
        // the dish: a quartz bowl, rim higher than the centre
        p.disc(3, 3, 1.4, 2, s(Blocks.SMOOTH_QUARTZ_SLAB), false);
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
            double r = Math.hypot(x - 3, z - 3);
            if (r > 1.6 && r <= 3.4) p.set(x, 2, z, s(Blocks.SMOOTH_QUARTZ));
            if (r > 2.5 && r <= 3.4) p.set(x, 3, z, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Math.abs(x - 3) > Math.abs(z - 3) ? (x > 3 ? E : W) : (z > 3 ? S : N)));
        }
        // four masts and cross beams carrying the receiver over the bowl
        int top = 6 + lv;
        for (int[] m : new int[][]{{3, 0}, {3, 6}, {0, 3}, {6, 3}}) { p.pillar(m[0], m[1], 3, top, IRON); p.set(m[0], top + 1, m[1], rod(Blocks.END_ROD, Direction.UP)); }
        for (int k = 0; k <= 6; k++) { p.set(3, top, k, BARS); p.set(k, top, 3, BARS); }
        p.set(3, top, 3, IRON).set(3, top - 1, 3, s(Blocks.OBSERVER)).set(3, top - 2, 3, rod(Blocks.END_ROD, Direction.DOWN));
        p.set(3, top + 1, 3, rod(Blocks.LIGHTNING_ROD, Direction.UP));
        if (lv >= 2) for (int[] m : new int[][]{{0, 0}, {6, 6}}) { p.set(m[0], 0, m[1], LIGHT); p.set(m[0], 1, m[1], LAMP); }
        if (lv >= 3) { p.set(3, top - 3, 3, LAMP); }
    }

    // ---------------------------------------------------------------- pulse turret (3x3)
    static void turret(Plan p, int lv) {
        p.fill(0, 0, 0, 2, 0, 2, DARK);
        p.corners(0, 0, 2, 2, 1, 1, s(Blocks.POLISHED_DEEPSLATE_WALL));
        int py = 1 + lv;
        p.pillar(1, 1, 1, py, IRON);
        // turret head: armoured housing, sensor eye, twin barrels forward
        p.fill(0, py + 1, 0, 2, py + 1, 1, GREY);
        p.set(1, py + 1, 0, LAMP).set(1, py + 2, 0, s(Blocks.DAYLIGHT_DETECTOR));
        p.set(0, py + 1, 2, rod(Blocks.LIGHTNING_ROD, Direction.SOUTH)).set(2, py + 1, 2, rod(Blocks.LIGHTNING_ROD, Direction.SOUTH));
        p.set(1, py + 1, 2, s(Blocks.OBSERVER)).set(1, py + 2, 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
        if (lv >= 2) { p.set(0, 1, 0, rod(Blocks.END_ROD, Direction.UP)); p.set(2, 1, 2, rod(Blocks.END_ROD, Direction.UP)); }
        if (lv >= 3) wallBanner(p, FRONT, 2, 1, py);
    }

    static void pylon(Plan p, int lv) {
        p.set(0, 0, 0, DARK);
        for (int y = 1; y <= lv; y++) p.set(0, y, 0, y == lv ? GLASS : IRON);
        p.set(0, lv + 1, 0, rod(Blocks.END_ROD, Direction.UP));
    }

    // ---------------------------------------------------------------- drop barracks (7x7)
    static void dropBarracks(Plan p, int lv) {
        p.fill(0, -1, 0, 6, -1, 6, LIGHT);
        hazard(p, 2, 6, 4, 6);
        // modular hall: white panels on a dark plinth, glazed band, flat roof with a parapet
        p.walls(1, 0, 1, 5, 0, 4, DARK);
        p.walls(1, 1, 1, 5, 3, 4, WHITE);
        p.corners(1, 1, 5, 4, 1, 3, LIGHT);
        for (int x = 2; x <= 4; x++) { p.set(x, 2, 1, PANE); }
        for (int z = 2; z <= 3; z++) { p.set(1, 2, z, PANE); p.set(5, 2, z, PANE); }
        p.fill(1, 4, 1, 5, 4, 4, GREY);
        p.walls(1, 5, 1, 5, 5, 4, slab(Blocks.SMOOTH_STONE_SLAB, false));
        // hangar door: three iron trapdoor leaves, a lit lintel
        for (int x = 2; x <= 4; x++) { p.set(x, 0, 4, trapdoor(Blocks.IRON_TRAPDOOR, S, true, false)); p.set(x, 1, 4, trapdoor(Blocks.IRON_TRAPDOOR, S, true, false)); }
        p.fill(2, 2, 4, 4, 2, 4, LAMP); p.set(3, 3, 4, BLACK);
        // roof plant: antenna mast with a dish, vents, AC units
        p.pillar(4, 2, 5, 7 + lv, BARS).set(4, 8 + lv, 2, rod(Blocks.LIGHTNING_ROD, Direction.UP)).set(4, 7, 3, s(Blocks.DAYLIGHT_DETECTOR));
        p.set(2, 5, 2, s(Blocks.OBSERVER)).set(2, 5, 3, trapdoor(Blocks.IRON_TRAPDOOR, N, false, false));
        // supply crates, floodlights, a team banner by the door
        p.set(0, 0, 6, s(Blocks.LIGHT_GRAY_SHULKER_BOX)).set(0, 0, 5, s(Blocks.WHITE_SHULKER_BOX)).set(0, 1, 6, s(Blocks.CYAN_SHULKER_BOX));
        p.set(6, 0, 6, BARS).set(6, 1, 6, BARS).set(6, 2, 6, LAMP);
        p.set(6, 0, 0, BARS).set(6, 1, 0, BARS).set(6, 2, 0, LAMP);
        wallBanner(p, FRONT, 4, 1, 3); wallBanner(p, FRONT, 4, 5, 3);
        if (lv >= 2) { p.fill(0, 0, 1, 0, 0, 3, s(Blocks.LIGHT_GRAY_SHULKER_BOX)); }
        if (lv >= 3) { p.set(2, 6, 2, LAMP); }
    }

    // ---------------------------------------------------------------- mech bay (9x9)
    static void mechBay(Plan p, int lv) {
        p.fill(0, -1, 0, 8, -1, 8, LIGHT);
        hazard(p, 1, 8, 7, 8);
        // hangar: side walls with ribs, open front, a roof of panels with skylights
        int h = 6;
        for (int z = 0; z <= 7; z++) for (int y = 0; y <= h; y++) { p.set(0, y, z, z % 2 == 0 ? LIGHT : WHITE); p.set(8, y, z, z % 2 == 0 ? LIGHT : WHITE); }
        for (int x = 1; x <= 7; x++) for (int y = 0; y <= h; y++) p.set(x, y, 0, WHITE);
        for (int z = 1; z <= 6; z += 2) { p.set(0, 3, z, PANE); p.set(8, 3, z, PANE); p.set(0, 4, z, PANE); p.set(8, 4, z, PANE); }
        // roof over the back half only; the front half is an open gantry so the mech stands in daylight
        p.fill(0, h + 1, 0, 8, h + 1, 3, GREY);
        for (int x = 2; x <= 6; x += 2) p.fill(x, h + 1, 1, x, h + 1, 2, GLASS);
        for (int z = 5; z <= 7; z += 2) p.fill(0, h + 1, z, 8, h + 1, z, IRON);
        for (int x = 0; x <= 8; x++) p.set(x, h, 7, x % 2 == 0 ? LAMP : GREY);
        // gantry and catwalk inside
        p.fill(1, 4, 1, 7, 4, 1, s(Blocks.SMOOTH_STONE_SLAB)); for (int x = 1; x <= 7; x++) p.set(x, 5, 2, BARS);
        p.fill(1, h, 3, 7, h, 3, IRON); p.set(4, h - 1, 3, s(Blocks.CHAIN));
        // the walker mech in its cradle: legs, hips, torso with a glowing cockpit, gun arms
        int mx = 4, mz = 4;
        for (int s2 = -1; s2 <= 1; s2 += 2) { p.pillar(mx + s2, mz, 0, 1, DARK); p.set(mx + s2, 2, mz, IRON); }
        p.fill(mx - 1, 3, mz, mx + 1, 3, mz, IRON);
        p.fill(mx - 1, 4, mz - 1, mx + 1, 5, mz, WHITE); p.set(mx, 4, mz + 1, GLASS).set(mx, 5, mz + 1, LAMP);
        p.set(mx - 2, 4, mz, GREY).set(mx + 2, 4, mz, GREY).set(mx - 2, 4, mz + 1, rod(Blocks.LIGHTNING_ROD, Direction.SOUTH)).set(mx + 2, 4, mz + 1, rod(Blocks.LIGHTNING_ROD, Direction.SOUTH));
        p.set(mx - 2, 3, mz, rod(Blocks.END_ROD, Direction.DOWN)).set(mx + 2, 2, mz - 1, rod(Blocks.END_ROD, Direction.UP));
        // parts, tool stations
        p.set(1, 0, 6, s(Blocks.SMITHING_TABLE)).set(7, 0, 6, s(Blocks.ANVIL)).set(1, 0, 1, s(Blocks.LIGHT_GRAY_SHULKER_BOX)).set(7, 0, 1, s(Blocks.WHITE_SHULKER_BOX));
        wallBanner(p, FRONT, 7, 0, h - 1); wallBanner(p, FRONT, 7, 8, h - 1);
        p.pillar(8, 8, 0, 2, BARS).set(8, 3, 8, LAMP).pillar(0, 8, 0, 2, BARS).set(0, 3, 8, LAMP);
        if (lv >= 2) { p.pillar(7, 0, h + 2, h + 4, BARS); p.set(7, h + 5, 0, rod(Blocks.LIGHTNING_ROD, Direction.UP)); }
        if (lv >= 3) p.set(mx, 6, mz, s(Blocks.DAYLIGHT_DETECTOR));
    }

    // ---------------------------------------------------------------- titan foundry (11x11)
    static void titanFoundry(Plan p, int lv) {
        p.fill(0, -1, 0, 10, -1, 10, LIGHT);
        hazard(p, 3, 10, 7, 10);
        // hyperbolic cooling towers at the back corners: wide, waisted, flaring, steam out the top
        int ct = 9 + lv;
        for (double[] c : new double[][]{{1.5, 1.5}, {8.5, 1.5}}) {
            for (int y = 0; y <= ct; y++) {
                double t = (double) y / ct, r = 1.75 - .55 * Math.sin(Math.PI * Math.min(1, t * 1.15));
                p.disc(c[0], c[1], r, y, y % 4 == 0 ? LIGHT : WHITE, true);
            }
            p.set((int) c[0], ct, (int) c[1], s(Blocks.CAMPFIRE)).set((int) c[0] + 1, ct, (int) c[1] + 1, s(Blocks.CAMPFIRE));
        }
        // assembly hall: tall, ribbed, with a lit clerestory and blast doors
        int h = 7;
        p.walls(1, 0, 4, 9, h, 9, WHITE);
        for (int x = 1; x <= 9; x += 2) p.pillar(x, 9, 0, h, LIGHT);
        for (int x = 2; x <= 8; x += 2) p.set(x, h - 1, 9, LAMP);
        p.fill(1, h + 1, 4, 9, h + 1, 9, GREY);
        for (int x = 3; x <= 7; x++) for (int y = 0; y <= 4; y++) p.set(x, y, 9, y <= 3 ? trapdoor(Blocks.IRON_TRAPDOOR, S, true, false) : BLACK);
        for (int x = 3; x <= 7; x++) p.set(x, 5, 9, (x % 2 == 0) ? YELLOW : BLACK);
        // the titan's head and shoulders rising through the roof
        p.fill(3, h + 2, 6, 7, h + 3, 7, WHITE);
        p.fill(4, h + 4, 6, 6, h + 5, 7, LIGHT); p.set(5, h + 5, 8, GLASS).set(5, h + 4, 8, LAMP);
        p.set(3, h + 4, 6, rod(Blocks.LIGHTNING_ROD, Direction.UP)).set(7, h + 4, 6, rod(Blocks.LIGHTNING_ROD, Direction.UP));
        // gantry crane on rails over the roof
        p.pillar(1, 4, h + 2, h + 4, IRON).pillar(9, 4, h + 2, h + 4, IRON).fill(1, h + 5, 4, 9, h + 5, 4, IRON);
        p.set(5, h + 4, 4, s(Blocks.CHAIN));
        // pipes from the towers to the hall
        p.fill(3, 3, 1, 7, 3, 1, BARS); p.fill(5, 3, 2, 5, 3, 3, BARS);
        wallBanner(p, FRONT, 9, 2, h - 1); wallBanner(p, FRONT, 9, 8, h - 1);
        p.set(0, 0, 10, s(Blocks.LIGHT_GRAY_SHULKER_BOX)).set(10, 0, 10, s(Blocks.WHITE_SHULKER_BOX));
    }

    // ---------------------------------------------------------------- artillery foundry (11x11)
    static void artilleryFoundry(Plan p, int lv) {
        p.fill(0, -1, 0, 10, -1, 10, s(Blocks.GRAVEL));
        hazard(p, 0, 10, 10, 10);
        // vehicle hangar along the back
        p.walls(0, 0, 0, 10, 4, 4, WHITE);
        for (int x = 0; x <= 10; x += 2) p.pillar(x, 4, 0, 4, LIGHT);
        for (int x = 2; x <= 8; x++) for (int y = 0; y <= 2; y++) p.set(x, y, 4, trapdoor(Blocks.IRON_TRAPDOOR, S, true, false));
        p.fill(0, 5, 0, 10, 5, 4, GREY);
        p.set(2, 6, 2, s(Blocks.OBSERVER)).set(8, 6, 2, s(Blocks.OBSERVER));
        // turntable with a heavy gun: dark disc, mount, a long barrel stepping up toward the front
        p.disc(5, 7, 2.3, 0, DARK, false);
        p.disc(5, 7, 1.2, 1, GREY, false);
        p.set(5, 2, 7, IRON).set(4, 2, 7, IRON).set(6, 2, 7, IRON);
        for (int k = 0; k < 4; k++) p.set(5, 2 + k / 2, 8 + k, s(k < 2 ? Blocks.IRON_BLOCK : Blocks.POLISHED_DEEPSLATE));
        p.set(5, 4, 10, rod(Blocks.LIGHTNING_ROD, Direction.SOUTH));
        // ammunition and a radar mast
        p.set(1, 0, 7, s(Blocks.RED_SHULKER_BOX)).set(1, 0, 8, s(Blocks.YELLOW_SHULKER_BOX)).set(1, 1, 7, s(Blocks.RED_SHULKER_BOX));
        p.set(9, 0, 7, s(Blocks.RED_SHULKER_BOX)).set(9, 0, 8, s(Blocks.LIGHT_GRAY_SHULKER_BOX));
        p.pillar(10, 6, 0, 6, BARS).set(10, 7, 6, s(Blocks.DAYLIGHT_DETECTOR)).set(10, 8, 6, rod(Blocks.LIGHTNING_ROD, Direction.UP));
        wallBanner(p, FRONT, 4, 1, 3); wallBanner(p, FRONT, 4, 9, 3);
        if (lv >= 2) { p.set(0, 6, 4, LAMP); p.set(10, 6, 4, LAMP); }
    }

    // ---------------------------------------------------------------- fusion core (6x6): tokamak
    static void tokamak(Plan p, int lv) {
        p.fill(0, -1, 0, 5, -1, 5, DARK);
        hazard(p, 0, 5, 5, 5);
        // the solenoid: a beacon on its iron base, its beam rising through the torus (tinted by the glass)
        p.fill(1, 0, 1, 3, 0, 3, IRON);
        p.set(2, 1, 2, s(Blocks.BEACON));
        // the torus: a glass ring with plasma glow, toroidal field magnets round it
        for (int y = 1; y <= 2; y++) for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) {
            double r = Math.hypot(x - 2, z - 2);
            if (r > 1.4 && r < 2.6) p.set(x, y, z, (x + z + y) % 3 == 0 ? LAMP : GLASS);
        }
        for (int[] m : new int[][]{{2, 0}, {2, 4}, {0, 2}, {4, 2}}) { p.set(m[0], 1, m[1], IRON); p.set(m[0], 2, m[1], IRON); p.set(m[0], 3, m[1], s(Blocks.POLISHED_DEEPSLATE_SLAB)); }
        p.set(2, 2, 2, GLASS).set(2, 3, 2, GLASS);
        // coolant loop and heat exchanger on the open side, cryostat vents
        p.fill(5, 0, 0, 5, 2, 3, LIGHT);
        for (int z = 0; z <= 3; z++) p.set(5, 3, z, trapdoor(Blocks.IRON_TRAPDOOR, E, false, true));
        p.set(5, 1, 1, LAMP).fill(4, 3, 1, 4, 3, 3, BARS);
        p.set(0, 0, 5, s(Blocks.OBSERVER)).set(1, 0, 5, s(Blocks.CYAN_SHULKER_BOX));
        p.set(5, 4, 0, rod(Blocks.LIGHTNING_ROD, Direction.UP)).set(0, 3, 0, rod(Blocks.END_ROD, Direction.UP)).set(0, 2, 0, IRON).set(0, 1, 0, IRON).set(0, 0, 0, IRON);
        if (lv >= 2) { p.set(4, 3, 4, rod(Blocks.END_ROD, Direction.UP)); p.set(0, 3, 4, rod(Blocks.END_ROD, Direction.UP)); p.set(4, 0, 4, LAMP); }
    }

    // ---------------------------------------------------------------- drone pad (4x4)
    static void dronePad(Plan p, int lv) {
        p.fill(0, -1, 0, 3, -1, 3, DARK);
        p.fill(0, 0, 0, 3, 0, 3, LIGHT);
        p.fill(1, 0, 1, 2, 0, 2, YELLOW);
        // docking posts with guidance lights
        for (int[] c : new int[][]{{0, 0}, {3, 3}}) { p.pillar(c[0], c[1], 1, 2, BARS); p.set(c[0], 3, c[1], rod(Blocks.END_ROD, Direction.UP)); }
        p.set(3, 0, 0, LIGHT).set(3, 1, 0, s(Blocks.OBSERVER)).set(0, 0, 3, s(Blocks.LIGHT_GRAY_SHULKER_BOX));
        // a drone hovering over the pad: body, four rotor arms
        int dy = 4;
        p.set(1, dy, 1, s(Blocks.OBSERVER)).set(2, dy, 2, LAMP);
        p.set(1, dy, 2, trapdoor(Blocks.IRON_TRAPDOOR, S, false, false)).set(2, dy, 1, trapdoor(Blocks.IRON_TRAPDOOR, N, false, false));
        if (lv >= 2) { p.set(2, 5, 1, s(Blocks.OBSERVER)); p.set(1, 5, 2, rod(Blocks.END_ROD, Direction.DOWN)); wallBanner(p, FRONT, 3, 3, 1); }
    }
}
