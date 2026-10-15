"""Pack the 魔術書 (grimoire) screen assets.

    python scripts/build_grimoire_assets.py

Reads assets/ui/grimoire/source/*.png (item icons and illustrations) and writes
server-minestom/src/main/resources/polish05/grimoire.zip (private-namespace sprites, merged into
the UI pack by CoreUiPackServer) and polish05/grimoire.json (sprite chars and tile layouts).

Images wider or taller than 256 px are split into tiles because larger font glyphs do not render.
Alpha is snapped to fully opaque or fully clear: a translucent glyph hides what is drawn after it.
The one exception is a single alpha-1 pixel that keeps a tile's advance at its full width.
"""
import io, json, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/grimoire/source"
OUT = ROOT / "server-minestom/src/main/resources/polish05"
NS = "projects_ui_polish05"
FONT = f"{NS}:grimoire"
TILE = 256


def snap_alpha(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    alpha = image.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    image.putalpha(alpha)
    return image


def main() -> None:
    files: dict[str, bytes] = {}
    providers: list[dict] = []
    sprites: dict[str, dict] = {}
    layouts: dict[str, dict] = {}
    code = [0xE000]

    def add(name: str, image: Image.Image) -> None:
        assert image.width <= TILE and image.height <= TILE, (name, image.size)
        # Vanilla measures a bitmap glyph's advance up to its last non-clear column, and the UI centres
        # glyphs by that advance: a tile whose right edge is clear would shift right and leave a gap.
        alpha = image.getchannel("A")
        if not any(alpha.getpixel((image.width - 1, y)) for y in range(image.height)):
            image = image.copy()
            r, g, b, _ = image.getpixel((image.width - 1, 0))
            image.putpixel((image.width - 1, 0), (r, g, b, 1))
        char = chr(code[0]); code[0] += 1
        rel = f"grimoire/{name}.png"
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height, "height": image.height, "chars": [char]})
        sprites[name] = {"char": char, "font": FONT, "width": image.width, "height": image.height}

    for path in sorted(SOURCE.glob("*.png")):
        image = snap_alpha(Image.open(path))
        name = path.stem
        if image.width <= TILE and image.height <= TILE:
            add(name, image)
            continue
        parts = []
        for y in range(0, image.height, TILE):
            for x in range(0, image.width, TILE):
                part = f"{name}_{x}_{y}"
                add(part, image.crop((x, y, min(x + TILE, image.width), min(y + TILE, image.height))))
                parts.append({"name": part, "x": x, "y": y})
        layouts[name] = {"width": image.width, "height": image.height, "tiles": parts}
    files[f"assets/{NS}/font/grimoire.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()

    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT / "grimoire.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    (OUT / "grimoire.json").write_text(json.dumps({"sprites": sprites, "layouts": layouts}, ensure_ascii=False), encoding="utf-8")
    print(f"GRIMOIRE_PACK_BUILT sprites={len(sprites)} layouts={len(layouts)} bytes={(OUT / 'grimoire.zip').stat().st_size}")


if __name__ == "__main__":
    main()
