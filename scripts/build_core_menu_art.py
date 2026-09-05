"""Compile original pixel-relic source sheets into slot-sized, namespaced menu sprites.

The source artwork is produced with the built-in image generator; this deterministic
asset compiler only slices the agreed 4x4 sheets and prepares Minecraft-sized cells.
It never downloads assets and never changes a vanilla texture or gameplay item model.
"""
from pathlib import Path
import hashlib
import json
import math

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/core-ui/pixel-relic"
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
ART_BASE = 0xE700
ART_CELL = 32
ART_YS = [18, 28, 30, 36, 42, 54, 56, 70, 72, 84, 90, 98, 108, 112, 126, 140, 154, 168, 182, 196]
ART_SIZES = [16, 32]
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
]


def build_art():
    sheets = {name: Image.open(SOURCE / f"source/{name}.png").convert("RGBA")
              for name in ("materials", "symbols")}
    atlas = Image.new("RGBA", (ART_CELL * 8, ART_CELL * 4))
    metadata, metrics = [], []
    for ordinal, (name, source, index) in enumerate(ART):
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
        metrics.append(f"{name}\t{ordinal}\t{advances[0]}\t{advances[1]}\n")
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
    (ASSETS / "menu/art.tsv").write_text("# name\tordinal\tadvance16\tadvance32\n" + "".join(metrics), encoding="utf-8")
    (SOURCE / "atlas.json").write_text(json.dumps({
        "cell": ART_CELL, "columns": 8, "sizes": ART_SIZES, "ys": ART_YS, "art": metadata,
        "sources": {name: hashlib.sha256((SOURCE / f"source/{name}.png").read_bytes()).hexdigest()
                    for name in sheets},
        "build": "4x4 cell slicing; nearest-neighbor 32px; binary alpha; 24-color palette per sprite",
    }, indent=2) + "\n", encoding="utf-8")
    atlas.resize((1024, 512), Image.Resampling.NEAREST).save(SOURCE / "atlas-preview.png", optimize=True)
    print(f"Built {len(ART)} original pixel-relic icons; {len(ART_SIZES) * len(ART_YS)} positioned fonts")


if __name__ == "__main__":
    build_art()
