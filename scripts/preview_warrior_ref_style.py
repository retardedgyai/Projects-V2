"""Warrior skills drawn by the reference rules (2026-10-20, Creator: 「このリファレンスで見ながらウォーリアーのスキル作ってみて」,
then 「本当にみたか？全然違う　差を埋めて」 → 「6技全部作り直して」).

References: wolf awwent's Outer Gods classes (Gorebound Devourer, Threadweaver), studied frame by frame at 1080p.
Rules taken from them (never their shapes):
- no bloom and no soft gradients: glowing things are flat, unlit, hard-edged pixel art;
- coarse pixels, about the size of a block texture's (16-32 per block), and big effects;
- 3-4 flat tones per effect (outline deep red, red, cream, a white-hot core), cooling in steps;
- designed silhouettes with a motif. The warrior's motif is red-hot iron: blades of iron rising from the ground,
  molten cracks, a riveted hammer;
- solid things carry a pixel texture (iron with molten seams), not plain colour;
- debris are big dark flat plates tumbling out, not small cubes;
- a hit raises a cage of flame-tongue boards around the target, and the target flashes red.

A look-only preview with scripted timing (no server): the player, greatsword and cameras are preview_warrior_cast's,
the ground is a grass texture under an overcast sky so the effects are seen as in a world.

Usage: python3 scripts/preview_warrior_ref_style.py [--ids slam,...] [--out .tools/rpage]
"""
import argparse
import math
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import preview_warrior_cast as pc

ROOT = Path(__file__).resolve().parents[1]

# the warrior's set: four flat tones and the iron
OUTLINE = (128, 18, 26)
RED = (232, 52, 38)
CREAM = (255, 214, 140)
CORE = (255, 250, 232)
IRON = ((30, 26, 30), (44, 38, 42), (60, 52, 56))
PLATE = ((26, 22, 26), (40, 34, 38))
COOL = [(CORE, CREAM, RED), (CREAM, RED, OUTLINE), (RED, OUTLINE, (70, 16, 20)), (OUTLINE, (70, 16, 20), None)]


def _h(*v):
    x = math.sin(sum(a * b for a, b in zip(v, (127.1, 311.7, 74.7, 19.3)))) * 43758.5453
    return x - math.floor(x)


