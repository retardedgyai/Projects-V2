"""桜牙獣 花吹雪 effects: a petal simulation per clip, drawn over the animated model. CPU preview, NOT Minecraft.

The emitters and physics here are the design the server-side effect follows (model space in px, 16 px = 1 block,
the beast at the origin facing +Z; times in seconds from the clip start, the strike at the ability telegraph).

    python scripts/boss_preview/sakura_fx.py      # build/boss-previews/sakura_fang_fx.gif + sakura_fang_fx.png
"""
import json
import math
import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402
import rbb  # noqa: E402
import sakura_clips  # noqa: E402
import sakura_glow  # noqa: E402
import build_boss_sakura_fang as fang  # noqa: E402

DT = .05
GRAVITY = -60.0          # px/s^2: petals sink slowly
DRAG = 1.6               # 1/s toward the wind
WIND = np.array([8.0, 0.0, -14.0])
PINKS = [(255, 226, 236), (248, 186, 208), (238, 140, 176), (214, 92, 140)]
BG = ((60, 58, 78), (24, 22, 32))


class Petal:
    __slots__ = ("p", "v", "age", "life", "size", "spin", "phase", "col", "drag", "flutter")

    def __init__(self, p, v, life, rng, size=None, drag=DRAG):
        self.p = np.array(p, float); self.v = np.array(v, float)
        self.age = 0.0; self.life = life
        self.size = size or rng.uniform(3.0, 5.0)
        self.spin = rng.uniform(4, 11); self.phase = rng.uniform(0, 6.28)
        self.col = rng.randrange(len(PINKS)); self.drag = drag
        self.flutter = rng.uniform(20, 45)


def rand_dir(rng):
    z = rng.uniform(-1, 1); a = rng.uniform(0, 2 * math.pi); r = math.sqrt(1 - z * z)
    return np.array([r * math.cos(a), z, r * math.sin(a)])


