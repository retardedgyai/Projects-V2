#!/usr/bin/env python3
"""野外の地図（towns/ow_frontier.py）を、マイクラのワールドに書き出す。実機で歩いて見るため。
出力は repo の外：~/ProjectS/worlds/<名前>/。出現位置は out/<名前>/play.json の spawn（地図の座標）。
サーバーは -Dprojects.frontierWorld=<出力フォルダ> で起動し、/frontier で入る（/creative で飛ぶ、/hub で戻る）。
使い方：python3 towns/ow_export.py [名前]"""
import json, os, runpy, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import vox, mcworld
from mcworld import MCWorld
mcworld.FULL_FIX.update({'farmland_moist': 'farmland', 'bone_block_side': 'bone_block'})
name = sys.argv[1] if len(sys.argv) > 1 else 'ow_frontier'
sys.argv = ['ow_frontier.py', name]
runpy.run_path(os.path.join(HERE, 'ow_frontier.py'), run_name='__main__')
play = json.load(open(os.path.join(HERE, '..', 'out', name, 'play.json')))
ox, oz = play['origin']; sx, sy, sz = play['spawn']
out = os.path.expanduser('~/ProjectS/worlds/' + name)
W = MCWorld(); print('チャンク', W.add({(x, y + 60, z): b for (x, y, z), b in vox.W.items() if b is not None}))
spawn = (sx - ox, sy + 60, sz - oz)
print('region', W.write(out, 'ProjectS ' + name, spawn), '出現', spawn, '->', out)
json.dump({'spawn': list(spawn), 'name': name}, open(os.path.join(out, 'projects-map.json'), 'w'))
