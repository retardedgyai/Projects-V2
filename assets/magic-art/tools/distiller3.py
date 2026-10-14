"""Distiller v3 (Thaumcraft skeleton, ProjectS materials): stone-brick smeltery with a
raised brass frame, a recessed fire mouth behind real grate bars, and an octagonal
alembic with raised brass hoops, a recessed glass window and a side spout.
Accent: a cool cyan rune plate (ProjectS magic light)."""
import random
from PIL import Image
from mcrender import render, box
import tcstyle


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


ST = ['#1c1a22', '#2c2a34', '#3c3946', '#4e4a58', '#635e6e', '#7a7486']   # arcane stone, violet shadows
BR = ['#4a2e0c', '#7a5a18', '#b08a2a', '#dcb848', '#f6e494']
GW = tcstyle.GW
IRON = ['#16161a', '#24242a', '#34343c', '#484852', '#5e5e6a']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
T = dict(tcstyle.T)


def bricks(x, y, r):
    # 4px tall courses, 8px bricks offset per course; each brick lit on top/left, shadowed bottom/right
    row = y // 4
    off = 0 if row % 2 == 0 else 4
    bx = (x + off) % 8
    by = y % 4
    if by == 3 or bx == 7:
        return ST[0]
    if by == 0 or bx == 0:
        return ST[4] if r.random() < 0.85 else ST[3]
    if by == 2 or bx == 6:
        return ST[2]
    return ST[3] if r.random() < 0.7 else ST[2]


T['stone'] = tile(bricks, 1)
T['stone_top'] = tile(lambda x, y, r: ST[0] if (x % 8 == 7 or y % 8 == 7) else (ST[4] if (x % 8 == 0 or y % 8 == 0) else ST[r.choice((3, 3, 2))]), 2)


def brass_bar(x, y, r):          # bevelled brass strip (used for raised frame pieces)
    if x == 0 or y == 0:
        return BR[4]
    if x == 15 or y == 15:
        return BR[1]
    return BR[3] if (x + y) < 10 else BR[2] if (x + y) < 22 else BR[1]


T['brass'] = tile(brass_bar, 3)
T['brass_rivet'] = tile(lambda x, y, r: (BR[4] if (x, y) in ((7, 6), (8, 6)) else BR[0] if (x, y) in ((7, 9), (8, 9)) else BR[3] if (7 <= x <= 8 and 7 <= y <= 8) else None) or brass_bar(x, y, r), 4)


def inner_wall(x, y, r):          # soot-darkened firebox interior, lit from the fire below
    s = 0 if y < 6 else 1 if y < 11 else 2
    return [IRON[0], '#3a1a10', '#6a2a14'][s] if r.random() < 0.8 else IRON[1]


T['firewall'] = tile(inner_wall, 5)


def fire(x, y, r):
    d = abs(x - 7.5) * 0.8 + max(0, 12 - y) * 0.55
    if d < 2.6:
        return '#fff4c0'
    if d < 4.4:
        return '#ffc048'
    if d < 6.4:
        return '#f07020'
    if d < 8.4:
        return '#a83010'
    return '#4a140a'


T['fire'] = tile(fire, 6)
T['iron'] = tile(lambda x, y, r: IRON[4] if x == 0 else IRON[1] if x == 15 else IRON[3] if x < 6 else IRON[2], 7)


def barrel(x, y, r):
    if x % 4 == 0:
        return GW[0]
    s = 3 if x % 4 == 1 else 2 if x % 4 == 2 else 1
    if r.random() < 0.15:
        s = max(1, s - 1)
    return GW[s]


T['barrel'] = tile(barrel, 8)
T['barrel_top'] = tile(lambda x, y, r: BR[2] if (x in (0, 15) or y in (0, 15)) else (IRON[0] if 5 <= x <= 10 and 5 <= y <= 10 else GW[r.choice((2, 3))]), 9)


def window(x, y, r):              # recessed glass window with glowing essentia behind
    if x in (0, 15) or y in (0, 15):
        return BR[1]
    if x == 1 or y == 1:
        return IRON[0]
    d = abs(x - 7.5) + abs(y - 8.5)
    return CY[3] if d < 2 else CY[2] if d < 4 else CY[1] if d < 7 else CY[0]


T['window'] = tile(window, 10)


def rune_plate(x, y, r):          # navy enamel plate with a cyan rune (ProjectS accent)
    if x in (0, 15) or y in (0, 15):
        return BR[2]
    on = (x in (7, 8) and 3 <= y <= 12) or (y in (7, 8) and 4 <= x <= 11) or (abs(x - 7.5) == abs(y - 7.5) and 2.5 < abs(x - 7.5) < 5)
    if on:
        return CY[2] if 5 <= x <= 10 and 5 <= y <= 10 else CY[1]
    return '#1c2a4e' if (x + y) % 5 else '#24345e'


T['rune'] = tile(rune_plate, 11)


