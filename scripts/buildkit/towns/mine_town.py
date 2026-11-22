#!/usr/bin/env python3
"""岩山の炭鉱町（11/07、町と拠点）。決まりは rules/mine.md（146〜155）。
手本：hagesan「山の地形を活かした炭鉱街」（9vZypdazdOY）の、山に巻きつく線路・高い木の架台・崖に張り付く家、
KD Plays「ドワーフの鉱山の入口」（JEacxqv0hJI）の、石の前庭と坑口の正面。

形：草地から立ち上がる尖った岩山（中心 (0,0)、頂 70）。南のふもとを削って石の前庭（y=4）にし、北の崖に坑口の正面 [154・155]。
前庭の上の南の崖に、等高線にそって木の歩廊を3段（y=10・17・24）、家は歩廊の外へ杭で張り出す [150]。段は木の階段でつなぐ [151]。
いちばん上の段の真ん中に、山の中の住まいの戸口とテラス [152]。
東の駅から線路が山に巻きつきながら上り、高い所は木の架台 [146・147]、北東の崖でトンネルに入る [149]。
実行：python3 towns/mine_town.py（out/mine_town/）。
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import vox
from vox import put, get, full, log, stair, slab, fence, cross, trap, door, vnoise, _h, LANTERN, grass, path
from town import save
import trees as TR
import walk3d as WK

def wall(t): return {'s': 'wall', 't': t}
HANG = {'s': 'lantern', 't': 'lantern', 'hang': True}
D = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}
OPP = {'n': 's', 's': 'n', 'e': 'w', 'w': 'e'}
def face_of(dx, dz): return max(D, key=lambda k: D[k][0] * dx + D[k][1] * dz)
def dist(x, z): return math.hypot(x, z)
def ang(x, z): return math.atan2(z, x)
rng = random.Random(3)
B = 92                                                                              # 地図の半分の幅
BASE = 4                                                                            # ふもとの高さ

# ================================================================ 1. 地形：尖った岩山。半径46から内へ急に上がり、ひだ（尾根と谷）を付ける
def nat(x, z):
    d = dist(x, z); a = ang(x, z)
    d2 = d * (1 + 0.10 * math.sin(5 * a + 0.7) + 0.06 * math.sin(11 * a + 2.1)) + (vnoise(x, z, 9, 2) - 0.5) * 5
    if d2 > 46: return BASE + (vnoise(x, z, 22, 3) - 0.4) * 2.5 * min(1, (d2 - 46) / 14)
    h = BASE + (46 - d2) * 2.4
    if d2 < 16: h = BASE + 30 * 2.4 + (16 - d2) * 0.9
    return h + (vnoise(x, z, 4, 5) - 0.5) * 3
HM = {(x, z): int(round(nat(x, z))) for x in range(-B, B + 1) for z in range(-B, B + 1)}

# 前庭：南のふもとを削った石の床（x -13..13、z 36..58）。北の壁（坑口の正面）は z=36
FC = (-13, 13, 36, 58)
def in_fc(x, z): return FC[0] <= x <= FC[1] and FC[2] <= z <= FC[3]
for (x, z) in HM:
    if in_fc(x, z): HM[(x, z)] = BASE

# ================================================================ 2. 歩廊の段（等高線ぞい）、段をつなぐ階段 [150・151]
TIERS = [10, 19, 28]
A0, A1 = math.radians(48), math.radians(132)
                                       # 南の崖の弧
def contour(y, a):
    """角度 a の向きで、地面が y 以下になる一番内の半径。"""
    for r10 in range(150, 600):
        r = r10 / 10.0; x, z = int(round(r * math.cos(a))), int(round(r * math.sin(a)))
        if HM.get((x, z), 0) <= y: return r
    return 60.0
def smooth_contour(y):
    n = 240; raw = [contour(y, A0 - 0.25 + (A1 - A0 + 0.5) * i / n) for i in range(n + 1)]   # 弧の両端の外 0.25 rad まで
    out = []
    for i in range(n + 1):
        w = raw[max(0, i - 6):i + 7]; out.append(sum(w) / len(w))
    return lambda a: out[min(n, max(0, int(round((a - (A0 - 0.25)) / (A1 - A0 + 0.5) * n))))]
RK = [smooth_contour(y) for y in TIERS]
DECK = {}                                                                           # (x, z) -> 歩く床の高さ（木の床・削った地面）
POSTED = set()
def deck_cell(x, z, y, mat='spruce_planks', posts=True):
    if HM.get((x, z), -99) >= y:                                                   # 地面の中：削って床に
        for yy in range(y + 1, HM[(x, z)] + 3): put(x, yy, z, None)
        put(x, y, z, full('spruce_planks') if mat == 'spruce_planks' else full(mat))
        HM[(x, z)] = y
    else:
        put(x, y, z, full(mat) if mat != 'slab' else slab('spruce_planks', 'top'))
        if in_fc(x, z) or HM.get((x, z), BASE) <= BASE + 1 and dist(x, z) < 47:          # 前庭の上は柱を立てず、軽いアーチの持ち送りで渡す [148]
            put(x, y - 1, z, stair('spruce_planks', face_of(-math.cos(ang(x, z)), -math.sin(ang(x, z))), 'top') if (x + z) % 2 else trap('spruce_trapdoor', 'top'))
        elif posts and (x * 3 + z * 5) % 9 == 0 and (x, z) not in POSTED:
            for yy in range(HM.get((x, z), BASE) + 1, y): put(x, yy, z, log('spruce_log'))
            POSTED.add((x, z))
    DECK[(x, z)] = y
ARCS = [(A0, A1), (A0 - math.radians(10), A1), (A0, A1 + math.radians(10))]       # 段1は東へ、段2は西へのばす（階段が下の段の上を通らない）
WALK_RING = {}                                                                      # 段ごとの、歩廊の外の縁（柵を立てる所）
for k, y in enumerate(TIERS):
    cells = {}
    a0, a1 = ARCS[k]
    steps = int((a1 - a0) * 60 * 4)
    for i in range(steps + 1):
        a = a0 + (a1 - a0) * i / steps
        if k == 0 and math.radians(76) < a < math.radians(104): continue          # 段0は前庭（坑口）の上で切る：東と西の2本
        rk = RK[k](a)
        for dr10 in range(-12, 26, 3):
            r = rk + dr10 / 10.0
            x, z = int(round(r * math.cos(a))), int(round(r * math.sin(a)))
            if in_fc(x, z) and y < 12 and False: continue
            cells[(x, z)] = max(cells.get((x, z), -9), dr10)
    for (x, z), dr in cells.items(): deck_cell(x, z, y)
    WALK_RING[k] = [(x, z) for (x, z), dr in cells.items() if dr >= 21]
def connector(p0, p1, y0, y1, width=3):
    """p0（高さ y0）から p1（高さ y1）へ、1マスに1段の木の階段。線を細かくたどり、マスの高さは最初に通った時に決める。"""
    n = abs(y1 - y0); L = max(n + 2, int(math.dist(p0, p1)))
    dx, dz = (p1[0] - p0[0]) / L, (p1[1] - p0[1]) / L
    px, pz = -dz, dx
    H = {}
    for j in range(L * 6 + 1):
        t = j / 6.0
        y = y0 + int(math.copysign(min(n, int(t)), y1 - y0))
        for w in range(width):
            c = (int(round(p0[0] + dx * t + px * (w - (width - 1) / 2))), int(round(p0[1] + dz * t + pz * (w - (width - 1) / 2))))
            if c not in H: H[c] = y
    for (x, z), y in H.items():
        for yy in range(y + 1, y + 4):
            b = get(x, yy, z)
            if b and b['s'] != 'door': put(x, yy, z, None)
        if HM.get((x, z), -99) >= y:
            for yy in range(y + 1, HM[(x, z)] + 3): put(x, yy, z, None)
            HM[(x, z)] = y
        elif (x + z) % 3 == 0:
            for yy in range(HM.get((x, z), BASE) + 1, y): put(x, yy, z, log('spruce_log'))
        put(x, y, z, full('spruce_planks')); DECK[(x, z)] = y
    for (x, z), y in H.items():                                                      # 1段上のとなりがあれば、階段にして半歩上げる
        up = [f for f, (ddx, ddz) in D.items() if H.get((x + ddx, z + ddz)) == y + 1]
        low = [f for f, (ddx, ddz) in D.items() if H.get((x + ddx, z + ddz)) == y - 1]
        if up and low: put(x, y, z, stair('spruce_planks', up[0]))
    return [(x, z, y) for (x, z), y in H.items()]
def tier_pt(k, a, dr=0.5): r = RK[k](a) + dr; return (r * math.cos(a), r * math.sin(a))
CONN = []
CONN += connector(tier_pt(0, math.radians(50), 1.5), tier_pt(1, math.radians(39), 0.5), TIERS[0], TIERS[1])   # 東の端の外で 0→1
CONN += connector(tier_pt(1, math.radians(130), 1.5), tier_pt(2, math.radians(141), 0.5), TIERS[1], TIERS[2])  # 西の端の外で 1→2
CONN += connector((9, 47), tier_pt(0, math.radians(66), 0.5), BASE, TIERS[0])                           # 前庭の東から段0へ
CONN += connector((-9, 47), tier_pt(0, math.radians(114), 0.5), BASE, TIERS[0])                         # 前庭の西から段0へ
CONN_CELLS = {(x, z) for (x, z, y) in CONN}

# ================================================================ 3. 地面を積む（岩肌の縞、ふもとは草、前庭は石）
def rock(x, y, z):
    n = vnoise(x, z, 3, 31) * 0.75 + vnoise(x, z, 8, 32) * 0.25 + (vnoise(x, y, 6, 33) - 0.5) * 0.12   # 縦の筋：横の位置で決め、高さではほとんど変えない（横縞にしない）
    return 'stone' if n < 0.38 else 'andesite' if n < 0.5 else 'coarse_dirt' if n < 0.58 else 'dirt' if n < 0.64 else 'stone' if n < 0.8 else 'tuff'
for (x, z), h in HM.items():
    if (x, z) in DECK and get(x, h, z) is not None and HM[(x, z)] == DECK[(x, z)]: lo_fill = h - 1
    else: lo_fill = h
    nb = min(HM.get((x + dx, z + dz), h) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    steep = h - nb >= 2
    for y in range(min(nb, h - 2), lo_fill + 1):
        if get(x, y, z) is not None: continue
        if y == h:
            if in_fc(x, z): b = full('smooth_stone') if _h(x, z, 4) < 0.5 else full('stone_bricks') if _h(x, z, 5) < 0.7 else full('gravel') if _h(x, z, 6) < 0.5 else full('cobblestone')
            elif steep: b = full(rock(x, y, z))
            elif h > 40: b = full('stone') if vnoise(x, z, 5, 8) < 0.5 else grass()
            else: b = grass() if vnoise(x, z, 6, 9) < 0.85 else full('coarse_dirt')
        else: b = full(rock(x, y, z)) if steep or y < h - 2 else full('dirt')
        put(x, y, z, b)
for (x, z) in list(HM):                                                              # 前庭の縁：削った崖の面（石の層）
    if in_fc(x, z):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if not in_fc(*n) and n in HM and HM[n] > BASE:
                for y in range(BASE + 1, HM[n] + 1):
                    if get(*n[:1], y, n[1]) is None: put(n[0], y, n[1], full(rock(n[0], y, n[1])))

# ================================================================ 4. 坑口の正面（前庭の北の壁、z=36）[154・155]
FZ = FC[2]                                                                           # 正面の面の z（壁の内側）
for x in range(-8, 9):
    top = BASE + 11 - (abs(x) > 5) * 2 - (abs(x) > 7) * 2
    for y in range(BASE + 1, top + 1):
        for zz in (FZ - 1, FZ):
            put(x, y, zz, full('stone_bricks' if _h(x, y, 7) < 0.7 else 'cracked_stone_bricks' if _h(x, y, 8) < 0.5 else 'mossy_stone_bricks'))
    put(x, top + 1, FZ, slab('stone_bricks') if x % 2 else full('chiseled_stone_bricks'))
for sx in (-1, 1):                                                                   # 前へ出る柱（2段）
    for (xo, zo, hh) in ((5, 1, 9), (6, 2, 6)):
        x = sx * xo
        for y in range(BASE + 1, BASE + hh + 1): put(x, y, FZ + zo, full('stone_bricks' if y % 3 else 'chiseled_stone_bricks'))
        put(x, BASE + hh + 1, FZ + zo, slab('stone_bricks'))
    for y in range(BASE + 1, BASE + 4): put(sx * 3, y, FZ + 3, full('stone_bricks'))   # 火の台
    put(sx * 3, BASE + 4, FZ + 3, full('magma_block') if False else full('chiseled_stone_bricks')); put(sx * 3, BASE + 5, FZ + 3, cross('fire_0'))
for x in range(-1, 2):                                                               # 口：幅3・高さ4、上の角を階段で丸める。奥へ掘る
    for zz in range(FZ - 14, FZ + 1):
        for y in range(BASE + 1, BASE + 5): put(x, y, zz, None)
        put(x, BASE, zz, full('stone_bricks') if x else full('spruce_planks'))
        if zz < FZ - 1 and zz % 4 == 0:
            for sx2 in (-2, 2):
                for y in range(BASE + 1, BASE + 5): put(sx2, y, zz, log('spruce_log'))
            for x2 in range(-2, 3): put(x2, BASE + 5, zz, log('spruce_log', 'x'))
        put(x, BASE + 1, zz, {'s': 'carpet', 't': 'rail'}) if x == 0 else None
for sx in (-1, 1): put(sx * 1, BASE + 4, FZ, stair('stone_bricks', 'e' if sx < 0 else 'w', 'top'))
for x in (-1, 0, 1): put(x, BASE + 6, FZ, full('gold_block'))                        # 口の上の金の飾り
put(-3, BASE + 7, FZ, full('gold_block')); put(3, BASE + 7, FZ, full('gold_block'))
put(0, BASE + 3, FZ + 1, HANG) if get(0, BASE + 4, FZ + 1) else None
# 前庭の通路：口から南へ、両脇に溶岩の溝と柵 [155]
for z in range(FZ + 4, FZ + 18):
    for sx in (-1, 1):
        put(sx * 3, BASE, z, full('lava_still'))
        for xx in (sx * 2, sx * 4):
            if get(xx, BASE + 1, z) is None: put(xx, BASE + 1, z, fence('spruce_planks') if (z - FZ) % 6 else wall('stone_bricks'))
    put(0, BASE, z, path() if _h(0, z, 9) < 0.6 else full('gravel'))
for sx in (-1, 1): put(sx * 2, BASE + 1, FZ + 4, None); put(sx * 4, BASE + 1, FZ + 4, None)

# ================================================================ 5. 線路：東の駅から山に巻きつきながら上り、北東でトンネルへ [146〜149]
A_START, A_END = math.radians(64), math.radians(-78)
N = 900
RAIL = []
for i in range(N + 1):
    t = i / N; a = A_START + (A_END - A_START) * t
    r = 61 - 27 * max(0, (t - 0.35) / 0.65) ** 1.3
    RAIL.append((r * math.cos(a), r * math.sin(a)))
L = 0; RY = []
for i, p in enumerate(RAIL):
    if i: L += math.dist(p, RAIL[i - 1])
    RY.append(BASE + 1 + int(L / 4.2))
RAIL_TOP = RY[-1]
print('線路の長さ', int(L), '上の高さ', RAIL_TOP)
RAILC = {}
for i, (x, z) in enumerate(RAIL):
    c = (int(round(x)), int(round(z)))
    if c not in RAILC: RAILC[c] = (RY[i], i)
def tangent(i):
    j = min(len(RAIL) - 1, i + 3); k = max(0, i - 3)
    dx, dz = RAIL[j][0] - RAIL[k][0], RAIL[j][1] - RAIL[k][1]; n = math.hypot(dx, dz) or 1
    return dx / n, dz / n
BED = {}
for i, (x, z) in enumerate(RAIL):
    tx, tz = tangent(i); px, pz = -tz, tx
    for w in (-1, 0, 1):
        c = (int(round(x + px * w)), int(round(z + pz * w)))
        BED[c] = max(BED.get(c, -99), RY[i])
for (x, z), y in BED.items():
    for yy in range(y + 1, y + 5):
        b = get(x, yy, z)
        if b and b['s'] in ('full', 'cross', 'stair', 'slab') and (x, z) not in DECK: put(x, yy, z, None)
    if HM.get((x, z), -99) >= y: HM[(x, z)] = y                                       # 切り通し
    put(x, y, z, log('spruce_log', 'x' if abs(tangent(RAILC.get((x, z), (0, 0))[1])[0]) > 0.7 else 'z'))
for (x, z), (y, i) in RAILC.items():                                                 # レールと枕木の階段
    tx, tz = tangent(i)
    put(x, y + 1, z, {'s': 'carpet', 't': 'rail', 'ax': 'x' if abs(tx) > abs(tz) else 'z'})
    px, pz = -tz, tx
    for w in (-1, 1):
        c = (int(round(x + px * w)), int(round(z + pz * w)))
        if BED.get(c) == y and get(c[0], y + 1, c[1]) is None and _h(c[0], c[1], 10) < 0.5:
            put(c[0], y + 1, c[1], slab('spruce_planks'))
# 架台：5マスおきの柱の組（線路の左右）、上から3区画に X の筋交い、5段ごとに横木 [147]
for i in range(0, N, 1):
    if i % int(N * 5 / L) != 0: continue
    x, z = RAIL[i]; y = RY[i]; tx, tz = tangent(i); px, pz = -tz, tx
    cols = [(int(round(x + px * w)), int(round(z + pz * w))) for w in (-2, 2)]
    g = min(HM.get(c, BASE) for c in cols)
    if y - g < 4: continue
    for c in cols:
        for yy in range(HM.get(c, BASE) + 1, y + 1): put(c[0], yy, c[1], log('stripped_spruce_log'))
    for w in range(-2, 3):
        c = (int(round(x + px * w)), int(round(z + pz * w)))
        if abs(w) < 2:
            for yy in range(y - 1, y): put(c[0], yy, c[1], log('stripped_spruce_log', 'x' if abs(px) > abs(pz) else 'z'))
    for panel in range(3):                                                           # 上から3区画だけ
        top = y - 1 - panel * 5
        if top - 5 < g: break
        for j in range(5):
            for (wa, f) in ((-2 + j, None), (2 - j, None)):
                c = (int(round(x + px * wa)), int(round(z + pz * wa)))
                yy = top - j
                if abs(wa) == 2: continue
                if get(c[0], yy, c[1]) is None:
                    put(c[0], yy, c[1], full('spruce_planks') if wa == 0 else stair('spruce_planks', face_of(px * (1 if wa > 0 else -1), pz * (1 if wa > 0 else -1)), 'top' if j < 2 else 'bottom'))
        for w in range(-1, 2):
            c = (int(round(x + px * w)), int(round(z + pz * w)))
            if get(c[0], top - 5, c[1]) is None: put(c[0], top - 5, c[1], log('stripped_spruce_log', 'x' if abs(px) > abs(pz) else 'z'))
# 坑口（トンネル）：線路の端から山の中へ8マス掘り、原木の枠を1マスずつ奥へずらす [149]
ex, ez = RAIL[-1]; tx, tz = tangent(N - 1); px, pz = -tz, tx
for s in range(0, 14):
    cx, cz = ex + tx * s, ez + tz * s
    for w in range(-2, 3):
        c = (int(round(cx + px * w)), int(round(cz + pz * w)))
        for yy in range(RAIL_TOP + 1, RAIL_TOP + 6): put(c[0], yy, c[1], None)
        put(c[0], RAIL_TOP, c[1], log('spruce_log') if abs(w) <= 1 else full('gravel'))
        if w == 0 and s > 0: put(c[0], RAIL_TOP + 1, c[1], {'s': 'carpet', 't': 'rail', 'ax': 'x' if abs(tx) > abs(tz) else 'z'})
    if s in (0, 1, 2, 5, 9):
        inset = {0: 0, 1: 0, 2: 1, 5: 1, 9: 1}[s]
        for w in (-2 + inset, 2 - inset):
            c = (int(round(cx + px * w)), int(round(cz + pz * w)))
            for yy in range(RAIL_TOP + 1, RAIL_TOP + 5 - inset + (1 if s == 0 and w > 0 else 0)): put(c[0], yy, c[1], log('spruce_log'))
        for w in range(-2 + inset, 3 - inset):
            c = (int(round(cx + px * w)), int(round(cz + pz * w)))
            put(c[0], RAIL_TOP + 5 - inset + (1 if s == 0 and w > 0 else 0), c[1], log('spruce_log', 'x' if abs(px) > abs(pz) else 'z'))
c = (int(round(ex + tx * 1)), int(round(ez + tz * 1))); put(c[0], RAIL_TOP + 4, c[1], HANG)

# 駅：線路の始まりの横に木の台と小さな屋根 [rules 13]、トロッコ3両 [153]
sx0, sz0 = RAIL[0]; tx, tz = tangent(0); px, pz = -tz, tx
ST = []
for s in range(-2, 9):
    for w in range(2, 6):
        c = (int(round(sx0 + tx * s + px * w)), int(round(sz0 + tz * s + pz * w)))
        ST.append(c); put(c[0], BASE + 1, c[1], full('spruce_planks')); DECK[c] = BASE + 1
        HM[c] = max(HM.get(c, BASE), BASE)
for s in (-2, 8):
    for w in (2, 5):
        c = (int(round(sx0 + tx * s + px * w)), int(round(sz0 + tz * s + pz * w)))
        for yy in range(BASE + 2, BASE + 6): put(c[0], yy, c[1], log('dark_oak_log'))
for s in range(-3, 10):
    for w in range(1, 7):
        c = (int(round(sx0 + tx * s + px * w)), int(round(sz0 + tz * s + pz * w)))
        put(c[0], BASE + 6, c[1], slab('dark_oak_planks') if w in (1, 6) else full('dark_oak_planks') if w in (2, 5) else slab('dark_oak_planks', 'top'))
c = (int(round(sx0 + tx * 3 + px * 3)), int(round(sz0 + tz * 3 + pz * 3))); put(c[0], BASE + 5, c[1], HANG)
def cart_car(i0, head):
    """トロッコ1両：線路の上に長さ3・幅3。先頭は窓と屋根、後ろは荷台。"""
    for s in range(3):
        x, z = RAIL[i0 + s * int(N / L)]; y = RY[i0]
        tx, tz = tangent(i0); px, pz = -tz, tx
        for w in (-1, 0, 1):
            c = (int(round(x + px * w)), int(round(z + pz * w)))
            put(c[0], y + 1, c[1], slab('dark_oak_planks', 'top'))
            if head:
                put(c[0], y + 2, c[1], log('dark_oak_log') if abs(w) == 1 and s != 1 else full('iron_bars') if abs(w) == 1 else None)
                put(c[0], y + 3, c[1], slab('dark_oak_planks'))
            elif abs(w) == 1 or s in (0, 2): put(c[0], y + 2, c[1], trap('spruce_trapdoor', face_of(-px * w, -pz * w)) if abs(w) == 1 else full('spruce_planks'))
            else: put(c[0], y + 2, c[1], full('coal_ore') if _h(c[0], c[1], 11) < 0.5 else full('iron_ore'))
    if head:
        x, z = RAIL[i0 + int(N / L)]; put(int(round(x)), RY[i0] + 4, int(round(z)), full('cauldron_side', top='cauldron_top'))
step = int(N / L) * 4
cart_car(2, True); cart_car(2 + step, False); cart_car(2 + 2 * step, False)

# ================================================================ 6. 崖に張り付く家（段ごとに、外へ杭で張り出す）[150]
ROOFS = ('acacia_planks', 'bricks')
HOUSES = []; BOXES = []; DOORWAY = set(); REJ = {}
def house(cx, cz, f, y, w, d, roofm, seed):
    """f は外の向き。奥（山側）の面に戸、外の面に窓。床は y、壁4段、切妻は外向きに下がる。"""
    r = random.Random(seed)
    fx, fz = D[f]; rx, rz = D[{'n': 'e', 'e': 's', 's': 'w', 'w': 'n'}[f]]
    cells = [(cx + fx * v + rx * u, cz + fz * v + rz * u, u, v) for u in range(-(w // 2), w - w // 2) for v in range(d)]
    xs = [c[0] for c in cells]; zs = [c[1] for c in cells]
    box = (min(xs), max(xs), min(zs), max(zs), y, y + 9)
    for b2 in BOXES:
        if not (box[1] + 1 < b2[0] or b2[1] + 1 < box[0] or box[3] + 1 < b2[2] or b2[3] + 1 < box[2] or box[5] < b2[4] or b2[5] < box[4]): return None
    why = '階段' if any((x, z) in CONN_CELLS for (x, z, u, v) in cells) else '線路' if any((x, z) in BED for (x, z, u, v) in cells) else '前庭' if y < 20 and any(in_fc(x, z) for (x, z, u, v) in cells) else None
    if why: REJ[why] = REJ.get(why, 0) + 1; return None
    if max(y - HM.get((x, z), BASE) for (x, z, u, v) in cells) > 20: REJ['杭'] = REJ.get('杭', 0) + 1; return None      # 張り付く家：杭は短く（手本の家は崖に寄りかかる）
    BOXES.append(box)
    umin, umax = -(w // 2), w - w // 2 - 1
    for (x, z, u, v) in cells:
        for yy in range(y + 1, y + 10): put(x, yy, z, None)
        put(x, y, z, full('spruce_planks')); DECK[(x, z)] = y                       # 家の床も床として数える（戸の前に柵を立てない）
        if HM.get((x, z), -99) < y and (u in (umin, umax)) and v in (0, d - 1) and not in_fc(x, z):        # 杭（四隅）：下の物に当たるまで
            yy = y - 1
            while yy > HM.get((x, z), BASE) and get(x, yy, z) is None: put(x, yy, z, log('stripped_jungle_log')); yy -= 1
        edge_u, edge_v = u in (umin, umax), v in (0, d - 1)
        for yy in range(y + 1, y + 5):
            if edge_u and edge_v: put(x, yy, z, log('stripped_jungle_log'))
            elif edge_u or edge_v: put(x, yy, z, full('jungle_planks'))
        if edge_u or edge_v:
            put(x, y + 4, z, log('stripped_jungle_log', 'x' if (edge_v and abs(rx)) or (edge_u and abs(fx)) else 'z'))
    for (x, z, u, v) in cells:                                                       # 窓（外の面と横の面）
        if v == d - 1 and u not in (umin, umax) and u % 2 == 0: put(x, y + 2, z, {'s': 'pane', 't': 'iron_bars', 'ax': 'x' if rx else 'z'}); put(x + fx, y + 2, z + fz, trap('spruce_trapdoor', f)) if get(x + fx, y + 2, z + fz) is None else None
        if u in (umin, umax) and 0 < v < d - 1 and v % 2: put(x, y + 2, z, {'s': 'pane', 't': 'iron_bars', 'ax': 'z' if rx else 'x'})
    dc = [(x, z) for (x, z, u, v) in cells if v == 0 and u == 0][0]                  # 戸（山側）
    put(dc[0], y + 1, dc[1], door('bottom', OPP[f], 'jungle')); put(dc[0], y + 2, dc[1], door('top', OPP[f], 'jungle'))
    put(dc[0] - fx, y + 3, dc[1] - fz, HANG) if get(dc[0] - fx, y + 3, dc[1] - fz) is None and False else None
    # 切妻：棟は u に沿う。v の両側から1段ずつ上がる。軒は外へ1マス
    for v in range(-1, d + 1):
        k = min(v + 1, d - v)
        yy = y + 5 + k // 1 - 1 if False else y + 4 + (k + 1) // 2
        half = (k % 2 == 1)
        for u in range(umin - 1, umax + 2):
            x, z = cx + fx * v + rx * u, cz + fz * v + rz * u
            if get(x, yy, z) is not None and get(x, yy, z)['s'] == 'full': continue
            if v * 2 == d - 1: put(x, yy, z, full(roofm))
            elif half: put(x, yy, z, slab(roofm))
            else: put(x, yy, z, stair(roofm, OPP[f] if v < (d - 1) / 2 else f))
        if 0 <= v < d and k >= 2:                                                   # 妻の三角を壁で埋める
            for u in (umin, umax):
                x, z = cx + fx * v + rx * u, cz + fz * v + rz * u
                for y2 in range(y + 5, yy): put(x, y2, z, full('jungle_planks'))
    if r.random() < 0.5:                                                             # 焚き火の煙突（壁ぎわ）
        x, z = cx + fx * (d - 1) + rx * umax, cz + fz * (d - 1) + rz * umax
        for y2 in range(y + 5, y + 8): put(x, y2, z, full('bricks'))
        put(x, y + 8, z, cross('fire_0') if False else slab('bricks'))
    HOUSES.append((dc[0] - fx, y, dc[1] - fz))
    return box
for k, y in enumerate(TIERS):
    a = A0 + 0.05 + (0.12 if k % 2 else 0.0)
    while a < A1 - 0.05:
        w = rng.choice((5, 5, 6, 7)); d = rng.choice((4, 5, 5))
        if k == 0 and math.radians(70) < a < math.radians(110): a += 0.03; continue
        rk = RK[k](a); rr = rk + 1.5
        cx, cz = int(round(rr * math.cos(a))), int(round(rr * math.sin(a)))
        f = face_of(math.cos(a), math.sin(a))
        fx, fz = D[f]
        if house(cx + fx, cz + fz, f, y, w, d, ROOFS[int(_h(cx, cz, 12) * 2)], int(a * 1000) + k):
            for s in range(0, 5):                                                    # 戸から歩廊まで床をつなぐ（柵を立てない）
                c = (cx + fx - fx * s, cz + fz - fz * s)
                if DECK.get(c) != y: deck_cell(c[0], c[1], y)
                DOORWAY.add(c)
                for yy in (y + 1, y + 2):
                    if s > 0 and get(c[0], yy, c[1]) is not None: put(c[0], yy, c[1], None)
            a += (w + 4) / rk
        else: a += 0.03
# 歩廊の外の縁に柵（家と階段の口は空ける）
for k, ring in WALK_RING.items():
    y = TIERS[k]
    for (x, z) in ring:
        if (x, z) in CONN_CELLS or (x, z) in DOORWAY or get(x, y + 1, z) is not None or DECK.get((x, z)) != y: continue
        out = (int(round(x + math.cos(ang(x, z)))), int(round(z + math.sin(ang(x, z)))))
        if DECK.get(out) == y: continue
        put(x, y + 1, z, fence('spruce_planks'))
        if (x * 7 + z * 3) % 9 == 0: put(x, y + 2, z, LANTERN)

# ================================================================ 7. いちばん上：山の中の住まいの戸口とテラス [152]
a = math.radians(90); rk = RK[2](a); y = TIERS[2]
tx_, tz_ = int(round(rk * math.cos(a))), int(round(rk * math.sin(a)))
for dx in range(-5, 6):
    for dz in range(0, 7):
        c = (tx_ + dx, tz_ - 1 + dz)
        if get(c[0], y, c[1]) is None or HM.get(c, -99) >= y: deck_cell(c[0], c[1], y)
for dx in range(-5, 6):
    for c in ((tx_ + dx, tz_ + 6),):
        if get(c[0], y + 1, c[1]) is None: put(c[0], y + 1, c[1], fence('spruce_planks'))
for dz in range(0, 7):
    for sx in (-6, 6):
        c = (tx_ + sx, tz_ - 1 + dz)
        if get(c[0], y + 1, c[1]) is None and DECK.get(c) != y: put(c[0], y + 1, c[1], fence('spruce_planks'))
for dz in range(-6, 0):                                                              # 崖に戸口（奥へ6マス）、枠は原木
    for dx in (-1, 0, 1):
        c = (tx_ + dx, tz_ + dz)
        for yy in range(y + 1, y + 4): put(c[0], yy, c[1], None)
        put(c[0], y, c[1], slab('spruce_planks', 'top')); HM[c] = y; DECK[c] = y
for yy in range(y + 1, y + 4):
    for dx in (-2, 2): put(tx_ + dx, yy, tz_ - 1, log('spruce_log'))
for dx in range(-2, 3): put(tx_ + dx, y + 4, tz_ - 1, log('spruce_log', 'x'))
put(tx_, y + 3, tz_ - 2, HANG)
put(tx_ - 4, y + 1, tz_ + 1, full('barrel_side', top='barrel_top')); put(tx_ - 3, y + 1, tz_ + 1, full('barrel_side', top='barrel_top'))
for dx in range(-3, 4):                                                              # 洗濯物：柵の柱2本に、羊毛の絨毯を吊る代わりに白い旗の帯
    pass
for sx in (-4, 4):
    for yy in range(y + 1, y + 4): put(tx_ + sx, yy, tz_ + 4, fence('spruce_planks'))
for dx in range(-4, 5):
    put(tx_ + dx, y + 4, tz_ + 4, fence('spruce_planks'))
put(tx_ + 3, y + 1, tz_ + 2, full('iron_ore')); put(tx_ + 2, y + 1, tz_ + 1, cross('fern'))

# ================================================================ 8. ふもとの森と草、生活の跡 [153]
for i in range(1400):
    x, z = rng.randint(-B + 4, B - 4), rng.randint(-B + 4, B - 4)
    if dist(x, z) < 64 or (abs(x) < 26 and z > 30) or any((x + dx, z + dz) in BED or in_fc(x + dx, z + dz) for dx in (-5, 0, 5) for dz in (-5, 0, 5)): continue
    if vnoise(x, z, 24, 41) < 0.45: continue
    g = HM[(x, z)]
    if any(get(x + dx, g + 2, z + dz) is not None for dx in (-5, 0, 5) for dz in (-5, 0, 5)): continue
    r = _h(x, z, 42)
    if r < 0.35: TR.spruce(x, z, g, x * 31 + z)
    elif r < 0.5: TR.birch(x, z, g, x * 31 + z)
    else: TR.rana_oak(x, z, g, x * 31 + z, 0.9)
for i in range(60):                                                                  # 山の上のほうに小さいトウヒ
    a = rng.uniform(0, 2 * math.pi); d = rng.uniform(4, 30)
    x, z = int(d * math.cos(a)), int(d * math.sin(a))
    g = HM[(x, z)]
    if max(abs(HM.get((x + dx, z + dz), g) - g) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) <= 1 and get(x, g + 1, z) is None and (x, z) not in DECK:
        TR.small_spruce(x, z, g, x * 7 + z)
for (x, z) in [(10, 52), (-11, 54), (8, 40), (-9, 41)]:                             # 前庭の荷：原木の束、樽、白いチェストの代わりに樽と鉱石
    g = BASE
    put(x, g + 1, z, log('spruce_log', 'x')); put(x + 1, g + 1, z, log('spruce_log', 'x')); put(x, g + 2, z, log('spruce_log', 'x'))
    put(x, g + 1, z + 1, full('barrel_side', top='barrel_top')); put(x + 1, g + 1, z + 1, full('iron_ore' if x > 0 else 'coal_ore'))
for (x, z), h in list(HM.items()):                                                   # 草（ふもと）
    if dist(x, z) > 48 and get(x, h + 1, z) is None and get(x, h, z) and get(x, h, z).get('top') == 'grass_top' and _h(x, z, 44) < 0.18:
        put(x, h + 1, z, cross('short_grass' if _h(x, z, 45) < 0.8 else 'fern'))

# ================================================================ 9. 検査と台帳
SPAWN = (int(round(sx0 + tx * 3 + px * 3.5)), BASE + 1, int(round(sz0 + tz * 3 + pz * 3.5)))
reach = WK.walk(SPAWN, (-B, B, -B, B, 0, 90))
targets = {'前庭': (0, BASE, 46), '坑口の中': (0, BASE, FZ - 8), '段0の東': (int(round(tier_pt(0, math.radians(60))[0])), TIERS[0], int(round(tier_pt(0, math.radians(60))[1]))), '段0の西': (int(round(tier_pt(0, math.radians(120))[0])), TIERS[0], int(round(tier_pt(0, math.radians(120))[1]))),
           '段1': (int(round(tier_pt(1, math.radians(90))[0])), TIERS[1], int(round(tier_pt(1, math.radians(90))[1]))),
           '段2のテラス': (tx_, TIERS[2], tz_ + 2), '山の中の戸口': (tx_, TIERS[2], tz_ - 5)}
for i, (x, y, z) in enumerate(HOUSES): targets['家%d' % i] = (x, y, z)
bad = WK.report(reach, targets)
RC = {(x, z): y for (x, y, z) in reach}
for nm, cs in (('0→1', CONN[:]),):
    pass
for i, (x, z, y) in enumerate(CONN):
    if (x, z) not in RC: print('階段の届かない所', i, (x, z, y), 'DECK', DECK.get((x, z)), [get(x, yy, z) and get(x, yy, z)['s'] for yy in range(y - 1, y + 4)]); break
print('家', len(HOUSES), '置けなかった理由', REJ)
ROOMS = {'spawn': [SPAWN[0], SPAWN[1] + 1, SPAWN[2]], 'places': {'駅': [SPAWN[0], SPAWN[2]], '前庭と坑口': [0, 46], '上のテラス': [tx_, tz_ + 2]}}
V = lambda x, y, z: [-x, y, z]
out = save('mine_town', {
    'over': [V(40, 110, 150), V(0, 22, 10)],
    'top': [V(0, 260, 1), V(0, 0, 0)],
    'front': [V(0, 16, 74), V(0, 12, 36)],
    'cliff': [V(-30, 30, 76), V(4, 18, 40)],
    'trestle': [V(80, 30, 30), V(40, 18, -10)],
    'tunnel': [V(30, RAIL_TOP + 6, -55), V(int(ex), RAIL_TOP, int(ez))],
    'terrace': [V(tx_ + 10, TIERS[2] + 4, tz_ + 14), V(tx_ - 6, TIERS[2] + 1, tz_ - 4)],
    'night': [V(0, 16, 74), V(0, 12, 36), {'night': True}],
    'nightover': [V(40, 110, 150), V(0, 22, 10), {'night': True}],
}, V(0, 22, 10), shadowR=140, fog=(220, 520))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
