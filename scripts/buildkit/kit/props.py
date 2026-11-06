"""町の小物。1つずつ物として組む（11/02 藍「小物の置き方がすごく雑」「小物の造形があまりよくなかった」）。

どれも (x, z, g, 向き) を取り、g は立つ地面の一番上のブロックの高さ。向き f は「正面（人が来る側）」。
手本：らーなろっそ 冒険者の街（露店 4:53、船着き場 15:20）、港町（20:50）、領主邸（街灯・ベンチ）。
"""
from vox import put, get, full, log, stair, slab, trap, cross, fence, door, water, CHAIN

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


# ---- 踏み跡の行き止まりの休み場：切り株の上にランタン、横に寝かせた丸太の腰掛け、足元に花2〜3本とキノコ。
#      小道の行き先になる。 [Mr CozyCraft 1:20 小道の突き当たりの空き地・One Team 小物の足元のキノコ]
def trail_end(x, z, g, f):
    cells = []
    put(x, g + 1, z, log('spruce_log')); put(x, g + 2, z, LANTERN); cells.append((x, z))
    for i in (1, 2):
        cx, cz = at(x, z, f, 1, i - 1)
        put(cx, g + 1, cz, log('oak_log', 'x' if f in ('n', 's') else 'z')); cells.append((cx, cz))
    for (fw, rt, plant) in ((0, 1, 'brown_mushroom'), (-1, 0, 'oxeye_daisy'), (-1, 1, 'cornflower'), (0, -1, 'fern')):
        cx, cz = at(x, z, f, fw, rt)
        if get(cx, g + 1, cz) is None: put(cx, g + 1, cz, cross(plant))
    return cells


# ---- 井戸：3×3。地面の高さの輪は四隅が石レンガで、口と四方は水。その上の縁は前後が低い石レンガの階段（背は内側）、
#      左右が石レンガ、四隅が塀。左右の柱は塀 → トウヒの柵、上にトウヒの階段の屋根と庇のトラップドア、
#      真ん中に上付きのハーフ、その下に砥石を吊り、鎖を口へ下ろす。縁にランタン。f は正面（人が来る側）。
#      [ごをくんな 庭の装飾 0:31〜1:38・props-notes.md D16]
def well(x, z, g, f):
    cells = []
    put(x, g - 1, z, full('stone_bricks'))
    for fw in (-1, 0, 1):
        for rt in (-1, 0, 1):
            cx, cz = at(x, z, f, fw, rt); cells.append((cx, cz))
            corner = fw != 0 and rt != 0
            put(cx, g, cz, full('stone_bricks') if corner else water())
            if fw == 0 and rt == 0: continue
            if corner: put(cx, g + 1, cz, wall('stone_bricks'))
            elif rt != 0: put(cx, g + 1, cz, full('stone_bricks'))
            else: put(cx, g + 1, cz, stair('stone_bricks', f if fw < 0 else OPP[f]))   # 前後は低い縁、背は内側
    for rt in (-1, 1):
        cx, cz = at(x, z, f, 0, rt)
        put(cx, g + 2, cz, wall('stone_bricks')); put(cx, g + 3, cz, fence('spruce_planks'))
        side = RIGHT[f] if rt > 0 else OPP[RIGHT[f]]
        put(cx, g + 4, cz, stair('spruce_planks', OPP[side]))                     # 屋根：外へ下がる
        ox, oz = at(x, z, f, 0, rt * 2)
        put(ox, g + 4, oz, stair('spruce_planks', side, 'top')); put(ox, g + 3, oz, trap('spruce_trapdoor', 'top'))
    put(x, g + 4, z, slab('spruce_planks', 'top'))
    put(x, g + 3, z, {'s': 'grindstone', 't': 'grindstone_side'})
    put(x, g + 2, z, CHAIN); put(x, g + 1, z, CHAIN)
    lx, lz = at(x, z, f, -1, -1); put(lx, g + 2, lz, LANTERN)
    return cells


