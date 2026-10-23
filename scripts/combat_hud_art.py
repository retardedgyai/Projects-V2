"""Artwork for the combat HUD approved on 2026-10-16 (direction mocks F1 + L3, class decorations N).

Everything is drawn at its final size on the Minecraft GUI pixel grid; the 32 px skill art is reduced
to the 16 px hotbar slot so every pixel on screen is the same size.
Coordinates: x relative to the screen centre, `top` = distance from the screen bottom (action bar)
or from the screen top (boss bar). See build_combat_hud.py for how each piece becomes a glyph.

Reference shots and the creator's verdicts for this direction are listed in
assets/core-ui/proposals/2026-10-16-combat-hud/README.md.
"""
import math

from PIL import Image


def hexc(value, alpha=255):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


OUT = hexc("0e0e11")
PLATE = hexc("2c2c32")
PLATE_HI = hexc("4c4c56")
PLATE_LO = hexc("1b1b20")
SLOT = hexc("17171b")
SLOT_HI = hexc("3c3c45")
INSET = hexc("0f1012")
INSET_HI = (60, 60, 64, 255)
METAL = (hexc("c4c5ce"), hexc("8d8e99"), hexc("5e5f6a"), hexc("3a3b43"))
IRON = (hexc("f0f0f0"), hexc("c6c6c6"), hexc("8c8c8c"))

# gauge ramps: dark, base, light; empty well colour
HP = (hexc("6c1020"), hexc("c8283a"), hexc("ff6a68")); HP_EMPTY = (34, 10, 14, 255)
MP = (hexc("143478"), hexc("2c6fe0"), hexc("78bcff")); MP_EMPTY = (10, 16, 36, 255)
BOSS = (hexc("7a1212"), hexc("d23a1e"), hexc("ff9a50")); BOSS_EMPTY = (36, 10, 8, 255)
RAGE = (hexc("8a3a06"), hexc("f08a1a"), hexc("ffd25a")); RAGE_EMPTY = (44, 30, 20, 255)
TRAIL = hexc("f6e2d2")
BARRIER = (hexc("e3a91c"), hexc("ffe066"))
BUFF_LINE = hexc("4fd06e")

CLASSES = ("warrior", "ranger", "mage", "assassin", "templar", "healer")
ACCENT = {"warrior": "e3283e", "ranger": "5ec64a", "mage": "ff8a1c", "assassin": "8a4cff",
          "templar": "f4c84e", "healer": "9cf07c"}
XP_STEPS = 24

# ---- geometry (GUI px) -------------------------------------------------------------------------
HOTBAR_X = -91                       # vanilla hotbar sprite, 182x22, top 22
CONSOLE_X, CONSOLE_TOP, CONSOLE_W, CONSOLE_H = -92, 40, 184, 19
HP_X, HP_W = -78, 54                 # gauge fill rows: top 38, 9 rows
MP_X, MP_W = 25, 54                  # mirrors HP about the centre column
GAUGE_TOP, GAUGE_ROWS = 38, 9
CELL_X, CELL_PITCH, CELL_COUNT, CELL_TOP = -89, 9, 20, 25
EMBLEM_CY = 34                       # diamond centre, from the bottom
DIAMOND_R = 13
SLOT_ICON_X = -88                    # slot i icon left = -88 + 20 i, top 19, 16x16
SLOT_ICON_TOP = 19
BUFF_TOP, BUFF_LEFT = 55, -92
BREAK = (hexc("8a6a10"), hexc("e8c23a"), hexc("fff2a0")); BREAK_EMPTY = (30, 26, 12, 255)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def px(img, x, y, col):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), col)


def rect(img, x0, y0, x1, y1, col):
    for y in range(max(0, y0), min(img.height, y1)):
        for x in range(max(0, x0), min(img.width, x1)):
            img.putpixel((x, y), col)


def step_plate(img, x0, y0, x1, y1, cut=2):
    """Charcoal plate, staircase-cut corners, 1 px outline, light top / dark bottom bevel."""
    for j in range(y0, y1):
        k = min(j - y0, y1 - 1 - j)
        d = cut - k if k < cut else 0
        rect(img, x0 + d, j, x1 - d, j + 1, OUT)
        if k > 0:
            dd = max(0, cut - k) + 1
            col = PLATE_HI if (k == 1 and j - y0 == 1) else PLATE_LO if k == 1 else PLATE
            rect(img, x0 + dd, j, x1 - dd, j + 1, col)


