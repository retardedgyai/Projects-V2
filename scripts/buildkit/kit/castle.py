"""和風の城の部品（11/06、藍「左右非対称」「作りが荒い」「もっと大きな規模で、簡略化しないで」）。
手本はらーなろっその黒牢城の回の天守（~/ProjectS/refs/build/rana/1WSmXolgmZE/keep/ の4秒ごとのコマ、notes.md）。決まりは rules/wafu.md。
どの部品も中心 (cx, cz) からの |u|・|v| で組むので、前後左右とも対称になる。
  ishigaki   石垣：建物より一回り広く、4段ごとに1マス引っ込む。上の縁に黒い帯と、垂れた支え
  storey     層の壁：'wood'（下は縦の板張りと細い柱、上2段は白）／'white'（白壁と柱）／'dark'（最上階、廻縁つき）
  skirt      腰屋根：1マス外へ出るごとに半マス下がる。軒先はギザギザ、軒下に支え、四隅は隅棟が跳ね上がって先に鯱
  chidori    千鳥破風：腰屋根の各面の真ん中に載る三角の屋根。中は白壁と格子窓
  gable      入母屋の上の切妻：棟の両端に鯱
  tenshu     天守：石垣 → 板張りの1層 → 白い2層 → 廻縁の最上階、層ごとに腰屋根と千鳥破風、上は入母屋
"""
import vox
from vox import put, get, full, log, stair, slab, fence

ROOF = 'cobbled_deepslate'
WHITE = 'calcite'
def wall(t): return {'s': 'wall', 't': t}
HANG = {'s': 'lantern', 't': 'lantern', 'hang': True}
LANTERN = {'s': 'lantern', 't': 'lantern'}
EGG = full('dragon_egg')                                                          # 鯱：黒い小さな飾り（手本も竜の卵）

def _side(u, v, hx, hz):
    """(u, v) が四辺のどれの外側か：'e' 'w' 's' 'n'。"""
    if abs(u) - hx >= abs(v) - hz: return 'e' if u > 0 else 'w'
    return 's' if v > 0 else 'n'
INWARD = {'e': 'w', 'w': 'e', 's': 'n', 'n': 's'}                                 # 階段の背（高い側）を中心へ向ける

