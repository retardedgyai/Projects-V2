"""Build exact-pixel UI geometry and metadata; existing authored PNGs are copied unchanged.

Only rectangles/lines are generated here (gauges and a slot-safe window wireframe).
The generated SVG files are editable native geometry; PNG is the Minecraft font transport.
The readable menu has its own PUA-encoded Japanese atlas. No global font is replaced.
"""
from pathlib import Path
import json
import shutil
import struct
import zlib
from build_core_hud_assets import build_hud
from build_core_menu_assets import build_menu
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
ASSETS = PACK / "assets/projects"
SOURCE = ROOT / "assets/core-ui"


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def image(name, width, height, rectangles):
    """Rasterize this script's own native rectangular UI geometry, without resampling art."""
    pixels = bytearray(width * height * 4)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">']
    for x, y, w, h, color in rectangles:
        rgba = tuple(bytes.fromhex(color)) if len(color) == 8 else (*bytes.fromhex(color), 255)
        svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#{color[:6]}" fill-opacity="{rgba[3]/255:.3f}"/>')
        for row in range(y, y + h):
            for column in range(x, x + w):
                assert 0 <= column < width and 0 <= row < height
                at = (row * width + column) * 4
                pixels[at:at+4] = bytes(rgba)
    svg.append("</svg>")
    SOURCE.mkdir(parents=True, exist_ok=True)
    (SOURCE / f"{name}.svg").write_text("\n".join(svg) + "\n", encoding="utf-8")
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    scanlines = b"".join(b"\0" + bytes(pixels[y*width*4:(y+1)*width*4]) for y in range(height))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(scanlines, 9)) + chunk(b"IEND", b"")
    target = ASSETS / f"textures/gui/core/{name}.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(png)


def bitmap(file, height, ascent, chars):
    return {"type": "bitmap", "file": f"projects:gui/{file}.png", "height": height, "ascent": ascent, "chars": chars}


