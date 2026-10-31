"""Bake the passive tree screen (the camera + display-entity UI, like the 開拓大陸 map).

    python3 scripts/build_passive_tree_assets.py [preview]

Reads assets/ui/passive-tree/tree.json (written by scripts/build_passive_tree.py) and writes
server-minestom/src/main/resources/polish05/passive-tree.zip (private-namespace sprites, merged into the
UI pack by CoreUiPackServer) and polish05/passive-tree.json (sprite chars and where each one sits).

The whole tree (dim lines, node frames, names) is baked into opaque 256 px tiles at SCALE design pixels
per tree unit. What changes per player is drawn on top: one gold sprite per edge for taken links and
gold node faces. Overlay sprites use hard alpha so they never hide each other. `preview` also writes
docs/proposals/skill-tree-2026-10/passive-tree-screen.png, a mock of the screen from the baked art.
"""
from __future__ import annotations

import io
import json
import math
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/ui/passive-tree/tree.json"
OUT = ROOT / "server-minestom/src/main/resources/polish05"
# The shared forge fonts are subsets without most of the tree's kanji, so every word on this screen is baked.
FONT_FILE = Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc")
FONT_REGULAR = Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")


def font(size, bold=True):
    return ImageFont.truetype(str(FONT_FILE if bold else FONT_REGULAR), size, index=0)  # index 0 is the JP face
NS = "projects_ui_polish05"
FONT = f"{NS}:passive_tree"
TILE = 256
SCALE = 0.45  # design px per tree unit at zoom 1
PAD = 160     # tree units of margin around the outermost node

BG_TOP = (14, 16, 24)
BG_MID = (22, 24, 36)
LINE = (66, 62, 54)
GOLD = (232, 182, 82)
GOLD_DARK = (120, 84, 30)
FRAME = (24, 24, 30)
SMALL = (122, 117, 106)
NOTABLE = (110, 140, 214)
KEY = (214, 112, 86)
MASTERY = (150, 146, 136)
START = (112, 200, 146)
TEXT = (232, 226, 210)
TEXT_DIM = (150, 144, 132)
RADIUS = {"SMALL": 22, "NOTABLE": 38, "KEYSTONE": 58, "START": 74, "MASTERY": 30}
# Gold faces are drawn this many design px wide (the frame shows a dark rim around them).
FACE = {k: round(2 * r * SCALE) for k, r in RADIUS.items()}


def bounds(nodes):
    xs = [n["x"] for n in nodes]; ys = [n["y"] for n in nodes]
    return min(xs) - PAD, min(ys) - PAD, max(xs) + PAD, max(ys) + PAD


def arc_points(tree, a, b, steps=24):
    g = tree["groups"][a["group"]]; r = tree["orbitRadii"][a["orbit"]]
    a1 = math.atan2(a["y"] - g["y"], a["x"] - g["x"]); a2 = math.atan2(b["y"] - g["y"], b["x"] - g["x"])
    d = (a2 - a1 + math.pi) % (2 * math.pi) - math.pi
    return [(g["x"] + r * math.cos(a1 + d * t / steps), g["y"] + r * math.sin(a1 + d * t / steps)) for t in range(steps + 1)]


def edge_points(tree, a, b, arc):
    return arc_points(tree, a, b) if arc else [(a["x"], a["y"]), (b["x"], b["y"])]


