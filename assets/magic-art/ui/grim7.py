"""Grimoire v7 art, drawn at 1 art px = 1 screen px so it matches the 32px icons and 16px text.
f7_frame.png 1280x720  carved wood frame: stepped moulding, brass strip with domed studs, iron
                       filigree corners, iron mullion, plaque, winged crest with a faceted gem.
f7_neb_<k>.png 724x568 per-branch magic space: hard-edged rim-lit cloud masses, 3 kinds of stars.
f7_node_<s>.png 48x48  brass node mounts per state (studs, rim light, halo).
f7_page.png 408x568    parchment with burnt edge steps and a pin.
f7_div.png 360x9       ornamental divider.   f7_tab.png / f7_tab_on.png 56x52 leather tabs."""
import math
import random
from PIL import Image, ImageDraw

WD = ['#0c0705', '#1a100b', '#28180f', '#382216', '#4a2e1e', '#5e3c28', '#764e34']
IRN = ['#0a0a0e', '#1a1a22', '#2c2c36', '#44444e', '#62626c', '#8a8a94', '#b4b4bc']
BRS = ['#4a2e0c', '#7a5414', '#a87c22', '#d4a838', '#f0cc5a', '#fff0a8']
CY = ['#123a5a', '#1f6fa8', '#49b4d9', '#a8ecf6', '#f0ffff']
PG = ['#8c7450', '#a8906a', '#c2aa80', '#d6c096', '#e2d0a8', '#ecdcb6']


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def canvas(w, h):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return im, im.load(), ImageDraw.Draw(im)


def R(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=hx(c))


# =====================================================================  frame
W, H = 1280, 720
im, px, d = canvas(W, H)
FX0, FY0, FX1, FY1 = 48, 44, 1256, 712
TOP = 116                       # windows start
MAP = (76, TOP, 800, 684)
PAGE = (820, TOP, 1228, 684)
rnd = random.Random(7)

