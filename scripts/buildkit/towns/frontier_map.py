#!/usr/bin/env python3
"""ProjectS の 1000×1000 の遊べる地図（11/03、地図の段階まで）。
藍「一回大きなマップを作ってみたいな1000x1000ぐらいで、ちゃんとMMORPGとしてProjectSのマップとして遊べるマップ」。

決まりの出どころ：
  ・藍の決定（docs/proposals/world-prototype/README.md）：港から街道が伸び、地域が数珠つなぎ（Wynncraft／FF14 型）。
    遊ぶ範囲はジャンプなしで歩ける傾斜、峠は道付き、高い山や崖は見るだけ。フィールドに店・村は置かない（採取と狩り）。
  ・区域の名前・Tier・地形の型は CoreFrontierZones.kt のもの。Lv 帯は Tier ごとに 10（CoreJourney.kt）。
  ・MMORPG の地図の決まり（~/ProjectS/refs/build/learn6/mmo-map.md）：区域は約 200×200、外周は山と海で閉じる、
    目印は区域に1つ、小さな見どころは 60〜110 マスごと、町から最初の狩り場まで 80〜170 マス、境目は尾根と峠。
  ・地形の作り方（rules/nature.md 68〜75）：尾根から盛る → 侵食 → 緩い面を均す → 傾きと光で塗る。

出力：out/frontier/ に 高さ（height.npy）・地面の種類（ground.npy）・点の一覧（pois.json）・上から見た地図（map.png）。
座標は x=東、z=南、(0, 0) が北西の角。高さはマイクラの y（海面 62）。
"""
import json, math, os, sys, time
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import terrain as TE

N = 1000
SEA = 62
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', 'frontier')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)
Z, X = np.mgrid[0:N, 0:N].astype(np.float32)


def noise(scale, seed, octaves=4, gain=0.5):
    """なめらかなノイズ（0〜1）。粗い乱数の格子を拡大して重ねる。"""
    r = np.random.default_rng(seed); out = np.zeros((N, N), np.float32); amp = 1.0; tot = 0.0
    n = max(2, int(N / scale))
    for o in range(octaves):
        g = r.random((n + 1, n + 1)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray(g, mode='F').resize((N, N), Image.BICUBIC)); tot += amp
        amp *= gain; n *= 2
    return out / tot


def blur(a, r):
    k = np.exp(-np.linspace(-2, 2, 2 * r + 1) ** 2); k /= k.sum()
    a = np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode='edge'), k, 'valid'), 1, a)
    return np.apply_along_axis(lambda v: np.convolve(np.pad(v, r, mode='edge'), k, 'valid'), 0, a).astype(np.float32)


# ================================================================ 区域（名前・型・Tier は CoreFrontierZones.kt）
#   key, 名前, 型, Tier, 中心 x, z, 底の高さ, ゲームの id
REG = [
    ('harbor',  '開拓港',     'harbor',   0, 500, 880, 64, 33),
    ('hanaikada', '花筏の淵', 'sakura',   1, 500, 715, 68, 26),
    ('deitan',  '泥炭の入江', 'marsh',    1, 225, 815, 63, 27),
    ('shunrai', '春雷の社',   'sakura',   1, 785, 800, 70, 32),
    ('hitsuji', '羊飼いの原', 'meadow',   2, 290, 585, 74, 31),
    ('ryokufu', '緑風の谷',   'forest',   2, 720, 585, 76, 35),
    ('kirinuma', '霧沼',      'marsh',    2, 505, 505, 72, 18),
    ('fuketsu', '風穴の崖',   'cliffs',   2, 125, 415, 84, 24),
    ('ookami',  '狼の背',     'highland', 2, 870, 420, 88, 12),
    ('shirakashi', '白樫の森', 'forest',  3, 330, 330, 92, 10),
    ('setsugen', '雪原の砦',  'snow',     3, 680, 305, 100, 11),
    ('okibi',   '熾火の火口', 'volcano',  4, 500, 150, 110, 0),
]
K = len(REG)
CX = np.array([r[4] for r in REG], np.float32); CZ = np.array([r[5] for r in REG], np.float32)
FLOOR = np.array([r[6] for r in REG], np.float32); TIER = np.array([r[3] for r in REG])
STYLE = [r[2] for r in REG]
LV = {0: '町', 1: 'Lv 1〜10', 2: 'Lv 11〜20', 3: 'Lv 21〜30', 4: 'Lv 31〜40'}

