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

# ================================================================ 2b. 区域ごとに違う形（11/04 藍「少し単調」→ 探検の決まり 76〜）
#   ・中の丘：高さ 8〜16、半径 16〜28。街道から根元を隠し、越えると次の物が見える（BotW の三角形の法則・隠して見せる）。
#   ・見晴らしの丘：区域に1〜2。登れる傾斜で +20〜28、頂上から次の区域の目印が見える（GW2 のビスタ、prospect）。
#   ・区域だけの大きな地形を1つ（Wynncraft の Nivla の牙・Kaul の大穴、エルデンのレガシーダンジョン）。
def seg_dist(ax, az, bx, bz, box=None):
    dx, dz = bx - ax, bz - az; L2 = dx * dx + dz * dz or 1e-6
    t = np.clip(((X - ax) * dx + (Z - az) * dz) / L2, 0, 1)
    return np.hypot(X - (ax + dx * t), Z - (az + dz * t)), t
FEAT = []                                                                                 # 形の印（区画を組む時に建物・滝・アーチを足す）
def bump(cx, cz, r, hh, power=2.0):
    d = np.hypot(X - cx, Z - cz) / r
    return hh * np.clip(np.cos(np.clip(d, 0, 1) * math.pi / 2), 0, 1) ** power
frng = np.random.default_rng(23)
for i, (key, name, style, tier, cx, cz, fl, gid) in enumerate(REG):
    if style in ('harbor', 'volcano'): continue
    inside = (region == i) & (road_d > 22) & (border > 30)
    cells = np.argwhere(inside)
    if len(cells) == 0: continue
    nh = int(frng.integers(5, 10)); got = []
    for c in cells[frng.permutation(len(cells))]:
        z_, x_ = int(c[0]), int(c[1])
        if all(math.hypot(x_ - a, z_ - b) > 55 for a, b in got):
            got.append((x_, z_))
            r_ = float(frng.uniform(22, 34)); hh_ = float(frng.uniform(7, 13)) * (0.6 if style == 'marsh' else 1.0)
            h += bump(x_, z_, r_, hh_) * np.clip((road_d - 8) / 14, 0, 1)
            FEAT.append({'kind': 'hill', 'x': x_, 'z': z_, 'r': r_, 'h': hh_, 'region': key})
        if len(got) >= nh: break
    near = np.argwhere((region == i) & (road_d > 40) & (road_d < 95) & (border > 35))
    for c in near[frng.permutation(len(near))][:1 if tier != 1 else 2]:
        z_, x_ = int(c[0]), int(c[1]); hh_ = float(frng.uniform(20, 28))
        h += bump(x_, z_, 46, hh_, 1.4)
        FEAT.append({'kind': 'vantage', 'x': x_, 'z': z_, 'r': 46, 'h': hh_, 'region': key})

def reg(key): return [i for i, r in enumerate(REG) if r[0] == key][0]
def at(key, dx, dz): r = REG[reg(key)]; return r[4] + dx, r[5] + dz
# 区域だけの大きな地形
x_, z_ = at('hanaikada', -60, -10); h -= bump(x_, z_, 34, 14, 1.2); FEAT.append({'kind': 'pool', 'x': x_, 'z': z_, 'r': 26, 'region': 'hanaikada', 'name': '花筏の淵（滝壺）'})
h += np.where(np.hypot(X - x_, Z - (z_ - 30)) < 26, 12 * np.clip(1 - np.hypot(X - x_, Z - (z_ - 30)) / 26, 0, 1) ** 0.4, 0)   # 淵の北の段（滝が落ちる崖）
FEAT.append({'kind': 'waterfall', 'x': x_, 'z': z_ - 18, 'region': 'hanaikada', 'name': '花筏の滝'})
for k_ in range(6):                                                                       # 泥炭の入江の沖の岩柱（遠くから見える）
    x_, z_ = 120 + k_ * 34 + int(frng.integers(-8, 9)), 952 + int(frng.integers(-10, 12))
    h = np.where(np.hypot(X - x_, Z - z_) < 5 + k_ % 3, np.maximum(h, SEA + 18 + 6 * (k_ % 3) + 4 * noise(6, 40 + k_)), h)
    FEAT.append({'kind': 'seastack', 'x': x_, 'z': z_, 'region': 'deitan'})
x_, z_ = at('shunrai', 40, -40); h += bump(x_, z_, 30, 36, 0.6); FEAT.append({'kind': 'pinnacle', 'x': x_, 'z': z_, 'r': 30, 'region': 'shunrai', 'name': '春雷の社の岩塔'})
x_, z_ = at('hitsuji', 10, 20); h += bump(x_, z_, 55, 18, 1.0); FEAT.append({'kind': 'dome', 'x': x_, 'z': z_, 'r': 55, 'region': 'hitsuji', 'name': '羊飼いの丸丘（石の輪）'})
x_, z_ = at('ookami', -10, 0); a_ = math.radians(35)
dd_, t_ = seg_dist(x_ - math.cos(a_) * 80, z_ - math.sin(a_) * 80, x_ + math.cos(a_) * 80, z_ + math.sin(a_) * 80)
h += np.where(dd_ < 16, 22 * np.clip(1 - dd_ / 16, 0, 1) ** 0.5 * np.clip(np.minimum(t_, 1 - t_) * 6, 0, 1), 0)   # 狼の背：歩ける尾根道（両端は坂）
FEAT.append({'kind': 'spine', 'x': x_, 'z': z_, 'region': 'ookami', 'name': '狼の背の尾根道'})
x_, z_ = at('shirakashi', 30, 40); h -= bump(x_, z_, 26, 16, 0.7); FEAT.append({'kind': 'sinkhole', 'x': x_, 'z': z_, 'r': 26, 'region': 'shirakashi', 'name': '白樫の陥没穴'})
x_, z_ = at('setsugen', 30, 30); h += bump(x_, z_, 34, 16, 0.5); FEAT.append({'kind': 'forthill', 'x': x_, 'z': z_, 'r': 34, 'region': 'setsugen', 'name': '雪原の砦の丘'})
x_, z_ = at('fuketsu', 0, 30); FEAT.append({'kind': 'arch', 'x': x_, 'z': z_, 'region': 'fuketsu', 'name': '風穴の大アーチ'})
FEAT.append({'kind': 'lava', 'x': int(CX[reg('okibi')]), 'z': int(CZ[reg('okibi')]), 'region': 'okibi', 'name': '熾火の溶岩湖'})

