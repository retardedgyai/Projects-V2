"""Three looks for the 花吹雪 effect, side by side on the same strike (桜爪の叩きつけ) and the enrage roar.
CPU preview, NOT Minecraft. Uses the petal simulation in sakura_fx.py; only the drawing differs.

  A 光る花びら   petals are self-lit with a soft bloom, short light trails and "+" glints
  B 花の奔流     petals leave long glowing ribbons, so the storm reads as streams and spirals
  C 光の大輪     A, plus a flash on the strike and a giant sakura crest of light that blooms on the ground

    python scripts/boss_preview/sakura_fx_styles.py   # build/boss-previews/sakura_fx_styles.{gif,png}
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
import sakura_fx as fx  # noqa: E402

CORE = np.array([255, 236, 244], float)
HOT = np.array([255, 150, 196], float)
DEEP = np.array([200, 60, 130], float)


def petal_shape(cx, cy, s, ang, tumble):
    """A notched sakura petal (base at the bottom, two lobes and the notch at the tip)."""
    w = .55 * tumble + .12
    pts = [(0, 1), (w * .6, .4), (w, -.3), (w * .55, -.9), (.0, -.62), (-w * .55, -.9), (-w, -.3), (-w * .6, .4)]
    ca, sa = math.cos(ang), math.sin(ang)
    return [(cx + (u * ca - v * sa) * s, cy + (u * sa + v * ca) * s) for u, v in pts]


class Style:
    def __init__(self, key):
        self.key = key
        self.trail = {"A": 3, "B": 12, "C": 3}[key]
        self.hist = {}

    def draw(self, img, zb, petals, t, view, hits):
        az, el, size, scale, cy, center = view
        cam, right, up, ox, oy = fx.camera(az, el, size, scale, cy, center)
        P = lambda p: (ox + p @ right * scale, oy - p @ up * scale)
        glow = Image.new("RGB", img.size)
        g = ImageDraw.Draw(glow)
        base = ImageDraw.Draw(img)
        alive = set()
        for q in petals:
            alive.add(id(q))
            h = self.hist.setdefault(id(q), [])
            h.append(q.p.copy())
            del h[:-self.trail]
        for k in list(self.hist):
            if k not in alive:
                del self.hist[k]
        # C: the strike flashes white and a crest of light blooms on the ground
        if self.key == "C":
            for (th, centre, radius) in hits:
                age = t - th
                if 0 <= age <= .9:
                    grow = min(1.0, age / .22) ** .5
                    fade = 1.0 if age < .3 else max(0.0, 1 - (age - .3) / .6)
                    R = radius * (.35 + .65 * grow)
                    col = tuple(int(c * fade) for c in HOT)
                    rim = tuple(int(c * fade) for c in CORE)
                    for k in range(5):
                        a0 = k * 2 * math.pi / 5 + t * .4
                        poly = []
                        for j in range(13):
                            u = j / 12
                            r = R * (.25 + .75 * math.sin(math.pi * u) ** .6) * (1 - .18 * (abs(u - .5) < .08))
                            a = a0 + (u - .5) * 1.05
                            poly.append(P(np.array(centre) + [math.cos(a) * r, 1, math.sin(a) * r]))
                        g.polygon(poly, fill=tuple(int(c * .45) for c in col), outline=rim)
                    for k in range(10):                     # rays
                        a = k * math.pi / 5 + .3
                        g.line([P(np.array(centre) + [0, 1, 0]), P(np.array(centre) + [math.cos(a) * R * 1.4, 1, math.sin(a) * R * 1.4])],
                               fill=tuple(int(c * fade * .7) for c in HOT), width=2)
                if 0 <= age <= .1:                          # one white frame on the strike
                    sx, sy = P(np.array(centre) + [0, 20, 0])
                    rr = 120 * (1 - age / .1 * .5)
                    g.ellipse([sx - rr, sy - rr * .6, sx + rr, sy + rr * .6], fill=(255, 240, 248))
        order = sorted(petals, key=lambda q: q.p @ cam)
        for q in order:
            sx, sy = P(q.p)
            ix, iy = int(sx), int(sy)
            if not (0 <= ix < img.width and 0 <= iy < img.height) or zb[iy, ix] > q.p @ cam + 1.0:
                continue
            fade = min(1.0, (q.life - q.age) / .35)
            heat = max(.0, 1 - q.age / max(.3, q.life * .6))         # fresh petals burn white-hot, then cool to pink
            body = CORE * heat + HOT * (1 - heat)
            h = self.hist.get(id(q), [])
            if len(h) > 1:                                             # light trail
                pts = [P(p) for p in h]
                n = len(pts)
                for i in range(n - 1):
                    f = (i + 1) / n * fade
                    c = tuple(int(v * f * (.8 if self.key == "B" else .5)) for v in (HOT * .7 + DEEP * .3))
                    g.line([pts[i], pts[i + 1]], fill=c, width=3 if self.key == "B" else 2)
            s = q.size * scale * .55
            tumble = abs(math.cos(q.age * q.spin + q.phase))
            shape = petal_shape(sx, sy, s, q.age * q.spin * .6 + q.phase, tumble)
            col = tuple(int(c * fade) for c in body)
            base.polygon(shape, fill=col)
            g.polygon(shape, fill=tuple(int(c * fade * .9) for c in body))
            if self.key != "B" and (int(q.phase * 100) % 9 == 0) and int(q.age * 20) % 6 < 2:   # glints
                L = 5 + 3 * tumble
                g.line([(sx - L, sy), (sx + L, sy)], fill=(255, 255, 255), width=1)
                g.line([(sx, sy - L), (sx, sy + L)], fill=(255, 255, 255), width=1)
        a = np.asarray(img, float)
        gl = np.asarray(glow, float)
        bloom = np.asarray(glow.filter(ImageFilter.GaussianBlur(6)), float) * 1.6 + np.asarray(glow.filter(ImageFilter.GaussianBlur(18)), float) * 1.2
        out = np.clip(a + gl * .35 + bloom, 0, 255).astype(np.uint8)
        return Image.fromarray(out)


def run(name, label, view, styles, t0=0.0, t1=None):
    sc = rbb.Scene(sakura_clips.PATH)
    anims = {a["name"]: a for a in json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))["animations"]}
    a = anims[name]
    L = a["length"] if t1 is None else t1
    sims = {k: fx.Fx(name) for k in styles}
    looks = {k: Style(k) for k in styles}
    hits = {"sakura-claw": [(1.2, (-12, 0, 40), 64)], "enrage": [(2.5, (0, 0, 0), 110)], "sakura-bloom": [(2.0, (0, 0, 0), 128)]}.get(name, [])
    frames = []
    for i in range(int(round(L / fx.DT)) + 1):
        t = i * fx.DT
        pose = sakura_clips.sample(a, min(t, a["length"]))
        row = []
        base = None
        for k in styles:
            sims[k].emit(t, sc, pose); sims[k].step(t)
        if t < t0 or i % 2:
            for k in styles:                      # keep the trail history moving even on skipped frames
                looks[k].hist = {id(q): (looks[k].hist.get(id(q), []) + [q.p.copy()])[-looks[k].trail:] for q in sims[k].petals}
            continue
        if base is None:
            az, el, size, scale, cy, center = view
            base = rbb.render(sc, pose, az=az, el=el, size=size, scale=scale, cy=cy, center=center, bg=fx.BG, with_depth=True,
                              emissive=sakura_glow.shader(t))
        for k in styles:
            img = looks[k].draw(base[0].copy(), base[1], sims[k].petals, t, view, hits)
            rbb.text(img, (10, 8), f"{k} {dict(A='光る花びら', B='花の奔流', C='光の大輪')[k]}  ／ {label}", font=rbb.SMALL)
            row.append(img)
        strip = Image.new("RGB", (sum(r.width for r in row) + 6 * (len(row) - 1), row[0].height), (8, 6, 10))
        x = 0
        for r in row:
            strip.paste(r, (x, 0)); x += r.width + 6
        frames.append((t, strip))
    return frames


def main():
    view = (38, 18, (560, 420), 1.9, .74, (0, 24, 40))
    styles = ("A", "B", "C")
    frames = run("sakura-claw", "桜爪の叩きつけ", view, styles)
    frames += run("enrage", "怒りの咆哮", view, styles, t0=1.8, t1=4.2)
    out = sakura_clips.OUT
    imgs = [f for _, f in frames]
    imgs[0].save(out / "sakura_fx_styles.gif", save_all=True, append_images=imgs[1:], duration=100, loop=0)
    pick = [min(frames[:12 + len(frames) // 2], key=lambda tf: abs(tf[0] - w))[1] for w in (1.0, 1.3, 1.7)]
    sheet = Image.new("RGB", (pick[0].width, pick[0].height * 3))
    for i, p in enumerate(pick):
        sheet.paste(p, (0, i * p.height))
    sheet.save(out / "sakura_fx_styles.png")
    print(out / "sakura_fx_styles.gif", len(imgs))


if __name__ == "__main__":
    main()
