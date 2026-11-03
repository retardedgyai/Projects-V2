"""大きな地形の部品（11/03）。藍「AIで作りきりたいからまた色々学んでみて大きな地形とか」。
式（ノイズ）だけの山は、どこも同じ揺れ方で尾根と谷の筋が無く、雑に見えた。地形を作る人の順番に合わせる：

  1. ridge_field  尾根の線を先に決め、その線から高さを盛る（山脈の背骨）
  2. erode        雨粒を流して削り、下に積もらせる（谷筋・尾根・扇状地ができる）
  3. talus        崩れる角度より急な所を崩す（岩の崩れ、斜面をならす）
  4. 塗りは呼ぶ側（町の ground_top / steep_top）で、傾きと高さで決める

高さは「格子 g[z][x]（小数）」で持つ。原点は (x0, z0)。
"""
import math, random
from vox import vnoise


class Grid:
    def __init__(self, x0, z0, w, d, fill=0.0):
        self.x0, self.z0, self.w, self.d = x0, z0, w, d
        self.h = [fill] * (w * d)

    def i(self, x, z): return (z - self.z0) * self.w + (x - self.x0)
    def get(self, x, z): return self.h[self.i(x, z)]
    def inside(self, x, z): return self.x0 <= x < self.x0 + self.w and self.z0 <= z < self.z0 + self.d

    def sample(self, fx, fz):
        """格子の中の小数の位置の高さ（双線形）と、その傾き。fx, fz は格子の中の座標（0〜w-1）。"""
        w = self.w; h = self.h
        ix, iz = int(fx), int(fz)
        u, v = fx - ix, fz - iz
        k = iz * w + ix
        a, b, c, e = h[k], h[k + 1], h[k + w], h[k + w + 1]
        gx = (b - a) * (1 - v) + (e - c) * v
        gz = (c - a) * (1 - u) + (e - b) * u
        return a * (1 - u) * (1 - v) + b * u * (1 - v) + c * (1 - u) * v + e * u * v, gx, gz


def _seg_dist(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz or 1e-9
    t = max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / L2))
    qx, qz = ax + dx * t, az + dz * t
    return math.hypot(px - qx, pz - qz), t


def ridge_field(G, ridges, noise=lambda x, z: 0.0):
    """尾根の線から山を盛る。ridges は [(点の列 [(x, z, 高さ), ...], 裾の幅), ...]。
    断面は尾根で尖り、裾へ向かって下に凸に下がる（円錐ではなく、尾根の背骨が通る形）。"""
    for z in range(G.z0, G.z0 + G.d):
        for x in range(G.x0, G.x0 + G.w):
            best = G.get(x, z)
            wx, wz = x + 12 * noise(x * 0.5 + 31, z * 0.5), z + 12 * noise(x * 0.5, z * 0.5 + 57)   # 位置を揺らして、尾根の直線の継ぎ目を消す
            for (pts, width) in ridges:
                for (a, b) in zip(pts, pts[1:]):
                    dd, t = _seg_dist(wx, wz, a[0], a[1], b[0], b[1])
                    wdt = width * (0.8 + 0.4 * noise(x * 0.7, z * 0.7))
                    top = a[2] + (b[2] - a[2]) * t
                    if dd > wdt:                                                          # 山の半径の2倍まで広がる緩い裾（前山）
                        if dd < wdt * 2:
                            sk = top * 0.22 * (1 - (dd - wdt) / wdt) ** 2
                            if sk > best: best = sk
                        continue
                    s = 1.0 - dd / wdt
                    hh = top * (0.22 + 0.78 * s ** 1.6) + noise(x, z) * 10 * s + noise(x * 3.1, z * 3.1) * 4 * s   # 斜面に最初のでこぼこ（谷筋の種）
                    if hh > best: best = hh
            G.h[G.i(x, z)] = best


