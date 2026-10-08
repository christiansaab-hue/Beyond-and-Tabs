"""Generates the unit skins (original pixel art) in Minecraft's 64x64 player-skin layout, one per unit in
design/units.json order, packed into mod/src/main/resources/assets/beyondtabs/textures/unit/atlas.png (16 x 8 slots).

Layers, as in Minecraft: the base layer (head, body, arms, legs) and the outer layer. In our skins the outer layer
has two jobs: the hat area holds helmets and hats (drawn as is), and the jacket / sleeve / trouser areas hold
grey-scale cloth (tabards, sashes, stripes) that the game tints with the team colour.

usage: python3 tools/gen_skins.py [--preview DIR]   (preview writes each skin as its own PNG)
"""
import json, os, sys, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "mod/src/main/resources/assets/beyondtabs/textures/unit/atlas.png")

# ------------------------------------------------------------------ layout (Minecraft player skin, 64x64)
# box origin (u, v) and size (w, h, d) -> faces: top, bottom, right, front, left, back
PARTS = {
    "head": ((0, 0), (8, 8, 8)), "hat": ((32, 0), (8, 8, 8)),
    "body": ((16, 16), (8, 12, 4)), "jacket": ((16, 32), (8, 12, 4)),
    "arm_r": ((40, 16), (4, 12, 4)), "sleeve_r": ((40, 32), (4, 12, 4)),
    "arm_l": ((32, 48), (4, 12, 4)), "sleeve_l": ((48, 48), (4, 12, 4)),
    "leg_r": ((0, 16), (4, 12, 4)), "pants_r": ((0, 32), (4, 12, 4)),
    "leg_l": ((16, 48), (4, 12, 4)), "pants_l": ((0, 48), (4, 12, 4)),
}


def faces(part):
    (u, v), (w, h, d) = PARTS[part]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


class Skin:
    def __init__(self):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); self.px = self.im.load()

    def put(self, x, y, c):
        if 0 <= x < 64 and 0 <= y < 64 and c is not None: self.px[x, y] = c if len(c) == 4 else c + (255,)

    def rect(self, x, y, w, h, c):
        for i in range(w):
            for j in range(h): self.put(x + i, y + j, c)

    def fill_part(self, part, c, noise=0, rng=None):
        for f, (x, y, w, h) in faces(part).items():
            for i in range(w):
                for j in range(h): self.put(x + i, y + j, jitter(c, noise, rng) if noise else c)

    def face(self, part, name):
        return faces(part)[name]

    def row(self, part, y0, y1, c, sides=("right", "front", "left", "back"), noise=0, rng=None):
        """Paint rows y0..y1-1 (0 = top of the side faces) round the part."""
        for s in sides:
            x, y, w, h = faces(part)[s]
            for j in range(max(0, y0), min(h, y1)):
                for i in range(w): self.put(x + i, y + j, jitter(c, noise, rng) if noise else c)


def jitter(c, n, rng):
    k = rng.randint(-n, n)
    return tuple(max(0, min(255, v + k)) for v in c[:3]) + ((c[3],) if len(c) == 4 else (255,))


def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def grey(v): return (v, v, v)


SKIN_TONES = [(244, 205, 166), (226, 180, 140), (198, 145, 105), (160, 110, 75), (120, 80, 55)]
HAIR = [(70, 45, 30), (40, 30, 25), (150, 100, 55), (210, 170, 90), (110, 70, 40), (25, 25, 30)]


def faction(key):
    p = key.split("_"); return p[1] if len(p) > 1 else ""


