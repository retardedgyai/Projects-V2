#!/usr/bin/env python3
"""沈みの井戸（11/06 夜、インスタンスダンジョン）。決まりは rules/sunken.md（131〜145）。
手本：hagesan「凝灰岩の遺跡」（CtB-xPjnHc0）の逆さの円錐の回廊、ethanOG「水中のギリシャ神殿」（1Wgpi-OMUSo）の白い列柱。

形：海辺の草地に開いた丸い井戸（壁の内径 36）。暗い深層岩の壁の中に、明るい凝灰岩の回廊を3層、上ほど4マスずつ広げて重ねる [131・132]。
各層は、縁の開いた所（空が見える）→ 上の層を支える柱の列とアーチ → 屋根の下の回廊、の3帯。柱は層ごとに互い違い [135・136]。
上の縁と各層の縁から細い滝が下の層の溝へ落ち、底では輪の溝と、回廊の下の浅い池になる [139〜141]。戦う床は乾かす [140]。

並び（一人称の近接で戦う）：
  北の白い列柱の祠（入口）[137] → 水路のある石段の切り通し [145] → 第三回廊（北から入り、南の階段へ。1波）
  → 第二回廊（南から北へ。1波）→ 第一回廊（北から南へ。1波）→ 底の祭場（半径18の乾いた円、ボス）。
  階段は壁ぞいの弧で、層の床に穴を開けて下りる。縁には跳び越せない塀（凝灰岩レンガの壁）を回し、落ちて先へ飛ばないようにする。
実行：python3 towns/sunken_temple.py（out/sunken_temple/）。
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import vox
from vox import put, get, full, log, stair, slab, fence, cross, vine, water, vnoise, _h, LANTERN, grass
from town import save
import trees as TR
import walk3d as WK

def wall(t): return {'s': 'wall', 't': t}
HANG = {'s': 'lantern', 't': 'lantern', 'hang': True}
SOUL = {'s': 'lantern', 't': 'soul_lantern', 'hang': True}
D = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}
def face_of(dx, dz):
    best = max(D, key=lambda k: D[k][0] * dx + D[k][1] * dz); return best

# ================================================================ 寸法
RW = 36                       # 壁の内側の半径（ここまでが中）
WT = 3                        # 壁の厚さ
LV = 11                       # 層の高さ
R = [None, 20, 24, 28, 32]    # 層 k の縁の半径（k=4 は地上の縁）。上ほど4ずつ広い [132]
TOP = 4 * LV                  # 地上の高さ 44
def dist(x, z): return math.hypot(x, z)
def ang(x, z): return math.atan2(z, x)
rng = random.Random(7)

# 材料 [134・142]
def floor_mat(x, z):
    r = _h(x, z, 3); n = vnoise(x, z, 5, 4)
    if n > 0.78: return full('moss_block')
    if r < 0.42: return full('tuff_bricks')
    if r < 0.66: return full('tuff')
    if r < 0.84: return full('cobblestone')
    if r < 0.92: return full('polished_tuff')
    return full('gravel')
def wall_mat(x, y, z):
    a = ang(x, z); col = int(round(a * RW))                                       # 縦の縞：3列ごとにタイル [131]
    r = _h(x * 7 + y, z, 11)
    if col % 3 == 0: return full('deepslate_tiles' if r > 0.12 else 'cracked_deepslate_tiles')
    return full('polished_deepslate' if r > 0.08 else 'deepslate_bricks')

# ================================================================ 1. 地上：草地と浅い潟（東から南）、井戸の縁は y=44
SPILL = [0.15, 0.85, 1.55]                                                          # 潟から縁へ流れる細い水路の角度（東〜南東）。縁で幅1〜2の滝になる [139]
def spill(x, z):
    a = ang(x, z); d = dist(x, z)
    return R[4] - 0.5 <= d < 47 and any(abs(math.sin(a - s)) * d < (1.0 if i != 1 else 0.6) and math.cos(a - s) > 0 for i, s in enumerate(SPILL))
def lagoon(x, z):
    a = ang(x, z); d = dist(x, z)
    return (d > 44 and (vnoise(x, z, 18, 21) + 0.35 * math.cos(a - 0.6)) > 0.74 and d < 66) or spill(x, z)
def ground_h(x, z):
    d = dist(x, z)
    if d < 46: return TOP
    return TOP + int(round((vnoise(x, z, 20, 22) - 0.4) * 4 * min(1, (d - 46) / 12)))
BOUND = 84
for x in range(-BOUND, BOUND + 1):
    for z in range(-BOUND, BOUND + 1):
        d = dist(x, z)
        if d < R[4] - 0.5: continue                                               # 井戸の口
        g = ground_h(x, z)
        if d < RW + 0.5:                                                          # 第三回廊の上の縁：厚さ2の床
            for y in (TOP - 1, TOP): put(x, y, z, full('tuff_bricks' if y < TOP else 'cobblestone'))
            if lagoon(x, z): put(x, TOP, z, water()); put(x, TOP - 1, z, full('prismarine_bricks'))
            continue
        lo = 0 if d < RW + WT + 0.5 else g - 3
        for y in range(lo, g + 1):
            if d < RW + WT + 0.5 and y < g: put(x, y, z, wall_mat(x, y, z) if d < RW + 1.5 else full('deepslate'))
            else: put(x, y, z, full('dirt'))
        if lagoon(x, z):
            put(x, g, z, water()); put(x, g - 1, z, full('sand' if _h(x, z, 5) < 0.6 else 'gravel'))
        else:
            near_water = any(lagoon(x + dx, z + dz) for dx in (-2, 0, 2) for dz in (-2, 0, 2))
            put(x, g, z, full('sand') if near_water and _h(x, z, 6) < 0.7 else grass() if vnoise(x, z, 6, 7) < 0.82 else full('coarse_dirt'))

# ================================================================ 2. 層の床（k=0 は底、1〜3 は回廊）
def floor_y(k): return k * LV
def lip_k(d):
    """d の所の一番上の層（床がある層）。"""
    for k in (3, 2, 1):
        if d >= R[k] - 0.5: return k
    return 0
GUTTER = {}                                                                         # (x, z) -> 層：縁の内側の溝 [139]
for x in range(-RW, RW + 1):
    for z in range(-RW, RW + 1):
        d = dist(x, z)
        if d >= RW + 0.5: continue
        for k in range(0, 4):
            if k > 0 and d < R[k] - 0.5: continue
            y = floor_y(k)
            put(x, y, z, floor_mat(x, z))
            put(x, y - 1, z, full('tuff_bricks'))
            if k > 0 and d < R[k] + 0.5:                                         # 縁の帯：厚い床の面、下にハーフの帯 [133]
                put(x, y, z, full('chiseled_tuff_bricks' if _h(x, z, 8) < 0.35 else 'polished_tuff'))
                put(x, y - 1, z, full('tuff_bricks'))
                put(x, y - 2, z, slab('mossy_cobblestone' if _h(x, z, 9) < 0.5 else 'tuff_bricks', 'top'))
            nxt = R[k + 1] if k < 3 else R[4]
            if nxt - 1.5 <= d < nxt - 0.5:                                       # 溝：上の縁から落ちる水を受ける
                put(x, y, z, water()); put(x, y - 1, z, full('prismarine_bricks')); GUTTER[(x, z)] = k
# 底：半径18の乾いた円（戦う床）、模様の輪、真ん中の低い台 [110・140]
for x in range(-19, 20):
    for z in range(-19, 20):
        d = dist(x, z)
        if d > 18.4: continue
        r = _h(x, z, 12)
        b = 'polished_tuff' if abs(d - 6) < 0.5 or abs(d - 12.5) < 0.5 else 'tuff_bricks' if r < 0.55 else 'tuff' if r < 0.8 else 'mossy_cobblestone' if r < 0.9 else 'cobblestone'
        if d < 3.5: b = 'chiseled_tuff_bricks' if (x, z) == (0, 0) else 'polished_tuff'
        put(x, 0, z, full(b))
# 回廊の下の池（底の外回り、柱の後ろ）。床を1下げて青緑に [141]
FOOT_A = math.radians(90) + (LV + 0.5) / (RW - 2)                                  # 第一回廊から下りる階段の足もとの角度
CAUSE = [FOOT_A + math.radians(a) for a in (0, 120, 240)]                          # 祭場への土手道（階段の足もとと、あと2本）
def on_cause(x, z): return any(abs(math.sin(ang(x, z) - c)) * dist(x, z) < 1.6 and math.cos(ang(x, z) - c) > 0 for c in CAUSE)
for x in range(-RW, RW + 1):
    for z in range(-RW, RW + 1):
        d = dist(x, z)
        if 22.5 <= d < RW - 0.5 and not on_cause(x, z):
            put(x, 0, z, water()); put(x, -1, z, full('warped_nylium_side', top='warped_nylium', bottom='netherrack') if vnoise(x, z, 4, 13) < 0.55 else full('prismarine'))
            GUTTER.pop((x, z), None)

# ================================================================ 3. 柱とアーチ（層 k の上に、層 k+1 の縁を支える）[135・136]
def column(cx, cz, y0, y1, seed):
    """芯1本に、階段を巻きつける柱。足は 3×3 の階段の台、頭は模様入りと外向きの逆さ階段。"""
    r = random.Random(seed)
    for (dx, dz), f in ((( 1, 0), 'w'), ((-1, 0), 'e'), ((0, 1), 'n'), ((0, -1), 's')):
        put(cx + dx, y0 + 1, cz + dz, stair('tuff_bricks' if r.random() < 0.6 else 'mossy_cobblestone', f))
    put(cx, y0 + 1, cz, full('chiseled_tuff_bricks'))
    for y in range(y0 + 2, y1 - 1):
        put(cx, y, cz, full('tuff_bricks' if (y - y0) % 4 else 'chiseled_tuff'))
        (dx, dz), f = [((1, 0), 'w'), ((0, 1), 'n'), ((-1, 0), 'e'), ((0, -1), 's')][(y - y0) % 4]
        if (y - y0) % 2 == 0: put(cx + dx, y, cz + dz, stair('mossy_cobblestone' if r.random() < 0.5 else 'tuff_bricks', f, 'top' if r.random() < 0.5 else 'bottom'))
    put(cx, y1 - 1, cz, full('chiseled_tuff_bricks'))
    put(cx, y1, cz, full('sea_lantern') if seed % 2 == 0 else full('chiseled_tuff_bricks'))
    for (dx, dz), f in (((1, 0), 'w'), ((-1, 0), 'e'), ((0, 1), 'n'), ((0, -1), 's')):
        put(cx + dx, y1, cz + dz, stair('tuff_bricks', f, 'top'))
PILLARS = []
for k in range(0, 4):
    pr = R[k + 1] + 1.0                                                            # 上の縁の1マス内側
    n = int(round(2 * math.pi * pr / 7))
    off = (0.5 if k % 2 else 0.0) * 2 * math.pi / n                                # 層ごとに半分ずらす（互い違い）
    y0, y1 = floor_y(k), floor_y(k + 1) - 2 if k < 3 else TOP - 2
    pts = []
    for i in range(n):
        a = off + 2 * math.pi * i / n
        cx, cz = int(round(pr * math.cos(a))), int(round(pr * math.sin(a)))
        if k == 3 and abs(cx) <= 4 and cz < 0: continue                           # 入口の口
        column(cx, cz, y0, y1, k * 100 + i); pts.append((cx, cz)); PILLARS.append((cx, cz, k))
    for (ax_, az_), (bx_, bz_) in zip(pts, pts[1:] + pts[:1]):                    # アーチ：柱の間の上に逆さ階段の持ち送り
        L = max(abs(bx_ - ax_), abs(bz_ - az_))
        for t in range(1, L):
            x = int(round(ax_ + (bx_ - ax_) * t / L)); z = int(round(az_ + (bz_ - az_) * t / L))
            e = min(t, L - t)
            if e <= 2 and get(x, y1, z) is None:
                f = face_of(-(bx_ - ax_) if t < L / 2 else (bx_ - ax_), -(bz_ - az_) if t < L / 2 else (bz_ - az_))
                put(x, y1, z, stair('tuff_bricks', f, 'top'))
                if e == 1 and get(x, y1 - 1, z) is None: put(x, y1 - 1, z, stair('tuff_bricks', f, 'top'))
            elif get(x, y1, z) is None: put(x, y1, z, slab('tuff_bricks', 'top'))

# ================================================================ 4. 階段：壁ぞいの弧で下の層へ。上の層の床に穴、穴の内側に塀
STAIRS = {3: math.radians(90), 2: math.radians(-90), 1: math.radians(90)}         # 層 k から k-1 へ下りる階段の上の端の角度
GATES = {}
SR = RW - 2                                                                         # 階段の真ん中の半径
for k, a_top in STAIRS.items():
    lo, hi = floor_y(k - 1), floor_y(k)
    dirn = 1 if k % 2 else -1                                                       # 下りながら回る向き
    for x in range(-RW, RW + 1):
        for z in range(-RW, RW + 1):
            d = dist(x, z)
            if not (SR - 1.5 <= d < SR + 1.5): continue
            da = (ang(x, z) - a_top + math.pi) % (2 * math.pi) - math.pi
            s = da * dirn * SR                                                     # 上の端から下りる向きに測った弧の長さ
            if not (0 <= s < LV + 1): continue
            i = LV - int(s)                                                         # 段の高さ（上の端が LV）
            top = lo + max(1, min(LV, i))
            for y in range(lo + 1, top): put(x, y, z, full('tuff_bricks'))
            tx, tz = -math.sin(ang(x, z)) * dirn, math.cos(ang(x, z)) * dirn       # 下りる向き
            put(x, top, z, stair('tuff_bricks', face_of(-tx, -tz)) if i < LV else full('polished_tuff'))
            for y in range(top + 1, min(hi + 1, top + 4)):                           # 頭の上：上の層の床を抜く
                if y >= hi - 1: put(x, y, z, None)
            if i >= LV - 3 and abs(d - (SR - 1.5)) < 0.6: pass
    # 穴の縁の塀（上の層の床の上、穴の内側の弧）
    for x in range(-RW, RW + 1):
        for z in range(-RW, RW + 1):
            d = dist(x, z)
            if not (SR - 2.5 <= d < SR - 1.5): continue
            da = (ang(x, z) - a_top + math.pi) % (2 * math.pi) - math.pi
            s = da * dirn * SR
            if 1 <= s < 5 and get(x, hi + 1, z) is None: put(x, hi + 1, z, wall('tuff_bricks'))
    gx, gz = int(round(SR * math.cos(a_top))), int(round(SR * math.sin(a_top)))
    GATES['第%s回廊→下' % '〇一二三'[k]] = [gx, hi + 1, gz]

# ================================================================ 5. 縁の塀（跳び越せない）[安全]、滝 [139]、蔦 [144]
FALLS = []
for k in (1, 2, 3):
    y = floor_y(k)
    for x in range(-RW, RW + 1):
        for z in range(-RW, RW + 1):
            d = dist(x, z)
            if R[k] - 0.5 <= d < R[k] + 0.5 and get(x, y + 1, z) is None and get(x, y, z) and get(x, y, z)['s'] == 'full':
                put(x, y + 1, z, wall('tuff_bricks') if _h(x, z, 14) < 0.85 else wall('mossy_cobblestone'))
rim_edge = [(x, z) for x in range(-R[4] - 1, R[4] + 2) for z in range(-R[4] - 1, R[4] + 2) if R[4] - 0.5 <= dist(x, z) < R[4] + 0.5]
for (x, z) in rim_edge:                                                             # 地上の縁：塀、潟の所は口を開けて水が落ちる
    if lagoon(x, z):
        put(x, TOP, z, water()); put(x, TOP - 1, z, full('prismarine_bricks'))
        ix, iz = int(round(x * (R[4] - 1.0) / dist(x, z))), int(round(z * (R[4] - 1.0) / dist(x, z)))
        FALLS.append((ix, iz, TOP, floor_y(3)))
    elif not (abs(x) <= 4 and z < 0): put(x, TOP + 1, z, wall('tuff_bricks'))
for k in (1, 2, 3):                                                                 # 層 k の縁から k-1 の溝へ：角度をずらして5〜6本 [139「チグハグに」]
    n = 5 + (k % 2)
    for i in range(n):
        a = 2 * math.pi * (i + rng.random() * 0.6) / n + k * 0.7
        if any(abs((a - s + math.pi) % (2 * math.pi) - math.pi) < 0.5 for s in STAIRS.values()): continue
        rr = R[k] - 1.0
        FALLS.append((int(round(rr * math.cos(a))), int(round(rr * math.sin(a))), floor_y(k), floor_y(k - 1)))
for (x, z, yt, yb) in FALLS:                                                        # 幅1〜2の水の柱
    for (dx, dz) in ((0, 0),) + (((1, 0),) if _h(x, z, 15) < 0.5 else ()):
        for y in range(yb + 1, yt + 1):
            if get(x + dx, y, z + dz) is None or get(x + dx, y, z + dz)['s'] in ('cross',): put(x + dx, y, z + dz, water())
for k in (1, 2, 3):
    for x in range(-RW, RW + 1):
        for z in range(-RW, RW + 1):
            d = dist(x, z)
            if not (R[k] - 0.5 <= d < R[k] + 0.5) or _h(x, z, 16) > 0.28: continue
            ix, iz = x - int(round(x / d)), z - int(round(z / d))
            if dist(ix, iz) >= R[k] - 0.5: continue
            f = face_of(x - ix, z - iz)
            for y in range(floor_y(k) - 2, floor_y(k) - 3 - int(_h(x, z, 17) * 6), -1):
                if get(ix, y, iz) is None: put(ix, y, iz, vine(f))

# ================================================================ 6. 火の台座（層ごとに3つ、柱の陰の壁ぎわ）[143]、汚し [144]
def pedestal(cx, cz, y):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0: continue
            if dx and dz: put(cx + dx, y + 1, cz + dz, fence('nether_bricks'))
            else: put(cx + dx, y + 1, cz + dz, stair('tuff_bricks', face_of(-dx, -dz)))
    put(cx, y + 1, cz, full('chiseled_tuff_bricks')); put(cx, y + 2, cz, full('nether_bricks')); put(cx, y + 3, cz, cross('fire_0'))
for k in (0, 1, 2):
    for i in range(3):
        a = math.radians(30 + 120 * i + 40 * k)
        if any(abs((a - s + math.pi) % (2 * math.pi) - math.pi) < 0.6 for s in STAIRS.values()): a += 0.8
        rr = (R[k + 1] + 4 if k > 0 else 21.5) if k > 0 else 0
        if k == 0: rr = 19.8                                                         # 底：祭場の縁の外の歩く輪、柱の間
        cx, cz = int(round(rr * math.cos(a))), int(round(rr * math.sin(a)))
        if k == 0:
            put(cx, 1, cz, full('chiseled_tuff_bricks')); put(cx, 2, cz, full('nether_bricks')); put(cx, 3, cz, cross('fire_0'))
        else: pedestal(cx, cz, floor_y(k))
for k in (1, 2, 3):                                                                 # 回廊の屋根の下に吊る灯り（壁ぎわ、8マスおき）
    y = floor_y(k + 1) - 2 if k < 3 else TOP - 2
    n = int(2 * math.pi * (RW - 1) / 8)
    for i in range(n):
        a = 2 * math.pi * (i + 0.5) / n
        x, z = int(round((RW - 1) * math.cos(a))), int(round((RW - 1) * math.sin(a)))
        if get(x, y, z) is None: put(x, y, z, SOUL if k == 3 else HANG)
for (x, z) in list((x, z) for x in range(-RW, RW + 1) for z in range(-RW, RW + 1)):   # 床の隅のシダと草（屋根の下の壁ぎわ）
    d = dist(x, z)
    if RW - 2.5 < d < RW - 0.5 and _h(x, z, 18) < 0.12:
        for k in (1, 2, 3):
            y = floor_y(k)
            if get(x, y + 1, z) is None and get(x, y, z) and get(x, y, z)['s'] == 'full': put(x, y + 1, z, cross('fern' if _h(x, z, 19) < 0.5 else 'short_grass'))

# 底の祭場：壊れた柱の根元4本（遮蔽）と、真ん中の低い台 [138・110]
for i in range(4):
    a = math.radians(45 + 90 * i); x, z = int(round(12.5 * math.cos(a))), int(round(12.5 * math.sin(a)))
    hh = (2, 4, 3, 2)[i]
    for y in range(1, hh + 1): put(x, y, z, full('chiseled_tuff' if y == 1 else 'tuff_bricks'))
    for (dx, dz), f in (((1, 0), 'w'), ((-1, 0), 'e'), ((0, 1), 'n'), ((0, -1), 's')): put(x + dx, 1, z + dz, stair('mossy_cobblestone', f))
    if hh == 2:                                                                     # 折れた先が外へ転がる
        ox, oz = int(round(math.cos(a) * 3)), int(round(math.sin(a) * 3))
        for t in range(0, 3): put(x + ox + int(round(math.cos(a) * t)), 1, z + oz + int(round(math.sin(a) * t)), log('quartz_pillar' if False else 'stripped_birch_log', 'x' if abs(math.cos(a)) > 0.7 else 'z') if False else full('tuff_bricks'))
for (x, z) in ((dx, dz) for dx in range(-3, 4) for dz in range(-3, 4)):
    if 2.5 <= dist(x, z) < 3.5: put(x, 1, z, slab('polished_tuff'))

# ================================================================ 7. 入口：北の白い列柱の祠 [137] と、水路のある石段の切り通し [145]
TZ0, TZ1 = -72, -56                                                                 # 祠の z の範囲（北が正面）
CW = 3                                                                              # 切り通しの半幅（x=-3..3）
def cut_floor(z):
    """切り通しの床の高さ：祠の中の z=-62 で 44、井戸の壁の手前 z=-40 で 33。"""
    t = min(1, max(0, (z - (-62)) / 22.0)); return TOP - int(round(t * LV))
for z in range(-62, -RW + 1):
    fy = cut_floor(z)
    for x in range(-CW - 1, CW + 2):
        if dist(x, z) < RW - 0.5: continue
        for y in range(fy + 1, TOP + 3): put(x, y, z, None)
        if abs(x) == CW + 1:                                                        # 両脇は凝灰岩レンガの壁
            for y in range(fy - 1, TOP + 1): put(x, y, z, full('tuff_bricks' if _h(x, y * 3 + z, 20) < 0.7 else 'mossy_stone_bricks'))
            continue
        for y in range(fy - 2, fy): put(x, y, z, full('tuff_bricks'))
        if x == 0:                                                                  # 真ん中の水路
            put(x, fy, z, water()); put(x, fy - 1, z, full('prismarine_bricks'))
        else:
            nxt = cut_floor(z + 1)
            put(x, fy, z, full('polished_tuff') if abs(x) == 1 else floor_mat(x, z))
            if nxt < fy and z < -RW: put(x, fy, z, stair('tuff_bricks', 'n'))     # 下り口の段
    if z % 4 == 0 and z <= -40:                                                     # 脇の柱の列と、上に渡すまぐさ、灯り
        for x in (-CW, CW):
            for y in range(fy + 1, fy + 6): put(x, y, z, full('tuff_bricks' if y < fy + 5 else 'chiseled_tuff_bricks'))
            put(x, fy + 6, z, LANTERN)
        for x in range(-CW, CW + 1): put(x, fy + 6, z, slab('tuff_bricks', 'top') if abs(x) < CW else full('chiseled_tuff_bricks'))
for x in range(-CW, CW + 1):                                                        # 井戸の壁を抜く口（まぐさ付き）
    for z in range(-RW - WT - 1, -RW + 2):
        if dist(x, z) < RW - 0.5: continue
        fy = floor_y(3)
        for y in range(fy + 1, fy + 7): put(x, y, z, None)
        put(x, fy, z, full('polished_tuff') if x else water())
        put(x, fy + 7, z, full('chiseled_tuff_bricks'))
# 祠：プリズマリンの基壇、白い列柱（高さ8）、梁、切妻 [137]
BX = 9
for x in range(-BX, BX + 1):
    for z in range(TZ0, TZ1 + 1):
        if abs(x) <= CW + 1 and z >= -62: continue
        edge = abs(x) == BX or z in (TZ0, TZ1)
        put(x, TOP, z, full('prismarine_bricks') if edge else full('quartz_block_side', top='quartz_block_top'))
        put(x, TOP - 1, z, full('dark_prismarine'))
for x in range(-BX - 1, BX + 2):                                                    # 正面（北）の段
    if get(x, TOP, TZ0 - 1) is not None and get(x, TOP, TZ0 - 1)['s'] == 'full': put(x, TOP + 0, TZ0 - 1, stair('prismarine_bricks', 's')) if abs(x) <= 4 else None
COLS = [(x, TZ0) for x in range(-BX + 1, BX, 3)] + [(x, TZ1) for x in range(-BX + 1, BX, 3)] + [(sx * (BX - 1), z) for sx in (-1, 1) for z in range(TZ0 + 3, TZ1 - 1, 3)]
for (x, z) in COLS:
    if abs(x) <= CW + 1 and z == TZ1: continue
    put(x, TOP + 1, z, full('chiseled_quartz_block', top='chiseled_quartz_block_top'))
    for y in range(TOP + 2, TOP + 8): put(x, y, z, {'s': 'full', 't': 'quartz_pillar_side', 'top': 'quartz_pillar_top', 'bot': 'quartz_pillar_top'})
    put(x, TOP + 8, z, full('chiseled_quartz_block', top='chiseled_quartz_block_top'))
    for (dx, dz), f in (((1, 0), 'w'), ((-1, 0), 'e'), ((0, 1), 'n'), ((0, -1), 's')):
        if get(x + dx, TOP + 8, z + dz) is None: put(x + dx, TOP + 8, z + dz, stair('quartz_block_side', f, 'top'))
for x in range(-BX + 1, BX):                                                        # 梁2段
    for z in range(TZ0, TZ1 + 1):
        if abs(x) == BX - 1 or z in (TZ0, TZ1):
            put(x, TOP + 9, z, full('quartz_block_side', top='quartz_block_top'))
            put(x, TOP + 10, z, full('prismarine_bricks') if (x + z) % 2 else full('quartz_block_side', top='quartz_block_top'))
for z in range(TZ0 - 1, TZ1 + 2):                                                   # 切妻：x に沿って1段ずつ内へ（屋根は青緑、軒は暗い帯）
    for j in range(0, BX + 1):
        y = TOP + 11 + j // 2
        for sx in (-1, 1):
            x = sx * (BX - j)
            if j % 2 == 0: put(x, y, z, slab('prismarine_bricks') if j else slab('dark_prismarine'))
            else: put(x, y, z, full('prismarine_bricks'))
        if j % 2 == 0 and j:
            for sx in (-1, 1): put(sx * (BX - j + 1), y, z, full('prismarine_bricks'))
    if z in (TZ0 - 1, TZ1 + 1):                                                    # 妻の三角は白
        for j in range(1, BX):
            for x in range(-(BX - j), BX - j + 1):
                y = TOP + 11 + j // 2 - 1
                if get(x, y, z) is None and y >= TOP + 11: put(x, y, z, full('quartz_block_side', top='quartz_block_top'))
for x in range(-BX + 1, BX):                                                        # 天井にシーランタン
    for z in range(TZ0 + 2, TZ1 - 1, 4):
        if x % 4 == 0: put(x, TOP + 11, z, full('sea_lantern'))

# 地上の崩れた白い柱（半分埋まる）[138] と木
def broken_column(x, z, h, fall):
    g = ground_h(x, z)
    put(x, g, z, full('chiseled_quartz_block', top='chiseled_quartz_block_top'))
    for y in range(g + 1, g + h): put(x, y, z, {'s': 'full', 't': 'quartz_pillar_side', 'top': 'quartz_pillar_top', 'bot': 'quartz_pillar_top'})
    if fall:
        a = rng.uniform(0, 2 * math.pi); ax = 'x' if abs(math.cos(a)) > 0.7 else 'z'
        for t in range(2, 5):
            fx, fz = x + int(round(math.cos(a) * t)), z + int(round(math.sin(a) * t))
            if get(fx, ground_h(fx, fz) + 1, fz) is None and not lagoon(fx, fz): put(fx, ground_h(fx, fz) + 1, fz, {'s': 'full', 't': 'quartz_pillar_side', 'top': 'quartz_pillar_top', 'bot': 'quartz_pillar_top', 'axis': ax})
for i in range(14):
    a = rng.uniform(0, 2 * math.pi); d = rng.uniform(40, 62)
    x, z = int(d * math.cos(a)), int(d * math.sin(a))
    if abs(x) < 12 and z < -50 or lagoon(x, z): continue
    broken_column(x, z, rng.randint(2, 7), rng.random() < 0.6)
TREES = 0
for i in range(400):
    x, z = rng.randint(-BOUND + 4, BOUND - 4), rng.randint(-BOUND + 4, BOUND - 4)
    d = dist(x, z)
    if d < 48 or abs(x) < 22 and z < -40 or any(lagoon(x + dx, z + dz) for dx in (-4, 0, 4) for dz in (-4, 0, 4)): continue
    if vnoise(x, z, 26, 31) < 0.5: continue
    if any(get(x + dx, ground_h(x, z) + 2, z + dz) is not None for dx in range(-5, 6, 5) for dz in range(-5, 6, 5)): continue
    g = ground_h(x, z)
    (TR.birch if _h(x, z, 32) < 0.35 else lambda x, z, g, s: TR.rana_oak(x, z, g, s, 0.9))(x, z, g, x * 31 + z); TREES += 1
print('木', TREES)

# ================================================================ 8. 検査と台帳
SPAWN = (0, ground_h(0, -80), -80)
reach = WK.walk(SPAWN, (-BOUND, BOUND, -BOUND, BOUND, -2, TOP + 12))
targets = {'祠の中': (0, TOP, -60), '第三回廊の入口': (0, floor_y(3), -33), '第三回廊の南（階段の上）': (0, floor_y(3), 34),
           '第二回廊の南': (0, floor_y(2), 30), '第二回廊の北（階段の上）': (0, floor_y(2), -34), '第一回廊の北': (0, floor_y(1), -27),
           '第一回廊の南（階段の上）': (0, floor_y(1), 34), '底の土手道': (int(28 * math.cos(FOOT_A)), 0, int(28 * math.sin(FOOT_A))), '祭場の真ん中': (4, 0, 0)}
bad = WK.report(reach, targets)
lv = {k: sum(1 for (x, y, z) in reach if y == floor_y(k) and dist(x, z) < RW) for k in range(4)}
print('層ごとの歩ける床', lv)
ROOMS = {'spawn': [SPAWN[0], SPAWN[1] + 1, SPAWN[2]], 'arena_center': [0, 1, 0], 'arena_r': 18,
         'rooms': {'白い祠': [0, -64, 6], '第三回廊': [0, 0, R[4] + 2], '第二回廊': [0, 0, R[3] + 2], '第一回廊': [0, 0, R[2] + 2], '底の祭場': [0, 0, 18]},
         'gates': GATES, 'order': '白い祠 → 石段の切り通し → 第三回廊（北→南）→ 第二回廊（南→北）→ 第一回廊（北→南）→ 底の祭場'}
V = lambda x, y, z: [-x, y, z]
out = save('sunken_temple', {
    'over': [V(70, 100, 95), V(0, 12, 0)],
    'top': [V(0, 230, 1), V(0, 0, 0)],
    'shrine': [V(-26, 56, -102), V(0, 48, -62)],
    'cut': [V(0, 52, -66), V(0, 36, -38)],
    'g3': [V(-8, 38, -30), V(18, 34, 16)],
    'g1': [V(10, 15, -28), V(-14, 12, 14)],
    'lookup': [V(0, 3, 14), V(0, 40, -16)],
    'arena': [V(0, 18, 34), V(0, 0, 0)],
    'night': [V(0, 3, 14), V(0, 40, -16), {'night': True}],
    'nightover': [V(70, 100, 95), V(0, 12, 0), {'night': True}],
}, V(0, 12, 0), shadowR=110, fog=(180, 420))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
