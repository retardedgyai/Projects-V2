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
    "guard": ("151a1d", "292f32", "4c5556"),
    "edge": ("24282b", "555d61", "929a9a"),
    "cloth": ("0d1c32", "183554", "315878"),
    "void": ("090d14", "161d29", "313b49"),
    "hair": ("0b0f16", "202833", "3d4853"),
    "ash": ("333738", "646b6a", "a4adac"),
    "ember": ("9d342f", "e56948", "ffc383"),
    "eye": ("0a101a", "121d29", "2d4857"),
    "mail": ("14191c", "2e3538", "596165"),
    "leather": ("16191b", "2c2c2c", "504b45"),
    "boot": ("10151a", "272d31", "41484a"),
    "sleeve": ("0a1016", "121920", "1c252b"),
    "skin": ("231c1d", "44302c", "66473d"),
    "bandage": ("302826", "67554d", "91786a"),
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
            if material in ("armor", "edge", "ash", "guard"):
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
                fold = (math.sin(x * .17 + y * .075 + seed * .013)
                        + .35 * math.sin(x * .08 - y * .13 + seed * .031))
                tone = 0 if fold < -.18 else 1
                if fold > 1.1 and fine % 37 == 0:
                    tone = 2
            elif material == "sleeve":
                fold = math.sin(x * .23 + y * .07 + seed)
                tone = 0 if fold < -.32 else 2 if fold > .78 and coarse % 7 == 0 else 1
                if fine % 113 == 0:
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
            elif material == "boot":
                # Uneven, worn hide: regular dark stripes resembled a machine
                # grille once projected onto the small boot cuboids.
                crease = math.sin(x * .18 + y * .11 + seed * .013)
                tone = 0 if crease < -.81 or coarse % 23 == 0 else 1
                if fine % 89 == 0 and y > 2:
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
    left_arm, right_arm, right_forearm, blade = [], [], [], []
    cape_left, cape_right, cape_left_edge, cape_right_edge = [], [], [], []
    cape_left_mid, cape_left_tail, cape_center_mid, cape_center_tail = [], [], [], []
    scarf, plume, cape_center = [], [], []

    # Narrow waist, uneven shoulders and a hunched profile keep the outline
    # readable at Minecraft viewing distances. The front faces negative Z.
    # The old full-width mail cuboid made the waist a bright horizontal box
    # when viewed from either side. Keep the cloth body dark and let narrow
    # broken mail remnants show beneath the belt and hip rags.
    add_rotated(m, hips, "fauld_cloth_core", [-2.35, 10.75, -1.36],
                [2.35, 13.95, 1.48], "void", [4, 0, -3], [0, 12.4, 0],
                "worn_tunic")
    add_rotated(m, hips, "fauld_mail_left", [-3.04, 11.45, -1.2],
                [-2.02, 13.75, 1.3], "mail", [2, 0, -7], [-2.45, 12.6, 0],
                "worn_mail")
    add_rotated(m, hips, "fauld_mail_right", [2.03, 11.1, -1.09],
                [2.95, 13.42, 1.16], "mail", [-3, 0, 6], [2.5, 12.25, 0],
                "worn_mail")
    add_rotated(m, hips, "belt_left", [-3.5, 12.35, -2.04],
                [-.08, 12.88, -1.63], "leather", [0, 0, -3],
                [-1.75, 12.62, -1.83], "scuffed_leather")
    add_rotated(m, hips, "belt_right", [-.12, 12.28, -2.04],
                [3.4, 12.82, -1.63], "leather", [0, 0, 2],
                [1.65, 12.55, -1.83], "scuffed_leather")
    add_rotated(m, hips, "skirt_underlayer_root", [-2.55, 10.25, -1.43],
                [2.55, 12.4, 1.72], "void", [3, 0, 2], [0, 11.2, 0],
                "worn_tunic")
    add_rotated(m, hips, "skirt_underlayer_left", [-2.4, 8.55, -1.31],
                [.22, 10.8, 1.56], "void", [-6, 0, -7], [-1.05, 10.1, 0],
                "worn_tunic")
    add_rotated(m, hips, "skirt_underlayer_right", [-.18, 9.05, -1.2],
                [2.23, 10.83, 1.72], "void", [7, 0, 6], [1.1, 10.0, 0],
                "worn_tunic")
    open_waist_uv = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0),
                            "open_waist_edge")
    def paint_tasset(px, py, seed):
        left = 3 + py // (14 if seed == 0 else 11)
        right = 29 - py // (18 if seed == 0 else 13)
        hem = 43 - authoring.noise(px // 4, seed, 989) % 7
        if px < left or px > right or py > hem:
            return (0, 0, 0, 0)
        if seed == 0 and py > 21 and px > right - 5 and (px + py) % 7 < 3:
            return (0, 0, 0, 0)
        if seed == 1 and py > 16 and px < left + 4:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 3, py // 3, 997 + seed)
        if px - left < 2 or right - px < 2 or py < 3:
            return (71, 78, 78, 255)
        if abs(px - (11 + py * .2 + seed * 8)) < .8 and py > 13:
            return (15, 22, 27, 255)
        color = (33, 41, 45) if grain % 6 else (46, 53, 55)
        return (*color, 255)
    for seed, (name, lo, hi) in enumerate((
            ("broken_tasset_left", [-3.8, 9.3, -2.1], [-1.65, 12.1, -1.98]),
            ("broken_tasset_right", [1.85, 10.3, -2.0], [3.4, 12.2, -1.88]))):
        tasset_uv = m.patch(32, 48,
                            lambda px, py, s=seed: paint_tasset(px, py, s),
                            f"broken_tasset_face_{seed}")
        m.cube(name, lo, hi, "armor", hips,
               face_uv={"north": tasset_uv, "south": tasset_uv,
                        "east": open_waist_uv, "west": open_waist_uv,
                        "up": open_waist_uv, "down": open_waist_uv})
    add(m, hips, "belt_buckle", [-.42, 12.26, -2.19], [.35, 12.91, -1.96], "guard")

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
           "cloth", hips, face_uv={"north": tabard_uv, "south": tabard_uv,
                                    "east": open_waist_uv, "west": open_waist_uv,
                                    "up": open_waist_uv, "down": open_waist_uv})
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
    def paint_sternum_cloth(px, py):
        # The exposed chest below the neck wrap must read as scorched cloth,
        # not the flat two-tone face of a torso cuboid.
        left = 3 + py // 15 + authoring.noise(py // 5, 0, 5153) % 2
        right = 60 - py // 18 - authoring.noise(py // 6, 0, 5167) % 3
        top = 3 + round(abs(px - 31.5) * .17)
        hem = 46 - authoring.noise(px // 4, 0, 5171) % 6
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        wear = authoring.noise(px // 3, py // 3, 5189)
        if px > 43 and 24 < py < 37 and wear % 9 < 2:
            return (0, 0, 0, 0)
        crease = abs(py - (12 + .37 * px + 2 * math.sin(px * .12)))
        if crease < 1.5 or (py + px * 2) % 29 < 2:
            color = (34, 49, 61)
        elif wear % 5 == 0:
            color = (11, 22, 32)
        else:
            color = (19, 33, 47)
        if px - left < 2 or right - px < 2 or py > hem - 2:
            color = (10, 21, 31)
        return (*color, 255)
    sternum_uv = m.patch(64, 48, paint_sternum_cloth, "burned_sternum_cloth")
    add_rotated(m, chest, "upper_tunic_sternum",
                [-2.4, 19.1, -2.05], [2.4, 22.05, 1.02], "void",
                [-8, 0, -3], [0, 20.45, -.5], "worn_tunic",
                {"north": sternum_uv})
    add_rotated(m, chest, "upper_tunic_abdomen",
                [-2.05, 16.95, -1.72], [2.05, 19.53, .95], "void",
                [-3, 0, 4], [0, 18.2, -.47], "worn_tunic")
    add_rotated(m, chest, "upper_tunic_left_rib",
                [-3.8, 17.5, -1.82], [-2.12, 21.8, .85], "void",
                [-6, 0, -8], [-3.0, 19.65, -.45], "worn_tunic")
    add_rotated(m, chest, "upper_tunic_right_rib",
                [2.12, 17.35, -1.78], [3.72, 21.65, .82], "void",
                [-6, 0, 7], [2.92, 19.5, -.45], "worn_tunic")
    def paint_back_tunic(px, py, seed):
        grain = authoring.noise(px // 3, py // 3, 5251 + seed)
        fold = math.sin(px * .11 + py * .06 + seed * 1.9)
        seam = abs(px - (18 + py * .32 + seed * 12)) < 1.4
        if seam and grain % 7 != 0:
            color = (37, 48, 56)
        elif fold > .58:
            color = (25, 39, 52)
        elif fold < -.6:
            color = (10, 21, 32)
        else:
            color = (18, 30, 42)
        if py > 34 and grain % 27 == 0:
            color = (8, 17, 27)
        return (*color, 255)
    back_shoulder_uv = m.patch(64, 48,
                               lambda px, py: paint_back_tunic(px, py, 0),
                               "burned_back_shoulders")
    back_waist_uv = m.patch(64, 48,
                            lambda px, py: paint_back_tunic(px, py, 1),
                            "burned_back_waist")
    add_rotated(m, chest, "upper_tunic_back_shoulders",
                [-3.05, 19.45, .4], [3.05, 21.8, 2.02], "void",
                [7, 0, 2], [0, 20.6, 1.2], "worn_tunic",
                {"south": back_shoulder_uv})
    add_rotated(m, chest, "upper_tunic_back_waist",
                [-2.45, 17.55, .5], [2.45, 19.85, 1.55], "void",
                [2, 0, -3], [0, 18.6, 1.15], "worn_tunic",
                {"south": back_waist_uv})
    # The lower torso narrows toward the belt. A single full-depth cuboid
    # exposed a long, ruler-straight side between the scarf and the tassets.
    add_rotated(m, chest, "waist_mail_tunic_upper",
                [-2.48, 15.8, -1.48], [2.48, 18.4, 1.45], "void",
                [-6, 0, -2], [0, 17.1, 0], "worn_tunic")
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
        # Smaller, partly buried links break the old regular keypad grid.
        row = py // 4
        tx = (px + (row % 2) * 3) % 6
        ty = py % 4
        wear = authoring.noise(px // 3, py // 3, 7477)
        missing = wear % 11 < 3 or (py > 48 and px > 79 and wear % 5 == 0)
        base = (20, 27, 31)
        ring = (ty == 0 and tx in (2, 3)) or (ty == 1 and tx in (1, 4))
        if ring and not missing:
            base = (43, 52, 56) if wear % 6 else (51, 59, 62)
        elif ty == 3 and tx in (2, 3) and not missing:
            base = (12, 19, 23)
        if abs(px - 61) < 17 and 43 < py < 66 and wear % 4 == 0:
            base = (13, 19, 23)
        return (*base, 255)

    chest_mail_uv = m.patch(128, 96, paint_chest_mail, "worn_chest_mail")
    m.cube("worn_chest_mail", [-3.63, 16.5, -2.24], [3.63, 21.9, -2.17],
           "mail", chest, face_uv={"north": chest_mail_uv})
    def paint_torn_waist_mail(px, py):
        # Chain links fade into scorched cloth rather than ending in a straight
        # horizontal line above the belt. Missing patches expose the tunic.
        u = px / 95
        edge = 5 + round(8 * abs(u - .47))
        hem = 59 - round(10 * abs(u - .34))
        hem -= authoring.noise(px // 5, 0, 8121) % 9
        if px < edge or px > 93 - edge or py < 2 or py > hem:
            return (0, 0, 0, 0)
        if px > 63 and py > 29 and abs(py - (.8 * px - 21)) < 5:
            return (0, 0, 0, 0)
        if px < 26 and py > 42 and authoring.noise(px // 4, py // 3, 8131) % 5 < 2:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 3, py // 3, 8141)
        row = py // 5
        link_x = (px + 3 * (row % 2)) % 7
        link_y = py % 5
        if ((link_y == 1 and link_x in (2, 3, 4)) or
                (link_y in (2, 3) and link_x in (1, 5))) and grain % 6 != 0:
            color = (48, 54, 55) if grain % 4 else (64, 66, 64)
        elif link_y == 4 and link_x in (2, 3, 4):
            color = (11, 17, 20)
        else:
            color = (20, 25, 27)
        if py > hem - 3 or px < edge + 2 or px > 91 - edge:
            color = (15, 20, 23)
        return (*color, 255)

    waist_mail_uv = m.patch(96, 64, paint_torn_waist_mail, "torn_waist_mail")
    m.cube("torn_waist_mail", [-2.7, 13.45, -1.94], [2.7, 17.05, -1.87],
           "mail", chest, face_uv={"north": waist_mail_uv,
                                   "south": waist_mail_uv})
    add_rotated(m, chest, "fractured_left_breastplate", [-3.0, 17.75, -2.78],
                [-.65, 20.1, -2.1], "armor", [0, 0, -7],
                [-1.8, 19.0, -2.5], "battered_scale")
    add(m, chest, "right_chest_scrap", [1.25, 17.3, -2.48],
        [2.55, 19.0, -1.92], "armor", "battered_scale")
    add(m, chest, "mail_under_left", [-4.25, 15.8, -1.8], [-3.55, 20.8, 1.5], "mail")
    add_rotated(m, chest, "right_rib_tunic_upper",
                [3.44, 17.85, -1.45], [4.12, 20.6, 1.28], "void",
                [6, 0, -5], [3.8, 19.2, 0], "worn_tunic")
    add_rotated(m, chest, "right_rib_tunic_lower",
                [3.14, 15.45, -1.26], [3.83, 18.15, 1.12], "void",
                [-4, 0, 6], [3.5, 16.8, 0], "worn_tunic")

    def paint_side_mail(px, py):
        top = 3 + round(abs(px - 29) * .13)
        hem = 87 - authoring.noise(px // 4, 0, 2411) % 13
        front = 3 + py // 15
        rear = 61 - py // 17
        if py < top or py > hem or px < front or px > rear:
            return (0, 0, 0, 0)
        if py > 56 and 16 < px < 30 and (px + py * 2) % 25 < 5:
            return (0, 0, 0, 0)
        row = py // 6
        tx = (px + 4 * (row % 2)) % 8
        grain = authoring.noise(px // 3, py // 3, 2423)
        ring = ((py % 6 == 1 and tx in (2, 3, 4)) or
                (py % 6 in (2, 3) and tx in (1, 5)))
        if ring and grain % 7 != 0:
            color = (45, 55, 58) if grain % 6 else (61, 68, 68)
        elif py % 6 == 4 and tx in (2, 3, 4):
            color = (10, 17, 22)
        else:
            color = (20, 29, 36)
        if px - front < 2 or rear - px < 2 or py > hem - 2:
            color = (12, 21, 28)
        return (*color, 255)

    side_mail_uv = m.patch(64, 96, paint_side_mail, "worn_side_mail")
    def paint_side_cloth(px, py):
        left = 3 + round(py * .13) + authoring.noise(py // 5, 0, 2451) % 3
        right = 60 - round(py * .16) - authoring.noise(py // 6, 0, 2459) % 4
        hem = 89 - authoring.noise(px // 4, 0, 2467) % 16
        slit = py > 52 and abs(px - (27 + py // 9)) < 2 + (py - 52) // 16
        if px < left or px > right or py > hem or slit:
            return (0, 0, 0, 0)
        fold = math.sin(px * .17 + py * .043) + .3 * math.sin(py * .11)
        grain = authoring.noise(px // 3, py // 4, 2477)
        if fold < -.35:
            color = (10, 23, 38)
        elif fold > .65:
            color = (29, 49, 67)
        else:
            color = (17, 34, 51)
        if px - left < 2 or right - px < 2 or py > hem - 3:
            color = (9, 20, 32)
        if grain % 67 == 0:
            color = (48, 62, 72)
        return (*color, 255)

    side_cloth_uv = m.patch(64, 96, paint_side_cloth,
                            "torn_sword_side_cloth")
    mail_edge_uv = m.patch(1, 1, lambda _x, _y: (17, 23, 27, 255),
                           "dark_mail_edge")
    for tier, (lo, hi, y0, y1, tilt, sweep) in enumerate((
            ((4.02, 18.78, -1.78), (4.35, 20.85, 1.68), 0, 34, -4, 0),
            ((4.08, 16.6, -1.67), (4.39, 19.05, 1.35), 32, 67, 7, 14),
            ((3.82, 14.22, -1.16), (4.22, 17.1, .96), 65, 96, -9, 26),
    )):
        source_uv = side_mail_uv if tier == 0 else side_cloth_uv
        segment_uv = [source_uv[0], source_uv[1] + y0,
                      source_uv[2], source_uv[1] + y1]
        add_rotated(m, chest, f"worn_sword_side_layer_{tier}", lo, hi,
                    "mail" if tier == 0 else "cloth", [tilt, sweep, 0],
                    [(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2,
                     (lo[2] + hi[2]) / 2], "torn_mail",
                    {"east": segment_uv, "west": segment_uv,
                     "north": mail_edge_uv, "south": mail_edge_uv,
                     "up": mail_edge_uv, "down": mail_edge_uv})

    add_rotated(m, chest, "left_pauldron_dark_mount", [-5.54, 19.9, -1.62],
                [-3.72, 21.38, .86], "sleeve", [0, -8, -12],
                [-4.55, 20.55, -.3], "worn_sleeve")
    add_rotated(m, chest, "left_pauldron_broken_hem", [-5.35, 19.38, -.45],
                [-4.02, 20.22, 1.02], "armor", [0, -6, -9],
                [-4.62, 19.88, .24], "battered_scale")
    def paint_worn_pauldron(px, py):
        top = 5 + abs(px - 20) // 3
        bottom = 32 - abs(px - 20) // 3 - authoring.noise(px // 5, 0, 1901) % 4
        if py < top or py > bottom or (px > 32 and py < 13):
            return (0, 0, 0, 0)
        if px < 4 and py < 14 and (px + py) % 3:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 2, py // 2, 1897)
        edge = py <= top + 1 or py >= bottom - 1
        if edge and authoring.noise(px // 3, py // 3, 1907) % 4 != 0:
            return ((78, 83, 81, 255) if grain % 4 else (94, 99, 96, 255))
        scar = abs(px - (12 + py * .31))
        if 11 < py < 31 and scar < 1.5:
            return (82, 90, 88, 255)
        fracture = abs(px - (29 - py * .37 + 2.4 * math.sin(py * .23)))
        if 14 < py < 31 and fracture < 1.2 and grain % 4 != 0:
            return (18, 25, 29, 255)
        if px < 9 and py > 23:
            return (22, 29, 34, 255)
        if grain % 31 == 0:
            return (23, 31, 34, 255)
        if grain % 19 == 0 and 10 < px < 34:
            return (63, 69, 68, 255)
        if 16 < px < 32 and 12 < py < 28 and grain % 5 == 0:
            return (51, 56, 54, 255)
        return (39, 47, 49, 255)

    pauldron_uv = m.patch(40, 40, paint_worn_pauldron, "worn_pauldron")
    m.cube("worn_left_shoulder_face", [-6.15, 19.75, -2.55], [-3.0, 23.65, -2.48],
           "armor", chest, face_uv={"north": pauldron_uv, "south": pauldron_uv})

    def paint_shoulder_lame(px, py, seed):
        left = 3 + authoring.noise(py // 4, seed, 1961) % 3
        right = 43 - authoring.noise(py // 5, seed, 1973) % 5
        top = 3 + abs(px - (22 + seed * 2)) // 10
        hem = 29 - abs(px - (18 + seed * 3)) // 9
        hem -= authoring.noise(px // 4, seed, 1987) % 5
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        if seed == 1 and px > 30 and py > 17 and (px + py) % 11 < 4:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 3, py // 3, 1993 + seed)
        gouge = abs(px - (10 + py * .42 + seed * 5)) < 1.2
        if gouge and 9 < py < hem - 2:
            color = (18, 25, 29)
        elif py <= top + 2 or py >= hem - 2:
            color = (78, 85, 83) if grain % 3 else (58, 66, 67)
        else:
            color = (42, 50, 52) if grain % 4 else (53, 61, 62)
        if grain % 47 == 0:
            color = (89, 94, 89)
        return (*color, 255)

    for lame, (lo, hi, tilt) in enumerate((
            ((-5.7, 22.1, -2.79), (-3.48, 23.36, -2.62), -5),
            ((-6.05, 20.92, -2.87), (-3.52, 22.25, -2.67), 3),
            ((-5.84, 19.84, -2.8), (-3.86, 21.18, -2.61), -7),
    )):
        lame_uv = m.patch(48, 32,
                          lambda px, py, seed=lame: paint_shoulder_lame(px, py, seed),
                          f"broken_shoulder_lame_{lame}")
        add_rotated(m, chest, f"broken_shoulder_lame_{lame}", lo, hi,
                    "armor", [0, 0, tilt],
                    [(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, lo[2]],
                    "battered_scale", {"north": lame_uv, "south": lame_uv})

    open_pauldron_edge = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0),
                                 "open_pauldron_edge")
    def paint_broken_right_pauldron(px, py):
        top = 4 + abs(px - 18) // 4
        hem = 34 - abs(px - 14) // 5 - authoring.noise(px // 4, 0, 1921) % 5
        if py < top or py > hem or px < 3 or px > 29:
            return (0, 0, 0, 0)
        if px > 21 and 17 < py < 30 and (px + py * 2) % 7 < 3:
            return (0, 0, 0, 0)
        if px < 8 and py > 23 and (px * 3 + py) % 5 < 2:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 3, py // 3, 1931)
        if py - top < 2 or px < 5:
            return ((75, 83, 84, 255) if grain % 3 else (56, 64, 67, 255))
        if 9 < py < 27 and abs(px - (13 + py * .22)) < 1:
            return (17, 24, 28, 255)
        return ((38, 46, 50, 255) if grain % 4 else (48, 55, 58, 255))

    right_pauldron_uv = m.patch(32, 40, paint_broken_right_pauldron,
                                 "broken_right_pauldron")
    m.cube("broken_right_shoulder_face", [3.72, 18.78, -1.46],
           [6.18, 21.55, -1.38], "armor", right_arm,
           face_uv={"north": right_pauldron_uv, "south": right_pauldron_uv,
                    "east": open_pauldron_edge, "west": open_pauldron_edge,
                    "up": open_pauldron_edge, "down": open_pauldron_edge})
    def paint_harness(px, py):
        edge = 1 + authoring.noise(px // 5, 0, 4081) % 2
        if py < edge or py > 15 - edge:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 3, py // 2, 4093)
        if py <= edge + 1 or py >= 14 - edge:
            color = (30, 28, 27)
        elif py in (4, 11) and px % 12 in (2, 3) and grain % 3:
            color = (80, 71, 57)
        elif grain % 29 == 0:
            color = (81, 67, 51)
        elif grain % 7 == 0:
            color = (34, 31, 29)
        else:
            color = (56, 49, 40)
        return (*color, 255)

    harness_uv = m.patch(96, 16, paint_harness, "worn_diagonal_harness")
    harness_edge_uv = m.patch(1, 1, lambda _x, _y: (34, 31, 28, 255),
                              "dark_harness_edge")
    harness_faces = {side: harness_uv if side in ("north", "south")
                     else harness_edge_uv for side in
                     ("north", "south", "east", "west", "up", "down")}
    add_rotated(m, chest, "diagonal_chest_binding",
                [-2.6, 15.59, -2.85], [2.6, 16.21, -2.55],
                "leather", [0, 0, -48], [0, 15.9, -2.7],
                "scuffed_leather", harness_faces)

    def paint_worn_backplate(px, py):
        shoulder = min(1, max(0, (py - 4) / 14))
        half_width = 22 + round(7 * shoulder) if py < 25 else 29 - round((py - 25) * .2)
        if py > 53:
            half_width -= round((py - 53) * .5)
        left = 31 - half_width + authoring.noise(py // 5, 0, 3991) % 3
        right = 32 + half_width - authoring.noise(py // 6, 0, 3997) % 4
        top = 3 + round(abs(px - 31.5) * .15)
        hem = 73 - authoring.noise(px // 5, 0, 4001) % 6
        if py < top or py > hem or px < left or px > right:
            return (0, 0, 0, 0)
        if 34 < py < 59 and px > right - max(0, 9 - abs(py - 46) // 2):
            return (0, 0, 0, 0)
        if 19 < py < 43 and px < left + max(0, 6 - abs(py - 31) // 3):
            return (0, 0, 0, 0)
        scar = abs(px - (19 + py * .46))
        if scar < 1.5 and 18 < py < 62:
            return (88, 95, 97, 255)
        if abs(px - 31.5) < 3 and py < 65:
            return (59, 67, 70, 255)
        if py - top < 2 or px - left < 2 or right - px < 2:
            return (68, 77, 81, 255)
        grain = authoring.noise(px // 3, py // 3, 4019)
        color = (35, 43, 47) if grain % 9 else (45, 53, 56)
        return (*color, 255)

    backplate_uv = m.patch(64, 80, paint_worn_backplate, "worn_backplate")
    m.cube("worn_backplate", [-2.3, 15.1, 2.24], [2.3, 22.3, 2.32],
           "armor", chest, face_uv={"south": backplate_uv})
    add_rotated(m, chest, "back_leather_binding", [-.35, 15.7, 2.48],
                [.35, 21.4, 2.74], "leather", [0, 0, 27],
                [0, 18.6, 2.6], "scuffed_leather")

    # The blue wrapping crosses the chest and continues onto the back. Keep
    # the folds exposed; a solid neck-filling box made them read as machinery.

    def paint_cowl_edge(px, py):
        # Give the thick folds their own dark side, not the transparent atlas
        # default used by a front-only decal.
        if px < 1 or px > 10 or py < 2 or py > 45 - authoring.noise(px, 0, 3317) % 4:
            return (0, 0, 0, 0)
        wear = authoring.noise(px // 2, py // 4, 3323)
        color = (10, 23, 38) if wear % 5 else (18, 35, 53)
        if wear % 71 == 0:
            color = (29, 47, 64)
        return (*color, 255)

    cowl_edge_uv = m.patch(12, 48, paint_cowl_edge, "cowl_fabric_edge")

    # The reference cowl has broad nested folds around the neck. A diagonal
    # six-facet sheet made the chest look like blue armor plates. Paint each
    # drooping fold continuously so its silhouette and shading read as cloth.
    front_cowl_folds = (
        (-3.65, 3.0, 20.5, 23.7, -3.83, 0),
        (-4.38, 2.3, 18.35, 22.15, -4.2, 2),
    )
    for left, right, low, high, depth, layer in front_cowl_folds:
        def paint_draped_fold(px, py, seed=layer):
            u = px / 95
            arc = max(0, math.sin(math.pi * u)) ** 1.2
            # Each fold has a different pull toward the wounded shoulder;
            # identical centred sags turned the wrap into stacked plating.
            pull = (0, 7, 8)[seed] * u
            top = 3 + round((12 + seed * 2) * arc + pull)
            top += authoring.noise(px // 6, seed, 3451) % 3
            hem = top + 29 - seed * 2 - authoring.noise(px // 7, seed, 3457) % 7
            if py < top or py > hem or px < 2 or px > 93:
                return (0, 0, 0, 0)
            v = (py - top) / max(1, hem - top)
            grain = authoring.noise(px // 3, py // 3, 3467 + seed) % 13
            ridge = (.34 + .09 * math.sin(px * .067 + seed * 1.3)
                     + .05 * math.sin(px * .19 + seed * 2.1))
            if v < .12 or v > .87:
                color = (11, 25, 41)
            elif abs(v - ridge) < .13 and grain > 2:
                color = (32, 51, 71) if grain > 6 else (26, 45, 64)
            elif v > .65:
                color = (16, 33, 52)
            else:
                color = (24, 43, 65)
            if grain == 0:
                color = tuple(min(255, channel + 5) for channel in color)
            return (*color, 255)

        fold_uv = m.patch(96, 48, paint_draped_fold,
                          f"front_draped_cowl_{layer}")
        m.cube(f"front_draped_cowl_{layer}", [left, low, depth - .5],
               [right, high, depth + .35], "cloth", scarf,
               face_uv={"north": fold_uv, "south": fold_uv,
                        "west": cowl_edge_uv, "east": cowl_edge_uv})
        m.elements[-1]["rotation"] = [0, 0, -11 if layer == 2 else 0]
        m.elements[-1]["origin"] = [(left + right) / 2,
                                     (low + high) / 2, depth]

    def paint_side_wrap(px, py):
        u = px / 95
        top = 3 + round(12 * max(0, math.sin(math.pi * u)) ** 1.15)
        top += authoring.noise(px // 7, 0, 3491) % 3
        hem = top + 25 - authoring.noise(px // 5, 0, 3497) % 5
        if px < 2 or px > 93 or py < top or py > hem:
            return (0, 0, 0, 0)
        v = (py - top) / max(1, hem - top)
        grain = authoring.noise(px // 3, py // 3, 3503) % 11
        ridge = .31 + .08 * math.sin(px * .077)
        if v < .12 or v > .9:
            color = (10, 24, 39)
        elif abs(v - ridge) < .12:
            color = (30, 48, 66) if grain else (25, 41, 59)
        elif v > .67:
            color = (14, 30, 48)
        else:
            color = (21, 40, 60)
        return (*color, 255)

    side_wrap_uv = m.patch(96, 48, paint_side_wrap, "wrapped_cowl_sides")
    for name, x0, x1 in (("left", -3.66, -3.28),
                         ("right", 3.07, 3.46)):
        m.cube(f"side_wrapped_cowl_{name}", [x0, 20.05, -3.65],
               [x1, 23.6, 2.5], "cloth", scarf,
               face_uv={"east": side_wrap_uv, "west": side_wrap_uv})

    back_cowl_folds = (
        (-4.02, 2.94, 19.75, 22.42, 3.02, 1),
        (-4.45, 1.54, 18.65, 21.6, 3.2, 2),
    )
    for left, right, low, high, depth, layer in back_cowl_folds:
        def paint_back_wrap(px, py, seed=layer):
            u = px / 95
            arc = max(0, math.sin(math.pi * u)) ** 1.2
            top = 2 + round((12 + seed * 2) * arc + (0, 5, 12)[seed] * u)
            top += authoring.noise(px // 6, seed, 3511) % 3
            hem = top + 22 - seed * 2 - authoring.noise(px // 7, seed, 3517) % 6
            if px < 2 or px > 93 or py < top or py > hem:
                return (0, 0, 0, 0)
            v = (py - top) / max(1, hem - top)
            grain = authoring.noise(px // 3, py // 3, 3527 + seed) % 13
            ridge = .33 + .08 * math.sin(px * .067 + seed)
            if v < .12 or v > .9:
                color = (10, 23, 37)
            elif abs(v - ridge) < .12:
                color = (31, 49, 67) if grain > 2 else (25, 42, 59)
            elif v > .67:
                color = (15, 30, 46)
            else:
                color = (22, 39, 57)
            return (*color, 255)

        back_uv = m.patch(96, 48, paint_back_wrap,
                          f"back_draped_cowl_{layer}")
        m.cube(f"back_draped_cowl_{layer}", [left, low, depth - .22],
               [right, high, depth + .5], "cloth", scarf,
               face_uv={"south": back_uv, "north": back_uv,
                        "east": cowl_edge_uv, "west": cowl_edge_uv})

    def paint_shoulder_cowl(px, py):
        left = 4 + py // 12
        right = 29 - py // 15
        hem = 43 - authoring.noise(px // 3, 0, 3413) % 8
        if px < left or px > right or py > hem or py < 2 + abs(px - 16) // 6:
            return (0, 0, 0, 0)
        if py > 26 and abs(px - (13 + py // 7)) < 2:
            return (0, 0, 0, 0)
        fold = math.sin(px * .19 + py * .07)
        color = (14, 30, 49) if fold < -.35 else (39, 64, 89) if fold > .75 else (23, 44, 68)
        if px - left < 2 or right - px < 2 or hem - py < 2:
            color = (11, 25, 41)
        return (*color, 255)

    shoulder_cowl_uv = m.patch(32, 48, paint_shoulder_cowl, "torn_shoulder_cowl")
    m.cube("scarf_right_torn_face", [2.65, 19.75, -2.24],
           [4.55, 22.65, -2.17], "cloth", scarf,
           face_uv={"north": shoulder_cowl_uv, "south": shoulder_cowl_uv})
    def paint_back_cowl(px, py):
        side = abs(px - 47.5) / 48
        top = 2 + round(8 * side ** 1.4)
        hem = 44 - round(16 * side) - authoring.noise(px // 7, 0, 3659) % 4
        if py < top or py > hem:
            return (0, 0, 0, 0)
        fold = math.sin(px * .072 + py * .045)
        color = (12, 26, 44) if fold < -.35 else (36, 58, 80) if fold > .7 else (22, 41, 64)
        if py - top < 2 or hem - py < 3:
            color = (9, 21, 37)
        return (*color, 255)

    back_cowl_uv = m.patch(96, 48, paint_back_cowl, "back_cowl_fold")
    m.cube("scarf_back_left", [-4.8, 19.3, 2.38], [.85, 22.6, 2.68],
           "cloth", scarf, face_uv={"south": back_cowl_uv, "north": back_cowl_uv,
                                    "east": cowl_edge_uv, "west": cowl_edge_uv})
    m.cube("scarf_back_right_end", [3.2, 20.35, 2.25], [4.9, 22.25, 3.18],
           "void", cape_right, face_uv={"south": shoulder_cowl_uv,
                                        "north": shoulder_cowl_uv,
                                        "east": cowl_edge_uv, "west": cowl_edge_uv})
    def paint_shoulder_bridge(px, py):
        taper = py / 79
        left = 3 + round(8 * taper) + authoring.noise(py // 6, 0, 3731) % 3
        right = 45 - round(11 * taper) - authoring.noise(py // 7, 0, 3737) % 3
        hem = 73 - authoring.noise(px // 4, 0, 3749) % 10
        top = 3 + round(px * .25) + authoring.noise(px // 6, 0, 3757) % 3
        if py < top or py > hem or px < left or px > right:
            return (0, 0, 0, 0)
        if py > 43 and px > right - 5 and (px * 2 + py) % 17 < 5:
            return (0, 0, 0, 0)
        ridge = abs(px - (18 + 4 * math.sin(py * .09)))
        grain = authoring.noise(px // 3, py // 3, 3761) % 13
        if px - left < 2 or right - px < 2 or py > hem - 2:
            color = (8, 19, 33)
        elif ridge < 4 and grain > 6:
            color = (27, 45, 65)
        elif ridge > 15:
            color = (10, 23, 38)
        else:
            color = (17, 34, 52)
        return (*color, 255)

    bridge_uv = m.patch(48, 80, paint_shoulder_bridge, "shoulder_to_cape_bridge")
    def paint_bridge_edge(px, py):
        taper = py / 79
        left = 2 + round(3 * taper) + authoring.noise(py // 7, 0, 3781) % 2
        right = 14 - round(3 * taper) - authoring.noise(py // 6, 0, 3787) % 2
        hem = 75 - authoring.noise(px // 3, 0, 3793) % 7
        if px < left or px > right or py < 3 + px // 5 or py > hem:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 2, py // 3, 3797)
        color = (8, 20, 33) if grain % 4 else (15, 30, 45)
        if grain % 61 == 0:
            color = (26, 43, 59)
        return (*color, 255)

    bridge_edge_uv = m.patch(16, 80, paint_bridge_edge,
                             "shoulder_to_cape_edge")
    open_bridge_uv = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0),
                             "open_shoulder_bridge")
    for facet, depth in enumerate((2.15, 2.86, 2.36)):
        xlo = -6.25 + facet * .9
        uvlo = bridge_uv[0] + facet * 16
        uvhi = uvlo + 16
        face_uv = {"north": [uvlo, bridge_uv[1], uvhi, bridge_uv[3]],
                   "south": [uvlo, bridge_uv[1], uvhi, bridge_uv[3]],
                   "east": open_bridge_uv, "west": open_bridge_uv,
                   "up": open_bridge_uv, "down": open_bridge_uv}
        if facet == 0:
            face_uv["west"] = bridge_edge_uv
        if facet == 2:
            face_uv["east"] = bridge_edge_uv
        m.cube(f"scarf_left_shoulder_bridge_{facet}",
               [xlo - .02, 14.65, depth],
               [xlo + .92, 21.15, depth + (1.38, 1.02, 1.21)[facet]],
               "cloth", cape_left_edge, face_uv=face_uv)

    # The mask is the face; only a narrow, dark head and a short mount sit
    # behind it. The mane supplies the rear silhouette.
    def paint_burned_hood(px, py):
        grain = authoring.noise(px // 3, py // 3, 5621)
        crease = (px * 2 + py) % 19 < 2
        if crease:
            return (28, 36, 44, 255)
        return ((14, 23, 33, 255) if grain % 5 == 0
                else (19, 28, 38, 255))
    hood_uv = m.patch(32, 32, paint_burned_hood, "burned_hood_shared")
    hood_faces = {face: hood_uv for face in
                  ("north", "south", "east", "west", "up", "down")}
    add_rotated(m, helm, "hood_skull_core",
                [-.76, 24.08, -.84], [.76, 25.99, .51], "void",
                [-8, 0, 0], [0, 25.0, -.1], "burned_hood", hood_faces)
    add_rotated(m, helm, "hood_brow_overhang",
                [-.99, 25.53, -2.04], [.99, 26.1, -.63], "void",
                [13, 0, 0], [0, 25.84, -1.3], "burned_hood", hood_faces)
    add_rotated(m, helm, "hood_lower_jaw",
                [-.74, 22.75, -2.34], [.74, 24.0, -.66], "void",
                [-11, 0, 0], [0, 23.3, -1.5], "burned_hood", hood_faces)
    add_rotated(m, helm, "hood_muzzle_base",
                [-.65, 22.52, -2.46], [.65, 23.62, -.62], "void",
                [-9, 0, 0], [0, 23.0, -1.5], "burned_hood", hood_faces)
    add(m, helm, "snout_dark_tip", [-.45, 21.15, -5.75], [.45, 21.85, -5.1], "void")

    # A cutout engraved faceplate supplies a distinct long-muzzled silhouette
    # without reproducing another game's texture or sculpt. The existing helm
    # cubes retain the depth from the side and above.
    def paint_faceplate(px, py):
        center = 24
        distance = abs(px - center)
        if py < 7:
            width = 2 + py // 2
        elif py < 19:
            width = 7 + (py - 7) // 2
        elif py < 33:
            width = 14 - max(0, py - 28) // 3
        elif py < 52:
            width = 12 - (py - 33) // 3
        else:
            width = max(1, 6 - (py - 52) // 2)
        if px > center and py > 32:
            width -= 1
        if distance > width:
            return (0, 0, 0, 0)
        if px < center - 8 and 38 < py < 47 and (px + py) % 4 != 0:
            return (0, 0, 0, 0)
        eye_line = (28 if px < center else 30) - distance * .32
        if 4 <= distance <= 12 and abs(py - eye_line) < (1.2 if px < center else 1.5):
            return (5, 10, 17, 255)
        if 4 <= distance <= 12 and abs(py - (eye_line - 2.2)) < 1:
            return (82, 91, 95, 255)
        snout_ridge = 6 - (py - 37) * .18
        if 37 <= py <= 59 and abs(distance - snout_ridge) < .85:
            return (73, 81, 84, 255)
        if 36 <= py <= 60 and distance <= 1:
            return (20, 27, 31, 255)
        if py >= 59 and distance < 3:
            return (17, 23, 27, 255)
        if abs(distance - width) <= 1 and py % 7 != 0:
            return (72, 81, 85, 255)
        if px > center + 7 and 17 < py < 45 and (px + py * 2) % 8 < 2:
            return (0, 0, 0, 0)
        scratch = (px * 3 + py * 5) % 47
        if scratch == 0:
            return (101, 109, 110, 255)
        return (42, 49, 53, 255)

    faceplate_uv = m.patch(48, 64, paint_faceplate, "ashen_faceplate")
    def paint_visor_profile(px, py):
        # The side is a compact, worn helmet bowl. An extended diagonal strip
        # became two crossing blade-like projections in profile.
        u = (px - 23.5) / 21
        if abs(u) >= 1:
            return (0, 0, 0, 0)
        half_height = 14 * math.sqrt(1 - u * u)
        center = 32 + 4 * u
        if abs(py - center) > half_height:
            return (0, 0, 0, 0)
        eye = 27 < px < 37 and center - 10 < py < center - 6
        if eye:
            return (8, 14, 19, 255)
        distance = abs(py - center) / max(1, half_height)
        if distance > .88:
            return ((48, 56, 61, 255) if py < center
                    else (28, 35, 40, 255))
        if authoring.noise(px // 3, py // 3, 5933) % 39 == 0:
            return (57, 64, 66, 255)
        return (23, 32, 39, 255)

    visor_profile_uv = m.patch(48, 64, paint_visor_profile, "wolf_visor_profile")
    visor_back_uv = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0), "wolf_visor_back")
    m.cube("engraved_wolf_visor", [-2.45, 21.05, -5.7], [2.45, 27.5, -4.15],
           "edge", helm, face_uv={"north": faceplate_uv, "south": visor_back_uv,
                                  "east": visor_profile_uv,
                                  "west": [visor_profile_uv[2], visor_profile_uv[1],
                                           visor_profile_uv[0], visor_profile_uv[3]],
                                  "up": visor_back_uv, "down": visor_back_uv})
    # Wind-torn locks start inside the hood and follow different curved paths.
    # Each short segment is aligned to its path so the mane has depth from all
    # angles without long, rectangular planes behind the head.
    def paint_mane(px, py, seed):
        fiber = (px * 3 + py // 3 + seed * 5) % 13
        wear = authoring.noise(px // 2, py // 4, 6721 + seed)
        if fiber in (0, 1) and wear % 5 != 0:
            tone = (34, 38, 40)
        elif fiber == 2:
            tone = (25, 29, 32)
        elif wear % 7 == 0:
            tone = (12, 17, 22)
        else:
            tone = (19, 23, 27)
        return (*tone, 255)

    mane_paths = (
        ((-1.2, 26.7, 1.2), (-3.5, 27.0, 4.0), (-3.8, 23.0, 8.5), 1.0),
        ((-.3, 26.8, 1.4), (-2.4, 27.1, 4.6), (-3.5, 24.0, 9.0), .95),
        ((.7, 26.6, 1.5), (-1.4, 26.3, 4.2), (-3.0, 22.6, 8.2), .9),
        ((-1.4, 26.2, 1.5), (-3.7, 25.5, 4.2), (-4.2, 20.6, 9.0), .8),
        ((-.4, 26.2, 1.7), (-2.1, 25.4, 4.7), (-3.7, 19.3, 9.4), .78),
        ((.7, 26.0, 1.8), (-.9, 25.2, 4.0), (-3.2, 20.6, 7.7), .8),
        ((-1.3, 25.6, 1.7), (-2.8, 24.5, 3.8), (-3.2, 17.8, 6.9), .7),
        ((-.4, 25.5, 1.8), (-1.6, 24.4, 4.5), (-2.8, 18.6, 7.5), .68),
        ((.5, 25.5, 1.7), (-.4, 24.5, 4.1), (-2.1, 19.7, 6.9), .65),
        ((.9, 26.5, 1.7), (1.2, 26.2, 5.5), (1.7, 22.5, 8.8), .62),
        ((.1, 26.3, 1.8), (.5, 25.1, 6.0), (2.5, 20.8, 9.5), .55),
        ((-.8, 26.0, 1.9), (-.3, 24.2, 5.6), (.5, 18.7, 8.6), .5),
    )
    for strand, (start, control, tip, root_width) in enumerate(mane_paths):
        mane_uv = m.patch(16, 32,
                          lambda px, py, s=strand: paint_mane(px, py, s),
                          f"wind_torn_mane_{strand}")
        mane_faces = {side: mane_uv if side in ("north", "south", "east", "west")
                      else visor_back_uv for side in
                      ("north", "south", "east", "west", "up", "down")}
        def point(u):
            a, b, c = (1 - u) ** 2, 2 * (1 - u) * u, u ** 2
            return tuple(a * start[i] + b * control[i] + c * tip[i]
                         for i in range(3))

        for section in range(8):
            u0, u1 = section / 8, (section + 1) / 8
            ax, ay, az = point(u0)
            bx, by, bz = point(u1)
            cx, cy, cz = ((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2)
            dx, dy, dz = bx - ax, by - ay, bz - az
            horizontal = math.hypot(dx, dz)
            length = math.hypot(horizontal, dy)
            mid_u = (u0 + u1) / 2
            root_relief = .55 + .45 * min(1, mid_u / .22)
            taper = (1 - mid_u) ** .95 * root_relief
            # A connected wind-torn mane needs mass near the hood. Thin,
            # evenly separated rods read as a crown of mechanical antennae.
            width = .12 + root_width * 1.25 * taper
            height = .13 + root_width * 1.15 * taper
            add_rotated(m, plume, f"mane_lock_{strand}_{section}",
                        [cx - width / 2, cy - height / 2, cz - length / 2 - .09],
                        [cx + width / 2, cy + height / 2, cz + length / 2 + .09],
                        "hair", [-math.degrees(math.atan2(dy, horizontal)),
                                 math.degrees(math.atan2(dx, dz)), 0],
                        [cx, cy, cz], "worn_mane", mane_faces)

    for side, x in (("left", -2.1), ("right", 2.1)):
        thigh = left_thigh if side == "left" else right_thigh
        shin = left_shin if side == "left" else right_shin
        add(m, thigh, f"{side}_thigh_mail", [x - 1.63, 6.6, -1.55], [x + 1.63, 11.2, 1.55], "mail")
        add(m, thigh, f"{side}_cloth_undertunic", [x - 1.58, 8.0, -1.71], [x + 1.58, 10.7, 1.6], "void")
        add_rotated(m, shin, f"{side}_shin_upper_underlayer",
                    [x - 1.16, 4.2, -1.28], [x + 1.16, 7.1, 1.2],
                    "void", [0, 0, -3 if side == "left" else 4],
                    [x, 4.3, 0], "worn_leather")
        add_rotated(m, shin, f"{side}_shin_lower_underlayer",
                    [x - .94, 1.7, -1.16], [x + .94, 4.35, 1.07],
                    "void", [0, 0, 3 if side == "left" else -4],
                    [x, 4.25, 0], "worn_leather")
        # A narrow leather ankle sits in a wider, low heel.  The old single
        # heel/vamp/toe cuboids read as a pair of mechanical rectangular feet.
        heel_width = .95 if side == "left" else .88
        add(m, shin, f"{side}_boot_heel", [x - heel_width, -.15, -.67],
            [x + heel_width, .69, 1.16], "boot")
        add_rotated(m, shin, f"{side}_boot_ankle",
                    [x - .82, .50, -.72], [x + .82, 1.97, .98],
                    "boot", [0, 0, -5 if side == "left" else 4],
                    [x, .76, .1], "worn_vamp")
        add_rotated(m, shin, f"{side}_boot_instep",
                    [x - 1.02, .12, -1.79], [x + 1.02, 1.12, -.34],
                    "boot", [-11, 0, 0], [x, .52, -1.05], "worn_vamp")
        add_rotated(m, shin, f"{side}_boot_vamp",
                    [x - .90, .07, -2.67], [x + .90, .75, -1.32],
                    "boot", [-8, 0, -4 if side == "left" else 3],
                    [x, .36, -1.85], "worn_vamp")
        toe_width = .81 if side == "left" else .75
        add_rotated(m, shin, f"{side}_toe_cap", [x - toe_width, .08, -3.03],
                    [x + toe_width, .52, -2.46], "boot", [0, 0, -3],
                    [x, .28, -2.67], "worn_vamp")
        if side == "left":
            add_rotated(m, shin, "left_worn_toe_shard",
                        [x - .58, .42, -3.12], [x + .34, .63, -2.55],
                        "armor", [-8, 0, 14], [x, .5, -2.8], "worn_toe")
        if side == "left":
            add(m, thigh, "left_broken_knee_plate", [x - 1.4, 6.0, -1.95], [x + .8, 7.4, -.98], "armor")
            add(m, shin, "left_greave_rim", [x - 1.48, 2.35, -1.72], [x - 1.1, 6.7, -1.39], "ash")
        else:
            add(m, thigh, "right_knee_cloth", [x - 1.45, 6.2, -1.9], [x + 1.15, 7.65, -.95], "void")
            add(m, shin, "right_greave_chip", [x + .72, 3.2, -1.65], [x + 1.22, 5.1, -1.35], "edge")

    def paint_side_greave(px, py, seed, upper):
        left = 3 + round(py * (.16 if upper else .12))
        right = 29 - round(py * (.30 if upper else .22))
        top = 2 + abs(px - (18 if upper else 12)) // 5
        hem = 36 - authoring.noise(px // 4, seed + upper, 1091) % 8
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        if (seed == 0 and upper and 14 < py < 32 and px > right - 5
                or seed == 1 and not upper and 17 < py < 34 and px < left + 5):
            return (0, 0, 0, 0)
        scratch = abs(px - (9 + py * .33 + seed * 5))
        if 8 < py < 27 and scratch < 1.1:
            return (66, 74, 76, 255)
        grain = authoring.noise(px // 3, py // 3, 1103 + seed * 3 + upper)
        if py - top < 2 and grain % 3 == 0:
            return (63, 72, 75, 255)
        base = (37, 45, 50) if grain % 5 else (24, 32, 37)
        if px - left < 2 or right - px < 2 or py > hem - 2:
            base = (23, 31, 36)
        if seed == 1:
            base = tuple(round(channel * .74) for channel in base)
        return (*base, 255)

    side_greave_clear = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0),
                                "open_greave_edge")
    for side, x, seed, shin in (("left", -2.1, 0, left_shin),
                                ("right", 2.1, 1, right_shin)):
        outer = x - 1.32 if seed == 0 else x + 1.32
        for upper, (low, high, zlo, zhi) in enumerate((
                (2.35, 4.85, -1.27, .95),
                (4.75, 6.85, -1.43, .85))):
            uv = m.patch(32, 40,
                         lambda px, py, s=seed, u=upper: paint_side_greave(px, py, s, u),
                         f"broken_{side}_side_greave_{upper}")
            m.cube(f"{side}_side_greave_shard_{upper}",
                   [outer - .07, low, zlo], [outer + .07, high, zhi],
                   "armor", shin,
                   face_uv={"east": uv, "west": uv,
                            "north": side_greave_clear, "south": side_greave_clear,
                            "up": side_greave_clear, "down": side_greave_clear})

    def paint_greave_face(px, py, seed):
        # The surviving plate narrows over the calf and ends in an uneven,
        # broken point. A uniform rectangular shin made the legs look robotic.
        taper = round(py * (.12 if seed == 0 else .055))
        left = 2 + taper + (2 if seed == 0 and 17 < py < 38 else 0)
        right = 29 - taper
        if seed == 1 and 9 < py < 24:
            right -= round((24 - py) * .36)
        if seed == 1:
            # The sword-side leg has a torn partial plate, exposing mail and
            # leather instead of repeating the other leg's full greave.
            left += 7 + round(4 * math.sin(py * .085))
            if py > 39:
                right -= round((py - 39) * .36)
        if seed == 0 and 33 < py < 52:
            left += round((py - 33) * .3)
        top = 2 + abs(px - (15 + seed * 2)) // 6
        hem = (58 - authoring.noise(px // 3, seed, 1083) % 5
               - max(0, abs(px - 15) - 5) // 2)
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        if seed == 0 and 25 < py < 48 and abs(px - (left + 1 + (py - 25) * .22)) < 1.3:
            return (0, 0, 0, 0)
        nick = abs(px - (13 + py * .17 + seed * 5))
        if 26 < py < 50 and nick < 1.1:
            return (18, 24, 29, 255)
        if px - left < 2 or right - px < 2 or py - top < 2:
            return (67, 76, 79, 255)
        if (px * 7 + py * 11 + seed * 17) % 83 < 2:
            return (92, 98, 98, 255)
        grain = authoring.noise(px // 3, py // 4, 1179 + seed)
        return ((37, 44, 48, 255) if grain % 5 else (26, 34, 39, 255))

    for side, x, seed, shin in (("left", -2.1, 0, left_shin),
                                ("right", 2.1, 1, right_shin)):
        face_uv = m.patch(32, 64, lambda px, py, s=seed: paint_greave_face(px, py, s),
                          f"battered_greave_{side}")
        m.cube(f"{side}_battered_greave_face", [x - 1.5, 1.95, -1.63],
               [x + 1.5, 7.15, -1.56], "armor", shin,
               face_uv={"north": face_uv, "south": face_uv})

    def paint_rear_greave(px, py, seed):
        progress = py / 63
        left = 3 + round(progress * (9 if seed == 0 else 7))
        right = 29 - round(progress * (8 if seed == 0 else 10))
        top = 3 + abs(px - (15 + seed * 2)) // 6
        hem = 58 - authoring.noise(px // 4, seed, 1129) % 9
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        if 22 < py < 47 and abs(px - (12 + py * .13 + seed * 4)) < 2:
            return (0, 0, 0, 0)
        if seed == 1 and py > 31 and px < left + 5:
            return (0, 0, 0, 0)
        scar = abs(px - (22 - py * .23 + seed * 3))
        if 9 < py < 37 and scar < 1:
            return (81, 88, 88, 255)
        grain = authoring.noise(px // 3, py // 3, 1147 + seed)
        if px - left < 2 or right - px < 2 or py > hem - 2:
            return (19, 26, 30, 255)
        return ((42, 49, 52, 255) if grain % 7 else (29, 36, 39, 255))

    rear_greave_clear = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0),
                                "open_rear_greave")
    for side, x, seed, shin in (("left", -2.1, 0, left_shin),
                                ("right", 2.1, 1, right_shin)):
        uv = m.patch(32, 64,
                     lambda px, py, s=seed: paint_rear_greave(px, py, s),
                     f"worn_rear_greave_{side}")
        m.cube(f"{side}_worn_rear_greave", [x - 1.2, 2.15, 1.35],
               [x + 1.2, 6.85, 1.43], "armor", shin,
               face_uv={"south": uv, "north": uv,
                        "east": rear_greave_clear, "west": rear_greave_clear,
                        "up": rear_greave_clear, "down": rear_greave_clear})

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

    add(m, left_arm, "bound_upper_arm", [-6, 15.8, -.8], [-3.7, 21, 1.3], "sleeve")
    def paint_wounded_mail(px, py):
        left = 3 + round(py * .07) + authoring.noise(py // 6, 0, 2363) % 3
        right = 38 - round(py * .1) - authoring.noise(py // 5, 0, 2371) % 4
        hem = 63 - authoring.noise(px // 3, 0, 2381) % 12
        if px < left or px > right or py > hem:
            return (0, 0, 0, 0)
        if py > 43 and px > 22 and (px + py * 2) % 19 < 4:
            return (0, 0, 0, 0)
        row = py // 6
        tx = (px + (row % 2) * 4) % 8
        grain = authoring.noise(px // 3, py // 3, 2387)
        ring = ((py % 6 == 1 and tx in (2, 3, 4)) or
                (py % 6 in (2, 3) and tx in (1, 5)))
        if ring and grain % 7 != 0:
            color = (59, 66, 66) if grain % 5 else (72, 76, 73)
        elif py % 6 == 4 and tx in (2, 3, 4):
            color = (10, 17, 22)
        else:
            color = (22, 30, 36)
        return (*color, 255)

    wounded_mail_uv = m.patch(40, 64, paint_wounded_mail,
                              "torn_wounded_arm_mail")
    m.cube("torn_wounded_arm_mail", [-6.22, 15.65, -1.21],
           [-3.5, 20.95, -1.13], "mail", left_arm,
           face_uv={"north": wounded_mail_uv, "south": wounded_mail_uv})
    add_rotated(m, left_arm, "wounded_upper_forearm", [-6.02, 12.8, -.89],
                [-4.21, 16.35, .94], "mail", [0, 0, 5],
                [-5.1, 14.5, 0], "torn_mail")
    add_rotated(m, left_arm, "wounded_tapered_forearm", [-5.78, 10.0, -.83],
                [-4.4, 13.45, .84], "mail", [0, 0, -4],
                [-5.1, 11.9, 0], "torn_mail")
    add(m, left_arm, "left_palm", [-5.72, 8.8, -.91], [-4.48, 10.55, .78], "boot")
    for finger, (x0, x1, low, high, tilt) in enumerate((
            (-5.92, -5.56, 7.7, 9.1, -8),
            (-5.51, -5.14, 7.45, 9.0, -2),
            (-5.08, -4.73, 7.55, 9.0, 5),
            (-4.68, -4.37, 8.0, 9.1, 13),
    )):
        add_rotated(m, left_arm, f"loose_finger_{finger}",
                    [x0, low, -.72], [x1, high, .34], "boot",
                    [0, 0, tilt], [(x0 + x1) / 2, high, -.2], "worn_vamp")
    add(m, left_arm, "left_arm_tear", [-6.5, 11.2, 1.1], [-4.9, 16.2, 1.8], "void")
    add(m, left_arm, "left_mail_shoulder", [-6.15, 17.2, -1], [-3.55, 20.2, 1.5], "sleeve")
    def paint_wounded_wrap(px, py, seed):
        top = 2 + authoring.noise(px // 5, seed, 2301) % 3
        hem = 25 - authoring.noise(px // 6, seed, 2311) % 5
        left = 3 + authoring.noise(py // 4, seed, 2321) % 3
        right = 60 - authoring.noise(py // 5, seed, 2333) % 5
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        if py > 15 and (px + seed * 11) % 31 < 3:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 2, py // 2, 2341 + seed)
        crease = abs(py - (11 + 2 * math.sin(px * .09 + seed))) < 2
        stain = ((px - (15 + seed * 17)) ** 2 / 100
                 + (py - (15 - seed * 2)) ** 2 / 32) < 1
        if stain and grain % 4 != 0:
            color = (87, 48, 44)
        elif crease:
            color = (78, 69, 63)
        elif py <= top + 2 or py >= hem - 2:
            color = (83, 75, 68)
        else:
            color = (139, 123, 103) if grain % 5 else (111, 98, 84)
        return (*color, 255)

    # Uneven narrow bindings expose the wounded arm between strips. Broad,
    # evenly spaced hoops made its silhouette read like a machine cylinder.
    for wrap, (lo, hi, tilt, pivot) in enumerate((
            ((-6.27, 14.61, -1.59), (-3.96, 15.19, 1.26), -17, (-5.1, 14.9, 0)),
            ((-6.16, 12.76, -1.57), (-4.04, 13.32, 1.23), 13, (-5.1, 13.0, 0)),
            ((-6.01, 10.6, -1.47), (-4.17, 11.08, 1.13), -12, (-5.1, 10.85, 0)),
    )):
        wrap_uv = m.patch(64, 28,
                          lambda px, py, seed=wrap: paint_wounded_wrap(px, py, seed),
                          f"wounded_arm_wrap_{wrap}")
        add_rotated(m, left_arm, f"wounded_arm_wrap_{wrap}", lo, hi,
                    "bandage", [0, 0, tilt], pivot, "torn_wrap",
                    {"north": wrap_uv, "south": wrap_uv,
                     "east": wrap_uv, "west": wrap_uv})
    def paint_sword_sleeve_side(px, py, seed):
        left = 2 + py // 19 + authoring.noise(py // 5, seed, 5971) % 3
        right = 45 - py // 16 - authoring.noise(py // 6, seed, 5981) % 3
        hem = 61 - authoring.noise(px // 4, seed, 5987) % 7
        if px < left or px > right or py > hem:
            return (0, 0, 0, 0)
        if py > 44 and (px * 3 + py + seed * 7) % 23 < 3:
            return (0, 0, 0, 0)
        fold = math.sin(px * .13 + py * .09 + seed) + .25 * math.sin(py * .17)
        grain = authoring.noise(px // 3, py // 4, 5993 + seed)
        if fold > .65:
            color = (32, 43, 51)
        elif fold < -.4:
            color = (12, 23, 31)
        else:
            color = (21, 33, 41)
        if grain % 31 == 0:
            color = (46, 53, 56)
        return (*color, 255)

    sword_sleeve_upper_uv = m.patch(48, 64,
                                     lambda px, py: paint_sword_sleeve_side(px, py, 0),
                                     "sword_sleeve_upper_side")
    sword_sleeve_lower_uv = m.patch(48, 64,
                                     lambda px, py: paint_sword_sleeve_side(px, py, 1),
                                     "sword_sleeve_lower_side")
    add_rotated(m, right_arm, "right_bicep_dark_under", [3.97, 17.65, -.73],
                [5.63, 19.79, 1.28], "sleeve", [3, 3, -10],
                [4.8, 18.7, .25], "worn_sleeve",
                {"east": sword_sleeve_upper_uv, "west": sword_sleeve_upper_uv})
    add_rotated(m, right_arm, "right_bicep_tapered_under", [4.15, 15.72, -.65],
                [5.43, 17.95, 1.14], "sleeve", [-2, -6, -12],
                [4.8, 16.85, .2], "worn_sleeve",
                {"east": sword_sleeve_lower_uv, "west": sword_sleeve_lower_uv})

    def paint_right_sleeve(px, py):
        # One ragged mail silhouette spans the underlying dark anatomy. The
        # former overlapping full grey cuboids made a uniform metal piston.
        left = 2 + round(py * .085) + authoring.noise(py // 5, 0, 2171) % 3
        right = 38 - round(py * .11) - authoring.noise(py // 6, 0, 2179) % 4
        hem = 62 - authoring.noise(px // 3, 0, 2191) % 10
        if px < left or px > right or py > hem:
            return (0, 0, 0, 0)
        if py > 42 and (px * 2 + py * 3) % 23 < 3:
            return (0, 0, 0, 0)
        row = py // 3
        ring_x = (px + (row % 2) * 2) % 5
        grain = authoring.noise(px // 3, py // 3, 2203)
        missing = grain % 11 < 3 or (px > 24 and 25 < py < 53 and
                                     authoring.noise(px // 5, py // 6, 2211) % 4 == 0)
        if not missing and ((py % 3 == 0 and ring_x in (1, 2)) or
                            (py % 3 == 1 and ring_x in (0, 3))):
            color = (43, 50, 52) if grain % 5 else (51, 57, 57)
        elif py % 3 == 2 and ring_x in (1, 2):
            color = (11, 18, 23)
        else:
            color = (23, 31, 36)
        if missing:
            color = (15, 24, 30) if grain % 3 else (20, 30, 36)
        return (*color, 255)

    sleeve_uv = m.patch(40, 64, paint_right_sleeve, "torn_sword_sleeve")
    m.cube("right_torn_mail_sleeve", [3.5, 15.55, -1.2], [6.05, 21.1, -1.12],
           "mail", right_arm, face_uv={"north": sleeve_uv, "south": sleeve_uv})
    def paint_outer_mail(px, py):
        left = 3 + py // 11
        right = 29 - py // 9
        hem = 38 - authoring.noise(px // 3, 0, 2279) % 9
        if px < left or px > right or py > hem:
            return (0, 0, 0, 0)
        if py > 23 and (px * 3 + py) % 17 < 3:
            return (0, 0, 0, 0)
        row = py // 3
        ring = (px + (row % 2) * 2) % 5
        wear = authoring.noise(px // 3, py // 3, 2287)
        if wear % 9 < 3:
            return (18, 26, 31, 255)
        if py % 3 == 0 and ring in (1, 2):
            return (39, 47, 49, 255)
        if py % 3 == 2 and ring in (1, 2):
            return (11, 18, 23, 255)
        return (23, 31, 36, 255)

    outer_mail_uv = m.patch(32, 48, paint_outer_mail, "frayed_outer_mail")
    add_rotated(m, right_arm, "right_outer_mail_fray",
                [5.51, 15.8, -.65], [5.6, 18.55, 1.3], "mail",
                [0, 0, -11], [4.85, 17.3, .3], "torn_mail",
                {"east": outer_mail_uv, "west": outer_mail_uv})
    add(m, right_forearm, "right_elbow_dark", [3.9, 15.35, -.83],
        [5.9, 16.15, 1.42], "void")
    def paint_gauntlet_leather(px, py):
        grain = authoring.noise(px // 2, py // 2, 2291)
        seam = abs(px - (7 + py // 6)) < 1
        if seam and 4 < py < 29:
            return (43, 44, 42, 255)
        if grain % 47 == 0:
            return (52, 53, 48, 255)
        if grain % 11 == 0 or (px + py * 2) % 29 == 0:
            return (12, 18, 22, 255)
        return (27, 31, 33, 255)

    gauntlet_uv = m.patch(24, 32, paint_gauntlet_leather,
                         "sword_gauntlet_dark_leather")
    gauntlet_faces = {face: gauntlet_uv for face in
                      ("north", "south", "east", "west", "up", "down")}
    add_rotated(m, right_forearm, "right_bracer_upper", [3.97, 14.58, -.99],
                [5.91, 16.02, 1.29], "leather", [0, 0, -13],
                [4.94, 15.3, .1], "scuffed_leather", gauntlet_faces)
    add_rotated(m, right_forearm, "right_bracer_middle", [4.06, 13.34, -1.02],
                [5.83, 14.78, 1.25], "leather", [0, 0, -4],
                [4.94, 14.05, .1], "scuffed_leather", gauntlet_faces)
    add_rotated(m, right_forearm, "right_bracer_wrist", [4.04, 11.08, -1.1],
                [5.92, 13.65, 1.37], "leather", [0, 0, 5],
                [4.98, 12.4, .1], "battered_scale", gauntlet_faces)
    def paint_bracer_shard(px, py, seed):
        left = (4 + py // 10) if seed == 0 else (3 + py // 15)
        right = (25 - py // 12) if seed == 0 else (28 - py // 19)
        top = 2 + abs(px - 15) // 8
        hem = 37 - authoring.noise(px // 4, seed, 2241) % 5
        if px < left or px > right or py < top or py > hem:
            return (0, 0, 0, 0)
        if seed == 0 and 16 < py < 32 and px > right - 5:
            return (0, 0, 0, 0)
        if seed == 1 and 11 < py < 25 and px < left + 3:
            return (0, 0, 0, 0)
        scar = abs(px - (9 + py * .38 + seed * 4))
        if scar < 1.1 and 8 < py < 31:
            return (91, 100, 102, 255)
        if py <= top + 1 or px - left < 2 or right - px < 2:
            return (89, 98, 100, 255)
        grain = authoring.noise(px // 3, py // 3, 2251 + seed)
        base = (42, 50, 53) if grain % 5 else (32, 39, 43)
        return (*base, 255)

    for shard, (lo, hi) in enumerate((((3.7, 13.45, -1.27), (6.1, 16.12, -1.2)),
                                      ((4.03, 11.05, -1.21), (5.95, 13.6, -1.14)))):
        uv = m.patch(32, 40, lambda px, py, s=shard: paint_bracer_shard(px, py, s),
                     f"bracer_shard_{shard}")
        m.cube(f"right_bracer_shard_{shard}", lo, hi, "armor", right_forearm,
               face_uv={"north": uv, "south": uv})
    add_rotated(m, right_forearm, "right_palm", [4.18, 10.18, -.98],
                [5.83, 11.94, 1.06], "leather", [0, 0, -4],
                [5, 11.06, 0], "scuffed_leather", gauntlet_faces)
    for finger, x in enumerate((4.35, 4.91, 5.47)):
        add_rotated(m, right_forearm, f"right_grip_finger_{finger}",
                    [x, 9.54 + finger * .09, -1.03],
                    [x + .43, 10.6 + finger * .04, -.42], "leather",
                    [5, 0, (-8, 2, 10)[finger]], [x + .21, 10.32, -.65],
                    "scuffed_leather", gauntlet_faces)
        add(m, right_forearm, f"right_knuckle_plate_{finger}",
            [x - .03, 10.3 + finger * .04, -1.24],
            [x + .45, 10.8 + finger * .04, -1.0], "armor", "battered_scale")
    add(m, right_forearm, "right_bracer_chip", [3.65, 12.2, -1.38], [5.45, 13.1, -.98], "armor")

    # At rest the heavy blade hangs beside the right leg.
    add(m, blade, "pommel", [4.38, 12.0, -.65], [5.62, 13.25, .65], "armor")
    add(m, blade, "grip", [4.45, 9, -.55], [5.55, 12.4, .55], "void")
    add_rotated(m, blade, "guard", [1.9, 8.6, -.9], [7.95, 9.3, .9],
                "guard", [0, 0, -3], [5, 8.95, 0], "battered_guard")
    add_rotated(m, blade, "guard_left_tooth",
                [1.47, 8.15, -1.0], [2.63, 10.35, 1.0], "guard",
                [0, 0, 11], [2.1, 9.2, 0], "battered_guard")
    add_rotated(m, blade, "guard_right_tooth",
                [7.17, 8.22, -.96], [8.12, 9.65, .96], "guard",
                [0, 0, -17], [7.67, 8.95, 0], "battered_guard")
    add(m, blade, "blade_spine_upper", [4.15, -.8, -.52],
        [5.85, 8.6, .52], "armor")
    add(m, blade, "blade_spine_middle", [4.32, -3.65, -.39],
        [5.68, -.75, .39], "armor")
    add(m, blade, "blade_spine_tip", [4.65, -5.33, -.22],
        [5.35, -3.6, .22], "armor")

    def paint_worn_blade(px, py):
        center = 23.5
        progress = py / 159
        narrowing = 4.0 * progress + 14.5 * max(0, (progress - .7) / .3) ** 1.25
        width = max(1.2, 20.5 - narrowing)
        distance = abs(px - center)
        edge_wear = authoring.noise(py // 4, 1 if px < center else 2, 709) % 4
        edge = width - (.42 * edge_wear if py > 24 else 0)
        nick_left = 78 < py < 103 and px < center - width + 4 + (py - 78) // 9
        nick_right = 43 < py < 61 and px > center + width - 4
        if distance > edge or nick_left or nick_right:
            return (0, 0, 0, 0)
        grain = authoring.noise(px // 3, py // 4, 713)
        if distance > edge - 1.6:
            return ((72, 80, 81, 255) if grain % 5 == 0
                    else (51, 61, 63, 255))
        if distance > edge - 5:
            return ((62, 71, 73, 255) if (py // 17 + grain) % 5 == 0
                    else (44, 54, 58, 255))
        if distance < 2.4 and py < 139:
            return (22, 30, 35, 255)
        scar = abs(px - (center + 5 * math.sin(py / 22 + .8)))
        if 40 < py < 124 and scar < 1.2 and grain % 5 != 0:
            return (69, 75, 76, 255)
        if 5 < distance < 13 and abs((py // 11) % 7 - (px // 7) % 7) <= 1:
            return (29, 38, 42, 255)
        if grain % 43 == 0:
            return (72, 79, 80, 255)
        return ((47, 57, 62, 255) if grain % 4 else (55, 63, 66, 255))

    blade_uv = m.patch(48, 160, paint_worn_blade, "worn_blade")
    m.cube("blade_worn_faces", [3.48, -5.7, -.9], [6.52, 8.6, -.78],
           "armor", blade, face_uv={"north": blade_uv, "south": blade_uv})
    m.cube("blade_worn_back_face", [3.48, -5.7, .78], [6.52, 8.6, .9],
           "armor", blade, face_uv={"north": blade_uv, "south": blade_uv})
    # Preserve the greatsword's long silhouette while it hangs beside the leg.
    for element in m.elements:
        if element["name"].startswith(("blade_spine_", "blade_worn_faces",
                                       "blade_worn_back_face")):
            for key in ("from", "to", "origin"):
                element[key][1] = round(8.6 + (element[key][1] - 8.6) * .72, 3)

    # Leave the chainmail back exposed. The short scarf above and torn cloth
    # tied at the hips have separate silhouettes, like a battle-worn knight.
    cape_strips = (
        (-5.15, 5.2, 3.05, 1.25, -20),
        (-.9, 4.25, 4.15, 2.0, -3),
        (2.2, 4.15, 3.05, 4.0, 20),
    )
    open_hem_uv = m.patch(1, 1, lambda _x, _y: (0, 0, 0, 0), "open_cloth_hem")

    for strip, (center, width, depth, hem, yaw) in enumerate(cape_strips):
        def paint_strip(px, py, seed=strip):
            progress = py / 191
            taper = round(progress ** 1.5 * (5 if seed == 0 else 8))
            left_edge = 1 + taper + authoring.noise(py // 7, seed, 817) % 6
            right_edge = 46 - taper - authoring.noise(py // 8, seed, 829) % 7
            if py < 55:
                root_taper = round((1 - py / 55) * (7, 5, 4)[seed])
                left_edge += root_taper
                right_edge -= root_taper
            if seed == 0 and 70 < py < 125:
                left_edge += round(11 * (1 - abs(py - 97) / 28))
            if seed == 1 and 93 < py < 153:
                right_edge -= round(12 * (1 - abs(py - 123) / 30))
            if seed == 2 and 46 < py < 107:
                right_edge -= round(8 * (1 - abs(py - 76) / 31))
            tear = 191 - (px // 5 % 5) * (2 + seed % 2) - authoring.noise(px // 3, seed, 839) % 10
            # A forked, missing wedge opens as the cloth descends. The former
            # one-pixel slits disappeared at boss-fight viewing distance.
            split_center = 20 + 4 * math.sin(progress * 3 + seed * 1.4)
            split_width = max(0, progress - (.63, .65, .58)[seed]) * (10, 16, 15)[seed]
            slit = abs(px - split_center) < split_width
            if px < left_edge or px > right_edge or py > tear or slit:
                return (0, 0, 0, 0)
            if seed in (0, 2) and 88 <= py <= 145:
                notch = max(0, 7 - abs(py - (115 + seed * 5)) * .43)
                if px > right_edge - notch:
                    return (0, 0, 0, 0)
            if seed == 1 and 116 <= py <= 168:
                notch = max(0, 11 - abs(py - 143) * .36)
                if px < left_edge + notch:
                    return (0, 0, 0, 0)
            ridge = (23 + 7 * math.sin(progress * 3.4 + seed * 1.35)
                     + 3 * math.sin(py * .17 + seed))
            distance_to_ridge = abs(px - ridge)
            grain = authoring.noise(px // 2, py // 3, seed + 2800)
            if distance_to_ridge < 3 and grain % 5 != 0:
                color = (42, 63, 85)
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
        def paint_cloak_edge(px, py, seed=strip):
            # The front/back cutout used to be paired with transparent cube
            # sides, making every strip vanish edge-on despite its geometry.
            left = 1 if authoring.noise(py // 5, seed, 8251) % 4 else 2
            right = 7 if authoring.noise(py // 4, seed, 8253) % 4 else 6
            torn_hem = 31 - authoring.noise(px // 2, seed, 8257) % 4
            if px < left or px > right or py < 1 or py > torn_hem:
                return (0, 0, 0, 0)
            grain = authoring.noise(px // 2, py // 3, 8267 + seed)
            color = (9, 20, 31) if grain % 5 else (14, 27, 39)
            if grain % 47 == 0:
                color = (20, 34, 46)
            return (*color, 255)
        side = strip < 2
        buckets = ((cape_left, cape_left_mid, cape_left_tail) if side else
                   (cape_center, cape_center_mid, cape_center_tail))
        cape_top = 14.8
        boundaries = ((cape_top, max(11.2, hem)),
                      (11.3, max(7.8, hem)), (7.9, hem))
        def fold_depth(u, v):
            return (.55 * math.sin((u * .95 + v * .22 + strip * .29) * math.tau)
                    + .28 * math.sin((u * 1.8 - v * .58 + strip * .4) * math.tau))

        for segment, (top, bottom) in enumerate(boundaries):
            if top <= bottom + .1:
                continue
            drift = segment * (-.72, -.15, .65)[strip]
            x = center + drift
            base_z = depth + (.22, .72, 1.32)[segment]
            segment_width = width * ((.72, 1.05, .87)[segment] if strip == 0
                                     else (.7, 1.08, .9)[segment])
            for row in range(3):
                row_top = top - (top - bottom) * row / 3
                row_bottom = top - (top - bottom) * (row + 1) / 3
                row_width = segment_width * (1 - row * .055)
                row_center = (x + row * (-.15, -.04, .14)[strip]
                              + .13 * math.sin((segment * 3 + row) * 1.5 + strip))
                v = (cape_top - (row_top + row_bottom) / 2) / (cape_top - hem)
                ty0 = uv[1] + round((cape_top - row_top) / (cape_top - hem) * 192)
                ty1 = uv[1] + round((cape_top - row_bottom) / (cape_top - hem) * 192)
                for facet in range(3):
                    edge_seed = strip * 150 + segment * 15 + row * 3 + facet
                    edge_uv = m.patch(8, 32,
                                      lambda px, py, seed=edge_seed:
                                      paint_cloak_edge(px, py, seed),
                                      f"ashen_cloak_edge_{edge_seed}")
                    u = (facet + .5) / 3
                    center_x = row_center + (u - .5) * row_width
                    center_y = (row_top + row_bottom) / 2
                    def cloth_z(across, down):
                        # Keep a curved cross-section, then tilt each facet
                        # along that curve so neighboring rows meet at their
                        # edges instead of forming separated horizontal slabs.
                        billow = (1.2 * math.sin(math.pi * max(0, min(1, down)))
                                  if strip == 0 else 0)
                        cross_section = (.9 + 2.4 * math.sin(
                            math.pi * max(0, min(1, down))))
                        return base_z + cross_section * fold_depth(across, down) + billow
                    z = cloth_z(u, v)
                    v_top = (cape_top - row_top) / (cape_top - hem)
                    v_bottom = (cape_top - row_bottom) / (cape_top - hem)
                    pitch = math.degrees(math.atan2(
                        cloth_z(u, v_top) - cloth_z(u, v_bottom),
                        row_top - row_bottom))
                    yaw_fold = -math.degrees(math.atan2(
                        cloth_z((facet + 1) / 3, v) - cloth_z(facet / 3, v),
                        row_width / 3))
                    face_uv = {"north": [uv[0] + round(facet * 48 / 3), ty0,
                                         uv[0] + round((facet + 1) * 48 / 3), ty1],
                               "south": [uv[0] + round(facet * 48 / 3), ty0,
                                         uv[0] + round((facet + 1) * 48 / 3), ty1],
                               "east": edge_uv, "west": edge_uv,
                               "up": open_hem_uv, "down": open_hem_uv}
                    half_depth = ((.58, .44, .29)[segment] if strip == 0
                                  else (.43, .33, .22)[segment]) * (1 - .10 * row)
                    add_rotated(m, buckets[segment], f"cape_strip_{strip}_{segment}_{row}_{facet}",
                                [center_x - row_width / 6 - .06, row_bottom - .08, z - half_depth],
                                [center_x + row_width / 6 + .06, row_top + .08, z + half_depth],
                                "cloth",
                                [max(-28, min(28, pitch)),
                                 yaw + max(-24, min(24, yaw_fold)), 0],
                                [center_x, center_y, z],
                                f"ragged_cape_{strip}_{segment}", face_uv)

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

    # Keep the hood large enough to read between the collar and mane. Narrow
    # the faceplate separately so the muzzle stays animal-like without making
    # the entire head disappear at combat distance.
    helm_ids = set(helm)
    plume_ids = set(plume)
    for element in m.elements:
        if element["uuid"] in helm_ids:
            for key in ("from", "to", "origin"):
                x, y, z = element[key]
                element[key] = [x, 22 + (y - 22) * .95, z]
            if element["name"] == "engraved_wolf_visor":
                for key in ("from", "to", "origin"):
                    element[key][0] *= .70
            if element["name"] in ("snout_left_ridge", "snout_right_ridge", "snout_dark_tip",
                                   "engraved_wolf_visor"):
                for key in ("from", "to", "origin"):
                    element[key][1] += 1.55
                    element[key][2] += 3.15
            if element["name"] == "engraved_wolf_visor":
                for key in ("from", "to", "origin"):
                    element[key][1] = 25.4 + (element[key][1] - 25.4) * .78
        elif element["uuid"] in plume_ids:
            for key in ("from", "to", "origin"):
                element[key][1] -= .7

    # The sword-side gauntlet should still carry weight, but its old uniform
    # width made the whole arm read as a mechanical piston from three-quarter
    # view. Keep the grip aligned while tapering the armor around it.
    right_arm_ids = set(right_arm + right_forearm)
    for element in m.elements:
        if element["uuid"] in right_arm_ids:
            for key in ("from", "to", "origin"):
                x, y, z = element[key]
                element[key] = [4.8 + (x - 4.8) * .72, y, z * .76]

    plume_bone = m.bone("plume", [0, 26.5, 1], plume)
    head_bone = m.bone("head", [0, 22, 0], helm + [plume_bone])
    left_arm_bone = m.bone("left_arm", [-4.8, 21, 0], left_arm)
    sword_bone = m.bone("sword", [5, 10.5, 0], blade)
    sword_bone["rotation"] = [-20, 0, -30]
    right_elbow_bone = m.bone("right_elbow", [4.8, 15.65, 0],
                              right_forearm + [sword_bone])
    right_elbow_bone["rotation"] = [0, 0, -14]
    right_arm_bone = m.bone("right_arm", [4.8, 21, 0],
                            right_arm + [right_elbow_bone])
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
        "torso": [(0, [28, 0, -3]), (1, [30, 0, -3]), (2, [28, 0, -3])],
        "head": [(0, [-16, -5, 0]), (1, [-18, -2, 0]), (2, [-16, -5, 0])],
        "plume": [(0, [0, 0, -3]), (1, [2, 0, 5]), (2, [0, 0, -3])],
        "left_leg": [(0, [-10, 0, 0]), (1, [-10, 0, 0]), (2, [-10, 0, 0])],
        "right_leg": [(0, [12, 0, 0]), (1, [12, 0, 0]), (2, [12, 0, 0])],
        "left_knee": [(0, [20, 0, 0]), (1, [20, 0, 0]), (2, [20, 0, 0])],
        "right_knee": [(0, [-17, 0, 0]), (1, [-17, 0, 0]), (2, [-17, 0, 0])],
        "right_arm": [(0, [4, 0, -16]), (1, [4, 0, -16]), (2, [4, 0, -16])],
        "right_elbow": [(0, [0, 0, 5]), (1, [0, 0, 5]), (2, [0, 0, 5])],
        "sword": [(0, [-30, 0, 30]), (1, [-30, 0, 30]), (2, [-30, 0, 30])],
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
        "right_elbow": [(0, [0, 0, -5]), (.4, [0, 0, 4]), (.8, [0, 0, -5])],
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
        "right_elbow": [(0, [0, 0, -10]), (.3, [0, 0, 5]), (.6, [0, 0, -10])],
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
        "right_elbow": [(0, [0, 0, -7]), (.8, [0, 0, 10]), (1.6, [0, 0, -3])],
        "sword": [(0, [0, 0, 0]), (.55, [55, 0, 0]), (1.2, [55, 0, 0]), (1.6, [0, 0, 0])],
        "cape_left": [(0, [20, 0, -7]), (1.1, [-18, 0, -22]), (1.6, [0, 0, -5])],
    })
    m.anim("cleave", 1.25, {
        "root": [(0, [0, 0, 0], "position"), (.35, [0, -.55, 0], "position"), (.72, [0, .4, -1], "position"), (1.25, [0, 0, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.38, [9, -32, -13]), (.7, [20, 38, 7]), (.92, [22, 46, 9]), (1.25, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.38, [-85, 0, -25]), (.72, [-24, 0, 0]), (.92, [18, 0, 7]), (1.25, [4, 0, 4])],
        "right_elbow": [(0, [0, 0, -3]), (.38, [0, 0, -19]), (.72, [0, 0, 13]), (.92, [0, 0, 8]), (1.25, [0, 0, -3])],
        "sword": [(0, [0, 0, 0]), (.15, [40, 0, -38]), (.38, [-20, 0, -95]), (.58, [45, 0, -25]), (.72, [40, 0, 25]), (.92, [25, 0, 10]), (1.25, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.72, [-22, 0, -19]), (1.25, [0, 0, -5])],
        "cape_center": [(0, [-4, 0, 0]), (.72, [-29, 0, 11]), (1.25, [-4, 0, 0])],
    })
    m.anim("cleave_reverse", 1.05, {
        "root": [(0, [0, 0, 0], "position"), (.5, [0, .45, -.6], "position"), (1.05, [0, 0, 0], "position")],
        "torso": [(0, [18, 40, 9]), (.22, [20, 48, 10]), (.5, [13, -40, -12]), (1.05, [16, 0, -6])],
        "right_arm": [(0, [22, 0, 90]), (.22, [28, 0, 100]), (.5, [-40, 0, -65]), (.78, [-25, 0, -80]), (1.05, [4, 0, 4])],
        "right_elbow": [(0, [0, 0, -6]), (.22, [0, 0, -17]), (.5, [0, 0, 12]), (.78, [0, 0, 7]), (1.05, [0, 0, -3])],
        "sword": [(0, [0, 0, 0]), (.22, [0, 0, 105]), (.5, [35, 0, -50]), (.78, [25, 0, -35]), (1.05, [0, 0, 0])],
        "cape_right": [(0, [-8, 0, 8]), (.5, [-30, 0, 22]), (1.05, [0, 0, 4])],
    })
    m.anim("thrust", 1.0, {
        "root": [(0, [0, 0, 0], "position"), (.32, [0, -.4, 0], "position"), (.55, [0, .1, -2], "position"), (1, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (.32, [7, 18, -7]), (.55, [27, -12, 3]), (1, [16, 0, -6])],
        "right_arm": [(0, [4, 0, 4]), (.32, [-75, 0, -28]), (.55, [-96, 0, 3]), (.8, [-90, 0, 2]), (1, [4, 0, 4])],
        "right_elbow": [(0, [0, 0, -3]), (.32, [0, 0, -18]), (.55, [0, 0, 13]), (.8, [0, 0, 10]), (1, [0, 0, -3])],
        "sword": [(0, [0, 0, 0]), (.1, [10, 0, -33]), (.32, [0, 0, -105]), (.55, [0, 0, -120]), (.8, [40, 0, -65]), (.93, [35, 0, -25]), (1, [0, 0, 0])],
        "left_leg": [(0, [0, 0, 0]), (.55, [-33, 0, 0]), (1, [0, 0, 0])],
        "right_leg": [(0, [0, 0, 0]), (.55, [19, 0, 0]), (1, [0, 0, 0])],
    })
    m.anim("dash", .9, {
        "torso": [(0, [8, 0, -4]), (.2, [28, 0, -3]), (.65, [30, 0, 4]), (.9, [8, 0, -4])],
        "right_arm": [(0, [4, 0, 4]), (.2, [-50, 0, -12]), (.65, [-45, 0, -10]), (.9, [4, 0, 4])],
        "right_elbow": [(0, [0, 0, -3]), (.2, [0, 0, -15]), (.65, [0, 0, -11]), (.9, [0, 0, -3])],
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
        "right_elbow": [(0, [0, 0, -3]), (.55, [0, 0, -21]), (.85, [0, 0, 8]), (1.1, [0, 0, -3])],
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
        "right_elbow": [(0, [0, 0, -3]), (.2, [0, 0, -15]), (.7, [0, 0, -24]), (.84, [0, 0, 8]), (.94, [0, 0, 15]), (1.35, [0, 0, -3])],
        "sword": [(0, [0, 0, 0]), (.2, [30, 0, -26]), (.7, [-50, 0, -60]), (.84, [10, 0, -90]), (.94, [-75, 0, -10]), (1.35, [0, 0, 0])],
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
        "right_elbow": [(0, [0, 0, -3]), (.25, [0, 0, -20]), (.8, [0, 0, -3])],
    })
    m.anim("enrage", 1.5, {
        "root": [(0, [0, 0, 0], "position"), (.3, [0, -1.1, 0], "position"), (1.1, [0, -.7, 0], "position"), (1.5, [0, 0, 0], "position")],
        "torso": [(0, [16, 0, -6]), (.3, [47, 0, -10]), (.8, [-22, 0, 3]), (1.5, [14, 0, -5])],
        "head": [(0, [-9, 0, 0]), (.3, [24, 0, 0]), (.8, [-26, 0, 0]), (1.5, [-9, 0, 0])],
        "right_arm": [(0, [4, 0, 4]), (.3, [48, 0, 22]), (.8, [-90, 0, 70]), (1.5, [4, 0, 4])],
        "right_elbow": [(0, [0, 0, -3]), (.3, [0, 0, -17]), (.8, [0, 0, 10]), (1.5, [0, 0, -3])],
        "sword": [(0, [0, 0, 0]), (.8, [0, 0, 0]), (1.1, [25, 0, 0]), (1.35, [25, 0, 0]), (1.5, [0, 0, 0])],
        "left_arm": [(0, [0, 0, 0]), (.8, [-70, 0, -60]), (1.5, [0, 0, 0])],
        "cape_left": [(0, [0, 0, -5]), (.8, [-33, 0, -27]), (1.5, [0, 0, -5])],
        "cape_right": [(0, [0, 0, 4]), (.8, [-37, 0, 30]), (1.5, [0, 0, 4])],
    })
    m.anim("death", 1.6, {
        "root": [(0, [0, 0, 0], "position"), (.8, [0, -2, 0], "position"), (1.6, [0, -5, 0], "position")],
        "torso": [(0, [8, 0, -4]), (.7, [38, 0, -8]), (1.6, [86, 0, -9])],
        "right_arm": [(0, [4, 0, 4]), (.7, [35, 0, 36]), (1.6, [88, 0, 85])],
        "right_elbow": [(0, [0, 0, -3]), (.7, [0, 0, -12]), (1.6, [0, 0, -35])],
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
