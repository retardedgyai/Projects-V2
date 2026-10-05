"""Pack the 開拓大陸 map screen assets.

    python scripts/build_atlas_assets.py

Reads assets/ui/atlas (continent.png, zones.json, icons/*.png) and writes
server-minestom/src/main/resources/polish05/atlas.zip (private-namespace sprites, merged into the
UI pack by CoreUiPackServer) and polish05/atlas-map.json (sprite chars + zone layout).
"""
import io, json, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/atlas"
OUT = ROOT / "server-minestom/src/main/resources/polish05"
NS = "projects_ui_polish05"
FONT = f"{NS}:atlas"
TILE = 256


def cloud() -> Image.Image:
    """A puffy pixel cloud (4px blocks, like the map), lit from above."""
    import math, random
    # Font glyphs larger than 256px do not render, so the cloud is drawn at 60x33 and scaled 4x (240x132).
    w, h = 60, 33
    blobs = [(30, 20, 12), (18, 21, 9), (42, 21, 10), (24, 14, 8), (37, 13, 9), (9, 24, 6), (51, 24, 7)]
    small = Image.new("RGBA", (w, h))
    px = small.load()
    for y in range(h):
        for x in range(w):
            inside = [b for b in blobs if math.hypot(x - b[0], (y - b[1]) * 1.15) < b[2]]
            if not inside or y > 28:
                continue
            top = min(b[1] - b[2] for b in inside)
            shade = (y - top) / 20
            base = 255 - int(min(1.0, max(0.0, shade)) * 70)
            px[x, y] = (base, base, min(255, base + 12), 255)
    image = small.resize((w * 4, h * 4), Image.NEAREST)
    assert image.width <= 256 and image.height <= 256
    return image


def banner() -> Image.Image:
    """A white pixel banner on a pole; tinted per guild with sprite-color."""
    rows = [
        "kkkkkkkkkkkk",
        "kwwwwwwwwwwk",
        "kwwwwwwwwwwk",
        "kwwsswwsswwk",
        "kwwwssssswwk",
        "kwwwwsswwwwk",
        "kwwwwwwwwwwk",
        "kwwwwwwwwwwk",
        "kwwwwwwwwwwk",
        "kwwwwkkwwwwk",
        "kwwwk..kwwwk",
        "kwwk....kwwk",
        "kkk......kkk",
    ]
    img = Image.new("RGBA", (12, 13))
    pal = {"k": (40, 34, 40, 255), "w": (255, 255, 255, 255), "s": (200, 200, 200, 255)}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal: img.putpixel((x, y), pal[ch])
    return img.resize((48, 52), Image.NEAREST)


def main() -> None:
    files: dict[str, bytes] = {}
    providers: list[dict] = []
    sprites: dict[str, dict] = {}
    code = [0xE000]

    def add(name: str, image: Image.Image) -> None:
        assert image.width <= 256 and image.height <= 256, (name, image.size)
        char = chr(code[0]); code[0] += 1
        rel = f"atlas/{name}.png"
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height, "height": image.height, "chars": [char]})
        sprites[name] = {"char": char, "font": FONT, "width": image.width, "height": image.height}

    continent = Image.open(SOURCE / "continent.png").convert("RGBA")
    tiles = []
    for y in range(0, continent.height, TILE):
        for x in range(0, continent.width, TILE):
            name = f"tile_{x}_{y}"
            add(name, continent.crop((x, y, min(x + TILE, continent.width), min(y + TILE, continent.height))))
            tiles.append({"name": name, "x": x, "y": y})
    add("fx_cloud", cloud())
    add("fx_banner", banner())
    for icon in sorted((SOURCE / "icons").glob("*.png")):
        add(f"icon_{icon.stem}", Image.open(icon).convert("RGBA"))
    files[f"assets/{NS}/font/atlas.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()

    with zipfile.ZipFile(OUT / "atlas.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    zones = json.loads((SOURCE / "zones.json").read_text(encoding="utf-8"))
    (OUT / "atlas-map.json").write_text(json.dumps({"width": continent.width, "height": continent.height, "tiles": tiles,
                                                    "sprites": sprites, "zones": zones["zones"]}, ensure_ascii=False), encoding="utf-8")
    print(f"ATLAS_PACK_BUILT tiles={len(tiles)} sprites={len(sprites)} bytes={(OUT / 'atlas.zip').stat().st_size}")


if __name__ == "__main__":
    main()
