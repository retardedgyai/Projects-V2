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


def block(x, y, z, frac, expo, concav):
    """frac 0 at the foot .. 1 at the rim."""
    tilt = (y + x * .12 - z * .05 + (noise3(x, 0, z, 371, 9) - .5) * 3)
    stra = ((int(tilt // 3) % 3) - 1) * .05                         # faint beds, a nudge only
    t = .0 + frac * .62 + expo * .2 - max(0, concav) * .5 + min(0, concav) * -.15 + stra
    t += (noise3(x, y * .6, z, 372, 2.2) - .5) * .2 + (noise3(x, y, z, 376, 5) - .5) * .18
    i = int(max(0, min(len(PAL) - 1, t * len(PAL))))
    name = PAL[i]
    # clusters of a neighbouring value, 1-3 blocks, with gaps
    if _h(x // 2, y // 2, z // 2, 373) < .22 and _h(x, y, z, 374) < .6:
        name = SECOND.get(name, [name])[int(_h(x // 2, y // 2, z // 2, 375) * 2) % len(SECOND.get(name, [name]))]
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
                        m = ("gravel", "andesite", "cobblestone", "stone", "mossy_cobblestone")[int(_h(x, 1, z, 383) * 5) % 5]
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
            if concav[z, x] > .25:
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
        if r < .09:                                                    # a buttress / boulder bulging out
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
                            ("full",) + (block(X, Y, Zc, .7, .6, -.3),) * 2
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
