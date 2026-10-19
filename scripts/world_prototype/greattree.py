"""The great tree, built properly: irregular buttressed trunk with mixed bark, a hollow at the base,
snaking roots, a recursive branch structure, layered mixed-leaf crowns and a dressed ground."""
import math
import random


def _h(ix, iy, iz, seed):
    n = ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 974634543
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xffff) / 0xffff


def noise3(x, y, z, seed=0, scale=1.0):
    x, y, z = x / scale, y / scale, z / scale
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    v = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                v += wgt * _h(ix + dx, iy + dy, iz + dz, seed)
    return v


BARK = [("dark_oak_log", "dark_oak_log")] * 5 + [("mangrove_log", "mangrove_log")] * 2 + [("stripped_dark_oak_log", "stripped_dark_oak_log")]


def bark(x, y, z):
    """Wood blocks (bark on all six faces) so roots and limbs never show end grain."""
    n = noise3(x, y * .5, z, 3, 3.0)
    if n > .78: return ("mangrove_wood", "mangrove_wood")
    if n < .18: return ("spruce_wood", "spruce_wood")
    return ("dark_oak_wood", "dark_oak_wood")


def build(w, put, at, B, road, cx, cz, mark, seed=77):
    R = random.Random(seed)
    g0 = at(cx, cz)
    H = 30
    taken = set()

    def set_wood(x, y, z, moss_ok=False):
        p = (x, y, z)
        if not (0 <= x < B and 0 <= z < B): return
        mat = bark(x, y, z)
        if moss_ok and y < g0 + 10 and noise3(x, y, z, 9, 2.5) > .55 and z < cz:
            mat = ("moss_block", "moss_block")
        put(x, y, z, *mat)
        taken.add(p)

    # ---- trunk: lobed, buttressed cross-section that narrows and leans
    def centre(y):
        t = (y - g0) / H
        return cx + math.sin(t * 2.2) * 2.0, cz - math.sin(t * 1.6) * 1.2

    for y in range(g0 - 2, g0 + H + 1):
        t = max(0, (y - g0)) / H
        base_r = 4.2 * (1 - t) ** .7 + 2.0
        flare = max(0, 6 - (y - g0)) * .55
        ccx, ccz = centre(y)
        rr = int(base_r + flare + 3)
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                th = math.atan2(dz, dx)
                lobes = 1 + .18 * math.cos(4 * th + .6) + .1 * math.cos(7 * th + 2)
                butt = flare * max(0, math.cos(5 * th + 1.1)) ** 3 * 1.6
                r = base_r * lobes * (1 + .15 * (noise3(th * 3, y * .3, 0, 5) - .5)) + flare * .4 + butt
                d = math.hypot(dx - (ccx - cx), dz - (ccz - cz))
                if d <= r and d > r - 2.2:
                    x, z = cx + dx, cz + dz
                    if y > at(x, z) - 1:
                        set_wood(x, y, z, moss_ok=True)
    # hollow at the base, opening to the south-east, lit from inside
    hx, hz = cx + 1, cz + 1
    for y in range(g0 + 1, g0 + 7):
        rh = 2.6 if y < g0 + 5 else 2.6 - (y - g0 - 4) * .9
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if math.hypot(dx, dz) <= rh: w.b.pop((hx + dx, y, hz + dz), None)
    for y in range(g0 + 1, g0 + 6):                                # the doorway, arched
        wdt = 1 if y < g0 + 5 else 0
        for k in range(-wdt, wdt + 1):
            for s in range(2, 9):
                w.b.pop((hx + s, y, hz + s + k), None); w.b.pop((hx + s + k, y, hz + s), None)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if math.hypot(dx, dz) <= 2.4: put(hx + dx, g0, hz + dz, "moss_block")
    put(hx - 1, g0 + 1, hz - 1, "stone_bricks", shape="wall"); put(hx - 1, g0 + 2, hz - 1, "shroomlight", shape="lantern")
    put(hx + 1, g0 + 1, hz - 1, "red_mushroom", shape="plant"); put(hx - 1, g0 + 1, hz + 1, "brown_mushroom", shape="plant")
    for y in range(g0 + 3, g0 + 6):
        put(hx + 1, y, hz - 2, "glow_lichen", shape="shut_s")

    # ---- roots: tapered tubes that leave the trunk, dip into the ground and fork
    def tube(p0, p1, r0, r1, steps=None, ground_follow=False, root=False):
        L = math.dist(p0, p1)
        n = steps or max(2, int(L * 2))
        for i in range(n + 1):
            f = i / n
            x = p0[0] + (p1[0] - p0[0]) * f; y = p0[1] + (p1[1] - p0[1]) * f; z = p0[2] + (p1[2] - p0[2]) * f
            r = r0 + (r1 - r0) * f
            if ground_follow:
                gx, gz = int(round(x)), int(round(z))
                if 0 <= gx < B and 0 <= gz < B: y = min(y, at(gx, gz) + r * .9)
            ri = int(math.ceil(r))
            for dx in range(-ri, ri + 1):
                for dy in range(-ri, ri + 1):
                    for dz in range(-ri, ri + 1):
                        if dx * dx + dy * dy + dz * dz <= r * r + .3:
                            px, py, pz = int(round(x)) + dx, int(round(y)) + dy, int(round(z)) + dz
                            if 0 <= px < B and 0 <= pz < B and py > at(px, pz) - 2:
                                if root and r < .9 and R.random() < .5:
                                    put(px, py, pz, "mangrove_roots_top", "mangrove_roots_side")
                                else:
                                    set_wood(px, py, pz)

    roots_end = []
    # buttress roots as tall thin plates (not tubes): unequal lengths and spacing, a convex top that runs down into the soil,
    # each fading into the ground through mangrove roots and rooted dirt; the big ones surface once more as a low hump
    a = R.uniform(0, math.tau)
    for k in range(9):
        a += math.radians(R.uniform(30, 52))
        major = k % 3 == 0
        L = R.uniform(18, 26) if major else R.uniform(9, 15)
        h0 = R.uniform(6, 8) if major else R.uniform(3, 5)
        bend = R.uniform(-.25, .25)
        for i in range(int(L * 2)):
            l = i / 2; f = l / L
            aa = a + bend * f
            x0, z0 = cx + math.cos(aa) * (4 + l), cz + math.sin(aa) * (4 + l)
            hh = h0 * (1 - f) ** .7 if f < .85 else (math.sin((f - .85) / .15 * math.pi) * 1.4 if major else 0)
            th = 3.0 * (1 - f) + 1
            for tq in range(-2, 3):
                if abs(tq) > th / 2: continue
                x = int(round(x0 - math.sin(aa) * tq)); z = int(round(z0 + math.cos(aa) * tq))
                if not (0 <= x < B and 0 <= z < B) or road[z, x]: continue
                gg = at(x, z)
                ht = int(round(hh))
                for y in range(gg - 1, gg + ht + 1):
                    set_wood(x, y, z, moss_ok=(y == gg + ht))
                if f > .7 and R.random() < .35:
                    put(x, gg, z, *(("mangrove_roots_top", "mangrove_roots_side") if R.random() < .5 else ("rooted_dirt",)))
        roots_end.append((cx + math.cos(a) * (4 + L), g0 - 1, cz + math.sin(a) * (4 + L)))

    # ---- branches: recursive, tapering, each tip carries a leaf cluster
    tips = []

    def grow(p, direction, length, r, depth):
        yaw, pitch = direction
        end = (p[0] + math.cos(yaw) * math.cos(pitch) * length,
               p[1] + math.sin(pitch) * length,
               p[2] + math.sin(yaw) * math.cos(pitch) * length)
        # a slight kink halfway so limbs are not ruler-straight
        midp = ((p[0] + end[0]) / 2 + R.uniform(-1.2, 1.2), (p[1] + end[1]) / 2 + R.uniform(-.8, 1.2), (p[2] + end[2]) / 2 + R.uniform(-1.2, 1.2))
        r1 = max(.5, r * .62)
        tube(p, midp, r, (r + r1) / 2)
        tube(midp, end, (r + r1) / 2, r1)
        if depth == 0:
            tips.append(end)
            return
        for _ in range(R.choice((2, 2, 3))):
            grow(end, (yaw + R.uniform(-.75, .75), max(-.15, min(1.1, pitch + R.uniform(-.35, .25)))),
                 length * R.uniform(.55, .75), r1, depth - 1)

    for k in range(6):
        yaw = k / 6 * math.tau + R.uniform(-.25, .25)
        y = g0 + R.uniform(16, 24)
        ccx, ccz = centre(y)
        grow((ccx + math.cos(yaw) * 2, y, ccz + math.sin(yaw) * 2), (yaw, R.uniform(.25, .6)), R.uniform(11, 15), 2.0, 2)
    tcx, tcz = centre(g0 + H)
    for k in range(3):
        grow((tcx, g0 + H, tcz), (k / 3 * math.tau + R.uniform(-.3, .3), R.uniform(.7, 1.0)), R.uniform(7, 9), 1.8, 2)

    # ---- leaves: flattened noisy clusters, layered leaf kinds, darker underside, things hanging below
    LEAF_TOP = ["oak_leaves", "oak_leaves", "oak_leaves", "azalea_leaves"]
    LEAF_UNDER = ["dark_oak_leaves", "dark_oak_leaves", "oak_leaves"]
    leaf_cells = {}
    for (tx, ty, tz) in tips:
        # each tip: 3-4 overlapping lumps of different sizes, so the crown is clumpy rather than layered
        lumps = [(tx, ty, tz, R.uniform(3.2, 4.4))]
        for _ in range(R.randint(2, 3)):
            lumps.append((tx + R.uniform(-3, 3), ty + R.uniform(-1.5, 2), tz + R.uniform(-3, 3), R.uniform(2.2, 3.4)))
        for (lx, ly, lz, r) in lumps:
            ri = int(r) + 2
            for dx in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    for dy in range(-ri, ri + 1):
                        q = (dx / r) ** 2 + (dz / r) ** 2 + (dy / (r * .78)) ** 2
                        nz = noise3(lx + dx, ly + dy, lz + dz, 11, 1.6)
                        if q <= .7 + nz * .6:
                            p = (int(round(lx)) + dx, int(round(ly)) + dy, int(round(lz)) + dz)
                            if p not in taken: leaf_cells[p] = (p[1] - ly) / r
    for p, dy in leaf_cells.items():
        x, y, z = p
        if (x, y, z) in w.b: continue
        if dy > .15:
            kind = "flowering_azalea_leaves" if _h(x, y, z, 4) < .05 else LEAF_TOP[min(len(LEAF_TOP) - 1, int(_h(x, y, z, 5) * len(LEAF_TOP)))]
        else:
            kind = LEAF_UNDER[min(len(LEAF_UNDER) - 1, int(_h(x, y, z, 6) * len(LEAF_UNDER)))]
        put(x, y, z, kind)
    under = [p for p in leaf_cells if (p[0], p[1] - 1, p[2]) not in leaf_cells and (p[0], p[1] - 1, p[2]) not in w.b]
    R.shuffle(under)
    for (x, y, z) in under[:len(under) // 6]:
        n = R.randint(2, 9)
        kind = "cave_vines_lit" if R.random() < .15 else "vine"
        for k in range(1, n):
            if (x, y - k, z) in w.b: break
            put(x, y - k, z, kind if kind == "vine" else ("cave_vines_plant_lit" if k < n - 1 else "cave_vines_lit"), shape="shut_s" if kind == "vine" else "chain")
    for (x, y, z) in under[len(under) // 6: len(under) // 6 + 6]:
        put(x, y - 1, z, "spore_blossom", shape="carpet")

    # ---- ground: root-bound earth, moss carpets, ferns, mushrooms, a few stones gripped by roots
    for dx in range(-26, 27):
        for dz in range(-26, 27):
            d = math.hypot(dx, dz)
            x, z = cx + dx, cz + dz
            if d > 26 or not (0 <= x < B and 0 <= z < B) or road[z, x]: continue
            g = at(x, z)
            top = w.b.get((x, g, z))
            if not top or top[1] not in ("grass_block_top", "coarse_dirt", "podzol_top", "moss_block", "dirt_path_top"): continue
            f = 1 - d / 26
            n = noise3(x, 0, z, 21, 4)
            if n * f > .3: put(x, g, z, "moss_block")
            elif n * f > .18: put(x, g, z, "rooted_dirt")
            elif n * f > .1: put(x, g, z, "podzol_top", "podzol_side")
            if (x, g + 1, z) in w.b: continue
            r = R.random()
            if r < .22 * f: put(x, g + 1, z, "moss_block", shape="carpet")
            elif r < .32 * f: put(x, g + 1, z, "fern", shape="plant")
            elif r < .35 * f: put(x, g + 1, z, R.choice(["red_mushroom", "brown_mushroom"]), shape="plant")
            elif r < .38 * f: put(x, g + 1, z, "dark_oak_leaves", shape="carpet")
    for (ex, ey, ez) in roots_end[:5]:
        x, z = int(round(ex)), int(round(ez))
        if 0 <= x < B and 0 <= z < B:
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    pass
    mark(cx - 34, cz - 34, cx + 34, cz + 34)
