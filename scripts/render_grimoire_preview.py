"""Render a UiScene JSON (written by GrimoireSceneGoldenTest to server-minestom/build/grimoire-golden/) to PNG,
approximating UiRenderer in the client, to look at the 魔術書 without starting a client.
usage: python scripts/render_grimoire_preview.py . server-minestom/build/grimoire-golden/0.json out.png [scale]"""
import io, json, sys, zipfile
from PIL import Image, ImageDraw, ImageFont

repo, src, out = sys.argv[1], sys.argv[2], sys.argv[3]
K = float(sys.argv[4]) if len(sys.argv) > 4 else 2.4
scene = json.load(open(src, encoding="utf-8"))

# --- sprites: char -> image cell, from the font providers in the packs
cells = {}
for z in ("server-minestom/src/main/resources/polish05/pack.zip", "server-minestom/src/main/resources/polish05/grimoire.zip"):
    with zipfile.ZipFile(f"{repo}/{z}") as a:
        names = set(a.namelist())
        for n in names:
            if "/font/" not in n or not n.endswith(".json"):
                continue
            ns = n.split("/")[1]; font = ns + ":" + n.split("/font/")[1][:-5]
            try:
                provs = json.loads(a.read(n))["providers"]
            except Exception:
                continue
            for p in provs:
                if p.get("type") != "bitmap":
                    continue
                fns, fpath = p["file"].split(":", 1)
                path = f"assets/{fns}/textures/{fpath}"
                if path not in names:
                    continue
                rows = p["chars"]
                for r, row in enumerate(rows):
                    for c, ch in enumerate(row):
                        if ch != "\0":
                            cells[(font, ch)] = (z, path, r, c, len(rows), len(row), p.get("height", 8), p.get("ascent", 7))
images = {}
def cell(font, ch):
    key = cells.get((font, ch))
    if not key:
        return None
    z, path, r, c, nr, nc = key[:6]
    if (z, path) not in images:
        with zipfile.ZipFile(f"{repo}/{z}") as a:
            images[(z, path)] = Image.open(io.BytesIO(a.read(path))).convert("RGBA")
    im = images[(z, path)]
    w, h = im.width // nc, im.height // nr
    return im.crop((c * w, r * h, c * w + w, r * h + h))

# Alpha at which a glyph texel counts as ink: the client's text cutoff is 0.1 (26). Set GRIMOIRE_TEXT_CUT=128
# to preview glyph pages that were thresholded at 0.5 when built.
import os
CUT = int(os.environ.get("GRIMOIRE_TEXT_CUT", "26"))
METRICS = json.load(open(f"{repo}/web-ui-lab/src/main/resources/polish05-font-metrics.json", encoding="utf-8"))
FONT = f"{repo}/web-ui-lab/ui/forge-v3-fonts/MPLUS2.ttf"
DOT = f"{repo}/web-ui-lab/ui/forge-v3-fonts/DotGothic16.ttf"
fonts = {}
def tfont(family, px):
    fam = family.split(":")[-1].rstrip("0123456789")
    key = (fam, round(px))
    if key not in fonts:
        if fam == "v3num":
            f = ImageFont.truetype(DOT, max(1, round(px)))
        else:
            f = ImageFont.truetype(FONT, max(1, round(px)))
            try:
                f.set_variation_by_axes([{"v3r": 400, "v3m": 500, "v3b": 700}.get(fam, 400)])
            except Exception:
                pass
        fonts[key] = f
    return fonts[key]

def color(hexs):
    h = hexs.lstrip("#")
    if len(h) == 8:
        return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)

W, H = round(scene["width"] * K), round(scene["height"] * K)
canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
items = []
for i, n in enumerate(scene["nodes"]):
    b = n["box"]; st = n.get("style", {})
    if "background-color" in st:
        items.append((n["depth"] * 0.005, i, "fill", n))
    if n.get("sprite") or n.get("text"):
        items.append((n["depth"] * 0.005 + 0.025, i, "glyph", n))
items.sort(key=lambda t: (t[0], t[1]))

for _, _, kind, n in items:
    b = n["box"]; st = n.get("style", {})
    x, y, w, h = b["x"] * K, b["y"] * K, b["w"] * K, b["h"] * K
    if kind == "fill":
        c = color(st["background-color"])
        if c[3] == 0 or round(x + w) <= round(x) or round(y + h) <= round(y):
            continue  # newer Pillow refuses empty rectangles
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(layer).rectangle((round(x), round(y), round(x + w) - 1, round(y + h) - 1), fill=c)
        canvas.alpha_composite(layer)
    elif n.get("sprite"):
        s = n["sprite"]
        im = cell(s["font"], s["char"])
        if im is None:
            continue
        factor = h / s["height"]
        sw = max(1, round(s["width"] * factor)); sh = max(1, round(h))
        im = im.resize((sw, sh), Image.NEAREST if s["height"] <= 64 and "v3" not in s["font"] else Image.BILINEAR)
        if "sprite-color" in st:
            t = color(st["sprite-color"])
            r, g, bb, a = im.split()
            im = Image.merge("RGBA", (r.point(lambda v: v * t[0] // 255), g.point(lambda v: v * t[1] // 255), bb.point(lambda v: v * t[2] // 255), a))
        # Sprites are glyphs too: every texel above the 0.1 cutoff is drawn fully opaque (soft glows become discs).
        im.putalpha(im.getchannel("A").point(lambda v: 255 if v >= 26 else 0))
        if "opacity" in st:
            o = max(26, int(float(st["opacity"]) * 255)) / 255
            im.putalpha(im.getchannel("A").point(lambda v: int(v * o)))
        cx = x + w / 2
        canvas.alpha_composite(im, (round(cx - sw / 2), round(y)))
    else:
        # Text as the client draws it: the pack's bitmap glyph pages, nearest-sampled, and cut out at alpha 0.1
        # (the text shader keeps every texel above the cutoff fully opaque, so anti-aliased edges thicken).
        fs = float(st.get("font-size", "8px").rstrip("px"))
        family = st.get("font-family", "")
        pixel = fs / 32.0
        c = color(st.get("color", "#ffffff"))
        widths = METRICS.get(family.split(":")[-1], {})
        text = n["text"]
        adv = sum(widths.get(str(ord(ch)), 32 if ord(ch) > 255 else 18) for ch in text) * pixel
        align = st.get("text-align", "center" if n.get("action") else "left")
        bx, bw = b["x"], b["w"]
        left = bx + bw / 2 - adv / 2 if align == "center" else (bx + bw - adv if align == "right" else bx)
        top = b["y"] + (b["h"] - fs) / 2 + pixel
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        def draw_line(ox, oy, col):
            x0 = left
            for ch in text:
                key = cells.get((family, ch))
                if key is not None:
                    g = cell(family, ch)
                    gh = key[6] * pixel; gw = gh * g.width / g.height
                    gx, gy = round((x0 + ox) * K), round((top + oy) * K)
                    sz = (max(1, round(gw * K)), max(1, round(gh * K)))
                    a = g.getchannel("A").point(lambda v: 255 if v >= CUT else 0).resize(sz, Image.NEAREST)
                    solid = Image.new("RGBA", sz, col); solid.putalpha(a)
                    layer.alpha_composite(solid, (gx, gy))
                x0 += widths.get(str(ord(ch)), 32 if ord(ch) > 255 else 18) * pixel
        if st.get("text-shadow") == "true":
            draw_line(pixel, pixel, (c[0] // 4, c[1] // 4, c[2] // 4, 255))
        draw_line(0, 0, c)
        canvas.alpha_composite(layer)

canvas.convert("RGB").save(out)
print(out)
