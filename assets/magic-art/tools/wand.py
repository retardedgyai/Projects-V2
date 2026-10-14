from PIL import Image


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


N = 32
WOOD = ['#2a150b', '#4e2a16', '#7a4426', '#a6623a', '#cc8a58']   # dark walnut shaft
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
CR = ['#1f6fa8', '#49b4d9', '#9fe7f6', '#f0ffff']               # crystal (cyan, the only glow)
im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = hx(c)


# shaft: 45deg band from bottom-left to the head, 3 diagonals thick (s = x+y in 30..32)
for y in range(N):
    for x in range(N):
        s = x + y
        a = x - y
        if 30 <= s <= 32 and -24 <= a <= 6:
            tone = {30: 3, 31: 2, 32: 1}[s]
            put(x, y, WOOD[tone])
# brass ferrules (bands) along the shaft
for a0 in (-20, -10, 2):
    for y in range(N):
        for x in range(N):
            s = x + y
            if 29 <= s <= 33 and x - y == a0:
                put(x, y, BR[4] if s == 29 else BR[3] if s <= 30 else BR[2] if s <= 31 else BR[1])
# pommel at the bottom end
for (x, y, c) in ((2, 28, BR[3]), (3, 28, BR[2]), (2, 29, BR[2]), (3, 29, BR[1]), (1, 29, BR[3]), (2, 30, BR[1]), (4, 27, BR[3])):
    put(x, y, c)
# head: open wrench jaw (U shape) rotated 45deg; built as a ring with a notch toward the top-right
import math
cx, cy = 21.5, 10.5
for y in range(N):
    for x in range(N):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy)
        # notch direction: toward top-right (dx>0, dy<0)
        u = (dx - dy) / math.sqrt(2)      # along the notch axis
        v = (dx + dy) / math.sqrt(2)
        if d <= 6.2 and not (u > 0 and abs(v) < 2.2):
            lit = -(dx + dy)
            tone = 4 if lit > 6 else 3 if lit > 1.5 else 2 if lit > -3 else 1
            if d > 5.6:
                tone = max(0, tone - 1)
            put(x, y, BR[tone])
# inner jaw shadow
for y in range(N):
    for x in range(N):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        u = (dx - dy) / math.sqrt(2)
        v = (dx + dy) / math.sqrt(2)
        if u > 0 and abs(v) < 2.2 and math.hypot(dx, dy) <= 6.2:
            px[x, y] = (0, 0, 0, 0)
# crystal seated at the centre of the head
for (x, y, c) in ((20, 10, CR[3]), (21, 10, CR[2]), (20, 11, CR[2]), (21, 11, CR[1]),
                  (19, 11, CR[1]), (20, 12, CR[1]), (22, 11, CR[0]), (21, 12, CR[0]), (19, 10, CR[2]), (20, 9, CR[2])):
    put(x, y, c)
# collar where the shaft meets the head
for (x, y, c) in ((16, 15, BR[3]), (17, 15, BR[2]), (16, 16, BR[2]), (15, 16, BR[3]), (17, 14, BR[3]), (18, 14, BR[2]), (15, 17, BR[1]), (16, 17, BR[1])):
    put(x, y, c)
im.save('wand.png')
out = Image.new('RGBA', (N * 10 + 160, N * 10 + 40), (14, 16, 20, 255))
b = im.resize((N * 10, N * 10), Image.NEAREST)
out.paste(b, (20, 20), b)
m = im.resize((64, 64), Image.NEAREST)
out.paste(m, (N * 10 + 50, 40), m)
out.paste(im, (N * 10 + 66, 130), im)
out.save('prev_wand.png')
print('ok')
