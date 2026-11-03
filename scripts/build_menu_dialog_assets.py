"""Bake the player menu (冒険の手帳) that is drawn inside a vanilla dialog.

    python scripts/build_menu_dialog_assets.py --fonts DIR

DIR holds the full MPLUS2.ttf (variable) and DotGothic16(-Regular).ttf. The copies in web-ui-lab/ui/forge-v3-fonts
are subsets made for the forge preview and miss many kanji, so they are not the default.

Needs fontTools and Pillow. Writes server-minestom/src/main/resources/polish05/menu.zip (private-namespace fonts and
sprites, merged into the UI pack by CoreUiPackServer) and polish05/menu-metrics.json, which PlayerMenuCanvas reads.

How the screen is drawn (checked against the 26.3 client):
  - The dialog body is one plain message of LINES lines, each padded to exactly CANVAS_W so the client's centring
    puts every line at the same left edge. Anything can be placed at any (x, y): x by advancing with the space
    font, y by picking the line and a font whose glyphs sit 0..8 px lower (TTF `shift`, bitmap `ascent`).
  - A text line's baseline is 7 px below the line top; a bitmap glyph's top is (7 - ascent) below it.
  - The client hit-tests clicks against the rectangle each glyph is drawn in (ActiveTextCollector), so an invisible
    sprite (alpha 1, below the 0.1 cut) over a baked button makes exactly that rectangle clickable.
Everything that never changes (backdrop, panels, tab row, labels, button plates) is baked into one sprite per page
at 4 texture px per design px. Words, numbers, rings, the selected row and hit areas are placed per player.

The server cannot see a player's GUI scale, so each player tells it once (PlayerDisplaySettings). Every page exists
in three SIZES, one per GUI scale 2 / 3 / 4: the same design (in design px, laid out for GUI scale 4) multiplied by
a unit of 2, 4/3 and 1 GUI px per design px, so it covers the same screen pixels at each scale. Textures are shared;
only glyph heights, ascents, font sizes and the title band (fixed 33 GUI px tall, as wide as the screen) differ.
"""
from __future__ import annotations

import argparse
import io
import json
import re
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "server-minestom/src/main/resources/polish05"
NS = "projects_ui_polish05"
K = 4                      # texture pixels per design pixel (= one screen pixel at every size)
CANVAS_W, CANVAS_H = 464, 189  # body canvas in design px
SCREEN_W = 480             # design px across a 1080p screen
TITLE_X = 8                # where the title starts on screen; its advance puts the vanilla warning mark at the right
TITLE_TOP = 12             # the title line's top on screen (GUI px; the header is 33 GUI px at every scale)
SIZES = {"4": 1.0, "3": 4 / 3, "2": 2.0}  # GUI scale -> GUI px per design px

TEXT_SOURCES = ["server-minestom/src/main/kotlin/dev/projects/server/coreloop/PlayerMenuDialog.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreSkillCatalog.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreWarriorSkillPresentation.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreJourney.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/questmap/QuestGathering.kt"]

# style -> (font file key, size)
# Dungeons look (5版): headings and numbers in DotGothic16 (d*, dot*), small text in M PLUS 2 (m*, b*).
STYLES = {"m6": ("m", 6), "m7": ("m", 7), "b7": ("b", 7), "b9": ("b", 9),
          "d6": ("d", 6), "d7": ("d", 7), "d9": ("d", 9), "dot6": ("d", 6), "dot9": ("d", 9), "dot16": ("d", 16)}

C = dict(ink="#f1ece4", muted="#9b8f96", faint="#5c5258", gold="#f2c84b", heat="#ff9a4a", line=(255, 240, 230, 46),
         slot=(10, 7, 10, 190), slot_line=(255, 240, 230, 48))
PATHS = [  # id, name, icon, colour
    ("combat", "戦い", "class_warrior", "#ff9a4a"),
    ("gather", "採取", "ore", "#6fd0c0"),
    ("smith", "鍛冶", "ingot", "#ffd166"),
    ("magic", "魔術", "book", "#a98cf0")]
