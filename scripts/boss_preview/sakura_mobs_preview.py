"""Stills and clips of the sakura common enemies, sampled FROM the bbmodel keyframes (what the server plays).
CPU preview, NOT Minecraft.

    python scripts/boss_preview/sakura_mobs_preview.py DIR [OUT]   # every DIR/*.bbmodel -> OUT/<id>_still.png, <id>.gif

The still is the idle pose from three sides (3/4, front, side) beside a player's height; the GIF plays
idle, walk, the skill (telegraph then blow), hurt and death, each labelled, at the server's speed.
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402
import rbb  # noqa: E402
from sakura_clips import sample  # noqa: E402

BG = ((132, 136, 156), (70, 70, 86))
JP = {"idle": "待機", "walk": "歩き", "hurt": "被弾", "death": "倒れる"}
FPS = 20


def player_marker(img, scale, cx, cy, W, H):
    """A thin 2-block ruler at the right edge: a player's height at the same scale."""
    d = ImageDraw.Draw(img)
    x = W - 14
    y0 = H * cy
    d.line((x, y0, x, y0 - 32 * scale), fill=(240, 236, 220), width=2)
    for k in range(3):
        d.line((x - 4, y0 - 16 * k * scale, x + 4, y0 - 16 * k * scale), fill=(240, 236, 220), width=2)


def view(sc, pose, az, el, size, scale, cy=.88):
    return rbb.render(sc, pose, az=az, el=el, size=size, scale=scale, cx=.5, cy=cy, bg=BG, outline=True)


def still(sc, anims, out, scale):
    pose = sample(anims["idle"], 0)
    W, H = 360, 330
    img = Image.new("RGB", (W * 3, H))
    for k, (az, el) in enumerate(((35, 14), (0, 4), (90, 4))):
        tile = view(sc, pose, az, el, (W, H), scale)
        if k == 2:
            player_marker(tile, scale, .5, .88, W, H)
        img.paste(tile, (k * W, 0))
    img.save(out)


def clip(sc, anims, out, scale, skills):
    order = ["idle", "walk"] + skills + ["hurt", "death"]
    frames, durs = [], []
    W, H = 420, 360
    for name in order:
        a = anims[name]
        reps = 2 if name == "walk" and a["length"] < 1 else 1
        n = max(2, int(round(a["length"] * reps * FPS)))
        for i in range(n + (6 if name == "death" else 0)):
            t = min(a["length"], (i / FPS) % a["length"] if reps > 1 else i / FPS)
            img = view(sc, sample(a, t), 35, 12, (W, H), scale)
            label = JP.get(name, name)
            ImageDraw.Draw(img).text((10, 8), label, font=rbb.MID, fill=(255, 250, 240))
            frames.append(img.convert("P", palette=Image.ADAPTIVE, colors=96))
            durs.append(1000 // FPS)
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=durs, loop=0, optimize=True, disposal=2)


def main():
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else src
    dst.mkdir(parents=True, exist_ok=True)
    only = sys.argv[3:]
    for path in sorted(src.glob("*.bbmodel")):
        if only and path.stem not in only:
            continue
        sc = rbb.Scene(path)
        anims = {a["name"]: a for a in json.loads(path.read_text(encoding="utf-8"))["animations"]}
        skills = [n for n in anims if n not in ("idle", "walk", "hurt", "death")]
        scale = 6.5   # one scale for all, so their sizes compare
        still(sc, anims, dst / f"{path.stem}_still.png", scale)
        clip(sc, anims, dst / f"{path.stem}.gif", scale, skills)
        print(path.stem)


if __name__ == "__main__":
    main()
