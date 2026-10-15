"""Pixel-art combat HUD: one texel = one GUI pixel everywhere, no resampled artwork.

Every sprite here is drawn at its final size. Skill art (assets/core-ui/skills/*.png, 32 px)
is used 1:1 inside a 36 px card. Text uses the pinned 12 px MaruMinya at its native grid.

Outputs (all generated, git-ignored):
  font/hud*.json                      bitmap providers, one per vertical placement
  textures/gui/core/hud_*.png         glyph sheets
  menu/hud-glyphs.tsv                 name, font, code, advance  (read by CoreHudGlyphs)
  menu/hud-text.tsv                   codepoint, glyph, advance  (read by CoreHudGlyphs)
  assets/minecraft/...                transparent vanilla HUD sprites (CoreUiPackPolicy allowlist)

Placement conventions
  Action bar (CoreHudLayout): a glyph whose top edge is `top` GUI px above the screen bottom has
  ascent = top - 65 (text baseline sits at height - 72; bitmap top = y + 7 - ascent).
  Boss bar name line is drawn at y = 3, so a glyph top at y=t needs ascent = 10 - t.
  Name tags and text displays draw at y = 0: top = 7 - ascent.
"""
from pathlib import Path
import json
import math

from PIL import Image, ImageDraw, ImageFont

from build_core_hud_assets import SKILLS

ROOT = Path(__file__).resolve().parents[1]
HUD = "assets/minecraft/textures/gui/sprites/hud/"
BOSS = "assets/minecraft/textures/gui/sprites/boss_bar/"
FONT_PATH = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"

# ---------------------------------------------------------------- palette
OUT = (7, 8, 11)
P_LO = (12, 13, 17)
P_BODY = (22, 25, 31)
P_BODY2 = (19, 21, 27)
P_HI = (46, 51, 60)
STEEL = (61, 67, 77)
STEEL_HI = (98, 106, 118)
GOLD_D = (110, 82, 38)
GOLD = (184, 145, 74)
GOLD_HI = (233, 201, 119)
GOLD_PALE = (247, 230, 176)
TEAL = (79, 214, 200)
TEAL_D = (25, 92, 90)
RECESS = (6, 7, 9)

RAMPS = {
    # top highlight → bottom shadow (7 rows); empty rows; leading edge
    "hp": ([(255, 166, 140), (242, 86, 76), (226, 62, 66), (204, 48, 60), (176, 38, 56), (142, 30, 50), (104, 24, 42)],
           [(46, 20, 27), (36, 15, 22), (33, 14, 20), (31, 13, 19), (29, 12, 18), (27, 11, 17), (24, 10, 15)],
           (255, 214, 196)),
    "mp": ([(170, 228, 255), (82, 170, 244), (62, 142, 228), (48, 116, 206), (38, 92, 178), (30, 70, 146), (24, 52, 110)],
           [(18, 30, 52), (14, 24, 42), (13, 22, 39), (12, 21, 37), (11, 20, 35), (10, 19, 33), (9, 16, 28)],
           (214, 242, 255)),
    "boss": ([(255, 150, 120), (236, 70, 62), (214, 50, 56), (190, 40, 54), (160, 32, 50), (128, 26, 46), (92, 20, 38)],
             [(44, 18, 24), (34, 13, 19), (31, 12, 18), (29, 11, 17), (27, 10, 16), (25, 10, 15), (22, 9, 13)],
             (255, 220, 200)),
}
RESOURCE = [(255, 226, 154), (240, 180, 60), (212, 146, 42), (176, 112, 32), (136, 84, 26)]
RESOURCE_EMPTY = [(40, 30, 14), (32, 24, 11), (30, 22, 10), (28, 21, 10), (24, 18, 8)]
TRAIL = [(255, 240, 214), (246, 222, 184), (240, 210, 170), (232, 198, 158), (222, 186, 146), (208, 170, 132), (190, 152, 118)]
SHIELD = [(236, 252, 255), (160, 232, 248), (110, 206, 236)]

