"""GIF of an ability clip with the shader VFX (CPU emulation of the boss branch of scripts/shaders/item.fsh, NOT Minecraft).

    SHADE_VFX_DUMP=/tmp/vfx.jsonl ./gradlew :server-minestom:test --tests '*ShadeWyrmVfxTest' --rerun
    python scripts/build_boss_vfx.py      # the data maps
    python scripts/boss_preview/shaderclip.py /tmp/vfx.jsonl shade-sweep out.gif

The dump's "shader" rows are the item displays ShadeWyrmShaderFx shows each tick from the wind-up on (position,
scale, rotation, progress). Each is drawn as its textured quad with the shader's colour maths, depth-tested
against the model, alpha-blended (the game blends translucent items the same way). The particles of the
dump's other rows are left out: with the pack, the strike's dust is not sent.
"""
import json
import math
import sys
from pathlib import Path

from rbb import *

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/item/boss_vfx"
HIT = {"shade-sweep": 1.1, "shade-pounce": 1.4, "shade-dust": 1.7, "shade-pulse": 1.2, "shade-breath": 1.6, "shade-storm": 2.2}
JP = {"shade-sweep": "翼脚薙ぎ", "shade-pounce": "飛びかかり", "shade-dust": "鱗粉炸裂", "shade-pulse": "触角の共鳴",
      "shade-breath": "翳の吐息（怒り）", "shade-storm": "鱗粉嵐（怒り）"}
ENRAGED_ONLY = ("shade-breath", "shade-storm")
FRAME = {"shade-sweep": (.7, 40), "shade-breath": (.5, 130), "shade-storm": (.42, 0), "shade-pounce": (.75, 70), "shade-dust": (.45, 0)}
UPRIGHT = ("beam_up", "ring_up", "puff", "smoke", "curtain")


# ---- item.fsh, line for line ----
def fract(x):
    return x - np.floor(x)


def hash2(x, y):
    x, y = fract(x * 123.34), fract(y * 456.21)
    d = x * (x + 45.32) + y * (y + 45.32)
    x, y = x + d, y + d
    return fract(x * y)


def noise(x, y):
    ix, iy, fx, fy = np.floor(x), np.floor(y), fract(x), fract(y)
    ux, uy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hash2(ix, iy), hash2(ix + 1, iy)
    c, d = hash2(ix, iy + 1), hash2(ix + 1, iy + 1)
    return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy


def fbm(x, y):
    return .55 * noise(x, y) + .3 * noise(x * 2.1 + 7.3, y * 2.1 + 7.3) + .15 * noise(x * 4.3 + 1.7, y * 4.3 + 1.7)


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(h):
    c0, c1, c2, c3 = (np.array(v) for v in ((.10, .04, .16), (.42, .20, .66), (.78, .60, .98), (1.0, .80, .95)))
    h = h[..., None]
    c = c0 + (c1 - c0) * sstep(0, .35, h)
    c = c + (c2 - c) * sstep(.35, .7, h)
    return c + (c3 - c) * sstep(.7, 1.0, h)


