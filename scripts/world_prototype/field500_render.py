"""Render the 500 field: overview with labels, and close-ups. Usage: python3 field500_render.py [overview] [shots...]"""
import pickle
import sys
from PIL import Image, ImageDraw, ImageFont
import iso

b, hi, LABELS = pickle.load(open("field500.pkl", "rb"))
SHOTS = {"tree": (190, 100, 290, 205, 10), "boss": (352, 52, 438, 138, 10), "fungal": (40, 190, 150, 285, 10),
         "wolf": (380, 355, 450, 430, 13), "tower": (60, 45, 112, 100, 14), "cliff": (420, 120, 499, 190, 12),
         "boulders": (235, 390, 335, 465, 10), "meadow": (270, 195, 395, 305, 8), "pond": (55, 385, 165, 460, 10),
         "rest": (165, 235, 235, 295, 14), "ramp": (120, 300, 220, 380, 9), "falls": (230, 140, 300, 220, 11), "gap": (140, 300, 230, 360, 12), "sap": (150, 190, 186, 226, 18), "arch": (240, 200, 300, 270, 12), "pillars": (230, 150, 499, 470, 5), "pillar1": (240, 270, 345, 375, 9), "top": (320, 350, 425, 465, 9), "scarp": (380, 150, 430, 200, 16), "mesa": (265, 285, 330, 350, 14), "sakura": (315, 340, 425, 455, 10), "sando": (280, 300, 380, 400, 9), "gate": (0, 400, 80, 499, 9), "egate": (430, 180, 499, 290, 9)}


def render(sub, W):
    world = iso.World(); world.b = sub
    return iso.render(world, W=W)


for name in sys.argv[1:]:
    if name == "overview":
        W = 4
        img = render(b, W)
        VY = W * .61; pad = 40
        xs = [(x - z) * W / 2 for (x, y, z) in b] + [(x + 1 - z) * W / 2 for (x, y, z) in b]
        ys = [(x + z) * W / 4 - (y + 1) * VY for (x, y, z) in b]
        ox, oy = -min(xs) + pad, -min(ys) + pad
        img.save("field500.png")
        d = ImageDraw.Draw(img, "RGBA")
        f = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 18)
        for (x, z), t, col in LABELS:
            px, py = (x - z) * W / 2 + ox, (x + z) * W / 4 - (hi[min(499, z), min(499, x)] + 22) * VY + oy
            tw = d.textlength(t, font=f)
            d.rounded_rectangle((px - tw / 2 - 8, py - 15, px + tw / 2 + 8, py + 15), 8, fill=(16, 18, 28, 215), outline=col + (255,), width=2)
            d.text((px, py), t, fill=col + (255,), font=f, anchor="mm")
        img.save("field500-guide.png")
    else:
        x0, z0, x1, z1, W = SHOTS[name]
        render({k: v for k, v in b.items() if x0 <= k[0] <= x1 and z0 <= k[2] <= z1}, W).save(f"f5-{name}.png")
    print(name, flush=True)
