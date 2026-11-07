#!/usr/bin/env python3
"""オープンワールドの一角 B：森の道と遺跡（128×128）。

手本：RuneForge verdant citadel の森の遺跡（~/ProjectS/night/refs/openworld/shots/ref_ruins_*）。物差しは ruler.md の A。
  灰の石の崖に苔の段の丘が囲み、真ん中を東西に敷石の古い参道が通る。参道の両側に白い石の柱の列（折れた柱・倒れた柱）、
  西の奥に崩れたアーチの門。北の段から小川が細い滝で落ち、参道の脇の池へ。木は大きな広葉樹とトウヒを段の上に。
  python3 towns/ow_ruins.py [版の名前]
"""
import math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import numpy as np
from vox import put, get, full, log, stair, slab, fence, cross, _h
from town import save
import terraces as T
import trees as TR
from props import hut as P_hut

NAME = sys.argv[1] if len(sys.argv) > 1 else 'ow_ruins'
REGION = '--region' in sys.argv                                      # 256×256 の地域：真ん中の 128 はそのまま、外に段の丘・道・見どころを足す
R = 124 if REGION else 60
L = T.Land(256 if REGION else 128, base=0, seed=8)
rng = random.Random(8)

# ---------------------------------------------------------------- 段の丘：参道（z≒-6）を挟んで北と南に台地
L.plateau(-30, -40, 30, 10, rz=20, rot=0.2)
L.plateau(26, -44, 30, 12, rz=18, rot=-0.3)
L.plateau(-10, -52, 20, 20, rz=12, steps=1)
L.plateau(40, -54, 14, 22, steps=1)
L.plateau(-34, 34, 28, 9, rz=20, rot=-0.4)
L.plateau(28, 38, 30, 11, rz=22, rot=0.3)
L.plateau(-46, 50, 14, 17, steps=1)
L.plateau(10, 52, 14, 18, steps=1)
L.plateau(-58, -8, 10, 14, steps=0)                            # 西の門の背の岩
if REGION:                                                      # 外の輪：大きな台地を 2〜3 段、北の頂に崩れた塔
    for cx, cz, r, h, rz, st in ((-84, -96, 40, 12, 28, 1), (64, -98, 44, 15, 26, 1), (4, -108, 24, 24, 18, 1), (2, -112, 12, 32, 10, 0),
                                 (104, 44, 30, 11, 34, 1), (108, -40, 20, 18, 16, 1), (-74, 92, 40, 11, 26, 1), (44, 102, 38, 13, 22, 1),
                                 (-106, 44, 24, 15, 30, 1), (-102, -58, 22, 12, 18, 0), (100, 104, 18, 20, 16, 1), (-112, 108, 14, 22, 12, 0)):
        L.plateau(cx, cz, r, h, rz=rz, steps=st, rot=rng.uniform(-0.6, 0.6))

# ---------------------------------------------------------------- 参道（敷石の広い道、幅 9）と森の小道
AX = [(63, -4), (36, -7), (10, -5), (-16, -6), (-42, -7)]
for i in range(L.N):
    for j in range(L.N):
        x, z = j - L.o, i - L.o
        zc = -6 + 1.5 * math.sin(x / 23.0)
        if -48 <= x <= 66 and abs(z - zc) <= 4.5 + (T.fbm(x, z, 5, 3) - 0.5) * 1.6:
            L.H[i, j] = 1; L.K[i, j] = 5
        elif -48 <= x <= 66 and abs(z - zc) <= 6.5 and L.K[i, j] == 0 and L.H[i, j] <= 3:
            L.H[i, j] = 1; L.K[i, j] = 2
L.path([(0, -2), (-4, 10), (-12, 18), (-22, 24)], w=2, rise=2.5)          # 南の段へ登る小道
L.path([(20, -10), (24, -20), (16, -28), (8, -34)], w=2, rise=2.5)        # 北の段へ
if REGION:                                                      # 参道の先：東と西へ土の道、北の頂の塔へ登る道、南の野営地へ
    L.path([(64, -5), (84, -2), (100, 12), (127, 16)], w=5, rise=3.0)
    L.path([(-50, -7), (-70, -12), (-92, -20), (-127, -18)], w=4, rise=3.0)
    L.path([(8, -34), (14, -60), (4, -80), (-10, -94), (0, -104)], w=3, rise=2.6)
    L.path([(-22, 24), (-40, 48), (-62, 66), (-80, 74)], w=3, rise=2.8)
    falls = T.stream(L, [(44, -127), (40, -100), (34, -80), (30, -62), (28, -50), (22, -40), (20, -30), (24, -20), (30, -14)], w=1.8)
