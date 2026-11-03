"""町の小物。1つずつ物として組む（11/02 藍「小物の置き方がすごく雑」「小物の造形があまりよくなかった」）。

どれも (x, z, g, 向き) を取り、g は立つ地面の一番上のブロックの高さ。向き f は「正面（人が来る側）」。
手本：らーなろっそ 冒険者の街（露店 4:53、船着き場 15:20）、港町（20:50）、領主邸（街灯・ベンチ）。
"""
from vox import put, get, full, log, stair, slab, trap, cross, fence, door

D = {'n': (0, -1), 's': (0, 1), 'e': (1, 0), 'w': (-1, 0)}
OPP = {'n': 's', 's': 'n', 'e': 'w', 'w': 'e'}
RIGHT = {'n': 'e', 'e': 's', 's': 'w', 'w': 'n'}          # 正面を向いて右

BARREL = full('barrel_side', top='barrel_top', bottom='barrel_bottom')
BARREL_OPEN = full('barrel_side', top='barrel_top_open', bottom='barrel_bottom')
HAY = full('hay_block_side', top='hay_block_top')
CRATE = full('composter_side', top='composter_bottom', bottom='composter_bottom')
MELON = full('melon_side', top='melon_top')
PUMPKIN = full('pumpkin_side', top='pumpkin_top')
LANTERN = {'s': 'lantern', 't': 'lantern'}
HANG = {'s': 'lantern', 't': 'lantern', 'hang': True}
def wall(t): return {'s': 'wall', 't': t}
def carpet(t): return {'s': 'carpet', 't': t}
def pot(plant): return {'s': 'pot', 't': 'flower_pot', 'plant': plant}

def at(x, z, f, fwd, right):
    """正面 f を基準に、前へ fwd・右へ right 進んだマス。"""
    fx, fz = D[f]; rx, rz = D[RIGHT[f]]
    return x + fx * fwd + rx * right, z + fz * fwd + rz * right


# ---- 街灯：石の台、暗い柵の柱、腕を出して灯りを吊る [領主邸・決まり14]
def lamp_post(x, z, g, arm='e', base='stone_bricks'):
    ax, az = D[arm]
    put(x, g + 1, z, wall(base))
    for y in (g + 2, g + 3, g + 4): put(x, y, z, fence('dark_oak_planks'))
    put(x + ax, g + 4, z + az, fence('dark_oak_planks'))
    put(x + ax, g + 3, z + az, HANG)
    put(x, g + 5, z, trap('dark_oak_trapdoor', 'bottom'))
    return [(x, z), (x + ax, z + az)]


# ---- 杭の灯り：水の底から立つ丸太の頭にランタン [港町 20:50]
def pile_lamp(x, z, bottom, top):
    for y in range(bottom, top + 1): put(x, y, z, log('spruce_log'))
    put(x, top + 1, z, LANTERN)


# ---- ベンチ：階段を2〜3個並べ、両端にトラップドアの肘掛け [領主邸 19:40]
def bench(x, z, g, f, n=2):
    cells = []
    for i in range(n):
        cx, cz = at(x, z, f, 0, i)
        put(cx, g + 1, cz, stair('spruce_planks', OPP[f])); cells.append((cx, cz))
    lx, lz = at(x, z, f, 0, -1); rx, rz = at(x, z, f, 0, n)
    put(lx, g + 1, lz, trap('spruce_trapdoor', RIGHT[f])); put(rx, g + 1, rz, trap('spruce_trapdoor', RIGHT[OPP[f]]))
    return cells + [(lx, lz), (rx, rz)]


