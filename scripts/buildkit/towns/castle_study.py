#!/usr/bin/env python3
"""見本の場：天守1棟（kit/castle.py）を平らな地面に置いて、正面・角・目の高さ・軒の寄りから見る（藍 11/06「作りが荒い」「簡略化しないで」）。"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
import vox, castle as C

T = Town((-40, 40, -40, 40), lambda x, z: 0, lambda x, z: False)
T.fill()
for (x, z) in list(T.H):
    vox.put(x, 0, z, vox.full('gravel') if vox._h(x, z, 7) < 0.5 else vox.full('coarse_dirt'))
ground = lambda x, z: T.H.get((x, z), 0)
top = C.tenshu(0, 0, 0, ground)
C.tenshu(-30, 22, 0, ground, base=7, levels=2)
C.tenshu(-30, -22, 0, ground, base=5, levels=1)
print('天守の高さ', top)
V = lambda x, y, z: [-x, y, z]
save('castle_study', {
    'front': [V(-62, 22, 0), V(0, 20, 0)],
    'corner': [V(-44, 30, 44), V(0, 20, 0)],
    'eye': [V(-24, 2, 6), V(0, 14, 0)],
    'eave': [V(-17, 18, 14), V(-10, 15, 10)],
    'top': [V(-16, 44, 16), V(0, 34, 0)],
    'small': [V(-62, 22, 0), V(-30, 12, 0)],
}, V(0, 18, 0), shadowR=80, fog=(200, 500))
