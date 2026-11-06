#!/usr/bin/env python3
"""クラス選択の場面（11/06）：森の空き地の、丸太の陣地。手本は RuneForge の職業選択（藍「あんな感じにしたい」）。
6本の丸太の腰掛けを東西に一列に並べ、その上にクラスの人（サーバーのマネキン）が座る。カメラは各腰掛けの南に立ち、
A / D で隣の腰掛けへすべる（ClassSelectScene.kt）。空き地は腰掛けの列とカメラの通り道の分だけ空け、まわりは森。

組む：python3 towns/class_camp.py。プレビューは out/class_camp/、ワールドは
server-minestom/src/main/resources/class-camp/（region/*.mca と level.dat）に書く。"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
from vox import vnoise, put, full, log
import vox
from mcworld import MCWorld

B = 48
Y0 = 64                      # 陣地の高さ
SEATS = [(-20 + 8 * i, 0) for i in range(6)]   # 腰掛けの真ん中。ClassSelectScene.SEATS と同じ
def nat(x, z):
    """陣地のまわりは陣地とほぼ同じ高さ（段の壁を作らない）、離れるほどうねる。"""
    far = min(1.0, max(0.0, math.hypot(x / 34.0, (z - 3) / 16.0) - 1) * 2)
    return Y0 + (2.5 * (vnoise(x, z, 30, 3) - 0.5) + 1.2 * (vnoise(x, z, 12, 4) - 0.5)) * (0.3 + 0.7 * far) + 2 * far * vnoise(x, z, 40, 5)
def camp(x, z):
    """空ける所：腰掛けの列と、その南のカメラの通り道。縁はうねらせる。"""
    return ((x / 31.0) ** 2 + ((z - 3) / 13.0) ** 2) < 1 + 0.25 * (vnoise(x, z, 9, 21) - 0.5)

T = Town((-B, B - 1, -B, B - 1), nat)
for x in range(-34, 35):
    for z in range(-14, 21):
        if camp(x, z): T.PAD[(x, z)] = (Y0, 'yard')
T.resolve(); T.fill()

# 陣地の地面：粗い土・ポドゾル・根の張った土・草のまだら。腰掛けのまわりほど踏まれて土。
for (x, z), y in list(T.H.items()):
    if not camp(x, z): continue
    worn = min(math.hypot(x - sx, z - sz) for sx, sz in SEATS)
    n = vnoise(x, z, 5, 31) - 0.25 * max(0, 4 - worn) / 4
    if n < 0.35: put(x, y, z, full('coarse_dirt'))
    elif n < 0.48: put(x, y, z, full('podzol_side', top='podzol_top', bottom='dirt'))
    elif n < 0.55: put(x, y, z, full('rooted_dirt'))

# 腰掛け：オークの丸太を東西に3本、端に1本を前後向きに添える（並べた感じを崩す）。
for i, (sx, sz) in enumerate(SEATS):
    y = T.H[(sx, sz)] + 1
    for dx in (-1, 0, 1):
        put(sx + dx, y, sz, log('oak_log', 'x'))
    k = -2 if i % 2 == 0 else 2
    put(sx + k, y, sz + (1 if i % 3 == 0 else 0), log('oak_log', 'z'))

def kind(x, z, r):
    return 'big' if r < 0.03 else 'birch' if r < 0.35 else 'oak' if r < 0.8 else 'spruce'
print('木・茂み', T.woods(kind=kind, clear=lambda x, z: camp(x, z) or ((x / 36.0) ** 2 + ((z - 3) / 17.0) ** 2) < 1))
T.ground_cover()

V = lambda x, y, z: [-x, y, z]
h = lambda x, z: T.H[(x, z)]
views = {'over': [V(0, Y0 + 40, 50), V(0, Y0, 0)], 'top': [V(0, Y0 + 120, 1), V(0, Y0, 0)]}
for i, (sx, sz) in enumerate(SEATS):
    views[f'seat{i}'] = [V(sx, h(sx, sz) + 2.6, sz + 4.5), V(sx, h(sx, sz) + 1.4, sz)]
print(save('class_camp', views, V(0, Y0, 0), shadowR=70, fog=(90, 260)))

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'server-minestom', 'src', 'main', 'resources', 'class-camp')
W = MCWorld(); W.add(vox.W)
print('region', W.write(out, 'ProjectS クラス選択の陣地', (0, Y0 + 2, 6)), '->', os.path.normpath(out))
