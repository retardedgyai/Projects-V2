"""開拓大陸 v2: Wynncraft / FF14-style world — one main road from the harbor, zones strung along it like beads.

2048 x 2048 blocks worked at 1024 x 1024 (one cell = 2 blocks).
Terrain classes:
  walk  - zone basins: gentle, no jumping needed (target slope < 0.5 block per block)
  climb - passes: the road crosses the ridge in a graded notch (grade <= 1 in 4)
  view  - ridges and the world rim: scenery, may be steep
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

N = 1024
BPC = 2                      # blocks per cell
SEA = 62.0
yy, xx = np.mgrid[0:N, 0:N].astype(np.float32) / N


def fbm(octaves=6, base=4, gain=.5, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((N, N), np.float32); amp = 1.0; tot = 0.0
    for o in range(octaves):
        n = base * 2 ** o
        g = r.random((n + 1, n + 1)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray(g, mode="F").resize((N, N), Image.BICUBIC)); tot += amp; amp *= gain
    return out / tot


def blur(a, r):
    k = np.exp(-np.linspace(-2, 2, 2 * r + 1) ** 2); k /= k.sum()
    a = np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode="edge"), k, "valid"), 1, a)
    return np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode="edge"), k, "valid"), 0, a).astype(np.float32)


# ---------------- zones along the road (name, style, tier, centre, basin floor) ----------------
REGIONS = [
    ("開拓港", "meadow", 1, (.50, .90), 66),
    ("羊飼いの原", "meadow", 1, (.32, .78), 70),
    ("花筏の淵", "sakura", 1, (.55, .67), 74),
    ("緑風の谷", "forest", 2, (.74, .55), 80),
    ("霧沼", "marsh", 2, (.50, .44), 66),
    ("白樫の森", "forest", 3, (.28, .33), 92),
    ("雪原の砦", "highland", 3, (.42, .19), 112),
    ("熾火の火口", "volcano", 4, (.68, .09), 120),
    # side branches off the main road
    ("風穴の崖", "cliffs", 2, (.15, .58), 86),
    ("紅霞の庭", "sakura", 4, (.78, .30), 104),
]
SPINE = [0, 1, 2, 3, 4, 5, 6, 7]
BRANCHES = [(1, 8), (4, 9)]
R = len(REGIONS)
C = np.array([r[3] for r in REGIONS], np.float32)
FLOOR = np.array([r[4] for r in REGIONS], np.float32)


def catmull(pts, n=40):
    pts = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = map(np.array, pts[i - 1:i + 3])
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(np.array(pts[-2]))
    return np.array(out, np.float32)


def road_poly(ids):
    # extra bends between zones so the road wanders instead of running straight
    pts = []
    rng = np.random.default_rng(sum(ids))
    for a, b in zip(ids, ids[1:]):
        pa, pb = C[a], C[b]
        pts.append(pa)
        mid = (pa + pb) / 2
        nrm = np.array([-(pb - pa)[1], (pb - pa)[0]]); nrm /= np.linalg.norm(nrm) + 1e-9
        pts.append(mid + nrm * rng.uniform(-.05, .05))
    pts.append(C[ids[-1]])
    return catmull(pts)


roads = [("main", road_poly(SPINE))] + [("branch", road_poly([a, b])) for a, b in BRANCHES]
# harbor approach: from the quay straight up into town
_app = np.stack([np.full(30, C[0, 0]), np.linspace(.965, C[0, 1], 30, endpoint=False)], 1).astype(np.float32)
roads[0] = ("main", np.vstack([_app, roads[0][1]]))


def dist_to_poly(poly):
    d = np.full((N, N), 9.0, np.float32)
    idx = np.zeros((N, N), np.int32)
    for i in range(len(poly) - 1):
        a, b = poly[i], poly[i + 1]
        ab = b - a; L2 = float(ab @ ab) + 1e-12
        t = np.clip(((xx - a[0]) * ab[0] + (yy - a[1]) * ab[1]) / L2, 0, 1)
        dd = np.hypot(xx - a[0] - t * ab[0], yy - a[1] - t * ab[1])
        m = dd < d; d[m] = dd[m]; idx[m] = i
    return d, idx


road_d = np.full((N, N), 9.0, np.float32)
for _, poly in roads:
    road_d = np.minimum(road_d, dist_to_poly(poly)[0])

# ---------------- basins (Voronoi of the zones, borders wander) ----------------
wx = (fbm(5, 2, .55, 11) - .5) * .12
wy = (fbm(5, 2, .55, 12) - .5) * .12
px, py = xx + wx, yy + wy
# (stretch along, stretch across, angle, size bias) — long valleys, round craters, wide plains
SHAPE = [(1.0, 1.0, 0, .00), (1.5, .8, .4, .02), (1.0, 1.0, 0, .00), (1.9, .6, -.7, .01), (1.3, 1.0, .2, .02),
         (1.2, .9, 1.0, .02), (1.4, .8, .1, .01), (1.0, 1.0, 0, .00), (1.1, .9, .9, .01), (1.3, .8, -.5, .00)]
D = np.empty((R, N, N), np.float32)
for k, (sa, sc, ang, bias) in enumerate(SHAPE):
    dx, dy = px - C[k, 0], py - C[k, 1]
    u = dx * math.cos(ang) + dy * math.sin(ang); v = -dx * math.sin(ang) + dy * math.cos(ang)
    D[k] = np.sqrt((u / sa) ** 2 + (v / sc) ** 2) - bias
order = np.argsort(D, axis=0)
i1, i2 = order[0], order[1]
d1 = np.take_along_axis(D, i1[None], 0)[0]
d2 = np.take_along_axis(D, i2[None], 0)[0]
Wt = np.exp(-(D - d1[None]) / .035); Wt /= Wt.sum(0, keepdims=True)

floor = (Wt * FLOOR[:, None, None]).sum(0)
# rolling ground: broad swells plus walkable hills (~50-block wavelength, slope well under 1 in 2)
gentle = (fbm(5, 3, .5, 21) - .5) * 18 + (fbm(4, 10, .5, 23) - .5) * 14 + (fbm(4, 24, .5, 22) - .5) * 3
H = floor + gentle

# ---------------- each zone gets its own landform ----------------
WL = np.full((N, N), SEA, np.float32)          # water surface (lakes sit above the sea)
VIEW = np.zeros((N, N), bool)                   # scenery that is allowed to be steep
LAVA = np.zeros((N, N), bool)
ICE = np.zeros((N, N), bool)


def local(k):
    sa, sc, ang, _ = SHAPE[k]
    dx, dy = xx - C[k, 0], yy - C[k, 1]
    return dx * math.cos(ang) + dy * math.sin(ang), -dx * math.sin(ang) + dy * math.cos(ang)


def lake(cx, cy, r, depth, k, frozen=False):
    global H
    ang = np.arctan2(yy - cy, xx - cx)
    lob = 1 + .28 * np.sin(ang * 3 + cx * 9) + .18 * np.sin(ang * 5 + cy * 7)     # bays and headlands
    d = np.hypot((xx - cx) / r, (yy - cy) / (r * .75)) / lob + (fbm(5, 24, .55, int(cx * 1e3)) - .5) * .35
    level = FLOOR[k] + 1
    bowl = np.clip(1 - d, 0, 1)
    shore = np.clip((1.25 - d) / .25, 0, 1)
    H = np.where(d < 1.25, H * (1 - shore) + (level + 1) * shore, H)        # flat beach around the water
    H = np.where(d < 1, level - .5 - bowl ** .6 * depth, H)
    WL[d < 1] = level
    if frozen: ICE[d < 1] = True


m = [Wt[k] for k in range(R)]
# 1 harbor: the town rises in two broad terraces from the quay
H += m[0] * (np.clip((.93 - yy) / .05, 0, 1) * 4 + np.clip((.87 - yy) / .04, 0, 1) * 4)
# 2 shepherds' plain: long drumlin hills, a pond
u, v = local(1)
for (a, b, hgt) in ((-.06, .02, 9), (.0, -.03, 11), (.07, .01, 8), (-.02, .06, 7), (.05, -.06, 9)):
    H += m[1] * hgt * np.exp(-(((u - a) / .05) ** 2 + ((v - b) / .022) ** 2))
lake(.27, .84, .025, 4, 1)
# 3 blossom pool: a wide lake (the 淵) ringed by low knolls, a falls feeding it from the north
lake(.60, .70, .05, 7, 2)
u, v = local(2)
H += m[2] * 6 * np.clip(np.sin(np.hypot(u - .05, v - .03) * 70) , 0, 1)
# 4 green-wind valley: a long trough, walls rising on both sides (the walls are scenery)
u, v = local(3)
wall = np.clip((np.abs(v) - .035) / .04, 0, 1) ** 1.6 * 40
H += m[3] * wall
VIEW |= (m[3] > .5) & (np.abs(v) > .05)
# 5 mist marsh: low ground with many pools and islets
pools = (fbm(5, 18, .55, 91) > .58) & (m[4] > .6) & (road_d > .012)
H = np.where(pools, FLOOR[4] - 1.5, H - m[4] * 6)
WL[pools] = FLOOR[4] - .2
# 6 white-oak forest: a raised plateau (mesa) in the middle, the road climbs onto it
u, v = local(5)
mesa = np.clip((.06 - np.hypot(u / 1.2, v)) / .012, 0, 1)
H += m[5] * mesa * 16
VIEW |= (mesa > .05) & (mesa < .95) & (m[5] > .5)
# 7 snowfield fort: a frozen lake under a ring of peaks
lake(.40, .15, .04, 3, 6, frozen=True)
u, v = local(6)
pk = np.zeros((N, N), np.float32)
craggy = 1 - np.abs(fbm(5, 20, .55, 96) * 2 - 1)
for (a, b, r, hgt) in ((.33, .10, .035, 75), (.47, .08, .03, 62), (.30, .21, .028, 55), (.52, .15, .025, 48), (.38, .05, .03, 70)):
    d = np.hypot(xx - a, yy - b) / r
    pk = np.maximum(pk, np.clip(1 - d, 0, 1) ** 1.9 * hgt * 1.5 * (.7 + .6 * craggy))
H += pk
VIEW |= pk > 6
# 8 ember crater: a volcano; the rim is scenery, the caldera floor is walkable, lava lake in the middle
dv = np.hypot(xx - C[7, 0], yy - C[7, 1]) + (fbm(5, 8, .55, 97) - .5) * .05
ang = np.arctan2(yy - C[7, 1], xx - C[7, 0])
gully = 1 - .35 * np.clip(np.sin(ang * 13 + fbm(4, 6, .5, 98) * 6) , 0, 1) * np.clip((dv - .06) / .04, 0, 1)
cone = np.clip(1 - np.abs(dv - .075) / .05, 0, 1) ** 1.5 * 60 * gully
H += m[7] * cone
VIEW |= (cone > 8) & (m[7] > .4)
LAVA |= (dv < .022) & (m[7] > .5)
H = np.where(LAVA, FLOOR[7] - 1, H)
# 2' windhole cliffs: buttes standing on a walkable floor
u, v = local(8)
butte = np.zeros((N, N), np.float32)
edge_n = (fbm(5, 30, .55, 99) - .5) * .5
for (a, b, r, hgt) in ((.10, .52, .03, 30), (.17, .64, .024, 24), (.07, .65, .02, 36), (.22, .53, .018, 20), (.13, .74, .022, 26)):
    d = np.hypot(xx - a, (yy - b) * 1.2) / r + edge_n
    butte = np.maximum(butte, np.where(d < 1, hgt, np.clip(1.4 - d, 0, .4) / .4 * hgt * .35))
H += butte
VIEW |= (butte > 2) & (butte < 18)
# 5' crimson garden: hillsides cut into one-block terraces (walkable with half steps)
u, v = local(9)
hill = m[9] * 22 * np.exp(-((u / .08) ** 2 + (v / .06) ** 2))
H += np.floor(hill) - hill * 0      # 1-block steps

# ridges between zones: scenery; a notch where a road crosses = the pass
border = np.clip(1 - (d2 - d1) / (.05 + fbm(4, 3, .5, 31) * .05), 0, 1)
massif = (1 - np.abs(fbm(5, 7, .55, 32) * 2 - 1)) ** 1.4
TIER = np.array([r[2] for r in REGIONS], np.float32)
ridge = border ** 1.4 * (30 + 22 * (TIER[i1] + TIER[i2]) / 2) * (.5 + .5 * massif)
notch = np.clip((road_d - .018) / .05, 0, 1) ** 1.5
H += blur(ridge * notch, 5)

# world rim: past the play corridor the land climbs into mountains (and the sea to the south)
corr = np.minimum(road_d, d1 * .9)
rim = np.clip((corr - .16) / .10, 0, 1) ** 1.5 * (70 + 60 * massif)
H += rim
sea = np.clip((yy - .93) / .04, 0, 1) * np.clip(1 - np.abs(xx - .5) / .45, 0, 1) + np.clip((yy - .97) / .02, 0, 1)
H = H * (1 - np.clip(sea, 0, 1)) + (SEA - 10) * np.clip(sea, 0, 1)
H = np.where(sea > .55, np.minimum(H, SEA - 3), H)

# ---------------- grade the roads (max 1 in 4) and carve them ----------------
MAXG = .25 * N / BPC   # height change per unit of normalised distance
roadmask = np.zeros((N, N), np.float32)
road_level = np.full((N, N), np.nan, np.float32)
for kind, poly in roads:
    iy = np.clip((poly[:, 1] * N).astype(int), 0, N - 1); ix = np.clip((poly[:, 0] * N).astype(int), 0, N - 1)
    prof = np.maximum(blur(H, 3)[iy, ix], SEA + 2)
    seg = np.r_[0, np.hypot(*np.diff(poly, axis=0).T)]
    for _ in range(40):  # clamp grade forwards and backwards
        for i in range(1, len(prof)): prof[i] = np.clip(prof[i], prof[i - 1] - MAXG * seg[i], prof[i - 1] + MAXG * seg[i])
        for i in range(len(prof) - 2, -1, -1): prof[i] = np.clip(prof[i], prof[i + 1] - MAXG * seg[i + 1], prof[i + 1] + MAXG * seg[i + 1])
    dd, idx = dist_to_poly(poly)
    level = prof[np.clip(idx, 0, len(prof) - 1)]
    width = .004 if kind == "main" else .003
    t = np.clip(1 - (dd - width) / .018, 0, 1); t = t * t * (3 - 2 * t)
    H = H * (1 - t) + level * t
    roadmask = np.maximum(roadmask, (dd < width).astype(np.float32))

# lakes and pools stay lakes even where a road came near: put the water back (roads go round)
H = np.where((WL > SEA) & (road_d > .012), np.minimum(H, WL - .5), H)

# ---------------- a river from the marsh down to the sea, beside the road ----------------
rpts = catmull([(.47, .46), (.42, .53), (.45, .60), (.41, .67), (.44, .74), (.39, .81), (.43, .88), (.42, .995)], 30)
rpts = rpts + np.stack([np.sin(np.linspace(0, 40, len(rpts))) * .006, np.zeros(len(rpts))], 1).astype(np.float32)
rd, _ = dist_to_poly(rpts)
river_mask = (rd < .0045) & (road_d > .016)   # the road crosses on a bridge: no cut beside it
valley = blur(H, 6)
bank = np.clip(1 - (rd - .0045) / .02, 0, 1) * np.clip((road_d - .016) / .02, 0, 1)
H = np.where(river_mask, np.maximum(valley - 2.5, SEA - 1.5), H * (1 - bank * .5) + (valley - 1) * bank * .5).astype(np.float32)

np.save("spine_H.npy", H.astype(np.float32))
np.save("spine_region.npy", i1.astype(np.int8))
np.save("spine_road.npy", roadmask.astype(np.uint8))
np.save("spine_river.npy", river_mask.astype(np.uint8))
np.save("spine_wl.npy", WL)
np.save("spine_lava.npy", LAVA.astype(np.uint8))
np.save("spine_ice.npy", ICE.astype(np.uint8))

# ---------------- walkability report ----------------
gy, gx = np.gradient(H)
slope = np.hypot(gx, gy) / BPC          # blocks up per block across
play = (rim < 5) & (ridge * notch < 6) & (H > WL) & ~VIEW
walk = slope < .5
print("scenery share", round(float((VIEW & (rim < 5)).mean() / (rim < 5).mean()), 3), "play area km^2", round(play.mean() * (N * BPC / 1000) ** 2, 2),
      "walkable share of play area", round((walk & play).sum() / play.sum(), 3),
      "road max grade", round(float(np.nanmax(np.where(roadmask > 0, slope, np.nan))), 2))

# ---------------- render the map ----------------
PAL = {"meadow": ((124, 172, 80), (150, 182, 92), (70, 120, 52), .2), "forest": ((92, 140, 64), (80, 124, 60), (46, 92, 44), .7),
       "sakura": ((120, 168, 86), (140, 176, 96), (232, 160, 196), .45), "marsh": ((96, 122, 80), (118, 130, 84), (62, 96, 58), .3),
       "cliffs": ((198, 172, 128), (172, 150, 112), (96, 128, 70), .12), "highland": ((120, 136, 104), (140, 146, 120), (52, 90, 70), .35),
       "volcano": ((96, 78, 70), (116, 90, 76), (60, 60, 50), .04)}
patch = fbm(6, 20, .6, 71)[..., None]
treen = fbm(6, 14, .55, 72)
speck = np.random.default_rng(3).random((N, N)).astype(np.float32)
base = np.zeros((N, N, 3), np.float32); dens = np.zeros((N, N), np.float32); can = np.zeros((N, N, 3), np.float32)
for k, (_, st, _, _, _) in enumerate(REGIONS):
    g1, g2, cc, de = PAL[st]
    base += Wt[k][..., None] * (np.array(g1) * (1 - patch) + np.array(g2) * patch)
    dens += Wt[k] * de; can += Wt[k][..., None] * np.array(cc, np.float32)
rock = np.clip((slope - .8) / 1.2, 0, 1)[..., None]
base = base * (1 - rock) + np.array([112, 106, 98], np.float32) * rock
snow = np.clip((H - 175 + (patch[..., 0] - .5) * 30) / 14, 0, 1)[..., None]
base = base * (1 - snow) + np.array([236, 240, 244], np.float32) * snow
trees = (treen > 1 - dens * .75) & (speck < .6) & (H > SEA + 1) & (slope < 1) & (roadmask < .5) & (road_d > .008)
base = np.where(trees[..., None], can * (.8 + .4 * speck[..., None]), base)
base = np.where((walk & play)[..., None] & False, base, base)
nrm = np.sqrt(gx ** 2 + gy ** 2 + 1)
shade = np.clip((gx + gy + 1.3) / (nrm * 2.03), 0, 1)
base *= (.5 + .7 * shade)[..., None]
water = (H < WL) | river_mask
depth = np.clip((WL - H) / 25, 0, 1)[..., None]
wcol = np.array([82, 150, 196], np.float32) * (1 - depth) + np.array([30, 64, 118], np.float32) * depth
base = np.where(water[..., None], np.where(river_mask[..., None], np.array([74, 144, 204], np.float32), wcol), base)
base = np.where((ICE & water)[..., None], np.array([196, 222, 240], np.float32), base)
base = np.where(LAVA[..., None], np.array([236, 110, 30], np.float32), base)
base = np.where((roadmask > 0)[..., None], np.array([232, 210, 156], np.float32), base)
S = 1600
img = Image.fromarray(base.clip(0, 255).astype(np.uint8)).resize((S, S), Image.LANCZOS)
d = ImageDraw.Draw(img, "RGBA")
font = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 22)
small = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 16)
TCOL = {1: (120, 220, 120), 2: (240, 220, 90), 3: (250, 150, 70), 4: (240, 80, 80)}
# number the zones in travel order so the route reads at a glance
order_label = {z: str(i + 1) for i, z in enumerate(SPINE)}
order_label[8] = "2'"; order_label[9] = "5'"
for k, (name, st, tier, (cx, cy), _) in enumerate(REGIONS):
    X, Y = cx * S, cy * S
    txt = f"{order_label[k]}  {name}"
    tw = d.textlength(txt, font=font)
    d.rounded_rectangle((X - tw / 2 - 10, Y - 18, X + tw / 2 + 48, Y + 18), 9, fill=(16, 18, 28, 210))
    d.text((X + 0, Y), txt, fill=(250, 250, 250), font=font, anchor="mm")
    d.rounded_rectangle((X + tw / 2 + 8, Y - 12, X + tw / 2 + 42, Y + 12), 6, fill=TCOL[tier] + (255,))
    d.text((X + tw / 2 + 25, Y), f"T{tier}", fill=(20, 20, 20), font=small, anchor="mm")
d.rectangle((40, 1540, 40 + S * 500 / (N * BPC), 1548), fill=(240, 240, 240))
d.text((40, 1518), "500 ブロック", fill=(240, 240, 240), font=small)
img.save("spine-map.png")
