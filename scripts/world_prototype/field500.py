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
from greattree import noise3, bark, _h
import terrainmat as TM

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
LOW, MID, HIGH = 64, 75, 94

# ---------------- terrain ----------------
xs_ = np.arange(B, dtype=np.float32)
n1 = np.asarray(Image.fromarray(np.random.default_rng(5).random((1, 12)).astype(np.float32), mode="F").resize((B, 1), Image.BICUBIC))[0]
n2 = np.asarray(Image.fromarray(np.random.default_rng(6).random((1, 12)).astype(np.float32), mode="F").resize((B, 1), Image.BICUBIC))[0]
B1 = 345 + (n1 - .5) * 30          # low / mid scarp (z, per x)
B2 = 172 + (n2 - .5) * 30          # mid / high scarp
CLIFF_Z = np.broadcast_to(B2[None, :], (B, B))
jag = (fbm(4, 40, .55, 61) - .5) * 9 + (fbm(3, 90, .5, 62) - .5) * 3          # notches every few blocks
width = 2 + fbm(4, 25, .5, 63) * 5                                              # some faces sheer, some broken into steps
step1 = np.clip((B1[None, :] + jag - zz) / width + .5, 0, 1)    # 1 north of B1
step2 = np.clip((B2[None, :] + jag * .8 - zz) / width + .5, 0, 1)    # 1 north of B2
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
import terrainmat as TM
# context fields for the surface rules: forest-ness (where trees will stand), damp ground (near water), local face extent
_forest = fbm(4, 6, .5, 121)
FOREST_F = np.clip((_forest - .48) * 2 + np.clip((150 - xx) / 120, 0, 1) * .8 + np.clip((60 - zz) / 60, 0, 1) * .5, 0, 1)
_wd = np.minimum(np.where(lake, 0, 99), st_d)
for _ in range(1):
    pass
DAMP_F = np.clip(1 - (_wd - 2) / 6, 0, 1)
pondd = np.sqrt(((xx - POND[0]) / 36) ** 2 + ((zz - POND[1]) / 24) ** 2)
DAMP_F = np.maximum(DAMP_F, np.clip((1.25 - pondd) / .25, 0, 1))
from numpy.lib.stride_tricks import sliding_window_view as _sw
_pad = np.pad(hi, 4, mode="edge")
LOCAL_MIN = _sw(_pad, (9, 9)).min(axis=(2, 3)); LOCAL_MAX = _sw(_pad, (9, 9)).max(axis=(2, 3))
_pad1 = np.pad(H, 2, mode="edge")
LOCAL_AVG = _sw(_pad1, (5, 5)).mean(axis=(2, 3))
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        nb = min(hi[max(0, z - 1), x], hi[min(B - 1, z + 1), x], hi[z, max(0, x - 1)], hi[z, min(B - 1, x + 1)])
        lo = h - 6 if x in (0, B - 1) or z in (0, B - 1) else min(nb, h) - 1
        face = slope[z, x] > 1.3
        fb, ft = LOCAL_MIN[z, x], max(LOCAL_MAX[z, x], LOCAL_MIN[z, x] + 1)
        recess = float(np.clip((LOCAL_AVG[z, x] - H[z, x]) / 3, 0, 1))
        for y in range(lo, h):
            if face or h - y > 2:
                put(x, y, z, TM.cliff_block(x, y, z, (y - fb) / (ft - fb), recess))
            else:
                put(x, y, z, "coarse_dirt" if (h - y == 2 and _h(x, y, z, 5) < .3) else "dirt")
        if lake[z, x]:
            put(x, h, z, TM.bed_block(x, z) if WL - h >= 1 else "grass_block_top", "grass_block_side")
            for y in range(h + 1, WL + 1): put(x, y, z, "water_still", shape="water")
            continue
        if stream[z, x]:
            put(x, h - 1, z, TM.bed_block(x, z)); put(x, h, z, "water_still", shape="water")
            if slope[z, x] > 1.0:                                         # a fall: rough up the pool edge below it
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)):
                    a, b = x + dx, z + dz
                    if 0 <= a < B and 0 <= b < B and not stream[b, a] and not road[b, a]:
                        put(a, hi[b, a], b, R.choice(["gravel", "mossy_cobblestone", "moss_block", "cobblestone"]))
            up = max([hi[z + dz, x + dx] for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                      if 0 <= x + dx < B and 0 <= z + dz < B and stream[z + dz, x + dx]] + [h])
            for y in range(h + 1, up): put(x, y, z, "water_still")          # waterfall down the scarp
            continue
        if road[z, x]:                                                    # worn centre, gravel and coarse dirt toward the verges
            edge = road_d[z, x] / 3.2
            v = TM.noise3(x, 0, z, 331, 2.0) * .7 + _h(x, 1, z, 9) * .3
            name = "dirt_path_top" if v > edge * .7 else ("gravel" if v > edge * .45 else "coarse_dirt")
            put(x, h, z, name, TM.SIDE.get(name, name))
            continue
        if trail[z, x]:
            v = TM.noise3(x, 0, z, 332, 1.6)
            name = "dirt_path_top" if v > .35 else "coarse_dirt"
            put(x, h, z, name, TM.SIDE.get(name, name))
            continue
        if face:                                                          # a ledge on the face: moss and grass collect on top
            n = TM.noise3(x, h, z, 341, 2)
            put(x, h, z, *(("moss_block",) if n > .72 else ("grass_block_top", "grass_block_side") if n > .64 else (TM.cliff_block(x, h, z, (h - fb) / (ft - fb), recess),)))
            continue
        top = TM.surface(x, z, slope[z, x], FOREST_F[z, x], DAMP_F[z, x])
        if 0 < anyway[z, x] - 2 < 2.5 and _h(x, 3, z, 11) < .5: top = ("coarse_dirt", "coarse_dirt")     # trampled verge
        put(x, h, z, *top)
        if slope[z, x] < 1.2:  # half steps so nothing on walkable ground needs a jump
            ups = [hi[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < B and 0 <= z + dz < B]
            if any(u == h + 1 for u in ups):
                put(x, h + 1, z, top[0], top[-1], shape="slab")


# talus: rockfall piles where a face meets the ground below it
from collections import Counter as _C
TALUS = ["gravel", "gravel", "cobblestone", "andesite", "stone", "tuff", "mossy_cobblestone"]
for z in range(2, B - 2):
    for x in range(2, B - 2):
        if slope[z, x] > 1.0 or road_d[z, x] < 4 or path_d[z, x] < 3 or lake[z, x] or stream[z, x]: continue
        up = max(hi[z + dz, x + dx] for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2))
        if up - hi[z, x] < 5: continue                                # only right below a real face
        n = noise3(x, 0, z, 371, 2.5)
        if n < .42: continue
        g = hi[z, x]
        put(x, g, z, R.choice(TALUS))
        if n > .6 and _h(x, 1, z, 52) < .35:                          # a fallen boulder, sometimes two high, mossy on top
            put(x, g + 1, z, R.choice(["cobblestone", "andesite", "stone", "mossy_cobblestone"]))
            if n > .72 and _h(x, 2, z, 53) < .4: put(x, g + 2, z, "moss_block" if _h(x, 3, z, 54) < .5 else "mossy_cobblestone", shape="slab")
        elif _h(x, 4, z, 55) < .15:
            put(x, g + 1, z, "stone", shape="slab")
