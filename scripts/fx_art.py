"""The house style for skill and boss effect sprites, as code (2026-10-28, Creator: 「作風を固定する型から小さく作り始めて」).

Why this exists: the mage fire bolt took 21 sample versions, and the first seven each looked like a different
author's work because each one swapped the whole way of drawing (three.js boxes, a lava cube, smoke balls, a
Gorebound copy, a boss claw ...). Meanwhile the approved look already existed in three places that agree with each
other: scripts/warrior_vfx_art.py (hand-placed flat tones), the sakura / shade shader pieces, and the fire bolt's
version 21. This module is that look, so a first draft comes out in it without anyone re-deciding it:

  - 16 px to a block, and a sprite is shown at exactly that size. A bigger state is a bigger drawing, never a
    stretched one.
  - Flat tones, opaque pixels, no anti-aliasing, no gradients, no noise: alpha is 0 or 255, every colour is one of
    the ramp's six steps.
  - One ramp per element, dark edge to white-hot core, written as the letters `k r o y p w` (edge -> core) in the
    drawings. The same drawing in another ramp is the first draft of that element's effect: swap the ramp first,
    redraw only when the Creator wants a different shape.
  - Things appear whole and leave by breaking apart or sinking, never by fading or shrinking (the timing lives in
    the Kotlin, but the drawings come in frames that do that).

The drawings of the fire bolt (sample version 21, 藍「この燃え方で火炎弾を決める」) are kept here as the reference
set, so a new element's first look is `samples(RAMPS['frost'])`.

    python3 scripts/fx_art.py --sheet .tools/fx/style-kit.png    # the reference set in every ramp, with the glow
    python3 scripts/fx_art.py --check path/to/sprite.png ...     # is this sprite in the style?
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

PX_PER_BLOCK = 16
MAX_SIDE = 64          # four blocks: anything bigger is a shader job, not a sprite
TONES = 'kroypw'       # edge -> core: dark, red, orange, yellow, pale, white
EMPTY = '.'

# One ramp per element, dark edge to white-hot core. fire = the fire bolt's own palette; iron = the warrior's
# (warrior_vfx_art.py: outline, deep red, red, orange, cream, white core); sakura and shade = the item shader's
# sakuraRamp / ramp sampled at six steps; frost, lightning, wind = the mage rework's element colours
# (氷=水色, 雷=黄, 風=薄い緑) in the same edge-to-core shape.
RAMPS = {
    'fire':      {'k': (106, 44, 28), 'r': (204, 68, 32), 'o': (232, 116, 44), 'y': (244, 180, 60), 'p': (252, 228, 132), 'w': (255, 251, 224)},
    'frost':     {'k': (40, 72, 140), 'r': (64, 124, 200), 'o': (96, 170, 232), 'y': (132, 202, 242), 'p': (186, 230, 250), 'w': (236, 250, 255)},
    'lightning': {'k': (120, 92, 20), 'r': (196, 160, 28), 'o': (240, 206, 48), 'y': (250, 228, 96), 'p': (254, 244, 160), 'w': (255, 255, 236)},
    'wind':      {'k': (50, 108, 78), 'r': (84, 164, 116), 'o': (124, 206, 150), 'y': (166, 232, 182), 'p': (206, 246, 212), 'w': (242, 255, 246)},
    'iron':      {'k': (52, 8, 14), 'r': (140, 16, 30), 'o': (226, 40, 46), 'y': (255, 120, 40), 'p': (255, 214, 150), 'w': (255, 250, 236)},
    'sakura':    {'k': (36, 8, 23), 'r': (199, 51, 120), 'o': (246, 112, 180), 'y': (255, 150, 200), 'p': (255, 196, 224), 'w': (255, 236, 244)},
    'shade':     {'k': (26, 10, 41), 'r': (107, 51, 168), 'o': (150, 100, 224), 'y': (190, 146, 248), 'p': (226, 190, 252), 'w': (250, 232, 255)},
}

# The ember's five colours as it cools: white-hot to dark red, one pixel each (the fire bolt's ember_0..4).
EMBER_STEPS = ('w', 'y', 'o', 'r', 'k')


# ---------------------------------------------------------------- drawing helpers (text rows, one letter per pixel)
def smear(right, left, length):
    """A smeared flame pointing right: rows of bars with hand-set front (right) and tail (left) ends. White in the
    middle rows near the front, then pale, yellow, orange, red toward the ragged tail (Demon Reaper's jets)."""
    height = len(right); mid = (height - 1) / 2; rows = []
    for y in range(height):
        row = ''
        for x in range(length):
            if x > right[y] or x < left[y]:
                row += EMPTY; continue
            d = right[y] - x; c = abs(y - mid)
            row += ('r' if x <= left[y] + 1 else 'o' if d == 0 and c > 2.5 else 'w' if c <= 1.5 and 2 <= d <= 12 else
                    'p' if c <= 2.5 and 1 <= d <= 16 else 'y' if c <= 3.5 and d <= 20 else 'o' if d <= 25 else 'r')
        rows.append(row)
    return rows


def upright(rows):
    """Turned a quarter so the front (right) becomes the top: a standing flame."""
    h, w = len(rows), len(rows[0])
    return [''.join(rows[y][x] for y in range(h)) for x in range(w - 1, -1, -1)]


def mirror(rows):
    return [row[::-1] for row in rows]


def cool(rows, steps=1):
    """The same drawing one step cooler (white gone, edges darker): how a flame ages before it goes out."""
    for _ in range(steps):
        rows = [''.join(EMPTY if ch == EMPTY else TONES[max(0, TONES.index(ch) - 1)] for ch in row) for row in rows]
    return rows


def burst(n, rays):
    """A hit: a white heart with tapered rays, (angle, length, width) each, in an n x n drawing."""
    c = (n - 1) / 2; rows = []
    for y in range(n):
        row = ''
        for x in range(n):
            dx, dy = x - c, y - c; r = math.hypot(dx, dy); ch = EMPTY
            if r < n * .14:
                ch = 'w' if r < n * .08 else 'p'
            else:
                for a, length, w in rays:
                    u = dx * math.cos(a) + dy * math.sin(a); v = abs(-dx * math.sin(a) + dy * math.cos(a))
                    if u < 0 or u > length or v > w * (1 - u / length) + .5:
                        continue
                    f = u / length
                    k = 'w' if f < .25 else 'p' if f < .45 else 'y' if f < .65 else 'o' if f < .85 else 'r'
                    if ch == EMPTY or TONES.index(k) > TONES.index(ch):
                        ch = k
            row += ch
        rows.append(row)
    return rows


def art(rows, ramp):
    """The drawing as an RGBA image of exactly its own size."""
    h, w = len(rows), len(rows[0])
    img = Image.new('RGBA', (w, h))
    for y, row in enumerate(rows):
        assert len(row) == w, f'row {y} is {len(row)} wide, not {w}'
        for x, ch in enumerate(row):
            if ch != EMPTY:
                img.putpixel((x, y), ramp[ch] + (255,))
    return img


def canvas(rows, ramp, standing=False):
    """The drawing in a square canvas (an item model is a centred square): centred, or, for something that stands
    on the ground, on the bottom edge so the Kotlin can stand it there."""
    h, w = len(rows), len(rows[0]); n = max(w, h)
    img = Image.new('RGBA', (n, n))
    img.alpha_composite(art(rows, ramp), ((n - w) // 2, n - h if standing else (n - h) // 2))
    return img


def pixel(ramp, tone):
    """One pixel of the ramp: an ember, a spark, a smoke dot."""
    img = Image.new('RGBA', (1, 1)); img.putpixel((0, 0), ramp[tone] + (255,))
    return img


# ---------------------------------------------------------------- the reference set: the fire bolt's drawings
BALL_HEAD = ['......r..r......', '....rroorr.r....', '...royyyoorr....', '..royppppyor.r..', '.roypwwwwpyorr..', 'rroypwwwwwpyor..',
             '.oyppwwwwwppyor.', 'royypwwwwppyyorr', '.oyyppppppyyyor.', '.royyyyyyyyyork.']
BALL_TAILS = [
    ['..ooyoyyyoyoor..', '.r.royoroyooor..', '...ro.or.oroor..', '..r..or..r.or...', '.....r....r.....', '....k.....r..k..', '.......k........', '................'],
    ['.rooyyoyoyyoo...', '..roorooyorr.r..', '..oro.ro.or.r...', '...r.or..ror....', '..k...r...r.....', '.......r.....k..', '....k...........', '..........k.....'],
    ['..oroyyoyyoor.r.', '.rroyoroyoorr...', '..r.ror.oor.r...', '.r..or..r..or...', '....r...k..r....', '..k.........k...', '........r.......', '.....k..........']]
BALL = [BALL_HEAD + tail for tail in BALL_TAILS]                                  # the bolt, 16 x 18, three in turn
LICK = [upright(smear(R, [2, 1, 0, 0, 1, 0, 2, 3], 14)) for R in                 # a small flame, 8 x 14
        ([6, 9, 12, 13, 13, 11, 8, 5], [5, 10, 11, 13, 12, 12, 9, 6], [7, 8, 12, 12, 13, 10, 9, 4])]
TALL = [upright(smear(R, L, 26)) for R, L in (                                   # the standing flames, 10 x 26
    ([14, 19, 23, 25, 24, 25, 22, 18, 15, 10], [9, 4, 2, 0, 1, 0, 3, 5, 8, 11]),
    ([12, 17, 21, 24, 25, 23, 25, 20, 16, 11], [8, 5, 1, 2, 0, 1, 0, 4, 7, 10]),
    ([15, 18, 22, 25, 23, 24, 21, 19, 13, 9], [10, 6, 3, 0, 2, 0, 2, 6, 9, 12]),
    ([10, 16, 19, 22, 23, 21, 18, 14, 11, 7], [7, 4, 1, 0, 1, 0, 3, 5, 8, 10]))]
BURST = [burst(24, [(0, 11, 2), (.8, 8, 1.5), (1.6, 10, 2), (2.4, 7, 1.5), (3.1, 11, 2), (3.9, 8, 1.5), (4.7, 10, 2), (5.5, 7, 1.5)]),
         burst(40, [(.2, 19, 3), (1, 13, 2), (1.8, 18, 3), (2.6, 12, 2), (3.3, 19, 3), (4.1, 13, 2), (4.9, 17, 3), (5.7, 12, 2)])]


def samples(ramp):
    """The fire bolt's sprite set in the given ramp: name -> image. `samples(RAMPS['frost'])` is the first draft of
    a frost bolt; the sprite names and canvases match scripts/build_mage_fire.py so the Kotlin needs no change."""
    out = {f'ball_{k}': canvas(r, ramp) for k, r in enumerate(BALL)}
    out.update({f'lick_{k}': canvas(r, ramp, standing=True) for k, r in enumerate(LICK)})
    out.update({f'burst_{k}': canvas(r, ramp) for k, r in enumerate(BURST)})
    out.update({f'tall_{k}': canvas(r, ramp, standing=True) for k, r in enumerate(TALL)})
    out.update({f'ember_{k}': pixel(ramp, tone) for k, tone in enumerate(EMBER_STEPS)})
    return out


# ---------------------------------------------------------------- the style check
def check(img, ramp=None):
    """Why this sprite is not in the house style: a list of problems, empty when it is. With no ramp given, the
    sprite must sit entirely in one of RAMPS."""
    problems = []
    img = img.convert('RGBA')
    w, h = img.size
    if w > MAX_SIDE or h > MAX_SIDE:
        problems.append(f'{w}x{h} is bigger than {MAX_SIDE} px ({MAX_SIDE // PX_PER_BLOCK} blocks): draw it smaller or make it a shader piece')
    px = list(img.getdata())
    alphas = {a for *_, a in px}
    if not alphas <= {0, 255}:
        problems.append('translucent pixels: alpha must be 0 or 255 (no anti-aliasing, no fades in the drawing)')
    colours = {(r, g, b) for r, g, b, a in px if a == 255}
    if not colours:
        problems.append('empty sprite')
        return problems
    ramps = {name: r for name, r in RAMPS.items()} if ramp is None else {'given': ramp}
    fits = [name for name, r in ramps.items() if colours <= set(r.values())]
    if not fits:
        nearest = max(ramps, key=lambda name: len(colours & set(ramps[name].values())))
        stray = sorted(colours - set(ramps[nearest].values()))[:4]
        problems.append(f'colours outside every ramp (nearest {nearest}); stray: {stray}')
    if len(colours) < 2 and (w, h) != (1, 1):
        problems.append('one flat colour: a sprite needs the ramp\'s edge-to-core steps (at least two tones)')
    return problems


# ---------------------------------------------------------------- the sheet: what the game shows, roughly
def glow(img, radius, strength=.9):
    """The pack's glow post-effect, roughly: the bright tones bleed a soft halo. Only for sheets, never baked into
    a sprite (the drawing stays flat; the light is the game's)."""
    bright = Image.new('RGB', img.size, (0, 0, 0))
    src = img.convert('RGBA').load(); dst = bright.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a and (r + g + b) > 560:
                dst[x, y] = (r, g, b)
    halo = Image.eval(bright.filter(ImageFilter.GaussianBlur(radius)), lambda v: int(v * strength))
    return ImageChops.add(img.convert('RGB'), halo)


def sheet(rows, out, scale=4, with_glow=True, background=(18, 16, 25)):
    """Rows of (label, {name: image}) at an integer scale on the night background, each row its own strip."""
    from PIL import ImageDraw
    strips = []
    for label, sprites in rows:
        tiles = [im.resize((im.width * scale, im.height * scale), Image.NEAREST) for im in sprites.values()]
        w = sum(t.width + scale for t in tiles) + scale; h = max(t.height for t in tiles) + 2 * scale
        strip = Image.new('RGB', (w, h), background); x = scale
        for t in tiles:
            strip.paste(t, (x, h - t.height - scale), t); x += t.width + scale
        if with_glow:
            strip = glow(strip, radius=scale * .9)
        ImageDraw.Draw(strip).text((scale, 2), label, fill=(170, 165, 180))
        strips.append(strip)
    page = Image.new('RGB', (max(s.width for s in strips), sum(s.height + 2 for s in strips)), background); y = 0
    for s in strips:
        page.paste(s, (0, y)); y += s.height + 2
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    page.save(out)
    return page


if __name__ == '__main__':
    import sys
    args = sys.argv[1:]
    if args[:1] == ['--sheet']:
        out = args[1] if len(args) > 1 else '.tools/fx/style-kit.png'
        sheet([(name, samples(ramp)) for name, ramp in RAMPS.items()], out)
        print(out)
    elif args[:1] == ['--check']:
        bad = 0
        for path in args[1:]:
            problems = check(Image.open(path))
            bad += bool(problems)
            print(f'{path}: ' + ('ok' if not problems else '\n  ' + '\n  '.join(problems)))
        sys.exit(1 if bad else 0)
    else:
        print(__doc__)
