"""巨樹の根の丘 — one complete 500 x 500 field (preview only).

Theme: the great tree's roots spread through the whole hill country; what grows from the roots spawns here.
Layout (x = east, z = south):
  road: enters from the harbor (south-west), forks at the waystone rest point; branches north and east
  great tree on the central hill, its root hollow is the dungeon entrance
  giant roots surface across the land and arch over the road
  world boss: 根の主 in a hollow ringed by root walls (north-east)
  grind spots: fungal hollow (west), wolf root-den (south-east), root-swallowed tower (north-west)
  gathering: woodcutting (old forest west/north), quarrying (boulder field south), mining (root-cracked cliff east),
             herbalism (flower meadows + fungal hollow), skinning (deer and boar in the meadows), fishing (pond)
"""
import math
import random
import sys
import numpy as np
from PIL import Image
import iso
import greattree
from greattree import noise3, bark

B = 500
G = 70
SEA = 60
R = random.Random(2026)
w = iso.World()
put = lambda x, y, z, *a, **k: w.set(x, y, z, *a, **k)
LOG = lambda n: (f"{n}_top", n)


def fbm(octaves, base, gain, seed):
    r = np.random.default_rng(seed)
    out = np.zeros((B, B), np.float32); amp = 1.0; tot = 0.0
    for o in range(octaves):
        n = base * 2 ** o
        g = r.random((n + 1, n + 1)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray(g, mode="F").resize((B, B), Image.BICUBIC)); tot += amp; amp *= gain
    return out / tot


zz, xx = np.mgrid[0:B, 0:B].astype(np.float32)


def bump(cx, cz, rx, rz, h, p=2.0):
    d = np.sqrt(((xx - cx) / rx) ** 2 + ((zz - cz) / rz) ** 2)
    t = np.clip(1 - d, 0, 1)
    return h * t ** p * (3 - 2 * t) if p == 0 else h * (t * t * (3 - 2 * t)) ** (p / 2)


# ---------------- places: three levels, low (south) -> mid -> high (north) ----------------
TREE = (240, 118)
REST = (200, 265)
BOSS = (400, 85)
FUNGAL = (95, 235)
WOLF = (415, 392)
TOWER = (80, 75)
POND = (105, 428)
CLIFF_X = 455
BOULDERS = (282, 428)
MEADOW = (330, 258)
LOW, MID, HIGH = 64, 74, 85

# ---------------- terrain ----------------
xs_ = np.arange(B, dtype=np.float32)
n1 = np.asarray(Image.fromarray(np.random.default_rng(5).random((1, 12)).astype(np.float32), mode="F").resize((B, 1), Image.BICUBIC))[0]
n2 = np.asarray(Image.fromarray(np.random.default_rng(6).random((1, 12)).astype(np.float32), mode="F").resize((B, 1), Image.BICUBIC))[0]
B1 = 345 + (n1 - .5) * 30          # low / mid scarp (z, per x)
B2 = 172 + (n2 - .5) * 30          # mid / high scarp
CLIFF_Z = np.broadcast_to(B2[None, :], (B, B))
step1 = np.clip((B1[None, :] - zz) / 3 + .5, 0, 1)    # 1 north of B1
step2 = np.clip((B2[None, :] - zz) / 3 + .5, 0, 1)    # 1 north of B2
H = LOW + (MID - LOW) * step1 + (HIGH - MID) * step2
H += (fbm(5, 3, .5, 1) - .5) * 7 + (fbm(4, 9, .5, 2) - .5) * 3           # gentle rolls inside each level
H += bump(*TREE, 75, 65, 12)                                              # the tree hill
H += bump(*TOWER, 55, 45, 7)                                              # tower knoll
_bd = np.hypot(xx - BOSS[0], zz - BOSS[1])
_floor = float(np.median(H[_bd < 30])) - 3
_t = np.clip((_bd - 28) / 14, 0, 1); _t = _t * _t * (3 - 2 * _t)
H = H * _t + _floor * (1 - _t)                                            # boss hollow: one flat floor
H -= bump(*FUNGAL, 46, 38, 6)                                             # fungal hollow
H -= bump(*POND, 55, 40, 5)                                               # pond basin
H = H.astype(np.float32)

# roads and side paths: every place sits on one; all are graded so the levels connect without a jump
ROADS = [[(0, 452), (55, 440), (120, 392), (170, 330), REST],
         [REST, (215, 225), (226, 185), (222, 150), (205, 60), (215, 0)],
         [REST, (265, 262), (330, 255), (400, 250), (450, 236), (499, 232)]]
PATHS = [[REST, (150, 252), (112, 240)],                                  # fungal hollow
         [(120, 392), (114, 408)],                                        # pond
         [(212, 280), (238, 325), (262, 372), (278, 412)],                # boulder field (down the scarp)
         [(278, 412), (340, 420), (398, 410)],                            # wolf den
         [(330, 255), (372, 214), (398, 196)],                            # foot of the mining scarp
         [(224, 140), (300, 112), (352, 96), (374, 90)],                  # boss arena
         [(218, 112), (160, 96), (108, 84)],                              # tower
         [(110, 240), (80, 300), (70, 345), (90, 398)]]                   # west forest loop: fungal -> woods -> pond


def polyline(pts, n=10):
    out = []
    for a, b in zip(pts, pts[1:]):
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    out.append(pts[-1])
    out = np.array(out, np.float32)
    for _ in range(3):  # chaikin: soft bends
        q = np.empty((len(out) * 2 - 2, 2), np.float32)
        q[0::2] = out[:-1] * .75 + out[1:] * .25
        q[1::2] = out[:-1] * .25 + out[1:] * .75
        out = np.vstack([out[:1], q, out[-1:]])
    return out


def dist_idx(pl):
    d = np.full((B, B), 999.0, np.float32); idx = np.zeros((B, B), np.int32)
    for i, (a, b) in enumerate(zip(pl, pl[1:])):
        ab = b - a; L2 = float(ab @ ab) + 1e-9
        t = np.clip(((xx - a[0]) * ab[0] + (zz - a[1]) * ab[1]) / L2, 0, 1)
        dd = np.hypot(xx - a[0] - t * ab[0], zz - a[1] - t * ab[1])
        m = dd < d; d[m] = dd[m]; idx[m] = i
    return d, idx


def sample(Hm, x, z):
    return float(Hm[int(min(B - 1, max(0, round(z)))), int(min(B - 1, max(0, round(x))))])