def base_human(s, rng, tone, hair, beard=False, bald=False, eyes=(60, 90, 160)):
    s.fill_part("head", tone, 6, rng)
    fx, fy, _, _ = s.face("head", "front")
    # eyes (white + iris), brows, nose shade, mouth
    for ex in (1, 5):
        s.put(fx + ex, fy + 4, (245, 245, 245)); s.put(fx + ex + 1, fy + 4, eyes)
    if not bald:
        for ex in (1, 5): s.rect(fx + ex, fy + 3, 2, 1, shade(hair, .9))
    s.put(fx + 3, fy + 5, shade(tone, .85)); s.put(fx + 4, fy + 5, shade(tone, .85))
    s.rect(fx + 3, fy + 6, 2, 1, (150, 80, 70))
    if not bald:
        s.row("head", 0, 2, hair, noise=8, rng=rng); s.row("head", 2, 4, hair, sides=("back",), noise=8, rng=rng)
        tx, ty, w, h = s.face("head", "top"); s.rect(tx, ty, w, h, hair)
        for x in range(8): s.put(fx + x, fy + (1 if x in (0, 7) else 0), hair)
        s.put(fx, fy + 2, hair); s.put(fx + 7, fy + 2, hair)
    if beard:
        b = shade(hair, 1.1)
        s.rect(fx + 1, fy + 6, 6, 2, b); s.rect(fx + 3, fy + 6, 2, 1, (150, 80, 70)); s.put(fx, fy + 5, b); s.put(fx + 7, fy + 5, b)
        for side in ("right", "left"):
            x, y, w, h = s.face("head", side); s.rect(x, y + 5, w, 3, b)


def clothes(s, rng, shirt, trousers, boots, sleeve=None, gloves=None, belt=(90, 60, 35)):
    s.fill_part("body", shirt, 7, rng)
    for arm in ("arm_r", "arm_l"):
        s.fill_part(arm, sleeve or shirt, 7, rng)
        s.row(arm, 7, 12, gloves or SKIN_TONES[0])
    for leg in ("leg_r", "leg_l"):
        s.fill_part(leg, trousers, 6, rng); s.row(leg, 9, 12, boots, noise=5, rng=rng)
        x, y, w, h = s.face(leg, "bottom"); s.rect(x, y, w, h, shade(boots, .7))
    s.row("body", 9, 10, belt)
    x, y, _, _ = s.face("body", "front"); s.put(x + 3, y + 9, (220, 190, 80)); s.put(x + 4, y + 9, (220, 190, 80))


