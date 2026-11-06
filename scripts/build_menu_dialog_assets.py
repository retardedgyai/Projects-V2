"""Bake the player menu (冒険の手帳) that is drawn inside a vanilla dialog.

    python scripts/build_menu_dialog_assets.py

Every word is MaruMinya, a rounded 12 px pixel gothic (the full TTF is cached in .tools/core-menu, see
assets/core-ui/fonts/README.md), only ever at sizes where one of its dots is a whole number of screen pixels:
6 design px (2 px a dot) and 9 (3 px a dot). 2026-11-03 藍 compared it with Minecraft Dungeons II, whose Japanese is
a pixel font too; smooth M PLUS 2 and DotGothic16 at in-between sizes read as pasted on.

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
only glyph heights, ascents, font sizes and the title width differ. Nothing is drawn behind the windows: the client
already blurs the world, and 藍 wanted it neither darkened nor topped with a band (2026-11-03, in game).
"""
from __future__ import annotations

import io
import json
import re
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

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
                "server-minestom/src/main/kotlin/dev/projects/server/questmap/QuestGathering.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/coreloop/DungeonGateDialog.kt"]

# style -> (font file key, size)
# Headings and numbers in DotGothic16 (d*, dot*), small text and names in M PLUS 2 (m*, b*).
# The names are roles kept from the earlier fonts; all of them are MaruMinya now, at 6 or 9 only.
STYLES = {"m6": ("p", 6), "m7": ("p", 6), "b7": ("p", 6), "b8": ("p", 9), "b9": ("p", 6),
          "d6": ("p", 6), "d7": ("p", 6), "d9": ("p", 9), "dot6": ("p", 6), "dot9": ("p", 6), "dot16": ("p", 9)}
MARU = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"
MARU_PX = 12               # the font's own pixel grid

C = dict(ink="#ece6dc", muted="#9a948c", faint="#6e6a64", gold="#f2c84b", heat="#ff9a4a", line=(255, 255, 255, 18), dark="#2a1a06")
PATHS = [  # id, name, colour (the banners are assets/core-ui/menu-banners/ban_<id>.png)
    ("combat", "戦闘", "#ff8a3d"),
    ("gather", "採取", "#79e0d2"),
    ("smith", "鍛冶", "#f2c84b"),
    ("magic", "魔術", "#b9a0ff")]
CLASSES = ["warrior", "ranger", "mage", "assassin", "templar", "healer"]

# ---------------------------------------------------------------- layout (design px; exported for PlayerMenuDialog)
# 2026-11-03 藍 chose 7版 C (港の窓): framed windows with iron rims and bronze corners over the darkened world, pixel
# buttons in gold / charcoal, sunken slots, and each path as a banner of that path being done (assets/core-ui/menu-banners).
# 2026-11-03 藍: a path is entered from its banner, and then the tabs are that path's growth (戦闘: 技, パッシブ; 採取 and 鍛冶
# get theirs when their growth exists). 自分 keeps only what is not a path.
SELF_TABS = ["自分", "持ち物"]
COMBAT_TABS = ["技", "パッシブ"]                     # after the 自分 back button and the path's name
TAB_Y, TAB_H, TAB_GAP = 1, 14, 3                   # pill tabs centred along the top
SLOT = 26
TOP = 32                                           # 技: content starts here, inside the windows
WIN_Y, WIN_H = 19, 169                             # every window runs from under the tabs to the canvas bottom
# 技
SKILL_WINS = [(2, 156), (162, 104), (272, 190)]    # x, width
EMBLEM64 = (51, TOP + 2)                           # x, y of the 64 px class emblem
HEX = [(15, TOP + 18), (121, TOP + 18)]            # left plates (26 x 28): Lv, ポイント
KEYS = [(10 + i * 29, TOP + 90) for i in range(5)] # key slots 2..6
GRID = [(172 + c * 29, TOP + 4 + r * 29) for r in range(3) for c in range(3)]   # normal skills
ULTS = [(172 + c * 29, TOP + 101) for c in range(3)]
RX = 284                                           # right column
BIG = (392, TOP - 2)                               # 64 px skill art
BARS = [(RX, TOP + 72), (RX + 56, TOP + 72), (RX + 112, TOP + 72)]   # 48 wide tracks
BAR_W = 48
DIAS = [(RX + 3 + i * 10, TOP + 93) for i in range(10)]
ACTS = [(RX, TOP + 109), (RX, TOP + 126)]          # gold (raise), charcoal (equip); 168 x 14 buttons
ACT_W, BTN_H = 168, 14
RESET = (RX, TOP + 144)
CLOSE_W = 60                                       # the dialog's own 閉じる button (20 GUI px tall at every size), covered by our plate
# 自分
SELF_WINS = [(0, 150), (156, 150), (312, 152)]     # 名刺, 道, 能力値 (藍 kept three windows of one width: two were lopsided)
CARD = (4, WIN_Y + 4, 142, 32)                     # the harbour banner on the name card
EMBLEM24 = (11, WIN_Y + 29)                        # the class emblem in its slot (slot 30 x 30 at -3)
NAME = (46, WIN_Y + 47)                            # face + name baseline; the title 10 below
KV = (8, WIN_Y + 70, 134, 11)                      # x, first baseline, width, step
KV_LABELS = ["職", "冒険 Lv", "踏破", "技ポイント", "パッシブポイント", "銀貨"]
EDIT = (8, WIN_Y + 147, 134)                       # charcoal button
ROWS = [(161, WIN_Y + 14 + i * 38) for i in range(4)]   # 140 x 35 path banners, each the way into its path
ROW_W, ROW_H = 140, 35
# 能力値: two groups of five, the labels baked; the values (CoreCombatSheet, as the gear tooltip shows them) drawn at the right.
STAT_GROUPS = [("攻め", ["物理攻撃 AD", "魔法攻撃 AP", "攻撃速度", "会心率 / 倍率", "炎 / 氷 / 雷"]),
               ("守り", ["最大HP", "最大マナ", "物理防御 AR", "魔法防御 MR", "追加軽減"])]
