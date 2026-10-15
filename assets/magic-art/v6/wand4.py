"""Wand-as-wrench v4, built the MatE/Anz way: a designed silhouette made of short rotated bars.
Head = an octagonal brass ring open at the top (the wrench jaw, two prong tips), a dark
backing plate and a cyan crystal floating in the centre, two small side spikes.
Below: dark iron leaves at the collar, a near-black greatwood shaft with brass bands and a
leather grip, a brass foot. Everything lies in one plane, thin in depth, tilted 45 degrees."""
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
    'gold': paint(lambda x, y: BR[3] if y < 5 else BR[2] if y < 11 else BR[1], rim=(BR[4], BR[0])),
    'gold_up': paint(lambda x, y: BR[3], rim=(BR[4], BR[2])),
    'iron': paint(lambda x, y: IR[3] if y < 5 else IR[2] if y < 11 else IR[1], rim=(IR[3], IR[0])),
    'plate': paint(lambda x, y: IR[1] if x + y < 16 else IR[0]),
    'crystal': paint(lambda x, y: CY[3] if x + y < 8 else CY[2] if x + y < 16 else CY[1] if x + y < 24 else CY[0]),
})
GOLD = {'north': 'gold', 'south': 'gold', 'east': 'gold', 'west': 'gold', 'up': 'gold_up', 'down': 'gold'}
IRON = 'iron'
full = [0, 0, 16, 16]
G = -45.0                     # whole wand tilt
O = (8.0, 16.0)               # tilt origin (x, y)
Z0, Z1 = 7.2, 8.8             # thin in depth


def P(cx, cy, w, h, tex, ang=0.0, z=(Z0, Z1), **kw):
    """A w x h bar centred at (cx, cy) in the wand's own plane, turned by ang, then the whole
    wand tilt applied (both about z, so they compose into one element rotation)."""
    g = math.radians(G)
    dx, dy = cx - O[0], cy - O[1]
    nx = O[0] + dx * math.cos(g) - dy * math.sin(g)
    ny = O[1] + dx * math.sin(g) + dy * math.cos(g)
    zc = (z[0] + z[1]) / 2
    b = box((nx - w / 2, ny - h / 2, z[0]), (nx + w / 2, ny + h / 2, z[1]), tex,
            uv=kw.pop('uv', {k: full for k in ('north', 'south')}), **kw)
    return rotated(b, 'z', ang + G, (nx, ny, zc))


def parts():
    E = []
    X = 8.0
    # foot: brass cap with a small turned-up toe
    E.append(P(X, 0.8, 2.6, 1.6, GOLD))
    E.append(P(X + 1.3, 0.4, 2.2, 0.9, GOLD, ang=25))
    # shaft, grip, bands
    E.append(P(X, 10.4, 1.6, 17.6, 'rod'))
    E.append(P(X, 6.0, 2.1, 5.6, 'grip'))
    for y in (2.6, 9.4, 16.2):
        E.append(P(X, y, 2.3, 0.9, GOLD))
    # collar + dark iron leaves (MatE-like), angled up and out
    E.append(P(X, 19.8, 2.8, 2.0, GOLD))
    E.append(P(X - 2.4, 21.2, 4.2, 1.2, IRON, ang=-35, z=(7.5, 8.5)))
    E.append(P(X + 2.4, 21.2, 4.2, 1.2, IRON, ang=35, z=(7.5, 8.5)))
    E.append(P(X, 21.6, 1.8, 1.4, GOLD))
    # head: octagonal ring open at the top (the jaw)
    cx, cy, r = X, 27.0, 4.6
    L = 2 * r * math.sin(math.radians(22.5)) + 0.7
    rm = r * math.cos(math.radians(22.5))
    for k in range(8):
        th = 45 * k
        if th == 90:
            continue                              # open jaw
        bx = cx + rm * math.cos(math.radians(th))
        by = cy + rm * math.sin(math.radians(th))
        E.append(P(bx, by, L, 1.4, GOLD, ang=th + 90))
    # jaw prongs rising from the ring's top corners, parallel, each ending in a small inward hook
    for s in (-1, 1):
        vx = cx + s * r * math.sin(math.radians(22.5))
        vy = cy + r * math.cos(math.radians(22.5))
        E.append(P(vx + s * 0.1, vy + 1.6, 1.4, 3.6, GOLD))
        E.append(P(vx - s * 0.5, vy + 3.0, 1.0, 0.9, GOLD))
    # side spikes
    for s in (-1, 1):
        E.append(P(cx + s * (rm + 1.3), cy, 1.6, 0.9, GOLD))
    # dark backing plate and the floating crystal (the only glow)
    E.append(P(cx, cy, 5.0, 5.0, 'plate', ang=0, z=(8.3, 8.6)))
    E.append(P(cx, cy, 2.8, 2.8, 'crystal', ang=0, z=(7.0, 9.0), uv={k: full for k in ('north', 'south', 'east', 'west', 'up', 'down')}, emissive=True))
    return E


def M():
    return {'elements': parts()}


if __name__ == '__main__':
    kw = dict(light=True, rot_x=20, rot_y=20, center=(8, 16, 8))
    big = render(M(), T, size=192, scale=0.42, **kw)
    big = outline(big, (255, 255, 255, 50))
    big.save('wand4_big.png')
    ico = render(M(), T, size=32, scale=0.62, **kw)
    ico.save('wand4.png')
    sh = Image.new('RGBA', (776, 616), (14, 16, 20, 255))
    b = big.resize((576, 576), Image.NEAREST)
    sh.paste(b, (20, 20), b)
    for i, s in enumerate((128, 64)):
        c = ico.resize((s, s), Image.NEAREST)
        sh.paste(c, (620, 20 + i * 160), c)
    sh.paste(ico, (620, 260), ico)
    sh.save('prev_wand4.png')
    print('ok')