for z in range(1, B - 1):
    for x in range(1, B - 1):
        if slope[z, x] <= 1.3: continue
        h = hi[z, x]
        for y in range(hi[z + 1, x] + 1, h + 1):                      # the face looking south (toward the camera)
            if (x, y, z + 1) in w.b: continue
            r3 = _h(x, y, z, 56)
            if r3 < .025: put(x, y, z + 1, "glow_lichen", shape="shut_s")
            elif r3 < .04 and y > h - 3: put(x, y, z + 1, "hanging_roots", shape="shut_s")
            elif r3 < .07 and y > h - 2: put(x, y, z + 1, "vine", shape="shut_s")


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
spawn(BX, BZ, 24, 12, "lime_wool", "sprite")                        # root sprites gather around the old stump
spawn(BX, BZ, 24, 8, "black_wool", "beetle")
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


# ---------------- Gaur-style rock pillars: narrow waists, wide overhanging grassy tops, joined by rock bridges ----------------
UPPER = np.full((B, B), -1, np.int32)          # walkable heights above the ground layer (pillar tops, bridges)
ROCK = ["stone", "andesite", "stone", "tuff", "stone", "andesite"]


def sakura_tree(x, z, g, size=1.0):
    """A big weeping sakura: dark leaning trunk, spreading limbs, a wide layered pink crown with long hanging strands."""
    rs = random.Random(x * 9973 + z)
    th = int(5 + 3 * size)
    lean = (rs.uniform(-1.5, 1.5), rs.uniform(-1.5, 1.5))
    tube((x, g, z), (x + lean[0], g + th, z + lean[1]), 1.2 * size + .3, .8 * size + .2, mat=("cherry_log_top", "cherry_log"))
    tops = []
    for k in range(rs.randint(4, 6)):
        a = k / 5 * math.tau + rs.uniform(-.4, .4)
        L = (4 + 2.5 * size) * rs.uniform(.8, 1.1)
        e = (x + lean[0] + math.cos(a) * L, g + th + rs.uniform(1, 3) * size, z + lean[1] + math.sin(a) * L)
        tube((x + lean[0], g + th - 1, z + lean[1]), e, .7 * size + .2, .45, mat=("cherry_log_top", "cherry_log"))
        tops.append(e)
    tops.append((x + lean[0], g + th + 2.5 * size, z + lean[1]))
    rim = []
    for (tx, ty, tz) in tops:
        r = (3.0 + 1.6 * size) * rs.uniform(.85, 1.1)
        ri = int(r) + 1
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                for dy in range(-2, 3):
                    q = (dx / r) ** 2 + (dz / r) ** 2 + (dy / (r * .5)) ** 2
                    if q <= .85 + noise3(tx + dx, ty + dy, tz + dz, 181, 1.6) * .35:
                        p = (int(round(tx)) + dx, int(round(ty)) + dy, int(round(tz)) + dz)
                        if p not in w.b:
                            put(*p, "cherry_leaves" if rs.random() > .08 else "pink_wool")
                        if dy == -2 or q > .7: rim.append(p)
    for p in rs.sample(rim, min(len(rim), int(len(rim) * .3))):          # weeping strands
        for k2 in range(1, rs.randint(2, int(4 + 4 * size))):
            q2 = (p[0], p[1] - k2, p[2])
            if q2 in w.b or q2[1] <= at(p[0], p[2]) + 1: break
            put(*q2, "cherry_leaves", shape="shut_s" if k2 > 2 else "full")
    for _ in range(int(40 * size)):                                     # petals on the ground beneath
        a = rs.random() * math.tau; r = (6 + 4 * size) * math.sqrt(rs.random())
        px, pz = int(round(x + math.cos(a) * r)), int(round(z + math.sin(a) * r))
        if 0 <= px < B and 0 <= pz < B:
            gy2 = UPPER[pz, px] if UPPER[pz, px] >= 0 else at(px, pz)
            if (px, gy2 + 1, pz) not in w.b: put(px, gy2 + 1, pz, "pink_petals", shape="carpet")


def torii(cx, cz, y, ux, uz, half=3, h=6):
    """Myojin torii across a path heading (ux, uz): stone kamebara bases, vermilion hashira, the nuki tie beam running
    past the posts, a dark gakuzuka plaque, the red shimaki and a dark kasagi whose ends turn up."""
    px, pz = -uz, ux
    P = lambda s2: (int(round(cx + px * s2)), int(round(cz + pz * s2)))
    for s2 in (-half, half):
        bx, bz = P(s2)
        put(bx, y + 1, bz, "polished_andesite")
        for yy in range(y + 2, y + h + 1): put(bx, yy, bz, "red_concrete")
    for s2 in range(-half - 1, half + 2):                            # nuki, one block past each post
        bx, bz = P(s2)
        put(bx, y + h - 2, bz, "red_concrete", shape="slab_t")
    bx, bz = P(0)
    put(bx, y + h - 1, bz, "black_concrete"); put(bx, y + h - 1, bz, "gold_block", shape="lantern")    # the plaque
    for s2 in range(-half - 1, half + 2):                            # shimaki
        bx, bz = P(s2); put(bx, y + h, bz, "red_concrete")
    for s2 in range(-half - 2, half + 3):                            # kasagi
        bx, bz = P(s2); put(bx, y + h + 1, bz, "deepslate_tiles")
    for s2 in (-half - 3, half + 3):                                 # the upturned ends
        bx, bz = P(s2); put(bx, y + h + 1, bz, "deepslate_tiles", shape="slab_t"); put(bx, y + h + 2, bz, "deepslate_tiles", shape="slab")


