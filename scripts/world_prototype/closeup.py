"""Isometric block-level close-up of one window of the continent (3 blocks per map cell)."""
import math
import random
import sys
import numpy as np
from PIL import Image
import iso
import importlib.util, types
_src = open("continent.py").read().split("# ---------------- land / sea")[0]
_m = types.ModuleType("cont"); exec(_src, _m.__dict__)
REGIONS = _m.REGIONS
SEA = 62.0

H = np.load("continent_H.npy")
REG = np.load("continent_region.npy")
ROAD = np.load("continent_road.npy")
RIV = np.load("continent_river.npy")
cx, cy, cells = float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3])
out = sys.argv[4]
N = H.shape[0]
x0, y0 = int(cx * N - cells / 2), int(cy * N - cells / 2)
sub = H[y0:y0 + cells, x0:x0 + cells]
B = cells * 3
hb = np.asarray(Image.fromarray(sub, mode="F").resize((B, B), Image.BICUBIC))
det = np.asarray(Image.fromarray(np.random.default_rng(5).random((B // 6 + 1, B // 6 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
hb = hb + (det - .5) * 2.5
# mountains get crags: block-scale ridged detail that grows with altitude
crag = np.asarray(Image.fromarray(np.random.default_rng(6).random((B // 22 + 1, B // 22 + 1)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
crag = 1 - np.abs(crag * 2 - 1)
hb = hb + crag ** 2 * np.clip((hb - 110) / 60, 0, 1) * 14
reg = np.repeat(np.repeat(REG[y0:y0 + cells, x0:x0 + cells], 3, 0), 3, 1)
road = np.repeat(np.repeat(ROAD[y0:y0 + cells, x0:x0 + cells], 3, 0), 3, 1)
riv = np.asarray(Image.fromarray(RIV[y0:y0 + cells, x0:x0 + cells].astype(np.float32), mode="F").resize((B, B), Image.BILINEAR)) > .5
hi = np.round(hb).astype(int)
hi = np.where(road > 0, np.round(np.asarray(Image.fromarray(hb, mode="F").resize((B // 3, B // 3), Image.BILINEAR).resize((B, B), Image.BILINEAR))).astype(int), hi)
base = int(min(hi.min(), SEA - 3)) - 8
hi -= base
sea = int(SEA) - base
style = np.vectorize(lambda r: REGIONS[r][1])(reg)
rnd = random.Random(3)
w = iso.World()
gy, gx = np.gradient(hb)
sl = np.hypot(gx, gy)

TOPS = {"meadow": ("grass_block_top", "grass_block_side"), "forest": ("podzol_top", "podzol_side"),
        "sakura": ("grass_block_top", "grass_block_side"), "marsh": ("mud",),
        "cliffs": ("terracotta",), "highland": ("grass_block_top", "grass_block_side"), "volcano": ("blackstone",)}
for z in range(B):
    for x in range(B):
        h = hi[z, x]
        nb = min(hi[max(0, z - 1), x], hi[min(B - 1, z + 1), x], hi[z, max(0, x - 1)], hi[z, min(B - 1, x + 1)])
        lo = max(0, h - 7) if x in (0, B - 1) or z in (0, B - 1) else max(0, min(nb, h) - 1)
        st = style[z, x]
        for y in range(lo, h):
            w.set(x, y, z, "stone" if h - y > 3 or st in ("cliffs", "volcano") or h + base > 140 or sl[z, x] > 1.6 else ("dirt" if st != "cliffs" else "terracotta"))
        if riv[z, x] and h > sea:
            w.set(x, h, z, "gravel" if (x * 7 + z) % 3 else "sand")
            w.set(x, h + 1, z, "water_still", shape="water")
            continue
        if h <= sea + 1 and h < sea + 1:
            top = ("sand",) if st != "marsh" else ("mud",)
        elif road[z, x]:
            top = ("dirt_path_top", "dirt_path_side") if (x + z) % 5 else ("gravel",)
        elif h + base > 200 + (det[z, x] - .5) * 30 and sl[z, x] < 2.4:
            top = ("snow",)
        elif sl[z, x] > 2.0 or (h + base > 150 and sl[z, x] > 1.3):
            top = (rnd.choice(["stone", "andesite", "stone", "tuff"]),)
        elif h + base > 205 + (det[z, x] - .5) * 30:
            top = ("snow",)
        elif h + base > 190 + (det[z, x] - .5) * 30:
            top = ("snow", "grass_block_snow")
        elif h <= sea + 2:
            top = ("sand",)
        else:
            top = TOPS[st]
        w.set(x, h, z, *top)
        for y in range(h + 1, sea + 1):
            w.set(x, y, z, "water_still", shape="water")


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


DENS = {"meadow": .006, "forest": .05, "sakura": .02, "marsh": .008, "cliffs": .003, "highland": .02, "volcano": 0}
KIND = {"meadow": "oak", "forest": "oak", "sakura": "cherry", "marsh": "oak", "cliffs": "oak", "highland": "spruce"}
patch = np.asarray(Image.fromarray(np.random.default_rng(9).random((12, 12)).astype(np.float32), mode="F").resize((B, B), Image.BICUBIC))
for z in range(3, B - 3):
    for x in range(3, B - 3):
        st = style[z, x]
        h = hi[z, x]
        if h <= sea + 1 or road[z, x] or riv[z, x] or sl[z, x] > 1.2 or h + base > 190: continue
        if rnd.random() < DENS.get(st, 0) * (patch[z, x] * 2.2):
            tree(x, z, KIND[st] if not (st == "forest" and rnd.random() < .3) else "spruce")

print("blocks", len(w.b))
img = iso.render(w, W=int(sys.argv[5]) if len(sys.argv) > 5 else 8)
img.save(out)
print(img.size)
