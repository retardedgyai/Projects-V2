"""和風の城の部品（11/06、藍「左右非対称」「作りが荒い」「もっと大きな規模で、簡略化しないで」）。
手本はらーなろっその黒牢城の回の天守（~/ProjectS/refs/build/rana/1WSmXolgmZE/keep/ の4秒ごとのコマ、notes.md）。決まりは rules/wafu.md。
どの部品も中心 (cx, cz) からの |u|・|v| で組むので、前後左右とも対称になる。
  ishigaki   石垣：建物より一回り広く、4段ごとに1マス引っ込む。上の縁に黒い帯と、垂れた支え
  storey     層の壁：'wood'（下は縦の板張りと細い柱、上2段は白）／'white'（白壁と柱）／'dark'（最上階、廻縁つき）
  skirt      腰屋根：1マス外へ出るごとに半マス下がる。軒先はギザギザ、軒下に支え、四隅は隅棟が跳ね上がって先に鯱
  chidori    千鳥破風：腰屋根の各面の真ん中に載る三角の屋根。中は白壁と格子窓
  gable      入母屋の上の切妻：棟の両端に鯱
  tenshu     天守：石垣 → 板張りの1層 → 白い2層 → 廻縁の最上階、層ごとに腰屋根と千鳥破風、上は入母屋
  ring_wall  四角い石垣の輪（城壁の土台）、tamon 多聞櫓（輪の上の長屋の回廊）、gatehouse 櫓門
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

def settle(x0, x1, z0, z1, y0, y1, maxgap=3):
    """隙間を埋める：屋根（深層岩の丸石）・白壁・柱の下に、3マス以下の空きがあって、その下に固い物があれば、白壁で埋めてつなぐ。
    腰屋根は壁の上1段を空けて載り、上の階の壁は下の天井まで届かないので、そのままだと屋根と上の階が浮く（11/06 浮いた部品の点検で見つけた）。
    階の中（天井が濃い板）と軒の下（下が遠い）は埋めない。"""
    def hard(b): return b is not None and b['s'] not in ('cross', 'carpet', 'lantern', 'chain', 'vine', 'water', 'fence')
    def top_kind(b): return b is not None and (b.get('t', '').startswith(ROOF) or b.get('t') in (WHITE, 'dark_oak_log'))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y1, y0, -1):
                if not top_kind(get(x, y, z)) or hard(get(x, y - 1, z)): continue
                gap = next((d for d in range(1, maxgap + 2) if hard(get(x, y - d, z))), None)
                if gap is None or gap > maxgap + 1 or gap == 1: continue
                for d in range(1, gap):
                    if get(x, y - d, z) is None: put(x, y - d, z, full(WHITE))   # 白で埋める（濃い板だと千鳥破風の白い三角が茶色になった）

def tenshu(cx, cz, g, ground, base=10, base_h=8, levels=3, flush=False):
    """天守・櫓。石垣（底の半幅 base、高さ base_h）の上に levels 層。flush なら層の壁を石垣の上の面と面一に（城壁の輪に組み込むとき）。
    3層：板張りの1層（高さ6）→ 腰屋根＋千鳥破風 → 白い2層（5）→ 腰屋根＋千鳥破風 → 廻縁の最上階（4）→ 入母屋。
    2層：板張り（6）→ 腰屋根＋千鳥破風 → 白（5）→ 入母屋。1層：板張り（6）→ 入母屋。層ごとに半幅を2ずつ小さく。いちばん上の高さを返す。"""
    top = ishigaki(cx, cz, g + 1, base, base_h, ground)
    y = g + 1 + base_h
    h = top if flush else top - 1
    plan = {3: [('wood', 6), ('white', 5), ('dark', 4)], 2: [('wood', 6), ('white', 5)], 1: [('wood', 6)]}[levels]
    for i, (style, hh) in enumerate(plan):
        storey(cx, cz, y, h, h, hh, style); y += hh
        y = skirt(cx, cz, y, h, h, out=3, inner=2)
        if i < len(plan) - 1:
            chidori(cx, cz, y - 3, h, h, 3 if i == 0 else 4)
            h -= 2
    top = gable(cx, cz, y, max(1, h - 1), max(1, h - 1), 'x')
    R = base + 4; settle(cx - R, cx + R, cz - R, cz + R, g + base_h, top + 3)
    return top


# ================================================================ 城壁：石垣の輪・多聞櫓・櫓門（藍 11/06「壁というよりはただの城をならべただけ」）
def _stone(x, y, z, seed=402):
    n = vox._h(x * 31 + y, z, seed)
    return 'cobblestone' if n < 0.45 else 'andesite' if n < 0.7 else 'stone' if n < 0.85 else 'tuff' if n < 0.95 else 'mossy_cobblestone'

SIDE_XY = {'e': lambda cx, cz, a, c: (cx + c, cz + a), 'w': lambda cx, cz, a, c: (cx - c, cz + a),
           's': lambda cx, cz, a, c: (cx + a, cz + c), 'n': lambda cx, cz, a, c: (cx + a, cz - c)}   # 辺に沿う a、外への c → (x, z)

def _gate_cell(u, v, side, half):
    if side == 'e': return u > 0 and abs(v) <= half
    if side == 'w': return u < 0 and abs(v) <= half
    if side == 's': return v > 0 and abs(u) <= half
    return v < 0 and abs(u) <= half

def ring_wall(cx, cz, W, g, ground, gate=None):
    """四角い石垣の輪。外の面は max(|u|,|v|) = W、上の3段（g+5〜g+7）は1マス引っ込む。内の面は W-3。崖の上では地面まで積む。
    上の縁（g+7）に磨いた深層岩の帯を1マス張り出し、その下に2マスおきの支え。gate=(辺, 半幅)：その辺の真ん中に幅 2*半幅+1・高さ5の通路。
    上の面の高さ（g+7）を返す。"""
    top = g + 7
    for u in range(-W, W + 1):
        for v in range(-W, W + 1):
            m = max(abs(u), abs(v))
            if m < W - 3 or m > W: continue
            x, z = cx + u, cz + v
            ing = gate is not None and _gate_cell(u, v, *gate)
            for y in range(min(ground(x, z) - 1, g), top + 1):
                if m == W and y >= g + 5: continue
                if ing and g + 1 <= y <= g + 5: put(x, y, z, None); continue
                face = m == W or (m == W - 1 and y >= g + 5) or m == W - 3
                put(x, y, z, full(_stone(x, y, z) if face else 'stone'))
            if m == W:
                put(x, top, z, full('polished_deepslate'))
                if (u + v) % 2 == 0: put(x, top - 1, z, wall('polished_deepslate'))
    return top

def tamon(cx, cz, W, y0, side, a0, a1):
    """多聞櫓：石垣の輪の1辺（side）の上、辺に沿う a0〜a1 に建つ長屋。床 y0（石垣の上の面 +1）、幅は内の面 W-3 から外の面 W-1 の3マス。
    外側は白壁に3マスおきの濃い柱、1つおきの間に格子窓。内側（中庭の側）は柱と手すりで開ける。天井の上に黒い切妻（棟は辺に沿う、1マスごとに1段、軒は両側へ1マス）。
    両端は妻（白い三角）で閉じる。隣の櫓の壁に突き当てて終わらせる。"""
    XY = SIDE_XY[side]
    def P(a, c, y, b):
        x, z = XY(cx, cz, a, c); put(x, y, z, b)
    out_f, in_f = INWARD[side], side                                              # 階段の背を棟へ向ける
    for a in range(a0, a1 + 1):
        end = a in (a0, a1); post = end or a % 3 == 0; bay = (abs(a) // 3) % 2 == 0
        for c in (W - 1, W - 2, W - 3):
            P(a, c, y0, full('dark_oak_planks'))                                  # 床
            for y in range(y0 + 1, y0 + 4):
                if c == W - 1: b = log('dark_oak_log') if post else fence('dark_oak_planks') if (y == y0 + 2 and bay) else full(WHITE)
                elif c == W - 3: b = log('dark_oak_log') if post else fence('dark_oak_planks') if y == y0 + 1 else None
                else: b = full(WHITE) if end else None
                P(a, c, y, b)
            P(a, c, y0 + 4, full('dark_oak_planks'))                              # 天井
        P(a, W, y0 + 4, stair(ROOF, out_f)); P(a, W - 1, y0 + 5, stair(ROOF, out_f))
        P(a, W - 2, y0 + 6, full(ROOF)); P(a, W - 2, y0 + 7, slab(ROOF))
        P(a, W - 3, y0 + 5, stair(ROOF, in_f)); P(a, W - 4, y0 + 4, stair(ROOF, in_f))
        if end: P(a, W - 2, y0 + 5, full(WHITE))                                  # 妻：棟の下の三角

def gatehouse(cx, cz, W, g, ground, side, half=3, span=8):
    """櫓門：輪の1辺の真ん中。通路（幅 2*half+1・高さ5、ring_wall が開けたもの）の両脇に石垣を外へ2マス張り出し、
    通路の口に濃い木の柱と横木、吊り灯り。その上に辺に沿って長い2階（白壁、幅5・長さ 2*span+1）、腰屋根、切妻。いちばん上の高さを返す。"""
    XY = SIDE_XY[side]
    top = g + 7
    for a in range(-span, span + 1):
        for c in (W + 1, W + 2):
            x, z = XY(cx, cz, a, c)
            for y in range(min(ground(x, z) - 1, g), top + 1):
                if c == W + 2 and y >= g + 5: continue
                if abs(a) <= half and g + 1 <= y <= g + 5: put(x, y, z, None); continue
                put(x, y, z, full(_stone(x, y, z, 403)))
            if c == W + 2:
                put(x, top, z, full('polished_deepslate'))
                if a % 2 == 0: put(x, top - 1, z, wall('polished_deepslate'))
    for a in range(-half, half + 1):                                              # 通路の床
        for c in range(W - 3, W + 3):
            x, z = XY(cx, cz, a, c); put(x, g, z, full('stone_bricks' if vox._h(x, z, 404) < 0.7 else 'mossy_stone_bricks'))
    for c in (W - 3, W + 2):                                                      # 口の枠：柱と横木、真ん中に吊り灯り
        for s in (-1, 1):
            x, z = XY(cx, cz, s * (half + 1), c)
            for y in range(g + 1, g + 6): put(x, y, z, log('dark_oak_log'))
        for a in range(-half - 1, half + 2):
            x, z = XY(cx, cz, a, c); put(x, g + 6, z, full('dark_oak_planks'))
        x, z = XY(cx, cz, 0, c); put(x, g + 5, z, HANG)
    xc, zc = XY(cx, cz, 0, W - 1)
    along = 'z' if side in 'ew' else 'x'
    hx, hz = (2, span) if along == 'z' else (span, 2)
    y = top + 1
    storey(xc, zc, y, hx, hz, 6, 'white'); y += 6
    y = skirt(xc, zc, y, hx, hz, out=2, inner=1)
    top_ = gable(xc, zc, y, max(1, hx - 1), max(1, hz - 1), along)
    settle(xc - hx - 3, xc + hx + 3, zc - hz - 3, zc + hz + 3, g + 7, top_ + 3)
    return top_
