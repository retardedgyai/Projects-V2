import sys, iso, zone2
SHOTS = {"rest": (150, 165, 215, 225, 16), "quarry": (55, 50, 115, 100, 14), "logging": (75, 196, 118, 236, 18), "wolf": (325, 325, 365, 365, 18), "meadow": (225, 245, 335, 335, 10), "village": (140, 105, 320, 255, 12), "downs": (225, 20, 390, 155, 10), "pond": (40, 240, 190, 370, 10),
         "camp": (35, 130, 95, 185, 16), "tower": (10, 8, 58, 52, 18), "barrow": (170, 325, 225, 365, 18)}
for name in sys.argv[1:]:
    x0, z0, x1, z1, W = SHOTS[name]
    sub = iso.World()
    sub.b = {k: v for k, v in zone2.w.b.items() if x0 <= k[0] <= x1 and z0 <= k[2] <= z1}
    iso.render(sub, W=W).save(f"zone2-{name}.png")
    print(name)