TINY = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "/": ["001", "001", "010", "100", "100"], "+": ["000", "010", "111", "010", "000"],
    "L": ["100", "100", "100", "100", "111"], "v": ["000", "101", "101", "101", "010"],
}
TINY_CHARS = "0123456789/+Lv"
BIG = {
    "0": [".###.", "##.##", "##.##", "##.##", "##.##", "##.##", ".###."],
    "1": [".##..", "###..", ".##..", ".##..", ".##..", ".##..", "####."],
    "2": [".###.", "##.##", "...##", "..##.", ".##..", "##...", "#####"],
    "3": ["####.", "...##", "...##", ".###.", "...##", "...##", "####."],
    "4": ["...##", "..###", ".####", "##.##", "#####", "...##", "...##"],
    "5": ["#####", "##...", "####.", "...##", "...##", "##.##", ".###."],
    "6": [".###.", "##...", "####.", "##.##", "##.##", "##.##", ".###."],
    "7": ["#####", "...##", "..##.", "..##.", ".##..", ".##..", ".##.."],
    "8": [".###.", "##.##", "##.##", ".###.", "##.##", "##.##", ".###."],
    "9": [".###.", "##.##", "##.##", "##.##", ".####", "...##", ".###."],
}
BIG_CHARS = "0123456789"


def vanilla_overrides():
    """Every vanilla HUD sprite this pack blanks. Mirrors CoreUiPackPolicy.vanillaOverrides."""
    result = {}
    for kind in ("", "absorbing_", "frozen_", "poisoned_", "withered_"):
        for hardcore in ("", "hardcore_"):
            for fill in ("full", "half"):
                for blink in ("", "_blinking"):
                    result[f"{HUD}heart/{kind}{hardcore}{fill}{blink}.png"] = (9, 9)
    for variant in ("container", "container_blinking", "container_hardcore", "container_hardcore_blinking"):
        result[f"{HUD}heart/{variant}.png"] = (9, 9)
    for fill in ("empty", "half", "full"):
        for hunger in ("", "_hunger"):
            result[f"{HUD}food_{fill}{hunger}.png"] = (9, 9)
        # Armour points live in the server's own defence stat; the vanilla row is noise.
        result[f"{HUD}armor_{fill}.png"] = (9, 9)
    # Level and progress move into the HUD plate.
    result[f"{HUD}experience_bar_background.png"] = (182, 5)
    result[f"{HUD}experience_bar_progress.png"] = (182, 5)
    # The pink bar is reserved for the pixel boss bar drawn by its name component.
    result[f"{BOSS}pink_background.png"] = (182, 5)
    result[f"{BOSS}pink_progress.png"] = (182, 5)
    return result


