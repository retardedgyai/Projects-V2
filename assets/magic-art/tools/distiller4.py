"""Distiller v4 in the Anz/YY way: clean flat stone bricks with a light rim, separate brass
frame parts, recessed fire mouth with grate bars, alembic as a vertical-stripe barrel with
raised hoops, a painted-glass window showing essentia, a cyan rune plate."""
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


paint = jar4.paint
ST = ['#2a2834', '#3c3a48', '#524e60', '#6a6578', '#847e92']
WD = ['#2a1810', '#3e2416', '#56321e', '#6e4428', '#8a5a34']
BR = jar4.BR
IR = ['#1a1a20', '#2c2c34', '#40404a', '#585862']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
T = dict(jar4.T)


def bricks(x, y):                  # 2 courses of 8px-wide bricks, each brick lit band on top
    row = y // 4
    off = 0 if row % 2 == 0 else 4
    bx, by = (x + off) % 8, y % 4
    if by == 3 or bx == 7:
        return ST[0]
    if by == 0:
        return ST[4]
    return ST[3] if bx < 3 else ST[2]


def stone_top(x, y):
    if x % 8 == 7 or y % 8 == 7:
        return ST[0]
    return ST[4] if (x % 8 + y % 8) < 6 else ST[3]


def fire(x, y):
    d = abs(x - 7.5) * 0.8 + max(0, 12 - y) * 0.55
    return '#fff4c0' if d < 2.6 else '#ffc048' if d < 4.4 else '#f07020' if d < 6.4 else '#a83010' if d < 8.4 else '#4a140a'


def firewall(x, y):
    return '#5a2414' if y > 10 else '#2a1410' if y > 5 else IR[0]


def barrel(x, y):                  # vertical stripe ramp per stave (Anz)
    st = x % 4
    return WD[4] if st == 0 else WD[3] if st == 1 else WD[2] if st == 2 else WD[1]


def window(x, y):                  # painted glass with essentia, rim in brass
    if x in (0, 15) or y in (0, 15):
        return BR[1]
    if y < 5:
        return '#b4e2ee'
    if y == 5:
        return CY[3]
    return CY[2] if x < 6 else CY[1] if x < 12 else CY[0]


def rune(x, y):
    if x in (0, 15) or y in (0, 15):
        return BR[2]
    on = (x in (7, 8) and 3 <= y <= 12) or (y in (7, 8) and 4 <= x <= 11)
    if on:
        return CY[3] if 6 <= x <= 9 and 6 <= y <= 9 else CY[1]
    return '#1c2a52' if y < 8 else '#16224a'


def iron(x, y):
    return IR[3] if x < 5 else IR[2] if x < 11 else IR[1]


T.update({
    'stone': paint(bricks, rim=(ST[4], ST[0])), 'stone_top': paint(stone_top, rim=(ST[4], ST[1])),
    'fire': paint(fire), 'firewall': paint(firewall),
    'barrel': paint(barrel, rim=(WD[4], WD[0])), 'barrel_top': paint(lambda x, y: IR[0] if 5 <= x <= 10 and 5 <= y <= 10 else WD[3] if x + y < 14 else WD[2], rim=(BR[3], BR[1])),
    'window': paint(window), 'rune': paint(rune), 'iron': paint(iron, rim=(IR[3], IR[0])),
})
BRS = {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}


def M():
    full = [0, 0, 16, 16]
    st = {'north': 'stone', 'south': 'stone', 'east': 'stone', 'west': 'stone', 'up': 'stone_top', 'down': 'stone'}
    E = []
    E.append(box((0.6, 0.6, 0.6), (15.4, 15.0, 13.2), st, uv={k: full for k in st}))
    E.append(box((0.6, 0.6, 13.2), (4.0, 15.0, 15.4), st))
    E.append(box((12.0, 0.6, 13.2), (15.4, 15.0, 15.4), st))
    E.append(box((4.0, 0.6, 13.2), (12.0, 2.2, 15.4), st))
    E.append(box((4.0, 9.0, 13.2), (12.0, 15.0, 15.4), st))
    for (x0, z0) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        E.append(box((x0, 0, z0), (x0 + 2, 15.0, z0 + 2), BRS))
    E.append(box((-0.2, 0, -0.2), (16.2, 1.2, 16.2), BRS))
    for (a, b) in (((-0.3, 15.0, -0.3), (16.3, 16.0, 1.7)), ((-0.3, 15.0, 14.3), (16.3, 16.0, 16.3)),
                   ((-0.3, 15.0, 1.7), (1.7, 16.0, 14.3)), ((14.3, 15.0, 1.7), (16.3, 16.0, 14.3))):
        E.append(box(a, b, BRS))
    E.append(box((1.7, 15.0, 1.7), (14.3, 15.6, 14.3), 'stone_top', faces=('up',)))
    # fire mouth
    E.append(box((4, 2.2, 13.2), (12, 9.0, 13.25), 'fire', faces=('south',), uv={'south': full}, emissive=True))
    E.append(box((4, 2.2, 13.25), (4.4, 9.0, 15.4), 'firewall', faces=('east',), uv={'east': full}))
    E.append(box((11.6, 2.2, 13.25), (12, 9.0, 15.4), 'firewall', faces=('west',), uv={'west': full}))
    E.append(box((3.4, 1.6, 15.4), (12.6, 2.2, 15.9), BRS))
    E.append(box((3.4, 9.0, 15.4), (12.6, 10.0, 15.9), BRS))
    E.append(box((3.4, 2.2, 15.4), (4.0, 9.0, 15.9), BRS))
    E.append(box((12.0, 2.2, 15.4), (12.6, 9.0, 15.9), BRS))
    for bx in (6.2, 9.0):
        E.append(box((bx, 2.2, 15.0), (bx + 0.7, 9.0, 15.5), 'iron'))
    E.append(box((5.5, 11.0, 15.4), (10.5, 14.0, 15.6), 'rune', faces=('south',), uv={'south': full}, emissive=True))
    # alembic: octagonal barrel with raised hoops
    bar = {'north': 'barrel', 'south': 'barrel', 'east': 'barrel', 'west': 'barrel', 'up': 'barrel_top', 'down': 'barrel'}
    E.append(box((3.5, 16.0, 5.0), (12.5, 25.0, 11.0), bar, uv={'up': full}))
    E.append(box((5.0, 16.0, 3.5), (11.0, 25.0, 12.5), bar, uv={'up': full}))
    for hy in (17.0, 23.0):
        E.append(box((3.2, hy, 4.7), (12.8, hy + 1.0, 11.3), BRS))
        E.append(box((4.7, hy, 3.2), (11.3, hy + 1.0, 12.8), BRS))
    E.append(box((6.2, 18.6, 12.3), (9.8, 22.4, 12.55), 'window', faces=('south',), uv={'south': full}, emissive=True))
    E.append(box((5.5, 25.0, 5.5), (10.5, 26.2, 10.5), BRS))
    E.append(box((7.0, 26.2, 7.0), (9.0, 27.6, 9.0), 'iron'))
    # spout, slightly tilted downward
    E.append(rotated(box((12.6, 21.0, 7.4), (16.4, 22.2, 8.6), BRS), 'z', -22.5, (12.6, 21.6, 8)))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=160, scale=0.42, light=True, rot_y=45, center=(8, 13, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('distiller4.png')
    big = im.resize((480, 480), Image.NEAREST)
    sh = Image.new('RGBA', (520, 520), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_distiller4.png')
    print('ok')
