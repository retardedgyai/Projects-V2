"""Research table v4 (two blocks) in the Anz/YY way: big flat colour masses, a 1px light
rim on every face, vertical stripe ramps on legs, separate brass trim parts, and small
objects tilted (book, quill, scroll) so the composition isn't grid-rigid."""
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


paint = jar4.paint
WD = ['#2a1810', '#3e2416', '#56321e', '#6e4428', '#8a5a34']        # greatwood: clean 5-tone ramp
LE = ['#5a2418', '#7a3222', '#98442e', '#b25a3c']
NV = ['#141c3c', '#1e2c5a', '#2c3e7a', '#40569c']
BR = jar4.BR
PG = ['#c8b488', '#e2d0a4', '#f4e6c0', '#fff6dc']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
T = dict(jar4.T)


def planks(x, y):                  # two wide boards: lit top band, mid, dark seam
    ry = y % 8
    if ry == 7:
        return WD[0]
    if ry <= 1:
        return WD[4]
    return WD[3] if ry < 5 else WD[2]


def leg(x, y):                     # vertical stripe ramp (Anz)
    return WD[4] if x < 4 else WD[3] if x < 9 else WD[2] if x < 13 else WD[1]


def top(x, y):                     # leather inset inside a wooden frame
    if x <= 1 or y <= 1:
        return WD[4]
    if x >= 14 or y >= 14:
        return WD[2]
    return LE[3] if x + y < 12 else LE[2] if x + y < 20 else LE[1]


def drawer(x, y):
    if 6 <= x <= 9 and 6 <= y <= 9:
        return BR[3] if y < 8 else BR[1]
    return WD[3] if y < 8 else WD[2]


def cloth(x, y):
    if y in (2, 13):
        return BR[2]
    return NV[3] if y < 5 else NV[2] if y < 11 else NV[1]


def cloth_hang(x, y):
    if x in (2, 13):
        return BR[2]
    return NV[2] if y < 8 else NV[1]


def page(x, y):
    if y in (4, 7, 10) and 3 <= x <= 12:
        return PG[0]
    return PG[3] if x < 6 else PG[2]


def rune_page(x, y):
    d = abs(x - 7.5) + abs(y - 7.5)
    if (x in (7, 8) and 3 <= y <= 12) or (y in (7, 8) and 3 <= x <= 12):
        return CY[3] if d < 2 else CY[2] if d < 4 else CY[1]
    if 4.5 <= d < 5.5:
        return CY[1]
    return PG[2] if x < 8 else PG[1]


def cover(x, y):
    return LE[2] if x + y < 14 else LE[1]


def scroll(x, y):
    if x in (0, 1, 14, 15):
        return BR[2]
    return PG[3] if y < 5 else PG[2] if y < 11 else PG[1]


def ink(x, y):
    return '#3a4a5e' if x < 6 else '#222c3a'


def quill(x, y):
    return None


T.update({
    'planks': paint(planks, rim=(WD[4], WD[0])), 'leg': paint(leg, rim=(WD[4], WD[0])),
    'top': paint(top), 'drawer': paint(drawer, rim=(WD[4], WD[0])),
    'cloth': paint(cloth, rim=(NV[3], NV[0])), 'cloth_hang': paint(cloth_hang, rim=(NV[3], NV[0])),
    'page': paint(page, rim=(PG[3], PG[0])), 'rune_page': paint(rune_page),
    'cover': paint(cover, rim=(LE[3], LE[0])), 'scroll': paint(scroll, rim=(PG[3], PG[0])),
    'ink': paint(ink, rim=('#5a6e84', '#121820')),
})
# flat quill sprite (white vane, gold shaft)
q = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
qp = q.load()
for i in range(12):
    x, y = 13 - i, 1 + i
    qp[x, y] = hx('#c8a040')
    if 1 <= i <= 9:
        qp[x - 1, y] = hx('#f4f0e6')
        qp[x + 1, y] = hx('#d8d2c4')
        if 3 <= i <= 7:
            qp[x - 2, y] = hx('#f4f0e6')
T['quill'] = q
T['wax'] = paint(lambda x, y: '#fbf2da' if x < 6 else '#e6d8b8', rim=('#fffaf0', '#c8b890'))
T['flame'] = paint(lambda x, y: '#fff6c8' if 5 <= x <= 10 and y > 5 else '#ffb848')
T['inkglass'] = paint(lambda x, y: '#2a3a5c' if y > 5 else '#86b8cc', rim=('#c4e4ee', '#18202e'))
T['band'] = paint(lambda x, y: jar4.BR[3] if y < 8 else jar4.BR[2], rim=(jar4.BR[4], jar4.BR[0]))