# ---------------------------------------------------------------- glyph registry
class FontBuilder:
    """Collect glyph groups into bitmap providers. Each group shares one sheet and one ascent."""

    def __init__(self, key, start=0xE000):
        self.key = key
        self.providers = []
        self.code = start
        self.rows = []  # (name, font, code, advance)

    def group(self, sheet_name, named_images, ascent, columns=16):
        """named_images: list of (name, image). Ink sits at the top-left of a uniform cell."""
        width = max(image.width for _, image in named_images)
        ink_height = max(image.height for _, image in named_images)
        height = max(ink_height, ascent)
        assert width <= 256 and height <= 256, sheet_name
        columns = min(columns, len(named_images))
        rows = math.ceil(len(named_images) / columns)
        sheet = Image.new("RGBA", (width * columns, height * rows))
        chars = []
        for index, (name, image) in enumerate(named_images):
            bounds = image.getchannel("A").getbbox()
            assert bounds is not None, f"Empty glyph {name}"
            sheet.alpha_composite(image, ((index % columns) * width, (index // columns) * height))
            code = self.code
            self.code += 1
            chars.append(chr(code))
            self.rows.append((name, self.key, code, bounds[2] + 1))
        # Pad the final row with unique private glyphs (never NUL).
        while len(chars) < rows * columns:
            chars.append(chr(self.code))
            self.code += 1
        grid = ["".join(chars[r * columns:(r + 1) * columns]) for r in range(rows)]
        assert self.code <= 0xF8FF
        self.providers.append((sheet_name, sheet, {"type": "bitmap", "file": f"projects:gui/core/{sheet_name}.png",
                                                   "height": height, "ascent": ascent, "chars": grid}))


def hud_ascent(top):
    return top - 65


# ---------------------------------------------------------------- drawing helpers
def rgba(color, alpha=255):
    return (*color, alpha)


def new(width, height):
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))


def put(image, x, y, color, alpha=255):
    if 0 <= x < image.width and 0 <= y < image.height:
        image.putpixel((x, y), rgba(color, alpha))


def outlined(mask_rows, ink=(255, 255, 255), shade=None, outline=OUT):
    """Glyph from '#'/'1' rows with a 1 px dark outline (8-neighbour)."""
    h, w = len(mask_rows), len(mask_rows[0])
    image = new(w + 2, h + 2)
    on = {(x, y) for y, row in enumerate(mask_rows) for x, c in enumerate(row) if c in "#1"}
    for x, y in on:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                put(image, x + 1 + dx, y + 1 + dy, outline)
    # Close pin-holes the 8-neighbour outline leaves beside diagonal strokes ("/", "v"), so the
    # gauge colour under a number never shows through as a stray dot.
    for y in range(image.height):
        for x in range(image.width):
            if image.getpixel((x, y))[3]:
                continue
            filled = lambda dx, dy: 0 <= x + dx < image.width and 0 <= y + dy < image.height and image.getpixel((x + dx, y + dy))[3] > 0
            if (filled(-1, 0) and filled(1, 0)) or (filled(0, -1) and filled(0, 1)) or (x in (0, image.width - 1) and filled(0, 1) and filled(1 if x == 0 else -1, 0)):
                put(image, x, y, outline)
    for x, y in on:
        color = shade(y, h) if shade else ink
        put(image, x + 1, y + 1, color)
    return image


def digit_shade(y, h):
    # Lit from above: white top, warm ivory lower rows (tints multiply this).
    return (255, 255, 255) if y < h // 2 else (226, 222, 214)


def solid(width, rows):
    image = new(width, len(rows))
    for y, color in enumerate(rows):
        for x in range(width):
            put(image, x, y, color)
    return image


def cells(width, rows):
    """Gauge fill with a 4 px energy-cell rhythm. Binary segments start on multiples of 4, so the
    rhythm stays continuous however the fill is split."""
    image = solid(width, rows)
    for x in range(3, width, 4):
        for y in range(1, len(rows)):
            r, g, b, a = image.getpixel((x, y))
            image.putpixel((x, y), (r * 84 // 100, g * 84 // 100, b * 84 // 100, a))
    return image


def segments(prefix, rows, widths, textured=False):
    return [(f"{prefix}_{w}", cells(w, rows) if textured and len(rows) > 2 else solid(w, rows)) for w in widths]


BINARY = (1, 2, 4, 8, 16, 32, 64, 128)


# ---------------------------------------------------------------- plate
PLATE_W, PLATE_H = 182, 24
PLATE_TOP = 47  # height - 47 .. height - 24, flush on the hotbar.
HP_FILL = (4, 4, 76)   # local x, local y, width (7 rows)
MP_FILL = (102, 4, 76)
RES_FILL = (47, 14, 88)  # 5 rows
XP_FILL = (3, 21, 176)  # 1 row


def chamfer_mask(width, height, top_cut, bottom_cut):
    def inside(x, y):
        if not (0 <= x < width and 0 <= y < height):
            return False
        if y < top_cut and (x < top_cut - y or x > width - 1 - (top_cut - y)):
            return False
        rb = height - 1 - y
        if rb < bottom_cut and (x < bottom_cut - rb or x > width - 1 - (bottom_cut - rb)):
            return False
        return True
    return inside


def panel(width, height, top_cut=3, bottom_cut=1, body=(P_BODY, P_BODY2)):
    """Opaque gunmetal panel: dark rim, 1 px bevel lit from the top-left, quiet two-tone rows."""
    image = new(width, height)
    inside = chamfer_mask(width, height, top_cut, bottom_cut)
    for y in range(height):
        for x in range(width):
            if not inside(x, y):
                continue
            ring = not all(inside(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if ring:
                put(image, x, y, OUT)
                continue
            upper = not inside(x, y - 2) or not inside(x - 2, y) or not inside(x - 1, y - 2) or not inside(x + 1, y - 2)
            lower = not inside(x, y + 2) or not inside(x + 2, y)
            if upper and not lower:
                put(image, x, y, P_HI)
            elif lower:
                put(image, x, y, P_LO)
            else:
                put(image, x, y, body[y % 2])
    return image


def recess(image, x0, y0, width, height, empty_rows, ticks=()):
    """A sunken well: dark rim, empty gauge rows inside, faint quarter ticks on the rim."""
    for y in range(height):
        for x in range(width):
            edge = x in (0, width - 1) or y in (0, height - 1)
            put(image, x0 + x, y0 + y, RECESS if edge else empty_rows[(y - 1) % len(empty_rows)])
    for t in ticks:
        put(image, x0 + 1 + t, y0 + height - 1, STEEL)
        put(image, x0 + 1 + t, y0, P_LO)


def build_plate():
    image = panel(PLATE_W, PLATE_H)
    # Gold trim on the top rim, broken by the level crest in the middle.
    for x in range(4, PLATE_W - 4):
        if not 80 <= x <= 101:
            put(image, x, 0, GOLD_D)
    for x in (4, PLATE_W - 5):
        put(image, x, 0, GOLD)
    hp_empty, mp_empty = RAMPS["hp"][1], RAMPS["mp"][1]
    recess(image, HP_FILL[0] - 1, HP_FILL[1] - 1, HP_FILL[2] + 2, 9, hp_empty, ticks=(18, 37, 56))
    recess(image, MP_FILL[0] - 1, MP_FILL[1] - 1, MP_FILL[2] + 2, 9, mp_empty, ticks=(19, 38, 57))
    recess(image, RES_FILL[0] - 1, RES_FILL[1] - 1, RES_FILL[2] + 2, 7, RESOURCE_EMPTY, ticks=(21, 43, 65))
    for x in range(XP_FILL[2]):
        put(image, XP_FILL[0] + x, XP_FILL[1], (14, 36, 36) if x % 4 else (20, 50, 48))
    # Level crest: an elongated hexagon in brass, rising into the gold rim.
    crest = [(82, 99)]
    for y in range(0, 14):
        cut = {0: 3, 1: 2, 2: 1, 11: 1, 12: 2, 13: 3}.get(y, 0)
        left, right = 82 + cut, 99 - cut
        for x in range(left, right + 1):
            edge = x in (left, right) or y in (0, 13)
            if edge:
                put(image, x, y, GOLD if y < 7 else GOLD_D)
            elif y == 1 or x == left + 1:
                put(image, x, y, (52, 42, 24))
            else:
                put(image, x, y, (26, 22, 15))
    put(image, 90, 0, GOLD_HI); put(image, 91, 0, GOLD_HI)
    # Small chevrons that point from the crest to each gauge (near-future console detail).
    for dx, flip in ((-1, True), (1, False)):
        cx = 81 if flip else 100
        put(image, cx, 6, GOLD_D); put(image, cx + dx, 7, GOLD); put(image, cx, 8, GOLD_D)
    # Resource well side brackets.
    for x0 in (41, 139):
        for y in range(14, 19):
            put(image, x0 + 1, y, STEEL if y in (14, 18) else P_HI)
    return image


def build_shield_icon():
    rows = ["#######", "#######", "#######", "#######", ".#####.", "..###..", "...#..."]
    image = outlined(rows, shade=lambda y, h: SHIELD[0] if y < 2 else SHIELD[1] if y < 4 else SHIELD[2])
    put(image, 4, 2, (60, 120, 150)); put(image, 4, 3, (60, 120, 150)); put(image, 4, 4, (60, 120, 150))
    return image


def build_pip(lit):
    rows = ["..#..", ".###.", "#####", ".###.", "..#.."]
    color = (lambda y, h: (240, 220, 255) if y < 2 else (180, 140, 250)) if lit else (lambda y, h: (52, 46, 66))
    return outlined(rows, shade=color)


# ---------------------------------------------------------------- skill cards
CARD = 36
CD_STEPS = 24
NO_MANA = CD_STEPS + 1
LOCKED = CD_STEPS + 2
SKILL_TOP = 96


def card_ring(state):
    if state == "ready":
        return GOLD_HI, GOLD, GOLD_D
    if state == "mana":
        return (126, 186, 240), (58, 116, 190), (32, 62, 112)
    if state == "locked":
        return (52, 56, 64), (36, 39, 45), (24, 26, 30)
    return STEEL_HI, STEEL, (36, 40, 46)


def skill_card(art, frame):
    image = new(CARD, CARD)
    state = "ready" if frame == 0 else "mana" if frame == NO_MANA else "locked" if frame == LOCKED else "cd"
    light, mid, dark = card_ring(state)
    cut = chamfer_mask(CARD, CARD, 2, 2)
    for y in range(CARD):
        for x in range(CARD):
            if not cut(x, y):
                continue
            d = min(x, y, CARD - 1 - x, CARD - 1 - y)
            diagonal = not all(cut(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if d == 0 or diagonal:
                put(image, x, y, OUT)
            elif d == 1:
                put(image, x, y, light if (y == 1 or x == 1) and not (x == CARD - 2 or y == CARD - 2) else dark if (x == CARD - 2 or y == CARD - 2) else mid)
    pixels = art.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = pixels[x, y]
            if a == 0:
                r, g, b, a = 10, 12, 16, 255
            if state == "mana":
                gray = (r * 3 + g * 6 + b) // 10
                r, g, b = gray * 30 // 100, gray * 48 // 100, min(255, gray * 80 // 100 + 24)
            elif state == "locked":
                gray = (r * 3 + g * 6 + b) // 10
                r, g, b = gray * 22 // 100, gray * 23 // 100, gray * 26 // 100
            elif state == "cd":
                remaining = frame / CD_STEPS
                clockwise = (math.atan2(x - 15.5, -(y - 15.5)) + math.tau) % math.tau
                if clockwise / math.tau >= 1 - remaining:
                    gray = (r * 3 + g * 6 + b) // 10
                    r, g, b = (r + gray) * 13 // 100, (g + gray) * 14 // 100, (b + gray) * 18 // 100
            put(image, x + 2, y + 2, (r, g, b))
    if state == "cd" and frame < CD_STEPS:
        # One teal sweep line on the moving edge of the reveal.
        angle = (1 - frame / CD_STEPS) * math.tau
        for step in range(4, 17):
            px = 15.5 + math.sin(angle) * step
            py = 15.5 - math.cos(angle) * step
            put(image, int(math.floor(px)) + 2, int(math.floor(py)) + 2, TEAL if step > 6 else TEAL_D)
    if state == "ready":
        # Brass corner brackets, mirrored to the pixel; the bottom-right corner carries the key number.
        for sx, sy in ((2, 2), (CARD - 3, 2), (2, CARD - 3)):
            dx = 1 if sx < CARD // 2 else -1
            dy = 1 if sy < CARD // 2 else -1
            for i in range(3):
                put(image, sx + dx * i, sy, GOLD_PALE if i == 0 else GOLD_HI)
                put(image, sx, sy + dy * i, GOLD_PALE if i == 0 else GOLD_HI)
    if state == "locked":
        lock = ["..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"]
        icon = outlined(lock, shade=lambda y, h: (214, 199, 164) if y < 3 else (186, 170, 136))
        image.alpha_composite(icon, ((CARD - icon.width) // 2, (CARD - icon.height) // 2))
    return image


# ---------------------------------------------------------------- buffs
BUFF = 12
BUFF_STEPS = 12
BUFFS = {
    # name: (rim colour, symbol rows (8x8), symbol shade)
    "whetstone": ((236, 156, 64), [
        "......##", ".....###", "....###.", "...###..", "#.###...", "####....", ".##.....", "#.#....."],
        lambda y, h: (246, 236, 214) if y < 4 else (196, 186, 170)),
    "potion": ((226, 70, 80), [
        "..####..", "...##...", "...##...", "..####..", ".######.", ".######.", ".######.", "..####.."],
        lambda y, h: (236, 230, 222) if y < 3 else (240, 80, 86) if y > 3 else (255, 150, 140)),
    "dodge": ((79, 214, 200), [
        "........", "#...#...", ".#...#..", "..#...#.", "..#...#.", ".#...#..", "#...#...", "........"],
        lambda y, h: (200, 255, 246)),
}


def buff_icon(name, frame):
    rim, rows, shade = BUFFS[name]
    image = new(BUFF, BUFF)
    for y in range(BUFF):
        for x in range(BUFF):
            d = min(x, y, BUFF - 1 - x, BUFF - 1 - y)
            if d == 0 and (x, y) not in ((0, 0), (BUFF - 1, 0), (0, BUFF - 1), (BUFF - 1, BUFF - 1)):
                put(image, x, y, OUT)
            elif d == 1:
                lit = y == 1 or x == 1
                put(image, x, y, rim if lit else tuple(c * 6 // 10 for c in rim))
            elif d > 1:
                put(image, x, y, (20, 22, 28) if (x + y) % 2 else (24, 26, 33))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                put(image, x + 2, y + 2, shade(y, 8))
    if frame:
        # Cooldown: the unrecovered upper part sinks into shadow, one row per step.
        for y in range(2, 2 + frame * 8 // BUFF_STEPS + (1 if frame * 8 % BUFF_STEPS else 0)):
            for x in range(2, 10):
                r, g, b, a = image.getpixel((x, y))
                image.putpixel((x, y), (r * 30 // 100, g * 30 // 100, b * 34 // 100, a))
    return image


# ---------------------------------------------------------------- text
def text_font():
    assert FONT_PATH.is_file(), "Run build_core_menu_assets first (it fetches the pinned MaruMinya font)"
    return ImageFont.truetype(str(FONT_PATH), 12)


def text_glyphs(chars):
    """Native 12 px MaruMinya, hard pixels, 1 px dark outline. 14x14 cells."""
    font = text_font()
    result = []
    for char in chars:
        if char in (" ", "　"):
            continue
        mask = Image.new("L", (14, 16))
        draw = ImageDraw.Draw(mask)
        draw.fontmode = "1"
        draw.text((1, 13), char, font=font, anchor="ls", fill=255)
        on = [(x, y) for y in range(16) for x in range(14) if mask.getpixel((x, y)) >= 128]
        if not on:
            continue
        image = new(14, 14)
        assert all(1 <= y <= 12 and 1 <= x <= 12 for x, y in on), f"Glyph leaves the 12 px grid: {char}"
        for x, y in on:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    put(image, x + dx, y + dy, OUT)
        for x, y in on:
            put(image, x, y, (255, 255, 255))
        result.append((char, image))
    return result


# ---------------------------------------------------------------- boss bar
BOSS_FILL = 182
BOSS_W = BOSS_FILL + 2 + 24
BOSS_BAR_TOP = 17  # screen y just under the bar frame top; name ink sits above at y=2..15.


def build_boss_frame():
    image = new(BOSS_W, 11)
    x0 = 12
    recess(image, x0, 1, BOSS_FILL + 2, 9, RAMPS["boss"][1], ticks=(45, 90, 136))
    # Outer rim and gold top/bottom lines.
    for x in range(x0 - 1, x0 + BOSS_FILL + 3):
        put(image, x, 0, GOLD_D); put(image, x, 10, OUT)
    for x in range(x0 + 2, x0 + BOSS_FILL, 6):
        put(image, x, 0, GOLD)
    # End caps: brass hexagonal studs with a red core (the boss emblem colour).
    for cx in (5, BOSS_W - 6):
        for y in range(11):
            half = (5 - abs(y - 5)) if abs(y - 5) <= 5 else -1
            for x in range(cx - half, cx + half + 1):
                d = half - abs(x - cx)
                put(image, x, y, OUT if d == 0 else GOLD if (d == 1 and y < 5) else GOLD_D if d == 1 else (150, 30, 40) if d > 2 else (90, 20, 28))
        put(image, cx - 1, 4, (255, 160, 140)); put(image, cx, 4, (230, 80, 70))
    return image


# ---------------------------------------------------------------- mob nameplate (world)
TAG_FILL = 40
TAG_COLORS = {
    "normal": [(250, 120, 100), (214, 56, 56), (150, 34, 44)],
    "elite": [(226, 170, 255), (164, 92, 222), (104, 52, 156)],
    "boss": [(255, 196, 120), (232, 120, 48), (160, 66, 26)],
}
WEAKNESS = {
    "fire": ["..#..", ".##..", ".###.", "#####", ".###."],
    "ice": ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"],
    "thunder": ["...##", "..##.", ".####", "..##.", ".##.."],
}
WEAKNESS_COLOR = {"fire": (255, 140, 60), "ice": (150, 220, 255), "thunder": (255, 230, 90)}


def build_tag_frame():
    image = new(TAG_FILL + 2, 5)
    for y in range(5):
        for x in range(TAG_FILL + 2):
            edge = x in (0, TAG_FILL + 1) or y in (0, 4)
            put(image, x, y, OUT if edge else (30, 12, 18))
    return image


# ---------------------------------------------------------------- build
def build_combat_hud(assets, pack, source, write_json, chars):
    output = assets / "textures/gui/core"
    output.mkdir(parents=True, exist_ok=True)
    fonts = []

    # -- projects:hud (action bar) ---------------------------------------
    hud = FontBuilder("projects:hud")
    hud.group("hud_plate", [("plate", build_plate())], hud_ascent(PLATE_TOP))
    top = PLATE_TOP - HP_FILL[1]
    for bar in ("hp", "mp"):
        fill, _, edge = RAMPS[bar]
        hud.group(f"hud_{bar}_fill", segments(f"{bar}_fill", fill, BINARY[:7], textured=True), hud_ascent(top))
        hud.group(f"hud_{bar}_edge", [(f"{bar}_edge", solid(1, [edge] * 7))], hud_ascent(top))
    hud.group("hud_hp_trail", segments("hp_trail", TRAIL, BINARY[:7]), hud_ascent(top))
    hud.group("hud_hp_shield", segments("hp_shield", SHIELD, BINARY[:7]), hud_ascent(top))
    hud.group("hud_res_fill", segments("res_fill", RESOURCE, BINARY[:7], textured=True), hud_ascent(PLATE_TOP - RES_FILL[1]))
    hud.group("hud_xp_fill", segments("xp_fill", [TEAL], BINARY), hud_ascent(PLATE_TOP - XP_FILL[1]))
    def boxed(image):
        backed = Image.new("RGBA", image.size, rgba(OUT))
        backed.alpha_composite(image)
        return backed
    # Small numbers sit on a dark readout strip (adjacent cells merge): with only a contour, single
    # gauge pixels peek through the gaps between 3 px digits and read as noise.
    tiny = [(c, boxed(outlined(TINY[c], shade=digit_shade))) for c in TINY_CHARS]
    # One digit sheet per row it is used on: bars, resource well, crest, skill keys, buff timers.
    hud.group("hud_tiny_bar", [(f"tiny_bar_{c}", i) for c, i in tiny], hud_ascent(top))
    hud.group("hud_tiny_res", [(f"tiny_res_{c}", i) for c, i in tiny], hud_ascent(PLATE_TOP - RES_FILL[1] + 1))
    hud.group("hud_tiny_crest", [(f"tiny_crest_{c}", i) for c, i in tiny], hud_ascent(PLATE_TOP - 3))
    hud.group("hud_tiny_key", [(f"tiny_key_{c}", i) for c, i in tiny], hud_ascent(SKILL_TOP - 28))
    hud.group("hud_tiny_buff", [(f"tiny_buff_{c}", i) for c, i in tiny], hud_ascent(SKILL_TOP - 30))
    hud.group("hud_shield_icon", [("shield_icon", build_shield_icon())], hud_ascent(PLATE_TOP - 13))
    hud.group("hud_pips", [("pip_on", build_pip(True)), ("pip_off", build_pip(False))], hud_ascent(PLATE_TOP - 14))
    big = [(c, outlined(BIG[c], shade=digit_shade)) for c in BIG_CHARS]
    hud.group("hud_big_cd", [(f"big_cd_{c}", i) for c, i in big], hud_ascent(SKILL_TOP - 14))
    buff_images = [(f"buff_{name}_{frame}", buff_icon(name, frame)) for name in BUFFS for frame in range(BUFF_STEPS + 1)]
    hud.group("hud_buffs", buff_images, hud_ascent(SKILL_TOP - 17))
    # Notice slot backing: translucent pill segments behind the caption line.
    slot_top = SKILL_TOP + 17
    pill_rows = [OUT] + [(12, 14, 19)] * 14 + [OUT]
    def pill(width):
        image = new(width, 16)
        for y, color in enumerate(pill_rows):
            for x in range(width):
                put(image, x, y, color, 255 if color == OUT else 200)
        return image
    def cap(right):
        image = new(4, 16)
        for y in range(16):
            for x in range(4):
                inset = 3 - x if not right else x
                if (y < inset) or (y > 15 - inset):
                    continue
                edge = y == inset or y == 15 - inset or (x == 0 and not right) or (x == 3 and right)
                put(image, x, y, GOLD_D if edge and 3 <= y <= 12 else OUT if edge else (12, 14, 19), 255 if edge else 200)
        return image
    hud.group("hud_pill", [(f"pill_{w}", pill(w)) for w in BINARY] + [("pill_left", cap(False)), ("pill_right", cap(True))],
              hud_ascent(slot_top))
    fonts.append(hud)

    # -- projects:hud_skills ---------------------------------------------
    skills = FontBuilder("projects:hud_skills")
    for index, name in enumerate(SKILLS):
        with Image.open(source / f"skills/{name}.png") as loaded:
            art = loaded.convert("RGBA")
        assert art.size == (32, 32), f"Skill art must be authored at 32 px: {name}"
        # Named by CoreSkillCatalog art index; HUD and source order are the same list.
        frames = [(f"skill_{index}_{frame}", skill_card(art, frame)) for frame in range(LOCKED + 1)]
        skills.group(f"hud_skill_{name}", frames, hud_ascent(SKILL_TOP), columns=9)
    fonts.append(skills)

    # -- text: action-bar caption slot and world/boss lines ---------------
    glyphs = text_glyphs(chars)
    text_rows = []
    def text_font_builder(key, ascent):
        builder = FontBuilder(key)
        builder.group(key.split(":")[1], [(f"text_{ord(c):X}", image) for c, image in glyphs], ascent, columns=32)
        return builder
    caption = text_font_builder("projects:hud_text_slot", hud_ascent(slot_top - 1))
    world = text_font_builder("projects:hud_text", 10)
    boss_name = text_font_builder("projects:hud_text_boss", 10 - 2)
    for c, _ in glyphs:
        row = next(r for r in caption.rows if r[0] == f"text_{ord(c):X}")
        text_rows.append((ord(c), row[2], row[3]))
    fonts += [caption, world, boss_name]

    # -- boss bar --------------------------------------------------------
    boss = FontBuilder("projects:hud_boss")
    boss.group("hud_boss_frame", [("boss_frame", build_boss_frame())], 10 - (BOSS_BAR_TOP - 1))
    fill, _, edge = RAMPS["boss"]
    boss.group("hud_boss_fill", segments("boss_fill", fill, BINARY, textured=True), 10 - BOSS_BAR_TOP - 1)
    boss.group("hud_boss_trail", segments("boss_trail", TRAIL, BINARY), 10 - BOSS_BAR_TOP - 1)
    boss.group("hud_boss_edge", [("boss_edge", solid(1, [edge] * 7))], 10 - BOSS_BAR_TOP - 1)
    boss.group("hud_boss_tiny", [(f"boss_tiny_{c}", i) for c, i in tiny], 10 - BOSS_BAR_TOP - 1)
    fonts.append(boss)

    # -- mob nameplate (world, below the name line) -----------------------
    tag = FontBuilder("projects:hud_tag")
    tag.group("hud_tag_frame", [("tag_frame", build_tag_frame())], 7 - 13)
    for rarity, colors in TAG_COLORS.items():
        tag.group(f"hud_tag_{rarity}", segments(f"tag_{rarity}", colors, BINARY[:6]), 7 - 14)
    tag.group("hud_tag_weak", [(f"weak_{k}", outlined(v, ink=WEAKNESS_COLOR[k])) for k, v in WEAKNESS.items()], 7 - 12)
    tag.group("hud_tag_tiny", [(f"tag_tiny_{c}", i) for c, i in tiny], 7 - 12)
    fonts.append(tag)

    # -- damage numbers (world text displays) ----------------------------
    damage = FontBuilder("projects:hud_damage")
    star = outlined(["...#...", "...#...", ".#####.", "#######", ".#####.", "...#...", "...#..."],
                    shade=lambda y, h: (255, 255, 255))
    weak = outlined(["#####", ".###.", "..#.."], shade=lambda y, h: (255, 255, 255))
    damage.group("hud_damage", [(f"dmg_{c}", i) for c, i in big] + [("dmg_crit", star), ("dmg_weak", weak)], 8)
    fonts.append(damage)

    # -- write ------------------------------------------------------------
    rows = []
    for builder in fonts:
        providers = []
        for sheet_name, sheet, provider in builder.providers:
            sheet.save(output / f"{sheet_name}.png", optimize=True)
            providers.append(provider)
        write_json(assets / f"font/{builder.key.split(':')[1]}.json", {"providers": providers})
        if builder.key.startswith("projects:hud_text"):
            continue
        rows += builder.rows
    (assets / "menu").mkdir(parents=True, exist_ok=True)
    (assets / "menu/hud-glyphs.tsv").write_text(
        "# name\tfont\tcode\tadvance; generated by build_combat_hud.py\n" +
        "".join(f"{name}\t{font}\t{code:X}\t{advance}\n" for name, font, code, advance in rows), encoding="utf-8")
    (assets / "menu/hud-text.tsv").write_text(
        "# codepoint\tglyph\tadvance; generated by build_combat_hud.py (fonts projects:hud_text*)\n" +
        "".join(f"{cp:X}\t{code:X}\t{advance}\n" for cp, code, advance in text_rows), encoding="utf-8")
    for path, size in sorted(vanilla_overrides().items()):
        target = pack / path
        target.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGBA", size).save(target)
    write_json(source / "hud-layout.json", {
        "vanilla": "26.2", "gui_coordinates": "relative to integer screen centre and GUI height",
        "texel": "1 texture pixel = 1 GUI pixel for every HUD glyph",
        "actionbar_bitmap_top": "height - 65 - ascent",
        "plate": {"left_x": -91, "width": PLATE_W, "height": PLATE_H, "top_from_bottom": PLATE_TOP,
                  "hp_fill": HP_FILL, "mp_fill": MP_FILL, "resource_fill": RES_FILL, "xp_fill": XP_FILL},
        "skills": {"size": CARD, "art": 32, "top_from_bottom": SKILL_TOP, "cooldown_steps": CD_STEPS,
                   "no_mana": NO_MANA, "locked": LOCKED},
        "caption_slot_top_from_bottom": slot_top,
        "boss_bar": {"name_top": 2, "bar_top": BOSS_BAR_TOP - 1, "fill": BOSS_FILL},
        "transparent_vanilla_sprites": sorted(vanilla_overrides()),
    })
    return rows, text_rows
