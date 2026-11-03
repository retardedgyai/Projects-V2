"""木の部品（rules/nature.md の 1〜10）。丸い玉の葉と円錐のトウヒは使わない（11/03 藍「配置されてる木とかのクオリティが低すぎて酷い」）。

どれも (x, z, g, seed) を取る。g は根元の地面の一番上のブロックの高さ。
  spruce(x, z, g, seed, h)    トウヒ：垂れた腕の層を重ねる [DiddiHD]
  small_spruce(x, z, g, seed) 小さいトウヒ：柵の幹に葉の段と幹の見える段を交互に [Broken Orbit]
  oak(x, z, g, seed, size)    広葉樹：ずらした幹、分かれるたびに細る枝、レンズ形の葉の板、濃淡 [Calven・Broken Orbit]
  birch(x, z, g, seed)        白樺：細い幹、小さな葉の板
  big_tree(x, z, g, seed)     大木：細い筋のらせんの幹、根、ジグザグの枝 [らーなろっそ 巨大樹]
木を置く前に、置ける所か（根元が平らで、上が空いているか）を呼ぶ側で確かめる。
"""
import math, random
from vox import put, get, full, log, stair, slab, trap, fence

D8 = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
FACE = {(1, 0): 'e', (-1, 0): 'w', (0, 1): 's', (0, -1): 'n'}

def leaf(x, y, z, t):
    if get(x, y, z) is None: put(x, y, z, full(t))

def wood(x, y, z, b):
    cur = get(x, y, z)
    if cur is None or 'leaves' in cur.get('t', ''): put(x, y, z, b)


# ================================================================ トウヒ [rules 8]
def spruce(x, z, g, seed, h=None):
    """2段ごとに葉の層。層は8方向の腕を中心に埋めた星形の円盤で、外の縁だけ1つ垂れる。層の間の段は小さな円盤で、影の隙間になる。
    下の層ほど広く（4〜5）、上ほど狭く（1〜2）、頂は葉の細い柱。"""
    r = random.Random(seed)
    h = h or r.randint(13, 18)
    lt = 'spruce_leaves'
    for y in range(g + 1, g + h): put(x, y, z, log('spruce_log'))
    y0, y1 = g + 3, g + h - 1
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, y1 - y0)
        big = (y - y0) % 2 == 0
        rad = (4.8 * (1 - t) + 1.0) if big else (2.4 * (1 - t) + 0.6)
        rad += r.uniform(-0.4, 0.4)
        R = int(rad) + 1
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if not (dx or dz): continue
                d = math.hypot(dx, dz)
                ang = math.atan2(dz, dx)
                star = 0.75 + 0.25 * abs(math.cos(4 * ang))                              # 8方向の腕が少し長い
                if d > rad * star + 0.2: continue
                if big and d > rad * star - 1.1 and t < 0.8: leaf(x + dx, y - 1, z + dz, lt)   # 外の縁は1つ垂れる
                else: leaf(x + dx, y, z + dz, lt)
    for y in range(y1, y1 + 3): leaf(x, y, z, lt)
    _roots(x, z, g, r, 'spruce_log', 'spruce', 2)


def small_spruce(x, z, g, seed):
    """小さいトウヒ：細い幹に、広い段（半径1.5〜2）と狭い段（幹の4方だけ）を交互に。上ほど狭く、頂は葉の柱。"""
    r = random.Random(seed)
    h = r.randint(7, 10)
    for y in range(g + 1, g + h): put(x, y, z, log('spruce_log') if y < g + 3 else fence('spruce_planks'))
    for i, y in enumerate(range(g + 2, g + h)):
        t = i / max(1, h - 3)
        rad = (1.6 * (1 - t) + 1.2) if i % 2 == 0 else (0.6 * (1 - t) + 1.0)        # 狭い段も幹の4方は必ず埋める
        R = int(rad) + 1
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if (dx or dz) and math.hypot(dx, dz) <= rad + r.uniform(-0.2, 0.2): leaf(x + dx, y, z + dz, 'spruce_leaves')
    for y in range(g + h - 1, g + h + 2): leaf(x, y, z, 'spruce_leaves')


# ================================================================ 広葉樹 [rules 1〜7]
def _roots(x, z, g, r, logt, plank, n):
    """根：幹と同じ材料の階段とハーフを、地面に這わせる。"""
    dirs = r.sample([(1, 0), (-1, 0), (0, 1), (0, -1)], n)
    for (dx, dz) in dirs:
        L = r.randint(1, 3)
        for k in range(1, L + 1):
            X, Z = x + dx * k, z + dz * k
            if get(X, g + 1, Z) is not None and get(X, g + 1, Z)['s'] != 'cross': break
            if k == 1 and L > 1: put(X, g + 1, Z, log(logt, 'x' if dx else 'z'))
            elif k < L: put(X, g + 1, Z, stair(plank + '_planks', FACE[(-dx, -dz)]))
            else: put(X, g + 1, Z, slab(plank + '_planks'))


