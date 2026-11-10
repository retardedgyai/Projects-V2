#!/usr/bin/env python3
"""オープンワールドの野外：開拓者の町の東門から、根の丘を抜けて、根の洞の入口まで（320×256）。

決まりは rules/openworld.md（Wynncraft・Drehmal・RuneForge と地図づくりの講座 28 本から）。
遊びの流れ（docs/proposals/world-prototype の最初の 1 時間）：
  町の東門 → 門前の畑（甲虫）→ 丘の間の谷の崩れた門 → 分かれ道 → 北：根の丘の大樹（伐採）／南：池と石の輪（狼・皮剥ぎ・苔）
  → 崩れた礼拝堂の野営地 → 石の橋 → 岩山の裾の採掘場（採鉱・採石）→ 岩壁の間の道 → 石段 → 根の洞の門
  python3 towns/ow_frontier.py [名前] [--stage N]   stage 1 地形 / 2 道の縁 / 3 建物 / 4 木 / 9 草まで全部
"""
import json, math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import numpy as np
from vox import put, get, full, log, stair, slab, fence, cross, grass, water
from town import save
import ow, ow_trees as OT, trees as TR, props as PR

NAME = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith('--') else 'ow_frontier'
STAGE = int(sys.argv[sys.argv.index('--stage') + 1]) if '--stage' in sys.argv else 9
W, D = 320, 256
L = ow.Land(W, D, seed=21, base=10.0, sea=6)
rng = random.Random(77)
MARKS = []                                            # 遊びの印（地図用）：(種類, x, z, 名前)

# ---------------------------------------------------------------- 大きな形：西は丘、真ん中に川、東は岩山
L.hill(88, 58, 48, 27, rz=40, rot=0.3); L.hill(64, 44, 26, 9); L.hill(112, 76, 22, 7)        # 北の丘と肩
L.hill(102, 200, 52, 23, rz=38, rot=-0.25); L.hill(78, 216, 26, 8); L.hill(128, 186, 20, 6)   # 南の丘と肩
L.hill(178, 56, 40, 30, rz=36, rot=0.5, sharp=2.4); L.hill(156, 40, 22, 9)                    # 根の丘（大樹。この地域の大きな目印）
L.hill(40, 86, 22, 7); L.hill(150, 156, 26, 8, rz=18, rot=0.6); L.hill(56, 176, 20, 6); L.hill(250, 196, 30, 17, rz=24, rot=0.4)
L.hill(140, 228, 34, 10, rz=22); L.hill(22, 36, 30, 12); L.hill(244, 40, 26, 9); L.hill(24, 214, 30, 11)
L.mesa(288, 50, 50, 30, rz=46, rot=0.2, edge=7)       # 東の岩山（北）
L.mesa(298, 184, 50, 34, rz=58, rot=-0.15, edge=7)    # 東の岩山（南）
L.mesa(300, 44, 26, 15, rz=22, rot=0.6, edge=5); L.mesa(304, 192, 26, 16, rz=32, rot=0.3, edge=5)   # 2 段目
L.mesa(326, 112, 34, 46, rz=44, edge=6)               # 道の突き当たり（門が入る壁）
for (x, z, r, h) in [(70, 118, 5, 5), (122, 142, 6, 6), (58, 150, 5, 4), (132, 96, 6, 7), (186, 152, 5, 5), (238, 140, 7, 8),
                     (244, 100, 6, 7), (96, 92, 6, 6), (30, 150, 6, 5), (166, 100, 5, 5), (112, 232, 7, 6), (200, 28, 6, 6)]:
    L.mesa(x, z, r, h, edge=2.6, dome=1.0)            # 岩の出っ張り（階段状に積んだ岩）[7]
L.flat(159, 137, 12); L.flat(34, 104, 15); L.flat(9, 128, 13); L.flat(176, 58, 9); L.flat(190, 190, 9); L.flat(250, 196, 6)
POND = (170, 214, 11)
dd = np.hypot(L.X - POND[0], (L.Z - POND[1]) * 1.25) + (ow.noise(W, D, 8, 5) - 0.5) * 5
L.H = np.where(dd < POND[2] + 6, np.minimum(L.H, 3.5 + np.clip((dd - POND[2] + 3) / 9, 0, 1) ** 1.5 * 5.5), L.H)
L.river_d = np.minimum(L.river_d, dd - POND[2])

