"""狂戦士の甲冑 (warrior ultimate, Creator 2026-10-23): the armor the warrior turns into for ten seconds.

Derived from the tier-4 warrior armor so it keeps the same cut, recoloured into Guts's berserker armor: near-black
iron, rust-red where the brass was, the gem a burning red eye. Writes the body/leg equipment layers, the equipment
asset, and a helmet model + item on a recoloured copy of the material atlas.

Run: python3 scripts/build_berserker_armor.py
"""
from pathlib import Path
import colorsys
import json

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects"
SOURCE = "warrior_t4"
NAME = "berserker"

# The tier-4 warrior palette, colour by colour.
EXACT = {
    (62, 75, 92): (34, 30, 32),      # plate
    (21, 29, 41): (12, 10, 11),      # shadow
    (123, 145, 161): (78, 70, 72),   # lit edge
    (100, 119, 138): (56, 50, 52),   # mid plate
    (45, 34, 40): (60, 14, 12),      # cloth
    (174, 140, 77): (110, 24, 18),   # brass -> rust red
    (240, 123, 50): (255, 58, 42),   # gem -> the eye
}


def recolour(rgb):
    if rgb in EXACT:
        return EXACT[rgb]
    r, g, b = (c / 255 for c in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    lum = .3 * r + .59 * g + .11 * b
    if s > .35 and (h < .17 or h > .95):          # warm (brass, gold, orange): rust red, brightest ones burn
        return (int(60 + 195 * lum), int(14 + 44 * lum * lum), int(12 + 30 * lum * lum)) if lum > .55 else \
               (int(50 + 110 * lum), int(10 + 20 * lum), int(8 + 16 * lum))
    k = .1 + .45 * lum                             # everything else: dark iron, a warm grey
    return (int(255 * k * 1.05), int(255 * k * .95), int(255 * k))


def convert(src: Path, dst: Path):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = recolour((r, g, b)) + (a,)
    dst.parent.mkdir(parents=True, exist_ok=True)
    im.save(dst)


def write(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")


def main():
    eq = ASSETS / "textures/entity/equipment"
    convert(eq / f"humanoid/armor/{SOURCE}.png", eq / f"humanoid/armor/{NAME}.png")
    convert(eq / f"humanoid_leggings/armor/{SOURCE}.png", eq / f"humanoid_leggings/armor/{NAME}.png")
    write(ASSETS / f"equipment/armor/{NAME}.json",
          {"layers": {"humanoid": [{"texture": f"projects:armor/{NAME}"}], "humanoid_leggings": [{"texture": f"projects:armor/{NAME}"}]}})
    # The sculpted helmet: same model, its material atlas recoloured.
    helmet = json.loads((ASSETS / f"models/item/armor/{SOURCE}_helmet.json").read_text())
    atlas = helmet["textures"]["atlas"].split(":")[1]
    convert(ASSETS / f"textures/{atlas}.png", ASSETS / f"textures/item/armor/{NAME}_materials.png")
    helmet["textures"] = {k: f"projects:item/armor/{NAME}_materials" for k in helmet["textures"]}
    write(ASSETS / f"models/item/armor/{NAME}_helmet.json", helmet)
    write(ASSETS / f"items/armor/{NAME}_helmet.json", {"model": {"type": "minecraft:model", "model": f"projects:item/armor/{NAME}_helmet"}})


if __name__ == "__main__":
    main()
