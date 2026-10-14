from PIL import Image
import math


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


N = 32
WOOD = ['#2a150b', '#4e2a16', '#7a4426', '#a6623a', '#cc8a58']
BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
CR = ['#1f6fa8', '#49b4d9', '#9fe7f6', '#f0ffff']
im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()
# axis from bottom-left (3,28) to top-right (27,4)
ox, oy = 3.0, 29.0
ux, uy = 1 / math.sqrt(2), -1 / math.sqrt(2)     # along the shaft
vx, vy = 1 / math.sqrt(2), 1 / math.sqrt(2)      # perpendicular (+v = bottom-right side)


def uv(x, y):
    dx, dy = x + 0.5 - ox, y + 0.5 - oy
    return dx * ux + dy * uy, dx * vx + dy * vy


HEAD0 = 25.0            # where the head starts along u
for y in range(N):
    for x in range(N):
        u, v = uv(x, y)
        c = None
        # shaft
        if 0.5 <= u <= HEAD0 and abs(v) <= 1.25:
            tone = 3 if v < -0.4 else 2 if v < 0.45 else 1
            c = WOOD[tone]
            # brass bands
            for b in (5.0, 13.0, 21.5):
                if abs(u - b) <= 0.9:
                    c = BR[4 if v < -0.4 else 3 if v < 0.45 else 1]
        # pommel
        if -1.2 <= u < 0.5 and abs(v) <= 1.9:
            c = BR[3] if v < 0 else BR[1]
        # head: spanner with two parallel prongs and an open slot
        if HEAD0 - 1 <= u <= HEAD0 + 9.2 and abs(v) <= 4.4:
            slot = u > HEAD0 + 3.2 and abs(v) < 1.7
            if not slot:
                tone = 3 if v < -1.2 else 2 if v < 1.4 else 1
                if abs(v) > 3.6:
                    tone = 4 if v < 0 else 0
                if u > HEAD0 + 8.2:
                    tone = max(0, tone - 1)
                c = BR[tone]
        if c:
            px[x, y] = hx(c)
# chamfer the four outer corners of the head so it is not a plain square
for y in range(N):
    for x in range(N):
        u, v = uv(x, y)
        if (u > HEAD0 + 8.4 and abs(v) > 3.4) or (u < HEAD0 - 0.2 and abs(v) > 3.2 and u > HEAD0 - 1.1):
            px[x, y] = (0, 0, 0, 0)
# crystal seated at the throat of the jaw (the only glow)
for y in range(N):
    for x in range(N):
        u, v = uv(x, y)
        if HEAD0 + 1.0 <= u <= HEAD0 + 3.6 and abs(v) <= 1.3:
            d = (u - (HEAD0 + 2.3)) - v
            px[x, y] = hx(CR[3] if d < -1.0 else CR[2] if d < 0.2 else CR[1] if d < 1.2 else CR[0])
im.save('wand.png')
out = Image.new('RGBA', (N * 10 + 160, N * 10 + 40), (14, 16, 20, 255))
b = im.resize((N * 10, N * 10), Image.NEAREST)
out.paste(b, (20, 20), b)
m = im.resize((64, 64), Image.NEAREST)
out.paste(m, (N * 10 + 50, 40), m)
out.paste(im, (N * 10 + 66, 130), im)
out.save('prev_wand.png')
print('ok')
