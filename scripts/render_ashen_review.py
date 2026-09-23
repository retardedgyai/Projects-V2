"""Render large orthographic review views of the authored Ashen Knight."""

from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "vendor" / "scorpius" / "bbmodel"))
import preview_bbmodel as preview  # noqa: E402

source = ROOT / "model-lab" / "models" / "ashen_knight.bbmodel"
target = ROOT / "model-lab" / "build" / "previews" / "ashen_knight_review.png"
data, elements, atlas, animations = preview.load(source)
views = [("idle", 0, "front"), ("idle", 0, "side"),
         ("idle", 0, "top"), ("cleave", .9, "front")]
tiles = [(f"{name} {view}", preview.render(data, elements, atlas,
          animations[name], at, view, 10)) for name, at, view in views]
width = max(image.width for _, image in tiles) * 2 + 36
height = max(image.height for _, image in tiles) * 2 + 56
sheet = Image.new("RGBA", (width, height), (18, 19, 25, 255))
draw = ImageDraw.Draw(sheet)
for index, (label, tile) in enumerate(tiles):
    x = 12 + (index % 2) * (width // 2)
    y = 12 + (index // 2) * (height // 2)
    draw.text((x, y), label, fill=(240, 235, 227, 255))
    sheet.paste(tile, (x, y + 16))
target.parent.mkdir(parents=True, exist_ok=True)
sheet.save(target)
print(target)
