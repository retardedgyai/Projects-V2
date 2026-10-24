"""Combat HUD (direction approved 2026-10-16: modern MMO x Minecraft) and the world-space HUD bits.

Art lives in combat_hud_art.py; this file turns each piece into glyphs. Everything is drawn on the
Minecraft GUI pixel grid at one texel per GUI pixel. Gauge values are small 3x5 digits; other numbers use the vanilla ascii sheet itself (minecraft:font/ascii.png, mapped to private
codepoints), so they are the game's own font; notices and the boss name are plain vanilla text.

Outputs (all generated, git-ignored):
  font/hud*.json                      bitmap providers, one per vertical placement
  textures/gui/core/hud_*.png         glyph sheets
  menu/hud-glyphs.tsv                 name, font, code, advance  (read by CoreHudGlyphs)
  menu/hud-text.tsv                   codepoint, glyph, advance  (MaruMinya, world name tags only)
  assets/minecraft/...                vanilla HUD sprites: blanked ones and the reskinned hotbar
                                      (CoreUiPackPolicy allowlist)

Placement conventions
  Action bar (CoreHudLayout): a glyph whose top edge is `top` GUI px above the screen bottom has
  ascent = top - 65 (text baseline sits at height - 72; bitmap top = y + 7 - ascent).
  Boss bar name line is drawn at y = 3, so a glyph top at y=t needs ascent = 10 - t.
  Name tags and text displays draw at y = 0: top = 7 - ascent.
"""
from pathlib import Path
import json
import math

from PIL import Image, ImageDraw, ImageFont

from build_core_hud_assets import SKILLS
import combat_hud_art as art
import mob_hud_art as mob_art
import boss_bar_art as boss_art

ROOT = Path(__file__).resolve().parents[1]
HUD = "assets/minecraft/textures/gui/sprites/hud/"
BOSS = "assets/minecraft/textures/gui/sprites/boss_bar/"
FONT_PATH = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"
# GNU Unifont (the client's own CJK font), pinned; OFL 1.1 / GPL2+ with the font embedding exception.
UNIFONT_URL = "https://unifoundry.com/pub/unifont/unifont-17.0.04/font-builds/unifont_jp-17.0.04.hex.gz"
UNIFONT_SHA256 = "a6195f525d998d883ed18aeba48bbbc281648ed41fd57b3c87af62e25b4158a1"
UNIFONT_PATH = ROOT / ".tools/unifont/unifont_jp-17.0.04.hex.gz"

# ---------------------------------------------------------------- palette (name tags, damage)
OUT = (7, 8, 11)

BIG = {
    "0": [".###.", "##.##", "##.##", "##.##", "##.##", "##.##", ".###."],
    "1": [".##..", "###..", ".##..", ".##..", ".##..", ".##..", "####."],
    "2": [".###.", "##.##", "...##", "..##.", ".##..", "##...", "#####"],
    "3": ["####.", "...##", "...##", ".###.", "...##", "...##", "####."],
    "4": ["...##", "..###", ".####", "##.##", "#####", "...##", "...##"],
    "5": ["#####", "##...", "####.", "...##", "...##", "##.##", ".###."],
    "6": [".###.", "##...", "####.", "##.##", "##.##", "##.##", ".###."],
    "7": ["#####", "...##", "..##.", "..##.", ".##..", ".##..", ".##.."],
    "8": [".###.", "##.##", "##.##", ".###.", "##.##", "##.##", ".###."],
    "9": [".###.", "##.##", "##.##", "##.##", ".####", "...##", ".###."],
}
BIG_CHARS = "0123456789"


def vanilla_overrides():
    """Every vanilla HUD sprite this pack blanks. Mirrors CoreUiPackPolicy.vanillaOverrides (blank part)."""
    result = {}
    for kind in ("", "absorbing_", "frozen_", "poisoned_", "withered_"):
        for hardcore in ("", "hardcore_"):
            for fill in ("full", "half"):
                for blink in ("", "_blinking"):
                    result[f"{HUD}heart/{kind}{hardcore}{fill}{blink}.png"] = (9, 9)
    for variant in ("container", "container_blinking", "container_hardcore", "container_hardcore_blinking"):
        result[f"{HUD}heart/{variant}.png"] = (9, 9)
    for fill in ("empty", "half", "full"):
        for hunger in ("", "_hunger"):
            result[f"{HUD}food_{fill}{hunger}.png"] = (9, 9)
        # Armour points live in the server's own defence stat; the vanilla row is noise.
        result[f"{HUD}armor_{fill}.png"] = (9, 9)
    # Level and progress move into the level diamond.
    result[f"{HUD}experience_bar_background.png"] = (182, 5)
    result[f"{HUD}experience_bar_progress.png"] = (182, 5)
    # The pink bar is reserved for the pixel boss bar drawn by its name component.
    result[f"{BOSS}pink_background.png"] = (182, 5)
    result[f"{BOSS}pink_progress.png"] = (182, 5)
    return result


