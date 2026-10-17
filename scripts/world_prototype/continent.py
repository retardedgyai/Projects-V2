"""開拓大陸 open-world terrain study (MMO-style continent). Preview only.

The continent is 3072 x 3072 blocks, worked at 1024 x 1024 (one cell = 3 blocks).
Regions are placed by hand like an MMO world: the harbor on the south coast, tiers rising toward
the north, mountain ridges along region borders with passes where the roads cross.
"""
import heapq
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

N = 1024
SIZE_BLOCKS = 3072
SEA = 62.0
rng = np.random.default_rng(7)
yy, xx = np.mgrid[0:N, 0:N].astype(np.float32) / N   # y = south, x = east


def fbm(octaves=6, base=4, gain=.5, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((N, N), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = base * 2 ** o
        g = r.random((n + 1, n + 1)).astype(np.float32)
        img = Image.fromarray(g, mode="F").resize((N, N), Image.BICUBIC)
        out += amp * np.asarray(img)
        tot += amp
        amp *= gain
    return out / tot


def ridged(seed, base=6):
    return 1 - np.abs(fbm(5, base, .55, seed) * 2 - 1)


# ---------------- regions (hand placed) ----------------
# name, terrain style, tier, centre (x, y)
REGIONS = [
    ("開拓港", "meadow", 1, (.50, .86)),
    ("花筏の淵", "sakura", 1, (.69, .80)),
    ("泥炭の入江", "marsh", 1, (.31, .81)),
    ("緑風の谷", "meadow", 2, (.50, .65)),
    ("霧沼", "marsh", 2, (.17, .64)),
    ("夜桜の参道", "sakura", 2, (.82, .63)),
    ("風穴の崖", "cliffs", 2, (.33, .54)),
    ("狼の背", "highland", 2, (.67, .50)),
    ("白樫の森", "forest", 3, (.49, .40)),
    ("蛙鳴の湿原", "marsh", 3, (.14, .42)),
    ("段々の白壁", "cliffs", 3, (.84, .38)),
    ("雪原の砦", "highland", 3, (.30, .28)),
    ("紅霞の庭", "sakura", 4, (.64, .26)),
    ("熾火の火口", "volcano", 4, (.83, .14)),
    ("凍て牙の峰", "highland", 4, (.46, .12)),
    ("沈み葦の集落", "marsh", 4, (.16, .17)),
]
R = len(REGIONS)
C = np.array([r[3] for r in REGIONS], np.float32)

# domain warp so borders wander instead of being straight Voronoi lines
wx = (fbm(5, 2, .55, 11) - .5) * .22
wy = (fbm(5, 2, .55, 12) - .5) * .22
px, py = xx + wx, yy + wy
D = np.sqrt((px[None] - C[:, 0, None, None]) ** 2 + (py[None] - C[:, 1, None, None]) ** 2)   # R,N,N
order = np.argsort(D, axis=0)
i1, i2 = order[0], order[1]
d1 = np.take_along_axis(D, i1[None], 0)[0]
d2 = np.take_along_axis(D, i2[None], 0)[0]
Wt = np.exp(-(D - d1[None]) / .03)
Wt /= Wt.sum(0, keepdims=True)

# ---------------- land / sea ----------------
cx, cy = .5, .47
ell = np.sqrt(((xx - cx) / .47) ** 2 + ((yy - cy) / .47) ** 2)
coastn = fbm(6, 3, .55, 21)
land_v = 1 - ell + (coastn - .5) * .55
# carve a south bay for the harbor and a few inlets
bay = np.exp(-(((xx - .50) / .07) ** 2 + ((yy - .97) / .07) ** 2))
land_v -= bay * .45
land = land_v > .05
coast_t = np.clip((land_v - .05) / .12, 0, 1)        # 0 at the shore, 1 inland

# ---------------- per-style heights ----------------
f1, f2, f3 = fbm(6, 4, .5, 1), fbm(6, 8, .5, 2), fbm(5, 16, .5, 3)
rid = ridged(4, 5)


def style_height(style):
    if style == "meadow":
        return 68 + f1 * 14 + f2 * 6
    if style == "forest":
        return 72 + f1 * 22 + f2 * 8
    if style == "sakura":
        return 70 + f1 * 26 + f2 * 10
    if style == "marsh":
        return 61 + f2 * 6 + f3 * 3            # many pools just under the sea level
    if style == "cliffs":
        base = 74 + f1 * 70
        return np.floor(base / 12) * 12 + f3 * 3   # terraced mesas
    if style == "highland":
        return 96 + f1 * 40 + rid * 70
    if style == "volcano":
        vx, vy = .83, .14
        d = np.sqrt((xx - vx) ** 2 + (yy - vy) ** 2)
        cone = np.clip(1 - d / .16, 0, 1) ** 1.6 * 150
        crater = np.exp(-(d / .018) ** 2) * 55
        return 78 + f1 * 20 + cone - crater
    raise ValueError(style)


H = np.zeros((N, N), np.float32)
for k, (_, style, tier, _) in enumerate(REGIONS):
    H += Wt[k] * style_height(style)

# ---------------- mountain ridges on region borders ----------------
TIER = np.array([r[2] for r in REGIONS], np.float32)
pair_t = (TIER[i1] + TIER[i2]) / 2
# each border gets its own character: a mountain range, a line of broken hills, or nothing (open land)
pair_id = np.minimum(i1, i2) * 32 + np.maximum(i1, i2)
kind = (pair_id * 2654435761 % 97) % 4            # 0,1 mountains  2 hills  3 open
bw = .035 + fbm(4, 3, .5, 41) * .07                 # border width wanders
border = np.clip(1 - (d2 - d1) / bw, 0, 1)
gaps = np.clip((fbm(5, 6, .55, 42) - .35) * 3, 0, 1)   # ranges break up into massifs with saddles
massif = ridged(31, 9) ** 1.5 * (.4 + .6 * fbm(5, 5, .5, 43))
mount = border ** 1.3 * gaps * massif * (55 + 45 * pair_t)
hills = border ** 1.1 * fbm(5, 10, .55, 44) * 22
ridge_h = np.where(kind <= 1, mount, np.where(kind == 2, hills, 0))
open_start = (TIER[i1] == 1) & (TIER[i2] == 1)
ridge_h[open_start] *= .1


def blur(a, r):
    k = np.exp(-np.linspace(-2, 2, 2 * r + 1) ** 2); k /= k.sum()
    a = np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode="edge"), k, "valid"), 1, a)
    return np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode="edge"), k, "valid"), 0, a)


