"""Pixel icons for the continent map, v3: hand-placed glyphs on one shared frame language.

Special zones are 32px medallions (boss / hub octagons, PvP shield) with a gold rim; ordinary zones are 22px
stone plaques whose rim carries the biome colour; materials are 12px item sprites for the panel's slots.
Everything is shown at 2x or 3x with nearest-neighbour scaling. Writes assets/ui/atlas/pixel/*.png at 1x.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / "assets/ui/atlas/pixel"
C = {k: tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) + (255,) for k, v in {
    "k": "#120e16", "G": "#fff0b8", "g": "#f0c050", "d": "#b47c2c", "D": "#6e461a",
    "W": "#fffaf0", "w": "#d8ccb4", "s": "#a89a80",
    "R": "#ff7a5a", "r": "#c8382a", "q": "#7e1c16", "Q": "#3a0a0c",
    "S": "#ffffff", "i": "#c8d0dc", "I": "#8a92a2", "j": "#4e5464",
    "E": "#9ef0a0", "e": "#3fa84a", "f": "#1d5a2a", "F": "#0e2a16",
    "B": "#a8dcff", "b": "#3f86d8", "n": "#1f4a88", "h": "#5fd0c0", "H": "#2a8a80",
    "P": "#ffd6ea", "p": "#f29ac4", "o": "#c05682",
    "O": "#ffe070", "a": "#f08a2a", "A": "#b03e18",
    "C": "#f4f8ff", "c": "#b0b6c4", "m": "#6a7080", "M": "#40444f",
    "t": "#a06e3a", "T": "#5e3818", "y": "#e8d49a", "Y": "#a89060",
    "x": "#1a1b22", "X": "#262833", "v": "#363a48", "V": "#4c5162",
}.items()}


def grid(img: Image.Image, rows: str, x0: int, y0: int) -> None:
    for y, row in enumerate(rows.strip("\n").splitlines()):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x0 + x, y0 + y), C[ch])


def outline(img: Image.Image) -> Image.Image:
    out = img.copy(); px = img.load(); op = out.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y][3]: continue
            if any(0 <= x + dx < img.width and 0 <= y + dy < img.height and px[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                op[x, y] = C["k"]
    return out


def octagon(i: int, cut: int = 9):
    a, b, c = 1 + i, 30 - i, max(2, cut - i // 2)
    return [(a + c, a), (b - c, a), (b, a + c), (b, b - c), (b - c, b), (a + c, b), (a, b - c), (a, a + c)]


def medallion(field: str, light: str, dark: str) -> Image.Image:
    img = Image.new("RGBA", (32, 32)); d = ImageDraw.Draw(img)
    o = octagon(0)
    d.polygon(o, fill=C["d"], outline=C["g"])
    d.line([o[6], o[7], o[0], o[1]], fill=C["G"])
    d.polygon(octagon(2), fill=C["k"])
    d.polygon(octagon(3), fill=C[dark])
    d.polygon(octagon(4), fill=C[field])
    i4 = octagon(4)
    d.polygon([i4[6], i4[7], i4[0], i4[1], (16, 16)], fill=C[light])
    d.ellipse((7, 7, 24, 24), fill=C[field])
    return img


SKULL = """
....wWWWWWw....
..wWWWWWWWWWw..
.wWWWWWWWWWWWw.
.WWWWWWWWWWWWw.
wWQQQQWWWQQQQWw
wWQQQQWWWQQQQWw
wWWQQWWWWWQQWWw
.wWWWWWQWWWWWw.
..wwWWQQQWWww..
...wWWWWWWWw...
...wQwQwQwQw...
....wwwwwww....
"""
ANCHOR = """
......SSS......
.....SiiiS.....
.....Si.iS.....
......SSS......
...SSSSSSSSS...
...iiiiSiiii...
.......Si......
.......Si......
.S.....Si....S.
SSS....Si...SSS
.SS....Si...SS.
..SS...Si..SS..
...SSSSSSSSS...
.....iiiiii....
"""
SWORDS = """
SS..........SS
SiS........SiS
.SiS......SiS.
..SiS....SiS..
...SiS..SiS...
....SiSSiS....
.....SiiS.....
.....SiiS.....
....SiSSiS....
.ggSiS..SiSgg.
..gdS....Sdg..
..dTd....dTd..
.tTd......dTt.
TT..........TT
"""


def boss() -> Image.Image:
    img = medallion("q", "r", "Q")
    grid(img, SKULL, 9, 10)
    return outline(img)


def hub() -> Image.Image:
    img = medallion("f", "e", "F")
    grid(img, ANCHOR, 9, 9)
    return outline(img)


def pvp() -> Image.Image:
    img = Image.new("RGBA", (32, 32)); d = ImageDraw.Draw(img)
    shield = [(2, 2), (29, 2), (29, 17), (26, 23), (16, 30), (5, 23), (2, 17)]
    d.polygon(shield, fill=C["d"], outline=C["g"]); d.line([(2, 17), (2, 2), (29, 2)], fill=C["G"])
    d.polygon([(4, 4), (27, 4), (27, 17), (24, 22), (16, 28), (7, 22), (4, 17)], fill=C["k"])
    d.polygon([(5, 5), (26, 5), (26, 17), (23, 21), (16, 27), (8, 21), (5, 17)], fill=C["q"])
    d.polygon([(5, 5), (16, 5), (16, 27), (8, 21), (5, 17)], fill=C["r"])
    grid(img, SWORDS, 9, 7)
    return outline(img)


PLAQUE_RIM = {"verdant": "e", "sakura_grove": "p", "saltmarsh": "h", "clifflands": "y", "highlands": "C", "infernal": "a"}
EMBLEM = {
    "verdant": """
