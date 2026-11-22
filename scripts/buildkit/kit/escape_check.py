#!/usr/bin/env python3
"""地図の「落ちたら戻れない所」と「外へ出られる所」を探す（藍 11/06「この隙間落ちちゃったら酷いことになる」「プレイヤーはどこにでもいけちゃう」）。
置いたブロック（vox.W）の上を、プレイヤーと同じ決まりで歩く：1段は跳んで上れる、下りはいくらでも落ちられる、同じ高さか下へは2〜3マスの隙間を跳び越せる。
柵・塀（高さ1.5）は上れない。葉の上は立てる（木を伝って崖を越えるのも見る）。
  reach(start) … 入口から行ける所すべて
  back        … そこから入口へ歩いて戻れる所
  罠 = reach - back、外 = reach のうち地図の端
使い方：import して check(start, bounds, extra) を呼ぶ（towns/<名前>.py の最後）。"""
from collections import deque, defaultdict
import vox

PASS = {None, 'carpet', 'cross', 'vine', 'lantern', 'chain', 'water', 'trap', 'light'}           # light：見えない灯り（体は通る）
NOSTAND = {'fence', 'wall', 'carpet', 'cross', 'vine', 'lantern', 'chain', 'door', 'water', 'trap', 'light'}
def _kind(b): return None if b is None else b['s']

def surfaces(W):
    """立てる所：(x,z) → [y, …]（y はブロックの上の面の高さ。下の半ブロック・階段は +0.5 を切り上げて y+1 とせず y として扱う）。"""
    cols = defaultdict(list)
    for (x, y, z), b in W.items(): cols[(x, z)].append(y)
    S = {}
    for (x, z), ys in cols.items():
        out = []
        for y in sorted(set(ys)):
            b = W.get((x, y, z)); k = _kind(b)
            a1, a2 = _kind(W.get((x, y + 1, z))), _kind(W.get((x, y + 2, z)))
            if a1 not in PASS or a2 not in PASS: continue
            if k == 'water':
                if a1 in (None, 'light'): out.append(y)                                       # 水面は泳いで浮ける
                continue
            if k in NOSTAND: continue
            if a1 == 'water': continue                                             # 水の底には立たない：泳いで水面へ浮く（落ちたら水面に止まる）
            out.append(y)
        if out: S[(x, z)] = out
    return S

def _top_below(S, x, z, y):
    """(x,z) で y 以下にある一番上の立てる面（落ちた先）。"""
    best = None
    for yy in S.get((x, z), ()):
        if yy <= y and (best is None or yy > best): best = yy
    return best

def _clear(W, x, z, y0, y1):
    """(x,z) の y0〜y1 に体を通せるか（跳び越す途中の柱・斜めの角）。"""
    return all(_kind(W.get((x, y, z))) in PASS for y in range(y0, y1 + 1))

def _moves(W, S, x, z, y):
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
        n = (x + dx, z + dz)
        if n not in S: continue
        diag = dx and dz
        up = [yy for yy in S[n] if y < yy <= y + 1]
        if up and (not diag or (_clear(W, x + dx, z, y + 1, y + 3) and _clear(W, x, z + dz, y + 1, y + 3))): yield (n[0], n[1], up[0])
        t = _top_below(S, n[0], n[1], y)
        if t is not None and _clear(W, n[0], n[1], max(t, y) + 1, y + 2) and (not diag or (_clear(W, x + dx, z, y + 1, y + 2) and _clear(W, x, z + dz, y + 1, y + 2))): yield (n[0], n[1], t)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):                               # 隙間を跳び越す（同じ高さか下、2〜3マス）。途中の柱が空いていること
        for k in (2, 3):
            if not all(_clear(W, x + dx * i, z + dz * i, y + 1, y + 3) for i in range(1, k)): break
            n = (x + dx * k, z + dz * k)
            if n in S:
                t = _top_below(S, n[0], n[1], y)
                if t is not None and t >= y - 3 and _clear(W, n[0], n[1], y + 1, y + 2): yield (n[0], n[1], t)

def check(start, bounds, extra=None):
    """extra：vox.W に足して見るブロック（見えない壁など、プレビューには出さないもの）。"""
    W = {**vox.W, **extra} if extra else vox.W
    S = surfaces(W)
    sx, sz = start
    sy = max(S[(sx, sz)])
    st = (sx, sz, sy)
    seen = {st}; q = deque([st]); rev = defaultdict(list)
    while q:
        c = q.popleft()
        for m in _moves(W, S, *c):
            rev[m].append(c)
            if m not in seen: seen.add(m); q.append(m)
    back = {st}; q = deque([st])
    while q:
        c = q.popleft()
        for p in rev[c]:
            if p not in back: back.add(p); q.append(p)
    x0, x1, z0, z1 = bounds
    trap = seen - back
    edge = {c for c in seen if c[0] <= x0 + 1 or c[0] >= x1 - 1 or c[1] <= z0 + 1 or c[1] >= z1 - 1}
    return seen, back, trap, edge

def clusters(cells, gap=4):
    """近い所を塊にまとめる：[(数, 中心x, 中心z, 最低y, 最高y)]。"""
    cells = list(cells); left = set((x, z) for x, z, _ in cells); ys = defaultdict(list)
    for x, z, y in cells: ys[(x, z)].append(y)
    out = []
    while left:
        s = left.pop(); grp = [s]; q = deque([s])
        while q:
            x, z = q.popleft()
            for dx in range(-gap, gap + 1):
                for dz in range(-gap, gap + 1):
                    n = (x + dx, z + dz)
                    if n in left: left.discard(n); grp.append(n); q.append(n)
        allys = [y for c in grp for y in ys[c]]
        out.append((len(grp), round(sum(c[0] for c in grp) / len(grp)), round(sum(c[1] for c in grp) / len(grp)), min(allys), max(allys)))
    return sorted(out, reverse=True)