def team_cloth(s, pattern):
    """Grey-scale cloth on the outer layer, tinted with the team colour in game."""
    light, mid, dark = grey(235), grey(200), grey(160)
    if pattern in ("tabard", "surcoat"):
        x, y, w, h = s.face("jacket", "front"); s.rect(x + 1, y, 6, 12 if pattern == "surcoat" else 10, light); s.rect(x + 3, y + 2, 2, 5, grey(250))
        x, y, w, h = s.face("jacket", "back"); s.rect(x + 1, y, 6, 10, mid)
    if pattern in ("sash", "tabard", "surcoat", "kilt", "cape"):
        for j in range(12):
            x, y, _, _ = s.face("jacket", "front"); s.put(x + (j * 8 // 12), y + j, dark)
    if pattern == "kilt":
        s.row("jacket", 8, 12, mid)
    if pattern == "cape":
        x, y, w, h = s.face("jacket", "back"); s.rect(x, y, w, h, mid)
    if pattern == "stripe":
        s.row("jacket", 3, 5, light); s.row("sleeve_r", 0, 3, light); s.row("sleeve_l", 0, 3, light)
    if pattern == "plates":   # sci-fi: coloured shoulder pads and a chest stripe
        x, y, _, _ = s.face("jacket", "front"); s.rect(x + 3, y + 1, 2, 7, light)
        for sl in ("sleeve_r", "sleeve_l"): s.row(sl, 0, 3, light)
        for p in ("pants_r", "pants_l"): s.row(p, 0, 2, mid)
    if pattern in ("tabard", "surcoat", "sash", "stripe", "kilt"):
        for p in ("pants_r", "pants_l"):
            x, y, w, h = s.face(p, "front"); s.rect(x + 1, y, 2, 8, mid)


# ------------------------------------------------------------------ headgear (hat layer, drawn as is)
def helmet(s, rng, c, crest=None, open_face=True, horns=None, plume=None, rim=None):
    for f, (x, y, w, h) in faces("hat").items():
        if f == "bottom": continue
        for i in range(w):
            for j in range(h):
                if f == "front" and open_face and j >= 3 and 1 <= i <= 6: continue
                if f in ("right", "left") and open_face and j >= 5: continue
                if f == "back" and j >= 6: continue
                if f != "top" and j >= 4 and f != "front": continue
                s.put(x + i, y + j, jitter(c, 8, rng))
    if rim:
        s.row("hat", 3, 4, rim)
    if crest:
        x, y, w, h = s.face("hat", "top"); s.rect(x + 3, y, 2, h, crest)
        x, y, w, h = s.face("hat", "front"); s.rect(x + 3, y, 2, 1, crest)
    if horns:
        for side in ("right", "left"):
            x, y, w, h = s.face("hat", side); s.rect(x + 3, y + 1, 2, 2, horns)
    if plume:
        x, y, w, h = s.face("hat", "top"); s.rect(x + 2, y + 2, 4, 3, plume)


def hood(s, rng, c):
    for f, (x, y, w, h) in faces("hat").items():
        if f == "bottom": continue
        for i in range(w):
            for j in range(h):
                if f == "front" and j >= 2 and 1 <= i <= 6: continue
                s.put(x + i, y + j, jitter(c, 6, rng))


def headband(s, c, feathers=None):
    s.row("hat", 2, 3, c)
    if feathers:
        x, y, w, h = s.face("hat", "back"); s.rect(x + 2, y, 1, 3, feathers[0]); s.rect(x + 5, y, 1, 2, feathers[1])


def crown(s, gold=(230, 185, 60), gem=(200, 40, 50)):
    s.row("hat", 0, 2, gold)
    for side in ("front", "back", "right", "left"):
        x, y, w, h = s.face("hat", side)
        for i in range(0, w, 2): s.put(x + i, y, (0, 0, 0, 0))
    x, y, w, h = s.face("hat", "front"); s.put(x + 3, y + 1, gem); s.put(x + 4, y + 1, gem)


def brimmed(s, rng, c, band=None):
    x, y, w, h = s.face("hat", "top"); s.rect(x, y, w, h, c)
    s.row("hat", 0, 2, c, noise=6, rng=rng)
    if band: s.row("hat", 1, 2, band)
    s.row("hat", 2, 3, shade(c, .8))


def visor_helmet(s, rng, shell, visor, crest=None):
    for f, (x, y, w, h) in faces("hat").items():
        if f == "bottom": continue
        for i in range(w):
            for j in range(h):
                if f == "front" and j >= 6: continue
                s.put(x + i, y + j, jitter(shell, 4, rng))
    x, y, w, h = s.face("hat", "front"); s.rect(x + 1, y + 3, 6, 2, visor)
    for side in ("right", "left"):
        x, y, w, h = s.face("hat", side); s.rect(x, y + 3, 2 if side == "left" else 0, 2, visor); s.put(x + 1, y + 6, shade(shell, .7))
    if crest:
        x, y, w, h = s.face("hat", "top"); s.rect(x + 3, y, 2, h, crest)


def mask(s, c, rows=(5, 8)):
    x, y, w, h = s.face("hat", "front"); s.rect(x, y + rows[0], w, rows[1] - rows[0], c)
    for side in ("right", "left", "back"):
        x, y, w, h = s.face("hat", side); s.rect(x, y + rows[0], w, rows[1] - rows[0], c)


# ------------------------------------------------------------------ armour on the base layer
def mail(s, rng, part, c=(150, 155, 165)):
    for f, (x, y, w, h) in faces(part).items():
        for i in range(w):
            for j in range(h):
                if (i + j) % 2 == 0: s.put(x + i, y + j, jitter(c, 10, rng))


def cuirass(s, c, trim):
    x, y, w, h = s.face("body", "front"); s.rect(x, y, w, 8, c); s.rect(x, y + 7, w, 1, trim); s.rect(x + 3, y + 2, 2, 3, shade(c, 1.15))
    x, y, w, h = s.face("body", "back"); s.rect(x, y, w, 8, shade(c, .9))


def fur(s, rng, part, c, rows=(0, 3)):
    s.row(part, rows[0], rows[1], c, noise=18, rng=rng)


# ------------------------------------------------------------------ per-unit recipes
def make(u):
    key, uid, role = u["tabs_key"], u["id"], u["role"]
    rng = random.Random(uid)
    s = Skin(); f = faction(key)
    tone = SKIN_TONES[rng.randrange(len(SKIN_TONES))]; hair = HAIR[rng.randrange(len(HAIR))]
    beard = rng.random() < .35
    K = key.upper()

    if f == "STARFORGE":
        mech = u["body"] == "large"
        base_human(s, rng, tone, hair)
        under, plate = (52, 58, 70), (232, 236, 240) if not mech else (200, 206, 214)
        clothes(s, rng, under, under, (40, 44, 52), gloves=(60, 66, 78), belt=(30, 34, 40))
        cuirass(s, plate, (150, 156, 166))
        for arm in ("arm_r", "arm_l"): s.row(arm, 0, 4, plate)
        for leg in ("leg_r", "leg_l"): s.row(leg, 2, 6, plate)
        visor = (255, 200, 90) if "MARSHAL" in K else (255, 90, 90) if "MEDIC" in K else (120, 230, 255)
        visor_helmet(s, rng, plate if not mech else (170, 176, 186), visor, crest=(230, 185, 60) if "MARSHAL" in K else None)
        if "MEDIC" in K:
            x, y, _, _ = s.face("body", "front"); s.rect(x + 3, y + 2, 2, 4, (210, 40, 40)); s.rect(x + 2, y + 3, 4, 2, (210, 40, 40))
        if mech:
            x, y, _, _ = s.face("body", "front"); s.rect(x + 2, y + 9, 4, 2, (110, 230, 255))
        team_cloth(s, "plates")
        return s

    if f == "FARMER":
        base_human(s, rng, tone, hair, beard)
        clothes(s, rng, (170, 140, 90), (90, 80, 60), (70, 50, 35), sleeve=(170, 140, 90))
        brimmed(s, rng, (215, 185, 110), band=(160, 60, 50)); team_cloth(s, "sash"); return s

    if f == "TRIBAL":
        base_human(s, rng, (198, 145, 105) if rng.random() < .5 else tone, hair, beard and "CHIEFTAIN" in K)
        clothes(s, rng, (150, 105, 65), (120, 85, 55), (90, 65, 40), sleeve=(198, 145, 105), gloves=(198, 145, 105), belt=(70, 50, 30))
        fur(s, rng, "body", (120, 90, 60), (0, 3))
        x, y, _, _ = s.face("body", "front"); s.rect(x + 2, y + 3, 4, 1, (230, 220, 200))   # bone necklace
        if "CHIEFTAIN" in K:
            headband(s, (190, 60, 40), feathers=[(240, 240, 230), (200, 70, 40)])
            x, y, w, h = s.face("hat", "top"); s.rect(x + 1, y + 1, 6, 2, (240, 240, 230))
        elif "BONEMAGE" in K:
            mask(s, (230, 222, 200), (0, 6)); x, y, _, _ = s.face("hat", "front"); s.rect(x + 1, y + 3, 2, 1, (20, 20, 20)); s.rect(x + 5, y + 3, 2, 1, (20, 20, 20))
        else:
            headband(s, (170, 70, 40), feathers=[(230, 220, 200), (60, 120, 70)] if rng.random() < .5 else None)
        team_cloth(s, "kilt"); return s

    if f == "VIKING":
        base_human(s, rng, (244, 205, 166), HAIR[rng.choice([2, 3, 4])], beard=True)
        clothes(s, rng, (120, 110, 100), (90, 80, 70), (80, 60, 45), belt=(70, 50, 35))
        mail(s, rng, "body")
        if "BERSERKER" in K:
            hood(s, rng, (90, 70, 50)); fur(s, rng, "body", (95, 75, 55), (0, 4))
        elif "VALKYRIE" in K:
            helmet(s, rng, (200, 205, 215), plume=(240, 240, 250)); x, y, _, _ = s.face("hat", "right"); s.rect(x, y, 3, 2, (245, 245, 250))
        elif "ARCHER" in K:
            hood(s, rng, (150, 190, 215))
        elif "JARL" in K:
            helmet(s, rng, (150, 155, 165), horns=(235, 225, 200)); crown(s) if False else None
        else:
            helmet(s, rng, (140, 145, 155), horns=(235, 225, 200) if rng.random() < .6 else None)
        team_cloth(s, "stripe"); return s

    if f == "ANCIENT":
        base_human(s, rng, tone, hair, beard)
        clothes(s, rng, (230, 220, 195), (210, 200, 175), (120, 85, 50), sleeve=(230, 220, 195), belt=(150, 110, 60))
        cuirass(s, (200, 150, 70), (150, 105, 50))
        if "ROMAN" in K: helmet(s, rng, (190, 150, 80), crest=(190, 40, 40), rim=(160, 120, 60))
        elif "SARISSA" in K: helmet(s, rng, (200, 160, 80), rim=(150, 110, 60))
        elif "ARCHER" in K: hood(s, rng, (110, 140, 80))
        elif "MINOTAUR" in K:
            s.fill_part("head", (110, 75, 50), 14, rng); x, y, _, _ = s.face("head", "front"); s.rect(x + 1, y + 4, 2, 1, (200, 40, 30)); s.rect(x + 5, y + 4, 2, 1, (200, 40, 30)); s.rect(x + 2, y + 6, 4, 2, (80, 55, 40))
            for side in ("right", "left"):
                x, y, _, _ = s.face("hat", side); s.rect(x + 2, y, 3, 2, (235, 225, 200))
        elif "ZEUS" in K:
            base_human(s, rng, (244, 205, 166), (235, 235, 240), beard=True); headband(s, (220, 185, 70))
            s.fill_part("body", (240, 238, 230), 4, rng)
        else: helmet(s, rng, (200, 155, 70), crest=(200, 50, 40), open_face=True, rim=(150, 110, 60))
        team_cloth(s, "sash" if "ZEUS" not in K else "cape"); return s

    if f == "MEDIEVAL":
        base_human(s, rng, tone, hair, beard)
        clothes(s, rng, (140, 140, 150), (100, 90, 80), (70, 55, 45), belt=(80, 55, 35))
        if "KING" in K:
            base_human(s, rng, (244, 205, 166), (150, 100, 55), beard=True); crown(s); s.fill_part("body", (140, 30, 40), 6, rng)
            x, y, _, _ = s.face("body", "front"); s.rect(x, y, 8, 2, (240, 240, 240))
            team_cloth(s, "cape"); return s
        mail(s, rng, "body"); mail(s, rng, "arm_r"); mail(s, rng, "arm_l")
        if "KNIGHT" in K:
            helmet(s, rng, (175, 180, 190), open_face=False, plume=(200, 40, 40)); x, y, _, _ = s.face("hat", "front"); s.rect(x + 1, y + 4, 6, 1, (20, 20, 25))
            team_cloth(s, "surcoat"); return s
        if "PRIEST" in K: hood(s, rng, (235, 230, 215)); s.fill_part("body", (235, 230, 215), 4, rng); team_cloth(s, "sash"); return s
        if "BANJO" in K: brimmed(s, rng, (110, 60, 120), band=(230, 190, 70)); s.fill_part("body", (170, 120, 70), 6, rng); team_cloth(s, "stripe"); return s
        if "ARCHER" in K: hood(s, rng, (80, 110, 60)); s.fill_part("body", (90, 120, 70), 6, rng); team_cloth(s, "sash"); return s
        helmet(s, rng, (160, 165, 175), rim=(120, 125, 135)); team_cloth(s, "tabard"); return s

    if f == "ASIA":
        base_human(s, rng, (240, 200, 160), (30, 25, 25), beard and "MONK" not in K, bald="MONK" in K)
        clothes(s, rng, (120, 40, 40), (50, 45, 50), (40, 35, 40), belt=(200, 160, 60))
        if "NINJA" in K:
            s.fill_part("body", (35, 35, 42), 4, rng); s.fill_part("arm_r", (35, 35, 42), 4, rng); s.fill_part("arm_l", (35, 35, 42), 4, rng)
            hood(s, rng, (35, 35, 42)); mask(s, (35, 35, 42), (5, 8)); team_cloth(s, "sash"); return s
        if "SAMURAI" in K:
            cuirass(s, (60, 60, 70), (200, 40, 40)); helmet(s, rng, (50, 50, 60), rim=(200, 40, 40))
            x, y, _, _ = s.face("hat", "front"); s.rect(x + 2, y, 4, 1, (230, 185, 60)); team_cloth(s, "stripe"); return s
        if "MONK" in K: s.fill_part("body", (230, 150, 50), 6, rng); team_cloth(s, "sash"); return s
        if "MONKEY" in K:
            s.fill_part("head", (190, 150, 100), 10, rng); x, y, _, _ = s.face("head", "front"); s.rect(x + 1, y + 3, 6, 4, (235, 200, 160))
            s.put(x + 2, y + 4, (20, 20, 20)); s.put(x + 5, y + 4, (20, 20, 20)); headband(s, (230, 185, 60)); s.fill_part("body", (200, 60, 40), 6, rng)
            team_cloth(s, "cape"); return s
        brimmed(s, rng, (210, 185, 120)); team_cloth(s, "sash"); return s

    if f == "RENAISSANCE":
        base_human(s, rng, tone, hair, beard)
        clothes(s, rng, (110, 50, 60), (60, 55, 70), (50, 40, 35), sleeve=(230, 220, 200), belt=(60, 40, 30))
        if "PAINTER" in K: brimmed(s, rng, (40, 40, 50)); x, y, _, _ = s.face("body", "front"); s.rect(x + 1, y + 6, 2, 2, (60, 120, 200)); s.rect(x + 5, y + 2, 2, 2, (220, 180, 40))
        elif "FENCER" in K: brimmed(s, rng, (40, 35, 45), band=(230, 230, 230)); x, y, w, h = s.face("hat", "right"); s.rect(x, y, 2, 3, (240, 240, 240))
        elif "HALBERD" in K or "MUSKET" in K: helmet(s, rng, (175, 180, 190), crest=(175, 180, 190), rim=(175, 180, 190)); cuirass(s, (165, 170, 180), (120, 125, 135))
        else: brimmed(s, rng, (60, 40, 30), band=(200, 160, 60))
        team_cloth(s, "stripe"); return s

    # anything else: a plain soldier
    base_human(s, rng, tone, hair, beard); clothes(s, rng, (120, 110, 100), (80, 70, 60), (60, 45, 35)); team_cloth(s, "tabard")
    return s


def main():
    units = json.load(open(os.path.join(ROOT, "design/units.json")))["rows"]
    atlas = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    prev = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    if prev: os.makedirs(prev, exist_ok=True)
    for i, u in enumerate(units):
        sk = make(u)
        atlas.paste(sk.im, ((i % 16) * 64, (i // 16) * 64))
        if prev: sk.im.save(os.path.join(prev, u["id"] + ".png"))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    atlas.save(OUT)
    print(f"{len(units)} skins -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
