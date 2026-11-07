#!/usr/bin/env python3
"""オープンワールドの一角 A：町の外れ（128×128）。

手本：RuneForge kingdoms の町の北西の外れ（~/ProjectS/night/refs/openworld/shots/ref_edge_*）。物差しは ruler.md の A。
  北の一段高い台地に町の最後の家並み。町から出る土の道は、台地を坂で下りて南西へ曲がりながら細くなる（幅 5 → 3）。
  道の南に柵で囲った畑と小屋、池。西の端は段の丘とトウヒの森で、野外へ続く。街灯は町側ほど密に。
  python3 towns/ow_edge.py [版の名前]
"""
import math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import put, get, full, log, stair, slab, fence, cross, _h
from town import Town, save, BROWN
import terraces as T
import trees as TR
import props as P

NAME = sys.argv[1] if len(sys.argv) > 1 else 'ow_edge'
L = T.Land(128, base=0, seed=12)
rng = random.Random(12)

# ---------------------------------------------------------------- 地形：北の町の台地（高さ 5）、西の段の丘
L.plateau(6, -46, 62, 5, rz=26, rough=0.12, steps=0)
L.plateau(-50, -10, 20, 11, rz=26, rot=0.3, steps=1)
L.plateau(-54, 34, 16, 15, rz=14, steps=1)
L.plateau(-44, 54, 12, 8, steps=0)
L.plateau(56, 40, 10, 7, steps=0)
L.pond(28, 34, 8)

# ---------------------------------------------------------------- 家の敷地（台地の上を均す）
HOUSES = [('timber', 0, (-14, -50)), ('fancy', 0, (24, -48)), ('timber', 180, (44, -36))]
for _, _, (x, z) in HOUSES:
    L.rect(x - 9, z - 7, x + 11, z + 8, 5, kind=4)

# ---------------------------------------------------------------- 道：町（北東）から台地を坂で下り、南西へ。町側は幅 5、外は 3
main = [(63, -26), (40, -24), (16, -26), (-4, -24), (-14, -14), (-12, 0), (-18, 14), (-30, 22), (-44, 26), (-63, 24)]
line, ys = L.path(main[:5], w=5, rise=3.0)
line2, ys2 = L.path(main[4:], w=3, rise=3.0)
L.path([(-12, 2), (2, 10), (14, 14)], w=2, rise=3.0)                  # 畑への踏み跡

YARD = (18, 2, 36, 14)                                                 # 作業場（v3 で試して戻した。--yard で入れる）：道と畑の間（x0, z0, x1, z1）
if '--yard' in sys.argv: L.rect(*YARD, L.h(26, 8), kind=5)
L.build(dict(T.MESA, pave=[('coarse_dirt', .35), ('dirt_path', .25), ('gravel', .15), ('packed_mud', .15), ('grass', .10)]))

# ---------------------------------------------------------------- 家（及第点の家の型）
t = Town((-64, 63, -64, 63), lambda x, z: 5)
for name, r, at in HOUSES:
    t.house(name, r, at, y=5, swap=BROWN if name == 'timber' and r == 180 else None, label=name)

# ---------------------------------------------------------------- 畑（柵で囲む）と小屋、荷
def farm(x0, z0, w, d, crop):
    g = L.h(x0, z0)
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            put(x, g, z, full('farmland_moist') if not edge else T.blk('grass'))
            if edge:
                if not (z == z0 + d - 1 and x == x0 + w // 2): put(x, g + 1, z, fence('spruce_planks'))
            elif (z - z0) % 4 == 2: put(x, g, z, {'s': 'water', 't': 'water_still'})
            else: put(x, g + 1, z, cross(crop))
for i, (fx, fz, w, d, c) in enumerate(((2, 16, 11, 9, 'wheat_stage7'), (16, 18, 9, 9, 'carrots_stage3'), (2, 28, 9, 8, 'potatoes_stage3'))):
    for x in range(fx, fx + w):
        for z in range(fz, fz + d): L.H[L.idx(x, z)] = L.h(fx, fz)
    farm(fx, fz, w, d, c)
P.shed(14, 30, L.h(14, 30), w=5, d=4)
P.woodpile(20, 30, L.h(20, 30), 'w')
P.cart(-6, -6, L.h(-6, -6), 'e')
P.notice_board(-6, 8, L.h(-6, 8), 'e')

# ---------------------------------------------------------------- 作業場：丸石の縁、干し草の山、樽、井戸、荷車、羊の囲い、物置
def yard():
    x0, z0, x1, z1 = YARD; g = L.h(x0, z0)
    for x in range(x0, x1 + 1):                                          # 縁：丸石と苔の丸石の低い縁石、入口を 2 か所空ける
        for z in (z0, z1):
            if not (x0 + 6 <= x <= x0 + 8 and z == z0): put(x, g + 1, z, slab('cobblestone' if _h(x, z, 1) < .7 else 'mossy_cobblestone'))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if not (z0 + 5 <= z <= z0 + 6 and x == x0): put(x, g + 1, z, slab('cobblestone' if _h(x, z, 2) < .7 else 'mossy_cobblestone'))
    hay = [(x0 + 2, z1 - 2), (x0 + 3, z1 - 2), (x0 + 2, z1 - 3), (x0 + 3, z1 - 3), (x0 + 4, z1 - 2), (x0 + 2, z1 - 4)]   # 干し草：2 段の山
    for i, (x, z) in enumerate(hay):
        put(x, g + 1, z, {'s': 'full', 't': 'hay_block_side', 'top': 'hay_block_top', 'bot': 'hay_block_top'})
        if i < 3: put(x, g + 2, z, {'s': 'full', 't': 'hay_block_side', 'top': 'hay_block_top', 'bot': 'hay_block_top'})
    for x, z, h in ((x1 - 2, z0 + 2, 2), (x1 - 3, z0 + 2, 1), (x1 - 2, z0 + 3, 1), (x1 - 5, z0 + 2, 1)):   # 樽：壁際に塊で
        for y in range(g + 1, g + 1 + h): put(x, y, z, {'s': 'full', 't': 'barrel_side', 'top': 'barrel_top', 'bot': 'barrel_bottom'})
    P.well(x0 + 10, z0 + 7, g, 's')
    P.cart(x1 - 7, z1 - 3, g, 'w')
    P.woodpile(x0 + 2, z0 + 2, g, 's')
    for x in range(x1 + 2, x1 + 12):                                     # 羊の囲い：作業場の東、柵で
        for z in range(z0 + 1, z0 + 10):
            if x in (x1 + 2, x1 + 11) or z in (z0 + 1, z0 + 9):
                if L.inb(x, z) and not (x == x1 + 2 and z == z0 + 5): put(x, L.h(x, z) + 1, z, fence('spruce_planks'))
    put(x1 + 7, L.h(x1 + 7, z0 + 5) + 1, z0 + 5, {'s': 'full', 't': 'hay_block_side', 'top': 'hay_block_top', 'bot': 'hay_block_top'})
if '--yard' in sys.argv: yard()

# ---------------------------------------------------------------- 街灯：町側から 6・12・24 と間を広げ、外の 40 マスは無し
acc, nxt, gap = 0, 6, 6
for k in range(1, len(line) + len(line2) - 1):
    pts = line + line2
    acc += math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1])
    if acc >= nxt and acc < 95:
        x, z = pts[k]; dx = pts[k + 1][0] - pts[k - 1][0]; dz = pts[k + 1][1] - pts[k - 1][1]; n = math.hypot(dx, dz) or 1
        off = 4.2 if k < len(line) else 3.2
        px, pz = int(round(x - dz / n * off)), int(round(z + dx / n * off))
        if L.inb(px, pz) and L.K[L.idx(px, pz)] in (0, 2): P.path_lamp(px, pz, L.h(px, pz), arm='e' if dz > 0 else 'w')
        gap = min(gap * 2, 24); nxt = acc + gap

