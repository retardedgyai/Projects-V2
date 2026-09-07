"""Pixel-preserving QA only; never creates or repaints sprite artwork."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "scripts"))
from build_core_hud_assets import skill_frame

sheet = Image.new("RGB", (760, 420), "#181b21")
draw = ImageDraw.Draw(sheet)
draw.text((20, 12), "Original proposals / actual pixels + nearest-neighbor enlargement", fill="#e6deca")
draw.text((20, 33), "Preview only - not installed. Existing HUD frame and cooldown compositor.", fill="#aaa79e")
for index, name in enumerate(("slash", "flame", "guard")):
    x = 24 + index * 248
    source = Image.open(HERE / f"{name}-source.png").convert("RGBA")
    master = source.resize((32, 32), Image.Resampling.NEAREST)
    master.save(HERE / f"{name}-32.png")
    small = source.resize((16, 16), Image.Resampling.NEAREST)
    small.save(HERE / f"{name}-16.png")
    assert master.size == (32, 32) and small.size == (16, 16)
    draw.text((x, 68), name.upper(), fill="#e6deca")
    sheet.paste(master.resize((128, 128), Image.Resampling.NEAREST), (x, 90))
    draw.text((x, 225), "32px x4", fill="#aaa79e")
    sheet.paste(small, (x, 260))
    sheet.paste(master, (x+70, 252))
    draw.text((x, 288), "16px       32px", fill="#aaa79e")
    fitted = source.resize((26, 26), Image.Resampling.NEAREST)
    tile = Image.new("RGBA", (28, 28))
    tile.alpha_composite(fitted, (1, 1))
    for offset, frame in enumerate((0, 10, 20)):
        hud = skill_frame(tile, frame)
        sheet.paste(hud.resize((64, 64), Image.Resampling.NEAREST), (x+offset*70, 325))
    draw.text((x, 397), "HUD x2: ready / half / full cooldown", fill="#aaa79e")
sheet.save(HERE / "comparison.png")
print("PASS: 3 source assets, 3 x 16px, 3 x 32px; HUD state composition rendered")
