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


ANATOMY = ROOT / "assets/bosses/shade_wyrm_anatomy.json"


def material_for(name):
    """Texture by anatomical part (anatomy JSON names come from the Creator's noctveil reference)."""
    n = name
    if "pit" in n:
        return painter("glow", "glow")
    if "gape" in n:
        return painter("mouth")
    if "fang" in n:
        return painter("fang", "bone")
    if any(k in n for k in ("toe", "hook", "shear")):
        return painter("talon")
    if "digit" in n:
        return painter("spar", "bone")
    if any(k in n for k in ("keel", "crown", "brow", "carapace", "collar", "knuckle", "palm", "ankle")):
        return painter("scale", "scales")
    return painter("hide", "scales")


def masked(base, outline, half):
    """Cut a membrane plate to its panel outline: the big faces show only inside the 2D triangles (local y, z);
    the thin edge faces are left empty so the plate reads as the panel shape, not its bounding box."""
    def inside(py, pz):
        for (ay, az), (by, bz), (cy, cz) in outline:
            d = (by - ay) * (cz - az) - (bz - az) * (cy - ay)
            if abs(d) < 1e-9:
                continue
            u = ((py - ay) * (cz - az) - (pz - az) * (cy - ay)) / d
            v = ((by - ay) * (pz - az) - (bz - az) * (py - ay)) / d
            if u >= -.02 and v >= -.02 and u + v <= 1.02:
                return True
        return False

    def make(side, w, h):
        if side not in ("east", "west"):
            return (lambda x, y, w, h: (0, 0, 0, 0)), ("clear", w, h)
        paint, key = base(side, w, h)

        def cut(x, y, w, h):
            pz = (half[2] - (x + .5) / w * 2 * half[2]) if side == "east" else (-half[2] + (x + .5) / w * 2 * half[2])
            py = half[1] - (y + .5) / h * 2 * half[1]
            return paint(x, y, w, h) if inside(py, pz) else (0, 0, 0, 0)
        return cut, key + (id(outline),)
    return make


