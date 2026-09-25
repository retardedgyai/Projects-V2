"""Compile pixel-relic sheets and forge materials into menu and item sprites.

The deterministic compiler slices 4x4 sheets for general menu symbols and uses
the dedicated 16 px material paintings for the forge and inventory. It never
downloads assets or changes a vanilla texture or gameplay item model.
"""
from pathlib import Path
import hashlib
import json
import math

from PIL import Image
from build_polish05_material_art import main as build_materials

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/core-ui/pixel-relic"
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
MATERIAL_ICONS = ROOT / "assets/core-ui/forge-v4/compiled"
ART_BASE = 0xE700
ART_CELL = 32
ART_YS = [18, 28, 30, 36, 42, 48, 54, 56, 70, 72, 84, 90, 98, 108, 112, 126, 140, 154, 168, 182, 196]
ART_SIZES = [16, 32, 48]
# Order is the wire contract with CoreMenuArt. Sources are row-major 4x4 sheets.
ART = [
    ("EXPEDITION", "materials", 0), ("FORGE", "materials", 1),
    ("STORAGE", "materials", 2), ("GEAR", "symbols", 10),
    ("GATHER", "materials", 5), ("HELP", "symbols", 5),
    ("TRIAL", "symbols", 3), ("RETURN", "symbols", 6),
    ("ENHANCE", "symbols", 7), ("REFINE", "symbols", 8),
    ("CRAFT", "symbols", 9), ("MOD", "symbols", 2),
    ("WEAPON", "materials", 3), ("ARMOR", "materials", 4),
    ("WOOD", "materials", 6), ("ORE", "materials", 7),
    ("STONE", "materials", 8), ("HIDE", "materials", 9),
    ("FIBER", "materials", 10), ("PLANK", "materials", 11),
    ("INGOT", "materials", 12), ("CUT_STONE", "materials", 13),
    ("LEATHER", "materials", 14), ("CLOTH", "materials", 15),
    ("POTION", "symbols", 0), ("TABLET", "symbols", 1),
    ("ORB", "symbols", 2), ("BOSS", "symbols", 3), ("SHARD", "symbols", 4),
    ("ARROW", "skills", 0), ("ARCANE", "skills", 1),
    ("DUST", "dust", 0),
]