CLASSES = ["warrior", "ranger", "mage", "assassin", "templar", "healer"]

# ---------------------------------------------------------------- layout (design px; exported for PlayerMenuDialog)
# 2026-11-03 藍 chose the Minecraft Dungeons look (5版): no boxed panels, everything floats on a big-pixel gradient;
# headings and numbers in DotGothic16, small text in M PLUS 2; slots are dark squares with a thin light line.
TABS = ["自分", "技", "パッシブ", "持ち物"]
TAB_ICONS = ["class_warrior", "skill_slam", "book", "ore"]
TAB_Y, TAB_GAP, TAB_W = 2, 30, 30                  # icon top; tab boxes TAB_W wide
SLOT = 26
TOP = 32                                           # content starts under the tab row
# 技
EMBLEM64 = (51, TOP + 2)                           # x, y of the 64 px class emblem
HEX = [(15, TOP + 18), (121, TOP + 18)]            # left hex frames (26 x 28): Lv, 点数
KEYS = [(10 + i * 29, TOP + 90) for i in range(5)] # key slots 2..6
GRID = [(172 + c * 29, TOP + 4 + r * 29) for r in range(3) for c in range(3)]   # normal skills
ULTS = [(172 + c * 29, TOP + 101) for c in range(3)]
DIV = [270]
RX = 284                                           # right column
BIG = (392, TOP - 2)                               # 64 px skill art
BARS = [(RX, TOP + 72), (RX + 56, TOP + 72), (RX + 112, TOP + 72)]   # 48 wide tracks
BAR_W = 48
DIAS = [(RX + 3 + i * 10, TOP + 93) for i in range(10)]
ACTS = [(RX, TOP + 112), (RX, TOP + 128)]          # gold (raise), white (equip); 160 x 14 hit areas
RESET = (RX, TOP + 144)
# 自分
EMBLEM48 = (60, TOP + 4)
HEX_SELF = [(56, TOP + 92), (86, TOP + 92)]
DIV_SELF = [178, 334]
PATH_D = [(196 + i * 34, TOP + 14) for i in range(4)]   # 26 x 26 diamond boxes
PATH_BAR = (192, TOP + 76, 130)
STEPS = (348, TOP + 24, 15)                        # x, first baseline, line step
PRIMARY = (348, TOP + 128)                         # the gold action line
EDIT = (40, TOP + 132)
BAKED = "".join(TABS) + "冒険の手帳いまの組枠を押してから技を選ぶ奥義威力再使用範囲左クリック右クリック名刺を整える道次の一歩点数銀貨"

def hexc(v, a=255):
    if isinstance(v, tuple):
        return v
    return tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def text_chars() -> set[int]:
    chars = set(range(0x20, 0x7F)) | set(range(0x3041, 0x3097)) | set(range(0x30A0, 0x3100)) | {ord(c) for c in "・·×／〜…％、。「」『』（）：！？―─→›‹◆"}
    chars |= {ord(c) for c in "".join(TABS) + "".join(p[1] for p in PATHS) + BAKED}
    for rel in TEXT_SOURCES:
        path = ROOT / rel
        if path.exists():
            chars |= {ord(c) for c in path.read_text(encoding="utf-8") if ord(c) >= 0x80}
    return chars


def build_fonts(src: Path, chars: set[int]) -> dict[str, bytes]:
    from fontTools import subset
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer

    def cut(font, keep):
        options = subset.Options(); options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]; options.name_languages = [0x409]
        options.layout_features = []; options.hinting = False
        sub = subset.Subsetter(options=options); sub.populate(unicodes=keep & font.getBestCmap().keys()); sub.subset(font)
        out = io.BytesIO(); font.save(out); return out.getvalue()

    files = {}
    for key, weight in (("m", 500), ("b", 700)):
        files[key] = cut(instancer.instantiateVariableFont(TTFont(src / "MPLUS2.ttf"), {"wght": weight}), chars)
    dot = src / "DotGothic16-Regular.ttf"
    files["d"] = cut(TTFont(dot if dot.exists() else src / "DotGothic16.ttf"), chars)
    return files


