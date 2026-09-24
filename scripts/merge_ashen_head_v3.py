"""Replace the Ashen Knight's old helmet with the sculpted head candidate.

The boss keeps its original head and plume bones, so its existing animations
still apply. The two upper collar courses bridge into the existing torso wrap;
the broad lowest course remains exclusive to the standalone head study.
"""

from pathlib import Path
import base64
import copy
import io
import json

from PIL import Image


def _children_ids(node):
    for child in node["children"]:
        if isinstance(child, dict):
            yield from _children_ids(child)
        else:
            yield child


def _atlas(texture):
    payload = texture["source"].split(",", 1)[1]
    return Image.open(io.BytesIO(base64.b64decode(payload))).convert("RGBA")


def merge(base_path: Path, candidate_path: Path):
    base = json.loads(base_path.read_text(encoding="utf-8"))
    candidate = json.loads(candidate_path.read_text(encoding="utf-8"))

    torso = next(node for node in base["outliner"][0]["children"]
                 if isinstance(node, dict) and node["name"] == "torso")
    head = next(node for node in torso["children"]
                if isinstance(node, dict) and node["name"] == "head")
    plume = next(node for node in head["children"]
                 if isinstance(node, dict) and node["name"] == "plume")
    old_ids = set(_children_ids(head))
    base["elements"] = [element for element in base["elements"]
                        if element["uuid"] not in old_ids]

    selected = [element for element in candidate["elements"]
                if not element["name"].startswith("cowl_2_")]
    existing_ids = {element["uuid"] for element in base["elements"]}
    if any(element["uuid"] in existing_ids for element in selected):
        raise ValueError("Candidate head UUID collides with the body")
    head_parts, hair_parts = [], []
    for source in selected:
        element = copy.deepcopy(source)
        for face in element["faces"].values():
            uv = face["uv"]
            if uv != [0, 0, 1, 1]:
                face["uv"] = [uv[0] + 1024, uv[1],
                              uv[2] + 1024, uv[3]]
        base["elements"].append(element)
        (hair_parts if element["name"].startswith("mane_") else head_parts).append(
            element["uuid"])
    plume["children"] = hair_parts
    head["children"] = head_parts + [plume]

    texture = Image.new("RGBA", (2048, 1024), (0, 0, 0, 0))
    texture.paste(_atlas(base["textures"][0]), (0, 0))
    texture.paste(_atlas(candidate["textures"][0]), (1024, 0))
    encoded = io.BytesIO()
    texture.save(encoded, format="PNG")
    base["textures"][0]["source"] = ("data:image/png;base64," +
                                      base64.b64encode(encoded.getvalue()).decode())
    base["textures"][0].update(width=2048, height=1024,
                                uv_width=2048, uv_height=1024)
    base["resolution"] = {"width": 2048, "height": 1024}

    temp_path = base_path.with_suffix(".bbmodel.tmp")
    temp_path.write_text(json.dumps(base, ensure_ascii=False,
                                    separators=(",", ":")), encoding="utf-8")
    temp_path.replace(base_path)
    return len(base["elements"])
