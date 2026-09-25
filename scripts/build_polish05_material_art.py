"""Compile ProjectS's material paintings at Minecraft's 16 px item density."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/core-ui/pixel-relic/source"
BASE = ROOT / "assets/core-ui/forge-v3-existing"
COMPILED = BASE / "compiled"
# The first six atlas cells are scene and equipment art; material cells are 6-15.
MATERIALS = {
    "wood": 6, "board": 11, "ore": 7, "ingot": 12,
    "stone": 8, "cut_stone": 13, "hide": 9, "leather": 14,
    "fiber": 10, "cloth": 15,
}
ORDER = ("wood", "board", "ore", "ingot", "stone", "cut_stone",
         "hide", "leather", "fiber", "cloth", "affix_dust")


def finish(cell: Image.Image) -> Image.Image:
    alpha = cell.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    rgb = cell.convert("RGB").quantize(colors=12, method=Image.Quantize.MEDIANCUT).convert("RGBA")
    rgb.putalpha(alpha)
    return rgb


def main() -> None:
    sheet = Image.open(SOURCE / "materials.png").convert("RGBA")
    assert abs(sheet.width - sheet.height) <= 2
    step_x, step_y = sheet.width / 4, sheet.height / 4
    COMPILED.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGBA", (5 * 144, 3 * 144), "#1b2123")
    for slot, name in enumerate(ORDER):
        if name == "affix_dust":
            dust = Image.open(SOURCE / "affix_dust.png").convert("RGBA")
            bounds = dust.getchannel("A").point(lambda a: 255 if a >= 128 else 0).getbbox()
            assert bounds is not None
            dust = dust.crop(bounds)
            dust.thumbnail((14, 14), Image.Resampling.NEAREST)
            cell = Image.new("RGBA", (16, 16))
            cell.alpha_composite(dust, ((16 - dust.width) // 2, (16 - dust.height) // 2))
        else:
            index = MATERIALS[name]
            col, row = index % 4, index // 4
            cell = sheet.crop(tuple(round(v) for v in (col * step_x, row * step_y,
                (col + 1) * step_x, (row + 1) * step_y))).resize((16, 16), Image.Resampling.NEAREST)
        icon = finish(cell)
        assert icon.getchannel("A").getbbox(), name
        icon.save(COMPILED / f"{name}.png", optimize=True)
        preview.alpha_composite(icon.resize((112, 112), Image.Resampling.NEAREST),
                                ((slot % 5) * 144 + 16, (slot // 5) * 144 + 12))
    preview.save(BASE / "preview.png", optimize=True)
    print(f"POLISH05_MATERIAL_ART {len(ORDER)} icons at 16x16")


if __name__ == "__main__":
    main()