class Fx:
    """One clip's petals. emit(t, sc, pose) adds petals; step() moves them."""

    def __init__(self, clip, seed=7):
        self.clip = clip
        self.rng = random.Random(seed)
        self.petals = []
        self.acc = {}
        self.fired = set()
        self.shell_bones = sorted(fang_frames())

    def rate(self, key, per_second):
        """How many petals a continuous emitter gives this step."""
        self.acc[key] = self.acc.get(key, 0.0) + per_second * DT
        n = int(self.acc[key]); self.acc[key] -= n
        return n

    def once(self, key, t, at):
        if key in self.fired or t < at:
            return False
        self.fired.add(key)
        return True

    def shell_point(self, sc, pose):
        bone = self.rng.choice(self.shell_bones)
        return sc.point(bone, sc.bones[bone]["pivot"], pose)

    def add(self, p, v, life, **kw):
        self.petals.append(Petal(p, v, life, self.rng, **kw))

    def emit(self, t, sc, pose):
        r = self.rng
        c = self.clip
        hit = fang.ABILITIES.get(c.replace("_enraged", ""), (None,))[0]
        enraged = c.endswith("_enraged") or c in fang.ENRAGED_ONLY
        # always: a few petals shed from the shells and drift off in the wind (more once enraged)
        for _ in range(self.rate("shed", 16 if enraged or (c == "enrage" and t > 2.5) else 6)):
            self.add(self.shell_point(sc, pose), np.array([r.uniform(-15, 15), r.uniform(0, 25), r.uniform(-30, 0)]), r.uniform(1.4, 2.2))
        base = c.replace("_enraged", "")
        if base in ("sakura-claw", "sakura-claw2", "sakura-claw3"):
            tip = sc.point("hand_l", (15, 1, 31), pose) if base == "sakura-claw2" else sc.point("hand_r", (-15, 1, 31), pose)
            big = 1.5 if base == "sakura-claw3" else .6 if base == "sakura-claw2" else 1.0
            if .25 <= t <= hit - .05:                              # petals spiral into the raised claw
                for _ in range(self.rate("gather", 70)):
                    off = rand_dir(r) * r.uniform(28, 44)
                    tang = np.cross(off, [0, 1, 0]); tang /= (np.linalg.norm(tang) + 1e-6)
                    self.add(tip + off, -off * 1.9 + tang * 90, .5, drag=0.0)
            if self.once("burst", t, hit):                         # the slam throws the petals up along the strip
                for _ in range(int(110 * big)):
                    p = np.array([r.uniform(-34, 34), 2, r.uniform(16, 96)])
                    self.add(p, np.array([r.uniform(-60, 60), r.uniform(140, 300), r.uniform(-30, 90)]), r.uniform(1.2, 2.0))
                for k in range(48):                                # and a flat shock ring from the paw
                    a = k * 2 * math.pi / 48
                    self.add(tip * [1, 0, 1] + [0, 3, 0], np.array([math.cos(a), .15, math.sin(a)]) * r.uniform(280, 340), .9, drag=3.0)
        elif c == "sakura-tail":
            if .3 <= t <= 1.45:                                    # the coiling tail sheds a stream
                for _ in range(self.rate("stream", 40)):
                    bone = r.choice([b for b in self.shell_bones if "_t" in b])
                    self.add(sc.point(bone, sc.bones[bone]["pivot"], pose), np.array([r.uniform(-20, 20), r.uniform(-10, 30), r.uniform(-40, 10)]), 1.2)
            for d in range(10):                                    # the slam erupts down the strip, block by block
                if self.once(f"erupt{d}", t, hit + d * .035):
                    for _ in range(14):
                        self.add(np.array([r.uniform(-26, 26), 2, 16 * d + r.uniform(-6, 6)]),
                                 np.array([r.uniform(-60, 60), r.uniform(200, 330), r.uniform(-20, 40)]), r.uniform(1.0, 1.8))
        elif c == "sakura-scatter":
            if .2 <= t <= hit - .05:                               # petals peel off and orbit the body, widening
                for _ in range(self.rate("orbit", 60)):
                    p = self.shell_point(sc, pose)
                    rad = p * [1, 0, 1]
                    tang = np.cross([0, 1, 0], rad); tang /= (np.linalg.norm(tang) + 1e-6)
                    self.add(p, tang * 150 + rad * .8 + [0, 30, 0], 1.0, drag=.4)
            if self.once("fan", t, hit):                           # the lunge flings a fan of petals forward
                o = sc.point("chest", (0, 42, 18), pose)
                for _ in range(170):
                    a = math.radians(r.uniform(-40, 40))
                    d = np.array([math.sin(a), r.uniform(-.05, .12), math.cos(a)])
                    self.add(o + rand_dir(r) * 6, d * r.uniform(480, 640), r.uniform(1.0, 1.6), drag=2.3)
        elif base == "sakura-lunge":                               # the small blows (SakuraPetalFx, same numbers x16)
            if .2 <= t <= hit - .02:
                for _ in range(self.rate("spring", 90)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-24, 24), r.uniform(5, 32), r.uniform(-64, -24)]), r.uniform(.7, 1.1), drag=1.0)
            if self.once("rake", t, hit):
                paw = sc.point("hand_r", (-15, 1, 31), pose)
                for _ in range(90):
                    a = math.radians(r.uniform(-30, 70))
                    self.add(paw + rand_dir(r) * 5, np.array([math.sin(a), r.uniform(.2, .9), math.cos(a)]) * r.uniform(192, 320), r.uniform(.8, 1.3), drag=2.0)
        elif base == "sakura-barge":
            if .1 <= t <= hit - .05:
                for _ in range(self.rate("load", 40)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-16, 16), r.uniform(24, 56), r.uniform(-16, 16)]), .8)
            if self.once("barge", t, hit):                         # off the leading flank, low and ahead
                flank = max((sc.point("chest", (16, 34, 4), pose), sc.point("chest", (-16, 34, 4), pose)), key=lambda q: q[2])
                for _ in range(90):
                    a = math.radians(r.uniform(-45, 45))
                    self.add(flank + rand_dir(r) * 8, np.array([math.sin(a), r.uniform(-.1, .15), math.cos(a)]) * r.uniform(192, 320), r.uniform(.9, 1.3), drag=2.0)
                for _ in range(50):
                    a = math.radians(r.uniform(-70, 70))
                    self.add(np.array([flank[0], 3, flank[2]]), np.array([math.sin(a), r.uniform(.1, .35), math.cos(a)]) * r.uniform(128, 224), r.uniform(.7, 1.1), drag=2.2)
        elif base == "sakura-hopswipe":
            if .2 <= t <= hit - .05:
                for _ in range(self.rate("hop", 60)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-48, -16), r.uniform(8, 32), r.uniform(-16, 16)]), .8, drag=1.0)
            if hit - .12 <= t <= hit + .15:
                for _ in range(self.rate("whip", 130)):
                    bone = r.choice([b for b in self.shell_bones if "_t" in b])
                    p = sc.point(bone, sc.bones[bone]["pivot"], pose)
                    radial = p * [1, 0, 1]; radial = radial / (np.linalg.norm(radial) + 1e-6)
                    tang = np.cross([0, 1, 0], radial)
                    self.add(p, -tang * 208 + radial * 80 + [0, r.uniform(8, 40), 0], r.uniform(.8, 1.2), drag=1.4)
        elif base == "sakura-horn":
            if self.once("horn", t, hit):                          # the horns rake petals up and ahead
                head = sc.point("skull", (0, 44, 40), pose)
                for _ in range(140):
                    a = math.radians(r.uniform(-25, 25))
                    self.add(head + rand_dir(r) * 5, np.array([math.sin(a), r.uniform(.6, 1.4), math.cos(a)]) * r.uniform(160, 256), r.uniform(.9, 1.5), drag=2.0)
        elif base == "sakura-spin":
            if hit - .3 <= t <= hit + .25:                         # the spinning tail throws a ring of petals
                for _ in range(self.rate("spin", 120)):
                    bone = r.choice([b for b in self.shell_bones if "_t" in b])
                    p = sc.point(bone, sc.bones[bone]["pivot"], pose)
                    radial = p * [1, 0, 1]; radial = radial / (np.linalg.norm(radial) + 1e-6)
                    tang = np.cross([0, 1, 0], radial)
                    self.add(p, -tang * 190 + radial * 96 + [0, r.uniform(8, 40), 0], r.uniform(.8, 1.3), drag=1.4)
        elif base == "sakura-howl":
            if .45 <= t <= hit - .1:
                for _ in range(self.rate("howl", 50)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-24, 24), r.uniform(40, 80), r.uniform(-24, 24)]), 1.0)
            if self.once("howl", t, hit):                          # the howl blasts every petal outward
                for _ in range(400):
                    a = r.uniform(0, 2 * math.pi); d = np.array([math.cos(a), 0, math.sin(a)])
                    self.add(self.shell_point(sc, pose), d * r.uniform(320, 450) + [0, r.uniform(32, 110), 0], r.uniform(.9, 1.5), drag=1.8)
        elif base == "sakura-pounce":
            if .6 <= t <= hit - .02:
                for _ in range(self.rate("leap", 80)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-24, 24), r.uniform(-8, 24), r.uniform(-48, -16)]), r.uniform(.8, 1.2), drag=1.2)
            if self.once("land", t, hit):
                for _ in range(160):
                    a = r.uniform(0, 2 * math.pi); d = np.array([math.cos(a), 0, math.sin(a)])
                    self.add(np.array([0, 5, 112]) + d * r.uniform(8, 24), d * r.uniform(224, 352) + [0, r.uniform(48, 144), 0], r.uniform(.9, 1.5), drag=2.0)
        elif base == "sakura-rain":
            if .5 <= t <= hit - .05:
                for _ in range(self.rate("rise", 60)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-16, 16), r.uniform(144, 224), r.uniform(-16, 16)]), 1.0, drag=.3)
            if hit - .7 <= t <= hit - .02:
                for _ in range(self.rate("fall", 90)):
                    a = r.uniform(0, 2 * math.pi); rr = r.uniform(0, 48)
                    self.add(np.array([math.cos(a) * rr, r.uniform(96, 144), 112 + math.sin(a) * rr]), np.array([r.uniform(-16, 16), -224, r.uniform(-16, 16)]), .7, drag=.2)
            if self.once("rain", t, hit):
                for _ in range(150):
                    a = r.uniform(0, 2 * math.pi); d = np.array([math.cos(a), 0, math.sin(a)])
                    self.add(np.array([0, 6, 112]) + d * r.uniform(5, 19), d * r.uniform(160, 272) + [0, r.uniform(64, 160), 0], r.uniform(.9, 1.4), drag=2.0)
        elif base == "sakura-charge":
            if .55 <= t <= 1.4:
                for _ in range(self.rate("wake", 140)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-40, 40), r.uniform(8, 48), r.uniform(-96, -32)]), r.uniform(1.0, 1.6), drag=1.0)
            if self.once("skid", t, 1.4):
                o = sc.point("skull", (0, 40, 40), pose) * [1, 0, 1] + [0, 3, 0]
                for _ in range(120):
                    a = math.radians(r.uniform(-50, 50))
                    self.add(o + rand_dir(r) * 6, np.array([math.sin(a), r.uniform(.3, .9), math.cos(a)]) * r.uniform(160, 288), r.uniform(.9, 1.4), drag=2.0)
        elif c == "finale":
            if t < 8.0:
                for _ in range(self.rate("finale", 70 + 50 * t)):
                    a = r.uniform(0, 2 * math.pi); rr = r.uniform(160, 320)
                    p = np.array([math.cos(a) * rr, r.uniform(5, 64), math.sin(a) * rr])
                    self.add(p, np.array([-math.cos(a), 0, -math.sin(a)]) * rr * .9 + np.array([-math.sin(a), 0, math.cos(a)]) * 112 + [0, r.uniform(16, 48), 0], r.uniform(1.0, 1.3), drag=.2)
            if self.once("blow", t, 8.0):
                for _ in range(360):
                    self.add(self.shell_point(sc, pose), rand_dir(r) * r.uniform(160, 512) + [0, 48, 0], r.uniform(1.0, 1.8), drag=1.6)
        elif c == "sigil":
            crests = [np.array([math.cos(math.pi / 2 + (k - 1) * 2 * math.pi / 3) * 112, 0, math.sin(math.pi / 2 + (k - 1) * 2 * math.pi / 3) * 112]) for k in range(3)]
            if t < 6.0:
                for n, cc in enumerate(crests):
                    for _ in range(self.rate(f"sigil{n}", 26)):
                        a = r.uniform(0, 2 * math.pi); d = np.array([math.cos(a), 0, math.sin(a)])
                        self.add(cc + d * 29 + [0, 3, 0], np.array([-d[2], 0, d[0]]) * 64 + [0, r.uniform(32, 64), 0] - d * 16, r.uniform(1.0, 1.6), drag=.3)
            if self.once("resolve", t, 6.0):
                for cc in crests:
                    for _ in range(60):
                        a = r.uniform(0, 2 * math.pi); d = np.array([math.cos(a), 0, math.sin(a)])
                        self.add(cc + d * r.uniform(3, 22) + [0, 6, 0], d * r.uniform(96, 192) + [0, r.uniform(64, 160), 0], r.uniform(.9, 1.5), drag=1.8)
        elif c == "sakura-bloom":
            if t <= hit - .1:                                      # a vortex rises round the beast
                radius = 40 + 70 * (t / hit)
                for _ in range(self.rate("vortex", 90)):
                    a = r.uniform(0, 2 * math.pi)
                    p = np.array([math.cos(a) * radius, r.uniform(0, 10), math.sin(a) * radius])
                    tang = np.array([-math.sin(a), 0, math.cos(a)])
                    self.add(p, tang * 190 + [0, r.uniform(60, 150), 0] - p * .5, 1.1, drag=.3)
            if self.once("ring", t, hit):                          # the crash bursts it into a ring out to 8 blocks
                for _ in range(240):
                    a = r.uniform(0, 2 * math.pi)
                    d = np.array([math.cos(a), 0, math.sin(a)])
                    self.add(d * r.uniform(10, 30) + [0, 4, 0], d * r.uniform(400, 520) + [0, r.uniform(40, 130), 0], r.uniform(1.0, 1.6), drag=1.9)
                for _ in range(60):
                    self.add(np.array([r.uniform(-10, 10), 10, r.uniform(-10, 10)]), np.array([r.uniform(-40, 40), r.uniform(260, 360), r.uniform(-40, 40)]), 1.6)
            if hit + .2 <= t <= hit + 1.0:                         # and it snows petals over the ring
                for _ in range(self.rate("snow", 50)):
                    a = r.uniform(0, 2 * math.pi); rr = 128 * math.sqrt(r.random())
                    self.add(np.array([math.cos(a) * rr, 100, math.sin(a) * rr]), np.array([0, -30, 0]), 2.0)
        elif c == "enrage":
            if .6 <= t <= 2.4:                                     # the shells shiver petals loose
                for _ in range(self.rate("shiver", 40)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-30, 30), r.uniform(40, 90), r.uniform(-30, 30)]), 1.2)
            if self.once("burst", t, 2.5):                         # the roar: every petal bursts at once
                for _ in range(260):
                    self.add(self.shell_point(sc, pose), rand_dir(r) * r.uniform(220, 400) + [0, 60, 0], r.uniform(1.2, 2.0), drag=1.4)
            if t >= 2.5:                                           # then a storm circles the arena
                for _ in range(self.rate("storm", 80)):
                    a = r.uniform(0, 2 * math.pi); rr = r.uniform(60, 170)
                    p = np.array([math.cos(a) * rr, r.uniform(5, 90), math.sin(a) * rr])
                    self.add(p, np.array([-math.sin(a), 0, math.cos(a)]) * 170 + [0, r.uniform(-20, 30), 0], 1.4, drag=.2)
        elif c.startswith("death"):
            if t >= .5:
                for _ in range(self.rate("fall", 40)):
                    self.add(self.shell_point(sc, pose), np.array([r.uniform(-20, 20), r.uniform(-10, 10), r.uniform(-20, 20)]), 1.8)

    def step(self, t):
        alive = []
        for q in self.petals:
            q.age += DT
            if q.age > q.life:
                continue
            flutter = np.array([math.sin(q.age * 7 + q.phase), 0, math.cos(q.age * 5 + q.phase)]) * q.flutter
            q.v += (np.array([0, GRAVITY, 0]) + q.drag * (WIND - q.v) + flutter) * DT
            q.p += q.v * DT
            if q.p[1] < .5:
                q.p[1] = .5; q.v *= [.3, 0, .3]
            alive.append(q)
        self.petals = alive


