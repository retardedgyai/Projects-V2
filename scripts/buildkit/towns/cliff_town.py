#!/usr/bin/env python3
"""崖の上の町（11/03）。らーなろっそ「廃キャンプを冒険者の街に」（oz7FHk64-IU）の作りを、部品集（kit/town.py）で組んだ例。

新しい町を作る時は、このファイルを写して「地形の式」と「並べ方」だけを書き換える。
  1. 地形の式：川・崖・台地・谷の高さ（nat）。
  2. 段：広場・家・館の前・大階段・小道・崖の段・歩道。
  3. resolve → 沢 → fill → 滝と蔦 → steps。
  4. 構造物：広場の縁・火・旗・門・桟敷・橋・城壁。
  5. 小物 → 畑と小屋 → 森と草 → 検査 → save。
実行：python3 towns/cliff_town.py（out/cliff_town/ に書き出す）。見るのは README の「見る・撮る」。
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save, RED, BROWN, SLATE, WHITE
from vox import put, get, full, water, vnoise, _h, LANTERN
import props as P

# ================================================================ 1. 地形の式：川・崖・台地・山・東の谷 [地形1・2・3]
def river_c(x): return 46 + 4 * math.sin(x / 15.0) + 2 * math.sin(x / 7.0 + 1)
def in_river(x, z): return abs(z - river_c(x)) <= 4.5
def cliff_top(x): return river_c(x) - 8 - 3 * vnoise(x, 0, 6, 3) - 2 * vnoise(x, 0, 17, 4)   # 北岸の崖の上の縁（出入りを付ける）
RAV = lambda z: 68 + 3 * math.sin(z / 10.0)                                     # 東の谷の中心
WF_X = -46                                                                       # 西の滝の沢
PY, HY = 16, 24                                                                  # 広場と館の高さ
def plateau(x, z):
    d = cliff_top(x) - z
    h = 15 + d * 0.06 + max(0, -38 - z) * 0.7 + max(0, abs(x) - 70) * 0.4
    return h + (vnoise(x, z, 22, 7) - 0.5) * 4.0 + (vnoise(x, z, 8, 9) - 0.5) * 1.4
def nat(x, z):
    rc = river_c(x)
    if z > rc + 4.5:                                                             # 南岸：低い草地、奥は森の丘
        d = z - rc - 4.5
        h = 0.3 + d * 0.13 + max(0, d - 18) * 0.5 + (vnoise(x, z, 14, 5) - 0.5) * 2.0
    elif z > cliff_top(x):                                                       # 崖の面：急さを場所で変え、ところどころ岩棚
        ct = cliff_top(x)
        h = plateau(x, ct) - (z - ct) * (2.4 + 3.5 * vnoise(x, 0, 5, 19))
        if vnoise(x, z, 4, 23) > 0.66: h = math.floor(h / 4) * 4 + 1
        h = max(-1.0, h)
    else:
        h = plateau(x, z)
    dx = abs(x - RAV(z))
    if z < rc - 4 and dx < 12: h = min(h, 4 + dx * 1.3 + (vnoise(x, z, 5, 13) - 0.5) * 1.5)
    return h

T = Town((-92, 104, -92, 84), nat, in_river)

# ================================================================ 2. 段
PCX, PCZ, PR = 0, int(cliff_top(0)) - 4, 13
T.pad_disc(PCX, PCZ, PR, PY, 'plaza')                                            # 崖の外へ張り出す丸い皿 [冒険者の街 2:30]
HOUSES = [
    ('lhouse', 180, (-3, -14), HY, None, False, '館'),
    ('fancy', 0, (-34, 6), None, None, False, '盛り西'),
    ('timber', 0, (-78, -14), None, RED, False, 'W1'),
    ('timber', 180, (-34, -16), None, WHITE, False, 'W3'),
    ('smithy', 90, (-47, -4), None, None, False, '鍛冶屋'),
    ('fancy', 0, (30, 2), None, {'mangrove_planks': 'dark_oak_planks', 'cherry_planks': 'mangrove_planks', 'birch_planks': 'calcite'}, True, '盛り東'),
    ('timber', 180, (36, -22), None, RED, True, 'E2'),
    ('timber', 0, (48, 14), None, BROWN, False, 'E3'),
    ('timber', 270, (80, 4), None, WHITE, True, '丘'),
    ('timber', 0, (-24, 66), None, BROWN, True, 'S1'),
]
for (name, r, at, y, sw, mi, label) in HOUSES: T.house(name, r, at, y, sw, mi, label)
hx0, hx1, hz0, hz1 = T.FOOT[0][1]
T.pad(hx0 - 4, hx1 + 4, hz0 - 3, hz1 + 4, HY, 'hall', keep=True)                 # 館の前の段
ST_Z0 = PCZ - PR
GATE_Z = T.grand_stair(3, ST_Z0, PY, HY)                                         # 大階段 [冒険者の街 7:57]
for z in range(T.stair_top_z - 1, hz1 + 3, -1):
    for x in range(-3, 4): T.PAD.setdefault((x, z), (HY, 'hall'))
T.lane([(-PR - 1, PCZ - 2, PY), (-20, PCZ - 4, None), (-28, 22, None), (-34, 16, None), (-44, 12, None), (-52, 8, None), (-60, 2, None), (-66, -8, None)])
T.lane([(-28, 22, None), (-26, 8, None), (-24, 4, None)])
T.lane([(-44, 12, None), (-40, -2, None), (-38, -8, None), (-48, -20, None), (-58, -26, None)])
T.lane([(PR + 1, PCZ - 2, PY), (20, PCZ - 4, None), (30, 16, None), (36, 10, None), (42, 0, None), (40, -6, None), (34, -18, None), (26, -28, None)])
T.lane([(42, 0, None), (48, 6, None), (54, 4, None), (57, 4, None)])
T.lane([(hx0 - 4, hz0 + 4, HY), (hx0 - 12, hz0, None), (-38, -8, None)])
T.lane([(hx1 + 4, hz0 + 4, HY), (hx1 + 12, hz0, None), (34, -18, None)])
edge = lambda x: int(math.ceil(river_c(x) - 4.5)) - 1                            # 川の北の縁の歩道の列
for sgn in (-1, 1): T.cliff_descent(sgn * (PR + 1), PCZ + 1, PY, sgn, lambda x: edge(x) - 3)   # 皿の両脇から崖を下りる
T.harbor_walk(range(-50, 51), edge, skip=lambda x, z: abs(x - PCX) <= PR)       # 皿の下は擁壁なので歩道を通さない

# ================================================================ 3. 高さ・沢・地面
T.resolve(no_blend=lambda x, z: z > cliff_top(x) - 1)                            # 崖の面はなじませない
H, KIND = T.H, T.KIND
prev = 99                                                                        # 東の谷の沢：水面は下るだけ
for z in range(-70, int(river_c(68)) - 3):
    sx = int(round(RAV(z)))
    s = max(-1, min(prev, min(H.get((sx + d, z), 99) for d in (-1, 0, 1)) - 1)); prev = s
    for d in (-1, 0, 1):
        if KIND.get((sx + d, z)) == 'nat': H[(sx + d, z)] = s - 1; KIND[(sx + d, z)] = 'stream'; T.stream_level[(sx + d, z)] = s
WF = []                                                                          # 西の沢：台地を南へ、崖から川へ落ちる
for z in range(-60, int(cliff_top(WF_X)) + 1):
    sx = WF_X + int(round(2 * math.sin(z / 8.0)))
    if KIND.get((sx, z)) != 'nat': continue
    g = H[(sx, z)]; WF.append((sx, z, g))
    for d in (0, 1):
        if KIND.get((sx + d, z)) == 'nat': H[(sx + d, z)] = g - 2; KIND[(sx + d, z)] = 'brook'
T.fill(sand=lambda x, z, g: g <= 1 and z > river_c(x))
wx = WF[-1][0] if WF else WF_X                                                   # 滝
for xx in (wx, wx + 1):
    zt = int(cliff_top(xx)) + 1; top = T.h(xx, zt - 1)
    for z in range(zt - 1, int(river_c(xx) - 4.5)):
        if KIND.get((xx, z)) not in ('nat', 'brook'): continue
        for y in range(0, top + 1):
            if get(xx, y, z) is None or get(xx, y, z)['s'] == 'full' and y > T.h(xx, z): put(xx, y, z, water())
T.vines_on_cliffs(lambda x, z: cliff_top(x) - 2 <= z <= river_c(x))
T.steps()

# ================================================================ 4. 構造物
T.plaza_rim(PCX, PCZ, PR, PY, open_cell=lambda x, z: abs(x) <= 3 and z < PCZ)
T.fire_pit(PCX, PCZ, PY)
T.floor_marks(PCX, PCZ, PY)
for x in (-5, 5): T.banner_post(x, ST_Z0 + 1, PY)                               # 縦長の赤い旗 [冒険者の街 16:52]
T.arch_gate(GATE_Z, T.PAD[(0, GATE_Z)][0])
DECK_Y = PY + 4
T.deck(-22, -10, ST_Z0 - 10, ST_Z0, DECK_Y)                                     # 柱で支えた桟敷
P.cargo(-20, ST_Z0 - 9, DECK_Y, 's')
P.stall(-14, ST_Z0 - 2, DECK_Y, 'n', 'orange_wool', 'white_wool', [P.BARREL_OPEN, P.HAY])
RBX = -22                                                                        # 川の石橋：船着き場 → 南岸の家
za = max(z for z in range(0, 80) if (RBX, z) in T.HARBOR) + 1
zb = int(math.ceil(river_c(RBX) + 4.5)) + 1
yb = T.stone_bridge(RBX, za, zb)
S1 = [f for f in T.FOOT if f[0] == 'S1'][0]
T.ramp(list(range(zb + 1, S1[1][2] - 1)), range(RBX - 1, RBX + 2), yb, S1[2])
BZ = 6                                                                           # 東の谷の長い木の橋 → 丘の家
HILL = [f for f in T.FOOT if f[0] == '丘'][0]
bx0 = max(x for x in range(30, 70) if KIND.get((x, BZ)) in ('nat', 'lane', 'lane_edge', 'yard') and T.h(x, BZ) > 10 and x < RAV(BZ) - 8)
T.timber_bridge(bx0, HILL[1][0] - 2, BZ, T.h(bx0, BZ), HILL[2])
T.curtain_wall(range(-56, 57), lambda x: int(round(-48 + 5 * math.sin(x / 13.0))))   # 館の裏の稜線の城壁

# ================================================================ 5. 小物
STALLS = [(-9, -5, 'e', 'purple_wool'), (-11, 3, 'e', 'yellow_wool'), (9, -6, 'w', 'lime_wool'), (11, 2, 'w', 'light_blue_wool'), (-6, 9, 'n', 'red_wool')]
GOODS = [[P.MELON, P.PUMPKIN], [P.HAY, P.BARREL_OPEN], [P.BARREL_OPEN, P.CRATE], [P.CRATE, P.MELON], [P.PUMPKIN, P.HAY]]
for (x, z, f, c), gd in zip(STALLS, GOODS): T.try_place(P.stall, (PCX + x, PCZ + z, PY, f, c, 'white_wool', gd), PY)
for (x, z, f) in [(-11, -2, 'e'), (10, 6, 'w'), (4, 10, 'n')]: T.try_place(P.cargo, (PCX + x, PCZ + z, PY, f), PY)
for (x, z) in [(-5, -7), (5, -7)]: T.try_place(P.bench, (PCX + x, PCZ + z, PY, 's', 2), PY)
for x in (-36, -20, 18, 32):                                                     # 桟橋・杭の灯り・小舟・荷 [冒険者の街 15:07]
    zq = max((z for z in range(0, 80) if (x, z) in T.HARBOR), default=None)
    if zq is not None: T.PIERS.update(P.pier(x, zq + 1, -1, -4, 's', 3, 2))
for x in range(-42, 42, 5):
    zq = max((z for z in range(0, 80) if (x, z) in T.HARBOR), default=None)
    if zq is None or any(abs(x - px) <= 3 for px in (-36, -20, 18, 32)): continue
    if KIND.get((x, zq + 1)) == 'river' and get(x, 1, zq) is None: P.pile_lamp(x, zq + 1, -3, 0)
for (x, along, load) in [(-30, 'x', P.CRATE), (-12, 'x', None), (24, 'x', P.BARREL), (38, 'z', None)]:
    zq = max((z for z in range(0, 80) if (x, z) in T.HARBOR), default=None)
    if zq is None: continue
    cs = T.cells_of(P.boat, x, zq + 2, -1, along, load)
    if all(KIND.get(c) == 'river' and c not in T.PIERS for c in cs): P.boat(x, zq + 2, -1, along, load)
for x in (-40, -26, 26):
    zq = min((z for z in range(0, 80) if (x, z) in T.HARBOR), default=None)
    if zq is not None: T.try_place(P.cargo, (x, zq, 0, 's'), 0, allow=('harbor',))
print('畑', T.fields(range(-30, 30, 10), range(hz0 - 22, hz0 - 4, 7)))
print('庭の物', T.yard_extras(skip=('館',)))

# ================================================================ 6. 木と草
def tree_kind(x, z, r):
    if z > 60: return 'oak' if r < 0.6 else 'birch'
    return 'cherry' if r < 0.12 else 'birch' if r < 0.3 else 'spruce'
print('trees', T.forest(skip=lambda x, z: cliff_top(x) - 3 < z < river_c(x) + 6, kind=tree_kind))   # 崖の面と川岸は岩と水を見せる
for (x, z, lv, lg) in [(-16, PCZ + 6, 'cherry_leaves', 'cherry_log'), (hx0 - 6, hz1 + 2, 'oak_leaves', 'oak_log'), (hx1 + 6, hz1 + 2, 'birch_leaves', 'birch_log'), (-44, 20, 'oak_leaves', 'oak_log'), (40, 18, 'cherry_leaves', 'cherry_log')]:
    if KIND.get((x, z)) in ('nat', 'yard'): T.blob_tree(x, z, 6, lv, lg, 3.0)
T.greens()

# ================================================================ 7. 検査と書き出し
T.walk_check((PCX, PCZ - 7))
V = lambda x, y, z: [-x, y, z]                                                   # viewer は x を鏡写しにするので、視点は V() で書く
out = save('cliff_town', {
    'overview': [V(10, 60, 110), V(0, 10, 20)], 'angle': [V(-60, 70, 150), V(0, 14, 10)], 'top': [V(0, 240, 20), V(0, 0, 0)],
    'cliff': [V(0, 22, 95), V(0, 10, 40)], 'plaza': [V(0, 26, 52), V(0, 17, 26)], 'hall': [V(0, 30, 8), V(0, 24, -14)],
    'harbor': [V(-52, 7, 52), V(-25, 2, 42)], 'bridge': [V(56, 30, 20), V(76, 14, 4)], 'west': [V(-26, 28, 34), V(-46, 18, 0)],
    'night': [V(10, 60, 110), V(0, 10, 20), {'night': True}], 'nightplaza': [V(0, 26, 52), V(0, 17, 26), {'night': True}],
}, V(0, 12, 20))
print('書き出し', out)
