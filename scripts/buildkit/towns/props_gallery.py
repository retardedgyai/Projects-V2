"""小物の見本の場：小物を1つずつ並べ、目の高さの視点で見る（11/02 藍「小物の造形があまりよくなかった」）。
新しい小物は kit/props.py に足し、ここに並べて撮ってから町に使う。"""

import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import put, get, full, grass, water, W
from town import save
import props as P
for x in range(-40, 50):
    for z in range(-14, 24):
        pool = 26 <= x <= 46 and z >= 4
        if pool:
            put(x, -4, z, full('gravel'))
            for y in (-3, -2, -1): put(x, y, z, water())
        else:
            put(x, -1, z, full('dirt')); put(x, 0, z, full('stone_bricks') if x >= 24 else grass())
P.lamp_post(-34, 2, 0, 'e')
P.bench(-29, 2, 0, 's', 2)
P.stall(-24, 2, 0, 's', 'red_wool', 'white_wool', [P.BARREL_OPEN, P.MELON])
P.cargo(-14, 0, 0, 's')
P.shed(-6, -2, 0)
P.garden(6, -2, 0)
P.woodpile(17, 1, 0)
P.pier(30, 4, -1, -4, 's', 6, 3)
P.boat(38, 8, -1, 'z', P.CRATE, 5); P.boat(41, 14, -1, 'x')
for x in range(26, 47, 4): P.pile_lamp(x, 3, -3, 0) if x not in (30, 34) else None
V = lambda x, y, z: [-x, y, z]
save('props', {
    'all': [V(0, 30, 45), V(4, 0, 2)],
    'lamp': [V(-31, 2.6, 9), V(-31, 2.5, 1)],
    'stall': [V(-19, 3, 9), V(-22.5, 2.2, 1)],
    'cargo': [V(-12, 2.6, 7), V(-12, 1.5, 0)],
    'shed': [V(-2, 3, 10), V(-3, 2.5, 0)],
    'shedw': [V(-14, 4, 6), V(-4, 3, 0)],
    'garden': [V(9, 4, 8), V(9, 0.5, 0)],
    'pier': [V(24, 4, 16), V(31, 0, 8)],
    'boats': [V(33, 3, 18), V(39, -0.5, 10)],
}, V(0, 2, 4), shadowR=50, fog=(80, 200))
