"""Package the approved Polish05 art for the isolated Vanilla UI laboratory."""
from __future__ import annotations

import json
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
KIT = ROOT / "assets/ui/polish05-import"
OUT = ROOT / "web-ui-lab/build/polish05"
PACK = OUT / "pack"
MAP = ROOT / "web-ui-lab/ui/polish05-font-map.json"
EFFECTS = ROOT / "web-ui-lab/ui/polish05-effects"
NAMESPACE = "projects_ui_polish05"


def main() -> None:
    if PACK.exists():
        shutil.rmtree(PACK)
    shutil.copytree(KIT / "resourcepack", PACK)
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
