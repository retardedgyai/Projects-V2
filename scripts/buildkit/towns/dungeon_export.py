#!/usr/bin/env python3
"""組んだ地図（towns/<名前>.py）を、マイクラのワールドに書き出す。実機で歩いて見るため（藍 11/06「実機でみていい？」）。
出力は repo の外：~/ProjectS/worlds/<名前>/。出現位置は out/<名前>/rooms.json の spawn。
サーバーは -Dprojects.frontierWorld=<出力フォルダ> で起動し、/frontier で入る（/hub で戻る）。
使い方：python3 towns/dungeon_export.py sakura_dungeon"""
import json, os, runpy, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'kit'))
import vox
from mcworld import MCWorld

name = sys.argv[1]
g = runpy.run_path(os.path.join(HERE, name + '.py'), run_name='__main__')
rooms = json.load(open(os.path.join(HERE, '..', 'out', name, 'rooms.json')))
out = sys.argv[2] if len(sys.argv) > 2 else os.path.expanduser('~/ProjectS/worlds/' + name)
W = MCWorld()
print('チャンク', W.add({**vox.W, **g.get('BAR', {})}))                         # BAR：見えない壁（プレビューには出さない）
spawn = tuple(rooms['spawn'])
print('region', W.write(out, 'ProjectS ' + name, spawn), '出現', spawn, '->', out)
json.dump({'spawn': list(spawn), 'name': name}, open(os.path.join(out, 'projects-map.json'), 'w'))
