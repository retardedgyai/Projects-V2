"""Class icons v2: Monumenta-style vivid item art in an enlarged centre diamond (direction mock).

    python scripts/mock_class_icons_v2.py

Reference (studied): Monumenta class selection / description GUIs (wiki + Monumenta MMO Resource
Pack gallery): the class mark is its weapon drawn as a Minecraft item — diagonal, 16 px — in
saturated material colours (gold, crimson, cyan), seated in a recessed stone medallion.
A 45-degree item only fits a diamond whose inner half-diagonal is >= 14, so the diamond grows from
r=13 to r=17. Every item is built on one axis: guard/hilt at B=(5,10), tip toward the top-right.

Writes build/hud-preview/directions/class-icons-v2-8x.png and class-icon-v2-hud.png.
"""
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402

C = lambda h: hud.rgba(h)  # noqa: E731


class Item:
    def __init__(self):
        self.img = Image.new("RGBA", (16, 16))

    def p(self, x, y, col):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.putpixel((x, y), C(col))

    def axis(self, t, b=(5, 10)):  # point t steps from B along the blade direction (up-right)
        return b[0] + t, b[1] - t

    def blade(self, t0, t1, section, taper=2, b=(5, 10)):
        """section: colours across one row, left to right (upper-left edge first)."""
        for t in range(t0, t1 + 1):
            cx, cy = self.axis(t, b)
            cut = max(0, t - (t1 - taper))  # the last rows narrow to a point
            cols = section[cut:len(section) - cut] if cut else section
            start = cx - len(section) // 2 + cut
            for k, col in enumerate(cols):
                self.p(start + k, cy, col)

    def perp(self, s0, s1, col, col2=None, b=(5, 10)):
        for s in range(s0, s1 + 1):
            self.p(b[0] + s, b[1] + s, col)
            if col2:
                self.p(b[0] + s + 1, b[1] + s, col2)

    def outline(self, dark):
        """Vanilla-like: a dark edge only on the lower-right of the silhouette."""
        src = self.img.copy()
        for y in range(16):
            for x in range(16):
                if src.getpixel((x, y))[3]:
                    for dx, dy in ((1, 0), (0, 1), (1, 1)):
                        if 0 <= x + dx < 16 and 0 <= y + dy < 16 and not src.getpixel((x + dx, y + dy))[3]:
                            self.img.putpixel((x + dx, y + dy), C(dark))
        return self.img


def warrior():  # greatsword: cool steel, crimson fuller, gold guard, ruby pommel
    it = Item()
    it.blade(1, 9, ["#5b6478", "#ffffff", "#d2271f", "#d8deec", "#8b95ad"], taper=2)
    it.perp(-3, 3, "#ffd24a", "#c07a12")
    it.p(5, 10, "#ff4058")
    for t, col in ((1, "#7a1e18"), (2, "#b8342a"), (3, "#7a1e18")):
        x, y = it.axis(-t)
        it.p(x, y, col); it.p(x + 1, y, "#4a100c")
    it.p(1, 14, "#ff4058"); it.p(2, 14, "#a8122a"); it.p(1, 15, "#a8122a"); it.p(0, 14, "#ffb0b8")
    return it.outline("#1a0c10")


def ranger():  # recurve bow bulging toward the top-left, green-stained wood, gold tips, white string
    it = Item()
    pts = []
    for k in range(41):
        t = k / 40
        x = (1 - t) ** 2 * 13 + 2 * (1 - t) * t * 1 + t * t * 1
        y = (1 - t) ** 2 * 1 + 2 * (1 - t) * t * 1 + t * t * 13
        pts.append((round(x), round(y)))
    for i, (x, y) in enumerate(dict.fromkeys(pts)):
        it.p(x, y, "#7ad85a" if i % 4 else "#3e8e2a"); it.p(x + 1, y + 1, "#2a5a1c")
    for k in range(1, 12):
        if k % 2:
            it.p(13 - k, 1 + k, "#f4f4f4")
    for x, y in ((13, 1), (14, 0), (1, 13), (0, 14)):
        it.p(x, y, "#ffd24a")
    it.p(4, 4, "#ffd24a"); it.p(5, 3, "#c07a12")  # grip wrap
    return it.outline("#14200c")


def mage():  # gold wand, three prongs around a burning gem (Monumenta's mage mark is a gold trident wand)
    it = Item()
    for t in range(-4, 4):
        x, y = it.axis(t)
        it.p(x, y, "#b8322a" if t < 0 else "#ffd24a"); it.p(x + 1, y, "#6e140e" if t < 0 else "#b07a12")
    for x, y, col in ((9, 4, "#ffd24a"), (8, 3, "#ffd24a"), (10, 5, "#ffd24a"), (12, 1, "#ffd24a"),
                      (13, 0, "#fff0a0"), (14, 3, "#ffd24a"), (15, 2, "#fff0a0"), (7, 2, "#ffd24a"),
                      (6, 1, "#fff0a0")):
        it.p(x, y, col)
    for x, y, col in ((10, 3, "#ff8a1c"), (11, 3, "#ffd860"), (10, 2, "#ffd860"), (11, 2, "#ffffff"),
                      (12, 3, "#ff5a1c"), (11, 4, "#d8341a"), (12, 2, "#ff8a1c")):
        it.p(x, y, col)
    return it.outline("#2a0e08")


