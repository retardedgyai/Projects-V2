from PIL import Image, ImageDraw, ImageFont
import math
exec(open('aspect32b.py', encoding='utf-8').read().split('# ---------- Ignis')[0])
BRASS = ['#2c1b0b', '#5a3a18', '#8a6a2c', '#c9a94e', '#f2e6a0']
SILVER = ['#1e2126', '#454b55', '#7d8592', '#b9c0ca', '#eef2f6']
C = 15.5


def generic(mask, ramp, bg, glow, frame, detail=None):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = im.load()

    def ins(x, y):
        return 0 <= x < N and 0 <= y < N and mask(x if x <= 15 else 31 - x, y)

    for y in range(N):
        for x in range(N):
            f = frame_px(x, y)
            if f is None:
                continue
            if f != 'in':
                px[x, y] = hx(frame[f])
                continue
            px[x, y] = hx(glow if math.hypot(x - C, y - C) < 8 else bg)
            if ins(x, y):
                g = (x - C) + (y - C)
                s = 3 if g < -5 else 2 if g < 4 else 1
                if not ins(x - 1, y) or not ins(x, y - 1):
                    s = max(s, 3)
                if not ins(x + 1, y) or not ins(x, y + 1):
                    s = min(s, 1)
                if not ins(x, y + 1) and not ins(x + 1, y):
                    s = 0
                px[x, y] = hx(ramp[s])
    if detail:
        detail(px, ins, ramp)
    return im


