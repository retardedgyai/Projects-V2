"""Native voxel meshes for transient ITEM_DISPLAY combat silhouettes, not UI icons.

XZ-plane geometry has a white-hot edge, saturated body and a darker trailing rim.
Contiguous cells are merged into cuboids; no textures, fonts or client code are replaced.
"""
import math
from pathlib import Path
import json

PALETTES = {
    "steel": ("white_concrete", "light_gray_concrete", "gray_concrete"),
    "gold": ("white_concrete", "yellow_concrete", "orange_concrete"),
    "astral": ("white_concrete", "magenta_concrete", "purple_concrete"),
    "ice": ("white_concrete", "light_blue_concrete", "blue_concrete"),
    "fire": ("yellow_concrete", "orange_concrete", "red_concrete"),
    "venom": ("white_concrete", "lime_concrete", "green_concrete"),
    "life": ("white_concrete", "lime_concrete", "cyan_concrete"),
}


def cell(shape, x, z):
    r = math.hypot(x, z)
    a = math.atan2(x, z)
    if shape == "crescent":
        # A continuous swept blade, front-facing in +Z; tapers to sharp tips.
        width = .28 * max(0, math.cos(a / 1.22))
        if abs(a) < 1.9 and .94 - width < r < .99:
            return 0 if r > .91 else 1 if r > .82 else 2
    elif shape == "orbit":
        if .82 < r < .98:
            return 0 if r > .94 else 1 if r > .87 else 2
        if (abs(x) < .045 or abs(z) < .045) and .70 < r < 1:
            return 0
    elif shape == "star":
        # Long four-point nucleus with smaller diagonal rays and a faceted centre.
        limit = max(.23, .98 * abs(math.cos(2 * a)) ** 7, .52 * abs(math.sin(2 * a)) ** 10)
        if r < limit:
            return 0 if r < limit * .52 else 1 if r < limit * .82 else 2
    elif shape == "lance":
        width = .19 * (1 - abs(z)) + .025
        if abs(z) < .99 and abs(x) < width:
            return 0 if abs(x) < width * .3 else 1 if abs(x) < width * .75 else 2
    elif shape == "burst":
        limit = .46 + .49 * abs(math.cos(4 * a)) ** 10
        if .18 < r < limit:
            return 0 if r < .35 else 1 if r < limit * .78 else 2
    elif shape == "rune":
        if .82 < r < .96:
            return 0 if r > .92 else 1
        if abs(math.sin(3 * a)) < .10 and .38 < r < .82:
            return 0
        if .30 < r < .40:
            return 2
    elif shape == "bolt":
        center = .17 * math.sin(z * 13)
        if abs(z) < .98 and abs(x - center) < .09:
            return 0 if abs(x - center) < .035 else 1
    return None


SHAPES = ("crescent", "orbit", "star", "lance", "burst", "rune", "bolt")


def mesh(shape, palette):
    elements = []
    grid = [[cell(shape, (x + .5) / 24 - 1, (z + .5) / 24 - 1) for x in range(48)] for z in range(48)]
    for z, row in enumerate(grid):
        x = 0
        while x < 48:
            ink = row[x]
            end = x + 1
            while end < 48 and row[end] == ink:
                end += 1
            if ink is not None:
                bottom = z + 1
                while bottom < 48 and all(grid[bottom][i] == ink for i in range(x, end)):
                    bottom += 1
                for merged in range(z + 1, bottom):
                    for i in range(x, end):
                        grid[merged][i] = None
                elements.append({
                    "from": [x / 3, 7.75, z / 3], "to": [end / 3, 8.25, bottom / 3],
                    "shade": False,
                    "faces": {face: {"texture": f"#{ink}", "uv": [2, 2, 3, 3]} for face in ("up", "down", "north", "south", "east", "west")},
                })
            x = end
    if shape in ("lance", "bolt", "star"):
        # Cross-section stays visible from the side: beams are not paper-thin ribbons.
        # Swap X/Y for a spear, Y/Z for the star's perpendicular radiant plane.
        axis = (1, 0, 2) if shape != "star" else (0, 2, 1)
        crossed = [{**e, "from": [e["from"][i] for i in axis], "to": [e["to"][i] for i in axis]} for e in elements]
        elements += crossed
    return {"ambientocclusion": False, "textures": {str(i): f"minecraft:block/{texture}" for i, texture in enumerate(PALETTES[palette])}, "elements": elements}


def build_combat_models(assets, write_json):
    for shape in SHAPES:
        for palette in PALETTES:
            name = f"{shape}_{palette}"
            write_json(assets / f"models/combat_vfx/{name}.json", mesh(shape, palette))
            write_json(assets / f"items/combat_vfx/{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:combat_vfx/{name}"}})


if __name__ == "__main__":
    pack = Path(__file__).resolve().parents[1] / "server-minestom/src/main/resources/core-ui-pack"
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")
    build_combat_models(pack / "assets/projects", write)
    (pack / "index.txt").write_text("\n".join(sorted(str(p.relative_to(pack)).replace("\\", "/") for p in pack.rglob("*") if p.is_file() and p.name != "index.txt")) + "\n", encoding="utf-8")
