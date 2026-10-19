"""Iron-mass warrior skill plates (2026-10-17): 地割り (slam), 押し薙ぎ (whirl), and 鉄槌 (war_ult, 2026-10-19).

The Creator rejected formula shapes ("ださい"). The main shapes are therefore hand-placed 32x32 pixel masters
(rows below), drawn after the references: SamusDev "Dragon Warrior" (a thick glowing crescent, bright head, torn
tail, a burst on impact), MatE "The Dark Sister" (saturated red heat in steps, loose red pixels). Only the timing
is procedural: a reveal along the stroke, a cooling colour shift and deliberate tearing. Colours come from a small
palette texture whose outer halo cells are translucent, so every shape carries its own glow.
Same native plate compiler as the approved dash (build_approved_dash_v3.geometry): one quad strip per run.
"""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image
from build_approved_dash_v3 import PACK, SIZE, geometry

TEXTURE = 'combat_vfx/warrior_iron/palette'
# Palette cell -> RGBA. Halo cells are translucent: the glow that the references get from bloom.
# Alpha doubles as a marker for scripts/shaders/item.fsh: 253 = heat (self-lit, flickering), 121 / 65 = glow
# halo. Rock and steel stay 255 and are lit by the world like any other item.
PALETTE = {
    'W': (255, 248, 222, 253),  # white-hot core
    'Y': (255, 205, 110, 253),  # yellow heat
    'O': (255, 130, 44, 253),   # orange
    'R': (232, 52, 28, 253),    # red
    'D': (142, 18, 26, 253),    # deep red
    'E': (70, 10, 18, 253),     # cooled ember
    'h': (255, 70, 32, 121),    # near halo
    'g': (170, 20, 30, 65),     # far halo
    'S': (242, 247, 255, 255),  # steel light
    's': (176, 192, 208, 255),  # steel mid
    'k': (78, 92, 108, 255),    # steel dark
    'L': (168, 156, 150, 255),  # rock lip, lit
    'M': (98, 88, 88, 255),     # rock lip, shade
    'N': (34, 14, 18, 255),     # open crack floor
    'K': (62, 54, 56, 255),     # rock side in shadow
}
KEYS = list(PALETTE)
COOL = {'W': 'Y', 'Y': 'O', 'O': 'R', 'R': 'D', 'D': 'E', 'E': 'E', 'h': 'g', 'g': 'g', 'S': 's', 's': 'k', 'k': 'k',
        'L': 'L', 'M': 'M', 'N': 'N', 'K': 'K'}

