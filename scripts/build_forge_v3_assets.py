"""Build the harbor forge v3 assets from the approved Forge3 design artboard.

    python scripts/build_forge_v3_assets.py fonts --source <dir with MPLUS2[wght].ttf as MPLUS2.ttf and DotGothic16-Regular.ttf>
    python scripts/build_forge_v3_assets.py render      # needs Chrome; refreshes assets/ui/forge-v3/chrome.png and measure.json
    python scripts/build_forge_v3_assets.py pack        # adds v3 fonts and plates to the Polish05 private pack

`fonts` subsets the OFL sources to the glyphs the forge can show. `render` draws the artboard in headless
Chrome: chrome.png keeps only what never changes (background, panels, fixed labels); measure.json records
every element's box for the native layout. `pack` rasterises the fonts to bitmap pages whose glyph widths
reproduce the browser advances, tiles chrome.png, and adds the tintable shapes used by ForgeV3Scene.
"""
from __future__ import annotations

import argparse
import io
import json
import math
import os
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/forge-v3"
FONTS = ROOT / "web-ui-lab/ui/forge-v3-fonts"
BUILD = ROOT / "web-ui-lab/build/forge-v3"
PACK = ROOT / "server-minestom/src/main/resources/polish05/pack.zip"
MAP = ROOT / "server-minestom/src/main/resources/polish05/font-map.json"
LAB_MAP = ROOT / "web-ui-lab/ui/polish05-font-map.json"
METRICS = ROOT / "web-ui-lab/src/main/resources/polish05-font-metrics.json"
NS = "projects_ui_polish05"
GLYPH_SOURCES = [
    SOURCE / "Forge3.dc.html",
    ROOT / "web-ui-lab/src/main/kotlin/dev/projects/webui/ForgeV3Scene.kt",
    *(ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop" / f for f in (
        "CorePolish05ForgeFlow.kt", "CoreAccount.kt", "CoreEnhancementCatalog.kt", "CoreAffixCatalog.kt",
        "CoreJourney.kt", "CoreCraftingCurrency.kt", "CoreEconomy.kt")),
]
# Glyph pages per family and CSS pixel size. Vanilla samples font textures without mipmaps, so each size the
# artboard uses gets a page rasterised at exactly that size: at 1080p one artboard pixel is one screen pixel.
# Every page declares the same 32-unit metrics, so the layout code treats all sizes alike.
SOURCES = {"r": ("MPLUS2.ttf", 400, False), "m": ("MPLUS2.ttf", 500, False),
           "b": ("MPLUS2.ttf", 700, False), "num": ("DotGothic16.ttf", None, True)}
SIZES = {"r": (11, 12, 13, 14, 15), "m": (12, 14, 15, 18), "b": (12, 16, 20, 24, 26, 44),
         "num": (11, 12, 13, 14, 19, 20, 21, 24, 26, 28, 40, 56, 64, 84)}
FAMILIES = {f"v3{key}": (*SOURCES[key], 32) for key in SOURCES}
FAMILIES.update({f"v3{key}{size}": (*SOURCES[key], size) for key in SIZES for size in SIZES[key]})
NUMERIC = set("0123456789+-.,%/:— 〜なしRankShift")
BLOBS = {
    "a10adfae1d18be0fb18e71a12fe9bc9f": "warrior_t2_helmet", "84bd09111b33a4bf17c0a50268dae3d3": "warrior_t2_chestplate",
    "dc7f9ac61123f6c041edfc28045d1237": "warrior_t2_leggings", "330c01ece8f5cfe7db66263d51240fdf": "warrior_t2_boots",
    "44a1a7161e02f5dec8ddd53d1727f3da": "ingot", "88677c8ff023c49dbc43062b4bc9d03d": "board",
    "4cdd55a3fc31a3e653ba2d84e2167628": "cut_stone", "f8fb12fde50a6fb7b87384bd1f2eaf9b": "leather",
    "39a592a83564420ea58353214906186e": "cloth", "6f72e2c9c8cd497b1c0330ad7b17f526": "affix_dust",
    "5c89d10a271a27d7419e3db5bf27b98d": "sword_hero",
}


def glyphs() -> list[str]:
    chars = {chr(c) for c in range(33, 127)}
    for source in GLYPH_SOURCES:
        chars.update(c for c in source.read_text(encoding="utf-8") if ord(c) >= 127 and not c.isspace())
    return sorted(c for c in chars if 0xE000 > ord(c) or ord(c) > 0xF8FF)