# ---- 露店：幅4・奥行き2。後ろが高い一枚の斜めの布屋根（2色の縞）、台に商品 [冒険者の街 4:53]
def stall(x, z, g, f, c1, c2, goods):
    """(x, z) は正面から見て左手前の柱。"""
    cells = []
    for a in range(4):
        for b in range(2):
            cx, cz = at(x, z, f, -b, a); cells.append((cx, cz))
            post = a in (0, 3)
            if post:
                for y in range(g + 1, g + (3 if b == 0 else 4)): put(cx, y, cz, fence('spruce_planks'))
            elif b == 0:                                                   # 台：上はハーフ、前にトラップドア
                put(cx, g + 1, cz, slab('spruce_planks', 'top'))
                fx, fz = at(x, z, f, 1, a); put(fx, g + 1, fz, trap('spruce_trapdoor', OPP[f]))
            else:
                put(cx, g + 1, cz, goods[(a - 1) % len(goods)])
    for a in range(-1, 5):                                                 # 屋根：ハーフ3段のなだらかな斜め、色は縦縞
        col = c1 if a % 2 == 0 else c2
        for b, y, h in ((1, g + 4, 'bottom'), (0, g + 3, 'top'), (-1, g + 3, 'bottom')):
            cx, cz = at(x, z, f, -b, a); put(cx, y, cz, slab(col, h))
    for a in (1, 2):                                                        # 台の上の商品：鉢と小さな荷
        cx, cz = at(x, z, f, 0, a)
        put(cx, g + 2, cz, pot('red_tulip' if a == 1 else 'cornflower') if goods[0] is not MELON else carpet('lime_wool'))
    return cells


# ---- 荷の塊：背を壁に付けて、大きい物を奥・小さい物を手前に段に積む [冒険者の街 4:36・決まり10]
def cargo(x, z, g, f, kinds=None, w=3):
    """(x, z) は奥の左端。f は塊の正面（通り側）。奥の列は2〜3段、手前は1段。"""
    k = kinds or [BARREL, CRATE, HAY]
    plan = [  # (前へ, 右へ, 段数)
        (0, 0, 2), (0, 1, 3), (0, 2, 1),
        (1, 0, 1), (1, 1, 1),
    ] if w == 3 else [(0, 0, 2), (0, 1, 1), (1, 0, 1)]
    cells = []
    for i, (fw, rt, h) in enumerate(plan):
        cx, cz = at(x, z, f, fw, rt); cells.append((cx, cz))
        for j in range(h):
            put(cx, g + 1 + j, cz, k[(i + j) % len(k)])
    return cells