def advances(ttf: bytes, size: float, oversample: float, chars: set[int]) -> dict[str, float]:
    """Advance per character in GUI px exactly as the 26.3 client gets it: FreeType at round(size * oversample) px,
    FT_Load_Glyph(NO_BITMAP | BITMAP_METRICS_ONLY), hinted advance.x / 64 / oversample (TrueTypeGlyphProvider).
    Pillow's getlength differed enough to wrap the lines in game (2026-11-02)."""
    import freetype
    face = freetype.Face(io.BytesIO(ttf))
    px = int(round(size * oversample))
    face.set_pixel_sizes(px, px)
    flags = freetype.FT_LOAD_NO_BITMAP | 0x400000
    out = {}
    for cp in sorted(chars):
        index = face.get_char_index(cp)
        if index:
            face.load_glyph(index, flags)
            out[str(cp)] = face.glyph.advance.x / 64 / oversample
    return out


class Sprites:
    """Bitmap glyphs for one size: one private-use char per (sprite, ascent). Textures are shared by every size."""

    textures: dict[str, Image.Image] = {}

    def __init__(self, unit: float, first: int):
        self.unit = unit
        self.next = first
        self.meta: dict[str, dict] = {}
        self.providers: list[dict] = []

    def add(self, name: str, image: Image.Image, h: float, tops: list[float], origin: int = 0, unit: float | None = None):
        """`h` and `tops` in design px (or GUI px when `unit` is 1); the glyph is round(h * unit) GUI px tall."""
        u = self.unit if unit is None else unit
        Sprites.textures[name] = image
        gui_h = max(1, round(h * u))
        alpha = image.getchannel("A")
        bbox = alpha.point(lambda a: 255 if a > 0 else 0).getbbox()
        actual = bbox[2] if bbox else 0
        scale = gui_h / image.height
        entry = {"advance": int(0.5 + actual * scale) + 1, "chars": {}}
        for ascent in sorted({ascent_for(round(t * u), origin, gui_h) for t in tops}):
            ch = self.next; self.next += 1
            self.providers.append({"type": "bitmap", "file": f"{NS}:menu/{name}.png", "ascent": ascent,
                                   "height": gui_h, "chars": [chr(ch)]})
            entry["chars"][str(ascent)] = ch
        self.meta[name] = entry

    def add_tiled(self, name: str, image: Image.Image, h: int, tops: list[int], origin: int = 0, unit: float | None = None):
        """A sprite bigger than the client's 256 px font texture page, cut into TILE x TILE pieces (same units as h)."""
        w = image.width // K
        tiles = []
        for ty in range(0, h, TILE):
            for tx in range(0, w, TILE):
                tw, th = min(TILE, w - tx), min(TILE, h - ty)
                piece = image.crop((tx * K, ty * K, (tx + tw) * K, (ty + th) * K))
                if piece.getchannel("A").getbbox() is None:
                    continue
                tile = f"{name}_{tx}_{ty}"
                self.add(tile, piece, th, [t + ty for t in tops], origin, unit)
                tiles.append({"sprite": tile, "dx": tx, "dy": ty})
        self.meta[name] = {"tiles": tiles}


TILE = 60  # 240 texture px: fits the client's 256 x 256 font texture page


