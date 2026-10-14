"""Thaumcraft-leaning versions: chunky silhouettes, vanilla-like noisy 16px textures,
dark greatwood, small brass accents, TC-style jar labels, smeltery + alembic stack."""
import random
from PIL import Image
from mcrender import render, box


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


R = random.Random(42)


def noise_tile(ramp, weights=(1, 3, 6, 3, 1), seed=0, fn=None):
    r = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    p = im.load()
    pool = [i for i, w in enumerate(weights) for _ in range(w)]
    for y in range(16):
        for x in range(16):
            c = fn(x, y, r) if fn else None
            p[x, y] = hx(c) if c else hx(ramp[r.choice(pool)])
    return im


GW = ['#1c120c', '#261810', '#2e1e14', '#38261a', '#463020']          # greatwood (very dark)
LEA = ['#4a2a18', '#5a341e', '#6a3e24', '#7a4a2c']                     # desk top inset
BR = ['#5a3a10', '#8a6a20', '#c09a30', '#e8c858', '#fff0a0']           # brass
IRON = ['#2a2a2e', '#3a3a40', '#4a4a52', '#5c5c66']
GLASS_EDGE = (180, 220, 235, 255)
T = {}


# greatwood planks: vertical grain, vanilla-ish noise
def gw_fn(x, y, r):
    if x % 4 == 0 and r.random() < 0.6:
        return GW[0]
    return None


T['gw'] = noise_tile(GW[1:], (2, 6, 4, 1), seed=1, fn=gw_fn)
T['gw_top'] = noise_tile(GW[1:], (2, 6, 4, 1), seed=2)


def inset_top(x, y, r):
    if x in (0, 15) or y in (0, 15):
        return GW[1]
    if x in (1, 14) or y in (1, 14):
        return GW[3] if (x == 1 or y == 1) else GW[0]
    return LEA[r.choice((0, 1, 1, 2, 2, 3))]


T['desk_top'] = noise_tile(LEA, seed=3, fn=inset_top)


def drawer_fn(x, y, r):
    if 6 <= x <= 9 and 7 <= y <= 8:
        return BR[3] if y == 7 else BR[1]
    return None


T['gw_drawer'] = noise_tile(GW[1:], (2, 6, 4, 1), seed=4, fn=lambda x, y, r: drawer_fn(x, y, r) or gw_fn(x, y, r))
# quill sprite (flat, billboarded): grey/gold feather on transparent
q = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
qp = q.load()
feather = [(11, 1), (12, 1), (10, 2), (11, 2), (12, 2), (9, 3), (10, 3), (11, 3), (12, 3), (8, 4), (9, 4), (10, 4), (11, 4),
           (7, 5), (8, 5), (9, 5), (10, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (5, 8), (6, 8), (7, 8), (5, 9), (6, 9), (4, 10), (5, 10)]
for i, (x, y) in enumerate(feather):
    qp[x, y] = hx('#d8d4cc' if (x + y) % 3 else '#a8a49c')
for (x, y) in ((11, 2), (10, 3), (9, 4), (8, 5), (7, 6), (6, 7), (5, 8)):
    qp[x, y] = hx('#c8a040')                           # gold shaft
for (x, y) in ((4, 11), (3, 12), (3, 13)):
    qp[x, y] = hx('#2a2a2e')
T['quill'] = q
T['ink'] = noise_tile(['#20242c', '#2a3038', '#38404a'], (3, 3, 1), seed=5)
T['inkglass'] = noise_tile(['#6a9aa8', '#8ab8c4', '#a8d4dc'], (2, 3, 1), seed=6)

# brass (vanilla gold-ish, slightly noisy)
T['brass'] = noise_tile(BR[1:], (2, 5, 3, 1), seed=7)
T['iron'] = noise_tile(IRON, (2, 4, 3, 1), seed=8)


# ---- jar: TC warded jar ----
def glass_fn(x, y, r):
    if x in (0, 15) or y in (0, 15):
        return None
    return None


g = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
gp = g.load()
for i in range(16):
    for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
        gp[x, y] = (205, 235, 245, 150) if (x == 0 or y == 0) else (140, 180, 200, 140)
for y in range(1, 15):
    for x in range(1, 15):
        gp[x, y] = (200, 230, 245, 18)
for (x, y) in ((2, 2), (3, 2), (2, 3), (2, 4)):
    gp[x, y] = (240, 255, 255, 200)
T['jar_glass'] = g
T['lid'] = noise_tile(['#2c1a10', '#3a2416', '#48301e', '#563a24'], (2, 5, 3, 1), seed=9)


def lid_top_fn(x, y, r):
    if 4 <= x <= 11 and 4 <= y <= 11:
        return '#2a180e' if (x in (4, 11) or y in (4, 11)) else '#3a2416'
    return None


T['lid_top'] = noise_tile(['#3a2416', '#48301e', '#563a24'], (3, 4, 2), seed=10, fn=lid_top_fn)


def essentia(ramp, seed):
    return noise_tile(ramp, (1, 4, 6, 3), seed=seed)


AQ = ['#1d5a96', '#2a78bc', '#3f96d6', '#6ab8e8']
T['ess'] = essentia(AQ, 11)


# TC-style label: gold frame, pale yellow paper, black aspect silhouette (water drop)
lb = Image.new('RGBA', (16, 16))
lp = lb.load()
for y in range(16):
    for x in range(16):
        if x in (0, 15) or y in (0, 15):
            lp[x, y] = hx('#b8861c')
        elif x in (1, 14) or y in (1, 14):
            lp[x, y] = hx('#f0c840')
        else:
            lp[x, y] = hx('#f6e8b0' if (x + y) % 7 else '#ecdca0')
drop = {3: (7, 8), 4: (7, 8), 5: (6, 9), 6: (6, 9), 7: (5, 10), 8: (5, 10), 9: (5, 10), 10: (5, 10), 11: (6, 9), 12: (7, 8)}
for y, (a, c) in drop.items():
    for x in range(a, c + 1):
        lp[x, y] = hx('#1a1a1a')
T['label'] = lb


def JAR():
    full = [0, 0, 16, 16]
    E = [
        box((4.5, 0.5, 4.5), (11.5, 9.5, 11.5), 'ess', emissive=True),
        box((3, 0, 3), (13, 12, 13), 'jar_glass', uv={k: full for k in ('north', 'south', 'east', 'west', 'up', 'down')}),
        box((5, 12, 5), (11, 14, 11), {'north': 'lid', 'south': 'lid', 'east': 'lid', 'west': 'lid', 'up': 'lid_top', 'down': 'lid'}, uv={'up': full}),
        box((4.5, 3, 13.02), (11.5, 10, 13.12), 'label', faces=('south',), uv={'south': full}),
    ]
    return {'elements': E}


# ---- research table: chunky TC table ----
def DESK():
    full = [0, 0, 16, 16]
    E = [
        box((0, 10, 0), (16, 16, 16), {'north': 'gw', 'south': 'gw_drawer', 'east': 'gw', 'west': 'gw', 'up': 'desk_top', 'down': 'gw'}, uv={'up': full, 'south': full}),
        box((1, 0, 1), (5, 10, 5), 'gw'), box((11, 0, 1), (15, 10, 5), 'gw'),
        box((1, 0, 11), (5, 10, 15), 'gw'), box((11, 0, 11), (15, 10, 15), 'gw'),
        # inkwell + flat quill sprite (two crossed planes, like TC's)
        box((10.5, 16, 2.5), (13.5, 17.5, 5.5), 'inkglass'),
        box((11, 17.5, 3), (13, 18, 5), 'ink'),
        box((5.0, 16.0, 4.0), (17.0, 28.0, 4.01), 'quill', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}),
    ]
    return {'elements': E}


