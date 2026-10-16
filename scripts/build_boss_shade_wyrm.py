"""黒翳竜 (shade wyrm) Blockbench model, second pass ("外套の死神" direction chosen by the creator).

    python scripts/build_boss_shade_wyrm.py   # writes assets/bosses/shade_wyrm.bbmodel

The written .bbmodel is the committed source. Once someone edits it in Blockbench, do not re-run
this script over it without merging by hand.

Read of the design: a blind wyrm that walks on its wing-arms. It crouches front-low: hips high,
chest and head low, the wing elbows up over the shoulders. The membranes hang from the elbows like
a torn black cloak, and a pale eyeless brow looks out from under a shaggy hood. Enraged it opens
the cloak (violet inside), raises two feelers and grows two horns.

Front is +Z, +X is the wyrm's left, 16 px = 1 block. Cubes are axis-aligned inside their bone;
every angle lives on a bone (rest rotation, Blockbench editor convention: euler ZYX, right-handed).
Animation names are the server contract: idle / walk / idle_enraged / walk_enraged / enrage /
death, plus one `once` animation per ability id (length = telegraph+recovery, impact at telegraph).
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
THIN = .06   # half thickness of a facet plate, px


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


def element_colour(sampler, el):
    """The reference's own colour for a part, averaged over its faces' texture (a part keeps one hue)."""
    tot, n = [0, 0, 0], 0
    for f in el["faces"]:
        uv = f["uv"]
        for i in range(len(uv)):
            for j in range(i + 1, len(uv)):
                u, v = (uv[i][0] + uv[j][0]) / 2, (uv[i][1] + uv[j][1]) / 2
                c = sampler(f["tex"], u, v)
                if c[3] > 8:
                    tot = [tot[k] + c[k] for k in range(3)]; n += 1
    return tuple(int(t / max(1, n)) for t in tot) + (255,)


class TextureSampler:
    def __init__(self):
        from PIL import Image
        meta = json.loads((REF / "facets.json").read_text(encoding="utf-8"))["textures"]
        self.tex = []
        for t in meta:
            im = Image.open(REF / t["file"]).convert("RGBA")
            self.tex.append((im.load(), im.width, im.height, t["uv_width"], t["uv_height"]))

    def __call__(self, index, u, v):
        px, w, h, uw, uh = self.tex[index]
        x = min(w - 1, max(0, int(u / uw * w)))
        y = min(h - 1, max(0, int(v / uh * h)))
        return px[x, y]


def facet_painter(sampler, tris3d, uvs, tex, R, c, half, glow=None, vnormals=None, base=None):
    """Paint a plate's two big faces from the reference texture, transparent outside the face outline.
    The plate's local X is the face normal; (y, z) span the face (see build: cube centred on c, rotated by R).
    Light is baked from normals interpolated across the face (smooth shading), so neighbouring plates
    blend into one surface instead of showing their seams."""
    ys, zs = half[1], half[2]
    loc = []
    for k, (tri, uv) in enumerate(zip(tris3d, uvs)):
        pts = [[_dot(_sub(p, c), [R[0][1], R[1][1], R[2][1]]), _dot(_sub(p, c), [R[0][2], R[1][2], R[2][2]])] for p in tri]
        loc.append((pts, uv, vnormals[k] if vnormals else None))

    def colour(py, pz):
        for (a, b, d), (ua, ub, ud), ns in loc:
            det = (b[0] - a[0]) * (d[1] - a[1]) - (b[1] - a[1]) * (d[0] - a[0])
            if abs(det) < 1e-9:
                continue
            s = ((py - a[0]) * (d[1] - a[1]) - (pz - a[1]) * (d[0] - a[0])) / det
            t = ((b[0] - a[0]) * (pz - a[1]) - (b[1] - a[1]) * (py - a[0])) / det
            if s >= -.08 and t >= -.08 and s + t <= 1.08:   # a little overlap closes the cracks between plates
                if glow:
                    return glow, None
                s, t = max(0.0, s), max(0.0, t)
                n = None
                if ns:
                    n = _unit([ns[0][i] + s * (ns[1][i] - ns[0][i]) + t * (ns[2][i] - ns[0][i]) for i in range(3)])
                if base is not None:
                    world = [c[i] + R[i][1] * py + R[i][2] * pz for i in range(3)]
                    return surface_colour(base, world), n
                u = ua[0] + s * (ub[0] - ua[0]) + t * (ud[0] - ua[0])
                v = ua[1] + s * (ub[1] - ua[1]) + t * (ud[1] - ua[1])
                return sampler(tex, u, v), n
        return (0, 0, 0, 0), None

    def make(side, w, h):
        if side not in ("east", "west"):
            return (lambda x, y, w, h: (0, 0, 0, 0)), ("clear",)
        cache = {}
        flip = 1 if side == "east" else -1

        def paint(x, y, w, h):
            key = (x, y)
            if key not in cache:
                pz = (zs - (x + .5) / w * 2 * zs) if side == "east" else (-zs + (x + .5) / w * 2 * zs)
                py = ys - (y + .5) / h * 2 * ys
                col, n = colour(py, pz)
                if col[3] and n:
                    col = bake(col, [flip * v for v in n])
                cache[key] = col
            return cache[key]
        return paint, ("facet", id(loc), side, w, h)
    return make


GLOW_PARTS = ("pit",)


def _hash3(x, y, z):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def solid_noise(p, scale):
    """Trilinear value noise in model space: continuous across plates, so no seams."""
    q = [v / scale for v in p]
    i = [math.floor(v) for v in q]
    f = [v - k for v, k in zip(q, i)]
    f = [t * t * (3 - 2 * t) for t in f]
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[0] if dx else 1 - f[0]) * (f[1] if dy else 1 - f[1]) * (f[2] if dz else 1 - f[2])
                out += w * _hash3(i[0] + dx, i[1] + dy, i[2] + dz)
    return out


def surface_colour(base, p):
    """Base hue with soft large mottling and a faint 1 px grain (reads as Minecraft pixels, but continuous)."""
    k = .88 + .22 * solid_noise(p, 9.0) + .08 * (solid_noise(p, 1.3) - .5)
    return tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,)


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
}
LIGHT = _unit([.35, .85, .55])


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
    for name in list(piv):
        if name not in leg_bones:
            xf[name] = body
            piv[name] = body(piv[name])
    return {k: [float(x) for x in v] for k, v in piv.items()}, xf


def bake(colour, n):
    """Baked key light on each facet, so the form reads even under Minecraft's flat item shading."""
    k = .56 + .55 * max(0.0, _dot(n, LIGHT)) + .12 * n[1]
    k = max(.42, min(1.22, k))
    return tuple(min(255, int(c * k)) for c in colour[:3]) + (colour[3],)