def ishigaki(cx, cz, g, half, height, ground):
    """石垣：底の半幅 half、高さ height。4段ごとに1マス引っ込む（反り気味の勾配）。角は磨いた安山岩を互い違いに（算木積み）。
    上の縁は磨いた深層岩の帯を1マス張り出し、その下に2マスおきに支え。上の半幅を返す。"""
    for r in range(-45, height):
        inset = max(0, r) // 4; h_ = half - inset
        for u in range(-h_, h_ + 1):
            for v in range(-h_, h_ + 1):
                x, z = cx + u, cz + v
                if r < 0 and g + r < ground(x, z) - 1: continue                       # 崖の上でも地面まで積む
                if r < 0 and not (abs(u) == h_ or abs(v) == h_) and g + r < ground(x, z): continue
                edge = abs(u) == h_ or abs(v) == h_
                corner = abs(u) == h_ and abs(v) == h_
                n = vox._h(x * 31 + (g + r), z, 401)
                b = ('polished_andesite' if (r // 2 + (u > 0) + (v > 0)) % 2 == 0 else 'smooth_stone') if corner else \
                    'cobblestone' if n < 0.45 else 'andesite' if n < 0.7 else 'stone' if n < 0.85 else 'tuff' if n < 0.95 else 'mossy_cobblestone'
                put(x, g + r, z, full(b if edge else 'stone'))
    top = half - (height - 1) // 4
    for u in range(-top - 1, top + 2):                                            # 上の縁の帯
        for v in range(-top - 1, top + 2):
            if max(abs(u), abs(v)) == top + 1:
                put(cx + u, g + height - 1, cz + v, full('polished_deepslate'))
                if (u + v) % 2 == 0 and not (abs(u) == top + 1 and abs(v) == top + 1): put(cx + u, g + height - 2, cz + v, wall('polished_deepslate'))
    return top

def storey(cx, cz, y0, hx, hz, h, style='white'):
    """層の壁（外周だけ）と床。柱は角と |u|%3==0 の所。
    'wood'：下は茶の板（トウヒ）と濃い柱、上2段は白。格子窓は板の段に 6マスおき。
    'white'：白壁と濃い柱、下の段に長押（濃い板）。格子窓は真ん中に3マスと、6マスおき。
    'dark'：最上階。濃い板の壁、各面の真ん中に3マスの口（上はアーチ）、中に灯り。"""
    for u in range(-hx, hx + 1):
        for v in range(-hz, hz + 1):
            x, z = cx + u, cz + v
            put(x, y0 - 1, z, full('dark_oak_planks'))                            # 床
            if not (abs(u) == hx or abs(v) == hz): continue
            w = abs(v) if abs(u) == hx else abs(u)                                # 面の上の、真ん中からの距離
            corner = abs(u) == hx and abs(v) == hz
            post = corner or w % 3 == 0
            for y in range(y0, y0 + h):
                k = y - y0
                if style == 'wood':
                    if k >= h - 2: b = full(WHITE) if not post else log('dark_oak_log')
                    elif post: b = log('dark_oak_log')
                    elif k in (1, 2) and w % 6 in (1, 2) and not corner: b = fence('dark_oak_planks')
                    else: b = full('spruce_planks')
                elif style == 'white':
                    if post: b = log('dark_oak_log')
                    elif k == 0: b = full('dark_oak_planks')
                    elif k == h // 2 and (w <= 1 or w % 6 in (1, 2)): b = fence('dark_oak_planks')
                    else: b = full(WHITE)
                else:
                    if post: b = log('dark_oak_log')
                    elif w <= 1 and k < 2: b = None
                    elif w <= 1 and k == 2: b = full('dark_oak_planks')
                    else: b = full('dark_oak_planks') if k % 2 == 0 else full('spruce_planks')
                put(x, y, z, b)
    if style == 'dark':
        put(cx, y0 + 1, cz, LANTERN)
        for u in range(-hx - 1, hx + 2):                                          # 廻縁：外へ1マスの縁と欄干
            for v in range(-hz - 1, hz + 2):
                if max(abs(u) - hx, abs(v) - hz) == 1:
                    put(cx + u, y0 - 1, cz + v, slab('dark_oak_planks', 'top'))
                    put(cx + u, y0, cz + v, fence('dark_oak_planks'))
    for u in range(-hx + 1, hx):                                                  # 天井（中が透けないように）
        for v in range(-hz + 1, hz):
            put(cx + u, y0 + h - 1, cz + v, full('dark_oak_planks'))

def skirt(cx, cz, y0, hx, hz, out=3, inner=2, horn=True):
    """腰屋根：壁（半幅 hx, hz）の外 out マスから内側 inner マスまで。1マスごとに半マス上がる（ハーフ・階段を交互）。
    四隅は持ち上げて反らせ、隅棟（対角の1列）を1段高く通し、軒の角で2段跳ね上げて先に鯱。
    軒先は1マスおきにハーフを1マス外へ出してギザギザ（瓦）。軒の下の1マス外に2マスおきの支え。屋根の上の端の高さを返す。"""
    def lift(ex, ez): return max(0, min(ex, ez) - 1) if ex > 0 and ez > 0 else 0
    for u in range(-hx - out, hx + out + 1):
        for v in range(-hz - out, hz + out + 1):
            ex, ez = abs(u) - hx, abs(v) - hz; o = max(ex, ez)
            if o > out: continue
            if o > 0: k = out - o
            else:
                d = min(hx - abs(u), hz - abs(v))
                if d >= inner: continue
                k = out + d
            yy = y0 + k // 2 + lift(ex, ez)
            x, z = cx + u, cz + v
            edge_tooth = o == out and not (ex > 0 and ez > 0) and (abs(u) + abs(v)) % 2 == 1   # 軒先のギザギザ：同じ線の上で1マスおきに高さを変える
            if k % 2 == 0 and not edge_tooth: put(x, yy, z, slab(ROOF))
            else: put(x, yy, z, stair(ROOF, INWARD[_side(u, v, hx, hz)]))
            if ex > 0 and ez > 0 and ex == ez: put(x, yy + 1, z, full(ROOF))       # 隅棟
            if o == 1 and not (ex > 0 and ez > 0) and (abs(u) + abs(v)) % 2 == 0:  # 軒の支え
                put(x, y0 - 1, z, wall('cobbled_deepslate'))
    if horn:
        for su in (-1, 1):
            for sv in (-1, 1):
                u, v = su * (hx + out), sv * (hz + out)                        # 隅：反った角の上に1段と小さな鯱（大きく跳ね上げると中華に寄る [rules 116]）
                yy = y0 + lift(out, out)
                X, Z = cx + u, cz + v
                put(X, yy + 1, Z, stair(ROOF, INWARD['e' if su > 0 else 'w']))
                put(X, yy + 2, Z, EGG)
                put(X, yy - 1, Z, HANG)
    return y0 + (out + inner - 1) // 2 + 1

def chidori(cx, cz, y0, hx, hz, w, depth=4):
    """千鳥破風：腰屋根（壁の半幅 hx, hz、軒の高さ y0）の各面の真ん中に、幅 2w+1 の三角屋根を載せる。
    前の端は壁の2マス外。棟は面に直交し、1マスごとに1段上がる。三角の面は白、縁は濃い板、真ん中に格子窓。棟の前に鯱。"""
    for s_ in ('e', 'w', 's', 'n'):
        du, dv = {'e': (1, 0), 'w': (-1, 0), 's': (0, 1), 'n': (0, -1)}[s_]
        H_ = hx if s_ in 'ew' else hz
        across = ('n', 's') if s_ in 'ew' else ('w', 'e')                         # 横の向き（負の側, 正の側）
        def P(a, t, y, b):                                                        # a：面に沿う（横）、t：面から外への距離（壁 = 0）
            if s_ in 'ew': put(cx + du * (H_ + t), y, cz + a, b)
            else: put(cx + a, y, cz + dv * (H_ + t), b)
        for t in range(-depth, 3):
            for a in range(-w - 1, w + 2):
                h = y0 + 1 + (w + 1 - abs(a))
                if abs(a) == 0: P(a, t, h, full(ROOF)); P(a, t, h + 1, slab(ROOF))
                else: P(a, t, h, stair(ROOF, across[1] if a < 0 else across[0]))
                if t == 1 and abs(a) <= w:                                        # 三角の面
                    for y in range(y0 + 1, h):
                        top = y == h - 1
                        win = abs(a) <= 1 and y in (y0 + 2, y0 + 3)
                        P(a, t, y, full('dark_oak_planks') if top else fence('dark_oak_planks') if win else full(WHITE))
                elif t < 1 and abs(a) <= w:
                    for y in range(y0 + 1, h):
                        P(a, t, y, full(WHITE))
        P(0, 2, y0 + w + 3, EGG)

def gable(cx, cz, y0, hx, hz, along='x'):
    """入母屋の上の切妻：棟は along の向き。1マスごとに1段上がる。妻（三角の面）は白に濃い縁、真ん中に格子窓。棟の両端に鯱。"""
    if along == 'x': n, m = hz, hx
    else: n, m = hx, hz
    top = y0 + n + 1
    for a in range(-n - 1, n + 2):                                                # a：棟に直交する向き
        h = y0 + (n + 1 - abs(a))
        for b_ in range(-m - 1, m + 2):                                           # b_：棟の向き
            x, z = (cx + b_, cz + a) if along == 'x' else (cx + a, cz + b_)
            if a == 0: put(x, h, z, full(ROOF)); put(x, h + 1, z, slab(ROOF))
            else: put(x, h, z, stair(ROOF, ('s' if a < 0 else 'n') if along == 'x' else ('e' if a < 0 else 'w')))
            if abs(b_) == m and abs(a) <= n:                                      # 妻
                for y in range(y0, h):
                    win = abs(a) <= 1 and y == y0 + 1
                    put(x, y, z, full('dark_oak_planks') if y == h - 1 else fence('dark_oak_planks') if win else full(WHITE))
            elif abs(b_) < m and abs(a) <= n:
                for y in range(y0, h):
                    if get(x, y, z) is None: put(x, y, z, full('dark_oak_planks'))
    for s in (-1, 1):
        x, z = (cx + s * (m + 1), cz) if along == 'x' else (cx, cz + s * (m + 1))
        put(x, top + 1, z, EGG)
    return top

def tenshu(cx, cz, g, ground, base=10, base_h=8, levels=3):
    """天守・櫓。石垣（底の半幅 base、高さ base_h）の上に levels 層。
    3層：板張りの1層（高さ6）→ 腰屋根＋千鳥破風 → 白い2層（5）→ 腰屋根＋千鳥破風 → 廻縁の最上階（4）→ 入母屋。
    2層：板張り（5）→ 腰屋根＋千鳥破風 → 白（4）→ 入母屋。1層：板張り（5）→ 入母屋。層ごとに半幅を2ずつ小さく。いちばん上の高さを返す。"""
    top = ishigaki(cx, cz, g + 1, base, base_h, ground)
    y = g + 1 + base_h
    h = top - 1
    plan = {3: [('wood', 6), ('white', 5), ('dark', 4)], 2: [('wood', 5), ('white', 4)], 1: [('wood', 5)]}[levels]
    for i, (style, hh) in enumerate(plan):
        storey(cx, cz, y, h, h, hh, style); y += hh
        y_eave = y
        y = skirt(cx, cz, y, h, h, out=3, inner=2)
        if i < len(plan) - 1:
            chidori(cx, cz, y - 3, h, h, 3 if i == 0 else 4)
            h -= 2
    return gable(cx, cz, y, max(1, h - 1), max(1, h - 1), 'x')
