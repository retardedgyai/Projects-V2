from PIL import Image
import math


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


N = 32
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']   # brass
LE = ['#24130a', '#46281a', '#6e4228', '#9a643c']              # leather
GL = ['#0b2228', '#17424a', '#2c7078', '#7cc6c8', '#e6ffff']   # glass
RU = ['#1f6fa8', '#49b4d9', '#c8f4ff']                         # rune glow
im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = hx(c)


# strap: arc behind the lenses (top), from left hinge to right hinge
for x in range(2, 30):
    t = (x - 15.5) / 13.5
    yc = 12.5 - 4.5 * (1 - t * t)          # arc peaks at the middle
    for dy in range(0, 3):
        y = int(round(yc)) + dy
        put(x, y, LE[3] if dy == 0 and x < 16 else LE[2] if dy == 0 else LE[1] if dy == 1 else LE[0])
# stitching on the strap
for x in range(4, 28, 3):
    t = (x - 15.5) / 13.5
    put(x, int(round(12.5 - 4.5 * (1 - t * t))) + 1, LE[3])
# side hinges (leather wraps down to the lens rims)
for (x0, x1) in ((1, 3), (28, 30)):
    for y in range(12, 19):
        for x in range(x0, x1 + 1):
            put(x, y, LE[2] if x == x0 else LE[1])


def lens(cx, cy, glow):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 6.4:
                lit = (cx - (x + 0.5)) + (cy - (y + 0.5))      # >0 toward the top-left
                if d > 4.6:                                    # brass rim
                    s = 4 if lit > 5 else 3 if lit > 1.5 else 2 if lit > -2.5 else 1
                    if d > 6.0:
                        s = max(0, s - 1)
                    put(x, y, BR[s])
                else:                                          # glass
                    s = 1
                    if lit > 1:
                        s = 2
                    if lit < -3:
                        s = 0
                    put(x, y, GL[s])
    # inner rim shadow (lower-right inside of the ring)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            lit = (cx - (x + 0.5)) + (cy - (y + 0.5))
            if 4.0 < d <= 4.6 and lit < 0:
                put(x, y, GL[0])
    # glass highlight: small diagonal streak top-left
    hx0, hy0 = int(cx - 2.5), int(cy - 2.5)
    put(hx0, hy0, GL[4])
    put(hx0 + 1, hy0, GL[3])
    put(hx0, hy0 + 1, GL[3])
    put(hx0 + 2, hy0 - 1 + 1, GL[3]) if False else None
    # rivets on the rim (4 points)
    for ang in (45, 135, 225, 315):
        a = math.radians(ang)
        rx, ry = int(cx + 5.4 * math.cos(a) - 0.5), int(cy + 5.4 * math.sin(a) - 0.5)
        put(rx, ry, BR[4] if ang in (225,) else BR[0])
    if glow:
        # faint aspect rune visible in this lens: small 4-point mark with a bright core
        cxi, cyi = int(cx - 0.5) + 1, int(cy - 0.5) + 1
        for (dx, dy, c) in ((0, 0, RU[2]), (1, 0, RU[1]), (-1, 0, RU[1]), (0, 1, RU[1]), (0, -1, RU[1]),
                            (2, 0, RU[0]), (-2, 0, RU[0]), (0, 2, RU[0]), (0, -2, RU[0])):
            put(cxi + dx, cyi + dy, c)


lens(9.0, 18.0, False)
lens(23.0, 18.0, True)
# bridge between lenses
for x in range(15, 17):
    put(x, 16, BR[3])
    put(x, 17, BR[2])
    put(x, 18, BR[1])
# small brass loupe stacked on the left lens (adjustable extra lens) — the "observer" detail
for y in range(N):
    for x in range(N):
        d = math.hypot(x + 0.5 - 6.0, y + 0.5 - 11.0)
        if d <= 2.6:
            put(x, y, BR[3] if d > 1.6 and (x + y) < 17 else BR[1] if d > 1.6 else GL[2])
put(5, 10, GL[4])
im.save('goggles.png')
out = Image.new('RGBA', (N * 10 + 160, N * 10 + 40), (14, 16, 20, 255))
b = im.resize((N * 10, N * 10), Image.NEAREST)
out.paste(b, (20, 20), b)
m = im.resize((64, 64), Image.NEAREST)
out.paste(m, (N * 10 + 50, 40), m)
out.paste(im, (N * 10 + 66, 130), im)
out.save('prev_goggles.png')
print('ok')
