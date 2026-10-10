#!/usr/bin/env python3
"""Draws the Tidewrought's unit skins and portrait icons (original pixel art, no source textures).

Reuses the skin toolkit of tools/gen_verdant_skins.py (box layouts, Skin, faces, robe/arms/legs helpers, Icon) so the
two factions are painted the same way; only the palette and the units differ. Every skin keeps the UV layout of the
model its renderer uses:
  - VillagerUnitModel (64x64): Shipwright (and the Admiral), Cutlass Raider, Reef Guard, Bombard Crew. Its head's
    8x12x8 "hat" box (32,0) is drawn wherever it is opaque: the corsairs use it for tricornes, a kerchief, a helmet.
  - vanilla WitchModel (64x128): Tide Priest (the pointed hat becomes a tall kelp mitre).
The Gull Spotter keeps Alex's Mobs' own seagull texture (RenderSeagull); it only gets a portrait here.

Art direction (claude/design-factions.md, the Tidewrought): corsairs and tide-priests in teal and brass - sea-teal
cloth, brass buckles and plate, weathered timber browns, kelp green, coral accents. Like the Court, the top faces (hats,
shoulders) carry the strongest shapes because the RTS camera looks down, with strong value steps and light noise.

usage: python3 tools/gen_tide_skins.py [--preview DIR]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png (16x16 art at 4x);
  --preview also writes a contact sheet (front/back mock-ups and the portraits) into DIR.
"""
import importlib.util, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("gen_verdant_skins", os.path.join(HERE, "gen_verdant_skins.py"))
V = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(V)

Skin, VILLAGER, WITCH, SIDES, Icon = V.Skin, V.VILLAGER, V.WITCH, V.SIDES, V.Icon
sh, villager_face, robe, arms, legs, shoulder_tops = V.sh, V.villager_face, V.robe, V.arms, V.legs, V.shoulder_tops
ENT, HEADS = V.ENT, V.HEADS

# ------------------------------------------------------------------ palette
TEAL_D, TEAL, TEAL_L = (16, 74, 72), (30, 127, 122), (72, 186, 176)
SEA = (60, 200, 190)
BRASS_D, BRASS, BRASS_L = (120, 88, 30), (201, 162, 62), (240, 212, 120)
WOOD_D, WOOD, WOOD_L = (54, 40, 30), (96, 70, 48), (140, 106, 72)
KELP_D, KELP, KELP_L = (30, 58, 30), (56, 98, 44), (98, 140, 60)
CORAL, CORAL_L = (220, 96, 92), (246, 150, 136)
SLATE_D, SLATE = (40, 48, 54), (70, 82, 90)
LINEN, LINEN_D = (222, 214, 190), (170, 160, 134)
RED, RED_D = (170, 40, 40), (110, 26, 26)
BLACK = (26, 26, 30)
SKIN_SUN, SKIN_BRONZE, SKIN_PALE, SKIN_DEEP = (214, 168, 124), (176, 126, 86), (232, 204, 176), (120, 84, 60)


def tricorne(s, c, trim, band=None):
    """A three-cornered hat on the hat box: brim on the top face with the three points, a crown, a trim line."""
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(c, 4))
    # the points: a lighter trim round the brim and three upturned corners (front-left, front-right, back)
    for i in range(8):
        s.fpx("hat", "top", i, 0, trim)
        s.fpx("hat", "top", i, 7, sh(c, 0.8))
    for j in range(8):
        s.fpx("hat", "top", 0, j, trim)
        s.fpx("hat", "top", 7, j, trim)
    for (i, j) in ((0, 0), (7, 0), (3, 7), (4, 7)):
        s.fpx("hat", "top", i, j, sh(trim, 1.15))
    # the crown sits on the head's upper rows round all four sides
    for f in SIDES:
        for i in range(8):
            for j in range(0, 3):
                s.fpx("hat", f, i, j, s.n(c, 4))
            s.fpx("hat", f, i, 3, trim)
    if band:
        for f in SIDES:
            for i in range(8):
                s.fpx("hat", f, i, 2, band)


def boots_and_breeches(s, cloth, boot, buckle=None):
    legs(s, cloth, boot)
    if buckle:
        for f in ("front",):
            s.fpx("leg", f, 1, 8, buckle)
            s.fpx("leg", f, 2, 8, buckle)


