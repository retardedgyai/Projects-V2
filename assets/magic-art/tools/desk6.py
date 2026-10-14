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
WD = ['#2e1c12', '#46301e', '#62442a', '#7e5a36', '#9c7446']       # weathered timber
RING = ['#7e5a36', '#a88456', '#c8a676']
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


def end_grain(x, y):              # log / board end with growth rings
    d = max(abs(x - 7.5), abs(y - 7.5) * 1.3)
    if d > 7:
        return WD[1]
    return RING[int(d) % 3]


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
    # top: three uneven planks (slightly different lengths/heights), end grain at the ends
    planks = [((-0.6, 11.0, 0.2), (32.4, 13.4, 5.4)), ((0.4, 11.0, 5.4), (31.6, 13.0, 10.6)), ((-0.2, 11.0, 10.6), (32.8, 13.3, 15.8))]
    for (a, b) in planks:
        E.append(box(a, b, {'north': 'plank', 'south': 'plank', 'up': 'plank_top', 'down': 'plank', 'east': 'end', 'west': 'end'},
                      uv={k: full for k in ('north', 'south', 'up', 'east', 'west')}))
    # cross battens under the top
    for x0 in (3.0, 27.0):
        E.append(box((x0, 9.8, 0.6), (x0 + 2.0, 11.0, 15.4), 'log'))
    # trestles: crossed logs (X) at each end, lashed with rope where they cross
    for xc in (4.0, 28.0):
        for ang in (22, -22):
            E.append(rotated(box((xc - 1.0, 0, 6.8), (xc + 1.0, 11.6, 9.2), 'log', uv={k: full for k in ('north', 'south', 'east', 'west')}), 'x', ang, (xc, 5.6, 8.0)))
        E.append(box((xc - 1.3, 4.6, 6.5), (xc + 1.3, 6.4, 9.5), 'rope'))
    # long stretcher log between the trestles
    E.append(box((4.0, 5.0, 7.2), (28.0, 6.6, 8.8), 'log', uv={k: full for k in ('north', 'south', 'up', 'down')}))
    # animal hide thrown over the middle, hanging over the front edge a little
    E.append(rotated(box((9.0, 13.4, 1.0), (23.0, 13.7, 15.0), {'up': 'hide', 'north': 'hide', 'south': 'hide', 'east': 'hide', 'west': 'hide', 'down': 'hide'}, uv={'up': full}), 'y', 6, (16, 13.5, 8)))
    E.append(rotated(box((10.0, 10.6, 15.6), (21.0, 13.7, 15.9), 'hide', faces=('south', 'east', 'west'), uv={'south': full}), 'y', 6, (16, 13.5, 8)))
    # cord-bound book, open, page with the glowing rune rising
    book = [
        box((11.0, 13.7, 4.0), (21.0, 14.3, 12.0), 'cover'),
        box((11.5, 14.3, 4.5), (15.9, 15.1, 11.5), 'page', uv={'up': full}),
        box((16.1, 14.3, 4.5), (20.5, 15.1, 11.5), 'page', uv={'up': full}),
        rotated(box((16.1, 15.1, 4.6), (20.3, 15.15, 11.4), {'up': 'rune_page', 'down': 'page'}, faces=('up', 'down'), uv={'up': full}, emissive=True), 'z', 14, (16.1, 15.1, 8)),
        box((15.6, 13.6, 7.4), (16.4, 15.4, 8.6), 'rope'),
    ]
    E += [rotated(b, 'y', -14, (16, 14, 8)) for b in book]
    # clay ink pot + quill (left)
    cl = {'north': 'clay', 'south': 'clay', 'east': 'clay', 'west': 'clay', 'up': 'clay_top', 'down': 'clay'}
    E.append(box((3.0, 13.4, 3.0), (6.4, 15.6, 6.4), cl, uv={'up': full}))
    E.append(rotated(box((-1.0, 14.6, 4.7), (11.0, 26.6, 4.71), 'quill', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}), 'y', 25, (4.7, 15, 4.7)))
    # candle on a clay dish (left front)
    E.append(box((5.4, 13.4, 9.6), (8.6, 13.9, 12.8), cl))
    E.append(box((6.4, 13.9, 10.6), (7.6, 17.4, 11.8), 'wax'))
    E.append(box((6.8, 17.4, 11.0), (7.2, 18.6, 11.4), 'flame', emissive=True))
    # bundle of herbs tied with rope (right back)
    E.append(rotated(box((24.0, 13.4, 2.4), (30.0, 15.2, 5.2), 'herb', uv={k: full for k in ('north', 'south', 'up')}), 'y', -18, (27, 13.4, 3.8)))
    E.append(rotated(box((25.6, 13.3, 2.2), (26.4, 15.4, 5.4), 'rope'), 'y', -18, (27, 13.4, 3.8)))
    # small carved rune stone (right front)
    E.append(box((25.0, 13.4, 10.0), (28.4, 15.6, 13.2), 'stone_rune', uv={'up': full, 'south': full}))
    E.append(box((25.0, 15.6, 10.0), (28.4, 15.65, 13.2), 'rune_glow', faces=('up',), uv={'up': full}, emissive=True))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=192, scale=0.36, light=True, rot_y=35, center=(16, 11, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('desk6.png')
    big = im.resize((576, 576), Image.NEAREST)
    sh = Image.new('RGBA', (616, 616), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_desk6.png')
    print('ok')
