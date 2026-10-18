"""Class icons v5: one plain, recognisable object per class, centred (direction mock).

    python scripts/mock_class_icons_v5.py

Creator feedback on v4: the drawings sat off-centre and it was unclear what they showed. So each
class is one readable object, upright on the diamond's axis (the diamond is tallest there), and
the drawing's bounding box is centred on the diamond. Rendering stays in the chosen Samus idiom
(2 px strokes, light on the upper-left, dark on the lower-right, no outline, 1 px drop shadow,
warm brown backing).

Writes build/hud-preview/directions/class-icons-v5-8x.png and class-icon-v5-hud.png.
"""
from pathlib import Path
import math
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
from mock_class_icons_v4 import HUE, ACCENT, diamond, BACK_SHADOW  # noqa: E402

TONE = {"H": 0, "L": 1, "M": 2, "D": 3}


class Art:
    def __init__(self, hue1, hue2):
        self.hues = (hue1, hue2)
        self.px = {}

    def put(self, x, y, tone, second=False):
        self.px[(x, y)] = hud.rgba(HUE[self.hues[1 if second else 0]][TONE[tone]])

    def vbar(self, x, y0, y1, second=False, tones="LM"):
        """A 2 px upright stroke: lit left column, shaded right column."""
        for y in range(y0, y1 + 1):
            self.put(x, y, tones[0], second)
            self.put(x + 1, y, tones[1], second)

    def image(self):
        xs = [x for x, _ in self.px]
        ys = [y for _, y in self.px]
        w, h = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
        img = Image.new("RGBA", (w, h))
        for (x, y), c in self.px.items():
            img.putpixel((x - min(xs), y - min(ys)), c)
        return img


def warrior():  # greatsword, point down: steel blade, crimson guard / grip / pommel
    a = Art("steel", "crimson")
    a.put(7, 0, "H", True); a.put(8, 0, "L", True); a.put(6, 1, "L", True); a.put(7, 1, "L", True)
    a.put(8, 1, "M", True); a.put(9, 1, "D", True)
    a.vbar(7, 2, 4, True, "MD")
    for x in range(2, 14):
        a.put(x, 5, "H" if x < 5 else "L", True)
        a.put(x, 6, "M" if x < 12 else "D", True)
    for y in range(7, 15):
        a.put(6, y, "H"); a.put(7, y, "L"); a.put(8, y, "M"); a.put(9, y, "D")
    a.put(7, 15, "L"); a.put(8, 15, "M"); a.put(7, 16, "M")
    return a


def ranger():  # bow standing upright, arrow nocked and drawn to the right
    a = Art("steel", "green")
    arc = [(9, 0), (8, 1), (7, 1), (6, 2), (5, 3), (4, 4), (4, 5), (3, 6), (3, 7), (3, 8), (3, 9), (3, 10),
           (4, 11), (4, 12), (5, 13), (6, 14), (7, 15), (8, 15), (9, 16)]
    for x, y in arc:
        a.put(x, y, "L", True); a.put(x + 1, y, "D", True)
    for y in range(1, 16):
        a.put(10, y, "H" if y < 8 else "M")
    for x in range(2, 13):
        a.put(x, 8, "L")
    a.put(13, 8, "H"); a.put(12, 7, "L"); a.put(12, 9, "M"); a.put(14, 8, "L")
    a.put(1, 7, "H", True); a.put(2, 7, "L", True); a.put(1, 9, "M", True); a.put(2, 9, "M", True)
    return a


def mage():  # an ice staff crowned with a burning orb
    a = Art("fire", "ice")
    for y in range(0, 8):
        for x in range(3, 12):
            d = math.hypot(x - 7, y - 4)
            if d <= 3.6:
                a.put(x, y, "H" if d < 1.2 else "L" if (x - 7) + (y - 4) < 1 else "M" if d < 3 else "D")
    a.put(7, -1, "L"); a.put(6, -2, "L"); a.put(8, -1, "M")           # flame tongue
    a.put(3, 6, "L", True); a.put(4, 8, "L", True); a.put(11, 6, "M", True); a.put(10, 8, "M", True)  # prongs
    a.vbar(7, 8, 17, True, "LM")
    a.put(6, 12, "H", True); a.put(9, 12, "M", True)                    # a frost knot on the haft
    return a


