"""Research desk v2: molded top with brass corners, turned legs, panelled sides,
drawer with a brass ring pull, lower shelf with books; open book with a glowing
rune, candle, inkwell + quill, rolled star chart."""
from PIL import Image
from mcrender import render, box
import jar2


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
                p[x, y] = hx(c) if isinstance(c, str) else c
    return im


OAK = ['#2a150b', '#4e2c18', '#73422a', '#975c38', '#bb7c4c']
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
PAGE = ['#8a7a56', '#b8a67a', '#e2d4a8', '#f6ecc8']
LEA = ['#2a0e12', '#561c22', '#7e2e30', '#a04440']
NAVY = ['#0e1428', '#1c2a4e', '#2e4274', '#4a64a0']
T = dict(jar2.T)


def oak(x, y):
    s = 2
    if (x * 7 + y * 3) % 11 == 0:
        s = 1
    if (x + y * 5) % 17 == 0:
        s = 3
    if y % 8 == 0:
        s = 3
    return OAK[s]


def oak_edge(x, y):           # molded top edge: light lip, dark under-cut, fret band
    if y <= 1:
        return OAK[4] if y == 0 else OAK[3]
    if y >= 14:
        return OAK[0]
    lx, ly = x % 4, y % 6
    if 3 <= y <= 12 and ((ly in (3, 4) and lx != 3) or lx == 0):
        return BR[2]
    return OAK[2]


