#!/usr/bin/env python3
"""Isometric preview renderer for Minecraft structure NBT files (no dependencies beyond Pillow).

Usage: python3 tools/preview_structure.py <file.nbt> [more.nbt ...] -o out.png

Renders each structure as shaded isometric cubes coloured by an approximate block palette, side by side,
so building changes can be eyeballed before a ~10 minute CI round trip. Unknown blocks fall back to a
hash-derived colour. Thin blocks (slabs, stairs, walls, panes, bars) are drawn as full cubes - it is a
silhouette/palette check, not a faithful render.
"""
import gzip
import struct
import sys
import hashlib
from PIL import Image, ImageDraw, ImageFont


# ---- minimal NBT reader ------------------------------------------------------
def _read(buf, pos, t):
    if t == 1:  return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if t == 2:  return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if t == 3:  return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if t == 4:  return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if t == 5:  return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if t == 6:  return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if t == 7:
        n = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return buf[pos:pos + n], pos + n
    if t == 8:
        n = struct.unpack_from(">H", buf, pos)[0]; pos += 2
        return buf[pos:pos + n].decode("utf-8", "replace"), pos + n
    if t == 9:
        et = buf[pos]; n = struct.unpack_from(">i", buf, pos + 1)[0]; pos += 5
        out = []
        for _ in range(n):
            v, pos = _read(buf, pos, et); out.append(v)
        return out, pos
    if t == 10:
        out = {}
        while True:
            tt = buf[pos]; pos += 1
            if tt == 0:
                return out, pos
            nl = struct.unpack_from(">H", buf, pos)[0]; pos += 2
            name = buf[pos:pos + nl].decode("utf-8", "replace"); pos += nl
            out[name], pos = _read(buf, pos, tt)
    if t == 11:
        n = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return list(struct.unpack_from(">%di" % n, buf, pos)), pos + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return list(struct.unpack_from(">%dq" % n, buf, pos)), pos + 8 * n
    raise ValueError("bad tag %d" % t)


def load_nbt(path):
    raw = open(path, "rb").read()
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    assert raw[0] == 10
    nl = struct.unpack_from(">H", raw, 1)[0]
    root, _ = _read(raw, 3 + nl, 10)
    return root


# ---- colours -----------------------------------------------------------------
COLOURS = {
    "stone": (125, 125, 125), "cobblestone": (110, 110, 110), "andesite": (135, 135, 135),
    "deepslate": (70, 70, 75), "blackstone": (42, 36, 42), "gilded": (190, 150, 50),
    "sandstone": (216, 202, 155), "mud": (140, 105, 80), "terracotta": (160, 90, 65),
    "white_terracotta": (210, 180, 160), "oak": (160, 130, 80), "spruce": (110, 80, 50),
    "dark_oak": (66, 43, 20), "birch": (200, 185, 130), "crimson": (125, 55, 80), "warped": (45, 110, 105),
    "jungle": (155, 110, 75), "acacia": (170, 90, 50), "mangrove": (115, 55, 50), "bamboo": (190, 175, 90),
    "cherry": (225, 175, 170), "log": (100, 80, 50), "planks": (160, 130, 80), "brick": (150, 85, 70),
    "iron": (215, 215, 215), "gold": (245, 205, 60), "copper": (190, 110, 80), "raw_iron": (180, 150, 120),
    "glass": (190, 220, 230), "wool": (230, 230, 230), "hay": (210, 180, 60), "grass": (95, 150, 60),
    "dirt": (130, 95, 65), "farmland": (100, 70, 45), "gravel": (135, 128, 125), "leaves": (60, 110, 40),
    "lantern": (250, 200, 100), "torch": (250, 200, 100), "campfire": (230, 120, 40), "fire": (240, 120, 30),
    "soul": (90, 200, 210), "water": (60, 90, 200), "lava": (230, 100, 20), "quartz": (235, 230, 225),
    "purpur": (170, 125, 170), "prismarine": (90, 160, 150), "obsidian": (25, 20, 35), "netherrack": (110, 50, 50),
    "nether_brick": (55, 28, 33), "red_nether": (90, 20, 20), "basalt": (80, 80, 85), "bone": (225, 220, 200),
    "moss": (90, 120, 50), "polished": (120, 120, 125), "smooth_stone": (160, 160, 160), "chain": (60, 65, 75),
    "bars": (100, 100, 100), "white": (235, 235, 235), "black": (25, 25, 30), "red": (160, 40, 35),
    "blue": (50, 70, 160), "yellow": (240, 200, 50), "purple": (120, 50, 160), "green": (85, 110, 30),
    "gray": (70, 75, 80), "light_gray": (150, 150, 145), "brown": (110, 75, 45), "orange": (230, 120, 30),
    "cyan": (30, 130, 145), "lime": (110, 185, 30), "pink": (225, 130, 160), "magenta": (180, 70, 170),
    "light_blue": (60, 170, 215),
}
SKIP = ("air", "structure_void", "barrier", "light")


