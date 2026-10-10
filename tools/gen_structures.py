#!/usr/bin/env python3
"""Generates the custom building structures (metal extractor T1/T2, wind generator) as vanilla
structure NBT files. Run from the repo root; writes into bar/.../structures/.

Design notes:
- Footprints are fixed (extractors 5x5, wind 3x3) because placement + metal-patch validation
  depend on them.
- metal_extractor_t2 must contain copper_block or cut_copper: MetalExtractor.getUpgradeLevel()
  reads the placed blocks as the save-proof tier marker.
- Omitted positions are left untouched by the game (sparse structures), so shapes can be open.
"""
import gzip, struct, io, os

# Structures must exist in BOTH trees: the client reads buildings through its resource manager (assets/) and the
# integrated/dedicated server through its data-pack manager (data/). Missing from data/ = the server can't place it.
OUT = "bar/src/main/resources/assets/reignofnether/structures"
OUT_DATA = "bar/src/main/resources/data/reignofnether/structures"
DATA_VERSION = 3465


# ---- minimal NBT writer ----------------------------------------------------
def tag(t, name, payload):
    return bytes([t]) + struct.pack(">H", len(name)) + name.encode() + payload

def t_string(s):
    return struct.pack(">H", len(s)) + s.encode()

def t_compound(pairs):     # pairs: list of (type, name, payload)
    return b"".join(tag(t, n, p) for t, n, p in pairs) + b"\x00"

def t_list(elem_type, payloads):
    return bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)

def t_int(v):
    return struct.pack(">i", v)


class Structure:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.palette = []       # list of (name, props-dict-frozenset)
        self.pal_index = {}
        self.blocks = {}        # (x,y,z) -> palette index

    def pal(self, name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in self.pal_index:
            self.pal_index[key] = len(self.palette)
            self.palette.append(key)
        return self.pal_index[key]

    def set(self, x, y, z, name, **props):
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (x, y, z)
        self.blocks[(x, y, z)] = self.pal(name, **props)

    def fill(self, x1, y1, z1, x2, y2, z2, name, **props):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, name, **props)

    def write(self, path):
        pal_payloads = []
        for name, props in self.palette:
            pairs = [(8, "Name", t_string(name if ":" in name else "minecraft:" + name))]
            if props:
                prop_pairs = [(8, k, t_string(str(v))) for k, v in props]
                pairs.insert(0, (10, "Properties", t_compound(prop_pairs)))
            pal_payloads.append(t_compound(pairs))
        block_payloads = []
        for (x, y, z), idx in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            block_payloads.append(t_compound([
                (3, "state", t_int(idx)),
                (9, "pos", t_list(3, [t_int(x), t_int(y), t_int(z)])),
            ]))
        root = t_compound([
            (9, "size", t_list(3, [t_int(v) for v in self.size])),
            (9, "entities", t_list(0, [])),
            (9, "blocks", t_list(10, block_payloads)),
            (9, "palette", t_list(10, pal_payloads)),
            (3, "DataVersion", t_int(DATA_VERSION)),
        ])
        raw = tag(10, "", root)
        with gzip.open(path, "wb") as f:
            f.write(raw)
        print(f"{path}: {self.size} {len(self.blocks)} blocks, {len(self.palette)} palette entries")