def vanilla_reskins():
    """Vanilla sprites drawn anew (not blanked): the hotbar and its selection frame, same sizes."""
    return {f"{HUD}hotbar.png": art.hotbar_sprite(), f"{HUD}hotbar_selection.png": art.hotbar_selection_sprite()}


# ---------------------------------------------------------------- glyph registry
class FontBuilder:
    """Collect glyph groups into bitmap providers. Each group shares one sheet and one ascent."""

    def __init__(self, key, start=0xE000):
        self.key = key
        self.providers = []
        self.code = start
        self.rows = []  # (name, font, code, advance)

    def group(self, sheet_name, named_images, ascent, columns=16, display_height=None):
        """named_images: list of (name, image). Ink sits at the top-left of a uniform cell.
        display_height: draw the cell this many GUI px tall (finer art, e.g. 32 px skill icons in 16)."""
        width = max(image.width for _, image in named_images)
        ink_height = max(image.height for _, image in named_images)
        height = ink_height if display_height else max(ink_height, ascent)
        scale = (display_height or height) / height
        assert width <= 256 and height <= 256, sheet_name
        columns = min(columns, len(named_images))
        rows = math.ceil(len(named_images) / columns)
        sheet = Image.new("RGBA", (width * columns, height * rows))
        chars = []
        for index, (name, image) in enumerate(named_images):
            bounds = image.getchannel("A").getbbox()
            assert bounds is not None, f"Empty glyph {name}"
            sheet.alpha_composite(image, ((index % columns) * width, (index // columns) * height))
            code = self.code
            self.code += 1
            chars.append(chr(code))
            # Client: advance = (int)(0.5 + ink width * scale) + 1.
            self.rows.append((name, self.key, code, int(0.5 + bounds[2] * scale) + 1))
        # Pad the final row with unique private glyphs (never NUL).
        while len(chars) < rows * columns:
            chars.append(chr(self.code))
            self.code += 1
        grid = ["".join(chars[r * columns:(r + 1) * columns]) for r in range(rows)]
        assert self.code < SPACE_BASE
        self.providers.append((sheet_name, sheet, {"type": "bitmap", "file": f"projects:gui/core/{sheet_name}.png",
                                                   "height": display_height or height, "ascent": ascent, "chars": grid}))


def hud_ascent(top):
    return top - 65


# ---------------------------------------------------------------- drawing helpers
def rgba(color, alpha=255):
    return (*color, alpha)


def new(width, height):
    return Image.new("RGBA", (width, height), (0, 0, 0, 0))


def put(image, x, y, color, alpha=255):
    if 0 <= x < image.width and 0 <= y < image.height:
        image.putpixel((x, y), rgba(color, alpha))


def outlined(mask_rows, ink=(255, 255, 255), shade=None, outline=OUT):
    """Glyph from '#'/'1' rows with a 1 px dark outline (8-neighbour)."""
    h, w = len(mask_rows), len(mask_rows[0])
    image = new(w + 2, h + 2)
    on = {(x, y) for y, row in enumerate(mask_rows) for x, c in enumerate(row) if c in "#1"}
    for x, y in on:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                put(image, x + 1 + dx, y + 1 + dy, outline)
    # Close pin-holes the 8-neighbour outline leaves beside diagonal strokes ("/", "v"), so the
    # gauge colour under a number never shows through as a stray dot.
    for y in range(image.height):
        for x in range(image.width):
            if image.getpixel((x, y))[3]:
                continue
            filled = lambda dx, dy: 0 <= x + dx < image.width and 0 <= y + dy < image.height and image.getpixel((x + dx, y + dy))[3] > 0
            if (filled(-1, 0) and filled(1, 0)) or (filled(0, -1) and filled(0, 1)) or (x in (0, image.width - 1) and filled(0, 1) and filled(1 if x == 0 else -1, 0)):
                put(image, x, y, outline)
    for x, y in on:
        color = shade(y, h) if shade else ink
        put(image, x + 1, y + 1, color)
    return image


def digit_shade(y, h):
    # Lit from above: white top, warm ivory lower rows (tints multiply this).
    return (255, 255, 255) if y < h // 2 else (226, 222, 214)


def solid(width, rows):
    image = new(width, len(rows))
    for y, color in enumerate(rows):
        for x in range(width):
            put(image, x, y, color)
    return image


def cells(width, rows):
    """Gauge fill with a 4 px energy-cell rhythm. Binary segments start on multiples of 4, so the
    rhythm stays continuous however the fill is split."""
    image = solid(width, rows)
    for x in range(3, width, 4):
        for y in range(1, len(rows)):
            r, g, b, a = image.getpixel((x, y))
            image.putpixel((x, y), (r * 84 // 100, g * 84 // 100, b * 84 // 100, a))
    return image


def segments(prefix, rows, widths, textured=False):
    return [(f"{prefix}_{w}", cells(w, rows) if textured and len(rows) > 2 else solid(w, rows)) for w in widths]


BINARY = (1, 2, 4, 8, 16, 32, 64, 128)
SPACE_BASE = 0xF800  # +2^n at F800+n, -2^n at F810+n, in every projects:hud* font (CoreHudLine)


# ---------------------------------------------------------------- text
def text_font():
    assert FONT_PATH.is_file(), "Run build_core_menu_assets first (it fetches the pinned MaruMinya font)"
    return ImageFont.truetype(str(FONT_PATH), 12)


def text_glyphs(chars):
    """Native 12 px MaruMinya, hard pixels, 1 px dark outline. 14x14 cells."""
    font = text_font()
    result = []
    for char in chars:
        if char in (" ", "　"):
            continue
        mask = Image.new("L", (14, 16))
        draw = ImageDraw.Draw(mask)
        draw.fontmode = "1"
        draw.text((1, 13), char, font=font, anchor="ls", fill=255)
        on = [(x, y) for y in range(16) for x in range(14) if mask.getpixel((x, y)) >= 128]
        if not on:
            continue
        image = new(14, 14)
        assert all(1 <= y <= 12 and 1 <= x <= 12 for x, y in on), f"Glyph leaves the 12 px grid: {char}"
        for x, y in on:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    put(image, x + dx, y + dy, OUT)
        for x, y in on:
            put(image, x, y, (255, 255, 255))
        result.append((char, image))
    return result


# ---------------------------------------------------------------- unifont text (boss bar rows)
def unifont_glyphs(chars):
    """Non-ASCII characters drawn exactly as the client draws them: Unifont's 16 px bitmaps shown 8 GUI px
    tall. ASCII stays on minecraft:font/ascii.png. Returns [(char, 16x16 white image)] for drawable chars."""
    import gzip, hashlib, urllib.request
    if not UNIFONT_PATH.is_file():
        UNIFONT_PATH.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(UNIFONT_URL, timeout=60) as response:
            data = response.read()
        assert hashlib.sha256(data).hexdigest() == UNIFONT_SHA256
        UNIFONT_PATH.write_bytes(data)
    assert hashlib.sha256(UNIFONT_PATH.read_bytes()).hexdigest() == UNIFONT_SHA256
    wanted = {ord(c) for c in chars if ord(c) >= 0x80}
    bitmaps = {}
    with gzip.open(UNIFONT_PATH, "rt", encoding="ascii") as hexfile:
        for line in hexfile:
            code, bits = line.strip().split(":")
            if int(code, 16) in wanted:
                bitmaps[int(code, 16)] = bits
    result = []
    for code in sorted(bitmaps):
        bits = bitmaps[code]
        width = len(bits) // 4  # 32 hex digits = 8 px wide, 64 = 16 px wide
        image = new(16, 16)
        for y in range(16):
            row = int(bits[y * width // 4:(y + 1) * width // 4], 16)
            for x in range(width):
                if row >> (width - 1 - x) & 1:
                    put(image, x, y, (255, 255, 255))
        if image.getchannel("A").getbbox():
            result.append((chr(code), image))
    return result


BOSS_TEXT_FONTS = {  # font -> top of its 8 px text row (boss bar: ascent = 10 - top)
    "hud_boss_name": boss_art.NAME_TOP,
    "hud_boss_tag": boss_art.TAG_TOP,
    "hud_boss_hp": boss_art.HP_TEXT_TOP,
    "hud_boss_lv": boss_art.LV_TOP,
    "hud_ded_erosion": boss_art.EROSION_LABEL_TOP,
}


# ---------------------------------------------------------------- vanilla-ascii number fonts
ASCII_BASE = 0xE600   # ascii.png cell n (= codepoint n) is drawn by U+E600+n in every hud_num* font
ASCII_SPACE = 4       # vanilla's own space advance (the ascii cell for ' ' is empty)


ASCII_UNUSED = 0xE7FF  # stands in for the empty ' ' cell; the space provider owns U+E620


def number_grid():
    cells = [chr(ASCII_BASE + n) if n != 32 else chr(ASCII_UNUSED) for n in range(256)]
    return ["".join(cells[row * 16:(row + 1) * 16]) for row in range(16)]


def number_provider(ascent):
    grid = number_grid()
    return {"type": "bitmap", "file": "minecraft:font/ascii.png", "height": 8, "ascent": ascent, "chars": grid}


NUMBER_FONTS = {  # font -> ascent; numbers are white glyphs tinted by the component colour
    "hud_num_gauge": hud_ascent(art.GAUGE_TOP - 1),      # HP / MP values and the level, on the gauge row
    "hud_num_cd": hud_ascent(15),                         # cooldown seconds on a hotbar skill
}


# ---------------------------------------------------------------- build
def build_combat_hud(assets, pack, source, write_json, chars):
    output = assets / "textures/gui/core"
    output.mkdir(parents=True, exist_ok=True)
    # Everything named hud* is this builder's (none of it is committed): drop the previous run's
    # files so a renamed or removed glyph sheet never lingers in the pack.
    for stale in list((assets / "font").glob("hud*.json")) + list(output.glob("hud_*.png")):
        stale.unlink()
    fonts = []

    # -- projects:hud (action bar) ---------------------------------------
    hud = FontBuilder("projects:hud")
    hud.group("hud_console", [("console", art.console())], hud_ascent(art.CONSOLE_TOP))
    rows = art.GAUGE_ROWS
    gauge = [(f"hp_fill_{w}", art.gauge_fill(w, rows, art.HP)) for w in BINARY[:6]]
    gauge += [(f"mp_fill_{w}", art.gauge_fill(w, rows, art.MP)) for w in BINARY[:6]]
    gauge += [(f"hp_trail_{w}", art.flat(w, rows, art.TRAIL)) for w in BINARY[:6]]
    gauge += [(f"hp_barrier_{w}", art.flat(w, rows, art.BARRIER[0], art.BARRIER[1])) for w in BINARY[:6]]
    gauge += [("hp_tick", art.hp_tick())]
    hud.group("hud_gauge", gauge, hud_ascent(art.GAUGE_TOP))
    hud.group("hud_cells", [("rage_cell", art.rage_cell()), ("xp_cell", art.xp_cell())], hud_ascent(art.CELL_TOP))
    sockets = [(f"beast_{k}", art.beast_socket(k)) for k in range(art.BEAST_FILL_STEPS + 1)]
    sockets += [(f"beast_glint_{g}", art.beast_socket(art.BEAST_FILL_STEPS, glint=g - art.BEAST_R)) for g in range(2 * art.BEAST_R + 1)]
    sockets.append(("beast_bead", art.beast_bead()))
    sockets += [("beast_cap_l", art.beast_cap()), ("beast_cap_r", art.beast_cap(right=True))]
    hud.group("hud_beast", sockets, hud_ascent(art.BEAST_TOP))
    hud.group("hud_beast_spark", [(f"beast_spark_{a}", art.beast_spark(a)) for a in range(10)],
              hud_ascent(art.BEAST_TOP + art.BEAST_SPARK_RISE))
    hud.group("hud_gauge_digits", [(f"gd_{c}", art.gauge_digit(c)) for c in art.DIGITS], hud_ascent(art.GAUGE_TOP - 2))
    hud.group("hud_buff_digits", [(f"bd_{c}", art.gauge_digit(c)) for c in art.DIGITS], hud_ascent(art.BUFF_TOP - 4))
    emblem_rows, emblem_x = [], None
    for job in art.CLASSES:
        image, emblem_x, emblem_top = art.emblem(job)
        emblem_rows.append((f"emblem_{job}", image))
        emblem_rows += [(f"xp_{job}_{step}", art.xp_overlay(job, step)) for step in range(1, art.XP_STEPS + 1)]
    hud.group("hud_emblem", emblem_rows, hud_ascent(emblem_top), columns=25)
    tiles = [("buff_left", art.buff_left()), ("buff_right", art.buff_right())]
    tiles += [(f"buff_mid_{w}", art.buff_mid(w)) for w in BINARY[:5]]
    hud.group("hud_buff_tile", tiles, hud_ascent(art.BUFF_TOP))
    icons = [(f"buff_{kind}_{frame}", art.buff_icon(kind, frame)) for kind in art.BUFF_ART for frame in range(art.BUFF_STEPS + 1)]
    hud.group("hud_buff_icon", icons, hud_ascent(art.BUFF_TOP - 2))
    fonts.append(hud)

    # -- projects:hud_skills: skill art reduced to 16 px over its hotbar slot -----------------------
    skills = FontBuilder("projects:hud_skills")
    for index, name in enumerate(SKILLS):
        with Image.open(source / f"skills/{name}.png") as loaded:
            sprite = loaded.convert("RGBA")
        assert sprite.size == (32, 32), f"Skill art must be authored at 32 px: {name}"
        # Named by CoreSkillCatalog art index; HUD and source order are the same list.
        frames = [(f"skill_{index}_{state}", art.skill_icon(sprite, state)) for state in art.SKILL_STATES]
        skills.group(f"hud_skill_{name}", frames, hud_ascent(art.SLOT_ICON_TOP), columns=4)
    fonts.append(skills)

    # -- text: MaruMinya for world name tags (unchanged) ------------------
    glyphs = text_glyphs(chars)
    text_rows = []
    world = FontBuilder("projects:hud_text")
    world.group("hud_text", [(f"text_{ord(c):X}", image) for c, image in glyphs], 10, columns=32)
    for c, _ in glyphs:
        row = next(r for r in world.rows if r[0] == f"text_{ord(c):X}")
        text_rows.append((ord(c), row[2], row[3]))
    fonts.append(world)

    # -- boss bar (boss_bar_art): graded v3 and the shade wyrm's own frame -----------------------
    boss = FontBuilder("projects:hud_boss")
    boss.group("hud_boss_emblem", [(f"boss_emblem_{g}", boss_art.emblem(g)) for g in boss_art.GRADES], 10)
    boss.group("hud_boss_plate", [(f"boss_plate_{g}", boss_art.plate(g)) for g in boss_art.GRADES],
               10 - (boss_art.PLATE_TOP - boss_art.PLATE_PAD), columns=1)
    rows = [(f"boss_fill_{w}", art.gauge_fill(w, boss_art.HP_ROWS, art.BOSS)) for w in BINARY]
    rows += [(f"boss_fillc_{w}", art.gauge_fill(w, boss_art.HP_ROWS, boss_art.CALAMITY_HP)) for w in BINARY]
    rows += [(f"boss_trail_{w}", art.flat(w, boss_art.HP_ROWS, boss_art.TRAIL)) for w in BINARY]
    rows += [("boss_tick", art.flat(1, boss_art.HP_ROWS, (0, 0, 0, 120)))]
    boss.group("hud_boss_fill", rows, 10 - boss_art.HP_TOP)
    boss.group("hud_boss_cell", [("boss_cell", boss_art.cell(True)), ("boss_cellw", boss_art.cell(False))], 10 - boss_art.CELL_TOP)
    boss.group("hud_boss_icon", [(f"boss_icon_{n}", boss_art.icon(n)) for n in boss_art.ICONS], 10 - boss_art.ICON_TOP)
    # dedicated bosses: the normal bar, decorated — ornaments behind it, horns over the diamond, own HP colours
    boss.group("hud_boss_plate_bare", [("boss_plate_calamity_bare", boss_art.plate("calamity", corners=False))],
               10 - (boss_art.PLATE_TOP - boss_art.PLATE_PAD))
    backs = []
    for key, (left, right) in (("wyrm", boss_art.wyrm_back()), ("sakura", boss_art.sakura_back())):
        backs += [(f"{key}_back_l", left), (f"{key}_back_r", right)]
    boss.group("hud_ded_back", backs, 10, columns=1)
    boss.group("hud_ded_front", [("wyrm_front", boss_art.wyrm_front()), ("sakura_front", boss_art.sakura_front())], 10)
    rows = []
    for key, ramp, trail in (("wyrm", boss_art.WYRM_HP, boss_art.WYRM_TRAIL), ("sakura", boss_art.SAKURA_HP, boss_art.SAKURA_TRAIL)):
        rows += [(f"{key}_fill_{w}", art.gauge_fill(w, boss_art.HP_ROWS, ramp)) for w in BINARY]
        rows += [(f"{key}_trail_{w}", art.flat(w, boss_art.HP_ROWS, trail)) for w in BINARY]
    boss.group("hud_ded_fill", rows, 10 - boss_art.HP_TOP)
    boss.group("hud_erosion", [("erosion_well", boss_art.erosion_well())], 10 - (boss_art.EROSION_TOP - 1))
    boss.group("hud_erosion_cell", [("erosion_cell", boss_art.erosion_cell())], 10 - boss_art.EROSION_TOP)
    fonts.append(boss)

    # Boss bar text rows: ascii.png for ASCII, Unifont bitmaps for the rest, one font per row.
    uni = unifont_glyphs(chars)
    uni_rows = []
    for key, top in BOSS_TEXT_FONTS.items():
        builder = FontBuilder(f"projects:{key}")
        builder.group(key, [(f"uni_{ord(c):X}", image) for c, image in uni], 10 - top, columns=32, display_height=8)
        builder.extra = [number_provider(10 - top)]
        if not uni_rows:
            uni_rows = [(int(name[4:], 16), code, advance) for name, _, code, advance in builder.rows]
        fonts.append(builder)
    assert max(code for _, code, _ in uni_rows) < ASCII_BASE, "Unifont rows reached the ascii codepoints"

    # -- mob plate (world text display, one 9 px line per row; mob_hud_art) ----------------
    big = [(c, outlined(BIG[c], shade=digit_shade)) for c in BIG_CHARS]
    plate = FontBuilder("projects:hud_plate")
    plate.group("hud_plate_frame", [(f"plate_frame_{g}", mob_art.plate_frame(g)) for g in mob_art.GRADES], 7)
    plate.group("hud_plate_icon", [(f"plate_icon_{n}", mob_art.icon(n)) for n in mob_art.ICONS], 7)
    plate.group("hud_plate_hp", [(f"plate_hp_{w}", art.gauge_fill(w, mob_art.HP_ROWS, mob_art.HP)) for w in BINARY[:6]],
                7 - mob_art.HP_TOP)
    plate.group("hud_plate_break", [(f"plate_brk_{w}", art.gauge_fill(w, mob_art.BREAK_ROWS, mob_art.BREAK)) for w in BINARY[:6]] +
                [(f"plate_brkw_{w}", art.gauge_fill(w, mob_art.BREAK_ROWS, mob_art.BROKEN)) for w in BINARY[:6]],
                7 - mob_art.BREAK_TOP)
    fonts.append(plate)

    # -- damage numbers (world text displays) ----------------------------
    damage = FontBuilder("projects:hud_damage")
    star = outlined(["...#...", "...#...", ".#####.", "#######", ".#####.", "...#...", "...#..."],
                    shade=lambda y, h: (255, 255, 255))
    weak = outlined(["#####", ".###.", "..#.."], shade=lambda y, h: (255, 255, 255))
    damage.group("hud_damage", [(f"dmg_{c}", i) for c, i in big] + [("dmg_crit", star), ("dmg_weak", weak)], 8)
    fonts.append(damage)

    # -- write ------------------------------------------------------------
    space = {chr(SPACE_BASE + bit): 1 << bit for bit in range(12)} | {chr(SPACE_BASE + 16 + bit): -(1 << bit) for bit in range(12)}
    rows = []
    for builder in fonts:
        # Signed binary spaces in every HUD font let the server keep one text run per colour.
        providers = [{"type": "space", "advances": space}]
        extra = getattr(builder, "extra", [])
        if extra:
            providers[0] = {"type": "space", "advances": space | {chr(ASCII_BASE + 32): ASCII_SPACE}}
        for sheet_name, sheet, provider in builder.providers:
            sheet.save(output / f"{sheet_name}.png", optimize=True)
            providers.append(provider)
        providers += extra
        write_json(assets / f"font/{builder.key.split(':')[1]}.json", {"providers": providers})
        if builder.key == "projects:hud_text" or builder.key.split(":")[1] in BOSS_TEXT_FONTS:
            continue
        rows += builder.rows
    for name, ascent in NUMBER_FONTS.items():
        write_json(assets / f"font/{name}.json", {"providers": [
            {"type": "space", "advances": space | {chr(ASCII_BASE + 32): ASCII_SPACE}}, number_provider(ascent)]})
    (assets / "menu").mkdir(parents=True, exist_ok=True)
    (assets / "menu/hud-glyphs.tsv").write_text(
        "# name\tfont\tcode\tadvance; generated by build_combat_hud.py\n" +
        "".join(f"{name}\t{font}\t{code:X}\t{advance}\n" for name, font, code, advance in rows), encoding="utf-8")
    (assets / "menu/hud-uni.tsv").write_text(
        "# codepoint\tglyph\tadvance; generated by build_combat_hud.py (Unifont rows: " + ", ".join(BOSS_TEXT_FONTS) + ")\n" +
        "".join(f"{cp:X}\t{code:X}\t{advance}\n" for cp, code, advance in uni_rows), encoding="utf-8")
    (assets / "menu/hud-text.tsv").write_text(
        "# codepoint\tglyph\tadvance; generated by build_combat_hud.py (font projects:hud_text)\n" +
        "".join(f"{cp:X}\t{code:X}\t{advance}\n" for cp, code, advance in text_rows), encoding="utf-8")
    for path, size in sorted(vanilla_overrides().items()):
        target = pack / path
        target.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGBA", size).save(target)
    for path, image in sorted(vanilla_reskins().items()):
        target = pack / path
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target)
    write_json(source / "hud-layout.json", {
        "vanilla": "26.2", "gui_coordinates": "x relative to the integer screen centre; top = GUI px above the bottom",
        "direction": "2026-10-16 modern MMO x Minecraft (proposals/2026-10-16-combat-hud)",
        "actionbar_bitmap_top": "height - 65 - ascent",
        "console": {"left_x": art.CONSOLE_X, "top": art.CONSOLE_TOP, "width": art.CONSOLE_W, "height": art.CONSOLE_H},
        "hp_gauge": {"x": art.HP_X, "width": art.HP_W, "top": art.GAUGE_TOP, "rows": art.GAUGE_ROWS},
        "mp_gauge": {"x": art.MP_X, "width": art.MP_W, "top": art.GAUGE_TOP, "rows": art.GAUGE_ROWS},
        "resource_cells": {"x": art.CELL_X, "pitch": art.CELL_PITCH, "count": art.CELL_COUNT, "top": art.CELL_TOP},
        "emblem": {"left_x": emblem_x, "top": emblem_top, "centre_from_bottom": art.EMBLEM_CY, "xp_steps": art.XP_STEPS,
                   "classes": list(art.CLASSES)},
        "skill_icons": {"left_x": art.SLOT_ICON_X, "pitch": 20, "top": art.SLOT_ICON_TOP, "size": 16, "texels": 16,
                        "states": list(art.SKILL_STATES)},
        "buffs": {"left_x": art.BUFF_LEFT, "top": art.BUFF_TOP, "height": art.BUFF_H},
        "number_fonts": {k: v for k, v in NUMBER_FONTS.items()},
        "boss_bar": {"plate_x": boss_art.PLATE_X, "plate_width": boss_art.PLATE_W, "plate_top": boss_art.PLATE_TOP,
                     "hp_x": boss_art.HP_X, "hp_width": boss_art.HP_W, "hp_top": boss_art.HP_TOP,
                     "text_rows": BOSS_TEXT_FONTS},
        "blank_vanilla_sprites": sorted(vanilla_overrides()),
        "reskinned_vanilla_sprites": sorted(vanilla_reskins()),
    })
    return rows, text_rows
