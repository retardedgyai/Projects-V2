"""開拓港 v2: Wynncraft-scale harbor. Preview only (rendered by iso.py)."""
import math
import random
import numpy as np
import iso

R = random.Random(11)
w = iso.World()
NX, NZ = 116, 74
SEA = 4          # water surface block
G = 8            # town ground surface block
LOG = lambda n: (f"{n}_top", n)


# ---------- helpers ----------
def noise2(seed, cell):
    rng = np.random.default_rng(seed)
    grid = rng.random((64, 64))

    def f(x, z):
        gx, gz = x / cell, z / cell
        i, j = int(gx) % 63, int(gz) % 63
        fx, fz = gx - i, gz - j
        fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
        a, b, c, d = grid[i, j], grid[i + 1, j], grid[i, j + 1], grid[i + 1, j + 1]
        return a * (1 - fx) * (1 - fz) + b * fx * (1 - fz) + c * (1 - fx) * fz + d * fx * fz
    return f


N1, N2, N3 = noise2(1, 9), noise2(2, 4), noise2(3, 6)


def pick(options, x, y, z, salt=0):
    return options[hash((x, y, z, salt)) % len(options)]


STONE = ["stone_bricks"] * 6 + ["cracked_stone_bricks", "mossy_stone_bricks", "tuff_bricks"]
STONE_LOW = ["mossy_stone_bricks", "mossy_cobblestone", "stone_bricks", "tuff_bricks", "cobblestone"]
ROCK = ["stone", "stone", "andesite", "tuff", "cobblestone", "stone"]
ROOF_RED = ["nether_bricks", "red_nether_bricks", "red_nether_bricks", "red_nether_bricks", "mangrove_planks"]  # eave -> ridge gradient (dark to warm)


def roof_mat(level, top_level, palette, x, z):
    t = level / max(1, top_level)
    i = min(len(palette) - 1, int(t * len(palette) + (hash((x, z)) % 3 - 1) * 0.12))
    return palette[max(0, i)]


def put(x, y, z, top, side=None, shape="full", keep=False):
    if keep and (x, y, z) in w.b: return
    w.set(x, y, z, top, side, shape)


ROOMS = ["dark_oak_planks", "dark_oak_planks", "shroomlight", "red_wool", "shroomlight", "spruce_planks"]


