"""Read-only structural checks for the optional Vanilla 26.2 UI pack."""
from pathlib import Path
import hashlib
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"


def verify():
    paths = (PACK / "index.txt").read_text(encoding="utf-8").splitlines()
    assert len(paths) == len(set(paths)) and paths == sorted(paths)
    assert not any(p.startswith("assets/minecraft/") or ".." in p for p in paths), "Global override is forbidden"
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
            for char in chars:
                assert 0xE000 <= ord(char) <= 0xF8FF, "Custom font must not claim Japanese or Latin characters"
                key = (path.name, char)
                assert key not in glyphs, "Duplicate glyph"
                glyphs.add(key)
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
        texture = model["textures"]["layer0"].replace("projects:", "assets/projects/textures/")
        assert (PACK / f"{texture}.png").is_file()
    digest = hashlib.sha256(b"".join(p.encode() + (PACK / p).read_bytes() for p in paths)).hexdigest()
    print(f"PASS: {len(paths)} assets, {len(glyphs)} private glyphs, no global font overrides; content SHA256 {digest}")


if __name__ == "__main__":
    verify()
