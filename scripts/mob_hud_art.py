"""Artwork for the mob HUD (direction mock approved 2026-10-19; grades 一般・精鋭・希少・特異・厄災).

Overhead plate: a text display riding the mob, one line per row (effects / Lv + name / gauge / MODs).
Every piece here sits on a 9 px text line; x is relative to the line centre. The gauge frame grows
with the grade: plain rim -> iron -> iron + purple gems -> gold + amber gems -> gold + gems + spikes.
The boss version of the same information lives on the boss bar plate (combat_hud_art.boss_plate).
"""
import combat_hud_art as art
from combat_hud_art import OUT, hexc, new, px, rect, gem

PLATE_FILL = 61                 # HP / break gauge width (odd: symmetric about column 0)
PLATE_LEFT = -(PLATE_FILL // 2)  # inner gauge left edge
FRAME_PAD = 10                  # frame glyph starts this far left of the gauge (room for gems / spikes)
HP_ROWS, BREAK_ROWS = 4, 2      # inside the frame: HP rows, one black row, break rows
HP_TOP = 1                      # within the 9 px line
BREAK_TOP = HP_TOP + HP_ROWS + 1

HP = (hexc("6c1020"), hexc("c8283a"), hexc("ff6a68")); HP_EMPTY = (34, 10, 14, 255)
BREAK, BREAK_EMPTY = art.BREAK, art.BREAK_EMPTY
BROKEN = (hexc("b8b8c0"), hexc("ffffff"), hexc("ffffff"))
IRON = (hexc("2a2b31"), hexc("5e5f6a"), hexc("8d8e99"), hexc("c4c5ce"))
GOLD = (hexc("5a3a18"), hexc("9c7a34"), hexc("d0b35a"), hexc("f2e6a0"))

# grade key -> (frame material, gem colour or None, spikes)
GRADES = {
    "common": (None, None, False),
    "elite": (IRON, None, False),
    "rare": (IRON, "a35cff", False),
    "unique": (GOLD, "f0a020", False),
    "calamity": (GOLD, "ff5a1e", True),
}


def _ring(img, x0, y0, x1, y1, mat):
    """1 px bevelled metal ring round [x0, x1) x [y0, y1): light top-left, dark bottom-right."""
    dark, mid, light, shine = mat
    rect(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, mid)
    rect(img, x0 - 1, y0 - 1, x1 + 1, y0, shine)
    rect(img, x0 - 1, y0 - 1, x0, y1 + 1, light)
    rect(img, x0 - 1, y1, x1 + 1, y1 + 1, dark)
    rect(img, x1, y0, x1 + 1, y1 + 1, dark)


def _spike(img, tip_x, cy, direction, mat):
    """Small gold point beyond a side gem, pointing outward (direction -1 = left)."""
    for i in range(3):
        x = tip_x - direction * i
        for dy in range(-i, i + 1):
            px(img, x, cy + dy, OUT)
    for i in range(1, 3):
        x = tip_x - direction * i
        for dy in range(-i + 1, i):
            px(img, x, cy + dy, mat[2] if dy < 0 else mat[1])


def plate_frame(grade):
    """The whole gauge frame for one grade, empty wells included. Glyph x = PLATE_LEFT - FRAME_PAD, top 0."""
    mat, gem_colour, spikes = GRADES[grade]
    breaks = grade != "common"
    inner = HP_ROWS + (1 + BREAK_ROWS if breaks else 0)
    img = new(PLATE_FILL + 2 * FRAME_PAD, 9)
    x0, x1 = FRAME_PAD, FRAME_PAD + PLATE_FILL
    y0, y1 = HP_TOP, HP_TOP + inner
    if mat:
        _ring(img, x0, y0, x1, y1, mat)
    else:
        rect(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, OUT)
    rect(img, x0, y0, x1, y0 + HP_ROWS, HP_EMPTY)
    if breaks:
        rect(img, x0, y0 + HP_ROWS, x1, y0 + HP_ROWS + 1, OUT)
        rect(img, x0, BREAK_TOP, x1, BREAK_TOP + BREAK_ROWS, BREAK_EMPTY)
    if gem_colour:
        cy = (y0 + y1 - 1) // 2
        gem(img, x0 - 4, cy, gem_colour, 2)
        gem(img, x1 + 3, cy, gem_colour, 2)
        if spikes:
            _spike(img, x0 - 9, cy, -1, mat)
            _spike(img, x1 + 8, cy, 1, mat)
    return img


# ---- effect icons (buff / debuff / crowd control): 9x9, coloured edge, dark well, 5x5 symbol ----------
KIND = {"buff": (hexc("4fd06e"), hexc("c8ffd4")), "debuff": (hexc("d8283a"), hexc("ffb0a8")),
        "cc": (hexc("e8c23a"), hexc("fff2a0"))}
ICONS = {  # name -> (kind, 5x5 symbol); names match MobHudEffect.icon
    "rage": ("buff", ["#.#.#", "#####", "#####", ".###.", "..#.."]),
    "slow": ("debuff", ["..#..", "..#..", "#.#.#", ".###.", "..#.."]),
    "exposed": ("debuff", ["##.##", "#..##", "#.#.#", ".#.#.", "..#.."]),
    "taunt": ("debuff", ["..#..", "..#..", "..#..", ".....", "..#.."]),
    "broken": ("cc", ["##.##", "#.#.#", ".#.#.", "#.#.#", "##.##"]),   # a shattered guard: the break
    "stun": ("cc", ["..#..", "#####", ".###.", ".#.#.", "#...#"]),
    "airborne": ("cc", ["..#..", ".###.", "#.#.#", "..#..", "#####"]),
    "knockback": ("cc", ["#..#.", ".#..#", "..#.#", ".#..#", "#..#."]),
    "charm": ("cc", [".#.#.", "#####", "#####", ".###.", "..#.."]),
    "fear": ("cc", [".###.", "#.#.#", "#####", "#####", "#.#.#"]),
    "flinch": ("cc", ["#....", ".#...", "..#.#", "...#.", "....#"]),
    "root": ("cc", [".#.#.", "#####", ".#.#.", "#####", ".#.#."]),
    "blind": ("cc", [".....", ".###.", "#...#", ".###.", "....."]),
    "silence": ("cc", ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"]),
}


def icon(name):
    kind, rows = ICONS[name]
    edge, ink = KIND[kind]
    img = new(9, 9)
    rect(img, 0, 0, 9, 9, OUT)
    rect(img, 1, 1, 8, 8, edge)
    rect(img, 2, 2, 7, 7, hexc("17171b"))
    for j, row in enumerate(rows):
        for i, c in enumerate(row):
            if c == "#":
                px(img, 2 + i, 2 + j, ink)
    return img


ICON_PITCH = 10

