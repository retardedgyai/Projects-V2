"""Warrior skills drawn by the reference rules (2026-10-20, Creator: 「このリファレンスで見ながらウォーリアーのスキル作ってみて」).

References: wolf awwent's Outer Gods classes (Gorebound Devourer, Threadweaver), see docs/development/class-cd-rework.md.
Rules taken from them (never their shapes):
- one class colour (red-hot iron) plus a white core; glowing things have no shading and bloom;
- solid things (rock spikes, iron slabs, the hammer) are their own shaded models;
- big pixel-art drawn on the ground (cracks, rings) that flash white and cool down to red, then dark;
- every hit: a white spiky flash and black shards flying out;
- thin glowing lines (speed lines, the arc of a sweep, roots) with a white core.

This is a look-only preview with scripted timing (it does not run the server); the player, greatsword and
cameras are preview_warrior_cast's. Once the look is chosen, the same pieces go into the pack and the
choreography.

Usage: python3 scripts/preview_warrior_ref_style.py [--ids slam,...] [--out .tools/rpage]
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import preview_warrior_cast as pc

ROOT = Path(__file__).resolve().parents[1]
WHITE = (255, 248, 232)
HOT = (255, 196, 120)
RED = (255, 70, 36)
DEEP = (170, 24, 22)
IRON = (52, 46, 50)
ROCK = (74, 60, 58)
SHARD = (18, 14, 16)


def _h(*v):
    x = math.sin(sum(a * b for a, b in zip(v, (127.1, 311.7, 74.7, 19.3)))) * 43758.5453
    return x - math.floor(x)


def heat(t):
    """0 = just hit (white) .. 1 = cooled (dark red)."""
    stops = [(0, WHITE), (.15, HOT), (.4, RED), (.75, DEEP), (1, (60, 12, 14))]
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if t <= b:
            k = (t - a) / (b - a)
            return tuple(int(ca[i] + (cb[i] - ca[i]) * k) for i in range(3))
    return stops[-1][1]


# ---------------------------------------------------------------- geometry
def box(lo, hi, color, glow=False, top_scale=1.0, yaw=0.0, center=None):
    """A shaded (or glowing) box; top_scale < 1 tapers it into a spike."""
    lo, hi = np.array(lo, float), np.array(hi, float)
    c = (lo + hi) / 2 if center is None else np.array(center, float)
    ca, sa = math.cos(yaw), math.sin(yaw)
    corners = []
    for i in range(8):
        p = np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)])
        if i & 2:  # top: taper toward the centre
            p[0] = c[0] + (p[0] - c[0]) * top_scale; p[2] = c[2] + (p[2] - c[2]) * top_scale
        d = p - c
        corners.append(c + np.array((d[0] * ca - d[2] * sa, d[1], d[0] * sa + d[2] * ca)))
    content = ('glow', color) if glow else ('shade', color)
    return [([corners[i] for i in ids], content) for _, ids in pc.FACES]


def spike(base, radius, height, color=ROCK, lean=(0, 0), yaw=0.0):
    x, y, z = base
    f = box((x - radius, y, z - radius), (x + radius, y + height, z + radius), color, top_scale=.12, yaw=yaw)
    for corners, _ in f:  # lean the tip
        for k, p in enumerate(corners):
            if p[1] > y + height * .5:
                corners[k] = p + np.array((lean[0], 0, lean[1])) * ((p[1] - y) / max(height, 1e-6))
    return f


def ground_quad(center, half_w, half_l, yaw=0.0, y=.02):
    c = np.array(center, float)
    ca, sa = math.cos(yaw), math.sin(yaw)
    def at(dx, dz): return c + np.array((dx * ca - dz * sa, y, dx * sa + dz * ca))
    return [at(half_w, half_l), at(-half_w, half_l), at(-half_w, -half_l), at(half_w, -half_l)]


def billboard(camera, center, r):
    return [center - camera.r * r + camera.u * r, center + camera.r * r + camera.u * r,
            center + camera.r * r - camera.u * r, center - camera.r * r - camera.u * r]


# ---------------------------------------------------------------- pixel art (ground and flashes)
def crack_art(size, branches, seed, age, reach=1.0):
    """Cracks running out from the centre: white at the core, cooling outward and with age."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = size / 2
    for b in range(branches):
        a = (b + _h(b, seed) * .6) / branches * math.tau
        x, y = c, c
        steps = int(size * .48 * reach * (.7 + .3 * _h(b, seed, 2)))
        for s in range(steps):
            a += (_h(b, s, seed) - .5) * .7
            x += math.cos(a); y += math.sin(a)
            if not (0 <= x < size and 0 <= y < size): break
            t = min(1, age + s / max(steps, 1) * .5)
            col = heat(t)
            img.putpixel((int(x), int(y)), col + (255,))
            if s < steps * .6 and 0 <= int(x) + 1 < size:
                img.putpixel((int(x) + 1, int(y)), heat(min(1, t + .25)) + (220,))
            if _h(b, s, seed, 7) > .9:  # a fork
                fx, fy, fa = x, y, a + (.9 if _h(s, b) > .5 else -.9)
                for k in range(int(steps * .25)):
                    fx += math.cos(fa); fy += math.sin(fa)
                    if 0 <= fx < size and 0 <= fy < size:
                        img.putpixel((int(fx), int(fy)), heat(min(1, t + .3)) + (230,))
    return img


