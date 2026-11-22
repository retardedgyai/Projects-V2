"""野外の大きな木（rules/openworld.md 12〜16）。Wynncraft の草原の樫：冠は平たく広い（幅 12〜16、高さ 12〜18）、幹は 2×2〜3×3 で少し曲がり、
根元が広がり、太い腕が冠の下に見える。高さ 5〜6 の小さな木は使わない。

  field_oak(x, z, g, seed, size)  草原の樫。size 1.0 で冠の幅 約 13・高さ 約 14、1.5 で巨木（冠 約 20・高さ 約 22）
  pine(x, z, g, seed, h)          高いマツ：幹 2×2、下の 4 割は幹だけ、葉の層は下が広く上ほど小さい（Beehns and Apples）
  stump(x, z, g, seed)            切り株と倒木
どれも vox の座標（原点を寄せた後）で置く。
"""
import math, random
from vox import put, get, full, log, fence, cross, vine

def _leaf(x, y, z, t):
    if get(x, y, z) is None: put(x, y, z, full(t))

def _wood(x, y, z, t, axis='y'):
    c = get(x, y, z)
    if c is None or 'leaves' in c.get('t', '') or c.get('s') == 'cross': put(x, y, z, log(t, axis))

def _blob(cx, cy, cz, rx, ry, r, leaves, drip=0.18):
    """平たい葉の塊：上は丸く、下は平らに近い。下の面は暗い葉、上は明るい葉。縁から 1〜3 マス垂らす。"""
    dark, mid, light = leaves
    for dy in range(-int(ry * 0.6) - 1, int(ry) + 2):
        for dx in range(-int(rx) - 1, int(rx) + 2):
            for dz in range(-int(rx) - 1, int(rx) + 2):
                yy = dy / (ry if dy >= 0 else ry * 0.6)
                q = (dx / rx) ** 2 + (dz / rx) ** 2 + yy * yy
                if q > 1.0 + (r.random() - 0.5) * 0.28: continue
                t = dark if dy < 0 or q < 0.35 else (light if dy >= ry * 0.55 and r.random() < 0.55 else mid)
                _leaf(cx + dx, cy + dy, cz + dz, t)
                if dy <= -int(ry * 0.6) + 1 and q > 0.6 and r.random() < drip:                # 縁の垂れ
                    for k in range(1, r.randint(1, 3) + 1): _leaf(cx + dx, cy + dy - k, cz + dz, dark)