# ---- faction palettes -------------------------------------------------------
# Variants keep their file suffixes (the building classes pick them), but the looks follow the factions in
# claude/design-factions.md:
#   ""        Sunforged Kingdom (villagers): white quartz and diorite, gold-capped, spruce sails
#   "_dark"   Gravebound (monsters): deepslate, dark oak, chains, soul fire
#   "_nether" Ironhide Horde (piglins): mud brick, rust-red terracotta, bone and dark timber
#   "_verdant" Verdant Court: moss and mossy stone, living oak, verdant froglights, lanterns, iron/oxidised-copper silver
# accent/lamp are Chipped blocks. They are decoration only (a frieze, hanging lamps): if Chipped is absent they
# load as air and the building still stands and works. Structural and foundation blocks stay vanilla.
PALETTES = {
    "": dict(
        pad="polished_diorite", pad_corner="gravel", ring="quartz_bricks",
        stair="quartz_stairs", wall="diorite_wall", slab="smooth_quartz_slab",
        metal="iron_block", body="white_terracotta", cap="spruce_planks",
        cap_stair="spruce_stairs", cap_slab="spruce_slab", hub="stripped_spruce_log",
        furnace="blast_furnace", bars="iron_bars",
        accent="chipped:decorated_white_terracotta", lamp="chipped:wrought_iron_lantern",
        core="lava_cauldron", pillar="quartz_pillar", band="gold_block",
    ),
    "_dark": dict(
        pad="polished_deepslate", pad_corner="soul_soil", ring="deepslate_bricks",
        stair="deepslate_brick_stairs", wall="deepslate_brick_wall", slab="deepslate_brick_slab",
        metal="iron_block", body="deepslate_tiles", cap="dark_oak_planks",
        cap_stair="dark_oak_stairs", cap_slab="dark_oak_slab", hub="stripped_dark_oak_log",
        furnace="blast_furnace", bars="chain",
        accent="chipped:checkered_deepslate_tiles", lamp="chipped:iron_bowl_soul_lantern",
        core="soul_campfire", pillar="polished_basalt", band="crying_obsidian",
    ),
    "_nether": dict(
        pad="packed_mud", pad_corner="coarse_dirt", ring="mud_bricks",
        stair="mud_brick_stairs", wall="mud_brick_wall", slab="mud_brick_slab",
        metal="iron_block", body="red_terracotta", cap="dark_oak_planks",
        cap_stair="dark_oak_stairs", cap_slab="dark_oak_slab", hub="bone_block",
        furnace="blast_furnace", bars="chain",
        accent="chipped:decorated_red_terracotta", lamp="chipped:burning_coal_lantern",
        core="magma_block", pillar="stripped_dark_oak_log", band="copper_block",
    ),
    # accent/lamp are vanilla here: froglights glow green from the RTS camera, lanterns are the Court's silver light.
    # The hub is a stripped oak log (WindmillRenderClientEvents gives it leaf-green sails on birch arms).
    "_verdant": dict(
        pad="moss_block", pad_corner="rooted_dirt", ring="mossy_stone_bricks",
        stair="mossy_stone_brick_stairs", wall="mossy_stone_brick_wall", slab="mossy_stone_brick_slab",
        metal="iron_block", body="stripped_birch_wood", cap="oak_planks",
        cap_stair="oak_stairs", cap_slab="oak_slab", hub="stripped_oak_log",
        furnace="smoker", bars="iron_bars",
        accent="verdant_froglight", lamp="lantern",
        core="campfire", pillar="stripped_oak_log", band="oxidized_copper",
    ),
}


# ---- metal extractor T1: open drill derrick over the patch -----------------
def metal_extractor(P):
    s = Structure(5, 5, 5)
    # worked pad: gravel corners, stone brick cross
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 0, z, P["pad_corner"])
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, P["ring"])
    # stair skirt pointing outward at edge midpoints (facing = direction the stair climbs toward)
    s.set(2, 0, 0, P["stair"], facing="south", half="bottom")
    s.set(2, 0, 4, P["stair"], facing="north", half="bottom")
    s.set(0, 0, 2, P["stair"], facing="east", half="bottom")
    s.set(4, 0, 2, P["stair"], facing="west", half="bottom")
    s.set(2, 0, 2, P["pad"])
    # four derrick legs leaning in: walls at corners, then top frame
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 1, z, P["wall"])
        s.set(x, 2, z, P["wall"])
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        s.set(x, 3, z, P["slab"], type="bottom")
    # crossbeams at the top of the legs
    s.set(2, 3, 1, P["slab"], type="bottom")
    s.set(2, 3, 3, P["slab"], type="bottom")
    s.set(1, 3, 2, P["slab"], type="bottom")
    s.set(3, 3, 2, P["slab"], type="bottom")
    # the drill itself: iron column + rod bit, hanging from the frame center
    s.set(2, 1, 2, P["metal"])
    s.set(2, 2, 2, P["metal"])
    s.set(2, 3, 2, P["metal"])
    s.set(2, 4, 2, "lightning_rod", facing="up")
    # a furnace shed corner so it reads "industry" (front faces outward, -z)
    s.set(1, 1, 0, P["furnace"], facing="north")
    s.set(3, 1, 0, P["ring"])
    # faction lamp standing on the shed post. Standing, not hanging: the build order only reaches this spot
    # once the full block under it exists, so it is always supported (a hanging lamp could pop off mid-build)
    s.set(3, 2, 0, P["lamp"], hanging="false", waterlogged="false")
    return s


