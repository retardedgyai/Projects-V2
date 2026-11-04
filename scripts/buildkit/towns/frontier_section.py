#!/usr/bin/env python3
"""開拓大陸（towns/frontier_map.py の地図）の一部を、ブロックで組んで 3D で見る。
使い方：python3 towns/frontier_section.py <区域の key> [大きさ]   例：harbor 240
地図の高さ・地面の種類・水を読み、山の岩は傾きと光の向きで塗り（rules 72・73）、木は区域の型で並べる（rules 28〜36・46）。"""
import json, math, os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
from vox import put, get, full, grass, vnoise, _h
import terrain as TE
import poi as PO
import random

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', 'frontier')
HM = np.load(os.path.join(D, 'height.npy')); GR = np.load(os.path.join(D, 'ground.npy'))
RG = np.load(os.path.join(D, 'region.npy')); WA = np.load(os.path.join(D, 'water.npy'))
INFO = json.load(open(os.path.join(D, 'pois.json')))
REG = INFO['regions']; SEA = INFO['sea']
G = {'water': 0, 'sand': 1, 'grass': 2, 'sakura': 3, 'marsh': 4, 'forest': 5, 'meadow': 6, 'highland': 7, 'cliff': 8,
     'snow': 9, 'rock': 10, 'basalt': 11, 'road': 12, 'gravel': 13, 'ash': 14, 'trail': 15, 'ice': 16, 'lava': 17}
LIGHT = (-0.45, 0.75, -0.48)


def hh(x, z): return float(HM[z, x])
def slope(x, z):
    gx = (HM[z, min(999, x + 2)] - HM[z, max(0, x - 2)]) / 4.0; gz = (HM[min(999, z + 2), x] - HM[max(0, z - 2), x]) / 4.0
    return math.degrees(math.atan(math.hypot(gx, gz))), gx, gz


class Section(Town):
    def ground_top(self, x, z, k, g, sand=lambda x, z, g: False):
        if k != 'nat': return super().ground_top(x, z, k, g, sand)
        code = int(GR[z, x]); a, gx, gz = slope(x, z)
        m = vnoise(x, z, 5, 77) * 0.6 + vnoise(x, z, 2, 78) * 0.4
        if a >= 45 or code == G['rock']:                                                 # 岩：光の向きの明るさで
            ln = math.sqrt(gx * gx + 1 + gz * gz)
            br = (-gx * LIGHT[0] + LIGHT[1] - gz * LIGHT[2]) / ln - 0.15 * max(0.0, (150 - g) / 150)
            if code == G['basalt'] or code == G['ash']: return full('basalt_side', top='basalt_top') if m < 0.5 else full('blackstone')
            if code == G['cliff']: return full('terracotta') if m < 0.4 else full('granite') if m < 0.7 else full('sandstone', top='sandstone_top')
            if br > 0.72: return full('calcite') if m < 0.18 else full('stone')
            if br > 0.22: return full('andesite') if m < 0.3 else full('stone')
            return full('tuff') if m < 0.35 else full('cobbled_deepslate') if m < 0.5 else full('andesite')
        if code == G['snow']: return full('snow')
        if code == G['ice']: return full('packed_ice') if m < 0.7 else full('ice')
        if code == G['trail']: return full('coarse_dirt') if m < 0.35 else full('dirt_path_side', top='dirt_path_top', bottom='dirt') if False else ({'s': 'path', 't': 'dirt_path_side', 'top': 'dirt_path_top', 'bot': 'dirt'} if m < 0.75 else full('gravel'))
        if code == G['sand']: return full('sand') if m < 0.8 else full('gravel')
        if code == G['gravel']: return full('gravel') if m < 0.5 else full('coarse_dirt') if m < 0.75 else full('stone')
        if code == G['basalt']: return full('basalt_side', top='basalt_top') if m < 0.6 else full('blackstone')
        if code == G['ash']: return full('coarse_dirt') if m < 0.4 else full('tuff') if m < 0.6 else full('gravel') if m < 0.7 else grass()
        if code == G['marsh']: return full('mud') if m < 0.22 else full('podzol_side', top='podzol_top', bottom='dirt') if m < 0.35 else grass()
        if code == G['cliff']: return full('coarse_dirt') if m < 0.22 else grass()                 # 橙の砂の斑点はやめた（汚く見えた）
        return grass()

    def steep_top(self, x, z): return self.ground_top(x, z, 'nat', self.H[(x, z)])