# ---------------------------------------------------------------- hand-drawn masters (32x32, row 0 = top / far)
# 地割り: the overhead cut seen from behind, from high right, bulging left, into the ground. Head at the bottom.
CLEAVE = [
    "......................gg........",
    ".....................ghRD.......",
    "...................ghRRD........",
    "..................hYORD.........",
    "................ghYORD..........",
    "...............hWYORRD..........",
    "..............hWYOORD...........",
    ".............hWYOORRD...........",
    "............hWYOORRDg...........",
    "...........hWYOOORD.............",
    "..........ghWYOORRDg............",
    "..........hWYYOORRD.............",
    ".........ghWYOOORRDg............",
    ".........hWYYOOORRD.............",
    ".........hWYOOORRRD.............",
    ".........hWYOOORRDg.............",
    "........ghWYOOORRRD.............",
    ".........hWYYOOORRD.............",
    ".........hWWYOOORRDg............",
    "..........hWYYOOORRD............",
    "..........hWWYOOORRDg...........",
    "...........hWYYOOORRD...........",
    "...........ghWYYOOORD...........",
    "............hWWYYOORRD..........",
    ".............hWWYOORRD..........",
    "..............hWWYOORD..........",
    "..............ghWWYORRg.........",
    "...............hWWWYORD.........",
    "................hWWWYOR.........",
    "...............ghWWWWYOh........",
    "..............ghWWWWWWYOh.......",
    ".............ghhWWWWWWWYhg......",
]
# 地割り impact: fire bursts up and out of the split ground (standing plane at the impact, 3 hand frames).
BURST = [[
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "...............hh...............",
    "..............hYYh..............",
    ".............hYWWYh.............",
    "............hYWWWWYh............",
    "...........hOYWWWWYOh...........",
    "..........hROYWWWWYORh..........",
    ".........ghROYWWWWYORhg.........",
    "........ghRROYYWWYYORRhg........",
    ".......ghRROOYYWWYYOORRhg.......",
    "......ghRRROOYYWWYYOORRRhg......",
    ".....ghRRRROOOYWWYOOORRRRhg.....",
    "....gghhRRRROOOYYOOORRRRhhgg....",
    "...gghhhhRRRRRRRRRRRRRRhhhhgg...",
], [
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "...............h................",
    "..............hYh......h........",
    "..............hYh.....hYh.......",
    "....h.........hOh.....hOh.......",
    "...hYh.......hOWOh....hRh.......",
    "...hOh.......hOWOh...hOh........",
    "...hRh......hROWORh..hRh....h...",
    "....hRh.....hROWWORh.hOh...hYh..",
    "....hOh....hRROWWORhhROh...hOh..",
    ".....hRh...hRROWWOORhROh..hRh...",
    ".....hOOh..hROOWWYOOROh..hOh....",
    "......hROh.hROYWWYOOROh.hROh....",
    "......hROOhhROYWWWYOORhhROh.....",
    ".......hROOhROYWWWYOORhROOh.....",
    ".......hRROOROYWWWYYOROORh......",
    "........hRROOYYWWWWYOORRh.......",
    "........hRROOYWWWWWYYORRh.......",
    ".........hRROYWWWWWWYORh........",
    ".........ghROYWWWWWWYORhg.......",
    "........ghRROYYWWWWYYORRhg......",
    ".......ghRROOYYWWWWYYOORRhg.....",
    "......ghRRROOOYYWWYYOOORRRhg....",
    ".....ghRRRROOOOYYYYOOOORRRRhg...",
    "....gghhRRRRROOOOOOOORRRRRhhgg..",
    "...gghhhhRRRRRRRRRRRRRRRRhhhhgg.",
], [
    "................................",
    "........g.......................",
    ".......gRg..........g...........",
    "........g..........gRg..........",
    "...g................g.....g.....",
    "..gOg.....................gRg...",
    "...g...........g...........g....",
    "..............gRg...............",
    ".....g........gOg......g........",
    "....gRg.......hOh.....gOg.......",
    ".....hRh.....hROh......hRh......",
    ".....hOh.....hROh.....hROh..g...",
    "......hRh...hRROh.....hRh..gRg..",
    "......hOh...hROORh...hROh...g...",
    ".......hRh..hROYORh..hRh........",
    ".......hROh.hROYOORh.hOh........",
    "........hRh.hROYYORhhROh........",
    "........hROhhROYYYORhROh........",
    ".........hRhROOYWYOORROh........",
    ".........hRROOYWWYYORROh........",
    "..........hRROYWWWYOORh.........",
    "..........hRROYWWWYYORh.........",
    "...........hROYWWWWYORh.........",
    "...........hROYYWWYYORhg........",
    "..........ghROOYYYYOORhg........",
    ".........ghRROOOYYOOORRhg.......",
    "........ghRRROOOOOOOORRRhg......",
    ".......ghRRRRROOOOOORRRRRhg.....",
    "......gghhRRRRRRRRRRRRRRRhhgg...",
    ".....gghhhhRRRRRRRRRRRRRhhhhgg..",
    "................................",
    "................................",
]]
# 地割り rift: ONE fissure, wide at the impact (row 31) and thinning away, with short side branches.
# Hand-chosen centre points (x per row) and half widths; drawn as dark floor, a molten core and rock lips.
RIFT_PATH = {31: (16, 3), 30: (16, 3), 29: (15, 3), 28: (15, 3), 27: (16, 2), 26: (17, 2), 25: (17, 2), 24: (16, 2),
             23: (15, 2), 22: (15, 2), 21: (16, 2), 20: (17, 1), 19: (18, 1), 18: (18, 1), 17: (17, 1), 16: (16, 1),
             15: (16, 1), 14: (17, 1), 13: (18, 1), 12: (18, 1), 11: (17, 0), 10: (17, 0), 9: (18, 0), 8: (19, 0),
             7: (19, 0), 6: (18, 0), 5: (18, 0), 4: (19, 0), 3: (19, 0), 2: (20, 0)}
RIFT_BRANCHES = [  # (row, x) cells of thin side cracks, hand-placed
    [(19, 15), (18, 14), (18, 13), (17, 12), (16, 11), (16, 10), (15, 9)],
    [(12, 19), (11, 20), (10, 21), (10, 22), (9, 23)],
    [(27, 18), (26, 19), (26, 20), (25, 21), (25, 22)],
]


