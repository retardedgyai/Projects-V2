"""Bake the passive tree screen (the camera + display-entity UI, like the 開拓大陸 map).

    python3 scripts/build_passive_tree_assets.py [preview | preview-only]

Reads assets/ui/passive-tree/tree.json and icons.json and writes
server-minestom/src/main/resources/polish05/passive-tree.zip (private-namespace sprites, merged into the
UI pack by CoreUiPackServer) and polish05/passive-tree.json (sprite chars and where each one sits).

The look follows docs/proposals/skill-tree-2026-10/screen-prototype.template.html, with one fixed rule:
  - pixel art: the things themselves (node badges and their icons, the class emblem), all at ONE dot size (DOT);
  - smooth: everything that explains or connects (nebula, lines, state rings, names, panels, buttons).
The tree (nebula, dim region-coloured lines, unallocated badges with grey icons, names) is baked into opaque
256 px tiles at SCALE design px per tree unit (zoom 1 in game = the prototype's default view). What changes per
player is drawn on top: gold lines per edge (cut out of a gold-lit bake, so they stay anti-aliased while
fully opaque), dashed orange lines for the planned path, one lit badge per (kind, icon), and smooth rings.
Glyphs keep hard alpha (the client draws every texel above 0.1 alpha opaque), so words on the panels are
blended against the colour they sit on before the faint edge is cut.

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
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/passive-tree/tree.json"
ICONS = ROOT / "assets/ui/passive-tree/icons.json"
OUT = ROOT / "server-minestom/src/main/resources/polish05"
# The forge v3 faces (the same files the approved HTML and the forge glyph pages use).
MPLUS = ROOT / "web-ui-lab/ui/forge-v3-fonts/MPLUS2.ttf"
NS = "projects_ui_polish05"
FONT = f"{NS}:passive_tree"
FIRST_CHAR = 0xE000
LAST_CHAR = 0xF8FF  # the BMP private use area; this font is ours alone, so the whole range is free
TILE = 256
DOT = 3                 # one art pixel = DOT design px at zoom 1
SCALE = 0.207 * DOT     # design px per tree unit at zoom 1 (the prototype's ZOOM_PER_DOT * dot)
PAD = 160               # tree units of margin around the outermost node
SS = 4                  # supersampling for smooth lines and rings

# ---- palette (prototype :root and canvas colours)
INK = (236, 230, 220)
MUTE = (162, 158, 151)
DIM = (111, 107, 101)
EMBER = (240, 122, 42)
EMBER_SOFT = (255, 196, 138)
GOLD = (232, 200, 120)
DANGER = (229, 139, 128)
OK = (154, 240, 160)
SKY = (16, 17, 22)        # canvas fill #101116
STAGE = (18, 19, 22)      # stage #121316 (the fades and the footer sit on it)
PANEL = (16, 17, 20)      # rgba(16,17,20,.95) over the dark tree
LINE = (37, 38, 41)       # rgba(255,255,255,.09) over the panel
SLOT = (13, 14, 16)
CHOICE = (23, 24, 27)     # rgba(255,255,255,.03) over the panel
OUTLINE = (10, 10, 14)
CLASSES = [("WARRIOR", "戦士", -90, "#E0604A"), ("TEMPLAR", "テンプラー", -30, "#E8C35A"), ("HEALER", "ヒーラー", 30, "#7CD69B"),
           ("MAGE", "メイジ", 90, "#7FA6FF"), ("ASSASSIN", "アサシン", 150, "#B58CFF"), ("RANGER", "レンジャー", 210, "#5FC7C0")]
KINDS = ["SMALL", "NOTABLE", "KEYSTONE", "START", "MASTERY"]
FRAME_SIZE = {"SMALL": 13, "NOTABLE": 19, "KEYSTONE": 29, "START": 23, "MASTERY": 13}
STAT = re.compile(r"^(.*)\+(\d+)%$")


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


_fonts = {}


def mplus(size, weight=400):
    key = ("m", size, weight)
    if key not in _fonts:
        f = ImageFont.truetype(str(MPLUS), size)
        f.set_variation_by_axes([weight])
        _fonts[key] = f
    return _fonts[key]


NOTO = {400: "Regular", 500: "Medium", 700: "Bold"}
_notdef = {}


def noto(size, weight=400):
    key = ("n", size, weight)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(f"/usr/share/fonts/opentype/noto/NotoSansCJK-{NOTO[weight]}.ttc", size, index=0)  # index 0: JP
    return _fonts[key]


def covers(f, ch):
    """The forge's MPLUS2.ttf is a subset: a character it lacks draws the .notdef box."""
    if ch.isspace():
        return True
    if f not in _notdef:
        _notdef[f] = _glyph(f, "\uffff")
    return _glyph(f, ch) != _notdef[f]


