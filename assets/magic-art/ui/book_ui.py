"""Pixel art for the open-book grimoire UI (drawn at 1 art px = 2 screen px).
book_spread.png 640x360: oxblood leather cover with brass corners, two parchment pages with
aged edges as flat masses, a stepped gutter shadow, page-stack edges, a faint ruled frame.
node_*.png 30x30: ink rings per state.  seal.png: red wax seal."""
from PIL import Image

W, H = 640, 360
LE = ['#160908', '#2a1210', '#3e1a14', '#55241a', '#6e3222']
BR = ['#5a3a10', '#a07818', '#d4a838', '#f4d870']
PG = ['#9c8460', '#b8a078', '#cdb68c', '#dcc89e', '#e6d6b0']    # parchment, dark (edge) -> light (centre)
INK = ['#2a1a10', '#4a3020', '#7a5a3a']


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = im.load()


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = hx(c) if isinstance(c, str) else c


# cover (leather), lit top-left rim, dark bottom-right
CX0, CY0, CX1, CY1 = 8, 14, 632, 350
rect(CX0, CY0, CX1, CY1, LE[3])
rect(CX0, CY0, CX1, CY0 + 1, LE[4]); rect(CX0, CY0, CX0 + 1, CY1, LE[4])
rect(CX0, CY1 - 1, CX1, CY1, LE[0]); rect(CX1 - 1, CY0, CX1, CY1, LE[0])
rect(CX0 + 3, CY0 + 3, CX1 - 3, CY0 + 4, LE[2]); rect(CX0 + 3, CY1 - 4, CX1 - 3, CY1 - 3, LE[2])   # tooled line
# brass corner caps
for (x, y, dx, dy) in ((CX0, CY0, 1, 1), (CX1 - 1, CY0, -1, 1), (CX0, CY1 - 1, 1, -1), (CX1 - 1, CY1 - 1, -1, -1)):
    for i in range(10):
        for j in range(10 - i):
            c = BR[3] if (i == 0 or j == 0) and dy > 0 else BR[2] if i + j < 7 else BR[1]
            px[x + dx * i, y + dy * j] = hx(c)
# page stacks under each page (outer and bottom edges), a few lines
for k in range(4):
    c = PG[1] if k % 2 == 0 else PG[0]
    rect(18 - k, 20 + k, 320, 21 + k, c) if False else None
    rect(22 - k - 1, 340 + k, 318, 341 + k, c)            # left bottom
    rect(322, 340 + k, 618 + k + 1, 341 + k, c)            # right bottom
    rect(22 - k - 1, 22, 22 - k, 340 + k, c)               # left outer
    rect(618 + k, 22, 619 + k, 340 + k, c)                 # right outer
# pages
LP = (22, 22, 318, 340)
RP = (322, 22, 618, 340)


STAINS = [(60, 300, 26), (90, 318, 14), (560, 52, 22), (585, 80, 12), (250, 70, 9), (470, 290, 11)]


def page(x0, y0, x1, y1, left):
    for x in range(x0, x1):
        t = (x - x0) / (x1 - x0) if left else (x1 - 1 - x) / (x1 - x0)
        dip = round(5 * t ** 4)                       # page surface curls down into the gutter
        top, bot = y0 + dip, y1 - round(3 * t ** 4)
        for y in range(top, bot):
            dout = min(y - top, bot - 1 - y, (x - x0) if left else (x1 - 1 - x))
            dg = (x1 - 1 - x) if left else (x - x0)
            c = PG[4]
            if dout < 2:
                c = PG[1]
            elif dout < 6:
                c = PG[2]
            elif dout < 14:
                c = PG[3]
            for (sx, sy, r) in STAINS:
                if (x - sx) ** 2 + (y - sy) ** 2 < r * r and c in (PG[4], PG[3]):
                    c = PG[3] if c == PG[4] else PG[2]
            if dg < 2:
                c = INK[2]
            elif dg < 6:
                c = PG[0]
            elif dg < 14:
                c = PG[1]
            elif dg < 30 and c in (PG[4], PG[3]):
                c = PG[2]
            elif dg < 54 and c == PG[4]:
                c = PG[3]
            px[x, y] = hx(c)
    m = 14
    fx0, fx1 = (x0 + m, x1 - m - 14) if left else (x0 + m + 14, x1 - m)
    for x in range(fx0, fx1):
        px[x, y0 + m + 4] = hx(PG[2]); px[x, y1 - m - 2] = hx(PG[2])
    for y in range(y0 + m + 4, y1 - m - 1):
        px[fx0, y] = hx(PG[2]); px[fx1 - 1, y] = hx(PG[2])


page(*LP, True)
page(*RP, False)
rect(318, 18, 322, 344, LE[1])           # spine gap between the pages
im.save('book_spread.png')


# --- node rings (30x30 art px, ring radius ~13) ---
def ring(name, col, fill=None, width=2, dashed=False, inner=None, glow=False):
    n = Image.new('RGBA', (30, 30), (0, 0, 0, 0))
    p = n.load()
    for y in range(30):
        for x in range(30):
            d = ((x - 14.5) ** 2 + (y - 14.5) ** 2) ** 0.5
            if fill and d < 13 - width + 0.5:
                p[x, y] = hx(fill)
            if 13 - width + 0.5 <= d < 13.5:
                if dashed and int((x + y) / 3) % 2:
                    continue
                c = col[0] if (x + y) < 29 else col[1]
                p[x, y] = hx(c)
            if inner and 10.0 <= d < 11.0:
                p[x, y] = hx(inner)
            if glow and 13.5 <= d < 15.0:
                p[x, y] = hx('#49b4d9', 140)
    n.save(f'node_{name}.png')


ring('done', (INK[1], INK[0]), fill=PG[3], inner='#d4a838')
ring('open', ('#49b4d9', '#1f6fa8'), fill='#e8f2e8')
ring('open_glow', ('#a8ecf6', '#49b4d9'), glow=True)
ring('locked', (PG[0], PG[0]), fill=None, width=1, dashed=True)
ring('side', ('#8a6ab0', '#5e4880'), fill=PG[3], dashed=True)
ring('goal', (INK[1], INK[0]), fill=PG[3], inner='#a03024', width=3)
ring('hidden', (PG[1], PG[1]), width=1, dashed=True)
# wax seal 14x14
s = Image.new('RGBA', (14, 14), (0, 0, 0, 0))
sp = s.load()
for y in range(14):
    for x in range(14):
        d = ((x - 6.5) ** 2 + (y - 6.5) ** 2) ** 0.5 + (0.8 if (x * 7 + y * 3) % 5 == 0 else 0)
        if d < 6.6:
            sp[x, y] = hx('#c0443a' if x + y < 11 else '#8a2a22' if d > 4 else '#a8362c')
        if 2.5 < d < 3.4:
            sp[x, y] = hx('#6a1c16')
s.save('seal.png')
print('ok')