def ring_art(size, radius, age, width=2):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if abs(d - radius) < width / 2 + .5 * _h(x, y):
                img.putpixel((x, y), heat(min(1, age + .1)) + (255,))
            elif abs(d - radius * .62) < .7 and int(math.degrees(math.atan2(y - c, x - c))) % 30 < 14:
                img.putpixel((x, y), heat(min(1, age + .35)) + (230,))  # an inner dashed ring, like a seal
    return img


def line_art(w, length, progress, age, seed):
    """A crack running along the ground (rows = length)."""
    img = Image.new('RGBA', (w, length), (0, 0, 0, 0))
    x = w / 2
    for y in range(int(length * progress)):
        x = min(w - 2, max(1, x + (_h(y, seed) - .5) * 1.6))
        t = min(1, age + (progress * length - y) / length * .6)
        img.putpixel((int(x), length - 1 - y), heat(t) + (255,))
        img.putpixel((int(x) + 1, length - 1 - y), heat(min(1, t + .3)) + (200,))
    return img


def star_art(size, k, rays=8, seed=0):
    """The hit flash: a spiky star, white core and red rim, swelling then thinning out."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    grow = [.45, .8, 1, .9, .6][min(k, 4)]
    for y in range(size):
        for x in range(size):
            a = math.atan2(y - c, x - c); d = math.hypot(x - c, y - c) / c
            spike = abs(math.cos(a * rays / 2 + seed)) ** 6
            limit = grow * (.28 + .72 * spike * (.6 + .4 * _h(round(a * rays / math.pi), seed)))
            if d < limit:
                core = d < limit * (.55 - k * .1)
                img.putpixel((x, y), (WHITE if core else RED) + (255,))
    return img


STAR = [[star_art(32, k, seed=s) for k in range(5)] for s in range(3)]


# ---------------------------------------------------------------- effects that live in time
class Shards:
    def __init__(self, origin, count, speed, seed, up=4.0):
        self.parts = []
        for i in range(count):
            a = _h(i, seed) * math.tau
            v = np.array((math.cos(a) * speed * (.5 + _h(i, seed, 1)), up * (.4 + _h(i, seed, 3)),
                          math.sin(a) * speed * (.5 + _h(i, seed, 1))))
            self.parts.append((np.array(origin, float), v, .06 + .08 * _h(i, seed, 5)))

    def faces(self, t):
        out = []
        for p, v, s in self.parts:
            q = p + v * t + np.array((0, -9.8, 0)) * t * t / 2
            if q[1] < 0: q[1] = 0
            out += box(q - s, q + s, SHARD, yaw=t * 7)
        return out


def ribbon(layer, glow, camera, pts, width, color=RED, core=True):
    """A thin glowing line through 3D points, drawn on screen with a white core."""
    d1, d2 = ImageDraw.Draw(layer), ImageDraw.Draw(glow)
    scr = []
    for p in pts:
        c = camera.cam(p)
        if c[2] < .2: scr.append(None); continue
        scr.append((camera.screen(c), pc.FOCAL / c[2]))
    for a, b in zip(scr, scr[1:]):
        if a is None or b is None: continue
        w = max(1, int(width * (a[1] + b[1]) / 2))
        for d in (d1, d2): d.line([a[0], b[0]], fill=color + (255,), width=w)
        if core: d1.line([a[0], b[0]], fill=WHITE + (255,), width=max(1, w // 3))


# ---------------------------------------------------------------- skills
class Skill:
    name = ''
    cast = 4
    length = 40
    pose_id = 'slam'
    startup = 10

    def player(self, tick): return np.zeros(3), 0.0

    def swing(self, tick):
        s = tick - (self.cast + self.startup - 3)
        return s / 6 if 0 <= s < 6 else None

    def scene(self, tick, camera):
        """Returns (faces, glow_faces, draw_fn(layer, glow, camera)) for this tick."""
        return [], [], None

    @property
    def impact(self): return self.cast + self.startup


def hit_flash(camera, at, since, seed=0, size=1.2):
    if 0 <= since < 5:
        return [(billboard(camera, np.array(at, float), size * (1 + since * .15)), STAR[seed % 3][since])]
    return []


class Dash(Skill):  # 轟進
    name, pose_id, startup, length = '轟進', 'dash', 4, 30

    def player(self, tick):
        k = min(1, max(0, (tick - self.cast) / self.startup))
        return np.array((0, 0, 4.0 * (1 - (1 - k) ** 2))), 0.0

    def scene(self, tick, camera):
        faces, glow = [], []
        pos, _ = self.player(tick)
        since = tick - self.impact
        if tick >= self.cast:  # scorched drag line along the path
            age = min(1, max(0, since) / 18)
            art = line_art(6, 64, min(1, (tick - self.cast) / self.startup), age, 3)
            q = (ground_quad((0, 0, pos[2] / 2 + .2), .25, max(.3, pos[2] / 2 + .2)), art)
            faces.append(q); glow.append(q)
        tip = pos + np.array((0, 1.1, 1.6))
        faces += hit_flash(camera, tip + (0, 0, .4), since, 1, 1.0)
        glow += hit_flash(camera, tip + (0, 0, .4), since, 1, 1.0)
        if since >= 0: faces += Shards(tip, 10, 3.0, 11, 2.5).faces(since / 20)

        def draw(layer, g, cam):
            if self.cast <= tick < self.impact + 6:
                fade = 1 if tick < self.impact else 1 - (tick - self.impact) / 6
                for k, (dx, dy) in enumerate(((-.45, .5), (.4, .8), (-.3, 1.3), (.5, 1.5), (0, 1.0))):
                    z1 = pos[2] - .3; z0 = max(0, z1 - 2.6 * fade - _h(k) * .8)
                    ribbon(layer, g, cam, [np.array((dx, dy, z0)), np.array((dx, dy, z1))], .03)
            if 0 <= since < 4:  # the thrust line ahead of the blade
                ribbon(layer, g, cam, [tip, tip + np.array((0, 0, 3.2 - since * .5))], .06)
        return faces, glow, draw


class Slam(Skill):  # 地割り
    name, pose_id, startup, length = '地割り', 'slam', 12, 44
    at = np.array((0, 0, 2.4))

    def scene(self, tick, camera):
        faces, glow = [], []
        since = tick - self.impact
        if since >= 0:
            age = min(1, since / 26)
            reach = min(1, (since + 1) / 3)
            q = (ground_quad(self.at, 3.2, 3.2), crack_art(64, 9, 5, age, reach)); faces.append(q); glow.append(q)
            rise = min(1, (since + 1) / 3) * (1 if since < 20 else max(0, 1 - (since - 20) / 8))
            for k in range(7):  # rock spikes thrown up in a fan
                a = math.radians(-60 + k * 20)
                r = 1.0 + 1.1 * _h(k, 2)
                base = self.at + np.array((math.sin(a) * r, 0, math.cos(a) * r * .7))
                h = (1.0 + 1.2 * _h(k, 9)) * rise
                if h > .05: faces += spike(base, .28 + .12 * _h(k, 4), h, lean=(math.sin(a) * .5, math.cos(a) * .3), yaw=k)
                # the red-hot seam at each spike's foot
                if h > .05: glow.append((ground_quad(base, .34, .34, k, .03), crack_art(8, 4, k, age + .2)))
            faces += Shards(self.at + (0, .3, 0), 16, 4.0, 3).faces(since / 20)
        faces += hit_flash(camera, self.at + (0, .8, 0), since, 0, 1.6)
        glow += hit_flash(camera, self.at + (0, .8, 0), since, 0, 1.6)
        return faces, glow, None


class Whirl(Skill):  # 押し薙ぎ
    name, pose_id, startup, length = '押し薙ぎ', 'whirl', 6, 30

    def scene(self, tick, camera):
        faces, glow = [], []
        since = tick - self.impact
        if since >= 0:
            q = (ground_quad((0, 0, 1.3), 2.8, 1.5), arc_ground(min(1, since / 18)))
            faces.append(q); glow.append(q)
            for k, z in enumerate((2.2, 1.6, 2.5)):
                x = (-1.4, 1.5, .1)[k]
                faces += Shards((x, 1.0, z), 6, 2.5, 20 + k, 2.0).faces(since / 20)
                faces += hit_flash(camera, (x, 1.0, z), since - k, k, .7)
                glow += hit_flash(camera, (x, 1.0, z), since - k, k, .7)

        def draw(layer, g, cam):
            # the sweep: 180 degrees from the left to the right in 3 ticks, its tail fading
            s = tick - self.impact + 3
            if 0 <= s < 10:
                head = min(1, s / 3); tail = max(0, (s - 4) / 6)
                pts = []
                for i in range(25):
                    u = tail + (head - tail) * i / 24
                    a = math.radians(90 - 180 * u)
                    pts.append(np.array((math.sin(a) * 2.6, 1.0 + .25 * math.sin(u * math.pi), math.cos(a) * 2.6 * .9 + .2)))
                ribbon(layer, g, cam, pts, .16)
                inner = [p * np.array((.8, 1, .8)) for p in pts]
                ribbon(layer, g, cam, inner, .025, DEEP, core=False)
        return faces, glow, draw


def arc_ground(age):
    img = Image.new('RGBA', (64, 34), (0, 0, 0, 0))
    for y in range(34):
        for x in range(64):
            d = math.hypot((x - 31.5) / 31.5, (y - 33) / 33)
            if .9 < d < .94 + .03 * _h(x, y): img.putpixel((x, y), heat(min(1, age + .35)) + (255,))
            elif .6 < d < .64 and x % 5 < 3: img.putpixel((x, y), heat(min(1, age + .4)) + (220,))
    return img


class Guard(Skill):  # 根張り
    name, pose_id, startup, length = '根張り', 'war_guard', 2, 52

    def swing(self, tick): return None

    def scene(self, tick, camera):
        faces, glow = [], []
        t = tick - self.impact
        if t >= 0:
            end = max(0, (t - 40) / 8)
            rise = min(1, (t + 1) / 4) * (1 - end)
            for k in range(8):  # iron slabs planted in a ring
                a = k / 8 * math.tau + .2
                c = np.array((math.sin(a) * 1.35, 0, math.cos(a) * 1.35))
                h = 1.3 * rise * (.8 + .3 * _h(k))
                if h > .05:
                    faces += box(c - (.32, 0, .07), c + (.32, h, .07), IRON, yaw=-a, center=c)
                    glow += box(c + (-.3, h - .06, -.08), c + (.3, h, .08), RED, glow=True, yaw=-a, center=c)
            q = (ground_quad((0, 0, 0), 2.4, 2.4), roots_art(min(1, (t + 1) / 6), min(1, end + max(0, t - 8) / 60)))
            faces.append(q); glow.append(q)
        return faces, glow, None


def roots_art(grow, age):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for b in range(10):
        a = b / 10 * math.tau
        x = y = 31.5
        for s in range(int(30 * grow)):
            a += (_h(b, s, 4) - .5) * .5
            x += math.cos(a); y += math.sin(a)
            if 0 <= x < 64 and 0 <= y < 64:
                img.putpixel((int(x), int(y)), heat(min(1, age + s / 60)) + (255,))
            if _h(b, s) > .88:
                for k in range(4):
                    xx, yy = int(x + math.cos(a + 1.1) * k), int(y + math.sin(a + 1.1) * k)
                    if 0 <= xx < 64 and 0 <= yy < 64: img.putpixel((xx, yy), heat(min(1, age + .3)) + (220,))
    return img


class Wound(Skill):  # 地走り
    name, pose_id, startup, length = '地走り', 'war_wound', 5, 46

    def scene(self, tick, camera):
        faces, glow = [], []
        since = tick - self.impact
        if since >= 0:
            run = min(1, (since + 1) / 12)  # 7 m in 0.6 s
            age = max(0, (since - 12) / 24)
            q = (ground_quad((0, 0, 4.5), .5, 3.5), line_art(8, 112, run, age * .8, 9)); faces.append(q); glow.append(q)
            for k in range(9):  # spikes popping up one by one behind the running crack
                z = 1.4 + k * .72
                born = since - (z - 1) / 7 * 12
                if born < 0: continue
                h = min(1, (born + 1) / 2) * (1 if born < 16 else max(0, 1 - (born - 16) / 6)) * (.9 + .6 * _h(k, 1))
                x = (.28 if k % 2 else -.28) + (_h(k) - .5) * .2
                if h > .05: faces += spike((x, 0, z), .26, h * 1.4, lean=(x * .9, .25), yaw=k * .7)
                if h > .05: glow.append((ground_quad((x, 0, z), .3, .3, k, .03), crack_art(8, 4, k, .2)))
                if 0 <= born < 3:
                    faces += Shards((x, .2, z), 4, 1.6, 40 + k, 2.2).faces(born / 20)
        return faces, glow, None


class Ult(Skill):  # 鉄槌
    name, pose_id, startup, length = '鉄槌', 'war_ult', 10, 66
    at = np.array((0, 0, 3.0))
    falls = (14, 26, 38)

    def swing(self, tick):
        for f in self.falls:
            if 0 <= tick - (f - 3) < 6: return (tick - (f - 3)) / 6
        return None

    def scene(self, tick, camera):
        faces, glow = [], []
        for n, f in enumerate(self.falls):
            s = tick - f
            big = 1 + .35 * n
            # the hammer: drops in 4 ticks, rests 3, lifts and fades
            if -4 <= s < 7:
                y = 4.5 * (1 - min(1, (s + 4) / 4)) ** 2 if s < 0 else (0 if s < 3 else (s - 3) * .9)
                head_lo = self.at + np.array((-1.0 * big, y, -.55 * big))
                head_hi = self.at + np.array((1.0 * big, y + 1.0 * big, .55 * big))
                faces += box(head_lo, head_hi, IRON)
                glow += box(head_lo + (.05, -.01, .05), head_hi * np.array((1, 0, 1)) + (-.05, y + .08, -.05), RED, glow=True)
                faces += box(self.at + (-.12, y + 1.0 * big, -.12), self.at + (.12, y + 3.6 * big, .12), (40, 34, 36))
            if s >= 0:
                age = min(1, s / 30)
                q = (ground_quad(self.at, 2.6 * big, 2.6 * big), ring_art(64, 20 + 4 * n, age))
                faces.append(q); glow.append(q)
                q = (ground_quad(self.at, 2.4 * big, 2.4 * big, n, .03), crack_art(64, 7, n, age, min(1, (s + 1) / 3)))
                faces.append(q); glow.append(q)
                faces += Shards(self.at + (0, .4, 0), 14, 4.5 * big, 70 + n, 3.5).faces(s / 20)
                rise = min(1, (s + 1) / 3) * (1 if s < 9 else max(0, 1 - (s - 9) / 5))
                for k in range(6):
                    a = k / 6 * math.tau + n
                    base = self.at + np.array((math.sin(a), 0, math.cos(a))) * 2.0 * big
                    if rise > .05: faces += spike(base, .25, (.9 + .5 * _h(k, n)) * rise * big, lean=(math.sin(a) * .5, math.cos(a) * .5), yaw=k)
            faces += hit_flash(camera, self.at + (0, 1.6 * big, 0), s, n, .9 * big)
            glow += hit_flash(camera, self.at + (0, 1.6 * big, 0), s, n, .9 * big)
        return faces, glow, None

    def white_frame(self, tick):
        return tick - self.falls[-1] in (0,)


SKILLS = {'dash': Dash, 'slam': Slam, 'whirl': Whirl, 'war_wound': Wound, 'war_guard': Guard, 'war_ult': Ult}


# ---------------------------------------------------------------- render
def make_camera(view):
    label, eye, target = pc.VIEWS[view]
    camera = pc.Camera(eye, target)
    camera.cam_dir_light = camera.cam(camera.eye + np.array((.3, 1.0, -.5)) / np.linalg.norm((.3, 1.0, -.5)))
    return label, camera


def render(skill, tick, view):
    label, camera = make_camera(view)
    image = Image.new('RGBA', (pc.W, pc.H), pc.BG + (255,))
    pc.grid(image, camera)
    pos, yaw = skill.player(tick)
    faces, hand = pc.player_faces(pos, yaw, skill.swing(tick))
    scene = {'castTick': skill.cast, 'startup': skill.startup, 'id': skill.pose_id, 'pulses': 3}
    faces += pc.weapon_faces(hand, pc.weapon_pose(scene, tick))
    extra, glow_faces, draw = skill.scene(tick, camera)
    pc.draw_faces(image, camera, [(c, flat(v) if isinstance(v, tuple) and v[0] == 'glow' else v) for c, v in faces + extra])
    # glowing things also go on a bloom layer
    glow = Image.new('RGBA', (pc.W, pc.H), (0, 0, 0, 0))
    pc.draw_faces(glow, camera, [(c, flat(v)) for c, v in glow_faces])
    lines = Image.new('RGBA', (pc.W, pc.H), (0, 0, 0, 0))
    if draw: draw(lines, glow, camera)
    image.alpha_composite(lines)
    bloom = np.array(glow.filter(ImageFilter.GaussianBlur(7))).astype(np.float32)
    arr = np.array(image).astype(np.float32)
    arr[..., :3] = np.minimum(255, arr[..., :3] + bloom[..., :3] * (bloom[..., 3:] / 255) * 1.1)
    image = Image.fromarray(arr.astype(np.uint8))
    if isinstance(skill, Ult) and skill.white_frame(tick):
        g = np.array(image.convert('L')); image = Image.fromarray(np.where(g > 70, 0, 255).astype(np.uint8)).convert('RGBA')
    d = ImageDraw.Draw(image)
    d.text((10, 6), f"{skill.name}　{label}", font=pc.FONT, fill=(236, 226, 206))
    phase = '待機' if tick < skill.cast else '構え' if tick < skill.impact else '発動'
    d.text((10, 28), f"{(tick - skill.cast) / 20:+.2f}秒　{phase}", font=pc.SMALL, fill=(170, 164, 154))
    return image.convert('RGB')


def flat(v):
    """Glowing faces fill flat (no face light); a plain RGB tuple is pc.draw_faces' unshaded fill."""
    return v[1] if isinstance(v, tuple) else v


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ids', default=','.join(SKILLS))
    parser.add_argument('--out', default='.tools/rpage')
    args = parser.parse_args()
    out = ROOT / args.out; out.mkdir(parents=True, exist_ok=True)
    for sid in args.ids.split(','):
        skill = SKILLS[sid]()
        frames = []
        for tick in range(skill.length):
            sheet = Image.new('RGB', (pc.W * 2, pc.H))
            sheet.paste(render(skill, tick, 'behind'), (0, 0)); sheet.paste(render(skill, tick, 'side'), (pc.W, 0))
            frames.append(sheet)
        for name, ms in (('', 50), ('-slow', 150)):
            frames[0].save(out / f'{sid}{name}.gif', save_all=True, append_images=frames[1:], duration=ms, loop=0)
        frames[min(len(frames) - 1, skill.impact + 2)].save(out / f'{sid}-hit.png')
        print(sid, len(frames), 'frames')


if __name__ == '__main__':
    main()
