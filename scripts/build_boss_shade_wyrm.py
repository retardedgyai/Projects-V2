"""First version of the 黒翳竜 (shade wyrm) Blockbench model.

    python scripts/build_boss_shade_wyrm.py   # writes assets/bosses/shade_wyrm.bbmodel

The written .bbmodel is the committed source. Once someone edits it in Blockbench, do not re-run
this script over it without merging by hand. Original design: a blind wyrm that walks on its
wing-arms, wraps itself in a black membrane cloak, senses with two long feelers and sheds a
shade scale-dust. Enraged it rears up, opens the cloak and grows two horns.

Front is +Z, +X is the wyrm's left, 16 px = 1 block. Animation names are the server contract:
idle / walk / idle_enraged / walk_enraged / enrage / death, plus one `once` animation per
ability id whose length is telegraph+recovery and whose impact pose sits at the telegraph time.
"""
from boss_models import Model, ROOT

# Semantic rotation helpers, checked against the WSEE runtime by ShadeWyrmBossTest:
# pitch>0 lowers the +Z end (raw Bedrock 4.x X rotation lifts it, hence -1); roll>0 raises the
# +X side, so spread(+) opens both membranes outward. WSEE mirrors Blockbench X in game, so the
# "_l" bones appear on the wyrm's right; the model is symmetric, only the sweep arm notices.
PITCH, YAW, ROLL = -1, 1, 1


def rot(pitch=0.0, yaw=0.0, roll=0.0):
    return (PITCH * pitch, YAW * yaw, ROLL * roll)


PALETTES = {
    # shadow, base, light. Black-violet hide; the dust glint is the only bright accent.
    "hide": ((16, 12, 21), (29, 22, 36), (46, 35, 57)),
    "scale": ((22, 16, 28), (38, 28, 48), (62, 47, 78)),
    "belly": ((40, 33, 47), (58, 48, 64), (78, 66, 84)),
    "membrane": ((17, 10, 23), (33, 19, 43), (55, 33, 70)),
    "bone": ((112, 104, 97), (170, 162, 150), (219, 211, 197)),
    "feeler": ((64, 48, 86), (108, 84, 138), (150, 120, 186)),
    "glow": ((168, 132, 220), (206, 176, 250), (240, 226, 255)),
    "mouth": ((60, 12, 28), (92, 22, 42), (128, 40, 62)),
}
DUST = (184, 156, 222)
VEIN = (98, 64, 128)


def noise(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n ^ (n >> 16)) & 0xffff