ridge_h = blur(ridge_h, 8)   # no seams where one border's character changes to another's
# free-standing peaks inside high regions and rolling macro relief everywhere
peaks = np.clip(ridged(51, 4) - .55, 0, None) * 2.2 * np.clip((TIER[i1] - 1.5) / 2.5, 0, 1) * 120
H += ridge_h + peaks + (fbm(6, 3, .5, 52) - .5) * 26

# shore: everything sinks to the sea toward the coast; sea floor outside
H = np.where(land, SEA + 1 + (H - SEA - 1) * coast_t, SEA - 4 - (1 - np.clip(land_v + .25, 0, 1)) * 30)
H = np.where(land & (H < SEA + .5) & (coast_t > .9) & (np.isin(i1, [k for k, r in enumerate(REGIONS) if r[1] != "marsh"])), SEA + 1, H)


# ---------------- roads: A* between neighbouring regions, they find the passes ----------------
def astar(cost, a, b):
    h, w = cost.shape
    dist = {a: 0.0}
    prev = {}
    pq = [(0.0, a)]
    while pq:
        _, cur = heapq.heappop(pq)
        if cur == b: break
        cd = dist[cur]
        y, x = cur
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if not dx and not dy: continue
                ny, nx = y + dy, x + dx
                if not (0 <= ny < h and 0 <= nx < w): continue
                nd = cd + cost[ny, nx] * (1.414 if dx and dy else 1)
                if nd < dist.get((ny, nx), 1e18):
                    dist[(ny, nx)] = nd
                    prev[(ny, nx)] = cur
                    heapq.heappush(pq, (nd + math.hypot(b[0] - ny, b[1] - nx), (ny, nx)))
    path = [b]
    while path[-1] != a: path.append(prev[path[-1]])
    return path[::-1]