# ---- metal extractor T2: tall copper drill tower ---------------------------
def metal_extractor_t2(P):
    s = Structure(5, 8, 5)
    # same pad language as T1 so the family reads together
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 0, z, P["pad_corner"])
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, P["ring"])
    s.set(2, 0, 0, P["stair"], facing="south", half="bottom")
    s.set(2, 0, 4, P["stair"], facing="north", half="bottom")
    s.set(0, 0, 2, P["stair"], facing="east", half="bottom")
    s.set(4, 0, 2, P["stair"], facing="west", half="bottom")
    s.set(2, 0, 2, "cut_copper")
    # flared copper base: stairs facing in on all four sides of the tower foot
    s.set(2, 1, 1, "cut_copper_stairs", facing="south", half="bottom")
    s.set(2, 1, 3, "cut_copper_stairs", facing="north", half="bottom")
    s.set(1, 1, 2, "cut_copper_stairs", facing="east", half="bottom")
    s.set(3, 1, 2, "cut_copper_stairs", facing="west", half="bottom")
    # tower shaft: cut copper with copper block banding
    s.set(2, 1, 2, "copper_block")
    s.set(2, 2, 2, "cut_copper")
    s.set(2, 3, 2, "cut_copper")
    s.set(2, 4, 2, "copper_block")
    s.set(2, 5, 2, "cut_copper")
    # machinery head: copper cap with stair cornice + drill rod
    s.set(1, 6, 2, "cut_copper_stairs", facing="east", half="top")
    s.set(3, 6, 2, "cut_copper_stairs", facing="west", half="top")
    s.set(2, 6, 1, "cut_copper_stairs", facing="south", half="top")
    s.set(2, 6, 3, "cut_copper_stairs", facing="north", half="top")
    s.set(2, 6, 2, "copper_block")
    s.set(2, 7, 2, "lightning_rod", facing="up")
    # side pipework: iron columns at two corners feeding the head
    for y in (1, 2, 3):
        s.set(0, y, 0, P["bars"])
        s.set(4, y, 4, P["bars"])
    s.set(0, 4, 0, "copper_block")
    s.set(4, 4, 4, "copper_block")
    # furnace shed carried over from T1 (front faces outward, -z)
    s.set(1, 1, 0, P["furnace"], facing="north")
    s.set(3, 1, 0, P["ring"])
    # faction lamp standing on the shed post. Standing, not hanging: the build order only reaches this spot
    # once the full block under it exists, so it is always supported (a hanging lamp could pop off mid-build)
    s.set(3, 2, 0, P["lamp"], hanging="false", waterlogged="false")
    return s


# ---- wind generator: a proper mill - plastered tower, wooden cap, hub log --
# The sails are NOT blocks: WindmillRenderClientEvents finds the horizontal
# stripped spruce log below and renders four spinning blades around it.
def wind_generator(P):
    s = Structure(3, 8, 3)
    # footing: full pad with a cobble flare at the corners
    s.fill(0, 0, 0, 2, 0, 2, P["pad"])
    s.set(1, 0, 1, P["ring"])
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 1, z, P["wall"])
    # plastered tower body: plus-shaped shaft (reads as a round mill from the RTS camera)
    arms = [(1, 1), (1, 0), (1, 2), (0, 1), (2, 1)]
    for y in range(1, 5):
        for (x, z) in arms:
            s.set(x, y, z, P["body"] if (y != 4 or (x, z) == (1, 1)) else P["accent"])   # carved frieze under the cap
    # spruce trim band + doorway shadow at the foot
    s.set(1, 1, 0, P["cap"])
    s.set(1, 3, 0, "glass_pane", north="false", south="false", east="false", west="false")
    # cap base: wooden ring with the rotor hub sticking out the front (-z)
    for (x, z) in arms:
        s.set(x, 5, z, P["cap"])
    s.set(1, 5, 0, P["hub"], axis="z")   # <- rotor hub (found by the renderer)
    # rounded cap: stairs leaning in, slab peak
    s.set(1, 6, 1, P["cap"])
    s.set(0, 6, 1, P["cap_stair"], facing="east", half="bottom")
    s.set(2, 6, 1, P["cap_stair"], facing="west", half="bottom")
    s.set(1, 6, 2, P["cap_stair"], facing="north", half="bottom")
    s.set(1, 6, 0, P["cap_stair"], facing="south", half="bottom")
    s.set(1, 7, 1, P["cap_slab"], type="bottom")
    return s


