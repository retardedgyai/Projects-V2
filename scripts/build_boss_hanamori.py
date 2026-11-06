"""散り桜の社's two mini-bosses (the provisional first picks; docs/development/sakura-minibosses.md).

    python scripts/build_boss_hanamori.py   # writes assets/bosses/hanamori_armor.bbmodel and stone_lantern.bbmodel

花守の鎧武者 (ミニボス1): an empty suit of armour the fallen petals live in. Black lamellar with crimson lacing, a
kabuto with gold horns and a five-petal crest, a nodachi on its shoulder, a banner with the crest on its back; petals
pour out of the gaps at the neck and the shoulder boards. 石灯籠の番人 (ミニボス2): a kasuga stone lantern that walks
on its split base and leans on two arms of stacked stone; its fire box is the face, pink light behind the lattice.

Front is +Z, +X is the model's left (WSEE shows it on the right), 16 px = 1 block. Every bone rests at rotation 0 and
its cubes are placed in world space (scaled by K), so a bone turns about its pivot (the joint). Bone names carry a
prefix (am_, sl_) so no boss-only bone list (ShadeWyrmHitbox) picks them up. Clip names are the server contract
(docs/development/boss-model-pipeline.md): idle, walk, death, countered, and one once clip per skill id, as long as
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


def lamellar(lace="crim"):
    lr = RAMPS[lace]

    def fn(x, y, w, hh, side, lvl):
        if side in ("up", "down"):
            return None
        if y % 4 == 3:
            return (*lr[2 + (1 if x % 3 == 0 else 0)], 255)
        if x % 4 == 0 and y % 4 in (1, 2):
            return (*lr[1], 255)
        return None
    return pattern("lacq", fn, "lam" + lace)


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


def crest_on(mat, scale=.84):
    """A banner: the crest in a square at the top of the big faces, a light rim."""
    ramp = RAMPS[mat]

    def painter(side, w, h):
        def paint(x, y, w_, hh):
            if side in ("north", "south"):
                m = min(w_, hh)
                cy = m / 2 if hh > w_ * 1.3 else hh / 2
                out = crest_rgba(((x + .5) - w_ / 2) / (m / 2) / scale, ((y + .5) - cy) / (m / 2) / scale)
                if out:
                    return out
                lvl = 3.4 if x in (0, w_ - 1) or y in (0, hh - 1) else 1.4 + .6 * (1 - y / max(1, hh - 1))
                return pick(ramp, lvl)
            return pick(ramp, shade_lvl(mat, x, y, hh, side))
        return paint, ("creston", mat, scale, side, w, h)
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
            lvl = 1.2 + v * 3.2 - (1.1 if abs(u) > half - .14 or v < .06 else 0) + (.8 if kind == "pale" else 0)
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

    def petal(self, bone, c, normal, ang, length, width, kind="petal", up_hint=(0, 1, 0)):
        c, length, width = self.K(c), length * self.k, width * self.k
        n = norm(normal)
        F = basis(up_hint if abs(dot(norm(up_hint), n)) < .95 else (1, 0, 0), n)
        R = mat_mul(F, euler_matrix(0, 0, ang))
        lo = [c[0] - width / 2, c[1], c[2] - .5]
        hi = [c[0] + width / 2, c[1] + length, c[2] + .5]
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
            if key.endswith("@pos"):
                bone = key[:-4]
                if bone in names:
                    tracks.setdefault(bone, []).append((t, tuple(round(c * b.k, 3) for c in v), "position"))
            elif key in names:
                tracks.setdefault(key, []).append((t, (round(-v[0], 3), round(v[1], 3), round(v[2], 3))))
    b.m.anim(name, length, tracks, loop)


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


# ======================================================================================================
# 花守の鎧武者 (hanamori_armor): ミニボス1
# ======================================================================================================
ARMOR_ABILITIES = {          # id: (telegraph s, recovery s) — QuestMobContent.SAKURA_MINIBOSS_1
    "armor-iai": (1.3, .9),
    "armor-combo": (1.5, .9),
    "armor-jodan": (2.0, 1.3),
    "armor-banner": (1.5, .9),
}
D_SWORD = norm((.35, .55, -.75))        # rest: the nodachi on the right shoulder, edge up (katsugi)
G_SWORD = (4.0, 38.0, 9.5)              # its guard
HAND_R = add(G_SWORD, D_SWORD, -2.2)
HAND_L = add(G_SWORD, D_SWORD, -9.5)
ARMOR_SPRINGS = {
    "am_banner": (2.2, .3, .25), "am_flag": (1.8, .25, .45),
    "am_sode_r": (3.0, .35, .25), "am_sode_l": (3.0, .35, .25),
    "am_kusa_f": (3.2, .4, .2), "am_kusa_b": (3.2, .4, .2), "am_kusa_r": (3.2, .4, .2), "am_kusa_l": (3.2, .4, .2),
    "am_petals": (1.6, .25, .5),
}


def build_armor():
    b = Builder("hanamori_armor", 1.3)
    L = lamellar()
    b.bone("am_body", None, (0, 26, 0))
    for s, side in ((1, "r"), (-1, "l")):
        x = 5.5 * s
        leg = b.bone(f"am_leg_{side}", "am_body", (x, 24, 0))
        b.seg(leg, (x, 13, 0), (x * .9, 24, .5), 6.5, 7, "ink")
        b.box(leg, (x - 3.6, 13, 3.6), (x + 3.6, 23, 4.8), L)
        shin = b.bone(f"am_shin_{side}", leg, (x, 13.5, .5))
        b.seg(shin, (x, 2, 1), (x, 14, 0), 5, 5, "lacq")
        b.box(shin, (x - 2.6, 0, -2), (x + 2.6, 2.4, 6), "black")
    for nm, (cx, cz, nx, nz) in (("am_kusa_f", (0, 5, 0, 1)), ("am_kusa_b", (0, -5, 0, -1)), ("am_kusa_r", (8.6, 0, 1, 0)), ("am_kusa_l", (-8.6, 0, -1, 0))):
        b.bone(nm, "am_body", (cx, 27.5, cz))
        b.seg(nm, (cx, 27.5, cz), (cx * 1.18 + nx * 1.6, 15.5, cz * 1.18 + nz * 1.6), 16.5 if nz else 10.5, 2, L, z_hint=(nx, 0, nz))
    b.box("am_body", (-8.5, 26, -5.5), (8.5, 28.4, 5.5), "crim")
    t = b.bone("am_torso", "am_body", (0, 28, 0))
    b.box(t, (-8, 27, -5), (8, 42, 5), L)
    b.box(t, (-7, 35, 5), (7, 42, 6.2), "lacq")
    b.box(t, (-7, 41.3, 5), (7, 42.2, 6.5), "gold")
    b.box(t, (-3.6, 42, -3.6), (3.6, 45, 3.6), "black")
    b.box(t, (-2.6, 42.6, 3.2), (2.6, 44.6, 3.9), GLOW2, name="neck")
    for s, side in ((1, "r"), (-1, "l")):
        sode = b.bone(f"am_sode_{side}", t, (10.5 * s, 44.5, .5))
        b.seg(sode, (10.5 * s, 44.5, .5), (13.8 * s, 30, 1.5), 11, 2.2, L, z_hint=(s, 0, 0))
        b.seg(sode, (10.5 * s, 44.5, .5), (11.2 * s, 43, .7), 11.6, 3, "gold", z_hint=(s, 0, 0))
        b.stream(sode, (13.8 * s, 29, 1.5), (15.5 * s, 16, 4), 4, size=2.2, seed=3 + s)
    # the arms hold the nodachi's grip at rest: the right hand at the guard, the left lower on the grip
    for s, side, el, hand in ((1, "r", (11.5, 35, 6), HAND_R), (-1, "l", (-6.5, 32.5, 9.5), HAND_L)):
        arm = b.bone(f"am_arm_{side}", t, (9 * s, 41, 0))
        b.seg(arm, (9 * s, 41, 0), el, 4.6, 4.6, "ink")
        fore = b.bone(f"am_fore_{side}", arm, el)
        b.seg(fore, el, hand, 4.2, 4.2, L)
        b.cbox(fore, hand, (3.6, 3.6, 3.6), "black", name="hand")
    blade = b.bone("am_blade", "am_fore_r", HAND_R)
    b.seg(blade, add(G_SWORD, D_SWORD, -13), G_SWORD, 2.3, 2.3, "crim", name="tsuka")
    b.seg(blade, add(G_SWORD, D_SWORD, -.6), add(G_SWORD, D_SWORD, .6), 6.5, 6.5, "gold", name="tsuba")
    tip = add(G_SWORD, D_SWORD, 52)
    b.seg(blade, G_SWORD, tip, 3.8, 1.0, "steel", z_hint=(1, 0, 0), name="ha")
    b.seg(blade, add(G_SWORD, D_SWORD, 3), add(tip, D_SWORD, -4), .9, 1.3, GLOW, z_hint=(1, 0, 0), name="hamon")
    hm = b.bone("am_helm", t, (0, 44, 0))
    b.box(hm, (-6, 45, -6), (6, 53, 6), "lacq")
    b.box(hm, (-.6, 45, -6.3), (.6, 53.7, 6.3), "gold")
    b.box(hm, (-8, 46, -8.2), (8, 48.6, 3.4), L)
    b.box(hm, (-9.6, 43.8, -9.8), (9.6, 46.2, 2.4), L)
    b.box(hm, (-11, 41.6, -11.4), (11, 44, 1.2), L)
    for s in (1, -1):
        b.seg(hm, (6.6 * s, 50, 3.5), (10 * s, 46.5, 5.5), 4.2, 1.2, "lacq")
        b.seg(hm, (2 * s, 52.8, 8.2), (5.5 * s, 60, 9.4), 2.3, 1.2, "gold")
        b.seg(hm, (5.5 * s, 60, 9.4), (8.5 * s, 68, 9.0), 1.8, 1.1, "gold")
    b.box(hm, (-6.4, 51, 5.6), (6.4, 52.6, 8.6), "lacq")
    b.box(hm, (-6.4, 51, 8.2), (6.4, 52.6, 8.8), "gold")
    b.box(hm, (-4.6, 44.4, 5), (4.6, 49.6, 7.2), "crim")
    b.box(hm, (-2.4, 45.6, 7.1), (2.4, 46.2, 7.4), "black")
    for s in (1, -1):
        b.cbox(hm, (2.3 * s, 50.4, 6.9), (1.8, .9, .4), GLOW, name="eye")
    b.crest(hm, (0, 59.5, 9.8), (0, 0, 1), 11.5)
    ban = b.bone("am_banner", t, (0, 30, -7))
    b.seg(ban, (0, 30, -6.6), (0, 84, -8), 1.4, 1.4, "wood")
    b.seg(ban, (0, 82.5, -8), (-13, 82.5, -8), 1.2, 1.2, "wood")
    flag = b.bone("am_flag", ban, (-.8, 82, -8.7))
    b.box(flag, (-12.6, 56, -9.1), (-.8, 82, -8.3), crest_on("crim"))
    pet = b.bone("am_petals", t, (0, 45, 0))
    b.stream(pet, (0, 45, 0), (-7, 70, -16), 10, size=2.4, seed=1)
    b.stream(pet, (3, 45, 2), (9, 64, -6), 6, size=2.2, seed=2)
    b.tree = b.outliner()           # the bones exist before the clips name them
    armor_clips(b)
    return b


def armor_clips(b):
    sp = ARMOR_SPRINGS

    def breathe(t):
        return {"am_torso": (1.2 * wave(t, 2.4), 0, 0), "am_helm": (-.8 * wave(t, 2.4, .1), 4 * wave(t, 4.8), 0),
                "am_body@pos": (0, -.3 * wave(t, 2.4), 0), "am_petals": (3 * wave(t, 2.4, .3), 0, 4 * wave(t, 4.8, .2))}
    bake(b, "idle", 2.4, [(0, {}, "lin"), (2.4, {}, "lin")], "loop", overlay=breathe, springs=sp)

    def stride(t):
        ph = 2 * math.pi * t / 1.2
        sw = math.sin(ph)
        return {"am_leg_r": (-24 * sw, 0, 0), "am_shin_r": (max(0.0, math.sin(ph + 1.2)) * 34, 0, 0),
                "am_leg_l": (24 * sw, 0, 0), "am_shin_l": (max(0.0, math.sin(ph + 1.2 + math.pi)) * 34, 0, 0),
                "am_body@pos": (0, -1.2 * abs(math.cos(ph)), 0), "am_torso": (4, 5 * sw, 0),
                "am_helm": (-2, -4 * sw, 0), "am_arm_l": (-6 * sw, 0, 0)}
    bake(b, "walk", 1.2, [(0, {}, "lin"), (1.2, {}, "lin")], "loop", overlay=stride, springs=sp)

    # 抜き打ち・花一文字: sinks into a drawing stance with the blade pulled back low at its right side, the grip
    # gathering petals, then springs (the server carries it 7 blocks) and cuts across in one stroke; holds the cut.
    crouch = {"am_body@pos": (0, -4.5, -2), "am_torso": (20, -25, 0), "am_helm": (-12, 20, 0),
              "am_leg_r": (-30, 0, 0), "am_shin_r": (38, 0, 0), "am_leg_l": (25, 0, 0), "am_shin_l": (20, 0, 0),
              "am_arm_r": (40, 0, 25), "am_fore_r": (-20, 0, 0), "am_blade": (75, 30, 0), "am_arm_l": (35, 0, -10)}
    coil = merged(crouch, {"am_torso": (4, -10, 0), "am_blade": (10, 10, 0), "am_body@pos": (0, -1, -1)})
    cut = {"am_body@pos": (0, -5, 6), "am_torso": (24, 50, 0), "am_helm": (-14, -35, 0),
           "am_leg_r": (-50, 0, 0), "am_shin_r": (30, 0, 0), "am_leg_l": (40, 0, 0), "am_shin_l": (10, 0, 0),
           "am_arm_r": (-70, -40, 60), "am_fore_r": (-10, 0, 0), "am_blade": (-40, -70, 0), "am_arm_l": (-40, 0, -40)}
    zanshin = merged(cut, {"am_torso": (-4, 6, 0), "am_body@pos": (0, .6, 0)})
    tl, rc = ARMOR_ABILITIES["armor-iai"]
    bake(b, "armor-iai", tl + rc, [
        (0, {}, "lin"), (.6, crouch, "smooth"), (1.0, coil, "smooth"), (tl, cut, "in"), (tl + .25, zanshin, "out"),
        (tl + .6, zanshin, "hold"), (tl + rc, {}, "smooth")], springs=sp)

    # 三段斬り: 袈裟 (high right to low left), 逆袈裟 (low left back up), 突き (a lunging thrust); each with its own
    # step and twist so no two blows share a pose.
    hits = (.6, 1.05, ARMOR_ABILITIES["armor-combo"][0])
    raise_ = {"am_torso": (-6, -20, 0), "am_arm_r": (-120, 0, 20), "am_fore_r": (-20, 0, 0), "am_blade": (-30, 0, 0),
              "am_arm_l": (-100, 0, -10), "am_leg_r": (-10, 0, 0), "am_leg_l": (8, 0, 0)}
    kesa = {"am_torso": (18, 35, 0), "am_body@pos": (0, -2, 3), "am_arm_r": (-20, -30, -30), "am_fore_r": (-10, 0, 0),
            "am_blade": (230, -130, 0), "am_arm_l": (-10, 0, -30), "am_leg_r": (-28, 0, 0), "am_shin_r": (20, 0, 0), "am_leg_l": (18, 0, 0)}
    gyaku = {"am_torso": (-8, -35, 0), "am_body@pos": (0, -1, 5), "am_arm_r": (-150, 20, 40), "am_fore_r": (-30, 0, 0),
             "am_blade": (-60, 40, 0), "am_arm_l": (-110, 0, 10), "am_leg_l": (-28, 0, 0), "am_shin_l": (20, 0, 0), "am_leg_r": (18, 0, 0)}
    pull = {"am_torso": (6, 25, 0), "am_body@pos": (0, -2, 2), "am_arm_r": (-30, 0, 50), "am_fore_r": (-60, 0, 0),
            "am_blade": (80, 0, 0), "am_arm_l": (-40, 0, -20), "am_leg_r": (-15, 0, 0), "am_leg_l": (15, 0, 0)}
    tsuki = {"am_torso": (26, 0, 0), "am_body@pos": (0, -3.5, 9), "am_arm_r": (-85, 0, 0), "am_fore_r": (0, 0, 0),
             "am_blade": (210, -45, 0), "am_arm_l": (-80, 0, -10), "am_leg_r": (-55, 0, 0), "am_shin_r": (25, 0, 0),
             "am_leg_l": (40, 0, 0), "am_shin_l": (15, 0, 0), "am_helm": (-14, 0, 0)}
    tl, rc = ARMOR_ABILITIES["armor-combo"]
    bake(b, "armor-combo", tl + rc, [
        (0, {}, "lin"), (.42, raise_, "smooth"), (hits[0], kesa, "in"), (.82, merged(kesa, {"am_torso": (-4, -10, 0)}), "out"),
        (hits[1], gyaku, "in"), (1.25, pull, "out"), (hits[2], tsuki, "in"), (tl + .3, merged(tsuki, {"am_body@pos": (0, .5, 0)}), "out"),
        (tl + .55, tsuki, "hold"), (tl + rc, {}, "smooth")], springs=sp)

    # 上段・桜落とし: lifts the nodachi high over its head and holds it, trembling (the wreath turns, the counter
    # window), then brings it down to the ground in front, the kneeling stance held as the crack runs out.
    jodan = {"am_torso": (-10, 0, 0), "am_body@pos": (0, 1, -1), "am_arm_r": (-165, 0, 15), "am_fore_r": (-25, 0, 0),
             "am_blade": (-125, 30, 0), "am_arm_l": (-160, 0, -15), "am_fore_l": (-25, 0, 0), "am_helm": (8, 0, 0),
             "am_leg_r": (-12, 0, 0), "am_leg_l": (10, 0, 0)}
    otoshi = {"am_torso": (38, 0, 0), "am_body@pos": (0, -6, 4), "am_arm_r": (-60, 0, 0), "am_fore_r": (0, 0, 0),
              "am_blade": (195, -30, 0), "am_arm_l": (-55, 0, 0), "am_fore_l": (0, 0, 0), "am_helm": (-20, 0, 0),
              "am_leg_r": (-45, 0, 0), "am_shin_r": (45, 0, 0), "am_leg_l": (35, 0, 0), "am_shin_l": (55, 0, 0)}

    def tremble(t):
        if 1.0 <= t < 1.95:
            a = (t - 1.0) / .95
            return {"am_arm_r": (1.6 * a * math.sin(t * 61), 0, 0), "am_arm_l": (1.6 * a * math.sin(t * 59 + 1), 0, 0),
                    "am_torso": (.8 * a * math.sin(t * 43), 0, 0)}
        return {}
    tl, rc = ARMOR_ABILITIES["armor-jodan"]
    bake(b, "armor-jodan", tl + rc, [
        (0, {}, "lin"), (.9, jodan, "smooth"), (1.85, merged(jodan, {"am_torso": (-4, 0, 0), "am_body@pos": (0, .8, 0)}), "smooth"),
        (tl, otoshi, "in"), (tl + .2, merged(otoshi, {"am_body@pos": (0, .8, 0)}), "out"), (tl + .7, otoshi, "hold"),
        (tl + rc, {}, "smooth")], overlay=tremble, springs=sp)

    # 旗の花紋: plants the nodachi in front of it, both hands on the pommel; the banner's crest swings round the pole
    # twice, faster and faster, and bursts open as it stops.
    plant = {"am_torso": (16, 0, 0), "am_body@pos": (0, -1.5, 0), "am_arm_r": (-50, 0, 20), "am_fore_r": (-40, 0, 0),
             "am_blade": (290, 40, 0), "am_arm_l": (-55, 0, -20), "am_fore_l": (-35, 0, 0), "am_leg_r": (-10, 0, 0), "am_leg_l": (10, 0, 0)}

    def spin(t):
        tl_ = ARMOR_ABILITIES["armor-banner"][0]
        if .4 <= t <= tl_:
            f = (t - .4) / (tl_ - .4)
            return {"am_flag": (0, 720 * f * f, 0), "am_helm": (-6 * f, 0, 0)}
        return {}
    tl, rc = ARMOR_ABILITIES["armor-banner"]
    bake(b, "armor-banner", tl + rc, [
        (0, {}, "lin"), (.45, plant, "smooth"), (tl - .1, merged(plant, {"am_torso": (6, 0, 0)}), "smooth"),
        (tl, merged(plant, {"am_torso": (-10, 0, 0), "am_body@pos": (0, 1, 0)}), "out"), (tl + .45, plant, "smooth"),
        (tl + rc, {}, "smooth")], overlay=spin, springs=sp)

    # カウンター: thrown out of the strike, it reels back and drops to one knee, the blade's point in the ground, then
    # rises (3.5 s, QuestEncounterCombat.MINIBOSS_COUNTER_STAGGER_MILLIS).
    reel = {"am_torso": (-26, 10, 0), "am_body@pos": (0, 0, -4), "am_helm": (-18, 15, 8), "am_arm_r": (-30, 0, 50),
            "am_blade": (30, 30, 0), "am_arm_l": (20, 0, -40), "am_leg_r": (20, 0, 0), "am_leg_l": (-10, 0, 0)}
    kneel = {"am_torso": (25, 0, 0), "am_body@pos": (0, -9, -2), "am_helm": (20, 0, 0), "am_arm_r": (-30, 0, 15),
             "am_fore_r": (-20, 0, 0), "am_blade": (95, 0, 0), "am_arm_l": (10, 0, -10),
             "am_leg_r": (-70, 0, 0), "am_shin_r": (75, 0, 0), "am_leg_l": (20, 0, 0), "am_shin_l": (95, 0, 0)}
    bake(b, "countered", 3.5, [(0, {}, "lin"), (.35, reel, "out"), (.9, kneel, "smooth"), (2.8, merged(kneel, {"am_torso": (4, 0, 0)}), "smooth"),
                               (3.5, {}, "smooth")], springs=sp)

    # 倒れる: drops to its knees, the helm falls from the empty armour and the petals pour out; the last pose stays.
    slump = merged(kneel, {"am_torso": (20, 0, 0), "am_arm_r": (40, 0, 10), "am_arm_l": (40, 0, -10), "am_blade": (60, 40, 0)})
    fallen = merged(slump, {"am_helm": (60, 30, 40), "am_helm@pos": (6, -32, 10), "am_torso": (25, 0, 0), "am_body@pos": (0, -3, 0)})
    bake(b, "death", 2.6, [(0, {}, "lin"), (.5, reel, "out"), (1.2, slump, "smooth"), (1.9, fallen, "in"), (2.6, fallen, "hold")], springs=sp)


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
    which = sys.argv[1:] or ["armor", "lantern"]
    if "armor" in which:
        write(build_armor())
    if "lantern" in which:
        write(build_lantern())
