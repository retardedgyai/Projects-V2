"""The field's working places, ported from the field500 prototype: the root hollow (grind spot), the fungal hollow with its
giant mushrooms, the wolves' den, the root-swallowed tower, the boulder field (quarrying), the rest point (waystone,
campfire, signpost) and the dungeon mouth under the great tree. Each is a function taking its centre; the builder
reserves the footprint first.
"""
import math
import random
import numpy as np
from greattree import noise3, _h, bark


CONTENT = []
LABELS_POI = []


def bind(ctx):
    g = globals()
    g.update(put=ctx.put, at=ctx.at, w=ctx.bl, B=ctx.B, bez=ctx.bez, tube=ctx.tube, mark=ctx.mark, R=ctx.R,
             road=ctx.route, road_d=ctx.z.road_d, lake=ctx.wet, stream=ctx.wet, used=ctx.used, hi=ctx.hi)


def spawn(*a, **k):                      # mob spawn markers are stand-ins with no block in the game; nothing to place
    pass


def level_pad(x0, z0, x1, z1, y):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if road[z, x]: continue                                    # roads keep their graded height
            g = at(x, z)
            top = w.b.get((x, g, z), ("full", "grass_block_top", "grass_block_side"))
            for yy in range(y + 1, g + 1): w.b.pop((x, yy, z), None)
            for yy in range(g + 1, y): put(x, yy, z, "dirt")
            for yy in range(y - 4, y):
                if (x, yy, z) not in w.b: put(x, yy, z, "dirt")
            put(x, y, z, top[1], top[2]) if top[0] == "full" else put(x, y, z, "grass_block_top", "grass_block_side")
            for yy in range(y + 1, y + 4):
                v = w.b.get((x, yy, z))
                if v and v[0] in ("plant", "carpet", "slab"): w.b.pop((x, yy, z), None)
            hi[z, x] = y


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



def root_hollow(BX, BZ):
    bg = at(BX, BZ)
    for x in range(BX - 36, BX + 37):                                 # arena floor: root-bound earth, rings of moss
        for z in range(BZ - 36, BZ + 37):
            d = math.hypot(x - BX, z - BZ)
            if d < 30 * (1 + .16 * math.sin(3 * math.atan2(z - BZ, x - BX) + 1) + .08 * math.sin(5 * math.atan2(z - BZ, x - BX))) and 0 <= x < B and 0 <= z < B:
                for y in range(at(x, z) + 1, at(x, z) + 6): w.b.pop((x, y, z), None)
                n = noise3(x, 0, z, 51, 10)
                put(x, at(x, z), z, *(("moss_block",) if n > .64 else ("grass_block_top", "grass_block_side")))
    mark(BX - 36, BZ - 36, BX + 36, BZ + 36)
    RAD = lambda a: 31 * (1 + .16 * math.sin(3 * a + 1) + .08 * math.sin(5 * a))
    # the wall: great roots of a tree long gone arch over the rim and plunge back in, each its own height and girth,
    # overlapping like ribs; feet flare into plates; one wide gap faces the road. Bezier control is set at twice the wanted
    # height so the root really stands that tall above the rim ground (not a low hump).
    a0 = 0.0
    while a0 < math.tau - .2:
        big = R.random() < .4
        span = R.uniform(.8, 1.1) if big else R.uniform(.35, .55)          # a few great ribs, the rest smaller, never a row of equal hoops
        a1 = a0 + span
        if abs(math.atan2(math.sin(a0 + span / 2 - 2.6), math.cos(a0 + span / 2 - 2.6))) < .5: a0 = a1 - .05; continue
        ends = []
        for aa in (a0, a1):
            ex, ez = BX + math.cos(aa) * RAD(aa), BZ + math.sin(aa) * RAD(aa)
            ends.append((ex, at(int(round(ex)), int(round(ez))) - 1, ez))
        am = (a0 + a1) / 2
        rim = max(at(int(round(BX + math.cos(am) * RAD(am))), int(round(BZ + math.sin(am) * RAD(am)))), ends[0][1], ends[1][1])
        hgt = R.uniform(15, 22) if big else R.uniform(5, 9)
        lean = R.uniform(-5, 1)                                        # the crown of each rib leans in over the hollow
        ctrl = (BX + math.cos(am) * (RAD(am) + lean), rim + hgt * 2, BZ + math.sin(am) * (RAD(am) + lean))
        r0 = R.uniform(2.6, 3.4) if big else R.uniform(1.4, 2.0)
        pts = bez(ends[0], ctrl, ends[1], 24)
        for i, (p, q) in enumerate(zip(pts, pts[1:])):
            f = i / (len(pts) - 1)
            rr = r0 * (1 - .45 * math.sin(math.pi * f))                # thick at the feet, thinner over the top
            tube(p, q, rr, rr, moss_top=True)
        for (ex, ey, ez) in ends:                                      # the feet flare into the ground
            for k in range(3):
                aa = R.uniform(0, math.tau)
                tube((ex, ey + 2, ez), (ex + math.cos(aa) * 4, at(int(ex + math.cos(aa) * 4), int(ez + math.sin(aa) * 4)) - 1, ez + math.sin(aa) * 4), 1.4, .6)
        for (px_, py_, pz_) in pts[5:-5:2]:                            # moss and vines hang from the underside
            if R.random() < .5:
                for j in range(1, R.randint(3, 8)):
                    p = (int(round(px_)), int(round(py_)) - int(r0) - j, int(round(pz_)))
                    if p in w.b or p[1] <= at(p[0], p[2]): break
                    put(*p, "vine" if j < 4 else "hanging_roots", shape="shut_s" if j < 4 else "plant")
        a0 = a1 - R.uniform(.12, .2) + (R.uniform(.05, .3) if R.random() < .4 else 0)   # sometimes a gap between ribs
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