def assassin():  # a dagger, point up, wreathed in violet smoke
    a = Art("steel", "violet")
    a.put(7, 0, "H"); a.put(7, 1, "H"); a.put(8, 1, "M")
    for y in range(2, 9):
        a.put(6, y, "H"); a.put(7, y, "L"); a.put(8, y, "M")
    for x in range(4, 11):
        a.put(x, 9, "L" if x < 8 else "D")
    a.vbar(7, 10, 12, True, "MD")
    a.put(7, 13, "H", True); a.put(8, 13, "L", True); a.put(7, 14, "M", True); a.put(8, 14, "D", True)
    for x, y, t in ((3, 4, "L"), (2, 5, "L"), (3, 6, "M"), (2, 7, "M"), (11, 5, "L"), (12, 6, "M"),
                    (11, 7, "M"), (12, 8, "D"), (4, 11, "M"), (10, 11, "M")):
        a.put(x, y, t, True)
    return a


def templar():  # a heater shield: gold rim, steel face, gold cross
    a = Art("gold", "steel")
    for y in range(0, 13):
        hw = 5 if y < 7 else 5 - (y - 6)
        for x in range(7 - hw, 8 + hw):
            rim = x in (7 - hw, 7 + hw) or y == 0 or y == 12 - (0 if hw else 0)
            if rim:
                a.put(x, y, "L" if x <= 7 and y < 8 else "M" if x <= 7 else "D")
            else:
                a.put(x, y, "L" if x < 7 else "M", True)
    for y in range(2, 10):
        a.put(7, y, "H")
    for x in range(4, 11):
        a.put(x, 4, "H" if x < 7 else "L")
    return a


def healer():  # a thick glowing cross with four motes of light
    a = Art("life", "gold")
    for y in range(0, 15):
        for x in (6, 7, 8):
            a.put(x, y, "H" if x == 6 else "L" if x == 7 else "M")
    for x in range(0, 15):
        for y in (5, 6, 7):
            if x in (6, 7, 8):
                continue
            a.put(x, y, "H" if y == 5 else "L" if y == 6 else "M")
    a.put(7, 6, "H")
    for x, y in ((2, 1), (12, 1), (2, 11), (12, 11)):
        a.put(x, y, "L", True)
    return a


ORDER = [("warrior", warrior), ("ranger", ranger), ("mage", mage), ("assassin", assassin),
         ("templar", templar), ("healer", healer)]


def seat(cv, art, cx, cy):
    img = art.image()
    x0, y0 = cx - img.width // 2, cy - img.height // 2  # bounding box centred on the diamond
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] and not (x + 1 < img.width and y + 1 < img.height and img.getpixel((x + 1, y + 1))[3]):
                hud.px(cv, x0 + x + 1, y0 + y + 1, BACK_SHADOW)
    cv.alpha_composite(img, (x0, y0))


def main():
    sheet = Image.new("RGBA", (6 * 34 + 4, 34), hud.D["plate"])
    for k, (name, fn) in enumerate(ORDER):
        cx = 19 + k * 34
        diamond(sheet, cx, 17, ACCENT[name])
        seat(sheet, fn(), cx, 17)
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(hud.OUT / "class-icons-v5-8x.png")
    base, icons, top, jp = hud.design_f1(center=-1)
    cy = hud.HB_Y - 5 - 13 + 4
    diamond(base, hud.C, cy, ACCENT["warrior"])
    seat(base, warrior(), hud.C, cy)
    hud.mc_text(top, hud.C - hud.mc_width("12") // 2 + 1, cy + 10, "12", hud.rgba("80ff20"), outline=True)
    hud.compose((base, icons, top, jp), 4).crop((540, 720, 1380, 1080)).save(hud.OUT / "class-icon-v5-hud.png")
    print(f"wrote {hud.OUT / 'class-icons-v5-8x.png'}")


if __name__ == "__main__":
    main()