# ---------------------------------------------------------------- 遊び（--play）：開拓者の町の出口。Lv1〜3 の最初の採取と狩り
PLAY = '--play' in sys.argv
if PLAY:
    import playmarks as PM
    PM.lay(L, rng, [
        ('town', 12, -48, '開拓者の町（死んだらここへ戻る）'),
        ('wood', -40, -28, ''), ('wood', -34, 8, ''), ('wood', -24, 46, ''),
        ('herb', 42, 20, ''), ('herb', 12, 50, ''), ('skin', -36, 36, ''), ('stone', 50, -8, ''),
        ('hunt', -24, 32, '狩り場 Lv1〜2（甲虫）'), ('hunt', 42, 50, '狩り場 Lv2〜3（狼）'),
        ('way', -50, 30, '根の丘への出口（復帰点の候補）'),
    ])

# ---------------------------------------------------------------- 木：西の段の上にトウヒの群れ、町と畑のまわりにまばらな広葉樹
CAMS = [(0, 50), (40, 30)]
SIGHT = [((0, 50), (0, 22)), ((40, 30), (21, 14))]                   # 比べる視点の見通し（手本と同じ向きの先 25 マス）は木で塞がない
def off_sight(x, z):
    if '--yard' not in sys.argv: return True                          # v1 は見通しを空けていない
    for (ax, az), (bx, bz) in SIGHT:
        t = max(0, min(1, ((x - ax) * (bx - ax) + (z - az) * (bz - az)) / ((bx - ax) ** 2 + (bz - az) ** 2)))
        if math.hypot(x - ax - t * (bx - ax), z - az - t * (bz - az)) < 7: return False
    return True
placed = [(x, z) for _, x, z, _ in PM.PLAY] if PLAY else []          # 木は遊びの印から離す
def ok(x, z, d):
    return (L.inb(x, z) and L.slope_ok(x, z) and all(math.hypot(x - a, z - b) >= d for a, b in placed)
            and all(math.hypot(x - a, z - b) >= 9 for a, b in CAMS) and off_sight(x, z) and (x, z) not in t.HOUSE_CELLS and get(x, L.h(x, z) + 1, z) is None)
for cx, cz in ((-52, -14), (-48, 6), (-56, 30), (-40, 52), (-30, -38)):
    for k in range(6):
        x, z = cx + rng.randint(-7, 7), cz + rng.randint(-7, 7)
        if ok(x, z, 4): TR.spruce(x, z, L.h(x, z), rng.randint(0, 9999), h=rng.randint(15, 22)); placed.append((x, z))
for k in range(120):
    x, z = rng.randint(-40, 62), rng.randint(-20, 62)
    if not ok(x, z, 12): continue
    if rng.random() < 0.5: TR.oak(x, z, L.h(x, z), rng.randint(0, 9999), size=rng.uniform(0.9, 1.2))
    else: TR.bush(x, z, L.h(x, z), rng.randint(0, 9999), big=rng.random() < 0.5)
    placed.append((x, z))
    if len(placed) >= 36: break
L.plants(density=0.25, skip=lambda x, z: (x, z) in t.HOUSE_CELLS)

if PLAY: PM.save_play(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', NAME))
V = lambda x, y, z: [-x, y, z]
save(NAME, {'overview': [V(0, 80, 95), V(0, 10, 0)]}, V(0, 10, 0), shadowR=100, fog=(150, 400))
print('木', len(placed), '家', [f[1] for f in t.FOOT])