def stone_lantern(x, z, y):
    """Ishidoro: a pedestal, a slim post, the fire box, a wide roof, a jewel on top."""
    put(x, y + 1, z, "polished_andesite")
    put(x, y + 2, z, "stone_bricks", shape="wall")
    put(x, y + 3, z, "polished_andesite", shape="slab")
    put(x, y + 3, z, "lantern", shape="lantern")
    put(x, y + 4, z, "deepslate_tiles")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if (x + dx, y + 4, z + dz) not in w.b: put(x + dx, y + 4, z + dz, "deepslate_tiles", shape="slab")
    put(x, y + 5, z, "polished_andesite", shape="post")


def pavilion(cx, cz, y, r=3, roof="deepslate_tiles"):
    """A small open shrine pavilion: raised stone floor, vermilion posts, a hipped tile roof with upturned eaves, a gold finial."""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            put(cx + dx, y + 1, cz + dz, "polished_andesite" if max(abs(dx), abs(dz)) == r else "spruce_planks", shape="full")
    for dx, dz in ((-r + 1, -r + 1), (r - 1, -r + 1), (-r + 1, r - 1), (r - 1, r - 1)):
        for yy in range(y + 2, y + 6): put(cx + dx, yy, cz + dz, "red_concrete", shape="post")
    for layer in range(3):                                           # roof: three shrinking tiers, the eaves one block past the posts
        rr = r + 1 - layer
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                edge = max(abs(dx), abs(dz)) == rr
                if edge or layer == 2:
                    corner = abs(dx) == rr and abs(dz) == rr
                    put(cx + dx, y + 6 + layer + (1 if corner and layer == 0 else 0), cz + dz, roof, shape="slab" if (edge and layer < 2) else "full")
        for dx in range(-rr + 1, rr):
            for dz in range(-rr + 1, rr):
                put(cx + dx, y + 6 + layer, cz + dz, "dark_oak_planks" if layer < 2 else roof)
    put(cx, y + 9, cz, "gold_block", shape="post")
    put(cx, y + 5, cz, "lantern", shape="lantern")
    put(cx, y + 2, cz, "barrel_top", "barrel_side")                  # offering box
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1): UPPER[cz + dz, cx + dx] = y + 1


PLANTED = []


def mesa(cx, cz, top_y, rb, rt, seed, needle=False, sakura=0.0, clear=0.0):
    sakura = float(sakura)
    LEAF = "cherry_leaves" if sakura >= .5 else "oak_leaves"
    base = min(at(cx + dx, cz + dz) for dx in (-int(rb), 0, int(rb)) for dz in (-int(rb), 0, int(rb))) - 2
    Hm = top_y - base
    for y in range(base, top_y + 1):
        f = (y - base) / Hm
        if f < .45: r = rb + (.6 * rb - rb) * (f / .45)
        elif f < .82: r = .6 * rb + (rt - .6 * rb) * ((f - .45) / .37) ** 1.7
        else: r = rt * (1 - max(0, f - .97) * 3)
        ri = int(r * 1.25) + 2
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                a = math.atan2(dz, dx)
                rr = r * (1 + .22 * (noise3(math.cos(a) * 3 + seed, y * .25, math.sin(a) * 3, seed, 1.0) - .5) * 2)
                d = math.hypot(dx, dz)
                if d > rr: continue
                x, z = cx + dx, cz + dz
                if not (0 <= x < B and 0 <= z < B) or y <= at(x, z) - 1: continue
                if needle:
                    put(x, y, z, "moss_block" if y >= top_y - 1 else TM.cliff_block(x, y, z, f, .2 * (d < rr - 1)))
                elif y == top_y:
                    put(x, y, z, "grass_block_top", "grass_block_side"); UPPER[z, x] = top_y
                elif y >= top_y - 3 and d > rr - 1.6:
                    put(x, y, z, "moss_block" if y < top_y - 1 else ("cherry_leaves" if noise3(x, y, z, 191, 3) < sakura else "oak_leaves"))
                else:
                    n = noise3(x, y, z, seed + 7, 2.2)
                    up_open = y > base + 2 and f > .45                          # the overhang's underside and the waist
                    rec = .5 if .35 < f < .75 else 0.0                               # the waist sits in shadow: darker blocks
                    mossy = TM.moss_streak(x, y, z, top_y) or (f > .8 and n > .62)    # streaks from the lip, the overhang's belly
                    mat = "moss_block" if mossy else TM.cliff_block(x, y, z, f, rec)
                    put(x, y, z, mat)
    for _ in range(int(rt * (10 if not needle else 2))):           # a curtain of vines and leaf drapes from the lip
        a = R.random() * math.tau
        out = rt * (1 + .22 * (noise3(math.cos(a) * 3 + seed, top_y * .25, math.sin(a) * 3, seed, 1.0) - .5) * 2)
        x, z = int(round(cx + math.cos(a) * (out + .6))), int(round(cz + math.sin(a) * (out + .6)))
        if not (0 <= x < B and 0 <= z < B): continue
        drape = R.random() < .35
        for k2 in range(R.randint(2, 6) if drape else R.randint(4, 18)):
            p = (x, top_y - k2, z)
            if p in w.b or p[1] <= at(x, z): break
            pk = R.random() < sakura
            put(*p, ("cherry_leaves" if pk else "oak_leaves") if drape else ("cherry_leaves" if pk else "vine"), shape="full" if drape else "shut_s")
    if needle:
        mark(cx - int(rb) - 3, cz - int(rb) - 3, cx + int(rb) + 3, cz + int(rb) + 3)
        return
    for _ in range(int(rt * (.55 - .3 * sakura)) + 1):               # trees on top: sakura share follows the route gradient (sakura are big, so fewer)
        a = R.random() * math.tau; rr = rt * (clear + (.7 - clear) * math.sqrt(R.random()))
        x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
        if (x, top_y + 1, z) in w.b: continue
        if R.random() < sakura:
            if any(math.hypot(x - tx2, z - tz2) < 13 for (tx2, tz2) in PLANTED): continue
            PLANTED.append((x, z))
            sakura_tree(x, z, top_y, size=R.uniform(.8, 1.2) + sakura * .2)
            continue
        th = R.randint(4, 7)
        for y in range(top_y + 1, top_y + th + 1): put(x, y, z, *LOG("oak_log"))
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(-2, 2):
                    if dx * dx + dz * dz + dy * dy * 2 <= 9 + R.random() * 3 and (x + dx, top_y + th + dy, z + dz) not in w.b:
                        put(x + dx, top_y + th + dy, z + dz, "oak_leaves")
    for _ in range(int(rt * rt * .5)):
        a = R.random() * math.tau; rr = rt * .85 * math.sqrt(R.random())
        x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
        if (x, top_y + 1, z) not in w.b:
            if R.random() < sakura: put(x, top_y + 1, z, "pink_petals", shape="carpet")
            else: put(x, top_y + 1, z, R.choice(["short_grass", "short_grass", "poppy", "dandelion", "fern"]), shape="plant")
    mark(cx - int(rb) - 3, cz - int(rb) - 3, cx + int(rb) + 3, cz + int(rb) + 3)


