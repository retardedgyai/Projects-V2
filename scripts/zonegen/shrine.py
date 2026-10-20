"""The sakura route: Gaur-style rock pillars rising off the high plateau's edge, joined by bridges (rock, a great root,
a rope bridge), and the world boss's shrine arena on the highest. Ported from the field500 prototype; the pillars' stems
are reserved before roads are routed (see zone spec "pillars"), and every walkable top or deck is written into UPPER.
"""
import math
import random
import numpy as np
from greattree import noise3, _h, bark
import terrainmat as TM

LOG = lambda n: (f"{n}_top", n)
PLANTED = []
CONTENT = []
LABELS_POI = []


def bind(ctx, upper):
    g = globals()
    g.update(put=ctx.put, at=ctx.at, w=ctx.bl, B=ctx.B, bez=ctx.bez, tube=ctx.tube, mark=ctx.mark, R=ctx.R,
             hi=ctx.hi, UPPER=upper, lake=ctx.wet.copy(), stream=ctx.wet.copy(), road_d=ctx.z.road_d)


def cache(x, y, z, what):
    put(x, y, z, "barrel_top", "barrel_side")


def sakura_tree(x, z, g, size=1.0):
    """A Somei Yoshino: short thick trunk forking low into 3-5 spreading limbs, an umbrella crown wider than tall built of
    separate blossom pads at different heights, with gaps where the dark limbs show through (research-builds.md recipe 5)."""
    rs = random.Random(x * 9973 + z)
    H = 8 + 7 * size
    W = H * rs.uniform(1.25, 1.45)
    D = max(1.2, H / 8)
    fork = g + H * rs.uniform(.2, .28)
    lean = (rs.uniform(-1, 1) * H / 9, rs.uniform(-1, 1) * H / 9)
    def wood(px, py, pz, base=False):
        n = "dark_oak_wood" if base or _h(px, py, pz, 801) < .2 else ("mangrove_wood" if _h(px, py, pz, 802) < .1 else "cherry_wood")
        return (n, n)
    top = (x + lean[0] * .4, fork, z + lean[1] * .4)
    for y in range(int(g) - 1, int(fork) + 1):                       # trunk with a flared foot
        t = (y - g) / max(1, fork - g)
        r = D / 2 * (1 + max(0, .15 - t) * 6) if t < .15 else D / 2
        cx_, cz_ = x + lean[0] * .4 * max(0, t), z + lean[1] * .4 * max(0, t)
        ri = int(r) + 2
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                if math.hypot(dx, dz) <= r + .35:
                    px, pz = int(round(cx_ + dx)), int(round(cz_ + dz))
                    put(px, y, pz, *wood(px, y, pz, base=t < .2))
    pads = []
    nl = rs.randint(3, 5)
    a0 = rs.uniform(0, math.tau)
    def limb(p, ang, elev, L, r, depth):
        e = (p[0] + math.cos(ang) * math.cos(elev) * L, p[1] + math.sin(elev) * L, p[2] + math.sin(ang) * math.cos(elev) * L)
        mid = ((p[0] + e[0]) / 2, (p[1] + e[1]) / 2 + L * .08, (p[2] + e[2]) / 2)        # limbs arch, then droop
        pts = bez(p, mid, e, max(3, int(L)))
        for i, (q0, q1) in enumerate(zip(pts, pts[1:])):
            rr = r * (1 - .35 * i / len(pts))
            n0 = int(math.dist(q0, q1) * 2) + 1
            for k in range(n0 + 1):
                f = k / n0
                cxp, cyp, czp = q0[0] + (q1[0] - q0[0]) * f, q0[1] + (q1[1] - q0[1]) * f, q0[2] + (q1[2] - q0[2]) * f
                ri = int(math.ceil(rr))
                for dx in range(-ri, ri + 1):
                    for dy in range(-ri, ri + 1):
                        for dz in range(-ri, ri + 1):
                            if dx * dx + dy * dy + dz * dz <= rr * rr + .3:
                                px, py, pz = int(round(cxp)) + dx, int(round(cyp)) + dy, int(round(czp)) + dz
                                put(px, py, pz, *wood(px, py, pz))
        if depth < 2:
            for s_ in (-1, 1):
                limb(e, ang + s_ * rs.uniform(.35, .7), max(-.1, elev - rs.uniform(.15, .35)), L * rs.uniform(.55, .7), max(.5, r * .7), depth + 1)
        else:
            pads.append(e)
    for k in range(nl):
        ang = a0 + k / nl * math.tau + rs.uniform(-.35, .35)
        reach = W / 2 * rs.uniform(.38, .5)
        limb(top, ang, math.radians(rs.uniform(30, 50)), reach, max(.6, D / 2 * .72), 0)
    limb(top, rs.uniform(0, math.tau), math.radians(70), H * .25, max(.6, D / 2 * .6), 1)    # a leader up the middle
    ps = max(1.0, size)
    for (px, py, pz) in pads:                                       # blossom pads: domed top, flatter thinner underside, gaps
        rx = rs.uniform(3, 4.2) * (.7 + .3 * ps); ry = rx * rs.uniform(.6, .75)                 # rounder: a dome, not a table top
        ri = int(rx) + 1
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                for dy in range(-int(ry) - 1, int(ry) + 2):
                    q = (dx / rx) ** 2 + (dz / rx) ** 2 + (dy / (ry if dy > 0 else ry * .7)) ** 2
                    if q > 1: continue
                    fill = 1.0 if q < .3 else (.78 if q < .6 else .32)          # a solid heart so no limb tip shows bare
                    if dy < 0: fill *= .6
                    p = (int(round(px)) + dx, int(round(py)) + dy, int(round(pz)) + dz)
                    if p in w.b or rs.random() > fill: continue
                    put(*p, "flowering_azalea_leaves" if (dy < 0 and q < .5 and rs.random() < .3) else "cherry_leaves")
        for _ in range(int(rx * .5)):                                # the hem droops a little (a few strands, not a curtain)
            a = rs.uniform(0, math.tau)
            p = (int(round(px + math.cos(a) * rx * .9)), int(round(py - ry * .5)), int(round(pz + math.sin(a) * rx * .9)))
            for k in range(rs.randint(1, 3)):
                q2 = (p[0], p[1] - k, p[2])
                if q2 in w.b or q2[1] <= at(p[0], p[2]) + 2: break
                put(*q2, "cherry_leaves")
    for _ in range(int(60 * size)):                                     # petals under the drip line, thinning toward the trunk
        a = rs.random() * math.tau; r = W / 2 * math.sqrt(rs.uniform(.15, 1.1))
        px, pz = int(round(x + math.cos(a) * r)), int(round(z + math.sin(a) * r))
        if 0 <= px < B and 0 <= pz < B:
            gy2 = UPPER[pz, px] if UPPER[pz, px] >= 0 else at(px, pz)
            if (px, gy2 + 1, pz) not in w.b: put(px, gy2 + 1, pz, "pink_petals", shape="carpet")


