"""港町の部品の見本の場（11/04）：kit/harbor.py の部品を1つずつ並べ、目の高さで見てから町に使う。"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import vox
from vox import put, full, water
from town import save
import harbor as HB

vox.TINT['water_still'] = (0x3A, 0xC4, 0xC8)
WY, BED = -1, -7
for x in range(-60, 70):
    for z in range(-40, 60):
        put(x, BED - 1, z, full('sand'))
        for y in range(BED, WY + 1): put(x, y, z, water())
HB.stilt_house(-50, -30, 9, 7, WY, 's', 'spruce', 'birch_planks')
HB.stilt_house(-34, -30, 7, 7, WY, 's', 'dark_oak', 'calcite', floors=2)
HB.stilt_house(-50, -6, 11, 7, WY, 'n', 'oak', 'mushroom_stem')
HB.round_quay(-26, 22, 9, 2, WY, BED)
HB.crane(-26, 31, 2, 's')
HB.ship(20, -32, WY, 40)
for i in range(-4, 5):
    for j in range(-4, 5):
        if i * i + j * j <= 20:
            for y in range(BED, 3): put(50 + i, y, 30 + j, full('calcite' if (i + j) % 3 else 'diorite'))
HB.lighthouse(50, 30, 2, 18, 3)
V = lambda x, y, z: [-x, y, z]
save('harbor_parts', {
    'all': [V(0, 50, 100), V(-5, 5, 0)],
    'houses': [V(-40, 8, 10), V(-42, 6, -20)],
    'roof': [V(-28, 12, -12), V(-44, 9, -26)],
    'ship': [V(-8, 10, -12), V(20, 14, -10)],
    'shipside': [V(50, 6, -12), V(20, 10, -12)],
    'light': [V(34, 10, 14), V(50, 16, 30)],
    'quay': [V(-12, 4, 40), V(-26, 1, 24)],
}, V(0, 4, 0), shadowR=90, fog=(150, 400))
