"""Boss bar artwork (direction approved 2026-10-19).

Graded bosses: the combat HUD's own parts — the level diamond (larger for higher grades) with a number
plaque over a charcoal plate holding the name row, a thick HP gauge with the number inside and the
break gauge as rage-bar cells. The grade is a labelled tag on the plate's corner plus the plate's frame:
none (精鋭) -> purple ring (希少) -> gold ring + gold corner brackets (特異) -> black iron and a red
glowing ring with iron thorns out of the corners (厄災). Dedicated bosses get a frame drawn from the boss
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

# ---- graded layout (2026-10-19: grade = a labelled tag + the plate's frame; Lost Ark-style restraint) ---
EMBLEM_CY = 20                  # level diamond centre
PLATE_X, PLATE_W, PLATE_TOP, PLATE_H = -118, 237, 28, 32   # odd widths: symmetric about column 0
PLATE_PAD = 9                   # the plate glyph's margin for corner brackets, thorns and the tag
NAME_TOP = PLATE_TOP + 4        # name / percentage row (8 px text)
TAG_TOP = PLATE_TOP - 6         # grade label on the tag
TAG_X = PLATE_X + 12            # tag plate left; its label starts 4 px in
HP_X, HP_W, HP_TOP, HP_ROWS = -112, 225, PLATE_TOP + 13, 9
HP_TEXT_TOP = HP_TOP + 1        # the HP number inside the gauge
LV_TOP = EMBLEM_CY - 3          # the level number on the diamond's plaque
CELL_X, CELL_PITCH, CELL_COUNT, CELL_TOP, CELL_ROWS = -109, 11, 20, PLATE_TOP + 25, 3
ICON_TOP = PLATE_TOP + PLATE_H + 1
EMBLEM_W = 80                   # emblem glyph canvas, centred on x = 0

TRAIL = hexc("c88a80")          # muted, so the white number inside the gauge stays readable
CALAMITY_HP = (hexc("4a0608"), hexc("a8141c"), hexc("ff5a4a"))
GRADES = ("elite", "rare", "unique", "calamity")
PURPLE = ("d9b8ff", "a36cf0", "6a3aa8")
GOLD = ("f2e6a0", "d0b35a", "9c7a34")
IRON = ("5e5c6a", "3a3844", "1a1820")
EMBER = ("ff6a3a", "e0302a", "8a1418")
STYLE = {  # diamond radius, rim (light, mid, dark), band, tag colour, frames (outer first), corners, body, empty well
    "elite": dict(r=11, rim=("c4c5ce", "8d8e99", "5e5f6a"), band="4a8cff", tag="4a8cff", frames=[], corners=None, body=None,
                  empty=art.BOSS_EMPTY),
    "rare": dict(r=13, rim=("c4c5ce", "8d8e99", "5e5f6a"), band="a35cff", tag="a36cf0", frames=[PURPLE], corners=None, body=None,
                 empty=art.BOSS_EMPTY),
    "unique": dict(r=14, rim=("fff2b0", "f0c040", "a8781c"), band="f0a020", tag="ffd25a", frames=[GOLD], corners="gold",
                   body=hexc("2a2418"), empty=art.BOSS_EMPTY),
    "calamity": dict(r=16, rim=("9a98a6", "66646f", "3e3c48"), band="e0302a", tag="ff5a3a", frames=[IRON, EMBER], corners="thorns",
                     body=hexc("2e2630"), empty=(58, 20, 24, 255)),
}


def _diamond(img, cx, cy, r, rim, band):
    """The level diamond at any size: black outline, a rim lit on its upper third, a coloured band, dark centre."""
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j); rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, OUT)
    for j in range(-r, r + 1):
        w = r - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, hexc(rim[0] if j < -r // 2 else rim[1] if j < 0 else rim[2]))
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j); rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, hexc(band))
    for j in range(-r + 3, r - 2):
        w = r - 3 - abs(j); rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, art.SLOT)


def emblem(grade):
    """Diamond (bigger for higher grades) + gem + number plaque. Glyph x = -EMBLEM_W // 2, top 0."""
    st = STYLE[grade]
    img = new(EMBLEM_W, EMBLEM_CY + 18)
    C, cy, r = EMBLEM_W // 2, EMBLEM_CY, st["r"]
    _diamond(img, C, cy, r, st["rim"], st["band"])
    gem(img, C, cy + r - 3, st["band"])
    step_plate(img, C - 11, cy - 5, C + 12, cy + 6, cut=1)
    rect(img, C - 9, cy - 3, C + 10, cy + 4, (12, 12, 16, 255))
    return img


