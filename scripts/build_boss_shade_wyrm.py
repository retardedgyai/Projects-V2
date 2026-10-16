"""黒翳竜 (shade wyrm) Blockbench model.

    python scripts/build_boss_shade_wyrm.py   # writes assets/bosses/shade_wyrm.bbmodel

The written .bbmodel is the committed source. Once someone edits it in Blockbench, do not re-run
this script over it without merging by hand.

Skeleton and proportions come from the Creator's noctveil reference (assets/bosses/shade_wyrm_ref,
see scripts/extract_boss_anatomy.py), re-posed lower and slimmer. It is built the way the Blockbench boss
creators the Creator pointed to build theirs: oriented boxes along the bones with hand-painted ramps,
fur / scale fringes as thin plates with transparent jagged ends, the cloak as plates cut to the panel
outline (violet on the inside), and glowing accents. Boxes rotate on all three axes (Minecraft 1.21.11+).

Front is +Z, +X is the wyrm's left, 16 px = 1 block. Animation names are the server contract:
idle / walk / idle_enraged / walk_enraged / enrage / death, plus one `once` animation per ability id
(length = telegraph+recovery, impact at telegraph).
"""
import json
import math

from boss_models import Model, ROOT

# Animation rotation helpers, checked against the WSEE runtime by ShadeWyrmBossTest:
# pitch>0 lowers the +Z end (raw Bedrock 4.x X rotation lifts it, hence -1); roll>0 raises the
# +X side. WSEE mirrors Blockbench X in game, so "_l" bones appear on the wyrm's right.
PITCH, YAW, ROLL = -1, 1, 1


def rot(pitch=0.0, yaw=0.0, roll=0.0):
    return (PITCH * pitch, YAW * yaw, ROLL * roll)


# --- rest-pose math (Blockbench editor: R = Rz * Ry * Rx, degrees, right-handed) ---------------
IDENTITY = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _t(a):
    return [list(r) for r in zip(*a)]


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def euler_matrix(x, y, z):
    x, y, z = (math.radians(v) for v in (x, y, z))
    rx = [[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]]
    ry = [[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]]
    rz = [[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]]
    return _mul(rz, _mul(ry, rx))


