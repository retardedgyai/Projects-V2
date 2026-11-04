"""開拓大陸の見どころの部品（11/04）。地図（towns/frontier_map.py）の点と形の印を、区画を組む時にブロックで建てる。
藍「少し単調に感じる」「探検しがいのあるマップに」。決まりは rules/nature.md 76〜（探検の決まり）。

どれも (x, z, g, rng[, ...]) を取る。g は立つ地面の一番上のブロックの高さ。get_h(x, z) で周りの地面の高さを引く。
形は1つずつ物として組む（no-slop-props）：芯の材料1つ＋差し色1〜2、崩れや苔、足元にキノコ・シダ・花（rules 25〜27・52）。
"""
import math
from vox import put, get, full, log, stair, slab, trap, cross, fence, vine, water
import props as P

STONE = ('stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks')


def _col(x, z, y0, y1, b):
    for y in range(y0, y1 + 1): put(x, y, z, b)


def _ground_fill(x, z, g, get_h, b=None):
    """(x, z) の地面が g より低ければ、g まで土台を詰める（部品が浮かないように）。"""
    gh = get_h(x, z)
    if gh is None: return
    for y in range(gh + 1, g + 1): put(x, y, z, b or full('cobblestone'))


def _plants(x, z, g, rng, get_h, n=4, kinds=('fern', 'short_grass', 'brown_mushroom')):
    for _ in range(n):
        dx, dz = rng.randint(-2, 2), rng.randint(-2, 2)
        gh = get_h(x + dx, z + dz)
        if gh is not None and get(x + dx, gh + 1, z + dz) is None and abs(gh - g) <= 1:
            put(x + dx, gh + 1, z + dz, cross(rng.choice(kinds)))


def _mat(rng, a=STONE, w=(0.55, 0.3, 0.15)):
    r = rng.random(); s = 0
    for m, p in zip(a, w):
        s += p
        if r < s: return m
    return a[-1]


# ================================================================ 区域だけの大きな地形の建物
def sunken_tower(x, z, g, rng, get_h, wl):
    """霧沼の沈んだ塔：半径4の丸い石の塔が傾いて沈み、上は崩れて片側だけ高い。水面から上に 6〜14。ツタと苔。"""
    for dy in range(-3, 16):
        y = wl + dy
        lean = int(dy * 0.15)
        for a in range(0, 360, 10):
            px, pz = x + lean + int(round(math.cos(math.radians(a)) * 4)), z + int(round(math.sin(math.radians(a)) * 4))
            top = 14 - int(8 * (0.5 + 0.5 * math.cos(math.radians(a - 40))))                       # 崩れ：片側だけ高い
            if dy > top: continue
            if dy > 2 and a % 90 == 0 and dy % 5 == 3: continue                                        # 窓
            put(px, y, pz, full(_mat(rng)))
            if dy > 0 and rng.random() < 0.12: put(px + (1 if px > x else -1), y, pz, vine('w' if px > x else 'e'))
    for a in range(0, 360, 30):                                                                      # 落ちた石
        px, pz = x + int(round(math.cos(math.radians(a)) * 7)), z + int(round(math.sin(math.radians(a)) * 7))
        if rng.random() < 0.5: put(px, wl, pz, full('mossy_cobblestone')); put(px, wl - 1, pz, full('mossy_cobblestone'))