def rock_bridge(p0, p1, rise=3, width=1.6, sakura=0.0):
    samples = []
    """A natural rock bridge with a grassy walkable deck; the deck height goes into UPPER."""
    L = math.dist((p0[0], p0[2]), (p1[0], p1[2])); n = int(L * 2)
    cpt = lambda t: (int(round(p0[0] + (p1[0] - p0[0]) * t)), int(round(p0[2] + (p1[2] - p0[2]) * t)))
    # the span between leaving the first top and reaching the second: the slope lives only there
    ts = 0.0
    for i in range(n + 1):
        x0_, z0_ = cpt(i / n)
        if UPPER[z0_, x0_] == p0[1]: ts = i / n
        else: break
    te = 1.0
    for i in range(n, -1, -1):
        x0_, z0_ = cpt(i / n)
        if UPPER[z0_, x0_] == p1[1]: te = i / n
        else: break
    for i in range(n + 1):
        t = i / n
        cx, cz = p0[0] + (p1[0] - p0[0]) * t, p0[2] + (p1[2] - p0[2]) * t
        u = min(1, max(0, (t - ts) / max(1e-6, te - ts)))
        deck = int(round(p0[1] + (p1[1] - p0[1]) * u + rise * math.sin(math.pi * u)))
        under = 2 + 5 * (1 - math.sin(math.pi * t)) + 2 * noise3(cx, 0, cz, 151, 3)       # thicker at the ends
        wid = width + .8 * noise3(cx, 1, cz, 152, 4)
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                x, z = int(round(cx + dx)), int(round(cz + dz))
                if not (0 <= x < B and 0 <= z < B): continue
                d = math.hypot(x - cx, z - cz)
                if d > wid + 1.2: continue
                if d <= wid:
                    put(x, deck, z, "grass_block_top", "grass_block_side"); UPPER[z, x] = max(UPPER[z, x], deck)
                    if w.b.get((x, deck + 1, z), ("", "", ""))[1] in ("vine",): w.b.pop((x, deck + 1, z), None)
                for y in range(int(deck - under * (1 - (d / (wid + 1.2)) ** 2)), deck):
                    if y > at(x, z): put(x, y, z, TM.cliff_block(x, y, z, .55 + (y - deck) / 20, .2) if noise3(x, y, z, 153, 2) < .5 else "moss_block")
                if wid < d <= wid + 1.2 and (x, deck, z) not in w.b:
                    put(x, deck, z, "cherry_leaves" if R.random() < sakura else ("azalea_leaves" if (x + z) % 3 else "oak_leaves"))
                if d <= wid and R.random() < sakura * .5 and (x, deck + 1, z) not in w.b: put(x, deck + 1, z, "pink_petals", shape="carpet")
        samples.append((cx, cz, deck, u))
        if i % 7 == 3:
            for k2 in range(R.randint(2, 7)):
                vx, vz = int(round(cx + R.uniform(-1, 1))), int(round(cz + R.uniform(-1, 1)))
                p = (vx, deck - int(under) - k2, vz)
                if p not in w.b and p[1] > at(vx, vz): put(*p, "cherry_leaves" if R.random() < sakura else "vine", shape="shut_s")
    return samples


# (the sakura arena is built right after the pillars)
# giant pillars stepping up and away from the high plateau's edge; each top is reached by a rock bridge from the one before
ex, ez = 300, int(B2[300]) - 7
EDGE_Y = at(ex, ez)
PILLARS = [(300, 222, EDGE_Y + 10, 15, 38, 3), (296, 322, EDGE_Y + 20, 16, 42, 5),
           (372, 408, EDGE_Y + 28, 17, 44, 1), (456, 318, EDGE_Y + 22, 13, 34, 7)]
SAKURA = 2                                                          # the highest pillar is the sakura boss's arena
SAK = [.3, .62, 1.0, .85]                                           # sakura share along the upper route
for k2, (px, pz, ty, rb, rt, sd) in enumerate(PILLARS):
    mesa(px, pz, ty, rb, rt, sd, sakura=SAK[k2], clear=(.66 if k2 == SAKURA else 0))


