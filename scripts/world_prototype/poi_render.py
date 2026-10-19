import pickle, iso, sys
b, hi, L = pickle.load(open('field500.pkl', 'rb'))
F, S = pickle.load(open('field500-fill.pkl', 'rb'))
for i, (x, z, name) in enumerate(F):
    r = 14
    sub = iso.World(); sub.b = {k: v for k, v in b.items() if abs(k[0] - x) <= r and abs(k[2] - z) <= r and k[1] >= hi[z, x] - 6}
    iso.render(sub, W=22).save(f'poi{i}.png'); print(i, name, flush=True)