# ================================================================ 3. 山脈の骨：主脈（区域の境目と外周）→ 主峰 → 枝尾根 → 裾 [rules 68、Lord Dakr 0:35・2:00、Calven 28:00]
def profile(dd, w, top):
    """尾根の断面：尾根で尖り、幅 w で下に凸に下がり、幅の2倍まで緩い裾（前山）が続く。"""
    s_ = np.clip(1 - dd / w, 0, 1)
    sk = np.clip(1 - (dd - w) / w, 0, 1) ** 2 * 0.22
    return top * np.where(dd < w, 0.22 + 0.78 * s_ ** 1.4, sk)

def seg_dist(ax, az, bx, bz, box=None):
    dx, dz = bx - ax, bz - az; L2 = dx * dx + dz * dz or 1e-6
    t = np.clip(((X - ax) * dx + (Z - az) * dz) / L2, 0, 1)
    return np.hypot(X - (ax + dx * t), Z - (az + dz * t)), t

ti, tj = TIER[region], TIER[second]
STYLE_arr = np.array(STYLE)
ridge_top = np.where(ti == tj, 22, 30 + 14 * np.abs(ti - tj)).astype(np.float32)          # Tier が上がる境ほど高い主脈
ridge_top = np.where(STYLE_arr[region] == 'harbor', 12, ridge_top) * (0.7 + 0.6 * noise(70, 6))
pass_cut = np.clip(road_d / 30.0, 0, 1) ** 1.5                                             # 峠：街道の所だけ刻む
mount = profile(border, 26.0, ridge_top)
rim_d = np.minimum.reduce([X, N - 1 - X, Z])                                                # 東・西・北の外周
rim_top = 105 * (0.75 + 0.5 * noise(90, 8))
mount = np.maximum(mount, profile(rim_d, 70.0, rim_top))

# 主峰：主脈の上に、Tier の境ごとに1〜2本だけ突き出す。外周の北には一番高い峰。 [Calven 28:00「主峰を1〜2本」]
prng = np.random.default_rng(11)
peaks = []
bmask = (border < 2) & (road_d > 70) & (rim_d > 60)
for (i, j) in sorted({(min(a_, b_), max(a_, b_)) for a_, b_ in zip(region[bmask].tolist(), second[bmask].tolist())}):
    cells = np.argwhere(bmask & (((region == i) & (second == j)) | ((region == j) & (second == i))))
    if len(cells) < 30: continue
    k = 1
    for c in cells[prng.choice(len(cells), k, replace=False)]:
        px_, pz_ = int(c[1]), int(c[0])
        if math.hypot(px_ - 500, pz_ - 150) < 170: continue                                   # 火口のまわりは火山だけ
        peaks.append((px_, pz_, 30 + 14 * abs(int(TIER[i]) - int(TIER[j])) + 8 * max(TIER[i], TIER[j]), 44, i, j))
for (x_, z_) in [(250, 45), (500, 30), (760, 40), (40, 300), (960, 330), (30, 620), (970, 640)]:
    peaks.append((x_, z_, 75 if z_ < 100 else 55, 50, -1, -1))
spurs = []
for (px, pz, ph, pr, i, j) in peaks:
    mount = np.maximum(mount, profile(np.hypot(X - px, (Z - pz) * 1.1) * (0.85 + 0.3 * noise(40, 30)), pr, ph + float(rim_top[pz, px] if i < 0 else ridge_top[pz, px])))
    # 枝尾根：峰から区域の中へ、主脈に対して 30〜60°で2〜3本。長さは区域の中心までの 4 割まで（遊ぶ所を塞がない）
    for r_ in ([i, j] if i >= 0 else [int(region[min(N - 1, pz + 80) if pz < 500 else pz, min(N - 1, max(0, px))])]):
        cx_, cz_ = CX[r_], CZ[r_]
        base = math.atan2(cz_ - pz, cx_ - px)
        for sgn in (-1, 1):
            ang = base + sgn * math.radians(30 + 30 * prng.random())
            L = min(55.0, 0.3 * math.hypot(cx_ - px, cz_ - pz))
            spurs.append((px, pz, px + math.cos(ang) * L, pz + math.sin(ang) * L, ph * 0.75, ph * 0.15))
for (px, pz, ph, pr, i, j) in list(peaks):                                                  # 大きな峰のまわりに小峰を3つ（尖りを和らげる） [Lord Dakr 4:30]
    for k_ in range(2):
        a_ = prng.uniform(0, 2 * math.pi); d_ = pr * prng.uniform(0.4, 0.6)
        qx, qz = px + math.cos(a_) * d_, pz + math.sin(a_) * d_
        mount = np.maximum(mount, profile(np.hypot(X - qx, Z - qz), pr * 0.45, ph * 0.5 + float(ridge_top[int(np.clip(qz, 0, N - 1)), int(np.clip(qx, 0, N - 1))])))
for (ax, az, bx, bz, h0, h1) in spurs:
    dd, t = seg_dist(ax, az, bx, bz)
    mount = np.maximum(mount, profile(dd * (0.85 + 0.3 * noise(30, 31)), 12.0, (h0 + (h1 - h0) * t) * 0.8))
mount *= np.where(rim_d < 60, 1.0, pass_cut)
coast = 925 + (noise(120, 9) - 0.5) * 70
coast = np.where(np.abs(X - 500) < 70, coast + 10, coast)                                  # 港の入江は少し奥まで陸
sea = Z > coast
h = h + mount
h = np.where(sea, SEA - 6 - np.clip(Z - coast, 0, 30) * 0.4, np.where(Z > coast - 25, np.minimum(h, SEA + 1 + (coast - Z) * 0.25), h))
h = np.nan_to_num(h, nan=float(SEA))
print('山脈の骨', round(time.time() - t0, 1), 's', '峰', len(peaks), '枝尾根', len(spurs))

# ================================================================ 4. 侵食を全体に2回（柔らかく長く → 硬く弱く）、緩い面を均す、遊ぶ所をならす [rules 69・71]
land = ~sea
h, flow = TE.erode_np(h.astype(np.float64), drops=int(land.sum() * 1.5), mask=land)
h2, flow2 = TE.erode_np(h, drops=int(land.sum() * 0.6), seed=6, erode_rate=0.1, capacity=2.0, mask=land)
h = h2; flow = flow + flow2
h = TE.smooth_gentle_np(h, rounds=2).astype(np.float32)
cliffw = cl * np.clip((road_d - 20) / 25, 0, 1) * (mount < 30)
h = np.where(cliffw > 0.5, np.floor(h / 8) * 8 + np.clip((h % 8) - 6, 0, 2), h)               # 風穴の崖：高さ8の台地の段（見る崖）。道の近くは段にしない
play = (mount < 16) & ~sea & ~(cliffw > 0.5)                                                  # 遊ぶ所：ジャンプなしで歩ける傾斜に
for _ in range(2):
    h = np.where(play, blur(h, 2), h)
