from PIL import Image
import math


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


N = 32
BRICK = ['#2a1612', '#4a2820', '#6e3a2c', '#94503a']
MORTAR = '#3a2c26'
CU = ['#3b1d10', '#7a3f22', '#b56a3c', '#e29a62', '#ffd2a6']   # copper
FIRE = ['#7a1e12', '#e05a1c', '#ffb040', '#fff0b0']
GLASS = ['#1a2a2e', '#34525a', '#5e8a92', '#a8d0d4', '#eefcfc']
LIQ = ['#0e2c55', '#1d63a0', '#3fa5d6', '#9fe3f4']
im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = hx(c)


# --- furnace (bricks) x 3..18, y 20..28
for y in range(20, 29):
    for x in range(3, 19):
        row = (y - 20) // 3
        off = 0 if row % 2 == 0 else 2
        mortar = (y - 20) % 3 == 2 or (x + off) % 5 == 0
        u = (x - 3) / 15
        tone = 3 if u < 0.25 else 2 if u < 0.7 else 1
        if y == 28:
            tone = 0
        put(x, y, MORTAR if mortar and y < 28 else BRICK[tone])
# fire mouth (arched opening) in the furnace front
for y in range(23, 28):
    for x in range(8, 14):
        d = abs(x + 0.5 - 11) + max(0, 24.5 - y) * 1.2
        if d <= 3.2:
            put(x, y, FIRE[0])
for (x, y, c) in ((10, 26, FIRE[1]), (11, 26, FIRE[2]), (11, 25, FIRE[2]), (10, 25, FIRE[1]), (12, 26, FIRE[1]),
                  (11, 24, FIRE[1]), (11, 27, FIRE[3]), (10, 27, FIRE[2]), (12, 27, FIRE[2])):
    put(x, y, c)
# --- copper pot (round belly) centre (11, 14.5) r 6.2, sitting on the furnace
cx, cy, r = 11.0, 14.5, 6.2
for y in range(N):
    for x in range(N):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy)
        if d <= r and y <= 20:
            lit = -(dx + dy * 1.1)
            tone = 4 if lit > 5.5 else 3 if lit > 1.5 else 2 if lit > -2.5 else 1
            if d > r - 0.7:
                tone = max(0, tone - 1)
            put(x, y, CU[tone])
# rivet band around the belly
for x in range(6, 17):
    put(x, 17, CU[1] if x > 11 else CU[2])
for x in range(6, 12):
    put(x, 16, CU[3])
# neck rising from the pot
for y in range(5, 9):
    for x in range(9, 13):
        put(x, y, CU[3] if x == 9 else CU[2] if x < 12 else CU[1])
for x in range(8, 14):
    put(x, 4, CU[4] if x < 11 else CU[3])
# swan-neck pipe: from the neck top going right and down to the receiver
pipe = [(13, 5), (14, 5), (15, 5), (16, 6), (17, 6), (18, 7), (19, 8), (20, 9), (21, 10), (22, 11), (23, 12), (24, 13), (24, 14), (24, 15), (24, 16)]
for (x, y) in pipe:
    put(x, y, CU[3])
    put(x, y + 1, CU[1]) if y < 13 else put(x + 1, y, CU[1])
# --- glass receiver flask on the right: round bottom, neck at x 24
fx, fy, fr = 24.5, 24.0, 4.6
for y in range(N):
    for x in range(N):
        d = math.hypot(x + 0.5 - fx, y + 0.5 - fy)
        if d <= fr:
            lit = -((x + 0.5 - fx) + (y + 0.5 - fy))
            tone = 3 if lit > 2.5 else 2 if lit > -1.5 else 1
            if d > fr - 0.7:
                tone = 0 if lit < 0 else 4
            put(x, y, GLASS[tone])
# essentia inside the flask (lower part)
for y in range(24, 28):
    for x in range(N):
        d = math.hypot(x + 0.5 - fx, y + 0.5 - fy)
        if d <= fr - 1.0:
            put(x, y, LIQ[3] if y == 24 else LIQ[2] if x < 25 else LIQ[1])
put(22, 21, GLASS[4])
put(23, 20, GLASS[4])
# flask neck
for y in range(17, 20):
    put(24, y, GLASS[3])
    put(25, y, GLASS[1])
im.save('distiller.png')
out = Image.new('RGBA', (N * 10 + 160, N * 10 + 40), (14, 16, 20, 255))
b = im.resize((N * 10, N * 10), Image.NEAREST)
out.paste(b, (20, 20), b)
m = im.resize((64, 64), Image.NEAREST)
out.paste(m, (N * 10 + 50, 40), m)
out.paste(im, (N * 10 + 66, 130), im)
out.save('prev_distiller.png')
print('ok')
