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


_WX, _WZ = None, None
def bump(cx, cz, rx, rz, h, p=2.0):
    global _WX, _WZ
    if _WX is None: _WX, _WZ = (fbm(3, 6, .5, 901) - .5) * 2, (fbm(3, 6, .5, 902) - .5) * 2
    d = np.sqrt(((xx + _WX * rx * .35 - cx) / rx) ** 2 + ((zz + _WZ * rz * .35 - cz) / rz) ** 2)
    d = d * (1 + (fbm(3, 10, .5, 903 + int(cx)) - .5) * .5)
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
jag = (fbm(4, 40, .55, 61) - .5) * 8 + (fbm(2, 18, .5, 62) - .5) * 26          # notches, plus bays and spurs every 20-30 blocks          # notches every few blocks
width = 2 + fbm(4, 25, .5, 63) * 5                                              # some faces sheer, some broken into steps
GAPS = fbm(3, 5, .5, 64)
width = np.where(GAPS > .56, 2 + (GAPS - .56) * 120, width)                       # here and there the scarp dissolves into a grassy slope
RISE = .45 + fbm(3, 7, .5, 66) * .95                                              # and its height swells and drops along its length
step1 = np.clip((B1[None, :] + jag - zz) / width + .5, 0, 1)    # 1 north of B1
step2 = np.clip((B2[None, :] + jag * .8 - zz) / width + .5, 0, 1)    # 1 north of B2
H = LOW + (MID - LOW) * RISE * step1 + (HIGH - MID) * RISE * step2
H += (fbm(4, 3, .5, 1) - .5) * 7 + (fbm(2, 9, .5, 2) - .5) * 3           # gentle rolls inside each level
H += bump(*TREE, 75, 65, 12)                                              # the tree hill
H += bump(TREE[0] + 38, TREE[1] - 20, 22, 16, 4) + bump(TREE[0] - 45, TREE[1] + 10, 18, 26, 3)   # shoulders: not one perfect dome
H += bump(*TOWER, 55, 45, 7)                                              # tower knoll
_bd = np.hypot(xx - BOSS[0], zz - BOSS[1])
_floor = float(np.median(H[_bd < 30])) - 3
_t = np.clip((_bd - 28) / 14, 0, 1); _t = _t * _t * (3 - 2 * _t)
H = H * _t + _floor * (1 - _t)                                            # boss hollow: one flat floor
H -= bump(*FUNGAL, 46, 38, 6)                                             # fungal hollow
H -= bump(*POND, 55, 40, 5)                                               # pond basin
H = H.astype(np.float32)
import os as _os, erode as _er
if _os.path.exists('h_eroded.npy') and _os.path.getmtime('h_eroded.npy') > _os.path.getmtime('erode.py') and _os.environ.get('REERODE') != '1':
    H = np.load('h_eroded.npy')
else:
    _H0 = H.copy()
    _gy, _gx = np.gradient(H); _sl0 = np.hypot(_gx, _gy)
    H = _er.hydraulic(H, n=140000, steps=70, seed=11, radius=3)
    _d = H - _H0                                                  # keep the gullies and fans, drop the fine rill fur:
    for _ in range(2):                                            # blur the change, and keep less of it on gentle ground
        _p = np.pad(_d, 2, mode="edge"); _d = sum(_p[a:a + B, b:b + B] for a in range(5) for b in range(5)) / 25
    H = _H0 + _d * np.clip(.35 + _sl0 * 2.5, 0, 1.4)
    H = _er.thermal(H, talus=1.6, rate=.2, iters=12)
    np.save('h_eroded.npy', H); np.save('h_raw.npy', _H0)

# roads and side paths: every place sits on one; all are graded so the levels connect without a jump
ROADS = [[(0, 452), (55, 440), (120, 392), (170, 330), REST],
         [REST, (215, 225), (226, 185), (222, 150), (205, 60), (215, 0)],
         [REST, (265, 262), (330, 255), (400, 250), (450, 236), (499, 232)]]
PATHS = [[REST, (150, 252), (112, 240)],                                  # fungal hollow
         [(120, 392), (118, 397)],                                        # pond (stops at the shore)
         [(212, 280), (238, 325), (262, 372), (278, 412)],                # boulder field (down the scarp)
         [(278, 412), (340, 420), (398, 410)],                            # wolf den
         [(330, 255), (372, 214), (398, 196)],                            # foot of the mining scarp
         [(224, 140), (300, 112), (352, 96), (374, 90)],                  # boss arena
         [(218, 112), (160, 96), (108, 84)],                              # tower
         [(110, 240), (80, 300), (70, 345), (90, 398)],                   # west forest loop: fungal -> woods -> pond
         [(224, 170), (231, 148), (240, 137), (249, 131)]]    # to the great tree's hollow (dungeon)


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
        MAXG = .2
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
RW = 1.7 + fbm(3, 10, .5, 341) * 1.6                                            # the road narrows and widens: 2-3 wide, not a 6-wide strip
road = road_d < RW
trail = (path_d < 1.7) & ~road
anyway = np.minimum(road_d, path_d)

# the pond
pond_d = np.sqrt(((xx - POND[0]) / 36) ** 2 + ((zz - POND[1]) / 24) ** 2) + (fbm(4, 14, .5, 7) - .5) * .4
lake = (pond_d < 1) & ~road & ~trail
WL = int(np.percentile(H[lake], 25)) if lake.any() else SEA
H = np.where(lake, np.minimum(H, WL - 1 - np.sqrt(np.clip(1 - pond_d, 0, 1)) * 2.2), H)
# a stream from the tree hill, over both scarps as waterfalls, into the pond
stream_pl = polyline([(262, 150), (272, 205), (252, 245), (236, 300), (205, 345), (165, 375), (128, 412)], 8)
sprof = np.array([sample(H, x, z) for x, z in stream_pl], np.float32)
sprof = np.minimum.accumulate(sprof) - 1.5
st_d, st_i = dist_idx(stream_pl)
stream = (st_d < 1.6) & ~lake & ~road & ~trail
chan = sprof[np.clip(st_i, 0, len(sprof) - 1)]
bank = np.clip(1 - (st_d - 1.6) / 6, 0, 1) * (anyway > 4)           # a wide, gentle bank (was 3 blocks: a wall)
bank = bank * bank * (3 - 2 * bank)
H = np.where(stream, np.minimum(H, chan), H * (1 - bank) + np.minimum(H, chan + 1 + (st_d - 1.6) * .35) * bank).astype(np.float32)
pbank = np.clip((1.35 - pond_d) / .35, 0, 1) * (pond_d >= 1)           # the pond shore slopes into the water
H = np.where(pbank > 0, H * (1 - pbank) + (WL + .4) * pbank, H).astype(np.float32)

