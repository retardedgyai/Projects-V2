"""Distiller v2: stone furnace with iron grate and fire, octagonal hammered-copper
pot with brass straps, swan-neck pipe with brass collars, glass receiver."""
from PIL import Image
from mcrender import render, box
import jar2


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def canvas():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


CU = ['#3b1d10', '#6e3820', '#a45e34', '#d48a52', '#f6be88']
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
ST = ['#1e1c22', '#36343c', '#504c56', '#6c6672', '#8a8490']   # dark stone, slight violet shadow
IRON = ['#121418', '#262a30', '#3e444c', '#5c646e']
T = dict(jar2.T)


def paint(fn):
    im = canvas()
    p = im.load()
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c:
                p[x, y] = hx(c) if isinstance(c, str) else c
    return im


# hammered copper: base gradient + scattered dimples (dark pixel with a light pixel above-left)
dimples = [(3, 4), (9, 2), (12, 8), (5, 11), (10, 13), (14, 3), (1, 9), (7, 7)]


def copper(x, y):
    s = 3 if y < 5 else 2 if y < 11 else 1
    if x < 2:
        s = min(4, s + 1)
    for (dx, dy) in dimples:
        if (x, y) == (dx, dy):
            return CU[max(0, s - 1)]
        if (x, y) == (dx - 1, dy - 1):
            return CU[min(4, s + 1)]
    return CU[s]


T['copper'] = paint(copper)
T['copper_top'] = paint(lambda x, y: CU[4] if x + y < 8 else CU[3] if x + y < 18 else CU[2])


def strap(x, y):
    s = 3 if y < 4 else 2 if y < 12 else 1
    if y == 0:
        s = 4
    if y == 15:
        s = 0
    if x % 5 == 2 and 5 <= y <= 9:          # rivets
        return BR[4] if y == 6 else BR[0] if y == 8 else BR[3]
    return BR[s]


T['strap'] = paint(strap)


def stone(x, y):
    row = y // 5
    off = 0 if row % 2 == 0 else 5
    if y % 5 == 4 or (x + off) % 10 == 0:
        return ST[0]
    s = 3 if y % 5 == 0 else 2
    if (x + off) % 10 == 1:
        s = 3
    if (x * 3 + y * 7) % 13 == 0:
        s = 1
    return ST[s]


T['stone'] = paint(stone)
T['stone_top'] = paint(lambda x, y: ST[0] if (x % 8 == 7 or y % 8 == 7) else ST[3] if (x + y) % 8 < 5 else ST[2])


def grate(x, y):
    # iron frame with vertical bars; fire behind the bars (bars opaque, gaps = fire)
    if x in (0, 15) or y in (0, 15):
        return IRON[3] if (x == 0 or y == 0) else IRON[1]
    if x % 3 == 0:
        return IRON[2] if y < 8 else IRON[1]
    d = abs(x - 7.5) + max(0, 9 - y) * 0.8
    if d < 2.5:
        return '#fff0b0'
    if d < 4.5:
        return '#ffb040'
    if d < 7:
        return '#e05a1c'
    return '#7a1e12'


T['grate'] = paint(grate)
T['iron'] = paint(lambda x, y: IRON[3] if x + y < 6 else IRON[2] if x + y < 18 else IRON[1])
T['brass'] = jar2.T['brass']


def M():
    E = []
    cu = {'north': 'copper', 'south': 'copper', 'east': 'copper', 'west': 'copper', 'up': 'copper_top', 'down': 'copper'}
    st = {'north': 'stone', 'south': 'stone', 'east': 'stone', 'west': 'stone', 'up': 'stone_top', 'down': 'stone'}
    # furnace: plinth + body + cap ledge
    E.append(box((2, 0, 2.5), (15, 1, 13.5), st))
    E.append(box((2.5, 1, 3), (14.5, 6.5, 13), st))
    E.append(box((2, 6.5, 2.5), (15, 7.5, 13.5), 'iron'))
    E.append(box((6, 1.4, 12.98), (11, 5.6, 13.1), 'grate', faces=('south',), uv={'south': [0, 0, 16, 16]}, emissive=True))
    # copper pot (octagonal, round-bellied): foot ring, belly (crossed), shoulder, dome
    E.append(box((6, 7.5, 6), (11, 8.3, 10), cu))
    E.append(box((4.6, 8.3, 5.4), (12.4, 11.7, 10.6), cu))
    E.append(box((5.4, 8.3, 4.6), (11.6, 11.7, 11.4), cu))
    E.append(box((5.0, 11.7, 5.8), (12.0, 12.3, 10.2), cu))
    E.append(box((5.8, 11.7, 5.0), (11.2, 12.3, 11.0), cu))
    E.append(box((5.8, 12.3, 6.4), (11.2, 12.9, 9.6), cu))
    E.append(box((6.4, 12.3, 5.8), (10.6, 12.9, 10.2), cu))
    E.append(box((6.8, 12.9, 6.8), (10.2, 13.5, 9.2), cu))
    # brass straps around the belly
    E.append(box((4.4, 9.4, 5.2), (12.6, 10.4, 10.8), 'strap'))
    E.append(box((5.2, 9.4, 4.4), (11.8, 10.4, 11.6), 'strap'))
    # neck
    E.append(box((7.8, 13.5, 7.3), (9.2, 15.2, 8.7), cu))
    E.append(box((7.5, 15.2, 7.0), (9.5, 15.8, 9.0), 'brass'))
    # swan-neck pipe going left and down to the receiver
    E.append(box((2.2, 15.0, 7.5), (7.6, 15.9, 8.5), cu))
    E.append(box((4.6, 14.8, 7.3), (5.4, 16.1, 8.7), 'brass'))            # collar
    E.append(box((1.6, 9.8, 7.5), (2.5, 15.9, 8.5), cu))
    E.append(box((1.4, 12.0, 7.3), (2.7, 12.8, 8.7), 'brass'))            # collar
    # receiver flask (glass + essentia) on an iron foot at the left
    L = {'north': 'aqua', 'south': 'aqua', 'east': 'aqua', 'west': 'aqua', 'up': 'aqua_top', 'down': 'aqua'}
    E.append(box((-2.4, 0, 5.6), (1.4, 0.8, 10.4), 'iron'))
    E.append(box((-1.8, 1.2, 6.6), (0.8, 4.4, 9.4), L, emissive=True))
    E.append(box((-2.2, 0.8, 6.2), (1.2, 7.0, 9.8), 'glass'))
    E.append(box((-1.0, 7.0, 7.4), (0.0, 9.9, 8.6), 'glass'))
    E.append(box((-1.2, 9.6, 7.3), (2.4, 10.4, 8.7), 'brass'))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=128, scale=0.66, light=True, rot_y=45)
    im.save('icon3d_distiller.png')
    big = im.resize((384, 384), Image.NEAREST)
    sheet = Image.new('RGBA', (424, 424), (14, 16, 20, 255))
    sheet.paste(big, (20, 20), big)
    sheet.save('prev_distiller2.png')
    print('ok')
