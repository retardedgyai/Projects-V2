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


def slash_texture(frame):
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


def chasm_pixels(stage, length=112, width=24, seed=23):
    """The crack as an open chasm seen from above, pixel by pixel: a jagged opening 4-10 px wide whose sides step in
    zig-zags; the lit wall on the left, the shadowed wall on the right, a black depth, and the magma at the bottom
    (stage 0 white-hot .. 2 dull, 3 cooled); a dark rim and loose light chips along the lip."""
    img = Image.new('RGBA', (width, length), (0, 0, 0, 0))
    px = img.load()
    magma = [((255, 236, 170), (255, 170, 60)), ((255, 150, 50), (226, 70, 30)), ((170, 46, 30), (120, 30, 26)),
             ((70, 24, 22), (50, 20, 20))][stage]
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
    model('plate', plate_model(texture('plate', plate_texture())))
    for st in range(4):
        model(f'ray_{st}', board_model(texture(f'ray_{st}', glow(ray_texture(st))), height=16))
        model(f'ring_{st}', centred_model(texture(f'ring_{st}', glow(ring_texture(st)))))
    for st in range(3):
        model(f'twinkle_{st}', centred_model(texture(f'twinkle_{st}', glow(twinkle_texture(st)))))
    for st in range(4):
        model(f'gcrack_{st}', ground_model(texture(f'gcrack_{st}', chasm_pixels(st))))
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