# ---- energy converter: a 3x3 crucible/furnace/smelter with its fire visible from the RTS camera --------
def energy_converter(P):
    s = Structure(3, 5, 3)
    s.fill(0, 0, 0, 2, 0, 2, P["pad"])
    s.set(1, 0, 1, P["ring"])
    # four posts carrying a band, open sides so the core glows through
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 1, z, P["pillar"], **({"axis": "y"} if P["pillar"].endswith(("log", "basalt", "pillar")) else {}))
        s.set(x, 2, z, P["pillar"], **({"axis": "y"} if P["pillar"].endswith(("log", "basalt", "pillar")) else {}))
    s.set(1, 1, 1, P["furnace"], facing="north", lit="true")
    core = {"lit": "true", "facing": "north", "signal_fire": "false", "waterlogged": "false"} if "campfire" in P["core"] else {}
    s.set(1, 2, 1, P["core"], **core)
    # band ring on top of the posts with a slab rim, chimney above the core
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 3, z, P["band"])
    for (x, z) in [(1, 0), (0, 1), (2, 1), (1, 2)]:
        s.set(x, 3, z, P["slab"], type="bottom")
    s.set(1, 4, 1, P["bars"], **({"axis": "y"} if P["bars"] == "chain" else
                                 {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}))
    return s


# ---- faction houses (7 wide x 5 deep footprint, same as Reign of Nether's) ----
# y=0 must contain the class's startingBlockTypes (placed instantly as the foundation).

def fill_air(s):
    sx, sy, sz = s.size
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                if (x, y, z) not in s.blocks:
                    s.set(x, y, z, "air")


# Macaw's windows are axis-aligned like panes: facing is only ever "north" (x-axis walls) or "east" (z-axis).
def kingdom_house():
    """Half-timbered Tudor cottage: cobble sill, white plaster between oak posts, a thatched steep roof
    (Macaw's Roofs) with ridge cap, spruce casement windows (Macaw's Windows) and a brick chimney with a
    live hearth. Front faces -z. Modded blocks load as air if their mod is absent, so this never crashes."""
    s = Structure(7, 9, 5)
    W, D = 7, 5
    # y0: plank floor, cobble sill front/back, oak-log corner posts (startingBlockTypes: planks + oak log)
    for x in range(W):
        for z in range(D):
            s.set(x, 0, z, "oak_planks")
    for x in range(W):
        s.set(x, 0, 0, "cobblestone"); s.set(x, 0, D - 1, "cobblestone")
    for (x, z) in [(0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)]:
        s.set(x, 0, z, "oak_log", axis="y")
    s.set(3, 0, 0, "oak_planks")   # threshold
    # y1-3: timber frame - corner posts, plaster panels, a stripped-oak beam band at y3
    for y in (1, 2, 3):
        for x in range(W):
            for z in range(D):
                if not (x in (0, W - 1) or z in (0, D - 1)):
                    continue
                if x in (0, W - 1) and z in (0, D - 1):
                    s.set(x, y, z, "oak_log", axis="y")
                elif y == 3:
                    s.set(x, y, z, "stripped_oak_log", axis="x" if z in (0, D - 1) else "z")
                else:
                    s.set(x, y, z, "white_concrete")
    # open doorway under a spruce awning; casement windows on every face
    s.set(3, 1, 0, "air"); s.set(3, 2, 0, "air")
    s.set(3, 3, 0, "spruce_trapdoor", facing="north", half="top", open="false")
    for x in (1, 5):
        s.set(x, 2, 0, "mcwwindows:spruce_window", facing="north", part="base")
        s.set(x, 2, D - 1, "mcwwindows:spruce_window", facing="north", part="base")
    s.set(0, 2, 2, "mcwwindows:spruce_window", facing="east", part="base")
    s.set(W - 1, 2, 2, "mcwwindows:spruce_window", facing="east", part="base")
    # y4-6: thatched steep gable along x, ridge cap over z=2; plastered gable ends with a king post
    for x in range(W):
        s.set(x, 4, 0, "mcwroofs:thatch_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 4, D - 1, "mcwroofs:thatch_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 5, 1, "mcwroofs:thatch_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 5, D - 2, "mcwroofs:thatch_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 6, 2, "mcwroofs:thatch_top_roof", part="switched_90")
    for x in (0, W - 1):
        for z in (1, 2, 3):
            s.set(x, 4, z, "white_concrete")
        s.set(x, 5, 2, "oak_log", axis="y")
    # brick chimney up the back-right through the thatch, a lit hearth on top (real smoke)
    for y in range(4, 8):
        s.set(5, y, 3, "bricks")
    s.set(5, 8, 3, "campfire", lit="true", signal_fire="false", facing="north")
    # a lantern glowing just inside the doorway
    s.set(3, 1, 1, "lantern", hanging="false")
    fill_air(s)
    return s