else: falls = T.stream(L, [(30, -62), (28, -50), (22, -40), (20, -30), (24, -20), (30, -14)], w=1.6)
L.pond(36, -14, 5)

PAVE = [('stone_bricks', .30), ('cracked_stone_bricks', .15), ('mossy_stone_bricks', .20), ('andesite', .10), ('cobblestone', .10), ('moss_block', .08), ('grass', .07)]
pal = dict(T.MOSSY); pal['pave'] = PAVE
if '--ground' in sys.argv: pal['mottle'] = T.GROUND_MOSSY
L.build(pal)
if '--relief' in sys.argv: L.cliff_relief(pal)
if '--relief2' in sys.argv: L.cliff_relief2(pal)              # 2 周目：3〜4 マス幅の柱・横の岩棚・草の垂れ                   # 崖の面の出入り・まだら・縁の欠け（11/08）
if '--ground' in sys.argv: L.ground_extras()                      # 3 周目：縁の土・シダと小石・道の縁の砂利と花
T.waterfalls(L, falls)

# ---------------------------------------------------------------- 柱の列：参道の両側、8 マスごと。折れた柱は高さを欠き、一部は倒す
WHITE = [('polished_diorite', .45), ('diorite', .25), ('calcite', .20), ('andesite', .10)]
def wb(x, y, z): return full(L.pick(WHITE, x, y, z, 61))
def column(x, z, g, hgt, banner=False, fallen_dir=None):
    for dx in range(-1, 2):                                             # 台座 3×3（苔の石）
        for dz in range(-1, 2):
            put(x + dx, g + 1, z + dz, full('mossy_stone_bricks' if _h(x + dx, z + dz, 3) < 0.4 else 'stone_bricks'))
    for y in range(g + 2, g + 2 + hgt):                                 # 胴：十字の断面（真ん中と四方）
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if (dx or dz) and y > g + hgt and _h(x + dx, y, z + dz) < 0.5: continue
            put(x + dx, y, z + dz, wb(x + dx, y, z + dz))
    if hgt >= 9:                                                        # 柱頭 3×3 と上の飾り
        yt = g + 2 + hgt
        for dx in range(-1, 2):
            for dz in range(-1, 2): put(x + dx, yt, z + dz, full('chiseled_stone_bricks' if dx == dz == 0 else 'polished_andesite'))
        for dx, dz, f in ((2, 0, 'w'), (-2, 0, 'e'), (0, 2, 'n'), (0, -2, 's')): put(x + dx, yt, z + dz, stair('polished_andesite', f, 'top'))
        if banner:                                                      # 赤い旗：柱頭から参道側へ 5 段
            bz = z + (2 if z < -6 else -2)
            for k in range(1, 6): put(x, yt - k, bz, full('white_wool' if k in (2, 5) else 'red_wool'))
    else:                                                               # 折れ口：ぎざぎざ
        yt = g + 2 + hgt
        for dx, dz in ((0, 0), (1, 0), (0, 1)):
            if _h(x + dx, z + dz, 9) < 0.6: put(x + dx, yt, z + dz, wb(x + dx, yt, z + dz))
    if fallen_dir:                                                      # 倒れた胴：地面に横たわる 2 段の太さ
        ddx, ddz = fallen_dir
        for k in range(3, 3 + rng.randint(5, 8)):
            px, pz = x + ddx * k, z + ddz * k
            if not L.inb(px, pz): break
            gy = L.h(px, pz)
            if _h(px, pz, 21) < 0.15: continue                           # 割れ目
            put(px, gy + 1, pz, wb(px, gy + 1, pz)); put(px + ddz, gy + 1, pz + ddx, wb(px, gy + 1, pz))
            if _h(px, pz, 22) < 0.5: put(px, gy + 2, pz, wb(px, gy + 2, pz))

