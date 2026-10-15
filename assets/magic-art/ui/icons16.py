"""16px inline icons for the grimoire page (drawn 1:1, lit from the top-left, flat masses).
i16_coin, i16_ink, i16_place, i16_see, i16_craft, i16_open, i16_get, i16_box, i16_check."""
import math
from PIL import Image


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def new():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    return im, im.load()


def disc(p, cx, cy, r, f):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                c = f(x, y, d)
                if c:
                    p[x, y] = hx(c)


SV = ['#3a3e48', '#6a707c', '#9ea4ae', '#d0d4da', '#f4f6f8']
# silver coin
im, p = new()
def coin(x, y, d):
    t = (x - 8) + (y - 8)
    if d > 6.0:
        return SV[1] if t < 0 else SV[0]
    if 3.6 < d <= 4.6:
        return SV[1] if t < 0 else SV[3]                 # embossed inner ring
    return SV[3] if t < -3 else SV[2] if t < 4 else SV[1]
disc(p, 8, 8, 7, coin)
p[5, 5] = hx(SV[4]); p[6, 5] = hx(SV[4]); p[5, 6] = hx(SV[4])
for y in (7, 8, 9):
    p[8, y] = hx(SV[1])
im.save('i16_coin.png')

# ink bottle
im, p = new()
GL = ['#0c1020', '#1c2a52', '#2c4280', '#6a88c0']
for y in range(6, 15):
    for x in range(4, 12):
        c = GL[2] if x < 6 else GL[1] if x < 10 else GL[0]
        if y == 6 or y == 14:
            c = GL[0]
        p[x, y] = hx(c)
for y in range(3, 6):
    for x in range(6, 10):
        p[x, y] = hx('#a8582a' if x < 8 else '#83401f')
for x in range(6, 10):
    p[x, 2] = hx('#c87640')
p[5, 8] = hx(GL[3]); p[5, 9] = hx(GL[3])
im.save('i16_ink.png')

# place: a small isometric block
im, p = new()
for y in range(16):
    for x in range(16):
        top = abs(x - 7.5) * 0.5 + y <= 6 and y >= 2 + abs(x - 7.5) * 0.5 - 4 and y >= 2
        if y < 2:
            continue
        # diamond top
        if abs(x - 7.5) / 7 + abs(y - 5) / 3.5 <= 1:
            p[x, y] = hx('#c8a070')
        elif 1 <= x <= 14 and 5 <= y <= 14 - abs(x - 7.5) * 0.45:
            p[x, y] = hx('#8a6040' if x < 8 else '#5e3e28')
for y in range(16):
    for x in range(16):
        if p[x, y][3] and (x in (1, 14) or y == 15):
            pass
p[7, 3] = hx('#ead0a0'); p[6, 4] = hx('#ead0a0')
im.save('i16_place.png')

# see: eye (almond drawn row by row: half-widths per row)
im, p = new()
HW = {4: 3, 5: 5, 6: 6, 7: 7, 8: 7, 9: 6, 10: 5, 11: 3}
for y, w in HW.items():
    for x in range(8 - w, 8 + w):
        p[x, y] = hx('#efe2bf')
    p[8 - w - 1 if y in (7, 8) else 8 - w, y] = hx('#2a1a10'); p[8 + w if y in (7, 8) else 8 + w - 1, y] = hx('#2a1a10')
for x in range(5, 11):
    p[x, 3] = hx('#2a1a10'); p[x, 12] = hx('#2a1a10')
p[3, 4] = hx('#2a1a10'); p[12, 4] = hx('#2a1a10'); p[3, 11] = hx('#2a1a10'); p[12, 11] = hx('#2a1a10')
p[2, 5] = hx('#2a1a10'); p[13, 5] = hx('#2a1a10'); p[2, 10] = hx('#2a1a10'); p[13, 10] = hx('#2a1a10')
disc(p, 8, 8, 3.4, lambda x, y, d: '#1f6fa8' if d > 1.6 else '#0c1020')
p[7, 6] = hx('#a8ecf6'); p[6, 7] = hx('#a8ecf6')
im.save('i16_see.png')

# craft: hammer
im, p = new()
for i in range(10):                                       # handle
    x, y = 3 + i, 13 - i
    p[x, y] = hx('#8a6040'); p[x + 1, y] = hx('#5e3e28')
for y in range(1, 7):                                     # head
    for x in range(8, 15):
        if abs((x - 11) - (y - 4)) <= 2 and abs((x - 11) + (y - 4)) <= 4:
            c = '#c8ccd4' if (x - 11) + (y - 4) < -1 else '#8a909c' if (x - 11) + (y - 4) < 2 else '#565c68'
            p[x, y] = hx(c)
im.save('i16_craft.png')

# open: open book
im, p = new()
for x in range(1, 15):
    curve = int(abs(x - 7.5) < 1)
    for y in range(4 + curve, 13):
        c = '#efe2bf' if x < 8 else '#d8c8a0'
        p[x, y] = hx(c)
    p[x, 13] = hx('#6e3222'); p[x, 3 + curve] = hx('#b8a078')
for y in range(4, 14):
    p[7, y] = hx('#8a7050'); p[8, y] = hx('#8a7050')
for y in (6, 8, 10):
    for x in range(2, 6):
        p[x, y] = hx('#8a7050')
    for x in range(10, 14):
        p[x, y] = hx('#8a7050')
im.save('i16_open.png')

# get: pouch
im, p = new()
disc(p, 8, 10, 5.6, lambda x, y, d: '#a87a48' if (x - 8) + (y - 10) < -2 else '#7a5430' if (x - 8) + (y - 10) < 3 else '#563a20')
for x in range(5, 11):
    p[x, 4] = hx('#7a5430')
for x in range(6, 10):
    p[x, 3] = hx('#a87a48')
for x in range(5, 11):
    p[x, 5] = hx('#d4a838')
p[6, 8] = hx('#c89a68')
im.save('i16_get.png')

# goal boxes
for name, checked in (('i16_box', False), ('i16_check', True)):
    im, p = new()
    for y in range(3, 15):
        for x in range(2, 14):
            if x in (2, 13) or y in (3, 14):
                p[x, y] = hx('#4a3020')
            else:
                p[x, y] = hx('#efe2bf' if not checked else '#e6d6b0')
    if checked:
        for (x, y) in ((4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4), (13, 3)):
            p[x, y] = hx('#2a7a3a'); p[x, y + 1] = hx('#1a5a28')
    im.save(f'{name}.png')

out = Image.new('RGBA', (16 * 9 * 6 + 80, 120), (230, 214, 176, 255))
for i, n in enumerate(['coin', 'ink', 'place', 'see', 'craft', 'open', 'get', 'box', 'check']):
    ic = Image.open(f'i16_{n}.png').resize((96, 96), Image.NEAREST)
    out.paste(ic, (8 + i * 100, 12), ic)
out.save('prev_icons16.png')
print('ok')
