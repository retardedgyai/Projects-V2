"""Validate the authored cuboid models, bounds and source sprites against a vanilla client JAR."""
from pathlib import Path
import argparse
import json
import math
import zipfile
from build_core_weapon_assets import ASSETS, model


def verify(jar=None):
    names = set(zipfile.ZipFile(jar).namelist()) if jar else None
    for tier in range(1, 5):
        name = f"greatsword_t{tier}"
        definition = json.loads((ASSETS / f"items/weapons/{name}.json").read_text())
        assert definition == {"model": {"type": "minecraft:model", "model": f"projects:item/weapons/{name}"}}
        data = json.loads((ASSETS / f"models/item/weapons/{name}.json").read_text())
        assert data == model(tier), "Regenerate models after editing their source geometry"
        assert len(data["elements"]) <= 24
        for texture in data["textures"].values():
            assert texture.startswith("minecraft:block/")
            if names is not None:
                assert "assets/minecraft/textures/" + texture.split(":")[1] + ".png" in names, texture
        for element in data["elements"]:
            for a, b in zip(element["from"], element["to"]):
                assert -16 <= a < b <= 32
            assert set(element["faces"]) == {"north", "south", "east", "west", "up", "down"}
            for face in element["faces"].values():
                assert face["texture"][1:] in data["textures"]
            if "rotation" in element:
                rotation = element["rotation"]
                assert rotation["angle"] in (-45, -22.5, 0, 22.5, 45)
                assert rotation["axis"] == "z"
                radians = math.radians(rotation["angle"])
                for x in (element["from"][0], element["to"][0]):
                    for y in (element["from"][1], element["to"][1]):
                        dx, dy = x-rotation["origin"][0], y-rotation["origin"][1]
                        rx = rotation["origin"][0] + dx*math.cos(radians) - dy*math.sin(radians)
                        ry = rotation["origin"][1] + dx*math.sin(radians) + dy*math.cos(radians)
                        assert -16 <= rx <= 32 and -16 <= ry <= 32
        # Equipment icons must fit a stock 16 px inventory socket after the display transform.
        display = data["display"]["gui"]
        angle = math.radians(display["rotation"][2])
        for element in data["elements"]:
            for x in (element["from"][0], element["to"][0]):
                for y in (element["from"][1], element["to"][1]):
                    dx, dy = x-8, y-8
                    if "rotation" in element:
                        rotation = element["rotation"]
                        a = math.radians(rotation["angle"])
                        px, py = x-rotation["origin"][0], y-rotation["origin"][1]
                        dx = rotation["origin"][0] + px*math.cos(a) - py*math.sin(a) - 8
                        dy = rotation["origin"][1] + px*math.sin(a) + py*math.cos(a) - 8
                    gx = (dx*math.cos(angle) - dy*math.sin(angle))*display["scale"][0] + display["translation"][0]
                    gy = (dx*math.sin(angle) + dy*math.cos(angle))*display["scale"][1] + display["translation"][1]
                    assert -8 <= gx <= 8 and -8 <= gy <= 8, (name, element["name"], gx, gy)
    print("PASS: 4 greatsword models, stock 16px sockets, finite geometry, namespaced items" + (", all vanilla 26.2 source textures found" if names else " (pass --vanilla-jar for texture validation)"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--vanilla-jar", type=Path)
    verify(parser.parse_args().vanilla_jar)
