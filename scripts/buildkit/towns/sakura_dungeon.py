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
# 一本道にしない（藍 11/06「一本道なのが退屈すぎる」）。手本の城塞も、器 → 中心の砦 → 西の島・北の島・東の部屋へ枝分かれする。
#   入口の野 → 分かれ道 →（尾根道 | 沢道。上と下で互いが見える。尾根道の脇に隠れた空き地）→ 散華の間（すり鉢、中心）
#   → 西の翼「古い祠の森」と東の翼「滝裏の洞」に封印の灯りが1つずつ（順番は自由）→ 2つともすと南の門が開き、散り桜の庭（ボス）。
def ridge_f(t): return 14 + 7 * math.sin(math.pi * min(1, t * 1.1)) - 3 * t   # 尾根道：上って、谷を見下ろして、下りる
AREAS = [   # (名前, 形, 線/中心, 大きさ, 床, 縁の急さ)
    ('A', 'ellipse', (0, -124), (26, 17), lambda x, z, t: 14 + 2.5 * vnoise(x, z, 18, 44) + 1.2 * vnoise(x, z, 6, 45), 1.1),
    ('Br', 'capsule', [(-5, -100), (-20, -88), (-25, -70), (-12, -50)], 4.2, lambda x, z, t: ridge_f(t), 2.0),
    ('Bv', 'capsule', [(5, -100), (17, -86), (15, -66), (5, -50)], 7.0, lambda x, z, t: 14 - 4 * t + 0.8 * vnoise(x, z, 9, 46), 1.6),
    ('S', 'disc', (-42, -76), 7, lambda x, z, t: 21, 2.4),                                   # 隠れた空き地
    ('C', 'disc', (0, -12), 24, lambda x, z, t: 6 + max(0, int((math.hypot(x, z + 12) + (vnoise(x, z, 5, 47) - 0.5) * 2 - 13) / 3)), 2.6),
    ('W', 'disc', (-52, -6), 15, lambda x, z, t: 13 + 1.5 * vnoise(x, z, 8, 48), 1.7),       # 古い祠の森
    ('E', 'disc', (46, -22), 12, lambda x, z, t: 9, 3.0),                                    # 滝裏の洞
    ('D', 'disc', (0, 50), 25, lambda x, z, t: 9 + max(0, math.hypot(x, z - 50) - 21) * 0.5 + 0.6 * vnoise(x, z, 7, 48), 0.55),
    # 切り通し（細い道）：(始め, 終わり, 半幅, 始めの床, 終わりの床)
    ('gA', 'cut', ((0, -110), (0, -98)), 4.0, (14, 14), 3.0),
    ('gS', 'cut', ((-26, -76), (-36, -76)), 1.8, (ridge_f(0.45), 21), 3.0),
    ('gBr', 'cut', ((-12, -50), (-2, -37)), 3.0, (ridge_f(1), 10), 3.0),
    ('gBv', 'cut', ((5, -50), (2, -37)), 3.5, (10, 10), 3.0),
    ('gW', 'cut', ((-23, -8), (-38, -6)), 3.5, (10, 13), 3.0),
    ('gE', 'cut', ((22, -16), (35, -20)), 3.5, (10, 9), 3.0),
    ('gD', 'cut', ((0, 10), (0, 27)), 4.2, (11, 9), 3.0),
]
def _seg_d(x, z, a, b):
    ax, az = a; bx, bz = b; vx, vz = bx - ax, bz - az
    t = max(0.0, min(1.0, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)))
    return math.hypot(x - ax - vx * t, z - az - vz * t), t
def _line_d(x, z, pts):
    """折れ線への距離と、線の上の位置（0〜1）。"""
    L = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:])]; tot = sum(L); acc = 0; best = (1e9, 0)
    for (a, b), l in zip(zip(pts, pts[1:]), L):
        d, t = _seg_d(x, z, a, b)
        if d < best[0]: best = (d, (acc + t * l) / tot)
        acc += l
    return best
