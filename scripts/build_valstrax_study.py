"""天彗龍（バルファルク）の習作モデル。調べたことと物差しは docs/development/valstrax-study.md。

    python scripts/build_valstrax_study.py   # writes assets/model-lab/valstrax/valstrax.bbmodel

A study only: Valstrax is Capcom's design, so this is not a game mob and it is kept out of assets/bosses/ (the build
converts only that folder). No official model data, texture or anyone else's file is used.

How it is made (the reference video fits boxes to a real 3D model and bakes that model's texture onto them; this does
the same from a solid of our own):
1. SOLID  - the body is a soft union of tapered round bars (PRIMS), written from public pictures and the video frames.
2. SLICES - each part is cut across its own axis into thin slices, each slice into a few bands, each band into one box
            as wide as the solid there. Round forms come out as fine stair steps, the way the reference's do.
3. BLADES - wings, spikes and claws are long turned boxes placed along their own lines.
4. BAKE   - colour is not painted per box. Every texel asks one material for its colour at its place on the solid
            (tone, scales, plate seams, how deep in a crease it sits, the red hide), so patterns run on across boxes.

Front is +Z, +X is the model's left, 16 px = 1 block, ground y = 0. Every bone rests at rotation 0 and its cubes are
placed in world space, so a bone turns about its pivot (the joint). The rest pose is the folded-wing idle.
"""
import io
import math
import sys

import numpy as np
from PIL import Image

import boss_models
from boss_models import Model, ROOT

OUT = ROOT / "assets/model-lab/valstrax/valstrax.bbmodel"
ATLAS = 2048
TEX = 3            # texels per model px on faces that show
SELF_LIT = 251     # alpha mark: drawn self-lit (scripts/shaders/item.fsh)


# --- math -------------------------------------------------------------------------------------------
def unit(v):
    v = np.array(v, float)
    return v / (np.linalg.norm(v) or 1.0)