def _lens(cx, cy, cz, rad, r, dark, mid, light, flat=True):
    """葉の塊：下に小さな暗い面、広い底、上へ半分ずつ小さくなる段、一番上は数個の点。輪郭は揺らし、内側に空気の穴。
    下面と縁は暗い葉、中はふつう、上は明るい葉（暗:明 ≒ 1:2）。"""
    for (yo, rr, kind) in ((-1, rad * 0.5, 'under'), (0, rad, 'base'), (1, rad * 0.9, 'mid'), (2, rad * 0.6, 'upper'), (3, rad * 0.25, 'top')):
        if rr < 0.5 and kind not in ('top',): continue
        R = int(math.ceil(rr)) + 1
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                d = math.hypot(dx, dz * 1.08) + r.uniform(-0.55, 0.55)
                if d > rr + 0.3: continue
                if kind == 'top' and r.random() < 0.4: continue
                if kind == 'base' and d < rr * 0.4 and r.random() < 0.3: continue
                if kind == 'under': t = dark
                elif kind == 'base': t = dark if d > rr - 1.0 or r.random() < 0.3 else mid
                elif kind == 'mid': t = mid if r.random() < 0.7 else light
                else: t = light if r.random() < 0.65 else mid
                leaf(cx + dx, cy + yo, cz + dz, t)

def _branch(x, y, z, dx, dz, length, rise, r, logt, plank, tips):
    """1マス太さの折れ線の枝。1歩ずつ（前・上・脇へ1つ）進むので、途中が離れない。少し上向きで、先は細る
    （原木 → 階段 → ハーフ）。先の位置を tips に返す。"""
    cx, cy, cz = x, y, z
    path = []
    for k in range(length):
        cx += dx; cz += dz; path.append((cx, cy, cz))
        if r.random() < rise: cy += 1; path.append((cx, cy, cz))
        if r.random() < 0.25:
            sx, sz = (dz, dx) if r.random() < 0.5 else (-dz, -dx)
            cx += sx; cz += sz; path.append((cx, cy, cz))
    n = len(path)
    for i, (px, py, pz) in enumerate(path):
        frac = (i + 1) / n
        if frac <= 0.6: wood(px, py, pz, log(logt, 'x' if dx else 'z'))
        elif frac < 1.0: wood(px, py, pz, full(plank + '_planks') if r.random() < 0.5 else log(logt, 'x' if dx else 'z'))
        else: wood(px, py, pz, slab(plank + '_planks', 'top'))
    tips.append((cx, cy, cz))

def oak(x, z, g, seed, size=1.0, logt='oak_log', plank='oak', leaves=('dark_oak_leaves', 'oak_leaves', 'azalea_leaves')):
    """広葉樹：幹は1〜2段ごとに横へずらし、根元は太い。上で3〜5本の枝に分かれ、枝先にレンズ形の葉の板を高さを変えて乗せる。"""
    r = random.Random(seed)
    th = int(round(r.randint(5, 7) * size))
    cx, cz = x, z
    lean = r.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (0, 0)])
    for i, y in enumerate(range(g + 1, g + th + 1)):
        put(cx, y, cz, log(logt))
        if size >= 1.2 and i < 2:                                                      # 根元を太く
            for (dx, dz) in ((1, 0), (0, 1)): put(cx + dx, y, cz + dz, log(logt))
        if i in (2, 4) and lean != (0, 0) and r.random() < 0.7:                        # 1マスずらし、段差は階段で埋める
            put(cx + lean[0], y, cz + lean[1], stair(plank + '_planks', FACE[(-lean[0], -lean[1])]))
            cx += lean[0]; cz += lean[1]
    top = g + th
    tips = []
    nb = r.randint(3, 5) if size >= 1 else r.randint(2, 3)
    a0 = r.uniform(0, 2 * math.pi)
    for i in range(nb):
        a = a0 + i * 2 * math.pi / nb + r.uniform(-0.4, 0.4)
        dx, dz = (1 if math.cos(a) > 0.38 else -1 if math.cos(a) < -0.38 else 0), (1 if math.sin(a) > 0.38 else -1 if math.sin(a) < -0.38 else 0)
        if (dx, dz) == (0, 0): dx = 1
        by = top - r.randint(0, 2)
        _branch(cx, by, cz, dx, dz, int(round(r.randint(2, 4) * size)), 0.45, r, logt, plank, tips)
    for y in range(top + 1, top + 3): put(cx, y, cz, log(logt))                       # 真ん中の芯
    tips.append((cx, top + 2, cz))
    dark, mid, light = leaves
    for (tx, ty, tz) in tips:
        _lens(tx, ty + 1, tz, r.uniform(2.0, 2.8) * size, r, dark, mid, light)
    _lens(cx, top + 3, cz, 3.0 * size, r, dark, mid, light)                        # 真ん中の大きな塊で、枝の塊を1つの樹冠にまとめる
    _roots(cx if False else x, z, g, r, logt, plank, 3 if size >= 1 else 2)