def rift_master():
    rows = [list('.' * 32) for _ in range(32)]
    def put(y, x, c, over=False):
        if 0 <= y < 32 and 0 <= x < 32 and (over or rows[y][x] in '.h'):
            rows[y][x] = c
    for y, (cx, w) in RIFT_PATH.items():
        hot = (31 - y) / 29                      # 0 at the impact, 1 far away
        for x in range(cx - w, cx + w + 1):
            put(y, x, 'N', True)
        core = 'W' if hot < .3 else 'Y' if hot < .6 else 'O'
        put(y, cx, core, True)
        if w >= 2:
            put(y, cx - 1, 'Y' if hot < .3 else 'O', True)
        if w >= 3:
            put(y, cx + 1, 'O', True)
        if hot < .45:
            put(y, cx - w - 2, 'h'); put(y, cx + w + 2, 'h')
    for branch in RIFT_BRANCHES:
        for k, (y, x) in enumerate(branch):
            put(y, x, 'R' if k < len(branch) - 2 else 'D', True)
    return ["".join(r) for r in rows]


RIFT = rift_master()
# 地割り upheaval: slabs of ground thrust up around the impact (standing plane, bottom row = ground line).
SLABS = [
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "..........L.............L.......",
    ".........LLM...........LLM......",
    "....L....LMM..........LLMM......",
    "...LLM..LLMMM.........LMMM..L...",
    "..LLMM..LMMMM........LLMMM.LLM..",
    "..LMMM.LLMMMM........LMMMM.LMM..",
    ".LLMMMMLMMMMM.......LLMMMMMLMMM.",
    ".LMMMMMMMMMMM.......LMMMMMMMMMM.",
    ".OORRRRORRRROh.....hORRRRORRROO.",
    "hYOOYYOOYOOOYh.....hYOOYYOOOYOYh",
    "gghhhhhhhhhhhg.....ghhhhhhhhhhgg",
]


# 押し薙ぎ: a thick 190 degree crescent around the wielder (top-down, centre = wielder, row 0 = far front).
# Outer rim is the white-hot blade edge; the body cools inward; the tail (right end) is already torn.
ARC = [
    "................................",
    "................................",
    "..........gghhhhhhhhgg..........",
    "........ghhWWWWWWWWWWhhg........",
    "......ghWWWYYYYYYYYYYWWWhg......",
    ".....hWWYYOOOOOOOOOOOOYYWWh.....",
    "....hWYYOOORRRRRRRRRRROOOYWh....",
    "...hWYOORRRDDDDDDDDDDRRRROOYh...",
    "..hWYOORRDDg..........gDRROOYh..",
    "..hWYORRDg..............gDROYWh.",
    ".hWYORDg..................gDOYh.",
    ".hWYORg....................gRYh.",
    "hWYORD......................DOh.",
    "hWYORg......................gRh.",
    "hWYOR........................Rg.",
    "hWYOR........................h..",
    "hWYOD........................g..",
    "hWYOg...........................",
    ".hYR............................",
    ".hYD............................",
    "..hR............................",
    "..hg............................",
    "...g............................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
]
# Hand-drawn shard sprites (steel with a hot edge) for both skills.
SHARDS = [
    ["..S.", ".SsY", "Ssk.", ".k.."],
    [".S..", "SSY.", ".skO", "..k."],
    ["SY..", ".sY.", "..kO", "...."],
]


def to_grid(rows):
    """32x32 master -> the 64x64 contour grid, each hand pixel a 2x2 block. Row 0 of the master is far/top."""
    g = np.zeros((SIZE, SIZE), dtype=np.uint8)
    for y, row in enumerate(rows):
        assert len(row) == 32, (y, len(row), row)
        for x, ch in enumerate(row):
            if ch != '.':
                v = KEYS.index(ch) + 1
                g[62 - 2 * y:64 - 2 * y, 2 * x:2 * x + 2] = v
    return g


def cool(rows, steps):
    for _ in range(steps):
        rows = ["".join(COOL.get(c, c) for c in r) for r in rows]
    return rows


def blank():
    return ["." * 32 for _ in range(32)]