def cmd_fonts(source: Path) -> None:
    from fontTools import subset
    from fontTools.ttLib import TTFont
    FONTS.mkdir(parents=True, exist_ok=True)
    wanted = {ord(c) for c in glyphs()} | {32}
    for name in ("MPLUS2.ttf", "DotGothic16.ttf"):
        src = source / ("DotGothic16-Regular.ttf" if name.startswith("Dot") else name)
        font = TTFont(src)
        options = subset.Options()
        options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
        options.name_languages = [0x409]
        options.layout_features = ["kern", "palt"]
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=wanted & font.getBestCmap().keys())
        sub.subset(font)
        font.save(FONTS / name)
        print(f"FORGE_V3_FONT_SUBSET {name} glyphs={len(font.getBestCmap())} bytes={(FONTS / name).stat().st_size}")


def chrome_exe() -> str:
    for candidate in (os.environ.get("CHROME"), r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                      r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", "google-chrome", "chromium"):
        if candidate and (Path(candidate).exists() or shutil.which(candidate)):
            return candidate
    raise SystemExit("Chrome or Edge is required for render")


def write_page() -> Path:
    (BUILD / "fonts").mkdir(parents=True, exist_ok=True)
    shutil.copytree(SOURCE / "img", BUILD / "img", dirs_exist_ok=True)
    shutil.copy2(FONTS / "MPLUS2.ttf", BUILD / "fonts/MPLUS2.ttf")
    shutil.copy2(FONTS / "DotGothic16.ttf", BUILD / "fonts/DotGothic16.ttf")
    src = (SOURCE / "Forge3.dc.html").read_text(encoding="utf-8")
    for blob, name in BLOBS.items():
        src = src.replace(f"/_blob/{blob}", f"img/{name}.png")
    style = re.search(r"<helmet>.*?<style>(.*?)</style>.*?</helmet>", src, re.S).group(1)
    body = re.search(r"</helmet>(.*)</x-dc>", src, re.S).group(1)
    logic = re.search(r'data-dc-script[^>]*>(.*?)</script>', src, re.S).group(1)
    runtime = (Path(__file__).with_name("forge_v3_runtime.js")).read_text(encoding="utf-8")
    page = ("<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><style>"
            "@font-face{font-family:'M PLUS 2';src:url('fonts/MPLUS2.ttf');font-weight:100 900}"
            "@font-face{font-family:'DotGothic16';src:url('fonts/DotGothic16.ttf')}"
            + style + "#out{display:none}</style></head><body>"
            f"<template id=\"tpl\">{body}</template><div id=\"root\"></div><pre id=\"out\"></pre>"
            "<script>class DCLogic{constructor(p){this.props=p||{};this.state={}}setState(p){Object.assign(this.state,p)}}\n"
            + logic + "\n" + runtime + "</script></body></html>")
    out = BUILD / "page.html"
    out.write_text(page, encoding="utf-8")
    return out


def chrome(args: list[str], url: str) -> str:
    base = [chrome_exe(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
            "--force-device-scale-factor=1", "--window-size=1920,1080", "--virtual-time-budget=6000"]
    return subprocess.run(base + args + [url], capture_output=True, text=True, encoding="utf-8", timeout=120).stdout


def cmd_render() -> None:
    page = write_page()
    url = page.resolve().as_uri()
    chrome([f"--screenshot={SOURCE / 'chrome.png'}"], url + "#chrome")
    chrome([f"--screenshot={BUILD / 'full.png'}"], url + "#full")
    dom = chrome(["--dump-dom"], url + "#measure")
    data = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S).group(1)
    import html
    items = json.loads(html.unescape(data))
    (SOURCE / "measure.json").write_text(json.dumps(items, ensure_ascii=False, indent=0), encoding="utf-8")
    image = Image.open(SOURCE / "chrome.png")
    assert image.size == (1920, 1080), image.size
    print(f"FORGE_V3_RENDERED elements={len(items)} chrome={SOURCE / 'chrome.png'}")


class Sprites:
    def __init__(self, files: dict[str, bytes]):
        self.files = files
        self.providers: list[dict] = []
        self.map: dict[str, dict] = {}
        self.codepoint = 0xE000

    def add(self, name: str, image: Image.Image, width: int | None = None, height: int | None = None) -> None:
        assert image.width <= 256 and image.height <= 256, (name, image.size)
        char = chr(self.codepoint)
        self.codepoint += 1
        rel = "v3plates/" + name.replace("/", "_") + ".png"
        buffer = io.BytesIO()
        image.save(buffer, "PNG", optimize=True)
        self.files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        self.providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height,
                               "height": image.height, "chars": [char]})
        self.map[name] = {"char": char, "width": width or image.width, "height": height or image.height,
                          "font": f"{NS}:v3plates"}

    def tiles(self, name: str, image: Image.Image) -> None:
        for y in range(0, image.height, 256):
            for x in range(0, image.width, 256):
                self.add(f"{name}/{x}_{y}", image.crop((x, y, min(x + 256, image.width), min(y + 256, image.height))))