def shade_vfx(mask, flow, across, kind, p, t):
    n = fbm(flow * 9.0 - t * 2.4, across * 5.0 + t * .7)
    if kind == 1:
        head = p * 1.25
        trail = np.clip(1 - (head - flow) / .55, 0, 1) * (flow <= head)
        body = mask * trail * sstep(.15, .55, n + trail * .5)
        heat = body * (.7 + .5 * trail) + sstep(.85, 1.0, trail) * mask * .6
        alpha = np.minimum(1, body * 1.5) * (1 - sstep(.75, 1.0, p))
    elif kind == 2:
        flick = .75 + .25 * np.sin(t * 40 + flow * 60)
        heat = mask * (.75 + .35 * n) * flick + mask * .3
        alpha = np.minimum(1, mask * 1.3) * (.8 + .2 * n) * (1 - p * p)
    elif kind == 3:
        stream = fbm(flow * 14 - t * 9, across * 3)
        heat = mask * mask * (.6 + .6 * stream)
        alpha = mask * sstep(.1, .5, stream + mask * .5) * sstep(0, .12, p) * (1 - sstep(.6, 1.0, p))
    elif kind == 4:
        threshold = p * 1.1
        v = mask * .7 + n * .5
        edge = 1 - sstep(0, .12, v - threshold)
        heat = mask * (.95 - .55 * p) + edge
        alpha = (v >= threshold) * mask * (1 - .4 * p)
    elif kind == 5:
        rise = fbm(flow * 18, across * 2.5 - t * 3)
        heat = mask * rise * (1 - across * .6) + .2 * mask
        alpha = mask * sstep(.25, .65, rise + (1 - across) * .3) * (1 - across) * (1 - sstep(.55, 1.0, p))
    else:
        threshold = p * 1.1
        v = mask * .7 + n * .5
        edge = 1 - sstep(0, .15, v - threshold)
        heat = mask * .18 + edge * .55 + n * .1
        alpha = (v >= threshold) * mask * .85
    return ramp(np.clip(heat, 0, 1)), np.clip(alpha, 0, 1)


# ---- geometry ----
def quat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def corners(v, cam_right, cam_up):
    """World (blocks) of the quad's uv (0,0), (1,0), (0,1) corners."""
    at, tr, sc = (np.array(v[k], float) for k in ("at", "translation", "scale"))
    if v["billboard"]:
        c = at + tr
        # Blockbench px axes -> world: x is mirrored.
        r, u = np.array([-cam_right[0], cam_right[1], cam_right[2]]), np.array([-cam_up[0], cam_up[1], cam_up[2]])
        f = lambda a, b: c + r * a * sc[0] + u * b * sc[1]
        return f(-.5, .5), f(.5, .5), f(-.5, -.5)
    R = quat(v["rotation"])
    if v["sprite"] in UPRIGHT:
        f = lambda a, b: at + tr + R @ (sc * np.array([a, b, 0]))
        return f(-.5, .5), f(.5, .5), f(-.5, -.5)
    f = lambda a, b: at + tr + R @ (sc * np.array([a, 0, b]))
    return f(-.5, -.5), f(.5, -.5), f(-.5, .5)


def to_px(w):
    return np.array([-w[0] * 16, w[1] * 16, w[2] * 16])


def draw(img, zb, views, t, cam, right, up, ox, oy, scale, textures):
    W, H = img.shape[1], img.shape[0]
    P = lambda p: (np.array([ox + p @ right * scale, oy - p @ up * scale]), p @ cam)
    items = []
    for v in views:
        c0, c1, c2 = (to_px(c) for c in corners(v, right, up))
        items.append(((c0 + c1 + c2) / 3 @ cam, v, c0, c1, c2))
    for _, v, c0, c1, c2 in sorted(items, key=lambda i: i[0]):
        (p0, d0), (p1, d1), (p2, d2) = P(c0), P(c1), P(c2)
        E1, E2 = p1 - p0, p2 - p0
        det = E1[0] * E2[1] - E1[1] * E2[0]
        if abs(det) < 1e-6:
            continue
        q = [p0, p1, p2, p1 + E2]
        x0, x1 = max(0, int(min(a[0] for a in q))), min(W, int(max(a[0] for a in q)) + 2)
        y0, y1 = max(0, int(min(a[1] for a in q))), min(H, int(max(a[1] for a in q)) + 2)
        if x0 >= x1 or y0 >= y1:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
        dx, dy = X - p0[0], Y - p0[1]
        u = (dx * E2[1] - dy * E2[0]) / det
        w = (E1[0] * dy - E1[1] * dx) / det
        inside = (u >= 0) & (u <= 1) & (w >= 0) & (w <= 1)
        depth = d0 + u * (d1 - d0) + w * (d2 - d0)
        inside &= depth > zb[y0:y1, x0:x1]
        if not inside.any():
            continue
        tex = textures[v["sprite"]]
        th, tw = tex.shape[:2]
        tx = np.clip((u * tw).astype(int), 0, tw - 1); ty = np.clip((w * th).astype(int), 0, th - 1)
        data = tex[ty, tx] / 255.0
        ok = inside & (data[..., 3] > .5)
        rgb, a = shade_vfx(data[..., 0], data[..., 1], data[..., 2], v["kind"], v["progress"], t)
        a = a * ok
        region = img[y0:y1, x0:x1]
        region[:] = region * (1 - a[..., None]) + rgb * 255 * a[..., None]


