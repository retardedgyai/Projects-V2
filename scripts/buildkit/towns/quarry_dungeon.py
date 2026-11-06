#!/usr/bin/env python3
"""石切り場の遺跡（11/06、インスタンスダンジョンの2つ目の試作）。藍「普通に今学習したものでうまくつくれそうなものも」。
今の道具で確かに作れる物だけで組む：
  - すり鉢と段（Runeforge 緑の城塞の器）：縁は森、段を渦に下りて、底で戦う。段の縁は石で縁取り、斜面は苔。
  - 切った石の面（崖の町の擁壁と岩の層）、木の桟敷（Town.deck）、クレーン（kit/harbor.crane）、荷・荷車・薪（kit/props）。
  - 白い石のアーチの遺跡（poi.white_arch）、滝と滝壺。
並び：縁の野営地（入口）→ 段1〜2の切り場（2波）→ 段3〜4の古い作業場（2波）→ 底の遺跡の円（ボス）。北の縁から入る。
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
import vox
from vox import put, get, full, log, stair, slab, fence, vnoise, _h, LANTERN, water
import poi as PO
import props as P
import harbor as HB

# ================================================================ 1. 地形：縁の台地（高さ40）と、角を落とした四角い穴を6段
TOP = 40
STEP_R, STEP_H = 7, 6                                                            # 段の幅と高さ
R0 = 46                                                                          # いちばん外の切り口
LEVELS = 6                                                                       # 段の数（底を含む）
def q(x, z):
    """切り口の形：角を落とした四角（切り出しの面はまっすぐ）と円の間。"""
    return 0.62 * max(abs(x), abs(z) * 1.1) + 0.38 * math.hypot(x, z) + (vnoise(x, z, 9, 11) - 0.5) * 2.5
def level(x, z):
    k = int((R0 - q(x, z)) // STEP_R) + 1 if q(x, z) < R0 else 0
    return min(k, LEVELS - 1)
def nat(x, z):
    k = level(x, z)
    if k == 0: return TOP + 3 * vnoise(x, z, 24, 12) + 1.2 * vnoise(x, z, 7, 13) + max(0, math.hypot(x, z) - 70) * 0.25
    return TOP - k * STEP_H + (0.6 * vnoise(x, z, 5, 14) if k < LEVELS - 1 else 0)
POOL = (14, 10)
def in_water(x, z): return level(x, z) == LEVELS - 1 and math.hypot(x - POOL[0], z - POOL[1]) < 6 + 2 * vnoise(x, z, 4, 15)

T = Town((-80, 80, -84, 80), nat, in_water)
H, KIND = T.H, T.KIND
CUT = ('stone', 'andesite', 'stone', 'tuff', 'cobblestone', 'stone')
T.rock_at = lambda x, y, z: CUT[(y // 2 + (x // 3 + z // 3) % 2) % len(CUT)] if y > 2 else 'deepslate'   # 切り出した面：2段ずつの帯を3マスごとにずらす（石の塊を切った跡）

# ================================================================ 2. 渦の坂道：段ごとに角度 120° ずつ回りながら1段下りる
def ring_pt(k, a):
    r = R0 - (k - 0.5) * STEP_R + 2.2                                            # 段 k の切り口の足もと（段の外寄り）
    return (r * math.sin(a), -r * math.cos(a))
RAMPS = []
a = 0.0
for k in range(1, LEVELS):
    yf, yt = TOP - (k - 1) * STEP_H, TOP - k * STEP_H
    pts = []
    for i in range(9):                                                            # 坂：60°で1段下りる
        t = i / 8; x, z = ring_pt(k, a + math.radians(60) * t)
        pts.append((int(round(x)), int(round(z)), int(round(yf + (yt - yf) * t))))
    for i in range(1, 5):                                                         # 段の上を 60° 歩く（戦う所）
        x, z = ring_pt(k, a + math.radians(60 + 15 * i)); pts.append((int(round(x)), int(round(z)), yt))
    RAMPS.append(pts); a += math.radians(120)
ENTRY = [(0, -72, None), (0, -60, None), (0, -50, TOP)]
T.lane(ENTRY, 2)
T.street([(0, -50, TOP)] + [RAMPS[0][0]], 2, 'lane')
for pts in RAMPS: T.street(pts, 2, 'lane')
for p0, p1 in zip(RAMPS, RAMPS[1:]): T.street([p0[-1], p1[0]], 2, 'lane')
T.resolve(blend_kinds=())
T.fill()
for (x, z), k in list(KIND.items()):                                             # 段の床：砂利・粗い土・丸石のまだら、段の縁は石で縁取り、斜面は苔
    if k != 'nat' or level(x, z) == 0: continue
    g = H[(x, z)]; b = get(x, g, z)
    if not b or b['s'] != 'full': continue
    lower = any(H.get((x + dx, z + dz), g) < g - 1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    n = vnoise(x, z, 4, 16); r = _h(x, z, 17)
    if lower: put(x, g, z, full('stone_bricks' if r < 0.4 else 'mossy_stone_bricks' if r < 0.65 else 'cobblestone' if r < 0.85 else 'andesite'))
    elif level(x, z) == LEVELS - 1: put(x, g, z, full('moss_block') if n < 0.45 else vox.grass() if n < 0.85 else full('gravel'))   # 底（戦う床）は苔と草を主に、静かに
    else: put(x, g, z, full('gravel') if n < 0.25 else full('coarse_dirt') if n < 0.45 else vox.grass() if n < 0.7 else full('moss_block') if n < 0.85 else full('cobblestone'))
T.steps()
T.vines_on_cliffs(lambda x, z: level(x, z) > 0, chance=0.18)

# ================================================================ 3. 構造物
rng = random.Random(5)
ground = lambda x, z: T.h(x, z)
BOT = TOP - (LEVELS - 1) * STEP_H
# 滝：北東の縁から、段の床を流れて縁で次の段へ落ち、底の滝壺へ
fx, fz = 26, -24
for dx in (-1, 0, 1):
    x = fx + dx; prev = None
    for z in range(fz, POOL[1] + 1):
        if math.hypot(x - POOL[0], z - POOL[1]) < 5: break
        g = ground(x, z)
        if get(x, g + 1, z) is None: put(x, g + 1, z, water())
        if prev is not None and prev > g:                                         # 段の縁：上の段の水面から下の段まで、面に沿って落ちる
            for y in range(g + 1, prev + 2): put(x, y, z, water())
        prev = g
# 縁の野営地（入口）：物置・薪・井戸・荷車・掲示板、縁にクレーン2基
T.try_place(P.shed, (-14, -66, ground(-14, -66), 5, 4), ground(-14, -66))
T.try_place(P.woodpile, (8, -64, ground(8, -64), 'w'), ground(8, -64))
T.try_place(P.well, (12, -56, ground(12, -56), 'w'), ground(12, -56))
T.try_place(P.notice_board, (-5, -58, ground(-5, -58), 's'), ground(-5, -58))
T.try_place(P.cart, (-10, -54, ground(-10, -54), 'e'), ground(-10, -54))
for (x, z, f) in [(-30, -42, 's'), (34, 30, 'w'), (-40, 18, 'e')]:
    g = ground(x, z)
    HB.crane(x, z, g, f, 6, 9)
# 段ごとの作業の跡：切った石の山（2×2×1〜2 の石レンガとなめらかな石をずらして積む）、荷、灯り
def stone_stack(x, z):
    g = ground(x, z)
    for i in range(2):
        for j in range(2):
            hh = 1 + (_h(x + i, z + j, 18) < 0.5)
            for y in range(g + 1, g + hh + 1):
                if get(x + i, y, z + j) is None: put(x + i, y, z + j, full('smooth_stone' if (i + j + y) % 2 else 'stone_bricks'))
    if get(x + 2, g + 1, z) is None: put(x + 2, g + 1, z, slab('smooth_stone'))
spots = 0
for k in range(1, LEVELS - 1):
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi); r = R0 - (k - 0.5) * STEP_R - 1
        x, z = int(r * math.sin(a)), int(-r * math.cos(a))
        if KIND.get((x, z)) != 'nat' or abs(ground(x, z) - (TOP - k * STEP_H)) > 1: continue
        if any(KIND.get((x + dx, z + dz)) == 'lane' for dx in range(-3, 4) for dz in range(-3, 4)): continue
        (stone_stack if i % 3 else lambda x, z: P.cargo(x, z, ground(x, z), 's'))(x, z); spots += 1
print('作業の跡', spots)
for pts in RAMPS:                                                                 # 坂の灯り：段の上の端と、坂の下り口
    for (x, z, y) in (pts[0], pts[8]):
        for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
            if KIND.get((x + dx, z + dz)) == 'nat' and abs(ground(x + dx, z + dz) - y) <= 1:
                P.lamp_post(x + dx, z + dz, ground(x + dx, z + dz), 'e'); break
# 段3の古い作業場：桟敷（木の台）と白い石のアーチの遺跡
for (x0, z0) in [(-24, 14), (18, -26)]:
    yy = max(ground(x, z) for x in range(x0, x0 + 7) for z in range(z0, z0 + 6)) + 1
    T.deck(x0, x0 + 6, z0, z0 + 5, yy)
    P.cargo(x0 + 1, z0 + 1, yy, 's')
# 底：遺跡の円（ボス）。白い石の柱の輪（半径11、8本のうち3本は折れて倒れている）、真ん中に古い石の台
for i in range(8):
    a = i * math.pi / 4
    x, z = int(round(11 * math.sin(a))), int(round(11 * math.cos(a)))
    if math.hypot(x - POOL[0], z - POOL[1]) < 7: continue
    g = ground(x, z); hh = 7 if i % 3 else 3
    put(x, g + 1, z, full('mossy_cobblestone'))
    for y in range(g + 2, g + hh + 1): put(x, y, z, full('polished_diorite' if y % 3 else 'diorite'))
    put(x, g + hh + 1, z, slab('polished_diorite') if hh > 3 else full('mossy_cobblestone'))
    if hh == 3:                                                                   # 折れた柱は外へ倒れて転がる
        for s in range(2, 5): put(x + int(round(s * math.sin(a))), ground(x + int(round(s * math.sin(a))), z + int(round(s * math.cos(a)))) + 1, z + int(round(s * math.cos(a))), log('stripped_birch_log', 'x' if abs(math.sin(a)) > 0.7 else 'z'))
for x in range(-2, 3):
    for z in range(-2, 3):
        put(x, ground(x, z), z, full('chiseled_stone_bricks' if (x, z) == (0, 0) else 'polished_andesite'))
for (x, z, f) in [(-16, -8, 'e'), (0, -17, 'n'), (-12, 12, 's')]:
    PO.white_arch(x, z, rng, ground, f)

# ================================================================ 4. 縁の森と下草
def kind(x, z, r): return 'spruce' if r < 0.3 else 'birch' if r < 0.5 else 'big' if r < 0.55 else 'oak'
def keep_open(x, z): return level(x, z) > 0 or math.hypot(x, z) < R0 + 5 or abs(x) < 6 and z < -40
print('木・茂み', T.woods(kind=kind, skip=keep_open))
T.ground_cover()
for (x, z), k in KIND.items():                                                    # 縁の斜面は苔ブロック
    if k != 'nat' or level(x, z) > 0: continue
    g = H[(x, z)]; b = get(x, g, z)
    if b and b.get('top') == 'grass_top' and max(g - H.get((x + dx, z + dz), g) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) >= 1 and _h(x, z, 19) < 0.8:
        put(x, g, z, full('moss_block'))

# ================================================================ 5. 台帳と書き出し
ROOMS = {'spawn': [0, ground(0, -68) + 1, -68], 'arena_center': [0, BOT + 1, 0], 'arena_r': 13,
         'rooms': {'縁の野営地': [0, -60], '切り場（段1〜2）': [ring_pt(1, 0.5)[0], ring_pt(1, 0.5)[1]], '古い作業場（段3〜4）': [ring_pt(3, 4.7)[0], ring_pt(3, 4.7)[1]], '遺跡の円': [0, 0]},
         'gates': [list(RAMPS[k][0]) for k in (1, 3, 4)]}
V = lambda x, y, z: [-x, y, z]
out = save('quarry_dungeon', {
    'over': [V(90, 110, 110), V(0, 14, 0)],
    'top': [V(0, 260, 1), V(0, 0, 0)],
    'enter': [V(0, 48, -78), V(0, 30, -30)],
    'rim': [V(-44, 70, -60), V(0, 10, 0)],
    'ramp': [V(-36, 46, -36), V(10, 20, 10)],
    'floor': [V(-4, 26, -18), V(0, 10, 4)],
    'falls': [V(0, 20, 8), V(26, 22, -24)],
    'night': [V(90, 110, 110), V(0, 14, 0), {'night': True}],
}, V(0, 14, 0), shadowR=140, fog=(200, 480))
json.dump(ROOMS, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print('書き出し', out)