# break the onion rings on smooth hillsides: a little mid-scale relief where the ground already slopes, away from roads and water
_gy, _gx = np.gradient(H); _sl = np.hypot(_gx, _gy)
_m = np.clip((_sl - .08) * 6, 0, 1) * np.clip((anyway - 7) / 6, 0, 1) * (~lake) * np.clip((st_d - 4) / 4, 0, 1)
H = (H + (fbm(3, 28, .5, 777) - .5) * 4.0 * _m).astype(np.float32)
# benches: on the knolls the ground rises in flat shelves and short steep risers (where the rock shelves show),
# not in an even onion-ring slope. Bench height varies with a slow noise so the shelves do not line up.
_hill = np.clip(bump(*TOWER, 60, 50, 1.0) * 2.2 + bump(*TREE, 80, 70, 1.0) * 1.6, 0, 1) * np.clip((anyway - 6) / 6, 0, 1) * (~lake)
_step = 3.0 + fbm(2, 8, .5, 778) * 2.5
_ph = fbm(2, 6, .5, 779) * 3
_q = (H + _ph) / _step; _f = _q - np.floor(_q)
_bench = (np.floor(_q) + np.clip((_f - .62) / .38, 0, 1) ** 1.5) * _step - _ph
# (the bench terraces made onion rings worse; off)
# contour warp: on gentle open ground a small mid-scale wobble makes each 1-block contour wander and break into short runs
# with varied tread widths, instead of long parallel rings (terraforming rules: no runs > ~7, no repeated step rhythm)
_gy2, _gx2 = np.gradient(H); _sl2 = np.hypot(_gx2, _gy2)
_wm = np.clip((anyway - 5) / 5, 0, 1) * (~lake) * np.clip((st_d - 4) / 4, 0, 1) * np.clip((.8 - _sl2) / .4, 0, 1)
H = (H + ((fbm(3, 22, .5, 780) - .5) * 2.2 + (fbm(2, 60, .5, 781) - .5) * .9) * _wm).astype(np.float32)
hi = np.round(H).astype(int)
gy_, gx_ = np.gradient(H)
slope = np.hypot(gx_, gy_)
used = np.zeros((B, B), bool)
CONTENT = []                      # (x, z, kind) of everything a player can do something at
LABELS_POI = []
SECRETS = []
def cache(x, y, z, what):
    put(x, y, z, "barrel_top", "barrel_side"); SECRETS.append((x, z, what))


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
_gl = np.maximum(slope, 1e-3)
EXPO = np.clip((-gx_ * .6 - gy_ * .8) / _gl, -1, 1)                  # sun from the south-east: faces turned to it are lit
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        nb = min(hi[max(0, z - 1), x], hi[min(B - 1, z + 1), x], hi[z, max(0, x - 1)], hi[z, min(B - 1, x + 1)])
        lo = h - 6 if x in (0, B - 1) or z in (0, B - 1) else min(nb, h) - 1
        near_water = st_d[z, x] < 8 or pond_d[z, x] < 1.5
        face = slope[z, x] > 1.3 and not near_water
        fb, ft = LOCAL_MIN[z, x], max(LOCAL_MAX[z, x], LOCAL_MIN[z, x] + 1)
        recess = float(np.clip((LOCAL_AVG[z, x] - H[z, x]) / 3, 0, 1))
        for y in range(lo, h):
            if near_water and h - y <= 3:
                put(x, y, z, "dirt" if h - y > 1 or _h(x, y, z, 6) < .7 else "coarse_dirt"); continue
            if face or h - y > 2:
                put(x, y, z, TM.cliff_block(x, y, z, (y - fb) / (ft - fb), recess, EXPO[z, x]))
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
            edge = road_d[z, x] / RW[z, x]                                # 0 centre .. 1 verge
            v = TM.noise3(x, 0, z, 331, 3.0)
            if edge < .45 + (v - .5) * .5: name = "dirt_path_top"           # the trodden line wanders inside the road
            elif edge < .8 + (v - .5) * .4: name = "coarse_dirt"
            else: name = "grass_block_top"                                  # the verge is ragged: turf creeps in
            put(x, h, z, name, TM.SIDE.get(name, "grass_block_side" if name == "grass_block_top" else name))
            continue
        if trail[z, x]:
            v = TM.noise3(x, 0, z, 332, 1.6)
            name = "dirt_path_top" if v > .35 else "coarse_dirt"
            put(x, h, z, name, TM.SIDE.get(name, name))
            continue
        if face:                                                          # a ledge on the face: moss and grass collect on top
            n = TM.noise3(x, h, z, 341, 2)
            put(x, h, z, *(("moss_block",) if n > .84 and recess > .2 else (TM.cliff_block(x, h, z, (h - fb) / (ft - fb), recess, EXPO[z, x]),)))
            continue
        top = TM.surface(x, z, min(slope[z, x], .4) if near_water else slope[z, x], FOREST_F[z, x], DAMP_F[z, x])
        if 0 < anyway[z, x] - 2 < 1.2 and noise3(x, 3, z, 11, 3) > .62: top = ("coarse_dirt", "coarse_dirt")     # trampled verge, in runs
        put(x, h, z, *top)
        if slope[z, x] < 1.2:  # half steps so nothing on walkable ground needs a jump
            ups = [hi[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < B and 0 <= z + dz < B]
            if any(u == h + 1 for u in ups):
                pass


# talus: rockfall piles where a face meets the ground below it
from collections import Counter as _C
WATER_NEAR = np.where(lake | stream, 0, 999).astype(np.float32)
_m = lake | stream
for _step in range(1, 26):                                          # true (chessboard-ish) distance to any water, by dilation
    _m = _m | np.roll(_m, 1, 0) | np.roll(_m, -1, 0) | np.roll(_m, 1, 1) | np.roll(_m, -1, 1)
    WATER_NEAR = np.where(_m & (WATER_NEAR > _step), _step, WATER_NEAR)
# rock shelves breaking through the turf on hillsides: the bones of the hill showing, in a few long bands
nshelf = 0
for z in range(3, B - 3):
    for x in range(3, B - 3):
        if not (.3 < slope[z, x] < 1.0) or anyway[z, x] < 6 or WATER_NEAR[z, x] < 8 or used[z, x]: continue
        n = noise3(x * .6, 0, z * 1.4, 381, 6) if (x // 1) else 0          # bands stretched across the slope
        if True: continue
        g = hi[z, x]
        put(x, g, z, TM.strata(x, g, z))
        if n > .8:
            put(x, g + 1, z, TM.strata(x, g + 1, z) if _h(x, 0, z, 382) > .3 else "mossy_cobblestone")
            if n > .86 and _h(x, 1, z, 383) < .5: put(x, g + 2, z, "moss_block", shape="slab")
        nshelf += 1
print("rock shelves", nshelf)

TALUS = ["gravel", "gravel", "cobblestone", "andesite", "stone", "tuff", "mossy_cobblestone"]
for z in range(2, B - 2):
    for x in range(2, B - 2):
        if slope[z, x] > 1.0 or road_d[z, x] < 4 or path_d[z, x] < 3 or lake[z, x] or stream[z, x] or WATER_NEAR[z, x] < 10: continue
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
    r0, r1 = r0 * .7, max(.55, r1 * .35)
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
    for (ex, ey, ez), rr in ((p0, r0), (p2, r1)):                    # soil heaped where the root enters the ground
        ex, ez = int(round(ex)), int(round(ez))
        for dx in range(-int(rr) - 3, int(rr) + 4):
            for dz in range(-int(rr) - 3, int(rr) + 4):
                d = math.hypot(dx, dz)
                if d < rr + 3 and 0 <= ex + dx < B and 0 <= ez + dz < B and not road[ez + dz, ex + dx]:
                    hgt = int((1 - d / (rr + 3)) * 2)
                    g = at(ex + dx, ez + dz)
                    for k in range(1, hgt + 1):
                        if (ex + dx, g + k, ez + dz) not in w.b:
                            put(ex + dx, g + k, ez + dz, *(("grass_block_top", "grass_block_side") if k == hgt else ("dirt",)))
    if r0 > 1.2 and len(pts) > 8:                                     # one side branch leaving from the middle
        mx, my, mz = pts[len(pts) // 2]
        a = math.atan2(p2[2] - p0[2], p2[0] - p0[0]) + R.choice((-1, 1)) * R.uniform(.7, 1.2)
        L = R.uniform(6, 10)
        tx, tz = mx + math.cos(a) * L, mz + math.sin(a) * L
        if 0 <= int(tx) < B and 0 <= int(tz) < B:
            tube((mx, my, mz), (tx, at(int(tx), int(tz)) - 1, tz), r0 * .45, .5, follow=follow, moss_top=True)
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


def oak(x, z, kind="oak", big=False, form=None, lift=0):
    """Broadleaf/conifer with a form: round, tall, wide, lean, snag. Forms vary the silhouette so a forest is not a field of lollipops."""
    if not (3 <= x < B - 4 and 3 <= z < B - 4) or used[z, x] or road[z, x] or lake[z, x] or stream[z, x] or slope[z, x] > 1.0: return
    g = at(x, z)
    leaves, logn = {"oak": ("oak_leaves", "oak_log"), "birch": ("birch_leaves", "birch_log"), "dark": ("dark_oak_leaves", "dark_oak_log"),
                    "spruce": ("spruce_leaves", "spruce_log")}[kind]
    if kind == "spruce":
        th = R.randint(7, 12)
        for y in range(g + 1, g + th + 1): put(x, y, z, *LOG(logn))
        for y in range(g + 3, g + th + 3):
            r = max(0, int((g + th + 2 - y) / 2.4))
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + (y % 2) and (x + dx, y, z + dz) not in w.b: put(x + dx, y, z + dz, leaves)
        mark(x - 2, z - 2, x + 2, z + 2); return
    form = form or R.choices(["round", "tall", "wide", "lean", "snag"], [4, 3, 3, 2, .4])[0]
    cells = ((0, 0), (1, 0), (0, 1), (1, 1)) if big else ((0, 0),)
    th = {"round": R.randint(5, 7), "tall": R.randint(9, 12), "wide": R.randint(4, 6), "lean": R.randint(6, 8), "snag": R.randint(5, 8)}[form] + (4 if big else 0) + lift
    lx, lz = (R.choice((-1, 1)), R.choice((-1, 1))) if form == "lean" else (0, 0)
    top = (x, z)
    for i, y in enumerate(range(g + 1, g + th + 1)):
        ox = int(lx * i / th * 3); oz = int(lz * i / th * 3)
        for dx, dz in cells: put(x + ox + dx, y, z + oz + dz, *LOG(logn))
        top = (x + ox, z + oz)
    tx, tz = top; ty = g + th
    if form == "snag":                                              # dead tree: two bare limbs, a few leaves at most
        for (dx, dz) in ((1, 0), (-1, 1)):
            for k in range(1, 3): put(tx + dx * k, ty - 2 + k, tz + dz * k, logn.replace("_log", "_wood"))
        mark(x - 1, z - 1, x + 2, z + 2); return
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):              # a couple of short limbs under the crown
        if R.random() < .5: put(tx + dx * 2, ty - 1, tz + dz * 2, logn.replace("_log", "_wood"))
    r = {"round": 2.8, "tall": 2.3, "wide": 4.4, "lean": 3.0}[form] + (1.6 if big else 0) + R.random() * .7 + lift * .45
    sq = {"round": .75, "tall": 1.2, "wide": .45, "lean": .7}[form]
    lumps = [(0, 0, 0)] + [(R.uniform(-1.8, 1.8), R.uniform(-1.5, 1) * (2 if form == "tall" else 1), R.uniform(-1.8, 1.8)) for _ in range(2 if not big else 3)]
    for (ox, oy, oz) in lumps:
        for dx in range(-7, 8):
            for dz in range(-7, 8):
                for dy in range(-4, 5):
                    q = ((dx - ox) / r) ** 2 + ((dz - oz) / r) ** 2 + ((dy - oy) / (r * sq)) ** 2
                    if q <= .8 + noise3(tx + dx, g + dy, tz + dz, 41, 1.8) * .4 and (tx + dx, ty + dy, tz + dz) not in w.b:
                        put(tx + dx, ty + dy, tz + dz, leaves if R.random() > .1 else ("azalea_leaves" if kind == "oak" else leaves))
    mark(x - 3, z - 3, x + 4, z + 4)


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
def surface_root(pts, r0, r1, seed, bury_road=True):
    """A root running out from the tree just under the turf: it heaves up as a mossy hump, sinks back under the grass,
    and comes up again further on, thinner each time. Where it crosses a road or water it stays under (the road was laid over it)."""
    pl = polyline(pts, 6)
    seg = np.r_[0, np.hypot(*np.diff(pl, axis=0).T)]; s_ = np.cumsum(seg); L = float(s_[-1])
    n = int(L * 2)
    for i in range(n + 1):
        d = L * i / n
        j = int(np.searchsorted(s_, d)); j = min(len(pl) - 1, max(1, j))
        t = (d - s_[j - 1]) / max(1e-6, seg[j])
        x = pl[j - 1][0] + (pl[j][0] - pl[j - 1][0]) * t; z = pl[j - 1][1] + (pl[j][1] - pl[j - 1][1]) * t
        f = d / L
        lat = (noise3(d * .08, 0, 0, seed, 1) - .5) * 6 * min(1, f * 4)          # it wanders
        ang = math.atan2(pl[j][1] - pl[j - 1][1], pl[j][0] - pl[j - 1][0]) + math.pi / 2
        x += math.cos(ang) * lat; z += math.sin(ang) * lat
        r = r0 * (1 - f) ** .9 + r1 * f
        xi, zi = int(round(x)), int(round(z))
        if not (1 <= xi < B - 1 and 1 <= zi < B - 1): continue
        g = at(xi, zi)
        if bury_road and (anyway[zi, xi] < 4 or lake[zi, xi] or stream[zi, xi]): continue
        e = math.sin(d / (9 + noise3(d * .05, 3, 0, seed, 1) * 10) + seed) * .5 + .5 # 1 heaved up .. 0 under the turf
        e = max(e, 1 - f * 6)                                                    # always up near the trunk
        if e < .35: continue
        cy = g - r + r * 1.3 * e
        ri = int(math.ceil(r))
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                for dy in range(-ri, ri + 1):
                    if dx * dx + dz * dz + dy * dy > r * r + .3: continue
                    px, py, pz = xi + dx, int(round(cy)) + dy, zi + dz
                    if not (0 <= px < B and 0 <= pz < B): continue
                    gg = at(px, pz)
                    if py < gg: continue
                    top = dx * dx + dz * dz + (dy + 1) ** 2 > r * r + .3
                    if top and noise3(px, py, pz, 31, 2.5) > .5: put(px, py, pz, "moss_block")
                    else: put(px, py, pz, *bark(px, py, pz))
        if e > .5 and r > 1.2:                                                   # torn turf along the heave
            for dx, dz in ((ri + 1, 0), (-ri - 1, 0), (0, ri + 1), (0, -ri - 1)):
                a, b2 = xi + dx, zi + dz
                if 0 <= a < B and 0 <= b2 < B and not road[b2, a] and (a, at(a, b2), b2) in w.b and _h(a, 0, b2, seed) < .5:
                    put(a, at(a, b2), b2, "rooted_dirt")
                    if _h(a, 1, b2, seed) < .3 and (a, at(a, b2) + 1, b2) not in w.b: put(a, at(a, b2) + 1, b2, "fern", shape="plant")


# one great root arches over the north road as a gate; the rest run out under the turf, surfacing and sinking
root_arch((TX0 - 10, TG, TZ0 + 6), (214, TG + 12, 136), (192, at(192, 142), 142), 3.2, 1.6)
RR = [[(TX0 + 12, TZ0 - 2), (290, 108), (345, 95), (392, 70)],                 # toward the boss hollow
      [(TX0 - 10, TZ0 + 12), (196, 168), (165, 205), (148, 225)],             # down the scarp
      [(TX0 - 8, TZ0 - 12), (195, 92), (150, 84), (118, 80)],                 # toward the tower
      [(TX0 + 10, TZ0 + 12), (275, 160), (300, 190)],
      [(TX0 + 4, TZ0 - 14), (255, 70), (270, 35)],
      [(TX0 - 14, TZ0 + 2), (185, 125), (160, 140)],
      [(TX0 + 14, TZ0 + 6), (285, 130), (320, 145), (340, 170)]]
for i, pts in enumerate(RR):
    surface_root(pts, 2.6 if i < 3 else 2.0, .5, 500 + i * 17)
    if len(pts) > 2:                                                        # a side root forks off near the middle
        mx, mz = pts[len(pts) // 2]; a0 = math.atan2(pts[-1][1] - pts[0][1], pts[-1][0] - pts[0][0]) + (1 if i % 2 else -1) * .9
        surface_root([(mx, mz), (mx + math.cos(a0) * 22, mz + math.sin(a0) * 22)], 1.3, .4, 600 + i * 13)
CONTENT.append((300, 120, "roots"))

# ---------------- world boss: 根の主, a hollow walled in by roots ----------------
BX, BZ = BOSS
bg = at(BX, BZ)
for x in range(BX - 36, BX + 37):                                 # arena floor: root-bound earth, rings of moss
    for z in range(BZ - 36, BZ + 37):
        d = math.hypot(x - BX, z - BZ)
        if d < 30 * (1 + .16 * math.sin(3 * math.atan2(z - BZ, x - BX) + 1) + .08 * math.sin(5 * math.atan2(z - BZ, x - BX))) and 0 <= x < B and 0 <= z < B:
            for y in range(at(x, z) + 1, at(x, z) + 6): w.b.pop((x, y, z), None)
            n = noise3(x, 0, z, 51, 10)
            put(x, at(x, z), z, *(("moss_block",) if n > .62 else ("podzol_top", "podzol_side")))
mark(BX - 36, BZ - 36, BX + 36, BZ + 36)
RAD = lambda a: 31 * (1 + .16 * math.sin(3 * a + 1) + .08 * math.sin(5 * a))
a0 = 0.0
while a0 < math.tau - .2:                                          # the wall: root arcs of uneven span and height, a wide gap to the road
    span = R.uniform(.38, .7)
    a1 = a0 + span
    if abs(math.atan2(math.sin(a0 + span / 2 - 2.6), math.cos(a0 + span / 2 - 2.6))) < .45: a0 = a1 - .05; continue
    p0 = (BX + math.cos(a0) * RAD(a0), bg - 1, BZ + math.sin(a0) * RAD(a0))
    p2 = (BX + math.cos(a1) * RAD(a1), bg - 1, BZ + math.sin(a1) * RAD(a1))
    am = (a0 + a1) / 2
    hgt = R.choice([R.uniform(5, 9), R.uniform(12, 18), R.uniform(18, 26)])
    apex = (BX + math.cos(am) * (RAD(am) + R.uniform(-4, 3)), bg + hgt, BZ + math.sin(am) * (RAD(am) + R.uniform(-4, 3)))
    root_arch(p0, apex, p2, R.uniform(1.8, 3.2), R.uniform(1.2, 2.0), follow=False)
    a0 = a1 - R.uniform(.05, .15)
# the stump at the centre, glowing cracks
for y in range(bg + 1, bg + 6):
    r = 6.5 - (y - bg) * .35
    for dx in range(-8, 9):
        for dz in range(-8, 9):
            d = math.hypot(dx, dz)
            if d <= r * (1 + .12 * math.cos(5 * math.atan2(dz, dx))):
                ring = d > r - 1.3 or y == bg + 5
                put(BX + dx, y, BZ + dz, *(bark(BX + dx, y, BZ + dz) if ring else ("stripped_dark_oak_log",)))
for dx in range(-5, 6):
    for dz in range(-5, 6):
        if math.hypot(dx, dz) < 5: put(BX + dx, bg + 6, BZ + dz, "stripped_dark_oak_log", "stripped_dark_oak_log", shape="slab") if math.hypot(dx, dz) > 1 else None
for k2 in range(6):                                               # the boss itself, rising from the stump (stand-in)
    pass
spawn(BX, BZ, 24, 12, "lime_wool", "sprite")                        # root sprites gather around the old stump
spawn(BX, BZ, 24, 8, "black_wool", "beetle")
# ---------------- grind spot: the fungal hollow ----------------
FX, FZ = FUNGAL
for x in range(FX - 50, FX + 51):
    for z in range(FZ - 42, FZ + 43):
        if not (0 <= x < B and 0 <= z < B) or road[z, x] or lake[z, x] or stream[z, x]: continue
        d = math.hypot((x - FX) / 48, (z - FZ) / 40)
        if d < 1:
            n = noise3(x, 0, z, 71, 14)
            g = at(x, z)
            if math.hypot(x - FX + 2, z - FZ - 8) < 6 + 2 * noise3(x, 1, z, 72, 3) and not used[z, x]:
                put(x, g, z, "water_still", shape="water")               # one dark pool in the bottom of the hollow
            elif d + (n - .5) * .35 < .72:                               # one rot floor that fades out at the rim, not blobs
                put(x, g, z, *(("moss_block",) if n > .66 else ("podzol_top", "podzol_side")))


def G_get(p):
    v = w.b.get(p)
    return v[1] if v else None


def giant_mushroom(x, z, height, cap_r, red=True):
    g = at(x, z)
    la = R.uniform(0, math.tau); lm = height * R.uniform(.18, .3)
    lean = (math.cos(la) * lm, math.sin(la) * lm)
    top = (x + lean[0], g + height, z + lean[1])
    pts = bez((x, g - 1, z), (x - lean[0] * .3, g + height * .55, z - lean[1] * .3), top, 10)   # a stem that bows, then reaches for light
    for i, (p0, p1) in enumerate(zip(pts, pts[1:])):
        r_ = 1.1 + (1 - i / 10) * (.5 + height * .04)
        tube(p0, p1, r_, r_, mat=("mushroom_stem",))
    for k2 in range(6):                                           # root flare: thick at the stem, gripping the ground
        a = k2 / 6 * math.tau + R.uniform(-.3, .3); L = R.uniform(3, 5.5)
        tube((x, g + 2, z), (x + math.cos(a) * L, g - 1, z + math.sin(a) * L), 1.3, .5, mat=("mushroom_stem",))
    for dx in range(-6, 7):                                       # mycelium spreads round the foot
        for dz in range(-6, 7):
            if math.hypot(dx, dz) < 4 + 2.5 * noise3(x + dx, 0, z + dz, 73, 2) and (x + dx, at(x + dx, z + dz), z + dz) in w.b:
                if w.b[(x + dx, at(x + dx, z + dz), z + dz)][1] in ("podzol_top", "moss_block", "grass_block_top", "coarse_dirt"):
                    put(x + dx, at(x + dx, z + dz), z + dz, "mycelium_top", "mycelium_side")
    for (px_, py_, pz_) in pts[:4]:                                # moss creeps up the foot of the stem on the shaded side
        for dx in range(-3, 4):
            for dz in range(-3, 1):
                p = (int(round(px_)) + dx, int(py_), int(round(pz_)) + dz)
                if G_get(p) == "mushroom_stem" and _h(*p, 621) < .55: put(*p, "moss_block")
    for (px_, py_, pz_) in pts[2:-2:2]:                            # glow lichen up the stem
        put(int(round(px_)) + 2, int(py_), int(round(pz_)), "glow_lichen", shape="shut_s")
    cap = "red_mushroom_block" if red else "brown_mushroom_block"
    ri = int(cap_r) + 1
    ta = R.uniform(0, math.tau); tilt = R.uniform(.12, .3)              # caps tilt toward the light and are never quite round
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            d = math.hypot(dx, dz)
            if d > cap_r * (1 + .1 * math.sin(3 * math.atan2(dz, dx) + ta) + .06 * math.sin(5 * math.atan2(dz, dx))): continue
            dome = int(round((1 - (d / cap_r) ** 2) * (cap_r * .45 if red else cap_r * .2)))
            cx2, cz2 = int(round(top[0])) + dx, int(round(top[2])) + dz
            y = int(top[1]) + dome + int(round((dx * math.cos(ta) + dz * math.sin(ta)) * tilt))
            put(cx2, y, cz2, cap)
            if d > cap_r - 1.5:
                put(cx2, y - 1, cz2, cap)                          # rim turns down
            else:
                put(cx2, y - 1, cz2, "mushroom_block_inside")      # gills underneath
    put(int(round(top[0])), int(top[1]) - 1, int(round(top[2])), "shroomlight")   # a glow under the cap
    for _ in range(14):                                               # small mushrooms and moss gathered at the foot
        a = R.random() * math.tau; rr = R.uniform(2.5, 5)
        px, pz = int(round(x + math.cos(a) * rr)), int(round(z + math.sin(a) * rr))
        if 0 <= px < B and 0 <= pz < B and (px, at(px, pz) + 1, pz) not in w.b:
            put(px, at(px, pz), pz, "moss_block")
            put(px, at(px, pz) + 1, pz, R.choice(["red_mushroom", "brown_mushroom", "moss_block"]), shape="plant" if R.random() < .7 else "carpet")
    mark(x - ri, z - ri, x + ri, z + ri)


for (mx, mz, h, r, red) in ((FX - 10, FZ - 6, 20, 10, True),                                   # one hero; the rest brown, in two clusters of falling size
                             (FX + 14, FZ + 8, 12, 6, False), (FX + 19, FZ + 13, 8, 4, False), (FX + 11, FZ + 15, 5, 3, False),
                             (FX - 26, FZ - 20, 10, 5, False), (FX - 31, FZ - 15, 6, 3, False)):
    giant_mushroom(mx, mz, h, r, red)
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
for k2, (a0, turn, rise, r0_) in enumerate(((.4, .35, 3.4, 1.5), (2.9, -.22, 2.6, 1.1), (4.6, .5, 4.2, .8))):   # three roots, each its own way up
    prev = (TX + math.cos(a0) * 10, tg - 1, TZ + math.sin(a0) * 10)
    a = a0
    for s in range(1, 9):
        a += turn * (1 + .6 * math.sin(s * 1.7 + k2))
        r = 6.6 if s < 7 else 4
        cur = (TX + math.cos(a) * r, tg + s * rise, TZ + math.sin(a) * r)
        tube(prev, cur, max(.6, r0_ - s * .09), max(.55, r0_ - (s + 1) * .09), moss_top=True)
        prev = cur
        if prev[1] > tg + 24: break
for y in range(tg + 1, tg + 4):                                     # keep the doorway clear of the roots too
    for k2 in (-1, 0, 1):
        for dz in range(3, 10): w.b.pop((TX + k2, y, TZ + dz), None)
for (dx, dz) in ((0, 0), (3, 2), (-2, 3)):                         # a crown of leaves where the roots break through
    for ddx in range(-5, 6):
        for ddz in range(-5, 6):
            for ddy in range(-2, 3):
                if ddx * ddx + ddz * ddz + ddy * ddy * 3 <= 11 and R.random() < .6:
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
# Glacial erratics: angular blocks of very different sizes, tilted and half sunk, clustered where the ice dropped them:
# one huge split boulder, a few medium ones leaning on each other, and a spill of small stones down the slope.
QX, QZ = BOULDERS
def erratic(x, z, s, seed, tilt=.25, split=False):
    rng = random.Random(seed)
    planes = []
    for _ in range(9):                                             # a convex block cut by random planes: flat faces, sharp edges
        th, ph = rng.uniform(0, math.tau), rng.uniform(-1.2, 1.3)
        planes.append((math.cos(ph) * math.cos(th), math.sin(ph), math.cos(ph) * math.sin(th), s * rng.uniform(.75, 1.05)))
    planes.append((0, -1, 0, s * .35))                               # sunk into the ground
    tx, tz = rng.uniform(-tilt, tilt), rng.uniform(-tilt, tilt)
    sa = rng.uniform(0, math.pi)
    g = at(x, z); body = rng.choice(["stone", "andesite", "tuff"]); ri = int(s * 1.5) + 1
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            for dy in range(-ri, ri + 1):
                px, py, pz = dx + dy * tx, dy, dz + dy * tz
                if not all(px * a1 + py * b1 + pz * c1 <= d1 for a1, b1, c1, d1 in planes): continue
                if split and abs(px * math.cos(sa) + pz * math.sin(sa)) < .7: continue      # the crack through the big one
                X, Y, Z = x + dx, g + dy, z + dz
                if not (0 <= X < B and 0 <= Z < B) or Y < at(X, Z) - 1: continue
                up = all(not (px * a1 + (py + 1) * b1 + pz * c1 <= d1) for a1, b1, c1, d1 in planes[:1]) or not all(
                    px * a1 + (py + 1) * b1 + pz * c1 <= d1 for a1, b1, c1, d1 in planes)
                v = noise3(X, Y, Z, seed % 997, 3.0)
                mat = "moss_block" if up and v > .45 else ("mossy_cobblestone" if up and v > .38 else
                      ("cobblestone" if v < .2 else "iron_ore" if _h(X, Y, Z, seed) < .012 else body))
                put(X, Y, Z, mat)
    mark(x - ri, z - ri, x + ri, z + ri)
import random
erratic(QX, QZ, 9.5, 1201, tilt=.15, split=True)
MED = [(QX - 26, QZ - 10, 5.5), (QX - 20, QZ - 3, 4.0), (QX + 24, QZ + 6, 6.0), (QX + 9, QZ - 22, 4.5)]
for i, (x, z, s2) in enumerate(MED):
    if road_d[z, x] > 6: erratic(x, z, s2, 1300 + i * 7, tilt=.35)
for i in range(26):                                                  # the spill: small stones trailing south-west, downhill
    t = R.random(); x = int(QX - 10 - t * 40 + R.gauss(0, 6)); z = int(QZ + 6 + t * 18 + R.gauss(0, 5))
    if 6 <= x < B - 6 and 6 <= z < B - 6 and road_d[z, x] > 5 and not used[z, x]:
        if R.random() < .35: erratic(x, z, R.uniform(1.6, 2.6), 1400 + i, tilt=.4)
        else:
            g = at(x, z); put(x, g + 1, z, R.choice(["stone", "andesite", "mossy_cobblestone", "cobblestone"]), shape=R.choice(["full", "slab", "slab"]))
CONTENT.append((QX, QZ, "quarry"))

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
        put(WSX + dx, rg + 1, WSZ + dz, "stone_bricks", shape="slab")
for dx, dz, f in ((1, 0, "w"), (-1, 0, "e"), (0, 1, "n"), (0, -1, "s")):   # waystone: a broad-footed dark stone, flared at the base
    put(WSX + dx, rg + 1, WSZ + dz, "polished_deepslate", shape="stair_" + f)
for y in range(rg + 1, rg + 5): put(WSX, y, WSZ, "polished_deepslate" if y != rg + 3 else "chiseled_deepslate")
put(WSX, rg + 5, WSZ, "lodestone_top", "lodestone_side")
put(WSX + 1, rg + 2, WSZ, "moss_block", shape="carpet")
put(RX + 3, rg + 1, RZ + 2, "campfire_log_lit", shape="fire")
for (dx, dz) in ((-2, 0), (2, 0), (0, 2)): put(RX + 3 + dx, rg + 1, RZ + 2 + dz, "spruce_log", "spruce_log", shape="slab")
# the signpost: a stout stripped post on a stone footing, plank boards pointing down each road at different heights, a capped top
SPX, SPZ = RX + 5, RZ - 4
for dx, dz, f in ((1, 0, "w"), (-1, 0, "e"), (0, 1, "n"), (0, -1, "s")):
    put(SPX + dx, rg + 1, SPZ + dz, "cobblestone", shape="stair_" + f)
for y in range(rg + 1, rg + 5): put(SPX, y, SPZ, "stripped_spruce_log_top", "stripped_spruce_log")
put(SPX, rg + 5, SPZ, "spruce_planks", shape="slab")
for k in (1, 2): put(SPX + k, rg + 4, SPZ, "spruce_trapdoor", shape="shut_s")      # east: to the next zone
for k in (1, 2): put(SPX, rg + 3, SPZ - k, "spruce_trapdoor", shape="shut_w")      # north: to the great tree
for k in (1, 2): put(SPX - k, rg + 2, SPZ, "spruce_trapdoor", shape="shut_n")      # west: back to the harbour
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
    """A Somei Yoshino: short thick trunk forking low into 3-5 spreading limbs, an umbrella crown wider than tall built of
    separate blossom pads at different heights, with gaps where the dark limbs show through (research-builds.md recipe 5)."""
    rs = random.Random(x * 9973 + z)
    H = 8 + 7 * size
    W = H * rs.uniform(1.25, 1.45)
    D = max(1.2, H / 8)
    fork = g + H * rs.uniform(.2, .28)
    lean = (rs.uniform(-1, 1) * H / 9, rs.uniform(-1, 1) * H / 9)
    def wood(px, py, pz, base=False):
        n = "dark_oak_wood" if base or _h(px, py, pz, 801) < .2 else ("mangrove_wood" if _h(px, py, pz, 802) < .1 else "cherry_wood")
        return (n, n)
    top = (x + lean[0] * .4, fork, z + lean[1] * .4)
    for y in range(int(g) - 1, int(fork) + 1):                       # trunk with a flared foot
        t = (y - g) / max(1, fork - g)
        r = D / 2 * (1 + max(0, .15 - t) * 6) if t < .15 else D / 2
        cx_, cz_ = x + lean[0] * .4 * max(0, t), z + lean[1] * .4 * max(0, t)
        ri = int(r) + 2
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                if math.hypot(dx, dz) <= r + .35:
                    px, pz = int(round(cx_ + dx)), int(round(cz_ + dz))
                    put(px, y, pz, *wood(px, y, pz, base=t < .2))
    pads = []
    nl = rs.randint(3, 5)
    a0 = rs.uniform(0, math.tau)
    def limb(p, ang, elev, L, r, depth):
        e = (p[0] + math.cos(ang) * math.cos(elev) * L, p[1] + math.sin(elev) * L, p[2] + math.sin(ang) * math.cos(elev) * L)
        mid = ((p[0] + e[0]) / 2, (p[1] + e[1]) / 2 + L * .08, (p[2] + e[2]) / 2)        # limbs arch, then droop
        pts = bez(p, mid, e, max(3, int(L)))
        for i, (q0, q1) in enumerate(zip(pts, pts[1:])):
            rr = r * (1 - .35 * i / len(pts))
            n0 = int(math.dist(q0, q1) * 2) + 1
            for k in range(n0 + 1):
                f = k / n0
                cxp, cyp, czp = q0[0] + (q1[0] - q0[0]) * f, q0[1] + (q1[1] - q0[1]) * f, q0[2] + (q1[2] - q0[2]) * f
                ri = int(math.ceil(rr))
                for dx in range(-ri, ri + 1):
                    for dy in range(-ri, ri + 1):
                        for dz in range(-ri, ri + 1):
                            if dx * dx + dy * dy + dz * dz <= rr * rr + .3:
                                px, py, pz = int(round(cxp)) + dx, int(round(cyp)) + dy, int(round(czp)) + dz
                                put(px, py, pz, *wood(px, py, pz))
        if depth < 2:
            for s_ in (-1, 1):
                limb(e, ang + s_ * rs.uniform(.35, .7), max(-.1, elev - rs.uniform(.15, .35)), L * rs.uniform(.55, .7), max(.5, r * .7), depth + 1)
        else:
            pads.append(e)
    for k in range(nl):
        ang = a0 + k / nl * math.tau + rs.uniform(-.35, .35)
        reach = W / 2 * rs.uniform(.38, .5)
        limb(top, ang, math.radians(rs.uniform(30, 50)), reach, max(.6, D / 2 * .72), 0)
    limb(top, rs.uniform(0, math.tau), math.radians(70), H * .25, max(.6, D / 2 * .6), 1)    # a leader up the middle
    ps = max(1.0, size)
    for (px, py, pz) in pads:                                       # blossom pads: domed top, flatter thinner underside, gaps
        rx = rs.uniform(3, 4.5) * (.7 + .3 * ps); ry = rs.uniform(1.8, 2.6) * (.8 + .2 * ps)
        ri = int(rx) + 1
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                for dy in range(-int(ry) - 1, int(ry) + 2):
                    q = (dx / rx) ** 2 + (dz / rx) ** 2 + (dy / (ry if dy > 0 else ry * .7)) ** 2
                    if q > 1: continue
                    fill = 1.0 if q < .3 else (.78 if q < .6 else .32)          # a solid heart so no limb tip shows bare
                    if dy < 0: fill *= .6
                    p = (int(round(px)) + dx, int(round(py)) + dy, int(round(pz)) + dz)
                    if p in w.b or rs.random() > fill: continue
                    put(*p, "flowering_azalea_leaves" if (dy < 0 and q < .5 and rs.random() < .3) else "cherry_leaves")
        for _ in range(int(rx * 1.5)):                               # the hem droops a little
            a = rs.uniform(0, math.tau)
            p = (int(round(px + math.cos(a) * rx * .9)), int(round(py - ry * .5)), int(round(pz + math.sin(a) * rx * .9)))
            for k in range(rs.randint(1, 3)):
                q2 = (p[0], p[1] - k, p[2])
                if q2 in w.b or q2[1] <= at(p[0], p[2]) + 2: break
                put(*q2, "cherry_leaves")
    for _ in range(int(60 * size)):                                     # petals under the drip line, thinning toward the trunk
        a = rs.random() * math.tau; r = W / 2 * math.sqrt(rs.uniform(.15, 1.1))
        px, pz = int(round(x + math.cos(a) * r)), int(round(z + math.sin(a) * r))
        if 0 <= px < B and 0 <= pz < B:
            gy2 = UPPER[pz, px] if UPPER[pz, px] >= 0 else at(px, pz)
            if (px, gy2 + 1, pz) not in w.b: put(px, gy2 + 1, pz, "pink_petals", shape="carpet")


def torii(cx, cz, y, ux, uz, half=3, h=6):
    """Myojin torii across a path heading (ux, uz). Posts are whole from base to shimaki; the nuki is one beam that runs
    through the posts and one block past them; a small dark plaque sits between nuki and shimaki; the dark kasagi is one
    block wider than the shimaki on each side and its tips turn up by a half block."""
    px, pz = -uz, ux
    P = lambda s2: (int(round(cx + px * s2)), int(round(cz + pz * s2)))
    nuki_y = y + h - 2
    for s2 in (-half, half):
        bx, bz = P(s2)
        put(bx, y + 1, bz, "polished_blackstone")                    # kamebara: a black foot on each post
        for dx2, dz2, f2 in ((px, pz, None),):
            pass
        for yy in range(y + 2, y + h + 1): put(bx, yy, bz, "red_concrete")
    for s2 in range(-half - 1, half + 2):
        if abs(s2) == half: continue
        bx, bz = P(s2)
        put(bx, nuki_y, bz, "red_nether_bricks" if abs(s2) == half + 1 else "red_concrete", shape="slab_t" if abs(s2) == half + 1 else "full")
    if h >= 10 and half >= 3:                                       # paper lanterns hang under the nuki
        for s2 in (-(half // 2 + 1), half // 2 + 1):
            bx, bz = P(s2); chochin(bx, nuki_y - 1, bz)
    big = h >= 14
    bx, bz = P(0)
    for yy in range(nuki_y + 1, y + h): put(bx, yy, bz, "polished_deepslate")       # the plaque, a dark board, not a hole
    if False:                                                        # (doubled posts removed: they read as a theme-park gate)
        for s2 in (-half, half):
            for k in (1,):
                bx2, bz2 = P(s2 + (k if s2 > 0 else -k))
                put(bx2, y + 1, bz2, "polished_andesite")
                for yy in range(y + 2, y + h + 1): put(bx2, yy, bz2, "red_concrete")
    for s2 in range(-half - 1, half + 2):
        bx, bz = P(s2); put(bx, y + h, bz, "red_concrete")
    ext = half + (3 if big else 2)
    for s2 in range(-ext, ext + 1):
        bx, bz = P(s2); put(bx, y + h + 1, bz, "deepslate_tiles", shape="full" if abs(s2) < ext else "slab")
        if big and abs(s2) < ext - 1: put(bx, y + h + 2, bz, "deepslate_tiles", shape="slab")
    for s2 in (-ext, ext):
        bx, bz = P(s2); put(bx, y + h + 2, bz, "deepslate_tiles", shape="slab" if not big else "full")
        if big: put(bx, y + h + 3, bz, "deepslate_tiles", shape="slab")
    bx, bz = P(0)
    for (dx, dz) in ((0, 0),):
        pass


def _firebox(x, y, z, frame="dark_oak_trapdoor"):
    """A lit fire box: the lantern in the middle, open trapdoors on the four sides as the paper screens."""
    put(x, y, z, "lantern", shape="lantern")
    for dx, dz, sh in ((1, 0, "shut_e"), (-1, 0, "shut_w"), (0, 1, "shut_s"), (0, -1, "shut_n")):
        put(x + dx, y, z + dz, frame, shape=sh)


def _hip_roof(x, y, z, mat="deepslate_tiles"):
    """3x3 hipped cap: a block in the centre, stairs on the sides rising toward it, slabs on the corners."""
    put(x, y, z, mat)
    for dx, dz, f in ((1, 0, "w"), (-1, 0, "e"), (0, 1, "n"), (0, -1, "s")):
        put(x + dx, y, z + dz, mat, shape="stair_" + f)
    for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        put(x + dx, y, z + dz, mat, shape="slab")


def stone_lantern(x, z, y, tall=False):
    """Kasuga-style ishidoro: a stepped foot, a slender shaft, the screened fire box, a 3x3 hipped roof, a jewel.
    tall=True: a 3x3 plinth and a longer shaft, for the precinct."""
    put(x, y + 1, z, "polished_andesite")
    if tall:
        for dx, dz, f in ((1, 0, "w"), (-1, 0, "e"), (0, 1, "n"), (0, -1, "s")):
            put(x + dx, y + 1, z + dz, "stone_bricks", shape="stair_" + f)
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            put(x + dx, y + 1, z + dz, "stone_bricks", shape="slab")
        put(x, y + 2, z, "chiseled_stone_bricks")
        for yy in (y + 3, y + 4): put(x, yy, z, "stone_bricks", shape="wall")
        fy = y + 6
    else:
        put(x, y + 2, z, "stone_bricks", shape="wall")
        put(x, y + 3, z, "stone_bricks", shape="wall")
        fy = y + 5
    put(x, fy - 1, z, "polished_andesite")                         # chudai: the platform the fire box sits on
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz: put(x + dx, fy - 1, z + dz, "polished_andesite", shape="slab_t")
    _firebox(x, fy, z)
    _hip_roof(x, fy + 1, z)
    put(x, fy + 2, z, "polished_andesite", shape="post")


def chochin(x, y, z):
    """A hanging red paper lantern: chain and a red body."""
    put(x, y, z, "iron_chain", shape="chain")
    put(x, y - 1, z, "red_concrete", shape="lantern")


def pavilion(cx, cz, y, r=3, roof="deepslate_tiles", kind="open", face=(0, 1)):
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
    put(cx, y + 9, cz, "gold_block" if kind == "hall" else roof, shape="post")
    put(cx, y + 5, cz, "lantern", shape="lantern")
    if kind == "hall":                                               # a closed sanctuary: white plaster between dark posts, open only at the front
        for dx in range(-r + 1, r):
            for dz in range(-r + 1, r):
                if max(abs(dx), abs(dz)) != r - 1: continue
                front = (dx * face[0] + dz * face[1]) == r - 1 and abs(dx * face[1] - dz * face[0]) <= 1
                if front: continue
                corner = abs(dx) == r - 1 and abs(dz) == r - 1
                for yy in range(y + 2, y + 6):
                    put(cx + dx, yy, cz + dz, "red_concrete" if corner else ("dark_oak_planks" if yy in (y + 2, y + 5) else "white_concrete"))
        put(cx - face[0] * (r - 2), y + 2, cz - face[1] * (r - 2), "gold_block", shape="slab")    # the mirror on the altar
        for k in (-1, 0, 1):                                         # steps up to the front
            put(cx + face[0] * (r + 1) + face[1] * k, y + 1, cz + face[1] * (r + 1) - face[0] * k, "polished_andesite", shape="slab")
    elif kind == "chozuya":                                          # the water basin under its own small roof, a dipper laid across
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                put(cx + dx, y + 2, cz + dz, "polished_andesite" if (dx or dz) else "water_still", shape="full" if (dx or dz) else "water")
        put(cx + 1, y + 3, cz, "bamboo_block", shape="thinlog")
    else:
        put(cx, y + 2, cz, "barrel_top", "barrel_side")              # offering box
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
        if f < .45: r = rb + (.72 * rb - rb) * (f / .45)
        elif f < .82: r = .72 * rb + (rt - .72 * rb) * ((f - .45) / .37) ** 1.7
        else: r = rt * (1 - max(0, f - .97) * 3)
        ri = int(r * 1.25) + 2
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                a = math.atan2(dz, dx)
                rr = r * (1 + .22 * (noise3(math.cos(a) * 3 + seed, y * .25, math.sin(a) * 3, seed, 1.0) - .5) * 2)
                x, z = cx + dx, cz + dz
                if not needle and y < top_y - 5:
                    nl = 3 + seed % 3                                   # an uneven lobed plan, not a lathe-turned disc
                    rr *= 1 + (.14 * math.cos(nl * a + seed) + .07 * math.cos((nl + 2) * a + seed * 2)) * min(1, (top_y - 5 - y) / 6)
                    fis = (a * 7 + seed) % math.tau                     # vertical fissures cut into the stem and waist
                    if y < top_y - 4 and min(fis, math.tau - fis) < .09 + .05 * math.sin(seed + a): rr -= 2.2
                d = math.hypot(dx, dz)
                if d > rr: continue
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
                    rec = .3 if .45 < f < .8 else 0.0                                    # the overhang belly sits in shadow                              # the waist: only a little darker
                    mossy = top_y - y <= 16 and TM.moss_streak(x, y, z, top_y)       # streaks drip from the lip only
                    dd = max(.5, math.hypot(dx, dz)); ex_ = -(dx / dd * .6 + dz / dd * .8)       # which way this face looks: the sunlit side is pale
                    mat = "moss_block" if mossy else TM.cliff_block(x, y, z, .35 + f * .45, rec + (.35 if d < rr - 1.2 else 0), ex_)
                    put(x, y, z, mat)
    for _ in range(int(rt * (10 if not needle else 2))):           # a curtain of vines and leaf drapes from the lip
        a = R.random() * math.tau
        out = rt * (1 + .22 * (noise3(math.cos(a) * 3 + seed, top_y * .25, math.sin(a) * 3, seed, 1.0) - .5) * 2)
        x, z = int(round(cx + math.cos(a) * (out + .6))), int(round(cz + math.sin(a) * (out + .6)))
        if not (0 <= x < B and 0 <= z < B): continue
        drape = False
        for k2 in range(R.randint(1, 3) if drape else R.randint(4, 14)):
            p = (x, top_y - k2, z)
            if p in w.b or p[1] <= at(x, z): break
            pk = R.random() < sakura
            put(*p, ("cherry_leaves" if pk else "oak_leaves") if drape else "vine", shape="full" if drape else "shut_s")
    if needle:
        mark(cx - int(rb) - 3, cz - int(rb) - 3, cx + int(rb) + 3, cz + int(rb) + 3)
        return
    for k in range(int(rb * 2.2)):                                  # rockfall apron: boulders of three sizes downhill of the stem
        a = R.uniform(0, math.tau); rr = rb * R.uniform(1.0, 1.9)
        x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
        if not (3 <= x < B - 3 and 3 <= z < B - 3) or road_d[z, x] < 4 or lake[z, x] or stream[z, x]: continue
        g = at(x, z); sz = R.choice([.8, .8, 1.3, 1.3, 2.2])
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(0, 3):
                    if (dx / sz) ** 2 + (dz / sz) ** 2 + (dy / (sz * .8)) ** 2 <= 1:
                        put(x + dx, g + dy, z + dz, "moss_block" if dy == int(sz * .8) and _h(x + dx, dy, z + dz, 622) < .6 else TM.cliff_block(x + dx, g + dy, z + dz, .5, .2))
    la = R.uniform(0, math.tau)                                     # two ledges on one flank, vines hanging off their lips
    for k, (fy, w_) in enumerate(((.35, 2.4), (.6, 1.8))):
        y = int(base + Hm * fy)
        for dd in range(-5, 6):
            a = la + dd * .07
            r0 = rb * .75 + (rt - rb * .75) * max(0, fy - .45) / .37
            for t in range(int(w_ + 1)):
                x, z = int(round(cx + math.cos(a) * (r0 + t))), int(round(cz + math.sin(a) * (r0 + t)))
                if (x, y, z) not in w.b and abs(dd) < 5 - t:
                    put(x, y, z, TM.cliff_block(x, y, z, .6, 0)); put(x, y + 1, z, "moss_block", shape="carpet")
                    if t == int(w_) and _h(x, y, z, 623) < .5:
                        for j in range(1, R.randint(3, 9)):
                            if (x, y - j, z) in w.b or y - j <= at(x, z): break
                            put(x, y - j, z, "vine", shape="shut_s")
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


def rock_bridge(p0, p1, rise=3, width=2.2, sakura=0.0, style="rock"):
    rise = min(rise, 2)
    if style == "rope": width = 1.5; rise = -2
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
                if style == "rope":
                    if d <= 1.5 and UPPER[z, x] < deck:
                        put(x, deck, z, "spruce_planks"); UPPER[z, x] = deck
                    elif 1.5 < d <= 2.5 and i % 5 == 0 and (x, deck, z) not in w.b:
                        put(x, deck, z, "spruce_log", "spruce_log"); put(x, deck + 1, z, "spruce_planks", shape="fence")
                    elif 1.5 < d <= 2.5 and (x, deck + 1, z) not in w.b and UPPER[z, x] < deck:
                        put(x, deck + 1, z, "iron_chain", shape="chain")
                    continue
                if d <= wid:
                    top_ = ("grass_block_top", "grass_block_side") if style == "rock" else (("moss_block",) if noise3(x, 0, z, 154, 2) > .45 else ("grass_block_top", "grass_block_side"))
                    put(x, deck, z, *top_); UPPER[z, x] = max(UPPER[z, x], deck)
                    if w.b.get((x, deck + 1, z), ("", "", ""))[1] in ("vine",): w.b.pop((x, deck + 1, z), None)
                if style == "root":
                    for y in range(int(deck - (1.5 + under * .5) * (1 - (d / (wid + 1.2)) ** 2)), deck):
                        if y > at(x, z): put(x, y, z, *bark(x, y, z))
                    continue
                for y in range(int(deck - under * (1 - (d / (wid + 1.2)) ** 2)), deck):
                    if y > at(x, z): put(x, y, z, TM.cliff_block(x, y, z, .55 + (y - deck) / 20, .2) if noise3(x, y, z, 153, 2) < .5 else "moss_block")
                if wid < d <= wid + 1.2 and (x, deck, z) not in w.b:
                    put(x, deck, z, "cherry_leaves" if R.random() < sakura else ("azalea_leaves" if (x + z) % 3 else "oak_leaves"))
                if d <= wid and R.random() < sakura * .5 and (x, deck + 1, z) not in w.b: put(x, deck + 1, z, "pink_petals", shape="carpet")
        samples.append((cx, cz, deck, u))
        if i % 7 == 3 and style != "rope":
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


def link(a, b, rise, sak=0.0, style="rock"):
    (ax, az, ay, _, art, _), (bx, bz, by, _, brt, _) = a, b
    d = math.hypot(bx - ax, bz - az); ux, uz = (bx - ax) / d, (bz - az) / d
    return rock_bridge((ax + ux * art * .84, ay, az + uz * art * .84), (bx - ux * brt * .84, by, bz - uz * brt * .84), rise=rise, sakura=sak, style=style), (ux, uz)


BR0 = rock_bridge((ex, EDGE_Y, ez - 2), (PILLARS[0][0], PILLARS[0][2], PILLARS[0][1] - PILLARS[0][4] * .84), rise=0, sakura=.15)
BR1, D1 = link(PILLARS[0], PILLARS[1], 5, .45, style="root")              # a great root grown across
BR2, D2 = link(PILLARS[1], PILLARS[2], 6, .85)
BR3, D3 = link(PILLARS[2], PILLARS[3], 7, .9, style="rope")              # a rope bridge, the last crossing
# the sando: torii appear along the route and grow denser toward the boss (one, then a few, then a tunnel of them)
def torii_on(samples, d, every):
    for i2, (cx, cz, deck, u) in enumerate(samples):
        if .08 < u < .92 and i2 % every == 0:
            torii(cx, cz, deck, d[0], d[1], half=3, h=6)
def landing_torii(samples, d, at_start, half=3, h=11):
    """A torii on the flat top just before/after a bridge, never on the slope."""
    pts = samples if not at_start else samples[::-1]
    for (cx, cz, deck, u) in pts:
        if (u <= .01 and at_start) or (u >= .99 and not at_start):
            torii(cx + (-d[0] if at_start else d[0]) * 3, cz + (-d[1] if at_start else d[1]) * 3, deck, d[0], d[1], half=half, h=h)
            return


landing_torii(BR1, D1, at_start=False)                              # arriving on pillar 2
landing_torii(BR2, D2, at_start=True)                               # leaving pillar 2 toward the boss
P0x, P0z, P0y, _, P0r, _ = PILLARS[0]
for (dx, dz) in ((0, -int(P0r * .6)),):                             # a single small torii marks the start of the sando on the first pillar
    torii(P0x + dx, P0z + dz + 6, P0y, 0, 1, half=3, h=10)
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
    else:                                                            # the far pillar: an old watchtower, fallen in, a fire someone still keeps
        wx, wz = px + 8, pz - 6
        for y in range(ty - 1, ty + 13):
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    d = math.hypot(dx, dz)
                    if 3.6 < d <= 5:
                        ang = math.atan2(dz, dx)
                        lim = ty + 4 + int(8 * max(0, math.cos(ang - .8))) + int(3 * noise3(wx + dx, 0, wz + dz, 171, 2))
                        door = abs(dx) <= 1 and dz > 0 and y <= ty + 3
                        if y <= lim and not door:
                            put(wx + dx, y, wz + dz, R.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"]))
        for k in range(14):                                            # its fallen stones lie down the slope of the top
            a2 = R.uniform(-.6, .9); r2 = R.uniform(6, 12)
            x2, z2 = int(wx + math.cos(a2) * r2), int(wz + math.sin(a2) * r2)
            if UPPER[z2, x2] == ty and (x2, ty + 1, z2) not in w.b:
                put(x2, ty + 1, z2, R.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"]), shape=R.choice(["full", "slab", "slab"]))
        put(wx, ty + 1, wz, "campfire_log_lit", shape="fire")
        for (dx, dz) in ((-2, 0), (2, 0)): put(wx + dx, ty + 1, wz + dz, "spruce_log", "spruce_log", shape="slab")
        cache(wx - 2, ty + 1, wz - 2, "見張り台の跡")
        LABELS_POI.append(((wx, wz), "柱の上の見張り台跡", (220, 210, 190)))
    CONTENT.append((px, pz, "pillar"))
# the first pillar is splitting: a cleft runs down its south face, ferns and a crooked tree grow in it
P0x_, P0z_, P0y_, P0rb_, P0r_, _ = PILLARS[0]
for y in range(P0y_ - 26, P0y_ + 1):
    f = (P0y_ - y) / 26
    wdt = 1.6 * (1 - f) + .4
    for dx in range(-3, 4):
        for z in range(P0z_, P0z_ + int(P0r_ * 1.3)):
            off = math.sin((P0y_ - y) * .25) * 1.5
            if abs(dx - off) <= wdt and (P0x_ + dx, y, z) in w.b:
                inner = (P0x_ + dx, y, z - 1) in w.b
                if z > P0z_ + P0r_ * .75 - 6 * f:
                    w.b.pop((P0x_ + dx, y, z), None); UPPER[z, P0x_ + dx] = -1 if y == P0y_ else UPPER[z, P0x_ + dx]
for y in range(P0y_ - 20, P0y_ - 2, 3):
    zz3 = max([z for z in range(P0z_, P0z_ + int(P0r_ * 1.3)) if (P0x_, y, z) in w.b] or [P0z_])
    put(P0x_ + R.choice((-1, 0, 1)), y, zz3 + 1, R.choice(["fern", "hanging_roots", "vine"]), shape="plant")
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
PREC = AR + 17
for (x, y, z) in [p for p in w.b if abs(p[0] - SX_) <= PREC and abs(p[2] - SZ_) <= PREC and SY_ < p[1] < SY_ + 30]:
    if math.hypot(x - SX_, z - SZ_) <= PREC and w.b[(x, y, z)][1] in ("cherry_log", "cherry_log_top", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "cherry_leaves", "pink_wool", "oak_log", "oak_leaves", "oak_log_top"):
        w.b.pop((x, y, z), None)
for (x, y, z) in [p for p in w.b if abs(p[0] - SX_) <= PREC and abs(p[2] - SZ_) <= PREC and p[1] == SY_ + 1]:
    if w.b[(x, y, z)][0] in ("carpet", "plant") and math.hypot(x - SX_, z - SZ_) <= PREC and UPPER[z, x] == SY_: w.b.pop((x, y, z), None)
for (x, y, z) in [p for p in w.b if abs(p[0] - SX_) <= PREC and abs(p[2] - SZ_) <= PREC and SY_ - 3 <= p[1] <= SY_]:
    if w.b[(x, y, z)][1] in ("cherry_log", "cherry_log_top", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "oak_log", "oak_log_top") and math.hypot(x - SX_, z - SZ_) <= PREC:
        put(x, y, z, "grass_block_top" if y == SY_ else "dirt", "grass_block_side" if y == SY_ else "dirt")
for dx in range(-AR - 4, AR + 5):
    for dz in range(-AR - 4, AR + 5):
        d = math.hypot(dx, dz)
        x, z = SX_ + dx, SZ_ + dz
        if UPPER[z, x] != SY_ or d > AR + 3: continue
        for y in range(SY_ + 1, SY_ + 12):
            v = w.b.get((x, y, z), ("", "", ""))
            if v[1] in ("cherry_log", "cherry_log_top", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "cherry_leaves", "pink_petals", "pink_wool") or v[0] in ("plant", "carpet"): w.b.pop((x, y, z), None)
        if d <= AR:
            ring = abs(d - (AR - 2.5)) < .55                                  # one ring near the edge
            ang = math.atan2(dz, dx)
            spoke = min(abs(((ang * 8 / math.tau) % 1) - .5), .5) > .47 and 8 < d < AR - 3  # eight thin radial lines
            petal = False                                                    # a five-petal sakura crest (sakura-mon) at the centre
            for pk in range(5):
                pa = ent_a + pk / 5 * math.tau
                qx, qz = dx - math.cos(pa) * 4.2, dz - math.sin(pa) * 4.2
                along = qx * math.cos(pa) + qz * math.sin(pa); across = -qx * math.sin(pa) + qz * math.cos(pa)
                notch = along > 1.6 and abs(across) < .7
                if (along / 3.0) ** 2 + (across / 2.3) ** 2 <= 1 and not notch: petal = True
            if d < 2.6: mat = "chiseled_stone_bricks" if d < 1.2 else "polished_andesite"
            elif ring: mat = "polished_andesite"
            elif abs(d - AR * .6) < 2.2 + noise3(x, 0, z, 616, 3): mat = "cracked_stone_bricks" if _h(x, 0, z, 61) < .45 else "polished_andesite"   # worn where the fight circles
            else: mat = "stone_bricks" if noise3(x, 1, z, 617, 4) > .3 else "mossy_stone_bricks"
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
    gone = noise3(math.cos(a) * 4, 0, math.sin(a) * 4, 613, 1.2)            # stretches of the old fence have fallen
    if gone > .68: continue
    put(x, SY_ + 1, z, "red_concrete", shape="fence")
    if gone < .6: put(x, SY_ + 2, z, "red_concrete", shape="slab")
gx0, gz0 = SX_ + math.cos(ent_a) * (AR + 2), SZ_ + math.sin(ent_a) * (AR + 2)
torii(gx0, gz0, SY_, -math.cos(ent_a), -math.sin(ent_a), half=5, h=17)   # the great torii at the entrance
for off in (.5, -.5, math.pi - .3, math.pi + .3):                      # lanterns only where something is: flanking the gate and the sacred tree
    a = ent_a + off
    stone_lantern(int(round(SX_ + math.cos(a) * (AR + 5))), int(round(SZ_ + math.sin(a) * (AR + 5))), SY_, tall=True)
for off, kind, rr0 in ((.85, "chozuya", 2), (-.85, "open", 3), (2.3, "hall", 5), (-2.3, "hall", 3)):   # a main hall, a small shrine, an open hall, the basin
    a = ent_a + off
    px2, pz2 = int(round(SX_ + math.cos(a) * (AR + 11))), int(round(SZ_ + math.sin(a) * (AR + 11)))
    fc = (-math.cos(a), -math.sin(a)); fc = (int(round(fc[0])), 0) if abs(fc[0]) > abs(fc[1]) else (0, int(round(fc[1])))
    if UPPER[pz2, px2] == SY_:
        pavilion(px2, pz2, SY_, r=rr0, kind=kind, face=fc)
        if rr0 < 5: continue
        for layer in range(2):                                           # a second, smaller roof floating above the first
            rr = 2 - layer
            for dx in range(-rr, rr + 1):
                for dz in range(-rr, rr + 1):
                    put(px2 + dx, SY_ + 10 + layer, pz2 + dz, "deepslate_tiles", shape="slab" if max(abs(dx), abs(dz)) == rr and layer == 0 else "full")
        for dx in range(-1, 2):                                          # the drum between the two roofs
            for dz in range(-1, 2):
                put(px2 + dx, SY_ + 9, pz2 + dz, "dark_oak_planks" if (dx or dz) else "red_concrete")
        put(px2, SY_ + 12, pz2, "gold_block", shape="post")
for off in (1.55, -1.55):                                            # reflecting ponds between the pavilions
    a = ent_a + off
    qx0, qz0 = SX_ + math.cos(a) * (AR + 10), SZ_ + math.sin(a) * (AR + 10)
    for dx in range(-7, 8):
        for dz in range(-7, 8):
            x, z = int(round(qx0)) + dx, int(round(qz0)) + dz
            d = math.hypot(dx / 1.3, dz)
            if UPPER[z, x] != SY_ or d > 5.2: continue
            if d <= 4.2:
                put(x, SY_ - 2, z, "gravel"); put(x, SY_ - 1, z, "water_still"); put(x, SY_, z, "water_still", shape="water")
                UPPER[z, x] = -1
                if _h(x, 0, z, 71) < .12: put(x, SY_ + 1, z, "lily_pad", shape="carpet")
            else:
                put(x, SY_, z, "polished_andesite" if _h(x, 1, z, 72) < .6 else "mossy_stone_bricks")
    stone_lantern(int(round(qx0)) + 5, int(round(qz0)) - 3, SY_)
for k2 in range(8):                                                  # framing sakura on the precinct edge, kept out of the sightlines
    if k2 % 2: continue                                              # four, not a ring
    a = ent_a + .45 + k2 / 8 * math.tau
    rel = abs(math.atan2(math.sin(a - ent_a), math.cos(a - ent_a)))
    if rel < .6 or rel > math.pi - .55: continue
    x, z = int(round(SX_ + math.cos(a) * (AR + 16))), int(round(SZ_ + math.sin(a) * (AR + 16)))
    if UPPER[z, x] == SY_: sakura_tree(x, z, SY_, size=(1.0, 1.3, .7, 1.1)[k2 // 2])
# the walk from the last bridge to the arena: a tunnel of torii lined with stone lanterns, a stone path
startx, startz = BR2[-1][0], BR2[-1][1]
for k2 in range(0, 40):
    for s2 in (-1, 0, 1):
        x, z = int(round(startx + ux2 * k2 - uz2 * s2)), int(round(startz + uz2 * k2 + ux2 * s2))
        if UPPER[z, x] == SY_ and math.hypot(x - SX_, z - SZ_) > AR + 1.5:
            put(x, SY_, z, ("stone_bricks", "polished_andesite", "mossy_stone_bricks", "stone_bricks")[int(noise3(x, 0, z, 614, 2.5) * 4) % 4] if s2 == 0 or _h(x, 0, z, 615) < .7 else "moss_block")
            for y in range(SY_ + 1, SY_ + 10):
                if w.b.get((x, y, z), ("", "", ""))[1] in ("cherry_log", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "cherry_leaves"): w.b.pop((x, y, z), None)
for k2 in range(1, 9):
    cx, cz = startx + ux2 * (3 + k2 * 8), startz + uz2 * (3 + k2 * 8)
    if math.hypot(cx - SX_, cz - SZ_) < AR + 6: break
    torii(cx, cz, SY_ - 1, ux2, uz2, half=3, h=12)
    lx0, lz0 = cx + ux2 * 4, cz + uz2 * 4
    if math.hypot(lx0 - SX_, lz0 - SZ_) > AR + 6:
        for s2 in (-4, 4):
            stone_lantern(int(round(lx0 - uz2 * s2)), int(round(lz0 + ux2 * s2)), SY_)
# the sacred sakura (goshinboku) behind the arena, opposite the entrance, wrapped in a shimenawa
back = ent_a + math.pi
GX_, GZ_ = int(round(SX_ + math.cos(back) * (AR + 12))), int(round(SZ_ + math.sin(back) * (AR + 12)))
sakura_tree(GX_, GZ_, SY_, size=3.0)
for k2 in range(48):
    a = k2 / 48 * math.tau
    x, z = int(round(GX_ + math.cos(a) * 4.8)), int(round(GZ_ + math.sin(a) * 4.8))
    put(x, SY_ + 4 + (k2 % 3 == 0), z, "hay_block_top", "hay_block_side")
    if k2 % 6 == 0:
        x2, z2 = int(round(GX_ + math.cos(a) * 5.8)), int(round(GZ_ + math.sin(a) * 5.8))
        sh = "shut_e" if abs(math.cos(a)) > abs(math.sin(a)) and math.cos(a) > 0 else "shut_w" if abs(math.cos(a)) > abs(math.sin(a)) else "shut_s" if math.sin(a) > 0 else "shut_n"
        put(x2, SY_ + 3, z2, "white_wall_banner", shape=sh)
stone_lantern(GX_ - 6, GZ_ + 6, SY_); stone_lantern(GX_ + 6, GZ_ + 6, SY_)
put(SX_, FY + 1, SZ_, "pink_wool", shape="boss")                    # the sakura boss (stand-in)
CONTENT.append((SX_, SZ_, "boss"))

# great stone arches over the road where it enters and leaves the zone: scenery gates (rounded rock, nothing to stand on)
def stone_arch(p0, p1, height, r):
    """A natural rock arch: a broad, flat-sided band of bedded rock (wider than thick), turf and a small tree on its back,
    a few ledges, vines from the soffit. Thick legs that spread into the ground."""
    pts = bez(p0, ((p0[0] + p1[0]) / 2, max(p0[1], p1[1]) + height, (p0[2] + p1[2]) / 2), p1, 40)
    ax, az = p1[0] - p0[0], p1[2] - p0[2]; al = math.hypot(ax, az); ax, az = ax / al, az / al
    nx, nz = -az, ax                                                  # across the band
    seen = set()
    for i2, (x, y, z) in enumerate(pts):
        f = i2 / (len(pts) - 1)
        leg = max(0, 1 - min(f, 1 - f) * 5)                           # 1 at the feet, 0 from a fifth in
        wdt = r * 1.5 + leg * r * 1.6                                 # wide band
        th = r * .9 + leg * r * 1.2                                   # thinner than wide
        for k in range(-int(wdt) - 1, int(wdt) + 2):
            for dy in range(-int(th) - 1, int(th) + 2):
                for dl in (-1, 0, 1):
                    if abs(k) > wdt or abs(dy) > th * (1 - .25 * (abs(k) / max(1, wdt)) ** 4): continue
                    px, py, pz = int(round(x + nx * k + ax * dl * .5)), int(round(y + dy)), int(round(z + nz * k + az * dl * .5))
                    if (px, py, pz) in seen or not (0 <= px < B and 0 <= pz < B) or py < at(px, pz) - 1: continue
                    seen.add((px, py, pz))
                    put(px, py, pz, TM.strata(px, py, pz))
    for (px, py, pz) in list(seen):                                   # turf on its back, moss where it is flat enough
        if (px, py + 1, pz) not in seen and (px, py - 1, pz) in seen and py > at(px, pz) + 2:
            put(px, py, pz, "grass_block_top", "grass_block_side") if noise3(px, py, pz, 162, 2) > .3 else put(px, py, pz, "moss_block")
            if _h(px, py, pz, 163) < .12: put(px, py + 1, pz, R.choice(["short_grass", "fern", "short_grass"]), shape="plant")
    top_i = len(pts) // 2 + R.randint(-4, 4)
    tx, ty, tz = pts[top_i]
    ty = max(py for (px, py, pz) in seen if px == int(round(tx)) and pz == int(round(tz))) if any(px == int(round(tx)) and pz == int(round(tz)) for (px, py, pz) in seen) else int(ty)
    for yy in range(ty + 1, ty + 5): put(int(round(tx)), yy, int(round(tz)), *LOG("oak_log"))   # a wind-bent tree on the crown
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-1, 2):
                if dx * dx + dz * dz + dy * dy * 3 <= 7 + R.random() * 2: put(int(round(tx)) + dx + 1, ty + 5 + dy, int(round(tz)) + dz, "oak_leaves")
    for (x, y, z) in pts[8:-8:2]:                                    # vines and roots hanging from the soffit
        for _ in range(2):
            vx, vz = int(round(x + nx * R.uniform(-r, r))), int(round(z + nz * R.uniform(-r, r)))
            under = [py for (px, py, pz) in seen if px == vx and pz == vz]
            if not under: continue
            for k2 in range(1, R.randint(3, 12)):
                p = (vx, min(under) - k2, vz)
                if p in w.b or p[1] <= at(vx, vz): break
                put(*p, "vine", shape="shut_s")
    for (x, y, z) in pts:
        mark(int(x) - 14, int(z) - 14, int(x) + 14, int(z) + 14)


stone_arch((22, at(22, 425) - 1, 425), (40, at(40, 474) - 1, 474), 34, 4.5)                  # entering from the harbor
stone_arch((470, at(470, 200) - 1, 200), (470, at(470, 268) - 1, 268), 40, 5)                # leaving east

# rock needles in clusters on the plains: scenery that frames the views
for (sx, sz, h2, r2) in ((150, 470, 48, 5), (172, 452, 36, 3.5), (130, 460, 28, 3), (40, 355, 44, 4.5), (60, 340, 30, 3.5),
                         (470, 470, 52, 5.5), (448, 460, 34, 4), (460, 120, 40, 4), (20, 120, 36, 4), (235, 470, 30, 3.5)):
    if not (5 <= sx < B - 5 and 5 <= sz < B - 5) or anyway[sz, sx] < 9 or used[sz, sx] or lake[sz, sx]: continue
    pass                                                              # needles removed after the in-game check


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
                    if hole and y < top: continue
                    put(x + dx, y, z + dz, R.choice(["coarse_dirt", "rooted_dirt", "coarse_dirt", "packed_mud", "dirt"]))
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
# ---------------- authored places: a few spots with a story, instead of filler on a grid ----------------
def find_spot(cx, cz, r=30, need=6, near_road=None):
    """The nearest flat, free cell to (cx, cz): slope low over a (2*need+1) square, off water and roads."""
    best = None
    for rr in range(0, r):
        for a in range(0, 360, 12):
            x = int(round(cx + math.cos(math.radians(a)) * rr)); z = int(round(cz + math.sin(math.radians(a)) * rr))
            if not (need + 2 <= x < B - need - 2 and need + 2 <= z < B - need - 2): continue
            win = (slice(z - need, z + need + 1), slice(x - need, x + need + 1))
            if used[win].any() or lake[win].any() or stream[win].any() or road[win].any() or trail[win].any(): continue
            if hi[win].max() - hi[win].min() > 2: continue
            if near_road is not None and not (near_road[0] <= anyway[z, x] <= near_road[1]): continue
            return x, z
    return None


def level_pad(x0, z0, x1, z1, y):
    """Make a small patch exactly level at y (fill or cut), keeping the top material."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g = at(x, z)
            top = w.b.get((x, g, z), ("full", "grass_block_top", "grass_block_side"))
            for yy in range(y + 1, g + 1): w.b.pop((x, yy, z), None)
            for yy in range(g + 1, y): put(x, yy, z, "dirt")
            put(x, y, z, top[1], top[2]) if top[0] == "full" else put(x, y, z, "grass_block_top", "grass_block_side")
            for yy in range(y + 1, y + 4):
                v = w.b.get((x, yy, z))
                if v and v[0] in ("plant", "carpet", "slab"): w.b.pop((x, yy, z), None)
            hi[z, x] = y



def soften(x0, z0, x1, z1):
    """Half steps on every one-block rise inside a box, so built-up mounds read as slopes, not terraces."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ys = [yy for yy in range(hi[z, x] - 2, hi[z, x] + 14) if (x, yy, z) in w.b and w.b[(x, yy, z)][0] == "full"]
            if not ys: continue
            y = max(ys)
            if (x, y + 1, z) in w.b: continue
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + 1, z + dz) in w.b and w.b[(x + dx, y + 1, z + dz)][0] == "full" and (x + dx, y + 2, z + dz) not in w.b:
                    t = w.b[(x, y, z)]
                    put(x, y + 1, z, "moss_block" if t[1] in ("grass_block_top", "dirt") else t[1], shape="carpet" if False else "slab")
                    break


def place(name, x, z, kind, half=6):
    mark(x - half - 2, z - half - 2, x + half + 2, z + half + 2)
    CONTENT.append((x, z, kind)); FILL.append((x, z, name))
    LABELS_POI.append(((x, z), name, (255, 230, 150)))



# 1. 根に呑まれた荷馬車: a covered wagon stopped where a root heaved up through the track; the root broke the front axle,
# the wagon sits nose-down, canvas torn where the root came through; the crates it carried lie where they slid off.
s1 = None and find_spot(150, 318, need=8)
if s1:
    x, z = s1; g = at(x, z); level_pad(x - 7, z - 6, x + 7, z + 6, g)
    WOOD = "spruce_planks"
    for dx in range(-3, 4):                                           # the bed, one block above the ground, front end (west) dropped
        yb = g + 1 if dx > -2 else g
        for dz in (-1, 0, 1): put(x + dx, yb, z + dz, WOOD, shape="slab_t" if dx > -2 else "slab")
    for dx in (1, 3):                                                 # rear wheels: dark trapdoor discs against the bed sides
        put(x + dx, g + 1, z - 2, "dark_oak_trapdoor", shape="shut_n"); put(x + dx, g + 1, z + 2, "dark_oak_trapdoor", shape="shut_s")
    put(x - 2, g + 1, z + 2, "dark_oak_trapdoor", shape="shut_s")     # one front wheel still on, the other lies flat in the grass
    put(x - 3, g + 1, z - 3, "dark_oak_trapdoor", shape="carpet")
    for dx in range(-1, 4):                                           # canvas: sides one high, a roof row above, torn open at the front
        for dz in (-1, 1): put(x + dx, g + 2, z + dz, "white_wool")
        put(x + dx, g + 3, z, "white_wool")
        for dz in (-1, 1): put(x + dx, g + 3, z + dz, "smooth_quartz", shape="slab")
    for dz in (-1, 0, 1): w.b.pop((x - 1, g + 3, z + dz), None)      # the torn front hoop: bare bent wood shows
    put(x - 1, g + 2, z - 1, "spruce_planks", shape="post"); put(x - 1, g + 2, z + 1, "spruce_planks", shape="post")
    put(x - 1, g + 3, z, "spruce_planks", shape="slab")
    put(x + 4, g + 2, z, "white_wool")                                # the back is laced shut
    for dz in (-1, 1): put(x + 4, g + 2, z + dz, "white_wool", shape="slab")
    for dz in (-1, 1): put(x - 4, g + 1, z + dz, "spruce_planks", shape="post")   # the shafts, resting on the ground
    put(x - 5, g + 1, z - 1, "spruce_planks", shape="carpet"); put(x - 5, g + 1, z + 1, "spruce_planks", shape="carpet")
    root_arch((x - 9, g - 1, z + 4), (x - 3, g + 4, z - 1), (x + 2, g - 1, z - 7), 1.9, 1.0, hang=False)   # the root that stopped it
    for (dx, dz, k) in ((-3, 3, "b"), (-4, 4, "c"), (-5, 2, "c"), (-2, 4, "s")):   # what slid off the front
        if k == "b": put(x + dx, g + 1, z + dz, "barrel_top", "barrel_side")
        elif k == "c": put(x + dx, g + 1, z + dz, "spruce_planks", "spruce_planks")
        else: put(x + dx, g + 1, z + dz, "brown_wool", shape="slab")
    for (dx, dz) in ((4, -4), (5, -5), (6, -4), (5, -3)): put(x + dx, g + 1, z + dz, "cobblestone", shape="slab")   # their cold fire
    put(x + 5, g, z - 4, "coarse_dirt"); put(x + 6, g + 1, z - 2, "spruce_log", "spruce_log", shape="slab")
    place("根に呑まれた荷馬車", x, z, "story")

# 2. 崩れた見張り小屋: on the high scarp edge, looking down over the mid level; half the floor has fallen
xs = 128; zs = int(B2[xs]) - 7
s2 = None and find_spot(xs, zs, r=20, need=4)
if s2:
    x, z = s2; g = at(x, z); level_pad(x - 4, z - 4, x + 4, z + 4, g)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            edge = max(abs(dx), abs(dz)) == 3
            put(x + dx, g, z + dz, "cobblestone" if edge else "spruce_planks")
            if edge:
                cd = math.hypot(dx - 3, dz - 3)                        # the valley-side corner has fallen, the rest stands to the eaves
                hgt = 5 if cd > 4.5 else max(0, int(cd * 1.1) - 1)
                for yy in range(g + 1, g + 1 + hgt):
                    put(x + dx, yy, z + dz, R.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "cobblestone"]))
    for yy in range(g + 1, g + 4): w.b.pop((x, yy, z - 3), None)    # doorway, with a timber lintel
    put(x, g + 4, z - 3, "spruce_log", "spruce_log")
    for dz in (-1, 1): w.b.pop((x - 3, g + 3, z + dz), None)         # small windows
    for dz in range(-3, 4): put(x - 2, g + 6, z + dz, "spruce_log", "spruce_log")   # roof beams: one whole, one snapped and hanging
    for dz in range(-3, 1): put(x + 1, g + 6, z + dz, "spruce_log", "spruce_log")
    put(x + 1, g + 5, z + 1, "spruce_log", "spruce_log"); put(x + 1, g + 4, z + 2, "spruce_log", "spruce_log")
    for dx in range(-3, 0):                                          # a corner of the roof still on, the rest fell in
        for dz in range(-3, 0): put(x + dx, g + 7, z + dz, "spruce_planks", shape="slab")
    for (dx, dz) in ((1, 1), (2, 2), (0, 2), (2, 0)): put(x + dx, g + 1, z + dz, R.choice(["cobblestone", "mossy_stone_bricks", "spruce_planks"]), shape="slab")
    put(x - 2, g + 1, z - 2, "barrel_top", "barrel_side"); put(x - 1, g + 1, z - 2, "spruce_planks", "spruce_trapdoor", shape="slab")
    put(x - 2, g + 1, z - 1, "lantern", shape="lantern")
    for (dx, dz) in ((4, 4), (5, 3), (3, 5)): put(x + dx, at(x + dx, z + dz) + 1, z + dz, "mossy_stone_bricks", shape="slab")
    place("崩れた見張り小屋", x, z, "story", 4)

# 3. 根の下の隠し洞: a low mound held by roots, with a dark mouth; inside a small chamber lit by glowing roots
s3 = None and find_spot(335, 145, need=10)
if s3:
    x, z = s3; g = at(x, z)
    for dx in range(-10, 11):
        for dz in range(-10, 11):
            d = math.hypot(dx, dz * 1.15)
            if d < 10:
                top = g + int((1 - (d / 10) ** 2) * 8)
                for yy in range(at(x + dx, z + dz) + 1, top + 1):
                    put(x + dx, yy, z + dz, *(("grass_block_top", "grass_block_side") if yy == top else ("dirt",)))
    for k in range(4):                                               # roots gripping the mound
        a = k / 4 * math.tau + .4
        if abs(math.sin(a) - 1) < .5: continue                          # keep the mouth side clear
        tube((x + math.cos(a) * 11, g - 1, z + math.sin(a) * 11), (x + math.cos(a) * 4, g + 8, z + math.sin(a) * 4), 1.0, .6, moss_top=True)
    soften(x - 11, z - 11, x + 11, z + 11)
    for dx in range(-2, 3):                                          # the chamber
        for dz in range(-2, 3):
            for yy in range(g + 1, g + 4): w.b.pop((x + dx, yy, z + dz), None)
            put(x + dx, g, z + dz, "rooted_dirt")
    mz = z + 5                                                       # the mouth: where the mound is still ~4 high, framed with old stones
    for dz in range(3, 12):
        for yy in range(g + 1, g + 4):
            for dx in (-1, 0, 1): w.b.pop((x + dx, yy, z + dz), None)
    for side in (-1, 1):                                             # two roots come down either side of the mouth and cross over it
        pts = bez((x + side * 6, g + 6, mz - 4), (x + side * 3, g + 6, mz + 1), (x - side * 1, g + 4.5, mz + 1), 8)
        pts += bez((x + side * 3, g + 6, mz + 1), (x + side * 2.6, g + 2, mz + 1.5), (x + side * 3, g - 1, mz + 2), 6)
        for p0, p1 in zip(pts, pts[1:]): tube(p0, p1, .7, .7, moss_top=True)
    for yy in range(g + 1, g + 4):
        for dx in (-1, 0, 1):
            for dz in range(mz - 1, mz + 3): w.b.pop((x + dx, yy, z + dz - z + dz * 0) if False else (x + dx, yy, dz), None)
    for dx in (-1, 0, 1):
        for dz in range(mz + 1, z + 12): put(x + dx, at(x + dx, dz), dz, "coarse_dirt" if (dx + dz) % 3 else "gravel")
    for k in range(3): put(x - 1 + k, g + 3, mz - 1, "hanging_roots", shape="shut_s")
    put(x, g, z - 1, "barrel_top", "barrel_side")                    # what someone hid here
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 1)): put(x + dx, g + 3, z + dz, "glow_lichen", shape="carpet") if False else put(x + dx, g + 3, z + dz, "shroomlight")
    put(x + 1, g, z - 2, "red_mushroom", shape="plant"); put(x - 1, g, z, "brown_mushroom", shape="plant")
    place("根の下の隠し洞", x, z, "secret", 11)

# 4. 古い樹液採り場: a great root arches out of the ground; three spouts are cut into its flank, a pail under each,
# and a couple of filled casks wait to be carried off.
s4 = None and find_spot(205, 190, need=5)
if s4:
    x, z = s4; g = at(x, z); level_pad(x - 6, z - 4, x + 6, z + 5, g)
    root_arch((x - 7, g - 1, z - 2), (x, g + 5, z - 3), (x + 7, g - 1, z - 2), 2.0, 1.4, hang=False)
    for dx in (-3, 0, 3):
        ys_ = [yy for yy in range(g + 1, g + 9) if (x + dx, yy, z - 2) in w.b]
        if not ys_: continue
        sy = min(ys_)
        for zz3 in range(z - 2, z + 2):                                # walk out to the root's south face
            if (x + dx, sy + 1, zz3) not in w.b: break
        put(x + dx, sy + 1, zz3, "spruce_trapdoor", shape="shut_n") if (x + dx, sy + 1, zz3) not in w.b else None   # the spout
        put(x + dx, g + 1, zz3, "cauldron")
    put(x + 4, g + 1, z + 3, "barrel_top", "barrel_side"); put(x + 5, g + 1, z + 3, "barrel_top", "barrel_side")
    put(x + 5, g + 2, z + 3, "honey_block_top", "honey_block_side", shape="slab")
    spawn(x, z + 6, 7, 3, "black_wool", "beetle")
    place("古い樹液採り場", x, z, "gather", 6)

# 5. 道端の祠: a hokora (a little roadside shrine) on a stone plinth with a gabled roof, a small torii before it,
# a lantern inside; a young sakura behind is the first hint of what waits on the pillars.
s5 = None and find_spot(262, 252, r=25, need=3, near_road=(4, 9))
if s5:
    x, z = s5; g = at(x, z); level_pad(x - 3, z - 3, x + 3, z + 6, g)
    for dx in (-1, 0, 1):
        for dz in (-1, 0): put(x + dx, g + 1, z + dz, "polished_andesite")
        put(x + dx, g + 1, z + 1, "polished_andesite", shape="slab")    # the step
    for dx in (-1, 0, 1): put(x + dx, g + 2, z - 1, "dark_oak_planks"); put(x + dx, g + 3, z - 1, "dark_oak_planks")
    for dx in (-1, 1): put(x + dx, g + 2, z, "stripped_dark_oak_log_top", "stripped_dark_oak_log"); put(x + dx, g + 3, z, "stripped_dark_oak_log_top", "stripped_dark_oak_log")
    put(x, g + 2, z, "lantern", shape="lantern")
    for dx in (-1, 0, 1):
        for dz in (-1, 0): put(x + dx, g + 4, z + dz, "dark_oak_planks")
    for dx in range(-2, 3):                                            # gabled roof, ridge running across the front, eaves overhanging
        put(x + dx, g + 4, z + 1, "dark_oak_planks", shape="stair_n"); put(x + dx, g + 4, z - 2, "dark_oak_planks", shape="stair_s")
        put(x + dx, g + 5, z, "dark_oak_planks", shape="stair_n"); put(x + dx, g + 5, z - 1, "dark_oak_planks", shape="stair_s")
    for dx in (-1, 1):                                                 # the little torii: posts three high, thin tie-beam, a red lintel capped black
        for yy in (g + 1, g + 2, g + 3): put(x + dx, yy, z + 4, "red_concrete", shape="fence")
    put(x, g + 3, z + 4, "mangrove_planks", shape="slab_t")
    for dx in (-1, 0, 1): put(x + dx, g + 4, z + 4, "mangrove_planks")
    for dx in (-2, 2): put(x + dx, g + 4, z + 4, "mangrove_planks", shape="slab_t")
    for dx in range(-2, 3): put(x + dx, g + 5, z + 4, "polished_blackstone", shape="slab")
    for dz in (2, 3, 5, 6): put(x, g, z + dz, "gravel")
    for (dx, dz) in ((-2, 2), (2, 1), (-3, 0)): put(x + dx, g + 1, z + dz, "pink_petals", shape="carpet")
    sakura_tree(x + 5, z - 5, g, size=.7)
    place("道端の祠", x, z, "story", 4)

# 6. 狩人の休み場: a lean-to on the edge of the west woods, hides on a rack, a chopping block
s6 = None and find_spot(70, 300, need=4)
if s6:
    x, z = s6; g = at(x, z); level_pad(x - 4, z - 4, x + 4, z + 4, g)
    for dz in range(-2, 3):
        put(x - 2, g + 1, z + dz, "spruce_planks", shape="fence") if abs(dz) == 2 else None
        put(x - 2, g + 3, z + dz, "spruce_planks", shape="stair_w"); put(x - 1, g + 2, z + dz, "spruce_planks", shape="stair_w")
        put(x, g + 1, z + dz, "spruce_planks", shape="stair_w") if False else None
    put(x - 1, g + 1, z, "brown_wool", shape="slab")                 # bedroll
    for yy in range(g + 1, g + 3): put(x + 2, yy, z - 2, "spruce_planks", shape="fence"); put(x + 2, yy, z + 2, "spruce_planks", shape="fence")
    for dz in range(-1, 2): put(x + 2, g + 2, z + dz, "brown_wool", shape="shut_e")      # hides drying
    put(x + 3, g + 1, z + 3, "oak_log", "oak_log_top")                # chopping block
    put(x + 1, g + 1, z - 3, "campfire", shape="fire")
    place("狩人の休み場", x, z, "story", 4)

# 7. 小川の崩れた石橋: an old arched bridge over the stream; the keystone and middle of the span have fallen into the water,
# leaving the two halves of the arch reaching toward each other, the old ramps grassed over.
best = None
for i, (sx, sz) in enumerate(stream_pl):
    ix, iz = int(sx), int(sz)
    if 220 < sz < 340 and anyway[iz, ix] > 14 and (UPPER[max(0, iz - 14):iz + 15, max(0, ix - 14):ix + 15] < 0).all() and slope[iz, ix] < .8:
        best = (int(sx), int(sz)); break
if False:
    x, z = best; g = at(x, z)
    RA = 5.0; deck = g + 6
    STONE = ["stone_bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"]
    for side in (-1, 1):
        for k in range(0, 11):
            bx = x + side * k
            dk = deck - max(0, k - 6)                                  # the ramps down at each end
            under = g + math.sqrt(max(0, RA * RA - k * k)) if k < RA else -99
            broken = k < 2 or (k == 2 and side == 1)                   # the fallen middle, one side gone a little further
            if broken: continue
            top_k = dk if not (k == 2 or (k == 3 and side == 1)) else dk - 1   # the broken ends crumble lower
            for dz in (-1, 0, 1):
                gz = at(bx, z + dz)
                for yy in range(max(gz + 1, int(math.ceil(under))), top_k + 1):
                    put(bx, yy, z + dz, R.choice(STONE))
                if top_k == dk and 2 < k < 8:                           # turf on the old deck, a parapet stub here and there
                    put(bx, dk, z + dz, "moss_block" if dz == 0 or _h(bx, 0, z + dz, 5) < .5 else R.choice(STONE))
                if dz != 0 and 3 < k < 8 and _h(bx, 1, z + dz, 6) < .55: put(bx, dk + 1, z + dz, "stone_bricks", shape="slab")
            if k < RA and under > g:                                    # voussoir edge: the arch ring reads as cut stone
                for dz in (-1, 1): put(bx, int(math.ceil(under)), z + dz, "stone_bricks" if side == -1 else "mossy_stone_bricks")
    for (dx, dz, sh) in ((0, 1, "full"), (1, -1, "slab"), (-1, 2, "slab"), (2, 1, "full")):   # the fallen keystones in the water
        put(x + dx, at(x + dx, z + dz) + 1, z + dz, "mossy_stone_bricks", shape=sh)
    for k in range(3, 8):
        for side in (-1, 1):
            if _h(k, side, 0, 7) < .5: put(x + side * k, deck - max(0, k - 6) - 1 - int(_h(k, side, 1, 8) * 2), z + 2, "vine", shape="shut_s")
    place("小川の崩れた石橋", x, z, "story", 10)
print("authored places", [f[2] for f in FILL])

# ---------------- large set pieces (research-builds.md recipes): fewer, bigger, one hero element each ----------------
import sys as _sys2, setpieces as SP
SP.bind(_sys2.modules[__name__])
ZA = 206                                                             # the aqueduct crosses the mid level west-east; the north road runs through one arch
_rx = int(min(range(150, 260), key=lambda xx: road_d[ZA, xx]))
AQ = SP.aqueduct(_rx - (4 * 12 + 3 + 4), ZA, bays=7, collapsed=5, seed=21, root_from=(236, 150))
FT = SP.fallen_trunk(24, 262, .25, L=52, seed=31)
ST = SP.stump(322, 132, D=19, seed=41)
for (x, z, kind) in ((_rx, ZA, "aqueduct"), (50, 268, "trunk"), (322, 132, "stump")): CONTENT.append((x, z, kind))
LABELS_POI += [((_rx, ZA), "根に貫かれた水道橋", (220, 210, 190)), ((50, 268), "倒れた巨樹", (170, 220, 120)), ((322, 132), "巨大な切り株", (220, 190, 150))]
print("set pieces: aqueduct x0", _rx - 55, "springline", AQ["Ys"])

# ---------------- the dungeon entrance ----------------
# Under the great tree's knoll, opening south onto the path to the boss: two great roots frame a dark mouth in the bank,
# light glows from deep inside, two worn guardians stand on either side. A passage runs north under the trunk to the gate.
M0 = (241, 132)
yd = at(*M0)
level_pad(M0[0] - 5, M0[1] - 1, M0[0] + 5, M0[1] + 3, yd)            # a small level apron before the mouth
for x in range(M0[0] - 6, M0[0] + 7):
    for z in range(M0[1] - 19, M0[1] + 1):
        t = M0[1] - z                                                # depth into the bank
        d = abs(x - M0[0])
        rad = 2.5 if t < 13 else 4.5
        roof = (5 if t < 13 else 7) - (d / rad) ** 2 * 2
        top = max(at(x, z), yd + 8)
        for y in range(yd - 1, top + 1):
            hgt = y - yd
            if d <= rad and 1 <= hgt <= roof:
                w.b.pop((x, y, z), None)
            elif d <= rad + 2 and -1 <= hgt and (y <= at(x, z) or t >= 1):
                if t == 0 and y > at(x, z): continue
                if (x, y, z) not in w.b or w.b[(x, y, z)][0] != "full":
                    put(x, y, z, *(("rooted_dirt",) if _h(x, y, z, 81) < .55 else ("dirt",) if _h(x, y, z, 82) < .6 else ("coarse_dirt",)))
        if d <= rad:
            put(x, yd, z, *(("dirt_path_top", "dirt_path_side") if d < 1.2 and t < 13 else ("moss_block",) if _h(x, 0, z, 78) < .5 else ("rooted_dirt",)))
            if t > 2 and _h(x, 2, z, 79) < .25 and (x, yd + int(roof) + 1, z) in w.b:
                put(x, yd + int(roof), z, "hanging_roots", shape="plant")
for t in range(1, 13, 3):                                            # root strands across the passage ceiling
    pts = bez((M0[0] - 3.5, yd + 2, M0[1] - t), (M0[0], yd + 6.2, M0[1] - t - 1), (M0[0] + 3.5, yd + 2, M0[1] - t + .5), 8)
    for p0, p1 in zip(pts, pts[1:]): tube(p0, p1, .6, .6)
for t in (4, 9):
    put(M0[0] + (2 if t == 4 else -2), yd + 1, M0[1] - t, "shroomlight")
# the frame: two great roots coming down out of the knoll and crossing over the mouth
for side in (-1, 1):
    pts = bez((M0[0] + side * 7, yd + 9, M0[1] - 5), (M0[0] + side * 4.5, yd + 9, M0[1] + .5), (M0[0] - side * 1.5, yd + 6.5, M0[1] + .5), 10)
    pts += bez((M0[0] + side * 4.5, yd + 9, M0[1] + .5), (M0[0] + side * 4.2, yd + 3, M0[1] + 1.2), (M0[0] + side * 4.6, yd - 1, M0[1] + 1.5), 8)
    for i, (p0, p1) in enumerate(zip(pts, pts[1:])):
        r = 1.9 if i < 10 else 1.6 - (i - 10) * .06
        tube(p0, p1, r, r, moss_top=True)
for x in range(M0[0] - 2, M0[0] + 3):                                 # keep the opening clear of the frame
    for y in range(yd + 1, yd + 5):
        for z in range(M0[1] - 1, M0[1] + 3): w.b.pop((x, y, z), None)
for k in range(9):                                                   # vines and hanging roots over the lintel
    x = M0[0] - 4 + k
    for y in range(yd + 9, yd + 3, -1):
        if (x, y, M0[1] + 2) in w.b or (x, y, M0[1] + 1) in w.b:
            for j in range(1, 2 + int(_h(x, 0, 0, 83) * 3)):
                if (x, y - j, M0[1] + 3) not in w.b and y - j > yd + 2: put(x, y - j, M0[1] + 3, "vine" if _h(x, j, 0, 84) < .6 else "hanging_roots", shape="shut_s" if _h(x, j, 0, 84) < .6 else "plant")
            break
GX, GZ = M0[0], M0[1] - 17                                           # the gate in the chamber at the far end
for x in range(M0[0] - 7, M0[0] + 8):                                # seal the chamber: the knoll is hollow behind it
    for z in range(GZ - 3, GZ):
        for y in range(yd - 1, yd + 10): put(x, y, z, "rooted_dirt" if _h(x, y, z, 85) < .5 else "dirt")
for x in range(M0[0] - 7, M0[0] + 8):
    for z in range(GZ, M0[1] - 10):
        for y in (yd + 8, yd + 9):
            if (x, y, z) not in w.b: put(x, y, z, "dirt")
        for y in range(yd, yd + 8):
            if abs(x - M0[0]) > 4.5 and (x, y, z) not in w.b: put(x, y, z, "rooted_dirt")
for k in (-2, -1, 0, 1, 2):
    for y in range(yd + 1, yd + 7):
        frame = abs(k) == 2 or y == yd + 6
        put(GX + k, y, GZ, *(("chiseled_stone_bricks",) if frame and k == 0 else ("mossy_stone_bricks",) if frame else ("black_concrete",)))
for k in (-3, 3): put(GX + k, yd + 1, GZ + 1, "soul_lantern", shape="lantern")
LABELS_POI.append(((GX, GZ), "根の洞の門（ダンジョン）", (200, 230, 160)))
for sgn in (1, -1):                                                  # two worn guardians on the apron
    sx, sz = M0[0] + 6 * sgn, M0[1] + 2
    g = at(sx, sz)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1): put(sx + dx, g + 1, sz + dz, "stone_bricks" if (dx or dz) else "chiseled_stone_bricks", shape="slab" if (dx and dz) else "full")
    put(sx, g + 2, sz, "mossy_stone_bricks"); put(sx, g + 3, sz, "mossy_stone_bricks"); put(sx, g + 4, sz, "chiseled_stone_bricks")
    put(sx, g + 5, sz, "mossy_stone_bricks", shape="slab"); put(sx, g + 6, sz, "moss_block", shape="carpet")
mark(M0[0] - 9, M0[1] - 20, M0[0] + 9, M0[1] + 4)

# ---------------- secrets: places only the curious find ----------------
# 1. behind the falls: a dry pocket in the scarp, reached from the plunge pool
_fall = [(x, z) for z in range(300, 390) for x in range(B) if stream[z, x] and slope[z, x] > 1.0]
if _fall:
    fx, fz = _fall[len(_fall) // 2]
    lowz = max(range(fz - 6, fz + 7), key=lambda zz2: -at(fx, zz2) if 0 <= zz2 < B else -999)
    fy = at(fx, lowz) + 1                                          # pool level
    dirz = -1 if lowz > fz else 1                                  # into the rock, upstream
    for k in range(1, 9):
        cz2 = lowz + dirz * k
        for dx in (-2, -1, 0, 1, 2) if 2 < k < 8 else (-1, 0, 1):
            X = fx + dx + (2 if k > 1 else 0)
            for y in range(fy, fy + (4 if 2 < k < 8 else 3)):
                w.b.pop((X, y, cz2), None)
            put(X, fy - 1, cz2, R.choice(["mossy_cobblestone", "gravel", "moss_block", "stone"]))
    cache(fx + 3, fy, lowz + dirz * 7, "滝の裏の洞")
    put(fx + 1, fy, lowz + dirz * 7, "lantern", shape="lantern")
    for k in range(3): put(fx + 2 + R.randint(-1, 1), fy + 2 + (k == 0), lowz + dirz * (3 + k * 2), "glow_lichen", shape="shut_s")
# 2. under the stone tower: a stair down to a little crypt
TX_, TZ_ = TOWER; tg_ = at(TX_, TZ_)
for k in range(5):
    for y in range(tg_ - k - 1, tg_ - k + 3): w.b.pop((TX_ - 1, y, TZ_ - 1 + k), None); w.b.pop((TX_, y, TZ_ - 1 + k), None)
    put(TX_ - 1, tg_ - k - 1, TZ_ - 1 + k, "stone_bricks", shape="stair_s"); put(TX_, tg_ - k - 1, TZ_ - 1 + k, "stone_bricks", shape="stair_s")
for dx in range(-3, 3):
    for dz in range(4, 10):
        for y in range(tg_ - 6, tg_ - 2): w.b.pop((TX_ + dx, y, TZ_ + dz), None)
        put(TX_ + dx, tg_ - 7, TZ_ + dz, R.choice(["mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks"]))
        put(TX_ + dx, tg_ - 2, TZ_ + dz, "stone_bricks")
        for y in range(tg_ - 6, tg_ - 2):
            if dx in (-3, 2) or dz in (4, 9):
                if not (dz == 4 and dx in (-1, 0)): put(TX_ + dx, y, TZ_ + dz, R.choice(["mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks"]))
cache(TX_ + 1, tg_ - 6, TZ_ + 8, "石塔の地下")
put(TX_ - 2, tg_ - 6, TZ_ + 8, "bone_block_top", "bone_block_side", shape="slab"); put(TX_ - 2, tg_ - 6, TZ_ + 6, "soul_lantern", shape="lantern")
for k in range(4): put(TX_ + R.randint(-2, 1), tg_ - 3, TZ_ + R.randint(5, 8), "hanging_roots", shape="plant")
# 3. the pond floor: a sunken skiff with its cargo, seen through the water from the jetty
_deep = min(((x, z) for z in range(B) for x in range(B) if lake[z, x]), key=lambda p: hi[p[1], p[0]])
dx0, dz0 = _deep
for k in range(-3, 4):
    for j in (-1, 0, 1):
        if abs(k) == 3 and j: continue
        X, Z = dx0 + k, dz0 + j; y = hi[Z, X] + 1
        put(X, y, Z, "spruce_planks", shape="slab" if j == 0 else "full")
cache(dx0, hi[dz0, dx0] + 2, dz0, "池の底の小舟")
put(dx0 + 2, hi[dz0, dx0] + 2, dz0, "sea_lantern", shape="slab")
# 4. a ledge on the side of the second pillar, reached by a vine ladder from its top
P1x_, P1z_, P1y_, _, P1r_, _ = PILLARS[1]
lx, lz = P1x_ - int(P1r_ * .82), P1z_ + 6
while (lx, P1y_, lz) not in w.b and lx < P1x_: lx += 1
for k in range(1, 10): put(lx - 1, P1y_ - k, lz, "vine", shape="shut_w")
for dx in range(-4, 0):
    for dz in (-1, 0, 1):
        put(lx + dx, P1y_ - 10, lz + dz, "moss_block" if _h(dx, 0, dz, 9) < .5 else "mossy_cobblestone")
        if dx == -4 or abs(dz) == 1: pass
cache(lx - 3, P1y_ - 9, lz, "柱の岩棚")
put(lx - 2, P1y_ - 9, lz + 1, "fern", shape="plant")
for (x, z, what) in SECRETS: CONTENT.append((x, z, "secret"))
print("secrets", [s3 for _, _, s3 in SECRETS])

# ---------------- forests: old dark-oak forest (west, woodcutting), birch on the tree hill, spruce on the east ridge ----------------
forest = fbm(4, 6, .5, 121)
west = np.clip((150 - xx) / 120, 0, 1)
north = np.clip((60 - zz) / 60, 0, 1)
treehill = np.exp(-(((xx - TREE[0]) / 90) ** 2 + ((zz - TREE[1]) / 80) ** 2))
dens = np.clip((forest - .52) * 3 + west * .9 + north * .6 + treehill * .3, 0, 1) ** 2 * .06 + .0002   # woods are woods, meadows are open
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
            put(a, y, b, R.choice(["bush", "bush", "azalea_leaves", "flowering_azalea_leaves", "sweet_berry_bush_stage3", "fern"]), shape="plant")


EDGE_LO, EDGE_HI = .006, .02
for z in range(4, B - 4):
    for x in range(4, B - 4):
        if WATER_NEAR[z, x] < 18: continue                            # keep the water's surroundings open
        dz_ = dens[z, x]
        if EDGE_LO < dz_ < EDGE_HI:                                   # the woodland edge: low cover in front of the trees
            r2 = R.random()
            if r2 < .004: young_tree(x, z)
            elif r2 < .02: bush_clump(x, z)
            continue
        if noise3(x, 0, z, 610, 16) > .76: continue                    # a clearing: light comes down, ferns and flowers instead
        if R.random() < dens[z, x]:
            lf = R.randint(3, 6) if dens[z, x] > .02 else 0          # inside a wood the trunks stand tall and the roof is overhead
            if x > CLIFF_X - 4: oak(x, z, "spruce")
            elif west[z, x] > .3 and R.random() < .55: oak(x, z, "dark", big=R.random() < .3, lift=lf)
            elif treehill[z, x] > .4 and R.random() < .4: oak(x, z, "birch", lift=lf)
            else: oak(x, z, "oak", big=R.random() < .08, lift=lf)
FALLEN = 0                                                           # the forest floor: fallen trunks, mossy rocks
for _ in range(9000):
    x, z = R.randrange(10, B - 10), R.randrange(10, B - 10)
    if dens[z, x] < .025 or anyway[z, x] < 4 or slope[z, x] > .8 or WATER_NEAR[z, x] < 10: continue
    if R.random() < .55:
        a = R.random() * math.tau; L = R.randint(6, 11); kind = "dark_oak_log" if west[z, x] > .3 else "oak_log"
        cells = [(int(round(x + math.cos(a) * k)), int(round(z + math.sin(a) * k))) for k in range(L)]
        if any(not (2 <= cx < B - 2 and 2 <= cz < B - 2) or used[cz, cx] or anyway[cz, cx] < 3 for cx, cz in cells): continue
        g0 = at(x, z)
        for k, (cx, cz) in enumerate(cells):
            gy = max(at(cx, cz) + 1, g0 + 1 - k // 4)
            if (cx, gy, cz) in w.b and w.b[(cx, gy, cz)][0] == "full": break
            for yy in range(gy, gy + 2):
                if w.b.get((cx, yy, cz), ("",))[0] in ("plant", "carpet"): w.b.pop((cx, yy, cz), None)
            put(cx, gy, cz, kind, kind)
            r4 = _h(cx, gy, cz, 611)
            if r4 < .35: put(cx, gy + 1, cz, "moss_block", shape="carpet")
            elif r4 < .45: put(cx, gy + 1, cz, R.choice(["brown_mushroom", "red_mushroom", "fern"]), shape="plant")
        if R.random() < .6:                                          # the root plate it tore up
            cx, cz = cells[0]
            for dy in range(0, 3):
                for k in (-1, 0, 1):
                    px3, pz3 = int(round(cx - math.sin(a) * k - math.cos(a))), int(round(cz + math.cos(a) * k - math.sin(a)))
                    put(px3, at(cx, cz) + 1 + dy, pz3, "rooted_dirt" if dy < 2 else "moss_block", shape="full" if dy < 2 else "slab")
        mark(min(c[0] for c in cells), min(c[1] for c in cells), max(c[0] for c in cells), max(c[1] for c in cells))
    else:
        g0 = at(x, z); rr4 = R.uniform(1.2, 2.2)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(0, 3):
                    if (dx / rr4) ** 2 + (dz / (rr4 * .8)) ** 2 + (dy / (rr4 * .7)) ** 2 <= 1 + noise3(x + dx, dy, z + dz, 612, 1.5) * .3:
                        put(x + dx, g0 + dy, z + dz, "moss_block" if dy == int(rr4 * .7) or _h(dx, dy, dz, 613) < .3 else R.choice(["mossy_cobblestone", "stone", "andesite"]))
        mark(x - 3, z - 3, x + 3, z + 3)
    FALLEN += 1
    if FALLEN >= 60: break
LONE = []                                                            # a few big lone trees standing in the open: landmarks in the meadows
for _ in range(4000):
    x, z = R.randrange(15, B - 15), R.randrange(15, B - 15)
    if dens[z, x] > .004 or used[z, x] or slope[z, x] > .6 or WATER_NEAR[z, x] < 18 or anyway[z, x] < 8: continue
    if any(math.hypot(x - a, z - b2) < 45 for a, b2 in LONE): continue
    LONE.append((x, z)); oak(x, z, "oak", big=True, form=R.choice(["wide", "round", "wide"]))
    if len(LONE) >= 7: break
GROVES = []
for _ in range(3000):
    x, z = R.randrange(30, B - 30), R.randrange(30, B - 30)
    if dens[z, x] > .004 or used[z, x] or slope[z, x] > .6 or WATER_NEAR[z, x] < 20 or anyway[z, x] < 12: continue
    if any(math.hypot(x - a, z - b2) < 70 for a, b2 in GROVES + LONE): continue
    GROVES.append((x, z))
    oak(x, z, "oak", big=True, form="wide")
    for k in range(R.randint(4, 8)):
        a = R.uniform(0, math.tau); r = R.uniform(6, 13)
        tx, tz = int(x + math.cos(a) * r), int(z + math.sin(a) * r)
        if R.random() < .6: oak(tx, tz, R.choice(["oak", "oak", "birch"]), form=R.choice(["round", "tall", "lean"]))
        else: young_tree(tx, tz)
    if len(GROVES) >= 6: break
print("lone trees", len(LONE), "groves", len(GROVES))
scatter_plants(70, 280, 70, .05, ["fern", "short_grass", "brown_mushroom", "leaf_litter"])
# leaf litter under canopies: find ground cells shaded by leaves within 6 blocks above
for (x, y, z), v in list(w.b.items()):
    if v[1] in ("oak_leaves", "dark_oak_leaves", "birch_leaves") and y - at(x, z) <= 9 and _h(x, y, z, 51) < .12:
        g = at(x, z)
        if (x, g + 1, z) not in w.b and w.b.get((x, g, z), ("", "", ""))[1] in ("grass_block_top", "podzol_top", "coarse_dirt", "moss_block"):
            put(x, g + 1, z, "leaf_litter", shape="carpet")
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
        clump = noise3(x, 0, z, 401, 5.0)
        drift = noise3(x, 0, z, 402, 14.0)
        top = t2[1]
        if top == "grass_block_top":
            if anyway[z, x] < 5 and _h(x, 9, z, 48) < .6: continue      # trodden near paths
            if drift > .78 and _h(x, 2, z, 41) < (drift - .76) * 2.2 and FOREST_F[z, x] < .35:
                put(x, y, z, FLOWERS[int(noise3(x, 0, z, 403, 30) * 12) % 6], shape="plant")
            elif clump > .52 and _h(x, 3, z, 42) < (clump - .45) * 2.2:
                put(x, y, z, "tall_grass_top" if clump > .78 and _h(x, 4, z, 43) < .4 else "short_grass", shape="plant")
            elif FOREST_F[z, x] > .5 and _h(x, 5, z, 44) < .2 * max(0, noise3(x, 5, z, 453, 4) - .5) * 4:
                put(x, y, z, "fern", shape="plant")
        elif top in ("podzol_top", "moss_block", "coarse_dirt"):
            if _h(x, 6, z, 45) < (.3 if top == "podzol_top" else .16) * max(0, noise3(x, 6, z, 452, 4) - .45) * 4:
                k_ = R.choice(["fern", "fern", "short_grass", "brown_mushroom", "leaf_litter"]); put(x, y, z, k_, shape="carpet" if k_ == "leaf_litter" else "plant")
            elif top == "moss_block" and _h(x, 7, z, 46) < .25:
                put(x, y, z, "moss_block", shape="carpet")
        elif top in ("stone", "andesite", "gravel", "cobblestone") and _h(x, 8, z, 47) < .03:
            put(x, y, z, "mossy_cobblestone", shape="slab")




REEDS = [(POND[0] - 30, POND[1] - 8, 11), (POND[0] + 22, POND[1] + 14, 8), (236, 300, 7)]
# reeds and stones at the waterline: sugar cane in clumps, a few mossy stones, lily pads on the shallows
for z in range(2, B - 2):
    for x in range(2, B - 2):
        if lake[z, x] or stream[z, x] or road[z, x] or trail[z, x]: continue
        if not any(lake[z + dz, x + dx] or stream[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))): continue
        y = at(x, z) + 1
        if (x, y, z) in w.b and w.b[(x, y, z)][0] not in ("plant", "carpet"): continue
        r3 = noise3(x, 0, z, 421, 3)
        bed = any(math.hypot(x - a, z - b2) < rr for a, b2, rr in REEDS)
        if bed and r3 > .45 and _h(x, 1, z, 71) < .75:
            for k in range(1 + int(_h(x, 2, z, 72) * 3)): put(x, y + k, z, "sugar_cane", shape="plant")
        elif _h(x, 3, z, 73) < .05:
            put(x, y, z, "mossy_cobblestone")

# ---------------- night: things that glow, so the dark field still has a shape ----------------
# fireflies over the water's edge, lichen glowing under the great roots, a lantern at each place people stop
nff = 0
for _ in range(6000):
    x, z = R.randrange(5, B - 5), R.randrange(5, B - 5)
    if not (2 <= WATER_NEAR[z, x] <= 5) or road[z, x] or trail[z, x] or used[z, x]: continue
    for k in range(R.randint(4, 8)):
        a2, b2 = x + R.randint(-3, 3), z + R.randint(-3, 3)
        if 0 <= a2 < B and 0 <= b2 < B and not (lake[b2, a2] or stream[b2, a2] or road[b2, a2]) and (a2, at(a2, b2) + 1, b2) not in w.b:
            put(a2, at(a2, b2) + 1, b2, "firefly_bush", shape="plant")
    nff += 1
    if nff >= 16: break
nlich = 0
for (x, y, z), v in list(w.b.items()):
    if v[1] in ("dark_oak_wood", "mangrove_wood", "spruce_wood", "dark_oak_log", "mangrove_log") and y > at(x, z) + 3 and (x, y - 1, z) not in w.b and _h(x, y, z, 901) < .14:
        put(x, y - 1, z, "glow_lichen", shape="ceil"); nlich += 1
print("night: firefly clusters", nff, "lichen", nlich)

nfoot = 0
for z in range(2, B - 2):
    for x in range(2, B - 2):
        if anyway[z, x] < 2.5 or lake[z, x] or stream[z, x] or used[z, x]: continue
        h = hi[z, x]
        if (x, h + 1, z) in w.b or w.b.get((x, h, z), ("", "", ""))[1] not in ("grass_block_top", "podzol_top", "moss_block", "coarse_dirt"): continue
        if not any(hi[z + dz, x + dx] == h + 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))): continue
        r = _h(x, 77, z, 991)
        if r < .55: put(x, h + 1, z, "short_grass", shape="plant")
        elif r < .75: put(x, h + 1, z, "fern", shape="plant")
        elif r < .9: put(x, h + 1, z, "tall_grass_top", shape="plant")
        nfoot += 1
print("step-foot cover", nfoot)
# road cut banks: where the graded road sits a block below the turf, the bank lip is worn (rooted / coarse dirt) and grass overhangs it
nbank = 0
for z in range(2, B - 2):
    for x in range(2, B - 2):
        if road[z, x] or trail[z, x] or anyway[z, x] > 4.5: continue
        h = hi[z, x]
        if not any((road[z + dz, x + dx] or trail[z + dz, x + dx]) and hi[z + dz, x + dx] < h for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))): continue
        if w.b.get((x, h, z), ("", "", ""))[1] != "grass_block_top": continue
        r = noise3(x, 0, z, 993, 2.5)
        if r < .35: put(x, h, z, "rooted_dirt")
        elif r < .5: put(x, h, z, "coarse_dirt")
        if (x, h + 1, z) not in w.b and _h(x, 1, z, 994) < .6: put(x, h + 1, z, "short_grass" if r > .5 else "fern", shape="plant")
        nbank += 1