def oak_top(x, y):
    s = 3 if (y // 4) % 2 == 0 else 2
    if x == 0 or y == 0:
        s = 4
    if (x * 5 + y) % 13 == 0:
        s = max(1, s - 1)
    return OAK[s]


def panel(x, y):              # nested-square raised panel
    d = max(abs(x - 7.5), abs(y - 7.5))
    if d >= 7:
        return OAK[1]
    if d >= 6:
        return OAK[3] if (x < 8 and y < 8) or (x + y < 15) else OAK[1]
    if d >= 3.5:
        return OAK[2]
    if d >= 2.5:
        return OAK[3] if x + y < 15 else OAK[1]
    return OAK[2]


def drawer(x, y):
    if x in (0, 15) or y in (0, 15):
        return OAK[1]
    if y in (1,) or x in (1,):
        return OAK[3]
    # brass ring pull in the middle
    if 6 <= x <= 9 and 5 <= y <= 10:
        ring = (x in (6, 9) and 6 <= y <= 9) or (y in (5, 10) and 7 <= x <= 8)
        if ring:
            return BR[4] if (x == 6 or y == 5) else BR[1]
        if (x, y) == (7, 4) or (x, y) == (8, 4):
            return BR[3]
    return OAK[2]


def leg(x, y):
    s = 3 if x < 5 else 2 if x < 11 else 1
    return OAK[s]


def page(x, y):
    if y in (3, 5, 7, 9, 11, 13) and 2 <= x <= 13 and (x * 3 + y) % 7 != 0:
        return PAGE[1]
    return PAGE[3] if x < 6 else PAGE[2]


def page_rune(x, y):
    on = (x in (7, 8) and 3 <= y <= 12) or (y in (7, 8) and 3 <= x <= 12) or (abs(x - 7.5) + abs(y - 7.5) in (5.0,))
    if on:
        return '#c8f4ff' if abs(x - 7.5) + abs(y - 7.5) < 3 else '#49b4d9'
    return PAGE[2] if x < 8 else PAGE[1]


T.update({
    'oak': paint(oak), 'oak_edge': paint(oak_edge), 'oak_top': paint(oak_top), 'panel': paint(panel),
    'drawer': paint(drawer), 'leg': paint(leg), 'page': paint(page), 'rune_page': paint(page_rune),
    'leather': paint(lambda x, y: LEA[3] if x + y < 6 else LEA[2] if x + y < 18 else LEA[1]),
    'navy': paint(lambda x, y: NAVY[3] if x + y < 6 else NAVY[2] if x + y < 18 else NAVY[1]),
    'wax': paint(lambda x, y: '#f4ecd6' if x < 6 else '#d8ccae'),
    'flame': paint(lambda x, y: '#fff4c0' if 5 <= x <= 10 and y > 6 else '#ffb040'),
    'ink': paint(lambda x, y: '#3a3a50' if x < 6 else '#1a1a24'),
    'feather': paint(lambda x, y: '#f8f4ea' if x < 8 else '#d8d0c0'),
    'chart': paint(lambda x, y: NAVY[2] if (x + y) % 5 else BR[3]),
    'brass': jar2.T['brass'],
})


def M():
    E = []
    top = {'north': 'oak_edge', 'south': 'oak_edge', 'east': 'oak_edge', 'west': 'oak_edge', 'up': 'oak_top', 'down': 'oak'}
    full = [0, 0, 16, 16]
    # top: main slab + under-moulding
    E.append(box((0, 11.4, 0.6), (16, 13, 15.4), top, uv={k: full for k in ('north', 'south', 'east', 'west')}))
    E.append(box((0.8, 10.8, 1.4), (15.2, 11.4, 14.6), 'oak'))
    # brass corner caps on the top
    for (x0, z0) in ((-0.1, 0.5), (14.6, 0.5), (-0.1, 14.0), (14.6, 14.0)):
        E.append(box((x0, 11.3, z0), (x0 + 1.5, 13.1, z0 + 1.5), 'brass'))
    # apron: front drawer, side panels
    E.append(box((1.2, 8.2, 1.8), (14.8, 10.8, 14.2), 'oak'))
    E.append(box((4.5, 8.4, 14.2), (11.5, 10.6, 14.4), 'drawer', faces=('south',), uv={'south': full}))
    E.append(box((15.0, 8.4, 3.0), (15.1, 10.6, 13.0), 'panel', faces=('east',), uv={'east': full}))
    E.append(box((0.9, 8.4, 3.0), (1.0, 10.6, 13.0), 'panel', faces=('west',), uv={'west': full}))
    # turned legs: foot, waist, bulb, neck, capital
    for (lx, lz) in ((1.4, 2.0), (12.6, 2.0), (1.4, 12.0), (12.6, 12.0)):
        E.append(box((lx, 0, lz), (lx + 2.0, 1.0, lz + 2.0), 'leg'))
        E.append(box((lx + 0.5, 1.0, lz + 0.5), (lx + 1.5, 3.0, lz + 1.5), 'leg'))
        E.append(box((lx + 0.2, 3.0, lz + 0.2), (lx + 1.8, 5.2, lz + 1.8), 'leg'))
        E.append(box((lx + 0.5, 5.2, lz + 0.5), (lx + 1.5, 7.2, lz + 1.5), 'leg'))
        E.append(box((lx + 0.1, 7.2, lz + 0.1), (lx + 1.9, 8.2, lz + 1.9), 'leg'))
    # lower shelf with two books
    E.append(box((2.0, 2.4, 3.0), (14.0, 3.0, 13.0), 'oak'))
    E.append(box((4.0, 3.0, 5.0), (8.5, 4.4, 11.0), 'leather'))
    E.append(box((4.3, 4.4, 5.4), (8.2, 5.4, 10.6), 'navy'))
    # on top: open book (leather cover, two page blocks rising toward the spine)
    E.append(box((3.0, 13.0, 4.2), (12.0, 13.4, 11.4), 'leather'))
    E.append(box((3.4, 13.4, 4.6), (7.3, 14.0, 11.0), 'page'))
    E.append(box((7.7, 13.4, 4.6), (11.6, 14.0, 11.0), {'north': 'page', 'south': 'page', 'east': 'page', 'west': 'page', 'up': 'rune_page', 'down': 'page'}, uv={'up': full}, emissive=True))
    E.append(box((7.2, 13.4, 4.6), (7.8, 14.3, 11.0), 'page'))
    # candle (back-left) with flame
    E.append(box((1.8, 13.0, 1.6), (3.4, 13.5, 3.2), 'brass'))
    E.append(box((2.1, 13.5, 1.9), (3.1, 16.0, 2.9), 'wax'))
    E.append(box((2.4, 16.0, 2.2), (2.8, 17.0, 2.6), 'flame', emissive=True))
    # inkwell + quill (back-right)
    E.append(box((12.6, 13.0, 1.8), (14.4, 14.6, 3.6), 'ink'))
    E.append(box((13.3, 14.6, 2.5), (13.7, 17.5, 2.9), 'feather'))
    E.append(box((12.9, 15.6, 2.6), (14.1, 18.4, 2.8), 'feather'))
    # rolled star chart lying at the front-right
    E.append(box((12.0, 13.0, 11.6), (15.0, 13.8, 12.6), 'chart'))
    E.append(box((11.6, 12.95, 11.5), (12.0, 13.9, 12.7), 'brass'))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=128, scale=0.6, light=True, rot_y=45)
    im.save('icon3d_desk.png')
    big = im.resize((384, 384), Image.NEAREST)
    sheet = Image.new('RGBA', (424, 424), (14, 16, 20, 255))
    sheet.paste(big, (20, 20), big)
    sheet.save('prev_desk2.png')
    print('ok')