vf = FLOOR[vi]
cone = vf + 75 * np.clip(1 - vd / 130, 0, 1) ** 1.2 * (0.9 + 0.2 * noise(20, 34))
crater = vd < 46 + 6 * noise(15, 35)
h = np.where(vd < 130, np.maximum(h, cone), h)
cr = 46 + 6 * noise(15, 35)
tw = np.clip((vd - cr) / 22.0, 0, 1); tw = tw * tw * (3 - 2 * tw)                             # 火口の内側の壁：縁から底へ 22 マスかけて下げる（まっすぐな円筒の壁にしない）
h = np.where(vd < cr + 22, (vf + 22 + 3 * noise(10, 36)) * (1 - tw) + h * tw, h)          # 火口の底：平らな遊ぶ所
h = np.where((vd < 130) & (road_d < 6), np.minimum(h, vf + 24 + np.clip(vd - 46, 0, 90) * 0.0), h)   # 街道は火口の縁を切って入る
play |= crater
for f in FEAT:                                                                            # 岩塔・岩柱・陥没穴の壁は見る所（道だけ通す）
    if f['kind'] in ('pinnacle', 'seastack'): play &= np.hypot(X - f['x'], Z - f['z']) > (f.get('r', 8) * 0.8)
flow_cut = np.quantile(flow[land], 0.92)
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

# ================================================================ 5b. 小道：区域ごとに街道から出て戻る輪（見晴らし・大きな地形を通る）と、隣の区域への横の近道
#   [theory 7：区域ごとに輪を1本 300〜600 マス、数珠つなぎの中に横の近道を2〜3本。mmo 11：層を輪でつなぐ]
allroad = np.concatenate([p for p in paths.values()])
def road_near(x, z):
    d = np.hypot(allroad[:, 0] - x, allroad[:, 1] - z); return tuple(allroad[int(np.argmin(d))])
TRAILS = {}
for i, (key, name, style, tier, cx, cz, fl, gid) in enumerate(REG):
    if style in ('harbor',): continue
    stops = [f for f in FEAT if f['region'] == key and f['kind'] in ('vantage', 'pool', 'pinnacle', 'dome', 'spine', 'sinkhole', 'forthill', 'arch', 'ice', 'sunken_tower')]
    if not stops: continue
    stops = sorted(stops, key=lambda f: math.atan2(f['z'] - cz, f['x'] - cx))
    a_ = road_near(cx - 80, cz + 20); b_ = road_near(cx + 80, cz - 20)
    pts = [a_]
    for f in stops:
        off = (f.get('r', 20) * 0.9) if f['kind'] in ('pinnacle', 'sinkhole', 'pool', 'ice') else 0     # 縁を回る
        ang = math.atan2(cz - f['z'], cx - f['x'])
        pts.append((f['x'] + math.cos(ang) * off, f['z'] + math.sin(ang) * off))
    pts.append(b_)
    if math.hypot(a_[0] - b_[0], a_[1] - b_[1]) < 40: pts.append(road_near(cx, cz + 60))
    TRAILS['loop_' + key] = pts
TRAILS['loop_harbor_w'] = [(492, 858), (450, 850), (395, 862), (360, 840), (390, 805), (430, 788)]   # 港の小道：町から西の浜と丘を回って街道へ（最初の寄り道）
TRAILS['loop_harbor_e'] = [(572, 796), (565, 840), (590, 880), (628, 862), (632, 808)]               # 東：川の向こうは街道から入って浜と丘の陰を回る（橋がまだ無いので川を渡らない）
TRAILS['cross_deitan_hitsuji'] = [(225, 790), (210, 700), (250, 640), (290, 600)]          # 横の近道（数珠の間をつなぐ）
TRAILS['cross_shunrai_ryokufu'] = [(785, 780), (790, 700), (750, 640), (720, 600)]
TRAILS['cross_kirinuma_ryokufu'] = [(560, 520), (640, 560), (700, 580)]
TRAILS['cross_hitsuji_shirakashi'] = [(290, 570), (300, 470), (330, 360)]
tpaths = {k: catmull([(float(a), float(b)) for a, b in v], n=16) for k, v in TRAILS.items()}
trail_d = np.minimum.reduce([dist_to_path(p, step=2) for p in tpaths.values()])
tw_ = np.zeros((N, N), np.float32); ty_ = np.zeros((N, N), np.float32)
for k, p in tpaths.items():                                                               # 小道の勾配は 1/3 まで（見晴らしの丘は回り込んで登る）
    xs = np.clip(p[:, 0].astype(int), 0, N - 1); zs = np.clip(p[:, 1].astype(int), 0, N - 1)
    prof = np.array([h[z, x] for x, z in zip(xs, zs)], np.float32)
    seg = np.hypot(np.diff(p[:, 0]), np.diff(p[:, 1]))
    for it in range(30):
        for j in range(1, len(prof)): prof[j] = np.clip(prof[j], prof[j - 1] - seg[j - 1] * 0.33, prof[j - 1] + seg[j - 1] * 0.33)
        for j in range(len(prof) - 2, -1, -1): prof[j] = np.clip(prof[j], prof[j + 1] - seg[j] * 0.33, prof[j + 1] + seg[j] * 0.33)
    for (x, z), y in zip(p, prof):
        x0, x1, z0, z1 = int(max(0, x - 6)), int(min(N, x + 7)), int(max(0, z - 6)), int(min(N, z + 7))
        dd = np.hypot(X[z0:z1, x0:x1] - x, Z[z0:z1, x0:x1] - z)
        wgt = np.clip(1 - (dd - 1.5) / 4.5, 0, 1) * (1 - rw[z0:z1, x0:x1])
        upd = wgt > tw_[z0:z1, x0:x1]
        ty_[z0:z1, x0:x1] = np.where(upd, y, ty_[z0:z1, x0:x1]); tw_[z0:z1, x0:x1] = np.where(upd, wgt, tw_[z0:z1, x0:x1])
