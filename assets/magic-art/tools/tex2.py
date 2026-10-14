"""Structured textures (no per-pixel noise): planks with seams and grain lines, legs with
vertical grain, a leather top with a lit gradient and a framed border, woven cloth."""
from PIL import Image


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def paint(fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c:
                p[x, y] = hx(c)
    return im


# greatwood ramp: warm highlights, cool-ish deep shadow (hue shifts toward violet in the dark)
W = ['#1a1016', '#2a1a16', '#3a2418', '#4c301e', '#5e3e26', '#74502e']


def planks(x, y):
    row, ry = y // 4, y % 4
    if ry == 3:
        return W[0]                       # seam
    if ry == 0:
        return W[5] if x not in (0,) else W[4]   # lit top edge of each plank
    # grain: two dashed lines per plank, offset per row
    off = (row * 5) % 16
    xx = (x + off) % 16
    if ry == 2 and xx in (2, 3, 4, 9, 10, 13):
        return W[2]
    if ry == 1 and xx in (6, 7, 14):
        return W[3]
    # plank end joint
    if (x + row * 7) % 16 == 11:
        return W[1]
    return W[4] if ry == 1 else W[3]


def leg(x, y):
    if x == 0:
        return W[5]
    if x == 15:
        return W[1]
    base = 4 if x < 5 else 3 if x < 11 else 2
    if x in (4, 9, 13) and (y % 6) != 2:
        return W[base - 1]
    return W[base]


L = ['#3a1a14', '#55281c', '#6c3626', '#844630', '#9c5a3c']


def leather_top(x, y):
    if x in (0, 15) or y in (0, 15):
        return W[2]
    if x == 1 or y == 1:
        return W[5]
    if x == 14 or y == 14:
        return W[0]
    g = (x + y) / 26.0                         # lit from the top-left
    s = 4 if g < 0.3 else 3 if g < 0.55 else 2 if g < 0.8 else 1
    if (x, y) in ((5, 9), (6, 9), (10, 5), (11, 6)):   # two small scuffs
        s = max(0, s - 1)
    return L[s]


N = ['#0e1428', '#16204a', '#1f2e62', '#2a3e7e', '#3c549c']
G = ['#7a5a18', '#c09a30', '#e8c858']


def cloth_top(x, y):
    if y in (1, 14):
        return G[1]
    if y in (0, 15):
        return N[1]
    twill = (x + y) % 4 == 0
    g = y / 15.0
    s = 3 if g < 0.35 else 2 if g < 0.75 else 1
    return N[s + 1] if twill else N[s]


def cloth_hang(x, y):
    if x in (1, 14):
        return G[1]
    twill = (x + y) % 4 == 0
    s = 2 if y < 6 else 1 if y < 12 else 0
    if y == 15:
        return G[0]
    return N[s + 1] if twill else N[s]


def apply(T):
    T['gw_side'] = paint(planks)
    T['gw'] = paint(planks)
    T['leg'] = paint(leg)
    T['top'] = paint(leather_top)
    T['cloth'] = paint(cloth_top)
    T['cloth_side'] = paint(cloth_hang)
    return T
