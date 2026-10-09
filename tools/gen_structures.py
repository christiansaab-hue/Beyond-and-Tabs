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
            pairs = [(8, "Name", t_string("minecraft:" + name))]
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


# ---- metal extractor T1: open drill derrick over the patch -----------------
def metal_extractor():
    s = Structure(5, 5, 5)
    # worked pad: gravel corners, stone brick cross
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 0, z, "gravel")
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, "stone_bricks")
    # stair skirt pointing outward at edge midpoints (facing = direction the stair climbs toward)
    s.set(2, 0, 0, "stone_brick_stairs", facing="south", half="bottom")
    s.set(2, 0, 4, "stone_brick_stairs", facing="north", half="bottom")
    s.set(0, 0, 2, "stone_brick_stairs", facing="east", half="bottom")
    s.set(4, 0, 2, "stone_brick_stairs", facing="west", half="bottom")
    s.set(2, 0, 2, "polished_andesite")
    # four derrick legs leaning in: walls at corners, then top frame
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 1, z, "stone_brick_wall")
        s.set(x, 2, z, "stone_brick_wall")
    for (x, z) in [(1, 1), (3, 1), (1, 3), (3, 3)]:
        s.set(x, 3, z, "smooth_stone_slab", type="bottom")
    # crossbeams at the top of the legs
    s.set(2, 3, 1, "smooth_stone_slab", type="bottom")
    s.set(2, 3, 3, "smooth_stone_slab", type="bottom")
    s.set(1, 3, 2, "smooth_stone_slab", type="bottom")
    s.set(3, 3, 2, "smooth_stone_slab", type="bottom")
    # the drill itself: iron column + rod bit, hanging from the frame center
    s.set(2, 1, 2, "iron_block")
    s.set(2, 2, 2, "iron_block")
    s.set(2, 3, 2, "iron_block")
    s.set(2, 4, 2, "lightning_rod", facing="up")
    # a furnace shed corner so it reads "industry" (front faces outward, -z)
    s.set(1, 1, 0, "blast_furnace", facing="north")
    s.set(3, 1, 0, "stone_bricks")
    s.set(3, 2, 0, "stone_brick_slab", type="bottom")
    return s


# ---- metal extractor T2: tall copper drill tower ---------------------------
def metal_extractor_t2():
    s = Structure(5, 8, 5)
    # same pad language as T1 so the family reads together
    for (x, z) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
        s.set(x, 0, z, "gravel")
    for (x, z) in [(1, 0), (3, 0), (0, 1), (0, 3), (4, 1), (4, 3), (1, 4), (3, 4)]:
        s.set(x, 0, z, "stone_bricks")
    s.set(2, 0, 0, "stone_brick_stairs", facing="south", half="bottom")
    s.set(2, 0, 4, "stone_brick_stairs", facing="north", half="bottom")
    s.set(0, 0, 2, "stone_brick_stairs", facing="east", half="bottom")
    s.set(4, 0, 2, "stone_brick_stairs", facing="west", half="bottom")
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
        s.set(0, y, 0, "iron_bars")
        s.set(4, y, 4, "iron_bars")
    s.set(0, 4, 0, "copper_block")
    s.set(4, 4, 4, "copper_block")
    # furnace shed carried over from T1 (front faces outward, -z)
    s.set(1, 1, 0, "blast_furnace", facing="north")
    s.set(3, 1, 0, "stone_bricks")
    s.set(3, 2, 0, "stone_brick_slab", type="bottom")
    return s


# ---- wind generator: a proper mill - plastered tower, wooden cap, hub log --
# The sails are NOT blocks: WindmillRenderClientEvents finds the horizontal
# stripped spruce log below and renders four spinning blades around it.
def wind_generator():
    s = Structure(3, 8, 3)
    # footing: full pad with a cobble flare at the corners
    s.fill(0, 0, 0, 2, 0, 2, "polished_andesite")
    s.set(1, 0, 1, "smooth_stone")
    for (x, z) in [(0, 0), (2, 0), (0, 2), (2, 2)]:
        s.set(x, 1, z, "cobblestone_wall")
    # plastered tower body: plus-shaped shaft (reads as a round mill from the RTS camera)
    arms = [(1, 1), (1, 0), (1, 2), (0, 1), (2, 1)]
    for y in range(1, 5):
        for (x, z) in arms:
            s.set(x, y, z, "white_terracotta")
    # spruce trim band + doorway shadow at the foot
    s.set(1, 1, 0, "spruce_planks")
    s.set(1, 3, 0, "glass_pane", north="false", south="false", east="false", west="false")
    # cap base: wooden ring with the rotor hub sticking out the front (-z)
    for (x, z) in arms:
        s.set(x, 5, z, "spruce_planks")
    s.set(1, 5, 0, "stripped_spruce_log", axis="z")   # <- rotor hub (found by the renderer)
    # rounded cap: stairs leaning in, slab peak
    s.set(1, 6, 1, "spruce_planks")
    s.set(0, 6, 1, "spruce_stairs", facing="east", half="bottom")
    s.set(2, 6, 1, "spruce_stairs", facing="west", half="bottom")
    s.set(1, 6, 2, "spruce_stairs", facing="north", half="bottom")
    s.set(1, 6, 0, "spruce_stairs", facing="south", half="bottom")
    s.set(1, 7, 1, "spruce_slab", type="bottom")
    return s


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    metal_extractor().write(f"{OUT}/metal_extractor.nbt")
    metal_extractor_t2().write(f"{OUT}/metal_extractor_t2.nbt")
    wind_generator().write(f"{OUT}/wind_generator.nbt")
