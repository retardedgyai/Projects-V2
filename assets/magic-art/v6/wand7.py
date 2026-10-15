"""Wand-as-wrench v7 for Blockbench: v6 shape, per-face painted textures (no stretching),
fewer / longer horn segments with plain ends (no seam rims), and a texture animation in the
Movsum way: body still, only the glow moves.
  frames 0-15  a light band runs up the shaft inlay toward the head
  frame  16    the crystal flashes white (1 frame)
  17-24        crystal cools from bright, a '+' sparkle blinks beside it
  rest         slow breathing pulse of the crystal"""
import math
import sys
from PIL import Image
import bbanim

F = 48
BR = ['#6a4410', '#a07818', '#d4a838', '#f4d870', '#fff4b8']
WD = ['#140b0c', '#201314', '#2e1c1c', '#3e2925', '#523630', '#68463c']
LE = ['#24130a', '#46281a', '#6e4228', '#9a643c']
IR = ['#16141c', '#24222c', '#36343f', '#4c4a56']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def solid(base, light, dark, up, down=None):
    """Flat mass + 1px light/dark rim along the long edges only (ends stay plain, so joints
    between segments don't show a seam)."""
    def paint(W, H, face, f, F):
        im = Image.new('RGBA', (W, H), hx(base))
        px = im.load()
        if face == 'up':
            return Image.new('RGBA', (W, H), hx(up))
        if face == 'down':
            return Image.new('RGBA', (W, H), hx(down or dark))
        if W >= H and H >= 2:
            for x in range(W):
                px[x, 0] = hx(light if H >= 3 else up)
                if H >= 3:
                    px[x, H - 1] = hx(dark)
        elif H > W and W >= 2:
            for y in range(H):
                px[0, y] = hx(light if W >= 3 else up)
                if W >= 3:
                    px[W - 1, y] = hx(dark)
        return im
    return paint


def rod(W, H, face, f, F):
    if face in ('up', 'down'):
        return Image.new('RGBA', (W, H), hx(WD[3]))
    im = Image.new('RGBA', (W, H), hx(WD[3]))
    px = im.load()
    for y in range(H):
        px[0, y] = hx(WD[5] if face in ('south', 'west') else WD[4])
    return im


GOLD = solid(BR[2], BR[4], BR[1], BR[3], BR[1])
GRIP = solid(LE[2], LE[3], LE[0], LE[2], LE[0])
PLATE = solid(IR[1], IR[2], IR[0], IR[1])


def inlay(W, H, face, f, F):
    im = Image.new('RGBA', (W, H), hx(CY[1]))
    px = im.load()
    if face in ('north', 'south') and f < 16:
        p = (H - 1) - round(f * (H + 2) / 15)          # band head position, moving up (y=0 is the top)
        for y, c in ((p, CY[3]), (p + 1, CY[3]), (p + 2, CY[2])):
            if 0 <= y < H:
                for x in range(W):
                    px[x, y] = hx(c)
    return im


def crystal(W, H, face, f, F):
    if f == 16:
        lv = 3
    elif 17 <= f <= 24:
        lv = 3 if f < 20 else 2
    else:
        lv = 2 if math.sin(f / F * 2 * math.pi) > 0.3 else 1
    ramp = [CY[0], CY[1], CY[2], CY[3], '#ffffff']
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = im.load()
    for y in range(H):
        for x in range(W):
            d = x + y
            px[x, y] = hx(ramp[min(4, lv + (1 if d < (W + H) / 3 else 0 if d < (W + H) * 2 / 3 else -1))])
    if f == 16:
        im = Image.new('RGBA', (W, H), hx('#ffffff'))
    return im


def sparkle(W, H, face, f, F):
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    if face not in ('north', 'south'):
        return im
    px = im.load()
    cx, cy = W // 2, H // 2
    if f in (17, 18, 19, 20):
        r = 1 if f in (17, 20) else 2 if W >= 5 else 1
        c = hx(CY[3]) if f < 20 else hx(CY[2])
        for d in range(-r, r + 1):
            if 0 <= cx + d < W:
                px[cx + d, cy] = c
            if 0 <= cy + d < H:
                px[cx, cy + d] = c
        if f in (18, 19):
            px[cx, cy] = hx('#ffffff')
    return im


G = -45.0
O = (8.0, 18.0)
Z0, Z1 = 7.4, 8.6


def P(cx, cy, w, h, mat, ang=0.0, z=(Z0, Z1), **kw):
    g = math.radians(G)
    dx, dy = cx - O[0], cy - O[1]
    nx = O[0] + dx * math.cos(g) - dy * math.sin(g)
    ny = O[1] + dx * math.sin(g) + dy * math.cos(g)
    el = dict(fr=(nx - w / 2, ny - h / 2, z[0]), to=(nx + w / 2, ny + h / 2, z[1]), mat=mat,
              rot=('z', ang + G, (nx, ny, (z[0] + z[1]) / 2)))
    el.update(kw)
    return el


def seg(p0, p1, th, mat=GOLD, ext=0.9, **kw):
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    return P((x0 + x1) / 2, (y0 + y1) / 2, L + th * ext, th, mat, ang=math.degrees(math.atan2(y1 - y0, x1 - x0)), **kw)


def ring(cx, cy, r, th):
    E = []
    L = 2 * r * math.sin(math.radians(22.5)) + th * 0.9
    rm = r * math.cos(math.radians(22.5))
    for k in range(8):
        a = 45 * k
        E.append(P(cx + rm * math.cos(math.radians(a)), cy + rm * math.sin(math.radians(a)), L, th, GOLD, ang=a + 90))
    return E


def model():
    E = []
    X = 8.0
    E.append(P(X, 1.4, 2.0, 2.0, GOLD, name='foot'))
    E.append(P(X, 14.6, 1.2, 25.0, rod, name='shaft'))
    E.append(P(X, 6.0, 1.6, 6.4, GRIP, name='grip'))
    E.append(P(X, 9.6, 2.0, 1.0, GOLD, name='band'))
    E.append(P(X, 18.0, 0.4, 14.0, inlay, z=(8.6, 8.7), emissive=True, name='inlay'))
    E.append(P(X, 26.4, 2.4, 1.4, GOLD, name='collar'))
    hx_, hy = X, 30.0
    E += ring(hx_, hy, 3.0, 1.4)
    E.append(P(hx_, hy, 3.6, 3.6, PLATE, ang=0, z=(8.2, 8.5), name='plate'))
    E.append(P(hx_, hy, 2.0, 2.0, crystal, ang=0, z=(7.1, 8.9), emissive=True, name='crystal'))
    # horns: four longer segments each, constant width, thin tip
    horn = [(1.8, 2.4), (3.8, 4.6), (4.2, 7.4), (2.9, 9.2), (1.4, 9.6)]
    th = [1.6, 1.4, 1.2, 0.9]
    for s in (-1, 1):
        pts = [(hx_ + s * px, hy + py) for (px, py) in horn]
        for i in range(len(pts) - 1):
            E.append(seg(pts[i], pts[i + 1], th[i], name=f'horn{s}_{i}'))
    # sparkle beside the crystal (transparent except during the flash)
    E.append(P(hx_ + 2.8, hy + 2.8, 2.5, 2.5, sparkle, ang=-G, z=(8.9, 8.95), emissive=True, res=2, name='sparkle'))
    return E


if __name__ == '__main__':
    n, at = bbanim.export(model(), F, 'wand7', 'wand7.bbmodel', png_dir='.')
    print(n, at)
