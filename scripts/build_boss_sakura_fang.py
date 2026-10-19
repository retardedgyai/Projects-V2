"""桜牙獣 (sakura fang) Blockbench model — the second Blockbench boss.

    python scripts/build_boss_sakura_fang.py   # writes assets/bosses/sakura_fang.bbmodel

A fanged beast built low and heavy: huge forearms with the elbows back, short crouched hind legs,
a short neck carrying the head low, a pale mane over the neck and shoulders, and a thick tail curling
up over the back. The face is the sakura: notched petal shells along the back and the tail. At rest they
lie folded like shingles; when the beast enrages they open outward like a flower (`shell_*` bones).
The tail tip carries a closed bud.

Front is +Z, +X is the beast's left, 16 px = 1 block. Every bone rests at rotation 0 and its cubes are
placed in world space, so a bone turns about its pivot (the joint). Animation names are the server
contract (see docs/development/boss-model-pipeline.md); the clips here are placeholders until the shape OK.
"""
import json
import math
import sys
from pathlib import Path

from boss_models import Model, ROOT

# --- math -------------------------------------------------------------------------------------------
def unit(v):
    n = math.sqrt(sum(c * c for c in v))
    return [c / n for c in v]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def add(a, b, k=1.0):
    return [a[i] + b[i] * k for i in range(3)]


def basis(y_dir, z_hint):
    """Columns (X, Y, Z) with local Y = y_dir and local Z as close to z_hint as possible."""
    y = unit(y_dir)
    zh = list(z_hint)
    if abs(dot(unit(zh), y)) > .97:
        zh = [1, 0, 0] if abs(y[0]) < .9 else [0, 0, 1]
    z = unit(add(zh, y, -dot(zh, y)))
    x = cross(y, z)
    return [[x[i], y[i], z[i]] for i in range(3)]


