from PIL import Image
import math


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


N = 32
WOOD = ['#24120a', '#45261a', '#6a3c24', '#8e5632', '#b47448']   # dark oak
PAGE = ['#7a6a4a', '#b8a67a', '#e2d4a8', '#f6ecc8']
LEATHER = ['#3a1418', '#6a242a', '#8e3a3a']
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
CYAN = ['#1f6fa8', '#49b4d9', '#c8f4ff']
im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = hx(c)


# --- desk top surface (seen from slightly above): y 15..18, x 2..29
for y in range(15, 19):
    for x in range(2, 30):
        u = (x - 2) / 27
        tone = 4 if y == 15 else 3 if u < 0.55 else 2
        put(x, y, WOOD[tone])
# front edge / apron with a drawer
for y in range(19, 23):
    for x in range(2, 30):
        tone = 2 if y == 19 else 1
        if x in (2, 29):
            tone = 0
        put(x, y, WOOD[tone])
for x in range(12, 20):
    put(x, 20, WOOD[2])
    put(x, 21, WOOD[0])
put(15, 21, BR[3])
put(16, 21, BR[2])
# legs
for (x0, x1) in ((3, 5), (26, 28)):
    for y in range(23, 30):
        for x in range(x0, x1 + 1):
            put(x, y, WOOD[2] if x == x0 else WOOD[1] if x < x1 else WOOD[0])
for x in range(3, 29):
    if not (5 < x < 26):
        continue
    put(x, 27, WOOD[1])
# brass corner caps on the top edge
for (x, y, c) in ((2, 15, BR[4]), (3, 15, BR[3]), (2, 16, BR[2]), (29, 15, BR[3]), (28, 15, BR[2]), (29, 16, BR[1])):
    put(x, y, c)
# --- open book on the desk (centre), slightly raised spine
for y in range(10, 16):
    for x in range(9, 23):
        if x == 15 or x == 16:
            put(x, y, PAGE[0] if y > 10 else PAGE[1])
            continue
        left = x < 16
        tone = 3 if (left and y < 13) else 2 if left else 2 if y < 13 else 1
        # pages curve up at the outer edge
        if (x == 9 or x == 22) and y == 10:
            continue
        put(x, y, PAGE[tone])
# text lines on the left page
for y in (11, 13):
    for x in range(10, 15):
        if x % 2 == 0:
            put(x, y, PAGE[0])
# the one glow: a cyan aspect rune drawn on the right page
for (x, y, c) in ((19, 12, CYAN[2]), (18, 12, CYAN[1]), (20, 12, CYAN[1]), (19, 11, CYAN[1]), (19, 13, CYAN[1]),
                  (17, 12, CYAN[0]), (21, 12, CYAN[0]), (19, 10, CYAN[0]), (19, 14, CYAN[0])):
    put(x, y, c)
# leather cover peeking under the pages
for x in range(8, 24):
    put(x, 16, LEATHER[1] if x < 16 else LEATHER[0])
put(8, 15, LEATHER[2])
put(23, 15, LEATHER[0])
# --- inkwell + quill (left)
for y in range(11, 15):
    for x in range(3, 7):
        put(x, y, '#1a1a24' if x > 4 else '#3a3a50')
for x in range(3, 7):
    put(x, 10, BR[3] if x < 5 else BR[2])
quill = [(6, 9), (6, 8), (7, 7), (7, 6), (8, 5), (8, 4), (9, 3)]
for i, (x, y) in enumerate(quill):
    put(x, y, '#f2eee4' if i > 1 else '#c8c0b0')
    if i > 2:
        put(x - 1, y, '#c8c0b0')
# --- brass armillary sphere (right)
cx, cy = 26.0, 10.5
for y in range(N):
    for x in range(N):
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if 2.6 <= d <= 3.6:
            lit = -((x + 0.5 - cx) + (y + 0.5 - cy))
            put(x, y, BR[4] if lit > 2 else BR[3] if lit > -1 else BR[1])
for x in range(23, 30):
    put(x, 10, BR[2])
put(26, 10, BR[4])
for y in range(14, 15):
    put(25, y, BR[2])
    put(26, y, BR[1])
im.save('desk.png')
out = Image.new('RGBA', (N * 10 + 160, N * 10 + 40), (14, 16, 20, 255))
b = im.resize((N * 10, N * 10), Image.NEAREST)
out.paste(b, (20, 20), b)
m = im.resize((64, 64), Image.NEAREST)
out.paste(m, (N * 10 + 50, 40), m)
out.paste(im, (N * 10 + 66, 130), im)
out.save('prev_desk.png')
print('ok')