def M():
    full = [0, 0, 16, 16]
    W = {'north': 'planks', 'south': 'planks', 'east': 'planks', 'west': 'planks', 'up': 'top', 'down': 'planks'}
    E = []
    for x0 in (0, 16):
        E.append(box((x0, 11, 0), (x0 + 16, 15, 16), W, uv={k: full for k in W}))
    # brass corner caps + front edge strip (separate trim parts)
    for (x0, z0) in ((-0.3, -0.3), (30.3, -0.3), (-0.3, 14.3), (30.3, 14.3)):
        E.append(box((x0, 10.7, z0), (x0 + 2, 15.3, z0 + 2), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}))
    E.append(box((1.7, 10.7, 15.9), (30.3, 11.3, 16.2), 'brass'))
    # apron + drawers
    E.append(box((1.0, 8.5, 1.0), (31.0, 11.0, 15.0), 'planks', uv={k: full for k in ('north', 'south', 'east', 'west')}))
    for dx in (4.0, 20.0):
        E.append(box((dx, 8.7, 15.0), (dx + 8.0, 10.8, 15.8), 'drawer', faces=('south', 'up', 'east', 'west'), uv={'south': full}))
        E.append(box((dx + 3.2, 9.3, 15.8), (dx + 4.8, 10.1, 16.4), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}))
    # legs + stretcher
    for (lx, lz) in ((1.0, 1.0), (27.0, 1.0), (1.0, 11.0), (27.0, 11.0)):
        E.append(box((lx, 1.2, lz), (lx + 4.0, 8.5, lz + 4.0), 'leg', uv={k: full for k in ('north', 'south', 'east', 'west')}))
        E.append(box((lx - 0.3, 0, lz - 0.3), (lx + 4.3, 1.2, lz + 4.3), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}))
        E.append(box((lx - 0.2, 6.0, lz - 0.2), (lx + 4.2, 6.8, lz + 4.2), 'band'))
    E.append(box((5.0, 2.0, 7.0), (27.0, 3.5, 9.0), 'planks'))
    # navy runner + drape
    E.append(box((10.0, 15.0, 0.0), (22.0, 15.4, 16.0), {'up': 'cloth', 'north': 'cloth_hang', 'south': 'cloth_hang', 'east': 'cloth_hang', 'west': 'cloth_hang', 'down': 'cloth_hang'}, uv={'up': full}))
    E.append(box((10.0, 11.0, 16.0), (22.0, 15.4, 16.4), 'cloth_hang', faces=('south', 'east', 'west'), uv={'south': full}))
    # open grimoire, slightly turned on the cloth
    book = [
        box((11.0, 15.4, 4.0), (21.0, 16.0, 12.0), 'cover'),
        box((11.5, 16.0, 4.5), (15.9, 16.9, 11.5), 'page', uv={'up': full}),
        box((16.1, 16.0, 4.5), (20.5, 16.9, 11.5), {'up': 'rune_page', 'north': 'page', 'south': 'page', 'east': 'page', 'west': 'page', 'down': 'page'}, uv={'up': full}, emissive=True),
    ]
    leaves = [rotated(box((16.1, 16.9, 4.6), (20.3, 16.95, 11.4), 'page', faces=('up', 'down'), uv={'up': full}), 'z', 26, (16.1, 16.9, 8)),
              rotated(box((16.1, 16.9, 4.6), (20.3, 16.95, 11.4), {'up': 'rune_page', 'down': 'page'}, faces=('up', 'down'), uv={'up': full}, emissive=True), 'z', 12, (16.1, 16.9, 8))]
    E += [rotated(b, 'y', -12, (16, 16, 8)) for b in book]
    E += [rotated(l, 'y', -12, (16, 16, 8)) for l in leaves]
    # inkwell + tilted quill
    E.append(box((3.0, 15.0, 3.0), (6.0, 17.4, 6.0), 'inkglass'))
    E.append(box((3.6, 17.4, 3.6), (5.4, 18.0, 5.4), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}))
    E.append(box((6.5, 15.0, 9.0), (8.5, 15.6, 11.0), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}))
    E.append(box((7.0, 15.6, 9.5), (8.0, 19.0, 10.5), 'wax'))
    E.append(box((7.25, 19.0, 9.75), (7.75, 20.2, 10.25), 'flame', emissive=True))
    E.append(rotated(box((-1.0, 15.6, 4.5), (11.0, 27.6, 4.51), 'quill', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}), 'y', 25, (4.5, 16, 4.5)))
    # rolled scroll lying tilted at the front right
    E.append(rotated(box((23.0, 15.0, 10.0), (30.0, 16.6, 11.6), 'scroll', uv={'north': full, 'south': full}), 'y', -20, (26.5, 15, 10.8)))
    # a small essentia jar (reusing jar parts, scaled down)
    E.append(box((25.5, 15.0, 2.5), (29.5, 19.5, 6.5), {k: 'glass_aqua' for k in ('north', 'south', 'east', 'west')} | {'up': 'glass_top', 'down': 'glass_top'}, uv={k: full for k in ('north', 'south', 'east', 'west')}))
    E.append(box((26.2, 19.5, 3.2), (28.8, 20.8, 5.8), {'north': 'cork', 'south': 'cork', 'east': 'cork', 'west': 'cork', 'up': 'cork_top', 'down': 'cork'}))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=192, scale=0.36, light=True, rot_y=35, center=(16, 12, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('desk5.png')
    big = im.resize((576, 576), Image.NEAREST)
    sh = Image.new('RGBA', (616, 616), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_desk5.png')
    print('ok')
