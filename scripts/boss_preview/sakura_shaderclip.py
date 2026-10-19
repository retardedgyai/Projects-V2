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
import sakura_fx  # noqa: E402
import sakura_fx_styles  # noqa: E402


def sakura_ramp(h):
    c0, c1, c2, c3 = (np.array(v) for v in ((.14, .03, .09), (.78, .20, .47), (1.0, .62, .80), (1.0, .94, .97)))
    h = h[..., None]
    c = c0 + (c1 - c0) * shaderclip.sstep(0, .35, h)
    c = c + (c2 - c) * shaderclip.sstep(.35, .7, h)
    return c + (c3 - c) * shaderclip.sstep(.7, 1.0, h)


shaderclip.ramp = sakura_ramp
shaderclip.UPRIGHT = shaderclip.UPRIGHT + ("sakura_blossom", "sakura_curtain")

def carry(name, t):
    """The body offset (x, z) the server adds while it carries the beast (QuestEncounterCombat *_CARRY), in px; the
    model's +X is the carrier's right."""
    def ease(a, b):
        k = min(1, max(0, (t - a) / (b - a))); return k * k * (3 - 2 * k)
    if name == "sakura-pounce":
        return 0, 112 * ease(.6, 1.1)
    if name == "sakura-charge":
        return 0, 13 * .8 * 16 * min(1, max(0, (t - .55) / .8))
    if name == "sakura-lunge":
        return 0, 48 * ease(.2, .5)
    if name == "sakura-barge":
        return 0, 56 * ease(.3, .65)
    if name == "sakura-hopswipe":
        return 40 * ease(.2, .55), 0
    return 0, 0


def sample(a, name, t):
    pose = sakura_clips.sample(a, t)
    dx, dz = carry(name, t)
    if dx or dz:
        rot, pos, scl = pose.get("body", (np.zeros(3), np.zeros(3), np.ones(3)))
        pose["body"] = (rot, pos + np.array([dx, 0, dz]), scl)
    return pose


SIGIL = [("sigil", "桜紋の陣（怒り後・協力）：紋に一人ずつ立つ", .55)]
FINALE = [("finale", "千本桜（HP25%）：柱の陰に隠れろ", .3)]
LIGHT = [("sakura-lunge", "踏み込み爪（小技）", .95), ("sakura-barge", "花肩の体当たり（小技）", .95), ("sakura-hopswipe", "跳ね尾払い（小技）", .95)]
NEW = [("sakura-pounce", "花冠の飛びかかり（新）", .8), ("sakura-rain", "花冠落とし（新）", .8), ("sakura-charge", "花吹雪の突進（新・怒り後）", .55)]
ORDER = [("sakura-claw", "桜爪の叩きつけ", 1.0), ("sakura-claw2", "桜爪・二の手", 1.0), ("sakura-claw3", "桜爪・止め（怒り後）", 1.0),
         ("sakura-tail", "花尾の宙返り", .9), ("sakura-horn", "枝角の突き上げ", 1.1), ("sakura-spin", "花尾の旋回", .9),
         ("sakura-scatter", "散華", .7), ("sakura-bloom", "花嵐（怒り後）", .62), ("sakura-howl", "散り際の遠吠え（怒り後）", .7),
         ("enrage", "怒りの遠吠え", .55)]
BG = ((52, 50, 64), (22, 20, 28))


def main(dump, out, which="all"):
    sc = rbb.Scene(sakura_clips.PATH)
    anims = {a["name"]: a for a in json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))["animations"]}
    rows = [json.loads(l) for l in Path(dump).read_text().splitlines()]
    textures = {p.stem: np.asarray(Image.open(p).convert("RGBA")).astype(np.float32) for p in shaderclip.TEX.glob("*.png")}
    frames, keys = [], []
    for name, label, zoom in (NEW if which == "new" else LIGHT if which == "light" else SIGIL if which == "sigil" else FINALE if which == "finale" else ORDER + NEW if which == "all" else ORDER):
        clip = name if name in fang.ENRAGED_ONLY or name == "enrage" else name
        a = anims[clip]
        by_tick = {}
        for r in rows:
            if r["ability"] == name:
                by_tick.setdefault(r["tick"], []).append(r)
        hit = fang.ABILITIES.get(name, ({"sigil": 6.0, "finale": 8.0}.get(name, 2.5), 0))[0]
        v = dict(az=35, el=48 if name in ("sigil", "finale") else 26, size=(560, 360), scale=zoom * 1.6,
                 center=(0, 20, 0 if name in ("sigil", "finale") else 40 + (70 if name in dict((n, 0) for n, _, _ in NEW) else 30 if name in dict((n, 0) for n, _, _ in LIGHT) else 0)), cy=.6 if name in ("sigil", "finale") else .66)
        storm = sakura_fx.Fx(name)                         # the petal storm (look B) that plays with the strike in game
        look = sakura_fx_styles.Style("B")
        st = 0
        n = int(a["length"] / .1)
        for i in range(n + 1):
            t = min(a["length"], i * .1)
            while st * sakura_fx.DT <= t:
                tt = st * sakura_fx.DT
                storm.emit(tt, sc, sample(a, name, min(tt, a["length"])))
                storm.step(tt)
                look.hist = {id(q): (look.hist.get(id(q), []) + [q.p.copy()])[-look.trail:] for q in storm.petals}
                st += 1
            pose = sample(a, name, t)
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
            img = look.draw(img, zb, storm.petals, t, (v["az"], v["el"], v["size"], v["scale"], v["cy"], v["center"]), [])
            on = hit - .02 <= t <= hit + .3
            rbb.text(img, (8, 4), f"{label}  {t:.1f}s" + ("  ← 決め" if on else ""), rbb.SMALL, (255, 150, 190) if on else (236, 228, 244))
            if not out.endswith(".png"):
                frames.append(img)                       # a .png out keeps only the overview (light on memory)
            for want in (hit * .7, hit + .05, hit + .4):
                if abs(t - want) < .051:
                    keys.append(img)
    if frames:
        frames = [f.convert("P", palette=Image.ADAPTIVE, colors=200) for f in frames]
        frames[0].save(out, save_all=True, append_images=frames[1:], duration=100, loop=0, optimize=True)
    rows = (len(keys) + 2) // 3
    sheet = Image.new("RGB", (560 * 3, 360 * rows))
    for i, k in enumerate(keys):
        sheet.paste(k, ((i % 3) * 560, (i // 3) * 360))
    sheet.save(Path(out).with_suffix(".png"))
    print(out, len(frames))


if __name__ == "__main__":
    main(*sys.argv[1:4])