# ------------------------------------------------------------------ the units (villager model)
def shipwright():
    """The worker (and the Admiral, who adds the livery's brass cap and teal coat): a sea-teal smock, a leather apron
    with brass buckles and a mallet loop, rolled linen sleeves, a red kerchief knotted on the head (hat box top)."""
    s = Skin(VILLAGER, (64, 64), 51)
    villager_face(s, SKIN_SUN, (60, 110, 120), WOOD_D, hair=WOOD, beard=WOOD)
    robe(s, TEAL, TEAL_D, sash=8, sash_c=WOOD_D)
    # the leather apron down the front of the smock, brass buckles at the sash
    for j in range(9, 17):
        for i in range(2, 6):
            s.fpx("jacket", "front", i, j, s.n(WOOD_L, 4))
    for i in range(2, 6):
        s.fpx("jacket", "front", i, 16, WOOD)
    s.fpx("jacket", "front", 2, 8, BRASS_L); s.fpx("jacket", "front", 5, 8, BRASS_L)
    s.fpx("jacket", "front", 1, 10, BRASS); s.fpx("jacket", "front", 6, 10, BRASS)   # tool loops
    arms(s, LINEN, TEAL_D, SKIN_SUN)
    boots_and_breeches(s, WOOD, WOOD_D, BRASS)
    shoulder_tops(s, TEAL_L)
    # the kerchief: red on the crown (top face of the hat box) with a knot falling down the back
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(RED, 5))
    for i in range(1, 7):
        s.fpx("hat", "front", i, 1, RED_D)
        s.fpx("hat", "front", i, 2, RED)
    for f in ("right", "left", "back"):
        for i in range(1, 7):
            s.fpx("hat", f, i, 2, RED)
    for j in range(2, 6):
        s.fpx("hat", "back", 3, j, RED_D); s.fpx("hat", "back", 4, j, RED)
    return s


def cutlass_raider():
    """The raider: a striped linen shirt under a teal sash, a black tricorne with a brass trim (the strongest shape
    from above), a red sash knot, bronzed sailor's skin and an eye patch."""
    s = Skin(VILLAGER, (64, 64), 52)
    villager_face(s, SKIN_BRONZE, (40, 40, 40), BLACK, hair=BLACK)
    for i in (1, 2):
        s.fpx("head", "front", i, 4, BLACK)          # the eye patch
    for i in range(4):
        s.fpx("head", "front", i, 3, BLACK)          # its strap across the brow
    # striped shirt
    s.fill("body", LINEN, 4)
    s.fill("jacket", LINEN, 4)
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for j in range(0, 12, 2):
            for i in range(w):
                s.fpx("jacket", f, i, j, s.n(TEAL_D, 3))
        x, y, w, h = s.face("body", f)
        for j in range(0, 10, 2):
            for i in range(w):
                s.fpx("body", f, i, j, s.n(TEAL_D, 3))
    # the sash and breeches below it
    s.band("jacket", 9, 11, RED)
    s.band("body", 9, 11, RED)
    s.fpx("jacket", "left", 2, 11, RED_D); s.fpx("jacket", "left", 2, 12, RED_D)
    s.band("jacket", 11, 18, TEAL_D)
    s.band("jacket", 17, 18, BLACK)
    s.vshade("jacket", 1.05, 0.82)
    arms(s, LINEN, TEAL, SKIN_BRONZE, bracer=WOOD_D)
    boots_and_breeches(s, TEAL_D, BLACK, BRASS)
    shoulder_tops(s, LINEN, accent=TEAL_D)
    tricorne(s, BLACK, BRASS)
    return s


