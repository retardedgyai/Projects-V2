"""32px item icon: the old journal / grimoire. Closed leather book seen from the front with the
page block showing on the right and bottom, brass corner caps and a brass clasp, a carved
rune on the cover whose groove glows cyan (the only glow). Lit from the top-left."""
from PIL import Image

N = 32
LE = ['#1e0d0b', '#3a1813', '#5a261c', '#7a3626', '#9a4c34']   # oxblood leather
BR = ['#5a3a10', '#a07818', '#d4a838', '#f4d870', '#fff4b8']
PG = ['#9c8a66', '#c8b488', '#e6d6aa', '#f8ecc8']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = hx(c)


X0, X1, Y0, Y1 = 7, 24, 4, 26          # front cover
# page block: right side and bottom, drawn first (behind the cover edge)
for y in range(Y0 + 2, Y1 + 3):
    for x in (X1 + 1, X1 + 2):
        put(x, y, PG[2] if y % 3 else PG[1])
    put(X1 + 3, y, PG[0])
for x in range(X0 + 2, X1 + 3):
    for y in (Y1 + 1, Y1 + 2):
        put(x, y, PG[1] if x % 3 else PG[0])
put(X1 + 3, Y1 + 3, PG[0])
for x in range(X0 + 2, X1 + 4):
    put(x, Y1 + 3, LE[0])                  # back cover peeking under the pages
for y in range(Y0 + 2, Y1 + 4):
    put(X1 + 4, y, LE[0])
# spine on the left, rounded with two raised bands
for y in range(Y0, Y1 + 2):
    put(X0 - 3, y, LE[1])
    put(X0 - 2, y, LE[3])
    put(X0 - 1, y, LE[2])
for by in (Y0 + 5, Y1 - 5):
    for x in range(X0 - 3, X0):
        put(x, by, LE[4] if x == X0 - 2 else LE[3])
        put(x, by + 1, LE[1])
put(X0 - 3, Y0, (0, 0, 0, 0) and LE[0])
# cover: flat masses, light toward the top-left, 1px rims
for y in range(Y0, Y1 + 1):
    for x in range(X0, X1 + 1):
        c = LE[3] if (x - X0) + (y - Y0) < 14 else LE[2]
        if x == X0 or y == Y0:
            c = LE[4]
        if x == X1 or y == Y1:
            c = LE[1]
        put(x, y, c)
# tooled inner border (a recessed line, no glow)
for x in range(X0 + 2, X1 - 1):
    put(x, Y0 + 2, LE[1]); put(x, Y1 - 2, LE[1])
for y in range(Y0 + 2, Y1 - 1):
    put(X0 + 2, y, LE[1]); put(X1 - 2, y, LE[1])
# brass corner caps (right side, top and bottom)
for (cx, cy, dx, dy) in ((X1, Y0, -1, 1), (X1, Y1, -1, -1)):
    for i in range(3):
        put(cx + dx * i, cy, BR[3] if dy > 0 else BR[1])
        put(cx, cy + dy * i, BR[2])
    put(cx + dx, cy + dy, BR[2])
# brass clasp: strap over the page edge into a buckle
for y in (14, 15, 16):
    for x in range(X1 - 3, X1 + 4):
        put(x, y, BR[3] if y == 14 else BR[2] if y == 15 else BR[1])
put(X1 - 4, 14, BR[4]); put(X1 - 4, 15, BR[2]); put(X1 - 4, 16, BR[1])
put(X1 - 2, 15, BR[0])                    # buckle pin hole
# carved rune on the cover: an eye in a diamond; groove dark, glowing core
cx, cy = 14, 15
R = 5
dia = [(dx, dy) for dx in range(-R, R + 1) for dy in range(-R, R + 1) if abs(dx) + abs(dy) == R]
for (dx, dy) in dia:
    put(cx + dx, cy + dy, CY[1] if dy < 0 or (dy == 0 and dx < 0) else CY[0])
for dx in range(-2, 3):                    # eye
    put(cx + dx, cy - (1 if abs(dx) < 2 else 0), CY[1])
    put(cx + dx, cy + (1 if abs(dx) < 2 else 0), CY[0])
put(cx, cy, CY[3]); put(cx - 1, cy, CY[2]); put(cx + 1, cy, CY[2])
im.save('book32.png')
out = Image.new('RGBA', (32 * 10 + 140, 32 * 10 + 40), (14, 16, 20, 255))
b = im.resize((320, 320), Image.NEAREST)
out.paste(b, (20, 20), b)
m = im.resize((64, 64), Image.NEAREST)
out.paste(m, (360, 40), m)
out.paste(im, (376, 130), im)
out.save('prev_book32.png')
print('ok')