# ================================================================ 街道（港から数珠つなぎ。枝道は行き止まりの奥へ）
ROADS = {
    'main': [(500, 905), (500, 845), (495, 780), (505, 715), (500, 640), (505, 560), (505, 505),
             (520, 440), (560, 390), (640, 330), (680, 305), (610, 240), (540, 200), (500, 165)],
    'west1': [(495, 780), (420, 800), (330, 815), (225, 815)],                               # 泥炭の入江
    'east1': [(495, 780), (590, 790), (690, 800), (785, 800)],                               # 春雷の社
    'west2': [(500, 640), (420, 610), (290, 585), (200, 500), (125, 415)],                   # 羊飼いの原 → 風穴の崖
    'east2': [(500, 640), (600, 610), (720, 585), (810, 500), (870, 420)],                   # 緑風の谷 → 狼の背
    'north3w': [(520, 440), (430, 380), (330, 330)],                                         # 白樫の森
}


def catmull(pts, n=24):
    p = [pts[0]] + list(pts) + [pts[-1]]; out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = (np.array(q, np.float32) for q in p[i - 1:i + 3])
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(np.array(pts[-1], np.float32))
    return np.array(out)


def dist_to_path(path, step=4):
    """各マスから道までの距離（粗い格子で近似して拡大）。"""
    s = step; g = np.full(((N + s - 1) // s, (N + s - 1) // s), 1e9, np.float32)
    zz, xx = np.mgrid[0:g.shape[0], 0:g.shape[1]].astype(np.float32) * s
    for (a, b) in zip(path, path[1:]):
        dx, dz = b[0] - a[0], b[1] - a[1]; L2 = dx * dx + dz * dz or 1e-6
        t = np.clip(((xx - a[0]) * dx + (zz - a[1]) * dz) / L2, 0, 1)
        g = np.minimum(g, np.hypot(xx - (a[0] + dx * t), zz - (a[1] + dz * t)))
    return np.asarray(Image.fromarray(g.astype(np.float32), mode='F').resize((N, N), Image.BILINEAR))   # 倍精度のまま渡すと中身が壊れた


t0 = time.time()
paths = {k: catmull(v) for k, v in ROADS.items()}
road_d = np.minimum.reduce([dist_to_path(p) for p in paths.values()])

# ================================================================ 1. 区域を割る（揺らしたボロノイ）
wx = X + (noise(130, 1) - 0.5) * 190; wz = Z + (noise(130, 2) - 0.5) * 190   # 境目をうねらせる（まっすぐな多角形にしない）
D = np.stack([np.hypot(wx - CX[i], wz - CZ[i]) for i in range(K)])
order = np.argsort(D, axis=0)
region = order[0]; second = order[1]
d1 = np.take_along_axis(D, order[:1], 0)[0]; d2 = np.take_along_axis(D, order[1:2], 0)[0]
border = (d2 - d1) / 2                                                                  # 境目までの距離

# ================================================================ 2. 底の高さ（区域ごと、境目でなじませる）と型ごとの起伏
W8 = np.exp(-(D - d1) / 18.0); W8 /= W8.sum(0)
floor = (W8 * FLOOR[:, None, None]).sum(0)
amp = {'harbor': 2, 'sakura': 6, 'marsh': 2, 'meadow': 5, 'forest': 6, 'highland': 12, 'cliffs': 6, 'snow': 8, 'volcano': 6}
A = (W8 * np.array([amp[s] for s in STYLE], np.float32)[:, None, None]).sum(0)
hills = (noise(90, 3) - 0.5) * 2 * A + (noise(30, 4) - 0.5) * A * 0.5
h = floor + hills

# 風穴の崖：台地の段（見る崖）
cl = (W8[[i for i, s in enumerate(STYLE) if s == 'cliffs']].sum(0))
terr = np.floor((noise(70, 5) * 3)) * 9
h += cl * terr
# 熾火の火口：中央に火山（縁は見るだけ、火口の底は遊ぶ所）
vi = [i for i, s in enumerate(STYLE) if s == 'volcano'][0]
vd = np.hypot(X - CX[vi], (Z - CZ[vi]) * 1.1)
h += np.where(vd < 120, 70 * np.clip(1 - vd / 120, 0, 1) ** 1.3, 0) - np.where(vd < 38, 70 * np.clip(1 - vd / 38, 0, 1) ** 0.6, 0)

# ================================================================ 3. 境目の尾根（Tier が上がる境ほど高い）と外周の山、南の海
ti, tj = TIER[region], TIER[second]
ridge_h = np.where(ti == tj, 16, 26 + 10 * np.abs(ti - tj)).astype(np.float32)
ridge_h = np.where((STYLE_arr := np.array(STYLE))[region] == 'harbor', 10, ridge_h)
ridge_w = 34.0
ridge = ridge_h * np.clip(1 - border / ridge_w, 0, 1) ** 1.4 * (0.75 + 0.5 * noise(60, 6))
pass_cut = np.clip(road_d / 26.0, 0, 1) ** 1.5                                          # 峠：道の所だけ尾根を刻む
ridge *= pass_cut
rim_d = np.minimum.reduce([X, N - 1 - X, Z])                                              # 東・西・北の外周
rim = 95 * np.clip(1 - rim_d / 110.0, 0, 1) ** 1.3 * (0.7 + 0.6 * noise(80, 8))
coast = 925 + (noise(120, 9) - 0.5) * 70
coast = np.where(np.abs(X - 500) < 70, coast + 10, coast)                                # 港の入江は少し奥まで陸
sea = Z > coast
h = h + ridge + rim
h = np.where(sea, SEA - 6 - np.clip(Z - coast, 0, 30) * 0.4, np.where(Z > coast - 25, np.minimum(h, SEA + 1 + (coast - Z) * 0.25), h))

# ================================================================ 4. 侵食（尾根と外周の山だけ）と、遊ぶ所を均す
mount = (ridge + rim) > 10
print('NaN', int(np.isnan(h).sum()))
h = np.nan_to_num(h, nan=float(SEA))
print('高さの下地', round(time.time() - t0, 1), 's')
h, flow = TE.erode_np(h.astype(np.float64), drops=int(mount.sum() * 1.5), mask=mount)
h = TE.smooth_gentle_np(h, rounds=2).astype(np.float32)
play = ((ridge + rim) < 6) & ~sea                                                         # 遊ぶ所：ジャンプなしで歩ける傾斜に
for _ in range(3):
    h = np.where(play, blur(h, 3), h)
print('侵食と均し', round(time.time() - t0, 1), 's')

# ================================================================ 5. 街道を刻む（勾配は 1/4 まで、幅 5、両脇はなじませる）
road_y = {}
for k, p in paths.items():
    xs = np.clip(p[:, 0].astype(int), 0, N - 1); zs = np.clip(p[:, 1].astype(int), 0, N - 1)
    prof = np.array([h[z, x] for x, z in zip(xs, zs)], np.float32)
    seg = np.hypot(np.diff(p[:, 0]), np.diff(p[:, 1]))
    for it in range(40):                                                                 # 前後から勾配を 1/4 に抑える
        for i in range(1, len(prof)): prof[i] = np.clip(prof[i], prof[i - 1] - seg[i - 1] * 0.25, prof[i - 1] + seg[i - 1] * 0.25)
        for i in range(len(prof) - 2, -1, -1): prof[i] = np.clip(prof[i], prof[i + 1] - seg[i] * 0.25, prof[i + 1] + seg[i] * 0.25)
    road_y[k] = (p, prof)
ry = np.zeros((N, N), np.float32); rw = np.zeros((N, N), np.float32)
for k, (p, prof) in road_y.items():
    for (x, z), y in zip(p[::2], prof[::2]):
        x0, x1, z0, z1 = int(max(0, x - 10)), int(min(N, x + 11)), int(max(0, z - 10)), int(min(N, z + 11))
        dd = np.hypot(X[z0:z1, x0:x1] - x, Z[z0:z1, x0:x1] - z)
        wgt = np.clip(1 - (dd - 2.5) / 7.5, 0, 1)
        upd = wgt > rw[z0:z1, x0:x1]
        ry[z0:z1, x0:x1] = np.where(upd, y, ry[z0:z1, x0:x1]); rw[z0:z1, x0:x1] = np.where(upd, wgt, rw[z0:z1, x0:x1])
h = h * (1 - rw) + ry * rw
on_road = rw > 0.99

# ================================================================ 6. 水：霧沼の沼、泥炭の入江の干潟、川（北の山から緑風の谷を抜けて海へ）
water = sea.copy()
mi = [i for i, r in enumerate(REG) if r[0] == 'kirinuma'][0]
md = np.hypot(X - CX[mi] - 40, (Z - CZ[mi]) * 1.3) - 55 * (0.55 + 0.9 * noise(28, 10))   # 沼の岸は入り組ませる
pools = (md < 0) & ~on_road
h = np.where(pools, np.minimum(h, FLOOR[mi] - 2), h)
water |= pools
di = [i for i, r in enumerate(REG) if r[0] == 'deitan'][0]
flats = (region == di) & (noise(25, 11) > 0.62) & ~on_road & ~sea
h = np.where(flats, np.minimum(h, SEA - 1), h); water |= flats
river = catmull([(760, 120), (740, 230), (760, 360), (735, 470), (760, 620), (800, 740), (850, 860), (880, 960)])
rv_d = dist_to_path(river, step=2)
rv = (rv_d < 4 + 2 * noise(30, 12)) & (rim_d > 40)
bank = np.where(rv, 0, 0)
h = np.where(rv_d < 9, np.minimum(h, blur(h, 6) - 2 + np.clip(rv_d - 4, 0, 5) * 0.6), h)
river_surface = h - 1
water |= rv
bridges = rv & on_road

# ================================================================ 7. 地面の種類（塗り）
def slope_deg(a):
    gx = (np.roll(a, -2, 1) - np.roll(a, 2, 1)) / 4; gz = (np.roll(a, -2, 0) - np.roll(a, 2, 0)) / 4
    return np.degrees(np.arctan(np.hypot(gx, gz)))
S = slope_deg(h)
G = {'water': 0, 'sand': 1, 'grass': 2, 'sakura': 3, 'marsh': 4, 'forest': 5, 'meadow': 6, 'highland': 7, 'cliff': 8,
     'snow': 9, 'rock': 10, 'basalt': 11, 'road': 12, 'gravel': 13, 'ash': 14}
style_g = {'harbor': 'grass', 'sakura': 'sakura', 'marsh': 'marsh', 'meadow': 'meadow', 'forest': 'forest',
           'highland': 'highland', 'cliffs': 'cliff', 'snow': 'snow', 'volcano': 'ash'}
ground = np.array([G[style_g[s]] for s in STYLE])[region]
snowline = 150 + (noise(50, 13) - 0.5) * 20
ground = np.where((h > snowline) & (S < 45), G['snow'], ground)
ground = np.where(S >= 45, np.where(h > snowline - 10, G['rock'], G['rock']), ground)
ground = np.where((ground == G['ash']) & (vd < 60), G['basalt'], ground)
ground = np.where((S >= 35) & (S < 45) & (ground != G['snow']), G['gravel'], ground)
ground = np.where((Z > coast - 10) & ~sea, G['sand'], ground)
ground = np.where(on_road, G['road'], ground)
ground = np.where(water, G['water'], ground)

# ================================================================ 8. 遊びの点（目印・狩り場・採取・ボス・ダンジョン・近道の地点）
pois = []
def flat_spot(cx, cz, rmin, rmax, avoid_road=12, tries=400, seed=0):
    r = np.random.default_rng(seed)
    for _ in range(tries):
        a = r.uniform(0, 2 * math.pi); d = r.uniform(rmin, rmax)
        x, z = int(cx + math.cos(a) * d), int(cz + math.sin(a) * d)
        if not (40 < x < N - 40 and 40 < z < N - 40): continue
        if water[z, x] or road_d[z, x] < avoid_road or S[z, x] > 20 or not play[z, x]: continue
        if any(math.hypot(x - p['x'], z - p['z']) < 45 for p in pois): continue
        return x, z
    return None
LAND = {'sakura': '大きな桜の古木', 'marsh': '沈んだ見張り塔', 'meadow': '丘の上の風車の廃墟', 'forest': '白樫の巨木',
        'highland': '狼の岩（遠くから見える尖った岩）', 'cliffs': '風穴の大アーチ', 'snow': '雪原の砦の塔', 'volcano': '火口の黒い尖塔'}
for i, (key, name, style, tier, cx, cz, fl, gid) in enumerate(REG):
    if style == 'harbor':
        pois.append({'kind': 'town', 'name': name, 'x': cx, 'z': cz, 'region': key}); pois.append({'kind': 'waypoint', 'name': '港の近道の地点', 'x': cx, 'z': cz - 30, 'region': key}); continue
    s = flat_spot(cx, cz, 0, 40, 8, seed=i * 10)
    if s: pois.append({'kind': 'landmark', 'name': LAND[style], 'x': s[0], 'z': s[1], 'region': key})
    for j in range(3 if tier < 3 else 2):
        s = flat_spot(cx, cz, 50, 95, 14, seed=i * 10 + j + 1)
        if s: pois.append({'kind': 'hunt', 'name': '%sの狩り場%d' % (name, j + 1), 'x': s[0], 'z': s[1], 'region': key,
                           'lv': [(tier - 1) * 10 + 1 + j * 3, (tier - 1) * 10 + 4 + j * 3]})
    for j in range(6):
        s = flat_spot(cx, cz, 30, 105, 6, seed=i * 10 + 50 + j)
        if s: pois.append({'kind': 'gather', 'name': '採取', 'x': s[0], 'z': s[1], 'region': key})
    s = flat_spot(cx, cz, 70, 110, 10, seed=i * 10 + 90)
    if s: pois.append({'kind': 'boss', 'name': '%sのボスの広場' % name, 'x': s[0], 'z': s[1], 'region': key})
    if tier >= 2:
        s = flat_spot(cx, cz, 80, 115, 16, seed=i * 10 + 95)
        if s: pois.append({'kind': 'dungeon', 'name': '%sの洞窟' % name, 'x': s[0], 'z': s[1], 'region': key})
for (x, z, n) in [(495, 780, '三叉路の近道の地点'), (500, 640, '花筏の峠の近道の地点'), (520, 440, '霧沼の北の近道の地点'), (640, 330, '雪原への峠の近道の地点')]:
    pois.append({'kind': 'waypoint', 'name': n, 'x': x, 'z': z, 'region': REG[region[z, x]][0]})

# ================================================================ 9. 検査：歩けるか
gy = np.abs(np.diff(h, axis=1)); gzz = np.abs(np.diff(h, axis=0))
step = np.maximum(np.pad(gy, ((0, 0), (0, 1))), np.pad(gzz, ((0, 1), (0, 0))))
walk_ok = (step[play & ~water] <= 1.0).mean()
road_ok = (step[on_road] <= 1.0).mean()
print('遊ぶ所の面積 %.0f%%、ジャンプなしで歩けるマス %.1f%%、街道 %.1f%%' % (play.mean() * 100, walk_ok * 100, road_ok * 100))

# ================================================================ 10. 書き出しと、上から見た地図
np.save(os.path.join(OUT, 'height.npy'), h.astype(np.float32)); np.save(os.path.join(OUT, 'ground.npy'), ground.astype(np.uint8))
np.save(os.path.join(OUT, 'region.npy'), region.astype(np.uint8)); np.save(os.path.join(OUT, 'water.npy'), water)
json.dump({'size': N, 'sea': SEA, 'regions': [{'key': r[0], 'name': r[1], 'style': r[2], 'tier': r[3], 'x': r[4], 'z': r[5], 'zone_id': r[7]} for r in REG],
           'roads': {k: [[float(a), float(b)] for a, b in v] for k, v in ROADS.items()}, 'pois': pois,
           'checks': {'walkable': float(walk_ok), 'road_walkable': float(road_ok), 'play_area': float(play.mean())}},
          open(os.path.join(OUT, 'pois.json'), 'w'), ensure_ascii=False, indent=1)

COL = {0: (64, 118, 196), 1: (219, 207, 158), 2: (112, 162, 72), 3: (150, 168, 92), 4: (96, 120, 70), 5: (64, 112, 52),
       6: (132, 172, 80), 7: (150, 158, 96), 8: (176, 140, 104), 9: (236, 240, 244), 10: (128, 128, 128), 11: (58, 54, 58),
       12: (186, 160, 112), 13: (150, 144, 136), 14: (92, 86, 84)}
rgb = np.zeros((N, N, 3), np.float32)
for g, c in COL.items(): rgb[ground == g] = c
pink = (ground == G['sakura']) & (noise(14, 14) > 0.62)
rgb[pink] = (232, 170, 196)
treecov = ((ground == G['forest']) & (noise(9, 15) > 0.45)) | ((ground == G['meadow']) & (noise(9, 16) > 0.78)) | ((ground == G['highland']) & (noise(9, 17) > 0.72))
rgb[treecov] = rgb[treecov] * 0.72
gx = np.roll(h, -1, 1) - np.roll(h, 1, 1); gz = np.roll(h, -1, 0) - np.roll(h, 1, 0)
shade = np.clip(0.78 + (-gx * 0.6 - gz * 0.6) * 0.09, 0.45, 1.25)
rgb = np.where(water[..., None], rgb * (0.9 + 0.1 * np.clip((h - (SEA - 12)) / 12, 0, 1))[..., None], rgb * shade[..., None])
edge = (region != np.roll(region, 1, 0)) | (region != np.roll(region, 1, 1))
rgb[edge & ~water] = rgb[edge & ~water] * 0.55 + np.array([255, 255, 255]) * 0.18
img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
F = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
f1, f2, f3 = ImageFont.truetype(F, 22, index=0), ImageFont.truetype(F, 14, index=0), ImageFont.truetype(F, 12, index=0)
dr = ImageDraw.Draw(img)
for (key, name, style, tier, cx, cz, fl, gid) in REG:
    lab = name if tier == 0 else '%s  T%d' % (name, tier)
    dr.text((cx, cz - 14), lab, font=f1, fill=(255, 255, 255), anchor='mm', stroke_width=3, stroke_fill=(30, 30, 30))
    dr.text((cx, cz + 10), LV[tier], font=f2, fill=(255, 236, 160), anchor='mm', stroke_width=2, stroke_fill=(30, 30, 30))
for k, (p, prof) in road_y.items():                                                   # 街道は太い線で（点線だと見えにくかった）
    pts = [tuple(q) for q in p[::3]]
    dr.line(pts, fill=(60, 44, 28), width=7, joint='curve'); dr.line(pts, fill=(226, 196, 140), width=4, joint='curve')
ICON = {'town': ((250, 220, 90), 9, 'o'), 'waypoint': ((90, 220, 250), 6, 'd'), 'landmark': ((255, 255, 255), 7, '^'),
        'hunt': ((230, 70, 60), 7, 'o'), 'gather': ((120, 230, 120), 3, 'o'), 'boss': ((170, 40, 200), 8, 's'), 'dungeon': ((40, 40, 40), 7, 's')}
for p in pois:
    c, r, sh = ICON[p['kind']]; x, z = p['x'], p['z']
    if sh == 'o': dr.ellipse([x - r, z - r, x + r, z + r], fill=c, outline=(20, 20, 20), width=2)
    elif sh == 's': dr.rectangle([x - r, z - r, x + r, z + r], fill=c, outline=(240, 240, 240), width=2)
    elif sh == 'd': dr.polygon([(x, z - r - 2), (x + r, z), (x, z + r + 2), (x - r, z)], fill=c, outline=(20, 20, 20))
    else: dr.polygon([(x, z - r - 3), (x + r, z + r), (x - r, z + r)], fill=c, outline=(20, 20, 20))
    if p['kind'] == 'hunt': dr.text((x, z + 14), 'Lv%d-%d' % tuple(p['lv']), font=f3, fill=(255, 220, 220), anchor='mm', stroke_width=2, stroke_fill=(40, 20, 20))
# 凡例
lg = [('town', '町（開拓港）'), ('waypoint', '近道の地点'), ('landmark', '区域の目印'), ('hunt', '狩り場（敵の Lv）'), ('gather', '採取'), ('boss', 'ボスの広場'), ('dungeon', '洞窟（ダンジョン）')]
dr.rectangle([12, 836, 196, 836 + 22 * len(lg) + 12], fill=(250, 248, 240), outline=(60, 60, 60), width=2)
for j, (kd, tx) in enumerate(lg):
    c, r, sh = ICON[kd]; x, z = 30, 852 + 22 * j
    if sh == 'o': dr.ellipse([x - 6, z - 6, x + 6, z + 6], fill=c, outline=(20, 20, 20), width=2)
    elif sh == 's': dr.rectangle([x - 6, z - 6, x + 6, z + 6], fill=c, outline=(60, 60, 60), width=2)
    elif sh == 'd': dr.polygon([(x, z - 8), (x + 6, z), (x, z + 8), (x - 6, z)], fill=c, outline=(20, 20, 20))
    else: dr.polygon([(x, z - 8), (x + 7, z + 6), (x - 7, z + 6)], fill=c, outline=(20, 20, 20))
    dr.text((44, z), tx, font=f2, fill=(30, 30, 30), anchor='lm')
img.save(os.path.join(OUT, 'map.png'))
print('書き出し', OUT, round(time.time() - t0, 1), 's', '点', len(pois))