cols = 0
def zc_at(x): return -6 + 1.5 * math.sin(x / 23.0)
x = -36; k = 0
while x < 8:                                                            # 柱の列は西の半分だけ。間は 8〜11 にばらし、ところどころ欠く
    for side in (-1, 1):
        z = int(round(zc_at(x) + side * 7))
        r = _h(x, side, 77)
        if r < 0.12: continue                                           # 失われた柱
        g = L.h(x, z)
        if r < 0.55: column(x, z, g, rng.randint(9, 12), banner=(k % 2 == 0 and side < 0 or k % 3 == 1 and side > 0))
        elif r < 0.8: column(x, z, g, rng.randint(2, 6))
        else: column(x, z, g, rng.randint(1, 3), fallen_dir=(rng.choice((-1, 1)), 0))
        cols += 1
    x += rng.randint(8, 11); k += 1

def broken_wall(x0, z, n, hmax):
    """崩れた低い壁：上ほど欠け、苔と割れた石。"""
    for i in range(n):
        x = x0 + i; g = L.h(x, z)
        hh = max(1, int(hmax * (1 - abs(i - n * 0.4) / n) + _h(x, z, 31) * 2 - 1))
        for y in range(g + 1, g + 1 + hh):
            put(x, y, z, full(L.pick([('mossy_stone_bricks', .4), ('stone_bricks', .3), ('cracked_stone_bricks', .2), ('cobblestone', .1)], x, y, z, 33)))
        if _h(x, z, 34) < 0.35: put(x, g + 1 + hh, z, slab('stone_bricks'))
for x0, side, n, hm in ((14, -1, 7, 4), (30, 1, 6, 3), (44, -1, 5, 2)):  # 東の半分：柱の代わりに崩れた壁のかけら
    broken_wall(x0, int(round(zc_at(x0) + side * 7)), n, hm)

