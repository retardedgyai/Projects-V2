from PIL import Image
import math


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


N = 32
WOOD = ['#2e170c', '#56301a', '#82482a', '#ad6a3e', '#d39462']
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
GLASS = ['#1a2a2e', '#34525a', '#5e8a92', '#a8d0d4', '#eefcfc']
# essentia inside (Aqua by default)
LIQ = ['#0e2c55', '#1d63a0', '#3fa5d6', '#9fe3f4', '#f2fdff']


def make(liq, name):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = im.load()

    def put(x, y, c):
        if 0 <= x < N and 0 <= y < N:
            px[x, y] = hx(c)

    X0, X1 = 9, 22            # glass body columns (14 wide, centred on 15.5)
    TOP, BOT = 10, 27
    # glass body
    for y in range(TOP, BOT + 1):
        for x in range(X0, X1 + 1):
            u = (x - X0) / (X1 - X0)
            s = 2
            if u < 0.12:
                s = 3
            if u > 0.86:
                s = 1
            if x in (X0, X1):
                s = 4 if x == X0 else 0
            put(x, y, GLASS[s])
    # rounded bottom corners
    for (x, y) in ((X0, BOT), (X1, BOT)):
        px[x, y] = (0, 0, 0, 0)
    for x in range(X0 + 1, X1):
        put(x, BOT, GLASS[0] if x > 15 else GLASS[1])
    # liquid: fills from y=15 down, with a meniscus line
    LV = 15
    for y in range(LV, BOT):
        for x in range(X0 + 1, X1):
            u = (x - X0) / (X1 - X0)
            s = 2
            if u < 0.25:
                s = 3
            if u > 0.75:
                s = 1
            if y > BOT - 3:
                s = max(0, s - 1)
            put(x, y, liq[s])
    for x in range(X0 + 1, X1):
        put(x, LV, liq[4] if x < 15 else liq[3])
    # glass reflection streak over the liquid (left side, vertical)
    for y in range(TOP + 2, BOT - 2):
        put(X0 + 2, y, GLASS[4] if y < LV else liq[4])
    put(X0 + 3, TOP + 3, GLASS[4])
    # brass collar
    for x in range(X0 - 1, X1 + 2):
        u = (x - X0 + 1) / (X1 - X0 + 2)
        put(x, TOP - 1, BR[4] if u < 0.2 else BR[3] if u < 0.6 else BR[2])
        put(x, TOP, BR[2] if u < 0.6 else BR[1])
    # wooden lid (slightly narrower, with knob)
    for y in range(TOP - 5, TOP - 1):
        for x in range(X0 + 1, X1):
            u = (x - X0) / (X1 - X0)
            s = 3 if u < 0.3 else 2 if u < 0.75 else 1
            if y == TOP - 5:
                s = min(4, s + 1)
            put(x, y, WOOD[s])
    for x in range(14, 18):
        put(x, TOP - 6, WOOD[3] if x < 16 else WOOD[2])
        put(x, TOP - 7, WOOD[4] if x < 16 else WOOD[3])
    # paper label with aspect mark
    for y in range(20, 25):
        for x in range(12, 20):
            put(x, y, '#e2d6b0' if x < 16 else '#c4b48c')
    for (x, y) in ((15, 21), (16, 21), (14, 22), (17, 22), (15, 23), (16, 23)):
        put(x, y, liq[1])
    im.save(f'{name}.png')
    return im


a = make(LIQ, 'jar_aqua')
b = make(['#5c1610', '#b8341a', '#ec7a26', '#ffc552', '#fff4cc'], 'jar_ignis')
c = make(['#3a1a46', '#6a2e7e', '#9a52b0', '#c88ad8', '#f0d8fa'], 'jar_venenum')
out = Image.new('RGBA', (3 * (N * 8 + 20) + 20, N * 8 + 40), (14, 16, 20, 255))
for i, im in enumerate((a, b, c)):
    big = im.resize((N * 8, N * 8), Image.NEAREST)
    out.paste(big, (20 + i * (N * 8 + 20), 20), big)
out.save('prev_jar.png')
print('ok')
