"""オープンワールドの野外の地形（rules/openworld.md）。高さの地図を numpy で作り、1 マスずつの段々にしてブロックを置く。

前の kit/terraces.py（ノイズの縁の台地・縦縞の壁・細かいまだら）は使わない。ここの決まり：
  平らな草地を作らない（なめらかな高さを丸めて、長い等高線の段々にする。段の側面は草ブロックの土が見える）[1・2]
  丘は 45〜60 度まで。急な面だけ岩で、材料は斜めの地層の帯ごと [6・9・10]
  高い崖は柱の出入り（3 マスの升目で高さをずらす）と横の色の帯、上の縁に土の帯、裾は石を草へ溶かす [8]
  道は幅 5〜7、周りより 1 段低い溝、土 6 割＋石の島＋草の食い込み、縁は切れ切れ [17〜19]
  川は線を先に描いて掘り、底を平らに止め、岸に砂と泥 [29]

  L = Land(w, d, seed); L.hill(...); L.cliff(...); L.river(...); L.road(...); L.build(); L.dress()
座標は x = 0..w-1（西→東）、z = 0..d-1（北→南）。置くときは ox, oz を引いて原点を真ん中に寄せる。
"""
import math, random
import numpy as np
from vox import put, get, full, log, slab, stair, cross, grass, water, path

GRASS, ROCK, ROAD, SAND, BED, MUD, PAVE, TALUS, DIRT = range(9)


def noise(w, d, scale, seed):
    """なめらかな値のノイズ（0〜1）。scale マスごとの格子を滑らかにつなぐ。"""
    r = np.random.default_rng(seed)
    gw, gd = int(w / scale) + 3, int(d / scale) + 3
    g = r.random((gd, gw))
    x = np.arange(w) / scale; z = np.arange(d) / scale
    xi = x.astype(int); zi = z.astype(int); fx = x - xi; fz = z - zi
    fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz)
    a = g[zi][:, xi]; b = g[zi][:, xi + 1]; c = g[zi + 1][:, xi]; e = g[zi + 1][:, xi + 1]
    return (a * (1 - fx) + b * fx) * (1 - fz)[:, None] + (c * (1 - fx) + e * fx) * fz[:, None]


def spline(pts, step=1.0):
    """Catmull-Rom で点をなめらかにつなぎ、ほぼ step マスおきの点の列にする。"""
    P = [pts[0]] + list(pts) + [pts[-1]]; out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = [np.array(p, float) for p in P[i - 1:i + 3]]
        n = max(2, int(np.linalg.norm(p2 - p1) / step))
        for k in range(n):
            t = k / n
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(np.array(pts[-1], float)); return np.array(out)