def fang_frames():
    if not hasattr(fang_frames, "names"):
        data = json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))
        names = []

        def walk(n):
            if n["name"].startswith("shell_"):
                names.append(n["name"])
            for c in n["children"]:
                if not isinstance(c, str):
                    walk(c)
        for n in data["outliner"]:
            walk(n)
        fang_frames.names = names
    return fang_frames.names


def camera(az, el, size, scale, cy, center):
    a, e = math.radians(az), math.radians(el)
    cam = np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross([0, 1.0, 0], cam); right /= np.linalg.norm(right); up = np.cross(cam, right)
    W, H = size
    ox, oy = W * .5, H * cy
    c = np.array(center, float); ox -= c @ right * scale; oy += c @ up * scale
    return cam, right, up, ox, oy


def draw_petals(img, zb, petals, t, view):
    az, el, size, scale, cy, center = view
    cam, right, up, ox, oy = camera(az, el, size, scale, cy, center)
    d = ImageDraw.Draw(img)
    order = sorted(petals, key=lambda q: q.p @ cam)
    for q in order:
        sx, sy = ox + q.p @ right * scale, oy - q.p @ up * scale
        ix, iy = int(sx), int(sy)
        if not (0 <= ix < img.width and 0 <= iy < img.height):
            continue
        if zb[iy, ix] > q.p @ cam + 1.0:
            continue                                               # behind the model
        fade = min(1.0, (q.life - q.age) / .35)
        s = q.size * scale * .5 * (.6 + .4 * min(1.0, q.age / .1))
        tumble = abs(math.cos(q.age * q.spin + q.phase))
        ang = q.age * q.spin * .6 + q.phase
        ca, sa = math.cos(ang), math.sin(ang)
        pts = []
        for u, v in ((0, -1), (.75 * tumble + .15, 0), (0, 1), (-.75 * tumble - .15, 0)):
            pts.append((sx + (u * ca - v * sa) * s, sy + (u * sa + v * ca) * s))
        col = PINKS[q.col] if fade >= 1 else tuple(int(PINKS[q.col][i] * fade + 40 * (1 - fade)) for i in range(3))
        d.polygon(pts, fill=col)
        if s > 3:
            d.line([pts[0], pts[2]], fill=PINKS[min(3, q.col + 1)], width=1)