def room(x, z):
    return ROOMS[hash((x // 2, z // 2, 7)) % len(ROOMS)]


def window(cells, inward, back=None):
    dx, dz = inward
    for (x, y, z) in cells:
        w.clear(x, y, z)
        put(x + dx, y, z + dz, "glass")
        put(x + 2 * dx, y, z + 2 * dz, back or room(x, z))


# ---------- terrain ----------
def coast(x):
    if x < 14: return 52 + int(4 * math.sin(x / 3.0))          # rocky headland to the west
    if x <= 64: return 42                                       # built quay
    return 44 + int(3 * math.sin((x - 64) / 7.0))             # sandy bay along the new east quarter                                   # beach turning north-east


nat = {}
for x in range(NX):
    for z in range(NZ):
        c = coast(x)
        if z <= c:
            hill = max(0.0, (12 - z) / 12) * (5 + 4 * N1(x, z))
            head = max(0.0, (16 - x) / 16) * (8 + 5 * N2(x, z)) if x < 16 else 0
            v = G + hill + head + (N3(x, z) - .5) * 1.4
            if x > 60: v = min(v, SEA + 1 + max(0, c - z) / 2.5 + max(0, 64 - x))
            nat[(x, z)] = max(v, SEA + 1)
        else:
            nat[(x, z)] = None

# flat ground only where it is used (town, roads, paths); everything else eases back to the natural
# terrain over BLEND blocks instead of stopping at a hard edge
ROAD_N = range(52, 56)
flat = set()
for x in range(9, 66):
    for z in range(7, 43):
        if x < 14 and z > 38: continue
        flat.add((x, z))
for x in range(56, 114):                     # east quarter: houses along a street to the windmill
    for z in range(6, 34): flat.add((x, z))
for x in ROAD_N:
    for z in range(0, 43): flat.add((x, z))
for x in range(5, 9):
    for z in range(37, coast(x)): flat.add((x, z))
for x in range(5, 15):
    for z in range(37, 42): flat.add((x, z))
flat = {p for p in flat if nat.get(p) is not None}
BLEND = 7
dist = {p: 0 for p in flat}
frontier = list(flat)
while frontier:
    nxt = []
    for (x, z) in frontier:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                q = (x + dx, z + dz)
                if q in nat and nat[q] is not None and q not in dist and dist[(x, z)] < BLEND:
                    dist[q] = dist[(x, z)] + (1.4 if dx and dz else 1)
                    nxt.append(q)
    frontier = nxt
heights = {}
for p, v in nat.items():
    if v is None:
        heights[p] = None
        continue
    t = min(1.0, dist.get(p, BLEND) / BLEND)
    t = t * t * (3 - 2 * t)
    heights[p] = max(SEA + 1, int(round(G + (v - G) * t)))

for (x, z), h in heights.items():
    if h is None:
        floor = 1 if z - coast(x) < 4 else 0
        put(x, 0, z, "sand")
        if floor: put(x, 1, z, pick(["sand", "gravel", "sand"], x, 1, z))
        for y in range(floor + 1, SEA + 1):
            put(x, y, z, "water_still", shape="water")
        continue
    steep = max(abs(h - (heights.get((x + dx, z + dz)) or SEA)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    for y in range(max(0, h - 4), h):
        put(x, y, z, pick(ROCK, x, y, z) if steep >= 2 or x < 14 else "dirt")
    if (x > 60 and coast(x) - z < 9) or h <= SEA + 2:
        put(x, h, z, "sand")
    elif steep >= 3:
        put(x, h, z, pick(["moss_block", "stone", "andesite", "mossy_cobblestone"], x, h, z))
    elif steep >= 1 and N2(x * 7, z * 7) > .45 and (x < 16 or z < 8):
        put(x, h, z, pick(["stone", "andesite", "tuff", "coarse_dirt", "moss_block"], x, h, z))
    else:
        put(x, h, z, *(("grass_block_top", "grass_block_side") if N2(x * 3, z * 3) > .22 else ("moss_block",)))
    if h - 4 > 0: put(x, h - 5, z, "stone")

# soften terraces: a half step on the low side of every 1-block rise, boulders on the headland
for (x, z), hh in list(heights.items()):
    if hh is None or hh == G and 8 <= x <= 66 and 6 <= z <= 42: continue
    up = [heights.get((x + dx, z + dz)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
    if any(u == hh + 1 for u in up if u is not None) and (x, hh + 1, z) not in w.b:
        top = w.b.get((x, hh, z), ("", "grass_block_top"))[1]
        put(x, hh + 1, z, "moss_block" if top in ("grass_block_top", "moss_block") else top, shape="slab")
rb = random.Random(5)
for _ in range(14):
    bx, bz = rb.randrange(0, 15), rb.randrange(2, 52)
    hh = heights.get((bx, bz))
    if hh is None: continue
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            for dy in range(1, 3):
                if rb.random() < (.8 if dy == 1 else .35) and abs(dx) + abs(dz) < 3:
                    put(bx + dx, hh + dy, bz + dz, pick(["stone", "andesite", "mossy_cobblestone", "tuff"], bx + dx, dy, bz + dz))

# ---------- quay: tall stone wall, cap, lamp posts, steps down to the pier ----------
for x in range(14, 65):
    for z in (41, 42):
        for y in range(0, G):
            put(x, y, z, pick(STONE_LOW if y < SEA + 1 else STONE, x, y, z))
        put(x, G, z, "polished_andesite" if z == 42 else "stone_bricks")
    put(x, G + 1, 42, "stone_bricks", shape="slab") if x % 2 else None
    if x % 8 == 2:
        put(x, G + 1, 42, "polished_andesite", shape="wall")
        put(x, G + 2, 42, "dark_oak_planks", shape="fence")
        put(x, G + 3, 42, "lantern", shape="lantern")
    # arches in the quay face at water level
    if x % 6 in (0, 1, 2) and x % 6 != 0:
        pass

# ---------- pier and ship ----------
PX0, PX1 = 48, 51
PIER_Y = G - 1
for z in range(43, 68):
    for x in range(PX0, PX1 + 1):
        put(x, PIER_Y, z, "spruce_planks")
    if z % 4 == 1 or z == 67:
        for x in (PX0, PX1):
            for y in range(0, PIER_Y):
                put(x, y, z, *LOG("stripped_spruce_log"))
            put(x, PIER_Y + 1, z, *LOG("spruce_log"), shape="thinlog")
    else:
        put(PX0, PIER_Y + 1, z, "spruce_planks", shape="fence_ns")
for x in range(PX0, PX1 + 1):  # steps from the quay down to the pier
    put(x, G, 43, "stone_bricks", shape="stair_n")
put(PX0, PIER_Y + 2, 67, "lantern", shape="lantern"); put(PX1, PIER_Y + 2, 67, "lantern", shape="lantern")


def ship(cx, z0, length):
    """Two-masted ship moored along the pier, bow pointing south."""
    keel, deck = 1, PIER_Y
    for i in range(length):
        z = z0 + i
        t = i / (length - 1)
        hw = 3.6 * (math.sin(min(1, (t + .04) / .25) * math.pi / 2) if t < .25 else 1) * (1 - max(0, t - .6) ** 1.6 * 4.2)
        hw = max(hw, .6)
        for y in range(keel, deck + 1):
            k = (y - keel) / (deck - keel)
            half = hw * (.45 + .55 * k ** .7)
            for x in range(cx - 5, cx + 6):
                d = abs(x - cx + .0)
                if d <= half:
                    edge = d > half - 1 or y == keel
                    if y == deck:
                        put(x, y, z, "spruce_planks" if not edge else "dark_oak_planks")
                    elif edge:
                        mat = "dark_oak_planks" if y < SEA + 1 else ("red_nether_bricks" if y == deck - 1 else "spruce_planks")
                        put(x, y, z, mat)
        # gunwale
        half = hw
        for x in range(cx - 5, cx + 6):
            if half - 1 < abs(x - cx) <= half and i not in range(0, 7):
                put(x, deck + 1, z, "dark_oak_planks", shape="fence")
    # stern castle (north end)
    for z in range(z0, z0 + 7):
        for x in range(cx - 3, cx + 4):
            if (x, deck, z) in w.b:
                for y in (deck + 1, deck + 2):
                    edge = abs(x - cx) == 3 or z == z0 or z == z0 + 6
                    if edge: put(x, y, z, "dark_oak_planks" if y == deck + 1 else "spruce_planks")
                put(x, deck + 3, z, "spruce_planks")
                if abs(x - cx) == 3 or z == z0 + 6: put(x, deck + 4, z, "dark_oak_planks", shape="fence")
    for x in (cx - 1, cx + 1):
        window([(x, deck + 2, z0 + 6)], (0, -1))
    put(cx, deck + 4, z0 + 6, "lantern", shape="lantern")
    # masts, yards and billowing sails (sails span x, thin in z)
    for mz, mh, sw in ((z0 + 11, 20, 5), (z0 + 19, 16, 4)):
        for y in range(deck + 1, deck + mh):
            put(cx, y, mz, *LOG("dark_oak_log"), shape="thinlog")
        for yard in (deck + mh - 2, deck + mh - 9):
            for x in range(cx - sw - 1, cx + sw + 2):
                if x != cx: put(x, yard, mz, *LOG("dark_oak_log"), shape="thinlog")
        for yard_top, rows in ((deck + mh - 3, 6), (deck + mh - 10, 6)):
            for r in range(rows):
                y = yard_top - r
                for x in range(cx - sw, cx + sw + 1):
                    belly = 0
                    stripe = "red_wool" if r == rows // 2 - 1 else "white_wool"
                    put(x, y, mz + 1 + belly, stripe, shape="pane_x")
        put(cx, deck + mh, mz, "red_wool", shape="shut_e")
    # bowsprit
    for k in range(5):
        put(cx, deck + 1 + k // 2, z0 + length + k - 1, *LOG("dark_oak_log"), shape="thinlog")


ship(56, 45, 24)

# ---------- harbor hall (cross-gabled, round tower) ----------
HX0, HX1, HZ0, HZ1 = 12, 36, 10, 20     # main body
WX0, WX1, WZ1 = 20, 28, 29              # south wing (gable faces the harbor)
F1 = G + 5                              # top of stone floor
F2 = F1 + 5                             # top of timber floor (wall plate)


def body(x0, x1, z0, z1, skip=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)): continue
            if skip and skip(x, z): continue
            for y in range(G + 1, F1 + 1):
                put(x, y, z, pick(STONE_LOW if y <= G + 1 else STONE, x, y, z))
            for y in range(F1 + 1, F2 + 1):
                frame = y in (F1 + 1, F2) or (x in (x0, x1) and z in (z0, z1)) or \
                    ((x - x0) % 3 == 0 and z in (z0, z1)) or ((z - z0) % 3 == 0 and x in (x0, x1))
                put(x, y, z, *(LOG("dark_oak_log") if frame else ("calcite",)))


# stone floor, timber floor
body(HX0, HX1, HZ0, HZ1, skip=lambda x, z: z == HZ1 and WX0 < x < WX1)
body(WX0, WX1, HZ1, WZ1, skip=lambda x, z: z == HZ1)
# plinth and pilasters
for x in range(HX0 - 1, HX1 + 2):
    for z in range(HZ0 - 1, WZ1 + 2):
        inside = (HX0 - 1 <= x <= HX1 + 1 and HZ0 - 1 <= z <= HZ1 + 1) or (WX0 - 1 <= x <= WX1 + 1 and z <= WZ1 + 1)
        if inside and (x, G + 1, z) not in w.b: put(x, G, z, "polished_andesite")
for x in range(HX0, HX1 + 1, 4):
    for y in range(G + 1, F1 + 1):
        put(x, y, HZ1 + 1 if not WX0 <= x <= WX1 else WZ1 + 1, "stone_bricks" if y < F1 else "chiseled_stone_bricks") if not (WX0 < x < WX1) else None
# arched ground-floor windows
for x in range(HX0 + 2, HX1 - 1, 4):
    if WX0 - 1 <= x <= WX1 + 1: continue
    window([(x, y, HZ1) for y in (G + 2, G + 3, G + 4)] + [(x + 1, y, HZ1) for y in (G + 2, G + 3, G + 4)], (0, -1))
    put(x, G + 4, HZ1, "stone_bricks", shape="stair_e_t"); put(x + 1, G + 4, HZ1, "stone_bricks", shape="stair_w_t")
    w.b.pop((x, G + 4, HZ1 - 1), None); w.b.pop((x + 1, G + 4, HZ1 - 1), None)
for z in (13, 17):
    window([(HX1, y, z) for y in (G + 2, G + 3)] + [(HX1, y, z + 1) for y in (G + 2, G + 3)], (-1, 0))
for x in (WX0, WX1):
    window([(x, y, 25) for y in (G + 2, G + 3, G + 4)], (1 if x == WX0 else -1, 0))
# upper windows: flush 2x2 panes filling every other timber bay, with a sill on the floor beam
def pane_window(cells, inward, sill_out):
    dx, dz = inward
    for (x, y, z) in cells:
        put(x, y, z, "glass"); put(x + dx, y, z + dz, room(x, z))
    for (x, y, z) in cells:
        if y == min(c[1] for c in cells):  # flower box on the floor beam
            put(x + sill_out[0], y - 1, z + sill_out[1], "spruce_planks", shape="slab_t")
            put(x + sill_out[0], y, z + sill_out[1], pick(["flowering_azalea_leaves", "azalea_leaves", "flowering_azalea_leaves"], x, y, z), shape="slab")


for x in range(HX0 + 1, HX1):
    if (x - HX0) % 6 in (1, 2) and not WX0 - 1 <= x <= WX1 + 1:
        pane_window([(x, F1 + 2, HZ1), (x, F1 + 3, HZ1)], (0, -1), (0, 1))
for z in range(HZ0 + 1, HZ1):
    if (z - HZ0) % 6 in (1, 2):
        pane_window([(HX1, F1 + 2, z), (HX1, F1 + 3, z)], (-1, 0), (1, 0))
for z in range(HZ1 + 1, WZ1):
    if (z - HZ1) % 6 in (1, 2):
        pane_window([(WX1, F1 + 2, z), (WX1, F1 + 3, z)], (-1, 0), (1, 0))

# grand entrance on the wing's gable: 3-wide arched door, steps, banners, balcony
EZ = WZ1
for x in range(23, 26):
    for y in range(G + 1, G + 5):
        w.clear(x, y, EZ)
        put(x, y, EZ - 1, "spruce_planks", "spruce_door_bottom" if y < G + 3 else "spruce_door_top") if y < G + 4 else None
put(23, G + 4, EZ, "stone_bricks", shape="stair_e_t"); put(25, G + 4, EZ, "stone_bricks", shape="stair_w_t")
put(24, G + 5, EZ, "chiseled_stone_bricks")
for x in range(22, 27):
    put(x, G, EZ + 1, "stone_bricks", shape="stair_n"); put(x, G - 1, EZ + 2, "stone_bricks", shape="stair_n") if (x, G, EZ + 2) not in w.b else None
for bx in (21, 27):  # hanging banners
    for y in range(F1 - 3, F1 + 1):
        put(bx, y, EZ + 1, "red_wool" if y > F1 - 3 else "yellow_wool", shape="shut_s")
    put(bx, F1 + 1, EZ + 1, "gold_block", shape="shut_s")
for x in range(WX0, WX1 + 1):  # balcony on corbels
    put(x, F1 + 1, EZ + 1, "dark_oak_planks", shape="slab")
    put(x, F1, EZ + 1, "dark_oak_planks", shape="stair_n_t") if x % 2 == 0 else None
    put(x, F1 + 2, EZ + 2 if False else EZ + 1, "dark_oak_planks", shape="fence_we") if x not in (WX0, WX1) else None
window([(24, F1 + 2, EZ), (24, F1 + 3, EZ), (23, F1 + 3, EZ), (25, F1 + 3, EZ)], (0, -1))


def gable_roof(x0, x1, z0, z1, base, axis, palette, trim="dark_oak_planks"):
    """Bell-cast gable: shallow at the eave, steep toward the ridge. axis = ridge direction."""
    lo, hi = (z0, z1) if axis == "x" else (x0, x1)
    half = (hi - lo) / 2
    prof = [0.5, 1.0, 2.0, 3.0, 4.0, 5.5, 7.0, 8.5, 10.0, 11.5]
    top_level = prof[min(len(prof) - 1, int(half))] + 1
    for a in range(x0, x1 + 1):
        for b in range(z0, z1 + 1):
            d = (b - lo) if axis == "x" else (a - lo)
            d = min(d, hi - lo - d)
            s = prof[min(len(prof) - 1, d)]
            ridge = abs((b if axis == "x" else a) - (lo + hi) / 2) < .6
            end = a in (x0, x1) if axis == "x" else b in (z0, z1)
            for k in range(int(s)):
                if end: continue
                if (a, base + k, b) not in w.b or w.b[(a, base + k, b)][0] != "full":
                    put(a, base + k, b, trim if end else roof_mat(k, top_level, palette, a, b))
            y = base + int(s)
            mat = trim if end else roof_mat(int(s), top_level, palette, a, b)
            if (a, y, b) in w.b and w.b[(a, y, b)][0] == "full": continue
            if ridge:
                put(a, y, b, "polished_blackstone_bricks" if not end else trim, shape="slab" if s % 1 else "full")
                if not s % 1: put(a, y + 1, b, "polished_blackstone_bricks", shape="slab")
            elif s % 1:
                put(a, y, b, mat, shape="slab")
            else:
                side = (b < (lo + hi) / 2) if axis == "x" else (a < (lo + hi) / 2)
                facing = ("s" if side else "n") if axis == "x" else ("e" if side else "w")
                put(a, y, b, mat, shape=f"stair_{facing}")


YR = F2 + 1
gable_roof(HX0 - 1, HX1 + 1, HZ0 - 1, HZ1 + 1, YR, "x", ROOF_RED)
gable_roof(WX0 - 1, WX1 + 1, HZ1 - 2, WZ1 + 1, YR, "z", ROOF_RED)
def gable_wall(axis, plane, lo, hi, base=None, palette=None):
    """Turn the roof fill on a gable plane into a half-timbered wall: edge posts, a sill beam, a king post."""
    base = base or YR
    palette = palette or ROOF_RED
    for t in range(lo, hi + 1):
        for y in range(base, base + 14):
            p = (plane, y, t) if axis == "x" else (t, y, plane)
            v = w.b.get(p)
            if not v or v[1] not in palette or v[0] != "full": continue
            frame = t in (lo, hi) or y == base or (t == (lo + hi) // 2 and y > base + 2)
            put(*p, *(LOG("dark_oak_log") if frame else ("calcite",)))


gable_wall("x", HX1, HZ0, HZ1)
gable_wall("x", HX0, HZ0, HZ1)
gable_wall("z", WZ1, WX0, WX1)

# east gable: door onto a balcony on corbels, windows either side, a lantern
for y in (YR, YR + 1):
    w.clear(HX1, y, 15); put(HX1 - 1, y, 15, "spruce_planks", "spruce_door_bottom" if y == YR else "spruce_door_top")
for z in (13, 17):
    window([(HX1, YR + 1, z), (HX1, YR + 2, z)], (-1, 0))
window([(HX1, YR + 4, 14), (HX1, YR + 4, 16)], (-1, 0), "dark_oak_planks")
for z in range(12, 19):
    for x in (HX1 + 1, HX1 + 2):
        put(x, YR - 1, z, "spruce_planks")
    put(HX1 + 1, YR - 2, z, "dark_oak_planks", shape="stair_w_t") if z % 2 == 0 else None
    put(HX1 + 3, YR - 1, z, "dark_oak_planks", shape="slab_t")
    put(HX1 + 3, YR, z, "dark_oak_planks", shape="fence_ns" if 12 < z < 18 else "fence")
for x in (HX1 + 1, HX1 + 2):
    put(x, YR, 12, "dark_oak_planks", shape="fence_we"); put(x, YR, 18, "dark_oak_planks", shape="fence_we")
put(HX1 + 2, YR, 13, "flowering_azalea_leaves"); put(HX1 + 2, YR, 17, "azalea_leaves")
put(HX1 + 1, YR, 16, "spruce_planks", shape="stair_e"); put(HX1 + 2, YR, 14, "barrel_top", "barrel_side")
put(HX1 + 2, YR + 1, 14, "lantern", shape="lantern")
for y in range(YR - 4, YR - 1):  # banner hanging from the balcony
    put(HX1 + 4, y, 15, "red_wool" if y > YR - 4 else "yellow_wool", shape="shut_e")

# wing gable face: timber + round window
for x in range(WX0, WX1 + 1):
    for y in range(YR, YR + 9):
        if (x, y, WZ1) not in w.b and (x, y, WZ1 - 1) in w.b:
            put(x, y, WZ1, "spruce_planks")
for x, y in ((24, YR + 1), (23, YR + 2), (24, YR + 2), (25, YR + 2), (24, YR + 3)):
    w.clear(x, y, WZ1); put(x, y, WZ1, "glass"); put(x, y, WZ1 - 1, "dark_oak_planks")
for y in range(YR, YR + 8):  # gable king post
    if (24, y, WZ1) in w.b and w.b[(24, y, WZ1)][1] == "spruce_planks": put(24, y, WZ1, *LOG("dark_oak_log"))

# round corner tower at the south-west corner with a teal bell-cast cone
TX, TZ, TR = 12, 20, 3.6
TTOP = F2 + 6
for x in range(int(TX - TR - 2), int(TX + TR + 3)):
    for z in range(int(TZ - TR - 2), int(TZ + TR + 3)):
        d = math.hypot(x - TX, z - TZ)
        if TR - 1 < d <= TR:
            for y in range(G + 1, TTOP + 1):
                if y <= F1: mat = pick(STONE, x, y, z)
                elif y in (F1 + 1, TTOP): mat = "dark_oak_planks"
                else: mat = "calcite"
                put(x, y, z, mat)
        elif d <= TR - 1:
            for y in range(G + 1, TTOP + 1): w.b.pop((x, y, z), None)
for y0 in (G + 3, F1 + 3, TTOP - 2):
    for (x, z, ix, iz) in ((int(TX), int(TZ + TR), 0, -1), (int(TX + TR), int(TZ), -1, 0), (int(TX + 2), int(TZ + 3), 0, -1)):
        if (x, y0, z) in w.b: window([(x, y0, z), (x, y0 + 1, z)], (ix, iz))
COPPER = ["oxidized_cut_copper", "weathered_cut_copper", "oxidized_copper"]


def cone(cx, cz, radius, base, palette, steep=1.0):
    """Bell-cast cone. Heights come from a smooth radial curve in half blocks; each column ends in a stair
    whose tall side faces the centre (or a slab on a half step), so the rings read as one surface."""
    def height(d):
        r = max(0.0, radius - d)
        return (r * 0.75 + max(0.0, r - radius * 0.35) ** 1.5 * 0.9) * steep
    cols = {}
    for x in range(int(cx - radius - 1), int(cx + radius + 2)):
        for z in range(int(cz - radius - 1), int(cz + radius + 2)):
            d = math.hypot(x - cx, z - cz)
            if d <= radius: cols[(x, z)] = round(height(d) * 2) / 2
    top = max(cols.values())
    for (x, z), s in cols.items():
        mat = lambda k: palette[min(len(palette) - 1, int(k / max(1, top) * len(palette)))]
        for k in range(int(s)):
            put(x, base + k, z, mat(k))
        dx, dz = x - cx, z - cz
        facing = ("w" if dx > 0 else "e") if abs(dx) >= abs(dz) else ("n" if dz > 0 else "s")
        if s % 1:
            put(x, base + int(s), z, mat(s), shape="slab")
        elif d_out_lower(cols, x, z, s, cx, cz):
            put(x, base + int(s) - 1, z, mat(s), shape=f"stair_{facing}") if s >= 1 else None
    return base + int(top)


def d_out_lower(cols, x, z, s, cx, cz):
    sx = 1 if x > cx else -1 if x < cx else 0
    sz = 1 if z > cz else -1 if z < cz else 0
    return cols.get((x + sx, z + sz), -1) < s
cone(TX, TZ, TR + 1.4, TTOP + 1, ["weathered_cut_copper", "oxidized_cut_copper", "oxidized_cut_copper", "oxidized_copper"], 1.6)
peak = max(y for (x, y, z) in w.b if x == TX and z == TZ)
for k in range(4):
    put(TX, peak + 1 + k, TZ, "gold_block" if k == 3 else "polished_blackstone_bricks", shape="post" if k < 3 else "lantern")

# chimneys
for cx, cz in ((16, 12), (33, 12)):
    for y in range(G + 1, YR + 11):
        put(cx, y, cz, pick(STONE, cx, y, cz)); put(cx + 1, y, cz, pick(STONE, cx + 1, y, cz))
    put(cx, YR + 11, cz, "stone_bricks", shape="slab"); put(cx + 1, YR + 11, cz, "stone_bricks", shape="slab")


# ---------- depth and daily life on the hall ----------
# string course under the timber floor and a battered plinth: two shadow lines on the stone
def ledge(x, y, z, facing, top=True):
    if (x, y, z) not in w.b: put(x, y, z, "stone_bricks", shape=f"stair_{facing}" + ("_t" if top else ""))


for x in range(HX0, HX1 + 1):
    if not WX0 <= x <= WX1:
        ledge(x, F1, HZ1 + 1, "n"); ledge(x, G + 1, HZ1 + 1, "n", False)
for z in range(HZ0, HZ1 + 1):
    ledge(HX1 + 1, F1, z, "w"); ledge(HX1 + 1, G + 1, z, "w", False)
for z in range(HZ1, WZ1 + 1):
    ledge(WX1 + 1, F1, z, "w"); ledge(WX1 + 1, G + 1, z, "w", False)
for x in range(WX0, WX1 + 1):
    if not 22 <= x <= 26: ledge(x, G + 1, WZ1 + 1, "n", False)
# east pilasters
for z in range(HZ0, HZ1 + 1, 4):
    for y in range(G + 1, F1):
        put(HX1 + 1, y, z, "stone_bricks" if y < F1 - 1 else "chiseled_stone_bricks")
# ground-floor shop front east of the wing: wide window and a striped awning
for x in range(30, 35):
    for y in (G + 2, G + 3):
        w.clear(x, y, HZ1); put(x, y, HZ1 - 1, "glass"); put(x, y, HZ1 - 2, "shroomlight" if x % 2 else "spruce_planks")
    w.b.pop((x, G + 1, HZ1 + 1), None)
    put(x, G + 1, HZ1 + 1, "spruce_planks", "barrel_side" if x % 2 else "spruce_planks", shape="slab_t" if x % 2 == 0 else "full")
    put(x, G + 4, HZ1 + 1, "green_wool" if x % 2 else "white_wool", shape="stair_n")
    put(x, G + 4, HZ1 + 2, "green_wool" if x % 2 else "white_wool", shape="slab_t")
# side door on the east face with a little roof, bench, lantern, doormat
for y in (G + 1, G + 2):
    w.clear(HX1, y, 15); put(HX1 - 1, y, 15, "spruce_planks", "spruce_door_bottom" if y == G + 1 else "spruce_door_top")
for z in (14, 15, 16):
    put(HX1 + 1, G + 3, z, "spruce_planks", shape="stair_w")
put(HX1 + 1, G, 15, "red_wool", shape="carpet") if False else None
put(HX1 + 1, G + 1, 17, "spruce_planks", shape="stair_w"); put(HX1 + 1, G + 1, 18, "spruce_planks", shape="stair_w")
put(HX1 + 1, G + 2, 13, "lantern", shape="lantern")

# dormers on the main roof's south slope (left and right of the wing)
def dormer(cx):
    zf = HZ1  # front flush with the wall
    for z in range(zf - 4, zf + 2):
        for x in range(cx - 2, cx + 3):
            for y in range(YR, YR + 6):
                v = w.b.get((x, y, z))
                if v and v[1] in ROOF_RED and z >= zf: w.clear(x, y, z)
    for x in range(cx - 1, cx + 2):
        for y in (YR, YR + 1):
            put(x, y, zf, *(LOG("dark_oak_log") if x != cx else ("calcite",)))
    window([(cx, YR, zf), (cx, YR + 1, zf)], (0, -1))
    put(cx, YR - 1, zf + 1, "spruce_planks", shape="slab_t")
    put(cx, YR, zf + 1, "flowering_azalea_leaves", shape="slab")
    for x, y in ((cx - 1, YR + 2), (cx, YR + 2), (cx + 1, YR + 2), (cx, YR + 3)):
        put(x, y, zf, "spruce_planks")
    for z in range(zf - 4, zf + 2):
        trim = z == zf + 1
        for x, y, f in ((cx - 2, YR + 2, "e"), (cx - 1, YR + 3, "e"), (cx + 2, YR + 2, "w"), (cx + 1, YR + 3, "w")):
            put(x, y, z, "dark_oak_planks" if trim else roof_mat(y - YR, 9, ROOF_RED, x, z), shape=f"stair_{f}", keep=z < zf)
        put(cx, YR + 4, z, "polished_blackstone_bricks", shape="slab", keep=z < zf)
        if not trim:
            for x in range(cx - 1, cx + 2):
                for y in range(YR + 2, YR + 4):
                    if (x, y, z) not in w.b: put(x, y, z, "red_nether_bricks")
    for x in (cx - 2, cx + 2):  # side cheeks
        for z in range(zf - 1, zf + 1):
            if (x, YR + 1, z) not in w.b: put(x, YR + 1, z, "spruce_planks")


dormer(16)


def roof_balcony(x0, x1):
    """Open terrace cut into the south slope: floor on the wall plate, projecting one block over the eave,
    railing, timber back wall with a door, table, chairs and plants."""
    zb = HZ1 - 3  # back wall
    for x in range(x0 - 1, x1 + 2):
        for z in range(zb + 1, HZ1 + 2):
            for y in range(YR, YR + 8):
                v = w.b.get((x, y, z))
                if v and v[1] in ROOF_RED + ["polished_blackstone_bricks"] and x0 <= x <= x1: w.clear(x, y, z)
                elif v and v[1] in ROOF_RED and x in (x0 - 1, x1 + 1) and v[0] == "full": put(x, y, z, "spruce_planks")
    for x in range(x0, x1 + 1):
        for z in range(zb + 1, HZ1 + 2):
            if z != HZ1: put(x, YR - 1, z, "spruce_planks")
        put(x, YR - 2, HZ1 + 1, "dark_oak_planks", shape="stair_n_t") if x % 2 == 0 else None
        put(x, YR, HZ1 + 1, "dark_oak_planks", shape="fence_we" if x0 < x < x1 else "fence")
        for y in range(YR, YR + 4):  # back wall
            post = x in (x0, x1) or y == YR + 3
            put(x, y, zb, *(LOG("dark_oak_log") if post else ("calcite",)))
    for z in range(zb + 1, HZ1 + 1):
        for x in (x0, x1):
            put(x, YR, z, "dark_oak_planks", shape="fence_ns")
    mid = (x0 + x1) // 2
    for y in (YR, YR + 1):
        w.clear(mid, y, zb); put(mid, y, zb - 1, "spruce_planks", "spruce_door_bottom" if y == YR else "spruce_door_top")
    for x in (mid - 1, mid + 1):
        window([(x, YR + 1, zb), (x, YR + 2, zb)], (0, -1), "shroomlight")
    put(mid, YR + 2, zb + 1, "lantern", shape="lantern") if False else None
    put(mid, YR, HZ1 - 1, "barrel_top", "barrel_side"); put(mid, YR + 1, HZ1 - 1, "lantern", shape="lantern")
    put(mid - 1, YR, HZ1 - 1, "spruce_planks", shape="stair_w"); put(mid + 1, YR, HZ1 - 1, "spruce_planks", shape="stair_e")
    put(x0 + 1, YR, zb + 1, "flowering_azalea_leaves"); put(x1 - 1, YR, zb + 1, "azalea_leaves")
    put(x0 + 1, YR + 1, HZ1 + 1, "flowering_azalea_leaves", shape="slab"); put(x1 - 1, YR + 1, HZ1 + 1, "flowering_azalea_leaves", shape="slab")
    for y in range(YR - 3, YR):  # banner under the balcony
        put(mid, y, HZ1 + 2, "blue_wool" if y > YR - 3 else "yellow_wool", shape="shut_s")


dormer(32)

# chimney caps and soot
for cx, cz in ((16, 12), (33, 12)):
    for dx in (-1, 0, 1, 2):
        for dz in (-1, 0, 1):
            if dx in (-1, 2) or dz != 0: put(cx + dx, YR + 10, cz + dz, "stone_bricks", shape="slab_t")
    put(cx, YR + 11, cz, "blackstone", shape="slab"); put(cx + 1, YR + 11, cz, "blackstone", shape="slab")

# yard: well, laundry line, firewood, vegetable rows, crates and sacks
WX, WZ = 41, 11
for dx in range(-1, 2):
    for dz in range(-1, 2):
        if (dx, dz) != (0, 0): put(WX + dx, G + 1, WZ + dz, pick(STONE_LOW, WX + dx, 1, WZ + dz))
        else: put(WX, G + 1, WZ, "water_still", shape="water")
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    for y in (G + 2, G + 3): put(WX + dx, y, WZ + dz, "spruce_planks", shape="fence")
for dx in range(-2, 3):
    put(WX + dx, G + 4, WZ - 1, "spruce_planks", shape="stair_s"); put(WX + dx, G + 4, WZ + 1, "spruce_planks", shape="stair_n")
    put(WX + dx, G + 5, WZ, "spruce_planks", shape="slab")
put(WX, G + 3, WZ, "lantern", shape="lantern")
for z in (8, 20):
    for y in (G + 1, G + 2, G + 3): put(46, y, z, "spruce_planks", shape="fence")
for z in range(9, 20):
    if z % 3 != 0: put(46, G + 3, z, pick(["white_wool", "light_blue_wool", "red_wool", "white_wool", "yellow_wool"], 46, 0, z), shape="shut_e")
for z in range(9, 13):
    for y in (G + 1, G + 2):
        put(HX1 + 2, y, z, "oak_log", "oak_log_top")
put(HX1 + 2, G + 3, 10, "spruce_planks", shape="slab")
for x in range(39, 45):
    for z in range(15, 21):
        if x in (38, 45) or z in (14, 21): continue
        put(x, G, z, "farmland_moist", "dirt")
        if x % 2 == 0: put(x, G + 1, z, pick(["azalea_leaves", "oak_leaves", "azalea_leaves"], x, 0, z), shape="slab")
for x in range(38, 46):
    put(x, G + 1, 14, "spruce_planks", shape="fence_we"); put(x, G + 1, 21, "spruce_planks", shape="fence_we")
for z in range(15, 21):
    put(38, G + 1, z, "spruce_planks", shape="fence_ns")
put(45, G + 1, 17, "pumpkin_top", "pumpkin_side"); put(44, G + 1, 19, "pumpkin_top", "pumpkin_side")
put(HX1 + 2, G + 1, 19, "hay_block_top", "hay_block_side"); put(HX1 + 3, G + 1, 19, "hay_block_top", "hay_block_side")
put(HX1 + 2, G + 2, 19, "hay_block_top", "hay_block_side")
put(HX1 + 3, G + 1, 18, "composter_top", "composter_side")
for x, z in ((21, 31), (27, 31), (19, 30)):
    put(x, G + 1, z, "spruce_planks", "spruce_trapdoor"); put(x, G + 2, z, "brown_wool", shape="slab")

# ---------- lighthouse on a rock islet, arched bridge from the quay ----------
LX, LZ = 7, 65
for x in range(LX - 8, LX + 9):
    for z in range(LZ - 7, LZ + 8):
        d = math.hypot(x - LX, (z - LZ) * 1.1) + (N2(x * 2, z * 2) - .5) * 3
        if d < 7:
            top = int(G + 1 - max(0, d - 3) * 1.4)
            for y in range(0, top + 1):
                put(x, y, z, pick(["stone", "andesite", "tuff", "cobblestone", "mossy_cobblestone"], x, y, z) if y < top or d > 4 else "polished_andesite")
# bridge: z 35..41 along x = 57..59, three arches
for z in range(coast(LX) - 1, LZ - 4):
    for x in range(LX - 1, LX + 2):
        put(x, G, z, "stone_bricks" if x == LX else "polished_andesite")
        put(x, G - 1, z, pick(STONE, x, G - 1, z))
        arch_t = ((z - LZ) % 4) / 3
        depth = int(1 + 2.6 * math.sin(arch_t * math.pi))
        pier = (z - LZ) % 4 == 0
        for y in range(0 if pier else G - 1 - (3 - depth), G - 1):
            put(x, y, z, pick(STONE_LOW, x, y, z))
    for x in (LX - 2, LX + 2):
        put(x, G, z, "stone_bricks"); put(x, G + 1, z, "stone_bricks", shape="wall")
        if (z - LZ) % 4 == 0:
            put(x, G + 2, z, "lantern", shape="lantern")
# tower: red/white bands, tapering
LB, LT = G + 2, G + 26
for y in range(LB, LT + 1):
    r = 4.2 - (y - LB) / (LT - LB) * 1.4
    band = ((y - LB) // 4) % 2
    for x in range(LX - 5, LX + 6):
        for z in range(LZ - 5, LZ + 6):
            d = math.hypot(x - LX, z - LZ)
            if r - 1.1 < d <= r:
                put(x, y, z, "red_terracotta" if band else "calcite")
for y in (LB + 1, LB + 9, LB + 17):
    for (x, z) in ((LX, LZ + 4 - (y - LB) // 9), (LX + 4 - (y - LB) // 9, LZ)):
        if (x, y, z) in w.b: window([(x, y, z), (x, y + 1, z)], (0, -1) if x == LX else (-1, 0))
put(LX, LB, LZ + 4, "spruce_planks", "spruce_door_bottom"); put(LX, LB + 1, LZ + 4, "spruce_planks", "spruce_door_top")
# gallery, lantern room, cone
GY = LT + 1
for x in range(LX - 5, LX + 6):
    for z in range(LZ - 5, LZ + 6):
        d = math.hypot(x - LX, z - LZ)
        if d <= 4.4:
            put(x, GY, z, "polished_blackstone_bricks", shape="slab" if d > 3.4 else "full")
        if 3.6 < d <= 4.4:
            put(x, GY + 1, z, "polished_blackstone_bricks", shape="fence")
        if d <= 2.4:
            for y in range(GY + 1, GY + 4):
                put(x, y, z, "glass" if d > 1.4 else "sea_lantern")
            put(x, GY + 4, z, "gold_block" if d > 1.6 else "polished_blackstone_bricks")
cone(LX, LZ, 3.4, GY + 5, ["weathered_cut_copper", "oxidized_cut_copper", "oxidized_copper"], 1.8)
top = max(y for (x, y, z) in w.b if x == LX and z == LZ)
put(LX, top + 1, LZ, "gold_block", shape="post"); put(LX, top + 2, LZ, "gold_block", shape="post")

# ---------- market stalls with striped awnings ----------
def stall(x0, z0, colour):
    for (x, z) in ((x0, z0), (x0 + 3, z0), (x0, z0 + 2), (x0 + 3, z0 + 2)):
        for y in (G + 1, G + 2):
            put(x, y, z, "spruce_planks", shape="fence")
    for x in range(x0 - 1, x0 + 5):
        for z in range(z0 - 1, z0 + 4):
            put(x, G + 3, z, colour if (x - x0) % 2 == 0 else "white_wool", shape="slab" if z in (z0 - 1, z0 + 3) else "full")
    for x in range(x0, x0 + 4):
        put(x, G + 1, z0 + 2, "spruce_planks", shape="slab_t") if x in (x0 + 1, x0 + 2) else None
    put(x0 + 1, G + 1, z0, "barrel_top", "barrel_side"); put(x0 + 2, G + 2, z0 + 2, "melon_top" if False else "pumpkin_top", "pumpkin_side")


stall(32, 24, "red_wool")
stall(39, 24, "blue_wool")
stall(46, 25, "yellow_wool")
for x, z, n in ((38, 38, 2), (39, 38, 1), (44, 39, 1), (45, 39, 2), (52, 39, 1), (33, 39, 2)):
    for k in range(n):
        put(x, G + 1 + k, z, "barrel_top", "barrel_side")
for x, z in ((35, 39), (35, 40), (36, 40), (50, 39)):
    put(x, G + 1, z, "spruce_planks", "spruce_trapdoor")

# ---------- roads: every destination is on a paved, lit road ----------
TERRAIN = {"grass_block_top", "moss_block", "dirt", "sand", "dirt_path_top", "gravel", "coarse_dirt", "stone", "andesite",
           "tuff", "mossy_cobblestone", "cobblestone", "podzol_top", "farmland_moist"}
PAVE = ["stone_bricks", "stone_bricks", "polished_andesite", "andesite", "cobblestone", "stone_bricks", "cracked_stone_bricks"]
EDGE = ["gravel", "coarse_dirt", "cobblestone", "dirt_path_top", "andesite"]


def pave(cells, main=True):
    cells = set(cells)
    for (x, z) in cells:
        top = w.b.get((x, G, z))
        if not top or top[0] != "full" or top[1] not in TERRAIN: continue
        for y in range(G + 1, G + 12):  # clear grass slabs / boulders left in a cutting
            v = w.b.get((x, y, z))
            if v and v[1] in TERRAIN | {"moss_block"}: w.clear(x, y, z)
        edge = any((x + dx, z + dz) not in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if main:
            put(x, G, z, pick(EDGE if edge else PAVE, x, G, z), "stone")
        else:
            put(x, G, z, pick(["dirt_path_top", "dirt_path_top", "gravel", "coarse_dirt"], x, G, z), "dirt_path_side")


def lamp(x, z, h=3):
    if (x, G + 1, z) in w.b: return
    for y in range(G + 1, G + 1 + h): put(x, y, z, "dark_oak_planks", shape="fence")
    put(x, G + 1 + h, z, "lantern", shape="lantern")


def banner_post(x, z, colour, facing="shut_s"):
    for y in range(G + 1, G + 6): put(x, y, z, "dark_oak_planks", shape="fence")
    put(x, G + 6, z, "gold_block", shape="lantern")
    for y in range(G + 3, G + 6):
        put(x, y, z + (1 if facing == "shut_s" else 0) if False else z, colour, shape="fence") if False else None
    dz, dx = (1, 0) if facing == "shut_s" else (0, 1)
    for y in range(G + 2, G + 6):
        put(x + dx, y, z + dz, colour if y > G + 2 else "yellow_wool", shape=facing)


promenade = [(x, z) for x in range(10, 65) for z in range(37, 41)]
north = [(x, z) for x in ROAD_N for z in range(0, 37)]
headland = [(x, z) for x in range(5, 9) for z in range(37, coast(x))] + [(x, z) for x in range(5, 15) for z in range(37, 41)]
pave(promenade); pave(north); pave(headland)
pave([(x, z) for x in range(44, 52) for z in range(10, 13)], main=False)       # to the well and yard
pave([(x, z) for x in range(63, 68) for z in range(35, 41)], main=False)       # down to the beach
SQUARE = [(x, z) for x in range(12, 50) for z in range(30, 37) if not (x > 30 and z < 29)]
pave(SQUARE)                                                                   # square in front of the hall
for z in range(2, 28, 6):
    lamp(51, z); lamp(56, z + 3)
for z in range(39, 53, 5):
    lamp(4, z); lamp(9, z + 2)
for x in range(12, 64, 8):
    lamp(x, 37) if not 20 <= x <= 28 else None

# destination markers: one colour per place, repeated on the signpost
banner_post(5, 37, "red_wool")      # lighthouse path
banner_post(8, 37, "red_wool")
banner_post(47, 40, "blue_wool")    # pier: arrival
banner_post(52, 40, "blue_wool")
# signpost at the junction in front of the pier
SX, SZ = 50, 38
for y in range(G + 1, G + 4): put(SX, y, SZ, "spruce_planks", shape="fence")
put(SX, G + 3, SZ - 1, "lime_wool", shape="arm_z")      # north: the road out
put(SX - 1, G + 3, SZ, "red_wool", shape="arm_x")       # west: lighthouse
put(SX, G + 2, SZ + 1, "light_blue_wool", shape="arm_z")  # south: pier
put(SX - 1, G + 2, SZ, "yellow_wool", shape="arm_x")    # west (lower): market and hall
put(SX, G + 4, SZ, "lantern", shape="lantern")

# north gate: where the road leaves the harbor for the continent
GZ = 2
for px in (50, 57):
    for y in range(G + 1, G + 9):
        put(px, y, GZ, pick(STONE, px, y, GZ)); put(px, y, GZ + 1, pick(STONE, px, y, GZ + 1))
    put(px, G + 9, GZ, "stone_bricks", shape="slab"); put(px, G + 9, GZ + 1, "stone_bricks", shape="slab")
    put(px, G + 5, GZ + 2, "lantern", shape="lantern")
for x in range(51, 57):
    put(x, G + 8, GZ, "stone_bricks"); put(x, G + 8, GZ + 1, "stone_bricks")
    put(x, G + 9, GZ + 1, "stone_bricks", shape="slab") if x % 2 else put(x, G + 9, GZ + 1, "stone_bricks")
put(51, G + 7, GZ + 1, "stone_bricks", shape="stair_e_t"); put(56, G + 7, GZ + 1, "stone_bricks", shape="stair_w_t")
for bx in (52, 55):
    for y in range(G + 5, G + 8): put(bx, y, GZ + 2, "lime_wool" if y > G + 5 else "yellow_wool", shape="shut_s")
put(53, G + 8, GZ + 2, "gold_block", shape="shut_s"); put(54, G + 8, GZ + 2, "gold_block", shape="shut_s")


# the square in front of the hall: fountain on the entrance axis, benches, planters, lamps
FX, FZ = 34, 33
for dx in range(-2, 3):
    for dz in range(-2, 3):
        rim = max(abs(dx), abs(dz)) == 2
        if abs(dx) == 2 and abs(dz) == 2: continue
        put(FX + dx, G + 1, FZ + dz, "stone_bricks" if rim else "water_still", shape="slab_t" if rim else "water")
for y in range(G + 1, G + 4):
    put(FX, y, FZ, "chiseled_stone_bricks" if y == G + 3 else "stone_bricks", shape="wall" if y < G + 3 else "full")
put(FX, G + 4, FZ, "water_still", shape="water")
for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
    put(FX + dx, G + 3, FZ + dz, "water_still", shape="slab")
for (x, z, f) in ((FX - 4, FZ, "stair_w"), (FX + 4, FZ, "stair_e"), (FX - 4, FZ + 1, "stair_w"), (FX + 4, FZ + 1, "stair_e")):
    put(x, G + 1, z, "spruce_planks", shape=f)
for (x, z) in ((FX - 4, FZ - 3), (FX + 4, FZ - 3), (FX - 4, FZ + 3), (FX + 4, FZ + 3)):
    lamp(x, z, 4)
for (x, z) in ((14, 34), (44, 34)):  # planters with small trees
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(x + dx, G + 1, z + dz, "spruce_planks" if (dx or dz) else "grass_block_top", "spruce_trapdoor" if (dx or dz) else "grass_block_side", shape="slab" if (dx or dz) else "full")
    for y in (G + 2, G + 3): put(x, y, z, *LOG("oak_log"))
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            for y in (G + 4, G + 5):
                if not (y == G + 5 and dx and dz): put(x + dx, y, z + dz, "oak_leaves", keep=True)

# the door's axis stays clear: a polished walk from the steps to the quay, framed by two lamps
for z in range(32, 37):
    for x in (23, 24, 25):
        top = w.b.get((x, G, z))
        if top and top[0] == "full" and (x, G + 1, z) not in w.b:
            put(x, G, z, "polished_andesite" if x == 24 else "stone_bricks")
lamp(21, 33, 4); lamp(27, 33, 4)

# people: benches facing the sea, a notice board, a cart
for x in (14, 15, 34, 35, 58, 59):
    put(x, G + 1, 40, "spruce_planks", shape="stair_n")
for x in (16, 20):
    for y in range(G + 1, G + 4): put(x, y, 31, "dark_oak_planks", shape="fence")
for x in range(16, 21):
    put(x, G + 4, 31, "dark_oak_planks", shape="slab")
for x in range(17, 20):
    for y in (G + 2, G + 3):
        put(x, y, 31, pick(["white_wool", "spruce_planks", "white_wool", "yellow_wool", "spruce_planks"], x, y, 7), shape="shut_s")
put(18, G + 1, 31, "lantern", shape="lantern")


# ---------- more details: ivy, clock, lanterns, weathervane ----------
rv = random.Random(21)
def ivy(cells, facing):
    """cells: outer face positions (x, z) of a wall; vines hang from the string course in ragged lengths."""
    for (x, z) in cells:
        if rv.random() < .45: continue
        top = F1 - 1 if rv.random() < .7 else F1 + 2
        for y in range(top, top - rv.randrange(2, 6), -1):
            if y <= G or (x, y, z) in w.b: break
            put(x, y, z, "vine", shape=facing)
ivy([(x, HZ1 + 1) for x in range(HX0 + 1, WX0 - 1)], "shut_s")
ivy([(HX1 + 1, z) for z in range(HZ0 + 1, HZ1)], "shut_e")
ivy([(x, WZ1 + 1) for x in (WX0 + 1, WX1 - 1)], "shut_s")
for x in range(int(TX - 2), int(TX + 3)):  # ivy on the tower
    zz = max((z for (xx, y, z) in w.b if xx == x and y == G + 3 and abs(z - TZ) <= TR), default=None)
    if zz is not None and rv.random() < .7:
        for y in range(G + 1, G + 2 + rv.randrange(2, 7)):
            if (x, y, zz + 1) not in w.b: put(x, y, zz + 1, "vine", shape="shut_s")

# clock on the tower's south face: gold rim, white face, dark centre
cy = TTOP - 3
for dx in (-1, 0, 1):
    for dy in (-1, 0, 1):
        x, y = int(TX) + dx, cy + dy
        zz = max((z for (xx, yy, z) in w.b if xx == x and yy == y and abs(z - TZ) <= TR + .5), default=int(TZ + TR))
        put(x, y, zz + 1, "black_concrete" if (dx, dy) == (0, 0) else "gold_block" if dx and dy else "quartz_block_top", shape="shut_s")

# wall lanterns under the string course
for x in range(HX0 + 2, HX1, 4):
    if not WX0 - 1 <= x <= WX1 + 1 and (x, G + 4, HZ1 + 1) not in w.b: put(x, G + 4, HZ1 + 1, "lantern", shape="lantern")
for z in range(HZ1 + 2, WZ1, 4):
    put(WX1 + 1, G + 4, z, "lantern", shape="lantern")
put(22, G + 4, WZ1 + 1, "lantern", shape="lantern"); put(26, G + 4, WZ1 + 1, "lantern", shape="lantern")

# weathervane on the main ridge
ridge = max(y for (x, y, z) in w.b if x == 30 and HZ0 <= z <= HZ1)
for k in range(1, 4): put(30, ridge + k, 15, "iron_bars", shape="chain")
put(30, ridge + 4, 15, "gold_block", shape="shut_e"); put(30, ridge + 4, 16, "gold_block", shape="shut_e")
put(30, ridge + 3, 14, "gold_block", shape="lantern")

# ---------- harbor life: crane, cargo, nets, rowboats, buoys, lily pads ----------
CX, CZ = 62, 40
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        put(CX + dx, G + 1, CZ + dz, "spruce_planks", shape="slab")
for y in range(G + 1, G + 10):
    put(CX, y, CZ, *LOG("dark_oak_log"))
for k in range(7):  # boom over the water, rising slightly
    put(CX, G + 9 + (k // 3), CZ + 1 + k, *LOG("spruce_log"), shape="thinlog")
for k in range(1, 4):  # brace
    put(CX, G + 6 + k, CZ + k, "dark_oak_planks", shape="stair_s")
hook = CZ + 7
for y in range(G + 1, G + 11):
    put(CX, y, hook, "iron_chain", shape="chain")
put(CX, G, hook, "spruce_planks", "spruce_trapdoor"); put(CX + 1, G, hook, "barrel_top", "barrel_side")
for dz, n in ((-2, 3), (-1, 2), (0, 1)):  # cargo pyramid
    for k in range(n):
        put(CX + 3 + k, G + 1, CZ + dz, "spruce_planks", "spruce_trapdoor")
    for k in range(n - 1):
        put(CX + 3 + k, G + 2, CZ + dz, "spruce_planks", "spruce_trapdoor")
for x in range(CX - 4, CX - 1):
    put(x, G + 1, CZ, "barrel_top", "barrel_side")
put(CX - 4, G + 2, CZ, "barrel_top", "barrel_side")
# net drying racks east of the pier
for x0 in (52, 56):
    for x in (x0, x0 + 3):
        for y in (G + 1, G + 2, G + 3): put(x, y, 39, "spruce_planks", shape="fence")
    for x in range(x0, x0 + 4):
        put(x, G + 3, 39, "spruce_planks", shape="fence_we") if x not in (x0, x0 + 3) else None
        for y in (G + 1, G + 2):
            if x not in (x0, x0 + 3): put(x, y, 39, "cobweb", shape="pane_x")


def rowboat(x0, z0, length=6):
    for dz in range(length):
        for dx in range(3):
            if dz == length - 1 and dx != 1: continue
            rim = dx != 1 or dz in (0, length - 1)
            put(x0 + dx, SEA + 1, z0 + dz, "dark_oak_planks" if rim else "spruce_planks", shape="slab" if rim else "carpet")
    put(x0 + 1, SEA + 1, z0 + 2, "spruce_planks", shape="slab")
    put(x0 - 1, SEA + 1, z0 + 2, "spruce_planks", shape="carpet"); put(x0 + 3, SEA + 1, z0 + 3, "spruce_planks", shape="carpet")


rowboat(66, 49)
rowboat(20, 48)
rowboat(36, 50)
for (x, z) in ((44, 58), (30, 64), (60, 68), (18, 70)):
    put(x, SEA + 1, z, "red_wool", shape="lantern"); put(x, SEA + 2, z, "white_wool", shape="lantern")
for _ in range(26):
    x, z = R.randrange(60, 76), R.randrange(36, 54)
    if heights.get((x, z)) is None and w.b.get((x, SEA, z), ("",))[0] == "water" and (x, SEA + 1, z) not in w.b:
        put(x, SEA + 1, z, "lily_pad", shape="carpet")
for _ in range(14):
    x, z = R.randrange(0, 16), R.randrange(52, 72)
    if heights.get((x, z)) is None and w.b.get((x, SEA, z), ("",))[0] == "water" and (x, SEA + 1, z) not in w.b:
        put(x, SEA + 1, z, "lily_pad", shape="carpet")

# ---------- trees ----------
def big_oak(tx, tz, base, height, spread, leaves="oak_leaves", log="oak_log"):
    """Flared 2x2 trunk tapering to 1x1, 6 branches ending in flattened ragged leaf clusters, hanging fringe."""
    rnd = random.Random(tx * 1000 + tz)
    for y in range(base + 1, base + height + 1):
        cells = ((0, 0), (1, 0), (0, 1), (1, 1)) if y < base + height * .6 else ((0, 0),)
        for dx, dz in cells:
            put(tx + dx, y, tz + dz, *LOG(log))
    for dx, dz in ((-1, 0), (2, 1), (0, 2), (1, -1), (-1, 1), (2, 0)):  # root flare
        if rnd.random() < .7: put(tx + dx, base + 1, tz + dz, *LOG(log), keep=True)
        if rnd.random() < .3: put(tx + dx, base + 2, tz + dz, *LOG(log), keep=True)

    def cluster(cx, cy, cz, r):
        for dx in range(-int(r) - 1, int(r) + 2):
            for dz in range(-int(r) - 1, int(r) + 2):
                for dy in range(-2, 3):
                    q = (dx / r) ** 2 + (dz / r) ** 2 + (dy / (r * .55)) ** 2
                    if q <= 1 - .45 * random.Random(hash((cx + dx, cy + dy, cz + dz))).random() * q:
                        put(cx + dx, cy + dy, cz + dz, leaves, keep=True)
        for dx in range(-int(r), int(r) + 1):  # hanging fringe under the rim
            for dz in range(-int(r), int(r) + 1):
                if r * .6 < math.hypot(dx, dz) <= r and rnd.random() < .22:
                    for k in range(1, 2 + (rnd.random() < .4)):
                        put(cx + dx, cy - 1 - k, cz + dz, leaves, keep=True)

    top = (tx, base + height, tz)
    n = 6
    for k in range(n):
        a = k / n * math.tau + rnd.random() * .7
        L = spread * (.75 + rnd.random() * .35)
        y0 = base + int(height * (.55 + .4 * rnd.random()))
        ex, ez = tx + math.cos(a) * L, tz + math.sin(a) * L
        ey = y0 + 2 + rnd.randrange(0, 3)
        steps = int(L * 1.5)
        for t in range(steps + 1):
            f = t / steps
            put(int(round(tx + (ex - tx) * f)), int(round(y0 + (ey - y0) * f)), int(round(tz + (ez - tz) * f)), *LOG(log), keep=True)
        cluster(int(round(ex)), ey + 1, int(round(ez)), 2.2 + rnd.random() * 1.2)
    cluster(top[0], top[1] + 1, top[2], spread * .6)


big_oak(4, 8, heights[(4, 8)], 10, 5)
big_oak(44, 6, G, 9, 4.5)
big_oak(57, 30, G, 8, 4, "cherry_leaves", "cherry_log")
big_oak(5, 24, heights[(5, 24)], 7, 3.5, "cherry_leaves", "cherry_log")
for x, z in ((19, 31), (29, 31), (37, 21), (11, 24), (35, 22), (50, 20)):
    h = heights.get((x, z)) or G
    if (x, h + 1, z) not in w.b:
        put(x, h + 1, z, "oak_leaves"); put(x + 1, h + 1, z, "oak_leaves", keep=True)


# NPC stand-ins (the real ones are entities in game): where a player finds someone to talk to
def npc(x, z, robe):
    y = (max((yy for (xx, yy, zz) in w.b if xx == x and zz == z and w.b[(xx, yy, zz)][0] == "full"), default=G)) + 1
    put(x, y, z, robe, shape="npc_low"); put(x, y + 1, z, robe, shape="npc_high")


npc(18, 32, "green_wool")     # quest giver at the notice board
npc(49, 39, "blue_wool")      # harbor master by the signpost
npc(34, 28, "red_wool")       # market sellers
npc(41, 28, "purple_wool")
npc(48, 29, "orange_wool")
npc(53, 4, "gray_wool")       # gate guard
npc(55, 4, "gray_wool")
npc(24, 32, "brown_wool")     # someone at the hall door
npc(7, 52, "white_wool")      # lighthouse keeper at the bridge
npc(29, 35, "cyan_wool")      # passers-by
npc(60, 38, "yellow_wool")


# ---------- east quarter: a lived-in street of houses leading to the windmill ----------
DARK = ["deepslate_tiles", "deepslate_tiles", "polished_deepslate"]
WOOD = ["spruce_planks", "spruce_planks", "dark_oak_planks"]
TEAL = ["weathered_cut_copper", "oxidized_cut_copper", "oxidized_cut_copper"]
ST0, ST1 = 19, 22                                  # street rows
pave([(x, z) for x in range(56, 101) for z in range(ST0, ST1 + 1)])
pave([(x, z) for x in range(99, 115) for z in range(12, 31)])                    # windmill green
pave([(x, z) for x in range(83, 87) for z in range(ST1 + 1, 38)], main=False)    # lane down to the bay


def house(x0, z0, wd, dp, door_south, palette, storeys, infill, seed):
    rh = random.Random(seed)
    x1, z1 = x0 + wd - 1, z0 + dp - 1
    top = G + 4 * storeys
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)): continue
            for y in range(G + 1, top + 1):
                post = (x in (x0, x1) and z in (z0, z1)) or ((x - x0) % 3 == 0 and z in (z0, z1)) or ((z - z0) % 3 == 0 and x in (x0, x1))
                beam = y == top or (storeys == 2 and y == G + 5)
                if y == G + 1: mat = (pick(STONE_LOW, x, y, z),)
                elif storeys == 2 and y <= G + 4 and infill != "bricks": mat = (pick(STONE, x, y, z),)
                elif post or beam: mat = LOG("dark_oak_log")
                else: mat = (infill,)
                put(x, y, z, *mat)
    rb = top + 1
    gable_roof(x0 - 1, x1 + 1, z0 - 1, z1 + 1, rb, "x", palette)
    gable_wall("x", x0, z0, z1, rb, palette); gable_wall("x", x1, z0, z1, rb, palette)
    zw, out = (z1, 1) if door_south else (z0, -1)
    dx = x0 + wd // 2
    for y in (G + 1, G + 2):
        w.clear(dx, y, zw); put(dx, y, zw - out, "spruce_planks", "spruce_door_bottom" if y == G + 1 else "spruce_door_top")
    for x in (dx - 1, dx, dx + 1):
        put(x, G + 3, zw + out, "spruce_planks", shape="slab_t")
    put(dx + 2, G + 3, zw + out, "lantern", shape="lantern")
    floors = [G + 2] + ([G + 6] if storeys == 2 else [])
    for x in range(x0 + 1, x1):
        if (x - x0) % 3 == 0 or abs(x - dx) <= 1: continue
        for fy in floors:
            pane_window([(x, fy, zw), (x, fy + 1, zw)], (0, -out), (0, out))
    for z in range(z0 + 1, z1):
        if (z - z0) % 3 == 0: continue
        for fy in floors:
            pane_window([(x1, fy, z), (x1, fy + 1, z)], (-1, 0), (1, 0))
    cx, cz = (x0 + 1, z0 + 1) if rh.random() < .5 else (x1 - 1, z0 + 1)
    for y in range(G + 1, rb + dp // 2 + 3):
        put(cx, y, cz, pick(STONE, cx, y, cz))
    put(cx, rb + dp // 2 + 3, cz, "stone_bricks", shape="slab")
    # life around the house: a bench or barrels by the door, a fenced garden behind
    if rh.random() < .5: put(dx - 2, G + 1, zw + out, "spruce_planks", shape="stair_" + ("n" if door_south else "s"))
    else: put(dx - 2, G + 1, zw + out, "barrel_top", "barrel_side")
    gz0, gz1 = (z0 - 4, z0 - 2) if door_south else (z1 + 2, z1 + 4)
    for x in range(x0, x1 + 1):
        for z in range(gz0, gz1 + 1):
            if x in (x0, x1) or z in (gz0, gz1) and not (z == (gz1 if door_south else gz0)):
                put(x, G + 1, z, "spruce_planks", shape="fence")
            else:
                put(x, G, z, "farmland_moist", "dirt")
                if (x + z) % 2: put(x, G + 1, z, pick(["azalea_leaves", "oak_leaves", "flowering_azalea_leaves"], x, 1, z), shape="slab")
    if rh.random() < .6:  # laundry between two poles beside the garden
        lz = gz0 + 1
        for y in (G + 1, G + 2, G + 3): put(x1 + 2, y, lz - 1, "spruce_planks", shape="fence"); put(x1 + 2, y, lz + 2, "spruce_planks", shape="fence")
        for z in (lz, lz + 1):
            put(x1 + 2, G + 3, z, pick(["white_wool", "red_wool", "light_blue_wool", "yellow_wool"], x1, z, seed), shape="shut_e")


house(59, 9, 9, 8, True, ROOF_RED, 2, "calcite", 1)
house(71, 10, 8, 7, True, DARK, 1, "spruce_planks", 2)
house(82, 9, 9, 8, True, WOOD, 2, "white_terracotta", 3)
house(94, 10, 7, 7, True, TEAL, 1, "calcite", 4)
house(61, 26, 8, 7, False, DARK, 2, "calcite", 5)
house(73, 26, 9, 7, False, ROOF_RED, 1, "bricks", 6)
house(88, 26, 8, 7, False, TEAL, 2, "spruce_planks", 7)
for x in range(58, 100, 7):
    lamp(x, ST0 - 1); lamp(x + 3, ST1 + 1)

# windmill: the landmark at the end of the street
MX, MZ, MR = 107, 21, 3.6
MT = G + 14
for x in range(int(MX - MR - 1), int(MX + MR + 2)):
    for z in range(int(MZ - MR - 1), int(MZ + MR + 2)):
        d = math.hypot(x - MX, z - MZ)
        if MR - 1 < d <= MR:
            for y in range(G + 1, MT + 1):
                if y <= G + 4: mat = (pick(STONE, x, y, z),)
                elif y in (G + 5, G + 10, MT): mat = ("dark_oak_planks",)
                else: mat = ("calcite",)
                put(x, y, z, *mat)
for y in (G + 1, G + 2):
    w.clear(MX, y, int(MZ + MR)); put(MX, y, int(MZ + MR) - 1, "spruce_planks", "spruce_door_bottom" if y == G + 1 else "spruce_door_top")
put(MX, G + 3, int(MZ + MR) + 1, "spruce_planks", shape="slab_t")
for (x, y, z, inward) in ((MX, G + 7, int(MZ + MR), (0, -1)), (int(MX + MR), G + 7, MZ, (-1, 0)), (int(MX + MR), G + 12, MZ, (-1, 0))):
    if (x, y, z) in w.b: window([(x, y, z), (x, y + 1, z)], inward)
cone(MX, MZ, MR + 1.2, MT + 1, WOOD + ["dark_oak_planks"], 1.3)
HY, BZ = MT - 2, int(MZ + MR) + 2
put(MX, HY, BZ - 1, *LOG("dark_oak_log")); put(MX, HY, BZ, *LOG("dark_oak_log"))
for k in range(1, 9):
    put(MX, HY + k, BZ, *LOG("spruce_log"), shape="thinlog"); put(MX, HY - k, BZ, *LOG("spruce_log"), shape="thinlog")
    put(MX + k, HY, BZ, *LOG("spruce_log"), shape="thinlog"); put(MX - k, HY, BZ, *LOG("spruce_log"), shape="thinlog")
for k in range(2, 9):  # lattice sails, pinwheel
    for s in (1, 2):
        put(MX + s, HY + k, BZ, "white_wool", shape="pane_x")
        put(MX - s, HY - k, BZ, "white_wool", shape="pane_x")
        put(MX + k, HY - s, BZ, "white_wool", shape="pane_x")
        put(MX - k, HY + s, BZ, "white_wool", shape="pane_x")
for (x, z) in ((101, 14), (101, 28), (113, 14), (113, 28)):
    lamp(x, z, 4)
for x, z in ((103, 28), (104, 28), (110, 15)):
    put(x, G + 1, z, "hay_block_top", "hay_block_side")
put(104, G + 2, 28, "hay_block_top", "hay_block_side")
for x, z, n in ((111, 27, 2), (112, 27, 1)):
    for k in range(n): put(x, G + 1 + k, z, "barrel_top", "barrel_side")

# second signpost where the street leaves the north road, and orange markers for the windmill
SX2, SZ2 = 57, 23
for y in range(G + 1, G + 4): put(SX2, y, SZ2, "spruce_planks", shape="fence")
put(SX2 + 1, G + 3, SZ2, "orange_wool", shape="arm_x")     # east: windmill
put(SX2, G + 3, SZ2 - 1, "lime_wool", shape="arm_z")       # north: gate
put(SX2, G + 2, SZ2 + 1, "light_blue_wool", shape="arm_z")  # south: pier
put(SX2, G + 4, SZ2, "lantern", shape="lantern")
banner_post(99, 17, "orange_wool"); banner_post(99, 24, "orange_wool")

# gentle grass mounds and flower bushes so the open ground is not a flat sheet
for (x, z) in list(flat):
    top = w.b.get((x, G, z))
    if not top or top[1] not in ("grass_block_top", "moss_block") or (x, G + 1, z) in w.b: continue
    n = N1(x * 4, z * 4)
    if n > .66: put(x, G + 1, z, "moss_block", shape="slab")
    elif N2(x * 5, z * 5) > .86: put(x, G + 1, z, "flowering_azalea_leaves", shape="slab")

npc(66, 20, "lime_wool")
npc(78, 21, "pink_wool")
npc(91, 20, "brown_wool")
npc(104, 26, "white_wool")    # the miller

if __name__ == "__main__":
    img = iso.render(w, W=22)
    img.save("harbor2.png")
    W, H = img.size
    img.crop((int(W * .12), int(H * .02), int(W * .58), int(H * .55))).resize((int(W * .46) * 2, int(H * .53) * 2), 0).save("harbor2-hall.png")
    img.crop((int(W * .45), int(H * .2), int(W * .9), int(H * .85))).resize((int(W * .45) * 2, int(H * .65) * 2), 0).save("harbor2-sea.png")
    print(len(w.b), img.size)
