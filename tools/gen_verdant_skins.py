#!/usr/bin/env python3
"""Draws the Verdant Court's unit skins and portrait icons (original pixel art, no source textures).

Every skin keeps the UV layout of the model its renderer uses:
  - VillagerUnitModel (64x64): Seedshaper, Elder Druid, Bloom Priestess, Leafblade, Hive Keeper. Its head carries an
    8x12x8 "hat" box (32,0) that is drawn whenever its pixels are opaque: the Court uses it for hoods, veils and
    crowns - a real 3D silhouette, not paint on the head.
  - vanilla SkeletonModel (64x32): Thornbow, Shade Ranger (slim archer body, 8x8x8 hat layer for the hood).
  - vanilla WitchModel (64x128): Moonwell Bearer (the pointed hat is four boxes at (0,64)..(0,95)).

Art direction (claude/design-factions.md, the Verdant Court): living wood, moss and lanterns in green and silver.
Every unit shares the bark / moss / silver palette and gets one accent of its own, a silhouette detail readable from
the RTS camera (which looks DOWN, so the top faces of hoods, crowns and shoulders carry the strongest shapes) and
strong value steps (dark bark against pale silver and skin) with only light texture noise.

usage: python3 tools/gen_verdant_skins.py [--preview DIR]
  writes bar/.../textures/entities/<unit>_unit.png and bar/.../textures/mobheads/<unit>.png (16x16 art at 4x, the
  64x64 icon size the HUD already uses); --preview also writes contact sheets (flat UV + front/back mock-ups and the
  portraits) into DIR.
"""
import os, sys, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENT = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/entities")
HEADS = os.path.join(ROOT, "bar/src/main/resources/assets/reignofnether/textures/mobheads")

# ------------------------------------------------------------------ palette
BARK_D, BARK, BARK_L = (52, 36, 26), (88, 60, 38), (126, 90, 56)
MOSS_D, MOSS, MOSS_L = (34, 62, 34), (58, 100, 48), (96, 146, 66)
LEAF, LEAF_L = (78, 142, 58), (140, 196, 84)
SILV_D, SILV, SILV_L = (112, 120, 128), (172, 180, 188), (224, 230, 234)
LINEN, LINEN_D = (214, 206, 182), (168, 158, 132)
INK = (24, 22, 20)
SKIN_PALE, SKIN_FAIR, SKIN_TAN, SKIN_DUSK = (236, 212, 184), (222, 186, 150), (188, 144, 104), (150, 132, 140)


def sh(c, f):
    """Shade a colour: f < 1 darker, f > 1 lighter (kept in range)."""
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ------------------------------------------------------------------ box layouts: (u, v, w, h, d)
VILLAGER = {"head": (0, 0, 8, 10, 8), "hat": (32, 0, 8, 12, 8), "nose": (24, 0, 2, 4, 2),
            "body": (16, 20, 8, 12, 6), "jacket": (0, 38, 8, 18, 6),
            "arm_up": (44, 22, 4, 8, 4), "arm_mid": (40, 38, 8, 4, 4), "arm": (40, 46, 4, 12, 4),
            "leg": (0, 22, 4, 12, 4)}
SKELETON = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4),
            "arm": (40, 16, 2, 12, 2), "leg": (0, 16, 2, 12, 2)}
WITCH = {"head": (0, 0, 8, 10, 8), "nose": (24, 0, 2, 4, 2), "mole": (0, 0, 1, 1, 1),
         "body": (16, 20, 8, 12, 6), "jacket": (0, 38, 8, 20, 6),
         "arm_up": (44, 22, 4, 8, 4), "arm_mid": (40, 38, 8, 4, 4), "leg": (0, 22, 4, 12, 4),
         "hat1": (0, 64, 10, 2, 10), "hat2": (0, 76, 7, 4, 7), "hat3": (0, 87, 4, 4, 4), "hat4": (0, 95, 1, 2, 1)}

SIDES = ("right", "front", "left", "back")


def faces(box):
    u, v, w, h, d = box
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


class Skin:
    def __init__(self, layout, size, seed):
        self.L = layout
        self.im = Image.new("RGBA", size, (0, 0, 0, 0))
        self.px = self.im.load()
        self.rng = random.Random(seed)

    def put(self, x, y, c):
        W, H = self.im.size
        if 0 <= x < W and 0 <= y < H:
            self.px[x, y] = (c + (255,)) if len(c) == 3 else c

    def clear(self, x, y):
        self.px[x, y] = (0, 0, 0, 0)

    def n(self, c, amt=5):
        """Light per-pixel noise: texture without clutter (RTS camera distance)."""
        k = self.rng.randint(-amt, amt)
        return tuple(max(0, min(255, v + k)) for v in c[:3])

    def fill(self, part, c, noise=5, which=None):
        for f, (x, y, w, h) in faces(self.L[part]).items():
            if which and f not in which:
                continue
            for i in range(w):
                for j in range(h):
                    self.put(x + i, y + j, self.n(c, noise))

    def face(self, part, f):
        return faces(self.L[part])[f]

    def fpx(self, part, f, i, j, c):
        """Pixel (i, j) of one face, (0, 0) top-left as seen from outside."""
        x, y, w, h = self.face(part, f)
        if 0 <= i < w and 0 <= j < h:
            self.put(x + i, y + j, c)

    def fclear(self, part, f, i, j):
        x, y, w, h = self.face(part, f)
        if 0 <= i < w and 0 <= j < h:
            self.clear(x + i, y + j)

    def band(self, part, j0, j1, c, sides=SIDES, noise=4):
        """Rows j0..j1-1 round the side faces (a belt, a hem, a cuff)."""
        for f in sides:
            x, y, w, h = self.face(part, f)
            for j in range(max(0, j0), min(h, j1)):
                for i in range(w):
                    self.put(x + i, y + j, self.n(c, noise))

    def wrap_cols(self, part):
        """Columns round the four sides in order (for diagonal straps / spirals): list of (face, i, w)."""
        out = []
        for f in SIDES:
            w = self.face(part, f)[2]
            for i in range(w):
                out.append((f, i))
        return out

    def vshade(self, part, top=1.08, bottom=0.82, sides=SIDES):
        """Darken toward the bottom of each side face: gives the volumes weight from far away."""
        for f in sides:
            x, y, w, h = self.face(part, f)
            for j in range(h):
                k = top + (bottom - top) * (j / max(1, h - 1))
                for i in range(w):
                    p = self.px[x + i, y + j]
                    if p[3]:
                        self.px[x + i, y + j] = sh(p, k) + (p[3],)

    def edge_shade(self, part, f, k=0.8):
        x, y, w, h = self.face(part, f)
        for j in range(h):
            for i in (0, w - 1):
                p = self.px[x + i, y + j]
                if p[3]:
                    self.px[x + i, y + j] = sh(p, k) + (p[3],)