def build(X0, Z0, X1, Z1, logs=20):
    """地図の (X0..X1, Z0..Z1) を組んで Town を返す。"""
    T = Section((X0, X1, Z0, Z1), lambda x, z: hh(x, z))
    H, KIND = T.H, T.KIND
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            H[(x, z)] = int(round(hh(x, z)))
            if WA[z, x]:
                KIND[(x, z)] = 'stream'
                T.stream_level[(x, z)] = SEA if HM[z, x] < SEA - 1 and z > 850 else int(round(hh(x, z))) + 1
                H[(x, z)] = T.stream_level[(x, z)] - 2 if H[(x, z)] >= T.stream_level[(x, z)] - 1 else H[(x, z)]
            elif GR[z, x] == G['road']: KIND[(x, z)] = 'lane'
            elif GR[z, x] == G['trail']: KIND[(x, z)] = 'nat'; T.TRAILC = getattr(T, 'TRAILC', set()); T.TRAILC.add((x, z))
            else: KIND[(x, z)] = 'nat'
    T.fill()

    STYLE = {q['key']: q['style'] for q in REG}
    def kind(x, z, rr):
        st = REG[int(RG[z, x])]['style']; g = H[(x, z)]
        if GR[z, x] == G['snow'] or st == 'snow': return 'spruce'
        if st == 'highland': return 'spruce' if rr < 0.6 else 'birch'
        if st == 'sakura': return 'cherry' if rr < 0.45 else 'oak' if rr < 0.85 else 'birch'
        if st == 'marsh': return 'birch' if rr < 0.5 else 'oak'
        if st == 'meadow': return 'oak' if rr < 0.7 else 'birch'
        return 'big' if rr < 0.015 else 'oak' if rr < 0.8 else 'birch'
    def skip(x, z):
        st = REG[int(RG[z, x])]['style']; a = slope(x, z)[0]
        if st == 'harbor' and math.hypot(x - REG[0]['x'], z - REG[0]['z']) < 60: return True     # 町の敷地は空ける（外は林と並木）
        return a >= 35 or st in ('volcano', 'cliffs') or GR[z, x] in (G['sand'], G['gravel'], G['rock'], G['basalt'])
    def clear(x, z):
        st = REG[int(RG[z, x])]['style']
        if st == 'harbor': return vnoise(x, z, 45, 23) < 0.68                                   # 港の外は林が点々（草原の7割は空ける）
        return (st in ('meadow', 'highland') and vnoise(x, z, 40, 21) < 0.62) or (st == 'marsh' and vnoise(x, z, 30, 22) < 0.5)
    T.TRAIL = getattr(T, 'TRAILC', set())                                                    # 小道は木を置かない（woods は TRAIL の両脇2マスを空ける）
    POI = [p for p in INFO['pois'] if X0 + 6 <= p['x'] <= X1 - 6 and Z0 + 6 <= p['z'] <= Z1 - 6]
    FEAT = [f for f in INFO.get('features', []) if X0 + 6 <= f['x'] <= X1 - 6 and Z0 + 6 <= f['z'] <= Z1 - 6]
    near = set()
    for p in POI + FEAT:
        rr = {'landmark': 14, 'boss': 13, 'dungeon': 9, 'place': 8, 'cave': 7, 'waypoint': 4, 'secret': 3, 'small': 3, 'town': 0}.get(p['kind'], 0)
        rr = max(rr, int(p.get('r', 0)) if p['kind'] in ('pool', 'sinkhole', 'ice', 'forthill', 'dome') else rr)
        rr = rr + 7 if rr else 0                                                             # 木の冠（半径5〜6）がかからないように
        for i in range(-rr, rr + 1):
            for j in range(-rr, rr + 1):
                if i * i + j * j <= rr * rr: near.add((p['x'] + i, p['z'] + j))
    skip0 = skip
    skip = lambda x, z: (x, z) in near or skip0(x, z)
    print('木・茂み', T.woods(skip=skip, kind=kind, clear=clear))
    print('倒木', T.fallen_logs(logs))
    T.ground_cover()
    place_pois(T, POI, FEAT)
    return T


