"""The house style for skill and boss effect sprites, as code (2026-10-28, Creator: 「作風を固定する型から小さく作り始めて」).

Why this exists: the mage fire bolt took 21 sample versions, and the first seven each looked like a different
author's work because each one swapped the whole way of drawing (three.js boxes, a lava cube, smoke balls, a
Gorebound copy, a boss claw ...). Meanwhile the approved look already existed in two places that agree with each
other: the sakura / shade shader pieces and the fire bolt's version 21 (the warrior's hit flame in
scripts/warrior_vfx_art.py is NOT approved: 2026-10-30 藍「よくない絵だな、OK していない」, design/ng.md). This
module is that look, so a first draft comes out in it without anyone re-deciding it:

  - 16 px to a block, and a sprite is shown at exactly that size. A bigger state is a bigger drawing, never a
    stretched one.
  - Flat tones, opaque pixels, no anti-aliasing, no gradients, no noise: alpha is 0 or 255, every colour is one of
    the ramp's six steps.
  - One ramp per element, dark edge to white-hot core, written as the letters `k r o y p w` (edge -> core) in the
    drawings. The same drawing in another ramp is the first draft of that element's effect: swap the ramp first,
    redraw only when the Creator wants a different shape.
  - Things appear whole and leave by breaking apart or sinking, never by fading or shrinking (the timing lives in
    the Kotlin, but the drawings come in frames that do that).

The drawings of the fire bolt (sample version 21, 藍「この燃え方で火炎弾を決める」) are the reference set, so a new
element's first look is `samples(RAMPS['frost'])`. They live in ONE place, scripts/build_mage_fire.py (PR #189), and
are imported from there, so a fix to the bolt's tail reaches every element's first draft; until #189 is merged the
helpers `smear` / `upright` / `burst` and `samples()` are unavailable here (RAMPS, check, cool, art, canvas work).

    python3 scripts/fx_art.py --sheet .tools/fx/style-kit.png    # the reference set in every ramp, with the glow
    python3 scripts/fx_art.py --check path/to/sprite.png ...     # is this sprite in the style?
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

PX_PER_BLOCK = 16
MAX_SIDE = 64          # four blocks: anything bigger is a shader job, not a sprite
TONES = 'kroypw'       # edge -> core: dark, red, orange, yellow, pale, white
EMPTY = '.'

# One ramp per element, dark edge to white-hot core. fire = the fire bolt's own palette; iron = the warrior's
# hit flame (warrior_vfx_art.py: outline, deep red, red, orange, cream, white core -- NOT approved by the Creator,
# so iron is unconfirmed until a warrior effect is); sakura and shade = the item shader's
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
# smear (a smeared flame pointing right), upright (turned so the front is the top) and burst (a white heart with
# tapered rays) are the fire bolt's own, in scripts/build_mage_fire.py (PR #189): one copy, imported, never repeated.
try:
    from build_mage_fire import smear, upright, burst
    HAS_FIRE = True
except ImportError:
    smear = upright = burst = None
    HAS_FIRE = False

NEEDS_FIRE = 'scripts/build_mage_fire.py (PR #189) is not here: the reference drawings and smear/upright/burst come from it'


def mirror(rows):
    return [row[::-1] for row in rows]


def cool(rows, steps=1):
    """The same drawing one step cooler (white gone, edges darker): how a flame ages before it goes out."""
    for _ in range(steps):
        rows = [''.join(EMPTY if ch == EMPTY else TONES[max(0, TONES.index(ch) - 1)] for ch in row) for row in rows]
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
def reference():
    """The fire bolt's drawings as text rows, name -> rows, read from scripts/build_mage_fire.py (the one copy):
    ball_0..2 (16 x 18), lick_0..2 (8 x 14, standing), burst_0/1 (24, 40), tall_0..3 (10 x 26, standing)."""
    if not HAS_FIRE:
        raise ImportError(NEEDS_FIRE)
    import build_mage_fire as fire
    out = {f'ball_{k}': fire.BALL_HEAD + tail for k, tail in enumerate(fire.BALL_TAIL)}
    out.update({f'lick_{k}': rows for k, rows in enumerate(fire.LICK)})
    out.update({f'burst_{k}': rows for k, rows in enumerate(fire.BURST)})
    out.update({f'tall_{k}': rows for k, rows in enumerate(fire.TALL)})
    return out


def samples(ramp):
    """The fire bolt's sprite set in the given ramp: name -> image. `samples(RAMPS['frost'])` is the first draft of
    a frost bolt; the sprite names and canvases match build_mage_fire.sprites() so the Kotlin needs no change. The
    one difference: the embers here are the ramp's own `w y o r k`, where the fire bolt uses five hand-picked colours."""
    out = {}
    for name, rows in reference().items():
        out[name] = canvas(rows, ramp, standing=name.startswith(('lick', 'tall')))
    out.update({f'ember_{k}': pixel(ramp, tone) for k, tone in enumerate(EMBER_STEPS)})
    return out


# ---------------------------------------------------------------- the style check
def check(img, ramp=None):
    """Why this sprite is not in the house style: a list of problems, empty when it is. With no ramp given, the
    sprite must sit entirely in one of RAMPS.

    What it sees: the size (<= MAX_SIDE), alpha 0 / 255 only, every colour in one ramp, more than one flat colour.
    What it does NOT see (the Creator's eye does): 16 px to a block and no stretching (that is how the sprite is
    shown, not what is in it), the edge -> core order of the tones, a ramp laid out as a stripe gradient."""
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
        if not args[1:]:                       # an empty glob must not look like "all ok"
            print('usage: fx_art.py --check path/to/sprite.png ...'); sys.exit(2)
        bad = 0
        for path in args[1:]:
            problems = check(Image.open(path))
            bad += bool(problems)
            print(f'{path}: ' + ('ok' if not problems else '\n  ' + '\n  '.join(problems)))
        sys.exit(1 if bad else 0)
    else:
        print(__doc__)