STATS = (320, [WIN_Y + 28, WIN_Y + 107], 136, 11)   # x, each group's first baseline (its heading 13 above), width, step
NOTICE = (8, WIN_Y + 139)                          # one short line above the edit button
BAKED = "".join(SELF_TABS + COMBAT_TABS) + "".join(h + "".join(l) for h, l in STAT_GROUPS) + "".join(KV_LABELS) + "冒険の手帳いまの組奥義威力再使用範囲左クリック右クリック名刺を整える道次の一歩ポイント銀貨閉じる"

# ---- ダンジョンの門 (DungeonGateDialog): Aion 2's dungeon screen (藍 2026-11-06 「アイオン2のインスタンスダンジョンと同じ構図で」),
# over the whole screen ("全画面にしたら変かな" → やってみよう, still a dialog). The dialog draws its header (the title line)
# first and unclipped, so the page is one screen-sized picture hung from the title line: art, frames and words, laid out in
# screen design px (480 x 270, GUI scale 4 on 1080p). The body underneath only carries the invisible hit areas, the
# footer our 閉じる plate. The boss fills the screen, darkened towards the left; the list down the left edge with a star
# rank over each name; beside it the open dungeon's stars, name and difficulty, then headed sections; 入る bottom right.
DG_KEYART = "assets/core-ui/dungeons/sakura_keyart.png"   # scripts/build_dungeon_keyart.py, 240 x 135 dots, 2 design px a dot
SCREEN_H = 270
HEADER = 33                                        # GUI px, the dialog's header (title row) and footer (button row) bands
BLEED = 2                                          # GUI px the screen picture runs past each edge
DG_LIST = (12, 44, 80, 26)                         # the one entry: x, top, w, h
DG_X = 112                                         # the info column
DG_W = 124
DG_HEADS = [(86, "入場条件"), (116, "道のり"), (146, "踏破の報酬")]
DG_TIER = (116, 106)                               # the tier (a title glyph per tier), then the baked rest of the line
DG_REWARDS = [("echo_shard", "討伐証", "2"), ("gold_nugget", "戦利品券", "12"), ("nether_star", "高揚のオーブ", "1"), ("experience_bottle", "経験", "")]
DG_REWARD_Y = 159
DG_ENTER = (374, 206, 92, 19)
TEAL = "#8fd3cf"
BAKED += "ダンジョン散り桜の社ふつう入場条件道のり踏破の報酬入るひとり無料部屋波ボス桜牙獣討伐証戦利品券高揚のオーブ経験"


def hexc(v, a=255):
    if isinstance(v, tuple):
        return v
    return tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def text_chars() -> set[int]:
    chars = set(range(0x20, 0x7F)) | set(range(0x3041, 0x3097)) | set(range(0x30A0, 0x3100)) | {ord(c) for c in "・·×／〜…％、。「」『』（）：！？―─→›‹◆"}
    chars |= {ord(c) for c in "".join(SELF_TABS + COMBAT_TABS) + "".join(p[1] for p in PATHS) + BAKED}
    for rel in TEXT_SOURCES:
        path = ROOT / rel
        if path.exists():
            chars |= {ord(c) for c in path.read_text(encoding="utf-8") if ord(c) >= 0x80}
    return chars