def link(a, b, rise, sak=0.0):
    (ax, az, ay, _, art, _), (bx, bz, by, _, brt, _) = a, b
    d = math.hypot(bx - ax, bz - az); ux, uz = (bx - ax) / d, (bz - az) / d
    return rock_bridge((ax + ux * art * .84, ay, az + uz * art * .84), (bx - ux * brt * .84, by, bz - uz * brt * .84), rise=rise, sakura=sak), (ux, uz)


BR0 = rock_bridge((ex, EDGE_Y, ez - 2), (PILLARS[0][0], PILLARS[0][2], PILLARS[0][1] - PILLARS[0][4] * .84), rise=0, sakura=.15)
BR1, D1 = link(PILLARS[0], PILLARS[1], 5, .45)
BR2, D2 = link(PILLARS[1], PILLARS[2], 6, .85)
BR3, D3 = link(PILLARS[2], PILLARS[3], 7, .9)
# the sando: torii appear along the route and grow denser toward the boss (one, then a few, then a tunnel of them)
def torii_on(samples, d, every):
    for i2, (cx, cz, deck, u) in enumerate(samples):
        if .08 < u < .92 and i2 % every == 0:
            torii(cx, cz, deck, d[0], d[1], half=3, h=6)
torii_on(BR1, D1, 40)
torii_on(BR2, D2, 18)
P0x, P0z, P0y, _, P0r, _ = PILLARS[0]
for (dx, dz) in ((0, -int(P0r * .6)),):                             # a single small torii marks the start of the sando on the first pillar
    torii(P0x + dx, P0z + dz + 6, P0y, 0, 1, half=2, h=5)
for x in range(ex - 3, ex + 4):                                     # the plateau edge where the first bridge starts
    for z in range(ez - 4, ez + 1):
        UPPER[z, x] = max(UPPER[z, x], at(x, z))

# a spring on the second pillar: a pool on top, spilling over the south rim (toward the road) as a waterfall into a plunge pool
P1x, P1z, P1y, _, P1r, _ = PILLARS[1]
for dx in range(-5, 6):
    for dz in range(-5, 6):
        if dx * dx + dz * dz <= 22:
            put(P1x + 4 + dx, P1y, P1z + 4 + dz, "water_still", shape="water"); put(P1x + 4 + dx, P1y - 1, P1z + 4 + dz, "gravel")
            UPPER[P1z + 4 + dz, P1x + 4 + dx] = -1
fz = P1z + 9
while UPPER[fz + 1, P1x + 4] == P1y: fz += 1                        # walk the channel out to the actual rim
for zz2 in range(P1z + 9, fz + 1):
    for dx in (3, 4, 5):
        put(P1x + dx, P1y, zz2, "water_still", shape="water"); UPPER[zz2, P1x + dx] = -1
fz += 1
gb = at(P1x + 4, fz + 3)
for y in range(gb + 1, P1y + 1):                                    # the fall, three blocks wide, spray near the bottom
    for dx in (3, 4, 5):
        put(P1x + dx, y, fz, "water_still")
        if y < gb + 6: put(P1x + dx, y, fz + 1, "water_still", shape="water")
for dx in range(-8, 9):                                             # plunge pool
    for dz in range(-8, 9):
        if dx * dx + dz * dz <= 50:
            x2, z2 = P1x + 4 + dx, fz + 3 + dz
            g2 = at(x2, z2)
            for y in range(g2 - 2, g2 + 2): w.b.pop((x2, y, z2), None)
            put(x2, g2 - 3, z2, "gravel"); put(x2, g2 - 2, z2, "water_still"); put(x2, g2 - 1, z2, "water_still", shape="water")
            lake[z2, x2] = True
fx = P1x + 4
CONTENT.append((fx, fz, "falls"))

# rewards on the tops: a lookout cairn, a rare herb patch by the spring, a treasure at the far end, a high camp
for (px, pz, ty, rb, rt, sd), what in zip(PILLARS, ("cairn", "herb", "treasure", "camp")):
    if what == "cairn":
        for y in range(ty + 1, ty + 4): put(px + 3, y, pz, R.choice(["mossy_cobblestone", "cobblestone"]))
        put(px + 3, ty + 4, pz, "lantern", shape="lantern")
    elif what == "herb":
        for _ in range(10):
            put(px + R.randint(2, 8), ty + 1, pz + R.randint(-5, 5), "firefly_bush", shape="plant")
    elif what == "treasure":
        pass                                                         # the sakura arena, built below
    else:
        put(px, ty + 1, pz, "campfire_log_lit", shape="fire")
        for (dx, dz) in ((-2, 0), (2, 0), (0, 2)): put(px + dx, ty + 1, pz + dz, "spruce_log", "spruce_log", shape="slab")
    CONTENT.append((px, pz, "pillar"))
def top_spawn(px, pz, ty, rad, count, colour, shape):
    k = tries = 0
    while k < count and tries < count * 30:
        tries += 1
        a = R.random() * math.tau; r = rad * math.sqrt(R.random())
        x, z = int(round(px + math.cos(a) * r)), int(round(pz + math.sin(a) * r))
        if UPPER[z, x] != ty or (x, ty + 1, z) in w.b and w.b[(x, ty + 1, z)][0] not in ("plant",): continue
        w.b.pop((x, ty + 1, z), None)
        if shape == "upright": put(x, ty + 1, z, colour, shape="npc_low"); put(x, ty + 2, z, colour, shape="npc_high")
        else: put(x, ty + 1, z, colour, shape=shape)
        k += 1


def top_herbs(px, pz, ty, n):
    for _ in range(n):
        x, z = px + R.randint(-4, 4), pz + R.randint(-4, 4)
        if UPPER[z, x] == ty and (x, ty + 1, z) not in w.b:
            put(x, ty + 1, z, R.choice(["sweet_berry_bush_stage3", "firefly_bush", "fern", "azalea_leaves"]),
                shape="slab" if "azalea" in w.b.get((x, ty + 1, z), ("", "", ""))[1] else "plant")


def top_rocks(px, pz, ty, ore):
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(1, 4):
                if dx * dx + dz * dz + dy * dy * 1.5 <= 11 and R.random() < .85:
                    put(px + dx, ty + dy, pz + dz, R.choice(["stone", "andesite", "mossy_cobblestone"] + ([ore] if ore else [])))


