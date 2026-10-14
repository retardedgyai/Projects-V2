"""Primitive distiller: a kiln of rough stacked stones with an arched fire mouth, a
terracotta pot lashed with rope, a reed pipe running down to a clay receiver pot.
Magic shows only as a carved rune glowing in one big stone."""
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


paint = jar4.paint
SK = ['#2c2622', '#433a32', '#5c5046', '#766858', '#92836e']      # weathered field stone (warm grey)
CL = ['#5a2a16', '#83401f', '#a8582a', '#c87640', '#e09a62']      # terracotta
RP = ['#5a4626', '#806838', '#a88e52', '#ccb478']                  # rope
RD = ['#4a5a26', '#6a7c34', '#8ea048', '#b4c46a']                  # reed
SOOT = ['#141010', '#2a1c16']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
T = dict(jar4.T)


def stone(x, y):                  # one rough stone face: lit upper-left lump, darker lower-right
    s = 3
    if x + y < 7:
        s = 4
    elif x + y > 20:
        s = 1
    elif x > 10 or y > 10:
        s = 2
    if (x, y) in ((4, 9), (5, 9), (11, 5), (9, 12)):      # pits
        s = 1
    return SK[s]


def stone_rune(x, y):             # carved rune groove, dark recess
    return stone(x, y)


def rune_glow(x, y):              # angular primitive rune (not a neat meander)
    pts = {(7, 2), (7, 3), (7, 4), (7, 5), (7, 6), (7, 7), (7, 8), (7, 9), (7, 10), (7, 11), (7, 12), (7, 13),
           (8, 4), (9, 5), (10, 6), (6, 4), (5, 5), (4, 6),
           (8, 9), (9, 10), (10, 11), (6, 9), (5, 10), (4, 11)}
    if (x, y) in pts:
        return CY[2] if 5 <= y <= 10 else CY[1]
    return None


def clay(x, y):
    s = 3 if x < 4 else 2 if x < 11 else 1
    if y in (3, 11) and 1 <= x <= 14:          # thumb-pressed ridge lines
        s = max(0, s - 1)
    return CL[s]


def clay_top(x, y):
    d = max(abs(x - 7.5), abs(y - 7.5))
    if d < 3:
        return SOOT[0]
    return CL[3] if x + y < 14 else CL[2]


def rope(x, y):
    return RP[3] if (x + y) % 4 == 0 else RP[2] if (x + y) % 4 == 1 else RP[1]


def reed(x, y):
    if x % 8 == 0:
        return RD[0]                            # node joints
    return RD[3] if y < 5 else RD[2] if y < 11 else RD[1]


def fire(x, y):
    d = abs(x - 7.5) * 0.8 + max(0, 12 - y) * 0.55
    return '#fff4c0' if d < 2.6 else '#ffc048' if d < 4.4 else '#f07020' if d < 6.4 else '#a83010' if d < 8.4 else '#2a0e08'


T.update({
    'stone': paint(stone, rim=(SK[4], SK[0])), 'stone_rune': paint(stone_rune, rim=(SK[4], SK[0])),
    'rune_glow': paint(rune_glow), 'clay': paint(clay, rim=(CL[4], CL[0])), 'clay_top': paint(clay_top, rim=(CL[4], CL[0])),
    'rope': paint(rope), 'reed': paint(reed, rim=(RD[3], RD[0])), 'fire': paint(fire),
    'soot': paint(lambda x, y: SOOT[1] if y > 8 else SOOT[0]),
})
STN = 'stone'


