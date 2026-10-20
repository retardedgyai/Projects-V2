"""Stage 8-9: set pieces and vegetation, placed into a reservation map.

Every piece is declared in the spec with a footprint radius; before anything is built the footprint is checked against
roads, water and other pieces, and reserved. Trees and ground cover are scattered only outside reservations and never on
or right beside a route. The building helpers (put, at, tube, bez, mark, oak) are the small vocabulary the piece builders
(setpieces.py, greattree.py) are written in.
"""
import math
import random

import numpy as np
from greattree import noise3, _h, bark
import terrainmat as TM


class Ctx:
    def __init__(self, zone, blocks):
        self.z = zone; self.bl = blocks; self.w = blocks
        self.B = zone.B
        self.hi = zone.hi
        self.used = np.zeros((zone.B, zone.B), bool)
        self.route = (zone.kind == 1) | (zone.kind == 2)
        self.wet = (zone.kind == 3) | (zone.kind == 4)
        self.road = self.route
        self.R = random.Random(zone.spec.get("seed", 1) + 99)
        self.pieces = []

    # ---- vocabulary ----
    def put(self, x, y, z, top, side=None, shape="full"):
        if 0 <= x < self.B and 0 <= z < self.B:
            self.bl.b[(x, y, z)] = (shape, top, side or top)

    def at(self, x, z):
        return int(self.hi[min(self.B - 1, max(0, z)), min(self.B - 1, max(0, x))])

    def mark(self, x0, z0, x1, z1):
        self.used[max(0, z0):min(self.B, z1 + 1), max(0, x0):min(self.B, x1 + 1)] = True

    def bez(self, p0, p1, p2, n=10):
        return [tuple((1 - t) ** 2 * a + 2 * (1 - t) * t * b + t * t * c for a, b, c in zip(p0, p1, p2)) for t in [i / n for i in range(n + 1)]]

    def tube(self, p0, p1, r0, r1, mat=None, follow=False, moss_top=False):
        L = math.dist(p0, p1); n = max(2, int(L * 2))
        for i in range(n + 1):
            f = i / n
            x = p0[0] + (p1[0] - p0[0]) * f; y = p0[1] + (p1[1] - p0[1]) * f; z = p0[2] + (p1[2] - p0[2]) * f
            r = r0 + (r1 - r0) * f
            if follow: y = max(y, self.at(int(round(x)), int(round(z))) - r * .6)
            ri = int(math.ceil(r))
            for dx in range(-ri, ri + 1):
                for dy in range(-ri, ri + 1):
                    for dz in range(-ri, ri + 1):
                        if dx * dx + dy * dy + dz * dz <= r * r + .3:
                            px, py, pz = int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz
                            if not (0 <= px < self.B and 0 <= pz < self.B) or py < self.at(px, pz) - 2: continue
                            if moss_top and dy == ri and noise3(px, py, pz, 31, 3) > .45: self.put(px, py, pz, "moss_block")
                            else: self.put(px, py, pz, *(mat or bark(px, py, pz)))

    def oak(self, x, z, kind="oak", big=False, form=None, lift=0):
        return tree(self, x, z, kind, big, form, lift)

    # ---- reservation ----
    def reserve(self, name, cx, cz, r, allow_route=False):
        B = self.B
        zz, xx = np.ogrid[0:B, 0:B]
        m = (xx - cx) ** 2 + (zz - cz) ** 2 <= r * r
        clash = []
        if (m & self.wet).any(): clash.append("水")
        if not allow_route and (m & self.route).any(): clash.append("道")
        if (m & self.used).any(): clash.append("他の大物")
        self.used |= m
        self.pieces.append(dict(name=name, at=(cx, cz), r=r, clash=clash))
        return clash