def clip_frames(sc, anims, name, view, every=2, label=""):
    a = anims[name]
    L = a["length"]
    fx = Fx(name)
    out = []
    steps = int(round(L / DT))
    for i in range(steps + 1):
        t = i * DT
        pose = sakura_clips.sample(a, min(t, L))
        fx.emit(t, sc, pose)
        fx.step(t)
        if i % every == 0:
            az, el, size, scale, cy, center = view
            img, zb = rbb.render(sc, pose, az=az, el=el, size=size, scale=scale, cy=cy, center=center, bg=BG,
                                 with_depth=True, emissive=sakura_glow.shader(t))
            draw_petals(img, zb, fx.petals, t, view)
            rbb.text(img, (10, 8), label or sakura_clips.JP.get(name, name), font=rbb.SMALL)
            out.append((t, img))
    return out


def main():
    sc = rbb.Scene(sakura_clips.PATH)
    anims = {a["name"]: a for a in json.loads(sakura_clips.PATH.read_text(encoding="utf-8"))["animations"]}
    view = (38, 18, (720, 440), 2.05, .74, (0, 24, 40))
    order = [("idle", "待機（花びらが散り続ける）"), ("sakura-claw", "桜爪の叩きつけ"), ("sakura-tail", "花尾の振り下ろし"),
             ("sakura-scatter", "散華"), ("enrage", "怒り"), ("sakura-bloom", "花嵐（怒り後）")]
    frames, keys = [], []
    for name, label in order:
        fr = clip_frames(sc, anims, name, view, label=label)
        frames += [f for _, f in fr]
        hit = fang.ABILITIES.get(name, (2.5 if name == "enrage" else 1.2, 0))[0]
        for want in (hit * .7, hit + .1, hit + .5):
            keys.append(min(fr, key=lambda tf: abs(tf[0] - want))[1])
    out = sakura_clips.OUT
    frames[0].save(out / "sakura_fang_fx.gif", save_all=True, append_images=frames[1:], duration=100, loop=0)
    sheet = Image.new("RGB", (3 * 720, len(order) * 440))
    for k, img in enumerate(keys):
        sheet.paste(img, ((k % 3) * 720, (k // 3) * 440))
    sheet.save(out / "sakura_fang_fx.png")
    print(out / "sakura_fang_fx.gif", len(frames))


if __name__ == "__main__":
    main()