def colour_for(name):
    n = name.split(":")[-1]
    best, best_len = None, 0
    for key, col in COLOURS.items():
        if key in n and len(key) > best_len:
            best, best_len = col, len(key)
    if best:
        return best
    h = hashlib.md5(n.encode()).digest()
    return (80 + h[0] % 140, 80 + h[1] % 140, 80 + h[2] % 140)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# ---- render ------------------------------------------------------------------
def render(path, cell=10):
    root = load_nbt(path)
    palette = root.get("palette") or (root.get("palettes") or [[]])[0]
    names = [p.get("Name", "minecraft:air") for p in palette]
    voxels = []
    for b in root.get("blocks", []):
        name = names[b["state"]]
        if any(name.endswith(s) for s in SKIP):
            continue
        x, y, z = b["pos"]
        voxels.append((x, y, z, colour_for(name)))
    sx, sy, sz = root["size"]
    w = int((sx + sz) * cell * 0.87) + cell * 4
    h = int((sx + sz) * cell * 0.5 + sy * cell) + cell * 6
    img = Image.new("RGB", (w, h), (34, 38, 44))
    d = ImageDraw.Draw(img)
    ox, oy = w // 2, int(sy * cell) + cell * 3
    # painter's order: far to near
    voxels.sort(key=lambda v: (v[0] + v[2], v[1]))
    occupied = {(v[0], v[1], v[2]) for v in voxels}
    for x, y, z, c in voxels:
        cx = ox + (x - z) * cell * 0.87
        cy = oy + (x + z) * cell * 0.5 - y * cell
        top = [(cx, cy - cell * 0.5), (cx + cell * 0.87, cy), (cx, cy + cell * 0.5), (cx - cell * 0.87, cy)]
        left = [(cx - cell * 0.87, cy), (cx, cy + cell * 0.5), (cx, cy + cell * 1.5), (cx - cell * 0.87, cy + cell)]
        right = [(cx + cell * 0.87, cy), (cx, cy + cell * 0.5), (cx, cy + cell * 1.5), (cx + cell * 0.87, cy + cell)]
        if (x, y + 1, z) not in occupied:
            d.polygon(top, fill=shade(c, 1.1))
        if (x, y, z + 1) not in occupied:
            d.polygon(left, fill=shade(c, 0.78))
        if (x + 1, y, z) not in occupied:
            d.polygon(right, fill=shade(c, 0.62))
    label = path.split("/")[-1].replace(".nbt", "") + f"  {sx}x{sy}x{sz}  {len(voxels)} blocks"
    d.text((6, 4), label, fill=(230, 230, 230))
    return img


def main(argv):
    out = "preview.png"
    files = []
    i = 0
    while i < len(argv):
        if argv[i] == "-o":
            out = argv[i + 1]; i += 2
        else:
            files.append(argv[i]); i += 1
    imgs = [render(f) for f in files]
    W = sum(im.width for im in imgs) + 8 * (len(imgs) + 1)
    H = max(im.height for im in imgs) + 16
    sheet = Image.new("RGB", (W, H), (20, 22, 26))
    x = 8
    for im in imgs:
        sheet.paste(im, (x, 8)); x += im.width + 8
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])
