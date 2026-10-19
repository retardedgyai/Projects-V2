"""python3 shot.py name x0 z0 x1 z1 [ymax] [W] : close-up, optionally cut away above ymax"""
import pickle, iso, sys
b, hi, L = pickle.load(open('field500.pkl', 'rb'))
a = sys.argv[1:]
nm, x0, z0, x1, z1 = a[0], *map(int, a[1:5])
ym = int(a[5]) if len(a) > 5 and a[5] != '-' else 999
W = int(a[6]) if len(a) > 6 else 16
w = iso.World(); w.b = {k: v for k, v in b.items() if x0 <= k[0] <= x1 and z0 <= k[2] <= z1 and k[1] <= ym}
iso.render(w, W=W).save(f's-{nm}.png'); print(nm)