# ------------------------------------------------------------------ shared pieces
def villager_face(s, skin, eyes, brow, hair=None, beard=None, nose_c=None):
    """The illager head: skin all round, brows, two-pixel eyes, hair on top/back (if any), the long nose."""
    s.fill("head", skin, 4)
    if hair:
        s.fill("head", hair, 5, which=("top", "back"))
        s.band("head", 0, 2, hair, sides=("right", "left", "front"))
        s.band("head", 2, 7, hair, sides=("right", "left"))
    # brow row 3, eyes row 4 (whites + iris), soft shadow under the eyes
    for i in (1, 2, 5, 6):
        s.fpx("head", "front", i, 3, brow)
    for i, c in ((1, (236, 236, 228)), (2, eyes), (5, eyes), (6, (236, 236, 228))):
        s.fpx("head", "front", i, 4, c)
    for i in (1, 2, 5, 6):
        s.fpx("head", "front", i, 5, sh(skin, 0.9))
    if beard:
        for j in range(7, 10):
            for i in range(8):
                if j > 7 or i in (0, 1, 6, 7):
                    s.fpx("head", "front", i, j, s.n(beard, 6))
        s.band("head", 7, 10, beard, sides=("right", "left"))
    s.fill("nose", nose_c or sh(skin, 0.94), 3)


def hood(s, part, c, inner, opening=(1, 6, 2, 9), collar=None, trim=None, point=True):
    """A hood on a hat box: solid shell, a face opening on the front, a deeper rim, a collar below the chin."""
    s.fill(part, c, 5)
    x0, x1, y0, y1 = opening
    for j in range(y0, y1 + 1):
        for i in range(x0, x1 + 1):
            s.fclear(part, "front", i, j)
    # the rim: inner shade one pixel round the opening reads as depth from afar
    for j in range(y0, y1 + 1):
        s.fpx(part, "front", x0 - 1, j, inner)
        s.fpx(part, "front", x1 + 1, j, inner)
    for i in range(x0 - 1, x1 + 2):
        s.fpx(part, "front", i, y0 - 1, inner)
    if trim:
        for i in range(8):
            s.fpx(part, "front", i, 0, trim)
        for j in range(12):
            s.fpx(part, "front", 0, j, trim)
            s.fpx(part, "front", 7, j, trim)
    _, _, w, h = s.face(part, "front")
    if collar:
        s.band(part, h - 2, h, collar)
    s.fill(part, sh(c, 0.55), 2, which=("bottom",))
    if point:   # the hood's tip falls down the back
        for j in range(2, 7):
            s.fpx(part, "back", 3, j, sh(c, 0.82))
            s.fpx(part, "back", 4, j, sh(c, 0.82))
    s.vshade(part, 1.06, 0.86)


def leaf_shape(s, part, f, ox, oy, c, vein, flip=False):
    """A 3x4 leaf (pointed tip at the top), vein down the middle."""
    shape = [".X.", "XXX", "XXX", ".V."]
    for j, row in enumerate(shape):
        for i, ch in enumerate(row):
            ii = 2 - i if flip else i
            if ch == "X":
                s.fpx(part, f, ox + ii, oy + j, s.n(c, 6))
            elif ch == "V":
                s.fpx(part, f, ox + ii, oy + j, vein)
    s.fpx(part, f, ox + 1, oy + 1, vein)
    s.fpx(part, f, ox + 1, oy + 2, vein)


def robe(s, base, hem, trim=None, sash=None, sash_c=None):
    """Villager-model body + the 18-high robe (jacket): base cloth, darker hem, optional front trim and sash."""
    s.fill("body", base, 5)
    s.fill("jacket", base, 5)
    s.band("jacket", 15, 18, hem)
    s.band("body", 10, 12, hem)
    if trim:
        for j in range(18):
            s.fpx("jacket", "front", 3, j, trim)
            s.fpx("jacket", "front", 4, j, sh(trim, 0.85))
    if sash:
        s.band("jacket", sash, sash + 1, sash_c)
        s.band("body", sash, sash + 1, sash_c)
    s.vshade("jacket", 1.05, 0.8)


def arms(s, cloth, cuff, hand, bracer=None):
    """Crossed arms (upper + middle bar) and the free arms: sleeves, cuffs, hands, optional bark bracers."""
    for part in ("arm_up", "arm"):
        if part not in s.L:
            continue
        s.fill(part, cloth, 5)
        h = s.face(part, "front")[3]
        s.band(part, h - 3, h - 2, cuff)
        s.band(part, h - 2, h, hand)
        s.fill(part, hand, 3, which=("bottom",))
        if bracer:
            s.band(part, h - 6, h - 3, bracer)
            for f in SIDES:
                s.fpx(part, f, 1, h - 5, sh(bracer, 1.25))
    # the middle bar is mostly hands and forearms (crossed)
    s.fill("arm_mid", cloth, 5)
    for i in range(3, 5):
        for j in range(4):
            s.fpx("arm_mid", "front", i, j, hand)
    s.fpx("arm_mid", "front", 2, 1, hand)
    s.fpx("arm_mid", "front", 5, 2, hand)
    if bracer:
        for i in (0, 1, 6, 7):
            for j in range(4):
                s.fpx("arm_mid", "front", i, j, s.n(bracer, 4))


def legs(s, cloth, boot):
    s.fill("leg", cloth, 5)
    h = s.face("leg", "front")[3]
    s.band("leg", h - 4, h, boot)
    s.fill("leg", sh(boot, 0.8), 2, which=("bottom",))
    s.vshade("leg", 1.0, 0.85)


def shoulder_tops(s, c, accent=None):
    """The top faces seen by the RTS camera: shoulders of the robe and arms."""
    for part in ("arm_up", "arm"):
        if part in s.L:
            s.fill(part, c, 4, which=("top",))
            if accent:
                x, y, w, h = s.face(part, "top")
                s.put(x + 1, y + 1, accent); s.put(x + 2, y + 2, accent)


# ------------------------------------------------------------------ the units (villager model)
def seedshaper():
    """The worker: a moss-green hooded smock, a seed-gold sash with pouches, bark bracers, a sprouting hood tip."""
    s = Skin(VILLAGER, (64, 64), 11)
    GOLD = (214, 170, 64)
    villager_face(s, SKIN_FAIR, (70, 130, 60), BARK_D, hair=BARK)
    robe(s, MOSS, MOSS_D, sash=8, sash_c=GOLD)
    # seed pouches hanging off the sash (front, both sides)
    for (i, j) in ((1, 9), (1, 10), (2, 9), (2, 10), (5, 9), (6, 9), (5, 10), (6, 10)):
        s.fpx("jacket", "front", i, j, s.n(BARK_L, 4))
    s.fpx("jacket", "front", 1, 9, GOLD); s.fpx("jacket", "front", 6, 9, GOLD)
    # an apron panel of linen down the front of the robe
    for j in range(11, 17):
        for i in range(2, 6):
            s.fpx("jacket", "front", i, j, s.n(LINEN, 4))
    for i in range(2, 6):
        s.fpx("jacket", "front", i, 16, LINEN_D)
    arms(s, MOSS, MOSS_D, SKIN_FAIR, bracer=BARK)
    legs(s, BARK, BARK_D)
    shoulder_tops(s, MOSS_L)
    hood(s, "hat", MOSS, MOSS_D, collar=MOSS_D)
    # a sprout growing out of the crown of the hood (top face - the RTS view)
    for (i, j, c) in ((3, 3, LEAF_L), (4, 3, LEAF_L), (3, 4, BARK_L), (4, 4, LEAF), (2, 2, LEAF_L), (5, 5, LEAF_L), (5, 2, LEAF)):
        s.fpx("hat", "top", i, j, c)
    s.fpx("hat", "top", 0, 0, GOLD); s.fpx("hat", "top", 7, 7, GOLD)
    return s