def place_pois(T, POI, FEAT):
    """地図の点と形の印を、kit/poi.py の部品で建てる。"""
    H = T.H
    def get_h(x, z): return H.get((x, z))
    def face_down(x, z):                                                                       # 斜面の下り向き（洞窟の口が向く方）
        best, f = 1e9, 's'
        for k, (dx, dz) in (('n', (0, -1)), ('s', (0, 1)), ('e', (1, 0)), ('w', (-1, 0))):
            v = H.get((x + dx * 4, z + dz * 4), 1e9)
            if v < best: best, f = v, k
        return f
    def face_road(x, z):
        return 's'
    for f in FEAT:
        x, z = f['x'], f['z']; g = H.get((x, z))
        if g is None: continue
        rng = random.Random(x * 7919 + z)
        k = f['kind']
        if k == 'sunken_tower': PO.sunken_tower(x, z, g, rng, get_h, int(round(SEA + 10)) if False else T.stream_level.get((x, z), g) )
        elif k == 'pinnacle':
            sx, sz = x, z + int(f.get('r', 30)) + 6
            if (sx, sz) in H: PO.torii_shrine(sx, sz, H[(sx, sz)], rng, get_h, 's')
        elif k == 'dome': PO.stone_ring(x, z, g, rng, get_h)
        elif k == 'arch': PO.rock_arch(x, z, g, rng, get_h)
        elif k == 'forthill': PO.snow_fort(x, z, g, rng, get_h)
        elif k == 'waterfall':
            lo = min((H.get((x + i, z + j), 999) for i in range(-3, 4) for j in range(4, 12)), default=g)
            PO.waterfall(x, z, g, lo)
        elif k == 'lava':
            cells = [(xx, zz, H[(xx, zz)]) for (xx, zz) in list(H) if GR[zz, xx] == G['lava']]
            PO.lava_lake(cells)
    for p in POI:
        x, z = p['x'], p['z']; g = H.get((x, z))
        if g is None: continue
        rng = random.Random(x * 31 + z * 17)
        st = REG[int(RG[z, x])]['style']; k = p['kind']
        if k == 'place': PO.ruin(x, z, g, rng, get_h, st)
        elif k == 'cave': PO.cave_mouth(x, z, g, rng, get_h, face_down(x, z))
        elif k == 'dungeon':
            PO.dungeon_gate(x, z, g, rng, get_h, face_down(x, z), st)
            ex = p.get('exit')
            if ex and tuple(ex) in H: PO.cave_mouth(ex[0], ex[1], H[tuple(ex)], rng, get_h, face_down(*ex))
        elif k == 'waypoint': PO.waypoint(x, z, g, rng, get_h)
        elif k == 'secret': PO.secret(x, z, g, rng, get_h, st)
        elif k == 'small': PO.small_find(x, z, g, rng, get_h, st)
        elif k == 'boss': PO.boss_ring(x, z, g, rng, get_h, st)


if __name__ == '__main__':
    key = sys.argv[1] if len(sys.argv) > 1 else 'harbor'
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 240
    r = next(q for q in REG if q['key'] == key)
    cx, cz = r['x'], r['z']
    X0, Z0 = max(2, cx - size // 2), max(2, cz - size // 2)
    X1, Z1 = min(997, X0 + size), min(997, Z0 + size)
    T = build(X0, Z0, X1, Z1); H = T.H
    road = [(x, z) for (x, z), k in T.KIND.items() if k == 'lane' and abs(x - cx) + abs(z - cz) > 25]
    ex, ez = min(road, key=lambda c: abs(math.hypot(c[0] - cx, c[1] - cz) - 45)) if road else (cx, min(Z1 - 5, cz + 40))   # 目の高さは街道の上から
    V = lambda x, y, z: [-x, y, z]
    mid = (X0 + X1) // 2, (Z0 + Z1) // 2
    gm = H[(cx, cz)]
    extra = {}
    picks = [f for f in INFO.get('features', []) if X0 + 20 <= f['x'] <= X1 - 20 and Z0 + 30 <= f['z'] <= Z1 - 30 and f['kind'] not in ('hill', 'vantage', 'seastack', 'lava')]
    picks += [q for q in INFO['pois'] if X0 + 20 <= q['x'] <= X1 - 20 and Z0 + 30 <= q['z'] <= Z1 - 30 and q['kind'] in ('place', 'cave', 'dungeon', 'boss', 'waypoint', 'secret')]
    seen_k = set()
    for q in picks:                                                                         # 見どころごとの視点：南へ 16〜30 マス、少し上から
        kk = q['kind']
        if kk in seen_k and kk not in ('place',): continue
        seen_k.add(kk)
        d = 30 if kk in ('pinnacle', 'forthill', 'dome', 'sunken_tower', 'arch', 'pool', 'waterfall', 'sinkhole', 'ice', 'spine', 'boss') else 16
        cxq, czq = q['x'], min(Z1 - 2, q['z'] + d)
        gq = H.get((q['x'], q['z']), gm); gc = H.get((cxq, czq), gq)
        extra['%s_%d' % (kk, len(extra))] = [V(cxq, max(gc, gq) + (10 if d == 30 else 5), czq), V(q['x'], gq + 3, q['z'])]
    print('視点', ' '.join(extra))
    print(save('frontier_' + key, {**extra,
        'over': [V(mid[0], gm + 110, Z1 + 60), V(mid[0], gm, mid[1] - 20)],
        'top': [V(mid[0], gm + 260, mid[1] + 1), V(mid[0], gm, mid[1])],
        'eye': [V(ex, H[(ex, ez)] + 3, ez), V(cx, gm + 4, cz)],
        'north': [V(cx, gm + 40, cz + 30), V(cx, gm + 30, Z0)],
    }, V(mid[0], gm, mid[1]), shadowR=180, fog=(260, 700)))