S = 256  # road search grid
k = N // S
Hs = H.reshape(S, k, S, k).mean((1, 3))
gy, gx = np.gradient(Hs)
slope = np.hypot(gx, gy)
water_s = Hs < SEA + .5
cost = 1 + slope ** 2 * 1.2 + water_s * 60 + np.clip(Hs - 110, 0, None) * .4

adj = set()
for a, b in zip(i1[border > .3].ravel(), i2[border > .3].ravel()):
    adj.add((min(a, b), max(a, b)))
# every region connects to its lowest-tier neighbour (the way in), plus a ring of extra links
links = set()
for r in range(R):
    nb = [o for (a, b) in adj for o in ((b,) if a == r else (a,) if b == r else ())]
    if not nb: continue
    nb.sort(key=lambda o: (TIER[o], np.hypot(*(C[o] - C[r]))))
    links.add((min(r, nb[0]), max(r, nb[0])))
    if len(nb) > 1 and TIER[nb[1]] <= TIER[r]: links.add((min(r, nb[1]), max(r, nb[1])))
roads = []
for a, b in sorted(links):
    pa = (int(C[a, 1] * S), int(C[a, 0] * S)); pb = (int(C[b, 1] * S), int(C[b, 0] * S))
    path = astar(cost, pa, pb)
    pts = np.array([(y * k + k / 2, x * k + k / 2) for y, x in path], np.float32)
    # smooth the polyline (chaikin) so roads curve
    for _ in range(3):
        q = np.empty((len(pts) * 2 - 2, 2), np.float32)
        q[0::2] = pts[:-1] * .75 + pts[1:] * .25
        q[1::2] = pts[:-1] * .25 + pts[1:] * .75
        pts = np.vstack([pts[:1], q, pts[-1:]])
    roads.append(((a, b), pts))

# carve the roads: a gently graded bed, cutting passes through the ridges
roadmask = np.zeros((N, N), np.float32)
Hc = H.copy()
for _, pts in roads:
    iy = np.clip(pts[:, 0].astype(int), 0, N - 1); ix = np.clip(pts[:, 1].astype(int), 0, N - 1)
    prof = np.maximum(H[iy, ix], SEA + 1.5)
    kern = np.ones(31) / 31
    prof = np.convolve(np.pad(prof, 15, mode="edge"), kern, "valid")
    for (y, x), hv in zip(pts, prof):
        y0, x0 = int(y), int(x)
        for dy in range(-6, 7):
            for dx in range(-6, 7):
                yy2, xx2 = y0 + dy, x0 + dx
                if not (0 <= yy2 < N and 0 <= xx2 < N): continue
                d = math.hypot(dy, dx)
                if d > 6: continue
                t = 1 if d < 1.8 else (1 - (d - 1.8) / 4.2) ** 2
                Hc[yy2, xx2] = Hc[yy2, xx2] * (1 - t) + hv * t if t == 1 or Hc[yy2, xx2] > hv else Hc[yy2, xx2]
                if d < 1.3: roadmask[yy2, xx2] = 1
H = Hc

