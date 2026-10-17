"""GIF of an ability clip WITH the server's particles drawn over it (CPU preview, NOT Minecraft).

    SHADE_VFX_DUMP=/tmp/vfx.jsonl ./gradlew :server-minestom:test --tests '*ShadeWyrmVfxTest' --rerun
    python scripts/boss_preview/vfxclip.py /tmp/vfx.jsonl shade-sweep out.gif

The dump is every particle ShadeWyrmVfx spawns (wind-up from 0 s, strike from the hit time), facing +Z, in blocks.
Lifetimes and drift are rough stand-ins for the vanilla ones. WSEE mirrors Blockbench X, so world x maps to -x px.
"""
import json
import math
import random
import sys
from pathlib import Path

from rbb import *

ROOT = Path(__file__).resolve().parents[2]
HIT = {"shade-sweep": 1.1, "shade-pounce": 1.4, "shade-dust": 1.7, "shade-pulse": 1.2, "shade-breath": 1.6, "shade-storm": 2.2}
JP = {"shade-sweep": "翼脚薙ぎ", "shade-pounce": "飛びかかり", "shade-dust": "鱗粉炸裂", "shade-pulse": "触角の共鳴",
      "shade-breath": "翳の吐息（怒り）", "shade-storm": "鱗粉嵐（怒り）"}
ENRAGED_ONLY = ("shade-breath", "shade-storm")
FRAME = {"shade-breath": (.5, 130), "shade-storm": (.42, 0), "shade-pounce": (.75, 70)}   # zoom, centre z px
# kind: (life ticks, rise blocks/tick, size px at scale 1, fixed colour or None)
KINDS = {"dust": (14, .01, 2.4, None), "dust_color_transition": (14, .01, 2.4, None), "block": (14, -.06, 2.2, (40, 38, 46)),
         "poof": (10, .03, 6.0, (226, 222, 230)), "explosion": (8, 0, 26.0, (236, 230, 240)), "dragon_breath": (22, .015, 3.4, (214, 92, 222)),
         "reverse_portal": (16, .08, 2.2, (176, 96, 236)), "sonic_boom": (8, 0, 30.0, (120, 200, 210)), "sweep_attack": (6, 0, 22.0, (236, 236, 244))}


def sample(a, t):
    pose = {}
    for an in a["animators"].values():
        ch = {}
        for k in an["keyframes"]:
            p = k["data_points"][0]
            ch.setdefault(k["channel"], []).append((k["time"], np.array([p["x"], p["y"], p["z"]], float)))
        o = {}
        for c, keys in ch.items():
            keys.sort(key=lambda k: k[0]); v = keys[-1][1]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    v = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 1); break
            o[c] = v
        r = o.get("rotation", np.zeros(3))
        pose[an["name"]] = (np.array([-r[0], r[1], r[2]]), o.get("position", np.zeros(3)), o.get("scale", np.ones(3)))
    return pose


def particles(dump, name):
    """Expanded to single particles with spawn time, start position (px) and per-kind life."""
    rnd = random.Random(7)
    out = []
    for line in Path(dump).read_text().splitlines():
        e = json.loads(line)
        if e["ability"] != name:
            continue
        t0 = (0 if e["phase"] == "windup" else HIT[name]) + e["tick"] * .05
        n = 1 if e["kind"] in ("explosion", "sonic_boom") else min(e["count"], 12)
        for _ in range(n):
            s = e["spread"]
            x, y, z = (e["x"] + rnd.gauss(0, s[0]), e["y"] + rnd.gauss(0, s[1]), e["z"] + rnd.gauss(0, s[2]))
            c = e["color"]
            col = KINDS.get(e["kind"], KINDS["dust"])[3] or ((c >> 16) & 255, (c >> 8) & 255, c & 255)
            out.append(dict(t=t0, p=np.array([-x * 16, y * 16, z * 16]), kind=e["kind"], col=col, size=e["size"],
                            life=KINDS.get(e["kind"], KINDS["dust"])[0] * rnd.uniform(.7, 1.2)))
    return out


