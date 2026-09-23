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
    "armor": ("181b1f", "32383c", "61696a"),
    "edge": ("24282b", "555d61", "929a9a"),
    "cloth": ("0d1c32", "183554", "315878"),
    "void": ("090d14", "161d29", "313b49"),
    "hair": ("0b0f16", "202833", "3d4853"),
    "ash": ("333738", "646b6a", "a4adac"),
    "ember": ("9d342f", "e56948", "ffc383"),
    "eye": ("0a101a", "121d29", "2d4857"),
    "mail": ("14191c", "2e3538", "596165"),
    "leather": ("16191b", "2c2c2c", "504b45"),
    "skin": ("251c1c", "513a35", "806052"),
    "bandage": ("3b3530", "776b5d", "a79783"),
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
                if y <= 1 and authoring.noise(x // 3, 0, seed + 281) % 5 < 2:
                    tone = 2
                if y >= height - 2 or x >= width - 2:
                    tone = 0
                if x <= 1 and y > 1 and authoring.noise(0, y // 4, seed + 283) % 4 == 0:
                    tone = 2
                if motif == "engraved" and width >= 8 and height >= 8 and y in (3, height - 4) and x in (3, width - 4):
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
            elif material == "hair":
                strand = (x // 3 + y // 11 + seed) % 9
                tone = 2 if strand == 0 else 0 if strand in (5, 6) else 1
            elif material == "mail":
                link = (x + 3 * (y // 4 % 2)) % 8
                weave = authoring.noise(x // 5, y // 7, seed + 233)
                tone = (2 if y % 4 == 1 and link in (2, 3) and weave % 2 == 0
                        else 0 if link in (0, 7) and weave % 2 == 0 else 1)
            elif material == "leather":
                tone = 0 if x % 9 in (0, 1) or fine % 29 == 0 else 1
                if y <= 1:
                    tone = 2
            elif material == "skin":
                tone = 2 if coarse % 17 == 0 else 0 if coarse % 7 == 0 else 1
                if (x * 2 + y * 3 + seed) % 47 < 2:
                    tone = 0
                if side in ("north", "south") and height > 14:
                    wound = x - (width * .38 + 1.9 * math.sin(y / 5 + seed))
                    if abs(wound) < 1.2 and height * .17 < y < height * .84:
                        tone = 0
            elif material == "bandage":
                tone = 2 if (y + x // 3 + seed) % 13 < 4 else 0 if coarse % 11 == 0 else 1
                if fine % 31 == 0:
                    tone = 0
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
    add(m, hips, "mail_skirt_underlayer", [-2.75, 8.6, -1.5],
        [2.75, 12.4, 2.1], "void")
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
    # Torn hip panels partially cover the rigid thigh silhouette. Different
    # hems and angles keep them from reading as a symmetrical armored skirt.
    for panel, (x0, x1, top, hem, depth, lean) in enumerate((
            (-5.05, -2.05, 11.85, 5.55, -2.46, -13),
            (1.8, 4.55, 11.4, 6.35, -2.39, 12),
            (-3.9, -1.05, 10.45, 7.15, -2.65, 7),
    )):
        def paint_hip_rag(px, py, seed=panel):
            side = abs(px - 23.5) / 24
            left = 3 + round(py * (.075 + seed * .015))
            right = 44 - round(py * (.055 + seed * .01))
            bottom = 91 - (px // 7 % 4) * (3 + seed) - authoring.noise(px // 3, seed, 9187) % 8
            if px < left or px > right or py > bottom or py < 3 + round(side * 5):
                return (0, 0, 0, 0)
            if py > 54 and abs(px - (17 + seed * 5 + py // 9)) < 2:
                return (0, 0, 0, 0)
            fold = math.sin(px * .24 + py * .075 + seed * 1.7)
            color = (15, 30, 49) if fold < -.35 else (40, 61, 81) if fold > .72 else (25, 45, 68)
            if authoring.noise(px // 2, py // 2, 9203 + seed) % 63 == 0:
                color = (69, 77, 78)
            return (*color, 255)

        rag_uv = m.patch(48, 96, paint_hip_rag, f"front_hip_rag_{panel}")
        add_rotated(m, hips, f"front_hip_rag_{panel}",
                    [x0, hem, depth], [x1, top, depth + .14], "cloth",
                    [5 + panel * 3, 0, lean], [(x0 + x1) / 2, top, depth],
                    "ragged_hip", face_uv={"north": rag_uv, "south": rag_uv})
    add(m, chest, "upper_mail_tunic", [-3.7, 17.1, -2.15], [3.7, 22, 2.1], "void")
    add(m, chest, "waist_mail_tunic", [-2.9, 13, -1.85], [2.9, 18.3, 1.85], "void")
    def paint_chest_mail(px, py):
        side = abs(px - 63.5) / 64
        top = 4 + round(10 * side ** 1.6)
        taper = max(0, py - 32) * .19
        left = 4 + round(taper)
        right = 123 - round(taper)
        hem = 91 - authoring.noise(px // 6, 0, 7451) % 6
        if py < top or py > hem or px < left or px > right:
            return (0, 0, 0, 0)
        if py > 58 and 78 < px < 100 and (px + py * 2) % 41 < 5:
            return (0, 0, 0, 0)
        row = py // 6
        tx = (px + (row % 2) * 4) % 8
        ty = py % 6
        wear = authoring.noise(px // 4, py // 4, 7477)
        base = (20, 26, 30)
        ring = (ty == 1 and tx in (2, 3, 4)) or (ty in (2, 3) and tx in (1, 5))
        if ring and wear % 7 != 0:
            base = (53, 61, 65) if wear % 5 else (67, 73, 76)
        elif ty == 4 and tx in (2, 3, 4):
            base = (11, 17, 21)
        if abs(px - 61) < 17 and 43 < py < 66 and wear % 4 == 0:
            base = (13, 19, 23)
        return (*base, 255)

    chest_mail_uv = m.patch(128, 96, paint_chest_mail, "worn_chest_mail")
    m.cube("worn_chest_mail", [-3.63, 16.5, -2.24], [3.63, 21.9, -2.17],
           "mail", chest, face_uv={"north": chest_mail_uv})
    add_rotated(m, chest, "fractured_left_breastplate", [-3.0, 17.75, -2.78],
                [-.65, 20.1, -2.1], "armor", [0, 0, -7],
                [-1.8, 19.0, -2.5], "battered_scale")
    add(m, chest, "right_chest_scrap", [1.25, 17.3, -2.48],
        [2.55, 19.0, -1.92], "armor", "battered_scale")
    add(m, chest, "high_collar", [-2.8, 21.4, -1.65], [2.8, 23.2, 2.2], "void")
    add(m, chest, "mail_under_left", [-4.25, 15.8, -1.8], [-3.55, 20.8, 1.5], "mail")
    add(m, chest, "mail_under_right", [3.5, 15.4, -1.7], [4.25, 20.5, 1.4], "mail")
    add_rotated(m, chest, "left_pauldron_upper_scale", [-5.7, 22.05, -1.96],
                [-3.35, 23.35, 1.58], "armor", [0, 0, -11],
                [-4.4, 22.35, 0], "battered_scale")
    add_rotated(m, chest, "left_pauldron_middle_scale", [-6.12, 20.9, -2.12],
                [-3.35, 22.25, 1.42], "armor", [0, 0, 8],
                [-4.55, 21.55, 0], "battered_scale")
    add_rotated(m, chest, "left_pauldron_lower_scale", [-5.8, 19.75, -1.96],
                [-3.65, 21.15, 1.12], "armor", [0, 0, -8],
                [-4.5, 20.45, 0], "battered_scale")
    add(m, chest, "right_shoulder_mail", [3.0, 20.0, -1.75], [5.5, 22.5, 1.9], "mail")
    def paint_worn_pauldron(px, py):
        top = 3 + abs(px - 15) // 4
        bottom = 34 - abs(px - 18) // 5 - authoring.noise(px // 5, 0, 1901) % 4
        if py < top or py > bottom or (px > 32 and py < 13):
            return (0, 0, 0, 0)
        if px < 4 and py < 14 and (px + py) % 3:
            return (0, 0, 0, 0)
        edge = py <= top + 1 or py >= bottom - 1
        if edge and authoring.noise(px // 3, py // 3, 1907) % 4 != 0:
            return (89, 94, 84, 255)
        scar = abs(px - (12 + py * .31))
        if 11 < py < 31 and scar < 1.5:
            return (100, 103, 91, 255)
        if px < 9 and py > 23:
            return (22, 29, 34, 255)
        return (51, 57, 53, 255)

    pauldron_uv = m.patch(40, 40, paint_worn_pauldron, "worn_pauldron")
    m.cube("worn_left_shoulder_face", [-6.45, 19.2, -2.55], [-2.75, 24.4, -2.48],
           "armor", chest, face_uv={"north": pauldron_uv, "south": pauldron_uv})
    add_rotated(m, chest, "diagonal_chest_binding", [-3.1, 15.05, -2.85],
                [2.4, 15.52, -2.55], "leather", [0, 0, -20],
                [-.35, 15.3, -2.7], "scuffed_leather")

    def paint_worn_backplate(px, py):
        shoulder = min(1, max(0, (py - 4) / 14))
        half_width = 22 + round(7 * shoulder) if py < 25 else 29 - round((py - 25) * .2)
        if py > 53:
            half_width -= round((py - 53) * .5)
        left = 31 - half_width
        right = 32 + half_width
        top = 3 + round(abs(px - 31.5) * .15)
        hem = 73 - authoring.noise(px // 5, 0, 4001) % 6
        if py < top or py > hem or px < left or px > right:
            return (0, 0, 0, 0)
        if 44 < py < 55 and px > right - 5 and (px + py) % 3 != 0:
            return (0, 0, 0, 0)
        scar = abs(px - (19 + py * .46))
        if scar < 1.5 and 18 < py < 62:
            return (87, 92, 82, 255)
        if abs(px - 31.5) < 3 and py < 65:
            return (56, 64, 60, 255)
        if py - top < 2 or px - left < 2 or right - px < 2:
            return (65, 72, 65, 255)
        grain = authoring.noise(px // 3, py // 3, 4019)
        color = (34, 40, 39) if grain % 9 else (46, 52, 47)
        return (*color, 255)

    backplate_uv = m.patch(64, 80, paint_worn_backplate, "worn_backplate")
    m.cube("worn_backplate", [-2.3, 15.1, 2.24], [2.3, 22.3, 2.32],
           "armor", chest, face_uv={"south": backplate_uv})
    add_rotated(m, chest, "back_leather_binding", [-.35, 15.7, 2.48],
                [.35, 21.4, 2.74], "leather", [0, 0, 27],
                [0, 18.6, 2.6], "scuffed_leather")

    # Royal-blue wrapping interrupts the chest armor and makes the head/torso
    # one continuous shape. The shoulder scarf also extends over the back.
    add(m, scarf, "scarf_dark_under", [-3.35, 20.5, -3.02], [3.1, 23.5, 2.45], "void")

    def paint_cowl_under(px, py):
        side = abs(px - 47.5) / 48
        top = 3 + round(10 * side ** 1.5)
        hem = 73 - round(19 * side) - authoring.noise(px // 5, 0, 3329) % 5
        if py < top or py > hem:
            return (0, 0, 0, 0)
        if py > 54 and (px + py * 2) % 37 < 3:
            return (0, 0, 0, 0)
        fold = math.sin(px * .071 + py * .11)
        color = (13, 30, 53) if fold < -.4 else (21, 42, 69)
        return (*color, 255)

    cowl_under_uv = m.patch(96, 80, paint_cowl_under, "cowl_under")
    m.cube("cowl_under_sheet", [-4.45, 19.65, -3.18], [4.25, 23.65, -3.1],
           "cloth", scarf, face_uv={"north": cowl_under_uv, "south": cowl_under_uv})

    # Uneven, broad fabric folds wrap the neck. Repeated narrow ridges looked
    # like a mechanical grille at the scale used in the boss fight.
    cowl_bands = (
        (-3.15, 2.95, 23.60, -3.18, 1.20),
        (-3.75, 3.50, 22.45, -3.55, 1.35),
        (-4.22, 4.04, 21.10, -3.88, 1.44),
        (-3.65, 3.54, 19.82, -4.02, 1.18),
    )
    for layer, (x0, x1, top_y, z, thickness) in enumerate(cowl_bands):
        def paint_cowl(px, py, seed=layer):
            side = abs(px - 47.5) / 48
            top = 1 + round(5 * side ** 1.4)
            hem = 30 - round(4 * side) - authoring.noise(px // 6, seed, 3341) % 3
            if py < top or py > hem:
                return (0, 0, 0, 0)
            if seed == 3 and py > 20 and (px + py * 2) % 23 < 2:
                return (0, 0, 0, 0)
            fold = math.sin(px * .043 + seed * 1.1)
            color = ((15, 29, 47) if fold < -.48 else
                     (43, 67, 91) if fold > .62 else (26, 47, 70))
            if py - top < 3 or hem - py < 4:
                color = (12, 27, 44)
            elif py > top + (hem - top) * .36 and py < hem - 5 and fold > .2:
                color = (38, 62, 86)
            if authoring.noise(px // 3, py // 3, 3367 + seed) % 137 == 0:
                color = (69, 78, 85)
            return (*color, 255)

        band_uv = m.patch(96, 32, paint_cowl, f"cowl_band_{layer}")
        for facet in range(5):
            u = (facet + .5) / 5
            xlo = x0 + (x1 - x0) * facet / 5
            xhi = x0 + (x1 - x0) * (facet + 1) / 5
            side = abs(u - .5) * 2
            center_y = (top_y - thickness / 2 + 2.15 * side ** 1.45
                        + .16 * math.sin(u * math.tau * 1.4 + layer))
            center_z = z + .67 * side + .18 * math.cos(u * math.tau)
            uvlo = band_uv[0] + round(facet * 96 / 5)
            uvhi = band_uv[0] + round((facet + 1) * 96 / 5)
            face_uv = {"north": [uvlo, band_uv[1], uvhi, band_uv[3]],
                       "south": [uvlo, band_uv[1], uvhi, band_uv[3]]}
            add_rotated(m, scarf, f"layered_cowl_{layer}_{facet}",
                        [xlo - .09, center_y - thickness / 2, center_z],
                        [xhi + .09, center_y + thickness / 2, center_z + .32],
                        "cloth", [7 - layer, (facet - 2) * 9,
                                  (facet - 2) * 12],
                        [(xlo + xhi) / 2, center_y, center_z + .16],
                        "worn_cowl", face_uv)
    add(m, scarf, "scarf_left_drape", [-5.1, 19.5, -1.8], [-3.45, 22.2, 2.25], "cloth")
    add(m, scarf, "scarf_right_drape", [2.7, 20.1, -1.8], [4.5, 22.5, 2.2], "cloth")
    add(m, scarf, "scarf_back_left", [-4.8, 19.3, 2.25], [.85, 22.6, 3.18], "cloth", "ragged_scarf")
    add(m, scarf, "scarf_back_right_end", [3.2, 20.35, 2.25], [4.9, 22.25, 3.18], "cloth", "ragged_scarf")
    add(m, scarf, "scarf_hanging_point", [-3.55, 14.2, -3.1], [-1.7, 18.1, -2.78], "cloth", "ragged_scarf")

    add(m, helm, "hood_crown", [-1.38, 25.25, -2.1], [1.38, 27.15, 2.3], "void")
    add_rotated(m, helm, "hood_left_temple", [-2.7, 23.9, -2.3], [-.85, 26.6, 2.3],
                "void", [0, 0, -12], [-1.65, 25.2, 0])
    add_rotated(m, helm, "hood_right_temple", [.8, 24.1, -2.2], [2.55, 26.3, 2.25],
                "void", [0, 0, 9], [1.65, 25.1, 0])
    add(m, helm, "hood_lower", [-2.25, 22.0, -1.15], [2.25, 25.1, 2.8], "void")
    add(m, helm, "hood_muzzle_base", [-1.6, 22.6, -3.8], [1.6, 24.0, -1.5], "armor")
    add_rotated(m, helm, "snout_left_ridge", [-.95, 22.6, -5.05], [-.4, 24.45, -3.65],
                "armor", [15, 0, -9], [-.65, 23.5, -4.0])
    add_rotated(m, helm, "snout_right_ridge", [.4, 22.65, -5.05], [.95, 24.4, -3.65],
                "armor", [15, 0, 9], [.65, 23.5, -4.0])
    add(m, helm, "snout_dark_tip", [-.6, 22.15, -5.55], [.6, 22.8, -4.8], "void")
    add(m, helm, "crest_base", [-.85, 26.5, -.4], [.85, 27.6, 1.4], "armor")
    add(m, helm, "left_cheek_armor", [-2.65, 22.7, -3.0], [-1.75, 25.0, -.9], "armor")
    add(m, helm, "right_broken_cheek", [2.1, 23.25, -2.8], [2.8, 24.35, -.9], "armor")

    # A cutout engraved faceplate supplies a distinct long-muzzled silhouette
    # without reproducing another game's texture or sculpt. The existing helm
    # cubes retain the depth from the side and above.
    def paint_faceplate(px, py):
        center = 24
        distance = abs(px - center)
        if py < 8:
            width = 4 + py // 2
        elif py < 20:
            width = 13 + (py - 8) // 4
        elif py < 34:
            width = 18 - max(0, py - 29) // 2
        elif py < 51:
            width = 14 - (py - 34) // 4
        else:
            width = max(1, 10 - (py - 51))
        if px > center and py > 34:
            width -= 2
        ear = 6 <= py < 21 and 16 <= distance <= 21 - abs(py - 12) // 3
        if px > center and py > 16:
            ear = False
        if distance > width and not ear:
            return (0, 0, 0, 0)
        if px < center - 10 and 38 < py < 46 and (px + py) % 4 != 0:
            return (0, 0, 0, 0)
        eye_line = 29 - distance * .27
        if 4 <= distance <= 15 and abs(py - eye_line) < 1.65:
            return (5, 10, 17, 255)
        if 4 <= distance <= 15 and abs(py - (eye_line - 2.4)) < 1:
            return (125, 132, 136, 255)
        snout_ridge = 7 - (py - 36) * .28
        if 36 <= py <= 55 and abs(distance - snout_ridge) < .9:
            return (106, 115, 120, 255)
        if 35 <= py <= 54 and distance <= 2:
            return (25, 32, 36, 255)
        if py >= 56 and distance < 4:
            return (24, 30, 32, 255)
        if 34 < py < 51 and distance in (4, 5):
            return (30, 42, 48, 255)
        if abs(distance - width) <= 1 and py % 5 != 0:
            return (112, 122, 126, 255)
        if px > center + 8 and 18 < py < 43 and (px + py * 2) % 9 < 2:
            return (0, 0, 0, 0)
        scratch = (px * 3 + py * 5) % 47
        if scratch == 0:
            return (140, 145, 143, 255)
        return (67, 75, 78, 255)

    faceplate_uv = m.patch(48, 64, paint_faceplate, "ashen_faceplate")
    m.cube("engraved_wolf_visor", [-2.65, 22.2, -5.86], [2.65, 27.35, -5.78],
           "edge", helm, face_uv={"north": faceplate_uv, "south": faceplate_uv})
    # A painted hair-like plume curves back from the crown. The cutout is
    # visible from both sides and avoids a row of hard rectangular spikes.
    add(m, plume, "crest_root", [-1.3, 27.15, .55], [1.25, 28.15, 3.2], "hair")

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
    m.cube("worn_plume_sides", [-.68, 24.5, 1.2], [.68, 28.6, 9.5],
           "hair", plume, face_uv={"east": plume_uv, "west": plume_uv})
    for strand, (x, y0, y1, z0, z1, bend) in enumerate((
            (-1.23, 25.0, 29.1, 1.1, 8.5, -3),
            (.94, 22.8, 27.0, 2.2, 11.2, 4),
    )):
        def paint_mane(px, py, layer=strand, curve=bend):
            along = px / 95
            centerline = 13 + (18 + curve) * along ** 1.35
            half_width = 10 - 5 * along
            if abs(py - centerline) > half_width:
                return (0, 0, 0, 0)
            if along > .68 and (px + 3 * py + layer * 13) % 19 < 3:
                return (0, 0, 0, 0)
            streak = (py + px // 11 + layer * 3) % 12
            color = (20, 27, 33) if streak in (0, 1, 2) else (10, 15, 23)
            if streak == 6 and px < 72:
                color = (34, 42, 48)
            return (*color, 255)

        strand_uv = m.patch(96, 48, paint_mane, f"mane_sheet_{strand}")
        m.cube(f"mane_sheet_{strand}", [x - .13, y0, z0], [x + .13, y1, z1],
               "hair", plume, face_uv={"east": strand_uv, "west": strand_uv})

    for side, x in (("left", -2.1), ("right", 2.1)):
        thigh = left_thigh if side == "left" else right_thigh
        shin = left_shin if side == "left" else right_shin
        add(m, thigh, f"{side}_thigh_mail", [x - 1.63, 6.6, -1.55], [x + 1.63, 11.2, 1.55], "mail")
        add(m, thigh, f"{side}_cloth_undertunic", [x - 1.58, 8.0, -1.71], [x + 1.58, 10.7, 1.6], "void")
        add(m, shin, f"{side}_shin_underlayer", [x - 1.24, 1.7, -1.36],
            [x + 1.24, 7.1, 1.28], "void")
        add(m, shin, f"{side}_upper_greave_side", [x - 1.44, 4.75, -1.47],
            [x + 1.44, 6.95, 1.34], "armor")
        add(m, shin, f"{side}_lower_greave_side", [x - 1.18, 2.1, -1.4],
            [x + 1.18, 4.95, 1.26], "armor")
        add(m, shin, f"{side}_worn_boot", [x - 1.38, -.15, -2.6], [x + 1.38, 2.2, 1.58], "leather")
        add(m, shin, f"{side}_toe_cap", [x - 1.15, .1, -3.25], [x + 1.1, 1.0, -2.12], "armor")
        if side == "left":
            add(m, thigh, "left_broken_knee_plate", [x - 1.4, 6.0, -1.95], [x + .8, 7.4, -.98], "armor")
            add(m, shin, "left_greave_rim", [x - 1.48, 2.35, -1.72], [x - 1.1, 6.7, -1.39], "ash")
        else:
            add(m, thigh, "right_knee_cloth", [x - 1.45, 6.2, -1.9], [x + 1.15, 7.65, -.95], "void")
            add(m, shin, "right_greave_chip", [x + .72, 3.2, -1.65], [x + 1.22, 5.1, -1.35], "edge")

    def paint_greave_face(px, py, seed):
        taper = round(py * .045)
        left = 2 + taper
        right = 29 - taper
        if seed == 1 and 9 < py < 24:
            right -= round((24 - py) * .36)
        if seed == 0 and 33 < py < 52:
            left += round((py - 33) * .3)
        hem = 61 - authoring.noise(px // 4, seed, 1083) % 6
        if px < left or px > right or py > hem:
            return (0, 0, 0, 0)
        nick = abs(px - (13 + py * .17 + seed * 5))
        if 26 < py < 50 and nick < 1.1:
            return (18, 24, 29, 255)
        if px - left < 2 or right - px < 2 or py < 2:
            return (89, 97, 100, 255)
        if (px * 7 + py * 11 + seed * 17) % 83 < 2:
            return (105, 111, 112, 255)
        return ((43, 50, 54, 255) if (px + py // 4) % 9 < 3
                else (36, 43, 48, 255))

    for side, x, seed, shin in (("left", -2.1, 0, left_shin),
                                ("right", 2.1, 1, right_shin)):
        face_uv = m.patch(32, 64, lambda px, py, s=seed: paint_greave_face(px, py, s),
                          f"battered_greave_{side}")
        m.cube(f"{side}_battered_greave_face", [x - 1.5, 1.95, -1.63],
               [x + 1.5, 7.15, -1.56], "armor", shin,
               face_uv={"north": face_uv, "south": face_uv})

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
    add_rotated(m, left_arm, "wounded_upper_forearm", [-6.18, 12.8, -1.02],
                [-4.05, 16.35, 1.12], "skin", [0, 0, 5],
                [-5.1, 14.5, 0], "scarred_skin")
    add_rotated(m, left_arm, "wounded_tapered_forearm", [-5.91, 10.0, -.93],
                [-4.28, 13.45, .98], "skin", [0, 0, -4],
                [-5.1, 11.9, 0], "scarred_skin")
    add(m, left_arm, "left_palm", [-5.85, 8.8, -1.05], [-4.35, 10.55, .91], "skin")
    for finger, (x0, x1, low, high, tilt) in enumerate((
            (-5.92, -5.56, 7.7, 9.1, -8),
            (-5.51, -5.14, 7.45, 9.0, -2),
            (-5.08, -4.73, 7.55, 9.0, 5),
            (-4.68, -4.37, 8.0, 9.1, 13),
    )):
        add_rotated(m, left_arm, f"loose_finger_{finger}",
                    [x0, low, -.82], [x1, high, .44], "skin",
                    [0, 0, tilt], [(x0 + x1) / 2, high, -.2], "scarred_skin")
    add(m, left_arm, "left_arm_tear", [-6.5, 11.2, 1.1], [-4.9, 16.2, 1.8], "void")
    add(m, left_arm, "left_mail_shoulder", [-6.15, 17.2, -1], [-3.55, 20.2, 1.5], "mail")
    add_rotated(m, left_arm, "left_wrapping_low", [-6.45, 13.0, -1.4], [-3.95, 13.85, 1.25],
                "bandage", [0, 0, 9], [-5.2, 13.4, 0], "torn_wrap")
    add(m, right_arm, "right_upper_arm", [3.7, 15.8, -.8], [6, 21, 1.5], "mail")
    add(m, right_arm, "right_bracer", [3.8, 11.1, -1.1], [6.2, 16.5, 1.5], "armor")
    add(m, right_arm, "right_hand", [4, 9.7, -1.2], [6, 12.2, 1.2], "leather")
    add(m, right_arm, "right_knuckles", [4, 9.5, -1.5], [6, 10.5, -1.15], "leather")
    add(m, right_arm, "right_mail_shoulder", [3.5, 17.3, -1], [6.05, 20, 1.55], "mail")
    add(m, right_arm, "right_bracer_chip", [3.65, 12.2, -1.38], [5.45, 13.1, -.98], "armor")

    # At rest the heavy blade hangs beside the right leg.
    add(m, blade, "pommel", [4.1, 11.8, -1], [5.9, 13.7, 1], "ash")
    add(m, blade, "grip", [4.45, 9, -.55], [5.55, 12.4, .55], "void")
    add(m, blade, "guard", [1.7, 8.6, -.95], [8.3, 9.4, .95], "armor")
    add(m, blade, "guard_left_tooth", [1.35, 8.2, -1.1], [2.65, 10.6, 1.1], "armor")
    add(m, blade, "guard_right_tooth", [7.45, 8.2, -1.1], [8.55, 9.8, 1.1], "armor")
    add(m, blade, "blade_dark_spine", [4.15, -5.1, -.5], [5.85, 8.6, .5], "armor")

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
    m.cube("blade_worn_faces", [2.6, -5.7, -.85], [7.4, 8.6, .85],
           "armor", blade, face_uv={"north": blade_uv, "south": blade_uv})

    # Leave the chainmail back exposed. The short scarf above and torn cloth
    # tied at the hips have separate silhouettes, like a battle-worn knight.
    cape_strips = (
        (-5.1, 3.9, 3.05, 1.25, -43),
        (-.4, 2.7, 4.55, 7.8, -6),
        (4.45, 3.3, 3.05, 3.65, 39),
    )

    for strip, (center, width, depth, hem, yaw) in enumerate(cape_strips):
        def paint_strip(px, py, seed=strip):
            progress = py / 191
            taper = int(progress * 8)
            left_edge = 1 + taper + authoring.noise(py // 9, seed, 817) % 4
            right_edge = 46 - taper - authoring.noise(py // 11, seed, 829) % 5
            if seed == 0 and 70 < py < 125:
                left_edge += round(11 * (1 - abs(py - 97) / 28))
            if seed == 1 and 93 < py < 153:
                right_edge -= round(12 * (1 - abs(py - 123) / 30))
            if seed == 2 and 46 < py < 107:
                right_edge -= round(8 * (1 - abs(py - 76) / 31))
            tear = 190 - (px // 7 % 5) * (2 + seed % 2) - authoring.noise(px, seed, 839) % 7
            slit = progress > .72 and (px + 3 * seed) % 41 == 0 and py > 151
            if px < left_edge or px > right_edge or py > tear or slit:
                return (0, 0, 0, 0)
            if seed in (0, 2) and 88 <= py <= 145:
                notch = max(0, 12 - abs(py - (115 + seed * 5)) * .43)
                if px > right_edge - notch:
                    return (0, 0, 0, 0)
            if seed == 1 and 116 <= py <= 168:
                notch = max(0, 9 - abs(py - 143) * .36)
                if px < left_edge + notch:
                    return (0, 0, 0, 0)
            if seed < 3 and py > 151 and abs(px - (19 + seed * 3)) < 2 + (py - 151) * .08:
                return (0, 0, 0, 0)
            ridge = 23 + 7 * math.sin(progress * 3.4 + seed * 1.35)
            distance_to_ridge = abs(px - ridge)
            grain = authoring.noise(px // 2, py // 3, seed + 2800)
            if distance_to_ridge < 2.5:
                color = (54, 77, 99)
            elif distance_to_ridge > 16:
                color = (11, 26, 44)
            else:
                color = (26, 48, 70)
            if (py + px * 2 + seed * 17) % 79 < 2 and progress > .24:
                color = (13, 30, 49)
            if px - left_edge < 2 or right_edge - px < 2 or grain % 103 == 0:
                color = (12, 26, 43)
            if grain % 173 == 0:
                color = (68, 79, 85)
            shade = (.87, .72, .92)[seed]
            color = tuple(round(channel * shade) for channel in color)
            return (*color, 255)

        uv = m.patch(48, 192, paint_strip, f"ashen_cloak_strip_{strip}")
        side = strip < 2
        buckets = ((cape_left, cape_left_mid, cape_left_tail) if side else
                   (cape_center, cape_center_mid, cape_center_tail))
        cape_top = 14.8
        boundaries = ((cape_top, max(11.2, hem)),
                      (11.3, max(7.8, hem)), (7.9, hem))
        def fold_depth(u, v):
            return (1.15 * math.sin((u * .95 + v * .22 + strip * .29) * math.tau)
                    + .65 * math.sin((u * 1.8 - v * .58 + strip * .4) * math.tau))

        for segment, (top, bottom) in enumerate(boundaries):
            if top <= bottom + .1:
                continue
            drift = (segment * (strip % 3 - 1) * .26)
            x = center + drift
            base_z = depth + (.22, 1.15, 2.35)[segment]
            segment_width = width * (1 - segment * .065)
            for row in range(2):
                row_top = top - (top - bottom) * row / 2
                row_bottom = top - (top - bottom) * (row + 1) / 2
                v = (cape_top - (row_top + row_bottom) / 2) / (cape_top - hem)
                ty0 = uv[1] + round((cape_top - row_top) / (cape_top - hem) * 192)
                ty1 = uv[1] + round((cape_top - row_bottom) / (cape_top - hem) * 192)
                for facet in range(5):
                    u = (facet + .5) / 5
                    center_x = x + (u - .5) * segment_width
                    center_y = (row_top + row_bottom) / 2
                    z = base_z + fold_depth(u, v)
                    du = (fold_depth(min(1, u + .01), v)
                          - fold_depth(max(0, u - .01), v)) / (.02 * segment_width)
                    dv = (fold_depth(u, min(1, v + .01))
                          - fold_depth(u, max(0, v - .01))) / (.02 * (cape_top - hem))
                    face_uv = {"north": [uv[0] + round(facet * 48 / 5), ty0,
                                         uv[0] + round((facet + 1) * 48 / 5), ty1],
                               "south": [uv[0] + round(facet * 48 / 5), ty0,
                                         uv[0] + round((facet + 1) * 48 / 5), ty1]}
                    add_rotated(m, buckets[segment], f"cape_strip_{strip}_{segment}_{row}_{facet}",
                                [center_x - segment_width / 10 - .06, row_bottom - .08, z - .18],
                                [center_x + segment_width / 10 + .06, row_top + .08, z + .18],
                                "cloth",
                                [(-6, 8, -3)[segment] + strip % 3 * 2
                                 + max(-12, min(12, math.degrees(math.atan(dv)))),
                                 yaw + (-4, 3, 8)[segment]
                                 - max(-32, min(32, math.degrees(math.atan(du)))), 0],
                                [center_x, center_y, z],
                                f"ragged_cape_{strip}_{segment}", face_uv)
    add(m, cape_left_edge, "left_shoulder_cloth", [-5.75, 19.5, 1.0], [-4.65, 21.5, 2.95], "cloth")
    add_rotated(m, cape_right, "right_mantle_remnant", [3.0, 17.8, 2.55], [4.75, 21.65, 3.15],
                "cloth", [0, 17, 0], [3.85, 21.65, 2.85], "ragged_mantle")
    add_rotated(m, cape_right, "right_hanging_remnant", [3.1, 9.2, 3.0], [5.05, 18.0, 3.55],
                "cloth", [0, -24, 0], [4.05, 18.0, 3.3], "ragged_right")
    add(m, cape_right_edge, "right_shoulder_cloth", [4.75, 19.5, 1.0], [5.75, 21.5, 2.95], "cloth")
    def paint_back_mail(px, py):
        # The hem follows the hips and breaks into missing links instead of
        # filling the exposed back with one rectangular grey surface.
        crown = 3 + round(abs(px - 47.5) * .12)
        taper = max(0, py - 17) * .53
        left = 5 + round(taper) + authoring.noise(py // 5, 0, 8013) % 3
        right = 90 - round(taper) - authoring.noise(py // 6, 0, 8021) % 4
        hem = 58 - authoring.noise(px // 6, 0, 8039) % 13
        if py < crown or py > hem or px < left or px > right:
            return (0, 0, 0, 0)
        if py > 39 and (px * 2 + py * 3) % 29 < 3:
            return (0, 0, 0, 0)
        offset = (py // 5 % 2) * 4
        link = (px + offset) % 9
        wear = authoring.noise(px // 5, py // 5, 8047)
        base = (28, 34, 37)
        if py % 5 == 1 and link in (2, 3) and wear % 4 == 0:
            base = (59, 67, 69)
        elif link == 0 and wear % 3 == 0:
            base = (17, 22, 26)
        return (*base, 255)

    back_mail_uv = m.patch(96, 64, paint_back_mail, "ragged_back_mail")
    m.cube("ragged_back_mail", [-3.65, 8.35, 2.35], [3.65, 13.15, 2.43],
           "mail", hips, face_uv={"south": back_mail_uv})

    # The head should read as a narrow animal mask embedded in hair, rather
    # than a full-width box above the shoulders. Keep the muzzle's depth.
    helm_ids = set(helm)
    plume_ids = set(plume)
    for element in m.elements:
        if element["uuid"] in helm_ids:
            for key in ("from", "to", "origin"):
                x, y, z = element[key]
                element[key] = [x * .82, 22 + (y - 22) * .83, z]
            if element["name"] == "engraved_wolf_visor":
                for key in ("from", "to", "origin"):
                    element[key][0] *= .86
            if element["name"] in ("hood_muzzle_base", "snout_left_ridge",
                                   "snout_right_ridge", "snout_dark_tip",
                                   "engraved_wolf_visor"):
                for key in ("from", "to", "origin"):
                    element[key][1] += 1.55
                    element[key][2] += 1.2
        elif element["uuid"] in plume_ids:
            for key in ("from", "to", "origin"):
                element[key][1] -= .7

    plume_bone = m.bone("plume", [0, 26.5, 1], plume)
    head_bone = m.bone("head", [0, 22, 0], helm + [plume_bone])
    left_arm_bone = m.bone("left_arm", [-4.8, 21, 0], left_arm)
    sword_bone = m.bone("sword", [5, 10.5, 0], blade)
    sword_bone["rotation"] = [-20, 0, -30]
    right_arm_bone = m.bone("right_arm", [4.8, 21, 0], right_arm + [sword_bone])
    left_tail_bone = m.bone("cape_left_tail", [-3.6, 7.8, 3.6], cape_left_tail)
    left_mid_bone = m.bone("cape_left_mid", [-3.1, 11.2, 3.2], cape_left_mid + [left_tail_bone])
    left_cape_bone = m.bone("cape_left", [-2.3, 14.7, 2.8], cape_left + [left_mid_bone])
    right_cape_bone = m.bone("cape_right", [2.2, 21, 2.8], cape_right)
    center_tail_bone = m.bone("cape_center_tail", [-.8, 7.8, 4.1], cape_center_tail)
    center_mid_bone = m.bone("cape_center_mid", [-.5, 11.2, 3.9], cape_center_mid + [center_tail_bone])
    middle_cape_bone = m.bone("cape_center", [0, 14.7, 3], cape_center + [center_mid_bone])
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
        "sword": [(0, [0, 0, 0]), (.4, [15, 0, 0]), (.8, [0, 0, 0])],
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
        "sword": [(0, [0, 0, 0]), (.55, [55, 0, 0]), (1.2, [55, 0, 0]), (1.6, [0, 0, 0])],
        "cape_left": [(0, [20, 0, -7]), (1.1, [-18, 0, -22]), (1.6, [0, 0, -5])],
    })
    m.anim("cleave", 1.25, {
        "root": [(0, [0, 0, 0], "position"), (.35, [0, -.55, 0], "position"), (.72, [0, .4, -1], "position"), (1.25, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.38, [9, -32, -13]), (.7, [20, 38, 7]), (.92, [22, 46, 9]), (1.25, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.38, [-85, 0, -25]), (.72, [-24, 0, 0]), (.92, [18, 0, 7]), (1.25, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.15, [40, 0, -38]), (.38, [-20, 0, -95]), (.58, [45, 0, -25]), (.72, [40, 0, 25]), (.92, [25, 0, 10]), (1.25, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.72, [-22, 0, -19]), (1.25, [0, 0, -5])],
        "cape_center": [(0, [-4, 0, 0]), (.72, [-29, 0, 11]), (1.25, [-4, 0, 0])],
    })
    m.anim("cleave_reverse", 1.05, {
        "root": [(0, [0, 0, 0], "position"), (.5, [0, .45, -.6], "position"), (1.05, [0, 0, 0], "position")],
        "torso": [(0, [18, 40, 9]), (.22, [20, 48, 10]), (.5, [13, -40, -12]), (1.05, [16, 0, -6])],
        "right_arm": [(0, [22, 0, 90]), (.22, [28, 0, 100]), (.5, [-40, 0, -65]), (.78, [-25, 0, -80]), (1.05, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.22, [0, 0, 105]), (.5, [35, 0, -50]), (.78, [25, 0, -35]), (1.05, [0, 0, 0])],
        "cape_right": [(0, [-8, 0, 8]), (.5, [-30, 0, 22]), (1.05, [0, 0, 4])],
    })
    m.anim("thrust", 1.0, {
        "root": [(0, [0, 0, 0], "position"), (.32, [0, -.4, 0], "position"), (.55, [0, .1, -2], "position"), (1, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (.32, [7, 18, -7]), (.55, [27, -12, 3]), (1, [16, 0, -6])],
        "right_arm": [(0, [4, 0, 4]), (.32, [-75, 0, -28]), (.55, [-96, 0, 3]), (.8, [-90, 0, 2]), (1, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.1, [10, 0, -33]), (.32, [0, 0, -105]), (.55, [0, 0, -120]), (.8, [40, 0, -65]), (.93, [35, 0, -25]), (1, [0, 0, 0])],
        "left_leg": [(0, [0, 0, 0]), (.55, [-33, 0, 0]), (1, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.55, [19, 0, 0]), (1, [0, 0, 0])],
    })
    m.anim("dash", .9, {
        "torso": [(0, [8, 0, -4]), (.2, [28, 0, -3]), (.65, [30, 0, 4]), (.9, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.2, [-50, 0, -12]), (.65, [-45, 0, -10]), (.9, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.2, [55, 0, 0]), (.65, [50, 0, 0]), (.9, [0, 0, 0])],
        "left_leg": [(0, [0, 0, 0]), (.25, [-35, 0, 0]), (.65, [20, 0, 0]), (.9, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.25, [22, 0, 0]), (.65, [-35, 0, 0]), (.9, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.25, [-37, 0, -10]), (.65, [-28, 0, -17]), (.9, [0, 0, -5])],
        "cape_right": [(0, [0, 0, 4]), (.25, [-34, 0, 12]), (.65, [-26, 0, 7]), (.9, [0, 0, 4])],
    })
    m.anim("leap", 1.1, {
        "root": [(0, [0, 0, 0], "position"), (.2, [0, -.7, 0], "position"), (.55, [0, 4.5, 0], "position"), (.85, [0, 3.2, 0], "position"), (1.1, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.2, [24, 0, 0]), (.55, [-15, 0, -3]), (1.1, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.55, [-135, 0, -8]), (.85, [-120, 0, -8]), (1.1, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.2, [55, 0, 0]), (.35, [55, 0, 0]), (.55, [15, 0, 0]), (.85, [35, 0, 0]), (1.0, [55, 0, 0]), (1.1, [0, 0, 0])],
        "left_leg": [(0, [0, 0, 0]), (.55, [-35, 0, 0]), (1.1, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.55, [-20, 0, 0]), (1.1, [0, 0, 0])],
        "left_knee": [(0, [0, 0, 0]), (.55, [38, 0, 0]), (1.1, [0, 0, 0])],
        "right_knee": [(0, [0, 0, 0]), (.55, [-32, 0, 0]), (1.1, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.55, [30, 0, -20]), (1.1, [0, 0, -5])],
    })
    m.anim("slam", 1.35, {
        "root": [(0, [0, 0, 0], "position"), (.72, [0, 1.4, 0], "position"), (.95, [0, -1, -1], "position"), (1.35, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.7, [-18, 0, -4]), (.94, [43, 0, -4]), (1.35, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.2, [4, 0, 4]), (.7, [-120, 0, -10]), (.84, [0, 0, 0]), (.94, [0, 0, 30]), (1.35, [4, 0, 4])],
        "sword": [(0, [0, 0, 0]), (.2, [30, 0, -26]), (.7, [-50, 0, -60]), (.84, [10, 0, -90]), (.94, [-30, 0, -20]), (1.35, [0, 0, 0])],
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
        "sword": [(0, [0, 0, 0]), (.8, [0, 0, 0]), (1.1, [25, 0, 0]), (1.35, [25, 0, 0]), (1.5, [0, 0, 0])],
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
