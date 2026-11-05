"""町を組む部品集。地形 → 段 → 家 → 道 → 地面 → 構造物 → 小物 → 木と草 → 検査 → 書き出し、の順に呼ぶ。

使い方は scripts/buildkit/README.md。例は towns/cliff_town.py（崖の上の町）。
決まりの出どころ：design/shelves/build.md、~/ProjectS/refs/build/（らーなろっその記録）。

座標：x=東、z=南、y=上。地面の一番上のブロックの高さを H[(x, z)]、その場所の種類を KIND[(x, z)] に持つ。
種類（KIND）：
  nat 自然の地面 / river 川 / yard 家の庭 / lane・lane_edge 小道 / plaza 広場 / hall 館の前の段 /
  stairs・landing・stairs_wall 大階段 / cliffstair 崖を下りる段 / harbor 板の歩道 / garden 畑・小屋の下 /
  deck 柱の桟敷 / bridge 橋 / stream・brook 沢
"""
import json, math, os, random
from collections import deque
from vox import put, get, full, log, stair, slab, trap, cross, fence, door, vine, water, path, grass, write, vnoise, _h, W, LANTERN
import props as P
import trees as TR

KIT = os.path.dirname(os.path.abspath(__file__))
HOUSES = os.path.join(os.path.dirname(KIT), 'houses')
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

# 家の色違い（ブロックの差し替え）
RED = {'bricks': 'mangrove_planks', 'granite': 'mangrove_planks', 'oak_planks': 'dark_oak_planks'}
BROWN = {'bricks': 'spruce_planks', 'granite': 'dark_oak_planks', 'birch_planks': 'calcite', 'oak_planks': 'dark_oak_planks'}
SLATE = {'bricks': 'deepslate_tiles', 'granite': 'deepslate_bricks', 'birch_planks': 'packed_mud', 'oak_planks': 'spruce_planks'}
WHITE = {'birch_planks': 'calcite'}

ROT = {0: lambda x, z: (x, z), 90: lambda x, z: (-z, x), 180: lambda x, z: (-x, -z), 270: lambda x, z: (z, -x)}
FROT = {90: {'n': 'e', 'e': 's', 's': 'w', 'w': 'n'}}
FROT[180] = {k: FROT[90][v] for k, v in FROT[90].items()}
FROT[270] = {k: FROT[90][v] for k, v in FROT[180].items()}
def rot_block(b, r):
    if r == 0: return b
    b = dict(b)
    for k in ('f', 'ff'):
        if b.get(k) in FROT[r]: b[k] = FROT[r][b[k]]
    if r in (90, 270):
        for k in ('axis', 'ax'):
            if b.get(k) in ('x', 'z'): b[k] = 'z' if b[k] == 'x' else 'x'
    return b

STRATA = ['stone', 'stone', 'andesite', 'stone', 'tuff', 'stone', 'cobblestone', 'andesite', 'granite', 'stone', 'diorite', 'stone', 'tuff', 'dripstone_block', 'stone']
WALLED = ('yard', 'plaza', 'lane', 'lane_edge', 'hall', 'stairs', 'stairs_wall', 'landing', 'cliffstair')
BLOCKING = ('full', 'fence', 'wall', 'slab', 'stair', 'pot', 'lantern', 'anvil', 'grindstone', 'pane')