def torii(cx, cz, y, ux, uz, half=3, h=6):
    """Myojin torii across a path heading (ux, uz). Posts are whole from base to shimaki; the nuki is one beam that runs
    through the posts and one block past them; a small dark plaque sits between nuki and shimaki; the dark kasagi is one
    block wider than the shimaki on each side and its tips turn up by a half block."""
    if abs(ux) >= abs(uz): ux, uz = math.copysign(1, ux), 0.0          # a torii stands square to the grid: diagonals staircase
    else: ux, uz = 0.0, math.copysign(1, uz)
    px, pz = -uz, ux
    P = lambda s2: (int(round(cx + px * s2)), int(round(cz + pz * s2)))
    nuki_y = y + h - 2
    for s2 in (-half, half):
        bx, bz = P(s2)
        put(bx, y + 1, bz, "polished_blackstone")                    # kamebara: a black foot on each post
        for dx2, dz2, f2 in ((px, pz, None),):
            pass
        for yy in range(y + 2, y + h + 1): put(bx, yy, bz, "red_concrete")
    for s2 in range(-half - 1, half + 2):
        if abs(s2) == half: continue
        bx, bz = P(s2)
        put(bx, nuki_y, bz, "red_nether_bricks" if abs(s2) == half + 1 else "red_concrete", shape="slab_t" if abs(s2) == half + 1 else "full")
    if h >= 10 and half >= 3:                                       # paper lanterns hang under the nuki
        for s2 in (-(half // 2 + 1), half // 2 + 1):
            bx, bz = P(s2); chochin(bx, nuki_y - 1, bz)
    big = h >= 14
    bx, bz = P(0)
    for yy in range(nuki_y + 1, y + h): put(bx, yy, bz, "polished_deepslate")       # the plaque, a dark board, not a hole
    if False:                                                        # (doubled posts removed: they read as a theme-park gate)
        for s2 in (-half, half):
            for k in (1,):
                bx2, bz2 = P(s2 + (k if s2 > 0 else -k))
                put(bx2, y + 1, bz2, "polished_andesite")
                for yy in range(y + 2, y + h + 1): put(bx2, yy, bz2, "red_concrete")
    for s2 in range(-half - 1, half + 2):
        bx, bz = P(s2); put(bx, y + h, bz, "red_concrete")
    ext = half + (3 if big else 2)
    for s2 in range(-ext, ext + 1):
        bx, bz = P(s2); put(bx, y + h + 1, bz, "deepslate_tiles", shape="full" if abs(s2) < ext else "slab")
        if big and abs(s2) < ext - 1: put(bx, y + h + 2, bz, "deepslate_tiles", shape="slab")
    for s2 in (-ext, ext):
        bx, bz = P(s2); put(bx, y + h + 2, bz, "deepslate_tiles", shape="slab" if not big else "full")
        if big: put(bx, y + h + 3, bz, "deepslate_tiles", shape="slab")
    bx, bz = P(0)
    for (dx, dz) in ((0, 0),):
        pass


def _firebox(x, y, z, frame="dark_oak_trapdoor"):
    """A lit fire box: the lantern in the middle, open trapdoors on the four sides as the paper screens."""
    put(x, y, z, "lantern", shape="lantern")
    for dx, dz, sh in ((1, 0, "shut_e"), (-1, 0, "shut_w"), (0, 1, "shut_s"), (0, -1, "shut_n")):
        put(x + dx, y, z + dz, frame, shape=sh)


def _hip_roof(x, y, z, mat="deepslate_tiles"):
    """3x3 hipped cap: a block in the centre, stairs on the sides rising toward it, slabs on the corners."""
    put(x, y, z, mat)
    for dx, dz, f in ((1, 0, "w"), (-1, 0, "e"), (0, 1, "n"), (0, -1, "s")):
        put(x + dx, y, z + dz, mat, shape="stair_" + f)
    for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        put(x + dx, y, z + dz, mat, shape="slab")


def stone_lantern(x, z, y, tall=False):
    """Kasuga-style ishidoro: a stepped foot, a slender shaft, the screened fire box, a 3x3 hipped roof, a jewel.
    tall=True: a 3x3 plinth and a longer shaft, for the precinct."""
    put(x, y + 1, z, "polished_andesite")
    if tall:
        for dx, dz, f in ((1, 0, "w"), (-1, 0, "e"), (0, 1, "n"), (0, -1, "s")):
            put(x + dx, y + 1, z + dz, "stone_bricks", shape="stair_" + f)
        for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            put(x + dx, y + 1, z + dz, "stone_bricks", shape="slab")
        put(x, y + 2, z, "chiseled_stone_bricks")
        for yy in (y + 3, y + 4): put(x, yy, z, "stone_bricks", shape="wall")
        fy = y + 6
    else:
        put(x, y + 2, z, "stone_bricks", shape="wall")
        put(x, y + 3, z, "stone_bricks", shape="wall")
        fy = y + 5
    put(x, fy - 1, z, "polished_andesite")                         # chudai: the platform the fire box sits on
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz: put(x + dx, fy - 1, z + dz, "polished_andesite", shape="slab_t")
    _firebox(x, fy, z)
    _hip_roof(x, fy + 1, z)
    put(x, fy + 2, z, "polished_andesite", shape="post")


def chochin(x, y, z):
    """A hanging red paper lantern: chain and a red body."""
    put(x, y, z, "iron_chain", shape="chain")
    put(x, y - 1, z, "red_concrete", shape="lantern")


def pavilion(cx, cz, y, r=3, roof="deepslate_tiles", kind="open", face=(0, 1)):
    """A small open shrine pavilion: raised stone floor, vermilion posts, a hipped tile roof with upturned eaves, a gold finial."""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            put(cx + dx, y + 1, cz + dz, "polished_andesite" if max(abs(dx), abs(dz)) == r else "spruce_planks", shape="full")
    for dx, dz in ((-r + 1, -r + 1), (r - 1, -r + 1), (-r + 1, r - 1), (r - 1, r - 1)):
        for yy in range(y + 2, y + 6): put(cx + dx, yy, cz + dz, "red_concrete", shape="post")
    for layer in range(3):                                           # roof: three shrinking tiers, the eaves one block past the posts
        rr = r + 1 - layer
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                edge = max(abs(dx), abs(dz)) == rr
                if edge or layer == 2:
                    corner = abs(dx) == rr and abs(dz) == rr
                    put(cx + dx, y + 6 + layer + (1 if corner and layer == 0 else 0), cz + dz, roof, shape="slab" if (edge and layer < 2) else "full")
        for dx in range(-rr + 1, rr):
            for dz in range(-rr + 1, rr):
                put(cx + dx, y + 6 + layer, cz + dz, "dark_oak_planks" if layer < 2 else roof)
    put(cx, y + 9, cz, "gold_block" if kind == "hall" else roof, shape="post")
    put(cx, y + 5, cz, "lantern", shape="lantern")
    if kind == "hall":                                               # a closed sanctuary: white plaster between dark posts, open only at the front
        for dx in range(-r + 1, r):
            for dz in range(-r + 1, r):
                if max(abs(dx), abs(dz)) != r - 1: continue
                front = (dx * face[0] + dz * face[1]) == r - 1 and abs(dx * face[1] - dz * face[0]) <= 1
                if front: continue
                corner = abs(dx) == r - 1 and abs(dz) == r - 1
                for yy in range(y + 2, y + 6):
                    put(cx + dx, yy, cz + dz, "red_concrete" if corner else ("dark_oak_planks" if yy in (y + 2, y + 5) else "white_concrete"))
        put(cx - face[0] * (r - 2), y + 2, cz - face[1] * (r - 2), "gold_block", shape="slab")    # the mirror on the altar
        for k in (-1, 0, 1):                                         # steps up to the front
            put(cx + face[0] * (r + 1) + face[1] * k, y + 1, cz + face[1] * (r + 1) - face[0] * k, "polished_andesite", shape="slab")
    elif kind == "chozuya":                                          # the water basin under its own small roof, a dipper laid across
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                put(cx + dx, y + 2, cz + dz, "polished_andesite" if (dx or dz) else "water_still", shape="full" if (dx or dz) else "water")
        put(cx + 1, y + 3, cz, "bamboo_block", shape="thinlog")
    else:
        put(cx, y + 2, cz, "barrel_top", "barrel_side")              # offering box
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1): UPPER[cz + dz, cx + dx] = y + 1


PLANTED = []


def mesa(cx, cz, top_y, rb, rt, seed, needle=False, sakura=0.0, clear=0.0):
    sakura = float(sakura)
    LEAF = "cherry_leaves" if sakura >= .5 else "oak_leaves"
    base = min(at(cx + dx, cz + dz) for dx in (-int(rb), 0, int(rb)) for dz in (-int(rb), 0, int(rb))) - 2
    Hm = top_y - base
    for y in range(base, top_y + 1):
        f = (y - base) / Hm
        if f < .45: r = rb + (.72 * rb - rb) * (f / .45)
        elif f < .82: r = .72 * rb + (rt - .72 * rb) * ((f - .45) / .37) ** 1.7
        else: r = rt * (1 - max(0, f - .97) * 3)
        ri = int(r * 1.25) + 2
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                a = math.atan2(dz, dx)
                rr = r * (1 + .22 * (noise3(math.cos(a) * 3 + seed, y * .25, math.sin(a) * 3, seed, 1.0) - .5) * 2)
                x, z = cx + dx, cz + dz
                if not needle and y < top_y - 5:
                    nl = 3 + seed % 3                                   # an uneven lobed plan, not a lathe-turned disc
                    rr *= 1 + (.14 * math.cos(nl * a + seed) + .07 * math.cos((nl + 2) * a + seed * 2)) * min(1, (top_y - 5 - y) / 6)
                    fis = (a * 7 + seed) % math.tau                     # vertical fissures cut into the stem and waist
                    if y < top_y - 4 and min(fis, math.tau - fis) < .09 + .05 * math.sin(seed + a): rr -= 2.2
                d = math.hypot(dx, dz)
                if d > rr: continue
                if not (0 <= x < B and 0 <= z < B) or y <= at(x, z) - 1: continue
                if needle:
                    put(x, y, z, "moss_block" if y >= top_y - 1 else TM.cliff_block(x, y, z, f, .2 * (d < rr - 1)))
                elif y == top_y:
                    put(x, y, z, "grass_block_top", "grass_block_side"); UPPER[z, x] = top_y
                elif y >= top_y - 3 and d > rr - 1.6:
                    put(x, y, z, "moss_block" if y < top_y - 1 else ("cherry_leaves" if noise3(x, y, z, 191, 3) < sakura else "oak_leaves"))
                else:
                    n = noise3(x, y, z, seed + 7, 2.2)
                    up_open = y > base + 2 and f > .45                          # the overhang's underside and the waist
                    rec = .3 if .45 < f < .8 else 0.0                                    # the overhang belly sits in shadow                              # the waist: only a little darker
                    mossy = top_y - y <= 16 and TM.moss_streak(x, y, z, top_y)       # streaks drip from the lip only
                    dd = max(.5, math.hypot(dx, dz)); ex_ = -(dx / dd * .6 + dz / dd * .8)       # which way this face looks: the sunlit side is pale
                    mat = "moss_block" if mossy else TM.cliff_block(x, y, z, .35 + f * .45, rec + (.35 if d < rr - 1.2 else 0), ex_)
                    put(x, y, z, mat)
    for _ in range(int(rt * (10 if not needle else 2))):           # a curtain of vines and leaf drapes from the lip
        a = R.random() * math.tau
        out = rt * (1 + .22 * (noise3(math.cos(a) * 3 + seed, top_y * .25, math.sin(a) * 3, seed, 1.0) - .5) * 2)
        x, z = int(round(cx + math.cos(a) * (out + .6))), int(round(cz + math.sin(a) * (out + .6)))
        if not (0 <= x < B and 0 <= z < B): continue
        drape = False
        for k2 in range(R.randint(1, 3) if drape else R.randint(4, 14)):
            p = (x, top_y - k2, z)
            if p in w.b or p[1] <= at(x, z): break
            pk = R.random() < sakura
            put(*p, ("cherry_leaves" if pk else "oak_leaves") if drape else "vine", shape="full" if drape else "shut_s")
    if needle:
        mark(cx - int(rb) - 3, cz - int(rb) - 3, cx + int(rb) + 3, cz + int(rb) + 3)
        return
    for k in range(int(rb * 2.2)):                                  # rockfall apron: boulders of three sizes downhill of the stem
        a = R.uniform(0, math.tau); rr = rb * R.uniform(1.0, 1.9)
        x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
        if not (3 <= x < B - 3 and 3 <= z < B - 3) or road_d[z, x] < 4 or lake[z, x] or stream[z, x]: continue
        g = at(x, z); sz = R.choice([.8, .8, 1.3, 1.3, 2.2])
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(0, 3):
                    if (dx / sz) ** 2 + (dz / sz) ** 2 + (dy / (sz * .8)) ** 2 <= 1:
                        put(x + dx, g + dy, z + dz, "moss_block" if dy == int(sz * .8) and _h(x + dx, dy, z + dz, 622) < .6 else TM.cliff_block(x + dx, g + dy, z + dz, .5, .2))
    la = R.uniform(0, math.tau)                                     # two ledges on one flank, vines hanging off their lips
    for k, (fy, w_) in enumerate(((.35, 2.4), (.6, 1.8))):
        y = int(base + Hm * fy)
        for dd in range(-5, 6):
            a = la + dd * .07
            r0 = rb * .75 + (rt - rb * .75) * max(0, fy - .45) / .37
            for t in range(int(w_ + 1)):
                x, z = int(round(cx + math.cos(a) * (r0 + t))), int(round(cz + math.sin(a) * (r0 + t)))
                if (x, y, z) not in w.b and abs(dd) < 5 - t:
                    put(x, y, z, TM.cliff_block(x, y, z, .6, 0)); put(x, y + 1, z, "moss_block", shape="carpet")
                    if t == int(w_) and _h(x, y, z, 623) < .5:
                        for j in range(1, R.randint(3, 9)):
                            if (x, y - j, z) in w.b or y - j <= at(x, z): break
                            put(x, y - j, z, "vine", shape="shut_s")
    for _ in range(int(rt * (.55 - .3 * sakura)) + 1):               # trees on top: sakura share follows the route gradient (sakura are big, so fewer)
        a = R.random() * math.tau; rr = rt * (clear + (.7 - clear) * math.sqrt(R.random()))
        x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
        if (x, top_y + 1, z) in w.b: continue
        if R.random() < sakura:
            if any(math.hypot(x - tx2, z - tz2) < 13 for (tx2, tz2) in PLANTED): continue
            PLANTED.append((x, z))
            sakura_tree(x, z, top_y, size=R.uniform(.8, 1.2) + sakura * .2)
            continue
        th = R.randint(4, 7)
        for y in range(top_y + 1, top_y + th + 1): put(x, y, z, *LOG("oak_log"))
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(-2, 2):
                    if dx * dx + dz * dz + dy * dy * 2 <= 9 + R.random() * 3 and (x + dx, top_y + th + dy, z + dz) not in w.b:
                        put(x + dx, top_y + th + dy, z + dz, "oak_leaves")
    for _ in range(int(rt * rt * .5)):
        a = R.random() * math.tau; rr = rt * .85 * math.sqrt(R.random())
        x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
        if (x, top_y + 1, z) not in w.b:
            if R.random() < sakura: put(x, top_y + 1, z, "pink_petals", shape="carpet")
            else: put(x, top_y + 1, z, R.choice(["short_grass", "short_grass", "poppy", "dandelion", "fern"]), shape="plant")
    mark(cx - int(rb) - 3, cz - int(rb) - 3, cx + int(rb) + 3, cz + int(rb) + 3)


def rock_bridge(p0, p1, rise=3, width=2.2, sakura=0.0, style="rock"):
    rise = min(rise, 2)
    if style == "rope": width = 1.5; rise = -2
    samples = []
    """A natural rock bridge with a grassy walkable deck; the deck height goes into UPPER."""
    L = math.dist((p0[0], p0[2]), (p1[0], p1[2])); n = int(L * 2)
    cpt = lambda t: (int(round(p0[0] + (p1[0] - p0[0]) * t)), int(round(p0[2] + (p1[2] - p0[2]) * t)))
    # the span between leaving the first top and reaching the second: the slope lives only there
    ts = 0.0
    for i in range(n + 1):
        x0_, z0_ = cpt(i / n)
        if UPPER[z0_, x0_] == p0[1]: ts = i / n
        else: break
    te = 1.0
    for i in range(n, -1, -1):
        x0_, z0_ = cpt(i / n)
        if UPPER[z0_, x0_] == p1[1]: te = i / n
        else: break
    for i in range(n + 1):
        t = i / n
        cx, cz = p0[0] + (p1[0] - p0[0]) * t, p0[2] + (p1[2] - p0[2]) * t
        u = min(1, max(0, (t - ts) / max(1e-6, te - ts)))
        deck = int(round(p0[1] + (p1[1] - p0[1]) * u + rise * math.sin(math.pi * u)))
        if style == "rope":
            ux_, uz_ = (p1[0] - p0[0]) / L, (p1[2] - p0[2]) / L
            for s_ in (-1.5, -1, -.5, 0, .5, 1, 1.5, -2.2, 2.2):
                x, z = int(round(cx - uz_ * s_)), int(round(cz + ux_ * s_))
                if UPPER[z, x] >= deck and abs(s_) < 2: continue
                if abs(s_) < 2:
                    put(x, deck, z, "spruce_planks", shape="slab_t"); UPPER[z, x] = max(UPPER[z, x], deck)
                    w.b.pop((x, deck + 1, z), None)
                elif UPPER[z, x] < deck:
                    put(x, deck, z, "spruce_planks", shape="slab_t")
                    put(x, deck + 1, z, *(("spruce_log", "spruce_log") if i % 12 == 0 else ("spruce_planks",)), shape="full" if i % 12 == 0 else "fence")
                    if i % 12 == 0: put(x, deck + 2, z, "spruce_planks", shape="fence")
            samples.append((cx, cz, deck, u))
            continue
        under = 2 + 5 * (1 - math.sin(math.pi * t)) + 2 * noise3(cx, 0, cz, 151, 3)       # thicker at the ends
        wid = width + .8 * noise3(cx, 1, cz, 152, 4)
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                x, z = int(round(cx + dx)), int(round(cz + dz))
                if not (0 <= x < B and 0 <= z < B): continue
                d = math.hypot(x - cx, z - cz)
                if d > wid + 1.2: continue
                if style == "rope":
                    if d <= 1.5 and UPPER[z, x] < deck:
                        put(x, deck, z, "spruce_planks"); UPPER[z, x] = deck
                    elif 1.5 < d <= 2.5 and i % 5 == 0 and (x, deck, z) not in w.b:
                        put(x, deck, z, "spruce_log", "spruce_log"); put(x, deck + 1, z, "spruce_planks", shape="fence")
                    elif 1.5 < d <= 2.5 and (x, deck + 1, z) not in w.b and UPPER[z, x] < deck:
                        put(x, deck + 1, z, "iron_chain", shape="chain")
                    continue
                if d <= wid:
                    top_ = ("grass_block_top", "grass_block_side") if style == "rock" else (("moss_block",) if noise3(x, 0, z, 154, 2) > .45 else ("grass_block_top", "grass_block_side"))
                    put(x, deck, z, *top_); UPPER[z, x] = max(UPPER[z, x], deck)
                    if w.b.get((x, deck + 1, z), ("", "", ""))[1] in ("vine",): w.b.pop((x, deck + 1, z), None)
                if style == "root":
                    for y in range(int(deck - (1.5 + under * .5) * (1 - (d / (wid + 1.2)) ** 2)), deck):
                        if y > at(x, z): put(x, y, z, *bark(x, y, z))
                    continue
                for y in range(int(deck - under * (1 - (d / (wid + 1.2)) ** 2)), deck):
                    if y > at(x, z): put(x, y, z, TM.cliff_block(x, y, z, .55 + (y - deck) / 20, .2) if noise3(x, y, z, 153, 2) < .5 else "moss_block")
                if wid < d <= wid + 1.2 and (x, deck, z) not in w.b:
                    put(x, deck, z, "cherry_leaves" if R.random() < sakura else ("azalea_leaves" if (x + z) % 3 else "oak_leaves"))
                if d <= wid and R.random() < sakura * .5 and (x, deck + 1, z) not in w.b: put(x, deck + 1, z, "pink_petals", shape="carpet")
        samples.append((cx, cz, deck, u))
        if i % 7 == 3 and style != "rope":
            for k2 in range(R.randint(2, 7)):
                vx, vz = int(round(cx + R.uniform(-1, 1))), int(round(cz + R.uniform(-1, 1)))
                p = (vx, deck - int(under) - k2, vz)
                if p not in w.b and p[1] > at(vx, vz): put(*p, "cherry_leaves" if R.random() < sakura else "vine", shape="shut_s")
    return samples


# (the sakura arena is built right after the pillars)
def link(a, b, rise, sak=0.0, style="rock"):
    (ax, az, ay, _, art, _), (bx, bz, by, _, brt, _) = a, b
    d = math.hypot(bx - ax, bz - az); ux, uz = (bx - ax) / d, (bz - az) / d
    return rock_bridge((ax + ux * art * .84, ay, az + uz * art * .84), (bx - ux * brt * .84, by, bz - uz * brt * .84), rise=rise, sakura=sak, style=style), (ux, uz)



def torii_on(samples, d, every):
    for i2, (cx, cz, deck, u) in enumerate(samples):
        if .08 < u < .92 and i2 % every == 0:
            torii(cx, cz, deck, d[0], d[1], half=3, h=6)
def landing_torii(samples, d, at_start, half=3, h=11):
    """A torii on the flat top just before/after a bridge, never on the slope."""
    pts = samples if not at_start else samples[::-1]
    for (cx, cz, deck, u) in pts:
        if (u <= .01 and at_start) or (u >= .99 and not at_start):
            torii(cx + (-d[0] if at_start else d[0]) * 3, cz + (-d[1] if at_start else d[1]) * 3, deck, d[0], d[1], half=half, h=h)
            return



def build(PILLARS, ex, ez):
    EDGE_Y = at(ex, ez)
    SAKURA = 2                                                          # the highest pillar is the sakura boss's arena
    SAK = [.3, .62, 1.0, .85]                                           # sakura share along the upper route
    for k2, (px, pz, ty, rb, rt, sd) in enumerate(PILLARS):
        mesa(px, pz, ty, rb, rt, sd, sakura=SAK[k2], clear=(.66 if k2 == SAKURA else 0))
    
    BR0 = rock_bridge((ex, EDGE_Y, ez - 2), (PILLARS[0][0], PILLARS[0][2], PILLARS[0][1] - PILLARS[0][4] * .84), rise=0, sakura=.15)
    BR1, D1 = link(PILLARS[0], PILLARS[1], 5, .45, style="root")              # a great root grown across
    BR2, D2 = link(PILLARS[1], PILLARS[2], 6, .85)
    BR3, D3 = link(PILLARS[2], PILLARS[3], 7, .9, style="rope")              # a rope bridge, the last crossing
    for (cx_, cz_, dk_, u_) in BR3:                                    # close any diagonal-only seam between the rope deck and the tops
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x_, z_ = int(round(cx_)) + dx, int(round(cz_)) + dz
                if UPPER[z_, x_] < 0:
                    put(x_, dk_, z_, "spruce_planks", shape="slab_t"); UPPER[z_, x_] = dk_
    # the sando: torii appear along the route and grow denser toward the boss (one, then a few, then a tunnel of them)
    def torii_on(samples, d, every):
        for i2, (cx, cz, deck, u) in enumerate(samples):
            if .08 < u < .92 and i2 % every == 0:
                torii(cx, cz, deck, d[0], d[1], half=3, h=6)
    def landing_torii(samples, d, at_start, half=3, h=11):
        """A torii on the flat top just before/after a bridge, never on the slope."""
        pts = samples if not at_start else samples[::-1]
        for (cx, cz, deck, u) in pts:
            if (u <= .01 and at_start) or (u >= .99 and not at_start):
                torii(cx + (-d[0] if at_start else d[0]) * 3, cz + (-d[1] if at_start else d[1]) * 3, deck, d[0], d[1], half=half, h=h)
                return
    
    
    landing_torii(BR1, D1, at_start=False)                              # arriving on pillar 2
    pass                                                                 # (second torii in a row removed)
    P0x, P0z, P0y, _, P0r, _ = PILLARS[0]
    for (dx, dz) in ((0, -int(P0r * .6)),):                             # a single small torii marks the start of the sando on the first pillar
        torii(P0x + dx, P0z + dz + 6, P0y, 0, 1, half=3, h=10)
    for x in range(ex - 3, ex + 4):                                     # the plateau edge where the first bridge starts
        for z in range(ez - 4, ez + 1):
            UPPER[z, x] = max(UPPER[z, x], at(x, z))
    
    # a spring on the second pillar: a pool on top, spilling over the south rim (toward the road) as a waterfall into a plunge pool
    P1x, P1z, P1y, _, P1r, _ = PILLARS[1]
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            if dx * dx + dz * dz <= 22:
                put(P1x + 4 + dx, P1y, P1z + 4 + dz, "water_still", shape="water"); put(P1x + 4 + dx, P1y - 1, P1z + 4 + dz, "gravel")
                UPPER[P1z + 4 + dz, P1x + 4 + dx] = -1
    fz = P1z + 9
    while UPPER[fz + 1, P1x + 4] == P1y: fz += 1                        # walk the channel out to the actual rim
    for zz2 in range(P1z + 9, fz + 1):
        for dx in (3, 4, 5):
            put(P1x + dx, P1y, zz2, "water_still", shape="water"); UPPER[zz2, P1x + dx] = -1
    fz += 1
    gb = at(P1x + 4, fz + 3)
    for y in range(gb + 1, P1y + 1):                                    # the fall, three blocks wide, spray near the bottom
        for dx in (3, 4, 5):
            put(P1x + dx, y, fz, "water_still")
            if y < gb + 6: put(P1x + dx, y, fz + 1, "water_still", shape="water")
    for dx in range(-8, 9):                                             # plunge pool
        for dz in range(-8, 9):
            if dx * dx + dz * dz <= 50:
                x2, z2 = P1x + 4 + dx, fz + 3 + dz
                g2 = at(x2, z2)
                for y in range(g2 - 2, g2 + 2): w.b.pop((x2, y, z2), None)
                put(x2, g2 - 3, z2, "gravel"); put(x2, g2 - 2, z2, "water_still"); put(x2, g2 - 1, z2, "water_still", shape="water"); put(x2, g2 - 4, z2, "gravel"); put(x2, g2 - 5, z2, "dirt"); hi[z2, x2] = g2 - 1
                lake[z2, x2] = True
    fx = P1x + 4
    CONTENT.append((fx, fz, "falls"))
    
    # rewards on the tops: a lookout cairn, a rare herb patch by the spring, a treasure at the far end, a high camp
    for (px, pz, ty, rb, rt, sd), what in zip(PILLARS, ("cairn", "herb", "treasure", "camp")):
        if what == "cairn":
            pass                                                         # (a lone cairn with a lantern on top said nothing; removed)
        elif what == "herb":
            pass                                                         # (scattered single bushes removed)
        elif what == "treasure":
            pass                                                         # the sakura arena, built below
        else:                                                            # the far pillar: an old watchtower, fallen in, a fire someone still keeps
            wx, wz = px + 8, pz - 6
            for y in range(ty - 1, ty + 13):
                for dx in range(-5, 6):
                    for dz in range(-5, 6):
                        d = math.hypot(dx, dz)
                        if 3.6 < d <= 5:
                            ang = math.atan2(dz, dx)
                            lim = ty + 4 + int(8 * max(0, math.cos(ang - .8))) + int(3 * noise3(wx + dx, 0, wz + dz, 171, 2))
                            door = abs(dx) <= 1 and dz > 0 and y <= ty + 3
                            if y <= lim and not door:
                                put(wx + dx, y, wz + dz, R.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"]))
            for k in range(14):                                            # its fallen stones lie down the slope of the top
                a2 = R.uniform(-.6, .9); r2 = R.uniform(6, 12)
                x2, z2 = int(wx + math.cos(a2) * r2), int(wz + math.sin(a2) * r2)
                if UPPER[z2, x2] == ty and (x2, ty + 1, z2) not in w.b:
                    put(x2, ty + 1, z2, R.choice(["stone_bricks", "mossy_stone_bricks", "cobblestone"]), shape=R.choice(["full", "slab", "slab"]))
            put(wx, ty + 1, wz, "campfire_log_lit", shape="fire")
            for (dx, dz) in ((-2, 0), (2, 0)): put(wx + dx, ty + 1, wz + dz, "spruce_log", "spruce_log", shape="slab")
            cache(wx - 2, ty + 1, wz - 2, "見張り台の跡")
            LABELS_POI.append(((wx, wz), "柱の上の見張り台跡", (220, 210, 190)))
        CONTENT.append((px, pz, "pillar"))
    # the first pillar is splitting: a cleft runs down its south face, ferns and a crooked tree grow in it
    P0x_, P0z_, P0y_, P0rb_, P0r_, _ = PILLARS[0]
    for y in range(P0y_ - 26, P0y_ + 1):
        f = (P0y_ - y) / 26
        wdt = 1.6 * (1 - f) + .4
        for dx in range(-3, 4):
            for z in range(P0z_, P0z_ + int(P0r_ * 1.3)):
                off = math.sin((P0y_ - y) * .25) * 1.5
                if abs(dx - off) <= wdt and (P0x_ + dx, y, z) in w.b and y > hi[z, P0x_ + dx] + 1:
                    inner = (P0x_ + dx, y, z - 1) in w.b
                    if z > P0z_ + P0r_ * .75 - 6 * f:
                        w.b.pop((P0x_ + dx, y, z), None); UPPER[z, P0x_ + dx] = -1 if y == P0y_ else UPPER[z, P0x_ + dx]
    for y in range(P0y_ - 20, P0y_ - 2, 3):
        zz3 = max([z for z in range(P0z_, P0z_ + int(P0r_ * 1.3)) if (P0x_, y, z) in w.b] or [P0z_])
        put(P0x_ + R.choice((-1, 0, 1)), y, zz3 + 1, R.choice(["fern", "hanging_roots", "vine"]), shape="plant")
    def top_spawn(px, pz, ty, rad, count, colour, shape):
        k = tries = 0
        while k < count and tries < count * 30:
            tries += 1
            a = R.random() * math.tau; r = rad * math.sqrt(R.random())
            x, z = int(round(px + math.cos(a) * r)), int(round(pz + math.sin(a) * r))
            if UPPER[z, x] != ty or (x, ty + 1, z) in w.b and w.b[(x, ty + 1, z)][0] not in ("plant",): continue
            w.b.pop((x, ty + 1, z), None)
            if shape == "upright": put(x, ty + 1, z, colour, shape="npc_low"); put(x, ty + 2, z, colour, shape="npc_high")
            else: put(x, ty + 1, z, colour, shape=shape)
            k += 1
    
    
    def top_herbs(px, pz, ty, n):
        for _ in range(n):
            x, z = px + R.randint(-4, 4), pz + R.randint(-4, 4)
            if UPPER[z, x] == ty and (x, ty + 1, z) not in w.b:
                put(x, ty + 1, z, R.choice(["sweet_berry_bush_stage3", "firefly_bush", "fern", "azalea_leaves"]),
                    shape="slab" if "azalea" in w.b.get((x, ty + 1, z), ("", "", ""))[1] else "plant")
    
    
    def top_rocks(px, pz, ty, ore):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                for dy in range(1, 4):
                    if dx * dx + dz * dz + dy * dy * 1.5 <= 11 and R.random() < .85:
                        put(px + dx, ty + dy, pz + dz, R.choice(["stone", "andesite", "mossy_cobblestone"] + ([ore] if ore else [])))
    
    
    # content on each top: these are fields in the sky, with their own hunting and gathering
    TOPS = [  # sprites + herbs; beetles + sap; deer herd + iron; wolves (strong) + gold
        (PILLARS[0], [("lime_wool", "sprite", 10)], 3, None),
        (PILLARS[1], [("black_wool", "beetle", 8), ("brown_wool", "deer", 6)], 4, "iron_ore"),
        (PILLARS[2], [], 0, None),
        (PILLARS[3], [("red_mushroom_block", "upright", 7)], 3, "copper_ore"),
    ]
    for (px, pz, ty, rb, rt, sd), spawns, herbs, ore in []:          # (one-block herb sprinkles and random rock blobs on the tops removed)
        for (col, shp, n) in spawns:
            top_spawn(px, pz, ty, rt * .75, n, col, shp)
        for k2 in range(herbs):
            a = R.random() * math.tau; r = rt * .6 * math.sqrt(R.random())
            top_herbs(int(px + math.cos(a) * r), int(pz + math.sin(a) * r), ty, 14)
        if ore:
            a = R.random() * math.tau
            top_rocks(int(px + math.cos(a) * rt * .5), int(pz + math.sin(a) * rt * .5), ty, ore)
    
    
    
    # ---------------- world boss: the sakura on the highest pillar ----------------
    # arena rules (boss-arena design + Japanese arena references): one open, readable circle with nothing inside to snag on;
    # the boundary is obvious (raised stone floor, red tamagaki fence); one entrance under a great torii; the landmark
    # (the sacred sakura with its shimenawa) stands behind, outside the fight; shrine pavilions and lanterns sit outside the fence.
    SX_, SZ_, SY_, _, SR_, _ = PILLARS[SAKURA]
    BOSS = (SX_, SZ_)
    AR = int(SR_ * .45)
    ux2, uz2 = D2                                                       # the direction the visitor arrives from
    ent_a = math.atan2(-uz2, -ux2)
    FY = SY_ + 1                                                        # arena floor is one step up
    PREC = AR + 17
    for (x, y, z) in [p for p in w.b if abs(p[0] - SX_) <= PREC and abs(p[2] - SZ_) <= PREC and SY_ < p[1] < SY_ + 30]:
        if math.hypot(x - SX_, z - SZ_) <= PREC and w.b[(x, y, z)][1] in ("cherry_log", "cherry_log_top", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "cherry_leaves", "pink_wool", "oak_log", "oak_leaves", "oak_log_top"):
            w.b.pop((x, y, z), None)
    for (x, y, z) in [p for p in w.b if abs(p[0] - SX_) <= PREC and abs(p[2] - SZ_) <= PREC and p[1] == SY_ + 1]:
        if w.b[(x, y, z)][0] in ("carpet", "plant") and math.hypot(x - SX_, z - SZ_) <= PREC and UPPER[z, x] == SY_: w.b.pop((x, y, z), None)
    for (x, y, z) in [p for p in w.b if abs(p[0] - SX_) <= PREC and abs(p[2] - SZ_) <= PREC and SY_ - 3 <= p[1] <= SY_]:
        if w.b[(x, y, z)][1] in ("cherry_log", "cherry_log_top", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "oak_log", "oak_log_top") and math.hypot(x - SX_, z - SZ_) <= PREC:
            put(x, y, z, "grass_block_top" if y == SY_ else "dirt", "grass_block_side" if y == SY_ else "dirt")
    for dx in range(-AR - 4, AR + 5):
        for dz in range(-AR - 4, AR + 5):
            d = math.hypot(dx, dz)
            x, z = SX_ + dx, SZ_ + dz
            if UPPER[z, x] != SY_ or d > AR + 3: continue
            for y in range(SY_ + 1, SY_ + 12):
                v = w.b.get((x, y, z), ("", "", ""))
                if v[1] in ("cherry_log", "cherry_log_top", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "cherry_leaves", "pink_petals", "pink_wool") or v[0] in ("plant", "carpet"): w.b.pop((x, y, z), None)
            if d <= AR:
                ring = abs(d - (AR - 2.5)) < .55                                  # one ring near the edge
                ang = math.atan2(dz, dx)
                spoke = min(abs(((ang * 8 / math.tau) % 1) - .5), .5) > .47 and 8 < d < AR - 3  # eight thin radial lines
                petal = False                                                    # a five-petal sakura crest (sakura-mon) at the centre
                for pk in range(5):
                    pa = ent_a + pk / 5 * math.tau
                    qx, qz = dx - math.cos(pa) * 4.2, dz - math.sin(pa) * 4.2
                    along = qx * math.cos(pa) + qz * math.sin(pa); across = -qx * math.sin(pa) + qz * math.cos(pa)
                    notch = along > 1.6 and abs(across) < .7
                    if (along / 3.0) ** 2 + (across / 2.3) ** 2 <= 1 and not notch: petal = True
                if d < 2.6: mat = "chiseled_stone_bricks" if d < 1.2 else "polished_andesite"
                elif ring: mat = "polished_andesite"
                elif abs(d - AR * .6) < 2.2 + noise3(x, 0, z, 616, 3): mat = "cracked_stone_bricks" if _h(x, 0, z, 61) < .45 else "polished_andesite"   # worn where the fight circles
                else: mat = "stone_bricks" if noise3(x, 1, z, 617, 4) > .3 else "mossy_stone_bricks"
                put(x, FY, z, mat); put(x, SY_, z, "stone_bricks"); UPPER[z, x] = FY
                if _h(x, 2, z, 63) < .1: put(x, FY + 1, z, "pink_petals", shape="carpet")   # a few petals blown in
            elif d <= AR + 1.2:
                put(x, FY, z, "polished_andesite", shape="slab"); UPPER[z, x] = FY      # a half-step lip all round
            elif d <= AR + 3:
                put(x, SY_, z, "gravel" if _h(x, 3, z, 64) < .7 else "stone_bricks")    # raked gravel band
    for k2 in range(int(math.tau * (AR + 2))):                          # tamagaki: a low red fence, open at the entrance
        a = k2 / (AR + 2)
        if abs(math.atan2(math.sin(a - ent_a), math.cos(a - ent_a))) < .32: continue
        x, z = int(round(SX_ + math.cos(a) * (AR + 2))), int(round(SZ_ + math.sin(a) * (AR + 2)))
        gone = noise3(math.cos(a) * 4, 0, math.sin(a) * 4, 613, 1.2)            # stretches of the old fence have fallen
        if gone > .68: continue
        put(x, SY_ + 1, z, "red_concrete", shape="fence")
        if gone < .6: put(x, SY_ + 2, z, "red_concrete", shape="slab")
    gx0, gz0 = SX_ + math.cos(ent_a) * (AR + 2), SZ_ + math.sin(ent_a) * (AR + 2)
    torii(gx0, gz0, SY_, -math.cos(ent_a), -math.sin(ent_a), half=5, h=17)   # the great torii at the entrance
    for off in (.5, -.5, math.pi - .3, math.pi + .3):                      # lanterns only where something is: flanking the gate and the sacred tree
        a = ent_a + off
        stone_lantern(int(round(SX_ + math.cos(a) * (AR + 5))), int(round(SZ_ + math.sin(a) * (AR + 5))), SY_, tall=True)
    for off, kind, rr0 in ((.85, "chozuya", 2), (-.85, "open", 3), (2.3, "hall", 5), (-2.3, "hall", 3)):   # a main hall, a small shrine, an open hall, the basin
        a = ent_a + off
        px2, pz2 = int(round(SX_ + math.cos(a) * (AR + 11))), int(round(SZ_ + math.sin(a) * (AR + 11)))
        fc = (-math.cos(a), -math.sin(a)); fc = (int(round(fc[0])), 0) if abs(fc[0]) > abs(fc[1]) else (0, int(round(fc[1])))
        if UPPER[pz2, px2] == SY_:
            pavilion(px2, pz2, SY_, r=rr0, kind=kind, face=fc)
            if rr0 < 5: continue
            for layer in range(2):                                           # a second, smaller roof floating above the first
                rr = 2 - layer
                for dx in range(-rr, rr + 1):
                    for dz in range(-rr, rr + 1):
                        put(px2 + dx, SY_ + 10 + layer, pz2 + dz, "deepslate_tiles", shape="slab" if max(abs(dx), abs(dz)) == rr and layer == 0 else "full")
            for dx in range(-1, 2):                                          # the drum between the two roofs
                for dz in range(-1, 2):
                    put(px2 + dx, SY_ + 9, pz2 + dz, "dark_oak_planks" if (dx or dz) else "red_concrete")
            put(px2, SY_ + 12, pz2, "gold_block", shape="post")
    for off in (1.55, -1.55):                                            # reflecting ponds between the pavilions
        a = ent_a + off
        qx0, qz0 = SX_ + math.cos(a) * (AR + 10), SZ_ + math.sin(a) * (AR + 10)
        for dx in range(-7, 8):
            for dz in range(-7, 8):
                x, z = int(round(qx0)) + dx, int(round(qz0)) + dz
                d = math.hypot(dx / 1.3, dz)
                if UPPER[z, x] != SY_ or d > 5.2: continue
                if d <= 4.2:
                    put(x, SY_ - 2, z, "gravel"); put(x, SY_ - 1, z, "water_still"); put(x, SY_, z, "water_still", shape="water")
                    UPPER[z, x] = -1
                    if _h(x, 0, z, 71) < .12: put(x, SY_ + 1, z, "lily_pad", shape="carpet")
                else:
                    put(x, SY_, z, "polished_andesite" if _h(x, 1, z, 72) < .6 else "mossy_stone_bricks")
        stone_lantern(int(round(qx0)) + 5, int(round(qz0)) - 3, SY_)
    for k2 in range(8):                                                  # framing sakura on the precinct edge, kept out of the sightlines
        if k2 % 2: continue                                              # four, not a ring
        a = ent_a + .45 + k2 / 8 * math.tau
        rel = abs(math.atan2(math.sin(a - ent_a), math.cos(a - ent_a)))
        if rel < .6 or rel > math.pi - .55: continue
        x, z = int(round(SX_ + math.cos(a) * (AR + 16))), int(round(SZ_ + math.sin(a) * (AR + 16)))
        if UPPER[z, x] == SY_: sakura_tree(x, z, SY_, size=(1.0, 1.3, .7, 1.1)[k2 // 2])
    # the walk from the last bridge to the arena: a tunnel of torii lined with stone lanterns, a stone path
    startx, startz = BR2[-1][0], BR2[-1][1]
    for k2 in range(0, 40):
        for s2 in (-1, 0, 1):
            x, z = int(round(startx + ux2 * k2 - uz2 * s2)), int(round(startz + uz2 * k2 + ux2 * s2))
            if UPPER[z, x] == SY_ and math.hypot(x - SX_, z - SZ_) > AR + 1.5:
                put(x, SY_, z, ("stone_bricks", "polished_andesite", "mossy_stone_bricks", "stone_bricks")[int(noise3(x, 0, z, 614, 2.5) * 4) % 4] if s2 == 0 or _h(x, 0, z, 615) < .7 else "moss_block")
                for y in range(SY_ + 1, SY_ + 10):
                    if w.b.get((x, y, z), ("", "", ""))[1] in ("cherry_log", "cherry_wood", "dark_oak_wood", "mangrove_wood", "flowering_azalea_leaves", "cherry_leaves"): w.b.pop((x, y, z), None)
    for k2 in range(1, 9):
        cx, cz = startx + ux2 * (3 + k2 * 8), startz + uz2 * (3 + k2 * 8)
        if math.hypot(cx - SX_, cz - SZ_) < AR + 6: break
        torii(cx, cz, SY_ - 1, ux2, uz2, half=3, h=12)
        lx0, lz0 = cx + ux2 * 4, cz + uz2 * 4
        if math.hypot(lx0 - SX_, lz0 - SZ_) > AR + 6:
            for s2 in (-4, 4):
                stone_lantern(int(round(lx0 - uz2 * s2)), int(round(lz0 + ux2 * s2)), SY_)
    # the sacred sakura (goshinboku) behind the arena, opposite the entrance, wrapped in a shimenawa
    back = ent_a + math.pi
    GX_, GZ_ = int(round(SX_ + math.cos(back) * (AR + 15))), int(round(SZ_ + math.sin(back) * (AR + 15)))
    sakura_tree(GX_, GZ_, SY_, size=2.2)            # behind the ring, so the fight is under open sky
    for k2 in range(48):
        a = k2 / 48 * math.tau
        x, z = int(round(GX_ + math.cos(a) * 4.8)), int(round(GZ_ + math.sin(a) * 4.8))
        put(x, SY_ + 4 + (k2 % 3 == 0), z, "hay_block_top", "hay_block_side")
        if k2 % 6 == 0:
            x2, z2 = int(round(GX_ + math.cos(a) * 5.8)), int(round(GZ_ + math.sin(a) * 5.8))
            sh = "shut_e" if abs(math.cos(a)) > abs(math.sin(a)) and math.cos(a) > 0 else "shut_w" if abs(math.cos(a)) > abs(math.sin(a)) else "shut_s" if math.sin(a) > 0 else "shut_n"
            put(x2, SY_ + 3, z2, "white_wall_banner", shape=sh)
    stone_lantern(GX_ - 6, GZ_ + 6, SY_); stone_lantern(GX_ + 6, GZ_ + 6, SY_)
    put(SX_, FY + 1, SZ_, "pink_wool", shape="boss")                    # the sakura boss (stand-in)
    return dict(arena=(SX_, SZ_, FY), entrance=BR0)
