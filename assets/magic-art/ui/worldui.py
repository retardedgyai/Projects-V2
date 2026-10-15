"""1:1 art for the in-world magic HUD pieces (matches grimoire v7).
w_toast.png   480x104  parchment slip with brass corner caps and a round 'magic window' (nebula
                       cut-out in a brass ring) on the left for the node mount.
w_tracker.png 344x112  pinned parchment card with the same round window."""
import math
import random
from PIL import Image

PG = ['#8c7450', '#a8906a', '#c2aa80', '#d6c096', '#e2d0a8', '#ecdcb6']
BRS = ['#4a2e0c', '#7a5414', '#a87c22', '#d4a838', '#f0cc5a', '#fff0a8']
IRN = ['#0a0a0e', '#1a1a22', '#2c2c36', '#44444e', '#62626c', '#8a8a94', '#b4b4bc']
NEB = Image.open('f7_neb_trunk.png').convert('RGBA')


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def slip(w, h, seed):
    rr = random.Random(seed)
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    p = im.load()
    top = [0] * w
    bot = [0] * w
    for x in range(w):
        for y in range(top[x], h - bot[x]):
            e = min(x, y - top[x], w - 1 - x, h - 1 - bot[x] - y)
            c = PG[5] if e > 10 else PG[4] if e > 5 else PG[3] if e > 2 else PG[2] if e > 0 else PG[0]
            p[x, y] = hx(c)
    for _ in range(w // 8):                                           # faint fibres
        x, y = rr.randrange(8, w - 30), rr.randrange(8, h - 8)
        for i in range(rr.randrange(5, 18)):
            if p[x + i, y] == hx(PG[5]):
                p[x + i, y] = hx(PG[4])
    return im, p


def window(p, cx, cy, r, src_xy):
    sx, sy = src_xy
    for y in range(-r - 4, r + 5):
        for x in range(-r - 4, r + 5):
            d = math.hypot(x, y)
            if d <= r:
                p[cx + x, cy + y] = NEB.getpixel((sx + x, sy + y))
            elif d <= r + 1:
                p[cx + x, cy + y] = hx(BRS[0])
            elif d <= r + 3:
                t = x + y
                p[cx + x, cy + y] = hx(BRS[4] if t < -r else BRS[3] if t < 0 else BRS[2] if t < r else BRS[1])
            elif d <= r + 4:
                p[cx + x, cy + y] = hx(BRS[0])


def caps(p, w, h):
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)):
        for i in range(10):
            for j in range(3):
                for (x, y) in ((cx + sx * i, cy + sy * j), (cx + sx * j, cy + sy * i)):
                    c = BRS[3] if j == 1 else (BRS[4] if (sx > 0 and sy > 0) or j == 0 else BRS[1])
                    p[x, y] = hx(c)
        p[cx + sx * 5, cy + sy * 5] = hx(BRS[4]); p[cx + sx * 6, cy + sy * 5] = hx(BRS[2])


# toast
im, p = slip(480, 104, 1)
window(p, 54, 52, 36, (150, 120))
caps(p, 480, 104)
im.save('w_toast.png')

# tracker (pinned)
im, p = slip(344, 112, 2)
window(p, 46, 60, 30, (560, 470))
for y in range(0, 12):                                                # pin
    for x in range(-4, 5):
        if x * x + (y - 4) ** 2 <= 16:
            p[172 + x, y + 2] = hx(IRN[5] if x + (y - 4) < -1 else IRN[3] if x + (y - 4) < 3 else IRN[1])
im.save('w_tracker.png')
print('ok')
