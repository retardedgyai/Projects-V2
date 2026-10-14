"""Essentia jar v4 following YYbwr's bottle: opaque painted glass (light cyan) with the
essentia drawn as big colour masses inside, a 1px light rim on every face, few colours,
no noise. Brass foot and neck band as separate parts, dark cork, ProjectS-style label."""
from PIL import Image
from mcrender import render, box, outline


def hx(s, a=255):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def paint(fn, rim=None):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c:
                p[x, y] = hx(c)
    if rim:                      # rim = (light, dark): 1px light on top/left, dark on bottom/right
        lt, dk = rim
        for i in range(16):
            p[i, 0] = hx(lt)
            p[0, i] = hx(lt)
            p[i, 15] = hx(dk)
            p[15, i] = hx(dk)
    return im


GL = ['#5aa6c4', '#86c8de', '#b4e2ee', '#e2f8fc']          # painted glass
BR = ['#6a4410', '#a07818', '#d4a838', '#f4d870', '#fff4b8']
CORK = ['#3a2214', '#583420', '#74482c']
ESS = {
    'aqua': ['#123a7a', '#1e5aa8', '#2f82cc', '#5ab0e8'],
    'ignis': ['#7a1a10', '#b8341a', '#e8641e', '#ffa040'],
    'venenum': ['#3a1660', '#5c2a8c', '#8048b4', '#a874d8'],
    'victus': ['#1a4a1e', '#2e7a30', '#4ea648', '#86d064'],
}
# liquid surface line per column (irregular but calm), as a fraction of height from top
SURF = [6, 6, 5, 5, 5, 6, 6, 6, 7, 7, 6, 6, 5, 5, 6, 6]


def glass_side(ess):
    E = ESS[ess]

    def f(x, y):
        s = SURF[x]
        if y < s:                                   # empty glass above the liquid
            return GL[2] if x < 5 else GL[1]
        if y == s:
            return E[3]                             # meniscus highlight
        # liquid body: big masses, lighter on the left, deep pocket bottom-right
        if x >= 10 and y >= 11:
            return E[0]
        if x <= 3:
            return E[3] if y < 9 else E[2]
        if (x - 6) ** 2 + (y - 10) ** 2 < 5:
            return E[1]
        return E[2]
    return paint(f, rim=(GL[3], GL[0]))


def glass_top(x, y):
    return GL[3] if x < 6 else GL[2]


T = {}
for k in ESS:
    T['glass_' + k] = glass_side(k)
T['glass_top'] = paint(glass_top, rim=(GL[3], GL[0]))
T['brass'] = paint(lambda x, y: BR[3] if y < 6 else BR[2] if y < 12 else BR[1], rim=(BR[4], BR[0]))
T['brass_top'] = paint(lambda x, y: BR[3] if x + y < 14 else BR[2], rim=(BR[4], BR[1]))
T['cork'] = paint(lambda x, y: CORK[2] if x < 5 else CORK[1], rim=(CORK[2], CORK[0]))
T['cork_top'] = paint(lambda x, y: CORK[0] if 5 <= x <= 10 and 5 <= y <= 10 else CORK[2] if x + y < 14 else CORK[1], rim=(CORK[2], CORK[0]))


def label(ess):
    E = ESS[ess]
    drop = {4: (7, 8), 5: (7, 8), 6: (6, 9), 7: (6, 9), 8: (5, 10), 9: (5, 10), 10: (5, 10), 11: (6, 9)}

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return BR[1]
        if x in (1, 14) or y in (1, 14):
            return BR[3]
        if y in drop and drop[y][0] <= x <= drop[y][1]:
            return E[3] if x < 7 and y < 9 else E[1]
        return '#f2e4b8' if x < 8 else '#e2d2a2'
    return paint(f)


for k in ESS:
    T['label_' + k] = label(k)


def M(ess='aqua'):
    full = [0, 0, 16, 16]
    g = {'north': 'glass_' + ess, 'south': 'glass_' + ess, 'east': 'glass_' + ess, 'west': 'glass_' + ess, 'up': 'glass_top', 'down': 'glass_top'}
    E = [
        box((3.5, 0, 3.5), (12.5, 1.0, 12.5), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}),
        box((4, 1, 4), (12, 11, 12), g, uv={k: full for k in g}),
        box((5.5, 11, 5.5), (10.5, 12, 10.5), g),
        box((5, 12, 5), (11, 13, 11), {'north': 'brass', 'south': 'brass', 'east': 'brass', 'west': 'brass', 'up': 'brass_top', 'down': 'brass'}),
        box((5.5, 13, 5.5), (10.5, 15.5, 10.5), {'north': 'cork', 'south': 'cork', 'east': 'cork', 'west': 'cork', 'up': 'cork_top', 'down': 'cork'}, uv={'up': full}),
        box((5.5, 3.0, 12.0), (10.5, 8.5, 12.25), 'label_' + ess, faces=('south',), uv={'south': full}),
    ]
    return {'elements': E}


if __name__ == '__main__':
    out = Image.new('RGBA', (4 * 250 + 20, 270), (14, 16, 20, 255))
    for i, k in enumerate(ESS):
        im = render(M(k), T, size=128, scale=0.8, light=True, rot_y=45, bevel=False)
        im = outline(im, (255, 255, 255, 60))
        im.save(f'jar4_{k}.png')
        big = im.resize((240, 240), Image.NEAREST)
        out.paste(big, (20 + i * 250, 15), big)
    out.save('prev_jar4.png')
    print('ok')
