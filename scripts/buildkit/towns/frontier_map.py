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
np.save(os.path.join(OUT, 'region.npy'), region.astype(np.uint8)); np.save(os.path.join(OUT, 'water.npy'), water); np.save(os.path.join(OUT, 'bridge.npy'), bridge)
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