# ---------------- rivers: priority-flood, then follow the downhill to the sea ----------------
M = 512
Hm = np.asarray(Image.fromarray(H, mode="F").resize((M, M), Image.BILINEAR)) + (fbm(6, 12, .65, 61)[::2, ::2] - .5) * 22
filled = np.full((M, M), np.inf, np.float32)
pq = []
for y in range(M):
    for x in range(M):
        if Hm[y, x] < SEA or y in (0, M - 1) or x in (0, M - 1):
            filled[y, x] = Hm[y, x]; heapq.heappush(pq, (float(Hm[y, x]), y, x))
down = {}
while pq:
    hv, y, x = heapq.heappop(pq)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
        ny, nx = y + dy, x + dx
        if 0 <= ny < M and 0 <= nx < M and filled[ny, nx] == np.inf:
            filled[ny, nx] = max(Hm[ny, nx], hv + 1e-3)
            down[(ny, nx)] = (y, x)
            heapq.heappush(pq, (float(filled[ny, nx]), ny, nx))
acc = np.ones((M, M), np.float32)
for (y, x) in sorted(down, key=lambda p: -filled[p]):
    dy, dx = down[(y, x)]
    acc[dy, dx] += acc[y, x]
river_m = (acc > 700) & (Hm > SEA - 1)
river = np.asarray(Image.fromarray(river_m.astype(np.float32) * np.sqrt(np.clip(acc / 900, 1, 40)), mode="F").resize((N, N), Image.BILINEAR))
_k = np.array([1, 4, 6, 4, 1], np.float32) / 16
river = np.apply_along_axis(lambda r: np.convolve(r, _k, "same"), 1, river)
river = np.apply_along_axis(lambda r: np.convolve(r, _k, "same"), 0, river)
# meander: sample the river field through a smooth warp so straight flood-fill runs bend
mw1 = (fbm(4, 10, .5, 81) - .5) * 26
mw2 = (fbm(4, 10, .5, 82) - .5) * 26
iy = np.clip((yy * N + mw1).astype(int), 0, N - 1); ix = np.clip((xx * N + mw2).astype(int), 0, N - 1)
river = river[iy, ix]
river_mask = (river > .22) & land & (roadmask < .5) & (H > SEA - 2)
# shallow channel: 2-4 blocks under the smoothed valley floor, never a gorge down to sea level
valley = blur(H, 5)
H = np.where(river_mask, np.minimum(H, np.maximum(valley - 2.2 - np.clip(river, 0, 3) * .6, SEA - 1.5)), H).astype(np.float32)
np.save("continent_river.npy", river_mask.astype(np.uint8))

np.save("continent_H.npy", H)
np.save("continent_region.npy", i1.astype(np.int8))
np.save("continent_road.npy", roadmask.astype(np.uint8))

# ---------------- render the world map ----------------
# (ground colour, second ground colour, canopy colour, tree density)
PAL = {"meadow": ((124, 172, 80), (150, 182, 92), (70, 120, 52), .18),
       "forest": ((92, 140, 64), (80, 124, 60), (46, 92, 44), .75),
       "sakura": ((120, 168, 86), (140, 176, 96), (232, 160, 196), .42),
       "marsh": ((96, 122, 80), (118, 130, 84), (62, 96, 58), .25),
       "cliffs": ((198, 172, 128), (172, 150, 112), (96, 128, 70), .10),
       "highland": ((120, 136, 104), (140, 146, 120), (52, 90, 70), .30),
       "volcano": ((86, 70, 64), (110, 84, 70), (60, 60, 50), .03)}
patch = fbm(6, 20, .6, 71)[..., None]
treen = fbm(6, 14, .55, 72)
speck = rng.random((N, N)).astype(np.float32)
base = np.zeros((N, N, 3), np.float32)
dens = np.zeros((N, N), np.float32)
canopy = np.zeros((N, N, 3), np.float32)
for kk, (_, style, _, _) in enumerate(REGIONS):
    g1, g2, cc, de = PAL[style]
    base += Wt[kk][..., None] * (np.array(g1, np.float32) * (1 - patch) + np.array(g2, np.float32) * patch)
    dens += Wt[kk] * de
    canopy += Wt[kk][..., None] * np.array(cc, np.float32)