def cleave(frame):
    rows = [list(r) for r in CLEAVE]
    if frame <= 2:
        # The cut travels top -> bottom; the head row and below are not there yet.
        head = [11, 22, 32][frame]
        for y in range(head, 32):
            rows[y] = list('.' * 32)
        if head < 32:
            for y in range(max(0, head - 2), head):
                rows[y] = [('W' if c in 'YO' else c) for c in rows[y]]
    elif frame >= 3:
        # After the cut lands only the bright rim stays, so the wielder can see past it.
        rows = [[c if c in 'WYh' else '.' for c in r] for r in rows]
        age = frame - 2
        gone = min(32, age * 6)          # the tail tears away from the top first
        for y in range(gone):
            rows[y] = list('.' * 32)
        if age >= 3:                     # one deliberate seam late in the fade, not stripes
            for y in range(gone, min(32, gone + 2)):
                rows[y] = list('.' * 32)
        return cool(["".join(r) for r in rows], (age + 1) // 2)
    return ["".join(r) for r in rows]


def burst(frame):
    if frame < 3:
        return BURST[frame]
    if frame >= 9:
        return blank()
    rows = [list(r) for r in BURST[2]]
    age = frame - 2
    for y in range(min(32, age * 5)):     # flames burn down from the tips
        rows[y] = list('.' * 32)
    return cool(["".join(r) for r in rows], (age + 1) // 2)


def rift(frame):
    # Opens from the impact outward in three frames, stays molten, then cools; the dark crack and lips remain.
    rows = [list(r) for r in RIFT]
    reach = [20, 8, 0][frame] if frame < 3 else 0
    for y in range(reach):
        rows[y] = list('.' * 32)
    rows = ["".join(r) for r in rows]
    rows = cool(rows, max(0, frame - 5) // 2)
    if frame > 12:                       # the lips and floor sink back in from the far end
        cut = (frame - 12) * 8
        rows = ['.' * 32 if y < cut else r for y, r in enumerate(rows)]
    return rows


def slabs(frame):
    # Thrust up in two frames, hang, then sink back into the ground and go dark.
    lift = [6, 2, 0, 0, 0, 0, 1, 2, 4, 7, 11][frame] if frame < 11 else 32
    rows = ['.' * 32] * lift + SLABS[:32 - lift]
    return cool(rows, max(0, frame - 4) // 2)


def arc(frame):
    # The sweep runs from the left end through the front to the right end; then the tail tears off.
    rows = [list(r) for r in ARC]
    cut = [6, 14, 22, 32][frame] if frame < 4 else 32
    for y in range(32):
        for x in range(32):
            if x > cut + (0 if y < 12 else -(y - 12)):
                rows[y][x] = '.'
    if frame >= 4:
        rows = [[c if c in 'WYh' else '.' for c in r] for r in rows]
        age = frame - 3
        for y in range(32):
            for x in range(32):
                if x < age * 6:
                    rows[y][x] = '.'
        return cool(["".join(r) for r in rows], (age + 1) // 2)
    return ["".join(r) for r in rows]


def shards(frame, sweep):
    """Shard sprites thrown along hand-picked paths (standing plane for slam, flat ring for sweep)."""
    rows = [list('.' * 32) for _ in range(32)]
    if frame >= 12:
        return ["".join(r) for r in rows]
    if sweep:
        paths = [(4, 16, -1.6, -.2), (8, 6, -1.0, -1.3), (16, 3, 0, -1.6), (24, 6, 1.0, -1.3), (28, 16, 1.6, -.2)]
    else:
        paths = [(14, 28, -1.4, -2.6), (17, 28, .9, -3.0), (15, 27, -.4, -3.3), (18, 27, 1.8, -2.2), (13, 28, -2.1, -1.8)]
    for k, (x0, y0, vx, vy) in enumerate(paths):
        f = frame * .9
        x = x0 + vx * f
        y = y0 + vy * f + (0 if sweep else .32 * f * f)
        sprite = SHARDS[k % 3]
        cooled = cool(sprite, frame // 4)
        for sy, srow in enumerate(cooled):
            for sx, ch in enumerate(srow):
                px, py = int(round(x)) + sx, int(round(y)) + sy
                if ch != '.' and 0 <= px < 32 and 0 <= py < 32:
                    rows[py][px] = ch
    return ["".join(r) for r in rows]


# ---------------------------------------------------------------- solid rock (Blockbench-style tilted cuboids)
def uv(key):
    i = KEYS.index(key)
    return [i, 0, i + 1, 1]


def cuboid(lo, hi, top='L', side='M', shade='K', bottom='N', axis=None, angle=0.0, pivot=None):
    """A shaded block: lit top, lit north/west, shadowed south/east (light from the upper left)."""
    faces = {'up': {'texture': '#0', 'uv': uv(top)}, 'down': {'texture': '#0', 'uv': uv(bottom)},
             'north': {'texture': '#0', 'uv': uv(side)}, 'west': {'texture': '#0', 'uv': uv(side)},
             'south': {'texture': '#0', 'uv': uv(shade)}, 'east': {'texture': '#0', 'uv': uv(shade)}}
    e = {'from': [round(v, 3) for v in lo], 'to': [round(v, 3) for v in hi], 'shade': False, 'faces': faces}
    if axis and angle:
        c = pivot or [(lo[k] + hi[k]) / 2 for k in range(3)]
        e['rotation'] = {'origin': [round(v, 3) for v in c], 'axis': axis, 'angle': angle, 'rescale': False}
    return e


def rock(x, z, w, d, h, y0=8.0, axis=None, angle=0.0, glow=None):
    """One broken chunk: a main block and a smaller offset cap so the top edge is not a clean box.
    Anything below the ground plane (y=8) is cut away; glow adds a hot band where it leaves the ground."""
    out = []
    if axis and angle:
        y0 += math.tan(math.radians(abs(angle))) * max(w, d) / 2 + .1   # a tilted base corner must not sink below the ground
    pivot = [x, max(8.0, y0), z]
    for lo, hi, top in (((x - w / 2, y0, z - d / 2), (x + w / 2, y0 + h, z + d / 2), 'L'),
                        ((x - w * .3, y0 + h * .55, z - d * .2), (x + w * .15, y0 + h * 1.25, z + d * .35), 'L')):
        # Clip at the ground, and keep a tilted block's lowest corner above it too.
        floor = 8.0 + (math.tan(math.radians(abs(angle))) * max(w, d) / 2 + .1 if axis and angle else 0.0)
        lo = [lo[0], max(floor, lo[1]), lo[2]]
        if hi[1] <= 8.05:
            continue
        out.append(cuboid(lo, hi, top=top, axis=axis, angle=angle, pivot=pivot))
    if glow and y0 + h > 8.05:
        # A thin molten seam where the chunk tore out of the ground, not a tile under it.
        out.append(cuboid((x - w * .56, 8.0, z - d * .56), (x + w * .56, 8.18, z + d * .56), top=glow, side=glow,
                          shade=glow, bottom=glow))
    return out


# Upheaval around the impact: hand-placed chunks (x, z, width, depth, height, tilt axis, tilt) in a 16-unit cell.
HEAVE = [(4.2, 9.0, 2.6, 2.2, 5.2, 'z', 22.5), (6.6, 11.6, 2.0, 2.4, 3.6, 'x', -22.5), (11.2, 11.0, 2.4, 2.0, 4.6, 'x', -22.5),
         (12.8, 8.2, 2.2, 2.6, 5.6, 'z', -22.5), (9.0, 12.8, 1.6, 1.6, 2.6, 'x', -22.5), (3.4, 6.0, 1.6, 1.8, 2.8, 'z', 22.5),
         (13.6, 5.2, 1.4, 1.6, 2.4, 'z', -22.5)]
RISE = [-1.0, -.35, 0.0, 0.0, 0.0, 0.0, -.08, -.2, -.38, -.6, -.82, -1.0]


def heave(frame):
    elements = []
    if frame >= len(RISE):
        return elements
    glow = 'Y' if frame < 3 else 'O' if frame < 6 else 'R' if frame < 9 else 'D'
    for x, z, w, d, h, axis, angle in HEAVE:
        h *= .7  # kept low enough to see the target over it
        elements += rock(x, z, w, d, h, 8.0 + RISE[frame] * h, axis, angle, glow)
    return elements


def rim(frame):
    """Low broken rock lips along both sides of the fissure, opening with it and sinking at the end."""
    elements = []
    reach = [20, 8, 0][frame] if frame < 3 else 0
    sink = max(0., (frame - 11) / 4)
    if sink >= 1:
        return elements
    for y, (cx, w) in RIFT_PATH.items():
        if y < reach or y % 2:
            continue
        hot = (31 - y) / 29
        for side, dx in ((-1, -w - 1.2), (1, w + 1.2)):
            x = (cx + dx + .5) / 2
            z = (31 - y + .5) / 2
            h = (3.2 - hot * 2.2) * (1 - sink) * (.8 + .4 * ((y * 7 + side) % 3) / 2)
            size = 1.3 - hot * .6
            elements += rock(x, z, size, size * 1.2, h, 8.0, 'z', 22.5 * side * (1 if y % 4 else -1) * -1,
                             'O' if hot < .4 and frame < 7 else None)
    return elements


# 鉄槌 finale (2026-10-19): a tight cluster of tall pillars bursting out under the target, the launch made visible.
# (x, z, width, depth, height, tilt axis, tilt); tilts lean away from the centre so the cluster flowers open.
PILLAR = [(8.0, 8.4, 2.6, 2.6, 11.0, None, 0.0), (5.6, 9.6, 2.0, 2.2, 8.0, 'z', 22.5), (10.6, 9.4, 2.2, 2.0, 8.6, 'z', -22.5),
          (7.0, 11.4, 1.8, 1.8, 6.4, 'x', -22.5), (9.4, 6.0, 1.8, 2.0, 6.8, 'x', 22.5), (4.6, 6.6, 1.4, 1.4, 4.2, 'z', 22.5),
          (11.8, 6.4, 1.4, 1.6, 4.6, 'z', -22.5)]
PILLAR_RISE = [-1.0, -.45, -.1, 0.0, 0.0, 0.0, 0.0, -.1, -.25, -.45, -.65, -.85, -1.0, -1.0]


def pillar(frame):
    elements = []
    if frame >= len(PILLAR_RISE):
        return elements
    glow = 'W' if frame < 2 else 'Y' if frame < 5 else 'O' if frame < 8 else 'R' if frame < 11 else 'D'
    for x, z, w, d, h, axis, angle in PILLAR:
        h *= .7  # the launch must read without walling off the target
        elements += rock(x, z, w, d, h, 8.0 + PILLAR_RISE[frame] * h, axis, angle, glow)
    return elements


SOLID = {'heave': (12, heave), 'rim': (16, rim), 'pillar': (14, pillar)}


CLIPS = {'cleave': (8, cleave), 'burst': (9, burst), 'burstx': (9, burst), 'rift': (16, rift), 'shards': (12, lambda f: shards(f, False)),
         'arc': (11, arc), 'wshards': (12, lambda f: shards(f, True)),
         # 鉄槌: the three falls heat up. The first cut is red, the second orange, the last the full white-hot cleave.
         'ucleave_r': (8, lambda f: cool(cleave(f), 2)), 'ucleave_o': (8, lambda f: cool(cleave(f), 1)),
         'uburst_o': (9, lambda f: cool(burst(f), 1))}


def palette_inks():
    # 16 px wide palette texture, one texel per colour; UVs are in model units (16 per texture edge).
    return {i + 1: [i, 0, i + 1, 1] for i in range(len(KEYS))}


def write_palette(assets):
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for i, key in enumerate(KEYS):
        for y in range(16):
            image.putpixel((i, y), PALETTE[key])
    path = assets / f'textures/{TEXTURE}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


SHADERS = Path(__file__).resolve().parent / 'shaders'


def write_shaders(assets):
    # The pack's minecraft namespace sits next to the projects namespace.
    target = assets.parent / 'minecraft/shaders/core'
    target.mkdir(parents=True, exist_ok=True)
    for name in ('item.vsh', 'item.fsh'):
        (target / name).write_text((SHADERS / name).read_text(encoding='utf-8'), encoding='utf-8')


def build(assets, write):
    write_palette(assets)
    write_shaders(assets)
    inks = palette_inks()
    for clip, (count, fn) in CLIPS.items():
        for frame in range(count):
            path = f'combat_vfx/warrior_iron/{clip}_{frame}'
            elements = geometry(to_grid(fn(frame)), inks, curved=False)
            for e in elements:
                for face in e['faces'].values():
                    face.pop('tintindex', None)
            write(assets / f'models/{path}.json', {'ambientocclusion': False, 'textures': {'0': f'projects:{TEXTURE}'},
                'elements': elements})
            write(assets / f'items/{path}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{path}'}})
    for clip, (count, fn) in SOLID.items():
        for frame in range(count):
            path = f'combat_vfx/warrior_iron/{clip}_{frame}'
            write(assets / f'models/{path}.json', {'ambientocclusion': False, 'textures': {'0': f'projects:{TEXTURE}'},
                'elements': fn(frame)})
            write(assets / f'items/{path}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{path}'}})


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    build(PACK / 'assets/projects', write)
    (PACK / 'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name != 'index.txt')) + '\n', encoding='utf-8')
