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
at 4x (GUI scale 4 = one screen pixel). Words, numbers, rings, the selected row and hit areas are placed per player.
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
K = 4                      # texture pixels per GUI pixel (GUI scale 4 at 1080p)
OVERSAMPLE = 4.0
CANVAS_W, LINES = 464, 21  # body canvas: 464 x 189 GUI px
CANVAS_H = LINES * 9
TITLE_W = 434              # title advance: puts the vanilla warning button at the right edge of a 480 px screen
TITLE_X = 8                # where the title starts on a 480 px screen ((480 - TITLE_W - 30) / 2)
TITLE_TOP = 12             # the title line's top on screen

TEXT_SOURCES = ["server-minestom/src/main/kotlin/dev/projects/server/coreloop/PlayerMenuDialog.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/coreloop/CoreJourney.kt",
                "server-minestom/src/main/kotlin/dev/projects/server/questmap/QuestGathering.kt"]

# style -> (font file key, size)
STYLES = {"m6": ("m", 6), "m7": ("m", 7), "b7": ("b", 7), "b8": ("b", 8), "b9": ("b", 9), "b14": ("b", 14),
          "d6": ("d", 6), "d7": ("d", 7), "d9": ("d", 9)}

C = dict(text="#ece6dc", muted="#8f8a82", faint="#5e5a54", ivory="#efe6d2", gold="#e8c878", heat="#ff9a4a",
         line=(255, 255, 255, 23), panel=(20, 21, 24, 214), dark="#16181c")
PATHS = [  # id, name, icon, colour
    ("combat", "戦い", "class_warrior", "#ff9a4a"),
    ("gather", "採取", "ore", "#6fd0c0"),
    ("smith", "鍛冶", "ingot", "#ffd166"),
    ("magic", "魔術", "firebolt", "#a98cf0")]
CLASSES = ["warrior", "ranger", "mage", "assassin", "templar", "healer"]

# ---------------------------------------------------------------- layout (canvas px; exported for PlayerMenuCanvas)
TABS = ["自分", "技", "パッシブ", "持ち物", "目標"]
TAB_BASE, TAB_GAP = 10, 16
LEFT_X, ROW_Y0, ROW_STEP, ROW_W, ROW_H = 4, 32, 30, 136, 26
BAR_DX, BAR_DY, BAR_W, BAR_H = 28, 17, 104, 3
CARD = (146, 22, 172, 165)
EMBLEM = (212, 27, 40)                 # x, y, size
RING_Y, RING, RING_GAP = 120, 20, 12
EDIT = (186, 168, 92, 14)
PANEL = (324, 22, 136, 165)
PRIMARY = (330, 166, 124, 16)


def hexc(v, a=255):
    if isinstance(v, tuple):
        return v
    return tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


def text_chars() -> set[int]:
    chars = set(range(0x20, 0x7F)) | set(range(0x3041, 0x3097)) | set(range(0x30A0, 0x3100)) | {ord(c) for c in "・·×／〜…％、。「」『』（）：！？―─→›‹◆"}
    chars |= {ord(c) for c in "".join(TABS) + "".join(p[1] for p in PATHS) + "冒険の手帳銀貨道名刺を整える"}
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


def advances(ttf: bytes, size: int, chars: set[int]) -> dict[str, float]:
    """Advance per character in GUI px, the way the client measures it (FreeType at size*oversample, / oversample)."""
    font = ImageFont.truetype(io.BytesIO(ttf), int(round(size * OVERSAMPLE)))
    from fontTools.ttLib import TTFont
    cmap = TTFont(io.BytesIO(ttf)).getBestCmap()
    return {str(cp): round(font.getlength(chr(cp)) / OVERSAMPLE, 4) for cp in sorted(chars) if cp in cmap}


class Sprites:
    """Bitmap glyphs: one private-use char per (sprite, ascent)."""

    def __init__(self):
        self.next = 0xE000
        self.images: dict[str, Image.Image] = {}
        self.meta: dict[str, dict] = {}
        self.providers: list[dict] = []

    def add(self, name: str, image: Image.Image, gui_h: int, ascents: set[int]):
        self.images[name] = image
        alpha = image.getchannel("A")
        bbox = alpha.point(lambda a: 255 if a > 0 else 0).getbbox()
        actual = bbox[2] if bbox else 0
        scale = gui_h / image.height
        entry = {"advance": int(0.5 + actual * scale) + 1, "w": round(image.width * scale), "h": gui_h, "chars": {}}
        for ascent in sorted(ascents):
            ch = self.next; self.next += 1
            self.providers.append({"type": "bitmap", "file": f"{NS}:menu/{name}.png", "ascent": ascent,
                                   "height": gui_h, "chars": [chr(ch)]})
            entry["chars"][str(ascent)] = ch
        self.meta[name] = entry


