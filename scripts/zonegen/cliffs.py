"""Cliff faces done the way terraformers do them (research-terrain.md B):
  - value follows form: darker where the face dips in, under a ledge and toward the foot; lighter on sun-facing bumps
  - a value-ordered palette, neighbours never skip a step; one block (stone) runs through every band
  - secondary blocks in small clusters (1-3) with gaps, not noise; faint tilted strata only as a nudge
  - grass > dirt > stone: a dirt lip under the rim; ledges carry grass and moss; moss only in grooves and under ledges
  - vines and drips hang from ledges; talus (gravel, cobble, fallen stone) gathers at the foot, grass growing through it
"""
import math
import numpy as np
from greattree import noise3, _h

PAL = ["deepslate", "cobbled_deepslate", "tuff", "cobblestone", "andesite", "stone", "andesite", "stone", "stone"]   # dark -> light
SECOND = {"calcite": ["stone", "diorite"], "deepslate": ["cobbled_deepslate"], "stone": ["andesite", "cobblestone"], "andesite": ["stone", "tuff"], "cobblestone": ["mossy_cobblestone", "andesite"],
          "tuff": ["cobbled_deepslate", "cobblestone"], "cobbled_deepslate": ["tuff"]}


def fields(H):
    gy, gx = np.gradient(H.astype(np.float32))
    slope = np.hypot(gx, gy)
    expo = np.clip((-gx * .6 - gy * .8) / np.maximum(slope, 1e-3), -1, 1)
    p = np.pad(H, 2, mode="edge"); B = H.shape[0]
    avg = sum(p[2 + dz:2 + dz + B, 2 + dx:2 + dx + B] for dz in (-2, -1, 0, 1, 2) for dx in (-2, -1, 0, 1, 2)) / 25
    concav = np.clip((avg - H) / 2.5, -1, 1)          # >0: the face dips in here (a groove, a bay)
    return slope, expo, concav


LIT = [("stone", 60), ("andesite", 25), ("diorite", 10), ("stone", 5)]
SHADE = [("stone", 35), ("tuff", 35), ("cobbled_deepslate", 15), ("deepslate", 10), ("mossy_cobblestone", 5)]
DEEP = [("deepslate", 50), ("cobbled_deepslate", 25), ("tuff", 20), ("blackstone", 5)]
STEP = {"stone": "andesite", "andesite": "diorite", "diorite": "andesite", "tuff": "stone", "cobbled_deepslate": "tuff",
        "deepslate": "cobbled_deepslate", "mossy_cobblestone": "stone", "blackstone": "deepslate"}


def _plate(mix, x, y, z, seed):
    """One material per plate: chosen by slow (12-24 block) noise, stretched vertically, so a plate reads as one rock."""
    tot = sum(w for _, w in mix)
    v = noise3(x * .9, y * .45, z * .9, seed, 16)
    acc = 0
    for n, w in mix:
        acc += w / tot
        if v <= acc: return n
    return mix[-1][0]


