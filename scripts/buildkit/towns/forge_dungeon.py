#!/usr/bin/env python3
"""炎の鍛冶場（11/07、インスタンスダンジョン）。決まりは rules/forge.md（156〜163）。
手本：Enchanted.Architecture「Dwarven Forge Tutorial」（moXALbzpQI4）の左右対称の溶岩の門と炉、
めねず「地下の溶岩工場」（psovPUgArDQ）の、赤レンガの壁・木の梁・機械の列の長い通路。

並び（南から北へ。一人称の近接で戦う）：
  入口の間（7×9）→ 炉の回廊（幅17・長さ40、両脇に炉の列＝遮蔽、真ん中の通路で1波）
  → 溶岩の橋の間（溶岩の溝を3本の石橋で渡る、1波）→ 大鍛冶場（半径15の円、北の壁に溶岩の門、隠れる柱4本、ボス）。
地下なので、上から見る視点は天井を切って見せる（viewer の cut）。
実行：python3 towns/forge_dungeon.py（out/forge_dungeon/）。
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import vox
from vox import put, get, full, log, stair, slab, fence, cross, vnoise, _h, LANTERN
from town import save
import walk3d as WK

def wall(t): return {'s': 'wall', 't': t}
HANG = {'s': 'lantern', 't': 'lantern', 'hang': True}
CHAIN = {'s': 'chain', 't': 'iron_chain'}
LAVA = full('lava_still')
D = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}
def face_of(dx, dz): return max(D, key=lambda k: D[k][0] * dx + D[k][1] * dz)
def blast(f): return {'s': 'full', 't': 'blast_furnace_side', 'top': 'blast_furnace_top', 'bot': 'blast_furnace_top', 'fr': 'blast_furnace_front_on', 'ff': f}
ORANGE = 'orange_terracotta'; COP = 'acacia_planks'                                  # 橙の差し色：塊は橙のテラコッタ、階段とハーフはアカシア（銅は桃色に見えた）

# ================================================================ 1. 部屋の空間（空気の所）。床の上は y=1 から
AC = (0, -34); AR = 15                                                               # 大鍛冶場の中心と半径（歩ける床）
def room(x, y, z):
    """(x, y, z) が空気なら部屋の名前、そうでなければ None。"""
    if -3 <= x <= 3 and 32 <= z <= 40 and 1 <= y <= 6: return '入口の間'
    if -8 <= x <= 8 and -6 <= z <= 31 and 1 <= y <= 12: return '炉の回廊'
    if -2 <= x <= 2 and -9 <= z <= -7 and 1 <= y <= 5: return '門1'
    if -9 <= x <= 9 and -17 <= z <= -10 and 1 <= y <= 10: return '溶岩の橋の間'
    if -2 <= x <= 2 and -19 <= z <= -18 and 1 <= y <= 5: return '門2'
    d = math.hypot(x - AC[0], z - AC[1])
    if d <= AR + 2.5 and 1 <= y <= 24 - max(0, d - 10) * 0.6: return '大鍛冶場'
    return None
X0, X1, Z0, Z1, Y0, Y1 = -20, 20, -56, 44, -3, 24
def rock(x, y, z):
    n = vnoise(x, z + y * 3, 6, 5)
    return 'deepslate' if n < 0.45 else 'tuff' if n < 0.6 else 'cobbled_deepslate' if n < 0.8 else 'basalt_side' if n < 0.9 else 'blackstone'
AIR = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        for y in range(1, Y1):
            r = room(x, y, z)
            if r: AIR[(x, y, z)] = r
for (x, y, z) in list(AIR):                                                          # 殻：空気に触れる所だけ岩を置く（中身は書かない）
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1), (1, 1, 0), (-1, 1, 0), (0, 1, 1), (0, 1, -1)):
        c = (x + dx, y + dy, z + dz)
        if c not in AIR and get(*c) is None: put(*c, full(rock(*c)) if c[1] > 0 else full('polished_blackstone_bricks'))
def room_of(x, z, y=1): return AIR.get((x, y, z))

# 床：部屋ごとに
for (x, y, z), r in AIR.items():
    if y != 1: continue
    h = _h(x, z, 3)
    if r in ('炉の回廊', '入口の間', '門1', '門2'):
        b = 'polished_blackstone_bricks' if abs(x) <= 2 else 'bricks' if h < 0.5 else 'polished_blackstone' if h < 0.8 else 'cracked_polished_blackstone_bricks'
        if abs(x) == 3: b = 'polished_blackstone'
    elif r == '溶岩の橋の間': b = 'polished_blackstone_bricks'
    else:
        d = math.hypot(x - AC[0], z - AC[1])
        b = 'chiseled_polished_blackstone' if d < 2 else 'gilded_blackstone' if abs(d - 6) < 0.5 else 'polished_blackstone_bricks' if h < 0.6 else 'stone_bricks' if h < 0.85 else 'cracked_polished_blackstone_bricks'
    put(x, 0, z, full(b))

# ================================================================ 2. 入口の間と炉の回廊 [161・162]
for z in range(32, 41):                                                              # 入口の間：赤レンガの壁、両脇に灯り
    for x in (-4, 4):
        for y in range(1, 7): put(x, y, z, full('bricks'))
put(0, 5, 36, HANG)
for z in range(-6, 32):                                                              # 回廊の壁は赤レンガ、腰は濃い石
    for x in (-9, 9):
        for y in range(1, 13): put(x, y, z, full('polished_blackstone_bricks' if y <= 2 else 'bricks'))
for z in range(-6, 32, 5):                                                           # 天井の梁（濃い木）と柱、吊った灯り
    for x in range(-8, 9): put(x, 12, z, log('dark_oak_log', 'x'))
    for x in (-8, 8):
        for y in range(1, 12): put(x, y, z, log('dark_oak_log'))
        put(x + (1 if x < 0 else -1), 11, z, stair('dark_oak_planks', 'e' if x < 0 else 'w', 'top'))
    for x in (-4, 4):
        put(x, 11, z, CHAIN); put(x, 10, z, HANG)
for x in range(-8, 9):                                                               # 梁の間の天井板
    for z in range(-6, 32):
        if z % 5 and get(x, 13, z) is None: put(x, 13, z, full('dark_oak_planks'))
def furnace(x, z, side):
    """炉の機械（2×3、高さ4）：濃い石の台、溶鉱炉2つ（焚き口は通路の向き）、橙の帽子、上にレンガの煙突を天井まで、管（銅）。前に金床と水の樽。"""
    f = 'e' if side < 0 else 'w'                                                     # 通路の向き
    for dz in (0, 1, 2):
        for dx in (0, side):
            X = x + dx; Z = z + dz
            put(X, 1, Z, full('polished_blackstone_bricks'))
            put(X, 2, Z, blast(f) if dx == 0 and dz != 1 else full('polished_blackstone_bricks'))
            put(X, 3, Z, full('polished_blackstone_bricks') if dz == 1 or dx else blast(f))
            put(X, 4, Z, full(ORANGE) if dx == 0 else full('bricks'))
    for dz in (0, 1, 2):                                                              # 帽子：上の縁を通路側へ張り出す [159]
        put(x - side, 4, z + dz, stair(COP, 'e' if side > 0 else 'w', 'top'))
        put(x, 5, z + dz, slab(COP))
    for y in range(5, 13): put(x + side, y, z + 1, full('bricks'))                    # 煙突
    put(x - side, 1, z + 1, {'s': 'anvil', 't': 'anvil', 'f': 'n'})                # 金床
    put(x - side, 1, z - 1 if (z // 5) % 2 else z + 3, full('barrel_side', top='barrel_top'))
FURN = []
for z in range(-3, 29, 7):
    for side in (-1, 1):
        x = side * 6
        furnace(x, z, side); FURN.append((x, z))
for z in range(-5, 31):                                                              # 通路の床の灯り（2列、4マスおき）[161]
    if z % 4 == 0:
        for x in (-3, 3): put(x, 0, z, full('shroomlight'))

# ================================================================ 3. 溶岩の橋の間：東西に流れる溶岩の溝を3本の石橋で渡る [156・160]
for x in range(-9, 10):
    for z in range(-16, -11):
        if abs(x) <= 1 or abs(x) in (5, 6): continue                                 # 橋は x=0、±5..6（真ん中は幅3、脇は幅2）
        if z in (-16, -12):                                                           # 溝の縁：濃い石の階段
            put(x, 1, z, stair('polished_blackstone_bricks', 'n' if z == -16 else 's'))
            continue
        put(x, 0, z, LAVA); put(x, -1, z, full('magma'))
for x in range(-9, 10):
    if abs(x) <= 1 or abs(x) in (5, 6):
        for z in range(-15, -12): put(x, 0, z, full('stone_bricks')); put(x, -1, z, stair('stone_bricks', 'n' if z < -14 else 's', 'top'))
        for z in (-16, -12): put(x, 0, z, full('polished_blackstone_bricks'))
for x in (-9, 9):                                                                    # 溶岩が壁の穴から流れ込む
    for y in range(1, 4): put(x, y, -14, LAVA)
for x in (-7, 7, -3, 3):
    for z in (-11, -17): put(x, 1, z, wall('polished_blackstone')); put(x, 2, z, LANTERN)

# ================================================================ 4. 大鍛冶場：半径15の床、外の輪に溶岩の溝と胸壁、北に溶岩の門、隠れる柱4本 [157〜160・163]
for (x, y, z), r in list(AIR.items()):
    if r != '大鍛冶場' or y != 1: continue
    d = math.hypot(x - AC[0], z - AC[1])
    if d > AR + 0.5:                                                                 # 外の輪：溶岩の溝
        put(x, 0, z, LAVA); put(x, -1, z, full('magma'))
    elif d > AR - 0.5 and not (z > AC[1] + 12 and abs(x) <= 2):                     # 胸壁（跳び越せない石の塀）、入口は空ける
        put(x, 1, z, wall('polished_blackstone_bricks'))
        if int(round(math.atan2(z - AC[1], x - AC[0]) * AR)) % 6 == 0: put(x, 2, z, LANTERN)
for zz in range(AC[1] + AR - 1, AC[1] + AR + 4):                                     # 入口の橋（溝の上）
    for x in range(-2, 3): put(x, 0, zz, full('polished_blackstone_bricks'))
def pillar(cx, cz):
    """隠れる柱＝炉の塔（3×3、高さ8）：濃い石の台、溶鉱炉の段、橙の帯、上に帽子と火。 [159・163]"""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for y in range(1, 9):
                b = 'polished_blackstone_bricks' if y <= 2 else ORANGE if y == 6 else 'stone_bricks' if y > 6 else ('bricks' if (dx and dz) else 'stone_bricks')
                put(cx + dx, y, cz + dz, full(b))
            put(cx + dx, 9, cz + dz, slab(COP) if dx and dz else full(COP))
    for f, (dx, dz) in D.items():                                                     # 4面に焚き口
        put(cx + dx, 3, cz + dz, blast(f))
        put(cx + 2 * dx, 6, cz + 2 * dz, stair(COP, {'n': 's', 's': 'n', 'e': 'w', 'w': 'e'}[f], 'top'))
    put(cx, 10, cz, cross('fire_0')); put(cx, 9, cz, full('netherrack'))
PILLARS = []
for a in (45, 135, 225, 315):
    cx, cz = int(round(AC[0] + 10.5 * math.cos(math.radians(a)))), int(round(AC[1] + 10.5 * math.sin(math.radians(a))))
    pillar(cx, cz); PILLARS.append((cx, cz))
# 北の壁の溶岩の門（左右対称）[157]：門の柱は高さ13、口から溶岩が落ちて溝へ
GZ = AC[1] - AR - 2                                                                 # 門の面の z
for sx in (-1, 1):
    for xo in (3, 4):
        x = sx * xo
        for y in range(1, 14): put(x, y, GZ, full('stone_bricks' if y % 4 else ORANGE))
        put(x, 14, GZ, full(COP))
    for y in range(1, 6):                                                             # 脇の炉（低い箱に橙の帽子）
        for xo in (6, 7, 8):
            put(sx * xo, y, GZ, full('stone_bricks' if y < 5 else ORANGE))
    put(sx * 7, 2, GZ + 1, blast('s')); put(sx * 7, 6, GZ, slab(COP))
    for xo in (5, 9): put(sx * xo, 6, GZ + 1, stair(COP, 's', 'top'))
for x in range(-2, 3):                                                               # 門の口：中を溶岩が落ちる
    for y in range(1, 14): put(x, y, GZ, full('stone_bricks') if abs(x) == 2 else LAVA if y <= 11 else full('stone_bricks'))
    put(x, 12, GZ + 1, stair('stone_bricks', 'n', 'top'))
for j in range(0, 6):                                                                # 門の上の段々の三角（橙）
    for x in range(-5 + j, 6 - j): put(x, 14 + j, GZ, full(ORANGE) if abs(x) == 5 - j else full('stone_bricks'))
    put(-5 + j, 14 + j, GZ + 1, stair(COP, 'e')); put(5 - j, 14 + j, GZ + 1, stair(COP, 'w'))
put(0, 16, GZ + 1, full('gold_block'))                                               # 真ん中の印
for y in range(1, 3):                                                                # 門の前の池は溝につながる
    pass

# ================================================================ 5. 検査と台帳
SPAWN = (0, 0, 39)
reach = WK.walk(SPAWN, (X0, X1, Z0, Z1, -2, Y1))
targets = {'炉の回廊': (0, 0, 10), '溶岩の橋の間': (0, 0, -14), '橋（東）': (5, 0, -14), '大鍛冶場の入口': (0, 0, AC[1] + 13), '大鍛冶場の真ん中': (0, 0, AC[1]),
           '柱の陰（北西の後ろ）': (PILLARS[2][0] - 2, 0, PILLARS[2][1] - 2)}
bad = WK.report(reach, targets)
# 柱の陰の数：ボスの目（真ん中、床+2.6）から人の目（床+1.6）へ線を引き、石に当たる床
BLOCK = ('full', 'stair', 'slab', 'wall', 'fence')
eye = (AC[0] + 0.5, 1 + 2.6, AC[1] + 0.5); hid = 0; floor = 0
for (x, y, z) in reach:
    if y != 0 or math.hypot(x - AC[0], z - AC[1]) > AR - 1: continue
    floor += 1
    tx, ty, tz = x + 0.5, 1 + 1.62, z + 0.5; n = int(math.dist(eye, (tx, ty, tz)) * 8)
    if any((lambda b: b is not None and b['s'] in BLOCK)(get(math.floor(eye[0] + (tx - eye[0]) * i / n), math.floor(eye[1] + (ty - eye[1]) * i / n), math.floor(eye[2] + (tz - eye[2]) * i / n))) for i in range(1, n)): hid += 1
print('ボス部屋の床', floor, '柱の陰', hid)
ROOMS = {'spawn': [SPAWN[0], 1, SPAWN[2]], 'arena_center': [AC[0], 1, AC[1]], 'arena_r': AR - 1,
         'rooms': {'入口の間': [0, 36, 3], '炉の回廊': [0, 12, 8], '溶岩の橋の間': [0, -14, 8], '大鍛冶場': [AC[0], AC[1], AR - 1]},
         'gates': {'門1': [0, 1, -8], '門2': [0, 1, -18]}, 'cover': [list(p) for p in PILLARS],
         'order': '入口の間 → 炉の回廊（1波）→ 溶岩の橋の間（1波）→ 大鍛冶場（ボス、柱の陰）'}
V = lambda x, y, z: [-x, y, z]
out = save('forge_dungeon', {
    'over': [V(60, 70, 60), V(0, 0, -6), {'cut': 11.5}],
    'top': [V(0, 160, -5), V(0, 0, -6), {'cut': 11.5}],
    'hall': [V(0, 4, 30), V(0, 3, -4)],
    'bridge': [V(7, 7, -9), V(-4, 0, -16)],
    'arena': [V(0, 5, AC[1] + 13), V(0, 8, GZ)],
    'arena_top': [V(30, 46, AC[1] + 30), V(0, 0, AC[1]), {'cut': 17}],
    'night': [V(0, 5, AC[1] + 13), V(0, 8, GZ), {'night': True}],
    'hall_night': [V(0, 4, 30), V(0, 3, -4), {'night': True}],
}, V(0, 0, -6), shadowR=80, fog=(120, 300))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