def _frames(img, x0, y0, x1, y1, mats):
    """Bevelled 1 px rings just inside the plate outline: light on top / left, dark on bottom / right."""
    for k, m in enumerate(mats):
        a0, b0, a1, b1 = x0 + 1 + k, y0 + 1 + k, x1 - 1 - k, y1 - 1 - k
        rect(img, a0, b0, a1, b0 + 1, hexc(m[0])); rect(img, a0, b0, a0 + 1, b1, hexc(m[1]))
        rect(img, a0, b1 - 1, a1, b1, hexc(m[2])); rect(img, a1 - 1, b0, a1, b1, hexc(m[2]))


def _bracket(img, x, y, sx, sy):
    """特異: an L-shaped gold bracket over a plate corner, mirrored by (sx, sy)."""
    for i in range(7):
        for t, col in enumerate((GOLD[0], GOLD[1])):
            px(img, x + sx * i, y + sy * t, hexc(col)); px(img, x + sx * t, y + sy * i, hexc(col))


def _thorns(img, x, y, sx, sy):
    """厄災: three black iron thorns out of a plate corner (sideways, upward, the longest on the diagonal):
    lit upper face, dark lower face, glowing red at the root."""
    layer = new(img.width, img.height)
    d = ImageDraw.Draw(layer)
    for ux, uy, length, base in ((-1, 0, 9, 2.5), (0, -1, 8, 2.5), (-.72, -.72, 13, 3.2)):
        ux, uy = ux * sx, uy * sy
        nx, ny = -uy, ux
        tip = (round(x + ux * length), round(y + uy * length))
        d.polygon([(round(x + nx * base), round(y + ny * base)), tip, (x, y)], fill=hexc("5e5c6a"))
        d.polygon([(round(x - nx * base), round(y - ny * base)), tip, (x, y)], fill=hexc("2a2832"))
        d.line([(x, y), (round(x + ux * 2), round(y + uy * 2))], fill=hexc("e0302a"))
    a = layer.getchannel("A").load()
    for yy in range(max(0, y - 16), min(img.height, y + 17)):
        for xx in range(max(0, x - 16), min(img.width, x + 17)):
            if not a[xx, yy] and img.getpixel((xx, yy))[3] == 0 and any(
                    0 <= xx + dx < img.width and 0 <= yy + dy < img.height and a[xx + dx, yy + dy]
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px(layer, xx, yy, OUT)
    img.alpha_composite(layer)


def plate(grade):
    """Plate with its frame, corner pieces, grade tag, HP well and empty break cells.
    Glyph x = PLATE_X - PLATE_PAD, top = PLATE_TOP - PLATE_PAD."""
    st = STYLE[grade]
    P = PLATE_PAD
    img = new(PLATE_W + 2 * P, PLATE_H + 2 * P)
    x0, y0, x1, y1 = P, P, P + PLATE_W, P + PLATE_H
    step_plate(img, x0, y0, x1, y1)
    if st["body"]:
        rect(img, x0 + 3, y0 + 3, x1 - 3, y1 - 3, st["body"])
        if grade == "calamity":  # bevel so the dark plate does not read flat
            rect(img, x0 + 3, y0 + 3, x1 - 3, y0 + 4, hexc("4a404e"))
            rect(img, x0 + 3, y1 - 4, x1 - 3, y1 - 3, hexc("1e1820"))
    _frames(img, x0, y0, x1, y1, st["frames"])
    well(img, x0 + HP_X - PLATE_X, x0 + HP_X - PLATE_X + HP_W, y0 + HP_TOP - PLATE_TOP, HP_ROWS, st["empty"])
    for i in range(CELL_COUNT):
        x = x0 + CELL_X - PLATE_X + i * CELL_PITCH
        rect(img, x, y0 + CELL_TOP - PLATE_TOP, x + CELL_PITCH - 1, y0 + CELL_TOP - PLATE_TOP + CELL_ROWS, art.RAGE_EMPTY)
    corners = ((x0 - 1, y0 - 1, 1, 1), (x1, y0 - 1, -1, 1), (x0 - 1, y1, 1, -1), (x1, y1, -1, -1))
    if st["corners"] == "gold":
        for c in corners:
            _bracket(img, *c)
    elif st["corners"] == "thorns":
        for (x, y, sx, sy) in ((x0 + 1, y0 + 1, 1, 1), (x1 - 2, y0 + 1, -1, 1), (x0 + 1, y1 - 2, 1, -1), (x1 - 2, y1 - 2, -1, -1)):
            _thorns(img, x, y, sx, sy)
    tx = x0 + TAG_X - PLATE_X
    step_plate(img, tx, y0 - 8, tx + 26, y0 + 4, cut=1)
    rect(img, tx + 1, y0 - 7, tx + 25, y0 - 6, hexc(st["tag"]))
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
