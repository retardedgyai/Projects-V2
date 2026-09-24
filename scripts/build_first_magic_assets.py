"""Author the first-magic pack assets. Original pixel art and cuboid models; no vanilla item swaps.

Run with `python scripts/build_first_magic_assets.py`. Only writes projects:first_magic
assets and two dedicated font glyphs, then updates the pack's explicit index.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
ASSET = PACK / "assets/projects"
MAGIC = "first_magic"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def item_icons() -> None:
    names = ("ember", "tide", "gale", "stone", "moonbell", "ember_moss", "hollow_crystal",
             "warm_ore", "tidewing_feather", "withered_core", "desk", "distiller", "jar",
             "journal", "return", "sealed")
    for name in names:
        source = ROOT / f"assets/first-magic/source-sprites/{name}.png"
        if not source.is_file():
            raise FileNotFoundError(f"Every first-magic icon needs its own original source: {source}")
        source_image = Image.open(source).convert("RGBA")
        bounds = source_image.getbbox()
        if not bounds:
            raise ValueError(f"Empty source sprite: {source}")
        crop = source_image.crop(bounds)
        # Keep the hand-pixelled source's harder boundaries. The source
        # remains in the repo for revision; only the tiny raster ships.
        crop.thumbnail((30, 30), Image.Resampling.NEAREST)
        sprite = Image.new("RGBA", (32, 32))
        sprite.alpha_composite(crop, ((32-crop.width)//2, (32-crop.height)//2))
        save(sprite, ASSET / f"textures/item/{MAGIC}/{name}.png")
        write_json(ASSET / f"models/{MAGIC}/icon_{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"projects:item/{MAGIC}/{name}"}})
        write_json(ASSET / f"items/{MAGIC}/icon_{name}.json", {
            "model": {"type": "minecraft:model", "model": f"projects:{MAGIC}/icon_{name}"}})
    sheet = Image.new("RGBA", (8 * 96, 2 * 96), "#23292b")
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        x, y = index % 8 * 96, index // 8 * 96
        sprite = Image.open(ASSET / f"textures/item/{MAGIC}/{name}.png").convert("RGBA")
        sheet.alpha_composite(sprite.resize((64, 64), Image.Resampling.NEAREST), (x + 16, y))
        draw.text((x + 3, y + 72), name, fill="#e7dac0")
    save(sheet, ROOT / "assets/first-magic/icon-sheet.png")


def preview() -> None:
    """Review sheet at native 1x size; never loaded as the in-game UI."""
    font_file = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"
    if not font_file.is_file():
        return
    left = Image.open(ASSET / "textures/gui/first_magic/desk_0.png").convert("RGBA")
    right = Image.open(ASSET / "textures/gui/first_magic/desk_1.png").convert("RGBA")
    im = Image.new("RGBA", (384, 222), "#171d21")
    im.alpha_composite(left, (0, 0)); im.alpha_composite(right, (192, 0))
    d = ImageDraw.Draw(im)
    font = ImageFont.truetype(str(font_file), 11)
    def label(x, y, words, fill="#e5d4b6"):
        d.text((x, y - 1), words, font=font, fill=fill, stroke_width=0)
    label(112, 5, "観測工房・研究机")
    label(6, 7, "観測手順"); label(288, 7, "古い観測工房")
    for i, words in enumerate(("01 素材を持ち帰る", "02 観測盤を修復", "03 素材を分析", "04 記録帳へ残す", "", "素材 2 / 6", "性質 2 / 4")):
        label(6, 30 + 14 * i, words, "#d5caba")
    for i, words in enumerate(("観測盤は起動中", "", "紙上の小さな星図は", "持ち帰った未知に応える", "", "素材を選ぶと分析", "素材は消費しない")):
        label(288, 30 + 14 * i, words, "#d5caba")
    icon_slots = {4: "journal", 10: "moonbell", 12: "ember_moss", 14: "hollow_crystal",
                  22: "desk", 28: "warm_ore", 30: "tidewing_feather", 32: "withered_core",
                  37: "sealed", 38: "tide", 40: "journal", 42: "gale", 43: "sealed",
                  49: "distiller", 53: "return"}
    for slot, name in icon_slots.items():
        x, y = 112 + slot % 9 * 18, 18 + slot // 9 * 18
        small = Image.open(ASSET / f"textures/item/{MAGIC}/{name}.png").convert("RGBA").resize((16, 16), Image.Resampling.NEAREST)
        im.alpha_composite(small, (x, y))
    save(im, ROOT / "assets/first-magic/workshop-screen-preview.png")


def build() -> None:
    from first_magic_art_v2 import build as build_models_v2, build_ui
    build_models_v2(ASSET, write_json)
    item_icons(); build_ui(ROOT, ASSET, write_json); preview()
    paths = sorted(str(path.relative_to(PACK)).replace("\\", "/") for path in PACK.rglob("*") if path.is_file() and path.name != "index.txt")
    (PACK / "index.txt").write_text("\n".join(paths) + "\n", encoding="utf-8")
    print(f"First magic: {len(list((ASSET / 'items/first_magic').glob('*.json')))} item models, {len(paths)} packed files")


if __name__ == "__main__":
    build()
