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
        model(f'flame_{s}', board_model(texture(f'flame_{s}', flame_texture(s))))
    model('plate', plate_model(texture('plate', plate_texture())))
    for st in range(4):
        model(f'ray_{st}', board_model(texture(f'ray_{st}', ray_texture(st)), height=16))
        model(f'ring_{st}', centred_model(texture(f'ring_{st}', ring_texture(st))))
    for st in range(3):
        model(f'twinkle_{st}', centred_model(texture(f'twinkle_{st}', twinkle_texture(st))))
    model('frame', centred_model(texture('frame', frame_texture())))
    model('bar', {'ambientocclusion': False, 'textures': {'0': texture('bar', bar_texture())}, 'elements': [
        box((0, 7.4, 6.5), (16, 8.6, 9.5), [0, 0, 16, 16], [0, 0, 16, 16])]})
    for s in range(5):
        model(f'crack_{s}', ground_model(texture(f'crack_{s}', crack_texture(s))))


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    build(PACK / 'assets/projects', write)