# ---------------------------------------------------------------- textures (all hard-edged, flat tones)
@lru_cache(maxsize=None)
def grass(blocks=26):
    n = blocks * 16
    tones = ((86, 128, 52), (96, 140, 58), (78, 118, 48), (104, 150, 62))
    img = Image.new('RGBA', (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            px[x, y] = tones[int(_h(x, y) * 4)] + (255,)
    return img


@lru_cache(maxsize=None)
def iron_tex(w, h, seam=True, seed=0):
    """Dark iron, three tones in pixel noise, a molten seam zig-zagging up the middle and a hot top edge."""
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = IRON[min(2, int(_h(x, y, seed) * 3.2))] + (255,)
    if seam:
        x = w // 2
        for y in range(h):
            x = min(w - 2, max(1, x + (1 if _h(y, seed, 3) > .7 else -1 if _h(y, seed, 3) < .3 else 0)))
            px[x, y] = (RED if y > h * .3 else CREAM) + (255,)
        for x in range(w):  # the hot edge
            px[x, 0] = CREAM + (255,)
            if h > 2: px[x, 1] = RED + (255,)
    return img


@lru_cache(maxsize=None)
def plate_tex(seed):
    img = Image.new('RGBA', (6, 6))
    for y in range(6):
        for x in range(6):
            img.putpixel((x, y), PLATE[int(_h(x, y, seed) * 2)] + (255,))
    return img


@lru_cache(maxsize=None)
def flame_tongue(stage, seed=0, w=24, h=40):
    """A tongue of flame drawn as one silhouette in insets (dark rim, red, cream, white core); stage cools it."""
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lean = (_h(seed) - .5) * 6
    def shape(k):
        cx, base = w / 2, h - 1
        return [(cx - w * .42 * k, base), (cx - w * .46 * k, base - h * .35 * k), (cx - w * .2 * k + lean * .3, base - h * .55 * k),
                (cx - w * .3 * k + lean * .5, base - h * .78 * k), (cx + lean, base - h * k + 1),
                (cx + w * .12 * k + lean * .6, base - h * .7 * k), (cx + w * .34 * k + lean * .2, base - h * .82 * k),
                (cx + w * .3 * k, base - h * .45 * k), (cx + w * .44 * k, base - h * .25 * k), (cx + w * .4 * k, base)]
    d.polygon(shape(1.0), fill=OUTLINE + (255,))
    for tone, k in zip((RED, CREAM, CORE)[:3 - stage], (.82, .58, .34)):  # cooling drops the inner insets
        d.polygon(shape(k), fill=tone + (255,))
    return img


@lru_cache(maxsize=None)
def crack_decal(size, branches, seed, stage, reach_step=4):
    """Molten cracks: thick jagged lines, a dark rim, red, and a cream core that cools in steps."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    paths = []
    c = size / 2
    reach = reach_step / 4
    for b in range(branches):
        a = (b + _h(b, seed) * .7) / branches * math.tau
        pts = [(c, c)]
        x, y = c, c
        steps = int(size * .45 * reach * (.65 + .35 * _h(b, seed, 2)) / 3)
        for s in range(steps):
            a += (_h(b, s, seed) - .5) * .9
            x += math.cos(a) * 3; y += math.sin(a) * 3
            pts.append((x, y))
            if _h(b, s, seed, 7) > .86 and s > 1:
                fa = a + (1.0 if _h(s, b) > .5 else -1.0)
                paths.append(([(x, y), (x + math.cos(fa) * 6, y + math.sin(fa) * 6), (x + math.cos(fa + .4) * 10, y + math.sin(fa + .4) * 10)], 1))
        paths.append((pts, 2))
    d = ImageDraw.Draw(img)
    rim, mid, core = COOL[stage]
    for layer, tone in ((0, rim), (1, mid), (2, core)):
        if tone is None: continue
        for pts, weight in paths:
            if layer == 2 and weight == 1: continue
            d.line(pts, fill=tone + (255,), width={0: 5, 1: 3, 2: 1}[layer] + (weight - 1))
    return img


@lru_cache(maxsize=None)
def ring_decal(size, stage):
    """A molten ring with rivets: the hammer's stamp on the ground."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rim, mid, core = COOL[stage]
    c = size / 2
    r = c - 4
    for tone, wd in ((rim, 5), (mid, 3), (core, 1)):
        if tone: d.ellipse((c - r, c - r, c + r, c + r), outline=tone + (255,), width=wd)
    for k in range(12):
        a = k / 12 * math.tau
        x, y = c + math.cos(a) * (c * .72), c + math.sin(a) * (c * .72)
        d.rectangle((x - 2, y - 2, x + 2, y + 2), fill=(rim or OUTLINE) + (255,))
        if mid: d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=mid + (255,))
    return img


@lru_cache(maxsize=None)
def drag_decal(length, stage, seed=3):
    img = Image.new('RGBA', (10, length), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rim, mid, core = COOL[stage]
    pts, x = [], 5
    for y in range(0, length + 3, 3):
        x = min(7, max(2, x + (_h(y, seed) - .5) * 3)); pts.append((x, y))
    for tone, wd in ((rim, 5), (mid, 3), (core, 1)):
        if tone: d.line(pts, fill=tone + (255,), width=wd)
    return img


@lru_cache(maxsize=None)
def roots_decal(size, grow_step, stage):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rim, mid, core = COOL[stage]
    c = size / 2
    lines, knots = [], []
    for b in range(8):
        a = b / 8 * math.tau + .3
        pts, x, y = [(c, c)], c, c
        for s in range(int(11 * grow_step / 4)):
            a += (_h(b, s, 4) - .5) * .8
            x += math.cos(a) * 3.2; y += math.sin(a) * 3.2; pts.append((x, y))
            if s % 3 == 2: knots.append((x, y))  # knots along the root, like the rune knots on the reference's threads
        lines.append(pts)
    for tone, wd in ((rim, 4), (mid, 2), (core, 1)):
        if tone:
            for p in lines: d.line(p, fill=tone + (255,), width=wd)
    for p in knots:
        d.rectangle((p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2), fill=(rim or OUTLINE) + (255,))
        if mid: d.point(p, fill=(core or mid) + (255,))
    return img


@lru_cache(maxsize=None)
def screen_cracks(w, h, stage):
    """The ultimate's last blow cracks the screen for a moment: big hard pixel cracks over the view."""
    small = Image.new('RGBA', (w // 6, h // 6), (0, 0, 0, 0))
    d = ImageDraw.Draw(small)
    sw, sh = small.size
    tone = (OUTLINE, RED, (90, 14, 20))[stage]
    for b in range(7):
        a = b / 7 * math.tau + .4
        x, y = sw / 2, sh / 2
        pts = [(x, y)]
        for s in range(14):
            a += (_h(b, s, 11) - .5) * .8
            x += math.cos(a) * 3.5; y += math.sin(a) * 3.5; pts.append((x, y))
        d.line(pts, fill=tone + (210,), width=2)
    return small.resize((w, h), Image.NEAREST)


@lru_cache(maxsize=None)
def arc_decal(stage):
    img = Image.new('RGBA', (96, 54), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rim, mid, core = COOL[stage]
    box = (4, 6, 92, 100)
    for tone, wd in ((rim, 6), (mid, 4), (core, 1)):
        if tone: d.arc(box, 190, 350, fill=tone + (255,), width=wd)
    for k in range(7):  # gouges cut by the sweep
        a = math.radians(200 + k * 22)
        x, y = 48 + math.cos(a) * 36, 53 + math.sin(a) * 40
        d.line([(x, y), (x + math.cos(a) * 6, y + math.sin(a) * 6)], fill=(rim or OUTLINE) + (255,), width=3)
    return img


@lru_cache(maxsize=None)
def hammer_tex():
    img = iron_tex(24, 12, False, 2).copy()
    px = img.load()
    for x in range(24):  # a molten seam around the head's middle, rivets on it
        px[x, 6] = RED + (255,); px[x, 5] = OUTLINE + (255,)
    for x in (3, 9, 15, 21):
        for dx in (0, 1):
            for y in (2, 3, 9, 10): px[x + dx, y] = (88, 80, 84, 255)
    return img


# ---------------------------------------------------------------- geometry
def textured_box(lo, hi, tex, top_scale=1.0, yaw=0.0, tilt=(0.0, 0.0), center=None):
    lo, hi = np.array(lo, float), np.array(hi, float)
    c = (lo + hi) / 2 if center is None else np.array(center, float)
    base_y = lo[1]
    ca, sa = math.cos(yaw), math.sin(yaw)
    corners = []
    for i in range(8):
        p = np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)])
        if i & 2:
            p[0] = c[0] + (p[0] - c[0]) * top_scale; p[2] = c[2] + (p[2] - c[2]) * top_scale
        d = p - c
        q = c + np.array((d[0] * ca - d[2] * sa, d[1], d[0] * sa + d[2] * ca))
        q[0] += tilt[0] * (q[1] - base_y); q[2] += tilt[1] * (q[1] - base_y)
        corners.append(q)
    return [([corners[i] for i in ids], tex) for _, ids in pc.FACES]


def blade(base, height, width, yaw, tilt, seed=0):
    """An iron blade rising from the ground: a flat tapered slab with a molten seam and a hot edge."""
    x, y, z = base
    return textured_box((x - width / 2, y, z - .07), (x + width / 2, y + height, z + .07),
                        iron_tex(8, 24, True, seed % 4), top_scale=.15, yaw=yaw, tilt=tilt, center=(x, y, z))


def plane(center, yaw, w, h, tex, y0=0.0):
    """A vertical board, both sides."""
    c = np.array(center, float)
    r = np.array((math.cos(yaw), 0, math.sin(yaw))) * w / 2
    pts = [c + (0, y0 + h, 0) - r, c + (0, y0 + h, 0) + r, c + (0, y0, 0) + r, c + (0, y0, 0) - r]
    return [(pts, tex), (pts[::-1], tex.transpose(Image.FLIP_LEFT_RIGHT))]


def ground_quad(center, half_w, half_l, yaw=0.0, y=.02):
    c = np.array(center, float)
    ca, sa = math.cos(yaw), math.sin(yaw)
    def at(dx, dz): return c + np.array((dx * ca - dz * sa, y, dx * sa + dz * ca))
    return [at(half_w, half_l), at(-half_w, half_l), at(-half_w, -half_l), at(half_w, -half_l)]


def flame_cage(at, since, size=1.0, seed=0):
    """The hit: tongues of flame stood up around the target, rising then cooling in steps."""
    if not 0 <= since < 8: return []
    stage = 0 if since < 2 else 1 if since < 4 else 2 if since < 6 else 3
    grow = (.6, 1.0, 1.1, 1.1, 1.0, .9, .8, .6)[since]
    out = []
    for k in range(5):  # boards stood in a ring around the target, facing out
        a = k / 5 * math.tau + seed
        c = np.array(at, float) + np.array((math.cos(a), 0, math.sin(a))) * .55 * size
        out += plane(c, a + math.pi / 2, 1.2 * size * grow, 2.5 * size * grow, flame_tongue(stage, seed * 5 + k))
    return out


class Plates:
    """Big dark flat plates thrown out and tumbling."""
    def __init__(self, origin, count, speed, seed, up=4.5):
        self.parts = []
        for i in range(count):
            a = _h(i, seed) * math.tau
            v = np.array((math.cos(a) * speed * (.5 + _h(i, seed, 1)), up * (.5 + _h(i, seed, 3)),
                          math.sin(a) * speed * (.5 + _h(i, seed, 1))))
            spin = np.array((_h(i, seed, 6) - .5, _h(i, seed, 7) - .5, _h(i, seed, 8) - .5)) * 14
            self.parts.append((np.array(origin, float), v, .22 + .2 * _h(i, seed, 5), spin, i))

    def faces(self, t):
        out = []
        for p, v, s, spin, i in self.parts:
            q = p + v * t + np.array((0, -9.8, 0)) * t * t / 2
            if q[1] < .02: q[1] = .02
            rot = pc.Rx(spin[0] * t) @ pc.Ry(spin[1] * t) @ pc.Rz(spin[2] * t)
            pts = [q + (rot @ np.array((dx * s, 0, dz * s, 1.0)))[:3] for dx, dz in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
            tex = plate_tex(i % 5)
            out += [(pts, tex), (pts[::-1], tex)]
        return out


def thin_line(layer, camera, pts, tone, px=2):
    """Hard 1-2 pixel lines through 3D points (no anti-aliasing, no glow)."""
    d = ImageDraw.Draw(layer)
    scr = []
    for p in pts:
        c = camera.cam(np.array(p, float))
        scr.append(camera.screen(c) if c[2] > .2 else None)
    for a, b in zip(scr, scr[1:]):
        if a and b: d.line([a, b], fill=tone + (255,), width=px)


def strip(camera, layer, pts, widths, tones):
    """A crisp flat band along 3D points; widths per point (in blocks, vertical), drawn as layered bands."""
    d = ImageDraw.Draw(layer)
    for scale, tone in zip((1.0, .62, .25), tones):
        if tone is None: continue
        for (p0, w0), (p1, w1) in zip(zip(pts, widths), zip(pts[1:], widths[1:])):
            quad = [p0 + (0, w0 * scale / 2, 0), p1 + (0, w1 * scale / 2, 0), p1 - (0, w1 * scale / 2, 0), p0 - (0, w0 * scale / 2, 0)]
            cs = [camera.cam(q) for q in quad]
            if min(c[2] for c in cs) < .2: continue
            d.polygon([camera.screen(c) for c in cs], fill=tone + (255,))


# ---------------------------------------------------------------- skills
class Skill:
    name = ''
    cast = 4
    length = 40
    pose_id = 'slam'
    startup = 10
    targets = ()
    stagger = 0

    def player(self, tick): return np.zeros(3), 0.0

    def swing(self, tick):
        s = tick - (self.cast + self.startup - 3)
        return s / 6 if 0 <= s < 6 else None

    def scene(self, tick, camera): return [], None

    def target_hit(self, k): return self.impact + k * self.stagger

    @property
    def impact(self): return self.cast + self.startup


def stage_of(since, step=6):
    return min(3, max(0, since // step))


class Dash(Skill):  # 轟進
    name, pose_id, startup, length = '轟進', 'dash', 4, 30
    targets = ((0, 0, 5.6),)

    def player(self, tick):
        k = min(1, max(0, (tick - self.cast) / self.startup))
        return np.array((0, 0, 4.0 * (1 - (1 - k) ** 2))), 0.0

    def scene(self, tick, camera):
        faces = []
        pos, _ = self.player(tick)
        since = tick - self.impact
        if tick > self.cast:
            stage = stage_of(max(0, since), 5)
            length = max(.4, pos[2])
            faces.append((ground_quad((0, 0, length / 2), .32, length / 2), drag_decal(max(8, int(length * 16)), stage)))
        tgt = np.array(self.targets[0])
        faces += flame_cage(tgt, since, 1.0, 1)
        if since >= 0: faces += Plates(tgt + (0, .9, 0), 7, 3.2, 11, 3.5).faces(since / 20)

        def draw(layer, cam):
            if self.cast <= tick < self.impact + 5:
                fade = 1 if tick < self.impact else 1 - (tick - self.impact) / 5
                for k, (dx, dy) in enumerate(((-.5, .55), (.45, .85), (-.35, 1.35), (.55, 1.55), (.05, 1.05), (-.6, 1.8))):
                    z1 = pos[2] - .25; z0 = max(-.5, z1 - (2.4 + _h(k) * 1.4) * fade)
                    thin_line(layer, cam, [(dx, dy, z0), (dx, dy, z1)], CREAM if k % 3 == 0 else RED, 2)
        return faces, draw


class Slam(Skill):  # 地割り
    name, pose_id, startup, length = '地割り', 'slam', 12, 46
    at = np.array((0, 0, 2.6))
    targets = ((-.9, 0, 3.6), (1.0, 0, 3.3))
    stagger = 1

    def scene(self, tick, camera):
        faces = []
        since = tick - self.impact
        if since >= 0:
            stage = stage_of(since, 7)
            faces.append((ground_quad(self.at, 3.6, 3.6), crack_decal(96, 8, 5, stage, min(4, since + 2))))
            rise = min(1, (since + 1) / 2.5) * (1 if since < 22 else max(0, 1 - (since - 22) / 6))
            for k in range(9):  # iron blades thrown up out of the crack in a fan
                a = math.radians(-70 + k * 17.5)
                r = 1.0 + 1.3 * _h(k, 2)
                base = self.at + np.array((math.sin(a) * r, 0, math.cos(a) * r * .8 - .3))
                h = (1.1 + 1.0 * _h(k, 9)) * rise
                if h > .05:
                    faces += blade(base, h, .5 + .15 * _h(k, 4), -a, (math.sin(a) * .45, math.cos(a) * .3), k)
            faces += Plates(self.at + (0, .3, 0), 12, 4.2, 3).faces(since / 20)
        for k, t in enumerate(self.targets):
            faces += flame_cage(np.array(t), since - k, .9, k + 2)
        return faces, None


class Whirl(Skill):  # 押し薙ぎ
    name, pose_id, startup, length = '押し薙ぎ', 'whirl', 6, 32
    targets = ((-1.8, 0, 1.8), (.2, 0, 2.7), (1.9, 0, 1.5))
    stagger = 1

    def scene(self, tick, camera):
        faces = []
        since = tick - self.impact
        if since >= 0:
            faces.append((ground_quad((0, 0, 1.2), 3.2, 1.8), arc_decal(stage_of(since, 5))))
        for k, t in enumerate(self.targets):
            hit = since - k
            faces += flame_cage(np.array(t), hit, .75, k + 5)
            if hit >= 0: faces += Plates(np.array(t) + (0, 1.0, 0), 4, 2.6, 20 + k, 2.4).faces(hit / 20)

        def draw(layer, cam):
            s = tick - self.impact + 3
            if not 0 <= s < 9: return
            head = min(1, s / 3); tail = max(0, (s - 3) / 6)
            pts, widths = [], []
            for i in range(30):
                u = tail + (head - tail) * i / 29
                a = math.radians(95 - 190 * u)
                pts.append(np.array((math.sin(a) * 2.9, 1.0 + .3 * math.sin(u * math.pi), math.cos(a) * 2.9 * .85 + .2)))
                widths.append(.06 + .55 * (i / 29) ** 1.5)  # thin at the tail, full at the leading edge
            strip(cam, layer, pts, widths, (OUTLINE, RED, CREAM) if s < 5 else (OUTLINE, RED, None))
        return faces, draw


class Guard(Skill):  # 根張り
    name, pose_id, startup, length = '根張り', 'war_guard', 2, 54

    def swing(self, tick): return None

    def scene(self, tick, camera):
        faces = []
        t = tick - self.impact
        if t >= 0:
            end = max(0, (t - 42) / 6)
            stage = 0 if t < 4 else 1 if t < 30 else 2
            faces.append((ground_quad((0, 0, 0), 2.8, 2.8), roots_decal(96, min(4, t + 1), stage)))
            rise = min(1, (t + 1) / 3) * (1 - end)
            for k in range(8):  # an iron wall planted in a ring, each slab hot along its top
                a = k / 8 * math.tau + .2
                c = np.array((math.sin(a) * 1.45, 0, math.cos(a) * 1.45))
                h = 1.5 * rise * (.85 + .3 * _h(k))
                if h > .05:
                    faces += textured_box(c - (.42, 0, .1), c + (.42, h, .1), iron_tex(12, 20, True, k % 4), top_scale=.9, yaw=-a, center=c)
            if t < 8: faces += Plates((0, .1, 0), 6, 2.4, 9, 2.6).faces(t / 20)
        return faces, None


class Wound(Skill):  # 地走り
    name, pose_id, startup, length = '地走り', 'war_wound', 5, 48
    targets = ((.3, 0, 7.4),)
    stagger = 11

    def scene(self, tick, camera):
        faces = []
        since = tick - self.impact
        if since >= 0:
            run = min(1, (since + 1) / 12)
            stage = stage_of(max(0, since - 10), 6)
            length = 7 * run
            faces.append((ground_quad((0, 0, .8 + length / 2), .8, max(.2, length / 2)), drag_decal(max(8, int(length * 16)), stage, 9)))
            for k in range(10):  # blades breaking out one after another behind the running crack
                z = 1.3 + k * .66
                born = since - (z - .8) / 7 * 12
                if born < 0: continue
                h = min(1, (born + 1) / 2) * (1 if born < 16 else max(0, 1 - (born - 16) / 5)) * (1.0 + .7 * _h(k, 1))
                x = (.3 if k % 2 else -.3) + (_h(k) - .5) * .2
                if h > .05: faces += blade((x, 0, z), h, .5, k * .9, (x * .8, .22), k)
                if 0 <= born < 4: faces += Plates((x, .2, z), 3, 1.8, 40 + k, 2.6).faces(born / 20)
            faces += flame_cage(np.array(self.targets[0]), since - 11, .9, 7)
        return faces, None

    def target_hit(self, k): return self.impact + 11


class Ult(Skill):  # 鉄槌
    name, pose_id, startup, length = '鉄槌', 'war_ult', 10, 70
    at = np.array((0, 0, 3.2))
    falls = (14, 26, 38)
    targets = ((0, 0, 3.2),)

    def swing(self, tick):
        for f in self.falls:
            if 0 <= tick - (f - 3) < 6: return (tick - (f - 3)) / 6
        return None

    def target_hit(self, k): return None

    def scene(self, tick, camera):
        faces = []
        for n, f in enumerate(self.falls):
            s = tick - f
            big = 1 + .3 * n
            if -4 <= s < 7:  # the hammer: riveted iron with a molten seam, slammed down and lifted away
                y = 5.0 * (1 - min(1, (s + 4) / 4)) ** 2 if s < 0 else (0 if s < 3 else (s - 3) * 1.1)
                faces += textured_box(self.at + (-1.1 * big, y, -.6 * big), self.at + (1.1 * big, y + 1.1 * big, .6 * big), hammer_tex())
                faces += textured_box(self.at + (-.13, y + 1.1 * big, -.13), self.at + (.13, y + 3.8 * big, .13), iron_tex(4, 32, False, 1))
            if s >= 0:
                stage = stage_of(s, 8)
                faces.append((ground_quad(self.at, 2.8 * big, 2.8 * big), ring_decal(96, stage)))
                faces.append((ground_quad(self.at, 2.6 * big, 2.6 * big, n * .7, .03), crack_decal(96, 7, n + 1, stage, min(4, s + 2))))
                faces += Plates(self.at + (0, .4, 0), 10, 4.6 * big, 70 + n, 4.0).faces(s / 20)
                rise = min(1, (s + 1) / 2.5) * (1 if s < 9 else max(0, 1 - (s - 9) / 4))
                for k in range(8):
                    a = k / 8 * math.tau + n * .4
                    base = self.at + np.array((math.sin(a), 0, math.cos(a))) * 2.2 * big
                    if rise > .05: faces += blade(base, (1.0 + .6 * _h(k, n)) * rise * big, .32, -a, (math.sin(a) * .5, math.cos(a) * .5), k + n)
            faces += flame_cage(self.at, s, 1.1 * big, n * 3)
        return faces, None

    def overlay(self, tick):
        s = tick - self.falls[-1]
        return s if 0 <= s < 6 else None


SKILLS = {'dash': Dash, 'slam': Slam, 'whirl': Whirl, 'war_wound': Wound, 'war_guard': Guard, 'war_ult': Ult}


# ---------------------------------------------------------------- render
VIEWS = {'behind': ('背後（F5視点）', (0.0, 3.2, -4.2), (0.0, .6, 4.0)), 'side': ('横から', (-6.4, 2.4, 2.0), (0.0, 1.0, 2.6))}


def sky(image):
    d = ImageDraw.Draw(image)
    top, low = np.array((156, 146, 156)), np.array((196, 186, 190))
    for y in range(pc.H):
        d.line([(0, y), (pc.W, y)], fill=tuple(int(v) for v in top + (low - top) * (y / pc.H)))


def target_faces(p, red):
    lo, hi = (p[0] - .25, p[1], p[2] - .25), (p[0] + .25, p[1] + 1.5, p[2] + .25)
    corners = [np.array([hi[j] if i & (1 << j) else lo[j] for j in range(3)]) for i in range(8)]
    col = (220, 40, 40) if red else (176, 140, 112)
    return [([corners[i] for i in ids], ('shade', col)) for _, ids in pc.FACES]


def _ground_tiles():
    tex, tiles = grass(), []
    for i in range(13):
        for j in range(13):
            x, z = -13 + 2 * i + 1, -10 + 2 * j + 1
            tiles.append((ground_quad((x, 0, z), 1, 1, 0, 0), tex.crop((i * 32, j * 32, i * 32 + 32, j * 32 + 32))))
    return tiles


GROUND = _ground_tiles()


def render(skill, tick, view):
    label, eye, target = VIEWS[view]
    camera = pc.Camera(eye, target)
    camera.cam_dir_light = camera.cam(camera.eye + np.array((.3, 1.0, -.5)) / np.linalg.norm((.3, 1.0, -.5)))
    image = Image.new('RGBA', (pc.W, pc.H), (0, 0, 0, 255))
    sky(image)
    pc.draw_faces(image, camera, GROUND)
    pos, yaw = skill.player(tick)
    faces, hand = pc.player_faces(pos, yaw, skill.swing(tick))
    scene = {'castTick': skill.cast, 'startup': skill.startup, 'id': skill.pose_id, 'pulses': 3}
    faces += pc.weapon_faces(hand, pc.weapon_pose(scene, tick))
    for k, t in enumerate(skill.targets):
        h = skill.target_hit(k)
        faces += target_faces(np.array(t), h is not None and 0 <= tick - h < 6)
    extra, draw = skill.scene(tick, camera)
    pc.draw_faces(image, camera, faces + extra)
    if draw:
        lines = Image.new('RGBA', (pc.W, pc.H), (0, 0, 0, 0))
        draw(lines, camera)
        image.alpha_composite(lines)
    if isinstance(skill, Ult) and skill.overlay(tick) is not None:
        image.alpha_composite(screen_cracks(pc.W, pc.H, min(2, skill.overlay(tick) // 2)))
    d = ImageDraw.Draw(image)
    d.text((10, 6), f"{skill.name}　{label}", font=pc.FONT, fill=(255, 255, 255), stroke_width=2, stroke_fill=(30, 26, 30))
    phase = '待機' if tick < skill.cast else '構え' if tick < skill.impact else '発動'
    d.text((10, 28), f"{(tick - skill.cast) / 20:+.2f}秒　{phase}", font=pc.SMALL, fill=(240, 236, 230), stroke_width=2, stroke_fill=(30, 26, 30))
    return image.convert('RGB')


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
