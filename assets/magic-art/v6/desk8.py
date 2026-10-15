"""Primitive research table (two blocks): uneven thick planks with end-grain rings on a
trestle of crossed logs lashed with rope; an animal hide instead of cloth; a cord-bound
book with a glowing rune page, a clay ink pot + quill, a candle on a clay dish, a bundle
of herbs and a small carved rune stone."""
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4
import distiller6


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


paint = jar4.paint
WD = ['#1e120c', '#2e1c12', '#43291a', '#5a3822', '#74492c']       # greatwood: dark, clean, warm
RING = ['#2e1c12', '#43291a', '#5a3822']
HD = ['#2a2420', '#3e342c', '#54483a', '#6c5e4c', '#8c7c64']        # dark fur hide
PG = ['#b8a27a', '#d6c296', '#ecdcb0', '#f8eccc']
LE = ['#3a2416', '#563620', '#6e4a2c']
HB = ['#2e4a1e', '#4a6e2a', '#6e963a', '#98be58']
CY = distiller6.CY
RP = distiller6.RP
T = dict(distiller6.T)


def plank(x, y):                  # clean board face: lit top band, one grain line, dark seam
    ry = y % 8
    if ry == 7:
        return WD[0]
    if ry == 0:
        return WD[4]
    if ry == 4 and x % 7 in (1, 2, 3):
        return WD[2]
    return WD[3] if ry < 4 else WD[2]


def plank_top(x, y):
    return WD[4] if y < 2 else WD[3]


def end_grain(x, y):              # board end: dark with two soft rings
    d = ((x - 7.5) ** 2 + ((y - 7.5) * 1.6) ** 2) ** 0.5
    if d > 7.5:
        return WD[1]
    return RING[1] if 3 <= d < 4 or 6 <= d < 7 else RING[0]


def log(x, y):                    # squared timber: clean vertical stripe ramp
    return WD[4] if x < 3 else WD[3] if x < 8 else WD[2] if x < 13 else WD[1]


LT = ['#3a1c14', '#52281c', '#6a3626', '#824632']


def leather(x, y):
    if x <= 1 or y <= 1:
        return WD[4]
    if x >= 14 or y >= 14:
        return WD[1]
    return LT[3] if x + y < 10 else LT[2] if x + y < 20 else LT[1]


def hide(x, y):                   # fur: short diagonal strands, lighter fringe at the edges
    if x in (0, 15) or y in (0, 15):
        return HD[4]
    s = 3 if x + y < 12 else 2 if x + y < 22 else 1
    if (x - y) % 4 == 0:
        s = min(4, s + 1)
    return HD[s]


def page(x, y):
    if y in (4, 7, 10) and 3 <= x <= 12 and (x + y) % 5:
        return PG[0]
    return PG[3] if x < 6 else PG[2]


def rune_page(x, y):
    pts = distiller6.rune_glow(x, y)
    if pts:
        return pts
    return PG[2] if x < 8 else PG[1]


def herb(x, y):
    return HB[3] if (x + y) % 5 == 0 else HB[2] if x < 8 else HB[1]


T.update({
    'plank': paint(plank, rim=(WD[4], WD[0])), 'plank_top': paint(plank_top, rim=(WD[4], WD[1])),
    'end': paint(end_grain), 'leather': paint(leather),
    'ink': paint(lambda x, y: '#3a4a5e' if x < 6 else '#222c3a', rim=('#5a6e84', '#121820')), 'log': paint(log, rim=(WD[4], WD[0])), 'hide': paint(hide, rim=(HD[4], HD[0])),
    'page': paint(page, rim=(PG[3], PG[0])), 'rune_page': paint(rune_page),
    'cover': paint(lambda x, y: LE[2] if x + y < 14 else LE[1], rim=(LE[2], LE[0])),
    'herb': paint(herb, rim=(HB[3], HB[0])),
    'wax': paint(lambda x, y: '#efe2c2' if x < 6 else '#d6c6a0', rim=('#fff6e0', '#b8a47c')),
    'flame': paint(lambda x, y: '#fff6c8' if 5 <= x <= 10 and y > 5 else '#ffb848'),
})
q = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
qp = q.load()
for i in range(12):
    x, y = 13 - i, 1 + i
    qp[x, y] = hx('#8a7a5a')
    if 1 <= i <= 9:
        qp[x - 1, y] = hx('#ece6d8')
        qp[x + 1, y] = hx('#c8c0ae')
T['quill'] = q