....eEEe....
..eEEEEeee..
.eEEeeeeeef.
.eEeeeeeeff.
.eeeeeeefff.
..efeeeffF..
...fFttFF...
.....tT.....
.....tT.....
....tTTt....
""",
    "sakura_grove": """
....pPPp....
...pPPPPp...
.pp.pPPp.pp.
pPPp.oo.pPPp
pPPPoOOoPPPp
.ppoOOOOopp.
..pPoOOoPp..
.pPPp.o.pPp.
.pPp....pPp.
..p......p..
""",
    "saltmarsh": """
.t..t....t..
.T..tt..tT..
.s..s...s...
.s..s..s....
..s.s..s....
..s.s.s.....
...ss.s.....
bBbbbbbbBbb.
.nnbbnnnbbn.
..nnnnnnnn..
""",
    "clifflands": """
....yyyyy...
...yWyyyYY..
...yyyyYYY..
...YYYYYYY..
.yyyyy.yyyyy
yWyyyYyWyyyY
yyyyYYyyyyYY
YYYYYYYYYYYY
yyy.yyyyyy.y
YYYYYYYYYYYY
""",
    "highlands": """
.....CC.....
....CCCc....
...CCcCcm...
..CcmmCcmm..
..mmmmmmmM..
.mmmmMmmmMM.
.mmmMMmmMMM.
mmmMMmmmMMMM
mMMMmmMMMMMM
MMMMMMMMMMMM
""",
    "infernal": """
.....a......
....aa...a..
...aOa..aa..
...aOaa.aOa.
..aOOOaaOOa.
.aaOOOOaOOa.
.aOOWWOOOOa.
.aOWWWWOOaA.
.AaOWWWOaaA.
..AAaaaaAA..
""",
}


def plaque(biome: str) -> Image.Image:
    img = Image.new("RGBA", (22, 26)); d = ImageDraw.Draw(img)
    d.rectangle((10, 20, 11, 24), fill=C["t"]); img.putpixel((11, 24), C["T"])
    d.rounded_rectangle((1, 1, 20, 20), radius=3, fill=C[PLAQUE_RIM[biome]])
    d.rounded_rectangle((2, 2, 19, 19), radius=2, fill=C["k"])
    d.rectangle((3, 3, 18, 18), fill=C["X"])
    d.line([(3, 3), (18, 3)], fill=C["V"]); d.line([(3, 3), (3, 18)], fill=C["v"])
    d.line([(4, 18), (18, 18)], fill=C["x"]); d.line([(18, 4), (18, 18)], fill=C["x"])
    grid(img, EMBLEM[biome], 5, 6)
    return outline(img)


MATERIAL = {
    "log": """
..TTTTTTTT..
.TttttttttT.
TtyyYyyyYytT
TtyYyyYyyytT
.TttttttttT.
.TtTtTTtTtT.
.TTtTtTtTTT.
.TtTTtTtTtT.
.TTtTtTTtTT.
.TtTtTtTtTT.
.TTTTTTTTTT.
""",
    "ore": """
....mmmm....
..mmcciimm..
.mcciiMmimM.
.mciyymmMMM.
mmiyyYmmmmM.
mcmmYmmyymMM
mmmmmmyyYmMM
.mMmmmmYmMM.
.MMMmmmmMM..
..MMMMMMM...
""",
    "herb": """