# ---- smeltery (TC5/6 style): brass-framed furnace, dark iron grate front with fire ----
def frame_fn(base):
    def f(x, y, r):
        if x in (0, 1, 14, 15) or y in (0, 1, 14, 15):
            return BR[r.choice((2, 3, 3, 4))] if (x < 2 or y < 2) else BR[r.choice((1, 2, 2))]
        return None
    return f


T['smelt_side'] = noise_tile(IRON, (2, 4, 3, 1), seed=12, fn=frame_fn('iron'))


def grate_fn(x, y, r):
    f = frame_fn(None)(x, y, r)
    if f:
        return f
    if 3 <= x <= 12 and 4 <= y <= 12:
        if x % 3 == 0:
            return IRON[0]
        d = abs(x - 7.5) + max(0, 10 - y) * 0.7
        return '#ffe08a' if d < 2 else '#ff9a30' if d < 3.6 else '#c8401a' if d < 5.5 else '#401008'
    return None


T['smelt_front'] = noise_tile(IRON, (2, 4, 3, 1), seed=13, fn=grate_fn)
T['smelt_top'] = noise_tile(IRON, (2, 4, 3, 1), seed=14, fn=lambda x, y, r: frame_fn(None)(x, y, r) or ('#18181c' if 5 <= x <= 10 and 5 <= y <= 10 else None))


# ---- alembic: dark wood barrel with brass hoops and a small glass window ----
def alembic_fn(x, y, r):
    if y in (2, 3, 12, 13):
        return BR[3] if y in (2, 12) else BR[1]
    if 6 <= x <= 9 and 6 <= y <= 9:
        return '#6ab8e8' if (x + y) % 3 else '#3f96d6'
    if x % 4 == 0:
        return GW[0]
    return None


T['alembic'] = noise_tile(GW[1:], (2, 6, 4, 1), seed=15, fn=alembic_fn)
T['alembic_top'] = noise_tile(GW[1:], (2, 6, 4, 1), seed=16, fn=lambda x, y, r: (BR[2] if (x in (0, 15) or y in (0, 15)) else ('#101010' if 6 <= x <= 9 and 6 <= y <= 9 else None)))


def SMELTERY(with_alembic=True):
    full = [0, 0, 16, 16]
    E = [box((0, 0, 0), (16, 16, 16), {'north': 'smelt_side', 'south': 'smelt_front', 'east': 'smelt_side', 'west': 'smelt_side', 'up': 'smelt_top', 'down': 'smelt_side'},
             uv={k: full for k in ('north', 'south', 'east', 'west', 'up', 'down')}, emissive=False)]
    if with_alembic:
        E += [box((3, 16, 3), (13, 27, 13), {'north': 'alembic', 'south': 'alembic', 'east': 'alembic', 'west': 'alembic', 'up': 'alembic_top', 'down': 'alembic'},
                  uv={k: full for k in ('north', 'south', 'east', 'west', 'up')}),
              box((6.5, 27, 6.5), (9.5, 29, 9.5), 'iron')]
    return {'elements': E}


if __name__ == '__main__':
    shots = [('jar', JAR(), 0.8), ('smeltery', SMELTERY(), 0.42), ('desk', DESK(), 0.52)]
    out = Image.new('RGBA', (3 * 300 + 40, 320), (14, 16, 20, 255))
    for i, (n, m, sc) in enumerate(shots):
        im = render(m, T, size=128, scale=sc, light=True, rot_y=45, pad={'jar': 0, 'smeltery': 26, 'desk': 12}[n])
        im.save(f'tc_{n}.png')
        big = im.resize((280, 280), Image.NEAREST)
        out.paste(big, (20 + i * 300, 20), big)
    out.save('prev_tc.png')
    print('ok')