def M():
    full = [0, 0, 16, 16]
    sides = ('north', 'south', 'east', 'west', 'up', 'down')
    st = {'north': 'stone', 'south': 'stone', 'east': 'stone', 'west': 'stone', 'up': 'stone_top', 'down': 'stone'}
    E = []
    # stone body, slightly inset so the brass frame stands proud
    E.append(box((0.6, 0.6, 0.6), (15.4, 15.0, 13.2), st))
    # front slab built around the fire mouth (so the recess is really open)
    E.append(box((0.6, 0.6, 13.2), (4.0, 15.0, 15.4), st))
    E.append(box((12.0, 0.6, 13.2), (15.4, 15.0, 15.4), st))
    E.append(box((4.0, 0.6, 13.2), (12.0, 2.2, 15.4), st))
    E.append(box((4.0, 9.0, 13.2), (12.0, 15.0, 15.4), st))
    # brass frame: four corner pillars + base plinth + top rim (raised)
    for (x0, z0) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        E.append(box((x0, 0, z0), (x0 + 2, 15.0, z0 + 2), 'brass'))
    E.append(box((-0.2, 0, -0.2), (16.2, 1.2, 16.2), 'brass'))
    E.append(box((-0.3, 15.0, -0.3), (16.3, 16.0, 1.7), 'brass'))
    E.append(box((-0.3, 15.0, 14.3), (16.3, 16.0, 16.3), 'brass'))
    E.append(box((-0.3, 15.0, 1.7), (1.7, 16.0, 14.3), 'brass'))
    E.append(box((14.3, 15.0, 1.7), (16.3, 16.0, 14.3), 'brass'))
    E.append(box((1.7, 15.0, 1.7), (14.3, 15.6, 14.3), 'stone_top', faces=('up',)))
    # recessed fire mouth on the front (south): back wall with fire, side walls, lintel
    E.append(box((4, 2.2, 13.2), (12, 9.0, 13.25), 'fire', faces=('south',), uv={'south': full}, emissive=True))
    E.append(box((4, 2.2, 13.25), (4.4, 9.0, 15.4), 'firewall', faces=('east',), uv={'east': full}))
    E.append(box((11.6, 2.2, 13.25), (12, 9.0, 15.4), 'firewall', faces=('west',), uv={'west': full}))
    E.append(box((4, 8.6, 13.25), (12, 9.0, 15.4), 'firewall', faces=('down',)))
    # cut the stone in front of the mouth: draw a dark frame around the opening instead of stone
    E.append(box((3.4, 1.6, 15.4), (12.6, 2.2, 15.9), 'brass'))             # sill
    E.append(box((3.4, 9.0, 15.4), (12.6, 10.0, 15.9), 'brass_rivet', uv={'south': full}))  # lintel
    E.append(box((3.4, 2.2, 15.4), (4.0, 9.0, 15.9), 'brass'))
    E.append(box((12.0, 2.2, 15.4), (12.6, 9.0, 15.9), 'brass'))
    # grate bars standing in the opening
    for bx in (6.2, 9.0):
        E.append(box((bx, 2.2, 15.0), (bx + 0.7, 9.0, 15.5), 'iron'))
    # rune plate above the mouth (ProjectS accent)
    E.append(box((5.5, 11.0, 15.4), (10.5, 14.0, 15.6), 'rune', faces=('south',), uv={'south': full}, emissive=True))
    # ---- alembic on top: octagonal barrel with raised hoops ----
    bar = {'north': 'barrel', 'south': 'barrel', 'east': 'barrel', 'west': 'barrel', 'up': 'barrel_top', 'down': 'barrel'}
    E.append(box((3.5, 16.0, 5.0), (12.5, 25.0, 11.0), bar, uv={'up': full}))
    E.append(box((5.0, 16.0, 3.5), (11.0, 25.0, 12.5), bar, uv={'up': full}))
    for hy in (17.0, 23.0):
        E.append(box((3.2, hy, 4.7), (12.8, hy + 1.0, 11.3), 'brass'))
        E.append(box((4.7, hy, 3.2), (11.3, hy + 1.0, 12.8), 'brass'))
    # recessed window on the front of the alembic
    E.append(box((6.2, 18.6, 12.3), (9.8, 22.4, 12.55), 'window', faces=('south',), uv={'south': full}, emissive=True))
    # domed cap + spout to the side
    E.append(box((5.5, 25.0, 5.5), (10.5, 26.2, 10.5), 'brass'))
    E.append(box((7.0, 26.2, 7.0), (9.0, 27.6, 9.0), 'iron'))
    E.append(box((12.8, 21.0, 7.4), (15.6, 22.2, 8.6), 'brass'))
    E.append(box((15.0, 19.4, 7.4), (16.2, 22.2, 8.6), 'brass'))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=160, scale=0.42, light=True, rot_y=45, center=(8, 13, 8))
    im.save('tc_smeltery.png')
    big = im.resize((480, 480), Image.NEAREST)
    sh = Image.new('RGBA', (520, 520), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_smeltery3.png')
    print('ok')