def build():
    m = Model("shade_wyrm", size=1024)
    rig = Rig(m)
    p = painter
    anatomy = json.loads(ANATOMY.read_text(encoding="utf-8"))
    MIRROR = [[-1, 0, 0], [0, 1, 0], [0, 0, 1]]

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

    def part(name, parent, pivot, rotation, spec, sign=1, suffix="", children=()):
        piv = [pivot[0] * sign, pivot[1], pivot[2]]
        rotation = [rotation[0], rotation[1] * sign, rotation[2] * sign]
        return rig.bone(name, piv, rotation, cubes(spec, sign, suffix), parent, list(children))

    def spike(name, parent, base, rotation, length, width, sign=1):
        """A tapered spike pointing along -Z from `base` before rotation: thick root, thin glowing tip."""
        x, y, z = base
        w2, w3 = width * .65, width * .35
        return part(name, parent, base, rotation, [
            (f"{name}_root", [x - width / 2, y, z - length * .5], [x + width / 2, y + width, z], p("scale", "scales")),
            (f"{name}_mid", [x - w2 / 2, y + (width - w2) / 2, z - length * .8], [x + w2 / 2, y + (width + w2) / 2, z - length * .5], p("hide")),
            (f"{name}_glow", [x - w3 / 2, y + (width - w3) / 2, z - length], [x + w3 / 2, y + (width + w3) / 2, z - length * .8], p("glow", "glow")),
        ], sign)

    # ---------- skeleton and body from the anatomy (both sides) ----------
    kids, pivots = {}, {}
    order = []
    for b in anatomy["bones"]:
        for side in ((1, -1) if sided(b["name"]) else (1,)):
            name = b["name"] if side > 0 else right(b["name"])
            parent = b["parent"] if side > 0 else right(b["parent"])
            pivots[name] = [b["pivot"][0] * side, b["pivot"][1], b["pivot"][2]]
            order.append((name, parent))
            kids.setdefault(name, [])
    for name, parent in order:
        rig.world[name] = IDENTITY

    def oriented(pt, sign, bone, suffix=""):
        """One anatomy box as its own bone: pivot at the box centre, rest rotation = the box frame."""
        R = pt["rotation"]
        c = [pt["center"][0] * sign, pt["center"][1], pt["center"][2]]
        if sign < 0:
            R = _mul(MIRROR, _mul(R, MIRROR))
        h = pt["half"]
        paint = material_for(pt["name"])
        if "panel" in pt["name"]:
            # Membrane plates: local X is the normal; the face toward the body is the violet inside.
            normal_x = R[0][0]
            inner = "west" if normal_x * c[0] > 0 else "east"
            paint = p("cloak", "membrane" if "tension" in pt["name"] else "veil", inner)
        if "outline" in pt:
            paint = masked(paint, pt["outline"], h)
        spec = [(pt["name"] + suffix, [c[0] - h[0], c[1] - h[1], c[2] - h[2]], [c[0] + h[0], c[1] + h[1], c[2] + h[2]], paint)]
        tipped = any(k in pt["name"] for k in ("toe", "hook", "crown", "shear"))
        if tipped:   # glowing point at the end of the long axis away from the bone's joint
            piv = pivots[bone]
            axis = [R[0][2], R[1][2], R[2][2]]
            out = sum((c[i] - piv[i]) * axis[i] for i in range(3))
            zs = 1 if out >= 0 else -1
            g = max(.35, min(h[0], h[1]) * .7)
            z0, z1 = (c[2] + h[2], c[2] + h[2] + max(1.5, h[2] * .45)) if zs > 0 else (c[2] - h[2] - max(1.5, h[2] * .45), c[2] - h[2])
            spec.append((pt["name"] + "_glow" + suffix, [c[0] - g, c[1] - g, z0], [c[0] + g, c[1] + g, z1], p("glow", "glow")))
        bname = f"p_{bone}_{pt['name']}{suffix if sign < 0 and not sided(bone) else ''}"
        return rig.bone(bname, c, matrix_euler(R), cubes(spec), bone)

    for pt in anatomy["parts"]:
        if pt["bone"] in ("skull", "jaw", "crown_l"):
            continue   # the head is rebuilt by hand below, on the same skull pivot
        if any(k in pt["name"] for k in ("throat", "mantle_chest")):
            pt = dict(pt, half=[pt["half"][0] * .85, pt["half"][1] * .88, pt["half"][2]])
        kids[pt["bone"]].append(oriented(pt, 1, pt["bone"]))
        if pt["sided"]:
            rb = right(pt["bone"])
            if rb != pt["bone"]:
                kids[rb].append(oriented(pt, -1, rb, "_r"))
            else:   # a sided part on a centre bone (brow, cheek, pit, fang, collar): mirror in place
                kids[pt["bone"]].append(oriented(pt, -1, pt["bone"], "_r"))

    # ---------- accents: B1-style glowing spikes along the spine and tail, horns for the rage ----------
    for k, (z, y, ln, w) in enumerate(((10, 92, 16, 5.5), (-6, 88, 16, 5.5), (-22, 80, 14, 5), (-36, 70, 12, 4.5))):
        kids["body"].append(spike(f"back_spike{k}", "body", [0, y - 3, z], [38, 0, 0], ln, w))
    for k, (seg, z, y, ln, w) in enumerate((("tail", -56, 44, 11, 4), ("tail_2", -74, 37, 10, 3.5), ("tail_mid", -94, 29, 8, 3))):
        x = pivots[seg][0]
        kids[seg].append(spike(f"{seg}_spike", seg, [x, y, z], [34, 0, 0], ln, w))
    # ---------- head (noctveil's shape in boxes): compact blind cranium, a beak tilted down, a keel,
    # brow ridges, deep cheeks with glowing pits, a short recessed jaw, a crown sweeping back over the neck ----------
    face = [
        ("cranium", [-11, 64, 34], [11, 84, 50], p("scale", "scales")),
        ("face1", [-9.5, 64, 50], [9.5, 82, 58], p("hide")),
        ("face2", [-7.5, 64.5, 58], [7.5, 80, 65], p("hide")),
        ("face3", [-5.5, 65, 65], [5.5, 78, 71], p("hide")),
        ("beak", [-3.5, 66, 71], [3.5, 75, 77], p("scale", "scales")),
        ("beak_hook", [-2, 62.5, 74], [2, 67, 78.5], p("talon")),
        ("keel", [-1.5, 84, 36], [1.5, 86.5, 66], p("scale", "scales")),
        ("keel_glow", [-.5, 86.5, 40], [.5, 87, 62], p("glow", "glow")),
        ("gape", [-6, 63.5, 52], [6, 64.5, 72], p("mouth")),
    ]
    sided_face = [
        ("brow_l", [8, 78, 40], [12, 83, 62], p("scale", "scales")),
        ("cheek_l", [9.5, 64, 42], [12.5, 74, 58], p("scale", "scales")),
        ("pit_glow_l", [12.5, 69, 47], [13.1, 73, 56], p("glow", "glow")),
        ("fang_l", [4.5, 60, 64], [6, 64, 66], p("fang", "bone")),
    ]
    face += sided_face + [(n[:-2] + "_r", [-b[0], a[1], a[2]], [-a[0], b[1], b[2]], q) for n, a, b, q in sided_face]
    kids["skull"].append(rig.bone("face", [0, 78, 44], [30, 0, 0], cubes(face), "skull"))
    kids["jaw"].append(part("jaw_low", "jaw", [0, 64, 46], [36, 0, 0], [
        ("jaw_bone", [-6, 57, 46], [6, 63, 66], p("hide")),
        ("jaw_plate", [-6.5, 56, 48], [6.5, 57, 64], p("belly", "belly")),
        ("lower_fang_l", [3.5, 63, 60], [5, 66, 62], p("fang", "bone")),
        ("lower_fang_r", [-5, 63, 60], [-3.5, 66, 62], p("fang", "bone")),
    ]))
    for sign, s in ((1, "l"), (-1, "r")):
        crown = "crown_" + s
        for k, (base, r, ln, w) in enumerate((([6, 82, 46], [24, -10, 0], 46, 7), ([10, 77, 44], [12, -24, 0], 30, 5),
                                              ([3.5, 86, 40], [34, -6, 0], 26, 4))):
            kids[crown].append(spike(f"crown{k}_{s}", crown, base, r, ln, w, sign))
    for sign, s in ((1, "l"), (-1, "r")):
        kids["skull"].append(part(f"horn_{s}", "skull", [10, 76, 56], [-28, -12, 0], [
            ("horn_base", [8, 73, 52], [13, 79, 62], p("bone", "bone")),
            ("horn_mid", [8.8, 74, 61], [12.2, 78, 72], p("bone", "bone")),
            ("horn_glow", [9.5, 74.8, 71], [11.5, 77.2, 80], p("glow", "glow")),
        ], sign, "_" + s))
        kids["skull"].append(part(f"antenna_{s}", "skull", [6, 86, 44], [18, -4, 0], [
            ("feeler", [5, 86, 14], [7.5, 88, 46], p("feeler")),
            ("feeler_glow", [5.25, 86.25, 8], [7.25, 87.75, 14], p("glow", "glow")),
        ], sign, "_" + s))

    built = {}
    for name, parent in reversed(order):
        built[name] = rig.bone(name, pivots[name], [0, 0, 0], [], parent, kids[name] + [built[c] for c, pp in order if pp == name])
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
