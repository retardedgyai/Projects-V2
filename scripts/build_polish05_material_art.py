"""Publish the ProjectS 16 px material paintings to both UI resource packs."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "assets/core-ui/forge-v4"
SOURCE = BASE / "source"
COMPILED = BASE / "compiled"
ORDER = ("wood", "board", "ore", "ingot", "stone", "cut_stone",
         "hide", "leather", "fiber", "cloth", "affix_dust")


def main() -> None:
    COMPILED.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGBA", (6 * 144, 2 * 144), "#1b2123")
    for slot, name in enumerate(ORDER):
        icon = Image.open(SOURCE / f"{name}.png").convert("RGBA")
        assert icon.size == (16, 16), name
        assert icon.getchannel("A").getbbox(), name
        assert set(icon.getchannel("A").getdata()) <= {0, 255}, name
        icon.save(COMPILED / f"{name}.png", optimize=True)
        preview.alpha_composite(icon.resize((112, 112), Image.Resampling.NEAREST),
                                ((slot % 6) * 144 + 16, (slot // 6) * 144 + 12))
    preview.save(BASE / "preview.png", optimize=True)
    print(f"POLISH05_MATERIAL_ART {len(ORDER)} icons at 16x16")


if __name__ == "__main__":
    main()