def elder_druid():
    """The T2 constructor: an old druid, white-bearded, in a deep forest robe trimmed silver, with an antler circlet
    of bone on an open silver-grey hood and a moonstone at the brow; bark bracers carved with silver runes."""
    s = Skin(VILLAGER, (64, 64), 12)
    BONE, MOON = (230, 220, 192), (150, 206, 236)
    FOREST = (30, 72, 46)
    villager_face(s, SKIN_TAN, (90, 170, 210), SILV_L, hair=SILV_L, beard=SILV_L)
    robe(s, FOREST, sh(FOREST, 0.7), trim=SILV_L)
    s.band("jacket", 6, 7, BARK_L)               # rope belt
    for j in (7, 8, 9):
        s.fpx("jacket", "front", 2, j, BARK_L)   # its knot hangs down
    s.fpx("jacket", "front", 2, 10, MOON)
    # a silver leaf embroidered on each side of the hem
    for f in ("right", "left"):
        leaf_shape(s, "jacket", f, 1, 12, SILV, SILV_D)
    arms(s, FOREST, SILV, SKIN_TAN, bracer=BARK_D)
    for part in ("arm_up", "arm"):
        h = s.face(part, "front")[3]
        s.fpx(part, "front", 1, h - 5, SILV_L); s.fpx(part, "front", 2, h - 4, SILV_L)
    legs(s, FOREST, BARK_D)
    shoulder_tops(s, sh(FOREST, 1.25), SILV_L)
    # the hood: silver-grey wool thrown back, so the face and beard show (wide opening)
    hood(s, "hat", (128, 136, 128), (80, 86, 82), opening=(1, 6, 2, 11), point=True)
    # the antler circlet: a bone band round the brow (row 1) with tines rising on every side
    s.band("hat", 1, 2, BONE)
    for f in ("right", "left"):
        for (i, j) in ((1, 0), (2, 0), (5, 0), (6, 0), (2, 1), (5, 1)):
            s.fpx("hat", f, i, j, BONE)
    s.fpx("hat", "front", 3, 1, MOON); s.fpx("hat", "front", 4, 1, MOON)
    # from above: two branching antlers across the crown of the hood
    top = ["........",
           "B..BB..B",
           ".B.BB.B.",
           "..B..B..",
           ".BB..BB.",
           "B..BB..B",
           "...BB...",
           "........"]
    for j, row in enumerate(top):
        for i, ch in enumerate(row):
            if ch == "B":
                s.fpx("hat", "top", i, j, BONE)
    return s