gy, gx = np.gradient(H)
sl = np.hypot(gx, gy)
rock = np.clip((sl - 1.8) / 2.5, 0, 1)[..., None] * .85
base = base * (1 - rock) + np.array([112, 106, 98], np.float32) * rock
snow = np.clip((H - 205 + (patch[..., 0] - .5) * 30) / 14, 0, 1)[..., None]
base = base * (1 - snow) + np.array([236, 240, 244], np.float32) * snow
trees = (treen > 1 - dens * .75) & (speck < .6) & (H > SEA + 1) & (H < 160) & (sl < 2.2)
base = np.where(trees[..., None], canopy * (.8 + .4 * speck[..., None]), base)
beach = (land & (H < SEA + 2.5) & (sl < 1.2))[..., None]
base = np.where(beach, np.array([222, 206, 160], np.float32), base)
# hillshade (light from the north-west)
lx, ly, lz = -1, -1, 1.3
nrm = np.sqrt(gx ** 2 + gy ** 2 + 1)
shade = np.clip((-gx * lx - gy * ly + lz) / (nrm * math.sqrt(lx * lx + ly * ly + lz * lz)), 0, 1)
base *= (.45 + .75 * shade)[..., None]
# contour lines every 20 blocks
cont = (np.floor(H / 20) != np.floor(np.roll(H, 1, 0) / 20)) | (np.floor(H / 20) != np.floor(np.roll(H, 1, 1) / 20))
base = np.where((cont & land)[..., None], base * .82, base)
water = H < SEA
depth = np.clip((SEA - H) / 30, 0, 1)[..., None]
wcol = np.array([82, 150, 196], np.float32) * (1 - depth) + np.array([26, 58, 112], np.float32) * depth
base = np.where(water[..., None], wcol, base)
base = np.where(river_mask[..., None], np.array([70, 140, 200], np.float32), base)
water = water | river_mask
base = np.where((roadmask > 0)[..., None], np.array([226, 204, 150], np.float32), base)
img = Image.fromarray(base.clip(0, 255).astype(np.uint8)).resize((1600, 1600), Image.LANCZOS)
d = ImageDraw.Draw(img, "RGBA")
s = 1600 / N
font = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 22)
small = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 16)
TCOL = {1: (120, 220, 120), 2: (240, 220, 90), 3: (250, 150, 70), 4: (240, 80, 80)}
for name, style, tier, (cxr, cyr) in REGIONS:
    X, Y = cxr * 1600, cyr * 1600
    tw = d.textlength(name, font=font)
    d.rounded_rectangle((X - tw / 2 - 10, Y - 18, X + tw / 2 + 10 + 34, Y + 18), 9, fill=(16, 18, 28, 200))
    d.text((X - 0 + 0, Y), name, fill=(250, 250, 250), font=font, anchor="mm")
    d.rounded_rectangle((X + tw / 2 + 6, Y - 12, X + tw / 2 + 40, Y + 12), 6, fill=TCOL[tier] + (255,))
    d.text((X + tw / 2 + 23, Y), f"T{tier}", fill=(20, 20, 20), font=small, anchor="mm")
hx, hy = .50 * 1600, .915 * 1600
d.polygon([(hx, hy - 16), (hx + 12, hy), (hx, hy + 16), (hx - 12, hy)], fill=(255, 220, 60), outline=(40, 30, 0))
# scale bar
d.rectangle((40, 1540, 40 + 1600 * 500 / SIZE_BLOCKS, 1548), fill=(240, 240, 240))
d.text((40, 1520), "500 ブロック", fill=(240, 240, 240), font=small)
img.save("continent-map.png")
print("land", land.mean(), "roads", len(roads), "H", H.min(), H.max())
