"""Essentia jar v2: octagonal glass body, brass straps, wooden lid with brass rim,
glowing essentia. Per-face painted textures."""
from PIL import Image
from mcrender import render, box


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def canvas():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


BR = ['#3a2410', '#6e4e1e', '#a8823a', '#d8b862', '#f6e8a8']
OAK = ['#2a150b', '#4e2c18', '#73422a', '#975c38', '#bb7c4c']
T = {}

# glass side: clear with a vertical highlight streak on the left, darker right edge, faint tint
g = canvas()
p = g.load()
for y in range(16):
    for x in range(16):
        p[x, y] = (185, 225, 230, 22)
for y in range(1, 15):
    p[1, y] = hx('#f2ffff', 230)
    p[2, y] = hx('#cfeff2', 140)
    p[14, y] = hx('#5e8a92', 170)
for y in (2, 3, 4):
    p[4, y] = hx('#f2ffff', 200)
for x in range(16):
    p[x, 0] = hx('#bfe6ea', 200)
    p[x, 15] = hx('#4e7a82', 220)
T['glass'] = g


def liquid(ramp, name):
    side = canvas()
    q = side.load()
    for y in range(16):
        for x in range(16):
            s = 2 if 4 <= x <= 11 else (3 if x < 4 else 1)
            if y >= 13:
                s = max(0, s - 1)
            q[x, y] = hx(ramp[s])
    for (x, y) in ((5, 10), (6, 5)):   # two small bubbles on the lit side
        q[x, y] = hx(ramp[3])
    for x in range(16):
        q[x, 0] = hx(ramp[4] if x < 8 else ramp[3])                 # meniscus
    top = canvas()
    t = top.load()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            t[x, y] = hx(ramp[4] if d < 4 else ramp[3] if d < 9 else ramp[2])
    T[name] = side
    T[name + '_top'] = top
    lb = canvas()
    q = lb.load()
    for y in range(16):
        for x in range(16):
            q[x, y] = hx('#e8dcb6' if x < 8 else '#d6c8a0')
            if x in (0, 15) or y in (0, 15):
                q[x, y] = hx('#a8986e')
    drop = {3: (7, 8), 4: (7, 8), 5: (6, 9), 6: (6, 9), 7: (5, 10), 8: (5, 10), 9: (5, 10), 10: (5, 10), 11: (6, 9), 12: (7, 8)}
    for yy, (a, c) in drop.items():
        for xx in range(a, c + 1):
            q[xx, yy] = hx(ramp[2] if xx < 8 else ramp[1])
    q[6, 7] = hx(ramp[4])
    T[name + '_label'] = lb


liquid(['#0e2c55', '#1d63a0', '#3fa5d6', '#9fe3f4', '#f2fdff'], 'aqua')
liquid(['#5c1610', '#b8341a', '#ec7a26', '#ffc552', '#fff4cc'], 'ignis')
liquid(['#3a1a46', '#6a2e7e', '#9a52b0', '#c88ad8', '#f0d8fa'], 'venenum')

# brass strap with a 1px fret line in the middle
b = canvas()
q = b.load()
for y in range(16):
    for x in range(16):
        s = 3 if y < 5 else 2 if y < 11 else 1
        if y == 0:
            s = 4
        if y == 15:
            s = 0
        q[x, y] = hx(BR[s])
for x in range(16):
    if x % 4 != 3:
        q[x, 7] = hx(BR[1])
    if x % 4 == 1:
        q[x, 6] = hx(BR[1])
        q[x, 8] = hx(BR[1])
T['strap'] = b

# lid wood (side): two tones + a brass nail
w = canvas()
q = w.load()
for y in range(16):
    for x in range(16):
        s = 3 if y < 6 else 2 if y < 12 else 1
        if (x * 5 + y) % 9 == 0:
            s = max(1, s - 1)
        q[x, y] = hx(OAK[s])
