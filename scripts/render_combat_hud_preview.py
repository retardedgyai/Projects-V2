"""Compose the combat HUD from exported component layouts; a design aid, not a game capture.

    ./gradlew :server-minestom:test --tests '*CoreHudLayoutTest*'   (writes build/hud-preview/snapshots)
    python scripts/render_combat_hud_preview.py

Every glyph is drawn from the generated pack (font JSON + sheet) at the x the Kotlin component puts
it and at the row its provider ascent gives it, scaled the way the client scales it (the 32 px
skill art lands in 16 GUI px). Numbers come from minecraft:font/ascii.png and vanilla-font text is
approximated (unifont is not on this machine; a 16 px aliased gothic stands in), both read from the
local Minecraft 26.2 client jar / system fonts for context only — never copied into the repository.
Text keeps the vanilla drop shadow (colour / 4, one GUI px down-right) where the component has it.

Outputs in build/hud-preview/: screen-<state>-2x.png and -4x.png (480x270 GUI = GUI scale 4 at
1920x1080), hud-<state>-8x.png close-ups, classes-8x.png (the six level diamonds), boss-8x.png.
"""
from pathlib import Path
import json
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets"
OUT = ROOT / "build/hud-preview"
JAR = Path(os.environ.get("APPDATA", "")) / ".minecraft/versions/26.2/26.2.jar"
W, H = 480, 270
S = 4  # screen pixels per GUI pixel
sys.path.insert(0, str(Path(__file__).resolve().parent))


def jar_png(path):
    with zipfile.ZipFile(JAR) as jar, jar.open(path) as f:
        return Image.open(f).convert("RGBA")


def width_of(text):
    """Mirror of CoreUiComponents.width (vanilla default font)."""
    total = 0
    for ch in text:
        if ch == " ":
            total += 4
        elif ch in "i!.,:;|'":
            total += 2
        elif ch == "l":
            total += 3
        elif ch in "I[]t":
            total += 4
        elif ch in "fk<>(){}":
            total += 5
        elif ch in "@~":
            total += 7
        else:
            total += 6 if 0x21 <= ord(ch) <= 0x7E else 9
    return total


