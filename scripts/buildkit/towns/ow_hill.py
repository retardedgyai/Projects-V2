#!/usr/bin/env python3
"""オープンワールドの一角 C：段の丘の山道と見張り台（128×128）。

手本：RuneForge kingdoms の西の段の丘（~/ProjectS/night/refs/openworld/shots/ref_hill_*）。物差しは ruler.md の A。
  東と南東は低い野原、北西へ丸い台地を 3 段重ねて登る。台地の崖はテラコッタの縦の筋と帯、上の縁は花崗岩と草の垂れ。
  野原から道が崖の切り通しを坂で登り、いちばん上の台地に見張り台。細い岩の柱を野原に 2〜3 本。
  python3 towns/ow_hill.py [版の名前]
"""
import math, os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import put, get, full, log, stair, slab, fence, cross, trap, LANTERN
from town import save
import terraces as T
import trees as TR
import props as P

NAME = sys.argv[1] if len(sys.argv) > 1 else 'ow_hill'
L = T.Land(128, base=0, seed=5)
rng = random.Random(5)

# ---------------------------------------------------------------- 台地（下の段から）
L.plateau(-24, -14, 44, 9, rz=38, rot=0.4, steps=1)          # 1 段目：西半分を覆う大きな台地
L.plateau(-34, -30, 26, 18, rz=22, rot=-0.3, steps=1)        # 2 段目
L.plateau(-40, -42, 13, 27, rz=12, steps=1)                  # 3 段目：見張り台の頂
L.plateau(26, -44, 17, 11, rz=13, rot=0.8, steps=1)          # 北東の台地
L.plateau(-46, 36, 18, 13, rz=16, steps=1)                   # 南西の台地
L.plateau(-42, 46, 9, 20, steps=0)                           # その上の小さな台
L.plateau(40, -6, 6, 15, steps=0, rough=0.2)                 # 野原の岩の柱
L.plateau(10, 40, 5, 11, steps=0, rough=0.2)
L.plateau(52, 30, 4, 8, steps=0, rough=0.2)
L.pond(28, 22, 9)

# ---------------------------------------------------------------- 山道：南東の端から台地を切り通しで登り、頂の見張り台へ
route = [(63, 58), (44, 50), (26, 44), (8, 26), (2, 8), (-6, 2), (-14, -2), (-12, -14), (-20, -22), (-28, -24), (-34, -36), (-38, -44)]
line, ys = L.path(route, w=3, rise=3.2)
TOWER = (-40, -44)
L.flatten(TOWER[0], TOWER[1], 6, int(L.H[L.idx(*TOWER)]), kind=4, blend=2)
side = [(-6, 2), (-20, 10), (-36, 22), (-46, 34)]            # 脇道：南西の台地へ
L.path(side, w=2, rise=3.0)

L.build(T.MESA)

# ---------------------------------------------------------------- 見張り台（下 1/3 は石、上は木。最上階を 1 マス張り出す）
def watchtower(cx, cz, g, door='s'):
    S = [('stone_bricks', .45), ('cobblestone', .25), ('andesite', .15), ('mossy_stone_bricks', .15)]
    def st(x, y, z): return full(L.pick(S, x, y, z, 51))
    for y in range(g + 1, g + 8):                                    # 石の胴：7×7 の外周、角を 1 マス欠いて丸く
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if (abs(dx) == 3) == (abs(dz) == 3) and not (abs(dx) < 3 and abs(dz) < 3) or (abs(dx) < 3 and abs(dz) < 3): continue
                put(cx + dx, y, cz + dz, st(cx + dx, y, cz + dz))
    for dx, dz, f in ((-4, -2, 'e'), (-4, 2, 'e'), (4, -2, 'w'), (4, 2, 'w'), (-2, -4, 's'), (2, -4, 's'), (-2, 4, 'n'), (2, 4, 'n')):   # 足元の控え壁
        for y in range(g + 1, g + 4): put(cx + dx, y, cz + dz, st(cx + dx, y, cz + dz))
        put(cx + dx, g + 4, cz + dz, stair('stone_bricks', f))
    put(cx, g + 1, cz + 3, {'s': 'door', 't': 'spruce_door_bottom', 'f': 'n'}); put(cx, g + 2, cz + 3, {'s': 'door', 't': 'spruce_door_top', 'f': 'n'})
    put(cx, g + 3, cz + 3, stair('stone_bricks', 'n', 'top'))
    for dx, dz in ((-3, 0), (3, 0), (0, -3)):                        # 細い窓（暗い板で穴に見せる）
        put(cx + dx, g + 5, cz + dz, full('black_concrete')); put(cx + dx, g + 6, cz + dz, full('black_concrete'))
    # 木の上部：角に丸太、壁は板、窓
    y0 = g + 8
    for y in range(y0, y0 + 6):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if not (abs(dx) == 3 or abs(dz) == 3): continue
                if abs(dx) == 3 and abs(dz) == 3: put(cx + dx, y, cz + dz, log('spruce_log')); continue
                if y in (y0, y0 + 5): put(cx + dx, y, cz + dz, log('dark_oak_log', 'x' if abs(dz) == 3 else 'z')); continue
                if 2 <= y - y0 <= 3 and (abs(dx) <= 1 or abs(dz) <= 1): continue      # 窓（各面の真ん中 3 マス）
                put(cx + dx, y, cz + dz, full('spruce_planks') if (dx + dz + y) % 5 else full('stripped_spruce_log'))
    # 張り出した見張りの床（9×9）と手すり
    yf = y0 + 6
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            put(cx + dx, yf, cz + dz, full('dark_oak_planks'))
            if abs(dx) == 4 or abs(dz) == 4:
                put(cx + dx, yf + 1, cz + dz, fence('spruce_planks'))
            if (abs(dx) == 4) != (abs(dz) == 4) and (abs(dx) == 4 and abs(dz) <= 3 or abs(dz) == 4 and abs(dx) <= 3):
                f = 'w' if dx == 4 else 'e' if dx == -4 else 'n' if dz == 4 else 's'
                put(cx + dx, yf - 1, cz + dz, stair('spruce_planks', f, 'top'))   # 張り出しの下の持ち送り
    for dx, dz in ((-4, -4), (4, -4), (-4, 4), (4, 4)):                       # 屋根の柱
        for y in range(yf + 1, yf + 5): put(cx + dx, y, cz + dz, log('spruce_log'))
    # 屋根：四角すい、軒は 1 マス外
    for k in range(0, 6):
        r = 5 - k; y = yf + 5 + k
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) != r: continue
                if abs(dx) == r and abs(dz) == r: put(cx + dx, y, cz + dz, full('dark_oak_planks')); continue
                f = 'e' if dx == -r else 'w' if dx == r else 's' if dz == -r else 'n'
                put(cx + dx, y, cz + dz, stair('dark_oak_planks', f))
    put(cx, yf + 11, cz, log('spruce_log')); put(cx, yf + 12, cz, fence('spruce_planks')); put(cx, yf + 13, cz, fence('spruce_planks'))
    for k in range(4): put(cx + 1 + k, yf + 13 - (k > 1), cz, full('red_wool'))   # 旗
    put(cx, yf + 4, cz, LANTERN)
    return yf

