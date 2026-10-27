"""The mage's fire bolt (2026-10-28, sample version 21: https://claude.ai/artifact/4C3q1PWvZQhzfeTNFsxJCJ, the
Creator: "この燃え方で火炎弾を決める"). The drawings are the sample's own (~/ProjectS/refs/mage-vfx/preview/src_fire.js),
one character per pixel, 16 px to a block, and every sprite is shown at exactly that size (CoreMageFire): a bigger
state is a bigger drawing, never a stretched one.

- ball_0..2: the bolt, a torn white-hot heart with a ragged tail (16 x 18), three drawings in turn.
- lick_0..2: a small flame (8 x 14): the charge at the staff and the flames dropped behind the ball.
- burst_0, burst_1: the hit, a small and a big drawing (24 and 40 px).
- tall_0..3: the standing flames (10 x 26) that wrap a burning enemy for the 4 s it burns.
- ember_0..4: one pixel, white-hot to dark red, the embers that pop out of a burning enemy.
Each drawing sits in a square canvas (the model is a centred square); a standing flame sits on the canvas's bottom
edge so CoreMageFire can stand it on the ground.
"""
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
DIR = 'combat_vfx/mage_fire'

FP = {'k': (106, 44, 28), 'r': (204, 68, 32), 'o': (232, 116, 44), 'y': (244, 180, 60), 'p': (252, 228, 132), 'w': (255, 251, 224)}
EMBERS = [(255, 240, 176), (255, 176, 64), (240, 74, 36), (184, 32, 24), (106, 20, 16)]


def smear(R, L, length):
    """Demon Reaper's smeared flame: rows of bars of hand-set lengths; white in the middle rows near the front,
    then pale, yellow, orange, red toward the ragged tail. Drawn pointing right."""
    H = len(R); mid = (H - 1) / 2; rows = []
    for y in range(H):
        row = ''
        for x in range(length):
            if x > R[y] or x < L[y]:
                row += '.'; continue
            d = R[y] - x; c = abs(y - mid)
            row += ('r' if x <= L[y] + 1 else 'o' if d == 0 and c > 2.5 else 'w' if c <= 1.5 and 2 <= d <= 12 else
                    'p' if c <= 2.5 and 1 <= d <= 16 else 'y' if c <= 3.5 and d <= 20 else 'o' if d <= 25 else 'r')
        rows.append(row)
    return rows


def upright(rows):
    """Turned so the front is the top."""
    h, w = len(rows), len(rows[0])
    return [''.join(rows[y][x] for y in range(h)) for x in range(w - 1, -1, -1)]


BALL_HEAD = ['......r..r......', '....rroorr.r....', '...royyyoorr....', '..royppppyor.r..', '.roypwwwwpyorr..', 'rroypwwwwwpyor..',
             '.oyppwwwwwppyor.', 'royypwwwwppyyorr', '.oyyppppppyyyor.', '.royyyyyyyyyork.']
BALL_TAIL = [
    ['..ooyoyyyoyoor..', '.r.royoroyooor..', '...ro.or.oroor..', '..r..or..r.or...', '.....r....r.....', '....k.....r..k..', '.......k........', '................'],
    ['.rooyyoyoyyoo...', '..roorooyorr.r..', '..oro.ro.or.r...', '...r.or..ror....', '..k...r...r.....', '.......r.....k..', '....k...........', '..........k.....'],
    ['..oroyyoyyoor.r.', '.rroyoroyoorr...', '..r.ror.oor.r...', '.r..or..r..or...', '....r...k..r....', '..k.........k...', '........r.......', '.....k..........']]
LICK = [upright(smear(R, [2, 1, 0, 0, 1, 0, 2, 3], 14)) for R in
        ([6, 9, 12, 13, 13, 11, 8, 5], [5, 10, 11, 13, 12, 12, 9, 6], [7, 8, 12, 12, 13, 10, 9, 4])]