road_d = np.full((B, B), 999.0, np.float32)
path_d = np.full((B, B), 999.0, np.float32)
k = np.exp(-np.linspace(-2, 2, 25) ** 2); k /= k.sum()
for kind, pts_list in (("road", ROADS), ("path", PATHS)):
    for pts in pts_list:
        pl = polyline(pts)
        sm = H
        prof = np.array([sample(sm, x, z) for x, z in pl], np.float32)
        prof = np.convolve(np.pad(prof, 6, mode="edge"), np.ones(13) / 13, "valid")
        seg = np.r_[0, np.hypot(*np.diff(pl, axis=0).T)]
        MAXG = .28
        for _ in range(30):
            for i in range(1, len(prof)): prof[i] = np.clip(prof[i], prof[i - 1] - MAXG * seg[i], prof[i - 1] + MAXG * seg[i])
            for i in range(len(prof) - 2, -1, -1): prof[i] = np.clip(prof[i], prof[i + 1] - MAXG * seg[i + 1], prof[i + 1] + MAXG * seg[i + 1])
        d, idx = dist_idx(pl)
        level = prof[np.clip(idx, 0, len(prof) - 1)]
        width, fall = (3.2, 9) if kind == "road" else (1.7, 7)
        t = np.clip(1 - (d - width) / fall, 0, 1); t = t * t * (3 - 2 * t)
        H = (H * (1 - t) + level * t).astype(np.float32)
        if kind == "road": road_d = np.minimum(road_d, d)
        else: path_d = np.minimum(path_d, d)
road = road_d < 3.2
trail = (path_d < 1.7) & ~road
anyway = np.minimum(road_d, path_d)

# the pond
pond_d = np.sqrt(((xx - POND[0]) / 36) ** 2 + ((zz - POND[1]) / 24) ** 2) + (fbm(4, 14, .5, 7) - .5) * .4
lake = (pond_d < 1) & ~road & ~trail
WL = int(np.percentile(H[lake], 25)) if lake.any() else SEA
H = np.where(lake, np.minimum(H, WL - 1 - (1 - pond_d) * 4), H)
# a stream from the tree hill, over both scarps as waterfalls, into the pond
stream_pl = polyline([(262, 150), (272, 205), (252, 245), (236, 300), (205, 345), (165, 375), (128, 412)], 8)
sprof = np.array([sample(H, x, z) for x, z in stream_pl], np.float32)
sprof = np.minimum.accumulate(sprof) - 1.5
st_d, st_i = dist_idx(stream_pl)
stream = (st_d < 1.6) & ~lake & ~road & ~trail
chan = sprof[np.clip(st_i, 0, len(sprof) - 1)]
bank = np.clip(1 - (st_d - 1.6) / 3, 0, 1) * (anyway > 4)
H = np.where(stream, np.minimum(H, chan), H * (1 - bank * .6) + np.minimum(H, chan + 1.5) * bank * .6).astype(np.float32)

hi = np.round(H).astype(int)
gy_, gx_ = np.gradient(H)
slope = np.hypot(gx_, gy_)
used = np.zeros((B, B), bool)
CONTENT = []                      # (x, z, kind) of everything a player can do something at


def at(x, z):
    return int(hi[min(B - 1, max(0, z)), min(B - 1, max(0, x))])


def mark(x0, z0, x1, z1):
    used[max(0, z0):min(B, z1 + 1), max(0, x0):min(B, x1 + 1)] = True


