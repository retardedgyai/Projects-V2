"""桜牙獣 glow flow (scripts/shaders/item.fsh) reproduced on the CPU: idle loop GIF with the light running along the
petals and seams. NOT Minecraft; the shader's maths is copied here.

    python scripts/boss_preview/sakura_glow.py     # build/boss-previews/sakura_fang_glow.gif
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import rbb  # noqa: E402
import sakura_clips  # noqa: E402

DUSK = ((60, 58, 78), (24, 22, 32))


def shader(t):
    def emissive(tex):
        c = np.rint(tex).astype(int)
        mask = (c[..., 3] >= 200) & (c[..., 3] <= 215) & (c[..., 0] & 3 == 1) & (c[..., 1] & 3 == 2) & (c[..., 2] & 3 == 3)
        phase = (c[..., 3] - 200) / 15.0
        d1 = np.abs(np.mod(t * .45 - phase + .5, 1.0) - .5)
        d2 = np.abs(np.mod(t * .45 - phase, 1.0) - .5)
        band = np.exp(-d1 * d1 * 140.0) + .45 * np.exp(-d2 * d2 * 260.0)
        rgb = tex[..., :3] / 255.0 * (.9 + .1 * np.sin(t * 2.1)) * 1.08
        k = np.clip(band * .8, 0, .85)[..., None]
        rgb = rgb * (1 - k) + np.array([1.0, .90, .95]) * k
        return mask, np.minimum(rgb, 1.0) * 255.0
    return emissive


def main():
    sc = rbb.Scene(sakura_clips.PATH)
    anims = {a["name"]: a for a in json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))["animations"]}
    frames = []
    for name in ("idle", "idle_enraged"):
        a = anims[name]
        for i in range(int(a["length"] / .05) * 2):
            t = i * .05
            pose = sakura_clips.sample(a, t % a["length"])
            img = Image.new("RGB", (1000, 460))
            img.paste(rbb.render(sc, pose, az=55, el=14, size=(500, 460), scale=3.0, cy=.86, center=(0, 0, -6), bg=DUSK, emissive=shader(t)), (0, 0))
            img.paste(rbb.render(sc, pose, az=95, el=6, size=(500, 460), scale=3.0, cy=.86, center=(0, 0, -6), bg=DUSK, emissive=shader(t)), (500, 0))
            rbb.text(img, (10, 8), "ふだん" if name == "idle" else "怒り（花びらが開く）", font=rbb.SMALL)
            frames.append(img)
    out = sakura_clips.OUT / "sakura_fang_glow.gif"
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=50, loop=0)
    print(out, len(frames))


if __name__ == "__main__":
    main()
