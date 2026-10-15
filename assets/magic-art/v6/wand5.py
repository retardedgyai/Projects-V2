"""Wand-as-wrench v5, from the staff references (V-Art golden staff, MythicCraft fire staff,
steampunk wrench): a long thin shaft with a cyan groove inlay, a gear-ring head with a crystal,
two sweeping crescent horns forming the wrench jaw above it, a row of small gem accents,
and a little box-end spanner ring at the foot (a wrench at both ends)."""
import math
from PIL import Image
from mcrender import render, box, rotated, outline
import jar4
import desk9

paint = jar4.paint
WD = desk9.WD
BR = jar4.BR
LE = ['#24130a', '#46281a', '#6e4228', '#9a643c']
IR = ['#16141c', '#24222c', '#36343f', '#4c4a56']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
T = dict(desk9.T)
T.update({
    'rod': paint(lambda x, y: WD[5] if x < 5 else WD[4] if x < 11 else WD[3], rim=(WD[5], WD[1])),
    'grip': paint(lambda x, y: LE[0] if y % 4 == 3 else (LE[3] if x < 6 else LE[2] if x < 12 else LE[1])),
    'gold': paint(lambda x, y: BR[3] if y < 6 else BR[2] if y < 12 else BR[1], rim=(BR[4], BR[0])),
    'gold_up': paint(lambda x, y: BR[3], rim=(BR[4], BR[2])),
    'plate': paint(lambda x, y: IR[1] if x + y < 16 else IR[0]),
    'glow': paint(lambda x, y: CY[2] if x < 8 else CY[1]),
    'crystal': paint(lambda x, y: CY[3] if x + y < 8 else CY[2] if x + y < 16 else CY[1] if x + y < 24 else CY[0]),
})
GOLD = {'north': 'gold', 'south': 'gold', 'east': 'gold', 'west': 'gold', 'up': 'gold_up', 'down': 'gold'}
full = [0, 0, 16, 16]
G = -45.0
O = (8.0, 18.0)
Z0, Z1 = 7.4, 8.6


def P(cx, cy, w, h, tex, ang=0.0, z=(Z0, Z1), **kw):
    g = math.radians(G)
    dx, dy = cx - O[0], cy - O[1]
    nx = O[0] + dx * math.cos(g) - dy * math.sin(g)
    ny = O[1] + dx * math.sin(g) + dy * math.cos(g)
    b = box((nx - w / 2, ny - h / 2, z[0]), (nx + w / 2, ny + h / 2, z[1]), tex,
            uv=kw.pop('uv', {k: full for k in ('north', 'south')}), **kw)
    return rotated(b, 'z', ang + G, (nx, ny, (z[0] + z[1]) / 2))


def seg(p0, p1, th, tex=GOLD, **kw):
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    return P((x0 + x1) / 2, (y0 + y1) / 2, L + th * 0.7, th, tex, ang=math.degrees(math.atan2(y1 - y0, x1 - x0)), **kw)


def ring(cx, cy, r, th, skip=(), teeth=False):
    E = []
    L = 2 * r * math.sin(math.radians(22.5)) + th * 0.5
    rm = r * math.cos(math.radians(22.5))
    for k in range(8):
        a = 45 * k
        if a in skip:
            continue
        bx, by = cx + rm * math.cos(math.radians(a)), cy + rm * math.sin(math.radians(a))
        E.append(P(bx, by, L, th, GOLD, ang=a + 90))
        if teeth:
            tx, ty = cx + (rm + th * 0.9) * math.cos(math.radians(a)), cy + (rm + th * 0.9) * math.sin(math.radians(a))
            E.append(P(tx, ty, th * 0.9, th * 0.9, GOLD, ang=a, z=(7.6, 8.4)))
    return E


def gem(x, y, s=0.9):
    return P(x, y, s, s, 'crystal', ang=45, z=(7.2, 8.8), uv={k: full for k in ('north', 'south', 'east', 'west', 'up', 'down')}, emissive=True)


def parts():
    E = []
    X = 8.0
    # foot: small box-end spanner ring, with a neck
    E += ring(X, 0.4, 1.7, 0.8)
    E.append(P(X, 2.6, 1.6, 1.4, GOLD))
    # long thin shaft
    E.append(P(X, 15.0, 1.2, 23.0, 'rod'))
    # leather grip
    E.append(P(X, 6.6, 1.6, 5.0, 'grip'))
    # cyan groove inlay running up the front of the shaft, broken by brass bands
    for (y0, y1) in ((9.8, 15.4), (16.4, 25.0)):
        E.append(P(X, (y0 + y1) / 2, 0.4, y1 - y0, 'glow', z=(8.6, 8.65), uv={'south': full}, faces=('south',), emissive=True))
    for y in (3.8, 9.4, 15.9):
        E.append(P(X, y, 1.7, 0.7, GOLD))
    # collar tapering into the head
    E.append(P(X, 25.6, 1.8, 1.2, GOLD))
    E.append(P(X, 26.6, 2.6, 0.9, GOLD))
    # head: toothed gear ring with a dark plate and the crystal
    hx, hy = X, 30.0
    E += ring(hx, hy, 3.0, 1.0, teeth=True)
    E.append(P(hx, hy, 3.6, 3.6, 'plate', ang=0, z=(8.2, 8.5)))
    E.append(P(hx, hy, 2.0, 2.0, 'crystal', ang=0, z=(7.1, 8.9), uv={k: full for k in ('north', 'south', 'east', 'west', 'up', 'down')}, emissive=True))
    # crescent horns forming the jaw: sweep out from the gear, curl back in at the tips
    horn = [(1.8, 2.4), (3.3, 3.9), (4.1, 5.8), (3.9, 7.7), (2.9, 9.0), (1.6, 9.4)]
    th = [1.4, 1.2, 1.0, 0.8, 0.5]
    for s in (-1, 1):
        pts = [(hx + s * px, hy + py) for (px, py) in horn]
        for i in range(len(pts) - 1):
            E.append(seg(pts[i], pts[i + 1], th[i]))
        # small gem accents along the horn (rhythm)
        E.append(gem(hx + s * 4.1, hy + 5.8, 0.8))
    # little spur under each horn root
    for s in (-1, 1):
        E.append(seg((hx + s * 2.6, hy - 1.6), (hx + s * 4.2, hy - 2.6), 0.7))
    return E


def M():
    return {'elements': parts()}


if __name__ == '__main__':
    kw = dict(light=True, rot_x=20, rot_y=20, center=(8, 19.5, 8))
    big = render(M(), T, size=192, scale=0.38, **kw)
    big = outline(big, (255, 255, 255, 50))
    big.save('wand5_big.png')
    ico = render(M(), T, size=32, scale=0.5, **kw)
    ico.save('wand5.png')
    sh = Image.new('RGBA', (776, 616), (14, 16, 20, 255))
    b = big.resize((576, 576), Image.NEAREST)
    sh.paste(b, (20, 20), b)
    for i, s in enumerate((128, 64)):
        c = ico.resize((s, s), Image.NEAREST)
        sh.paste(c, (620, 20 + i * 160), c)
    sh.paste(ico, (620, 260), ico)
    sh.save('prev_wand5.png')
    print('ok')
