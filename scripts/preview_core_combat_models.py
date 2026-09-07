"""Render the shipped JSON cuboid geometry for QA (not a Minecraft screenshot).

No concept art or new texture painting: faces are projected directly from pack models.
"""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw
from build_core_combat_models import PALETTES, SHAPES

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
COLORS = {"white_concrete": "#eeeef0", "light_gray_concrete": "#969a9a", "gray_concrete": "#3b4044",
          "yellow_concrete": "#f1b817", "orange_concrete": "#e16816", "magenta_concrete": "#ab30a0",
          "purple_concrete": "#6a239c", "light_blue_concrete": "#259ad0", "blue_concrete": "#303292",
          "red_concrete": "#962d29", "lime_concrete": "#67ad18", "green_concrete": "#4d5e21", "cyan_concrete": "#167b82"}


def render(parts, cx, cy, scale=36):
    polygons = []
    for shape, palette, size, offset, pitch, yaw, roll in parts:
        path = PACK / f"assets/projects/models/combat_vfx/{shape}_{palette}.json"
        model = json.loads(path.read_text())
        def transform(v):
            x, y, z = [(v[i] - 8) / 16 * size[i] for i in range(3)]
            y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
            x, y = x * math.cos(roll) - y * math.sin(roll), x * math.sin(roll) + y * math.cos(roll)
            x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
            x, y, z = x + offset[0], y + offset[1], z + offset[2]
            return (cx + (x * .94 + z * .34) * scale, cy + (z * .53 - x * .19 - y * .82) * scale, z * .77 - x * .28 + y * .58)
        for e in model["elements"]:
            lo, hi = e["from"], e["to"]
            assert all(0 <= v <= 16 for v in lo + hi)
            vertices = [transform([hi[0] if i & 1 else lo[0], hi[1] if i & 2 else lo[1], hi[2] if i & 4 else lo[2]]) for i in range(8)]
            for ids in ((0,1,3,2), (4,6,7,5), (0,4,5,1), (2,3,7,6), (0,2,6,4), (1,5,7,3)):
                points = [vertices[i] for i in ids]
                key = e["faces"]["up"]["texture"][1:]
                color = COLORS[model["textures"][key].split("/")[-1]]
                polygons.append((sum(p[2] for p in points) / 4, [(p[0], p[1]) for p in points], color))
    return sorted(polygons, reverse=True)


def main():
    # Validate every generated model, not only the representative views.
    index = set((PACK / "index.txt").read_text().splitlines())
    for shape in SHAPES:
        for palette in PALETTES:
            path = f"assets/projects/models/combat_vfx/{shape}_{palette}.json"
            model = json.loads((PACK / path).read_text())
            assert path in index and 1 < len(model["elements"]) < 400
            assert all(all(-16 <= n <= 32 for n in e["from"] + e["to"]) for e in model["elements"])
            assert all(e["to"][i] > e["from"][i] for e in model["elements"] for i in range(3))
    image = Image.new("RGB", (1200, 740), "#17202b")
    draw = ImageDraw.Draw(image)
    scenes = [
        ("SLASH / continuous edge", [("crescent", "gold", (7.8,7.8,7.8), (0,1,0), 0, -.25, -.28), ("crescent", "gold", (6.4,6.4,6.4), (0,1.15,0), 0, -.43, -.28)]),
        ("STARFALL / nucleus + orbital rings", [("star", "astral", (2.5,2.5,2.5), (0,1.5,0), math.pi/2, 0, 0), ("orbit", "astral", (6.8,6.8,6.8), (0,1.2,0), .38, 0, 0), ("orbit", "astral", (5.2,5.2,5.2), (0,1.6,0), -.65, .5, 0)]),
        ("ICE / rising shards", [("lance", "ice", (2.8,2.8,4.8), (math.sin(a)*2.6,.65,math.cos(a)*2.6), -.7, a, 0) for a in (-1,-.5,0,.5,1)]),
        ("ASTRAL NEEDLE / solid ray and impact", [("lance", "astral", (4.5,4.5,7), (0,1,0), 0, -.8, 0), ("star", "astral", (1.6,1.6,1.6), (-2.5,1,2.45), math.pi/2, -.8, 0)]),
    ]
    for i, (label, parts) in enumerate(scenes):
        left, top = i % 2 * 600, i // 2 * 340
        draw.text((left + 20, top + 30), label, fill="#f1e7cb")
        for n in range(-4,5):
            draw.line((left+80,top+200+n*16,left+520,top+200+n*16), fill="#25313b")
        for _, points, color in render(parts, left + 300, top + 215):
            draw.polygon(points, fill=color)
    draw.text((20,710), "JSON geometry QA projection. No particles, game lighting or bloom; not a Minecraft screenshot.", fill="#acb6c0")
    out = ROOT / ".tools/combat-mesh-preview.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out)
    print(f"Validated 49 meshes + item definitions; preview: {out}")


if __name__ == "__main__":
    main()
