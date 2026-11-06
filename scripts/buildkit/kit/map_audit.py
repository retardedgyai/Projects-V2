#!/usr/bin/env python3
"""地図の点検（11/06、司令塔「町と社を rules に照らして点検し、直せるものは直す」）。towns/<名前>.py を走らせてから見る：
  1. 歩ける検査（escape_check）：入口から行ける所のうち、落ちたら戻れない所・地図の外へ出られる所 [rules/dungeon.md、藍 11/06「この隙間落ちちゃったら酷いことになる」]
  2. 浮いた部品：葉・草・灯り以外のブロックが、地面とつながらずに宙に浮いている塊 [build-structural-integrity]
使い方：python3 kit/map_audit.py first_town -40,17,30   （3つ目は入口の x,z と、あれば見えない壁の変数名 BAR を自動で使う）"""
import contextlib, io, os, runpy, sys
from collections import deque
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import vox, escape_check as EC

SOFT = ('cross', 'carpet', 'lantern', 'chain', 'vine', 'water', 'door', 'trap', 'pot')
def floating(W, min_size=1):
    """6方向でつながる固いブロックの塊のうち、いちばん下が地面（その柱のいちばん下の層）に届かないもの。葉は木の一部なので数えない。"""
    solid = {p for p, b in W.items() if b['s'] not in SOFT and not b.get('t', '').endswith('_leaves') and b.get('t') != 'barrier'}
    low = {}
    for (x, y, z) in W: low[(x, z)] = min(low.get((x, z), y), y)
    seen, out = set(), []
    for p in solid:
        if p in seen: continue
        grp = [p]; seen.add(p); q = deque([p]); grounded = False
        while q:
            x, y, z = q.popleft()
            if y <= low[(x, z)] + 0: grounded = True
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + d[0], y + d[1], z + d[2])
                if n in solid and n not in seen: seen.add(n); grp.append(n); q.append(n)
            if len(grp) > 400000: grounded = True; break
        if not grounded and len(grp) >= min_size: out.append(grp)
    return out

def is_tree(grp, W):
    """木の枝や葉の上の部品（原木・板の柵・階段・苔）だけの塊。"""
    return all(any(k in W[p].get('t', '') for k in ('_log', 'cherry_planks', 'spruce_planks', 'dark_oak_planks', 'oak_planks', 'birch_planks', 'moss_block')) and
               W[p]['s'] in ('full', 'fence', 'stair', 'slab') for p in grp) and any('_log' in W[p].get('t', '') for p in grp)

def close_gaps(maxgap=3):
    """浮いた塊（木の枝を除く）の下の、maxgap マス以下の隙間を、真下の固いブロックと同じもので埋めて地面へつなぐ。埋めた数を返す。"""
    n = 0
    for grp in floating(vox.W):
        if is_tree(grp, vox.W): continue
        g = set(grp)
        for (x, y, z) in grp:
            if (x, y - 1, z) in g or vox.W.get((x, y - 1, z)) is not None: continue
            for d in range(2, maxgap + 2):
                b = vox.W.get((x, y - d, z))
                if b is None: continue
                if b['s'] in SOFT or b.get('t', '').endswith('_leaves'): break
                for k in range(1, d): vox.W[(x, y - k, z)] = dict(b); n += 1
                break
    return n

def edge_barrier(x0, x1, z0, z1, ring=2, height=30):
    """地図の外周 ring 列に、いちばん上のブロックの上から height 段の見えない壁（町は自由に歩けるので、外へ落ちないように端だけ閉じる）。"""
    top, low = {}, {}
    for (x, y, z), b in vox.W.items():
        top[(x, z)] = max(top.get((x, z), -99), y); low[(x, z)] = min(low.get((x, z), 999), y)
    bar = {}
    for (x, z), t in top.items():
        if x < x0 + ring or x > x1 - ring or z < z0 + ring or z > z1 - ring:
            for y in range(low[(x, z)], t + height + 1):                         # いちばん下から上まで、空いている所を全部（葉の下・枝の下も）
                b = vox.W.get((x, y, z))
                if b is None or b['s'] in SOFT[:5]: bar[(x, y, z)] = {'s': 'full', 't': 'barrier'}
    return bar

def fill_traps(start, bounds, bar, rounds=4):
    """跳び込むと上がれない穴の口に見えない壁を積み、もう一度歩く（散り桜の社と同じやり方）。残った罠と外への漏れを返す。"""
    for _ in range(rounds):
        reach, back, traps, edge = EC.check(start, bounds, bar)
        if not traps: break
        tops = {}
        for (x, z, y) in back: tops[(x, z)] = max(tops.get((x, z), -99), y)
        for (x, z, y) in traps:
            hi = max([tops.get((x + dx, z + dz), -99) for dx in range(-3, 4) for dz in range(-3, 4)] + [y]) + 6
            for yy in range(y + 1, hi + 1):
                b = vox.W.get((x, yy, z))
                if b is None or b['s'] in SOFT[:5]: bar[(x, yy, z)] = {'s': 'full', 't': 'barrier'}
    return traps, edge

if __name__ == '__main__':
    name = sys.argv[1]; sx, sz = map(int, sys.argv[2].split(','))
    with contextlib.redirect_stdout(io.StringIO()):
        g = runpy.run_path(os.path.join(HERE, '..', 'towns', name + '.py'), run_name='__main__')
    BAR = g.get('BAR', {})
    xs = [x for (x, y, z) in vox.W]; zs = [z for (x, y, z) in vox.W]
    bounds = (min(xs), max(xs), min(zs), max(zs))
    reach, back, traps, edge = EC.check((sx, sz), bounds, BAR)
    print('範囲', bounds, '行ける所', len(reach))
    print('落ちたら戻れない所', EC.clusters(traps)[:12] or 'なし')
    print('地図の外へ出られる所', EC.clusters(edge)[:6] or 'なし')
    fl = floating({**vox.W})
    print('浮いた塊', len(fl))
    for grp in sorted(fl, key=len, reverse=True)[:25]:
        cx = sum(p[0] for p in grp) // len(grp); cy = sum(p[1] for p in grp) // len(grp); cz = sum(p[2] for p in grp) // len(grp)
        kinds = sorted({vox.W[p].get('t') for p in grp})[:4]
        print('  ', len(grp), (cx, cy, cz), kinds)