def item_model(name, source):
    # Bitmap fonts read arbitrary PNGs directly, but generated item models require the item atlas.
    # Vanilla 26.2's items.json atlas automatically scans textures/item, not textures/gui.
    target = ASSETS / f"textures/item/core_ui/{name}.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ASSETS / f"textures/gui/{source}.png", target)
    write_json(ASSETS / f"items/core_ui/{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:core_ui/{name}"}})
    write_json(ASSETS / f"models/core_ui/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"projects:item/core_ui/{name}"}})


def build():
    for name in ("dash", "slam", "whirl"):
        master = SOURCE / f"skills/{name}.png"
        if master.is_file():
            shutil.copyfile(master, ASSETS / f"textures/gui/skills/{name}.png")
    write_json(PACK / "pack.mcmeta", {"pack": {"description": "ProjectS · 開拓者のUI / Vanilla 26.2", "min_format": [88, 0], "max_format": [88, 0]}})
    stats = [("attack", "attack_power"), ("speed", "attack_speed"), ("critical", "magic_power"), ("defense", "defense"),
             ("health", "health"), ("magic", "magic_power"), ("mana", "mana"), ("reward", "xp"), ("mod", "level")]
    skills = [("dash", 0xE021), ("slam", 0xE022), ("whirl", 0xE023)]
    icon_providers = [bitmap(f"stats/{asset}", 9, 8, [chr(0xE001 + i)]) for i, (_, asset) in enumerate(stats)]
    icon_providers += [bitmap(f"skills/{name}", 16, 12, [chr(glyph)]) for name, glyph in skills]
    write_json(ASSETS / "font/core_icons.json", {"providers": icon_providers})
    advances = {chr(0xE100 + bit): 1 << bit for bit in range(12)}
    advances.update({chr(0xE180 + bit): -(1 << bit) for bit in range(12)})
    write_json(ASSETS / "font/core_spacing.json", {"providers": [{"type": "space", "advances": advances}]})

    # 26.2 AbstractContainerScreen extracts labels before items: the bitmap is a true backdrop.
    # Keep every vanilla hitbox untouched; render socket recesses at their exact existing coordinates.
    frame = [(0, 0, 176, 222, "24272B"), (4, 17, 20, 109, "1D2023"),
             (25, 17, 1, 109, "5D5038"), (4, 127, 168, 11, "B8AF99"),
             (0, 0, 176, 3, "24272B"), (0, 219, 176, 3, "24272B"),
             (0, 3, 3, 216, "24272B"), (173, 3, 3, 216, "24272B"),
             (3, 3, 170, 12, "24272B"), (4, 15, 168, 1, "746547"),
             (1, 1, 174, 1, "B7A16A"), (1, 220, 174, 1, "746547"),
             (1, 1, 1, 220, "8F7C52"), (174, 1, 1, 220, "8F7C52"),
             (3, 3, 3, 3, "D7C188"), (170, 3, 3, 3, "D7C188"),
             (3, 216, 3, 3, "8F7C52"), (170, 216, 3, 3, "8F7C52")]
    for y in list(range(18, 126, 18)) + list(range(140, 194, 18)) + [198]:
        for x in range(8, 168, 18):
            frame += [(x-1, y-1, 18, 18, "15181B"), (x, y, 16, 16, "303236"),
                      (x, y+16, 17, 1, "4B4B45"), (x+16, y, 1, 16, "44453F")]
    image("menu_frame", 176, 222, frame)
    # Dedicated workbench: category rail / recipes / subject / costs / explicit bottom actions.
    # Repaint only the container area; the player's 36 slots retain their original coordinates.
    forge = frame + [(4, 17, 168, 109, "24272B"), (4, 17, 20, 109, "181C20"),
                     (25, 17, 36, 89, "202326"), (62, 17, 53, 89, "2A2927"),
                     (116, 17, 56, 89, "202326"), (25, 107, 147, 19, "292922"),
                     (24, 17, 1, 109, "746547"), (61, 17, 1, 89, "514732"),
                     (115, 17, 1, 89, "514732"), (25, 106, 147, 1, "746547")]
    used = {0, 9, 18, 27, 36, 45, 1, 3, 4, 5, 7, 8, 13, 22, 31, 40,
            10, 11, 19, 20, 28, 29, 37, 38, 16, 17, 25, 26, 34, 35, 43, 44,
            46, 47, 48, 49, 50, 52}
    for slot in sorted(used):
        x, y = 8 + slot % 9 * 18, 18 + slot // 9 * 18
        color = "756344" if slot == 52 else "303236"
        forge += [(x-1, y-1, 18, 18, "15181B"), (x, y, 16, 16, color),
                  (x, y+16, 17, 1, "746547" if slot == 52 else "4B4B45")]
    # Quiet direction marks occupy the unused gutters, never a slot or a text field.
    for x in (66, 103):
        forge += [(x, 60, 5, 1, "9D8554"), (x+3, 59, 1, 3, "9D8554"), (x+4, 60, 1, 1, "D5BB7D")]
    image("forge_frame", 176, 222, forge)
    # The authored illustration appears ONLY while no MOD recipe is selected.
    # It is a technical thumbnail derivative of the supplied original, not generated pixel art.
    empty_path = ASSETS / "textures/gui/core/forge_empty.png"
    with Image.open(ASSETS / "textures/gui/core/forge_frame.png") as base:
        empty = base.convert("RGBA")
    master = SOURCE / "forge-vignette-master.png"
    if master.is_file():
        with Image.open(master) as original:
            thumbnail = original.convert("RGBA")
        thumbnail.thumbnail((48, 32), Image.Resampling.LANCZOS)
        empty.alpha_composite(thumbnail, (64 + (48-thumbnail.width)//2, 39 + (32-thumbnail.height)//2))
    empty.save(empty_path)
    write_json(SOURCE / "forge-layout.json", {"size": [176, 222], "categories": [0, 9, 18, 27],
               "recipes": [10, 11, 19, 20, 28, 29, 37, 38], "target": 22, "result": 31,
               "costs": [16, 17, 25, 26, 34, 35, 43, 44], "quantities": [47, 48, 49], "execute": 52,
               "vignette": {"box": [64, 39, 48, 32], "only_when": "no_selected_mod_recipe"}})
    write_json(ASSETS / "font/core_menu.json", {"providers": [
        bitmap("core/menu_frame", 222, 13, [chr(0xE200)]),
        bitmap("core/forge_frame", 222, 13, [chr(0xE201)]),
        bitmap("core/forge_empty", 222, 13, [chr(0xE202)])]})
    for name, asset in stats:
        item_model(name, f"stats/{asset}")
    for name, _ in skills:
        item_model(name, f"skills/{name}")
    image("blank", 1, 1, [])
    item_model("blank", "core/blank")
    build_hud(ASSETS, SOURCE, write_json)
    build_menu()
    paths = sorted(str(path.relative_to(PACK)).replace("\\", "/") for path in PACK.rglob("*") if path.is_file() and path.name != "index.txt")
    (PACK / "index.txt").write_text("\n".join(paths) + "\n", encoding="utf-8")
    print(f"Built {len(paths)} indexed assets under {PACK}")


if __name__ == "__main__":
    build()
