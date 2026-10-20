"""Constraints-first zone generator.

Order of work (each stage only reads earlier stages, and never writes a cell an earlier stage locked):
  1 macro height   levels and scarps from the spec, low-frequency only
  2 water          lakes get one level and a closed rim; streams get a bed that only goes down, drops only at falls
  3 places         flat pads at the spec's places
  4 roads          routed by A* around water and steep ground, graded to a max slope, cut and filled to that grade
  5 solve          the free ground is relaxed toward the locked cells so nothing next to a lock is a wall
  6 detail         fine relief and erosion only far from locks
  7 blocks         every column is filled solid down below its lowest neighbour (no holes), materials by slope
The spec (a plain dict) says what goes where; this module decides how.
"""
import heapq
import math
import random

import numpy as np
from PIL import Image


class Zone:
    def __init__(self, spec):
        self.spec = spec
        self.B = spec["size"]
        B = self.B
        self.zz, self.xx = np.mgrid[0:B, 0:B].astype(np.float32)
        self.H = np.zeros((B, B), np.float32)          # float height
        self.lock = np.zeros((B, B), bool)              # cells whose height is final
        self.kind = np.zeros((B, B), np.uint8)          # 0 ground, 1 road, 2 path, 3 lake, 4 stream, 5 pad, 6 shoulder
        self.water = np.full((B, B), -1, np.int32)      # water surface y per cell, -1 none
        self.route_y = {}
        self.routes = []                                # (kind, polyline, profile, width)
        self.falls = []
        self.R = random.Random(spec.get("seed", 1))

    # ---------------- noise ----------------
    def fbm(self, octaves, base, gain, seed):
        B = self.B
        r = np.random.default_rng(seed)
        out = np.zeros((B, B), np.float32); amp = 1.0; tot = 0.0
        for o in range(octaves):
            n = base * 2 ** o
            g = r.random((n + 1, n + 1)).astype(np.float32)
            out += amp * np.asarray(Image.fromarray(g, mode="F").resize((B, B), Image.BICUBIC)); tot += amp; amp *= gain
        return out / tot

    def bump(self, cx, cz, rx, rz, h):
        d = np.sqrt(((self.xx - cx) / rx) ** 2 + ((self.zz - cz) / rz) ** 2)
        t = np.clip(1 - d, 0, 1)
        return h * t * t * (3 - 2 * t)

    def set_locked(self, mask, values, kind):
        """Write heights only where not already locked; lock them."""
        m = mask & ~self.lock
        self.H[m] = values[m] if isinstance(values, np.ndarray) else values
        self.lock |= m
        self.kind[m] = kind
        return m

    # ---------------- 1 macro ----------------
    def macro(self):
        s = self.spec; B = self.B
        H = np.full((B, B), float(s["levels"][0]), np.float32)
        for sc in s["scarps"]:
            # a scarp is a line z = f(x) (or x = f(z)) with a rise; width varies: narrow = cliff, wide = slope (a ramp)
            xs = np.array([p[0] for p in sc["line"]], np.float32); zs = np.array([p[1] for p in sc["line"]], np.float32)
            line_z = np.interp(np.arange(B), xs, zs).astype(np.float32)
            jag = (self.fbm(3, 12, .5, sc["seed"]) - .5) * sc.get("jag", 10)
            wnoise = self.fbm(2, 6, .5, sc["seed"] + 1)
            width = sc.get("cliff_width", 3) + wnoise * 3
            for (a, b) in sc.get("ramps", []):               # designed slopes where the scarp is walkable
                ramp = np.clip(1 - np.abs(self.xx - (a + b) / 2) / ((b - a) / 2), 0, 1)
                width = width + ramp * 40
            t = np.clip((line_z[None, :] + jag - self.zz) / width + .5, 0, 1)
            H += sc["rise"] * (t * t * (3 - 2 * t))
        H += (self.fbm(3, 3, .5, s["seed"] + 10) - .5) * s.get("roll", 6)
        for b in s.get("bumps", []):
            H += self.bump(*b)
        self.H = H.astype(np.float32)
        self.macroH = self.H.copy()

    # ---------------- 2 water ----------------
    def lakes(self):
        for lk in self.spec.get("lakes", []):
            cx, cz, rx, rz = lk["at"][0], lk["at"][1], lk["r"][0], lk["r"][1]
            n = (self.fbm(3, 10, .5, lk.get("seed", 7)) - .5) * .35
            d = np.sqrt(((self.xx - cx) / rx) ** 2 + ((self.zz - cz) / rz) ** 2) + n
            inside = d < 1
            rim = (d >= 1) & (d < 1.6)
            WL = int(round(float(np.percentile(self.H[rim], 20)))) - 1     # one level for the whole lake
            depth = 1 + (lk.get("depth", 4) - 1) * np.sqrt(np.clip(1 - d, 0, 1) / 1.0)       # a bowl: 1 deep at the shore, deepest in the middle
            bed = WL - depth
            self.set_locked(inside, bed.astype(np.float32), 3)
            self.water[inside] = WL
            # the rim: never below WL+1 within the first ring, a gentle shore beyond
            shore = (d >= 1) & (d < 1.18)
            self.set_locked(shore, float(WL + 1), 6)
            lk["WL"] = WL

    def streams(self):
        for st in self.spec.get("streams", []):
            pts = polyline(st["line"], 8)
            prof = np.array([self.H[int(z), int(x)] for x, z in pts], np.float32)
            bed = np.minimum.accumulate(prof - 1.5)            # the bed only goes down
            # end at the lake level if it flows into one
            if st.get("into"):
                lk = [l for l in self.spec["lakes"] if l["name"] == st["into"]][0]
                bed = np.maximum(bed, lk["WL"] - 2)
            bed_i = np.round(bed).astype(int)
            half = st.get("half", 1.4)
            banks = []
            for i, (x, z) in enumerate(pts):
                yb = bed_i[i]
                for dx in range(-3, 4):
                    for dz in range(-3, 4):
                        X, Z = int(round(x + dx)), int(round(z + dz))
                        if not (0 <= X < self.B and 0 <= Z < self.B): continue
                        dd = math.hypot(x - X, z - Z)
                        if half < dd <= half + 2.2: banks.append((X, Z, yb + 1))
                        if dd <= half:
                            if self.kind[Z, X] == 3: continue
                            if not self.lock[Z, X] or self.kind[Z, X] == 4:
                                self.H[Z, X] = min(self.H[Z, X], yb) if self.kind[Z, X] == 4 else yb
                                self.lock[Z, X] = True; self.kind[Z, X] = 4
                                self.water[Z, X] = int(round(self.H[Z, X])) + 1
            for (X, Z, y) in banks:                              # banks: grass at the water's level, after every channel cell is set
                if not self.lock[Z, X]:
                    self.H[Z, X] = y; self.lock[Z, X] = True; self.kind[Z, X] = 6
                elif self.kind[Z, X] == 6: self.H[Z, X] = min(self.H[Z, X], y)
            for i in range(1, len(bed_i)):
                if bed_i[i - 1] - bed_i[i] >= 2: self.falls.append((pts[i][0], pts[i][1], bed_i[i - 1], bed_i[i]))

    # ---------------- 3 places ----------------
    def places(self):
        for p in self.spec["places"]:
            if not p.get("pad"): continue
            cx, cz, r = p["at"][0], p["at"][1], p["pad"]
            y = p.get("y") or int(round(float(np.median(self.H[max(0, cz - r):cz + r, max(0, cx - r):cx + r]))))
            p["y"] = y
            n = (self.fbm(2, 14, .5, 900 + cx) - .5) * .3
            d = np.hypot(self.xx - cx, self.zz - cz) / r + n
            self.set_locked(d < 1, float(y), 5)

    def obstacles(self):
        """Pillar stems and other big footprints are reserved before roads are routed, so roads go round them."""
        self.obst = np.zeros((self.B, self.B), bool)
        for p in self.spec.get("pillars", []):
            self.obst |= np.hypot(self.xx - p[0], self.zz - p[1]) < p[3] + 5
        for (x, z, r) in self.spec.get("obstacles", []):
            self.obst |= np.hypot(self.xx - x, self.zz - z) < r

    # ---------------- 4 roads ----------------
    def route(self, a, b, kind):
        """A* on the macro height: cost grows with slope; water and locked non-route cells are forbidden."""
        B = self.B; H = self.H
        gy, gx = np.gradient(self.macroH)
        slope = np.hypot(gx, gy)
        blocked = (self.kind == 3) | (self.kind == 6) | getattr(self, "obst", np.zeros((B, B), bool))
        wet = self.kind == 4                                       # a stream may be crossed, at a bridge
        def snap(p):                                                # a place inside water snaps to the nearest dry cell
            x, z = p
            if not blocked[z, x]: return (z, x)
            best = min(((abs(dz) + abs(dx), (z + dz, x + dx)) for dz in range(-20, 21) for dx in range(-20, 21)
                        if 0 <= z + dz < B and 0 <= x + dx < B and not blocked[z + dz, x + dx]), default=(0, (z, x)))
            return best[1]
        start, goal = snap(a), snap(b)
        INF = 1e18
        dist = {start: 0.0}; prev = {}
        pq = [(0.0, 0.0, start)]
        nbs = [(1, 0, 1.0), (-1, 0, 1.0), (0, 1, 1.0), (0, -1, 1.0), (1, 1, 1.414), (1, -1, 1.414), (-1, 1, 1.414), (-1, -1, 1.414)]
        while pq:
            _, d0, (z, x) = heapq.heappop(pq)
            if (z, x) == goal: break
            if d0 > dist.get((z, x), INF): continue
            for dx, dz, L in nbs:
                X, Z = x + dx, z + dz
                if not (0 <= X < B and 0 <= Z < B) or blocked[Z, X]: continue
                grade = abs(float(self.macroH[Z, X] - self.macroH[z, x])) / L
                c = L * (1 + 60 * grade * grade + (40 if grade > .35 else 0) + 3 * slope[Z, X])
                if self.kind[Z, X] in (1, 2): c *= .5                 # reuse existing roads
                if wet[Z, X]: c += 25                                  # a bridge is allowed but not free
                nd = d0 + c
                if nd < dist.get((Z, X), INF):
                    dist[(Z, X)] = nd; prev[(Z, X)] = (z, x)
                    heapq.heappush(pq, (nd + math.hypot(goal[0] - Z, goal[1] - X) * 1.0, nd, (Z, X)))
        path = []; cur = goal
        while cur != start:
            path.append(cur); cur = prev.get(cur)
            if cur is None: raise RuntimeError(f"no route {a}->{b}")
        path.append(start); path.reverse()
        # smooth the raster path into a gentle curve
        pts = np.array([(x, z) for z, x in path], np.float32)
        k = 9
        if len(pts) > k:
            pad = np.pad(pts, ((k // 2, k // 2), (0, 0)), mode="edge")
            pts = np.stack([np.convolve(pad[:, i], np.ones(k) / k, "valid") for i in range(2)], 1)
        return pts

    def roads(self):
        for e in self.spec["roads"]:
            places = {p["name"]: p for p in self.spec["places"]}
            seq = [places[n]["at"] if isinstance(n, str) else n for n in e["via"]]
            for a, b in zip(seq, seq[1:]):                     # every leg is its own route with its own profile
                self.lay_route(self.route(a, b, e["kind"]), e)
        # junctions: a path meeting a road (or its verge) at another height eases to it, one block per block
        rt = (self.kind == 1) | (self.kind == 7)
        pth = self.kind == 2
        H = self.H
        for _ in range(30):
            p = np.pad(np.where(rt | pth, H, np.nan), 1, constant_values=np.nan)
            nb = np.stack([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1]])
            with np.errstate(all="ignore"):
                mn = np.nanmin(nb, 0); mx = np.nanmax(nb, 0)
            H = np.where(pth, np.clip(H, np.nan_to_num(mx - .9, nan=-1e9), np.nan_to_num(mn + .9, nan=1e9)), H).astype(np.float32)
        self.H = H

    def lay_route(self, pts, e):
        if True:
            seglen = np.r_[0, np.hypot(*np.diff(pts, axis=0).T)]
            prof = np.array([self.H[int(round(z)), int(round(x))] for x, z in pts], np.float32)
            prof = np.convolve(np.pad(prof, 8, mode="edge"), np.ones(17) / 17, "valid")
            maxg = e.get("max_grade", .12)
            fixed = np.full(len(prof), np.nan, np.float32)
            for i in range(len(prof)):                         # honour locked cells on the way (pads, earlier roads)
                X, Z = int(round(pts[i][0])), int(round(pts[i][1]))
                if self.kind[Z, X] in (1, 2, 5, 7): fixed[i] = self.H[Z, X]
            fx = ~np.isnan(fixed)
            for _ in range(60):
                prof[fx] = fixed[fx]
                for i in range(1, len(prof)): prof[i] = np.clip(prof[i], prof[i - 1] - maxg * seglen[i], prof[i - 1] + maxg * seglen[i])
                for i in range(len(prof) - 2, -1, -1): prof[i] = np.clip(prof[i], prof[i + 1] - maxg * seglen[i + 1], prof[i + 1] + maxg * seglen[i + 1])
            prof[fx] = fixed[fx]
            w = e.get("width", 2.5)
            d, idx = dist_idx(pts, self.xx, self.zz)
            level = prof[np.clip(idx, 0, len(prof) - 1)]
            core = d < w
            k = 1 if e["kind"] == "road" else 2
            m = self.set_locked(core, level.astype(np.float32), k)
            self.set_locked((d >= w) & (d < w + 2.5), level.astype(np.float32), 7)       # shoulders: the verge is at road level
            self.road_d = np.minimum(getattr(self, "road_d", np.full((self.B, self.B), 999, np.float32)), d)
            self.routes.append((e["kind"], pts, prof, w))

    def meet_pads(self):
        """Where a road runs along a pad's edge at another height, the pad's edge slopes down (or up) to the road, one block per block."""
        pad = self.kind == 5; road = (self.kind == 1) | (self.kind == 2)
        H = self.H
        for _ in range(8):
            p = np.pad(np.where(road | pad, H, np.nan), 1, mode="constant", constant_values=np.nan)
            nb = np.stack([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1]])
            with np.errstate(all="ignore"):
                mn = np.nanmin(nb, 0); mx = np.nanmax(nb, 0)
            H = np.where(pad, np.clip(H, np.nan_to_num(mx - 1, nan=-1e9), np.nan_to_num(mn + 1, nan=1e9)), H).astype(np.float32)
        self.H = H

    # ---------------- 5 solve ----------------
    def solve(self, iters=400):
        self.meet_pads()
        """Relax the free cells: the difference between the locked heights and the macro surface spreads outward and fades,
        so ground meets every road, pad and shore without a wall."""
        D = np.where(self.lock, self.H - self.macroH, 0).astype(np.float32)
        L = self.lock
        for i in range(iters):
            p = np.pad(D, 1, mode="edge")
            avg = (p[1:-1, 2:] + p[1:-1, :-2] + p[2:, 1:-1] + p[:-2, 1:-1]) * .25
            D = np.where(L, D, avg * .995)
        self.H = np.where(L, self.H, self.macroH + D).astype(np.float32)
        self.limit_slope_near_locks()

    def limit_slope_near_locks(self, reach=6, max_step=.95):
        """Within `reach` of a road or pad, the free ground may not rise or fall more than one block per block."""
        near = self.dist_to(self.kind == 1) < reach
        near |= self.dist_to(self.kind == 2) < reach
        near |= self.dist_to(self.kind == 5) < reach
        near |= self.dist_to((self.kind == 6) | (self.kind == 7), 8) < reach
        near &= ~self.lock
        H = self.H
        for _ in range(reach * 2):
            p = np.pad(H, 1, mode="edge")
            mn = np.minimum.reduce([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1]])
            mx = np.maximum.reduce([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1]])
            H = np.where(near, np.clip(H, mx - max_step, mn + max_step), H).astype(np.float32)
        self.H = H

    def dist_to(self, mask, cap=40):
        d = np.where(mask, 0, cap).astype(np.float32)
        m = mask.copy()
        for k in range(1, cap):
            m = m | np.roll(m, 1, 0) | np.roll(m, -1, 0) | np.roll(m, 1, 1) | np.roll(m, -1, 1)
            d = np.where(m & (d > k), k, d)
        return d

    # ---------------- 6 detail ----------------
    def detail(self):
        free = ~self.lock
        dl = self.dist_to(self.lock, 12)
        wgt = np.clip((dl - 3) / 8, 0, 1) * free
        det = (self.fbm(3, 24, .5, self.spec["seed"] + 20) - .5) * 3.0 + (self.fbm(2, 60, .5, self.spec["seed"] + 21) - .5) * 1.2
        self.H = (self.H + det * wgt).astype(np.float32)
        self.limit_slope_near_locks()

    # ---------------- 7 integer surface ----------------
    def quantize(self):
        self.hi = np.round(self.H).astype(np.int32)
        gy, gx = np.gradient(self.H)
        self.slope = np.hypot(gx, gy)


