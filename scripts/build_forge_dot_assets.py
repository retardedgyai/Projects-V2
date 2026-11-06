"""Bake the harbor forge's plates in the 冒険の手帳's pixel look.

    python3 scripts/build_forge_dot_assets.py [preview | preview-only]

Writes server-minestom/src/main/resources/polish05/forge.zip (private-namespace sprites, merged into the UI pack by
CoreUiPackServer) and polish05/forge.json (sprite chars and sizes, read by ForgeV3Scene). Like the passive tree
(藍 2026-11-04 「Forgeもリワークしないとね」): iron windows and plates from scripts/build_menu_dialog_assets.py at 2 design
px a dot, words in the passive tree's MaruMinya dot font ("ptdot"), no prose. Items are the game's own item models.

`preview` (after building) and `preview-only` run CorePolish05ForgeFlowTest, which writes the flow's real scene, and
render it with scripts/render_grimoire_preview.py to docs/proposals/forge-dot/forge-screen.png.
"""
from __future__ import annotations

import io
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_menu_dialog_assets import DARK_BTN, GOLD_BTN, slot_tile, window_tile  # noqa: E402
from build_passive_tree_assets import GOLD, INK, MUTE, OFF_BTN, RED_BTN, centred, nine, strip, word  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "server-minestom/src/main/resources/polish05"
NS = "projects_ui_polish05"
FONT = f"{NS}:forge"
FIRST_CHAR = 0xE000
# Sizes in design px; ForgeV3Scene places them.
LEFT_W, RIGHT_W, PANEL_H = 380, 460, 908
TRACK_W, TRACK_H = 888, 96
ROW_W, ROW_H = 332, 84
INNER_W = 412
COST_H, TOGGLE_H, BOX_W, BOX_H = 72, 64, 202, 104
GO_W, GO_H = 412, 72
CHIP_W, CHIP_H = 220, 44
MODAL_W, MODAL_H, MODAL_BTN_W, MODAL_BTN_H = 600, 400, 250, 60
STATS_W = 504


def hammer():
    """The header emblem: a dot hammer over an anvil, in a sunken slot."""
    img = nine(slot_tile(), 4, 52, 52)
    px = Image.new("RGBA", (13, 13)); d = ImageDraw.Draw(px)
    iron, dark, wood = (154, 158, 166, 255), (74, 76, 82, 255), (150, 104, 58, 255)
    d.rectangle([3, 1, 9, 3], fill=iron); d.rectangle([3, 3, 9, 3], fill=dark)
    d.line([(6, 4), (6, 7)], fill=wood)
    d.rectangle([1, 9, 11, 10], fill=iron); d.rectangle([3, 11, 9, 11], fill=dark); d.rectangle([1, 8, 2, 8], fill=iron)
    d.point((10, 6), fill=GOLD + (255,)); d.point((8, 7), fill=GOLD + (255,))
    return centred(img, px.resize((39, 39), Image.NEAREST))


def glow(n=160):
    """A white disc that thins out in ordered dither towards its rim: tinted, it is the item's heat. Drawn 480 design px
    wide, one texel is the passive tree's 3 px dot."""
    bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
    img = Image.new("RGBA", (n, n)); c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            r = ((x - c) ** 2 + (y - c) ** 2) ** .5 / (n / 2)
            if r < 1 and (1 - r) ** 1.5 * 8 > bayer[y % 4][x % 4] + 1:
                img.putpixel((x, y), (255, 255, 255, 255))
    return img


def ring(n=64):
    """A one-dot white ring (the hit and break burst)."""
    img = Image.new("RGBA", (n, n))
    ImageDraw.Draw(img).ellipse([1, 1, n - 2, n - 2], outline=(255, 255, 255, 255), width=2)
    return img


def switch(on):
    """The 集中鍛造 toggle: a sunken track and a knob, 2 design px a dot."""
    px = Image.new("RGBA", (22, 12)); d = ImageDraw.Draw(px)
    d.rectangle([0, 0, 21, 11], fill=(8, 8, 9, 255))
    d.rectangle([1, 1, 20, 10], fill=(150, 90, 30, 255) if on else (36, 36, 40, 255))
    kx = 11 if on else 1
    d.rectangle([kx, 1, kx + 9, 10], fill=(236, 230, 220, 255)); d.rectangle([kx, 9, kx + 9, 10], fill=(150, 146, 138, 255))
    return px.resize((44, 24), Image.NEAREST)


def footer():
    parts = []
    for k, t in (("左クリック", "選ぶ"), ("Shift", "閉じる")):
        cap = word(k, MUTE)
        parts.append(strip([centred(nine(DARK_BTN, 5, cap.width + 16, 34), cap), word(t, MUTE)], 8))
    return strip(parts, 26)


