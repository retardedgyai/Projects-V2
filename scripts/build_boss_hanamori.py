"""散り桜の社's two mini-bosses (the provisional first picks; docs/development/sakura-minibosses.md).

    python scripts/build_boss_hanamori.py   # writes assets/bosses/white_serpent.bbmodel and stone_lantern.bbmodel

花喰いの白蛇 (ミニボス1, 藍's pick 2026-11-07): the shrine's white serpent, coiled on the floor from its tail tip outside
the coil inward, its neck rising forward from the middle; a full five-petal bloom opens behind its head, a shimenawa
round its neck. 石灯籠の番人 (ミニボス2): a kasuga stone lantern that walks
on its split base and leans on two arms of stacked stone; its fire box is the face, pink light behind the lattice.

Front is +Z, +X is the model's left (WSEE shows it on the right), 16 px = 1 block. Every bone rests at rotation 0 and
its cubes are placed in world space (scaled by K), so a bone turns about its pivot (the joint). Bone names carry a
prefix (sn_, sl_) so no boss-only bone list (ShadeWyrmHitbox) picks them up. Clip names are the server contract
(docs/development/boss-model-pipeline.md): idle, walk, death and one once clip per skill id, as long as
its telegraph plus recovery, the blow landing at the telegraph's end.
"""
import math
import sys
from pathlib import Path

from boss_models import Model, ROOT


# --- math -------------------------------------------------------------------------------------------
def norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / n for c in v]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def dot(a, b):
    return sum(p * q for p, q in zip(a, b))


def add(a, b, k=1.0):
    return [p + q * k for p, q in zip(a, b)]


def lerp(a, b, t):
    return [p + (q - p) * t for p, q in zip(a, b)]


def basis(y_dir, z_hint):
    """Rotation (columns = local X, Y, Z) whose local Y is y_dir."""
    y = norm(y_dir)
    z = list(z_hint)
    if abs(dot(y, norm(z))) > .95:
        z = [1, 0, 0] if abs(y[0]) < .9 else [0, 0, 1]
    x = norm(cross(y, z))
    z = cross(x, y)
    return [[x[i], y[i], z[i]] for i in range(3)]


def matrix_euler(m):
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    x = math.atan2(m[2][1], m[2][2])
    z = math.atan2(m[1][0], m[0][0])
    return [round(math.degrees(v), 3) for v in (x, y, z)]


def euler_matrix(x, y, z):
    x, y, z = (math.radians(v) for v in (x, y, z))
    rx = [[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]]
    ry = [[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]]
    rz = [[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]]
    return mat_mul(rz, mat_mul(ry, rx))


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _h(*k):
    n = 0
    for v in k:
        n = (n * 1000003 + int(v) * 2654435761 + 97) & 0xffffffff
    n ^= n >> 15; n = (n * 2246822519) & 0xffffffff; n ^= n >> 13
    return (n & 0xffff) / 65535.0


# --- paint ------------------------------------------------------------------------------------------
RAMPS = {
    "white": ((146, 140, 152), (180, 176, 188), (210, 206, 216), (234, 232, 238), (250, 249, 252)),
    "straw": ((90, 70, 38), (128, 100, 54), (164, 132, 74), (194, 164, 98), (220, 194, 128)),
    "red": ((96, 18, 26), (136, 26, 36), (176, 40, 48), (206, 66, 66), (226, 100, 94)),
    "pinkc": ((140, 56, 86), (180, 82, 112), (210, 116, 142), (230, 152, 172), (242, 186, 200)),
    "pearl": ((150, 136, 148), (186, 172, 182), (214, 204, 210), (234, 228, 232), (248, 244, 246)),
    "lacq": ((14, 12, 18), (26, 22, 32), (42, 36, 52), (64, 56, 78), (92, 84, 108)),
    "ink": ((16, 18, 30), (26, 30, 48), (38, 44, 68), (54, 62, 90), (76, 86, 116)),
    "crim": ((60, 14, 26), (96, 24, 40), (138, 36, 56), (176, 58, 78), (206, 96, 110)),
    "gold": ((90, 58, 24), (156, 122, 52), (208, 179, 90), (242, 230, 160), (255, 246, 210)),
    "steel": ((120, 120, 136), (160, 160, 176), (196, 196, 210), (226, 226, 236), (250, 250, 255)),
    "wood": ((70, 46, 30), (100, 68, 44), (132, 92, 60), (160, 118, 80), (188, 146, 104)),
    "black": ((12, 12, 16), (22, 22, 28), (34, 34, 42), (50, 50, 60), (70, 70, 82)),
    "stone": ((62, 64, 70), (88, 90, 96), (114, 116, 120), (140, 140, 142), (168, 168, 166)),
    "moss": ((36, 56, 28), (52, 80, 38), (72, 104, 48), (94, 128, 60), (120, 152, 76)),
}
PINK = ((150, 30, 74), (204, 64, 112), (236, 120, 158), (248, 176, 200), (255, 226, 236))
WARM = (255, 236, 150)
GLOW = (255, 120, 170)
GLOW2 = (255, 186, 214)
SELF_LIT = 251          # alpha mark: the pack's item shader draws the texel self-lit (scripts/shaders/item.fsh)


def pick(ramp, lvl):
    return (*ramp[max(0, min(4, int(lvl)))], 255)


