"""Magic furniture: 16px material textures + block models (jar, distiller, research desk),
rendered into item icons with mcrender."""
import json
import random
from PIL import Image
from mcrender import render, box


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def tile(fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c == 'GLASSFILL':
                px[x, y] = (150, 200, 205, 70)
            elif c:
                px[x, y] = hx(c)
    return im


R = random.Random(7)
OAK = ['#2a150b', '#4e2c18', '#73422a', '#975c38', '#bb7c4c']
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
CU = ['#3b1d10', '#7a3f22', '#b56a3c', '#e29a62', '#ffd2a6']
BRICK = ['#24120e', '#462318', '#6a3424', '#8c4a32']
PAGE = ['#8a7a56', '#b8a67a', '#e2d4a8', '#f6ecc8']
LEATHER = ['#2a0e12', '#561c22', '#7e2e30']
IRON = ['#16181c', '#30343c', '#4e545e', '#727a86']

# --- dark oak planks: two boards per tile, grain streaks, hue drifts warm in highlights
grain = [[R.random() for _ in range(16)] for _ in range(16)]


def oak(x, y):
    board = y // 8
    if y % 8 == 7:
        return OAK[0]
    if y % 8 == 0:
        return OAK[3]
    g = grain[(y + board * 3) % 16][(x + board * 5) % 16]
    s = 2
    if g > 0.86:
        s = 3
    if g < 0.12:
        s = 1
    if (x + board * 7) % 13 == 0:
        s = 1
    return OAK[s]


def oak_top(x, y):
    return oak(y, x)


def brass(x, y):
    if x == 0 or y == 0:
        return BR[4] if x + y < 6 else BR[3]
    if x == 15 or y == 15:
        return BR[1]
    return BR[3] if (x + y) < 12 else BR[2]


def brass_fret(x, y):
    # meander (Greek key) band: 1px line, 1px gap
    base = brass(x, y)
    if 0 < y < 15 and 0 < x < 15:
        m = (x // 4) % 2
        lx, ly = x % 4, y % 6
        on = (ly in (1, 4) and lx != 3) or (lx == 0 and ly in (1, 2, 3, 4)) or (lx == 2 and ly in (2, 3) )
        if on and 2 <= y <= 13:
            return BR[1]
    return base


def copper(x, y):
    s = 2
    if x + y < 9:
        s = 3
    if x + y < 3:
        s = 4
    if x + y > 24:
        s = 1
    if (x * 7 + y * 3) % 11 == 0:
        s = max(1, s - 1)
    return CU[s]


def brick(x, y):
    row = y // 4
    off = 0 if row % 2 == 0 else 4
    if y % 4 == 3 or (x + off) % 8 == 0:
        return '#2c201c'
    s = 2 if (x + off) % 8 < 4 else 3
    if y % 4 == 0:
        s = 3
    return BRICK[s]


def glass(x, y):
    if x in (0, 15) or y in (0, 15):
        return '#c8eef0' if (x == 0 or y == 0) else '#6e9aa2'
    if x == 2 and 2 <= y <= 10:
        return '#f4ffff'
    if x == 3 and 2 <= y <= 4:
        return '#f4ffff'
    return 'GLASSFILL'


def liquid(ramp):
    def f(x, y):
        s = 2
        if x < 5:
            s = 3
        if x > 11:
            s = 1
        if y > 12:
            s = max(0, s - 1)
        return ramp[s]
    return f


def liquid_top(ramp):
    def f(x, y):
        return ramp[4] if (x + y) < 9 else ramp[3]
    return f


def page(x, y):
    if y in (3, 6, 9, 12) and 2 <= x <= 13 and (x + y) % 5 != 0:
        return PAGE[1]
    return PAGE[3] if x < 8 else PAGE[2]


def leather(x, y):
    return LEATHER[2] if (x + y) < 8 else LEATHER[1] if (x + y) < 22 else LEATHER[0]


def iron(x, y):
    return IRON[3] if (x + y) < 6 else IRON[2] if (x + y) < 20 else IRON[1]


def fire(x, y):
    d = abs(x - 7.5) + max(0, 8 - y) * 0.9
    if d < 2:
        return '#fff0b0'
    if d < 4:
        return '#ffb040'
    if d < 6.5:
        return '#e05a1c'
    return '#7a1e12'


def rune(x, y):
    d = abs(x - 7.5) + abs(y - 7.5)
    if x in (7, 8) and 3 <= y <= 12 or y in (7, 8) and 3 <= x <= 12:
        return '#c8f4ff' if d < 3 else '#49b4d9'
    return None


def ink(x, y):
    return '#3a3a50' if x < 6 else '#1a1a24'


def paper_label(x, y):
    if x in (0, 15) or y in (0, 15):
        return '#b8ac88'
    return '#e6dab4' if x < 8 else '#d2c49c'


AQ = ['#0e2c55', '#1d63a0', '#3fa5d6', '#9fe3f4', '#f2fdff']
T = {
    'oak': tile(oak), 'oak_top': tile(oak_top), 'brass': tile(brass), 'fret': tile(brass_fret),
    'copper': tile(copper), 'brick': tile(brick), 'glass': tile(glass),
    'liq': tile(liquid(AQ)), 'liq_top': tile(liquid_top(AQ)),
    'page': tile(page), 'leather': tile(leather), 'iron': tile(iron), 'fire': tile(fire),
    'rune': tile(rune), 'ink': tile(ink), 'label': tile(paper_label),
}


def label_mark(base):
    im = base.copy()
    px = im.load()
    drop = {4: (7, 8), 5: (7, 8), 6: (6, 9), 7: (6, 9), 8: (5, 10), 9: (5, 10), 10: (5, 10), 11: (6, 9)}
    for y, (a, b) in drop.items():
        for x in range(a, b + 1):
            px[x, y] = hx(AQ[2] if x < 8 else AQ[1])
    px[6, 8] = hx(AQ[4])
    return im


T['label'] = label_mark(T['label'])

# ---------------- models ----------------
JAR = {'elements': [
    box((5, 0.5, 5), (11, 8.5, 11), {'north': 'liq', 'south': 'liq', 'east': 'liq', 'west': 'liq', 'up': 'liq_top', 'down': 'liq'}),
    box((4, 0, 4), (12, 11, 12), 'glass'),
    box((5.5, 2.5, 12.02), (10.5, 7.5, 12.2), 'label', faces=('south',)),
    box((3.6, 11, 3.6), (12.4, 12, 12.4), 'fret'),
    box((5, 12, 5), (11, 13.5, 11), {'north': 'oak', 'south': 'oak', 'east': 'oak', 'west': 'oak', 'up': 'oak_top', 'down': 'oak'}),
    box((7.2, 13.5, 7.2), (8.8, 14.6, 8.8), 'brass'),
]}

DIST = {'elements': [
    # brick furnace
    box((2, 0, 3), (15, 6, 13), 'brick'),
    box((6.5, 0.6, 12.98), (10.5, 4.6, 13.1), 'fire', faces=('south',), emissive=True),
    box((1.5, 6, 2.5), (15.5, 7, 13.5), 'iron'),
    # copper pot + dome + neck
    box((5, 7, 5), (12, 8, 11), 'copper'),
    box((4, 8, 4), (13, 11, 12), 'copper'),
    box((5, 11, 5), (12, 12.5, 11), 'copper'),
    box((6, 12.5, 6), (11, 13.2, 10), 'copper'),
    box((7.5, 13, 7), (9.5, 15.5, 9), 'copper'),
    box((3.8, 9, 3.8), (13.2, 10, 12.2), 'fret'),
    # swan-neck pipe to the receiver (west side)
    box((1.5, 14.5, 7.5), (7.5, 15.5, 8.5), 'copper'),
    box((1.5, 9.5, 7.5), (2.5, 14.5, 8.5), 'copper'),
    # receiver flask on the left, standing on the ground in front of the furnace corner
    box((-2.5, 0, 5), (1.5, 1, 11), 'iron'),
    box((-1.9, 1.4, 6.4), (0.9, 4.6, 9.6), {'north': 'liq', 'south': 'liq', 'east': 'liq', 'west': 'liq', 'up': 'liq_top', 'down': 'liq'}),
    box((-2.5, 1, 6), (1.5, 7, 10), 'glass'),
    box((-0.8, 7, 7.3), (-0.2, 9.6, 8.7), 'glass'),
]}

DESK = {'elements': [
    # top slab
    box((0, 11, 1), (16, 13, 15), {'north': 'oak', 'south': 'oak', 'east': 'oak', 'west': 'oak', 'up': 'oak_top', 'down': 'oak'}),
    box((-0.1, 12.6, 0.9), (16.1, 13.05, 15.1), 'fret', faces=('north', 'south', 'east', 'west')),
    # apron / drawer
    box((1, 8.5, 1.5), (15, 11, 14.5), 'oak'),
    box((5, 9, 14.5), (11, 10.5, 14.7), 'brass', faces=('south',)),
    # legs
    box((1, 0, 2), (3, 8.5, 4), 'oak'), box((13, 0, 2), (15, 8.5, 4), 'oak'),
    box((1, 0, 12), (3, 8.5, 14), 'oak'), box((13, 0, 12), (15, 8.5, 14), 'oak'),
    box((1, 2, 3), (15, 3, 13), 'oak', faces=('up', 'north', 'south')),
    # open book: leather cover + two pages + rune on the right page
    box((3, 13, 4), (12, 13.5, 11.5), 'leather'),
    box((3.4, 13.5, 4.4), (7.4, 14.2, 11.1), 'page'),
    box((7.6, 13.5, 4.4), (11.6, 14.2, 11.1), 'page'),
    box((8.2, 14.21, 5.2), (11.0, 14.25, 10.2), 'rune', faces=('up',), emissive=True),
    # inkwell + quill
    box((12.5, 13, 3), (14.5, 15, 5), 'ink'),
    box((13.3, 15, 3.7), (13.7, 18, 4.1), 'page'),
    box((12.9, 16, 3.75), (14.1, 18.8, 4.05), 'page'),
    # stacked books on the right
    box((12, 13, 8.5), (15.5, 14.2, 13), 'leather'),
    box((12.3, 14.2, 8.8), (15.2, 15.2, 12.6), 'oak'),
    box((12.5, 14.25, 9.0), (15.0, 15.15, 12.4), 'page', faces=('east','west')),
]}


def save_model(name, model):
    out = {'textures': {k: f'projects:block/magic/{k}' for k in sorted({f['texture'].lstrip('#') for e in model['elements'] for f in e['faces'].values()})},
           'elements': [{'from': e['from'], 'to': e['to'], 'faces': {k: {kk: vv for kk, vv in v.items() if kk != 'emissive'} for k, v in e['faces'].items()}} for e in model['elements']]}
    json.dump(out, open(f'model_{name}.json', 'w'), indent=1)


for k, im in T.items():
    im.save(f'tex_{k}.png')
icons = {}
for name, m, sc in (('jar', JAR, 0.78), ('distiller', DIST, 0.62), ('desk', DESK, 0.6)):
    save_model(name, m)
    icons[name] = render(m, T, size=128, scale=sc, light=True, rot_y=45)
    icons[name].save(f'icon3d_{name}.png')
sheet = Image.new('RGBA', (3 * 140 + 20 + 3 * 300, 300), (14, 16, 20, 255))
for i, n in enumerate(('jar', 'distiller', 'desk')):
    big = icons[n].resize((256, 256), Image.NEAREST)
    sheet.paste(big, (20 + i * 280, 20), big)
for i, n in enumerate(('jar', 'distiller', 'desk')):
    sm = icons[n].resize((48, 48), Image.NEAREST)
    sheet.paste(sm, (880 + i * 70 + 60, 40), sm)
sheet.save('prev_furniture.png')
print('ok')