RIVER = L.river([(214, -4), (206, 50), (222, 100), (212, 150), (228, 200), (218, 260)], width=11, depth=4)
MAIN = [(2, 128), (40, 124), (70, 137), (104, 127), (140, 118), (172, 127), (204, 124)]
L.road(MAIN, width=8)
L.road([(229, 123), (248, 124), (266, 113), (292, 112)], width=8)
NORTH = [(140, 118), (150, 96), (164, 80), (171, 66)]
SOUTH = [(118, 124), (130, 160), (156, 190), (188, 198), (204, 152), (197, 127)]
L.road(NORTH, width=4, sink=0.5, kind='branch'); L.road(SOUTH, width=4, sink=0.5, kind='branch')
L.finish()
L.kind[(L.X > 282) & (L.kind == ow.GRASS) & (L.road_d > 0) & (L.road_d < 9)] = ow.TALUS                 # 岩壁の間の道の脇は石
gate_m = np.hypot(L.X - 10, L.Z - 128) < 12 + (ow.noise(W, D, 5, 9) - 0.5) * 6          # 門前は石畳が土へ溶ける [18]
L.kind[gate_m & (L.road_d < 3)] = ow.PAVE
L.kind[(np.hypot(L.X - 159, L.Z - 137) < 9) & (ow.noise(W, D, 3, 11) > 0.55)] = ow.PAVE     # 礼拝堂の床の名残
L.kind[(np.hypot(L.X - 34, L.Z - 104) < 12) & (ow.noise(W, D, 4, 12) > 0.6) & (L.kind == ow.GRASS)] = ow.DIRT  # 使っている場所は土 [nature 54]

PAL = dict(
    rock=[['tuff', 'cobbled_deepslate', 'tuff', 'andesite'], ['andesite', 'stone', 'cobblestone', 'stone'],
          ['stone', 'stone', 'andesite', 'stone', 'cobblestone'], ['stone', 'andesite', 'stone', 'stone']],
    isle=['cobblestone', 'gravel', 'stone_bricks', 'andesite', 'cobblestone'], bed=['gravel', 'sand', 'clay', 'stone', 'dirt'],
    pave=['cobblestone', 'stone_bricks', 'andesite', 'mossy_cobblestone', 'gravel'])
L.build(PAL)
P, T = L.P, L.top
STONES = ['stone_bricks'] * 5 + ['cracked_stone_bricks', 'mossy_stone_bricks', 'cobblestone', 'andesite', 'mossy_cobblestone']
def st(r=rng): return full(r.choice(STONES))
HAY = {'s': 'full', 't': 'hay_block_side', 'top': 'hay_block_top', 'bot': 'hay_block_top'}
BARREL = {'s': 'full', 't': 'barrel_side', 'top': 'barrel_top', 'bot': 'barrel_bottom'}
BONE = {'s': 'full', 't': 'bone_block_side', 'top': 'bone_block_top', 'bot': 'bone_block_top'}
LANT = {'s': 'lantern', 't': 'lantern'}
def wallb(t): return {'s': 'wall', 't': t}
def banner(x, y0, y1, z, f, c='red_wool'):
    for y in range(y0, y1 + 1): P(x, y, z, {'s': 'trap', 't': c if y not in (y0, y1 - 1) else 'yellow_wool', 'f': f})

def campfire(x, z, seats=2):
    g = T(x, z); P(x, g, z, full('coarse_dirt')); P(x, g + 1, z, cross('fire_0'))
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)): P(x + dx, T(x + dx, z + dz) + 1, z + dz, slab('cobblestone'))
    for k in range(seats):
        a = rng.random() * 6.283; sx, sz = x + int(round(math.cos(a) * 3)), z + int(round(math.sin(a) * 3))
        P(sx, T(sx, sz) + 1, sz, log('spruce_log', 'x' if abs(math.sin(a)) > 0.7 else 'z'))
    L.claim(x, z, 3)

def tent(x, z, c='white_wool', ax='x'):
    g = max(T(x + i, z + j) for i in range(-2, 3) for j in range(-2, 3))
    for k in range(-2, 3):
        for s in (-1, 1):
            a, b = (x + k, z + s) if ax == 'x' else (x + s, z + k); P(a, g + 1, b, full(c))
        a, b = (x + k, z) if ax == 'x' else (x, z + k); P(a, g + 2, b, full(c if k % 2 else 'brown_wool'))
    for k in (-2, 2):
        a, b = (x + k, z) if ax == 'x' else (x, z + k); P(a, g + 1, b, fence('spruce_planks'))
    L.claim(x, z, 4)

def signpost(x, z, arms=('e', 'n')):
    g = T(x, z); P(x, g + 1, z, log('spruce_log')); P(x, g + 2, z, fence('spruce_planks')); P(x, g + 3, z, fence('spruce_planks'))
    D4 = {'e': (1, 0), 'w': (-1, 0), 'n': (0, -1), 's': (0, 1)}
    for i, a in enumerate(arms): P(x + D4[a][0], g + 3 - i, z + D4[a][1], slab('dark_oak_planks', 'top'))
    P(x, g + 4, z, LANT); L.claim(x, z, 2); MARKS.append(('sign', x, z, '道しるべ'))

