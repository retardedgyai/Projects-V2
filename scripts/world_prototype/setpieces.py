"""Large set pieces, built to the recipes in research-builds.md (fewer, bigger, each with one hero element).

Every piece is one readable silhouette from 100+ blocks, with detail that has a cause up close:
  fallen_trunk  - an uprooted giant lying along the ground; hero = the upturned root plate and its pit
  aqueduct      - a ruined arcade; hero = the collapsed bay with a great root dropping through it
  stump         - a giant felled long ago; hero = the ringed cut face with the felling step and hinge splinters
Texture is chosen per 3-6 block patch by low-frequency noise and by cause (shade, wet, top), never per block.
"""
import math, random
from greattree import noise3, _h

G = None   # the generator module (field500's globals), set by bind()


def bind(g):
    global G
    G = g


def put(*a, **k): G.put(*a, **k)
def at(x, z): return G.at(x, z)


def pick(mix, x, y, z, seed, blob=3.0):
    tot = sum(w for _, w in mix)
    v = noise3(x, y, z, seed, blob) * .94 + _h(x, y, z, seed) * .06
    acc = 0
    for n, w in mix:
        acc += w / tot
        if v <= acc: return n
    return mix[-1][0]


BARK = [("dark_oak_wood", 55), ("spruce_wood", 25), ("mangrove_wood", 10), ("stripped_dark_oak_log", 10)]
BARK_SHADE = [("spruce_wood", 50), ("dark_oak_wood", 40), ("mangrove_wood", 10)]


def bark(x, y, z, shade=False, lossy=True):
    if shade: return pick(BARK_SHADE, x, y, z, 701)
    n = pick(BARK if lossy else BARK[:3], x, y, z, 702)
    return n


def set_ground(x, z, y, top=("grass_block_top", "grass_block_side")):
    """Lower or raise the ground column at (x, z) to y, keeping a sensible top."""
    g = at(x, z)
    for yy in range(y + 1, g + 2):
        G.w.b.pop((x, yy, z), None)
    for yy in range(g + 1, y): put(x, yy, z, "dirt")
    put(x, y, z, *top)
    G.hi[z, x] = y


