#!/usr/bin/env python3
"""開拓大陸（1000×1000）を、マイクラのワールドに書き出す。256 マスずつ組んで（端は 10 マス余分に組み、木の切れ端を捨てる）、
kit/mcworld.py で region/*.mca と level.dat を書く。出力は repo の外：~/ProjectS/worlds/frontier1000/
使い方：python3 towns/frontier_export.py [出力フォルダ]"""
import json, os, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox
from mcworld import MCWorld
import frontier_section as FS

out = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/ProjectS/worlds/frontier1000')
W = MCWorld(); t0 = time.time(); TILE = 256; M = 10
for tz in range(0, 1000, TILE):
    for tx in range(0, 1000, TILE):
        vox.W.clear()
        x0, z0 = max(2, tx - M), max(2, tz - M)
        x1, z1 = min(997, tx + TILE + M), min(997, tz + TILE + M)
        FS.build(x0, z0, x1, z1, logs=12)
        n = W.add(vox.W, (tx, tx + TILE), (tz, tz + TILE))
        print('区画', tx, tz, 'チャンク', n, round(time.time() - t0), 's', flush=True)
hx, hz = FS.INFO['regions'][0]['x'], FS.INFO['regions'][0]['z']
spawn = (hx, int(round(FS.HM[hz - 30, hx])) + 2, hz - 30)
print('region', W.write(out, 'ProjectS 開拓大陸（1000×1000 試作）', spawn), '出現', spawn, '->', out)
json.dump({'spawn': spawn, 'size': 1000}, open(os.path.join(out, 'projects-map.json'), 'w'))
