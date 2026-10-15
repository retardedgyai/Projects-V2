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
    "glow": ((188, 156, 240), (222, 200, 255), (246, 238, 255)),
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
                row = y // 4
                cell = (x + (row % 2) * 3) // 6
                lx = (x + (row % 2) * 3) % 6
                lit = noise(cell, row, seed + 2) % 3 == 0
                if lit and y % 4 == 0 and 0 < lx < 5:
                    c = pal[2]
                elif lit and y % 4 == 1 and 0 < lx < 5:
                    c = pal[1]
                elif y % 4 == 3 and noise(cell, row, seed + 4) % 2 == 0:
                    c = pal[0]
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
                c = pal[2 if (x + y) % 3 == 0 else 1]
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
    m = Model("shade_wyrm", size=512)
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
    def head():
        jaw = cubes([
            ("jaw", [-7, 25, 46], [7, 31, 78], p("hide")),
            ("jaw_plate", [-7.5, 24, 50], [7.5, 25, 76], p("belly", "belly")),
            ("lower_fang_l", [4, 31, 72], [6, 34, 75], p("bone", "bone")),
            ("lower_fang_r", [-6, 31, 72], [-4, 34, 75], p("bone", "bone")),
            ("tongue", [-3, 30.5, 48], [3, 31, 60], p("mouth")),
        ])
        skull_items = cubes([
            ("cranium", [-11, 30, 40], [11, 48, 58], p("hide")),
            ("snout", [-7.5, 31, 58], [7.5, 42, 74], p("hide")),
            ("snout_tip", [-6, 32, 74], [6, 41, 80], p("hide")),
            # The eyeless face: one pale bone mask over the brow and the top of the snout.
            ("mask_brow", [-10, 46, 50], [10, 51, 62], p("bone", "bone")),
            ("mask_snout", [-7, 42, 62], [7, 46, 78], p("bone", "bone")),
            ("nose", [-5, 38, 78], [5, 46, 81], p("bone", "bone")),
            ("fang_l", [4.5, 27, 74], [6.5, 31, 77], p("bone", "bone")),
            ("fang_r", [-6.5, 27, 74], [-4.5, 31, 77], p("bone", "bone")),
            # Shaggy hood behind the mask.
            ("hood_top", [-12, 48, 30], [12, 54, 50], p("fringe", "fringe")),
            ("hood_back", [-12, 32, 22], [12, 52, 32], p("fringe", "fringe")),
        ])
        for sign, s in ((1, "_l"), (-1, "_r")):
            skull_items += cubes([
                # Swept-back crown of hair, stepping up and back: the head's silhouette.
                ("crown_a", [4, 52, 32], [12, 60, 46], p("fringe", "fringe")),
                ("crown_b", [5, 57, 20], [11, 65, 34], p("fringe", "fringe")),
                ("crown_c", [6, 62, 8], [10, 70, 22], p("fringe", "fringe")),
                ("crown_tip", [7, 67, -2], [9, 72, 10], p("bone", "bone")),
                ("hood_side", [11, 30, 30], [15, 50, 52], p("fringe", "fringe")),
                ("hood_flap", [15, 34, 34], [19, 48, 48], p("fringe", "fringe")),
            ], sign, s)
        jaw_b = rig.bone("jaw", [0, 32, 46], [0, 0, 0], jaw, "skull")
        feelers, horns = [], []
        for sign, s in ((1, "l"), (-1, "r")):
            items = cubes([
                ("feeler", [2, 54, 8], [4.5, 56, 46], p("feeler")),
                ("feeler_tip", [2.25, 54.5, 2], [4.25, 55.5, 8], p("glow", "glow")),
            ], sign, "_" + s)
            feelers.append(rig.bone(f"antenna_{s}", [3.25 * sign, 55, 44], [0, 0, 0], items, "skull"))
            items = cubes([
                ("horn_base", [9, 44, 50], [13, 50, 58], p("bone", "bone")),
                ("horn_mid", [10, 48, 56], [13, 53, 64], p("bone", "bone")),
                ("horn_tip", [10.5, 52, 62], [12.5, 55, 70], p("bone", "bone")),
            ], sign, "_" + s)
            horns.append(rig.bone(f"horn_{s}", [11 * sign, 47, 52], [0, 0, 0], items, "skull"))
        skull = rig.bone("skull", [0, 38, 44], SKULL, skull_items, "neck", [jaw_b] + feelers + horns)
        neck_items = cubes([
            ("neck", [-9, 30, 18], [9, 48, 46], p("scale", "scales")),
            ("throat", [-7, 28.5, 20], [7, 30, 44], p("belly", "belly")),
            ("neck_mane", [-10, 46, 16], [10, 52, 40], p("fringe", "fringe")),
        ])
        return rig.bone("neck", [0, 40, 22], NECK, neck_items, "body", [skull])

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
            ("humerus", [11, 46, 9], [21, ey, 19], p("scale", "scales")),
            ("shoulder_plate", [9, 42, 6], [23, 56, 22], p("scale", "scales")),
            # Elbow knuckle and spike: the tallest point of the silhouette.
            ("elbow", [10.5, ey - 6, 8], [21.5, ey + 5, 20], p("hide")),
            ("elbow_spike", [14, ey + 5, 11], [18, ey + 15, 15], p("bone", "bone")),
            ("elbow_hook", [14.5, ey - 1, 4], [17.5, ey + 3, 8], p("bone", "bone")),
        ], sign, "_" + s)
        # Forearm: elbow -> wrist planted forward on the ground.
        down = [w - e for w, e in zip(wrist_world, elbow_world)]
        fore_len = length(down)
        fore_rot = aim(down, parent=rig.world[name])
        fore_name = f"wing_{s}_fore"
        rig.world[fore_name] = _mul(rig.world[name], euler_matrix(*fore_rot))
        fore = cubes([
            ("forearm", [11.5, ey - fore_len, 9.5], [20.5, ey, 18.5], p("hide")),
            ("forearm_ridge", [13, ey - fore_len * .75, 18.5], [19, ey - 8, 21], p("scale", "scales")),
        ], sign, "_" + s)
        wy = ey - fore_len
        hand = cubes([
            ("hand", [10, wy - 5, 6], [22, wy + 4, 22], p("scale", "scales")),
            ("talon_a", [10.5, wy - 5, 22], [13, wy - 2, 28], p("bone", "bone")),
            ("talon_b", [14.5, wy - 5, 22], [17.5, wy - 1.5, 30], p("bone", "bone")),
            ("talon_c", [19, wy - 5, 21], [21.5, wy - 2, 26], p("bone", "bone")),
        ], sign, "_" + s)
        # The hand cancels the arm's rotation so it sits flat on the ground.
        hand_b = rig.bone(f"wing_{s}_hand", [16 * sign, wy, 14], matrix_euler(_t(rig.world[fore_name])), hand, fore_name)
        fore_b = rig.bone(fore_name, top, fore_rot, fore, name, [hand_b])

        # Cloak: three finger spars fan back from the elbow; each carries the membrane behind it.
        inner = "west" if sign > 0 else "east"
        mem_name = f"wing_{s}_membrane"
        rig.world[mem_name] = rig.world[name]
        fingers = []
        # (finger, world direction of the spar from the elbow, spar length, membrane width behind it)
        layout = ((1, [16 * sign, -64, 8], 70, 24), (2, [16 * sign, -60, -26], 70, 28), (3, [14 * sign, -44, -52], 66, 30))
        for idx, (i, direction, size, width) in enumerate(layout):
            x0 = top[0] + sign * (3 + idx * .4)
            items = cubes([
                ("membrane", [x0 - .6, ey - size + 2, 14 - width], [x0 + .6, ey - 2, 13], p("cloak", "membrane", inner)),
                ("spar", [x0 - 1.5, ey - size, 12.5], [x0 + 1.5, ey, 15.5], p("spar", "bone")),
            ], 1, f"_{s}{i}")
            fingers.append(rig.bone(f"wing_{s}_finger{i}", top, aim(direction, parent=rig.world[mem_name]), items, mem_name))
        # Mantle: the folded part of the membrane lying over the back between the elbows.
        # It cancels the arm's tilt so it lies level over the hips, and still opens with the wing.
        mantle = cubes([("mantle", [-4, ey - 14, -40], [16, ey - 12, 18], p("cloak", "plain", "down"))], sign, "_" + s)
        mantle_b = rig.bone(f"wing_{s}_mantle", top, matrix_euler(_t(rig.world[mem_name])), mantle, mem_name)
        mem_b = rig.bone(mem_name, top, [0, 0, 0], [], name, fingers + [mantle_b])
        return rig.bone(name, shoulder, upper_rot, upper, "body", [fore_b, mem_b])

    # ---------- hind legs ----------
    def leg(sign, s):
        name = f"leg_h{s}"
        foot = cubes([
            ("foot", [9, -1, -33], [20, 6, -18], p("scale", "scales")),
            ("toe_a", [9.5, -1, -18], [12, 2, -13], p("bone", "bone")),
            ("toe_b", [13.5, -1, -18], [16, 2, -12], p("bone", "bone")),
            ("toe_c", [17, -1, -18], [19.5, 2, -13], p("bone", "bone")),
            ("spur", [13.5, 2, -36], [15.5, 5, -32], p("bone", "bone")),
        ], sign, "_" + s)
        foot_b = rig.bone(f"{name}_foot", [14.5 * sign, 5, -26], [-26, 0, 0], foot, f"{name}_shin")
        shin = cubes([("shin", [10.5, 4, -30], [18.5, 28, -22], p("hide"))], sign, "_" + s)
        shin_b = rig.bone(f"{name}_shin", [14.5 * sign, 26, -26], [26, 0, 0], shin, name, [foot_b])
        thigh = cubes([
            ("thigh", [7, 22, -38], [22, 52, -12], p("scale", "scales")),
            ("thigh_plate", [21.5, 30, -32], [22.5, 46, -18], p("bone", "bone")),
        ], sign, "_" + s)
        return rig.bone(name, [14.5 * sign, 44, -24], [0, 0, 0], thigh, "body", [shin_b])

    # ---------- tail ----------
    def tail():
        tip = cubes([
            ("tail_tip", [-4, 38.5, -114], [4, 46.5, -89], p("scale", "scales")),
            ("tail_blade", [-1, 37, -126], [1, 48, -112], p("bone", "bone")),
            ("tail_fin_l", [3.5, 41, -112], [8, 44, -100], p("bone", "bone")),
            ("tail_fin_r", [-8, 41, -112], [-3.5, 44, -100], p("bone", "bone")),
        ])
        mid = cubes([
            ("tail_mid", [-6, 37, -90], [6, 49, -63], p("scale", "scales")),
            ("tail_mid_spike", [-1, 48, -82], [1, 53, -74], p("bone", "bone")),
        ])
        base = cubes([
            ("tail_base", [-9, 35, -64], [9, 53, -34], p("scale", "scales")),
            ("tail_base_spike", [-1.5, 52, -56], [1.5, 59, -48], p("bone", "bone")),
        ])
        for sign, sfx in ((1, "_l"), (-1, "_r")):
            mid += cubes([("tail_mid_barb", [5.5, 41, -84], [9, 44, -76], p("bone", "bone"))], sign, sfx)
            base += cubes([("tail_base_barb", [8, 42, -58], [12, 46, -50], p("bone", "bone"))], sign, sfx)
        end = rig.bone("tail_end", [0, 43, -89], [-14, 0, 0], tip, "tail_mid")
        midb = rig.bone("tail_mid", [0, 43, -63], [-12, 0, 0], mid, "tail", [end])
        return rig.bone("tail", [0, 44, -36], [12, 0, 0], base, "body", [midb])

    # ---------- body: hips high, chest low ----------
    body_items = cubes([
        ("chest", [-17, 20, -6], [17, 48, 24], p("scale", "scales")),
        ("breast", [-13, 18, 16], [13, 40, 26], p("belly", "belly")),
        ("hips", [-15, 28, -40], [15, 56, -6], p("scale", "scales")),
        ("belly_front", [-12, 16, -6], [12, 21, 18], p("belly", "belly")),
        ("belly_back", [-12, 24, -36], [12, 29, -6], p("belly", "belly")),
        ("collar", [-20, 40, 0], [20, 54, 28], p("fringe", "fringe")),
        ("collar_top", [-15, 54, 2], [15, 58, 20], p("fringe", "fringe")),
    ])
    for index, (z, y, h) in enumerate(((-10, 56, 9), (-20, 56, 8), (-30, 56, 7), (-38, 55, 5))):
        body_items += cubes([(f"spine_{index}", [-1.5, y, z - 3], [1.5, y + h, z + 2], p("bone", "bone"))])
    parts = [head(), tail(), wing(1, "l"), wing(-1, "r"), leg(1, "l"), leg(-1, "r")]
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
