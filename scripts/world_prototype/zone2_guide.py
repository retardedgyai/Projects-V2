import iso, zone2
from PIL import Image, ImageDraw, ImageFont
W = 5; VY = W * 0.61; pad = 40
img = Image.open("zone2.png").convert("RGBA")
xs = [(x - z) * W / 2 for (x, y, z) in zone2.w.b] ; ys = [(x + z) * W / 4 - y * VY for (x, y, z) in zone2.w.b]
xs += [(x + 1 - z) * W / 2 for (x, y, z) in zone2.w.b]; ys += [(x + z) * W / 4 - (y + 1) * VY for (x, y, z) in zone2.w.b]
ox, oy = -min(xs) + pad, -min(ys) + pad
P = lambda x, y, z: ((x - z) * W / 2 + ox, (x + z) * W / 4 - y * VY + oy)
d = ImageDraw.Draw(img, "RGBA")
f = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 20)
for (name, lv, sx, sz, rad, cnt, col) in zone2.SPAWNS:
    pts = []
    for k in range(48):
        a = k / 48 * 6.2832
        x = min(399, max(0, round(sx + rad * __import__("math").cos(a)))); z = min(399, max(0, round(sz + rad * __import__("math").sin(a))))
        pts.append(P(x, zone2.at(x, z) + 1, z))
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).polygon(pts, fill=(255, 60, 60, 55))
    img.alpha_composite(ov)
    d = ImageDraw.Draw(img, "RGBA")
    d.line(pts + [pts[0]], fill=(255, 110, 110, 230), width=3)
    off = {"カエル": (35, 20), "オオカミ": (-25, -25)}.get(name, (0, 0))
    px, py = P(sx + off[0], zone2.at(sx, sz) + 2, sz + off[1])
    t = f"{name} {lv}"
    tw = d.textlength(t, font=f)
    d.rounded_rectangle((px - tw / 2 - 8, py - 15, px + tw / 2 + 8, py + 15), 8, fill=(120, 20, 20, 220))
    d.text((px, py), t, fill=(255, 235, 235, 255), font=f, anchor="mm")
L = [((172, 196), "休憩所（転移石・たき火・道しるべ）", (255, 255, 255)),
     ((359, 55), "立石（目印）", (200, 200, 200)), ((300, 234), "大樹と古い森", (140, 230, 140)), ((165, 126), "古代の水道橋", (230, 220, 190)), ((248, 42), "巨獣の骨", (245, 240, 220)), ((193, 283), "岩のアーチ", (200, 200, 200)), ((248, 352), "巨人の剣", (255, 215, 80)),
     ((280, 290), "花の原（植物採取）", (255, 170, 220)), ((120, 300), "池・釣り場", (120, 190, 255)),
     ((33, 29), "見張り塔の廃墟（展望）", (200, 200, 200)), ((85, 75), "石切り場（採掘）", (190, 190, 200)),
     ((95, 215), "伐採キャンプ（伐採）", (170, 220, 120)),
     ((62, 140), "盗賊のキャンプ", (255, 100, 90)), ((345, 345), "狼の巣", (255, 120, 80)),
     ((196, 345), "古墳（ダンジョン入口）", (190, 150, 255)),
     ((150, 25), "→ 2' 風穴の崖", (255, 170, 80)), ((385, 150), "→ 3 花筏の淵", (255, 160, 200)), ((385, 240), "→ 1 開拓港（町）", (120, 200, 255))]
for (x, z), t, col in L:
    px, py = P(x, zone2.at(x, z) + 14, z)
    tw = d.textlength(t, font=f)
    d.rounded_rectangle((px - tw / 2 - 10, py - 18, px + tw / 2 + 10, py + 18), 9, fill=(16, 18, 28, 215), outline=col + (255,), width=2)
    d.text((px, py), t, fill=col + (255,), font=f, anchor="mm")
img.save("zone2-guide.png")
