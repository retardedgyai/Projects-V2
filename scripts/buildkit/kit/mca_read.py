"""マイクラのワールド（region/*.mca）を読んで、ブロックを (x, y, z) -> (名前, 性質) で返す。手本の地図を見るための読み手。
書き手は kit/mcworld.py（こちらはその逆）。チャンクは 1.18 以降の形（sections / block_states）。

  for (x, y, z), (name, props) in blocks('<ワールド>'): ...
"""
import gzip, io, math, os, struct, zlib
import numpy as np

AIR = {'air', 'cave_air', 'void_air'}


def _nbt(buf):
    """NBT を Python の値に（compound は dict、list は list、配列は numpy）。"""
    f = io.BytesIO(buf)
    def rd(fmt, n): return struct.unpack('>' + fmt, f.read(n))[0]
    def s(): return f.read(rd('H', 2)).decode('utf-8', 'replace')
    def pay(t):
        if t == 1: return rd('b', 1)
        if t == 2: return rd('h', 2)
        if t == 3: return rd('i', 4)
        if t == 4: return rd('q', 8)
        if t == 5: return rd('f', 4)
        if t == 6: return rd('d', 8)
        if t == 7: n = rd('i', 4); return f.read(n)
        if t == 8: return s()
        if t == 9:
            et = rd('b', 1); n = rd('i', 4)
            return [pay(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = rd('b', 1)
                if tt == 0: return d
                k = s(); d[k] = pay(tt)
        if t == 11: n = rd('i', 4); return np.frombuffer(f.read(4 * n), '>i4')
        if t == 12: n = rd('i', 4); return np.frombuffer(f.read(8 * n), '>i8')
        raise ValueError('NBT の型 %d' % t)
    t = rd('b', 1); s()
    return pay(t)


def chunks(world):
    """ワールドの全チャンクの NBT を出す。"""
    rd = os.path.join(world, 'region')
    for fn in sorted(os.listdir(rd)):
        if not fn.endswith('.mca'): continue
        data = open(os.path.join(rd, fn), 'rb').read()
        if len(data) < 8192: continue
        for i in range(1024):
            off = int.from_bytes(data[i * 4:i * 4 + 3], 'big'); cnt = data[i * 4 + 3]
            if not off or not cnt: continue
            p = off * 4096
            ln = struct.unpack('>i', data[p:p + 4])[0]; comp = data[p + 4]
            raw = data[p + 5:p + 4 + ln]
            try: buf = zlib.decompress(raw) if comp == 2 else gzip.decompress(raw) if comp == 1 else raw
            except Exception: continue
            yield _nbt(buf)


def _entry(p):
    """パレットの1つ：{'Name', 'Properties'}（1.18〜）、文字だけ・{'id', 'properties'}・{'': 値}（26.x）を読む。"""
    if isinstance(p, dict) and '' in p: p = p['']                                # 26.x：型の混ざった list は {'': 値} で包まれる
    if isinstance(p, dict) and 'id' in p: return p['id'].split(':')[-1], p.get('properties', {})   # 26.x：性質があれば {'id', 'properties'}、無ければ文字だけ
    if isinstance(p, dict): return p['Name'].split(':')[-1], p.get('Properties', {})
    n, _, rest = p.partition('[')
    props = dict(kv.split('=', 1) for kv in rest.rstrip(']').split(',') if '=' in kv)
    return n.split(':')[-1], props


def blocks(world, keep=lambda name: True):
    """空気以外のブロックを ((x, y, z), (名前, 性質)) で出す。名前は 'minecraft:' を外したもの。"""
    for c in chunks(world):
        cx, cz = c.get('xPos'), c.get('zPos')
        if cx is None: continue
        for sec in c.get('sections', []):
            bs = sec.get('block_states')
            if not bs: continue
            pal = [_entry(p) for p in bs['palette']]
            if len(pal) == 1:
                if pal[0][0] in AIR: continue
                idx = np.zeros(4096, int)
            else:
                bits = max(4, math.ceil(math.log2(len(pal))))
                per = 64 // bits
                d = bs['data'].astype('>u8').astype(np.uint64)
                idx = np.zeros(len(d) * per, np.int64)
                mask = np.uint64((1 << bits) - 1)
                for k in range(per): idx[k::per] = ((d >> np.uint64(k * bits)) & mask).astype(np.int64)
                idx = idx[:4096]
            y0 = sec['Y'] * 16
            ok = np.array([pal[i][0] not in AIR and keep(pal[i][0]) for i in range(len(pal))])
            for n in np.nonzero(ok[idx])[0]:
                yield ((cx * 16 + (n & 15), y0 + (n >> 8), cz * 16 + ((n >> 4) & 15)), pal[idx[n]])


def dense(world, box=None):
    """ワールドを密な配列に：(配列[y, z, x] のパレット番号（0 は空気）, パレット [(名前, 性質)], 原点 (x0, y0, z0))。
    box=(x0, z0, x1, z1) で切り出す。手本の地図はブロックが数千万あるので、dict ではなくこちらで扱う。"""
    pal, idx_of = [('air', {})], {('air', '()'): 0}
    pts = []
    for (p, (n, pr)) in blocks(world):
        if box and not (box[0] <= p[0] <= box[2] and box[1] <= p[2] <= box[3]): continue
        k = (n, repr(sorted(pr.items())))
        if k not in idx_of: idx_of[k] = len(pal); pal.append((n, pr))
        pts.append((p[0], p[1], p[2], idx_of[k]))
    a = np.array(pts, np.int32)
    o = a[:, :3].min(0); s = a[:, :3].max(0) - o + 1
    arr = np.zeros((s[1], s[2], s[0]), np.uint16)
    arr[a[:, 1] - o[1], a[:, 2] - o[2], a[:, 0] - o[0]] = a[:, 3]
    return arr, pal, tuple(int(v) for v in o)