def painter(material, motif="plain"):
    def make(side, w, h):
        seed = sum(ord(c) * (i + 1) for i, c in enumerate(material + motif + side[:1]))
        palette = PALETTES[material]

        def paint(x, y, w, h):
            tone = 1
            cluster = noise(x // 2, y // 2, seed)
            if cluster % 11 == 0:
                tone = 0
            elif cluster % 17 == 0:
                tone = 2
            colour = palette[tone]
            if motif == "scales" and side != "down":
                # Offset rows of small overlapping scales: light top edge, dark lower edge.
                row, col = y // 3, (x + (y // 3) * 2) // 4
                if y % 3 == 0:
                    colour = palette[2]
                elif (x + (y // 3) * 2) % 4 == 0:
                    colour = palette[0]
                if noise(col, row, seed + 3) % 29 == 0:
                    colour = DUST
            elif motif == "membrane":
                # Sparse veins radiating from the leading (top) edge plus dust specks.
                if (x + y // 3) % 9 == 0 or (y == 0):
                    colour = VEIN
                elif noise(x, y, seed + 9) % 53 == 0:
                    colour = DUST
            elif motif == "bone":
                colour = palette[2 if y < h // 3 else 1 if y < 2 * h // 3 else 0]
            elif motif == "glow":
                colour = palette[2 if (x + y) % 3 == 0 else 1]
            if w >= 4 and h >= 4 and motif not in ("membrane", "glow"):
                if y == h - 1 or x == w - 1:
                    colour = tuple(max(0, c - 8) for c in palette[0])
            return (*colour, 255)
        return paint, (material, motif, side in ("up", "down"), w, h)
    return make


def build():
    m = Model("shade_wyrm", size=512)
    p = painter

    def mirror(start, end):
        return [-end[0], start[1], start[2]], [-start[0], end[1], end[2]]

    def side_pair(name_l, name_r, pivot, parts, rotation=(0, 0, 0), children=None):
        """Left (+X) and mirrored right bone with the same parts."""
        children = children or (lambda side: [])
        bones = []
        for side, sign in (("l", 1), ("r", -1)):
            items = []
            for name, start, end, paint in parts:
                if sign < 0:
                    start, end = mirror(start, end)
                m.cube(f"{name}_{side}", start, end, items, paint)
            items += children(side)
            bones.append(m.bone(name_l if sign > 0 else name_r, [pivot[0] * sign, pivot[1], pivot[2]], items, rotation))
        return bones

    # --- head ---------------------------------------------------------------------------------
    jaw = []
    m.cube("jaw", [-5, 36, 40], [5, 41, 60], jaw, p("hide"))
    m.cube("teeth", [-4, 41, 52], [4, 42, 60], jaw, p("bone", "bone"))
    m.cube("tongue_glow", [-2, 41, 44], [2, 41.5, 52], jaw, p("mouth"))

    antennae = side_pair("antenna_l", "antenna_r", [4, 54, 45], [
        ("feeler_stalk", [3, 54, 44], [5, 62, 46], p("feeler")),
        ("feeler", [3, 61, 46], [5, 63, 70], p("feeler")),
        ("feeler_tip", [2.5, 60.5, 70], [5.5, 63.5, 74], p("glow", "glow")),
    ])
    horns = side_pair("horn_l", "horn_r", [7, 53, 40], [
        ("horn_base", [5, 52, 36], [9, 58, 43], p("bone", "bone")),
        ("horn_mid", [6, 57, 29], [9, 61, 37], p("bone", "bone")),
        ("horn_tip", [6.5, 60, 24], [8.5, 62, 30], p("bone", "bone")),
    ])
    skull_items = []
    m.cube("cranium", [-8, 41, 34], [8, 54, 50], skull_items, p("scale", "scales"))
    m.cube("snout", [-5, 41, 50], [5, 48, 63], skull_items, p("hide"))
    m.cube("hood", [-9, 50, 38], [9, 55, 55], skull_items, p("scale", "scales"))
    m.cube("hood_glow", [-7, 49, 54.5], [7, 50, 55.5], skull_items, p("glow", "glow"))
    m.cube("nostril_ridge", [-3, 48, 54], [3, 49.5, 62], skull_items, p("scale", "scales"))
    skull = m.bone("skull", [0, 46, 36], skull_items + [m.bone("jaw", [0, 42, 40], jaw)] + antennae + horns)

    neck_items = []
    m.cube("neck", [-6, 38, 18], [6, 50, 37], neck_items, p("hide"))
    m.cube("mane", [-7, 48, 20], [7, 53, 35], neck_items, p("scale", "scales"))
    neck = m.bone("neck", [0, 44, 20], neck_items + [skull])

    # --- wing-arms (forelegs) and their membrane cloak ---------------------------------------
    def wing_children(side):
        sign = 1 if side == "l" else -1
        hand, membrane = [], []
        for name, start, end, paint in (
            ("forearm", [13, 6, 9], [18, 24, 15], p("hide")),
            ("claw", [11, 0, 8], [19, 6, 19], p("scale", "scales")),
            ("talon_a", [11.5, 0, 19], [13.5, 2, 24], p("bone", "bone")),
            ("talon_b", [16, 0, 19], [18, 2, 24], p("bone", "bone")),
        ):
            if sign < 0:
                start, end = mirror(start, end)
            m.cube(f"{name}_{side}", start, end, hand, paint)
        for name, start, end, paint in (
            ("membrane", [18, 20, -34], [19.5, 55, 13], p("membrane", "membrane")),
            ("spar_lead", [17.5, 54, -32], [20, 57, 14], p("hide")),
            ("spar_mid", [17.5, 30, -26], [20, 54, -23], p("hide")),
            ("spar_aft", [17.5, 24, -36], [20, 52, -33], p("hide")),
            ("membrane_dust", [19.5, 40, -20], [19.8, 41, 6], p("glow", "glow")),
        ):
            if sign < 0:
                start, end = mirror(start, end)
            m.cube(f"{name}_{side}", start, end, membrane, paint)
        return [m.bone(f"wing_{side}_hand", [15 * sign, 22, 12], hand),
                m.bone(f"wing_{side}_membrane", [17 * sign, 52, 12], membrane)]

    wings = side_pair("wing_l", "wing_r", [15, 48, 12], [
        ("humerus", [12, 22, 8], [18, 50, 16], p("scale", "scales")),
        ("shoulder", [11, 44, 6], [19, 52, 18], p("scale", "scales")),
    ], children=wing_children)

    # --- hind legs ----------------------------------------------------------------------------
    def leg_children(side):
        sign = 1 if side == "l" else -1
        foot = []
        for name, start, end, paint in (
            ("shin", [6, 0, -23], [11, 15, -15], p("hide")),
            ("toes", [5, 0, -15], [12, 3, -10], p("bone", "bone")),
        ):
            if sign < 0:
                start, end = mirror(start, end)
            m.cube(f"{name}_{side}", start, end, foot, paint)
        return [m.bone(f"leg_h{side}_foot", [8.5 * sign, 15, -19], foot)]

    legs = side_pair("leg_hl", "leg_hr", [8, 30, -18], [
        ("thigh", [5, 14, -25], [12, 33, -12], p("scale", "scales")),
    ], children=leg_children)

    # --- tail ---------------------------------------------------------------------------------
    tip = []
    m.cube("tail_tip", [-3.5, 32, -70], [3.5, 39, -47], tip, p("hide"))
    m.cube("tail_spade", [-7, 33, -79], [7, 37, -69], tip, p("membrane", "membrane"))
    m.cube("tail_spine", [-1, 39, -64], [1, 42, -52], tip, p("bone", "bone"))
    tail_items = []
    m.cube("tail", [-5, 31, -48], [5, 42, -25], tail_items, p("scale", "scales"))
    m.cube("tail_ridge", [-1.5, 42, -44], [1.5, 45, -30], tail_items, p("bone", "bone"))
    tail = m.bone("tail", [0, 37, -26], tail_items + [m.bone("tail_end", [0, 36, -48], tip)])

    # --- body ---------------------------------------------------------------------------------
    body_items = []
    m.cube("chest", [-12, 28, -2], [12, 50, 22], body_items, p("hide"))
    m.cube("hump", [-10, 50, 0], [10, 56, 18], body_items, p("scale", "scales"))
    m.cube("abdomen", [-10, 24, -26], [10, 44, -2], body_items, p("scale", "scales"))
    m.cube("belly", [-8, 22, -22], [8, 28, 18], body_items, p("belly"))
    for index, (z, y) in enumerate(((13, 56), (5, 56), (-6, 44), (-15, 44), (-23, 44))):
        m.cube(f"ridge_{index}", [-1.5, y, z], [1.5, y + 5, z + 4], body_items, p("bone", "bone"))
    body = m.bone("body", [0, 34, 0], body_items + [neck, tail] + wings + legs)

    animate(m)
    return m, [body]


def animate(m):
    def spread(degrees, fan=0.0):
        return {"wing_l_membrane": rot(roll=degrees, yaw=-fan), "wing_r_membrane": rot(roll=-degrees, yaw=fan)}

    def pose_track(times_poses):
        """[(t, {bone: rotation})] -> rotation tracks; bones absent at a time are left unkeyed."""
        tracks = {}
        for time, pose in times_poses:
            for bone, value in pose.items():
                tracks.setdefault(bone, []).append((time, value))
        return tracks

    def merge(*parts):
        merged = {}
        for part in parts:
            for bone, frames in part.items():
                merged.setdefault(bone, []).extend(frames)
        for frames in merged.values():
            frames.sort(key=lambda frame: (frame[2] if len(frame) > 2 else "rotation", frame[0]))
        return merged

    zero = (0, 0, 0)

    def idle(open_degrees):
        base = pose_track([(t, {
            "neck": rot(pitch=-8 if open_degrees else 0) if t != 1.0 else rot(pitch=(-8 if open_degrees else 0) + 3),
            "antenna_l": rot(yaw=a, pitch=(-20 if open_degrees else 0) + b),
            "antenna_r": rot(yaw=-a, pitch=(-20 if open_degrees else 0) + b),
            "tail": rot(yaw=c), "tail_end": rot(yaw=c * 1.5),
            **spread(open_degrees + d, 20 if open_degrees else 0),
        }) for t, a, b, c, d in ((0, 0, 0, 0, 0), (0.5, 8, -3, 6, 2), (1.0, 2, -6, 0, 4), (1.5, -4, -3, -6, 2), (2.0, 0, 0, 0, 0))])
        breathing = {"body": [(0, zero, "position"), (1.0, (0, -0.6, 0), "position"), (2.0, zero, "position")]}
        return merge(base, breathing)

    def walk(open_degrees):
        frames = []
        for t, s in ((0, 1), (0.3, 0), (0.6, -1), (0.9, 0), (1.2, 1)):
            frames.append((t, {
                "wing_l": rot(pitch=-18 * s), "wing_r": rot(pitch=18 * s),
                "leg_hl": rot(pitch=18 * s), "leg_hr": rot(pitch=-18 * s),
                "leg_hl_foot": rot(pitch=-10 * max(s, 0)), "leg_hr_foot": rot(pitch=-10 * max(-s, 0)),
                "tail": rot(yaw=10 * s), "tail_end": rot(yaw=-8 * s),
                "neck": rot(yaw=3 * s, pitch=-8 if open_degrees else 0),
                "antenna_l": rot(pitch=(-20 if open_degrees else 4)), "antenna_r": rot(pitch=(-20 if open_degrees else 4)),
                **spread(open_degrees, 15 if open_degrees else 0),
            }))
        bob = {"body": [(t, (0, -1 if t in (0.3, 0.9) else 0, 0), "position") for t in (0, 0.3, 0.6, 0.9, 1.2)]}
        return merge(pose_track(frames), bob)

    m.anim("idle", 2.0, idle(0), "loop")
    m.anim("walk", 1.2, walk(0), "loop")
    m.anim("idle_enraged", 2.0, idle(75), "loop")
    m.anim("walk_enraged", 1.2, walk(60), "loop")

    # Ability animations: length = (telegraph + recovery) / 1000, impact at telegraph / 1000.
    rest = {"body": zero, "neck": zero, "skull": zero, "jaw": zero, "wing_l": zero, "wing_r": zero,
            "wing_l_membrane": zero, "wing_r_membrane": zero, "leg_hl": zero, "leg_hr": zero,
            "antenna_l": zero, "antenna_r": zero, "tail": zero}

    def ability(name, length, keys, position=None):
        poses = [(0, dict(rest))] + keys + [(length, dict(rest))]
        tracks = pose_track(poses)
        if position:
            tracks = merge(tracks, {"body": [(t, v, "position") for t, v in [(0, zero)] + position + [(length, zero)]]})
        m.anim(name, length, tracks)

    ability("shade-sweep", 2.0, [
        (0.9, {"body": rot(yaw=-25), "wing_l": rot(pitch=-50, roll=40), "neck": rot(yaw=-15), "tail": rot(yaw=20)}),
        (1.1, {"body": rot(yaw=30), "wing_l": rot(pitch=30, roll=10), "neck": rot(yaw=20), "tail": rot(yaw=-25)}),
        (1.4, {"body": rot(yaw=20), "wing_l": rot(pitch=15), "neck": rot(yaw=10), "tail": rot(yaw=-15)}),
    ])
    ability("shade-pounce", 2.6, [
        (1.2, {"body": rot(pitch=-8), "leg_hl": rot(pitch=25), "leg_hr": rot(pitch=25),
               "wing_l": rot(pitch=20), "wing_r": rot(pitch=20), "neck": rot(pitch=-12)}),
        (1.4, {"body": rot(pitch=12), "wing_l": rot(pitch=-50), "wing_r": rot(pitch=-50),
               "leg_hl": rot(pitch=-20), "leg_hr": rot(pitch=-20), "neck": rot(pitch=8), "jaw": rot(pitch=25)}),
        (1.6, {"body": rot(pitch=8), "wing_l": rot(pitch=-10), "wing_r": rot(pitch=-10), "jaw": rot(pitch=10)}),
    ], position=[(1.2, (0, -6, -2)), (1.4, (0, 4, 18)), (1.6, (0, 0, 18)), (2.2, (0, 0, 6))])
    ability("shade-dust", 2.6, [
        (1.0, spread(50)), (1.2, spread(58)), (1.4, spread(46)), (1.6, spread(56)),
        (1.7, {**spread(85), "neck": rot(pitch=-10)}), (2.0, spread(20)),
    ], position=[(1.7, (0, 1.5, 0)), (2.0, zero)])
    ability("shade-pulse", 2.3, [
        (0.9, {"antenna_l": rot(yaw=35, pitch=-25), "antenna_r": rot(yaw=-35, pitch=-25), "neck": rot(pitch=-20)}),
        (1.2, {"antenna_l": rot(yaw=40, pitch=-30), "antenna_r": rot(yaw=-40, pitch=-30), "neck": rot(pitch=10), **spread(25)}),
        (1.6, {"antenna_l": rot(yaw=20), "antenna_r": rot(yaw=-20), "neck": rot(pitch=5)}),
    ], position=[(1.2, (0, -2, 0)), (1.6, zero)])
    ability("shade-breath", 2.6, [
        (1.3, {"neck": rot(pitch=-30), "skull": rot(pitch=-15), "jaw": rot(pitch=15)}),
        (1.6, {"neck": rot(pitch=10), "skull": rot(pitch=5), "jaw": rot(pitch=32)}),
        (2.2, {"neck": rot(pitch=8), "skull": rot(pitch=4), "jaw": rot(pitch=28)}),
    ], position=[(1.6, (0, 0, 4)), (2.2, (0, 0, 4))])
    ability("shade-storm", 3.5, [
        (1.4, {"body": rot(pitch=-25), "wing_l": rot(pitch=-40, roll=30), "wing_r": rot(pitch=-40, roll=-30),
               **spread(100, 10), "neck": rot(pitch=-15)}),
        (2.2, {"body": rot(pitch=0), "wing_l": rot(pitch=-10), "wing_r": rot(pitch=-10), **spread(40, -10),
               "neck": rot(pitch=10), "jaw": rot(pitch=25)}),
        (2.6, {**spread(60), "jaw": rot(pitch=10)}),
    ], position=[(1.4, (0, 6, -2)), (2.2, zero)])

    # Enrage ends in the idle_enraged pose; the horns grow while the cloak opens.
    enraged = {"neck": rot(pitch=-8), "antenna_l": rot(pitch=-20), "antenna_r": rot(pitch=-20), **spread(75, 20)}
    enrage = pose_track([
        (0, dict(rest)),
        (0.9, {"body": rot(pitch=-30), "wing_l": rot(pitch=-50, roll=35), "wing_r": rot(pitch=-50, roll=-35),
               **spread(110, 25), "neck": rot(pitch=-35), "jaw": rot(pitch=40), "antenna_l": rot(pitch=-40), "antenna_r": rot(pitch=-40)}),
        (2.0, {"body": rot(pitch=-25), "wing_l": rot(pitch=-45, roll=30), "wing_r": rot(pitch=-45, roll=-30),
               **spread(105, 25), "neck": rot(pitch=-30), "jaw": rot(pitch=35), "antenna_l": rot(pitch=-35), "antenna_r": rot(pitch=-35)}),
        (2.6, {**{bone: zero for bone in ("body", "wing_l", "wing_r", "jaw")}, **enraged}),
    ])
    enrage = merge(enrage, {
        "body": [(0, zero, "position"), (0.9, (0, 8, -2), "position"), (2.0, (0, 7, -2), "position"), (2.6, zero, "position")],
        "horn_l": [(0, (0.1, 0.1, 0.1), "scale"), (0.3, (0.1, 0.1, 0.1), "scale"), (1.2, (1, 1, 1), "scale")],
        "horn_r": [(0, (0.1, 0.1, 0.1), "scale"), (0.3, (0.1, 0.1, 0.1), "scale"), (1.2, (1, 1, 1), "scale")],
    })
    m.anim("enrage", 2.6, enrage)
    m.anim("death", 2.4, merge(pose_track([
        (0, dict(rest)),
        (0.8, {"body": rot(roll=20, pitch=-10), "neck": rot(pitch=-25), "jaw": rot(pitch=30), **spread(60)}),
        (2.0, {"body": rot(roll=70), "neck": rot(pitch=30, yaw=20), "jaw": rot(pitch=10), **spread(15),
               "leg_hl": rot(pitch=-30), "leg_hr": rot(pitch=20), "tail": rot(yaw=25)}),
        (2.4, {"body": rot(roll=72), "neck": rot(pitch=32, yaw=20), **spread(12)}),
    ]), {"body": [(0, zero, "position"), (0.8, (0, 3, 0), "position"), (2.0, (0, -10, 0), "position"), (2.4, (0, -10, 0), "position")]}))


if __name__ == "__main__":
    model, outliner = build()
    out = ROOT / "assets/bosses/shade_wyrm.bbmodel"
    data = model.write(out, outliner)
    print(f"{out.relative_to(ROOT)}: {len(data['elements'])} cubes, {len(model.bones)} bones, {len(data['animations'])} animations")
