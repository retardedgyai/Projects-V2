"""Stage 7: turn the solved height and water maps into blocks.

Every column is filled solid from below its lowest neighbour up to its top, so a column can never be a hole or a thin
shell that shows the void. Surface material follows slope, wetness and what the cell is (road, shore, pad...).
Blocks go into a dict {(x, y, z): (shape, top, side)} — the format iso.py, fp.py and export_world.py already read.
"""
import math
import numpy as np
import terrainmat as TM
import cliffs
from greattree import noise3, _h


class Blocks:
    def __init__(self, zone):
        self.z = zone
        self.b = {}

    def put(self, x, y, z, top, side=None, shape="full"):
        self.b[(x, y, z)] = (shape, top, side or top)

    def build(self):
        Z = self.z; B = Z.B; hi = Z.hi; kind = Z.kind; water = Z.water
        slope, expo, concav = cliffs.fields(Z.hi.astype(np.float32))
        Z.slope = slope
        W = cliffs.warp(Z, slope)                                          # carved faces: vertical, ribbed, bedded
        for (x, z), (t, ys) in W.items(): hi[z, x] = t
        self.carved = W
        p = np.pad(hi, 1, mode="edge")
        lo_nb = np.minimum.reduce([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1], hi])
        hi_nb = np.maximum.reduce([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1], hi])
        road_d = getattr(Z, "road_d", np.full((B, B), 99, np.float32))
        forest = getattr(Z, "forest", np.zeros((B, B), np.float32))
        put = self.put
        for z in range(B):
            for x in range(B):
                h = int(hi[z, x]); k = int(kind[z, x])
                lo = int(lo_nb[z, x]) - 3
                if (x, z) in W:
                    t, ys = W[(x, z)]; S = set(ys); lo2 = min(ys)
                    rim = t >= int(hi_nb[z, x])
                    for y in ys:
                        if y == t and rim:                                 # the rim: turf on top; where the side shows, an overgrown lip of moss, not a dirt band
                            put(x, y, z, *(("moss_block",) if (y - 1) not in S or t - int(lo_nb[z, x]) >= 2 else ("grass_block_top", "grass_block_side"))); continue
                        fr = (y - lo2) / max(1, int(hi_nb[z, x]) - lo2)
                        if (y + 1) not in S and noise3(x, y * 2, z, 441, 6) > .5:   # green gathers on every shelf the wall throws out
                            put(x, y, z, "moss_block"); continue
                        put(x, y, z, cliffs.face_block(x, y, z, float(expo[z, x]), fr, (y - 1) not in S and y > lo2, t - y))
                    continue
                face = slope[z, x] > 1.25 and k == 0
                top_face = int(hi_nb[z, x])
                drop = h - int(lo_nb[z, x])                               # how far this column's side is exposed
                soil = 3 if drop < 2 else (1 if drop > 2 and noise3(x, 0, z, 395, 3) > .6 else 0)   # 1-block contour lines stay soil; a taller side shows rock, at most one row of soil
                for y in range(lo, h):
                    depth = h - y
                    if face or depth > soil:
                        fr = (y - lo) / max(1, top_face - lo)
                        put(x, y, z, cliffs.block(x, y, z, fr, float(expo[z, x]), float(concav[z, x]), depth))
                    else:
                        put(x, y, z, "dirt" if depth > 1 or k in (1, 2) else ("coarse_dirt" if _h(x, y, z, 5) < .25 else "dirt"))
                # the top block
                if k == 3:                                                # lake bed
                    put(x, h, z, TM.bed_block(x, z))
                    for y in range(h + 1, int(water[z, x]) + 1): put(x, y, z, "water_still", shape="water")
                    continue
                if k == 4:                                                # stream bed
                    put(x, h, z, TM.bed_block(x, z))
                    for y in range(h + 1, int(water[z, x]) + 1): put(x, y, z, "water_still", shape="water")
                    continue
                if k in (1, 2):
                    w = 2.6 if k == 1 else 1.6
                    e = road_d[z, x] / w
                    v = noise3(x, 0, z, 331, 3.0)
                    u = noise3(x, 3, z, 333, 2.2) * .85 + _h(x, 3, z, 333) * .15     # in 2-4 block patches, not per-block speckle
                    if e < .55 + (v - .5) * .5: name = "dirt_path_top" if u < .6 else ("coarse_dirt" if u < .9 else "gravel")     # tread: path 60 / coarse 30 / gravel 10
                    else: name = "coarse_dirt" if u < .4 else ("grass_block_top" if u < .9 else "dirt_path_top")             # shoulder: worn patches feathering into the grass
                    put(x, h, z, name, TM.SIDE.get(name, name))
                    continue
                if face:
                    put(x, h, z, cliffs.block(x, h, z, 1.0, float(expo[z, x]), float(concav[z, x])))
                    continue
                if k == 7:                                                # verge beside a road: mostly grass, coarse dirt in short fingers
                    name = "coarse_dirt" if noise3(x, 0, z, 334, 2.5) > .78 else "grass_block_top"
                    put(x, h, z, name, "grass_block_side" if name == "grass_block_top" else name)
                    continue
                if k == 6:                                                # shore: grass to the water, a little mud
                    name = "grass_block_top"
                    put(x, h, z, name, TM.SIDE.get(name, "grass_block_side" if name == "grass_block_top" else name))
                    continue
                top = TM.surface(x, z, float(slope[z, x]), float(forest[z, x]), 0.0)
                put(x, h, z, *top)
        # around the carved faces no soil shows sideways: an exposed dirt side becomes rock, an exposed turf block becomes moss
        near = set()
        for (x, z) in W:
            for dx in (-2, -1, 0, 1, 2):
                for dz in (-2, -1, 0, 1, 2): near.add((x + dx, z + dz))
        for z in range(1, B - 1):                                           # and every other face 3+ high (road verges on a rim too)
            for x in range(1, B - 1):
                if kind[z, x] in (0, 7) and int(hi[z, x]) - int(lo_nb[z, x]) >= 3: near.add((x, z))
        for (x, z) in near:
            if not (0 <= x < B and 0 <= z < B) or kind[z, x] not in (0, 7): continue
            h = int(hi[z, x])
            for y in range(h - 6, h + 1):
                v = self.b.get((x, y, z))
                if not v or v[1] not in ("dirt", "coarse_dirt", "grass_block_top"): continue
                if all((x + a, y, z + c) in self.b for a, c in ((1, 0), (-1, 0), (0, 1), (0, -1))): continue
                if v[1] == "grass_block_top": put(x, y, z, "moss_block")
                else: put(x, y, z, cliffs.face_block(x, y, z, float(expo[z, x]), .8, False, h - y))
        self.cliff_stats = cliffs.finish(self, Z, slope, concav)
        import random as _r
        self.cliff_stats.update(cliffs.sculpt(self, Z, _r.Random(5)))
        self.falls()
        self.bridges()
        return self.b

    def falls(self):
        """Where a stream steps down, the upper water spills over: water blocks stand in the lower cell up to the upper level."""
        Z = self.z; B = Z.B
        st = Z.kind == 4
        for z in range(1, B - 1):
            for x in range(1, B - 1):
                if not st[z, x]: continue
                w0 = int(Z.water[z, x])
                up = max([int(Z.water[z + dz, x + dx]) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if st[z + dz, x + dx]] + [w0])
                for y in range(w0 + 1, up): self.put(x, y, z, "water_still")

    def bridges(self):
        """A road over a stream gets a plank deck at road height, with posts and a rail on both sides."""
        Z = self.z; B = Z.B
        self.bridge_cells = []
        for (kind, pts, prof, w) in Z.routes:
            for i, (px, pz) in enumerate(pts):
                X, Zc = int(round(px)), int(round(pz))
                if not (0 <= X < B and 0 <= Zc < B) or Z.kind[Zc, X] != 4: continue
                deck = max(int(round(prof[i])), int(Z.water[Zc, X]) + 1)
                if i + 1 < len(pts): dx, dz = pts[i + 1][0] - px, pts[i + 1][1] - pz
                else: dx, dz = px - pts[i - 1][0], pz - pts[i - 1][1]
                L = math.hypot(dx, dz) or 1; nx, nz = -dz / L, dx / L
                for s in np.arange(-w - 1, w + 1.01, .5):
                    x2, z2 = int(round(px + nx * s)), int(round(pz + nz * s))
                    edge = abs(s) > w
                    if edge:
                        if Z.kind[z2, x2] == 4: self.put(x2, deck + 1, z2, "spruce_planks", shape="fence")
                    else:
                        self.put(x2, deck, z2, "spruce_planks", shape="slab_t")
                        self.bridge_cells.append((x2, z2, deck))