def fallen_house():
    """Gothic manor: deepslate sill, dark-oak posts over deepslate-tile walls, tall blackstone gothic windows
    (Macaw's Windows), a steep deepslate roof (Macaw's Roofs) and a narrow bell spire lit by a soul lantern;
    cobwebs in the eaves. Front faces -z."""
    s = Structure(7, 11, 5)
    W, D = 7, 5
    for x in range(W):
        for z in range(D):
            s.set(x, 0, z, "spruce_planks")
    for (x, z) in [(0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)]:
        s.set(x, 0, z, "dark_oak_log", axis="y")
    for x in range(1, W - 1):
        s.set(x, 0, 0, "polished_deepslate"); s.set(x, 0, D - 1, "polished_deepslate")
    s.set(3, 0, 0, "spruce_planks")
    # y1-4: tall walls - dark oak posts, deepslate tiles, a dark-oak band under the eaves
    for y in (1, 2, 3, 4):
        for x in range(W):
            for z in range(D):
                if not (x in (0, W - 1) or z in (0, D - 1)):
                    continue
                if x in (0, W - 1) and z in (0, D - 1):
                    s.set(x, y, z, "dark_oak_log", axis="y")
                elif y == 4:
                    s.set(x, y, z, "dark_oak_planks")
                else:
                    s.set(x, y, z, "deepslate_tiles")
    # doorway with a pointed arch; tall gothic windows either side, narrow dark-oak windows elsewhere
    s.set(3, 1, 0, "air"); s.set(3, 2, 0, "air")
    s.set(3, 3, 0, "deepslate_tile_stairs", facing="north", half="top")
    for x in (1, 5):
        s.set(x, 2, 0, "mcwwindows:blackstone_brick_gothic", facing="north", part="bottom", open="false")
        s.set(x, 3, 0, "mcwwindows:blackstone_brick_gothic", facing="north", part="top", open="false")
        s.set(x, 2, D - 1, "mcwwindows:dark_oak_window", facing="north", part="base")
    s.set(0, 2, 2, "mcwwindows:dark_oak_window", facing="east", part="base")
    s.set(W - 1, 2, 2, "mcwwindows:dark_oak_window", facing="east", part="base")
    # a crooked buttress leaning on the left wall
    s.set(0, 1, 1, "cobbled_deepslate_wall"); s.set(0, 1, 3, "cobbled_deepslate_wall")
    # y5-7: steep deepslate gable with ridge cap; dark-oak gable ends
    for x in range(W):
        s.set(x, 5, 0, "mcwroofs:deepslate_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 5, D - 1, "mcwroofs:deepslate_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 6, 1, "mcwroofs:deepslate_steep_roof", facing="south", shape="straight", half="bottom")
        s.set(x, 6, D - 2, "mcwroofs:deepslate_steep_roof", facing="north", shape="straight", half="bottom")
        s.set(x, 7, 2, "mcwroofs:deepslate_top_roof", part="switched_90")
    for x in (0, W - 1):
        for (y, z) in [(5, 1), (5, 2), (5, 3), (6, 2)]:
            s.set(x, y, z, "dark_oak_planks")
    # the bell spire over the front-right, topped by a soul lantern
    for y in range(7, 10):
        s.set(5, y, 1, "deepslate_brick_wall")
    s.set(5, 10, 1, "soul_lantern", hanging="false")
    # cobwebs in the eaves, a soul lantern glowing just inside the door
    for (x, y, z) in [(1, 4, 1), (5, 4, 3), (1, 5, 2)]:
        s.set(x, y, z, "cobweb")
    s.set(3, 1, 1, "soul_lantern", hanging="false")
    fill_air(s)
    return s


