#!/usr/bin/env python3
"""Generates the Verdant thicket bush models + blockstate (output is committed).

Usage: python3 tools/gen_thicket_models.py   (writes into bar/src/main/resources/assets/reignofnether)

A thicket is a ~1.4 block azalea bush: a plus-shaped dark leaf base (oak leaves tinted a fixed deep green in
ClientModEvents, so it never follows the biome), an azalea middle, a flowering crown, and fern / flower crosses
in the corners so a patch of them reads as shrubbery from the RTS camera instead of a carpet of lime cubes.
Each age gets 4 y-rotations (age 3 also a second flower mix) so neighbouring bushes don't line up.
"""
import json, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "bar/src/main/resources/assets/reignofnether")

TEX = {
    "particle": "minecraft:block/azalea_leaves",
    "leaves": "minecraft:block/azalea_leaves",
    "flowers": "minecraft:block/flowering_azalea_leaves",
    "dark": "minecraft:block/oak_leaves",      # grey texture, tinted a fixed deep green (tintindex 0)
    "fern": "minecraft:block/fern",            # likewise tinted
    "stem": "minecraft:block/oak_log",
    "allium": "minecraft:block/allium",
    "daisy": "minecraft:block/oxeye_daisy",
    "bluet": "minecraft:block/azure_bluet",
    "cornflower": "minecraft:block/cornflower",
}
TINTED = {"dark", "fern"}


def clampuv(a, b):
    a, b = max(0.0, min(16.0, a)), max(0.0, min(16.0, b))
    if b - a < 1:
        return 0.0, 16.0
    return a, b


def box(f, t, tex, up=None):
    """Axis-aligned box; uv follows the face's own extent so leaves aren't stretched."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    def face(texkey, uv):
        d = {"texture": "#" + texkey, "uv": list(uv)}
        if texkey in TINTED:
            d["tintindex"] = 0
        return d
    vy = clampuv(16 - y2, 16 - y1)
    ux = clampuv(x1, x2)
    uz = clampuv(z1, z2)
    return {
        "from": [x1, y1, z1], "to": [x2, y2, z2],
        "faces": {
            "north": face(tex, (16 - ux[1], vy[0], 16 - ux[0], vy[1])),
            "south": face(tex, (ux[0], vy[0], ux[1], vy[1])),
            "west": face(tex, (uz[0], vy[0], uz[1], vy[1])),
            "east": face(tex, (16 - uz[1], vy[0], 16 - uz[0], vy[1])),
            "up": face(up or tex, (ux[0], uz[0], ux[1], uz[1])),
            "down": face(tex, (ux[0], uz[0], ux[1], uz[1])),
        },
    }


def cross(cx, cz, size, height, tex, y0=0):
    """Two crossed planes (like a flower) centred on (cx, cz)."""
    h = size / 2
    out = []
    for ang in (45, -45):
        face = {"texture": "#" + tex, "uv": [0, 0, 16, 16]}
        if tex in TINTED:
            face["tintindex"] = 0
        out.append({
            "from": [cx - h, y0, cz], "to": [cx + h, y0 + height, cz],
            "shade": False,
            "rotation": {"origin": [cx, y0, cz], "axis": "y", "angle": ang},
            "faces": {"north": dict(face), "south": dict(face)},
        })
    return out


def model(elements):
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout_mipped",
        "ambientocclusion": True,
        "textures": TEX,
        "elements": elements,
    }


stage0 = [box([5.5, 0, 5.5], [10.5, 4, 10.5], "leaves")] + cross(8, 8, 9, 7, "fern") + cross(11.5, 4.5, 5, 6, "daisy")

stage1 = ([box([3, 0, 3.5], [13, 6, 12.5], "dark"),
           box([4.5, 4, 4], [11.5, 10, 12], "leaves", up="flowers")]
          + cross(2.5, 2.5, 6, 8, "fern") + cross(13.5, 13, 6, 7, "fern") + cross(13, 3, 5, 6, "allium"))

stage2 = ([box([7, 0, 7], [9, 4, 9], "stem"),
           box([1.5, 0.5, 3.5], [14.5, 8, 12.5], "dark"),
           box([3.5, 0.5, 1.5], [12.5, 9, 14.5], "dark"),
           box([3, 7, 3], [13, 13, 13], "leaves"),
           box([5, 12, 5.5], [11, 16, 11], "flowers")]
          + cross(2, 2, 6, 10, "fern") + cross(14, 14, 6, 9, "fern")
          + cross(14, 2.5, 5, 7, "bluet") + cross(2.5, 13.5, 5, 7, "daisy"))


def mature(flower_a, flower_b, crown_off):
    ox = crown_off
    return ([box([7, 0, 7], [9, 6, 9], "stem"),
             box([1, 0.5, 3], [15, 9, 13], "dark"),
             box([3, 0.5, 1], [13, 10, 15], "dark"),
             box([2.5, 8, 2.5], [13.5, 16, 13.5], "leaves"),
             box([4 + ox, 14, 4.5], [12 + ox, 20, 12], "leaves", up="flowers"),
             box([5.5 + ox, 18, 6], [10.5 + ox, 22.5, 11], "flowers")]
            + cross(1.5, 1.5, 6, 12, "fern") + cross(14.5, 14.5, 6, 11, "fern")
            + cross(14.5, 1.5, 5, 9, flower_a) + cross(1.5, 14.5, 5, 9, flower_b)
            + cross(8, 0.5, 5, 7, "fern"))


stage3 = mature("allium", "daisy", 0)
stage3b = mature("cornflower", "bluet", -1.5)

mdir = os.path.join(OUT, "models/block")
for name, els in [("thicket_stage0", stage0), ("thicket_stage1", stage1), ("thicket_stage2", stage2),
                  ("thicket_stage3", stage3), ("thicket_stage3b", stage3b)]:
    with open(os.path.join(mdir, name + ".json"), "w") as fh:
        json.dump(model(els), fh, indent=2)
        fh.write("\n")


def rots(m):
    return [{"model": "reignofnether:block/" + m, "y": y} if y else {"model": "reignofnether:block/" + m}
            for y in (0, 90, 180, 270)]


bs = {"variants": {
    "age=0": rots("thicket_stage0"),
    "age=1": rots("thicket_stage1"),
    "age=2": rots("thicket_stage2"),
    "age=3": rots("thicket_stage3") + rots("thicket_stage3b"),
}}
with open(os.path.join(OUT, "blockstates/thicket.json"), "w") as fh:
    json.dump(bs, fh, indent=2)
    fh.write("\n")
print("ok")