def erode(G, drops=None, seed=5, inertia=0.05, capacity=4.0, min_slope=0.01, deposit=0.3, erode_rate=0.3,
          evaporate=0.01, gravity=4.0, life=30, radius=3, mask=lambda x, z: True):
    """雨粒の侵食（Beyer 2015）。雨粒は傾きを下り、速さと水の量に応じて土を削って運び、緩い所で落とす。
    谷筋が刻まれ、尾根が残り、谷の出口に扇状地ができる。mask(x, z) が偽の所には雨を降らせない（町など）。"""
    rng = random.Random(seed)
    w, d = G.w, G.d
    drops = drops or int(w * d * 1.5)                                                # 雨粒は面積の1.5倍（多いほど削り続ける）
    top = max(G.h) or 1.0
    G.h = [v / top for v in G.h]                                                       # 式は高さ 0〜1 の前提（マス単位のまま流すと削りが百倍になった）
    h = G.h
    if not hasattr(G, 'flow'): G.flow = [0.0] * (w * d)                                # 流れの地図：雨粒が通った回数（筋は岩、上位は川）
    flow = G.flow
    offs = []                                                                         # 削るときの筆（半径 radius の重み）
    for oz in range(-radius, radius + 1):
        for ox in range(-radius, radius + 1):
            r = math.hypot(ox, oz)
            if r <= radius: offs.append((ox, oz, radius - r))
    tot = sum(o[2] for o in offs); offs = [(ox, oz, wt / tot) for (ox, oz, wt) in offs]
    for n in range(drops):
        fx, fz = rng.uniform(1, w - 2), rng.uniform(1, d - 2)
        if not mask(int(fx) + G.x0, int(fz) + G.z0): continue
        dx = dz = 0.0; speed = 1.0; water = 1.0; sed = 0.0
        for step in range(life):
            ix, iz = int(fx), int(fz)
            flow[iz * w + ix] += water
            hh, gx, gz = G.sample(fx, fz)
            dx = dx * inertia - gx * (1 - inertia); dz = dz * inertia - gz * (1 - inertia)
            ln = math.hypot(dx, dz)
            if ln < 1e-9: break
            dx /= ln; dz /= ln
            nx, nz = fx + dx, fz + dz
            if not (1 <= nx < w - 2 and 1 <= nz < d - 2): break
            nh = G.sample(nx, nz)[0]
            dh = nh - hh
            cap = max(-dh, min_slope) * speed * water * capacity
            u, v = fx - ix, fz - iz
            k = iz * w + ix
            if sed > cap or dh > 0:                                                  # 落とす（上り坂なら穴を埋める分だけ）
                amt = min(dh, sed) if dh > 0 else (sed - cap) * deposit
                sed -= amt
                h[k] += amt * (1 - u) * (1 - v); h[k + 1] += amt * u * (1 - v)
                h[k + w] += amt * (1 - u) * v; h[k + w + 1] += amt * u * v
            else:                                                                    # 削る（筆で周りから少しずつ）
                amt = min((cap - sed) * erode_rate, -dh)
                for (ox, oz, wt) in offs:
                    X, Z = ix + ox, iz + oz
                    if 0 <= X < w and 0 <= Z < d:
                        kk = Z * w + X
                        take = min(amt * wt, h[kk])
                        h[kk] -= take; sed += take
            speed = math.sqrt(max(0.0, speed * speed - dh * gravity))
            water *= 1 - evaporate
            fx, fz = nx, nz
    G.h = [v * top for v in G.h]


def erode2(G, seed=5, mask=lambda x, z: True):
    """侵食を2回：1回目は柔らかい岩で長く（大きな谷筋）、2回目は硬い岩で弱く（細かい筋だけ）。 [smarf 2:35・4:50]"""
    erode(G, seed=seed, erode_rate=0.3, capacity=4.0, mask=mask)
    erode(G, drops=int(G.w * G.d * 0.6), seed=seed + 1, erode_rate=0.1, capacity=2.0, radius=2, mask=mask)


def smooth_gentle(G, max_slope=0.58, strength=0.5, rounds=2):
    """緩い面（傾き30度未満＝1マスあたり0.58未満）だけ均す。雪や草の面が長い1マスの段の等高線になる。
    急な所（岩）は均さない。強さは5割（10割だと作り物っぽい）。 [smarf 11:30・Cerulean 12:10]"""
    w, d = G.w, G.d
    for _ in range(rounds):
        h = G.h; out = list(h)
        for z in range(1, d - 1):
            for x in range(1, w - 1):
                k = z * w + x
                nb = (h[k + 1], h[k - 1], h[k + w], h[k - w])
                if max(abs(h[k] - v) for v in nb) >= max_slope * 2: continue
                avg = (sum(nb) + h[k + w + 1] + h[k + w - 1] + h[k - w + 1] + h[k - w - 1] + h[k]) / 9.0
                out[k] = h[k] + (avg - h[k]) * strength
        G.h = out