def slot(img, x, y, size=18):
    rect(img, x, y, x + size, y + size, SLOT)
    rect(img, x, y, x + size, y + 1, OUT)
    rect(img, x, y, x + 1, y + size, OUT)
    rect(img, x, y + size - 1, x + size, y + size, SLOT_HI)
    rect(img, x + size - 1, y, x + size, y + size, SLOT_HI)


def well(img, x0, x1, y, rows, empty):
    """Sunken gauge well: inset rim around the empty rows (the fill glyphs cover it from x0)."""
    rect(img, x0 - 1, y - 1, x1 + 1, y + rows + 1, INSET)
    rect(img, x0 - 1, y - 1, x1 + 1, y, (0, 0, 0, 255))
    rect(img, x0 - 1, y - 1, x0, y + rows + 1, (0, 0, 0, 255))
    rect(img, x0 - 1, y + rows, x1 + 1, y + rows + 1, INSET_HI)
    rect(img, x1, y - 1, x1 + 1, y + rows + 1, INSET_HI)
    rect(img, x0, y, x1, y + rows, empty)


def gauge_fill(width, rows, ramp):
    dark, base, light = ramp
    img = new(width, rows)
    rect(img, 0, 0, width, rows, base)
    rect(img, 0, 0, width, 1, light)
    rect(img, 0, rows - 1, width, rows, dark)
    return img


def flat(width, rows, col, top=None):
    img = new(width, rows)
    rect(img, 0, 0, width, rows, col)
    if top:
        rect(img, 0, 0, width, 1, top)
    return img


HEART = [".xx...xx.", "xrrx.xrrx", "xrHrxrrrx", "xrHrrrrrx", "xrrrrrrdx", ".xrrrrdx.", "..xrrdx..", "...xdx...", "....x...."]
MANA = ["....x....", "...xbx...", "..xbHbx..", ".xbHbbbx.", "xbbbbbbdx", ".xbbbbdx.", "..xbbdx..", "...xdx...", "....x...."]
ICON_COL = {"x": OUT, "r": hexc("d8283a"), "H": hexc("ff9c9c"), "d": hexc("8a1020"), "b": hexc("3a8cf0")}


def icon(img, rows, x0, y0, colours=None):
    colours = colours or ICON_COL
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                col = colours[ch]
                if rows is MANA and ch == "H":
                    col = hexc("cfeaff")
                elif rows is MANA and ch == "d":
                    col = hexc("1a4aa8")
                px(img, x0 + i, y0 + j, col)


def console():
    """HP plate | MP plate over the class-resource strip. Origin (CONSOLE_X, CONSOLE_TOP)."""
    img = new(CONSOLE_W, CONSOLE_H)
    ox = -CONSOLE_X                       # image x of the screen centre
    step_plate(img, 0, 0, ox - 1, 13)
    step_plate(img, ox + 1, 0, CONSOLE_W, 13)
    step_plate(img, 0, 13, CONSOLE_W, 19)
    well(img, ox + HP_X, ox + HP_X + HP_W, 2, GAUGE_ROWS, HP_EMPTY)
    well(img, ox + MP_X, ox + MP_X + MP_W, 2, GAUGE_ROWS, MP_EMPTY)
    icon(img, HEART, ox - 89, 2)
    icon(img, MANA, ox + 81, 2)
    for i in range(CELL_COUNT):
        x = ox + CELL_X + i * CELL_PITCH
        rect(img, x, 15, x + CELL_PITCH - 1, 17, RAGE_EMPTY)
    return img


def rage_cell():
    img = new(CELL_PITCH - 1, 2)
    rect(img, 0, 0, img.width, 2, RAGE[1])
    rect(img, 0, 0, img.width, 1, RAGE[2])
    return img


# ---- the warrior's beast (Creator 2026-10-24, 案A): three diamond sockets over the emblem's crest ---------
BEAST_R = 4                        # diamond radius; a socket is 2R+3 px square with its rim
BEAST_PITCH = 14                   # centre to centre
BEAST_TOP = 62                     # top of the socket row, GUI px from the bottom: the middle one is set in the crest
BEAST_FILL_STEPS = 4               # a part-built stack fills from the bottom in this many steps
BEAST_RED = [hexc("460810"), hexc("800e14"), hexc("c41c20"), hexc("f04638"), hexc("ffaa8c")]
BEAST_RIM, BEAST_EMPTY = (14, 10, 12, 255), ((44, 30, 34, 255), (30, 20, 24, 255))


