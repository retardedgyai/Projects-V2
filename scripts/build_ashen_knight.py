"""Build the original Ashen Knight boss for the isolated model laboratory.

The Scorpius bbmodel authoring class supplies deterministic bones, UVs, texture
atlas packing, and animation serialization. Run from any directory:
    python scripts/build_ashen_knight.py
"""

from pathlib import Path
import math
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "vendor" / "scorpius" / "bbmodel"))
import gen_vesper as authoring  # noqa: E402


PALETTE = {
    "armor": ("303943", "566674", "8796a0"),
    "edge": ("667984", "a7b6be", "d4dedc"),
    "cloth": ("192a50", "315b86", "568db4"),
    "void": ("101923", "263540", "394e5a"),
    "ash": ("4c4550", "796c74", "ab9391"),
    "ember": ("9d342f", "e56948", "ffc383"),
    "eye": ("357b9d", "7bced6", "c5fff2"),
}
authoring.MATERIALS = {
    name: tuple(bytes.fromhex(color) for color in shades)
    for name, shades in PALETTE.items()
}


class KnightModel(authoring.Model):
    """Paint material cues on each face, keeping the texture pixel sized."""

    def surface(self, width, height, material, side, motif):
        width = max(1, round(width * self.texels))
        height = max(1, round(height * self.texels))
        seed = sum((i + 1) * ord(ch) for i, ch in enumerate(material + side + motif))
        shades = authoring.MATERIALS[material]

        def paint(x, y):
            coarse = authoring.noise(x // 3, y // 3, seed)
            fine = authoring.noise(x, y, seed + 73)
            tone = 0 if coarse % 17 < 3 else 2 if coarse % 23 == 0 else 1
            if material in ("armor", "edge", "ash"):
                # Broken light-catching rim, dents and sparse metal scratches.
                if y == 0 and x % 7 != 3:
                    tone = 2
                if y == height - 1 or x == width - 1:
                    tone = 0
                if width >= 8 and height >= 8 and y in (2, height - 3) and x in (2, width - 3):
                    tone = 2
                if fine % 79 == 0 or (x + y * 2 + seed) % 47 == 0:
                    tone = 0
                if fine % 89 == 0:
                    tone = 2
            elif material == "cloth":
                if (x + y * 2 + seed) % 13 == 0:
                    tone = 2
                if x % 11 == 0 and y % 3 != 0:
                    tone = 0
                if y == height - 1:
                    tone = 0
                if side in ("north", "south") and height >= 12:
                    tear = 1 + authoring.noise(x // 3, 0, seed + 41) % 5
                    if y >= height - tear and (x + seed) % 11 > 2:
                        return (0, 0, 0, 0)
            elif material == "void":
                if coarse % 9 < 6:
                    tone = 0
            elif material in ("ember", "eye"):
                tone = 2 if (x + y) % 5 < 3 else 1
            return (*shades[tone], 255)

        return self.patch(width, height, paint, (width, height, material, side, motif))


def add(model, bucket, name, lo, hi, material, motif="plain"):
    model.cube(name, lo, hi, material, bucket, motif)


def build(out=ROOT / "model-lab" / "models"):
    out.mkdir(parents=True, exist_ok=True)
    m = KnightModel("ashen_knight", size=256, texels=2)

    hips, chest, helm = [], [], []
    left_leg, right_leg, left_arm, right_arm, blade = [], [], [], [], []
    cape_left, cape_right, cape_left_edge, cape_right_edge = [], [], [], []

    # Narrow waist, uneven shoulders and a hunched profile keep the outline
    # readable at Minecraft viewing distances. The front faces negative Z.
    add(m, hips, "fauld_core", [-3.2, 10.5, -1.8], [3.2, 14, 2], "armor")
    add(m, hips, "fauld_left", [-3.8, 9.2, -2], [-.3, 12, 2.2], "edge")
    add(m, hips, "fauld_right", [.3, 9.6, -2], [3.5, 12, 2.2], "armor")
    add(m, hips, "belt", [-3.4, 12.3, -2.15], [3.4, 13.2, -1.7], "ash")
    add(m, chest, "cuirass", [-3.6, 13, -2.4], [3.6, 22, 2.1], "armor")
    add(m, chest, "left_breast_plate", [-4.1, 17, -2.8], [-.4, 22.5, -2.1], "edge")
    add(m, chest, "right_breast_plate", [.5, 17.2, -2.7], [3.8, 21.7, -2.1], "armor")
    add(m, chest, "heart_fissure", [-.5, 17, -2.84], [.5, 20.3, -2.75], "ember")
    add(m, chest, "heart_ember", [-1, 18.1, -2.92], [1, 19, -2.83], "ember")
    add(m, chest, "high_collar", [-3, 21.2, -1.7], [3, 23.2, 2.3], "void")
    add(m, chest, "left_pauldron", [-6.5, 19.8, -2.2], [-2.7, 23.8, 2.8], "edge")
    add(m, chest, "left_pauldron_ridge", [-6.9, 22.5, -2.3], [-3.2, 23.8, 2.9], "ash")
    add(m, chest, "right_pauldron", [2.8, 20.1, -1.9], [5.8, 23, 2.2], "armor")
    add(m, chest, "right_pauldron_lip", [3.7, 20.2, -2.5], [6.2, 21.3, 2.3], "edge")
    add(m, chest, "left_pauldron_front", [-6.7, 20.2, -2.8], [-3.6, 22.8, -2.15], "armor")
    add(m, chest, "left_spike_base", [-6.2, 22.9, -.3], [-4.8, 25.4, 1.8], "edge")
    add(m, chest, "left_spike_tip", [-5.9, 24.7, .1], [-5.1, 27.2, 1.4], "ash")
    add(m, chest, "right_spike", [4.2, 22.6, -.4], [5.3, 25, 1.1], "armor")
    add(m, chest, "cuirass_rib_left", [-3.5, 14.8, -2.9], [-2.8, 20.2, -2.4], "edge")
    add(m, chest, "cuirass_rib_right", [2.7, 15.5, -2.9], [3.45, 20.8, -2.4], "edge")
    add(m, chest, "cuirass_lower_rib", [-2.7, 14.1, -2.75], [2.8, 14.9, -2.35], "ash")

    add(m, helm, "hood", [-2.7, 21.9, -2.3], [2.7, 27.5, 2.8], "void")
    add(m, helm, "visor", [-2.4, 23.2, -3.1], [2.4, 26.4, -2.25], "armor")
    add(m, helm, "visor_brow", [-2.8, 25.6, -3.4], [2.8, 26.5, -2.5], "edge")
    add(m, helm, "visor_muzzle", [-1.5, 22.9, -3.35], [1.5, 24.4, -2.6], "armor")
    add(m, helm, "left_eye", [-1.8, 24.45, -3.47], [-.35, 24.85, -3.39], "eye")
    add(m, helm, "right_eye", [.35, 24.45, -3.47], [1.8, 24.85, -3.39], "eye")
    add(m, helm, "crest_base", [-.9, 27, -.7], [.9, 29.3, 1], "edge")
    add(m, helm, "crest_tip", [-.65, 28.5, -.4], [.65, 31, .7], "armor")
    add(m, helm, "left_cheek_plate", [-2.9, 22.8, -3.0], [-1.8, 25.3, -.9], "edge")
    add(m, helm, "right_cheek_plate", [1.8, 22.8, -3.0], [2.9, 25.3, -.9], "edge")
    add(m, helm, "helmet_left_horn", [-3.5, 25.1, -.2], [-2.4, 29.4, 1.3], "armor")
    add(m, helm, "helmet_left_horn_tip", [-3.3, 28.5, .1], [-2.7, 31.6, 1], "edge")
    add(m, helm, "helmet_right_horn_broken", [2.4, 25.3, -.2], [3.5, 27.6, 1.3], "armor")
    add(m, helm, "face_lower_mask", [-2.2, 21.8, -3.5], [2.2, 23.5, -2.8], "edge")

    for side, x in (("left", -2.1), ("right", 2.1)):
        leg = left_leg if side == "left" else right_leg
        add(m, leg, f"{side}_thigh", [x - 1.4, 6.8, -1.45], [x + 1.4, 11.3, 1.5], "armor")
        add(m, leg, f"{side}_greave", [x - 1.25, 2, -1.6], [x + 1.25, 7.5, 1.5], "edge")
        add(m, leg, f"{side}_boot", [x - 1.4, 0, -2.8], [x + 1.4, 2.8, 1.7], "void")
        add(m, leg, f"{side}_knee", [x - 1.45, 6.2, -2], [x + 1.45, 7.6, -.95], "armor")
        add(m, leg, f"{side}_knee_ridge", [x - 1.1, 6.8, -2.35], [x + 1.1, 7.2, -1.9], "edge")
        add(m, leg, f"{side}_shin_trim", [x - .8, 2.7, -1.78], [x + .8, 5.9, -1.62], "ash")

    add(m, left_arm, "bound_upper_arm", [-6, 15.8, -.8], [-3.7, 21, 1.3], "void")
    add(m, left_arm, "bound_forearm", [-6.3, 10, -1], [-4, 16.4, 1.2], "cloth")
    add(m, left_arm, "left_hand", [-6.1, 8.8, -1.2], [-4.2, 11, 1], "armor")
    add(m, left_arm, "arm_binding", [-6.35, 12.7, -1.35], [-3.9, 13.6, 1.4], "ash")
    add(m, left_arm, "left_arm_tear", [-6.5, 11.2, 1.1], [-4.9, 16.2, 1.8], "void")
    add(m, left_arm, "left_gauntlet_finger", [-5.6, 7.8, -1], [-4.8, 9.4, .6], "edge")
    add(m, right_arm, "right_upper_arm", [3.7, 15.8, -.8], [6, 21, 1.5], "armor")
    add(m, right_arm, "right_bracer", [3.8, 11.1, -1.1], [6.2, 16.5, 1.5], "edge")
    add(m, right_arm, "right_hand", [4, 9.7, -1.2], [6, 12.2, 1.2], "armor")
    add(m, right_arm, "bracer_ember", [4, 13.1, -1.28], [6, 13.7, -1.16], "ember")
    add(m, right_arm, "right_elbow_ridge", [3.5, 15.8, -1.4], [6.5, 16.5, 1.7], "edge")
    add(m, right_arm, "right_knuckles", [4, 9.5, -1.5], [6, 10.5, -1.15], "edge")

    # The blade hangs from the hand at rest. Arm rotation gives it one clean
    # arc for telegraphed sweeps instead of many independently animated cubes.
    add(m, blade, "pommel", [4.1, 11.8, -1], [5.9, 13.7, 1], "ash")
    add(m, blade, "grip", [4.45, 9, -.55], [5.55, 12.4, .55], "void")
    add(m, blade, "guard", [1.9, 8.6, -.75], [8.1, 9.4, .75], "edge")
    add(m, blade, "guard_left_tooth", [1.4, 8.4, -.9], [2.5, 10.4, .9], "ash")
    add(m, blade, "guard_right_tooth", [7.5, 8.4, -.9], [8.6, 10.4, .9], "ash")
    add(m, blade, "blade_core", [3.55, -8, -.55], [6.45, 8.6, .55], "armor")
    add(m, blade, "blade_left_edge", [3.15, -5.8, -.65], [3.65, 7.7, .65], "edge")
    add(m, blade, "blade_right_edge", [6.35, -5.8, -.65], [6.85, 7.7, .65], "edge")
    add(m, blade, "blade_ember", [4.7, -5.6, -.68], [5.3, 7.2, -.57], "ember")
    add(m, blade, "blade_point", [4.15, -10.2, -.45], [5.85, -7.9, .45], "ash")
    add(m, blade, "blade_notch_left", [3.4, -4.9, -.8], [4.05, -3.7, .8], "void")
    add(m, blade, "blade_notch_right", [5.95, -1.4, -.8], [6.7, -.4, .8], "void")
    add(m, blade, "blade_back_ridge", [4.5, -6.7, .55], [5.5, 7.5, .9], "edge")

    # Separate ragged cape strips move as two broad masses while the tattered
    # ends make a stepped silhouette. No borrowed game meshes or textures.
    add(m, cape_left, "left_cape_upper", [-4.4, 17, 2.4], [0, 22, 3.2], "cloth")
    add(m, cape_left, "left_cape_tail", [-6.7, 5.5, 2.8], [-1, 18.1, 3.55], "cloth")
    add(m, cape_left, "left_cape_tip", [-6.4, 3.1, 3], [-2.4, 6.3, 3.5], "void")
    add(m, cape_left_edge, "left_cape_fringe", [-8.1, 8, 2.7], [-5.6, 16.9, 3.4], "cloth")
    add(m, cape_left_edge, "left_cape_fringe_tip", [-8, 5.8, 2.9], [-6.4, 8.7, 3.35], "void")
    add(m, cape_left_edge, "left_shouldercape", [-7.2, 17.4, -.2], [-5.5, 21.9, 2.8], "cloth")
    add(m, cape_left_edge, "left_frayed_front", [-7.5, 11.2, -.4], [-6.1, 17.9, 1.6], "cloth")
    add(m, cape_right, "right_cape_upper", [0, 17, 2.4], [4.2, 22, 3.2], "cloth")
    add(m, cape_right, "right_cape_tail", [1, 8.3, 2.8], [6.2, 18, 3.55], "cloth")
    add(m, cape_right, "right_cape_tip", [2.7, 6, 3], [6, 9, 3.5], "void")
    add(m, cape_right_edge, "right_cape_fringe", [5.6, 12.6, 2.8], [7.3, 19.3, 3.45], "cloth")
    add(m, cape_right_edge, "right_shouldercape", [5.3, 17.4, -.2], [7, 21.5, 2.8], "cloth")

    head_bone = m.bone("head", [0, 22, 0], helm)
    left_arm_bone = m.bone("left_arm", [-4.8, 21, 0], left_arm)
    sword_bone = m.bone("sword", [5, 10.5, 0], blade)
    sword_bone["rotation"] = [0, 0, 60]
    right_arm_bone = m.bone("right_arm", [4.8, 21, 0], right_arm + [sword_bone])
    left_cape_bone = m.bone("cape_left", [-2.3, 21, 2.8], cape_left)
    right_cape_bone = m.bone("cape_right", [2.2, 21, 2.8], cape_right)
    left_edge_bone = m.bone("cape_left_edge", [-6, 20, 2.7], cape_left_edge)
    right_edge_bone = m.bone("cape_right_edge", [6, 20, 2.7], cape_right_edge)
    torso_bone = m.bone("torso", [0, 13, 0], chest + [head_bone, left_arm_bone, right_arm_bone,
        left_cape_bone, right_cape_bone, left_edge_bone, right_edge_bone])
    left_leg_bone = m.bone("left_leg", [-2.1, 10.7, 0], left_leg)
    right_leg_bone = m.bone("right_leg", [2.1, 10.7, 0], right_leg)
    root = m.bone("root", [0, 0, 0], hips + [torso_bone, left_leg_bone, right_leg_bone])

    m.anim("idle", 2.0, {
        "root": [(0, [0, 0, 0], "position"), (1, [0, .28, 0], "position"), (2, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (1, [18, 0, -5]), (2, [16, 0, -6])],
        "head": [(0, [-9, -5, 0]), (1, [-11, -2, 0]), (2, [-9, -5, 0])],
        "right_arm": [(0, [4, 0, 4]), (1, [0, 0, 6]), (2, [4, 0, 4])],
        "cape_left": [(0, [0, 0, -5]), (1, [-6, 0, -10]), (2, [0, 0, -5])],
        "cape_right": [(0, [0, 0, 4]), (1, [-4, 0, 8]), (2, [0, 0, 4])],
        "cape_left_edge": [(0, [-4, 0, -8]), (.5, [-14, 0, -17]), (1.2, [-5, 0, -8]), (2, [-4, 0, -8])],
        "cape_right_edge": [(0, [-3, 0, 8]), (.8, [-11, 0, 15]), (1.5, [-4, 0, 7]), (2, [-3, 0, 8])],
    }, loop="loop")
    m.anim("walk", .8, {
        "root": [(0, [0, 0, 0], "position"), (.2, [0, .35, 0], "position"), (.4, [0, 0, 0], "position"), (.6, [0, .35, 0], "position"), (.8, [0, 0, 0], "position")],
        "left_leg": [(0, [-24, 0, 0]), (.4, [24, 0, 0]), (.8, [-24, 0, 0])],
        "right_leg": [(0, [24, 0, 0]), (.4, [-24, 0, 0]), (.8, [24, 0, 0])],
        "torso": [(0, [11, -4, -3]), (.4, [11, 4, -3]), (.8, [11, -4, -3])],
        "cape_left": [(0, [-13, 0, 0]), (.4, [-6, 0, -8]), (.8, [-13, 0, 0])],
        "cape_right": [(0, [-7, 0, 7]), (.4, [-14, 0, 0]), (.8, [-7, 0, 7])],
    }, loop="loop")
    m.anim("run", .6, {
        "root": [(0, [0, .15, 0], "position"), (.15, [0, .55, 0], "position"), (.3, [0, .15, 0], "position"), (.45, [0, .55, 0], "position"), (.6, [0, .15, 0], "position")],
        "torso": [(0, [30, 0, -4]), (.3, [34, 0, 0]), (.6, [30, 0, -4])],
        "left_leg": [(0, [-35, 0, 0]), (.3, [35, 0, 0]), (.6, [-35, 0, 0])],
        "right_leg": [(0, [35, 0, 0]), (.3, [-35, 0, 0]), (.6, [35, 0, 0])],
        "cape_left": [(0, [-25, 0, -9]), (.3, [-40, 0, -14]), (.6, [-25, 0, -9])],
        "cape_right": [(0, [-35, 0, 12]), (.3, [-22, 0, 6]), (.6, [-35, 0, 12])],
        "cape_left_edge": [(0, [-45, 0, -13]), (.3, [-25, 0, -20]), (.6, [-45, 0, -13])],
    }, loop="loop")
    m.anim("awaken", 1.6, {
        "torso": [(0, [27, 0, -10]), (.55, [15, 0, -5]), (1.2, [-7, 0, 0]), (1.6, [8, 0, -4])],
        "head": [(0, [16, 0, 0]), (.8, [0, 0, 0]), (1.6, [-9, 0, 0])],
        "right_arm": [(0, [15, 0, 15]), (.8, [-50, 0, -24]), (1.6, [4, 0, 4])],
        "cape_left": [(0, [20, 0, -7]), (1.1, [-18, 0, -22]), (1.6, [0, 0, -5])],
    })
    m.anim("cleave", 1.25, {
        "root": [(0, [0, 0, 0], "position"), (.35, [0, -.55, 0], "position"), (.72, [0, .4, -1], "position"), (1.25, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.38, [9, -32, -13]), (.7, [20, 38, 7]), (.92, [22, 46, 9]), (1.25, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.38, [-95, 0, -37]), (.66, [-38, 0, 72]), (.9, [25, 0, 92]), (1.25, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.38, [0, 0, -80]), (.9, [0, 0, 45]), (1.25, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.72, [-22, 0, -19]), (1.25, [0, 0, -5])],
    })
    m.anim("cleave_reverse", 1.05, {
        "root": [(0, [0, 0, 0], "position"), (.5, [0, .45, -.6], "position"), (1.05, [0, 0, 0], "position")],
        "torso": [(0, [18, 40, 9]), (.22, [20, 48, 10]), (.5, [13, -40, -12]), (1.05, [16, 0, -6])],
        "right_arm": [(0, [22, 0, 90]), (.22, [28, 0, 100]), (.5, [-40, 0, -65]), (.78, [-25, 0, -80]), (1.05, [4, 0, 4])],
        "sword": [(0, [0, 0, 45]), (.5, [0, 0, -75]), (1.05, [0, 0, 0])],
        "cape_right": [(0, [-8, 0, 8]), (.5, [-30, 0, 22]), (1.05, [0, 0, 4])],
    })
    m.anim("thrust", 1.0, {
        "root": [(0, [0, 0, 0], "position"), (.32, [0, -.4, 0], "position"), (.55, [0, .1, -2], "position"), (1, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (.32, [7, 18, -7]), (.55, [27, -12, 3]), (1, [16, 0, -6])],
        "right_arm": [(0, [4, 0, 4]), (.32, [-75, 0, -28]), (.55, [-96, 0, 3]), (.8, [-90, 0, 2]), (1, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.32, [0, 0, -60]), (.55, [0, 0, -75]), (1, [0, 0, 0])],
        "left_leg": [(0, [0, 0, 0]), (.55, [-33, 0, 0]), (1, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.55, [19, 0, 0]), (1, [0, 0, 0])],
    })
    m.anim("dash", .9, {
        "torso": [(0, [8, 0, -4]), (.2, [28, 0, -3]), (.65, [30, 0, 4]), (.9, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.2, [-50, 0, -12]), (.65, [-45, 0, -10]), (.9, [4, 0, 4])],
        "left_leg": [(0, [0, 0, 0]), (.25, [-35, 0, 0]), (.65, [20, 0, 0]), (.9, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.25, [22, 0, 0]), (.65, [-35, 0, 0]), (.9, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.25, [-37, 0, -10]), (.65, [-28, 0, -17]), (.9, [0, 0, -5])],
        "cape_right": [(0, [0, 0, 4]), (.25, [-34, 0, 12]), (.65, [-26, 0, 7]), (.9, [0, 0, 4])],
    })
    m.anim("leap", 1.1, {
        "root": [(0, [0, 0, 0], "position"), (.2, [0, -.7, 0], "position"), (.55, [0, 4.5, 0], "position"), (.85, [0, 3.2, 0], "position"), (1.1, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.2, [24, 0, 0]), (.55, [-15, 0, -3]), (1.1, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.55, [-135, 0, -8]), (.85, [-120, 0, -8]), (1.1, [4, 0, 4])],
        "left_leg": [(0, [0, 0, 0]), (.55, [-35, 0, 0]), (1.1, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.55, [-20, 0, 0]), (1.1, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.55, [30, 0, -20]), (1.1, [0, 0, -5])],
    })
    m.anim("slam", 1.35, {
        "root": [(0, [0, 0, 0], "position"), (.72, [0, 1.4, 0], "position"), (.95, [0, -1, -1], "position"), (1.35, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.7, [-18, 0, -4]), (.94, [43, 0, -4]), (1.35, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.7, [-155, 0, 0]), (.94, [45, 0, 4]), (1.35, [4, 0, 4])],
        "left_arm": [(0, [0, 0, 0]), (.7, [-30, 0, -15]), (.94, [20, 0, -12]), (1.35, [0, 0, 0])],
        "cape_right": [(0, [0, 0, 4]), (.94, [-32, 0, 19]), (1.35, [0, 0, 4])],
    })
    m.anim("stagger", .8, {
        "root": [(0, [0, 0, 0], "position"), (.25, [0, -.8, 0], "position"), (.8, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.25, [-25, 0, 10]), (.8, [8, 0, -4])],
        "head": [(0, [0, 0, 0]), (.25, [20, 0, 0]), (.8, [0, 0, 0])],
        "right_arm": [(0, [4, 0, 4]), (.25, [42, 0, 22]), (.8, [4, 0, 4])],
    })
    m.anim("enrage", 1.5, {
        "root": [(0, [0, 0, 0], "position"), (.3, [0, -1.1, 0], "position"), (1.1, [0, -.7, 0], "position"), (1.5, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (.3, [47, 0, -10]), (.8, [-22, 0, 3]), (1.5, [14, 0, -5])],
        "head": [(0, [-9, 0, 0]), (.3, [24, 0, 0]), (.8, [-26, 0, 0]), (1.5, [-9, 0, 0])],
        "right_arm": [(0, [4, 0, 4]), (.3, [48, 0, 22]), (.8, [-90, 0, 70]), (1.5, [4, 0, 4])],
        "left_arm": [(0, [0, 0, 0]), (.8, [-70, 0, -60]), (1.5, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.8, [-33, 0, -27]), (1.5, [0, 0, -5])],
        "cape_right": [(0, [0, 0, 4]), (.8, [-37, 0, 30]), (1.5, [0, 0, 4])],
    })
    m.anim("death", 1.6, {
        "root": [(0, [0, 0, 0], "position"), (.8, [0, -2, 0], "position"), (1.6, [0, -5, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.7, [38, 0, -8]), (1.6, [86, 0, -9])],
        "right_arm": [(0, [4, 0, 4]), (.7, [35, 0, 36]), (1.6, [88, 0, 85])],
        "head": [(0, [0, 0, 0]), (1.6, [-24, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (1.6, [-36, 0, -34])],
    })

    m.write(out, root)
    build_warning(out)
    build_fissure(out)


def build_warning(out):
    """A readable red ring on the floor, separate from the attack hit shape."""
    m = authoring.Model("ashen_warning", size=64)
    middle = 31.5
    for y in range(64):
        for x in range(64):
            dx, dy = x - middle, y - middle
            radius = math.hypot(dx, dy)
            if 28 <= radius <= 31.5:
                m.pixels[y][x] = (246, 75, 54, 235)
            elif 23 <= radius < 28 and (x + y) % 7 < 3:
                m.pixels[y][x] = (140, 39, 42, 130)
            elif abs(dx) < .8 and 2 < abs(dy) < 20:
                m.pixels[y][x] = (237, 101, 55, 215)
            elif abs(dy) < .8 and 2 < abs(dx) < 20:
                m.pixels[y][x] = (237, 101, 55, 215)
    parts = []
    m.cube("warning_ring", [-16, 0, -16], [16, .03, 16], "armor", parts,
           face_uv={"up": [0, 0, 64, 64], "down": [0, 0, 64, 64]})
    m.write(out, m.bone("ring", [0, 0, 0], parts))


def build_fissure(out):
    """Three rising groups of stone and light for the sword-slam impact."""
    m = KnightModel("ashen_fissure", size=128, texels=2)
    waves = [[], [], []]
    for index in range(12):
        group = waves[index // 4]
        z = -14 + index * 2.2
        x = (-1 if index % 2 else 1) * (.35 + index % 3 * .45)
        rise = 2.2 + index % 4 * .8
        add(m, group, f"stone_{index}", [x - .95, 0, z - .75], [x + .95, rise, z + .75], "ash")
        add(m, group, f"ember_{index}", [x - .25, .1, z - .85], [x + .25, rise - .3, z - .75], "ember")
        add(m, group, f"cap_{index}", [x - .55, rise - .4, z - .55], [x + .55, rise + .55, z + .55], "edge")
    bones = [m.bone(f"wave_{i+1}", [0, 0, 0], wave) for i, wave in enumerate(waves)]
    root = m.bone("root", [0, 0, 0], bones)
    tracks = {}
    for i in range(3):
        start = i * .12
        tracks[f"wave_{i+1}"] = [
            (0, [0, -5, 0], "position"),
            (start, [0, -5, 0], "position"),
            (start + .16, [0, .55, 0], "position"),
            (start + .28, [0, 0, 0], "position"),
            (.9, [0, 0, 0], "position"),
            (1.3, [0, -5, 0], "position"),
        ]
    m.anim("burst", 1.3, tracks)
    m.write(out, root)


if __name__ == "__main__":
    build()
