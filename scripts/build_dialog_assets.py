"""Pack the dialog look: ProjectS buttons and text fields, M PLUS 2 for dialog text, small ornaments.

    python scripts/build_dialog_assets.py --source D:/path/to/fonts

Writes server-minestom/src/main/resources/polish05/dialog.zip, merged into the UI pack by CoreUiPackServer.
The vanilla widget sprites are the one global override here (asked for on 2026-10-07): every vanilla button and
text field takes the ProjectS frame while the pack is loaded. Fonts and ornaments stay in the private namespace.
"""
import argparse, io, json, zipfile
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "server-minestom/src/main/resources/polish05/dialog.zip"
NS = "projects_ui_polish05"
WIDGET = "assets/minecraft/textures/gui/sprites/widget"
TEXT_SOURCES = [ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CorePartyDialogs.kt"]


def hexc(v: str, a: int = 255):
    return tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def button(border: str, top: str, bottom: str, shine: str) -> Image.Image:
    """200x20 nine-slice (border 3): dark outline, 1px coloured frame, vertical fill, a bright top row."""
    img = Image.new("RGBA", (200, 20)); d = ImageDraw.Draw(img)
    t, b = hexc(top), hexc(bottom)
    for y in range(20):
        k = y / 19
        d.line([(0, y), (199, y)], fill=tuple(round(t[i] + (b[i] - t[i]) * k) for i in range(3)) + (255,))
    d.rectangle((1, 1, 198, 18), outline=hexc(border))
    d.line([(2, 2), (197, 2)], fill=hexc(shine))
    d.rectangle((0, 0, 199, 19), outline=hexc("#0a0a0d"))
    return img


def field(border: str) -> Image.Image:
    img = Image.new("RGBA", (200, 20)); d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 199, 19), fill=hexc("#0d0e11"), outline=hexc(border))
    return img


def divider() -> Image.Image:
    """Gold hairline that fades at both ends with a small diamond in the middle (240x7, drawn 7 GUI px tall)."""
    img = Image.new("RGBA", (240, 7)); d = ImageDraw.Draw(img)
    for x in range(240):
        a = int(200 * min(1.0, min(x, 239 - x) / 70))
        if a > 30: img.putpixel((x, 3), hexc("#c8aa6e", a))
    d.polygon([(120, 0), (123, 3), (120, 6), (117, 3)], fill=hexc("#121317"), outline=hexc("#e8c878"))
    return img


def slot() -> Image.Image:
    """Empty-member marker: a dim dashed square (9x9)."""
    img = Image.new("RGBA", (9, 9))
    for i in range(9):
        if i % 2 == 0:
            for (x, y) in ((i, 0), (i, 8), (0, i), (8, i)): img.putpixel((x, y), hexc("#5a5650"))
    return img


def glyphs() -> set[int]:
    chars = set(range(0x20, 0x7F)) | set(range(0x3000, 0x3100)) | set(range(0xFF01, 0xFF5F)) | {ord(c) for c in "★☆✔…・〜"}
    for source in TEXT_SOURCES:
        chars |= {ord(c) for c in source.read_text(encoding="utf-8") if ord(c) >= 0x80}
    return chars


def font(source: Path, weight: int) -> bytes:
    from fontTools import subset
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
    f = instancer.instantiateVariableFont(TTFont(source / "MPLUS2.ttf"), {"wght": weight})
    options = subset.Options(); options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]; options.name_languages = [0x409]
    options.layout_features = ["kern", "palt"]
    sub = subset.Subsetter(options=options)
    sub.populate(unicodes=glyphs() & f.getBestCmap().keys()); sub.subset(f)
    out = io.BytesIO(); f.save(out)
    return out.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser(); parser.add_argument("--source", required=True)
    source = Path(parser.parse_args().source)
    files: dict[str, bytes] = {}

    def png(path: str, image: Image.Image) -> None:
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True); files[path] = buffer.getvalue()

    nine = lambda border: json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": border}}}).encode()
    for name, image in (("button", button("#4a4030", "#2a2a31", "#18191e", "#3a3a44")),
                        ("button_highlighted", button("#e8c878", "#3a352a", "#221f1a", "#6a5c3c")),
                        ("button_disabled", button("#26272c", "#16171b", "#121317", "#1c1d22"))):
        png(f"{WIDGET}/{name}.png", image); files[f"{WIDGET}/{name}.png.mcmeta"] = nine(3)
    for name, border in (("text_field", "#4a4030"), ("text_field_highlighted", "#e8c878")):
        png(f"{WIDGET}/{name}.png", field(border)); files[f"{WIDGET}/{name}.png.mcmeta"] = nine(1)

    for weight, name in ((500, "dialog_medium"), (700, "dialog_bold")):
        files[f"assets/{NS}/font/{name}.ttf"] = font(source, weight)
    fallback = {"type": "reference", "id": "minecraft:default"}
    for font_id, file in (("dialog", "dialog_medium"), ("dialog_b", "dialog_bold")):
        files[f"assets/{NS}/font/{font_id}.json"] = json.dumps({"providers": [
            {"type": "ttf", "file": f"{NS}:{file}.ttf", "size": 9.0, "oversample": 4.0, "shift": [0.0, 0.5]}, fallback]}).encode()
    png(f"assets/{NS}/textures/dialog/divider.png", divider())
    png(f"assets/{NS}/textures/dialog/slot.png", slot())
    files[f"assets/{NS}/font/dialog_art.json"] = json.dumps({"providers": [
        {"type": "bitmap", "file": f"{NS}:dialog/divider.png", "ascent": 6, "height": 7, "chars": ["\uE000"]},
        {"type": "bitmap", "file": f"{NS}:dialog/slot.png", "ascent": 8, "height": 9, "chars": ["\uE001"]},
    ]}).encode()

    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    print(f"DIALOG_PACK_BUILT files={len(files)} bytes={OUT.stat().st_size}")


if __name__ == "__main__":
    main()
