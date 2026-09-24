"""Build a rejected, separate silhouette study for the Hollow Vow boss.

The mantle is a curved, segmented shell around a solid body, not a flat cape
decal. This study keeps Ashen Knight's animation bone names for direct motion
comparisons. Its current shape fails HOLLOW_VOW_DIRECTION.md's visual gate;
it must not replace the production asset.
"""

from pathlib import Path
import copy
import json
import math
import sys
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_ashen_knight import KnightModel, add_rotated  # noqa: E402
from build_ashen_head_v3 import build as build_head  # noqa: E402
from merge_ashen_head_v3 import merge as merge_head  # noqa: E402


def build(out=ROOT / "model-lab" / "build" / "hollow-vow"):
    out.mkdir(parents=True, exist_ok=True)
    m = KnightModel("hollow_vow", size=1024, texels=4)
    hips, chest, head, plume = [], [], [], []
    left_arm, right_arm, right_forearm, sword = [], [], [], []
    left_thigh, right_thigh, left_shin, right_shin = [], [], [], []
    cape_left, cape_right, cape_center = [], [], []
    cape_left_mid, cape_right_mid, cape_center_mid = [], [], []
    cape_left_tail, cape_right_tail, cape_center_tail = [], [], []

    def swatch(color, key):
        uv = m.patch(6, 6, lambda x, y: (*(
            max(0, min(255, channel + (((x * 17 + y * 11 + len(key)) % 13) - 6) // 3))
            for channel in color), 255), key)
        return {face: uv for face in
                ("north", "south", "east", "west", "up", "down")}

    void = swatch((13, 16, 20), "vow_void")
    cloth = swatch((28, 31, 37), "vow_cloth")
    cloth_light = swatch((37, 40, 45), "vow_cloth_light")
    iron = swatch((46, 46, 44), "vow_iron")
    iron_edge = swatch((72, 69, 64), "vow_iron_edge")
    bandage = swatch((37, 32, 31), "vow_bandage")
    leather = swatch((43, 37, 32), "vow_leather")

    # A three-dimensional, tapered core keeps the front opening in the mantle
    # from becoming a hole. The upper back is deeper than the waist.
    for row, (lo, hi) in enumerate((
            ((-2.35, 16.0, -1.65), (2.35, 19.0, 1.80)),
            ((-3.30, 19.0, -1.85), (3.30, 22.0, 2.20)),
            ((-2.75, 22.0, -1.70), (2.75, 23.55, 2.15)))):
        m.cube(f"gambeson_core_{row}", lo, hi, "void", chest,
               face_uv=cloth if row == 0 else void)
    m.cube("waist_core", [-2.8, 12.4, -1.60], [2.8, 16.1, 1.72],
           "void", hips, face_uv=void)

    # The front glimpse of ruined mail is deliberately narrow and dark.
    def mail_paint(x, y):
        left = 4 + y // 18
        right = 59 - y // 15
        if x < left or x > right or y > 91 - (x * 7 + 13) % 11:
            return (0, 0, 0, 0)
        if x > 32 and y > 33 and (x // 5 + y // 7) % 4 != 0:
            return (0, 0, 0, 0)
        grain = (x * 37 + y * 53 + x * y * 7) % 31
        ring = (x + 3 * (y // 5 % 2)) % 7 in (2, 3) and y % 5 in (1, 2)
        return (*((34, 35, 35) if ring and grain % 5 else
                  (18, 21, 24) if grain % 8 else (25, 26, 27)), 255)

    mail_uv = m.patch(64, 96, mail_paint, "vow_broken_mail")
    m.cube("broken_mail_glimpse", [-2.95, 14.2, -2.0],
           [2.95, 22.15, -1.87], "mail", chest,
           face_uv={"north": mail_uv, "south": mail_uv,
                    "east": void["east"], "west": void["west"],
                    "up": void["up"], "down": void["down"]})

    concept = Image.open(ROOT / "model-lab" / "references" /
                         "hollow_vow_concept_v1.png").convert("RGB")
    if concept.size != (1536, 1024):
        raise ValueError("hollow_vow_concept_v1.png must be 1536x1024")

    # The cloak uses one continuous cylindrical painting wrapped around a
    # faceted shell. It has weight, broad folds, side depth and a ragged hem.
    facet_count = 24
    layer_count = 4
    def mantle_paint(x, y):
        angle = 2 * math.pi * x / (facet_count * 16)
        v = y / (layer_count * 28)
        fold = (math.sin(angle * 5.0 + v * 3.5)
                + .48 * math.sin(angle * 9.0 - v * 2.0))
        grain = (x * 31 + y * 17 + (x // 3) * (y // 4) * 7) % 79
        tone = 6 * fold + (grain % 7 - 3) * .42
        if v > .67:
            tone -= 3 * (v - .67) / .33
        if y > 224 + (x // 11 * 17 % 23) - (x // 27 % 3) * 5:
            return (0, 0, 0, 0)
        if v > .78 and (x * 7 + y * 3) % 97 < 3:
            return (0, 0, 0, 0)
        color = (round(22 + tone * 1.3), round(25 + tone * 1.3),
                 round(31 + tone * 1.3))
        row = min(layer_count - 1, y // 63)
        radius_x = (3.85, 4.20, 5.20, 6.20)[row]
        radius_z = (2.18, 2.82, 3.53, 4.27)[row]
        center_z = (.55, 1.13, 1.73, 2.23)[row]
        world_x = radius_x * math.sin(angle)
        world_z = center_z + radius_z * math.cos(angle)
        world_y = 23.25 - y * (22.0 / 252)
        if math.cos(angle) < -.35:
            sx = round(204 + world_x * 26)
        elif math.cos(angle) > .35:
            sx = round(1333 - world_x * 25)
        else:
            sx = round(988 + world_z * 19)
        sy = round(861 - world_y * 25.5)
        sx = max(0, min(1535, sx))
        sy = max(0, min(1023, sy))
        sampled = concept.getpixel((sx, sy))
        if 8 < sum(sampled) / 3 < 110:
            color = tuple(round(.72 * sampled[i] + .28 * color[i])
                          for i in range(3))
        return (*(max(7, min(92, c)) for c in color), 255)

    mantle_uv = m.patch(384, 252, mantle_paint, "hollow_vow_mantle_wrap")
    edge_uv = cloth["east"]
    mantle_shape = (
        (3.85, 2.18, .55),
        (4.20, 2.82, 1.13),
        (5.20, 3.53, 1.73),
        (6.20, 4.27, 2.23),
    )
    for row in range(layer_count):
        top = 23.25 - row * 5.47
        bottom = top - 5.85
        mid_y = (top + bottom) / 2
        rx, rz, cz = mantle_shape[row]
        for facet in range(facet_count):
            theta = 2 * math.pi * (facet + .5) / facet_count
            front = abs((theta - math.pi + math.pi) % (2 * math.pi) - math.pi)
            # The cowl opens into a narrow damaged mail wedge in front.
            if front < (1.18 if row < 2 else .94):
                continue
            cx = rx * math.sin(theta)
            z = cz + rz * math.cos(theta)
            tangent = math.sqrt((rx * math.cos(theta)) ** 2 +
                                (rz * math.sin(theta)) ** 2)
            width = 2 * math.pi * tangent / facet_count * 1.13
            hem_rise = ((facet * 17 + facet * facet * 3) % 11) * .18 if row == 3 else 0
            lo = [cx - width / 2, bottom + hem_rise, z - .13]
            hi = [cx + width / 2, top, z + .13]
            uv = [mantle_uv[0] + facet * 16,
                  mantle_uv[1] + row * 63,
                  mantle_uv[0] + (facet + 1) * 16,
                  mantle_uv[1] + (row + 1) * 63]
            faces = {"north": uv, "south": uv,
                     "east": edge_uv, "west": edge_uv,
                     "up": edge_uv, "down": edge_uv}
            bucket = (cape_left if cx < -1.4 else
                      cape_right if cx > 1.4 else cape_center)
            if row == 3:
                bucket = (cape_left_tail if cx < -1.4 else
                          cape_right_tail if cx > 1.4 else cape_center_tail)
            elif row >= 1:
                bucket = (cape_left_mid if cx < -1.4 else
                          cape_right_mid if cx > 1.4 else cape_center_mid)
            add_rotated(m, bucket, f"mantle_{row}_{facet}", lo, hi,
                        "cloth", [0, math.degrees(theta), 0],
                        [cx, mid_y, z], "worn_cloth", faces)

    # Layered throat wrap forms a hanging V, hiding the flat breast and
    # interrupting the old straight shoulder line.
    def cowl_paint(x, y):
        u = abs((x - 63.5) / 63.5)
        v = y / 127
        width = .98 - .80 * v ** 1.5
        nick = ((x * 29 + y * 37) % 113 == 0 and v > .62)
        if u > width or nick:
            return (0, 0, 0, 0)
        broad_fold = (math.sin(x * .095 + y * .06)
                      + .55 * math.sin(x * .19 - y * .038))
        grain = ((x * 19 + y * 43 + x * y * 3) % 37 - 18) / 18
        pleat = 4 if abs((x + y * .2) % 24 - 12) < 2 else 0
        value = round(27 + 6 * broad_fold + 1.2 * grain + pleat)
        return (max(15, value - 2), value, min(55, value + 6), 255)
    cowl_uv = m.patch(128, 128, cowl_paint, "vow_front_cowl")
    for rank, (half_width, top, bottom, depth) in enumerate((
            (3.65, 24.1, 19.1, -2.61),
            (3.10, 23.0, 18.4, -2.73),
            (2.55, 21.9, 17.9, -2.85))):
        faces = {face: cowl_uv if face in ("north", "south") else cloth[face]
                 for face in ("north", "south", "east", "west", "up", "down")}
        m.cube(f"cowl_fold_{rank}",
               [-half_width, bottom, depth - .055],
               [half_width, top, depth + .055], "cloth", chest,
               face_uv=faces)

    # The hanging arm and sword arm have different mass and posture.
    m.cube("left_upper_sleeve", [-5.23, 15.6, -1.15],
           [-3.65, 21.1, 1.0], "sleeve", left_arm, face_uv=cloth)
    add_rotated(m, left_arm, "left_shattered_forearm",
                [-5.45, 10.6, -1.12], [-3.72, 15.7, .96],
                "bandage", [9, 0, -8], [-4.65, 15.4, 0],
                "wounded", bandage)
    m.cube("left_limp_hand", [-5.1, 9.0, -1.05],
           [-3.9, 11.1, .52], "leather", left_arm, face_uv=bandage)
    for rank in range(5):
        y = 15.8 + rank * .96
        add_rotated(m, left_arm, f"left_torn_arm_wrap_{rank}",
                    [-5.28, y, -1.32], [-3.52, y + .35, 1.10],
                    "bandage", [0, 0, 8 - rank * 2], [-4.4, y, 0],
                    "cloth", bandage if rank % 2 else cloth_light)
    m.cube("right_upper_arm", [3.68, 15.9, -1.26],
           [5.32, 21.3, 1.17], "sleeve", right_arm, face_uv=cloth)
    add_rotated(m, right_forearm, "right_armoured_forearm",
                [3.73, 11.1, -1.39], [5.75, 16.1, 1.2],
                "armor", [0, 0, -7], [4.7, 15.9, 0],
                "battered", cloth_light)
    m.cube("right_grip_hand", [3.95, 12.8, -2.52],
           [5.52, 14.3, -.6], "leather", right_forearm, face_uv=iron)
    for rank in range(4):
        y = 12.3 + rank * .93
        add_rotated(m, right_forearm, f"right_forearm_course_{rank}",
                    [3.58, y, -1.55], [5.85, y + .38, 1.32],
                    "armor", [0, 0, -8], [4.7, 15.9, 0],
                    "battered", iron if rank % 2 else iron_edge)

    # Broken forged shoulder courses turn over the joint; no square plate.
    for side, bucket in ((-1, chest), (1, right_arm)):
        for rank in range(6 if side == -1 else 3):
            x = side * (3.55 + .34 * rank)
            lo = [x - .54, 21.0 - .4 * rank, -2.0 + .16 * rank]
            hi = [x + .54, 23.15 - .30 * rank, .62 + .12 * rank]
            faces = iron_edge if side == -1 and rank == 0 else iron
            add_rotated(m, bucket, f"shoulder_{side}_{rank}", lo, hi,
                        "armor", [-8, side * 10, side * (9 + 3 * rank)],
                        [x, 22.0 - .4 * rank, 0], "forged", faces)

    # Dark legs are mostly concealed by the mantle; boots remain grounded.
    for side, thigh, shin in ((-1, left_thigh, left_shin),
                              (1, right_thigh, right_shin)):
        x = side * 2.05
        m.cube(f"leg_{side}_thigh", [x - 1.4, 6.3, -1.25],
               [x + 1.4, 13.6, 1.55], "void", thigh, face_uv=void)
        m.cube(f"leg_{side}_shin", [x - 1.24, 2.1, -1.45],
               [x + 1.24, 7.0, 1.4], "void", shin, face_uv=iron)
        m.cube(f"leg_{side}_boot", [x - 1.27, .08, -2.48],
               [x + 1.27, 2.08, 1.12], "boot", shin, face_uv=cloth_light)
        for rank in range(3):
            z = -2.72 - .24 * rank
            m.cube(f"leg_{side}_toe_course_{rank}",
                   [x - 1.25 + .14 * rank, .15 + .10 * rank, z],
                   [x + 1.25 - .14 * rank, 1.1 + .16 * rank, z + .35],
                   "boot", shin, face_uv=iron if rank == 0 else cloth_light)

    # Chipped execution blade: sword plus arm forms the idle triangle.
    m.cube("sword_grip", [4.05, 9.8, -.68],
           [5.15, 13.9, .68], "leather", sword, face_uv=leather)
    m.cube("sword_crossguard", [2.05, 9.25, -.8],
           [7.2, 9.95, .8], "iron", sword, face_uv=iron)
    m.cube("sword_spine_upper", [3.45, 1.0, -.58],
           [5.75, 9.25, .58], "iron", sword, face_uv=iron)
    m.cube("sword_spine_middle", [3.8, -2.5, -.47],
           [5.4, 1.05, .47], "iron", sword, face_uv=iron)
    m.cube("sword_spine_tip", [4.35, -5.05, -.3],
           [4.85, -2.45, .3], "iron", sword, face_uv=iron)
    def blade_paint(x, y):
        t = y / 192
        half = 28 - 23 * max(0, (t - .62) / .38) ** 1.4
        edge = abs(x - 32)
        chip = (x * 17 + y * 31) % 43
        if edge > half - (2 if chip < 3 and y > 30 else 0):
            return (0, 0, 0, 0)
        scar = abs(x - 32 - 5 * math.sin(y / 28)) < 1.6 and 28 < y < 156
        line = abs(x - 32) < 2 and 12 < y < 154
        grain = (x * 43 + y * 29 + x * y * 5) % 29
        if line and y % 31 < 25:
            color = (65, 36, 27)
        elif scar or edge > half - 2:
            color = (61, 62, 59)
        else:
            base = 40 + (grain % 7 - 3)
            color = (base, base + 1, base)
        return (*color, 255)
    blade_uv = m.patch(64, 192, blade_paint, "vow_execution_blade")
    m.cube("sword_battered_face", [2.95, -5.25, -.78],
           [6.25, 9.15, -.65], "armor", sword,
           face_uv={"north": blade_uv, "south": blade_uv,
                    "east": iron["east"], "west": iron["west"],
                    "up": iron["up"], "down": iron["down"]})
    for element in m.elements:
        if element["uuid"] in sword:
            for field in ("from", "to", "origin"):
                element[field][2] = round(element[field][2] - 2.75, 3)
                element[field][1] = round(element[field][1] + 3.0, 3)

    plume_bone = m.bone("plume", [0, 26.5, 1], plume)
    head_bone = m.bone("head", [0, 22, 0], head + [plume_bone])
    head_bone["rotation"] = [8, -5, 0]
    left_arm_bone = m.bone("left_arm", [-4.7, 21, 0], left_arm)
    sword_bone = m.bone("sword", [4.6, 13.5, -2.75], sword)
    sword_bone["rotation"] = [0, 0, -34]
    right_elbow_bone = m.bone("right_elbow", [4.7, 15.7, 0],
                              right_forearm + [sword_bone])
    right_arm_bone = m.bone("right_arm", [4.7, 21, 0],
                            right_arm + [right_elbow_bone])
    left_tail = m.bone("cape_left_tail", [-3.2, 7.5, 2.9], cape_left_tail)
    left_mid = m.bone("cape_left_mid", [-3.2, 14, 2.3],
                       cape_left_mid + [left_tail])
    left_cape = m.bone("cape_left", [-3.4, 21, 1.3],
                        cape_left + [left_mid])
    right_tail = m.bone("cape_right_tail", [3.2, 7.5, 2.9], cape_right_tail)
    right_mid = m.bone("cape_right_mid", [3.2, 14, 2.3],
                        cape_right_mid + [right_tail])
    right_cape = m.bone("cape_right", [3.4, 21, 1.3],
                         cape_right + [right_mid])
    center_tail = m.bone("cape_center_tail", [0, 7.5, 3.1],
                          cape_center_tail)
    center_mid = m.bone("cape_center_mid", [0, 14, 2.7],
                         cape_center_mid + [center_tail])
    center_cape = m.bone("cape_center", [0, 21, 1.5],
                          cape_center + [center_mid])
    torso_bone = m.bone("torso", [0, 13, 0], chest + [
        head_bone, left_arm_bone, right_arm_bone,
        left_cape, right_cape, center_cape])
    torso_bone["rotation"] = [-20, 0, -6]
    left_knee = m.bone("left_knee", [-2.05, 6.8, 0], left_shin)
    right_knee = m.bone("right_knee", [2.05, 6.8, 0], right_shin)
    left_leg = m.bone("left_leg", [-2.05, 10.7, 0],
                       left_thigh + [left_knee])
    right_leg = m.bone("right_leg", [2.05, 10.7, 0],
                        right_thigh + [right_knee])
    left_leg["rotation"] = [-14, 0, -10]
    right_leg["rotation"] = [18, 0, 10]
    root = m.bone("root", [0, 0, 0], hips + [torso_bone, left_leg, right_leg])

    # A shared orthographic painting ties the chest and sleeves to one worn
    # material language rather than a collection of unrelated gray cuboids.
    def front_paint(x, y):
        world_x = -6.25 + x * (12.5 / 300)
        world_y = 27.0 - y * (16.0 / 400)
        sx = max(0, min(1535, round(204 + world_x * 26)))
        sy = max(0, min(1023, round(861 - world_y * 25.5)))
        sampled = concept.getpixel((sx, sy))
        if sum(sampled) / 3 < 7:
            return (0, 0, 0, 0)
        return (*(max(10, min(130, round(c * .93))) for c in sampled), 255)
    front_uv = m.patch(300, 400, front_paint, "vow_front_projection")
    projected_parts = (
        "gambeson_core", "broken_mail_glimpse", "left_upper_sleeve",
        "left_shattered_forearm", "left_torn_arm_wrap", "right_upper_arm",
        "right_armoured_forearm", "right_forearm_course", "shoulder_",
    )
    for element in m.elements:
        if not element["name"].startswith(projected_parts):
            continue
        lo, hi = element["from"], element["to"]
        element["faces"]["north"]["uv"] = [
            front_uv[0] + round((lo[0] + 6.25) * 24),
            front_uv[1] + round((27 - hi[1]) * 25),
            front_uv[0] + round((hi[0] + 6.25) * 24),
            front_uv[1] + round((27 - lo[1]) * 25),
        ]
    m.anim("idle", 2.0, {
        "torso": [(0, [-4, 0, -2]), (1, [-2, 0, -2]), (2, [-4, 0, -2])],
        "head": [(0, [3, -5, 0]), (1, [1, -3, 0]), (2, [3, -5, 0])],
        "cape_left": [(0, [0, 0, -2]), (1, [-3, 0, -6]), (2, [0, 0, -2])],
        "cape_right": [(0, [0, 0, 2]), (1, [-4, 0, 6]), (2, [0, 0, 2])],
        "cape_center": [(0, [-1, 0, 0]), (1, [-4, 0, 2]), (2, [-1, 0, 0])],
    }, loop="loop")
    m.write(out, root)
    head_path = build_head()
    if head_path is None:
        head_path = ROOT / "model-lab" / "build" / "head-v3" / "ashen_head_v3.bbmodel"
    model_path = out / "hollow_vow.bbmodel"
    merge_head(model_path, head_path)
    data = json.loads(model_path.read_text(encoding="utf-8"))
    torso = next(item for item in data["outliner"][0]["children"]
                 if isinstance(item, dict) and item["name"] == "torso")
    helm = next(item for item in torso["children"]
                if isinstance(item, dict) and item["name"] == "head")
    plume = next(item for item in helm["children"]
                 if isinstance(item, dict) and item["name"] == "plume")
    mane_ids = set(plume["children"])
    data["elements"] = [e for e in data["elements"]
                        if e["uuid"] not in mane_ids]
    plume["children"] = []
    source = next(e for e in data["elements"]
                  if e["name"].startswith("solid_nose_bridge_"))
    for side in (-1, 1):
        for level, (width, height) in enumerate(((.78, .57), (.63, .52),
                                                  (.43, .42), (.20, .31))):
            horn = copy.deepcopy(source)
            horn["name"] = f"wolf_crown_horn_{side}_{level}"
            horn["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, horn["name"] +
                                         "/hollow_vow"))
            center = side * (1.06 + .15 * level)
            bottom = 27.1 + sum((.57, .52, .42, .31)[:level])
            horn["from"] = [center - width / 2, bottom, .32 + .18 * level]
            horn["to"] = [center + width / 2, bottom + height,
                          .90 + .18 * level]
            horn["origin"] = [0, 0, 0]
            data["elements"].append(horn)
            helm["children"].insert(0, horn["uuid"])
    model_path.write_text(json.dumps(data, ensure_ascii=False,
                                     separators=(",", ":")) + "\n", encoding="utf-8")
    return model_path


if __name__ == "__main__":
    print(build())