def dais(cx, cz, w=9, d=11):
    """参道の西の奥の壇：苔の石段 3 段で上がる 9×11 の壇と、崩れた祭壇。"""
    for st in range(3):                                                 # 東向きの段（下から）
        xs = cx + d // 2 + 3 - st
        for z in range(cz - w // 2 - 2 + st, cz + w // 2 + 3 - st):
            for x in range(cx - d // 2 - 2 + st, xs + 1):
                y = 1 + st + 1
                b = L.pick([('stone_bricks', .4), ('mossy_stone_bricks', .35), ('cracked_stone_bricks', .15), ('moss_block', .1)], x, y, z, 41)
                put(x, y, z, full(b))
            if _h(xs, z, 42) < 0.85: put(xs + 1, 2 + st, z, stair('stone_bricks' if _h(xs, z, 43) < 0.6 else 'mossy_stone_bricks', 'e'))
    top = 4
    for dz in (-1, 0, 1):                                               # 祭壇：3 幅の台に、割れた石の箱
        put(cx, top + 1, cz + dz, full('polished_andesite')); put(cx - 1, top + 1, cz + dz, full('polished_andesite'))
    put(cx, top + 2, cz, full('chiseled_stone_bricks')); put(cx - 1, top + 2, cz - 1, full('chiseled_stone_bricks')); put(cx, top + 2, cz + 1, slab('stone_bricks'))
    put(cx, top + 3, cz, {'s': 'lantern', 't': 'soul_lantern'})
    for dz in (-4, 4):                                                  # 壇の両脇の像の台（像は失われ、足だけ残る）
        for y in range(top + 1, top + 3): put(cx - 2, y, cz + dz, full('polished_diorite'))
        put(cx - 2, top + 3, cz + dz, stair('polished_diorite', 'e'))
    for k in range(5):                                                  # 段に散る石と草
        x, z = cx + rng.randint(-3, 6), cz + rng.choice((-5, -4, 4, 5))
        if get(x, top + 1, z) is None: put(x, top + 1, z, cross('fern') if k % 2 else slab('cobblestone'))
dais(-30, -6)

# ---------------------------------------------------------------- 小さな造作：種類を変えて不揃いに（木の見張り台・崖の石段・旗の竿・水たまり・小屋）
def deck(x0, z0, g, w=5, d=5, hgt=4):
    """段の縁の木の台：柱 4 本に板の床と手すり、旗の竿。"""
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            put(x, g + hgt, z, full('spruce_planks'))
            corner = x in (x0, x0 + w - 1) and z in (z0, z0 + d - 1)
            if corner:
                for y in range(L.h(x, z) + 1, g + hgt): put(x, y, z, log('spruce_log'))
            if x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1): put(x, g + hgt + 1, z, fence('spruce_planks'))
    for y in range(g + hgt + 1, g + hgt + 7): put(x0, y, z0, fence('spruce_planks'))
    for k in range(1, 4): put(x0 + k, g + hgt + 6, z0, full('red_wool')); put(x0 + k, g + hgt + 5, z0, full('red_wool' if k < 3 else 'white_wool'))
    put(x0 + w // 2, g + hgt - 1, z0 + d // 2, {'s': 'lantern', 't': 'lantern', 'hang': True})

def cliff_stair(x, z0, z1):
    """参道から北の段へ上がる石段（幅 3、手すりは苔の石の低い壁、途中で 1 段欠ける）。"""
    n = z0 - z1
    g0, g1 = L.h(x, z0), L.h(x, z1)
    for k in range(n + 1):
        z = z0 - k; y = g0 + round((g1 - g0) * k / n)
        for dx in (-1, 0, 1):
            for yy in range(L.h(x + dx, z) + 1, y): put(x + dx, yy, z, full('stone_bricks'))
            if _h(x + dx, z, 51) < 0.08: continue
            put(x + dx, y, z, stair('mossy_stone_bricks' if _h(x + dx, z, 52) < 0.4 else 'stone_bricks', 's'))
        for dx in (-2, 2):
            put(x + dx, y, z, full('mossy_cobblestone'))
            if k % 4 == 0: put(x + dx, y + 1, z, {'s': 'wall', 't': 'mossy_cobblestone'})

def banner_post(x, z, f='s'):
    g = L.h(x, z)
    for y in range(g + 1, g + 6): put(x, y, z, fence('dark_oak_planks'))
    put(x, g + 6, z, full('dark_oak_planks'))
    for k in range(1, 4): put(x, g + 6 - k, z + (1 if f == 's' else -1), full('red_wool' if k != 2 else 'white_wool'))

def puddles(n):
    for k in range(n):
        x, z = rng.randint(-30, 50), int(round(zc_at(0))) + rng.randint(-3, 3)
        for dx in range(rng.randint(2, 4)):
            for dz in range(rng.randint(1, 3)):
                if L.inb(x + dx, z + dz) and L.K[L.idx(x + dx, z + dz)] == 5: put(x + dx, L.h(x + dx, z + dz), z + dz, {'s': 'water', 't': 'water_still'})

if '--extras' in sys.argv or REGION:                               # v4 で足した小さな造作（v2 には無い）
    deck(-14, -22, L.h(-12, -20), hgt=3)
    deck(30, 14, L.h(32, 16), hgt=4)
    cliff_stair(-2, -12, -24)
    for bx, bz, f in ((8, -13, 's'), (24, 1, 'n'), (-20, 2, 'n')): banner_post(bx, bz, f)
    puddles(5)

    P_hut(-26, 22, L.h(-23, 25), w=7, d=6, cloth='red_wool', open_side='n')

# ---------------------------------------------------------------- 空き地の小物（--clutter、4 周目）：参道のまわり 20 マスの空き地に、低い石壁のかけら・樽・花の塊を 6〜9 マスおきに
def clutter():
    crng = random.Random(77)                                         # 木の乱数をずらさないよう別の乱数
    BAR = {'s': 'full', 't': 'barrel_side', 'top': 'barrel_top', 'bot': 'barrel_bottom'}
    placed_c = []
    for k in range(400):
        x, z = crng.randint(-46, 62), int(round(zc_at(0))) + crng.choice((-1, 1)) * crng.randint(7, 22)
        if not L.inb(x, z) or L.K[L.idx(x, z)] != 0 or not L.slope_ok(x, z) or get(x, L.h(x, z) + 1, z) is not None: continue
        if any(math.hypot(x - a, z - b) < 6 for a, b in placed_c): continue
        g = L.h(x, z); r = crng.random()
        if r < 0.4:                                                      # 低い石壁のかけら（2〜4 長、1〜2 高）
            ax = crng.choice(((1, 0), (0, 1)))
            for i in range(crng.randint(2, 4)):
                X, Z = x + ax[0] * i, z + ax[1] * i
                if not L.inb(X, Z): break
                gg = L.h(X, Z)
                for y in range(gg + 1, gg + 1 + crng.randint(1, 2)): put(X, y, Z, full(L.pick([('mossy_stone_bricks', .5), ('cracked_stone_bricks', .3), ('cobblestone', .2)], X, y, Z, 141)))
        elif r < 0.65:                                                   # 樽と木箱の塊
            put(x, g + 1, z, BAR); put(x + 1, g + 1, z, full('spruce_planks')); put(x, g + 2, z, BAR)
        elif r < 0.85:                                                   # 花の塊
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if L.inb(x + dx, z + dz) and get(x + dx, L.h(x + dx, z + dz) + 1, z + dz) is None and crng.random() < 0.7:
                        put(x + dx, L.h(x + dx, z + dz) + 1, z + dz, cross(crng.choice(('azure_bluet', 'oxeye_daisy', 'cornflower', 'allium'))))
        else: banner_post(x, z, crng.choice(('s', 'n')))
        placed_c.append((x, z))
        if len(placed_c) >= 18: break

if '--clutter' in sys.argv: clutter()

# ---------------------------------------------------------------- 西の門：崩れたアーチ（幅 11、北の半分だけ残る）
def gate(cx, cz, g):
    for side in (-1, 1):
        px = cx; pz = cz + side * 7
        top = g + (17 if side < 0 else 9)
        for y in range(g + 1, top):
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    if side > 0 and y > top - 3 and _h(dx, y, dz) < 0.5: continue
                    put(px + dx, y, pz + dz, full('stone_bricks') if abs(dx) == 1 and abs(dz) == 1 else wb(px + dx, y, pz + dz))
    for k in range(-5, 2):                                              # 弧：北から真ん中を少し過ぎるまで
        y = g + 17 - int(round(abs(k) ** 1.6 * 0.35))
        for dx in range(-1, 2):
            put(cx + dx, y, cz + k, wb(cx + dx, y, cz + k)); put(cx + dx, y + 1, cz + k, full('polished_andesite') if dx == 0 else wb(cx + dx, y + 1, cz + k))
        if k < 1: put(cx, y - 1, cz + k, stair('polished_diorite', 's' if k < 0 else 'n', 'top'))
    for k in range(6):                                                  # 崩れた石：門の南の足元
        x, z = cx + rng.randint(-3, 3), cz + rng.randint(3, 9)
        if L.inb(x, z): put(x, L.h(x, z) + 1, z, wb(x, 0, z) if k % 2 else full('cobblestone'))
gate(-44, -6, 1)

# ---------------------------------------------------------------- 地域だけ：北の頂の崩れた塔、南西の野営地
def ruin_tower(cx, cz, g, r=4, h=22):
    """丸い石の塔の残り：南の半分が崩れて中が見え、上ほど欠ける。足元に崩れた石。"""
    for y in range(g + 1, g + h):
        t = (y - g) / h
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                d = math.hypot(dx, dz)
                if not (r - 1.2 <= d <= r + 0.3): continue
                ang = math.atan2(dz, dx)
                gap = 0.9 + 1.4 * t + (_h(dx, y, dz) - 0.5) * 0.6          # 崩れ：南（+z）側の弧が上ほど広く欠ける
                if abs(ang - math.pi / 2) < gap and y > g + 3: continue
                if t > 0.75 and _h(dx, y, dz) < (t - 0.75) * 3: continue
                put(cx + dx, y, cz + dz, full(L.pick([('stone_bricks', .4), ('mossy_stone_bricks', .3), ('cracked_stone_bricks', .2), ('andesite', .1)], cx + dx, y, cz + dz, 71)))
    for y in (g + 8, g + 15):                                          # 中の床の残り
        for dx in range(-r + 1, r):
            for dz in range(-r + 1, 0): put(cx + dx, y, cz + dz, slab('spruce_planks', 'top'))
    for k in range(14):
        x, z = cx + rng.randint(-2, 2) + rng.choice((-1, 1)) * rng.randint(0, 4), cz + rng.randint(4, 10)
        if L.inb(x, z): put(x, L.h(x, z) + 1, z, full('cobblestone') if k % 3 else slab('stone_bricks'))
    put(cx, g + 9, cz - 2, {'s': 'lantern', 't': 'soul_lantern'})
def camp(cx, cz):
    g = L.h(cx, cz)
    P_hut(cx - 3, cz - 3, g + 1, w=7, d=6, cloth='brown_wool', open_side='e', bed=g)
    put(cx + 7, g, cz, full('magma_block'))                            # 焚き火の跡：赤い炭と石の輪
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)): put(cx + 7 + dx, g + 1, cz + dz, slab('cobblestone'))
    for dx, dz in ((6, -2), (8, 2), (9, -1)): put(cx + dx, g + 1, cz + dz, {'s': 'full', 't': 'barrel_side', 'top': 'barrel_top', 'bot': 'barrel_bottom'})
    banner_post(cx + 5, cz + 4, 's')
