"""Render actual serialized Adventure Components using the selected pack's bitmap fonts.

Adaptation layer over Scorpius's glyph renderer; no copied HUD shader or color scheme.
Input: JSON array of Components. Output: PNG + missing-glyph diagnostics.
Not a client/shader screenshot; unsupported providers and missing vanilla glyphs need real-client QA.
"""
import argparse
import importlib.util
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def renderer_for(pack):
    file = ROOT / "vendor/scorpius/reference/bbmodel/render_lore.py"
    spec = importlib.util.spec_from_file_location("scorpius_glyphs", file)
    renderer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(renderer)
    renderer.PACK = Path(pack) / "assets"
    renderer.FONTS = renderer.FontSet()
    return renderer


def render_components(components, pack, output, font, width=800):
    renderer = renderer_for(pack)
    style = {"font": font, "color": "white", "bold": False}
    canvas = Image.new("RGBA", (width, 48 + len(components) * 44), (30, 28, 35, 255))
    ImageDraw.Draw(canvas).text((12, 8), "PACK GLYPH QA / NOT MINECRAFT", fill="white")
    missing = []
    for index, component in enumerate(components):
        renderer.draw_line(canvas, 8, 36 + index * 22, component, missing, style)
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(output)
    return sorted(set(missing), key=str)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("components", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--pack", type=Path, default=ROOT / "server-minestom/src/main/resources/core-ui-pack")
    parser.add_argument("--font", required=True)
    args = parser.parse_args()
    missing = render_components(json.loads(args.components.read_text(encoding="utf-8")), args.pack, args.output, args.font)
    print(args.output)
    for char, font in missing:
        print(f"MISSING U+{ord(char):04X} font={font}")
    if missing:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
