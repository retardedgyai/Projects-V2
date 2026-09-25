"""Build small hex frames around existing item art for the research board.

The frames are UI geometry; the elemental illustrations remain the existing
First Magic artwork or Minecraft's item sprites. No accepted art is repainted.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
TEXTURES = PACK / "assets/projects/textures/item/first_magic"
MODELS = PACK / "assets/projects/models/first_magic"
ITEMS = PACK / "assets/projects/items/first_magic"
INDEX = PACK / "index.txt"

# Illustration sources and frame colors correspond to AspectCatalog's stable IDs.
ASPECTS = {
    "aer": ("projects:item/first_magic/gale", 0xDBE8AF),
    "aqua": ("projects:item/first_magic/tide", 0x78CADB),
    "ignis": ("projects:item/first_magic/ember", 0xEBA16B),
    "terra": ("projects:item/first_magic/stone", 0xA9B889),
    "ordo": ("minecraft:item/quartz", 0xE8D9B4),
    "perditio": ("minecraft:item/echo_shard", 0xB9A0C8),
    "lux": ("minecraft:item/glowstone_dust", 0xF3DB91),
    "tempestas": ("minecraft:item/prismarine_crystals", 0xA4D5D7),
    "motus": ("minecraft:item/feather", 0xD6C7AA),
    "vacuos": ("minecraft:item/ender_pearl", 0xAD9CC8),
    "victus": ("minecraft:item/glistering_melon_slice", 0xB5D89F),
    "gelum": ("minecraft:item/snowball", 0xB5DFE9),
    "venenum": ("minecraft:item/poisonous_potato", 0xB8C687),
    "potentia": ("minecraft:item/blaze_powder", 0xE9BB86),
    "mortuus": ("minecraft:item/bone", 0xA89FAD),
    "metallum": ("minecraft:item/iron_ingot", 0xC8B79B),
}


def frame(color: int, empty: bool = False) -> Image.Image:
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    outer = [(8, 0), (14, 3), (14, 12), (8, 15), (2, 12), (2, 3)]
    inner = [(8, 2), (12, 4), (12, 11), (8, 13), (4, 11), (4, 4)]
    draw.polygon(outer, fill=(10, 19, 24, 175 if empty else 105))
    draw.line(outer + [outer[0]], fill=((color >> 16) & 255, (color >> 8) & 255, color & 255, 255), width=1)
    draw.line(inner + [inner[0]], fill=(235, 223, 192, 90 if empty else 55), width=1)
    draw.point((8, 0), fill=(255, 245, 218, 255))
    draw.point((8, 15), fill=(67, 53, 42, 255))
    return image


def write_json(path: Path, content: dict) -> None:
    path.write_text(json.dumps(content, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def main() -> None:
    entries = set(INDEX.read_text(encoding="utf-8").splitlines())
    for aspect, (source, color) in ASPECTS.items():
        name = f"research_{aspect}"
        frame(color).save(TEXTURES / f"{name}.png")
        write_json(MODELS / f"{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": source, "layer1": f"projects:item/first_magic/{name}"},
        })
        write_json(ITEMS / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:first_magic/{name}"}})
        entries.update({
            f"assets/projects/textures/item/first_magic/{name}.png",
            f"assets/projects/models/first_magic/{name}.json",
            f"assets/projects/items/first_magic/{name}.json",
        })
    name = "research_empty"
    frame(0x7A8F8E, empty=True).save(TEXTURES / f"{name}.png")
    write_json(MODELS / f"{name}.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"projects:item/first_magic/{name}"},
    })
    write_json(ITEMS / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:first_magic/{name}"}})
    entries.update({
        f"assets/projects/textures/item/first_magic/{name}.png",
        f"assets/projects/models/first_magic/{name}.json",
        f"assets/projects/items/first_magic/{name}.json",
    })
    INDEX.write_text("\n".join(sorted(entries)) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
