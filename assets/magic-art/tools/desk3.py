"""Research table v3: two blocks wide (Thaumcraft skeleton), chunky greatwood with a
leather inset top, raised brass corners, two drawers, a lower stretcher, and ProjectS
touches: a navy cloth runner draped over the front edge, an open grimoire with a cool
cyan rune, inkwell + flat quill, a small essentia jar and a rolled star chart."""
import random
from PIL import Image
from mcrender import render, box
import tcstyle
import distiller3


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def tile(fn, seed=0):
    r = random.Random(seed)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    for y in range(16):
        for x in range(16):
            c = fn(x, y, r)
            if c:
                p[x, y] = hx(c) if isinstance(c, str) else c
    return im


GW = tcstyle.GW
BR = distiller3.BR
CY = distiller3.CY
NAVY = ['#0c1226', '#16203e', '#22305a', '#30447a', '#4a62a0']
PAGE = ['#8a7a56', '#b8a67a', '#e0d0a0', '#f4e8c0']
LEA = ['#3a1c12', '#52281a', '#683424', '#7e422e']
T = dict(tcstyle.T)
T.update({k: distiller3.T[k] for k in ('brass', 'iron', 'window')})


def plank(x, y, r):
    if y % 4 == 3:
        return GW[0]
    if y % 4 == 0:
        return GW[4] if r.random() < 0.7 else GW[3]
    return GW[r.choice((2, 2, 3, 1))]


T['gw_side'] = tile(plank, 1)


def leg(x, y, r):
    if x == 0:
        return GW[4]
    if x == 15:
        return GW[0]
    return GW[3] if x < 6 else GW[2] if x < 11 else GW[1]


T['leg'] = tile(leg, 2)


def top_inset(x, y, r):           # leather writing surface with a darker border and a highlight lip
    if x in (0, 15) or y in (0, 15):
        return GW[1]
    if x == 1 or y == 1:
        return GW[4]
    if x == 14 or y == 14:
        return GW[0]
    return LEA[r.choice((1, 2, 2, 2, 3))]


T['top'] = tile(top_inset, 3)


def drawer(x, y, r):
    if x in (0, 15) or y in (0, 15):
        return GW[0]
    if x == 1 or y == 1:
        return GW[4]
    if 6 <= x <= 9 and 7 <= y <= 9:
        return BR[4] if y == 7 else BR[2] if y == 8 else BR[1]
    return GW[r.choice((2, 3, 3))]


T['drawer'] = tile(drawer, 4)


