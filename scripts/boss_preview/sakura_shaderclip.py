"""桜牙獣's strike effects (SakuraStrikeFx, shader marker 79) drawn over its clips. CPU emulation, NOT Minecraft.

    SAKURA_VFX_DUMP=/tmp/sakura_vfx.jsonl ./gradlew :server-minestom:test --tests '*SakuraFangBossTest' --rerun
    python scripts/boss_preview/sakura_shaderclip.py /tmp/sakura_vfx.jsonl build/boss-previews/sakura_strikes.gif

Uses shaderclip.py's drawing (item.fsh line for line) with the sakura palette, and sakura_glow's emissive petals.
"""
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402
import rbb  # noqa: E402
import shaderclip  # noqa: E402
import sakura_clips  # noqa: E402
import sakura_glow  # noqa: E402
import build_boss_sakura_fang as fang  # noqa: E402


def sakura_ramp(h):
    c0, c1, c2, c3 = (np.array(v) for v in ((.14, .03, .09), (.78, .20, .47), (1.0, .62, .80), (1.0, .94, .97)))
    h = h[..., None]
    c = c0 + (c1 - c0) * shaderclip.sstep(0, .35, h)
    c = c + (c2 - c) * shaderclip.sstep(.35, .7, h)
    return c + (c3 - c) * shaderclip.sstep(.7, 1.0, h)


shaderclip.ramp = sakura_ramp

ORDER = [("sakura-claw", "桜爪の叩きつけ", 1.0), ("sakura-claw2", "桜爪・二の手", 1.0), ("sakura-claw3", "桜爪・止め（怒り後）", 1.0),
         ("sakura-tail", "花尾の宙返り", .9), ("sakura-horn", "枝角の突き上げ", 1.1), ("sakura-spin", "花尾の旋回", .9),
         ("sakura-scatter", "散華", .7), ("sakura-bloom", "花嵐（怒り後）", .62), ("sakura-howl", "散り際の遠吠え（怒り後）", .7),
         ("enrage", "怒りの遠吠え", .4)]
BG = ((52, 50, 64), (22, 20, 28))


def main(dump, out):
    sc = rbb.Scene(sakura_clips.PATH)
    anims = {a["name"]: a for a in json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))["animations"]}
    rows = [json.loads(l) for l in Path(dump).read_text().splitlines()]
    textures = {p.stem: np.asarray(Image.open(p).convert("RGBA")).astype(np.float32) for p in shaderclip.TEX.glob("*.png")}
    frames, keys = [], []
    for name, label, zoom in ORDER:
        clip = name if name in fang.ENRAGED_ONLY or name == "enrage" else name
        a = anims[clip]
        by_tick = {}
        for r in rows:
            if r["ability"] == name:
                by_tick.setdefault(r["tick"], []).append(r)
        hit = fang.ABILITIES.get(name, (2.5, 0))[0]
        v = dict(az=35, el=26, size=(560, 360), scale=zoom * 1.6, center=(0, 20, 40), cy=.66)
        n = int(a["length"] / .1)
        for i in range(n + 1):
            t = min(a["length"], i * .1)
            pose = sakura_clips.sample(a, t)
            live = by_tick.get(int(round(t / .05)) - 1, [])
            panel, zb = rbb.render(sc, pose, bg=BG, outline=False, with_depth=True, emissive=sakura_glow.shader(t), **v)
            arr = np.asarray(panel).astype(np.float32).copy()
            ae, ee = math.radians(v["az"]), math.radians(v["el"])
            cam = np.array([math.cos(ee) * math.sin(ae), math.sin(ee), math.cos(ee) * math.cos(ae)])
            right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
            W, H = v["size"]
            ox = W * .5 - np.array(v["center"], float) @ right * v["scale"]
            oy = H * v["cy"] + np.array(v["center"], float) @ up * v["scale"]
            shaderclip.draw(arr, zb, live, 100 + t, cam, right, up, ox, oy, v["scale"], textures)
            img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
            on = hit - .02 <= t <= hit + .3
            rbb.text(img, (8, 4), f"{label}  {t:.1f}s" + ("  ← 決め" if on else ""), rbb.SMALL, (255, 150, 190) if on else (236, 228, 244))
            frames.append(img)
            if abs(t - (hit + .3)) < .051:
                keys.append(img)
    frames = [f.convert("P", palette=Image.ADAPTIVE, colors=200) for f in frames]
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=100, loop=0, optimize=True)
    sheet = Image.new("RGB", (560 * 5, 360 * 2))
    for i, k in enumerate(keys[:10]):
        sheet.paste(k, ((i % 5) * 560, (i // 5) * 360))
    sheet.save(Path(out).with_suffix(".png"))
    print(out, len(frames))


if __name__ == "__main__":
    main(*sys.argv[1:3])
