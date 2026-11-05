"""港町の部品（11/04）。決まりは rules/harbor.md（85〜105）。手本は ~/ProjectS/refs/build/learn8/。

二回目に式から作った茅葺き（干し草のピラミッド）・箱の船・板の旗は捨てて、手本の作り方で組み直した。
どれも (x, z, ...) の座標と、水面の高さ wy（一番上の水のブロック）を取る。見本の場は towns/harbor_parts.py。
"""
import math
from vox import put, get, full, log, stair, slab, trap, fence, cross, door, water, vnoise, _h, LANTERN, CHAIN
import props as P

IN = {'n': 's', 's': 'n', 'e': 'w', 'w': 'e'}


def _ring(x0, z0, x1, z1):
    """長方形の縁のマスと、そのマスが向く外の向き。角は2回出る（両方の向き）。"""
    for x in range(x0, x1 + 1):
        yield x, z0, 'n'; yield x, z1, 's'
    for z in range(z0, z1 + 1):
        yield x0, z, 'w'; yield x1, z, 'e'


# ================================================================ 屋根：茅葺きの寄棟 [95〜98]
def thatch_roof(x0, z0, x1, z1, ye, face='birch', ridge='spruce'):
    """(x0..x1, z0..z1) は壁の外側の長方形、ye は壁の一番上のブロックの高さ。
    垂木（トウヒの逆さ階段）を壁の外1マスに一列、軒は壁から2マス（ハーフ1枚の厚み）。
    面は 1 上がって 1 内へ（45°）。短い辺の残りが 3 マス以下になったら 1 内へ 2 上がる（上ほど急）。
    最後に長い辺に沿って濃い棟（原木をハーフで挟み、両端は上付き階段→ハーフで反らせる）。"""
    S = face + '_planks'
    for (x, z, f) in _ring(x0 - 1, z0 - 1, x1 + 1, z1 + 1):                        # 垂木
        if get(x, ye, z) is None: put(x, ye, z, stair('spruce_planks', IN[f], 'top'))
    for (x, z, f) in _ring(x0 - 2, z0 - 2, x1 + 2, z1 + 2):                        # 軒（ハーフ1枚）
        put(x, ye, z, slab(S, 'top'))
    k, y = -1, ye + 1
    while True:
        a0, b0, a1, b1 = x0 + k, z0 + k, x1 - k, z1 - k
        short = min(a1 - a0, b1 - b0)
        if short < 2: break
        for (x, z, f) in _ring(a0, b0, a1, b1):
            if (x, z) in ((a0, b0), (a1, b0), (a0, b1), (a1, b1)):
                put(x, y, z, stair(S, IN[f]))                                         # 角も階段（外向き）
            else: put(x, y, z, stair(S, IN[f]))
        for x in range(a0 + 1, a1):                                                  # 裏打ち：次の段の下を満たす
            for z in range(b0 + 1, b1):
                put(x, y, z, full(S))
        steep = short <= 3
        if steep:                                                                    # 上ほど急：1 内へ 2 上がる
            for (x, z, f) in _ring(a0 + 1, b0 + 1, a1 - 1, b1 - 1):
                put(x, y + 1, z, full(S))
            y += 2
        else: y += 1
        k += 1
    a0, b0, a1, b1 = x0 + k, z0 + k, x1 - k, z1 - k                                  # 残り（幅1〜2）＝棟
    along_x = (a1 - a0) >= (b1 - b0)
    length = max(a1 - a0, b1 - b0) + 1
    for x in range(a0, a1 + 1):                                                      # 棟：残りを満たし、芯に濃い原木、上にハーフ
        for z in range(b0, b1 + 1): put(x, y, z, full(S))
    if along_x:
        zc = (b0 + b1) // 2
        for x in range(a0, a1 + 1): put(x, y, zc, log(ridge + '_log', 'x')); put(x, y + 1, zc, slab(ridge + '_planks'))
        if length >= 3:                                                              # 長い棟だけ、両端を上付き階段で外へ反らせる
            put(a0 - 1, y, zc, stair(ridge + '_planks', 'e', 'top')); put(a1 + 1, y, zc, stair(ridge + '_planks', 'w', 'top'))
    else:
        xc = (a0 + a1) // 2
        for z in range(b0, b1 + 1): put(xc, y, z, log(ridge + '_log', 'z')); put(xc, y + 1, z, slab(ridge + '_planks'))
        if length >= 3:
            put(xc, y, b0 - 1, stair(ridge + '_planks', 's', 'top')); put(xc, y, b1 + 1, stair(ridge + '_planks', 'n', 'top'))
    return y + 1