def build_fonts(chars: set[int]) -> dict[str, bytes]:
    from fontTools import subset
    from fontTools.ttLib import TTFont

    def cut(font, keep):
        options = subset.Options(); options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]; options.name_languages = [0x409]
        options.layout_features = []; options.hinting = False
        sub = subset.Subsetter(options=options); sub.populate(unicodes=keep & font.getBestCmap().keys()); sub.subset(font)
        out = io.BytesIO(); font.save(out); return out.getvalue()

    return {"p": cut(TTFont(MARU), chars)}


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

    def add(self, name: str, image: Image.Image, h: float, tops: list[float], origin: int = 0, unit: float | None = None,
            first_line: bool = False):
        """`h` and `tops` in design px (or GUI px when `unit` is 1); the glyph is round(h * unit) GUI px tall.
        [first_line]: hang it from the canvas's first line (backgrounds; see DialogCanvas.background)."""
        u = self.unit if unit is None else unit
        Sprites.textures[name] = image
        gui_h = max(1, round(h * u))
        alpha = image.getchannel("A")
        bbox = alpha.point(lambda a: 255 if a > 0 else 0).getbbox()
        actual = bbox[2] if bbox else 0
        scale = gui_h / image.height
        entry = {"advance": int(0.5 + actual * scale) + 1, "chars": {}}
        for ascent in sorted({(7 - (round(t * u) - origin)) if first_line else ascent_for(round(t * u), origin, gui_h) for t in tops}):
            ch = self.next; self.next += 1
            self.providers.append({"type": "bitmap", "file": f"{NS}:menu/{name}.png", "ascent": ascent,
                                   "height": gui_h, "chars": [chr(ch)]})
            entry["chars"][str(ascent)] = ch
        self.meta[name] = entry

    def add_tiled(self, name: str, image: Image.Image, h: int, tops: list[int], origin: int = 0, unit: float | None = None,
                  first_line: bool = False, k: int = K, tile_size: int = None, place: float = 1.0):
        """A sprite bigger than the client's 256 px font texture page, cut into tiles of `tile_size` (same units as h,
        `k` texture px each). `place` scales the stored tile offsets into the units of the canvas that draws it."""
        tile_size = tile_size or TILE
        w = image.width // k
        tiles = []
        for ty in range(0, h, tile_size):
            for tx in range(0, w, tile_size):
                tw, th = min(tile_size, w - tx), min(tile_size, h - ty)
                piece = image.crop((tx * k, ty * k, (tx + tw) * k, (ty + th) * k))
                if piece.getchannel("A").getbbox() is None:
                    continue
                tile = f"{name}_{tx}_{ty}"
                self.add(tile, piece, th, [t + ty for t in tops], origin, unit, first_line)
                tiles.append({"sprite": tile, "dx": tx * place, "dy": ty * place})
        self.meta[name] = {"tiles": tiles}


TILE = 60  # 240 texture px: fits the client's 256 x 256 font texture page


def ascent_for(top: int, origin: int, h: int) -> int:
    """The ascent that puts a sprite's top at `top` (measured like the canvas, from `origin`): on the line holding it,
    or hung from the line above when the client would reject ascent > height. DialogCanvas.sprite tries the same."""
    y = top - origin
    line = max(0, y // 9)
    ascent = 7 - (y - 9 * line)
    return ascent if ascent <= h else ascent - 9


def pixel_text(fonts, style, text, fill):
    """`text` drawn on the font's own 12 px grid, each dot then made a whole n x n block of texture px, with a one-dot
    shadow under light words (none under dark ones on gold). Returns the image and its ascent in texture px."""
    from PIL import ImageChops
    key, size = STYLES[style]
    n = size * K // MARU_PX
    font = ImageFont.truetype(io.BytesIO(fonts[key]), MARU_PX)
    w = max(1, int(font.getlength(text)) + 2)
    mask = Image.new("L", (w, MARU_PX + 4))
    ImageDraw.Draw(mask).text((0, MARU_PX), text, font=font, fill=255, anchor="ls")
    mask = mask.point(lambda a: 255 if a > 110 else 0).resize((w * n, (MARU_PX + 4) * n), Image.NEAREST)
    colour = hexc(fill)
    out = Image.new("RGBA", (mask.width + n, mask.height + n))
    if sum(colour[:3]) > 380:
        out.paste(Image.new("RGBA", mask.size, (0, 0, 0, 200)), (n, n), mask)
    out.paste(Image.new("RGBA", mask.size, colour), (0, 0), mask)
    return out, MARU_PX * n


def draw_text(d, fonts, style, x, base, text, fill, anchor="ls"):
    img, ascent = pixel_text(fonts, style, text, fill)
    width = text_w(fonts, style, text) * K
    left = x * K - (width / 2 if anchor == "ms" else width if anchor == "rs" else 0)
    d._image.alpha_composite(img, (round(left), round(base * K - ascent)))


def text_w(fonts, style, text):
    key, size = STYLES[style]
    return ImageFont.truetype(io.BytesIO(fonts[key]), MARU_PX).getlength(text) * size / MARU_PX


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


def diamond(size, fill, outline=None, glow_colour=None):
    img = Image.new("RGBA", (size * K, size * K)); d = ImageDraw.Draw(img)
    c = size * K / 2; r = c - K
    if glow_colour:
        g = glow(size, size, size / 2, size / 2, size / 2, size / 2, glow_colour, 120); img.alpha_composite(g)
    d.polygon([(c, c - r), (c + r, c), (c, c + r), (c - r, c)], fill=fill, outline=outline, width=K // 2 if outline else 0)
    return img


# ---------------------------------------------------------------- 7版 C: frames and buttons
# Drawn as small pixel tiles at 2 texture px per design px, then nine-sliced at K (each tile px becomes K / 2 px).
BLACK, IRON_HI, IRON_LO = (0, 0, 0, 255), (92, 84, 74, 255), (38, 34, 30, 255)
BRONZE, BRONZE_HI = (168, 112, 56, 255), (232, 170, 96, 255)


def window_tile(n=24, fill=(17, 17, 19, 240)):
    """Iron rim with a bronze L-bracket and a rivet on each corner."""
    im = Image.new("RGBA", (n, n), fill); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, n - 1, n - 1), outline=BLACK)
    d.line((1, 1, n - 2, 1), fill=IRON_HI); d.line((1, 1, 1, n - 2), fill=IRON_HI)
    d.line((1, n - 2, n - 2, n - 2), fill=IRON_LO); d.line((n - 2, 1, n - 2, n - 2), fill=IRON_LO)
    d.rectangle((2, 2, n - 3, n - 3), outline=(10, 10, 11, 255))
    for cx, cy, sx, sy in ((0, 0, 1, 1), (n - 1, 0, -1, 1), (0, n - 1, 1, -1), (n - 1, n - 1, -1, -1)):
        for i in range(6):
            d.point((cx + sx * i, cy + sy), fill=BRONZE); d.point((cx + sx, cy + sy * i), fill=BRONZE)
            d.point((cx + sx * i, cy), fill=BLACK); d.point((cx, cy + sy * i), fill=BLACK)
        d.point((cx + sx, cy + sy), fill=BRONZE_HI)
        d.point((cx + sx * 3, cy + sy * 3), fill=BRONZE)
    return im