def project(az, el, size, scale, center, cy):
    a, e = math.radians(az), math.radians(el)
    cam = np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
    W, H = size
    ox, oy = W * .5, H * cy
    c = np.array(center, float); ox -= c @ right * scale; oy += c @ up * scale
    return lambda p: (ox + p @ right * scale, oy - p @ up * scale)


def overlay(img, parts, t, view, scale):
    d = ImageDraw.Draw(img)
    for q in parts:
        age = (t - q["t"]) / .05
        if age < 0 or age > q["life"]:
            continue
        life, rise, px, _ = KINDS.get(q["kind"], KINDS["dust"])
        k = age / q["life"]
        p = q["p"] + np.array([0, rise * 16 * age - (2.4 * age * age if q["kind"] == "block" else 0), 0])
        x, y = view(p)
        s = px * scale * (q["size"] if q["kind"].startswith("dust") else 1) * (1 - .6 * k if not q["kind"] in ("poof", "explosion", "sonic_boom") else .5 + k)
        col = tuple(int(c * (1 - .35 * k) + 30 * .35 * k) for c in q["col"])
        if q["kind"] in ("explosion", "sonic_boom", "sweep_attack"):
            d.ellipse((x - s, y - s * .6, x + s, y + s * .6), outline=col, width=max(1, int(3 * (1 - k))))
        else:
            h = max(1.0, s / 2)
            d.rectangle((x - h, y - h, x + h, y + h), fill=col)


def main(dump, name, out, fps=15):
    model = ROOT / "assets/bosses/shade_wyrm.bbmodel"
    sc = Scene(model)
    anims = {a["name"]: a for a in json.loads(model.read_text(encoding="utf-8"))["animations"]}
    a = anims[name]
    hidden = ("wing_l", "wing_r") if name in ENRAGED_ONLY else ("horn_l", "horn_r", "cloak_l", "cloak_r")
    parts = particles(dump, name)
    bg = ((150, 152, 166), (92, 92, 106))
    zoom, cz = FRAME.get(name, (1.0, 30))
    views = [dict(az=90, el=6, size=(380, 280), scale=zoom, center=(0, 40, cz), cy=.66),
             dict(az=30, el=28 if name == "shade-storm" else 22, size=(380, 280), scale=zoom, center=(0, 30, cz), cy=.6)]
    frames = []
    n = int(a["length"] * fps)
    times = [a["length"] * i / n for i in range(n + 1)]
    if out.endswith(".png"):                       # a strip around the hit, for checking
        times = [HIT[name] + d for d in (-.5, -.15, 0, .1, .2, .35)]
    for t in times:
        pose = sample(a, t)
        img = Image.new("RGB", (760, 300))
        for j, v in enumerate(views):
            panel = render(sc, pose, hidden=hidden, bg=bg, outline=True, **v)
            overlay(panel, parts, t, project(v["az"], v["el"], v["size"], v["scale"], v["center"], v["cy"]), v["scale"])
            img.paste(panel, (380 * j, 20))
        on = HIT[name] - .02 <= t <= HIT[name] + .25
        text(img, (8, 1), f"{JP[name]}  {t:.1f}s / {a['length']}s" + ("   ← 当たる" if on else ""), MID, (255, 110, 130) if on else (236, 228, 244))
        frames.append(img.convert("P", palette=Image.ADAPTIVE, colors=128))
    if out.endswith(".png"):
        strip = Image.new("RGB", (760 * 2, 300 * 3))
        for i, f in enumerate(frames):
            strip.paste(f.convert("RGB"), ((i % 2) * 760, (i // 2) * 300))
        strip.save(out)
        return
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=round(1000 / fps), loop=0, optimize=True)


if __name__ == "__main__":
    main(*sys.argv[1:4])
