#!/usr/bin/env python3
"""開拓港（11/04）。藍「港町なんだけどタンジアみたいにできる？」→「一回やってみて」。

写さずに、港町が居心地よい理由だけを使う（調べた決まり）：
  1. 岬に一列：付け根は「作る・買う」（工房・風車・市場）、先は「集まる・受ける」（集会所の広場）。歩く向きに意味がある。
  2. 遠くから見える目印が1つ：海に離れて立つ岩の上の灯台。開拓者が最初に灯した火という理由を持たせる。
  3. 道が市場になる：通りの両脇に屋台、頭の上に日よけの布。
  4. 三方が海：どこからでも視線が海へ抜ける。西の崖下に船着き場。
色は3つに絞る：白（方解石）・赤茶の屋根（赤い砂岩）・暗い木。日よけは白と橙。

座標：北（-z）が本土で、岬は南へ突き出す。海面 -1、船着き場 0、岬の上は 10〜13。
実行：python3 towns/harbor_town.py（out/harbor_town/ に書き出す）。
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
from vox import put, get, full, log, stair, slab, trap, fence, cross, vnoise, LANTERN
import props as P

# ================================================================ 1. 地形：本土・岬・西の船着き場の段・灯台の岩
TIP_C, TIP_R = 78, 30                      # 岬の先の丸み（中心 z と半径）
STACK = (46, 96, 6)                        # 灯台の岩（x, z, 半径）
def hw(z):                                  # 岬の半分の幅
    w = 30 - 0.03 * max(0, z + 10) + (vnoise(0, z, 14, 3) - 0.5) * 3
    if z > TIP_C: w = min(w, math.sqrt(max(0, TIP_R * TIP_R - (z - TIP_C) ** 2)))
    return w
def top(x, z): return 10 + 0.03 * z + (vnoise(x, z, 18, 5) - 0.5) * 2.5
QUAY = lambda z: 0 <= z <= 74               # 西の崖下の船着き場の段がある所
def nat(x, z):
    if z < -14: return 10 + (vnoise(x, z, 20, 7) - 0.5) * 3 + max(0, -40 - z) * 0.2     # 本土
    sx, sz, sr = STACK
    ds = math.hypot(x - sx, z - sz)
    if ds < sr + 4: return max(-4, 12 - max(0, ds - sr) * 4 + (vnoise(x, z, 3, 9) - 0.5) * 2)
    w = hw(z); d = abs(x) - w
    if d <= 0: return top(x, z)
    if x < 0 and QUAY(z):                    # 西：崖を下りると板の段、その先が海
        if d < 4: return top(x, z) - d * 3
        if d < 10: return 0.2
        return -4
    if d < 5: return top(x, z) - d * (2.6 + 2 * vnoise(x, z, 4, 11))
    return -4
def in_sea(x, z): return nat(x, z) < -1 and z >= -14

T = Town((-60, 64, -40, 124), nat, in_sea)

# ================================================================ 2. 段：通り・広場・家
SY = 11                                                                          # 通りの高さ
T.street([(0, -36, 10), (0, -16, SY), (0, 58, SY + 1)], 3, 'lane')              # 本土から岬の先へ一本の通り
PCX, PCZ, PR = 0, 70, 12
T.pad_disc(PCX, PCZ, PR, SY + 1, 'plaza')                                        # 先の広場（集会所の前）
SOUTH = {'bricks': 'red_sandstone', 'mangrove_planks': 'red_sandstone', 'red_nether_bricks': 'red_sandstone', 'birch_planks': 'calcite',
         'granite': 'calcite', 'cobblestone': 'stone_bricks', 'mossy_cobblestone': 'mossy_stone_bricks'}
HOUSES = [
    ('mill', 90, (-9, 0), SY, SOUTH, False, '風車小屋'),         # 付け根：作る（戸は通りを向く。屋台を戸の前に置く幅を空ける）
    ('smithy', 270, (9, 4), SY, SOUTH, False, '工房'),
    ('timber', 90, (-9, 31), SY + 1, SOUTH, False, '西の家'),
    ('fancy', 90, (-9, 50), SY + 1, SOUTH, False, '西の家2'),
    ('timber', 270, (9, 30), SY + 1, SOUTH, True, '東の家'),
    ('fancy', 270, (9, 50), SY + 1, SOUTH, True, '東の家2'),
    ('lhouse', 0, (0, 84), SY + 1, SOUTH, False, '集会所'),      # 先：集まる
]
for (name, r, at, y, sw, mi, label) in HOUSES: T.house(name, r, at, y, sw, mi, label)
T.lane([(-3, 19, SY), (-14, 19, None), (-22, 19, None)])                         # 通りから西の崖の上へ（船着き場へ下りる階段の上）

# ================================================================ 3. 高さ・地面
T.resolve()
H, KIND = T.H, T.KIND
T.fill(sand=lambda x, z, g: g <= 1 and not (x < 0 and QUAY(z) and abs(x) - hw(z) < 10))
for (x, z), k in list(KIND.items()):                                             # 船着き場の段は板張り
    if k == 'nat' and x < 0 and QUAY(z) and 4 <= abs(x) - hw(z) < 10 and H.get((x, z)) == 0:
        put(x, 0, z, full('spruce_planks')); KIND[(x, z)] = 'harbor'; T.HARBOR.add((x, z))
for (x, z), k in list(KIND.items()):                                             # 岬の上は人が歩く町：踏み固めた土と石を草に混ぜる
    if k in ('nat', 'yard') and -14 < z and abs(x) < hw(z) - 3 and H.get((x, z), 0) >= SY - 1:
        n = vnoise(x, z, 5, 41)
        if n < 0.42: put(x, H[(x, z)], z, full('coarse_dirt' if n < 0.2 else 'packed_mud'))
        elif n < 0.5: put(x, H[(x, z)], z, full('gravel'))
T.vines_on_cliffs(lambda x, z: z > -14 and abs(x) - hw(z) > 0 and H.get((x, z), 0) > 2, 0.25)
T.steps()

# 西の崖を下りる石段：(−24, 25) から西へ、1マスで1段
zq = 19; y = H.get((-22, zq), SY); x = -23
while y > 0 and x > -60:
    y -= 1
    for z in (zq - 1, zq, zq + 1):
        for yy in range(y + 1, y + 5): put(x, yy, z, None)
        for yy in range(-3, y): put(x, yy, z, full('stone_bricks'))
        put(x, y, z, stair('stone_bricks', 'e'))
        H[(x, z)] = y; KIND[(x, z)] = 'cliffstair'
    for z in (zq - 2, zq + 2):
        for yy in range(-3, y + 2): put(x, yy, z, full('stone_bricks' if yy <= y else 'stone_brick_wall' if False else 'stone_bricks'))
        put(x, y + 2, z, slab('stone_bricks'))
    x -= 1

# ================================================================ 4. 構造物：灯台・灯台への橋・広場・門
sx, sz, sr = STACK
gs = 13
for i in range(-sr - 3, sr + 4):                                                  # 岩の上をならす
    for j in range(-sr - 3, sr + 4):
        if math.hypot(i, j) <= sr:
            for yy in range(-4, gs + 1): put(sx + i, yy, sz + j, full('stone' if yy < gs else 'mossy_cobblestone' if (i * j) % 3 == 0 else 'cobblestone'))
            for yy in range(gs + 1, gs + 8): put(sx + i, yy, sz + j, None)
            H[(sx + i, sz + j)] = gs; KIND[(sx + i, sz + j)] = 'yard'
P.lighthouse(sx, sz, gs, 4, 22)
bx0 = max(x for x in range(0, 40) if KIND.get((x, sz)) in ('nat', 'plaza', 'yard') and H.get((x, sz), -9) >= SY - 1)   # 岬の東の縁から岩へ木の橋
T.timber_bridge(bx0, sx - sr - 1, sz, H[(bx0, sz)], gs + 2); print('灯台への橋', bx0, sx - sr)
T.plaza_rim(PCX, PCZ, PR, SY + 1, open_cell=lambda x, z: abs(x) <= 3)
T.floor_marks(PCX, PCZ, SY + 1)
P.well(PCX, PCZ, SY + 1, 's') if hasattr(P, 'well') else None
T.arch_gate(-14, T.PAD.get((0, -14), (SY, ''))[0])

# 日よけ：通りの上に、柱から柱へ渡した布（白と橙）。柱は通りの縁、布はトラップドアの上の絨毯
def canopy(z0, z1, y, c1='white_wool', c2='orange_wool'):
    """通りの上の布の日よけ：縁の柱から、羊毛の帆を切妻に張る（真ん中の3列が1段高い）。縞は z 方向に2マスずつ。"""
    for z in (z0, z1):
        for x in (-4, 4):
            for yy in range(T.h(x, z) + 1, y + 1): put(x, yy, z, log('stripped_spruce_log'))
    for z in range(z0, z1 + 1):
        c = c1 if (z // 2) % 2 else c2
        for x in range(-4, 5):
            if abs(x) == 4 and z in (z0, z1): continue
            put(x, y + (1 if abs(x) <= 1 else 0), z, full(c))
        if (z - z0) % 4 == 2: put(0, y, z, P.HANG)
for (z0, z1) in ((-10, 2), (6, 16), (24, 40)): canopy(z0, z1, SY + 6)

# ================================================================ 5. 小物：屋台・荷・桟橋・小舟・灯り
STALLS = [(-7, -12, 'e', 'orange_wool'), (-7, 6, 'e', 'white_wool'), (7, -8, 'w', 'orange_wool'), (7, 14, 'w', 'white_wool'), (-7, 22, 'e', 'orange_wool'), (7, 38, 'w', 'white_wool')]
GOODS = [[P.MELON, P.PUMPKIN], [P.HAY, P.BARREL_OPEN], [P.BARREL_OPEN, P.CRATE], [P.CRATE, P.MELON], [P.PUMPKIN, P.HAY]]
for (x, z, f, c), gd in zip(STALLS, GOODS): T.try_place(P.stall, (x, z, SY, f, c, 'white_wool', gd), SY)
for (x, z, f) in [(-5, 30, 'e'), (5, 46, 'w'), (-9, PCZ + 2, 'e')]: T.try_place(P.cargo, (x, z, SY + 1, f), SY + 1)
for (x, z) in [(-6, PCZ - 7), (6, PCZ - 7)]: T.try_place(P.bench, (x, z, SY + 1, 's', 2), SY + 1)
for z in range(-10, 70, 12):                                                      # 通りの灯り
    for x in (-4, 4):
        if KIND.get((x, z)) in ('lane', 'lane_edge') and get(x, T.h(x, z) + 1, z) is None: P.lamp_post(x, z, T.h(x, z), 'e' if x < 0 else 'w')
PIER_Z = (6, 28, 46, 64)
for z in PIER_Z:                                                                  # 桟橋：船着き場の段から西の海へ
    xe = min((x for x in range(-60, 0) if (x, z) in T.HARBOR), default=None)
    if xe is not None: T.PIERS.update(P.pier(xe - 1, z, -1, -4, 'w', 7, 3))
for (z, load) in [(14, P.CRATE), (38, None), (60, P.BARREL)]:
    xe = min((x for x in range(-60, 0) if (x, z) in T.HARBOR), default=None)
    if xe is None: continue
    cs = T.cells_of(P.boat, xe - 6, z, -1, 'z', load)
    if all(KIND.get(c) == 'river' and c not in T.PIERS for c in cs): P.boat(xe - 6, z, -1, 'z', load)
for z in range(4, 74, 10):
    xe = max((x for x in range(-60, 0) if (x, z) in T.HARBOR), default=None)
    if xe is not None: T.try_place(P.cargo, (xe, z, 0, 'w'), 0, allow=('harbor',))
print('庭の物', T.yard_extras(skip=('集会所',)))

# ================================================================ 6. 木と草：岬の上は少なく（海を見せる）、本土は林
def tree_kind(x, z, r): return 'oak' if r < 0.7 else 'birch'
print('trees', T.forest(skip=lambda x, z: z > -16, kind=tree_kind))
T.greens(flowers=6000, rocks=600)

# ================================================================ 7. 検査と書き出し
T.walk_check((0, 10))
V = lambda x, y, z: [-x, y, z]
out = save('harbor_town', {
    'arrive': [V(0, 18, -30), V(0, 12, 10)],
    'over': [V(-80, 60, 140), V(0, 10, 45)],
    'sea': [V(-40, 30, 170), V(5, 14, 70)],
    'market': [V(0, 14, -6), V(0, 13, 20)],
    'plaza': [V(0, 16, 54), V(0, 15, 76)],
    'quay': [V(-52, 6, 10), V(-38, 2, 40)],
    'light': [V(16, 30, 60), V(46, 30, 96)],
    'top': [V(0, 220, 40), V(0, 0, 41)],
    'bridge': [V(10, 18, 84), V(40, 16, 96)],
    'nightst': [V(0, 15, -10), V(0, 13, 20), {'night': True}],
    'night': [V(-80, 60, 140), V(0, 10, 45), {'night': True}],
}, V(0, 12, 40))
print('書き出し', out)