class Land:
    def __init__(self, w, d, seed=1, base=10.0, sea=6):
        self.w, self.d, self.seed, self.sea = w, d, seed, sea
        self.X, self.Z = np.meshgrid(np.arange(w, dtype=float), np.arange(d, dtype=float))
        # 小さなうねり：平らな所にも 1 段の筋が 20〜40 マスごとに出る [1]
        self.H = base + (noise(w, d, 46, seed) - 0.5) * 5.0 + (noise(w, d, 19, seed + 1) - 0.5) * 2.2
        self.kind = np.zeros((d, w), int)
        self.steep = np.zeros((d, w))                       # 崖の面（柱の出入りをかける強さ）
        self.road_d = np.full((d, w), 99.0); self.river_d = np.full((d, w), 99.0)
        self.keep = np.zeros((d, w), bool)                  # 木や草を置かない所
        self.roads = []; self.ox, self.oz = w // 2, d // 2
        self.wx = (noise(w, d, 30, seed + 7) - 0.5) * 14; self.wz = (noise(w, d, 30, seed + 8) - 0.5) * 14   # 形をゆがめる（丸を崩す）

    # ---------------------------------------------------------------- 形
    def _dist(self, cx, cz, rx, rz, rot):
        dx = self.X + self.wx - cx; dz = self.Z + self.wz - cz; c, s = math.cos(rot), math.sin(rot)
        return np.hypot((dx * c + dz * s) / rx, (-dx * s + dz * c) / rz)

    def hill(self, cx, cz, r, h, rz=None, rot=0.0, sharp=2.0):
        """丘：なめらかな山。sharp が大きいほど頂が平たく裾が急。傾きは h / r で決まる（45 度なら h ≈ r × 0.8）。"""
        dd = self._dist(cx, cz, r, rz or r, rot)
        self.H += h * np.exp(-dd ** sharp * 1.6)

    def mesa(self, cx, cz, r, h, rz=None, rot=0.0, edge=5.0, dome=2.5):
        """台地と崖：上がゆるい丸屋根、縁が edge マスで落ちる。縁は柱の出入りをかける。"""
        dd = self._dist(cx, cz, r, rz or r, rot)
        rr = min(r, rz or r)
        t = np.clip((1 - dd) * rr / edge, 0, 1); t = t * t * (3 - 2 * t)
        self.H += t * (h + dome * np.clip(1 - dd, 0, 1))
        self.steep = np.maximum(self.steep, np.where((t > 0.04) & (t < 0.96), 1.0, 0.0))

    def ridge(self, pts, h, r, sharp=1.6):
        """尾根の線：点の列からの距離で盛る。"""
        dd = np.full_like(self.H, 1e9)
        for p in spline(pts, 3.0): dd = np.minimum(dd, np.hypot(self.X + self.wx - p[0], self.Z + self.wz - p[1]))
        self.H += h * np.exp(-(dd / r) ** sharp * 1.6)

    def flat(self, cx, cz, r, y=None, soft=6.0):
        """場所を均す（遺跡・野営地・門前）。y を省くと今の高さ。"""
        dd = np.hypot(self.X - cx, self.Z - cz); y = self.at(cx, cz) if y is None else y
        t = np.clip((r + soft - dd) / soft, 0, 1); t = t * t * (3 - 2 * t)
        self.H = self.H * (1 - t) + y * t; return y

    def at(self, x, z): return float(self.H[int(np.clip(z, 0, self.d - 1)), int(np.clip(x, 0, self.w - 1))])
    def top(self, x, z): return int(self.Hi[int(np.clip(z, 0, self.d - 1)), int(np.clip(x, 0, self.w - 1))])

    # ---------------------------------------------------------------- 川と道
    def river(self, pts, width=10, depth=4, level=None):
        """川：線からの距離で掘り、底を平らに止める。岸は砂 → 泥 → 草 [29]。"""
        level = self.sea if level is None else level
        line = spline(pts, 1.0); dd = np.full_like(self.H, 1e9)
        for p in line[::2]: dd = np.minimum(dd, np.hypot(self.X - p[0], self.Z - p[1]))
        dd += (noise(self.w, self.d, 9, self.seed + 31) - 0.5) * 3.0
        hw = width / 2.0; bank = 9.0
        t = np.clip((dd - hw) / bank, 0, 1); t = t * t * (3 - 2 * t)
        bed = level - depth + np.clip(dd / hw, 0, 1) ** 2 * (depth - 0.6)
        self.H = np.where(dd < hw + bank, np.minimum(self.H, bed * (1 - t) + np.maximum(self.H, level + 1.2) * t), self.H)
        self.river_d = np.minimum(self.river_d, dd - hw); self.level = level
        return line

    def road(self, pts, width=6, sink=1.0, grade=0.2, kind='main'):
        """道：線に沿って高さをならし（勾配 grade まで）、周りより sink だけ下げた溝にする [19]。"""
        line = spline(pts, 1.0)
        hs = np.array([self.at(p[0], p[1]) for p in line])
        k = 9; hs = np.convolve(np.pad(hs, k, mode='edge'), np.ones(2 * k + 1) / (2 * k + 1), mode='valid')
        for _ in range(3):                                                           # 勾配を均す
            for i in range(1, len(hs)): hs[i] = min(max(hs[i], hs[i - 1] - grade), hs[i - 1] + grade)
            for i in range(len(hs) - 2, -1, -1): hs[i] = min(max(hs[i], hs[i + 1] - grade), hs[i + 1] + grade)
        hs = hs - sink
        hw = width / 2.0; rad = int(hw + 7)
        dd = np.full_like(self.H, 1e9); hh = np.zeros_like(self.H); ss = np.zeros_like(self.H)
        for i, p in enumerate(line):
            x0, x1 = max(0, int(p[0]) - rad), min(self.w, int(p[0]) + rad + 1); z0, z1 = max(0, int(p[1]) - rad), min(self.d, int(p[1]) + rad + 1)
            if x0 >= x1 or z0 >= z1: continue
            dl = np.hypot(self.X[z0:z1, x0:x1] - p[0], self.Z[z0:z1, x0:x1] - p[1]); m = dl < dd[z0:z1, x0:x1]
            dd[z0:z1, x0:x1][m] = dl[m]; hh[z0:z1, x0:x1][m] = hs[i]; ss[z0:z1, x0:x1][m] = i
        wob = (noise(self.w, self.d, 7, self.seed + 41) - 0.5) * 1.6               # 幅をゆらす（縁をまっすぐにしない）
        de = dd + wob
        inside = de <= hw
        t = np.clip((de - hw) / 2.2, 0, 1)                                         # 縁は 2 マスで周りへ（1 段の土手が残る）
        near = de < hw + 2.2
        bank = hh + sink + 0.3                                                     # 土手は道より 1 段だけ高い
        self.H = np.where(inside, hh, np.where(near, np.clip(self.H, hh - 0.2, bank), self.H))
        cut = (de >= hw + 2.2) & (de < hw + 16)                                    # その外は 14 マスかけて元の地面へ戻す（深い切り通しにしない）
        t2 = np.clip((de - hw - 2.2) / 13.8, 0, 1) ** 1.3
        self.H = np.where(cut & (self.H > bank), bank + (self.H - bank) * t2, self.H)
        self.H = np.where(cut & (self.H < hh - 0.2), hh - 0.2 + (self.H - hh + 0.2) * np.clip((de - hw - 2.2) / 5.0, 0, 1), self.H)
        self.road_d = np.minimum(self.road_d, de - hw)
        self.roads.append(dict(line=line, hs=hs, w=width, kind=kind, s=ss, d=de - hw))
        return line

    # ---------------------------------------------------------------- 組む
    def finish(self):
        """高さを決める：崖の面に柱の出入り（3 マスの升目ごとに高さをずらす）をかけ、丸める。"""
        w, d = self.w, self.d
        cell = np.random.default_rng(self.seed + 5).random((d // 3 + 2, w // 3 + 2))
        pil = cell[(self.Z // 3).astype(int), (self.X // 3).astype(int)]
        gz, gx = np.gradient(self.H); self.slope = np.hypot(gx, gz)
        st = np.clip(self.steep * np.clip((self.slope - 0.7) / 0.8, 0, 1), 0, 1)
        st = np.where(self.road_d < 2, 0, st)
        self.H = self.H + st * (pil - 0.5) * 6.0
        self.H += (noise(w, d, 5, self.seed + 3) - 0.5) * 0.5                       # 等高線を 3〜6 マスごとに 1 マス出入り
        self.H = np.where(self.road_d <= 0, np.round(self.H), self.H)
        self.Hi = np.round(self.H).astype(int)
        gz, gx = np.gradient(self.H); self.slope = np.hypot(gx, gz)
        k = self.kind
        Hp = np.pad(self.Hi, 1, mode='edge')
        self.low = np.minimum.reduce([Hp[:-2, 1:-1], Hp[2:, 1:-1], Hp[1:-1, :-2], Hp[1:-1, 2:]])
        drop = self.Hi - self.low
        rk = (noise(w, d, 6, self.seed + 9) - 0.5) * 0.5
        k[(self.slope + rk > 1.15) | (drop >= 3)] = ROCK                            # 急な面は岩 [6]
        k[(self.river_d < 2.5) & (self.Hi <= self.level + 1)] = SAND
        k[(self.river_d < 5.5) & (self.river_d >= 2.5) & (self.Hi <= self.level + 2) & (noise(w, d, 5, self.seed + 13) > 0.45)] = MUD
        k[(self.Hi < self.level)] = BED
        k[self.road_d <= 0] = ROAD
        # 崖の裾：岩の隣 2〜4 マスの草を石と砂利へ溶かす [8]
        rock = k == ROCK; near = rock.copy()
        for _ in range(3):
            n2 = near.copy(); n2[1:] |= near[:-1]; n2[:-1] |= near[1:]; n2[:, 1:] |= near[:, :-1]; n2[:, :-1] |= near[:, 1:]; near = n2
        tal = near & (k == GRASS) & (noise(w, d, 4, self.seed + 17) > 0.52) & (self.steep_near() > 0)
        k[tal] = TALUS
        self.keep |= (k != GRASS)

    def steep_near(self):
        s = self.steep.copy()
        for _ in range(5):
            n2 = s.copy(); n2[1:] = np.maximum(n2[1:], s[:-1]); n2[:-1] = np.maximum(n2[:-1], s[1:]); n2[:, 1:] = np.maximum(n2[:, 1:], s[:, :-1]); n2[:, :-1] = np.maximum(n2[:, :-1], s[:, 1:]); s = n2
        return s

    def build(self, pal):
        """ブロックを置く。pal：'rock'（下から上への帯の材料の列）、'road'、'isle'（道の石の島）、'bed'。"""
        w, d = self.w, self.d; r = random.Random(self.seed)
        n1 = noise(w, d, 14, self.seed + 21); n2 = noise(w, d, 5, self.seed + 22); n3 = noise(w, d, 3, self.seed + 23)
        isle = noise(w, d, 4, self.seed + 24); gin = noise(w, d, 3, self.seed + 25)
        rock = pal['rock']; nb = len(rock); hmin = int(self.Hi.min()); hspan = max(1, int(self.Hi.max()) - hmin)
        def rock_at(x, y, z):
            # 斜めの地層の帯：厚さ 3〜5、傾きは x 方向に 0.35 [10]。帯ごとに 1 種、帯の中に 2 割だけ隣の材料
            b = (y + n1[z, x] * 7 + x * 0.35) / 4.0
            i = int(np.clip((y - hmin) / hspan * nb + (b % 1.0) * 1.3 - 0.4, 0, nb - 1))
            seq = rock[i]; j = int(b) % len(seq)
            if n2[z, x] > 0.78: j = (j + 1) % len(seq)                                 # 帯の中の別材料は 3〜5 マスの塊で（点で散らさない）
            return seq[j]
        self.rock_at = rock_at
        for z in range(d):
            for x in range(w):
                h = int(self.Hi[z, x]); lo = int(min(self.low[z, x] + 1, h)); k = self.kind[z, x]
                X, Zz = x - self.ox, z - self.oz
                if z in (0, d - 1) or x in (0, w - 1): lo = min(lo, h - 2)
                for y in range(lo, h + 1):
                    topc = y == h; dep = h - y
                    if k == ROCK:
                        shelf = self.slope[z, x] < 1.6 and n2[z, x] > 0.42                            # 岩場の上の草の棚 [7・8]
                        b = grass() if (topc and shelf) else (full('dirt') if (shelf and dep == 1) else full(rock_at(x, y, z)))
                    elif k == TALUS: b = full(('stone', 'andesite', 'cobblestone', 'gravel', 'mossy_cobblestone')[int(n3[z, x] * 4.99)]) if topc else full('stone')
                    elif k == ROAD:
                        if not topc: b = full('dirt')
                        else:
                            e = self.road_d[z, x]
                            if e > -1.6 and gin[z, x] > 0.62: b = grass()                             # 草の食い込み [18]
                            elif isle[z, x] > 0.66: b = full(pal['isle'][int(n3[z, x] * len(pal['isle']) * 0.999)])
                            elif n2[z, x] > 0.8: b = full('coarse_dirt')
                            else: b = path()
                    elif k == SAND: b = full('sand' if n2[z, x] > 0.25 else 'gravel') if dep < 2 else full('stone')
                    elif k == MUD: b = full(('coarse_dirt', 'packed_mud', 'rooted_dirt')[int(n3[z, x] * 2.99)]) if topc else full('dirt')
                    elif k == BED: b = full(pal['bed'][int(n3[z, x] * len(pal['bed']) * 0.999)])
                    elif k == PAVE: b = full(pal['pave'][int(n3[z, x] * len(pal['pave']) * 0.999)]) if topc else full('dirt')
                    elif k == DIRT: b = full(('coarse_dirt', 'dirt', 'rooted_dirt')[int(n3[z, x] * 2.99)])
                    else: b = grass() if topc else (full('dirt') if dep <= 3 else full(rock_at(x, y, z)))
                    put(X, y, Zz, b)
                if h < self.level:
                    for y in range(h + 1, self.level + 1): put(X, y, Zz, water())
                if k == ROAD and 0 < x < w - 1 and 0 < z < d - 1:                  # 道の段：1 段上がる所の手前に石のハーフを 1 列（石段）
                    if any(self.kind[z + b_, x + a_] == ROAD and self.Hi[z + b_, x + a_] == h + 1 for a_, b_ in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        put(X, h + 1, Zz, slab(('cobblestone', 'stone', 'andesite', 'stone_bricks')[int(n3[z, x] * 3.99)]))

    # ---------------------------------------------------------------- 仕上げ
    def free(self, x, z, r=0):
        x, z = int(x), int(z)
        if not (r <= x < self.w - r and r <= z < self.d - r): return False
        return not self.keep[z - r:z + r + 1, x - r:x + r + 1].any()

    def claim(self, x, z, r):
        x, z = int(x), int(z); self.keep[max(0, z - r):z + r + 1, max(0, x - r):x + r + 1] = True

    def P(self, x, y, z, b):
        """地図の座標で置く。"""
        put(int(x) - self.ox, int(y), int(z) - self.oz, b)

    def G(self, x, y, z): return get(int(x) - self.ox, int(y), int(z) - self.oz)

    def road_edges(self, seed=3):
        """道の縁：石のハーフ・丸石・丸太の 1 マス塊を 3〜15 マスつなげて 3〜10 マス空ける。土手の上（道の外 1〜2 マス）に置く [19]。"""
        r = random.Random(self.seed + seed)
        for R in self.roads:
            line = R['line']; hw = R['w'] / 2.0
            for side in (-1, 1):
                i = r.randint(0, 8)
                while i < len(line) - 2:
                    run = r.randint(3, 14); kind = r.choice(['slab', 'slab', 'cobble', 'log', 'mix'])
                    for j in range(i, min(i + run, len(line) - 2)):
                        t = line[j + 1] - line[j]; n = np.array([-t[1], t[0]]); n = n / (np.linalg.norm(n) + 1e-9)
                        p = line[j] + n * side * (hw + 1.3 + r.random() * 0.8); x, z = int(round(p[0])), int(round(p[1]))
                        if not (1 <= x < self.w - 1 and 1 <= z < self.d - 1) or self.kind[z, x] not in (GRASS,) or self.road_d[z, x] > 3: continue
                        g = self.top(x, z)
                        if self.G(x, g + 1, z) is not None: continue
                        kk = r.choice(['slab', 'cobble', 'log']) if kind == 'mix' else kind
                        if kk == 'slab': self.P(x, g + 1, z, slab(r.choice(['stone', 'cobblestone', 'andesite', 'stone_bricks'])))
                        elif kk == 'cobble':
                            self.P(x, g, z, full(r.choice(['cobblestone', 'mossy_cobblestone', 'stone', 'andesite'])))
                            if r.random() < 0.45: self.P(x, g + 1, z, slab(r.choice(['cobblestone', 'stone'])))
                        else: self.P(x, g + 1, z, log('spruce_log', 'x' if abs(t[0]) > abs(t[1]) else 'z'))
                        self.keep[z, x] = True
                    i += run + r.randint(3, 10)

    def grasses(self, cover=0.55, seed=4, flowers=()):
        """背の高い草を地面の 4〜7 割に、塊で。花は 1 色を 5〜20 本の群れで [3]。flowers：(x, z, 半径, 種) の列。"""
        r = random.Random(self.seed + seed)
        n = noise(self.w, self.d, 6, self.seed + 51); n2 = noise(self.w, self.d, 2, self.seed + 52)
        for z in range(1, self.d - 1):
            for x in range(1, self.w - 1):
                if self.kind[z, x] != GRASS or self.keep[z, x]: continue
                g = self.top(x, z)
                if self.G(x, g + 1, z) is not None: continue
                near_road = self.road_d[z, x] < 3
                if n[z, x] * 0.6 + n2[z, x] * 0.4 < (cover + (0.12 if near_road else 0)):
                    if r.random() < 0.82: self.P(x, g + 1, z, cross('short_grass' if r.random() < 0.8 else 'fern'))
        for (fx, fz, fr, kind) in flowers:
            for _ in range(int(fr * fr * 1.6)):
                a = r.random() * 6.283; q = fr * math.sqrt(r.random()); x, z = int(fx + math.cos(a) * q), int(fz + math.sin(a) * q)
                if 1 <= x < self.w - 1 and 1 <= z < self.d - 1 and self.kind[z, x] == GRASS:
                    g = self.top(x, z); c = self.G(x, g + 1, z)
                    if c is None or c.get('s') == 'cross': self.P(x, g + 1, z, cross(kind))
