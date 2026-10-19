"""Boss bar artwork (direction approved 2026-10-19).

Graded bosses ("v3"): the combat HUD's own parts — the level diamond with a number plaque, wings and a
crown that grow with the grade, a charcoal plate holding the name row, a thick HP gauge with the
number inside and the break gauge as rage-bar cells. Dedicated bosses get a frame drawn from the boss
itself; the first is the shade wyrm (黒翳竜): its enraged fan of blade-like wing panels and its horned,
eyeless head around a slim gauge.

Coordinates: x relative to the screen centre, y from the screen top (the boss bar's name line sits at
y = 3, so a glyph whose top is at y needs ascent 10 - y). See build_combat_hud.py for the glyphs.
"""
import math

from PIL import Image, ImageDraw

import combat_hud_art as art
import mob_hud_art as mob_art
from combat_hud_art import OUT, hexc, new, px, rect, gem, step_plate, well

# ---- graded layout -------------------------------------------------------------------------------
EMBLEM_CY = 25                  # level diamond centre
PLATE_X, PLATE_W, PLATE_TOP, PLATE_H = -118, 237, 31, 33   # odd widths: symmetric about column 0
NAME_TOP = PLATE_TOP + 3        # name / grade / percentage row (8 px text)
HP_X, HP_W, HP_TOP, HP_ROWS = -112, 225, PLATE_TOP + 13, 9
HP_TEXT_TOP = HP_TOP + 1        # the HP number inside the gauge
LV_TOP = EMBLEM_CY - 3          # the level number on the diamond's plaque
CELL_X, CELL_PITCH, CELL_COUNT, CELL_TOP, CELL_ROWS = -109, 11, 20, PLATE_TOP + 25, 3
ICON_TOP = PLATE_TOP + PLATE_H + 1
EMBLEM_W = 80                   # emblem glyph canvas, centred on x = 0

TRAIL = hexc("c88a80")          # muted, so the white number inside the gauge stays readable
GRADES = ("elite", "rare", "unique", "calamity")
ACCENT = {"elite": "4a8cff", "rare": "a35cff", "unique": "f0a020", "calamity": "e8283a"}
MATS = dict(art.MAT)
WINGS = {  # (dy, length, material, droop) per feather, mirrored
    "elite": [],
    "rare": [(-2, 9, "steel", 0), (2, 6, "steel", 1)],
    "unique": [(-4, 13, "gold", -1), (0, 11, "gold", 0), (4, 8, "steel", 1)],
    "calamity": [(-5, 15, "gold", -2), (-1, 13, "gold", 0), (3, 11, "gold", 1), (7, 7, "steel", 2)],
}


def _paint(img, fill):
    """Fill {(x, y): (material, tone)} with a 1 px outline only where nothing is drawn yet."""
    edge = {(x + a, y + b) for (x, y) in fill for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))} - set(fill)
    for x, y in edge:
        if 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] == 0:
            px(img, x, y, OUT)
    for (x, y), (mat, tone) in fill.items():
        px(img, x, y, hexc(MATS[mat][tone]))


def _feathers(C, cy, spec):
    """Feathers taper 3 -> 2 -> 1 px from the diamond outward, mirrored about C (the class wings' build)."""
    f = {}
    root = C - 15
    for dy, length, mat, droop in spec:
        for i in range(length):
            th = 3 if i < length * .4 else 2 if i < length * .75 else 1
            top = cy + dy - (i * 2) // 5 + (droop * i * i) // (length * length)
            for t in range(th):
                f[(root - i, top + t)] = (mat, 0 if t == 0 else 1 if t == 1 else 2)
    return f | {(2 * C - x, y): v for (x, y), v in f.items()}


def _crown(C, cy, mat):
    """Three sword points on a band, the middle one tallest, mirrored; returns (fill, gem position)."""
    tip = cy - art.DIAMOND_R - 1
    f = {}

    def put(x, y, t):
        f[(x, y)] = (mat, t); f[(2 * C - x, y)] = (mat, t)
    for k in range(4):
        put(C - 5, tip - 1 - k, 0 if k == 3 else 1)
    for k in range(7):
        put(C, tip - 1 - k, 0 if k >= 5 else 1)
        if k < 5:
            put(C - 1, tip - 1 - k, 2)
    for x in range(C - 6, C + 1):
        put(x, tip, 1)
    return f, (C, tip - 1)


def emblem(grade):
    """Wings + diamond + crown + plaque for a grade. Glyph x = -EMBLEM_W // 2, top 0."""
    img = new(EMBLEM_W, EMBLEM_CY + 16)
    C, cy = EMBLEM_W // 2, EMBLEM_CY
    accent = ACCENT[grade]
    if WINGS[grade]:
        _paint(img, _feathers(C, cy, WINGS[grade]))
    art.diamond(img, C, cy, accent)
    if grade == "calamity":
        crown, at = _crown(C, cy, "gold")
        _paint(img, crown)
        gem(img, *at, accent)
    gem(img, C, cy + 10, accent)
    step_plate(img, C - 11, cy - 5, C + 12, cy + 6, cut=1)
    rect(img, C - 9, cy - 3, C + 10, cy + 4, (12, 12, 16, 255))
    return img


