#!/usr/bin/env python3
"""見どころの部品（kit/poi.py）を並べて目の高さで見る見本の場。"""
import os, sys, random
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from vox import put, full, grass, water
from town import save
import poi as PO
for x in range(-20, 260):
    for z in range(-30, 90):
        put(x, -1, z, full('dirt')); put(x, 0, z, grass())
gh = lambda x, z: 0
r = random.Random(3)
for x in range(-7, 8):                                                       # 沈んだ塔の水
    for z in range(-7, 8): put(x, 0, z, water()); put(x, -1, z, full('mud'))
PO.sunken_tower(0, 0, 0, r, gh, 0)
PO.torii_shrine(40, 0, 0, r, gh, 's')
PO.stone_ring(80, 0, 0, r, gh)
PO.rock_arch(120, 0, 0, r, gh)
PO.snow_fort(170, 0, 0, r, gh)
PO.boss_ring(220, 0, 0, r, gh, 'meadow')
for i, st in enumerate(['sakura', 'marsh', 'forest', 'snow']):
    x0 = i * 50
    PO.ruin(x0, 50, 0, r, gh, st); PO.secret(x0 + 14, 50, 0, r, gh, st); PO.small_find(x0 + 20, 52, 0, r, gh, st)
for x in range(200, 260):                                                    # 洞窟の口を掘る斜面
    for z in range(40, 70):
        hh = max(0, min(10, 60 - z))
        for y in range(1, hh + 1): put(x, y, z, full('stone'))
PO.cave_mouth(215, 50, 0, r, gh, 's'); PO.dungeon_gate(240, 50, 0, r, gh, 's', 'meadow'); PO.waypoint(190, 60, 0, r, gh)
V = lambda x, y, z: [-x, y, z]
save('poi_gallery', {
    'tower': [V(-12, 8, 22), V(0, 6, 0)], 'shrine': [V(40, 4, 16), V(40, 4, -4)], 'ring': [V(80, 6, 22), V(80, 3, 0)],
    'arch': [V(120, 5, 26), V(120, 8, 0)], 'fort': [V(170, 10, 34), V(170, 4, 0)], 'boss': [V(220, 10, 28), V(220, 2, 0)],
    'ruins': [V(40, 8, 74), V(40, 2, 50)], 'ruins2': [V(130, 8, 74), V(130, 2, 50)], 'caves': [V(225, 5, 28), V(225, 3, 50)],
}, V(120, 2, 30), shadowR=160, fog=(200, 500))