# wood body: planks with continuous, gently waving grain lines (no random scratches)
R(d, FX0, FY0, FX1, FY1, WD[3])
for y in range(FY0, FY1):
    for x in range(FX0, FX1):
        side = x < MAP[0] or x >= PAGE[2] or (MAP[2] <= x < PAGE[0])
        u, v = (y, x) if side else (x, y)
        wave = int(2 * math.sin(u / 47.0 + v * 0.3))
        g = (v + wave) % 7
        c = WD[3]
        if g == 0:
            c = WD[2]
        elif g == 3 and (u // 23) % 3 == 0:
            c = WD[4]
        if (v // 7) % 5 == 2 and g == 0:
            c = WD[1]
        px[x, y] = hx(c)
# stepped moulding around the outside: dark line, light edge, body, groove, brass, groove
def frame_rect(x0, y0, x1, y1, col_tl, col_br, w=1):
    for k in range(w):
        d.line([x0 + k, y0 + k, x1 - 1 - k, y0 + k], fill=hx(col_tl))
        d.line([x0 + k, y0 + k, x0 + k, y1 - 1 - k], fill=hx(col_tl))
        d.line([x0 + k, y1 - 1 - k, x1 - 1 - k, y1 - 1 - k], fill=hx(col_br))
        d.line([x1 - 1 - k, y0 + k, x1 - 1 - k, y1 - 1 - k], fill=hx(col_br))

frame_rect(FX0, FY0, FX1, FY1, WD[0], WD[0])
frame_rect(FX0 + 1, FY0 + 1, FX1 - 1, FY1 - 1, WD[6], WD[1], 2)
frame_rect(FX0 + 12, FY0 + 12, FX1 - 12, FY1 - 12, WD[1], WD[5])            # carved groove
frame_rect(FX0 + 13, FY0 + 13, FX1 - 13, FY1 - 13, WD[0], WD[0])
# brass strip
frame_rect(FX0 + 14, FY0 + 14, FX1 - 14, FY1 - 14, BRS[4], BRS[1])
frame_rect(FX0 + 15, FY0 + 15, FX1 - 15, FY1 - 15, BRS[3], BRS[2], 2)
frame_rect(FX0 + 17, FY0 + 17, FX1 - 17, FY1 - 17, BRS[1], BRS[0])
frame_rect(FX0 + 18, FY0 + 18, FX1 - 18, FY1 - 18, WD[0], WD[0])


def stud(cx, cy):
    for y in range(-2, 3):
        for x in range(-2, 3):
            if x * x + y * y <= 5:
                c = BRS[2]
                if x + y <= -2:
                    c = BRS[5] if (x, y) == (-1, -1) else BRS[4]
                elif x + y >= 2:
                    c = BRS[0]
                px[cx + x, cy + y] = hx(c)


for x in range(FX0 + 40, FX1 - 40, 32):
    if not (440 < x < 840):
        stud(x, FY0 + 16)
    stud(x, FY1 - 17)
for y in range(FY0 + 48, FY1 - 40, 32):
    stud(FX0 + 16, y); stud(FX1 - 17, y)

# window bevels: inner wood bevel lit from top-left -> the window looks recessed
for (x0, y0, x1, y1) in (MAP, PAGE):
    frame_rect(x0 - 6, y0 - 6, x1 + 6, y1 + 6, WD[1], WD[6], 1)
    frame_rect(x0 - 5, y0 - 5, x1 + 5, y1 + 5, WD[2], WD[5], 2)
    frame_rect(x0 - 3, y0 - 3, x1 + 3, y1 + 3, BRS[1], BRS[3], 1)
    frame_rect(x0 - 2, y0 - 2, x1 + 2, y1 + 2, WD[0], WD[0], 2)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=(0, 0, 0, 0))

# iron mullion between the windows
R(d, 803, TOP - 8, 817, 692, IRN[3])
d.line([803, TOP - 8, 803, 691], fill=hx(IRN[5])); d.line([804, TOP - 8, 804, 691], fill=hx(IRN[4]))
d.line([816, TOP - 8, 816, 691], fill=hx(IRN[1]))
for y in range(TOP + 20, 680, 46):
    for (dx, dy) in ((0, 0), (1, 0), (0, 1)):
        px[808 + dx, y + dy] = hx(IRN[6] if (dx, dy) == (0, 0) else IRN[5])
    px[810, y + 2] = hx(IRN[1])
# small gem in the mullion middle
for y in range(-6, 7):
    for x in range(-5, 6):
        if abs(x) + abs(y) <= 6:
            c = CY[2] if x + y < -2 else CY[1] if x + y < 2 else CY[0]
            if abs(x) + abs(y) == 6:
                c = BRS[2]
            px[810 + x, 400 + y] = hx(c)


# iron corner plates inside the band: L plate, a diamond boss, two studs
def corner(cx, cy, sx, sy):
    for i in range(0, 30):
        for j in range(0, 6):
            for (x, y) in ((cx + sx * i, cy + sy * j), (cx + sx * j, cy + sy * i)):
                c = IRN[3]
                if i == 0 or j == 0:
                    c = IRN[5]
                if j == 5 or i == 29:
                    c = IRN[1]
                px[x, y] = hx(c)
    for y in range(-6, 7):
        for x in range(-6, 7):
            if abs(x) + abs(y) <= 6:
                c = IRN[5] if x + y < -1 else IRN[4] if x + y < 2 else IRN[2]
                if abs(x) + abs(y) == 6:
                    c = IRN[1]
                px[cx + sx * 9 + x, cy + sy * 9 + y] = hx(c)
    px[cx + sx * 9 - 1, cy + sy * 9 - 1] = hx(CY[3]); px[cx + sx * 9, cy + sy * 9] = hx(CY[2])
    stud(cx + sx * 22, cy + sy * 3); stud(cx + sx * 3, cy + sy * 22)


corner(FX0 + 3, FY0 + 3, 1, 1); corner(FX1 - 4, FY0 + 3, -1, 1)
corner(FX0 + 3, FY1 - 4, 1, -1); corner(FX1 - 4, FY1 - 4, -1, -1)

# plaque in the top band
PX0, PX1, PY0, PY1 = 470, 810, 62, 106
R(d, PX0, PY0, PX1, PY1, WD[1])
frame_rect(PX0, PY0, PX1, PY1, BRS[4], BRS[0], 1)
frame_rect(PX0 + 1, PY0 + 1, PX1 - 1, PY1 - 1, BRS[3], BRS[1], 2)
frame_rect(PX0 + 3, PY0 + 3, PX1 - 3, PY1 - 3, WD[0], WD[2], 1)
for (x, y) in ((PX0 + 8, PY0 + 8), (PX1 - 9, PY0 + 8), (PX0 + 8, PY1 - 9), (PX1 - 9, PY1 - 9)):
    stud(x, y)
# flourish ends on the plaque
for s in (-1, 1):
    bx = PX0 - 2 if s < 0 else PX1 + 1
    for i in range(14):
        y = (PY0 + PY1) // 2 + int(5 * math.sin(i / 14 * math.pi * 2))
        px[bx + s * i, y] = hx(BRS[3]); px[bx + s * i, y + 1] = hx(BRS[1])

# crest: feathered wings + faceted gem, breaking out above the frame
CX, CYc = 640, 30
for s in (-1, 1):
    for k in range(7):                                    # back feathers first (longest)
        k2 = 6 - k
        L = 70 + k2 * 20
        ang = math.radians(8 + k2 * 6)                   # fan upward a little
        root = (CX + s * 22, CYc + 4 + k2 * 2)
        tip = (root[0] + s * L * math.cos(ang), root[1] - L * math.sin(ang) + k2 * 6)
        wid = 9 - k2 // 2
        nx, ny = -(tip[1] - root[1]), (tip[0] - root[0])
        nl = math.hypot(nx, ny); nx, ny = nx / nl, ny / nl
        if s < 0:
            nx, ny = -nx, -ny
        mid = ((root[0] + tip[0]) / 2, (root[1] + tip[1]) / 2)
        poly = [root, (mid[0] - nx * wid * 0.2, mid[1] - ny * wid * 0.2), tip,
                (mid[0] + nx * wid, mid[1] + ny * wid), (root[0] + nx * wid * 0.8, root[1] + ny * wid * 0.8)]
        fill = IRN[3] if k % 2 else IRN[4]
        d.polygon(poly, fill=hx(fill), outline=hx(IRN[1]))
        d.line([root, (mid[0] - nx * wid * 0.2, mid[1] - ny * wid * 0.2), tip], fill=hx(IRN[6]))
        d.line([(root[0] + nx * 2, root[1] + ny * 2), (tip[0] + nx * 1, tip[1] + ny * 1)], fill=hx(IRN[2]))
# gem setting + faceted gem
for y in range(-26, 27):
    for x in range(-24, 25):
        dd = abs(x) * 1.0 + abs(y) * 0.92
        if dd <= 25:
            c = IRN[3]
            if dd > 23:
                c = IRN[6] if (x < 0 and y < 0) else IRN[1]
            elif dd <= 17:
                # 4 facets + table
                if dd <= 6:
                    c = CY[4] if x + y < 0 else CY[3]
                elif x <= 0 and y <= 0:
                    c = CY[3]
                elif x > 0 and y <= 0:
                    c = CY[2]
                elif x <= 0 and y > 0:
                    c = CY[1]
                else:
                    c = CY[0]
                if dd > 15.5:
                    c = BRS[3] if (x < 0 and y < 0) else BRS[1]
            px[CX + x, CYc + y] = hx(c)
px[CX - 6, CYc - 8] = hx('#ffffff'); px[CX - 5, CYc - 8] = hx('#ffffff'); px[CX - 6, CYc - 7] = hx('#ffffff')
# pendant at the bottom
for y in range(0, 12):
    for x in range(-8, 9):
        if abs(x) + y <= 10:
            px[640 + x, 708 + y] = hx(IRN[4] if x < 0 else IRN[2]) if abs(x) + y < 9 else hx(IRN[1])
px[640, 712] = hx(CY[3]); px[640, 713] = hx(CY[2])
im.save('f7_frame.png')

# =====================================================================  nebulas
PAL = {
    'trunk': ['#05060f', '#0a1028', '#121c44', '#1b2e62', '#25497e', '#327494', '#5fb8c8'],
    'koku': ['#0c0804', '#1c1206', '#33200a', '#4e3410', '#735018', '#a07a28', '#e0c060'],
    'chu': ['#0c0406', '#1c080e', '#330e18', '#4e1622', '#73222e', '#a03a38', '#e07a60'],
    'sho': ['#08040e', '#140a24', '#22103c', '#341a5a', '#4c2a7e', '#6e48a6', '#b090e0'],
    'ren': ['#040a06', '#0a180e', '#122a16', '#1c4020', '#2a5c2c', '#45803e', '#a0d07a'],
    'chi': ['#0a0604', '#180e08', '#2a1a0e', '#402816', '#5a3c20', '#80603a', '#d0a870'],
    'mad': ['#04060c', '#0a101e', '#121e36', '#1c3050', '#284870', '#406a96', '#98c0e8'],
}
NW, NH = 724, 568
for key, pal in PAL.items():
    rr = random.Random(key)
    img, p, dr = canvas(NW, NH)
    G = [(rr.uniform(0, NW), rr.uniform(0, NH), rr.uniform(120, 260), rr.uniform(80, 180), rr.uniform(0.45, 0.85)) for _ in range(8)]
    ph = [rr.uniform(0, 6.3) for _ in range(6)]
    TH = [0.26, 0.52, 0.82, 1.12, 1.42]
    lvmap = [[0] * NW for _ in range(NH)]
    for y in range(NH):
        for x in range(NW):
            v = 0.0
            for (cx, cy, sx, sy, a_) in G:
                v += a_ * math.exp(-(((x - cx) / sx) ** 2 + ((y - cy) / sy) ** 2))
            v += 0.2 * math.sin(x / 61.0 + ph[0]) * math.sin(y / 47.0 + ph[1])
            v += 0.13 * math.sin(x / 23.0 + y / 31.0 + ph[2]) + 0.1 * math.sin(x / 37.0 - y / 19.0 + ph[4])
            v += 0.04 * math.sin(x / 11.0 - y / 13.0 + ph[3])
            lv = sum(1 for t in TH if v > t)
            lvmap[y][x] = lv
            p[x, y] = hx(pal[lv])
    for y in range(1, NH):                      # rim light on the upper-left edge of the bright levels
        for x in range(1, NW):
            lv = lvmap[y][x]
            if lv >= 3 and lvmap[y - 1][x - 1] < lv:
                p[x, y] = hx(pal[min(6, lv + 1)])
    # stars
    for _ in range(420):
        x, y = rr.randrange(1, NW - 1), rr.randrange(1, NH - 1)
        p[x, y] = hx(pal[6] if rr.random() < 0.25 else pal[5])
    for _ in range(40):
        x, y = rr.randrange(3, NW - 3), rr.randrange(3, NH - 3)
        for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            p[x + dx, y + dy] = hx('#ffffff' if (dx, dy) == (0, 0) else pal[6])
    for _ in range(9):
        x, y = rr.randrange(6, NW - 6), rr.randrange(6, NH - 6)
        for t in range(1, 5):
            c = hx(pal[6]) if t < 3 else hx(pal[5])
            for (dx, dy) in ((t, 0), (-t, 0), (0, t), (0, -t)):
                p[x + dx, y + dy] = c
        for (dx, dy) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            p[x + dx, y + dy] = hx(pal[5])
        p[x, y] = hx('#ffffff')
    img.save(f'f7_neb_{key}.png')

# =====================================================================  node mounts
def mount(name, rim, inner, halo=None, dash=False, studs=True):
    img, p, dr = canvas(48, 48)
    c0 = 23.5
    for y in range(48):
        for x in range(48):
            dd = math.hypot(x - c0, y - c0)
            if halo and 21.5 <= dd < 23.5:
                p[x, y] = hx(halo[0], halo[1])
            if 17 <= dd < 21.5:
                if dash and int((math.atan2(y - c0, x - c0) + 4) * 5) % 2:
                    continue
                t = (x - c0) + (y - c0)
                c = rim[3] if t < -14 else rim[2] if t < 0 else rim[1] if t < 14 else rim[0]
                if 20.5 <= dd:
                    c = rim[0]
                if dd < 18:
                    c = rim[0] if t < 0 else rim[3]           # inner lip: shadow top-left, light bottom-right
                p[x, y] = hx(c)
            elif dd < 17 and inner:
                p[x, y] = hx(inner[0] if (x - c0) + (y - c0) > 6 else inner[1])
    if studs:
        for (sx, sy) in ((24, 4), (24, 43), (4, 24), (43, 24)):
            for (dx, dy, c) in ((0, 0, rim[3]), (-1, 0, rim[2]), (0, -1, rim[3]), (-1, -1, '#ffffff'), (0, 1, rim[0]), (1, 0, rim[0])):
                p[sx + dx, sy + dy] = hx(c)
    img.save(f'f7_node_{name}.png')


GOLD = ['#5a3a10', '#a07818', '#d4a838', '#f4d870']
SILV = ['#1f4a6a', '#49b4d9', '#a8ecf6', '#f0ffff']
mount('done', GOLD, ('#140f06', '#1e170a'), halo=('#d4a838', 90))
mount('open', SILV, ('#06141e', '#0a2030'), halo=('#49b4d9', 130))
mount('glow', ['#49b4d9', '#a8ecf6', '#f0ffff', '#ffffff'], None, halo=('#a8ecf6', 200), studs=False)
mount('locked', ['#16161c', '#2a2a32', '#3c3c46', '#54545e'], ('#08080c', '#0c0c12'))
mount('side', ['#3a2460', '#7a5aa8', '#b090e0', '#e0d0ff'], ('#0e0818', '#140c22'), halo=('#8a6ab0', 110), dash=True)
mount('goal', ['#5a1810', '#a03024', '#e07a60', '#ffc0a0'], ('#140606', '#1e0a0a'), halo=('#b04a3a', 120))
mount('hidden', ['#14182a', '#20263c', '#2c3450', '#3a4466'], None, dash=True, studs=False)

# =====================================================================  parchment page
PW, PH = 408, 568
img, p, dr = canvas(PW, PH)
for y in range(PH):
    for x in range(PW):
        e = min(x, y, PW - 1 - x, PH - 1 - y)
        c = PG[5] if e > 22 else PG[4] if e > 10 else PG[3] if e > 4 else PG[2] if e > 1 else PG[0]
        p[x, y] = hx(c)
rr = random.Random(3)
for _ in range(60):                                       # faint fibres
    x, y = rr.randrange(14, PW - 30), rr.randrange(14, PH - 14)
    L = rr.randrange(6, 22)
    for i in range(L):
        if p[x + i, y] == hx(PG[5]):
            p[x + i, y] = hx(PG[4])
for y in range(PH - 22, PH):                              # curled corner
    for x in range(PW - 22, PW):
        t = (x - (PW - 22)) + (y - (PH - 22))
        if t > 21:
            p[x, y] = (0, 0, 0, 0)
        elif t > 17:
            p[x, y] = hx(PG[1] if t < 20 else PG[0])
# pin
for y in range(0, 14):
    for x in range(-4, 5):
        if x * x + (y - 4) ** 2 <= 16:
            p[PW // 2 + x, y + 2] = hx(IRN[5] if x + (y - 4) < -1 else IRN[3] if x + (y - 4) < 3 else IRN[1])
img.save('f7_page.png')

# divider: line with a diamond in the middle and dots
img, p, dr = canvas(360, 9)
for x in range(360):
    if 160 <= x <= 200:
        continue
    if x % 6 != 5:
        p[x, 4] = hx(PG[1]); p[x, 5] = hx(PG[3])
for y in range(9):
    for x in range(-8, 9):
        if abs(x) + abs(y - 4) <= 4:
            p[180 + x, y] = hx('#8a5a10' if abs(x) + abs(y - 4) == 4 else '#d4a838')
for s in (-1, 1):
    p[180 + s * 12, 4] = hx('#8a5a10'); p[180 + s * 16, 4] = hx('#8a5a10')
img.save('f7_div.png')

# =====================================================================  tabs
for name, lit in (('f7_tab', False), ('f7_tab_on', True)):
    img, p, dr = canvas(56, 52)
    LEc = ['#1a0c08', '#2e1610', '#44221a', '#5a3024', '#743e2e'] if not lit else ['#0a1a24', '#123048', '#1a4868', '#22608a', '#3a80b0']
    dr.rounded_rectangle([0, 0, 63, 51], radius=6, fill=hx(LEc[3]))
    for y in range(52):
        for x in range(56):
            if p[x, y][3]:
                c = LEc[4] if y < 3 or x < 3 else LEc[1] if y > 47 else LEc[3] if y < 26 else LEc[2]
                p[x, y] = hx(c)
    for y in range(4, 48):                                   # brass edge cap on the left
        p[1, y] = hx(BRS[4]); p[2, y] = hx(BRS[2]); p[3, y] = hx(BRS[1])
    stud(8, 8); stud(8, 43)
    img.save(f'{name}.png')

# smaller node icons for the 3 model props (32px, fitted)
for f, o in (('desk9.png', 'f7_ic_desk'), ('distiller6.png', 'f7_ic_distiller'), ('jar4_aqua.png', 'f7_ic_jar')):
    s = Image.open(f).convert('RGBA'); s = s.crop(s.getbbox())
    w, h = s.size; k = 32 / max(w, h)
    s = s.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
    c = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); c.paste(s, ((32 - s.size[0]) // 2, (32 - s.size[1]) // 2), s)
    c.save(f'{o}.png')
print('ok')
