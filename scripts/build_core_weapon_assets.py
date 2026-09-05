"""Original code-native cuboid greatswords. No client code, shaders or global item override.

Run before build_core_ui_assets.py to include these files in the resource pack index.
All surfaces use vanilla block-atlas textures; each equipment tier has its own model.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
PALETTES = [
    ("polished_deepslate", "smooth_stone", "copper_block", "orange_terracotta"),
    ("iron_block", "quartz_block_side", "copper_block", "redstone_block"),
    ("prismarine_bricks", "diamond_block", "gold_block", "sea_lantern"),
    ("netherite_block", "iron_block", "gold_block", "amethyst_block"),
]


def box(name, lower, upper, texture, rotation=None):
    value = {"name": name, "from": lower, "to": upper,
             "faces": {face: {"uv": [0, 0, 16, 16], "texture": f"#{texture}"}
                       for face in ("north", "south", "east", "west", "up", "down")}}
    if rotation:
        value["rotation"] = rotation
    return value


def model(tier):
    body, edge, trim, gem = PALETTES[tier-1]
    elements = [
        box("leather wrapped long grip", [7, 1, 7], [9, 8, 9], "grip"),
        box("pommel", [6.5, 0, 6.5], [9.5, 2, 9.5], "trim"),
        box("lower grip ring", [6.8, 2.5, 6.8], [9.2, 3, 9.2], "trim"),
        box("upper grip ring", [6.8, 6, 6.8], [9.2, 6.5, 9.2], "trim"),
        box("wide cross guard", [2.5, 8, 6.5], [13.5, 9.5, 9.5], "trim"),
        box("steel collar", [5.5, 9.5, 6.75], [10.5, 11.5, 9.25], "body"),
        box("broad blade", [5.25, 11.5, 7.25], [10.75, 26, 8.75], "body"),
        box("left sharpened edge", [4.5, 11.5, 7.6], [5.25, 25.25, 8.4], "edge"),
        box("right sharpened edge", [10.75, 11.5, 7.6], [11.5, 25.25, 8.4], "edge"),
        box("tip shoulder", [5.75, 26, 7.3], [10.25, 27.5, 8.7], "edge"),
        box("forged point", [6.25, 26.25, 7.5], [9.75, 29.75, 8.5], "edge",
            {"origin": [8, 28, 8], "axis": "z", "angle": 45, "rescale": False}),
        box("front fuller", [7.55, 12, 7.1], [8.45, 25, 7.3], "trim"),
        box("rear fuller", [7.55, 12, 8.7], [8.45, 25, 8.9], "trim"),
        box("guard jewel", [7, 8.5, 6.25], [9, 10.5, 6.75], "gem"),
    ]
    if tier >= 3:
        for x in (3.0, 11.5):
            elements.append(box("raised guard", [x, 9.5, 7], [x+1.5, 12, 9], "trim"))
        elements.append(box("blade crest", [7.1, 13, 6.95], [8.9, 16, 7.25], "gem"))
    if tier == 4:
        for x in (4.0, 10.5):
            elements.append(box("forged shoulder", [x, 11, 7], [x+1.5, 15, 9], "trim"))
    return {
        "credit": "ProjectS original cuboid model / 2026-09-05",
        "gui_light": "front", "ambientocclusion": False,
        "textures": {"body": f"minecraft:block/{body}", "edge": f"minecraft:block/{edge}",
                     "trim": f"minecraft:block/{trim}", "gem": f"minecraft:block/{gem}",
                     "grip": "minecraft:block/dark_oak_planks", "particle": f"minecraft:block/{body}"},
        "elements": elements,
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 2, 1], "scale": [.8, .8, .8]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 2, 1], "scale": [.8, .8, .8]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, -1.5], "scale": [.72, .72, .72]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, -1.5], "scale": [.72, .72, .72]},
            "gui": {"rotation": [0, 0, -35], "translation": [-1.8, -2.5, 0], "scale": [.43, .43, .43]},
            "ground": {"rotation": [0, 0, 90], "translation": [0, 3, 0], "scale": [.4, .4, .4]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -3, 0], "scale": [.45, .45, .45]},
        },
    }


def build():
    for tier in range(1, 5):
        name = f"greatsword_t{tier}"
        files = {
            f"models/item/weapons/{name}.json": model(tier),
            f"items/weapons/{name}.json": {"model": {"type": "minecraft:model", "model": f"projects:item/weapons/{name}"}},
        }
        for relative, data in files.items():
            path = ASSETS / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print("Built four tier-specific original greatswords (8 namespaced item/model files).")


if __name__ == "__main__":
    build()