def torii_shrine(x, z, g, rng, get_h, f='s'):
    """春雷の社：岩塔のふもとの鳥居と小さな社、石灯籠2つ。朱（赤いコンクリート）を差し色に。"""
    dx, dz = P.D[f]; rx, rz = P.D[P.RIGHT[f]]
    for s_ in (-2, 2):                                                                                # 鳥居
        cx, cz = x + rx * s_, z + rz * s_
        _ground_fill(cx, cz, g, get_h)
        _col(cx, cz, g + 1, g + 5, log('stripped_mangrove_log') if False else full('red_concrete'))
    for s_ in range(-3, 4):
        cx, cz = x + rx * s_, z + rz * s_
        put(cx, g + 6, cz, full('dark_oak_planks') if abs(s_) < 3 else stair('dark_oak_planks', P.RIGHT[f] if s_ > 0 else P.OPP[P.RIGHT[f]], 'top'))
        put(cx, g + 4, cz, full('red_concrete') if abs(s_) < 3 else slab('red_concrete'))       # 貫（ぬき）：柱を突き抜ける横木
        if s_ == 0: put(cx, g + 5, cz, full('dark_oak_planks'))                                   # 額束
    bx, bz = x - dx * 7, z - dz * 7                                                                   # 社：鳥居の奥
    for i in range(-2, 3):
        for j in range(-2, 3):
            _ground_fill(bx + rx * i + dx * j, bz + rz * i + dz * j, g, get_h)
            put(bx + rx * i + dx * j, g + 1, bz + rz * i + dz * j, full('polished_andesite'))
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            cx, cz = bx + rx * i + dx * j, bz + rz * i + dz * j
            if abs(i) == 1 and abs(j) == 1: _col(cx, cz, g + 2, g + 4, log('dark_oak_log'))
            elif (i, j) != (0, 1): put(cx, g + 2, cz, full('dark_oak_planks')); put(cx, g + 3, cz, full('dark_oak_planks') if j != 1 else trap('dark_oak_trapdoor', f))
    for i in range(-2, 3):                                                                            # 屋根：反った瓦
        for j in range(-2, 3):
            cx, cz = bx + rx * i + dx * j, bz + rz * i + dz * j
            put(cx, g + 5, cz, slab('deepslate_tiles') if abs(i) == 2 or abs(j) == 2 else full('deepslate_tiles'))
    put(bx, g + 6, bz, slab('deepslate_tiles'))
    for s_ in (-4, 4):                                                                                # 石灯籠
        lx, lz = x + rx * s_ - dx * 2, z + rz * s_ - dz * 2
        _ground_fill(lx, lz, g, get_h)
        put(lx, g + 1, lz, P.wall('stone_bricks')); put(lx, g + 2, lz, full('chiseled_stone_bricks')); put(lx, g + 3, lz, P.LANTERN)
    _plants(x, z, g, rng, get_h, 6, ('pink_petals', 'fern', 'short_grass'))


def stone_ring(x, z, g, rng, get_h, r=9):
    """羊飼いの丸丘の石の輪：丘の頂に、高さ3〜5の石柱を10本、輪に。2〜3本は倒れて、横木（まぐさ）を3か所に。"""
    n = 10; tops = []
    for k in range(n):
        a = 2 * math.pi * k / n
        px, pz = x + int(round(math.cos(a) * r)), z + int(round(math.sin(a) * r))
        gh = get_h(px, pz)
        if gh is None: continue
        if rng.random() < 0.25:                                                                       # 倒れた柱
            for t in range(3):
                put(px + int(round(math.cos(a) * t)), gh + 1, pz + int(round(math.sin(a) * t)), log('stripped_birch_log', 'x') if False else full('andesite'))
            continue
        hh = rng.randint(3, 5)
        for y in range(gh + 1, gh + hh + 1): put(px, y, pz, full('andesite' if rng.random() < 0.7 else 'mossy_cobblestone'))
        tops.append((px, pz, gh + hh))
    for (a_, b_) in zip(tops[::3], tops[1::3]):                                                       # まぐさ
        if math.hypot(a_[0] - b_[0], a_[1] - b_[1]) <= 7:
            y = max(a_[2], b_[2]) + 1
            for t in range(0, 11):
                px = int(round(a_[0] + (b_[0] - a_[0]) * t / 10)); pz = int(round(a_[1] + (b_[1] - a_[1]) * t / 10))
                put(px, y, pz, full('polished_andesite'))
    put(x, g + 1, z, full('chiseled_stone_bricks')); put(x, g + 2, z, P.LANTERN)                    # 真ん中の祭壇


