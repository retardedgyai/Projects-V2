import harbor2 as h, iso
from PIL import ImageDraw, ImageFont
W = 24; VY = W * 0.61; pad = 40
img = iso.render(h.w, W=W)
xs = []; ys = []
for (x, y, z) in h.w.b:
    for a in (0, 1):
        for b in (0, 1):
            for c in (0, 1):
                xs.append((x + a - z - c) * W / 2); ys.append((x + a + z + c) * W / 4 - (y + b) * VY)
ox, oy = -min(xs) + pad, -min(ys) + pad
P = lambda x, y, z: ((x - z) * W / 2 + ox, (x + z) * W / 4 - y * VY + oy)
G = h.G + 1.2
d = ImageDraw.Draw(img, "RGBA")
routes = [((90, 200, 255), [(50, 41), (50, 38)]),                                   # pier -> junction
          ((120, 230, 90), [(50, 38), (53.5, 38), (53.5, 1)]),                       # junction -> gate
          ((255, 90, 80), [(50, 38), (7, 38.5), (7, 54)]),                           # junction -> lighthouse
          ((255, 210, 60), [(50, 37), (40, 30)]),                                    # market
          ((255, 210, 60), [(24, 37), (24, 31)]),
          ((220, 220, 220), [(53.5, 11), (45, 11)]),                                 # to the well
          ((220, 220, 220), [(56, 39), (66, 37)])]                                   # to the beach
for col, pts in routes:
    sp = [P(x, G, z) for x, z in pts]
    d.line(sp, fill=col + (230,), width=9, joint="curve")
f = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 26)
labels = [((50, 44), "到着：桟橋", (90, 200, 255)), ((50, 38), "道しるべ", (255, 255, 255)),
          ((53.5, 1), "大陸への門", (120, 230, 90)), ((7, 55), "灯台", (255, 90, 80)),
          ((24, 30), "港の館", (255, 210, 60)), ((40, 26), "市場", (255, 210, 60)),
          ((18, 31), "掲示板（依頼）", (200, 255, 200)),
          ((34, 35), "広場・噴水", (255, 255, 255)), ((45, 11), "井戸・裏庭", (220, 220, 220)),
          ((67, 37), "浜辺", (220, 220, 220)),
          ((80, 15), "住宅街", (255, 200, 150)), ((107, 21), "風車", (255, 160, 60))]
for (x, z), text, col in labels:
    px, py = P(x, G + 4, z)
    tw = d.textlength(text, font=f)
    d.rounded_rectangle((px - tw / 2 - 12, py - 22, px + tw / 2 + 12, py + 22), 10, fill=(20, 22, 34, 225), outline=col + (255,), width=3)
    d.text((px, py), text, fill=col + (255,), font=f, anchor="mm")
img.save("harbor2-guide.png")