# ================================================================ 高床の家 [88・94・96・101]
FRAMES = ['spruce', 'dark_oak', 'oak', 'mangrove', 'stripped_spruce']                  # 枠の色は5つから
INFILL = ['birch_planks', 'calcite', 'mushroom_stem', 'sandstone', 'birch_planks']   # 壁は地味な色
def stilt_house(x0, z0, w, d, wy, front='s', frame='spruce', infill='birch_planks', floors=1, bed=None, roof='birch', seed=0):
    """(x0, z0) は床の北西の角。床は水面+3（wy+3）。杭は外周だけ、3マスおきの原木を水底から。
    床の縁に梁を回して外に逆さ階段。枠（柱と上の梁）と壁の色を変え、壁は枠より1マス内。
    前に2列の縁側（手すりは一番外の列、角は切る）と、水面+1 の台へ下りる階段。"""
    fy = wy + 3
    bed = wy - 3 if bed is None else bed
    x1, z1 = x0 + w - 1, z0 + d - 1
    LOG = log((frame if frame.startswith('stripped') else frame) + ('_log' if not frame.startswith('stripped') else '_log'))
    PL = (frame.replace('stripped_', '') + '_planks')
    px0, pz0, px1, pz1 = x0, z0, x1, z1                                             # 縁側の分の床
    fdx, fdz = P.D[front]
    if front == 's': pz1 += 2
    elif front == 'n': pz0 -= 2
    elif front == 'e': px1 += 2
    else: px0 -= 2
    for x in range(px0, px1 + 1):                                                    # 床
        for z in range(pz0, pz1 + 1):
            put(x, fy, z, full('spruce_planks' if _h(x, z, seed) < 0.7 else 'oak_planks'))
    for (x, z, f) in _ring(px0, pz0, px1, pz1):                                      # 梁と外の逆さ階段
        put(x, fy, z, log('stripped_' + PL.replace('_planks', '_log') if 'mangrove' not in PL else 'stripped_mangrove_log', 'x' if f in 'ns' else 'z'))
        ox, oz = P.D[f]
        if get(x + ox, fy, z + oz) is None: put(x + ox, fy, z + oz, stair('spruce_planks', IN[f], 'top'))
    for (x, z, f) in _ring(px0, pz0, px1, pz1):                                      # 杭：外周だけ3マスおき＋角
        corner = (x in (px0, px1)) and (z in (pz0, pz1))
        if corner or (f in 'ns' and (x - px0) % 3 == 0) or (f in 'ew' and (z - pz0) % 3 == 0):
            for y in range(bed, fy): put(x, y, z, LOG)
    H = 4 * floors
    for x in range(x0, x1 + 1):                                                      # 壁と枠
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not edge: continue
            corner = x in (x0, x1) and z in (z0, z1)
            post = corner or (z in (z0, z1) and (x - x0) % 3 == 0) or (x in (x0, x1) and (z - z0) % 3 == 0)
            for y in range(fy + 1, fy + H + 1):
                if post: put(x, y, z, LOG)
                elif y == fy + H or (floors > 1 and y == fy + 4): put(x, y, z, log(PL.replace('_planks', '_log') if 'stripped' not in frame else 'stripped_spruce_log', 'x' if z in (z0, z1) else 'z'))
                else:
                    ix = x + (1 if x == x0 else -1 if x == x1 else 0); iz = z + (1 if z == z0 else -1 if z == z1 else 0)
                    put(x, y, z, None)
                    put(ix, y, iz, full(infill))                                     # 壁は枠より1マス内
    for x in range(x0 + 1, x1):                                                      # 中の床（壁の内）と2階の床
        for z in range(z0 + 1, z1):
            if floors > 1: put(x, fy + 4, z, full('spruce_planks'))
    # 窓：柱の間の真ん中に縦長のガラス、両脇に開いた鎧戸（トラップドア）
    for (x, z, f) in _ring(x0, z0, x1, z1):
        corner = x in (x0, x1) and z in (z0, z1)
        if corner: continue
        along = (x - x0) if f in 'ns' else (z - z0)
        if along % 3 != 2 and along % 3 != 1: continue
        if along % 3 != 2: continue
        ix = x + (1 if f == 'w' else -1 if f == 'e' else 0); iz = z + (1 if f == 'n' else -1 if f == 's' else 0)
        for fl in range(floors):
            yb = fy + 2 + fl * 4
            if f == front and fl == 0 and along == ((w if f in 'ns' else d) // 2 // 3) * 3 + 2: continue
            for y in (yb, yb + 1):
                put(ix, y, iz, {'s': 'pane', 't': 'glass', 'ax': 'x' if f in 'ns' else 'z'})
    # 扉：前の壁の真ん中（柱を避ける）
    if front in 'ns':
        dx_ = x0 + w // 2; dz_ = (z1 - 1) if front == 's' else (z0 + 1)
        if (dx_ - x0) % 3 == 0: dx_ += 1
        for y in (fy + 1, fy + 2): put(dx_, y, dz_, None)
        put(dx_, fy + 1, dz_, door('bottom', IN[front])); put(dx_, fy + 2, dz_, door('top', IN[front]))
        put(dx_, fy + 1, z1 if front == 's' else z0, None); put(dx_, fy + 2, z1 if front == 's' else z0, None)
    else:
        dz_ = z0 + d // 2; dx_ = (x1 - 1) if front == 'e' else (x0 + 1)
        if (dz_ - z0) % 3 == 0: dz_ += 1
        for y in (fy + 1, fy + 2): put(dx_, y, dz_, None)
        put(dx_, fy + 1, dz_, door('bottom', IN[front])); put(dx_, fy + 2, dz_, door('top', IN[front]))
        put(x1 if front == 'e' else x0, fy + 1, dz_, None); put(x1 if front == 'e' else x0, fy + 2, dz_, None)
    # 縁側の手すり：一番外の列に柵、角は切る、真ん中は階段の口
    for (x, z, f) in _ring(px0, pz0, px1, pz1):
        if f != front: continue
        corner = (x in (px0, px1)) and (z in (pz0, pz1))
        mid = (abs(x - (px0 + px1) / 2) < 1.5) if f in 'ns' else (abs(z - (pz0 + pz1) / 2) < 1.5)
        if corner or mid: continue
        put(x, fy + 1, z, fence('spruce_planks'))
    # 水面+1 の小さな台へ下りる階段（前の真ん中から外へ）
    cx = (px0 + px1) // 2; cz = (pz0 + pz1) // 2
    sx, sz = (cx, pz1 + 1) if front == 's' else (cx, pz0 - 1) if front == 'n' else (px1 + 1, cz) if front == 'e' else (px0 - 1, cz)
    for i in range(2):
        for s in (-1, 0, 1):
            ax_, az_ = (sx + s, sz + fdz * i) if front in 'ns' else (sx + fdx * i, sz + s)
            put(ax_, fy - 1 - i, az_, stair('spruce_planks', IN[front]))
            for y in range(bed, fy - 1 - i): put(ax_, y, az_, log('spruce_log')) if s != 0 and i == 1 else None
    top = thatch_roof(x0, z0, x1, z1, fy + H, roof)
    put((x0 + x1) // 2, fy + H - 1, z0 + 1, P.HANG) if d > 4 else None
    return fy


# ================================================================ 杭の灯り [88]
def pile_post(x, z, bed, top):
    """水底から top+1 まで原木、頭に階段のつば（四方）は載せず、原木1つ＋ランタン。"""
    for y in range(bed, top + 2): put(x, y, z, log('spruce_log'))
    put(x, top + 2, z, LANTERN)


# ================================================================ 護岸：丸い広場の縁 [85・86]
def round_quay(cx, cz, r, top, wy, bed, cargo_every=4):
    """半径 r の円の広場（床 top）の縁を、石レンガの壁で水底から立てる。
    上端は水側へ1マス張り出す逆さ階段、水面の高さに砂岩の帯。4マスおきに控え壁、間の窪みに荷物。"""
    cells = []
    for i in range(-r - 3, r + 4):
        for j in range(-r - 3, r + 4):
            d = math.hypot(i, j)
            x, z = cx + i, cz + j
            if d <= r + 0.3:
                for y in range(bed, top): put(x, y, z, full('stone_bricks' if _h(x, y, z) < 0.75 else 'cracked_stone_bricks'))
                cells.append((x, z))
    edge = [(x, z) for (x, z) in cells if any(math.hypot(x + dx - cx, z + dz - cz) > r + 0.3 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    edge.sort(key=lambda c: math.atan2(c[1] - cz, c[0] - cx))
    for n, (x, z) in enumerate(edge):
        ang = math.atan2(z - cz, x - cx)
        f = 'e' if abs(math.cos(ang)) > abs(math.sin(ang)) and math.cos(ang) > 0 else 'w' if abs(math.cos(ang)) > abs(math.sin(ang)) else 's' if math.sin(ang) > 0 else 'n'
        ox, oz = P.D[f]
        if get(x + ox, top - 1, z + oz) is None: put(x + ox, top - 1, z + oz, stair('stone_bricks', IN[f], 'top'))   # 張り出し
        if get(x + ox, wy, z + oz) is None: put(x + ox, wy, z + oz, slab('sandstone', 'top'))                         # 水面の帯
        if n % cargo_every == 0:                                                     # 控え壁
            for y in range(bed, top - 1): put(x + ox, y, z + oz, full('stone_bricks'))
            put(x + ox, top, z + oz, slab('stone_bricks'))
        put(x, top, z, full('stone_bricks')) if n % 2 else put(x, top, z, full('chiseled_stone_bricks')) if n % cargo_every == 0 else put(x, top, z, full('stone_bricks'))
    return edge


# ================================================================ クレーン [90・91]
def crane(x, z, g, f='e', reach=5, h=8):
    """暗い木の柱（2本）と腕、腕の先から鎖で荷を吊る。"""
    ox, oz = P.D[f]
    rx, rz = P.D[P.RIGHT[f]]
    for s in (0, 1):
        for y in range(g + 1, g + h + 1): put(x + rx * s, y, z + rz * s, log('dark_oak_log'))
    for i in range(-1, reach + 1):
        for s in (0, 1):
            put(x + ox * i + rx * s, g + h + 1, z + oz * i + rz * s, log('dark_oak_log', 'x' if f in 'ew' else 'z'))
    for i in (1, 2):                                                                 # 筋交い（逆さ階段）
        put(x + ox * i, g + h + 1 - i, z + oz * i, stair('dark_oak_planks', IN[f], 'top'))
    ex, ez = x + ox * reach, z + oz * reach
    for y in range(g + h - 2, g + h + 1): put(ex, y, ez, CHAIN)
    put(ex, g + h - 3, ez, P.BARREL)


# ================================================================ 帆船 [99〜104]
HALF = [0, 2, 3, 5, 6, 7, 7, 7]                                                     # 中央断面の片幅（下の層から）
def ship(x, z0, wy, n=40):
    """x は船の芯、z0 は船首の先（北）、船尾は z0+n-1。キールは wy-2（3層目の上が水面）、甲板は wy+5。
    船べりの線：真ん中は水平、船首は 45° で +5、船尾は楼で +3（窓と手すり）。
    船体は層ごとに左右対称。前の 1/4 で細く尖り、後ろは短く丸めて平らに。枠（暗い）と壁（明るい）を変える。"""
    kl = wy - 2
    dk = kl + 7
    def scale(t, i):
        lowp = 1.0 + (7 - i) * 0.18                                                  # 下の層ほど早く細くなる
        if t < 0.28: s = (t / 0.28) ** (0.55 * lowp)
        elif t > 0.86: s = 1.0 - ((t - 0.86) / 0.14) ** 2 * 0.35
        else: s = 1.0
        return s
    shell = {}
    for k in range(n):
        t = k / (n - 1); z = z0 + k
        bowrise = max(0, 5 - int(k * 1.0)) if k < 6 else 0
        for i in range(8):
            hw = HALF[i] * scale(t, i)
            if i <= 2 and t > 0.9: hw *= 0.8
            w = int(round(hw))
            if HALF[i] > 0 and w == 0 and t > 0.03: w = 0
            for xx in range(-w, w + 1): shell[(x + xx, kl + i, z)] = 'in' if abs(xx) < w else 'edge'
        for b in range(1, bowrise + 1):                                              # 船首の反り：最上層を上へ伸ばす
            w = int(round(HALF[7] * scale(t, 7)))
            for xx in (-w, w): shell[(x + xx, dk + b, z)] = 'edge'
    for (xx, y, z), kind in shell.items():
        i = y - kl
        if kind == 'in' and i < 7:
            put(xx, y, z, full('dark_oak_planks'))
            continue
        if i >= 7:                                                                   # 甲板の高さ以上：甲板と手すり
            if kind == 'in': put(xx, y, z, full('spruce_planks'))
            else:
                put(xx, y, z, full('dark_oak_planks')); put(xx, y + 1, z, fence('dark_oak_planks'))
            continue
        frame = (z - z0) % 3 == 0 or i in (2, 6)                                     # 枠：3マスおきの肋材と、水線・上の帯
        put(xx, y, z, full('dark_oak_planks' if frame else 'spruce_planks' if i < 3 else 'oak_planks'))
    # 甲板の下の空気を抜いて、甲板を張る（中は見えないが、船べりの手すりを甲板の上に立てるため）
    for k in range(n):
        z = z0 + k; t = k / (n - 1)
        w = int(round(HALF[7] * scale(t, 7)))
        for xx in range(-w + 1, w):
            put(x + xx, dk, z, full('spruce_planks' if (xx + k) % 4 else 'stripped_spruce_log'))
        if 0.6 < t < 0.75 and k % 2 == 0:                                            # 舷側の窓（砲門）列：層5 に3マスおき
            for xx in (-w, w): put(x + xx, kl + 5, z, full('dark_oak_planks')); put(x + xx + (1 if xx > 0 else -1), kl + 5, z, trap('spruce_trapdoor', 'e' if xx > 0 else 'w'))
    # 船尾の楼：後ろ 7 マスに 3 段、窓、上に手すり
    zs = z0 + n - 8
    for k in range(n - 8, n):
        z = z0 + k; t = k / (n - 1)
        w = int(round(HALF[7] * scale(t, 7)))
        for xx in range(-w, w + 1):
            for y in range(dk + 1, dk + 4):
                wall = abs(xx) == w or k == n - 1 or k == n - 8
                if wall:
                    win = y == dk + 2 and (k == n - 1 and xx % 2 == 0 or abs(xx) == w and k % 2 == 1)
                    door_ = k == n - 8 and abs(xx) <= 0 and y < dk + 3
                    if door_: put(x + xx, y, z, None)
                    else: put(x + xx, y, z, {'s': 'pane', 't': 'glass', 'ax': 'x' if k == n - 1 else 'z'} if win else full('dark_oak_planks' if y == dk + 3 or abs(xx) == w and k % 3 == 0 else 'spruce_planks'))
            put(x + xx, dk + 4, z, full('spruce_planks'))
            if abs(xx) == w or k == n - 1 or k == n - 8: put(x + xx, dk + 5, z, fence('dark_oak_planks'))
        if k == n - 1:
            for xx in range(-w, w + 1): put(x + xx, dk + 4, z + 1, stair('dark_oak_planks', 'n', 'top'))
    put(x, dk + 6, z0 + n - 1, LANTERN)
    # 船首の斜檣と鎖のランタン
    for i in range(9): put(x, dk + 5 + i // 3, z0 - i, log('dark_oak_log', 'z'))
    put(x, dk + 4, z0 - 4, CHAIN); put(x, dk + 3, z0 - 4, LANTERN)
    # マスト3本：真ん中が一番高く（船の長さの 0.8）、前後は 3/4
    masts = [(z0 + int(n * 0.28), int(n * 0.62)), (z0 + int(n * 0.50), int(n * 0.80)), (z0 + int(n * 0.74), int(n * 0.58))]
    for (mz, mh) in masts:
        top = dk + mh
        for y in range(dk + 1, top + 1): put(x, y, mz, log('dark_oak_log'))
        put(x, top + 1, mz, fence('dark_oak_planks')); put(x, top + 2, mz, {'s': 'trap', 't': 'red_wool', 'f': 'e'}) if False else put(x, top + 2, mz, full('red_wool'))
        # 見張り台（真ん中のマストだけ）
        if mh == max(m[1] for m in masts):
            yo = top - 4
            for i in range(-2, 3):
                for j in range(-2, 3):
                    if abs(i) + abs(j) <= 3 and (i, j) != (0, 0):
                        put(x + i, yo, mz + j, slab('spruce_planks', 'top'))
                        if abs(i) == 2 or abs(j) == 2 or abs(i) + abs(j) == 3: put(x + i, yo + 1, mz + j, fence('dark_oak_planks'))
        # 帆桁と帆：下ほど長い桁、帆は上が狭く下が広く、前へ膨らむ（真ん中ほど）
        yards = [top - 3, top - 3 - mh // 3, top - 3 - 2 * mh // 3]
        lens = [5, 8, 10]
        if mh < max(m[1] for m in masts): lens = [4, 6, 8]
        for idx, (yt, L) in enumerate(zip(yards, lens)):
            for i in range(-L - 1, L + 2): put(x + i, yt, mz, log('dark_oak_log', 'x'))
            yb = yards[idx + 1] + 1 if idx + 1 < len(yards) else dk + 4
            Lb = lens[idx + 1] if idx + 1 < len(lens) else L + 1
            hgt = yt - yb
            if hgt < 2: continue
            for y in range(yb, yt):
                u = (yt - y) / hgt                                                    # 0（上）→1（下）
                half = int(round(L + (Lb - L) * u * 0.8))
                for i in range(-half, half + 1):
                    v = i / max(1, half)
                    c = 'white_wool' if _h(x + i, y, mz) > 0.06 else 'light_gray_wool'      # 汚し
                    put(x + i, y, mz - 1, full(c))
                    belly = abs(v) < 0.75 and 0.15 < u < 0.92                               # 膨らみ：真ん中だけ1マス前へ
                    if belly: put(x + i, y, mz - 2, full(c))
                    if belly and abs(v) < 0.4 and 0.35 < u < 0.8: put(x + i, y, mz - 3, full(c))
    # 索具：舳先→前のマストの上、マストの頂上どうし（柵の斜めの段）
    def rope(a, b):
        (ax, ay, az), (bx, by, bz) = a, b
        m = int(max(abs(by - ay), abs(bz - az)))
        for s in range(m + 1):
            t = s / max(1, m)
            p = (int(round(ax + (bx - ax) * t)), int(round(ay + (by - ay) * t)), int(round(az + (bz - az) * t)))
            if get(*p) is None: put(p[0], p[1], p[2], fence('spruce_planks'))
    (fz, fh), (mz_, mh_), (az_, ah) = masts
    rope((x, dk + 8, z0 - 8), (x, dk + fh - 1, fz))
    rope((x, dk + fh - 1, fz), (x, dk + mh_ - 1, mz_))
    rope((x, dk + mh_ - 6, mz_), (x, dk + ah - 1, az_))
    for (mz, mh) in masts:                                                            # 縄梯子：マストの中ほどから舷側へ
        for sgn in (-1, 1):
            w = 7
            for s in range(0, mh // 2, 1):
                xx = x + sgn * int(round(w * (1 - s / (mh / 2))))
                if get(xx, dk + 1 + s, mz + 1) is None: put(xx, dk + 1 + s, mz + 1, fence('spruce_planks'))
    # 甲板の荷
    for (dx_, dz_) in ((-2, 9), (2, 10), (-3, 18), (3, 24)):
        put(x + dx_, dk + 1, z0 + dz_, P.BARREL)
    put(x + 1, dk + 1, z0 + 18, P.CRATE); put(x + 1, dk + 2, z0 + 18, P.CRATE)
    return dk


# ================================================================ 灯台 [105]
def lighthouse(x, z, g, h=20, r=3):
    """根元はふっくら（r+2 → r）、胴に縦長の窓、上を膨らませ、石の展望台を逆階段でゴツゴツ張り出す、
    灯りの東屋と小さな屋根。頭は胴より1〜2マス大きい。白（方解石）の胴に、濃い木と石の縁取り。"""
    def disc(cy, rad, fn):
        for i in range(-int(rad) - 2, int(rad) + 3):
            for j in range(-int(rad) - 2, int(rad) + 3):
                d = math.hypot(i, j)
                if d <= rad + 0.35: fn(x + i, cy, z + j, i, j, d)
    for k in range(4):                                                               # 根元：4段で r+2 → r
        rad = r + 2 - k * 0.6
        disc(g + 1 + k, rad, lambda X, Y, Z, i, j, d: put(X, Y, Z, full('stone_bricks' if _h(X, Y, Z) < 0.7 else 'mossy_stone_bricks')))
    for y in range(g + 5, g + h + 1):
        bulge = 1 if y > g + h - 3 else 0                                             # 上を膨らませる
        rad = r + bulge
        def wall(X, Y, Z, i, j, d, rad=rad, y=y):
            if d < rad - 0.8: put(X, Y, Z, None); return
            lvl = (y - g) % 7
            win = lvl in (3, 4, 5) and (i == 0 or j == 0) and not bulge and y < g + h - 4
            if win: put(X, Y, Z, {'s': 'pane', 't': 'glass', 'ax': 'x' if i == 0 else 'z'})
            elif lvl == 0: put(X, Y, Z, full('stone_bricks'))                         # 帯
            else: put(X, Y, Z, full('calcite'))
        disc(y, rad, wall)
    gy = g + h + 1
    disc(gy, r + 2, lambda X, Y, Z, i, j, d: put(X, Y, Z, full('polished_andesite' if d > r else 'stone_bricks')))    # 展望台
    for i in range(-r - 3, r + 4):                                                    # 逆階段のゴツゴツ（下の段）
        for j in range(-r - 3, r + 4):
            d = math.hypot(i, j)
            if r + 1.4 < d <= r + 2.4 and _h(x + i, gy, z + j) < 0.8:
                f = 'w' if i > abs(j) else 'e' if -i > abs(j) else 'n' if j > 0 else 's'
                put(x + i, gy - 1, z + j, stair('stone_bricks', IN[{'w': 'e', 'e': 'w', 'n': 's', 's': 'n'}[f]], 'top'))
            if r + 1.4 < d <= r + 2.4: put(x + i, gy + 1, z + j, fence('dark_oak_planks'))
    for y in range(gy + 1, gy + 4):                                                   # 灯りの東屋：4本柱とガラス
        for i in range(-2, 3):
            for j in range(-2, 3):
                if max(abs(i), abs(j)) != 2: continue
                put(x + i, y, z + j, log('dark_oak_log') if abs(i) == 2 and abs(j) == 2 else {'s': 'pane', 't': 'glass', 'ax': 'z' if abs(i) == 2 else 'x'})
    put(x, gy + 1, z, full('chiseled_stone_bricks')); put(x, gy + 2, z, full('sea_lantern')); put(x, gy + 3, z, full('glowstone'))
    ry = gy + 4                                                                       # 小さな屋根：濃い木の寄棟、頂に尖り
    for k in range(4):
        rad = 3 - k
        for i in range(-rad, rad + 1):
            for j in range(-rad, rad + 1):
                if max(abs(i), abs(j)) == rad:
                    f = 'e' if i == -rad else 'w' if i == rad else 's' if j == -rad else 'n'
                    put(x + i, ry + k, z + j, stair('dark_oak_planks', f))
                elif max(abs(i), abs(j)) < rad: put(x + i, ry + k, z + j, full('dark_oak_planks'))
    put(x, ry + 4, z, fence('dark_oak_planks')); put(x, ry + 5, z, full('red_wool'))


# ================================================================ 板の足場（水面+1） [85・88・89]
def boardwalk(x0, z0, x1, z1, wy, bed, posts=True):
    """水面+1 の板の床。板は色を1マスずつ混ぜ、水に面した縁に杭（4マスおき、頭はランタンを1つおき）と、縁のトラップドア。"""
    y = wy + 1
    cells = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if get(x, y, z) is not None and get(x, y, z)['s'] != 'water': continue
            put(x, y, z, full(('spruce_planks', 'oak_planks', 'spruce_planks', 'birch_planks')[int(_h(x, z, 77) * 4)]))
            cells.add((x, z))
    n = 0
    for (x, z) in sorted(cells):
        for (dx, dz), f in (((1, 0), 'e'), ((-1, 0), 'w'), ((0, 1), 's'), ((0, -1), 'n')):
            nb = get(x + dx, y, z + dz)
            if (x + dx, z + dz) in cells or nb is not None and nb['s'] != 'water': continue
            if get(x + dx, wy, z + dz) and get(x + dx, wy, z + dz)['s'] == 'water':
                if get(x + dx, y, z + dz) is None: put(x + dx, y, z + dz, trap('spruce_trapdoor', IN[f]))
                if posts and (x + z) % 4 == 0:
                    for yy in range(bed, y + 2): put(x, yy, z, log('spruce_log'))
                    n += 1
                    if n % 2 == 0: put(x, y + 2, z, LANTERN)
                    else: put(x, y + 2, z, slab('spruce_planks'))
    return cells