def ascent_for(top: int, origin: int, h: int) -> int:
    """The ascent that puts a sprite's top at `top` (measured like the canvas, from `origin`): on the line holding it,
    or hung from the line above when the client would reject ascent > height. DialogCanvas.sprite tries the same."""
    y = top - origin
    line = max(0, y // 9)
    ascent = 7 - (y - 9 * line)
    return ascent if ascent <= h else ascent - 9


def font_for(fonts, key, size):
    return ImageFont.truetype(io.BytesIO(fonts[key]), size * K)


def draw_text(d, fonts, style, x, base, text, fill, anchor="ls"):
    key, size = STYLES[style]
    d.text((x * K, base * K), text, font=font_for(fonts, key, size), fill=hexc(fill), anchor=anchor)


def text_w(fonts, style, text):
    key, size = STYLES[style]
    return font_for(fonts, key, size).getlength(text) / K


def glow(w, h, cx, cy, rx, ry, colour, peak):
    img = Image.new("RGBA", (w * K, h * K))
    px = img.load()
    r, g, b = colour
    for y in range(h * K):
        for x in range(w * K):
            dx, dy = (x / K - cx) / rx, (y / K - cy) / ry
            t = max(0.0, 1 - (dx * dx + dy * dy) ** 0.5)
            a = int(peak * t * t)
            if a:
                px[x, y] = (r, g, b, a)
    return img


def rounded(d, box, fill=None, outline=None, r=3, width=1):
    x, y, w, h = box
    d.rounded_rectangle((x * K, y * K, (x + w) * K - 1, (y + h) * K - 1), radius=r * K, fill=fill, outline=outline, width=width * K)



def bigpixel(w, h, block=10, seed=7):
    """The Dungeons ground: a warm crimson glow from the upper middle fading to violet-black, drawn in big blocks."""
    import math, random
    rng = random.Random(seed)
    img = Image.new("RGBA", (w * K, h * K))
    d = ImageDraw.Draw(img)
    for by in range(0, h, block):
        for bx in range(0, w, block):
            cx, cy = (bx + block / 2) / w, (by + block / 2) / h
            dist = math.hypot((cx - .5) * 1.3, (cy - .15) * 1.1)
            t = max(0.0, 1 - dist * 1.35)
            base, mid, hot = (14, 10, 16), (36, 14, 30), (78, 22, 38)
            c = [base[i] + (mid[i] - base[i]) * min(1, t * 1.6) + (hot[i] - mid[i]) * max(0, t - .45) * 1.4 for i in range(3)]
            k = 1 + rng.uniform(-.09, .09)
            d.rectangle((bx * K, by * K, (bx + block) * K - 1, (by + block) * K - 1), fill=tuple(max(0, min(255, int(v * k))) for v in c) + (255,))
    return img.filter(ImageFilter.GaussianBlur(K * 1.2))


def slot_frame(d, x, y, size=SLOT):
    d.rectangle((x * K, y * K, (x + size) * K - 1, (y + size) * K - 1), fill=C["slot"], outline=C["slot_line"], width=K // 2)


def hex_frame(img, x, y):
    w, h = 26, 28
    hx = Image.new("RGBA", (w * K, h * K)); hd = ImageDraw.Draw(hx)
    pts = lambda inset: [((w / 2) * K, inset * K), ((w - inset) * K, (h * .25) * K), ((w - inset) * K, (h * .75) * K),
                         ((w / 2) * K, (h - inset) * K), (inset * K, (h * .75) * K), (inset * K, (h * .25) * K)]
    hd.polygon(pts(0), fill=(255, 240, 230, 128)); hd.polygon(pts(1), fill=(10, 7, 10, 215))
    img.alpha_composite(hx, (x * K, y * K))


def diamond(size, fill, outline=None, glow_colour=None):
    img = Image.new("RGBA", (size * K, size * K)); d = ImageDraw.Draw(img)
    c = size * K / 2; r = c - K
    if glow_colour:
        g = glow(size, size, size / 2, size / 2, size / 2, size / 2, glow_colour, 120); img.alpha_composite(g)
    d.polygon([(c, c - r), (c + r, c), (c, c + r), (c - r, c)], fill=fill, outline=outline, width=K // 2 if outline else 0)
    return img


def tabs_row(img, d, fonts, icons, selected):
    total = len(TABS) * TAB_W + (len(TABS) - 1) * (TAB_GAP - TAB_W)
    x0 = (CANVAS_W - total) / 2
    boxes = []
    for i, (t, ic) in enumerate(zip(TABS, TAB_ICONS)):
        x = x0 + i * TAB_GAP
        on = t == selected
        art = icons[ic].resize((12 * K, 12 * K), Image.NEAREST)
        if not on:
            a = art.getchannel("A").point(lambda v: v * 6 // 10); art.putalpha(a)
        img.alpha_composite(art, (int((x + (TAB_W - 12) / 2) * K), TAB_Y * K))
        draw_text(d, fonts, "m6", x + TAB_W / 2, TAB_Y + 20, t, C["ink"] if on else C["muted"], anchor="ms")
        if on:
            d.rectangle(((x + TAB_W / 2 - 1.5) * K, (TAB_Y + 23) * K, (x + TAB_W / 2 + 1.5) * K - 1, (TAB_Y + 26) * K - 1), fill=hexc(C["ink"]))
        boxes.append((round(x), TAB_Y - 1, TAB_W, 26))
    return boxes


def bake_skills(fonts, icons):
    img = bigpixel(CANVAS_W, CANVAS_H); d = ImageDraw.Draw(img)
    tabs_row(img, d, fonts, icons, "技")
    img.alpha_composite(glow(110, 110, 55, 55, 55, 55, (255, 170, 110), 90), ((EMBLEM64[0] - 23) * K, (EMBLEM64[1] - 23) * K))
    for (x, y), label in zip(HEX, ["Lv", "点数"]):
        hex_frame(img, x, y); draw_text(d, fonts, "m6", x + 13, y + 11, label, C["muted"], anchor="ms")
    draw_text(d, fonts, "dot9", 83, TOP + 85, "いまの組", C["ink"], anchor="ms")
    for x, y in KEYS: slot_frame(d, x, y)
    draw_text(d, fonts, "m6", 83, TOP + 128, "枠を押してから、技を選ぶ", C["muted"], anchor="ms")
    for x, y in GRID + ULTS: slot_frame(d, x, y)
    draw_text(d, fonts, "m6", 172, TOP + 98, "奥義", C["muted"])
    for x in DIV: d.rectangle((x * K, (TOP + 2) * K, x * K + K // 2, 184 * K), fill=C["line"])
    for (x, y), label in zip(BARS, ["威力", "再使用", "範囲"]):
        draw_text(d, fonts, "m6", x, y - 4, label, C["muted"])
        d.rectangle((x * K, y * K, (x + BAR_W) * K - 1, (y + 2) * K - 1), fill=(255, 240, 230, 46))
    draw_text(d, fonts, "b7", RX, TOP + 90, "技の Lv", C["ink"])
    for x, y in DIAS: img.alpha_composite(diamond(10, (10, 7, 10, 150), (255, 240, 230, 80)), (x * K, y * K))
    for (x, y), (label, colour) in zip(ACTS, [("左クリック", C["gold"]), ("右クリック", C["ink"])]):
        w = text_w(fonts, "dot6", label) + 6
        d.rectangle((x * K, y * K, (x + w) * K - 1, (y + 10) * K - 1), fill=hexc(colour))
        draw_text(d, fonts, "dot6", x + 3, y + 8, label, "#1a1306")
    return img


def bake_self(fonts, icons):
    img = bigpixel(CANVAS_W, CANVAS_H); d = ImageDraw.Draw(img)
    tabs_row(img, d, fonts, icons, "自分")
    img.alpha_composite(glow(120, 120, 60, 60, 60, 60, (255, 170, 110), 90), ((EMBLEM48[0] - 36) * K, (EMBLEM48[1] - 36) * K))
    for x, y in HEX_SELF: hex_frame(img, x, y)
    for x in DIV_SELF: d.rectangle((x * K, (TOP + 2) * K, x * K + K // 2, 184 * K), fill=C["line"])
    draw_text(d, fonts, "dot9", 192, TOP + 9, "道", C["ink"])
    for (x, y), (pid, name, icon, colour) in zip(PATH_D, PATHS):
        img.alpha_composite(diamond(26, (10, 7, 10, 200), (255, 240, 230, 90)), (x * K, y * K))
        img.alpha_composite(icons[icon].resize((12 * K, 12 * K), Image.NEAREST), ((x + 7) * K, (y + 7) * K))
        draw_text(d, fonts, "m6", x + 13, y + 34, name, C["ink"], anchor="ms")
    px, py, pw = PATH_BAR
    d.rectangle((px * K, py * K, (px + pw) * K - 1, (py + 2) * K - 1), fill=(255, 240, 230, 46))
    draw_text(d, fonts, "dot9", STEPS[0], TOP + 9, "次の一歩", C["ink"])
    x, y = PRIMARY
    w = text_w(fonts, "dot6", "左クリック") + 6
    d.rectangle((x * K, y * K, (x + w) * K - 1, (y + 10) * K - 1), fill=hexc(C["gold"]))
    draw_text(d, fonts, "dot6", x + 3, y + 8, "左クリック", "#1a1306")
    x, y = EDIT
    w = text_w(fonts, "dot6", "右クリック") + 6
    d.rectangle((x * K, y * K, (x + w) * K - 1, (y + 10) * K - 1), fill=hexc(C["ink"]))
    draw_text(d, fonts, "dot6", x + 3, y + 8, "右クリック", "#1a1306")
    draw_text(d, fonts, "m6", x + w + 4, y + 8, "名刺を整える", C["muted"])
    return img


def bake_band(fonts, book, w: int) -> Image.Image:
    """The title band in GUI px (4 texture px each): the same big-pixel ground, the book, the purse."""
    h = 33
    img = bigpixel(w, h, seed=3)
    d = ImageDraw.Draw(img)
    img.alpha_composite(book.resize((16 * K, 17 * K), Image.NEAREST), (12 * K, 9 * K))
    draw_text(d, fonts, "m7", 32, 20, "冒険の手帳", C["muted"])
    draw_text(d, fonts, "dot9", w - 108, 21, "◆", C["gold"])
    return img


def main() -> None:
    parser = argparse.ArgumentParser(); parser.add_argument("--fonts", required=True)
    src = Path(parser.parse_args().fonts)
    chars = text_chars()
    fonts = build_fonts(src, chars)
    files: dict[str, bytes] = {}

    def png(path, image):
        buf = io.BytesIO(); image.save(buf, "PNG", optimize=True); files[path] = buf.getvalue()

    pack = zipfile.ZipFile(OUT / "pack.zip")
    raw = lambda z, p: Image.open(io.BytesIO(z.read(p))).convert("RGBA")
    icons = {"ore": raw(pack, f"assets/{NS}/textures/forge_materials/ore.png"),
             "ingot": raw(pack, f"assets/{NS}/textures/forge_materials/ingot.png"),
             "book": raw(zipfile.ZipFile(OUT / "grimoire.zip"), f"assets/{NS}/textures/grimoire/ic_book.png"),
             "skill_slam": Image.open(ROOT / "assets/core-ui/skills/slam.png").convert("RGBA"),
             "class_warrior": Image.open(ROOT / "assets/core-ui/proposals/2026-10-16-class-emblems/warrior-32.png").convert("RGBA")}
    book = raw(pack, f"assets/{NS}/textures/raw/book_ui.png")
    for key, data in fonts.items():
        files[f"assets/{NS}/font/menu_{key}.ttf"] = data
    metrics = {"space": {}, "sizes": {}}
    advances_map = {}
    cp = 0xF000
    for step in [2 ** i for i in range(11)] + [0.5, 0.25]:
        for sign in (1, -1):
            advances_map[chr(cp)] = step * sign; metrics["space"][str(step * sign)] = cp; cp += 1
    files[f"assets/{NS}/font/menu_space.json"] = json.dumps({"providers": [{"type": "space", "advances": advances_map}]}).encode()

    page_self, page_skills = bake_self(fonts, icons), bake_skills(fonts, icons)
    tab_boxes = tabs_row(Image.new("RGBA", (CANVAS_W * K, CANVAS_H * K)), ImageDraw.Draw(Image.new("RGBA", (4, 4))), fonts, icons, "")
    skill_source = (ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreSkillCatalog.kt").read_text(encoding="utf-8")
    skill_icons = sorted(set(re.findall(r's\("[^"]*", "([a-z_]+)"', skill_source)))
    skill_art = {icon: Image.open(ROOT / f"assets/core-ui/skills/{icon}.png").convert("RGBA") for icon in skill_icons}
    slot_tops = sorted({y for _, y in KEYS + GRID + ULTS})
    sel = Image.new("RGBA", (SLOT * K, SLOT * K)); ImageDraw.Draw(sel).rectangle((0, 0, SLOT * K - 1, SLOT * K - 1), outline=hexc(C["ink"]), width=K // 2 + 1)
    dim = Image.new("RGBA", (SLOT * K, SLOT * K), (10, 7, 10, 160))
    cross = Image.new("RGBA", (SLOT * K, SLOT * K)); cd = ImageDraw.Draw(cross)
    cd.line((2 * K, 2 * K, (SLOT - 2) * K, (SLOT - 2) * K), fill=(255, 240, 230, 40), width=K // 2); cd.line(((SLOT - 2) * K, 2 * K, 2 * K, (SLOT - 2) * K), fill=(255, 240, 230, 40), width=K // 2)
    tri = Image.new("RGBA", (9 * K, 9 * K)); ImageDraw.Draw(tri).polygon([(0, 0), (9 * K, 0), (0, 9 * K)], fill=hexc(C["gold"]))
    glyphs = 0
    for n, (tag, unit) in enumerate(SIZES.items()):
        sprites = Sprites(unit, 0xE000 + n * 0x500)
        screen = round(SCREEN_W * unit)
        size = {"unit": unit, "canvasWidth": round(CANVAS_W * unit), "lines": -(-round(CANVAS_H * unit) // 9),
                "titleWidth": screen - 2 * TITLE_X - 30, "titleX": TITLE_X, "titleTop": TITLE_TOP,
                "art": f"{NS}:menu{tag}_art", "styles": {}, "sprites": {}, "layout": {}}
        oversample = K / unit
        for style, (key, pt) in STYLES.items():
            for r in range(9):
                files[f"assets/{NS}/font/menu{tag}_{style}_{r}.json"] = json.dumps({"providers": [
                    {"type": "ttf", "file": f"{NS}:menu_{key}.ttf", "size": pt * unit, "oversample": oversample, "shift": [0.0, float(r)]},
                ]}).encode()
            size["styles"][style] = {"font": f"{NS}:menu{tag}_{style}_", "advances": advances(fonts[key], pt * unit, oversample, chars)}
        sprites.add_tiled("page_self", page_self, CANVAS_H, [0])
        sprites.add_tiled("page_skills", page_skills, CANVAS_H, [0])
        sprites.add_tiled(f"band{tag}", bake_band(fonts, book, screen), 33, [0], TITLE_TOP, unit=1.0)
        for c in CLASSES:
            art = Image.open(ROOT / f"assets/core-ui/proposals/2026-10-16-class-emblems/{c}-32.png").convert("RGBA")
            sprites.add(f"emblem64_{c}", art.resize((64 * K, 64 * K), Image.NEAREST).resize((256, 256), Image.NEAREST), 64, [EMBLEM64[1]])
            sprites.add(f"emblem48_{c}", art.resize((48 * K, 48 * K), Image.NEAREST), 48, [EMBLEM48[1]])
        for icon, art in skill_art.items():
            sprites.add(f"sk20_{icon}", art.resize((20 * K, 20 * K), Image.NEAREST), 20, [t + 3 for t in slot_tops])
            sprites.add(f"skb_{icon}", art.resize((64 * K, 64 * K), Image.NEAREST).resize((256, 256), Image.NEAREST), 64, [BIG[1]])
        sprites.add("slot_sel", sel, SLOT, slot_tops)
        sprites.add("slot_dim", dim, SLOT, slot_tops)
        sprites.add("slot_x", cross, SLOT, slot_tops)
        sprites.add("key_tri", tri, 9, slot_tops)
        sprites.add("dia_on", diamond(10, (233, 221, 208, 255), None, (255, 230, 200)), 10, [DIAS[0][1]])
        sprites.add("dia_gear", diamond(10, hexc(C["heat"]), None, (255, 150, 80)), 10, [DIAS[0][1]])
        sprites.add("dia_sel", diamond(26, None, hexc(C["ink"]), (255, 230, 200)), 26, [PATH_D[0][1]])
        sprites.add("bar_fill", Image.new("RGBA", (2 * K, 2 * K), hexc(C["ink"])), 2, [BARS[0][1]])
        for pid, name, icon, colour in PATHS:
            sprites.add(f"bar_{pid}", Image.new("RGBA", (2 * K, 2 * K), hexc(colour)), 2, [PATH_BAR[1]])
        bullet = lambda colour: diamond(6, colour)
        sprites.add("ms_now", bullet(hexc(C["ink"])), 6, [STEPS[1] - 6 + i * STEPS[2] for i in range(7)])
        # invisible hit areas (alpha 1: under the client's 0.1 cut, but the glyph rectangle still takes clicks)
        hits = {"slot": (SLOT, SLOT, slot_tops), "dia": (26, 26, [PATH_D[0][1]]), "act": (160, 14, [y - 2 for _, y in ACTS] + [PRIMARY[1] - 2]),
                "edit": (100, 12, [EDIT[1] - 1]), "reset": (110, 10, [RESET[1]])}
        for i, (x, y, w, h) in enumerate(tab_boxes):
            hits[f"tab{i}"] = (w, h, [y])
        for name, (w, h, tops) in hits.items():
            sprites.add(f"hit_{name}", Image.new("RGBA", (w, h), (0, 0, 0, 1)), h, tops)
        sprites.meta["band"] = sprites.meta.pop(f"band{tag}")
        files[f"assets/{NS}/font/menu{tag}_art.json"] = json.dumps({"providers": sprites.providers}).encode()
        size["sprites"] = sprites.meta
        size["layout"] = {"tabs": [list(b) for b in tab_boxes], "emblem64": list(EMBLEM64), "hex": [list(h) for h in HEX],
                          "keys": [list(k) for k in KEYS], "grid": [list(g) for g in GRID], "ults": [list(u) for u in ULTS],
                          "rx": RX, "big": list(BIG), "bars": [list(b) for b in BARS], "barW": BAR_W, "dias": [list(x) for x in DIAS],
                          "acts": [list(a) for a in ACTS], "reset": list(RESET), "emblem48": list(EMBLEM48), "hexSelf": [list(h) for h in HEX_SELF],
                          "pathD": [list(p) for p in PATH_D], "pathBar": list(PATH_BAR), "steps": list(STEPS), "primary": list(PRIMARY),
                          "edit": list(EDIT), "wallet": [screen - 20, 21]}
        metrics["sizes"][tag] = size
        glyphs += len(sprites.providers)
    for name, image in Sprites.textures.items():
        assert image.width <= 256 and image.height <= 256, name
        png(f"assets/{NS}/textures/menu/{name}.png", image)
    with zipfile.ZipFile(OUT / "menu.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    (OUT / "menu-metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"menu.zip {sum(len(v) for v in files.values()) // 1024} KB, {len(chars)} chars, {glyphs} sprite glyphs")


if __name__ == "__main__":
    main()