# ------------------------------------------------------------------ 1. fallen hollow giant trunk
def fallen_trunk(bx, bz, ang, L=52, D0=11.0, D1=8.0, seed=1):
    R = random.Random(seed)
    ux, uz = math.cos(ang), math.sin(ang); nx, nz = -uz, ux
    sink = .2
    cells = {}
    centre = []
    gbase = at(bx, bz)
    for i in range(int(L * 2) + 1):
        t = i / (L * 2)
        lat = math.sin(t * 2.6 + seed) * 1.6
        cx, cz = bx + ux * t * L + nx * lat, bz + uz * t * L + nz * lat
        D = D0 + (D1 - D0) * t
        rh, rv = D / 2, D / 2 * .82
        g = at(int(round(cx)), int(round(cz)))
        g = min(g, gbase + 2) if t < .15 else g                        # the heavy base end rests low
        cy = g - D * sink + rv
        centre.append((cx, cy, cz, rh, rv, t, g))
        ri = int(rh) + 2
        for s in range(-ri, ri + 1):
            for dy in range(-int(rv) - 2, int(rv) + 3):
                q = (s / rh) ** 2 + (dy / rv) ** 2
                if q > 1: continue
                for half in (0, .5):
                    x = int(round(cx + nx * s + ux * half)); z = int(round(cz + nz * s + uz * half)); y = int(round(cy + dy))
                    if y < at(x, z) - 1: continue
                    cells[(x, y, z)] = (s, dy, rh, rv, t, cy, g)
    hollow = lambda s, y, rh, cy, rv, t, g: .22 < t and abs(s) <= rh - 2.0 and g + 1 <= y <= cy + rv - 2.2
    windows = [(.5, .58), (.7, .76)]
    for (x, y, z), (s, dy, rh, rv, t, cy, g) in cells.items():
        if hollow(s, y, rh, cy, rv, t, g): continue
        top_side = dy > rv * .55
        if top_side and any(a < t < b for a, b in windows) and abs(s) < rh * .45: continue      # collapse windows in the roof
        inner = (s / max(1, rh - 1.2)) ** 2 + (dy / max(1, rv - 1.2)) ** 2 <= 1
        if inner:
            put(x, y, z, "stripped_dark_oak_log_top", "stripped_dark_oak_log")                  # sapwood under the bark
        else:
            shade = s * (1 if nz > 0 else -1) > rh * .3 or dy < -rv * .3                       # the north / under side
            put(x, y, z, bark(x, y, z, shade=shade))
    # moss along the spine, in long patches, with tongues running down the sides
    for (x, y, z), (s, dy, rh, rv, t, cy, g) in cells.items():
        if (x, y + 1, z) in G.w.b or (x, y, z) not in G.w.b: continue
        if noise3(x * .5, 0, z * .5, 711, 4) > .58:
            put(x, y, z, "moss_block")
            if _h(x, y, z, 712) < .35: put(x, y + 1, z, "moss_block", shape="carpet")
            for k in range(1, 1 + int(_h(x, 1, z, 713) * 3)):                                 # tongue
                p = (int(round(x + nx * math.copysign(1, s) * k * .5)), y - k, int(round(z + nz * math.copysign(1, s) * k * .5)))
                if p in G.w.b and G.w.b[p][0] == "full": put(*p, "moss_block")
    # the interior floor: rooted dirt and podzol, moss carpet, a mushroom or two, glow lichen in the open end
    for (cx, cy, cz, rh, rv, t, g) in centre:
        if .22 < t:
            for s in range(-int(rh - 2), int(rh - 2) + 1):
                x, z = int(round(cx + nx * s)), int(round(cz + nz * s))
                put(x, g, z, "rooted_dirt" if _h(x, 0, z, 714) < .5 else "podzol_top", "podzol_side")
                r = _h(x, 2, z, 715)
                if r < .25: put(x, g + 1, z, "moss_block", shape="carpet")
                elif r < .29: put(x, g + 1, z, R.choice(["brown_mushroom", "red_mushroom"]), shape="plant")
    cx, cy, cz, rh, rv, t, g = centre[-1]
    for k in range(5): put(int(round(cx + nx * R.uniform(-2, 2))), int(cy + rv - 2), int(round(cz + nz * R.uniform(-2, 2))), "glow_lichen", shape="ceil")
    # the hero: the upturned root plate at the base, standing almost upright, and the pit it tore out
    Rp = D0 * 1.15
    px, pz = bx - ux * 1.5, bz - uz * 1.5
    pc_y = gbase + Rp * .55
    rim = lambda a: Rp * (1 + .14 * math.sin(3 * a + seed) + .09 * math.sin(7 * a + 2 * seed))
    for s in range(-int(Rp * 1.3), int(Rp * 1.3) + 1):
        for v in range(-int(Rp * 1.3), int(Rp * 1.3) + 1):
            a = math.atan2(v, s); d = math.hypot(s, v)
            if d > rim(a): continue
            for th in range(3):
                lean = th + v * .18                                     # tilted back ~80 degrees
                x, z = int(round(px + nx * s - ux * lean)), int(round(pz + nz * s - uz * lean)); y = int(round(pc_y + v))
                if y < gbase - 3: continue
                if th == 2:                                             # the face that looks away from the trunk: soil and stones caught in roots
                    mat = pick([("coarse_dirt", 35), ("rooted_dirt", 25), ("dirt", 15), ("gravel", 6), ("cobblestone", 4), ("muddy_mangrove_roots_top", 15)], x, y, z, 721, 2.2)
                    if d > rim(a) - 1.3 and v > Rp * .3: mat = "grass_block_top"
                    put(x, y, z, *((mat, "grass_block_side") if mat == "grass_block_top" else (mat,)))
                else:
                    put(x, y, z, bark(x, y, z, shade=True) if d > Rp * .55 else "rooted_dirt")
    for k in range(7):                                                  # root stubs bristling from the rim
        a = k / 7 * math.tau + R.uniform(-.3, .3); Ls = R.uniform(2.5, 6.5)
        r0 = rim(a)
        p0 = (px + nx * math.cos(a) * r0 * .9, pc_y + math.sin(a) * r0 * .9, pz + nz * math.cos(a) * r0 * .9)
        p1 = (px + nx * math.cos(a) * (r0 + Ls) - ux * 2, pc_y + math.sin(a) * (r0 + Ls) - 1, pz + nz * math.cos(a) * (r0 + Ls) - uz * 2)
        G.tube(p0, p1, 1.0, .5, mat=("mangrove_roots_top", "mangrove_roots_side") if k % 3 == 0 else None)
    for k in range(18):                                                  # hanging roots from the lower rim
        a = R.uniform(-2.6, -.5); r0 = rim(a)
        x, z = int(round(px + nx * math.cos(a) * r0 - ux * 2.5)), int(round(pz + nz * math.cos(a) * r0 - uz * 2.5)); y = int(round(pc_y + math.sin(a) * r0))
        for j in range(1, R.randint(2, 4)):
            if (x, y - j, z) not in G.w.b and y - j > at(x, z): put(x, y - j, z, "hanging_roots", shape="plant")
    # the pit (in front of the plate's soil face) and the mound beyond it
    qx, qz = px - ux * (5 + Rp * .2), pz - uz * (5 + Rp * .2)
    deep = []
    for s in range(-int(Rp) - 2, int(Rp) + 3):
        for f in range(-6, 7):
            x, z = int(round(qx + nx * s + ux * f)), int(round(qz + nz * s + uz * f))
            d = math.hypot(s / Rp, f / 5.5)
            if d >= 1 or not (2 <= x < G.B - 2 and 2 <= z < G.B - 2): continue
            dep = int(round((1 - d * d) * 3.2))
            y = gbase - dep
            set_ground(x, z, y, ("mud",) if dep >= 2 and _h(x, 0, z, 731) < .6 else ("coarse_dirt",) if dep >= 1 else ("grass_block_top", "grass_block_side"))
            if dep >= 3: deep.append((x, y, z))
            elif dep == 1 and _h(x, 1, z, 732) < .3: put(x, y + 1, z, "fern", shape="plant")
    for (x, y, z) in deep[: max(3, len(deep) // 3)]:
        put(x, y, z, "water_still", shape="water")
    # the far end: snapped, splinters with one long hinge side; a standing snag of the same tree nearby
    cx, cy, cz, rh, rv, t, g = centre[-1]
    for k in range(9):
        s = R.uniform(-rh + .5, rh - .5); dy = R.uniform(-rv * .6, rv * .7)
        Ls = R.randint(1, 3) + (3 if s > rh * .3 else 0)                 # the hinge side is longer
        for j in range(1, Ls + 1):
            put(int(round(cx + nx * s + ux * j)), int(round(cy + dy)), int(round(cz + nz * s + uz * j)), "spruce_planks", shape="fence" if j > 1 else "full") \
                if j > 1 else put(int(round(cx + nx * s + ux * j)), int(round(cy + dy)), int(round(cz + nz * s + uz * j)), "stripped_dark_oak_log_top", "stripped_dark_oak_log")
    sx, sz = int(round(cx + ux * 9 + nx * 7)), int(round(cz + uz * 9 + nz * 7))
    sg = at(sx, sz); sh = R.randint(9, 13)
    for y in range(sg - 1, sg + sh):
        r = D1 / 2 * (1 + max(0, 2 - (y - sg)) * .25)
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                d = math.hypot(dx, dz)
                lim = sg + sh - 1 - int(3 * (.5 + .5 * math.sin(math.atan2(dz, dx) * 2 + seed)))   # jagged top
                if d <= r and y <= lim:
                    put(sx + dx, y, sz + dz, bark(sx + dx, y, sz + dz) if d > r - 1.2 else "stripped_dark_oak_log_top")
    for k in range(4):
        a = R.random() * math.tau
        for j in range(R.randint(2, 5)): put(int(round(sx + math.cos(a) * 2.5)), sg + sh + j - 2, int(round(sz + math.sin(a) * 2.5)), "spruce_planks", shape="fence")
    # branch stubs on the upper half, one broken limb lying alongside
    for k in range(5):
        cx, cy, cz, rh, rv, t, g = centre[int(len(centre) * R.uniform(.2, .85))]
        side = R.choice((-1, 1))
        p0 = (cx + nx * side * rh * .6, cy + rv * .6, cz + nz * side * rh * .6)
        p1 = (p0[0] + nx * side * R.uniform(2, 5), p0[1] + R.uniform(1, 4), p0[2] + nz * side * R.uniform(2, 5))
        G.tube(p0, p1, 1.3, .8)
    cx, cy, cz, rh, rv, t, g = centre[len(centre) // 2]
    lx0, lz0 = cx + nx * (rh + 4), cz + nz * (rh + 4)
    G.tube((lx0, at(int(lx0), int(lz0)), lz0), (lx0 + ux * 14, at(int(lx0 + ux * 14), int(lz0 + uz * 14)), lz0 + uz * 14), 1.4, 1.0)
    # nurse-log life along the spine: young trees and azalea growing out of the rotting wood; mushrooms on the shaded flank
    for k in range(5):
        cx, cy, cz, rh, rv, t, g = centre[int(len(centre) * (.3 + k * .13))]
        y = int(round(cy + rv))
        while (int(round(cx)), y + 1, int(round(cz))) in G.w.b: y += 1
        x, z = int(round(cx)), int(round(cz))
        if k % 2 == 0:
            for yy in range(y + 1, y + 4): put(x, yy, z, "oak_log_top", "oak_log")
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    for dy in (0, 1):
                        if dx * dx + dz * dz + dy * 3 <= 5 and (x + dx, y + 4 + dy, z + dz) not in G.w.b: put(x + dx, y + 4 + dy, z + dz, "oak_leaves")
        else:
            put(x, y + 1, z, R.choice(["azalea_leaves", "flowering_azalea_leaves"]), shape="plant")
    for k in range(10):
        cx, cy, cz, rh, rv, t, g = centre[int(len(centre) * R.uniform(.1, .9))]
        side = 1 if nz < 0 else -1                                      # the north flank
        for j in range(R.randint(2, 3)):
            x, z = int(round(cx + nx * side * (rh + 1))), int(round(cz + nz * side * (rh + 1)))
            put(x, int(cy) - j, z, "brown_mushroom_block", shape="slab")
    xs = [c[0] for c in centre]; zs = [c[2] for c in centre]
    G.mark(int(min(xs)) - 16, int(min(zs)) - 16, int(max(xs)) + 16, int(max(zs)) + 16)
    return centre


# ------------------------------------------------------------------ 2. ruined aqueduct pierced by a root
def aqueduct(x0, zc, bays=7, pitch=12, pier=3, seed=2, collapsed=3, road_bay=None, root_from=None):
    R = random.Random(seed)
    opening = pitch - pier; r = opening / 2
    Ltot = bays * pitch + pier
    gs = [at(x0 + u, zc) for u in range(Ltot)]
    Ys = int(sorted(gs)[len(gs) // 2]) + 9                               # springline
    crown = Ys + int(math.ceil(r))
    floor_ = crown + 2
    top = floor_ + 2
    BASE = [("mossy_stone_bricks", 40), ("stone_bricks", 30), ("mossy_cobblestone", 20), ("cobblestone", 10)]
    MID = [("stone_bricks", 55), ("cracked_stone_bricks", 25), ("tuff_bricks", 15), ("andesite", 5)]
    CORE = [("cobblestone", 50), ("tuff", 30), ("gravel", 20)]
    # how much of each pier / bay survives
    stump_piers = {0, 1}                                                 # the west end has crumbled to stumps
    pier_top = {}
    for k in range(bays + 1):
        pier_top[k] = (at(x0 + k * pitch + 1, zc) + R.randint(2, 5)) if k in stump_piers else top
    wall_loss = lambda u: noise3(u * .15, 0, 0, seed + 5, 1) > .45      # channel walls missing over about half the length
    removed = 0
    solid = {}
    for u in range(Ltot):
        x = x0 + u
        k = u // pitch; o = u % pitch
        is_pier = o < pier
        oc = o - pier - r + .5                                           # offset from the opening's centre
        intrados = Ys + math.sqrt(max(0, r * r - oc * oc)) if not is_pier else -1
        bay_dead = (not is_pier) and (k == collapsed or k in stump_piers or (k + 1) in stump_piers and k + 1 <= 1)
        for s in range(-3, 4):
            z = zc + s
            g = at(x, z)
            for y in range(g - 1, top + 1):
                half = 2.5 if y < Ys - 3 else (2 if y < floor_ else 2)
                if abs(s) > half: continue
                if not is_pier and y < intrados: continue
                if is_pier:
                    pk = k if o < pier else k
                    if y > pier_top.get(k, top): continue
                if bay_dead and not is_pier:
                    # springer stubs: 2-4 voussoirs stay on each side of the collapsed bay, broken along courses
                    keep = abs(oc) > r - 2.2 and y < Ys + 2 + (1 if oc > 0 else 0) and k == collapsed
                    if not keep: removed += 1; continue
                if k in stump_piers and not is_pier: continue
                if y >= floor_ and abs(s) <= 1 and y > floor_: continue        # the channel
                if y > floor_ and abs(s) == 2 and wall_loss(u): continue
                if y == top and _h(x, y, z, seed) < .35: continue              # the parapet is jagged
                solid[(x, y, z)] = (u, s, k, o, oc, intrados, g)
    for (x, y, z), (u, s, k, o, oc, intrados, g) in solid.items():
        ring = (not (o < pier)) and y < intrados + 1.2
        if ring:
            mat = "chiseled_stone_bricks" if abs(oc) < .6 and k != collapsed else ("polished_andesite" if int(u + y) % 2 else "tuff_bricks")
        elif y <= g + 3:
            mat = pick(BASE, x, y, z, 741, 2.5)
        elif y == floor_ and abs(s) <= 1:
            mat = "smooth_stone"
        else:
            mat = pick(MID, x, y, z, 742, 3)
        # exposed fracture faces show the rubble core
        if any((x + a, y + b, z + c) not in solid and (x + a, y + b, z + c) not in G.w.b for a, b, c in ((1, 0, 0), (-1, 0, 0), (0, 1, 0))) \
                and (k == collapsed or k in stump_piers or y >= top - 1) and abs(s) < 2:
            mat = pick(CORE, x, y, z, 743, 2)
        put(x, y, z, mat)
    # soffits darkest, with glow lichen, vines and hanging roots
    for (x, y, z), (u, s, k, o, oc, intrados, g) in solid.items():
        if (x, y - 1, z) not in solid and y > g + 1 and not (o < pier):
            if _h(x, y, z, 744) < .5: put(x, y, z, pick([("tuff", 50), ("cobbled_deepslate", 30), ("tuff_bricks", 20)], x, y, z, 745))
            r3 = _h(x, y, z, 746)
            if r3 < .08: put(x, y - 1, z, "glow_lichen", shape="ceil")
            elif r3 < .22 and abs(s) == 2:
                for j in range(1, R.randint(2, 6)):
                    if (x, y - j, z + (1 if s > 0 else -1)) in G.w.b: break
                    put(x, y - j, z + (1 if s > 0 else -1), "vine", shape="shut_s" if s > 0 else "shut_n")
    # drip stains under channel leaks: vertical moss stripes down the outer face
    for u in range(0, Ltot, 7):
        if R.random() < .6:
            x = x0 + u + R.randint(0, 3)
            for s in (-2, 2):
                for j in range(R.randint(3, 6)):
                    p = (x, floor_ - j, zc + s)
                    if p in solid: put(*p, "mossy_stone_bricks")
    # a little water still standing in the channel's intact stretches
    for u in range(pitch * (max(stump_piers) + 1), Ltot):
        if u // pitch == collapsed: continue
        if _h(x0 + u, 0, zc, 747) < .4:
            for s in (-1, 0, 1): put(x0 + u, floor_ + 1, zc + s, "water_still", shape="water") if (x0 + u, floor_, zc + s) in solid else None
        elif _h(x0 + u, 1, zc, 748) < .3:
            put(x0 + u, floor_ + 1, zc, "moss_block", shape="carpet")
    # rubble: the fallen bay piled under and beyond the gap, downhill, half buried; one arc segment lying tilted
    gx0 = x0 + collapsed * pitch + pier
    n_rub = int(removed * .18)
    for _ in range(n_rub):
        x = int(gx0 + r + R.gauss(0, r * .8)); z = int(zc + R.gauss(2.5, 3.2))
        if not (0 <= x < G.B and 0 <= z < G.B): continue
        g = at(x, z)
        if (x, g + 1, z) in G.w.b: continue
        m = pick([("stone_bricks", 3), ("cracked_stone_bricks", 3), ("cobblestone", 2), ("tuff", 1), ("gravel", 2), ("mossy_stone_bricks", 2)], x, 0, z, 749, 1.5)
        if _h(x, 2, z, 750) < .45: put(x, g, z, m)                         # half buried: replaces the turf
        else: put(x, g + 1, z, m, shape=R.choice(["full", "slab", "stair_e", "stair_n"]))
    for j in range(4):                                                   # the arc segment
        x = int(gx0 + r + 3 + j); z = zc + 5 - j // 2
        put(x, at(x, z) + 1 + (1 if j in (1, 2) else 0), z, "polished_andesite" if j % 2 else "tuff_bricks", shape="stair_w" if j == 0 else "stair_e" if j == 3 else "full")
    # a young tree growing from the rubble inside the fallen bay
    tx, tz = int(gx0 + r - 1), zc + 1
    G.oak(tx, tz, "oak", form="tall")
    # the great root: from uphill (the tree), over the channel, down through the gap, round the east pier, into the ground
    if root_from:
        rx, rz = root_from
        pe = x0 + (collapsed + 1) * pitch + 1                            # the pier east of the gap
        mx_, mz_ = (rx + gx0 + r) / 2, (rz + zc - 8) / 2
        pts = G.bez((rx, at(rx, rz) - 1, rz), (mx_, at(int(mx_), int(mz_)) + 1, mz_), (gx0 + r + 2, at(int(gx0 + r + 2), zc - 9) + 1, zc - 9), 14)   # along the ground first
        pts += G.bez((gx0 + r + 2, at(int(gx0 + r + 2), zc - 9) + 1, zc - 9), (gx0 + r + 2, top + 4, zc - 5), (gx0 + r + 1, top + 1, zc - 1), 10)
        pts += G.bez((gx0 + r + 1, top + 1, zc - 1), (gx0 + r + 3, Ys, zc + 1), (pe - 2.5, Ys - 4, zc + 2), 10)
        pts += G.bez((pe - 2.5, Ys - 4, zc + 2), (pe + 1, Ys - 7, zc + 5), (pe + 4, at(pe + 4, zc + 8) - 1, zc + 8), 10)
        for i, (p0, p1) in enumerate(zip(pts, pts[1:])):
            rr = 2.4 - i / len(pts) * 1.0
            G.tube(p0, p1, rr, rr, moss_top=True)
        for i, (x, y, z) in enumerate(pts[::3]):                         # where it presses on stone: cracked and pushed blocks
            for dx in (-3, 3):
                p = (int(round(x)) + dx, int(round(y)), int(round(z)))
                if p in solid: put(*p, "cracked_stone_bricks")
        for k in range(3):                                               # minor roots creeping down the pier faces
            px = x0 + (collapsed + 1 + k % 2) * pitch + R.randint(0, 2)
            G.tube((px, top, zc - 3), (px + R.uniform(-1, 1), Ys - 2, zc - 3), .7, .5)
            for j in range(1, 4): put(px, Ys - 2 - j, zc - 3, "hanging_roots", shape="plant")
    # ferns and tall grass at the pier feet
    for k in range(bays + 1):
        for s in (-4, 4):
            for j in range(3):
                x = x0 + k * pitch + R.randint(-1, 3); z = zc + s + R.randint(-1, 1)
                if (x, at(x, z) + 1, z) not in G.w.b: put(x, at(x, z) + 1, z, R.choice(["fern", "tall_grass_top", "fern"]), shape="plant")
    G.mark(x0 - 4, zc - 10, x0 + Ltot + 4, zc + 12)
    return dict(Ys=Ys, top=top, x0=x0, Ltot=Ltot)


# ------------------------------------------------------------------ 3. giant cut stump
def stump(cx, cz, D=15, seed=3):
    R = random.Random(seed)
    rad = D / 2
    g = at(cx, cz)
    H = int(D * .55)                                                     # a tall drum: reads as a stump from far
    yt = g + H
    wob = lambda a, k=0: 1 + .06 * math.sin(5 * a + seed + k) + .04 * math.sin(9 * a + 2 * seed + k)
    step_dir = R.uniform(0, math.tau)                                    # the felling step: one third of the face stands a block higher
    sdx, sdz = math.cos(step_dir), math.sin(step_dir)
    fy = lambda dx, dz: yt + (1 if (dx * sdx + dz * sdz) > rad * .33 else 0) + int(round(-dz / rad * 2.5))
    for dx in range(-int(rad * 1.7) - 1, int(rad * 1.7) + 2):
        for dz in range(-int(rad * 1.7) - 1, int(rad * 1.7) + 2):
            a = math.atan2(dz, dx); d = math.hypot(dx, dz)
            flute = .45 * (1 if int((a + math.pi) / math.tau * 2 * math.pi * rad / 2.5) % 2 else 0)   # vertical bark plates
            x, z = cx + dx, cz + dz
            gg = at(x, z)
            high = (dx * sdx + dz * sdz) > rad * .33
            ytop = fy(dx, dz)                                              # the cut slopes down toward the south: the rings face the road
            for y in range(gg - 1, ytop + 1):
                f = max(0, (g + 3 - y)) / 3                                  # flare over the bottom 3 layers
                rr = rad * wob(a) * (1 + .5 * f) + flute
                if d > rr: continue
                if y == ytop and d < rr - 1:
                    # the cut face: rings from the centre, each border wobbled
                    rn = d / (rad * wob(a, 3))
                    if rn > .88: m = ("dark_oak_log_top", "dark_oak_log")
                    elif rn > .72: m = ("stripped_oak_log_top", "stripped_oak_log")
                    elif .5 < rn < .56: m = ("stripped_mangrove_log_top", "stripped_mangrove_log")
                    elif rn > .26: m = ("stripped_spruce_log_top", "stripped_spruce_log")
                    else: m = None                                           # heart rot
                    if m: put(x, y, z, *m)
                    continue
                out = d > rr - 1.3
                if out:
                    shade = dz < 0 and abs(dx) < rad
                    peel = y > ytop - 3 and noise3(x, y, z, 761, 2) > .72      # bark peels from the top down
                    put(x, y, z, *(("stripped_dark_oak_log_top", "stripped_dark_oak_log") if peel else (bark(x, y, z, shade=shade, lossy=False),)))
                else:
                    put(x, y, z, "stripped_spruce_log_top", "stripped_spruce_log")
    # heart rot: a hollow 2 deep, soil, moss, a fern and a sapling
    for dx in range(-int(rad * .3) - 1, int(rad * .3) + 2):
        for dz in range(-int(rad * .3) - 1, int(rad * .3) + 2):
            if math.hypot(dx, dz) < rad * .27:
                for y in (yt, yt - 1): G.w.b.pop((cx + dx, y, cz + dz), None)
                put(cx + dx, yt - 2, cz + dz, "rooted_dirt" if _h(dx, 0, dz, 762) < .5 else "podzol_top", "podzol_side")
                if _h(dx, 1, dz, 763) < .3: put(cx + dx, yt - 1, cz + dz, R.choice(["fern", "brown_mushroom", "moss_block"]), shape="plant" if _h(dx, 2, dz, 764) < .7 else "carpet")
    for yy in range(yt - 1, yt + 2): put(cx, yy, cz, "birch_log_top", "birch_log")    # a birch sapling has taken the heart
    for dx in range(-1, 2):
        for dz in range(-1, 2): put(cx + dx, yt + 2, cz + dz, "birch_leaves")
    put(cx, yt + 3, cz, "birch_leaves")
    # radial drying cracks
    for k in range(3):
        a = R.uniform(0, math.tau)
        for j in range(int(rad * .45), int(rad * .95)):
            p = (int(round(cx + math.cos(a) * j)), fy(int(round(math.cos(a) * j)), int(round(math.sin(a) * j))), int(round(cz + math.sin(a) * j)))
            if p in G.w.b: put(*p, "dark_oak_log_top", "dark_oak_log")
    # the hinge: a strip of splinters standing up along the step line (the torn fibres when the tree went over)
    for j in range(-int(rad * .8), int(rad * .8) + 1):
        dx_, dz_ = int(round(sdx * rad * .33 - sdz * j)), int(round(sdz * rad * .33 + sdx * j))
        x, z = cx + dx_, cz + dz_
        y0 = fy(dx_ + int(round(sdx)), dz_ + int(round(sdz)))
        if (x, y0, z) not in G.w.b: continue
        tall = int(1 + 5 * max(0, math.sin(j / (rad * .8) * 2.4 + seed)) * (.4 + .6 * _h(x, 2, z, 766)))
        for q in range(tall):
            put(x, y0 + 1 + q, z, *(("stripped_dark_oak_log_top", "stripped_dark_oak_log") if q < tall - 1 else ("dark_oak_planks",)), shape="full" if q < tall - 1 else "fence")
    # moss creeps over the rim on the shaded side only
    for dx in range(-int(rad) - 1, int(rad) + 2):
        for dz in range(-int(rad) - 1, 0):
            d = math.hypot(dx, dz)
            if rad - 1.6 < d < rad + .4 and _h(dx, 3, dz, 766) < .45:
                y = fy(dx, dz)
                if (cx + dx, y + 1, cz + dz) not in G.w.b: put(cx + dx, y + 1, cz + dz, "moss_block", shape="carpet")
    # buttress roots: tall thin plates of unequal length, convex profile, diving and surfacing once more
    angs = []
    a = R.uniform(0, math.tau)
    for k in range(6):
        angs.append(a); a += math.radians(R.uniform(42, 78))
    for k, a in enumerate(angs):
        major = k < 2
        Lr = D * (R.uniform(1.6, 2.3) if major else R.uniform(.6, 1.0))
        h0 = H * (R.uniform(.6, .75) if major else R.uniform(.3, .45))
        ca, sa = math.cos(a), math.sin(a)
        for i in range(int(Lr * 2)):
            l = i / 2
            f = l / Lr
            hh = h0 * (1 - f) ** 1.8 if f < .82 else (max(0, math.sin((f - .82) / .18 * math.pi) * 1.3) if major else 0)  # dives, then a hump
            th = 2.5 * (1 - f) + 1
            for tq in range(-int(th // 2) - 1, int(th // 2) + 2):
                if abs(tq) > th / 2: continue
                x = int(round(cx + ca * (rad * .8 + l) - sa * tq)); z = int(round(cz + sa * (rad * .8 + l) + ca * tq))
                gg = at(x, z)
                for y in range(gg - 1, gg + int(round(hh)) + 1):
                    if y == gg + int(round(hh)) and hh > .5 and _h(x, y, z, 767) < .5: put(x, y, z, "moss_block"); continue
                    put(x, y, z, bark(x, y, z, shade=(sa < 0), lossy=False))
            if f > .8 and _h(i, k, 0, 768) < .3:
                x = int(round(cx + ca * (rad * .8 + l))); z = int(round(cz + sa * (rad * .8 + l)))
                put(x, at(x, z), z, "muddy_mangrove_roots_top", "muddy_mangrove_roots_side")
    # shelf brackets of mushroom on the shaded side, stepped
    for k in range(3):
        a = -math.pi / 2 + R.uniform(-.6, .6); y = g + 1 + k + R.randint(0, 1)
        for j in range(R.randint(2, 3)):
            x = int(round(cx + math.cos(a + j * .12) * (rad + 1.4))); z = int(round(cz + math.sin(a + j * .12) * (rad + 1.4)))
            put(x, y + j % 2, z, "brown_mushroom_block", shape="slab")
    # the ground between the roots: rooted dirt, coarse dirt, moss and ferns, leaf litter
    for dx in range(-int(D * 1.6), int(D * 1.6) + 1):
        for dz in range(-int(D * 1.6), int(D * 1.6) + 1):
            d = math.hypot(dx, dz)
            if rad * 1.3 < d < D * 1.5 and (cx + dx, at(cx + dx, cz + dz) + 1, cz + dz) not in G.w.b:
                x, z = cx + dx, cz + dz; gg = at(x, z)
                if noise3(x, 0, z, 769, 3) > .55 - (1 - d / (D * 1.5)) * .3:
                    put(x, gg, z, pick([("rooted_dirt", 3), ("coarse_dirt", 2), ("moss_block", 3)], x, 0, z, 770, 2.5))
                    r3 = _h(x, 1, z, 771)
                    if r3 < .12: put(x, gg + 1, z, "fern", shape="plant")
                    elif r3 < .2: put(x, gg + 1, z, "leaf_litter", shape="carpet")
    G.mark(cx - int(D * 1.8), cz - int(D * 1.8), cx + int(D * 1.8), cz + int(D * 1.8))
    return yt