def M():
    full = [0, 0, 16, 16]
    E = []
    # kiln: courses of rough stones of different sizes (no frame), leaving an arched mouth at the front
    stones = [
        ((0.5, 0, 1.0), (5.5, 4.0, 15.0)), ((10.5, 0, 1.0), (15.5, 4.0, 15.0)), ((5.5, 0, 1.0), (10.5, 1.5, 15.0)),
        ((0.0, 4.0, 0.5), (6.0, 7.5, 15.5)), ((10.0, 4.0, 0.5), (16.0, 7.5, 15.5)), ((5.5, 4.0, 0.5), (10.5, 7.5, 11.0)),
        ((0.8, 7.5, 1.2), (8.2, 10.8, 14.8)), ((7.8, 7.5, 1.2), (15.2, 10.8, 14.8)),
        ((2.0, 10.8, 2.5), (14.0, 12.4, 13.5)),
    ]
    for (a, b) in stones:
        E.append(box(a, b, STN, uv={k: full for k in ('north', 'south', 'east', 'west', 'up')}))
    # arched mouth: lintel stone across the opening
    E.append(rotated(box((4.6, 6.2, 12.0), (11.4, 7.8, 15.6), STN, uv={k: full for k in ('south', 'up', 'north')}), 'z', 4, (8, 7, 13)))
    # soot-black inner walls and the fire
    E.append(box((5.5, 1.5, 11.0), (10.5, 6.2, 11.05), 'fire', faces=('south',), uv={'south': full}, emissive=True))
    E.append(box((5.5, 1.5, 11.05), (5.6, 6.2, 15.0), 'soot', faces=('east',)))
    E.append(box((10.4, 1.5, 11.05), (10.5, 6.2, 15.0), 'soot', faces=('west',)))
    # the big rune stone on the left side, with its glowing carving
    E.append(box((15.4, 4.4, 4.0), (16.4, 10.4, 11.0), 'stone_rune', uv={'east': full}))
    E.append(box((16.4, 4.4, 4.0), (16.45, 10.4, 11.0), 'rune_glow', faces=('east',), uv={'east': full}, emissive=True))
    # terracotta pot (octagonal), slightly tilted toward the pipe
    cl = {'north': 'clay', 'south': 'clay', 'east': 'clay', 'west': 'clay', 'up': 'clay_top', 'down': 'clay'}
    pot = [
        box((5.0, 12.4, 5.5), (11.0, 13.4, 10.5), cl),
        box((3.6, 13.4, 4.8), (12.4, 18.6, 11.2), cl, uv={'up': full}),
        box((4.8, 13.4, 3.6), (11.2, 18.6, 12.4), cl, uv={'up': full}),
        box((5.2, 18.6, 5.2), (10.8, 19.8, 10.8), cl, uv={'up': full}),
        box((6.2, 19.8, 6.2), (9.8, 21.6, 9.8), cl, uv={'up': full}),
        box((5.9, 20.2, 5.9), (10.1, 20.9, 10.1), 'rope'),           # rope lashing at the neck
        box((3.4, 16.0, 4.6), (12.6, 16.7, 11.4), 'rope'),
        box((4.6, 16.0, 3.4), (11.4, 16.7, 12.6), 'rope'),
    ]
    E += [rotated(p, 'z', -6, (8, 12.4, 8)) for p in pot]
    # reed pipe from the neck down to the receiver pot on the ground (right side)
    E.append(rotated(box((9.0, 20.4, 7.4), (24.0, 21.6, 8.6), 'reed', uv={k: full for k in ('north', 'south', 'up', 'down')}), 'z', -47, (9.0, 21.0, 8.0)))
    # receiver: small clay pot standing on the ground beside the kiln
    rc = {'north': 'clay', 'south': 'clay', 'east': 'clay', 'west': 'clay', 'up': 'clay_top', 'down': 'clay'}
    E.append(box((18.0, 0, 5.6), (22.0, 4.0, 10.4), rc, uv={'up': full}))
    E.append(box((17.4, 0.8, 6.2), (22.6, 3.2, 9.8), rc))
    E.append(box((18.8, 4.0, 6.8), (21.2, 5.0, 9.2), rc, uv={'up': full}))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=176, scale=0.42, light=True, rot_y=-20, center=(11, 10, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('distiller6.png')
    big = im.resize((528, 528), Image.NEAREST)
    sh = Image.new('RGBA', (568, 568), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_distiller6.png')
    print('ok')
