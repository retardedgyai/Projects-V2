"""Build positioned HUD sprites and deterministic radial cooldown variants from authored masters.

Pillow is used only for asset composition. Replace assets/core-ui/skills/*.png to adopt new
masters. Missing artwork fails explicitly instead of recycling an already-framed pack sprite.
"""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ("warrior", "ranger", "mage", "starweaver", "assassin", "templar", "healer")
OVERRIDE_PREFIX = "assets/minecraft/textures/gui/sprites/hud/"
DIGITS = "0123456789/HMPR"
# Same stable order as CoreClass.entries and its eight skills + two ultimates.
SKILLS = (
    "dash", "slam", "whirl", "war_guard", "war_wound", "war_counter", "war_cry", "war_breach", "war_ult", "war_banner",
    "pierce", "frost_fan", "arrow_rain", "hunt_retreat", "hunt_pierce", "hunt_trap", "hunt_mark", "hunt_volley", "hunt_ult", "hunt_storm",
    "firebolt", "frost_nova", "meteor", "mage_blink", "mage_mark", "mage_garden", "mage_burst", "mage_ward", "mage_ult", "mage_zero",
    "star_thread", "star_ring", "starfall", "star_step", "star_needle", "star_cloud", "star_shield", "star_break", "star_ult", "star_constellation",
    "ass_stab", "ass_execute", "ass_fan", "ass_escape", "ass_poison", "ass_chase", "ass_needle", "ass_guard", "ass_ult", "ass_contract",
    "temp_mace", "temp_pull", "temp_ward", "temp_guard", "temp_rebuke", "temp_field", "temp_break", "temp_dash", "temp_ult", "temp_sanctuary",
    "heal_light", "heal_ring", "heal_pillar", "heal_step", "heal_lamp", "heal_shield", "heal_mark", "heal_wind", "heal_ult", "heal_judgment",
)
PATTERNS = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "/": ["001", "001", "010", "100", "100"], "H": ["101", "101", "111", "101", "101"],
    "M": ["101", "111", "111", "101", "101"], "P": ["111", "101", "111", "100", "100"],
    "R": ["111", "101", "111", "110", "101"],
}


def vanilla_overrides():
    result = set()
    for kind in ("", "absorbing_", "frozen_", "poisoned_", "withered_"):
        for hardcore in ("", "hardcore_"):
            for fill in ("full", "half"):
                for blink in ("", "_blinking"):
                    result.add(f"{OVERRIDE_PREFIX}heart/{kind}{hardcore}{fill}{blink}.png")
    for variant in ("container", "container_blinking", "container_hardcore", "container_hardcore_blinking"):
        result.add(f"{OVERRIDE_PREFIX}heart/{variant}.png")
    for fill in ("empty", "half", "full"):
        for hunger in ("", "_hunger"):
            result.add(f"{OVERRIDE_PREFIX}food_{fill}{hunger}.png")
    return result


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def provider(name, height, ascent, chars):
    return {"type": "bitmap", "file": f"projects:gui/core/{name}.png", "height": height, "ascent": ascent, "chars": chars}


def digit_sheet(scale, cell_height, ink_y):
    # All glyphs touch the third column at least once: advances are exactly 4 / 9 (outlined large).
    outline = 1 if scale == 2 else 0
    width = 3 * scale + outline * 2
    sheet = Image.new("RGBA", (width * len(DIGITS), cell_height))
    for index, digit in enumerate(DIGITS):
        cell = Image.new("RGBA", (width, cell_height))
        pen = ImageDraw.Draw(cell)
        for y, row in enumerate(PATTERNS[digit]):
            for x, pixel in enumerate(row):
                if pixel == "1":
                    left, top = outline + x * scale, ink_y + y * scale
                    pen.rectangle((left, top, left + scale - 1, top + scale - 1), fill="#fff5de")
        if outline:
            shadow = Image.new("RGBA", cell.size, "#080d15")
            shadow.putalpha(cell.getchannel("A").filter(ImageFilter.MaxFilter(3)))
            shadow.alpha_composite(cell)
            cell = shadow
        sheet.alpha_composite(cell, (index * width, 0))
    return sheet


