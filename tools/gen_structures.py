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

OUT = "bar/src/main/resources/assets/reignofnether/structures"
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
# The Kingdom (villagers): worked stone and plaster. The Fallen (monsters): deepslate,
# dark oak and soul fire. The Gilded Legion (piglins): blackstone, gold and crimson.
PALETTES = {
    "": dict(
        pad="polished_andesite", pad_corner="gravel", ring="stone_bricks",
        stair="stone_brick_stairs", wall="stone_brick_wall", slab="smooth_stone_slab",
        metal="iron_block", body="white_terracotta", cap="spruce_planks",
        cap_stair="spruce_stairs", cap_slab="spruce_slab", hub="stripped_spruce_log",
        furnace="blast_furnace", bars="iron_bars",
    ),
    "_dark": dict(
        pad="polished_deepslate", pad_corner="coarse_dirt", ring="deepslate_bricks",
        stair="deepslate_brick_stairs", wall="deepslate_brick_wall", slab="deepslate_brick_slab",
        metal="iron_block", body="deepslate_bricks", cap="dark_oak_planks",
        cap_stair="dark_oak_stairs", cap_slab="dark_oak_slab", hub="stripped_dark_oak_log",
        furnace="blast_furnace", bars="chain",
    ),
    "_nether": dict(
        pad="polished_blackstone", pad_corner="soul_soil", ring="polished_blackstone_bricks",
        stair="polished_blackstone_brick_stairs", wall="polished_blackstone_brick_wall",
        slab="polished_blackstone_brick_slab", metal="gilded_blackstone", body="polished_blackstone",
        cap="crimson_planks", cap_stair="crimson_stairs", cap_slab="crimson_slab",
        hub="stripped_crimson_stem", furnace="blast_furnace", bars="chain",
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
    s.set(3, 2, 0, P["slab"], type="bottom")
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
    s.set(3, 2, 0, P["slab"], type="bottom")
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
            s.set(x, y, z, P["body"])
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


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for variant, P in PALETTES.items():
        metal_extractor(P).write(f"{OUT}/metal_extractor{variant}.nbt")
        metal_extractor_t2(P).write(f"{OUT}/metal_extractor_t2{variant}.nbt")
        wind_generator(P).write(f"{OUT}/wind_generator{variant}.nbt")
    kingdom_house().write(f"{OUT}/villager_house.nbt")
    fallen_house().write(f"{OUT}/haunted_house.nbt")