def block(x, y, z, frac, expo, concav, depth=None):
    """frac 0 at the foot .. 1 at the rim. Value by role (research-texturing.md): lit plates ~L140, shaded ~L100,
    creases/undersides ~L80; within a plate at most ~15% of one neighbouring step, in small clusters."""
    s = expo * .3 + frac * .5 - .15 - max(0, concav) * 1.3 + (noise3(x, y * .5, z, 377, 10) - .5) * .3
    if depth is not None:                                            # each step's own face: lit at its lip, in shadow below it
        s += .35 if depth <= 1 else (-.25 if depth <= 3 else -.05)
    if s > .32: mix, seed = LIT, 501
    elif s > -.12: mix, seed = SHADE, 502
    else: mix, seed = DEEP, 503
    name = _plate(mix, x, y, z, seed)
    if _h(x // 2, y // 2, z // 2, 373) < .15: name = STEP.get(name, name)
    if mix is LIT and frac > .92 and _h(x, y, z, 378) < .05: name = "calcite"
    return name


def finish(bl, Z, slope, concav):
    """After the columns are filled: dirt lips, ledge turf, moss in grooves, vines and drips, talus at the foot."""
    b = bl.b; B = Z.B; hi = Z.hi; kind = Z.kind
    face = (slope > 1.25) & (kind == 0)
    p = np.pad(hi, 4, mode="edge")
    hmax = np.maximum.reduce([p[4 + dz:4 + dz + B, 4 + dx:4 + dx + B] for dz in range(-4, 5) for dx in range(-4, 5)])   # the face within 4 blocks
    n_lip = n_ledge = n_vine = n_talus = 0
    for z in range(1, B - 1):
        for x in range(1, B - 1):
            h = int(hi[z, x])
            if not face[z, x]:
                # rim: the first ground cell next to a face drop gets a dirt lip under its grass
                if kind[z, x] == 0 and any(face[z + dz, x + dx] and hi[z + dz, x + dx] < h - 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    n_lip += 1
                # talus: right below a tall face, broken stone gathers, grass and ferns grow through
                if kind[z, x] == 0 and hmax[z, x] - h >= 6 and slope[z, x] < 1.0:
                    r = noise3(x, 0, z, 382, 2.2)
                    if r > .45:
                        u = _h(x, 1, z, 383)
                        m = "gravel" if u < .4 else "cobblestone" if u < .65 else "andesite" if u < .8 else "stone" if u < .95 else "mossy_cobblestone"
                        b[(x, h, z)] = ("full", m, m)
                        if r > .62 and _h(x, 2, z, 384) < .45 and (x, h + 1, z) not in b:
                            m2 = ("stone", "cobblestone", "andesite")[int(_h(x, 3, z, 385) * 3) % 3]; b[(x, h + 1, z)] = ("slab", m2, m2)
                        elif _h(x, 4, z, 386) < .25 and (x, h + 1, z) not in b:
                            b[(x, h + 1, z)] = ("plant", "fern", "fern")
                        n_talus += 1
                continue
            # a face column: is this cell a ledge (a step's tread)? Ledges hold turf and moss
            lower = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if hi[z + dz, x + dx] < h - 1)
            higher = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if hi[z + dz, x + dx] > h)
            if higher and lower and noise3(x, 0, z, 386, 4) > .3:
                # the tread keeps its rock; moss and turf sit on it as a carpet, so no strip of dirt shows on the face
                if (x, h + 1, z) not in b:
                    b[(x, h + 1, z)] = ("carpet", "moss_block", "moss_block")
                if (x, h + 2, z) not in b and _h(x, 5, z, 387) < .35 and False:
                    m2 = ("short_grass", "fern", "short_grass")[int(_h(x, 6, z, 388) * 3) % 3]; b[(x, h + 1, z)] = ("plant", m2, m2)
                n_ledge += 1
            # moss in grooves: a few blocks under a ledge where the face dips in
            if concav[z, x] > .25 and (x, z) not in getattr(bl, "carved", {}):
                for k in range(1, 4):
                    if (x, h - k, z) in b and b[(x, h - k, z)][0] == "full" and _h(x, h - k, z, 389) < .5 - k * .1:
                        b[(x, h - k, z)] = ("full", "moss_block", "moss_block")
            # vines and drips from south-facing lips (the side the low sun and the camera see)
            if hi[z + 1, x] < h - 2 and noise3(x, 7, z, 390, 6) > .72 and _h(x, 7, z, 392) < .6:
                L = 1 + int(_h(x, 8, z, 391) * min(7, h - hi[z + 1, x] - 1))
                for k in range(L):
                    q = (x, h - k, z + 1)
                    if q in b or h - k <= hi[z + 1, x]: break
                    b[q] = ("shut_s", "vine", "vine")
                n_vine += 1
    return dict(lips=n_lip, ledges=n_ledge, vines=n_vine, talus=n_talus)