def birch(x, z, g, seed):
    """白樺：細い幹（白い原木）、小さな枝、小さなレンズの葉の板を2〜3枚。"""
    r = random.Random(seed)
    th = r.randint(6, 8)
    for y in range(g + 1, g + th + 1): put(x, y, z, log('birch_log'))
    tips = [(x, g + th + 1, z)]
    for i in range(2):
        a = r.uniform(0, 2 * math.pi)
        dx, dz = round(math.cos(a)), round(math.sin(a))
        if (dx, dz) == (0, 0): continue
        _branch(x, g + th - 2 - i * 2, z, dx, dz, 2, 0.5, r, 'birch_log', 'birch', tips)
    for (tx, ty, tz) in tips: _lens(tx, ty, tz, r.uniform(1.6, 2.2), r, 'birch_leaves', 'birch_leaves', 'azalea_leaves')
    _roots(x, z, g, r, 'birch_log', 'birch', 2)


def cherry(x, z, g, seed):
    """桜：低く広がる枝に、平たい桃色の葉の板。"""
    r = random.Random(seed)
    th = r.randint(4, 5)
    for y in range(g + 1, g + th + 1): put(x, y, z, log('cherry_log'))
    tips = []
    for i in range(r.randint(3, 4)):
        a = r.uniform(0, 2 * math.pi)
        dx, dz = round(math.cos(a)), round(math.sin(a))
        if (dx, dz) == (0, 0): dx = 1
        _branch(x, g + th, z, dx, dz, r.randint(3, 4), 0.4, r, 'cherry_log', 'cherry', tips)
    for (tx, ty, tz) in tips: _lens(tx, ty + 1, tz, r.uniform(2.2, 2.8), r, 'cherry_leaves', 'cherry_leaves', 'cherry_leaves')
    _roots(x, z, g, r, 'cherry_log', 'cherry', 2)


def big_tree(x, z, g, seed, h=22):
    """大木：細い筋（1〜2マス幅）を4本、らせんに立てて幹の骨にし、中をダークオークで埋める。外はトウヒの原木で、向きを混ぜる。
    根元は外へ広げて横向きの原木を這わせ、上でジグザグの太い枝を張って、葉の板を何段も乗せる。苔を縦に貼る。 [らーなろっそ 巨大樹]"""
    r = random.Random(seed)
    for y in range(g + 1, g + h + 1):
        t = (y - g) / h
        rad = 2.6 - 1.1 * t + (0.8 if t > 0.85 else 0)                                 # 根元が太い → 中ほど細い → 分かれ目で少し太い
        tw = (y - g) * 0.16
        strands = [(math.cos(tw + k * math.pi / 2) * rad, math.sin(tw + k * math.pi / 2) * rad) for k in range(4)]
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                d = math.hypot(dx, dz)
                if d <= rad - 0.6: put(x + dx, y, z + dz, full('dark_oak_planks'))
                elif d <= rad + 0.5 or any(math.hypot(dx - sx, dz - sz) < 0.9 for sx, sz in strands):
                    ax = ('x' if r.random() < 0.5 else 'z') if r.random() < 0.1 else 'y'
                    moss = (int(math.atan2(dz, dx) * 3) + (y - g) // 4) % 7 == 0 and t < 0.7
                    put(x + dx, y, z + dz, full('moss_block') if moss else log('spruce_log', ax))
    for k in range(6):                                                                  # 根：外へ広げて這わせる
        a = k * math.pi / 3 + r.uniform(-0.3, 0.3)
        L = r.randint(4, 7)
        for s in range(2, L + 1):
            X, Z = x + int(round(math.cos(a) * s)), z + int(round(math.sin(a) * s))
            yy = g + max(1, 3 - s // 2)
            for y2 in range(g + 1, yy + 1): put(X, y2, Z, log('spruce_log', 'x' if abs(math.cos(a)) > 0.7 else 'z'))
            if s == L: put(X, g + 1, Z, stair('spruce_planks', 'n'))
    tips = []
    for k in range(r.randint(5, 6)):
        a = k * 2 * math.pi / 5.5 + r.uniform(-0.3, 0.3)
        dx, dz = round(math.cos(a)), round(math.sin(a))
        if (dx, dz) == (0, 0): dx = 1
        sx, sz = x + dx * 2, z + dz * 2
        _branch(sx, g + h - r.randint(0, 4), sz, dx, dz, r.randint(5, 8), 0.4, r, 'spruce_log', 'spruce', tips)
    tips.append((x, g + h + 1, z))
    for (tx, ty, tz) in tips:
        _lens(tx, ty + 1, tz, r.uniform(3.0, 4.2), r, 'dark_oak_leaves', 'oak_leaves', 'azalea_leaves')
        if r.random() < 0.6: _lens(tx + r.choice((-2, 2)), ty + 3, tz + r.choice((-2, 2)), r.uniform(2.0, 2.8), r, 'dark_oak_leaves', 'oak_leaves', 'azalea_leaves')
