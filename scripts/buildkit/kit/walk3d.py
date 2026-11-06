"""ブロックの世界（vox.W）の上を歩いて、つながりを確かめる（Town を使わない地図、洞や多層の回廊のため）。11/06 夜。
立てる所：足元が支え（full・path・slab・stair）で、頭の2マスが通れる（空・草花・蔦・水・絨毯）。
となりへは、立つ高さの差が1以内なら歩ける（下ハーフは +0.5）。水に立つ所は数えておく。

  reach = walk(start, box)               # start=(x, y, z)：立つブロックの位置、box=(x0, x1, z0, z1, y0, y1)
  report(reach, {'名前': (x, y, z), ...})  # 名前の場所へ行けるかを表示
"""
from collections import deque
from vox import get

SUPPORT = ('full', 'path', 'slab', 'stair')
PASS = (None, 'cross', 'vine', 'water', 'carpet')

def _top(b):
    if b is None or b['s'] not in SUPPORT or 'leaves' in b.get('t', ''): return None
    if b['s'] == 'slab': return 1.0 if b.get('h') == 'top' else 0.5
    return 1.0

def _open(x, y, z):
    b = get(x, y, z)
    return b is None or b['s'] in PASS

def stand(x, y, z):
    """(x, y, z) のブロックの上に立てるなら、立つ高さ（小数）を返す。"""
    t = _top(get(x, y, z))
    if t is None or not _open(x, y + 1, z) or not _open(x, y + 2, z): return None
    if t == 0.5 and not _open(x, y + 1, z): return None
    return y + t

def walk(start, box, step=1.0):
    x0, x1, z0, z1, y0, y1 = box
    sx, sy, sz = start
    if stand(sx, sy, sz) is None: print('立てない出発点', start); return {}
    seen = {(sx, sy, sz): stand(sx, sy, sz)}; q = deque([(sx, sy, sz)])
    while q:
        x, y, z = q.popleft(); h = seen[(x, y, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (x0 <= nx <= x1 and z0 <= nz <= z1): continue
            for ny in (y + 1, y, y - 1, y - 2):
                if ny < y0 or ny > y1 or (nx, ny, nz) in seen: continue
                hh = stand(nx, ny, nz)
                if hh is None: continue
                if abs(hh - h) <= step: seen[(nx, ny, nz)] = hh; q.append((nx, ny, nz))
                break
    return seen

def report(reach, targets, near=2):
    cols = {}
    for (x, y, z) in reach: cols.setdefault((x, z), []).append(y)
    bad = []
    for k, (tx, ty, tz) in targets.items():
        ok = any(abs(y - ty) <= 2 for dx in range(-near, near + 1) for dz in range(-near, near + 1) for y in cols.get((tx + dx, tz + dz), ()))
        if not ok: bad.append(k)
    print('歩ける所', len(reach), '行けない場所', len(bad), bad)
    return bad