patch = fbm(4, 20, .55, 11)
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        nb = min(hi[max(0, z - 1), x], hi[min(B - 1, z + 1), x], hi[z, max(0, x - 1)], hi[z, min(B - 1, x + 1)])
        lo = h - 6 if x in (0, B - 1) or z in (0, B - 1) else min(nb, h) - 1
        face = slope[z, x] > 1.3
        for y in range(lo, h):
            if face:
                put(x, y, z, ("stone", "andesite", "stone", "tuff", "stone", "cobblestone")[(y // 2 + (x // 9)) % 6])
            else:
                put(x, y, z, "dirt" if h - y <= 2 else "stone")
        if lake[z, x]:
            put(x, h, z, "sand" if WL - h < 2 else "gravel")
            for y in range(h + 1, WL + 1): put(x, y, z, "water_still", shape="water")
            continue
        if stream[z, x]:
            put(x, h - 1, z, "gravel"); put(x, h, z, "water_still", shape="water")
            up = max([hi[z + dz, x + dx] for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                      if 0 <= x + dx < B and 0 <= z + dz < B and stream[z + dz, x + dx]] + [h])
            for y in range(h + 1, up): put(x, y, z, "water_still")          # waterfall down the scarp
            continue
        if road[z, x]:
            t = (x * 7 + z * 13) % 9
            put(x, h, z, *(("dirt_path_top", "dirt_path_side") if t < 6 else ("gravel",) if t < 8 else ("coarse_dirt",)))
            continue
        if trail[z, x]:
            put(x, h, z, *(("dirt_path_top", "dirt_path_side") if (x + z) % 4 else ("coarse_dirt",)))
            continue
        if face:
            put(x, h, z, R.choice(["stone", "andesite", "mossy_cobblestone", "tuff", "moss_block"]))
            continue
        p = patch[z, x]
        top = ("grass_block_top", "grass_block_side") if p > .3 else ("moss_block",) if p > .25 else ("podzol_top", "podzol_side")
        put(x, h, z, *top)
        if slope[z, x] < 1.2:  # half steps so nothing on walkable ground needs a jump
            ups = [hi[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < B and 0 <= z + dz < B]
            if any(u == h + 1 for u in ups):
                put(x, h + 1, z, top[0], top[-1], shape="slab")


# ---------------- shared builders ----------------
def tube(p0, p1, r0, r1, mat=None, follow=False, moss_top=False):
    L = math.dist(p0, p1); n = max(2, int(L * 2))
    for i in range(n + 1):
        f = i / n
        x = p0[0] + (p1[0] - p0[0]) * f; y = p0[1] + (p1[1] - p0[1]) * f; z = p0[2] + (p1[2] - p0[2]) * f
        r = r0 + (r1 - r0) * f
        if follow: y = max(y, at(int(round(x)), int(round(z))) - r * .6)
        ri = int(math.ceil(r))
        for dx in range(-ri, ri + 1):
            for dy in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    if dx * dx + dy * dy + dz * dz <= r * r + .3:
                        px, py, pz = int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz
                        if not (0 <= px < B and 0 <= pz < B) or py < at(px, pz) - 2: continue
                        if moss_top and dy == ri and noise3(px, py, pz, 31, 3) > .45:
                            put(px, py, pz, "moss_block")
                        else:
                            put(px, py, pz, *(mat or bark(px, py, pz)))


def bez(p0, p1, p2, n=10):
    out = []
    for i in range(n + 1):
        t = i / n
        out.append(tuple((1 - t) ** 2 * a + 2 * (1 - t) * t * b + t * t * c for a, b, c in zip(p0, p1, p2)))
    return out


def root_arch(p0, apex, p2, r0, r1, hang=True, follow=True):
    """A giant surfaced root: rises from the ground, arches, dives back in. Moss on its back, vines below.
    The path wobbles and the girth swells and pinches, so it reads as grown rather than placed."""
    pts = bez(p0, apex, p2, 18)
    seed = int(p0[0] * 7 + p0[2] * 13)
    pts = [(x + (noise3(i, 0, 0, seed, 3) - .5) * 4, y + (noise3(i, 1, 0, seed, 3) - .5) * 1.5, z + (noise3(i, 2, 0, seed, 3) - .5) * 4)
           if 0 < i < len(pts) - 1 else (x, y, z) for i, (x, y, z) in enumerate(pts)]
    for i, (a, b) in enumerate(zip(pts, pts[1:])):
        f0, f1 = i / (len(pts) - 1), (i + 1) / (len(pts) - 1)
        sw = lambda f: 1 + .22 * math.sin(f * 11 + seed)
        ra = (r0 + (r1 - r0) * f0) * sw(f0); rb = (r0 + (r1 - r0) * f1) * sw(f1)
        tube(a, b, ra, rb, follow=follow, moss_top=True)
    if hang:
        for (x, y, z) in pts[2:-2]:
            for _ in range(3):
                vx, vz = int(round(x)) + R.randint(-1, 1), int(round(z)) + R.randint(-1, 1)
                vy = int(round(y)) - int(r0 * .8) - 1
                for k2 in range(R.randint(2, 7)):
                    p = (vx, vy - k2, vz)
                    if p in w.b or vy - k2 <= at(vx, vz): break
                    put(*p, "vine", shape="shut_s")


def scatter_plants(cx, cz, rad, density, kinds):
    if rad < 120: CONTENT.append((cx, cz, "plants"))
    for _ in range(int(rad * rad * density)):
        a = R.random() * math.tau; r = rad * math.sqrt(R.random())
        x, z = int(round(cx + math.cos(a) * r)), int(round(cz + math.sin(a) * r))
        if not (0 <= x < B and 0 <= z < B) or road[z, x] or lake[z, x] or stream[z, x] or used[z, x]: continue
        y = at(x, z) + 1
        if (x, y, z) in w.b: continue
        kind = R.choice(kinds)
        put(x, y, z, kind, shape="plant" if kind not in ("moss_block", "leaf_litter", "dark_oak_leaves") else "carpet")


def oak(x, z, kind="oak", big=False):
    if not (2 <= x < B - 3 and 2 <= z < B - 3) or used[z, x] or road[z, x] or lake[z, x] or stream[z, x] or slope[z, x] > 1.0: return
    g = at(x, z)
    leaves, logn = {"oak": ("oak_leaves", "oak_log"), "birch": ("birch_leaves", "birch_log"), "dark": ("dark_oak_leaves", "dark_oak_log"),
                    "spruce": ("spruce_leaves", "spruce_log")}[kind]
    th = R.randint(5, 7) + (4 if big else 0)
    cells = ((0, 0), (1, 0), (0, 1), (1, 1)) if big else ((0, 0),)
    for y in range(g + 1, g + th + 1):
        for dx, dz in cells: put(x + dx, y, z + dz, *LOG(logn))
    if kind == "spruce":
        for y in range(g + 3, g + th + 3):
            r = max(0, int((g + th + 2 - y) / 2.4))
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + (y % 2) and (x + dx, y, z + dz) not in w.b: put(x + dx, y, z + dz, leaves)
    else:
        r = (4.2 if big else 2.6) + R.random() * .8
        for _ in range(3 if big else 2):  # two or three lumps per crown
            ox, oy, oz = R.uniform(-1.5, 1.5), R.uniform(-1, 1), R.uniform(-1.5, 1.5)
            for dx in range(-6, 7):
                for dz in range(-6, 7):
                    for dy in range(-3, 4):
                        q = ((dx - ox) / r) ** 2 + ((dz - oz) / r) ** 2 + ((dy - oy) / (r * .7)) ** 2
                        if q <= .8 + noise3(x + dx, g + dy, z + dz, 41, 1.8) * .4 and (x + dx, g + th + dy, z + dz) not in w.b:
                            put(x + dx, g + th + dy, z + dz, leaves if R.random() > .12 else "oak_leaves")
    mark(x - 2, z - 2, x + 3, z + 3)


def mob(x, z, colour, shape):
    if not (2 <= x < B - 2 and 2 <= z < B - 2) or road[z, x] or lake[z, x] or stream[z, x] or slope[z, x] > 1.2: return False
    y = at(x, z) + 1
    if (x, y, z) in w.b or (x, y + 1, z) in w.b: return False
    if shape == "upright":
        put(x, y, z, colour, shape="npc_low"); put(x, y + 1, z, colour, shape="npc_high")
    else:
        put(x, y, z, colour, shape=shape)
    return True


def spawn(cx, cz, rad, count, colour, shape):
    if rad < 120: CONTENT.append((cx, cz, "mob"))
    k = tries = 0
    while k < count and tries < count * 40:
        tries += 1
        a = R.random() * math.tau; r = rad * math.sqrt(R.random())
        if mob(int(round(cx + math.cos(a) * r)), int(round(cz + math.sin(a) * r)), colour, shape): k += 1


# ---------------- the great tree, its hollow is the dungeon entrance ----------------
greattree.build(w, put, at, B, road, TREE[0], TREE[1], mark, seed=77)
TG = at(*TREE)
for (dx, dz) in ((3, 9), (4, 10), (5, 11)):                       # worn path from the road into the hollow
    pass
# stair down inside the hollow, lit, framed by mossy stone
hx, hz = TREE[0] + 1, TREE[1] + 1
for k2 in range(5):
    for dx in (-1, 0):
        w.b.pop((hx + dx, TG - k2, hz + k2), None)
        put(hx + dx, TG - k2 - 1, hz + k2, "mossy_stone_bricks", shape="stair_s")
for y in range(TG - 5, TG + 1):
    put(hx - 2, y, hz + 2, "mossy_stone_bricks"); put(hx + 1, y, hz + 2, "mossy_stone_bricks")
put(hx - 2, TG + 1, hz + 3, "soul_lantern", shape="lantern"); put(hx + 1, TG + 1, hz + 3, "soul_lantern", shape="lantern")

# ---------------- giant roots surfacing across the land ----------------
TX0, TZ0 = TREE
ROOTS = [((TX0 - 10, TG, TZ0 + 6), (214, TG + 12, 136), (192, at(192, 142), 142), 3.2, 1.6),            # arches over the north road
         ((TX0 + 14, TG - 2, TZ0 - 4), (300, TG + 8, 104), (345, at(345, 95), 95), 2.8, 1.4),            # toward the boss
         ((TX0 - 12, TG - 2, TZ0 + 12), (188, TG + 6, 182), (150, at(150, 214), 214), 2.8, 1.4),         # pours over the scarp
         ((TX0 - 6, TG - 2, TZ0 - 16), (170, TG + 8, 84), (125, at(125, 78), 78), 2.6, 1.2),             # toward the tower
         ((345, at(345, 95) + 1, 95), (372, at(372, 90) + 10, 90), (392, at(392, 58), 58), 1.8, 1.0)]
for (p0, apex, p2, r0, r1) in ROOTS:
    root_arch(p0, apex, p2, r0, r1)
# smaller roots breaking the surface in the meadows
for _ in range(26):
    x, z = R.randrange(30, 470), R.randrange(30, 470)
    if anyway[z, x] < 8 or lake[z, x] or used[z, x]: continue
    a = R.random() * math.tau; L = R.uniform(8, 16)
    p0 = (x, at(x, z) - 1, z); p2 = (x + math.cos(a) * L, at(int(x + math.cos(a) * L), int(z + math.sin(a) * L)) - 1, z + math.sin(a) * L)
    apex = ((p0[0] + p2[0]) / 2, max(p0[1], p2[1]) + R.uniform(2, 4), (p0[2] + p2[2]) / 2)
    if 0 <= int(p2[0]) < B and 0 <= int(p2[2]) < B: root_arch(p0, apex, p2, 1.2, .7, hang=False)

# ---------------- world boss: 根の主, a hollow walled in by roots ----------------
BX, BZ = BOSS
bg = at(BX, BZ)
for x in range(BX - 36, BX + 37):                                 # arena floor: root-bound earth, rings of moss
    for z in range(BZ - 36, BZ + 37):
        d = math.hypot(x - BX, z - BZ)
        if d < 30 and 0 <= x < B and 0 <= z < B:
            for y in range(at(x, z) + 1, at(x, z) + 6): w.b.pop((x, y, z), None)
            n = noise3(x, 0, z, 51, 3)
            put(x, at(x, z), z, "rooted_dirt" if n > .55 else "moss_block" if n > .4 else "coarse_dirt" if n > .25 else "podzol_top", "podzol_side")
mark(BX - 36, BZ - 36, BX + 36, BZ + 36)
for k2 in range(14):                                              # the wall: overlapping root arcs, one gap toward the road
    a0 = k2 / 14 * math.tau
    if abs(math.atan2(math.sin(a0 - 2.6), math.cos(a0 - 2.6))) < .35: continue
    a1 = a0 + math.tau / 14 * 1.3
    p0 = (BX + math.cos(a0) * 31, bg - 1, BZ + math.sin(a0) * 31)
    p2 = (BX + math.cos(a1) * 31, bg - 1, BZ + math.sin(a1) * 31)
    am = (a0 + a1) / 2
    apex = (BX + math.cos(am) * 30, bg + R.uniform(16, 22), BZ + math.sin(am) * 30)
    root_arch(p0, apex, p2, 2.4, 1.8, follow=False)
# the stump at the centre, glowing cracks
for y in range(bg + 1, bg + 6):
    r = 6.5 - (y - bg) * .35
    for dx in range(-8, 9):
        for dz in range(-8, 9):
            d = math.hypot(dx, dz)
            if d <= r * (1 + .12 * math.cos(5 * math.atan2(dz, dx))):
                ring = d > r - 1.3 or y == bg + 5
                crack = noise3(BX + dx, y, BZ + dz, 61, 1.5) > .72
                put(BX + dx, y, BZ + dz, *(("shroomlight",) if crack and ring else bark(BX + dx, y, BZ + dz) if ring else ("stripped_dark_oak_log",)))
for dx in range(-5, 6):
    for dz in range(-5, 6):
        if math.hypot(dx, dz) < 5: put(BX + dx, bg + 6, BZ + dz, "stripped_dark_oak_log", "stripped_dark_oak_log", shape="slab") if math.hypot(dx, dz) > 1 else None
for k2 in range(6):                                               # the boss itself, rising from the stump (stand-in)
    pass
put(BX, bg + 6, BZ, "dark_oak_log", shape="boss")
for _ in range(40):                                               # bones and broken weapons of those who came before
    a = R.random() * math.tau; r = R.uniform(10, 26)
    x, z = int(BX + math.cos(a) * r), int(BZ + math.sin(a) * r)
    if (x, at(x, z) + 1, z) not in w.b:
        put(x, at(x, z) + 1, z, R.choice(["bone_block_top", "bone_block_top", "iron_bars", "spruce_planks"]), shape=R.choice(["slab", "carpet", "post"]))
for _ in range(10):
    a = R.random() * math.tau; r = R.uniform(8, 24)
    x, z = int(BX + math.cos(a) * r), int(BZ + math.sin(a) * r)
    for y in range(at(x, z) + 1, at(x, z) + 3): put(x, y, z, "sea_lantern" if y == at(x, z) + 2 else "dark_oak_log", shape="post")

# ---------------- grind spot: the fungal hollow ----------------
FX, FZ = FUNGAL
for x in range(FX - 50, FX + 51):
    for z in range(FZ - 42, FZ + 43):
        if not (0 <= x < B and 0 <= z < B) or road[z, x] or lake[z, x] or stream[z, x]: continue
        d = math.hypot((x - FX) / 48, (z - FZ) / 40)
        if d < 1:
            n = noise3(x, 0, z, 71, 3)
            g = at(x, z)
            if d < .75 and n > .62:
                put(x, g, z, "water_still", shape="water")               # damp pools
            elif n > .45 or d < .5:
                put(x, g, z, "podzol_top", "podzol_side") if n < .55 else put(x, g, z, "moss_block")


def giant_mushroom(x, z, height, cap_r, red=True):
    g = at(x, z)
    lean = (R.uniform(-1.5, 1.5), R.uniform(-1.5, 1.5))
    top = (x + lean[0], g + height, z + lean[1])
    tube((x, g - 1, z), (x + lean[0] * .4, g + height * .5, z + lean[1] * .4), 1.9, 1.4, mat=("mushroom_stem",))
    tube((x + lean[0] * .4, g + height * .5, z + lean[1] * .4), top, 1.4, 1.2, mat=("mushroom_stem",))
    for k2 in range(5):                                           # root flare
        a = k2 / 5 * math.tau
        tube((x, g + 1.5, z), (x + math.cos(a) * 3.5, g - 1, z + math.sin(a) * 3.5), 1.0, .6, mat=("mushroom_stem",))
    cap = "red_mushroom_block" if red else "brown_mushroom_block"
    ri = int(cap_r) + 1
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            d = math.hypot(dx, dz)
            if d > cap_r: continue
            dome = int(round((1 - (d / cap_r) ** 2) * (cap_r * .45 if red else cap_r * .2)))
            cx2, cz2 = int(round(top[0])) + dx, int(round(top[2])) + dz
            y = int(top[1]) + dome
            put(cx2, y, cz2, cap)
            if d > cap_r - 1.5:
                put(cx2, y - 1, cz2, cap)                          # rim turns down
            else:
                put(cx2, y - 1, cz2, "mushroom_block_inside")      # gills underneath
    mark(x - ri, z - ri, x + ri, z + ri)


for (mx, mz, h, r, red) in ((FX - 10, FZ - 6, 18, 9, True), (FX + 18, FZ + 10, 13, 7, False), (FX - 28, FZ + 16, 11, 6, True),
                             (FX + 6, FZ - 26, 9, 5, False), (FX + 30, FZ - 14, 8, 5, True), (FX - 30, FZ - 18, 7, 4, False)):
    giant_mushroom(mx, mz, h, r, red)
scatter_plants(FX, FZ, 42, .09, ["red_mushroom", "brown_mushroom", "red_mushroom", "fern", "moss_block", "glow_lichen"])
spawn(FX, FZ, 38, 16, "red_mushroom_block", "upright")              # fungal parasites
mark(FX - 40, FZ - 32, FX + 40, FZ + 32)

# ---------------- grind spot: the wolves' root-den ----------------
WX, WZ = WOLF
wg = at(WX, WZ)
for x in range(WX - 16, WX + 17):                                 # rocky knoll
    for z in range(WZ - 14, WZ + 12):
        d = math.hypot((x - WX) / 16, (z - WZ + 2) / 12) + (noise3(x, 0, z, 81, 3) - .5) * .4
        if d < 1:
            top = wg + int((1 - d) * 10)
            for y in range(at(x, z) + 1, top + 1):
                put(x, y, z, R.choice(["stone", "andesite", "mossy_cobblestone", "stone", "tuff", "cobblestone"]))
            if top > wg + 3 and R.random() < .6: put(x, top + 1, z, "moss_block", shape="carpet")
root_arch((WX - 22, wg - 1, WZ - 6), (WX - 2, wg + 16, WZ - 10), (WX + 20, wg - 1, WZ - 2), 3.0, 2.2)
for (cx2, cz2) in ((WX - 4, WZ + 9), (WX + 7, WZ + 7)):            # two den mouths facing south
    for y in range(wg + 1, wg + 4):
        for dx in (-1, 0, 1):
            for dz in range(-6, 3):
                w.b.pop((cx2 + dx, y, cz2 + dz), None)
        for dx in (-1, 0, 1): put(cx2 + dx, y, cz2 - 7, "black_concrete")
for _ in range(14):
    a = R.random() * math.tau; r = R.uniform(10, 20)
    x, z = int(WX + math.cos(a) * r), int(WZ + 10 + math.sin(a) * r * .6)
    if (x, at(x, z) + 1, z) not in w.b and not road[z, x]: put(x, at(x, z) + 1, z, "bone_block_top", shape=R.choice(["slab", "carpet"]))
mark(WX - 22, WZ - 16, WX + 22, WZ + 14)
spawn(WX, WZ + 22, 26, 12, "light_gray_wool", "wolf")

# ---------------- grind spot + viewpoint: the root-swallowed tower ----------------
TX, TZ = TOWER
tg = at(TX, TZ)
for y in range(tg - 2, tg + 26):
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            d = math.hypot(dx, dz)
            if 4.5 < d <= 6:
                broken = y > tg + 14 + int(8 * noise3(TX + dx, 0, TZ + dz, 91, 3)) or (y > tg + 6 and noise3(TX + dx, y, TZ + dz, 92, 2) > .78)
                if not broken: put(TX + dx, y, TZ + dz, R.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "mossy_stone_bricks"]))
for y in range(tg + 1, tg + 4):                                     # ground-level doorway on the south side, kept clear of roots
    for k2 in (-1, 0, 1):
        for dz in range(3, 10): w.b.pop((TX + k2, y, TZ + dz), None)
for (dy, wdx, wdz) in ((3, 0, 6), (9, 6, 0), (15, 0, 6)):           # windows
    for y in range(tg + dy, tg + dy + 3):
        for k2 in (-1, 0, 1):
            w.b.pop((TX + wdx + (k2 if wdx == 0 else 0), y, TZ + wdz + (k2 if wdz == 0 else 0)), None)
for k2 in range(3):                                                # roots spiral up the tower and burst from the top
    a0 = k2 / 3 * math.tau
    prev = (TX + math.cos(a0) * 9, tg - 1, TZ + math.sin(a0) * 9)
    for s in range(1, 9):
        a = a0 + s * .7
        r = 6.6 if s < 8 else 3
        cur = (TX + math.cos(a) * r, tg + s * 3, TZ + math.sin(a) * r)
        tube(prev, cur, 1.6 - s * .1, 1.5 - s * .1, moss_top=True)
        prev = cur
for y in range(tg + 1, tg + 4):                                     # keep the doorway clear of the roots too
    for k2 in (-1, 0, 1):
        for dz in range(3, 10): w.b.pop((TX + k2, y, TZ + dz), None)
for (dx, dz) in ((0, 0), (3, 2), (-2, 3)):                         # a crown of leaves where the roots break through
    for ddx in range(-5, 6):
        for ddz in range(-5, 6):
            for ddy in range(-2, 3):
                if ddx * ddx + ddz * ddz + ddy * ddy * 3 <= 20 and R.random() < .85:
                    p = (TX + dx + ddx, tg + 25 + ddy, TZ + dz + ddz)
                    if p not in w.b: put(*p, R.choice(["oak_leaves", "azalea_leaves", "flowering_azalea_leaves"]))
for k2 in range(14):                                               # spiral stair inside up to the lookout
    a = k2 * .55
    put(TX + round(math.cos(a) * 3.5), tg + k2, TZ + round(math.sin(a) * 3.5), "stone_bricks", shape="slab_t")
put(TX + 1, tg + 15, TZ, "barrel_top", "barrel_side")
mark(TX - 12, TZ - 12, TX + 12, TZ + 12)
spawn(TX, TZ + 16, 20, 10, "lime_wool", "sprite")                 # root sprites guarding the tower

# ---------------- mining: the root-cracked escarpment (east), its face looks south over the road ----------------
for x in range(405, B):
    for z in range(150, 205):
        if slope[z, x] < 1.1 or road[z, x]: continue
        for y in range(at(x, z) - 16, at(x, z) + 1):
            if (x, y, z) not in w.b: continue
            n = noise3(x, y, z, 101, 2.2)
            if n > .78: put(x, y, z, "iron_ore")
            elif n < .14: put(x, y, z, "coal_ore")
            elif .47 < n < .5: put(x, y, z, "copper_ore")
            elif .62 < n < .63: put(x, y, z, "gold_ore")
for xc in (420, 452, 480):                                           # roots pouring over the edge and splitting the face
    zc = int(CLIFF_Z[180, xc])
    top = at(xc, zc - 6)
    root_arch((xc - 4, top - 1, zc - 12), (xc, top + 4, zc - 3), (xc + 3, at(xc + 3, zc + 5) - 1, zc + 5), 1.9, 1.2)
for _ in range(20):                                                  # fallen rubble at the foot
    x = R.randrange(408, 498); z = int(CLIFF_Z[180, x]) + R.randint(4, 9)
    if 0 <= z < B and not road[z, x]: put(x, at(x, z) + 1, z, R.choice(["cobblestone", "stone", "andesite", "coal_ore", "gravel"]), shape=R.choice(["full", "slab"]))
mark(400, 140, B - 1, 200)

# ---------------- quarrying: the boulder field (south) ----------------
QX, QZ = BOULDERS
for _ in range(13):
    a = R.random() * math.tau; r = 42 * math.sqrt(R.random())
    x, z = int(QX + math.cos(a) * r * 1.4), int(QZ + math.sin(a) * r * .8)
    if not (6 <= x < B - 6 and 6 <= z < B - 6) or road_d[z, x] < 6 or used[z, x]: continue
    g = at(x, z); s = R.uniform(4.5, 7.5)
    kind = R.choice([["stone", "andesite", "stone"], ["granite", "stone"], ["tuff", "stone", "andesite"], ["calcite", "andesite"]])
    for dx in range(-9, 10):
        for dz in range(-9, 10):
            for dy in range(-2, 9):
                q = (dx / s) ** 2 + (dz / (s * .8)) ** 2 + (dy / (s * .75)) ** 2
                if q <= .85 + noise3(x + dx, g + dy, z + dz, 111, 2.5) * .35 and g + dy > at(x + dx, z + dz) - 3:
                    layer = kind[(dy + 9) // 2 % len(kind)]
                    put(x + dx, g + dy, z + dz, "moss_block" if dy > s * .55 and noise3(x + dx, dy, z + dz, 112, 2) > .45 else layer)
    for _k in range(6):                                              # small stones around each outcrop
        a2 = R.random() * math.tau; r2 = s + R.uniform(1, 4)
        x2, z2 = int(x + math.cos(a2) * r2), int(z + math.sin(a2) * r2)
        if 0 <= x2 < B and 0 <= z2 < B and not road[z2, x2]: put(x2, at(x2, z2) + 1, z2, R.choice(kind), shape=R.choice(["full", "slab"]))
    mark(x - 9, z - 9, x + 9, z + 9)

# ---------------- herbalism: flower meadows; skinning: deer and boar ----------------
MX, MZ = MEADOW
scatter_plants(MX, MZ, 60, .28, ["poppy", "dandelion", "cornflower", "wildflowers", "short_grass", "short_grass", "fern"])
scatter_plants(300, 392, 40, .18, ["poppy", "dandelion", "wildflowers", "short_grass", "sweet_berry_bush_stage3"])
for (hx2, hz2) in ((310, 235), (355, 262), (338, 300), (300, 385), (150, 260)):     # rare herb nodes: firefly bushes
    for _ in range(6):
        x, z = hx2 + R.randint(-3, 3), hz2 + R.randint(-3, 3)
        if (x, at(x, z) + 1, z) not in w.b and not road[z, x]: put(x, at(x, z) + 1, z, "firefly_bush" if R.random() < .5 else "bush", shape="plant")
spawn(MX, MZ, 55, 12, "brown_wool", "deer")
spawn(320, 140, 40, 8, "brown_wool", "deer")
spawn(260, 390, 45, 10, "dark_oak_planks", "boar")

# ---------------- scattered mobs across the whole field ----------------
spawn(250, 250, 230, 40, "black_wool", "beetle")                  # sap beetles
spawn(250, 250, 230, 24, "lime_wool", "sprite")                   # root sprites

# ---------------- rest point at the fork: waystone, campfire, signpost ----------------
RX, RZ = REST
rg = at(RX, RZ)
for x in range(RX - 7, RX + 8):
    for z in range(RZ - 7, RZ + 8):
        d = math.hypot(x - RX, z - RZ)
        if d < 7 and not road[z, x]:
            put(x, at(x, z), z, R.choice(["cobblestone", "mossy_cobblestone", "gravel", "stone_bricks"]) if d > 5 else "coarse_dirt")
            for y in range(at(x, z) + 1, at(x, z) + 3):
                if w.b.get((x, y, z), ("",))[0] in ("slab", "plant"): w.b.pop((x, y, z), None)
WSX, WSZ = RX - 2, RZ - 2
for dx in range(-1, 2):
    for dz in range(-1, 2):
        put(WSX + dx, rg + 1, WSZ + dz, "chiseled_stone_bricks" if (dx, dz) == (0, 0) else "stone_bricks", shape="full" if (dx, dz) == (0, 0) else "slab")
for y in range(rg + 2, rg + 5): put(WSX, y, WSZ, "polished_deepslate" if y != rg + 3 else "sea_lantern")
put(WSX, rg + 5, WSZ, "sea_lantern", shape="lantern")
put(RX + 3, rg + 1, RZ + 2, "campfire_log_lit", shape="fire")
for (dx, dz) in ((-2, 0), (2, 0), (0, 2)): put(RX + 3 + dx, rg + 1, RZ + 2 + dz, "spruce_log", "spruce_log", shape="slab")
for y in range(rg + 1, rg + 4): put(RX + 5, y, RZ - 4, "spruce_planks", shape="fence")
put(RX + 5, rg + 3, RZ - 5, "lime_wool", shape="arm_z"); put(RX + 6, rg + 3, RZ - 4, "orange_wool", shape="arm_x")
put(RX + 4, rg + 2, RZ - 4, "light_blue_wool", shape="arm_x"); put(RX + 5, rg + 4, RZ - 4, "lantern", shape="lantern")
mark(RX - 8, RZ - 8, RX + 8, RZ + 8)
# bridge where the road crosses the stream
for x in range(B):
    for z in range(B):
        if (road[z, x] or trail[z, x]) and st_d[z, x] < 2.5:
            put(x, at(x, z), z, "spruce_planks")
            if road[z, x] and road_d[z, x] > 2.4: put(x, at(x, z) + 1, z, "spruce_planks", shape="fence")


# ---------------- main places count as content ----------------
for p in (TREE, BOSS, FUNGAL, WOLF, TOWER, REST, BOULDERS, MEADOW, POND):
    CONTENT.append((p[0], p[1], "place"))
for xc in range(410, 499, 12):
    CONTENT.append((xc, int(B2[xc]) + 4, "ore"))


# ---------------- fill the gaps: small things worth walking to, all from the theme ----------------
def sap_pool(x, z):           # 樹液溜まり: amber sap pooled in a ring of roots; beetles feed on it (gathering: sap)
    g = at(x, z)
    for k2 in range(5):
        a0 = k2 / 5 * math.tau + R.random() * .4
        p0 = (x + math.cos(a0) * 6, g - 1, z + math.sin(a0) * 6)
        p2 = (x + math.cos(a0 + 1.3) * 6, g - 1, z + math.sin(a0 + 1.3) * 6)
        apex = (x + math.cos(a0 + .65) * 7, g + R.uniform(2, 4), z + math.sin(a0 + .65) * 7)
        root_arch(p0, apex, p2, 1.1, .8, hang=False)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if dx * dx + dz * dz <= 8 + R.random() * 3:
                put(x + dx, g, z + dz, "honey_block_top", "honey_block_side")
                w.b.pop((x + dx, g + 1, z + dz), None)
    spawn(x, z, 9, 4, "black_wool", "beetle")
    mark(x - 8, z - 8, x + 8, z + 8)


def beetle_mound(x, z):       # 甲虫の巣塚: a dirt mound riddled with holes
    g = at(x, z)
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            d = math.hypot(dx, dz)
            if d < 5:
                top = g + int((1 - d / 5) * 4)
                for y in range(at(x + dx, z + dz) + 1, top + 1):
                    hole = noise3(x + dx, y, z + dz, 131, 1.2) > .8
                    put(x + dx, y, z + dz, "black_concrete" if hole else R.choice(["coarse_dirt", "rooted_dirt", "coarse_dirt", "mud"]))
    spawn(x, z, 10, 5, "black_wool", "beetle")
    mark(x - 6, z - 6, x + 6, z + 6)


def herb_thicket(x, z):       # 薬草の茂み: dense berries, azalea and a rare firefly bush (gathering: herbs)
    for _ in range(40):
        a = R.random() * math.tau; r = 5 * math.sqrt(R.random())
        px, pz = int(round(x + math.cos(a) * r)), int(round(z + math.sin(a) * r))
        if anyway[pz, px] < 2 or lake[pz, px]: continue
        y = at(px, pz) + 1
        if (px, y, pz) in w.b: continue
        kind = R.choice(["sweet_berry_bush_stage3", "azalea_leaves", "flowering_azalea_leaves", "fern", "poppy", "sweet_berry_bush_stage3"])
        put(px, y, pz, kind, shape="slab" if "azalea" in kind else "plant")
    put(x, at(x, z) + 1, z, "firefly_bush", shape="plant")
    CONTENT.append((x, z, "herb")); mark(x - 5, z - 5, x + 5, z + 5)


def ore_outcrop(x, z):        # 小さな露頭: a rock lump with ore (gathering: mining)
    g = at(x, z); s2 = R.uniform(2.5, 3.5)
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            for dy in range(-1, 5):
                if (dx / s2) ** 2 + (dz / s2) ** 2 + (dy / (s2 * .9)) ** 2 <= 1 + noise3(x + dx, dy, z + dz, 141, 1.5) * .3:
                    put(x + dx, g + dy, z + dz, R.choice(["stone", "andesite", "iron_ore", "coal_ore", "stone", "copper_ore"]))
    CONTENT.append((x, z, "ore")); mark(x - 4, z - 4, x + 4, z + 4)


def fallen_root(x, z):        # 倒れた根: a dead root log with mushrooms growing on it (gathering: mushrooms, wood)
    a = R.random() * math.tau; L = R.uniform(10, 15); g = at(x, z)
    p0 = (x - math.cos(a) * L / 2, g + 1, z - math.sin(a) * L / 2); p2 = (x + math.cos(a) * L / 2, g + 1, z + math.sin(a) * L / 2)
    tube(p0, p2, 1.6, 1.2, follow=True, moss_top=True)
    for k2 in range(6):
        f = R.random()
        px, pz = int(round(p0[0] + (p2[0] - p0[0]) * f)), int(round(p0[2] + (p2[2] - p0[2]) * f))
        top = max((yy for (xx2, yy, zz2) in [(px, y, pz) for y in range(g, g + 5)] if (xx2, yy, zz2) in w.b), default=g)
        put(px, top + 1, pz, R.choice(["red_mushroom", "brown_mushroom", "red_mushroom"]), shape="plant")
    CONTENT.append((x, z, "fallen")); mark(x - 8, z - 8, x + 8, z + 8)


def sprite_ring(x, z):        # 根の精の輪: a ring of root sprouts where root sprites gather
    g = at(x, z)
    for k2 in range(9):
        a = k2 / 9 * math.tau
        px, pz = int(round(x + math.cos(a) * 5)), int(round(z + math.sin(a) * 5))
        for y in range(at(px, pz) + 1, at(px, pz) + 2 + k2 % 2): put(px, y, pz, *LOG("mangrove_log"), shape="thinlog")
        put(px, at(px, pz) + 3 + k2 % 2, pz, "flowering_azalea_leaves", shape="slab")
    put(x, g + 1, z, "moss_block"); put(x, g + 2, z, "glow_lichen", shape="carpet")
    spawn(x, z, 9, 4, "lime_wool", "sprite")
    mark(x - 6, z - 6, x + 6, z + 6)


def content_dist():
    pts = np.array([(c[0], c[1]) for c in CONTENT], np.float32)
    d = np.full((B, B), 999.0, np.float32)
    for (cx, cz) in pts:
        d = np.minimum(d, np.hypot(xx - cx, zz - cz))
    return d


FILL = []
cd = content_dist()
cand = [(x, z) for z in range(14, B - 14, 7) for x in range(14, B - 14, 7)]
R.shuffle(cand)
for (x, z) in cand:
    if cd[z, x] < 34 or anyway[z, x] < 6 or lake[z, x] or stream[z, x] or slope[z, x] > .9 or used[z, x]: continue
    lvl = "high" if z < B2[x] - 4 else "low" if z > B1[x] + 4 else "mid"
    near_scarp = min(abs(z - B1[x]), abs(z - B2[x])) < 16
    opts = (["ore"] if near_scarp else []) + {"high": ["sap", "sprite", "fallen", "herb"], "mid": ["sap", "beetle", "herb", "fallen"],
                                              "low": ["beetle", "herb", "fallen", "sprite"]}[lvl]
    CAP = {"sap": 7, "sprite": 10, "beetle": 10, "herb": 26, "fallen": 18, "ore": 14}
    opts = [o for o in opts if sum(1 for f in FILL if f[2] == o) < CAP[o]]
    if not opts: continue
    kind = "ore" if near_scarp and "ore" in opts and R.random() < .6 else R.choice(opts)
    {"sap": sap_pool, "beetle": beetle_mound, "herb": herb_thicket, "ore": ore_outcrop, "fallen": fallen_root, "sprite": sprite_ring}[kind](x, z)
    FILL.append((x, z, kind))
    if kind in ("sap", "beetle", "sprite"): CONTENT.append((x, z, kind))
    cd = np.minimum(cd, np.hypot(xx - x, zz - z))
print("fill", len(FILL), {k: sum(1 for f in FILL if f[2] == k) for k in ("sap", "beetle", "herb", "ore", "fallen", "sprite")})

# ---------------- forests: old dark-oak forest (west, woodcutting), birch on the tree hill, spruce on the east ridge ----------------
forest = fbm(4, 6, .5, 121)
west = np.clip((150 - xx) / 120, 0, 1)
north = np.clip((60 - zz) / 60, 0, 1)
treehill = np.exp(-(((xx - TREE[0]) / 90) ** 2 + ((zz - TREE[1]) / 80) ** 2))
dens = np.clip((forest - .48) * 2 + west * .8 + north * .5 + treehill * .35, 0, 1) ** 1.6 * .035 + .0025
for z in range(4, B - 4):
    for x in range(4, B - 4):
        if R.random() < dens[z, x]:
            if x > CLIFF_X - 4: oak(x, z, "spruce")
            elif west[z, x] > .3 and R.random() < .55: oak(x, z, "dark", big=R.random() < .3)
            elif treehill[z, x] > .4 and R.random() < .4: oak(x, z, "birch")
            else: oak(x, z, "oak", big=R.random() < .08)
scatter_plants(70, 280, 70, .05, ["fern", "short_grass", "brown_mushroom", "leaf_litter"])
scatter_plants(250, 250, 240, .006, ["poppy", "dandelion", "short_grass", "short_grass"])



# ---------------- checks: can every piece of land be reached on foot, and is anything far from content ----------------
from collections import deque
FULL_SOLID = lambda v: v is not None and v[0] == "full" and v[1] not in ("oak_leaves", "dark_oak_leaves", "birch_leaves", "spruce_leaves",
                                                                         "azalea_leaves", "flowering_azalea_leaves", "water_still", "glass")
walk_h = hi.copy()
block = np.zeros((B, B), bool)
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        if lake[z, x]: block[z, x] = True; continue
        a, b2 = w.b.get((x, h + 1, z)), w.b.get((x, h + 2, z))
        if FULL_SOLID(a) and FULL_SOLID(b2): block[z, x] = True
seen = np.zeros((B, B), bool)
start = (452, 1)
q = deque([start]); seen[start] = True
while q:
    z, x = q.popleft()
    for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nz, nx = z + dz, x + dx
        if 0 <= nz < B and 0 <= nx < B and not seen[nz, nx] and not block[nz, nx] and abs(int(hi[nz, nx]) - int(hi[z, x])) <= 1:
            seen[nz, nx] = True; q.append((nz, nx))
land = ~lake & ~block
scenic = slope > 1.3
unreach = land & ~seen & ~scenic
logmask = np.zeros((B, B), bool)
for (x, y, z), v in w.b.items():
    if v[0] == "full" and v[2] in ("oak_log", "dark_oak_log", "birch_log", "spruce_log") and y > hi[z, x] + 1: logmask[z, x] = True
treed = np.zeros((B, B), np.float32)
ys, xs2 = np.nonzero(logmask)
cdist = content_dist()
tdist = np.full((B, B), 999.0, np.float32)
for (tz, tx) in zip(ys[::3], xs2[::3]):
    tdist = np.minimum(tdist, np.hypot(xx - tx, zz - tz))
empty = land & seen & (cdist > 40) & (tdist > 14) & (anyway > 4)
STATS = {"reachable": float((land & seen).sum() / land.sum()), "unreachable_flat": int(unreach.sum()),
         "scenic": float((scenic & land).sum() / land.sum()), "empty": float(empty.sum() / land.sum())}
print("checks", STATS)
img = np.zeros((B, B, 3), np.uint8)
shade = np.clip((hi - 55) / 40, 0, 1)
img[...] = (np.stack([60 + shade * 90, 110 + shade * 90, 60 + shade * 60], -1)).astype(np.uint8)
img[scenic & land] = (128, 124, 118)
img[lake | stream] = (70, 130, 210)
img[anyway < 2.5] = (236, 214, 150)
img[logmask] = (40, 90, 40)
img[empty] = (240, 200, 60)
img[unreach] = (230, 50, 50)
mapimg = Image.fromarray(img).resize((1000, 1000), Image.NEAREST)
from PIL import ImageDraw, ImageFont
dd = ImageDraw.Draw(mapimg)
ff = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 15)
for (cx, cz, kd) in CONTENT:
    dd.ellipse((cx * 2 - 4, cz * 2 - 4, cx * 2 + 4, cz * 2 + 4), fill=(255, 255, 255), outline=(0, 0, 0))
dd.rectangle((10, 10, 360, 120), fill=(16, 18, 28))
for i2, (col, t2) in enumerate((((236, 214, 150), "道"), ((128, 124, 118), "崖（見るだけ）"), ((240, 200, 60), "空白（中身から40以上）"),
                                ((230, 50, 50), "行けない平地"), ((255, 255, 255), "中身のある場所"))):
    dd.rectangle((18, 18 + i2 * 20, 32, 32 + i2 * 20), fill=col)
    dd.text((40, 16 + i2 * 20), t2, fill=(255, 255, 255), font=ff)
mapimg.save("field500-check.png")

LABELS = [(TREE, "大樹（根の洞＝ダンジョン入口）", (140, 230, 140)), (BOSS, "ワールドボス：根の主", (255, 90, 90)),
          (FUNGAL, "狩り場：キノコの窪地", (255, 150, 120)), (WOLF, "狩り場：狼の根穴", (255, 150, 120)),
          (TOWER, "狩り場＋展望：根に飲まれた石塔", (255, 150, 120)), (REST, "休憩所（転移石）", (255, 255, 255)),
          ((455, 175), "採鉱：根の割れた崖", (200, 200, 220)), (BOULDERS, "採石：岩の原", (200, 200, 200)),
          (MEADOW, "植物採取・皮（鹿）：花の原", (255, 200, 230)), (POND, "釣り：池", (120, 190, 255)),
          ((60, 300), "伐採：黒樫の古い森", (170, 220, 120)), ((196, 160), "根のアーチ（街道の上）", (220, 200, 160)),
          ((20, 455), "→ 開拓港", (120, 200, 255)), ((215, 8), "→ 北の地域", (200, 255, 200)), ((488, 200), "→ 東の地域", (200, 255, 200))]

if __name__ == "__main__":
    print("blocks", len(w.b))
    import pickle
    pickle.dump((w.b, hi, LABELS), open("field500.pkl", "wb")); pickle.dump((FILL, STATS), open("field500-fill.pkl", "wb"))