def button_tile(top, body, bottom, side, n=16):
    """A raised plate with cut corners: light top, dark bottom."""
    im = Image.new("RGBA", (n, n), body); d = ImageDraw.Draw(im)
    d.rectangle((0, 1, n - 1, 2), fill=top); d.rectangle((0, n - 3, n - 1, n - 2), fill=bottom)
    d.line((1, 1, 1, n - 2), fill=side); d.line((n - 2, 1, n - 2, n - 2), fill=side)
    d.rectangle((0, 0, n - 1, n - 1), outline=BLACK)
    for c in ((0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)): d.point(c, fill=(0, 0, 0, 0))
    for c in ((1, 1), (n - 2, 1), (1, n - 2), (n - 2, n - 2)): d.point(c, fill=BLACK)
    return im


def slot_tile(on=False, n=12):
    """Sunken: dark top-left edge, light bottom-right; bronze outline when chosen."""
    im = Image.new("RGBA", (n, n), (20, 20, 22, 255)); d = ImageDraw.Draw(im)
    d.line((1, 1, n - 2, 1), fill=(8, 8, 9, 255)); d.line((1, 1, 1, n - 2), fill=(8, 8, 9, 255))
    d.line((1, n - 2, n - 2, n - 2), fill=(62, 60, 66, 255)); d.line((n - 2, 1, n - 2, n - 2), fill=(62, 60, 66, 255))
    d.rectangle((0, 0, n - 1, n - 1), outline=BRONZE_HI if on else BLACK)
    return im


def row_tile(on=False, n=12):
    """The frame over a path banner (no fill)."""
    im = Image.new("RGBA", (n, n)); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, n - 1, n - 1), outline=BLACK)
    d.rectangle((1, 1, n - 2, n - 2), outline=(255, 160, 80, 255) if on else (74, 68, 61, 255))
    if on: d.rectangle((2, 2, n - 3, n - 3), outline=(255, 138, 61, 90))
    return im


GOLD_BTN = button_tile((248, 228, 168, 255), (228, 192, 104, 255), (150, 112, 44, 255), (206, 168, 82, 255))
DARK_BTN = button_tile((74, 74, 82, 255), (43, 43, 48, 255), (22, 22, 25, 255), (56, 56, 62, 255))


def tint(img, x0, y0, x1, y1, rgba):
    """A translucent rectangle laid over what is there (ImageDraw would replace the pixels, alpha and all)."""
    box = (round(x0 * K), round(y0 * K), round(x1 * K), round(y1 * K))
    img.alpha_composite(Image.new("RGBA", (box[2] - box[0], box[3] - box[1]), rgba), box[:2])