class Town:
    def __init__(self, bounds, nat, in_water=None, seed=11):
        """bounds=(X0, X1, Z0, Z1)。nat(x, z) は自然の地面の高さ（小数でよい）。in_water(x, z) が真の所は川（底 -4、水面 -1）。"""
        self.X0, self.X1, self.Z0, self.Z1 = bounds
        self.nat = nat
        self.in_water = in_water or (lambda x, z: False)
        self.rng = random.Random(seed)
        self.PAD = {}; self.H = {}; self.KIND = {}
        self.FOOT = []; self.HOUSE_CELLS = set()
        self.dist = {}
        self.DECK = {}; self.BRIDGE = {}; self.PIERS = set(); self.HARBOR = set()
        self.stream_level = {}                                                   # (x, z) -> 沢の水面

    # ================================================================ 家
    def house(self, name, r, at, y=None, swap=None, mirror=False, label=''):
        """houses/<name>.json の家を置く。at は基準点の行き先、y は床の高さ（省略で自然の地面）。庭（家の周り1マス）を段にする。"""
        d = json.load(open(os.path.join(HOUSES, name + '.json'), encoding='utf-8'))
        if y is None: y = self.ny(*at)
        af = tuple(d['anchor']); ax, az = ROT[r](*af)
        xs, zs = [], []
        for x, yy, z, p in d['blocks']:
            b = d['pal'][p]
            if mirror:
                x = 2 * af[0] - x
                b = dict(b)
                for k in ('f', 'ff'):
                    if b.get(k) in ('e', 'w'): b[k] = 'w' if b[k] == 'e' else 'e'
            if swap:
                b = dict(b)
                for k in ('t', 'top', 'bot'):
                    if b.get(k) in swap: b[k] = swap[b[k]]
            rx, rz = ROT[r](x, z)
            X, Z = rx - ax + at[0], rz - az + at[1]
            put(X, yy + y, Z, rot_block(b, r)); xs.append(X); zs.append(Z)
            self.HOUSE_CELLS.add((X, Z))
        rect = (min(xs), max(xs), min(zs), max(zs))
        for (n2, r2, _) in self.FOOT:
            if not (rect[1] < r2[0] or r2[1] < rect[0] or rect[3] < r2[2] or r2[3] < rect[2]):
                print('家が重なる', label, rect, n2, r2)
        self.FOOT.append((label or name, rect, y))
        self.pad(rect[0] - 1, rect[1] + 1, rect[2] - 1, rect[3] + 1, y, 'yard')
        return rect, y

    def ny(self, x, z): return int(round(self.nat(x, z)))

    # ================================================================ 段（パッド）と道
    def pad(self, x0, x1, z0, z1, y, kind, keep=False):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if keep and (x, z) in self.PAD: continue
                self.PAD[(x, z)] = (y, kind)

    def pad_disc(self, cx, cz, r, y, kind):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            for z in range(int(cz - r) - 1, int(cz + r) + 2):
                if math.hypot(x - cx, z - cz) <= r + 0.3: self.PAD[(x, z)] = (y, kind)

    def street(self, points, w, kind, keep_kinds=('plaza', 'hall', 'stairs', 'stairs_wall', 'landing', 'yard')):
        """points=(x, z, y) を結ぶ幅 w の道。高さが変わる所は後で階段になる。真ん中は kind、縁は kind_edge。"""
        for (ax, az, ay), (bx, bz, by) in zip(points, points[1:]):
            n = int(max(abs(bx - ax), abs(bz - az))) * 2 + 1
            for i in range(n + 1):
                t = i / n
                cx, cz = ax + (bx - ax) * t, az + (bz - az) * t
                y = round(ay + (by - ay) * t)
                for dx in range(-w, w + 1):
                    for dz in range(-w, w + 1):
                        x, z = int(round(cx + dx)), int(round(cz + dz))
                        if (x, z) in self.HOUSE_CELLS: continue
                        cur = self.PAD.get((x, z))
                        if cur and cur[1] in keep_kinds: continue
                        inner = abs(dx) < w and abs(dz) < w
                        if cur and cur[1] == kind and not inner: continue
                        if cur and cur[1] in (kind, kind + '_edge') and cur[0] != y: continue
                        self.PAD[(x, z)] = (y, kind if inner else kind + '_edge')

    def lane(self, pts, w=2):
        """小道：y を None にすると自然の地面の高さ（等高線をなぞる）。"""
        self.street([(x, z, self.ny(x, z) if y is None else y) for (x, z, y) in pts], w, 'lane')

    def grand_stair(self, x_half, z_start, y_from, y_to, landing_after=4, top_kind='hall'):
        """北（-z）へ上る大階段。y_from の段の北の端 z_start から。landing_after 段上った所に2列の踊り場。踊り場の z を返す。"""
        steps = []; y = y_from; z = z_start
        while y < y_to:
            z -= 1
            if y == y_from + landing_after and not any(s[2] == 'land' for s in steps):
                steps.append((z, y, 'land')); z -= 1; steps.append((z, y, 'land')); z -= 1
            y += 1; steps.append((z, y, 'step'))
        for (z, y, k) in steps:
            for x in range(-x_half, x_half + 1): self.PAD[(x, z)] = (y, 'stairs' if k == 'step' else 'landing')
            for x in (-x_half - 1, x_half + 1): self.PAD[(x, z)] = (y + 1, 'stairs_wall')
        self.stair_top_z = min(s[0] for s in steps)
        return [s[0] for s in steps if s[2] == 'land'][0]

    def cliff_descent(self, x_start, z_start, y_start, sgn, target_z):
        """崖の面に沿って下りる幅2の段。sgn=±1 の向きへ、2マスで1段下がり、z は target_z(x) へ1つずつ寄せる。"""
        out = []; x = x_start; y = y_start; z = z_start
        while y > 0:
            out.append((x, z, y))
            tz = target_z(x)
            if z < tz: z += 1
            elif z > tz: z -= 1
            x += sgn
            if (x + 100) % 2 == 0: y -= 1
        for (x, z, y) in out:
            for dz in (0, 1): self.PAD[(x, z + dz)] = (y, 'cliffstair')
        return out

    def harbor_walk(self, xs, edge_z, skip=lambda x, z: False):
        """川の縁の板の歩道（2列、高さ 0）。edge_z(x) は歩道の川側の列の z。"""
        for x in xs:
            zr = edge_z(x)
            for z in (zr - 1, zr):
                if (x, z) in self.PAD or skip(x, z): continue
                self.PAD[(x, z)] = (0, 'harbor'); self.HARBOR.add((x, z))

    # ================================================================ 高さを決める
    def resolve(self, blend_kinds=('yard', 'lane', 'lane_edge', 'hall'), blend=6, no_blend=lambda x, z: False):
        """段・川・自然の地面から H と KIND を決め、段の際を blend マスかけて元の地面へなじませる（縁は揺らぎで崩す）。"""
        H, KIND = self.H, self.KIND
        for x in range(self.X0, self.X1 + 1):
            for z in range(self.Z0, self.Z1 + 1):
                if (x, z) in self.PAD: H[(x, z)], KIND[(x, z)] = self.PAD[(x, z)]; continue
                if self.in_water(x, z): H[(x, z)] = -4; KIND[(x, z)] = 'river'; continue
                KIND[(x, z)] = 'nat'; H[(x, z)] = int(math.floor(self.nat(x, z) + 0.5))
        dist = {}; src = {}; q = deque()
        for (x, z), k in KIND.items():
            if k in blend_kinds: dist[(x, z)] = 0; src[(x, z)] = H[(x, z)]; q.append((x, z))
        while q:
            c = q.popleft()
            if dist[c] >= blend: continue
            for dx, dz in N4:
                n = (c[0] + dx, c[1] + dz)
                if n in dist or KIND.get(n) != 'nat' or no_blend(*n): continue
                dist[n] = dist[c] + 1; src[n] = src[c]; q.append(n)
        for (x, z), d in dist.items():
            if d == 0: continue
            t = min(1.0, max(0.0, d / float(blend) + (vnoise(x, z, 5, 44) - 0.5) * 0.6))
            s_ = t * t * (3 - 2 * t)
            H[(x, z)] = int(math.floor(src[(x, z)] * (1 - s_) + H[(x, z)] * s_ + 0.5))
        self.dist = dist

    def h(self, x, z): return self.H.get((x, z), -99)

    # ================================================================ 地面を積む：段の擁壁は石積み、崖は岩の層 [地形3]
    def rock_at(self, x, y, z):
        if y < -1: return 'deepslate' if _h(x, y, z) < 0.6 else 'cobbled_deepslate'
        return STRATA[int(y + vnoise(x, z, 9, 2) * 3 + vnoise(x, z, 3, 8) * 0.8) % len(STRATA)]

    def masonry(self, x, y, z, low):
        r = _h(x, y * 7, z)
        if y <= low + 1: return 'cobblestone' if r < 0.5 else 'mossy_cobblestone'
        return 'stone_bricks' if r < 0.62 else 'mossy_stone_bricks' if r < 0.82 else 'cracked_stone_bricks'

    def steep_top(self, x, z):
        """急な所の上面：岩・安山岩・苔・草を塊で（1マスずつ混ぜると斑点になる）。"""
        n = vnoise(x, z, 5, 67)
        return full('stone') if n < 0.4 else full('andesite') if n < 0.55 else full('moss_block') if n < 0.72 else grass()

    def ground_top(self, x, z, k, g, sand=lambda x, z, g: False):
        r = _h(x, z, 41)
        if k == 'stairs_wall': return full('stone_bricks')
        if k in ('stairs', 'landing', 'cliffstair'): return full('stone_bricks') if r < 0.6 else full('cobblestone') if r < 0.85 else full('andesite')
        if k == 'lane_edge': return full('coarse_dirt') if r < 0.4 else path() if r < 0.7 else grass()
        if k == 'lane': return path() if r < 0.5 else full('cobblestone') if r < 0.7 else full('coarse_dirt') if r < 0.88 else full('gravel')
        if k == 'plaza': return full('stone_bricks') if r < 0.45 else full('polished_andesite') if r < 0.75 else full('chiseled_stone_bricks') if r < 0.8 else full('stone_bricks')
        if k == 'hall': return full('stone_bricks') if r < 0.5 else full('polished_andesite') if r < 0.8 else full('cobblestone')
        if k == 'harbor': return full('spruce_planks')
        if k == 'yard':
            n = vnoise(x, z, 4, 8)
            return full('coarse_dirt') if n < 0.28 else full('rooted_dirt') if n < 0.33 else grass()
        if k == 'river': return full('gravel') if r < 0.6 else full('sand') if r < 0.85 else full('clay')
        if k in ('stream', 'brook'): return full('gravel') if r < 0.5 else full('cobblestone') if r < 0.8 else full('mossy_cobblestone')
        if sand(x, z, g): return full('sand') if r < 0.7 else full('gravel')
        n = vnoise(x, z, 6, 12)
        if n < 0.1: return full('coarse_dirt')
        if n > 0.9: return full('podzol_side', top='podzol_top', bottom='dirt')
        return grass()

    def fill(self, sand=lambda x, z, g: False):
        """全マスの地面を積む。段が2以上落ちる所は石積み、自然の地面が深く落ちる所は岩の層、川と沢には水。"""
        H, KIND = self.H, self.KIND
        for (x, z), g in H.items():
            k = KIND[(x, z)]
            nbr = [self.h(x + dx, z + dz) for dx, dz in N4]
            low = min([n for n in nbr if n > -99] + [g])
            for y in range(min(low, g - 2), g + 1):
                if get(x, y, z) is not None: continue
                if y == g: b = self.steep_top(x, z) if k == 'nat' and g - low >= 2 else self.ground_top(x, z, k, g, sand)
                elif k in ('river', 'stream', 'brook'): b = full('gravel')
                elif k in WALLED and g - low >= 2: b = full(self.masonry(x, y, z, low))
                elif k == 'harbor': b = log('spruce_log') if x % 3 == 0 else None
                elif g - low < 2 and y >= g - 1: b = full('dirt')
                else: b = full(self.rock_at(x, y, z))
                if b: put(x, y, z, b)
            if k == 'harbor':
                for y in range(-3, 0):
                    if get(x, y, z) is None: put(x, y, z, water())
                put(x, -4, z, full('gravel'))
            if k == 'river':
                for y in range(g + 1, 0):
                    if get(x, y, z) is None: put(x, y, z, water())
            if k == 'stream':
                for y in range(g + 1, self.stream_level.get((x, z), -1) + 1):
                    if get(x, y, z) is None: put(x, y, z, water())
            if k == 'brook' and get(x, g + 1, z) is None: put(x, g + 1, z, water())

    def vines_on_cliffs(self, where, chance=0.35):
        """3段以上落ちる自然の地面の縁から、蔦を垂らす。where(x, z) が真の所だけ。"""
        for (x, z), g in list(self.H.items()):
            if self.KIND[(x, z)] != 'nat' or not where(x, z): continue
            for dx, dz, f in ((0, 1, 'n'), (0, -1, 's'), (1, 0, 'w'), (-1, 0, 'e')):
                n = (x + dx, z + dz)
                if self.h(*n) < g - 3 and _h(x, z, 71) < chance:
                    for y in range(g, g - 2 - int(_h(x, z, 72) * 5), -1):
                        if get(n[0], y, n[1]) is None and y > self.h(*n): put(n[0], y, n[1], vine(f))

    def steps(self):
        """段差を階段に：大階段は段そのものを階段に、小道・崖の段・歩道は一段下のマスの上に階段を足す。"""
        for (x, z), k in self.KIND.items():
            g = self.H[(x, z)]
            if k == 'stairs' and self.h(x, z + 1) == g - 1: put(x, g, z, stair('stone_bricks', 'n'))
            if k in ('lane', 'lane_edge', 'cliffstair', 'harbor'):
                for dx, dz, f in ((0, -1, 'n'), (0, 1, 's'), (1, 0, 'e'), (-1, 0, 'w')):
                    n = (x + dx, z + dz)
                    if self.KIND.get(n) in (k, 'lane', 'lane_edge', 'plaza', 'hall', 'cliffstair', 'harbor') and self.H[n] == g + 1 and get(x, g + 1, z) is None:
                        if k in ('cliffstair', 'harbor'): put(x, g + 1, z, stair('cobblestone', f))
                        else:                                                       # 小道の段：石のハーフ（半段）、材料を混ぜ、ところどころ階段
                            r = _h(x, z, 97)
                            t = 'cobblestone' if r < 0.4 else 'andesite' if r < 0.65 else 'stone_bricks' if r < 0.85 else 'mossy_cobblestone'
                            put(x, g + 1, z, slab(t) if r < 0.8 else stair(t, f))
                        break

    def top_y(self, x, z):
        y = 80
        while y > -8:
            b = get(x, y, z)
            if b and b['s'] in ('full', 'path', 'slab', 'stair') and 'leaves' not in b['t']: return y
            y -= 1
        return 0

    def free(self, x, y, z): return get(x, y, z) is None and (x, z) not in self.HOUSE_CELLS

    # ================================================================ 広場の皿 [冒険者の街 2:30〜4:21]
    def plaza_rim(self, cx, cz, r, y, open_cell=lambda x, z: False, pillar_every=5):
        """広場の縁：柵で囲み、落ちる側の壁に柱（灯り付き）と、間にトラップドアの出っ張り。"""
        for (x, z), (yy, k) in list(self.PAD.items()):
            if k != 'plaza' or yy != y: continue
            out = [(x + dx, z + dz) for dx, dz in N4 if self.KIND.get((x + dx, z + dz)) not in ('plaza', 'stairs', 'stairs_wall', 'landing') and self.h(x + dx, z + dz) < y - 1]
            if not out: continue
            if self.free(x, y + 1, z) and not open_cell(x, z): put(x, y + 1, z, fence('spruce_planks'))
            ox, oz = out[0][0] - x, out[0][1] - z; nx, nz = x + ox, z + oz
            if int(round(math.atan2(z - cz, x - cx) * r)) % pillar_every == 0:
                lo = self.h(nx, nz)
                for yy2 in range(max(lo + 1, -3), y + 1):
                    if get(nx, yy2, nz) is None or get(nx, yy2, nz)['s'] == 'water': put(nx, yy2, nz, full('stone_bricks' if yy2 < y else 'chiseled_stone_bricks'))
                put(nx, y + 1, nz, LANTERN)
            elif self.free(nx, y - 1, nz):
                put(nx, y - 1, nz, trap('spruce_trapdoor', {(1, 0): 'w', (-1, 0): 'e', (0, 1): 'n', (0, -1): 's'}[(ox, oz)]))

    def fire_pit(self, cx, cz, y):
        """広場の真ん中の大きな火：ネザーラックを3段のピラミッドに積み、段の上面すべてに火をつける（ネザーラックを火で隠す）。
        燃えるので木で囲わず、泥の輪、石レンガの輪、外に石レンガの階段の輪。平らな所に火を散らさない。
        [らーなろっそ 廃キャンプ 3:36「ネザーラックが見えると変なのでピラミッド状に隠す」「泥などを置いて石レンガで囲う」]"""
        for x in range(-4, 5):
            for z in range(-4, 5):
                d = max(abs(x), abs(z)); X, Z = cx + x, cz + z
                if d == 4: put(X, y + 1, Z, stair('stone_bricks', 'e' if x == -4 else 'w' if x == 4 else 's' if z == -4 else 'n'))
                elif d == 3: put(X, y + 1, Z, full('stone_bricks'))
                elif d == 2 and abs(x) + abs(z) == 4: put(X, y + 1, Z, full('mud'))          # 角は泥（丸く見せる）
                elif d == 2: put(X, y + 1, Z, full('netherrack')); put(X, y + 2, Z, cross('fire_0'))
                elif d == 1: put(X, y + 1, Z, full('netherrack')); put(X, y + 2, Z, full('netherrack')); put(X, y + 3, Z, cross('fire_0'))
                else:
                    for k in range(1, 4): put(X, y + k, Z, full('netherrack'))
                    put(X, y + 4, Z, cross('fire_0'))

    def floor_marks(self, cx, cz, y, ring=8, n=6):
        """広い床に、木の模様（3×3）を円く n 個。"""
        for i in range(n):
            a = i * 2 * math.pi / n + 0.3
            mx, mz = int(round(cx + ring * math.cos(a))), int(round(cz + ring * math.sin(a)))
            for x in range(mx - 1, mx + 2):
                for z in range(mz - 1, mz + 2):
                    if self.KIND.get((x, z)) == 'plaza' and get(x, y + 1, z) is None:
                        put(x, y, z, full('spruce_planks') if (x, z) != (mx, mz) else log('stripped_spruce_log'))

    def banner(self, x, z, ytop, f, n=8):
        """横木から下がる縦長の赤い旗（薄い布）。"""
        for y in range(ytop - n + 1, ytop + 1):
            put(x, y, z, {'s': 'trap', 't': 'yellow_wool' if y in (ytop - 1, ytop - n + 3) else 'red_wool', 'f': P.OPP[f]})
        put(x, ytop + 1, z, log('dark_oak_log', 'x' if f in 'ns' else 'z'))

    def banner_post(self, x, z, y, f='s', h=12):
        for yy in range(y + 1, y + h): put(x, yy, z, log('dark_oak_log'))
        put(x, y + h, z, LANTERN)
        fx, fz = P.D[f]
        put(x + fx, y + h - 1, z + fz, fence('dark_oak_planks')); self.banner(x + fx, z + fz, y + h - 2, f)

    def arch_gate(self, z, y, half=4):
        """大階段の踊り場の石のアーチ門。柱は2列で外へ厚く、足は前後へ広げ、上は濃い張り出しと小さな寄棟の笠（11/04 平たい箱に見えたので）。"""
        Z = (z, z - 1)
        for x in (-half - 1, -half, half, half + 1):
            for zz in Z:
                for yy in range(min(y + 1, self.h(x, zz) + 1), y + 8): put(x, yy, zz, full('stone_bricks' if _h(x, yy, zz) < 0.7 else 'mossy_stone_bricks'))   # 外の柱は地面から
            for zz, f in ((z + 1, 'n'), (z - 2, 's')):                          # 柱の足
                g = self.h(x, zz)
                if g <= y + 1 and get(x, g + 1, zz) is None: put(x, g + 1, zz, stair('stone_bricks', f))
        for x in range(-half - 1, half + 2):
            for zz in Z: put(x, y + 8, zz, full('chiseled_stone_bricks' if x == 0 else 'stone_bricks'))
            put(x, y + 8, z + 1, stair('deepslate_bricks', 'n', 'top')); put(x, y + 8, z - 2, stair('deepslate_bricks', 's', 'top'))   # 上の張り出し
            for zz in Z: put(x, y + 9, zz, full('deepslate_bricks'))
            put(x, y + 9, z + 1, stair('deepslate_bricks', 'n')); put(x, y + 9, z - 2, stair('deepslate_bricks', 's'))
            if abs(x) == half - 1:
                for zz in Z: put(x, y + 7, zz, stair('stone_bricks', 'w' if x < 0 else 'e', 'top'))
        for x in (-half - 2, half + 2):                                          # 笠の妻：横へも1マス張り出す
            for zz in (z + 1,) + Z + (z - 2,): put(x, y + 9, zz, stair('deepslate_bricks', 'e' if x < 0 else 'w'))
        for x in range(-half - 1, half + 2):
            for zz in Z: put(x, y + 10, zz, slab('deepslate_bricks'))
        put(0, y + 10, z, full('chiseled_stone_bricks')); put(0, y + 11, z, LANTERN)
        put(0, y + 7, z, P.HANG)

    def deck(self, x0, x1, z0, z1, y, keep_open=('stairs', 'stairs_wall', 'landing')):
        """柱で支えた木の桟敷：自然の地面より上の所に板を張り、3マスおきに暗い柱を地面まで。縁は柵。"""
        cells = []
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.KIND.get((x, z)) != 'nat' or self.H[(x, z)] >= y: continue
                cells.append((x, z)); put(x, y, z, full('spruce_planks')); self.KIND[(x, z)] = 'deck'; self.DECK[(x, z)] = y
                if x % 3 == 0 and z % 3 == 0 or x in (x0, x1) and z % 2 == 0:
                    for yy in range(self.H[(x, z)] + 1, y): put(x, yy, z, log('dark_oak_log'))
        for (x, z) in cells:
            if any((x + dx, z + dz) not in self.DECK and self.KIND.get((x + dx, z + dz)) not in keep_open and self.h(x + dx, z + dz) < y for dx, dz in N4):
                if self.free(x, y + 1, z): put(x, y + 1, z, fence('spruce_planks'))
        return cells

    # ================================================================ 橋と壁
    def stone_bridge(self, bx, za, zb, axis='z', base=-1):
        """川を渡る石のアーチ橋（幅3）。axis='z' なら x=bx で z を za→zb、'x' なら z=bx で x を za→zb。base は水面の高さ。
        両端に柱と灯り。橋の床の高さ（終わりの端）を返す。"""
        P_ = (lambda u, v, y, b: put(u, y, v, b)) if axis == 'z' else (lambda u, v, y, b: put(v, y, u, b))
        key = (lambda u, v: (u, v)) if axis == 'z' else (lambda u, v: (v, u))
        f_lo, f_hi = ('s', 'n') if axis == 'z' else ('e', 'w')
        mid = (za + zb) / 2; half = (zb - za) / 2; ys = {}
        for v in range(za, zb + 1):
            y = base + 2 if abs(v - mid) > half - 2 else base + 3
            ys[v] = y
            dep = int(round(math.sqrt(max(0, half * half - (v - mid) ** 2)) * 0.5))
            for u in range(bx - 1, bx + 2):
                P_(u, v, y, full('stone_bricks' if u == bx else 'polished_andesite')); self.BRIDGE[key(u, v)] = y
                if abs(v - mid) > half - 1.5:
                    for yy in range(base - 2, y): P_(u, v, yy, full('stone_bricks'))
                elif dep >= 1: P_(u, v, y - 1, stair('stone_bricks', f_lo if v < mid else f_hi, 'top'))
            for u in (bx - 2, bx + 2):
                P_(u, v, y, full('stone_bricks')); P_(u, v, y + 1, slab('stone_bricks'))
                if abs(v - mid) > half - 1.5:
                    for yy in range(base - 2, y): P_(u, v, yy, full('stone_bricks'))
        for (u, v) in [(bx - 2, za), (bx + 2, za), (bx - 2, zb), (bx + 2, zb)]:
            P_(u, v, ys[v] + 1, full('chiseled_stone_bricks')); P_(u, v, ys[v] + 2, LANTERN)
        return ys[zb]

    def ramp(self, cells_z, xs, y_from, y_to):
        """坂道：cells_z の順に y_from から y_to へ均した土の道（地面を削る・盛る）。"""
        n = max(1, len(cells_z) - 1)
        for i, z in enumerate(cells_z):
            y = int(round(y_from + (y_to - y_from) * i / n))
            for x in xs:
                if (x, z) in self.HOUSE_CELLS or (x, z) not in self.H: continue
                for yy in range(y + 1, self.H[(x, z)] + 3): put(x, yy, z, None)
                for yy in range(min(self.H[(x, z)], y) - 1, y): put(x, yy, z, full('dirt'))
                put(x, y, z, path() if (x + z) % 3 else full('coarse_dirt')); self.H[(x, z)] = y; self.KIND[(x, z)] = 'lane'

    def timber_bridge(self, x0, x1, z, ya, yb):
        """谷に架ける長い木の橋（x 方向、幅3）。6マスおきに暗い柱を谷底まで下ろし、柱の頭に灯り。 [冒険者の街 15:24]"""
        for x in range(x0, x1 + 1):
            y = round(ya + (yb - ya) * (x - x0) / max(1, x1 - x0))
            for zz in (z - 1, z, z + 1):
                put(x, y, zz, full('birch_planks' if zz == z else 'oak_planks')); self.BRIDGE[(x, zz)] = y
                if zz != z and get(x, y + 1, zz) is None: put(x, y + 1, zz, fence('spruce_planks'))
            if (x - x0) % 6 == 0:
                for zz in (z - 1, z + 1):
                    for yy in range(self.h(x, zz) + 1, y): put(x, yy, zz, log('dark_oak_log'))
                    put(x, y + 1, zz, log('dark_oak_log')); put(x, y + 2, zz, LANTERN)
            if self.KIND.get((x, z)) == 'nat': self.KIND[(x, z)] = 'bridge'

    def curtain_wall(self, xs, z_of, tower_every=12):
        """稜線に沿う城壁：石積み2厚・高さ4、上は凸凹、tower_every マスおきに塔。"""
        for x in xs:
            z = z_of(x)
            if self.KIND.get((x, z)) != 'nat': continue
            g = self.H[(x, z)]
            for dz in (0, 1):
                for y in range(g + 1, g + 5): put(x, y, z + dz, full(self.masonry(x, y, z + dz, g)))
            put(x, g + 5, z, full('stone_bricks') if x % 2 == 0 else slab('stone_bricks'))
            if x % tower_every == 0:
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1, 2):
                        for y in range(self.h(x + dx, z + dz) + 1, g + 7): put(x + dx, y, z + dz, full('stone_bricks'))
                put(x, g + 7, z, LANTERN)

    def dress_walls(self, kinds=('stairs_wall', 'hall', 'landing'), min_h=3, every=4):
        """擁壁の平らな面に厚みを付ける（藍 11/04「この平坦な感じだけ直したい」）。
        段の縁から自然の地面へ min_h 以上落ちる面に：every マスおきに1マス出た控え柱（上に階段の笠）、
        柱の間は上の縁に逆さ階段の張り出しと足元の幅木、高い面（6以上）は柱の間の真ん中に灯りの窪み（上は模様の石）。"""
        TO = {(1, 0): 'w', (-1, 0): 'e', (0, 1): 'n', (0, -1): 's'}                 # 外のマスから見て壁のある向き（階段の高い側）
        for (x, z), k in list(self.KIND.items()):
            if k not in kinds: continue
            g = self.H[(x, z)]
            for (dx, dz), f in TO.items():
                n = (x + dx, z + dz)
                if self.KIND.get(n) not in ('nat', 'garden') or (n in self.HOUSE_CELLS): continue
                lo = self.h(*n)
                if g - lo < min_h: continue
                par = (z if dx else x) + 1000
                if par % every == 0:                                             # 控え柱：外へ1マス、笠は壁の上端より1上
                    for y in range(lo + 1, g + 1):
                        if get(n[0], y, n[1]) is None: put(n[0], y, n[1], full(self.masonry(n[0], y, n[1], lo)))
                    if get(n[0], g + 1, n[1]) is None:
                        if par % (every * 2) == 0: put(n[0], g + 1, n[1], full('chiseled_stone_bricks')); put(n[0], g + 2, n[1], LANTERN)   # 1本おきに灯り
                        else: put(n[0], g + 1, n[1], stair('stone_bricks', f))
                    m = (n[0] + dx, n[1] + dz)                                    # 柱の足：もう1マス外へ広げる
                    if self.KIND.get(m) in ('nat', 'garden') and self.h(*m) == lo and get(m[0], lo + 1, m[1]) is None: put(m[0], lo + 1, m[1], stair('stone_bricks', f))
                    continue
                if get(n[0], g, n[1]) is None: put(n[0], g, n[1], stair('deepslate_bricks', f, 'top'))     # 上の縁の張り出し（濃い帯で縁取る）
                if g - lo >= 4 and get(n[0], lo + 1, n[1]) is None:                                          # 足元の幅木
                    put(n[0], lo + 1, n[1], stair('mossy_cobblestone' if _h(x, z, 83) < 0.5 else 'cobblestone', f))
                if g - lo >= 6 and par % every == every // 2:                    # 灯りの窪み：壁を1マス掘り、上に逆さ階段
                    ys = (lo + 2, lo + 3)
                    if all(get(x, y, z) and get(x, y, z)['s'] == 'full' for y in ys + (lo + 4,)):
                        for y in ys: put(x, y, z, None)
                        put(x, lo + 2, z, LANTERN)
                        put(x, lo + 4, z, full('chiseled_stone_bricks'))

    # ================================================================ 小物：置ける所か確かめてから置く [小物10〜15]
    def spot_ok(self, cells, g, allow=('plaza', 'yard', 'nat', 'garden', 'lane_edge', 'hall', 'harbor')):
        for (x, z) in cells:
            if self.KIND.get((x, z)) not in allow or self.H.get((x, z)) != g or (x, z) in self.HOUSE_CELLS: return False
            if any(get(x, y, z) is not None for y in range(g + 1, g + 4)): return False
        return True

    def cells_of(self, fn, *args):
        saved = dict(W); W.clear(); cs = fn(*args); W.clear(); W.update(saved); return cs

    def try_place(self, fn, args, g, allow=None):
        cs = self.cells_of(fn, *args)
        if self.spot_ok(cs, g, allow) if allow else self.spot_ok(cs, g): fn(*args); return True
        print('置けない', fn.__name__, args[:2]); return False

    # ================================================================ 畑・小屋・薪
    def flat_nat(self, x0, z0, w, d, tol=2):
        hs = []
        for x in range(x0, x0 + w):
            for z in range(z0, z0 + d):
                if self.KIND.get((x, z)) != 'nat' or (x, z) in self.HOUSE_CELLS: return None
                if any(get(x, yy, z) for yy in range(self.H[(x, z)] + 1, self.H[(x, z)] + 5)): return None
                hs.append(self.H[(x, z)])
        return hs if max(hs) - min(hs) <= tol else None

    def level(self, x0, z0, w, d, y):
        """区画の下だけ平らにし、周りの1マスは半分だけ寄せて段を残さない。"""
        for x in range(x0 - 1, x0 + w + 1):
            for z in range(z0 - 1, z0 + d + 1):
                if (x, z) not in self.H or self.KIND.get((x, z)) != 'nat': continue
                inside = x0 <= x < x0 + w and z0 <= z < z0 + d
                ty = y if inside else int(round((self.H[(x, z)] + y) / 2))
                hh = self.H[(x, z)]
                if any(get(x, yy, z) for yy in range(max(ty, hh) + 1, max(ty, hh) + 3)): continue
                for yy in range(hh + 1, ty + 1): put(x, yy, z, full('dirt'))
                for yy in range(ty + 1, hh + 1): put(x, yy, z, None)
                put(x, ty, z, grass()); self.H[(x, z)] = ty
                if inside: self.KIND[(x, z)] = 'garden'

    def fields(self, xs, zs, w=9, d=6, n=6):
        """平らな所を探して畑（丸太の縁取り・水路・作物）を n 枚まで。"""
        made = 0
        for gz in zs:
            for gx in xs:
                if made >= n: return made
                hs = self.flat_nat(gx, gz, w, d, 3)
                if not hs: continue
                y = int(round(sum(hs) / len(hs))); self.level(gx, gz, w, d, y); P.garden(gx, gz, y, w, d); made += 1
        return made

    def yard_extras(self, skip=()):
        """家の脇に、物置か薪の棚を1つずつ（平らな所があれば）。"""
        made = 0
        for (n, (x0, x1, z0, z1), y) in self.FOOT:
            if n in skip: continue
            for i, (gx, gz) in enumerate([(x1 + 3, z0 + 1), (x0 - 7, z0 + 1), (x0 + 1, z1 + 3), (x0 + 1, z0 - 6)]):
                kind = (made + i) % 2
                w, d = (5, 4) if kind == 0 else (4, 2)
                hs = self.flat_nat(gx, gz, w + 2, d)
                if not hs: continue
                yy = int(round(sum(hs) / len(hs))); self.level(gx, gz, w + 2, d, yy)
                if kind == 0: P.shed(gx + 2, gz, yy, 4, 4)
                else: P.woodpile(gx, gz, yy, 'n')
                made += 1; break
        return made

    # ================================================================ 木と草 [植物8・9]
    def spruce_tree(self, sx, sz, h):
        gy = self.top_y(sx, sz)
        for y in range(gy + 1, gy + h + 1): put(sx, y, sz, log('spruce_log'))
        for k in range(h - 2):
            y = gy + h - k; r = 0.6 + (k % 3) * 0.6 + k * 0.2
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    if (dx or dz) and math.hypot(dx, dz) <= r and get(sx + dx, y, sz + dz) is None: put(sx + dx, y, sz + dz, full('spruce_leaves'))
        put(sx, gy + h + 1, sz, full('spruce_leaves')); put(sx, gy + h + 2, sz, full('spruce_leaves'))

    def blob_tree(self, tx, tz, h, leaves='oak_leaves', logt='oak_log', r0=3.0):
        gy = self.top_y(tx, tz)
        for y in range(gy + 1, gy + h + 1): put(tx, y, tz, log(logt))
        for dy in range(-2, 3):
            r = r0 if dy <= 0 else r0 * 0.7 if dy == 1 else r0 * 0.4
            for dx in range(-5, 6):
                for dz in range(-5, 6):
                    if math.hypot(dx, dz) + _h(dx * 7 + tx, dz + dy, tz) * 0.9 <= r:
                        p = (tx + dx, gy + h + dy, tz + dz)
                        if get(*p) is None: put(*p, full(leaves))

    def town_zone(self, x, z):
        if self.KIND.get((x, z)) not in ('nat', 'stream', 'brook', 'river'): return True
        if (x, z) in self.DECK: return True
        for (n, (x0, x1, z0, z1), _) in self.FOOT:
            if x0 - 4 <= x <= x1 + 4 and z0 - 4 <= z <= z1 + 4: return True
        return False

    def forest(self, skip=lambda x, z: False, kind=lambda x, z, r: 'spruce'):
        """町の外を密に囲む森。外の縁ほど密。kind(x, z, r) で木の種類（'spruce'・'oak'・'birch'・'cherry'）。"""
        trees = 0
        for gx in range(self.X0 + 2, self.X1 - 1, 4):
            for gz in range(self.Z0 + 2, self.Z1 - 1, 4):
                x, z = gx + int(_h(gx, gz, 1) * 3) - 1, gz + int(_h(gx, gz, 2) * 3) - 1
                if not (self.X0 + 2 < x < self.X1 - 2 and self.Z0 + 2 < z < self.Z1 - 2): continue
                if self.KIND.get((x, z)) != 'nat' or skip(x, z) or any(self.town_zone(x + dx, z + dz) for dx in (-5, 0, 5) for dz in (-5, 0, 5)): continue
                edge = min(x - self.X0, self.X1 - x, z - self.Z0, self.Z1 - z)
                if edge > 26 and _h(x, z, 3) < 0.4: continue
                k = kind(x, z, _h(x, z, 6))
                g = self.top_y(x, z); sd = x * 7919 + z
                if k == 'spruce': (TR.small_spruce if _h(x, z, 8) < 0.3 else TR.spruce)(x, z, g, sd)
                elif k == 'cherry': TR.cherry(x, z, g, sd)
                elif k == 'birch': TR.birch(x, z, g, sd)
                elif k == 'big': TR.big_tree(x, z, g, sd)
                else: TR.oak(x, z, g, sd, 0.8 + 0.5 * _h(x, z, 9))
                trees += 1
        return trees

    # ================================================================ 森の並べ方と地面 [rules/nature.md 28〜39]
    TREE_R = {'oak': 6.0, 'birch': 3.0, 'cherry': 4.0, 'spruce': 4.0, 'small_spruce': 2.3, 'big': 9.0}

    def trail(self, pts, w=3.0):
        """森の小道（踏み跡）：折れ線を幅 w で通す。縁はギザギザ。地面は ground_cover で塗る。 [rules 32]"""
        if not hasattr(self, 'TRAIL'): self.TRAIL = set(); self.TRAIL_LINES = []
        self.TRAIL_LINES.append(pts)
        for (a, b) in zip(pts, pts[1:]):
            n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) * 2 + 1
            for i in range(n + 1):
                t = i / n; x = a[0] + (b[0] - a[0]) * t; z = a[1] + (b[1] - a[1]) * t
                half = w / 2 + vnoise(x, z, 5, 71) * 1.4 - 0.5
                for dx in range(-4, 5):
                    for dz in range(-4, 5):
                        c = (int(round(x)) + dx, int(round(z)) + dz)
                        if math.hypot(dx, dz) <= half and self.KIND.get(c) == 'nat': self.TRAIL.add(c)

    def trail_lamps(self, line, every=16):
        """踏み跡の街灯：道の縁の外1〜2マス、左右交互に every マスおき。腕は道の上へ、柱の高さは3本ごとに変える。
        木や物のある所は飛ばす。 [rules 39・props.path_lamp]"""
        pts, n, side = [], 0, 1
        for (a, b) in zip(line, line[1:]):
            L = int(math.hypot(b[0] - a[0], b[1] - a[1]))
            for i in range(L):
                pts.append((a[0] + (b[0] - a[0]) * i / L, a[1] + (b[1] - a[1]) * i / L, (b[0] - a[0]) / L, (b[1] - a[1]) / L))
        for k in range(every // 2, len(pts), every):
            x, z, ux, uz = pts[k]
            for off in (3, 4, 5):
                lx, lz = int(round(x - uz * side * off)), int(round(z + ux * side * off))
                if (lx, lz) in self.TRAIL or self.KIND.get((lx, lz)) != 'nat': continue
                g = self.H[(lx, lz)]
                if any(get(lx, y, lz) is not None for y in range(g + 1, g + 12)) or any(get(lx + dx, y, lz + dz) is not None for dx, dz in N4 for y in range(g + 7, g + 11)): continue
                ddx, ddz = x - lx, z - lz
                arm = ('e' if ddx > 0 else 'w') if abs(ddx) > abs(ddz) else ('s' if ddz > 0 else 'n')
                P.path_lamp(lx, lz, g, arm, posts=4 + n % 3); n += 1; side = -side
                break
        return n

    def rest_near(self, x0, z0, reach=8):
        """木陰の休みどころを (x0, z0) の近くの木の根元に置く。座面は踏み跡か水の側を向く。 [rules 37・39]"""
        trees = sorted(getattr(self, 'TREES', []), key=lambda t: math.hypot(t[0] - x0, t[1] - z0))[:6]
        for (tx, tz, R, k) in trees:
            for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2), (3, 1), (-3, -1), (1, 3), (-1, -3)):
                x, z = tx + dx, tz + dz
                g = self.H.get((x, z))
                for f in ('n', 's', 'e', 'w'):
                    cs = self.cells_of(P.rest_spot, x, z, g, f)
                    if any(c in getattr(self, 'TRAIL', ()) for c in cs) or not self.spot_ok(cs, g, ('nat',)): continue
                    P.rest_spot(x, z, g, f); return (x, z)
        print('置けない rest_spot', (x0, z0)); return None

    def trail_ends(self, lines):
        """踏み跡の奥の端に、行き止まりの休み場を置く（端から2〜4マス、踏み跡の外の平らな所）。 [props.trail_end]"""
        n = 0
        for line in lines:
            ex, ez = line[-1]
            done = False
            for rad in (2, 3, 4):
                for (dx, dz) in ((rad, 0), (-rad, 0), (0, rad), (0, -rad), (rad, rad), (-rad, -rad)):
                    x, z = int(ex) + dx, int(ez) + dz
                    g = self.H.get((x, z))
                    if g is None: continue
                    for f in ('n', 's', 'e', 'w'):
                        cs = self.cells_of(P.trail_end, x, z, g, f)
                        if any(c in self.TRAIL for c in cs) or not self.spot_ok(cs, g, ('nat',)): continue
                        P.trail_end(x, z, g, f); n += 1; done = True; break
                    if done: break
                if done: break
        return n

    def _water_dist(self, reach=9):
        wet = [c for c, k in self.KIND.items() if k in ('river', 'stream', 'brook', 'lake') or self.in_water(*c)]
        d = {c: 0 for c in wet}; q = deque(wet)
        while q:
            c = q.popleft()
            if d[c] >= reach: continue
            for dx, dz in N4:
                n = (c[0] + dx, c[1] + dz)
                if n in self.H and n not in d: d[n] = d[c] + 1; q.append(n)
        return d

    def woods(self, skip=lambda x, z: False, kind=lambda x, z, r: 'oak', clear=lambda x, z: False, open_at=0.30, sparse_at=0.42):
        """森：密な群れ・まばらな所・空き地を、大きなまだら（vnoise）で分ける。格子に置かない。
        密な所は冠が触れるくらい（幹の間 = 半径の和 −1）、まばらな所は冠の間を4〜12マス空ける。空き地は木を置かない（まれに一本立ち）。
        道の両脇2マスと水際3マスは空け、水際に近い所は小さい木だけ。木は self.TREES に (x, z, 半径, 種類) で残す。 [rules 28〜31・36]"""
        H, KIND = self.H, self.KIND
        trail = getattr(self, 'TRAIL', set())
        near_trail = {(x + dx, z + dz) for (x, z) in trail for dx in range(-2, 3) for dz in range(-2, 3)}
        wd = self._water_dist()
        self.WD = wd
        cand = [c for c in H if KIND.get(c) == 'nat' and c not in near_trail and not skip(*c)
                and self.X0 + 3 < c[0] < self.X1 - 3 and self.Z0 + 3 < c[1] < self.Z1 - 3]
        rng = random.Random(77); rng.shuffle(cand)
        trees, grid = [], {}
        def near(x, z, rad):
            for gx in range(int(x - rad) // 12 - 1, int(x + rad) // 12 + 2):
                for gz in range(int(z - rad) // 12 - 1, int(z + rad) // 12 + 2):
                    for t in grid.get((gx, gz), ()): yield t
        for (x, z) in cand:
            d = vnoise(x, z, 34, 81) * 0.8 + vnoise(x, z, 11, 82) * 0.2
            if clear(x, z) or d < open_at: zone = 'open'
            elif d < sparse_at: zone = 'sparse'
            else: zone = 'dense'
            if zone == 'open' and _h(x, z, 83) > 0.006: continue
            k = kind(x, z, _h(x, z, 6))
            if k == 'spruce' and _h(x, z, 8) < 0.3: k = 'small_spruce'
            w = wd.get((x, z), 99)
            if w < 3: continue
            if w < 6 and k in ('oak', 'big', 'spruce', 'cherry'): k = 'birch' if k != 'spruce' else 'small_spruce'
            size = 0.85 + 0.4 * _h(x, z, 9) if k == 'oak' else 1.0
            R = self.TREE_R[k] * size
            if any(self.town_zone(x + dx, z + dz) for dx in (-int(R), 0, int(R)) for dz in (-int(R), 0, int(R))): continue
            g = H[(x, z)]
            if any(abs(H.get((x + dx, z + dz), g) - g) > 1 for dx, dz in N4): continue
            gap = (-0.3 if k in ('spruce', 'small_spruce') else -1.0) if zone == 'dense' else 2 + 8 * _h(x, z, 84)
            if any(math.hypot(x - tx, z - tz) < R + tr + gap for (tx, tz, tr, tk) in near(x, z, R + 12)): continue
            trees.append((x, z, R, k)); grid.setdefault((x // 12, z // 12), []).append((x, z, R, k))
            if zone == 'dense': self.TALL = getattr(self, 'TALL', set()); self.TALL.add((x, z))
        for (x, z, R, k) in trees:
            g = H[(x, z)]; sd = x * 7919 + z
            if k == 'spruce': TR.spruce(x, z, g, sd)
            elif k == 'small_spruce': TR.small_spruce(x, z, g, sd)
            elif k == 'cherry': TR.cherry(x, z, g, sd)
            elif k == 'birch': TR.birch(x, z, g, sd)
            elif k == 'big': TR.big_tree(x, z, g, sd)
            else: TR.rana_oak(x, z, g, sd, R / self.TREE_R['oak'])                        # らーなろっその木（腕ごとの丸い葉の塊）
        self.TREES = trees
        # 茂み：森の縁から草地へ、6〜10マスおきに散らして薄くする。水際の草の帯の外にも。 [rules 31]
        bushes = 0
        for (x, z, R, k) in trees:
            for i in range(3):
                a = _h(x + i, z, 85) * 2 * math.pi; dd = R + 2 + 7 * _h(x, z + i, 86)
                bx, bz = int(round(x + math.cos(a) * dd)), int(round(z + math.sin(a) * dd))
                if KIND.get((bx, bz)) != 'nat' or (bx, bz) in near_trail or wd.get((bx, bz), 99) < 2 or self.town_zone(bx, bz): continue
                if any(math.hypot(bx - tx, bz - tz) < tr + 1 for (tx, tz, tr, tk) in near(bx, bz, 12)): continue
                g = H[(bx, bz)]
                if any(H.get((bx + dx, bz + dz)) != g for dx in (0, 1) for dz in (0, 1)) or get(bx, g + 1, bz) is not None: continue
                if _h(bx, bz, 87) < 0.45: TR.bush(bx, bz, g, bx * 31 + bz, big=_h(bx, bz, 88) < 0.3); bushes += 1
        return len(trees), bushes

    def fallen_logs(self, n=40):
        """倒木と切り株：森の中の平らな所に。切り株は立てた原木1つ、1〜2マス離して寝かせた丸太（オーク4〜7、トウヒ6〜10）、
        丸太の上にキノコ、切り株の脇に苔の絨毯。 [rules 38]"""
        rng = random.Random(91); H = self.H; done = 0
        trees = getattr(self, 'TREES', [])
        for (x, z, R, k) in rng.sample(trees, min(len(trees), n * 4)):
            if done >= n: break
            a = rng.uniform(0, 2 * math.pi); sx, sz = int(round(x + math.cos(a) * (R + 2))), int(round(z + math.sin(a) * (R + 2)))
            sp = k in ('spruce', 'small_spruce')
            L = rng.randint(6, 9) if sp else rng.randint(4, 6)
            ax = rng.choice([(1, 0), (0, 1)]); off = rng.randint(1, 2)
            cells = [(sx + ax[0] * (off + i), sz + ax[1] * (off + i)) for i in range(L)]
            g = H.get((sx, sz))
            if g is None or any(H.get(c) != g or self.KIND.get(c) != 'nat' or get(c[0], g + 1, c[1]) not in (None,) and get(c[0], g + 1, c[1])['s'] != 'cross' for c in [(sx, sz)] + cells): continue
            if any((c in getattr(self, 'TRAIL', ())) for c in [(sx, sz)] + cells): continue
            lt = 'spruce_log' if sp else 'oak_log'
            put(sx, g + 1, sz, log(lt))
            if rng.random() < 0.5: put(sx, g + 2, sz, slab(lt.replace('_log', '_planks')))
            for i, (cx, cz) in enumerate(cells):
                put(cx, g + 1, cz, log(lt, 'x' if ax[0] else 'z'))
                if i in (1, L - 2) and rng.random() < 0.7: put(cx, g + 2, cz, cross(rng.choice(['brown_mushroom', 'red_mushroom'])))
                elif rng.random() < 0.3: put(cx, g + 2, cz, full('moss_carpet') if False else cross('short_grass'))
            for (dx, dz) in N4:
                c = (sx + dx, sz + dz)
                if c not in cells and H.get(c) == g and get(c[0], g + 1, c[1]) is None and rng.random() < 0.5:
                    put(c[0], g, c[1], full('moss_block'))
            ex, ez = cells[-1]                                                     # L字：端から横へ短い丸太 2〜3（One Team 11:20）
            px, pz = (ax[1], ax[0])
            for i in range(1, rng.randint(2, 3) + 1):
                c = (ex + px * i, ez + pz * i)
                if H.get(c) == g and self.KIND.get(c) == 'nat' and get(c[0], g + 1, c[1]) in (None,) or (H.get(c) == g and get(c[0], g + 1, c[1]) and get(c[0], g + 1, c[1])['s'] == 'cross'):
                    put(c[0], g + 1, c[1], log(lt, 'x' if px else 'z'))
                else: break
            lx, lz = cells[len(cells) // 2][0] - px * 2, cells[len(cells) // 2][1] - pz * 2   # 反対側に2×2の葉の塊
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                c = (lx + dx, lz + dz)
                if H.get(c) == g and (get(c[0], g + 1, c[1]) is None or get(c[0], g + 1, c[1])['s'] == 'cross'): put(c[0], g + 1, c[1], full('oak_leaves' if not sp else 'spruce_leaves'))
            for (dx, dz, pl) in ((-1, -1, 'brown_mushroom'), (1, -1, 'fern'), (-1, 1, 'fern')):   # 足元にキノコとシダ
                c = (sx + dx, sz + dz)
                if H.get(c) == g and get(c[0], g + 1, c[1]) is None: put(c[0], g + 1, c[1], cross(pl))
            done += 1
        return done

    def ground_cover(self, flower_groups=1.0 / 260, rocks=1.0 / 500):
        """地面と下草。草ブロックを主に、混ぜもの（粗い土・ポドゾル・苔・砂利）を1〜2割、かたまりで、木の根元・段の縁・道の脇に。
        下草は3〜6マスの塊で、冠の下と岸は濃く（7〜8割）、開けた所は薄く（3〜4割）。花は2〜4本の群れで、場所ごとに2色。
        踏み跡は dirt_path・podzol・coarse_dirt・苔・砂利のまだらで、縁の半分は草のまま（ギザギザ）。 [rules 32〜34]"""
        H, KIND = self.H, self.KIND
        trail = getattr(self, 'TRAIL', set())
        trees = getattr(self, 'TREES', [])
        wd = getattr(self, 'WD', None) or self._water_dist()
        canopy, root, conifer = set(), set(), set()
        for (x, z, R, k) in trees:
            for dx in range(-int(R), int(R) + 1):
                for dz in range(-int(R), int(R) + 1):
                    dd = math.hypot(dx, dz)
                    if dd <= R: canopy.add((x + dx, z + dz))
                    if dd <= 2.2: root.add((x + dx, z + dz))
                    if dd <= R and k in ('spruce', 'small_spruce'): conifer.add((x + dx, z + dz))
        PODZOL = full('podzol_side', top='podzol_top', bottom='dirt')
        PAIRS = [('poppy', 'oxeye_daisy'), ('cornflower', 'azure_bluet'), ('dandelion', 'allium'), ('oxeye_daisy', 'cornflower'), ('red_tulip', 'white_tulip')]
        rng = random.Random(93)
        for (x, z), k in KIND.items():
            if k != 'nat': continue
            g = H[(x, z)]; b = get(x, g, z)
            if not b or b.get('top') not in ('grass_top', 'podzol_top', 'coarse_dirt'): continue
            above = get(x, g + 1, z)
            if (x, z) in trail:
                edge = any((x + dx, z + dz) not in trail for dx, dz in N4)
                if edge and _h(x, z, 94) < 0.5: continue
                r = vnoise(x, z, 2.5, 95) * 0.6 + _h(x, z, 96) * 0.4
                put(x, g, z, path() if r < 0.38 else PODZOL if r < 0.55 else full('coarse_dirt') if r < 0.72 else full('moss_block') if r < 0.86 else full('gravel'))
                if above and above['s'] == 'cross': put(x, g + 1, z, None)
                continue
            slope = any(H.get((x + dx, z + dz), g) < g for dx, dz in N4)
            beside = any((x + dx, z + dz) in trail for dx in (-2, 0, 2) for dz in (-2, 0, 2))
            mix = vnoise(x, z, 3, 97)
            want = 0.30 if (x, z) in root or beside else 0.22 if slope else 0.06
            if (x, z) in conifer: want += 0.35                                   # 針葉樹の床は茶色を4〜5割（azrel 森）
            if mix < want:
                r = _h(x, z, 98)
                if (x, z) in conifer: put(x, g, z, PODZOL if r < 0.6 else full('coarse_dirt') if r < 0.85 else full('moss_block'))
                elif slope: put(x, g, z, full('coarse_dirt') if r < 0.5 else full('gravel') if r < 0.7 else full('moss_block'))
                else: put(x, g, z, full('moss_block') if r < 0.4 else full('coarse_dirt') if r < 0.7 else PODZOL)
            if above is not None: continue
            if beside and any((x + dx, z + dz) in trail for dx, dz in N4 + ((1, 1), (-1, -1), (1, -1), (-1, 1))) and _h(x, z, 103) < 0.7 and get(x, g + 2, z) is None:
                put(x, g + 1, z, cross('tall_grass_bottom')); put(x, g + 2, z, cross('tall_grass_top')); continue   # 踏み跡の脇は背の高い草で縁取る（Mr CozyCraft 1:20）
            dens = 0.78 if (x, z) in canopy or wd.get((x, z), 99) <= 3 else 0.36
            if vnoise(x, z, 4, 99) < dens and _h(x, z, 100) < 0.85:
                put(x, g + 1, z, cross('fern' if (x, z) in canopy and _h(x, z, 101) < 0.45 else 'short_grass'))
        cells = [c for c, k in KIND.items() if k == 'nat' and c not in trail]
        for i in range(int(len(cells) * flower_groups)):
            x, z = rng.choice(cells)
            pair = PAIRS[int(vnoise(x, z, 50, 102) * len(PAIRS)) % len(PAIRS)]
            for j in range(rng.randint(2, 4)):
                fx, fz = x + rng.randint(-1, 1), z + rng.randint(-1, 1)
                g = H.get((fx, fz))
                if g is None or KIND.get((fx, fz)) != 'nat' or (fx, fz) in trail: continue
                b = get(fx, g, fz); a = get(fx, g + 1, fz)
                if b and b.get('top') == 'grass_top' and (a is None or a['s'] == 'cross'): put(fx, g + 1, fz, cross(pair[j % 2]))
        for i in range(int(len(cells) * rocks)):
            x, z = rng.choice(cells)
            g = H[(x, z)]
            if (x, z) in canopy or self.town_zone(x, z) or not any(H.get((x + dx, z + dz), g) < g for dx, dz in N4): continue
            if get(x, g + 1, z) is None or get(x, g + 1, z)['s'] == 'cross':
                put(x, g + 1, z, None); self.rock(x, z, g, rng)
        for (x, y, z), b in list(W.items()):                                     # 岩に下を取られた背の高い草の上半分を消す
            if b and b.get('t') == 'tall_grass_top' and (get(x, y - 1, z) or {}).get('t') != 'tall_grass_bottom': put(x, y, z, None)

    def rock(self, x, z, g, rng):
        """岩：芯の石を半分埋め、周りに石の階段とハーフ、苔の石を少し。大きい岩は芯を2〜3つ。"""
        mat = rng.choice(['stone', 'andesite', 'stone', 'cobblestone', 'tuff'])
        cores = [(0, 0)] + ([(1, 0), (0, 1)][:rng.randint(0, 2)] if rng.random() < 0.4 else [])
        for (dx, dz) in cores:
            put(x + dx, g + 1, z + dz, full(mat if rng.random() < 0.8 else 'mossy_cobblestone'))
            if len(cores) > 1 and (dx, dz) == (0, 0): put(x, g + 2, z, slab(mat))
        for (dx, dz) in cores:
            for (ox, oz, f) in ((1, 0, 'w'), (-1, 0, 'e'), (0, 1, 'n'), (0, -1, 's')):
                X, Z = x + dx + ox, z + dz + oz
                if (X - x, Z - z) in cores or get(X, g + 1, Z) not in (None,) and get(X, g + 1, Z)['s'] != 'cross': continue
                if self.H.get((X, Z)) != g or rng.random() < 0.35: continue
                put(X, g + 1, Z, stair(mat + ('_bricks' if mat == 'stone' and False else ''), f) if mat != 'tuff' else slab('tuff') if False else stair(mat, f)) if rng.random() < 0.6 else put(X, g + 1, Z, slab(mat))

    def greens(self, flowers=22000, rocks=2400):
        """草花、坂の岩と茂み（段のしまを崩す）、段の際の草と茂み（土の切り口を隠す）。"""
        rng = self.rng; H, KIND = self.H, self.KIND
        for i in range(flowers):
            x, z = rng.randint(self.X0, self.X1), rng.randint(self.Z0, self.Z1)
            g = H.get((x, z)); b = get(x, g, z) if g is not None else None
            if b and b.get('top') == 'grass_top' and self.free(x, g + 1, z) and KIND.get((x, z)) in ('nat', 'yard'):
                r = rng.random()
                put(x, g + 1, z, cross('short_grass' if r < 0.74 else 'fern' if r < 0.88 else rng.choice(['dandelion', 'poppy', 'oxeye_daisy', 'cornflower', 'azure_bluet'])))
        def slope(x, z): return max(abs(H.get((x, z), 0) - H.get((x + dx, z + dz), H.get((x, z), 0))) for dx, dz in N4)
        for i in range(rocks):
            x, z = rng.randint(self.X0 + 2, self.X1 - 2), rng.randint(self.Z0 + 2, self.Z1 - 2)
            if KIND.get((x, z)) != 'nat' or self.town_zone(x, z): continue
            g = self.top_y(x, z)
            if g != H[(x, z)] or not self.free(x, g + 1, z): continue
            if slope(x, z) >= 1 and rng.random() < 0.18:
                self.rock(x, z, g, rng)
            elif rng.random() < 0.5:
                lv = rng.choice(['oak_leaves', 'azalea_leaves', 'flowering_azalea_leaves', 'spruce_leaves'])
                for (dx, dz, dy) in [(0, 0, 1), (1, 0, 1), (0, 1, 1), (0, 0, 2), (-1, 0, 1)][:rng.randint(1, 5)]:
                    gy = H.get((x + dx, z + dz), g)
                    if gy == g and self.free(x + dx, gy + dy, z + dz): put(x + dx, gy + dy, z + dz, full(lv))
        for (x, z), d in self.dist.items():
            if d == 0 or KIND.get((x, z)) != 'nat': continue
            g = H[(x, z)]
            if self.top_y(x, z) != g or not self.free(x, g + 1, z): continue
            b = get(x, g, z)
            if not b or b.get('top') != 'grass_top': continue
            step = any(H.get((x + dx, z + dz), g) < g for dx, dz in N4)
            r = _h(x, z, 91)
            if step and r < 0.22: put(x, g + 1, z, full(['azalea_leaves', 'oak_leaves', 'flowering_azalea_leaves'][int(_h(x, z, 92) * 3)]))
            elif r < (0.7 if step else 0.4): put(x, g + 1, z, cross('short_grass' if r < 0.4 else 'fern'))

    # ================================================================ 検査 [見せる前に必ず]
    def blocked(self, x, y, z):
        b = get(x, y, z)
        return b is not None and (b['s'] in BLOCKING or b['s'] == 'trap' and b.get('f') not in ('top', 'bottom'))

    def floor(self, x, z):
        if (x, z) in self.BRIDGE: return self.BRIDGE[(x, z)]
        if (x, z) in self.DECK: return self.DECK[(x, z)]
        if (x, z) in self.PIERS: return 0
        g = self.H.get((x, z))
        if g is None: return None
        b = get(x, g + 1, z)
        if b and b['s'] in ('stair', 'slab') and b.get('h') != 'top': return g + 1
        return g

    def walk_check(self, start, walk=('lane', 'lane_edge', 'plaza', 'hall', 'stairs', 'landing', 'cliffstair', 'harbor', 'yard', 'garden', 'nat', 'deck', 'bridge'),
                   roads=('lane', 'stairs', 'landing', 'cliffstair', 'harbor', 'hall')):
        """広場（start）から歩いて、道が全部つながっているか・戸口が全部外へ出られるかを数える（11/02 藍「道が塞がってたり」）。"""
        seen = {start}; q = deque(seen)
        while q:
            x, z = q.popleft(); f0 = self.floor(x, z)
            for dx, dz in N4:
                n = (x + dx, z + dz)
                if n in seen or (self.KIND.get(n) not in walk and n not in self.PIERS and n not in self.BRIDGE and n not in self.DECK): continue
                f1 = self.floor(*n)
                if f1 is None or abs(f1 - f0) > 1 or self.blocked(n[0], f1 + 1, n[1]) or self.blocked(n[0], f1 + 2, n[1]): continue
                seen.add(n); q.append(n)
        lost = [(x, z) for (x, z), k in self.KIND.items() if k in roads and (x, z) not in seen and not self.blocked(x, self.floor(x, z) + 1, z)]
        floor_of = {}
        for (n, (x0, x1, z0, z1), fy) in self.FOOT:
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1): floor_of[(x, z)] = fy
        doors = [(x, y, z) for (x, y, z), b in W.items() if b['s'] == 'door' and b['t'].endswith('bottom') and (x, z) in floor_of and y - floor_of[(x, z)] <= 4]
        def ok(x, y, z):
            return any((x + dx, z + dz) in seen and abs(self.floor(x + dx, z + dz) - (y - 1)) <= 3 for dx in range(-4, 5) for dz in range(-4, 5) if (dx, dz) != (0, 0))
        bad = [d for d in doors if not ok(*d)]
        print('歩いて行けない道', len(lost), sorted(lost)[:10])
        print('外へ出られない戸口', len(bad), bad[:10])
        return lost, bad


def save(name, views, target, shadowR=120, fog=(150, 480)):
    """out/<name>/ に house.js と atlas.png を書き、見るための viewer・test.html・影の描き方を写す。"""
    import shutil
    out = os.path.join(os.path.dirname(KIT), 'out', name)
    os.makedirs(out, exist_ok=True)
    for f in ('viewer.js', 'test.html', 'lightmap.fsh', 'lightmap_vanilla.fsh'):
        shutil.copy(os.path.join(KIT, f), out)
    write(out, {'target': target, 'shadowR': shadowR, 'fog': list(fog), 'views': views})
    return out