print("road banks", nbank)

# ---------- walkability pass: a slab on the low side of every one-block step along roads, trails, bridges and tops ----------
STEP_SLAB = ["mud_bricks", "mud_bricks", "mud_bricks", "cobblestone", "mud_bricks"]
def _top_at(x, z):
    return int(UPPER[z, x] if UPPER[z, x] >= 0 else hi[z, x])
nstep = 0
for z in range(1, B - 1):
    for x in range(1, B - 1):
        if not (road[z, x] or trail[z, x] or UPPER[z, x] >= 0) or lake[z, x] or stream[z, x]: continue
        y = _top_at(x, z)
        if (x, y + 1, z) in w.b and w.b[(x, y + 1, z)][0] not in ("plant", "carpet"): continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if _top_at(x + dx, z + dz) == y + 1 and (road[z + dz, x + dx] or trail[z + dz, x + dx] or UPPER[z + dz, x + dx] >= 0):
                on_path = road[z, x] or trail[z, x]
                if on_path:                                           # a half-buried flat stone, not a placeholder stair
                    put(x, y + 1, z, ("cobblestone", "mossy_cobblestone", "stone", "cobblestone")[int(_h(x, 0, z, 91) * 4)], shape="slab")
                else:
                    face = {(1, 0): "e", (-1, 0): "w", (0, 1): "s", (0, -1): "n"}[(dx, dz)]
                    put(x, y + 1, z, "stone_bricks", shape="stair_" + face)
                nstep += 1
                break
print("step slabs", nstep)

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
for _pass in range(3):                                              # tidy: blocks that touch nothing (left by diagonal tubes) are removed
    lone = [p for p, v in w.b.items() if v[0] in ("full", "slab", "slab_t") and not any(
        (p[0] + a, p[1] + b2, p[2] + c) in w.b for a, b2, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    for p in lone: w.b.pop(p, None)
FLOAT = []
for (x, y, z), v in w.b.items():
    if v[0] in ("full", "slab", "slab_t") and not any((x + a, y + b2, z + c) in w.b for a, b2, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
        FLOAT.append((x, y, z, v[1]))
STATS["floating"] = len(FLOAT)
print("floating sample", FLOAT[:12])
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
LABELS = LABELS + LABELS_POI

if __name__ == "__main__":
    print("blocks", len(w.b))
    import pickle
    np.save("upper.npy", UPPER); pickle.dump((w.b, hi, LABELS), open("field500.pkl", "wb")); pickle.dump((FILL, STATS), open("field500-fill.pkl", "wb"))