def beast_socket(step, burning=False):
    """One socket filled `step` of BEAST_FILL_STEPS from the bottom (0 empty); `burning`: all three full,
    a dark red halo round it. Hand-shaded: lit top-left, dark bottom-right, a glint on a full one."""
    r = BEAST_R
    size = 2 * r + 5
    img = new(size, size)
    c = size // 2
    fill = step / BEAST_FILL_STEPS
    for dy in range(-r - 2, r + 3):
        for dx in range(-r - 2, r + 3):
            k = abs(dx) + abs(dy)
            x, y = c + dx, c + dy
            if k == r + 2:
                if burning: px(img, x, y, (110, 16, 18, 255))
            elif k == r + 1:
                px(img, x, y, BEAST_RIM)
            elif k <= r:
                level = (r - dy) / (2 * r)
                if level <= fill + 1e-9 and step > 0:
                    t = 2 + (1 if dx < 0 and dy < 0 else 0) - (1 if dx > 0 and dy > 0 else 0)
                    if k == r: t = max(1, t - 1)
                    if step == BEAST_FILL_STEPS and dx == -1 and dy == -2: t = 4
                    px(img, x, y, BEAST_RED[t])
                else:
                    px(img, x, y, BEAST_EMPTY[(dx + dy) % 2])
    return img


def xp_cell():
    """The resource strip as the XP bar (the warrior's beast moved to its sockets): the level emblem's gold."""
    img = new(CELL_PITCH - 1, 2)
    rect(img, 0, 0, img.width, 2, hexc("a8781c"))
    rect(img, 0, 0, img.width, 1, hexc("f0c040"))
    return img


def hp_tick():
    return flat(1, GAUGE_ROWS, (0, 0, 0, 150))


# ---- level emblem: wings, diamond, crown, number plaque (per class) ------------------------------
MAT = {  # light, mid, dark
    "steel": ("c4c5ce", "8d8e99", "5e5f6a"), "wood": ("c8945a", "8a5a2e", "5a3618"),
    "leaf": ("9ce86a", "4cb040", "2a6e2a"), "ice": ("e0f6ff", "7ac8f0", "3a7ac0"),
    "bronze": ("f0b060", "b07a2e", "6e4818"), "fire": ("fff0a0", "ff9a2a", "c8401a"),
    "iron": ("7a7888", "46444f", "26242c"), "violet": ("d0a0ff", "9a5af0", "54289c"),
    "gold": ("fff2b0", "f0c040", "a8781c"), "white": ("ffffff", "eee4c8", "c4b080"),
}