def build_art():
    build_materials()
    sheets = {name: Image.open(SOURCE / f"source/{name}.png").convert("RGBA")
              for name in ("materials", "symbols")}
    atlas = Image.new("RGBA", (ART_CELL * 8, ART_CELL * 4))
    metadata, metrics = [], []
    for ordinal, (name, source, index) in enumerate(ART):
        material_key = {"PLANK": "board", "CUT_STONE": "cut_stone", "DUST": "affix_dust"}.get(name, name.lower())
        if material_key in {"wood", "ore", "stone", "hide", "fiber", "board", "ingot", "cut_stone", "leather", "cloth", "affix_dust"}:
            original = Image.open(MATERIAL_ICONS / f"{material_key}.png").convert("RGBA")
        elif source == "skills":
            master = Image.open(ROOT / f"assets/core-ui/skills/{('pierce','star_thread')[index]}.png").convert("RGBA")
            master = master.crop(master.getchannel('A').getbbox())
            master.thumbnail((28,28), Image.Resampling.NEAREST)
            original = Image.new('RGBA',(32,32))
            original.alpha_composite(master,((32-master.width)//2,(32-master.height)//2))
        else:
            sheet = sheets[source]
            assert abs(sheet.width - sheet.height) <= 2, "Source atlas must be square"
            w, h = sheet.width / 4, sheet.height / 4
            original = sheet.crop(tuple(round(value) for value in (
                (index % 4) * w, (index // 4) * h, (index % 4 + 1) * w, (index // 4 + 1) * h)))
        # Fixed-cell sampling preserves the authored grid; never stretch tight bounds
        # separately (that would make each material a different apparent scale).
        cell = original.resize((ART_CELL, ART_CELL), Image.Resampling.NEAREST)
        alpha = cell.getchannel("A").point(lambda value: 255 if value >= 128 else 0)
        cell = cell.convert("RGB").quantize(colors=24, method=Image.Quantize.MEDIANCUT).convert("RGBA")
        cell.putalpha(alpha)
        bounds = alpha.getbbox()
        assert bounds is not None, f"Empty menu artwork: {name}"
        advances = [math.floor(0.5 + bounds[2] * size / ART_CELL) + 1 for size in ART_SIZES]
        atlas.alpha_composite(cell, (ordinal % 8 * ART_CELL, ordinal // 8 * ART_CELL))
        metrics.append(f"{name}\t{ordinal}\t" + "\t".join(map(str, advances)) + "\n")
        metadata.append({"name": name, "ordinal": ordinal, "source": source,
                         "source_cell": index, "advances": dict(zip(map(str, ART_SIZES), advances))})
    destination = ASSETS / "textures/gui/core/menu_art.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(destination, optimize=True)
    grid = ["".join(chr(ART_BASE + row * 8 + column) for column in range(8)) for row in range(4)]
    for size in ART_SIZES:
        for y in ART_YS:
            font = {"providers": [{"type": "bitmap", "file": "projects:gui/core/menu_art.png",
                                   "height": size, "ascent": 13 - y, "chars": grid}]}
            (ASSETS / f"font/core_menu_art_{size}_{y}.json").write_text(
                json.dumps(font, separators=(",", ":")) + "\n", encoding="utf-8")
    (ASSETS / "menu/art.tsv").write_text("# name\tordinal\tadvance16\tadvance32\tadvance48\n" + "".join(metrics), encoding="utf-8")
    # Give storage projections and the forge exactly the same low-resolution
    # original artwork. Their models are scoped to ProjectS items only.
    subjects = {"wood": "WOOD", "ore": "ORE", "stone": "STONE", "hide": "HIDE",
                "fiber": "FIBER", "board": "PLANK", "ingot": "INGOT",
                "cut_stone": "CUT_STONE", "leather": "LEATHER", "cloth": "CLOTH",
                "affix_dust": "DUST"}
    for key, art_name in subjects.items():
        ordinal = next(index for index, art in enumerate(ART) if art[0] == art_name)
        cell = atlas.crop((ordinal % 8 * ART_CELL, ordinal // 8 * ART_CELL,
                           (ordinal % 8 + 1) * ART_CELL, (ordinal // 8 + 1) * ART_CELL))
        texture = ASSETS / f"textures/item/forge_materials/{key}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        Image.open(MATERIAL_ICONS / f"{key}.png").save(texture, optimize=True)
        item = ASSETS / f"items/forge_materials/{key}.json"
        item.parent.mkdir(parents=True, exist_ok=True)
        item.write_text(json.dumps({"model": {"type": "minecraft:model",
            "model": f"projects:item/forge_materials/{key}"}}, separators=(",", ":")) + "\n", encoding="utf-8")
        model = ASSETS / f"models/item/forge_materials/{key}.json"
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps({"parent": "minecraft:item/generated",
            "textures": {"layer0": f"projects:item/forge_materials/{key}"}}, separators=(",", ":")) + "\n", encoding="utf-8")
    (SOURCE / "atlas.json").write_text(json.dumps({
        "cell": ART_CELL, "columns": 8, "sizes": ART_SIZES, "ys": ART_YS, "art": metadata,
        "sources": {**{name: hashlib.sha256((SOURCE / f"source/{name}.png").read_bytes()).hexdigest()
                    for name in sheets},
                    **{f"forge_v4_{name}": hashlib.sha256(
                        (MATERIAL_ICONS / f"{name}.png").read_bytes()).hexdigest()
                        for name in subjects}},
        "build": "ProjectS material source at 16px; atlas uses nearest-neighbor 32px; binary-alpha inventory sprites",
    }, indent=2) + "\n", encoding="utf-8")
    atlas.resize((1024, 512), Image.Resampling.NEAREST).save(SOURCE / "atlas-preview.png", optimize=True)
    pack = ASSETS.parents[1]
    paths = sorted(path.relative_to(pack).as_posix() for path in pack.rglob("*")
                   if path.is_file() and path.name != "index.txt")
    (pack / "index.txt").write_text("\n".join(paths) + "\n", encoding="utf-8")
    print(f"Built {len(ART)} original pixel-relic icons; {len(ART_SIZES) * len(ART_YS)} positioned fonts")


if __name__ == "__main__":
    build_art()
