"""First draft of the 狂戦士の甲冑 helm and mantle as Blockbench models (Creator 2026-10-23: stop building boxes in
code, make it a Blockbench model).

    python3 scripts/seed_berserker_bbmodel.py           # write assets/armor/berserker_{helmet,mantle}.bbmodel
    python3 scripts/build_berserker_armor.py            # pack them (and the body layers) into the resource pack

assets/armor/*.bbmodel is the source of truth once someone edits it in Blockbench; this refuses to overwrite a
model that already exists unless --force is given.

Game 26.3 reads element rotations on all three axes at any angle ({"x", "y", "z"}, applied Z·Y·X, right-handed),
so the plates are laid by their two ends instead of on 22.5 degree steps. Generic Model format (no rotation limit);
coordinates are the Java item model's (0..16, front is north, -z; the head is 4..12 on every axis).

The helm follows the Knight Artorias helm of the reference (~/ProjectS/refs/artorias/fr/h_48.png), in black steel
and red: a broad middle plate from the crown down to a blunt tip leaning forward, side bands spread at the top and
running down and in, ending in hooks curling back, the face only darkness, three tall tabs on the crown, a plume.
"""
import argparse
import base64
import io
import json
import math
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

from build_berserker_armor import paint_face

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/armor"
DENSITY = 3


def box(name, lo, hi, mat, light=0, rotation=(0, 0, 0), origin=None):
    lo, hi = np.array(lo, float), np.array(hi, float)
    return {"name": name, "from": lo, "to": hi, "mat": mat, "light": light, "rotation": tuple(rotation),
            "origin": np.array(origin, float) if origin is not None else (lo + hi) / 2}


