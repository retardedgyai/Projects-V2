"""Offline isometric review of the shipped cuboid JSON. Not a Minecraft screenshot."""
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
OUT = ROOT / "assets/first-magic/model-sheet.png"


def tint(rgb, factor):
    return tuple(max(0, min(255, int(v * factor))) for v in rgb[:3]) + (255,)


def palette(name):
    im = Image.open(ASSET / f"textures/item/first_magic/model/{name}.png").convert("RGBA")
    values = [p for p in im.get_flattened_data() if p[3] > 0]
    return tuple(sum(pixel[c] for pixel in values) // len(values) for c in range(3))


def render(name, scale=(1, 1, 1)):
    data = json.loads((ASSET / f"models/first_magic/{name}.json").read_text())
    image = Image.new("RGBA", (360, 340), "#1d272b")
    draw = ImageDraw.Draw(image)
    sx, sy, sz = scale

    def project(x, y, z):
        near_z = 16 - z
        return (180 + (x * sx - near_z * sz) * 7.3, 239 + (x * sx + near_z * sz) * 3.2 - y * sy * 8.2)

    ordered = sorted(data["elements"], key=lambda e:
                     (e["from"][0] + e["to"][0]) + (32 - e["from"][2] - e["to"][2])
                     + (e["from"][1] + e["to"][1]) * .8)
    for element in ordered:
        x1, y1, z1 = element["from"]
        x2, y2, z2 = element["to"]
        texture = element["faces"]["up"]["texture"].removeprefix("#")
        color = palette(texture)
        # Three visible faces from the entry path (north-east camera).
        draw.polygon([project(x2,y1,z1),project(x2,y1,z2),project(x2,y2,z2),project(x2,y2,z1)],
                     fill=tint(color,.66), outline=tint(color,.45))
        draw.polygon([project(x1,y1,z1),project(x2,y1,z1),project(x2,y2,z1),project(x1,y2,z1)],
                     fill=tint(color,.82), outline=tint(color,.5))
        draw.polygon([project(x1,y2,z1),project(x2,y2,z1),project(x2,y2,z2),project(x1,y2,z2)],
                     fill=tint(color,1.08), outline=tint(color,.63))
    return image


def main():
    panels = [("research_desk", (1.1,.85,1), "RESEARCH DESK"),
              ("crude_distiller", (1,1,1), "CRUDE DISTILLER"),
              ("jar_shelf", (1.5,1,1), "JAR SHELF"),
              ("jar_tide_high", (1,1,1), "TIDE JAR")]
    sheet = Image.new("RGBA", (720, 740), "#182126")
    d = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for i, (model, scale, title) in enumerate(panels):
        x, y = (i % 2) * 360, (i // 2) * 370
        sheet.alpha_composite(render(model, scale), (x, y))
        d.rectangle((x+12, y+12, x+348, y+350), outline="#ac8655", width=2)
        d.text((x+22, y+23), title, fill="#ead8ae", font=font)
    sheet.save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