def starweaver():  # violet wand topped by a gold four-point star
    it = Item()
    for t in range(-4, 3):
        x, y = it.axis(t)
        it.p(x, y, "#9a5af0" if t % 2 else "#d0a8ff"); it.p(x + 1, y, "#4e2398")
    star = {(11, 4): "#ffffff", (10, 4): "#ffd24a", (12, 4): "#ffd24a", (11, 3): "#ffd24a", (11, 5): "#ffd24a",
            (9, 4): "#ffb030", (13, 4): "#ffb030", (11, 2): "#ffb030", (11, 6): "#ffb030", (8, 4): "#ff9a1c",
            (14, 4): "#ff9a1c", (11, 1): "#ff9a1c", (11, 7): "#ff9a1c", (10, 3): "#fff0a0", (12, 5): "#e0a020"}
    for (x, y), col in star.items():
        it.p(x, y, col)
    for x, y in ((14, 1), (8, 1), (14, 7)):
        it.p(x, y, "#e8c8ff")
    return it.outline("#1c0c30")


def assassin():  # curved dagger: violet-sheened blade, black hilt, amethyst pommel
    it = Item()
    it.blade(1, 7, ["#3a2a5a", "#f0e8ff", "#b48aff", "#7a5ad0"], taper=2)
    it.p(13, 2, "#f0e8ff"); it.p(14, 2, "#7a5ad0")
    it.perp(-2, 2, "#9aa0b8", "#4a4e60")
    for t in (1, 2):
        x, y = it.axis(-t)
        it.p(x, y, "#2a2a34"); it.p(x + 1, y, "#14141a")
    it.p(2, 13, "#c070ff"); it.p(3, 13, "#7a2ad0"); it.p(2, 14, "#7a2ad0"); it.p(1, 13, "#f0c8ff")
    return it.outline("#0c0814")


def templar():  # gold flanged mace with a sapphire, white-wrapped haft
    it = Item()
    for t in range(-4, 3):
        x, y = it.axis(t)
        it.p(x, y, "#f0e8d8" if t % 2 else "#b8a888"); it.p(x + 1, y, "#6a5a40")
    head = {}
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            if abs(dx) + abs(dy) <= 4:
                head[(11 + dx, 4 + dy)] = "#ffd24a" if dx + dy < 0 else "#e0a020" if dx + dy < 3 else "#a86c10"
    for (x, y), col in head.items():
        it.p(x, y, col)
    for x, y in ((11, 0), (15, 4), (11, 8), (7, 4), (14, 1), (8, 7)):
        it.p(x, y, "#fff0a0")
    it.p(11, 4, "#3a8cff"); it.p(10, 4, "#9ad0ff"); it.p(11, 5, "#1a4aa8")
    return it.outline("#2a1804")


def healer():  # white-gold staff crowned with a green life crystal in a ring
    it = Item()
    for t in range(-4, 3):
        x, y = it.axis(t)
        it.p(x, y, "#ffffff" if t % 2 else "#e8dcc0"); it.p(x + 1, y, "#a89870")
    for a in ((9, 2), (10, 1), (12, 1), (13, 2), (14, 4), (13, 6), (12, 7), (10, 7), (9, 6), (8, 4), (11, 0), (15, 3)):
        it.p(a[0], a[1], "#ffd24a")
    crystal = {(11, 2): "#d8ffb8", (11, 3): "#9cf07c", (12, 3): "#5ac83a", (10, 4): "#9cf07c", (11, 4): "#ffffff",
               (12, 4): "#3e9c3a", (11, 5): "#5ac83a", (12, 5): "#2a6a24", (11, 6): "#2a6a24"}
    for (x, y), col in crystal.items():
        it.p(x, y, col)
    return it.outline("#2a2410")


ICONS = [("warrior", warrior, "#e3283e"), ("ranger", ranger, "#5ec64a"), ("mage", mage, "#ff8a1c"),
         ("starweaver", starweaver, "#9a5af0"), ("assassin", assassin, "#8a4cff"),
         ("templar", templar, "#f4c84e"), ("healer", healer, "#9cf07c")]


def big_diamond(cv, cx, cy, accent, r=17):
    D = hud.D
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["out"])
    for j in range(-r, r + 1):
        w = r - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["metal"][1] if j < 0 else D["metal"][2])
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), C(accent))
    for j in range(-r + 3, r - 2):
        w = r - 3 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["slot"])


def main():
    sheet = Image.new("RGBA", (7 * 42 + 4, 42), hud.D["plate"])
    for k, (name, fn, accent) in enumerate(ICONS):
        cx = 23 + k * 42
        big_diamond(sheet, cx, 21, accent)
        sheet.alpha_composite(fn(), (cx - 8, 21 - 8))
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(hud.OUT / "class-icons-v2-8x.png")
    base, icons, top, jp = hud.design_f1(center=-1)
    py = hud.HB_Y - 5 - 13
    cy = py                     # bottom tip ends on the resource strip, clear of the hotbar slots
    big_diamond(base, hud.C, cy, ICONS[0][2])
    base.alpha_composite(warrior(), (hud.C - 8, cy - 9))
    hud.mc_text(top, hud.C - hud.mc_width("12") // 2 + 1, cy + 9, "12", hud.rgba("80ff20"), outline=True)
    hud.compose((base, icons, top, jp), 4).crop((540, 720, 1380, 1080)).save(hud.OUT / "class-icon-v2-hud.png")
    print(f"wrote {hud.OUT / 'class-icons-v2-8x.png'}")


if __name__ == "__main__":
    main()