def nine(tile, cut, w, h):
    """`tile` (2 texture px per design px) stretched to w x h design px at K; `cut` tile px kept at each edge."""
    s = K // 2
    src = tile.resize((tile.width * s, tile.height * s), Image.NEAREST)
    c, W, H = cut * s, w * K, h * K
    out = Image.new("RGBA", (W, H))
    xs = [(0, c, 0, c), (c, src.width - c, c, W - c), (src.width - c, src.width, W - c, W)]
    ys = [(0, c, 0, c), (c, src.height - c, c, H - c), (src.height - c, src.height, H - c, H)]
    for sx0, sx1, dx0, dx1 in xs:
        for sy0, sy1, dy0, dy1 in ys:
            if dx1 > dx0 and dy1 > dy0:
                out.alpha_composite(src.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


def window(img, d, fonts, x, w, title=None):
    img.alpha_composite(nine(window_tile(), 8, w, WIN_H), (x * K, WIN_Y * K))
    if title:
        draw_text(d, fonts, "b7", x + 7, WIN_Y + 11, title, "#f2e6d2")


def slot_frame(img, x, y, w=SLOT, h=SLOT, on=False):
    img.alpha_composite(nine(slot_tile(on), 4, w, h), (round(x * K), round(y * K)))


def button(img, d, fonts, x, y, w, plate, kbd=None):
    """A plate with its key hint at the right end; the words are placed per player."""
    img.alpha_composite(nine(plate, 5, w, BTN_H), (x * K, y * K))
    if kbd:
        ink = C["dark"] if plate is GOLD_BTN else C["muted"]
        kw = text_w(fonts, "dot6", kbd) + 4
        tint(img, x + w - kw - 4, y + 3, x + w - 4, y + 11, (0, 0, 0, 56))
        draw_text(d, fonts, "dot6", x + w - kw - 2, y + 9.5, kbd, ink)


def tabs_row(img, d, fonts, tabs, selected, lead=0):
    """Pill tabs centred along the top (after `lead` px of other things): the open one gold, the others charcoal. Returns their boxes."""
    widths = [text_w(fonts, "b7", t) + 18 for t in tabs]
    x = (CANVAS_W - lead - sum(widths) - TAB_GAP * (len(tabs) - 1)) / 2 + lead
    boxes = []
    for t, w in zip(tabs, widths):
        on = t == selected
        if img is not None:
            img.alpha_composite(nine(GOLD_BTN if on else DARK_BTN, 5, round(w), TAB_H), (round(x * K), TAB_Y * K))
            draw_text(d, fonts, "b7", x + w / 2, TAB_Y + 10, t, C["dark"] if on else C["muted"], anchor="ms")
        boxes.append((round(x), TAB_Y, round(w), TAB_H))
        x += w + TAB_GAP
    return boxes


def banner(name, dim=False):
    art = Image.open(ROOT / f"assets/core-ui/menu-banners/{name}.png").convert("RGBA")
    if dim:
        from PIL import ImageEnhance
        art = ImageEnhance.Brightness(ImageEnhance.Color(art).enhance(0.3)).enhance(0.6)
    return art


def combat_tabs(img, d, fonts):
    """戦闘's tab row: the 自分 button at the left edge, then the path's name and its tabs centred. Returns [自分, 技, パッシブ] boxes."""
    name = PATHS[0][1]
    lead = text_w(fonts, "b8", name) + 8
    tabs = tabs_row(img, d, fonts, COMBAT_TABS, "技", lead)
    back = (2, TAB_Y, round(text_w(fonts, "b7", "自分") + 18), TAB_H)
    if img is not None:
        img.alpha_composite(nine(DARK_BTN, 5, back[2], TAB_H), (back[0] * K, TAB_Y * K))
        draw_text(d, fonts, "b7", back[0] + back[2] / 2, TAB_Y + 10, "自分", C["muted"], anchor="ms")
        draw_text(d, fonts, "b8", tabs[0][0] - lead, TAB_Y + 11, name, PATHS[0][2])
    return [back] + tabs


def bake_skills(fonts):
    img = Image.new("RGBA", (CANVAS_W * K, CANVAS_H * K)); d = ImageDraw.Draw(img)
    combat_tabs(img, d, fonts)
    for x, w in SKILL_WINS: window(img, d, fonts, x, w)
    img.alpha_composite(glow(110, 110, 55, 55, 55, 55, (255, 170, 110), 70), ((EMBLEM64[0] - 23) * K, (EMBLEM64[1] - 23) * K))
    for (x, y), label in zip(HEX, ["Lv", "ポイント"]):
        slot_frame(img, x - 2, y, 30, 28); draw_text(d, fonts, "m6", x + 13, y + 11, label, C["muted"], anchor="ms")
    draw_text(d, fonts, "b7", 83, TOP + 85, "いまの組", C["ink"], anchor="ms")
    for x, y in KEYS: slot_frame(img, x, y)
    for x, y in GRID + ULTS: slot_frame(img, x, y)
    draw_text(d, fonts, "m6", 172, TOP + 98, "奥義", C["muted"])
    for (x, y), label in zip(BARS, ["威力", "再使用", "範囲"]):
        draw_text(d, fonts, "m6", x, y - 4, label, C["muted"])
        tint(img, x, y, x + BAR_W, y + 2, (255, 255, 255, 26))
    draw_text(d, fonts, "b7", RX, TOP + 90, "技の Lv", C["ink"])
    for x, y in DIAS: img.alpha_composite(diamond(10, (10, 7, 10, 150), (255, 240, 230, 80)), (x * K, y * K))
    button(img, d, fonts, ACTS[0][0], ACTS[0][1], ACT_W, GOLD_BTN, "左クリック")
    button(img, d, fonts, ACTS[1][0], ACTS[1][1], ACT_W, DARK_BTN, "右クリック")
    return img


def bake_self(fonts):
    img = Image.new("RGBA", (CANVAS_W * K, CANVAS_H * K)); d = ImageDraw.Draw(img)
    tabs_row(img, d, fonts, SELF_TABS, "自分")
    (lx, lw), (mx, mw), (rx, rw) = SELF_WINS
    window(img, d, fonts, lx, lw)
    x, y, w, h = CARD
    card = Image.open(ROOT / "assets/core-ui/menu-banners/card_harbor.png").convert("RGBA")
    card = card.resize((w * K, round(card.height * w * K / card.width)), Image.LANCZOS)
    img.alpha_composite(card.crop((0, 0, w * K, h * K)), (x * K, y * K))
    slot_frame(img, EMBLEM24[0] - 3, EMBLEM24[1] - 3, 30, 30, on=True)
    kx, ky, kw, step = KV
    for i, label in enumerate(KV_LABELS):
        draw_text(d, fonts, "m6", kx, ky + i * step, label, C["muted"])
        tint(img, kx, ky + i * step + 3, kx + kw, ky + i * step + 3.5, C["line"])
    button(img, d, fonts, EDIT[0], EDIT[1], EDIT[2], DARK_BTN, "右クリック")
    draw_text(d, fonts, "b7", EDIT[0] + 6, EDIT[1] + 9.5, "名刺を整える", C["ink"])
    window(img, d, fonts, mx, mw, "道")
    for (x, y), (pid, name, colour) in zip(ROWS, PATHS):
        img.alpha_composite(banner(f"ban_{pid}", dim=pid == "magic"), (x * K, y * K))
        img.alpha_composite(nine(row_tile(), 4, ROW_W, ROW_H), (x * K, y * K))
        draw_text(d, fonts, "b8", x + 6, y + 13, name, colour if pid != "magic" else C["muted"])
    window(img, d, fonts, rx, rw)
    sx, tops, sw, step = STATS
    for (head, labels), top in zip(STAT_GROUPS, tops):
        draw_text(d, fonts, "b7", sx - 2, top - 13, head, C["ink"])
        for i, label in enumerate(labels):
            draw_text(d, fonts, "m6", sx, top + i * step, label, C["muted"])
            tint(img, sx, top + i * step + 3, sx + sw, top + i * step + 3.5, C["line"])
    return img


def star(img, x, y, on=True):
    """A 7-dot star at 1 design px a dot."""
    px = Image.new("RGBA", (7, 7)); d = ImageDraw.Draw(px)
    c = hexc(C["gold"]) if on else (70, 66, 60, 255)
    for (a, b) in ((3, 0), (3, 1), (2, 2), (3, 2), (4, 2), (0, 3), (1, 3), (2, 3), (3, 3), (4, 3), (5, 3), (6, 3),
                   (1, 4), (2, 4), (3, 4), (4, 4), (5, 4), (2, 5), (4, 5), (1, 6), (5, 6)):
        d.point((a, b), fill=c)
    img.alpha_composite(px.resize((7 * K, 7 * K), Image.NEAREST), (x * K, y * K))


def item_icon(name):
    """A vanilla item texture from the local 26.2 client jar (the same picture the game shows)."""
    import zipfile as zf
    jar = Path.home() / ".minecraft/versions/26.2/26.2.jar"
    with zf.ZipFile(jar) as a:
        im = Image.open(io.BytesIO(a.read(f"assets/minecraft/textures/item/{name}.png"))).convert("RGBA")
    return im.crop((0, 0, 16, 16))


def bake_dungeon(fonts):
    """The whole screen, 480 x 270 design px at K."""
    img = Image.new("RGBA", (SCREEN_W * K, SCREEN_H * K)); d = ImageDraw.Draw(img)
    art = Image.open(ROOT / DG_KEYART).convert("RGBA")
    img.alpha_composite(art.resize((art.width * 2 * K, art.height * 2 * K), Image.NEAREST).crop((0, 0, SCREEN_W * K, SCREEN_H * K)))
    # Darken towards the left so the list and the words read over the art (Aion 2's left shade).
    for x in range(0, 300, 2):
        a = round(236 * max(0.0, 1 - x / 300) ** 1.2)
        tint(img, x, 0, x + 2, SCREEN_H, (10, 8, 12, a))
    draw_text(d, fonts, "b8", 16, 22, "ダンジョン", C["ink"])
    tint(img, 10, 32, 270, 32.5, (255, 255, 255, 30))
    # The list: one entry, open (a gold bar at its left edge).
    x, top, w, h = DG_LIST
    tint(img, x, top, x + w, top + h, (255, 255, 255, 14)); tint(img, x, top, x + 1, top + h, hexc(C["gold"]))
    for i in range(2): star(img, x + 5 + i * 8, top + 4)
    draw_text(d, fonts, "m6", x + 5, top + 21, "散り桜の社", C["ink"])
    tint(img, 100, 42, 100.5, 228, (255, 255, 255, 22))
    # The open dungeon.
    for i in range(2): star(img, DG_X + i * 8, 44)
    draw_text(d, fonts, "b8", DG_X, 64, "散り桜の社", C["ink"])
    img.alpha_composite(nine(slot_tile(True), 4, 46, 12), (DG_X * K, 69 * K))
    draw_text(d, fonts, "m6", DG_X + 23, 77.5, "ふつう", C["ink"], anchor="ms")
    for y, head in DG_HEADS:
        tint(img, DG_X, y, DG_X + DG_W, y + 11, (40, 90, 96, 70))
        img.alpha_composite(diamond(7, hexc(TEAL), None, (140, 220, 215)), ((DG_X + 1) * K, (y + 2) * K))
        draw_text(d, fonts, "m6", DG_X + 11, y + 8.5, head, TEAL)
    draw_text(d, fonts, "m6", DG_TIER[0] + 11, DG_TIER[1], "・ ひとり ・ 入場 無料", C["ink"])
    draw_text(d, fonts, "m6", DG_X + 4, 136, "部屋 2 ・ 波 4 ・ ボス 桜牙獣", C["ink"])
    for i, (name, _, count) in enumerate(DG_REWARDS):
        sx = DG_X + 4 + i * 24
        slot_frame(img, sx, DG_REWARD_Y, 20, 20)
        img.alpha_composite(item_icon(name).resize((16 * K, 16 * K), Image.NEAREST), ((sx + 2) * K, (DG_REWARD_Y + 2) * K))
        if count: draw_text(d, fonts, "m6", sx + 19, DG_REWARD_Y + 19, count, C["ink"], anchor="rs")
    ex, ey, ew, eh = DG_ENTER
    img.alpha_composite(nine(GOLD_BTN, 5, ew, eh), (ex * K, ey * K))
    draw_text(d, fonts, "b8", ex + ew / 2, ey + 13.5, "入る", C["dark"], anchor="ms")
    return img


def add_screen(sprites, name, image, unit):
    """[image] (the whole screen at K) as tiles hung from the title line: each 240 texture px square, its top in GUI px
    below the screen top (the title canvas works in GUI px from TITLE_TOP). Tiles are shared between the sizes."""
    k = round(K / unit)                            # texture px per GUI px: 4, 3, 2
    # A window a few px off 1080 (its border, a bar) moves the header by a pixel: bleed BLEED GUI px past every edge,
    # repeating the edge pixels, so no line of the world shows (2026-11-06, in game: a 1 px gap along the top).
    b = BLEED * k
    big = Image.new("RGBA", (image.width + 2 * b, image.height + 2 * b))
    big.paste(image.resize((image.width + 2 * b, image.height + 2 * b), Image.NEAREST))
    big.paste(image, (b, b))
    image = big
    tiles = []
    for py in range(0, image.height, 240):
        for px in range(0, image.width, 240):
            piece = image.crop((px, py, min(px + 240, image.width), min(py + 240, image.height)))
            tile = f"{name}_{px}_{py}"
            sprites.add(tile, piece, piece.height / k, [py / k - BLEED], origin=TITLE_TOP, unit=1, first_line=True)
            tiles.append({"sprite": tile, "dx": px / k - BLEED, "dy": py / k - BLEED})
    sprites.meta[name] = {"tiles": tiles}


def gate_body(unit):
    """Where the body sits on a 1080p screen: the dialog centres it in the space between the header and footer bands, so
    it is as many lines as fit there. Returns (lines, its top in design px)."""
    area = round(SCREEN_H * unit) - 2 * HEADER
    lines = (area - 8) // 9                        # the text widget pads 4 GUI px all round (FocusableTextWidget)
    return lines, (HEADER + (area - lines * 9 - 8) // 2 + 4) / unit


def main() -> None:
    chars = text_chars()
    fonts = build_fonts(chars)
    files: dict[str, bytes] = {}

    def png(path, image):
        buf = io.BytesIO(); image.save(buf, "PNG", optimize=True); files[path] = buf.getvalue()

    for key, data in fonts.items():
        files[f"assets/{NS}/font/menu_{key}.ttf"] = data
    metrics = {"space": {}, "sizes": {}}
    advances_map = {}
    cp = 0xF000
    for step in [2 ** i for i in range(11)] + [0.5, 0.25]:
        for sign in (1, -1):
            advances_map[chr(cp)] = step * sign; metrics["space"][str(step * sign)] = cp; cp += 1
    files[f"assets/{NS}/font/menu_space.json"] = json.dumps({"providers": [{"type": "space", "advances": advances_map}]}).encode()

    page_self, page_skills, gate_screen = bake_self(fonts), bake_skills(fonts), bake_dungeon(fonts)
    gate_screen.save(ROOT / "build/dungeon_page.png")
    self_tabs, combat_boxes = tabs_row(None, None, fonts, SELF_TABS, ""), combat_tabs(None, None, fonts)
    skill_source = (ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreSkillCatalog.kt").read_text(encoding="utf-8")
    skill_icons = sorted(set(re.findall(r's\("[^"]*", "([a-z_]+)"', skill_source)))
    skill_art = {icon: Image.open(ROOT / f"assets/core-ui/skills/{icon}.png").convert("RGBA") for icon in skill_icons}
    slot_tops = sorted({y for _, y in KEYS + GRID + ULTS})
    sel = nine(row_tile(True), 4, SLOT, SLOT)
    dim = Image.new("RGBA", (SLOT * K, SLOT * K), (10, 10, 11, 170))
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
        sprites.add_tiled("page_self", page_self, CANVAS_H, [0], first_line=True)
        sprites.add_tiled("page_skills", page_skills, CANVAS_H, [0], first_line=True)
        add_screen(sprites, "gate_screen", gate_screen, unit)
        gate_lines, gate_top = gate_body(unit)
        for c in CLASSES:
            art = Image.open(ROOT / f"assets/core-ui/proposals/2026-10-16-class-emblems/{c}-32.png").convert("RGBA")
            sprites.add(f"emblem64_{c}", art.resize((64 * K, 64 * K), Image.NEAREST).resize((256, 256), Image.NEAREST), 64, [EMBLEM64[1]])
            sprites.add(f"emblem24_{c}", art.resize((24 * K, 24 * K), Image.NEAREST), 24, [EMBLEM24[1]])
        for icon, art in skill_art.items():
            sprites.add(f"sk20_{icon}", art.resize((20 * K, 20 * K), Image.NEAREST), 20, [t + 3 for t in slot_tops])
            sprites.add(f"skb_{icon}", art.resize((64 * K, 64 * K), Image.NEAREST).resize((256, 256), Image.NEAREST), 64, [BIG[1]])
        sprites.add("slot_sel", sel, SLOT, slot_tops)
        sprites.add("slot_dim", dim, SLOT, slot_tops)
        sprites.add("slot_x", cross, SLOT, slot_tops)
        sprites.add("key_tri", tri, 9, slot_tops)
        sprites.add("dia_on", diamond(10, (233, 221, 208, 255), None, (255, 230, 200)), 10, [DIAS[0][1]])
        sprites.add("dia_gear", diamond(10, hexc(C["heat"]), None, (255, 150, 80)), 10, [DIAS[0][1]])
        sprites.add("bar_fill", Image.new("RGBA", (2 * K, 2 * K), hexc(C["ink"])), 2, [BARS[0][1]])
        # invisible hit areas (alpha 1: under the client's 0.1 cut, but the glyph rectangle still takes clicks)
        hits = {"slot": (SLOT, SLOT, slot_tops), "row": (ROW_W, ROW_H, [y for _, y in ROWS]), "act": (ACT_W, BTN_H, [y for _, y in ACTS]),
                "edit": (EDIT[2], BTN_H, [EDIT[1]]), "reset": (110, 10, [RESET[1]]),
                "enter": (DG_ENTER[2], DG_ENTER[3], [DG_ENTER[1] - gate_top]), "reward": (20, 20, [DG_REWARD_Y - gate_top])}
        for page, boxes in (("self", self_tabs), ("combat", combat_boxes)):
            for i, (x, y, w, h) in enumerate(boxes):
                hits[f"tab_{page}{i}"] = (w, h, [y])
        for name, (w, h, tops) in hits.items():
            q = 2 if w > 256 else 1                # the glyph keeps the texture's aspect, so a wide one is drawn at half
            sprites.add(f"hit_{name}", Image.new("RGBA", (w // q, h // q), (0, 0, 0, 1)), h, tops)
        # The vanilla button's label is drawn after its sprite and unclipped, so a label the button's size covers it.
        # Its text line starts 6 GUI px below the button's top (the canvas origin), so the glyph hangs 6 px above it.
        close_h = round(20 / unit)                 # 20, 15, 10 design px
        close = nine(DARK_BTN, 5, CLOSE_W, close_h)
        draw_text(ImageDraw.Draw(close), fonts, "b7", CLOSE_W / 2, close_h / 2 + 3, "閉じる", C["muted"], anchor="ms")
        sprites.add(f"close{tag}", close, 20, [0], origin=6, unit=1)
        sprites.meta["close"] = sprites.meta.pop(f"close{tag}")
        files[f"assets/{NS}/font/menu{tag}_art.json"] = json.dumps({"providers": sprites.providers}).encode()
        size["sprites"] = sprites.meta
        size["layout"] = {"tabsSelf": [list(b) for b in self_tabs], "tabsCombat": [list(b) for b in combat_boxes], "emblem64": list(EMBLEM64), "hex": [list(h) for h in HEX],
                          "keys": [list(k) for k in KEYS], "grid": [list(g) for g in GRID], "ults": [list(u) for u in ULTS],
                          "rx": RX, "big": list(BIG), "bars": [list(b) for b in BARS], "barW": BAR_W, "dias": [list(x) for x in DIAS],
                          "acts": [list(a) for a in ACTS], "reset": list(RESET), "emblem24": list(EMBLEM24), "name": list(NAME),
                          "kv": list(KV), "rows": [list(r) for r in ROWS], "rowW": ROW_W, "rowH": ROW_H, "stats": [STATS[0], STATS[1][0], STATS[1][1], STATS[2], STATS[3]], "notice": list(NOTICE),
                          "edit": list(EDIT), "closeW": CLOSE_W,
                          "gateTier": list(DG_TIER), "gateEnter": list(DG_ENTER), "gateLines": gate_lines, "gateTop": gate_top,
                          "gateRewards": [[DG_X + 4 + i * 24, DG_REWARD_Y] for i in range(len(DG_REWARDS))],
                          "gateRewardNames": [n for _, n, _ in DG_REWARDS], "titleX": TITLE_X}
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
