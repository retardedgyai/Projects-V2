"""Class icons v3: the "Samus RPG skill icons" idiom, in the centre diamond (direction mock).

    python scripts/mock_class_icons_v3.py

Reference (studied, chosen by the creator): mcmodels.net "Skill Icons - Samus RPG Classes Edition
v.2" — gestural subjects (slashes, crescents, bursts, flames) rather than literal objects, one hue
family per icon in three tones, no black outline: the upper-left edge is the light tone, the
lower-right edge the dark tone, chunky clusters, little detail. Shapes here are defined as
functions on a 14x14 grid and shaded by that edge rule, so all six icons share one hand.
Starweaver is gone (the creator removed the class).

Writes build/hud-preview/directions/class-icons-v3-8x.png and class-icon-v3-hud.png.
"""
from pathlib import Path
import math
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
from mock_class_icons_v2 import big_diamond  # noqa: E402
from mock_class_sigils import diamond  # noqa: E402  (the G2 diamond the creator chose)

N = 14
HUES = {  # highlight, light, mid, dark
    "crimson": ("#ffe0d8", "#ff6a5a", "#d8262e", "#7a0e1c"),
    "steel": ("#ffffff", "#e4ecf8", "#9cacc8", "#56648a"),
    "green": ("#f0ffd8", "#9cf06a", "#4cb83a", "#1e6a2a"),
    "fire": ("#fff6c0", "#ffc83a", "#ff6a1c", "#a8240e"),
    "ice": ("#ffffff", "#bfe8ff", "#5aaef0", "#2a5ab0"),
    "violet": ("#f4e0ff", "#c890ff", "#8a4ce0", "#3e1a8a"),
    "gold": ("#fffbe0", "#ffe070", "#e0a424", "#8a5a0a"),
    "life": ("#ffffff", "#d8ffc8", "#7ae07a", "#2a8a4a"),
}


def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = max(0, min(1, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy or 1)))
    return math.hypot(px - (ax + t * vx), py - (ay + t * vy))


def crescent(cx, cy, r, ox, oy, r2):
    return lambda x, y: math.hypot(x - cx, y - cy) <= r and math.hypot(x - cx - ox, y - cy - oy) > r2


def stroke(points, w):
    def f(x, y):
        return any(seg_dist(x, y, *points[i], *points[i + 1]) <= w for i in range(len(points) - 1))
    return f


def taper(points, w):
    """A slash: thickest in the middle, tapering to points at both ends."""
    (ax, ay), (bx, by) = points
    def f(x, y):
        vx, vy = bx - ax, by - ay
        t = max(0, min(1, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)))
        return math.hypot(x - (ax + t * vx), y - (ay + t * vy)) <= w * math.sin(math.pi * t) + .15
    return f


def union(*fs):
    return lambda x, y: any(f(x, y) for f in fs)


def minus(a, b):
    return lambda x, y: a(x, y) and not b(x, y)


def layer(shape, hue, highlights=()):
    """Shade one shape by the reference's edge rule."""
    hi, light, mid, dark = (hud.rgba(c) for c in HUES[hue])
    cells = {(x, y) for y in range(N) for x in range(N) if shape(x + .5, y + .5)}
    out = {}
    for (x, y) in cells:
        tl = (x - 1, y) not in cells or (x, y - 1) not in cells
        br = (x + 1, y) not in cells or (x, y + 1) not in cells
        out[(x, y)] = light if tl and not br else dark if br and not tl else mid if not tl else light
        if br and tl:
            out[(x, y)] = mid
    for p in highlights:
        if p in cells:
            out[p] = hi
    return out


def render(*layers):
    img = Image.new("RGBA", (N, N))
    for lay in layers:
        for (x, y), col in lay.items():
            img.putpixel((x, y), col)
    return img


def warrior():  # a crimson crescent sweep cut by a steel blade glint
    sweep = crescent(7.5, 7.5, 6.6, 2.4, -1.6, 5.6)
    glint = stroke([(3, 12), (12.5, 2)], .7)
    return render(layer(sweep, "crimson", [(2, 6), (2, 7), (3, 4)]), layer(glint, "steel", [(11, 3), (10, 4)]))