def body_ascents(top: int, h: int) -> set[int]:
    """Ascent for a sprite whose top is `top` px below the canvas top. The client rejects ascent > height, so a short
    sprite hangs from the line above instead (PlayerMenuCanvas tries the same two lines in the same order)."""
    r = top % 9
    return {7 - r if 7 - r <= h else 7 - r - 9}


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


def bake_page(fonts, icons) -> Image.Image:
    img = Image.new("RGBA", (CANVAS_W * K, CANVAS_H * K))
    back = Image.new("RGBA", img.size, (9, 10, 13, 150))
    img.alpha_composite(back)
    d = ImageDraw.Draw(img)
    # tab row (自分 selected)
    widths = [text_w(fonts, "b8", t) for t in TABS]
    x = (CANVAS_W - (sum(widths) + TAB_GAP * (len(TABS) - 1))) / 2
    tab_boxes = []
    for t, w in zip(TABS, widths):
        draw_text(d, fonts, "b8", x, TAB_BASE, t, C["ivory"] if t == "自分" else C["muted"])
        tab_boxes.append((round(x) - 2, TAB_BASE - 9, round(w) + 4, 13))
        if t == "自分":
            g = glow(int(w) + 12, 8, (w + 12) / 2, 4, (w + 12) / 2, 4, (255, 170, 90), 140)
            img.alpha_composite(g, (int((x - 6) * K), (TAB_BASE + 1) * K))
            d.rectangle((x * K, (TAB_BASE + 3) * K, (x + w) * K, (TAB_BASE + 3) * K + 2 * K - 1), fill=hexc(C["ivory"]))
        x += w + TAB_GAP
    d.rectangle((0, 17 * K, CANVAS_W * K, 17 * K + K - 1), fill=C["line"])
    # left: the paths
    draw_text(d, fonts, "m6", LEFT_X, 28, "道", C["muted"])
    for i, (pid, name, icon, colour) in enumerate(PATHS):
        top = ROW_Y0 + i * ROW_STEP
        ic = icons[icon].resize((16 * K, 16 * K), Image.NEAREST)
        img.alpha_composite(ic, ((LEFT_X + 6) * K, (top + 4) * K))
        draw_text(d, fonts, "b7", LEFT_X + 28, top + 11, name, C["text"])
        d.rectangle(((LEFT_X + BAR_DX) * K, (top + BAR_DY) * K, (LEFT_X + BAR_DX + BAR_W) * K - 1, (top + BAR_DY + BAR_H) * K - 1), fill=(11, 12, 15, 255))
    # card
    x0, y0, w, h = CARD
    card = Image.new("RGBA", (w * K, h * K))
    cd = ImageDraw.Draw(card)
    for yy in range(h * K):
        t = min(1.0, yy / (70 * K))
        col = tuple(int(a + (b - a) * t) for a, b in zip((122, 59, 34), (20, 21, 24))) + (int(200 + 20 * t),)
        cd.line([(0, yy), (w * K, yy)], fill=col)
    mask = Image.new("L", card.size, 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * K - 1, h * K - 1), radius=3 * K, fill=255)
    card.putalpha(Image.composite(card.getchannel("A"), Image.new("L", card.size, 0), mask))
    img.alpha_composite(card, (x0 * K, y0 * K))
    img.alpha_composite(glow(w, 70, w / 2, 26, 60, 34, (255, 130, 60), 120), (x0 * K, y0 * K))
    rounded(d, CARD, outline=(255, 190, 130, 64))
    ex, ey, es = EMBLEM
    for inset, col in ((-3, (255, 190, 130, 64)), (-1, (255, 190, 130, 180))):
        d.rectangle(((ex + inset) * K, (ey + inset) * K, (ex + es - inset) * K - 1, (ey + es - inset) * K - 1), outline=col, width=K // 2)
    rounded(d, EDIT, fill=(11, 12, 15, 220), outline=(255, 255, 255, 30), r=2)
    label = "名刺を整える ›"
    draw_text(d, fonts, "m7", EDIT[0] + (EDIT[2] - text_w(fonts, "m7", label)) / 2, EDIT[1] + 10, label, "#c9c3b8")
    # right panel
    rounded(d, PANEL, fill=C["panel"], outline=(255, 255, 255, 20))
    px, py, pw, ph = PRIMARY
    rounded(d, PRIMARY, fill=hexc(C["ivory"]), r=2)
    d.rectangle((px * K, (py + ph) * K - K, (px + pw) * K - 1, (py + ph) * K - 1), fill=hexc("#c9bea6"))
    return img, tab_boxes


def bake_band(fonts, book) -> Image.Image:
    w, h = 480, 33
    img = Image.new("RGBA", (w * K, h * K))
    d = ImageDraw.Draw(img)
    for yy in range(h * K):
        d.line([(0, yy), (w * K, yy)], fill=(14, 15, 18, int(235 - 50 * yy / (h * K))))
    d.rectangle((0, (h - 1) * K, w * K, h * K - 1), fill=(255, 255, 255, 20))
    img.alpha_composite(book.resize((24 * K, 25 * K), Image.NEAREST), (12 * K, 4 * K))
    draw_text(d, fonts, "b9", 42, 19, "冒険の手帳", C["text"])
    # wallet chip: the number is drawn per player, right-aligned at WALLET_RIGHT
    rounded(d, (352, 9, 92, 15), fill=(232, 200, 120, 18), outline=(232, 200, 120, 64), r=2)
    draw_text(d, fonts, "d9", 357, 20, "◆", C["gold"])
    draw_text(d, fonts, "m6", 423, 20, "銀貨", C["muted"])
    return img


def ring(colour, step, steps=10):
    img = Image.new("RGBA", (RING * K, RING * K))
    d = ImageDraw.Draw(img)
    box = (K, K, RING * K - K - 1, RING * K - K - 1)
    d.ellipse((0, 0, RING * K - 1, RING * K - 1), fill=(21, 22, 26, 255))
    d.arc(box, 0, 360, fill=(255, 255, 255, 40), width=2 * K)
    if step:
        d.arc(box, -90, -90 + 360 * step / steps, fill=hexc(colour), width=2 * K)
    return img


def main() -> None:
    parser = argparse.ArgumentParser(); parser.add_argument("--fonts", required=True)
    src = Path(parser.parse_args().fonts)
    chars = text_chars()
    fonts = build_fonts(src, chars)
    files: dict[str, bytes] = {}

    def png(path, image):
        buf = io.BytesIO(); image.save(buf, "PNG", optimize=True); files[path] = buf.getvalue()

    pack = zipfile.ZipFile(OUT / "pack.zip"); atlas = zipfile.ZipFile(OUT / "atlas.zip")
    raw = lambda z, p: Image.open(io.BytesIO(z.read(p))).convert("RGBA")
    icons = {"ore": raw(pack, f"assets/{NS}/textures/forge_materials/ore.png"),
             "ingot": raw(pack, f"assets/{NS}/textures/forge_materials/ingot.png"),
             "firebolt": Image.open(ROOT / "assets/core-ui/skills/firebolt.png").convert("RGBA"),
             "class_warrior": Image.open(ROOT / "assets/core-ui/proposals/2026-10-16-class-emblems/warrior-32.png").convert("RGBA")}
    book = raw(pack, f"assets/{NS}/textures/raw/book_ui.png")

    # fonts: one TTF file per weight, one definition per (style, line offset 0..8)
    for key, data in fonts.items():
        files[f"assets/{NS}/font/menu_{key}.ttf"] = data
    metrics = {"canvasWidth": CANVAS_W, "lines": LINES, "titleWidth": TITLE_W, "titleX": TITLE_X, "titleTop": TITLE_TOP,
               "styles": {}, "sprites": {}, "space": {}, "layout": {}}
    for style, (key, size) in STYLES.items():
        for r in range(9):
            files[f"assets/{NS}/font/menu_{style}_{r}.json"] = json.dumps({"providers": [
                {"type": "ttf", "file": f"{NS}:menu_{key}.ttf", "size": float(size), "oversample": OVERSAMPLE, "shift": [0.0, float(r)]},
            ]}).encode()
        metrics["styles"][style] = {"font": f"{NS}:menu_{style}_", "advances": advances(fonts[key], size, chars)}

    # space font: powers of two both ways plus quarters
    steps = [2 ** i for i in range(10)] + [0.5, 0.25]
    advances_map = {}
    cp = 0xF000
    for s in steps:
        for sign in (1, -1):
            advances_map[chr(cp)] = s * sign; metrics["space"][str(s * sign)] = cp; cp += 1
    files[f"assets/{NS}/font/menu_space.json"] = json.dumps({"providers": [{"type": "space", "advances": advances_map}]}).encode()

    sprites = Sprites()
    page, tab_boxes = bake_page(fonts, icons)
    sprites.add("page_self", page, CANVAS_H, {7})
    sprites.add("band", bake_band(fonts, book), 33, {7 + TITLE_TOP})
    for c in CLASSES:
        path = ROOT / f"assets/core-ui/proposals/2026-10-16-class-emblems/{c}-32.png"
        sprites.add(f"emblem_{c}", Image.open(path).convert("RGBA").resize((EMBLEM[2] * K, EMBLEM[2] * K), Image.NEAREST), EMBLEM[2], body_ascents(EMBLEM[1], EMBLEM[2]))
    for i, (pid, name, icon, colour) in enumerate(PATHS):
        top = ROW_Y0 + i * ROW_STEP
        bar = Image.new("RGBA", (2 * K, BAR_H * K), hexc(colour))
        sprites.add(f"bar_{pid}", bar, BAR_H, body_ascents(top + BAR_DY, BAR_H))
        for step in range(11):
            sprites.add(f"ring_{pid}_{step}", ring(colour, step), RING, body_ascents(RING_Y, RING))
    sel = Image.new("RGBA", (ROW_W * K, ROW_H * K)); sd = ImageDraw.Draw(sel)
    for xx in range(ROW_W * K):
        sd.line([(xx, 0), (xx, ROW_H * K)], fill=(239, 230, 210, int(34 * (1 - xx / (ROW_W * K)))))
    sd.rectangle((0, 0, 2 * K - 1, ROW_H * K - 1), fill=hexc(C["ivory"]))
    sprites.add("row_sel", sel, ROW_H, {a for i in range(4) for a in body_ascents(ROW_Y0 + i * ROW_STEP, ROW_H)})
    halo = Image.new("RGBA", ((RING + 4) * K, (RING + 4) * K)); hd = ImageDraw.Draw(halo)
    hd.ellipse((0, 0, (RING + 4) * K - 1, (RING + 4) * K - 1), outline=hexc(C["ivory"]), width=K)
    sprites.add("ring_sel", halo, RING + 4, body_ascents(RING_Y - 2, RING + 4))
    # invisible hit areas (alpha 1: under the client's 0.1 cut, but the glyph rectangle still takes clicks)
    hits = {"row": (ROW_W, ROW_H, {a for i in range(4) for a in body_ascents(ROW_Y0 + i * ROW_STEP, ROW_H)}),
            "edit": (EDIT[2], EDIT[3], body_ascents(EDIT[1], EDIT[3])), "primary": (PRIMARY[2], PRIMARY[3], body_ascents(PRIMARY[1], PRIMARY[3]))}
    for i, (x, y, w, h) in enumerate(tab_boxes):
        hits[f"tab{i}"] = (w, h, body_ascents(y, h))
    for name, (w, h, asc) in hits.items():
        sprites.add(f"hit_{name}", Image.new("RGBA", (w, h), (0, 0, 0, 1)), h, asc)
        # a fully transparent column at the right would shrink the advance; alpha 1 everywhere keeps it = w + 1
    for name, image in sprites.images.items():
        png(f"assets/{NS}/textures/menu/{name}.png", image)
    files[f"assets/{NS}/font/menu_art.json"] = json.dumps({"providers": sprites.providers}).encode()
    metrics["sprites"] = sprites.meta
    metrics["layout"] = {"tabs": [list(b) for b in tab_boxes], "left": LEFT_X, "rowY": ROW_Y0, "rowStep": ROW_STEP, "rowW": ROW_W,
                         "barDx": BAR_DX, "barDy": BAR_DY, "barW": BAR_W, "card": list(CARD), "emblem": list(EMBLEM),
                         "ringY": RING_Y, "ring": RING, "ringGap": RING_GAP, "edit": list(EDIT), "panel": list(PANEL),
                         "primary": list(PRIMARY), "wallet": [440 - 18, 20]}
    with zipfile.ZipFile(OUT / "menu.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    (OUT / "menu-metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"menu.zip {sum(len(v) for v in files.values()) // 1024} KB, {len(chars)} chars, {len(sprites.providers)} sprite glyphs")


if __name__ == "__main__":
    main()