# ---------------------------------------------------------------- trees
def tree(c, x, z, kind="oak", big=False, form=None, lift=0):
    """Broadleaf or conifer with a form. Forms vary the silhouette; a canopy grows with the trunk so there are no bare poles."""
    B = c.B; R = c.R
    if not (3 <= x < B - 4 and 3 <= z < B - 4) or c.used[z, x] or c.route[z, x] or c.wet[z, x]: return False
    if c.z.slope[z, x] > 1.0: return False
    g = c.at(x, z)
    leaves, logn = {"oak": ("oak_leaves", "oak_log"), "birch": ("birch_leaves", "birch_log"), "dark": ("dark_oak_leaves", "dark_oak_log"),
                    "spruce": ("spruce_leaves", "spruce_log")}[kind]
    LOG = (logn + "_top", logn)
    if kind == "spruce":
        th = R.randint(8, 13)
        for y in range(g + 1, g + th + 1): c.put(x, y, z, *LOG)
        for y in range(g + 3, g + th + 3):
            r = max(0, int((g + th + 2 - y) / 2.4))
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + (y % 2) and (x + dx, y, z + dz) not in c.bl.b: c.put(x + dx, y, z + dz, leaves)
        c.used[z, x] = True
        return True
    form = form or R.choices(["round", "tall", "wide", "lean"], [4, 3, 3, 2])[0]
    cells = ((0, 0), (1, 0), (0, 1), (1, 1)) if big else ((0, 0),)
    th = {"round": R.randint(5, 7), "tall": R.randint(8, 11), "wide": R.randint(4, 6), "lean": R.randint(6, 8)}[form] + (4 if big else 0) + lift
    lx, lz = (R.choice((-1, 1)), R.choice((-1, 1))) if form == "lean" else (0, 0)
    tx, tz = x, z
    for i, y in enumerate(range(g + 1, g + th + 1)):
        ox = int(lx * i / th * 3); oz = int(lz * i / th * 3)
        for dx, dz in cells: c.put(x + ox + dx, y, z + oz + dz, *LOG)
        tx, tz = x + ox, z + oz
    ty = g + th
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):              # a couple of short limbs under the crown
        if R.random() < .5: c.put(tx + dx * 2, ty - 1, tz + dz * 2, logn.replace("_log", "_wood"))
        if R.random() < .5: c.put(tx + dx, ty - 1, tz + dz, logn.replace("_log", "_wood"))
    r = {"round": 2.8, "tall": 2.3, "wide": 4.2, "lean": 3.0}[form] + (1.6 if big else 0) + R.random() * .7 + lift * .4
    sq = {"round": .75, "tall": 1.2, "wide": .45, "lean": .7}[form]
    lumps = [(0, 0, 0)] + [(R.uniform(-1.8, 1.8), R.uniform(-1.5, 1) * (2 if form == "tall" else 1), R.uniform(-1.8, 1.8)) for _ in range(2 if not big else 3)]
    for (ox, oy, oz) in lumps:
        ri = int(r) + 2
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                for dy in range(-4, 5):
                    q = ((dx - ox) / r) ** 2 + ((dz - oz) / r) ** 2 + ((dy - oy) / (r * sq)) ** 2
                    if q <= .8 + noise3(tx + dx, g + dy, tz + dz, 41, 1.8) * .4 and (tx + dx, ty + dy, tz + dz) not in c.bl.b:
                        c.put(tx + dx, ty + dy, tz + dz, leaves if R.random() > .1 else ("azalea_leaves" if kind == "oak" else leaves))
    c.used[max(0, z - 1):z + 2, max(0, x - 1):x + 2] = True
    return True


