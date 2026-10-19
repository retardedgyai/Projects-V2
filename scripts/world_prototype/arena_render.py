import pickle, iso, sys
b, hi, L = pickle.load(open('field500.pkl','rb'))
FLIP = {"stair_n": "stair_s", "stair_s": "stair_n", "stair_e": "stair_w", "stair_w": "stair_e",
        "stair_n_t": "stair_s_t", "stair_s_t": "stair_n_t", "stair_e_t": "stair_w_t", "stair_w_t": "stair_e_t"}
x0, z0, x1, z1 = 315, 340, 430, 460
for flip, name in ((True, 'f5-arena-nw.png'), (False, 'f5-arena.png')):
    sub = iso.World()
    for (x, y, z), v in b.items():
        if x0 <= x <= x1 and z0 <= z <= z1 and y >= 95:
            sub.b[(1000 - x, y, 1000 - z) if flip else (x, y, z)] = (FLIP.get(v[0], v[0]) if flip else v[0], v[1], v[2])
    iso.render(sub, W=12).save(name)
    print(name)
