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
    "armor": ("161c22", "293139", "505d65"),
    "edge": ("252e35", "4d5c62", "81908f"),
    "cloth": ("0d1c32", "183554", "315878"),
    "void": ("090d14", "161d29", "313b49"),
    "ash": ("343a42", "5d6871", "98a4a9"),
    "ember": ("9d342f", "e56948", "ffc383"),
    "eye": ("0a101a", "121d29", "2d4857"),
    "mail": ("141b21", "303940", "59656b"),
    "leather": ("16191b", "2c2c2c", "504b45"),
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
            tone = 0 if coarse % 31 == 0 else 2 if coarse % 47 == 0 else 1
            if material in ("armor", "edge", "ash"):
                # Directional bevel, a few dents and selected engravings. Do
                # not turn a large armor face into uniform visual noise.
                if y <= 1 and x % 11 != 3:
                    tone = 2
                if y >= height - 2 or x >= width - 2:
                    tone = 0
                if x <= 1 and y > 1:
                    tone = 2
                if width >= 8 and height >= 8 and y in (3, height - 4) and x in (3, width - 4):
                    tone = 2
                if motif == "engraved" and width >= 12 and height >= 12:
                    cx, cy = (width - 1) / 2, (height - 1) / 2
                    rhombus = abs(x - cx) / max(1, width * .32) + abs(y - cy) / max(1, height * .33)
                    if .85 < rhombus < 1.1:
                        tone = 2
                    elif rhombus < .42:
                        tone = 0
                    if abs(x - cx) < 1 and height * .2 < y < height * .8:
                        tone = 2
                if fine % 107 == 0:
                    tone = 0
                if fine % 139 == 0:
                    tone = 2
            elif material == "cloth":
                fold = (x // 4 + y // 17 + seed) % 11
                tone = 0 if fold in (0, 1) else 2 if fold in (5, 6) else 1
                if (x + y * 2 + seed) % 59 == 0:
                    tone = 2
                if fine % 127 == 0:
                    tone = 0
                if motif == "embroidered" and y in (height - 4, height - 2):
                    tone = 2 if x % 6 < 3 else 0
                if y == height - 1:
                    tone = 0
                if side in ("north", "south") and motif.startswith("cape_facet"):
                    inset = 1 + round((y / height) ** 1.4 * width * .23)
                    inset += authoring.noise(y // 4, 0, seed + 83) % 2
                    if x < inset or x >= width - inset:
                        return (0, 0, 0, 0)
                    if y > height - 3 and (x + seed) % 5 < 2:
                        return (0, 0, 0, 0)
                if side in ("north", "south") and height >= 12 and motif.startswith("rag"):
                    tail = y / height
                    edge_left = 1 + round(tail * width * .22) + authoring.noise(y // 6, 0, seed + 11) % 4
                    edge_right = 1 + round(tail * width * .18) + authoring.noise(y // 7, 0, seed + 17) % 4
                    tear = 2 + authoring.noise(x // 3, 0, seed + 41) % max(4, height // 4)
                    slit = tail > .58 and (x + seed) % 23 in (0, 1) and y > height * .72
                    if x < edge_left or x >= width - edge_right or y >= height - tear or slit:
                        return (0, 0, 0, 0)
            elif material == "void":
                if coarse % 9 < 6:
                    tone = 0
            elif material == "mail":
                link = (x + 3 * (y // 4 % 2)) % 8
                tone = 2 if y % 4 == 1 and link in (2, 3) else 0 if link in (0, 4, 7) else 1
            elif material == "leather":
                tone = 0 if x % 9 in (0, 1) or fine % 29 == 0 else 1
                if y <= 1:
                    tone = 2
            elif material in ("ember", "eye"):
                tone = 2 if (x + y) % 5 < 3 else 1
            return (*shades[tone], 255)

        return self.patch(width, height, paint, (width, height, material, side, motif))


def add(model, bucket, name, lo, hi, material, motif="plain"):
    model.cube(name, lo, hi, material, bucket, motif)


def add_rotated(model, bucket, name, lo, hi, material, rotation, pivot, motif="plain", face_uv=None):
    model.cube(name, lo, hi, material, bucket, motif, face_uv=face_uv)
    model.elements[-1]["rotation"] = list(rotation)
    model.elements[-1]["origin"] = list(pivot)


def build(out=ROOT / "model-lab" / "models"):
    out.mkdir(parents=True, exist_ok=True)
    m = KnightModel("ashen_knight", size=1024, texels=4)

    hips, chest, helm = [], [], []
    left_thigh, right_thigh, left_shin, right_shin = [], [], [], []
    left_arm, right_arm, blade = [], [], []
    cape_left, cape_right, cape_left_edge, cape_right_edge = [], [], [], []
    cape_left_mid, cape_left_tail, cape_center_mid, cape_center_tail = [], [], [], []
    scarf, plume, cape_center = [], [], []

    # Narrow waist, uneven shoulders and a hunched profile keep the outline
    # readable at Minecraft viewing distances. The front faces negative Z.
    add(m, hips, "fauld_core", [-3.1, 10.5, -1.7], [3.1, 14, 1.9], "mail")
    add(m, hips, "belt", [-3.5, 12.2, -2.05], [3.5, 13, -1.52], "leather")
    add(m, hips, "mail_skirt", [-3.4, 8.6, -1.5], [3.4, 12.4, 2.35], "mail")
    add(m, hips, "broken_tasset_left", [-3.8, 9.3, -2.1], [-1.65, 12.1, -.95], "armor")
    add(m, hips, "broken_tasset_right", [1.85, 10.3, -2.0], [3.4, 12.2, -.9], "armor")
    add(m, hips, "belt_buckle", [-.65, 11.9, -2.3], [.45, 12.9, -1.94], "edge")

    def paint_torn_tabard(px, py):
        left = 5 + py // 10
        right = 30 - py // 7
        if px < left or px > right or (py > 56 and (px + py * 3) % 17 < 3):
            return (0, 0, 0, 0)
        if py > 72 - authoring.noise(px // 3, 0, 1007) % 16:
            return (0, 0, 0, 0)
        fold = (px + py // 3) % 13
        if fold < 3:
            return (12, 27, 45, 255)
        if fold > 10:
            return (43, 74, 98, 255)
        return (23, 49, 76, 255)

    tabard_uv = m.patch(36, 80, paint_torn_tabard, "torn_tabard")
    m.cube("front_torn_tabard", [-1.75, 4.7, -2.38], [1.75, 11.5, -2.31],
           "cloth", hips, face_uv={"north": tabard_uv, "south": tabard_uv})
    add(m, chest, "upper_mail_tunic", [-3.7, 17.1, -2.15], [3.7, 22, 2.1], "mail")
    add(m, chest, "waist_mail_tunic", [-2.9, 13, -1.85], [2.9, 18.3, 1.85], "mail")
    add(m, chest, "upper_damaged_cuirass", [-3.1, 17.0, -2.7], [2.4, 21.3, -1.85], "armor")
    add(m, chest, "lower_damaged_cuirass", [-2.5, 14.2, -2.48], [1.8, 17.5, -1.78], "armor")
    add(m, chest, "fractured_breastplate", [-2.9, 17.6, -2.91], [-.4, 20.7, -2.63], "edge", "engraved")
    add(m, chest, "high_collar", [-2.8, 21.4, -1.65], [2.8, 23.2, 2.2], "void")
    add(m, chest, "mail_under_left", [-4.25, 15.8, -1.8], [-3.55, 20.8, 1.5], "mail")
    add(m, chest, "mail_under_right", [3.5, 15.4, -1.7], [4.25, 20.5, 1.4], "mail")
    add(m, chest, "worn_left_shoulder_core", [-5.85, 20.1, -1.95], [-3.35, 22.9, 1.9], "armor")
    add(m, chest, "right_shoulder_mail", [3.0, 20.0, -1.75], [5.5, 22.5, 1.9], "mail")
    add(m, chest, "right_shoulder_scrap", [3.5, 21.2, -2.15], [5.55, 22.8, 1.5], "armor")
    def paint_worn_pauldron(px, py):
        top = 3 + abs(px - 11) // 5
        bottom = 31 + (px // 7) % 5
        if py < top or py > bottom or (px > 32 and py < 13):
            return (0, 0, 0, 0)
        if px < 4 and py < 14 and (px + py) % 3:
            return (0, 0, 0, 0)
        edge = py <= top + 2 or py >= bottom - 2 or px in (3, 4, 35, 36)
        if edge:
            return (122, 136, 138, 255)
        curl = abs((px - 17) ** 2 / 130 + (py - 19) ** 2 / 90 - 1)
        if curl < .11:
            return (119, 132, 130, 255)
        if px < 9 and py > 23:
            return (22, 29, 34, 255)
        return (47, 57, 62, 255)

    pauldron_uv = m.patch(40, 40, paint_worn_pauldron, "worn_pauldron")
    m.cube("worn_left_shoulder_face", [-6.45, 19.2, -2.55], [-2.75, 24.4, -2.48],
           "armor", chest, face_uv={"north": pauldron_uv, "south": pauldron_uv})
    add(m, chest, "chest_leather_binding", [-3.3, 15.2, -2.8], [2.55, 15.8, -2.58], "leather")

    # Royal-blue wrapping interrupts the chest armor and makes the head/torso
    # one continuous shape. The shoulder scarf also extends over the back.
    add(m, scarf, "scarf_dark_under", [-3.35, 20.5, -3.02], [3.1, 23.5, 2.45], "void")

    def paint_wound_scarf(px, py):
        collar = py < 18 and 9 + py // 5 <= px <= 86 - py // 6
        broad_fold = abs(px - (22 + py * .68)) < max(10, 25 - py // 6)
        crossing_fold = py < 61 and abs(px - (76 - py * .74)) < 10
        if not (collar or broad_fold or crossing_fold):
            return (0, 0, 0, 0)
        if py > 65 and (px + py * 2) % 13 < 4:
            return (0, 0, 0, 0)
        if py > 74 - authoring.noise(px // 3, 0, 2307) % 7:
            return (0, 0, 0, 0)
        diagonal_fold = (px + py * 2 // 3) % 19
        tone = 0 if diagonal_fold < 4 else 2 if 11 <= diagonal_fold < 15 else 1
        if py < 2 or abs(px - (22 + py * .68)) > max(7, 22 - py // 6):
            tone = 0
        if authoring.noise(px, py, 2751) % 151 == 0:
            tone = 0
        return (*(round(channel * .78) for channel in authoring.MATERIALS["cloth"][tone]), 255)

    scarf_uv = m.patch(96, 80, paint_wound_scarf, "wound_scarf")
    m.cube("wound_scarf_front", [-4.85, 16.5, -3.6], [4.85, 24.0, -3.52],
           "cloth", scarf, face_uv={"north": scarf_uv, "south": scarf_uv})
    add(m, scarf, "scarf_left_drape", [-5.1, 19.5, -1.8], [-3.45, 22.2, 2.25], "cloth")
    add(m, scarf, "scarf_right_drape", [2.7, 20.1, -1.8], [4.5, 22.5, 2.2], "cloth")
    add(m, scarf, "scarf_back_left", [-4.8, 19.3, 2.25], [.85, 22.6, 3.18], "cloth", "ragged_scarf")
    add(m, scarf, "scarf_back_right_end", [3.2, 20.35, 2.25], [4.9, 22.25, 3.18], "cloth", "ragged_scarf")
    add(m, scarf, "scarf_hanging_point", [-3.55, 14.2, -3.1], [-1.7, 18.1, -2.78], "cloth", "ragged_scarf")

    add(m, helm, "hood_crown", [-1.85, 25.4, -2.1], [1.85, 27.5, 2.3], "void")
    add_rotated(m, helm, "hood_left_temple", [-2.7, 23.9, -2.3], [-.85, 26.6, 2.3],
                "void", [0, 0, -12], [-1.65, 25.2, 0])
    add_rotated(m, helm, "hood_right_temple", [.8, 24.1, -2.2], [2.55, 26.3, 2.25],
                "void", [0, 0, 9], [1.65, 25.1, 0])
    add(m, helm, "hood_lower", [-2.25, 22.0, -1.15], [2.25, 25.1, 2.8], "void")
    add(m, helm, "hood_muzzle_base", [-1.6, 22.6, -3.8], [1.6, 24.0, -1.5], "armor")
    add_rotated(m, helm, "snout_bridge", [-.75, 22.5, -5.25], [.75, 24.65, -3.55],
                "armor", [16, 0, 0], [0, 23.5, -4.0])
    add(m, helm, "snout_dark_tip", [-.6, 22.15, -5.55], [.6, 22.8, -4.8], "void")
    add(m, helm, "crest_base", [-.85, 26.9, -.4], [.85, 28.1, 1.4], "armor")
    add(m, helm, "left_cheek_armor", [-2.65, 22.7, -3.0], [-1.75, 25.0, -.9], "armor")
    add(m, helm, "right_broken_cheek", [2.1, 23.25, -2.8], [2.8, 24.35, -.9], "armor")

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
        if px > center and py > 14:
            ear = False
        if distance > width and not ear:
            return (0, 0, 0, 0)
        if px < center - 11 and 35 < py < 43 and (px + py) % 4 != 0:
            return (0, 0, 0, 0)
        if 28 <= py <= 32 and 4 <= distance <= 13:
            return (8, 15, 23, 255)
        if 20 <= py < 36 and px > 28 and (px + py * 2) % 7 < 2:
            return (0, 0, 0, 0)
        ridge = center + (1 if py > 34 else 0)
        if abs(px - ridge) <= 1 and 12 <= py < 53 and py not in (31, 32):
            return (64, 75, 78, 255)
        if abs(distance - width) <= 1 or (py < 18 and distance <= 3):
            return (70, 81, 83, 255)
        if py in (20, 21, 39, 40) and distance < width - 2:
            return (44, 62, 69, 255)
        if 35 < py < 49 and distance in (4, 5):
            return (32, 46, 55, 255)
        if (px * 3 + py * 5) % 31 == 0:
            return (98, 111, 111, 255)
        return (45, 56, 62, 255)

    faceplate_uv = m.patch(48, 64, paint_faceplate, "ashen_faceplate")
    m.cube("engraved_wolf_visor", [-2.65, 21.85, -4.78], [2.65, 27.8, -4.7],
           "edge", helm, face_uv={"north": faceplate_uv, "south": faceplate_uv})
    # A painted hair-like plume curves back from the crown. The cutout is
    # visible from both sides and avoids a row of hard rectangular spikes.
    add(m, plume, "crest_root", [-1.3, 28, .55], [1.25, 29, 3.2], "void")

    def paint_plume(px, py):
        sweep = px / 95
        centerline = 10 + 24 * sweep ** 1.4
        half_width = 8 - 5 * sweep
        if abs(py - centerline) > half_width:
            return (0, 0, 0, 0)
        if px > 58 and (px * 3 + py) % 17 in (0, 1):
            return (0, 0, 0, 0)
        streak = (py - centerline + px // 13) % 9
        tone = 2 if streak < 2 else 0 if streak > 6 else 1
        return (*authoring.MATERIALS["void"][tone], 255)

    plume_uv = m.patch(96, 48, paint_plume, "worn_plume")
    m.cube("worn_plume_sides", [-.68, 24.7, 1.2], [.68, 30.5, 9.5],
           "void", plume, face_uv={"east": plume_uv, "west": plume_uv})

    for side, x in (("left", -2.1), ("right", 2.1)):
        thigh = left_thigh if side == "left" else right_thigh
        shin = left_shin if side == "left" else right_shin
        add(m, thigh, f"{side}_thigh_mail", [x - 1.63, 6.6, -1.55], [x + 1.63, 11.2, 1.55], "mail")
        add(m, thigh, f"{side}_cloth_undertunic", [x - 1.58, 8.0, -1.71], [x + 1.58, 10.7, 1.6], "void")
        add(m, shin, f"{side}_dark_greave", [x - 1.48, 1.7, -1.6], [x + 1.48, 7.1, 1.45], "armor")
        add(m, shin, f"{side}_worn_boot", [x - 1.38, -.15, -2.6], [x + 1.38, 2.2, 1.58], "leather")
        add(m, shin, f"{side}_toe_cap", [x - 1.15, .1, -3.25], [x + 1.1, 1.0, -2.12], "armor")
        if side == "left":
            add(m, thigh, "left_broken_knee_plate", [x - 1.4, 6.0, -1.95], [x + .8, 7.4, -.98], "armor")
            add(m, shin, "left_greave_rim", [x - 1.48, 2.35, -1.72], [x - 1.1, 6.7, -1.39], "ash")
        else:
            add(m, thigh, "right_knee_cloth", [x - 1.45, 6.2, -1.9], [x + 1.15, 7.65, -.95], "void")
            add(m, shin, "right_greave_chip", [x + .72, 3.2, -1.65], [x + 1.22, 5.1, -1.35], "edge")

    def paint_battered_thigh(px, py):
        left = 3 + py // 15
        right = 26 - py // 12
        if py < 3 or py > 47 or px < left or px > right:
            return (0, 0, 0, 0)
        if py > 35 and (px + py * 3) % 13 < 3:
            return (0, 0, 0, 0)
        if px - left < 2 or right - px < 2 or py < 6:
            return (92, 103, 104, 255)
        if (px + py * 2) % 37 == 0:
            return (65, 75, 78, 255)
        return (39, 48, 54, 255)

    thigh_uv = m.patch(32, 52, paint_battered_thigh, "battered_thigh")
    m.cube("left_outer_thigh_plate", [-3.85, 6.55, -1.9], [-.65, 11.7, -1.83],
           "armor", left_thigh, face_uv={"north": thigh_uv, "south": thigh_uv})
    add(m, right_thigh, "right_thigh_leather_wear", [2.9, 8.1, -1.87], [3.45, 10.9, -1.65], "leather")

    add(m, left_arm, "bound_upper_arm", [-6, 15.8, -.8], [-3.7, 21, 1.3], "mail")
    add(m, left_arm, "bound_forearm", [-6.3, 10, -1], [-4, 16.4, 1.2], "mail")
    add(m, left_arm, "left_hand", [-6.1, 8.8, -1.2], [-4.2, 11, 1], "leather")
    add(m, left_arm, "left_arm_tear", [-6.5, 11.2, 1.1], [-4.9, 16.2, 1.8], "void")
    add(m, left_arm, "left_mail_shoulder", [-6.15, 17.2, -1], [-3.55, 20.2, 1.5], "mail")
    add(m, left_arm, "left_wrapping_low", [-6.45, 13.15, -1.4], [-3.95, 13.75, 1.25], "cloth")
    add(m, left_arm, "left_gauntlet_fragment", [-6.25, 8.85, -1.52], [-4.4, 10.1, -.96], "armor")
    add(m, right_arm, "right_upper_arm", [3.7, 15.8, -.8], [6, 21, 1.5], "mail")
    add(m, right_arm, "right_bracer", [3.8, 11.1, -1.1], [6.2, 16.5, 1.5], "armor")
    add(m, right_arm, "right_hand", [4, 9.7, -1.2], [6, 12.2, 1.2], "leather")
    add(m, right_arm, "right_knuckles", [4, 9.5, -1.5], [6, 10.5, -1.15], "edge")
    add(m, right_arm, "right_mail_shoulder", [3.5, 17.3, -1], [6.05, 20, 1.55], "mail")
    add(m, right_arm, "right_bracer_chip", [3.65, 12.2, -1.38], [5.45, 13.1, -.98], "edge")

    # At rest the heavy blade hangs beside the right leg.
    add(m, blade, "pommel", [4.1, 11.8, -1], [5.9, 13.7, 1], "ash")
    add(m, blade, "grip", [4.45, 9, -.55], [5.55, 12.4, .55], "void")
    add(m, blade, "guard", [1.7, 8.6, -.95], [8.3, 9.4, .95], "armor")
    add(m, blade, "guard_left_tooth", [1.35, 8.2, -1.1], [2.65, 10.6, 1.1], "armor")
    add(m, blade, "guard_right_tooth", [7.45, 8.2, -1.1], [8.55, 9.8, 1.1], "armor")
    add(m, blade, "blade_dark_spine", [4.15, -.1, -.5], [5.85, 8.6, .5], "armor")

    def paint_worn_blade(px, py):
        center = 23.5
        narrowing = max(0, py - 128) * .55
        width = max(1, 20 - narrowing)
        distance = abs(px - center)
        nick_left = 87 < py < 103 and px < 11
        nick_right = 48 < py < 59 and px > 36
        if distance > width or nick_left or nick_right:
            return (0, 0, 0, 0)
        if distance > width - 2.3:
            return (93, 111, 114, 255)
        if distance < 2 and py < 130:
            return (27, 37, 43, 255)
        if 5 < distance < 13 and abs((py // 9) % 8 - (px // 6) % 8) <= 1:
            return (30, 39, 45, 255)
        if 42 < py < 108 and abs(px - center - 5 * math.sin(py / 25)) < 1.5:
            return (29, 43, 51, 255)
        grain = authoring.noise(px // 2, py // 3, 713)
        if grain % 37 == 0:
            return (75, 89, 91, 255)
        return (43, 54, 60, 255)

    blade_uv = m.patch(48, 160, paint_worn_blade, "worn_blade")
    m.cube("blade_worn_faces", [2.6, -.7, -.85], [7.4, 8.6, .85],
           "armor", blade, face_uv={"north": blade_uv, "south": blade_uv})

    def paint_cloak(px, py):
        u, v = px / 127, py / 255
        left = 2 + round(3 * v) + authoring.noise(py // 11, 0, 2719) % 3
        right = 126 - round(7 * v) - authoring.noise(py // 13, 0, 2729) % 4
        if px < left or px > right:
            return (0, 0, 0, 0)
        if v > .66 and (px + 2 * py) % 37 < 2:
            return (0, 0, 0, 0)
        if v > .84 and py > 245 - authoring.noise(px // 4, 0, 2741) % 19:
            return (0, 0, 0, 0)
        fold = math.sin(u * 20 + v * 2.7) + .34 * math.sin(u * 39 - v * 5)
        if fold > .72:
            color = (43, 69, 92)
        elif fold < -.45:
            color = (10, 25, 43)
        else:
            color = (20, 43, 66)
        if v < .12 or px - left < 3 or right - px < 3:
            color = tuple(round(channel * .75) for channel in color)
        if authoring.noise(px, py, 2777) % 167 == 0:
            color = (70, 85, 93)
        return (*color, 255)

    cloak_uv = m.patch(128, 256, paint_cloak, "ashen_cloak_continuous")

    # A curved, asymmetrical mantle: overlapping short facets follow a bowed
    # cross section. Distinct yaw angles and depth offsets make each fold
    # occupy volume instead of stacking long coplanar cloth rectangles.
    for row in range(6):
        top = 21.55 - row * 3.35
        bottom = top - 3.55
        for col in range(4):
            left = -5.65 - row * .03 + col * 2.35 + (1.45 if col >= 2 else 0)
            right = left + 3.0
            depth = 5.0 + row * .18 + (1.25 if col in (1, 2) else .12)
            bucket = (cape_left if col < 2 else cape_center) if row == 0 else (
                (cape_left_mid if col < 2 else cape_center_mid) if row < 3 else
                (cape_left_tail if col < 2 else cape_center_tail))
            yaw = (-29, -9, 9, 29)[col] + (row - 2) * (2 if col in (1, 2) else -1)
            motif = f"ragged_cape_{row}_{col}" if row == 5 else f"cape_facet_{row}_{col}"
            tx0 = cloak_uv[0] + round((5.4 - right) / 12.0 * 128)
            tx1 = cloak_uv[0] + round((5.4 - left) / 12.0 * 128)
            ty0 = cloak_uv[1] + round((21.55 - top) / 20.3 * 256)
            ty1 = cloak_uv[1] + round((21.55 - bottom) / 20.3 * 256)
            face_uv = {"north": [tx0, ty0, tx1, ty1],
                       "south": [tx0, ty0, tx1, ty1]}
            add_rotated(m, bucket, f"cape_facet_{row}_{col}",
                        [left, bottom, depth], [right, top, depth + .34],
                        "cloth", [0, yaw, 0], [(left + right) / 2, top, depth + .17], motif, face_uv)
    add(m, cape_left_edge, "left_shoulder_cloth", [-5.75, 19.5, 1.0], [-4.65, 21.5, 2.95], "cloth")
    add_rotated(m, cape_right, "right_mantle_remnant", [3.0, 17.8, 2.55], [4.75, 21.65, 3.15],
                "cloth", [0, 17, 0], [3.85, 21.65, 2.85], "ragged_mantle")
    add_rotated(m, cape_right, "right_hanging_remnant", [3.1, 9.2, 3.0], [5.05, 18.0, 3.55],
                "cloth", [0, -24, 0], [4.05, 18.0, 3.3], "ragged_right")
    add(m, cape_right_edge, "right_shoulder_cloth", [4.75, 19.5, 1.0], [5.75, 21.5, 2.95], "cloth")
    add(m, cape_center, "back_mail", [-4.05, 8.3, 2.35], [4.1, 13.2, 3.05], "mail")

    plume_bone = m.bone("plume", [0, 28, 1], plume)
    head_bone = m.bone("head", [0, 22, 0], helm + [plume_bone])
    left_arm_bone = m.bone("left_arm", [-4.8, 21, 0], left_arm)
    sword_bone = m.bone("sword", [5, 10.5, 0], blade)
    sword_bone["rotation"] = [20, 0, -30]
    right_arm_bone = m.bone("right_arm", [4.8, 21, 0], right_arm + [sword_bone])
    left_tail_bone = m.bone("cape_left_tail", [-3.6, 10.3, 3.6], cape_left_tail)
    left_mid_bone = m.bone("cape_left_mid", [-3.1, 17.2, 3.2], cape_left_mid + [left_tail_bone])
    left_cape_bone = m.bone("cape_left", [-2.3, 21, 2.8], cape_left + [left_mid_bone])
    right_cape_bone = m.bone("cape_right", [2.2, 21, 2.8], cape_right)
    center_tail_bone = m.bone("cape_center_tail", [-.8, 9.0, 4.1], cape_center_tail)
    center_mid_bone = m.bone("cape_center_mid", [-.5, 16, 3.9], cape_center_mid + [center_tail_bone])
    middle_cape_bone = m.bone("cape_center", [0, 20, 3], cape_center + [center_mid_bone])
    left_cape_bone["rotation"] = [18, 0, 2]
    left_mid_bone["rotation"] = [-5, -6, 0]
    left_tail_bone["rotation"] = [-6, 8, 0]
    right_cape_bone["rotation"] = [18, 0, 3]
    middle_cape_bone["rotation"] = [20, 0, 0]
    center_mid_bone["rotation"] = [-4, 7, 1]
    center_tail_bone["rotation"] = [-6, -5, 0]
    left_edge_bone = m.bone("cape_left_edge", [-6, 20, 2.7], cape_left_edge)
    right_edge_bone = m.bone("cape_right_edge", [6, 20, 2.7], cape_right_edge)
    torso_bone = m.bone("torso", [0, 13, 0], chest + scarf + [head_bone, left_arm_bone, right_arm_bone,
        left_cape_bone, right_cape_bone, middle_cape_bone, left_edge_bone, right_edge_bone])
    left_knee_bone = m.bone("left_knee", [-2.1, 6.65, 0], left_shin)
    right_knee_bone = m.bone("right_knee", [2.1, 6.65, 0], right_shin)
    left_leg_bone = m.bone("left_leg", [-2.1, 10.7, 0], left_thigh + [left_knee_bone])
    right_leg_bone = m.bone("right_leg", [2.1, 10.7, 0], right_thigh + [right_knee_bone])
    torso_bone["rotation"] = [-12, 0, -5]
    right_arm_bone["rotation"] = [0, 0, 30]
    left_leg_bone["rotation"] = [-16, 0, -18]
    right_leg_bone["rotation"] = [19, 0, 18]
    left_knee_bone["rotation"] = [25, 0, 0]
    right_knee_bone["rotation"] = [-22, 0, 0]
    plume_bone["rotation"] = [0, -9, 0]
    root = m.bone("root", [0, 0, 0], hips + [torso_bone, left_leg_bone, right_leg_bone])

    m.anim("idle", 2.0, {
        "root": [(0, [0, 0, 0], "position"), (1, [0, .28, 0], "position"), (2, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (1, [18, 0, -5]), (2, [16, 0, -6])],
        "head": [(0, [-9, -5, 0]), (1, [-11, -2, 0]), (2, [-9, -5, 0])],
        "plume": [(0, [0, 0, -3]), (1, [2, 0, 5]), (2, [0, 0, -3])],
        "right_arm": [(0, [4, 0, 4]), (1, [0, 0, 6]), (2, [4, 0, 4])],
        "cape_left": [(0, [0, 0, -5]), (1, [-6, 0, -10]), (2, [0, 0, -5])],
        "cape_right": [(0, [0, 0, 4]), (1, [-4, 0, 8]), (2, [0, 0, 4])],
        "cape_center": [(0, [-2, 0, -2]), (1, [-7, 0, 3]), (2, [-2, 0, -2])],
        "cape_left_edge": [(0, [-4, 0, -8]), (.5, [-14, 0, -17]), (1.2, [-5, 0, -8]), (2, [-4, 0, -8])],
        "cape_right_edge": [(0, [-3, 0, 8]), (.8, [-11, 0, 15]), (1.5, [-4, 0, 7]), (2, [-3, 0, 8])],
    }, loop="loop")
    m.anim("walk", .8, {
        "root": [(0, [0, 0, 0], "position"), (.2, [0, .35, 0], "position"), (.4, [0, 0, 0], "position"), (.6, [0, .35, 0], "position"), (.8, [0, 0, 0], "position")],
        "left_leg": [(0, [-24, 0, 0]), (.4, [24, 0, 0]), (.8, [-24, 0, 0])],
        "right_leg": [(0, [24, 0, 0]), (.4, [-24, 0, 0]), (.8, [24, 0, 0])],
        "left_knee": [(0, [16, 0, 0]), (.4, [-4, 0, 0]), (.8, [16, 0, 0])],
        "right_knee": [(0, [-4, 0, 0]), (.4, [16, 0, 0]), (.8, [-4, 0, 0])],
        "torso": [(0, [11, -4, -3]), (.4, [11, 4, -3]), (.8, [11, -4, -3])],
        "left_arm": [(0, [-3, 0, -3]), (.4, [5, 0, 2]), (.8, [-3, 0, -3])],
        "right_arm": [(0, [1, 0, 3]), (.4, [-5, 0, -2]), (.8, [1, 0, 3])],
        "cape_left": [(0, [-13, 0, 0]), (.4, [-6, 0, -8]), (.8, [-13, 0, 0])],
        "cape_right": [(0, [-7, 0, 7]), (.4, [-14, 0, 0]), (.8, [-7, 0, 7])],
        "cape_center": [(0, [-10, 0, 0]), (.4, [-5, 0, 3]), (.8, [-10, 0, 0])],
    }, loop="loop")
    m.anim("run", .6, {
        "root": [(0, [0, .15, 0], "position"), (.15, [0, .55, 0], "position"), (.3, [0, .15, 0], "position"), (.45, [0, .55, 0], "position"), (.6, [0, .15, 0], "position")],
        "torso": [(0, [30, 0, -4]), (.3, [34, 0, 0]), (.6, [30, 0, -4])],
        "left_leg": [(0, [-35, 0, 0]), (.3, [35, 0, 0]), (.6, [-35, 0, 0])],
        "right_leg": [(0, [35, 0, 0]), (.3, [-35, 0, 0]), (.6, [35, 0, 0])],
        "left_knee": [(0, [28, 0, 0]), (.3, [-12, 0, 0]), (.6, [28, 0, 0])],
        "right_knee": [(0, [-12, 0, 0]), (.3, [28, 0, 0]), (.6, [-12, 0, 0])],
        "left_arm": [(0, [-10, 0, -8]), (.3, [7, 0, 2]), (.6, [-10, 0, -8])],
        "right_arm": [(0, [-6, 0, 5]), (.3, [8, 0, -2]), (.6, [-6, 0, 5])],
        "cape_left": [(0, [-25, 0, -9]), (.3, [-40, 0, -14]), (.6, [-25, 0, -9])],
        "cape_right": [(0, [-35, 0, 12]), (.3, [-22, 0, 6]), (.6, [-35, 0, 12])],
        "cape_center": [(0, [-29, 0, 0]), (.3, [-39, 0, 7]), (.6, [-29, 0, 0])],
        "plume": [(0, [-12, 0, -5]), (.3, [-20, 0, 8]), (.6, [-12, 0, -5])],
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
        "right_arm": [(0, [4, 0, 4]), (.38, [-85, 0, -25]), (.72, [-24, 0, 0]), (.92, [18, 0, 7]), (1.25, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.38, [0, 0, -95]), (.72, [0, 0, 25]), (.92, [0, 0, 10]), (1.25, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.72, [-22, 0, -19]), (1.25, [0, 0, -5])],
        "cape_center": [(0, [-4, 0, 0]), (.72, [-29, 0, 11]), (1.25, [-4, 0, 0])],
    })
    m.anim("cleave_reverse", 1.05, {
        "root": [(0, [0, 0, 0], "position"), (.5, [0, .45, -.6], "position"), (1.05, [0, 0, 0], "position")],
        "torso": [(0, [18, 40, 9]), (.22, [20, 48, 10]), (.5, [13, -40, -12]), (1.05, [16, 0, -6])],
        "right_arm": [(0, [22, 0, 90]), (.22, [28, 0, 100]), (.5, [-40, 0, -65]), (.78, [-25, 0, -80]), (1.05, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.22, [0, 0, 105]), (.5, [0, 0, -50]), (1.05, [0, 0, 0])],
        "cape_right": [(0, [-8, 0, 8]), (.5, [-30, 0, 22]), (1.05, [0, 0, 4])],
    })
    m.anim("thrust", 1.0, {
        "root": [(0, [0, 0, 0], "position"), (.32, [0, -.4, 0], "position"), (.55, [0, .1, -2], "position"), (1, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (.32, [7, 18, -7]), (.55, [27, -12, 3]), (1, [16, 0, -6])],
        "right_arm": [(0, [4, 0, 4]), (.32, [-75, 0, -28]), (.55, [-96, 0, 3]), (.8, [-90, 0, 2]), (1, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.32, [0, 0, -105]), (.55, [0, 0, -120]), (1, [0, 0, 0])],
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
        "left_knee": [(0, [0, 0, 0]), (.55, [38, 0, 0]), (1.1, [0, 0, 0])],
        "right_knee": [(0, [0, 0, 0]), (.55, [-32, 0, 0]), (1.1, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.55, [30, 0, -20]), (1.1, [0, 0, -5])],
    })
    m.anim("slam", 1.35, {
        "root": [(0, [0, 0, 0], "position"), (.72, [0, 1.4, 0], "position"), (.95, [0, -1, -1], "position"), (1.35, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.7, [-18, 0, -4]), (.94, [43, 0, -4]), (1.35, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.7, [-30, 0, -5]), (.94, [0, 0, 0]), (1.35, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.7, [0, 0, -90]), (.94, [0, 0, 0]), (1.35, [0, 0, 0])],
        "left_arm": [(0, [0, 0, 0]), (.7, [-30, 0, -15]), (.94, [20, 0, -12]), (1.35, [0, 0, 0])],
        "left_knee": [(0, [0, 0, 0]), (.94, [22, 0, 0]), (1.35, [0, 0, 0])],
        "right_knee": [(0, [0, 0, 0]), (.94, [-18, 0, 0]), (1.35, [0, 0, 0])],
        "cape_right": [(0, [0, 0, 4]), (.94, [-32, 0, 19]), (1.35, [0, 0, 4])],
        "cape_center": [(0, [-5, 0, 0]), (.94, [-38, 0, -9]), (1.35, [-5, 0, 0])],
        "plume": [(0, [0, 0, 0]), (.7, [-16, 0, -8]), (.94, [18, 0, 12]), (1.35, [0, 0, 0])],
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