def forests(c, spec):
    """Woods are drawn from the spec's forest regions; inside a wood trees stand on a jittered grid whose spacing breathes with
    noise (clearings where it opens up), and the edge has saplings and bushes before the meadow."""
    Z = c.z; B = c.B; R = c.R
    dens = np.zeros((B, B), np.float32)
    zz, xx = np.mgrid[0:B, 0:B].astype(np.float32)
    for f in spec.get("forests", []):
        cx, cz, rx, rz = f["at"][0], f["at"][1], f["r"][0], f["r"][1]
        d = np.sqrt(((xx - cx) / rx) ** 2 + ((zz - cz) / rz) ** 2) + (Z.fbm(3, 10, .5, f.get("seed", 5)) - .5) * .5
        dens = np.maximum(dens, np.clip((1 - d) * 3, 0, 1))
    Z.forest = dens
    route_d = Z.dist_to(c.route, 6)
    wet_d = Z.dist_to(c.wet, 12)
    clear = Z.fbm(2, 10, .5, 611)
    n = 0
    for f in spec.get("forests", []):
        kind = f.get("kind", "oak")
        sp = f.get("spacing", 6)
        cx, cz, rx, rz = f["at"][0], f["at"][1], f["r"][0], f["r"][1]
        for gz in range(int(cz - rz), int(cz + rz), sp):
            for gx in range(int(cx - rx), int(cx + rx), sp):
                x, z = gx + R.randint(-sp // 2, sp // 2), gz + R.randint(-sp // 2, sp // 2)
                if not (4 <= x < B - 5 and 4 <= z < B - 5): continue
                if dens[z, x] < .35 or clear[z, x] > .7 or route_d[z, x] < 4 or wet_d[z, x] < 5: continue
                k = kind if R.random() > f.get("mix", .2) else R.choice(["oak", "birch", "dark"])
                if tree(c, x, z, k, big=R.random() < f.get("big", .12), lift=R.randint(2, 5) if dens[z, x] > .8 else 0): n += 1
        # the edge: saplings and bushes in front of the trees
        for _ in range(int(rx * rz * .02)):
            a = R.uniform(0, math.tau); rr = 1.0 + R.uniform(-.05, .15)
            x, z = int(cx + math.cos(a) * rx * rr), int(cz + math.sin(a) * rz * rr)
            if not (3 <= x < B - 3 and 3 <= z < B - 3) or c.used[z, x] or route_d[z, x] < 3 or c.wet[z, x]: continue
            g = c.at(x, z)
            for k in range(R.randint(2, 5)):
                a2, b2 = x + R.randint(-1, 1), z + R.randint(-1, 1)
                if (a2, c.at(a2, b2) + 1, b2) not in c.bl.b and not c.route[b2, a2]:
                    c.put(a2, c.at(a2, b2) + 1, b2, R.choice(["bush", "azalea_leaves", "sweet_berry_bush_stage3", "fern"]), shape="plant")
    # groves on the meadows: one big tree with smaller ones and saplings round it
    for (x, z) in spec.get("groves", []):
        tree(c, x, z, "oak", big=True, form="wide")
        for k in range(R.randint(4, 7)):
            a = R.uniform(0, math.tau); r = R.uniform(6, 12)
            tree(c, int(x + math.cos(a) * r), int(z + math.sin(a) * r), R.choice(["oak", "oak", "birch"]), form=R.choice(["round", "tall", "lean"]))
    return n


def ground_cover(c):
    """Grass in clumps, one flower species per drift, ferns under trees, and grass at the foot of every one-block rise."""
    Z = c.z; B = c.B; hi = c.hi
    FL = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium"]
    route_d = Z.dist_to(c.route, 4)
    for z in range(2, B - 2):
        for x in range(2, B - 2):
            if route_d[z, x] < 1.5 or c.wet[z, x]: continue
            y = int(hi[z, x]) + 1
            if (x, y, z) in c.bl.b: continue
            t = c.bl.b.get((x, y - 1, z), ("", "", ""))[1]
            rise = any(hi[z + dz, x + dx] == y for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if t == "grass_block_top":
                clump = noise3(x, 0, z, 401, 5.0); drift = noise3(x, 0, z, 402, 14.0)
                if rise and _h(x, 77, z, 991) < .8:
                    c.put(x, y, z, "short_grass" if _h(x, 1, z, 9) < .6 else "fern", shape="plant")
                elif drift > .78 and _h(x, 2, z, 41) < (drift - .76) * 2.2 and Z.forest[z, x] < .3:
                    c.put(x, y, z, FL[int(noise3(x, 0, z, 403, 30) * 12) % 6], shape="plant")
                elif clump > .52 and _h(x, 3, z, 42) < (clump - .45) * 2.2:
                    c.put(x, y, z, "tall_grass_top" if clump > .78 and _h(x, 4, z, 43) < .4 else "short_grass", shape="plant")
            elif t in ("podzol_top", "moss_block"):
                if _h(x, 6, z, 45) < .3 * max(0, noise3(x, 6, z, 452, 4) - .45) * 4:
                    c.put(x, y, z, "fern", shape="plant")
            if Z.forest[z, x] > .6 and t == "grass_block_top" and _h(x, 9, z, 47) < .15 and (x, y, z) not in c.bl.b:
                c.put(x, y, z, "leaf_litter", shape="carpet")


# ---------------------------------------------------------------- gathering nodes
def gather_nodes(c, spec):
    """Fill the empty ground with things to gather, chosen by what the place is (expert rule: objects follow context,
    cluster in small groups, keep negative space between groups):
      near a rock face or on steep ground -> an ore outcrop (mossy stone mound with a few ore blocks showing)
      in or at the edge of a wood          -> a mushroom ring on moss, or a fallen branch to chop
      on open meadow                       -> an herb patch: one kind of plant, dense in the middle
      by water                             -> a reed bed and a clay bank
    Groups of 2-3 nodes of one kind, groups at least 26 blocks apart, never on or beside a route, never in a reserved spot."""
    Z = c.z; B = c.B; R = c.R
    route_d = Z.dist_to(c.route, 8)
    wet_d = Z.dist_to(c.wet, 14)
    face = Z.slope > 1.2
    face_d = Z.dist_to(face, 12)
    used_d = Z.dist_to(c.used, 10)
    groups = []
    kinds_n = {}
    for _ in range(20000):
        x, z = R.randrange(12, B - 12), R.randrange(12, B - 12)
        if c.wet[z, x] or route_d[z, x] < 5 or used_d[z, x] < 4 or Z.slope[z, x] > 1.0: continue
        if any((x - a) ** 2 + (z - b) ** 2 < 26 * 26 for a, b, _ in groups): continue
        if wet_d[z, x] < 6: kind = "reeds"
        elif face_d[z, x] < 7: kind = "ore"
        elif Z.forest[z, x] > .35: kind = R.choice(["mushroom", "mushroom", "branch"])
        else: kind = R.choice(["herb", "herb", "berry"])
        groups.append((x, z, kind))
    for (gx, gz, kind) in groups:
        n = R.randint(2, 3) if kind != "reeds" else 1
        for k in range(n):
            a = R.uniform(0, math.tau); r = 0 if k == 0 else R.uniform(5, 9)
            x, z = int(gx + math.cos(a) * r), int(gz + math.sin(a) * r)
            if not (4 <= x < B - 4 and 4 <= z < B - 4) or c.wet[z, x] or route_d[z, x] < 4 or c.used[z, x]: continue
            {"ore": node_ore, "mushroom": node_mushroom, "branch": node_branch, "herb": node_herb,
             "berry": node_berry, "reeds": node_reeds}[kind](c, x, z, R)
            c.used[max(0, z - 2):z + 3, max(0, x - 2):x + 3] = True
            kinds_n[kind] = kinds_n.get(kind, 0) + 1
    c.gather = groups
    return kinds_n


def node_ore(c, x, z, R):
    g = c.at(x, z); s = R.uniform(1.6, 2.4); ore = R.choice(["iron_ore", "coal_ore", "copper_ore", "coal_ore"])
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(0, 3):
                q = (dx / s) ** 2 + (dz / (s * .85)) ** 2 + (dy / (s * .7)) ** 2
                if q > 1 + noise3(x + dx, dy, z + dz, 811, 1.4) * .3: continue
                top = dy >= int(s * .7) - (1 if q > .6 else 0)
                m = "moss_block" if top and _h(x + dx, dy, z + dz, 812) < .5 else (ore if _h(x + dx, dy, z + dz, 813) < .3 else
                    R.choice(["stone", "andesite", "stone", "tuff"]))
                c.put(x + dx, c.at(x + dx, z + dz) + dy, z + dz, m)
    for k in range(3):
        a = R.uniform(0, math.tau); xx, zz = int(x + math.cos(a) * 3.5), int(z + math.sin(a) * 3.5)
        if (xx, c.at(xx, zz) + 1, zz) not in c.bl.b: c.put(xx, c.at(xx, zz) + 1, zz, R.choice(["cobblestone", "stone"]), shape="slab")


def node_mushroom(c, x, z, R):
    kind = R.choice(["red_mushroom", "brown_mushroom"])
    for k in range(R.randint(6, 10)):
        a = k / 8 * math.tau + R.uniform(-.3, .3); r = R.uniform(1.5, 2.8)
        xx, zz = int(round(x + math.cos(a) * r)), int(round(z + math.sin(a) * r))
        g = c.at(xx, zz)
        if (xx, g + 1, zz) in c.bl.b: continue
        c.put(xx, g, zz, "moss_block")
        c.put(xx, g + 1, zz, kind if R.random() < .75 else "fern", shape="plant")
    c.put(x, c.at(x, z), z, "mycelium_top", "mycelium_side")


def node_branch(c, x, z, R):
    a = R.uniform(0, math.tau); L = R.randint(4, 6); log = R.choice(["oak_log", "birch_log", "dark_oak_log"])
    for k in range(L):
        xx, zz = int(round(x + math.cos(a) * k)), int(round(z + math.sin(a) * k))
        g = c.at(xx, zz)
        if (xx, g + 1, zz) in c.bl.b and c.bl.b[(xx, g + 1, zz)][0] == "full": break
        c.put(xx, g + 1, zz, log, log)
        if _h(xx, 0, zz, 821) < .35: c.put(xx, g + 2, zz, "moss_block", shape="carpet")
    c.put(x, c.at(x, z) + 1 + 1, z, "brown_mushroom", shape="plant") if (x, c.at(x, z) + 2, z) not in c.bl.b else None


def node_herb(c, x, z, R):
    plant = R.choice(["allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "firefly_bush"])
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            d = math.hypot(dx, dz)
            if d > 3.2 or _h(x + dx, 0, z + dz, 831) > 1.05 - d * .25: continue
            g = c.at(x + dx, z + dz)
            if (x + dx, g + 1, z + dz) in c.bl.b and c.bl.b[(x + dx, g + 1, z + dz)][0] != "plant": continue
            if c.bl.b.get((x + dx, g, z + dz), ("", "", ""))[1] != "grass_block_top": continue
            c.put(x + dx, g + 1, z + dz, plant, shape="plant")


def node_berry(c, x, z, R):
    for k in range(R.randint(4, 7)):
        xx, zz = x + R.randint(-2, 2), z + R.randint(-2, 2)
        g = c.at(xx, zz)
        if (xx, g + 1, zz) in c.bl.b and c.bl.b[(xx, g + 1, zz)][0] != "plant": continue
        c.put(xx, g + 1, zz, "sweet_berry_bush_stage3" if R.random() < .7 else "bush", shape="plant")


def node_reeds(c, x, z, R):
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            xx, zz = x + dx, z + dz
            if not (0 <= xx < c.B and 0 <= zz < c.B) or c.wet[zz, xx] or c.route[zz, xx]: continue
            near = any(c.wet[zz + a, xx + b] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= zz + a < c.B and 0 <= xx + b < c.B)
            if not near or _h(xx, 0, zz, 841) > .7: continue
            g = c.at(xx, zz)
            c.put(xx, g, zz, "clay" if _h(xx, 1, zz, 842) < .3 else c.bl.b.get((xx, g, zz), ("full", "grass_block_top", ""))[1],
                  "grass_block_side" if c.bl.b.get((xx, g, zz), ("", "", ""))[1] == "grass_block_top" else None)
            for k in range(1 + int(_h(xx, 2, zz, 843) * 3)): c.put(xx, g + 1 + k, zz, "sugar_cane", shape="plant")