def shade_lvl(mat, x, y, hh, side, period=7):
    lvl = 1.0 + (1 - y / max(1, hh - 1)) * 2.2
    if ((x // 2) + (y // 2) * 3 + int(_h(x // 2, y // 2, len(mat)) * 1000)) % period == 0:
        lvl += 1 if (int(_h(x, y) * 1000) & 1) else -1
    if side == "up":
        lvl += .8
    if side == "down":
        lvl -= 1.2
    return lvl


def solid(mat):
    ramp = RAMPS[mat]

    def painter(side, w, h):
        def paint(x, y, w_, hh):
            lvl = shade_lvl(mat, x, y, hh, side)
            if y == 0 or (x == 0 and side in ("north", "west")):
                lvl = max(lvl, 3.2)
            return pick(ramp, lvl)
        return paint, (mat, side in ("up", "down"), side, w, h)
    return painter


def flat(col):
    def painter(side, w, h):
        return (lambda x, y, w_, hh: (*col, 255)), ("flat", col, w, h)
    return painter


def glow(col):
    """Self-lit in the game (alpha mark) and drawn unshaded by the preview (cube names start glow_)."""
    def painter(side, w, h):
        return (lambda x, y, w_, hh: (*col, SELF_LIT)), ("glow", col, w, h)
    return painter


def pattern(mat, fn, key):
    ramp = RAMPS[mat]

    def painter(side, w, h):
        def paint(x, y, w_, hh):
            lvl = shade_lvl(mat, x, y, hh, side, period=13)
            if y == 0:
                lvl = max(lvl, 3.0)
            out = fn(x, y, w_, hh, side, lvl)
            return out if out is not None else pick(ramp, lvl)
        return paint, (key, mat, side, w, h)
    return painter


def mossy():
    def fn(x, y, w, hh, side, lvl):
        if side == "up" or (y < 2 and side != "down"):
            if int(_h(x // 2, y // 2, 11) * 1000) % 3:
                return pick(RAMPS["moss"], 2 + (int(_h(x, y) * 1000) & 1))
        if int(_h(x // 2, y // 2, 12) * 1000) % 23 == 0:
            return pick(RAMPS["moss"], 1.6)
        return None
    return pattern("stone", fn, "mossy")


def lattice():
    """The fire box: a stone frame and bars, pink light behind them, brightest in the middle (self-lit)."""
    def painter(side, w, h):
        def paint(x, y, w_, hh):
            if side in ("up", "down"):
                return pick(RAMPS["stone"], 2)
            if x < 2 or x >= w_ - 2 or y < 2 or y >= hh - 2:
                return pick(RAMPS["stone"], 2.6 if y < 2 else 1.6)
            if (x - 2) % 3 == 2 or (y - 2) % 3 == 2:
                return pick(RAMPS["stone"], 1.4)
            core = 1 - min(1, math.hypot((x + .5) / w_ - .5, (y + .5) / hh - .5) * 2.2)
            c = lerp(GLOW, (255, 240, 248), core)
            return (*[int(q) for q in c], SELF_LIT)
        return paint, ("lattice", side, w, h)
    return painter


def crest_hit(u, v):
    """Five-petal sakura crest in u, v in [-1, 1] (petal 0 points up): ('core', r) or ('petal', along, e) or None."""
    r = math.hypot(u, v)
    if r < .2:
        return ("core", r)
    best = None
    for k in range(5):
        a = math.radians(k * 72)
        d = (math.sin(a), -math.cos(a))
        t = (math.cos(a), math.sin(a))
        pd = u * d[0] + v * d[1]
        pt = u * t[0] + v * t[1]
        e = ((pd - .5) / .5) ** 2 + (pt / .33) ** 2
        if e <= 1 and not (pd > .8 and abs(pt) < (pd - .8) * .55 + .015):
            if best is None or e < best[1]:
                best = (pd, e)
    return None if best is None else ("petal", best[0], best[1])


def crest_rgba(u, v, lit=False):
    hit = crest_hit(u, v)
    if hit is None:
        return None
    a = SELF_LIT if lit else 255
    if hit[0] == "core":
        return (*WARM, a) if hit[1] < .1 else (*PINK[1], a)
    lvl = 1.0 + hit[1] * 3.4 - (1.2 if hit[2] > .72 else 0)
    return (*PINK[max(0, min(4, int(lvl)))], a)


def crest_plate(lit=False):
    def painter(side, w, h):
        big = side in ("north", "south")

        def paint(x, y, w_, hh):
            if not big:
                return (0, 0, 0, 0)
            out = crest_rgba((x + .5) / w_ * 2 - 1, (y + .5) / hh * 2 - 1, lit)
            return out or (0, 0, 0, 0)
        return paint, ("crestplate", lit, big, w, h)
    return painter


def petal(kind="petal"):
    """A notched petal on the two big faces, tip at texture row 0."""
    def painter(side, w, h):
        big = side in ("north", "south")

        def paint(x, y, w_, hh):
            if not big:
                return (0, 0, 0, 0)
            u = (x + .5) / w_ * 2 - 1
            v = 1 - (y + .5) / hh
            half = (.22 + .78 * min(1, v / .7) ** .6) * math.sqrt(max(0, 1 - max(0, (v - .72) / .3) ** 2))
            if abs(u) > half or v > 1 - .2 * max(0, 1 - abs(u) / .3):
                return (0, 0, 0, 0)
            lvl = 1.2 + v * 3.2 - (1.1 if abs(u) > half - .14 or v < .06 else 0) + (.8 if kind == "pale" else -1 if kind == "deep" else 0)
            return (*PINK[max(0, min(4, int(lvl)))], 255)
        return paint, (kind, big, w, h)
    return painter


# --- builder ----------------------------------------------------------------------------------------
class Builder:
    """Cubes in world space (scaled by k) under named bones; outliner() nests them by parent."""

    def __init__(self, name, k):
        self.m = Model(name, size=1024)
        self.k = k
        self.items = {}
        self.bones = []          # (name, parent, pivot)

    def K(self, v):
        return [c * self.k for c in v]

    def bone(self, name, parent, pivot):
        self.items[name] = []
        self.bones.append((name, parent, [round(v, 3) for v in self.K(pivot)]))
        return name

    def _cube(self, bone, lo, hi, paint, name, rotation=None, origin=None):
        nm = f"{name or bone}_{len(self.items[bone])}"
        self.m.cube(nm, lo, hi, self.items[bone], paint, rotation=rotation, origin=origin)

    def box(self, bone, lo, hi, mat, name=None):
        lo, hi = self.K(lo), self.K(hi)
        lo, hi = [min(a, b) for a, b in zip(lo, hi)], [max(a, b) for a, b in zip(lo, hi)]
        self._cube(bone, lo, hi, painter_of(mat), ("glow_" + (name or bone)) if is_glow(mat) else name)

    def cbox(self, bone, c, size, mat, rot=None, name=None):
        c, size = self.K(c), self.K(size)
        lo = [c[i] - size[i] / 2 for i in range(3)]
        hi = [c[i] + size[i] / 2 for i in range(3)]
        self._cube(bone, lo, hi, painter_of(mat), ("glow_" + (name or bone)) if is_glow(mat) else name,
                   rotation=list(rot) if rot else None, origin=c)

    def seg(self, bone, a, b, w, d=None, mat="lacq", z_hint=(0, 0, 1), over=0.0, name=None):
        d = d or w
        a, b = self.K(a), self.K(b)
        w, d, over = w * self.k, d * self.k, over * self.k
        L = math.dist(a, b) + over
        mid = [(p + q) / 2 for p, q in zip(a, b)]
        R = basis([q - p for p, q in zip(a, b)], z_hint)
        lo = [mid[0] - w / 2, mid[1] - L / 2, mid[2] - d / 2]
        hi = [mid[0] + w / 2, mid[1] + L / 2, mid[2] + d / 2]
        self._cube(bone, lo, hi, painter_of(mat), ("glow_" + (name or bone)) if is_glow(mat) else name,
                   rotation=matrix_euler(R), origin=mid)

    def plate(self, bone, c, normal, up, size, mat, thick=1.0, name=None):
        c, size, thick = self.K(c), self.K(size), thick * self.k
        n = norm(normal)
        u = norm(add(up, n, -dot(up, n)))
        x = cross(u, n)
        R = [[x[i], u[i], n[i]] for i in range(3)]
        lo = [c[0] - size[0] / 2, c[1] - size[1] / 2, c[2] - thick / 2]
        hi = [c[0] + size[0] / 2, c[1] + size[1] / 2, c[2] + thick / 2]
        self._cube(bone, lo, hi, painter_of(mat), name, rotation=matrix_euler(R), origin=c)

    def crest(self, bone, c, normal, size, lit=True):
        self.plate(bone, c, normal, (0, 1, 0), (size, size), crest_plate(lit), thick=.6, name="glow_crest" if lit else "crest")

    def petal(self, bone, c, normal, ang, length, width, kind="petal", up_hint=(0, 1, 0), r0=0.0):
        c, length, width, r0 = self.K(c), length * self.k, width * self.k, r0 * self.k
        n = norm(normal)
        F = basis(up_hint if abs(dot(norm(up_hint), n)) < .95 else (1, 0, 0), n)
        R = mat_mul(F, euler_matrix(0, 0, ang))
        lo = [c[0] - width / 2, c[1] + r0, c[2] - .5]
        hi = [c[0] + width / 2, c[1] + r0 + length, c[2] + .5]
        self._cube(bone, lo, hi, petal(kind), "petal", rotation=matrix_euler(R), origin=c)

    def stream(self, bone, a, b, n, size=2.4, seed=0, grow=1.2):
        """Petals pouring out of a gap along a -> b: one connected line, not scattered dots."""
        for j in range(n):
            t = j / max(1, n - 1)
            p = add(lerp(a, b, t), ((_h(j, 90 + seed) - .5) * 2.4 * t, (_h(j, 91 + seed) - .5) * 1.6 * t, (_h(j, 92 + seed) - .5) * 2.4 * t))
            self.petal(bone, p, norm((_h(j, 93 + seed) - .5, .5, 1)), j * 47 + seed * 11, size + t * grow, size + t * grow,
                       kind="pale" if j % 3 == 2 else "petal")

    def outliner(self):
        made = {}
        for name, parent, pivot in reversed(self.bones):
            kids = [made[c] for c, p, _ in self.bones if p == name]
            made[name] = self.m.bone(name, pivot, self.items[name] + kids)
        return [made[n] for n, p, _ in self.bones if p is None]


def is_glow(mat):
    return isinstance(mat, tuple)


def painter_of(mat):
    if callable(mat):
        return mat
    if isinstance(mat, tuple):
        return glow(mat)
    return solid(mat)


# --- animation ----------------------------------------------------------------------------------------
STEP = .05


def ease(kind, f):
    if kind == "in":          # accelerate into the blow
        return f ** 3
    if kind == "out":         # snap, then settle
        return 1 - (1 - f) ** 3
    if kind == "hold":
        return 0.0
    if kind == "lin":
        return f
    return f * f * (3 - 2 * f)


def blend(p, q, f):
    out = {}
    for k in set(p) | set(q):
        a, c = p.get(k, (0, 0, 0)), q.get(k, (0, 0, 0))
        out[k] = tuple(a[i] + (c[i] - a[i]) * f for i in range(3))
    return out


def merged(*poses):
    out = {}
    for p in poses:
        for k, v in p.items():
            o = out.get(k, (0, 0, 0))
            out[k] = tuple(o[i] + v[i] for i in range(3))
    return out


def follow_through(b, frames, loop, springs):
    """Hanging parts (banner, flag, shoulder boards, skirt plates, the lantern's roof) lag behind the turns of the
    bones above them and overshoot: a damped spring driven by the ancestors' angular acceleration (x and z) and the
    body's bob. Loops are run three times and the last pass kept, so they stay seamless."""
    parent = {nm: p for nm, p, _ in b.bones}
    n = len(frames)
    reps = 3 if loop else 1
    for name, (f, zeta, gain) in springs.items():
        k = (2 * math.pi * f) ** 2
        c = 2 * zeta * math.sqrt(k)
        drive = []
        for _, pose in frames:
            dx = dz = 0.0
            a = parent.get(name)
            while a is not None:
                v = pose.get(a, (0, 0, 0))
                dx += v[0]; dz += v[2]
                a = parent.get(a)
            root = [nm for nm, p, _ in b.bones if p is None]
            dx += sum(2.0 * pose.get(r + "@pos", (0, 0, 0))[1] for r in root)
            drive.append((dx, dz))
        seq = drive[:-1] * reps + [drive[-1]] if loop else drive
        acc = []
        for i in range(len(seq)):
            prv, nxt, cur = seq[max(0, i - 1)], seq[min(len(seq) - 1, i + 1)], seq[i]
            acc.append(tuple((prv[j] - 2 * cur[j] + nxt[j]) / (STEP * STEP) for j in range(2)))
        x = [0.0, 0.0]; v = [0.0, 0.0]
        out = []
        for i in range(len(seq)):
            for _ in range(10):
                for j in range(2):
                    a_ = -k * x[j] - c * v[j] - gain * acc[i][j]
                    v[j] += a_ * STEP / 10
                    x[j] += v[j] * STEP / 10
            out.append((max(-25.0, min(25.0, x[0])), max(-25.0, min(25.0, x[1]))))
        out = out[-n:]
        for i, (t, pose) in enumerate(frames):
            o = pose.get(name, (0, 0, 0))
            pose[name] = (o[0] + out[i][0], o[1], o[2] + out[i][1])


def bake(b, name, length, keys, loop="once", overlay=None, springs=None):
    """keys: [(time, pose, ease into this key)]; a pose maps bone -> (x, y, z) degrees and bone@pos -> px offset.
    Sampled every STEP into linear keyframes; rotations are written with x negated (Bedrock 4.x, see
    boss-model-pipeline.md), as the preview tools read them."""
    frames = []
    n = int(round(length / STEP))
    for i in range(n + 1):
        t = min(length, i * STEP)
        k = 0
        while k + 1 < len(keys) and keys[k + 1][0] <= t:
            k += 1
        if k + 1 < len(keys):
            t0, p0, _ = keys[k]
            t1, p1, kind = keys[k + 1]
            pose = blend(p0, p1, ease(kind, (t - t0) / max(1e-6, t1 - t0)))
        else:
            pose = dict(keys[-1][1])
        frames.append((t, merged(pose, overlay(t) if overlay else {})))
    if springs:
        follow_through(b, frames, loop == "loop", springs)
    names = {nm for nm, _, _ in b.bones}
    tracks = {}
    for t, pose in frames:
        for key, v in pose.items():
            if key.endswith("@scale"):
                bone = key[:-6]
                if bone in names:
                    tracks.setdefault(bone, []).append((t, tuple(round(1 + c, 3) for c in v), "scale"))
            elif key.endswith("@pos"):
                bone = key[:-4]
                if bone in names:
                    tracks.setdefault(bone, []).append((t, tuple(round(c * b.k, 3) for c in v), "position"))
            elif key in names:
                tracks.setdefault(key, []).append((t, (round(-v[0], 3), round(v[1], 3), round(v[2], 3))))
    b.m.anim(name, length, tracks, loop)


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


# ======================================================================================================
# 花喰いの白蛇 (white_serpent): ミニボス1
# ======================================================================================================
SERPENT_ABILITIES = {        # id: (telegraph s, recovery s) — QuestMobContent.SAKURA_MINIBOSS_1
    "serpent-breath": (1.7, .9),
    "serpent-bite": (.9, .8),
    "serpent-tail": (.7, .7),
    "serpent-coil": (1.6, 1.0),
}
SERPENT_SPRINGS = {"sn_neck2": (2.4, .35, .2), "sn_neck3": (2.2, .32, .28), "sn_neck4": (2.0, .3, .35),
                   "sn_head": (2.6, .4, .25), "sn_ruff": (2.0, .28, .4), "sn_tail": (2.2, .3, .3)}


def serpent_path():
    """The coil from the tail tip (outside, behind) spiralling in, then the neck rising forward to the head."""
    coil = []
    for k in range(16):
        t = k / 15
        a = math.radians(210 - 320 * t)
        r = 28 - 15 * t
        coil.append((r * math.cos(a) + 4, 3 + 4.5 * t, r * math.sin(a) - 8))
    rise = [(-1, 14, -14), (0, 28, -6), (-3, 44, 0), (-4, 58, 2), (1, 70, 6), (4, 78, 12)]
    return coil + rise


def build_serpent():
    b = Builder("white_serpent", 1.0)
    pts = serpent_path()
    n = len(pts)
    b.bone("sn_coil", None, (4, 3, -8))
    b.bone("sn_tail", "sn_coil", pts[5])
    b.bone("sn_neck1", "sn_coil", pts[15])
    b.bone("sn_neck2", "sn_neck1", pts[17])
    b.bone("sn_neck3", "sn_neck2", pts[18])
    b.bone("sn_neck4", "sn_neck3", pts[19])
    S = pattern("pearl", scale_fn, "scales")
    for i in range(n - 1):
        w = 3 + 13 * min(1.0, (i + 1) / 9) if i < 9 else (16 if i < 16 else 14)
        bone = "sn_tail" if i < 5 else "sn_coil" if i < 15 else "sn_neck1" if i < 17 else "sn_neck2" if i < 18 else "sn_neck3" if i < 19 else "sn_neck4"
        b.seg(bone, pts[i], pts[i + 1], w, w * .9, S, over=w * .5)
        if i >= 15:
            d = norm([q - p for p, q in zip(pts[i], pts[i + 1])])
            front = norm(cross(cross(d, (0, 0, 1)), d)) if abs(d[2]) < .95 else [0, -1, 0]
            front = [-c for c in front] if front[2] < 0 else front
            mid = lerp(pts[i], pts[i + 1], .5)
            b.seg(bone, add(mid, front, w * .42), add(add(mid, front, w * .42), d, 1), w * .6, w * .25, "pinkc", z_hint=front, over=w * .9)
    hb = pts[-1]
    hd = b.bone("sn_head", "sn_neck4", hb)
    b.seg(hd, hb, add(hb, (1, -1, 12)), 12, 8.5, S, z_hint=(0, 1, 0))
    b.seg(hd, add(hb, (1, -1, 10)), add(hb, (2, -3.5, 23)), 8.5, 5.5, S, z_hint=(0, 1, 0))
    for s in (1, -1):
        b.cbox(hd, add(hb, (4.6 * s + 1, 2.5, 14.2)), (1.4, 1.4, 2.4), (255, 70, 110), name="eye")
    jaw = b.bone("sn_jaw", hd, add(hb, (0, -5, 5)))
    b.seg(jaw, add(hb, (0, -5, 5)), add(hb, (2, -7.5, 21)), 7.5, 3, "pinkc", z_hint=(0, 1, 0))
    b.seg(jaw, add(hb, (2, -6, 21)), add(hb, (2.5, -7, 29)), .8, .5, "red", name="tongue")
    ruff = b.bone("sn_ruff", hd, add(hb, (0, 0, 1)))
    c = add(hb, (0, 0, 1))
    nrm = norm((.05, .3, 1))
    for k in range(5):
        b.petal(ruff, c, nrm, k * 72 + 36, 24, 19, r0=6)
    for k in range(5):
        b.petal(ruff, add(c, nrm, -1), nrm, k * 72, 13, 8, kind="deep", r0=5)
    ring_c = add(hb, (0, -12, -4))
    for k in range(10):
        a0, a1 = k / 10 * 2 * math.pi, (k + 1) / 10 * 2 * math.pi
        b.seg("sn_neck4", add(ring_c, (math.cos(a0) * 8, 0, math.sin(a0) * 7)), add(ring_c, (math.cos(a1) * 8, 0, math.sin(a1) * 7)), 2.6, 2.6, rope(), over=1)
    for x in (-3.5, 3.5):
        for j in range(3):
            b.box("sn_neck4", add(ring_c, (x - 1 + (j % 2) * 1.2, -2 - j * 2, 7)), add(ring_c, (x + .8 + (j % 2) * 1.2, -.4 - j * 2, 7.7)), "white", name="shide")
    b.tree = b.outliner()
    serpent_clips(b)
    return b


def scale_fn(x, y, w, hh, side, lvl):
    r = y // 3
    if y % 3 == 2 or ((x + (r % 2) * 2) % 4 == 0 and y % 3 == 1):
        return pick(RAMPS["pearl"], lvl - 1.3)
    if (x + (r % 2) * 2) % 4 == 2 and y % 3 == 0:
        return pick(RAMPS["pearl"], lvl + .8)
    return None


def rope():
    return pattern("straw", lambda x, y, w, hh, side, lvl: pick(RAMPS["straw"], 3.4 if (x + y) % 4 < 2 else 1.8), "rope")


def necks(a, b_, c, d):
    return {"sn_neck1": (a, 0, 0), "sn_neck2": (b_, 0, 0), "sn_neck3": (c, 0, 0), "sn_neck4": (d, 0, 0)}


def serpent_clips(b):
    sp = SERPENT_SPRINGS

    def sway(t):
        return {"sn_neck2": (0, 0, 2.5 * wave(t, 2.4)), "sn_neck3": (0, 0, 3 * wave(t, 2.4, .15)), "sn_neck4": (1.5 * wave(t, 2.4, .1), 0, 3 * wave(t, 2.4, .3)),
                "sn_head": (0, 8 * wave(t, 4.8), 0), "sn_ruff@scale": (.06 * wave(t, 2.4), .06 * wave(t, 2.4), 0),
                "sn_tail": (0, 10 * wave(t, 2.4, .5), 0), "sn_jaw": (max(0.0, 6 * wave(t, 1.2)) if t > 1.8 else 0, 0, 0)}
    bake(b, "idle", 2.4, [(0, {}, "lin"), (2.4, {}, "lin")], "loop", overlay=sway, springs=sp)

    def slither(t):
        return {"sn_coil": (0, 6 * wave(t, 1.6), 0), "sn_neck1": (0, 0, 4 * wave(t, 1.6, .2)), "sn_neck2": (0, 0, 6 * wave(t, 1.6, .35)),
                "sn_neck3": (0, 0, 6 * wave(t, 1.6, .5)), "sn_neck4": (0, 0, 4 * wave(t, 1.6, .65)), "sn_tail": (0, 22 * wave(t, 1.6, .5), 0),
                "sn_coil@pos": (0, .4 * abs(wave(t, .8)), 0)}
    bake(b, "walk", 1.6, [(0, {}, "lin"), (1.6, {}, "lin")], "loop", overlay=slither, springs=sp)

    # 花の吐息: rears back and up, the ruff opening wide; then the whole neck throws forward and the jaw drops as the fan
    # of petals leaves it.
    rear = merged(necks(-6, -10, -12, -10), {"sn_head": (-25, 0, 0), "sn_ruff@scale": (.35, .35, .1), "sn_coil@pos": (0, 1, -2)})
    breath = merged(necks(4, 7, 9, 8), {"sn_head": (14, 0, 0), "sn_jaw": (35, 0, 0), "sn_ruff@scale": (.45, .45, .1), "sn_coil@pos": (0, 0, 2)})
    tl, rc = SERPENT_ABILITIES["serpent-breath"]
    bake(b, "serpent-breath", tl + rc, [(0, {}, "lin"), (1.1, rear, "smooth"), (1.45, merged(rear, {"sn_head": (-6, 0, 0)}), "smooth"),
                                        (tl, breath, "in"), (tl + .35, merged(breath, {"sn_neck4": (-3, 0, 0)}), "out"), (tl + rc, {}, "smooth")], springs=sp)

    # 突き噛み: draws its neck back into an S, then strikes low and long (the server carries it 3 blocks), jaw wide.
    coil = merged(necks(-14, -16, 10, -8), {"sn_head": (-6, 0, 0), "sn_coil@pos": (0, 0, -2)})
    strike = merged(necks(12, 14, 6, 8), {"sn_head": (10, 0, 0), "sn_jaw": (38, 0, 0), "sn_coil@pos": (0, -1, 4)})
    tl, rc = SERPENT_ABILITIES["serpent-bite"]
    bake(b, "serpent-bite", tl + rc, [(0, {}, "lin"), (.55, coil, "smooth"), (tl, strike, "in"), (tl + .25, merged(strike, {"sn_jaw": (-30, 0, 0)}), "out"),
                                      (tl + rc, {}, "smooth")], springs=sp)

    # 尾払い: someone behind: the tail draws back, then whips round behind the coil as the neck glances back.
    wind = {"sn_tail": (0, -55, 0), "sn_coil": (0, -12, 0), "sn_neck3": (0, 0, -8), "sn_head": (0, -30, 0)}
    whip = {"sn_tail": (0, 95, 0), "sn_coil": (0, 18, 0), "sn_neck3": (0, 0, 8), "sn_head": (0, 30, 0)}
    tl, rc = SERPENT_ABILITIES["serpent-tail"]
    bake(b, "serpent-tail", tl + rc, [(0, {}, "lin"), (.4, wind, "smooth"), (tl, whip, "in"), (tl + .25, merged(whip, {"sn_tail": (0, -10, 0)}), "out"),
                                      (tl + rc, {}, "smooth")], springs=sp)

    # 締めの輪: rises tall and the coil swells into a wide ring, then it tightens with the head slamming down at its middle;
    # the space right by it is safe.
    tall = merged(necks(-6, -8, -8, -6), {"sn_head": (-15, 0, 0), "sn_coil@scale": (.25, 0, .25), "sn_ruff@scale": (.2, .2, 0)})
    tight = merged(necks(8, 10, 10, 8), {"sn_head": (22, 0, 0), "sn_coil@scale": (.6, -.1, .6), "sn_coil@pos": (0, -1, 0), "sn_jaw": (20, 0, 0)})
    tl, rc = SERPENT_ABILITIES["serpent-coil"]
    bake(b, "serpent-coil", tl + rc, [(0, {}, "lin"), (1.1, tall, "smooth"), (tl, tight, "in"), (tl + .3, merged(tight, {"sn_coil@scale": (-.15, 0, -.15)}), "out"),
                                      (tl + rc, {}, "smooth")], springs=sp)

    # 倒れる: the neck folds down segment by segment, the head comes to rest on the coil, the ruff closes.
    slump = merged(necks(25, 30, 35, 25), {"sn_head": (25, 0, 0), "sn_ruff@scale": (-.3, -.3, 0), "sn_jaw": (15, 0, 0)})
    bake(b, "death", 2.4, [(0, {}, "lin"), (.4, merged(necks(-8, -8, -8, -8), {"sn_head": (-20, 0, 0)}), "out"), (1.6, slump, "in"),
                           (2.4, merged(slump, {"sn_ruff@scale": (-.5, -.5, 0)}), "smooth")], springs=sp)


# ======================================================================================================
# 石灯籠の番人 (stone_lantern): ミニボス2
# ======================================================================================================
LANTERN_ABILITIES = {        # id: (telegraph s, recovery s) — QuestMobContent.SAKURA_MINIBOSS_2
    "lantern-stomp": (1.2, 1.0),
    "lantern-lob": (1.6, .8),
    "lantern-topple": (1.4, 2.6),
    "lantern-glare": (4.0, 1.2),
}
LANTERN_SPRINGS = {"sl_roof": (2.0, .3, .35), "sl_hand_r": (2.6, .35, .3), "sl_hand_l": (2.6, .35, .3)}


def build_lantern():
    b = Builder("stone_lantern", 1.2)
    M = mossy()
    for s, side in ((1, "r"), (-1, "l")):
        leg = b.bone(f"sl_leg_{side}", None, (6 * s, 6, 0))
        b.box(leg, (2 * s, 0, -6.5), (10.5 * s, 6.5, 6.5), M)
    # the body turns about the front edge of the base, so it can topple forward onto its face
    body = b.bone("sl_body", None, (0, 7, 7))
    b.box(body, (-8.5, 6, -7.5), (8.5, 10.5, 7.5), M)
    b.box(body, (-4.2, 10, -4.2), (4.2, 31, 4.2), "stone")
    for y in (16, 24):
        b.box(body, (-5.2, y, -5.2), (5.2, y + 1.8, 5.2), M)
    deck = b.bone("sl_deck", body, (0, 31, 0))
    b.box(deck, (-10.5, 30.5, -9.5), (10.5, 35.5, 9.5), M)
    for s, side in ((1, "r"), (-1, "l")):
        arm = b.bone(f"sl_arm_{side}", deck, (12 * s, 33, 0))
        b.seg(arm, (12 * s, 33, 0), (18 * s, 22, 3), 7, 6.5, M, over=2)
        hand = b.bone(f"sl_hand_{side}", arm, (18 * s, 22, 3))
        b.seg(hand, (18 * s, 22, 3), (16.5 * s, 8.5, 6), 6.5, 6, M, over=2)
        b.cbox(hand, (16.5 * s, 8.5, 6), (8.5, 7.5, 8.5), M, rot=(10 * s, 20 * s, 8), name="fist")
        b.cbox(arm, (20 * s, 23, 6), (3.5, 3.5, 3.5), "stone", rot=(30, 10, 20), name="rock")
    fire = b.bone("sl_firebox", deck, (0, 35.5, 0))
    b.box(fire, (-7.6, 35, -7.6), (7.6, 48, 7.6), lattice())
    b.box(fire, (-8.4, 35, -8.4), (8.4, 36.4, 8.4), "stone")
    for x in (-8.4, 6.8):
        for z in (-8.4, 6.8):
            b.box(fire, (x, 35, z), (x + 1.6, 48, z + 1.6), "stone")
    b.box(fire, (-3.5, 38, -3.5), (3.5, 45, 3.5), GLOW2, name="flame")
    roof = b.bone("sl_roof", fire, (0, 48, 0))
    b.box(roof, (-13.5, 48, -13.5), (13.5, 51, 13.5), M)
    b.box(roof, (-10.5, 51, -10.5), (10.5, 54, 10.5), M)
    b.box(roof, (-5.5, 54, -5.5), (5.5, 57, 5.5), M)
    for sx in (1, -1):
        for sz in (1, -1):
            c = (13.2 * sx, 50.5, 13.2 * sz)
            b.seg(roof, c, add(c, (2.5 * sx, 3.5, 2.5 * sz)), 3, 3, M)
    b.box(roof, (-3.2, 57, -3.2), (3.2, 62, 3.2), "stone")
    b.box(roof, (-2.2, 62, -2.2), (2.2, 65.5, 2.2), "stone")
    b.cbox(roof, (0, 66.5, 0), (2.4, 2.4, 2.4), "stone", rot=(45, 0, 45), name="tip")
    for k in range(7):
        a = _h(k, 71) * 2 * math.pi
        r = 4 + _h(k, 72) * 8
        b.petal(roof, (math.cos(a) * r, 54.3 if r < 10 else 51.3, math.sin(a) * r), (0, 1, 0), k * 51, 3, 3, up_hint=(0, 0, 1))
    b.tree = b.outliner()
    lantern_clips(b)
    return b


def lantern_clips(b):
    sp = LANTERN_SPRINGS

    def sway(t):
        return {"sl_body": (0, 0, 1.4 * wave(t, 2.4)), "sl_roof@pos": (0, .3 * wave(t, 1.2), 0),
                "sl_arm_r": (2 * wave(t, 2.4, .25), 0, 0), "sl_arm_l": (2 * wave(t, 2.4, .75), 0, 0)}
    bake(b, "idle", 2.4, [(0, {}, "lin"), (2.4, {}, "lin")], "loop", overlay=sway, springs=sp)

    def waddle(t):
        ph = 2 * math.pi * t / 1.6
        s = math.sin(ph)
        return {"sl_body": (3, 0, 6 * s), "sl_leg_r@pos": (0, max(0.0, -s) * 3.5, max(0.0, -s) * 2),
                "sl_leg_l@pos": (0, max(0.0, s) * 3.5, max(0.0, s) * 2), "sl_arm_r": (-14 * s, 0, 0),
                "sl_arm_l": (14 * s, 0, 0)}
    bake(b, "walk", 1.6, [(0, {}, "lin"), (1.6, {}, "lin")], "loop", overlay=waddle, springs=sp)

    # 踏み鳴らし: rocks onto its left foot, lifts the right high with both arms raised, and stamps it down.
    lift = {"sl_body": (-4, 0, 9), "sl_leg_r@pos": (0, 10, 2), "sl_arm_r": (-95, 0, 20), "sl_arm_l": (-95, 0, -20),
            "sl_hand_r": (-20, 0, 0), "sl_hand_l": (-20, 0, 0)}
    stamp = {"sl_body": (6, 0, -3), "sl_body@pos": (0, -2, 0), "sl_leg_r@pos": (0, 0, 1), "sl_arm_r": (-10, 0, 10),
             "sl_arm_l": (-10, 0, -10)}
    tl, rc = LANTERN_ABILITIES["lantern-stomp"]
    bake(b, "lantern-stomp", tl + rc, [(0, {}, "lin"), (.85, lift, "smooth"), (tl, stamp, "in"),
                                       (tl + .2, merged(stamp, {"sl_body@pos": (0, 1.2, 0)}), "out"), (tl + rc, {}, "smooth")], springs=sp)

    # 灯の弾: the roof lifts and the fire box opens, it leans back gathering the light, then flings it forward-up;
    # the ball lands on the mark at the telegraph's end.
    open_ = {"sl_roof@pos": (0, 6, -1), "sl_roof": (-15, 0, 0), "sl_body": (-12, 0, 0), "sl_arm_r": (-30, 0, 35),
             "sl_hand_r": (-50, 0, 0), "sl_arm_l": (-20, 0, -25)}
    fling = {"sl_roof@pos": (0, 4, 1), "sl_roof": (8, 0, 0), "sl_body": (14, 0, 0), "sl_arm_r": (-150, 0, 10),
             "sl_hand_r": (-10, 0, 0), "sl_arm_l": (-30, 0, -15)}
    tl, rc = LANTERN_ABILITIES["lantern-lob"]
    bake(b, "lantern-lob", tl + rc, [(0, {}, "lin"), (.8, open_, "smooth"), (1.1, fling, "in"), (tl, merged(fling, {"sl_body": (-6, 0, 0)}), "out"),
                                     (tl + rc, {}, "smooth")], springs=sp)

    # 倒れ込み: rocks back, then falls forward onto its face, lies there (the opening), and pushes itself up.
    back = {"sl_body": (-12, 0, 0), "sl_arm_r": (20, 0, 20), "sl_arm_l": (20, 0, -20)}
    down = {"sl_body": (82, 0, 0), "sl_arm_r": (-160, 0, 15), "sl_arm_l": (-160, 0, -15), "sl_roof": (10, 0, 0)}
    push = {"sl_body": (55, 0, 0), "sl_arm_r": (-100, 0, 10), "sl_hand_r": (40, 0, 0), "sl_arm_l": (-100, 0, -10), "sl_hand_l": (40, 0, 0)}
    tl, rc = LANTERN_ABILITIES["lantern-topple"]
    bake(b, "lantern-topple", tl + rc, [(0, {}, "lin"), (.7, back, "smooth"), (tl, down, "in"), (tl + .15, merged(down, {"sl_body": (-4, 0, 0)}), "out"),
                                        (tl + 1.4, down, "smooth"), (tl + 2.0, push, "smooth"), (tl + rc, {}, "smooth")], springs=sp)

    # 灯明: arms thrown wide, the roof rising and turning while the fire box fills (a tremble that grows), then the
    # flash: the roof jumps, the arms thrust out; it settles slowly.
    wide = {"sl_arm_r": (-40, 0, 70), "sl_arm_l": (-40, 0, -70), "sl_roof@pos": (0, 7, 0), "sl_body": (-6, 0, 0)}
    flash = {"sl_arm_r": (-70, 0, 95), "sl_arm_l": (-70, 0, -95), "sl_roof@pos": (0, 10, 0), "sl_body": (-14, 0, 0), "sl_body@pos": (0, 1, 0)}

    def quiver(t):
        tl_ = LANTERN_ABILITIES["lantern-glare"][0]
        out = {}
        if t <= tl_:
            f = t / tl_
            out["sl_roof"] = (0, 120 * f, 0)
            out["sl_body"] = (1.5 * f * math.sin(t * 53), 0, 1.5 * f * math.sin(t * 47 + 1))
        else:
            out["sl_roof"] = (0, 120, 0)
        return out
    tl, rc = LANTERN_ABILITIES["lantern-glare"]
    bake(b, "lantern-glare", tl + rc, [(0, {}, "lin"), (.8, wide, "smooth"), (tl - .15, merged(wide, {"sl_roof@pos": (0, 1.5, 0)}), "smooth"),
                                       (tl, flash, "in"), (tl + .3, merged(flash, {"sl_body": (6, 0, 0)}), "out"),
                                       (tl + rc, {}, "smooth")], overlay=quiver, springs=sp)

    # 倒れる: the roof slides off and drops, the fire box goes dark (its flame shrinks away), the body sinks askew.
    slip = {"sl_roof@pos": (12, -6, 4), "sl_roof": (20, 30, 55), "sl_body": (14, 0, 10), "sl_body@pos": (0, -2, 0),
            "sl_arm_r": (25, 0, 30), "sl_arm_l": (30, 0, -20)}
    rubble = {"sl_roof@pos": (20, -38, 8), "sl_roof": (35, 40, 88), "sl_body": (24, 0, 16), "sl_body@pos": (0, -6, 0),
              "sl_arm_r": (40, 0, 45), "sl_arm_l": (45, 0, -35), "sl_hand_r": (30, 0, 0), "sl_hand_l": (30, 0, 0)}
    bake(b, "death", 2.8, [(0, {}, "lin"), (.6, slip, "smooth"), (1.4, merged(slip, {"sl_roof@pos": (14, -14, 5)}), "in"),
                           (2.1, rubble, "in"), (2.8, rubble, "hold")], springs=sp)


def write(b):
    path = ROOT / "assets/bosses" / f"{b.m.name}.bbmodel"
    b.m.write(path, b.tree)
    print(path)
    return path


if __name__ == "__main__":
    which = sys.argv[1:] or ["serpent", "lantern"]
    if "serpent" in which:
        write(build_serpent())
    if "lantern" in which:
        write(build_lantern())
