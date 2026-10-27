"""霜の波紋 first draft from the style kit (2026-10-28, Creator: 「霜の波紋の1版目をこの型の色替えから出して」).

Not a new way of drawing: the fire bolt's sprite set in the frost ramp (fx_art.samples), plus three small frost-only
drawings made under the same rules (a ring dash, a snowflake, an ice shard), laid out along the nova's own timing
(sample version 12, 藍「氷は一旦これでOK」): the staff charges, strikes, a thin ring races out at knee height and
breaks into dashes, three rings of ice stand up one after another, hold, and break into shards and flakes.
Every sprite is 16 px to a block and drawn at that size; the storyboard is a side view at an integer scale.

    python3 scripts/build_fx_frost_draft.py            # assets/combat-vfx/frost-nova-draft-v1.png
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

from fx_art import RAMPS, art, canvas, check, cool, glow, pixel, samples, upright, smear

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'assets/combat-vfx/frost-nova-draft-v1.png'
FROST = RAMPS['frost']


def rnd(seed):
    s = (seed * 2654435761 + 12345) & 0xffffffff
    def r():
        nonlocal s
        s = (s * 1664525 + 1013904223) & 0xffffffff
        return s / 0x100000000
    return r


# ---------------------------------------------------------------- the frost-only drawings (same rules: six tones, flat, 16 px to a block)
DASH = ['rooyyoor']                                            # a piece of the racing ring, 8 x 1, brighter in the middle
FLAKE = ['w...w', '.wpw.', 'wpypw', '.wpw.', 'w...w']           # a 5 x 5 snowflake, pale heart
SHARD = [['.w.', 'wpw', 'wyw', 'ryr', '.k.'], ['w.', 'pw', 'yr', 'k.']]   # bits of ice thrown out when a column breaks


def ice_column(width, height, seed, lean=0):
    """An ice column the Goosezilla way, in the ramp's letters: a stepped tapering outline with a 1 px white rim, a
    pale band inside it, a yellow-step body (the ramp's 4th tone: lavender-blue in frost) and two darker blots.
    Drawn narrow and tall; the top is a stepped point, the base sits on the bottom row."""
    R = rnd(seed)
    rows = []
    blots = [(int(R() * width), int(height * (.25 + R() * .2))), (int(R() * width), int(height * (.55 + R() * .25)))]
    for y in range(height):
        # the half-width at this row: full at the base, stepping in toward the point
        t = y / (height - 1)
        half = max(1, round(width / 2 * (.35 + .65 * t ** .8)))
        cx = width / 2 + lean * (1 - t)
        row = ''
        for x in range(width):
            d = abs(x + .5 - cx)
            if d > half:
                row += '.'; continue
            edge = half - d
            top_edge = y < 2 or (y > 0 and abs(x + .5 - cx) > max(1, round(width / 2 * (.35 + .65 * ((y - 1) / (height - 1)) ** .8))))
            if edge < 1 or top_edge:
                ch = 'w'
            elif edge < 2:
                ch = 'p'
            else:
                ch = 'y'
                for bx, by in blots:
                    dd = math.hypot(x - bx, (y - by) * .6)
                    if dd < 1.3:
                        ch = 'r'
                    elif dd < 2.1 and ch == 'y':
                        ch = 'o'
            row += ch
        # a leaning column can run against the canvas edge: that edge is still its rim
        row = ''.join('w' if ch != '.' and (x == 0 or x == width - 1) else ch for x, ch in enumerate(row))
        rows.append(row)
    return rows


COLUMN = [ice_column(8, 22, 11), ice_column(7, 18, 12, lean=.6), ice_column(9, 24, 13, lean=-.5)]


def frost_sprites():
    out = samples(FROST)
    out['dash'] = art(DASH, FROST)
    out['flake'] = art(FLAKE, FROST)
    out.update({f'shard_{k}': art(r, FROST) for k, r in enumerate(SHARD)})
    out.update({f'column_{k}': canvas(r, FROST, standing=True) for k, r in enumerate(COLUMN)})
    out.update({f'column_cool_{k}': canvas(cool(r), FROST, standing=True) for k, r in enumerate(COLUMN)})
    return out


# ---------------------------------------------------------------- the storyboard
S = 3                          # px per sprite pixel
BLOCK = 16 * S                 # px per block
W, H = 11 * BLOCK, 5 * BLOCK   # a frame: eleven blocks wide, the mage at the centre, ground a block from the bottom
GROUND = H - BLOCK
RINGS = (2.3, 3.4, 4.5)        # the three rings of columns, blocks from the mage (CoreMageFrost.RINGS)
NIGHT = (18, 16, 25)


def world(x, z, y=0.0):
    """Blocks (x right, z toward the camera, y up) -> frame pixels: a side view with a little top-down tilt."""
    return int(W / 2 + x * BLOCK), int(GROUND + z * BLOCK * .28 - y * BLOCK)


def put(frame, sprite, x, z, y=0.0, standing=False):
    big = sprite.resize((sprite.width * S, sprite.height * S), Image.NEAREST)
    px, py = world(x, z, y)
    frame.alpha_composite(big, (px - big.width // 2, py - big.height if standing else py - big.height // 2))


def figure(frame, x, z, pale=False):
    """A stand-in enemy, two blocks tall, so the sizes read."""
    d = ImageDraw.Draw(frame)
    px, py = world(x, z)
    body = (150, 160, 190) if pale else (70, 64, 80)
    d.rectangle((px - 4 * S, py - 30 * S, px + 4 * S, py - 12 * S), fill=body)
    d.rectangle((px - 4 * S, py - 12 * S, px - 1 * S, py), fill=body)
    d.rectangle((px + 1 * S, py - 12 * S, px + 4 * S, py), fill=body)
    d.rectangle((px - 4 * S, py - 38 * S, px + 4 * S, py - 30 * S), fill=(120, 110, 100) if not pale else (190, 200, 230))


def mage(frame):
    d = ImageDraw.Draw(frame)
    px, py = world(0, 0)
    d.rectangle((px - 3 * S, py - 28 * S, px + 3 * S, py), fill=(60, 50, 90))
    d.line((px + 3 * S, py - 20 * S, px + 9 * S, py - 34 * S), fill=(120, 90, 60), width=S)


def frame_at(a, sp):
    """The nova at tick a after the strike (a < 0: charging)."""
    f = Image.new('RGBA', (W, H), NIGHT + (255,))
    ImageDraw.Draw(f).rectangle((0, GROUND, W, H), fill=(28, 30, 40, 255))
    figure(f, 3.6, .4, pale=a >= 3)
    figure(f, -2.6, -.6, pale=a >= 2)
    mage(f)
    staff = (0.55, 0.0, 2.1)
    if a < 0:                                                                     # charge: a small frost at the staff's gem
        put(f, sp[f'lick_{(-a) % 3}'], *staff, standing=True)
        return f
    if a < 2:                                                                     # the strike: one small burst, then the big one
        put(f, sp[f'burst_{a}'], staff[0], staff[1], 1.4)
    if a < 6:                                                                     # the ring: dashes racing out at knee height, thinning from tick 3
        r = 1 + 4.6 * (1 - .55 ** (a + 1))
        n = 28
        for i in range(n):
            if a >= 3 and i % 2 or a >= 5 and i % 4:
                continue
            an = i / n * math.tau
            put(f, sp['dash'], math.cos(an) * r, math.sin(an) * r, .35 + a * .05)
    for k, rr in enumerate(RINGS):                                                # the columns: ring k from tick k, standing to 22 + k, cooling just before the break
        life = a - k
        if life < 0 or life >= 24 + k:
            continue
        n = (5, 7, 9)[k]
        for c in range(n):
            R = rnd(30 + k * 40 + c)
            an = (c + (k % 2) * .5) / n * math.tau + (R() - .5) * .3
            x, z = math.cos(an) * (rr + (R() - .5) * .4), math.sin(an) * (rr + (R() - .5) * .4)
            if z < -1.2 and k == 2 and c % 2:
                continue                                                          # the far side thins so the near side reads
            name = f'column_{(c + life) % 3}' if life < 20 + k else f'column_cool_{(c + life) % 3}'
            img = sp[name]
            if life < 4:                                                          # grows a step a tick: the bottom of the drawing, not a stretch
                keep = img.height * (life + 1) // 5
                img = img.crop((0, img.height - keep, img.width, img.height))
            put(f, img, x, z, standing=True)
    if a >= 22:                                                                   # the break: shards and flakes thrown out where the columns stood
        for j in range(60):
            R = rnd(400 + j)
            k = j % 3
            l = a - 22 - k
            if l < 0 or l >= 10:
                continue
            an = R() * math.tau
            d = RINGS[k] + l * (.07 + R() * .06)
            y = .4 + R() * 1.0 + l * .12 - l * l * .025
            if y < 0:
                continue
            sp_name = 'flake' if j % 3 else f'shard_{j % 2}'
            put(f, sp[sp_name], math.cos(an) * d, math.sin(an) * d, y)
    if a < 6:                                                                     # embers of the strike: single pixels, cooling
        for j in range(14):
            R = rnd(700 + j)
            l = a - int(R() * 2)
            if l < 0 or l > 5:
                continue
            an = R() * math.tau
            put(f, sp[f'ember_{min(4, l)}'], staff[0] + math.cos(an) * (.2 + l * .15), staff[1], 1.4 + math.sin(an) * (.2 + l * .12) + l * .05)
    return f


TICKS = ((-4, '溜め'), (0, '打つ'), (1, '輪が走る'), (3, '氷が立つ'), (8, '立つ'), (14, '立つ'), (21, '冷える'), (23, '割れる'), (27, '散る'))


def label_font():
    """A font that can draw the Japanese labels, if the machine has one; the sheet still builds without it."""
    from PIL import ImageFont
    for path in ('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc',
                 '/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf', '/usr/share/fonts/truetype/fonts-japanese-gothic.ttf'):
        if Path(path).exists():
            return ImageFont.truetype(path, 13)
    return None


def storyboard(out=OUT):
    sp = frost_sprites()
    for name, img in sp.items():
        assert check(img, FROST) == [], (name, check(img, FROST))
    cols = 3
    rows = math.ceil(len(TICKS) / cols)
    page = Image.new('RGB', (cols * (W + 6) + 6, rows * (H + 24) + 6), NIGHT)
    d = ImageDraw.Draw(page)
    for i, (a, label) in enumerate(TICKS):
        f = glow(frame_at(a, sp), radius=S * .8, strength=.4)
        x, y = 6 + (i % cols) * (W + 6), 6 + (i // cols) * (H + 24)
        page.paste(f, (x, y + 18))
        d.text((x + 2, y + 2), f'{label}  tick {a:+d}', fill=(170, 165, 180), font=label_font())
    out.parent.mkdir(parents=True, exist_ok=True)
    page.save(out)
    return page


if __name__ == '__main__':
    storyboard()
    print(OUT)
