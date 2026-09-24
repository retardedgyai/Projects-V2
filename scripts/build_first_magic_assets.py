"""Author the first-magic pack assets. Original pixel art and cuboid models; no vanilla item swaps.

Run with `python scripts/build_first_magic_assets.py`. Only writes projects:first_magic
assets and two dedicated font glyphs, then updates the pack's explicit index.
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack"
ASSET = PACK / "assets/projects"
MAGIC = "first_magic"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


P = {
    "ink": "#171d24", "wood": "#392d29", "wood_light": "#694438", "wood_edge": "#9a6550",
    "brass": "#bd8b4f", "brass_light": "#edc881", "brass_dark": "#685033",
    "paper": "#ead9b3", "paper_shadow": "#b49b75", "stone": "#768286", "glass": "#b2dde0",
    "ember": "#e98a4f", "ember_light": "#ffd58b", "tide": "#59bfc1", "tide_light": "#abe9d8",
    "gale": "#a2cedd", "gale_light": "#e2f3ee", "stone_aspect": "#bdc5bd", "stone_light": "#f5efdb",
}


def item_base() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    im = Image.new("RGBA", (32, 32))
    return im, ImageDraw.Draw(im)


def mark(draw: ImageDraw.ImageDraw, xy, fill, outline=P["ink"], width=1):
    draw.ellipse(xy, fill=fill, outline=outline, width=width)


def icon(name: str) -> Image.Image:
    im, d = item_base()
    if name in ("ember", "tide", "gale", "stone"):
        # An engraved observatory seal, with a different silhouette at its center.
        accent = P["stone_aspect"] if name == "stone" else P[name]
        pale = P["stone_light"] if name == "stone" else P[name + "_light"]
        d.ellipse((2, 2, 29, 29), fill=P["ink"], outline=P["brass_dark"], width=2)
        d.arc((4, 4, 27, 27), 22, 158, fill=P["brass_light"], width=2)
        d.arc((4, 4, 27, 27), 202, 338, fill=P["brass_light"], width=2)
        for x, y in ((15, 1), (30, 15), (15, 30), (1, 15)):
            d.rectangle((x, y, x + 1, y + 1), fill=P["brass_light"])
        if name == "ember":
            d.polygon([(16, 5), (20, 12), (20, 19), (16, 26), (10, 22), (9, 16), (12, 13), (11, 20), (16, 16)], fill=accent)
            d.polygon([(17, 14), (18, 20), (15, 24), (13, 20)], fill=pale)
        elif name == "tide":
            d.arc((7, 7, 25, 24), 205, 530, fill=accent, width=4)
            d.arc((10, 12, 22, 22), 15, 195, fill=pale, width=3)
            d.polygon([(8, 17), (12, 19), (8, 22)], fill=accent)
        elif name == "gale":
            for box, angle in [((6, 7, 25, 19), (188, 350)), ((9, 13, 26, 25), (170, 346))]:
                d.arc(box, *angle, fill=pale, width=3)
            d.line((12, 13, 22, 13), fill=accent, width=2)
            d.line((9, 20, 18, 20), fill=accent, width=2)
        else:
            d.polygon([(15, 6), (23, 12), (23, 21), (16, 26), (8, 21), (8, 12)], fill=accent, outline=P["ink"])
            d.line((15, 7, 15, 24), fill=pale, width=2)
            d.line((9, 13, 22, 13), fill=pale, width=2)
            d.polygon([(16, 13), (22, 13), (21, 20), (16, 24)], fill=P["stone"])
        return im

    if name == "moonbell":
        d.line((16, 27, 15, 12, 12, 8), fill="#789976", width=2)
        d.line((15, 20, 8, 18), fill="#789976", width=2)
        d.polygon([(15, 9), (10, 12), (11, 20), (17, 22), (23, 19), (22, 12)], fill="#77c7c5", outline=P["ink"])
        d.polygon([(15, 10), (12, 15), (14, 20), (17, 20), (19, 14)], fill="#d6ede3")
        mark(d, (14, 19, 18, 23), P["brass_light"])
        d.point((5, 10), fill=P["gale_light"]); d.point((25, 6), fill=P["gale_light"])
    elif name == "ember_moss":
        d.polygon([(4, 23), (8, 18), (12, 20), (15, 14), (18, 18), (22, 13), (27, 23), (24, 27), (7, 27)], fill="#46583d", outline=P["ink"])
        for x, y in ((7, 19), (12, 16), (18, 19), (23, 16)):
            d.polygon([(x, y + 5), (x + 1, y - 3), (x + 4, y + 2), (x + 2, y + 1)], fill=P["ember"])
            d.point((x + 1, y + 1), fill=P["ember_light"])
    elif name == "hollow_crystal":
        d.polygon([(15, 3), (23, 9), (26, 21), (17, 29), (8, 23), (7, 10)], fill="#6aaeb4", outline=P["ink"])
        d.polygon([(15, 3), (16, 25), (8, 23), (7, 10)], fill="#c0e6de")
        d.polygon([(16, 7), (21, 12), (20, 20), (16, 24), (12, 20), (12, 12)], fill=P["ink"])
        d.line((17, 8, 23, 10, 25, 20), fill=P["gale_light"], width=1)
    elif name == "warm_ore":
        d.polygon([(3, 19), (9, 10), (19, 7), (28, 15), (27, 25), (18, 29), (6, 26)], fill="#79615a", outline=P["ink"])
        d.polygon([(9, 10), (19, 7), (27, 15), (18, 18)], fill="#ad8170")
        d.line((8, 22, 14, 17, 19, 19, 23, 13), fill=P["ember"], width=3)
        d.line((13, 18, 18, 20, 22, 15), fill=P["ember_light"], width=1)
    elif name == "tidewing_feather":
        d.polygon([(4, 26), (6, 17), (14, 7), (24, 4), (27, 8), (23, 17), (13, 24)], fill="#7dbdbc", outline=P["ink"])
        d.polygon([(11, 18), (17, 9), (24, 5), (25, 10), (20, 15)], fill="#e2f0db")
        d.line((5, 27, 21, 10), fill=P["paper"], width=2)
        d.line((13, 19, 7, 17), fill=P["tide_light"]); d.line((18, 14, 16, 10), fill=P["tide_light"])
    elif name == "withered_core":
        d.polygon([(16, 3), (24, 8), (27, 18), (22, 27), (11, 29), (4, 21), (6, 10)], fill="#55494b", outline=P["ink"])
        d.line((16, 7, 16, 23, 10, 26), fill="#ae947f", width=2)
        d.line((16, 16, 23, 11), fill="#ae947f", width=2)
        mark(d, (11, 12, 20, 21), P["ember"])
        mark(d, (14, 14, 17, 17), P["ember_light"])
    elif name == "desk":
        d.rectangle((3, 12, 29, 25), fill=P["wood"], outline=P["ink"])
        d.rectangle((5, 9, 27, 14), fill=P["wood_light"], outline=P["brass"])
        d.line((7, 26, 7, 30), fill=P["wood_light"], width=3); d.line((26, 26, 26, 30), fill=P["wood_light"], width=3)
        d.polygon([(7, 8), (13, 7), (16, 9), (19, 7), (25, 8), (25, 17), (17, 15), (8, 17)], fill=P["paper"])
        d.line((16, 9, 16, 16), fill=P["paper_shadow"])
        mark(d, (18, 16, 25, 23), P["brass"])
    elif name == "distiller":
        d.rectangle((5, 15, 25, 28), fill=P["wood"], outline=P["brass"])
        d.rectangle((11, 20, 19, 27), fill=P["ember"])
        d.polygon([(10, 10), (21, 10), (24, 17), (8, 17)], fill=P["glass"], outline=P["ink"])
        d.line((16, 10, 16, 4, 25, 4, 25, 13), fill=P["brass"], width=2)
        d.rectangle((22, 14, 29, 24), fill=P["tide_light"], outline=P["ink"])
    elif name == "jar":
        d.rectangle((7, 6, 25, 28), fill="#a9d0d1", outline=P["ink"])
        d.rectangle((9, 19, 23, 26), fill=P["tide"], outline=P["tide_light"])
        d.rectangle((10, 3, 22, 8), fill=P["wood_light"], outline=P["brass"])
        d.rectangle((11, 11, 20, 15), fill=P["paper"], outline=P["paper_shadow"])
    elif name == "journal":
        d.polygon([(5, 7), (14, 5), (16, 8), (18, 5), (27, 7), (27, 26), (17, 24), (15, 27), (5, 26)], fill=P["paper"], outline=P["wood"])
        d.line((16, 8, 16, 25), fill=P["paper_shadow"], width=2)
        d.arc((9, 10, 22, 22), 0, 355, fill=P["brass"], width=1)
        d.point((16, 15), fill=P["ember"])
    elif name == "return":
        d.polygon([(6, 5), (25, 5), (25, 27), (6, 27)], fill=P["wood"], outline=P["brass"])
        d.polygon([(10, 14), (17, 8), (17, 12), (22, 12), (22, 19), (17, 19), (17, 23)], fill=P["paper"])
    elif name == "sealed":
        d.arc((3, 3, 28, 28), 0, 355, fill=P["brass"] , width=3)
        d.line((16, 3, 16, 29), fill=P["brass_dark"], width=2)
        d.ellipse((11, 11, 20, 20), fill=P["stone"], outline=P["paper"])
    else:
        raise ValueError(name)
    bounds = im.getbbox()
    if bounds:
        cropped = im.crop(bounds)
        factor = min(28 / cropped.width, 28 / cropped.height)
        size = (max(1, round(cropped.width * factor)), max(1, round(cropped.height * factor)))
        cropped = cropped.resize(size, Image.Resampling.NEAREST)
        im = Image.new("RGBA", (32, 32))
        im.alpha_composite(cropped, ((32 - size[0]) // 2, (32 - size[1]) // 2))
    return im


def textures() -> None:
    rng = random.Random(1409)
    base = {
        "dark_oak": (62, 43, 37), "oak_edge": (110, 70, 50), "brass": (178, 122, 60),
        "brass_dark": (107, 77, 48),
        "brass_edge": (221, 174, 102), "parchment": (223, 208, 171),
        "paper_shadow": (180, 155, 117), "ink": (25, 30, 36),
        "copper": (156, 87, 63), "glass": (201, 222, 217), "glass_edge": (232, 242, 228),
        "ember": (225, 112, 58), "tide": (66, 169, 170), "gale": (151, 207, 220),
        "stone": (185, 194, 185), "fire": (252, 166, 74), "cloth": (40, 59, 71),
    }
    for name, rgb in base.items():
        im = Image.new("RGBA", (16, 16))
        pix = im.load()
        for y in range(16):
            for x in range(16):
                jitter = rng.choice((-10, -6, -3, 0, 0, 2, 4, 8))
                if name in ("dark_oak", "oak_edge", "copper"):
                    jitter += (-7 if x % 5 == 0 else 0) + (3 if y % 6 == 0 else 0)
                if name.startswith("glass"):
                    alpha = 118 if name == "glass" else 205
                else:
                    alpha = 255
                pix[x, y] = tuple(max(0, min(255, c + jitter)) for c in rgb) + (alpha,)
        save(im, ASSET / f"textures/item/{MAGIC}/model/{name}.png")


def model(name: str, boxes: list[tuple[tuple[float, ...], str]], gui_scale: float = 1.0) -> None:
    elements = []
    for bounds, texture in boxes:
        x1, y1, z1, x2, y2, z2 = bounds
        assert all(0 <= n <= 16 for n in bounds) and x1 < x2 and y1 < y2 and z1 < z2
        elements.append({"from": [x1, y1, z1], "to": [x2, y2, z2],
                         "faces": {face: {"texture": f"#{texture}"} for face in
                                   ("north", "south", "east", "west", "up", "down")}})
    used = sorted({tex for _, tex in boxes})
    for tex in used:
        assert (ASSET / f"textures/item/{MAGIC}/model/{tex}.png").is_file(), f"Model {name} lacks {tex} texture"
    write_json(ASSET / f"models/{MAGIC}/{name}.json", {
        "textures": {tex: f"projects:item/{MAGIC}/model/{tex}" for tex in used},
        "elements": elements,
        "display": {"gui": {"rotation": [25, 40, 0], "translation": [0, -1, 0], "scale": [gui_scale] * 3},
                    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
    })
    write_json(ASSET / f"items/{MAGIC}/{name}.json", {"model": {"type": "minecraft:model", "model": f"projects:{MAGIC}/{name}"}})


def box(x1, y1, z1, x2, y2, z2, texture):
    return ((x1, y1, z1, x2, y2, z2), texture)


def models() -> None:
    b = box
    desk = [
        b(1, 0, 1, 3, 8, 3, "dark_oak"), b(13, 0, 1, 15, 8, 3, "dark_oak"),
        b(1, 0, 13, 3, 8, 15, "dark_oak"), b(13, 0, 13, 15, 8, 15, "dark_oak"),
        b(0, 7, 0, 16, 9, 16, "dark_oak"), b(0, 9, 0, 16, 10, 16, "oak_edge"),
        b(0, 7, 0, 1, 10, 16, "brass"), b(15, 7, 0, 16, 10, 16, "brass"),
        b(1, 7, 0, 15, 10, 1, "brass"), b(1, 7, 15, 15, 10, 16, "brass"),
        b(2, 2, 1, 14, 3, 2, "oak_edge"), b(2, 2, 14, 14, 3, 15, "oak_edge"),
        b(1, 10, 2, 7, 10.4, 10, "dark_oak"),
        b(1.2, 10.4, 2.3, 3.9, 11.2, 9.6, "parchment"),
        b(4.1, 10.4, 2.3, 6.8, 11.2, 9.6, "parchment"),
        b(3.8, 11.2, 2.3, 4.2, 11.4, 9.6, "paper_shadow"),
        b(1.6, 11.2, 4, 3.4, 11.3, 4.3, "ink"), b(1.6, 11.2, 6.2, 3.4, 11.3, 6.5, "ink"),
        b(4.5, 11.2, 4, 6.2, 11.3, 4.3, "ink"), b(4.5, 11.2, 6.2, 6.2, 11.3, 6.5, "ink"),
        b(7, 10, 3, 14, 10.7, 12, "brass"), b(8, 10.7, 4, 13, 11, 11, "ink"),
        b(9, 11, 5, 12, 11.25, 10, "brass_edge"), b(10, 11.25, 6, 11, 11.45, 9, "gale"),
        b(2, 10, 12, 4, 11.5, 14, "ink"), b(3, 11.5, 12.7, 3.4, 15, 13.2, "glass_edge"),
        b(14, 10, 12, 15, 12, 14, "brass_edge"),
    ]
    model("research_desk", desk, .8)
    dormant = [p for p in desk if p[1] not in ("gale",)] + [b(10, 11.25, 6, 11, 11.45, 9, "stone")]
    model("research_desk_dormant", dormant, .8)
    dist = [
        b(1, 0, 1, 15, 2, 15, "dark_oak"), b(2, 2, 2, 14, 9, 14, "copper"),
        b(3, 3, 1, 13, 8, 2, "brass"), b(5, 4, .8, 11, 7, 1.2, "ink"),
        b(6, 4.1, .65, 10, 6.8, .85, "fire"), b(2, 9, 2, 14, 10, 14, "brass_edge"),
        b(4, 10, 4, 11, 11, 11, "glass_edge"), b(5, 11, 5, 10, 14, 10, "glass"),
        b(6, 11, 6, 9, 12, 9, "tide"), b(6.4, 14, 6.4, 8.6, 15, 8.6, "copper"),
        b(7, 15, 7, 8, 16, 8, "brass"), b(8, 14.5, 7, 14, 15.2, 8, "copper"),
        b(13, 11, 7, 14, 15, 8, "copper"), b(11, 9, 9, 15, 10, 15, "brass"),
        b(12, 10, 10, 15, 13, 14, "glass"), b(12, 13, 10, 15, 13.7, 14, "brass_edge"),
        b(1, 2, 1, 2, 10, 2, "brass_dark"), b(14, 2, 1, 15, 10, 2, "brass_dark"),
    ]
    model("crude_distiller", dist, .75)
    shelf = [
        b(0, 0, 1, 2, 16, 15, "dark_oak"), b(14, 0, 1, 16, 16, 15, "dark_oak"),
        b(0, 0, 0, 16, 2, 16, "oak_edge"), b(0, 8, 0, 16, 9.5, 16, "dark_oak"),
        b(0, 14, 0, 16, 16, 16, "oak_edge"), b(1, 1, 14, 15, 15, 16, "dark_oak"),
        b(0, 0, 0, 16, 1, 1, "brass_dark"), b(0, 8, 0, 16, 8.5, 1, "brass"),
        b(0, 14, 0, 16, 15, 1, "brass"),
    ]
    model("jar_shelf", shelf, .7)
    chart = [
        b(0, 0, 14, 16, 16, 15, "dark_oak"), b(1, 1, 13.7, 15, 15, 14, "cloth"),
        b(0, 0, 13, 16, 1, 16, "brass"), b(0, 15, 13, 16, 16, 16, "brass"),
        b(0, 1, 13, 1, 15, 16, "brass"), b(15, 1, 13, 16, 15, 16, "brass"),
        b(2, 8, 13.4, 14, 8.25, 13.6, "brass_dark"),
        b(8, 2, 13.4, 8.25, 14, 13.6, "brass_dark"),
        b(3, 4, 13.25, 3.7, 4.7, 13.5, "brass_edge"),
        b(5, 11, 13.25, 5.7, 11.7, 13.5, "brass_edge"),
        b(8, 7.7, 13.2, 8.7, 8.4, 13.5, "brass_edge"),
        b(11, 5, 13.25, 11.7, 5.7, 13.5, "brass_edge"),
        b(13, 12, 13.25, 13.7, 12.7, 13.5, "brass_edge"),
    ]
    model("star_chart", chart, .7)
    for aspect in ("ember", "tide", "gale", "stone"):
        for level in ("empty", "low", "high"):
            liquid = [] if level == "empty" else [b(4, 2, 4, 12, 5 if level == "low" else 11, 12, aspect)]
            jar = [b(3, 0, 3, 13, 2, 13, "brass"), b(3, 2, 3, 13, 13, 13, "glass"),
                   *liquid, b(3, 12, 3, 13, 13, 13, "glass_edge"),
                   b(4, 13, 4, 12, 15, 12, "dark_oak"), b(5, 15, 5, 11, 16, 11, "brass"),
                   b(2.5, 6, 2.8, 13.5, 9, 3.2, "parchment"),
                   b(6, 7, 2.6, 10, 7.6, 2.8, aspect)]
            model(f"jar_{aspect}_{level}", jar, .75)


def item_icons() -> None:
    names = ("ember", "tide", "gale", "stone", "moonbell", "ember_moss", "hollow_crystal",
             "warm_ore", "tidewing_feather", "withered_core", "desk", "distiller", "jar",
             "journal", "return", "sealed")
    for name in names:
        save(icon(name), ASSET / f"textures/item/{MAGIC}/{name}.png")
        write_json(ASSET / f"models/{MAGIC}/icon_{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"projects:item/{MAGIC}/{name}"}})
        write_json(ASSET / f"items/{MAGIC}/icon_{name}.json", {
            "model": {"type": "minecraft:model", "model": f"projects:{MAGIC}/icon_{name}"}})
    sheet = Image.new("RGBA", (8 * 96, 2 * 96), "#23292b")
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        x, y = index % 8 * 96, index // 8 * 96
        sheet.alpha_composite(icon(name).resize((64, 64), Image.Resampling.NEAREST), (x + 16, y))
        draw.text((x + 3, y + 72), name, fill="#e7dac0")
    save(sheet, ROOT / "assets/first-magic/icon-sheet.png")


def menu_canvas() -> None:
    # Same 384 x 222 title origin and slot grid as CoreMenuCanvas. The central 176 px
    # remains clickable; engraved side folios hold observations and next steps.
    im = Image.new("RGBA", (384, 222), (19, 24, 27, 248))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 383, 221), outline="#9e7652", width=2)
    d.rectangle((3, 3, 380, 218), outline="#433d34")
    for left, right in ((5, 101), (105, 278), (282, 378)):
        d.rectangle((left, 5, right, 124), fill="#243039", outline="#765b40", width=1)
        d.line((left + 6, 24, right - 6, 24), fill="#ad8855")
        d.rectangle((left, 130, right, 216), fill="#272a2c", outline="#544b3e", width=1)
    # Dedicated observation dial and restrained star coordinate marks.
    d.ellipse((21, 145, 84, 208), outline="#796a51", width=1)
    d.ellipse((29, 153, 76, 200), outline="#4e7680", width=1)
    d.ellipse((312, 145, 375, 208), outline="#796a51", width=1)
    for cx in (52, 344):
        d.line((cx, 148, cx, 205), fill="#555c59")
        d.line((cx - 28, 176, cx + 28, 176), fill="#555c59")
        d.ellipse((cx - 3, 173, cx + 3, 179), fill="#bd8b4f")
    # Slot outlines are quiet but unmistakably interactive at native 1x pixels.
    for row in range(6):
        for col in range(9):
            x, y = 112 + col * 18, 18 + row * 18
            d.rectangle((x, y, x + 16, y + 16), fill="#283338", outline="#4c5553")
            d.line((x + 2, y + 2, x + 14, y + 2), fill="#36484a")
    for x in (102, 280):
        d.line((x, 4, x, 216), fill="#ad8855", width=2)
    for x, y in ((8, 8), (372, 8), (8, 210), (372, 210)):
        d.line((x, y + 6, x, y, x + 6, y), fill="#d1ab6b", width=2)
    def folio(page: str, frame: Image.Image) -> None:
        q = ImageDraw.Draw(frame)
        if page == "desk":
            for radius, color in ((37, "#8d6946"), (30, "#ba8a55"), (19, "#55777a")):
                q.ellipse((192-radius, 175-radius, 192+radius, 175+radius), outline=color)
            q.line((192, 139, 192, 211), fill="#886f50")
            q.line((156, 175, 228, 175), fill="#886f50")
            for x, y, color in ((192, 140, P["ember"]), (226, 175, P["tide"]),
                                (192, 209, P["gale"]), (158, 175, P["stone_aspect"])):
                q.ellipse((x-3, y-3, x+3, y+3), fill=color, outline=P["brass_dark"])
            q.ellipse((187, 170, 197, 180), fill=P["brass"], outline=P["brass_light"])
            for x, y in ((178, 150), (207, 158), (173, 194), (214, 193)):
                q.point((x, y), fill=P["paper"])
        elif page == "distiller":
            q.rectangle((126, 170, 156, 205), fill="#5b3931", outline="#bd8b4f", width=2)
            q.rectangle((135, 185, 148, 200), fill="#d8793f", outline="#f4ba68")
            q.polygon([(157, 169), (163, 148), (186, 148), (194, 169)], fill="#6caaa9", outline="#cde7dc")
            q.line((173, 148, 173, 141, 221, 141, 221, 171), fill="#c9905f", width=3)
            q.rectangle((218, 170, 252, 202), fill="#446f76", outline="#bcd8d3", width=2)
            q.rectangle((221, 189, 249, 199), fill="#57bbb6")
            for x, y in ((167, 160), (183, 162), (228, 181)):
                q.ellipse((x, y, x+2, y+2), fill="#d9f3dc")
        elif page == "jars":
            for i, liquid in enumerate((P["ember"], P["tide"], P["gale"], P["stone_aspect"])):
                x = 120 + i * 38
                q.rectangle((x+3, 160, x+28, 201), fill="#5b7d80", outline="#c0d8d5", width=2)
                q.rectangle((x+6, 184, x+25, 198), fill=liquid)
                q.rectangle((x+6, 153, x+25, 161), fill=P["wood_light"], outline=P["brass"])
                q.rectangle((x+7, 169, x+24, 177), fill=P["paper"], outline=P["paper_shadow"])
                q.ellipse((x+14, 171, x+18, 175), fill=liquid)
            q.line((118, 207, 268, 207), fill=P["brass"] , width=2)
        else:
            q.polygon([(130, 153), (176, 149), (192, 156), (207, 149), (253, 153), (253, 201),
                       (207, 197), (192, 203), (176, 197), (130, 201)], fill="#d9c8a0", outline="#8d6946")
            q.line((192, 157, 192, 202), fill="#816b53", width=2)
            for y in (162, 169, 176, 183, 190):
                q.line((139, y, 177, y-2), fill="#aa9470")
                q.line((207, y-2, 244, y), fill="#aa9470")
            q.ellipse((181, 168, 202, 189), outline=P["brass"], width=2)
            q.ellipse((188, 175, 195, 182), fill=P["tide"])

    for page in ("desk", "distiller", "jars", "journal"):
        frame = im.copy(); folio(page, frame)
        for side in (0, 1):
            save(frame.crop((side*192, 0, (side+1)*192, 222)),
                 ASSET / f"textures/gui/first_magic/{page}_{side}.png")
        write_json(ASSET / f"font/first_magic_canvas_{page}.json", {"providers": [
            {"type": "bitmap", "file": f"projects:gui/first_magic/{page}_0.png", "height": 222,
             "ascent": 13, "chars": ["\ue600"]},
            {"type": "bitmap", "file": f"projects:gui/first_magic/{page}_1.png", "height": 222,
             "ascent": 13, "chars": ["\ue601"]},
        ]})


def preview() -> None:
    """Review sheet at native 1x size; never loaded as the in-game UI."""
    font_file = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"
    if not font_file.is_file():
        return
    left = Image.open(ASSET / "textures/gui/first_magic/desk_0.png").convert("RGBA")
    right = Image.open(ASSET / "textures/gui/first_magic/desk_1.png").convert("RGBA")
    im = Image.new("RGBA", (384, 222), "#171d21")
    im.alpha_composite(left, (0, 0)); im.alpha_composite(right, (192, 0))
    d = ImageDraw.Draw(im)
    font = ImageFont.truetype(str(font_file), 11)
    def label(x, y, words, fill="#e5d4b6"):
        d.text((x, y - 1), words, font=font, fill=fill, stroke_width=0)
    label(112, 5, "観測工房・研究机")
    label(6, 7, "観測手順"); label(288, 7, "古い観測工房")
    for i, words in enumerate(("01 素材を持ち帰る", "02 観測盤を修復", "03 素材を分析", "04 記録帳へ残す", "", "素材 2 / 6", "性質 2 / 4")):
        label(6, 30 + 14 * i, words, "#d5caba")
    for i, words in enumerate(("観測盤は起動中", "", "紙上の小さな星図は", "持ち帰った未知に応える", "", "素材を選ぶと分析", "素材は消費しない")):
        label(288, 30 + 14 * i, words, "#d5caba")
    icon_slots = {4: "journal", 10: "moonbell", 12: "ember_moss", 14: "hollow_crystal",
                  22: "desk", 28: "warm_ore", 30: "tidewing_feather", 32: "withered_core",
                  37: "sealed", 38: "tide", 40: "journal", 42: "gale", 43: "sealed",
                  49: "distiller", 53: "return"}
    for slot, name in icon_slots.items():
        x, y = 112 + slot % 9 * 18, 18 + slot // 9 * 18
        small = icon(name).resize((16, 16), Image.Resampling.NEAREST)
        im.alpha_composite(small, (x, y))
    save(im, ROOT / "assets/first-magic/workshop-screen-preview.png")


def build() -> None:
    textures(); models(); item_icons(); menu_canvas(); preview()
    paths = sorted(str(path.relative_to(PACK)).replace("\\", "/") for path in PACK.rglob("*") if path.is_file() and path.name != "index.txt")
    (PACK / "index.txt").write_text("\n".join(paths) + "\n", encoding="utf-8")
    print(f"First magic: {len(list((ASSET / 'items/first_magic').glob('*.json')))} item models, {len(paths)} packed files")


if __name__ == "__main__":
    build()
