"""Carry the approved selected-gear light treatment into forge details.

The selected row is the source of the left-edge accent and fading gradient.
Text, amounts, icons and their hitboxes remain live Minestom entities.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image


OUT = Path(__file__).resolve().parents[1] / "web-ui-lab/ui/polish05-effects"
BACKDROP = (18, 22, 25)
SOURCE = OUT / "gear_selected.png"


def selection_light(width: int, height: int, theme: str) -> Image.Image:
    approved = Image.open(SOURCE).convert("RGB").resize((width, height), Image.Resampling.BILINEAR)
    sample = approved.load()
    for y in range(height):
        end = sample[width - 36, y]
        for x in range(width - 35, width):
            fade = (x - (width - 35)) / 34
            sample[x, y] = tuple(round(end[c] * (1 - fade) + BACKDROP[c] * fade) for c in range(3))
    if theme == "gold":
        return approved
    accent = (131, 179, 151) if theme == "ready" else (215, 130, 117)
    peak = (16, 27, 19) if theme == "ready" else (34, 19, 15)
    image = Image.new("RGB", (width, height), BACKDROP)
    source = approved.load()
    target = image.load()
    for y in range(height):
        for x in range(width):
            if x < 3:
                target[x, y] = accent
            else:
                # Preserve the approved plate's falloff; change its hue only.
                intensity = min(1.0, max(0.0, (source[x, y][0] - BACKDROP[0]) / 32.0))
                target[x, y] = tuple(round(BACKDROP[c] + peak[c] * intensity) for c in range(3))
    return image


def result_level_plate() -> Image.Image:
    """Match the HTML level-change band; its gold glow belongs to +next text."""
    width, height = 326, 78
    base = (18, 22, 24)  # #121618, the result column background
    stops = ((43, 44, 39, 0x22), (49, 49, 41, 0x66), (42, 45, 40, 0x22))
    pixels = Image.new("RGB", (width, height))
    target = pixels.load()
    for x in range(width):
        position = x / (width - 1) * 2
        start = 0 if position <= 1 else 1
        fraction = position - start
        left, right = stops[start], stops[start + 1]
        overlay = [left[c] * (1 - fraction) + right[c] * fraction for c in range(4)]
        alpha = overlay[3] / 255
        color = tuple(round(base[c] * (1 - alpha) + overlay[c] * alpha) for c in range(3))
        for y in range(height):
            target[x, y] = color
    return pixels


def save(name: str, image: Image.Image) -> None:
    image.save(OUT / f"{name}.png", optimize=True)
    print(f"POLISH05_GLOW {name} {image.width}x{image.height}")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    # The production forge fits five equipment parts into 41px rows. Keep the
    # approved 229px left-to-right falloff at its native width so TextDisplay
    # does not shrink and center the 82px source plate inside each row.
    for state in ("selected", "unselected"):
        plate = Image.open(OUT / f"gear_{state}.png").convert("RGBA")
        save(f"gear_{state}_compact", plate.resize((229, 41), Image.Resampling.BILINEAR))
    save("result_level_halo", result_level_plate())
    save("cost_ready_halo", selection_light(326, 52, "ready"))
    save("cost_missing_halo", selection_light(326, 52, "missing"))
    for state in ("off", "on"):
        source = Image.open(OUT / f"catalyst_{state}.png").convert("RGB")
        gradient = selection_light(source.width, source.height, "gold")
        card = Image.blend(source, gradient, 0.72)
        image = Image.new("RGB", (336, 62), BACKDROP)
        image.paste(card, (5, 9))
        save(f"catalyst_{state}_halo", image)


if __name__ == "__main__":
    main()