def plate(grade):
    """Charcoal plate with the HP well and the empty break cells; 厄災 adds a gold rule on its top edge."""
    img = new(PLATE_W, PLATE_H)
    step_plate(img, 0, 0, PLATE_W, PLATE_H)
    well(img, HP_X - PLATE_X, HP_X - PLATE_X + HP_W, HP_TOP - PLATE_TOP, HP_ROWS, art.BOSS_EMPTY)
    for i in range(CELL_COUNT):
        x = CELL_X - PLATE_X + i * CELL_PITCH
        rect(img, x, CELL_TOP - PLATE_TOP, x + CELL_PITCH - 1, CELL_TOP - PLATE_TOP + CELL_ROWS, art.RAGE_EMPTY)
    if grade == "calamity":
        rect(img, 3, 1, PLATE_W - 3, 2, hexc("d0b35a"))
    return img


def cell(lit):
    """One break-gauge cell: amber (lit) or white (the break countdown)."""
    img = new(CELL_PITCH - 1, CELL_ROWS)
    if lit:
        rect(img, 0, 0, img.width, CELL_ROWS, art.BREAK[1])
        rect(img, 0, 0, img.width, 1, art.BREAK[2]); rect(img, 0, CELL_ROWS - 1, img.width, CELL_ROWS, art.BREAK[0])
    else:
        rect(img, 0, 0, img.width, CELL_ROWS, hexc("ffffff")); rect(img, 0, CELL_ROWS - 1, img.width, CELL_ROWS, hexc("c8c8d0"))
    return img


# ---- shade wyrm (黒翳竜) ----------------------------------------------------------------------------
WY = dict(d0=hexc("0c0a12"), d1=hexc("17131f"), d2=hexc("211b2e"), rib=hexc("3e3352"), rib_hi=hexc("5c4c78"),
          stripe=hexc("5a2a8c"), stripe_hi=hexc("8a4cff"), horn=hexc("e070c0"), horn_hi=hexc("ffb0e8"),
          horn_lo=hexc("a04080"), mote=hexc("d8c8ff"),
          plate=(hexc("0c0a12"), hexc("1a1524"), hexc("2a2238"), hexc("4a3a66")))
WYRM_HP = (hexc("3a1260"), hexc("7a2cc0"), hexc("c49aff"))
WYRM_EMPTY = (20, 10, 30, 255)
WYRM_TRAIL = hexc("9a86c0")
WYRM_FRAME_HALF = 180           # frame canvas half width: two glyphs, x -180..-1 and 0..179
WYRM_HP_X, WYRM_HP_W, WYRM_HP_ROWS = -110, 221, 6
WYRM_CELL_X = -109
WYRM_CELL_ROWS = 2