def bloom_priestess():
    """The T2 support healer: an azalea priestess, a linen-and-blossom robe, a crown of pink flowers on a pale veil,
    a petal-pink sash and green sleeves; the flowers make a ring on the top face."""
    s = Skin(VILLAGER, (64, 64), 13)
    PINK, PINK_D, PETAL_L = (232, 118, 168), (176, 70, 120), (250, 196, 220)
    villager_face(s, SKIN_PALE, (200, 90, 150), BARK, hair=(120, 82, 52))
    robe(s, (232, 228, 214), (194, 186, 160), sash=7, sash_c=PINK)
    # blossoms scattered down the robe (sparse: they must not read as noise)
    for (f, i, j) in (("front", 1, 11), ("front", 6, 13), ("front", 2, 15), ("left", 2, 10), ("right", 3, 14), ("back", 5, 12), ("back", 2, 9)):
        s.fpx("jacket", f, i, j, PINK)
        s.fpx("jacket", f, i + 1, j, PETAL_L)
    # a green stole falling from the shoulders down the front
    for j in range(0, 12):
        s.fpx("jacket", "front", 1, j, LEAF); s.fpx("jacket", "front", 6, j, LEAF)
    arms(s, LEAF, PINK, SKIN_PALE)
    legs(s, LINEN, BARK)
    shoulder_tops(s, LEAF_L, PINK)
    # the veil: pale, open to the face, falling to the shoulders
    hood(s, "hat", (238, 236, 226), (196, 192, 178), opening=(1, 6, 2, 9), collar=(222, 218, 204), point=False)
    # the flower crown round the brow and as a ring from above
    s.band("hat", 1, 2, LEAF)
    for f in SIDES:
        for i in range(0, 8, 2):
            s.fpx("hat", f, i, 1, PINK if (i // 2) % 2 == 0 else PETAL_L)
            s.fpx("hat", f, i, 0, PINK_D)
    for i in range(8):
        for j in range(8):
            if i in (0, 7) or j in (0, 7):
                s.fpx("hat", "top", i, j, PINK if (i + j) % 3 else LEAF)
    s.fpx("hat", "top", 3, 3, PETAL_L); s.fpx("hat", "top", 4, 4, PETAL_L)
    return s


def leafblade():
    """The raider: a lean blade-dancer in fitted bark-brown leathers with layered leaf pauldrons (on the shoulder
    tops - the RTS view), a lime-green scarf mask, and a cropped leaf crest instead of a hood."""
    s = Skin(VILLAGER, (64, 64), 14)
    LIME, LIME_D = (172, 222, 72), (110, 160, 40)
    villager_face(s, SKIN_FAIR, (130, 200, 60), BARK_D, hair=(60, 44, 30))
    # the scarf over the lower face (rows 6-9) and nose
    for j in range(6, 10):
        for i in range(8):
            s.fpx("head", "front", i, j, s.n(LIME_D, 4))
    s.band("head", 6, 10, LIME_D, sides=("right", "left", "back"))
    s.fill("nose", LIME_D, 3)
    # leathers: dark bark with a cross-strap and a silver buckle
    s.fill("body", BARK, 5)
    s.fill("jacket", BARK, 4)
    s.band("jacket", 7, 8, BARK_D)
    for j in range(7):
        s.fpx("jacket", "front", j + 1 if j < 7 else 7, j, BARK_L)
        s.fpx("body", "front", min(7, j + 1), j, BARK_L)
    s.fpx("jacket", "front", 3, 7, SILV_L); s.fpx("jacket", "front", 4, 7, SILV)
    # split skirt panels (the robe is cut into leaf tabs at the hem)
    for f in SIDES:
        x, y, w, h = s.face("jacket", f)
        for i in range(w):
            if i % 3 == 2:
                for j in range(13, 18):
                    s.fpx("jacket", f, i, j, BARK_D)
            else:
                s.fpx("jacket", f, i, 17, LEAF)
    s.vshade("jacket", 1.05, 0.8)
    arms(s, BARK, LIME, SKIN_FAIR, bracer=BARK_D)
    legs(s, BARK_D, (40, 30, 22))
    # leaf pauldrons: overlapping leaves over the shoulder tops and the upper sleeves
    for part in ("arm_up", "arm"):
        s.fill(part, LEAF, 4, which=("top",))
        x, y, w, h = s.face(part, "top")
        s.put(x + 1, y + 1, LEAF_L); s.put(x + 2, y + 2, LEAF_L); s.put(x, y + 3, MOSS_D)
        s.band(part, 0, 3, LEAF)
        for f in SIDES:
            s.fpx(part, f, 1, 0, LEAF_L); s.fpx(part, f, 2, 1, MOSS_D); s.fpx(part, f, 0, 2, MOSS_D); s.fpx(part, f, 3, 2, MOSS_D)
    # the leaf crest: no hood, just a ridge of leaves front to back over the crown (hat box only on top + upper back)
    for i in range(8):
        for j in range(8):
            if i in (3, 4):
                s.fpx("hat", "top", i, j, s.n(LIME if j % 2 else LEAF, 4))
    for j in range(0, 3):
        s.fpx("hat", "back", 3, j, LEAF); s.fpx("hat", "back", 4, j, LIME)
    for i in (3, 4):
        s.fpx("hat", "front", i, 0, LIME)
    return s


def hive_keeper():
    """The bee-summoner: a broad straw hat with a mesh veil (the hat box: brim ring on top, veil grid round the face),
    a honey-gold smock with dark bands, a wax-sealed hive pot at the belt and moss-green gloves."""
    s = Skin(VILLAGER, (64, 64), 15)
    HONEY, HONEY_D, WAX = (230, 172, 44), (170, 112, 24), (246, 220, 140)
    STRAW, STRAW_D = (214, 186, 110), (160, 132, 70)
    villager_face(s, SKIN_TAN, (150, 110, 40), BARK_D, hair=BARK_D)
    robe(s, HONEY, HONEY_D)
    for j in (3, 9, 14):   # bee-stripe bands
        s.band("jacket", j, j + 1, INK)
    s.band("body", 3, 4, INK)
    # the hive pot at the belt: a little honeycomb patch
    comb = ["WHW", "HWH", "WHW"]
    for j, row in enumerate(comb):
        for i, ch in enumerate(row):
            s.fpx("jacket", "front", 5 + i, 10 + j, WAX if ch == "W" else HONEY_D)
    arms(s, HONEY, INK, MOSS, bracer=None)
    for part in ("arm_up", "arm"):   # moss-green gloves up the forearm
        h = s.face(part, "front")[3]
        s.band(part, h - 5, h, MOSS)
    legs(s, BARK, BARK_D)
    shoulder_tops(s, sh(HONEY, 1.1))
    # the hat box: a straw crown and brim from above, then a dark mesh veil hanging round the head
    s.fill("hat", STRAW, 5, which=("top",))
    for i in range(8):
        for j in range(8):
            if i in (0, 7) or j in (0, 7):
                s.fpx("hat", "top", i, j, STRAW_D)
            elif 2 <= i <= 5 and 2 <= j <= 5:
                s.fpx("hat", "top", i, j, sh(STRAW, 1.08))
    s.fpx("hat", "top", 3, 3, HONEY); s.fpx("hat", "top", 4, 4, HONEY)
    for f in SIDES:
        x, y, w, h = s.face("hat", f)
        for i in range(w):
            s.fpx("hat", f, i, 0, STRAW_D)       # brim edge
            s.fpx("hat", f, i, 1, HONEY_D)       # hat band
            for j in range(2, h):
                if f == "front" and 1 <= i <= 6 and 2 <= j <= 9:
                    if (i + j) % 2 == 0:
                        s.fpx("hat", f, i, j, (40, 36, 30, 255))   # mesh: every other pixel, the face shows through
                else:
                    if (i + j) % 2 == 0 or j >= h - 2:
                        s.fpx("hat", f, i, j, (52, 48, 40, 255))
    s.fill("hat", (40, 36, 30), 2, which=("bottom",))
    return s


# ------------------------------------------------------------------ skeleton model (64x32)
def thornbow():
    """The skirmisher: an elf archer in a leaf-green hood with a sprig of thorn-berries, bark-brown tunic, a diagonal
    quiver strap with silver studs and berry-red fletching at the shoulder; dark leggings, soft boots."""
    s = Skin(SKELETON, (64, 32), 21)
    BERRY, BERRY_D = (192, 46, 52), (128, 26, 34)
    s.fill("head", SKIN_PALE, 4)
    for i in (1, 2, 5, 6):
        s.fpx("head", "front", i, 3, BARK)
    for i, c in ((1, (236, 236, 228)), (2, (60, 130, 70)), (5, (60, 130, 70)), (6, (236, 236, 228))):
        s.fpx("head", "front", i, 4, c)
    s.fpx("head", "front", 3, 6, sh(SKIN_PALE, 0.86)); s.fpx("head", "front", 4, 6, sh(SKIN_PALE, 0.86))
    s.fill("head", (150, 104, 60), 5, which=("top", "back"))
    # tunic and strap
    s.fill("body", BARK, 5)
    s.band("body", 7, 8, BARK_D)
    for j in range(12):
        i = 7 - (j * 7) // 11
        s.fpx("body", "front", i, j, (60, 42, 28))
        if j % 4 == 1:
            s.fpx("body", "front", i, j, SILV_L)
        bi = (j * 7) // 11
        s.fpx("body", "back", bi, j, (60, 42, 28))
    for (i, j) in ((1, 0), (2, 0), (1, 1), (6, 0)):
        s.fpx("body", "back", i, j, BERRY)        # fletching over the shoulder
    s.fill("body", MOSS, 3, which=("top",))
    s.band("body", 10, 12, MOSS_D)
    s.vshade("body", 1.06, 0.85)
    # slim arms: green sleeve, bark bracer, pale hands
    s.fill("arm", MOSS, 4)
    s.band("arm", 6, 10, BARK_D)
    s.band("arm", 10, 12, SKIN_PALE)
    s.fill("arm", LEAF, 3, which=("top",))
    s.fill("leg", (54, 46, 38), 4)
    s.band("leg", 8, 12, BARK)
    s.fill("leg", BARK_D, 2, which=("bottom",))
    # the hood: leaf-green with an open face, a thorn sprig with red berries across the top
    s.fill("hat", MOSS, 5)
    for j in range(2, 8):
        for i in range(1, 7):
            s.fclear("hat", "front", i, j)
    for j in range(2, 8):
        s.fpx("hat", "front", 0, j, MOSS_D); s.fpx("hat", "front", 7, j, MOSS_D)
    s.fill("hat", MOSS_D, 2, which=("bottom",))
    for j in range(0, 8):                       # hood seam down the back
        s.fpx("hat", "back", 3, j, MOSS_D)
    for (i, j, c) in ((1, 1, BARK_D), (2, 2, BARK_D), (3, 3, BARK_D), (4, 4, BARK_D), (5, 5, BARK_D), (6, 6, BARK_D),
                      (2, 1, BERRY), (4, 3, BERRY), (6, 5, BERRY), (3, 4, LEAF_L), (5, 6, LEAF_L), (1, 2, LEAF_L)):
        s.fpx("hat", "top", i, j, c)
    s.fpx("hat", "front", 6, 0, BERRY); s.fpx("hat", "front", 7, 1, BERRY_D)
    s.vshade("hat", 1.08, 0.86)
    return s


def shade_ranger():
    """The cloaked sniper: a deep dusk-violet cloak and peaked hood with the face lost in shadow (only two teal eyes
    glow), a silver crescent clasp at the throat and moss-grey wraps; from above, a dark point with a silver clasp."""
    s = Skin(SKELETON, (64, 32), 22)
    DUSK, DUSK_D, DUSK_L = (62, 56, 86), (36, 32, 52), (96, 88, 124)
    GLOW = (118, 236, 206)
    s.fill("head", (44, 40, 54), 3)               # the face in the hood's shadow
    s.fpx("head", "front", 2, 4, GLOW); s.fpx("head", "front", 5, 4, GLOW)
    s.fpx("head", "front", 2, 5, sh(GLOW, 0.6)); s.fpx("head", "front", 5, 5, sh(GLOW, 0.6))
    for i in range(1, 7):
        s.fpx("head", "front", i, 6, (64, 72, 64))   # a moss-grey mask over the mouth
        s.fpx("head", "front", i, 7, (56, 62, 56))
    s.fill("body", DUSK, 4)
    for j in range(12):                            # the cloak falls open down the front
        s.fpx("body", "front", 3, j, DUSK_D); s.fpx("body", "front", 4, j, (70, 78, 70))
    s.fpx("body", "front", 3, 0, SILV_L); s.fpx("body", "front", 4, 0, SILV)
    s.fpx("body", "front", 4, 1, SILV_L)
    s.band("body", 7, 8, (40, 36, 30))
    s.fill("body", DUSK_L, 3, which=("top",))
    s.vshade("body", 1.04, 0.78)
    s.fill("arm", DUSK, 4)
    s.band("arm", 7, 12, (70, 78, 70))
    s.band("arm", 11, 12, (44, 40, 54))
    s.fill("leg", (40, 38, 46), 3)
    s.band("leg", 9, 12, (30, 28, 34))
    # the peaked hood: almost closed, a slit of shadow for the face
    s.fill("hat", DUSK, 4)
    for j in range(3, 8):
        for i in range(2, 6 if j < 7 else 6):
            s.fclear("hat", "front", i, j)
    for j in range(3, 8):
        s.fpx("hat", "front", 1, j, DUSK_D); s.fpx("hat", "front", 6, j, DUSK_D)
    s.fill("hat", DUSK_D, 2, which=("bottom",))
    for i in range(8):
        for j in range(8):
            d = abs(i - 3.5) + abs(j - 3.5)
            s.fpx("hat", "top", i, j, sh(DUSK, 1.25 - d * 0.07))
    s.fpx("hat", "top", 3, 7, SILV_L); s.fpx("hat", "top", 4, 7, SILV)
    for j in range(8):
        s.fpx("hat", "back", 3, j, DUSK_D); s.fpx("hat", "back", 4, j, DUSK_L)
    s.vshade("hat", 1.06, 0.84)
    return s


# ------------------------------------------------------------------ witch model (64x128)
def moonwell_bearer():
    """The healer: a moon-silver robe over moss, a belt of three little moon lanterns, a pale crescent sash; the witch
    hat recut as a tall moss cowl with a silver brim and a moon at its tip (big readable cone from above)."""
    s = Skin(WITCH, (64, 128), 31)
    MOON, MOON_D, GLOW = (196, 222, 240), (130, 160, 190), (236, 248, 255)
    villager_face(s, SKIN_DUSK, (120, 200, 236), SILV_D, hair=SILV)
    s.fill("mole", MOON, 0)
    robe(s, (150, 164, 172), (98, 110, 120))
    # the robe's lower half is moss (water-weed) with a silver line between
    s.band("jacket", 11, 20, MOSS)
    s.band("jacket", 11, 12, SILV_L)
    s.band("jacket", 18, 20, MOSS_D)
    # lantern belt: a dark strap with three pale lanterns (front and both sides)
    s.band("jacket", 8, 9, BARK_D)
    for (f, i) in (("front", 1), ("front", 5), ("right", 2), ("left", 2)):
        s.fpx("jacket", f, i, 9, INK)
        s.fpx("jacket", f, i, 10, GLOW)
        s.fpx("jacket", f, i + 1, 10, MOON)
        s.fpx("jacket", f, i, 11, MOON_D)
    # crescent sash from shoulder to hip
    for j in range(8):
        s.fpx("jacket", "front", 1 + (j * 5) // 7, j, MOON)
    arms(s, (150, 164, 172), MOON, SKIN_DUSK)
    legs(s, MOSS_D, BARK_D)
    shoulder_tops(s, SILV_L)
    # the hat: brim silver, cone moss with a silver band, the tip a little moon
    s.fill("hat1", SILV, 4)
    x, y, w, h = s.face("hat1", "top")
    for i in range(w):
        for j in range(h):
            if i in (0, w - 1) or j in (0, h - 1):
                s.put(x + i, y + j, SILV_L)
    s.fill("hat2", MOSS, 4)
    s.band("hat2", 3, 4, SILV_L)
    s.fill("hat3", MOSS_D, 4)
    s.band("hat3", 0, 1, LEAF)
    s.fill("hat4", GLOW, 0)
    return s


UNITS = {
    # name: (painter, preview kind)
    "seedshaper": (seedshaper, "villager"),
    "elder_druid": (elder_druid, "villager"),
    "bloom_priestess": (bloom_priestess, "villager"),
    "leafblade": (leafblade, "villager"),
    "hive_keeper": (hive_keeper, "villager"),
    "thornbow": (thornbow, "skeleton"),
    "shade_ranger": (shade_ranger, "skeleton"),
    "moonwell_bearer": (moonwell_bearer, "witch"),
}


# ------------------------------------------------------------------ portraits (16x16 art, saved at 4x = 64x64)
class Icon:
    def __init__(self, bg_top, bg_bottom):
        self.im = Image.new("RGBA", (16, 16))
        self.px = self.im.load()
        for y in range(16):
            c = mix(bg_top, bg_bottom, y / 15)
            for x in range(16):
                self.px[x, y] = c + (255,)

    def p(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = (c + (255,)) if len(c) == 3 else c

    def rect(self, x, y, w, h, c):
        for i in range(w):
            for j in range(h):
                self.p(x + i, y + j, c)

    def art(self, rows, pal):
        """Paint a 16-row string map; '.' leaves the background."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch in pal:
                    self.p(x, y, pal[ch])

    def frame(self, c):
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                self.p(x, y, c)

    def save(self, path):
        self.im.resize((64, 64), Image.NEAREST).save(path)


BG_T, BG_B = (40, 64, 44), (18, 30, 22)   # one forest-dusk backdrop for the whole faction
FRAME = (92, 120, 84)

ICONS = {}


def icon(fn):
    ICONS[fn.__name__.replace("icon_", "")] = fn
    return fn


# Portraits are drawn as head-and-shoulders busts over a shared dusk backdrop with a moss frame, so the Court's
# buttons read as one family in the HUD while each silhouette (hood, crown, antlers, hat, crest) stays distinct.
# Legend letters are per icon (see each pal dict).

@icon
def icon_seedshaper():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......LL.......",
           "......LGL.......",
           ".....MMMMMM.....",
           "....MMMMMMMM....",
           "...MMDDDDDDMM...",
           "...MDSSSSSSDM...",
           "...MDWESSEWDM...",
           "...MDSSNNSSDM...",
           "...MDSSNNSSDM...",
           "...MMDSNNSDMM...",
           "..MMMMMMMMMMMM..",
           ".MMMMMMMMMMMMMM.",
           ".MMYYYYYYYYYYMM.",
           ".MMMBMMMMMMBMMM.",
           "................"],
          {"L": LEAF_L, "G": BARK_L, "M": MOSS, "D": MOSS_D, "S": SKIN_FAIR, "W": (236, 236, 228), "E": (70, 130, 60),
           "N": sh(SKIN_FAIR, 0.88), "Y": (214, 170, 64), "B": BARK_L})
    i.frame(FRAME)
    return i


@icon
def icon_elder_druid():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "..B.B......B.B..",
           "...BB......BB...",
           "....BB.MM.BB....",
           ".....BBOOBB.....",
           "....GGGGGGGG....",
           "...GSSSSSSSSG...",
           "...GHWEHHEWHG...",
           "...GSSSNNSSSG...",
           "...GWWSNNSWWG...",
           "...GWWWNNWWWG...",
           "..FFWWWWWWWWFF..",
           ".FFFFWWWWWWFFFF.",
           ".FFFFFWWWWFFFFF.",
           ".FFFFFFIIFFFFFF.",
           "................"],
          {"B": (230, 220, 192), "M": (150, 206, 236), "O": (230, 220, 192), "G": (128, 136, 128), "S": SKIN_TAN,
           "H": SILV_L, "W": SILV_L, "E": (90, 170, 210), "N": sh(SKIN_TAN, 0.88), "F": (30, 72, 46), "I": SILV_L})
    i.p(7, 3, (150, 206, 236)); i.p(8, 3, (150, 206, 236))
    i.frame(FRAME)
    return i


@icon
def icon_bloom_priestess():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "....P.Q..Q.P....",
           "...PQPLPPLPQP...",
           "...VVVVVVVVVV...",
           "...VHHHHHHHHV...",
           "...VSSSSSSSSV...",
           "...VWESSSSEWV...",
           "...VSSSNNSSSV...",
           "...VSSSNNSSSV...",
           "...VVSSNNSSVV...",
           "..VVVVSSSSVVVV..",
           ".VVLLVVVVVVLLVV.",
           ".RRLLRRRRRRLLRR.",
           ".RRLLPPPPPPLLRR.",
           ".RRLLRRQRRRLLRR.",
           "................"],
          {"P": (232, 118, 168), "Q": (250, 196, 220), "L": LEAF, "V": (238, 236, 226), "H": (120, 82, 52),
           "S": SKIN_PALE, "W": (236, 236, 228), "E": (200, 90, 150), "N": sh(SKIN_PALE, 0.88), "R": (232, 228, 214)})
    i.frame(FRAME)
    return i


@icon
def icon_leafblade():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......LK.......",
           "......LKLK......",
           ".....HHHHHH.....",
           "....HHHHHHHH....",
           "....HSSSSSSH....",
           "....SWESSEWS....",
           "....SSSSSSSS....",
           "....CCCCCCCC....",
           "....CCCKKCCC....",
           ".....CCCCCC.....",
           "..LLLBBBBBBLLL..",
           ".LKLLBBXBBBLLKL.",
           ".LLLBBBBXBBBLLL.",
           "..BBBBBBBBXBBB..",
           "................"],
          {"L": LEAF, "K": (172, 222, 72), "H": (60, 44, 30), "S": SKIN_FAIR, "W": (236, 236, 228), "E": (130, 200, 60),
           "C": (110, 160, 40), "B": BARK, "X": BARK_L})
    i.frame(FRAME)
    return i


@icon
def icon_hive_keeper():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           "................",
           ".....TTTTTT.....",
           ".....TYYYYT.....",
           "..TTTTTTTTTTTT..",
           "...DSDSDSDSDS...",
           "...SDSDSDSDSD...",
           "...DWDEDDEDWD...",
           "...SDSDSDSDSD...",
           "...DSDSDSDSDS...",
           "...SDSDSDSDSD...",
           "..HHHHHHHHHHHH..",
           ".HHKKKKKKKKKKHH.",
           ".HHHHHHHHHWOWHH.",
           ".HHKKKKKKKOWOKH.",
           "................"],
          {"T": (214, 186, 110), "Y": (170, 112, 24), "D": (52, 48, 40), "S": SKIN_TAN, "W": (236, 236, 228),
           "E": (150, 110, 40), "H": (230, 172, 44), "K": INK, "O": (246, 220, 140)})
    i.frame(FRAME)
    return i


@icon
def icon_thornbow():
    i = Icon(BG_T, BG_B)
    i.art(["................",
           ".......R........",
           "......BRB.......",
           ".....MMBMMM.....",
           "....MMMMMBMM....",
           "....MDSSSSDM....",
           "....MSWESEWM....",
           "....MSSSSSSM....",
           "....MDSSSSDM....",
           ".....MSSSSM...F.",
           "......MMMM...F..",
           "..KKKKKKKKKKFK..",
           ".KKKKKKKKKKSKKK.",
           ".KKKKKKKKKSKKKK.",
           ".KKKKKKKKSKKKKK.",
           "................"],
          {"R": (192, 46, 52), "B": BARK_D, "M": MOSS, "D": MOSS_D, "S": SKIN_PALE, "W": (236, 236, 228),
           "E": (60, 130, 70), "K": BARK, "F": (192, 46, 52)})
    i.p(11, 12, (60, 42, 28)); i.p(10, 13, (60, 42, 28)); i.p(9, 14, (60, 42, 28))
    i.p(11, 12, SILV_L)
    i.frame(FRAME)
    return i


@icon
def icon_shade_ranger():
    i = Icon((34, 32, 48), (14, 14, 22))   # its own dusk: the one Court icon on a violet night
    i.art(["................",
           ".......DD.......",
           "......DDDD......",
           ".....DDDDDD.....",
           "....DDKKKKDD....",
           "....DKKKKKKD....",
           "....DKGKKGKD....",
           "....DKKKKKKD....",
           "....DKMMMMKD....",
           "....DDMMMMDD....",
           "...DDDDSSDDDD...",
           "..DDDDDDDDDDDD..",
           ".DDDDDLMMLDDDDD.",
           ".DDDDDLMMLDDDDD.",
           ".DDDDDLMMLDDDDD.",
           "................"],
          {"D": (62, 56, 86), "K": (24, 22, 30), "G": (118, 236, 206), "M": (70, 78, 70), "S": SILV_L,
           "L": (36, 32, 52)})
    i.frame((80, 74, 110))
    return i


@icon
def icon_moonwell_bearer():
    i = Icon(BG_T, BG_B)
    i.art(["........O.......",
           ".......MM.......",
           "......MMMM......",
           "......LLLL......",
           ".....MMMMMM.....",
           "..SSSSSSSSSSSS..",
           "....HSSSSSSH....",
           "....SWESSEWS....",
           "....SSSNNSSS....",
           "....HSSNNSSH....",
           ".....SSNNSS.....",
           "..RRRRRRRRRRRR..",
           ".RRCRRRRRRRRRRR.",
           ".BBBOBBBOBBBOBB.",
           ".GGGGGGGGGGGGGG.",
           "................"],
          {"O": (236, 248, 255), "M": MOSS, "L": SILV_L, "S": SILV, "H": SILV, "W": (236, 236, 228),
           "E": (120, 200, 236), "N": sh(SKIN_DUSK, 0.9), "R": (150, 164, 172), "C": (196, 222, 240),
           "B": BARK_D, "G": MOSS})
    # face skin under the brim
    for (x, y) in ((5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (5, 8), (6, 8), (9, 8), (10, 8), (6, 9), (9, 9),
                   (6, 10), (9, 10), (5, 7), (10, 7), (7, 7), (8, 7), (5, 9), (10, 9)):
        i.p(x, y, SKIN_DUSK)
    i.p(6, 7, (236, 236, 228)); i.p(7, 7, (120, 200, 236)); i.p(8, 7, (120, 200, 236)); i.p(9, 7, (236, 236, 228))
    i.frame(FRAME)
    return i


@icon
def icon_fox_courier():
    i = Icon(BG_T, BG_B)
    O, OD, W, K = (226, 124, 48), (170, 80, 30), (244, 236, 222), INK
    i.art(["................",
           "..OO........OO..",
           "..OKO......OKO..",
           "..OOOO....OOOO..",
           "...OOOOOOOOOO...",
           "..OOOOOOOOOOOO..",
           "..OOKKOOOOKKOO..",
           "..OOWKOOOOKWOO..",
           "..WWOOOOOOOOWW..",
           "...WWWOOOOWWW...",
           "....WWWKKWWW....",
           ".....WWWWWW.....",
           "..SSSSSWWSSSSS..",
           ".SSSGSSSSSSGSSS.",
           ".SSSSSSSSSSSSSS.",
           "................"],
          {"O": O, "K": K, "W": W, "S": MOSS, "G": (214, 170, 64)})   # a moss courier's scarf with seed-gold studs
    i.frame(FRAME)
    return i


@icon
def icon_owl_watcher():
    i = Icon(BG_T, BG_B)
    G, GD, Y, K, B = (150, 146, 136), (104, 100, 92), (238, 196, 60), INK, (220, 170, 70)
    i.art(["................",
           "...GG......GG...",
           "...GDG....GDG...",
           "....GGGGGGGG....",
           "...GLLLGGLLLG...",
           "...LLYYLLYYLL...",
           "...LYKKYYKKYL...",
           "...LLYYLLYYLL...",
           "...GLLLBBLLLG...",
           "...GGGGBBGGGG...",
           "...GDGDGGDGDG...",
           "...GGDGDDGDGG...",
           "...GDGDGGDGDG...",
           "..CCCCCCCCCCCC..",
           "..CCCCCCCCCCCC..",
           "................"],
          {"G": G, "D": GD, "L": (214, 208, 196), "Y": Y, "K": K, "B": B, "C": BARK})   # perched on a bough
    i.frame(FRAME)
    return i


@icon
def icon_sentinel_treant():
    i = Icon(BG_T, BG_B)
    i.art(["...LLLLLLLLLL...",
           "..LLKLLLLLKLLL..",
           "..LLLLLKLLLLLL..",
           "...BBBBBBBBBB...",
           "...BXBBBBBBXB...",
           "...BBGGBBGGBB...",
           "...BBGGBBGGBB...",
           "...BXBBBBBBXB...",
           "...BBBXXXXBBB...",
           "...BBBBBBBBBB...",
           "..BBXBBBBBBXBB..",
           ".BBBBBMMMMBBBBB.",
           ".BBXBMMMMMMBXBB.",
           ".BBBBBMMMMBBBBB.",
           ".BBBBBBBBBBBBBB.",
           "................"],
          {"L": LEAF, "K": LEAF_L, "B": BARK, "X": BARK_D, "G": (150, 236, 110), "M": MOSS})
    i.frame(FRAME)
    return i


@icon
def icon_elder_treant():
    i = Icon(BG_T, BG_B)
    i.art(["..PLLLLLLLLLLP..",
           ".LLPLLLLLLLPLLL.",
           ".LLLLLPLLLLLLLL.",
           "..XBBBBBBBBBBX..",
           "..BXBBBBBBBBXB..",
           "..BBGGBBBBGGBB..",
           "..BBGGBBBBGGBB..",
           "..BBBBBXXBBBBB..",
           "..BBXXXXXXXXBB..",
           "..BMMBBBBBBMMB..",
           "..MMMMBBBBMMMM..",
           ".BBMMMMMMMMMMBB.",
           ".BXBBMMMMMMBBXB.",
           ".BBBXBBBBBBXBBB.",
           ".BBBBBBBBBBBBBB.",
           "................"],
          {"L": MOSS, "P": (232, 118, 168), "B": BARK_D, "X": (36, 26, 20), "G": (150, 236, 110), "M": MOSS_L})
    i.frame(FRAME)
    return i


@icon
def icon_stag_lancer():
    i = Icon(BG_T, BG_B)
    A, B, BD, W, K = (230, 220, 192), (130, 90, 56), (92, 62, 38), (232, 222, 200), INK
    i.art(["A.A........A.A..",
           ".AA.A....A.AA...",
           "..AAA....AAA....",
           "....AA..AA......",
           ".....ABBA.......",
           "....BBBBBB......",
           "...BKBBBBKB.....",
           "...BBBBBBBB.....",
           "....BBBBBB......",
           "....BBWWBB......",
           ".....BWWB.......",
           ".....BKKB.......",
           "..SSSSBBSSSS....",
           ".SSSSSSSSSSSS...",
           ".SSSSSSSSSSSS...",
           "................"],
          {"A": A, "B": B, "K": K, "W": W, "S": SILV})   # silver barding under the head
    i.frame(FRAME)
    return i


@icon
def icon_wisp_choir():
    i = Icon((26, 46, 40), (10, 20, 18))
    C, CL, CD = (130, 236, 196), (220, 255, 240), (60, 150, 120)
    i.art(["................",
           "......DCCD......",
           ".....DCLLCD.....",
           ".....CLWWLC.....",
           ".....DCLLCD.....",
           "......DCCD......",
           "................",
           "..DCCD....DCCD..",
           ".DCLLCD..DCLLCD.",
           ".CLWWLC..CLWWLC.",
           ".DCLLCD..DCLLCD.",
           "..DCCD....DCCD..",
           "................",
           "...D..D..D..D...",
           "................",
           "................"],
          {"C": C, "L": CL, "W": (255, 255, 255), "D": CD})
    i.frame(FRAME)
    return i


@icon
def icon_world_tree_walker():
    i = Icon((50, 70, 54), (18, 30, 22))
    i.art(["LLPLLLLLLLLLPLLL",
           "LLLLLKLLLLKLLLLL",
           ".LLLLLLPLLLLLLL.",
           "...BBBBBBBBBB...",
           "..BBXBBBBBBXBB..",
           "..BGGGBBBBGGGB..",
           "..BGWGBBBBGWGB..",
           "..BBBBBXXBBBBB..",
           "..BXXBXXXXBXXB..",
           "..BBBBBBBBBBBB..",
           ".MBBBBBBBBBBBBM.",
           ".MMBXBBMMBBXBMM.",
           ".BMMBBMMMMBBMMB.",
           ".BBMMMMMMMMMMBB.",
           ".BBBBBBBBBBBBBB.",
           "................"],
          {"L": LEAF, "K": LEAF_L, "P": (232, 118, 168), "B": BARK, "X": BARK_D, "G": (150, 236, 110),
           "W": (230, 255, 210), "M": MOSS})
    i.frame((140, 170, 110))   # the experimental gets a brighter frame
    return i


# ------------------------------------------------------------------ previews
def front_mock(s, kind, back=False):
    """A rough front (or back) view assembled from the side faces: enough to judge the silhouette and values."""
    f = "back" if back else "front"
    W, H = 20, 36
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def blit(part, face_name, ox, oy, flip=False):
        x, y, w, h = faces(s.L[part])[face_name]
        reg = s.im.crop((x, y, x + w, y + h))
        if flip:
            reg = reg.transpose(Image.FLIP_LEFT_RIGHT)
        im.alpha_composite(reg, (ox, oy))

    if kind == "skeleton":
        blit("leg", f, 7, 20); blit("leg", f, 11, 20)
        blit("body", f, 6, 8)
        blit("arm", f, 4, 8); blit("arm", f, 14, 8)
        blit("head", f, 6, 0); blit("hat", f, 6, 0)
    else:
        blit("leg", f, 6, 22); blit("leg", f, 10, 22)
        blit("body", f, 6, 10)
        blit("jacket", f, 6, 10)
        if back or kind == "witch" or "arm" not in s.L:
            blit("arm_up", f, 2, 11); blit("arm_up", f, 14, 11)
            if not back:
                blit("arm_mid", f, 6, 15)
        else:
            blit("arm", f, 2, 10); blit("arm", f, 14, 10)
        blit("head", f, 6, 0)
        if kind == "witch":
            blit("hat2", f, 6, -2); blit("hat1", f, 5, 0)
        elif "hat" in s.L:
            blit("hat", f, 6, 0)
    return im


def preview(outdir, skins, icons):
    os.makedirs(outdir, exist_ok=True)
    SC = 6
    # contact sheet 1: each skin flat (UV) and its front/back mock-up
    cols = []
    for name, (s, kind) in skins.items():
        flat = s.im.resize((s.im.width * SC, s.im.height * SC), Image.NEAREST)
        fm = front_mock(s, kind).resize((20 * SC, 36 * SC), Image.NEAREST)
        bm = front_mock(s, kind, back=True).resize((20 * SC, 36 * SC), Image.NEAREST)
        cols.append((name, flat, fm, bm))
    tile_w = 64 * SC + 2 * 20 * SC + 40
    rows = [cols[r:r + 4] for r in range(0, len(cols), 4)]
    row_h = [max(max(c[1].height, c[2].height) for c in row) + 10 for row in rows]
    sheet = Image.new("RGBA", (tile_w * 4, sum(row_h)), (86, 92, 86, 255))
    for k, (name, flat, fm, bm) in enumerate(cols):
        ox, oy = (k % 4) * tile_w, sum(row_h[:k // 4])
        sheet.alpha_composite(flat, (ox + 4, oy + 4))
        sheet.alpha_composite(fm, (ox + 64 * SC + 12, oy + 4))
        sheet.alpha_composite(bm, (ox + 64 * SC + 20 * SC + 24, oy + 4))
    sheet.convert("RGB").save(os.path.join(outdir, "verdant_skins_sheet.png"))
    # contact sheet 2: mock-ups only, small - roughly what reads from the RTS camera
    small = Image.new("RGBA", (len(cols) * 46, 84), (110, 140, 90, 255))
    for k, (name, flat, fm, bm) in enumerate(cols):
        s, kind = skins[name]
        small.alpha_composite(front_mock(s, kind).resize((40, 72), Image.NEAREST), (k * 46 + 3, 6))
    small.convert("RGB").save(os.path.join(outdir, "verdant_skins_small.png"))
    # contact sheet 3: the portraits at 4x and at 1x (HUD size)
    names = list(icons)
    sheet3 = Image.new("RGBA", (len(names) * 72, 100), (50, 50, 50, 255))
    for k, n in enumerate(names):
        sheet3.alpha_composite(icons[n].im.resize((64, 64), Image.NEAREST), (k * 72 + 4, 4))
        sheet3.alpha_composite(icons[n].im, (k * 72 + 28, 76))
    sheet3.convert("RGB").save(os.path.join(outdir, "verdant_portraits_sheet.png"))


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
        print("previews in", out_prev)


if __name__ == "__main__":
    main()