def boulder(x, z, r, h, seed):
    """段々に積んだ岩：下ほど広く、上に草か苔。根元に小石と草 [7]。"""
    q = random.Random(seed); g = min(T(x + i, z + j) for i in (-r, 0, r) for j in (-r, 0, r))
    for y in range(h):
        rr = r * (1 - y / (h + 0.8)) + 0.4; cx, cz = x + q.uniform(-0.8, 0.8), z + q.uniform(-0.8, 0.8)
        for i in range(-r - 1, r + 2):
            for j in range(-r - 1, r + 2):
                if math.hypot(x + i - cx, (z + j - cz) * q.uniform(0.9, 1.2)) <= rr + q.uniform(-0.4, 0.3):
                    for yy in range(T(x + i, z + j) + 1 if y == 0 else g + y + 1, g + y + 2):
                        P(x + i, yy, z + j, full(q.choice(['stone', 'stone', 'andesite', 'cobblestone', 'mossy_cobblestone' if y < 2 else 'stone'])))
    L.claim(x, z, r + 1)

# ================================================================ 建物と見どころ（木より先に場所を取る）
if STAGE >= 3:
    # ---------------- 町の東門：城壁と門の塔 2 つ。門前 10〜15 マスは石畳 [18]
    gy = T(9, 128); WT = gy + 9
    for z in range(70, 187):
        if 123 <= z <= 133: continue
        for x in (4, 5, 6):
            for y in range(T(x, z) - 1, WT + 1): P(x, y, z, st())
        if z % 2 == 0: P(6, WT + 1, z, st()); P(4, WT + 1, z, st())
        if z % 9 == 0:
            for y in range(T(7, z) + 1, WT - 1): P(7, y, z, st())                       # 控え壁
    for z0 in (117, 134):                                                              # 門の塔（6×6、高さ 16）
        for x in range(3, 10):
            for z in range(z0, z0 + 6):
                edge = x in (3, 9) or z in (z0, z0 + 5)
                for y in range(T(x, z) - 1, gy + 17):
                    if edge or y == gy + 15: P(x, y, z, st())
                if edge and (x + z) % 2 == 0: P(x, gy + 17, z, st())
        for y in range(gy + 9, gy + 12): P(9, y, z0 + 2, None); P(9, y, z0 + 3, None)    # 矢狭間
        banner(10, gy + 4, gy + 9, z0 + 2, 'w'); banner(10, gy + 4, gy + 9, z0 + 3, 'w')
        P(10, gy + 3, z0 + (5 if z0 == 117 else 0), LANT); P(10, gy + 2, z0 + (5 if z0 == 117 else 0), wallb('cobblestone')); P(10, gy + 1, z0 + (5 if z0 == 117 else 0), wallb('cobblestone'))
    for z in range(123, 134):                                                          # 門のアーチ（厚さ 5、上に歩廊と狭間）
        for x in range(3, 8):
            top = gy + 8 - (0 if 126 <= z <= 130 else (1 if z in (125, 131) else (3 if z in (124, 132) else 7)))
            for y in range(top, gy + 13): P(x, y, z, st())
            if x in (3, 7) and z % 2 == 0: P(x, gy + 13, z, st())
    for z in (124, 132):                                                               # 開いた木の扉
        for y in range(gy + 1, gy + 6): P(8, y, z, full('dark_oak_planks')); P(9, y, z, full('dark_oak_planks') if y % 2 else full('spruce_planks'))
    for x in range(0, 3):                                                              # 門の内側：町の石畳と家並みの影
        for z in range(118, 139):
            for y in range(T(x, z) + 1, gy + (9 if z % 7 < 4 else 6)):
                if not 124 <= z <= 132: P(x, y, z, full('spruce_planks' if y > gy + 4 else 'stone_bricks'))
    L.claim(8, 128, 14); MARKS.append(('town', 9, 128, '開拓者の町 東門'))

    # ---------------- 門前の畑と農家（甲虫の狩り場）[22・27]
    fx, fz = 26, 99
    PR.garden(fx - L.ox, fz - L.oz, T(fx + 5, fz + 3), 13, 7)
    PR.garden(fx + 2 - L.ox, fz + 10 - L.oz, T(fx + 6, fz + 12), 9, 5, crops=('carrots_stage3', 'beetroots_stage3'))
    PR.shed(fx + 15 - L.ox, fz - 3 - L.oz, T(fx + 17, fz - 1), 6, 5)
    for (x, z) in ((fx + 15, fz + 5), (fx + 16, fz + 5), (fx + 15, fz + 6)): P(x, T(x, z) + 1, z, HAY)
    P(fx + 15, T(fx + 15, fz + 5) + 2, fz + 5, HAY); P(fx + 18, T(fx + 18, fz + 6) + 1, fz + 6, BARREL)
    PR.cart(fx + 13 - L.ox, fz + 12 - L.oz, T(fx + 13, fz + 12), 'e')
    for (x0, z0, n, ax) in ((fx - 2, fz - 2, 7, 'x'), (fx - 2, fz - 2, 5, 'z'), (fx + 8, fz + 17, 6, 'x'), (fx + 22, fz + 2, 6, 'z')):   # 柵は切れはし [19]
        for k in range(n):
            x, z = (x0 + k, z0) if ax == 'x' else (x0, z0 + k); P(x, T(x, z) + 1, z, fence('spruce_planks'))
    L.claim(fx + 8, fz + 6, 15); MARKS.append(('hunt', fx + 6, fz + 6, '畑の甲虫 Lv1〜2'))

    # ---------------- 谷の口の崩れた門：道をまたぐ石の柱 2 本と折れたアーチ（高さ 12）[22・24]
    ax_, az_ = 99, 128
    for (dz, h) in ((-7, 12), (7, 8)):
        for x in range(ax_ - 1, ax_ + 2):
            for z in range(az_ + dz - 1, az_ + dz + 2):
                for y in range(T(x, z) - 1, T(ax_, az_) + h + (0 if (x + z) % 3 else -1)): P(x, y, z, st())
    gy2 = T(ax_, az_)
    for k, dz in enumerate(range(-5, 0)):                                               # 北の柱から伸びて途中で折れたアーチ
        for x in range(ax_ - 1, ax_ + 2): P(x, gy2 + 11 + min(k, 2), az_ + dz, st()); P(x, gy2 + 12 + min(k, 2), az_ + dz, st() if k < 4 else slab('stone_bricks'))
    for (x, z) in ((ax_ + 3, az_ + 3), (ax_ + 4, az_ + 3), (ax_ + 3, az_ + 4), (ax_ - 3, az_ + 5), (ax_ + 2, az_ + 6)): P(x, T(x, z) + 1, z, st())   # 落ちた石
    banner(ax_ - 2, gy2 + 6, gy2 + 10, az_ - 7, 'e')
    L.claim(ax_, az_, 9); MARKS.append(('mark', ax_, az_, '崩れた門'))

    # ---------------- 分かれ道の道しるべ [21]
    signpost(122, 119, ('e', 's')); signpost(144, 112, ('e', 'n')); signpost(194, 131, ('e', 's')); signpost(232, 128, ('e', 'w'))

    # ---------------- 崩れた礼拝堂と野営地（道のすぐ南。焚き火 4 つが敵の印）[23・27]
    cx0, cz0, cw, cd = 152, 132, 15, 11; cg = T(159, 137)
    hw_ = lambda i: [10, 9, 7, 6, 4, 5, 6, 3, 2, 4, 5, 3, 2, 1, 2][i]                    # 西ほど高く残る
    for i in range(cw):
        for (z, face) in ((cz0, 'n'), (cz0 + cd - 1, 's')):
            x = cx0 + i; h = hw_(i) - (2 if face == 'n' and i in (6, 7) else 0)
            if face == 'n' and i in (9, 10): continue                                    # 道側の入口
            for y in range(cg + 1, cg + 1 + h):
                if 3 <= y - cg <= 5 and i % 4 == 2 and h > 5: continue                   # 窓
                P(x, y, z, st())
    for j in range(cd):
        for y in range(cg + 1, cg + 11 + (3 - abs(j - cd // 2)) * 1):                    # 西の妻壁（一番高い。丸窓）
            if 6 <= y - cg <= 8 and abs(j - cd // 2) <= 1: continue
            P(cx0, y, cz0 + j, st())
        if rng.random() < 0.6: P(cx0 + cw - 1, cg + 1, cz0 + j, st())
    for i in (3, 7, 11):
        for j in (3, cd - 4):
            for y in range(cg + 1, cg + 1 + rng.randint(2, 6)): P(cx0 + i, y, cz0 + j, full('stone_bricks') if y % 3 else full('chiseled_stone_bricks'))
    for _ in range(14):
        x, z = cx0 + rng.randint(-3, cw + 2), cz0 + rng.randint(-3, cd + 2)
        if get(x - L.ox, T(x, z) + 1, z - L.oz) is None: P(x, T(x, z) + 1, z, st() if rng.random() < 0.6 else slab('cobblestone'))
    campfire(149, 128); campfire(171, 131); campfire(160, 147); campfire(144, 141)
    tent(172, 139, 'white_wool', 'z'); tent(147, 147, 'light_gray_wool', 'x')
    for (x, z) in ((166, 130), (167, 130), (166, 129)): P(x, T(x, z) + 1, z, BARREL)
    P(153, T(153, 144) + 1, 144, HAY); P(154, T(154, 144) + 1, 144, HAY)
    L.claim(159, 137, 13); MARKS.append(('hunt', 159, 139, '野営地の野盗 Lv3〜4'))

    # ---------------- 石の橋（川幅 11。太鼓橋、欄干、両端の灯り）[30]
    bz, x0, x1 = 123, 203, 230; y0, y1 = T(203, 124), T(230, 123)
    for x in range(x0, x1 + 1):
        t = (x - x0) / (x1 - x0); fy = y0 + (y1 - y0) * t + 2.4 * math.sin(t * math.pi); dy = int(math.floor(fy + 0.25)); half = fy + 0.25 - dy >= 0.5
        for z in range(bz - 4, bz + 5):
            P(x, dy, z, st()); P(x, dy - 1, z, st())
            if half and abs(z - bz) < 4: P(x, dy + 1, z, slab('stone_bricks'))
            if abs(z - bz) == 4: P(x, dy + 1, z, st()); P(x, dy + 2, z, wallb('stone_bricks') if x % 3 else st())
        if x in (x0, x1) or x in (x0 + 9, x1 - 9):
            for z in (bz - 4, bz + 4): P(x, dy + 2, z, st()); P(x, dy + 3, z, wallb('cobblestone')); P(x, dy + 4, z, LANT)
    for px in (209, 224):                                                                # 橋脚と半円のアーチ
        for x in range(px - 1, px + 2):
            for z in range(bz - 3, bz + 4):
                for y in range(T(x, z), y0 + 2): P(x, y, z, st())
        for (dx, dy_) in ((-2, 0), (2, 0), (-3, 1), (3, 1)):
            for z in range(bz - 3, bz + 4): P(px + dx, y0 + dy_, z, st())
    L.claim(216, 123, 16); MARKS.append(('mark', 216, 123, '石の橋'))

    # ---------------- 石の輪（狼の狩り場・皮剥ぎ）：7 本の立石と骨 [27]
    sx, sz = 190, 190
    for k in range(7):
        a = k * 6.283 / 7 + 0.3; x, z = sx + int(round(math.cos(a) * 6)), sz + int(round(math.sin(a) * 6)); h = [5, 3, 4, 2, 5, 4, 3][k]
        for y in range(T(x, z) + 1, T(x, z) + 1 + h): P(x, y, z, full(rng.choice(['stone', 'andesite', 'mossy_cobblestone', 'cobblestone'])))
        if h >= 4: P(x + (1 if math.cos(a) < 0 else -1), T(x, z) + 1, z, full('mossy_cobblestone'))
    for (dx, dz, hh) in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (-2, 1, 1)):
        for y in range(hh): P(sx + dx, T(sx + dx, sz + dz) + 1 + y, sz + dz, BONE)
    L.claim(sx, sz, 9); MARKS.append(('hunt', sx, sz, '石の輪の狼 Lv4〜6')); MARKS.append(('skin', sx, sz + 1, '皮剥ぎ'))

    # ---------------- 崩れた見張り塔（南東の丘の頂、高さ 19。中の目印）[24]
    tx_, tz_ = 250, 196; tg = T(tx_, tz_)
    for a in range(72):
        an = a * 6.283 / 72; x, z = tx_ + int(round(math.cos(an) * 4.4)), tz_ + int(round(math.sin(an) * 4.4))
        hh = 19 - int(7 * max(0, math.cos(an - 2.2)) ** 2) - (a % 5 == 0)
        for y in range(T(x, z), tg + hh):
            if (y - tg) in (6, 7, 12, 13) and a % 18 == 4: continue
            P(x, y, z, st())
    for i in range(-3, 4):
        for j in range(-3, 4):
            if math.hypot(i, j) < 3.9 and (i + j) % 5: P(tx_ + i, tg + 10, tz_ + j, full('spruce_planks'))
    banner(tx_ + 5, tg + 11, tg + 16, tz_, 'w')
    for (dx, dz) in ((6, 2), (7, 2), (6, 3), (-6, -3), (5, -5)): P(tx_ + dx, T(tx_ + dx, tz_ + dz) + 1, tz_ + dz, st())
    L.claim(tx_, tz_, 8); MARKS.append(('mark', tx_, tz_, '崩れた見張り塔'))

    # ---------------- 岩山の裾の採掘場（採鉱・採石）：岩肌の銅鉱石、木の足場、荷車 [27]
    mx, mz = 246, 82
    cand = [(x, z) for x in range(mx - 14, mx + 15) for z in range(mz - 14, mz + 15) if L.kind[z, x] == ow.ROCK and L.Hi[z, x] - L.low[z, x] >= 2]
    cand.sort(key=lambda p: math.hypot(p[0] - mx, p[1] - mz)); ore = cand[:26]
    for (x, z) in ore:
        for y in range(int(L.low[z, x]) + 1, int(L.Hi[z, x]) + 1):
            if rng.random() < 0.5: P(x, y, z, full('copper_ore'))
    if ore:
        ox_, oz_ = ore[0]; og = T(ox_ - 4, oz_)
        for i in range(-3, 3):
            for (dz) in (-2, 2):
                for y in range(T(ox_ - 4 + i, oz_ + dz) + 1, og + 4):
                    if i in (-3, 2): P(ox_ - 4 + i, y, oz_ + dz, fence('spruce_planks'))
            for dz in range(-2, 3): P(ox_ - 4 + i, og + 4, oz_ + dz, slab('spruce_planks', 'top'))
        PR.cart(ox_ - 9 - L.ox, oz_ + 4 - L.oz, T(ox_ - 9, oz_ + 4), 'w')
        for (dx, dz) in ((-7, -3), (-8, -3), (-7, -4)): P(ox_ + dx, T(ox_ + dx, oz_ + dz) + 1, oz_ + dz, full('andesite'))
        P(ox_ - 7, T(ox_ - 7, oz_ - 3) + 2, oz_ - 3, full('andesite'))
        L.claim(ox_ - 5, oz_, 9); MARKS.append(('ore', ox_, oz_, '採鉱（銅）')); MARKS.append(('stone', ox_ - 7, oz_ - 3, '採石'))

    # ---------------- 根の洞の門：岩壁の間の道 → 石段 → 高さ 26 の門。上から巨大な根が這い下りる [28]
    dx0, dz0 = 300, 112; rg = T(292, 112)
    for k in range(9):                                                                   # 石段（幅 11）
        for z in range(dz0 - 5, dz0 + 6):
            for y in range(min(T(291 + k, z), rg), rg + 1 + (k + 1) // 2): P(291 + k, y, z, st())
            for y in range(rg + 1 + (k + 1) // 2 + 1, rg + 16):
                if abs(z - dz0) <= 5: P(291 + k, y, z, None)
            if (k + 1) % 2: P(291 + k, rg + 1 + (k + 1) // 2, z, stair('stone_bricks', 'w'))
    fg = rg + 5
    DS = ['deepslate_bricks'] * 4 + ['deepslate_tiles', 'cracked_deepslate_bricks', 'polished_deepslate', 'cobbled_deepslate']
    for z in range(dz0 - 11, dz0 + 12):
        top = fg + 26 - abs(z - dz0) // 2
        for x in range(dx0, dx0 + 4):
            for y in range(rg - 2, top + 1):
                opening = abs(z - dz0) <= 3 and y <= fg + 10 - (abs(z - dz0) == 3) * 1 - max(0, abs(z - dz0) - 1)
                if opening and y > fg: P(x, y, z, None if x < dx0 + 3 else full('black_concrete'))
                else: P(x, y, z, full(rng.choice(DS)))
        for x in range(dx0 + 4, dx0 + 10):                                               # 奥の暗がり
            for y in range(fg + 1, fg + 11):
                if abs(z - dz0) <= 3: P(x, y, z, full('black_concrete'))
    for zz in (dz0 - 6, dz0 + 6):                                                        # 門の両脇の柱と火
        for x in range(dx0 - 2, dx0):
            for z in range(zz - 1, zz + 2):
                for y in range(rg - 1, fg + 15): P(x, y, z, full(rng.choice(DS)) if (y - fg) % 5 else full('chiseled_deepslate'))
        P(dx0 - 1, fg + 15, zz, full('magma')); P(dx0 - 1, fg + 16, zz, cross('fire_0'))
        banner(dx0 - 3, fg + 6, fg + 13, zz, 'e')
    for k in range(7):                                                                   # 巨大な根（dark_oak）：門の上から扉のまわりへ
        z = dz0 + [-10, -7, -4, 4, 6, 9, 1][k]; y = fg + 26 - abs(z - dz0) // 2; x = dx0 - 1; end = fg + [3, 8, 11, 12, 6, 2, 13][k]
        while y > end:
            P(x, y, z, log('dark_oak_log', 'y')); P(x, y, z + 1, log('dark_oak_log', 'y')) if k % 2 else None
            y -= 1
            if rng.random() < 0.3 and abs(z - dz0) > 4: z += rng.choice([-1, 1]); P(x, y + 1, z, log('dark_oak_log', 'z'))
    L.claim(296, 112, 12); MARKS.append(('dungeon', 300, 112, '根の洞（T2 ダンジョン）'))
    for (x, z) in ((286, 106), (286, 118)):                                              # 目安の札の柱と灯り
        g = T(x, z); P(x, g + 1, z, wallb('cobblestone')); P(x, g + 2, z, wallb('cobblestone')); P(x, g + 3, z, LANT)

    # ---------------- 岩（道ばたと丘の腹）
    for i, (x, z, r, h) in enumerate([(52, 131, 3, 3), (86, 141, 4, 4), (128, 110, 3, 3), (182, 118, 4, 4), (240, 130, 4, 5), (258, 106, 3, 4), (64, 96, 4, 4),
                                      (110, 164, 3, 3), (160, 170, 4, 3), (200, 70, 4, 4), (36, 160, 3, 3), (270, 128, 4, 5), (176, 226, 3, 3), (20, 60, 4, 4)]):
        if L.free(x, z, r): boulder(x, z, r, h, 100 + i)
    for (x, z) in ((176, 226), (163, 205), (181, 208)):                                  # 池のほとりの苔（植物の採取）
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)):
            if L.kind[z + dz, x + dx] in (ow.GRASS, ow.SAND, ow.MUD): P(x + dx, T(x + dx, z + dz), z + dz, full('moss_block'))
    MARKS.append(('herb', 163, 205, '苔（植物）'))

# ================================================================ 木 [12〜16]
def can_tree(x, z, r):
    if not L.free(x, z, r): return False
    w = L.Hi[z - 2:z + 3, x - 2:x + 3]; return w.max() - w.min() <= 2 and L.kind[z, x] == ow.GRASS
def oak(x, z, size, seed, **kw):
    if not can_tree(x, z, 2): return False
    OT.field_oak(x - L.ox, z - L.oz, T(x, z), seed, size, **kw); L.claim(x, z, 3 if size < 1.3 else 5); return True
NTREE = 0
if STAGE >= 4:
    # 根の丘の大樹（地域の大きな目印。冠の幅 約 40、高さ 約 34）と、根元の伐採場
    gx, gz = 176, 58
    OT.field_oak(gx - 2 - L.ox, gz - 2 - L.oz, T(gx, gz), 9, 2.3, logt='dark_oak_log', leaves=('dark_oak_leaves', 'oak_leaves', 'azalea_leaves'))
    L.claim(gx, gz, 9); MARKS.append(('mark', gx, gz, '根の丘の大樹'))
    for (x, z, ax) in ((166, 66, 'x'), (166, 68, 'x'), (185, 68, 'z')):
        for k in range(4):
            a, b = (x + k, z) if ax == 'x' else (x, z + k); P(a, T(a, b) + 1, b, log('oak_log', ax))
            if k < 3 and z != 68: P(a, T(a, b) + 2, b, log('oak_log', ax))
    OT.stump(160 - L.ox, 62 - L.oz, T(160, 62), 3); OT.stump(188 - L.ox, 50 - L.oz, T(188, 50), 4); tent(186, 62, 'green_wool', 'z')
    L.claim(166, 67, 4); L.claim(185, 69, 3); MARKS.append(('wood', 166, 67, '伐採'))
    # 丘の頂に 1 本ずつ（空を背にする）[13]
    for (x, z, s) in ((88, 57, 1.25), (103, 199, 1.2), (150, 156, 1.0), (22, 36, 1.1), (244, 40, 1.0), (40, 86, 0.95), (140, 228, 1.1), (24, 214, 1.05)):
        NTREE += oak(x, z, s, 300 + x)
    # 礼拝堂にかぶさる大木 2 本と、門前・橋のたもとの木
    for (x, z, s) in ((148, 136, 1.35), (170, 146, 1.2), (20, 114, 1.0), (22, 142, 1.1), (196, 114, 1.15), (199, 136, 1.0), (236, 114, 1.0)):
        NTREE += oak(x, z, s, 400 + x)
    # 道沿い：20〜40 マスあきで 1 本ずつ、道の縁から 6〜10 離して左右交互 [13]
    for R in L.roads[:1] + L.roads[2:]:
        line = R['line']; i = 18; side = 1
        while i < len(line) - 12:
            t = line[i + 1] - line[i]; n = np.array([-t[1], t[0]]) / (np.linalg.norm(t) + 1e-9)
            p = line[i] + n * side * (R['w'] / 2 + rng.uniform(6, 10)); ok = oak(int(p[0]), int(p[1]), rng.uniform(0.9, 1.15), 500 + i)
            NTREE += ok; i += rng.randint(22, 38) if ok else 6; side = -side
    # 木立ち：7〜10 本、幹の間 8〜14 [13]
    for (cx, cz, n, rad) in ((58, 74, 8, 20), (70, 176, 9, 22), (128, 60, 7, 18), (118, 238, 7, 18), (30, 186, 6, 16), (186, 24, 6, 16)):
        k = 0; tries = 0
        while k < n and tries < 200:
            tries += 1; a = rng.random() * 6.283; q = rad * math.sqrt(rng.random()); x, z = int(cx + math.cos(a) * q), int(cz + math.sin(a) * q)
            if 6 < x < W - 6 and 6 < z < D - 6 and oak(x, z, rng.uniform(0.85, 1.2), 600 + tries + cx): k += 1
        NTREE += k
    # 川の東はマツ（木の型で地域の替わり目を見せる）[16・32]：岩山の上と裾に 5〜8 本の固まり
    for (cx, cz, n, rad) in ((286, 46, 9, 22), (300, 186, 10, 26), (262, 150, 7, 14), (252, 66, 6, 12), (238, 220, 6, 14), (272, 232, 5, 12), (240, 18, 5, 12)):
        k = 0; tries = 0
        while k < n and tries < 200:
            tries += 1; a = rng.random() * 6.283; q = rad * math.sqrt(rng.random()); x, z = int(cx + math.cos(a) * q), int(cz + math.sin(a) * q)
            if 6 < x < W - 6 and 6 < z < D - 6 and can_tree(x, z, 2):
                OT.pine(x - L.ox, z - L.oz, T(x, z), 700 + tries + cx, rng.randint(18, 30)); L.claim(x, z, 3); k += 1
        NTREE += k
    # 茂み：木と岩の足元、森の縁 [nature 31・36]
    nb = 0
    for _ in range(900):
        x, z = rng.randint(4, W - 6), rng.randint(4, D - 6)
        if L.kind[z, x] != ow.GRASS or L.road_d[z, x] < 3 or get(x - L.ox, T(x, z) + 1, z - L.oz) is not None: continue
        near = L.keep[max(0, z - 5):z + 6, max(0, x - 5):x + 6].mean()
        if 0.08 < near < 0.6 and not L.keep[z, x] and L.Hi[z:z + 3, x:x + 3].max() == L.Hi[z:z + 3, x:x + 3].min():
            TR.bush(x - L.ox, z - L.oz, T(x, z), 800 + nb, big=rng.random() < 0.35); nb += 1
            if nb > 150: break

if STAGE >= 2: L.road_edges()
if STAGE >= 9:
    L.grasses(0.5, flowers=[(48, 118, 4, 'poppy'), (90, 136, 3, 'dandelion'), (126, 130, 4, 'cornflower'), (154, 110, 3, 'oxeye_daisy'), (180, 138, 4, 'poppy'),
                             (66, 160, 4, 'azure_bluet'), (146, 172, 3, 'dandelion'), (240, 112, 3, 'cornflower'), (176, 196, 4, 'allium'), (110, 100, 3, 'oxeye_daisy'),
                             (30, 124, 3, 'red_tulip'), (206, 110, 3, 'azure_bluet'), (160, 76, 4, 'poppy'), (262, 132, 3, 'allium')])
    for _ in range(60):                                                                  # 池と川のふちの葦
        x, z = rng.randint(4, W - 5), rng.randint(4, D - 5)
        if L.kind[z, x] == ow.SAND and L.Hi[z, x] == L.level:
            for y in range(1, rng.randint(2, 4)): P(x, L.level + y, z, cross('sugar_cane'))

# ---------------------------------------------------------------- 視点と遊びの印
V = lambda x, y, z: [-(x - L.ox), y, z - L.oz]
def eye(x, z, yaw, up=2.6, down=1.0, dist=30):
    g = L.top(x, z); dx, dz = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    return '@%.1f;%.1f;%.1f;%.1f;%.1f;%.1f' % (x - L.ox + .5, g + up, z - L.oz + .5, x - L.ox + .5 + dx * dist, g + up - down, z - L.oz + .5 + dz * dist)
EYES = dict(gate=eye(44, 124, 180, up=3.2), out=eye(20, 127, 0, up=3.2), farm=eye(50, 124, -120, up=3.2), valley=eye(76, 136, -8, up=3.2), fork=eye(126, 122, 0, up=3.2),
            camp=eye(150, 121, 60, up=3.2), bridge=eye(190, 126, 0, up=3.2), east=eye(240, 124, -15, up=3.2), corridor=eye(272, 112, 0, up=4, down=-6),
            north=eye(151, 94, -55, up=3.2, down=-8), south=eye(131, 162, 55, up=3.2), stones=eye(176, 198, -20, up=3.2), back=eye(226, 123, 180, up=3.2),
            hilltop=eye(92, 62, 15, up=4, down=6), mine=eye(232, 98, -40, up=3.2), tower=eye(214, 168, 38, up=3.2, down=-7), tree=eye(150, 100, -58, up=3.2, down=-9))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out')
os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, NAME + '.eyes'), 'w').write('\n'.join('%s %s' % kv for kv in EYES.items()))
out = save(NAME, {'overview': [V(160, 150, 330), V(160, 10, 128)], 'west': [V(-40, 80, 128), V(110, 10, 128)], 'east': [V(360, 90, 150), V(230, 10, 120)],
                  'mid': [V(160, 70, 230), V(160, 10, 120)], 'top': [V(160, 330, 129), V(160, 0, 128)]}, V(160, 10, 128), shadowR=220, fog=(260, 700))
json.dump(dict(marks=MARKS, size=[W, D], origin=[L.ox, L.oz], spawn=[16, L.top(16, 127) + 1, 127]), open(os.path.join(out, 'play.json'), 'w'), ensure_ascii=False)
np.save(os.path.join(out, 'height.npy'), L.Hi); np.save(os.path.join(out, 'kind.npy'), L.kind)
print('書き出し', out, '高さ', int(L.Hi.min()), int(L.Hi.max()), '岩', round(float((L.kind == ow.ROCK).mean()), 3), '木', NTREE, '印', len(MARKS))
