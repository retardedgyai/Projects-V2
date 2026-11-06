#!/usr/bin/env python3
"""手本のマイクラの地図（ワールドのフォルダ）を、プレビューの out/<名前>/ に書き出す。藍 11/06「学習用のマップ、ダンジョンとかも入ってるから一旦見れる？」。

  python3 towns/mc_view.py <ワールド> <名前> [x0 z0 x1 z1] [--ymin Y] [--cache 読んだ配列の pkl]

中身の見えないブロック（6面とも不透明に囲まれた物）は落とす。地図の真ん中を原点に寄せ、視点は全体・斜め4方向・真上。
"""
import argparse, os, pickle, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
import numpy as np
import mca_read as M, mc_to_vox as C, vox
from town import save

ap = argparse.ArgumentParser()
ap.add_argument('world'); ap.add_argument('name'); ap.add_argument('box', nargs='*', type=int)
ap.add_argument('--ymin', type=int, default=None); ap.add_argument('--ymax', type=int, default=None); ap.add_argument('--cache')
ap.add_argument('--views', default=''); ap.add_argument('--sea', type=int, default=None); ap.add_argument('--open', type=int, default=None)
a = ap.parse_args()

if a.cache and os.path.exists(a.cache): arr, pal, o = pickle.load(open(a.cache, 'rb'))
else: arr, pal, o = M.dense(a.world)
if a.box:                                                                         # 切り出し（ワールドの座標）
    x0, z0, x1, z1 = a.box
    arr = arr[:, max(0, z0 - o[2]):z1 - o[2] + 1, max(0, x0 - o[0]):x1 - o[0] + 1]; o = (max(x0, o[0]), o[1], max(z0, o[2]))
if a.ymin is not None: arr = arr[max(0, a.ymin - o[1]):]; o = (o[0], max(a.ymin, o[1]), o[2])
if a.ymax is not None: arr = arr[:a.ymax - o[1] + 1]

VB = [C.convert(n, pr) for n, pr in pal]
solid = np.array([b is not None and b['s'] == 'full' and not any(k in b['t'] for k in ('glass', 'ice')) for b in VB])   # 葉は不透明とみなす（茂みの中身を落とす）
keep = np.array([b is not None for b in VB])
wat = np.array([b is not None and b['s'] == 'water' for b in VB])
S = solid[arr] | wat[arr]                                                         # 水も覆うものとみなす（水の下の底は書かない）
def nb(m, ax, d, edge):
    sh = np.roll(m, d, ax); idx = [slice(None)] * 3; idx[ax] = 0 if d == 1 else -1; sh[tuple(idx)] = edge; return sh
inner = S.copy()
for ax in range(3):
    for d in (1, -1): inner &= nb(S, ax, d, not (ax == 0 and d == -1))           # 切り口の外は埋まっているとみなす（底と四方の断面を書かない）。上だけは空
W_ = wat[arr]
vis = keep[arr] & ~inner
vis &= ~(W_ & nb(S, 0, -1, False))                                               # 水は水面だけ
if a.sea is not None:                                                             # 陸の柱から sea マスより遠い海は落とす
    near = (solid[arr] & ~W_).any(0) & ~W_.any(0)
    for _ in range(a.sea):
        n2 = near.copy(); n2[1:] |= near[:-1]; n2[:-1] |= near[1:]; n2[:, 1:] |= near[:, :-1]; n2[:, :-1] |= near[:, 1:]; near = n2
    vis &= ~(W_ & ~near[None])
if a.open is not None:                                                            # 地形の地図：空から届く空気に面した物だけ（洞窟の中の壁は落とす）。open 回だけ横と下へ広げて張り出しの下も拾う
    air = ~S
    op = np.zeros_like(air); col = np.ones(air.shape[1:], bool)
    for y in range(air.shape[0] - 1, -1, -1): col &= air[y]; op[y] = col
    for _ in range(a.open):
        n2 = op.copy()
        n2[:-1] |= op[1:]; n2[:, 1:] |= op[:, :-1]; n2[:, :-1] |= op[:, 1:]; n2[:, :, 1:] |= op[:, :, :-1]; n2[:, :, :-1] |= op[:, :, 1:]
        op = n2 & air
    touch = op.copy()
    for ax in range(3):
        for d in (1, -1): touch |= nb(op, ax, d, ax == 0 and d == -1)
    vis &= touch
ys, zs, xs = np.nonzero(vis)
H, Z, X = arr.shape
cx, cz = X // 2, Z // 2
base = int(np.percentile(ys, 5)) if len(ys) else 0
for y, z, x in zip(ys, zs, xs):
    vox.W[(int(x) - cx, int(y) - base, int(z) - cz)] = VB[arr[y, z, x]]
miss = sorted({pal[i][0] for i in np.unique(arr[vis == False]) if i and VB[i] is None})[:40]
print('落とした種類（形が無い）', miss)
print('見える', len(ys), '大きさ', X, Z, H, '原点', o)

R = max(X, Z); top = int(ys.max() - base) if len(ys) else 40
V = lambda x, y, z: [-x, y, z]
views = {
    'overview': [V(0, top + R * 0.55, R * 0.75), V(0, top * 0.3, 0)],
    'ne': [V(R * 0.55, top + R * 0.3, -R * 0.55), V(0, top * 0.3, 0)],
    'nw': [V(-R * 0.55, top + R * 0.3, -R * 0.55), V(0, top * 0.3, 0)],
    'se': [V(R * 0.55, top + R * 0.3, R * 0.55), V(0, top * 0.3, 0)],
    'sw': [V(-R * 0.55, top + R * 0.3, R * 0.55), V(0, top * 0.3, 0)],
    'top': [V(0, top + R * 1.3, 1), V(0, 0, 0)],
}
for s in filter(None, a.views.split('|')):                                         # 追加の視点：名前:x,y,z,tx,ty,tz（ワールドの座標）
    k, v = s.split(':'); n = [float(t) for t in v.split(',')]
    views[k] = [V(n[0] - o[0] - cx, n[1] - o[1] - base, n[2] - o[2] - cz), V(n[3] - o[0] - cx, n[4] - o[1] - base, n[5] - o[2] - cz)]
print('書き出し', save(a.name, views, V(0, top * 0.3, 0), shadowR=int(R * 0.8), fog=(R * 1.2, R * 3.5)))
