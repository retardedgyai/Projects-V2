"""ハーフブロック（下半分）や階段の低い側の上に、床から立つ物（柵・灯り・花・扉・樽…）が載って、
半ブロック浮いて見える所を探す。house.js（公開ページのブロックのデータ）を直接読む。

    python3 float_on_half.py <house.js> [--fix]   # --fix：下のハーフを同じ素材の満ブロックにする
"""
import json, sys

# 床から立つ形（下の面がブロックの底にある物）
STANDS = {'fence', 'lantern', 'cross', 'door', 'pot', 'anvil', 'grindstone', 'pane', 'chain'}

def load(path):
    s = open(path, encoding='utf-8').read()
    head, data = s.split('=', 1)
    return head, json.loads(data.rstrip().rstrip(';'))

def low_top(b):
    """上の面が半分の高さで終わる所がある形か。"""
    if b['s'] == 'slab' and b.get('h') != 'top': return True
    if b['s'] == 'stair' and b.get('h') != 'top': return True      # 階段の低い側
    return False

def find(H):
    pal = H['pal']; W = {(x, y, z): pal[p] for x, y, z, p in H['blocks']}
    out = []
    for (x, y, z), b in W.items():
        if b['s'] not in STANDS: continue
        if b['s'] == 'lantern' or b['s'] == 'chain':
            up = W.get((x, y + 1, z))
            if up and up['s'] not in ('cross',): continue        # 上から吊っている
        dn = W.get((x, y - 1, z))
        if dn and low_top(dn): out.append(((x, y, z), b['t'], dn['s'], dn['t']))
    return out, W

if __name__ == '__main__':
    path = sys.argv[1]; head, H = load(path)
    out, W = find(H)
    for o in out: print(o)
    print('浮いた物', len(out))
    if '--fix' in sys.argv and out:
        pal = H['pal']; key = {json.dumps(b, sort_keys=True): i for i, b in enumerate(pal)}
        def idx(b):
            k = json.dumps(b, sort_keys=True)
            if k not in key: key[k] = len(pal); pal.append(b)
            return key[k]
        fixed = {}
        for (x, y, z), t, s, mat in out:
            fixed[(x, y - 1, z)] = {'s': 'full', 't': mat, 'top': mat, 'bot': mat}
        H['blocks'] = [[x, y, z, idx(fixed[(x, y, z)]) if (x, y, z) in fixed else p] for x, y, z, p in H['blocks']]
        open(path, 'w', encoding='utf-8').write(head + '=' + json.dumps(H, separators=(',', ':')) + ';')
        print('直した', len(fixed))
