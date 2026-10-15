"""Compose the pixel combat HUD from exported component layouts; a design aid, not a game capture.

    ./gradlew :server-minestom:test --tests '*CoreHudLayoutTest*'   (writes build/hud-preview/snapshots)
    python scripts/render_combat_hud_preview.py

Every glyph comes from the generated pack (font JSON + sheet) at the x the Kotlin component puts
it, with the vertical position its provider ascent gives it, at one texel per GUI pixel.
If the local Minecraft 26.2 client jar is present, its hotbar/crosshair sprites are drawn for
context only (read locally, never copied into the repository or the pack).

Outputs in build/hud-preview/: screen-<state>.png at 1x/2x/3x (480x270 GUI = GUI scale 4 at
1920x1080), hud-<state>-8x.png close-ups, world-8x.png (nameplates, damage), boss-8x.png.
"""
from pathlib import Path
import json
import os
import zipfile

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
OUT = ROOT / "build/hud-preview"
JAR = Path(os.environ.get("APPDATA", "")) / ".minecraft/versions/26.2/26.2.jar"


class Glyphs:
    def __init__(self):
        self.fonts, self.sheets = {}, {}

    def get(self, font, code):
        name = font.split(":")[1]
        if name not in self.fonts:
            self.fonts[name] = json.loads((ASSETS / f"font/{name}.json").read_text())["providers"]
        char = chr(code)
        for provider in self.fonts[name]:
            for row, chars in enumerate(provider.get("chars", [])):
                if char in chars:
                    file = provider["file"].split(":")[1]
                    if file not in self.sheets:
                        with Image.open(ASSETS / f"textures/{file}") as sheet:
                            self.sheets[file] = sheet.convert("RGBA")
                    sheet = self.sheets[file]
                    w, h = sheet.width // len(chars), sheet.height // len(provider["chars"])
                    assert h == provider["height"], f"{file}: texel must equal GUI pixel"
                    col = chars.index(char)
                    return sheet.crop((col * w, row * h, (col + 1) * w, (row + 1) * h)), provider["ascent"]
        raise ValueError(f"Missing glyph {font} U+{code:04X}")


def draw(canvas, glyphs, snapshot, anchor_x, baseline):
    """baseline: the y the client passes as text y; bitmap top = y + 7 - ascent."""
    for g in snapshot["glyphs"]:
        image, ascent = glyphs.get(g["font"], g["code"])
        tint = Image.new("RGBA", image.size, "#%06x" % g["color"])
        image = ImageChops.multiply(image, tint)
        canvas.alpha_composite(image, (anchor_x + g["x"], baseline + 7 - ascent))


def vanilla(path):
    if not JAR.is_file():
        return None
    with zipfile.ZipFile(JAR) as jar:
        try:
            with jar.open(f"assets/minecraft/textures/gui/sprites/{path}.png") as file:
                return Image.open(file).convert("RGBA")
        except KeyError:
            return None


def scene(width, height):
    canvas = Image.new("RGBA", (width, height))
    for y in range(height):
        for x in range(width):
            if y < height * 0.55:
                t = y / (height * 0.55)
                canvas.putpixel((x, y), (int(118 + 40 * t), int(160 + 30 * t), int(214 + 10 * t), 255))
            else:
                checker = ((x // 16) + (y // 16)) % 2
                canvas.putpixel((x, y), (86, 124, 58, 255) if checker else (78, 114, 52, 255))
    return canvas


def screen(glyphs, actionbar, boss=None, width=480, height=270):
    canvas = scene(width, height)
    centre = width // 2
    hotbar, selection, crosshair = vanilla("hud/hotbar"), vanilla("hud/hotbar_selection"), vanilla("hud/crosshair")
    if hotbar:
        canvas.alpha_composite(hotbar, (centre - 91, height - 22))
        canvas.alpha_composite(selection, (centre - 92, height - 23))
    else:
        canvas.paste((40, 40, 40, 255), (centre - 91, height - 22, centre + 91, height))
    if crosshair:
        canvas.alpha_composite(crosshair, (centre - 7, height // 2 - 7))
    draw(canvas, glyphs, actionbar, centre, height - 72)
    if boss:
        draw(canvas, glyphs, boss, centre, 3)
    return canvas


def save_scaled(image, path, scale):
    image.resize((image.width * scale, image.height * scale), Image.Resampling.NEAREST).save(path)


def main():
    snapshots = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted((OUT / "snapshots").glob("*.json"))}
    assert snapshots, "Run CoreHudLayoutTest first"
    glyphs = Glyphs()
    OUT.mkdir(parents=True, exist_ok=True)
    actionbars = [k for k in snapshots if k.startswith("actionbar-")]
    for key in actionbars:
        state = key.split("-", 1)[1]
        image = screen(glyphs, snapshots[key], snapshots.get("boss-boss") if state == "combat" else None)
        image.save(OUT / f"screen-{state}-1x.png")
        save_scaled(image, OUT / f"screen-{state}-2x.png", 2)
        save_scaled(image, OUT / f"screen-{state}-3x.png", 3)
        save_scaled(image.crop((240 - 130, 270 - 120, 240 + 130, 270)), OUT / f"hud-{state}-8x.png", 8)
    # Smaller screens: GUI scale 3 at 1280x720 (427x240) is the tightest common case.
    tight = screen(glyphs, snapshots["actionbar-combat"], snapshots.get("boss-boss"), 426, 240)
    save_scaled(tight, OUT / "screen-combat-426x240-2x.png", 2)
    if "boss-boss" in snapshots:
        top = screen(glyphs, snapshots["actionbar-ready"], snapshots["boss-boss"]).crop((240 - 110, 0, 240 + 110, 30))
        save_scaled(top, OUT / "boss-8x.png", 8)
    world = scene(200, 70)
    for index, key in enumerate(k for k in snapshots if k.startswith("nametag-")):
        draw(world, glyphs, snapshots[key], 50 + index * 100, 10)
    for index, key in enumerate(k for k in snapshots if k.startswith("damage-")):
        draw(world, glyphs, snapshots[key], 40 + index * 60, 50)
    world.save(OUT / "world-1x.png")
    save_scaled(world, OUT / "world-8x.png", 8)
    print(f"Rendered {len(actionbars)} HUD states into {OUT}")


if __name__ == "__main__":
    main()