# ---- Verdant Court: Heartwood Hall (capitol) and Grove (T1 lab) --------------------------------------------
# y=0 holds the classes' startingBlockTypes (moss block, mossy stone bricks). Nothing hangs: the build order runs
# bottom-up, so a hanging lantern or spore blossom would pop off before the canopy above it exists. Leaves are
# persistent, or they would decay away from the trunk.
LEAF = dict(persistent="true", distance="1", waterlogged="false")


def heartwood_hall():
    """A living-wood pavilion round a great oak trunk: moss floor ringed with mossy stone, eight oak posts carrying a
    stripped-oak beam ring, a domed canopy of azalea and flowering azalea, lanterns on mossy walls, calcite steps
    (the Court's silver) at the -z entrance and mangrove roots splaying from the trunk. 11 x 10 x 11."""
    W = 11
    s = Structure(W, 10, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            s.set(x, 0, z, "mossy_stone_bricks" if edge else "moss_block")
    for x in (c - 1, c, c + 1):
        s.set(x, 0, 0, "calcite")                   # silver steps at the entrance
    # the trunk: 3x3 oak, rising through the canopy
    for y in range(1, 9):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if y >= 7 and dx != 0 and dz != 0:
                    continue                        # rounds off the top
                s.set(c + dx, y, c + dz, "oak_log", axis="y")
    # roots splaying out from the trunk foot
    for (dx, dz) in [(-2, 0), (2, 0), (0, -2), (0, 2), (-2, -2), (2, 2), (-2, 2), (2, -2)]:
        s.set(c + dx, 1, c + dz, "mangrove_roots", waterlogged="false")
    # eight posts on the ring, with mossy walls and standing lanterns between them (the -z middle is the door)
    posts = [(1, 1), (c, 1), (W - 2, 1), (1, c), (W - 2, c), (1, W - 2), (c, W - 2), (W - 2, W - 2)]
    for (x, z) in posts:
        if (x, z) == (c, 1):
            continue
        for y in range(1, 5):
            s.set(x, y, z, "oak_log", axis="y")
    for (x, z) in [(3, 1), (W - 4, 1), (1, 3), (1, W - 4), (W - 2, 3), (W - 2, W - 4), (3, W - 2), (W - 4, W - 2)]:
        s.set(x, 1, z, "mossy_cobblestone_wall")
        s.set(x, 2, z, "lantern", hanging="false", waterlogged="false")
    # beam ring at y=5 joining the posts, spokes into the trunk
    for i in range(1, W - 1):
        s.set(i, 5, 1, "stripped_oak_log", axis="x"); s.set(i, 5, W - 2, "stripped_oak_log", axis="x")
        s.set(1, 5, i, "stripped_oak_log", axis="z"); s.set(W - 2, 5, i, "stripped_oak_log", axis="z")
    for i in range(2, c - 1):
        s.set(i, 5, c, "stripped_oak_log", axis="x"); s.set(W - 1 - i, 5, c, "stripped_oak_log", axis="x")
        s.set(c, 5, i, "stripped_oak_log", axis="z"); s.set(c, 5, W - 1 - i, "stripped_oak_log", axis="z")
    # the canopy dome: wide at y6, narrowing to a crown at y9, flowering every few leaves
    for y, r in [(6, 5.6), (7, 5.0), (8, 3.8), (9, 2.2)]:
        for x in range(W):
            for z in range(W):
                d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
                if d <= r and (x, y, z) not in s.blocks:
                    leaf = "flowering_azalea_leaves" if (x * 7 + z * 3 + y) % 5 == 0 else "azalea_leaves"
                    s.set(x, y, z, leaf, **LEAF)
    # froglights glowing through the canopy where the spokes meet the ring
    for (x, z) in [(c, 1), (1, c), (W - 2, c), (c, W - 2)]:
        s.set(x, 6, z, "verdant_froglight", axis="y")
    # flowering azalea bushes in the corners of the floor
    for (x, z) in [(2, 2), (W - 3, 2), (2, W - 3), (W - 3, W - 3)]:
        s.set(x, 1, z, "flowering_azalea")
    fill_air(s)
    return s


def grove():
    """The Court's training glade: a moss lawn inside mossy stone, a living arch of oak and azalea over the -z
    entrance, two archery targets on oak posts for the Thornbows, a fletching table and a sapling ring. 9 x 7 x 8."""
    W, D = 9, 8
    s = Structure(W, 7, D)
    for x in range(W):
        for z in range(D):
            edge = x in (0, W - 1) or z in (0, D - 1)
            s.set(x, 0, z, "mossy_stone_bricks" if edge else "moss_block")
    # the arch: two oak trunks and a leafy lintel over the entrance
    for y in range(1, 5):
        s.set(2, y, 0, "oak_log", axis="y"); s.set(W - 3, y, 0, "oak_log", axis="y")
    for x in range(1, W - 1):
        s.set(x, 5, 0, "stripped_oak_log", axis="x")
        s.set(x, 6, 0, "azalea_leaves", **LEAF)
    for x in (1, W - 2):
        s.set(x, 5, 1, "flowering_azalea_leaves", **LEAF)
        s.set(x, 4, 0, "azalea_leaves", **LEAF)
    s.set(4, 4, 0, "verdant_froglight", axis="y")
    # low mossy walls down the sides, lanterns standing on them
    for z in range(2, D - 1):
        s.set(0, 1, z, "mossy_cobblestone_wall"); s.set(W - 1, 1, z, "mossy_cobblestone_wall")
    for z in (3, D - 2):
        s.set(0, 2, z, "lantern", hanging="false", waterlogged="false")
        s.set(W - 1, 2, z, "lantern", hanging="false", waterlogged="false")
    # archery targets on posts at the back
    for x in (2, W - 3):
        s.set(x, 1, D - 2, "stripped_birch_log", axis="y")
        s.set(x, 2, D - 2, "target")
    s.set(4, 1, D - 2, "fletching_table")
    # saplings and moss carpet on the lawn
    for (x, z) in [(2, 3), (W - 3, 3), (4, 4)]:
        s.set(x, 1, z, "azalea")
    for (x, z) in [(3, 2), (5, 5), (6, 2), (3, 5)]:
        s.set(x, 1, z, "moss_carpet")
    fill_air(s)
    return s


def circle_of_elders():
    """The Court's T2 lab: five elder oaks standing in a ring round a moonlit well, their crowns knitted into a leafy
    halo open to the sky in the middle. Moss floor in a mossy stone border, a calcite (silver) ring and path to the -z
    entrance arch, the well a mossy rim round a water cauldron on a froglight, lanterns hung under every crown and
    mangrove roots at each trunk foot. 11 x 9 x 11."""
    W = 11
    s = Structure(W, 9, W)
    c = W // 2
    for x in range(W):
        for z in range(W):
            edge = x in (0, W - 1) or z in (0, W - 1)
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if edge:
                s.set(x, 0, z, "mossy_stone_bricks")
            elif 1.6 <= d <= 2.4:
                s.set(x, 0, z, "calcite")              # the silver ring round the well
            else:
                s.set(x, 0, z, "moss_block")
    for z in range(0, c - 1):
        s.set(c, 0, z, "calcite")                      # silver path in from the entrance
    # the well: a froglight under a water cauldron, ringed by a mossy rim
    s.set(c, 0, c, "verdant_froglight", axis="y")
    s.set(c, 1, c, "water_cauldron", level="3")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx and dz:
                s.set(c + dx, 1, c + dz, "mossy_stone_brick_wall")
            elif dx or dz:
                s.set(c + dx, 1, c + dz, "mossy_stone_brick_slab", type="bottom", waterlogged="false")
    # five elders on a ring of radius 4 (the -z point is left open for the entrance)
    trees = [(c + 4, c - 1), (c + 3, c + 3), (c - 3, c + 3), (c - 4, c - 1), (c, c + 4)]
    for (tx, tz) in trees:
        for y in range(1, 7):
            s.set(tx, y, tz, "oak_log", axis="y")
        for (dx, dz) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
            x, z = tx + dx, tz + dz
            if 0 < x < W - 1 and 0 < z < W - 1 and (x, 1, z) not in s.blocks:
                s.set(x, 1, z, "mangrove_roots", waterlogged="false")
        # the crown: a squat dome of azalea, flowering every few leaves
        for y, r in [(5, 1.6), (6, 2.2), (7, 1.9), (8, 1.0)]:
            for x in range(max(0, tx - 3), min(W, tx + 4)):
                for z in range(max(0, tz - 3), min(W, tz + 4)):
                    if ((x - tx) ** 2 + (z - tz) ** 2) ** 0.5 <= r and (x, y, z) not in s.blocks:
                        leaf = "flowering_azalea_leaves" if (x * 5 + z * 3 + y) % 4 == 0 else "azalea_leaves"
                        s.set(x, y, z, leaf, **LEAF)
        # a lantern hung under the crown on the side facing the well
        lx = tx + (1 if tx < c else -1 if tx > c else 0)
        lz = tz + (1 if tz < c else -1 if tz > c else 0)
        if (lx, 4, lz) not in s.blocks and (lx, 5, lz) in s.blocks:
            s.set(lx, 4, lz, "lantern", hanging="true", waterlogged="false")
    # the halo: leaves knitting the crowns together round the open middle
    for x in range(W):
        for z in range(W):
            d = ((x - c) ** 2 + (z - c) ** 2) ** 0.5
            if 3.4 <= d <= 4.6 and (x, 6, z) not in s.blocks and z >= 2:
                s.set(x, 6, z, "azalea_leaves", **LEAF)
    # entrance arch at -z: two stripped oak posts and a leafy lintel with a froglight keystone
    for y in range(1, 5):
        s.set(c - 2, y, 1, "stripped_oak_log", axis="y")
        s.set(c + 2, y, 1, "stripped_oak_log", axis="y")
    for x in range(c - 2, c + 3):
        s.set(x, 5, 1, "stripped_oak_log", axis="x")
        if (x, 6, 1) not in s.blocks:
            s.set(x, 6, 1, "flowering_azalea_leaves" if x == c else "azalea_leaves", **LEAF)
    s.set(c, 4, 1, "lantern", hanging="true", waterlogged="false")
    # moss carpet and a few ferns on the lawn
    for (x, z) in [(2, 3), (8, 3), (3, 7), (7, 7), (2, 6), (8, 6)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "moss_carpet")
    for (x, z) in [(1, 5), (9, 5), (5, 9)]:
        if (x, 1, z) not in s.blocks:
            s.set(x, 1, z, "fern")
    fill_air(s)
    return s


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(OUT_DATA, exist_ok=True)
    for out in (OUT, OUT_DATA):
        for variant, P in PALETTES.items():
            metal_extractor(P).write(f"{out}/metal_extractor{variant}.nbt")
            metal_extractor_t2(P).write(f"{out}/metal_extractor_t2{variant}.nbt")
            wind_generator(P).write(f"{out}/wind_generator{variant}.nbt")
            energy_converter(P).write(f"{out}/energy_converter{variant}.nbt")
        kingdom_house().write(f"{out}/villager_house.nbt")
        fallen_house().write(f"{out}/haunted_house.nbt")
        heartwood_hall().write(f"{out}/heartwood_hall.nbt")
        grove().write(f"{out}/grove.nbt")
        circle_of_elders().write(f"{out}/circle_of_elders.nbt")
