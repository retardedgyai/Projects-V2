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

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'out', 'frontier')
HM = np.load(os.path.join(D, 'height.npy')); GR = np.load(os.path.join(D, 'ground.npy'))
RG = np.load(os.path.join(D, 'region.npy')); WA = np.load(os.path.join(D, 'water.npy'))
INFO = json.load(open(os.path.join(D, 'pois.json')))
REG = INFO['regions']; SEA = INFO['sea']
G = {'water': 0, 'sand': 1, 'grass': 2, 'sakura': 3, 'marsh': 4, 'forest': 5, 'meadow': 6, 'highland': 7, 'cliff': 8,
     'snow': 9, 'rock': 10, 'basalt': 11, 'road': 12, 'gravel': 13, 'ash': 14}
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
        if code == G['sand']: return full('sand') if m < 0.8 else full('gravel')
        if code == G['gravel']: return full('gravel') if m < 0.5 else full('coarse_dirt') if m < 0.75 else full('stone')
        if code == G['basalt']: return full('basalt_side', top='basalt_top') if m < 0.6 else full('blackstone')
        if code == G['ash']: return full('coarse_dirt') if m < 0.4 else full('tuff') if m < 0.6 else full('gravel') if m < 0.7 else grass()
        if code == G['marsh']: return full('mud') if m < 0.22 else full('podzol_side', top='podzol_top', bottom='dirt') if m < 0.35 else grass()
        if code == G['cliff']: return full('coarse_dirt') if m < 0.3 else full('red_sand') if m < 0.38 else grass()
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
        return a >= 35 or st in ('volcano', 'harbor', 'cliffs') or GR[z, x] in (G['sand'], G['gravel'], G['rock'], G['basalt'])
    def clear(x, z):
        st = REG[int(RG[z, x])]['style']
        return (st in ('meadow', 'highland') and vnoise(x, z, 40, 21) < 0.62) or (st == 'marsh' and vnoise(x, z, 30, 22) < 0.5)
    print('木・茂み', T.woods(skip=skip, kind=kind, clear=clear))
    print('倒木', T.fallen_logs(logs))
    T.ground_cover()

    return T


if __name__ == '__main__':
    key = sys.argv[1] if len(sys.argv) > 1 else 'harbor'
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 240
    r = next(q for q in REG if q['key'] == key)
    cx, cz = r['x'], r['z']
    X0, Z0 = max(2, cx - size // 2), max(2, cz - size // 2)
    X1, Z1 = min(997, X0 + size), min(997, Z0 + size)
    T = build(X0, Z0, X1, Z1); H = T.H
    V = lambda x, y, z: [-x, y, z]
    mid = (X0 + X1) // 2, (Z0 + Z1) // 2
    gm = H[(cx, cz)]
    print(save('frontier_' + key, {
        'over': [V(mid[0], gm + 110, Z1 + 60), V(mid[0], gm, mid[1] - 20)],
        'top': [V(mid[0], gm + 260, mid[1] + 1), V(mid[0], gm, mid[1])],
        'eye': [V(cx, H[(cx, min(Z1 - 5, cz + 40))] + 3, min(Z1 - 5, cz + 40)), V(cx, gm + 2, cz)],
        'north': [V(cx, gm + 40, cz + 30), V(cx, gm + 30, Z0)],
    }, V(mid[0], gm, mid[1]), shadowR=180, fog=(260, 700)))
