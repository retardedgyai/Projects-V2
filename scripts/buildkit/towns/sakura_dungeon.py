#!/usr/bin/env python3
"""散り桜の社（11/06、最初のインスタンスダンジョンの外の地図）。藍「最初は平原とか森みたいなのを基調に、ボスが近づくにつれて桜が咲きほこる感じ」
→「一本道なのが退屈すぎる」→「もっとダンジョンを長く、まっすぐじゃなくて複雑に」。
手本は Runeforge の「緑の城塞」（towns/mc_view.py で見る）：器 → 中心の砦 → 西の島・北の島・東の部屋へ枝分かれ。決まりは rules/dungeon.md。

形：真ん中に桜の山（頂上がボスの庭）、まわりを深い谷が囲む。道は谷の外を時計回りに一周しながら上り、最後に谷を石橋で渡って山へ。
庭は入口から谷越しに見えているのに、着くのは最後。
  入口の野（南西、14）
  →（沢道〈谷底の小川沿い〉｜ 尾根道〈谷を見下ろす細道〉）… 2本は並んで走り、苔の窪で合う
  → 苔の窪（南東、10、波）→ 滝の九十九折（崖を折り返して上る、10→24）→ 散華の間（東のすり鉢、波）
  → 崖道（谷の縁、24→30）… 脇に隠れた空き地 → 古い祠の森（北、封印1）
  → 西の岩棚（30→36）→ 滝裏の洞（西、封印2）→ 谷の石橋（38）→ 散り桜の庭（山の上、40、ボス）
"""
import json, math, os, random, sys
from collections import deque
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
import vox
from vox import put, get, full, log, stair, slab, fence, cross, trap, vnoise, _h, LANTERN, water
import trees as TR
import poi as PO
import props as P
import escape_check as EC

# ================================================================ 1. 区域の形と床の高さ
MR, CLIFF, RAV_OUT = 31, 40, 52                                                  # 山の頂の半径・山の崖の外・谷の外の縁
def ridge_f(t): return 15 + 7 * math.sin(math.pi * t) - 3 * t                    # 尾根道：上って、谷を見下ろして、下りる
AREAS = [   # (名前, 形, 線/中心, 大きさ, 床, 縁の急さ, 進み 0〜1)
    ('A', 'ellipse', (-70, 78), (20, 14), lambda x, z, t: 14 + 2.5 * vnoise(x, z, 18, 44) + 1.2 * vnoise(x, z, 6, 45), 1.1, 0.0),
    ('P1', 'capsule', [(-52, 88), (-20, 94), (15, 92), (45, 82)], 6.5, lambda x, z, t: 14 - 4 * t + 0.8 * vnoise(x, z, 9, 46), 1.6, 0.1),
    ('P1b', 'capsule', [(-55, 70), (-25, 68), (10, 70), (42, 66)], 3.8, lambda x, z, t: ridge_f(t), 2.0, 0.12),
    ('R1', 'disc', (62, 70), 11, lambda x, z, t: 10 + 0.8 * vnoise(x, z, 6, 47), 1.8, 0.25),
    ('P2', 'capsule', [(68, 56), (90, 47), (72, 37), (92, 26), (77, 15)], 4.8, lambda x, z, t: 10 + 14 * t, 2.4, 0.35),
    ('H', 'disc', (84, -6), 17, lambda x, z, t: 18 + max(0, int((math.hypot(x - 84, z + 6) + (vnoise(x, z, 5, 48) - 0.5) * 2 - 8) / 3)), 2.6, 0.5),
    ('P3', 'capsule', [(80, -26), (72, -52), (50, -74), (22, -86)], 5.0, lambda x, z, t: 20 + 10 * t, 2.2, 0.6),
    ('S', 'disc', (85, -64), 6, lambda x, z, t: 27, 2.6, 0.62),
    ('N', 'disc', (0, -88), 14, lambda x, z, t: 30 + 1.2 * vnoise(x, z, 8, 49), 1.7, 0.7),
    ('P4', 'capsule', [(-16, -86), (-48, -74), (-70, -50), (-80, -24)], 5.0, lambda x, z, t: 30 + 6 * t, 2.2, 0.8),
    ('W', 'disc', (-82, -2), 11, lambda x, z, t: 36, 2.8, 0.88),
    # 道の途中の開けた所（藍 11/06「細道が長いかも」：細い所を20〜30マスで切る）
    ('q1', 'disc', (-8, 68), 6, lambda x, z, t: 20.5, 1.8, 0.14),                  # 尾根の見晴らし
    ('q2', 'disc', (74, 37), 7, lambda x, z, t: 17, 2.0, 0.35),                    # 九十九折の踊り場
    ('q3', 'disc', (62, -66), 8, lambda x, z, t: 25.5, 2.0, 0.6),                  # 崖道の見晴らし台
    ('q4', 'disc', (34, -82), 7, lambda x, z, t: 28.5, 2.0, 0.66),                   # 崖道の野営跡
    ('q5', 'disc', (-48, -74), 8, lambda x, z, t: 32, 2.0, 0.77),                  # 岩棚の崩れた見張り台
    ('q6', 'disc', (-72, -44), 7, lambda x, z, t: 35, 2.0, 0.83),                  # 岩棚の湧き水
    ('D', 'disc', (0, 0), 25, lambda x, z, t: 40, 0.0, 1.0),
    # 切り通し：(始め, 終わり), 半幅, (始めの床, 終わりの床)
    ('gR1', 'cut', ((45, 82), (54, 76)), 3.5, (10, 10), 3.0, 0.2),
    ('gR1b', 'cut', ((42, 66), (53, 68)), 2.5, (12, 10), 3.0, 0.2),
    ('gH', 'cut', ((77, 15), (81, 7)), 3.5, (24, 21), 3.0, 0.45),
    ('gS', 'cut', ((75, -55), (81, -61)), 1.8, (24, 27), 3.0, 0.6),
    ('gW', 'cut', ((-80, -24), (-81, -12)), 3.2, (36, 36), 3.0, 0.85),
    ('gB', 'cut', ((-71, -2), (-50, -2)), 2.5, (36, 38), 3.0, 0.92),
]
PROG = {k: p for (k, *_, p) in AREAS}
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
    """各区域への「縁からの距離」（中は負）、その区域の床、縁の急さ。"""
    wob = (vnoise(x, z, 11, 41) - 0.5) * 6
    out = {}
    for (k, kind, geo, size, fl, rim, p) in AREAS:
        if kind == 'ellipse':
            (cx, cz), (rx, rz) = geo, size
            d = (math.hypot((x - cx) / rx, (z - cz) / rz) - 1) * min(rx, rz) + wob; t = 0
        elif kind == 'capsule': d, t = _line_d(x, z, geo); d = d - size + wob * 0.4
        elif kind == 'disc': d = math.hypot(x - geo[0], z - geo[1]) - size + wob * 0.6; t = 0
        else: d, t = _seg_d(x, z, *geo); d = d - size + (vnoise(x, z, 4, 43) - 0.5) * 1.2
        f = fl[0] + (fl[1] - fl[0]) * t if kind == 'cut' else fl(x, z, t)
        out[k] = (d, f, rim)
    return out
