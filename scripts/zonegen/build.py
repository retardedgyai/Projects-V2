import sys, time, pickle, math
import numpy as np
from core import Zone
from blocks import Blocks
import validate, kit, setpieces, greattree
import zone1
t0 = time.time()
z = Zone(zone1.SPEC)
for st in ("macro", "lakes", "streams", "places", "obstacles", "roads", "solve", "detail", "quantize"):
    getattr(z, st)(); print(st, round(time.time() - t0, 1), flush=True)
bl = Blocks(z); bl.build()
import cliffs as _cl, random as _rnd
# rockmass trial (2026-10-20): reads as confetti, not as plates; off until reworked
# print("rockmass", _cl.rockmass(bl, z, 305, 385, 150, 200, _rnd.Random(7)), flush=True)
if True: print("blocks", len(bl.b), "cliffs", bl.cliff_stats, round(time.time() - t0, 1), flush=True)
c = kit.Ctx(z, bl)
setpieces.bind(c)
import places
places.bind(c)
for p in zone1.SPEC.get("pieces", []):
    x, zz = p["at"]
    if p["kind"] == "great_tree":
        c.reserve("大樹", x, zz, p["r"], allow_route=True)
        places.knoll(x, zz + 2, 24, 9)
        TG0 = c.at(x, zz)
        greattree.build(bl, c.put, c.at, z.B, c.route, x, zz, c.mark, seed=77)
        places.dungeon((x, zz), TG0)                                # carved after the tree, so no root plate blocks the mouth
    elif p["kind"] in ("rest", "fungal", "hollow", "wolf", "tower", "boulders"):
        fn = {"rest": places.rest_point, "fungal": places.fungal, "hollow": places.root_hollow, "wolf": places.wolf_den,
              "tower": places.tower, "boulders": places.boulders}[p["kind"]]
        fn(x, zz)
    elif p["kind"] == "aqueduct":
        rx = int(min(range(150, 280), key=lambda xx: z.road_d[zz, xx]))
        x0 = rx - (4 * 12 + 3 + 4)
        c.reserve("水道橋", rx, zz, 0, allow_route=True)
        setpieces.aqueduct(x0, zz, bays=7, collapsed=5, seed=21, root_from=(236, 150))
    elif p["kind"] == "fallen_trunk":
        setpieces.fallen_trunk(x, zz, .25, L=52, seed=31)
    elif p["kind"] == "stump":
        c.reserve("切り株", x, zz, p["r"])
        setpieces.stump(x, zz, D=19, seed=41)
import shrine
UPPER = np.full((z.B, z.B), -1, np.int32)
shrine.bind(c, UPPER)
ex, ez = zone1.SPEC["pillar_edge"]
EY = c.at(ex, ez)
PIL = [(px, pz, EY + rise, rb, rt, sd) for (px, pz, rise, rb, rt, sd) in zone1.SPEC["pillars"]]
for (px, pz, ty, rb, rt, sd) in PIL: c.reserve("岩の柱", px, pz, rb + 3)
ARENA = shrine.build(PIL, ex, ez)
print("pillars", round(time.time() - t0, 1), flush=True)
print("pieces", [(q["name"], q["clash"]) for q in c.pieces], round(time.time() - t0, 1), flush=True)
nt = kit.forests(c, zone1.SPEC); print("trees", nt, round(time.time() - t0, 1), flush=True)
c.gather = []
kit.ground_cover(c); print("cover", round(time.time() - t0, 1), flush=True)
for zc in range(z.B):                                           # solid ground under every column, whatever the pieces dug
    for xc in range(z.B):
        hc = int(z.hi[zc, xc])
        for y in range(hc - 4, hc):
            if (xc, y, zc) not in bl.b: bl.b[(xc, y, zc)] = ("full", "dirt", "dirt")
        if (xc, hc, zc) not in bl.b: bl.b[(xc, hc, zc)] = ("full", "coarse_dirt", "coarse_dirt")
for _ in range(3):                                              # tidy: loose single blocks left by diagonal tubes
    lone = [p for p, v in bl.b.items() if v[0] in ("full", "slab", "slab_t") and "leaves" not in v[1] and not any(
        (p[0] + a, p[1] + b2, p[2] + d) in bl.b for a, b2, d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    for p in lone: bl.b.pop(p, None)
res = validate.run(z, bl, zone1.SPEC["places"], upper=UPPER, upper_start=(ex, ez), upper_goal=ARENA["arena"])
ok = validate.report(res)
print("点検", "合格" if ok else "不合格")
pickle.dump((bl.b, z.hi, []), open("field500.pkl", "wb"))
np.save("upper.npy", UPPER)
pickle.dump(z, open("zone.pkl", "wb")); pickle.dump(c.gather, open("gather.pkl", "wb"))