def _glyph(f, ch):
    im = Image.new("L", (f.size * 2, f.size * 2)); ImageDraw.Draw(im).text((0, 0), ch, font=f, fill=255)
    return im.tobytes()


def runs(text, size, weight=400):
    """Split [text] into (piece, font) runs: M PLUS 2 where it has the glyph, Noto Sans CJK JP otherwise."""
    out = []
    for ch in text:
        f = mplus(size, weight) if covers(mplus(size, weight), ch) else noto(size, weight)
        if out and out[-1][1] is f:
            out[-1] = (out[-1][0] + ch, f)
        else:
            out.append((ch, f))
    return out


def text_width(text, size, weight=400):
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    return sum(probe.textlength(piece, font=f) for piece, f in runs(text, size, weight))


def draw_text(draw, x, y, text, size, weight=400, centre=False, **kw):
    """Draw at baseline y (left or centre aligned), switching fonts per run."""
    if centre:
        x -= text_width(text, size, weight) / 2
    for piece, f in runs(text, size, weight):
        draw.text((x, y), piece, font=f, anchor="ls", **kw)
        x += draw.textlength(piece, font=f)



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


class Layer:
    """Supersampled colour + coverage for smooth strokes, composited over an RGB float image."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.color = Image.new("RGB", (w * SS, h * SS)); self.mask = Image.new("L", (w * SS, h * SS))
        self.dc = ImageDraw.Draw(self.color); self.dm = ImageDraw.Draw(self.mask)

    def line(self, pts, width, rgb):
        p = [(x * SS, y * SS) for x, y in pts]
        stroke(self.dc, p, width * SS, rgb); stroke(self.dm, p, width * SS, 255)

    def over(self, img, alpha=1.0):
        # The colour image is black where nothing was drawn, so its average is already premultiplied.
        c = np.asarray(self.color.resize((self.w, self.h), Image.BOX), np.float32)
        m = np.asarray(self.mask.resize((self.w, self.h), Image.BOX), np.float32)[..., None] / 255
        img *= 1 - m * alpha
        img += c * alpha
        return img


# ------------------------------------------------------------------ the tree bake

class TreeBake:
    def __init__(self, tree, iconset):
        self.tree = tree; self.iconset = iconset
        self.nodes = tree["nodes"]
        self.x0, self.y0, x1, y1 = bounds(self.nodes)
        self.w, self.h = math.ceil((x1 - self.x0) * SCALE), math.ceil((y1 - self.y0) * SCALE)
        for n in self.nodes:
            n["rgb"] = region(n)
            n["icon"] = icon_of(n, iconset)
            size = FRAME_SIZE[n["kind"]] * DOT
            # Badges sit on whole design pixels; the spot is the badge centre so lit badges land exactly on it.
            n["bx"] = round(self.px(n["x"]) - size / 2); n["by"] = round(self.py(n["y"]) - size / 2)
            n["cx"] = n["bx"] + size / 2; n["cy"] = n["by"] + size / 2
        self.edges = [(a, b, arc, [self.to(p) for p in edge_points(tree, self.nodes[a], self.nodes[b], arc)])
                      for a, b, arc in tree["edges"]]
        self.badges = {}
        rnd = self.lcg(7)
        self.stars = []
        for _ in range(500):
            x = rnd() * 7000 - 3500; y = rnd() * 7000 - 3500; a = .15 + rnd() * .35; r = .6 + rnd() * 1.1
            self.stars.append((self.px(x), self.py(y), a, r))

    @staticmethod
    def lcg(seed):
        s = [seed]

        def rnd():
            s[0] = (s[0] * 16807) % 2147483647
            return s[0] / 2147483647
        return rnd

    def px(self, x): return (x - self.x0) * SCALE
    def py(self, y): return (y - self.y0) * SCALE
    def to(self, p): return self.px(p[0]), self.py(p[1])

    def badge_of(self, n):
        key = (n["kind"], n["icon"], n["rgb"])
        if key not in self.badges:
            # Every start is drawn as another class's start; the player's own one is always lit on top.
            self.badges[key] = badge(n["kind"], n["icon"], n["rgb"], False, self.iconset, foreign_start=n["kind"] == "START")
        return self.badges[key]

    def nebula(self, left, top, w, h):
        xs = np.arange(w, dtype=np.float32) + left + .5; ys = np.arange(h, dtype=np.float32) + top + .5
        X, Y = np.meshgrid(xs, ys)
        img = np.empty((h, w, 3), np.float32); img[:] = SKY
        for _, _, deg, c in CLASSES:
            cx = self.px(math.cos(math.radians(deg)) * 1500); cy = self.py(math.sin(math.radians(deg)) * 1500)
            a = .15 * np.clip(1 - np.hypot(X - cx, Y - cy) / (1900 * SCALE), 0, 1)
            img += a[..., None] * np.array(hexrgb(c), np.float32)
        star = np.array((230, 235, 255), np.float32)
        for sx, sy, a, r in self.stars:
            if sx < left - 3 or sy < top - 3 or sx > left + w + 3 or sy > top + h + 3:
                continue
            for yy in range(math.floor(sy - r - 1), math.ceil(sy + r + 1)):
                for xx in range(math.floor(sx - r - 1), math.ceil(sx + r + 1)):
                    if not (left <= xx < left + w and top <= yy < top + h):
                        continue
                    cov = min(1, max(0, r + .5 - math.hypot(xx + .5 - sx, yy + .5 - sy))) * a
                    if cov > 0:
                        p = img[yy - top, xx - left]; p += (star - p) * cov
        return np.minimum(img, 255)

    def start_glow(self, img, left, top):
        h, w = img.shape[:2]
        for n in self.nodes:
            if n["kind"] != "START":
                continue
            r = FRAME_SIZE["START"] * DOT * .9
            if n["cx"] + r < left or n["cy"] + r < top or n["cx"] - r > left + w or n["cy"] - r > top + h:
                continue
            xs = np.arange(w, dtype=np.float32) + left + .5; ys = np.arange(h, dtype=np.float32) + top + .5
            X, Y = np.meshgrid(xs, ys)
            a = .18 * np.clip(1 - np.hypot(X - n["cx"], Y - n["cy"]) / r, 0, 1)[..., None]
            img += (np.array(INK, np.float32) - img) * a

    def touching(self, left, top, w, h, margin):
        for a, b, arc, pts in self.edges:
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            if max(xs) + margin < left or max(ys) + margin < top or min(xs) - margin > left + w or min(ys) - margin > top + h:
                continue
            yield a, b, arc, pts

    def labels(self, img, left, top):
        """Names under notables, keystones and starts: M PLUS 2 with a dark outline, region-tinted."""
        h, w = img.shape[:2]
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
        for n in self.nodes:
            if n["kind"] not in ("NOTABLE", "KEYSTONE", "START"):
                continue
            r = FRAME_SIZE[n["kind"]] * DOT / 2
            x, y = n["cx"] - left, n["cy"] + r + 18 - top
            if x < -260 or x > w + 260 or y < -40 or y > h + 40:
                continue
            label = n["name"].replace("の起点", "") if n["kind"] == "START" else n["name"]
            size, weight = (22, 700) if n["kind"] == "START" else (17 if n["kind"] == "KEYSTONE" else 15, 500)
            fill = INK if n["kind"] == "START" else tuple(min(255, v + 40) for v in n["rgb"])
            draw_text(d, x, y, label, size, weight, centre=True, fill=fill + (255,), stroke_width=2, stroke_fill=OUTLINE + (230,))
        a = np.asarray(layer, np.float32)
        al = a[..., 3:] / 255
        img *= 1 - al; img += a[..., :3] * al

    def tile(self, left, top, w, h, lit):
        img = self.nebula(left, top, w, h)
        dim = Layer(w, h)
        for a, b, arc, pts in self.touching(left, top, w, h, 8):
            dim.line([(x - left, y - top) for x, y in pts], .9 * DOT, self.nodes[a]["rgb"])
        dim.over(img, .26)
        if lit:
            glow, core = Layer(w, h), Layer(w, h)
            for a, b, arc, pts in self.touching(left, top, w, h, 12):
                p = [(x - left, y - top) for x, y in pts]
                glow.line(p, 3.4 * DOT, GOLD); core.line(p, 1.3 * DOT, GOLD)
            glow.over(img, .28); core.over(img)
        self.start_glow(img, left, top)
        if not lit:
            pil = Image.fromarray(np.clip(img + .5, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
            for n in self.nodes:
                sp = self.badge_of(n)
                if n["bx"] + sp.width < left or n["by"] + sp.height < top or n["bx"] > left + w or n["by"] > top + h:
                    continue
                # alpha_composite needs a non-negative destination: crop what hangs over the tile's top-left.
                cut = sp.crop((max(0, left - n["bx"]), max(0, top - n["by"]), sp.width, sp.height))
                pil.alpha_composite(cut, (max(0, n["bx"] - left), max(0, n["by"] - top)))
            img = np.asarray(pil.convert("RGB"), np.float32).copy()
        self.labels(img, left, top)
        return np.clip(img + .5, 0, 255).astype(np.uint8)


# ------------------------------------------------------------------ UI pieces

def ink_on(text, size, weight, fg, bg, cut=.28, pad=2):
    """Text blended onto [bg] and cut at [cut] coverage: opaque glyph pixels with smooth-looking edges."""
    f = mplus(size, weight)
    asc, desc = f.getmetrics()
    w = max(1, math.ceil(text_width(text, size, weight)) + pad * 2); h = asc + desc + pad * 2
    m = Image.new("L", (w, h)); draw_text(ImageDraw.Draw(m), pad, pad + asc, text, size, weight, fill=255)
    return blend_mask(m, fg, bg, cut)



def blend_mask(mask, fg, bg, cut=.28):
    a = np.asarray(mask, np.float32)[..., None] / 255
    rgb = np.array(fg, np.float32) * a + np.array(bg, np.float32) * (1 - a)
    out = np.concatenate([rgb, np.where(a >= cut, 255, 0)], axis=2)
    return Image.fromarray(np.clip(out + .5, 0, 255).astype(np.uint8), "RGBA")


def supersampled(w, h, paint):
    """Paint at SS x into a white-on-black mask and return its coverage (0..1) at 1 x."""
    m = Image.new("L", (w * SS, h * SS)); paint(ImageDraw.Draw(m), SS)
    return np.asarray(m.resize((w, h), Image.BOX), np.float32) / 255


def ring_sprite(kind, rgb, width, halo=None):
    """A smooth state ring around a badge (the prototype's r = frame/2 + 1.6 dots), cut hard."""
    r = FRAME_SIZE[kind] * DOT / 2 + 1.6 * DOT
    outer = r + (7 if halo else 0) + width
    size = math.ceil(2 * outer + 4); c = size / 2
    img = np.zeros((size, size, 4), np.uint8)

    def put(rad, wd, col):
        lo, hi = rad - wd / 2, rad + wd / 2
        a = supersampled(size, size, lambda d, k: (d.ellipse([(c - hi) * k, (c - hi) * k, (c + hi) * k, (c + hi) * k], fill=255),
                                                   d.ellipse([(c - lo) * k, (c - lo) * k, (c + lo) * k, (c + lo) * k], fill=0)))
        img[a >= .45] = (*col, 255)
    if halo:
        put(r + 5, 4, halo)
    put(r, width, rgb)
    return Image.fromarray(img, "RGBA")


def rounded(w, h, r, fill, edge=None, back=None, gradient=None, highlight=False):
    """A rounded rectangle; its corners are blended onto [back] (fully opaque) or cut hard (back=None)."""
    k = SS
    a = supersampled(w, h, lambda d, s: d.rounded_rectangle([0, 0, w * s - 1, h * s - 1], r * s, fill=255))[..., None]
    body = np.empty((h, w, 3), np.float32)
    if gradient:
        t = np.linspace(0, 1, h, dtype=np.float32)[:, None, None]
        body[:] = np.array(gradient[0], np.float32) * (1 - t) + np.array(gradient[1], np.float32) * t
    else:
        body[:] = fill
    if highlight:
        body[1:3] = body[1:3] * .65 + np.array((255, 255, 255), np.float32) * .35
    if edge:
        ia = supersampled(w, h, lambda d, s: d.rounded_rectangle([s, s, w * s - 1 - s, h * s - 1 - s], (r - 1) * s, fill=255))[..., None]
        body = body * ia + np.array(edge, np.float32) * (1 - ia)
    if back is not None:
        rgb = body * a + np.array(back, np.float32) * (1 - a); alpha = np.full((h, w, 1), 255, np.float32)
    else:
        rgb = body; alpha = np.where(a >= .5, 255, 0)
    return Image.fromarray(np.clip(np.concatenate([rgb, alpha], 2) + .5, 0, 255).astype(np.uint8), "RGBA")


def slot(w, h):
    """The forge's inset slot: dark top-left band, light bottom-right band."""
    img = Image.new("RGBA", (w, h), SLOT + (255,)); d = ImageDraw.Draw(img)
    d.rectangle([w - 3, 0, w - 1, h - 1], fill=(44, 46, 52, 255)); d.rectangle([0, h - 3, w - 1, h - 1], fill=(44, 46, 52, 255))
    d.rectangle([0, 0, 2, h - 1], fill=(5, 5, 6, 255)); d.rectangle([0, 0, w - 1, 2], fill=(5, 5, 6, 255))
    return img


def brand():
    """The header emblem: a 13-dot compass in the class colours, at the tree's dot size."""
    s = 52; img = slot(s, s)
    px = Image.new("RGBA", (13, 13), (0, 0, 0, 0)); d = ImageDraw.Draw(px)
    d.rectangle([6, 0, 6, 12], fill=(107, 112, 120, 255)); d.rectangle([0, 6, 12, 6], fill=(107, 112, 120, 255))
    d.rectangle([5, 5, 7, 7], fill=GOLD + (255,))
    for (x, y), c in (((1, 1), "#8FB4F0"), ((10, 2), "#E0604A"), ((2, 10), "#7CD69B"), ((10, 10), "#B58CFF")):
        d.rectangle([x, y, x + 1, y + 1], fill=hexrgb(c) + (255,))
    px = px.resize((13 * DOT, 13 * DOT), Image.NEAREST)
    img.alpha_composite(px, ((s - px.width) // 2, (s - px.height) // 2))
    return img


def keystone_slot(on):
    s = 38; img = np.asarray(slot(s, s), np.float32).copy()
    c = s / 2; r = 11 * math.sqrt(2)

    def diamond(rr, k):
        return [(c * k, (c - rr) * k), ((c + rr) * k, c * k), (c * k, (c + rr) * k), ((c - rr) * k, c * k)]
    outer = supersampled(s, s, lambda d, k: d.polygon(diamond(r + 1.4, k), fill=255))[..., None]
    inner = supersampled(s, s, lambda d, k: d.polygon(diamond(r - 1.4, k), fill=255))[..., None]
    border = GOLD if on else (74, 70, 64); fill = (90, 66, 20) if on else (24, 25, 28)
    rgb = img[..., :3]
    rgb[:] = rgb * (1 - outer) + np.array(border, np.float32) * outer
    rgb[:] = rgb * (1 - inner) + np.array(fill, np.float32) * inner
    return Image.fromarray(np.clip(img + .5, 0, 255).astype(np.uint8), "RGBA")


def keycap(text):
    """A footer key: dark cap with a thicker bottom edge."""
    t = ink_on(text, 14, 700, INK, (30, 32, 37), pad=0)
    w = max(28, t.width + 14); h = 26
    img = rounded(w, h, 5, (30, 32, 37), edge=(58, 60, 66), back=STAGE)
    ImageDraw.Draw(img).rectangle([1, h - 3, w - 2, h - 2], fill=(58, 60, 66, 255))
    img.alpha_composite(t, ((w - t.width) // 2, (h - 3 - t.height) // 2))
    return img


def strip(parts, gap):
    """Lay images out left to right, vertically centred."""
    h = max(p.height for p in parts); w = sum(p.width for p in parts) + gap * (len(parts) - 1)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); x = 0
    for p in parts:
        img.alpha_composite(p, (x, (h - p.height) // 2)); x += p.width + gap
    return img


def footer():
    keys = [("左クリック", "選ぶ・2回で取る"), ("端", "移動"), ("ホイール", "拡大縮小"), ("Shift", "閉じる")]
    return strip([strip([keycap(k), ink_on(t, 15, 700, MUTE, STAGE)], 6) for k, t in keys], 22)


def legend():
    parts = []
    disc = np.where(supersampled(10, 10, lambda d, k: d.ellipse([0, 0, 10 * k - 1, 10 * k - 1], fill=255)) >= .5, 255, 0).astype(np.uint8)
    for _, name, _, c in CLASSES:
        dot = Image.fromarray(np.dstack([np.full((10, 10, 3), hexrgb(c), np.uint8), disc]), "RGBA")
        parts.append(strip([dot, ink_on(name, 15, 700, MUTE, (20, 21, 26))], 6))
    return strip(parts, 16)


def zoom_button(sign):
    img = rounded(44, 44, 10, PANEL, edge=LINE)
    d = ImageDraw.Draw(img)
    # Plus and minus as smooth bars (the full-width glyphs are not in the forge subset).
    d.rectangle([14, 21, 29, 22], fill=INK + (255,))
    if sign == "+":
        d.rectangle([21, 14, 22, 29], fill=INK + (255,))
    return img


def wrap(size, weight, text, width):
    lines, line = [], ""
    for ch in text:
        if text_width(line + ch, size, weight) > width and line:
            lines.append(line); line = ""
        line += ch
    return lines + [line] if line else lines


INFO_W = 408
KIND_LABEL = {"SMALL": "小さいノード", "NOTABLE": "主要ノード", "KEYSTONE": "キーストーン", "START": "起点", "MASTERY": "極意（マスタリー）"}


def info_image(kind, name, body, rgb=None):
    """The right panel's head for one node: kind, name, then stat lines or a paragraph, on the panel colour."""
    img = Image.new("RGBA", (INFO_W, 400), (0, 0, 0, 0))

    def put(im, x, y):
        img.alpha_composite(im.crop((0, 0, min(im.width, INFO_W - x), im.height)), (x, y))
    put(ink_on(kind, 15, 700, MUTE, PANEL, pad=0), 0, 0)
    y = 24
    for line in wrap(30, 700, name, INFO_W):
        put(ink_on(line, 30, 700, INK, PANEL, pad=0), 0, y); y += 38
    y += 12
    if isinstance(body, list):
        for part in body:
            m = STAT.match(part)
            head = m[1] if m else part
            lines = wrap(18, 700, head, INFO_W - (70 if m else 0))
            for k, line in enumerate(lines):
                t = ink_on(line, 18, 700, INK, PANEL, pad=0); put(t, 0, y)
                if m and k == len(lines) - 1:
                    # The value in the same face, weight and size as its name, on the same baseline.
                    put(ink_on(f"+{m[2]}%", 18, 700, rgb, PANEL, pad=0), t.width + 6, y)
                y += 30
    else:
        for line in wrap(16, 700, body, INFO_W):
            put(ink_on(line, 16, 700, (201, 195, 184), PANEL, pad=0), 0, y); y += 26
    return img.crop((0, 0, INFO_W, min(256, y + 2)))


def main(preview: bool) -> None:
    tree = json.loads(SOURCE.read_text(encoding="utf-8"))
    iconset = json.loads(ICONS.read_text(encoding="utf-8"))
    bake = TreeBake(tree, iconset)
    nodes = bake.nodes
    files: dict[str, bytes] = {}
    providers: list[dict] = []
    sprites: dict[str, dict] = {}
    code = [FIRST_CHAR]

    def add(name: str, image: Image.Image, words=False) -> str:
        assert image.width <= 256 and image.height <= 256, (name, image.size)
        if words:
            # Words only hold a few blends of one colour into the panel: a palette keeps the pack small.
            image = image.quantize(colors=32, method=Image.Quantize.FASTOCTREE)  # indexed PNG + tRNS
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

    def columns(key, img):
        return [{"sprite": add(f"{key}_{k}", img.crop((k * 256, 0, min(img.width, (k + 1) * 256), img.height)), True), "x": k * 256, "y": 0}
                for k in range(math.ceil(img.width / 256))]

    # Tiles: the unlit tree, and a gold-lit twin that the lit edges are cut out of.
    tiles = []
    lit = np.zeros((bake.h, bake.w, 3), np.uint8)
    for y in range(0, bake.h, TILE):
        for x in range(0, bake.w, TILE):
            w, h = min(TILE, bake.w - x), min(TILE, bake.h - y)
            tiles.append({"name": add(f"tile_{x}_{y}", Image.fromarray(bake.tile(x, y, w, h, False), "RGB").convert("RGBA")), "x": x, "y": y})
            lit[y:y + h, x:x + w] = bake.tile(x, y, w, h, True)
        print(f"  tiles row {y // TILE + 1}/{math.ceil(bake.h / TILE)}", flush=True)

    def pieces(prefix, img, left, top):
        parts = []
        # Glyphs over 256 px do not render: long edges are split into pieces.
        for py in range(0, img.height, TILE):
            for px in range(0, img.width, TILE):
                piece = img.crop((px, py, min(px + TILE, img.width), min(py + TILE, img.height)))
                box = piece.getbbox()
                if box is None:
                    continue
                parts.append({"sprite": add(f"{prefix}_{px}_{py}", piece.crop(box)), "x": left + px + box[0], "y": top + py + box[1]})
        return parts

    edges, paths = [], []
    for a, b, arc, pts in bake.edges:
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        left, top = math.floor(min(xs)) - 8, math.floor(min(ys)) - 8
        right, bottom = math.ceil(max(xs)) + 8, math.ceil(max(ys)) + 8
        w, h = right - left, bottom - top
        local = [(x - left, y - top) for x, y in pts]
        # Gold: the lit bake inside this edge's glow band, fully opaque.
        band = supersampled(w, h, lambda d, k: stroke(d, [(x * k, y * k) for x, y in local], 3.4 * DOT * k, 255))
        cl, ct = max(0, left), max(0, top)
        crop = np.zeros((h, w, 3), np.uint8)
        src = lit[ct:min(bake.h, bottom), cl:min(bake.w, right)]
        crop[ct - top:ct - top + src.shape[0], cl - left:cl - left + src.shape[1]] = src
        gold = Image.fromarray(np.dstack([crop, np.where(band >= .08, 255, 0).astype(np.uint8)]), "RGBA")
        edges.append({"a": a, "b": b, "parts": pieces(f"edge_{a}_{b}", gold, left, top)})
        # The planned path: orange dashes, cut hard.
        dash = supersampled(w, h, lambda d, k: [stroke(d, [(x * k, y * k) for x, y in seg], 1.1 * DOT * k, 255)
                                                for seg in dashes(local, 3 * DOT, 2.5 * DOT)])
        path = Image.fromarray(np.dstack([np.full((h, w, 3), EMBER_SOFT, np.uint8), np.where(dash >= .45, 255, 0).astype(np.uint8)]), "RGBA")
        paths.append({"a": a, "b": b, "parts": pieces(f"path_{a}_{b}", path, left, top)})

    # Lit badges: one per (kind, icon); every taken node of that kind and icon shares it.
    for kind, icon in sorted({(n["kind"], n["icon"]) for n in nodes}):
        add(f"lit_{kind}_{icon}", badge(kind, icon, INK, True, iconset))
    # State rings (smooth, drawn over the badges).
    for kind in KINDS:
        add(f"ring_sel_{kind}", ring_sprite(kind, EMBER, 3, halo=(94, 54, 29)))
        add(f"ring_hover_{kind}", ring_sprite(kind, INK, 2))
        add(f"ring_reach_{kind}", ring_sprite(kind, OK, 2))
        add(f"ring_path_{kind}", ring_sprite(kind, EMBER_SOFT, 2))

    # Right panel heads.
    infos = {}
    for n in nodes:
        body = n["text"].split(" / ") if n["kind"] in ("SMALL", "NOTABLE") else \
            "この輪の主要ノードを取ると、1点で1つ選べる。同じ効果はツリー全体で1回だけ。" if n["kind"] == "MASTERY" else n["text"]
        infos[str(n["id"])] = columns(f"info_{n['id']}", info_image(KIND_LABEL[n["kind"]], n["name"], body, n["rgb"]))
    infos["none"] = columns("info_none", info_image("ノード", "ノードを選んでください",
                                                    "木の上でノードを押すと、ここに中身が出ます。もう一度押すと、そこまでの道をまとめて取ります。"))

    labels = {}

    def label(key, text, size, color, bg, weight=400):
        labels[key] = columns(f"label_{key}", ink_on(text, size, weight, color, bg))
    # Header, panel and footer words (the forge glyph subset lacks many of these kanji).
    label("brand", "星の盤", 22, INK, STAGE, 700)
    label("brand_sub", "開拓港 · 修練所", 15, MUTE, STAGE, 700)
    label("tab_passive", "パッシブ", 19, INK, STAGE, 700)
    label("tab_skills", "技の編成", 19, MUTE, STAGE, 500)
    label("tab_stats", "戦闘能力", 19, MUTE, STAGE, 500)
    label("chip_points", "残りポイント", 15, MUTE, PANEL, 700)
    label("chip_silver", "銀貨", 15, MUTE, PANEL, 700)
    label("totals", "取ったものの合計", 17, INK, PANEL, 700)
    label("totals_empty", "まだ何も取っていません", 16, DIM, PANEL, 700)
    label("keystones", "キーストーン", 17, INK, PANEL, 700)
    label("keystones_sub", "外周の◆", 15, MUTE, PANEL, 700)
    label("cost", "戻す代金", 15, MUTE, PANEL, 700)
    label("cost_unit", "銀貨", 15, MUTE, PANEL, 700)
    for key, text, color in [("taken", "取得済み", GOLD), ("here", "ここから伸ばす", MUTE), ("other", "ほかの職業の起点", MUTE),
                             ("pick", "1つ選んで 1 点で取る", OK), ("locked", "この輪の主要ノードを先に取る", MUTE),
                             ("far", "つながっていない", DANGER), ("need_a", "あと", OK), ("need_b", "点で届く", OK),
                             ("short", "点必要（ポイントが足りない）", DANGER), ("nopoint", "ポイントが足りない", DANGER),
                             ("hub", "港で変更できます", DANGER), ("soon", "中身はこれから決めます", MUTE), ("failed", "できませんでした", DANGER)]:
        label(f"st_{key}", text, 16, color, PANEL, 700)
    label("dup", "ほかの輪で選び済み", 15, DANGER, CHOICE, 700)
    labels["footer"] = columns("label_footer", footer())
    labels["legend"] = columns("label_legend", legend())
    # Stat names for the totals, one per distinct name.
    stat_names = sorted({m[1] for n in nodes for part in (n["text"].split(" / ") if n["kind"] in ("SMALL", "NOTABLE") else []) +
                         [p for c in n.get("choiceTexts", []) for p in c.split(" / ")] if (m := STAT.match(part))})
    stat_keys = {}
    for i, name in enumerate(stat_names):
        stat_keys[name] = f"stat_{i}"; label(f"stat_{i}", name, 16, INK, PANEL, 700)
    # Mastery option buttons: one label per distinct option text, shared by every wheel that offers it.
    choice_keys: dict[str, str] = {}
    for n in nodes:
        for text in n.get("choiceTexts", []):
            if text not in choice_keys:
                choice_keys[text] = f"choice_{len(choice_keys)}"; label(choice_keys[text], text, 16, INK, CHOICE, 700)

    # Chrome sprites: rounded corners (white, tinted per use), the emblem, keystone slots, buttons.
    q = supersampled(64, 64, lambda d, s: d.ellipse([-64 * s, -64 * s, 64 * s - 1, 64 * s - 1], fill=255))
    q = np.where(q >= .5, 255, 0).astype(np.uint8)  # the curve of a bottom-right corner (centre at the top-left)
    for k, m in [("round_tl", q[::-1, ::-1]), ("round_tr", q[::-1, :]), ("round_bl", q[:, ::-1]), ("round_br", q)]:
        add(k, Image.fromarray(np.dstack([np.full((64, 64, 3), 255, np.uint8), np.ascontiguousarray(m)]), "RGBA"))
    add("brand_slot", brand())
    add("key_off", keystone_slot(False)); add("key_on", keystone_slot(True))
    add("zoom_in", zoom_button("+")); add("zoom_out", zoom_button("-"))
    for state, kw in [("take", dict(fill=EMBER, gradient=((255, 176, 112), EMBER), highlight=True)),
                      ("refund", dict(fill=(42, 28, 28), edge=(105, 70, 66))), ("off", dict(fill=(28, 29, 34)))]:
        img = rounded(INFO_W, 72, 12, back=PANEL, **kw)
        for k in range(2):
            add(f"go_{state}_{k}", img.crop((k * 256, 0, min(INFO_W, (k + 1) * 256), 72)))

    files[f"assets/{NS}/font/passive_tree.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()
    with zipfile.ZipFile(OUT / "passive-tree.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    layout = {"scale": SCALE, "dot": DOT, "originX": bake.x0, "originY": bake.y0, "width": bake.w, "height": bake.h,
              "tiles": tiles, "edges": edges, "paths": paths, "sprites": sprites, "infos": infos, "labels": labels,
              "statLabels": stat_keys,
              "nodes": [{"id": n["id"], "x": n["cx"], "y": n["cy"], "kind": n["kind"], "icon": n["icon"], "text": n["text"],
                         "choiceTexts": n.get("choiceTexts", []),
                         "choiceLabels": [choice_keys[t] for t in n.get("choiceTexts", [])]} for n in nodes]}
    (OUT / "passive-tree.json").write_text(json.dumps(layout, ensure_ascii=False), encoding="utf-8")
    print(f"PASSIVE_TREE_PACK_BUILT tiles={len(tiles)} sprites={len(sprites)} last=U+{code[0] - 1:04X} "
          f"bytes={(OUT / 'passive-tree.zip').stat().st_size}")
    if preview:
        render_preview()


def render_preview():
    """The flow's own scene (written by PassiveTreeFlowTest) drawn the way the client draws it."""
    scene = ROOT / "server-minestom/build/passive-tree-preview/scene.json"
    subprocess.run(["flock", str(Path.home() / "ProjectS/.heavy.lock"), "./gradlew", ":server-minestom:test", "--offline", "--console=plain",
                    "--tests", "dev.projects.server.coreloop.PassiveTreeFlowTest"], cwd=ROOT, check=True)
    out = ROOT / "docs/proposals/skill-tree-2026-10/passive-tree-screen.png"
    subprocess.run([sys.executable, str(ROOT / "scripts/render_grimoire_preview.py"), str(ROOT), str(scene), str(out), "2.4"], check=True)
    # The scene is 800x480 around a 16:9 screen: keep only the 1920x1080 game area.
    shot = Image.open(out); top = round((480 - 1080 * 800 / 1920) / 2 * 2.4)
    shot.crop((0, top, 1920, top + 1080)).save(out)
    print(f"PASSIVE_TREE_PREVIEW {out}")


if __name__ == "__main__":
    if sys.argv[1:] == ["preview-only"]:
        render_preview()
    else:
        main(preview=sys.argv[1:] == ["preview"])
