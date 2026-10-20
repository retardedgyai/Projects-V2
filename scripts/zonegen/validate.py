"""Automatic checks run before a zone may be exported. Each returns (id, severity, list of (x, y, z)).

Errors stop the build. They cover exactly the failures found in game: holes to the void, lakes at more than one level,
water open to the air at its own level, roads through water, walls or ledges beside and along roads, floating blocks,
places you cannot walk to.
"""
from collections import deque
import numpy as np

LEAVES = ("leaves",)


def solid(v):
    return v is not None and v[0] in ("full", "slab", "slab_t", "water") or (v is not None and v[0].startswith("stair"))


def run(zone, blocks, places=None, upper=None, upper_start=None, upper_goal=None):
    Z = zone; b = blocks.b; B = Z.B; hi = Z.hi; kind = Z.kind
    out = []
    route = (kind == 1) | (kind == 2)
    bridge = np.zeros((B, B), bool)
    for (x, z, d) in getattr(blocks, "bridge_cells", []): bridge[z, x] = True
    # surface y the player stands on (bridge deck counts)
    top = hi.copy()
    for (x, z, d) in getattr(blocks, "bridge_cells", []): top[z, x] = d

    # V-HOLE: every column has its top block and solid ground for 3 below it
    bad = []
    for z in range(B):
        for x in range(B):
            h = int(hi[z, x])
            if not all((x, y, z) in b for y in range(h - 3, h + 1)): bad.append((x, h, z))
    out.append(("穴（地面が抜けている）", "error", bad))

    # V-LEVEL: each lake has one water surface
    bad = []
    for lk in Z.spec.get("lakes", []):
        m = kind == 3
        lv = set(int(v) for v in Z.water[m])
        if len(lv) > 1: bad += [(int(x), int(Z.water[z, x]), int(z)) for z, x in zip(*np.nonzero(m))][:20]
    out.append(("池の水面が段になっている", "error", bad))

    # V-RIM: water at the lake level never touches air sideways
    bad = []
    for z, x in zip(*np.nonzero(kind == 3)):
        WL = int(Z.water[z, x])
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            X, Zc = x + dx, z + dz
            if (X, WL, Zc) not in b: bad.append((int(X), WL, int(Zc)))
    out.append(("水が横に漏れる（岸が低い）", "error", bad))

    # V-WET-ROUTE: no road or path cell is water, except at a bridge
    bad = [(int(x), int(hi[z, x]), int(z)) for z, x in zip(*np.nonzero(route & ((kind == 3) | (kind == 4)) & ~bridge))]
    out.append(("道が水の中を通っている", "error", bad))

    # V-STEP: along a route, neighbouring route cells differ by at most one block
    bad = []
    for z, x in zip(*np.nonzero(route | bridge)):
        for dx, dz in ((1, 0), (0, 1)):
            X, Zc = x + dx, z + dz
            if X < B and Zc < B and (route[Zc, X] or bridge[Zc, X]) and abs(int(top[z, x]) - int(top[Zc, X])) > 1:
                bad.append((int(x), int(top[z, x]), int(z)))
    out.append(("道の中に2段以上の段差", "error", bad))

    # V-CROSS: next to a route, the ground is within one block of the route (no bank walls)
    bad = []
    for z, x in zip(*np.nonzero(route)):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            X, Zc = x + dx, z + dz
            if 0 <= X < B and 0 <= Zc < B and not route[Zc, X] and kind[Zc, X] not in (3, 4, 6) and abs(int(hi[Zc, X]) - int(hi[z, x])) > 1:
                bad.append((int(x), int(hi[z, x]), int(z)))
    out.append(("道の横に壁（2段以上）", "error", bad))

    # V-FLOW: each stream's water never rises downstream
    bad = []
    for st in Z.spec.get("streams", []):
        from core import polyline
        pts = polyline(st["line"], 8); last = 999
        for x, z in pts:
            X, Zc = int(round(x)), int(round(z))
            if kind[Zc, X] == 4:
                w = int(Z.water[Zc, X])
                if w > last + 0: bad.append((X, w, Zc))
                last = min(last, w)
    out.append(("川が下流で上っている", "error", bad))

    # V-FLOAT: solid blocks with nothing under them and nothing beside them (single loose blocks)
    bad = []
    for (x, y, z), v in b.items():
        if v[0] != "full" or "leaves" in v[1]: continue
        if not any((x + a, y + c, z + d) in b for a, c, d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            bad.append((x, y, z))
    out.append(("浮いたブロック", "error", bad))

    # V-CONNECT: every place can be walked to from the first place (up 1, down 3)
    bad = []
    if places:
        start = places[0]["at"]
        seen = np.zeros((B, B), bool); q = deque([(start[1], start[0])]); seen[start[1], start[0]] = True
        walk = top
        while q:
            z, x = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                X, Zc = x + dx, z + dz
                if 0 <= X < B and 0 <= Zc < B and not seen[Zc, X] and kind[Zc, X] != 3:
                    d = int(walk[Zc, X]) - int(walk[z, x])
                    if d <= 1 and d >= -3: seen[Zc, X] = True; q.append((Zc, X))
        for p in places:
            x, z = p["at"]
            if not seen[min(B - 1, z), min(B - 1, x)]: bad.append((x, int(hi[min(B - 1, z), min(B - 1, x)]), z))
        Z.reach = seen
    out.append(("歩いて行けない場所", "error", bad))

    # V-UPPER: every pillar top and bridge deck can be walked to from the plateau edge, and the arena is among them
    bad = []
    if upper is not None:
        sx, sz = upper_start
        seen = np.zeros((B, B), bool); q = deque()
        for dz in range(-4, 5):
            for dx in range(-4, 5):
                if upper[sz + dz, sx + dx] >= 0: seen[sz + dz, sx + dx] = True; q.append((sz + dz, sx + dx))
        while q:
            z, x = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                X, Zc = x + dx, z + dz
                if 0 <= X < B and 0 <= Zc < B and not seen[Zc, X] and upper[Zc, X] >= 0 and abs(int(upper[Zc, X]) - int(upper[z, x])) <= 1:
                    seen[Zc, X] = True; q.append((Zc, X))
        miss = (upper >= 0) & ~seen
        bad = [(int(x), int(upper[z, x]), int(z)) for z, x in zip(*np.nonzero(miss))]
        if upper_goal and not seen[upper_goal[1], upper_goal[0]]: bad.insert(0, upper_goal)
    out.append(("柱の上・橋に歩いて行けない所", "error", bad))

    # warnings
    spikes = []
    p = np.pad(hi, 1, mode="edge")
    mn = np.minimum.reduce([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1]])
    mx = np.maximum.reduce([p[1:-1, 2:], p[1:-1, :-2], p[2:, 1:-1], p[:-2, 1:-1]])
    for z, x in zip(*np.nonzero((hi - mx >= 2) | (mn - hi >= 2))):
        spikes.append((int(x), int(hi[z, x]), int(z)))
    out.append(("1マスだけ飛び出し・へこみ", "warn", spikes))
    return out


def report(results):
    ok = True
    for name, sev, cells in results:
        mark = "OK " if not cells else ("NG " if sev == "error" else "注意")
        if cells and sev == "error": ok = False
        print(f"  {mark} {name}: {len(cells)}" + (f"  例 {cells[:4]}" if cells else ""))
    return ok
