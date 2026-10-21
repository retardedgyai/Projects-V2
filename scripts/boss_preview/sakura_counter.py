"""桜牙獣's counter and part breaks, CPU preview (NOT Minecraft): the horns flash blue in the counter window of the paw
slam (a whirling petal wreath and blossoms on the horns), the hit throws it into `countered`; then a part break: the horn tips are gone and it flinches.

    python scripts/boss_preview/sakura_counter.py   # build/boss-previews/sakura_counter.gif + .png
"""
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFilter  # noqa: E402
import rbb  # noqa: E402
import sakura_clips  # noqa: E402
import sakura_glow  # noqa: E402
import sakura_fx  # noqa: E402
import sakura_fx_styles  # noqa: E402

BG = ((52, 50, 64), (22, 20, 28))
VIEW = dict(az=35, el=16, size=(640, 400), scale=2.4, center=(0, 34, 0), cy=.7)
TIPS = [(11.4, 27.0, -18.0), (-11.4, 27.0, -18.0)]


def head(x, y, z):
    return (x, 40.5 + y * .94 - z * .34, 27.0 + y * .34 + z * .94)


def glow_at(img, sc, pose, t, t0):
    """The counter cue (SakuraPetalFx.wreath + SakuraStrikeFx.counterBloom): a wreath of 18 petals whirls round the
    head trailing light, and a blossom opens on each horn tip."""
    cam, right, up, ox, oy = sakura_fx.camera(VIEW["az"], VIEW["el"], VIEW["size"], VIEW["scale"], VIEW["cy"], VIEW["center"])
    P = lambda p: (ox + p @ right * VIEW["scale"], oy - p @ up * VIEW["scale"])
    layer = Image.new("RGB", img.size)
    d = ImageDraw.Draw(layer)
    base = ImageDraw.Draw(img)
    centre = sc.point("skull", (0, 48, 32), pose)
    grow = min(1.0, (t - t0) / .15)
    R = 2.2 * 16 * grow
    spin = (t - t0) * 9.0
    order = []
    for k in range(18):
        a = spin + k * 2 * math.pi / 18
        order.append((k, a))
    for k, a in order:
        pts = []
        for j in range(8):                                   # the ribbon: the last 0.08 s of its arc
            aa = a - j * .09
            pts.append(P(centre + np.array([math.cos(aa) * R, .25 * 16 * math.sin(aa * 2 + spin), math.sin(aa) * R])))
        for j in range(len(pts) - 1):
            f = 1 - j / len(pts)
            d.line([pts[j], pts[j + 1]], fill=(int(255 * f), int(140 * f), int(190 * f)), width=3)
        sx, sy = pts[0]
        shape = sakura_fx_styles.petal_shape(sx, sy, 7, a * 2, abs(math.cos(a * 3)))
        base.polygon(shape, fill=(255, 226, 238))
        d.polygon(shape, fill=(255, 190, 220))
    for x in (11.4, -11.4):                                  # blossoms on the horn tips
        tip = sc.point("skull", head(x, 27.0, -18.0), pose)
        sx, sy = P(tip)
        for k in range(5):
            a = k * 2 * math.pi / 5 + t
            shape = sakura_fx_styles.petal_shape(sx + math.sin(a) * 7, sy - math.cos(a) * 7, 8, a + math.pi, 1.0)
            base.polygon(shape, fill=(255, 214, 230))
            d.polygon(shape, fill=(255, 170, 205))
        d.ellipse([sx - 3, sy - 3, sx + 3, sy + 3], fill=(255, 240, 180))
    a = np.asarray(img, float) + np.asarray(layer.filter(ImageFilter.GaussianBlur(5)), float) * 1.5 + np.asarray(layer, float) * .5
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def main():
    sc = rbb.Scene(sakura_clips.PATH)
    A = {a["name"]: a for a in json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))["animations"]}
    frames, keys = [], []
    claw = A["sakura-claw"]
    hit = 1.2
    for i in range(int((hit - .1) / .05)):                       # wind-up; the window is the last 0.6..0.15 s
        t = i * .05
        img = rbb.render(sc, sakura_clips.sample(claw, t), bg=BG, emissive=sakura_glow.shader(t), **VIEW)
        open_ = hit - .6 <= t <= hit - .15
        if open_:
            img = glow_at(img, sc, sakura_clips.sample(claw, t), t, hit - .6)
        rbb.text(img, (10, 8), "桜爪の溜め" + ("  ← 頭のまわりを花輪が回り、角に花が咲く：正面から打つとカウンター" if open_ else ""), rbb.SMALL,
                 (255, 190, 220) if open_ else (236, 228, 244))
        frames.append(img)
        if abs(t - .85) < .026:
            keys.append(img)
    cnt = A["countered"]
    for i in range(int(3.5 / .05) + 1):
        t = i * .05
        img = rbb.render(sc, sakura_clips.sample(cnt, t), bg=BG, emissive=sakura_glow.shader(t), **VIEW)
        rbb.text(img, (10, 8), "カウンター！ 崩れた（3.5秒 動けない・被ダメ+30%）", rbb.SMALL, (255, 180, 120))
        frames.append(img)
        if abs(t - 1.2) < .026:
            keys.append(img)
    fl = A["flinch"]
    for i in range(int(1.0 / .05) + 1):
        t = i * .05
        img = rbb.render(sc, sakura_clips.sample(fl, t), hidden=("horn_l_tip", "horn_r_tip"), bg=BG, emissive=sakura_glow.shader(t), **VIEW)
        rbb.text(img, (10, 8), "部位破壊：枝角が折れた（突き上げを使わなくなる）", rbb.SMALL, (255, 200, 220))
        frames.append(img)
        if abs(t - .15) < .026:
            keys.append(img)
    out = sakura_clips.OUT
    frames = [f.convert("P", palette=Image.ADAPTIVE, colors=200) for f in frames]
    frames[0].save(out / "sakura_counter.gif", save_all=True, append_images=frames[1:], duration=50, loop=0, optimize=True)
    sheet = Image.new("RGB", (640 * len(keys), 400))
    for i, k in enumerate(keys):
        sheet.paste(k, (i * 640, 0))
    sheet.save(out / "sakura_counter.png")
    print(out / "sakura_counter.gif", len(frames))


if __name__ == "__main__":
    main()
