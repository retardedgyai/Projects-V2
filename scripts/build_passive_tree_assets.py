"""Bake the passive tree screen (the camera + display-entity UI, like the 開拓大陸 map).

    python3 scripts/build_passive_tree_assets.py [preview | preview-only]

Reads assets/ui/passive-tree/tree.json and icons.json and writes
server-minestom/src/main/resources/polish05/passive-tree.zip (private-namespace sprites and the dot font, merged
into the UI pack by CoreUiPackServer), polish05/passive-tree.json (sprite chars and where each one sits) and the
dot font's widths into web-ui-lab/src/main/resources/polish05-font-metrics.json.

Everything is pixel art, in the 冒険の手帳's look (藍 2026-11-04: 「ちゃんと全部ドットにしよう」):
  - the tree (sky, lines, rings, badges) on one art grid of DOT design px, drawn without anti-aliasing;
  - words in x12y12pxMaruMinya, one font dot = 2 design px (3 for names), white with a one-dot shadow;
  - panels and buttons are the 手帳's iron windows and plates (scripts/build_menu_dialog_assets.py), 2 px a dot.
The tree is baked into opaque 256 px tiles at SCALE design px per tree unit (zoom 1 in game). What changes per
player is drawn on top: gold lines per edge (cut out of a gold-lit bake), dashed lines for the planned path, one
lit badge per (kind, icon), and state rings. Words are text: the dot font is a glyph family ("ptdot", "ptdotn"
without the shadow) on the forge pages' 32-unit metrics, so the flow writes any string.

`preview` (after building) and `preview-only` run PassiveTreeFlowTest, which writes the flow's real scene for a
warrior with 14 points taken and a notable selected, and render it with scripts/render_grimoire_preview.py to
docs/proposals/skill-tree-2026-10/passive-tree-screen.png.
"""
from __future__ import annotations

import io
import json
import math
import re
import subprocess
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_menu_dialog_assets import BLACK, BRONZE, BRONZE_HI, DARK_BTN, GOLD_BTN, MARU, MARU_PX, button_tile, slot_tile, window_tile  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/passive-tree/tree.json"
ICONS = ROOT / "assets/ui/passive-tree/icons.json"
FLOW = ROOT / "server-minestom/src/main/kotlin/dev/projects/server/coreloop/PassiveTreeFlow.kt"
OUT = ROOT / "server-minestom/src/main/resources/polish05"
METRICS = ROOT / "web-ui-lab/src/main/resources/polish05-font-metrics.json"
NS = "projects_ui_polish05"
FONT = f"{NS}:passive_tree"
FIRST_CHAR = 0xE000
LAST_CHAR = 0xF8FF  # the BMP private use area; this font is ours alone, so the whole range is free
TILE = 256
DOT = 3                 # one art pixel = DOT design px at zoom 1
SCALE = 0.207 * DOT     # design px per tree unit at zoom 1 (the prototype's ZOOM_PER_DOT * dot)
PAD = 160               # tree units of margin around the outermost node
DOT_CELL = 15           # font dots per glyph cell: 12 for the letter, room for descenders and the shadow
PLATE = 2               # design px per dot of the panels, plates and words

# ---- palette (the 手帳's)
INK = (236, 230, 220)
MUTE = (154, 148, 140)
EMBER = (255, 154, 74)
EMBER_SOFT = (255, 196, 138)
GOLD = (242, 200, 75)
OK = (154, 240, 160)
SKY = (16, 17, 22)
OUTLINE = (10, 10, 14)
SHADOW = (8, 8, 10)
CLASSES = [("WARRIOR", "戦士", -90, "#E0604A"), ("TEMPLAR", "テンプラー", -30, "#E8C35A"), ("HEALER", "ヒーラー", 30, "#7CD69B"),
           ("MAGE", "メイジ", 90, "#7FA6FF"), ("ASSASSIN", "アサシン", 150, "#B58CFF"), ("RANGER", "レンジャー", 210, "#5FC7C0")]
