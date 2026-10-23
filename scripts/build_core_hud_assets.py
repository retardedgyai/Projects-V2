"""Skill art list, class ornaments and the framed menu skill icons (the HUD lives in build_combat_hud.py).

Pillow is used only for asset composition. Replace assets/core-ui/skills/*.png to adopt new
masters. Missing artwork fails explicitly instead of recycling an already-framed pack sprite.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ("warrior", "ranger", "mage", "assassin", "templar", "healer")
# Same stable order as CoreClass.entries and its eight skills + two ultimates.
SKILLS = (
    "dash", "slam", "whirl", "war_guard", "war_wound", "war_counter", "war_cry", "war_breach", "war_ult", "war_banner", "war_awaken",
    "pierce", "frost_fan", "arrow_rain", "hunt_retreat", "hunt_pierce", "hunt_trap", "hunt_mark", "hunt_volley", "hunt_ult", "hunt_storm",
    "firebolt", "frost_nova", "meteor", "mage_blink", "mage_mark", "mage_garden", "mage_burst", "mage_ward", "mage_ult", "mage_zero",
    "ass_stab", "ass_execute", "ass_fan", "ass_escape", "ass_poison", "ass_chase", "ass_needle", "ass_guard", "ass_ult", "ass_contract",
    "temp_mace", "temp_pull", "temp_ward", "temp_guard", "temp_rebuke", "temp_field", "temp_break", "temp_dash", "temp_ult", "temp_sanctuary",
    "heal_light", "heal_ring", "heal_pillar", "heal_step", "heal_lamp", "heal_shield", "heal_mark", "heal_wind", "heal_ult", "heal_judgment",
)










def class_for_skill(name):
    # Ten per class, but the warrior has an eleventh (a third ultimate, 2026-10-23).
    index = SKILLS.index(name)
    return CLASSES[0] if index < 11 else CLASSES[1 + (index - 11) // 10]


def class_ornament(name, source=None):
    source = source or ROOT / "assets/core-ui"
    with Image.open(source / "skill-frames" / f"{class_for_skill(name)}.png") as loaded:
        ornament = loaded.convert("RGBA")
    if ornament.size != (32, 32):
        raise ValueError(f"Class frame must be 32px: {name}")
    if ornament.crop((4, 4, 28, 28)).getchannel("A").getbbox() is not None:
        raise ValueError(f"Class frame overlaps skill artwork: {name}")
    return ornament


def fit_skill(artwork):
    """Same fit for menu and HUD: never shrink a selected icon twice."""
    artwork = artwork.convert("RGBA")
    bounds = artwork.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Skill master is fully transparent")
    artwork = artwork.crop(bounds)
    factor = 24 / max(artwork.size)
    fitted = artwork.resize((max(1, round(artwork.width * factor)), max(1, round(artwork.height * factor))), Image.Resampling.NEAREST)
    master = Image.new("RGBA", (28, 28))
    master.alpha_composite(fitted, ((28 - fitted.width) // 2, (28 - fitted.height) // 2))
    return master


def skill_frame(master, frame, ornament=None, key_badge=True):
    card = Image.new("RGBA", (32, 32), "#10151b")
    card.alpha_composite(master, (2, 2))
    pen = ImageDraw.Draw(card)
    if frame in range(1, 21):
        # Clockwise reveal from twelve o'clock, leaving a dark wedge for remaining cooldown.
        remaining = frame / 20
        pixels = card.load()
        for y in range(2, 30):
            for x in range(2, 30):
                clockwise = (math.atan2(x - 15.5, -(y - 15.5)) + math.tau) % math.tau
                if clockwise / math.tau >= 1 - remaining:
                    r, g, b, a = pixels[x, y]
                    pixels[x, y] = (int(r * .23), int(g * .23), int(b * .27), a)
        edge, light = "#5c6067", "#8b8b85"
    elif frame in (21, 22):
        pixels = card.load()
        for y in range(2, 30):
            for x in range(2, 30):
                r, g, b, a = pixels[x, y]
                gray = int((r + g + b) / 3)
                pixels[x, y] = (int(gray * .23), int(gray * .40), min(255, int(gray * .65) + 20), a)
        edge, light = "#3479ae", "#7bbddd"
    else:
        edge, light = "#c7a964", "#f0d999"
    if ornament is None:
        # Historical proposal previews only. Production requires authored class ornaments.
        pen.rectangle((0, 0, 31, 31), outline="#0b0e13")
        pen.rectangle((1, 1, 30, 30), outline=edge)
        pen.line((2, 1, 29, 1), fill=light)
        pen.point((0, 0), fill=light)
        pen.point((31, 31), fill=edge)
    else:
        # Opaque dark backing fixes the 33px glyph advance even with open corner artwork.
        for inset in range(4):
            pen.rectangle((inset, inset, 31-inset, 31-inset), outline="#10151b")
    if ornament is not None:
        # Keep class identity visible in every state; only the inner art is dimmed.
        card.alpha_composite(ornament)
    if key_badge:
        pen.rectangle((23, 24, 28, 28), fill="#11171e")
    if frame == 22:
        pen.rectangle((11, 14, 21, 23), fill="#d4c7a4", outline="#181c24")
        pen.rectangle((13, 9, 19, 15), outline="#e9ddbe", width=2)
        pen.rectangle((15, 17, 17, 20), fill="#36363c")
    return card