q[7, 7] = hx(BR[4])
q[8, 7] = hx(BR[2])
T['lid'] = w
# lid top: concentric rings (turned wood) with a brass knob socket
lt = canvas()
q = lt.load()
for y in range(16):
    for x in range(16):
        d = max(abs(x - 7.5), abs(y - 7.5))
        s = 3 if int(d) % 3 == 0 else 2
        if x + y < 8:
            s = 4
        if x + y > 24:
            s = 1
        q[x, y] = hx(OAK[s])
T['lid_top'] = lt
# brass solid (for knob and rim)
bb = canvas()
q = bb.load()
for y in range(16):
    for x in range(16):
        q[x, y] = hx(BR[4] if x + y < 6 else BR[3] if x + y < 16 else BR[2] if x + y < 24 else BR[1])
T['brass'] = bb
# label: parchment with a small aspect drop and a brass-pinned border
lb = canvas()
q = lb.load()
for y in range(16):
    for x in range(16):
        q[x, y] = hx('#e8dcb6' if x < 8 else '#d6c8a0')
        if x in (0, 15) or y in (0, 15):
            q[x, y] = hx('#a8986e')
drop = {3: (7, 8), 4: (7, 8), 5: (6, 9), 6: (6, 9), 7: (5, 10), 8: (5, 10), 9: (5, 10), 10: (5, 10), 11: (6, 9), 12: (7, 8)}
for y, (a, c) in drop.items():
    for x in range(a, c + 1):
        q[x, y] = hx('#3fa5d6' if x < 8 else '#1d63a0')
q[6, 7] = hx('#f2fdff')
T['label'] = lb


def jar_model(liq):
    L = {'north': liq, 'south': liq, 'east': liq, 'west': liq, 'up': liq + '_top', 'down': liq}
    els = []
    # liquid core (octagon = two crossed boxes)
    els.append(box((5, 1, 6), (11, 8.5, 10), L, emissive=True))
    els.append(box((6, 1, 5), (10, 8.5, 11), L, emissive=True))
    # glass body (octagon)
    els.append(box((4, 0.5, 5.5), (12, 11, 10.5), 'glass'))
    els.append(box((5.5, 0.5, 4), (10.5, 11, 12), 'glass'))
    # base ring and neck strap (brass)
    els.append(box((3.6, 0, 5.2), (12.4, 1.2, 10.8), 'strap'))
    els.append(box((5.2, 0, 3.6), (10.8, 1.2, 12.4), 'strap'))
    els.append(box((3.7, 10.2, 5.2), (12.3, 11.4, 10.8), 'strap'))
    els.append(box((5.2, 10.2, 3.7), (10.8, 11.4, 12.3), 'strap'))
    # lid (narrower neck then a cap with lip)
    els.append(box((5.2, 11.6, 5.2), (10.8, 12.6, 10.8), {'north': 'lid', 'south': 'lid', 'east': 'lid', 'west': 'lid', 'up': 'lid_top', 'down': 'lid'}))
    els.append(box((4.6, 12.6, 4.6), (11.4, 13.5, 11.4), {'north': 'lid', 'south': 'lid', 'east': 'lid', 'west': 'lid', 'up': 'lid_top', 'down': 'lid'}))
    els.append(box((7.0, 13.5, 7.0), (9.0, 14.6, 9.0), 'brass'))
    # label on the front
    els.append(box((5.8, 2.6, 12.02), (10.2, 8.2, 12.15), liq + '_label', faces=('south',), uv={'south': [0, 0, 16, 16]}))
    return {'elements': els}


if __name__ == '__main__':
    imgs = []
    for liq in ('aqua', 'ignis', 'venenum'):
        im = render(jar_model(liq), T, size=128, scale=0.8, light=True, rot_y=45)
        im.save(f'icon3d_jar_{liq}.png')
        imgs.append(im)
    sheet = Image.new('RGBA', (3 * 270 + 20, 290), (14, 16, 20, 255))
    for i, im in enumerate(imgs):
        big = im.resize((256, 256), Image.NEAREST)
        sheet.paste(big, (20 + i * 270, 20), big)
    sheet.save('prev_jar2.png')
    print('ok')