def RX(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def RY(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def RZ(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_of(R):
    y = math.asin(max(-1.0, min(1.0, -R[2][0])))
    x = math.atan2(R[2][1], R[2][2])
    z = math.atan2(R[1][0], R[0][0])
    return tuple(round(math.degrees(v), 3) for v in (x, y, z))


def frame_zx(zdir, xhint=(1, 0, 0)):
    """Rotation whose local Z is zdir and whose local X is as near xhint as it can be."""
    z = unit(zdir)
    x = np.array(xhint, float)
    x = x - (x @ z) * z
    if np.linalg.norm(x) < 1e-6:
        x = np.array([0, 1.0, 0]) - z[1] * z
    x = unit(x)
    return np.column_stack([x, np.cross(z, x), z])


MIR = np.diag([-1.0, 1.0, 1.0])
V = lambda *a: np.array(a, float)


def smooth(f):
    f = np.clip(f, 0.0, 1.0)
    return f * f * (3 - 2 * f)


def _hash(i, j, k, seed):
    n = (i * 374761393 + j * 668265263 + k * 2147483647 + seed * 974711) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def noise3(P, scale, seed=0):
    """Smooth value noise in 0..1 over points P (N, 3)."""
    Q = P / scale
    I = np.floor(Q).astype(np.int64)
    F = Q - I
    F = F * F * (3 - 2 * F)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (F[:, 0] if dx else 1 - F[:, 0]) * (F[:, 1] if dy else 1 - F[:, 1]) * (F[:, 2] if dz else 1 - F[:, 2])
                out = out + w * _hash(I[:, 0] + dx, I[:, 1] + dy, I[:, 2] + dz, seed)
    return out


# --- 1. the solid ------------------------------------------------------------------------------------
PRIMS = {}   # part -> [(a, b, r0, r1, sx, sy)]


def bar(part, a, b, r0, r1=None, sx=1.0, sy=1.0, sym=False):
    """A round bar a -> b, radius r0 -> r1, its section stretched sx wide and sy tall."""
    a, b = np.array(a, float), np.array(b, float)
    PRIMS.setdefault(part, []).append((a, b, r0, r0 if r1 is None else r1, sx, sy))
    if sym:
        PRIMS.setdefault(other(part), []).append((MIR @ a, MIR @ b, r0, r0 if r1 is None else r1, sx, sy))


def chain(part, pts, sx=1.0, sy=1.0, sym=False):
    """pts: (x, y, z, r) along a line."""
    for p, q in zip(pts, pts[1:]):
        bar(part, p[:3], q[:3], p[3], q[3], sx, sy, sym)


def prim_d(P, pr):
    a, b, r0, r1, sx, sy = pr
    ab = b - a
    t = np.clip(((P - a) @ ab) / max(1e-9, ab @ ab), 0, 1)
    q = P - (a + t[:, None] * ab)
    return np.sqrt((q[:, 0] / sx) ** 2 + (q[:, 1] / sy) ** 2 + q[:, 2] ** 2) - (r0 + (r1 - r0) * t)


def sdf(parts, P, k=2.2):
    """Distance to the soft union of the parts' bars (negative inside)."""
    d = None
    for part in parts:
        for pr in PRIMS[part]:
            e = prim_d(P, pr)
            if d is None:
                d = e
            else:
                h = np.clip(.5 + .5 * (e - d) / k, 0, 1)
                d = e + (d - e) * h - k * h * (1 - h)
    return d


def sdf_all(P):
    d = np.full(len(P), 1e9)
    for prims in PRIMS.values():
        for pr in prims:
            d = np.minimum(d, prim_d(P, pr))
    return d


# anatomy (world space, px). The head is written in its own frame, nose along +Z.
HEAD_O, HEAD_TIP = V(0, 21.4, 61), V(0, 9, 91)
HZ = .8                       # the head's length against the first draft


def lift(z):
    """How much higher the back sits at z than in the first draft (the chest was raised)."""
    return 7.0 * float(smooth((z + 10) / 34))
HEAD_R = frame_zx(HEAD_TIP - HEAD_O)
JAW_O = HEAD_O + HEAD_R @ V(0, -4.6, 2.5)
JAW_R = HEAD_R @ RX(3)
SPINE = [(0, 26.5, -42, 8.2), (0, 28.2, -34, 10.6), (0, 30, -22, 13), (0, 32, -6, 15), (0, 34.6, 10, 17),
         (0, 36.6, 24, 16.4), (0, 37, 34, 12)]
NECK = [(0, 37, 34, 12), (0, 33.5, 44, 10.4), (0, 27.5, 54, 8.8), (0, 21.6, 61, 7.4)]
TAIL = [(0, 26.5, -42, 8.2), (0, 24.6, -56, 6.4), (0, 22.8, -72, 5.1), (0, 21.4, -88, 4.1), (0, 20.4, -104, 3.2),
        (0, 19.8, -120, 2.4), (0, 19.5, -136, 1.4)]
FL = (V(17, 34, 25), V(25, 19, 15), V(22.5, 6.5, 25))
HL = (V(14, 27, -28), V(23.5, 17, -18), V(21, 10.5, -33), V(21, 4, -27))
SHOULDER, ELBOW, WRIST = V(11, 45, 14), V(20, 66, -4), V(24, 78, 38)


def solid():
    chain("torso", SPINE, sx=1.08, sy=.93)
    for sx in (1, -1):
        bar("torso", (sx * 11, 40, 25), (sx * 13, 37, 12), 9.6, 8, 1, .9)            # shoulder mass
        bar("torso", (sx * 9, 32, -20), (sx * 11, 31, -31), 8, 7.4, 1, .9)           # hip mass
    bar("torso", (0, 47, 22), (0, 38.5, -30), 3.4, 2.4, .8, 1)
    for sx in (1, -1):
        bar("torso", (sx * 9, 46, 18), (sx * 11, 40, 2), 7, 5.4)                       # wing-root mass                        # back ridge
    chain("neck", NECK, sx=.94, sy=1.0)
    bar("neck", (0, 47.5, 34), (0, 42.5, 44), 2.8, 2.6, 1.3, 1)                       # the raised strip of red hide
    bar("neck", (0, 42.5, 44), (0, 35, 54), 2.6, 2.4, 1.3, 1)
    bar("neck", (0, 35, 54), (0, 28, 61), 2.4, 2.2, 1.3, 1)                           # the raised strip of red hide
    chain("tail", TAIL, sx=1.0, sy=.94)
    H = lambda x, y, z: HEAD_O + HEAD_R @ V(x, y, z * HZ)
    bar("skull", H(0, .9, -1), H(0, -.1, 13), 7.4, 6.0, 1.08, .9)
    bar("skull", H(0, -.1, 12), H(0, -2.3, 32), 5.8, 1.7, 1.0, .74)
    bar("skull", H(0, 2.6, 13), H(0, -.9, 30.5), 1.5, .8, 1.2, 1)                     # nasal ridge
    for sx in (1, -1):
        bar("skull", H(sx * 3.8, 3.4, 6), H(sx * 2.7, 1.5, 19), 2.0, 1.0)             # brow
        bar("skull", H(sx * 5.4, -1.6, 2), H(sx * 3.5, -2.4, 15), 2.6, 1.4)           # cheek
    J = lambda x, y, z: JAW_O + JAW_R @ V(x, y, z * HZ)
    bar("jaw", J(0, -1.2, 0), J(0, -1.0, 26.5), 3.3, 1.1, 1.4, .5)
    S, E, W = FL
    chain("fl_upper_l", [(*S, 7.4), (*((S + E) / 2 + V(1, 0, -1)), 6.6), (*E, 5.0)], sym=True)
    chain("fl_lower_l", [(*E, 5.0), (*((E + W) / 2 + V(0, 0, -1)), 4.4), (*W, 3.4)], sym=True)
    bar("fl_paw_l", W + V(0, -2.6, .5), W + V(0, -3.4, 6), 3.6, 3.4, 1.25, .62, sym=True)
    Hp, K, Hk, A = HL
    chain("hl_thigh_l", [(*Hp, 8.8), (*((Hp + K) / 2 + V(0, 1, -2)), 8.0), (*K, 5.4)], sx=.9, sym=True)
    chain("hl_shin_l", [(*K, 5.4), (*((K + Hk) / 2 + V(0, 0, 1)), 4.4), (*Hk, 3.4)], sym=True)
    bar("hl_foot_l", Hk, A, 3.4, 3.0, sym=True)
    bar("hl_foot_l", A + V(0, -1, 0), A + V(0, -1.6, 6), 3.3, 3.2, 1.25, .62, sym=True)
    chain("wing_arm_l", [(*SHOULDER, 6.4), (*((SHOULDER + ELBOW) / 2), 5.2), (*ELBOW, 4.2)], sym=True)
    chain("wing_fore_l", [(*ELBOW, 4.2), (*((ELBOW + WRIST) / 2 + V(0, 1, 0)), 4.4), (*WRIST, 4.6)], sym=True)


# --- builder ----------------------------------------------------------------------------------------
def other(name):
    return name[:-2] + "_r" if name and name.endswith("_l") else name


class Builder:
    def __init__(self):
        self.m = Model("valstrax", size=ATLAS)
        self.items = {}
        self.bones = []          # (name, parent, pivot)
        self.cubes = []          # (element, centre, R, size, ctx, mirrored)

    def bone(self, name, parent, pivot, sym=False):
        self.items[name] = []
        self.bones.append((name, parent, [round(float(v), 3) for v in pivot]))
        if sym:
            self.items[other(name)] = []
            self.bones.append((other(name), other(parent), [round(float(v), 3) for v in MIR @ np.array(pivot, float)]))
        return name

    def cube(self, bone, c, size, ctx, R=None, sym=False):
        c = np.array(c, float)
        R = np.eye(3) if R is None else R
        self._emit(bone, c, size, ctx, R, False)
        if sym:
            self._emit(other(bone), MIR @ c, size, ctx, MIR @ R @ MIR, True)

    def _emit(self, bone, c, size, ctx, R, mirrored):
        size = [max(.4, float(v)) for v in size]
        ident = self.m.uid()
        glow = ctx.get("kind") in ("ember", "eye")
        el = {"name": ("glow_" if glow else "") + f"{bone}_{len(self.items[bone])}", "box_uv": False, "rescale": False,
              "locked": False, "from": [round(float(c[i] - size[i] / 2), 3) for i in range(3)],
              "to": [round(float(c[i] + size[i] / 2), 3) for i in range(3)], "autouv": 0, "color": 0,
              "origin": [round(float(v), 3) for v in c], "faces": {}, "type": "cube", "uuid": ident}
        if not np.allclose(R, np.eye(3), atol=1e-6):
            el["rotation"] = list(euler_of(R))
        self.m.elements.append(el)
        self.items[bone].append(ident)
        self.cubes.append((el, c, R, size, ctx, mirrored))

    def outliner(self):
        made = {}
        for name, parent, pivot in reversed(self.bones):
            kids = [made[c] for c, p, _ in self.bones if p == name]
            made[name] = self.m.bone(name, pivot, self.items[name] + kids)
        return [made[n] for n, p, _ in self.bones if p is None]


class Fr:
    """A local frame: cubes are given in its coordinates."""

    def __init__(self, b, bone, o, R, sym=False):
        self.b, self.bone, self.o, self.R, self.sym = b, bone, np.array(o, float), R, sym

    def c(self, p, size, ctx, rot=None):
        self.b.cube(self.bone, self.o + self.R @ np.array(p, float), size, ctx,
                    self.R if rot is None else self.R @ rot, sym=self.sym)

    def pt(self, p):
        return self.o + self.R @ np.array(p, float)


# --- 2. slices ----------------------------------------------------------------------------------------
def slices(b, bone, parts, a, c, dz, band, half, ctx, sym=False, pad=(0.0, 0.0), xhint=(1, 0, 0), res=.4):
    """Cut the solid of `parts` across the line a -> c: slices dz thick, bands about `band` tall, one box per run."""
    a, c = np.array(a, float), np.array(c, float)
    R = frame_zx(c - a, xhint)
    L = np.linalg.norm(c - a)
    g = np.arange(-half, half, res) + res / 2
    X, Y = np.meshgrid(g, g)
    n = max(1, int(round((L + pad[0] + pad[1]) / dz)))
    dz = (L + pad[0] + pad[1]) / n
    for k in range(n):
        z = -pad[0] + (k + .5) * dz
        P = a + (np.stack([X.ravel(), Y.ravel(), np.full(X.size, z)], 1)) @ R.T
        occ = (sdf(parts, P) < 0).reshape(X.shape)
        rows = np.where(occ.any(axis=1))[0]
        if not len(rows):
            continue
        r0, r1 = rows[0], rows[-1] + 1
        nb = max(1, int(round((r1 - r0) * res / band)))
        edges = np.linspace(r0, r1, nb + 1).round().astype(int)
        for e0, e1 in zip(edges, edges[1:]):
            if e1 <= e0:
                continue
            line = occ[e0:e1].sum(axis=0) >= max(1, (e1 - e0) // 2)      # the band's middle width, not its widest
            idx = np.where(line)[0]
            if not len(idx):
                continue
            for run in np.split(idx, np.where(np.diff(idx) > 1)[0] + 1):
                x0, x1 = g[run[0]] - res / 2, g[run[-1]] + res / 2
                y0, y1 = g[e0] - res / 2, g[e1 - 1] + res / 2
                ctr = a + R @ V((x0 + x1) / 2, (y0 + y1) / 2, z)
                b.cube(bone, ctr, (x1 - x0, y1 - y0, dz + .04), ctx, R, sym=sym)


# --- 3. blades, spikes, claws -------------------------------------------------------------------------
def spike(b, bone, base, direction, L, w0, d0, n=4, t0=.7, t1=.25, sym=False, xhint=(1, 0, 0), bend=0.0, kind="spike"):
    """A tapered stack of n boxes; bend (degrees per box) curls it toward local -X."""
    base = np.array(base, float)
    R = frame_zx(direction, xhint)
    f = Fr(b, bone, base, R, sym=sym)
    ctx = dict(kind=kind, o=base, d=R[:, 2], L=L, t0=t0, t1=t1)
    x, z, ang = 0.0, 0.0, 0.0
    for i in range(n):
        u = i / n
        w, d = max(.5, w0 * (1 - u) ** .85), max(.5, d0 * (1 - u) ** .85)
        ln = L / n
        rot = RY(-ang)
        f.c(V(x, 0, z) + rot @ V(0, 0, ln / 2), (w, d, ln + .6), ctx, rot=rot)
        end = V(x, 0, z) + rot @ V(0, 0, ln)
        x, z = end[0], end[2]
        ang += bend


def toe(f, x, z0, splay, long_=1.0):
    """A toe along the frame's +Z: knuckles in small steps, then a claw hooking down."""
    R0 = RY(splay)
    p = V(x, 0.0, z0)
    hide_ = dict(kind="hide", tone=.44, part="toe")
    for ln, w, pitch in ((2.4 * long_, 2.9, 4), (2.2 * long_, 2.7, 14), (2.0 * long_, 2.5, 26), (1.8 * long_, 2.2, 38)):
        rot = R0 @ RX(pitch)
        f.c(p + rot @ V(0, 0, ln / 2), (w, w * .95, ln + .4), hide_, rot=rot)
        p = p + rot @ V(0, 0, ln)
    ctx = dict(kind="claw", o=f.pt(p), d=f.R @ R0 @ RX(62) @ V(0, 0, 1), L=7.5)
    for ln, w, pitch in ((2.0, 2.0, 46), (1.9, 1.6, 58), (1.8, 1.2, 70), (1.6, .8, 82)):
        rot = R0 @ RX(pitch)
        f.c(p + rot @ V(0, 0, ln / 2), (w, w, ln + .3), ctx, rot=rot)
        p = p + rot @ V(0, 0, ln)


def knot(b, bone, c, r, seed, sym=True, n=12, tone=.14):
    """A joint: a cluster of small dark boxes, the way the reference shows the wing elbow."""
    c = np.array(c, float)
    ctx = dict(kind="hide", tone=tone, part="knot")
    for i in range(n):
        th, ph = _hash(i, seed, 1, 0) * math.tau, (_hash(i, seed, 2, 0) - .5) * math.pi * .9
        p = c + r * .6 * V(math.cos(th) * math.cos(ph), math.sin(ph), math.sin(th) * math.cos(ph))
        s = 1.6 + _hash(i, seed, 3, 0) * 1.4
        b.cube(bone, p, (s, s, s), ctx, R=RZ(_hash(i, seed, 5, 0) * 40) @ RX(_hash(i, seed, 6, 0) * 40), sym=sym)
    b.cube(bone, c, (r, r, r), ctx, sym=sym)


def finger(b, bone, o, direction, xhint, L, W, seed, out=-1):
    """One wing blade from its root: a solid slab that swells and then tapers to a spear point, a thick leading
    spine, three lapped cover plates, teeth stepping back along the trailing edge, and light under that edge.
    Local Z runs to the tip, local X to the trailing edge, `out` * local Y is the face that shows."""
    o = np.array(o, float)
    R = frame_zx(direction, xhint)
    f = Fr(b, bone, o, R, sym=True)
    ctx = dict(kind="blade", o=o, R=R, L=L, W=W, seed=seed, out=out)
    e = out
    width = lambda s: W * (.66 + .34 * float(smooth(s / .22))) * (1 - .9 * float(smooth((s - .26) / .74)))
    bend = math.radians(ARCH)

    def put(x, y, s0, s1, size_xy, c=ctx):
        """A box over s0..s1 of the blade's length, laid on its downward arc."""
        sm = (s0 + s1) / 2
        f.c((x, y - e * L * sm * sm * bend / 2, L * sm), (size_xy[0], size_xy[1], L * (s1 - s0) + .5), c, rot=RX(e * ARCH * sm))
    m = 9
    for i in range(m):                                       # the slab
        s0, s1 = i / m, (i + 1) / m
        w = width((s0 + s1) / 2)
        put(w / 2, 0, s0, s1, (w, 5.6 - 3.8 * s0))
        if 1 <= i <= m - 2:                                  # a tooth where this length of blade ends
            put(w * .82, e * .1, s1 - .01, s1 + .06, (w * .3, 2.0 - s0))
            put(w * .93, e * .1, s1 + .04, s1 + .1, (w * .12, 1.2 - .5 * s0))
    for i in range(m + 1):                                   # spine, tapering to the spear tip
        s0, s1 = 1.03 * i / (m + 1), 1.03 * (i + 1) / (m + 1)
        w, th = W * .2 * (1 - s0 / 1.03) ** .8 + .8, 6.8 * (1 - s0 * .8) + .6
        put(w / 2 - .7, e * .8, s0, s1, (w, th))
    for i, (z0, z1, w) in enumerate(((-8, 1.5, W * .5), (-15, -7, W * .32), (-21, -14, W * .16))):       # root spear
        f.c((w / 2 - .7 + i * .3, e * .3, (z0 + z1) / 2), (w, max(1.2, 4.2 - i * 1.2), z1 - z0), ctx)
    for i, (s0, s1) in enumerate(((.03, .34), (.26, .56), (.48, .76))):                                # cover plates
        w = width((s0 + s1) / 2) * .66
        put(w / 2 + .6, e * (2.6 - .7 * i), s0, s1, (w, 1.8))
        put(w * .36 + .6, e * (2.6 - .7 * i), s1 - .01, s1 + .06, (w * .5, 1.5))
    for i, (s0, s1) in enumerate(((.18, .32), (.38, .5), (.56, .64))):                                 # light under the edge
        w = width((s0 + s1) / 2)
        put(w * .82, -e * 1.4, s0, s1, (w * .26, .5), dict(kind="ember", seed=seed + i))


ARCH = 20.0     # degrees a blade curves down from root to tip
FINGERS = (  # bone, root offset from the wrist, direction, trailing-edge hint, length, width
    ("wing_f1_l", (0, 1, -2), (.02, .13, -1), (.9, -.44, 0), 160, 34),
    ("wing_f2_l", (4, -5, -8), (.25, -.04, -1), (.86, -.52, 0), 134, 31),
    ("wing_f3_l", (8, -10, -14), (.46, -.2, -1), (.8, -.6, 0), 104, 27),
)


# --- 4. bake ------------------------------------------------------------------------------------------
PAL_T = np.array([0.0, .2, .4, .6, .8, 1.0])
PAL_C = np.array([(16, 18, 28), (42, 50, 68), (80, 94, 120), (128, 144, 172), (184, 196, 218), (238, 242, 252)], float)
RED = np.array([(84, 34, 48), (136, 50, 66), (190, 40, 54), (240, 84, 72)], float)


def silver(t, tint):
    t = np.clip(t, 0, 1)
    c = np.stack([np.interp(t, PAL_T, PAL_C[:, i]) for i in range(3)], 1)
    k = (4 + 10 * (1 - np.abs(t - .55) * 1.6))[:, None]
    return c + tint[:, None] * k * np.array([1.0, -.35, .7])       # pearl: lavender one way, teal the other


def red(k):
    k = np.clip(k, 0, 1) * 3
    i = np.clip(np.floor(k).astype(int), 0, 2)
    f = (k - i)[:, None]
    return RED[i] * (1 - f) + RED[i + 1] * f


def shade(ctx, P, n, mirrored):
    """Colour of the points P (N, 3) of a face whose outward normal is n. Returns (N, 4) floats."""
    kind = ctx["kind"]
    N = len(P)
    a = np.full(N, 255.0)
    Q = P * np.array([-1.0, 1, 1]) if mirrored else P        # paint both sides from the left side's pattern
    nq = n * np.array([-1.0, 1, 1]) if mirrored else n
    if kind == "eye":
        return np.concatenate([np.tile([96.0, 232, 255], (N, 1)), np.full((N, 1), SELF_LIT)], 1)
    if kind == "flat":
        return np.concatenate([np.tile(np.array(ctx["col"], float), (N, 1)), a[:, None]], 1)
    if kind == "ember":
        k = .5 + (noise3(Q, 3.0, ctx.get("seed", 0)) - .5) * .6
        return np.concatenate([red(k), np.full((N, 1), SELF_LIT)], 1)
    tint = noise3(Q, 11.0, 7) * 2 - 1
    lit = .1 * nq[1]                                          # a little sky light is baked in
    if kind == "blade":
        l = (Q - ctx["o"]) @ ctx["R"]
        s, x = l[:, 2] / ctx["L"], l[:, 0]
        t = .68 - .56 * smooth((s - .06) / .74) - .1 * smooth((x / ctx['W'] - .55) / .4)
        t += (noise3(np.stack([x * 2.2, l[:, 1] * 0, l[:, 2] / 6], 1), 2.4, ctx["seed"]) - .5) * .2     # long streaks
        t += (noise3(Q, 2.0, 3) - .5) * .06
        ridge = np.abs(((x + .7) / 4.6) % 1.0 - .5)
        t += np.where(ridge > .44, -.13, np.where(ridge < .06, .07, 0)) * (s < .78)
        face_out = nq @ (ctx["R"][:, 1] * ctx["out"])
        t += .06 * face_out + lit
        if ctx.get("under"):
            t -= .26
        col = silver(t, tint)
        # dragon light: long patches in the middle of the blade, strongest on the under-layer and plank edges
        r = noise3(np.stack([x / 1.7, l[:, 1] * 0, l[:, 2] / 24.0], 1), 1.0, ctx["seed"] + 20)
        zone = smooth((s - .14) / .12) * (1 - smooth((s - .62) / .16))
        k = (r - .8) / .2
        k = np.where(zone > .3, k * zone, -1.0)
        hot = k > 0
        halo = (k > -.3) & ~hot
        col[halo] = col[halo] * .5 + RED[0] * .5
        col[hot] = red(.2 + k[hot] * .6)
        a[hot & (k > .6)] = SELF_LIT
        return np.concatenate([col, a[:, None]], 1)
    if kind in ("spike", "claw"):
        s = np.clip(((Q - ctx["o"]) @ ctx["d"]) / ctx["L"], 0, 1)
        if kind == "claw":
            t = .2 + .6 * s + (noise3(Q, 1.6, 2) - .5) * .12
            tint = tint * 0 - .5
        else:
            t = ctx["t0"] + (ctx["t1"] - ctx["t0"]) * s + (noise3(Q, 2.2, 4) - .5) * .14
            t += np.where(np.abs((s * ctx["L"] / 4.2) % 1.0 - .5) > .43, -.12, 0)
        return np.concatenate([silver(t + lit, tint), a[:, None]], 1)
    # hide: the sliced body
    part = ctx.get("part", "")
    t = ctx["tone"] + lit
    if ctx.get('end'):
        ao = np.full(N, .9)
    else:
        ao = np.clip(sdf_all(Q + nq * 2.5) / 2.5, 0, 1) * .6 + np.clip(sdf_all(Q + nq * 7) / 7, 0, 1) * .4
    t += (noise3(Q, 9.0, 1) - .5) * .2 + (noise3(Q, 2.6, 2) - .5) * .1
    # scales: rows along the body, staggered, a light upper lip and a dark lower rim
    u = Q[:, 2] / 3.0
    v = (Q[:, 1] * .9 + np.abs(Q[:, 0]) * .55) / 2.6
    row = np.floor(v)
    fu, fv = (u + .5 * (row % 2)) % 1.0, v % 1.0
    t += np.where(fv > .78, .06, np.where(fv < .2, -.06, 0)) + np.where(fu < .14, -.04, 0)
    if part in ("torso", "neck", "tail"):                    # armour seams ring the body
        seam = np.abs((Q[:, 2] / 9.5 + (noise3(Q, 14.0, 5) - .5) * .5) % 1.0 - .5)
        t += np.where(seam > .46, -.13, np.where(seam > .42, .07, 0))
        t -= .16 * smooth((24 - Q[:, 1]) / 9)                # darker belly
        t += (noise3(Q, 17.0, 6) - .5) * .22                 # plates differ in brightness
    if part == "skull":
        l = (Q - HEAD_O) @ HEAD_R
        t -= .2 * smooth((l[:, 1] - 1.5) / 3) * smooth((3.4 - np.abs(l[:, 0])) / 2)      # dark crown
        t += .2 * smooth((l[:, 2] - 20) / 12)                                             # pale snout
    t = t * (.5 + .5 * ao)
    col = silver(t, tint)
    # the red hide: along the top of the neck, under the shoulder shells, between the two tall fins
    k = np.zeros(N)
    if part == "neck" or part == "torso":
        z, y, x = Q[:, 2], Q[:, 1], np.abs(Q[:, 0])
        k = np.maximum(k, smooth((3.8 - x) / 1.6) * smooth((z - 14) / 6) * smooth((63 - z) / 4) * (nq[1] > .15) * smooth((y - 26 - lift(20) * 0 - np.interp(z, [14, 34, 61], [12, 13, -4])) / 3))
        k = np.maximum(k, 1.2 * smooth(1 - np.sqrt(((x - 14.5) * 1.6) ** 2 + ((y - 33) * .7) ** 2 + ((z - 34) * 1.2) ** 2) / 5.0))
    if part == "skull":
        l = (Q - HEAD_O) @ HEAD_R
        k = np.maximum(k, .9 * smooth((2.2 - np.abs(l[:, 0])) / 1.4) * smooth((6 - l[:, 2]) / 5) * (nq[1] > .2))
    if k.any():
        k = k * (.75 + .5 * noise3(Q, 3.2, 9))
        rc = red(.08 + k * .34 + (noise3(Q, 1.8, 11) - .5) * .16) * (.6 + .4 * ao)[:, None]
        w = smooth(k * 1.5)[:, None]
        col = col * (1 - w) + rc * w
        a[k > 1.25] = SELF_LIT
    return np.concatenate([col, a[:, None]], 1)


SIDES = {  # side -> (u axis, v axis, fixed axis, fixed value, u flipped, v flipped) in the cube's 0..1 box
    "up": (0, 2, 1, 1, False, False), "down": (0, 2, 1, 0, False, True),
    "east": (2, 1, 0, 1, True, True), "west": (2, 1, 0, 0, False, True),
    "south": (0, 1, 2, 1, False, True), "north": (0, 1, 2, 0, True, True),
}


def bake(b):
    """Paint every face from the material and pack the faces into one atlas."""
    jobs = []
    for el, c, R, size, ctx, mirrored in b.cubes:
        thin = ctx["kind"] == "hide" and ctx.get("sliced")
        for side, (ua, va, fa, fv, uf, vf) in SIDES.items():
            dens = 1 if (thin and side in ("north", "south")) else TEX      # slice faces mostly hide behind the next
            w, h = max(1, round(size[ua] * dens)), max(1, round(size[va] * dens))
            jobs.append((h, w, el, c, R, size, ctx, mirrored, side))
    jobs.sort(key=lambda j: (-j[0], -j[1]))
    atlas = np.zeros((ATLAS, ATLAS, 4), np.uint8)
    x = y = shelf = 0
    for h, w, el, c, R, size, ctx, mirrored, side in jobs:
        if x + w > ATLAS:
            x, y, shelf = 0, y + shelf, 0
        if y + h > ATLAS:
            raise ValueError("atlas full")
        ua, va, fa, fv, uf, vf = SIDES[side]
        U, Vv = np.meshgrid((np.arange(w) + .5) / w, (np.arange(h) + .5) / h)
        l = np.zeros((h * w, 3))
        l[:, ua] = (1 - U if uf else U).ravel()
        l[:, va] = (1 - Vv if vf else Vv).ravel()
        l[:, fa] = fv
        P = c + ((l - .5) * np.array(size)) @ R.T
        n = R[:, fa] * (1 if fv else -1)
        if ctx.get('sliced') and side in ('north', 'south'):
            ctx = dict(ctx, end=True)
        atlas[y:y + h, x:x + w] = np.clip(shade(ctx, P, n, mirrored), 0, 255).reshape(h, w, 4).astype(np.uint8)
        el["faces"][side] = {"uv": [x, y, x + w, y + h], "texture": 0}
        x += w
        shelf = max(shelf, h)
    b.m.pixels = atlas
    return (y + shelf) / ATLAS


def _png(pixels):
    out = io.BytesIO()
    Image.fromarray(np.asarray(pixels, np.uint8), "RGBA").save(out, "PNG", optimize=True)
    return out.getvalue()


boss_models.png = _png


# --- assembly ---------------------------------------------------------------------------------------
def hide(part, tone):
    return dict(kind="hide", part=part, tone=tone, sliced=True)


def head(b):
    R = HEAD_R
    f = Fr(b, "skull", HEAD_O, R)
    f0, fp0 = f.c, f.pt
    f.c = lambda p, size, ctx, rot=None: f0((p[0], p[1], p[2] * HZ), size, ctx, rot)
    f.pt = lambda p: fp0((p[0], p[1], p[2] * HZ))
    slices(b, "skull", ["skull"], HEAD_O, HEAD_O + R @ V(0, 0, 33.6 * HZ), 1.5, 1.5, 10, hide("skull", .5), pad=(7, 0))
    slices(b, "jaw", ["jaw"], JAW_O, JAW_O + JAW_R @ V(0, 0, 27 * HZ), 1.6, 1.4, 8, hide("jaw", .5), pad=(3, 0))
    tooth = dict(kind="flat", col=(226, 222, 208))
    for sx in (1, -1):
        f.c((sx * 7.25, .6, 13.6), (.6, 1.4, 2.6), dict(kind="eye"), rot=RY(sx * 10))
        f.c((sx * 6.95, .6, 13.6), (1.0, 2.4, 4.0), dict(kind="flat", col=(14, 16, 24)), rot=RY(sx * 10))
        f.c((sx * 6.2, 3.4, 13), (3.8, 1.6, 9.5), dict(kind="spike", o=f.pt((0, 0, 8)), d=R[:, 2], L=12, t0=.8, t1=.5), rot=RZ(-sx * 24) @ RY(sx * 9))
        for k in range(9):                                   # teeth, upper row
            z = 9.5 + k * 2.4
            w = np.interp(z, [9, 20, 31], [5.6, 4.0, 1.3])
            f.c((sx * (w - .2), -4.2 - (z - 9) * .03, z), (.7, 1.7 - k * .09, .8), tooth)
        spike(b, "skull", f.pt((sx * 6.6, -2.8, 2)), R @ unit((sx * .5, -.3, -1)), 10, 3, 2, 4, .72, .34)      # jowl spines
        spike(b, "skull", f.pt((sx * 6.0, -4.2, 5)), R @ unit((sx * .4, -.6, -1)), 6, 2.2, 1.6, 3, .66, .34)
    crest = ((0, 5.4, 0, 0, .46, 14), (2.6, 5.0, .5, .2, .56, 19), (4.6, 3.8, 1, .4, .38, 18), (6.0, 1.8, .8, .62, .16, 15), (6.2, -.8, .5, .72, -.08, 11))
    for x, y, z, ox, oy, L in crest:                          # the swept-back crest
        for sx in ((1, -1) if x else (1,)):
            spike(b, "skull", f.pt((sx * x, y, z)), R @ unit((sx * ox, oy, -1)), L, 2.8, 1.8, 5, .6, .12)
    fj = Fr(b, "jaw", JAW_O, JAW_R)
    j0, jp0 = fj.c, fj.pt
    fj.c = lambda p, size, ctx, rot=None: j0((p[0], p[1], p[2] * HZ), size, ctx, rot)
    fj.pt = lambda p: jp0((p[0], p[1], p[2] * HZ))
    fj.c((0, .3, 13), (5.4, .4, 16), dict(kind="flat", col=(104, 22, 36)))
    for sx in (1, -1):
        for k in range(8):
            z = 8 + k * 2.3
            w = np.interp(z, [8, 18, 26], [4.4, 3.2, 1.3])
            fj.c((sx * (w - .1), .9, z), (.7, 1.5, .7), tooth)
        spike(b, "jaw", fj.pt((sx * 2.4, -2.6, 8)), JAW_R @ unit((sx * .2, -.8, -.6)), 5, 1.8, 1.3, 3, .6, .3)


def body(b):
    slices(b, "body", ["torso"], (0, 32, -42), (0, 32, 34), 3.0, 3.0, 28, hide("torso", .6), pad=(0, 0))
    for (a, c), bone in zip(zip(NECK, NECK[1:]), ("neck1", "neck2", "neck3")):
        slices(b, bone, ["neck"], a[:3], c[:3], 2.4, 2.2, 18, hide("neck", .58), pad=(1.5, 1.5))
    for i, (c, a) in enumerate(zip(TAIL, TAIL[1:])):
        slices(b, f"tail{i + 1}", ["tail"], a[:3], c[:3], 3.2, 2.0, 12, hide("tail", .46 - .02 * i), pad=(.5, .5))
        mid = (V(*a[:3]) + V(*c[:3])) / 2
        r = (a[3] + c[3]) / 2
        for dz in (-4, 3):                                   # ridge and flank spines along the tail
            spike(b, f"tail{i + 1}", mid + V(0, r * .85, dz), (0, .72, -.7), 5 + r * .8, 2.4, 1.5, 3, .76, .34, xhint=(0, 0, 1))
            for sx in (1, -1):
                spike(b, f"tail{i + 1}", mid + V(sx * r * .85, r * .15, dz + 1), (sx * .72, .2, -.66), 3.5 + r * .6, 2.2, 1.3, 3, .72, .38, xhint=(0, 1, 0))
    for sx in (1, -1):
        for k, (z, L) in enumerate(((-2, 20), (-12, 25), (-22, 23), (-31, 17))):       # raked back spikes
            spike(b, "body", (sx * 6.5, 42 - k * .7 + lift(z), z), (sx * .36, .8, -.7), L, 5, 2.4, 5, .88, .26, xhint=(0, 0, 1))
            spike(b, "body", (sx * 13, 37 - k * .9 + lift(z), z + 4), (sx * .74, .46, -.7), L * .55, 3.6, 2, 4, .8, .34, xhint=(0, 0, 1))
        for k, (y, z, s) in enumerate(((44, 24, 16), (40, 22.5, 14), (36, 21.5, 11.5))):   # shoulder shells
            b.cube("body", (sx * (22.5 - k * .4), y, z), (2.8, 7, s), dict(kind="spike", o=V(0, y + 3, z), d=V(0, -1, 0), L=7, t0=.92 - .07 * k, t1=.66),
                   R=RY(sx * 6) @ RZ(sx * (16 - k * 10)) @ RX(-26))
        # the two tall fins behind the neck, red inside
        base = V(sx * 4.8, 47, 20)
        d = unit((sx * .1, 1, -.32))
        spike(b, "body", base, d, 32, 8, 3.6, 6, .5, .12, xhint=(0, 0, 1))
        fr = Fr(b, "body", base, frame_zx(d, (0, 0, 1)))
        fr.c((0, sx * 2.0, 9), (4.6, .6, 15), dict(kind="ember", seed=1))
        fr.c((0, sx * 1.8, 19.5), (2.6, .6, 9), dict(kind="ember", seed=2))
    # tail fins and the forked tip
    spike(b, "tail6", (0, 21.4, -116), (0, .52, -.85), 20, 6.4, 1.5, 5, .7, .16, xhint=(0, 1, 0))
    for sx in (1, -1):
        spike(b, "tail6", (sx * 1.6, 19.8, -116), (sx * .62, .06, -.78), 22, 7, 1.4, 5, .74, .16, xhint=(0, 0, 1))
        spike(b, "tail6", (sx * 1, 19.6, -129), (sx * .42, .1, -.9), 12, 3.6, 1.2, 4, .6, .16, xhint=(0, 0, 1))
    spike(b, "tail6", (0, 19.5, -134), (0, -.02, -1), 18, 3, 2.4, 5, .5, .1)


def legs(b):
    S, E, W = FL
    slices(b, "fl_upper_l", ["fl_upper_l"], S, E, 2.0, 2.2, 11, hide("leg", .56), sym=True, pad=(1, .5))
    slices(b, "fl_lower_l", ["fl_lower_l"], E, W, 2.0, 2.0, 9, hide("leg", .52), sym=True, pad=(.5, .5))
    slices(b, "fl_paw_l", ["fl_paw_l"], W + V(0, -3, -3), W + V(0, -3, 9.6), 1.6, 1.3, 7, hide("leg", .42), sym=True)
    spike(b, "fl_lower_l", E + V(1, 1, -2), (.25, .5, -1), 10, 3.4, 2.2, 4, .8, .36, sym=True)
    f = Fr(b, "fl_paw_l", W + V(0, -3.2, 2.5), np.eye(3), sym=True)
    for i, x in enumerate((-3.4, -1.15, 1.15, 3.4)):
        toe(f, x, 4.6, x * 6, long_=1.0 if i in (1, 2) else .86)
    Hp, K, Hk, A = HL
    slices(b, "hl_thigh_l", ["hl_thigh_l"], Hp, K, 2.0, 2.4, 13, hide("leg", .56), sym=True, pad=(1, .5))
    slices(b, "hl_shin_l", ["hl_shin_l"], K, Hk, 2.0, 2.0, 9, hide("leg", .42), sym=True, pad=(.5, .5))
    slices(b, "hl_foot_l", ["hl_foot_l"], Hk, A, 2.0, 2.0, 9, hide("leg", .4), sym=True, pad=(.5, 2.5))
    slices(b, "hl_foot_l", ["hl_foot_l"], A + V(0, -1.3, .5), A + V(0, -1.3, 9.4), 1.6, 1.3, 7, hide("leg", .4), sym=True)
    for i, (dz, L) in enumerate(((-3, 14), (-7, 12), (1, 10))):                             # thigh spikes
        spike(b, "hl_thigh_l", (Hp + K) / 2 + V(5.5, 4 - i * 3.4, dz), (.62, .42, -.72), L, 3.8, 2.2, 4, .86, .3, sym=True, xhint=(0, 0, 1))
    spike(b, "hl_foot_l", Hk + V(0, 1, -1), (.1, .5, -1), 8, 2.8, 2.2, 4, .72, .34, sym=True)
    f = Fr(b, "hl_foot_l", A + V(0, -1, 3.2), np.eye(3), sym=True)
    for i, x in enumerate((-3.1, -1.05, 1.05, 3.1)):
        toe(f, x, 4.2, x * 6, long_=.95)


def wings(b):
    slices(b, "wing_arm_l", ["wing_arm_l"], SHOULDER, ELBOW, 2.4, 2.4, 11, hide("wingarm", .56), sym=True, pad=(1, .5))
    slices(b, "wing_fore_l", ["wing_fore_l"], ELBOW, WRIST, 2.4, 2.2, 11, hide("wingarm", .64), sym=True, pad=(.5, 1))
    knot(b, "wing_fore_l", ELBOW, 7.5, 9, n=16, tone=.12)
    # wrist shield: broad pale plates stepped toward the front, and the forward thorns
    fw = Fr(b, "wing_hand_l", WRIST, frame_zx((.06, -.12, 1), (.9, -.44, 0)), sym=True)
    shield = dict(kind="spike", o=fw.pt((0, 0, -14)), d=fw.R[:, 2], L=34, t0=.66, t1=.86)
    for i, (z, ln, w) in enumerate(((-4, 20, 16), (5, 13, 12), (11, 9, 8))):
        fw.c((w / 2 - 2, -2.4 - i * 1.2, z), (w, 3.2, ln), shield)
    for i, (x, L) in enumerate(((0, 22), (5.5, 14))):
        spike(b, "wing_hand_l", fw.pt((x, -1.3, 9 - i * 3)), fw.R @ unit((.08 * i, 0, 1)), L, 6 - i, 3.4, 5, .84, .7, sym=True, xhint=fw.R[:, 0])
    knot(b, "wing_hand_l", WRIST + V(-1, -2, -1), 6.5, 11, n=8, tone=.44)
    for k, (bone, off, d, xh, L, W) in enumerate(FINGERS):
        finger(b, bone, WRIST + V(*off), d, xh, L, W, seed=k * 3 + 1)


def build():
    PRIMS.clear()
    solid()
    b = Builder()
    b.bone("root", None, (0, 0, 0))
    b.bone("body", "root", (0, 29, -8))
    b.bone("neck1", "body", NECK[0][:3])
    b.bone("neck2", "neck1", NECK[1][:3])
    b.bone("neck3", "neck2", NECK[2][:3])
    b.bone("skull", "neck3", HEAD_O)
    b.bone("jaw", "skull", JAW_O)
    for i in range(6):
        b.bone(f"tail{i + 1}", "body" if i == 0 else f"tail{i}", TAIL[i][:3])
    b.bone("wing_arm_l", "body", SHOULDER, sym=True)
    b.bone("wing_fore_l", "wing_arm_l", ELBOW, sym=True)
    b.bone("wing_hand_l", "wing_fore_l", WRIST, sym=True)
    for bone, off, *_ in FINGERS:
        b.bone(bone, "wing_hand_l", WRIST + V(*off), sym=True)
    b.bone("fl_upper_l", "body", FL[0], sym=True)
    b.bone("fl_lower_l", "fl_upper_l", FL[1], sym=True)
    b.bone("fl_paw_l", "fl_lower_l", FL[2], sym=True)
    b.bone("hl_thigh_l", "body", HL[0], sym=True)
    b.bone("hl_shin_l", "hl_thigh_l", HL[1], sym=True)
    b.bone("hl_foot_l", "hl_shin_l", HL[2], sym=True)
    body(b)
    head(b)
    legs(b)
    wings(b)
    return b


if __name__ == "__main__":
    import valstrax_anim
    b = build()
    outliner = b.outliner()
    used = bake(b)
    valstrax_anim.write(b)
    b.m.write(OUT, outliner)
    rotated = sum(1 for e in b.m.elements if e.get("rotation"))
    print(f"{OUT.relative_to(ROOT)}: {len(b.m.elements)} cubes ({rotated} turned) / {len(b.bones)} bones / "
          f"{len(b.m.animations)} animations, atlas {ATLAS}px {used:.0%} tall")