def slope_deg(G, x, z):
    """傾き（度）。2マス先との差で見る（1マスの段の凸凹を拾わない）。"""
    if not (G.inside(x - 2, z - 2) and G.inside(x + 2, z + 2)): return 0.0
    gx = (G.get(x + 2, z) - G.get(x - 2, z)) / 4.0; gz = (G.get(x, z + 2) - G.get(x, z - 2)) / 4.0   # 2マス先で見る（1マスの段々だと 0度か45度しか出ない）
    return math.degrees(math.atan(math.hypot(gx, gz)))


def talus(G, angle=1.6, rounds=6, rate=0.5):
    """崩れる角度（1マスあたりの高さの差）より急な所を、低い隣へ崩す。岩が崩れて斜面がならされる。"""
    w, d, h = G.w, G.d, G.h
    for _ in range(rounds):
        for z in range(1, d - 1):
            for x in range(1, w - 1):
                k = z * w + x
                for kk in (k + 1, k - 1, k + w, k - w):
                    diff = h[k] - h[kk]
                    if diff > angle:
                        mv = (diff - angle) * rate * 0.5
                        h[k] -= mv; h[kk] += mv


def slope_at(G, x, z):
    """隣4マスとの高さの差の最大（塗り分けに使う）。"""
    c = G.get(x, z)
    return max(abs(c - G.get(x + dx, z + dz)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if G.inside(x + dx, z + dz))


LIGHT = (-0.45, 0.75, -0.48)                                                           # 光の向き（北西の上から）

def paint(G, x, z, snow_h=62):
    """上面のブロックの名前。
    ・45度より緩い面は草か雪（雪の線より上。線は場所で揺らす）。雪の線の少し下は砂利と粗い土。
    ・急な面は岩。光の向きの明るさで材料を選ぶ：明るい面は方解石・閃緑岩を少し、中は石と安山岩、暗い面は凝灰岩と深層岩。
      暗い側を多く、低いほど暗く、溝（流れの筋）は暗く。
    ・雪の筋：峰の頭から溝に沿って雪が流れ下る（急な面でも、雪の線の近くの流れの筋は雪）。
    [Calven 16:00・17:30・33:20・34:30、Shoeler 37:00〜41:00、Lord Dakr 18:30]"""
    h = G.get(x, z); a = slope_deg(G, x, z)
    f = G.flow[G.i(x, z)] if hasattr(G, 'flow') else 0.0
    streak = f > getattr(G, 'flow_cut', 1e9)
    n = ((x * 73856093) ^ (z * 19349663)) % 1000 / 1000.0
    sl = snow_h + 6 * (math.sin(x * 0.11) * math.cos(z * 0.09))
    if a < 45:
        if h >= sl: return 'snow'
        if h >= sl - 6: return 'gravel' if n < 0.3 else 'coarse_dirt' if n < 0.5 else 'grass'
        return 'grass'
    if streak and h >= sl - 12: return 'snow'
    if h >= sl + 12 and vnoise(x, z, 3, 79) > 0.45: return 'snow'                      # 上のほうは急な所にもまだらに雪（Zeplington 13:21）
    gx = (G.get(x + 1, z) - G.get(x - 1, z)) / 2.0 if G.inside(x + 1, z) and G.inside(x - 1, z) else 0.0
    gz = (G.get(x, z + 1) - G.get(x, z - 1)) / 2.0 if G.inside(x, z + 1) and G.inside(x, z - 1) else 0.0
    ln = math.sqrt(gx * gx + 1 + gz * gz)
    br = (-gx * LIGHT[0] + 1 * LIGHT[1] - gz * LIGHT[2]) / ln
    br -= 0.18 * max(0.0, (snow_h - h) / snow_h) + (0.12 if streak else 0.0)
    m = vnoise(x, z, 5, 77) * 0.6 + vnoise(x, z, 2, 78) * 0.4                        # 材料は塊のノイズで（1マスごとの乱数だとザラついた）
    if br > 0.72: return 'calcite' if m < 0.18 else 'diorite' if m < 0.3 else 'stone'   # 明るい面：主は石、差し色に方解石・閃緑岩
    if br > 0.22: return 'andesite' if m < 0.3 else 'stone'                              # 中：石が主（5〜7割）
    return 'tuff' if m < 0.35 else 'cobbled_deepslate' if m < 0.5 else 'andesite'         # 暗い面・溝


def flow_cut(G, top=0.11):
    """流れの多い順に上位 top の境目（筋と川の候補）。"""
    v = sorted(G.flow, reverse=True)
    G.flow_cut = v[int(len(v) * top)]
    return G.flow_cut