def supersampled(size: int, draw, scale: int = 4) -> Image.Image:
    big = Image.new("L", (size * scale, size * scale), 0)
    draw(ImageDraw.Draw(big), size * scale)
    alpha = big.resize((size, size), Image.LANCZOS)
    image = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    image.putalpha(alpha)
    return image


def corners(sprites: Sprites, name: str, size: int, ring: float | None) -> None:
    """Quarter shapes for rounded rectangles; white, tinted per node."""
    def quarter(d: ImageDraw.ImageDraw, s: int) -> None:
        # Circle centred on the bottom-right of a top-left quarter.
        d.ellipse((0, 0, 2 * s - 1, 2 * s - 1), fill=255)
        if ring is not None:
            inset = ring * s / size
            d.ellipse((inset, inset, 2 * s - 1 - inset, 2 * s - 1 - inset), fill=0)
    tl = supersampled(size, quarter)
    sprites.add(f"{name}_tl", tl)
    sprites.add(f"{name}_tr", tl.transpose(Image.FLIP_LEFT_RIGHT))
    sprites.add(f"{name}_bl", tl.transpose(Image.FLIP_TOP_BOTTOM))
    sprites.add(f"{name}_br", tl.transpose(Image.ROTATE_180))


def atlas(files: dict[str, bytes], metrics: dict, family: str, chars: list[str]) -> None:
    filename, weight, pixel, px = FAMILIES[family]
    cell = math.ceil(px * 1.25)
    scale = 40 / cell  # Vanilla scale of this page: every page declares height 40, ascent 32
    font = ImageFont.truetype(str(FONTS / filename), px)
    if weight is not None:
        font.set_variation_by_axes([weight])
    if pixel and px > 40:
        chars = [c for c in chars if c in NUMERIC or ord(c) < 127]
    widths: dict[str, int] = {}
    providers: list[dict] = [{"type": "space", "advances": {" ": round(font.getlength(" ") * scale)}}]
    usable = [c for c in chars if font.getmask(c).getbbox() is not None]
    for page, start in enumerate(range(0, len(usable), 256)):
        part = usable[start:start + 256]
        image = Image.new("RGBA", (16 * cell, 16 * cell), (0, 0, 0, 0))
        for index, char in enumerate(part):
            glyph = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
            draw = ImageDraw.Draw(glyph)
            if pixel:
                draw.fontmode = "1"
            # One pixel of left padding keeps negative side bearings (ノ, j) inside the cell.
            draw.text((1, round(cell * 0.8)), char, font=font, fill=(255, 255, 255, 255), anchor="ls")
            if not pixel and px >= 32:
                alpha = glyph.getchannel("A")
                glyph.putalpha(Image.blend(alpha, alpha.filter(ImageFilter.MaxFilter(3)), 0.18))
            # Vanilla derives a bitmap glyph's advance from its rightmost non-transparent column
            # ((rightmost + 1) * scale + 1). An alpha-1 pixel at the browser advance, which the text
            # shader discards, restores the browser's spacing.
            # Vanilla adds one pixel after the rightmost column; the left padding pixel adds the other.
            advance = max(2, min(cell - 1, round(font.getlength(char))))
            if glyph.getpixel((advance - 2, cell - 1))[3] == 0:
                glyph.putpixel((advance - 2, cell - 1), (255, 255, 255, 1))
            ink = glyph.getchannel("A").getbbox()
            widths[char] = round((ink[2] if ink else advance - 1) * scale + 1)
            image.paste(glyph, ((index % 16) * cell, (index // 16) * cell))
        name = f"v3font/{family}-{page}.png"
        buffer = io.BytesIO()
        image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{name}"] = buffer.getvalue()
        rows = ["".join(part[r:r + 16]).ljust(16, "\0") for r in range(0, 256, 16)]
        providers.append({"type": "bitmap", "file": f"{NS}:{name}", "ascent": 32, "height": 40, "chars": rows})
    providers.append({"type": "reference", "id": f"{NS}:sans"})
    files[f"assets/{NS}/font/{family}.json"] = json.dumps({"providers": providers}, ensure_ascii=False,
                                                          separators=(",", ":")).encode("utf-8")
    metrics[family] = {str(ord(c)): w for c, w in sorted(widths.items(), key=lambda p: ord(p[0]))}
    metrics[family]["32"] = round(font.getlength(" ") * scale)


def cmd_pack() -> None:
    files: dict[str, bytes] = {}
    with zipfile.ZipFile(PACK) as archive:
        for info in archive.infolist():
            if "/v3font/" in info.filename or "/v3plates/" in info.filename or \
                    re.search(r"/font/v3[a-z]*\.json$", info.filename) or "/licenses/" in info.filename:
                continue
            files[info.filename] = archive.read(info)
    metrics = json.loads(METRICS.read_text(encoding="utf-8"))
    chars = glyphs()
    for family in list(metrics):
        if family.startswith("v3"):
            del metrics[family]
    for family in FAMILIES:
        atlas(files, metrics, family, chars)
    print(f"FORGE_V3_ATLASES families={len(FAMILIES)}")
    for name in ("OFL-MPLUS2.txt", "OFL-DotGothic16.txt"):
        files[f"assets/{NS}/licenses/{name.lower()}"] = (FONTS / name).read_bytes()

    sprites = Sprites(files)
    sprites.tiles("v3_chrome", Image.open(SOURCE / "chrome.png").convert("RGBA"))
    corners(sprites, "v3_round", 64, None)
    for radius in (10, 14):
        corners(sprites, f"v3_ring{radius}", radius * 4, 4)
    glow = Image.new("L", (128, 128), 0)
    for y in range(128):
        for x in range(128):
            d = math.hypot(x - 63.5, y - 63.5) / 64
            glow.putpixel((x, y), int(255 * max(0.0, 1 - d) ** 2))
    soft = Image.new("RGBA", (128, 128), (255, 255, 255, 0))
    soft.putalpha(glow)
    sprites.add("v3_glow", soft)
    sprites.add("v3_ring", supersampled(256, lambda d, s: (d.ellipse((0, 0, s - 1, s - 1), fill=255),
                                                             d.ellipse((s * 0.03, s * 0.03, s * 0.97, s * 0.97), fill=0))))
    sword = Image.open(SOURCE / "img/sword_hero.png").convert("RGBA")
    sprites.add("v3_sword_hero_0", sword.crop((0, 0, sword.width, 256)), sword.width, 256)
    sprites.add("v3_sword_hero_1", sword.crop((0, 256, sword.width, sword.height)), sword.width, sword.height - 256)
    pad = 24
    silhouette = Image.new("L", (sword.width + pad * 2, sword.height + pad * 2), 0)
    silhouette.paste(sword.getchannel("A"), (pad, pad))
    silhouette = silhouette.filter(ImageFilter.GaussianBlur(9)).point(lambda v: min(255, int(v * 1.8)))
    halo = Image.new("RGBA", silhouette.size, (255, 255, 255, 0))
    halo.putalpha(silhouette)
    sprites.add("v3_sword_glow_0", halo.crop((0, 0, halo.width, 256)))
    sprites.add("v3_sword_glow_1", halo.crop((0, 256, halo.width, halo.height)))
    small = sword.resize((round(sword.width * 50 / sword.height), 50), Image.NEAREST)
    sprites.add("v3_sword_thumb", small.rotate(-35, resample=Image.NEAREST, expand=True))

    files[f"assets/{NS}/font/v3plates.json"] = json.dumps({"providers": sprites.providers}, ensure_ascii=False,
                                                          separators=(",", ":")).encode("utf-8")
    with zipfile.ZipFile(PACK, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            archive.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    METRICS.write_text(json.dumps(metrics, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")
    for target in (MAP, LAB_MAP):
        merged = {k: v for k, v in json.loads(target.read_text(encoding="utf-8")).items() if not k.startswith("v3_")}
        merged.update(sprites.map)
        target.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"FORGE_V3_PACK_BUILT sprites={len(sprites.map)} bytes={PACK.stat().st_size}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("step", choices=["fonts", "render", "pack"])
    parser.add_argument("--source", type=Path)
    args = parser.parse_args()
    if args.step == "fonts":
        cmd_fonts(args.source or Path("."))
    elif args.step == "render":
        cmd_render()
    else:
        cmd_pack()


if __name__ == "__main__":
    main()
