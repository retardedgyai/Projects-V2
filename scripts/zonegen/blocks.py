"""Stage 7: turn the solved height and water maps into blocks.

Every column is filled solid from below its lowest neighbour up to its top, so a column can never be a hole or a thin
shell that shows the void. Surface material follows slope, wetness and what the cell is (road, shore, pad...).
Blocks go into a dict {(x, y, z): (shape, top, side)} — the format iso.py, fp.py and export_world.py already read.
"""
import math
import numpy as np
import terrainmat as TM
from greattree import noise3, _h


class Blocks:
    def __init__(self, zone):
        self.z = zone
        self.b = {}

    def put(self, x, y, z, top, side=None, shape="full"):
        self.b[(x, y, z)] = (shape, top, side or top)

    def build(self):
        Z = self.z; B = Z.B; hi = Z.hi; kind = Z.kind; water = Z.water
        gy, gx = np.gradient(Z.H.astype(np.float32))
        slope = Z.slope
        expo = np.clip((-gx * .6 - gy * .8) / np.maximum(slope, 1e-3), -1, 1)
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
                face = slope[z, x] > 1.25 and k == 0
                top_face = int(hi_nb[z, x])
                for y in range(lo, h):
                    depth = h - y
                    if face or depth > 3:
                        fr = (y - lo) / max(1, top_face - lo)
                        put(x, y, z, TM.cliff_block(x, y, z, fr, 0.0, float(expo[z, x])))
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
                    name = "dirt_path_top" if e < .55 + (v - .5) * .5 else "coarse_dirt"
                    put(x, h, z, name, TM.SIDE.get(name, name))
                    continue
                if face:
                    put(x, h, z, TM.cliff_block(x, h, z, 1.0, 0.0, float(expo[z, x])))
                    continue
                if k == 6:                                                # shore: grass to the water, a little mud
                    name = "grass_block_top"
                    put(x, h, z, name, TM.SIDE.get(name, "grass_block_side" if name == "grass_block_top" else name))
                    continue
                top = TM.surface(x, z, float(slope[z, x]), float(forest[z, x]), 0.0)
                put(x, h, z, *top)
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
