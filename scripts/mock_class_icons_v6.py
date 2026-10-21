"""Class icons v6: the v5 objects at item density (32 texels in 16 GUI px) with rich shading.

    python scripts/mock_class_icons_v6.py

Creator feedback on v5: readable but too poor. The skill icons are 32 px textures shown in 16 GUI
px slots, so the centre emblem may use the same density. Each object is a set of shape masks;
every mask is shaded by its depth from the silhouette edge against a light from the upper-left
(five-tone ramp, a lit rim, a dark lower-right edge, a specular glint), over a dithered aura in
the class colour, with a few sparks. Drawn on a 40x40 texel canvas = 20 GUI px.

Writes build/hud-preview/directions/class-icons-v6-8x.png (4 texels per screen... at 4x GUI) and
class-icon-v6-hud.png.
"""
from pathlib import Path
import math
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
from mock_class_icons_v4 import ACCENT, BACK, BACK_SHADOW  # noqa: E402

S = 40  # texels; shown in 20 GUI px
RAMP = {  # darkest .. lightest, then specular
    "steel": ["#2c3446", "#4c5a74", "#7c8aa8", "#b4c0d8", "#e4ecf8", "#ffffff"],
    "crimson": ["#4a0812", "#82101e", "#c41e2a", "#ee3a3a", "#ff7a6a", "#ffd8cc"],
    "gold": ["#4a2c06", "#8a560e", "#c88a1c", "#f0b830", "#ffe070", "#fffbe0"],
    "leather": ["#2a140a", "#4a2614", "#6e3c20", "#94562e", "#b8783e", "#e0a868"],
    "wood": ["#2a1a0c", "#4e3216", "#7a5224", "#a8783a", "#cc9e58", "#f0d090"],
    "green": ["#0e3014", "#1e5a24", "#2e8a32", "#52c040", "#9cf06a", "#eaffc8"],
    "fire": ["#5a1006", "#a8240e", "#e04a14", "#ff8a1c", "#ffc83a", "#fff6c0"],
    "ice": ["#10244a", "#1e4a8a", "#2e7ad0", "#5aaef0", "#a8dcff", "#ffffff"],
    "violet": ["#1e0c40", "#3a1a78", "#6030c0", "#8a50f0", "#c090ff", "#f4e0ff"],
    "life": ["#0c3a20", "#18643a", "#2e9a50", "#5ad870", "#b0ffb0", "#ffffff"],
    "bone": ["#3a3428", "#6a6050", "#9a8e78", "#c8bca0", "#ece2c8", "#ffffff"],
}


def C(h, a=255):
    return hud.rgba(h, a)


def poly(points):
    def inside(x, y):
        n, c = len(points), False
        for i in range(n):
            (x1, y1), (x2, y2) = points[i], points[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        return c
    return inside


def disc(cx, cy, r):
    return lambda x, y: math.hypot(x - cx, y - cy) <= r


def ring(cx, cy, r0, r1):
    return lambda x, y: r0 <= math.hypot(x - cx, y - cy) <= r1


def seg(ax, ay, bx, by, w):
    def f(x, y):
        vx, vy = bx - ax, by - ay
        t = max(0, min(1, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)))
        return math.hypot(x - ax - t * vx, y - ay - t * vy) <= w
    return f


def any_of(*fs):
    return lambda x, y: any(f(x, y) for f in fs)


def but(a, b):
    return lambda x, y: a(x, y) and not b(x, y)


