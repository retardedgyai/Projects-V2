#!/usr/bin/env python3
"""散り桜の社（11/06、最初のインスタンスダンジョンの外の地図）。藍「最初は平原とか森みたいなのを基調に、ボスが近づくにつれて桜が咲きほこる感じ」。
手本は Runeforge の「緑の城塞」（~/.minecraft/saves/play.runeforge.fun_minecraft_dgn_verified_verdant_citadel、towns/mc_view.py で見る）：
  - 区域は「縁が高い崖・上に森がかぶさる・中は開けて段になる」器。器と器を細い切り通しと門でつなぐ。
  - 地面は草1色にしない（苔・草・粗い土・ポドゾルのまだら）。崖は石の層を見せる。開けた所に白い石の遺跡のアーチ。
  - 主役の門は大きく（道幅の2倍以上の柱と笠）、器の中には木の桟敷と滝。
部屋の並びは PR #216 の InstanceDungeon（入口の間 → 花守の回廊（2波）→ 散華の間（2波）→ 散り桜の庭（桜牙獣））と同じ。北（-z）から南へ進む。
  A 入口の野      平原。木はまばら、桜なし。
  B 花守の回廊    森の谷。両脇の尾根に広葉樹、桜が混じり始める（1〜3割）。
  C 散華の間      すり鉢。段を下りて底で戦う。桜が半分、花びらが地面に落ち始める。滝と桟敷。
  D 散り桜の庭    ボスの円庭（半径19で戦う）。周りは大きな桜だけ、床は苔と花びら、奥に鳥居と社。
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
import vox
from vox import put, get, full, log, stair, slab, fence, cross, vnoise, _h, LANTERN, water
import trees as TR
import poi as PO
import props as P

# ================================================================ 1. 区域の形（開けた所）と床の高さ
SPINE = [(0, -122), (0, -100), (0, -92), (-9, -76), (7, -60), (0, -48), (0, -38), (0, -12), (0, 14), (0, 28), (0, 50)]
AREAS = {   # 名前: (形, 中心/線, 大きさ, 床の高さの関数)
    'A': ('ellipse', (0, -122), (26, 18)),
    'B': ('capsule', [(0, -92), (-9, -76), (7, -60), (0, -48)], 9.5),
    'C': ('disc', (0, -12), 24),
    'D': ('disc', (0, 50), 25),
}
GATES = [(0, -103, -89, 3.2), (0, -50, -34, 3.6), (0, 10, 27, 4.2)]             # 切り通し：x, z0, z1, 半幅
def _seg_d(x, z, a, b):
    ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az
    t = max(0.0, min(1.0, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)))
    return math.hypot(x - ax - vx * t, z - az - vz * t), t
def shapes(x, z):
    """各区域への「縁からの距離」（中は負）。"""
    wob = (vnoise(x, z, 11, 41) - 0.5) * 6
    out = {}
    cx, cz = AREAS['A'][1]; rx, rz = AREAS['A'][2]
    out['A'] = (math.hypot((x - cx) / rx, (z - cz) / rz) - 1) * min(rx, rz) + wob
    pts = AREAS['B'][1]
    out['B'] = min(_seg_d(x, z, a, b)[0] for a, b in zip(pts, pts[1:])) - AREAS['B'][2] + wob * 0.6
    for k in ('C', 'D'):
        (cx, cz), r = AREAS[k][1], AREAS[k][2]
        out[k] = math.hypot(x - cx, z - cz) - r + wob * (0.5 if k == 'D' else 0.8)
    for i, (gx, z0, z1, hw) in enumerate(GATES):
        out['g%d' % i] = _seg_d(x, z, (gx, z0), (gx, z1))[0] - hw + (vnoise(x, z, 4, 43) - 0.5) * 1.5
    return out
def floor(k, x, z):
    if k == 'A': return 14 + 2.5 * vnoise(x, z, 18, 44) + 1.2 * vnoise(x, z, 6, 45)
    if k == 'B':
        t = (z + 92) / 44.0
        return 14 - 4 * max(0, min(1, t)) + 1.5 * vnoise(x, z, 9, 46)
    if k == 'C':                                                                 # すり鉢：底は半径13で 6、外へ3マスごとに1段
        r = math.hypot(x, z + 12) + (vnoise(x, z, 5, 47) - 0.5) * 2
        return 6 + max(0, int((r - 13) / 3))
    if k == 'D':
        r = math.hypot(x, z - 50)
        return 9 + max(0, r - 21) * 0.5 + 0.6 * vnoise(x, z, 7, 48)
    i = int(k[1]); gx, z0, z1, hw = GATES[i]                                     # 切り通し：両端の区域の床をつなぐ坂
    ends = [(14, 10), (10, 10), (11, 9)][i]
    t = max(0, min(1, (z - z0) / (z1 - z0)))
    return ends[0] + (ends[1] - ends[0]) * t
RIM = {'A': 1.1, 'B': 1.7, 'C': 2.6, 'D': 0.55, 'g0': 3.0, 'g1': 3.0, 'g2': 3.0}   # 縁の外の立ち上がりの急さ（器ほど崖）
def nat(x, z):
    sh = shapes(x, z)
    k = min(sh, key=sh.get); d = sh[k]
    f = floor(k, x, z)
    if d <= 0: return f
    up = d * RIM[k] + (vnoise(x, z, 7, 49) - 0.4) * 4 * min(1, d / 6)
    if vnoise(x, z, 5, 50) > 0.62: up = math.floor(up / 4) * 4 + 1             # 崖の面に岩棚
    cap = 14 + 4 * vnoise(x, z, 20, 53) if k == 'D' else 30 + 6 * vnoise(x, z, 30, 51)   # 庭の周りは低い丘（大きな桜を見せる）
    return f + min(cap, up)
POOL = (17, -20)                                                                 # すり鉢の東の縁の滝壺
def in_water(x, z): return math.hypot(x - POOL[0], z - POOL[1]) < 3.2 + vnoise(x, z, 3, 52)

T = Town((-72, 72, -150, 92), nat, in_water)
H, KIND = T.H, T.KIND

# ================================================================ 2. 床：器の底と門の前を平らに、ボスの円庭は半径21で平らな床
T.pad_disc(0, 50, 21, 9, 'yard')                                                  # 庭（戦う床）
T.resolve(no_blend=lambda x, z: True)
T.fill()
for (x, z), k in list(KIND.items()):                                             # すり鉢の段の縁は石で縁取る [城塞の器の灰色の輪]
    if k != 'nat' or shapes(x, z)['C'] > 1: continue
    g = H[(x, z)]
    if any(H.get((x + dx, z + dz), g) == g - 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        r = _h(x, z, 54)
        put(x, g, z, full('stone_bricks' if r < 0.35 else 'mossy_stone_bricks' if r < 0.6 else 'cobblestone' if r < 0.8 else 'andesite'))
T.trail(SPINE[:3], w=4.5)
T.trail(SPINE[2:7], w=4.0)
T.trail([(0, -38), (-6, -30), (-16, -22), (-20, -10), (-12, 2), (0, 6)], w=3.5)  # すり鉢の段を渦に下りる道 [城塞の器]
T.trail([(0, 6), (0, 14), (0, 28)], w=4.0)

# ================================================================ 3. 構造物
rng = random.Random(7)
gh = lambda x, z: T.H.get((x, z))
def ground(x, z): return T.h(x, z)

def big_cherry(x, z, seed):
    """散り桜の庭を囲む大きな桜：桜の木（trees.cherry）を一回り大きく。幹7〜9、枝5〜6本で長さ5〜7、先に桃色の平たい葉の板。"""
    r = random.Random(seed); g = ground(x, z)
    th = r.randint(5, 6)                                                        # 幹は低めに：冠を庭の縁へかぶせる
    for y in range(g + 1, g + th + 1):
        put(x, y, z, log('cherry_log'))
        if y < g + 3:
            for dx, dz in ((1, 0), (0, 1)): put(x + dx, y, z + dz, log('cherry_log'))
    tips = []
    for i in range(r.randint(5, 6)):
        a = i * 2 * math.pi / 6 + r.uniform(-0.4, 0.4)
        dx, dz = round(math.cos(a)), round(math.sin(a))
        if (dx, dz) == (0, 0): dx = 1
        TR._branch(x, g + th - r.randint(0, 2), z, dx, dz, r.randint(5, 7), 0.45, r, 'cherry_log', 'cherry', tips)
    for (tx, ty, tz) in tips: TR._lens(tx, ty + 1, tz, r.uniform(3.2, 4.2), r, 'cherry_leaves', 'cherry_leaves', 'cherry_leaves')
    TR._lens(x, g + th + 2, z, 4.0, r, 'cherry_leaves', 'cherry_leaves', 'cherry_leaves')
    TR._roots(x, z, g, r, 'cherry_log', 'cherry', 4)
    if not hasattr(T, 'TREES'): T.TREES = []
    T.TREES.append((x, z, 8, 'cherry'))

# 門：切り通しの中ほど（ゲームではここを閉じる）
GATE_AT = []
for i, (gx, z0, z1, hw) in enumerate(GATES):
    zc = (z0 + z1) // 2 + (2 if i == 2 else 0)
    T.arch_gate(zc, ground(0, zc), half=4 if i < 2 else 5)
    GATE_AT.append((gx, zc, ground(0, zc)))
# 庭の入口の大鳥居：門の南に朱の鳥居 [poi.torii_shrine の鳥居の作り]
# 庭：床を苔と花びらに、外周に石灯籠8つ、奥に鳥居と社
for (x, z), k in KIND.items():
    if k != 'yard': continue
    g = H[(x, z)]; r = math.hypot(x, z - 50)
    n = vnoise(x, z, 4, 61)
    put(x, g, z, full('moss_block') if n < 0.4 else vox.grass())
    if 15.5 < r < 17.2: put(x, g, z, full('stone_bricks' if _h(x, z, 62) < 0.6 else 'mossy_stone_bricks'))   # 円の石の帯
for i in range(8):
    a = i * math.pi / 4 + math.pi / 8
    PO.stone_lantern(int(round(19.5 * math.sin(a))), int(round(50 + 19.5 * math.cos(a))), ground)
for k_ in range(360):                                                            # 庭の縁の低い石垣（高さ1〜2、上にハーフ）。北の入口と南の鳥居の前は空ける
    t = math.radians(k_)
    x, z = int(round(22.5 * math.sin(t))), int(round(50 - 22.5 * math.cos(t)))
    if abs(x) <= 5: continue
    g = ground(x, z); hh = 1 + (vnoise(x, z, 4, 56) > 0.5)
    for y in range(g + 1, g + hh + 1): put(x, y, z, full('mossy_stone_bricks' if _h(x, y, z) < 0.4 else 'stone_bricks'))
    if get(x, g + hh + 1, z) is None: put(x, g + hh + 1, z, slab('stone_bricks'))
PO.torii_shrine(0, 67, ground(0, 67), rng, gh, f='n')                           # 円の奥の縁に鳥居、その奥に社

# すり鉢：東の縁から滝、西の段に木の桟敷
top = max(ground(POOL[0] + 6, POOL[1] + d) for d in (-1, 0, 1))
for x in range(POOL[0] + 2, POOL[0] + 7):
    for dz in (-1, 0, 1):
        z = POOL[1] + dz; g = ground(x, z)
        for y in range(g + 1, top + 1):
            if get(x, y, z) is None: put(x, y, z, water())
for (x0, x1, z0, z1) in [(-22, -15, -4, 4)]:
    yy = max(ground(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)) + 1
    T.deck(x0, x1, z0, z1, yy)
    P.cargo(x0 + 1, z0 + 1, yy, 'e')

# 遺跡のアーチ：開けた所の縁（A に2つ、B に2つ、C に2つ）
for (x, z, f) in [(-16, -126, 'e'), (14, -112, 's'), (-14, -70, 'e'), (16, -56, 's'), (14, 2, 'e'), (-6, -32, 'n')]:
    PO.white_arch(x, z, rng, ground, f)

# ================================================================ 4. 木：進むほど桜（A 0 → B 1〜3割 → C 半分 → D ほぼ全部）
def bloom(z): return max(0.0, min(1.0, (z + 100) / 120.0)) ** 1.4               # 0（入口）→ 1（庭）
def kind(x, z, r):
    if r < bloom(z) * 0.9: return 'cherry'
    if z < -100: return 'oak' if r < 0.55 else 'birch' if r < 0.85 else 'big'
    return 'big' if r < 0.04 else 'birch' if r < 0.3 else 'oak'
def fight_floor(x, z):                                                            # 戦う所は木を置かない
    sh = shapes(x, z)
    return sh['A'] < -8 or sh['B'] < -4 or sh['C'] < 1 or math.hypot(x, z - 50) < 27 or any(sh['g%d' % i] < 1 for i in range(3))
for i in range(11):                                                              # 大きな桜：円の縁のすぐ外に、枝が庭へかぶさるように（入口の北は空ける）
    a = math.pi * (0.22 + 1.56 * i / 10)
    rr = 24.5 + 1.5 * _h(i, 0, 55)
    big_cherry(int(round(rr * math.sin(a))), int(round(50 - rr * math.cos(a))), i + 1)
print('木・茂み', T.woods(kind=kind, skip=fight_floor))
T.ground_cover()
for (x, z), k in KIND.items():                                                    # 斜面の段の縁は苔ブロック（横の面も緑。草ブロックの横は土の縞に見える）[城塞の地面の2割は苔]
    if k != 'nat': continue
    g = H[(x, z)]; b = get(x, g, z)
    if not b or b.get('top') != 'grass_top': continue
    drop = max(g - H.get((x + dx, z + dz), g) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if drop >= 1 and _h(x, z, 57) < 0.8: put(x, g, z, full('moss_block'))
for (x, z), k in KIND.items():                                                    # 戦う床（すり鉢の底・庭）に残った木と茂みを除く
    if not (math.hypot(x, z + 12) < 18 or math.hypot(x, z - 50) < 21): continue
    g = H[(x, z)]
    for y in range(g + 1, g + 14):
        b = get(x, y, z)
        if b and ('leaves' in b.get('t', '') or '_log' in b.get('t', '') or b['s'] == 'fence') and not (math.hypot(x, z - 50) < 21 and y > g + 4): put(x, y, z, None)
for z in range(28, 66):                                                           # 庭の参道：北の入口から鳥居まで、床と同じ高さの踏み石（幅3、ところどころ欠ける）
    for x in (-1, 0, 1):
        if (x, z) in H and _h(x, z, 58) < 0.82:
            put(x, H[(x, z)], z, full('stone_bricks' if _h(x, z, 59) < 0.5 else 'mossy_stone_bricks' if _h(x, z, 59) < 0.8 else 'cracked_stone_bricks'))
petal = 0
CHERRY = [t for t in T.TREES if t[3] == 'cherry']
for (x, z), k in KIND.items():                                                    # 花びら：桜の多い所ほど地面に落ちる（庭は床の4割）
    g = H[(x, z)]
    p = bloom(z) ** 1.6 * ((0.12 + 0.3 * max(0, math.hypot(x, z - 50) - 12) / 9) if k == 'yard' else 0.3)
    if k == 'yard': near = True                       # 塊で（木の下と風下に溜まる）
    near = k == 'yard' or any(math.hypot(x - tx, z - tz) < R + 1 for (tx, tz, R, kk) in CHERRY if abs(tx - x) < 10 and abs(tz - z) < 10)
    if k in ('nat', 'yard') and get(x, g + 1, z) is None and get(x, g, z) and get(x, g, z)['s'] == 'full' and vnoise(x, z, 4, 63) * 0.75 + _h(x, z, 64) * 0.25 < p * (2.2 if near else 0.9):
        put(x, g + 1, z, {'s': 'carpet', 't': 'pink_petals'}); petal += 1
print('花びら', petal)

# ================================================================ 5. 部屋の台帳（ゲームの InstanceDungeon に渡す形）と書き出し
ROOMS = {'spawn': [0, ground(0, -126) + 1, -126], 'gates': GATE_AT, 'arena_center': [0, 10, 50], 'arena_r': 19,
         'rooms': {'入口の間': AREAS['A'][1], '花守の回廊': [0, -70], '散華の間': [0, -12], '散り桜の庭': [0, 50]}}
V = lambda x, y, z: [-x, y, z]
out = save('sakura_dungeon', {
    'over': [V(130, 120, -40), V(0, 8, -30)],
    'back': [V(60, 100, 140), V(0, 10, 0)],
    'top': [V(0, 300, -29), V(0, 0, -30)],
    'enter': [V(4, 20, -138), V(0, 16, -110)],
    'meadow': [V(0, 30, -96), V(0, 14, -126)],
    'corridor': [V(-12, 26, -86), V(7, 12, -60)],
    'ravine': [V(40, 95, -90), V(0, 12, -70)],
    'bowl': [V(12, 32, -38), V(0, 6, -12)],
    'bowlside': [V(30, 30, -12), V(-10, 6, -12)],
    'gate': [V(0, 14, 0), V(0, 14, 22)],
    'arena': [V(0, 22, 24), V(0, 10, 56)],
    'shrine': [V(-10, 16, 50), V(0, 12, 70)],
    'night': [V(0, 16, 26), V(0, 10, 58), {'night': True}],
}, V(0, 10, -30), shadowR=160, fog=(220, 520))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
