"""Build exact-pixel UI geometry and metadata; existing authored PNGs are copied unchanged.

Only rectangles/lines are generated here (gauges and a slot-safe window wireframe).
The generated SVG files are editable native geometry; PNG is the Minecraft font transport.
No Japanese/Latin glyph atlas is generated or replaced.
"""
from pathlib import Path
import json
import shutil
import struct
import zlib

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
    write_json(ASSETS / "font/core_menu.json", {"providers": [bitmap("core/menu_frame", 222, 13, [chr(0xE200)])]})
    providers = []
    for name, base, color, light in [("health_bar", 0xE300, "AA5555", "E2A08C"), ("mana_bar", 0xE320, "477FA1", "91CAE2")]:
        rectangles = []
        for step in range(21):
            y = step * 7
            rectangles += [(0, y, 40, 7, "706249"), (1, y+1, 38, 5, "24272B")]
            filled = round(36 * step / 20)
            if filled:
                rectangles += [(2, y+2, filled, 3, color), (2, y+2, filled, 1, light)]
        image(name, 40, 147, rectangles)
        providers.append(bitmap(f"core/{name}", 7, 7, [chr(base + n) for n in range(21)]))
    rectangles = []
    for step in range(11):
        y = step * 7
        rectangles += [(0, y, 12, 7, "6A5A3F"), (1, y+1, 10, 5, "24272B")]
        if step:
            rectangles.append((1, y+2, step, 3, "CDB77B"))
    image("skill_charge", 12, 77, rectangles)
    providers.append(bitmap("core/skill_charge", 7, 7, [chr(0xE340 + n) for n in range(11)]))
    write_json(ASSETS / "font/core_hud.json", {"providers": providers})
    for name, asset in stats:
        item_model(name, f"stats/{asset}")
    for name, _ in skills:
        item_model(name, f"skills/{name}")
    image("blank", 1, 1, [])
    item_model("blank", "core/blank")
    paths = sorted(str(path.relative_to(PACK)).replace("\\", "/") for path in PACK.rglob("*") if path.is_file() and path.name != "index.txt")
    (PACK / "index.txt").write_text("\n".join(paths) + "\n", encoding="utf-8")
    print(f"Built {len(paths)} indexed assets under {PACK}")


if __name__ == "__main__":
    build()