def reef_guard():
    """The line: brass plate over a teal gambeson, studded with coral; a brass morion helmet (hat box) with a coral
    crest along the top - from above, a gold dome with a red ridge."""
    s = Skin(VILLAGER, (64, 64), 53)
    villager_face(s, SKIN_SUN, (50, 80, 90), WOOD_D, hair=WOOD_D, beard=WOOD)
    s.fill("body", BRASS, 5)
    s.fill("jacket", TEAL, 4)
    # the breastplate: brass over the upper robe, a darker rim and rivets, coral growing on it
    s.band("jacket", 0, 9, BRASS)
    s.band("jacket", 8, 9, BRASS_D)
    for f in SIDES:
        for i in (1, 6):
            s.fpx("jacket", f, i, 2, BRASS_L)
            s.fpx("jacket", f, i, 6, BRASS_L)
    for (i, j) in ((2, 3), (3, 4), (5, 2), (5, 5)):
        s.fpx("jacket", "front", i, j, CORAL)
    s.fpx("jacket", "front", 3, 3, CORAL_L)
    # tassets of teal quilting below
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            if i % 2 == 0:
                for j in range(10, 17):
                    s.fpx("jacket", f, i, j, TEAL_D)
    s.band("jacket", 17, 18, BRASS_D)
    s.vshade("jacket", 1.08, 0.8)
    arms(s, TEAL, BRASS, SKIN_SUN, bracer=BRASS_D)
    boots_and_breeches(s, TEAL_D, BRASS_D)
    # pauldrons: brass shoulder tops with a coral stud
    shoulder_tops(s, BRASS_L, accent=CORAL)
    for part in ("arm_up", "arm"):
        s.band(part, 0, 3, BRASS)
    # the morion: brass dome on the crown, brim round the sides, coral crest front to back
    for i in range(8):
        for j in range(8):
            s.fpx("hat", "top", i, j, s.n(BRASS, 5))
    for j in range(8):
        s.fpx("hat", "top", 3, j, CORAL); s.fpx("hat", "top", 4, j, CORAL_L if j % 2 else CORAL)
    for f in SIDES:
        for i in range(8):
            s.fpx("hat", f, i, 0, BRASS_L)
            s.fpx("hat", f, i, 1, BRASS)
            s.fpx("hat", f, i, 2, BRASS_D)
    s.fpx("hat", "front", 3, 0, CORAL); s.fpx("hat", "front", 4, 0, CORAL)
    return s


def bombard_crew():
    """The gunner: a slate coat with brass buttons, a powder belt of little casks, a soot-smudged face and a brass
    gunner's cap with a teal band."""
    s = Skin(VILLAGER, (64, 64), 54)
    villager_face(s, SKIN_PALE, (70, 70, 80), SLATE_D, hair=WOOD)
    for (i, j) in ((5, 6), (6, 7), (1, 7)):
        s.fpx("head", "front", i, j, sh(SKIN_PALE, 0.7))   # soot
    robe(s, SLATE, SLATE_D, trim=BRASS_D)
    for j in (2, 5, 8):
        s.fpx("jacket", "front", 3, j, BRASS_L); s.fpx("jacket", "front", 4, j, BRASS_L)
    # the powder belt: a bandolier of casks across the chest, a dark belt at the waist
    for j in range(9):
        i = 7 - min(7, j)
        s.fpx("jacket", "front", i, j, WOOD_D)
        if j % 3 == 1:
            s.fpx("jacket", "front", i, j, WOOD_L)
    s.band("jacket", 9, 10, BLACK)
    s.fpx("jacket", "front", 3, 9, BRASS); s.fpx("jacket", "front", 4, 9, BRASS)
    arms(s, SLATE, TEAL, SKIN_PALE, bracer=WOOD_D)
    boots_and_breeches(s, SLATE_D, BLACK)
    shoulder_tops(s, sh(SLATE, 1.2), accent=BRASS)
    # the cap: brass crown with a teal band (no brim: the crew sights along the barrel)
    for i in range(1, 7):
        for j in range(1, 7):
            s.fpx("hat", "top", i, j, s.n(BRASS, 5))
    s.fpx("hat", "top", 3, 3, BRASS_L); s.fpx("hat", "top", 4, 4, BRASS_L)
    for f in SIDES:
        for i in range(1, 7):
            s.fpx("hat", f, i, 0, BRASS)
            s.fpx("hat", f, i, 1, TEAL)
    return s


def tide_priest():
    """The support: kelp-green over sea-teal robes with a shell belt and a coral pendant; the witch hat recut as a
    tall kelp mitre with a brass band and a pale shell at its tip (a big readable cone from above)."""
    s = Skin(WITCH, (64, 128), 55)
    SHELL, SHELL_D = (236, 226, 206), (190, 172, 150)
    villager_face(s, SKIN_DEEP, (90, 210, 200), KELP_D, hair=KELP_D)
    s.fill("mole", CORAL, 0)
    robe(s, TEAL, TEAL_D)
    # the overrobe of kelp strands over the upper body, hanging in fronds
    s.band("jacket", 0, 8, KELP)
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            for j in range(8, 8 + (i * 5) % 6):
                s.fpx("jacket", f, i, j, s.n(KELP_D, 4))
    # shell belt and a coral pendant
    s.band("jacket", 11, 12, WOOD_D)
    for (f, i) in (("front", 1), ("front", 4), ("front", 6), ("right", 2), ("left", 2), ("back", 3)):
        s.fpx("jacket", f, i, 11, SHELL)
    s.fpx("jacket", "front", 3, 5, CORAL); s.fpx("jacket", "front", 4, 5, CORAL_L); s.fpx("jacket", "front", 3, 6, CORAL)
    s.band("jacket", 18, 20, SEA)
    arms(s, KELP, SEA, SKIN_DEEP)
    legs(s, TEAL_D, WOOD_D)
    shoulder_tops(s, KELP_L, accent=SEA)
    # the mitre: brim teal, cone kelp with a brass band, tip a pale shell
    s.fill("hat1", TEAL, 4)
    x, y, w, h = s.face("hat1", "top")
    for i in range(w):
        for j in range(h):
            if i in (0, w - 1) or j in (0, h - 1):
                s.put(x + i, y + j, SEA)
    s.fill("hat2", KELP, 4)
    s.band("hat2", 3, 4, BRASS)
    s.fill("hat3", KELP_D, 4)
    s.band("hat3", 0, 1, BRASS_L)
    s.fill("hat4", SHELL, 0)
    s.fpx("hat2", "front", 3, 1, SHELL_D)
    return s