def fungal(FX, FZ):
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
    

def wolf_den(WX, WZ):
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
    

def tower(TX, TZ):
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
    

def boulders(QX, QZ):
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
    

def rest_point(RX, RZ):
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

def knoll(cx, cz, r, h):
    """Raise a rounded knoll (grass over dirt) so the dungeon passage has earth over it and the tree stands up on it."""
    g0 = at(cx, cz)
    for dz in range(-r, r + 1):
        for dx in range(-r, r + 1):
            x, z = cx + dx, cz + dz
            d = math.hypot(dx, dz * 1.15) / r + (noise3(x, 0, z, 88, 6) - .5) * .25
            if d >= 1 or road[z, x]: continue
            g = at(x, z)
            t = min(1, (1 - d) / .55) * min(1, max(0, (road_d[z, x] - 2) / 6))   # sink to road level near a road
            top = int(round(g + (g0 + h - g) * (t * t * (3 - 2 * t))))
            top = min(top, g + max(0, int(road_d[z, x] - 2.5)))           # rises at most one block per block away from a road
            if top <= g: continue
            w.b.pop((x, g + 1, z), None)
            for y in range(g, top): put(x, y, z, "dirt")
            put(x, top, z, "grass_block_top", "grass_block_side")
            hi[z, x] = top


def dungeon(TREE, TG):
    M0 = (TREE[0] + 1, TREE[1] + 17)                                   # the mouth sits in the knoll's face, just off the road
    yd = min(at(*M0), at(M0[0], M0[1] + 5))                           # its floor is at the road's level
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
    for x in range(M0[0] - 6, M0[0] + 7):
        for z in range(M0[1] - 19, M0[1] + 1):
            if (x, at(x, z), z) not in w.b:
                for y in range(yd - 4, yd): 
                    if (x, y, z) not in w.b: put(x, y, z, "dirt")
                hi[z, x] = yd
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
            r = 1.2 if i < 10 else 1.0 - (i - 10) * .04
            tube(p0, p1, r, r, moss_top=True)
    for x in range(M0[0] - 3, M0[0] + 4):                                 # keep the opening clear of the frame and of the tree's roots
        for y in range(yd + 1, yd + 6 - (1 if abs(x - M0[0]) == 3 else 0)):
            for z in range(M0[1] - 2, M0[1] + 8): w.b.pop((x, y, z), None)
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
        if road[sz - 2:sz + 3, sx - 2:sx + 3].any(): sz -= 4                 # stand beside the mouth, not on the road
        if road[sz - 2:sz + 3, sx - 2:sx + 3].any(): continue
        g = at(sx, sz)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1): put(sx + dx, g + 1, sz + dz, "stone_bricks" if (dx or dz) else "chiseled_stone_bricks", shape="slab" if (dx and dz) else "full")
        put(sx, g + 2, sz, "mossy_stone_bricks"); put(sx, g + 3, sz, "mossy_stone_bricks"); put(sx, g + 4, sz, "chiseled_stone_bricks")
        put(sx, g + 5, sz, "mossy_stone_bricks", shape="slab"); put(sx, g + 6, sz, "moss_block", shape="carpet")
    mark(M0[0] - 9, M0[1] - 20, M0[0] + 9, M0[1] + 4)