def build():
    m = Model("shade_wyrm", size=2048)
    rig = Rig(m)
    p = painter
    ref = json.loads((REF / "facets.json").read_text(encoding="utf-8"))
    sampler = TextureSampler()
    new_pivots, xf = repose(ref)
    for b in ref["bones"]:
        b["pivot"] = new_pivots[b["name"]]
    for el in ref["elements"]:
        for f in el["faces"]:
            f["v"] = [[float(x) for x in xf[el["bone"]](q)] for q in f["v"]]

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

    # ---------- skeleton: the reference's joints, both sides ----------
    kids, pivots, order = {}, {}, []
    for b in ref["bones"]:
        for s in ((1, -1) if sided(b["name"]) else (1,)):
            name = b["name"] if s > 0 else right(b["name"])
            pivots[name] = [b["pivot"][0] * s, b["pivot"][1], b["pivot"][2]]
            order.append((name, b["parent"] if s > 0 else right(b["parent"])))
            kids[name] = []

    # ---------- surface: every reference face becomes a thin plate cube, cut to its outline ----------
    def plates(el, sign, bone, suffix):
        verts = [q for f in el["faces"] for q in f["v"]]
        centre = [sum(q[i] for q in verts) / len(verts) * (sign if i == 0 else 1) for i in range(3)]
        glow = PALETTES["glow"][1] + (255,) if any(k in el["name"] for k in GLOW_PARTS) else None
        # Smooth vertex normals: area-weighted outward face normals summed per shared vertex position.
        acc = {}
        faces = []
        for f in el["faces"]:
            v, uv = ordered(f)
            v = [[q[0] * sign, q[1], q[2]] for q in v]
            fn = _cross(_sub(v[1], v[0]), _sub(v[2], v[0]))
            if len(v) == 4:
                fn = [fn[i] + _cross(_sub(v[2], v[0]), _sub(v[3], v[0]))[i] for i in range(3)]
            cc = [sum(q[i] for q in v) / len(v) for i in range(3)]
            if _dot(fn, _sub(cc, centre)) < 0:
                fn = [-x for x in fn]
            for q in v:
                key = tuple(round(x, 2) for x in q)
                acc[key] = [acc.get(key, [0, 0, 0])[i] + fn[i] for i in range(3)]
            faces.append((f, v, uv))
        vn = lambda q: _unit(acc[tuple(round(x, 2) for x in q)]) or [0, 1, 0]
        base = element_colour(sampler, el)
        for k, (f, v, uv) in enumerate(faces):
            tris = [(v[0], v[1], v[2])] + ([(v[0], v[2], v[3])] if len(v) == 4 else [])
            tuv = [(uv[0], uv[1], uv[2])] + ([(uv[0], uv[2], uv[3])] if len(v) == 4 else [])
            n = None
            for t in tris:
                n = _unit(_cross(_sub(t[1], t[0]), _sub(t[2], t[0])))
                if n:
                    break
            if not n:
                continue
            c = [sum(q[i] for q in v) / len(v) for i in range(3)]
            if _dot(n, _sub(c, centre)) < 0:   # local X (the east face) points out of the volume
                n = [-x for x in n]
            edges = [_sub(v[(i + 1) % len(v)], v[i]) for i in range(len(v))]
            z = max(edges, key=lambda e: _dot(e, e))
            z = _unit(_sub(z, [n[i] * _dot(z, n) for i in range(3)]))
            if not z:
                continue
            y = _cross(z, n)
            R = [[n[0], y[0], z[0]], [n[1], y[1], z[1]], [n[2], y[2], z[2]]]
            ly = [_dot(_sub(q, c), y) for q in v]
            lz = [_dot(_sub(q, c), z) for q in v]
            off = [(max(ly) + min(ly)) / 2, (max(lz) + min(lz)) / 2]
            c = [c[i] + y[i] * off[0] + z[i] * off[1] for i in range(3)]
            half = [THIN, max(.15, (max(ly) - min(ly)) / 2), max(.15, (max(lz) - min(lz)) / 2)]
            tvn = [[vn(q) for q in t] for t in tris]
            paint = facet_painter(sampler, tris, tuv, f["tex"], R, c, half, glow, tvn, base)
            items = []
            m.cube(f"{el['name']}_{k}{suffix}", [c[0] - half[0], c[1] - half[1], c[2] - half[2]],
                   [c[0] + half[0], c[1] + half[1], c[2] + half[2]], items, paint,
                   rotation=matrix_euler(R), origin=c)
            kids[bone].extend(items)

    for el in ref["elements"]:
        plates(el, 1, el["bone"], "")
        if el["sided"]:
            plates(el, -1, right(el["bone"]), "_r")

    # ---------- rage parts: horns (hidden until enraged) and the two feelers ----------
    # Horns are authored on the reference head; the re-pose moves them with it.
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