def ranger():  # a green arrow streak with two wind slivers
    shaft = stroke([(2, 12), (10.5, 3.5)], .75)
    head = lambda x, y: (x - 12) + (3 - y) <= 0 and seg_dist(x, y, 9, 2, 12.6, 1.4) <= 1.6 or \
        seg_dist(x, y, 12.5, 1.5, 12.5, 5.2) <= .9 or seg_dist(x, y, 12.5, 1.5, 8.8, 1.5) <= .9
    wind1 = crescent(5, 5.5, 4.2, 1.6, 1.6, 3.6)
    wind2 = crescent(9, 10, 3.4, 1.4, 1.4, 2.9)
    return render(layer(union(wind1, wind2), "green"), layer(union(shaft, head), "steel", [(12, 1), (11, 2)]),
                  layer(stroke([(1.5, 11), (3, 12.5)], .8), "green", [(2, 11)]))


def mage():  # a burning orb, frost licking its lower edge
    orb = lambda x, y: math.hypot(x - 7, y - 8) <= 4.6
    flame = union(stroke([(5, 5), (4, 1.5)], 1.1), stroke([(8, 4.5), (8.6, .6)], 1.2), stroke([(10.4, 6), (12, 2.6)], 1))
    frost = crescent(7, 8.5, 5.6, 0, -1.6, 5.2)
    return render(layer(union(orb, flame), "fire", [(5, 6), (6, 6), (5, 7), (8, 1)]),
                  layer(minus(frost, orb), "ice", [(3, 11)]))


def assassin():  # two violet slashes crossing, a steel glint where they meet
    s1 = taper([(1.5, 2), (12.5, 12)], 1.9)
    s2 = taper([(12.5, 2.5), (2, 12)], 1.6)
    return render(layer(s1, "violet", [(3, 3), (4, 4)]), layer(s2, "violet", [(11, 3), (10, 4)]),
                  layer(lambda x, y: math.hypot(x - 7, y - 7.2) <= 1.1, "steel", [(7, 7)]))


def templar():  # a golden heater shield with a steel face and a gold cross
    outer = lambda x, y: 2.5 <= x <= 11.5 and 1.5 <= y <= 13 and (y < 7 or abs(x - 7) <= (13 - y) * .75)
    inner = lambda x, y: 4 <= x <= 10 and 3 <= y <= 11.4 and (y < 7 or abs(x - 7) <= (11.4 - y) * .75 - .2)
    cross = union(stroke([(7, 3.5), (7, 10)], .6), stroke([(4.6, 6), (9.4, 6)], .6))
    return render(layer(minus(outer, inner), "gold", [(3, 2), (4, 2), (3, 3)]), layer(inner, "steel", [(4, 3), (5, 3)]),
                  layer(cross, "gold", [(7, 4)]))


def healer():  # a life-green cross blooming, with sparks
    cross = union(stroke([(7, 2.5), (7, 11.5)], 1.3), stroke([(2.5, 7), (11.5, 7)], 1.3))
    bloom = minus(lambda x, y: math.hypot(x - 7, y - 7) <= 5.2, lambda x, y: math.hypot(x - 7, y - 7) <= 4.3)
    sparks = lambda x, y: any(math.hypot(x - a, y - b) <= .6 for a, b in ((2, 2), (12, 2.5), (11.5, 12), (2.5, 11.5)))
    return render(layer(bloom, "life"), layer(sparks, "life", [(2, 2), (12, 2), (11, 12)]),
                  layer(cross, "life", [(6, 3), (6, 4), (3, 6), (4, 6)]))


ICONS = [("warrior", warrior, "#e3283e"), ("ranger", ranger, "#5ec64a"), ("mage", mage, "#ff8a1c"),
         ("assassin", assassin, "#8a4cff"), ("templar", templar, "#f4c84e"), ("healer", healer, "#9cf07c")]


def main():
    sheet = Image.new("RGBA", (6 * 34 + 4, 34), hud.D["plate"])
    for k, (name, fn, accent) in enumerate(ICONS):
        cx = 19 + k * 34
        diamond(sheet, cx, 17, accent)
        sheet.alpha_composite(fn(), (cx - 7, 17 - 7))
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(hud.OUT / "class-icons-v3-8x.png")
    base, icons, top, jp = hud.design_f1(center=-1)
    cy = hud.HB_Y - 5 - 13 + 4
    diamond(base, hud.C, cy, ICONS[0][2])
    base.alpha_composite(warrior(), (hud.C - 7, cy - 7))
    hud.mc_text(top, hud.C - hud.mc_width("12") // 2 + 1, cy + 9, "12", hud.rgba("80ff20"), outline=True)
    hud.compose((base, icons, top, jp), 4).crop((540, 720, 1380, 1080)).save(hud.OUT / "class-icon-v3-hud.png")
    print(f"wrote {hud.OUT / 'class-icons-v3-8x.png'}")


if __name__ == "__main__":
    main()
