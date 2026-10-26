"""Warrior skill objects after the Outer Gods reference rules (2026-10-20, Creator: 「このリファレンスで見ながら
ウォーリアーのスキル作ってみて」 → filmed in game and compared).

The reference (wolf awwent, studied at 1080p60) builds every skill from textured models that move: they rise, open,
tumble, and vanish by losing pixels. The server poses one display per object every tick
(CoreWarriorRefChoreography); this script only draws the textures and the models:

  fang_<n>      an iron fang (three tapered blocks, iron texture with a molten seam, hot tip); n = 0..2 dissolve
  flame_<s>     a tongue of flame on a two-sided board; s = 0 white-hot .. 3 dying
  plate         a dark flat plate thrown out by the blow
  crack_<s>     the crack star burnt into the ground; s = 0 white-hot .. 2 embers, 3..4 dissolving

Flat tones only, no gradients: dark rim, red, cream, white core; four irons.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
DIR = 'combat_vfx/warrior_ref'

K = (52, 8, 14)
D = (140, 16, 30)
R = (226, 40, 46)
C = (255, 214, 150)
W = (255, 250, 236)
I0, I1, I2, I3 = (22, 18, 22), (38, 32, 38), (58, 50, 58), (92, 84, 92)


def _h(*v):
    x = math.sin(sum(a * b for a, b in zip(v, (127.1, 311.7, 74.7, 19.3)))) * 43758.5453
    return x - math.floor(x)


def dissolve(img, amount, seed=0):
    """Remove a share of the pixels in a fixed ordered pattern (the reference's pixel break-up, not a fade)."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            if _h(x // 2, y // 2, seed) < amount:
                px[x, y] = (0, 0, 0, 0)
    return out


# ---------------------------------------------------------------- textures
def fang_texture():
    """16 x 16: dark iron with a lit left panel, a molten seam, hot red edges on both sides and a glowing tip."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if y == 0: c = C
            elif y <= 2: c = R if 4 <= x <= 11 else D
            elif y <= 4: c = I2 if x <= 4 else I1
            else:
                c = I3 if x <= 1 else I2 if x <= 4 else I0 if x >= 14 else I1   # lit left edge, shadowed right
                if (x * 3 + y * 5) % 11 == 0 and 2 < x < 13: c = I2                  # hammered pits
            px[x, y] = c + (255,)
    seam = [8, 8, 7, 7, 8, 9, 9, 8, 7, 7, 8]
    for i, y in enumerate(range(5, 16)):
        px[seam[i], y] = (C if y < 9 else R) + (255,)
        px[seam[i] + 1, y] = (R if y < 9 else D) + (255,)
    for y in range(16):  # hot edges: the iron glows where it was torn
        px[0, y] = R + (255,); px[15, y] = R + (255,)
    for x in range(16):  # where it tore out of the ground: molten
        px[x, 15] = (R if x % 3 else C) + (255,)
    return img


def plate_texture():
    img = Image.new('RGBA', (16, 16), I0 + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=I2 + (255,))
    d.line([(1, 1), (14, 1)], fill=I3 + (255,))
    d.line([(3, 6), (9, 9), (12, 13)], fill=I1 + (255,))
    d.point([(4, 6), (10, 10)], fill=R + (255,))
    return img


def flame_texture(stage, w=32, h=48):
    """One tongue: a ragged silhouette in insets (dark rim, red, cream, white core); later stages lose the insets."""
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, base = w / 2, h - 1

    def tongue(k, lean=0.0):
        return [(cx - w * .46 * k, base), (cx - w * .48 * k, base - h * .30 * k), (cx - w * .30 * k, base - h * .42 * k),
                (cx - w * .40 * k + lean, base - h * .66 * k), (cx - w * .14 * k + lean, base - h * .58 * k),
                (cx + lean * 1.4, base - h * k + 1), (cx + w * .12 * k + lean, base - h * .62 * k),
                (cx + w * .36 * k + lean, base - h * .78 * k), (cx + w * .30 * k, base - h * .44 * k),
                (cx + w * .48 * k, base - h * .28 * k), (cx + w * .44 * k, base)]
    if stage == 3:  # dying: three broken licks, rim colour only
        for x0, top in ((6, .45), (14, .75), (22, .5)):
            d.polygon([(x0 - 3, base), (x0, base - h * top), (x0 + 3, base)], fill=K + (255,))
            d.polygon([(x0 - 1, base), (x0, base - h * top * .7), (x0 + 1, base)], fill=D + (255,))
        return img
    tones = [(K, 1.0), (R, .84), (C, .62), (W, .38)]
    if stage == 0: tones = [(K, 1.0), (C, .86), (W, .64)]               # the flash: mostly white
    if stage == 2: tones = [(K, 1.0), (R, .80), (C, .34)]
    for tone, k in tones:
        d.polygon(tongue(k, lean=2.0 * k), fill=tone + (255,))
    return img


CRACKS = [[(32, 32), (40, 26), (44, 17), (51, 13), (55, 4)], [(32, 32), (41, 35), (49, 32), (57, 37), (63, 36)],
          [(32, 32), (36, 41), (33, 49), (39, 56), (37, 63)], [(32, 32), (24, 39), (18, 36), (12, 44), (3, 47)],
          [(32, 32), (25, 25), (27, 16), (20, 11), (17, 1)], [(32, 32), (21, 29), (11, 27), (2, 19)]]
FORKS = [[(44, 17), (48, 23)], [(49, 32), (52, 25)], [(33, 49), (28, 53)], [(18, 36), (16, 29)], [(27, 16), (33, 12)],
         [(11, 27), (8, 33)], [(57, 37), (59, 44)], [(12, 44), (13, 51)]]


# 狂戦士の甲冑 (warrior ultimate): what burns off the body while it lasts. Black flame with a red rim and a red
# heart, the same tongue as flame_texture; and red embers drifting up off it.
BLACK_FLAME = {K: (70, 10, 10), R: (24, 16, 18), C: (40, 22, 24), W: (170, 28, 22), D: (52, 12, 12)}


def black_flame_texture(stage):
    img = flame_texture(stage)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a: px[x, y] = BLACK_FLAME.get((r, g, b), (24, 16, 18)) + (a,)
    return img


def ember_texture(stage):
    """A red ember: a hot two-pixel core, a dim halo that thins out as it cools."""
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    core = [(255, 120, 60), (230, 50, 30), (150, 24, 20)][stage]
    d.rectangle((2, 2, 5, 5), fill=(120, 16, 14, (150, 100, 60)[stage]))
    d.rectangle((3, 3, 4, 4), fill=core + (255,))
    return img


# Blood, in the hand-drawn pixel style of the other skill sprites, but never a flat fill: every shape is shaded
# by height (a lit top-left, a deep bottom-right), wet highlights, a darker rim, and texture inside.
BLOOD = [(26, 2, 6), (58, 4, 10), (96, 8, 14), (140, 14, 20), (184, 26, 30), (222, 56, 50), (250, 140, 120)]


def _blood_at(level):
    return BLOOD[max(0, min(len(BLOOD) - 1, int(level)))]


def _shade_blob(img, mask, cx, cy, r, seed, wet=True):
    """Fill a mask as a domed wet blob: tone from a light over the top-left, a noisy inside, a dark rim."""
    px = img.load()
    rng = random.Random(seed)
    jitter = {(x, y): rng.random() for x in range(img.width) for y in range(img.height)}
    for (x, y) in mask:
        dx, dy = (x - cx) / max(r, 1), (y - cy) / max(r, 1)
        d = math.hypot(dx, dy)
        lit = .5 - .45 * (dx + dy) / 1.4                      # light from the top-left
        edge = min(1.0, d)
        level = 1.9 + 3.6 * lit * (1 - .4 * edge) + (jitter[x, y] - .5) * 1.1
        rim = not all((x + ox, y + oy) in mask for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if rim: level = min(level, 1.2) - .5
        px[x, y] = _blood_at(level) + (255,)
    if wet:   # a couple of hard wet glints top-left of centre
        for gx, gy in ((cx - r * .35, cy - r * .4), (cx - r * .15, cy - r * .5)):
            gx, gy = int(round(gx)), int(round(gy))
            if (gx, gy) in mask: px[gx, gy] = BLOOD[6] + (255,)


def blood_drop_texture(stage):
    """A falling drop, stretched while it falls (0, 1) and round as it lands (2)."""
    img = Image.new('RGBA', (10, 14), (0, 0, 0, 0))
    if stage < 2:
        mask = {(x, y) for x in range(10) for y in range(14)
                if (y >= 6 and math.hypot(x - 4.5, y - 9.5) <= 3.4) or (y < 7 and abs(x - 4.5) <= (y + 1) * .42)}
        _shade_blob(img, mask, 4.5, 9.0, 4, 3 + stage)
    else:
        mask = {(x, y) for x in range(10) for y in range(14) if math.hypot((x - 4.5) / 1.2, y - 9) <= 3.2}
        _shade_blob(img, mask, 4.5, 9, 4, 5)
    return img


def blood_splat_texture(stage):
    """A splash on the ground seen from above: spreads (0..2), then darkens and soaks away (3, 4)."""
    img = Image.new('RGBA', (40, 40), (0, 0, 0, 0))
    rng = random.Random(41)
    r = (6, 10, 12, 12, 12)[stage]
    blobs = [(20, 20, r)] + [(20 + math.cos(a) * r * rng.uniform(1.05, 1.5), 20 + math.sin(a) * r * rng.uniform(1.05, 1.5),
                             rng.uniform(1.2, 3.2) * r / 12) for a in (rng.uniform(0, 6.28) for _ in range(11))]
    mask = {(x, y) for x in range(40) for y in range(40)
            if any(math.hypot(x - bx, y - by) <= br + (1 if (bx, by) == (20, 20) else 0) * math.sin(math.atan2(y - by, x - bx) * 5) * .9
                   for bx, by, br in blobs)}
    _shade_blob(img, mask, 20, 20, r, 9, wet=stage < 3)
    if stage >= 3:   # drying: darker, and soaking away into specks
        px = img.load()
        for (x, y) in mask:
            c = px[x, y]
            px[x, y] = (c[0] * 6 // 10, c[1] * 6 // 10, c[2] * 6 // 10, c[3])
        img = dissolve(img, .3 if stage == 3 else .65, 13)
    return img


def blood_mist_texture(stage):
    """A puff of blood spray: a lumpy cloud, five tones, thinning out."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    rad = (7, 10, 12, 13, 14)[stage]
    for y in range(32):
        for x in range(32):
            lump = 2.5 * math.sin(math.atan2(y - 16, x - 16) * 5 + 11) + 1.5 * _h(x // 3, y // 3, 11)
            d = math.hypot(x - 16, y - 16)
            if d > rad + lump: continue
            v = _h(x // 2, y // 2, 11, 4)
            lit = .5 - ((x - 16) + (y - 16)) / (rad * 2.8)
            level = 1.5 + 3.0 * lit + (v - .5) * 1.6 - 1.5 * d / (rad + 3)
            px[x, y] = _blood_at(level) + ((200, 180, 150, 110, 70)[stage],)
    return dissolve(img, (0, 0, .15, .4, .65)[stage], 20) if stage > 1 else img


def red_halo_texture():
    """The faint light round the body: soft and dithered (no flat rings), see-through."""
    img = Image.new('RGBA', (48, 48), (0, 0, 0, 0))
    px = img.load()
    for y in range(48):
        for x in range(48):
            r = math.hypot(x - 23.5, y - 23.5) / 24
            if r >= 1: continue
            a = (1 - r) ** 1.6 * 64 + (_h(x, y, 31) - .5) * 14
            if a > 4: px[x, y] = (230, 36, 28, int(max(0, min(70, a))))
    return img


def scale_dust_texture(stage):
    """A mote of red scale-dust: a tiny diamond flake that turns as it rises, so it glints and goes dark.
    0 = flat to the eye, brightest; 1 = edge-on, a thin line; 2 = turned away, dark; 3 = turning back."""
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    w = (3, 1, 2, 2)[stage]
    for y in range(8):
        for x in range(8):
            d = abs(x - 3.5) / max(w, .6) + abs(y - 3.5) / 3.4
            if d > 1: continue
            level = (6.4, 5.6, 3.4, 4.8)[stage] - 1.6 * d + (.5 if x < 4 and y < 4 else -.3)
            px[x, y] = _blood_at(level) + (255,)
    if stage == 0:
        px[3, 2] = (255, 220, 200, 255)
    return img


# 獣の目覚め: the beast's afterimage, the warrior's own shape left behind in black smoke with a red edge, the eyes
# burning; it breaks up in steps (stage 0 whole .. 4 nearly gone).
def ghost_texture(stage):
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y in range(32):
        for x in range(24):
            edge = min(x, 23 - x, y, 31 - y)
            n = _h(x // 3, y // 3, 61, 3) * .55 + _h(x // 2, y // 2, 62) * .3 + _h(x, y, 64) * .15
            # Smoke: near-black with dark red folds, lighter where it billows; the edge only catches red in places.
            tone = n - .2 * (y / 31)
            c = (int(18 + 40 * tone), int(8 + 10 * tone), int(12 + 12 * tone))
            if tone > .62: c = (int(70 + 60 * (tone - .62)), 10, 14)
            if edge == 0 and _h(x // 2, y // 3, 65) > .45: c = (190, 28, 24) if n > .5 else (130, 16, 16)
            # The outline frays: near the edge, pixels drop out so the box shape breaks up like smoke.
            if edge <= 3 and _h(x, y // 2, 66) < (.75, .55, .35, .18)[edge]: continue
            px[x, y] = c + (255,)
    for y in range(4):            # the eye: a hot slit
        for x in range(26, 32):
            px[x, y] = ((255, 190, 150) if y == 1 and 27 <= x <= 30 else (250, 50, 36)) + (255,)
    # It is coming apart even when whole: holes stretched upward, as if the smoke were rising out of it.
    body = img.crop((0, 0, 24, 32))
    bpx = body.load()
    gone = (.22, .38, .55, .7, .85)[stage]
    for y in range(32):
        for x in range(24):
            if _h(x // 2, (y + x % 3) // 5, 67) * .7 + _h(x, y, 68) * .3 < gone: bpx[x, y] = (0, 0, 0, 0)
    img.paste(Image.new('RGBA', (24, 32), (0, 0, 0, 0)), (0, 0)); img.paste(body, (0, 0))
    if stage >= 3:
        for y in range(4):
            for x in range(26, 32): px[x, y] = (0, 0, 0, 0) if stage == 4 else (120, 14, 16, 255)
    return img


def beast_smoke_texture(stage):
    """The smoke off the beast: dark crimson-grey, lit at the top edge so it reads against the dark."""
    return dust_puff(stage, 23, ((62, 34, 40), (34, 18, 24), (118, 66, 70)))


def ghost_model(tex):
    """The warrior's standing shape (a player at .9 scale: 16 units = 1 block), every face the smoke texture."""
    k = .9
    def part(lo, hi):
        lo = [8 + (lo[0] - 8) * k, lo[1] * k, 8 + (lo[2] - 8) * k]; hi = [8 + (hi[0] - 8) * k, hi[1] * k, 8 + (hi[2] - 8) * k]
        side = [0, 0, 12, 16]
        return {'from': lo, 'to': hi, 'shade': False, 'faces': {f: face(side) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    els = [part((4, 0, 6), (8, 12, 10)), part((8, 0, 6), (12, 12, 10)), part((4, 12, 6), (12, 24, 10)),
           part((0, 12, 6), (4, 24, 10)), part((12, 12, 6), (16, 24, 10)), part((4, 24, 4), (12, 32, 12))]
    for x0 in (5.0, 9.0):   # the eyes, just proud of the face (front is +z here: the display faces the way it is turned)
        els.append({'from': [8 + (x0 - 8) * k, 28.2 * k, 8 + 4.05 * k], 'to': [8 + (x0 + 2 - 8) * k, 29.0 * k, 8 + 4.1 * k], 'shade': False,
                    'faces': {'south': face([13, 0, 16, 2]), 'north': face([13, 0, 16, 2])}})
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': els}


def crack_texture(stage):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rim, mid, core = [(K, R, W), (K, R, C), (K, D, R)][min(stage, 2)]
    for path in CRACKS: d.line(path, fill=rim + (255,), width=6)
    for path in FORKS: d.line(path, fill=rim + (255,), width=3)
    for path in CRACKS: d.line(path, fill=mid + (255,), width=3)
    for path in FORKS: d.line(path, fill=mid + (255,), width=1)
    for path in CRACKS: d.line(path[:3], fill=core + (255,), width=1)
    d.polygon([(26, 27), (31, 23), (38, 24), (41, 29), (40, 36), (34, 40), (27, 39), (23, 34)], fill=rim + (255,))
    d.polygon([(28, 28), (32, 25), (37, 26), (39, 30), (38, 35), (33, 38), (28, 37), (25, 33)], fill=mid + (255,))
    d.polygon([(30, 30), (34, 28), (36, 32), (33, 35), (29, 34)], fill=core + (255,))
    if stage >= 3: img = dissolve(img, .45 if stage == 3 else .8, 7)
    return img


Y = (255, 238, 170)    # pale yellow, between the white core and the red (the flash reads white, not beige)
O = (255, 120, 40)


def ray_texture(stage, w=24, h=48):
    """One flame ray of the flash, base at the bottom centre: mostly white, a thin yellow band, a red edge, a dark
    outline. stage 0 = full white, 1 = white core, 2 = red, 3 = broken."""
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, base = w / 2, h - 1

    def shape(k):
        return [(cx - w * .30 * k, base), (cx - w * .46 * k, base - h * .36 * k), (cx - w * .18 * k, base - h * .44 * k),
                (cx - w * .26 * k, base - h * .70 * k), (cx, base - h * k + 1), (cx + w * .22 * k, base - h * .66 * k),
                (cx + w * .14 * k, base - h * .46 * k), (cx + w * .44 * k, base - h * .40 * k), (cx + w * .30 * k, base)]
    layers = {0: [(K, 1.0), (R, .93), (Y, .86), (W, .78)], 1: [(K, 1.0), (R, .9), (Y, .76), (W, .56)],
              2: [(K, 1.0), (R, .86), (O, .5)], 3: [(K, .8), (D, .6)]}[stage]
    for tone, k in layers:
        d.polygon(shape(k), fill=tone + (255,))
    if stage == 3: img = dissolve(img, .45, 2)
    return img


def ring_texture(stage):
    """The ring of hot pixels left round the blow: chunky red and orange blocks that break up and drift apart."""
    img = Image.new('RGBA', (48, 48), (0, 0, 0, 0))
    px = img.load()
    c = 23.5
    r0 = 14 + stage * 2.5
    for y in range(0, 48, 2):
        for x in range(0, 48, 2):
            dd = math.hypot(x + 1 - c, y + 1 - c)
            band = abs(dd - r0) + 2.2 * _h(x // 2, y // 2, 4)
            if band < 3.4:
                col = (O if _h(x, y, 1) > .55 else R) if stage < 2 else (R if _h(x, y, 1) > .5 else D)
                if band > 2.6: col = K
                for dy in (0, 1):
                    for dx in (0, 1): px[x + dx, y + dy] = col + (255,)
    return dissolve(img, (0, .2, .45, .7)[stage], 11) if stage else img


def twinkle_texture(stage):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    arm = (7, 6, 4)[stage]
    d.polygon([(8, 8 - arm), (9, 7), (8 + arm, 8), (9, 9), (8, 8 + arm), (7, 9), (8 - arm, 8), (7, 7)], fill=R + (255,))
    a2 = max(1, arm - 2)
    d.polygon([(8, 8 - a2), (8.6, 7.4), (8 + a2, 8), (8.6, 8.6), (8, 8 + a2), (7.4, 8.6), (8 - a2, 8), (7.4, 7.4)], fill=W + (255,))
    return img


def frame_texture():
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((1, 1, 30, 30), outline=W + (255,), width=2)
    return img


def bar_texture():
    img = Image.new('RGBA', (16, 4), I0 + (255,))
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (15, 0)], fill=I2 + (255,))
    return img


# ---------------------------------------------------------------- the greatsword's cut (2026-10-20, Creator:
# 「閃光を出す表現自体が間違い。リファレンスは銃、こっちは剣」). The reference's blade skills (Jawbreaker, the
# first-person cut) draw the blade's path as one thick crescent band with streaks along its length; that is what a
# sword borrows, not the gun's hit flash. 地割り's band: from over the shoulder down into the ground ahead.
# Board: 64 x 64 texels = 5 blocks; the shoulder pivot is at texel (16, 34); x runs forward, y up (texel y down).
SLASH_HEAD = [112, 64, 14, -34, -34, -34, -34, -34]       # where the blade is, degrees above the forward horizon
SLASH_TAIL = [150, 136, 112, 80, 40, 0, -20, -30]          # where the band's tail has burnt back to
SLASH_FADE = [0, 0, 0, 0, .25, .5, .7, .85]


# 百刃 (2026-10-27): the same crescent in white steel, so its flurry reads apart from 刻み打ち's red one.
STEEL = {W: (255, 255, 255), C: (226, 236, 244), R: (156, 172, 190), O: (196, 208, 222), D: (74, 86, 104)}


def slash_texture(frame, palette=None):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    cx, cy, radius = 16, 34, 33.0
    head, tail = SLASH_HEAD[frame], SLASH_TAIL[frame]
    for y in range(64):
        for x in range(64):
            dx, dy = x + .5 - cx, cy - (y + .5)
            r = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx))
            if not head <= a <= tail: continue
            k = (a - head) / max(tail - head, 1)           # 0 at the blade, 1 at the burnt tail
            width = 22.0 * (1 - k) ** .6 + 3.0               # thick behind the blade, thin at the tail
            inset = radius - r                              # 0 at the edge, grows inward
            if not -.5 <= inset <= width: continue
            # Streaks along the band: each ring of radius has its own tone, broken in steps along the angle.
            band = int(inset)
            streak = _h(band, int(a / 7)) < .22
            if inset < 1.5: c, al = W, 255
            elif inset < 3.0: c, al = (C if not streak else W), 236
            elif inset < width * .7: c, al = ((R if band % 3 else O) if not streak else C), 190
            elif inset < width * .9: c, al = (D if not streak else R), 150
            else: c, al = D, 96
            al = int(al * (1 - .55 * k))                   # the tail is see-through
            if palette: c = palette.get(c, c)
            px[x, y] = c + (al,)
    if SLASH_FADE[frame]: img = dissolve(img, SLASH_FADE[frame], 21)
    return img


# ---------------------------------------------------------------- 地割り, rebuilt (2026-10-20, Creator: 「地割れ部分が
# ダサい。縁の縞模様は意味がわからない、何を描きたいかわからない」). Only what the name says: one fissure runs forward
# from the blow and the ground on both sides lifts. Stone slabs (vanilla stone, so they belong to the world) whose
# underside glows from the heat below; the fissure is a single jagged line, bright at its heart, cooling and breaking up.
FISSURE = [(8, 95), (9, 90), (7, 85), (8, 80), (10, 75), (9, 70), (7, 65), (6, 60), (8, 55), (10, 50), (11, 45), (9, 40),
           (8, 35), (6, 30), (7, 25), (9, 20), (10, 15), (9, 10), (8, 5), (8, 0)]
BRANCHES = [[(8, 80), (4, 76), (2, 72)], [(9, 70), (13, 66)], [(10, 50), (14, 47), (15, 43)], [(6, 30), (2, 27)],
            [(10, 15), (13, 12)]]


def fissure_texture(stage):
    """16 x 96 (x across, y along: y=95 at the blow, 0 far ahead). stage 0..2 = the crack running out (a third each),
    3 = hot, 4 = cooling, 5..6 = breaking up."""
    img = Image.new('RGBA', (16, 96), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    reach = {0: 64, 1: 32, 2: 0}.get(stage, 0)
    path = [p for p in FISSURE if p[1] >= reach]
    branches = [b for b in BRANCHES if b[0][1] >= reach]
    rim, mid, core = [(R, O, Y), (R, O, Y), (R, O, Y), (R, O, Y), (D, R, O), (D, R, O), (D, D, R)][stage]
    for tone, wd, alpha in ((rim, 5, 120), (mid, 3, 210), (core, 1, 255)):
        d.line(path, fill=tone + (alpha,), width=wd)
        for b in branches: d.line(b, fill=tone + (alpha,), width=max(1, wd - 2))
    if stage >= 5: img = dissolve(img, .35 if stage == 5 else .7, 31)
    return img


def slab_underside(stage):
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    tones = [(O, R), (R, D), (D, (60, 20, 22))][stage]
    for y in range(16):
        for x in range(16):
            px[x, y] = tones[0 if _h(x // 2, y // 2, 5) > .45 else 1] + (255,)
    return img


STONE = ((112, 108, 106), (128, 124, 120), (96, 92, 92), (140, 136, 132))


def stone_texture(seed):
    """Plain grey stone, chunky 2-pixel speckle, a lit top edge: reads as ground rock torn up."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = STONE[int(_h(x // 2, y // 2, seed) * 4)] + (255,)
    return img


def slab_model(under, stone, side_tex):
    # 12 x 3 x 16 units of stone, centred on the model origin; the underside glows.
    side = {'texture': '#side', 'uv': [0, 0, 16, 3]}
    return {'ambientocclusion': False, 'textures': {'top': stone, 'side': side_tex, 'under': under,
                                                     'particle': stone}, 'elements': [
        {'from': [2, 6.5, 0], 'to': [14, 9.5, 16], 'faces': {
            'up': {'texture': '#top', 'uv': [2, 0, 14, 16]}, 'down': {'texture': '#under', 'uv': [2, 0, 14, 16]},
            'north': side, 'south': side, 'east': {'texture': '#side', 'uv': [0, 0, 16, 3]},
            'west': {'texture': '#side', 'uv': [0, 0, 16, 3]}}}]}


# ---------------------------------------------------------------- 地割り after Braum's Glacial Fissure (2026-10-20,
# Creator: 「縦に割れるノックアップ技として好き。ブラウムのR、レルムブレイカーみたいに作画を寄せて派手に」).
# Studied frame by frame: a tall fan of shards bursts at the blow, a wave of shard clusters runs forward along the line
# in two rows with light streaks shooting up at its front, then the rows stand with a lit outline and sink.
def rock_shard_texture():
    """16 x 16 rock with a molten vein: lit left, shadowed right, the vein hot near the tip (top rows)."""
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = STONE[int(_h(x // 2, y // 2, 9) * 4)]
            if x <= 2: c = STONE[3]
            if x >= 13: c = STONE[2]
            px[x, y] = c + (255,)
    vein = [9, 8, 8, 7, 7, 8, 9, 9, 8, 7, 6, 6, 7, 8, 8, 9]
    for y, x in enumerate(vein):
        px[x, y] = (Y if y < 4 else O if y < 9 else R) + (255,)
        px[x + 1, y] = (O if y < 4 else R if y < 9 else D) + (255,)
    for x in range(16): px[x, 0] = O + (255,)
    return img


def ray_streak_texture():
    """A light streak shooting up: white at its head, thinning and going see-through toward the tail."""
    img = Image.new('RGBA', (8, 48), (0, 0, 0, 0))
    px = img.load()
    for y in range(48):
        k = y / 47                       # 0 = head (top), 1 = tail
        half = 3.2 * (1 - k) + .6
        for x in range(8):
            d = abs(x + .5 - 4)
            if d <= half:
                tone = W if d < half * .4 and k < .5 else Y if k < .45 else O
                px[x, y] = tone + (int(255 * (1 - k) ** .7),)
    return img


def field_texture():
    """The fissure's area: a faint red floor with a lit rim, like the reference's outline of the lingering field."""
    img = Image.new('RGBA', (16, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 63), fill=R + (26,))
    d.rectangle((0, 0, 15, 63), outline=O + (170,), width=1)
    d.rectangle((1, 1, 14, 62), outline=R + (90,), width=1)
    return img


# ---------------------------------------------------------------- 地割り objects drawn like the reference's (2026-10-20,
# read at full size from Gorebound Devourer's Maw and Gorging Rush): broad flat slabs, not spikes; a block-texture style
# of one base tone with 2-4 pixel clusters of two other tones; plain darker sides; the game's face shading does the
# light (shade on), and only the heat (the fissure, the slab's torn edge) glows.
GROUND_TOP = ((150, 142, 132), (126, 118, 110), (174, 166, 156))      # base, dark spots, light spots
GROUND_SIDE = ((112, 104, 98), (94, 88, 84))
HOT = ((236, 84, 36), (255, 170, 70), (178, 40, 30))


def clustered(base, spots, seed, size=16, density=.16):
    """A block-style texture: the base tone with clusters (2-4 px) of each spot tone."""
    img = Image.new('RGBA', (size, size), base + (255,))
    px = img.load()
    for n, tone in enumerate(spots):
        for k in range(int(size * size * density / 3)):
            x, y = int(_h(k, n, seed) * size), int(_h(k, n, seed, 1) * size)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1))[: 2 + int(_h(k, n, seed, 2) * 3)]:
                px[(x + dx) % size, (y + dy) % size] = tone + (255,)
    return img


def slab_textures(seed):
    top = clustered(GROUND_TOP[0], GROUND_TOP[1:], seed, density=.09)
    side = clustered(GROUND_SIDE[0], GROUND_SIDE[1:], seed + 7, density=.06)
    hot = clustered(HOT[0], HOT[1:], seed + 3, density=.25)
    return top, side, hot


def ground_slab_model(top, side, hot):
    """A broad slab of ground (14 x 3 x 12 units) standing on its torn edge at y=8; that edge (north) glows."""
    def f(t, uv): return {'texture': t, 'uv': uv}
    return {'textures': {'top': top, 'side': side, 'hot': hot, 'particle': top}, 'elements': [
        {'from': [1, 8, 6.5], 'to': [15, 20, 9.5], 'faces': {
            'north': f('#top', [1, 0, 15, 12]), 'south': f('#side', [1, 0, 15, 12]),
            'east': f('#side', [6, 0, 9, 12]), 'west': f('#side', [6, 0, 9, 12]),
            'up': f('#side', [1, 6, 15, 9]), 'down': f('#hot', [1, 6, 15, 9])}},
        # the hot torn edge along the bottom of the face
        {'from': [1, 8, 6.4], 'to': [15, 9, 6.5], 'shade': False, 'faces': {'north': f('#hot', [0, 0, 16, 2])}}]}


def chunk_model(tex):
    return {'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [5, 5, 5], 'to': [11, 11, 11], 'faces': {k: {'texture': '#0', 'uv': [0, 0, 6, 6]}
                                                           for k in ('north', 'south', 'east', 'west', 'up', 'down')}}]}


# ---------------------------------------------------------------- petals drawn the Outer Gods way (2026-10-20, Creator:
# 「スキルはなんでもいい、作画を合わせてほしいだけ」). Read from the Maw crown at full size: thick flat petals with a
# cut-out notched tip; the body one tone, the tip another, the change made by scattered 1-2 px clusters (no gradient);
# a darker back; the game's face shading does the light. Warrior colours: rusted iron body, heated cream tip.
PETAL_BODY = ((150, 62, 50), (128, 50, 42), (168, 76, 60))
PETAL_TIP = ((238, 206, 168), (220, 180, 144), (250, 228, 196))
PETAL_BACK = ((96, 40, 36), (82, 34, 32))


def petal_textures(seed):
    w, h = 16, 32
    front = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    back = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    fp, bp = front.load(), back.load()
    for y in range(h):
        k = y / (h - 1)                                   # 0 = tip (top) .. 1 = base
        half = 7.5 * min(1.0, .45 + k * 1.6)              # tapered toward the tip
        notch = (y < 6) and int(_h(y // 2, seed) * 3) == 0  # notched, broken tip
        for x in range(w):
            if abs(x + .5 - 8) > half - (2 if notch else 0): continue
            tipness = 1 - k * 3.6                         # >0 only in the top third
            cl = _h(x // 2, y // 2, seed)                 # 2 px clusters
            if tipness > .45 or (tipness > -.1 and cl < tipness + .35):
                c = PETAL_TIP[0 if cl > .3 else 1 if cl > .12 else 2]
            else:
                c = PETAL_BODY[0 if cl > .28 else 1 if cl > .1 else 2]
            fp[x, y] = c + (255,)
            bp[x, y] = PETAL_BACK[0 if _h(x // 2, y // 2, seed, 3) > .25 else 1] + (255,)
    return front, back


def petal_model(front, back):
    """A thick flat petal on y=8, 16 wide and 32 tall (2 blocks at scale 1), the cut-out shape on both faces."""
    def f(t, uv): return {'texture': t, 'uv': uv}
    return {'textures': {'front': front, 'back': back, 'particle': front}, 'elements': [
        {'from': [0, 8, 7.5], 'to': [16, 40, 8.5], 'faces': {
            'north': f('#front', [0, 0, 16, 16]), 'south': f('#back', [16, 0, 0, 16])}}]}


# ---------------------------------------------------------------- 地割り, third pass (2026-10-20, Creator: 「亀裂の書き方も
# 悪いし3dの岩の出てくる感じもひどい、全部直そう」). Both are now Minecraft ground: the crack is placed pixel by
# pixel at block resolution (16 px a block) as a black gap of uneven width with chipped light edges and a thin heat
# line deep inside; the rocks are lumps of grass-topped dirt built from 2-3 boxes, heaved up and tilted away from it.
GRASS = ((92, 140, 52), (78, 122, 44), (108, 158, 62), (70, 108, 40))
DIRT = ((134, 96, 66), (116, 82, 56), (150, 110, 78), (98, 70, 48))
GAP = (22, 16, 14)
CHIP = (176, 140, 104)


def block_tex(tones, seed, size=16):
    """Vanilla-like: a base tone with single pixels and 2-px clusters of three other tones."""
    img = Image.new('RGBA', (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            v = _h(x, y, seed)
            c = tones[0] if v < .45 else tones[1] if v < .7 else tones[2] if v < .88 else tones[3]
            px[x, y] = c + (255,)
    return img


def grass_side(seed):
    img = block_tex(DIRT, seed)
    px = img.load()
    for x in range(16):  # the grass overhang, ragged
        for y in range(2 + int(_h(x, seed, 5) * 3)):
            px[x, y] = GRASS[int(_h(x, y, seed) * 3)] + (255,)
    return img


def crack_pixels(stage, length=112, width=24, seed=17):
    """A crack placed pixel by pixel: a centre line that wanders, a gap 1-3 px wide around it, chipped light edges on
    both sides, and the deep heat line (stage 0 bright .. 2 dull, 3 none). Short side cracks branch off."""
    img = Image.new('RGBA', (width, length), (0, 0, 0, 0))
    px = img.load()
    def put(x, y, c):
        if 0 <= x < width and 0 <= y < length: px[x, y] = c + (255,)
    heat = [(255, 196, 96), (236, 84, 36), (150, 40, 30), None][stage]
    x = width // 2
    for y in range(length - 1, -1, -1):
        if _h(y, seed) < .3: x += 1 if _h(y, seed, 1) > .5 else -1
        x = max(4, min(width - 5, x))
        w = 1 + int(_h(y // 3, seed, 2) * 3) * (1 if y > 10 else 0)       # thins out at the far end
        for dx in range(-(w // 2), w - w // 2): put(x + dx, y, GAP)
        if _h(y, seed, 3) > .35: put(x - w // 2 - 1, y, CHIP)              # chipped edges, broken
        if _h(y, seed, 4) > .55: put(x + w - w // 2, y, DIRT[3])
        if heat and w >= 2 and _h(y, seed, 6) > .25: put(x, y, heat)
        if _h(y, seed, 7) > .93:                                           # a short side crack
            d = 1 if _h(y, seed, 8) > .5 else -1
            for k in range(1, 4 + int(_h(y, seed, 9) * 4)): put(x + d * k, y - k // 2, GAP)
    return img


STONE_T = ((124, 124, 124), (108, 108, 110), (140, 140, 140), (92, 92, 94))
WALL_LIT, WALL_DARK, VOID = (112, 104, 100), (54, 48, 48), (18, 14, 14)


def stone_tex(seed):
    """Vanilla-stone-like grey, with one darker crack streak across it."""
    img = block_tex(STONE_T, seed)
    px = img.load()
    x, y = int(_h(seed, 1) * 6) + 2, 0
    while y < 16:
        px[x % 16, y] = STONE_T[3] + (255,)
        if _h(seed, y, 2) > .5: x += 1
        y += 1
    return img


FINGER = ((22, 9), (18, 8), (14, 7))      # segment length and width in model units (16 = 1 block)


def chasm_pixels(stage, length=112, width=24, seed=23):
    """The crack as an open chasm seen from above, pixel by pixel: a jagged opening 4-10 px wide whose sides step in
    zig-zags; the lit wall on the left, the shadowed wall on the right, a black depth, and the magma at the bottom
    (stage 0 white-hot .. 2 dull, 3 cooled); a dark rim and loose light chips along the lip."""
    img = Image.new('RGBA', (width, length), (0, 0, 0, 0))
    px = img.load()
    magma = [((236, 254, 252), (176, 240, 236)), ((176, 240, 236), (112, 206, 214)), ((88, 160, 172), (60, 120, 134)),
             ((40, 70, 80), (30, 50, 60))][stage]
    cx, dirn, half = width / 2, 1, 4
    for y in range(length - 1, -1, -1):
        if y % 7 == 0: dirn = 1 if _h(y, seed) > .5 else -1
        cx = max(7, min(width - 8, cx + dirn * .6))
        if y % 4 == 0: half = 2 + int(_h(y, seed, 1) * 3)
        far = y / length                       # the far end (y small) narrows
        h = max(1, round(half * (.45 + .55 * far)))
        left, right = int(cx) - h, int(cx) + h
        for x in range(left - 1, right + 2):
            if not 0 <= x < width: continue
            if x == left - 1 or x == right + 1: c = (40, 34, 34)                      # the dark lip
            elif x - left < 2 and h > 2: c = WALL_LIT
            elif right - x < 2 and h > 2: c = WALL_DARK
            elif abs(x - cx) <= max(.6, h * .35): c = magma[0] if _h(x, y, seed, 3) > .3 else magma[1]
            else: c = VOID
            px[x, y] = c + (255,)
        for side, x in ((-1, left - 2), (1, right + 2)):                            # loose chips on the lip
            if 0 <= x < width and _h(y, seed, 4 + side) > .72: px[x, y] = STONE_T[2] + (255,)
    return img


# ---------------------------------------------------------------- the Chained Colossus (2026-10-20, Creator agreed: the
# warrior as the third Outer Gods class, an iron giant chained under the earth whose body parts break out as skills).
# 地割り: the ground splits and the colossus's fingers rise in a row, knock up, close and drag back down.
RUST = ((122, 98, 90), (106, 84, 78), (140, 114, 102), (156, 92, 66))   # iron base, shadow, light, rust
BONE = ((228, 214, 188), (204, 188, 162), (244, 234, 214))
CHAIN = ((74, 70, 78), (96, 92, 100), (52, 50, 56))
GLOW = ((176, 240, 236), (112, 206, 214), (230, 252, 250))


def iron_plate(seed):
    """Rusted iron in the reference's way: one tone with 2 px clusters; rivets in the corners; rust bleeding down."""
    img = Image.new('RGBA', (16, 16), RUST[0] + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = _h(x // 2, y // 2, seed)
            if v > .82: px[x, y] = RUST[2] + (255,)
            elif v < .14: px[x, y] = RUST[1] + (255,)
    for k in range(3):                              # rust runs from the top
        x = int(_h(k, seed, 5) * 14) + 1
        for y in range(int(3 + _h(k, seed, 6) * 8)): px[x, y] = RUST[3] + (255,)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        px[x, y] = RUST[2] + (255,); px[x + 1, y + 1] = RUST[1] + (255,)
    return img


def chain_band(seed):
    """A band of chain wrapped round a joint: links drawn as 4 px ovals, the gaps leaking the cold glow."""
    img = Image.new('RGBA', (16, 16), GLOW[1] + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            lx, ly = x % 6, y % 8
            ring = (lx in (0, 4) and 1 <= ly <= 6) or (ly in (1, 6) and 1 <= lx <= 3)
            if ring: px[x, y] = CHAIN[1] + (255,)
            elif (x // 6 + y // 8) % 2 == 0 and 1 <= lx <= 3 and 2 <= ly <= 5: px[x, y] = CHAIN[2] + (255,)
            elif _h(x, y, seed) > .7: px[x, y] = GLOW[0] + (255,)
    return img


def bone_tex(seed):
    img = Image.new('RGBA', (16, 16), BONE[0] + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = _h(x // 2, y // 2, seed)
            if v > .8: px[x, y] = BONE[2] + (255,)
            elif v < .2: px[x, y] = BONE[1] + (255,)
    return img


def finger_segment(iron, chain, bone, length, width, nail):
    """One finger segment standing on y=8 (its joint): an iron box, a chain band round its base, a bone nail on top."""
    def f(t, uv): return {'texture': t, 'uv': uv}
    w, c = width / 2, 8
    def box(lo, hi, side, top):
        return {'from': lo, 'to': hi, 'faces': {k: f(side, [0, 0, 16, 16]) for k in ('north', 'south', 'east', 'west')}
                | {'up': f(top, [0, 0, 16, 16]), 'down': f(top, [0, 0, 16, 16])}}
    els = [box([c - w, 8, c - w], [c + w, 8 + length, c + w], '#iron', '#iron'),
           box([c - w - .6, 8, c - w - .6], [c + w + .6, 11, c + w + .6], '#chain', '#chain')]
    if nail:
        els.append(box([c - w * .8, 8 + length - 1, c - w - .4], [c + w * .8, 8 + length + 4, c - w + 2.4], '#bone', '#bone'))
    return {'textures': {'iron': iron, 'chain': chain, 'bone': bone, 'particle': iron}, 'elements': els}


# ---------------------------------------------------------------- 地割り, the Guts mood (2026-10-20, Creator: 「ベルセルクの
# ガッツみたいな雰囲気」). No light, no summoning: the weight alone. A crater caved in where the slab lands, a dark crack
# running on, clods of earth thrown up and tumbling, dust rolling out, and the swing leaving a dark band of wind.
EARTH = ((112, 92, 74), (96, 78, 62), (128, 108, 88), (78, 62, 50))
SHADOW = (46, 38, 34)
DUST = ((150, 138, 124), (128, 116, 104), (172, 160, 146))


def crater_pixels(stage):
    """48 x 48 seen from above: a raised broken rim, the floor sunk into shadow, black cracks out from the centre."""
    img = Image.new('RGBA', (48, 48), (0, 0, 0, 0))
    px = img.load()
    c = 23.5
    for y in range(48):
        for x in range(48):
            d = math.hypot(x - c, y - c) + 2.2 * (_h(x // 2, y // 2, 3) - .5)
            v = _h(x // 2, y // 2, 7)
            if d > 22: continue
            if d > 18: col = EARTH[2] if v > .45 else EARTH[0]                 # the thrown-up rim
            elif d > 15: col = EARTH[1] if v > .3 else EARTH[3]
            elif d > 8: col = EARTH[3] if v > .35 else SHADOW                   # the sunken floor
            else: col = SHADOW if v > .25 else (30, 24, 22)
            px[x, y] = col + (255,)
    d = ImageDraw.Draw(img)
    for k in range(7):                                                         # black cracks from the centre
        a = k / 7 * math.tau + .4
        pts, x, y = [(c, c)], c, c
        for s_ in range(6):
            a += (_h(k, s_, 9) - .5) * .7
            x += math.cos(a) * 3.6; y += math.sin(a) * 3.6; pts.append((x, y))
        d.line(pts, fill=(20, 16, 14, 255), width=2 if k % 2 else 1)
    if stage: img = dissolve(img, (0, .35, .7)[stage], 13)
    return img


def dark_crack(stage, length=112, width=24, seed=23):
    """The crack with no light: a jagged black gap, a lit lip on one side and loose earth along it."""
    img = chasm_pixels(3, length, width, seed)
    px = img.load()
    for y in range(length):
        for x in range(width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) not in (WALL_LIT, WALL_DARK, STONE_T[2]): px[x, y] = (22, 18, 16, 255)
            elif a and (r, g, b) == WALL_LIT: px[x, y] = EARTH[2] + (255,)
            elif a and (r, g, b) == WALL_DARK: px[x, y] = EARTH[3] + (255,)
    if stage: img = dissolve(img, (0, .4, .75)[stage], 17)
    return img


def furrow_pixels(stage, w=32, n=128, seed=41):
    """The ground torn forward (2026-10-20, Creator: 「丸に割れてるだけ、前に伸びていくスキルなんだから」): a gouged
    furrow, widest and deepest where the slab lands (row n-1) and narrowing to a crack far ahead (row 0); raised broken
    lips on both sides, chevron cracks splitting forward off them, a black crack down the middle.
    stage 0..3 = the furrow running out a quarter at a time, 4 = whole, 5..6 = breaking up."""
    img = Image.new('RGBA', (w, n), (0, 0, 0, 0))
    px = img.load()
    def put(x, y, c):
        if 0 <= x < w and 0 <= y < n: px[x, y] = c + (255,)
    reach = int(n * (1 - min(stage + 1, 4) / 4))
    cx = w / 2
    for y in range(n - 1, reach - 1, -1):
        t = y / (n - 1)                                     # 1 at the blow, 0 far ahead
        cx += (_h(y // 5, seed) - .5) * .5
        cx = max(10, min(w - 11, cx))
        hw = 1.2 + 12.5 * t ** 1.4
        if y > n - 8: hw *= (1 - (y - (n - 8)) / 10) ** .5    # rounded near end
        for x in range(w):
            d = abs(x + .5 - cx) / max(hw, .1)
            v = _h(x // 2, y // 2, seed, 1)
            if d <= .25 and hw > 3: c = (18, 14, 12) if v > .3 else SHADOW
            elif d <= .7: c = SHADOW if v > .55 else EARTH[3]
            elif d <= 1.0: c = EARTH[3] if v > .5 else EARTH[1]
            elif d <= 1.0 + 2.6 / max(hw, 1) and hw > 2: c = EARTH[2] if v > .35 else EARTH[0]   # the raised lip
            elif d <= 1.0 + 3.6 / max(hw, 1) and hw > 2 and v > .55: c = EARTH[0]               # broken bits past it
            else: continue
            put(x, y, c)
        if hw <= 3: put(int(cx), y, (18, 14, 12))
        if y % 11 == 0 and hw > 3:                           # chevron cracks splitting forward off both lips
            for side in (-1, 1):
                x0 = cx + side * (hw + 1)
                for k in range(int(3 + hw * .9)):
                    put(int(x0 + side * k * .8), y - k, (30, 24, 20))
    if stage >= 5: img = dissolve(img, .35 if stage == 5 else .7, 19)
    return img


# 押し薙ぎ in the same mood: a flat dark crescent of wind swept left to right at the waist, and the ground scraped in
# an arc in front, scraped earth heaped on its outer lip. Both seen from above, the wielder at the image centre, the
# front (the direction faced) toward the image top; angles run from the left (180) over the top to the right (0).
SWEEP_HEAD = [175, 120, 60, 5, 5, 5, 5, 5]
SWEEP_TAIL = [185, 185, 175, 140, 90, 45, 20, 10]


def sweep_wind(frame, n=96):
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    px = img.load()
    c = n / 2
    head, tail = SWEEP_HEAD[frame], SWEEP_TAIL[frame]
    for y in range(n):
        for x in range(n):
            dx, dy = x + .5 - c, c - (y + .5)
            a = math.degrees(math.atan2(dy, dx)) % 360
            if a > 270: a -= 360
            if not head <= a <= tail: continue
            k = (tail - a) / max(tail - head, 1)          # 1 at the blade (head), 0 at the tail
            r = math.hypot(dx, dy)
            outer, width = 44, 4 + 16 * k ** 1.2
            if not outer - width <= r <= outer: continue
            depth = (outer - r) / width
            tone = (196, 188, 180) if depth < .12 and k > .6 else (88, 82, 80) if depth < .45 else (36, 32, 32)
            px[x, y] = tone + (int((70 + 150 * k) * (1 - depth * .5)),)
    if frame >= 5: img = dissolve(img, (frame - 4) * .25, 29)
    return img


def scrape_arc(stage, n=96, seed=47):
    """The ground scraped in an arc in front: a dark gouge with a heaped outer lip and earth thrown outward.
    stage 0..3 = the scrape running left to right, 4 = whole, 5..6 = breaking up."""
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    px = img.load()
    c = n / 2
    reach = 180 - 45 * min(stage + 1, 4) - (5 if stage >= 3 else 0)
    for y in range(n):
        for x in range(n):
            dx, dy = x + .5 - c, c - (y + .5)
            a = math.degrees(math.atan2(dy, dx))
            if not (max(reach, -5) <= a <= 185): continue
            r = math.hypot(dx, dy) + 1.6 * (_h(x // 2, y // 2, seed) - .5)
            mid = 30 + 2 * math.sin(math.radians(a) * 3)
            w = 3.5 + 3.5 * math.sin(math.radians(min(max(a, 0), 180)))   # deepest straight ahead
            v = _h(x // 2, y // 2, seed, 1)
            if abs(r - mid) <= w * .4: c_ = (22, 18, 16) if v > .3 else SHADOW
            elif abs(r - mid) <= w: c_ = SHADOW if v > .5 else EARTH[3]
            elif mid + w < r <= mid + w + 3: c_ = EARTH[2] if v > .35 else EARTH[0]     # heaped outer lip
            elif mid + w + 3 < r <= mid + w + 7 and v > .72: c_ = EARTH[1]              # earth thrown outward
            else: continue
            px[x, y] = c_ + (255,)
    if stage >= 5: img = dissolve(img, .35 if stage == 5 else .7, 23)
    return img


SMOKE = ((30, 26, 28), (14, 12, 13), (58, 46, 48))


def dust_puff(frame, seed=0, tones=None):
    """A puff of dust as a lumpy pixel cloud: three tones in 2 px clusters, swelling then thinning out."""
    tones = tones or DUST
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = img.load()
    r = (7, 10, 12, 13, 14)[frame]
    for y in range(32):
        for x in range(32):
            lump = 2.5 * math.sin(math.atan2(y - 16, x - 16) * 5 + seed) + 1.5 * _h(x // 3, y // 3, seed)
            if math.hypot(x - 16, y - 16) > r + lump: continue
            v = _h(x // 2, y // 2, seed, 4)
            col = tones[2] if v > .7 and y < 16 else tones[1] if v < .3 or y > 20 else tones[0]
            px[x, y] = col + ((215, 200, 170, 120, 70)[frame],)
    return dissolve(img, (0, 0, .15, .4, .65)[frame], seed + 9) if frame > 1 else img


def wind_band(frame):
    """The swing's dark trail: the blade's crescent in near-black and grey, see-through, burning away from its tail."""
    img = slash_texture(min(frame, 7))
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if not a: continue
            lum = (r + g + b) / 3
            tone = (196, 188, 180) if lum > 230 else (88, 82, 80) if lum > 150 else (36, 32, 32)
            px[x, y] = tone + (int(a * .8),)
    return img


def lump_model(top, side, under, seed):
    """A lump of ground: a main box and 1-2 smaller ones stuck on, base at y=8, all with grass on top."""
    def f(t, uv): return {'texture': t, 'uv': uv}
    def box(lo, hi):
        u = lambda a, b: [round(a, 2), 0, round(b, 2), round(hi[1] - lo[1], 2)]
        return {'from': lo, 'to': hi, 'faces': {
            'up': f('#top', [lo[0], lo[2], hi[0], hi[2]]), 'down': f('#under', [0, 0, 16, 16]),
            'north': f('#side', u(lo[0], hi[0])), 'south': f('#side', u(lo[0], hi[0])),
            'east': f('#side', u(lo[2], hi[2])), 'west': f('#side', u(lo[2], hi[2]))}}
    a = _h(seed, 1)
    els = [box([3, 8, 3], [13, 16 + int(a * 4), 13]),
           box([1 + int(a * 3), 8, 9], [7 + int(a * 3), 13, 15]),
           box([10, 8, 1], [15, 12 + int(_h(seed, 2) * 3), 7])]
    return {'textures': {'top': top, 'side': side, 'under': under, 'particle': side}, 'elements': els}


# The reference's light is see-through (studied at full size, 2026-10-20): only the white core is opaque, the colour
# around it lets the world show through, and a faint halo spills past the edge. The dark outline is dropped.
# Alphas avoid the shader markers (253, 121, 65, 200..215 in scripts/shaders/item.fsh).
GLOW_ALPHA = {W: 255, Y: 236, C: 228, O: 176, R: 142, D: 98, K: 70}


def glow(img, halo=R, halo_alpha=44):
    out = img.copy()
    px = out.load()
    w, h = out.size
    solid = [[px[x, y][3] > 0 for x in range(w)] for y in range(h)]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a: px[x, y] = (r, g, b, GLOW_ALPHA.get((r, g, b), 150))
    for y in range(h):  # one pixel of faint halo round the shape
        for x in range(w):
            if not solid[y][x] and any(0 <= x + i < w and 0 <= y + j < h and solid[y + j][x + i]
                                       for i in (-1, 0, 1) for j in (-1, 0, 1)):
                px[x, y] = halo + (halo_alpha,)
    return out


# ---------------------------------------------------------------- models (16 units = the display's scale in blocks)
def face(uv, tex='#0'):
    return {'texture': tex, 'uv': uv}


def box(lo, hi, side_uv, top_uv):
    return {'from': list(lo), 'to': list(hi), 'shade': False,
            'faces': {'north': face(side_uv), 'south': face(side_uv), 'east': face(side_uv), 'west': face(side_uv),
                      'up': face(top_uv), 'down': face([0, 15, 16, 16])}}


def fang_model(tex):
    # Base sits on y=8 (the ground). Three tapered blocks; the tip block is the white-hot point.
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        # A broad, flat blade of iron: wide across, thin front to back, tapering in four steps to a hot point.
        box((2.0, 8, 6.8), (14.0, 14, 9.2), [0, 10, 16, 16], [2, 11, 14, 14]),
        box((3.5, 14, 7.1), (12.5, 19, 8.9), [0, 6, 16, 11], [3, 6, 13, 9]),
        box((5.2, 19, 7.4), (10.8, 23, 8.6), [0, 3, 16, 7], [5, 3, 11, 5]),
        box((6.8, 23, 7.7), (9.2, 26, 8.3), [0, 0, 16, 3], [6, 0, 10, 2]),
    ]}


def shard_model(tex):
    # A slender rock shard on y=8: 5 units across at the base, tapering in three steps to a hot point 18 units up.
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        box((5.5, 8, 6.5), (10.5, 15, 9.5), [0, 9, 16, 16], [2, 10, 14, 14]),
        box((6.4, 15, 7.0), (9.6, 21, 9.0), [2, 3, 14, 9], [4, 4, 12, 7]),
        box((7.3, 21, 7.6), (8.7, 26, 8.4), [5, 0, 11, 3], [6, 0, 10, 2])]}


def board_model(tex, height=24):
    # A two-sided upright board standing on y=8, centred on x=8, z=8.
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 8, 8], 'to': [16, 8 + height, 8], 'shade': False,
         'faces': {'north': face([0, 0, 16, 16]), 'south': face([16, 0, 0, 16])}}]}


def ground_model(tex):
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 8.02, 0], 'to': [16, 8.02, 16], 'shade': False, 'faces': {'up': face([0, 0, 16, 16])}}]}


def centred_model(tex):
    # A two-sided square centred on the model origin (for camera-facing sprites).
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False,
         'faces': {'north': face([0, 0, 16, 16]), 'south': face([16, 0, 0, 16])}}]}


def plate_model(tex):
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        box((0, 7.6, 0), (16, 8.4, 16), [0, 0, 16, 1], [0, 0, 16, 16])]}


def build(assets, write):
    def texture(name, img):
        path = assets / f'textures/{DIR}/{name}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        return f'projects:{DIR}/{name}'

    def model(name, value):
        write(assets / f'models/{DIR}/{name}.json', value)
        write(assets / f'items/{DIR}/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{DIR}/{name}'}})

    fang = fang_texture()
    for n, amount in enumerate((0.0, .4, .75)):
        model(f'fang_{n}', fang_model(texture(f'fang_{n}', dissolve(fang, amount, 3) if amount else fang)))
    for s in range(4):
        model(f'flame_{s}', board_model(texture(f'flame_{s}', glow(flame_texture(s)))))
        model(f'bflame_{s}', centred_model(texture(f'bflame_{s}', black_flame_texture(s))))
    for s in range(3):
        model(f'ember_{s}', centred_model(texture(f'ember_{s}', ember_texture(s))))
        model(f'bdrop_{s}', centred_model(texture(f'bdrop_{s}', blood_drop_texture(s))))
    for s in range(5):
        model(f'bsplat_{s}', ground_model(texture(f'bsplat_{s}', blood_splat_texture(s))))
        model(f'bmist_{s}', centred_model(texture(f'bmist_{s}', blood_mist_texture(s))))
    model('rhalo', centred_model(texture('rhalo', red_halo_texture())))
    for st in range(5):
        model(f'ghost_{st}', ghost_model(texture(f'ghost_{st}', ghost_texture(st))))
        model(f'bsmoke_{st}', centred_model(texture(f'bsmoke_{st}', beast_smoke_texture(st))))
    for s in range(4):
        model(f'scale_{s}', centred_model(texture(f'scale_{s}', scale_dust_texture(s))))
    model('plate', plate_model(texture('plate', plate_texture())))
    for st in range(4):
        model(f'ray_{st}', board_model(texture(f'ray_{st}', glow(ray_texture(st))), height=16))
        model(f'ring_{st}', centred_model(texture(f'ring_{st}', glow(ring_texture(st)))))
    for st in range(3):
        model(f'twinkle_{st}', centred_model(texture(f'twinkle_{st}', glow(twinkle_texture(st)))))
    for st in range(4):
        model(f'gcrack_{st}', ground_model(texture(f'gcrack_{st}', chasm_pixels(st))))
    for st in range(3):
        model(f'crater_{st}', ground_model(texture(f'crater_{st}', crater_pixels(st))))
        model(f'dcrack_{st}', ground_model(texture(f'dcrack_{st}', dark_crack(st))))
    grass_top, turf_side = texture('turf_top', block_tex(GRASS, 51)), texture('turf_side', grass_side(52))
    def turf_box(lo, hi):
        def f(t, uv): return {'texture': t, 'uv': uv}
        return {'from': lo, 'to': hi, 'faces': {
            'up': f('#top', [lo[0], lo[2], hi[0], hi[2]]), 'down': f('#under', [lo[0], lo[2], hi[0], hi[2]]),
            'north': f('#side', [lo[0], 0, hi[0], hi[1] - lo[1]]), 'south': f('#side', [lo[0], 0, hi[0], hi[1] - lo[1]]),
            'east': f('#side', [lo[2], 0, hi[2], hi[1] - lo[1]]), 'west': f('#side', [lo[2], 0, hi[2], hi[1] - lo[1]])}}
    turf_tex = {'top': grass_top, 'side': turf_side, 'under': texture('turf_under', block_tex(EARTH, 53)), 'particle': turf_side}
    # Three torn shapes so the lips are not tiles: a whole slab, one with a corner broken off, two plates overlapping.
    TURFS = [[turf_box([2, 8, 3], [14, 12, 13])],
             [turf_box([2, 8, 3], [10, 12, 13]), turf_box([10, 8, 6], [14, 11, 13])],
             [turf_box([1, 8, 4], [9, 11, 12]), turf_box([6, 9.5, 2], [15, 13, 10])]]
    model('gblock', {'textures': turf_tex, 'elements': [turf_box([0, 0, 0], [16, 16, 16])]})   # preview stand-in for a ground block
    for n, els in enumerate(TURFS):
        model(f'turf_{n}', {'textures': turf_tex, 'elements': els})
    model('turfclod', lump_model(grass_top, turf_side, texture('turf_under', block_tex(EARTH, 53)), 7))
    for st in range(7):
        # Square texture (a tall one reads as an animation strip): the furrow down the middle third.
        sq = Image.new('RGBA', (128, 128), (0, 0, 0, 0)); sq.alpha_composite(furrow_pixels(st, w=40).transpose(Image.Transpose.FLIP_TOP_BOTTOM), (44, 0))  # up face: row 0 lies at the near end
        model(f'furrow_{st}', ground_model(texture(f'furrow_{st}', sq)))
    for f in range(8):
        model(f'sweep_{f}', ground_model(texture(f'sweep_{f}', sweep_wind(f).transpose(Image.Transpose.ROTATE_180))))
    for st in range(7):
        model(f'scrape_{st}', ground_model(texture(f'scrape_{st}', scrape_arc(st).transpose(Image.Transpose.ROTATE_180))))
    # 引き摺り: the held greatsword is swapped to this while a dragged one is drawn at the side.
    model('empty', {'textures': {}, 'elements': []})
    for f in range(5):
        model(f'dust_{f}', centred_model(texture(f'dust_{f}', dust_puff(f))))
        # 狂戦士の甲冑: the black that climbs the body as the armor closes over it.
        model(f'smoke_{f}', centred_model(texture(f'smoke_{f}', dust_puff(f, 5, SMOKE))))
    for f in range(8):
        model(f'wind_{f}', centred_model(texture(f'wind_{f}', wind_band(f))))
    model('clod', lump_model(texture('clod_top', block_tex(EARTH, 31)), texture('clod_side', block_tex(EARTH, 32)),
                             texture('clod_under', block_tex(EARTH, 33)), 4))
    iron, chain, bone = texture('colossus_iron', iron_plate(3)), texture('colossus_chain', chain_band(4)), texture('colossus_bone', bone_tex(5))
    for n, (length, width) in enumerate(FINGER):
        model(f'finger_{n}', finger_segment(iron, chain, bone, length, width, n == 2))
    for n in range(3):
        model(f'lump_{n}', lump_model(texture(f'lump_top_{n}', stone_tex(n * 3 + 1)),
                                      texture(f'lump_side_{n}', stone_tex(n * 3 + 2)),
                                      texture(f'lump_under_{n}', block_tex((STONE_T[1], STONE_T[3], STONE_T[0], STONE_T[3]), n * 3 + 3)), n))
    texture('preview_grass', block_tex(GRASS, 9))
    for n in range(3):
        fr, bk = petal_textures(n * 5 + 1)
        model(f'petal_{n}', petal_model(texture(f'petal_front_{n}', fr), texture(f'petal_back_{n}', bk)))
    for n in range(3):
        top, side, hot = slab_textures(n * 11)
        model(f'gslab_{n}', ground_slab_model(texture(f'gslab_top_{n}', top), texture(f'gslab_side_{n}', side),
                                              texture(f'gslab_hot_{n}', hot)))
    model('chunk', chunk_model(texture('chunk', clustered(GROUND_SIDE[0], (GROUND_TOP[1], (54, 50, 48)), 4, density=.3))))
    shard = rock_shard_texture()
    for n, amount in enumerate((0.0, .4, .75)):
        model(f'shard_{n}', shard_model(texture(f'shard_{n}', dissolve(shard, amount, 5) if amount else shard)))
    streak_tex = texture('streak', ray_streak_texture())
    model('streak', {'ambientocclusion': False, 'textures': {'0': streak_tex, 'particle': streak_tex}, 'elements': [
        {'from': [7, 8, 8], 'to': [9, 32, 8], 'shade': False,
         'faces': {'north': face([0, 0, 16, 16]), 'south': face([16, 0, 0, 16])}}]})
    model('field', ground_model(texture('field', field_texture())))
    for st in range(7):
        model(f'fissure_{st}', ground_model(texture(f'fissure_{st}', fissure_texture(st))))
    for st in range(3):
        model(f'slab_{st}', slab_model(texture(f'slab_under_{st}', slab_underside(st)),
                                       texture('stone_top', stone_texture(1)), texture('stone_side', stone_texture(2))))
    for f in range(len(SLASH_HEAD)):
        model(f'slash_{f}', centred_model(texture(f'slash_{f}', slash_texture(f))))
        model(f'steel_{f}', centred_model(texture(f'steel_{f}', slash_texture(f, STEEL))))
    model('frame', centred_model(texture('frame', frame_texture())))
    model('bar', {'ambientocclusion': False, 'textures': {'0': texture('bar', bar_texture())}, 'elements': [
        box((0, 7.4, 6.5), (16, 8.6, 9.5), [0, 0, 16, 16], [0, 0, 16, 16])]})
    for s in range(5):
        model(f'crack_{s}', ground_model(texture(f'crack_{s}', glow(crack_texture(s)))))


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    build(PACK / 'assets/projects', write)