def sculpt(bl, Z, R):
    """Make the faces three-dimensional, the way a builder works a face with brushes after the heightmap:
      - buttresses and boulders bulge out of the wall (ellipsoids, lighter where they catch light, moss on top)
      - narrow vertical cracks are cut back into the wall, darkest inside, with vines or lichen
      - the outer edge of ledges is broken with stairs and slabs so a step is never a clean box
    Only above head height at the foot, never over a route or water."""
    b = bl.b; B = Z.B; hi = Z.hi; kind = Z.kind
    route = (kind == 1) | (kind == 2) | (kind == 3) | (kind == 4) | (kind == 7)
    faces = []
    for z in range(2, B - 2):
        for x in range(2, B - 2):
            h = int(hi[z, x])
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                nh = int(hi[nz, nx])
                if h - nh >= 2 and Z.slope[z, x] > 1.0 and not route[nz, nx]:
                    faces.append((x, z, dx, dz, nh, h))
    n_b = n_c = n_e = 0
    for (x, z, dx, dz, nh, h) in faces:
        r = _h(x, nh, z, 401)
        if r < .04:                                                    # a buttress / boulder bulging out
            y0 = R.randint(nh + 1, max(nh + 1, h - 1))
            rx, ry, ro = R.uniform(1.8, 4.2), R.uniform(1.8, 4.5), R.uniform(1.0, 2.4)
            px, pz = -dz, dx                                           # along the wall
            for a in range(-4, 5):
                for v in range(-4, 5):
                    for o in range(0, 3):
                        q = (a / rx) ** 2 + (v / ry) ** 2 + (o / ro) ** 2
                        if q > 1 + (noise3(x + a, y0 + v, z + o, 402, 1.5) - .5) * .5: continue
                        X, Y, Zc = x + dx * (1 + o) + px * a, y0 + v, z + dz * (1 + o) + pz * a
                        if not (0 <= X < B and 0 <= Zc < B) or route[Zc, X] or Y <= int(hi[Zc, X]) + 2: continue
                        if (X, Y, Zc) in b: continue
                        top = (X, Y + 1, Zc) not in b and v >= ry * .5
                        b[(X, Y, Zc)] = ("full", "moss_block", "moss_block") if top and _h(X, Y, Zc, 403) < .5 else \
                            ("full",) + (face_block(X, Y, Zc, .6, .7, False, None),) * 2
            n_b += 1
        elif r < .1 and h - nh >= 2:                                   # a crack cut back into the wall
            px, pz = -dz, dx
            y1 = R.randint(nh - 2, max(nh - 2, h - 4)); y2 = min(h - 4, y1 + R.randint(3, 7))
            for y in range(y1, y2 + 1):
                for d in (0, 1):
                    q = (x - dx * d, y, z - dz * d)
                    if q in b and hi[q[2], q[0]] - 3 > y:
                        b[q] = ("full", "cobbled_deepslate", "cobbled_deepslate") if d == 1 else b.pop(q) and None
                        if d == 0: b.pop(q, None)
                if _h(x, y, z, 404) < .25: b[(x, y, z)] = ("plant", "glow_lichen", "glow_lichen") if (x, y, z) not in b else b[(x, y, z)]
            n_c += 1
    # ledge edges: stairs and slabs break the outer edge of every tread
    for (x, z, dx, dz, nh, h) in faces:
        if _h(x, h, z, 405) < .35 and (x, h + 1, z) in b and b[(x, h + 1, z)][0] == "carpet":
            m = b.get((x, h, z), ("", "stone", ""))[1]
            if m in ("stone", "andesite", "cobblestone", "tuff", "cobbled_deepslate", "deepslate", "calcite"):
                f = {(1, 0): "e", (-1, 0): "w", (0, 1): "s", (0, -1): "n"}[(dx, dz)]
                mm = m if m != "calcite" else "stone"; b[(x, h, z)] = ("stair_" + {"e": "w", "w": "e", "s": "n", "n": "s"}[f], mm, mm)
                b[(x, h + 1, z)] and b.pop((x, h + 1, z), None)
                n_e += 1
    return dict(buttress=n_b, cracks=n_c, edges=n_e)