class Fonts:
    def __init__(self):
        self.fonts, self.sheets = {}, {}
        self.jp = ImageFont.truetype("C:/Windows/Fonts/BIZ-UDGothicR.ttc", 15)

    def sheet(self, file):
        if file not in self.sheets:
            ns, rel = file.split(":")
            if ns == "minecraft":
                self.sheets[file] = jar_png(f"assets/minecraft/textures/{rel}")
            else:
                with Image.open(ASSETS / ns / "textures" / rel) as image:
                    self.sheets[file] = image.convert("RGBA")
        return self.sheets[file]

    def glyph(self, font, code):
        """(image at its cell size, display height, ascent) or None for spaces."""
        ns, name = font.split(":")
        if font not in self.fonts:
            self.fonts[font] = json.loads((ASSETS / ns / f"font/{name}.json").read_text())["providers"]
        char = chr(code)
        for provider in self.fonts[font]:
            if provider["type"] == "space":
                if char in provider["advances"]:
                    return None
                continue
            for row, chars in enumerate(provider["chars"]):
                if char in chars:
                    sheet = self.sheet(provider["file"])
                    cw, ch = sheet.width // len(chars), sheet.height // len(provider["chars"])
                    col = chars.index(char)
                    return sheet.crop((col * cw, row * ch, (col + 1) * cw, (row + 1) * ch)), provider["height"], provider["ascent"]
        raise ValueError(f"Missing glyph {font} U+{code:04X}")

    def vanilla(self, code):
        """Vanilla default-font glyph as an 8 GUI px tall mask at 2 texels per GUI px."""
        if code < 0x80:
            ascii_png = self.sheet("minecraft:font/ascii.png")
            cell = ascii_png.crop((code % 16 * 8, code // 16 * 8, code % 16 * 8 + 8, code // 16 * 8 + 8))
            return cell.resize((16, 16), Image.NEAREST)
        mask = Image.new("L", (16, 16))
        d = ImageDraw.Draw(mask)
        d.fontmode = "1"
        d.text((0, -self.jp.getbbox("国")[1] + 1), chr(code), font=self.jp, fill=255)
        img = Image.new("RGBA", (16, 16), (255, 255, 255, 0))
        img.putalpha(mask)
        return img


def tint(image, colour, factor=1.0):
    r, g, b = ((colour >> 16) & 255, (colour >> 8) & 255, colour & 255)
    r, g, b = int(r * factor), int(g * factor), int(b * factor)
    out = image.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            pr, pg, pb, pa = px[x, y]
            if pa:
                px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


def draw(screen, fonts, snapshot, row_top):
    """row_top(ascent) -> GUI y of a bitmap glyph top; vanilla text uses row_top(None)."""
    cx = W // 2
    for g in snapshot["glyphs"]:
        if g["font"] == "":
            image, gui_h, top = fonts.vanilla(g["code"]), 8, row_top(None)
        else:
            found = fonts.glyph(g["font"], g["code"])
            if found is None:
                continue
            image, gui_h, ascent = found
            top = row_top(ascent)
        scale = gui_h / image.height
        size = (max(1, round(image.width * scale * S)), max(1, round(image.height * scale * S)))
        big = image.resize(size, Image.NEAREST)
        x, y = (cx + g["x"]) * S, top * S
        if g.get("shadow"):
            screen.alpha_composite(tint(big, g["color"], .25), (x + S, y + S))
        screen.alpha_composite(tint(big, g["color"]), (x, y))


def actionbar_top(ascent):
    return H - 72 if ascent is None else H - 65 - ascent


def boss_top(ascent):
    return 3 if ascent is None else 10 - ascent


def screen_for(fonts, actionbar, boss=None):
    import mock_combat_hud_directions as scene
    world = scene.world()
    world.alpha_composite(jar_png("assets/minecraft/textures/gui/sprites/hud/crosshair.png"), (W // 2 - 7, H // 2 - 7))
    screen = world.resize((W * S, H * S), Image.NEAREST)
    hud = ASSETS / "minecraft/textures/gui/sprites/hud"
    with Image.open(hud / "hotbar.png") as bar, Image.open(hud / "hotbar_selection.png") as sel:
        layer = Image.new("RGBA", (W, H))
        layer.alpha_composite(bar.convert("RGBA"), (W // 2 - 91, H - 22))
        layer.alpha_composite(jar_png("assets/minecraft/textures/item/iron_sword.png"), (W // 2 - 91 + 3, H - 19))
        layer.alpha_composite(jar_png("assets/minecraft/textures/item/potion.png"), (W // 2 - 91 + 3 + 140, H - 19))
        layer.alpha_composite(sel.convert("RGBA"), (W // 2 - 92, H - 23))
        screen.alpha_composite(layer.resize((W * S, H * S), Image.NEAREST))
    draw(screen, fonts, actionbar, actionbar_top)
    if boss:
        draw(screen, fonts, boss, boss_top)
    return screen


def main():
    snapshots = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted((OUT / "snapshots").glob("*.json"))}
    assert snapshots, "Run CoreHudLayoutTest first"
    fonts = Fonts()
    OUT.mkdir(parents=True, exist_ok=True)
    states = [k.split("-", 1)[1] for k in snapshots if k.startswith("actionbar-")]
    for state in states:
        boss = snapshots.get("boss-boss") if state == "combat" else None
        image = screen_for(fonts, snapshots[f"actionbar-{state}"], boss)
        image.save(OUT / f"screen-{state}-4x.png")
        image.resize((W * 2, H * 2), Image.NEAREST).save(OUT / f"screen-{state}-2x.png")
        image.crop(((W // 2 - 100) * S, (H - 75) * S, (W // 2 + 100) * S, H * S)).resize((200 * 8, 75 * 8), Image.NEAREST) \
            .save(OUT / f"hud-{state}-8x.png")
    crops = []
    for state in [s for s in states if s.startswith("class-")]:
        image = screen_for(fonts, snapshots[f"actionbar-{state}"])
        crops.append(image.crop(((W // 2 - 40) * S, (H - 62) * S, (W // 2 + 40) * S, (H - 18) * S)))
    if crops:
        cw, ch = crops[0].size
        sheet = Image.new("RGBA", (cw * 3 + 8, ch * 2 + 4), (20, 20, 20, 255))
        for k, crop in enumerate(crops):
            sheet.alpha_composite(crop, ((k % 3) * (cw + 4), (k // 3) * (ch + 4)))
        sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).save(OUT / "classes-8x.png")
    if "boss-boss" in snapshots:
        image = screen_for(fonts, snapshots["actionbar-ready"], snapshots["boss-boss"])
        image.crop(((W // 2 - 120) * S, 0, (W // 2 + 120) * S, 30 * S)).resize((240 * 8, 30 * 8), Image.NEAREST).save(OUT / "boss-8x.png")
    print(f"Rendered {len(states)} HUD states into {OUT}")


if __name__ == "__main__":
    main()
