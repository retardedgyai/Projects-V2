"""Read-only structural checks for the optional Vanilla 26.2 UI pack."""
from pathlib import Path
import hashlib
import json
import struct
import zlib
from PIL import Image
from build_core_hud_assets import vanilla_overrides

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
    digest = hashlib.sha256(b"".join(p.encode() + (PACK / p).read_bytes() for p in paths)).hexdigest()
    print(f"PASS: {len(paths)} assets, {len(glyphs)} private glyphs, {len(overrides)} scoped transparent HUD sprites, no global font overrides; content SHA256 {digest}")


if __name__ == "__main__":
    verify()