def euler_zyx(m):
    """Angles (x, y, z) in degrees with m = Rz(z)·Ry(y)·Rx(x)."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(m[2][1], m[2][2]); z = math.atan2(m[1][0], m[0][0])
    else:
        x = math.atan2(-m[1][2], m[1][1]); z = 0.0
    return tuple(round(math.degrees(a), 3) for a in (x, y, z))


def plate(name, top, bottom, width, thick, out, mat="iron", light=0):
    """A plate whose long side runs from bottom to top and whose face (north) looks along out."""
    top, bottom, out = np.array(top, float), np.array(bottom, float), np.array(out, float)
    up = top - bottom; length = np.linalg.norm(up); up /= length
    n = out - up * out.dot(up); n /= np.linalg.norm(n)
    zl = -n                          # the north face (-z local) looks out
    xl = np.cross(up, zl)
    m = np.column_stack([xl, up, zl])
    c = (top + bottom) / 2
    return box(name, c - [width / 2, length / 2, thick / 2], c + [width / 2, length / 2, thick / 2], mat, light, euler_zyx(m), c)


def frame(top, bottom, out):
    up = top - bottom; length = np.linalg.norm(up); up = up / length
    n = out - up * out.dot(up); n = n / np.linalg.norm(n)
    return up, n, np.cross(up, -n), length


def chain(name, points, width, thick, outs, mat="iron", light=0, bevel=False):
    """A plate bent along a polyline (top first): one segment per span, each a little longer so the joints close,
    with narrow bevels along both long edges turned 40 degrees off the face (the reference's cut edges)."""
    pts = [np.array(p, float) for p in points]
    widths = width if isinstance(width, (list, tuple)) else [width] * (len(pts) - 1)
    outs = outs if isinstance(outs[0], (list, tuple)) else [outs] * (len(pts) - 1)
    out = []
    dirs = [(pts[i] - pts[i + 1]) / np.linalg.norm(pts[i] - pts[i + 1]) for i in range(len(pts) - 1)]

    def reach(a, b):
        # How far a segment must run past a joint so the outside of the bend stays closed (no seam).
        bend = math.acos(max(-1.0, min(1.0, float(a @ b))))
        return .25 + min(1.2, thick * math.tan(bend / 2) + w * .5 * math.sin(bend))
    for i in range(len(pts) - 1):
        top, bottom, o, w = pts[i], pts[i + 1], np.array(outs[i], float), widths[i]
        up, n, side, length = frame(top, bottom, o)
        t_ext = up * (reach(dirs[i - 1], dirs[i]) if i > 0 else .15)
        b_ext = up * (reach(dirs[i], dirs[i + 1]) if i < len(dirs) - 1 else .15)
        out.append(plate(name, top + t_ext, bottom - b_ext, w, thick, n, mat, light))
        if bevel:
            for sgn in (-1, 1):
                edge = side * sgn * (w / 2) + n * (thick / 2 - .15)
                o2 = n * math.cos(math.radians(40)) + side * sgn * math.sin(math.radians(40))
                out.append(plate(name + " bevel", top + t_ext + edge, bottom - b_ext + edge, .8, .5, o2, mat, light))
    return out


# Creator 2026-10-23: next to Artorias the head read too big. Everything outside the shell is drawn toward the
# head's middle (more in height and depth than in width, where the shell must still cover the head) and thinned.
# Creator 2026-10-23: next to Artorias the head read too big, and could be smaller still. The shell cannot shrink
# (it covers the game's head), so what sticks out past it is pressed back toward it: in each axis the part beyond
# the shell's box keeps only SQUASH of its reach, and the parts are thinned to THIN. The shell's own parts stay.
SHELL_LO, SHELL_HI = np.array((3.8, 3.0, 3.9)), np.array((12.2, 12.5, 12.4))
SQUASH = np.array((.7, .6, .6))
THIN = .85
TIGHT = ("shell", "neck guard", "face dark", "side")


def press(c):
    below, above = np.minimum(c - SHELL_LO, 0), np.maximum(c - SHELL_HI, 0)
    return c - (below + above) * (1 - SQUASH)


def pulled_in(elements):
    for e in elements:
        if e["name"] in TIGHT:
            continue
        c = e["origin"]; size = e["to"] - e["from"]; mid = (e["from"] + e["to"]) / 2
        moved = press(c)
        mid = mid + (moved - c)
        half = size * THIN / 2
        e["from"], e["to"], e["origin"] = mid - half, mid + half, moved
    return elements


class Mesh:
    """A Blockbench mesh being built: vertices, and faces wound so they look away from a point inside."""

    def __init__(self, name):
        self.name, self.verts, self.faces = name, [], []

    def v(self, p):
        self.verts.append(np.array(p, float)); return len(self.verts) - 1

    def face(self, ids, mat, inside):
        pts = [self.verts[i] for i in ids]
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        if n @ (sum(pts) / len(pts) - np.array(inside, float)) < 0:
            ids = ids[::-1]
        self.faces.append((list(ids), mat))

    def loft(self, rings, mat, top=None, bottom=None):
        """Closed rings stacked bottom to top, joined by quads, capped with a fan to a point at each end."""
        ids = [[self.v(p) for p in ring] for ring in rings]
        centre = [np.mean(np.array(r, float), axis=0) for r in rings]
        for k in range(len(rings) - 1):
            mid = (centre[k] + centre[k + 1]) / 2
            for i in range(len(rings[k])):
                j = (i + 1) % len(rings[k])
                self.face([ids[k][i], ids[k][j], ids[k + 1][j], ids[k + 1][i]], mat, mid)
        for cap, ring, c in ((top, ids[-1], centre[-1]), (bottom, ids[0], centre[0])):
            if cap is not None:
                tip = self.v(cap)
                for i in range(len(ring)):
                    self.face([ring[i], ring[(i + 1) % len(ring)], tip], mat, c + (c - np.array(cap)) * .5)

    def ribbon(self, points, widths, thick, mat, away):
        """A plate with thickness along a curve: one cross-section per point, the joints shared (no seams). Its face
        looks away from the point `away`; widths and thickness may change along it."""
        pts = [np.array(p, float) for p in points]
        widths = widths if isinstance(widths, (list, tuple)) else [widths] * len(pts)
        secs = []
        for i, p in enumerate(pts):
            t = pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]; t /= np.linalg.norm(t)
            g = p - np.array(away, float); g -= t * (g @ t); g /= np.linalg.norm(g)
            side = np.cross(g, t); side /= np.linalg.norm(side)
            w = widths[i] / 2
            secs.append([self.v(p - side * w), self.v(p + side * w), self.v(p + side * w + g * thick), self.v(p - side * w + g * thick)],
                        )
            secs[-1].append(p + g * thick / 2)
        for k in range(len(secs) - 1):
            a, b = secs[k], secs[k + 1]
            inside = (a[4] + b[4]) / 2
            for i in range(4):
                j = (i + 1) % 4
                self.face([a[i], a[j], b[j], b[i]], mat, inside)
        for sec in (secs[0], secs[-1]):
            self.face(sec[:4], mat, sec[4] + (sec[4] - secs[len(secs) // 2][4]) * -.01)


def squircle(y, hw, zc, front, back, n=16, power=.3):
    """A ring round the head at height y: nearly square in plan with rounded corners, so it covers the corners of
    the game's head (a square) without growing much past its sides."""
    out = []
    for k in range(n):
        a = 2 * math.pi * k / n
        sx, cz = math.sin(a), math.cos(a)
        x = 8 + hw * math.copysign(abs(sx) ** power, sx)
        z = zc - (front if cz > 0 else back) * math.copysign(abs(cz) ** power, cz)
        out.append((x, y, z))
    return out


MATS = {"iron": (112, 106, 124), "shell": (64, 59, 72), "dark": (42, 37, 48), "cloth": (196, 42, 34), "eye": (255, 74, 44)}


def helm():
    """Creator 2026-10-23: a real shape, not boxes. A mesh: the skull lofted from rings, the plates as ribbons
    lying on it whose cross-sections share their corners (no seams), lit by their real facing when packed."""
    m = Mesh("helm")
    rings = [squircle(2.8, 2.4, 6.6, 3.0, 2.2), squircle(4.0, 4.45, 8.1, 4.45, 4.5), squircle(7.0, 4.45, 8.1, 4.45, 4.5),
             squircle(10.0, 4.45, 8.1, 4.45, 4.5), squircle(12.5, 4.45, 8.1, 4.4, 4.5), squircle(13.3, 3.5, 8.3, 3.3, 3.6)]
    m.loft(rings, "shell", top=(8, 13.8, 8.4), bottom=(8, 2.6, 6.8))
    centre = (8, 8, 9)
    P = lambda pts: [press(np.array(p, float)) for p in pts]
    # The middle plate from the back of the crown over the brow and down to a blunt tip, narrowing.
    m.ribbon(P([(8, 13.95, 9.0), (8, 14.0, 6.2), (8, 13.2, 3.6), (8, 9.8, 2.4), (8, 6.0, 1.5), (8, 2.6, 1.0), (8, 1.2, 1.0)]),
             [3.0, 3.6, 4.2, 4.0, 3.4, 2.6, 2.0], 1.0, "iron", centre)
    for s in (-1, 1):
        # The side band from the top corner round the brow, down and in toward the tip.
        m.ribbon(P([(8 + s * 3.4, 13.7, 7.4), (8 + s * 4.4, 12.6, 4.6), (8 + s * 4.7, 10.6, 3.4), (8 + s * 4.3, 7.0, 2.6), (8 + s * 3.0, 3.2, 1.8)]),
                 [2.4, 3.2, 3.4, 3.2, 2.4], .9, "iron", centre)
        # The hook: the band's end curls back along the cheek and turns up.
        m.ribbon(P([(8 + s * 3.0, 3.0, 1.8), (8 + s * 4.4, 2.6, 2.8), (8 + s * 5.1, 3.4, 4.6), (8 + s * 5.0, 5.6, 6.0)]),
                 [1.6, 1.7, 1.6, 1.3], .9, "iron", (8, 6, 7))
        # The side tabs on the crown, leaning out.
        m.ribbon(P([(8 + s * 3.3, 13.0, 7.0), (8 + s * 3.8, 15.0, 7.5), (8 + s * 4.2, 16.4, 8.0)]), [1.9, 1.7, 1.4], 1.4, "iron",
                 (8 + s * 3.0, 14.5, 12))
    # The middle tab, highest.
    m.ribbon(P([(8, 13.2, 6.4), (8, 15.6, 6.9), (8, 17.6, 7.6)]), [2.8, 2.6, 2.2], 1.8, "iron", (8, 15, 12))
    # The plume: crimson streaming back off the crown and falling.
    for i, (dx, length, drop, w) in enumerate(((-1.0, 11, 6.5, 2.4), (1.1, 13, 8.0, 2.4), (0, 9, 4.5, 2.6), (.2, 15, 10.0, 1.8))):
        top = np.array((8 + dx, 13.7 - .2 * i, 10.0))
        m.ribbon(P([top, top + (dx * .1, .2, length * .3), top + (dx * .2, -drop * .4, length * .65), top + (dx * .3, -drop, length)]),
                 [w, w, w * .9, w * .7], .7, "cloth", top + (0, -4, 4))
    return [{"mesh": m}]


def mantle():
    """The shoulders, skirt and cape as they were (single-axis plates), carried over as the first draft."""
    out = []
    for b in mantle_parts():
        r = b.get("rotation")
        rot = (0, 0, 0)
        if r:
            rot = tuple(r["angle"] if a == r["axis"] else 0 for a in "xyz")
        out.append(box(b["name"], b["from"], b["to"], b["mat"], b["light"], rot, r["origin"] if r else None))
    return out


# The mantle as it was built in code before (2026-10-23), kept as the first draft.

def el(name, lo, hi, mat, rot=None, light=0, flat=False):
    """One box; rot = (axis, angle, origin). light nudges the whole box brighter or darker; flat leaves out the
    lit top edge (for parts stacked into one surface, where the edges would read as stripes)."""
    e = {"name": name, "from": list(lo), "to": list(hi), "mat": mat, "light": light, "flat": flat}
    if rot:
        e["rotation"] = {"origin": list(rot[2]), "axis": rot[0], "angle": rot[1], "rescale": False}
    return e




def mantle_parts():
    """Body space of the preview: body x4..12, y12..24, z6..10, arms x0..4 / x12..16, front is low z. Model
    rotations: +x swings a part's lower end forward, -z swings its lower end out to -x.

    Creator 2026-10-23: the silhouette was too far from Artorias. His reads as an inverted triangle (pauldrons
    stacked and falling away outward, narrow waist), a skirt of cloth flaring from the belt to the knee (half his
    outline), cloth over the shoulders, a small tall helm with a plume streaming back."""
    e = []
    # Pauldrons: a round cop over the shoulder and three lames below it, each further out, the whole falling away
    # from the neck. The left one (+x) bigger, Guts's iron arm side.
    for s, big in ((-1, 0.0), (1, 0.5)):
        cx = 8 + s * 6.2
        fall = ("z", -s * 22.5, (cx, 24.6, 8.0))
        e += [el("pauldron cop", (cx - 3.0 - big, 23.4, 4.2 - big * .4), (cx + 3.0 + big, 25.8 + big * .4, 11.8 + big * .4), "iron", fall, light=1),
              el("pauldron cop", (cx - 2.2 - big, 25.6 + big * .4, 5.0), (cx + 2.2 + big, 26.6 + big * .6, 11.0), "iron", fall, light=1),
              el("pauldron ridge", (cx - .4, 26.4 + big * .6, 5.4), (cx + .4, 27.4 + big * .8, 10.6), "iron", fall, light=2)]
        for i in range(3):
            y1 = 23.6 - 1.3 * i
            out = s * .45 * (i + 1)
            e += [el("pauldron lame", (cx - 2.8 - big + out, y1 - 1.6, 4.4 + .2 * i), (cx + 2.8 + big + out, y1, 11.6 - .2 * i), "iron", fall,
                     light=0 if i % 2 else -1)]
    # The belt and the skirt: crimson flaps flaring from the belt to the knee, metal tassets over the front, torn hem.
    e += [el("belt", (3.0, 11.8, 4.6), (13.0, 13.0, 11.4), "iron", light=1)]
    front, back = ("x", 11.25, (8.0, 12.6, 4.7)), ("x", -11.25, (8.0, 12.6, 11.3))
    e += [el("skirt front", (2.8, 4.2, 4.4), (13.2, 12.6, 5.0), "cloth", front, light=1),
          el("skirt back", (2.8, 3.8, 11.0), (13.2, 12.6, 11.6), "cloth", back)]
    for s in (-1, 1):
        x0, x1 = (2.2, 3.0) if s < 0 else (13.0, 13.8)
        side = ("z", s * 22.5, ((x0 + x1) / 2, 12.6, 8.0))
        e += [el("skirt side", (x0, 4.6, 4.8), (x1, 12.6, 11.2), "cloth", side, light=-1)]
        for k, bottom in enumerate((2.6, 3.8, 2.0)):
            e += [el("hem", (x0, bottom, 5.0 + 2.0 * k), (x1, 4.8, 6.9 + 2.0 * k), "cloth", side, light=-1 - k % 2)]
        e += [el("tasset", (8 + s * 3.0 - 2.0, 9.4, 4.0), (8 + s * 3.0 + 2.0, 12.6, 4.5), "iron", front, light=1)]
    for i, (x, bottom) in enumerate(((2.8, 2.0), (4.6, 3.4), (6.2, 1.2), (7.8, 2.8), (9.4, 1.6), (11.2, 3.0))):
        e += [el("hem", (x, bottom, 4.45), (x + 1.8, 4.4, 4.95), "cloth", front, light=i % 2)]
        e += [el("hem", (x, bottom - .6, 11.05), (x + 1.8, 4.0, 11.55), "cloth", back, light=-(i % 2))]
    # Cloth over the shoulders and down the back, under the pauldrons.
    e += [el("hood front", (4.6, 22.6, 4.8), (11.4, 24.8, 6.4), "cloth", ("x", -22.5, (8.0, 23.6, 5.6)), light=1),
          el("hood back", (3.4, 20.6, 9.6), (12.6, 25.4, 12.0), "cloth"),
          el("shoulder drape", (11.6, 18.0, 4.8), (14.0, 24.6, 11.6), "cloth", ("z", -22.5, (12.8, 24.0, 8.0)), light=-1)]
    e += [el("cape", (3.6, 9.0, 11.6), (12.4, 21.0, 12.4), "cloth")]
    for i, (x, bottom) in enumerate(((3.6, 3.0), (4.8, 6.8), (5.8, 1.2), (7.0, 5.0), (8.0, 7.6), (9.0, 2.2), (10.2, 6.0), (11.2, 4.0))):
        e += [el("cape strip", (x, bottom, 11.7 + .1 * (i % 2)), (x + 1.2, 9.2, 12.3 + .1 * (i % 2)), "cloth", light=-(i % 2))]
    # A torn scarf trailing back from the right shoulder.
    for i in range(3):
        e += [el("scarf", (2.4 + .6 * i, 23.0 - .8 * i, 11.0), (3.4 + .6 * i, 24.0 - .8 * i, 17.0 - 1.4 * i), "cloth",
                 ("x", 22.5, (3.0, 23.5, 11.0)), light=1 - i % 2)]
    return e


def face_size(e, face):
    dx, dy, dz = e["to"] - e["from"]
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


LIGHT = np.array((-.4, .75, -.55)) / np.linalg.norm((-.4, .75, -.55))   # from above, front and left
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def rotation_matrix(x, y, z):
    x, y, z = (math.radians(a) for a in (x, y, z))
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def face_level(e, face):
    """How lit a face is (0..5) from where it really faces after its rotation: the plates' angles do the shading,
    so the game's own face shading is turned off for these cubes (shade: false)."""
    n = rotation_matrix(*e["rotation"]) @ np.array(NORMALS[face], float)
    return .1 + 5.4 * max(0.0, float(n @ LIGHT)) ** 2 + e["light"]


def bbmodel(name, elements, size=1024):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cx = cy = row = 0
    cubes = []
    meshes = [e["mesh"] for e in elements if "mesh" in e]
    elements = [e for e in elements if "mesh" not in e]
    for e in elements:
        faces = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            fw, fh = face_size(e, face)
            w, h = max(2, math.ceil(fw * DENSITY)), max(2, math.ceil(fh * DENSITY))
            if cx + w > size: cx, cy, row = 0, cy + row, 0
            assert cy + h <= size, f"{name} texture is full"
            paint_face(img, cx, cy, w, h, e["mat"], face_level(e, face), True)
            faces[face] = {"uv": [cx, cy, cx + w, cy + h], "texture": 0}
            cx += w; row = max(row, h)
        cubes.append({"name": e["name"], "box_uv": False, "rescale": False, "locked": False,
                      "from": [round(float(v), 4) for v in e["from"]], "to": [round(float(v), 4) for v in e["to"]],
                      "autouv": 0, "color": 0, "origin": [round(float(v), 4) for v in e["origin"]],
                      "rotation": list(e["rotation"]), "shade": False, "faces": faces, "type": "cube",
                      "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/{name}/{len(cubes)}"))})
    mats = list(MATS)
    for m in meshes:
        keys = [f"v{i}" for i in range(len(m.verts))]
        cubes.append({"name": m.name, "color": 0, "origin": [0, 0, 0], "rotation": [0, 0, 0], "export": True, "visibility": True,
                      "locked": False, "render_order": "default", "allow_mirror_modeling": True, "type": "mesh",
                      "vertices": {k: [round(float(c), 4) for c in m.verts[i]] for i, k in enumerate(keys)},
                      "faces": {f"f{n}": {"uv": {keys[i]: [0, 0] for i in ids}, "vertices": [keys[i] for i in ids],
                                          "texture": 1 + mats.index(mat)} for n, (ids, mat) in enumerate(m.faces)},
                      "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/{name}/mesh/{m.name}"))})
    png = io.BytesIO(); img.save(png, "PNG")

    def swatch(i, mat):
        b = io.BytesIO(); Image.new("RGBA", (16, 16), MATS[mat] + (255,)).save(b, "PNG")
        return {"path": "", "name": f"{mat}.png", "folder": "", "namespace": "", "id": str(i), "particle": False,
                "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True, "saved": False,
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/{name}/swatch/{mat}")),
                "width": 16, "height": 16, "uv_width": 16, "uv_height": 16,
                "source": "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()}
    return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
            "name": name, "model_identifier": "", "visible_box": [1, 1, 0], "resolution": {"width": size, "height": size},
            "elements": cubes, "outliner": [c["uuid"] for c in cubes],
            "textures": [{"path": "", "name": f"{name}.png", "folder": "", "namespace": "", "id": "0", "particle": True,
                          "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True, "saved": False,
                          "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"projects/{name}/texture")),
                          "width": size, "height": size, "uv_width": size, "uv_height": size,
                          "source": "data:image/png;base64," + base64.b64encode(png.getvalue()).decode()}]
            + [swatch(i + 1, mat) for i, mat in enumerate(mats)]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    SOURCE.mkdir(parents=True, exist_ok=True)
    for name, elements in (("berserker_helmet", helm()), ("berserker_mantle", mantle())):
        path = SOURCE / f"{name}.bbmodel"
        if path.exists() and not args.force:
            raise SystemExit(f"{path} exists (edited in Blockbench?). Use --force to overwrite it.")
        path.write_text(json.dumps(bbmodel(name, elements), indent=1) + "\n", encoding="utf-8")
        print(path)


if __name__ == "__main__":
    main()