def cloth(x, y, r):               # navy runner with a brass-thread border and a tiny star motif
    if y in (1, 14):
        return BR[2]
    if (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
        return NAVY[4]
    return NAVY[2] if (x + y) % 6 else NAVY[3]


T['cloth'] = tile(cloth, 5)
T['cloth_side'] = tile(lambda x, y, r: NAVY[1] if y > 10 else NAVY[2], 6)


def page(x, y, r):
    if y in (3, 5, 7, 9, 11) and 2 <= x <= 13 and r.random() < 0.75:
        return PAGE[1]
    return PAGE[3] if x < 7 else PAGE[2]


def rune_page(x, y, r):
    d = abs(x - 7.5) + abs(y - 7.5)
    on = (x in (7, 8) and 3 <= y <= 12) or (y in (7, 8) and 3 <= x <= 12) or (4.5 <= d < 5.5)
    if on:
        return CY[2] if d < 3 else CY[1]
    return PAGE[2] if x < 8 else PAGE[1]


T['page'] = tile(page, 7)
T['rune_page'] = tile(rune_page, 8)
T['cover'] = tile(lambda x, y, r: LEA[3] if (x + y) < 8 else LEA[2] if (x + y) < 20 else LEA[1], 9)
T['chart'] = tile(lambda x, y, r: BR[2] if (x * 3 + y) % 7 == 0 else NAVY[3] if y < 8 else NAVY[2], 10)


def M():
    full = [0, 0, 16, 16]
    E = []
    # top slab (two blocks): sides planks, top leather inset per block
    for x0 in (0, 16):
        E.append(box((x0, 11, 0), (x0 + 16, 15, 16), {'north': 'gw_side', 'south': 'gw_side', 'east': 'gw_side', 'west': 'gw_side', 'up': 'top', 'down': 'gw_side'}, uv={'up': full}))
    # raised brass corner caps
    for (x0, z0) in ((-0.3, -0.3), (30.3, -0.3), (-0.3, 14.3), (30.3, 14.3)):
        E.append(box((x0, 10.7, z0), (x0 + 2, 15.3, z0 + 2), 'brass'))
    # apron with two drawers on the front
    E.append(box((1.0, 8.5, 1.0), (31.0, 11.0, 15.0), 'gw_side'))
    for dx in (4.0, 20.0):
        E.append(box((dx, 8.7, 15.0), (dx + 8.0, 10.8, 15.25), 'drawer', faces=('south',), uv={'south': full}))
    # chunky legs + lower stretcher
    for (lx, lz) in ((1.0, 1.0), (27.0, 1.0), (1.0, 11.0), (27.0, 11.0)):
        E.append(box((lx, 0, lz), (lx + 4.0, 8.5, lz + 4.0), 'leg'))
    E.append(box((5.0, 2.0, 2.5), (27.0, 3.5, 4.5), 'gw_side'))
    E.append(box((5.0, 2.0, 11.5), (27.0, 3.5, 13.5), 'gw_side'))
    # navy cloth runner across the top, draping over the front edge
    E.append(box((10.0, 15.0, 0.0), (22.0, 15.3, 16.0), {'up': 'cloth', 'north': 'cloth_side', 'south': 'cloth_side', 'east': 'cloth_side', 'west': 'cloth_side', 'down': 'cloth_side'}, uv={'up': full}))
    E.append(box((10.0, 11.5, 16.0), (22.0, 15.3, 16.3), 'cloth', faces=('south', 'east', 'west'), uv={'south': full}))
    # open grimoire on the runner (cover + two pages, rune page glowing)
    E.append(box((11.0, 15.3, 4.0), (21.0, 15.8, 12.0), 'cover'))
    E.append(box((11.5, 15.8, 4.5), (15.9, 16.6, 11.5), 'page', uv={'up': full}))
    E.append(box((16.1, 15.8, 4.5), (20.5, 16.6, 11.5), {'up': 'rune_page', 'north': 'page', 'south': 'page', 'east': 'page', 'west': 'page', 'down': 'page'}, uv={'up': full}, emissive=True))
    E.append(box((15.8, 15.8, 4.5), (16.2, 16.9, 11.5), 'page'))
    # inkwell + flat quill (left)
    E.append(box((3.0, 15.0, 3.0), (6.0, 16.6, 6.0), 'inkglass'))
    E.append(box((3.5, 16.6, 3.5), (5.5, 17.1, 5.5), 'ink'))
    E.append(box((-1.0, 15.6, 4.5), (11.0, 27.6, 4.51), 'quill', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}))
    # small essentia jar (right back)
    E.append(box((25.6, 15.0, 2.6), (29.4, 19.4, 6.4), 'ess', emissive=True))
    E.append(box((25.0, 15.0, 2.0), (30.0, 20.0, 7.0), 'jar_glass', uv={k: full for k in ('north', 'south', 'east', 'west', 'up')}))
    E.append(box((26.0, 20.0, 3.0), (29.0, 21.0, 6.0), 'lid'))
    # rolled star chart (right front)
    E.append(box((24.0, 15.0, 10.0), (30.0, 16.2, 11.4), 'chart'))
    E.append(box((23.6, 14.95, 9.9), (24.2, 16.3, 11.5), 'brass'))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=192, scale=0.36, light=True, rot_y=35, center=(16, 12, 8))
    im.save('tc_desk.png')
    big = im.resize((576, 576), Image.NEAREST)
    sh = Image.new('RGBA', (616, 616), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_desk3.png')
    print('ok')