def _outline(layer):
    a = layer.getchannel("A").load()
    out = layer.copy(); o = out.load()
    for y in range(layer.height):
        for x in range(layer.width):
            if not a[x, y] and any(0 <= x + dx < layer.width and 0 <= y + dy < layer.height and a[x + dx, y + dy]
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                o[x, y] = OUT
    return out


def _mirror(layer, C):
    left = layer.crop((0, 0, C, layer.height))
    out = layer.copy()
    out.paste(Image.new("RGBA", (layer.width - C, layer.height)), (C, 0))
    out.alpha_composite(left.transpose(Image.FLIP_LEFT_RIGHT), (C + 1, 0))   # column C is the axis
    return out


def _poly(layer, pts, col):
    ImageDraw.Draw(layer).polygon([(round(x), round(y)) for x, y in pts], fill=col)


def _blade(layer, pivot, ang_deg, length, width, purple, k):
    """One wing panel: widest at 60 %, tapering to a point; lit top edge, shaded lower edge, lavender tip."""
    a = math.radians(ang_deg)
    ux, uy = math.cos(a), -math.sin(a) * .8
    nx, ny = -uy, ux
    n = math.hypot(nx, ny); nx, ny = nx / n, ny / n

    def at(t, w):
        return pivot[0] + ux * length * t + nx * w, pivot[1] + uy * length * t + ny * w

    def w(t):
        return width * .5 * ((.45 + .55 * t / .6) if t < .6 else (1 - t) / .4)
    ts = (0, .2, .4, .6, .75, .88)
    top = [at(t, w(t)) for t in ts]
    bottom = [at(t, -w(t)) for t in reversed(ts)]
    _poly(layer, top + [at(1, 0)] + bottom, WY["stripe"] if purple else (WY["d2"] if k % 2 else WY["d1"]))
    d = ImageDraw.Draw(layer)
    d.line([tuple(map(round, p)) for p in top[1:]] + [tuple(map(round, at(1, 0)))], fill=WY["stripe_hi"] if purple else WY["rib"])
    d.line([tuple(map(round, p)) for p in bottom[:-1]], fill=WY["d0"])
    _poly(layer, [at(.8, w(.8) * .7), at(1, 0), at(.8, -w(.8) * .7)], WY["rib_hi"])


def _wyrm_head(img, cx, top):
    """Eyeless wedge head from the front (ridges where eyes would be); three pairs of pink horns sweep back."""
    head = new(img.width, img.height)
    rows = [(7, 0), (9, 1), (10, 2), (11, 3), (11, 4), (10, 5), (10, 6), (9, 7), (8, 8), (7, 9), (6, 10), (5, 11),
            (4, 12), (3, 13), (2, 14), (1, 15)]
    for half, j in rows:
        rect(head, cx - half, top + j, cx + half + 1, top + j + 1, WY["d2"] if j < 3 else WY["d1"])
    rect(head, cx - 6, top + 1, cx + 7, top + 2, WY["rib_hi"])
    for side in (-1, 1):
        rect(head, cx + side * 6 - (1 if side < 0 else 0), top + 5, cx + side * 6 + (1 if side > 0 else 0) + 1, top + 6, WY["d0"])
        for x0, y0, count, lean in ((6, 1, 13, 3), (9, 4, 11, 4), (11, 7, 8, 5)):
            for i in range(count):
                x = cx + side * (x0 + (i * i) // (lean * 4)); y = top + y0 - i
                px(head, x, y, WY["horn_hi"] if i > count - 4 else WY["horn"])
                if i < count - 3:
                    px(head, x + side, y, WY["horn_lo"])
    rect(head, cx, top + 3, cx + 1, top + 13, WY["d0"])
    img.alpha_composite(_outline(head))


def _wyrm_canvas(bar_y, height):
    C = WYRM_FRAME_HALF
    layer = new(2 * C, height)
    pivot = (C - 34, bar_y + 6)
    for k in range(9):  # outer blade first, so the inner ones overlap it
        _blade(layer, pivot, 172 - k * 8.2, 96 + 10 * math.sin(math.pi * k / 8) - 3 * k, 13 - k * .5, k in (6, 7), k)
    layer = _outline(_mirror(layer, C))
    for x, y in ((C - 70, bar_y - 40), (C - 98, bar_y - 30), (C - 52, bar_y - 58), (C - 124, bar_y - 12),
                 (C - 84, bar_y - 54), (C - 112, bar_y - 36), (C - 60, bar_y - 22), (C - 136, bar_y - 22), (C - 40, bar_y - 40)):
        for xx in (x, 2 * C - x):
            p = layer.getpixel((xx, y)) if 0 <= y < height else (0, 0, 0, 0)
            if p[3] and p[:3] != OUT[:3]:
                px(layer, xx, y, WY["mote"])
    # slim plate round the gauge, with its wells
    gx = C + WYRM_HP_X
    rect(layer, gx - 4, bar_y - 3, gx + WYRM_HP_W + 4, bar_y + 13, OUT)
    rect(layer, gx - 3, bar_y - 2, gx + WYRM_HP_W + 3, bar_y + 12, WY["plate"][1])
    rect(layer, gx - 3, bar_y - 2, gx + WYRM_HP_W + 3, bar_y - 1, WY["plate"][3])
    rect(layer, gx - 3, bar_y + 11, gx + WYRM_HP_W + 3, bar_y + 12, WY["plate"][0])
    well(layer, gx, gx + WYRM_HP_W, bar_y, WYRM_HP_ROWS, WYRM_EMPTY)
    for i in range(CELL_COUNT):
        x = C + WYRM_CELL_X + i * CELL_PITCH
        rect(layer, x, bar_y + 8, x + CELL_PITCH - 1, bar_y + 8 + WYRM_CELL_ROWS, art.RAGE_EMPTY)
    _wyrm_head(layer, C, bar_y - 18)
    return layer


def _wyrm_layout():
    """Place the bar so the highest blade tip sits at y = 2."""
    probe = _wyrm_canvas(120, 200)
    top = probe.getchannel("A").getbbox()[1]
    bar_y = 120 - (top - 2)
    return bar_y


WYRM_BAR_Y = _wyrm_layout()
WYRM_NAME_TOP = WYRM_BAR_Y + 16    # Lv + name, centred under the gauge
WYRM_PCT_TOP = WYRM_BAR_Y - 12     # percentage, right end above the gauge
WYRM_ICON_TOP = WYRM_BAR_Y + 15


def wyrm_frame():
    """(left half, right half): the frame is wider than one 256 px glyph."""
    full = _wyrm_canvas(WYRM_BAR_Y, WYRM_BAR_Y + 14)
    C = WYRM_FRAME_HALF
    return full.crop((0, 0, C, full.height)), full.crop((C, 0, 2 * C, full.height))


def wyrm_cell(lit):
    img = new(CELL_PITCH - 1, WYRM_CELL_ROWS)
    rect(img, 0, 0, img.width, WYRM_CELL_ROWS, art.BREAK[1] if lit else hexc("ffffff"))
    rect(img, 0, 0, img.width, 1, art.BREAK[2] if lit else hexc("ffffff"))
    return img


ICONS = mob_art.ICONS
icon = mob_art.icon
