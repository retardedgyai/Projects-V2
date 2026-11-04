#!/usr/bin/env python3
"""開拓港（11/04、二回目）。藍「タンジアの雰囲気を感じないな、ただ崖にある街って感じだな今は」。

一回目は崖の上の草地に欧州の木組みの家を並べた。それでは「崖の町」にしかならない。
タンジアの画面写真を見て読んだ、雰囲気の出どころ（形や配置は写さない）：
  1. 町が海の高さにある。浅い青緑の海の上に、杭の上の板張りの床と、低い岩の上の石の丸い広場。
  2. 大きな帆船が町のすぐ横に着いていて、帆柱と白い帆が空の輪郭の主役。
  3. 布だらけ：色の旗の綱が頭の上を渡り、日よけの布、垂れ幕。
  4. 白っぽい木（日に焼けた板）と縄と樽。屋根は茅葺き（干し草）。
  5. 海から白い岩の柱が何本も立つ。いちばん高い柱の上に灯台。
色：白い岩と砂、焼けた白い木、干し草の黄、差し色に赤・青・黄の布。

座標：北（-z）が本土の浜、南が沖。海面 -1（浅い：底 -4）、板の床 1、広場 1。
実行：python3 towns/harbor_town.py（out/harbor_town/ に書き出す）。
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import town as TW
from town import Town, save
import vox
from vox import put, get, full, log, stair, slab, trap, fence, cross, vnoise, _h, LANTERN
import props as P

vox.TINT['water_still'] = (0x3A, 0xC4, 0xC8)                                     # 浅い南の海の青緑

# ================================================================ 1. 地形：浜と白い崖（北）、広場の岩、海の岩の柱
STACKS = [(-46, 52, 7, 34, True), (64, 58, 5, 22, False), (-74, 4, 6, 26, False), (34, 92, 4, 16, False), (-20, 96, 3, 12, False)]   # x, z, 半径, 高さ, 灯台
PLAZA = (0, 8, 15)
def nat(x, z):
    for (sx, sz, sr, sh, _) in STACKS:                                           # 岩の柱：上ほど少し細く、縁は欠ける
        d = math.hypot(x - sx, z - sz) + (vnoise(x, z, 3, 21) - 0.5) * 2.5
        if d < sr: return sh - max(0, d - sr + 2) * 3
        if d < sr + 2.5: return max(-4, -1 - (d - sr) * 1.2 + 1)
    px, pz, pr = PLAZA
    if math.hypot(x - px, z - pz) < pr + 3: return 0.6                          # 広場の岩（低い）
    if z < -24:                                                                  # 浜 → 白い崖の背
        b = -24 - z
        h = -1 + b * 0.35 + (vnoise(x, z, 9, 4) - 0.5) * 1.5
        if b > 22: h += min(18, (b - 22) * 0.9) * (0.55 + vnoise(x, z, 24, 6)) + (vnoise(x, z, 6, 8) - 0.5) * 3   # 背の丘は低く、高さを場所で変える（白い壁にしない）
        return h
    return -4
def in_sea(x, z): return nat(x, z) < -1.2

T = Town((-96, 96, -70, 112), nat, in_sea)
PALE = ['calcite', 'diorite', 'calcite', 'sandstone_top', 'calcite', 'diorite', 'sandstone', 'calcite']
def rock_pale(x, y, z):                                                          # 白い岩の層（灰色の石を使わない）
    return PALE[int(y * 0.5 + vnoise(x, z, 9, 2) * 3) % len(PALE)]
T.rock_at = rock_pale
_gt = T.ground_top
SANDSTONE = full('sandstone', top='sandstone_top', bottom='sandstone_bottom')
def ground_south(x, z, k, g, sand=lambda x, z, g: False):
    if k == 'river': return full('sand')
    if k == 'nat' and g < 8: return full('sand') if _h(x, z, 5) < 0.85 else SANDSTONE
    if k == 'nat': return full('calcite') if vnoise(x, z, 5, 3) < 0.5 else TW.grass() if vnoise(x, z, 6, 9) > 0.6 else full('diorite')
    if k == 'plaza':
        r = _h(x, z, 41)
        return full('sandstone_top') if r < 0.5 else full('cut_sandstone', top='sandstone_top') if r < 0.8 else full('smooth_stone')
    return _gt(x, z, k, g, sand)
T.ground_top = ground_south
T.steep_top = lambda x, z: full('calcite') if vnoise(x, z, 5, 67) < 0.6 else full('diorite')

# ================================================================ 2. 段：広場・浜の小道
px, pz, pr = PLAZA
T.pad_disc(px, pz, pr, 1, 'plaza')
T.street([(0, -60, 14), (0, -36, 2), (0, -25, 0)], 2, 'lane')                    # 崖の背から浜へ下りる道
T.resolve(blend_kinds=('lane', 'lane_edge'))
H, KIND = T.H, T.KIND
T.fill()
T.steps()

# ================================================================ 3. 板の床（杭の上）：浜から広場、西の市場、東の船着き場、南の集会所
DECK = {}
def deck(x0, x1, z0, z1, y=1, rail=True):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if KIND.get((x, z)) == 'plaza' or H.get((x, z), -9) >= y: continue
            put(x, y, z, full('birch_planks'))
            DECK[(x, z)] = y; KIND[(x, z)] = 'deck'
            if (x % 4 == 0 and z % 4 == 0) or (x in (x0, x1) or z in (z0, z1)) and (x + z) % 3 == 0:
                for yy in range(H.get((x, z), -4) + 1, y): put(x, yy, z, log('stripped_birch_log'))
    if rail:
        for (x, z) in list(DECK):
            if not (x0 <= x <= x1 and z0 <= z <= z1): continue
            if any((x + dx, z + dz) not in DECK and KIND.get((x + dx, z + dz)) not in ('plaza', 'lane', 'lane_edge') and H.get((x + dx, z + dz), -9) < 1 for dx, dz in TW.N4):
                if get(x, y + 1, z) is None: put(x, y + 1, z, fence('birch_planks'))
deck(-3, 3, -26, -7)                                                             # 浜 → 広場
deck(-42, -14, -8, 22)                                                           # 西の市場
deck(14, 26, -12, 36)                                                            # 東の船着き場
deck(-14, 14, 22, 44)                                                            # 南の集会所の床
deck(-6, 6, 44, 70)                                                              # 沖へ伸びる桟橋
for (x, z) in list(DECK): H[(x, z)] = DECK[(x, z)]
for (x, z) in list(DECK):                                                        # 床と床・広場・浜の継ぎ目の柵を外す
    near = [KIND.get((x + dx, z + dz)) for dx, dz in TW.N4] + [KIND.get((x + 2 * dx, z + 2 * dz)) for dx, dz in TW.N4]
    if any(k in ('plaza', 'lane', 'lane_edge') for k in near) or H.get((x, z - 1), -9) >= 0 and KIND.get((x, z - 1)) == 'nat':
        if get(x, 2, z) and get(x, 2, z)['s'] == 'fence': put(x, 2, z, None)

# ================================================================ 4. 広場：縁、方位の床、真ん中の帆柱（旗の綱の芯）
T.plaza_rim(px, pz, pr, 1, open_cell=lambda x, z: abs(x - px) <= 3 or abs(z - pz) <= 3)
T.floor_marks(px, pz, 1)
for yy in range(2, 18): put(px, yy, pz, log('stripped_spruce_log'))
put(px, 18, pz, {'s': 'trap', 't': 'red_wool', 'f': 'e'}); put(px, 19, pz, LANTERN)
for (dx, dz), f in (((1, 0), 'w'), ((-1, 0), 'e'), ((0, 1), 'n'), ((0, -1), 's')): put(px + dx, 2, pz + dz, stair('cut_sandstone', f))

# ================================================================ 5. 茅葺きの小屋（市場・船着き場・集会所）
HUTS = [(-40, -6, 7, 6, 'red_wool', 's'), (-31, -6, 7, 6, 'light_blue_wool', 's'), (-22, -6, 6, 6, 'yellow_wool', 's'),
        (-40, 14, 7, 7, 'light_blue_wool', 'n'), (-29, 15, 8, 6, 'red_wool', 'n'), (19, -10, 7, 6, 'yellow_wool', 'w'), (18, 26, 8, 8, 'red_wool', 'w')]
for (x0, z0, w, d, c, o) in HUTS: P.hut(x0, z0, 1, w, d, c, -4, o)
P.hut(-11, 28, 1, 23, 15, 'red_wool', -4, 'n')                                   # 集会所：大きな茅葺きの屋根、壁は布、北は開いている

# ================================================================ 6. 帆船・灯台・旗・屋台・小物
P.ship(40, -10, -1, 34, 5)
for (sx, sz, sr, sh, lit) in STACKS:
    if lit: P.lighthouse(sx, sz, int(T.h(sx, sz)), 3, 12)
for (a, b, y) in [((px, pz), (-31, -6), 14), ((px, pz), (19, -10), 14), ((px, pz), (0, 28), 13), ((px, pz), (-3, -24), 12), ((px, pz), (-29, 21), 12),
                  ((px, pz), (26, 30), 13), ((-40, -6), (-40, 20), 7)]:
    P.bunting(a, b, y)
for (x, z, f, c) in [(-17, 2, 'w', 'red_wool'), (-17, 9, 'w', 'light_blue_wool'), (-36, 3, 'e', 'yellow_wool')]:
    P.stall(x, z, 1, f, c, 'white_wool', [P.BARREL_OPEN, P.MELON])
for (x, z, f) in [(-26, 4, 's'), (23, 4, 'e'), (23, 16, 'e'), (9, 46, 'n'), (-12, 46, 'n')]:
    P.cargo(x, z, 1, f)
for z in range(-24, -6, 6):
    for x in (-3, 3): put(x, 2, z, log('stripped_birch_log')); put(x, 3, z, LANTERN)
for (x, z) in [(-6, 70), (6, 70), (26, 36), (14, 36), (-42, 22)]:
    put(x, 2, z, log('stripped_birch_log')); put(x, 3, z, log('stripped_birch_log')); put(x, 4, z, LANTERN)
for (x, z) in [(-10, 52), (10, 60)]: P.boat(x, z, -1, 'z', P.CRATE)

# ================================================================ 7. 書き出し
V = lambda x, y, z: [-x, y, z]
out = save('harbor_town', {
    'arrive': [V(0, 12, -44), V(0, 6, 0)],
    'over': [V(70, 50, 100), V(-4, 6, 16)],
    'sea': [V(10, 14, 120), V(0, 10, 20)],
    'plaza': [V(-6, 4, -6), V(4, 7, 20)],
    'market': [V(-12, 4, -2), V(-34, 4, 6)],
    'ship': [V(8, 6, 40), V(40, 12, 6)],
    'hall': [V(0, 4, 14), V(0, 5, 36)],
    'top': [V(0, 220, 20), V(0, 0, 21)],
    'night': [V(70, 50, 100), V(-4, 6, 16), {'night': True}],
    'nightplaza': [V(-6, 4, -6), V(4, 7, 20), {'night': True}],
}, V(0, 4, 10))
print('書き出し', out)
