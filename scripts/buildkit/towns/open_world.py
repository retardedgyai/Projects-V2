#!/usr/bin/env python3
"""オープンワールドの試み（11/03）。256マス四方に、雪山・谷・川・湖・森・村・丘の館・花畑を置く。

藍「一旦オープンワールドを作る試みをしてみよう」。
手本：maaaaagarin「花と森の村」（~/ProjectS/refs/build/03-maaaaagarin/1.jpg）。藍が前に「全体でどういう雰囲気になるか、
オープンワールドとしてどう違和感なく遊べて見栄え良くできるか」と言った物。読んだ作り：
  ・奥に雪をかぶった岩山の連なり。山際は針葉樹、手前ほど広葉樹の密な森。
  ・主役（館）は小高い丘の上。村の家は森の中にばらけて建つ。谷を石のアーチ橋で渡る。
  ・色の濃い花畑を帯にして置く。道は細く、地形に沿って曲がる。
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save, RED, BROWN, SLATE, WHITE, N4
from vox import put, get, full, grass, cross, vnoise, _h, LANTERN
import props as P

B = 128
def fbm(x, z, s=64.0, seed=1):
    return (vnoise(x, z, s, seed) * 0.55 + vnoise(x, z, s / 2, seed + 1) * 0.27 + vnoise(x, z, s / 4, seed + 2) * 0.12 + vnoise(x, z, s / 8, seed + 3) * 0.06)
def smooth(t): t = min(1.0, max(0.0, t)); return t * t * (3 - 2 * t)

# ================================================================ 地形の式 [地形1・3]
def river_x(z): return 10 + 18 * math.sin(z / 40.0) + 8 * math.sin(z / 17.0)   # 山から南へ流れる川の中心
def river_w(z): return 22 - (z + 70) * 0.085                                     # 水面は南へ下がる
LAKE = (-66, 26, 15, 11)                                                          # 湖の中心と半径と水面
HILL = (40, -12)                                                                  # 館の丘
PEAKS = [(-96, -104, 92, 34), (-52, -112, 78, 30), (-12, -100, 104, 38), (30, -116, 84, 30), (70, -98, 96, 36), (110, -112, 74, 28), (-120, -70, 52, 26), (118, -64, 48, 24)]
def mountains(x, z):
    """峰を1つずつ立てる（円錐を揺らして尾根でつなぐ）。手前の裾は森に沈む。"""
    h = 0.0
    for (px, pz, ph, pr) in PEAKS:
        d = math.hypot(x - px, (z - pz) * 1.15) / (pr * 1.7 * (0.8 + 0.4 * vnoise(x, z, 18, 61)))   # 裾を広く：勾配を1マスずつの段々に近づけ、雪の乗る上面を見せる
        if d < 1.8:
            sh = max(0.0, 1 - d / 1.8) ** 0.95                                         # 裾の広い山塊（細い牙にしない）
            crag = (1 - abs(2 * fbm(x, z, 22, 81) - 1)) * 18 + (fbm(x, z, 16, 83) - 0.5) * 6    # 尾根の起伏。細かい揺れは弱く（強いと雪の段が市松模様になった）
            h = max(h, 0.72 * ph * sh + crag * sh)
    ridge = 1 - abs(2 * fbm(x, z, 36, 11) - 1)
    mf = smooth((-z - 50) / 60.0)
    return h * (0.82 + 0.3 * ridge) + mf * 10 * ridge                      # 段にする式はやめた（段の高さを場所で変えると高さが途切れ、壁ができた）
def terrace(m, x, z):
    """岩山を段にする：垂直な岩の板（高さ5〜12）と、雪の乗る狭い棚を交互に重ねる。段の高さと位置は場所でずらし、
    同心の輪にしない（らーなろっその尖った峰：RgH1E52D1cA 8:50「垂直な板が重なり、棚の雪が斜めの白い線になる」）。"""
    if m < 12: return m
    T = 5 + 7 * vnoise(x, z, 38, 91)
    mm = m + 6 * vnoise(x, z, 17, 92)
    k, f = divmod(mm, T)
    edge = 0.62 + 0.2 * vnoise(x, z, 9, 93)                                           # 棚の奥行き：段の6〜8割は緩い棚
    rise = f / edge * 0.35 if f < edge * T and False else (0.35 * f / T if f / T < edge else 0.35 * edge + (1 - 0.35 * edge) * (f / T - edge) / (1 - edge))
    return k * T + rise * T - 6 * vnoise(x, z, 17, 92)
def base(x, z):
    h = 16 + (fbm(x, z, 70, 3) - 0.5) * 12 + mountains(x, z)
    h += 14 * math.exp(-((x - HILL[0]) ** 2 + (z - HILL[1]) ** 2) / 420.0)        # 館の丘
    return h
def nat(x, z):
    h = base(x, z)
    # 川の谷：川に向かって下がる。z=-75 より奥は、同じ式のまま底だけ上げて山へ上らせ、中心線を少しずつ揺らす
    # （奥を別の式にすると境目で高さ35〜70の壁ができた）
    t = smooth((-75 - z) / 30.0) if z < -75 else 0.0
    cxr = river_x(max(z, -75)) + 10 * (vnoise(x * 0.2, z, 18, 24) - 0.5) * t
    d = abs(x - cxr); w = river_w(max(z, -75))
    up = max(0.0, -75 - z) * (1.1 + 0.8 * vnoise(x, z, 12, 25)) + 6 * fbm(x, z, 9, 26) * t
    if d > 4 or z < -75: h = min(h, w + 1 + up + max(0.0, d - 4 * (1 - t)) * (0.35 + 0.4 * fbm(x, z, 30, 21) + 0.5 * t))
    lx, lz, lr, lw = LAKE
    dl = math.hypot(x - lx, (z - lz) * 1.2) - lr * (0.85 + 0.3 * vnoise(x, z, 9, 31))
    if dl < 10: h = min(h, lw + 1 + max(0, dl) * 0.5)
    return h
def in_river(x, z): return -75 <= z and abs(x - river_x(z)) <= 4
def in_lake(x, z):
    lx, lz, lr, lw = LAKE
    return math.hypot(x - lx, (z - lz) * 1.2) - lr * (0.85 + 0.3 * vnoise(x, z, 9, 31)) < 0

class World(Town):
    """山の岩肌と雪（らーなろっその自然の山：learn4/MKw4LbDT4Zk・RgH1E52D1cA・Pti0n-5whR4）。
    ・雪は上面にだけ乗せる：雪の線より上の柱は、一番上の1マスを必ず雪にする（まだらに岩を混ぜない）。その下は岩なので、
      1段ずつの所は白い段々、2段以上落ちる壁の下だけ灰色の岩が見え、壁の棚の雪が斜めの白い線になる。
    ・岩は石を地にして、安山岩・閃緑岩を大きな塊で入れる（細かく混ぜると斑点になる）。
    ・山すそは、岩と草の段々の間に土の厚い帯をはさむ。"""
    def snow(self, x, z, g): return g >= 44 + 8 * vnoise(x, z, 12, 41)
    def foot(self, x, z, g):
        if not 32 <= g < 44 + 4 * vnoise(x, z, 14, 49): return False
        return max(abs(g - self.H.get((x + dx, z + dz), g)) for dx, dz in N4) <= 2       # 土の帯は緩い所だけ（急な壁に塗ると浮いた三角に見えた）
    def steep_top(self, x, z):
        g = self.H[(x, z)]
        if self.snow(x, z, g): return full('snow')
        if g >= 44: return full(self.rock_at(x, g, z))
        if self.foot(x, z, g): return full('coarse_dirt') if vnoise(x, z, 4, 46) < 0.5 else full('dirt')
        return super().steep_top(x, z)
    def ground_top(self, x, z, k, g, sand=lambda x, z, g: False):
        if k == 'nat' and self.snow(x, z, g): return full('snow')
        if k == 'nat' and g >= 46: return full(self.rock_at(x, g, z))
        if k == 'nat' and self.foot(x, z, g) and vnoise(x, z, 6, 45) > 0.62: return full('coarse_dirt')
        return super().ground_top(x, z, k, g, sand)
    def rock_at(self, x, y, z):
        g = self.H.get((x, z), -99)
        if y > 26 and self.foot(x, z, g) and y >= g - 3 and g < 46: return 'dirt'      # 山すその土の帯
        if y > 26:                                                                 # 石を地に、安山岩と閃緑岩は大きな塊
            n = vnoise(x + y * 0.3, z - y * 0.2, 6, 71)
            return 'andesite' if n < 0.28 else 'gravel' if 0.5 < n < 0.54 else 'tuff' if 0.62 < n < 0.66 else 'stone'   # 閃緑岩は雪の斑に見えるので使わない
        return super().rock_at(x, y, z)
    def snow_caps(self):
        """雪の線より上の柱の上面に、薄い雪の層を1枚（水平な面にだけ雪）。"""
        n = 0
        for (x, z), g in self.H.items():
            if self.KIND.get((x, z)) == 'nat' and self.snow(x, z, g) and get(x, g + 1, z) is None:
                put(x, g + 1, z, {'s': 'carpet', 't': 'snow'}); n += 1
        return n

T = World((-B, B - 1, -B, B - 1), nat, lambda x, z: False)

# ================================================================ 段：村・館・道
VX, VZ = -34, 70                                                                  # 村の広場
VY = T.ny(VX, VZ)
T.pad_disc(VX, VZ, 8, VY, 'plaza')
HOUSES = [('timber', 0, (VX - 20, VZ - 22), RED, False, 'V1'), ('timber', 180, (VX + 16, VZ - 18), BROWN, True, 'V2'),
          ('fancy', 0, (VX - 22, VZ + 10), None, False, 'V3'), ('timber', 270, (VX + 24, VZ + 8), WHITE, False, 'V4'),
          ('smithy', 90, (VX + 18, VZ + 34), None, False, '鍛冶屋'),
          ('timber', 0, (-60, 104), SLATE, True, 'V5')]
for (name, r, at, sw, mi, label) in HOUSES: print('家', label, T.house(name, r, at, None, sw, mi, label))
MY = T.ny(*HILL) + 1
mrect, _ = T.house('lhouse', 180, (HILL[0] - 3, HILL[1] - 4), MY, None, False, '館')
T.pad(mrect[0] - 4, mrect[1] + 4, mrect[2] - 3, mrect[3] + 5, MY, 'hall', keep=True)
T.house('fancy', 90, (64, 24), None, None, True, '丘の東')
BRIDGE_Z = 22                                                                     # 川を渡る所
bxa, bxb = int(river_x(BRIDGE_Z)) - 7, int(river_x(BRIDGE_Z)) + 7
T.lane([(VX + 8, VZ - 2, VY), (VX + 22, VZ - 12, None), (bxa - 14, 40, None), (bxa - 6, BRIDGE_Z + 2, None), (bxa - 1, BRIDGE_Z, None)])
# 館への坂：まっすぐ上らず、丘の肩を回って折り返す（急な所ほど曲がりくねらせる）[rules/nature 19・22]
GY = T.ny(bxb + 8, BRIDGE_Z - 4)
T.lane([(bxb + 1, BRIDGE_Z, None), (bxb + 8, BRIDGE_Z - 4, None), (HILL[0] - 14, mrect[3] + 14, GY + (MY - GY) // 3),
        (HILL[0] + 2, mrect[3] + 16, GY + (MY - GY) * 2 // 3), (HILL[0] + 12, mrect[3] + 9, MY - 1), (HILL[0] + 4, mrect[3] + 5, MY)])
T.lane([(HILL[0] + 8, mrect[3] + 6, MY), (58, 20, None), (64, 30, None)])
T.lane([(VX - 8, VZ, VY), (VX - 20, VZ - 6, None), (VX - 20, VZ - 12, None)])
T.lane([(VX, VZ + 8, VY), (VX - 2, VZ + 18, None), (-48, 96, None), (-58, 100, None)])
T.lane([(VX + 6, VZ + 6, VY), (VX + 14, VZ + 10, None), (VX + 16, VZ + 8, None)])
T.lane([(VX - 8, VZ + 4, VY), (VX - 16, VZ + 10, None), (VX - 22, VZ + 8, None)])

# ================================================================ 高さ・川・湖・地面
T.resolve()
H, KIND = T.H, T.KIND
for (x, z) in list(H):
    if in_river(x, z) and KIND[(x, z)] == 'nat':
        w = int(round(river_w(z))); H[(x, z)] = w - 3; KIND[(x, z)] = 'stream'; T.stream_level[(x, z)] = w
    elif in_lake(x, z) and KIND[(x, z)] == 'nat':
        H[(x, z)] = LAKE[3] - 3 - int(2 * vnoise(x, z, 6, 33)); KIND[(x, z)] = 'stream'; T.stream_level[(x, z)] = LAKE[3]
T.fill(sand=lambda x, z, g: any(KIND.get((x + dx, z + dz)) == 'stream' for dx, dz in N4) and g <= T.stream_level.get((x + 1, z), 99) + 2)
T.vines_on_cliffs(lambda x, z: H[(x, z)] < 50, chance=0.2)
T.steps()

# ================================================================ 構造物と小物
yb = T.stone_bridge(BRIDGE_Z, bxa, bxb, axis='x', base=int(round(river_w(BRIDGE_Z))))
T.fire_pit(VX, VZ, VY)
def place_near(fn, x, z, g, *rest):
    """(x, z) から1〜2マスずらしながら、置ける所に置く（広場の縁の柱や段にかからない所）。"""
    for (dx, dz) in [(0, 0), (-1, 0), (0, -1), (-1, -1), (1, 0), (0, 1), (-2, 0), (0, -2), (-2, -1), (-1, -2)]:
        cs = T.cells_of(fn, x + dx, z + dz, g, *rest)
        if T.spot_ok(cs, g): fn(x + dx, z + dz, g, *rest); return True
    print('置けない', fn.__name__, (x, z)); return False
for (x, z, f, c) in [(VX + 6, VZ - 5, 'w', 'yellow_wool'), (VX - 6, VZ + 4, 'e', 'lime_wool')]:
    place_near(P.stall, x, z, VY, f, c, 'white_wool', [P.MELON, P.HAY])
place_near(P.cargo, VX + 5, VZ + 6, VY, 'n')
def place_by_lane(fn, x0, z0, f, reach=8):
    """(x0, z0) の近くの、道の脇の平らな所に置く（道の縁から1〜2マス外）。 [rules 53：道の小物は種類を変えて6〜10マスおき]"""
    for r in range(0, reach + 1):
        for dx in range(-r, r + 1):
            for dz in (-r, r) if abs(dx) != r else range(-r, r + 1):
                x, z = x0 + dx, z0 + dz
                g = H.get((x, z))
                if g is None: continue
                cs = T.cells_of(fn, x, z, g, f)
                if not T.spot_ok(cs, g, ('nat', 'yard')): continue
                if not any(KIND.get((cx + ex, cz + ez)) in ('lane', 'lane_edge') for (cx, cz) in cs for ex, ez in N4): continue
                fn(x, z, g, f); return (x, z)
    print('置けない', fn.__name__, (x0, z0)); return None
print('well', place_by_lane(P.well, VX - 14, VZ + 6, 'e'), 'cart', place_by_lane(P.cart, VX + 16, VZ - 8, 's'))
for (x, z, arm) in [(VX - 8, VZ - 3, 'e'), (VX + 8, VZ + 3, 'w')]:
    if T.spot_ok([(x, z)], VY, allow=('plaza', 'lane', 'lane_edge')): P.lamp_post(x, z, VY, arm)
print('畑', T.fields(range(VX - 40, VX + 40, 11), range(VZ + 20, VZ + 50, 8), n=6))
print('庭の物', T.yard_extras(skip=('館',)))
# 花畑：色の帯を、村のまわりの緩い草地に [手本：色の濃い花畑]
FLOWERS = [['poppy', 'red_tulip'], ['cornflower', 'blue_orchid'], ['dandelion', 'sunflower_front' if False else 'dandelion'], ['allium', 'pink_tulip'], ['oxeye_daisy', 'azure_bluet']]
fields = 0
for (cx, cz) in [(VX - 34, VZ - 4), (VX + 30, VZ + 34), (VX - 12, VZ - 36), (20, 70), (-80, 64)]:
    band = 0
    for x in range(cx - 14, cx + 15):
        for z in range(cz - 8, cz + 9):
            if KIND.get((x, z)) != 'nat' or T.town_zone(x, z): continue
            g = H[(x, z)]
            if get(x, g + 1, z) is not None or abs(g - H.get((x + 1, z), g)) > 1: continue
            if math.hypot((x - cx) / 14.0, (z - cz) / 8.0) + vnoise(x, z, 5, 51) * 0.4 > 1.1: continue
            k = int((x - cx + 14 + vnoise(x, z, 7, 52) * 6) // 6) % len(FLOWERS)
            put(x, g + 1, z, cross(FLOWERS[k][int(_h(x, z, 53) * 2)])); KIND[(x, z)] = 'flowers'
    fields += 1

# ================================================================ 森と草
def tree_kind(x, z, r):
    """木の種類は帯で分ける：水際に白樺、中にオーク、奥と高い所にトウヒ。種類を交互に混ぜない（めいきゃん 19:11〜19:45）。"""
    g = H[(x, z)]
    if g >= 56: return None
    w = T.WD.get((x, z), 99) if hasattr(T, 'WD') else 99
    if g >= 34 or z < -40 + 10 * vnoise(x, z, 30, 7): return 'spruce'
    if w <= 9: return 'birch' if r < 0.85 else 'oak'
    return 'big' if r < 0.015 else 'cherry' if r < 0.04 else 'birch' if r < 0.12 else 'oak'
def skip(x, z):
    return tree_kind(x, z, 0.5) is None or KIND.get((x, z)) in ('flowers', 'stream') or abs(x - river_x(z)) < 7
# 森の踏み跡：村 → 湖、湖 → 山すその森、館の丘 → 東の森、村 → 南の森。木は道の両脇2マスまで残す [rules 32]
T.trail([(VX - 20, VZ - 12), (-60, 52), (-66, 42)])
T.trail([(-84, 30), (-92, 6), (-84, -18), (-66, -40), (-44, -56)], w=2.5)
T.trail([(HILL[0] + 22, HILL[1] + 6), (78, -6), (96, -26), (104, -52)], w=2.5)
T.trail([(-48, 96), (-30, 112), (-6, 116), (16, 106)], w=2.5)
print('trees, bushes', T.woods(skip=skip, kind=lambda x, z, r: tree_kind(x, z, r) or 'spruce'))
print('fallen logs', T.fallen_logs(40))
print('trail ends', T.trail_ends(T.TRAIL_LINES[1:]))
print('lamps', sum(T.trail_lamps(l) for l in T.TRAIL_LINES[:2]), 'rest', T.rest_near(-62, 46), T.rest_near(-86, 26))
T.ground_cover()

# ================================================================ 検査と書き出し
T.walk_check((VX, VZ - 6))
V = lambda x, y, z: [-x, y, z]
print('書き出し', save('open_world', {
    'overview': [V(-10, 95, 185), V(0, 30, -40)],
    'valley': [V(-80, 60, 130), V(10, 24, 10)],
    'village': [V(VX + 24, VY + 34, VZ + 52), V(VX, VY + 2, VZ)],
    'manor': [V(HILL[0] - 34, MY + 22, HILL[1] + 56), V(HILL[0], MY + 6, HILL[1])],
    'bridge': [V(river_x(BRIDGE_Z) - 10, yb + 26, BRIDGE_Z + 30), V(river_x(BRIDGE_Z), yb, BRIDGE_Z)],
    'lake': [V(LAKE[0] - 30, 46, LAKE[1] + 44), V(LAKE[0], 12, LAKE[1])],
    'mountains': [V(0, 70, 60), V(0, 70, -110)],
    'woods': [V(112, H[(112, 60)] + 30, 60), V(80, H[(80, 20)], 20)],
    'trail': [V(-64, H[(-64, 42)] + 4, 42), V(-58, H[(-58, 54)] + 2, 54)],
    'well': [V(-38, H[(-38, 88)] + 5, 88), V(-44, H[(-44, 82)] + 2, 82)],
    'cart': [V(-15, H[(-15, 53)] + 4, 53), V(-21, H[(-21, 59)] + 1, 59)],
    'rest': [V(-66, H[(-66, 58)] + 7, 58), V(-75, H[(-75, 49)] + 1, 49)],
    'eastpath': [V(76, H[(76, -4)] + 3, -4), V(96, H[(96, -26)] + 2, -26)],
    'top': [V(0, 320, 0), V(0, 0, 1)],
    'night': [V(VX + 24, VY + 34, VZ + 52), V(VX, VY + 2, VZ), {'night': True}],
}, V(0, 20, 0), shadowR=170, fog=(200, 700)))
