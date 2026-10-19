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


def plate(grade, corners=True):
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
    at = ((x0 - 1, y0 - 1, 1, 1), (x1, y0 - 1, -1, 1), (x0 - 1, y1, 1, -1), (x1, y1, -1, -1))
    if not corners:
        pass
    elif st["corners"] == "gold":
        for c in at:
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


ORNAMENT_TOP = 0                 # ornament glyphs are drawn from the screen top
ORNAMENT_H = PLATE_TOP + PLATE_H + 6
EROSION_TOP = ICON_TOP + 2       # 黒翳竜's erosion gauge (per player), centred under the plate, only above zero
EROSION_X, EROSION_CELLS, EROSION_PITCH = -45, 10, 9
EROSION_LABEL_TOP = EROSION_TOP - 3


def _capped(pivot_y, ang_deg, want, squash=.8, top=3):
    """Longest blade at this angle whose tip stays at or below y = top."""
    rise = math.sin(math.radians(ang_deg)) * squash
    return want if rise <= 0 else min(want, (pivot_y - top) / rise)


def wyrm_back():
    """黒翳竜, behind the normal bar: its enraged fan of blade-like wing panels spreading from behind the
    diamond, out past both ends of the plate. Returns (left half, right half)."""
    C = WYRM_FRAME_HALF
    layer = new(2 * C, ORNAMENT_H)
    pivot = (C - 22, PLATE_TOP + 8)
    for k in range(9):  # outer (near level) first, so the inner ones overlap it
        ang = 176 - k * 9
        length = _capped(pivot[1], ang, 150 - 9 * k)
        _blade(layer, pivot, ang, length, 13 - k * .5, k in (6, 7), k)
    layer = _outline(_mirror(layer, C))
    for x, y in ((C - 70, 18), (C - 98, 22), (C - 52, 8), (C - 124, 26), (C - 84, 12), (C - 140, 30), (C - 40, 14)):
        for xx in (x, 2 * C - x):
            p = layer.getpixel((xx, y))
            if p[3] and p[:3] != OUT[:3]:
                px(layer, xx, y, WY["mote"])
    return _split(layer)


def _horn_pairs(img, cx, cy, r, pairs, cols):
    """Horns rooted on the diamond's upper edges, sweeping up and out, mirrored. pairs: (dx, length, lean)."""
    layer = new(img.width, img.height)
    for dx, length, lean in pairs:
        for side in (-1, 1):
            x0, y0 = cx + side * dx, cy - (r - dx) + 1
            for i in range(length):
                x = x0 + side * (i * i) // (lean * 4); y = y0 - i
                px(layer, x, y, hexc(cols[0]) if i > length - 4 else hexc(cols[1]))
                if i < length - 3:
                    px(layer, x + side, y, hexc(cols[2]))
    img.alpha_composite(_outline(layer))