def nearest(x, z):
    sh = shapes(x, z); k = min(sh, key=lambda k: sh[k][0]); return k, sh[k]
def wall(x, z, r):
    """谷の外の崖。一枚の壁にしない [nature.md 13]：縁の線を揺らし（出入り4〜6マス）、縦の柱を束ねた凸凹、ところどころ岩棚。"""
    rr = r - RAV_OUT - 5 * (vnoise(x, z, 14, 56) - 0.5) - 2.5 * (vnoise(x, z, 5, 57) - 0.5)
    h = 15 + max(0, rr) * 3.4 + (vnoise(x, z, 2.5, 58) - 0.5) * 3                # 谷底の縁（14）より下げない：下げると谷のまわりに深い溝ができる（藍 11/06 F2「この隙間」）
    if vnoise(x, z, 6, 59) > 0.6: h = math.floor(h / 5) * 5 + 2
    return h
def nat(x, z):
    r = math.hypot(x, z)
    if r < MR + 1 + vnoise(x, z, 6, 51) * 2: return 40 + 1.5 * vnoise(x, z, 9, 52) * min(1, (MR - r) / 6 + 0.3)   # 山の頂
    if r < CLIFF + 3 * (vnoise(x, z, 9, 60) - 0.5):                              # 山の崖：段（岩棚）と、前へ出た柱の束 [nature.md 13]
        h = 40 - (r - MR) * 4.6
        h = math.floor(h / 7) * 7 + 3 + (r - MR) * 0.6 if vnoise(x, z, 7, 61) > 0.45 else h   # 7マスごとの岩棚
        return h + (vnoise(x, z, 2.2, 53) - 0.5) * 7
    if r < RAV_OUT: return 2 + 2 * vnoise(x, z, 10, 54) + max(0, 44 - r) * 0 + (max(0, r - 46) * 2)   # 谷底
    k, (d, f, rim) = nearest(x, z)
    if k == 'gB' and d <= 0: return f                                              # 橋へ向かう切り通しは谷の崖で削らない
    if d <= 0: h = f
    else:
        up = d * rim + (vnoise(x, z, 7, 49) - 0.4) * 4 * min(1, d / 6)
        if vnoise(x, z, 5, 50) > 0.62: up = math.floor(up / 4) * 4 + 1         # 崖の面に岩棚
        h = f + min(30 + 6 * vnoise(x, z, 30, 51), up)
        if r < 66: h = min(h, f + 2 + max(0, 60 - r) * 0)                       # 谷の側は低い縁（谷を見下ろせる）
    return min(h, wall(x, z, r))
def in_water(x, z): return 41 < math.hypot(x, z) + (vnoise(x, z, 8, 55) - 0.5) * 2 < 44.5   # 谷底の川（山を一周する）

T = Town((-112, 112, -112, 112), nat, in_water)
H, KIND = T.H, T.KIND