# ---- 小舟：階段の輪で舷を半ブロック立て、水の上に浮かべる [冒険者の街 15:20]
def boat(x, z, wy, along='x', load=None, n=4):
    """wy は一番上の水のブロックの高さ。長さ n・幅2。舷は階段の背、底はその下半分。"""
    a = 'e' if along == 'x' else 's'; b = RIGHT[a]
    cells = []
    for i in range(n):
        for j in range(2):
            cx, cz = at(x, z, a, i, j); cells.append((cx, cz))
            f = OPP[a] if i == 0 else a if i == n - 1 else (OPP[b] if j == 0 else b)
            put(cx, wy + 1, cz, stair('spruce_planks', f))
    cx, cz = at(x, z, a, n // 2, 0); put(cx, wy + 1, cz, slab('spruce_planks'))      # 腰掛け
    cx, cz = at(x, z, a, n // 2, 1); put(cx, wy + 1, cz, slab('spruce_planks'))
    if load is not None:                                                   # 積み荷は舷の内側に収める
        cx, cz = at(x, z, a, 1, 0); put(cx, wy + 1, cz, load)
    return cells


# ---- 桟橋：板の床、2マスおきの杭、先の杭に灯り。脇へ1マスの細い渡り板 [冒険者の街 15:20・港町]
def pier(x, z, wy, bed, f, length=6, width=3, catwalk=True):
    """(x, z) は岸の付け根の左。f は水へ向かう向き。床は wy+1（岸の通りと同じ）。"""
    cells = []
    for i in range(length):
        for j in range(width):
            cx, cz = at(x, z, f, i, j); cells.append((cx, cz))
            put(cx, wy + 1, cz, full('spruce_planks'))
            for y in range(bed + 1, wy + 1):
                if get(cx, y, cz) and get(cx, y, cz)['s'] == 'water': put(cx, y, cz, None)
            if (j in (0, width - 1)) and i % 2 == 1 or i == length - 1 and j in (0, width - 1):
                for y in range(bed + 1, wy + 1): put(cx, y, cz, log('spruce_log'))
    for j in (0, width - 1):                                              # 先の杭：床の角から1つ立てて灯り
        cx, cz = at(x, z, f, length - 1, j)
        put(cx, wy + 2, cz, log('spruce_log')); put(cx, wy + 3, cz, LANTERN)
    for i in range(1, length):                                            # 縁：板の横にトラップドア
        for j, side in ((0, OPP[RIGHT[f]]), (width - 1, RIGHT[f])):
            pass
    if catwalk:                                                            # 渡り板：水面のすぐ上
        for i in range(2, length):
            cx, cz = at(x, z, f, i, width); put(cx, wy + 1, cz, trap('spruce_trapdoor', 'top')); cells.append((cx, cz))
        cx, cz = at(x, z, f, length - 1, width + 1); pile_lamp(cx, cz, bed + 1, wy + 1)
    return cells


# ---- 物置：石の土台、角に皮を剥いだ丸太、板の壁、切妻の屋根、横に薪の差し掛け [領主邸・決まり13]
def shed(x0, z0, g, w=5, d=4):
    """南（+z）が正面。x0..x0+w-1, z0..z0+d-1。"""
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            corner = x in (x0, x0 + w - 1) and z in (z0, z0 + d - 1)
            if not edge:
                put(x, g, z, full('spruce_planks')); continue
            put(x, g + 1, z, full('cobblestone' if (x + z) % 3 else 'mossy_cobblestone'))
            for y in (g + 2, g + 3):
                put(x, y, z, log('stripped_spruce_log') if corner else full('spruce_planks'))
    # 戸口と窓
    dx = x0 + w // 2
    put(dx, g + 1, z0 + d - 1, door('bottom', 'n')); put(dx, g + 2, z0 + d - 1, door('top', 'n'))
    put(dx, g + 1, z0 + d - 1, door('bottom', 'n'))
    put(x0 + w - 1, g + 2, z0 + 1, {'s': 'pane', 't': 'glass', 'ax': 'z'})
    put(x0 + w, g + 2, z0 + 1, trap('spruce_trapdoor', 'w')) if False else None
    # 切妻：棟は x に沿う
    rows = [(z0 - 1, g + 3, 's'), (z0, g + 4, 's'), (z0 + 1, g + 5, 's'), (z0 + 2, g + 5, 'n'), (z0 + 3, g + 4, 'n'), (z0 + 4, g + 3, 'n')]
    rows = [r for r in rows if r[0] <= z0 + d]
    for x in range(x0 - 1, x0 + w + 1):
        t = 'dark_oak_planks' if x in (x0 - 1, x0 + w) else 'spruce_planks'
        for (z, y, f) in rows: put(x, y, z, stair(t, f))
    for x in (x0, x0 + w - 1):                                              # 妻壁
        for z in range(z0 + 1, z0 + d - 1): put(x, g + 4, z, full('spruce_planks'))
    put(x0 + w - 1, g + 4, z0 + 1, log('stripped_spruce_log'))
    # 差し掛け：西の壁に、柵の柱2本とハーフの屋根、下に薪
    for z in range(z0 - 1, z0 + d + 1): put(x0 - 1, g + 3, z, slab('spruce_planks')) if get(x0 - 1, g + 3, z) is None else None
    for z in range(z0, z0 + d):
        put(x0 - 2, g + 3, z, slab('dark_oak_planks'))
    for z in (z0, z0 + d - 1):
        put(x0 - 2, g + 1, z, fence('spruce_planks')); put(x0 - 2, g + 2, z, fence('spruce_planks'))
    for z in range(z0 + 1, z0 + d - 1):
        put(x0 - 1, g + 1, z, log('oak_log', 'z')); put(x0 - 1, g + 2, z, log('oak_log', 'z'))
        put(x0 - 2, g + 1, z, log('oak_log', 'z'))
    put(x0 - 1, g + 1, z0, BARREL)
    return [(x, z) for x in range(x0 - 2, x0 + w) for z in range(z0, z0 + d)]


# ---- 畑：丸太で縁取った区画、真ん中に水路、作物は2列ずつ、角にコンポスターと干し草 [冒険者の街 18:50・決まり12]
def garden(x0, z0, g, w=7, d=5, crops=('wheat_stage7', 'carrots_stage3', 'beetroots_stage3', 'potatoes_stage3')):
    """x0..x0+w-1, z0..z0+d-1。縁は丸太、水路は z の真ん中の列。"""
    mid = z0 + d // 2
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            if edge:
                put(x, g, z, log('stripped_spruce_log', 'x' if z in (z0, z0 + d - 1) else 'z'))
            elif z == mid:
                put(x, g, z, {'s': 'water', 't': 'water_still'}); put(x, g - 1, z, full('dirt'))
            else:
                put(x, g, z, full('dirt', top='farmland_moist'))
                put(x, g + 1, z, cross(crops[((z < mid) * 2 + (x - x0) // 3) % len(crops)]))
    put(x0 + w - 1, g + 1, z0, CRATE); put(x0 + w, g + 1, z0, HAY); put(x0 + w, g + 2, z0, HAY) if False else None
    put(x0, g + 1, z0, fence('spruce_planks')); put(x0, g + 2, z0, fence('spruce_planks')); put(x0, g + 3, z0, LANTERN)
    return [(x, z) for x in range(x0, x0 + w + 1) for z in range(z0, z0 + d)]


# ---- 薪の棚：丸太を横に積み、上にハーフの屋根、脇に切り株と斧台 [決まり13]
def woodpile(x, z, g, f='s'):
    cells = []
    for a in range(3):
        cx, cz = at(x, z, f, 0, a); cells.append((cx, cz))
        ax = 'x' if f in 'ns' else 'z'
        for y in (g + 1, g + 2): put(cx, y, cz, log('oak_log', 'z' if f in 'ns' else 'x'))
        put(cx, g + 3, cz, slab('spruce_planks'))
    cx, cz = at(x, z, f, 0, 3); put(cx, g + 1, cz, log('oak_log')); cells.append((cx, cz))   # 切り株
    return cells


# ---- 道の街灯：暗い石の塀2段の台、柵の柱、板の頭、逆さ階段とハーフの腕、腕の先にランタンを吊る。
#      3本並べるときは高さを変える（柱4〜6段）。腕は道の上へ向ける。 [そろんご 道作り 12:12〜12:30・learn3/props-notes.md C5]
def path_lamp(x, z, g, arm='e', posts=5):
    ax, az = D[arm]
    put(x, g + 1, z, wall('cobbled_deepslate')); put(x, g + 2, z, wall('cobbled_deepslate'))
    for y in range(g + 3, g + 3 + posts): put(x, y, z, fence('spruce_planks'))
    top = g + 3 + posts
    put(x, top, z, full('spruce_planks'))
    put(x + ax, top, z + az, stair('spruce_planks', OPP[arm], 'top'))
    put(x + ax * 2, top, z + az * 2, slab('spruce_planks', 'top'))
    put(x + ax * 2, top - 1, z + az * 2, HANG)
    return [(x, z)]


# ---- 木陰の休みどころ：丸石の階段2つの座面、片端に丸石の塀とランタン。隣に、1段盛った粗い土を
#      暗い石の塀で縁取った花壇（4×2）。木の根元に置く。 [そろんご 道作り 13:04〜13:50・props-notes.md C9・C12]
def rest_spot(x, z, g, f, flowers=('red_tulip', 'orange_tulip', 'cornflower', 'oxeye_daisy')):
    cells = []
    for i in range(2):
        cx, cz = at(x, z, f, 0, i)
        put(cx, g + 1, cz, stair('cobblestone', OPP[f])); cells.append((cx, cz))
    ex, ez = at(x, z, f, 0, -1)
    put(ex, g + 1, ez, wall('cobblestone')); put(ex, g + 2, ez, LANTERN); cells.append((ex, ez))
    for i in range(4):                                                   # 花壇：ベンチの右隣から奥へ
        for j in range(2):
            cx, cz = at(x, z, f, -j, 3 + i)
            put(cx, g + 1, cz, full('coarse_dirt')); put(cx, g + 2, cz, cross(flowers[(i + j) % len(flowers)])); cells.append((cx, cz))
    for i in range(-1, 5):                                               # 縁の塀：長辺の前後と短辺
        for j in (1, -2):
            cx, cz = at(x, z, f, j, 3 + i)
            if get(cx, g + 1, cz) is None: put(cx, g + 1, cz, wall('cobbled_deepslate')); cells.append((cx, cz))
    for j in (0, -1):
        cx, cz = at(x, z, f, j, 7)
        if get(cx, g + 1, cz) is None: put(cx, g + 1, cz, wall('cobbled_deepslate')); cells.append((cx, cz))
    return cells
