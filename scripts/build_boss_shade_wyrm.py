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

        def paint(x, y, w, h):
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
            if motif in ("membrane", "fringe") and big and h >= 6:
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


def build():
    m = Model("shade_wyrm", size=1024)
    rig = Rig(m)
    p = painter

    def cubes(spec, sign=1, suffix=""):
        """spec: [(name, from, to, paint)]; sign -1 mirrors across X for the right side."""
        items = []
        for name, a, b, paint in spec:
            if sign < 0:
                a, b = [-b[0], a[1], a[2]], [-a[0], b[1], b[2]]
            m.cube(f"{name}{suffix}", a, b, items, paint)
        return items

    rig.world["body"] = IDENTITY
    NECK, SKULL = (12, 0, 0), (-10, 0, 0)
    rig.world["neck"] = euler_matrix(*NECK)
    rig.world["skull"] = _mul(rig.world["neck"], euler_matrix(*SKULL))

    # ---------- head ----------
    def part(name, parent, pivot, rotation, spec, sign=1, suffix="", children=()):
        """A bone holding cubes: angles live on bones so the server can verify them (see the rest-pose test)."""
        piv = [pivot[0] * sign, pivot[1], pivot[2]]
        rotation = [rotation[0], rotation[1] * sign, rotation[2] * sign]
        return rig.bone(name, piv, rotation, cubes(spec, sign, suffix), parent, list(children))

    def spike(name, parent, base, rotation, length, width, sign=1, glow=True):
        """A tapered spike pointing along -Z from `base` before rotation: thick root, thin glowing tip."""
        x, y, z = base
        w2, w3 = width * .65, width * .35
        spec = [(f"{name}_root", [x - width / 2, y, z - length * .5], [x + width / 2, y + width, z], p("scale", "scales")),
                (f"{name}_mid", [x - w2 / 2, y + (width - w2) / 2, z - length * .8], [x + w2 / 2, y + (width + w2) / 2, z - length * .5], p("hide")),
                (f"{name}_glow", [x - w3 / 2, y + (width - w3) / 2, z - length], [x + w3 / 2, y + (width + w3) / 2, z - length * .8],
                 p("glow", "glow") if glow else p("bone", "bone"))]
        return part(name, parent, base, rotation, spec, sign)

    # ---------- head: a ridged helmet with a glowing mouth and a swept-back crown ----------
    def head():
        jaw_items = [
            ("jaw", [-7, 27, 48], [7, 33, 72], p("hide")),
            ("jaw_tip", [-5, 27.5, 72], [5, 32.5, 82], p("hide")),
            ("jaw_plate", [-7.5, 26, 50], [7.5, 27, 74], p("belly", "belly")),
            ("chin_spike", [-1, 24, 58], [1, 26, 70], p("bone", "bone")),
            ("tongue", [-4, 32.5, 50], [4, 33.5, 74], p("mouth")),
            ("throat_glow", [-3, 31.5, 48], [3, 33, 56], p("glow", "glow")),
        ]
        for k, z in enumerate((60, 65, 70, 75)):
            for sx in (1, -1):
                x = (5.5 - k * .4) * sx
                jaw_items.append((f"lower_tooth_glow_{k}_{'l' if sx > 0 else 'r'}", [x - .75, 33, z], [x + .75, 35.5, z + 1.5], p("fang", "bone")))
        jaw_b = part("jaw", "skull", [0, 33, 48], [9, 0, 0], jaw_items)

        skull_items = [
            ("cranium", [-10, 32, 36], [10, 48, 56], p("hide")),
            ("skull_back", [-11, 30, 26], [11, 50, 37], p("fringe", "fringe")),
            ("snout_base", [-8, 33, 56], [8, 45, 70], p("hide")),
            ("snout_mid", [-6, 34, 70], [6, 43, 79], p("hide")),
            ("snout_tip", [-4, 35, 79], [4, 41, 86], p("hide")),
            ("beak", [-2.5, 32, 84], [2.5, 37, 88], p("scale", "scales")),
            ("palate", [-5.5, 32.5, 52], [5.5, 33.5, 84], p("mouth")),
        ]
        for k, z in enumerate((58, 63, 68, 73, 78)):
            for sx in (1, -1):
                x = (6 - k * .45) * sx
                skull_items.append((f"upper_tooth_{k}_{'l' if sx > 0 else 'r'}", [x - .75, 30.5, z], [x + .75, 33.5, z + 1.5], p("fang", "bone")))
        skull_parts = [jaw_b]
        # Helmet: two stepped dome plates leaning down to the nose, and a ridged nasal plate.
        skull_parts.append(part("helm", "skull", [0, 48, 58], [12, 0, 0], [
            ("helm_dome", [-11, 47, 34], [11, 52, 60], p("scale", "scales")),
            ("helm_crest", [-7, 51, 36], [7, 54.5, 56], p("scale", "scales")),
            ("helm_seam_glow", [-1, 54.5, 38], [1, 55, 54], p("glow", "glow")),
        ]))
        skull_parts.append(part("nasal", "skull", [0, 45, 70], [8, 0, 0], [
            ("nasal_plate", [-6, 44, 58], [6, 47, 84], p("scale", "scales")),
            ("nasal_ridge", [-2, 46.5, 60], [2, 48.5, 82], p("scale", "scales")),
            ("nasal_seam_glow", [-.5, 48.5, 62], [.5, 49, 80], p("glow", "glow")),
        ]))
        for sign, s in ((1, "l"), (-1, "r")):
            # Cheek guard flaring out toward the back, with the glowing sense slit where an eye would be.
            skull_parts.append(part(f"cheek_{s}", "skull", [10, 40, 60], [0, -16, 0], [
                ("cheek", [9, 33, 40], [12, 45, 60], p("scale", "scales")),
                ("cheek_rim", [11.5, 43, 38], [13, 46.5, 58], p("hide")),
                ("sense_glow", [12, 39, 48], [12.6, 41, 57], p("glow", "glow")),
            ], sign, "_" + s))
            # Ragged hood of hair flaring out behind the cheeks.
            skull_parts.append(part(f"hood_{s}", "skull", [10, 40, 38], [0, -38, 8], [
                ("hood_flap", [9.5, 28, 18], [11, 52, 38], p("fringe", "fringe")),
            ], sign, "_" + s))
            # Helmet rim spikes and the long crown, all swept back and up.
            for k, (base, rot, length, width) in enumerate((
                    ([11, 46, 44], [10, -24, 0], 9, 3), ([10.5, 42, 34], [4, -30, 0], 8, 2.5))):
                skull_parts.append(spike(f"rim_spike{k}_{s}", "skull", base, rot, length, width, sign))
            for k, (base, rot, length, width) in enumerate((
                    ([4, 52, 42], [16, -8, 0], 30, 5), ([7, 49, 32], [10, -18, 0], 26, 4))):
                skull_parts.append(spike(f"crown{k}_{s}", "skull", base, rot, length, width, sign))
        feelers, horns = [], []
        for sign, s in ((1, "l"), (-1, "r")):
            feelers.append(part(f"antenna_{s}", "skull", [1.5, 55, 52], [0, 0, 0], [
                ("feeler", [0.5, 55, 26], [2.5, 57, 52], p("feeler")),
                ("feeler_glow", [0.75, 55.25, 20], [2.25, 56.75, 26], p("glow", "glow")),
            ], sign, "_" + s))
            horns.append(part(f"horn_{s}", "skull", [10, 47, 58], [-24, 0, 0], [
                ("horn_base", [8.5, 45, 56], [12, 49, 66], p("bone", "bone")),
                ("horn_mid", [9, 45.5, 64], [11.5, 48.5, 74], p("bone", "bone")),
                ("horn_glow", [9.5, 46, 72], [11, 48, 80], p("glow", "glow")),
            ], sign, "_" + s))
        skull = rig.bone("skull", [0, 38, 44], SKULL, cubes(skull_items), "neck", skull_parts + feelers + horns)
        neck_parts = [spike(f"neck_spike{k}", "neck", [0, 48, z], [20, 0, 0], 12, 4) for k, z in enumerate((42, 32, 22))]
        neck_items = cubes([
            ("neck", [-9, 30, 18], [9, 48, 46], p("scale", "scales")),
            ("throat", [-7, 28.5, 20], [7, 30, 44], p("belly", "belly")),
            ("neck_mane", [-10, 44, 16], [10, 50, 40], p("fringe", "fringe")),
        ])
        return rig.bone("neck", [0, 40, 22], NECK, neck_items, "body", [skull] + neck_parts)

    def claw(name, parent, base, sign, size=1.0, pitch=24):
        """A hooked claw pointing +Z from `base`: dark root, then a tip bone bent down, glowing at the point."""
        x, y, z = base
        w = 3 * size
        tip = part(f"{name}_tip", name, [x, y + w / 2, z + 7 * size], [pitch, 0, 0], [
            (f"{name}_hook", [x - w * .35, y + w * .15, z + 7 * size], [x + w * .35, y + w * .85, z + 12 * size], p("talon")),
            (f"{name}_glow", [x - w * .2, y + w * .3, z + 12 * size], [x + w * .2, y + w * .7, z + 15 * size], p("glow", "glow")),
        ], sign)
        return part(name, parent, [x, y + w / 2, z], [8, 0, 0], [
            (f"{name}_root", [x - w / 2, y, z - 2], [x + w / 2, y + w, z + 7 * size], p("talon")),
        ], sign, children=[tip])

    # ---------- wing-arms (front legs) and the cloak ----------
    def wing(sign, s):
        shoulder = [16 * sign, 48, 14]
        elbow_world = [21 * sign, 74, 4]
        wrist_world = [40 * sign, 4, 38]
        up = [e - b for e, b in zip(elbow_world, shoulder)]
        upper_rot = aim(up, along=(0, 1, 0))
        upper_len = length(up)
        name = f"wing_{s}"
        rig.world[name] = euler_matrix(*upper_rot)
        ey = shoulder[1] + upper_len   # the elbow, in the upper arm's local frame
        top = [16 * sign, ey, 14]
        upper = cubes([
            ("humerus", [10.5, 44, 8], [21.5, 44 + upper_len * .6, 20], p("scale", "scales")),
            ("humerus_upper", [11.5, 44 + upper_len * .6, 9], [20.5, ey, 19], p("scale", "scales")),
            ("shoulder_plate", [9, 42, 5], [23, 55, 22], p("scale", "scales")),
            ("elbow", [10.5, ey - 6, 8], [21.5, ey + 5, 20], p("hide")),
        ], sign, "_" + s)
        elbow_spikes = [spike(f"elbow_spike_{s}", name, [16, ey + 3, 12], [-50, 0, 0], 16, 5, sign),
                        spike(f"elbow_barb_{s}", name, [16, ey - 2, 8], [10, 0, 0], 10, 3.5, sign)]
        # Forearm: elbow -> wrist planted forward on the ground, thick at the elbow, thin at the wrist.
        down = [w - e for w, e in zip(wrist_world, elbow_world)]
        fore_len = length(down)
        fore_rot = aim(down, parent=rig.world[name])
        fore_name = f"wing_{s}_fore"
        rig.world[fore_name] = _mul(rig.world[name], euler_matrix(*fore_rot))
        fore = cubes([
            ("forearm", [11, ey - fore_len * .5, 9], [21, ey, 19], p("hide")),
            ("forearm_low", [12.5, ey - fore_len, 10.5], [19.5, ey - fore_len * .5, 17.5], p("hide")),
            ("forearm_ridge", [13, ey - fore_len * .8, 17.5], [19, ey - 6, 20.5], p("scale", "scales")),
            ("forearm_seam_glow", [15.5, ey - fore_len * .55, 20.5], [16.5, ey - fore_len * .3, 21], p("glow", "glow")),
        ], sign, "_" + s)
        wy = ey - fore_len
        hand = cubes([
            ("wrist", [11, wy - 4, 6], [21, wy + 5, 20], p("scale", "scales")),
        ], sign, "_" + s)
        hand_name = f"wing_{s}_hand"
        # Big hooked wing-claws: the brightest accent of the silhouette, planted in front.
        claws = [claw(f"wclaw{k}_{s}", hand_name, [cx, wy - 4, 18], sign, size, 30)
                 for k, (cx, size) in enumerate(((11.5, 1.1), (16, 1.35), (20.5, 1.0)))]
        # The hand cancels the arm's rotation so it sits flat on the ground.
        hand_b = rig.bone(hand_name, [16 * sign, wy, 14], matrix_euler(_t(rig.world[fore_name])), hand, fore_name, claws)
        fore_b = rig.bone(fore_name, top, fore_rot, fore, name, [hand_b])

        # Cloak: four finger spars fan back from the elbow; the membranes trail back past the hips.
        inner = "west" if sign > 0 else "east"
        mem_name = f"wing_{s}_membrane"
        rig.world[mem_name] = rig.world[name]
        fingers = []
        # (finger, world direction of the spar from the elbow, spar length, membrane width behind it)
        # Each finger carries the membrane on its trailing side; the rear ones run back toward the tail.
        layout = ((1, [16 * sign, -64, 4], 72, 24, -1), (2, [18 * sign, -52, -36], 80, 30, -1),
                  (3, [16 * sign, -40, -60], 88, 34, -1), (4, [12 * sign, -28, -76], 84, 30, -1))
        for idx, (i, direction, size, width, side) in enumerate(layout):
            x0 = top[0] + sign * (3 + idx * .4)
            z0, z1 = (14 - width, 13) if side < 0 else (15, 14 + width)
            items = cubes([
                ("membrane", [x0 - .6, ey - size + 2, z0], [x0 + .6, ey - 2, z1], p("cloak", "membrane", inner)),
                ("spar", [x0 - 1.5, ey - size * .55, 12.5], [x0 + 1.5, ey, 15.5], p("spar", "bone")),
                ("spar_low", [x0 - 1, ey - size, 13], [x0 + 1, ey - size * .55, 15], p("spar", "bone")),
                ("spar_glow", [x0 - .75, ey - size - 1.5, 13.25], [x0 + .75, ey - size, 14.75], p("glow", "glow")),
            ], 1, f"_{s}{i}")
            fingers.append(rig.bone(f"wing_{s}_finger{i}", top, aim(direction, parent=rig.world[mem_name]), items, mem_name))
        # Mantle: the folded part of the membrane lying over the back between the elbows.
        # It cancels the arm's tilt so it lies level over the hips, and still opens with the wing.
        mantle = cubes([("mantle", [-4, ey - 14, -40], [16, ey - 12, 18], p("cloak", "plain", "down"))], sign, "_" + s)
        mantle_b = rig.bone(f"wing_{s}_mantle", top, matrix_euler(_t(rig.world[mem_name])), mantle, mem_name)
        mem_b = rig.bone(mem_name, top, [0, 0, 0], [], name, fingers + [mantle_b])
        return rig.bone(name, shoulder, upper_rot, upper, "body", [fore_b, mem_b] + elbow_spikes)

    # ---------- hind legs: thick thigh, thin shin, clawed foot ----------
    def leg(sign, s):
        name = f"leg_h{s}"
        foot_name = f"{name}_foot"
        toes = [claw(f"toe{k}_{s}", foot_name, [tx, -1, -18], sign, .8, 26)
                for k, tx in enumerate((10.5, 14.5, 18.5))]
        foot = cubes([
            ("foot", [9, -1, -33], [20, 6, -18], p("scale", "scales")),
            ("spur", [13.5, 2, -37], [15.5, 5, -32], p("talon")),
        ], sign, "_" + s)
        foot_b = rig.bone(foot_name, [14.5 * sign, 5, -26], [-26, 0, 0], foot, f"{name}_shin", toes)
        shin = cubes([
            ("shin", [10.5, 14, -30], [18.5, 28, -21], p("hide")),
            ("shin_low", [11.5, 4, -29], [17.5, 14, -22], p("hide")),
        ], sign, "_" + s)
        shin_b = rig.bone(f"{name}_shin", [14.5 * sign, 26, -26], [26, 0, 0], shin, name, [foot_b])
        thigh = cubes([
            ("thigh", [7, 30, -38], [22, 54, -12], p("scale", "scales")),
            ("thigh_low", [9, 20, -34], [20, 30, -16], p("scale", "scales")),
            ("thigh_plate", [21.5, 34, -32], [22.5, 48, -18], p("scale", "scales")),
            ("thigh_seam_glow", [22.5, 38, -28], [23, 46, -22], p("glow", "glow")),
        ], sign, "_" + s)
        return rig.bone(name, [14.5 * sign, 44, -24], [0, 0, 0], thigh, "body", [shin_b])

    # ---------- tail: five tapering segments in an S-curve, spiked along the top ----------
    def tail():
        segs = (("tail", 22, 18, 17, 12), ("tail_2", 22, 14, 13, -8), ("tail_mid", 22, 11, 10, -10),
                ("tail_4", 20, 8, 8, -8), ("tail_end", 18, 6, 6, 8))
        z, built = -36, []
        for k, (seg, ln, w, h, pitch) in enumerate(segs):
            spec = [(f"{seg}_seg", [-w / 2, 44 - h / 2, z - ln - 1], [w / 2, 44 + h / 2, z], p("scale", "scales")),
                    (f"{seg}_belly", [-w / 2 + 1, 44 - h / 2 - 1, z - ln], [w / 2 - 1, 44 - h / 2, z - 1], p("belly", "belly"))]
            if k == len(segs) - 1:
                spec += [("tail_blade", [-1, 40, z - ln - 12], [1, 48, z - ln], p("talon")),
                         ("tail_blade_glow", [-.5, 41.5, z - ln - 16], [.5, 46.5, z - ln - 12], p("glow", "glow"))]
            kids = [spike(f"{seg}_spike", seg, [0, 44 + h / 2 - 1, z - ln * .45], [30, 0, 0], 6 + h * .6, max(2, h * .35))]
            if 1 <= k <= 3:
                for sg, sx in ((1, "l"), (-1, "r")):
                    kids.append(spike(f"{seg}_barb_{sx}", seg, [w / 2 - 1, 44, z - ln * .6], [6, -60, 0], 5 + w * .5, max(1.5, w * .25), sg))
            built.append((seg, [0, 44, z], [pitch, 0, 0], spec, kids))
            z -= ln
        names = [b[0] for b in built]
        child = None
        for i in range(len(built) - 1, -1, -1):
            seg, pivot, r, spec, kids = built[i]
            parent = "body" if i == 0 else names[i - 1]
            child = rig.bone(seg, pivot, r, cubes(spec), parent, kids + ([child] if child else []))
        return child

    # ---------- body: hips high, chest low, spiked spine ----------
    body_items = cubes([
        ("chest", [-15, 20, -4], [15, 46, 24], p("scale", "scales")),
        ("chest_hump", [-12, 46, -2], [12, 54, 20], p("scale", "scales")),
        ("breast", [-12, 18, 14], [12, 40, 26], p("belly", "belly")),
        ("belly_front", [-11, 16, -4], [11, 21, 16], p("belly", "belly")),
        ("mane_top", [-10, 52, 4], [10, 57, 24], p("fringe", "fringe")),
    ])
    body_parts = [part("waist", "body", [0, 40, -6], [-8, 0, 0], [
        ("waist", [-13, 26, -40], [13, 50, -4], p("scale", "scales")),
        ("waist_top", [-10, 50, -38], [10, 54, -6], p("scale", "scales")),
        ("belly_back", [-10, 24, -36], [10, 27, -6], p("belly", "belly")),
    ], children=[spike(f"spine_spike{k}", "waist", [0, 53, z], [36, 0, 0], ln, w)
                 for k, (z, ln, w) in enumerate(((-12, 14, 5), (-22, 12, 4.5), (-32, 10, 4)))])]
    body_parts += [spike(f"back_spike{k}", "body", [0, 56, z], [40, 0, 0], ln, w)
                   for k, (z, ln, w) in enumerate(((18, 14, 5), (8, 16, 5.5)))]
    for sign, s in ((1, "l"), (-1, "r")):
        body_parts.append(part(f"mane_{s}", "body", [12, 50, 20], [0, -30, 12], [
            ("mane_flap", [11.5, 32, -2], [13, 56, 24], p("fringe", "fringe")),
        ], sign, "_" + s))
    parts = [head(), tail(), wing(1, "l"), wing(-1, "r"), leg(1, "l"), leg(-1, "r")] + body_parts
    body = rig.bone("body", [0, 36, 0], [0, 0, 0], body_items, None, parts)
    outliner = [body()]
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