g = int(L.H[L.idx(*TOWER)])
watchtower(TOWER[0], TOWER[1], g)
P.woodpile(TOWER[0] + 6, TOWER[1] + 1, g, 'w')

# ---------------------------------------------------------------- 道の灯りと休み場
for k in range(30, len(line) - 30, 46):
    x, z = line[k]; dx = line[k + 1][0] - line[k - 1][0]; dz = line[k + 1][1] - line[k - 1][1]
    n = math.hypot(dx, dz) or 1
    px, pz = int(round(x - dz / n * 3.2)), int(round(z + dx / n * 3.2))
    if L.inb(px, pz) and L.K[L.idx(px, pz)] in (0, 2):
        P.path_lamp(px, pz, int(L.H[L.idx(px, pz)]), arm='e' if dz > 0 else 'w')

# ---------------------------------------------------------------- 木：台地の縁の内側 2〜4 マスに 2〜4 本の群れ。野原はまばら
import numpy as np
H = L.H
cliffish = np.zeros_like(H, bool)
for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    cliffish |= (H - np.roll(np.roll(H, a, 0), b, 1)) >= 3
near_cliff = cliffish.copy()
for _ in range(4):
    near_cliff = near_cliff | np.roll(near_cliff, 1, 0) | np.roll(near_cliff, -1, 0) | np.roll(near_cliff, 1, 1) | np.roll(near_cliff, -1, 1)
cands = [(j - 64, i - 64) for i in range(4, 124, 3) for j in range(4, 124, 3) if near_cliff[i, j] and not cliffish[i, j] and H[i, j] >= 8]
rng.shuffle(cands)
placed = []
def far(x, z, d): return all(math.hypot(x - a, z - b) >= d for a, b in placed)
groups = 0
for x, z in cands:
    if groups >= 7: break
    if not far(x, z, 22) or math.hypot(x - TOWER[0], z - TOWER[1]) < 9: continue
    groups += 1
    for k in range(rng.randint(2, 4)):
        tx, tz = x + rng.randint(-4, 4), z + rng.randint(-4, 4)
        if not L.inb(tx, tz) or not L.slope_ok(tx, tz) or not all(math.hypot(tx - a, tz - b) >= 4 for a, b in placed): continue
        TR.spruce(tx, tz, L.h(tx, tz), rng.randint(0, 9999), h=rng.randint(15, 22)); placed.append((tx, tz))
for k in range(26):                                                  # 野原と低い所：小さいトウヒと茂み
    tx, tz = rng.randint(-60, 60), rng.randint(-60, 60)
    if not L.slope_ok(tx, tz) or not far(tx, tz, 7): continue
    if rng.random() < 0.5: TR.small_spruce(tx, tz, L.h(tx, tz), rng.randint(0, 9999))
    else: TR.bush(tx, tz, L.h(tx, tz), rng.randint(0, 9999), big=rng.random() < 0.4)
    placed.append((tx, tz))
L.plants(density=0.22)

# ---------------------------------------------------------------- 遊び（--play、木の後に置いて木の並びを変えない）：Lv5〜8 の採石と採鉱の丘。登るほど強い。頂は見晴らし
PLAY = '--play' in sys.argv
if PLAY:
    import playmarks as PM
    PM.lay(L, rng, [
        ('view', TOWER[0], TOWER[1], '見張り台（見晴らし：遺跡の森と岩柱の桜が見える）'),
        ('ore', -22, -32, ''), ('ore', 24, -40, ''), ('ore', -46, 30, ''),
        ('stone', 48, 4, ''), ('stone', -8, -20, ''), ('stone', -52, 12, ''), ('herb', 20, 32, ''), ('wood', 52, 42, ''),
        ('hunt', 36, 48, '狩り場 Lv5（野原の狼）'), ('hunt', -8, -6, '狩り場 Lv6〜7（段の上の猪）'), ('hunt', -30, -28, '狩り場 Lv8（頂の守り）'),
        ('way', -30, -40, '見張り台の下（復帰点の候補）'),
    ])


if PLAY: PM.save_play(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', NAME))
V = lambda x, y, z: [-x, y, z]
save(NAME, {'overview': [V(0, 80, 95), V(0, 10, 0)]}, V(0, 10, 0), shadowR=100, fog=(150, 400))
print('木', len(placed))