KINDS = ["SMALL", "NOTABLE", "KEYSTONE", "START", "MASTERY"]
FRAME_SIZE = {"SMALL": 13, "NOTABLE": 19, "KEYSTONE": 29, "START": 23, "MASTERY": 13}
# Panel and button sizes (design px); PassiveTreeFlow places them.
LEFT_W, RIGHT_W, PANEL_H = 380, 460, 908
GO_W, GO_H = 408, 72
BTN_W, BTN_H = 129, 48
CHOICE_H = [18 + 28 * lines for lines in range(1, 5)]   # a mastery option: 1-3 lines (+1 for 選び済み)
CHIP_W, CHIP_H = 236, 44


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def mix(a, b, t):
    return tuple(round(v + (w - v) * t) for v, w in zip(a, b))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def region(n):
    """Colour by direction from the centre: a class colour, blended across the wedge between two classes."""
    if math.hypot(n["x"], n["y"]) < 420:
        return INK
    deg = math.degrees(math.atan2(n["y"], n["x"]))
    rel = ((deg + 90) % 360 + 360) % 360
    i = int(rel // 60); f = (rel % 60) / 60
    t = 0 if f < .3 else 1 if f > .7 else (f - .3) / .4
    return mix(hexrgb(CLASSES[i][3]), hexrgb(CLASSES[(i + 1) % 6][3]), t)


# ------------------------------------------------------------------ dot words

_maru = []


def maru():
    if not _maru:
        _maru.append(ImageFont.truetype(str(MARU), MARU_PX))
    return _maru[0]


def dots(text):
    """[text] on the font's own 12 px grid as a 1-bit mask, baseline at row MARU_PX."""
    f = maru()
    m = Image.new("L", (max(1, round(f.getlength(text))) + 1, MARU_PX + 4))
    ImageDraw.Draw(m).text((0, MARU_PX), text, font=f, fill=255, anchor="ls")
    return m.point(lambda a: 255 if a > 110 else 0)


def solid(size, rgb):
    return Image.new("RGBA", size, tuple(rgb) + (255,))


def word(text, fill, n=PLATE, outline=None, shadow=True):
    """Baked dot words: each font dot n x n design px, with a one-dot shadow (or a ring of [outline])."""
    m = dots(text)
    big = Image.new("L", (m.width + 2, m.height + 2)); big.paste(m, (1, 1))
    out = Image.new("RGBA", big.size)
    if outline:
        out.paste(solid(big.size, outline), (0, 0), big.filter(ImageFilter.MaxFilter(3)))
    elif shadow:
        out.paste(solid(big.size, SHADOW), (1, 1), big)
    out.paste(solid(big.size, fill), (0, 0), big)
    return out.resize((out.width * n, out.height * n), Image.NEAREST)


def text_chars():
    """Every character the screen can write: node names and texts, the flow's own strings, ASCII."""
    tree = json.loads(SOURCE.read_text(encoding="utf-8"))
    chars = {chr(c) for c in range(0x21, 0x7F)} | set("、。・：（）＋－／％→←〜…")
    for n in tree["nodes"]:
        chars |= set(n["name"]) | set(n["text"])
        for t in n.get("choiceTexts", []):
            chars |= set(t)
    for literal in re.findall(r'"([^"\\]*)"', FLOW.read_text(encoding="utf-8")):
        chars |= set(literal)
    return sorted(c for c in chars if not c.isspace())


def dot_font(files, metrics, family, chars, shadow):
    """The dot font as glyph pages on the forge pages' metrics (cells declared 40 tall with ascent 32, widths in
    32-unit font px), so UiRenderer and Polish05FontMetrics treat it like any other family."""
    f = maru(); scale = 40 / DOT_CELL
    usable = [c for c in chars if f.getmask(c).getbbox() is not None]
    widths = {}
    providers = [{"type": "space", "advances": {" ": round(6 * scale)}}]
    for page, start in enumerate(range(0, len(usable), 256)):
        part = usable[start:start + 256]
        image = Image.new("RGBA", (16 * DOT_CELL, 16 * DOT_CELL))
        for i, ch in enumerate(part):
            m = Image.new("L", (DOT_CELL, DOT_CELL))
            ImageDraw.Draw(m).text((0, MARU_PX), ch, font=f, fill=255, anchor="ls")
            m = m.point(lambda a: 255 if a > 110 else 0)
            g = Image.new("RGBA", (DOT_CELL, DOT_CELL))
            if shadow:
                g.paste(solid((DOT_CELL - 1, DOT_CELL - 1), SHADOW), (1, 1), m.crop((0, 0, DOT_CELL - 1, DOT_CELL - 1)))
            g.paste(solid(g.size, (255, 255, 255)), (0, 0), m)
            # The client takes a glyph's advance from its rightmost non-clear column: an alpha-1 texel (under the
            # text cutoff, so never drawn) at the font's own advance keeps the letters on the font's spacing.
            adv = max(2, min(DOT_CELL, round(f.getlength(ch))))
            if g.getpixel((adv - 1, DOT_CELL - 1))[3] == 0:
                g.putpixel((adv - 1, DOT_CELL - 1), (255, 255, 255, 1))
            right = g.getchannel("A").getbbox()[2]
            widths[ch] = round(right * scale) + 1
            image.paste(g, ((i % 16) * DOT_CELL, (i // 16) * DOT_CELL))
        name = f"passive_tree/font/{family}_{page}.png"
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{name}"] = buffer.getvalue()
        rows = ["".join(part[r:r + 16]).ljust(16, "\0") for r in range(0, 256, 16)]
        providers.append({"type": "bitmap", "file": f"{NS}:{name}", "ascent": 32, "height": 40, "chars": rows})
    providers.append({"type": "reference", "id": f"{NS}:sans"})
    files[f"assets/{NS}/font/{family}.json"] = json.dumps({"providers": providers}, ensure_ascii=False, separators=(",", ":")).encode()
    metrics[family] = {str(ord(c)): w for c, w in sorted(widths.items(), key=lambda p: ord(p[0]))}
    metrics[family]["32"] = round(6 * scale)


# ------------------------------------------------------------------ pixel badges (the prototype's sprite())

def frame_mask(kind):
    """'#' outline, 'r' rim, 'i' inside, '.' empty. Built from simple shapes so every size stays crisp."""
    size = FRAME_SIZE[kind]; c = (size - 1) / 2; rows = []
    for y in range(size):
        row = ""
        for x in range(size):
            dx, dy = abs(x - c), abs(y - c)
            if kind == "KEYSTONE":
                d = dx + dy
            elif kind in ("NOTABLE", "START"):
                d = max(dx, dy, (dx + dy) * 0.72)
            else:
                d = math.hypot(dx, dy)
            r = c + (0 if kind == "KEYSTONE" else .35)
            inner = 2 if kind == "SMALL" else 3
            row += "." if d > r else "#" if d > r - 1 else "r" if d > r - inner else "." if kind == "MASTERY" and d > r - 4 else "i"
        rows.append(row)
    return rows


FRAMES = {k: frame_mask(k) for k in KINDS}


def icon_of(n, iconset):
    """Start: the class icon; keystone: the crown; otherwise the icon of the first plain stat (else the gem)."""
    if n.get("start"):
        return iconset["classIcon"][n["start"]]
    if n["kind"] == "KEYSTONE":
        return "crown"
    if n["kind"] == "MASTERY":
        return "none"
    keys = list(n["stats"])
    plain = [k for k in keys if "@" not in k] or [k.split("@")[0] for k in keys]
    return iconset["statIcon"].get(plain[0], "gem") if plain else "gem"


def badge(kind, icon, rgb, on, iconset, foreign_start=False):
    """One node badge as a DOT-scaled RGBA image (transparent outside the frame)."""
    fr = FRAMES[kind]; s = len(fr)
    px = np.zeros((s, s, 4), np.uint8)
    rim = GOLD if on else shade(rgb, .45) if foreign_start else shade(rgb, .85) if kind == "SMALL" else rgb
    inside = shade(GOLD, .42) if on else shade(rgb, .38) if kind == "SMALL" else (24, 24, 30)
    pal = {"#": OUTLINE, "r": rim, "i": inside}
    for y, row in enumerate(fr):
        for x, ch in enumerate(row):
            if ch in pal:
                px[y, x] = (*pal[ch], 255)
    if kind == "MASTERY":
        mark = [".k.", "kak", ".k."]; o = (s >> 1) - 1
        for y, row in enumerate(mark):
            for x, ch in enumerate(row):
                if ch != ".":
                    px[o + y, o + x] = (*(OUTLINE if ch == "k" else GOLD if on else rgb), 255)
    else:
        art = iconset["icons"][icon]["small" if kind == "SMALL" else "big"]; o = (s - len(art)) >> 1
        for y, row in enumerate(art):
            for x, c in enumerate(row):
                if not c:
                    continue
                v = hexrgb(c)
                if not on:
                    lum = v[0] * .3 + v[1] * .59 + v[2] * .11
                    v = tuple(round((t * .55 + lum * .45) * .78) for t in v)
                px[o + y, o + x] = (*v, 255)
    return Image.fromarray(px, "RGBA").resize((s * DOT, s * DOT), Image.NEAREST)


# ------------------------------------------------------------------ geometry

def bounds(nodes):
    xs = [n["x"] for n in nodes]; ys = [n["y"] for n in nodes]
    return min(xs) - PAD, min(ys) - PAD, max(xs) + PAD, max(ys) + PAD


def edge_points(tree, a, b, arc):
    """Tree-unit polyline of one edge: a straight line, or an arc around the group centre."""
    if not arc:
        return [(a["x"], a["y"]), (b["x"], b["y"])]
    g = tree["groups"][a["group"]]; r = tree["orbitRadii"][a["orbit"]]
    a1 = math.atan2(a["y"] - g["y"], a["x"] - g["x"]); a2 = math.atan2(b["y"] - g["y"], b["x"] - g["x"])
    d = (a2 - a1 + math.pi) % (2 * math.pi) - math.pi
    steps = max(8, math.ceil(abs(d) * r * SCALE / 4))
    return [(g["x"] + r * math.cos(a1 + d * t / steps), g["y"] + r * math.sin(a1 + d * t / steps)) for t in range(steps + 1)]


def stroke(draw, pts, width, fill):
    """A round-capped, round-joined polyline (PIL lines have square caps)."""
    draw.line(pts, fill=fill, width=max(1, round(width)), joint="curve")
    r = width / 2
    for x, y in (pts[0], pts[-1]):
        draw.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def dashes(pts, on, off):
    """Split a polyline into dash polylines (lengths in the polyline's units)."""
    out, cur, pos, drawing = [], [pts[0]], 0.0, True
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        seg = math.hypot(x1 - x0, y1 - y0); t = 0.0
        while seg - t > 1e-9:
            length = on if drawing else off
            step = min(length - pos, seg - t); t += step; pos += step
            p = (x0 + (x1 - x0) * t / seg, y0 + (y1 - y0) * t / seg)
            if drawing:
                cur.append(p)
            if pos >= length - 1e-9:
                if drawing and len(cur) > 1:
                    out.append(cur)
                drawing = not drawing; pos = 0.0; cur = [p]
    if drawing and len(cur) > 1:
        out.append(cur)
    return out


def up(img):
    """An art-grid image at design px (DOT x DOT per art pixel)."""
    return img.resize((img.width * DOT, img.height * DOT), Image.NEAREST)


def polyline(draw, pts, width, fill):
    draw.line([(round(x), round(y)) for x, y in pts], fill=fill, width=width, joint="curve" if width > 1 else None)


BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) + .5) / 16


# ------------------------------------------------------------------ the tree bake (on the art grid)

class TreeBake:
    def __init__(self, tree, iconset):
        self.tree = tree; self.iconset = iconset
        self.nodes = tree["nodes"]
        self.x0, self.y0, x1, y1 = bounds(self.nodes)
        self.aw, self.ah = math.ceil((x1 - self.x0) * SCALE / DOT), math.ceil((y1 - self.y0) * SCALE / DOT)
        self.w, self.h = self.aw * DOT, self.ah * DOT
        for n in self.nodes:
            n["rgb"] = region(n)
            n["icon"] = icon_of(n, iconset)
            s = FRAME_SIZE[n["kind"]]
            # Badges sit on the art grid; the spot is the badge centre so lit badges land exactly on it.
            n["ax"] = round(self.ax(n["x"]) - s / 2); n["ay"] = round(self.ay(n["y"]) - s / 2)
            n["bx"], n["by"] = n["ax"] * DOT, n["ay"] * DOT
            n["cx"] = n["bx"] + s * DOT / 2; n["cy"] = n["by"] + s * DOT / 2
        # Edge polylines in art px, from badge centre to badge centre.
        self.edges = []
        for a, b, arc in tree["edges"]:
            pts = [(self.ax(x), self.ay(y)) for x, y in edge_points(tree, self.nodes[a], self.nodes[b], arc)]
            na, nb = self.nodes[a], self.nodes[b]
            pts[0] = (na["cx"] / DOT, na["cy"] / DOT); pts[-1] = (nb["cx"] / DOT, nb["cy"] / DOT)
            self.edges.append((a, b, arc, pts))

    def ax(self, x): return (x - self.x0) * SCALE / DOT
    def ay(self, y): return (y - self.y0) * SCALE / DOT

    def sky(self):
        """The nebula on the art grid: class-coloured clouds, ordered-dithered into flat steps, and single-pixel stars."""
        X, Y = np.meshgrid(np.arange(self.aw, dtype=np.float32) + .5, np.arange(self.ah, dtype=np.float32) + .5)
        img = np.empty((self.ah, self.aw, 3), np.float32); img[:] = SKY
        for _, _, deg, c in CLASSES:
            cx = self.ax(math.cos(math.radians(deg)) * 1500); cy = self.ay(math.sin(math.radians(deg)) * 1500)
            a = .15 * np.clip(1 - np.hypot(X - cx, Y - cy) / (1900 * SCALE / DOT), 0, 1)
            img += a[..., None] * np.array(hexrgb(c), np.float32)
        for n in self.nodes:
            if n["kind"] != "START":
                continue
            r = FRAME_SIZE["START"] * .9
            a = .18 * np.clip(1 - np.hypot(X - n["cx"] / DOT, Y - n["cy"] / DOT) / r, 0, 1)[..., None]
            img += (np.array(INK, np.float32) - img) * a
        step = 5.0
        bayer = np.tile(BAYER, (self.ah // 4 + 1, self.aw // 4 + 1))[:self.ah, :self.aw, None]
        img = np.floor(img / step + bayer) * step
        rnd = lcg(7)
        for _ in range(900):
            x = int(rnd() * self.aw); y = int(rnd() * self.ah); a = .2 + rnd() * .45
            img[y, x] += (np.array((230, 235, 255), np.float32) - img[y, x]) * a
        return img

    def lines(self, img, width, alpha, colour=None):
        """Every edge as an aliased polyline on the art grid, blended at [alpha]."""
        layer = Image.new("RGB", (self.aw, self.ah)); mask = Image.new("L", (self.aw, self.ah))
        dl, dm = ImageDraw.Draw(layer), ImageDraw.Draw(mask)
        for a, b, arc, pts in self.edges:
            polyline(dl, pts, width, colour or self.nodes[a]["rgb"]); polyline(dm, pts, width, 255)
        m = np.asarray(mask, np.float32)[..., None] / 255 * alpha
        return img * (1 - m) + np.asarray(layer, np.float32) * m

    def labels(self, pil):
        """Names under notables, keystones and starts: dot words with a dark ring, region-tinted."""
        for n in self.nodes:
            if n["kind"] not in ("NOTABLE", "KEYSTONE", "START"):
                continue
            label = n["name"].replace("の起点", "") if n["kind"] == "START" else n["name"]
            fill = INK if n["kind"] == "START" else tuple(min(255, v + 40) for v in n["rgb"])
            im = word(label, fill, 3 if n["kind"] == "START" else PLATE, outline=OUTLINE)
            x = round(n["cx"] - im.width / 2); y = round(n["cy"] + FRAME_SIZE[n["kind"]] * DOT / 2 + 2)
            pil.alpha_composite(im.crop((max(0, -x), max(0, -y), im.width, im.height)), (max(0, x), max(0, y)))

    def bake(self):
        """The unlit tree (badges and names on it) and its gold-lit twin, both at design px."""
        sky = self.sky()
        dim = self.lines(sky, 1, .38)
        lit = self.lines(self.lines(dim, 3, .30, GOLD), 1, 1.0, GOLD)
        out = []
        for art, badges in ((dim, True), (lit, False)):
            pil = up(Image.fromarray(np.clip(art + .5, 0, 255).astype(np.uint8), "RGB")).convert("RGBA")
            if badges:
                cache = {}
                for n in self.nodes:
                    key = (n["kind"], n["icon"], n["rgb"])
                    if key not in cache:
                        # Every start is drawn as another class's start; the player's own one is always lit on top.
                        cache[key] = badge(n["kind"], n["icon"], n["rgb"], False, self.iconset, foreign_start=n["kind"] == "START")
                    pil.alpha_composite(cache[key], (n["bx"], n["by"]))
            self.labels(pil)
            out.append(pil.convert("RGB"))
        return out


def lcg(seed):
    s = [seed]

    def rnd():
        s[0] = (s[0] * 16807) % 2147483647
        return s[0] / 2147483647
    return rnd


def ring_sprite(kind, rgb, width=1, halo=None):
    """A state ring around a badge on the art grid, two art px clear of its frame (odd sized, so it centres)."""
    s = FRAME_SIZE[kind]; gap = 2; extra = 2 if halo else 0
    size = s + 2 * (gap + width + extra)
    img = Image.new("RGBA", (size, size)); d = ImageDraw.Draw(img)
    if halo:
        d.ellipse([0, 0, size - 1, size - 1], outline=halo + (255,), width=1)
    o = extra
    d.ellipse([o, o, size - 1 - o, size - 1 - o], outline=rgb + (255,), width=width)
    return up(img)


# ------------------------------------------------------------------ chrome (the 手帳's windows and plates)

def nine(tile, cut, w, h, s=PLATE):
    """[tile] at [s] design px a pixel, stretched to w x h design px with [cut] tile px kept at each edge."""
    src = tile.resize((tile.width * s, tile.height * s), Image.NEAREST)
    c = cut * s
    out = Image.new("RGBA", (w, h))
    xs = [(0, c, 0, c), (c, src.width - c, c, w - c), (src.width - c, src.width, w - c, w)]
    ys = [(0, c, 0, c), (c, src.height - c, c, h - c), (src.height - c, src.height, h - c, h)]
    for sx0, sx1, dx0, dx1 in xs:
        for sy0, sy1, dy0, dy1 in ys:
            if dx1 > dx0 and dy1 > dy0:
                out.alpha_composite(src.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


OFF_BTN = button_tile((48, 48, 54, 255), (30, 30, 34, 255), (16, 16, 18, 255), (38, 38, 43, 255))
RED_BTN = button_tile((150, 84, 74, 255), (88, 40, 36, 255), (40, 18, 16, 255), (120, 60, 52, 255))


def centred(img, im, dy=0):
    img.alpha_composite(im, ((img.width - im.width) // 2, (img.height - im.height) // 2 + dy))
    return img


def brand():
    """The header emblem: a 13-dot compass in the class colours, at the tree's dot size, in a sunken slot."""
    img = nine(slot_tile(), 4, 52, 52)
    px = Image.new("RGBA", (13, 13)); d = ImageDraw.Draw(px)
    d.rectangle([6, 0, 6, 12], fill=(107, 112, 120, 255)); d.rectangle([0, 6, 12, 6], fill=(107, 112, 120, 255))
    d.rectangle([5, 5, 7, 7], fill=GOLD + (255,))
    for (x, y), c in (((1, 1), "#8FB4F0"), ((10, 2), "#E0604A"), ((2, 10), "#7CD69B"), ((10, 10), "#B58CFF")):
        d.rectangle([x, y, x + 1, y + 1], fill=hexrgb(c) + (255,))
    return centred(img, up(px))


def keystone_slot(on):
    img = nine(slot_tile(on), 4, 38, 38)
    px = Image.new("RGBA", (13, 13)); d = ImageDraw.Draw(px)
    d.polygon([(6, 0), (12, 6), (6, 12), (0, 6)], fill=(90, 66, 20, 255) if on else (24, 25, 28, 255), outline=GOLD + (255,) if on else (74, 70, 64, 255))
    return centred(img, px.resize((26, 26), Image.NEAREST))


def zoom_button(sign):
    img = nine(DARK_BTN, 5, 44, 44); d = ImageDraw.Draw(img)
    d.rectangle([14, 20, 29, 23], fill=INK + (255,))
    if sign == "+":
        d.rectangle([20, 14, 23, 29], fill=INK + (255,))
    return img


def strip(parts, gap):
    """Lay images out left to right, vertically centred."""
    h = max(p.height for p in parts); w = sum(p.width for p in parts) + gap * (len(parts) - 1)
    img = Image.new("RGBA", (w, h)); x = 0
    for p in parts:
        img.alpha_composite(p, (x, (h - p.height) // 2)); x += p.width + gap
    return img


def footer():
    keys = [("左クリック", "選ぶ"), ("端", "移動"), ("ホイール", "拡大縮小"), ("Shift", "閉じる")]
    parts = []
    for k, t in keys:
        cap = word(k, MUTE)
        plate = centred(nine(DARK_BTN, 5, cap.width + 16, 34), cap)
        parts.append(strip([plate, word(t, MUTE)], 8))
    return strip(parts, 26)


def legend():
    parts = []
    for _, name, _, c in CLASSES:
        sw = Image.new("RGBA", (10, 10), BLACK); ImageDraw.Draw(sw).rectangle([2, 2, 7, 7], fill=hexrgb(c) + (255,))
        parts.append(strip([sw, word(name, MUTE)], 6))
    return strip(parts, 18)


def chrome():
    """Every fixed plate the flow lays out: name -> image."""
    out = {"panel_left": nine(window_tile(fill=(17, 17, 19, 255)), 8, LEFT_W, PANEL_H),
           "panel_right": nine(window_tile(fill=(17, 17, 19, 255)), 8, RIGHT_W, PANEL_H),
           "chip": nine(slot_tile(), 4, CHIP_W, CHIP_H),
           "go_take": nine(GOLD_BTN, 5, GO_W, GO_H), "go_refund": nine(RED_BTN, 5, GO_W, GO_H), "go_off": nine(OFF_BTN, 5, GO_W, GO_H),
           "btn": nine(DARK_BTN, 5, BTN_W, BTN_H), "btn_gold": nine(GOLD_BTN, 5, BTN_W, BTN_H), "btn_off": nine(OFF_BTN, 5, BTN_W, BTN_H),
           "brand_slot": brand(), "key_off": keystone_slot(False), "key_on": keystone_slot(True),
           "zoom_in": zoom_button("+"), "zoom_out": zoom_button("-"), "footer": footer(), "legend": legend()}
    for h in CHOICE_H:
        out[f"choice_{h}"] = nine(slot_tile(), 4, GO_W, h)
        out[f"choice_on_{h}"] = nine(slot_tile(True), 4, GO_W, h)
    return out


def main(preview: bool) -> None:
    tree = json.loads(SOURCE.read_text(encoding="utf-8"))
    iconset = json.loads(ICONS.read_text(encoding="utf-8"))
    bake = TreeBake(tree, iconset)
    nodes = bake.nodes
    files: dict[str, bytes] = {}
    providers: list[dict] = []
    sprites: dict[str, dict] = {}
    code = [FIRST_CHAR]

    def add(name: str, image: Image.Image) -> str:
        assert image.width <= 256 and image.height <= 256, (name, image.size)
        assert code[0] <= LAST_CHAR, "passive tree glyphs ran past the private use area"
        char = chr(code[0]); code[0] += 1
        # Minecraft resource paths take [a-z0-9/._-] only: one bad name voids the whole font (2026-11-02, in game).
        rel = f"passive_tree/{name.lower()}.png"
        assert re.fullmatch(r"[a-z0-9/._-]+", rel), rel
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height, "height": image.height, "chars": [char]})
        sprites[name] = {"char": char, "font": FONT, "width": image.width, "height": image.height}
        return name

    def pieces(prefix, img, left=0, top=0, crop=True):
        """Glyphs over 256 px do not render: big images are cut into pieces (empty ones dropped)."""
        parts = []
        for py in range(0, img.height, TILE):
            for px in range(0, img.width, TILE):
                piece = img.crop((px, py, min(px + TILE, img.width), min(py + TILE, img.height)))
                box = piece.getbbox() if crop else (0, 0, piece.width, piece.height)
                if box is None:
                    continue
                parts.append({"sprite": add(f"{prefix}_{px}_{py}", piece.crop(box)), "x": left + px + box[0], "y": top + py + box[1]})
        return parts

    unlit, lit = bake.bake()
    tiles = [{"name": p["sprite"], "x": p["x"], "y": p["y"]} for p in pieces("tile", unlit.convert("RGBA"), crop=False)]
    print(f"  tiles {len(tiles)}", flush=True)
    lit = np.asarray(lit)

    edges, paths = [], []
    for a, b, arc, pts in bake.edges:
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        left, top = math.floor(min(xs)) - 3, math.floor(min(ys)) - 3
        w, h = math.ceil(max(xs)) + 3 - left, math.ceil(max(ys)) + 3 - top
        local = [(x - left, y - top) for x, y in pts]
        # Gold: the lit bake under this edge's three-pixel band, fully opaque.
        band = Image.new("L", (w, h)); polyline(ImageDraw.Draw(band), local, 3, 255)
        band = np.asarray(up(band)) > 0
        L, T = left * DOT, top * DOT
        crop = np.zeros((h * DOT, w * DOT, 3), np.uint8)
        cl, ct = max(0, L), max(0, T)
        src = lit[ct:min(bake.h, T + h * DOT), cl:min(bake.w, L + w * DOT)]
        crop[ct - T:ct - T + src.shape[0], cl - L:cl - L + src.shape[1]] = src
        gold = Image.fromarray(np.dstack([crop, np.where(band, 255, 0).astype(np.uint8)]), "RGBA")
        edges.append({"a": a, "b": b, "parts": pieces(f"edge_{a}_{b}", gold, L, T)})
        # The planned path: one-pixel dashes, three on and two off.
        dash = Image.new("RGBA", (w, h)); dd = ImageDraw.Draw(dash)
        for seg in dashes(local, 3, 2):
            polyline(dd, seg, 1, EMBER_SOFT + (255,))
        paths.append({"a": a, "b": b, "parts": pieces(f"path_{a}_{b}", up(dash), L, T)})

    # Lit badges: one per (kind, icon); every taken node of that kind and icon shares it.
    for kind, icon in sorted({(n["kind"], n["icon"]) for n in nodes}):
        add(f"lit_{kind}_{icon}", badge(kind, icon, INK, True, iconset))
    for kind in KINDS:
        add(f"ring_sel_{kind}", ring_sprite(kind, EMBER, 1, halo=(120, 64, 30)))
        add(f"ring_hover_{kind}", ring_sprite(kind, INK))
        add(f"ring_reach_{kind}", ring_sprite(kind, OK))
        add(f"ring_path_{kind}", ring_sprite(kind, EMBER_SOFT))

    plates = {name: pieces(name, img, crop=False) for name, img in chrome().items()}

    metrics = json.loads(METRICS.read_text(encoding="utf-8"))
    chars = text_chars()
    dot_font(files, metrics, "ptdot", chars, True)
    dot_font(files, metrics, "ptdotn", chars, False)
    METRICS.write_text(json.dumps(metrics, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")

    files[f"assets/{NS}/font/passive_tree.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()
    with zipfile.ZipFile(OUT / "passive-tree.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    layout = {"scale": SCALE, "dot": DOT, "originX": bake.x0, "originY": bake.y0, "width": bake.w, "height": bake.h,
              "tiles": tiles, "edges": edges, "paths": paths, "sprites": sprites, "plates": plates,
              "nodes": [{"id": n["id"], "x": n["cx"], "y": n["cy"], "kind": n["kind"], "icon": n["icon"], "text": n["text"],
                         "choiceTexts": n.get("choiceTexts", [])} for n in nodes]}
    (OUT / "passive-tree.json").write_text(json.dumps(layout, ensure_ascii=False), encoding="utf-8")
    print(f"PASSIVE_TREE_PACK_BUILT tiles={len(tiles)} sprites={len(sprites)} chars={len(chars)} last=U+{code[0] - 1:04X} "
          f"bytes={(OUT / 'passive-tree.zip').stat().st_size}")
    if preview:
        render_preview()


def render_preview():
    """The flow's own scene (written by PassiveTreeFlowTest) drawn the way the client draws it."""
    scene = ROOT / "server-minestom/build/passive-tree-preview/scene.json"
    subprocess.run([str(Path.home() / "ProjectS/bin/heavy"), "./gradlew", ":server-minestom:test", "--offline", "--console=plain",
                    "--tests", "dev.projects.server.coreloop.PassiveTreeFlowTest"], cwd=ROOT, check=True)
    out = ROOT / "docs/proposals/skill-tree-2026-10/passive-tree-screen.png"
    for src, dst in ((scene, out), (scene.with_name("scene-mastery.json"), out.with_name("passive-tree-screen-mastery.png"))):
        if not src.exists():
            continue
        subprocess.run([sys.executable, str(ROOT / "scripts/render_grimoire_preview.py"), str(ROOT), str(src), str(dst), "2.4"], check=True)
        # The scene is 800x480 around a 16:9 screen: keep only the 1920x1080 game area.
        shot = Image.open(dst); top = round((480 - 1080 * 800 / 1920) / 2 * 2.4)
        shot.crop((0, top, 1920, top + 1080)).save(dst)
        print(f"PASSIVE_TREE_PREVIEW {dst}")


if __name__ == "__main__":
    if sys.argv[1:] == ["preview-only"]:
        render_preview()
    else:
        main(preview=sys.argv[1:] == ["preview"])