def matrix_euler(m):
    """Blockbench editor convention R = Rz * Ry * Rx (degrees)."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    x = math.atan2(m[2][1], m[2][2])
    z = math.atan2(m[1][0], m[0][0])
    return [round(math.degrees(v), 3) for v in (x, y, z)]


def axis_angle(axis, deg):
    x, y, z = unit(axis)
    a = math.radians(deg)
    c, s, t = math.cos(a), math.sin(a), 1 - math.cos(a)
    return [[t * x * x + c, t * x * y - s * z, t * x * z + s * y],
            [t * x * y + s * z, t * y * y + c, t * y * z - s * x],
            [t * x * z - s * y, t * y * z + s * x, t * z * z + c]]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_vec(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


# --- texture: crisp 4-tone ramps (deep, dark, base, light), no random noise --------------------------
RAMPS = {
    "hide": ((16, 14, 22), (30, 27, 40), (50, 45, 64), (86, 78, 104)),
    "plate": ((20, 17, 26), (38, 32, 46), (62, 54, 74), (112, 100, 128)),
    "fur": ((112, 86, 102), (170, 142, 156), (218, 196, 206), (248, 238, 242)),
    "shell": ((66, 56, 70), (112, 98, 112), (160, 144, 156), (210, 198, 206)),
    "claw": ((58, 50, 56), (108, 98, 102), (168, 158, 158), (226, 220, 214)),
    "mouth": ((52, 14, 30), (86, 24, 46), (124, 40, 66), (160, 64, 90)),
}
PETAL = ((128, 26, 68), (190, 56, 104), (232, 112, 152), (247, 172, 198), (255, 226, 236))
EYE = (255, 132, 186)


def _h(*k):
    n = 0
    for v in k:
        n = (n * 1000003 + int(v) * 2654435761 + 97) & 0xffffffff
    n ^= n >> 15; n = (n * 2246822519) & 0xffffffff; n ^= n >> 13
    return (n & 0xffff) / 65535.0


SKIN = __import__("os").environ.get("SAKURA_SKIN", "fur")
# 5 tones, hue shifting from cold indigo shadows to warm mauve lights (no black outline, no noise).
HIDE5 = ((13, 11, 26), (27, 23, 44), (46, 39, 66), (74, 59, 90), (112, 86, 118))
SHELL5 = ((60, 48, 66), (98, 84, 102), (144, 128, 142), (190, 176, 186), (232, 222, 228))


def skin_painter(mat, seed=0):
    """Hide in the chosen skin: fine overlapping scales, large grooved plates, or short combed fur.
    Shells get a bevelled plate with a light upper rim and an engraved inner line."""
    ramp = SHELL5 if mat == "shell" else HIDE5
    kind = "bevel" if mat in ("shell", "plate") else SKIN

    def tone(level):
        return ramp[max(0, min(4, int(round(level))))] + (255,)

    def make(side, w, h):
        def paint(x, y, w, h):
            if side == "down":
                return tone(.6)
            v = y / max(1, h - 1)
            L = 3.0 if side == "up" else 3.3 - 2.4 * v
            if w < 3 or h < 3:
                return tone(L)
            if kind == "bevel":
                edge = min(x, y, w - 1 - x, h - 1 - y)
                if y == 0 or (x == 0 and side != "up"):
                    return tone(L + 1)
                if y == h - 1 or x == w - 1:
                    return tone(L - 1.4)
                if edge == 2 and w > 6 and h > 6:
                    return tone(L - .8)                        # engraved line
                return tone(L - .2)
            def scallop(x, y, L):
                row = y // 4
                cx, cy = (x + (row % 2) * 3) % 6, y % 4
                d = abs(cx - 2.5)
                if (cy == 3 and d <= 1.6) or (cy == 2 and d > 1.6):
                    return tone(L - 1.1)                       # the scalloped lower edge over the next row
                if cy == 0 and d < 1.2:
                    return tone(L + 1.0)                       # light catching the scale's crown
                if cy == 1 and d < 2:
                    return tone(L + .4)
                return tone(L - .1)
            if kind == "scale":
                return scallop(x, y, L)
            if kind == "plates":                                # shell band on top, scales below (two materials)
                band = max(2, int(h * .32)) if side != "up" else h
                if y < band:
                    S = SHELL5
                    lv = 3.3 - 1.2 * y / band
                    if y == 0:
                        lv += .8
                    if y == band - 1:
                        lv -= 1.6
                    if x % 9 == 0 and y < band - 1:
                        lv -= 1.0                              # shell seams
                    return S[max(0, min(4, int(round(lv - 1.9))))] + (255,)
                return scallop(x, y - band, L)
            if kind == "fur":
                clump = x // 2
                ls = 5 + int(_h(seed, clump, 9) * 3)
                ph = (y + int(_h(seed, clump, 4) * 9)) % ls
                f = ph / (ls - 1)
                lv = L + .9 - 1.7 * f                          # each strand light at its root, dark where the next one overlaps
                if x % 2 and ph == 0:
                    lv += .3
                return tone(lv)
            return tone(L)
        return paint, ("skin", kind, mat, side, w, h, seed % 9)
    return make


def box_painter(mat, seed=0):
    if SKIN != "old" and mat in ("hide", "plate", "shell"):
        return skin_painter(mat, seed)
    return old_box_painter(mat, seed)


def old_box_painter(mat, seed=0):
    """Top-lit ramp down each side, overlapping 6x4 scales where only some catch a light lip
    (the shade wyrm hand-painted look), bright top, shadowed bottom."""
    ramp = RAMPS[mat]
    scaled = mat in ("hide", "plate", "shell")

    def make(side, w, h):
        def paint(x, y, w, h):
            if w < 3 or h < 3:
                return ramp[3 if side == "up" else 1 if side == "down" else 2] + (255,)
            row = y // 4
            off = (row % 2) * 3
            cx, cy = (x + off) % 6, y % 4
            cell = _h(seed % 5, (x + off) // 6, row, len(side))
            if side == "down":
                return ramp[0 if cy == 3 else 1] + (255,)
            if side == "up":
                t = 2
                if scaled and cy == 0 and 1 <= cx <= 4 and cell > .5:
                    t = 3
                elif scaled and cy == 3 and cx in (0, 5):
                    t = 1
                return ramp[t] + (255,)
            v = y / (h - 1)
            t = 3 if y == 0 else 2 if v < .5 else 1 if v < .85 else 0
            if scaled and v < .85:
                if cy == 0 and 1 <= cx <= 4 and cell > .72:
                    t = min(3, t + 1)
                elif cy == 3 and cx in (0, 5):
                    t = max(0, t - 1)
            return ramp[t] + (255,)
        return paint, ("box", mat, side, w, h, seed % 5)
    return make


def grad_painter(t0, t1, base="shell"):
    """Box whose colour climbs from the `base` ramp into sakura pink between t0 and t1 along its length."""
    ramp = RAMPS[base]

    def make(side, w, h):
        def paint(x, y, w, h):
            along = 1 - y / max(1, h - 1) if side not in ("up", "down") else .5
            t = max(0.0, min(1.0, t0 + (t1 - t0) * along))
            k = t ** 1.3
            tone = 3 if side == "up" else 0 if side == "down" else 2 if x > 0 else 3
            c = ramp[tone]
            p = PETAL[{0: 0, 1: 1, 2: 2, 3: 3}[tone]]
            return tuple(int(c[i] + (p[i] - c[i]) * k) for i in range(3)) + (255,)
        return paint, ("grad", base, round(t0, 2), round(t1, 2), side, w, h)
    return make


GLOW = (255, 150, 196)


def flat_painter(color):
    def make(side, w, h):
        return (lambda x, y, w, h: color + (255,)), ("flat", color, w, h)
    return make


def fur_painter(seed=0, tint=False):
    """Fur plate: strand clumps (2 px) hang from the root edge (local +Y), dark at the root and pale at
    the tips, jagged ends cut to transparent. Only the two big faces (east/west) are drawn."""
    pal = RAMPS["fur"]

    def make(side, w, h):
        if side not in ("east", "west"):
            return (lambda x, y, w, h: (0, 0, 0, 0)), ("clear",)

        def paint(x, y, w, h):
            col = x if side == "east" else w - 1 - x
            clump = col // 2
            ln = h * (.6 + .4 * _h(seed, clump, 3)) - (col % 2) * 1.5
            if y >= ln:
                return (0, 0, 0, 0)
            f = y / max(1.0, ln)
            t = 0 if f < .18 else 1 if f < .5 else 2 if f < .82 else 3
            if _h(seed, clump, 7) < .3:
                t = max(0, t - 1)
            if tint and f > .66:                                   # sakura-tinted tips
                return PETAL[3 if f < .84 else 2] + (255,)
            return pal[t] + (255,)
        return paint, ("fur", seed % 9, tint, side, w, h)
    return make


# --- glow marker for scripts/shaders/item.fsh --------------------------------------------------------
# A texel glows (self-lit, with a light that flows along the body) when its alpha is 200..215 and the low bits
# of R/G/B are 1/2/3. alpha - 200 is where it sits along the flow, 0 at the shoulders to 15 at the tail tip.
def glow_mark(rgb, phase):
    q = max(0, min(15, int(round(phase * 15))))
    r, g, b_ = rgb
    return ((r & ~3) | 1, (g & ~3) | 2, (b_ & ~3) | 3, 200 + q)


def glow_painter(color, phase):
    def make(side, w, h):
        return (lambda x, y, w, h: glow_mark(color, phase)), ("glow", color, round(phase, 3), w, h)
    return make


def petal_painter(kind="shell", phase=None):
    """Sakura petal on the two big faces (north/south): narrow base, wide rounded top with the notch,
    smooth ramp from a deep base to a pale tip, a darker rim and a short centre vein. Tip = texture row 0."""
    def make(side, w, h):
        big = side in ("north", "south")

        def paint(x, y, w, h):
            if not big:
                return (0, 0, 0, 0)
            u = (x + .5) / w * 2 - 1
            v = 1 - (y + .5) / h
            half = (.22 + .78 * min(1, v / .7) ** .6) * math.sqrt(max(0, 1 - max(0, (v - .72) / .3) ** 2))
            if abs(u) > half or v > 1 - .2 * max(0, 1 - abs(u) / .3):
                return (0, 0, 0, 0)
            lvl = .9 + v * 3.4
            if kind == "bud":
                lvl -= .9
            if abs(u) > half - 2.2 / w or v < .05:
                lvl -= 1.1
            elif abs(u) < 1.0 / w and v < .45:
                lvl -= .7
            c = PETAL[max(0, min(4, int(lvl)))]
            if phase is not None:
                return glow_mark(c, min(1.0, phase + .05 * v))        # the light runs base -> tip inside a petal
            return c + (255,)
        return paint, ("petal", kind, big, w, h, None if phase is None else round(phase, 3))
    return make


# --- builder ----------------------------------------------------------------------------------------
class Builder:
    def __init__(self):
        self.m = Model("sakura_fang", size=1024)
        self.items = {}
        self.bones = []          # (name, parent, pivot) in creation order
        self.frames = {}         # shell bone -> (closed basis, open basis) for the enrage clip / preview

    def bone(self, name, parent, pivot):
        self.items[name] = []
        self.bones.append((name, parent, [round(v, 3) for v in pivot]))

    def oriented(self, bone, name, centre, half, R, paint):
        lo = [centre[i] - half[i] for i in range(3)]
        hi = [centre[i] + half[i] for i in range(3)]
        self.m.cube(name, lo, hi, self.items[bone], paint, rotation=matrix_euler(R), origin=list(centre))

    def seg(self, bone, a, b, w, d=None, mat="hide", z_hint=(0, 0, 1), over=.5, name=None, seed=0):
        d = d or w
        L = math.dist(a, b) + w * over
        R = basis([b[i] - a[i] for i in range(3)], z_hint)
        mid = [(a[i] + b[i]) / 2 for i in range(3)]
        paint = mat if callable(mat) else box_painter(mat, seed)
        self.oriented(bone, name or f"{bone}_{len(self.items[bone])}", mid, [w / 2, L / 2, d / 2], R, paint)

    def box(self, bone, lo, hi, mat, name=None, seed=0):
        paint = mat if callable(mat) else box_painter(mat, seed)
        self.m.cube(name or f"{bone}_{len(self.items[bone])}", lo, hi, self.items[bone], paint)

    def fur(self, bone, root, tip_dir, width_dir, L, W, seed, tint=False):
        """Fur plate: root edge at `root`, strands hang toward tip_dir, plate spans width_dir."""
        d = unit(tip_dir)
        R = basis([-c for c in d], width_dir)
        self.oriented(bone, f"{bone}_fur{len(self.items[bone])}", add(root, d, L / 2), [.05, L / 2, W / 2], R, fur_painter(seed, tint))

    def petal(self, bone, base, out_dir, normal, L, W, kind="shell", r0=0.0, phase=None):
        R = basis(out_dir, normal)
        y = [R[i][1] for i in range(3)]
        self.oriented(bone, f"{bone}_petal{len(self.items[bone])}", add(base, y, r0 + L / 2), [W / 2, L / 2, .5], R, petal_painter(kind, phase))

    def outliner(self):
        made = {}
        for name, parent, pivot in reversed(self.bones):
            kids = [made[c] for c, p, _ in self.bones if p == name]
            made[name] = self.m.bone(name, pivot, self.items[name] + kids)
        return [made[n] for n, p, _ in self.bones if p is None]


FUR = {}   # fur bone -> (wave phase, amplitude) for the wind sway layered on every clip


def fur_bone(b, name, parent, pivot, phase, amp=1.0):
    b.bone(name, parent, pivot)
    FUR[name] = (phase, amp)
    return name


def tufts(b, bone, root, direction, n, spread, L, W, axis, seed, tint=False):
    """n fur plates fanned around `direction`, rotating about `axis`."""
    d0, ax = unit(direction), unit(axis)
    for k in range(n):
        t = (k / (n - 1) - .5) if n > 1 else 0.0
        d = mat_vec(axis_angle(ax, spread * t), d0)
        b.fur(bone, root, d, cross(ax, d), L * (1 - .3 * abs(t) * 2), W, seed + k, tint)


# --- head: a long wedge pitched down, brow ridges, big swept-back horns, a fur beard -----------------
HEAD_O = (0, 40.5, 27)          # back of the skull
HEAD_PITCH = 20                 # degrees nose-down


def head_frame():
    a = math.radians(HEAD_PITCH)
    f = [0, -math.sin(a), math.cos(a)]
    u = [0, math.cos(a), math.sin(a)]
    return f, u


def H(x, y, z):
    """Head-local (x side, y up, z forward) to world."""
    f, u = head_frame()
    return [HEAD_O[0] + x + u[0] * y + f[0] * z, HEAD_O[1] + u[1] * y + f[1] * z, HEAD_O[2] + u[2] * y + f[2] * z]


def head(b):
    f, u = head_frame()
    hs = lambda bone, p, q, w, d, mat, **kw: b.seg(bone, H(*p), H(*q), w, d, mat, z_hint=u, **kw)
    hs("skull", (0, 1.5, -1), (0, 1, 11), 12.5, 11, "hide", seed=15)                 # cranium
    hs("skull", (0, .5, 10), (0, -.3, 17), 10, 8.5, "hide", seed=16)                  # muzzle
    hs("skull", (0, -.4, 16), (0, -1.2, 21), 8, 7, "hide", seed=19)              # nose
    hs("skull", (0, 1.4, 20.5), (0, -2.2, 23), 5.6, 3.8, "shell", seed=22)              # hooked nose tip
    hs("skull", (0, 5.8, 1), (0, 4, 20), 5.4, 2, "shell", seed=17)                # shell running down the snout
    hs("jaw", (0, -4.6, 2), (0, -6.2, 19), 9, 4.4, "hide", seed=18)             # deep lower jaw
    hs("jaw", (0, -2.6, 5), (0, -4, 18), 7, 1, "mouth")
    for s in (1, -1):
        hs("skull", (5.6 * s, 4.8, 2), (4.9 * s, 3.8, 12), 3, 3, "shell", seed=23)  # brow ridge
        hs("skull", (5.9 * s, 2.4, 7.5), (5.7 * s, 2.0, 11), 1, 1.2, glow_painter(EYE, 0.0), name="glow_eye")
        for z in (18, 14):
            hs("skull", (2.8 * s, -2.4, z), (2.8 * s, -5.4, z + .4), 1.3, 1.3, "claw")   # upper fangs
        hs("jaw", (2.5 * s, -3.6, 16), (2.5 * s, -1.4, 16.4), 1.1, 1.1, "claw")         # lower fangs
        # horns: thick roots above the eyes, sweeping back past the neck, tips curling up, one side spike
        pts = [(4.4, 5.6, 7), (7.4, 10, 0), (9.8, 14.2, -7), (11.4, 18.6, -12.5), (12, 23, -16), (11.4, 27, -18), (10.2, 29.6, -18.6)]
        ws = [4.4, 3.7, 3.0, 2.3, 1.7, 1.2]
        n = len(ws)
        # the horns are their own bones so a part break can snap the outer half off (QuestEncounterCombat.sakuraHit)
        side = "l" if s > 0 else "r"
        b.bone(f"horn_{side}", "skull", H(pts[0][0] * s, pts[0][1], pts[0][2]))
        b.bone(f"horn_{side}_tip", f"horn_{side}", H(pts[3][0] * s, pts[3][1], pts[3][2]))
        for k in range(n):
            a, c = pts[k], pts[k + 1]
            hs(f"horn_{side}" if k < 3 else f"horn_{side}_tip", (a[0] * s, a[1], a[2]), (c[0] * s, c[1], c[2]), ws[k], ws[k], grad_painter(k / n, (k + 1) / n))
        for (bx, by, bz), (tx, ty, tz), w, t, bone in (((9.6, 13.6, -6), (14.6, 15.4, -10.5), 1.5, .55, f"horn_{side}"),
                                                        ((11.6, 19.6, -13), (16, 23, -15.5), 1.2, .75, f"horn_{side}_tip"),
                                                        ((8, 11, -1.5), (11.6, 9.4, -6), 1.3, .45, f"horn_{side}")):
            hs(bone, (bx * s, by, bz), (tx * s, ty, tz), w, w, grad_painter(t, 1))
        hs("skull", (5.9 * s, 2.6, 7.2), (6.4 * s, 4.8, .5), .6, .6, glow_painter(GLOW, 0.0), name="glow_streak")   # eye streak
        # cheek fur flaring back
        cheek = fur_bone(b, f"cheekfur_{'l' if s > 0 else 'r'}", "skull", H(6 * s, -1.2, 5), .05, .8)
        tufts(b, cheek, H(6 * s, -1.2, 5), mat_vec([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [s * .55, -.25, -1]), 5, 60, 13, 4, (s, 0, 0), 80 + s)
    # beard: pale fur hanging under the jaw
    for k, z in enumerate((5, 9, 13, 17)):
        beard = fur_bone(b, f"beard_{k + 1}", "jaw", H(0, -6.6, z), .08 * k, .9)
        tufts(b, beard, H(0, -6.6, z), [-c for c in u], 4, 80, 9 - k, 3.5, f, 90 + k)


# --- the beast --------------------------------------------------------------------------------------
TAIL = [(0, 34, -30), (0, 40, -42), (0, 51, -51), (0, 64, -53), (0, 75, -48), (0, 81, -39)]
CURL = (0, 62, -38)   # centre of the tail curl
BACK = [(0, 52, 7), (0, 50, -2), (0, 45, -12), (0, 43, -21)]


def build():
    b = Builder()
    # skeleton: pivots at the joints
    b.bone("body", None, (0, 34, -10))
    b.bone("chest", "body", (0, 38, 4))
    b.bone("neck", "chest", (0, 42, 14))
    b.bone("skull", "neck", (0, 39, 27))
    b.bone("jaw", "skull", H(0, -4.6, 2))
    for s, side in ((1, "l"), (-1, "r")):
        b.bone(f"arm_{side}", "chest", (13 * s, 44, 12))
        b.bone(f"forearm_{side}", f"arm_{side}", (15 * s, 21, 6))
        b.bone(f"hand_{side}", f"forearm_{side}", (15 * s, 4, 18))
        b.bone(f"thigh_{side}", "body", (10 * s, 35, -25))
        b.bone(f"shin_{side}", f"thigh_{side}", (12 * s, 18, -16))
        b.bone(f"foot_{side}", f"shin_{side}", (12 * s, 8, -30))
    for i in range(len(TAIL) - 1):
        b.bone(f"tail_{i + 1}", "body" if i == 0 else f"tail_{i}", TAIL[i])

    # torso: high shoulders, low hips
    b.seg("chest", (0, 37, -4), (0, 41, 16), 26, 25, "hide", seed=1)
    b.seg("body", (0, 35, -2), (0, 32, -22), 18, 18, "hide", seed=2)
    b.seg("body", (0, 32, -19), (0, 34, -31), 19, 18, "hide", seed=3)
    b.seg("chest", (0, 41, 0), (0, 44, 14), 20, 8, "plate", z_hint=(0, 1, 0), seed=4)       # shoulder plate
    for z in (14, 9, 4, -1):                                                                     # chest fur
        chest = fur_bone(b, f"chestfur_{int(z) + 2}", "chest", (0, 27, z), .3 + .03 * (14 - z), .7)
        tufts(b, chest, (0, 27, z), (0, -1, -.35), 5, 70, 9, 4, (0, 0, 1), int(z) + 40)

    for s, side in ((1, "l"), (-1, "r")):
        # massive forearms: elbow back, forearm forward, wide paw
        b.seg(f"arm_{side}", (13 * s, 44, 12), (15 * s, 21, 6), 12, 13, "hide", seed=5)
        b.seg(f"forearm_{side}", (15 * s, 22, 6), (15 * s, 4, 18), 11, 12, "hide", seed=6)
        for k, (top, bot, w) in enumerate((((17.6, 22, 4), (18.4, 15, 9), 11), ((18, 17, 7.5), (18.8, 10, 12.5), 10), ((18.2, 12, 11), (18.8, 5.5, 15.5), 8.5))):
            T = (top[0] * s, top[1], top[2]); B = (bot[0] * s, bot[1], bot[2])
            b.seg(f"forearm_{side}", T, B, 2.4, w, "shell", seed=7 + k)                         # layered forearm shells
            b.seg(f"forearm_{side}", (B[0] + .9 * s, B[1] + .4, B[2] - w / 2 + .6), (B[0] + .9 * s, B[1] + .4, B[2] + w / 2 - .6), .6, .6,
                  glow_painter(GLOW, .04), name="glow_seam")
            b.seg(f"forearm_{side}", (B[0], B[1] + 1.2, B[2] + w / 2 - 1), (B[0] + 1.6 * s, B[1] - 2.6, B[2] + w / 2 + 1.2), 1.4, 1.4, grad_painter(.3, 1))  # spur
        for k, (top, bot, w) in enumerate((((17, 47, 14), (19, 37, 11), 13), ((18.6, 40, 11.5), (20, 30, 8.5), 11))):
            T = (top[0] * s, top[1], top[2]); B = (bot[0] * s, bot[1], bot[2])
            b.seg(f"arm_{side}", T, B, 2.6, w, "shell", seed=11 + k)                             # layered shoulder shells
            b.seg(f"arm_{side}", (B[0] + .9 * s, B[1] + .4, B[2] - w / 2 + .6), (B[0] + .9 * s, B[1] + .4, B[2] + w / 2 - .6), .6, .6,
                  glow_painter(GLOW, .04), name="glow_seam")
        b.box(f"hand_{side}", (15 * s - 7, 0, 12), (15 * s + 7, 5, 27), "hide", seed=9)
        for k in (-4.4, 0, 4.4):
            x = 15 * s + k
            b.seg(f"hand_{side}", (x, 3.4, 25), (x, 3, 30), 2.4, 2.4, "claw")
            b.seg(f"hand_{side}", (x, 3, 30), (x, -.2, 32.6), 1.6, 1.6, grad_painter(.2, .9, "claw"))
        elbow = fur_bone(b, f"elbowfur_{side}", f"forearm_{side}", (15 * s, 22, 3), .25, .8)
        tufts(b, elbow, (15 * s, 22, 3), (s * .25, .1, -1), 4, 40, 10, 3.5, (1, 0, 0), 60 + s)
        # short crouched hind legs
        b.seg(f"thigh_{side}", (10 * s, 35, -25), (12 * s, 18, -16), 10, 12, "hide", seed=10)
        b.seg(f"shin_{side}", (12 * s, 19, -16), (12 * s, 8, -30), 6, 6, "hide", seed=11)
        b.seg(f"foot_{side}", (12 * s, 9, -30), (12 * s, 1, -24), 5.5, 5.5, "hide", seed=12)
        b.box(f"foot_{side}", (12 * s - 4.5, 0, -27), (12 * s + 4.5, 4, -17), "hide", seed=13)
        for k in (-3, 0, 3):
            b.seg(f"foot_{side}", (12 * s + k, 2, -17.5), (12 * s + k, .2, -14), 1.6, 1.6, "claw")
        hip = fur_bone(b, f"hipfur_{side}", f"thigh_{side}", (11 * s, 33, -27), .5, .8)
        tufts(b, hip, (11 * s, 33, -27), (s * .3, .1, -1), 4, 40, 9, 3.5, (1, 0, 0), 70 + s)

    # glowing seams along the flanks and the tail sides (thin, sakura pink)
    for s in (1, -1):
        b.seg("chest", (13.3 * s, 33, 13), (13.3 * s, 30, -1), .6, .6, glow_painter(GLOW, .02), name="glow_seam")
        b.seg("chest", (13.3 * s, 38, 13), (13.3 * s, 36, 2), .6, .6, glow_painter(GLOW, .02), name="glow_seam")
        b.seg("body", (9.4 * s, 34.5, -3), (9.6 * s, 31.5, -27), .6, .6, glow_painter(GLOW, .1), name="glow_seam")
        for i in range(len(TAIL) - 1):
            w = [13, 12, 11, 9.5, 8][i]
            a, c = TAIL[i], TAIL[i + 1]
            b.seg(f"tail_{i + 1}", (a[0] + (w / 2 + .1) * s, a[1], a[2]), (c[0] + (w / 2 + .1) * s, c[1], c[2]), .6, .6, glow_painter(GLOW, .17 + .16 * i), name="glow_seam", over=0)

    # neck and head, carried low and forward
    b.seg("neck", (0, 43, 14), (0, 39, 28), 16, 16, "hide", seed=14)
    head(b)

    # mane: rows of pale fur over neck and shoulders, standing back and up
    for z0, y0, L, bone in ((31, 44, 15, "skull"), (27, 46, 19, "neck"), (22, 48, 22, "neck"), (17, 50, 24, "neck"),
                            (11, 50, 22, "chest"), (5, 48, 18, "chest"), (-1, 46, 13, "chest")):
        mane = fur_bone(b, f"mane_{z0 + 2}", bone, (0, y0 + 2, z0), .04 * (31 - z0) / 4, 1.0)
        tufts(b, mane, (0, y0 + 2, z0), (0, .75, -1), 13, 175, L * 1.3, 5, (0, 0, 1), z0, tint=True)

    # tail: thick, curling up over the back
    widths = [(13, 11), (12, 10), (11, 9), (9.5, 8), (8, 7)]
    for i in range(len(TAIL) - 1):
        bone = f"tail_{i + 1}"
        w, d = widths[i]
        b.seg(bone, TAIL[i], TAIL[i + 1], w, d, "hide", seed=20 + i)
    # sakura shells: short, broad petals overlapping like Zinogre's tail plates, pointing toward the tip.
    # One bone per run of petals and side; opening swings the run about its own spine axis.
    def splay(D, X, U, deg):
        """(axis, degrees) about the spine axis that tips the dorsal row out toward its own side."""
        R = axis_angle(D, deg)
        return (D, deg) if dot(mat_vec(R, U), X) > 0 else (D, -deg)

    def run(name, parent, a, c, X, U, sizes, open_deg, top, phases):
        D = unit([c[i] - a[i] for i in range(3)])
        b.bone(name, parent, a)
        n = len(sizes)
        for k, L in enumerate(sizes):
            f = (k + .3) / n
            p = [a[i] + (c[i] - a[i]) * f for i in range(3)]
            base = add(add(p, X, 1.4), U, top)
            out = unit(add(add(D, U, .95), X, .25))
            b.petal(name, base, out, X, L, L * .82, phase=phases[k])
            b.petal(name, add(base, X, .7), out, X, L * .58, L * .44, kind="bud", phase=phases[k] + .02)      # inner petal
        b.frames[name] = splay(D, X, U, open_deg)

    up = [0, 1, 0]
    for s, side in ((1, "l"), (-1, "r")):
        X = [s, 0, 0]
        # one dorsal row from the shoulders to the tail tip: petals point back-and-up and overlap
        run(f"shell_b_{side}", "body", BACK[0], BACK[-1], X, up, [9, 10, 11], 50, 1.0, [0.0, .05, .1])
        # tail: petals lie on the tail sides
        sizes = [[11, 12], [13, 14], [15, 16], [16, 15.5], [14, 12]]
        widths = [13, 12, 11, 9.5, 8]
        for i in range(len(TAIL) - 1):
            D = unit([TAIL[i + 1][j] - TAIL[i][j] for j in range(3)])
            mid = [(TAIL[i][j] + TAIL[i + 1][j]) / 2 for j in range(3)]
            side_dir = [CURL[j] - mid[j] for j in range(3)]
            if i > 0:
                side_dir = [-c for c in side_dir]                  # past the base the plates ride the outer edge of the curl
            U = unit(add(side_dir, D, -dot(side_dir, D)))
            run(f"shell_t{i + 1}_{side}", f"tail_{i + 1}", TAIL[i], TAIL[i + 1], X, U, sizes[i], 50, widths[i] * .35,
                [.17 + .08 * (2 * i + k) for k in range(2)])
    # the tail ends in a small closed fan
    tip, prev = TAIL[-1], TAIL[-2]
    D = unit([tip[i] - prev[i] for i in range(3)])
    for k, sx in enumerate((-.45, 0, .45)):
        b.petal("tail_5", add(tip, D, -3), unit(add(D, [1, 0, 0], sx)), [1, 0, 0] if sx == 0 else cross(D, [0, 0, 1]), 13 - 2 * abs(sx) * 2, 11, phase=.97)
    return b


# --- animation ----------------------------------------------------------------------------------------
# Poses are written in Blockbench editor euler degrees about each bone's pivot (bones rest at 0, so axes
# are world axes): +x tips a forward-pointing part down / swings a hanging limb back / lifts the tail,
# +z lifts the beast's left (+X) side, +y turns the front toward its left. Keyframes store x negated
# (Bedrock 4.x; see ShadeWyrmBossTest / boss-model-pipeline.md). "shells" is the petal opening (0 closed,
# 1 open), "tremble" a shiver amplitude in degrees, "pos" the body offset in px.
STEP = .05


def ease(kind, f):
    if kind == "in":          # accelerate into the strike
        return f ** 3
    if kind == "out":         # snap then settle
        return 1 - (1 - f) ** 3
    if kind == "hold":
        return 0.0
    if kind == "lin":
        return f
    return f * f * (3 - 2 * f)


def blend(p, q, f):
    out = {}
    for k in set(p) | set(q):
        a, c = p.get(k, 0 if k in ("shells", "tremble") else (0, 0, 0)), q.get(k, 0 if k in ("shells", "tremble") else (0, 0, 0))
        out[k] = a + (c - a) * f if isinstance(a, (int, float)) else tuple(a[i] + (c[i] - a[i]) * f for i in range(3))
    return out


def merged(*poses):
    out = {}
    for p in poses:
        for k, v in p.items():
            if k in ("shells", "tremble"):
                out[k] = v
            else:
                o = out.get(k, (0, 0, 0))
                out[k] = tuple(o[i] + v[i] for i in range(3))
    return out


def spring_params(name):
    """(frequency Hz, damping ratio, gain) of the lag a bone gets behind its ancestors, or None."""
    if name.startswith("tail_"):
        k = int(name[5:])
        return 2.4 - .2 * k, .35, .35 + .12 * k
    if name == "neck":
        return 2.6, .45, .25
    if name == "skull":
        return 3.0, .45, .3
    if name == "jaw":
        return 4.0, .4, .2
    if name.startswith(("mane_", "cheekfur_", "beard_", "chestfur_", "elbowfur_", "hipfur_")):
        return 2.2, .3, .6
    return None


def follow_through(b, frames, loop):
    """Secondary motion: chain bones (tail, neck, head, fur) lag behind the turns of their ancestors and overshoot
    (a damped spring driven by the ancestors' angular acceleration, x and z). Loops are run three times and the last
    pass kept, so they stay seamless."""
    parent = {nm: p for nm, p, _ in b.bones}
    n = len(frames)
    reps = 3 if loop else 1
    dt = STEP
    for name in [nm for nm, _, _ in b.bones]:
        sp = spring_params(name)
        if sp is None:
            continue
        f, zeta, gain = sp
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
            dx += 2.0 * pose.get("pos", (0, 0, 0))[1]
            drive.append((dx, dz))
        seq = drive[:-1] * reps + [drive[-1]] if loop else drive
        acc = []
        for i in range(len(seq)):
            prv, nxt = seq[max(0, i - 1)], seq[min(len(seq) - 1, i + 1)]
            cur = seq[i]
            acc.append(tuple((prv[j] - 2 * cur[j] + nxt[j]) / (dt * dt) for j in range(2)))
        x = [0.0, 0.0]; v = [0.0, 0.0]
        out = []
        sub = 10
        for i in range(len(seq)):
            for _ in range(sub):
                for j in range(2):
                    a_ = -k * x[j] - c * v[j] - gain * acc[i][j]
                    v[j] += a_ * dt / sub
                    x[j] += v[j] * dt / sub
            out.append((max(-22.0, min(22.0, x[0])), max(-22.0, min(22.0, x[1]))))
        out = out[-n:]
        for i, (t, pose) in enumerate(frames):
            o = pose.get(name, (0, 0, 0))
            pose[name] = (o[0] + out[i][0], o[1], o[2] + out[i][1])


def bake(b, name, length, keys, loop="once", base=None, overlay=None):
    """keys: [(time, pose, ease_into_this_key)]. Samples every STEP and writes linear keyframes."""
    base = base or {}
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
            pose = keys[-1][1]
        layers = [base, pose, wind(t, pose.get("shells", 0.0))]
        if overlay:
            layers.append(overlay(t))
        frames.append((t, merged(*layers)))
    follow_through(b, frames, loop == "loop")
    bones = {nm for nm, _, _ in b.bones}
    tracks = {}
    opening = [pose.get("shells", 0.0) for _, pose in frames]
    n_frames = len(frames)

    def ripple(i, shell):
        """Petal opening seen by one shell: it follows the shoulders' a little later the further down the tail it
        sits, so every opening and closing travels shoulders -> tail tip as a wave."""
        order = 0 if "_b_" in shell else int(shell.split("_t")[1].split("_")[0])
        j = i - order * RIPPLE_FRAMES
        if loop == "loop":
            j %= max(1, n_frames - 1)
        return opening[max(0, j)]
    for i, (t, pose) in enumerate(frames):
        shells = pose.get("shells", 0.0)
        tremble = pose.get("tremble", 0.0)
        for bone, v in pose.items():
            if bone in ("shells", "tremble"):
                continue
            if bone == "pos":
                tracks.setdefault("body", []).append((t, tuple(round(c, 3) for c in v), "position"))
                continue
            if bone in bones:
                tracks.setdefault(bone, []).append((t, (round(-v[0], 3), round(v[1], 3), round(v[2], 3))))
        for k, (shell, (axis, deg)) in enumerate(sorted(b.frames.items())):
            jitter = tremble * math.sin(t * 47 + k * 1.7)
            e = matrix_euler(axis_angle(axis, deg * ripple(i, shell) + math.copysign(jitter, deg)))
            tracks.setdefault(shell, []).append((t, (round(-e[0], 3), round(e[1], 3), round(e[2], 3))))
    b.m.anim(name, length, tracks, loop)


RIPPLE_FRAMES = 1   # each tail segment opens one 0.05 s frame after the one before it

WIND_PERIOD = 1.2   # divides every loop length (idle 2.4, walk 1.2)


def wind(t, shells=0.0):
    """Fur sway: a wave that runs from the head back along the mane (x: strands lift and fall, z: a side
    swing a quarter beat later). It bristles harder while the petals are open."""
    gain = 1.0 + .6 * min(1.0, shells)
    out = {}
    for name, (phase, amp) in FUR.items():
        a = 2 * math.pi * (t / WIND_PERIOD - phase)
        out[name] = (5.0 * amp * gain * math.sin(a), 0.0, 3.0 * amp * gain * math.sin(a - 1.3))
    return out


def wave(t, period, phase=0.0):
    return math.sin((t / period + phase) * 2 * math.pi)


def looped(length, fn):
    """Loop keys sampled from fn(t) every 0.1 s (smoothstep between them)."""
    ts = [round(i * .1, 3) for i in range(int(round(length / .1)) + 1)]
    return [(t, fn(t), "io") for t in ts]


# --- Zinogre / Inagami-style motion ---------------------------------------------------------------------------
# Rules taken from how those beasts move: the whole body moves and never freezes (holds keep drifting and shivering);
# weight goes the other way first (anticipation), the hips lead and chest, neck, head and tail arrive after them
# (the springs in follow_through add the lag); rear-ups pivot on planted hind feet; strikes crash the whole body
# down and bounce; recoveries settle with an overshoot instead of easing into a pose.
HIP = (0.0, 35.0, -25.0)          # hind-leg joint, the pivot of a rear-up
BODY_PIVOT = (0.0, 34.0, -10.0)


def rear(a):
    """Body pitch a (editor degrees, negative = nose up) about the body pivot, with the body offset and hind-leg
    counter-rotation that keep the hips over the planted hind feet."""
    r = math.radians(a)
    dy, dz = HIP[1] - BODY_PIVOT[1], HIP[2] - BODY_PIVOT[2]
    ny = dy * math.cos(r) - dz * math.sin(r)
    nz = dy * math.sin(r) + dz * math.cos(r)
    return {"body": (a, 0, 0), "pos": (0, dy - ny, dz - nz), "thigh_l": (-a, 0, 0), "thigh_r": (-a, 0, 0)}


def mie(shells, side=1):
    """見得: the closing flourish of a big move (kabuki's held pose): chest out, head thrown up, one forepaw lifted,
    the tail arched high and every petal flared and quivering, held a moment before it relaxes."""
    paw = "arm_l" if side > 0 else "arm_r"
    fore = "forearm_l" if side > 0 else "forearm_r"
    return pose(rear(-10), chest=(-8, 6 * side, 0), neck=(-24, 8 * side, 0), skull=(-14, 6 * side, 4 * side), jaw=(12, 0, 0),
                **{paw: (-28, 0, 0), fore: (-36, 0, 0)}, tail_1=(20, 0, 0), tail_2=(14, 0, 0), tail_3=(6, 0, 0),
                shells=max(shells, 1.35), tremble=2.5)


def sit_howl(open_):
    """Zinogre's howl: it sits back on its haunches like a wolf, forelegs straight, chest out and the head thrown
    straight up at the sky with the jaw wide."""
    a = -30
    # rear(a) already turns the hind legs back upright; on top of that the knees go forward and the hocks fold under
    return pose(rear(a), pos=(0, -14, -4), thigh_l=(-45, 0, 0), thigh_r=(-45, 0, 0), shin_l=(85, 0, 0), shin_r=(85, 0, 0),
                foot_l=(-40, 0, 0), foot_r=(-40, 0, 0), arm_l=(-a + 4, 0, 0), arm_r=(-a + 4, 0, 0), forearm_l=(-4, 0, 0), forearm_r=(-4, 0, 0),
                hand_l=(2, 0, 0), hand_r=(2, 0, 0), chest=(-6, 0, 0), neck=(-56, 0, 0), skull=(-30, 0, 0), jaw=(46, 0, 0),
                tail_1=(22, 0, 0), tail_2=(12, 0, 0), shells=open_, tremble=3.0)


def pose(*parts, **extra):
    out = merged(*parts)
    for k, v in extra.items():
        out[k] = v if not isinstance(v, tuple) else tuple(out.get(k, (0, 0, 0))[i] + v[i] for i in range(3))
    return out


def idle_pose(t, L=2.4, enraged=False):
    """Crouched and alive: a slow breath rolls through the shoulders, the head sweeps as if scenting, the weight
    shifts from side to side and the raised tail sways in an S."""
    w = wave(t, L)
    look = wave(t, L, .1)
    sway = wave(t, L, .3)
    p = {"chest": (2.0 * w, 1.5 * sway, 1.2 * sway), "body": (-.8 * w, -1.0 * sway, -1.0 * sway),
         "neck": (-2.5 * w + 1.5 * wave(t, L / 2), 9.0 * look, 0), "skull": (2.0 * wave(t, L, -.15), 4.0 * look, 2.0 * look),
         "jaw": (1.5 + 1.5 * max(0.0, wave(t, L / 2, .2)), 0, 0), "pos": (.6 * sway, -.8 + .8 * w, 0),
         "arm_l": (1.5 * sway, 0, 0), "arm_r": (-1.5 * sway, 0, 0)}
    for k in range(1, 6):                         # the tail draws a slow figure eight, the tip trailing
        p[f"tail_{k}"] = (4.0 * wave(t, L / 2, -.08 * k), 9.0 * wave(t, L, .15 - .12 * k), 2.5 * wave(t, L, -.1 * k))
    p["neck"] = tuple(p["neck"][i] + (-6, 0, 0)[i] for i in range(3))   # head carried a little higher: proud, not slumped
    if enraged:                                   # lower, wider, snarling; the petals stay open and shiver
        p = pose(p, chest=(4, 0, 0), neck=(8, 0, 0), skull=(4, 0, 0), jaw=(8 + 3 * w, 0, 0), pos=(0, -2, 0))
        p["shells"] = 1.0
        p["tremble"] = 2.0
    return p


def walk_pose(t, L=1.2, enraged=False):
    """A wolf's prowl: diagonal gait, the shoulder blades roll up over each planted forefoot, the head dips on
    every step and the body rolls; the tail swings against the hips."""
    ph = t / L * 2 * math.pi
    s1, c1 = math.sin(ph), math.cos(ph)
    p = {"pos": (0, -1.6 * abs(math.sin(ph)) + .4, 0), "chest": (2.5 * math.cos(2 * ph), 3 * s1, 4 * s1),
         "body": (1.2 * math.cos(2 * ph + .6), -2.5 * s1, -3 * s1), "neck": (4 * math.sin(2 * ph + .9), -3 * s1, 0),
         "skull": (-3 * math.sin(2 * ph + 1.4), -2 * s1, 0)}
    for side, ph2 in (("l", ph), ("r", ph + math.pi)):
        s, c = math.sin(ph2), math.cos(ph2)
        lift = max(0.0, c)
        p[f"arm_{side}"] = (-20 * s, 0, (3 if side == "l" else -3) * lift)
        p[f"forearm_{side}"] = (-30 * lift, 0, 0)
        p[f"hand_{side}"] = (22 * lift - 8 * s, 0, 0)
    for side, ph2 in (("l", ph + math.pi), ("r", ph)):          # diagonal pairs
        s, c = math.sin(ph2), math.cos(ph2)
        lift = max(0.0, c)
        p[f"thigh_{side}"] = (-18 * s, 0, 0)
        p[f"shin_{side}"] = (16 * lift, 0, 0)
        p[f"foot_{side}"] = (-14 * lift + 5 * s, 0, 0)
    for k in range(1, 6):
        p[f"tail_{k}"] = (3 * math.sin(2 * ph - .5 * k) + (4 if k == 1 else 0), 10 * math.sin(ph + math.pi - .45 * k), 0)
    p["neck"] = tuple(p["neck"][i] + (-8, 0, 0)[i] for i in range(3))   # a proud carriage, head high
    if enraged:
        p = pose(p, neck=(8, 0, 0), chest=(3, 0, 0))
        p["shells"] = 1.0
        p["tremble"] = 1.5
    return p


ABILITIES = {"sakura-claw": (1.2, 1.0), "sakura-tail": (1.5, 1.1), "sakura-scatter": (1.7, .9), "sakura-bloom": (2.0, 1.2),
             "sakura-horn": (.8, .7), "sakura-spin": (1.0, .9), "sakura-howl": (1.6, 1.0),
             "sakura-claw2": (.6, .7), "sakura-claw3": (.9, 1.3),
             "sakura-pounce": (1.1, .9), "sakura-rain": (1.8, .8), "sakura-charge": (1.0, 1.2),
             # small, quick blows thrown on the move between the big ones (Zinogre attacks while it travels)
             "sakura-lunge": (.55, .55), "sakura-barge": (.65, .65), "sakura-hopswipe": (.6, .6)}
# Only after the enrage: no phase-one twin is needed (the clip itself is authored with the petals open).
ENRAGED_ONLY = {"sakura-bloom", "sakura-howl", "sakura-claw3", "sakura-charge"}
CALM = {}


def claw_keys(hit, end, shells):
    """桜爪の叩きつけ (Zinogre's paw slam): sink back on the haunches, rear up on the hind legs with the right forearm
    raised over the head, hang and shiver at the top, then throw the whole body down onto the paw; the impact bounces
    the body, the left paw slaps down after it, and the beast settles with a snort."""
    s = {"shells": shells}
    sink = pose(rear(8), chest=(6, 0, 0), neck=(10, 0, 0), skull=(6, 0, 0), arm_r=(10, 0, 0), arm_l=(8, 0, 0),
                tail_1=(-8, 0, 0), pos=(0, -2, -1), **s)
    up = pose(rear(-42), chest=(-8, -10, -6), neck=(-18, -8, 0), skull=(-10, -6, 0), jaw=(18, 0, 0),
              arm_r=(-120, 0, -10), forearm_r=(-40, 0, 0), hand_r=(-30, 0, 0), arm_l=(-50, 0, 6), forearm_l=(-30, 0, 0),
              tail_1=(16, 0, 0), tail_2=(10, 0, 0), **s)
    top = pose(up, rear(-4), arm_r=(-8, 0, 0), tremble=3.0, shells=shells + .1)
    slam = pose(rear(18), chest=(18, 6, 4), neck=(24, 4, 0), skull=(12, 0, 0), jaw=(28, 0, 0), arm_r=(16, 0, 4),
                forearm_r=(4, 0, 0), hand_r=(-8, 0, 0), arm_l=(-30, 0, 0), forearm_l=(-20, 0, 0),
                tail_1=(-14, 0, 0), tail_2=(-8, 0, 0), pos=(0, -3, 4), shells=shells + .3, tremble=2.5)
    bounce = pose(rear(4), chest=(4, 4, 2), neck=(6, 0, 0), jaw=(14, 0, 0), arm_r=(8, 0, 0), arm_l=(10, 0, 0),
                  pos=(0, -1, 3), **s)
    settle = pose(chest=(-2, 0, 0), neck=(-4, 0, 0), skull=(-3, 0, 0), pos=(0, 0, 1), **s)
    step_back = pose(rear(-6), pos=(0, 1, -2), arm_r=(-24, 0, 0), forearm_r=(-20, 0, 0), neck=(-8, 0, 0), jaw=(10, 0, 0), **s)
    return [(0, dict(s), "io"), (.3, sink, "io"), (hit * .72, up, "out"), (hit - .06, top, "io"), (hit, slam, "in"),
            (hit + .14, bounce, "out"), (hit + .32, pose(bounce, arm_l=(14, 0, 0)), "io"), (hit + .5, step_back, "io"),
            (hit + .7, mie(shells, -1), "out"), (hit + .86, pose(mie(shells, -1), skull=(-4, 0, 0)), "io"), (end, dict(s), "io")]


def tail_keys(hit, end, shells):
    """花尾の宙返り (Zinogre's somersault): crouch and coil, spring up and flip forward so the petal tail comes over
    the top and crashes down ahead, then land on all fours with a stagger."""
    s = {"shells": shells}
    crouch = pose(rear(10), pos=(0, -4, -2), chest=(10, 0, 0), neck=(16, 0, 0), skull=(8, 0, 0),
                  arm_l=(-14, 0, 0), arm_r=(-14, 0, 0), forearm_l=(20, 0, 0), forearm_r=(20, 0, 0),
                  shin_l=(20, 0, 0), shin_r=(20, 0, 0), tail_1=(-24, 0, 0), tail_2=(-12, 0, 0), tremble=1.5, **s)
    takeoff = hit - .38
    coil = pose(crouch, rear(6), pos=(0, -6, -4), tail_1=(-34, 0, 0), tail_2=(-18, 0, 0), neck=(22, 0, 0), tremble=3.0)
    frames = [(0, dict(s), "io"), (.3, pose(rear(-6), pos=(0, 1, 1), neck=(-6, 0, 0), **s), "io"), (.6, crouch, "io"),
              (takeoff - .05, coil, "io")]
    # the flip: body pitch 0 -> 360 (nose down first) on a height arc; the tail is over the front at the strike
    flip = []
    t_land = hit + .22
    for k in range(9):
        u = k / 8                                     # 0 takeoff .. 1 landing
        tt = takeoff + (t_land - takeoff) * u
        ang = 360 * (u ** 1.15)
        height = 34 * math.sin(math.pi * u)
        tuck = math.sin(math.pi * u)
        flip.append((tt, pose(body=(ang, 0, 0), pos=(0, height, 4 * u), neck=(20 * tuck, 0, 0), skull=(10 * tuck, 0, 0),
                              arm_l=(-50 * tuck, 0, 0), arm_r=(-50 * tuck, 0, 0), thigh_l=(30 * tuck, 0, 0), thigh_r=(30 * tuck, 0, 0),
                              shin_l=(20 * tuck, 0, 0), shin_r=(20 * tuck, 0, 0),
                              tail_1=(-10 * tuck, 0, 0), tail_2=(-6 * tuck, 0, 0), shells=shells + .4 * tuck), "lin" if k else "in"))
    land = pose(rear(10), pos=(0, -4, 4), chest=(10, 0, 0), neck=(12, 0, 0), jaw=(16, 0, 0), arm_l=(10, 0, 0), arm_r=(10, 0, 0),
                shin_l=(16, 0, 0), shin_r=(16, 0, 0), tail_1=(-10, 0, 0), tremble=2.0, **s)
    stagger = pose(rear(-4), pos=(0, 0, 3), chest=(-2, 4, 4), neck=(-6, 6, 0), **s)
    return frames + flip + [(t_land + .12, land, "out"), (t_land + .3, stagger, "io"), (t_land + .5, mie(shells), "out"),
                            (t_land + .64, pose(mie(shells), neck=(4, 0, 0)), "io"), (end, dict(s), "io")]


def scatter_keys(hit, end, shells):
    """散華: plant the feet, sink and let the petals flare and shake (a building snarl), draw back once more, then
    a full-body thrust: forelegs brace, chest drives forward, head snaps up and the tail whips over, flinging them."""
    gather = pose(rear(6), pos=(0, -3, -2), chest=(6, 0, 0), neck=(14, 0, 0), skull=(8, 0, 0), jaw=(10, 0, 0),
                  arm_l=(-8, 0, -6), arm_r=(-8, 0, 6), tail_1=(20, 0, 0), tail_2=(12, 0, 0), shells=1.25, tremble=4.0)
    draw = pose(gather, rear(4), pos=(0, -4, -5), neck=(20, 0, 0), shells=1.4, tremble=6.0)
    fling = pose(rear(-12), pos=(0, 1, 6), chest=(-6, 0, 0), neck=(-14, 0, 0), skull=(-18, 0, 0), jaw=(38, 0, 0),
                 arm_l=(-22, 0, -8), arm_r=(-22, 0, 8), forearm_l=(10, 0, 0), forearm_r=(10, 0, 0),
                 tail_1=(40, 0, 0), tail_2=(26, 0, 0), tail_3=(16, 0, 0), shells=.35, tremble=0)
    after = pose(rear(4), pos=(0, -1, 4), neck=(6, 0, 0), skull=(-4, 0, 0), jaw=(16, 0, 0), tail_1=(8, 0, 0), shells=shells)
    return [(0, {"shells": shells}, "io"), (hit * .75, gather, "io"), (hit - .12, draw, "io"), (hit, fling, "in"),
            (hit + .22, after, "out"), (hit + .5, mie(shells), "out"), (hit + .68, pose(mie(shells), skull=(-4, 0, 0)), "io"),
            (end, {"shells": shells}, "io")]


def bloom_keys(hit, end, shells):
    """花嵐 (enraged only): rear straight up on the hind legs, forelegs clawing the air, a howl to the sky with every
    petal flared and shaking, then crash down on both forepaws; the body bounces twice before it settles."""
    rise = pose(rear(-62), arm_l=(-80, 0, 10), arm_r=(-70, 0, -10), forearm_l=(-40, 0, 0), forearm_r=(-50, 0, 0),
                neck=(-24, 0, 0), skull=(-22, 0, 0), jaw=(40, 0, 0), tail_1=(-6, 0, 0), shells=1.3, tremble=3.0)
    claw = pose(rise, arm_l=(-60, 0, 10), arm_r=(-95, 0, -10), skull=(-26, 6, 0), shells=1.4, tremble=6.0)
    crash = pose(rear(16), pos=(0, -4, 4), arm_l=(14, 0, 0), arm_r=(14, 0, 0), chest=(8, 0, 0), neck=(16, 0, 0), skull=(10, 0, 0),
                 jaw=(30, 0, 0), tail_1=(-14, 0, 0), tail_2=(-10, 0, 0), shells=.6, tremble=3.0)
    bounce = pose(rear(2), pos=(0, 0, 3), neck=(4, 0, 0), jaw=(16, 0, 0), shells=1.1)
    return [(0, {"shells": shells}, "io"), (.25, pose(rear(6), pos=(0, -2, -1), neck=(10, 0, 0), shells=shells), "io"),
            (hit * .6, rise, "out"), (hit - .14, claw, "io"), (hit, crash, "in"), (hit + .16, bounce, "out"),
            (hit + .34, pose(rear(8), pos=(0, -2, 3), shells=1.0), "in"), (hit + .55, pose(bounce, shells=1.0), "out"),
            (hit + .8, mie(1.0, -1), "out"), (hit + 1.0, pose(mie(1.0, -1), skull=(-4, 0, 0)), "io"), (end, {"shells": shells}, "io")]


def horn_keys(hit, end, shells):
    """枝角の突き上げ: dip the head low with the horns levelled, then a short lunge that throws the head up, horns
    raking the air; it shakes the head as it backs off."""
    s = {"shells": shells}
    dip = pose(rear(8), pos=(0, -3, -3), chest=(8, 0, 0), neck=(26, 0, 0), skull=(20, 0, 0), jaw=(4, 0, 0),
               arm_l=(-10, 0, 0), arm_r=(-10, 0, 0), tail_1=(10, 0, 0), tremble=1.5, **s)
    thrust = pose(rear(-14), pos=(0, 2, 8), chest=(-8, 0, 0), neck=(-24, 0, 0), skull=(-34, 0, 0), jaw=(14, 0, 0),
                  arm_l=(14, 0, 0), arm_r=(-20, 0, 0), tail_1=(-10, 0, 0), shells=shells + .2)
    return [(0, dict(s), "io"), (hit * .7, dip, "io"), (hit - .05, pose(dip, pos=(0, -4, -4)), "io"), (hit, thrust, "in"),
            (hit + .25, pose(thrust, skull=(-10, 14, 0), pos=(0, 0, 6)), "out"), (end, dict(s), "io")]


def spin_keys(hit, end, shells):
    """花尾の旋回 (Zinogre's 360 tail swipe): wind the body the other way, then spin on the spot with the petal tail
    stretched out flat, the tail passing the front at the strike; it skids out of the turn and settles."""
    s = {"shells": shells}
    wind = pose(rear(6), pos=(0, -3, 0), body=(0, 28, 0), chest=(0, 10, 0), neck=(10, 20, 0), tail_1=(-12, -20, 0),
                tail_2=(-8, -16, 0), tremble=1.5, **s)
    start, stop = hit - .3, hit + .25
    keys = [(0, dict(s), "io"), (start - .02, wind, "io")]
    for k in range(7):
        u = k / 6
        yaw = 28 - (360 + 28) * u
        flat = math.sin(math.pi * u)
        keys.append((start + (stop - start) * u, pose(body=(0, yaw, 0), pos=(0, 3 * flat, 0), chest=(0, -10 * flat, 0),
                                                      neck=(0, -14 * flat, 0), tail_1=(-16 * flat, 0, 0), tail_2=(-14 * flat, 0, 0),
                                                      tail_3=(-12 * flat, 0, 0), tail_4=(-10 * flat, 0, 0),
                                                      shells=shells + .35 * flat), "lin" if k else "in"))
    skid = pose(rear(8), body=(0, -14, 0), pos=(0, -3, 0), neck=(6, 10, 0), tail_2=(0, 18, 0), **s)
    return keys + [(stop + .18, skid, "out"), (stop + .4, pose(mie(shells), body=(0, 4, 0)), "io"), (end, dict(s), "io")]


def howl_attack_keys(hit, end, shells):
    """散り際の遠吠え (enraged only): it sits back and howls; at the strike every petal bursts open in a wave and the
    blast of petals throws everything around it back."""
    sit = sit_howl(1.0)
    return [(0, {"shells": 1.0}, "io"), (.45, pose(sit, jaw=(-30, 0, 0), neck=(30, 0, 0), shells=1.0), "out"),
            (hit - .2, pose(sit, jaw=(-30, 0, 0), neck=(36, 0, 0), shells=.6, tremble=6.0), "io"),
            (hit, pose(sit, shells=1.55, tremble=5.0), "in"), (hit + .45, pose(sit, shells=1.3, skull=(-8, 0, 0)), "io"),
            (end, {"shells": 1.0}, "io")]


def claw2_keys(hit, end, shells):
    """三連桜爪, second blow: no settling after the first slam, the left paw comes straight up and down."""
    s = {"shells": shells}
    up = pose(rear(-24), arm_l=(-100, 0, 8), forearm_l=(-36, 0, 0), hand_l=(-24, 0, 0), arm_r=(10, 0, 0),
              chest=(-6, 8, 4), neck=(-10, 8, 0), jaw=(16, 0, 0), tremble=2.0, shells=shells + .1)
    slam = pose(rear(16), pos=(0, -3, 3), arm_l=(16, 0, -4), chest=(16, -6, -4), neck=(20, -4, 0), skull=(10, 0, 0),
                jaw=(26, 0, 0), tail_1=(-12, 0, 0), shells=shells + .3, tremble=2.5)
    return [(0, pose(rear(10), pos=(0, -2, 3), **s), "io"), (hit * .75, up, "out"), (hit, slam, "in"),
            (hit + .16, pose(rear(4), pos=(0, -1, 3), **s), "out"), (end, pose(rear(4), **s), "io")]


def claw3_keys(hit, end, shells):
    """三連桜爪, the last blow (enraged only): rear up high with both forepaws raised, crash down with both, the petals
    flare in a wave and it holds the 見得."""
    up = pose(rear(-52), arm_l=(-110, 0, 10), arm_r=(-110, 0, -10), forearm_l=(-40, 0, 0), forearm_r=(-40, 0, 0),
              neck=(-24, 0, 0), skull=(-20, 0, 0), jaw=(36, 0, 0), tail_1=(18, 0, 0), shells=1.3, tremble=4.0)
    slam = pose(rear(20), pos=(0, -5, 5), arm_l=(18, 0, 0), arm_r=(18, 0, 0), chest=(18, 0, 0), neck=(24, 0, 0), skull=(12, 0, 0),
                jaw=(30, 0, 0), tail_1=(-16, 0, 0), tail_2=(-10, 0, 0), shells=1.5, tremble=3.0)
    return [(0, pose(rear(4), pos=(0, -1, 3), shells=1.0), "io"), (hit * .7, up, "out"), (hit - .05, pose(up, rear(-4)), "io"),
            (hit, slam, "in"), (hit + .16, pose(rear(6), pos=(0, -1, 4), shells=1.2), "out"),
            (hit + .34, pose(rear(12), pos=(0, -3, 4), shells=1.0), "in"), (hit + .6, mie(1.0), "out"),
            (hit + .9, pose(mie(1.0), skull=(-4, 0, 0)), "io"), (end, {"shells": 1.0}, "io")]


def pounce_keys(hit, end, shells):
    """花冠の飛びかかり: the server carries the body onto the target from 0.6 to 1.1 s (POUNCE_CARRY). It crouches and
    wiggles, springs in a high arc with the forepaws reaching out, crashes down on the mark and rises into the 見得."""
    s = {"shells": shells}
    crouch = pose(rear(10), pos=(0, -5, -2), chest=(10, 0, 0), neck=(14, 0, 0), skull=(6, 0, 0), shin_l=(22, 0, 0), shin_r=(22, 0, 0),
                  tail_1=(-20, 0, 0), tremble=2.0, **s)
    keys = [(0, dict(s), "io"), (.4, crouch, "io"), (.58, pose(crouch, pos=(0, -7, -3)), "io")]
    for k in range(6):
        u = (k + 1) / 6
        h = 34 * math.sin(math.pi * u)
        reach = math.sin(math.pi * min(1, u * 1.2))
        keys.append((.6 + .5 * u, pose(rear(-18 * (1 - u) + 12 * u), pos=(0, h, 0), arm_l=(-70 * reach, 0, 0), arm_r=(-70 * reach, 0, 0),
                                      forearm_l=(-20 * reach, 0, 0), forearm_r=(-20 * reach, 0, 0), thigh_l=(30 * reach, 0, 0), thigh_r=(30 * reach, 0, 0),
                                      neck=(-12 * reach, 0, 0), jaw=(24 * reach, 0, 0), tail_1=(10 * reach, 0, 0), shells=shells + .3 * reach), "lin"))
    land = pose(rear(18), pos=(0, -5, 3), chest=(14, 0, 0), neck=(18, 0, 0), jaw=(26, 0, 0), arm_l=(16, 0, 0), arm_r=(16, 0, 0),
                shin_l=(18, 0, 0), shin_r=(18, 0, 0), shells=shells + .3, tremble=2.5)
    return keys + [(hit + .02, land, "in"), (hit + .2, pose(rear(4), pos=(0, -1, 2), **s), "out"), (hit + .45, mie(shells), "out"),
                   (hit + .65, pose(mie(shells), skull=(-4, 0, 0)), "io"), (end, dict(s), "io")]


def charge_keys(hit, end, shells):
    """花吹雪の突進 (enraged only): it scrapes the ground and lowers its horns, then a bounding gallop carries it down
    the strip (CHARGE_CARRY 0.55..1.35 s), it skids to a stop and throws its head up."""
    scrape = pose(rear(8), pos=(0, -3, -2), neck=(22, 0, 0), skull=(18, 0, 0), arm_r=(-30, 0, 0), forearm_r=(-20, 0, 0), tail_1=(14, 0, 0),
                  shells=1.2, tremble=3.0)
    keys = [(0, {"shells": 1.0}, "io"), (.2, scrape, "io"), (.35, pose(scrape, arm_r=(20, 0, 0)), "io"), (.5, pose(scrape, arm_r=(-30, 0, 0)), "io")]
    for k in range(9):                              # a bounding gallop: fore and hind pairs together, the spine flexing
        tt = .55 + k * .1
        ph = k * math.pi / 2
        flex = math.sin(ph)
        keys.append((tt, pose(body=(8 * flex, 0, 0), pos=(0, 6 * abs(math.sin(ph)) - 2, 0), chest=(-6 * flex, 0, 0),
                              arm_l=(-40 * flex, 0, 0), arm_r=(-40 * flex + 6, 0, 0), forearm_l=(-30 * max(0, -flex), 0, 0), forearm_r=(-30 * max(0, -flex), 0, 0),
                              thigh_l=(40 * flex, 0, 0), thigh_r=(40 * flex - 6, 0, 0), neck=(18 - 4 * flex, 0, 0), skull=(12, 0, 0),
                              tail_1=(10 * flex, 0, 0), tail_2=(8 * flex, 0, 0), shells=1.3), "io"))
    skid = pose(rear(16), pos=(0, -4, 4), arm_l=(20, 0, 0), arm_r=(24, 0, 0), chest=(12, 0, 0), neck=(4, 0, 0), jaw=(20, 0, 0),
                tail_1=(-14, 0, 0), shells=1.2, tremble=3.0)
    return keys + [(1.5, skid, "out"), (1.75, mie(1.0), "out"), (1.95, pose(mie(1.0), skull=(-4, 0, 0)), "io"), (end, {"shells": 1.0}, "io")]


def rain_keys(hit, end, shells):
    """花冠落とし: it sits up and howls the petals into the sky over its prey, holds the note, then snaps its head down
    as the blossoms fall on the mark."""
    sit = sit_howl(shells + .4)
    return [(0, {"shells": shells}, "io"), (.5, pose(sit, jaw=(-20, 0, 0)), "out"), (.8, sit, "io"),
            (hit - .15, pose(sit, skull=(-6, 0, 0), tremble=4.0), "io"),
            (hit, pose(rear(-8), neck=(20, 0, 0), skull=(14, 0, 0), jaw=(8, 0, 0), shells=shells + .2), "in"),
            (hit + .3, pose(rear(-4), neck=(4, 0, 0), shells=shells), "out"), (end, {"shells": shells}, "io")]


def lunge_keys(hit, end, shells):
    """踏み込み爪 (small, on the move): it sinks and cocks the right paw, springs forward (the server carries it 3
    blocks, 0.2..0.5 s) and rakes the paw down and across as it lands, the body twisting into the blow."""
    s = {"shells": shells}
    cock = pose(rear(10), pos=(0, -4, -2), chest=(6, -8, 0), neck=(16, -6, 0), arm_r=(-30, 0, 10), forearm_r=(-40, 0, 0),
                thigh_l=(10, 0, 0), thigh_r=(10, 0, 0), tail_1=(12, 0, 0), shells=shells + .1, tremble=1.5)
    spring = pose(rear(-12), pos=(0, 7, 6), chest=(-6, -10, 0), neck=(-4, -8, 0), arm_l=(-30, 0, 0), arm_r=(-95, 0, 16),
                  forearm_r=(-30, 0, 0), hand_r=(-20, 0, 0), thigh_l=(30, 0, 0), thigh_r=(30, 0, 0), tail_1=(-16, 0, 0), tail_2=(-10, 0, 0),
                  shells=shells + .35)
    rake = pose(rear(14), pos=(0, -5, 8), body=(0, 14, -6), chest=(12, 16, -6), neck=(20, 14, 0), skull=(6, 8, 0), jaw=(16, 0, 0),
                arm_r=(30, 0, -26), forearm_r=(6, 0, 0), hand_r=(24, 0, 0), arm_l=(10, 0, 0), tail_1=(6, -24, 0), tail_2=(0, -20, 0),
                shells=shells + .45, tremble=2.0)
    return [(0, dict(s), "io"), (.18, cock, "out"), (.38, spring, "out"), (hit, rake, "in"),
            (hit + .2, pose(rake, rear(-4), body=(0, -6, 2), pos=(0, 1, 0)), "out"), (end, dict(s), "io")]


def barge_keys(hit, end, shells):
    """花肩の体当たり (small, on the move): Zinogre's shoulder barge — it swings its flank round toward its prey,
    leans away to load, then throws the whole side of its body into it (carried 3.5 blocks, 0.3..0.65 s), the
    petals on that side slamming open."""
    s = {"shells": shells}
    load = pose(rear(6), pos=(0, -3, 0), body=(0, 48, 12), chest=(4, 10, 8), neck=(10, -30, 0), skull=(0, -16, 0),
                arm_l=(-14, 0, -10), arm_r=(10, 0, 18), tail_1=(0, 30, 0), tail_2=(0, 20, 0), shells=shells + .1, tremble=2.0)
    slam = pose(rear(-4), pos=(0, 3, 4), body=(0, 64, -20), chest=(-4, 16, -14), neck=(-6, -36, -6), skull=(-6, -20, 0), jaw=(20, 0, 0),
                arm_l=(-40, 0, -24), arm_r=(24, 0, 30), thigh_l=(-20, 0, 0), tail_1=(0, -14, 0), tail_2=(0, -24, 0), tail_3=(0, -20, 0),
                shells=shells + .55)
    return [(0, dict(s), "io"), (.32, load, "out"), (hit, slam, "in"), (hit + .18, pose(slam, body=(0, -6, 8), pos=(0, -3, 0), tremble=3.0), "out"),
            (hit + .45, pose(body=(0, 14, 0), neck=(0, -10, 0), shells=shells), "io"), (end, dict(s), "io")]


def hopswipe_keys(hit, end, shells):
    """跳ね尾払い (small, on the move): a quick hop to the side (carried 2.5 blocks, 0.2..0.55 s) that turns into a
    whip of the tail across its front as it lands, the head turned the other way to keep its prey in sight."""
    s = {"shells": shells}
    crouch = pose(rear(8), pos=(0, -4, 0), arm_l=(-8, 0, 0), arm_r=(-8, 0, 0), shin_l=(14, 0, 0), shin_r=(14, 0, 0),
                  tail_1=(10, 30, 0), tail_2=(6, 24, 0), tail_3=(0, 20, 0), shells=shells + .1)
    air = pose(rear(-8), pos=(0, 11, 0), body=(0, -20, -10), arm_l=(-40, 0, -10), arm_r=(-40, 0, 10), forearm_l=(-30, 0, 0), forearm_r=(-30, 0, 0),
               thigh_l=(-16, 0, 0), thigh_r=(-16, 0, 0), neck=(0, 20, 0), tail_1=(0, 40, 0), tail_2=(0, 34, 0), tail_3=(0, 30, 0), shells=shells + .3)
    whip = pose(rear(6), pos=(0, -3, 0), body=(0, 26, 6), chest=(0, 14, 0), neck=(4, 30, 0), skull=(0, 14, 0), jaw=(10, 0, 0),
                arm_l=(10, 0, 0), arm_r=(10, 0, 0), tail_1=(-6, -50, 0), tail_2=(-4, -46, 0), tail_3=(0, -40, 0), tail_4=(0, -36, 0), tail_5=(0, -30, 0),
                shells=shells + .5, tremble=2.0)
    return [(0, dict(s), "io"), (.16, crouch, "out"), (.38, air, "out"), (hit, whip, "in"),
            (hit + .2, pose(whip, body=(0, -8, 0), tail_2=(0, 12, 0), tail_3=(0, 16, 0)), "out"), (end, dict(s), "io")]


def search_pose(t, L=2.4, enraged=False):
    """Lost its prey: it prowls with the nose low to the ground, sweeping side to side, now and then lifting the head
    to scent the air."""
    p = walk_pose(t * .5, L * .5, enraged)
    sweep = wave(t, L)
    lift = max(0.0, wave(t, L, .3)) ** 3
    return pose(p, neck=(26 - 30 * lift, 14 * sweep, 0), skull=(14 - 18 * lift, 8 * sweep, 0), pos=(0, -2, 0))


# --- maneuvers (the server carries the body; QuestEncounterCombat.startManeuver timings) ---------------------
def hop_back_keys(shells):
    """1.1 s, moved back 4 blocks between 0.28 and 0.68 s: crouch, a bounding leap with the forelegs tucked, a soft
    landing that sinks and rises."""
    s = {"shells": shells}
    crouch = pose(rear(10), pos=(0, -4, 0), arm_l=(-12, 0, 0), arm_r=(-12, 0, 0), shin_l=(18, 0, 0), shin_r=(18, 0, 0), **s)
    air = pose(rear(-16), pos=(0, 16, 0), arm_l=(-50, 0, 0), arm_r=(-50, 0, 0), forearm_l=(-40, 0, 0), forearm_r=(-40, 0, 0),
               thigh_l=(-20, 0, 0), thigh_r=(-20, 0, 0), neck=(-10, 0, 0), tail_1=(-14, 0, 0), shells=shells + .3)
    land = pose(rear(8), pos=(0, -4, 0), arm_l=(8, 0, 0), arm_r=(8, 0, 0), neck=(14, 0, 0), shin_l=(18, 0, 0), shin_r=(18, 0, 0), **s)
    return [(0, dict(s), "io"), (.26, crouch, "io"), (.48, air, "out"), (.68, land, "in"), (.85, pose(rear(-2), **s), "out"),
            (1.1, dict(s), "io")]


def dash_keys(sign, shells):
    """0.9 s, moved 5 blocks sideways between 0.2 and 0.5 s: a low sideways bound, the body banked into it and the
    tail swinging out behind, then a skid."""
    s = {"shells": shells}
    load = pose(pos=(0, -3, 0), chest=(0, 0, -10 * sign), body=(0, 0, -6 * sign), neck=(4, 14 * sign, 0), **s)
    bound = pose(pos=(0, 9, 0), chest=(0, 0, 18 * sign), body=(0, 0, 14 * sign), neck=(-4, 18 * sign, 0),
                 arm_l=(-30, 0, 0), arm_r=(-30, 0, 0), tail_1=(0, -24 * sign, 0), tail_2=(0, -18 * sign, 0), shells=shells + .25)
    skid = pose(rear(6), pos=(0, -3, 0), chest=(0, 0, -8 * sign), body=(0, -10 * sign, 0), tail_2=(0, 20 * sign, 0), **s)
    return [(0, dict(s), "io"), (.18, load, "io"), (.36, bound, "out"), (.52, skid, "in"), (.9, dict(s), "io")]


def strafe_pose(t, sign, L=1.2, enraged=False):
    """Circling its prey: the walk cycle turned into a sideways prowl, body angled in, head locked on."""
    p = walk_pose(t, L, enraged)
    return pose(p, body=(0, 16 * sign, 0), neck=(0, -18 * sign, 0), skull=(0, -8 * sign, 0), chest=(0, 0, 4 * sign))


def growl_keys(shells):
    """1.6 s, 威嚇 (between attacks, standing): it drops its head low with the jaw half open, weaves it slowly from
    side to side and rumbles (the petals rattle), then snaps it up at its prey."""
    s = {"shells": shells}
    low = pose(rear(6), pos=(0, -3, 0), chest=(8, 0, 0), neck=(26, 0, 0), skull=(10, 0, 0), jaw=(18, 0, 0),
               arm_l=(-6, 0, -6), arm_r=(-6, 0, 6), tail_1=(10, 0, 0), shells=shells + .15, tremble=2.5)
    return [(0, dict(s), "io"), (.25, low, "out"),
            (.55, pose(low, neck=(0, 16, 0), skull=(0, 10, -6), chest=(0, 4, 0)), "io"),
            (.9, pose(low, neck=(0, -16, 0), skull=(0, -10, 6), chest=(0, -4, 0)), "io"),
            (1.15, pose(low, neck=(2, 6, 0), jaw=(10, 0, 0), tremble=4.0), "io"),
            (1.3, pose(rear(-6), neck=(-14, 0, 0), skull=(-6, 0, 0), jaw=(26, 0, 0), shells=shells + .3, tremble=2.0), "out"),
            (1.6, dict(s), "io")]


def shake_keys(shells):
    """1.3 s, 身震い (between attacks, standing): a dog shaking off water, head first and then the shoulders, the back
    and the tail in turn, the petals clattering open and shut."""
    keys = [(0, {"shells": shells}, "io"), (.15, pose(pos=(0, -2, 0), neck=(8, 0, 0)), "io")]
    for k in range(8):                      # the shake runs down the body: each joint a little later than the last
        t = .25 + k * .1
        a = (-1) ** k * (1 - k / 10)
        keys.append((t, pose(pos=(0, -2, 0), neck=(6, 0, 22 * a), skull=(0, 0, 16 * a), chest=(0, 0, 14 * a),
                             body=(0, 0, 8 * a * (k > 1)), tail_1=(0, 18 * a * (k > 2), 0), tail_2=(0, 20 * a * (k > 3), 0),
                             tail_3=(0, 18 * a * (k > 3), 0), shells=shells + (.4 if k % 2 else 0), tremble=3.0), "io"))
    return keys + [(1.1, pose(neck=(-4, 0, 0), pos=(0, 1, 0), shells=shells), "out"), (1.3, {"shells": shells}, "io")]


def scrape_keys(shells):
    """1.2 s, 地を掻く (between attacks, standing): it rakes the ground twice with a forepaw like a bull, head low,
    eyes on its prey."""
    base = pose(rear(8), pos=(0, -3, 0), chest=(6, 0, 0), neck=(20, 0, 0), skull=(8, 0, 0), tail_1=(14, 0, 0), shells=shells)
    up = pose(base, arm_r=(-48, 0, 0), forearm_r=(-50, 0, 0), hand_r=(-20, 0, 0), chest=(0, -4, 0))
    back = pose(base, arm_r=(26, 0, 0), forearm_r=(-10, 0, 0), hand_r=(16, 0, 0), chest=(0, 3, 0), tremble=2.0)
    return [(0, {"shells": shells}, "io"), (.2, base, "io"), (.35, up, "out"), (.5, back, "in"), (.68, up, "out"), (.84, back, "in"),
            (1.0, base, "io"), (1.2, {"shells": shells}, "io")]


def stalk_pose(t, enraged=False):
    """忍び寄り (between attacks, moving in from afar): a slow, low creep, shoulders rolling, head level and the tail
    held still and low."""
    p = walk_pose(t, 1.6, enraged)
    return pose(p, pos=(0, -5, 0), chest=(8, 0, 0), neck=(22, 0, 0), skull=(-8, 0, 0), tail_1=(-10, 0, 0), tail_2=(-4, 0, 0))


def flinch_keys(shells):
    """1.0 s: a part breaks (QuestEncounterCombat.partBroken): the head snaps back, the body recoils and shakes it off."""
    s = {"shells": shells}
    hit = pose(rear(-12), pos=(0, 1, -4), neck=(-24, 10, 0), skull=(-18, 8, 0), jaw=(30, 0, 0), chest=(-6, 0, 8),
               arm_l=(-20, 0, 0), tail_1=(18, 0, 0), tremble=4.0, **s)
    return [(0, dict(s), "io"), (.12, hit, "out"), (.4, pose(rear(6), pos=(0, -2, -3), neck=(10, -12, 0), **s), "io"),
            (.65, pose(neck=(0, 14, 0), skull=(0, 10, 0), **s), "io"), (1.0, dict(s), "io")]


def countered_keys(shells):
    """3.5 s, the counter: thrown back off its strike, it stumbles, its legs give and it crashes onto its side, the
    petals sagging; it thrashes, then scrambles up and shakes the petals back open."""
    thrown = pose(rear(-30), pos=(0, 3, -6), neck=(-34, 0, 0), skull=(-24, 0, 0), jaw=(38, 0, 0), arm_l=(-60, 0, 0), arm_r=(-50, 0, 0),
                  tail_1=(24, 0, 0), shells=shells + .3, tremble=5.0)
    stumble = pose(rear(4), pos=(0, -2, -8), chest=(0, 0, 14), neck=(12, 18, 0), arm_r=(20, 0, 0), shells=shells)
    down = pose(body=(0, 0, 70), pos=(0, -13, -8), neck=(8, 16, 0), skull=(10, 0, 0), jaw=(16, 0, 0), arm_l=(24, 0, 0), arm_r=(-30, 0, 0),
                thigh_l=(-16, 0, 0), thigh_r=(20, 0, 0), tail_1=(-16, 0, 0), tail_2=(-12, 0, 0), shells=shells * .2)
    thrash = pose(down, neck=(-6, 22, 0), arm_l=(-30, 0, 0), arm_r=(10, 0, 0), thigh_l=(-40, 0, 0), tremble=3.0)
    rise = pose(rear(10), pos=(0, -4, -6), chest=(0, 0, 8), neck=(16, 0, 0), shells=shells * .5)
    return [(0, {"shells": shells}, "io"), (.15, thrown, "out"), (.5, stumble, "io"), (.85, down, "in"), (1.0, pose(down, pos=(0, -11, -8)), "out"),
            (1.15, down, "in"), (1.8, thrash, "io"), (2.3, down, "io"), (2.9, rise, "out"), (3.2, pose(rear(-4), pos=(0, 0, -6), neck=(-6, 0, 0), shells=shells + .2, tremble=4.0), "io"),
            (3.5, {"shells": shells}, "io")]


def sigil_keys():
    """6 s, 桜紋の陣 (QuestEncounterCombat.SIGIL_MILLIS): it sits back and howls the crests into bloom, swings its
    head round the arena as if counting them, the howl rising and the petals flaring; at the end the head drops."""
    sit = sit_howl(1.2)
    keys = [(0, {"shells": 1.0}, "io"), (.4, pose(sit, jaw=(-20, 0, 0)), "out")]
    for k in range(5):                                   # the head sweeps from one side of the arena to the other
        t = .9 + k * .95
        side = (-1) ** k
        keys.append((t, pose(sit, neck=(4, 26 * side, 0), skull=(0, 12 * side, 0), shells=1.2 + .05 * k, tremble=2.0 + k), "io"))
    keys += [(5.6, pose(sit, shells=1.5, tremble=6.0), "io"), (5.85, pose(rear(-6), neck=(22, 0, 0), skull=(12, 0, 0), jaw=(10, 0, 0), shells=1.1), "in"),
             (6.0, {"shells": 1.0}, "io")]
    return keys


def finale_keys():
    """8 s, 千本桜 (QuestEncounterCombat.FINALE_MILLIS), 蓄花: Zinogre's charge-up, no sitting howl. Feet planted wide,
    low, back arched; the body rocks harder and harder while the petals pulse open in waves; at 6.4 s it throws its
    head up and roars standing on all fours, then leaps and slams both forepaws down at 8 s."""
    low = pose(rear(14), pos=(0, -7, -2), arm_l=(10, 0, -14), arm_r=(10, 0, 14), forearm_l=(14, 0, 0), forearm_r=(14, 0, 0),
               chest=(14, 0, 0), neck=(34, 0, 0), skull=(20, 0, 0), jaw=(10, 0, 0),
               tail_1=(30, 0, 0), tail_2=(24, 0, 0), tail_3=(18, 0, 0), tail_4=(12, 0, 0))
    keys = [(0, {"shells": 1.0}, "io"), (.35, pose(rear(16), pos=(0, -5, 0), neck=(16, 0, 0), shells=1.0, tremble=2.0), "out"),
            (1.0, pose(low, shells=.6, tremble=2.0), "io")]
    for k in range(10):                                   # the rocking builds: side to side, faster and wider
        t = 1.45 + k * .48
        g = (k + 1) / 10
        s = (-1) ** k
        keys.append((t, pose(low, body=(0, 0, 7 * g * s), chest=(0, 6 * g * s, 4 * g * s), neck=(-4 * g, 10 * g * s, 0),
                             pos=(0, -1 * g, 0), shells=.6 + .9 * g * (1 if k % 2 else .7), tremble=2 + 5 * g), "io"))
    roar = pose(rear(-18), pos=(0, 2, 0), arm_l=(-10, 0, -8), arm_r=(-10, 0, 8), chest=(-10, 0, 0), neck=(-44, 0, 0), skull=(-26, 0, 0),
                jaw=(48, 0, 0), tail_1=(26, 0, 0), tail_2=(18, 0, 0), shells=1.7, tremble=6.0)
    leap = pose(rear(-40), pos=(0, 16, 6), arm_l=(-90, 0, 6), arm_r=(-90, 0, -6), forearm_l=(-40, 0, 0), forearm_r=(-40, 0, 0),
                neck=(-20, 0, 0), skull=(-14, 0, 0), jaw=(30, 0, 0), tail_1=(-10, 0, 0), shells=1.7, tremble=3.0)
    slam = pose(rear(22), pos=(0, -7, 8), arm_l=(24, 0, -10), arm_r=(24, 0, 10), chest=(18, 0, 0), neck=(26, 0, 0), skull=(10, 0, 0),
                jaw=(30, 0, 0), tail_1=(-20, 0, 0), tail_2=(-10, 0, 0), shells=1.8, tremble=3.0)
    return keys + [(6.4, roar, "out"), (7.2, pose(roar, neck=(0, 12, 0), tremble=7.0), "io"), (7.55, pose(low, shells=1.7, tremble=5.0), "in"),
                   (7.8, leap, "out"), (8.0, slam, "in")]


def enrage_keys():
    """5 s (QuestEncounterCombat.ENRAGE_HOLD_MILLIS), Zinogre's howl: the beast stops and shudders low while the petals
    rattle, sits back on its haunches and howls straight at the sky; at 2.5 s the petals burst open in a wave down the
    tail, the howl swells and sweeps the sky, then it drops forward onto all fours in its new stance."""
    crouch = pose(rear(10), pos=(0, -4, -1), chest=(10, 0, 0), neck=(18, 0, 0), skull=(12, 0, 0), jaw=(6, 0, 0),
                  tail_1=(-14, 0, 0), shells=.15, tremble=2.0)
    shiver = pose(crouch, shells=.35, tremble=6.0, neck=(22, -6, 0))
    land = pose(rear(14), pos=(0, -3, 3), arm_l=(10, 0, 0), arm_r=(10, 0, 0), neck=(16, 0, 0), skull=(8, 0, 0), jaw=(18, 0, 0),
                shells=1.0, tremble=2.0)
    return [(0, {}, "io"), (.6, crouch, "io"), (1.5, shiver, "io"), (2.25, sit_howl(.4), "out"), (2.5, sit_howl(1.45), "in"),
            (3.9, pose(sit_howl(1.35), skull=(-6, 0, 0), jaw=(6, 0, 0)), "io"), (4.3, land, "in"),
            (5.0, idle_pose(0, enraged=True), "io")]


def death_keys(shells):
    """Staggers, legs buckle one after another, rolls onto its side with a thud and a bounce; the petals close."""
    hit = pose(rear(-10), pos=(0, 0, -3), chest=(-6, 0, 10), neck=(-20, 10, 0), jaw=(36, 0, 0), shells=shells, tremble=4.0)
    buckle = pose(rear(10), pos=(0, -5, -2), chest=(8, 0, 18), arm_r=(30, 0, 0), forearm_r=(30, 0, 0), neck=(10, 14, 0),
                  shells=shells * .8, tremble=2.0)
    fall = pose(body=(0, 0, 76), pos=(0, -14, 0), neck=(12, 10, 0), skull=(14, 0, 0), jaw=(26, 0, 0),
                arm_l=(24, 0, 0), arm_r=(-28, 0, 0), thigh_l=(-14, 0, 0), thigh_r=(18, 0, 0),
                tail_1=(-16, 0, 0), tail_2=(-14, 0, 0), tail_3=(-12, 0, 0), shells=shells * .3)
    return [(0, {"shells": shells}, "io"), (.35, hit, "out"), (.9, buckle, "io"), (1.45, fall, "in"),
            (1.62, pose(fall, pos=(0, -12, 0)), "out"), (1.85, fall, "in"), (2.4, dict(fall, shells=0.0), "io")]


def window(t, a, b, fade=.15):
    """1 inside [a, b], easing in and out over `fade` seconds, 0 outside."""
    if t <= a - fade or t >= b + fade:
        return 0.0
    k = min(1.0, (t - a + fade) / fade, (b + fade - t) / fade)
    return k * k * (3 - 2 * k)


def decay(t, at, rate=3.0):
    return math.exp(-max(0.0, t - at) * rate) if t >= at else 0.0


def overlay_for(name, hit, end):
    """Continuous motion laid over the key poses so no stretch of a clip stands still."""
    s = math.sin
    if name == "sakura-claw":
        def f(t):
            w = window(t, .55, hit - .1, .12)
            r = decay(t, hit + .3, 2.5)
            return {"arm_r": (-18 * w * (t - .55) / max(.1, hit - .65), 0, 0),                   # the claw keeps drawing back
                    "pos": (0, 2.5 * w * (t - .55) / max(.1, hit - .65), 0),                       # and the body keeps rising
                    "chest": (0, 0, 2.5 * w * s(t * 18)),
                    "neck": (0, 12 * r * s((t - hit) * 16), 0), "skull": (0, 8 * r * s((t - hit) * 16 - .8), 0),   # head shake
                    "tail_2": (0, 14 * r * s((t - hit) * 9), 0), "tail_3": (0, 16 * r * s((t - hit) * 9 - .7), 0)}
        return f
    if name == "sakura-tail":
        def f(t):
            w = window(t, .3, hit - .42, .12)
            freq = 3.5 + 3 * (t - .3)                                                                # the hip wiggle quickens
            return {"body": (0, 7 * w * s(t * freq * 6.28), 0), "chest": (0, -5 * w * s(t * freq * 6.28 + .5), 0),
                    "pos": (0, -1.2 * w * abs(s(t * freq * 6.28)), 0),
                    "tail_3": (0, 12 * w * s(t * 5), 0), "tail_4": (0, 14 * w * s(t * 5 - .6), 0), "tail_5": (0, 16 * w * s(t * 5 - 1.2), 0),
                    "neck": (0, 0, 3 * w * s(t * 9))}
        return f
    if name == "sakura-scatter":
        def f(t):
            g = window(t, .3, hit - .15, .12)
            r = decay(t, hit + .35, 4.0)
            return {"body": (0, 0, 4 * g * s(t * 30)), "chest": (0, 0, -4 * g * s(t * 30 + .4)),       # a shudder while gathering
                    "neck": (0, 6 * r * s((t - hit) * 22), 10 * r * s((t - hit) * 22)),               # then a dog-like shake
                    }
        return f
    if name == "sakura-bloom":
        def f(t):
            w = window(t, hit * .5, hit - .2, .12)
            return {"arm_l": (22 * w * s(t * 12), 0, 0), "arm_r": (22 * w * s(t * 12 + 3.1), 0, 0),     # forepaws claw the air
                    "skull": (0, 6 * w * s(t * 3), 0)}
        return f
    if name == "enrage":
        def f(t):
            w = window(t, 2.6, 3.8, .2)
            return {"neck": (0, 10 * w * s(t * 2.6), 0), "skull": (0, 6 * w * s(t * 2.6 - .5), 0),       # the howl sweeps the sky
                    "arm_l": (14 * w * s(t * 9), 0, 0), "arm_r": (14 * w * s(t * 9 + 3.1), 0, 0)}
        return f
    return None


def animate(m, b):
    bake(b, "idle", 2.4, looped(2.4, lambda t: idle_pose(t)), "loop")
    bake(b, "idle_enraged", 2.4, looped(2.4, lambda t: idle_pose(t, enraged=True)), "loop")
    bake(b, "walk", 1.2, looped(1.2, lambda t: walk_pose(t)), "loop")
    bake(b, "walk_enraged", 1.2, looped(1.2, lambda t: walk_pose(t, enraged=True)), "loop")
    for name, keys in (("sakura-claw", claw_keys), ("sakura-tail", tail_keys), ("sakura-scatter", scatter_keys), ("sakura-bloom", bloom_keys),
                       ("sakura-horn", horn_keys), ("sakura-spin", spin_keys), ("sakura-howl", howl_attack_keys),
                       ("sakura-claw2", claw2_keys), ("sakura-claw3", claw3_keys),
                       ("sakura-pounce", pounce_keys), ("sakura-rain", rain_keys), ("sakura-charge", charge_keys),
                       ("sakura-lunge", lunge_keys), ("sakura-barge", barge_keys), ("sakura-hopswipe", hopswipe_keys)):
        hit, rec = ABILITIES[name]
        ov = overlay_for(name, hit, hit + rec)
        if name not in ENRAGED_ONLY:
            bake(b, name, hit + rec, keys(hit, hit + rec, 0.0), overlay=ov)
        bake(b, name if name in ENRAGED_ONLY else f"{name}_enraged", hit + rec, keys(hit, hit + rec, 1.0), overlay=ov)
    bake(b, "search", 2.4, looped(2.4, lambda t: search_pose(t)), "loop")
    for tag, sh in (("", 0.0), ("_enraged", 1.0)):
        bake(b, f"flinch{tag}", 1.0, flinch_keys(sh))
        bake(b, f"countered{tag}", 3.5, countered_keys(sh))
    bake(b, "sigil", 6.0, sigil_keys())
    bake(b, "finale", 8.0, finale_keys())
    for tag, sh in (("", 0.0), ("_enraged", 1.0)):
        bake(b, f"hop_back{tag}", 1.1, hop_back_keys(sh))
        bake(b, f"dash_l{tag}", .9, dash_keys(1, sh))
        bake(b, f"dash_r{tag}", .9, dash_keys(-1, sh))
        bake(b, f"growl{tag}", 1.6, growl_keys(sh))
        bake(b, f"shake{tag}", 1.3, shake_keys(sh))
        bake(b, f"scrape{tag}", 1.2, scrape_keys(sh))
        bake(b, f"stalk{tag}", 1.6, looped(1.6, lambda t, e=bool(sh): stalk_pose(t, enraged=e)))
        bake(b, f"strafe_l{tag}", 1.2, looped(1.2, lambda t, e=bool(sh): strafe_pose(t, 1, enraged=e)), "loop")
        bake(b, f"strafe_r{tag}", 1.2, looped(1.2, lambda t, e=bool(sh): strafe_pose(t, -1, enraged=e)), "loop")
    bake(b, "enrage", 5.0, enrage_keys(), overlay=overlay_for("enrage", 2.5, 5.0))
    bake(b, "death", 2.4, death_keys(0.0))
    bake(b, "death_enraged", 2.4, death_keys(1.0))


def open_pose(b, f=1.0):
    """Raw Blockbench (editor) euler per shell bone at opening f, for previews."""
    return {name: matrix_euler(axis_angle(axis, deg * f)) for name, (axis, deg) in b.frames.items()}


# Clips whose feet must stay on the ground: their body height is corrected frame by frame after baking.
GROUNDED = ("idle", "idle_enraged", "walk", "walk_enraged", "strafe_l", "strafe_r", "strafe_l_enraged", "strafe_r_enraged",
            "stalk", "stalk_enraged", "search")


def ground_feet(path):
    """Keeps the paws on the ground in the walking clips. Their poses swing and roll the legs freely, and the arcs
    push paws through the floor (a dog paddling more than walking) or leave the hind pair hanging; here every frame
    is posed with the real bones (scripts/boss_preview/rbb.py), the lowest corner of the fore pair and of the hind
    pair measured, and the body pitched and raised so both pairs stand exactly where the paws stand at rest."""
    sys.path.insert(0, str(Path(__file__).resolve().parent / "boss_preview"))
    import numpy as np
    import rbb
    sc = rbb.Scene(path)

    def tree(bone):
        return [bone] + [n for n, v in sc.bones.items() if v["parent"] == bone for n in tree(n)]

    def cubes(limbs):
        return [(bone, np.array([[x, y, z] for x in (c["from"][0], c["to"][0]) for y in (c["from"][1], c["to"][1])
                                 for z in (c["from"][2], c["to"][2])])) for k in limbs for bone in tree(k) for c in sc.bones[bone]["cubes"]]
    fore, hind = cubes(("hand_l", "hand_r")), cubes(("foot_l", "foot_r"))

    def lowest(pose, paws):
        mats = sc.mats(pose)
        return min(float((corners @ mats[bone][:3, :3].T + mats[bone][:3, 3])[:, 1].min()) for bone, corners in paws)

    def pitched(pose, d):
        rot, pos, scl = pose.get("body", (np.zeros(3), np.zeros(3), np.ones(3)))
        return {**pose, "body": (rot + np.array([d, 0, 0]), pos, scl)}
    ground = min(lowest({}, fore), lowest({}, hind))
    data = json.loads(path.read_text(encoding="utf-8"))
    for anim in data["animations"]:
        if anim["name"] not in GROUNDED:
            continue
        body = next(a for a in anim["animators"].values() if a["name"] == "body")
        turns = {k["time"]: k for k in body["keyframes"] if k["channel"] == "rotation"}
        for k in body["keyframes"]:
            if k["channel"] != "position" or k["time"] not in turns:
                continue
            pose = sample_pose(anim, k["time"])
            # pitch until the fore and hind pairs are level with each other (secant steps on the height gap)
            gap = lambda d: lowest(pitched(pose, d), hind) - lowest(pitched(pose, d), fore)
            d0, d1, g0 = 0.0, 1.0, gap(0.0)
            for _ in range(4):
                g1 = gap(d1)
                if abs(g1 - g0) < 1e-6:
                    break
                d0, d1, g0 = d1, d1 - g1 * (d1 - d0) / (g1 - g0), g1
            d = max(-8.0, min(8.0, d0))
            pose = pitched(pose, d)
            turns[k["time"]]["data_points"][0]["x"] = round(float(turns[k["time"]]["data_points"][0]["x"]) - d, 3)   # stored negated
            low = min(lowest(pose, fore), lowest(pose, hind))
            k["data_points"][0]["y"] = round(float(k["data_points"][0]["y"]) + ground - low, 3)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def sample_pose(anim, t):
    """The pose a clip shows at t (linear between keys), in rbb.Scene's form; editor x is stored negated."""
    import numpy as np
    pose = {}
    for an in anim["animators"].values():
        ch = {}
        for k in an["keyframes"]:
            d = k["data_points"][0]
            ch.setdefault(k["channel"], []).append((k["time"], np.array([float(d["x"]), float(d["y"]), float(d["z"])])))
        o = {}
        for c, keys in ch.items():
            keys.sort(key=lambda q: q[0])
            v = keys[-1][1]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    v = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 1)
                    break
            o[c] = v
        r = o.get("rotation", np.zeros(3))
        pose[an["name"]] = (np.array([-r[0], r[1], r[2]]), o.get("position", np.zeros(3)), o.get("scale", np.ones(3)))
    return pose


def main():
    b = build()
    outliner = b.outliner()
    animate(b.m, b)
    out = Path(__import__("os").environ.get("SAKURA_OUT", ROOT / "assets/bosses/sakura_fang.bbmodel"))
    data = b.m.write(out, outliner)
    ground_feet(out)
    print(f"{out}: {len(data['elements'])} cubes, {len(b.bones)} bones, {len(data['animations'])} animations")
    return b


if __name__ == "__main__":
    main()
