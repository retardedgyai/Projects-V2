"""Build the readable chest canvas, isolated Japanese bitmap font, and exact-pixel QA sheet.

This is native UI geometry/font rasterization, not image generation. The user's global
Minecraft font is untouched. A pinned OFL font is fetched only when regenerating assets;
normal server builds consume the checked-in small PNGs/JSON files and never fetch fonts.
"""
from pathlib import Path
import hashlib
import json
import math
import shutil
import urllib.request

from PIL import Image, ImageDraw, ImageFont
from build_core_menu_art import build_art

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
ASSETS = PACK / "assets/projects"
SOURCE = ROOT / "assets/core-ui"
FONT_COMMIT = "295d98a7a0c17c68f1341eaeea354e7960ea70d3"
FONT_URL = f"https://raw.githubusercontent.com/google/fonts/{FONT_COMMIT}/ofl/notosansjp/NotoSansJP%5Bwght%5D.ttf"
FONT_SHA256 = "c2f3b4d463500a2ddcd3849cded1fceeb9fd6d1c32e6cbecd568453ba50fc68f"
CELL = 14
TEXT_SCALE = 2
SOURCE_CELL = CELL * TEXT_SCALE
BASELINE = 10
TEXT_BASE = 0xE800
FRAME_BASE = 0xE600
BUTTON_BASE = 0xE610
CARD_BASE = 0xE650
TEXT_YS = sorted({6, 8, 128, *(20 + 18 * row for row in range(6)), *(30 + 14 * row for row in range(13))})
PALETTE = {
    "NEUTRAL": ("31444D", "71847F", "ECF1F2"),
    "SELECTED": ("284F59", "69BBB7", "D8F3F5"),
    "PRIMARY": ("32646A", "9BD9CD", "E7F8FA"),
    "DISABLED": ("27343B", "4B575B", "ABB0B4"),
    "DANGER": ("623F44", "CD8D8A", "FFE3E0"),
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=True, separators=(",", ":")) + "\n", encoding="utf-8")


def source_font():
    target = ROOT / ".tools/core-menu/NotoSansJP.ttf"
    if not target.is_file():
        target.parent.mkdir(parents=True, exist_ok=True)
        print("Fetching the pinned Noto Sans JP authoring font (not part of the resource pack)")
        with urllib.request.urlopen(FONT_URL, timeout=45) as response:
            data = response.read()
        assert hashlib.sha256(data).hexdigest() == FONT_SHA256, "Unexpected font download; inspect upstream before accepting it"
        target.write_bytes(data)
    assert hashlib.sha256(target.read_bytes()).hexdigest() == FONT_SHA256, "Authoring font hash mismatch"
    font = ImageFont.truetype(str(target), 10 * TEXT_SCALE)
    font.set_variation_by_axes([600])
    return font


def repertoire():
    # Include all current production Kotlin wording, not user names or arbitrary incoming chat.
    # Full kana/ASCII and common menu symbols keep future simple labels immediately usable.
    chars = set(chr(code) for code in range(0x20, 0x7F))
    chars.update(chr(code) for code in range(0x3041, 0x3097))
    chars.update(chr(code) for code in range(0x30A1, 0x30FB))
    chars.update("□…→←↑↓×÷±％〜・·。、：「」『』（）【】！？ー　")
    for directory in ROOT.glob("*/src/main/kotlin"):
        for path in directory.rglob("*.kt"):
            chars.update(char for char in path.read_text(encoding="utf-8")
                         if 0x3000 <= ord(char) <= 0x9FFF or 0xFF01 <= ord(char) <= 0xFF60)
    # The component QA fixture is also source text and must never silently display fallback boxes.
    chars.update(char for char in Path(__file__).read_text(encoding="utf-8") if 0x3000 <= ord(char) <= 0x9FFF)
    # Invisible control/combining/non-character glyphs must not acquire bitmap advances.
    chars.difference_update(chr(code) for code in (0x3099, 0x309A, 0x309B, 0x309C))
    result = sorted(chars, key=ord)
    assert TEXT_BASE + len(result) <= 0xF8FF, "Repertoire exhausted the reserved menu PUA range"
    return result