# ---- 荷車：トウヒの上付きハーフ 3×2 の荷台、両側面に開いたオークのトラップドアの車輪 2 つずつ（1マス空け）、
#      前に引き手（柵）、後ろにトラップドア、荷台に樽と木箱とランタン。f は引き手の向き。
#      [ごをくんな 庭の装飾 1:43〜2:27・props-notes.md D17]
def cart(x, z, g, f):
    cells = []
    for fw in (0, 1, 2):
        for rt in (0, 1):
            cx, cz = at(x, z, f, -fw, rt); put(cx, g + 1, cz, slab('spruce_planks', 'top')); cells.append((cx, cz))
    side = RIGHT[f]
    for fw in (0, 2):                                                    # 車輪：側面に貼った開いたトラップドア
        cx, cz = at(x, z, f, -fw, -1); put(cx, g + 1, cz, trap('oak_trapdoor', side)); cells.append((cx, cz))
        cx, cz = at(x, z, f, -fw, 2); put(cx, g + 1, cz, trap('oak_trapdoor', OPP[side])); cells.append((cx, cz))
    hx, hz = at(x, z, f, 1, 0); put(hx, g + 1, hz, fence('spruce_planks')); cells.append((hx, hz))
    bx, bz = at(x, z, f, -3, 0); put(bx, g + 1, bz, trap('spruce_trapdoor', f)); cells.append((bx, bz))
    for (fw, rt, b) in ((-1, 0, BARREL), (-2, 1, CRATE), (-2, 0, HAY)):
        cx, cz = at(x, z, f, fw, rt); put(cx, g + 2, cz, b)
    cx, cz = at(x, z, f, 0, 1); put(cx, g + 2, cz, LANTERN)
    return cells


# ---- 灯台：海に立つ岩の上の白い塔。下ほど太く、赤い帯2本、縦長の窓、持ち送りの回廊、ガラスの火の部屋、尖った屋根 [港町 11/04]
def lighthouse(x, z, g, r=4, h=22):
    """(x, z) は塔の芯、g は立つ岩の上面。塔は下で半径 r、上で r-1。上に回廊（g+h+1）・火の部屋・屋根。"""
    import math as _m
    def ring(cx, cz, rad, fn):
        for i in range(-int(rad) - 2, int(rad) + 3):
            for j in range(-int(rad) - 2, int(rad) + 3):
                d = _m.hypot(i, j)
                fn(cx + i, cz + j, i, j, d)
    for k in range(2):                                                      # 土台：2段の石の台、上の段の縁は階段
        rad = r + 2 - k
        ring(x, z, rad, lambda X, Z, i, j, d: put(X, g + 1 + k, Z, full('stone_bricks' if (i * 3 + j) % 5 else 'mossy_stone_bricks')) if d <= rad + 0.3 else None)
    for y in range(g + 3, g + h + 1):
        t = (y - g - 3) / max(1, h - 3)
        rad = r - t * 1.0
        band = (y - g) in (8, 9, 16, 17)
        lvl = (y - g) in (5, 6, 12, 13, 19, 20)
        def wall(X, Z, i, j, d, rad=rad, band=band, lvl=lvl):
            if not (rad - 1.0 < d <= rad + 0.4): return
            win = lvl and ((i == 0 and abs(j) > 0) or (j == 0 and abs(i) > 0))
            if win: put(X, y, Z, {'s': 'pane', 't': 'glass', 'ax': 'x' if i == 0 else 'z'}); return
            put(X, y, Z, full('red_sandstone' if band else 'calcite'))
        ring(x, z, rad, wall)
        ring(x, z, rad, lambda X, Z, i, j, d, rad=rad: put(X, y, Z, None) if d <= rad - 1.0 and get(X, y, Z) is None else None)
    gy = g + h + 1
    rt = r - 1.0
    ring(x, z, rt + 1, lambda X, Z, i, j, d: put(X, gy - 1, Z, stair('stone_bricks', 'w' if i > abs(j) else 'e' if -i > abs(j) else 's' if j > 0 else 'n', 'top')) if rt + 0.4 < d <= rt + 1.4 else None)   # 持ち送り
    def gal(X, Z, i, j, d):
        if d <= rt + 1.4:
            put(X, gy, Z, full('polished_andesite' if d > rt - 0.5 else 'stone_bricks'))
            if d > rt + 0.5: put(X, gy + 1, Z, fence('dark_oak_planks'))
    ring(x, z, rt + 1, gal)
    for y in range(gy + 1, gy + 4):                                         # 火の部屋：四隅の柱とガラス、真ん中に火の籠
        for i in range(-2, 3):
            for j in range(-2, 3):
                if max(abs(i), abs(j)) == 2:
                    put(x + i, y, z + j, log('dark_oak_log') if abs(i) == 2 and abs(j) == 2 else {'s': 'pane', 't': 'glass', 'ax': 'z' if abs(i) == 2 else 'x'})
    put(x, gy + 1, z, full('netherrack')); put(x, gy + 2, z, cross('fire_0'))
    for (i, j) in ((1, 1), (-1, 1), (1, -1), (-1, -1)): put(x + i, gy + 1, z + j, full('chiseled_stone_bricks')); put(x + i, gy + 2, z + j, LANTERN)
    ry = gy + 4                                                             # 屋根：軒は1マス出し、芯からの距離で柱を積んで尖らせる
    for i in range(-3, 4):
        for j in range(-3, 4):
            d = _m.hypot(i, j)
            if d > 3.3: continue
            hh = int(round((3.3 - d) * 1.7))
            for yy in range(ry, ry + hh): put(x + i, yy, z + j, full('deepslate_tiles'))
            put(x + i, ry + hh, z + j, slab('deepslate_tiles') if d > 0.5 else full('deepslate_tiles'))
    put(x, ry + 7, z, fence('dark_oak_planks')); put(x, ry + 8, z, LANTERN)


