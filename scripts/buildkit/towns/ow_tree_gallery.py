"""野外の木の見本の場：field_oak（大きさ 3 つ）と pine を並べ、目の高さと斜めから見る。"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import put, full, grass, cross
from town import save
import ow_trees as OT, trees as TR
for x in range(-60, 61):
    for z in range(-22, 23): put(x, -1, z, full('dirt')); put(x, 0, z, grass())
for i, (x, fn) in enumerate([(-48, lambda: OT.field_oak(-48, 0, 0, 3)), (-28, lambda: OT.field_oak(-28, 0, 0, 8, 1.15)), (-4, lambda: OT.field_oak(-4, 0, 0, 5, 1.5)),
                              (20, lambda: OT.pine(20, 0, 0, 2)), (32, lambda: OT.pine(32, 4, 0, 4, 20)), (48, lambda: TR.rana_oak(48, 0, 0, 7, 1.3))]): fn()
OT.stump(8, -8, 0, 1)
for y in (1, 2): put(-40, y, -8, full('white_wool'))     # 背 2 の物差し
V = lambda x, y, z: [-x, y, z]
save('ow_trees', {'all': [V(0, 16, -62), V(0, 10, 0)], 'up': [V(-10, 50, -45), V(-10, 6, 0)]}, V(0, 8, 0), shadowR=70, fog=(140, 320))
