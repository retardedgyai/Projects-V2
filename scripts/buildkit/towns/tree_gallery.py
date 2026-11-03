"""木の見本の場：新しい木を1種類ずつ並べ、目の高さと斜め上から見る（11/03 藍「配置されてる木とかのクオリティが低すぎて酷い」）。"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import put, full, grass, cross
from town import save
import trees as TR

for x in range(-50, 60):
    for z in range(-20, 30):
        put(x, -1, z, full('dirt')); put(x, 0, z, grass())
        if (x * 7 + z * 13) % 5 == 0: put(x, 1, z, cross('short_grass'))
row = [(-40, TR.spruce), (-28, TR.spruce), (-18, TR.small_spruce), (-12, TR.small_spruce), (-2, TR.rana_oak), (10, TR.oak), (20, TR.birch), (28, TR.cherry), (44, TR.big_tree)]
TR.rana_oak(0, 0, 0, 7, 1.25) if False else None
for i, (x, fn) in enumerate(row):
    for zz in range(-1, 2):
        for xx in range(x - 1, x + 2): put(xx, 1, zz, None)
    fn(x, 0, 0, 100 + i)
for (x, sz, sd) in ((-2, 1.0, 5), (54, 1.3, 9)): pass
V = lambda x, y, z: [-x, y, z]
save('trees', {
    'all': [V(0, 22, -70), V(2, 12, 0)],
    'conifer': [V(-28, 8, -26), V(-28, 9, 0)],
    'broad': [V(8, 7, -24), V(8, 7, 0)],
    'big': [V(44, 14, -40), V(44, 16, 0)],
    'up': [V(0, 45, -40), V(0, 6, 0)],
}, V(0, 8, 0), shadowR=60, fog=(120, 300))