def matrix_euler(m):
    """Inverse of euler_matrix (ZYX)."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    x = math.atan2(m[2][1], m[2][2])
    z = math.atan2(m[1][0], m[0][0])
    return [round(math.degrees(v), 2) for v in (x, y, z)]


def aim(direction, along=(0, -1, 0), parent=None):
    """Rest rotation (x, 0, z) that turns the local axis `along` (±Y) to the world `direction`."""
    if parent is not None:
        direction = _apply(_t(parent), direction)
    n = math.sqrt(sum(c * c for c in direction))
    dx, dy, dz = (c / n for c in direction)
    if along[1] > 0:
        dx, dy, dz = -dx, -dy, -dz
    # Rz(b) Rx(a) (0,-1,0) = (cos a sin b, -cos a cos b, -sin a)
    a = -math.asin(max(-1.0, min(1.0, dz)))
    ca = math.cos(a)
    b = math.atan2(dx / ca, -dy / ca)
    return [round(math.degrees(a), 2), 0, round(math.degrees(b), 2)]


def length(v):
    return math.sqrt(sum(c * c for c in v))


# --- texture ------------------------------------------------------------------------------------
# Large, calm areas of colour with clear value steps; detail only where it explains the form.
PALETTES = {
    # shadow, base, light
    "hide": ((24, 21, 30), (38, 34, 46), (58, 52, 70)),
    "scale": ((32, 28, 40), (50, 45, 60), (82, 74, 96)),
    "belly": ((80, 72, 88), (108, 98, 114), (140, 130, 146)),
    "bone": ((128, 118, 106), (192, 182, 166), (234, 226, 212)),
    "fringe": ((16, 14, 22), (32, 28, 40), (116, 102, 136)),
    "cloak": ((15, 12, 21), (25, 21, 33), (62, 48, 84)),
    "hem": ((40, 32, 54), (62, 50, 82), (96, 80, 122)),
    "inner": ((46, 28, 68), (70, 42, 102), (112, 76, 156)),
    "spar": ((66, 60, 78), (100, 92, 114), (148, 140, 160)),
    "feeler": ((60, 46, 80), (100, 80, 128), (152, 126, 188)),
    "glow": ((176, 120, 255), (212, 170, 255), (246, 230, 255)),
    "fang": ((150, 132, 170), (212, 198, 226), (244, 236, 250)),
    "talon": ((70, 30, 72), (118, 52, 112), (170, 92, 160)),
    "mouth": ((72, 18, 36), (112, 32, 54), (152, 52, 74)),
}
VEIN = (88, 60, 120)
INNER_VEIN = (56, 32, 84)


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n ^ (n >> 16)) & 0xffff


def darker(c, k=.72):
    return tuple(int(v * k) for v in c)


def painter(material, motif="plain", inner_face=None):
    """inner_face: the face of a membrane slab that faces the body (violet when the cloak opens)."""
    def make(side, w, h):
        seed = sum(ord(c) * (i + 1) for i, c in enumerate(material + motif + side))
        big = side not in ("up", "down")

        def paint(x, y, w, h, motif=("membrane" if motif == "veil" else motif), torn=(motif != "veil")):
            mat = "inner" if side == inner_face else material
            pal = PALETTES[mat]
            c = pal[1]
            if noise(x // 3, y // 3, seed) % 6 == 0:   # soft 3x3 blotches, never single-pixel noise
                c = pal[0]
            if motif == "scales" and big:
                # Overlapping 6x4 plates: a light lip on top, a dark gap underneath.
                stroke = noise((x + y * 7) // 3, y, seed + 2)
                if stroke % 9 == 0:
                    c = pal[2]                       # short light stroke (scale edge catching light)
                elif noise(x, y, seed + 4) % 11 == 0:
                    c = darker(pal[0], .8)           # dark fleck
            elif motif == "belly" and big:
                c = pal[0] if y % 5 == 4 else pal[2] if y % 5 == 0 else pal[1]
            elif motif == "bone":
                c = pal[2] if y < max(1, h // 3) else pal[1] if y < 2 * h // 3 else pal[0]
            elif motif == "membrane" and big:
                # Dark at the top, smoky and dusted toward the torn hem.
                t = y / max(1, h - 1)
                if mat != "inner" and t > .62 + (noise(x // 3, 2, seed) % 4) * .05:
                    c = PALETTES["hem"][1] if noise(x // 2, y // 2, seed + 3) % 4 else PALETTES["hem"][2]
                if y < 2:
                    c = pal[2]                       # leading edge along the finger
                elif (x + y // 3) % 8 in (0, 1) and noise(x // 8, 1, seed) % 3:
                    c = INNER_VEIN if mat == "inner" else VEIN
                elif noise(x, y, seed + 9) % (23 if mat == "inner" else 47) == 0:
                    c = PALETTES["glow"][0]          # shade dust caught in the membrane
            elif motif == "fringe" and big:
                strand = noise(x // 2, 3, seed) % 4
                c = pal[0] if strand == 0 else pal[1]
                if y >= h - 4 - strand % 2:
                    c = pal[2]                       # pale hair tips
            elif motif == "glow":
                c = pal[2] if 0 < x < w - 1 and 0 < y < h - 1 else pal[1]
            # Ragged hem: notches cut into the lower edge of membranes and hair.
            if torn and motif in ("membrane", "fringe") and big and h >= 6:
                depth = noise(x // 2, 7, seed) % (6 if motif == "membrane" else 4)
                if y >= h - depth:
                    return (0, 0, 0, 0)
            # Bevel: light top edge, dark bottom/right edge so every block reads as a block.
            if w >= 3 and h >= 3 and motif not in ("glow", "membrane", "fringe"):
                if y == 0:
                    c = pal[2]
                elif y == h - 1 or x == w - 1:
                    c = darker(pal[0], .75)
            return (*c, 255)
        return paint, (material, motif, inner_face, side, w, h)
    return make


# --- model --------------------------------------------------------------------------------------
class Rig:
    """Tracks world rotations so child bones can be aimed in world terms."""

    def __init__(self, m):
        self.m = m
        self.world = {}

    def bone(self, name, pivot, rotation, items, parent=None, children=()):
        local = euler_matrix(*rotation)
        self.world[name] = _mul(self.world.get(parent, IDENTITY), local) if parent else local
        return lambda: self.m.bone(name, pivot, items + [c() for c in children], rotation)


REF = ROOT / "assets/bosses/shade_wyrm_ref"


def _sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def _dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _unit(a):
    n = math.sqrt(_dot(a, a))
    return [v / n for v in a] if n > 1e-9 else None


def ordered(face):
    """Quads in Blockbench meshes are not stored in winding order: sort them around the centroid."""
    v, uv = face["v"], face["uv"]
    if len(v) != 4:
        return v, uv
    c = [sum(p[i] for p in v) / 4 for i in range(3)]
    n = _unit(_cross(_sub(v[1], v[0]), _sub(v[2], v[0]))) or _unit(_cross(_sub(v[2], v[0]), _sub(v[3], v[0])))
    a = _unit(_sub(v[0], c))
    if not n or not a:
        return v, uv
    b = _cross(n, a)
    idx = sorted(range(4), key=lambda i: math.atan2(_dot(_sub(v[i], c), b), _dot(_sub(v[i], c), a)))
    return [v[i] for i in idx], [uv[i] for i in idx]


# --- re-pose the reference before it becomes plates: lower, shorter front legs, slimmer -----------------
# Tuned against the Creator's in-game look (2026-10-16: "front legs too long, lower like Gore Magala, more slender").
POSE = {
    "drop": 14.0,            # px the body comes down
    "pitch": 6.0,            # degrees the front tips down, about the hips
    "hips": (0.0, 40.0, -40.0),
    "slim": .84,             # body, neck, head, tail and wings: width (X) scale about the centre line
    "legs": {               # chain, length scale per segment, limb thickness, foot shrink, knee hint
        "leg_fl": (("leg_fl", "leg_fl_fore", "leg_fl_paw"), (.74, .80), .82, .9, (0.15, 0, -1.0)),
        "leg_hl": (("leg_hl", "leg_hl_shin", "leg_hl_foot"), (.95, .95), .82, .9, (0.15, 0, 1.0)),
    },
    "foot_x": .9,
    "head": .68,             # head (skull, jaw, crown) scale about the skull joint (2026-10-16: "face too big")
    "head_shift": (0.0, -2.0, 7.0),   # and moved forward so the smaller head clears the collar
}


def _np():
    import numpy
    return numpy


def repose(ref):
    """Returns (pivot per bone, transform per bone) for the left side; transforms map reference points."""
    np = _np()
    piv = {b["name"]: np.array(b["pivot"], float) for b in ref["bones"]}
    a = math.radians(POSE["pitch"])
    Rp = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])  # front (+Z) goes down
    H = np.array(POSE["hips"])
    S = np.diag([POSE["slim"], 1, 1])

    def body(v):
        v = S @ np.asarray(v, float)
        return Rp @ (v - H) + H + np.array([0, -POSE["drop"], 0])

    def rot_between(u, w):
        u = u / np.linalg.norm(u); w = w / np.linalg.norm(w)
        c = float(u @ w); ax = np.cross(u, w); s = np.linalg.norm(ax)
        if s < 1e-9:
            return np.eye(3)
        ax /= s; K = np.array([[0, -ax[2], ax[1]], [ax[2], 0, -ax[0]], [-ax[1], ax[0], 0]])
        return np.eye(3) + s * K + (1 - c) * K @ K

    xf = {}
    leg_bones = set()
    for chain, scales, thick, foot, hint in POSE["legs"].values():
        b0, b1, b2 = chain
        leg_bones.update(chain)
        J0, J1, J2 = piv[b0], piv[b1], piv[b2]
        N0 = body(J0)
        L1 = np.linalg.norm(J1 - J0) * scales[0]; L2 = np.linalg.norm(J2 - J1) * scales[1]
        F = np.array([J2[0] * POSE["foot_x"], J2[1], J2[2]])
        d = F - N0; dist = np.linalg.norm(d)
        if dist > (L1 + L2) * .98:
            F = N0 + d / dist * (L1 + L2) * .98; dist = (L1 + L2) * .98
        u = (F - N0) / dist
        aa = (L1 ** 2 - L2 ** 2 + dist ** 2) / (2 * dist)
        h = math.sqrt(max(0.0, L1 ** 2 - aa ** 2))
        w = np.array(hint, float); w = w - (w @ u) * u; w /= np.linalg.norm(w)
        K = N0 + aa * u + h * w

        def seg(J, Jn, N, Nn, f):
            axis = (Jn - J) / np.linalg.norm(Jn - J)
            Sc = thick * np.eye(3) + (f - thick) * np.outer(axis, axis)
            R = rot_between(Jn - J, Nn - N)
            return lambda v, R=R, Sc=Sc, J=J, N=N: N + R @ (Sc @ (np.asarray(v, float) - J))
        xf[b0] = seg(J0, J1, N0, K, scales[0])
        xf[b1] = seg(J1, J2, K, F, scales[1])
        xf[b2] = lambda v, J2=J2, F=F, foot=foot: F + foot * (np.asarray(v, float) - J2)
        piv[b0], piv[b1], piv[b2] = N0, K, F
    head = {"skull", "jaw", "crown_l"}
    hp = piv["skull"].copy()
    k = POSE["head"]
    for name in list(piv):
        if name in head:
            xf[name] = lambda v, hp=hp: body(hp + k * (np.asarray(v, float) - hp) + np.array(POSE["head_shift"]))
            piv[name] = xf[name](piv[name])
        elif name not in leg_bones:
            xf[name] = body
            piv[name] = body(piv[name])
    return {k: [float(x) for x in v] for k, v in piv.items()}, xf





# --- texture: hand-painted look, crisp 4-tone ramps (deep, dark, base, light) -----------------------------
RAMPS = {
    "hide": ((14, 13, 22), (26, 25, 38), (44, 42, 62), (78, 74, 104)),
    "plate": ((18, 17, 28), (34, 32, 50), (56, 52, 78), (104, 96, 134)),
    "belly": ((40, 38, 52), (58, 54, 72), (78, 74, 94), (106, 100, 124)),
    "bone": ((112, 102, 94), (160, 150, 138), (204, 196, 182), (236, 230, 218)),
    "talon": ((48, 22, 46), (82, 38, 76), (124, 64, 112), (176, 112, 160)),
    "spar": ((30, 30, 42), (50, 48, 66), (74, 70, 94), (108, 102, 132)),
    "fur": ((12, 12, 20), (26, 24, 38), (46, 42, 62), (112, 102, 142)),
    "cloak": ((10, 10, 16), (20, 18, 28), (32, 28, 44), (54, 46, 72)),
    "inner": ((44, 26, 64), (66, 40, 94), (92, 58, 128), (132, 94, 176)),
    "glow": ((176, 120, 255), (190, 140, 255), (212, 170, 255), (246, 230, 255)),
    "mouth": ((56, 14, 28), (88, 24, 42), (124, 40, 60), (158, 64, 84)),
}


def _h(*k):
    n = 0
    for v in k:
        n = (n * 1000003 + int(v) * 2654435761 + 97) & 0xffffffff
    n ^= n >> 15; n = (n * 2246822519) & 0xffffffff; n ^= n >> 13
    return (n & 0xffff) / 65535.0


def box_painter(mat, seed=0):
    """A box face painted like the reference creators do it: a top-light ramp down each side face,
    big overlapping scales (6 px wide, 4 px tall) of which only some catch a light lip, darker scale gaps,
    a brighter top face and a shadowed bottom face."""
    ramp = RAMPS[mat]

    def make(side, w, h):
        key = (mat, side, w, h, seed % 5)

        def paint(x, y, w, h):
            if mat == "glow":
                return ramp[3 if 0 < x < w - 1 and 0 < y < h - 1 else 2] + (255,)
            if w < 3 or h < 3:
                return ramp[3 if side == "up" else 1 if side == "down" else 2] + (255,)
            row = y // 4
            off = (row % 2) * 3
            cx, cy = (x + off) % 6, y % 4
            cell = _h(seed % 5, (x + off) // 6, row, len(side))
            scaled = mat in ("hide", "plate", "spar")
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
                elif cy == 3 and (cx == 0 or cx == 5):
                    t = max(0, t - 1)
            if mat == "belly" and y % 5 == 4:
                t = max(0, t - 1)
            return ramp[t] + (255,)
        return paint, key
    return make


def fur_painter(seed=0, ramp="fur", up=False):
    """A fur / scale-fringe plate: clumps of strands (2 px wide) hang from the top edge, dark at the root
    and pale at the tips, with jagged ends cut to transparent. Only the two big faces are drawn."""
    pal = RAMPS[ramp]

    def make(side, w, h):
        if side not in ("east", "west"):
            return (lambda x, y, w, h: (0, 0, 0, 0)), ("clear",)

        def paint(x, y, w, h):
            col = x if side == "east" else w - 1 - x   # the same strand seen from both sides
            clump = col // 2
            ln = h * (.55 + .45 * _h(seed, clump, 3)) - (col % 2) * 1.5
            if y >= ln:
                return (0, 0, 0, 0)
            f = y / max(1.0, ln)
            t = 0 if f < .2 else 1 if f < .55 else 2 if f < .85 else 3
            if _h(seed, clump, 7) < .3:
                t = max(0, t - 1)
            return pal[t] + (255,)
        return paint, ("fur", seed % 9, ramp, side, w, h)
    return make


def membrane_painter(outline, half, inner_side, seed):
    """Cloak membrane plate cut to its panel outline: near-black outside, violet on the side facing the body,
    thin veins and a scatter of glowing scale dust."""
    ys, zs = half[1], half[2]

    def inside(py, pz):
        for (ay, az), (by, bz), (cy, cz) in outline:
            d = (by - ay) * (cz - az) - (bz - az) * (cy - ay)
            if abs(d) < 1e-9:
                continue
            u = ((py - ay) * (cz - az) - (pz - az) * (cy - ay)) / d
            v = ((by - ay) * (pz - az) - (bz - az) * (py - ay)) / d
            if u >= -.06 and v >= -.06 and u + v <= 1.06:
                return True
        return False

    def make(side, w, h):
        if side not in ("east", "west"):
            return (lambda x, y, w, h: (0, 0, 0, 0)), ("clear",)
        pal = RAMPS["inner" if side == inner_side else "cloak"]

        def paint(x, y, w, h):
            pz = (zs - (x + .5) / w * 2 * zs) if side == "east" else (-zs + (x + .5) / w * 2 * zs)
            py = ys - (y + .5) / h * 2 * ys
            if not inside(py, pz):
                return (0, 0, 0, 0)
            r = _h(seed, x, y)
            if r < .008:
                return RAMPS["glow"][2] + (255,)
            t = 2 if (x + y // 2) % 7 == 0 else 1 if r < .55 else 2 if r < .9 else 0
            return pal[t] + (255,)
        return paint, ("membrane", seed, side, w, h)
    return make


# --- fitting the reference's mesh parts with oriented boxes -----------------------------------------------
def _no_roll(np, major):
    a = major / np.linalg.norm(major)
    up = np.array([0, 1.0, 0])
    u = up - (up @ a) * a
    if np.linalg.norm(u) < 1e-3:
        u = np.array([1.0, 0, 0]) - a[0] * a
    u /= np.linalg.norm(u)
    return np.column_stack([np.cross(u, a), u, a])


def fit_boxes(faces, slim=.9):
    """Oriented boxes for one mesh part: long axis from PCA, no roll, sliced along the long axis so a taper
    survives (1-3 slices); each slice fits the surface points inside it, then thins a little (`slim`)."""
    np = _np()
    pts = []
    for f in faces:
        v = [np.array(q, float) for q in f["v"]]
        for i in range(1, len(v) - 1):
            a, b, c = v[0], v[i], v[i + 1]
            n = 6
            for s in range(n + 1):
                for t in range(n + 1 - s):
                    pts.append(a + (b - a) * s / n + (c - a) * t / n)
    S = np.array(pts)
    c0 = S.mean(0); X = S - c0
    ev, U = np.linalg.eigh(X.T @ X)
    R = _no_roll(np, U[:, np.argmax(ev)])
    L = X @ R
    lo, hi = L.min(0), L.max(0)
    ln = hi[2] - lo[2]
    width = max(np.ptp(L[:, 0]), np.ptp(L[:, 1]), 1e-6)
    n = 1 if ln < 1.3 * width else 2 if ln < 2.6 * width else 3
    out = []
    for k in range(n):
        z0 = lo[2] + ln * k / n; z1 = lo[2] + ln * (k + 1) / n
        sel = L[(L[:, 2] >= z0 - .01) & (L[:, 2] <= z1 + .01)]
        if len(sel) < 4:
            continue
        a, b = sel[:, :2].min(0), sel[:, :2].max(0)
        cen = (a + b) / 2; half = (b - a) / 2 * slim
        out.append((c0 + R @ np.array([cen[0], cen[1], (z0 + z1) / 2]),
                    np.maximum(.4, [half[0], half[1], (z1 - z0) / 2 + .2]), R))
    return out


MATERIAL = (("pit", "glow"), ("gape", "mouth"), ("fang", "bone"), ("hook", "talon"), ("toe", "talon"),
            ("shear", "talon"), ("digit", "spar"), ("keel", "plate"), ("crown", "plate"), ("brow", "plate"),
            ("collar", "plate"), ("carapace", "plate"), ("knuckle", "plate"), ("throat", "belly"), ("jaw", "belly"))
FUR_BONES = {"body": 1.0, "neck": 1.0, "skull": .45, "tail": .8, "tail_2": .7, "tail_mid": .55,
             "wing_l": .7, "wing_r": .7}


def material_of(name):
    return next((m for k, m in MATERIAL if k in name), "hide")


def build():
    np = _np()
    m = Model("shade_wyrm", size=2048)
    rig = Rig(m)
    p = painter
    ref = json.loads((REF / "facets.json").read_text(encoding="utf-8"))
    new_pivots, xf = repose(ref)
    for b in ref["bones"]:
        b["pivot"] = new_pivots[b["name"]]
    for el in ref["elements"]:
        for f in el["faces"]:
            f["v"] = [[float(x) for x in xf[el["bone"]](q)] for q in f["v"]]
    M = np.diag([-1.0, 1, 1])

    def sided(name):
        return bool(name) and name.startswith(("leg_", "wing_", "crown_"))

    def right(name):
        if not sided(name):
            return name
        for a, b in (("leg_fl", "leg_fr"), ("leg_hl", "leg_hr"), ("wing_l", "wing_r"), ("crown_l", "crown_r")):
            name = name.replace(a, b)
        return name

    def cubes(spec, sign=1, suffix=""):
        items = []
        for name, a, b, paint in spec:
            if sign < 0:
                a, b = [-b[0], a[1], a[2]], [-a[0], b[1], b[2]]
            m.cube(f"{name}{suffix}", a, b, items, paint)
        return items

    def part(name, parent, pivot, rotation, spec, sign=1, suffix=""):
        piv = [pivot[0] * sign, pivot[1], pivot[2]]
        rotation = [rotation[0], rotation[1] * sign, rotation[2] * sign]
        return rig.bone(name, piv, rotation, cubes(spec, sign, suffix), parent)

    kids, pivots, order = {}, {}, []
    for b in ref["bones"]:
        for s in ((1, -1) if sided(b["name"]) else (1,)):
            name = b["name"] if s > 0 else right(b["name"])
            pivots[name] = [b["pivot"][0] * s, b["pivot"][1], b["pivot"][2]]
            order.append((name, b["parent"] if s > 0 else right(b["parent"])))
            kids[name] = []

    def oriented(name, bone, c, half, R, paint):
        items = []
        c = [float(v) for v in c]; half = [float(v) for v in half]
        m.cube(name, [c[i] - half[i] for i in range(3)], [c[i] + half[i] for i in range(3)], items, paint,
               rotation=matrix_euler([[float(v) for v in row] for row in R]), origin=c)
        kids[bone].extend(items)

    def fur_for(bone, name, c, half, R, scale, sign, seed):
        """Fringe plates hanging from both upper side edges of a box, tilted out, plus a crest on top."""
        x, y, z = R[:, 0], R[:, 1], R[:, 2]
        ln = max(4.0, half[1] * 2 * .75 * scale)
        for side in (1, -1):
            for k, (tilt, inset, f) in enumerate(((30, .0, 1.0), (14, .45, .75))):   # outer skirt, then an inner layer
                a = math.radians(tilt)
                d = (-math.cos(a)) * y + side * math.sin(a) * x          # hanging direction, out and down
                attach = c + side * half[0] * (1 - inset) * x + half[1] * y
                centre = attach + d * ln * f / 2
                Yp = -d; Zp = z; Xp = np.cross(Yp, Zp)
                oriented(f"{name}_fur{side}{k}", bone, centre, [.05, ln * f / 2, half[2] * .95], np.column_stack([Xp, Yp, Zp]),
                         fur_painter(seed + side * 3 + k))
        if bone in ("body", "neck", "tail"):
            cl = max(4.0, ln * .6)
            centre = c + half[1] * y + y * cl / 2
            Yp = -y; Zp = z; Xp = np.cross(Yp, Zp)   # strands start at the box and grow upward
            oriented(f"{name}_crest", bone, centre, [.05, cl / 2, half[2] * .9], np.column_stack([Xp, Yp, Zp]),
                     fur_painter(seed + 5, "fur"))

    def build_part(el, sign, bone, suffix):
        name = el["name"]
        if "panel" in name:
            return membrane(el, sign, bone, suffix)
        mat = material_of(name)
        faces = [{"v": [[q[0] * sign, q[1], q[2]] for q in f["v"]]} for f in el["faces"]]
        slim = .82 if bone.startswith("leg_") else .7 if bone.startswith("wing_") else .72 if bone == "neck" else .9
        for k, (c, half, R) in enumerate(fit_boxes(faces, slim)):
            seed = int(_h(len(name), k, sign) * 9973)
            oriented(f"{name}_{k}{suffix}", bone, c, half, R, box_painter(mat, seed))
            fur_scale = FUR_BONES.get(bone)
            if fur_scale and mat in ("hide", "plate") and half[2] > 2.5:
                fur_for(bone, f"{name}_{k}{suffix}", np.array(c), np.array(half), np.array(R), fur_scale, sign, seed)

    def membrane(el, sign, bone, suffix):
        verts = [q for f in el["faces"] for q in f["v"]]
        for k, f in enumerate(el["faces"]):
            v, _ = ordered({"v": f["v"], "uv": f["uv"]})
            v = [np.array([q[0] * sign, q[1], q[2]], float) for q in v]
            n = np.cross(v[1] - v[0], v[2] - v[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n /= np.linalg.norm(n)
            edges = [v[(i + 1) % len(v)] - v[i] for i in range(len(v))]
            zz = max(edges, key=lambda e: e @ e); zz = zz - n * (zz @ n)
            if np.linalg.norm(zz) < 1e-9:
                continue
            zz /= np.linalg.norm(zz); yy = np.cross(zz, n)
            c = sum(v) / len(v)
            ly = [(q - c) @ yy for q in v]; lz = [(q - c) @ zz for q in v]
            c = c + yy * (max(ly) + min(ly)) / 2 + zz * (max(lz) + min(lz)) / 2
            half = [.05, max(.2, (max(ly) - min(ly)) / 2), max(.2, (max(lz) - min(lz)) / 2)]
            tris = [(v[0], v[1], v[2])] + ([(v[0], v[2], v[3])] if len(v) == 4 else [])
            outline = [[[float((q - c) @ yy), float((q - c) @ zz)] for q in t] for t in tris]
            inner = "west" if n[0] * c[0] > 0 else "east"   # the side facing the centre line is the violet inside
            oriented(f"{el['name']}_{k}{suffix}", bone, c, half, np.column_stack([n, yy, zz]),
                     membrane_painter(outline, half, inner, int(_h(k, len(el["name"]), sign) * 9973)))

    for el in ref["elements"]:
        build_part(el, 1, el["bone"], "")
        if el["sided"]:
            build_part(el, -1, right(el["bone"]), "_r")

    # ---------- rage parts: horns, hidden until enraged ----------
    hp = [float(v) for v in xf["skull"]([9, 80, 56])]
    off = [hp[i] - (9, 80, 56)[i] for i in range(3)]
    horn = [("horn_base", [7.5, 78, 52], [11, 82, 60], p("bone", "bone")),
            ("horn_mid", [8, 78.5, 59], [10.5, 81.5, 68], p("bone", "bone")),
            ("horn_glow", [8.5, 79, 67], [10, 81, 74], p("glow", "glow"))]
    horn = [(n, [a[i] + off[i] for i in range(3)], [b[i] + off[i] for i in range(3)], q) for n, a, b, q in horn]
    for sign, s in ((1, "l"), (-1, "r")):
        kids["skull"].append(part(f"horn_{s}", "skull", hp, [-28, -12, 0], horn, sign, "_" + s))

    built = {}
    for name, parent in reversed(order):
        own = [k for k in kids[name] if isinstance(k, str)]
        sub = [k for k in kids[name] if not isinstance(k, str)]
        built[name] = rig.bone(name, pivots[name], [0, 0, 0], own, parent, sub + [built[c] for c, pp in order if pp == name])
    outliner = [built["body"]()]
    animate(m)
    return m, outliner


def animate(m):
    """Placeholder clips that keep the server contract; the motion pass comes after the shape OK."""
    zero = (0, 0, 0)
    breathe = {"body": [(0, zero, "position"), (1.0, (0, -0.6, 0), "position"), (2.0, zero, "position")]}
    bob = {"body": [(t, (0, -1 if t in (0.3, 0.9) else 0, 0), "position") for t in (0, 0.3, 0.6, 0.9, 1.2)]}
    m.anim("idle", 2.0, breathe, "loop")
    m.anim("walk", 1.2, bob, "loop")
    m.anim("idle_enraged", 2.0, breathe, "loop")
    m.anim("walk_enraged", 1.2, bob, "loop")
    for name, seconds in (("shade-sweep", 2.0), ("shade-pounce", 2.6), ("shade-dust", 2.6), ("shade-pulse", 2.3),
                          ("shade-breath", 2.6), ("shade-storm", 3.5), ("enrage", 2.6), ("death", 2.4)):
        m.anim(name, seconds, {"body": [(0, zero), (seconds, zero)]})


if __name__ == "__main__":
    model, outliner = build()
    out = ROOT / "assets/bosses/shade_wyrm.bbmodel"
    data = model.write(out, outliner)
    print(f"{out.relative_to(ROOT)}: {len(data['elements'])} cubes, {len(model.bones)} bones, {len(data['animations'])} animations")