if REGION:
    ruin_tower(2, -112, L.h(2, -112))
    camp(-86, 76)

# ---------------------------------------------------------------- 木：段の上に大きな広葉樹とトウヒの群れ、参道の上は空ける
placed = []
CAMS = [(56, -6), (0, 30)]                                          # 比べる視点の立ち位置（手本と同じ）。木で塞がない
def ok(x, z, d=6):
    return (L.inb(x, z) and L.slope_ok(x, z) and all(math.hypot(x - a, z - b) >= d for a, b in placed) and abs(z + 6) > 10
            and all(math.hypot(x - a, z - b) >= 10 for a, b in CAMS))
for k in range(220 * (12 if REGION else 1)):
    x, z = rng.randint(-R, R), rng.randint(-R, R)
    g = L.h(x, z)
    if REGION and abs(x) > 60 or REGION and abs(z) > 60:            # 地域の外の輪：丘の間の草地も大きな木の群れで埋める（間 6〜8、空き地は雑音で残す）
        if not ok(x, z, 7) or T.fbm(x, z, 30, 99) < 0.42: continue
        if rng.random() < 0.65: TR.oak(x, z, g, rng.randint(0, 9999), size=rng.uniform(1.3, 1.8))
        else: TR.spruce(x, z, g, rng.randint(0, 9999), h=rng.randint(17, 24))
        placed.append((x, z))
        if len(placed) >= 260: break
        continue
    if not ok(x, z, 9 if g > 6 else 13): continue
    if g >= 9 and rng.random() < 0.6: TR.oak(x, z, g, rng.randint(0, 9999), size=rng.uniform(1.1, 1.5))
    elif g >= 9: TR.spruce(x, z, g, rng.randint(0, 9999), h=rng.randint(14, 19))
    elif rng.random() < 0.5: TR.bush(x, z, g, rng.randint(0, 9999), big=True)
    else: continue
    placed.append((x, z))
    if len(placed) >= (260 if REGION else 26): break