# content on each top: these are fields in the sky, with their own hunting and gathering
TOPS = [  # sprites + herbs; beetles + sap; deer herd + iron; wolves (strong) + gold
    (PILLARS[0], [("lime_wool", "sprite", 10)], 3, None),
    (PILLARS[1], [("black_wool", "beetle", 8), ("brown_wool", "deer", 6)], 4, "iron_ore"),
    (PILLARS[2], [], 0, None),
    (PILLARS[3], [("red_mushroom_block", "upright", 7)], 3, "copper_ore"),
]
for (px, pz, ty, rb, rt, sd), spawns, herbs, ore in TOPS:
    for (col, shp, n) in spawns:
        top_spawn(px, pz, ty, rt * .75, n, col, shp)
    for k2 in range(herbs):
        a = R.random() * math.tau; r = rt * .6 * math.sqrt(R.random())
        top_herbs(int(px + math.cos(a) * r), int(pz + math.sin(a) * r), ty, 14)
    if ore:
        a = R.random() * math.tau
        top_rocks(int(px + math.cos(a) * rt * .5), int(pz + math.sin(a) * rt * .5), ty, ore)



# ---------------- world boss: the sakura on the highest pillar ----------------
# arena rules (boss-arena design + Japanese arena references): one open, readable circle with nothing inside to snag on;
# the boundary is obvious (raised stone floor, red tamagaki fence); one entrance under a great torii; the landmark
# (the sacred sakura with its shimenawa) stands behind, outside the fight; shrine pavilions and lanterns sit outside the fence.
SX_, SZ_, SY_, _, SR_, _ = PILLARS[SAKURA]
BOSS = (SX_, SZ_)
AR = int(SR_ * .45)
ux2, uz2 = D2                                                       # the direction the visitor arrives from
ent_a = math.atan2(-uz2, -ux2)
FY = SY_ + 1                                                        # arena floor is one step up
for dx in range(-AR - 4, AR + 5):
    for dz in range(-AR - 4, AR + 5):
        d = math.hypot(dx, dz)
        x, z = SX_ + dx, SZ_ + dz
        if UPPER[z, x] != SY_ or d > AR + 3: continue
        for y in range(SY_ + 1, SY_ + 12):
            v = w.b.get((x, y, z), ("", "", ""))
            if v[1] in ("cherry_log", "cherry_log_top", "cherry_leaves", "pink_petals", "pink_wool") or v[0] in ("plant", "carpet"): w.b.pop((x, y, z), None)
        if d <= AR:
            ring = abs(d - round(d / 5) * 5) < .5 and d > 2                  # concentric rings every 5
            ang = math.atan2(dz, dx)
            spoke = min(abs(((ang * 8 / math.tau) % 1) - .5), .5) > .44 and d > 4  # eight radial lines
            if d < 2.6: mat = "gold_block" if d < 1.2 else "polished_diorite"   # the seal at the centre
            elif ring: mat = "polished_andesite"
            elif spoke: mat = "red_nether_bricks"
            else: mat = "stone_bricks" if _h(x, 0, z, 61) > .18 else ("mossy_stone_bricks" if _h(x, 1, z, 62) > .5 else "cracked_stone_bricks")
            put(x, FY, z, mat); put(x, SY_, z, "stone_bricks"); UPPER[z, x] = FY
            if _h(x, 2, z, 63) < .1: put(x, FY + 1, z, "pink_petals", shape="carpet")   # a few petals blown in
        elif d <= AR + 1.2:
            put(x, FY, z, "polished_andesite", shape="slab"); UPPER[z, x] = FY      # a half-step lip all round
        elif d <= AR + 3:
            put(x, SY_, z, "gravel" if _h(x, 3, z, 64) < .7 else "stone_bricks")    # raked gravel band
for k2 in range(int(math.tau * (AR + 2))):                          # tamagaki: a low red fence, open at the entrance
    a = k2 / (AR + 2)
    if abs(math.atan2(math.sin(a - ent_a), math.cos(a - ent_a))) < .32: continue
    x, z = int(round(SX_ + math.cos(a) * (AR + 2))), int(round(SZ_ + math.sin(a) * (AR + 2)))
    put(x, SY_ + 1, z, "red_concrete", shape="fence"); put(x, SY_ + 2, z, "red_concrete", shape="slab")
gx0, gz0 = SX_ + math.cos(ent_a) * (AR + 2), SZ_ + math.sin(ent_a) * (AR + 2)
torii(gx0, gz0, SY_, -math.cos(ent_a), -math.sin(ent_a), half=4, h=9)   # the great torii at the entrance
for k2 in range(16):                                                # lanterns on the gravel band
    a = ent_a + (k2 + .5) / 16 * math.tau
    if abs(math.atan2(math.sin(a - ent_a), math.cos(a - ent_a))) < .4: continue
    stone_lantern(int(round(SX_ + math.cos(a) * (AR + 5))), int(round(SZ_ + math.sin(a) * (AR + 5))), SY_)
for off in (.85, -.85, 2.3, -2.3):                                   # four pavilions outside the fence
    a = ent_a + off
    px2, pz2 = int(round(SX_ + math.cos(a) * (AR + 11))), int(round(SZ_ + math.sin(a) * (AR + 11)))
    if UPPER[pz2, px2] == SY_: pavilion(px2, pz2, SY_)
# the walk from the last bridge to the arena: a tunnel of torii lined with stone lanterns, a stone path
startx, startz = BR2[-1][0], BR2[-1][1]
for k2 in range(0, 40):
    for s2 in (-1, 0, 1):
        x, z = int(round(startx + ux2 * k2 - uz2 * s2)), int(round(startz + uz2 * k2 + ux2 * s2))
        if UPPER[z, x] == SY_ and math.hypot(x - SX_, z - SZ_) > AR + 1.5:
            put(x, SY_, z, "polished_andesite" if s2 == 0 else "stone_bricks")
            for y in range(SY_ + 1, SY_ + 10):
                if w.b.get((x, y, z), ("", "", ""))[1] in ("cherry_log", "cherry_leaves"): w.b.pop((x, y, z), None)