def field_oak(x, z, g, seed, size=1.0, logt='oak_log', leaves=('dark_oak_leaves', 'oak_leaves', 'oak_leaves')):
    r = random.Random(seed)
    tw = 2 if size < 1.3 else (3 if size < 1.9 else 5)           # 幹の太さ
    th = int(round(r.randint(6, 8) * size))                       # 冠の下までの高さ
    lean = r.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)]); ox = oz = 0
    cells = [(i, j) for i in range(tw) for j in range(tw) if not (tw >= 3 and (i, j) in ((0, 0), (tw - 1, tw - 1), (0, tw - 1), (tw - 1, 0)) and (tw == 5 or r.random() < 0.6))]
    for i, j in [(-1, 0), (tw, tw - 1), (0, -1), (tw - 1, tw), (-1, tw - 1), (tw, 0), (0, tw), (tw - 1, -1)]:        # 根元を広げる
        if r.random() < 0.75:
            for y in range(g + 1, g + 1 + r.randint(1, 2 + (tw >= 3))): _wood(x + i, y, z + j, logt)
    if tw == 5:                                                   # 巨木：根が外へ這い下りる
        for k in range(8):
            a = k * 0.785 + r.uniform(-0.3, 0.3); ln = r.randint(4, 8)
            for s in range(ln):
                rx_, rz_ = x + 2 + int(round(math.cos(a) * (3 + s))), z + 2 + int(round(math.sin(a) * (3 + s)))
                for y in range(g - 2, g + 1 + max(0, (ln - s) // 2)): _wood(rx_, y, rz_, logt, 'y' if s < 2 else ('x' if abs(math.cos(a)) > 0.7 else 'z'))
    for y in range(g + 1, g + th + 1):
        if y - g in (int(th * 0.45), int(th * 0.8)): ox += lean[0]; oz += lean[1]            # 少し曲げる
        for i, j in cells: _wood(x + ox + i, y, z + oz + j, logt)
    tx, tz, ty = x + ox + tw // 2, z + oz + tw // 2, g + th
    n = r.randint(4, 5) if size < 1.3 else r.randint(5, 7); a0 = r.random() * 6.283; tips = []
    for k in range(n):                                            # 太い腕：外へ出てから上を向く
        a = a0 + k * 6.283 / n + r.uniform(-0.35, 0.35); ln = r.uniform(3.5, 5.5) * size; rise = r.uniform(1.5, 3.5) * size
        px, py, pz = float(tx), float(ty - r.randint(0, 2)), float(tz); steps = int(ln * 1.3)
        for s in range(1, steps + 1):
            t = s / steps; px += math.cos(a) * ln / steps; pz += math.sin(a) * ln / steps; py += rise / steps * (0.5 + t)
            _wood(int(round(px)), int(round(py)), int(round(pz)), logt, 'x' if abs(math.cos(a)) > 0.7 else ('z' if abs(math.sin(a)) > 0.7 else 'y'))
        tips.append((int(round(px)), int(round(py)), int(round(pz))))
    for (bx, by, bz) in tips: _blob(bx, by + 1, bz, r.uniform(3.4, 4.4) * size ** 0.8, r.uniform(2.0, 2.7) * size ** 0.7, r, leaves)
    _blob(tx, ty + int(3 * size) + 1, tz, r.uniform(4.2, 5.2) * size ** 0.8, r.uniform(2.6, 3.2) * size ** 0.7, r, leaves, drip=0.05)   # 真ん中の一番高い塊
    return tips

def pine(x, z, g, seed, h=None, logt='spruce_log', leaves=('spruce_leaves', 'spruce_leaves', 'spruce_leaves')):
    r = random.Random(seed); h = h or r.randint(24, 32); tw = 2 if h >= 22 else 1
    for y in range(g + 1, g + h - 5):
        for i in range(tw):
            for j in range(tw): _wood(x + i, y, z + j, logt)
    y = g + int(h * r.uniform(0.38, 0.46)); rad = r.uniform(4.6, 5.4) if tw == 2 else r.uniform(3.2, 3.8)
    c = (tw - 1) / 2.0
    while y < g + h:
        rr = max(0.8, rad * (1 - (y - g - h * 0.4) / (h * 0.6)) + 0.4)
        for dx in range(-int(rr) - 1, int(rr) + 2 + tw):
            for dz in range(-int(rr) - 1, int(rr) + 2 + tw):
                q = math.hypot(dx - c, dz - c)
                if q <= rr + (r.random() - 0.5) * 0.9:
                    _leaf(x + dx, y, z + dz, leaves[1])
                    if q > rr - 1.2 and r.random() < 0.3: _leaf(x + dx, y - 1, z + dz, leaves[0])
                    if q < rr - 1.5: _leaf(x + dx, y + 1, z + dz, leaves[1])
        y += 3 if rr > 2.5 else 2
    for k in range(-5, 3): _leaf(x, g + h + k, z, leaves[1]); _leaf(x + tw - 1, g + h + min(k, 0), z + tw - 1, leaves[1])
    for _ in range(r.randint(2, 4)):                              # 幹に残る折れた枝
        yy = g + r.randint(4, max(5, int(h * 0.35))); d = r.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        put(x + (tw if d[0] > 0 else d[0]), yy, z + (tw if d[1] > 0 else d[1]), fence('spruce_planks'))

def stump(x, z, g, seed, logt='oak_log'):
    r = random.Random(seed)
    put(x, g + 1, z, log(logt)); ax = r.choice(['x', 'z']); n = r.randint(4, 7); s = r.choice([-1, 1])
    for k in range(2, 2 + n):
        xx, zz = (x + s * k, z + 1) if ax == 'x' else (x + 1, z + s * k)
        if get(xx, g + 1, zz) is None and get(xx, g, zz) is not None: put(xx, g + 1, zz, log(logt, ax))
    if r.random() < 0.6: put(x + (0 if ax == 'x' else -1), g + 1, z + (-1 if ax == 'x' else 0), cross(r.choice(['brown_mushroom', 'red_mushroom', 'fern'])))