L.plants(density=0.3, kinds=('short_grass', 'fern', 'fern', 'short_grass'), flowers=('azure_bluet', 'oxeye_daisy', 'lily_of_the_valley'))

# ---------------------------------------------------------------- 地域の大きな見どころ（--landmarks）：遠くから読める城門と高い塔。木の後に置き、中の葉を払う
def clear_box(x0, x1, z0, z1, y0, y1):
    from vox import W as _W
    for k in [k for k in _W if x0 <= k[0] <= x1 and z0 <= k[2] <= z1 and y0 <= k[1] <= y1 and 'leaves' in _W[k].get('t', '')]: del _W[k]

def great_gate(cx, cz, g, span=13, h=26):
    """東の道の入口の城門：7×7 の塔 2 本（背 h、上に狭間）を、背 18 のアーチの橋でつなぐ。北の塔の頭は崩れる。赤い旗を 4 枚。"""
    clear_box(cx - 5, cx + 5, cz - span // 2 - 9, cz + span // 2 + 9, g - 2, g + h + 6)
    S = [('stone_bricks', .45), ('mossy_stone_bricks', .25), ('cracked_stone_bricks', .15), ('andesite', .15)]
    for side in (-1, 1):
        tz = cz + side * (span // 2 + 4)
        top = g + h - (6 if side < 0 else 0)
        for y in range(g - 3, top):
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if max(abs(dx), abs(dz)) < 3 and y > g: continue
                    if side < 0 and y > top - 5 and _h(dx, y, dz) < 0.45: continue
                    put(cx + dx, y, tz + dz, full(L.pick(S, cx + dx, y, tz + dz, 81)))
        if side > 0:
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if max(abs(dx), abs(dz)) == 3 and (dx + dz) % 2 == 0: put(cx + dx, top, tz + dz, full('stone_bricks'))
        for y in (g + 6, g + 12, g + 18):
            if y < top - 1:
                for dx in (-3, 3): put(cx + dx, y, tz, full('black_concrete')); put(cx + dx, y + 1, tz, full('black_concrete'))
        for dx in (-4, 4):
            for k in range(1, 8): put(cx + dx, top - 2 - k, tz, full('red_wool' if k % 4 else 'yellow_wool'))
    for dz in range(-(span // 2), span // 2 + 1):                     # アーチ：真ん中ほど高い
        t = abs(dz) / (span / 2); ya = g + 13 + int(round(5 * (1 - t * t)))
        for y in range(ya, g + 20):
            for dx in range(-3, 4): put(cx + dx, y, cz + dz, full(L.pick(S, cx + dx, y, cz + dz, 82)))
        put(cx - 3, g + 20, cz + dz, full('stone_bricks') if dz % 2 else slab('stone_bricks')); put(cx + 3, g + 20, cz + dz, full('stone_bricks') if dz % 2 else slab('stone_bricks'))
    put(cx, g + 12, cz, {'s': 'lantern', 't': 'lantern', 'hang': True})

def high_tower(cx, cz, g, r=6, h=38):
    """北の頂の塔：丸い石の塔（背 38）、上 3 段は崩れ、南の面に縦の割れ目。頂に赤い旗。遠くから一本の柱に見える。"""
    clear_box(cx - r - 2, cx + r + 2, cz - r - 2, cz + r + 2, g - 2, g + h + 8)
    S = [('stone_bricks', .4), ('mossy_stone_bricks', .25), ('cracked_stone_bricks', .2), ('polished_andesite', .15)]
    for y in range(g - 2, g + h):
        t = (y - g) / h
        for dx in range(-r - 1, r + 2):
            for dz in range(-r - 1, r + 2):
                d = math.hypot(dx, dz)
                if not (r - 1.3 <= d <= r + 0.3): continue
                if t > 0.85 and _h(dx, y, dz) < (t - 0.85) * 5: continue
                if dz > r - 2 and abs(dx) <= 1 and 0.3 < t < 0.8: continue    # 南の割れ目
                put(cx + dx, y, cz + dz, full(L.pick(S, cx + dx, y, cz + dz, 83)))
        if (y - g) % 9 == 0 and y > g:                                   # 帯
            for dx in range(-r - 1, r + 2):
                for dz in range(-r - 1, r + 2):
                    if r - 0.3 <= math.hypot(dx, dz) <= r + 1.2: put(cx + dx, y, cz + dz, full('polished_andesite'))
    for y in range(g + h - 2, g + h + 6): put(cx, y, cz, fence('dark_oak_planks'))
    for k in range(1, 5): put(cx + 1, g + h + 6 - k, cz, full('red_wool'))
    put(cx, g + h - 3, cz, {'s': 'lantern', 't': 'soul_lantern'})

if REGION and '--landmarks' in sys.argv:
    great_gate(96, 10, L.h(96, 10))
    high_tower(2, -112, L.h(2, -112))

# ---------------------------------------------------------------- 遊び（--play、木の後に置いて木の並びを変えない）：T1 の終わり Lv8〜10。奥に T2 ダンジョンの入口、伐採が特産
PLAY = '--play' in sys.argv
if PLAY:
    import playmarks as PM
    PM.BIG = '--big' in sys.argv
    PM.lay(L, rng, [
        ('dungeon', -44, -6, '遺跡の地下（T2 ダンジョンの入口）'), ('gather', -10, -6, '集まる広場（4 人）'),
        ('sign', -22, -15, '入口の目安の札'),
        ('wood', -30, -40, ''), ('wood', 22, -44, ''), ('wood', -34, 34, ''), ('wood', 28, 40, ''),
        ('skin', 46, 26, ''), ('skin', -52, 20, ''), ('herb', 36, -26, ''), ('herb', 12, 30, ''), ('ore', -56, -32, ''),
        ('hunt', -16, -44, '狩り場 Lv8〜9（根の精）'), ('hunt', 34, 46, '狩り場 Lv9〜10（苔の獣）'),
        ('way', 56, -16, '谷の入口（復帰点の候補）'),
    ])


if PLAY: PM.save_play(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', NAME))
V = lambda x, y, z: [-x, y, z]
if REGION: save(NAME, {'overview': [V(0, 150, 190), V(0, 10, 0)]}, V(0, 10, 0), shadowR=200, fog=(260, 700))
else: save(NAME, {'overview': [V(0, 80, 95), V(0, 10, 0)]}, V(0, 10, 0), shadowR=100, fog=(150, 400))
print('柱', cols, '木', len(placed), '滝', len(falls))