def plates():
    win = window_tile(fill=(17, 17, 19, 255))
    out = {"panel_left": nine(win, 8, LEFT_W, PANEL_H), "panel_right": nine(win, 8, RIGHT_W, PANEL_H),
           "track": nine(win, 8, TRACK_W, TRACK_H), "modal": nine(win, 8, MODAL_W, MODAL_H),
           "chip": nine(slot_tile(), 4, CHIP_W, CHIP_H), "brand": hammer(),
           "row": nine(slot_tile(), 4, ROW_W, ROW_H), "row_on": nine(slot_tile(True), 4, ROW_W, ROW_H),
           "slot60": nine(slot_tile(), 4, 60, 60), "slot44": nine(slot_tile(), 4, 44, 44),
           "stats": nine(slot_tile(), 4, STATS_W, 2 * 40 + 20), "stats1": nine(slot_tile(), 4, STATS_W, 40 + 20),
           "box": nine(slot_tile(), 4, BOX_W, BOX_H), "box_risk": nine(slot_tile(True), 4, BOX_W, BOX_H),
           "cost": nine(slot_tile(), 4, INNER_W, COST_H),
           "toggle": nine(slot_tile(), 4, INNER_W, TOGGLE_H), "toggle_on": nine(slot_tile(True), 4, INNER_W, TOGGLE_H),
           "switch_off": switch(False), "switch_on": switch(True),
           "go_take": nine(GOLD_BTN, 5, GO_W, GO_H), "go_risk": nine(RED_BTN, 5, GO_W, GO_H), "go_off": nine(OFF_BTN, 5, GO_W, GO_H),
           "modal_cancel": nine(DARK_BTN, 5, MODAL_BTN_W, MODAL_BTN_H), "modal_confirm": nine(RED_BTN, 5, MODAL_BTN_W, MODAL_BTN_H),
           "toast": nine(win, 8, 360, 84), "footer": footer(), "glow": glow(), "ring": ring()}
    return out


def main(preview: bool) -> None:
    files: dict[str, bytes] = {}
    providers, sprites = [], {}
    code = [FIRST_CHAR]

    def add(name, image):
        assert image.width <= 256 and image.height <= 256, (name, image.size)
        char = chr(code[0]); code[0] += 1
        rel = f"forge/{name}.png"
        assert re.fullmatch(r"[a-z0-9/._-]+", rel), rel
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height, "height": image.height, "chars": [char]})
        sprites[name] = {"char": char, "font": FONT, "width": image.width, "height": image.height}
        return name

    layout = {}
    for name, img in plates().items():
        # Glyphs over 256 px do not render: big plates are cut into pieces.
        parts = []
        for py in range(0, img.height, 256):
            for px in range(0, img.width, 256):
                piece = img.crop((px, py, min(px + 256, img.width), min(py + 256, img.height)))
                parts.append({"sprite": add(f"{name}_{px}_{py}", piece), "x": px, "y": py})
        layout[name] = parts
    files[f"assets/{NS}/font/forge.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()
    with zipfile.ZipFile(OUT / "forge.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    (OUT / "forge.json").write_text(json.dumps({"sprites": sprites, "plates": layout}, ensure_ascii=False), encoding="utf-8")
    print(f"FORGE_PACK_BUILT sprites={len(sprites)} bytes={(OUT / 'forge.zip').stat().st_size}")
    if preview:
        render_preview()


def render_preview():
    """The flow's own scene (written by CorePolish05ForgeFlowTest) drawn the way the client draws it."""
    subprocess.run([str(Path.home() / "ProjectS/bin/heavy"), "./gradlew", ":server-minestom:test", "--offline", "--console=plain",
                    "--tests", "dev.projects.server.coreloop.CorePolish05ForgeFlowTest"], cwd=ROOT, check=True)
    out = ROOT / "docs/proposals/forge-dot"; out.mkdir(parents=True, exist_ok=True)
    for scene in sorted((ROOT / "server-minestom/build/forge-preview").glob("*.json")):
        dst = out / f"forge-{scene.stem}.png"
        subprocess.run([sys.executable, str(ROOT / "scripts/render_grimoire_preview.py"), str(ROOT), str(scene), str(dst), "2.4"], check=True)
        shot = Image.open(dst); top = round((480 - 1080 * 800 / 1920) / 2 * 2.4)
        shot.crop((0, top, 1920, top + 1080)).save(dst)
        print(f"FORGE_PREVIEW {dst}")


if __name__ == "__main__":
    if sys.argv[1:] == ["preview-only"]:
        render_preview()
    else:
        main(preview=sys.argv[1:] == ["preview"])
