"""Ability key-frame sheet + GIF for 桜牙獣, sampled FROM the bbmodel keyframes (what the server plays).
CPU preview, NOT Minecraft.

    python scripts/boss_preview/sakura_clips.py            # build/boss-previews/sakura_fang_{abilities.png,abilities.gif}
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import rbb  # noqa: E402
import build_boss_sakura_fang as fang  # noqa: E402

BG = ((118, 122, 140), (64, 64, 78))
PATH = fang.ROOT / "assets/bosses/sakura_fang.bbmodel"
OUT = fang.ROOT / "build/boss-previews"
JP = {"sakura-claw": "桜爪の叩きつけ", "sakura-tail": "花尾の振り下ろし", "sakura-scatter": "散華", "sakura-bloom": "花嵐（怒り後）",
      "enrage": "怒り", "idle": "待機", "walk": "歩き", "death": "死亡"}


def sample(anim, t):
    pose = {}
    for an in anim["animators"].values():
        ch = {}
        for k in an["keyframes"]:
            p = k["data_points"][0]
            ch.setdefault(k["channel"], []).append((k["time"], np.array([p["x"], p["y"], p["z"]], float)))
        o = {}
        for c, keys in ch.items():
            keys.sort(key=lambda k: k[0])
            v = keys[-1][1]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    v = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 1)
                    break
            o[c] = v
        r = o.get("rotation", np.zeros(3))
        pose[an["name"]] = (np.array([-r[0], r[1], r[2]]), o.get("position", np.zeros(3)), o.get("scale", np.ones(3)))
    return pose


def views(sc, anim, t, w=300, h=230):
    pose = sample(anim, t)
    img = Image.new("RGB", (2 * w, h))
    img.paste(rbb.render(sc, pose, az=90, el=4, size=(w, h), scale=1.55, center=(0, 40, -4), cy=.62, bg=BG), (0, 0))
    img.paste(rbb.render(sc, pose, az=40, el=14, size=(w, h), scale=1.6, center=(0, 40, -4), cy=.62, bg=BG), (w, 0))
    return img


def main():
    sc = rbb.Scene(PATH)
    anims = {a["name"]: a for a in json.loads(PATH.read_text(encoding="utf-8"))["animations"]}
    rows = []
    for name, (hit, rec) in fang.ABILITIES.items():
        clip = name if name in anims and name == "sakura-bloom" else name
        a = anims[clip]
        row = Image.new("RGB", (4 * 600, 260), (20, 18, 24))
        for k, (label, t) in enumerate((("構え", 0.0), ("溜め", hit * .85), ("決め", hit + .05), ("余韻", hit + rec * .45))):
            row.paste(views(sc, a, t), (k * 600, 30))
            rbb.text(row, (k * 600 + 10, 4), f"{JP[name]}  {label} {t:.2f}s")
        rows.append(row)
    sheet = Image.new("RGB", (4 * 600, 260 * len(rows)))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, i * 260))
    OUT.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT / "sakura_fang_abilities.png")
    frames = []
    for name in ("idle", "walk", "sakura-claw", "sakura-tail", "sakura-scatter", "enrage", "sakura-bloom", "death"):
        a = anims[name]
        L = a["length"]
        for i in range(int(L / .1) + 1):
            f = views(sc, a, min(L, i * .1))
            rbb.text(f, (8, 6), JP[name], font=rbb.SMALL)
            frames.append(f)
    frames[0].save(OUT / "sakura_fang_abilities.gif", save_all=True, append_images=frames[1:], duration=100, loop=0)
    print(OUT / "sakura_fang_abilities.png", len(frames), "gif frames")


if __name__ == "__main__":
    main()
