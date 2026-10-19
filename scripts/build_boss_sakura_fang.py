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


def box_painter(mat, seed=0):
    """Top-lit ramp down each side, overlapping 6x4 scales where only some catch a light lip
    (the shade wyrm hand-painted look), bright top, shadowed bottom."""
    ramp = RAMPS[mat]
    scaled = mat in ("hide", "plate")

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


def flat_painter(color):
    def make(side, w, h):
        return (lambda x, y, w, h: color + (255,)), ("flat", color, w, h)
    return make


def fur_painter(seed=0):
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
            return pal[t] + (255,)
        return paint, ("fur", seed % 9, side, w, h)
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

    def fur(self, bone, root, tip_dir, width_dir, L, W, seed):
        """Fur plate: root edge at `root`, strands hang toward tip_dir, plate spans width_dir."""
        d = unit(tip_dir)
        R = basis([-c for c in d], width_dir)
        self.oriented(bone, f"{bone}_fur{len(self.items[bone])}", add(root, d, L / 2), [.05, L / 2, W / 2], R, fur_painter(seed))

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


def tufts(b, bone, root, direction, n, spread, L, W, axis, seed):
    """n fur plates fanned around `direction`, rotating about `axis`."""
    d0, ax = unit(direction), unit(axis)
    for k in range(n):
        t = (k / (n - 1) - .5) if n > 1 else 0.0
        d = mat_vec(axis_angle(ax, spread * t), d0)
        b.fur(bone, root, d, cross(ax, d), L * (1 - .3 * abs(t) * 2), W, seed + k)


# --- the beast --------------------------------------------------------------------------------------
TAIL = [(0, 34, -30), (0, 40, -42), (0, 51, -51), (0, 64, -53), (0, 75, -48), (0, 81, -39)]
BACK = [(0, 52, 7), (0, 50, -2), (0, 45, -12), (0, 43, -21)]


