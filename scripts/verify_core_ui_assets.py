"""Read-only structural checks for the optional Vanilla 26.2 UI pack."""
from pathlib import Path
import hashlib
import json
import math
import struct
import zlib
from PIL import Image
from build_core_hud_assets import vanilla_overrides
from build_core_menu_assets import TEXT_YS, CELL, SOURCE_CELL, TEXT_SCALE, TEXT_BASE, FRAME_BASE, BUTTON_BASE, CARD_BASE, PALETTE, DOT_FONT_SHA256
from build_core_menu_art import ART, ART_BASE, ART_CELL, ART_YS, ART_SIZES

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"


def verify():
    paths = (PACK / "index.txt").read_text(encoding="utf-8").splitlines()
    assert len(paths) == len(set(paths)) and paths == sorted(paths)
    overrides = vanilla_overrides()
    actual_overrides = {p for p in paths if p.startswith("assets/minecraft/")}
    assert actual_overrides == overrides, "Only the requested player heart/food overrides are permitted"
    assert not any(".." in p or "\\" in p or p.startswith("/") for p in paths)
    for path in overrides:
        with Image.open(PACK / path) as sprite:
            assert sprite.size == (9, 9) and sprite.convert("RGBA").getchannel("A").getbbox() is None
    actual = sorted(str(p.relative_to(PACK)).replace("\\", "/") for p in PACK.rglob("*") if p.is_file() and p.name != "index.txt")
    assert actual == paths, "Stale pack index"
    meta = json.loads((PACK / "pack.mcmeta").read_text())
    assert meta["pack"]["min_format"] == [88, 0] and meta["pack"]["max_format"] == [88, 0]
    glyphs = set()
    for path in (PACK / "assets/projects/font").glob("*.json"):
        providers = json.loads(path.read_text())["providers"]
        for provider in providers:
            assert provider["type"] in ("bitmap", "space"), "No font replacement/reference needed"
            if provider["type"] == "space":
                chars = provider["advances"]
            else:
                chars = "".join(provider["chars"])
                filename = provider["file"].replace("projects:", "assets/projects/textures/")
                png = (PACK / filename).read_bytes()
                assert png[:8] == b"\x89PNG\r\n\x1a\n"
                width, height = struct.unpack(">II", png[16:24])
                assert height % len(provider["chars"]) == 0
                assert all(len(row) == len(provider["chars"][0]) for row in provider["chars"])
                assert width % len(provider["chars"][0]) == 0
                assert provider["ascent"] <= provider["height"]
                assert width // len(provider["chars"][0]) <= 256
                assert height // len(provider["chars"]) <= 256
            for char in chars:
                assert 0xE000 <= ord(char) <= 0xF8FF, "Custom font must not claim Japanese or Latin characters"
                key = (path.name, char)
                assert key not in glyphs, "Duplicate glyph"
                glyphs.add(key)
    hud = json.loads((PACK / "assets/projects/font/core_hud.json").read_text())["providers"]
    assert len(hud) == 8
    for provider in hud:
        filename = provider["file"].replace("projects:", "assets/projects/textures/")
        with Image.open(PACK / filename) as sheet:
            cell_w, cell_h = sheet.width // len(provider["chars"][0]), sheet.height // len(provider["chars"])
            for y, row in enumerate(provider["chars"]):
                for x, char in enumerate(row):
                    box = sheet.crop((x * cell_w, y * cell_h, (x + 1) * cell_w, (y + 1) * cell_h)).getchannel("A").getbbox()
                    assert box is not None
                    advance = round(box[2] * provider["height"] / cell_h) + 1
                    expected = 82 if ord(char) < 0xE400 else 33 if ord(char) < 0xE500 else 9 if ord(char) < 0xE520 else 4
                    assert advance == expected, f"HUD anchor drift: {hex(ord(char))} advances {advance}, expected {expected}"
    layout = json.loads((ROOT / "assets/core-ui/hud-layout.json").read_text())
    assert layout["bars"] == {"left_x": [-91, 10], "width": 81, "height": 9, "top_from_bottom": 39}
    for name in ("dash", "slam", "whirl"):
        with Image.open(PACK / f"assets/projects/textures/gui/core/skill_{name}_states.png") as sheet:
            def frame(index): return sheet.crop(((index % 4)*32, (index // 4)*32, (index % 4)*32+32, (index // 4)*32+32))
            assert frame(0).tobytes() != frame(20).tobytes() != frame(21).tobytes()
    # Slots retain exact vanilla coordinates and readable socket centers (items render afterwards).
    png = (PACK / "assets/projects/textures/gui/core/menu_frame.png").read_bytes()
    cursor, compressed = 8, b""
    while cursor < len(png):
        length = struct.unpack(">I", png[cursor:cursor+4])[0]
        if png[cursor+4:cursor+8] == b"IDAT": compressed += png[cursor+8:cursor+8+length]
        cursor += length + 12
    pixels = zlib.decompress(compressed)
    for y in list(range(18, 126, 18)) + list(range(140, 194, 18)) + [198]:
        for x in range(8, 168, 18):
            at = (y+8) * (176*4+1) + 1 + (x+8)*4
            assert pixels[at:at+4] == bytes.fromhex("303236ff"), "Inventory socket/hitbox mismatch"
    for path in (PACK / "assets/projects/items/core_ui").glob("*.json"):
        ref = json.loads(path.read_text())["model"]["model"].replace("projects:", "assets/projects/models/")
        model = json.loads((PACK / f"{ref}.json").read_text())
        texture_id = model["textures"]["layer0"]
        assert texture_id.startswith("projects:item/core_ui/"), "Generated models need stock item-atlas sprites, not direct GUI PNGs"
        texture = texture_id.replace("projects:", "assets/projects/textures/")
        assert (PACK / f"{texture}.png").is_file()
        source = {"attack": "stats/attack_power", "speed": "stats/attack_speed", "critical": "stats/magic_power",
                  "defense": "stats/defense", "health": "stats/health", "magic": "stats/magic_power",
                  "mana": "stats/mana", "reward": "stats/xp", "mod": "stats/level", "blank": "core/blank"}.get(path.stem, f"skills/{path.stem}")
        assert (PACK / f"{texture}.png").read_bytes() == (PACK / f"assets/projects/textures/gui/{source}.png").read_bytes(), "Item-atlas copies must preserve original artwork"
    forge_layout = json.loads((ROOT / "assets/core-ui/forge-layout.json").read_text())
    assert forge_layout["size"] == [176, 222] and forge_layout["execute"] == 52
    assert forge_layout["categories"] == [0, 9, 18, 27]
    assert forge_layout["quantities"] == [47, 48, 49]
    with Image.open(PACK / "assets/projects/textures/gui/core/forge_frame.png") as normal, Image.open(PACK / "assets/projects/textures/gui/core/forge_empty.png") as empty:
        assert normal.size == empty.size == (176, 222)
        # Vignette cannot touch the player's inventory, costs, execute button, or result item.
        box = forge_layout["vignette"]["box"]
        for y in range(222):
            for x in range(176):
                if not (box[0] <= x < box[0]+box[2] and box[1] <= y < box[1]+box[3]):
                    assert normal.getpixel((x, y)) == empty.getpixel((x, y)), "Forge artwork escaped its empty-state region"
        assert normal.getpixel((175, 221))[3] == 255  # Both title glyphs keep their 177 px advance.
    # Readable menu: actual glyph advances, complete repertoire, exact hitboxes, and
    # the two-tile wide canvas are verified separately from the older optional frame.
    menu = json.loads((ROOT / "assets/core-ui/readable-menu-layout.json").read_text())
    assert menu["size"] == [384, 222] and menu["origin"] == [-104, 0]
    assert menu["text_ys"] == TEXT_YS and len(TEXT_YS) <= 23
    assert menu["panel"] == {"left_x": -98, "right_x": 184, "width": 88, "header_y": 8, "line_y": 30, "line_height": 14, "lines": 13}
    assert menu["slots"] == {"origin": [8, 18], "stride": 18, "columns": 9, "rows": 6}
    font_meta = json.loads((PACK / "assets/projects/menu/font-source.json").read_text())
    assert font_meta["source_sha256"] == DOT_FONT_SHA256 and font_meta["weight"] == 400 and font_meta["size"] == 8
    assert font_meta["emphasis"] == font_meta["body"], "Menu labels and body must share the rounded typeface"
    assert (PACK / "assets/projects/textures/gui/core/menu_text.png").read_bytes() == (PACK / "assets/projects/textures/gui/core/menu_text_emphasis.png").read_bytes()
    assert font_meta["body"]["source_sha256"] == DOT_FONT_SHA256 and font_meta["body"]["source_size"] == 24
    assert font_meta["native_grid"] == 12 and font_meta["source_scale"] == 3
    assert font_meta["horizontal_weight_px"] == 1, "Keep the modest horizontal weight adjustment; do not dilate kanji vertically"
    assert (PACK / "assets/projects/menu/MaruMinya-OFL.txt").read_bytes() == (ROOT / "assets/core-ui/fonts/MaruMinya-OFL.txt").read_bytes()
    assert font_meta["source_scale"] == TEXT_SCALE and font_meta["alpha"] == "binary"
    assert menu["source_cell"] == SOURCE_CELL and menu["text_scale"] == TEXT_SCALE
    assert (PACK / "assets/projects/menu/OFL.txt").read_bytes() == (ROOT / "assets/core-ui/fonts/OFL.txt").read_bytes()
    assert (PACK / "assets/projects/menu/DotGothic16-OFL.txt").read_bytes() == (ROOT / "assets/core-ui/fonts/DotGothic16-OFL.txt").read_bytes()
    assert not any(path.endswith((".ttf", ".otf")) for path in paths), "The full authoring font must not bloat the player pack"
    metrics = {}
    for line in (PACK / "assets/projects/menu/glyphs.tsv").read_text().splitlines():
        if line.startswith("#") or not line: continue
        code, glyph, advance = line.split("\t")
        assert int(code, 16) not in metrics
        metrics[int(code, 16)] = (int(glyph, 16), int(advance))
    assert ord("□") in metrics and all(code in metrics for code in range(0x20, 0x7F))
    with Image.open(PACK / "assets/projects/textures/gui/core/menu_text.png") as atlas:
        for code, (glyph, advance) in metrics.items():
            index = glyph - TEXT_BASE
            alpha = atlas.crop(((index % 32) * SOURCE_CELL, (index // 32) * SOURCE_CELL,
                                (index % 32 + 1) * SOURCE_CELL, (index // 32 + 1) * SOURCE_CELL)).getchannel("A")
            assert set(alpha.tobytes()) <= {0, 255}, "Menu text must use deliberate hard pixels"
            box = alpha.getbbox()
            if code in (0x20, 0x3000):
                assert box is None and advance == (4 if code == 0x20 else 10)
            else:
                assert box is not None and math.floor(0.5 + box[2] / TEXT_SCALE) + 1 == advance, f"Menu text anchor drift U+{code:04X}"
    for y in TEXT_YS:
        providers = json.loads((PACK / f"assets/projects/font/core_menu_y{y}.json").read_text())["providers"]
        assert len(providers) == 2 and providers[1]["ascent"] == 13 - y and providers[1]["height"] == CELL
        assert providers[1]["file"] == "projects:gui/core/menu_text.png"
    emphasized = {}
    for line in (PACK / "assets/projects/menu/glyphs-emphasis.tsv").read_text().splitlines():
        if line.startswith("#") or not line: continue
        code, glyph, advance = line.split("\t")
        assert int(code, 16) not in emphasized
        emphasized[int(code, 16)] = (int(glyph, 16), int(advance))
    assert metrics.keys() == emphasized.keys()
    with Image.open(PACK / "assets/projects/textures/gui/core/menu_text_emphasis.png") as atlas:
        for code, (glyph, advance) in emphasized.items():
            assert glyph == metrics[code][0], "Text roles must share codepoint/glyph mapping"
            index = glyph - TEXT_BASE
            alpha = atlas.crop(((index % 32) * SOURCE_CELL, (index // 32) * SOURCE_CELL,
                                (index % 32 + 1) * SOURCE_CELL, (index // 32 + 1) * SOURCE_CELL)).getchannel("A")
            assert set(alpha.tobytes()) <= {0, 255}
            box = alpha.getbbox()
            if code in (0x20, 0x3000):
                assert box is None and advance == (4 if code == 0x20 else 10)
            else:
                assert box is not None and math.floor(0.5 + box[2] / TEXT_SCALE) + 1 == advance
                if chr(code) in "+0123456789→":
                    assert 100 + box[1] / TEXT_SCALE >= 102, "Caption ink must start below the hero ink"
    assert emphasized == metrics, "Both menu text roles must preserve the same rounded glyph metrics"
    for y in TEXT_YS:
        providers = json.loads((PACK / f"assets/projects/font/core_menu_emphasis_y{y}.json").read_text())["providers"]
        assert len(providers) == 2 and providers[1]["ascent"] == 13 - y and providers[1]["height"] == CELL
        assert providers[1]["file"] == "projects:gui/core/menu_text_emphasis.png"
    focus = json.loads((PACK / "assets/projects/font/core_menu_focus.json").read_text())["providers"][0]
    assert focus["height"] == 64 and focus["ascent"] == -31 and focus["chars"] == [chr(0xE6F0)]
    with Image.open(PACK / "assets/projects/textures/gui/core/menu_focus.png") as sprite:
        assert sprite.size == (106, 64) and sprite.getchannel("A").getbbox()[2] == 106
        assert 44 + sprite.height <= 108, "Focus must end before the execute row"
        assert sprite.crop((0, 0, 106, 10)).getchannel("A").getbbox() is None, "Focus decoration must not paint over gear selectors"
    canvas = Image.new("RGBA", (384, 222))
    providers = json.loads((PACK / "assets/projects/font/core_menu_canvas.json").read_text())["providers"]
    assert len(providers) == 2
    for index in range(2):
        with Image.open(PACK / f"assets/projects/textures/gui/core/menu_canvas_{index}.png") as tile:
            assert tile.size == (192, 222) and tile.getchannel("A").getbbox() == (0, 0, 192, 222)
            canvas.alpha_composite(tile, (index * 192, 0))
            assert providers[index]["chars"] == [chr(FRAME_BASE + index)] and providers[index]["ascent"] == 13
    for y in (140, 158, 176, 198):
        for column in range(9):
            assert canvas.getpixel((104 + 8 + column * 18 + 8, y + 8)) == (41, 36, 29, 255)
    assert canvas.getpixel((104 + 8, 128)) == (191, 167, 122, 255), "Vanilla inventory text needs a light tab, not a competing overlay"
    assert canvas.getpixel((270, 128)) != canvas.getpixel((112, 128)), "Do not restore the full-width bright inventory bar"
    with Image.open(PACK / "assets/projects/textures/gui/core/menu_buttons.png") as buttons:
        assert buttons.size == (1440, 80)
        for row, tone in enumerate(PALETTE):
            for span in range(1, 10):
                cell = buttons.crop(((span - 1) * 160, row * 16, span * 160, row * 16 + 16))
                assert cell.getchannel("A").getbbox() == (0, 0, span * 18 - 2, 16)
                if tone == "SELECTED": assert cell.getpixel((2, 14)) == (203, 161, 102, 255)
        for row in range(6):
            provider = json.loads((PACK / f"assets/projects/font/core_menu_buttons_{row}.json").read_text())["providers"][0]
            assert provider["ascent"] == 13 - (18 + row * 18) and provider["height"] == 16
            assert [ord(char) for char in "".join(provider["chars"])] == list(range(BUTTON_BASE, BUTTON_BASE + 45))
    # Multi-row buttons still correspond to rectangles of real, unmodified slots.
    for rows in range(1, 4):
        height = rows * 18 - 2
        with Image.open(PACK / f"assets/projects/textures/gui/core/menu_cards_{rows}.png") as cards:
            assert cards.size == (1440, height * len(PALETTE))
            for tone_index, tone in enumerate(PALETTE):
                for columns in range(1, 10):
                    cell = cards.crop(((columns - 1) * 160, tone_index * height,
                                       columns * 160, (tone_index + 1) * height))
                    assert cell.getchannel("A").getbbox() == (0, 0, columns * 18 - 2, height)
        for row in range(7 - rows):
            provider = json.loads((PACK / f"assets/projects/font/core_menu_cards_{rows}_{row}.json").read_text())["providers"][0]
            assert provider["height"] == height and provider["ascent"] == 13 - (18 + row * 18)
            assert [ord(char) for char in "".join(provider["chars"])] == list(range(CARD_BASE, CARD_BASE + 45))
    art_metrics = [line.split("\t") for line in (PACK / "assets/projects/menu/art.tsv").read_text().splitlines() if line and not line.startswith("#")]
    assert [line[0] for line in art_metrics] == [art[0] for art in ART]
    with Image.open(PACK / "assets/projects/textures/gui/core/menu_art.png") as atlas:
        assert atlas.size == (256, 128)
        for index, (name, ordinal, advance16, advance32, advance48) in enumerate(art_metrics):
            assert int(ordinal) == index
            cell = atlas.crop((index % 8 * ART_CELL, index // 8 * ART_CELL,
                               (index % 8 + 1) * ART_CELL, (index // 8 + 1) * ART_CELL))
            alpha = cell.getchannel("A")
            assert set(alpha.tobytes()) <= {0, 255}
            box = alpha.getbbox()
            assert box is not None, f"Empty menu sprite {name}"
            if name in ("WEAPON", "ARMOR"):
                assert 54 + box[1] * 48 / ART_CELL >= 54, "Hero ink must not paint over the gear-selector row"
                assert 54 + box[3] * 48 / ART_CELL <= 102, "Hero ink must finish before caption ink"
            assert cell.convert("RGB").getcolors(maxcolors=24) is not None
            for size, advance in zip(ART_SIZES, (advance16, advance32, advance48)):
                assert int(advance) == math.floor(0.5 + box[2] * size / ART_CELL) + 1, f"Artwork anchor drift: {name}"
        # Raw and processed resources must not become the same tiny graphic.
        hashes = []
        for index in range(14, 24):
            hashes.append(hashlib.sha256(atlas.crop((index % 8 * 32, index // 8 * 32,
                                                    (index % 8 + 1) * 32, (index // 8 + 1) * 32)).tobytes()).hexdigest())
        assert len(set(hashes)) == 10
    for size in ART_SIZES:
        for y in ART_YS:
            provider = json.loads((PACK / f"assets/projects/font/core_menu_art_{size}_{y}.json").read_text())["providers"][0]
            assert provider["height"] == size and provider["ascent"] == 13 - y
            assert [ord(char) for char in "".join(provider["chars"])] == list(range(ART_BASE, ART_BASE + 32))
    digest = hashlib.sha256(b"".join(p.encode() + (PACK / p).read_bytes() for p in paths)).hexdigest()
    print(f"PASS: {len(paths)} assets, {len(glyphs)} private glyphs, {len(overrides)} scoped transparent HUD sprites, no global font overrides; content SHA256 {digest}")


if __name__ == "__main__":
    verify()
