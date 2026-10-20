import pickle, iso, sys
b, hi, L = pickle.load(open('field500.pkl', 'rb'))
w = iso.World(); w.b = b
iso.render(w, W=4).save('overview.png'); print('ok')
