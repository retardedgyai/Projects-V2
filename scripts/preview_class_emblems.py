"""Import generated class emblems and preview them in the HUD's centre diamond.

    python scripts/preview_class_emblems.py

Inputs: .tools/class-emblem-generation/<id>.png, one unmodified image_gen output per class listed
in assets/core-ui/proposals/2026-10-16-class-emblems/prompts.json. Each is reduced to its 32x32
logical pixel grid with nearest-neighbour sampling, the flat charcoal background is keyed out,
alpha is made binary, and the result is clipped to the diamond's inner backing.

Outputs (in that proposals folder): <id>-32.png per class, sheet-8x.png (all classes in their
diamonds) and hud-<id>.png (each seated in the F1 HUD at GUI scale 4). Missing inputs are listed
and skipped; nothing is drawn in their place.
"""
from pathlib import Path
import json
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
from mock_class_icons_v6 import diamond_hi, shadowed  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
PROPOSAL = ROOT / "assets/core-ui/proposals/2026-10-16-class-emblems"
RAW = ROOT / ".tools/class-emblem-generation"
GRID = 32
BG = (14, 14, 16)


def to_grid(path):
    with Image.open(path) as src:
        img = src.convert("RGBA")
    img = img.resize((GRID, GRID), Image.Resampling.NEAREST)
    for y in range(GRID):
        for x in range(GRID):
            r, g, b, a = img.getpixel((x, y))
            if a < 128 or abs(r - BG[0]) + abs(g - BG[1]) + abs(b - BG[2]) < 30:
                img.putpixel((x, y), (0, 0, 0, 0))
            else:
                img.putpixel((x, y), (r, g, b, 255))
    return img


def clip(img, r_inner_gui=10):
    out = img.copy()
    half = GRID / 2
    for y in range(GRID):
        for x in range(GRID):
            if abs(x + .5 - half) + abs(y + .5 - half) > r_inner_gui * 2 - .5:
                out.putpixel((x, y), (0, 0, 0, 0))
    return out


def seat(screen, accent, icon, cx, cy, s):
    screen.alpha_composite(diamond_hi(1, accent).resize((27 * s, 27 * s), Image.NEAREST),
                           ((cx - 13) * s, (cy - 13) * s))
    ic = shadowed_any(icon).resize(((GRID + 2) * s // 2, (GRID + 2) * s // 2), Image.NEAREST)
    screen.alpha_composite(ic, (cx * s - GRID * s // 4, cy * s - GRID * s // 4))


def shadowed_any(icon):
    out = Image.new("RGBA", (icon.width + 2, icon.height + 2))
    for y in range(icon.height):
        for x in range(icon.width):
            if icon.getpixel((x, y))[3]:
                out.putpixel((x + 2, y + 2), (44, 28, 28, 255))
    out.alpha_composite(icon, (0, 0))
    return out


def main():
    spec = json.loads((PROPOSAL / "prompts.json").read_text(encoding="utf-8"))
    ready, missing = [], []
    for item in spec["queue"]:
        src = RAW / f"{item['id']}.png"
        if not src.is_file():
            missing.append(item["id"])
            continue
        icon = clip(to_grid(src))
        icon.save(PROPOSAL / f"{item['id']}-32.png")
        ready.append((item, icon))
    if missing:
        print("missing generated sources:", ", ".join(missing), f"(expected in {RAW})")
    if not ready:
        return
    s = 8
    sheet = Image.new("RGBA", ((len(ready) * 34 + 4) * s, 34 * s), hud.D["plate"])
    for k, (item, icon) in enumerate(ready):
        seat(sheet, item["accent"], icon, 19 + k * 34, 17, s)
    sheet.save(PROPOSAL / "sheet-8x.png")
    for item, icon in ready:
        base, icons, top, jp = hud.design_f1(center=-1)
        cy = hud.HB_Y - 5 - 13 + 4
        screen = hud.compose((base, icons, Image.new("RGBA", (hud.W, hud.H)), jp), 4)
        seat(screen, item["accent"], icon, hud.C, cy, 4)
        screen.alpha_composite(top.resize((hud.W * 4, hud.H * 4), Image.NEAREST))
        lv = Image.new("RGBA", (hud.W, hud.H))
        hud.mc_text(lv, hud.C - hud.mc_width("12") // 2 + 1, cy + 10, "12", hud.rgba("80ff20"), outline=True)
        screen.alpha_composite(lv.resize((hud.W * 4, hud.H * 4), Image.NEAREST))
        screen.crop((540, 720, 1380, 1080)).save(PROPOSAL / f"hud-{item['id']}.png")
    print(f"wrote {len(ready)} emblems into {PROPOSAL}")


if __name__ == "__main__":
    main()