def draw_node(draw, n, x, y):
    r = RADIUS[n["kind"]] * SCALE
    if n["kind"] == "KEYSTONE":
        pts = [(x, y - r), (x + r, y), (x, y + r), (x - r, y)]
        draw.polygon(pts, fill=FRAME, outline=KEY, width=3)
        inner = [(x, y - r + 6), (x + r - 6, y), (x, y + r - 6), (x - r + 6, y)]
        draw.polygon(inner, fill=(64, 34, 28))
    elif n["kind"] == "MASTERY":
        draw.ellipse([x - r, y - r, x + r, y + r], outline=MASTERY, width=2)
        draw.ellipse([x - r / 3, y - r / 3, x + r / 3, y + r / 3], fill=MASTERY)
    else:
        color = {"SMALL": SMALL, "NOTABLE": NOTABLE, "START": START}[n["kind"]]
        draw.ellipse([x - r, y - r, x + r, y + r], fill=FRAME, outline=color, width=3 if n["kind"] != "SMALL" else 2)
        inner = r - (5 if n["kind"] != "SMALL" else 4)
        draw.ellipse([x - inner, y - inner, x + inner, y + inner], fill=tuple(c // 3 for c in color))


def draw_label(draw, n, x, y, fonts):
    """Names go on after every frame and line, outlined so a crossing line never cuts them."""
    if n["kind"] not in ("NOTABLE", "KEYSTONE", "START"):
        return
    r = RADIUS[n["kind"]] * SCALE
    label = n["name"].replace("の起点", "") if n["kind"] == "START" else n["name"]
    f = fonts[n["kind"]]
    w = draw.textlength(label, font=f)
    draw.text((x - w / 2, y + r + 3), label, font=f, fill=TEXT, stroke_width=3, stroke_fill=BG_TOP)


def base_image(tree):
    nodes = tree["nodes"]
    x0, y0, x1, y1 = bounds(nodes)
    w, h = math.ceil((x1 - x0) * SCALE), math.ceil((y1 - y0) * SCALE)
    img = Image.new("RGB", (w, h), BG_TOP)
    # A soft radial glow toward the middle where the classes start.
    glow = Image.new("L", (w, h), 0)
    gd = ImageDraw.Draw(glow)
    cx, cy = -x0 * SCALE, -y0 * SCALE
    for k in range(40, 0, -1):
        rr = k / 40 * max(w, h) * 0.55
        gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=int(255 * (1 - k / 40) ** 1.6))
    img = Image.composite(Image.new("RGB", (w, h), BG_MID), img, glow)
    draw = ImageDraw.Draw(img)
    to = lambda p: ((p[0] - x0) * SCALE, (p[1] - y0) * SCALE)
    for a, b, arc in tree["edges"]:
        draw.line([to(p) for p in edge_points(tree, nodes[a], nodes[b], arc)], fill=LINE, width=5, joint="curve")
    fonts = {"NOTABLE": font(15), "KEYSTONE": font(18),
            "START": font(22)}
    for n in nodes:
        draw_node(draw, n, *to((n["x"], n["y"])))
    for n in nodes:
        draw_label(draw, n, *to((n["x"], n["y"])), fonts)
    return img, (x0, y0)


def hard(img):
    """Overlay sprites keep only fully opaque pixels so they never hide one another."""
    alpha = img.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    img.putalpha(alpha)
    return img


def edge_sprite(tree, a, b, arc, origin):
    """A gold line over one edge, cropped to its bounding box (split if longer than a glyph)."""
    pts = [((x - origin[0]) * SCALE, (y - origin[1]) * SCALE) for x, y in edge_points(tree, a, b, arc)]
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    left, top = math.floor(min(xs)) - 4, math.floor(min(ys)) - 4
    w, h = math.ceil(max(xs)) + 4 - left, math.ceil(max(ys)) + 4 - top
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([(x - left, y - top) for x, y in pts], fill=GOLD + (255,), width=5, joint="curve")
    return hard(img), left, top


def face(kind, lit=True):
    """The gold (or highlight) face drawn over a node frame."""
    size = FACE[kind]
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = size / 2
    color = GOLD if lit else (245, 240, 225)
    if kind == "KEYSTONE":
        d.polygon([(r, 0), (size - 1, r), (r, size - 1), (0, r)], fill=GOLD_DARK)
        d.polygon([(r, 5), (size - 6, r), (r, size - 6), (5, r)], fill=color)
    elif kind == "MASTERY":
        d.ellipse([0, 0, size - 1, size - 1], outline=color, width=3)
        d.ellipse([r - r / 3, r - r / 3, r + r / 3, r + r / 3], fill=color)
    else:
        d.ellipse([0, 0, size - 1, size - 1], fill=GOLD_DARK)
        d.ellipse([3, 3, size - 4, size - 4], fill=color)
    return hard(img)


def ring(kind, color, gap=5, width=3):
    size = FACE[kind] + 2 * gap
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "KEYSTONE":
        r = size / 2
        d.polygon([(r, 0), (size - 1, r), (r, size - 1), (0, r)], outline=color + (255,), width=width)
    else:
        d.ellipse([0, 0, size - 1, size - 1], outline=color + (255,), width=width)
    return hard(img)


PANEL_W = 400
KIND_LABEL = {"SMALL": "小さいノード", "NOTABLE": "主要ノード", "KEYSTONE": "キーストーン", "START": "起点",
              "MASTERY": "極意"}
# Fixed words on the screen: key -> (text, size, colour).
LABELS = {
    "title": ("パッシブツリー", 34, TEXT), "points": ("残りポイント", 18, TEXT_DIM),
    "hint": ("画面の端にカーソルで移動 ・ ホイールで拡大縮小 ・ 同じノードを2回押すと取る ・ しゃがむと閉じる", 17, TEXT_DIM),
    "take": ("取る", 28, (26, 18, 8)), "refund": ("戻す", 28, (26, 18, 8)), "reset": ("全部戻す", 20, TEXT),
    "close": ("閉じる", 20, TEXT), "cost": ("戻す代金（銀貨）", 16, TEXT_DIM), "silver": ("所持銀貨", 16, TEXT_DIM),
    "reach": ("あと何点で届くか", 16, TEXT_DIM), "taken": ("取得済み", 22, (242, 212, 138)), "far": ("つながっていない", 20, (255, 154, 138)),
    "nopoint": ("ポイントが足りない", 20, (255, 154, 138)), "soon": ("中身はこれから決めます", 20, TEXT_DIM),
    "hub": ("港で変更できます", 20, (255, 154, 138)),
    "locked": ("この輪の主要ノードを先に取る", 20, (255, 154, 138)), "pick": ("下から1つ選ぶ（1点）", 20, (154, 240, 160)),
    "dup": ("ほかの輪で取得済み", 15, (255, 154, 138)), "failed": ("できませんでした", 20, (255, 154, 138)), "need": ("必要", 20, TEXT_DIM), "pt": ("点", 20, TEXT_DIM),
}
for job, label in [("WARRIOR", "戦士"), ("TEMPLAR", "テンプラー"), ("MAGE", "メイジ"), ("HEALER", "ヒーラー"), ("RANGER", "レンジャー"), ("ASSASSIN", "アサシン")]:
    LABELS[f"job_{job}"] = (label, 22, (242, 212, 138))


def wrap(draw, text, f, width):
    lines, line = [], ""
    for ch in text:
        if draw.textlength(line + ch, font=f) > width and line:
            lines.append(line); line = ""
        line += ch
    return lines + [line] if line else lines


def info_image(n):
    """The panel block for one node: kind, name, then its effect lines, wrapped to PANEL_W."""
    img = Image.new("RGBA", (PANEL_W, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.text((0, 0), KIND_LABEL[n["kind"]], font=font(16, False), fill=TEXT_DIM + (255,))
    d.text((0, 22), n["name"], font=font(30), fill=TEXT + (255,))
    y = 70
    body = font(20, False)
    parts = n["text"].split(" / ") if n["kind"] in ("SMALL", "NOTABLE") else [n["text"]]
    if n["kind"] == "MASTERY":
        parts = ["この輪の主要ノードを取ると、1点で次の3つから1つ選べる。同じものはほかの輪では選べない。"]
    for part in parts:
        for line in wrap(d, part, body, PANEL_W):
            color = (169, 200, 255) if n["kind"] in ("SMALL", "NOTABLE") else TEXT
            d.text((0, y), line, font=body, fill=color + (255,)); y += 28
    return hard(img.crop((0, 0, PANEL_W, min(256, y + 4))))


def label_image(text, size, color):
    f = font(size)
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    w = math.ceil(probe.textlength(text, font=f)) + 2
    img = Image.new("RGBA", (w, round(size * 1.45)), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((0, 0), text, font=f, fill=color + (255,))
    return hard(img)


def main(preview: bool) -> None:
    tree = json.loads(SOURCE.read_text(encoding="utf-8"))
    nodes = tree["nodes"]
    files: dict[str, bytes] = {}
    providers: list[dict] = []
    sprites: dict[str, dict] = {}
    code = [0xE000]

    def add(name: str, image: Image.Image) -> str:
        assert image.width <= 256 and image.height <= 256, (name, image.size)
        char = chr(code[0]); code[0] += 1
        rel = f"passive_tree/{name}.png"
        buffer = io.BytesIO(); image.save(buffer, "PNG", optimize=True)
        files[f"assets/{NS}/textures/{rel}"] = buffer.getvalue()
        providers.append({"type": "bitmap", "file": f"{NS}:{rel}", "ascent": image.height, "height": image.height, "chars": [char]})
        sprites[name] = {"char": char, "font": FONT, "width": image.width, "height": image.height}
        return name

    base, origin = base_image(tree)
    tiles = []
    for y in range(0, base.height, TILE):
        for x in range(0, base.width, TILE):
            tiles.append({"name": add(f"tile_{x}_{y}", base.crop((x, y, min(x + TILE, base.width), min(y + TILE, base.height))).convert("RGBA")), "x": x, "y": y})
    edges = []
    for a, b, arc in tree["edges"]:
        img, left, top = edge_sprite(tree, nodes[a], nodes[b], arc, origin)
        parts = []
        # Glyphs over 256 px do not render: long edges are split into pieces.
        for py in range(0, img.height, TILE):
            for px in range(0, img.width, TILE):
                piece = img.crop((px, py, min(px + TILE, img.width), min(py + TILE, img.height)))
                if piece.getbbox() is None:
                    continue
                parts.append({"sprite": add(f"edge_{a}_{b}_{px}_{py}", piece), "x": left + px, "y": top + py})
        edges.append({"a": a, "b": b, "parts": parts})
    # Panel text per node (name, kind, effect lines), split into glyph-sized columns.
    infos = {}
    for n in nodes:
        img = info_image(n)
        infos[n["id"]] = [{"sprite": add(f"info_{n['id']}_{k}", img.crop((k * 256, 0, min(img.width, (k + 1) * 256), img.height))),
                           "x": k * 256, "y": 0} for k in range(math.ceil(img.width / 256))]
    labels = {}
    for key, (text, size, color) in LABELS.items():
        img = label_image(text, size, color)
        labels[key] = [{"sprite": add(f"label_{key}_{k}", img.crop((k * 256, 0, min(img.width, (k + 1) * 256), img.height))),
                        "x": k * 256, "y": 0} for k in range(math.ceil(img.width / 256))]
    # Mastery option buttons: one label per distinct option text, shared by every wheel that offers it.
    choice_keys: dict[str, str] = {}
    for n in nodes:
        for text in n.get("choiceTexts", []):
            if text in choice_keys:
                continue
            key = choice_keys[text] = f"choice_{len(choice_keys)}"
            img = label_image(text, 16, TEXT)
            labels[key] = [{"sprite": add(f"label_{key}_{k}", img.crop((k * 256, 0, min(img.width, (k + 1) * 256), img.height))),
                            "x": k * 256, "y": 0} for k in range(math.ceil(img.width / 256))]
    for kind in RADIUS:
        add(f"face_{kind}", face(kind))
        add(f"ring_{kind}", ring(kind, (245, 240, 225)))
        add(f"reach_{kind}", ring(kind, (120, 200, 150), gap=3, width=2))
    files[f"assets/{NS}/font/passive_tree.json"] = json.dumps({"providers": providers}, separators=(",", ":")).encode()
    with zipfile.ZipFile(OUT / "passive-tree.zip", "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(files):
            archive.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), files[name])
    layout = {"scale": SCALE, "originX": origin[0], "originY": origin[1], "width": base.width, "height": base.height,
              "tiles": tiles, "edges": edges, "sprites": sprites, "infos": infos, "labels": labels,
              "nodes": [{"id": n["id"], "x": round((n["x"] - origin[0]) * SCALE, 1), "y": round((n["y"] - origin[1]) * SCALE, 1),
                         "kind": n["kind"], "text": n["text"],
                         "choiceLabels": [choice_keys[t] for t in n.get("choiceTexts", [])]} for n in nodes]}
    (OUT / "passive-tree.json").write_text(json.dumps(layout, ensure_ascii=False), encoding="utf-8")
    print(f"PASSIVE_TREE_PACK_BUILT tiles={len(tiles)} sprites={len(sprites)} bytes={(OUT / 'passive-tree.zip').stat().st_size}")
    if preview:
        mock(base, tree, origin)


def mock(base, tree, origin):
    """A 1920x1080 still of the screen: zoom 1, a warrior with a few nodes taken, the side panel."""
    nodes = tree["nodes"]
    start = next(n for n in nodes if n.get("start") == "WARRIOR")
    adj = {n["id"]: [] for n in nodes}
    for a, b, _ in tree["edges"]:
        adj[a].append(b); adj[b].append(a)
    taken, frontier = set(), [start["id"]]
    # Walk outward from the start toward the nearest notables, like a first build.
    for _ in range(14):
        best = min((m for f in [start["id"], *taken] for m in adj[f] if m not in taken and nodes[m]["kind"] in ("SMALL", "NOTABLE")),
                   key=lambda m: math.hypot(nodes[m]["x"] - start["x"] * 1.6, nodes[m]["y"] - start["y"] * 1.6))
        taken.add(best)
    screen = Image.new("RGB", (1920, 1080), BG_TOP)
    cx = (start["x"] - origin[0]) * SCALE; cy = (start["y"] - origin[1]) * SCALE
    pan = (720 - cx, 560 - cy)
    screen.paste(base, (round(pan[0]), round(pan[1])))
    d = ImageDraw.Draw(screen)
    lit = taken | {start["id"]}
    for a, b, arc in tree["edges"]:
        if a in lit and b in lit:
            pts = [((x - origin[0]) * SCALE + pan[0], (y - origin[1]) * SCALE + pan[1]) for x, y in edge_points(tree, nodes[a], nodes[b], arc)]
            d.line(pts, fill=GOLD, width=5, joint="curve")
    for i in taken:
        n = nodes[i]; f = face(n["kind"])
        x = (n["x"] - origin[0]) * SCALE + pan[0] - f.width / 2; y = (n["y"] - origin[1]) * SCALE + pan[1] - f.height / 2
        screen.paste(f, (round(x), round(y)), f)
    reach = {m for t in lit for m in adj[t] if m not in lit and nodes[m]["kind"] in ("SMALL", "NOTABLE")}
    for i in reach:
        n = nodes[i]; f = ring(n["kind"], (120, 200, 150), gap=3, width=2)
        x = (n["x"] - origin[0]) * SCALE + pan[0] - f.width / 2; y = (n["y"] - origin[1]) * SCALE + pan[1] - f.height / 2
        screen.paste(f, (round(x), round(y)), f)
    title = font(30); body = font(20); small = font(16)
    d.rectangle([0, 0, 1920, 84], fill=(12, 13, 18))
    d.text((36, 22), "パッシブツリー", font=title, fill=TEXT)
    d.text((300, 32), f"戦士　残り {39 - len(taken)} / 39 ポイント", font=body, fill=(242, 212, 138))
    d.text((1000, 34), "端にカーソルで移動 ・ ホイールで拡大縮小 ・ 2回押すと取る ・ しゃがむと閉じる", font=small, fill=TEXT_DIM)
    px = 1460
    d.rectangle([px, 84, 1920, 1080], fill=(16, 17, 22)); d.line([px, 84, px, 1080], fill=(90, 76, 52), width=2)
    sel = max((nodes[m] for m in reach if nodes[m]["kind"] == "NOTABLE"), key=lambda n: -math.hypot(n["x"], n["y"]), default=nodes[next(iter(reach))])
    d.text((px + 28, 120), "主要ノード", font=small, fill=TEXT_DIM)
    d.text((px + 28, 146), sel["name"], font=font(28), fill=TEXT)
    y = 200
    for line in sel["text"].split(" / "):
        d.text((px + 28, y), line, font=body, fill=(169, 200, 255)); y += 32
    d.text((px + 28, y + 12), "あと 1 点で届く", font=body, fill=(120, 200, 150))
    d.rounded_rectangle([px + 28, 900, 1892, 972], radius=10, fill=(232, 182, 82))
    d.text((px + 150, 920), "取る（1点）", font=font(26), fill=(26, 18, 8))
    d.text((px + 28, 990), "戻す代金：1点ごとに銀貨400枚", font=small, fill=TEXT_DIM)
    ring_sel = ring(sel["kind"], (245, 240, 225))
    screen.paste(ring_sel, (round((sel["x"] - origin[0]) * SCALE + pan[0] - ring_sel.width / 2), round((sel["y"] - origin[1]) * SCALE + pan[1] - ring_sel.height / 2)), ring_sel)
    out = ROOT / "docs/proposals/skill-tree-2026-10/passive-tree-screen.png"
    screen.save(out)
    print(f"PASSIVE_TREE_MOCK {out}")


if __name__ == "__main__":
    main(preview=sys.argv[1:] == ["preview"])