def wing_pixels(job, C, cy):
    f = {}
    root = C - 10

    def put(i, y, m, t):
        f[(root - i, y)] = (m, t)

    if job in ("warrior", "templar"):
        for dy, length in ((-3, 12), (0, 10), (3, 8)):
            for i in range(length):
                y = cy + dy - (i // 3 if dy < 0 else 0)
                put(i, y, "gold" if job == "templar" else "steel", 0 if dy < 0 or job == "templar" else 1)
                if i < length - 2:
                    put(i, y + 1, "steel", 2 if job == "warrior" else 1)
                if job == "templar" and i < length - 4:
                    put(i, y + 2, "steel", 2)
    elif job == "ranger":
        for i in range(13):
            put(i, cy + (i // 6), "wood", 1)
            put(i, cy + 1 + (i // 6), "wood", 2)
        leaf = [(1, 0, 1), (2, 0, 1), (3, 0, 1), (4, 0, 1), (2, -1, 0), (3, -1, 0), (4, -1, 0), (5, -1, 0),
                (4, -2, 0), (5, -2, 0), (6, -2, 0), (0, 1, 1), (1, 1, 2), (2, 1, 2)]
        for i0, up in ((0, True), (4, False), (8, True)):
            base = cy - 1 if up else cy + 2 + (i0 // 6)
            for dx, dy, tone in leaf:
                put(i0 + dx, base + (dy if up else -dy), "leaf", tone if up else 2 - tone if tone else 1)
    elif job == "mage":
        for dy, length, rise in ((-4, 11, 2), (0, 9, 0), (4, 6, -1)):
            for i in range(length):
                th = 3 if i < length * .45 else 2 if i < length * .8 else 1
                top = cy + dy - (rise * i) // max(1, length - 1)
                for t in range(th):
                    put(i, top + t, "ice", 0 if t == 0 else 1 if t == 1 else 2)
    elif job == "assassin":
        for dy, length in ((-1, 13), (3, 9)):
            for i in range(length):
                y = cy + dy - (i * i) // 22
                put(i, y, "violet" if i > length - 4 else "iron", 0)
                if i < length - 3:
                    put(i, y + 1, "iron", 2)
    elif job == "healer":
        for k, (dy, length, droop) in enumerate(((-4, 11, -1), (-1, 12, 0), (2, 10, 1), (5, 7, 2))):
            for i in range(length):
                y = cy + dy + (droop * i) // 6
                put(i, y, "white", 0)
                if i < length - 1:
                    put(i, y + 1, "white", 2 if k % 2 else 1)
    mirrored = dict(f)
    for (x, y), v in f.items():
        mirrored[(2 * C - x, y)] = v
    return mirrored


def crown_pixels(job, C, cy):
    """Crown on the diamond's top point. put() draws every pixel mirrored about the centre column,
    so each crown is left-right symmetric in shape and shade, like the wings."""
    tip = cy - 14
    f = {}

    def put(x, y, m, t):
        f[(x, y)] = (m, t)
        f[(2 * C - x, y)] = (m, t)

    gem = None
    if job == "warrior":                           # three sword points on an iron band, gem at the middle root
        for k in range(3):
            put(C - 5, tip - 1 - k, "steel", 0 if k == 2 else 1)
        for k in range(6):
            put(C, tip - 1 - k, "steel", 0 if k >= 4 else 1)
            if k < 4:
                put(C - 1, tip - 1 - k, "steel", 2)
        for x in range(C - 6, C + 1):
            put(x, tip, "steel", 1)
        gem = (C, tip - 1)
    elif job == "ranger":                          # a forked twig with buds
        for k in range(5):
            put(C, tip - k, "wood", 1)
            put(C - 1 - k // 2, tip - 1 - k, "wood", 1)
        put(C - 3, tip - 6, "leaf", 0); put(C, tip - 5, "leaf", 0)
    elif job == "mage":                            # a bronze cradle holding a fire gem
        for x in range(C - 4, C + 1):
            put(x, tip, "bronze", 1)
        for x, y in ((C - 4, tip - 1), (C - 4, tip - 2), (C - 3, tip - 3)):
            put(x, y, "bronze", 0)
        for x, y, t in ((C, tip - 1, 2), (C - 1, tip - 2, 1), (C, tip - 2, 1), (C - 1, tip - 3, 1),
                        (C, tip - 3, 0), (C, tip - 4, 0), (C, tip - 5, 0)):
            put(x, y, "fire", t)
    elif job == "assassin":                        # two hooked horns
        for k, (dx, dy) in enumerate(((2, 0), (3, -1), (4, -2), (4, -3), (3, -4), (2, -5))):
            put(C - dx, tip + dy, "iron", 0 if k < 3 else 1)
        gem = (C, tip - 1)
    elif job == "templar":                         # a gold cross, three pixels wide
        for y in range(tip - 6, tip + 1):
            put(C, y, "gold", 0); put(C - 1, y, "gold", 2)
        for x in range(C - 4, C - 1):
            put(x, tip - 4, "gold", 1)
    elif job == "healer":                          # a halo ring with a leaf
        for x in range(C - 5, C + 1):
            put(x, tip - 6, "gold", 0)
            put(x, tip - 2, "gold", 1)
        for y in (tip - 5, tip - 4, tip - 3):
            put(C - 6, y, "gold", 1)
        put(C, tip - 1, "leaf", 0); put(C - 1, tip - 1, "leaf", 1)
    return f, gem


def paint(img, fill):
    edge = {(x + a, y + b) for (x, y) in fill for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))} - set(fill)
    for x, y in edge:
        px(img, x, y, OUT)
    for (x, y), (mat, tone) in fill.items():
        px(img, x, y, hexc(MAT[mat][tone]))


def gem(img, cx, cy, accent, r=1):
    dark, base, light = (0, 0, 0, 255), hexc(accent), (255, 255, 255, 255)
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, OUT)
    for j in range(-r, r + 1):
        w = r - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, base if j < r else dark)
    px(img, cx - 1 if r > 1 else cx, cy - r + 1 if r > 1 else cy - 1, light)


def diamond(img, cx, cy, accent, r=DIAMOND_R):
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, OUT)
    for j in range(-r, r + 1):
        w = r - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, METAL[1] if j < 0 else METAL[2])
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, hexc(accent))
    for j in range(-r + 3, r - 2):
        w = r - 3 - abs(j)
        rect(img, cx - w, cy + j, cx + w + 1, cy + j + 1, SLOT)