def shapes(x, z):
    """各区域への「縁からの距離」（中は負）と、その区域の床の高さ。"""
    wob = (vnoise(x, z, 11, 41) - 0.5) * 6
    out = {}
    for (k, kind, geo, size, fl, rim) in AREAS:
        if kind == 'ellipse':
            (cx, cz), (rx, rz) = geo, size
            d = (math.hypot((x - cx) / rx, (z - cz) / rz) - 1) * min(rx, rz) + wob; t = 0
        elif kind == 'capsule':
            d, t = _line_d(x, z, geo); d = d - size + wob * 0.5
        elif kind == 'disc':
            d = math.hypot(x - geo[0], z - geo[1]) - size + wob * (0.5 if k in ('D', 'S') else 0.8); t = 0
        else:
            d, t = _seg_d(x, z, *geo); d = d - size + (vnoise(x, z, 4, 43) - 0.5) * 1.5
        f = fl[0] + (fl[1] - fl[0]) * t if kind == 'cut' else fl(x, z, t)
        out[k] = (d, f, rim)
    return out
def nat(x, z):
    sh = shapes(x, z)
    k = min(sh, key=lambda k: sh[k][0]); d, f, rim = sh[k]
    if d <= 0: return f
    up = d * rim + (vnoise(x, z, 7, 49) - 0.4) * 4 * min(1, d / 6)
    if vnoise(x, z, 5, 50) > 0.62: up = math.floor(up / 4) * 4 + 1             # 崖の面に岩棚
    cap = 14 + 4 * vnoise(x, z, 20, 53) if k == 'D' else 30 + 6 * vnoise(x, z, 30, 51)   # 庭の周りは低い丘（大きな桜を見せる）
    return f + min(cap, up)
def inside(k, x, z, m=0): return shapes(x, z)[k][0] < m
POOL = (17, -20)                                                                 # すり鉢の東の縁の滝壺
def in_water(x, z): return False

T = Town((-72, 72, -150, 92), nat, in_water)
H, KIND = T.H, T.KIND