for k2 in range(1, 9):
    cx, cz = startx + ux2 * k2 * 5, startz + uz2 * k2 * 5
    if math.hypot(cx - SX_, cz - SZ_) < AR + 6: break
    torii(cx, cz, SY_ - 1, ux2, uz2, half=2, h=6)
    for s2 in (-4, 4):
        stone_lantern(int(round(cx - uz2 * s2)), int(round(cz + ux2 * s2)), SY_)
# the sacred sakura (goshinboku) behind the arena, opposite the entrance, wrapped in a shimenawa
back = ent_a + math.pi
GX_, GZ_ = int(round(SX_ + math.cos(back) * (AR + 12))), int(round(SZ_ + math.sin(back) * (AR + 12)))
sakura_tree(GX_, GZ_, SY_, size=3.0)
for k2 in range(36):
    a = k2 / 36 * math.tau
    x, z = int(round(GX_ + math.cos(a) * 4.6)), int(round(GZ_ + math.sin(a) * 4.6))
    put(x, SY_ + 4, z, "hay_block_top", "hay_block_side", shape="slab_t")
    if k2 % 4 == 0:
        put(x, SY_ + 3, z, "white_wool", shape="shut_s"); put(x, SY_ + 2, z, "white_wool", shape="shut_s")
stone_lantern(GX_ - 6, GZ_ + 6, SY_); stone_lantern(GX_ + 6, GZ_ + 6, SY_)
put(SX_, FY + 1, SZ_, "pink_wool", shape="boss")                    # the sakura boss (stand-in)
CONTENT.append((SX_, SZ_, "boss"))

# great stone arches over the road where it enters and leaves the zone: scenery gates (rounded rock, nothing to stand on)
def stone_arch(p0, p1, height, r):
    pts = bez(p0, ((p0[0] + p1[0]) / 2, max(p0[1], p1[1]) + height, (p0[2] + p1[2]) / 2), p1, 30)
    for i2, (a, b) in enumerate(zip(pts, pts[1:])):
        f = i2 / (len(pts) - 1)
        rr = r * (1.5 - .6 * math.sin(math.pi * f))
        tube(a, b, rr, rr, mat=None, follow=True)
    for (x, y, z) in pts:
        for dx in range(-int(r) - 2, int(r) + 3):
            for dz in range(-int(r) - 2, int(r) + 3):
                for dy in range(-int(r) - 2, int(r) + 3):
                    v = w.b.get((int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz))
                    if v and v[1] in ("dark_oak_log", "mangrove_log", "stripped_dark_oak_log"):
                        p = (int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz)
                        mat = TM.cliff_block(p[0], p[1], p[2], .7, 0)
                        if noise3(*p, 161, 2.5) > .68: mat = "moss_block"
                        put(*p, mat)
    for (x, y, z) in pts:
        mark(int(x) - 14, int(z) - 14, int(x) + 14, int(z) + 14)
    for (x, y, z) in pts[6:-6:2]:
        for _ in range(2):
            vx, vz = int(round(x)) + R.randint(-2, 2), int(round(z)) + R.randint(-2, 2)
            for k2 in range(1, R.randint(3, 14)):
                p = (vx, int(y) - int(r) - k2, vz)
                if p in w.b or p[1] <= at(vx, vz): break
                put(*p, "vine", shape="shut_s")


stone_arch((22, at(22, 425) - 1, 425), (40, at(40, 474) - 1, 474), 34, 4.5)                  # entering from the harbor
stone_arch((470, at(470, 200) - 1, 200), (470, at(470, 268) - 1, 268), 40, 5)                # leaving east

# rock needles in clusters on the plains: scenery that frames the views
for (sx, sz, h2, r2) in ((150, 470, 48, 5), (172, 452, 36, 3.5), (130, 460, 28, 3), (40, 355, 44, 4.5), (60, 340, 30, 3.5),
                         (470, 470, 52, 5.5), (448, 460, 34, 4), (460, 120, 40, 4), (20, 120, 36, 4), (235, 470, 30, 3.5)):
    if not (5 <= sx < B - 5 and 5 <= sz < B - 5) or anyway[sz, sx] < 9 or used[sz, sx] or lake[sz, sx]: continue
    mesa(sx, sz, at(sx, sz) + h2, r2 * 1.5, r2 * .8, 2, needle=True)


# ---------------- main places count as content ----------------
for p in (TREE, (400, 85), FUNGAL, WOLF, TOWER, REST, BOULDERS, MEADOW, POND):
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
def young_tree(x, z):
    if not (2 <= x < B - 3 and 2 <= z < B - 3) or used[z, x] or anyway[z, x] < 3 or lake[z, x] or stream[z, x] or slope[z, x] > 1.0: return
    g = at(x, z); th = R.randint(2, 4)
    for y in range(g + 1, g + th + 1): put(x, y, z, *LOG("oak_log"))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(-1, 2):
                if dx * dx + dz * dz + dy * dy * 2 <= 4.5 + R.random() * 2 and (x + dx, g + th + dy, z + dz) not in w.b:
                    put(x + dx, g + th + dy, z + dz, R.choice(["oak_leaves", "oak_leaves", "azalea_leaves"]))
    mark(x - 1, z - 1, x + 1, z + 1)


def bush_clump(x, z):
    if not (2 <= x < B - 3 and 2 <= z < B - 3) or used[z, x] or anyway[z, x] < 3 or lake[z, x] or stream[z, x]: return
    for _ in range(R.randint(2, 5)):
        a, b = x + R.randint(-1, 1), z + R.randint(-1, 1)
        y = at(a, b) + 1
        if (a, y, b) not in w.b:
            put(a, y, b, R.choice(["oak_leaves", "azalea_leaves", "flowering_azalea_leaves", "bush"]),
                shape="plant" if R.random() < .25 else ("slab" if R.random() < .4 else "full"))