def wyrm_front():
    """Pink horns growing out of the diamond (over the emblem). Glyph x = -EMBLEM_W // 2, top 0."""
    img = new(EMBLEM_W, EMBLEM_CY + 18)
    r = STYLE["calamity"]["r"]
    _horn_pairs(img, EMBLEM_W // 2, EMBLEM_CY, r, ((5, 10, 3), (9, 8, 4), (12, 6, 5)), ("ffb0e8", "e070c0", "a04080"))
    return img


def _split(full):
    C = WYRM_FRAME_HALF
    return full.crop((0, 0, C, full.height)), full.crop((C, 0, 2 * C, full.height))


def erosion_well():
    """Ten empty cells for the erosion gauge, on a dark violet strip."""
    img = new(EROSION_CELLS * EROSION_PITCH + 3, 5)
    rect(img, 0, 0, img.width, 5, OUT)
    rect(img, 1, 1, img.width - 1, 4, WY["plate"][1])
    for i in range(EROSION_CELLS):
        rect(img, 2 + i * EROSION_PITCH, 1, 2 + i * EROSION_PITCH + EROSION_PITCH - 1, 4, (40, 26, 56, 255))
    return img


def erosion_cell():
    img = new(EROSION_PITCH - 1, 3)
    rect(img, 0, 0, img.width, 3, hexc("b070ff")); rect(img, 0, 0, img.width, 1, hexc("e0c8ff"))
    rect(img, 0, 2, img.width, 3, hexc("6a2ab0"))
    return img


# ---- sakura beast (桜牙獣) ---------------------------------------------------------------------------
SK = dict(s0=hexc("1e1c26"), s1=hexc("2c2a38"), s2=hexc("3c3a4c"), s3=hexc("56546a"),
          fur0=hexc("8a8292"), fur1=hexc("c8c0cc"), fur2=hexc("f0ecf0"), horn=hexc("b8b0b0"), horn_hi=hexc("e8e2dc"),
          p0=hexc("c8507a"), p1=hexc("f08aa8"), p2=hexc("ffc0d4"), eye=hexc("ff5a9a"),
          plate=(hexc("161420"), hexc("26242e"), hexc("34323e"), hexc("56546a")))
SAKURA_HP = (hexc("8a1a3a"), hexc("e0507a"), hexc("ffb0c8"))
SAKURA_EMPTY = (36, 12, 20, 255)
SAKURA_TRAIL = hexc("d898aa")


def _blossom(layer, cx, cy, r):
    """Five round petals with a notch at each tip, pale toward the centre, a dark-pink heart."""
    m = Image.new("L", layer.size); d = ImageDraw.Draw(m)
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        pcx, pcy, pr = cx + math.cos(a) * r * .62, cy + math.sin(a) * r * .62, r * .56
        d.ellipse([pcx - pr, pcy - pr, pcx + pr, pcy + pr], fill=255)
    for k in range(5):
        a = -math.pi / 2 + k * 2 * math.pi / 5
        tx, ty = cx + math.cos(a) * r * 1.2, cy + math.sin(a) * r * 1.2
        d.polygon([(tx + math.cos(a + 2.4) * 1.6, ty + math.sin(a + 2.4) * 1.6), (cx + math.cos(a) * r * .8, cy + math.sin(a) * r * .8),
                   (tx + math.cos(a - 2.4) * 1.6, ty + math.sin(a - 2.4) * 1.6)], fill=0)
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            if 0 <= x < layer.width and 0 <= y < layer.height and m.getpixel((x, y)):
                dd = math.hypot(x - cx, y - cy)
                px(layer, x, y, SK["p2"] if dd < r * .45 else SK["p1"] if y < cy + r * .3 else SK["p0"])
    px(layer, cx, cy, SK["p0"])


def _sakura_head(img, cx, top):
    """Wolf head from the front: dark slate muzzle, pink eyes, pale horns sweeping up and back, ears."""
    head = new(img.width, img.height)
    rows = [(7, 0), (8, 1), (8, 2), (8, 3), (7, 4), (6, 5), (5, 6), (4, 7), (4, 8), (3, 9), (3, 10), (2, 11)]
    for half, j in rows:
        rect(head, cx - half, top + j, cx + half + 1, top + j + 1, SK["s2"] if j < 3 else SK["s1"] if j < 8 else SK["s3"])
    rect(head, cx - 2, top + 9, cx + 3, top + 12, SK["fur1"])
    for side in (-1, 1):
        px(head, cx + side * 4, top + 4, SK["eye"]); px(head, cx + side * 5, top + 4, SK["eye"])
        for i in range(16):
            x = cx + side * (5 + (i * i) // 40); y = top + 1 - i
            px(head, x, y, SK["horn_hi"] if i > 9 else SK["horn"]); px(head, x + side, y, SK["horn"] if i < 11 else SK["horn_hi"])
        for i in range(4):
            px(head, cx + side * (8 + i // 2), top - i, SK["s2"])
    img.alpha_composite(_outline(head))


def sakura_back():
    """桜牙獣, behind the normal bar: a white mane fanning up behind the diamond, and from each end of the
    plate a dark tail curling up with sakura blossoms along it. Returns (left half, right half)."""
    C = WYRM_FRAME_HALF
    layer = new(2 * C, ORNAMENT_H)
    y_mid = PLATE_TOP + 16
    p0, p1, p2, p3 = (C - 116, y_mid), (C - 150, y_mid + 2), (C - 160, y_mid - 22), (C - 142, y_mid - 34)
    pts = []
    for i in range(60):
        u = i / 59
        pts.append(tuple((1 - u) ** 3 * a + 3 * (1 - u) ** 2 * u * b + 3 * (1 - u) * u * u * c + u ** 3 * d
                         for a, b, c, d in zip(p0, p1, p2, p3)))
    for i, (x, y) in enumerate(pts):
        th = 6 if i < 20 else 5 if i < 40 else 4
        for t in range(th):
            px(layer, round(x) - t // 2, round(y) + t - th // 2, SK["s3"] if t == 0 else SK["s2"] if t < th - 1 else SK["s1"])
    for i, r in ((26, 4), (44, 5), (59, 6)):
        x, y = pts[i]
        _blossom(layer, round(x) - 3, max(r + 1, round(y) - 2), r)
    bx, by = C - 4, EMBLEM_CY + 4
    d = ImageDraw.Draw(layer)
    for k, ang in enumerate((118, 134, 150, 166, 180)):
        a_ = math.radians(ang)
        length = _capped(by, ang, (30, 34, 34, 30, 24)[k], squash=.85, top=2)
        w = (3.6, 3.8, 3.6, 3.2, 2.8)[k]
        tip = (bx + math.cos(a_) * length, by - math.sin(a_) * length * .85)
        n = (-math.sin(a_) * w, -math.cos(a_) * w)
        bend = (bx + math.cos(a_ + .25) * length * .6, by - math.sin(a_ + .25) * length * .55)
        _poly(layer, [(bx + n[0], by + n[1]), bend, tip, (bx - n[0], by - n[1])], SK["fur2"] if k % 2 == 0 else SK["fur1"])
        d.line([(bx, by), (round(tip[0]), round(tip[1]))], fill=SK["fur0"], width=1)
    return _split(_outline(_mirror(layer, C)))


def sakura_front():
    """Pale horns out of the diamond's upper edges (over the emblem)."""
    img = new(EMBLEM_W, EMBLEM_CY + 18)
    r = STYLE["calamity"]["r"]
    _horn_pairs(img, EMBLEM_W // 2, EMBLEM_CY, r, ((6, 12, 3),), ("e8e2dc", "b8b0b0", "8a8292"))
    return img


ICONS = mob_art.ICONS
icon = mob_art.icon
