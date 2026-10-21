"""Zone 2 羊飼いの原 — one zone built out completely (400 x 400 blocks).

Layout (block coords, x = east, z = south):
  village 羊飼いの村 at the road junction (~200, 180): plaza with well, waystone, notice board, signpost,
      inn, houses, market stalls between the two east roads
  pastures + shearing barn + windmill on the north-east downs
  wheat fields with dry-stone walls and a farmstead in the south-east
  the pond (south-west) with a fishing jetty and a fisher's hut
  a ruined watchtower on the north-west hilltop (viewpoint)
  a bandit camp in the western woods (enemies)
  a barrow / dungeon entrance in the south
  ore outcrops and herb patches for gathering; dirt paths from the plaza to every place
"""
import math
import random
import sys
import numpy as np
from PIL import Image
import iso
import harbor2 as hb          # reuse the house / roof / lamp builders (they write to hb.w at height hb.G)

SEA = 62
CX, CY, CELLS, F = .32, .79, 200, 2
B = CELLS * F
H = np.load("spine_H.npy"); ROAD = np.load("spine_road.npy"); WL = np.load("spine_wl.npy")
x0c, y0c = int(CX * 1024 - CELLS / 2), int(CY * 1024 - CELLS / 2)
win = (slice(y0c, y0c + CELLS), slice(x0c, x0c + CELLS))
up = lambda a: np.asarray(Image.fromarray(a[win].astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
hf = up(H)
rng = np.random.default_rng(4)
det = np.asarray(Image.fromarray(rng.random((B // 8 + 1, B // 8 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
hf = hf + (det - .5) * 2.0
_r = np.asarray(Image.fromarray(ROAD[win].astype(np.float32), mode="F").resize((B, B), Image.BILINEAR))
_k = np.exp(-np.linspace(-2, 2, 13) ** 2); _k /= _k.sum()
for ax in (0, 1):
    _r = np.apply_along_axis(lambda v: np.convolve(np.pad(v, 6, mode="edge"), _k, "valid"), ax, _r)
road = _r > .78          # the map road is 16 blocks wide; ~8 reads better at village scale
wl = np.repeat(np.repeat(WL[win], F, 0), F, 1)
lake = (hf < wl - .3) & (wl > SEA)
R = random.Random(8)

# ---------------- pads: flat ground for every building, blended into the slope ----------------
pads = []


def pad(x0, z0, x1, z1, level=None, blend=7):
    sub = hf[z0:z1 + 1, x0:x1 + 1]
    lv = float(np.median(sub)) if level is None else level
    pads.append((x0, z0, x1, z1, round(lv), blend))
    return round(lv)


def apply_pads():
    global hf
    zz, xx = np.mgrid[0:B, 0:B]
    for (x0, z0, x1, z1, lv, bl) in pads:
        dx = np.maximum(np.maximum(x0 - xx, xx - x1), 0)
        dz = np.maximum(np.maximum(z0 - zz, zz - z1), 0)
        d = np.hypot(dx, dz)
        t = np.clip(1 - d / bl, 0, 1); t = t * t * (3 - 2 * t)
        hf = hf * (1 - t) + lv * t


# road level: smooth the road bed so it never steps
# (done after pads, on the road mask only)

# ---------------- layout: the field is for gathering and hunting; shops, inn and quests stay in town ----------------
REST = (172, 196)                         # waystone rest point at the junction
LOGC = (95, 215)                          # logging camp in the western woods
QUARRY = (68, 63, 102, 87)                # open quarry on the north-west hill
HERBS = (230, 255, 330, 330)              # wildflower / herb meadow
WOLF = (345, 345)                         # wolf den in the south-east woods
STONES = (359, 55)                        # standing stones on the downs (landmark)
SHELTER = (160, 292)                      # fishing shelter by the pond
pad(REST[0] - 10, REST[1] - 10, REST[0] + 10, REST[1] + 10, blend=8)
pad(LOGC[0] - 13, LOGC[1] - 11, LOGC[0] + 13, LOGC[1] + 11, blend=8)
pad(SHELTER[0] - 4, SHELTER[1] - 4, SHELTER[0] + 4, SHELTER[1] + 4)
pad(22, 18, 44, 40, blend=10)            # tower ruin on the hilltop
pad(42, 140, 82, 176, blend=8)           # bandit camp clearing
pad(STONES[0] - 9, STONES[1] - 9, STONES[0] + 9, STONES[1] + 9, blend=8)
apply_pads()
# road bed: smooth along the road so it never steps, then lower slightly into the ground
rb = hf.copy()
k = np.exp(-np.linspace(-2, 2, 15) ** 2); k /= k.sum()
for _ in range(2):
    rb = np.apply_along_axis(lambda v: np.convolve(np.pad(v, 7, mode="edge"), k, "valid"), 1, rb)
    rb = np.apply_along_axis(lambda v: np.convolve(np.pad(v, 7, mode="edge"), k, "valid"), 0, rb)
_t = road.astype(np.float32)
for ax in (0, 1):
    _t = np.apply_along_axis(lambda v: np.convolve(np.pad(v, 3, mode="edge"), np.ones(7) / 7, "valid"), ax, _t)
_t = np.clip(np.maximum(_t * 1.6, road), 0, 1)        # the bed eases into the verge over ~4 blocks: no ragged step
hf = hf * (1 - _t) + rb * _t
hi = np.round(hf).astype(int)
hi = np.where(lake, np.minimum(hi, wl.astype(int) - 1), hi)
gy, gx = np.gradient(hf)
slope = np.hypot(gx, gy)

w = iso.World()
hb.w = w
put = lambda x, y, z, *a, **k: w.set(x, y, z, *a, **k)
LOGS = lambda n: (f"{n}_top", n)
patch = np.asarray(Image.fromarray(np.random.default_rng(31).random((B // 12 + 1, B // 12 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
used = np.zeros((B, B), bool)          # cells taken by a structure: no trees, no scatter


def mark(x0, z0, x1, z1):
    used[max(0, z0):z1 + 1, max(0, x0):x1 + 1] = True


# ---------------- terrain blocks ----------------
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        nb = min(hi[max(0, z - 1), x], hi[min(B - 1, z + 1), x], hi[z, max(0, x - 1)], hi[z, min(B - 1, x + 1)])
        lo = max(0, h - 6) if x in (0, B - 1) or z in (0, B - 1) else max(0, min(nb, h) - 1)
        for y in range(lo, h):
            put(x, y, z, "dirt" if h - y <= 3 and slope[z, x] < 1.3 else "stone")
        if lake[z, x]:
            put(x, h, z, "sand" if wl[z, x] - h < 2 else "gravel")
            for y in range(h + 1, int(wl[z, x]) + 1):
                put(x, y, z, "water_still", shape="water")
            continue
        if road[z, x]:
            put(x, h, z, *((("dirt_path_top", "dirt_path_side"),) * 5 + (("gravel",), ("coarse_dirt",), ("cobblestone",)))[(x * 7 + z * 13) % 8])
        elif slope[z, x] > 1.3:
            put(x, h, z, R.choice(["stone", "andesite", "stone", "mossy_cobblestone"]))
        else:
            p = patch[z, x]
            sp = (x * 73 + z * 151) % 97
            top = ("grass_block_top", "grass_block_side") if sp > 2 else ("coarse_dirt",)
            put(x, h, z, *top)
        # half steps on walkable 1-block rises
        if not road[z, x] and slope[z, x] < 1.2:
            ups = [hi[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < B and 0 <= z + dz < B]
            if any(u == h + 1 for u in ups):
                t = w.b[(x, h, z)]
                put(x, h + 1, z, t[1], t[2], shape="slab")
road_level = lambda x, z: hi[z, x]
hb.G = 0


def at(x, z):
    """Ground surface y at a column (top solid block)."""
    return hi[z, x]


# ---------------- small builders ----------------
def fence_line(pts, y_of=at, mat="spruce_planks", gate=None):
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        n = max(abs(bx - ax), abs(bz - az))
        for i in range(n + 1):
            x, z = round(ax + (bx - ax) * i / n), round(az + (bz - az) * i / n)
            if gate and abs(x - gate[0]) + abs(z - gate[1]) <= 1: continue
            if road[z, x] or lake[z, x]: continue
            sh = "fence_ns" if ax == bx else "fence_we" if az == bz else "fence"
            if i % 4 == 0: sh = "fence"
            put(x, y_of(x, z) + 1, z, mat, shape=sh)


def stone_wall(pts):
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        n = max(abs(bx - ax), abs(bz - az))
        for i in range(n + 1):
            x, z = round(ax + (bx - ax) * i / n), round(az + (bz - az) * i / n)
            if road[z, x] or lake[z, x]: continue
            put(x, at(x, z) + 1, z, R.choice(["cobblestone", "mossy_cobblestone", "stone", "cobblestone", "andesite"]), shape="wall")
            mark(x, z, x, z)


def path(pts, width=1):
    """A trodden dirt path between points, following the ground."""
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        n = max(abs(bx - ax), abs(bz - az))
        for i in range(n + 1):
            cx, cz = ax + (bx - ax) * i / n, az + (bz - az) * i / n
            cx += math.sin(i * .13 + ax) * 1.2
            for dx in range(-width, width + 1):
                for dz in range(-width, width + 1):
                    x, z = round(cx + dx), round(cz + dz)
                    if not (0 <= x < B and 0 <= z < B) or road[z, x] or lake[z, x]: continue
                    if abs(dx) + abs(dz) > width + (i % 3 == 0): continue
                    t = w.b.get((x, at(x, z), z))
                    if t and t[1] in ("grass_block_top", "podzol_top", "coarse_dirt"):
                        put(x, at(x, z), z, *(("dirt_path_top", "dirt_path_side") if (x + z) % 5 else ("coarse_dirt",)))
                        w.b.pop((x, at(x, z) + 1, z), None)


def lamp_at(x, z, h=3):
    g = at(x, z)
    for y in range(g + 1, g + 1 + h): put(x, y, z, "dark_oak_planks", shape="fence")
    put(x, g + 1 + h, z, "lantern", shape="lantern")
    mark(x, z, x, z)


def npc(x, z, robe):
    g = at(x, z)
    put(x, g + 1, z, robe, shape="npc_low"); put(x, g + 2, z, robe, shape="npc_high")


def sheep(x, z):
    if road[z, x] or lake[z, x] or (x, at(x, z) + 1, z) in w.b: return
    put(x, at(x, z) + 1, z, "white_wool", shape=R.choice(["sheep_x", "sheep_z"]))


def hb_big(x, z):
    """An old dark-oak: 2x2 trunk, broad flat crown."""
    if used[z, x] or road[z, x] or lake[z, x] or slope[z, x] > 1.0 or x + 1 >= B or z + 1 >= B: return
    g = at(x, z)
    th = R.randint(8, 12)
    for y in range(g + 1, g + th + 1):
        for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)): put(x + dx, y, z + dz, *LOGS("dark_oak_log"))
    r = 4.5 + R.random() * 1.5
    for dx in range(-6, 8):
        for dz in range(-6, 8):
            for dy in range(-2, 3):
                q = ((dx - .5) / r) ** 2 + ((dz - .5) / r) ** 2 + dy * dy / 5
                if q <= 1 - R.random() * .25 and (x + dx, g + th + dy, z + dz) not in w.b:
                    put(x + dx, g + th + dy, z + dz, "oak_leaves")
    mark(x - 4, z - 4, x + 5, z + 5)


def oak(x, z, kind="oak"):
    if used[z, x] or road[z, x] or lake[z, x] or slope[z, x] > 1.1: return
    g = at(x, z)
    leaves, log = {"oak": ("oak_leaves", "oak_log"), "birch": ("birch_leaves", "birch_log")}[kind]
    th = R.randint(4, 6) + (kind == "birch")
    for y in range(g + 1, g + th + 1): put(x, y, z, *LOGS(log))
    r = 2.4 + R.random() * .8
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                q = (dx * dx + dz * dz) / (r * r) + dy * dy / 4.8
                if q <= 1 - R.random() * .3 and (x + dx, g + th + dy, z + dz) not in w.b:
                    put(x + dx, g + th + dy, z + dz, leaves)
    mark(x - 2, z - 2, x + 2, z + 2)


# ---------------- rest point: waystone, campfire, benches, signpost (no shops: those are in town) ----------------
RX, RZ = REST
rg = at(RX, RZ)
for x in range(RX - 7, RX + 8):
    for z in range(RZ - 7, RZ + 8):
        d = math.hypot(x - RX, z - RZ)
        if d < 7 and not road[z, x]:
            put(x, at(x, z), z, R.choice(["cobblestone", "mossy_cobblestone", "gravel", "coarse_dirt", "stone_bricks"]) if d > 5 else "coarse_dirt")
mark(RX - 8, RZ - 8, RX + 8, RZ + 8)
for dx in range(-1, 2):
    for dz in range(-1, 2):
        put(RX + dx, rg + 1, RZ + dz, "chiseled_stone_bricks" if (dx, dz) == (0, 0) else "stone_bricks", shape="full" if (dx, dz) == (0, 0) else "slab")
for y in range(rg + 2, rg + 5): put(RX, y, RZ, "polished_deepslate" if y != rg + 3 else "sea_lantern")
put(RX, rg + 5, RZ, "sea_lantern", shape="lantern")
CF = (RX - 4, RZ + 3)
put(CF[0], rg + 1, CF[1], "campfire_log_lit", shape="fire")
for (dx, dz) in ((-2, 0), (2, 0), (0, 2)): put(CF[0] + dx, rg + 1, CF[1] + dz, "spruce_log", "spruce_log", shape="slab")
put(RX + 4, rg + 1, RZ - 3, "barrel_top", "barrel_side"); put(RX + 5, rg + 1, RZ - 3, "spruce_planks", "spruce_trapdoor")
for (x, z) in ((RX - 6, RZ - 6), (RX + 6, RZ + 6)): lamp_at(x, z, 4)
JX, JZ = 190, 182
jg = at(JX, JZ)
for y in range(jg + 1, jg + 4): put(JX, y, JZ, "spruce_planks", shape="fence")
put(JX, jg + 3, JZ - 1, "orange_wool", shape="arm_z")        # north-west road: 風穴の崖
put(JX + 1, jg + 3, JZ, "pink_wool", shape="arm_x")          # east road: 花筏の淵
put(JX + 1, jg + 2, JZ, "light_blue_wool", shape="arm_x")    # south-east road: harbor (town)
put(JX, jg + 2, JZ + 1, "yellow_wool", shape="arm_z")        # south path: meadow, pond, barrow
put(JX, jg + 4, JZ, "lantern", shape="lantern")
npc(RX - 4, RZ + 6, "light_blue_wool")   # a traveller resting
for (x, z) in ((200, 150), (230, 165), (215, 200), (250, 210), (180, 120)):
    if not road[z, x]: lamp_at(x, z)

# ---------------- downs: wild sheep herds (hunting / hides) among boulders and gorse ----------------
for _ in range(26):                                             # boulders
    x, z = R.randrange(225, 395), R.randrange(15, 150)
    if used[z, x] or road[z, x]: continue
    g = at(x, z)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if R.random() < .7: put(x + dx, g + 1, z + dz, R.choice(["stone", "andesite", "mossy_cobblestone", "cobblestone"]))
    if R.random() < .5: put(x, g + 2, z, R.choice(["stone", "andesite", "mossy_cobblestone"]))
    mark(x - 1, z - 1, x + 1, z + 1)
for _ in range(60):                                             # gorse / bramble clumps
    x, z = R.randrange(225, 395), R.randrange(15, 150)
    if used[z, x] or road[z, x]: continue
    for _k in range(R.randint(3, 7)):
        a, b = x + R.randint(-2, 2), z + R.randint(-2, 2)
        if 0 <= a < B and 0 <= b < B and not road[b, a]:
            put(a, at(a, b) + 1, b, R.choice(["flowering_azalea_leaves", "azalea_leaves", "oak_leaves"]), shape=R.choice(["slab", "full"]))
# abandoned sheepfold: a ring of dry-stone wall, half fallen (shelter / herd spawn)
SFX, SFZ, SFR = 300, 70, 9
for k2 in range(64):
    a = k2 / 64 * math.tau
    x, z = round(SFX + math.cos(a) * SFR), round(SFZ + math.sin(a) * SFR)
    if (k2 % 11) in (0, 1) or 40 <= k2 <= 44: continue
    g = at(x, z)
    for y in range(g + 1, g + 2 + (k2 % 3 != 0)): put(x, y, z, R.choice(["cobblestone", "mossy_cobblestone", "stone"]))
mark(SFX - SFR, SFZ - SFR, SFX + SFR, SFZ + SFR)
# standing stones on the high downs: a landmark and the herd boss's spawn
SX2, SZ2 = STONES
sg2 = at(SX2, SZ2)
for k2 in range(7):
    a = k2 / 7 * math.tau
    x, z = round(SX2 + math.cos(a) * 6), round(SZ2 + math.sin(a) * 6)
    for y in range(sg2 + 1, sg2 + 4 + (k2 % 3)): put(x, y, z, R.choice(["mossy_stone_bricks", "stone", "andesite", "mossy_cobblestone"]))
    if k2 % 2 == 0: put(x + 1, sg2 + 3 + (k2 % 3), z, "stone", shape="slab")
put(SX2, sg2 + 1, SZ2, "chiseled_stone_bricks"); put(SX2, sg2 + 2, SZ2, "sea_lantern", shape="lantern")
mark(SX2 - 8, SZ2 - 8, SX2 + 8, SZ2 + 8)
put(SX2 + 2, at(SX2 + 2, SZ2 + 2) + 1, SZ2 + 2, "white_wool", shape="sheep_x")    # the ram (stand-in)

# ---------------- herb meadow (plant gathering): dense wildflowers, berry thickets, beehives ----------------
hx0, hz0, hx1, hz1 = HERBS
for z in range(hz0, hz1):
    for x in range(hx0, hx1):
        if road[z, x] or lake[z, x] or used[z, x] or (x, at(x, z) + 1, z) in w.b: continue
        n = patch[z, x]
        r = R.random()
        if r < .30:
            put(x, at(x, z) + 1, z, R.choice(["poppy", "dandelion", "cornflower", "poppy", "short_grass"]), shape="plant")
        elif r < .34 and n > .5:
            put(x, at(x, z) + 1, z, "sweet_berry_bush_stage3", shape="plant")
for (x, z) in ((250, 270), (300, 300), (280, 320), (315, 265)):    # herb nodes: marked by a lone birch and a ring of flowers
    oak(x, z, "birch")
    for k2 in range(10):
        a = k2 / 10 * math.tau
        a2, b2 = round(x + math.cos(a) * 4), round(z + math.sin(a) * 4)
        w.b.pop((a2, at(a2, b2) + 1, b2), None); put(a2, at(a2, b2) + 1, b2, "flowering_azalea_leaves", shape="slab")
for (x, z) in ((268, 286), (272, 286)):                      # wild beehives on a stump
    put(x, at(x, z) + 1, z, *LOGS("oak_log")); put(x, at(x, z) + 2, z, "honeycomb_block" if False else "hay_block_top", "hay_block_side", shape="slab")

# ---------------- logging camp (woodcutting) ----------------
LX, LZ = LOGC
lg = at(LX, LZ)
mark(LX - 13, LZ - 11, LX + 13, LZ + 11)
for _ in range(16):                                              # stumps where trees were felled
    x, z = LX + R.randint(-11, 11), LZ + R.randint(-9, 9)
    put(x, at(x, z) + 1, z, *LOGS("oak_log"), shape="slab")
for row in range(3):                                             # stacked log piles (logs lying along x)
    for x in range(LX - 8, LX - 2):
        for y in range(3 - row):
            put(x, lg + 1 + y, LZ + 5 + row, "oak_log", "oak_log_top")
for x in range(LX + 3, LX + 9):
    put(x, lg + 1, LZ - 6, "spruce_log", "spruce_log_top"); put(x, lg + 1, LZ - 5, "spruce_log", "spruce_log_top")
    if x < LX + 7: put(x, lg + 2, LZ - 6, "spruce_log", "spruce_log_top")
for dx in (0, 3):                                                # sawhorse with a log on it
    put(LX + 4 + dx, lg + 1, LZ + 4, "spruce_planks", shape="fence")
for x in range(LX + 3, LX + 9): put(x, lg + 2, LZ + 4, "oak_log", "oak_log_top", shape="thinlog")
put(LX, lg + 1, LZ, *LOGS("oak_log")); put(LX, lg + 2, LZ, "iron_bars", shape="chain")    # chopping block with an axe
for z in range(LZ - 2, LZ + 3):                                  # lean-to shelter
    for y in (lg + 1, lg + 2, lg + 3):
        if z in (LZ - 2, LZ + 2): put(LX - 9, y, z, "spruce_planks", shape="fence")
    put(LX - 9, lg + 4, z, "spruce_planks", shape="stair_e"); put(LX - 8, lg + 4, z, "spruce_planks", shape="slab")
put(LX - 9, lg + 1, LZ, "barrel_top", "barrel_side")
npc(LX + 2, LZ + 1, "brown_wool")                                 # woodcutter (trainer for the gathering path)

# ---------------- quarry (mining): stepped pit with ore veins in the walls ----------------
qx0, qz0, qx1, qz1 = QUARRY
qtop = int(np.median(hi[qz0:qz1, qx0:qx1]))
for x in range(qx0, qx1 + 1):
    for z in range(qz0, qz1 + 1):
        depth = min(x - qx0, qx1 - x, z - qz0, qz1 - z) // 2     # two-block-wide benches = walkable terraces
        depth = min(depth, 6)
        g = at(x, z)
        floor = min(g, qtop) - depth
        for y in range(floor + 1, g + 2): w.b.pop((x, y, z), None)
        for y in range(floor - 3, floor + 1):
            put(x, y, z, R.choice(["stone", "stone", "andesite", "tuff", "iron_ore", "coal_ore", "copper_ore"] if (x + z) % 7 == 0 else ["stone", "andesite", "stone", "gravel"]))
        put(x, floor, z, R.choice(["stone", "andesite", "gravel", "cobblestone"]))
        hi[z, x] = floor
for (x, z) in ((qx0 + 4, qz0 + 3), (qx1 - 5, qz0 + 4), (qx0 + 6, qz1 - 4)):      # visible veins on the benches
    for dx in range(-1, 2):
        for dy in range(1, 3): put(x + dx, at(x, z) + dy, z, R.choice(["iron_ore", "coal_ore", "copper_ore", "iron_ore"]))
CRX, CRZ = qx1 + 2, (qz0 + qz1) // 2                            # wooden winch at the rim
cg2 = at(CRX, CRZ)
for y in range(cg2 + 1, cg2 + 6): put(CRX, y, CRZ, *LOGS("spruce_log"))
for k2 in range(1, 6): put(CRX - k2, cg2 + 5, CRZ, *LOGS("spruce_log"), shape="thinlog")
for y in range(cg2 - 2, cg2 + 5): put(CRX - 5, y, CRZ, "iron_chain", shape="chain")
put(CRX - 5, cg2 - 3, CRZ, "barrel_top", "barrel_side")
mark(qx0 - 3, qz0 - 3, qx1 + 4, qz1 + 3)
npc(qx0 + 10, qz0 + 10, "gray_wool")                             # a miner at work

# ---------------- wolf den (hunting, harder): a rocky outcrop with a cave mouth ----------------
WX2, WZ2 = WOLF
wg = at(WX2, WZ2)
for x in range(WX2 - 9, WX2 + 10):
    for z in range(WZ2 - 9, WZ2 + 6):
        d = math.hypot((x - WX2) / 9, (z - (WZ2 - 3)) / 7)
        if d < 1:
            top = wg + int((1 - d) * 9 + R.random() * 1.5)
            for y in range(at(x, z) + 1, top + 1): put(x, y, z, R.choice(["stone", "andesite", "mossy_cobblestone", "stone", "tuff"]))
for x in range(WX2 - 2, WX2 + 3):                                # cave mouth facing south
    for y in range(wg + 1, wg + 4):
        for z in range(WZ2 - 2, WZ2 + 6): w.b.pop((x, y, z), None)
    for y in range(wg + 1, wg + 4): put(x, y, WZ2 - 3, "black_concrete")
for (x, z) in ((WX2 - 3, WZ2 + 6), (WX2 + 4, WZ2 + 7), (WX2, WZ2 + 9)):
    put(x, at(x, z) + 1, z, "bone_block" if False else "white_terracotta", shape="slab")    # bones
for (x, z) in ((WX2 - 1, WZ2 + 4), (WX2 + 3, WZ2 + 8), (WX2 - 5, WZ2 + 9), (WX2 + 6, WZ2 + 4)):
    put(x, at(x, z) + 1, z, "light_gray_wool", shape="wolf")    # wolves (stand-ins)
mark(WX2 - 10, WZ2 - 10, WX2 + 10, WZ2 + 10)

# ---------------- the pond: jetty and a fisher's hut ----------------
FX2, FZ2 = SHELTER
fg = at(FX2, FZ2)
for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    for y in range(fg + 1, fg + 4): put(FX2 + dx, y, FZ2 + dz, "spruce_planks", shape="fence")
for dx in range(-3, 4):
    for dz in range(-3, 4):
        put(FX2 + dx, fg + 4, FZ2 + dz, "spruce_planks" if abs(dz) < 3 else "dark_oak_planks", shape="slab")
for x in range(FX2 - 1, FX2 + 2):                                 # fish drying rack
    put(x, fg + 2, FZ2 + 4, "spruce_planks", shape="fence_we")
put(FX2 + 1, fg + 1, FZ2, "barrel_top", "barrel_side"); put(FX2 - 1, fg + 1, FZ2 - 1, "spruce_planks", "spruce_trapdoor")
mark(FX2 - 4, FZ2 - 4, FX2 + 4, FZ2 + 5)
jy = int(wl[300, 140])
for x in range(128, 158):
    for z in (299, 300, 301):
        if lake[z, x] or x > 150:
            put(x, jy, z, "spruce_planks", shape="slab_t" if lake[z, x] else "full")
    if x % 5 == 0:
        for z in (299, 301):
            for y in range(hi[z, x], jy): put(x, y, z, *LOGS("stripped_spruce_log"))
put(129, jy + 1, 300, "barrel_top", "barrel_side"); put(130, jy + 1, 299, "lantern", shape="lantern")
npc(133, 300, "cyan_wool")       # the fisher
for (x, z) in ((110, 280), (90, 330), (120, 320), (75, 290), (140, 340)):
    if lake[z, x]: put(x, int(wl[z, x]) + 1, z, "lily_pad", shape="carpet")
# reeds around the shore
for _ in range(220):
    x, z = R.randrange(40, 170), R.randrange(240, 370)
    if not lake[z, x] and any(lake[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) and not used[z, x]:
        for y in range(at(x, z) + 1, at(x, z) + R.randint(2, 3)): put(x, y, z, "short_grass", shape="plant")

# ---------------- hilltop watchtower ruin (viewpoint) ----------------
TX, TZ = 33, 29
tg = at(TX, TZ)
for x in range(TX - 5, TX + 6):
    for z in range(TZ - 5, TZ + 6):
        ring = max(abs(x - TX), abs(z - TZ))
        if ring == 5:
            top = tg + 1 + int(13 - 9 * ((x * 7 + z * 3) % 5 == 0) - 4 * (x > TX and z > TZ) - (x + z) % 3)
            for y in range(tg + 1, top): put(x, y, z, R.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "mossy_cobblestone"]))
        elif ring <= 4:
            put(x, tg, z, R.choice(["mossy_stone_bricks", "cobblestone", "moss_block"]))
for (x, z) in ((TX, TZ + 5), (TX + 5, TZ)):                     # doorway and a window gap
    for y in range(tg + 1, tg + 4): w.b.pop((x, y, z), None)
for k2 in range(8):                                             # spiral stair remains
    a = k2 * .8
    put(TX + round(math.cos(a) * 3), tg + 1 + k2, TZ + round(math.sin(a) * 3), "stone_bricks", shape="slab")
for (x, z) in ((TX + 8, TZ + 3), (TX + 7, TZ + 6), (TX - 7, TZ + 4)):   # fallen stones
    put(x, at(x, z) + 1, z, R.choice(["mossy_stone_bricks", "cracked_stone_bricks"]))
put(TX + 2, tg + 1, TZ - 2, "chest_front" if False else "barrel_top", "barrel_side")     # the reward at the top of the walk
mark(TX - 9, TZ - 9, TX + 9, TZ + 9)
path([(165, 190), (120, 120), (102, 90), (70, 60), (TX + 2, TZ + 7)])

# ---------------- bandit camp in the western woods ----------------
cg = at(62, 158)
for (tx, tz, col) in ((50, 148, "brown_wool"), (70, 146, "red_wool"), (52, 168, "brown_wool"), (74, 166, "white_wool")):
    # A-frame: a thin cloth sheet on each side (rise 1 per block, 3 blocks out from the ridge), ridge pole on top,
    # open dark front, cloth overhangs the back; poles and guy ropes outside
    for dz in range(-3, 4):
        for k in range(3):
            put(tx - 3 + k, cg + 1 + k, tz + dz, col, shape="cloth_e")
            put(tx + 3 - k, cg + 1 + k, tz + dz, col, shape="cloth_w")
        put(tx, cg + 4, tz + dz, *LOGS("spruce_log"), shape="thinlog") if False else put(tx, cg + 4, tz + dz, "spruce_planks", shape="arm_z")
        if dz == 3:
            for (dx, top) in ((-2, 1), (-1, 2), (0, 3), (1, 2), (2, 1)):
                for y in range(cg + 1, cg + 1 + top): put(tx + dx, y, tz + dz, col, shape="shut_s")
    for dx in range(-2, 3):                                      # dark inside seen through the open front
        for y in range(cg + 1, cg + 4 - abs(dx)):
            if abs(dx) < 2 or y == cg + 1: put(tx + dx, y, tz + 2, "black_concrete", shape="shut_s")
    put(tx - 1, cg + 1, tz - 2, "spruce_planks", "spruce_trapdoor", shape="slab")    # bedroll / crate at the mouth
    for y in range(cg + 1, cg + 5):
        put(tx, y, tz - 4, "spruce_planks", shape="fence")
    for dx in (-4, 4): put(tx + dx, cg + 1, tz - 3, "spruce_planks", shape="post")    # guy-rope pegs
put(62, cg + 1, 157, "campfire_log_lit", shape="fire")
for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)): put(62 + dx, cg + 1, 157 + dz, "spruce_log", "spruce_log", shape="slab")
for x in range(42, 83):                                         # sharpened palisade with a gap toward the path
    for z in (140, 176):
        if 58 <= x <= 66 and z == 176: continue
        if R.random() < .85:
            for y in range(at(x, z) + 1, at(x, z) + 4 + (x % 2)): put(x, y, z, *LOGS("spruce_log"), shape="thinlog")
for z in range(140, 177):
    for x in (42, 82):
        if R.random() < .85:
            for y in range(at(x, z) + 1, at(x, z) + 4 + (z % 2)): put(x, y, z, *LOGS("spruce_log"), shape="thinlog")
for (x, z) in ((60, 150), (66, 162), (56, 160), (70, 155)): npc(x, z, "black_wool")
for (x, z) in ((47, 172), (78, 143)): put(x, at(x, z) + 1, z, "barrel_top", "barrel_side")
for y in range(cg + 1, cg + 5): put(62, y, 178, "dark_oak_planks", shape="fence")
put(62, cg + 5, 178, "red_wool", shape="shut_s"); put(63, cg + 5, 178, "black_wool", shape="shut_s")    # warning banner
mark(40, 138, 84, 180)
path([(165, 200), (110, 205), (LOGC[0] + 13, LOGC[1])]); path([(LOGC[0] - 10, LOGC[1] - 8), (62, 180)])

# ---------------- barrow: the dungeon entrance in the south ----------------
DX, DZ = 196, 352
dg = at(DX, DZ)
for x in range(DX - 16, DX + 17):                                # grassy mound
    for z in range(DZ - 18, DZ + 6):
        d = math.hypot((x - DX) / 16, (z - (DZ - 8)) / 13)
        if d < 1:
            top = dg + int((1 - d * d) * 9)
            for y in range(at(x, z) + 1, top + 1):
                put(x, y, z, "grass_block_top" if y == top else "dirt", "grass_block_side" if y == top else "dirt")
# soften the mound with half steps, ring it with standing stones
for x in range(DX - 17, DX + 18):
    for z in range(DZ - 19, DZ + 7):
        y = max((yy for yy in range(dg, dg + 11) if (x, yy, z) in w.b), default=None)
        if y is None or w.b[(x, y, z)][0] != "full": continue
        if any((x + a, y + 1, z + b) in w.b and w.b[(x + a, y + 1, z + b)][0] == "full" for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))) and (x, y + 1, z) not in w.b:
            put(x, y + 1, z, "grass_block_top", "grass_block_side", shape="slab")
for k2 in range(9):
    a = k2 / 9 * math.tau
    sx2, sz2 = round(DX + math.cos(a) * 22), round(DZ - 8 + math.sin(a) * 18)
    if 0 <= sx2 < B and 0 <= sz2 < B and not road[sz2, sx2]:
        for y in range(at(sx2, sz2) + 1, at(sx2, sz2) + 3 + (k2 % 3)):
            put(sx2, y, sz2, R.choice(["mossy_stone_bricks", "stone", "mossy_cobblestone"]))
for x in range(DX - 3, DX + 4):                                   # stone portal
    for y in range(dg + 1, dg + 7):
        if abs(x - DX) == 3 or y >= dg + 5:
            put(x, y, DZ + 1, R.choice(["mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks"]))
        else:
            put(x, y, DZ + 1, "black_concrete")
put(DX - 2, dg + 5, DZ + 2, "stone_bricks", shape="stair_n_t"); put(DX + 2, dg + 5, DZ + 2, "stone_bricks", shape="stair_n_t")
put(DX, dg + 6, DZ + 2, "chiseled_stone_bricks")
for x in (DX - 5, DX + 5):                                        # braziers
    put(x, dg + 1, DZ + 3, "stone_bricks", shape="wall"); put(x, dg + 2, DZ + 3, "campfire_log_lit", shape="fire")
for z in range(DZ + 2, DZ + 8):
    for x in range(DX - 2, DX + 3): put(x, at(x, z), z, R.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"]))
mark(DX - 17, DZ - 19, DX + 17, DZ + 8)
path([(178, 205), (190, 260), (200, 320), (DX, DZ + 8)])

# ---------------- gathering: ore outcrops and herb patches ----------------
for (ox, oz, ore) in ((18, 70, "iron_ore"), (24, 110, "coal_ore"), (120, 40, "copper_ore"), (220, 370, "iron_ore"), (380, 220, "coal_ore")):
    if used[oz, ox]: continue
    g = at(ox, oz)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(1, 4):
                if abs(dx) + abs(dz) + dy < 5 and R.random() < .8:
                    put(ox + dx, g + dy, oz + dz, ore if R.random() < .35 else R.choice(["stone", "andesite", "mossy_cobblestone"]))
    mark(ox - 3, oz - 3, ox + 3, oz + 3)
for (hx2, hz2) in ((120, 250), (200, 300), (100, 210), (340, 190), (260, 140)):
    for _ in range(14):
        x, z = hx2 + R.randint(-5, 5), hz2 + R.randint(-5, 5)
        if not used[z, x] and not road[z, x] and not lake[z, x]:
            put(x, at(x, z) + 1, z, R.choice(["sweet_berry_bush_stage3", "flowering_azalea_leaves", "azalea_leaves"]),
                shape=R.choice(["plant", "slab"]))
# wild flowers scattered in the open
for _ in range(1400):
    x, z = R.randrange(2, B - 2), R.randrange(2, B - 2)
    if used[z, x] or road[z, x] or lake[z, x] or (x, at(x, z) + 1, z) in w.b: continue
    t = w.b.get((x, at(x, z), z))
    if t and t[1] == "grass_block_top":
        put(x, at(x, z) + 1, z, R.choice(["poppy", "dandelion", "cornflower", "short_grass", "short_grass", "short_grass"]), shape="plant")

# ---------------- landmarks: each one a different idea, each visible from far away ----------------
RL = random.Random(91)
STONEW = ["stone_bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "andesite"]


def vines_down(x, y, z, n, face="shut_s"):
    for k in range(n):
        if (x, y - k, z) in w.b: break
        put(x, y - k, z, "vine", shape=face)


# 1. ruined aqueduct: a line of arches striding across the valley, crossing the north road on an intact span
AZ, AX0, AX1 = 126, 104, 228
deck = max(at(x, AZ) for x in range(AX0, AX1 + 1)) + 4
SPAN = 12                                   # pier every 12 blocks (2 wide), 10-wide arch between
broken = {3, 6, 7}                          # spans that have fallen
for x in range(AX0, AX1 + 1):
    i, o = divmod(x - AX0, SPAN)
    pier = o < 2
    if not pier and i in broken: continue
    mid = AX0 + i * SPAN + 1.5 + SPAN / 2
    half = (SPAN - 2) / 2
    under = deck - 1 if pier else deck - 1 - int(round(math.sqrt(max(0, half * half - (x - mid) ** 2)) * .9))
    for z in (AZ - 1, AZ, AZ + 1):
        lo = at(x, z) + 1 if pier else under
        for y in range(lo, deck + 1):
            put(x, y, z, RL.choice(STONEW))
        put(x, deck + 1, z, RL.choice(STONEW) if z != AZ else "water_still", shape="full" if z != AZ else "water")
    if RL.random() < .15:
        vines_down(x, under - (0 if pier else 0), AZ + 2, RL.randint(2, 6))
    if pier and o == 0:
        for z in (AZ - 2, AZ + 2):                              # buttresses
            for y in range(at(x, z) + 1, at(x, z) + 6): put(x, y, z, RL.choice(STONEW))
    mark(x, AZ - 3, x, AZ + 3)
for i in broken:                                                # fallen blocks below the missing spans
    for _ in range(26):
        x = AX0 + i * SPAN + RL.randint(2, SPAN - 1); z = AZ + RL.randint(-5, 5)
        if 0 <= z < B and not road[z, x]:
            put(x, at(x, z) + 1, z, RL.choice(STONEW + ["mossy_cobblestone"]))
            if RL.random() < .3: put(x, at(x, z) + 2, z, RL.choice(STONEW), shape="slab")
for k in range(3):                                              # broken stubs of the missing spans
    for i in broken:
        for z in (AZ - 1, AZ + 1):
            x = AX0 + i * SPAN + 2 + k
            for y in range(deck - 2 + k, deck + 1): put(x, y, z, RL.choice(STONEW))

# 2. the beast's bones: a huge ribcage half sunk in the downs, its skull toward the road
BX, BZ, BL = 232, 42, 34
bg = at(BX, BZ)
BONE = ("bone_block_top", "bone_block_side")
for t in range(BL):                                             # spine, sagging into the ground at the tail
    x = BX + t; y = bg + 1 + int(5 * math.sin(math.pi * min(1, t / (BL * .8))))
    for dy in (0, 1): put(x, y + dy, BZ, *BONE)
    if t % 4 == 1 and 4 < t < BL - 6:                           # a pair of ribs every 4 blocks
        h = 7 + int(6 * math.sin(math.pi * t / BL))
        for side in (-1, 1):
            if RL.random() < .12: continue                       # a few ribs are snapped off
            for k in range(h * 2):
                a = k / (h * 2) * math.pi * .95
                z = BZ + side * round(math.sin(a) * (h * .9))
                yy = y + 1 - round((1 - math.cos(a)) * h * .9) + h
                if 0 <= z < B: put(x, max(at(x, z) + 1, yy - h), z, *BONE)
sx0 = BX - 6                                                    # skull with tusks
for x in range(sx0, BX):
    for z in range(BZ - 3, BZ + 4):
        for y in range(bg + 1, bg + 6):
            d = ((x - (sx0 + 3)) / 3.2) ** 2 + ((z - BZ) / 3.2) ** 2 + ((y - bg - 3) / 2.6) ** 2
            if d <= 1: put(x, y, z, *BONE)
for z in (BZ - 2, BZ + 2):
    w.b.pop((sx0 + 1, bg + 4, z), None); put(sx0 + 1, bg + 4, z, "black_concrete")   # eye sockets
    for k in range(5): put(sx0 - 1 - k // 2, bg + 1 + k, z + (-1 if z < BZ else 1) * (k // 3), *BONE)   # tusks
mark(BX - 8, BZ - 14, BX + BL + 2, BZ + 14)

# 3. natural stone arch over the south path
NX0, NX1, NZ = 180, 206, 283
ng = max(at(x, NZ) for x in range(NX0, NX1 + 1))
top = ng + 18
for x in range(NX0, NX1 + 1):
    c = (x - (NX0 + NX1) / 2) / ((NX1 - NX0) / 2)
    inner = ng + 1 + int(round((1 - (c / .78) ** 2) ** .5 * 12)) if abs(c) < .78 else at(x, NZ) + 1
    outer = top - int(abs(c) ** 3 * 4)
    for z in range(NZ - 3, NZ + 4):
        wobble = int(2 * math.sin(x * .7 + z))
        for y in range(max(inner if abs(c) < .78 else at(x, z) + 1, 0), outer + wobble):
            put(x, y, z, RL.choice(["stone", "andesite", "stone", "tuff", "mossy_cobblestone", "dripstone_block"]))
        if RL.random() < .5: put(x, outer + wobble, z, RL.choice(["moss_block", "grass_block_top"]), "grass_block_side")
for _ in range(14):
    x = RL.randint(NX0 + 6, NX1 - 6); c = (x - (NX0 + NX1) / 2) / ((NX1 - NX0) / 2)
    vines_down(x, ng + int(round((1 - (c / .78) ** 2) ** .5 * 12)), NZ + 2, RL.randint(2, 7))
mark(NX0 - 2, NZ - 5, NX1 + 2, NZ + 5)

# 4. the giant's sword: driven into a knoll, cracked earth around it
SWX, SWZ = 248, 352
sg = at(SWX, SWZ)
for dx in range(-8, 9):                                         # cracked crater rim
    for dz in range(-8, 9):
        d = math.hypot(dx, dz)
        if d < 8:
            x, z = SWX + dx, SWZ + dz
            put(x, at(x, z), z, RL.choice(["coarse_dirt", "gravel", "stone", "coarse_dirt", "andesite"]))
            if 4 < d < 7 and RL.random() < .35: put(x, at(x, z) + 1, z, RL.choice(["stone", "cobblestone", "coarse_dirt"]))
blade_len = 24
for k in range(blade_len):                                     # blade, leaning a little toward the east
    y = sg + 1 + k; ox = round(k * .18)
    for bx in (-1, 0, 1):
        put(SWX + bx + ox, y, SWZ, "iron_block" if bx == 0 or k % 5 else "smooth_stone")
    if k < 3:
        put(SWX + ox, y, SWZ - 1, "iron_block"); put(SWX + ox, y, SWZ + 1, "iron_block")
gy0 = sg + 1 + blade_len; gox = round(blade_len * .18)
for bx in range(-6, 7):                                         # crossguard
    put(SWX + bx + gox, gy0, SWZ, "gold_block")
    if abs(bx) > 4: put(SWX + bx + gox, gy0 + 1, SWZ, "gold_block")
for k in range(1, 8):                                           # grip and pommel
    put(SWX + gox + round(k * .18), gy0 + k, SWZ, "dark_oak_log_top", "dark_oak_log")
put(SWX + gox + 1, gy0 + 8, SWZ, "amethyst_block"); put(SWX + gox + 1, gy0 + 9, SWZ, "gold_block", shape="slab")
for k in range(5): put(SWX + gox - 3 + round(k * .18), gy0 - 1 - k, SWZ + 1, "red_wool", shape="shut_s")   # a torn cloth tied to the guard
mark(SWX - 9, SWZ - 9, SWX + 9, SWZ + 9)

# ---------------- the great tree: a landmark seen from the road, the old forest grows out from it ----------------
import greattree
GTX, GTZ = 300, 234
greattree.build(w, put, at, B, road, GTX, GTZ, mark)

# ---------------- woods: west and south edges, single oaks in the open ----------------
forest = np.asarray(Image.fromarray(np.random.default_rng(41).random((B // 30 + 1, B // 30 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
west = np.clip((140 - np.arange(B)[None, :]) / 90, 0, 1) * np.ones((B, 1))
south = np.clip((np.arange(B)[:, None] - 330) / 50, 0, 1) * np.ones((1, B))
dens = np.clip((forest - .45) * 1.4 + west * .8 + south * .6, 0, 1) ** 1.5 * .045 + .0008
_zz, _xx = np.mgrid[0:B, 0:B]
_band = np.zeros((B, B))
for (ax, az, bx, bz) in ((300, 234, 352, 288), (352, 288, 345, 345), (300, 234, 390, 230)):
    _ab = np.array([bx - ax, bz - az], float); _L2 = _ab @ _ab
    _t = np.clip(((_xx - ax) * _ab[0] + (_zz - az) * _ab[1]) / _L2, 0, 1)
    _d = np.hypot(_xx - ax - _t * _ab[0], _zz - az - _t * _ab[1])
    _band = np.maximum(_band, np.clip(1 - _d / (26 + 10 * forest), 0, 1))
dens = np.maximum(dens, _band ** 1.2 * .055)
OLD = np.hypot(_xx - 300, _zz - 234)
for z in range(3, B - 3):
    for x in range(3, B - 3):
        if R.random() < dens[z, x]:
            if OLD[z, x] < 90 and _band[z, x] > .2 and R.random() < .35:
                hb_big(x, z)
            else:
                oak(x, z, "birch" if R.random() < .3 else "oak")
# plaza path to farms and pond
path([(178, 205), (230, 255), (280, 290)])
path([(178, 205), (165, 285)])
path([(240, 150), (280, 100), (SX2 - 7, SZ2 + 3)])
path([(300, 330), (330, 345), (WX2, WZ2 + 9)])




# ---------------- mob spawn areas: plain field, monsters wander in patches (stand-ins: coloured bodies) ----------------
SPAWNS = [  # (name, level, centre x, z, radius, count, colour)
    ("野ウサギ", "Lv1-2", 235, 150, 38, 14, "white_wool"),
    ("イノシシ", "Lv3-5", 305, 75, 62, 22, "brown_wool"),
    ("カエル", "Lv2-4", 115, 290, 55, 14, "lime_wool"),
    ("森ゴブリン", "Lv4-6", 85, 105, 48, 16, "green_wool"),
    ("オオカミ", "Lv5-7", 330, 320, 48, 16, "light_gray_wool"),
    ("盗賊", "Lv6-8", 62, 158, 28, 0, "black_wool"),
    ("スケルトン", "Lv7-9", 196, 342, 26, 9, "white_terracotta"),
]


MOB_SHAPE = {"white_wool": "rabbit", "brown_wool": "boar", "lime_wool": "frog", "light_gray_wool": "wolf"}


def mob(x, z, colour):
    if not (2 <= x < B - 2 and 2 <= z < B - 2) or road[z, x] or lake[z, x] or slope[z, x] > 1.1: return False
    y = at(x, z) + 1
    if (x, y, z) in w.b or (x, y + 1, z) in w.b: return False
    if colour in MOB_SHAPE:
        put(x, y, z, colour, shape=MOB_SHAPE[colour])
    else:                                    # goblins and skeletons stand upright
        put(x, y, z, colour, shape="npc_low"); put(x, y + 1, z, colour, shape="npc_high")
    return True


for (_n, _lv, sx, sz, rad, cnt, col) in SPAWNS:
    k = tries = 0
    while k < cnt and tries < cnt * 30:
        tries += 1
        a = R.random() * math.tau; r = rad * math.sqrt(R.random())
        if mob(round(sx + math.cos(a) * r), round(sz + math.sin(a) * r), col): k += 1

if __name__ == "__main__":
    print("blocks", len(w.b))
    W = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    img = iso.render(w, W=W)
    img.save("zone2.png")
    print(img.size)
