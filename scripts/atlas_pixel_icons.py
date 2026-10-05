"""Hand-authored pixel icons for the continent map (one art pixel = 3 screen pixels at 1080p).

Each icon is a character grid; '.' is transparent. Palette letters are shared so every icon uses the
same light (top-left), the same dark outline and the same gold. Writes assets/ui/atlas/pixel/*.png at 1x.
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[1] / "assets/ui/atlas/pixel"
PAL = {
    "k": "#14101a",  # outline
    "G": "#ffe9a8", "g": "#e8b84a", "d": "#a8742a", "D": "#6a4418",  # gold light / mid / dark / deep
    "W": "#fffaf0", "w": "#e8e0cc", "s": "#b8ae98",  # bone
    "R": "#ff7a5a", "r": "#c8382a", "q": "#7a1e18", "Q": "#3e0e0e",  # red
    "S": "#ffffff", "i": "#c8d0dc", "I": "#8a92a2", "j": "#555b68",  # steel
    "E": "#9af0a8", "e": "#3fa84a", "f": "#1e5a2a", "F": "#0e2a16",  # green
    "B": "#a8d8ff", "b": "#4a8ad8", "n": "#24508f",  # blue
    "P": "#ffd8ea", "p": "#f4a0c8", "o": "#c8608a",  # pink
    "O": "#ffd060", "a": "#f08a2a", "A": "#b8401a",  # fire
    "C": "#f4f4f4", "c": "#a8acb8", "m": "#6a6e7a",  # snow / rock
    "t": "#8a5a30", "T": "#5a3418",  # wood
    "x": "#1e1f26", "X": "#2c2e38",  # badge inner dark
}

ICONS = {
    # 24x24 crest: gold ring, dark red field, bone skull, crown on top, pointer tail
    "boss": [
        "........G..G..G.........",
        "........gG.gg.Gg........",
        ".......kgggggggggk......",
        ".......kdgdgdgdgdk......",
        "......kkkkkkkkkkkkk.....",
        ".....kGggggggggggggk....",
        "....kGgdqqqqqqqqqdggk...",
        "...kGgqqqWWWWWWqqqdgk...",
        "...kgqqqWWwwwwWWqqqdk...",
        "..kGgqqWWwwwwwwWWqqdgk..",
        "..kgqqqWkkwwwwkkWqqqdk..",
        "..kgqqqWkkRwwRkkWqqqdk..",
        "..kgqqqWWwwkkwwWWqqqdk..",
        "..kgqqqqWWwkkwWWqqqqdk..",
        "..kdgqqqqWkwkwkWqqqdDk..",
        "...kdqqqqWWWWWWWqqqDk...",
        "...kdgqqqqqqqqqqqqdDk...",
        "....kddqqqqqqqqqqdDk....",
        ".....kDddgggggggdDk.....",
        "......kkDDddddDDkk......",
        "........kkkddkkk........",
        "..........kdgk..........",
        "...........kk...........",
        "........................",
    ],
    "pvp": [
        "........................",
        "...kkkkkkkkkkkkkkkkk....",
        "..kSiiiiiiiiiiiiiiIk....",
        "..kiqqqqqqqqqqqqqqIjk...",
        "..kiqSkqqqqqqqqqkSqIk...",
        "..kiqiSkqqqqqqqkSiqIk...",
        "..kiqqiSkqqqqqkSiqqIk...",
        "..kiqqqiSkqqqkSiqqqIk...",
        "..kiqqqqiSkqkSiqqqqIk...",
        "..kiqqqqqiSkSiqqqqqIk...",
        "..kiqqqqqqkSkqqqqqqIk...",
        "..kiqqqqqgkikgqqqqqIk...",
        "...kiqqqgkSkSkgqqqIk....",
        "...kiqqgkSkqkSkgqqIk....",
        "....kiqtkkqqqkktqIk.....",
        "....kiqTtqqqqqtTqIk.....",
        ".....kiqqqqqqqqqIk......",
        "......kiqqqqqqqIk.......",
        ".......kiiqqqiIk........",
        "........kkiiikk.........",
        "..........kkk...........",
        "..........krk...........",
        "...........k............",
        "........................",
    ],
    "hub": [
        "........................",
        "........kkkkkkkk........",
        "......kkGGggggddkk......",
        ".....kGgFFFFFFFFddk.....",
        "....kGFFfFFeFFfFFFdk....",
        "...kGFFfFFFeEFFFfFFdk...",
        "...kgFFFFFFeEFFFFFFdk...",
        "..kGFFFFFFkeEkFFFFFFdk..",
        "..kgFfFFFFkrRkFFFFfFdk..",
        "..kgFFFFFFkrRkFFFFFFdk..",
        "..kgFeeEEEkWWkGgggdFdk..",
        "..kgFFFFFFkwskFFFFFFdk..",
        "..kgFfFFFFkwskFFFFfFdk..",
        "..kdFFFFFFkwskFFFFFFDk..",
        "...kdFFFFFFwsFFFFFFDk...",
        "...kdFFfFFFwsFFFfFFDk...",
        "....kdFFFFFFFFFFFFDk....",
        ".....kDdFFFFFFFFdDk.....",
        "......kkDDddddDDkk......",
        "........kkkddkkk........",
        "..........kdgk..........",
        "...........kk...........",
        "........................",
        "........................",
    ],
}

# 16x16 pins: gold-rimmed diamond with a terrain emblem, tail at the bottom
PIN_FRAME = [
    ".......kk.......",
    "......kGgk......",
    ".....kGxxdk.....",
    "....kGxxxxdk....",
    "...kGxxxxxxdk...",
    "..kGxxxxxxxxdk..",
    ".kGxxxxxxxxxxdk.",
    "kgxxxxxxxxxxxxDk",
    ".kdxxxxxxxxxxDk.",
    "..kdxxxxxxxxDk..",
    "...kdxxxxxxDk...",
    "....kdxxxxDk....",
    ".....kdxxDk.....",
    "......kddk......",
    ".......kk.......",
    "................",
]
EMBLEMS = {  # 8x8 emblems placed at (4,3)
    "verdant": ["..eeee..", ".eEeeee.", "eeEeeeef", "eeeeeeff", ".efeeff.", "...tt...", "...tt...", "..TttT.."],
    "sakura_grove": ["...pp...", ".ppPPpp.", "pPPOOPPp", "pPOOOOPo", ".pPOOPo.", "..pPPo..", "...oo...", "........"],
    "saltmarsh": ["..t..t..", "..s.ts..", ".ts.s.t.", ".s..s.s.", "bbbbbbbb", "bBBbbBBb", "nbbbnbbn", "........"],
    "clifflands": ["........", "...ee...", "..ewwe..", "..wwww..", ".wwsswwe", ".sswwsww", "wwwwssww", "ssssssss"],
    "highlands": ["...CC...", "..CCCm..", "..CmCmm.", ".cmmcmmm", ".cmcmmcm", "cmmcmmcm", "mmcmmmcm", "mmmmmmmm"],
    "infernal": ["...O....", "...aO...", "..aaO.a.", ".aAaaOa.", ".AaOOaA.", "AaOOOOaA", "AaaOOaaA", ".AAaaAA."],
}

TIMER_LIVE = [
    "..kkkkkkkkkkkkkkkkkkkkkk..",
    ".kRrrrrrrrrrrrrrrrrrrrrrk.",
    "krrkkrrrrrrrrrrrrrrrrrrrrk",
    "krkWWkrrrrrrrrrrrrrrrrrrqk",
    "krkWSkrrrrrrrrrrrrrrrrrrqk",
    "krrkkrrrrrrrrrrrrrrrrrrrqk",
    ".kqqqqqqqqqqqqqqqqqqqqqqk.",
    "..kkkkkkkkkkkkkkkkkkkkkk..",
]
TIMER_SOON = [
    "..kkkkkkkkkkkkkkkkkkkkkk..",
    ".kXxxxxxxxxxxxxxxxxxxxxxk.",
    "kxkgggkxxxxxxxxxxxxxxxxxxk",
    "kxgxGxgxxxxxxxxxxxxxxxxxxk",
    "kxgxGggxxxxxxxxxxxxxxxxxxk",
    "kxkgggkxxxxxxxxxxxxxxxxxxk",
    ".kxxxxxxxxxxxxxxxxxxxxxxk.",
    "..kkkkkkkkkkkkkkkkkkkkkk..",
]
SELECT = [  # 28x28 corner brackets around a selected marker
    "GGGGGG..............GGGGGG..",
]


def draw(rows: list[str]) -> Image.Image:
    h, w = len(rows), max(len(r) for r in rows)
    img = Image.new("RGBA", (w, h))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in PAL:
                c = PAL[ch]
                img.putpixel((x, y), (int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16), 255))
    return img


def brackets(size: int = 28, arm: int = 6) -> Image.Image:
    img = Image.new("RGBA", (size, size))
    gold, dark = (255, 233, 168, 255), (20, 16, 26, 255)
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (size - 1, 0, -1, 1), (0, size - 1, 1, -1), (size - 1, size - 1, -1, -1)):
        for i in range(arm):
            for t in (0, 1):
                for (x, y) in ((cx + sx * i, cy + sy * t), (cx + sx * t, cy + sy * i)):
                    img.putpixel((x, y), gold)
            for (x, y) in ((cx + sx * i, cy + sy * 2), (cx + sx * 2, cy + sy * i)):
                if 0 <= x < size and 0 <= y < size and img.getpixel((x, y))[3] == 0:
                    img.putpixel((x, y), dark)
    return img


def hub() -> Image.Image:
    """Compass crest: gold ring, deep green field, 8-point rose with a red north point. Drawn without anti-aliasing."""
    from PIL import ImageDraw
    rgb = lambda k: tuple(int(PAL[k][i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    img = Image.new("RGBA", (24, 24)); d = ImageDraw.Draw(img)
    d.ellipse((1, 0, 22, 21), fill=rgb("k"))
    d.ellipse((2, 1, 21, 20), fill=rgb("g"))
    d.ellipse((2, 1, 20, 19), fill=rgb("G"))
    d.ellipse((3, 2, 20, 19), fill=rgb("d"))
    d.ellipse((4, 3, 19, 18), fill=rgb("F"))
    d.ellipse((5, 4, 18, 17), fill=rgb("f"))
    cx, cy = 11.5, 10.5
    d.polygon([(cx, 4), (cx + 1.5, cy - 1.5), (cx, cy)], fill=rgb("R")); d.polygon([(cx, 4), (cx - 1.5, cy - 1.5), (cx, cy)], fill=rgb("r"))
    d.polygon([(cx, 17), (cx + 1.5, cy + 1.5), (cx, cy)], fill=rgb("w")); d.polygon([(cx, 17), (cx - 1.5, cy + 1.5), (cx, cy)], fill=rgb("W"))
    d.polygon([(5, cy), (cx - 1.5, cy - 1.5), (cx, cy)], fill=rgb("W")); d.polygon([(5, cy), (cx - 1.5, cy + 1.5), (cx, cy)], fill=rgb("w"))
    d.polygon([(18, cy), (cx + 1.5, cy - 1.5), (cx, cy)], fill=rgb("W")); d.polygon([(18, cy), (cx + 1.5, cy + 1.5), (cx, cy)], fill=rgb("w"))
    for (x, y) in ((8, 7), (15, 7), (8, 14), (15, 14)):
        img.putpixel((x, y), rgb("G"))
    img.putpixel((11, 10), rgb("k")); img.putpixel((12, 10), rgb("k"))
    for y, row in enumerate(["........kkkddkkk........", "..........kdgk..........", "...........kk..........."]):
        for x, ch in enumerate(row):
            if ch in PAL: img.putpixel((x, 20 + y), rgb(ch))
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, rows in ICONS.items():
        draw(rows).save(OUT / f"badge_{name}.png")
    hub().save(OUT / "badge_hub.png")
    for name, emblem in EMBLEMS.items():
        pin = draw(PIN_FRAME)
        pin.alpha_composite(draw(emblem), (4, 3))
        pin.save(OUT / f"pin_{name}.png")
    draw(TIMER_LIVE).save(OUT / "timer_live.png")
    draw(TIMER_SOON).save(OUT / "timer_soon.png")
    brackets().save(OUT / "select.png")
    print(f"ATLAS_PIXEL_ICONS {len(ICONS) + len(EMBLEMS) + 3}")


if __name__ == "__main__":
    main()
