"""One-off: the Creator's noctveil reference (Blockbench mesh, -Z front) -> shade wyrm facet data.

    python scripts/extract_boss_anatomy.py <noctveil.bbmodel> assets/bosses/shade_wyrm_ref

Writes facets.json (every mesh face of the finished left side and the centre line, +Z front, scaled)
and the reference's textures as PNGs. build_boss_shade_wyrm.py turns each face into a thin plate cube
(item models only have cuboids; Minecraft 1.21.11+ lets them rotate on all three axes) cut to the
face's outline, and mirrors the left side to the right.
"""
import base64
import json
import math
import sys
from pathlib import Path

SCALE = 3.2
RENAME = {"thorax": "body", "neck": "neck", "head": "skull", "jaw": "jaw", "left_crown": "crown_l",
          "left_fore_upper": "leg_fl", "left_fore_lower": "leg_fl_fore", "left_fore_foot": "leg_fl_paw",
          "left_hind_upper": "leg_hl", "left_hind_lower": "leg_hl_shin", "left_hind_foot": "leg_hl_foot",
          "tail_0": "tail", "tail_1": "tail_2", "tail_2": "tail_mid", "tail_3": "tail_end",
          "left_wing_shoulder": "wing_l", "left_wing_forearm": "wing_l_fore", "left_wing_hand": "wing_l_hand",
          "left_folded_membrane": "wing_l_membrane"}


def conv(p):
    """Rotate 180 degrees about Y (front -Z -> +Z, the reference's left x<0 -> our +X), then scale."""
    return [round(-p[0] * SCALE, 4), round(p[1] * SCALE, 4), round(-p[2] * SCALE, 4)]


def main(src, out):
    data = json.loads(Path(src).read_text(encoding="utf-8"))
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    elements = {e["uuid"]: e for e in data["elements"]}
    textures = []
    for i, t in enumerate(data["textures"]):
        name = f"texture_{i}.png"
        (out / name).write_bytes(base64.b64decode(t["source"].split(",", 1)[1]))
        textures.append({"file": name, "uv_width": t.get("uv_width", data["resolution"]["width"]),
                         "uv_height": t.get("uv_height", data["resolution"]["height"])})
    bones, elems = [], []

    def walk(node, parent):
        name = node["name"]
        if name == "root":
            for c in node["children"]:
                if isinstance(c, dict):
                    walk(c, None)
            return
        if name not in RENAME and not name.startswith("left_mantle_hinge"):
            return
        ours = RENAME.get(name) or name.replace("left_mantle_hinge_", "wing_l_hinge")
        bones.append({"name": ours, "parent": parent, "pivot": conv(node["origin"])})
        for c in node["children"]:
            if isinstance(c, str) and c in elements and "right" not in elements[c]["name"]:
                e = elements[c]
                origin = e.get("origin", [0, 0, 0])
                verts = {k: conv([v[0] + origin[0], v[1] + origin[1], v[2] + origin[2]]) for k, v in e["vertices"].items()}
                faces = [{"v": [verts[i] for i in f["vertices"]], "uv": [f["uv"][i] for i in f["vertices"]], "tex": f.get("texture", 0)}
                         for f in e["faces"].values() if f.get("texture") is not None and len(f["vertices"]) >= 3]
                elems.append({"name": e["name"].replace("left_", "").replace("_left", ""), "bone": ours,
                              "sided": "left" in e["name"] or ours.startswith(("leg_", "wing_", "crown_")), "faces": faces})
        for c in node["children"]:
            if isinstance(c, dict):
                walk(c, ours)

    for n in data["outliner"]:
        if isinstance(n, dict):
            walk(n, None)
    (out / "facets.json").write_text(json.dumps({
        "source": f"{Path(src).name} (Creator reference), left side + centre, +Z front, scale {SCALE}",
        "textures": textures, "bones": bones, "elements": elems}, separators=(",", ":")), encoding="utf-8")
    print(len(bones), "bones", len(elems), "elements", sum(len(e["faces"]) for e in elems), "faces")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
