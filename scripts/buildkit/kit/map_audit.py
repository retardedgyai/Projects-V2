#!/usr/bin/env python3
"""地図の点検（11/06、司令塔「町と社を rules に照らして点検し、直せるものは直す」）。towns/<名前>.py を走らせてから見る：
  1. 歩ける検査（escape_check）：入口から行ける所のうち、落ちたら戻れない所・地図の外へ出られる所 [rules/dungeon.md、藍 11/06「この隙間落ちちゃったら酷いことになる」]
  2. 浮いた部品：葉・草・灯り以外のブロックが、地面とつながらずに宙に浮いている塊 [build-structural-integrity]。木の枝（is_tree）は別に数える
  3. 暗すぎる所（11/08 夜）：入口から行ける床のうち、昼（ゲームは time 6000 で止めてある）でも足もとの明るさが DARK 以下の所。
     明るさはサーバー（Minestom の LightingChunk）と同じ決まりで近く計る（light() を見る）
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

DARK = 4                                                                          # 明るさ 0〜4：昼でも夜のように暗く、敵や道が見えない
EMIT = {'lantern': 15, 'soul_lantern': 10, 'copper_lantern': 15, 'torch': 14, 'wall_torch': 14, 'soul_torch': 10, 'soul_wall_torch': 10,
        'campfire': 15, 'soul_campfire': 10, 'glowstone': 15, 'sea_lantern': 15, 'shroomlight': 15, 'jack_o_lantern': 15, 'lava': 15,
        'ochre_froglight': 15, 'verdant_froglight': 15, 'pearlescent_froglight': 15, 'end_rod': 14, 'light': 15, 'fire': 15,
        'crying_obsidian': 10, 'glow_lichen': 7, 'magma_block': 3, 'redstone_torch': 7, 'redstone_wall_torch': 7}
def light(W):
    """明るさの箱を返す：(L[x, y, z], 原点)。W は vox の dict（見えない壁も入れて渡す）。
    サーバー（Minestom の LightingChunk）と同じ決まりで計る：空の光 15 は、柱でいちばん上の「光を止める物」（満ブロック・半ブロック・階段）か
    「光を散らす物」（葉・水・氷）の上まで。そこから下と横へは、1マスごとに 1 減る（葉の中も 1 ずつ）。満ブロックは通さず、半ブロックと階段は上下だけ止める。
    barrier・light・柵・灯り・草は光を通す。"""
    import numpy as np, mcworld
    ks = list(W); xs = [p[0] for p in ks]; ys = [p[1] for p in ks]; zs = [p[2] for p in ks]
    o = (min(xs) - 1, min(ys) - 1, min(zs) - 1); n = (max(xs) - o[0] + 2, max(ys) - o[1] + 4, max(zs) - o[2] + 2)
    full_ = np.zeros(n, bool); half_ = np.zeros(n, bool); diff_ = np.zeros(n, bool); em = np.zeros(n, np.int8)
    with contextlib.redirect_stdout(io.StringIO()):
        for (x, y, z), b in W.items():
            i = (x - o[0], y - o[1], z - o[2]); t = b.get('t', ''); st = mcworld.to_state(b); name = st[0] if st else t
            if t.endswith('_leaves') or b['s'] == 'water' or 'ice' in t: diff_[i] = True
            elif b['s'] == 'full' and not any(k in t for k in ('glass', 'barrier')) and name != 'light': full_[i] = True
            elif b['s'] in ('slab', 'stair'): half_[i] = True
            em[i] = b.get('lv', 15) if name == 'light' else EMIT.get(name, 0)
    low = {}                                                                     # 柱のいちばん下より下は石で埋まる（mcworld.add）
    for (x, y, z) in W: low[(x, z)] = min(low.get((x, z), y), y)
    for (x, z), y in low.items(): full_[x - o[0], :y - o[1], z - o[2]] = True
    stop = full_ | half_ | diff_
    clear = np.cumsum(stop[:, ::-1, :], axis=1)[:, ::-1, :] == 0                  # 上に光を止める・散らす物が何もない所：空の光 15
    L = np.maximum(np.where(clear, 15, 0), em).astype(np.int8)
    vblock = full_ | half_
    for _ in range(16):
        P = np.pad(L, 1, constant_values=15)                                     # 地図の外は空
        hz = np.max([P[2:, 1:-1, 1:-1], P[:-2, 1:-1, 1:-1], P[1:-1, 1:-1, 2:], P[1:-1, 1:-1, :-2]], axis=0)
        VB = np.pad(vblock, 1, constant_values=False)
        up = np.where(VB[1:-1, 2:, 1:-1], 0, P[1:-1, 2:, 1:-1]); dn = np.where(VB[1:-1, :-2, 1:-1], 0, P[1:-1, :-2, 1:-1])
        vt = np.where(half_, 0, np.maximum(up, dn))
        L2 = np.maximum(L, np.where(full_, 0, np.maximum(hz, vt) - 1)).astype(np.int8)
        if (L2 == L).all(): break
        L = L2
    return L, o

def dark(reach, extra=None, level=DARK):
    """入口から行ける床 (x, z, y) のうち、足もと（y+1）の明るさが level 以下の所：[(x, z, y, 明るさ)]。"""
    L, o = light({**vox.W, **extra} if extra else vox.W)
    out = []
    for (x, z, y) in reach:
        v = int(L[x - o[0], y + 1 - o[1], z - o[2]])
        if v <= level: out.append((x, z, y, v))
    return out

def light_up(start, bounds, bar, lv=10, level=DARK, rounds=8):
    """暗すぎる所の足もとに、見えない灯り（マイクラの light ブロック、明るさ lv）を bar に足す。ブロックの見た目も形も変えない。
    いちばん暗い所から、互いに lv-level マス以上離して置き、明るさを計り直す。置いた数と、残った暗い所を返す。"""
    n = 0
    for _ in range(rounds):
        reach = EC.check(start, bounds, bar)[0]
        dk = dark(reach, bar, level)
        if not dk: break
        picked = []
        for (x, z, y, v) in sorted(dk, key=lambda c: (c[3], c)):
            at = next((yy for yy in (y + 1, y + 2) if vox.W.get((x, yy, z)) is None and (x, yy, z) not in bar), None)   # 足もとに草や花があれば頭の高さ
            if at is None: continue
            if any(abs(x - a) + abs(z - b) + abs(y - c) < lv - level for a, b, c in picked): continue
            picked.append((x, z, y)); bar[(x, at, z)] = {'s': 'light', 't': 'light', 'lv': lv}; n += 1
        if not picked: break
    reach = EC.check(start, bounds, bar)[0]
    return n, dark(reach, bar, level)

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
    dk = dark(reach, BAR)
    print('暗すぎる所（明るさ %d 以下）' % DARK, len(dk), EC.clusters([(x, z, y) for x, z, y, v in dk])[:12] or 'なし')
    fl = floating({**vox.W})
    tr = [g for g in fl if is_tree(g, vox.W)]; fl = [g for g in fl if not is_tree(g, vox.W)]
    print('浮いた塊（木の枝を除く）', len(fl), '／ 木の枝', len(tr))
    for grp in sorted(fl, key=len, reverse=True)[:40]:
        cx = sum(p[0] for p in grp) // len(grp); cy = sum(p[1] for p in grp) // len(grp); cz = sum(p[2] for p in grp) // len(grp)
        kinds = sorted({vox.W[p].get('t') for p in grp})[:4]
        print('  ', len(grp), (cx, cy, cz), kinds)
