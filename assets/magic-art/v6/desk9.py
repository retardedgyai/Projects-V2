"""Research table v9, close to the Thaumcraft 4 research table: near-black greatwood, a thick
top whose face carries a raised frame around a lower panel, a deep apron with wooden pegs,
short chunky square legs set in from the corners, a broad low stretcher board.
On top: a stack of research notes, the open grimoire with its rising rune page, a stone-based
inkwell with an upright quill, and a small essentia jar."""
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4
import desk8


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


paint = jar4.paint
WD = ['#140b0c', '#201314', '#2e1c1c', '#3e2925', '#523630', '#68463c']   # TC greatwood: almost black, faint plum
IS = ['#3a4048', '#56606a', '#76828c', '#9aa6ae']                        # inkwell stone base
PG = desk8.PG
T = dict(desk8.T)


def board(x, y):                  # apron / stretcher face: lit top band, one grain line, dark foot
    if y == 0:
        return WD[5]
    if y == 15:
        return WD[0]
    if y == 6 and x % 9 in (2, 3, 4, 5):
        return WD[2]
    if y == 11 and x % 7 in (1, 2, 3):
        return WD[2]
    return WD[3] if y < 8 else WD[2]


def panel(x, y):                  # lowered top panel: flat mass, lit toward the back-left
    return WD[3] if x + y < 14 else WD[2]


def frame(x, y):                  # raised frame rail: one flat mass (rim does the edge)
    return WD[4]


def leg(x, y):                    # squared leg: vertical stripe ramp
    return WD[4] if x < 4 else WD[3] if x < 9 else WD[2] if x < 13 else WD[1]


def stone(x, y):
    return IS[3] if y < 3 else IS[2] if x < 7 else IS[1]


q = Image.new('RGBA', (16, 16), (0, 0, 0, 0))   # upright quill: grey shaft, cream vane with gold barbs
qp = q.load()
for y in range(1, 16):
    qp[8, y] = hx('#4a4038')
for y in range(2, 11):
    w = 1 if y in (2, 10) else 2
    for dx in range(1, w + 1):
        qp[8 - dx, y] = hx('#f2ecd8')
        qp[8 + dx, y] = hx('#d0c6a8')
    if y in (4, 7):
        qp[7, y] = hx('#d8b44a')
        qp[9, y] = hx('#b8942e')

T.update({
    'board': paint(board, rim=(WD[5], WD[0])), 'panel': paint(panel), 'frame': paint(frame),
    'leg': paint(leg, rim=(WD[4], WD[0])), 'peg': paint(lambda x, y: WD[0] if x + y < 18 else WD[1]),
    'istone': paint(stone, rim=(IS[3], IS[0])), 'quill_v': q,
    'ink_top': paint(lambda x, y: '#0c0e14' if 4 <= x <= 11 and 4 <= y <= 11 else IS[3]),
})


def M():
    full = [0, 0, 16, 16]
    E = []
    side = {k: 'board' for k in ('north', 'south', 'east', 'west', 'down')}
    # thick top slab (apron is part of it), lowered panel, raised frame rails
    E.append(box((0, 10.0, 0), (32, 14.0, 16), side | {'up': 'panel'}, uv={k: full for k in side} | {'up': full}))
    E.append(box((2.0, 14.0, 2.0), (30.0, 14.2, 14.0), 'panel', faces=('up',), uv={'up': full}))
    fr = {k: 'frame' for k in ('north', 'south', 'east', 'west', 'up', 'down')}
    E.append(box((0, 14.0, 0), (32, 15.0, 2.0), fr, uv={k: full for k in fr}))
    E.append(box((0, 14.0, 14.0), (32, 15.0, 16), fr, uv={k: full for k in fr}))
    E.append(box((0, 14.0, 2.0), (2.0, 15.0, 14.0), fr))
    E.append(box((30.0, 14.0, 2.0), (32, 15.0, 14.0), fr))
    # wooden pegs at the apron ends (TC's dark corner marks)
    for px in (1.4, 29.4):
        E.append(box((px, 11.6, 16.0), (px + 1.2, 12.8, 16.3), 'peg'))
    # short chunky legs set in from the corners + a broad low stretcher board
    for (lx, lz) in ((2.0, 2.0), (26.0, 2.0), (2.0, 10.0), (26.0, 10.0)):
        E.append(box((lx, 0, lz), (lx + 4.0, 10.0, lz + 4.0), 'leg', uv={k: full for k in ('north', 'south', 'east', 'west')}))
    E.append(box((6.0, 2.4, 6.5), (26.0, 5.6, 9.5), 'board', uv={k: full for k in ('north', 'south', 'up')}))
    # small stack of research notes (left front), sheets slightly fanned
    for i, ang in enumerate((-5, 4, -1)):
        y0 = 14.2 + i * 0.4
        E.append(rotated(box((3.4, y0, 8.4), (9.4, y0 + 0.4, 13.4), 'page', uv={'up': full}), 'y', ang, (6.4, y0, 10.9)))
    # open grimoire (hero) in the middle, rune page rising
    book = [
        box((12.5, 14.2, 4.0), (23.5, 14.8, 12.0), 'cover'),
        box((13.0, 14.8, 4.5), (17.9, 15.6, 11.5), 'page', uv={'up': full}),
        box((18.1, 14.8, 4.5), (23.0, 15.6, 11.5), 'page', uv={'up': full}),
        rotated(box((18.1, 15.6, 4.6), (22.8, 15.65, 11.4), {'up': 'rune_page', 'down': 'page'}, faces=('up', 'down'), uv={'up': full}, emissive=True), 'z', 16, (18.1, 15.6, 8)),
    ]
    E += [rotated(b, 'y', -10, (18, 14.2, 8)) for b in book]
    # TC inkwell: stone base, dark ink cup, upright quill (right back)
    E.append(box((25.6, 14.2, 3.0), (29.6, 15.4, 7.0), {'north': 'istone', 'south': 'istone', 'east': 'istone', 'west': 'istone', 'up': 'istone', 'down': 'istone'}))
    E.append(box((26.6, 15.4, 4.0), (28.6, 16.6, 6.0), {'north': 'istone', 'south': 'istone', 'east': 'istone', 'west': 'istone', 'up': 'ink_top', 'down': 'istone'}, uv={'up': full}))
    E.append(rotated(box((24.1, 15.8, 5.0), (31.1, 26.8, 5.01), 'quill_v', faces=('south', 'north'), uv={'south': full, 'north': [16, 0, 0, 16]}), 'z', -10, (27.6, 16, 5)))
    # small essentia jar (left back)
    E.append(box((3.0, 14.2, 2.6), (6.6, 18.4, 6.2), {k: 'glass_aqua' for k in ('north', 'south', 'east', 'west')} | {'up': 'glass_top', 'down': 'glass_top'}, uv={k: full for k in ('north', 'south', 'east', 'west')}))
    E.append(box((3.6, 18.4, 3.2), (6.0, 19.4, 5.6), {'north': 'cork', 'south': 'cork', 'east': 'cork', 'west': 'cork', 'up': 'cork_top', 'down': 'cork'}))
    return {'elements': E}


if __name__ == '__main__':
    im = render(M(), T, size=192, scale=0.36, light=True, rot_y=35, center=(16, 10.5, 8))
    im = outline(im, (255, 255, 255, 50))
    im.save('desk9.png')
    big = im.resize((576, 576), Image.NEAREST)
    sh = Image.new('RGBA', (616, 616), (14, 16, 20, 255))
    sh.paste(big, (20, 20), big)
    sh.save('prev_desk9.png')
    print('ok')
