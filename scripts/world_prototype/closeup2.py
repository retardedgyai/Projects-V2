"""Isometric block-level close-up of one window of the continent (3 blocks per map cell)."""
import math
import random
import sys
import numpy as np
from PIL import Image
import iso
import importlib.util, types
_src = open("spine.py").read().split("def catmull")[0]
_m = types.ModuleType("cont"); exec(_src, _m.__dict__)
REGIONS = _m.REGIONS
SEA = 62.0

H = np.load("spine_H.npy")
REG = np.load("spine_region.npy")
ROAD = np.load("spine_road.npy")
RIV = np.load("spine_river.npy")
WLm = np.load("spine_wl.npy"); LAVm = np.load("spine_lava.npy"); ICEm = np.load("spine_ice.npy")
cx, cy, cells = float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3])
out = sys.argv[4]
N = H.shape[0]
x0, y0 = int(cx * N - cells / 2), int(cy * N - cells / 2)
sub = H[y0:y0 + cells, x0:x0 + cells]
F = 2
B = cells * F
hb = np.asarray(Image.fromarray(sub, mode="F").resize((B, B), Image.BICUBIC))
det = np.asarray(Image.fromarray(np.random.default_rng(5).random((B // 6 + 1, B // 6 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
hb = hb + (det - .5) * 2.5
# mountains get crags: block-scale ridged detail that grows with altitude
crag = np.asarray(Image.fromarray(np.random.default_rng(6).random((B // 22 + 1, B // 22 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
crag = 1 - np.abs(crag * 2 - 1)
hb = hb + crag ** 2 * np.clip((hb - 110) / 60, 0, 1) * 14
# biome edges are ragged, not ruler lines: sample the region map through block-scale jitter
_j = np.random.default_rng(12)
_jy = (np.arange(B)[:, None] // F + _j.integers(-5, 6, (B, B))).clip(0, cells - 1)
_jx = (np.arange(B)[None, :] // F + _j.integers(-5, 6, (B, B))).clip(0, cells - 1)
reg = REG[y0:y0 + cells, x0:x0 + cells][_jy, _jx]
road = np.repeat(np.repeat(ROAD[y0:y0 + cells, x0:x0 + cells], F, 0), F, 1)
up = lambda a: np.repeat(np.repeat(a[y0:y0 + cells, x0:x0 + cells], F, 0), F, 1)
wl_b = up(WLm); lava_b = up(LAVm) > 0; ice_b = up(ICEm) > 0
riv = np.asarray(Image.fromarray(RIV[y0:y0 + cells, x0:x0 + cells].astype(np.float32), mode="F").resize((B, B), Image.BILINEAR)) > .5
hi = np.round(hb).astype(int)
hi = np.where(road > 0, np.round(np.asarray(Image.fromarray(hb, mode="F").resize((B // F, B // F), Image.BILINEAR).resize((B, B), Image.BILINEAR))).astype(int), hi)
base = int(min(hi.min(), SEA - 3)) - 8
hi -= base
sea = int(SEA) - base
style = np.vectorize(lambda r: REGIONS[r][1])(reg)
rnd = random.Random(3)
w = iso.World()
gy, gx = np.gradient(hb)
sl = np.hypot(gx, gy)

TOPS = {"meadow": ("grass_block_top", "grass_block_side"), "forest": ("podzol_top", "podzol_side"),
        "sakura": ("grass_block_top", "grass_block_side"), "marsh": ("grass_block_top", "grass_block_side"),
        "cliffs": ("red_sand",), "highland": ("grass_block_top", "grass_block_side"), "volcano": ("blackstone",)}
patch2 = np.asarray(Image.fromarray(np.random.default_rng(21).random((B // 10 + 1, B // 10 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        nb = min(hi[max(0, z - 1), x], hi[min(B - 1, z + 1), x], hi[z, max(0, x - 1)], hi[z, min(B - 1, x + 1)])
        lo = max(0, h - 7) if x in (0, B - 1) or z in (0, B - 1) else max(0, min(nb, h) - 1)
        st = style[z, x]
        for y in range(lo, h):
            if st == "cliffs":
                w.set(x, y, z, ("terracotta", "orange_terracotta", "terracotta", "white_terracotta", "red_sand")[(y // 3) % 5]); continue
            if st == "volcano":
                w.set(x, y, z, "blackstone" if (y // 4) % 3 else "basalt_side"); continue
            w.set(x, y, z, "stone" if h - y > 3 or st in ("cliffs", "volcano") or h + base > 140 or sl[z, x] > 1.6 else ("dirt" if st != "cliffs" else "terracotta"))
        if riv[z, x] and h > sea:
            w.set(x, h, z, "gravel" if (x * 7 + z) % 3 else "sand")
            w.set(x, h + 1, z, "water_still", shape="water")
            continue
        if h <= sea + 1 and h < sea + 1:
            top = ("sand",) if st != "marsh" else ("mud",)
            if st == "marsh" and sea - h <= 1 and (x * 7 + z * 11) % 9 == 0: w.set(x, sea + 1, z, "lily_pad", shape="carpet")
        elif road[z, x]:
            top = ("dirt_path_top", "dirt_path_side") if (x + z) % 5 else ("gravel",)
        elif h + base > 200 + (det[z, x] - .5) * 30 and sl[z, x] < 2.4:
            top = ("snow",)
        elif st == "cliffs" and sl[z, x] > 1.0:
            top = ("terracotta",) if (h // 3) % 2 else ("orange_terracotta" if (h // 3) % 4 == 1 else "white_terracotta",)
        elif st == "volcano" and sl[z, x] > .9:
            top = (rnd.choice(["basalt_top", "blackstone", "smooth_basalt"]),)
        elif sl[z, x] > 2.0 or (h + base > 150 and sl[z, x] > 1.3):
            top = (rnd.choice(["stone", "andesite", "stone", "tuff"]),)
        elif h + base > 205 + (det[z, x] - .5) * 30:
            top = ("snow",)
        elif h + base > 190 + (det[z, x] - .5) * 30:
            top = ("snow", "grass_block_snow")
        elif h <= sea + 2 and st != "marsh":
            top = ("sand",)
        elif st == "marsh" and (h <= sea + 3 or det[z, x] > .7):
            top = ("mud",) if (x * 3 + z * 5) % 4 else ("moss_block",)
        else:
            top = TOPS[st]
            pz = patch2[z, x]
            if st == "cliffs":
                top = ("grass_block_top", "grass_block_side") if pz > .62 else ("coarse_dirt",) if pz > .5 else ("red_sand",) if pz > .3 else ("terracotta",)
            elif st == "volcano":
                top = ("tuff",) if pz > .6 else ("gravel",) if pz > .48 else ("smooth_basalt",) if pz > .3 else ("blackstone",)
                if pz < .12 and (x * 13 + z * 7) % 5 == 0: top = ("magma",)
        w.set(x, h, z, *top)
        wl = int(round(wl_b[z, x])) - base
        if lava_b[z, x]:
            w.set(x, h, z, "magma"); w.set(x, h + 1, z, "lava_still", shape="water")
        for y in range(h + 1, max(sea, wl) + 1):
            w.set(x, y, z, "ice" if ice_b[z, x] and y == wl else "water_still", shape="full" if ice_b[z, x] and y == wl else "water")


def tree(x, z, kind):
    h = hi[z, x]
    if kind == "spruce":
        th = rnd.randint(7, 11)
        for y in range(h + 1, h + th + 1): w.set(x, y, z, "spruce_log_top", "spruce_log")
        for y in range(h + 3, h + th + 2):
            r = max(0, int((h + th + 1 - y) / 2.6))
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + (y % 2) and (x + dx, y, z + dz) not in w.b: w.set(x + dx, y, z + dz, "spruce_leaves")
        return
    leaves, log = {"cherry": ("cherry_leaves", "cherry_log"), "oak": ("oak_leaves", "oak_log"),
                   "birch": ("oak_leaves", "dark_oak_log")}[kind]
    th = rnd.randint(4, 6)
    for y in range(h + 1, h + th + 1): w.set(x, y, z, log + "_top" if log != "dark_oak_log" else "dark_oak_log_top", log)
    r = 2.6 if kind != "cherry" else 3.2
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                q = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (2.2 ** 2)
                if q <= 1 - rnd.random() * .25 and (x + dx, h + th + dy, z + dz) not in w.b:
                    w.set(x + dx, h + th + dy, z + dz, leaves)


DENS = {"meadow": .006, "forest": .05, "sakura": .02, "marsh": .008, "cliffs": .006, "highland": .02, "volcano": 0}
KIND = {"meadow": "oak", "forest": "oak", "sakura": "cherry", "marsh": "oak", "cliffs": "oak", "highland": "spruce"}
patch = np.asarray(Image.fromarray(np.random.default_rng(9).random((12, 12)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
for z in range(3, B - 3):
    for x in range(3, B - 3):
        st = style[z, x]
        h = hi[z, x]
        if h <= sea + 1 or road[z, x] or riv[z, x] or sl[z, x] > 1.2 or h + base > 190: continue
        if rnd.random() < DENS.get(st, 0) * (patch[z, x] * 2.2):
            tree(x, z, KIND[st] if not (st == "forest" and rnd.random() < .3) else "spruce")

for z in range(B):
    for x in range(B):
        h = hi[z, x]
        top = w.b.get((x, h, z))
        if not top or top[0] != "full" or top[1] in ("water_still", "snow", "stone", "andesite", "tuff"): continue
        if (x, h + 1, z) in w.b: continue
        up = [hi[z + dz, x + dx] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < B and 0 <= z + dz < B]
        if any(u == h + 1 for u in up) and sl[z, x] < 1.2:
            w.set(x, h + 1, z, top[1] if top[1] != "grass_block_top" else "moss_block" if False else top[1], top[2], shape="slab")
print("blocks", len(w.b))
img = iso.render(w, W=int(sys.argv[5]) if len(sys.argv) > 5 else 8)
img.save(out)
print(img.size)