def class_for_skill(name):
    return CLASSES[SKILLS.index(name) // 10]


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


def build_hud(assets, source, write_json):
    pack = assets.parents[1]
    output = assets / "textures/gui/core"
    providers = []
    for name, base, color, highlight in (("health_bar", 0xE300, "#9f3d48", "#e18c82"),
                                        ("mana_bar", 0xE320, "#265c8d", "#82bbdc"),
                                        ("resource_bar", 0xE340, "#9e7132", "#f6d58b")):
        sheet = Image.new("RGBA", (81, 9 * 21))
        pen = ImageDraw.Draw(sheet)
        for step in range(21):
            y = step * 9
            pen.rectangle((0, y, 80, y + 8), fill="#13161c")
            pen.rectangle((1, y + 1, 79, y + 7), outline="#ad925b")
            pen.rectangle((2, y + 2, 78, y + 6), fill="#22262d")
            filled = round(77 * step / 20)
            if filled:
                pen.rectangle((2, y + 2, 1 + filled, y + 6), fill=color)
                pen.line((2, y + 2, 1 + filled, y + 2), fill=highlight)
        save(sheet, output / f"{name}.png")
        providers.append(provider(name, 9, -11 if name == "resource_bar" else -26, [chr(base + n) for n in range(21)]))
    frames = {}
    shield = Image.new("RGBA", (9,9))
    pen = ImageDraw.Draw(shield)
    pen.polygon([(0,0),(4,1),(8,0),(7,6),(4,8),(1,6)], fill="#81c8ed", outline="#ddf5ff")
    pen.line((4,2,4,6),fill="#275577")
    save(shield,output / "shield_icon.png")
    providers.append(provider("shield_icon",9,-11,[chr(0xE380)]))
    sources = {}
    for index, name in enumerate(SKILLS):
        master_path = source / f"skills/{name}.png"
        sources[name] = str(master_path.relative_to(ROOT)).replace("\\", "/")
        with Image.open(master_path) as loaded:
            master = fit_skill(loaded)
        ornament = class_ornament(name, source)
        sheet = Image.new("RGBA", (128, 192))
        frames[name] = [skill_frame(master, frame, ornament) for frame in range(23)]
        for frame in range(24):
            sheet.alpha_composite(frames[name][min(frame, 22)], ((frame % 4) * 32, (frame // 4) * 32))
        save(sheet, output / f"skill_{name}_states.png")
        base = 0xE400 + index * 32 if index < 3 else 0xE600 + (index - 3) * 32
        chars = ["".join(chr(base + y * 4 + x) for x in range(4)) for y in range(6)]
        providers.append(provider(f"skill_{name}_states", 32, 29, chars))
    for name, base, scale, height, ink_y, ascent in (
        ("counter_digits", 0xE500, 2, 32, 11, 29),
        ("key_digits", 0xE520, 1, 5, 0, 3),
        ("bar_digits", 0xE540, 1, 5, 0, -28),
        ("resource_digits", 0xE560, 1, 5, 0, -13),
    ):
        save(digit_sheet(scale, height, ink_y), output / f"{name}.png")
        providers.append(provider(name, height, ascent, ["".join(chr(base + index) for index in range(len(DIGITS)))]))
    write_json(assets / "font/core_hud.json", {"providers": providers})
    for path in sorted(vanilla_overrides()):
        save(Image.new("RGBA", (9, 9)), pack / path)
    write_json(source / "hud-layout.json", {
        "vanilla": "26.2", "gui_coordinates": "relative to integer screen centre and GUI height",
        "actionbar_text_y_from_bottom": 72, "bitmap_top_formula": "height - 65 - ascent",
        "bars": {"left_x": [-91, 10], "width": 81, "height": 9, "top_from_bottom": 39},
        "skills": {"left_x": [-88, -52, -16, 20, 56], "size": 32, "top_from_bottom": 94,
                   "ready": 0, "cooldown_remaining_steps": [1, 20], "no_mana": 21},
        "master_sources": sources,
        "class_frames": {name: f"assets/core-ui/skill-frames/{class_for_skill(name)}.png" for name in SKILLS},
        "transparent_vanilla_sprites": sorted(vanilla_overrides()),
    })
    # Preview is a design aid, not a claim of an in-game render or GUI-scale verification.
    preview = Image.new("RGBA", (182, 88), "#343a43")
    for column, (name, frame) in enumerate((("dash", 0), ("slam", 12), ("whirl", 21), ("war_guard", 0), ("war_ult", 20))):
        preview.alpha_composite(frames[name][frame], (3 + column * 36, 0))
    def preview_digits(value, x, y, large=False):
        with Image.open(output / ("counter_digits.png" if large else "bar_digits.png")) as sheet:
            cell = 8 if large else 3
            height = 32 if large else 5
            for index, char in enumerate(value):
                at = DIGITS.index(char) * cell
                preview.alpha_composite(sheet.crop((at, 0, at + cell, height)), (x + index * (cell + 1), y))
    preview_digits("4", 39 + 12, 0, large=True)
    preview_digits("RP", 75 + 7, 0, large=True)
    for column, key in enumerate("23456"):
        preview_digits(key, 3 + column * 36 + 25, 26)
    with Image.open(output / "resource_bar.png") as sheet:
        preview.alpha_composite(sheet.crop((0, 108, 81, 117)), (51, 40))
    preview_digits("60/100", 81, 42)
    for name, x in (("health_bar", 0), ("mana_bar", 101)):
        with Image.open(output / f"{name}.png") as sheet:
            preview.alpha_composite(sheet.crop((0, 180, 81, 189)), (x, 55))
        preview_digits("HP" if x == 0 else "MP", x + 3, 57)
        preview_digits("100/100", x + 34, 57)
    save(preview.resize((728, 352), Image.Resampling.NEAREST), source / "hud-layout-preview.png")
    overview = Image.new("RGBA", (364, 270), "#211d22")
    for index, name in enumerate(SKILLS):
        overview.alpha_composite(frames[name][0], (4 + index % 10 * 36, 4 + index // 10 * 38))
    save(overview.resize((1092, 810), Image.Resampling.NEAREST), source / "skill-classes-preview.png")
