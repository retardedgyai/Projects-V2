#!/usr/bin/env python3
"""大きな地形の試しの場（11/03）：尾根の線 → 侵食 → 崩れ を、山脈1つで確かめる。陰影の地図（PNG）と 3D を書き出す。"""
import math, os, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import vnoise
import terrain as TE

N = 192; X0 = Z0 = -N // 2
G = TE.Grid(X0, Z0, N, N, 8.0)
def nz(x, z): return vnoise(x, z, 14, 3) + 0.5 * vnoise(x, z, 6, 4) - 0.75
ridges = [
    ([(-80, -40, 70), (-40, -55, 92), (-5, -45, 80), (30, -60, 98), (75, -40, 72)], 80),   # 主の尾根
    ([(-40, -55, 90), (-50, -10, 50), (-70, 20, 26)], 44),                                    # 南へ下る枝尾根
    ([(30, -60, 96), (45, -15, 55), (40, 20, 24)], 46),
    ([(-5, -45, 78), (5, -10, 40)], 34),
]
t = time.time(); TE.ridge_field(G, ridges, nz); print('尾根', round(time.time() - t, 1))
G0 = list(G.h); print('盛った後の最高', round(max(G0), 1))
t = time.time(); TE.erode2(G); TE.smooth_gentle(G); print('侵食', round(time.time() - t, 1))
t = time.time(); TE.talus(G, 1.6, 4); print('崩れ', round(time.time() - t, 1))

from PIL import Image
def shade(h, path):
    im = Image.new('RGB', (N, N))
    for z in range(1, N - 1):
        for x in range(1, N - 1):
            k = z * N + x
            dx = h[k + 1] - h[k - 1]; dz = h[k + N] - h[k - N]
            l = max(0.0, min(1.0, 0.55 + (-dx * 0.7 + -dz * 0.7) * 0.18))
            e = h[k] / 100.0
            c = (int(90 + 140 * l * (0.6 + 0.4 * e)), int(110 + 120 * l * (0.6 + 0.4 * e) - 30 * (1 - e)), int(90 + 130 * l))
            im.putpixel((x, z), c)
    im.resize((N * 3, N * 3), Image.NEAREST).save(path)
S = os.environ.get('OUT', '.')
shade(G0, S + '/terrain_before.png'); shade(G.h, S + '/terrain_after.png')
print('max', round(max(G.h), 1), 'min', round(min(G.h), 1))

# ---- ブロックで組む（上面を塗り分け、下は岩）
from vox import put, full, grass
from town import save
TE.flow_cut(G, 0.08)
def blk(name): return grass() if name == 'grass' else full(name)
for z in range(G.z0 + 1, G.z0 + N - 1):
    for x in range(G.x0 + 1, G.x0 + N - 1):
        h = int(round(G.get(x, z)))
        low = min(int(round(G.get(x + dx, z + dz))) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for y in range(min(low, h - 1), h): put(x, y, z, full('stone' if (x + y + z) % 5 else 'andesite'))
        put(x, h, z, blk(TE.paint(G, x, z, 56)))
V = lambda x, y, z: [-x, y, z]
print(save('terrain', {'over': [V(0, 150, 150), V(0, 20, -20)], 'side': [V(-120, 70, 60), V(0, 40, -40)], 'close': [V(-20, 60, 10), V(-10, 60, -50)]}, V(0, 30, 0), shadowR=120, fog=(250, 600)))
