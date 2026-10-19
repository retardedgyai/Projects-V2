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
import math
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


def petal_painter(kind="shell"):
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
            return PETAL[max(0, min(4, int(lvl)))] + (255,)
        return paint, ("petal", kind, big, w, h)
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

    def petal(self, bone, base, out_dir, normal, L, W, kind="shell", r0=0.0):
        R = basis(out_dir, normal)
        y = [R[i][1] for i in range(3)]
        self.oriented(bone, f"{bone}_petal{len(self.items[bone])}", add(base, y, r0 + L / 2), [W / 2, L / 2, .5], R, petal_painter(kind))

    def outliner(self):
        made = {}
        for name, parent, pivot in reversed(self.bones):
            kids = [made[c] for c, p, _ in self.bones if p == name]
            made[name] = self.m.bone(name, pivot, self.items[name] + kids)
        return [made[n] for n, p, _ in self.bones if p is None]


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
        hs("skull", (5.9 * s, 2.4, 7.5), (5.7 * s, 2.0, 11), 1, 1.2, flat_painter(EYE), name="glow_eye")
        for z in (18, 14):
            hs("skull", (2.8 * s, -2.4, z), (2.8 * s, -5.4, z + .4), 1.3, 1.3, "claw")   # upper fangs
        hs("jaw", (2.5 * s, -3.6, 16), (2.5 * s, -1.4, 16.4), 1.1, 1.1, "claw")         # lower fangs
        # horns: thick roots above the eyes, sweeping back past the neck, tips curling up, one side spike
        pts = [(4.4, 5.6, 7), (7.4, 10, 0), (9.8, 14.2, -7), (11.4, 18.6, -12.5), (12, 23, -16), (11.4, 27, -18), (10.2, 29.6, -18.6)]
        ws = [4.4, 3.7, 3.0, 2.3, 1.7, 1.2]
        n = len(ws)
        for k in range(n):
            a, c = pts[k], pts[k + 1]
            hs("skull", (a[0] * s, a[1], a[2]), (c[0] * s, c[1], c[2]), ws[k], ws[k], grad_painter(k / n, (k + 1) / n))
        for (bx, by, bz), (tx, ty, tz), w, t in (((9.6, 13.6, -6), (14.6, 15.4, -10.5), 1.5, .55), ((11.6, 19.6, -13), (16, 23, -15.5), 1.2, .75),
                                                  ((8, 11, -1.5), (11.6, 9.4, -6), 1.3, .45)):
            hs("skull", (bx * s, by, bz), (tx * s, ty, tz), w, w, grad_painter(t, 1))
        hs("skull", (5.9 * s, 2.6, 7.2), (6.4 * s, 4.8, .5), .6, .6, flat_painter(GLOW), name="glow_streak")   # eye streak
        # cheek fur flaring back
        tufts(b, "skull", H(6 * s, -1.2, 5), mat_vec([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [s * .55, -.25, -1]), 5, 60, 13, 4, (s, 0, 0), 80 + s)
    # beard: pale fur hanging under the jaw
    for k, z in enumerate((5, 9, 13, 17)):
        tufts(b, "jaw", H(0, -6.6, z), [-c for c in u], 4, 80, 9 - k, 3.5, f, 90 + k)


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
        tufts(b, "chest", (0, 27, z), (0, -1, -.35), 5, 70, 9, 4, (0, 0, 1), int(z) + 40)

    for s, side in ((1, "l"), (-1, "r")):
        # massive forearms: elbow back, forearm forward, wide paw
        b.seg(f"arm_{side}", (13 * s, 44, 12), (15 * s, 21, 6), 12, 13, "hide", seed=5)
        b.seg(f"forearm_{side}", (15 * s, 22, 6), (15 * s, 4, 18), 11, 12, "hide", seed=6)
        for k, (top, bot, w) in enumerate((((17.6, 22, 4), (18.4, 15, 9), 11), ((18, 17, 7.5), (18.8, 10, 12.5), 10), ((18.2, 12, 11), (18.8, 5.5, 15.5), 8.5))):
            T = (top[0] * s, top[1], top[2]); B = (bot[0] * s, bot[1], bot[2])
            b.seg(f"forearm_{side}", T, B, 2.4, w, "shell", seed=7 + k)                         # layered forearm shells
            b.seg(f"forearm_{side}", (B[0] + .9 * s, B[1] + .4, B[2] - w / 2 + .6), (B[0] + .9 * s, B[1] + .4, B[2] + w / 2 - .6), .6, .6,
                  flat_painter(GLOW), name="glow_seam")
            b.seg(f"forearm_{side}", (B[0], B[1] + 1.2, B[2] + w / 2 - 1), (B[0] + 1.6 * s, B[1] - 2.6, B[2] + w / 2 + 1.2), 1.4, 1.4, grad_painter(.3, 1))  # spur
        for k, (top, bot, w) in enumerate((((17, 47, 14), (19, 37, 11), 13), ((18.6, 40, 11.5), (20, 30, 8.5), 11))):
            T = (top[0] * s, top[1], top[2]); B = (bot[0] * s, bot[1], bot[2])
            b.seg(f"arm_{side}", T, B, 2.6, w, "shell", seed=11 + k)                             # layered shoulder shells
            b.seg(f"arm_{side}", (B[0] + .9 * s, B[1] + .4, B[2] - w / 2 + .6), (B[0] + .9 * s, B[1] + .4, B[2] + w / 2 - .6), .6, .6,
                  flat_painter(GLOW), name="glow_seam")
        b.box(f"hand_{side}", (15 * s - 7, 0, 12), (15 * s + 7, 5, 27), "hide", seed=9)
        for k in (-4.4, 0, 4.4):
            x = 15 * s + k
            b.seg(f"hand_{side}", (x, 3.4, 25), (x, 3, 30), 2.4, 2.4, "claw")
            b.seg(f"hand_{side}", (x, 3, 30), (x, -.2, 32.6), 1.6, 1.6, grad_painter(.2, .9, "claw"))
        tufts(b, f"forearm_{side}", (15 * s, 22, 3), (s * .25, .1, -1), 4, 40, 10, 3.5, (1, 0, 0), 60 + s)
        # short crouched hind legs
        b.seg(f"thigh_{side}", (10 * s, 35, -25), (12 * s, 18, -16), 10, 12, "hide", seed=10)
        b.seg(f"shin_{side}", (12 * s, 19, -16), (12 * s, 8, -30), 6, 6, "hide", seed=11)
        b.seg(f"foot_{side}", (12 * s, 9, -30), (12 * s, 1, -24), 5.5, 5.5, "hide", seed=12)
        b.box(f"foot_{side}", (12 * s - 4.5, 0, -27), (12 * s + 4.5, 4, -17), "hide", seed=13)
        for k in (-3, 0, 3):
            b.seg(f"foot_{side}", (12 * s + k, 2, -17.5), (12 * s + k, .2, -14), 1.6, 1.6, "claw")
        tufts(b, f"thigh_{side}", (11 * s, 33, -27), (s * .3, .1, -1), 4, 40, 9, 3.5, (1, 0, 0), 70 + s)

    # glowing seams along the flanks and the tail sides (thin, sakura pink)
    for s in (1, -1):
        b.seg("chest", (13.3 * s, 33, 13), (13.3 * s, 30, -1), .6, .6, flat_painter(GLOW), name="glow_seam")
        b.seg("chest", (13.3 * s, 38, 13), (13.3 * s, 36, 2), .6, .6, flat_painter(GLOW), name="glow_seam")
        b.seg("body", (9.4 * s, 34.5, -3), (9.6 * s, 31.5, -27), .6, .6, flat_painter(GLOW), name="glow_seam")
        for i in range(len(TAIL) - 1):
            w = [13, 12, 11, 9.5, 8][i]
            a, c = TAIL[i], TAIL[i + 1]
            b.seg(f"tail_{i + 1}", (a[0] + (w / 2 + .1) * s, a[1], a[2]), (c[0] + (w / 2 + .1) * s, c[1], c[2]), .6, .6, flat_painter(GLOW), name="glow_seam", over=0)

    # neck and head, carried low and forward
    b.seg("neck", (0, 43, 14), (0, 39, 28), 16, 16, "hide", seed=14)
    head(b)

    # mane: rows of pale fur over neck and shoulders, standing back and up
    for z0, y0, L, bone in ((31, 44, 15, "skull"), (27, 46, 19, "neck"), (22, 48, 22, "neck"), (17, 50, 24, "neck"),
                            (11, 50, 22, "chest"), (5, 48, 18, "chest"), (-1, 46, 13, "chest")):
        tufts(b, bone, (0, y0 + 2, z0), (0, .75, -1), 13, 175, L * 1.3, 5, (0, 0, 1), z0, tint=True)

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

    def run(name, parent, a, c, X, U, sizes, open_deg, top):
        D = unit([c[i] - a[i] for i in range(3)])
        b.bone(name, parent, a)
        n = len(sizes)
        for k, L in enumerate(sizes):
            f = (k + .3) / n
            p = [a[i] + (c[i] - a[i]) * f for i in range(3)]
            base = add(add(p, X, 1.4), U, top)
            out = unit(add(add(D, U, .95), X, .25))
            b.petal(name, base, out, X, L, L * .82)
            b.petal(name, add(base, X, .7), out, X, L * .58, L * .44, kind="bud")      # inner petal
        b.frames[name] = splay(D, X, U, open_deg)

    up = [0, 1, 0]
    for s, side in ((1, "l"), (-1, "r")):
        X = [s, 0, 0]
        # one dorsal row from the shoulders to the tail tip: petals point back-and-up and overlap
        run(f"shell_b_{side}", "body", BACK[0], BACK[-1], X, up, [9, 10, 11], 50, 1.0)
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
            run(f"shell_t{i + 1}_{side}", f"tail_{i + 1}", TAIL[i], TAIL[i + 1], X, U, sizes[i], 50, widths[i] * .35)
    # the tail ends in a small closed fan
    tip, prev = TAIL[-1], TAIL[-2]
    D = unit([tip[i] - prev[i] for i in range(3)])
    for k, sx in enumerate((-.45, 0, .45)):
        b.petal("tail_5", add(tip, D, -3), unit(add(D, [1, 0, 0], sx)), [1, 0, 0] if sx == 0 else cross(D, [0, 0, 1]), 13 - 2 * abs(sx) * 2, 11)
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


def bake(b, name, length, keys, loop="once", base=None):
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
        frames.append((t, merged(base, pose)))
    bones = {nm for nm, _, _ in b.bones}
    tracks = {}
    for t, pose in frames:
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
            e = matrix_euler(axis_angle(axis, deg * shells + math.copysign(jitter, deg)))
            tracks.setdefault(shell, []).append((t, (round(-e[0], 3), round(e[1], 3), round(e[2], 3))))
    b.m.anim(name, length, tracks, loop)


def wave(t, period, phase=0.0):
    return math.sin((t / period + phase) * 2 * math.pi)


def looped(length, fn):
    """Loop keys sampled from fn(t) every 0.1 s (smoothstep between them)."""
    ts = [round(i * .1, 3) for i in range(int(round(length / .1)) + 1)]
    return [(t, fn(t), "io") for t in ts]


def idle_pose(t, L=2.4, enraged=False):
    w = wave(t, L)
    lag = lambda k: wave(t, L, -.12 * k)
    p = {"chest": (1.2 * w, 0, 0), "neck": (-1.5 * w, 1.5 * wave(t, L, .25), 0), "skull": (1.5 * lag(2), 0, 0),
         "jaw": (1 + w, 0, 0), "pos": (0, -.5 + .5 * w, 0)}
    for k in range(1, 6):
        p[f"tail_{k}"] = (1.6 * lag(k), 3.0 * wave(t, L, -.15 * k), 0)
    if enraged:
        p["neck"] = (6 - 1.5 * w, 2 * wave(t, L, .25), 0)
        p["skull"] = (4 + 2 * lag(2), 0, 0)
        p["jaw"] = (6 + 2 * w, 0, 0)
        p["shells"] = 1.0
        p["tremble"] = 1.5
    return p


def walk_pose(t, L=1.2, enraged=False):
    ph = t / L * 2 * math.pi
    s1, c1 = math.sin(ph), math.cos(ph)
    s2 = math.sin(ph + math.pi)
    p = {"pos": (0, -1.0 * abs(math.sin(ph)), 0), "chest": (1.5 * c1, 2 * s1, 0), "neck": (2 * math.sin(2 * ph), -2 * s1, 0),
         "skull": (-1.5 * math.sin(2 * ph), 0, 0)}
    for side, s in (("l", s1), ("r", s2)):
        lift = max(0.0, math.cos(ph if side == "l" else ph + math.pi))
        p[f"arm_{side}"] = (16 * s, 0, 0)
        p[f"forearm_{side}"] = (-22 * lift, 0, 0)
        p[f"hand_{side}"] = (14 * lift - 6 * s, 0, 0)
    for side, s in (("l", s2), ("r", s1)):          # diagonal gait: right hind with left fore
        lift = max(0.0, math.cos(ph + math.pi if side == "l" else ph))
        p[f"thigh_{side}"] = (-14 * s, 0, 0)
        p[f"shin_{side}"] = (12 * lift, 0, 0)
        p[f"foot_{side}"] = (-10 * lift + 4 * s, 0, 0)
    for k in range(1, 6):
        p[f"tail_{k}"] = (2 * math.sin(2 * ph - .5 * k), 4 * math.sin(ph - .4 * k), 0)
    if enraged:
        p["neck"] = (6 + p["neck"][0], p["neck"][1], 0)
        p["shells"] = 1.0
        p["tremble"] = 1.0
    return p


# Ability clips: (id, telegraph s, recovery s). Length = telegraph + recovery, impact pose at the telegraph.
ABILITIES = {"sakura-claw": (1.2, 1.0), "sakura-tail": (1.5, 1.1), "sakura-scatter": (1.7, .9), "sakura-bloom": (2.0, 1.2)}
CALM = {}


def claw_keys(hit, end, shells):
    """桜爪の叩きつけ: rear the right forearm high, shiver, slam it down, shake, recover."""
    up = {"arm_r": (-75, 0, -8), "forearm_r": (-35, 0, 0), "hand_r": (-20, 0, 0), "chest": (-10, 0, 6), "pos": (0, 2, -1),
          "neck": (-8, -10, 0), "skull": (-6, 0, 0), "jaw": (14, 0, 0), "arm_l": (-6, 0, 0), "tail_1": (8, 0, 0), "tail_2": (6, 0, 0),
          "shells": shells, "tremble": 0}
    shake = dict(up, tremble=3.0, chest=(-12, 0, 7))
    slam = {"arm_r": (14, 0, 2), "forearm_r": (4, 0, 0), "hand_r": (-6, 0, 0), "chest": (10, 0, -4), "pos": (0, -2.5, 2),
            "neck": (12, -4, 0), "skull": (8, 0, 0), "jaw": (20, 0, 0), "arm_l": (8, 0, 0), "tail_1": (-6, 0, 0), "tail_2": (-4, 0, 0),
            "shells": shells + .25, "tremble": 2.0}
    return [(0, dict(CALM, shells=shells), "io"), (hit * .7, up, "out"), (hit - .1, shake, "io"), (hit, slam, "in"),
            (hit + .25, dict(slam, tremble=0, shells=shells), "out"), (end, dict(CALM, shells=shells), "io")]


def tail_keys(hit, end, shells):
    """花尾の振り下ろし: crouch with the tail pulled low behind, then whip it over the back onto the ground ahead."""
    wind = {"chest": (-10, 0, 0), "pos": (0, -2, -2), "neck": (-6, 0, 0), "skull": (-4, 0, 0), "jaw": (8, 0, 0),
            "arm_l": (-8, 0, 0), "arm_r": (-8, 0, 0), "thigh_l": (10, 0, 0), "thigh_r": (10, 0, 0),
            "tail_1": (-26, 0, 0), "tail_2": (-14, 0, 0), "tail_3": (-10, 0, 0), "tail_4": (-6, 0, 0), "tail_5": (-6, 0, 0),
            "shells": shells, "tremble": 0}
    coil = dict(wind, tremble=2.0, tail_1=(-30, 0, 0))
    whip = {"chest": (16, 0, 0), "pos": (0, -3, 3), "neck": (14, 0, 0), "skull": (6, 0, 0), "jaw": (4, 0, 0),
            "arm_l": (10, 0, 0), "arm_r": (10, 0, 0), "thigh_l": (-18, 0, 0), "thigh_r": (-18, 0, 0), "shin_l": (10, 0, 0), "shin_r": (10, 0, 0),
            "tail_1": (58, 0, 0), "tail_2": (16, 0, 0), "tail_3": (-6, 0, 0), "tail_4": (-18, 0, 0), "tail_5": (-12, 0, 0),
            "shells": shells + .4, "tremble": 2.5}
    return [(0, dict(CALM, shells=shells), "io"), (hit * .75, wind, "io"), (hit - .12, coil, "io"), (hit, whip, "in"),
            (hit + .3, dict(whip, tremble=0), "out"), (end, dict(CALM, shells=shells), "io")]


def scatter_keys(hit, end, shells):
    """散華: the petals open wide and shiver while the tail rises, then a lunge flings them out in a fan."""
    gather = {"chest": (4, 0, 0), "pos": (0, -2, -1), "neck": (10, 0, 0), "skull": (6, 0, 0), "jaw": (10, 0, 0),
              "tail_1": (18, 0, 0), "tail_2": (10, 0, 0), "thigh_l": (6, 0, 0), "thigh_r": (6, 0, 0), "shells": 1.25, "tremble": 4.0}
    fling = {"chest": (10, 0, 0), "pos": (0, -1, 4), "neck": (2, 0, 0), "skull": (-10, 0, 0), "jaw": (34, 0, 0),
             "arm_l": (-14, 0, 0), "arm_r": (-14, 0, 0), "tail_1": (34, 0, 0), "tail_2": (22, 0, 0), "tail_3": (14, 0, 0),
             "shells": .35, "tremble": 0}
    return [(0, dict(CALM, shells=shells), "io"), (hit * .8, gather, "io"), (hit - .08, dict(gather, shells=1.35, tremble=6.0), "io"),
            (hit, fling, "in"), (hit + .3, dict(fling, jaw=(20, 0, 0)), "out"), (end, dict(CALM, shells=shells), "io")]


def bloom_keys(hit, end, shells):
    """花嵐 (enraged only): rear up on the hind legs with every petal flared, then crash down and burst."""
    rear = {"body": (-26, 0, 0), "pos": (0, 4, -3), "arm_l": (-50, 0, 0), "arm_r": (-46, 0, 0), "forearm_l": (-30, 0, 0), "forearm_r": (-34, 0, 0),
            "thigh_l": (20, 0, 0), "thigh_r": (20, 0, 0), "shin_l": (-14, 0, 0), "shin_r": (-14, 0, 0), "foot_l": (6, 0, 0), "foot_r": (6, 0, 0),
            "neck": (-14, 0, 0), "skull": (-14, 0, 0), "jaw": (30, 0, 0), "tail_1": (-14, 0, 0), "tail_2": (-6, 0, 0),
            "shells": 1.35, "tremble": 3.0}
    crash = {"body": (10, 0, 0), "pos": (0, -3, 3), "arm_l": (12, 0, 0), "arm_r": (12, 0, 0), "neck": (14, 0, 0), "skull": (8, 0, 0),
             "jaw": (24, 0, 0), "tail_1": (10, 0, 0), "shells": .5, "tremble": 0}
    return [(0, dict(CALM, shells=shells), "io"), (hit * .7, rear, "out"), (hit - .15, dict(rear, tremble=6.0, shells=1.45), "io"),
            (hit, crash, "in"), (hit + .35, dict(crash, shells=1.2), "out"), (end, dict(CALM, shells=shells), "io")]


def enrage_keys():
    """5 s (QuestEncounterCombat.ENRAGE_HOLD_MILLIS): crouch, shiver, rear up and roar as the petals burst open at 2.5 s."""
    crouch = {"pos": (0, -3, -1), "chest": (8, 0, 0), "neck": (14, 0, 0), "skull": (10, 0, 0), "tail_1": (-10, 0, 0), "shells": .1}
    shiver = dict(crouch, tremble=5.0, shells=.3)
    rear = {"body": (-24, 0, 0), "pos": (0, 4, -3), "arm_l": (-44, 0, 0), "arm_r": (-44, 0, 0), "forearm_l": (-26, 0, 0), "forearm_r": (-26, 0, 0),
            "thigh_l": (18, 0, 0), "thigh_r": (18, 0, 0), "neck": (-26, 0, 0), "skull": (-18, 0, 0), "jaw": (36, 0, 0),
            "tail_1": (-10, 0, 0), "shells": .3, "tremble": 2.0}
    burst = dict(rear, shells=1.4, tremble=4.0)
    land = {"body": (6, 0, 0), "pos": (0, -2, 2), "arm_l": (8, 0, 0), "arm_r": (8, 0, 0), "neck": (10, 0, 0), "skull": (4, 0, 0),
            "jaw": (14, 0, 0), "shells": 1.0, "tremble": 1.0}
    calm = dict(idle_pose(0, enraged=True))
    return [(0, {}, "io"), (.6, crouch, "io"), (1.6, shiver, "io"), (2.3, rear, "out"), (2.5, burst, "in"),
            (3.9, dict(burst, tremble=2.0), "io"), (4.3, land, "in"), (5.0, calm, "io")]


def death_keys(shells):
    stagger = {"pos": (0, -1, -2), "chest": (-6, 0, 8), "neck": (-10, 0, 0), "jaw": (30, 0, 0), "shells": shells, "tremble": 3.0}
    fall = {"pos": (0, -14, 0), "body": (0, 0, 72), "neck": (10, 0, 0), "skull": (12, 0, 0), "jaw": (22, 0, 0),
            "arm_l": (20, 0, 0), "arm_r": (-24, 0, 0), "thigh_l": (-12, 0, 0), "thigh_r": (16, 0, 0),
            "tail_1": (-14, 0, 0), "tail_2": (-12, 0, 0), "tail_3": (-10, 0, 0), "shells": shells * .3, "tremble": 0}
    return [(0, {"shells": shells}, "io"), (.6, stagger, "io"), (1.5, fall, "in"), (1.8, dict(fall, pos=(0, -13, 0)), "out"),
            (2.4, dict(fall, shells=0.0), "io")]


def animate(m, b):
    bake(b, "idle", 2.4, looped(2.4, lambda t: idle_pose(t)), "loop")
    bake(b, "idle_enraged", 2.4, looped(2.4, lambda t: idle_pose(t, enraged=True)), "loop")
    bake(b, "walk", 1.2, looped(1.2, lambda t: walk_pose(t)), "loop")
    bake(b, "walk_enraged", 1.2, looped(1.2, lambda t: walk_pose(t, enraged=True)), "loop")
    for name, keys in (("sakura-claw", claw_keys), ("sakura-tail", tail_keys), ("sakura-scatter", scatter_keys), ("sakura-bloom", bloom_keys)):
        hit, rec = ABILITIES[name]
        if name != "sakura-bloom":
            bake(b, name, hit + rec, keys(hit, hit + rec, 0.0))
        bake(b, f"{name}_enraged" if name != "sakura-bloom" else name, hit + rec, keys(hit, hit + rec, 1.0))
    bake(b, "enrage", 5.0, enrage_keys())
    bake(b, "death", 2.4, death_keys(0.0))
    bake(b, "death_enraged", 2.4, death_keys(1.0))


def open_pose(b, f=1.0):
    """Raw Blockbench (editor) euler per shell bone at opening f, for previews."""
    return {name: matrix_euler(axis_angle(axis, deg * f)) for name, (axis, deg) in b.frames.items()}


def main():
    b = build()
    outliner = b.outliner()
    animate(b.m, b)
    out = Path(__import__("os").environ.get("SAKURA_OUT", ROOT / "assets/bosses/sakura_fang.bbmodel"))
    data = b.m.write(out, outliner)
    print(f"{out}: {len(data['elements'])} cubes, {len(b.bones)} bones, {len(data['animations'])} animations")
    return b


if __name__ == "__main__":
    main()