class Canvas:
    def __init__(self, aura):
        self.img = Image.new("RGBA", (S, S))
        self.aura = aura

    def glow(self, cx, cy, r):
        """Dithered aura in the class colour, brighter toward the centre."""
        col = RAMP[self.aura]
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - cx, y - cy) / r
                if d < 1 and (x + y) % 2 == 0:
                    self.img.putpixel((x, y), C(col[2], 70 if d > .6 else 110))

    def shade(self, shape, ramp, flat=False):
        cells = {(x, y) for y in range(S) for x in range(S) if shape(x + .5, y + .5)}
        depth, frontier, k = {}, set(cells), 0
        while frontier:  # depth from the silhouette edge, by repeated erosion
            k += 1
            edge = {(x, y) for (x, y) in frontier if any((x + a, y + b) not in frontier
                                                         for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
            for p in edge:
                depth[p] = k
            frontier -= edge
        col = RAMP[ramp]
        for (x, y) in cells:
            d = depth[(x, y)]
            # light from the upper-left: compare depth toward the light vs away from it
            toward = depth.get((x - 1, y - 1), 0)
            away = depth.get((x + 1, y + 1), 0)
            slope = toward - away
            tone = 3 + (1 if slope < 0 else -1 if slope > 0 else 0) + (0 if flat else min(1, d // 3))
            if (x + 1, y + 1) not in cells:
                tone = 1  # dark lower-right edge
            if (x - 1, y - 1) not in cells and (x + 1, y + 1) in cells:
                tone = 4  # lit upper-left rim
            self.img.putpixel((x, y), C(col[max(0, min(4, tone))]))
        return cells

    def dot(self, x, y, ramp, tone=5):
        self.img.putpixel((x, y), C(RAMP[ramp][tone]))

    def sparks(self, pts, ramp):
        for x, y in pts:
            self.dot(x, y, ramp, 5)
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if not self.img.getpixel((x + a, y + b))[3]:
                    self.img.putpixel((x + a, y + b), C(RAMP[ramp][3], 200))


def warrior():
    c = Canvas("crimson")
    c.glow(20, 20, 12)
    c.shade(poly([(17, 13), (23, 13), (22.5, 32), (20, 37), (17.5, 32)]), "steel")       # blade
    c.shade(seg(20, 15, 20, 31, .5), "crimson", flat=True)                                 # fuller
    c.shade(any_of(poly([(9, 10), (31, 10), (29, 13.5), (11, 13.5)]), disc(9, 10, 2.2), disc(31, 10, 2.2)), "gold")
    c.shade(poly([(18, 3), (22, 3), (22, 10), (18, 10)]), "leather")                       # grip
    for y in (4, 6, 8):
        c.shade(seg(18.2, y, 21.8, y + .6, .45), "gold", flat=True)
    c.shade(disc(20, 2.2, 2.4), "crimson")                                                 # pommel gem
    c.dot(19, 1, "crimson"); c.dot(18, 11, "gold"); c.dot(18, 15, "steel")
    c.sparks([(7, 26), (33, 22), (11, 33)], "crimson")
    return c.img


def ranger():
    c = Canvas("green")
    c.glow(20, 20, 12)
    bow = lambda x, y: 13.5 <= math.hypot((x - 27) * .78, y - 20) <= 16.2 and x < 24
    c.shade(bow, "wood")
    for y in (6, 34):
        c.shade(disc(23.5, y, 1.8), "gold")
    c.shade(seg(23, 5.5, 23, 34.5, .45), "bone", flat=True)                                # string
    c.shade(seg(6, 20, 33, 20, .9), "wood", flat=True)                                     # shaft
    c.shade(poly([(32, 16.5), (38, 20), (32, 23.5), (33.5, 20)]), "steel")                 # head
    c.shade(any_of(poly([(4, 16), (9, 18.5), (9, 20), (3, 18)]), poly([(4, 24), (9, 21.5), (9, 20), (3, 22)])), "green")
    c.sparks([(30, 9), (31, 31), (9, 9)], "green")
    return c.img


def mage():
    c = Canvas("fire")
    c.glow(20, 13, 10)
    c.shade(poly([(18.5, 18), (21.5, 18), (21, 38), (19, 38)]), "ice")                    # staff
    c.shade(any_of(poly([(13, 17), (16, 13), (18, 19), (15, 21)]), poly([(27, 17), (24, 13), (22, 19), (25, 21)])), "ice")
    c.shade(disc(20, 12, 7.2), "fire")                                                     # orb
    c.shade(any_of(poly([(15, 8), (13, 1), (18, 6)]), poly([(19, 5), (21, 0), (23, 6)]), poly([(24, 7), (28, 2), (26, 9)])), "fire")
    c.shade(ring(20, 12, 3.2, 4.2), "fire", flat=True)
    c.dot(17, 9, "fire"); c.dot(18, 9, "fire"); c.dot(17, 10, "fire")
    c.shade(poly([(17, 27), (20, 24), (23, 27), (20, 30)]), "ice")                         # frost knot
    c.sparks([(8, 14), (32, 18), (12, 30), (29, 32)], "ice")
    return c.img


def assassin():
    c = Canvas("violet")
    c.glow(20, 20, 12)
    blade = lambda x, y: poly([(18, 9), (20, 2), (23.5, 9), (22.5, 24), (17.5, 24)])(x, y)
    c.shade(blade, "steel")
    c.shade(seg(20.5, 6, 20.5, 22, .45), "violet", flat=True)                              # poison sheen
    c.shade(any_of(poly([(12, 24), (28, 24), (26, 27), (14, 27)]), disc(12, 25.5, 1.8), disc(28, 25.5, 1.8)), "steel")
    c.shade(poly([(18.5, 27), (21.5, 27), (21.5, 34), (18.5, 34)]), "leather")
    c.shade(disc(20, 36, 2.3), "violet")
    for (x, y, r) in ((9, 13, 3.2), (6, 19, 2.6), (31, 15, 3), (34, 21, 2.2), (8, 28, 2), (32, 29, 2.4)):
        c.shade(disc(x, y, r), "violet")                                                   # smoke
    c.sparks([(14, 6), (27, 5)], "violet")
    return c.img


def templar():
    c = Canvas("gold")
    for k in range(8):  # light rays behind the shield
        a = math.radians(k * 45 + 22.5)
        c.shade(seg(20, 19, 20 + 14 * math.cos(a), 19 + 14 * math.sin(a), .5), "gold", flat=True)
    outer = poly([(9, 7), (31, 7), (31, 20), (20, 35), (9, 20)])
    inner = poly([(11.5, 9.5), (28.5, 9.5), (28.5, 19.5), (20, 31.5), (11.5, 19.5)])
    c.shade(outer, "gold")
    c.shade(inner, "steel")
    c.shade(any_of(poly([(18.5, 10), (21.5, 10), (21.5, 29), (18.5, 29)]), poly([(12, 15), (28, 15), (28, 18), (12, 18)])), "gold")
    for x, y in ((10.5, 8.5), (29.5, 8.5), (10.5, 19), (29.5, 19)):
        c.shade(disc(x, y, 1.2), "steel")
    c.shade(disc(20, 16.5, 2.4), "crimson")
    return c.img


def healer():
    c = Canvas("life")
    c.glow(20, 20, 12)
    c.shade(ring(20, 20, 11.5, 13.5), "gold", flat=True)                                  # halo
    cross = any_of(poly([(16.5, 3), (23.5, 3), (23.5, 37), (16.5, 37)]), poly([(3, 16.5), (37, 16.5), (37, 23.5), (3, 23.5)]))
    c.shade(cross, "life")
    c.shade(any_of(poly([(20, 25), (26, 30), (21, 33)]), poly([(20, 25), (14, 30), (19, 33)])), "green")  # leaves
    c.shade(disc(20, 20, 2.6), "gold")
    c.sparks([(8, 7), (32, 8), (7, 32), (33, 31)], "life")
    return c.img


ORDER = [("warrior", warrior), ("ranger", ranger), ("mage", mage), ("assassin", assassin),
         ("templar", templar), ("healer", healer)]


def diamond_hi(scale, accent, r=13):
    """The G2 diamond at 2 texels per GUI px (same shape, finer)."""
    D = hud.D
    n = (2 * r + 3)
    img = Image.new("RGBA", (n, n))
    cx = cy = r + 1
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j)
        hud.over(img, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["out"])
    for j in range(-r, r + 1):
        w = r - abs(j)
        hud.over(img, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["metal"][1] if j < 0 else D["metal"][2])
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        hud.over(img, (cx - w, cy + j, cx + w + 1, cy + j + 1), hud.rgba(accent))
    for j in range(-r + 3, r - 2):
        w = r - 3 - abs(j)
        hud.over(img, (cx - w, cy + j, cx + w + 1, cy + j + 1), BACK)
    return img


def clip_to_diamond(icon, r_inner_gui=10):
    """Keep only texels inside the diamond's inner backing (|dx|+|dy| <= r in GUI px)."""
    out = icon.copy()
    half = S / 2
    for y in range(S):
        for x in range(S):
            if abs(x + .5 - half) + abs(y + .5 - half) > r_inner_gui * 2 - .5:
                out.putpixel((x, y), (0, 0, 0, 0))
    return out


def shadowed(icon):
    out = Image.new("RGBA", (S + 2, S + 2))
    for y in range(S):
        for x in range(S):
            if icon.getpixel((x, y))[3] > 200:
                out.putpixel((x + 2, y + 2), BACK_SHADOW)
    out.alpha_composite(icon, (0, 0))
    return out


def place(screen, accent, icon, cx_gui, cy_gui, s):
    """Lay the diamond (GUI px) and the 2x-dense icon onto a canvas already scaled by s."""
    dm = diamond_hi(1, accent).resize((27 * s, 27 * s), Image.NEAREST)
    screen.alpha_composite(dm, ((cx_gui - 13) * s, (cy_gui - 13) * s))
    ic = shadowed(clip_to_diamond(icon)).resize(((S + 2) * s // 2, (S + 2) * s // 2), Image.NEAREST)
    screen.alpha_composite(ic, (cx_gui * s - S * s // 4, cy_gui * s - S * s // 4))


def main():
    s = 8
    sheet = Image.new("RGBA", ((6 * 34 + 4) * s, 34 * s), hud.D["plate"])
    for k, (name, fn) in enumerate(ORDER):
        place(sheet, ACCENT[name], fn(), 19 + k * 34, 17, s)
    sheet.save(hud.OUT / "class-icons-v6-8x.png")
    base, icons, top, jp = hud.design_f1(center=-1)
    cy = hud.HB_Y - 5 - 13 + 4
    screen = hud.compose((base, icons, Image.new("RGBA", (hud.W, hud.H)), jp), 4)
    place(screen, ACCENT["warrior"], warrior(), hud.C, cy, 4)
    screen.alpha_composite(top.resize((hud.W * 4, hud.H * 4), Image.NEAREST))
    lv = Image.new("RGBA", (hud.W, hud.H))
    hud.mc_text(lv, hud.C - hud.mc_width("12") // 2 + 1, cy + 10, "12", hud.rgba("80ff20"), outline=True)
    screen.alpha_composite(lv.resize((hud.W * 4, hud.H * 4), Image.NEAREST))
    screen.crop((540, 720, 1380, 1080)).save(hud.OUT / "class-icon-v6-hud.png")
    print(f"wrote {hud.OUT / 'class-icons-v6-8x.png'}")


if __name__ == "__main__":
    main()