h = h * (1 - tw_) + ty_ * tw_
on_trail = (tw_ > 0.9) & ~on_road
play |= on_trail
def walkable_relax(h, mask, iters=30):
    """遊ぶ所の段差を1マスまでに寄せる：隣4マスの高さから [最大−1, 最小+1] に収める（ジャンプなしで歩ける）。
    侵食で丘や前山に刻まれた細い溝が、1段より深い段になっていた。"""
    for _ in range(iters):
        p_ = np.pad(h, 1, mode='edge')
        nb = np.stack([p_[1:-1, 2:], p_[1:-1, :-2], p_[2:, 1:-1], p_[:-2, 1:-1]])
        lo = nb.max(0) - 1.0; hi = nb.min(0) + 1.0
        tgt = np.where(lo <= hi, np.clip(h, lo, hi), (lo + hi) / 2)
        h = np.where(mask, tgt, h)
    return h
print('小道', len(TRAILS), round(time.time() - t0, 1), 's')

# ================================================================ 6. 水：霧沼の沼、泥炭の入江の干潟、川（北の山から緑風の谷を抜けて海へ）
water = sea.copy()
mi = [i for i, r in enumerate(REG) if r[0] == 'kirinuma'][0]
md = np.hypot(X - CX[mi] - 40, (Z - CZ[mi]) * 1.3) - 55 * (0.55 + 0.9 * noise(28, 10))   # 沼の岸は入り組ませる
pools = (md < 0) & ~on_road
islands = np.zeros((N, N), bool)
for k_, (ox, oz, rr) in enumerate([(30, -10, 9), (70, 15, 6), (55, -30, 5)]):              # 霧沼の島（沈んだ塔は一番大きい島の脇）
    islands |= np.hypot(X - CX[mi] - ox, Z - CZ[mi] - oz) < rr + 2 * noise(8, 50 + k_)
FEAT.append({'kind': 'sunken_tower', 'x': int(CX[mi] + 30), 'z': int(CZ[mi] - 22), 'region': 'kirinuma', 'name': '霧沼の沈んだ塔'})
pools &= ~islands
h = np.where(pools, np.minimum(h, FLOOR[mi] - 2), np.where(islands & (md < 0), np.maximum(h, FLOOR[mi] + 1), h))
water |= pools
for f in FEAT:
    if f['kind'] == 'pool':                                                               # 花筏の淵：滝壺
        pd_ = np.hypot(X - f['x'], Z - f['z']) < f['r'] * (0.85 + 0.3 * noise(10, 60))
        h = np.where(pd_, np.minimum(h, FLOOR[reg('hanaikada')] - 4), h); water |= pd_