def main(dump, name, out, fps=15):
    model = ROOT / "assets/bosses/shade_wyrm.bbmodel"
    sc = Scene(model)
    anims = {a["name"]: a for a in json.loads(model.read_text(encoding="utf-8"))["animations"]}
    from vfxclip import sample
    a = anims[name]
    hidden = ("wing_l", "wing_r") if name in ENRAGED_ONLY else ("horn_l", "horn_r", "cloak_l", "cloak_r")
    rows = [json.loads(l) for l in Path(dump).read_text().splitlines()]
    rows = [r for r in rows if r["ability"] == name and r["phase"] == "shader"]
    by_tick = {}
    for r in rows:
        by_tick.setdefault(r["tick"], []).append(r)
    textures = {p.stem: np.asarray(Image.open(p).convert("RGBA")).astype(np.float32) for p in TEX.glob("*.png")}
    bg = ((52, 50, 64), (22, 20, 28))                 # the night arena
    zoom, cz = FRAME.get(name, (1.0, 30))
    views = [dict(az=90, el=6, size=(380, 280), scale=zoom, center=(0, 40, cz), cy=.66),
             dict(az=30, el=28 if name == "shade-storm" else 22, size=(380, 280), scale=zoom, center=(0, 30, cz), cy=.6)]
    frames = []
    n = int(a["length"] * fps)
    times = [a["length"] * i / n for i in range(n + 1)]
    if out.endswith(".png"):
        times = [HIT[name] * .2, HIT[name] * .45, HIT[name] * .7, HIT[name] - .1, HIT[name] + .1, HIT[name] + .35]
    for t in times:
        pose = sample(a, t)
        live = by_tick.get(int(round(t / .05)) - 1, [])       # the dump's "time" is (tick + 1) * 0.05 from the start
        img = Image.new("RGB", (760, 300))
        for j, v in enumerate(views):
            panel, zb = render(sc, pose, hidden=hidden, bg=bg, outline=False, with_depth=True, **v)
            arr = np.asarray(panel).astype(np.float32).copy()
            ae, ee = math.radians(v["az"]), math.radians(v["el"])
            cam = np.array([math.cos(ee) * math.sin(ae), math.sin(ee), math.cos(ee) * math.cos(ae)])
            right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
            Wd, Hd = v["size"]
            ox, oy = Wd * .5 - np.array(v["center"], float) @ right * v["scale"], Hd * v["cy"] + np.array(v["center"], float) @ up * v["scale"]
            draw(arr, zb, live, 100 + t, cam, right, up, ox, oy, v["scale"], textures)
            img.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)), (380 * j, 20))
        on = HIT[name] - .02 <= t <= HIT[name] + .25
        text(img, (8, 1), f"{JP[name]}  {t:.1f}s / {a['length']}s" + ("   ← 当たる" if on else ""), MID, (255, 110, 130) if on else (236, 228, 244))
        frames.append(img)
    if out.endswith(".png"):
        strip = Image.new("RGB", (760 * 2, 300 * 3))
        for i, f in enumerate(frames):
            strip.paste(f, ((i % 2) * 760, (i // 2) * 300))
        strip.save(out)
        return
    frames = [f.convert("P", palette=Image.ADAPTIVE, colors=160) for f in frames]
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=round(1000 / fps), loop=0, optimize=True)


if __name__ == "__main__":
    main(*sys.argv[1:4])