# ================================================================ 2. 床・道
T.pad_disc(0, 0, 21, 40, 'yard')                                                  # 庭（戦う床）
T.resolve(no_blend=lambda x, z: True)
T.fill()
for (x, z), k in list(KIND.items()):                                             # すり鉢の段の縁は石で縁取る [rules 107]
    if k != 'nat' or shapes(x, z)['H'][0] > 1: continue
    g = H[(x, z)]
    if any(H.get((x + dx, z + dz), g) == g - 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        r = _h(x, z, 54)
        put(x, g, z, full('stone_bricks' if r < 0.35 else 'mossy_stone_bricks' if r < 0.6 else 'cobblestone' if r < 0.8 else 'andesite'))
G = {k: geo for (k, kind, geo, *_ ) in AREAS}
T.trail([(-84, 80), (-62, 82), (-52, 88)] + G['P1'][1:] + [(54, 76), (62, 70)], w=4.0)   # 沢道
T.trail([(-62, 76), (-55, 70)] + G['P1b'][1:] + [(53, 68), (62, 70)], w=2.6)          # 尾根道
T.trail([(62, 70), (68, 56)] + G['P2'][1:] + [(81, 7), (84, -6)], w=3.6)              # 九十九折
T.trail([(84, -6), (80, -26)] + G['P3'][1:] + [(0, -88)], w=3.8)                      # 崖道
T.trail([(75, -55), (81, -61), (85, -64)], w=1.8)                                   # 隠れた空き地へ（茂みの隙間）
T.trail([(0, -88)] + G['P4'] + [(-81, -12), (-82, -2), (-71, -2), (-50, -2)], w=3.8)   # 岩棚 → 洞 → 橋
P1BROOK = [(-48, 92), (-20, 98), (15, 96), (44, 86)]                              # 沢道の小川：道の南脇を流れる
for (x, z), k in list(KIND.items()):
    if k == 'nat' and _line_d(x, z, P1BROOK)[0] < 1.3 and shapes(x, z)['P1'][0] < -1:
        H[(x, z)] -= 1; KIND[(x, z)] = 'brook'
for (x, z), k in KIND.items():
    if k == 'brook': put(x, H[(x, z)], z, full('gravel')); put(x, H[(x, z)] + 1, z, water())

# ================================================================ 3. 構造物
rng = random.Random(7)
gh = lambda x, z: T.H.get((x, z))
def ground(x, z): return T.h(x, z)

def big_cherry(x, z, seed):
    """散り桜の庭を囲む大きな桜：桜の木（trees.cherry）を一回り大きく。幹5〜6（冠を庭の縁へかぶせる）、枝5〜6本で長さ5〜7、先に桃色の平たい葉の板。"""
    r = random.Random(seed); g = ground(x, z)
    th = r.randint(5, 6)
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
def seal(x, z, base='chiseled_stone_bricks'):
    g = ground(x, z)
    for y in range(g + 1, g + 3): put(x, y, z, full(base if y == g + 2 else 'mossy_stone_bricks'))
    put(x, g + 3, z, {'s': 'lantern', 't': 'soul_lantern'}); return [x, g + 3, z]

# 谷の石橋：西の岩棚の先から、山の頂へ。8マスおきに谷底まで橋脚
yb = T.stone_bridge(-2, -52, -30, axis='x', base=37, rail='wall')
for x in range(-48, -31, 6):
    for u in (-3, -1):
        for y in range(ground(x, u), 39): put(x, y, u, full('stone_bricks' if _h(x, y, u) < 0.7 else 'mossy_stone_bricks'))
# 門（ゲームではここを閉じる）
GATE_AT = {}
for (name, z, cx, axis, half) in [('苔の窪', 50, 79, 'x', 4), ('散華の間', 11, 79, 'z', 4), ('古い祠の森', 17, -87, 'x', 4), ('滝裏の洞', -15, -81, 'z', 4), ('庭', -26, -2, 'x', 4)]:
    gx, gz = (cx, z) if axis == 'z' else (z, cx)
    T.arch_gate(z, ground(gx, gz), half=half, cx=cx, axis=axis)
    GATE_AT[name] = [gx, ground(gx, gz) + 1, gz]
SEALS = {}
for sz in (-7, 3): seal(-24, sz)                                                  # 庭の門の両脇：封印の灯り（ゲームでは2つそろうと門が開く）
# 庭：苔の床・石の帯・石灯籠・低い石垣（西の橋の口は空ける）・大きな桜・奥（東）に鳥居と社
for (x, z), k in KIND.items():
    if k != 'yard': continue
    g = H[(x, z)]; r = math.hypot(x, z)
    put(x, g, z, full('moss_block') if vnoise(x, z, 4, 61) < 0.4 else vox.grass())
    if 15.5 < r < 17.2: put(x, g, z, full('stone_bricks' if _h(x, z, 62) < 0.6 else 'mossy_stone_bricks'))
for i in range(8):
    a = i * math.pi / 4 + math.pi / 8
    PO.stone_lantern(int(round(19.5 * math.cos(a))), int(round(19.5 * math.sin(a))), ground)
# --- ボスの庭を豪華に（藍 11/06「最後のボスが湧くであろう場所をもっと豪華に」）。戦う床は静かなまま [rules 110]、豪華さは縁・入口・奥に寄せる
RED, BLACK = 'red_concrete', 'polished_blackstone'
HALL = (21, 33, -8, 8)                                                            # 拝殿の基壇（x0, x1, z0, z1）
def in_hall(x, z): return HALL[0] <= x <= HALL[1] and HALL[2] <= z <= HALL[3]
# 真ん中の紋：ボスが湧く所。床と同じ高さの象嵌で、桜の花（花びら5枚）
for x in range(-10, 11):
    for z in range(-10, 11):
        r = math.hypot(x, z); th = math.atan2(z, x)
        R = 3.2 + 4.3 * abs(math.cos(2.5 * (th + math.pi / 2))) ** 0.8              # 花びら5枚、1枚は西（参道）を向く
        g = H[(x, z)]
        if r < 1.5: put(x, g, z, full('chiseled_stone_bricks'))
        elif r < 2.6: put(x, g, z, full('polished_diorite'))
        elif r < R - 0.7: put(x, g, z, full('pink_concrete'))
        elif r < R + 0.3: put(x, g, z, full('polished_diorite'))
        elif 8.6 < r < 9.6: put(x, g, z, full('polished_andesite'))
# 玉垣：石の土台に朱の柱（9度おき）と柵。西の参道の口と東の拝殿は空ける。高さ2.5で跳び越せない
for k_ in range(720):
    t = math.radians(k_ / 2)
    x, z = int(round(22.5 * math.cos(t))), int(round(22.5 * math.sin(t)))
    if (x < 0 and abs(z) <= 5) or in_hall(x, z): continue
    g = ground(x, z)
    put(x, g + 1, z, full('stone_bricks' if _h(x, z, 81) < 0.7 else 'mossy_stone_bricks'))
    if (k_ // 2) % 9 == 0 or (get(x, g + 2, z) or {}).get('t') == RED:
        put(x, g + 2, z, full(RED)); put(x, g + 3, z, full(RED)); put(x, g + 4, z, slab('dark_oak_planks'))
    elif get(x, g + 2, z) is None: put(x, g + 2, z, fence('mangrove_planks'))
def torii(x, z, g, half=4, h=7):
    """大鳥居（明神鳥居）：朱の柱（足元は黒い亀腹）、貫、島木、黒い笠木の両端を反らせ、真ん中に額束。柱は z 方向に並ぶ。"""
    P_ = lambda u, y, b: put(x, y, z + u, b)
    for s_ in (-half, half):
        P_(s_, g + 1, full(BLACK))
        for y in range(g + 2, g + h + 1): P_(s_, y, full(RED))
    for u in range(-half - 1, half + 2): P_(u, g + h - 2, full(RED) if abs(u) <= half else slab(RED, 'top'))   # 貫
    for u in range(-half - 1, half + 2): P_(u, g + h, full(RED))                                               # 島木
    for u in range(-half - 2, half + 3):                                                                       # 笠木：両端は1段上へ反る
        if abs(u) == half + 2: P_(u, g + h + 1, full(BLACK)); P_(u, g + h + 2, slab(BLACK))
        else: P_(u, g + h + 1, full(BLACK))
    P_(0, g + h - 1, full('dark_oak_planks'))                                                                  # 額束
    for s_ in (-half + 1, half - 1): P_(s_, g + h - 3, P.HANG)                                                 # 貫から吊り灯籠
torii(-20, -1, ground(-20, -1), half=4, h=8)
def tall_lantern(x, z, g):
    """大きな石灯籠：台・竿・火袋・笠・宝珠。高さ5。"""
    put(x, g + 1, z, full('stone_bricks')); put(x, g + 2, z, P.wall('stone_bricks')); put(x, g + 3, z, full('chiseled_stone_bricks'))
    put(x, g + 4, z, P.LANTERN)
    for dx, dz, f in ((1, 0, 'w'), (-1, 0, 'e'), (0, 1, 'n'), (0, -1, 's')): put(x + dx, g + 4, z + dz, stair('stone_bricks', f, 'top'))
    put(x, g + 5, z, slab('stone_bricks'))
# 拝殿：東の奥、2段の基壇の上。朱の柱、白い壁、反った黒い瓦の屋根、軒に吊り灯籠、正面に注連縄。屋根の棟は南北（z）に通す
x0, x1, z0, z1 = HALL; gb = 40; top = gb + 2
for x in range(x0, x1 + 1):
    for z in range(z0, z1 + 1):
        for y in range(min(ground(x, z), gb) - 6, top + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            put(x, y, z, full(('chiseled_stone_bricks' if (x in (x0, x1) and z in (z0, z1)) else 'stone_bricks' if _h(x, y, z) < 0.75 else 'mossy_stone_bricks') if edge or y < top else 'polished_andesite'))
        for y in range(top + 1, top + 12):
            if get(x, y, z) and get(x, y, z)['s'] != 'full': put(x, y, z, None)
        H[(x, z)] = top; KIND[(x, z)] = 'hall'
for z in range(-3, 4):                                                            # 正面の石段
    put(x0 - 2, gb + 1, z, stair('stone_bricks', 'e')); put(x0 - 1, gb + 1, z, full('stone_bricks')); put(x0 - 1, gb + 2, z, stair('stone_bricks', 'e'))
    H[(x0 - 1, z)] = gb + 1
for x in range(x0, x1 + 1):                                                       # 基壇の縁：朱の柵（正面の石段の口は空ける）
    for z in range(z0, z1 + 1):
        if not (x in (x0, x1) or z in (z0, z1)) or (x == x0 and abs(z) <= 3): continue
        put(x, top + 1, z, full(RED) if (x in (x0, x1) and z in (z0, z1)) or (x == x0 and abs(z) == 4) else fence('mangrove_planks'))
for (lx, lz) in ((x0 - 3, -6), (x0 - 3, 6)): tall_lantern(lx, lz, ground(lx, lz))
hx0, hx1, hz0, hz1 = 24, 31, -6, 6; fy = top + 1                               # 本体：前の2列は吹き放しの縁
for x in range(hx0, hx1 + 1):
    for z in range(hz0, hz1 + 1): put(x, top, z, full('dark_oak_planks'))
for x in range(hx0, hx1 + 1):
    for z in range(hz0, hz1 + 1):
        post = x in (hx0, 26, hx1) and z in (hz0, -2, 2, hz1)
        wallc = ((x == hx1 or z in (hz0, hz1)) and x >= 26) or (x == 26 and abs(z) > 1)
        for y in range(fy, fy + 5):
            if post: put(x, y, z, full(RED))
            elif wallc:
                put(x, y, z, full('dark_oak_planks') if y in (fy, fy + 4) else full('white_concrete') if x != 26 else trap('dark_oak_trapdoor', 'w') if y < fy + 3 else full('white_concrete'))
for z in range(hz0, hz1 + 1):                                                     # 長押：柱の上の黒い横木
    for x in (hx0, 26):
        if get(x, fy + 4, z) is None: put(x, fy + 4, z, full('dark_oak_planks'))
put(30, fy, 0, full('gold_block')); put(30, fy + 1, 0, P.LANTERN)                # 奥の御神体（金の鏡と灯り）
for z in (-1, 1): put(30, fy, z, full('chiseled_stone_bricks')); put(30, fy + 1, z, {'s': 'lantern', 't': 'soul_lantern'})
ry = fy + 5                                                                       # 屋根：軒は基壇の外へ、両端（z）の軒先を1段反らす
for i in range(6):
    xa, xb = 21 + i, 34 - i
    for z in range(-9, 10):
        up = 1 if abs(z) >= 8 and i == 0 else 0
        put(xa, ry + i + up, z, stair('deepslate_tiles', 'e')); put(xb, ry + i + up, z, stair('deepslate_tiles', 'w'))
        if i > 0 and abs(z) <= 8:
            for x in (xa, xb):
                if get(x, ry + i - 1, z) is None: put(x, ry + i - 1, z, full('dark_oak_planks'))
    for x in range(xa + 1, xb):                                                   # 妻（三角の壁）
        for z in (-8, 8):
            if get(x, ry + i, z) is None: put(x, ry + i, z, full('white_concrete') if 0 < i < 5 and abs(x - 27.5) < 4 - i * 0.6 else full('dark_oak_planks'))
for z in range(-9, 10): put(27, ry + 6, z, full('deepslate_tiles')); put(28, ry + 6, z, full('deepslate_tiles'))
for z in range(-10, 11): put(27, ry + 7, z, slab('deepslate_tiles')); put(28, ry + 7, z, slab('deepslate_tiles'))
for z in (-10, 10):                                                               # 鬼瓦：棟の両端
    for x in (27, 28): put(x, ry + 6, z, full(BLACK)); put(x, ry + 7, z, full(BLACK)); put(x, ry + 8, z, slab(BLACK))
for z in (-6, -2, 2, 6): put(22, ry - 1, z, P.HANG)                              # 軒の吊り灯籠
for z in range(-6, 7):                                                            # 注連縄：正面の柱の外、真ん中が垂れる
    put(23, fy + 3 if abs(z) < 4 else fy + 4, z, {**full('hay_block_side', top='hay_block_top'), 'axis': 'z'})
    if z in (-3, 0, 3): put(23, fy + 2, z, full('white_wool'))                    # 紙垂
for i in range(11):                                                               # 大きな桜：円の縁のすぐ外（西の口は空ける）
    a = math.pi * (0.22 + 1.56 * i / 10)
    rr = 24.5 + 1.5 * _h(i, 0, 55)
    if abs(math.sin(a)) * rr < 10: continue                                       # 西の参道の口（橋から鳥居まで）は空ける
    big_cherry(int(round(rr * math.cos(a + math.pi))) * -1, int(round(rr * math.sin(a + math.pi))), i + 1)
# 散華の間（東のすり鉢）：北の段に木の桟敷
for (x0, x1, z0, z1) in [(74, 80, -18, -13)]:
    yy = max(ground(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)) + 1
    T.deck(x0, x1, z0, z1, yy); P.cargo(x0 + 1, z0 + 1, yy, 's')
# 苔の窪：倒れた石柱と古い荷車
T.try_place(P.cart, (66, 74, ground(66, 74), 'w'), ground(66, 74))
# 古い祠の森（北、封印1）：鳥居と社、社の前に封印の台
PO.torii_shrine(0, -84, ground(0, -84), rng, gh, f='s')
SEALS['北'] = seal(3, -88)
# 滝裏の洞（西、封印2）：西の崖から幅5の滝、滝の裏の崖を掘って奥に封印の台
WX, WZ = -94, -2
fl = ground(WX + 3, WZ)
for x in range(WX - 4, WX + 2):                                                   # 洞のまわりの岩の殻
    for z in range(WZ - 5, WZ + 6):
        for y in range(ground(x, z) + 1, fl + 10): put(x, y, z, full(T.rock_at(x, y, z)))
        if ground(x, z) < fl + 9: H[(x, z)] = fl + 9
for x in range(WX - 2, WX + 2):                                                   # 洞：幅7・奥行き4・高さ4
    for z in range(WZ - 3, WZ + 4):
        for y in range(fl + 1, fl + 5): put(x, y, z, None)
        put(x, fl, z, full('mossy_cobblestone' if _h(x, z, 66) < 0.5 else 'cobblestone'))
        H[(x, z)] = fl
for z in range(WZ - 2, WZ + 3):
    for y in range(fl + 1, fl + 10):
        if get(WX + 2, y, z) is None: put(WX + 2, y, z, water())
for x in range(WX + 3, WX + 7):
    for z in range(WZ - 2, WZ + 3):
        if math.hypot(x - (WX + 4), z - WZ) < 2.6: put(x, ground(x, z), z, water())
put(WX - 1, fl + 1, WZ, full('chiseled_stone_bricks')); put(WX - 1, fl + 2, WZ, full('chiseled_stone_bricks'))
put(WX - 1, fl + 3, WZ, {'s': 'lantern', 't': 'soul_lantern'}); SEALS['西'] = [WX - 1, fl + 3, WZ]
# 隠れた空き地：崖道の脇の茂みの隙間の先に、古い大桜と宝の樽
big_cherry(87, -66, 99)
put(83, ground(83, -61) + 1, -61, P.BARREL)
# 道の途中の開けた所の中身：見晴らしには石灯籠と腰掛け、踊り場と野営跡には荷と灯り、見張り台には崩れた石の台、湧き水には小さな池
for (x, z) in [(-8, 64), (62, -70)]:                                              # 見晴らし（谷の側に灯籠2つ）
    PO.stone_lantern(x - 3, z, ground); PO.stone_lantern(x + 3, z, ground)
    T.try_place(P.bench, (x, z + 4, ground(x, z + 4), 'n', 2), ground(x, z + 4))
for (x, z, f) in [(76, 40, 'w'), (36, -78, 's')]:                                 # 踊り場・野営跡
    for (ox, oz) in ((0, 0), (-3, 0), (3, 0), (0, 3), (0, -3), (-4, 3), (4, -3)):
        cs = T.cells_of(P.cargo, x + ox, z + oz, ground(x + ox, z + oz), f)
        if T.spot_ok(cs, ground(x + ox, z + oz)): P.cargo(x + ox, z + oz, ground(x + ox, z + oz), f); break
    P.lamp_post(x - 3, z - 2, ground(x - 3, z - 2), 'e')
T.try_place(P.woodpile, (31, -86, ground(31, -86), 'n'), ground(31, -86))
for (x, z) in [(-52, -78), (-51, -78), (-50, -78), (-52, -77), (-50, -77), (-52, -76), (-51, -76), (-50, -76)]:   # 崩れた見張り台の石の台
    g = ground(x, z); hh = 1 + int(_h(x, z, 71) * 3)
    for y in range(g + 1, g + hh + 1): put(x, y, z, full('mossy_stone_bricks' if _h(x, y, z) < 0.5 else 'cracked_stone_bricks'))
for x in range(-75, -70):                                                         # 湧き水の池
    for z in range(-48, -43):
        if math.hypot(x + 72.5, z + 45.5) < 2.4: put(x, ground(x, z), z, water())
# 遺跡のアーチ：開けた所の縁に目印として [rules 109]
for (x, z, f) in [(-80, 70, 'e'), (-58, 86, 's'), (0, 86, 'e'), (68, 66, 'n'), (92, 0, 'e'), (60, -66, 'n'), (-10, -94, 'e'), (-62, -60, 'n'), (-76, 6, 'e')]:
    PO.white_arch(x, z, rng, ground, f)

# ================================================================ 4. 木：道を進むほど桜（入口 0 → 庭 1）[藍 11/06]
def bloom(x, z):
    r = math.hypot(x, z)
    if r < RAV_OUT: return 0.75 + 0.25 * (r < CLIFF)                             # 谷と山は桜（入口から谷越しに見える目標）
    return PROG[nearest(x, z)[0]] ** 1.3
def kind(x, z, rr):
    if rr < bloom(x, z) * 0.9: return 'cherry'
    return 'big' if rr < 0.04 else 'birch' if rr < 0.3 else 'oak'
def fight_floor(x, z):                                                            # 戦う所と道の上は木を置かない
    k, (d, f, rim) = nearest(x, z)
    if math.hypot(x, z) < 27 or (17 <= x <= 37 and abs(z) <= 12): return True
    if k == 'N': return math.hypot(x, z + 88) < 8 or d < -11
    return d < (1 if k in ('H', 'R1', 'W', 'S') or k[0] in 'gq' else -1.5)
for (x, z) in [(-42, 12), (-38, -20), (-20, 40), (14, 42), (40, 20), (38, -22), (12, -42), (-24, -40)]:
    big_cherry(x, z, x * 7 + z)                                                  # 谷底の大きな桜（山を桃色に囲む）
print('木・茂み', T.woods(kind=kind, skip=fight_floor))
T.ground_cover()
for (x, z), k in KIND.items():                                                    # 斜面の段の縁は苔ブロック [rules 108]
    if k != 'nat': continue
    g = H[(x, z)]; b = get(x, g, z)
    if not b or b.get('top') != 'grass_top': continue
    if max(g - H.get((x + dx, z + dz), g) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) >= 1 and _h(x, z, 57) < 0.8: put(x, g, z, full('moss_block'))
for (x, z), k in KIND.items():                                                    # 戦う床（すり鉢の底・庭）に残った木を除く
    if not (math.hypot(x - 84, z + 6) < 12 or math.hypot(x, z) < 21): continue
    g = H[(x, z)]
    for y in range(g + 1, g + 14):
        b = get(x, y, z)
        if b and ('leaves' in b.get('t', '') or '_log' in b.get('t', '') or b['s'] == 'fence') and not (math.hypot(x, z) < 21 and y > g + 4): put(x, y, z, None)
for x in range(-21, 12):                                                          # 庭の参道：西の橋の口から紋の縁まで、紋の東から拝殿まで
    for z in (-1, 0, 1):
        if math.hypot(x, z) < 9.6: continue
        if (x, z) in H and _h(x, z, 58) < 0.82:
            put(x, H[(x, z)], z, full('stone_bricks' if _h(x, z, 59) < 0.5 else 'mossy_stone_bricks' if _h(x, z, 59) < 0.8 else 'cracked_stone_bricks'))
petal = 0
CHERRY = [t for t in T.TREES if t[3] == 'cherry']
for (x, z), k in KIND.items():                                                    # 花びら：桜の多い所ほど、木の下に塊で
    g = H[(x, z)]
    p = bloom(x, z) ** 1.6 * ((0.12 + 0.3 * max(0, math.hypot(x, z) - 12) / 9) if k == 'yard' else 0.3)
    near = k == 'yard' or any(math.hypot(x - tx, z - tz) < R + 1 for (tx, tz, R, kk) in CHERRY if abs(tx - x) < 10 and abs(tz - z) < 10)
    if k in ('nat', 'yard') and get(x, g + 1, z) is None and get(x, g, z) and get(x, g, z)['s'] == 'full' and vnoise(x, z, 4, 63) * 0.75 + _h(x, z, 64) * 0.25 < p * (2.2 if near else 0.9):
        put(x, g + 1, z, {'s': 'carpet', 't': 'pink_petals'}); petal += 1
print('花びら', petal)

# ================================================================ 5. 台帳・歩ける検査・書き出し
def stand(x, z):
    g = H.get((x, z))
    if g is None: return None
    if T.BRIDGE.get((x, z)) is not None: return T.BRIDGE[(x, z)]
    b = get(x, g + 1, z)
    if b and b['s'] in ('carpet', 'cross'): b = None
    if b and b['s'] == 'water': return g
    if b and b['s'] in ('slab', 'stair'): g += 1; b = None
    if b is not None or (get(x, g + 2, z) and get(x, g + 2, z)['s'] in ('full', 'fence', 'wall')): return None
    return g
START = (-78, 80)
seen = {START}; q = deque([START]); far = {START: 0}
while q:
    x, z = q.popleft(); g = stand(x, z)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = (x + dx, z + dz)
        if n in seen: continue
        gn = stand(*n)
        if gn is not None and gn - g <= 1 and g - gn <= 2: seen.add(n); far[n] = far[(x, z)] + 1; q.append(n)
want = {'沢道': (15, 92), '尾根道': (10, 70), '苔の窪': (62, 70), '九十九折の上': (77, 15), '散華の間': (84, -6), '隠れた空き地': (85, -62),
        '古い祠の森': (0, -88), '西の岩棚': (-70, -50), '滝裏の洞の封印': (WX, WZ), '橋': (-40, -2), '庭': (0, 0)}
res = {k: min((far[(x + dx, z + dz)] for dx in range(-3, 4) for dz in range(-3, 4) if (x + dx, z + dz) in far), default=None) for k, (x, z) in want.items()}
print('歩いて行けない所', [k for k, v in res.items() if v is None] or 'なし')
print('入口からの歩数', {k: v for k, v in res.items() if v is not None})
# ================================================================ 6. 落ちない・外へ出ない（藍 11/06 F2「この隙間落ちちゃったら酷いことになる」「プレイヤーはどこにでもいけちゃう」）
# 遊ぶ所 M ＝ 歩ける検査で行けた所のうち、区域の中と縁（d ≤ 2.5）・道・橋・庭・拝殿。谷の側は崖の縁まで（床より下がった崖の面は入れない）。
# M のすぐ外の1列に見えない壁（barrier）を、隣の床+1〜+6に立てる（低いと木を伝って壁の上を歩けた）。谷へ落ちる縁には、見える縁石（苔の丸石の塀・茂み・岩）を置いて、止まる理由を見せる。
def designed(x, z):
    r = math.hypot(x, z)
    if KIND.get((x, z)) in ('hall', 'path') or T.BRIDGE.get((x, z)) is not None or r < 23: return True
    if WX - 2 <= x <= WX + 1 and abs(z - WZ) <= 3: return True                    # 滝裏の洞の中
    k, (d, f, rim) = nearest(x, z); g = H[(x, z)]
    if r < 66 and f - 1 <= g <= f + 2: return True                                # 谷を見下ろす低い縁
    return d <= 2.5 and g <= f + 3
M = {c for c in seen if designed(*c)}
FL = {c: stand(*c) for c in M}                                                    # 床（縁石を置く前に読む）
SOFT = ('carpet', 'cross', 'vine', 'lantern', 'chain')
def top_at(x, z, ymax):
    for y in range(ymax, -12, -1):
        b = get(x, y, z)
        if b and b['s'] not in SOFT: return y
    return -12
N8 = ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1))
need, lips = {}, set()
for (x, z) in M:
    fl_ = FL[(x, z)]
    for dx, dz in N8:
        n = (x + dx, z + dz)
        if n in M: continue
        lo, hi = need.get(n, (999, -999)); need[n] = (min(lo, fl_ + 1), max(hi, fl_ + 6))   # +6：木や縁石を伝って壁の上へ乗らない高さ
        if abs(dx) + abs(dz) == 1 and top_at(*n, fl_) < fl_ - 3 and math.hypot(x, z) > 34 and T.BRIDGE.get((x, z)) is None: lips.add((x, z))
for (x, z) in lips:                                                               # 縁石：落ちる縁の最後の1マス
    g = FL[(x, z)]; r_ = _h(x, z, 91)
    if get(x, g + 1, z) not in (None,) and get(x, g + 1, z)['s'] not in SOFT: continue
    put(x, g + 1, z, P.wall('mossy_cobblestone') if r_ < 0.45 else P.wall('cobblestone') if r_ < 0.65 else full('azalea_leaves' if r_ < 0.8 else 'oak_leaves') if r_ < 0.88 else full('andesite' if r_ < 0.94 else 'mossy_cobblestone'))
BAR = {}
for (x, z), (lo, hi) in need.items():
    for y in range(lo, hi + 1):
        b = get(x, y, z)
        if b is None or b['s'] in SOFT: BAR[(x, y, z)] = {'s': 'full', 't': 'barrier'}
for (x, z) in lips:                                                               # 縁石の上も（茂みと岩は乗れる）
    g = FL[(x, z)]
    for y in range(g + 2, g + 7):
        if get(x, y, z) is None: BAR[(x, y, z)] = {'s': 'full', 't': 'barrier'}
for it in range(4):                                                              # 跳び込むと上がれない小さな穴：穴の口をふさいで、もう一度歩く
    reach, back, traps, edge = EC.check(START, (-112, 111, -112, 111), BAR)
    if not traps: break
    tops = {}
    for (x, z, y) in back: tops[(x, z)] = max(tops.get((x, z), -99), y)
    for (x, z, y) in traps:
        hi = max([tops.get((x + dx, z + dz), -99) for dx in range(-3, 4) for dz in range(-3, 4)] + [y]) + 6
        for yy in range(y + 1, hi + 1):
            b = get(x, yy, z)
            if b is None or b['s'] in SOFT: BAR[(x, yy, z)] = {'s': 'full', 't': 'barrier'}
print('縁石', len(lips), '見えない壁', len(BAR), '行ける所', len(reach))
print('落ちたら戻れない所', EC.clusters(traps)[:8] or 'なし')
print('地図の外へ出られる所', EC.clusters(edge)[:5] or 'なし')
RC = {(x, z) for (x, z, y) in reach}
print('壁を立てても行ける', [k for k, (x, z) in want.items() if any((x + dx, z + dz) in RC for dx in range(-3, 4) for dz in range(-3, 4))])
ROOMS = {'spawn': [START[0], ground(*START) + 1, START[1]], 'gates': GATE_AT, 'arena_center': [0, 41, 0], 'arena_r': 19, 'seals': SEALS,
         'secret': [85, ground(85, -64) + 1, -64], 'rooms': {k: (list(geo) if isinstance(geo, tuple) and isinstance(geo[0], int) else None) for (k, kind, geo, *_ ) in AREAS if kind == 'disc'},
         'order': '入口の野 →（沢道｜尾根道）→ 苔の窪 → 滝の九十九折 → 散華の間 → 崖道（隠れた空き地）→ 古い祠の森（封印）→ 西の岩棚 → 滝裏の洞（封印）→ 谷の石橋 → 散り桜の庭'}
V = lambda x, y, z: [-x, y, z]
def look_from_ravine(cx, cz, back=22, up=34):
    """区域を、谷の側（山の方、低い側）の空から見下ろす視点。"""
    r = math.hypot(cx, cz); ux, uz = -cx / r, -cz / r
    g = ground(cx, cz)
    return [V(cx + ux * back, g + up, cz + uz * back), V(cx, g + 1, cz)]
EXTRA_VIEWS = {n: look_from_ravine(*G[n]) for n in ('q1', 'q2', 'q3', 'q4', 'q5', 'q6', 'R1', 'H', 'N', 'W', 'S')}
out = save('sakura_dungeon', {**EXTRA_VIEWS,
    'over': [V(-150, 150, 150), V(0, 10, 0)],
    'top': [V(0, 380, 1), V(0, 0, 0)],
    'enter': [V(-96, 26, 84), V(0, 40, 0)],
    'valley': [V(-30, 30, 108), V(15, 12, 90)],
    'ridge': [V(-20, 30, 60), V(20, 18, 72)],
    'room1': [V(48, 26, 86), V(64, 10, 68)],
    'switchback': [V(60, 60, 30), V(82, 18, 36)],
    'bowl': [V(70, 36, 14), V(84, 18, -6)],
    'cliffpath': [V(90, 40, -40), V(50, 26, -74)],
    'secret': [V(76, 36, -52), V(86, 28, -64)],
    'grove': [V(14, 42, -70), V(0, 30, -88)],
    'ledge': [V(-40, 46, -84), V(-72, 34, -48)],
    'cave': [V(-74, 42, -2), V(-94, 38, -2)],
    'bridge': [V(-60, 92, 40), V(-40, 38, -2)],
    'ravine': [V(-20, 30, 64), V(0, 14, 40)],
    'garden': [V(-22, 60, 0), V(8, 40, 0)],
    'shrine': [V(-6, 48, 10), V(20, 42, 0)],
    'hall': [V(4, 47, 3), V(27, 47, 0)],
    'torii': [V(2, 46, 5), V(-20, 46, -1)],
    'crest': [V(-16, 66, 0), V(2, 40, 0)],
    'edge': [V(-40, 50, 70), V(-20, 20, 58)],
    'night': [V(-22, 60, 0), V(8, 40, 0), {'night': True}],
}, V(0, 20, 0), shadowR=200, fog=(260, 620))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
if False:
    pts = [(84, -6), (82, -16), (80, -26), (76, -39), (72, -52), (66, -58), (61, -63), (56, -69), (50, -74)]
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        for i in range(6):
            x = int(ax + (bx - ax) * i / 6); z = int(az + (bz - az) * i / 6)
            print((x, z), stand(x, z), H.get((x, z)), (x, z) in seen, nearest(x, z)[0], end=' | ')
        print()
