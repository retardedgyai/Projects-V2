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


def pixelize(image: Image.Image, factor: int = 3, colors: int = 20) -> Image.Image:
    """Turn a smooth vector part into pixel art: box-downsample, hard alpha, few colours, dark outline, nearest upscale."""
    w, h = image.width // factor, image.height // factor
    small = image.resize((w, h), Image.BOX)
    alpha = small.getchannel("A").point(lambda a: 255 if a >= 110 else 0)
    rgb = small.convert("RGB").quantize(colors=colors, method=Image.Quantize.MEDIANCUT).convert("RGB")
    out = Image.new("RGBA", (w, h))
    out.paste(rgb, mask=alpha)
    px = out.load(); src = alpha.load()
    for y in range(h):
        for x in range(w):
            if src[x, y]: continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (14, 11, 16, 255)
    return out.resize((w * factor, h * factor), Image.NEAREST)


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
    parts = Image.open(SOURCE / "parts.png").convert("RGBA")
    panel = parts.crop((0, 0, 432, 1032))
    for y in range(0, panel.height, TILE):
        for x in range(0, panel.width, TILE):
            add(f"panel_{x}_{y}", panel.crop((x, y, min(x + TILE, panel.width), min(y + TILE, panel.height))))
    # Hand-placed pixel art (scripts/atlas_pixel_icons.py), doubled so each art pixel stays square on screen.
    for f in sorted((SOURCE / "pixel").glob("*.png")):
        image = Image.open(f).convert("RGBA")
        add(f.stem, image.resize((image.width * 2, image.height * 2), Image.NEAREST))
    add("plate", parts.crop((940, 0, 1060, 40)))
    card = parts.crop((460, 360, 836, 432))
    add("card_0", card.crop((0, 0, 256, 72))); add("card_1", card.crop((256, 0, 376, 72)))
    add("title", parts.crop((460, 460, 716, 524)))
    add("notice_l", parts.crop((740, 460, 796, 516))); add("notice_m", parts.crop((800, 460, 832, 516))); add("notice_r", parts.crop((840, 460, 864, 516)))
    add("vignette", parts.crop((460, 560, 716, 704)))
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


def render() -> None:
    """Rasterise atlas-ui.html with headless Chrome into parts.png (transparent background)."""
    import subprocess, sys
    sys.path.insert(0, str(ROOT / "scripts"))
    from build_forge_v3_assets import chrome_exe
    out = SOURCE / "parts.png"
    subprocess.run([chrome_exe(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    "--default-background-color=00000000", "--window-size=1100,1100", f"--screenshot={out}",
                    (SOURCE / "atlas-ui.html").as_uri()], check=True, capture_output=True, timeout=120)
    print(f"ATLAS_PARTS_RENDERED {out}")


if __name__ == "__main__":
    import sys
    if sys.argv[1:] == ["render"]:
        render()
    main()