TALL = [upright(smear(R, L, 26)) for R, L in (
    ([14, 19, 23, 25, 24, 25, 22, 18, 15, 10], [9, 4, 2, 0, 1, 0, 3, 5, 8, 11]),
    ([12, 17, 21, 24, 25, 23, 25, 20, 16, 11], [8, 5, 1, 2, 0, 1, 0, 4, 7, 10]),
    ([15, 18, 22, 25, 23, 24, 21, 19, 13, 9], [10, 6, 3, 0, 2, 0, 2, 6, 9, 12]),
    ([10, 16, 19, 22, 23, 21, 18, 14, 11, 7], [7, 4, 1, 0, 1, 0, 3, 5, 8, 10]))]
ORDER = 'rk oypw'


def burst(n, rays):
    c = (n - 1) / 2; rows = []
    for y in range(n):
        row = ''
        for x in range(n):
            dx, dy = x - c, y - c; r = math.hypot(dx, dy); ch = '.'
            if r < n * .14:
                ch = 'w' if r < n * .08 else 'p'
            else:
                for a, length, w in rays:
                    u = dx * math.cos(a) + dy * math.sin(a); v = abs(-dx * math.sin(a) + dy * math.cos(a))
                    if u < 0 or u > length or v > w * (1 - u / length) + .5:
                        continue
                    f = u / length
                    k = 'w' if f < .25 else 'p' if f < .45 else 'y' if f < .65 else 'o' if f < .85 else 'r'
                    if ch == '.' or ORDER.index(k) > ORDER.index(ch):
                        ch = k
            row += ch
        rows.append(row)
    return rows


BURST = [burst(24, [(0, 11, 2), (.8, 8, 1.5), (1.6, 10, 2), (2.4, 7, 1.5), (3.1, 11, 2), (3.9, 8, 1.5), (4.7, 10, 2), (5.5, 7, 1.5)]),
         burst(40, [(.2, 19, 3), (1, 13, 2), (1.8, 18, 3), (2.6, 12, 2), (3.3, 19, 3), (4.1, 13, 2), (4.9, 17, 3), (5.7, 12, 2)])]


def canvas(rows, standing=False):
    """The drawing in a square canvas: centred, or (a standing flame) on the bottom edge."""
    h, w = len(rows), len(rows[0]); n = max(w, h)
    img = Image.new('RGBA', (n, n))
    ox, oy = (n - w) // 2, (n - h if standing else (n - h) // 2)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((ox + x, oy + y), FP[ch] + (255,))
    return img


def sprites():
    out = {f'ball_{k}': canvas(BALL_HEAD + t) for k, t in enumerate(BALL_TAIL)}
    out.update({f'lick_{k}': canvas(r, standing=True) for k, r in enumerate(LICK)})
    out.update({f'burst_{k}': canvas(r) for k, r in enumerate(BURST)})
    out.update({f'tall_{k}': canvas(r, standing=True) for k, r in enumerate(TALL)})
    for k, c in enumerate(EMBERS):
        img = Image.new('RGBA', (1, 1)); img.putpixel((0, 0), c + (255,)); out[f'ember_{k}'] = img
    return out


def centred_model(tex):
    face = lambda uv: {'texture': '#0', 'uv': uv}
    return {'ambientocclusion': False, 'textures': {'0': tex, 'particle': tex}, 'elements': [
        {'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False,
         'faces': {'north': face([0, 0, 16, 16]), 'south': face([16, 0, 0, 16])}}]}


def build(assets, write):
    for name, img in sprites().items():
        path = assets / f'textures/{DIR}/{name}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        write(assets / f'models/{DIR}/{name}.json', centred_model(f'projects:{DIR}/{name}'))
        write(assets / f'items/{DIR}/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{DIR}/{name}'}})


def sheet(out):
    tiles = [img.resize((img.width * 4, img.height * 4), Image.NEAREST) for img in sprites().values()]
    w = sum(t.width + 4 for t in tiles); h = max(t.height for t in tiles)
    s = Image.new('RGBA', (w, h + 4), (18, 16, 25, 255)); x = 2
    for t in tiles:
        s.alpha_composite(t, (x, 2)); x += t.width + 4
    s.save(out)


if __name__ == '__main__':
    import json, sys
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, separators=(',', ':')) + '\n', encoding='utf-8')
    if len(sys.argv) > 1 and sys.argv[1] == '--sheet':
        sheet(Path(sys.argv[2]))
    else:
        build(PACK / 'assets/projects', write)