def build():
    b = Builder()
    # skeleton: pivots at the joints
    b.bone("body", None, (0, 34, -10))
    b.bone("chest", "body", (0, 38, 4))
    b.bone("neck", "chest", (0, 42, 14))
    b.bone("skull", "neck", (0, 39, 27))
    b.bone("jaw", "skull", (0, 31, 31))
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
        b.seg(f"forearm_{side}", (17.5 * s, 21, 5), (18 * s, 7, 15), 3, 10, "plate", seed=7)   # forearm shell
        b.seg(f"arm_{side}", (17 * s, 46, 13), (19 * s, 30, 9), 3, 12, "plate", seed=8)        # shoulder shell
        b.box(f"hand_{side}", (15 * s - 7, 0, 12), (15 * s + 7, 5, 27), "hide", seed=9)
        for k in (-4.4, 0, 4.4):
            b.seg(f"hand_{side}", (15 * s + k, 2.8, 25.5), (15 * s + k, .2, 31), 2.2, 2.2, "claw")
        tufts(b, f"forearm_{side}", (15 * s, 22, 3), (s * .25, .1, -1), 4, 40, 10, 3.5, (1, 0, 0), 60 + s)
        # short crouched hind legs
        b.seg(f"thigh_{side}", (10 * s, 35, -25), (12 * s, 18, -16), 10, 12, "hide", seed=10)
        b.seg(f"shin_{side}", (12 * s, 19, -16), (12 * s, 8, -30), 6, 6, "hide", seed=11)
        b.seg(f"foot_{side}", (12 * s, 9, -30), (12 * s, 1, -24), 5.5, 5.5, "hide", seed=12)
        b.box(f"foot_{side}", (12 * s - 4.5, 0, -27), (12 * s + 4.5, 4, -17), "hide", seed=13)
        for k in (-3, 0, 3):
            b.seg(f"foot_{side}", (12 * s + k, 2, -17.5), (12 * s + k, .2, -14), 1.6, 1.6, "claw")
        tufts(b, f"thigh_{side}", (11 * s, 33, -27), (s * .3, .1, -1), 4, 40, 9, 3.5, (1, 0, 0), 70 + s)

    # neck and head, carried low and forward
    b.seg("neck", (0, 43, 14), (0, 39, 28), 16, 16, "hide", seed=14)
    b.seg("skull", (0, 40, 26), (0, 38, 37), 14, 12, "hide", z_hint=(0, 1, 0), seed=15)
    b.seg("skull", (0, 38, 36), (0, 35.5, 44), 10.5, 8.5, "hide", z_hint=(0, 1, 0), seed=16)    # muzzle
    b.seg("skull", (0, 36, 43), (0, 34, 51), 7.5, 6.5, "hide", z_hint=(0, 1, 0), seed=19)       # nose
    b.seg("skull", (0, 42.5, 28), (0, 39.5, 45), 11, 3, "plate", z_hint=(0, 1, 0), seed=17)    # brow shell
    b.seg("skull", (0, 42.5, 28), (0, 44, 20), 9, 2.5, "plate", z_hint=(0, 1, 0), seed=21)     # crest back
    b.seg("jaw", (0, 31, 30), (0, 26.5, 46), 9, 4, "hide", z_hint=(0, 1, 0), seed=18)          # jaw, a little open
    b.seg("jaw", (0, 31.4, 32), (0, 28.6, 45), 7, 1, "mouth", z_hint=(0, 1, 0))
    for s in (1, -1):
        b.seg("skull", (2.8 * s, 34.5, 48), (2.8 * s, 30.5, 48.5), 1.4, 1.4, "claw")             # upper fangs
        b.seg("jaw", (2.6 * s, 28.5, 44), (2.6 * s, 31.5, 44.5), 1.2, 1.2, "claw")              # lower fangs
    for s in (1, -1):
        b.box("skull", (5.4 * s - .7, 38.4, 39), (5.4 * s + .7, 39.6, 41.6), flat_painter(EYE), name="glow_eye")
        b.seg("skull", (5 * s, 42, 35), (8.5 * s, 48, 26), 3.4, 3.4, "plate")                 # horns, swept back
        b.seg("skull", (8.5 * s, 48, 26), (9.5 * s, 51, 16), 2.4, 2.4, "plate")
        b.seg("skull", (9.5 * s, 51, 16), (9.8 * s, 52, 11), 1.4, 1.4, "claw")
        tufts(b, "skull", (6.5 * s, 35, 32), (s * .65, -.15, -.7), 4, 50, 10, 3.5, (s, 0, 0), 80 + s)  # cheek fur

    # mane: rows of pale fur over neck and shoulders, standing back and up
    for z0, y0, L, bone in ((31, 44, 15, "skull"), (25, 47, 20, "neck"), (18, 50, 23, "neck"), (11, 50, 21, "chest"), (4, 48, 16, "chest")):
        tufts(b, bone, (0, y0 + 2, z0), (0, .75, -1), 11, 175, L * 1.3, 6, (0, 0, 1), z0)

    # tail: thick, curling up over the back
    widths = [(13, 11), (12, 10), (11, 9), (9.5, 8), (8, 7)]
    for i in range(len(TAIL) - 1):
        bone = f"tail_{i + 1}"
        w, d = widths[i]
        b.seg(bone, TAIL[i], TAIL[i + 1], w, d, "hide", seed=20 + i)
    # the closed bud at the tip
    tip, prev = TAIL[-1], TAIL[-2]
    t = unit([tip[i] - prev[i] for i in range(3)])
    for k in range(5):
        a = k * 72
        side = mat_vec(axis_angle(t, a), [1, 0, 0])
        b.petal("tail_5", add(tip, t, -2), add(t, side, .28), side, 10, 8, kind="bud")

    # sakura shells: one bone per petal (left/right) so each swings out about its own base
    def shells(points, sizes, parent_of, tag):
        for k, (p, L) in enumerate(zip(points, sizes)):
            parent, T = parent_of(k)
            U = unit(add([0, 1, 0], T, -dot([0, 1, 0], T)))
            if abs(dot([0, 1, 0], T)) > .8:                       # the tail tip turns over: up is the old "back"
                U = unit(cross([1, 0, 0], T))
            for s, side in ((1, "l"), (-1, "r")):
                X = [s, 0, 0]
                base = add(add(p, X, 1.6), U, 1.0)
                closed_out = unit(add(add([-c for c in T], U, .14), X, .3))
                closed_n = unit(add(U, X, .25))
                open_out = unit(add(add(X, U, .6), T, -.2))
                name = f"shell_{tag}{k + 1}_{side}"
                b.bone(name, parent, base)
                b.petal(name, base, closed_out, closed_n, L, L * .74)
                hinge = cross(closed_out, open_out)               # lift the petal off the body
                angle = math.degrees(math.acos(max(-1, min(1, dot(closed_out, open_out)))))
                b.frames[name] = axis_angle(hinge, angle)

    BT = unit([BACK[-1][i] - BACK[0][i] for i in range(3)])
    shells(BACK, [12, 14, 15, 14], lambda k: ("chest" if k < 2 else "body", [-c for c in BT]), "b")
    tail_pts, tail_parent = [], []
    for i in range(len(TAIL) - 1):
        for f in (.3, .78):
            tail_pts.append([TAIL[i][j] + (TAIL[i + 1][j] - TAIL[i][j]) * f for j in range(3)])
            tail_parent.append((f"tail_{i + 1}", unit([TAIL[i][j] - TAIL[i + 1][j] for j in range(3)])))
    shells(tail_pts, [14, 15, 17, 18, 19, 19, 18, 17, 15, 12], lambda k: tail_parent[k], "t")
    return b


def animate(m, b):
    """Placeholder clips that keep the server contract; the motion pass comes after the shape OK."""
    zero = (0, 0, 0)
    breathe = {"body": [(0, zero, "position"), (1.0, (0, -0.6, 0), "position"), (2.0, zero, "position")]}
    bob = {"body": [(t, (0, -1 if t in (0.3, 0.9) else 0, 0), "position") for t in (0, 0.3, 0.6, 0.9, 1.2)]}
    m.anim("idle", 2.0, breathe, "loop")
    m.anim("walk", 1.2, bob, "loop")


def open_pose(b):
    """Raw Blockbench euler per shell bone for the opened (enraged) shells, for previews."""
    return {name: matrix_euler(R) for name, R in b.frames.items()}


def main():
    b = build()
    outliner = b.outliner()
    animate(b.m, b)
    out = ROOT / "assets/bosses/sakura_fang.bbmodel"
    data = b.m.write(out, outliner)
    print(f"{out.relative_to(ROOT)}: {len(data['elements'])} cubes, {len(b.bones)} bones, {len(data['animations'])} animations")
    return b


if __name__ == "__main__":
    main()