def M():
    full = [0, 0, 16, 16]
    E = []
    side = {'north': 'plank', 'south': 'plank', 'east': 'plank', 'west': 'plank', 'up': 'leather', 'down': 'plank'}
    # thick top made of two blocks, a leather work surface inset in each, a slim overhanging lip
    for x0 in (0, 16):
        E.append(box((x0, 11.0, 0), (x0 + 16, 15.0, 16), side, uv={k: full for k in side}))
    E.append(box((-0.4, 14.6, -0.4), (32.4, 15.2, 16.4), {'north': 'plank', 'south': 'plank', 'east': 'plank', 'west': 'plank', 'up': 'plank_top', 'down': 'plank'}, faces=('north', 'south', 'east', 'west', 'down')))
    # chunky square legs with a small foot, and a stretcher
    for (lx, lz) in ((1.0, 1.0), (27.0, 1.0), (1.0, 11.0), (27.0, 11.0)):
        E.append(box((lx, 1.0, lz), (lx + 4.0, 11.0, lz + 4.0), 'log', uv={k: full for k in ('north', 'south', 'east', 'west')}))
        E.append(box((lx - 0.4, 0, lz - 0.4), (lx + 4.4, 1.0, lz + 4.4), 'log'))
    E.append(box((5.0, 3.0, 7.0), (27.0, 4.6, 9.0), 'log'))
    # open book (hero) on the left block, rune page rising
    book = [
        box((7.0, 15.0, 4.0), (18.0, 15.6, 12.0), 'cover'),
        box((7.5, 15.6, 4.5), (12.4, 16.4, 11.5), 'page', uv={'up': full}),
        box((12.6, 15.6, 4.5), (17.5, 16.4, 11.5), 'page', uv={'up': full}),
        rotated(box((12.6, 16.4, 4.6), (17.3, 16.45, 11.4), {'up': 'rune_page', 'down': 'page'}, faces=('up', 'down'), uv={'up': full}, emissive=True), 'z', 16, (12.6, 16.4, 8)),
    ]
    E += [rotated(b, 'y', -12, (12.5, 15, 8)) for b in book]
    # inkwell (dark glass) + quill, left back
    E.append(box((2.4, 15.0, 2.4), (5.6, 17.2, 5.6), 'ink'))
    E.append(rotated(box((-1.6, 16.2, 4.0), (10.4, 28.2, 4.01), 'quill', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}), 'y', 25, (4.0, 16.6, 4.0)))
    # candle on a dish, left front
    E.append(box((2.6, 15.0, 10.4), (6.2, 15.5, 14.0), 'clay'))
    E.append(box((3.8, 15.5, 11.6), (5.0, 19.2, 12.8), 'wax'))
    E.append(box((4.2, 19.2, 12.0), (4.6, 20.4, 12.4), 'flame', emissive=True))
    # right block: a few research materials laid out (herb bundle, rune stone, stacked notes)
    # a small essentia jar (same glass as the placed jar)
    E.append(box((21.0, 15.0, 2.2), (25.0, 19.6, 6.2), {k: 'glass_aqua' for k in ('north', 'south', 'east', 'west')} | {'up': 'glass_top', 'down': 'glass_top'}, uv={k: full for k in ('north', 'south', 'east', 'west')}))
    E.append(box((21.7, 19.6, 2.9), (24.3, 20.8, 5.5), {'north': 'cork', 'south': 'cork', 'east': 'cork', 'west': 'cork', 'up': 'cork_top', 'down': 'cork'}))
    E.append(rotated(box((24.0, 15.0, 8.6), (29.2, 17.8, 13.4), 'stone_rune', uv={'up': full, 'south': full}), 'y', 15, (26.6, 15, 11)))
    E.append(rotated(box((24.0, 17.8, 8.6), (29.2, 17.85, 13.4), 'rune_glow', faces=('up',), uv={'up': full}, emissive=True), 'y', 15, (26.6, 15, 11)))
    E.append(rotated(box((19.0, 15.0, 9.0), (23.6, 15.8, 14.0), 'page', uv={'up': full}), 'y', -8, (21.3, 15, 11.5)))
    E.append(rotated(box((19.4, 15.8, 9.3), (24.0, 16.4, 14.3), 'page', uv={'up': full}), 'y', 6, (21.7, 15.8, 11.8)))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=192, scale=0.36, light=True, rot_y=35, center=(16, 10.5, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('desk8.png')
    big = im.resize((576, 576), Image.NEAREST)
    sh = Image.new('RGBA', (616, 616), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_desk8.png')
    print('ok')
