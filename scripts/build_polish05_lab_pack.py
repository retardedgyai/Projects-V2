"""Package the approved Polish05 art for the isolated Vanilla UI laboratory."""
from __future__ import annotations

import json
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
KIT = ROOT / "assets/ui/polish05-import"
OUT = ROOT / "web-ui-lab/build/polish05"
PACK = OUT / "pack"
MAP = ROOT / "web-ui-lab/ui/polish05-font-map.json"
EFFECTS = ROOT / "web-ui-lab/ui/polish05-effects"
FONTS = ROOT / "web-ui-lab/ui/polish05-fonts"
NAMESPACE = "projects_ui_polish05"


def main() -> None:
    if PACK.exists():
        shutil.rmtree(PACK)
    shutil.copytree(KIT / "resourcepack", PACK)
    # Vanilla 26.2 does not load the old TTF provider in this pack format. Rasterize
    # the source Noto outlines into bitmap glyphs so Japanese stays crisp and colored
    # by TextDisplay while preserving the HTML's font shapes.
    metrics = json.loads((ROOT / "web-ui-lab/src/main/resources/polish05-font-metrics.json").read_text(encoding="utf-8"))
    for family in ("sans", "serif"):
        font_dir = PACK / "assets" / NAMESPACE / "font"
        texture_dir = PACK / "assets" / NAMESPACE / "textures" / "font"
        texture_dir.mkdir(parents=True, exist_ok=True)
        font = ImageFont.truetype(FONTS / f"{family}.ttf", 32)
        glyphs = [chr(int(cp)) for cp in sorted(metrics[family], key=int) if int(cp) != 32]
        providers = [{"type": "space", "advances": {" ": 10}}]
        for page, start in enumerate(range(0, len(glyphs), 256)):
            chars = glyphs[start:start + 256]
            image = Image.new("RGBA", (640, 640), (0, 0, 0, 0))
            draw = ImageDraw.Draw(image)
            for index, char in enumerate(chars):
                draw.text(((index % 16) * 40, (index // 16) * 40 - 4), char,
                          font=font, fill=(255, 255, 255, 255))
            name = f"{family}-{page}.png"
            image.save(texture_dir / name, optimize=True)
            # Bitmap providers divide the whole texture by the declared grid.
            # Pad the final page too, or its glyphs become 200px-tall cells.
            rows = ["".join(chars[row:row + 16]).ljust(16, "\0")
                    for row in range(0, 256, 16)]
            providers.append({"type": "bitmap", "file": f"{NAMESPACE}:font/{name}",
                              "ascent": 32, "height": 40, "chars": rows})
        providers.append({"type": "reference", "id": "minecraft:default"})
        (font_dir / f"{family}.json").write_text(json.dumps({"providers": providers},
            ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    license_dir = PACK / "LICENSES"
    license_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(FONTS / "OFL.txt", license_dir / "Noto-OFL.txt")
    providers = []
    sprites = {}
    codepoint = 0xE980

    def register(name: str, rel: str, width: int, height: int) -> None:
        nonlocal codepoint
        char = chr(codepoint)
        codepoint += 1
        providers.append({"type": "bitmap", "file": f"{NAMESPACE}:{rel}",
                          "ascent": height, "height": height, "chars": [char]})
        sprites[name] = {"char": char, "width": width, "height": height,
                         "font": f"{NAMESPACE}:plates"}

    plates = json.loads((KIT / "layout/static_plates.json").read_text(encoding="utf-8"))
    for name in ("window_chrome", "forge_environment_with_glow", "forge_environment_plate"):
        for tile in plates[name]["tiles"]:
            rel = tile["file"].split(":", 1)[1]
            register(f"{name}/{tile['x']}_{tile['y']}", rel, tile["w"], tile["h"])

    for name in ("enhance_button", "replenish_row"):
        image = Image.open(KIT / "assets/layers" / f"{name}.png").convert("RGBA")
        for y in range(0, image.height, 256):
            for x in range(0, image.width, 256):
                tile = image.crop((x, y, min(x + 256, image.width), min(y + 256, image.height)))
                rel = f"textures/plates/{name}_{x}_{y}.png"
                dest = PACK / "assets" / NAMESPACE / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                tile.save(dest)
                register(f"{name}/{x}_{y}", f"plates/{name}_{x}_{y}.png", tile.width, tile.height)

    # Browser-exported CSS light phases and Georgia numerals keep soft alpha in
    # opaque environment tiles / dedicated glyphs instead of relying on low-alpha
    # TextDisplay sprites, which differ visibly from the approved HTML in Vanilla.
    for name in ("forge_environment_striking", "forge_environment_result_warm"):
        image = Image.open(EFFECTS / f"{name}.png").convert("RGBA")
        assert image.size == (628, 382), (name, image.size)
        for y in range(0, image.height, 256):
            for x in range(0, image.width, 256):
                tile = image.crop((x, y, min(x + 256, image.width), min(y + 256, image.height)))
                rel = f"textures/plates/{name}_{x}_{y}.png"
                dest = PACK / "assets" / NAMESPACE / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                tile.save(dest)
                register(f"{name}/{x}_{y}", f"plates/{name}_{x}_{y}.png", tile.width, tile.height)
    for name in ([f"next_level_{level}" for level in range(31)] + ["next_level_max"]):
        image = Image.open(EFFECTS / f"{name}.png").convert("RGBA")
        rel = f"textures/effects/{name}.png"
        dest = PACK / "assets" / NAMESPACE / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        image.save(dest)
        register(name, f"effects/{name}.png", image.width, image.height)
    for name in ("gear_selected", "gear_unselected", "tab_active_forge", "tab_active_refine", "tab_active_bag",
                 "catalyst_off", "catalyst_on", "bag_slot_selected", "bag_slot_regular", "bag_slot_empty",
                 "recipe_selected", "recipe_regular"):
        image = Image.open(EFFECTS / f"{name}.png").convert("RGBA")
        rel = f"textures/effects/{name}.png"
        dest = PACK / "assets" / NAMESPACE / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        image.save(dest)
        register(name, f"effects/{name}.png", image.width, image.height)
    # Vanilla bitmap providers silently show a missing-glyph square for these
    # >256px plates. Split them exactly like the approved chrome tiles.
    for name in ("result_level_halo", "catalyst_off_halo", "catalyst_on_halo",
                 "cost_ready_halo", "cost_missing_halo"):
        image = Image.open(EFFECTS / f"{name}.png").convert("RGBA")
        for x in range(0, image.width, 256):
            tile = image.crop((x, 0, min(x + 256, image.width), image.height))
            rel = f"textures/effects/{name}_{x}_0.png"
            dest = PACK / "assets" / NAMESPACE / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            tile.save(dest)
            register(f"{name}/{x}_0", f"effects/{name}_{x}_0.png", tile.width, tile.height)

    # The kit's source font already contains the sharp icons and both original swords.
    raw = json.loads((KIT / "layout/sprite_glyphs.json").read_text(encoding="utf-8"))
    for name, sprite in raw.items():
        sprites[name] = {"char": sprite["char"], "width": sprite["width"],
                         "height": sprite["height"], "font": sprite["font"]}

    font = PACK / "assets" / NAMESPACE / "font/plates.json"
    font.parent.mkdir(parents=True, exist_ok=True)
    font.write_text(json.dumps({"providers": providers}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    MAP.write_text(json.dumps(sprites, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    output = OUT / "polish05-lab.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for file in sorted(PACK.rglob("*")):
            if file.is_file():
                info = zipfile.ZipInfo(file.relative_to(PACK).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    print(f"POLISH05_PACK_BUILT {output} sprites={len(sprites)}")


if __name__ == "__main__":
    main()