def put(px, x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = hx(c)


def D(x, y, cx, cy):
    return math.hypot(x + 0.5 - cx, y + 0.5 - cy)


# ---------- masks (x is the left half incl. column 15) ----------
def m_aer(x, y):
    if not 8 <= y <= 25:
        return False
    band = (y - 8) % 5
    if band == 4:
        return False
    hw = 1.5 + (25 - y) * 0.42 + (0.8 if band == 0 else 0)
    return 16 - (x + 0.5) <= hw


def m_ordo(x, y):
    dx = abs(x + 0.5 - 16)
    dy = abs(y + 0.5 - 16)
    return dx ** 0.55 + dy ** 0.55 <= 3.75


def m_perd(x, y):
    dx = 16 - (x + 0.5)            # >0 on the left half
    # left shard is the orb shifted 1px left and 1px up
    if D(x + 1, y + 1, 16, 16.5) > 7.4:
        return False
    if dx < 1.6:                   # central rift
        return False
    if abs((y + 0.5) - (16 + (dx - 4) * 0.35)) < 0.7 and dx > 2:   # side crack
        return False
    return True


def m_lux(x, y):
    dx = x + 0.5 - 16
    dy = y + 0.5 - 16
    d = math.hypot(dx, dy)
    if d <= 4.8:
        return True
    a = math.degrees(math.atan2(dy, dx)) % 45
    return 6.5 <= d <= 9.5 and (a < 9 or a > 36)


def m_tem(x, y):
    cloud = D(x, y, 16, 12) <= 4.6 or D(x, y, 11.2, 14.2) <= 3.4 or (12 <= y <= 17 and abs(x + 0.5 - 16) <= 6.5 and D(x, y, 16, 13.5) <= 7.4)
    cloud = cloud and y <= 17
    dx = abs(x + 0.5 - 16)
    rain = 19 <= y <= 26 and ((dx <= 0.5 and y % 3 != 0) or (abs(dx - 5) <= 0.5 and y % 3 != 1))
    return cloud or rain


def m_mot(x, y):
    dx = abs(x + 0.5 - 16)
    for top in (7, 15):
        yy = y + 0.5 - top
        if 0 <= yy <= 8 and yy * 1.05 - 3.6 <= dx <= yy * 1.05 and dx <= 8:
            return True
    return False


def m_vac(x, y):
    d = D(x, y, 16, 16)
    return 6.2 <= d <= 8.6 and abs(x + 0.5 - 16) > 1.2


def m_vic(x, y):
    dx = x + 0.5 - 16
    stem = abs(dx) <= 1 and 12 <= y <= 25
    a = math.radians(-35)
    lx, ly = dx + 5.2, y + 0.5 - 13.6
    u = lx * math.cos(a) - ly * math.sin(a)
    v = lx * math.sin(a) + ly * math.cos(a)
    leaf = (u / 3.9) ** 2 + (v / 1.9) ** 2 <= 1
    soil = 23 <= y <= 25 and abs(dx) <= 6 - (y - 23)
    bud = D(x, y, 16, 10.5) <= 1.6
    return stem or leaf or soil or bud


def m_gel(x, y):
    dx = x + 0.5 - 16
    dy = y + 0.5 - 16
    for ang in (90, 30, -30):
        a = math.radians(ang)
        ux, uy = math.cos(a), math.sin(a)
        t = dx * ux + dy * uy
        n = -dx * uy + dy * ux
        if abs(t) <= 9.5 and abs(n) <= 1.0:
            return True
        for k in (5.5, -5.5):
            if abs(t - k) <= 2.4 and abs(abs(n) - abs(t - k)) <= 0.9 and abs(t) >= 3:
                return True
    return math.hypot(dx, dy) <= 2.2


def m_ven(x, y):
    dx = x + 0.5 - 16
    cap = y <= 17 and ((dx / 9.2) ** 2 + ((y + 0.5 - 17) / 8.6) ** 2) <= 1
    stem = 17 < y <= 25 and abs(dx) <= 2.6 + (y - 17) * 0.08
    return cap or stem


def m_pot(x, y):
    dx = abs(x + 0.5 - 16)
    dy = y + 0.5 - 16
    return dx / 7.2 + abs(dy + 1.5) / 9.8 <= 1 and dy >= -8.6


def m_mor(x, y):
    dx = x + 0.5 - 16
    cran = D(x, y, 16, 13.5) <= 7.4 and y <= 19
    jaw = 18 <= y <= 23 and abs(dx) <= 4.4
    eye = D(x, y, 12.6, 14.5) <= 2.0
    nose = 18 <= y <= 19 and abs(dx) <= 0.9
    teeth = y == 22 and abs(dx) <= 3.5 and int(x) % 2 == 0
    return (cran or jaw) and not eye and not nose and not teeth


def m_met(x, y):
    dx = abs(x + 0.5 - 16)
    yy = y + 0.5
    top = 10 <= yy <= 15 and dx <= 5.5 + (yy - 10) * 0.9
    front = 15 < yy <= 22 and dx <= 10
    return top or front


# ---------- details ----------
def d_ordo(px, ins, r):
    for y in range(N):
        for x in range(N):
            if ins(x, y) and abs(x - 15.5) <= 0.5 and abs(y - 15.5) <= 0.5:
                put(px, x, y, r[4])
            elif ins(x, y) and max(abs(x - 15.5), abs(y - 15.5)) <= 1.5:
                put(px, x, y, r[1])


def d_perd(px, ins, r):
    # violet glow leaking from the rift
    for y in range(10, 24):
        put(px, 15, y, '#9a62d8' if 13 <= y <= 20 else '#4a2e6e')
        put(px, 16, y, '#d8b4ff' if 15 <= y <= 18 else '#6a46a0')


def d_lux(px, ins, r):
    for y in range(N):
        for x in range(N):
            if D(x, y, 16, 16) <= 2.6:
                put(px, x, y, r[4])


def d_vac(px, ins, r):
    for y in range(N):
        for x in range(N):
            dd = D(x, y, 16, 16)
            if dd < 6.0:
                put(px, x, y, '#020103' if dd < 4.5 else '#120c1e')
    for (x, y, c) in ((13, 13, r[4]), (18, 14, r[3]), (15, 18, r[3]), (17, 11, r[2])):
        put(px, x, y, c)


def d_vic(px, ins, r):
    soil = ['#3a2a1a', '#5a4226', '#7a5a34']
    for y in range(23, 26):
        for x in range(N):
            if ins(x, y):
                put(px, x, y, soil[2] if y == 23 else soil[1 if x < 16 else 0])


def d_gel(px, ins, r):
    for y in range(14, 18):
        for x in range(14, 18):
            if ins(x, y):
                put(px, x, y, r[4] if x + y < 31 else r[3])


def d_ven(px, ins, r):
    for (cx, cy, rr) in ((12, 12, 1.6), (20, 12, 1.6), (16, 8.5, 1.2), (9.5, 15, 1.0), (22.5, 15, 1.0)):
        for y in range(N):
            for x in range(N):
                if ins(x, y) and y <= 17 and D(x, y, cx, cy) <= rr:
                    put(px, x, y, '#f0d8fa' if x < 16 else '#c88ad8')
    for y in range(18, 26):
        for x in range(N):
            if ins(x, y):
                put(px, x, y, '#e8e0c8' if x < 16 else '#b8ae94')


def d_pot(px, ins, r):
    for y in range(N):
        for x in range(N):
            if ins(x, y):
                if y < 14 and x < 16:
                    put(px, x, y, r[3])
                if 12 <= y <= 13 and 13 <= x <= 15:
                    put(px, x, y, r[4])
                if y >= 18 and x >= 16:
                    put(px, x, y, r[1])


def d_met(px, ins, r):
    for y in range(N):
        for x in range(N):
            if ins(x, y) and 10 <= y <= 14:
                put(px, x, y, r[3] if x < 20 else r[2])
            if ins(x, y) and y == 15:
                put(px, x, y, r[4] if x < 16 else r[3])


def d_tem(px, ins, r):
    for y in range(19, 27):
        for x in range(N):
            if ins(x, y):
                put(px, x, y, r[3] if x < 16 else r[2])


SPEC = [
    ('Aer', '風', m_aer, ['#3a4a2a', '#6e8a4a', '#a8c47a', '#dbecaa', '#f8ffe0'], '#0b0f08', '#141a0e', BRASS, None),
    ('Ordo', '秩序', m_ordo, ['#4a4030', '#8a7c5c', '#c8b890', '#ece0bc', '#fffaea'], '#0e0c08', '#18150f', BRASS, d_ordo),
    ('Perditio', '混沌', m_perd, ['#1e1626', '#4a3a5a', '#7e6894', '#b9a0c8', '#e8dcf0'], '#0a070e', '#130e1a', BRASS, d_perd),
    ('Lux', '光', m_lux, ['#5a3a10', '#a8741c', '#e8b43a', '#ffe07a', '#fffbe0'], '#110b03', '#1d1406', SILVER, d_lux),
    ('Tempestas', '嵐', m_tem, ['#123236', '#2a6468', '#58a2a4', '#a4d5d7', '#effcfc'], '#050e10', '#0a1a1c', SILVER, d_tem),
    ('Motus', '運動', m_mot, ['#3a3020', '#6e5e40', '#a8946a', '#d6c7aa', '#fbf4e4'], '#0c0a07', '#16130d', SILVER, None),
    ('Vacuos', '虚空', m_vac, ['#140f1e', '#3a2e52', '#6c5a8a', '#ad9cc8', '#e6defa'], '#040308', '#0a0812', SILVER, d_vac),
    ('Victus', '生命', m_vic, ['#16301a', '#2f6a34', '#5fa45a', '#b5d89f', '#effae0'], '#070e08', '#0d180e', SILVER, d_vic),
    ('Gelum', '氷', m_gel, ['#163a4a', '#3a7a92', '#7cc0d4', '#b5dfe9', '#f4fdff'], '#051018', '#0a1c28', SILVER, d_gel),
    ('Venenum', '毒', m_ven, ['#3a1a46', '#6a2e7e', '#9a52b0', '#c88ad8', '#f0d8fa'], '#0b060e', '#150c1a', SILVER, d_ven),
    ('Potentia', '力', m_pot, ['#4a1e0c', '#9a4a1c', '#e08a3a', '#ffc27a', '#fff0d8'], '#100703', '#1c0e06', SILVER, d_pot),
    ('Mortuus', '死', m_mor, ['#1e1a1e', '#4a434a', '#857c84', '#c4bcc0', '#f2eef0'], '#080608', '#121014', SILVER, None),
    ('Metallum', '金属', m_met, ['#1e2228', '#4a525e', '#8090a0', '#c0ccd8', '#f4f8fc'], '#07080b', '#0f1116', SILVER, d_met),
]
icons = {}
for name, jp, m, r, bg, gl, fr, dt in SPEC:
    icons[name] = (jp, generic(m, r, bg, gl, fr, dt))
icons['Ignis'] = ('火', Image.open('aspect32_ignis.png'))
icons['Aqua'] = ('水', Image.open('aspect32_aqua.png'))
icons['Terra'] = ('地', Image.open('terra_opt_C.png'))
order = ['Aer', 'Aqua', 'Ignis', 'Terra', 'Ordo', 'Perditio', 'Lux', 'Tempestas', 'Motus', 'Vacuos', 'Victus', 'Gelum', 'Venenum', 'Potentia', 'Mortuus', 'Metallum']
F = ImageFont.truetype('C:/Windows/Fonts/meiryob.ttc', 16)
S = 6
cw = N * S + 24
out = Image.new('RGBA', (8 * cw + 24, 2 * (N * S + 56) + 20), (14, 16, 20, 255))
d = ImageDraw.Draw(out)
for i, n in enumerate(order):
    jp, im = icons[n]
    im.save(f'final_{n.lower()}.png')
    cx = 24 + (i % 8) * cw
    cy = 20 + (i // 8) * (N * S + 56)
    b = im.resize((N * S, N * S), Image.NEAREST)
    out.paste(b, (cx, cy), b)
    d.text((cx, cy + N * S + 6), f'{n}  {jp}', font=F, fill=(242, 240, 235))
out.save('prev_all16.png')
print('ok')
