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
    "armor": ("20252d", "343d48", "64727c"),
    "edge": ("35414b", "71828b", "b2bcb6"),
    "cloth": ("142b58", "28548e", "5686ba"),
    "void": ("090d14", "161d29", "313b49"),
    "ash": ("343a42", "5d6871", "98a4a9"),
    "ember": ("9d342f", "e56948", "ffc383"),
    "eye": ("0a101a", "121d29", "2d4857"),
    "mail": ("11161d", "2d343b", "55616a"),
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
                fold = (x // 4 + y // 17 + seed) % 11
                tone = 0 if fold in (0, 1) else 2 if fold in (5, 6) else 1
                if (x + y * 2 + seed) % 29 == 0:
                    tone = 2
                if fine % 67 == 0:
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
            elif material == "mail":
                tone = 2 if (x + y) % 5 == 0 else 0 if x % 3 == 0 else 1
            elif material in ("ember", "eye"):
                tone = 2 if (x + y) % 5 < 3 else 1
            return (*shades[tone], 255)

        return self.patch(width, height, paint, (width, height, material, side, motif))


def add(model, bucket, name, lo, hi, material, motif="plain"):
    model.cube(name, lo, hi, material, bucket, motif)


def build(out=ROOT / "model-lab" / "models"):
    out.mkdir(parents=True, exist_ok=True)
    m = KnightModel("ashen_knight", size=512, texels=2)

    hips, chest, helm = [], [], []
    left_leg, right_leg, left_arm, right_arm, blade = [], [], [], [], []
    cape_left, cape_right, cape_left_edge, cape_right_edge = [], [], [], []
    scarf, plume, cape_center = [], [], []

    # Narrow waist, uneven shoulders and a hunched profile keep the outline
    # readable at Minecraft viewing distances. The front faces negative Z.
    add(m, hips, "fauld_core", [-3.2, 10.5, -1.8], [3.2, 14, 2], "armor")
    add(m, hips, "fauld_left", [-3.8, 9.2, -2], [-.3, 12, 2.2], "edge")
    add(m, hips, "fauld_right", [.3, 9.6, -2], [3.5, 12, 2.2], "armor")
    add(m, hips, "belt", [-3.4, 12.3, -2.15], [3.4, 13.2, -1.7], "ash")
    add(m, hips, "mail_skirt", [-3.25, 8.7, -1.55], [3.25, 12.5, 2.4], "mail")
    add(m, hips, "waist_tasset_left", [-3.7, 9, -2.5], [-2, 12.3, -.9], "armor")
    add(m, hips, "waist_tasset_right", [1.9, 9.8, -2.45], [3.6, 12.4, -.9], "armor")
    add(m, hips, "belt_buckle", [-.8, 11.9, -2.55], [.8, 13.1, -1.96], "edge")
    add(m, chest, "cuirass", [-3.6, 13, -2.4], [3.6, 22, 2.1], "armor")
    add(m, chest, "left_breast_plate", [-4.1, 17, -2.8], [-.4, 22.5, -2.1], "edge")
    add(m, chest, "right_breast_plate", [.5, 17.2, -2.7], [3.8, 21.7, -2.1], "armor")
    add(m, chest, "heart_fissure", [-.35, 16, -2.84], [.35, 17.2, -2.75], "void")
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
    add(m, chest, "mail_under_left", [-4.35, 15.8, -1.8], [-3.75, 20.7, 1.5], "mail")
    add(m, chest, "mail_under_right", [3.7, 15.4, -1.7], [4.35, 20.5, 1.4], "mail")
    add(m, chest, "left_pauldron_second_plate", [-6.6, 20.5, -2.98], [-3.3, 21.5, -2.72], "edge")
    add(m, chest, "right_pauldron_second_plate", [3.7, 20.3, -2.92], [6.1, 21.1, -2.46], "edge")

    # Royal-blue wrapping interrupts the chest armor and makes the head/torso
    # one continuous shape. The shoulder scarf also extends over the back.
    add(m, scarf, "scarf_dark_under", [-3.35, 20.5, -3.02], [3.1, 23.5, 2.45], "void")
    add(m, scarf, "scarf_fold_high", [-3.8, 21.5, -3.48], [2.1, 23.2, -2.78], "cloth")
    add(m, scarf, "scarf_fold_mid", [-3.4, 19.7, -3.42], [2.9, 21.4, -2.82], "cloth")
    add(m, scarf, "scarf_fold_low", [-2.6, 18.5, -3.24], [3.25, 19.9, -2.8], "cloth")
    add(m, scarf, "scarf_left_drape", [-5.6, 18.8, -2.2], [-3.25, 22.4, 2.25], "cloth")
    add(m, scarf, "scarf_right_drape", [2.4, 19.7, -2.45], [5.2, 22.7, 2.65], "cloth")
    add(m, scarf, "scarf_back", [-4.8, 19.3, 2.25], [4.9, 22.6, 3.18], "cloth")
    add(m, scarf, "scarf_hanging_point", [-3.75, 15.2, -3.1], [-1.6, 19.6, -2.78], "cloth")

    add(m, helm, "hood", [-2.7, 21.9, -2.3], [2.7, 27.5, 2.8], "void")
    add(m, helm, "visor", [-2.25, 23.3, -3.15], [2.25, 26.3, -2.25], "edge")
    add(m, helm, "visor_brow", [-2.5, 25.6, -3.62], [2.5, 26.3, -2.7], "edge")
    add(m, helm, "visor_bridge", [-.55, 23.3, -3.92], [.55, 26.1, -3.18], "edge")
    add(m, helm, "visor_beak_upper", [-1.45, 23.1, -4.25], [1.45, 24.1, -3.31], "edge")
    add(m, helm, "visor_beak_point", [-.78, 22.55, -4.52], [.78, 23.45, -3.77], "edge")
    add(m, helm, "visor_left_carving", [-2.35, 23.65, -3.78], [-1.75, 25.3, -3.56], "edge")
    add(m, helm, "visor_right_carving", [1.75, 23.65, -3.78], [2.35, 25.3, -3.56], "edge")
    add(m, helm, "visor_left_eye_frame", [-2.15, 24.25, -3.81], [-.3, 25, -3.55], "armor")
    add(m, helm, "visor_right_eye_frame", [.3, 24.25, -3.81], [2.15, 25, -3.55], "armor")
    add(m, helm, "left_eye", [-1.95, 24.5, -3.59], [-.45, 24.75, -3.49], "eye")
    add(m, helm, "right_eye", [.45, 24.5, -3.59], [1.95, 24.75, -3.49], "eye")
    add(m, helm, "crest_base", [-.85, 26.9, -.4], [.85, 28.1, 1.4], "armor")
    add(m, helm, "crest_tip", [-.45, 27.6, -.1], [.45, 29, .8], "edge")
    add(m, helm, "left_cheek_plate", [-2.9, 22.8, -3.0], [-1.8, 25.3, -.9], "edge")
    add(m, helm, "right_cheek_plate", [1.8, 22.8, -3.0], [2.9, 25.3, -.9], "edge")
    add(m, helm, "helmet_left_cheek_fin", [-3.45, 22.5, -2.55], [-2.6, 26.3, -.9], "armor")
    add(m, helm, "helmet_right_cheek_fin", [2.6, 22.5, -2.55], [3.45, 26.3, -.9], "armor")
    add(m, helm, "face_lower_mask", [-1.9, 21.8, -3.55], [1.9, 23.5, -2.8], "edge")

    # A cutout engraved faceplate supplies a distinct long-muzzled silhouette
    # without reproducing another game's texture or sculpt. The existing helm
    # cubes retain the depth from the side and above.
    def paint_faceplate(px, py):
        center = 24
        distance = abs(px - center)
        if py < 6:
            width = 3 + py // 2
        elif py < 18:
            width = 13 if py < 13 else 17
        elif py < 35:
            width = 18 - max(0, py - 27) // 3
        elif py < 52:
            width = 13 - (py - 35) // 5
        else:
            width = max(1, 9 - (py - 52))
        ear = 8 <= py < 22 and 17 <= distance <= 21 - (py - 8) // 6
        if distance > width and not ear:
            return (0, 0, 0, 0)
        if 28 <= py <= 33 and 4 <= distance <= 13:
            return (8, 15, 23, 255)
        if distance <= 2 and 12 <= py < 53:
            return (177, 188, 184, 255)
        if abs(distance - width) <= 1 or (py < 18 and distance <= 3):
            return (172, 185, 179, 255)
        if py in (20, 21, 39, 40) and distance < width - 2:
            return (44, 62, 69, 255)
        if 35 < py < 49 and distance in (4, 5):
            return (32, 46, 55, 255)
        if (px * 3 + py * 5) % 31 == 0:
            return (139, 151, 148, 255)
        return (76, 92, 98, 255)

    faceplate_uv = m.patch(48, 64, paint_faceplate, "ashen_faceplate")
    m.cube("engraved_wolf_visor", [-3.25, 21.25, -4.78], [3.25, 29.25, -4.7],
           "edge", helm, face_uv={"north": faceplate_uv, "south": faceplate_uv})
    # The loose black crest gives the helmet a long, backward swept profile.
    add(m, plume, "crest_root", [-1.3, 28, .55], [1.25, 29, 3.2], "void")
    add(m, plume, "crest_arch", [-1.65, 28.5, 2.7], [1.5, 29.25, 5.9], "void")
    add(m, plume, "crest_back", [-2.2, 27.6, 5.25], [1.05, 28.4, 8.1], "void")
    add(m, plume, "crest_long_tip", [-2.7, 25.4, 7.4], [-1.75, 28.1, 8.05], "void")
    add(m, plume, "crest_short_tip", [.4, 26.25, 6.7], [1.1, 28.15, 7.3], "void")

    for side, x in (("left", -2.1), ("right", 2.1)):
        leg = left_leg if side == "left" else right_leg
        add(m, leg, f"{side}_thigh", [x - 1.4, 6.8, -1.45], [x + 1.4, 11.3, 1.5], "armor")
        add(m, leg, f"{side}_greave", [x - 1.25, 2, -1.6], [x + 1.25, 7.5, 1.5], "edge")
        add(m, leg, f"{side}_boot", [x - 1.4, 0, -2.8], [x + 1.4, 2.8, 1.7], "void")
        add(m, leg, f"{side}_knee", [x - 1.45, 6.2, -2], [x + 1.45, 7.6, -.95], "armor")
        add(m, leg, f"{side}_knee_ridge", [x - 1.1, 6.8, -2.35], [x + 1.1, 7.2, -1.9], "edge")
        add(m, leg, f"{side}_shin_trim", [x - .8, 2.7, -1.78], [x + .8, 5.9, -1.62], "ash")
        add(m, leg, f"{side}_thigh_mail", [x - 1.5, 7.3, -1.8], [x + 1.5, 10.4, 1.65], "mail")
        add(m, leg, f"{side}_greave_lip", [x - 1.43, 4.65, -1.98], [x + 1.43, 5.45, 1.55], "armor")
        add(m, leg, f"{side}_sabatons", [x - 1.2, .15, -3.8], [x + 1.15, 1.15, -1.95], "edge")
        add(m, leg, f"{side}_ankle_guard", [x - 1.45, 1.15, -1.85], [x + 1.45, 2.3, 1.25], "armor")

    add(m, left_arm, "bound_upper_arm", [-6, 15.8, -.8], [-3.7, 21, 1.3], "void")
    add(m, left_arm, "bound_forearm", [-6.3, 10, -1], [-4, 16.4, 1.2], "cloth")
    add(m, left_arm, "left_hand", [-6.1, 8.8, -1.2], [-4.2, 11, 1], "armor")
    add(m, left_arm, "arm_binding", [-6.35, 12.7, -1.35], [-3.9, 13.6, 1.4], "ash")
    add(m, left_arm, "left_arm_tear", [-6.5, 11.2, 1.1], [-4.9, 16.2, 1.8], "void")
    add(m, left_arm, "left_gauntlet_finger", [-5.6, 7.8, -1], [-4.8, 9.4, .6], "edge")
    add(m, left_arm, "left_mail_shoulder", [-6.15, 17.2, -1], [-3.55, 20.2, 1.5], "mail")
    add(m, left_arm, "left_wrapping_low", [-6.5, 10.6, -1.4], [-3.9, 12.2, 1.2], "cloth")
    add(m, left_arm, "left_gauntlet_plate", [-6.4, 8.8, -1.55], [-4.05, 10.5, -.95], "armor")
    add(m, right_arm, "right_upper_arm", [3.7, 15.8, -.8], [6, 21, 1.5], "armor")
    add(m, right_arm, "right_bracer", [3.8, 11.1, -1.1], [6.2, 16.5, 1.5], "edge")
    add(m, right_arm, "right_hand", [4, 9.7, -1.2], [6, 12.2, 1.2], "armor")
    add(m, right_arm, "bracer_engraving", [4, 13.1, -1.28], [6, 13.7, -1.16], "ash")
    add(m, right_arm, "right_elbow_ridge", [3.5, 15.8, -1.4], [6.5, 16.5, 1.7], "edge")
    add(m, right_arm, "right_knuckles", [4, 9.5, -1.5], [6, 10.5, -1.15], "edge")
    add(m, right_arm, "right_mail_shoulder", [3.5, 17.3, -1], [6.05, 20, 1.55], "mail")
    add(m, right_arm, "right_bracer_ridge", [3.55, 12.2, -1.45], [6.45, 13.05, 1.62], "armor")
    add(m, right_arm, "right_gauntlet_plate", [3.85, 10.1, -1.55], [6.15, 11.2, -.95], "edge")

    # The sword arm reaches across the chest, holding the long blade behind
    # the shoulder at rest; animation rotates the arm and blade separately.
    add(m, blade, "pommel", [4.1, 11.8, -1], [5.9, 13.7, 1], "ash")
    add(m, blade, "grip", [4.45, 9, -.55], [5.55, 12.4, .55], "void")
    add(m, blade, "guard", [1.7, 8.6, -.95], [8.3, 9.4, .95], "edge")
    add(m, blade, "guard_left_tooth", [1.35, 8.2, -1.1], [2.65, 10.6, 1.1], "ash")
    add(m, blade, "guard_right_tooth", [7.35, 8.2, -1.1], [8.65, 10.6, 1.1], "ash")
    add(m, blade, "blade_core", [3.15, -9.4, -.78], [6.85, 8.6, .78], "armor")
    add(m, blade, "blade_left_edge", [2.8, -7.7, -.86], [3.25, 7.7, .86], "edge")
    add(m, blade, "blade_right_edge", [6.75, -7.7, -.86], [7.2, 7.7, .86], "edge")
    add(m, blade, "blade_fuller", [4.8, -7.2, -.83], [5.2, 6.8, -.72], "void")
    add(m, blade, "blade_weathering", [5.45, -3.6, -.84], [6, 3.4, -.76], "ash")
    add(m, blade, "blade_point", [4, -11.8, -.5], [6, -9.2, .5], "ash")
    add(m, blade, "blade_notch_left", [2.75, -6.8, -.9], [3.55, -5.8, .9], "void")
    add(m, blade, "blade_notch_right", [6.45, -2.9, -.9], [7.2, -1.9, .9], "void")
    add(m, blade, "blade_back_ridge", [4.55, -8.1, .78], [5.45, 7.5, 1.1], "edge")

    # Separate ragged cape strips move as two broad masses while the tattered
    # ends make a stepped silhouette. No borrowed game meshes or textures.
    add(m, cape_left, "left_cape_upper", [-4.4, 17, 2.4], [0, 22, 3.2], "cloth")
    add(m, cape_left, "left_cape_tail", [-6.7, 5.5, 2.8], [-1, 18.1, 3.55], "cloth")
    add(m, cape_left, "left_cape_tip", [-6.4, 3.1, 3], [-2.4, 6.3, 3.5], "void")
    add(m, cape_left_edge, "left_cape_fringe", [-8.1, 8, 2.7], [-5.6, 16.9, 3.4], "cloth")
    add(m, cape_left_edge, "left_cape_fringe_tip", [-8, 5.8, 2.9], [-6.4, 8.7, 3.35], "void")
    add(m, cape_left_edge, "left_shouldercape", [-7.2, 17.4, -.2], [-5.5, 21.9, 2.8], "cloth")
    add(m, cape_left_edge, "left_frayed_front", [-7.5, 11.2, -.4], [-6.1, 17.9, 1.6], "cloth")
    for index, bottom in enumerate((4.4, 2.8, 5.9)):
        x = -9.6 + index * 1.8
        add(m, cape_left_edge, f"left_rag_{index}", [x, bottom, 3], [x + 1.45, 11.8 + index % 2, 3.55], "cloth", f"rag{index}")
    add(m, cape_right, "right_cape_upper", [0, 17, 2.4], [4.2, 22, 3.2], "cloth")
    add(m, cape_right, "right_cape_tail", [1, 8.3, 2.8], [6.2, 18, 3.55], "cloth")
    add(m, cape_right, "right_cape_tip", [2.7, 6, 3], [6, 9, 3.5], "void")
    add(m, cape_right_edge, "right_cape_fringe", [5.6, 12.6, 2.8], [7.3, 19.3, 3.45], "cloth")
    add(m, cape_right_edge, "right_shouldercape", [5.3, 17.4, -.2], [7, 21.5, 2.8], "cloth")
    for index, bottom in enumerate((7.1, 4.8)):
        x = 3.6 + index * 1.75
        add(m, cape_right_edge, f"right_rag_{index}", [x, bottom, 3], [x + 1.3, 13 + index, 3.55], "cloth", f"rag{index}")

    # Layered back silhouette: short mail at the waist, then long staggered
    # blue strips. Each piece is independently textured with cutout tears.
    add(m, cape_center, "back_mail", [-4.25, 8.3, 3.6], [4.3, 13.2, 3.85], "mail")
    add(m, cape_center, "cape_middle_upper", [-3.45, 12.2, 3.9], [3.2, 19.5, 4.25], "cloth")
    add(m, cape_center, "cape_middle_rag", [-2.25, 3.5, 4], [1.55, 13.2, 4.36], "cloth")
    add(m, cape_center, "cape_middle_point", [-1.55, 1.7, 4.06], [-.45, 5.4, 4.38], "cloth")
    add(m, cape_left_edge, "left_outer_streamer", [-10.4, 7.2, 3.2], [-8.2, 18.6, 3.62], "cloth")
    add(m, cape_left_edge, "left_outer_tip", [-10.15, 2.1, 3.28], [-9, 8.4, 3.65], "cloth")
    add(m, cape_right_edge, "right_outer_streamer", [7.1, 8.8, 3.15], [9.65, 17.3, 3.54], "cloth")
    add(m, cape_right_edge, "right_outer_tip", [8.15, 3.3, 3.24], [9.35, 9.3, 3.59], "cloth")

    plume_bone = m.bone("plume", [0, 28, 1], plume)
    head_bone = m.bone("head", [0, 22, 0], helm + [plume_bone])
    left_arm_bone = m.bone("left_arm", [-4.8, 21, 0], left_arm)
    sword_bone = m.bone("sword", [5, 10.5, 0], blade)
    sword_bone["rotation"] = [0, 0, -140]
    right_arm_bone = m.bone("right_arm", [4.8, 21, 0], right_arm + [sword_bone])
    left_cape_bone = m.bone("cape_left", [-2.3, 21, 2.8], cape_left)
    right_cape_bone = m.bone("cape_right", [2.2, 21, 2.8], cape_right)
    middle_cape_bone = m.bone("cape_center", [0, 18, 3], cape_center)
    left_edge_bone = m.bone("cape_left_edge", [-6, 20, 2.7], cape_left_edge)
    right_edge_bone = m.bone("cape_right_edge", [6, 20, 2.7], cape_right_edge)
    torso_bone = m.bone("torso", [0, 13, 0], chest + scarf + [head_bone, left_arm_bone, right_arm_bone,
        left_cape_bone, right_cape_bone, middle_cape_bone, left_edge_bone, right_edge_bone])
    left_leg_bone = m.bone("left_leg", [-2.1, 10.7, 0], left_leg)
    right_leg_bone = m.bone("right_leg", [2.1, 10.7, 0], right_leg)
    torso_bone["rotation"] = [-12, 0, -5]
    right_arm_bone["rotation"] = [0, 0, -70]
    left_leg_bone["rotation"] = [-6, 0, -10]
    right_leg_bone["rotation"] = [12, 0, 10]
    plume_bone["rotation"] = [0, -9, 0]
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
    build_slash(out)


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


def build_slash(out):
    """Broad pixel-painted blade arc, animated as one brief detached effect."""
    m = authoring.Model("ashen_slash", size=64)
    for y in range(64):
        for x in range(64):
            dx, dy = x - 31.5, y - 37
            radius = math.hypot(dx, dy)
            angle = math.atan2(dy, dx)
            if -2.95 < angle < -.15 and 19 < radius < 30:
                if radius > 28.5:
                    m.pixels[y][x] = (247, 230, 203, 255)
                elif radius > 25:
                    m.pixels[y][x] = (252, 133, 88, 245)
                elif (x * 7 + y * 3) % 11 < 8:
                    m.pixels[y][x] = (134, 57, 65, 210)
            if -2.7 < angle < -.35 and 15 < radius <= 19 and (x + y) % 7 < 2:
                m.pixels[y][x] = (77, 139, 171, 175)
    pieces = []
    m.cube("arc", [-16, 0, -.05], [16, 32, .05], "armor", pieces,
           face_uv={"north": [0, 0, 64, 64], "south": [0, 0, 64, 64]})
    root = m.bone("root", [0, 16, 0], pieces)
    m.anim("slash", .38, {
        "root": [(0, [0, 0, -25]), (.09, [0, 0, -25]), (.26, [0, 0, 43]), (.38, [0, 0, 52])],
    })
    m.write(out, root)


if __name__ == "__main__":
    build()
