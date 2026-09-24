"""Detailed, original pixel textures and cuboid furniture for the observatory workshop.

The four workshop objects share a material library, so their wood, copper and
brass have the same visual weight in the world. All coordinates are 1/16 block.
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw


MATERIALS = {
    "oak": (60, 37, 29), "oak_light": (91, 54, 35), "oak_edge": (40, 28, 26),
    "brass": (164, 107, 45), "brass_light": (230, 177, 83), "brass_dark": (94, 59, 34),
    "copper": (146, 71, 49), "copper_light": (210, 119, 73),
    "paper": (228, 210, 167), "paper_edge": (156, 123, 83), "ink": (26, 30, 37),
    "cloth": (28, 42, 66), "cloth_light": (44, 62, 89),
    "glass": (186, 225, 222), "glass_edge": (226, 241, 228),
    "fire": (237, 111, 42), "stone": (104, 105, 103),
    "ember": (216, 95, 44), "tide": (38, 170, 157),
    "gale": (146, 203, 219), "stone_essence": (205, 206, 190),
}


def _png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def _texture(name: str, color: tuple[int, int, int]) -> Image.Image:
    rng = random.Random(9000 + sum(ord(ch) for ch in name))
    im = Image.new("RGBA", (32, 32), color + (255,))
    d = ImageDraw.Draw(im)
    def mul(rgb, v):
        return tuple(max(0, min(255, c + v)) for c in rgb) + (255,)
    if name.startswith("oak"):
        # Long irregular grain, warm edges, and nail-sized end marks.
        for x in (2, 7, 13, 18, 24, 29):
            v = rng.choice((-22, -15, 12, 18))
            d.line((x, 0, x + rng.choice((-1, 0, 1)), 31), fill=mul(color, v), width=rng.choice((1, 1, 2)))
        for y in (6, 17, 26):
            x = rng.randrange(3, 26)
            d.line((x, y, x + rng.randrange(2, 5), y), fill=mul(color, 21))
            d.point((x + 1, y + 1), fill=mul(color, -20))
    elif name.startswith("brass") or name.startswith("copper"):
        for y in range(2, 32, 7):
            for x in range(1 + (y // 7 % 2) * 3, 32, 8):
                v = rng.choice((-17, -11, 12, 21))
                d.rectangle((x, y, x + 2, y + 1), fill=mul(color, v))
                if name.startswith("copper"):
                    d.point((x + 3, y + 3), fill=mul(color, -25))
        d.line((0, 0, 31, 0), fill=mul(color, 29))
        d.line((0, 31, 31, 31), fill=mul(color, -29))
    elif name == "paper":
        for y in (4, 11, 18, 25):
            d.line((2, y, 28, y), fill=mul(color, 3))
        for _ in range(18):
            x, y = rng.randrange(32), rng.randrange(32)
            d.point((x, y), fill=mul(color, rng.choice((-11, -6, 7))))
    elif name == "cloth" or name == "cloth_light":
        for x in range(0, 32, 4):
            d.line((x, 0, x, 31), fill=mul(color, 7))
        for y in range(0, 32, 5):
            d.line((0, y, 31, y), fill=mul(color, -5))
    elif name == "glass":
        im.putalpha(100)
        d = ImageDraw.Draw(im)
        d.line((0, 0, 0, 31), fill=(236, 251, 244, 210), width=3)
        d.line((4, 1, 4, 26), fill=(235, 251, 242, 85))
        d.line((31, 0, 31, 31), fill=(94, 164, 174, 150), width=3)
        d.line((0, 30, 31, 30), fill=(110, 180, 188, 130), width=2)
    elif name == "glass_edge":
        im.putalpha(165)
        d = ImageDraw.Draw(im)
        d.line((0, 0, 0, 31), fill=(250, 255, 244, 220), width=3)
    elif name == "fire":
        im = Image.new("RGBA", (32, 32), (34, 24, 24, 255))
        d = ImageDraw.Draw(im)
        for x, h in ((3, 12), (10, 21), (17, 15), (24, 25)):
            d.polygon(((x, 30), (x - 2, 22), (x + 2, 30), (x + 4, 30), (x + 3, 30 - h), (x + 8, 30)), fill="#d65327")
            d.polygon(((x + 2, 30), (x + 3, 22), (x + 5, 29)), fill="#ffd26c")
    elif name in ("ember", "tide", "gale", "stone_essence"):
        for y in (3, 8, 14, 21, 27):
            d.line((2, y, 28, y), fill=mul(color, 13))
            for x in (5, 16, 25):
                d.point((x + y % 4, y - 2), fill=mul(color, 50))
    return im


def _dial(active: bool) -> Image.Image:
    im = Image.new("RGBA", (64, 64), "#17243a" if active else "#242c32")
    d = ImageDraw.Draw(im)
    gold = "#e4b965" if active else "#7f755e"
    muted = "#836b46" if active else "#606261"
    for margin, width in ((3, 2), (7, 1), (17, 1), (25, 1)):
        d.ellipse((margin, margin, 63-margin, 63-margin), outline=gold if margin == 3 else muted, width=width)
    for a in range(0, 360, 15):
        rad = math.radians(a)
        cx, cy = 31.5, 31.5
        r1, r2 = (20, 26) if a % 45 == 0 else (23, 26)
        d.line((cx + math.cos(rad)*r1, cy + math.sin(rad)*r1,
                cx + math.cos(rad)*r2, cy + math.sin(rad)*r2), fill=gold if a % 45 == 0 else muted, width=1)
    for a in (0, 45, 90, 135):
        rad = math.radians(a)
        d.line((31.5-math.cos(rad)*21, 31.5-math.sin(rad)*21,
                31.5+math.cos(rad)*21, 31.5+math.sin(rad)*21), fill=muted)
    for x, y in ((12, 15), (51, 18), (15, 49), (49, 51)):
        d.rectangle((x, y, x+2, y+2), fill=gold)
    d.ellipse((29, 29, 35, 35), fill="#75cad0" if active else "#8a8779", outline=gold)
    return im


def _page(right: bool) -> Image.Image:
    im = Image.new("RGBA", (32, 32), "#ead9b4")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 31, 31), outline="#ad8a5c", width=1)
    spine_x = 1 if right else 30
    d.line((spine_x, 1, spine_x, 30), fill="#a9885c", width=2)
    if right:
        for y in (6, 9, 13, 16, 20, 24):
            d.line((6, y, 26 - (y % 3)*2, y), fill="#6e6c65", width=1)
        d.ellipse((13, 17, 23, 27), outline="#a57639", width=1)
        d.ellipse((17, 21, 19, 23), fill="#417b86")
    else:
        d.ellipse((6, 6, 24, 24), outline="#ab793b", width=2)
        d.line((15, 5, 15, 25), fill="#ad793e")
        d.line((5, 15, 25, 15), fill="#ad793e")
        for x, y in ((9, 8), (20, 11), (11, 21), (20, 20)):
            d.rectangle((x, y, x+1, y+1), fill="#47687e")
        d.line((5, 28, 27, 28), fill="#8f8878")
    return im


def _chart() -> Image.Image:
    im = Image.new("RGBA", (64, 64), "#1c2b46")
    d = ImageDraw.Draw(im)
    for r in (9, 17, 25, 30):
        d.ellipse((32-r, 32-r, 32+r, 32+r), outline="#735938", width=1)
    for a in range(0, 360, 30):
        rad = math.radians(a)
        d.line((32, 32, 32+math.cos(rad)*30, 32+math.sin(rad)*30), fill="#584938")
    for x, y in ((13, 11), (20, 30), (47, 17), (44, 44), (22, 51), (32, 32), (56, 52)):
        d.rectangle((x-1, y-1, x+1, y+1), fill="#d7a45b")
        d.line((x-3, y, x+3, y), fill="#d7a45b")
        d.line((x, y-3, x, y+3), fill="#d7a45b")
    d.line((20, 30, 32, 32, 44, 44, 47, 17), fill="#ac7744")
    return im


def build_textures(asset: Path) -> None:
    directory = asset / "textures/item/first_magic/model"
    for name, color in MATERIALS.items():
        _png(_texture(name, color), directory / f"{name}.png")
    for name, im in {"dial_active": _dial(True), "dial_dormant": _dial(False),
                     "book_left": _page(False), "book_right": _page(True),
                     "star_chart": _chart()}.items():
        _png(im, directory / f"{name}.png")


def _b(x1, y1, z1, x2, y2, z2, tex: str, top: str | None = None):
    assert 0 <= x1 < x2 <= 16 and 0 <= y1 < y2 <= 16 and 0 <= z1 < z2 <= 16, (x1,y1,z1,x2,y2,z2)
    return (x1,y1,z1,x2,y2,z2,tex,top)


def _write_model(asset: Path, write_json, name: str, boxes: list[tuple], scale: float = 0.8) -> None:
    elements = []
    used = set()
    for x1,y1,z1,x2,y2,z2,tex,top in boxes:
        used.add(tex)
        if top: used.add(top)
        faces = {side: {"texture": f"#{tex}"} for side in ("north","south","east","west","up","down")}
        if top: faces["up"] = {"texture": f"#{top}", "uv": [0,0,16,16]}
        elements.append({"from": [x1,y1,z1], "to": [x2,y2,z2], "faces": faces})
    write_json(asset / f"models/first_magic/{name}.json", {
        "textures": {t: f"projects:item/first_magic/model/{t}" for t in sorted(used)},
        "elements": elements,
        "display": {"gui": {"rotation": [25,40,0], "translation": [0,-1,0], "scale": [scale]*3},
                    "fixed": {"rotation": [0,0,0], "translation": [0,0,0], "scale": [1,1,1]}}
    })
    write_json(asset / f"items/first_magic/{name}.json", {
        "model": {"type": "minecraft:model", "model": f"projects:first_magic/{name}"}})


def build_models(asset: Path, write_json) -> None:
    b = _b
    desk = []
    # Four carved legs, stretcher rails, a deep apron and an oak tabletop.
    for x in (0.7, 13.3):
        for z in (0.7, 13.3):
            desk += [b(x,0,z,x+2,9,z+2,"oak"), b(x-.1,0,z-.1,x+2.1,1,z+2.1,"brass_dark"),
                     b(x-.1,7.3,z-.1,x+2.1,9.8,z+2.1,"brass")]
    desk += [b(2.5,2,1.4,13.5,3,2.3,"oak_light"), b(2.5,2,13.7,13.5,3,14.6,"oak_light"),
             b(1.5,2,2.5,2.4,3,13.5,"oak_light"), b(13.6,2,2.5,14.5,3,13.5,"oak_light"),
             b(0,8.1,0,16,9.7,16,"oak"), b(0,9.7,0,16,10.2,16,"oak_light")]
    for x in (0,15.3):
        desk += [b(x,8.1,0,x+.7,10.3,16,"brass_dark")]
    for z in (0,15.3):
        desk += [b(.7,8.1,z,15.3,10.3,z+.7,"brass_dark")]
    for x,z in ((.2,.2),(14.5,.2),(.2,14.5),(14.5,14.5)):
        desk += [b(x,9.7,z,x+1.3,10.5,z+1.3,"brass_light"), b(x+.5,10.5,z+.5,x+.8,10.7,z+.8,"brass_dark")]
    # Drawers and their tiny brass pulls face the player (north side).
    for x in (2,11):
        desk += [b(x,3.4,.3,x+3,7.5,1.1,"oak_edge"), b(x+.25,3.7,.18,x+2.75,7.1,.35,"oak_light"),
                 b(x+1.15,5,.04,x+1.85,5.65,.18,"brass_light")]
    # Observatory cloth falls over the front edge.
    desk += [b(8.4,10.2,.1,14.5,10.3,7,"cloth"), b(8.4,4.4,.03,14.5,10.3,.15,"cloth"),
             b(8.7,4.5,0,8.95,10.2,.1,"brass"), b(14,4.5,0,14.25,10.2,.1,"brass")]
    for x,y in ((11.2,7.1),(10,5.4),(12.5,5.4)):
        desk += [b(x,y,0,x+.5,y+.5,.06,"brass_light")]
    # Open codex: dark cover, stacked page edges, engraved drawings on both pages.
    desk += [b(1.2,10.2,3.1,7.6,10.55,10.8,"oak_edge"),
             b(1.5,10.55,3.4,4.3,11.08,10.4,"paper_edge"),
             b(4.4,10.55,3.4,7.25,11.08,10.4,"paper_edge"),
             b(1.65,11.08,3.55,4.2,11.17,10.25,"paper","book_left"),
             b(4.5,11.08,3.55,7.1,11.17,10.25,"paper","book_right"),
             b(4.25,11.08,3.4,4.45,11.32,10.4,"brass_dark")]
    # Two tiny stacked volumes at the back left.
    for y,tex in ((10.2,"cloth"),(10.62,"oak_light")):
        desk += [b(1.4,y,11.2,5.1,y+.33,14.4,tex),b(1.7,y+.33,11.3,4.8,y+.47,14.3,"paper_edge")]
    # Stepped brass meridians form a small physical armillary at the back.
    desk += [b(2.3,11.1,11.8,4.5,11.4,14.0,"brass_dark"),
             b(3.3,11.4,12.8,3.6,12.15,13.1,"brass_light"),
             b(2.7,12.1,12.85,4.1,12.32,13.15,"brass"),
             b(2.25,12.5,12.85,2.48,14.7,13.15,"brass_light"),
             b(4.32,12.5,12.85,4.55,14.7,13.15,"brass_light"),
             b(2.55,14.65,12.85,4.25,14.88,13.15,"brass_light"),
             b(2.55,12.35,12.85,4.25,12.55,13.15,"brass_light"),
             b(3.35,12.35,12.85,3.55,14.85,13.15,"brass_dark"),
             b(2.4,13.48,12.72,4.45,13.68,12.9,"brass")]
    # Inkwell and a slender ivory quill, with nib and shaft separate.
    desk += [b(5.5,10.2,12.3,7,11.15,13.8,"ink"), b(5.7,11.15,12.5,6.8,11.4,13.6,"brass"),
             b(6.15,11.4,12.95,6.4,15.8,13.2,"paper"),
             b(6.2,13.6,12.7,6.5,15.8,13.45,"paper_edge")]
    # Astrolabe: raised dark disk, six sided brass perimeter, latitude rings,
    # etched dial top, central spindle, compass pointer, and a small sighting arm.
    desk += [b(7.7,10.2,2.7,15.35,10.55,11.55,"brass_dark"),
             b(8.1,10.55,3.05,15,10.85,11.2,"brass"),
             b(8.65,10.85,3.6,14.45,10.98,10.65,"ink","dial_active")]
    for x1,z1,x2,z2 in ((8.1,3.05,15,3.45),(8.1,10.8,15,11.2),(8.1,3.45,8.5,10.8),(14.6,3.45,15,10.8)):
        desk += [b(x1,10.85,z1,x2,11.11,z2,"brass_light")]
    desk += [b(11.35,10.98,6.75,11.65,12.4,7.05,"brass_light"),
             b(11.1,12.4,6.5,11.9,12.85,7.3,"brass_light"),
             b(11.5,11.14,7.1,14.1,11.29,7.32,"brass_light"),
             b(13.95,11.16,6.95,14.3,11.45,7.45,"brass")]
    for x,z in ((8.5,3.5),(14.4,3.5),(8.5,10.6),(14.4,10.6)):
        desk += [b(x,11.08,z,x+.3,11.6,z+.3,"brass_light")]
    # Lantern at rear: recognizable cage, base, light core and roof.
    desk += [b(13.5,10.2,12,15.1,10.5,14,"brass_dark"),
             b(13.8,10.5,12.3,14.8,12.45,13.7,"fire")]
    for x in (13.6,14.9):
        for z in (12.1,13.8):
            desk += [b(x,10.5,z,x+.13,12.5,z+.13,"brass_light")]
    desk += [b(13.5,12.45,12,15.1,12.8,14,"brass_dark"),
             b(13.9,12.8,12.4,14.7,13.2,13.6,"brass_light")]
    _write_model(asset,write_json,"research_desk",desk,.78)
    dormant = [(x1,y1,z1,x2,y2,z2,t,("dial_dormant" if top=="dial_active" else top))
               for x1,y1,z1,x2,y2,z2,t,top in desk]
    _write_model(asset,write_json,"research_desk_dormant",dormant,.78)

    dist = []
    # Braced stone furnace with an actual warm fire window.
    dist += [b(.5,0,.5,15.5,1.3,15.5,"oak_edge"), b(1.3,1.3,1.1,10.8,8.4,14.6,"stone")]
    for x in (1.1,9.8):
        for z in (1.1,13.6):
            dist += [b(x,1.2,z,x+1,9,z+1,"brass_dark")]
    dist += [b(2.7,3,.65,9.3,7.1,1.15,"brass"),
             b(3.3,3.5,.43,8.7,6.6,.65,"ink"),
             b(3.7,3.7,.38,8.3,6.4,.43,"fire"),
             b(1.1,8.2,1.1,10.8,9.1,14.6,"brass_dark"),
             b(1.4,9.1,1.4,10.5,9.5,14.2,"brass_light")]
    # Copper boiler: a shouldered vessel with stacked riveted rings and dark top.
    for coords,tex in [((3,9.5,3.2,9.4,10.2,11.8),"copper"),
                       ((2.5,10.2,2.8,9.9,12.2,12.2),"copper"),
                       ((3.1,12.2,3.3,9.3,13.2,11.7),"copper_light"),
                       ((4.3,13.2,4.2,8.1,14.6,10.8),"copper"),
                       ((4.9,14.6,4.8,7.5,15.1,10.2),"brass_dark"),
                       ((5.7,15.1,5.6,6.7,15.7,9.4),"brass_light")]:
        dist += [b(*coords,tex)]
    for x,z in ((2.7,3.3),(8.9,3.3),(2.7,11.5),(8.9,11.5)):
        dist += [b(x,11,z,x+.35,11.45,z+.35,"brass_light")]
    # Condenser arm arcs from the neck into a raised clear receiver. Distinct
    # horizontal and vertical sections read from the colony's approach.
    dist += [b(6.1,15.25,7.1,12.1,15.75,7.7,"copper_light"),
             b(11.85,12,7.1,12.45,15.75,7.7,"copper"),
             b(11.9,12,7.1,14.1,12.5,7.7,"copper_light"),
             b(13.6,10.9,7.1,14.15,12.5,7.7,"brass")]
    # Receiving shelf and glass vessel, with a visible early Tide drop.
    dist += [b(10.6,7.3,5.8,15.6,8.1,12.5,"oak"),
             b(11.3,8.1,6.2,15.1,8.5,11.7,"brass"),
             b(11.7,8.5,7,14.7,12.1,10.4,"glass"),
             b(12,8.6,7.3,14.4,9.8,10.1,"tide"),
             b(11.5,12.1,6.8,14.9,12.45,10.6,"glass_edge")]
    for x in (11.65,14.55):
        for z in (6.95,10.25):
            dist += [b(x,8.5,z,x+.16,12.2,z+.16,"glass_edge")]
    dist += [b(12.4,12.45,7.55,14.1,12.8,9.8,"brass_dark"),
             b(12.8,12.8,8,13.7,13.1,9.3,"brass_light")]
    _write_model(asset,write_json,"crude_distiller",dist,.72)

    shelf = []
    for x in (0,14.2):
        for z in (.5,13.7):
            shelf += [b(x,0,z,x+1.8,16,z+1.8,"oak"),
                      b(max(0,x-.1),7.75,max(0,z-.1),min(16,x+1.9),8.3,min(16,z+1.9),"brass_dark")]
    shelf += [b(.4,0.3,.4,15.6,1.6,15.6,"oak_edge"),
              b(.4,7.2,.4,15.6,8.2,15.6,"oak_light"),
              b(.4,14.6,.4,15.6,16,15.6,"oak_edge"),
              b(1.7,1.6,14.6,14.3,14.6,15.9,"oak")]
    for y in (1.3,7.9,14.7):
        shelf += [b(.4,y,.2,15.6,y+.35,.6,"brass"),
                  b(.4,y,15.4,15.6,y+.35,15.8,"brass_dark")]
    # Lower cubby has a few atlases and a sealed instruments box.
    for x,width,tex in ((2,1.1,"cloth"),(3.3,.7,"oak_light"),(4.3,1.3,"cloth_light")):
        shelf += [b(x,1.7,11.5,x+width,6.7,14.3,tex),
                  b(x,6.4,11.5,x+width,6.7,14.3,"brass_dark")]
    shelf += [b(9.5,1.7,10.8,13.3,5.6,14.3,"oak_edge"),
              b(9.8,5.6,10.9,13,5.9,14.2,"brass_dark"),
              b(11.25,3.3,10.7,11.7,3.8,10.9,"brass_light")]
    for x in (1.5,7.7,13.6):
        shelf += [b(x,8.2,.7,x+.2,8.6,2.6,"brass_light")]
    _write_model(asset,write_json,"jar_shelf",shelf,.7)

    chart = [b(0,0,14,16,16,15.8,"oak_edge"),
             b(1.2,1.2,13.85,14.8,14.8,14.1,"cloth","star_chart")]
    for x in (0,.55,15.1):
        chart += [b(x,0,13.6,x+.9,16,14.4,"brass_dark")]
    for y in (0,.55,15.1):
        chart += [b(0,y,13.6,16,y+.9,14.4,"brass")]
    for x,y in ((2,2),(13,2),(2,13),(13,13)):
        chart += [b(x,y,13.25,x+.7,y+.7,13.8,"brass_light")]
    _write_model(asset,write_json,"star_chart",chart,.72)
    chart_path = asset / "models/first_magic/star_chart.json"
    chart_json = json.loads(chart_path.read_text(encoding="utf-8"))
    for face in ("north", "south"):
        chart_json["elements"][1]["faces"][face] = {"texture":"#star_chart", "uv":[0,0,16,16]}
    write_json(chart_path, chart_json)

    for aspect in ("ember","tide","gale","stone"):
        tex = "stone_essence" if aspect == "stone" else aspect
        for level in ("empty","low","high"):
            jar = [b(3.2,0,3.2,12.8,1,12.8,"brass_dark"),
                   b(3.5,1,3.5,12.5,2,12.5,"brass"),
                   b(3.8,2,3.8,12.2,12.2,12.2,"glass"),
                   b(3.4,12.2,3.4,12.6,12.6,12.6,"glass_edge"),
                   b(4.2,12.6,4.2,11.8,13.35,11.8,"oak_edge"),
                   b(5,13.35,5,11,14.3,11,"brass"),
                   b(5.8,14.3,5.8,10.2,15,10.2,"oak_light"),
                   b(3.8,6,3.49,12.2,9.2,3.8,"paper"),
                   b(5.1,7.1,3.38,10.9,7.55,3.49,tex)]
            if level != "empty":
                liquid_y = 5 if level == "low" else 10.8
                jar += [b(4.2,2.1,4.2,11.8,liquid_y,11.8,tex),
                        b(4.2,liquid_y,4.2,11.8,liquid_y+.18,11.8,"glass_edge")]
                if aspect == "ember":
                    jar += [b(6,liquid_y+.2,6,6.6,liquid_y+.55,6.6,"brass_light"),
                            b(9,liquid_y+.3,9,9.35,liquid_y+.7,9.35,"fire")]
                elif aspect == "tide":
                    jar += [b(5.2,liquid_y+.35,6,5.65,liquid_y+.75,6.45,"glass_edge"),
                            b(9,liquid_y+.8,9,9.35,liquid_y+1.25,9.35,"glass_edge")]
                elif aspect == "gale":
                    jar += [b(5,liquid_y+.45,7,7.2,liquid_y+.62,7.2,"glass_edge"),
                            b(8.8,liquid_y+.85,8.5,10.6,liquid_y+1,8.7,"glass_edge")]
                else:
                    jar += [b(5,liquid_y+.18,5,7,liquid_y+.37,7,"stone_essence"),
                            b(8.5,liquid_y+.18,8.2,10.5,liquid_y+.37,10.2,"paper_edge")]
            for x in (3.55,12.24):
                for z in (3.55,12.24):
                    jar += [b(x,2,z,x+.2,12.2,z+.2,"glass_edge")]
            _write_model(asset,write_json,f"jar_{aspect}_{level}",jar,.75)


def build(asset: Path, write_json) -> None:
    build_textures(asset)
    build_models(asset, write_json)


def build_ui(root: Path, asset: Path, write_json) -> None:
    """A dark-oak, brass and manuscript surround for the native six-row grid."""
    rng = random.Random(2294)
    source = root / "assets/first-magic/source-sprites"
    for page in ("desk", "distiller", "jars", "journal"):
        im = Image.new("RGBA", (384, 222), "#201d1b")
        d = ImageDraw.Draw(im)
        # A carved timber chassis with eight iron/brass corner brackets.
        for i, c in ((0,"#241811"),(2,"#65432d"),(4,"#ad793e"),(5,"#332b27")):
            d.rectangle((i,i,383-i,221-i), outline=c, width=1)
        for y in range(8,215,9):
            d.line((5,y,9+rng.randrange(2,14),y), fill=rng.choice(("#4e3529","#5d412e","#79543b")))
            d.line((373-rng.randrange(2,14),y,378,y), fill="#553b2c")
        for x,y in ((6,6),(369,6),(6,207),(369,207)):
            d.rectangle((x,y,x+9,y+9), fill="#a8783d", outline="#d9b46c")
            d.rectangle((x+3,y+3,x+5,y+5), fill="#5b3d28")
        # The side folios are navy book cloth, with inset aged paper ruled for
        # text. The accepted light text stays legible on a deep manuscript ink.
        # CoreMenuCanvas anchors the left folio text at x=6. The ink field
        # reaches that pixel so no heading lands on the timber margin.
        for l,r in ((5,100),(283,379)):
            d.rectangle((l,10,r,126), fill="#192932", outline="#a97c45", width=1)
            d.rectangle((l+1,28,r-2,123), fill="#243038")
            for yy in range(29,123,3):
                d.line((l+3,yy,r-3,yy), fill="#283b43")
            d.rectangle((l+2,11,r-2,25), fill="#503823", outline="#89623c")
            d.line((l+6,25,r-6,25), fill="#d9ad67")
            for y in (31,120):
                d.rectangle((l+4,y,r-4,y), fill="#7c6244")
            d.rectangle((l,131,r,215), fill="#172a39", outline="#967347")
            d.rectangle((l+3,134,r-3,212), outline="#4c6070")
        # Central interactive field: felt mat in an oak frame, 54 honest slots.
        d.rectangle((102,10,281,126), fill="#4d3429", outline="#b5874e", width=2)
        d.rectangle((106,13,277,124), fill="#17272b")
        for y in range(14,124,7):
            d.line((107,y,276,y), fill="#1c3034")
        for row in range(6):
            for col in range(9):
                x,y = 112+col*18,18+row*18
                d.rectangle((x-1,y-1,x+16,y+16), fill="#0d1c22", outline="#735943")
                d.rectangle((x+1,y+1,x+14,y+14), fill="#263941")
                d.line((x+1,y+1,x+14,y+1), fill="#4d6667")
                d.line((x+1,y+1,x+1,y+14), fill="#4d6667")
                d.line((x+1,y+15,x+15,y+15), fill="#13242a")
        # Middle bench: distinct from the menu grid, a lit specimen plinth.
        d.rectangle((102,130,281,215), fill="#3b2a25", outline="#b4854e", width=2)
        d.rectangle((106,135,277,211), fill="#1a2a37", outline="#544733")
        for x in range(109,278,17):
            d.line((x,133,x+5,133), fill="#c99953")
        d.line((111,206,273,206), fill="#a37a48", width=2)
        # Fine etched observatory graduations give the corners a purpose.
        for cx in (54,330):
            d.ellipse((cx-31,144,cx+31,206), outline="#816744", width=2)
            d.ellipse((cx-23,152,cx+23,198), outline="#395a69", width=1)
            for a in range(0,360,30):
                r=math.radians(a)
                d.line((cx+math.cos(r)*27,175+math.sin(r)*27,
                        cx+math.cos(r)*30,175+math.sin(r)*30), fill="#d0a468")
            d.line((cx-17,175,cx+17,175), fill="#42606a")
            d.line((cx,158,cx,192), fill="#42606a")
            d.ellipse((cx-2,173,cx+2,177), fill="#d1a258")
        # One large authored pixel object makes each screen recognizably about
        # its equipment. This is decorative; the native slots stay click targets.
        art = {"desk":"desk", "distiller":"distiller", "jars":"jar", "journal":"journal"}[page]
        src_path = source / f"{art}.png"
        if src_path.is_file():
            src = Image.open(src_path).convert("RGBA")
            bb = src.getbbox()
            if bb:
                src = src.crop(bb)
                src.thumbnail((92,68),Image.Resampling.NEAREST)
                im.alpha_composite(src,(192-src.width//2,139+(68-src.height)//2))
        # Bottle gauges add a quiet repeated motif without covering text.
        for i,color in enumerate(("#e98a4f","#59bfc1","#a2cedd","#bdc5bd")):
            x=151+i*27
            d.rectangle((x,207,x+10,210),fill="#58422f")
            d.rectangle((x+2,207,x+8,208),fill=color)
        for side in (0,1):
            _png(im.crop((side*192,0,(side+1)*192,222)),
                 asset / f"textures/gui/first_magic/{page}_{side}.png")
        write_json(asset / f"font/first_magic_canvas_{page}.json", {"providers": [
            {"type":"bitmap","file":f"projects:gui/first_magic/{page}_0.png","height":222,"ascent":13,"chars":["\ue600"]},
            {"type":"bitmap","file":f"projects:gui/first_magic/{page}_1.png","height":222,"ascent":13,"chars":["\ue601"]}
        ]})