def polyline(pts, n=10):
    out = []
    for a, b in zip(pts, pts[1:]):
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    out.append(pts[-1])
    out = np.array(out, np.float32)
    for _ in range(3):
        q = np.empty((len(out) * 2 - 2, 2), np.float32)
        q[0::2] = out[:-1] * .75 + out[1:] * .25
        q[1::2] = out[:-1] * .25 + out[1:] * .75
        out = np.vstack([out[:1], q, out[-1:]])
    return out


def dist_idx(pl, xx, zz):
    B = xx.shape[0]
    d = np.full((B, B), 999.0, np.float32); idx = np.zeros((B, B), np.int32)
    for i, (a, b) in enumerate(zip(pl, pl[1:])):
        x0, x1 = int(max(0, min(a[0], b[0]) - 12)), int(min(B, max(a[0], b[0]) + 13))
        z0, z1 = int(max(0, min(a[1], b[1]) - 12)), int(min(B, max(a[1], b[1]) + 13))
        X = xx[z0:z1, x0:x1]; Z = zz[z0:z1, x0:x1]
        ab = b - a; L2 = float(ab @ ab) + 1e-9
        t = np.clip(((X - a[0]) * ab[0] + (Z - a[1]) * ab[1]) / L2, 0, 1)
        dd = np.hypot(X - a[0] - t * ab[0], Z - a[1] - t * ab[1])
        sub = d[z0:z1, x0:x1]; si = idx[z0:z1, x0:x1]
        m = dd < sub; sub[m] = dd[m]; si[m] = i
    return d, idx
