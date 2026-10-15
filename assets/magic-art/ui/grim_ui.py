"""Art for the 'magic window' grimoire (1 art px = 2 screen px).
frame.png 640x360   carved dark-wood + iron frame with two windows (map / page), studs, iron corner
                    brackets, a winged sigil crest breaking the top edge, a title plaque, a pendant.
neb_<k>.png 362x302 per-branch magic space: stepped nebula masses + stars.
gring_<s>.png 28x28 glowing node rings for a dark background.
page.png 212x302    parchment research note pinned in the right window.
tab.png / tab_on.png 30x30 wooden bookmark tabs."""
import math
import random
from PIL import Image

WD = ['#0e0806', '#1c110c', '#2c1b12', '#3e271a', '#533524', '#6a4630']
IRN = ['#0c0c10', '#1e1e26', '#34343e', '#50505c', '#74747e', '#a0a0aa']
BRS = ['#5a3a10', '#a07818', '#d4a838', '#f4d870']
CY = ['#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
PG = ['#9c8460', '#b8a078', '#cdb68c', '#dcc89e', '#e6d6b0']


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def new(w, h):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return im, im.load()


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = hx(c) if isinstance(c, str) else c


# ---------------- frame ----------------
W, H = 640, 360
fr, fp = new(W, H)
OX0, OY0, OX1, OY1 = 24, 22, 636, 354
MAP = (40, 62, 398, 338)
PAGE = (410, 62, 620, 338)
# wood band: horizontal grain on top/bottom, vertical on the sides; flat masses
for y in range(OY0, OY1):
    for x in range(OX0, OX1):
        inside_map = MAP[0] <= x < MAP[2] and MAP[1] <= y < MAP[3]
        inside_page = PAGE[0] <= x < PAGE[2] and PAGE[1] <= y < PAGE[3]
        if inside_map or inside_page:
            continue
        side = x < MAP[0] or x >= PAGE[2] or (MAP[2] <= x < PAGE[0])
        g = (x // 3) if side else (y // 3)
        c = WD[3] if g % 5 else WD[2]
        if (x + 2 * y) % 41 == 0:
            c = WD[2]
        fp[x, y] = hx(c)
# outer rim: light top/left, dark bottom/right
rect(fp, OX0, OY0, OX1, OY0 + 1, WD[5]); rect(fp, OX0, OY0, OX0 + 1, OY1, WD[5])
rect(fp, OX0, OY1 - 1, OX1, OY1, WD[0]); rect(fp, OX1 - 1, OY0, OX1, OY1, WD[0])
rect(fp, OX0 + 1, OY0 + 1, OX1 - 1, OY0 + 2, WD[4]); rect(fp, OX0 + 1, OY0 + 1, OX0 + 2, OY1 - 1, WD[4])
# window bevels: dark inner lip with iron line (recessed)
for (x0, y0, x1, y1) in (MAP, PAGE):
    rect(fp, x0 - 2, y0 - 2, x1 + 2, y0, IRN[1]); rect(fp, x0 - 2, y0 - 2, x0, y1 + 2, IRN[1])
    rect(fp, x0 - 2, y1, x1 + 2, y1 + 2, IRN[3]); rect(fp, x1, y0 - 2, x1 + 2, y1 + 2, IRN[3])
    rect(fp, x0 - 3, y0 - 3, x1 + 3, y0 - 2, WD[0]); rect(fp, x0 - 3, y0 - 3, x0 - 2, y1 + 3, WD[0])
# mullion: an iron bar between the windows
rect(fp, 400, 58, 408, 342, IRN[2]); rect(fp, 400, 58, 401, 342, IRN[4]); rect(fp, 407, 58, 408, 342, IRN[0])
for y in range(70, 336, 24):
    rect(fp, 401, y, 407, y + 5, IRN[3]); fp[401, y] = hx(IRN[5])
# brass studs along the band
def stud(x, y):
    rect(fp, x, y, x + 3, y + 3, BRS[1]); fp[x, y] = hx(BRS[3]); fp[x + 1, y] = hx(BRS[2]); fp[x, y + 1] = hx(BRS[2])
    fp[x + 2, y + 2] = hx(BRS[0])
for x in range(40, 628, 22):
    if not (196 < x < 444):
        stud(x, 28); stud(x, 346)
    else:
        stud(x, 346)
for y in range(70, 340, 22):
    stud(29, y); stud(628, y)
# carved moulding line inside the band
rect(fp, OX0 + 5, OY0 + 5, OX1 - 5, OY0 + 6, WD[1]); rect(fp, OX0 + 5, OY1 - 6, OX1 - 5, OY1 - 5, WD[4])
rect(fp, OX0 + 5, OY0 + 5, OX0 + 6, OY1 - 5, WD[1]); rect(fp, OX1 - 6, OY0 + 5, OX1 - 5, OY1 - 5, WD[4])
# iron corner brackets (L plates with rivets)
def bracket(cx, cy, dx, dy):
    for i in range(18):
        for j in range(5):
            for (x, y) in ((cx + dx * i, cy + dy * j), (cx + dx * j, cy + dy * i)):
                c = IRN[3] if (i + j) % 7 else IRN[2]
                if j == 0 or i == 0:
                    c = IRN[4]
                fp[x, y] = hx(c)
    for (i, j) in ((3, 2), (10, 2), (2, 10), (16, 2), (2, 16)):
        fp[cx + dx * i, cy + dy * j] = hx(IRN[5]); fp[cx + dx * i + dx, cy + dy * j + dy] = hx(IRN[0])
bracket(OX0, OY0, 1, 1); bracket(OX1 - 1, OY0, -1, 1); bracket(OX0, OY1 - 1, 1, -1); bracket(OX1 - 1, OY1 - 1, -1, -1)
# crest: winged sigil breaking the top edge
CX = 320
def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        fp[x, y] = hx(c)
for s in (-1, 1):                                   # wings: 6 feathers, stepped iron blades
    for k in range(6):
        L = 104 - k * 14
        y0 = 4 + k * 4
        for t in range(L):
            x = CX + s * (18 + t)
            yy = y0 + int(t * (0.10 + k * 0.04)) - int(5 * math.sin(t / L * math.pi))
            th = max(2, 6 - t * 5 // max(L, 1))
            for d in range(th):
                c = IRN[5] if d == 0 else IRN[4] if d == 1 else IRN[3] if d < th - 1 else IRN[2]
                put(x, yy + d, c)
            put(x, yy + th, IRN[0])
for y in range(0, 42):                               # diamond sigil
    for x in range(CX - 21, CX + 22):
        d = abs(x - CX) + abs(y - 20)
        if d <= 20:
            c = IRN[3] if d > 16 else WD[1]
            if d in (19, 20):
                c = IRN[5] if (x <= CX and y <= 20) else IRN[1]
            if d <= 9:
                c = CY[1] if d > 6 else CY[2] if d > 3 else CY[3]
            if d == 10:
                c = IRN[4]
            put(x, y, c)
# plaque under the crest
PX0, PX1, PY0, PY1 = 236, 404, 38, 58
rect(fp, PX0, PY0, PX1, PY1, WD[1])
rect(fp, PX0, PY0, PX1, PY0 + 1, BRS[2]); rect(fp, PX0, PY0, PX0 + 1, PY1, BRS[2])
rect(fp, PX0, PY1 - 1, PX1, PY1, BRS[0]); rect(fp, PX1 - 1, PY0, PX1, PY1, BRS[0])
for (x, y) in ((PX0 + 3, PY0 + 3), (PX1 - 6, PY0 + 3), (PX0 + 3, PY1 - 6), (PX1 - 6, PY1 - 6)):
    stud(x, y)
# pendant at the bottom centre
for y in range(350, 360):
    for x in range(CX - 6, CX + 7):
        if abs(x - CX) + (y - 350) <= 7:
            put(x, y, IRN[3] if abs(x - CX) + (y - 350) < 6 else IRN[1])
put(CX, 353, CY[2]); put(CX, 354, CY[1])
fr.save('frame.png')

# ---------------- nebulas ----------------
PAL = {
    'trunk': ['#07091a', '#0f1636', '#1a2a5a', '#23497a', '#2f7a9a', '#6ad0d8'],
    'koku': ['#120c06', '#2a1a0a', '#4a3010', '#7a5a1a', '#b8902a', '#f4d870'],
    'chu': ['#12060a', '#2a0c14', '#4a1420', '#7a2430', '#b04a3a', '#f08a6a'],
    'sho': ['#0c0614', '#1c0e30', '#32185a', '#4e2a86', '#7a5aa8', '#c8a8f0'],
    'ren': ['#06100a', '#0e2414', '#18401e', '#2a6430', '#5a8a4a', '#b8e08a'],
    'chi': ['#100a06', '#24160c', '#3e2814', '#5e4020', '#8a6a42', '#e0b880'],
    'mad': ['#060a12', '#0e1628', '#182a48', '#264a70', '#4a6a9a', '#a8c8f0'],
}
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
NW, NH = 358, 276
for k, pal in PAL.items():
    rnd = random.Random(k)
    blobs = [(rnd.randint(30, NW - 30), rnd.randint(30, NH - 30), rnd.randint(70, 150), rnd.uniform(0.6, 1.0)) for _ in range(6)]
    im, p = new(NW, NH)
    for y in range(NH):
        for x in range(NW):
            v = 0.0
            for (bx, by, r, w) in blobs:
                d = math.hypot((x - bx) * 0.8, y - by)
                v += max(0.0, 1 - d / r) * w
            v = min(v, 1.6) / 1.6 * 4.0                       # 0..4 tone levels
            lv = int(v)
            frac = v - lv
            if frac * 16 > BAYER[y % 4][x % 4] + 8:          # dither only near band edges
                lv += 1
            p[x, y] = hx(pal[min(lv, 4)])
    for _ in range(140):                                     # stars
        x, y = rnd.randrange(2, NW - 2), rnd.randrange(2, NH - 2)
        p[x, y] = hx(pal[5] if rnd.random() < 0.3 else pal[4])
    for _ in range(9):
        x, y = rnd.randrange(4, NW - 4), rnd.randrange(4, NH - 4)
        for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            p[x + dx, y + dy] = hx('#ffffff' if (dx, dy) == (0, 0) else pal[5])
    im.save(f'neb_{k}.png')

# ---------------- glowing node rings ----------------
def gring(name, ring, fill, halo=None, dashed=False, inner=None, width=2):
    im, p = new(28, 28)
    for y in range(28):
        for x in range(28):
            d = math.hypot(x - 13.5, y - 13.5)
            if fill and d < 12 - width + 0.5:
                p[x, y] = hx(fill)
            if 12 - width + 0.5 <= d < 12.5:
                if dashed and int((math.atan2(y - 13.5, x - 13.5) + 4) * 4) % 2:
                    continue
                p[x, y] = hx(ring[0] if x + y < 27 else ring[1])
            if inner and 9.4 <= d < 10.3:
                p[x, y] = hx(inner)
            if halo and 12.5 <= d < 13.9:
                p[x, y] = hx(halo[0], halo[1])
    im.save(f'gring_{name}.png')

gring('done', ('#f4d870', '#a07818'), '#1a1408', halo=('#d4a838', 110), inner='#6a4410')
gring('open', ('#a8ecf6', '#49b4d9'), '#0a2030', halo=('#49b4d9', 120))
gring('glow', ('#f0ffff', '#a8ecf6'), None, halo=('#a8ecf6', 200))
gring('locked', ('#5a6278', '#3a4058'), '#0c0e18', dashed=False, width=1)
gring('side', ('#c8a8f0', '#7a5aa8'), '#160c24', halo=('#7a5aa8', 110), dashed=True)
gring('goal', ('#f08a6a', '#a03024'), '#1a0808', halo=('#b04a3a', 120), inner='#d4a838', width=3)
gring('hidden', ('#3a4058', '#2a3048'), None, dashed=True, width=1)

# ---------------- parchment page ----------------
PW, PH = 210, 276
im, p = new(PW, PH)
for y in range(PH):
    for x in range(PW):
        d = min(x, y, PW - 1 - x, PH - 1 - y)
        c = PG[4] if d > 10 else PG[3] if d > 4 else PG[2] if d > 1 else PG[0]
        if (x - 26) ** 2 + (y - 240) ** 2 < 120 and c == PG[4]:
            c = PG[3]
        if x + (PH - 1 - y) < 16 and x > PW:   # (reserved)
            c = PG[1]
        p[x, y] = hx(c)
for y in range(PH - 14, PH):                 # curled bottom-right corner
    for x in range(PW - 14, PW):
        if (x - (PW - 14)) + (y - (PH - 14)) > 13:
            p[x, y] = (0, 0, 0, 0)
        elif (x - (PW - 14)) + (y - (PH - 14)) > 10:
            p[x, y] = hx(PG[1])
im.save('page.png')

# ---------------- tabs ----------------
for name, base in (('tab', WD), ('tab_on', ['#1c110c', '#2c1b12', '#3e271a', '#533524', '#6a4630', '#86603e'])):
    im, p = new(30, 30)
    for y in range(30):
        for x in range(30):
            cut = max(0, 4 - x) if (y < 4 - min(x, 4) or y > 25 + min(x, 4)) else 0
            if x < 4 and (y < 4 - x or y > 25 + x):
                continue
            c = base[4] if y < 3 else base[3] if y < 22 else base[2]
            if x == 0 or y == 0:
                c = base[5]
            if y == 29:
                c = base[0]
            p[x, y] = hx(c)
    for y in range(4, 26):
        p[0, y] = hx(BRS[2] if name == 'tab_on' else BRS[1])
    im.save(f'{name}.png')
print('ok')
