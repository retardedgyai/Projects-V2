"""Draft class emblems for the centre diamond of the combat HUD (direction mock, not pack output).

    python scripts/mock_class_emblems.py

One texel per GUI pixel, drawn in code so each class shares the same palette, light from the
top-left and an automatic 1 px dark outline. Writes build/hud-preview/directions/emblems-8x.png
(the seven emblems in their diamonds) and emblems-in-hud-4x.png (each one seated in the HUD).
"""
from pathlib import Path
import math
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402

OUT = hud.OUT
P = {"o": (12, 12, 16), "L": (244, 244, 250), "M": (180, 182, 196), "D": (106, 108, 124),
     "g": (242, 196, 84), "G": (168, 116, 30), "w": (196, 140, 80), "W": (122, 78, 40), "b": (90, 52, 24),
     "r": (224, 40, 60), "R": (255, 154, 154), "f": (255, 138, 28), "F": (255, 216, 96), "c": (214, 64, 28),
     "i": (127, 216, 255), "I": (58, 160, 232), "j": (26, 90, 168), "p": (216, 168, 255), "P": (154, 90, 240),
     "q": (90, 42, 168), "v": (176, 92, 255), "s": (255, 255, 255), "n": (120, 230, 140)}


class Sprite:
    def __init__(self):
        self.px = {}

    def put(self, x, y, c):
        self.px[(x, y)] = c

    def finish(self):
        """Add the 1 px dark outline around every coloured pixel."""
        out = dict(self.px)
        for (x, y) in self.px:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in self.px:
                    out.setdefault((x + dx, y + dy), "o")
        self.px = out
        return self

    def draw(self, cv, cx, cy):
        xs = [x for x, _ in self.px]
        ys = [y for _, y in self.px]
        ox = cx - (min(xs) + max(xs)) // 2
        oy = cy - (min(ys) + max(ys)) // 2
        for (x, y), c in self.px.items():
            hud.px(cv, ox + x, oy + y, P[c] + (255,))


def warrior():
    s = Sprite()
    for (x, y), c in {(7, 1): "R", (6, 2): "r", (7, 2): "r", (8, 2): "r", (7, 3): "r"}.items():
        s.put(x, y, c)                                 # ruby pommel
    for y in range(4, 8):
        s.put(7, y, "w" if y % 2 else "W")             # wrapped grip
    for x in range(1, 14):
        s.put(x, 8, "g")
        s.put(x, 9, "G" if x > 7 else "g")             # crossguard
    s.put(1, 7, "g"); s.put(13, 7, "G")
    for y in range(10, 19):
        width = 2 if y < 17 else 1 if y < 18 else 0
        for x in range(7 - width, 8 + width):
            s.put(x, y, "L" if x < 7 else "M" if x == 7 else "D")
    s.put(7, 19, "M")
    return s.finish()


def ranger():
    s = Sprite()
    for y in range(0, 17):
        x = 12 - round(math.sqrt(max(0, 72 - (y - 8) ** 2)))
        s.put(x, y, "w"); s.put(x + 1, y, "W")         # bow limb
        s.put(13, y, "s") if y not in (0, 16) else None  # string
    for x in range(3, 15):
        s.put(x, 8, "b")                               # shaft
    for dx, dy in ((15, 8), (14, 7), (14, 9), (16, 8), (15, 7), (15, 9)):
        s.put(dx, dy, "L" if dy <= 8 else "M")         # head
    for dx, dy in ((2, 7), (3, 7), (1, 6), (2, 9), (3, 9), (1, 10), (2, 8)):
        s.put(dx, dy, "r")                             # fletching
    return s.finish()


def mage():
    s = Sprite()
    for y in range(8, 21):
        s.put(7, y, "w" if y % 3 else "W")             # staff
    for x, y in ((5, 7), (9, 7), (4, 5), (10, 5), (6, 8), (8, 8)):
        s.put(x, y, "g")                               # claws
    for y in range(0, 8):
        for x in range(4, 11):
            if (x - 7) ** 2 + (y - 3.5) ** 2 <= 10:
                s.put(x, y, ("F" if y < 3 else "f" if y < 6 else "c") if x < 7 else
                      ("i" if y < 3 else "I" if y < 6 else "j") if x > 7 else "s")
    s.put(5, 2, "s")
    return s.finish()


def starweaver():
    s = Sprite()
    for y in range(-8, 9):
        for x in range(-8, 9):
            ax, ay = abs(x), abs(y)
            if ax * ay <= 5 and ax + ay <= 8:
                s.put(x + 8, y + 8, "s" if ax + ay <= 1 else "g" if ax + ay <= 3 else "P" if ax + ay <= 6 else "q")
    for x, y in ((3, 3), (13, 3), (3, 13), (13, 13)):
        s.put(x, y, "p")                               # woven sparks
    return s.finish()


def assassin():
    s = Sprite()
    for side in (0, 1):
        f = (lambda t: 1 + t) if side == 0 else (lambda t: 13 - t)
        n = 1 if side == 0 else -1
        for t in range(0, 7):
            s.put(f(t), 1 + t, "L"); s.put(f(t) + n, 1 + t, "D")   # blade, tip at the top
        for d in (-2, -1, 1, 2):
            s.put(f(7) + d * n, 1 + 7 - d, "g")                       # guard, perpendicular
        s.put(f(7), 8, "G")
        for t in (8, 9, 10):
            s.put(f(t), 1 + t, "b")                                    # grip
        s.put(f(11), 12, "v"); s.put(f(11) + n, 12, "P")               # violet gem pommel
    return s.finish()


def templar():
    s = Sprite()
    halves = [7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6, 6, 5, 4, 3, 2, 1]
    for y, hw in enumerate(halves):
        for x in range(7 - hw, 8 + hw):
            edge = x in (7 - hw, 7 + hw) or y == 0
            s.put(x, y, "g" if edge else "L" if x < 7 else "M")
    for y in range(3, 13):
        s.put(7, y, "g" if y != 6 else "F")
    for x in range(3, 12):
        s.put(x, 6, "g" if x != 7 else "F")
    s.put(6, 6, "G"); s.put(8, 6, "G")
    return s.finish()


def healer():
    s = Sprite()
    for a in range(0, 360, 6):
        x = 7 + round(6 * math.cos(math.radians(a)))
        y = 6 + round(6 * math.sin(math.radians(a)))
        s.put(x, y, "g")                               # halo
    for y in range(2, 19):
        for x in (6, 7, 8):
            s.put(x, y, "s" if x == 6 else "L" if x == 7 else "M")
    for x in range(2, 13):
        for y in (6, 7):
            s.put(x, y, "s" if y == 6 else "M")
    s.put(7, 16, "n"); s.put(7, 17, "n")              # a green leaf of life at the foot
    return s.finish()


EMBLEMS = [("warrior", warrior), ("ranger", ranger), ("mage", mage), ("starweaver", starweaver),
           ("assassin", assassin), ("templar", templar), ("healer", healer)]


def diamond(cv, cx, cy, r=13):
    D = hud.D
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["out"])
    for j in range(-r, r + 1):
        w = r - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["metal"][1] if j < 0 else D["metal"][2])
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["slot"])


def main():
    sheet = Image.new("RGBA", (7 * 36 + 4, 36), hud.D["plate"])
    for k, (_, fn) in enumerate(EMBLEMS):
        cx = 20 + k * 36
        diamond(sheet, cx, 18)
        fn().draw(sheet, cx, 18)
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(OUT / "emblems-8x.png")
    print(f"wrote {OUT / 'emblems-8x.png'}")


if __name__ == "__main__":
    main()