UNITS = {
    "shipwright": (shipwright, "villager"),
    "cutlass_raider": (cutlass_raider, "villager"),
    "reef_guard": (reef_guard, "villager"),
    "bombard_crew": (bombard_crew, "villager"),
    "tide_priest": (tide_priest, "witch"),
}

# ------------------------------------------------------------------ portraits (16x16 art, saved at 4x = 64x64)
BG_T, BG_B = (28, 70, 78), (10, 26, 34)   # one deep-sea dusk backdrop for the whole faction
FRAME = (150, 122, 58)                     # a brass frame

ICONS = {}


def icon(fn):
    ICONS[fn.__name__.replace("icon_", "")] = fn
    return fn


@icon
def icon_shipwright():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".....RRRRRR.....",
           "....RRRRRRRR....",
           "....RHHHHHHR..R.",
           "....HSSSSSSH.RR.",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SBSNNSBS....",
           "....SBBBBBBS....",
           ".....BBBBBB.....",
           "..TTTTTTTTTTTT..",
           ".TTTTAAAAAATTTT.",
           ".TTTTAYAAYATTTT.",
           ".TTTTAAAAAATTTT.",
           "................"],
          {"R": RED, "H": WOOD, "S": SKIN_SUN, "W": (236, 236, 228), "E": (60, 110, 120), "N": sh(SKIN_SUN, 0.88),
           "B": WOOD_L, "T": TEAL, "A": WOOD_L, "Y": BRASS_L})
    i.frame(FRAME)
    return i


@icon
def icon_gull_spotter():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           "................",
           "......WWW.......",
           ".....WWWWW......",
           ".....WEWWWOO....",
           ".....WWWWWOY....",
           "GG...WWWWW....GG",
           ".GGG.WWWWW..GGG.",
           "..GGGWWWWWGGGG..",
           "...GGGWWWGGGG...",
           ".....GWWWG......",
           "......WWW.......",
           ".......W........",
           "................",
           "................"],
          {"W": (240, 240, 236), "E": BLACK, "O": (240, 180, 40), "Y": (220, 60, 40), "G": (150, 160, 170)})
    i.frame(FRAME)
    return i


@icon
def icon_cutlass_raider():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....BBBBBBBB....",
           "..BBYYYYYYYYBB..",
           "...BBBBBBBBBB...",
           "....HSSSSSSH....",
           "....SKKSSEWS....",
           "....SKKSSSSS....",
           "....SSSNNSSS....",
           ".....SSNNSS...C.",
           ".....SSSSSS..C..",
           "..LLLLLLLLLLC...",
           ".LTTLLLLLLTCLL..",
           ".LLLRRRRRRCLLL..",
           ".TTTTTTTTTTTTT..",
           ".LLLLLLLLLLLLL..",
           "................"],
          {"B": BLACK, "Y": BRASS, "H": BLACK, "S": SKIN_BRONZE, "K": BLACK, "E": (40, 40, 40), "W": (236, 236, 228),
           "N": sh(SKIN_BRONZE, 0.88), "L": LINEN, "T": TEAL_D, "R": RED, "C": (210, 216, 222)})
    i.frame(FRAME)
    return i