EDGE_LO, EDGE_HI = .006, .02
for z in range(4, B - 4):
    for x in range(4, B - 4):
        dz_ = dens[z, x]
        if EDGE_LO < dz_ < EDGE_HI:                                   # the woodland edge: low cover in front of the trees
            r2 = R.random()
            if r2 < .004: young_tree(x, z)
            elif r2 < .02: bush_clump(x, z)
            continue
        if R.random() < dens[z, x]:
            if x > CLIFF_X - 4: oak(x, z, "spruce")
            elif west[z, x] > .3 and R.random() < .55: oak(x, z, "dark", big=R.random() < .3)
            elif treehill[z, x] > .4 and R.random() < .4: oak(x, z, "birch")
            else: oak(x, z, "oak", big=R.random() < .08)
scatter_plants(70, 280, 70, .05, ["fern", "short_grass", "brown_mushroom", "leaf_litter"])
# leaf litter under canopies: find ground cells shaded by leaves within 6 blocks above
for (x, y, z), v in list(w.b.items()):
    if v[1] in ("oak_leaves", "dark_oak_leaves", "birch_leaves") and y - at(x, z) <= 9 and _h(x, y, z, 51) < .12:
        g = at(x, z)
        if (x, g + 1, z) not in w.b and w.b.get((x, g, z), ("", "", ""))[1] in ("grass_block_top", "podzol_top", "coarse_dirt", "moss_block"):
            put(x, g + 1, z, "leaf_litter", shape="carpet")
scatter_plants(250, 250, 240, .006, ["poppy", "dandelion", "short_grass", "short_grass"])
# ground cover in clumps and drifts: grass tufts where the clump noise is high, flowers in single-species drifts,
# ferns in shade and damp ground, a few small mossy stones on the hills
FLOWERS = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium"]
for z in range(2, B - 2):
    for x in range(2, B - 2):
        if anyway[z, x] < 3 or lake[z, x] or stream[z, x]: continue
        y = at(x, z) + 1
        if (x, y, z) in w.b: continue
        t2 = w.b.get((x, y - 1, z))
        if not t2: continue
        clump = noise3(x, 0, z, 401, 2.2)
        drift = noise3(x, 0, z, 402, 7.0)
        top = t2[1]
        if top == "grass_block_top":
            if drift > .72 and _h(x, 2, z, 41) < .55:
                put(x, y, z, FLOWERS[int(noise3(x, 0, z, 403, 12) * 6) % 6], shape="plant")
            elif clump > .52 and _h(x, 3, z, 42) < (clump - .45) * 2.2:
                put(x, y, z, "tall_grass_top" if clump > .78 and _h(x, 4, z, 43) < .4 else "short_grass", shape="plant")
            elif FOREST_F[z, x] > .5 and _h(x, 5, z, 44) < .08:
                put(x, y, z, "fern", shape="plant")
        elif top in ("podzol_top", "moss_block", "coarse_dirt"):
            if _h(x, 6, z, 45) < (.14 if top == "podzol_top" else .08):
                put(x, y, z, R.choice(["fern", "fern", "short_grass", "brown_mushroom", "leaf_litter"]), shape="plant" if top != "moss_block" else "plant")
            elif top == "moss_block" and _h(x, 7, z, 46) < .25:
                put(x, y, z, "moss_block", shape="carpet")
        elif top in ("stone", "andesite", "gravel", "cobblestone") and _h(x, 8, z, 47) < .03:
            put(x, y, z, "mossy_cobblestone", shape="slab")



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
# second layer: pillar tops and bridges, entered from reachable ground with a step of at most 1
up_seen = np.zeros((B, B), bool)
q = deque()
for z in range(B):
    for x in range(B):
        if UPPER[z, x] >= 0 and seen[z, x] and abs(int(UPPER[z, x]) - int(hi[z, x])) <= 1:
            up_seen[z, x] = True; q.append((z, x))
while q:
    z, x = q.popleft()
    for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nz, nx = z + dz, x + dx
        if 0 <= nz < B and 0 <= nx < B and not up_seen[nz, nx] and UPPER[nz, nx] >= 0 and abs(int(UPPER[nz, nx]) - int(UPPER[z, x])) <= 1:
            up_seen[nz, nx] = True; q.append((nz, nx))
UP_STATS = (int((UPPER >= 0).sum()), int(up_seen.sum()))
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
         "scenic": float((scenic & land).sum() / land.sum()), "empty": float(empty.sum() / land.sum()),
         "upper_cells": UP_STATS[0], "upper_reached": UP_STATS[1]}
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
img[(UPPER >= 0) & up_seen] = (150, 210, 255)
img[(UPPER >= 0) & ~up_seen] = (230, 50, 50)
mapimg = Image.fromarray(img).resize((1000, 1000), Image.NEAREST)
from PIL import ImageDraw, ImageFont
dd = ImageDraw.Draw(mapimg)
ff = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 15)
for (cx, cz, kd) in CONTENT:
    dd.ellipse((cx * 2 - 4, cz * 2 - 4, cx * 2 + 4, cz * 2 + 4), fill=(255, 255, 255), outline=(0, 0, 0))
dd.rectangle((10, 10, 360, 140), fill=(16, 18, 28))
for i2, (col, t2) in enumerate((((236, 214, 150), "道"), ((128, 124, 118), "崖（見るだけ）"), ((240, 200, 60), "空白（中身から40以上）"),
                                ((230, 50, 50), "行けない場所"), ((150, 210, 255), "柱の上と岩の橋（行ける）"), ((255, 255, 255), "中身のある場所"))):
    dd.rectangle((18, 18 + i2 * 20, 32, 32 + i2 * 20), fill=col)
    dd.text((40, 16 + i2 * 20), t2, fill=(255, 255, 255), font=ff)
mapimg.save("field500-check.png")

LABELS = [((352, 300), "巨大な岩の柱と岩の橋・柱の滝（高台から渡れる）", (150, 210, 255)), ((60, 470), "岩の大アーチ（入口）", (220, 210, 190)), ((455, 290), "岩の大アーチ（出口）", (150, 210, 255)), (TREE, "大樹（根の洞＝ダンジョン入口）", (140, 230, 140)), ((400, 85), "狩り場：根の窪地", (255, 150, 120)), (BOSS, "ワールドボス：桜（いちばん高い柱の上）", (255, 120, 170)),
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

