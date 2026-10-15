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
WD = ['#22180f', '#342518', '#4a3624', '#634a32', '#7e6244']       # old timber, a touch warmer
RING = ['#3a2a1c', '#4e3a28', '#634a32']
HD = ['#2a2420', '#3e342c', '#54483a', '#6c5e4c', '#8c7c64']        # dark fur hide
PG = ['#b8a27a', '#d6c296', '#ecdcb0', '#f8eccc']
LE = ['#3a2416', '#563620', '#6e4a2c']
HB = ['#2e4a1e', '#4a6e2a', '#6e963a', '#98be58']
CY = distiller6.CY
RP = distiller6.RP
T = dict(distiller6.T)


def plank(x, y):                  # rough plank face: grain streaks, lit top band, dark lower edge
    if y == 0:
        return WD[4]
    if y == 15:
        return WD[0]
    if y in (5, 6) and x in (2, 3, 4, 5, 10, 11):
        return WD[2]
    if y == 10 and x in (6, 7, 8, 13, 14):
        return WD[2]
    if (x, y) in ((12, 7), (13, 7), (12, 8)):  # knot
        return WD[1]
    return WD[3] if y < 8 else WD[2]


def plank_top(x, y):
    if x in (0, 15):
        return WD[1]
    if x in (5, 6) and y % 7 != 3:
        return WD[2]
    return WD[4] if x < 4 else WD[3]


def end_grain(x, y):              # board end: dark with two soft rings
    d = ((x - 7.5) ** 2 + ((y - 7.5) * 1.6) ** 2) ** 0.5
    if d > 7.5:
        return WD[1]
    return RING[1] if 3 <= d < 4 or 6 <= d < 7 else RING[0]


def log(x, y):                    # round log side: vertical stripe ramp + bark flecks
    s = 4 if x < 3 else 3 if x < 7 else 2 if x < 11 else 1
    if (x * 5 + y * 3) % 17 == 0:
        s = max(0, s - 2)
    return WD[s]


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
    'end': paint(end_grain), 'log': paint(log, rim=(WD[4], WD[0])), 'hide': paint(hide, rim=(HD[4], HD[0])),
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
    pl = {'north': 'plank', 'south': 'plank', 'up': 'plank_top', 'down': 'plank', 'east': 'end', 'west': 'end'}
    # top: three planks with visible gaps and uneven heights/lengths
    for (a, b) in (((-0.8, 12.6, 0.2), (32.2, 15.0, 5.0)), ((0.2, 12.6, 5.4), (31.4, 14.6, 10.4)), ((-0.4, 12.6, 10.8), (32.6, 14.9, 15.8))):
        E.append(box(a, b, pl, uv={k: full for k in ('north', 'south', 'up', 'east', 'west')}))
    # cleats under the top
    for x0 in (4.0, 26.0):
        E.append(box((x0, 11.6, 0.8), (x0 + 2.0, 12.6, 15.2), 'log'))
    # four thick splayed log legs (octagon-ish = two crossed boxes), lashed crossbars
    for (lx, lz, ax, az) in ((5.0, 3.0, 8, -8), (27.0, 3.0, 8, 8), (5.0, 13.0, -8, -8), (27.0, 13.0, -8, 8)):
        legs = [box((lx - 1.4, 0, lz - 1.0), (lx + 1.4, 12.6, lz + 1.0), 'log', uv={k: full for k in ('north', 'south', 'east', 'west')}),
                box((lx - 1.0, 0, lz - 1.4), (lx + 1.0, 12.6, lz + 1.4), 'log', uv={k: full for k in ('north', 'south', 'east', 'west')})]
        for l in legs:
            l = rotated(l, 'x', ax, (lx, 12.6, lz))
            E.append(rotated(l, 'z', az, (lx, 12.6, lz)) if False else l)
    for z0 in (2.4, 12.4):
        E.append(box((5.0, 4.0, z0), (27.0, 5.4, z0 + 1.2), 'log', uv={k: full for k in ('north', 'south', 'up', 'down')}))
        for xc in (5.0, 27.0):
            E.append(box((xc - 1.6, 3.6, z0 - 0.4), (xc + 1.6, 5.8, z0 + 1.6), 'rope'))
    # fur hide thrown over the right end, hanging down the side
    hd = {'up': 'hide', 'north': 'hide', 'south': 'hide', 'east': 'hide', 'west': 'hide', 'down': 'hide'}
    E.append(box((22.0, 15.0, 1.0), (33.2, 15.3, 15.0), hd, uv={'up': full}))
    E.append(rotated(box((32.9, 8.0, 1.6), (33.2, 15.3, 14.4), 'hide', faces=('east', 'north', 'south'), uv={'east': full}), 'z', -6, (33, 15, 8)))
    # open cord-bound book (hero), page with the rune rising
    book = [
        box((9.0, 15.0, 4.0), (20.0, 15.6, 12.0), 'cover'),
        box((9.5, 15.6, 4.5), (14.4, 16.4, 11.5), 'page', uv={'up': full}),
        box((14.6, 15.6, 4.5), (19.5, 16.4, 11.5), 'page', uv={'up': full}),
        rotated(box((14.6, 16.4, 4.6), (19.3, 16.45, 11.4), {'up': 'rune_page', 'down': 'page'}, faces=('up', 'down'), uv={'up': full}, emissive=True), 'z', 16, (14.6, 16.4, 8)),
        box((14.1, 14.9, 7.4), (14.9, 16.7, 8.6), 'rope'),
    ]
    E += [rotated(b, 'y', -12, (14.5, 15, 8)) for b in book]
    # clay ink pot + quill (left back)
    cl = {'north': 'clay', 'south': 'clay', 'east': 'clay', 'west': 'clay', 'up': 'clay_top', 'down': 'clay'}
    E.append(box((2.6, 15.0, 2.6), (6.0, 17.2, 6.0), cl, uv={'up': full}))
    E.append(rotated(box((-1.4, 16.2, 4.3), (10.6, 28.2, 4.31), 'quill', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}), 'y', 25, (4.3, 16.6, 4.3)))
    # candle on a clay dish (left front)
    E.append(box((3.0, 15.0, 10.0), (6.6, 15.5, 13.6), cl))
    E.append(box((4.2, 15.5, 11.2), (5.4, 19.2, 12.4), 'wax'))
    E.append(box((4.6, 19.2, 11.6), (5.0, 20.4, 12.0), 'flame', emissive=True))
    # carved rune stone resting on the hide
    E.append(rotated(box((24.0, 15.3, 8.0), (29.6, 18.4, 13.4), 'stone_rune', uv={'up': full, 'south': full}), 'y', 15, (26.8, 15.3, 10.7)))
    E.append(rotated(box((24.0, 18.4, 8.0), (29.6, 18.45, 13.4), 'rune_glow', faces=('up',), uv={'up': full}, emissive=True), 'y', 15, (26.8, 15.3, 10.7)))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=192, scale=0.36, light=True, rot_y=35, center=(16, 10, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('desk7.png')
    big = im.resize((576, 576), Image.NEAREST)
    sh = Image.new('RGBA', (616, 616), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_desk7.png')
    print('ok')
