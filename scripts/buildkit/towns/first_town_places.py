#!/usr/bin/env python3
"""開拓者の町（first_town.py）の場所の一覧を out/first_town/rooms.json に書く（散り桜の社の rooms.json と同じ作り）。
サーバーはこれを読んで、出てくる場所・施設・鍛冶師を置く。first_town.py は変えない（デザインのセッションの持ち物）。
使い方：python3 towns/first_town_places.py → python3 towns/dungeon_export.py first_town ../../server-minestom/src/main/resources/lobby/first_town（level.dat と projects-map.json は消す）"""
import contextlib, io, json, os, runpy, sys
HERE = os.path.dirname(os.path.abspath(__file__))
B = os.path.join(HERE, '..')
sys.path.insert(0, os.path.join(B, 'kit'))
with contextlib.redirect_stdout(io.StringIO()):
    g = runpy.run_path(os.path.join(B, 'towns', 'first_town.py'), run_name='__main__')
from vox import get
SOFT = ('cross', 'carpet', 'vine', 'lantern', 'chain')
def free(x, y, z): b = get(x, y, z); return b is None or b['s'] in SOFT
def stand(x, z, near=40):
    """x, z の、near に近い立てる高さ（足の置き場の1つ上）。"""
    for d in range(0, 30):
        for y in (near - d, near + d):
            b = get(x, y - 1, z)
            if b is not None and b['s'] not in SOFT and free(x, y, z) and free(x, y + 1, z): return y
    raise SystemExit(f'立てる所がない {x},{z}')
def spot(x, z, near, yaw=0):
    return [x, stand(x, z, near), z, yaw]
PCX, PCZ, PY, GATE_Z = g['PCX'], g['PCZ'], g['PY'], g['GATE_Z']
gy = stand(0, GATE_Z, 21); sy = stand(3, -12, 25)
PLACES = {
    # yaw：マイクラの向き（0 南・90 西・180 北・-90 東）
    'spawn': spot(PCX, PCZ + 7, PY + 1, 180),                                   # 広場の焚き火の南、大階段と館を向く
    # 遠征受付と自分のコロニーは 11/20 には置かない（藍「遠征は使わないかも」）。掲示板と桟橋は飾りのまま。
    'facilities': {
        'DUNGEON': {'name': 'ダンジョンの門', 'at': [0, gy, GATE_Z],          # 大階段の途中の石の門
                    'click': [[x, y, z] for x in (-5, -4, 4, 5) for z in (GATE_Z - 1, GATE_Z) for y in range(gy, gy + 3)]},
        'WORKSHOP': {'name': '鍛冶工房', 'at': [-51, 18, 8],                    # 鍛冶屋の金床
                     'click': [[-51, 18, 8], [-55, 18, 5], [-50, 18, 5]]},
        'MASTERY': {'name': '熟練の手引き', 'at': [-27, 19, -12],               # 訓練の的の前
                    'click': [[x, y, -14] for x in (-30, -27, -24) for y in (19, 20)]},
        'SUPPLIES': {'name': '交易市場', 'at': [9, 17, 34],                     # 広場の東の屋台
                     'click': [[11, 17, 33], [11, 17, 34], [11, 18, 33], [11, 18, 34]]},
        'STORAGE': {'name': '所持品目録', 'at': [3, sy, -11],                    # 館の前の段：町に物がないので、place の樽と書見台をサーバーが置く
                    'click': [[x, sy, -12] for x in (2, 3, 4)],
                    'place': [[2, sy, -12, 'barrel'], [3, sy, -12, 'lectern'], [4, sy, -12, 'barrel']]},
    },
    'smith': [-51.5, 18, 8.5, -90],                                              # 金床のすぐ奥（西）、東の入口を向く
}
out = os.path.join(B, 'out', 'first_town'); os.makedirs(out, exist_ok=True)
json.dump(PLACES, open(os.path.join(out, 'rooms.json'), 'w'), ensure_ascii=False, indent=1)
print(json.dumps(PLACES, ensure_ascii=False)[:600])