# ---- 高床の茅葺き小屋：白っぽい木の柱と床、背と片脇は布、屋根は干し草を段に積んだ寄棟（軒は1マス出す） [南の港 11/04]
def hut(x0, z0, y, w=7, d=6, cloth='red_wool', bed=-4, open_side='s'):
    """(x0, z0) は床の北西の角、y は床の高さ。bed は柱を下ろす底の高さ。open_side の面は開けて、ほかの面は腰の高さまで板の手すり。"""
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            put(x, y, z, full('birch_planks'))
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            corner = x in (x0, x0 + w - 1) and z in (z0, z0 + d - 1)
            if corner or edge and ((x - x0) % 3 == 0 and z in (z0, z0 + d - 1) or (z - z0) % 3 == 0 and x in (x0, x0 + w - 1)):
                for yy in range(bed + 1, y): put(x, yy, z, log('stripped_birch_log'))
                for yy in range(y + 1, y + 4): put(x, yy, z, log('stripped_birch_log'))
            elif edge:
                side = 'n' if z == z0 else 's' if z == z0 + d - 1 else 'w' if x == x0 else 'e'
                if side == open_side: continue
                if side == 'n': put(x, y + 1, z, trap('birch_trapdoor', 's')); put(x, y + 2, z, {'s': 'trap', 't': cloth, 'f': 's'}); put(x, y + 3, z, {'s': 'trap', 't': cloth, 'f': 's'})
                else: put(x, y + 1, z, fence('birch_planks'))
    ry = y + 4
    k = -1
    while True:                                                             # 屋根：段ごとに1マス内へ。最初の段は軒として外へ1マス
        xa, xb, za, zb = x0 + k, x0 + w - 1 - k, z0 + k, z0 + d - 1 - k
        if xa > xb or za > zb: break
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                if k == -1 or x in (xa, xb) or z in (za, zb) or xb - xa <= 1 or zb - za <= 1:
                    put(x, ry + k + 1, z, full('hay_block_side', top='hay_block_top'))
        k += 1
    put(x0 + w // 2, y + 3, z0 + d // 2, HANG)


# ---- 旗の綱：2点の間に細い綱（上のトラップドア）を張り、1マスおきに色の布を下げる [南の港 11/04]
def bunting(a, b, y, colors=('red_wool', 'yellow_wool', 'light_blue_wool', 'white_wool'), sag=2):
    """a, b = (x, z)。綱は真ん中で sag だけ下がる。旗は綱の下のマスに縦の薄い布。"""
    import math as _m
    n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1])))
    f = 'n' if abs(b[0] - a[0]) >= abs(b[1] - a[1]) else 'e'
    for i in range(n + 1):
        t = i / max(1, n)
        x = int(round(a[0] + (b[0] - a[0]) * t)); z = int(round(a[1] + (b[1] - a[1]) * t))
        yy = y - int(round(sag * _m.sin(_m.pi * t)))
        if get(x, yy, z) is None: put(x, yy, z, trap('spruce_trapdoor', 'top'))
        if i % 2 == 1 and get(x, yy - 1, z) is None: put(x, yy - 1, z, {'s': 'trap', 't': colors[(i // 2) % len(colors)], 'f': f})


# ---- 帆船：長さ n の船体を z 方向に。腹は暗い木で太く、舳先は細く上がり、艫は一段高い楼。帆柱2本と白い大きな帆（赤い帯） [南の港 11/04]
def ship(x, z0, wy, n=32, wmax=5):
    """x は船の芯、z0 は舳先（北）の端、wy は水面の高さ。甲板は wy+3。"""
    import math as _m
    dk = wy + 3
    for k in range(n):
        t = k / (n - 1)
        hw = wmax * min(1.0, _m.sin(_m.pi * min(1.0, t * 1.15)) ** 0.6 + 0.15) if t < 0.95 else wmax * 0.7
        hw = max(1.0, hw if t > 0.15 else wmax * (t / 0.15) ** 0.7 + 0.6)
        z = z0 + k
        bow = max(0, int(round((0.18 - t) * 18))) if t < 0.18 else 0                  # 舳先は上がる
        stern = 3 if t > 0.82 else 0                                                   # 艫の楼
        top = dk + bow
        for i in range(-int(hw), int(hw) + 1):
            d = abs(i) / max(0.5, hw)
            bot = wy - 2 + int(round(d * d * 2))                                       # 腹は丸く
            for yy in range(bot, top + 1):
                shell = abs(i) == int(hw) or yy == bot
                if shell: put(x + i, yy, z, full('dark_oak_planks' if yy != dk - 1 else 'spruce_planks'))
                elif yy == top: put(x + i, yy, z, full('spruce_planks' if bow == 0 else 'dark_oak_planks'))
                else: put(x + i, yy, z, None) if yy > wy else put(x + i, yy, z, full('dark_oak_planks'))
            if abs(i) == int(hw):
                put(x + i, top + 1, z, fence('dark_oak_planks'))
        if stern:                                                                      # 艫の楼：甲板の上に3段、窓、上に手すり
            for i in range(-int(hw), int(hw) + 1):
                for yy in range(dk + 1, dk + stern + 1):
                    wall = abs(i) == int(hw) or k == n - 1 or k == int(0.82 * (n - 1)) + 1
                    if wall: put(x + i, yy, z, {'s': 'pane', 't': 'glass', 'ax': 'z' if abs(i) == int(hw) else 'x'} if yy == dk + 2 and (k + i) % 3 == 0 else full('dark_oak_planks'))
                put(x + i, dk + stern + 1, z, full('spruce_planks'))
                if abs(i) == int(hw) or k == n - 1: put(x + i, dk + stern + 2, z, fence('dark_oak_planks'))
    put(x, dk + 2 + 8, z0 + 1, fence('dark_oak_planks'))                              # 舳先の斜檣の先
    for (mz, mh, sw) in ((z0 + int(n * 0.36), 20, 6), (z0 + int(n * 0.64), 17, 5)):  # 帆柱と帆
        for yy in range(dk + 1, dk + mh + 1): put(x, yy, mz, log('dark_oak_log'))
        put(x, dk + mh + 1, mz, {'s': 'trap', 't': 'red_wool', 'f': 'e'})
        for (yt, half) in ((dk + mh - 1, sw), (dk + mh - 9, sw + 1)):
            for i in range(-half - 1, half + 2): put(x + i, yt, mz, log('dark_oak_log', 'x'))
            for yy in range(yt - 7, yt):
                for i in range(-half, half + 1):
                    c = 'red_wool' if yy == yt - 4 else 'white_wool'
                    put(x + i, yy, mz + 1, {'s': 'trap', 't': c, 'f': 'n'})
    put(x, dk + 1, z0 + n // 2, BARREL); put(x + 1, dk + 1, z0 + n // 2, BARREL); put(x - 1, dk + 1, z0 + n // 2 + 1, CRATE)


# ---- 依頼の掲示板：2本の柱に横木、板の面に紙（白樺のトラップドア）を散らし、上に小屋根とランタン [冒険者の街]
def notice_board(x, z, g, f='s'):
    """(x, z) は左の柱。f は板の面が向く向き。幅4。"""
    rx, rz = D[RIGHT[f]]
    for s in (0, 3):
        for y in range(g + 1, g + 5): put(x + rx * s, y, z + rz * s, log('dark_oak_log'))
    for s in (1, 2):
        for y in (g + 2, g + 3): put(x + rx * s, y, z + rz * s, full('spruce_planks'))
    fx, fz = D[f]
    for (s, y) in ((1, g + 2), (2, g + 3), (1, g + 3)):                    # 紙
        put(x + rx * s + fx, y, z + rz * s + fz, trap('birch_trapdoor', OPP[f]))
    for s in range(-1, 5):                                                 # 小屋根：板の上に階段を前後に
        put(x + rx * s, g + 5, z + rz * s, stair('dark_oak_planks', OPP[f]))
        put(x + rx * s - fx, g + 5, z + rz * s - fz, stair('dark_oak_planks', f))
    put(x + rx * 1, g + 1, z + rz * 1, slab('spruce_planks')); put(x + rx * 2, g + 1, z + rz * 2, slab('spruce_planks'))
    return [(x + rx * s, z + rz * s) for s in range(4)] + [(x + rx * s + fx, z + rz * s + fz) for s in (1, 2)]


# ---- 訓練の的：柵の柱に干し草の胴、くり抜いたカボチャの頭、横木の腕 [冒険者の街の訓練場]
def dummy(x, z, g, f='s'):
    rx, rz = D[RIGHT[f]]
    put(x, g + 1, z, fence('spruce_planks'))
    put(x, g + 2, z, HAY)
    put(x, g + 3, z, PUMPKIN)
    put(x + rx, g + 2, z + rz, fence('spruce_planks')); put(x - rx, g + 2, z - rz, fence('spruce_planks'))
    return [(x, z)]