si = reg('setsugen'); fx_, fz_ = CX[si] - 50, CZ[si] - 10                                   # 雪原の凍った湖
ice = (np.hypot(X - fx_, (Z - fz_) * 1.2) < 34 * (0.8 + 0.4 * noise(20, 61))) & ~on_road
h = np.where(ice, np.minimum(h, FLOOR[si] - 1), h)
FEAT.append({'kind': 'ice', 'x': int(fx_), 'z': int(fz_), 'region': 'setsugen', 'name': '雪原の凍った湖'})
lava = (vd < 20 + 5 * noise(10, 62))                                                       # 火口の溶岩湖（真ん中だけ。縁は歩ける）
h = np.where(lava, h - 1, h); play &= ~lava
di = [i for i, r in enumerate(REG) if r[0] == 'deitan'][0]
flats = (region == di) & (noise(25, 11) > 0.62) & ~on_road & ~sea
h = np.where(flats, np.minimum(h, SEA - 1), h); water |= flats
def trace_river(sx, sz, step=4):
    """川は流れに任せて引く：低い所を埋めた地形（priority flood）の上で、源から一番低い隣へ下り続けて海まで。
    線で引くと谷に沿わず、尾根や雪原を横切った。 [McMeddon 6:19「川は流れの地図から」、Lord Dakr 9:05]"""
    import heapq
    c = h[::step, ::step].astype(np.float64); n0, n1 = c.shape
    filled = np.full_like(c, np.inf); seen = np.zeros(c.shape, bool); pq = []
    for z in range(n0):
        for x in range(n1):
            if z == n0 - 1 or x in (0, n1 - 1) or z == 0 or sea[z * step, x * step]:
                filled[z, x] = c[z, x]; seen[z, x] = True; heapq.heappush(pq, (c[z, x], z, x))
    down = {}
    while pq:
        v, z, x = heapq.heappop(pq)
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            zz, xx = z + dz, x + dx
            if 0 <= zz < n0 and 0 <= xx < n1 and not seen[zz, xx]:
                seen[zz, xx] = True; filled[zz, xx] = max(c[zz, xx], v + 1e-3); down[(zz, xx)] = (z, x)
                heapq.heappush(pq, (filled[zz, xx], zz, xx))
    pts = []; cur = (sz // step, sx // step)
    while cur in down and len(pts) < 2000:
        pts.append((cur[1] * step + step / 2, cur[0] * step + step / 2)); cur = down[cur]
        if sea[min(N - 1, cur[0] * step), min(N - 1, cur[1] * step)]: pts.append((cur[1] * step, cur[0] * step)); break
    return np.array(pts, np.float32)
rivers = [trace_river(760, 150), trace_river(250, 160)]
rv = np.zeros((N, N), bool); bridge = np.zeros((N, N), bool); river_lines = []
for rp in rivers:
    if len(rp) < 10: continue
    sm = np.array([rp[max(0, i - 3):i + 4].mean(0) for i in range(len(rp))])            # 折れ線をならす
    seg = np.hypot(*np.diff(sm, axis=0).T); cum = np.concatenate([[0], np.cumsum(seg)])
    tt = np.arange(0, cum[-1], 1.0)                                                       # 1マスおきに置き直す（間が空くと点線になった）
    sm = np.stack([np.interp(tt, cum, sm[:, 0]), np.interp(tt, cum, sm[:, 1])], 1)
    river_lines.append(sm)
    d = dist_to_path(sm, step=2)
    ys = np.array([h[int(min(N - 1, z)), int(min(N - 1, x))] for x, z in sm]); ys = np.minimum.accumulate(ys)  # 水面は下流へ下がるだけ
    wid = np.linspace(2.5, 6.0, len(sm))
    for (x, z), y, w_ in zip(sm[::2], ys[::2], wid[::2]):
        x0, x1, z0, z1 = int(max(0, x - 12)), int(min(N, x + 13)), int(max(0, z - 12)), int(min(N, z + 13))
        dd = np.hypot(X[z0:z1, x0:x1] - x, Z[z0:z1, x0:x1] - z)
        wet = dd < w_ + 1.5 * noise(30, 12)[z0:z1, x0:x1]
        bed = np.where(wet, y - 2, np.minimum(h[z0:z1, x0:x1], y + np.clip(dd - w_, 0, 8) * 0.7))   # 川の外は水面より高く、岸はなだらか
        keep = rw[z0:z1, x0:x1] > 0.5                                                     # 街道の所は削らない（橋の場所）
        h[z0:z1, x0:x1] = np.where((dd < w_ + 8) & ~keep, np.minimum(h[z0:z1, x0:x1], bed), h[z0:z1, x0:x1])
        rv[z0:z1, x0:x1] |= wet & (rim_d[z0:z1, x0:x1] > 30) & ~keep
        bridge[z0:z1, x0:x1] |= wet & keep
water |= rv
bridges = rv & on_road

wet_near = water.copy()
for _ in range(3): wet_near = wet_near | np.roll(wet_near, 1, 0) | np.roll(wet_near, -1, 0) | np.roll(wet_near, 1, 1) | np.roll(wet_near, -1, 1)
h = walkable_relax(h, play & ~wet_near & ~on_road & ~on_trail & ~lava, iters=60)          # 水際3マスは触らない（水が漏れる）

# ================================================================ 7. 地面の種類（塗り）
def slope_deg(a):
    gx = (np.roll(a, -2, 1) - np.roll(a, 2, 1)) / 4; gz = (np.roll(a, -2, 0) - np.roll(a, 2, 0)) / 4
    return np.degrees(np.arctan(np.hypot(gx, gz)))
S = slope_deg(h)
G = {'water': 0, 'sand': 1, 'grass': 2, 'sakura': 3, 'marsh': 4, 'forest': 5, 'meadow': 6, 'highland': 7, 'cliff': 8,
     'snow': 9, 'rock': 10, 'basalt': 11, 'road': 12, 'gravel': 13, 'ash': 14, 'trail': 15, 'ice': 16, 'lava': 17}
style_g = {'harbor': 'grass', 'sakura': 'sakura', 'marsh': 'marsh', 'meadow': 'meadow', 'forest': 'forest',
           'highland': 'highland', 'cliffs': 'cliff', 'snow': 'snow', 'volcano': 'ash'}
ground = np.array([G[style_g[s]] for s in STYLE])[region]
# 雪：高さと傾きの段（5マス下がるごとに許す傾きを5°減らす）＋谷筋を流れ下る雪の筋。急な所（45°以上）は岩。
# 雪原の砦は区域ごと雪原（緩い面）。 [rules 55・72・74、Lord Dakr 22:10、Calven 34:30]
SL = 128 + (noise(50, 13) - 0.5) * 16
snow = np.zeros((N, N), bool)
for i in range(6): snow |= (h >= SL - 5 * i) & (S < 55 - 5 * i)
snow |= (flow > flow_cut) & (h >= SL - 18) & (S < 70)
snow |= (STYLE_arr[region] == 'snow') & (S < 40)
snow &= ~((STYLE_arr[region] == 'volcano') & (mount < 40))                                # 火山は灰と玄武岩（雪は外周の高い峰だけ）
ground = np.where(snow, G['snow'], ground)
ground = np.where((S >= 45) & ~((flow > flow_cut) & (h >= SL - 18)), G['rock'], ground)
ground = np.where((ground == G['ash']) & (vd < 60), G['basalt'], ground)
ground = np.where((S >= 35) & (S < 45) & (ground != G['snow']), G['gravel'], ground)
ground = np.where((h >= SL - 25) & (h < SL) & (S < 35) & (ground != G['snow']) & (noise(12, 33) > 0.55), G['gravel'], ground)   # 雪の線の下は砂利と粗い土がまだら
ground = np.where((Z > coast - 10) & ~sea, G['sand'], ground)
ground = np.where(on_road, G['road'], ground)
ground = np.where(on_trail, G['trail'], ground)
ground = np.where(water, G['water'], ground)
ground = np.where(ice, G['ice'], ground)
ground = np.where(lava, G['lava'], ground)

# ================================================================ 8. 遊びの点：数も種類も区域ごとに変え、丘の陰・街道から少しそれた所に、固まりと空き地で [rules 76〜]
pois = []
prg = np.random.default_rng(31)
NAMES = {
    'sakura': ['古い茶屋の跡', '花の祠', '苔の石段', '倒れた鳥居', '桜の洞', '花見の岩'],
    'marsh': ['沈んだ小舟', '葦の隠れ家', '杭の桟橋の跡', '泥の洞窟', '鷺の巣', '浮き島'],
    'meadow': ['石の輪', '羊飼いの小屋の跡', '古い井戸', '風車の廃墟', '狼の穴', '花畑の丘'],
    'forest': ['狩人の野営の跡', '倒木の橋', '木こりの小屋の跡', '獣道の洞窟', '古木の洞', '苔の泉'],
    'cliffs': ['鷹の巣', '崖の洞窟', '落石の野', '風の通り穴', '古い見張り台', '赤岩の割れ目'],
    'highland': ['岩の座', '狼の巣穴', '古い烽火台', '石積みの墓', '尾根の祠', '風見の岩'],
    'snow': ['凍った荷車', '雪の洞窟', '崩れた見張り塔', '氷柱の谷間', '遭難者の野営の跡', '霜の祭壇'],
    'harbor': ['開拓者の墓', '漁師の小屋の跡', '古い見張り台', '丘の洞', '崩れた石垣', '最初の開拓地の跡'],
    'volcano': ['黒曜の割れ目', '溶岩の洞', '焦げた砦の跡', '硫黄の泉', '灰の墓所', '溶岩の橋'],
}
SECRET = {'sakura': '石灯籠', 'marsh': '古い錨', 'meadow': '羊の石像', 'forest': '木彫りの梟', 'cliffs': '鷹の羽の碑',
          'highland': '狼の牙の石', 'snow': '凍った鐘', 'volcano': '黒曜の欠片', 'harbor': '開拓の碑'}
hills = [f for f in FEAT if f['kind'] in ('hill', 'vantage', 'dome', 'forthill', 'pinnacle')]
empty = noise(60, 70) < 0.27                                                                 # 何も置かない所（遊ぶ面積の2〜3割）[theory 12]
ok = play & ~water & ~ice & ~lava & (S < 20) & ~on_road
def near_pois(x, z, d): return any(math.hypot(x - p['x'], z - p['z']) < d for p in pois)
def pick(i, rmin_road, rmax_road, spacing, prefer_hill=True, extra=None, tries=600):
    key = REG[i][0]
    cand = np.argwhere(ok & (region == i) & (road_d >= rmin_road) & (road_d <= rmax_road) & ((extra) if extra is not None else True))
    if len(cand) == 0: return None
    best = None; bs = -1e9
    for c in cand[prg.choice(len(cand), min(tries, len(cand)), replace=False)]:
        z_, x_ = int(c[0]), int(c[1])
        if near_pois(x_, z_, spacing): continue
        sc = prg.random()
        if prefer_hill:                                                                      # 丘の陰（丘の縁から少し外）にあるほど良い
            dh = min([abs(math.hypot(x_ - f['x'], z_ - f['z']) - f.get('r', 20) - 8) for f in hills if f['region'] == key] or [99])
            sc += max(0, 1 - dh / 20)
        if sc > bs: bs, best = sc, (x_, z_)
    return best
for i, (key, name, style, tier, cx, cz, fl, gid) in enumerate(REG):
    if style == 'harbor':
        pois.append({'kind': 'town', 'name': name, 'x': cx, 'z': cz, 'region': key}); pois.append({'kind': 'waypoint', 'name': '港の近道の地点', 'x': cx, 'z': cz - 30, 'region': key})
        # 町の外（町の敷地の半径 55 より外）：最初の2分がいちばん濃い所。町を出て 30 秒で最初の狩り場と名所が見える [single-player 1、mmo 4]
        town_out = np.hypot(X - cx, Z - cz) > 55
        prg_state = prg.bit_generator.state                                                  # 港の分の乱数は、ほかの区域の配置をずらさないよう後で戻す
        for j, (lo_, hi_) in enumerate(((20, 50), (45, 100))):                               # 最初の狩り場：町のすぐ外は Lv1〜2、少し先は Lv2〜4
            sp = pick(i, lo_, hi_, 32, prefer_hill=False, extra=town_out & (np.hypot(X - cx, Z - cz) < 95 + 45 * j) & (Z < cz - 25))   # 町を出る向き（北）に
            if sp: pois.append({'kind': 'hunt', 'name': '港の外の狩り場', 'x': sp[0], 'z': sp[1], 'region': key, 'lv': [1 + j, 2 + 2 * j]})
        names = list(NAMES['harbor']); prg.shuffle(names)
        for j in range(5):
            sp = pick(i, 12, 90, 45, extra=town_out & (np.hypot(X - cx, Z - cz) < 170) & (trail_d < 28))   # 港の小道の脇に
            if not sp: break
            pois.append({'kind': 'cave' if '洞' in names[j] else 'place', 'name': names[j], 'x': sp[0], 'z': sp[1], 'region': key})
            a_ = prg.uniform(0, 2 * math.pi); sx, sz = int(sp[0] + math.cos(a_) * 14), int(sp[1] + math.sin(a_) * 14)
            if ok[sz, sx]: pois.append({'kind': 'small', 'name': '小さな見つけ物', 'x': sx, 'z': sz, 'region': key})
        sp = pick(i, 15, 60, 40, extra=town_out & (np.hypot(X - cx, Z - cz) < 120) & (Z < coast - 20) & (h > SEA + 2))     # 最初の秘密は町から見える所に（集める遊びを教える）
        if sp: pois.append({'kind': 'secret', 'name': SECRET['harbor'], 'x': sp[0], 'z': sp[1], 'region': key})
        prg.bit_generator.state = prg_state
        continue
    for f in FEAT:                                                                          # 大きな地形は区域の目印
        if f['region'] == key and f.get('name') and f['kind'] not in ('waterfall',):
            pois.append({'kind': 'landmark', 'name': f['name'], 'x': int(f['x']), 'z': int(f['z']), 'region': key})
    cand_r = [q for q in allroad if 0 <= int(q[0]) < N and 0 <= int(q[1]) < N and not (lava | water | ice)[int(q[1]), int(q[0])] and region[int(q[1]), int(q[0])] == i]
    rx, rz = min(cand_r, key=lambda q: math.hypot(q[0] - cx, q[1] - cz)) if cand_r else road_near(cx, cz)   # 溶岩・水の上に置かない
    pois.append({'kind': 'waypoint', 'name': '%sの近道の地点' % name, 'x': int(rx), 'z': int(rz), 'region': key})
    first = key == 'hanaikada'
    n_med = int(prg.integers(4, 8)) + (2 if first else 0)                                   # 名所の数は区域ごとに変える（同じ数を撒かない）
    names = list(NAMES[style]); prg.shuffle(names)
    for j in range(n_med):
        sp = pick(i, 20, 120, 65 if first else 85, extra=~empty)
        if not sp: break
        nm = names[j % len(names)]
        kind = 'cave' if '洞' in nm or '穴' in nm else 'place'
        pois.append({'kind': kind, 'name': nm, 'x': sp[0], 'z': sp[1], 'region': key})
        for k_ in range(int(prg.integers(1, 4))):                                           # 名所のそばに小さな見つけ物を1〜3（固まり）
            a_ = prg.uniform(0, 2 * math.pi); d_ = prg.uniform(8, 22)
            sx, sz = int(sp[0] + math.cos(a_) * d_), int(sp[1] + math.sin(a_) * d_)
            if 0 <= sx < N and 0 <= sz < N and ok[sz, sx]: pois.append({'kind': 'small', 'name': '小さな見つけ物', 'x': sx, 'z': sz, 'region': key})
    n_hunt = int(prg.integers(2, 5)) if tier < 4 else 3
    for j in range(n_hunt):                                                                 # 狩り場：道の近くは弱く、奥ほど強い
        sp = pick(i, 35 + j * 15, 110 + j * 10, 70, prefer_hill=False)
        if not sp: break
        lo = (tier - 1) * 10 + 1 + min(6, int(road_d[sp[1], sp[0]] / 16)); pois.append({'kind': 'hunt', 'name': '%sの狩り場' % name, 'x': sp[0], 'z': sp[1], 'region': key, 'lv': [lo, min((tier - 1) * 10 + 10, lo + 3)]})
    sig = [f for f in FEAT if f['region'] == key and f['kind'] in ('pool', 'pinnacle', 'dome', 'spine', 'sinkhole', 'forthill', 'arch', 'lava', 'ice', 'sunken_tower')]
    if sig:                                                                                 # ボスは大きな地形のふもと
        f = sig[0]; bx, bz = f['x'], f['z']
        cand = [(int(bx + math.cos(a) * d), int(bz + math.sin(a) * d)) for d in (35, 45, 55, 65) for a in np.linspace(0, 2 * math.pi, 16, endpoint=False)]
        cand = [c for c in cand if 0 <= c[0] < N and 0 <= c[1] < N and ok[c[1], c[0]] and region[c[1], c[0]] == i and not near_pois(c[0], c[1], 30)]
        if cand: pois.append({'kind': 'boss', 'name': '%sのボス' % name, 'x': cand[0][0], 'z': cand[0][1], 'region': key})
    if tier >= 1:                                                                           # 洞窟（ダンジョン）：山の斜面の入口と、別の谷へ抜ける出口 [single-player 11]
        slope_in = (S > 22) & (S < 40) & (region == i) & (road_d > 30) & (mount > 8) & (mount < 40) & ~water & (rim_d > 90) & (border < 45)   # 区域の縁の山すそ（外周の奥山は除く）
        cc = np.argwhere(slope_in)
        for j in range(1 if tier < 3 else 2):
            if len(cc) == 0: break
            c = cc[prg.integers(len(cc))]; ex_ = None
            for c2 in cc[prg.permutation(len(cc))][:300]:
                dd = math.hypot(int(c2[1]) - int(c[1]), int(c2[0]) - int(c[0]))
                if 80 < dd < 160: ex_ = (int(c2[1]), int(c2[0])); break
            pois.append({'kind': 'dungeon', 'name': '%sの洞窟%s' % (name, '一二'[j]), 'x': int(c[1]), 'z': int(c[0]), 'region': key, 'exit': ex_})
    for j, (lo_, hi_) in enumerate(((10, 60), (40, 110), (80, 160))):                       # 集める秘密：入口・中・奥に1つずつ（同じ形） [minecraft 6]
        sp = pick(i, lo_, hi_, 40, prefer_hill=True)
        if sp: pois.append({'kind': 'secret', 'name': SECRET[style], 'x': sp[0], 'z': sp[1], 'region': key})
# 小さな見つけ物を散らす：45〜60マスおき、何も無い所は除く（印は地図に出さない種類）
cand = np.argwhere(ok & ~empty & ~((region == reg('harbor')) & (np.hypot(X - REG[0][4], Z - REG[0][5]) < 55)))   # 町の敷地だけ除く
GRID = {}
for p in pois: GRID.setdefault((p['x'] // 50, p['z'] // 50), []).append((p['x'], p['z']))
def free50(x, z):
    for gx in (x // 50 - 1, x // 50, x // 50 + 1):
        for gz in (z // 50 - 1, z // 50, z // 50 + 1):
            for (a, b) in GRID.get((gx, gz), ()):
                if math.hypot(x - a, z - b) < 50: return False
    return True
nsm = 0
for c in cand[prg.permutation(len(cand))][:60000]:
    z_, x_ = int(c[0]), int(c[1])
    if not free50(x_, z_, 44) if False else not free50(x_, z_): continue
    pois.append({'kind': 'small', 'name': '小さな見つけ物', 'x': x_, 'z': z_, 'region': REG[region[z_, x_]][0]})
    GRID.setdefault((x_ // 50, z_ // 50), []).append((x_, z_)); nsm += 1

# ================================================================ 9. 検査：歩けるか
hr = np.round(h); gy = np.abs(np.diff(hr, axis=1)); gzz = np.abs(np.diff(hr, axis=0))
step = np.maximum(np.pad(gy, ((0, 0), (0, 1))), np.pad(gzz, ((0, 1), (0, 0))))
walk_ok = (step[play & ~water] <= 1.0).mean()
road_ok = (step[on_road] <= 1.0).mean()
print('遊ぶ所の面積 %.0f%%、ジャンプなしで歩けるマス %.1f%%、街道 %.1f%%' % (play.mean() * 100, walk_ok * 100, road_ok * 100))

# ================================================================ 9b. 検査：どこからでも150マス以内に行きたい物が見えるか、街道を歩いて何秒ごとに新しい物が見えるか
#   [theory 2・3、single-player 1（30秒の決まり）、minecraft 12]
WANT = [(p['x'], p['z'], 10 if p['kind'] == 'landmark' else 5) for p in pois if p['kind'] in ('landmark', 'place', 'cave', 'boss', 'dungeon', 'town')]
WANT += [(f['x'], f['z'], f.get('h', 10)) for f in FEAT if f['kind'] in ('vantage', 'pinnacle', 'seastack', 'dome', 'forthill')]
WX = np.array([w[0] for w in WANT], np.float32); WZ = np.array([w[1] for w in WANT], np.float32); WH = np.array([w[2] for w in WANT], np.float32)
def visible_from(x, z, rmax=150):
    d = np.hypot(WX - x, WZ - z); idx = np.where((d < rmax) & (d > 4))[0]; seen = []
    ey = h[int(z), int(x)] + 1.6
    for k in idx:
        tx, tz = WX[k], WZ[k]; ty = h[int(tz), int(tx)] + WH[k]
        n = int(d[k] / 3) + 2; ts = np.linspace(0.05, 0.95, n)
        xs = np.clip((x + (tx - x) * ts).astype(int), 0, N - 1); zs = np.clip((z + (tz - z) * ts).astype(int), 0, N - 1)
        if np.all(h[zs, xs] < ey + (ty - ey) * ts): seen.append(int(k))
    return seen
pts = np.argwhere(play & ~water)[::997]
vis_n = [len(visible_from(int(c[1]), int(c[0]))) for c in pts]
vis_ok = float(np.mean([1 <= v for v in vis_n])); vis_many = float(np.mean([v >= 5 for v in vis_n]))
hb = [c for c in np.argwhere(play & ~water & (region == reg('harbor')) & (np.hypot(X - REG[0][4], Z - REG[0][5]) > 55))[::97]]
hb_n = [len(visible_from(int(c[1]), int(c[0]), 100)) for c in hb]
print('港の外（町の敷地の外）：100マス以内に行きたい物が見える %.0f%%、平均 %.1f 個' % (np.mean([v >= 1 for v in hb_n]) * 100, np.mean(hb_n)))
mp = paths['main']; seg_ = np.hypot(np.diff(mp[:, 0]), np.diff(mp[:, 1])); cum = np.concatenate([[0], np.cumsum(seg_)])
seen_all = set(); last = 0.0; gaps = []
for dist in np.arange(0, cum[-1], 8.0):
    x = float(np.interp(dist, cum, mp[:, 0])); z = float(np.interp(dist, cum, mp[:, 1]))
    new_ = set(visible_from(x, z)) - seen_all
    if new_: gaps.append((dist - last) / 4.3); last = dist; seen_all |= new_
reveal = (float(np.mean(gaps)) if gaps else 0, float(np.max(gaps)) if gaps else 0)
print('見える物：150マス以内に1つ以上 %.0f%%（5つ以上 %.0f%%）、街道で新しい物が見える間隔 平均 %.0f 秒・最長 %.0f 秒' % (vis_ok * 100, vis_many * 100, reveal[0], reveal[1]))

# ================================================================ 10. 書き出しと、上から見た地図
np.save(os.path.join(OUT, 'height.npy'), h.astype(np.float32)); np.save(os.path.join(OUT, 'ground.npy'), ground.astype(np.uint8))
np.save(os.path.join(OUT, 'region.npy'), region.astype(np.uint8)); np.save(os.path.join(OUT, 'water.npy'), water); np.save(os.path.join(OUT, 'bridge.npy'), bridge); np.save(os.path.join(OUT, 'trail.npy'), on_trail)
json.dump({'size': N, 'sea': SEA, 'regions': [{'key': r[0], 'name': r[1], 'style': r[2], 'tier': r[3], 'x': r[4], 'z': r[5], 'zone_id': r[7]} for r in REG],
           'roads': {k: [[float(a), float(b)] for a, b in v] for k, v in ROADS.items()}, 'pois': pois,
           'checks': {'walkable': float(walk_ok), 'road_walkable': float(road_ok), 'play_area': float(play.mean()), 'visible_any': vis_ok, 'reveal_avg_s': reveal[0], 'reveal_max_s': reveal[1]},
           'features': FEAT, 'trails': {k: [[float(a), float(b)] for a, b in v] for k, v in TRAILS.items()}},
          open(os.path.join(OUT, 'pois.json'), 'w'), ensure_ascii=False, indent=1)

COL = {0: (64, 118, 196), 1: (219, 207, 158), 2: (112, 162, 72), 3: (150, 168, 92), 4: (96, 120, 70), 5: (64, 112, 52),
       6: (132, 172, 80), 7: (150, 158, 96), 8: (176, 140, 104), 9: (236, 240, 244), 10: (128, 128, 128), 11: (58, 54, 58),
       12: (186, 160, 112), 13: (150, 144, 136), 14: (92, 86, 84), 15: (170, 140, 96), 16: (170, 200, 230), 17: (230, 110, 30)}
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
img0 = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
img = Image.new('RGB', (N + 270, N), (246, 243, 234)); img.paste(img0, (0, 0))
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
for k, p in tpaths.items():                                                               # 小道は細い線
    pts = [tuple(q) for q in p[::2]]
    dr.line(pts, fill=(90, 66, 40), width=3, joint='curve')
ICON = {'town': ((250, 220, 90), 9, 'o'), 'waypoint': ((90, 220, 250), 6, 'd'), 'landmark': ((255, 255, 255), 8, '^'),
        'place': ((255, 190, 90), 5, 'o'), 'cave': ((120, 90, 60), 5, 'o'), 'hunt': ((230, 70, 60), 6, 'o'), 'small': ((255, 255, 255), 1, 'o'),
        'boss': ((170, 40, 200), 7, 's'), 'dungeon': ((40, 40, 40), 6, 's'), 'secret': ((90, 250, 220), 4, 'd')}
for p in pois:
    c, r, sh = ICON[p['kind']]; x, z = p['x'], p['z']
    if sh == 'o': dr.ellipse([x - r, z - r, x + r, z + r], fill=c, outline=(20, 20, 20), width=1 if r < 3 else 2)
    elif sh == 's': dr.rectangle([x - r, z - r, x + r, z + r], fill=c, outline=(240, 240, 240), width=2)
    elif sh == 'd': dr.polygon([(x, z - r - 2), (x + r, z), (x, z + r + 2), (x - r, z)], fill=c, outline=(20, 20, 20))
    else: dr.polygon([(x, z - r - 3), (x + r, z + r), (x - r, z + r)], fill=c, outline=(20, 20, 20))
    if p['kind'] in ('place', 'cave'): dr.text((x, z - 11), p['name'], font=f3, fill=(255, 245, 225), anchor='mm', stroke_width=2, stroke_fill=(30, 30, 30))
    if p['kind'] == 'landmark': dr.text((x, z + 16), p['name'], font=f3, fill=(255, 255, 255), anchor='mm', stroke_width=2, stroke_fill=(20, 20, 60))
    if p['kind'] == 'hunt': dr.text((x, z + 13), 'Lv%d-%d' % tuple(p['lv']), font=f3, fill=(255, 220, 220), anchor='mm', stroke_width=2, stroke_fill=(40, 20, 20))
    if p['kind'] == 'dungeon' and p.get('exit'): dr.line([(x, z), p['exit']], fill=(40, 40, 40), width=1); dr.rectangle([p['exit'][0] - 3, p['exit'][1] - 3, p['exit'][0] + 3, p['exit'][1] + 3], outline=(40, 40, 40))
# 凡例
lg = [('town', '町（開拓港）'), ('waypoint', '近道の地点（区域に1つ）'), ('landmark', '区域だけの大きな地形'), ('place', '名所（見つけると発見）'), ('cave', '小さな洞窟'),
      ('hunt', '狩り場（敵の Lv）'), ('boss', 'ボス'), ('dungeon', '洞窟のダンジョン（線は出口）'), ('secret', '集める秘密（区域に3つ）'), ('small', '小さな見つけ物（地図に出さない）')]
LX, LZ = N + 14, 20
for j, (kd, tx) in enumerate(lg):
    c, r, sh = ICON[kd]; x, z = LX + 12, LZ + 16 + 24 * j; r = max(r, 3)
    if sh == 'o': dr.ellipse([x - r, z - r, x + r, z + r], fill=c, outline=(20, 20, 20), width=1)
    elif sh == 's': dr.rectangle([x - 6, z - 6, x + 6, z + 6], fill=c, outline=(60, 60, 60), width=2)
    elif sh == 'd': dr.polygon([(x, z - 7), (x + 5, z), (x, z + 7), (x - 5, z)], fill=c, outline=(20, 20, 20))
    else: dr.polygon([(x, z - 8), (x + 7, z + 6), (x - 7, z + 6)], fill=c, outline=(20, 20, 20))
    dr.text((x + 16, z), tx, font=f3, fill=(30, 30, 30), anchor='lm')
img.save(os.path.join(OUT, 'map.png'))
print('書き出し', OUT, round(time.time() - t0, 1), 's', '点', len(pois))
