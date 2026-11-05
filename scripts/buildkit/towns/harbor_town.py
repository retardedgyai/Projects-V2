#!/usr/bin/env python3
"""開拓港（11/04、三回目）。決まりは rules/harbor.md（85〜105）、部品は kit/harbor.py（見本の場 towns/harbor_parts.py）。

一回目「ただ崖にある街って感じ」。二回目は空気（海の高さ・帆船・布・茅葺き・白い岩の柱）は合ったが、
部品を手本なしで式から作り「建築の作りとか全部がおわってしまった」。三回目は手本17本の決まりで作った部品だけで組む。

配置（決まりの番号）：
  - 高さの段 [85]：水面 -1 → 板の足場 0（+1）→ 石の道 1（+2）→ 広場と家の床 2（+3）。
  - 真ん中は石の護岸の丸い広場 [86]。浜から石の土手道（芯は石、両脇に木の足場）[89]。
  - 西は高床の茅葺きの家の群れ [94〜98]、板の足場でつなぐ。枠の色を変え、壁は地味に [101 の家版・fI0KI93UrBo]。
  - 東は船着き場：深い水路に、港には大きすぎるくらいの帆船 [91・99〜104]、クレーン2基 [90]、荷の流れ。
  - 南は大きな集会所（2階建ての高床）、南西の岩の島に灯台 [105]。
  - 街の床は白い石、港まわりは木 [87]。強い差し色は屋台の縞と帆だけ [92]。
座標：北（-z）が本土の浜、南が沖。
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import town as TW
from town import Town, save
import vox
from vox import put, get, full, log, stair, slab, trap, fence, cross, vnoise, _h, LANTERN
import props as P
import harbor as HB

vox.TINT['water_still'] = (0x3A, 0xC4, 0xC8)                                     # 浅い南の海の青緑
WY, BED, DEEP = -1, -5, -9

# ================================================================ 1. 地形：浅い海、北の浜と白い丘、岩の小島、船の通る深い水路
ISLETS = [(-58, 58, 7, 4, True), (66, 70, 5, 14, False), (-80, 10, 6, 12, False)]   # x, z, 半径, 高さ, 灯台
def nat(x, z):
    for (sx, sz, sr, sh, _) in ISLETS:
        d = math.hypot(x - sx, z - sz) + (vnoise(x, z, 3, 21) - 0.5) * 2
        if d < sr: return sh - max(0, d - sr + 3) * 1.2
    if z < -30:
        b = -30 - z
        h = -1 + b * 0.3 + (vnoise(x, z, 9, 4) - 0.5) * 1.2
        if b > 20: h += min(14, (b - 20) * 0.7) * (0.5 + vnoise(x, z, 24, 6))
        return h
    if 30 <= x <= 52 and -40 < z < 60: return DEEP                                # 船の水路（座礁しない深さ）[7EX7a 17:53]
    return BED - (vnoise(x, z, 12, 3) - 0.5) * 1.5
def in_sea(x, z): return nat(x, z) < -1.2

T = Town((-100, 100, -64, 100), nat, in_sea)
def ground_south(x, z, k, g, sand=lambda x, z, g: False):
    if k == 'river': return full('sand') if g > DEEP + 1 else full('gravel')
    if k == 'nat' and g < 6: return full('sand')
    if k == 'nat': return TW.grass() if vnoise(x, z, 6, 9) > 0.45 else full('calcite')
    return TW.Town.ground_top(T, x, z, k, g, sand)
T.ground_top = ground_south
T.rock_at = lambda x, y, z: ('calcite', 'diorite', 'calcite', 'sandstone')[int(y * 0.5 + vnoise(x, z, 9, 2) * 3) % 4]
T.steep_top = lambda x, z: full('calcite') if vnoise(x, z, 5, 67) < 0.6 else full('diorite')
T.street([(0, -60, 12), (0, -40, 2), (0, -31, 1)], 2, 'lane')
T.resolve(blend_kinds=('lane', 'lane_edge'))
T.fill()
T.steps()
for (x, z), k in list(T.KIND.items()):                                          # 水底の砂の上に水を満たす（水路も）
    if k == 'river':
        for y in range(T.H[(x, z)] + 1, WY + 1):
            if get(x, y, z) is None: put(x, y, z, vox.water())

# ================================================================ 2. 真ん中の丸い広場（石の護岸）と土手道
PX, PZ, PR = 0, 4, 14
edge = HB.round_quay(PX, PZ, PR, 2, WY, BED - 1)
for i in range(-PR, PR + 1):                                                     # 広場の床：白い石のタイル（滑らかな石・砂岩）
    for j in range(-PR, PR + 1):
        d = math.hypot(i, j)
        if d <= PR - 0.7:
            r = _h(PX + i, PZ + j, 41)
            put(PX + i, 2, PZ + j, full('smooth_stone') if r < 0.45 else full('cut_sandstone', top='sandstone_top') if r < 0.8 else full('chiseled_stone_bricks') if d < 3 else full('stone_bricks'))
T.floor_marks(PX, PZ, 2)
OPEN = lambda x, z: abs(x - PX) <= 2 or abs(z - PZ) <= 2                           # 東西南北の口
for (x, z) in edge:                                                              # 手すり：縁の内側に柵、口は空ける
    if not OPEN(x, z) and get(x, 3, z) is None: put(x, 3, z, fence('spruce_planks'))
# 土手道：浜 → 広場の北の口。芯は石レンガ幅3（+2）、両脇に木の足場幅2（+1）
for z in range(-31, PZ - PR + 1):
    for x in range(-2, 3):
        if abs(x) <= 1:
            for y in range(BED - 1, 1): put(x, y, z, full('stone_bricks'))
            put(x, 1, z, full('stone_bricks' if (x + z) % 3 else 'polished_andesite'))
        else:
            if get(x, 0, z) is None or get(x, 0, z)['s'] == 'water': put(x, 0, z, full('spruce_planks'))
    for x in (-3, 3):
        if z % 4 == 0: HB.pile_post(x, z, BED - 1, 0)
        elif get(x, 1, z) is None: put(x, 1, z, fence('spruce_planks'))
for x in (-1, 0, 1): put(x, 2, PZ - PR, stair('stone_bricks', 's'))             # 広場へ上がる1段

# ================================================================ 3. 西：高床の茅葺きの家の群れと板の足場
WEST = [(-46, -14, 9, 7, 's', 'spruce', 'birch_planks', 1), (-34, -16, 7, 7, 's', 'dark_oak', 'calcite', 2), (-58, -2, 7, 9, 'e', 'oak', 'mushroom_stem', 1),
        (-46, 14, 9, 7, 'n', 'mangrove', 'birch_planks', 1), (-32, 18, 7, 7, 'n', 'spruce', 'sandstone', 2), (-62, 20, 7, 7, 'e', 'dark_oak', 'calcite', 1)]
HB.boardwalk(-50, -1, -16, 3, WY, BED - 1)                                        # 家々の間の板の道（東西）
HB.boardwalk(-50, -6, -46, 12, WY, BED - 1)                                       # 南北の枝
for x in range(-16, -13):                                                        # 広場の西の口へ：板から石へ2段
    put(x, 0, PZ - 1, full('spruce_planks')); put(x, 0, PZ, full('spruce_planks')); put(x, 0, PZ + 1, full('spruce_planks'))
for j in (-1, 0, 1): put(-PR - 1, 1, PZ + j, stair('stone_bricks', 'e')); put(-PR - 2, 0, PZ + j, full('spruce_planks'))
for (x0, z0, w, d, f, fr, inf, fl) in WEST: HB.stilt_house(x0, z0, w, d, WY, f, fr, inf, fl, BED - 1, seed=x0)

# ================================================================ 4. 東：船着き場・帆船・クレーン・荷
HB.boardwalk(PR + 1, -14, 29, 24, WY, BED - 1)                                    # 広場の東から水路まで
for j in (-1, 0, 1): put(PR + 1, 1, PZ + j, stair('stone_bricks', 'w'))
HB.ship(41, -34, WY, 44)
HB.crane(27, -8, 0, 'e', 6, 9)
HB.crane(27, 14, 0, 'e', 6, 9)
for (x, z, f) in [(19, -12, 's'), (22, -4, 'w'), (19, 18, 'n'), (23, 22, 'w')]: P.cargo(x, z, 0, f)
for (x, z) in [(26, 2), (26, 6)]:                                                # タラップ（船の脇へ渡す板）
    for i in range(29, 36): put(i, 1 + (i - 29) // 3, z, slab('spruce_planks')) if False else None

# ================================================================ 5. 南：集会所（2階建ての高床）と広場からの道、南西の灯台
for z in range(PZ + PR, 34):
    for x in range(-2, 3):
        if abs(x) <= 1:
            for y in range(BED - 1, 2): put(x, y, z, full('stone_bricks'))
            put(x, 2, z, full('smooth_stone' if (x + z) % 3 else 'stone_bricks'))
        elif get(x, 3, z) is None: put(x, 3, z, fence('spruce_planks'))
HB.stilt_house(-9, 34, 19, 11, WY, 'n', 'dark_oak', 'calcite', 2, BED - 1, seed=7)
sx, sz, sr, sh, _ = ISLETS[0]
HB.lighthouse(sx, sz, int(T.h(sx, sz)), 18, 3)

# ================================================================ 6. 広場の屋台・荷・小舟
for (x, z, f, c) in [(-8, -6, 's', 'red_wool'), (5, -6, 's', 'white_wool'), (-9, 12, 'n', 'white_wool'), (6, 12, 'n', 'red_wool')]:
    P.stall(x, z, 2, f, c, 'white_wool' if c != 'white_wool' else 'red_wool', [P.BARREL_OPEN, P.MELON])
for (x, z, f) in [(-11, 1, 'e'), (10, 1, 'w')]: P.cargo(x, z, 2, f)
for (x, z, al) in [(-30, 8, 'x'), (-24, -6, 'z'), (8, -20, 'z'), (-10, 30, 'x')]: P.boat(x, z, WY, al, P.CRATE)
for (x, z) in [(PX + 10, PZ + 10), (PX - 10, PZ + 10), (PX + 10, PZ - 10), (PX - 10, PZ - 10)]: P.lamp_post(x, z, 2, 'e')

# ================================================================ 7. 書き出し
V = lambda x, y, z: [-x, y, z]
out = save('harbor_town', {
    'over': [V(80, 50, 90), V(-6, 6, 4)],
    'west': [V(-90, 40, 60), V(-20, 4, 4)],
    'arrive': [V(0, 10, -46), V(0, 5, 0)],
    'plaza': [V(-4, 4, -6), V(4, 6, 24)],
    'houses': [V(-20, 3, 1), V(-46, 6, -6)],
    'dock': [V(18, 3, 22), V(36, 12, -8)],
    'hall': [V(0, 4, 18), V(0, 8, 40)],
    'light': [V(-36, 10, 40), V(-58, 14, 58)],
    'top': [V(0, 220, 10), V(0, 0, 11)],
    'night': [V(80, 50, 90), V(-6, 6, 4), {'night': True}],
}, V(0, 4, 4))
print('書き出し', out)