def xp_band(cx, cy, r=DIAMOND_R):
    """The 2 px class-colour band, ordered clockwise from the top, minus what the plaque covers."""
    cells = []
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        for x in range(cx - w, cx + w + 1):
            if abs(x - cx) + abs(j) > r - 4 and not (abs(j) <= 5 and abs(x - cx) <= 11):
                ang = (math.degrees(math.atan2(x - cx, -j)) + 360) % 360
                cells.append((ang, x, cy + j))
    return sorted(cells)


EMBLEM_W, EMBLEM_H = 56, 38          # drawing canvas; the centre column is EMBLEM_W // 2
EMBLEM_CENTRE_ROW = 23               # canvas row of the diamond centre


def even(img):
    """The emblem is drawn mirrored about one centre column (odd width), but the hotbar and the plates are
    mirrored about the line between two columns (even width): doubling the centre column puts the emblem's
    axis on the screen centre line instead of half a GUI pixel right of it (Creator 2026-10-23)."""
    C = img.width // 2
    out = new(2 * (C + 1), img.height)
    out.paste(img.crop((0, 0, C + 1, img.height)), (0, 0))
    out.paste(img.crop((C, 0, img.width, img.height)), (C + 1, 0))
    return out


def emblem(job):
    """Wings + diamond + crown + lower gem + number plaque. Returns (image, left x, top from bottom)."""
    img = new(EMBLEM_W, EMBLEM_H)
    C, cy = EMBLEM_W // 2, EMBLEM_CENTRE_ROW
    accent = ACCENT[job]
    paint(img, wing_pixels(job, C, cy))
    diamond(img, C, cy, accent)
    for ang, x, y in xp_band(C, cy):  # unlit band
        rgb = hexc(accent)
        px(img, x, y, (rgb[0] // 3, rgb[1] // 3, rgb[2] // 3, 255))
    crown, crown_gem = crown_pixels(job, C, cy)
    paint(img, crown)
    if crown_gem:
        gem(img, *crown_gem, accent)
    gem(img, C, cy + 10, accent)
    step_plate(img, C - 11, cy - 5, C + 12, cy + 6, cut=1)
    rect(img, C - 9, cy - 3, C + 10, cy + 4, (12, 12, 16, 255))
    for x in (C - 10, C + 10):
        px(img, x, cy, METAL[0])
    return even(img), -(C + 1), EMBLEM_CY + EMBLEM_CENTRE_ROW


def xp_overlay(job, step):
    """Lit part of the XP band for `step` of XP_STEPS (same canvas/origin as emblem())."""
    img = new(EMBLEM_W, EMBLEM_H)
    C, cy = EMBLEM_W // 2, EMBLEM_CENTRE_ROW
    cells = xp_band(C, cy)
    for ang, x, y in cells:
        if ang < 360 * step / XP_STEPS:
            px(img, x, y, hexc(ACCENT[job]))
    if img.getchannel("A").getbbox() is None:
        px(img, C, cy - DIAMOND_R + 2, hexc(ACCENT[job]))  # step 0 never used; keep the glyph drawable
    return even(img)


# ---- hotbar (vanilla sprite overrides) -----------------------------------------------------------
def hotbar_sprite():
    img = new(182, 22)
    step_plate(img, 0, 0, 182, 22)
    for i in range(9):
        slot(img, 2 + 20 * i, 2)
    return img


def hotbar_selection_sprite():
    img = new(24, 23)
    rect(img, 2, 2, 22, 3, IRON[0]); rect(img, 2, 2, 3, 22, IRON[0])
    rect(img, 2, 21, 22, 22, IRON[2]); rect(img, 21, 2, 22, 22, IRON[2])
    return img


# ---- skill icons in hotbar slots (16 px, one texel per GUI pixel like everything else) -----------
SKILL_STATES = ("ready", "cooldown", "mana", "locked")
LOCK = ["..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"]


def skill_icon(art, state):
    """The 32 px skill art reduced to the 16 px slot by area averaging (keeps colour and shape;
    in-game the 32 px art showed half-size pixels next to the rest of the HUD)."""
    src = art.convert("RGBA")
    img = src.resize((16, 16), Image.BOX)
    img.putalpha(img.getchannel("A").point(lambda v: 255 if v >= 100 else 0))
    pix = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = pix[x, y]
            if a == 0:
                continue
            if state == "cooldown":
                r, g, b = r * 35 // 100, g * 35 // 100, b * 35 // 100
            elif state == "mana":
                lum = (r + g + b) // 3
                r, g, b = lum * 66 // 255, lum * 99 // 255, min(255, lum * 153 // 255 + 20)
            elif state == "locked":
                lum = (r + g + b) // 3
                r, g, b = lum * 16 // 100, lum * 16 // 100, lum * 19 // 100
            pix[x, y] = (r, g, b, 255)
    if state == "locked":  # a small, quiet lock: it must not outshine the usable skills
        for j, row in enumerate(LOCK):
            for i, ch in enumerate(row):
                if ch == "#":
                    px(img, 4 + i, 5 + j, hexc("8c8270") if j < 3 else hexc("6e6656"))
    return img


# ---- small gauge digits (3x5, shadow baked in) ---------------------------------------------------
DIGITS = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "/": ["001", "001", "010", "100", "100"], "s": ["011", "100", "010", "001", "110"],
}
DIGIT_PITCH = 4


def gauge_digit(ch):
    """White 3x5 digit with a dark one-pixel drop shadow (the shadow lands in the 1 px gap)."""
    rows = DIGITS[ch]
    img = new(4, 6)
    for j, row in enumerate(rows):
        for i, v in enumerate(row):
            if v == "1":
                px(img, i + 1, j + 1, (0, 0, 0, 110))
    for j, row in enumerate(rows):
        for i, v in enumerate(row):
            if v == "1":
                px(img, i, j, (255, 255, 255, 255))
    return img


# ---- buffs ---------------------------------------------------------------------------------------
BUFF_H = 13


def buff_left():
    """Left end of a buff tile: plate + icon well (icon glyph sits at +2,+2, 10x10)."""
    img = new(14, BUFF_H)
    step_plate(img, 0, 0, 30, BUFF_H)  # drawn wider, cropped: the right side continues in buff_mid
    rect(img, 2, 11, 12, 12, BUFF_LINE)
    return img.crop((0, 0, 14, BUFF_H))


def buff_mid(width):
    img = new(width + 4, BUFF_H)
    step_plate(img, -4, 0, width + 8, BUFF_H)
    return img.crop((0, 0, width, BUFF_H))


def buff_right():
    img = new(12, BUFF_H)
    step_plate(img, -9, 0, 3, BUFF_H)
    return img.crop((0, 0, 3, BUFF_H))


BUFF_ART = {
    "whetstone": (["......##", ".....###", "....###.", "...###..", "#.###...", "####....", ".##.....", "#.#....."],
                  ("f6ecd6", "c4baaa")),
    "potion": (["..####..", "...##...", "...##...", "..####..", ".######.", ".######.", ".######.", "..####.."],
               ("ece6de", "f05056")),
    "dodge": (["........", "#...#...", ".#...#..", "..#...#.", "..#...#.", ".#...#..", "#...#...", "........"],
              ("c8fff6", "6ed6c8")),
}
BUFF_STEPS = 12


def buff_icon(kind, frame):
    rows, (top, low) = BUFF_ART[kind]
    img = new(10, 10)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                px(img, i + 1, j + 1, hexc(top) if j < 4 else hexc(low))
    if frame:
        cut = 1 + frame * 8 // BUFF_STEPS + (1 if frame * 8 % BUFF_STEPS else 0)
        for y in range(1, cut):
            for x in range(10):
                r, g, b, a = img.getpixel((x, y))
                if a:
                    img.putpixel((x, y), (r * 30 // 100, g * 30 // 100, b * 34 // 100, a))
    return img