def rock_arch(x, z, g, rng, get_h, span=14, hh=14, axis='x'):
    """風穴の大アーチ：風で削られた赤い岩のアーチ。脚は太く（幅5〜7）根元が広がり、上は薄く（厚さ3〜5）、
    輪郭は1マスずつ揺らす。層の縞（テラコッタと花崗岩）を横に。半円の帯にしない（11/04 見本の場で虹に見えた）。"""
    from vox import vnoise
    for t in range(-span - 4, span + 5):
        u = t / float(span)
        ys = hh * max(0.0, 1 - abs(u) ** 2.2) if abs(u) <= 1 else 0
        leg = abs(u) > 0.72
        thick = int(3 + 2 * vnoise(x + t, z, 3, 81) + (4 if leg else 0) * min(1.0, (abs(u) - 0.72) * 4))
        wid = int(2 + 2 * vnoise(x + t, z, 4, 82) + (2 if leg else 0))
        for w in range(-wid, wid + 1):
            px, pz = (x + t, z + w) if axis == 'x' else (x + w, z + t)
            gh = get_h(px, pz)
            if gh is None: continue
            lo = gh + 1 if (leg or abs(u) > 1) else g + int(ys)
            hi = g + int(ys) + thick - int(abs(w) * 0.6) + int(2 * vnoise(px, pz, 2, 83))
            if abs(u) > 1: hi = gh + max(0, int((span + 4 - abs(t)) * 1.5) - abs(w))       # 根元の広がり
            for y in range(lo, hi + 1):
                put(px, y, pz, full('terracotta' if ((y + int(2 * vnoise(px, pz, 6, 84))) // 3) % 2 else 'granite'))


def snow_fort(x, z, g, rng, get_h, r=11):
    """雪原の砦：丘の上の四角い石の砦。四隅に塔、壁は欠けて雪が積もる。門は南。"""
    for i in range(-r, r + 1):
        for j in range(-r, r + 1):
            edge = max(abs(i), abs(j)) == r
            if not edge: continue
            px, pz = x + i, z + j
            gh = get_h(px, pz)
            if gh is None: continue
            corner = abs(i) == r and abs(j) == r
            if j == r and abs(i) <= 1: continue                                                       # 門
            top = g + (9 if corner else 5) - (rng.randint(0, 3) if not corner and rng.random() < 0.3 else 0)
            for y in range(gh + 1, top + 1): put(px, y, pz, full(_mat(rng, ('stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks'), (0.6, 0.25, 0.15))))
            put(px, top + 1, pz, {'s': 'carpet', 't': 'snow'})
            if corner:
                for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    for y in range(gh + 1, top + 1): put(px + a, y, pz + b, full('stone_bricks'))
                    put(px + a, top + 1, pz + b, P.wall('stone_bricks'))
    put(x, g + 1, z, full('chiseled_stone_bricks')); put(x, g + 2, z, P.LANTERN)


def waterfall(x, z, top, bottom):
    """滝：段の縁から滝壺まで、幅3の水の柱。"""
    for dx in (-1, 0, 1):
        for y in range(bottom, top + 1): put(x + dx, y, z, water())


def lava_lake(cells_y):
    for (x, z, y) in cells_y: put(x, y, z, full('lava_still'))


# ================================================================ 名所（区域の型ごとに作りを変える）
def ruin(x, z, g, rng, get_h, style):
    """名所の廃墟：L字の崩れた壁（高さ1〜4）、真ん中に焚き火の跡か宝箱、足元に草とキノコ。型ごとに材料を変える。"""
    mats = {'sakura': ('stone_bricks', 'mossy_stone_bricks', 'cherry_planks'), 'marsh': ('mud_bricks', 'mossy_cobblestone', 'spruce_planks'),
            'meadow': ('cobblestone', 'mossy_cobblestone', 'oak_planks'), 'forest': ('cobblestone', 'mossy_cobblestone', 'spruce_planks'),
            'cliffs': ('granite', 'terracotta', 'oak_planks'), 'highland': ('andesite', 'cobblestone', 'spruce_planks'),
            'snow': ('stone_bricks', 'cracked_stone_bricks', 'spruce_planks'), 'volcano': ('blackstone', 'polished_blackstone_bricks', 'crimson_planks')}
    a, b, wood = mats.get(style, mats['meadow'])
    L = rng.randint(4, 6)
    for i in range(L):
        for (px, pz) in ((x + i, z), (x, z + i)):
            gh = get_h(px, pz)
            if gh is None: continue
            hh = max(1, 4 - abs(i - 1) - rng.randint(0, 2))
            for y in range(gh + 1, gh + hh + 1): put(px, y, pz, full(a if rng.random() < 0.6 else b))
            if rng.random() < 0.3: put(px, gh + hh + 1, pz, slab(a))
    put(x + 2, g + 1, z + 2, full('campfire_log') if False else log(wood.replace('_planks', '_log') if 'cherry' not in wood and 'crimson' not in wood else 'oak_log', 'x'))
    put(x + 3, g + 1, z + 1, P.BARREL)
    _plants(x + 2, z + 2, g, rng, get_h, 5)


def cave_mouth(x, z, g, rng, get_h, f, size=3):
    """斜面の洞窟の入口：斜面を幅 size×高さ4 で 8 マス掘り、入口を苔の丸石の枠と坑木（原木の柱と梁）で囲む。"""
    dx, dz = P.D[f]; rx, rz = P.D[P.RIGHT[f]]
    for k in range(0, 9):
        for s_ in range(-(size // 2), size // 2 + 1):
            px, pz = x - dx * k + rx * s_, z - dz * k + rz * s_
            for y in range(g + 1, g + 5): put(px, y, pz, None)
            put(px, g, pz, full('gravel' if rng.random() < 0.4 else 'coarse_dirt'))
        if k in (1, 5):                                                                              # 坑木
            for s_ in (-(size // 2) - 0, size // 2):
                px, pz = x - dx * k + rx * s_, z - dz * k + rz * s_
                _col(px, pz, g + 1, g + 3, log('spruce_log'))
            for s_ in range(-(size // 2), size // 2 + 1):
                put(x - dx * k + rx * s_, g + 4, z - dz * k + rz * s_, log('spruce_log', 'x' if rx else 'z'))
    put(x - dx * 1, g + 3, z - dz * 1, P.HANG)
    for s_ in range(-(size // 2) - 1, size // 2 + 2):                                              # 入口の枠
        px, pz = x + rx * s_, z + rz * s_
        if abs(s_) == size // 2 + 1:
            for y in range(g + 1, g + 5): put(px, y, pz, full('mossy_cobblestone'))
        put(px, g + 5, pz, full('mossy_cobblestone' if rng.random() < 0.6 else 'cobblestone'))


def dungeon_gate(x, z, g, rng, get_h, f, style):
    """洞窟のダンジョンの入口：洞窟の口（幅5）の前に、崩れた石の門と柱2本、灯り2つ、足元に骨か旗。"""
    cave_mouth(x, z, g, rng, get_h, f, size=5)
    dx, dz = P.D[f]; rx, rz = P.D[P.RIGHT[f]]
    a = 'blackstone' if style == 'volcano' else 'deepslate_bricks' if style in ('snow', 'highland') else 'stone_bricks'
    for s_ in (-4, 4):
        px, pz = x + dx * 2 + rx * s_, z + dz * 2 + rz * s_
        _ground_fill(px, pz, g, get_h)
        _col(px, pz, g + 1, g + 5 - (1 if s_ > 0 else 0), full(a))
        put(px, g + 6 - (1 if s_ > 0 else 0), pz, P.LANTERN)
    put(x + dx * 3, g + 1, z + dz * 3, full('bone_block') if False else cross('fern'))


def waypoint(x, z, g, rng, get_h):
    """近道の地点：3×3 の石の台に、高さ5の石柱、頭に光る石（遠くから見える）と小さな屋根。"""
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            _ground_fill(x + i, z + j, g, get_h); put(x + i, g + 1, z + j, full('polished_andesite' if (i + j) % 2 else 'stone_bricks'))
    _col(x, z, g + 2, g + 5, full('chiseled_stone_bricks')); put(x, g + 6, z, full('sea_lantern'))
    for (i, j, f) in ((1, 0, 'w'), (-1, 0, 'e'), (0, 1, 'n'), (0, -1, 's')): put(x + i, g + 6, z + j, stair('stone_bricks', f, 'top'))
    put(x, g + 7, z, slab('stone_bricks'))


def secret(x, z, g, rng, get_h, style):
    """集める秘密：区域ごとに同じ形の小さな置物（台に乗せ、足元に花）。"""
    _ground_fill(x, z, g, get_h)
    if style == 'sakura':                                                                            # 石灯籠
        put(x, g + 1, z, P.wall('stone_bricks')); put(x, g + 2, z, full('chiseled_stone_bricks')); put(x, g + 3, z, P.LANTERN)
    elif style == 'marsh':                                                                           # 古い錨（鎖と金床）
        put(x, g + 1, z, {'s': 'anvil', 't': 'anvil'}); put(x, g + 2, z, {'s': 'chain', 't': 'iron_chain'})
    elif style == 'meadow':                                                                          # 羊の石像
        put(x, g + 1, z, full('white_wool')); put(x + 1, g + 1, z, full('white_wool')); put(x + 1, g + 2, z, full('white_wool')) if False else put(x + 2, g + 1, z, stair('stone', 'e'))
    elif style == 'forest':                                                                          # 木彫りの梟
        put(x, g + 1, z, log('oak_log')); put(x, g + 2, z, full('carved_pumpkin') if False else log('stripped_oak_log')); put(x, g + 3, z, slab('oak_planks'))
    elif style == 'snow':                                                                            # 凍った鐘
        put(x, g + 1, z, full('packed_ice')); put(x, g + 2, z, full('blue_ice')); put(x, g + 3, z, P.LANTERN)
    elif style == 'volcano':
        put(x, g + 1, z, full('obsidian')); put(x, g + 2, z, full('crying_obsidian'))
    else:                                                                                            # 牙の石・羽の碑
        put(x, g + 1, z, full('calcite')); put(x, g + 2, z, P.wall('diorite')); put(x, g + 3, z, fence('birch_planks') if False else P.wall('diorite'))
    _plants(x, z, g, rng, get_h, 3, ('cornflower', 'oxeye_daisy', 'fern'))


def small_find(x, z, g, rng, get_h, style):
    """小さな見つけ物（地図に出さない）：4種からひとつ。宝箱の代わりの樽と焚き火の跡、苔の岩と花、古い墓石、落ちた荷。"""
    k = rng.randint(0, 3)
    _ground_fill(x, z, g, get_h)
    if k == 0:
        put(x, g + 1, z, P.BARREL); put(x + 1, g + 1, z, slab('spruce_planks')); put(x - 1, g + 1, z + 1, cross('brown_mushroom'))
    elif k == 1:
        put(x, g + 1, z, full('mossy_cobblestone')); put(x + 1, g + 1, z, stair('cobblestone', 'w')); put(x, g + 2, z, cross('cornflower' if style != 'snow' else 'fern'))
    elif k == 2:
        put(x, g + 1, z, P.wall('mossy_cobblestone')); put(x, g + 2, z, slab('stone_bricks')); put(x + 1, g + 1, z, cross('poppy'))
    else:
        put(x, g + 1, z, P.CRATE); put(x + 1, g + 1, z, P.HAY); put(x, g + 1, z + 1, trap('spruce_trapdoor', 'bottom'))


def boss_ring(x, z, g, rng, get_h, style):
    """ボスの広場：直径 22 の円をならし、縁に崩れた柱を8本、真ん中に割れた石の床。"""
    for i in range(-11, 12):
        for j in range(-11, 12):
            d = math.hypot(i, j)
            if d > 11: continue
            gh = get_h(x + i, z + j)
            if gh is None: continue
            for y in range(gh + 1, g + 1): put(x + i, y, z + j, full('cobblestone'))
            for y in range(g + 1, gh + 1): put(x + i, y, z + j, None)
            if d < 6 and rng.random() < 0.5: put(x + i, g, z + j, full('cracked_stone_bricks' if style != 'volcano' else 'polished_blackstone_bricks'))
    for k in range(8):
        a = 2 * math.pi * k / 8
        px, pz = x + int(round(math.cos(a) * 11)), z + int(round(math.sin(a) * 11))
        hh = rng.randint(2, 6)
        _col(px, pz, g + 1, g + hh, full('stone_bricks' if style != 'volcano' else 'blackstone'))
        if hh >= 5: put(px, g + hh + 1, pz, P.LANTERN)