# ================================================================ 2. 床：器の底と門の前を平らに、ボスの円庭は半径21で平らな床
T.pad_disc(0, 50, 21, 9, 'yard')                                                  # 庭（戦う床）
T.resolve(no_blend=lambda x, z: True)
T.fill()
for (x, z), k in list(KIND.items()):                                             # すり鉢の段の縁は石で縁取る [城塞の器の灰色の輪]
    if k != 'nat' or shapes(x, z)['C'][0] > 1: continue
    g = H[(x, z)]
    if any(H.get((x + dx, z + dz), g) == g - 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        r = _h(x, z, 54)
        put(x, g, z, full('stone_bricks' if r < 0.35 else 'mossy_stone_bricks' if r < 0.6 else 'cobblestone' if r < 0.8 else 'andesite'))
BR, BV = AREAS[1][2], AREAS[2][2]
T.trail([(0, -132), (0, -110), (0, -100)], w=4.5)
T.trail([(0, -100)] + BR + [(-2, -37)], w=3.0)                                  # 尾根道（細い）
T.trail([(0, -100)] + BV + [(2, -37)], w=4.5)                                   # 沢道
T.trail([(-25, -76), (-36, -76), (-42, -76)], w=2.0)                            # 隠れた空き地へ（茂みの隙間）
T.trail([(0, -37), (-8, -30), (-18, -20), (-22, -8), (-38, -6), (-52, -6)], w=3.5)   # すり鉢の段を渦に下り、西の翼へ
T.trail([(-18, -20), (-14, -4), (-4, 4), (0, 10), (0, 28)], w=3.5)              # 底を回って南の門へ
T.trail([(0, -37), (10, -32), (20, -22), (35, -20), (46, -22)], w=3.5)          # 東の翼へ
T.trail([(-52, -6), (-58, 4), (-50, 6)], w=2.5)
VBROOK = [(8, -96), (16, -84), (13, -66), (6, -52)]                              # 沢道の小川：道の脇を流れて、すり鉢の縁で滝になる
for (x, z), k in list(KIND.items()):
    if k == 'nat' and _line_d(x, z, VBROOK)[0] < 1.3 and inside('Bv', x, z, -2):
        H[(x, z)] -= 1; KIND[(x, z)] = 'brook'
for (x, z), k in KIND.items():
    if k == 'brook':
        put(x, H[(x, z)], z, full('gravel')); put(x, H[(x, z)] + 1, z, water())

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

# 門：切り通しの中ほど（ゲームではここを閉じる）。南の庭の門の両脇に封印の灯りの台を2つ（西と東の翼の灯りに対応）
GATE_AT = {}
for (name, z, cx, axis, half) in [('入口', -104, 0, 'z', 4), ('すり鉢', -39, 0, 'z', 5), ('西の翼', -30, -7, 'x', 4), ('東の翼', 28, -18, 'x', 4), ('庭', 20, 0, 'z', 5)]:
    gx, gz = (cx, z) if axis == 'z' else (z, cx)
    T.arch_gate(z, ground(gx, gz), half=half, cx=cx, axis=axis)
    GATE_AT[name] = [gx, ground(gx, gz) + 1, gz]
for sx in (-8, 8):
    g = ground(sx, 22)
    for y in range(g + 1, g + 3): put(sx, y, 22, full('chiseled_stone_bricks' if y == g + 2 else 'polished_andesite'))
    put(sx, g + 3, 22, {'s': 'lantern', 't': 'soul_lantern'})
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

# 西の翼「古い祠の森」：苔むした古い森の奥に鳥居と社、社の前に封印の灯りの台
SEALS = {}
PO.torii_shrine(-48, -6, ground(-48, -6), rng, gh, f='e')
g = ground(-44, -9)
for y in range(g + 1, g + 3): put(-44, y, -9, full('chiseled_stone_bricks' if y == g + 2 else 'mossy_stone_bricks'))
put(-44, g + 3, -9, {'s': 'lantern', 't': 'soul_lantern'}); SEALS['西'] = [-44, g + 3, -9]
# 東の翼「滝裏の洞」：東の崖から滝。滝の裏の崖を3マス掘って上を残し（張り出し）、奥に封印の台。滝の脇から裏へ回り込む
WX, WZ = 57, -22
fl = ground(WX - 3, WZ)
for x in range(WX - 1, WX + 5):                                                   # 洞のまわりの岩の殻（奥と上を厚く、空が透けないように）
    for z in range(WZ - 5, WZ + 6):
        for y in range(ground(x, z) + 1, fl + 10): put(x, y, z, full(T.rock_at(x, y, z)))
        if ground(x, z) < fl + 9: H[(x, z)] = fl + 9
for x in range(WX - 1, WX + 3):                                                   # 洞：幅7・奥行き4・高さ4
    for z in range(WZ - 3, WZ + 4):
        top_ = ground(x, z)
        for y in range(fl + 1, fl + 5): put(x, y, z, None)
        put(x, fl, z, full('mossy_cobblestone' if _h(x, z, 66) < 0.5 else 'cobblestone'))
        for y in range(fl + 5, max(top_, fl + 7) + 1): put(x, y, z, full(T.rock_at(x, y, z)))
cliff_top = max(ground(WX - 2, WZ + d) for d in (-1, 0, 1))
for z in range(WZ - 2, WZ + 3):                                                   # 滝：洞の口の前を、崖の上から滝壺へ（幅5）
    for y in range(fl + 1, max(cliff_top, fl + 9) + 1):
        if get(WX - 2, y, z) is None: put(WX - 2, y, z, water())
for x in range(WX - 6, WX - 2):
    for z in range(WZ - 2, WZ + 3):
        if math.hypot(x - (WX - 4), z - WZ) < 2.6: put(x, ground(x, z), z, water())
put(WX + 1, fl + 1, WZ, full('chiseled_stone_bricks')); put(WX + 1, fl + 2, WZ, full('chiseled_stone_bricks'))
put(WX + 1, fl + 3, WZ, {'s': 'lantern', 't': 'soul_lantern'}); SEALS['東'] = [WX + 1, fl + 3, WZ]
# 隠れた空き地：尾根道の脇の茂みの隙間の先に、古い大桜と宝の樽
big_cherry(-44, -78, 99)
put(-40, ground(-40, -74) + 1, -74, P.BARREL)
# 遺跡のアーチ：開けた所の縁（A に2つ、B に2つ、C に2つ）
for (x, z, f) in [(-16, -128, 'e'), (14, -116, 's'), (24, -76, 'e'), (-30, -64, 's'), (14, 2, 'e'), (-6, -30, 'n'), (-60, -12, 'e')]:
    PO.white_arch(x, z, rng, ground, f)

# ================================================================ 4. 木：進むほど桜（A 0 → B 1〜3割 → C 半分 → D ほぼ全部）
def bloom(z): return max(0.0, min(1.0, (z + 100) / 120.0)) ** 1.4               # 0（入口）→ 1（庭）
def kind(x, z, r):
    if r < bloom(z) * 0.9: return 'cherry'
    if z < -100: return 'oak' if r < 0.55 else 'birch' if r < 0.85 else 'big'
    return 'big' if r < 0.04 else 'birch' if r < 0.3 else 'oak'
def fight_floor(x, z):                                                            # 戦う所は木を置かない
    sh = shapes(x, z)
    return (sh['A'][0] < -8 or sh['Bv'][0] < -3 or sh['Br'][0] < -1 or sh['C'][0] < 1 or sh['E'][0] < 2 or sh['S'][0] < 1
            or math.hypot(x + 52, z + 6) < 8 or math.hypot(x, z - 50) < 27 or any(sh[k][0] < 1 for k in sh if k.startswith('g')))
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
ROOMS = {'spawn': [0, ground(0, -128) + 1, -128], 'gates': GATE_AT, 'arena_center': [0, 10, 50], 'arena_r': 19,
         'seals': SEALS, 'secret': [-42, ground(-42, -76) + 1, -76],
         'rooms': {'入口の野': [0, -124], '尾根道': [-24, -72], '沢道': [15, -70], '散華の間': [0, -12], '古い祠の森': [-52, -6], '滝裏の洞': [46, -22], '散り桜の庭': [0, 50]},
         'order': '入口の野 →（尾根道 | 沢道）→ 散華の間 →（古い祠の森・滝裏の洞の封印を2つ、順番は自由）→ 散り桜の庭'}
# 歩ける検査：入口から、1段までの段差で歩いて行けるか（門は開いているとみなす）。封印2つ・隠れた空き地・庭・各区域の真ん中
from collections import deque
def stand(x, z):
    g = H.get((x, z))
    if g is None: return None
    b = get(x, g + 1, z)
    if b and b['s'] in ('carpet', 'cross'): b = None
    if b and b['s'] == 'water': return g
    if b and b['s'] in ('slab', 'stair'): g += 1; b = None
    if b is not None or (get(x, g + 2, z) and get(x, g + 2, z)['s'] in ('full', 'fence', 'wall')): return None
    return g
seen = {(0, -128)}; q = deque([(0, -128)])
while q:
    x, z = q.popleft(); g = stand(x, z)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = (x + dx, z + dz)
        if n in seen: continue
        gn = stand(*n)
        if gn is not None and gn - g <= 1 and g - gn <= 3: seen.add(n); q.append(n)
want = {'西の封印': (-45, -9), '東の封印': (WX, WZ), '隠れた空き地': (-40, -75), '庭': (0, 50), '尾根道': (-24, -72), '沢道': (12, -70), '散華の間': (0, -12)}
bad = [k for k, (x, z) in want.items() if not any((x + dx, z + dz) in seen for dx in range(-2, 3) for dz in range(-2, 3))]
print('歩いて行けない所', bad or 'なし')
V = lambda x, y, z: [-x, y, z]
out = save('sakura_dungeon', {
    'over': [V(130, 120, -40), V(0, 8, -30)],
    'back': [V(60, 100, 140), V(0, 10, 0)],
    'top': [V(0, 320, -29), V(0, 0, -30)],
    'enter': [V(4, 22, -142), V(0, 16, -112)],
    'fork': [V(-10, 62, -128), V(0, 14, -84)],
    'ridge': [V(-22, 26, -92), V(-24, 20, -66)],
    'valley': [V(44, 64, -92), V(10, 12, -70)],
    'secret': [V(-34, 28, -70), V(-42, 22, -77)],
    'bowl': [V(12, 32, -38), V(0, 6, -12)],
    'west': [V(-34, 24, -8), V(-52, 14, -6)],
    'east': [V(36, 18, -24), V(56, 12, -22)],
    'gate': [V(0, 14, 0), V(0, 14, 22)],
    'arena': [V(0, 22, 24), V(0, 10, 56)],
    'shrine': [V(-10, 16, 50), V(0, 12, 70)],
    'night': [V(0, 16, 26), V(0, 10, 58), {'night': True}],
}, V(0, 10, -30), shadowR=160, fog=(220, 520))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