def rockmass(bl, Z, x0, x1, z0, z1, R):
    """A cliff built as a builder would sculpt it: the face is a pile of big rock plates (rounded, tilted boxes of
    different sizes), each plate one family of stone so it reads as one rock, overlapping and stepping out so their
    undersides throw shadow. Tops carry turf and moss, undersides are the darkest stone, joints between plates are dark."""
    b = bl.b; hi = Z.hi; kind = Z.kind; B = Z.B
    route = (kind == 1) | (kind == 2) | (kind == 3) | (kind == 4) | (kind == 7)
    # the face line: for each x, the z where the ground drops most toward the south
    line = []
    for x in range(x0, x1 + 1):
        col = hi[z0:z1, x].astype(int)
        d = np.diff(col)
        i = int(np.argmin(d))
        if d[i] > -3: continue
        zf = z0 + i
        top, low = int(hi[zf, x]), int(hi[min(B - 1, zf + 6), x])
        line.append((x, zf, top, low))
    FAM = [("stone", "andesite", "cobblestone"), ("andesite", "stone", "tuff"), ("tuff", "cobbled_deepslate", "andesite"),
           ("stone", "stone", "andesite"), ("cobblestone", "andesite", "mossy_cobblestone")]
    plates = []
    i = 0
    while i < len(line):
        x, zf, top, low = line[i]
        H = top - low
        w = R.randint(5, 11)
        y = low
        while y < top - 1:                                             # plates stacked up the face, each set a little back
            h = R.randint(3, 6)
            plates.append(dict(c=(x + w / 2 + R.uniform(-1, 1), y + h / 2, zf + R.uniform(-.5, 2.5) - (y - low) / max(1, H) * 3),
                               hs=(w / 2 + R.uniform(0, 1.5), h / 2 + .3, R.uniform(1.5, 3.2)), r=R.uniform(.6, 1.4),
                               yaw=R.uniform(-.25, .25), tilt=R.uniform(-.18, .18), fam=R.choice(FAM)))
            y += h - R.randint(0, 1)
        i += max(1, int(w * R.uniform(.6, .9)))
    filled = {}
    for k, p in enumerate(plates):
        cx, cy, cz = p["c"]; ax, ay, az = p["hs"]; r = p["r"]
        cyaw, syaw = math.cos(p["yaw"]), math.sin(p["yaw"]); ct, st = math.cos(p["tilt"]), math.sin(p["tilt"])
        R_ = int(max(ax, ay, az) + r + 2)
        for X in range(int(cx) - R_, int(cx) + R_ + 1):
            for Y in range(int(cy) - R_, int(cy) + R_ + 1):
                for Zc in range(int(cz) - R_, int(cz) + R_ + 1):
                    if not (0 <= X < B and 0 <= Zc < B) or route[Zc, X]: continue
                    px, py, pz = X - cx, Y - cy, Zc - cz
                    px, pz = px * cyaw - pz * syaw, px * syaw + pz * cyaw         # yaw
                    py, px = py * ct - px * st, py * st + px * ct                 # tilt (bedding dips along the face)
                    qx, qy, qz = abs(px) - ax + r, abs(py) - ay + r, abs(pz) - az + r
                    d = math.sqrt(max(qx, 0) ** 2 + max(qy, 0) ** 2 + max(qz, 0) ** 2) + min(max(qx, qy, qz), 0) - r
                    d += (noise3(X, Y, Zc, 411 + k, 2.0) - .5) * .9
                    if d < 0 and Y > hi[Zc, X] - 4:
                        filled[(X, Y, Zc)] = k
    for (X, Y, Zc), k in filled.items():
        fam = plates[k]["fam"]
        above = (X, Y + 1, Zc) in filled or ((X, Y + 1, Zc) in b and b[(X, Y + 1, Zc)][0] == "full")
        below = (X, Y - 1, Zc) in filled or (X, Y - 1, Zc) in b
        joint = any(filled.get((X + a, Y + c, Zc + e), k) != k for a, c, e in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
        if not above and Y >= hi[Zc, X] - 1 and _h(X, Y, Zc, 412) < .7:
            m = "moss_block" if _h(X, Y, Zc, 413) < .55 else "grass_block_top"
        elif not below:
            m = "cobbled_deepslate" if _h(X, Y, Zc, 414) < .6 else "deepslate"
        elif joint:
            m = "tuff" if _h(X, Y, Zc, 415) < .5 else "cobbled_deepslate"
        else:
            v = noise3(X, Y * .5, Zc, 416 + k, 2.5)
            m = fam[0] if v < .55 else fam[1] if v < .85 else fam[2]
        b[(X, Y, Zc)] = ("full", m, "grass_block_side" if m == "grass_block_top" else m)
        if m in ("moss_block", "grass_block_top") and (X, Y + 1, Zc) not in b and _h(X, Y, Zc, 417) < .3:
            b[(X, Y + 1, Zc)] = ("plant", "fern" if _h(X, Y, Zc, 418) < .5 else "short_grass", "fern")
    # hanging roots and vines from the deepest undersides
    for (X, Y, Zc), k in filled.items():
        if (X, Y - 1, Zc) not in b and _h(X, Y, Zc, 419) < .12:
            for j in range(1, 2 + int(_h(X, Y, Zc, 420) * 5)):
                q = (X, Y - j, Zc)
                if q in b or Y - j <= hi[Zc, X]: break
                b[q] = ("plant", "hanging_roots" if j < 3 else "vine", "vine")
    return dict(plates=len(plates), blocks=len(filled))


def bed_of(x, y, z):
    """Strata: beds 4-8 blocks thick, boundaries wander +-1.5 and dip gently, so bed lines run across the face but never level."""
    yy = y + (noise3(x, 0, z, 431, 22) - .5) * 3 + (x * .04 + z * .02)
    i = 0; top = 40.0
    while top <= yy:
        top += 4 + _h(i, 0, 0, 432) * 4; i += 1
    return i, yy - (top - (4 + _h(i - 1, 0, 0, 432) * 4) if i else 0)   # bed index, height above the bed's base


def warp(Z, slope):
    """The face as a builder carves it, not as a staircase: the heightmap is sampled at a point pushed uphill or downhill by
    a 3D field, so the wall stands vertical and its line moves in and out with height:
      - ribs: tall vertical buttresses and gullies (noise stretched 4x in y)
      - beds: each stratum steps out or in by up to a block; the base of every bed is eaten back (a shadow groove)
      - the rim stays put: the top 4 blocks of every column are solid, so nothing can fall through.
    Returns {(x, z): (top, solid y list)} for the cliff columns."""
    B = Z.B; hi = Z.hi.astype(np.float32); kind = Z.kind
    k = np.ones(5, np.float32) / 5
    sm = np.apply_along_axis(lambda r: np.convolve(np.pad(r, 2, mode="edge"), k, "valid"), 0, hi)
    sm = np.apply_along_axis(lambda r: np.convolve(np.pad(r, 2, mode="edge"), k, "valid"), 1, sm)
    gz, gx = np.gradient(sm); gl = np.maximum(np.hypot(gx, gz), 1e-3)
    ux, uz = gx / gl, gz / gl                                          # uphill
    face = (slope > 1.0) & (kind == 0)
    busy = kind != 0
    near = np.zeros_like(busy); fz = np.zeros_like(face)
    for dz in range(-3, 4):
        for dx in range(-3, 4):
            near |= np.roll(np.roll(busy, dz, 0), dx, 1)
            if abs(dx) <= 2 and abs(dz) <= 2: fz |= np.roll(np.roll(face, dz, 0), dx, 1)
    zone = fz & ~near & (gl > .6)
    zone[:4] = zone[-4:] = False; zone[:, :4] = zone[:, -4:] = False

    def H(px, pz):
        x0, z0 = int(math.floor(px)), int(math.floor(pz)); fx, fz_ = px - x0, pz - z0
        x0 = min(max(x0, 0), B - 2); z0 = min(max(z0, 0), B - 2)
        return (hi[z0, x0] * (1 - fx) * (1 - fz_) + hi[z0, x0 + 1] * fx * (1 - fz_) + hi[z0 + 1, x0] * (1 - fx) * fz_ + hi[z0 + 1, x0 + 1] * fx * fz_)

    out = {}
    for z, x in zip(*np.nonzero(zone)):
        h0 = int(hi[z, x]); lo = int(min(hi[max(0, z - 3):z + 4, max(0, x - 3):x + 4].min(), h0)) - 3
        top_max = int(hi[max(0, z - 3):z + 4, max(0, x - 3):x + 4].max())
        ys = []
        for y in range(lo, top_max + 1):
            bed, above = bed_of(x, y, z)
            d = (noise3(x * 1.0, y * .2, z * 1.0, 433, 8) - .5) * 5.0 + (noise3(x, y * .5, z, 442, 3) - .5) * .9        # ribs and gullies
            d += (_h(bed, 0, 0, 434) - .5) * 1.8                           # each bed sits a little in or out
            if above < 1.2: d -= 1.1                                       # the eaten base of a bed: a groove of shadow
            if y <= H(x + .5, z + .5) + .0 and y < lo + 4: ys.append(y); continue
            if y <= H(x + .5 + ux[z, x] * d, z + .5 + uz[z, x] * d) - .5: ys.append(y)
        if not ys: continue
        t = max(ys)
        if t > h0 + 1: t = h0 + 1; ys = [y for y in ys if y <= t]          # never grow a column far above where it was
        for y in range(t - 3, t + 1):
            if y not in ys: ys.append(y)
        out[(int(x), int(z))] = (t, sorted(ys))
    return out


def face_block(x, y, z, expo, frac, under, depth):
    """Material of a carved face block: one material per bed (stone / andesite / tuff), shifted darker in shade, at the
    foot and under anything that overhangs; a neighbouring step in small clusters; light rock only at the very rim."""
    bed, above = bed_of(x, y, z)
    tone = _h(bed, 1, 0, 435)
    base = 2 if tone < .5 else (3 if tone < .8 else 1)                    # LADDER index: stone / andesite / tuff
    s = expo * .6 + (frac - .5) * 1.2 + (noise3(x, y * .4, z, 436, 12) - .5) * .6
    if s < -.35: base -= 1
    if s > .55 and base < 3: base += 1
    if above < 1.2: base -= 1                                              # groove row
    if under: base -= 2
    if depth is not None and depth <= 1 and not under: base += 1           # the lip of a ledge catches light
    base = min(max(base, 0), 4)
    name = LADDER[base]
    if _h(x // 2, y // 2, z // 2, 437) < .14: name = LADDER[min(4, max(0, base + (1 if _h(x // 2, y // 3, z // 2, 438) < .5 else -1)))]
    if name == "diorite" and _h(x // 2, y // 2, z // 2, 439) > .35: name = "andesite"
    return name


LADDER = ["cobbled_deepslate", "tuff", "stone", "andesite", "andesite"]