def build_font():
    font = source_font()
    chars = repertoire()
    columns = 32
    rows = math.ceil(len(chars) / columns)
    atlas = Image.new("RGBA", (columns * SOURCE_CELL, rows * SOURCE_CELL))
    metrics = {}
    private = []
    for index, char in enumerate(chars):
        glyph = chr(TEXT_BASE + index)
        private.append(glyph)
        cell = Image.new("RGBA", (SOURCE_CELL, SOURCE_CELL))
        if char not in (" ", "　"):
            # A common baseline preserves punctuation position and all kana descenders.
            # 台/$ overshoot the cap height; square brackets/braces reach four pixels
            # under the baseline. Keep the entire outline, not just common kanji.
            _, top, _, bottom = font.getbbox(char, anchor="ls")
            assert 0 <= BASELINE * TEXT_SCALE + top and BASELINE * TEXT_SCALE + bottom <= SOURCE_CELL, f"Clipped menu glyph U+{ord(char):04X}"
            ImageDraw.Draw(cell).text((0, BASELINE * TEXT_SCALE), char, font=font, anchor="ls", fill="white")
            # Deliberate hard pixels rather than translucent smudges from a tiny 10px
            # raster. Two source pixels per GUI pixel preserve Japanese inner spaces.
            cell.putalpha(cell.getchannel("A").point(lambda value: 255 if value >= 100 else 0))
            box = cell.getchannel("A").getbbox()
            assert box is not None, f"Unexpected empty glyph U+{ord(char):04X}"
            advance = math.floor(0.5 + box[2] / TEXT_SCALE) + 1
            atlas.alpha_composite(cell, ((index % columns) * SOURCE_CELL, (index // columns) * SOURCE_CELL))
        else:
            advance = 4 if char == " " else 10
        metrics[char] = {"glyph": glyph, "advance": advance, "index": index}
    # Padding cells use unique PUA glyphs, never NUL (which would violate the pack contract).
    private += [chr(TEXT_BASE + index) for index in range(len(private), rows * columns)]
    grid = ["".join(private[row * columns:(row + 1) * columns]) for row in range(rows)]
    # Space characters are only in the space provider, not in the bitmap provider.
    for char in (" ", "　"):
        index = metrics[char]["index"]
        replacement = chr(TEXT_BASE + rows * columns + (0 if char == " " else 1))
        row, column = divmod(index, columns)
        grid[row] = grid[row][:column] + replacement + grid[row][column + 1:]
    destination = ASSETS / "textures/gui/core/menu_text.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(destination, optimize=True)
    for y in TEXT_YS:
        write_json(ASSETS / f"font/core_menu_y{y}.json", {"providers": [
            {"type": "space", "advances": {metrics[char]["glyph"]: metrics[char]["advance"] for char in (" ", "　")}},
            {"type": "bitmap", "file": "projects:gui/core/menu_text.png", "height": CELL,
             "ascent": 13 - y, "chars": grid},
        ]})
    target = ASSETS / "menu/glyphs.tsv"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("# codepoint\tprivate glyph\tadvance; generated by build_core_menu_assets.py\n" +
                      "".join(f"{ord(char):X}\t{ord(value['glyph']):X}\t{value['advance']}\n" for char, value in metrics.items()), encoding="utf-8")
    license_path = SOURCE / "fonts/OFL.txt"
    assert license_path.is_file(), "The Noto-derived bitmap font must retain its OFL license"
    shutil.copyfile(license_path, ASSETS / "menu/OFL.txt")
    write_json(ASSETS / "menu/font-source.json", {
        "name": "ProjectS Pixel Relic Menu", "derived_from": "Noto Sans JP", "weight": 600,
        "size": 10, "source_size": 20, "source_scale": TEXT_SCALE, "alpha": "binary",
        "source_url": FONT_URL, "source_sha256": FONT_SHA256, "license": "SIL Open Font License 1.1",
        "copyright": "Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'",
        "scope": "Private-use characters in projects:core_menu_y* only; minecraft:default is never modified",
    })
    return atlas, metrics


def build_frame():
    frame = Image.new("RGBA", (384, 222), "#434C4D")
    draw = ImageDraw.Draw(frame)
    # Intentional one-pixel stone steps, not a downscaled photographic slab.
    draw.rectangle((1, 1, 382, 220), fill="#AAAFA0")
    draw.line((2, 1, 381, 1), fill="#E1E2CE")
    draw.line((1, 2, 1, 218), fill="#D4D7C1")
    draw.line((2, 220, 382, 220), fill="#687471")
    draw.rectangle((4, 4, 379, 217), fill="#182B35", outline="#506365")
    # x=104 in this image is the unmodified vanilla chest x=0.
    for left, right in ((5, 99), (283, 378)):
        draw.rectangle((left, 5, right, 23), fill="#293F48")
        draw.line((left + 3, 24, right - 3, 24), fill="#718D8B")
        # Quiet carved detail is restricted to the margin below the last line.
        center = (left + right) // 2
        draw.line((left + 12, 214, center - 5, 214), fill="#3D5860")
        draw.line((center + 5, 214, right - 12, 214), fill="#3D5860")
        draw.polygon([(center, 212), (center + 2, 214), (center, 216), (center - 2, 214)], fill="#6B9B98")
    for x in (100, 280):
        draw.rectangle((x, 2, x + 3, 219), fill="#858F84")
        draw.line((x, 3, x, 218), fill="#C8CEB8")
        draw.line((x + 3, 3, x + 3, 218), fill="#34494C")
    draw.rectangle((104, 2, 279, 219), fill="#192E38")
    draw.rectangle((107, 4, 276, 16), fill="#2B424B")
    draw.line((108, 16, 275, 16), fill="#718D8B")
    draw.rectangle((108, 18, 275, 124), fill="#142832")
    draw.rectangle((108, 126, 275, 138), fill="#C7CBB6")
    # Vanilla draws its own dark inventory title AFTER our component at x=8,y=128.
    # Leave a light strip for that label, and never paint another label over it.
    # The 36 item positions stay exactly unchanged.
    for y in (140, 158, 176, 198):
        for column in range(9):
            x = 104 + 8 + column * 18
            draw.rectangle((x - 1, y - 1, x + 16, y + 16), fill="#101F27")
            draw.rectangle((x, y, x + 15, y + 15), fill="#354951")
            draw.line((x, y + 16, x + 16, y + 16), fill="#81928A")
    for index in range(2):
        frame.crop((index * 192, 0, (index + 1) * 192, 222)).save(
            ASSETS / f"textures/gui/core/menu_canvas_{index}.png", optimize=True)
    write_json(ASSETS / "font/core_menu_canvas.json", {"providers": [
        {"type": "bitmap", "file": f"projects:gui/core/menu_canvas_{index}.png", "height": 222,
         "ascent": 13, "chars": [chr(FRAME_BASE + index)]} for index in range(2)
    ]})
    return frame


def build_buttons():
    # Nine span widths, five states. Every backdrop is confined to the actual slot strip.
    atlas = Image.new("RGBA", (160 * 9, 16 * len(PALETTE)))
    grid = []
    for row, (tone, (fill, border, text)) in enumerate(PALETTE.items()):
        line = []
        for span in range(1, 10):
            line.append(chr(BUTTON_BASE + row * 9 + span - 1))
            x, y, width = (span - 1) * 160, row * 16, span * 18 - 2
            draw = ImageDraw.Draw(atlas)
            pixel_bevel(draw, (x, y, x + width - 1, y + 15), tone)
            if tone == "SELECTED":
                # Row 13 remains free for Japanese descenders; the accent never touches a label.
                draw.line((x + 1, y + 14, x + width - 2, y + 14), fill="#91D5CC")
        grid.append("".join(line))
    atlas.save(ASSETS / "textures/gui/core/menu_buttons.png", optimize=True)
    for row in range(6):
        write_json(ASSETS / f"font/core_menu_buttons_{row}.json", {"providers": [
            {"type": "bitmap", "file": "projects:gui/core/menu_buttons.png", "height": 16,
             "ascent": 13 - (18 + row * 18), "chars": grid}
        ]})
    return atlas


def pixel_bevel(draw, box, tone):
    x, y, right, bottom = box
    fill, border, _ = PALETTE[tone]
    draw.rectangle(box, fill="#" + fill, outline="#" + border)
    draw.line((x + 1, y + 1, right - 1, y + 1), fill="#96ACA1" if tone == "NEUTRAL" else "#" + border)
    draw.line((x + 1, bottom - 1, right - 1, bottom - 1), fill="#142B33")
    draw.line((right - 1, y + 2, right - 1, bottom - 1), fill="#142B33")
    # Two-pixel cut corners give the same restrained, block-built edge on every control.
    for corner in ((x, y), (right, y), (x, bottom), (right, bottom)):
        draw.point(corner, fill="#425B60")


def build_cards():
    for rows in range(1, 4):
        height = rows * 18 - 2
        atlas = Image.new("RGBA", (160 * 9, height * len(PALETTE)))
        draw = ImageDraw.Draw(atlas)
        grid = []
        for tone_index, tone in enumerate(PALETTE):
            grid.append("".join(chr(CARD_BASE + tone_index * 9 + span - 1) for span in range(1, 10)))
            for span in range(1, 10):
                x, y, width = (span - 1) * 160, tone_index * height, span * 18 - 2
                pixel_bevel(draw, (x, y, x + width - 1, y + height - 1), tone)
                if rows > 1:
                    draw.rectangle((x + 2, y + height - 16, x + width - 3, y + height - 3), fill="#233C46")
                if tone == "SELECTED":
                    draw.line((x + 2, y + height - 2, x + width - 3, y + height - 2), fill="#91D5CC")
        atlas.save(ASSETS / f"textures/gui/core/menu_cards_{rows}.png", optimize=True)
        for row in range(7 - rows):
            write_json(ASSETS / f"font/core_menu_cards_{rows}_{row}.json", {"providers": [
                {"type": "bitmap", "file": f"projects:gui/core/menu_cards_{rows}.png", "height": height,
                 "ascent": 13 - (18 + row * 18), "chars": grid}]})


def build_preview(frame, text_atlas, metrics, buttons):
    """Use the same bitmap cells, advances, slots and palette as CoreMenuCanvas, at 1x then 3x.

    This is a component QA fixture, explicitly not a gameplay screenshot or a server state.
    """
    preview = frame.copy()

    def width(text):
        return sum(metrics.get(char, metrics["□"])["advance"] for char in text)

    def text(x, y, value, color="ECF1F2", limit=88):
        assert width(value) <= limit, f"QA fixture overflow: {value}={width(value)} > {limit}"
        assert all(char in metrics for char in value), f"QA fixture contains an unrenderable character: {value}"
        for char in value:
            metric = metrics.get(char, metrics["□"])
            index = metric["index"]
            cell = text_atlas.crop(((index % 32) * SOURCE_CELL, (index // 32) * SOURCE_CELL,
                                    (index % 32 + 1) * SOURCE_CELL, (index // 32 + 1) * SOURCE_CELL))
            cell = cell.resize((CELL, CELL), Image.Resampling.NEAREST)
            colored = Image.new("RGBA", cell.size, "#" + color)
            colored.putalpha(cell.getchannel("A"))
            preview.alpha_composite(colored, (x + 104, y))
            x += metric["advance"]

    def button(slot, span, label, tone="NEUTRAL"):
        x, y = 8 + (slot % 9) * 18, 18 + (slot // 9) * 18
        row = list(PALETTE).index(tone)
        crop = buttons.crop(((span - 1) * 160, row * 16, (span - 1) * 160 + span * 18 - 2, row * 16 + 16))
        preview.alpha_composite(crop, (x + 104, y))
        text(x + (span * 18 - 2 - width(label)) // 2, y + 2, label, PALETTE[tone][2], span * 18 - 2)

    text(8, 6, "開拓工房 / 強化", "E9D5A0", 160)
    text(-98, 8, "装備の変化", "E9D5A0")
    text(184, 8, "必要な素材", "E9D5A0")
    left = ["大剣 T2", "強化 +5 → +6", "攻撃 42 → 46", "", "成功率 85%", "失敗しても", "装備は消えない", "", "通常の強化", "を選択中"]
    right = ["必要 / 所持", "木材 T1", "12 / 80", "金属材 T1", "8 / 24", "結晶 T1", "4 / 2 不足", "", "素材をクリック", "して補充へ"]
    for x, lines in ((-98, left), (184, right)):
        for row, value in enumerate(lines):
            text(x, 30 + row * 14, value, "E99E97" if "不足" in value else "ECF1F2")
    for slot, label in ((0, "強化"), (2, "精製"), (4, "制作"), (6, "MOD")):
        button(slot, 2, label, "SELECTED" if slot == 0 else "NEUTRAL")
    button(8, 1, "?")
    button(9, 4, "武器", "SELECTED")
    button(13, 4, "防具")
    button(18, 4, "通常の強化", "SELECTED")
    button(22, 4, "集中の強化")
    button(27, 3, "木材")
    button(30, 3, "金属材")
    button(33, 3, "結晶", "DANGER")
    button(36, 4, "素材を補充", "PRIMARY")
    button(40, 4, "素材が不足", "DISABLED")
    button(45, 3, "戻る")
    button(48, 3, "装備詳細")
    button(51, 3, "保管庫")
    # No substitute for the vanilla inventory title: this offline component QA image
    # deliberately omits the label that the real client draws on the light strip.
    preview.save(SOURCE / "readable-menu-canvas-preview.png", optimize=True)
    preview.resize((1152, 666), Image.Resampling.NEAREST).save(SOURCE / "readable-menu-canvas-preview-3x.png", optimize=True)


def build_menu():
    text_atlas, metrics = build_font()
    frame = build_frame()
    buttons = build_buttons()
    build_cards()
    build_art()
    build_preview(frame, text_atlas, metrics, buttons)
    write_json(SOURCE / "readable-menu-layout.json", {
        "size": [384, 222], "origin": [-104, 0], "frame_tile_width": 192,
        "text_cell": CELL, "source_cell": SOURCE_CELL, "text_scale": TEXT_SCALE,
        "text_ys": TEXT_YS, "font_size": 10, "font_weight": 600,
        "panel": {"left_x": -98, "right_x": 184, "width": 88, "header_y": 8, "line_y": 30, "line_height": 14, "lines": 13},
        "slots": {"origin": [8, 18], "stride": 18, "columns": 9, "rows": 6},
        "button": {"height": 16, "width": "span * 18 - 2", "text_y": "slotY + 2", "icon_reserved_width": 18, "tones": PALETTE},
        "minimum_scaled_width": 400,
        "preview": "Offline component QA fixture, using the actual generated PNG cells and glyph advances; not a gameplay screenshot.",
    })
    print(f"Built readable menu: {len(metrics)} text glyphs, {len(TEXT_YS)} isolated row fonts, 384x222 canvas")


if __name__ == "__main__":
    build_menu()