.....eE.....
...eEEe.eE..
..eEe..eEEe.
.eEe..eEe...
.ee..eEe.ee.
...e.ee.eEe.
....eeeee...
.....yy.....
....yYYy....
.....yy.....
.....ff.....
""",
    "hide": """
.tt......tt.
tttttttttttt
.tyytttttyt.
.tyttttttTt.
..tttttttT..
..ttttttTT..
.ttttttTTTt.
.tttTTTTTTt.
tt.tTTTTT.tt
.....TT.....
""",
    "stone": """
.mmmmmmmmmm.
mccmcccmcccm
mcmmcmmmcmmM
mmmMmmMMmmMM
mcccmmcccmcM
mcmmMmcmmmMM
mmmMmmmMMmMM
mccmccmmccmM
mmmmmmmmmmMM
.MMMMMMMMMM.
""",
    "shard": """
.....B......
....BBb.....
....BSbn....
...BBSbn....
...BSbbnn...
..BBSbbnn.B.
..BSbbnn.BBn
..BbbbnnBSbn
...bbnn.BBnn
....nn...nn.
""",
}


def material(name: str) -> Image.Image:
    img = Image.new("RGBA", (14, 14))
    rows = MATERIAL[name].strip("\n").splitlines()
    grid(img, MATERIAL[name], 1 + (12 - max(map(len, rows))) // 2, 1 + (12 - len(rows)) // 2)
    return outline(img)


def timer(live: bool) -> Image.Image:
    img = Image.new("RGBA", (40, 12)); d = ImageDraw.Draw(img)
    body, light, dark = (C["r"], C["R"], C["q"]) if live else (C["x"], C["v"], C["k"])
    d.rounded_rectangle((0, 0, 39, 11), radius=5, fill=body)
    d.line([(4, 1), (35, 1)], fill=light); d.line([(4, 10), (35, 10)], fill=dark)
    if live:
        d.ellipse((3, 3, 8, 8), fill=C["W"]); d.ellipse((4, 4, 7, 7), fill=C["S"])
    else:
        d.ellipse((3, 2, 9, 8), outline=C["g"]); d.line([(6, 3), (6, 5), (8, 5)], fill=C["g"])
    return outline(img)


def select() -> Image.Image:
    img = Image.new("RGBA", (40, 40)); d = ImageDraw.Draw(img)
    for (x, y, sx, sy) in ((1, 1, 1, 1), (38, 1, -1, 1), (1, 38, 1, -1), (38, 38, -1, -1)):
        d.line([(x, y), (x + sx * 7, y)], fill=C["G"], width=2); d.line([(x, y), (x, y + sy * 7)], fill=C["G"], width=2)
    return outline(img)


def slot() -> Image.Image:
    """Minecraft inventory slot: dark well, light bottom-right bevel inverted (sunken)."""
    img = Image.new("RGBA", (20, 20)); d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 19, 19), fill=C["k"])
    d.rectangle((1, 1, 18, 18), fill=(27, 28, 34, 255))
    d.line([(1, 1), (18, 1)], fill=(12, 12, 15, 255)); d.line([(1, 1), (1, 18)], fill=(12, 12, 15, 255))
    d.line([(2, 18), (18, 18)], fill=(52, 54, 64, 255)); d.line([(18, 2), (18, 18)], fill=(52, 54, 64, 255))
    return img


def wave(color: str) -> Image.Image:
    """Flat ellipse outline that expands under a selected or live marker."""
    img = Image.new("RGBA", (48, 20)); d = ImageDraw.Draw(img)
    d.ellipse((0, 0, 47, 19), outline=C[color], width=2)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for f in OUT.glob("*.png"): f.unlink()
    boss().save(OUT / "badge_boss.png"); pvp().save(OUT / "badge_pvp.png"); hub().save(OUT / "badge_hub.png")
    for b in PLAQUE_RIM: plaque(b).save(OUT / f"pin_{b}.png")
    for m in MATERIAL: material(m).save(OUT / f"mat_{m}.png")
    slot().save(OUT / "slot.png")
    wave("G").save(OUT / "wave_gold.png"); wave("R").save(OUT / "wave_red.png")
    timer(True).save(OUT / "timer_live.png"); timer(False).save(OUT / "timer_soon.png"); select().save(OUT / "select.png")
    print("ATLAS_PIXEL_ICONS v3")


if __name__ == "__main__":
    main()