@icon
def icon_reef_guard():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......CC.......",
           ".....YYCCYY.....",
           "....YYYYYYYY....",
           "...DDDDDDDDDD...",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SHSNNSHS....",
           ".....HHHHHH.....",
           "..YYYYYYYYYYYY..",
           ".YYYYCYYYYYYYYY.",
           ".YYYYYYYCYYYYYY.",
           ".TYYYYYYYYYYYYT.",
           ".TTTTTTTTTTTTTT.",
           "................"],
          {"C": CORAL, "Y": BRASS, "D": BRASS_D, "S": SKIN_SUN, "W": (236, 236, 228), "E": (50, 80, 90),
           "N": sh(SKIN_SUN, 0.88), "H": WOOD, "T": TEAL})
    i.frame(FRAME)
    return i


@icon
def icon_bombard_crew():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".....YYYYYY.....",
           "....YYYLYYYY....",
           "....TTTTTTTT....",
           "....SSSSSSSS....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....SSDNNSDS....",
           ".....SSSSSS.....",
           "..GGGGGGGGGGGG..",
           ".GGGGGGYYGGGWGG.",
           ".GGGGGWGGGGWGGG.",
           ".GGGGWGGYYWGGGG.",
           ".GGGWGGGGWGGGGG.",
           "................"],
          {"Y": BRASS, "L": BRASS_L, "T": TEAL, "S": SKIN_PALE, "W": (236, 236, 228), "E": (70, 70, 80),
           "N": sh(SKIN_PALE, 0.88), "D": sh(SKIN_PALE, 0.7), "G": SLATE})
    # the bandolier drawn over the coat in wood brown (the "W" diagonal above is re-tinted)
    for x in range(16):
        for y in range(10, 15):
            if i.px[x, y][:3] == (236, 236, 228):
                i.p(x, y, WOOD_L)
    i.frame(FRAME)
    return i


@icon
def icon_tide_priest():
    i = Icon(BG_T, BG_B)
    i.art(["......PP........",
           "......KK........",
           ".....KKKK.......",
           ".....YYYY.......",
           "....KKKKKK......",
           "..AAAAAAAAAA....",
           "....SSSSSSS.....",
           "....SWESEWS.....",
           "....SSSNSSS.....",
           ".....SSNSS......",
           "..KKKKKKKKKKK...",
           ".KKKKCKKKKKKK.P.",
           ".TTTKCCKKKTTTPP.",
           ".TTTTTTTTTTTTPP.",
           ".TTTTTTTTTTTTT..",
           "................"],
          {"P": (236, 226, 206), "K": KELP, "Y": BRASS, "A": SEA, "S": SKIN_DEEP, "W": (236, 236, 228),
           "E": (90, 210, 200), "N": sh(SKIN_DEEP, 0.88), "C": CORAL, "T": TEAL})
    i.frame(FRAME)
    return i


def preview(outdir, skins, icons):
    os.makedirs(outdir, exist_ok=True)
    SC = 6
    tiles = []
    for name, (s, kind) in skins.items():
        fm = V.front_mock(s, kind).resize((20 * SC, 36 * SC), Image.NEAREST)
        bm = V.front_mock(s, kind, back=True).resize((20 * SC, 36 * SC), Image.NEAREST)
        tiles.append((fm, bm))
    sheet = Image.new("RGBA", (len(tiles) * (40 * SC + 30), 36 * SC + 100), (90, 110, 120, 255))
    for k, (fm, bm) in enumerate(tiles):
        sheet.alpha_composite(fm, (k * (40 * SC + 30) + 4, 4))
        sheet.alpha_composite(bm, (k * (40 * SC + 30) + 20 * SC + 12, 4))
    for k, n in enumerate(icons):
        sheet.alpha_composite(icons[n].im.resize((64, 64), Image.NEAREST), (k * 72 + 4, 36 * SC + 20))
    sheet.convert("RGB").save(os.path.join(outdir, "tide_skins_sheet.png"))


def main():
    out_prev = None
    if "--preview" in sys.argv:
        out_prev = sys.argv[sys.argv.index("--preview") + 1]
    skins = {}
    for name, (fn, kind) in UNITS.items():
        s = fn()
        s.im.save(os.path.join(ENT, f"{name}_unit.png"))
        skins[name] = (s, kind)
        print(f"textures/entities/{name}_unit.png {s.im.size}")
    icons = {}
    for name, fn in ICONS.items():
        ic = fn()
        ic.save(os.path.join(HEADS, f"{name}.png"))
        icons[name] = ic
        print(f"textures/mobheads/{name}.png")
    if out_prev:
        preview(out_prev, skins, icons)
        print("preview in", out_prev)


if __name__ == "__main__":
    main()
