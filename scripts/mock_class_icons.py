"""Draft class icons as Minecraft-style 16x16 items (direction mock, not pack output).

    python scripts/mock_class_icons.py

Reference: Monumenta's class selection GUI shows each class as its weapon drawn like a vanilla
item (diagonal, 16 px, vanilla shading) with the class told apart by a coloured frame accent.
Vanilla item rules followed here (read from iron_sword / bow / mace): handle bottom-left, tip
top-right; no all-round black outline — the upper-left edge is a dark mid tone, the lower-right
edge the darkest tone; blade cross-section reads edge, white highlight, light, mid, dark edge.

Writes build/hud-preview/directions/class-icons-8x.png.
"""
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402

PAL = {
    # steel
    "a": "#4a4f58", "c": "#17191d", "b": "#ffffff", "d": "#c9ced8", "e": "#99a0ad",
    # gold
    "g": "#f4c84e", "G": "#b07a1c", "k": "#5c3a0a",
    # wood / leather
    "h": "#493615", "i": "#896727", "j": "#684e1e", "x": "#281e0b",
    # gems & magic
    "r": "#e3283e", "R": "#ff9c9c", "q": "#701022",
    "f": "#ff8a1c", "F": "#ffd860", "y": "#c8401a",
    "u": "#86dcff", "U": "#3aa0ea", "z": "#1a5aa8",
    "p": "#dcb0ff", "P": "#9a5af0", "Q": "#4e2398",
    "n": "#9cf07c", "N": "#3e9c3a",
    "w": "#fff6d8",
}

ICONS = {
    # 戦士: greatsword — wide blade, gold crossguard, ruby pommel
    "warrior": [
        "............aaab",
        "...........abbdc",
        "..........abddec",
        ".........abddec.",
        "........abddec..",
        ".......abddec...",
        "..gg..abddec....",
        "..gFg.addec.....",
        "...gGcddec......",
        "....gGeec.......",
        "....gGGcGg......",
        "...hic.gGFg.....",
        "..hic...ggg.....",
        ".hic............",
        "rRc.............",
        "qq..............",
    ],
    # レンジャー: recurve bow with a nocked arrow
    "ranger": [
        "..........aaab..",
        "........aaijjxe.",
        "......aaijxx.e..",
        ".....aijx...e...",
        "....aijx...e....",
        "...aijx...e.....",
        "...ajx...e......",
        "..aix...e.......",
        "..ajx..e........",
        "..aix.e.....rr..",
        ".aix.e.....r....",
        ".ajxe......r....",
        ".aieb...........",
        ".ajx............",
        "..ij............",
        "...x............",
    ],
    # メイジ: staff with a split fire / frost orb
    "mage": [
        "...........FFuu.",
        "..........FffUuz",
        "..........fywUUz",
        "..........yfyzUz",
        ".........GgyyzzG",
        "........iGG.zG..",
        ".......ij..G....",
        "......ij........",
        ".....ij.........",
        "....ij..........",
        "...ij...........",
        "..ij............",
        ".ij.............",
        "ij..............",
        "x...............",
        "................",
    ],
    # 星織り師: a woven four-point star shard on a thread
    "starweaver": [
        "..........p.....",
        "..........P.....",
        ".........pPp....",
        "........pPwPp...",
        ".....ppPPwwwPPp.",
        ".......pPPwPPp..",
        "........pPPPp...",
        ".........pPp....",
        "..p......PQP....",
        ".........PQ.....",
        "........P.......",
        "......pP........",
        "....pP..........",
        "..pP............",
        ".pQ.......p.....",
        "................",
    ],
    # アサシン: a curved dagger with a violet gem
    "assassin": [
        "..............ab",
        ".............abc",
        "............abdc",
        "...........abdc.",
        "..........abdec.",
        ".........abdec..",
        "........abdec...",
        "....a..abdec....",
        "....Pa.addc.....",
        ".....PPdec......",
        "......PPc.......",
        ".....hiPPa......",
        "....hic..Pa.....",
        "...hic....Q.....",
        "..PPc...........",
        "..QQ............",
    ],
    # テンプラー: flanged mace
    "templar": [
        ".........aa.....",
        "........abda.ea.",
        ".......abdbdeFe.",
        "......abdeFdde..",
        ".....abdagddade.",
        ".....aeggddaaade",
        "......aegdaaedae",
        ".......eeaaedae.",
        "......ijedadae..",
        ".....ijx.edae...",
        "....ijx...ee....",
        "...ijx..........",
        "..ijx...........",
        "aajx............",
        "agG.............",
        "GGG.............",
    ],
    # ヒーラー: a sunburst scepter
    "healer": [
        "..........g.g...",
        "...........g...g",
        "........g.wFw.g.",
        ".........wFgFw..",
        "........gFgwgFg.",
        ".........wFgFw..",
        "........g.wGw.g.",
        "..........GG....",
        ".........GG.g...",
        "........ij......",
        ".......ij.......",
        "......ij........",
        ".....ij.........",
        "....ij..........",
        "...nN...........",
        "..NN............",
    ],
}
ACCENT = {"warrior": "#e3283e", "ranger": "#5ec64a", "mage": "#ff8a1c", "starweaver": "#9a5af0",
          "assassin": "#6a3fd0", "templar": "#f4c84e", "healer": "#9cf07c"}
ORDER = ["warrior", "ranger", "mage", "starweaver", "assassin", "templar", "healer"]


def icon_image(name):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(ICONS[name]):
        assert len(row) == 16, (name, y, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), hud.rgba(PAL[ch]))
    return img


def frame(cv, cx, cy, accent, r=13):
    """The G2 diamond, with the class colour on its inner rim (Monumenta tells classes apart by frame colour)."""
    D = hud.D
    for j in range(-r - 1, r + 2):
        w = r + 1 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["out"])
    for j in range(-r, r + 1):
        w = r - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["metal"][1] if j < 0 else D["metal"][2])
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), hud.rgba(accent))
    for j in range(-r + 3, r - 2):
        w = r - 3 - abs(j)
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), D["slot"])


def square_frame(cv, x, y, accent, size=22):
    """Monumenta-style: a stepped charcoal slot with the class colour on its four corner brackets."""
    hud.step_plate(cv, (x - 2, y - 2, x + size + 2, y + size + 2), cut=2)
    hud.over(cv, (x, y, x + size, y + size), hud.D["slot"])
    hud.over(cv, (x, y, x + size, y + 1), hud.D["out"])
    hud.over(cv, (x, y + size - 1, x + size, y + size), hud.D["slot_hi"])
    a = hud.rgba(accent)
    for cx, cy, sx, sy in ((x + 1, y + 1, 1, 1), (x + size - 2, y + 1, -1, 1),
                           (x + 1, y + size - 2, 1, -1), (x + size - 2, y + size - 2, -1, -1)):
        for d in range(4):
            hud.px(cv, cx + sx * d, cy, a)
            hud.px(cv, cx, cy + sy * d, a)


def hud_with(name, kind):
    """Seat one class icon in the F1 HUD centre (kind: 'square' or 'diamond')."""
    base, icons, top, jp = hud.design_f1(center=-1)
    py = hud.HB_Y - 5 - 13
    # clear the level number area by redrawing the seam plates' join
    if kind == "square":
        x, y = hud.C - 11, py - 9
        square_frame(base, x, y, ACCENT[name])
        base.alpha_composite(icon_image(name), (x + 3, y + 3))
        hud.mc_text(top, x + 23 - hud.mc_width("12"), y + 15, "12", hud.rgba("80ff20"), outline=True)
    else:
        frame(base, hud.C, py + 4, ACCENT[name])
        base.alpha_composite(icon_image(name), (hud.C - 8, py - 4))
        hud.mc_text(top, hud.C - hud.mc_width("12") // 2 + 1, py + 13, "12", hud.rgba("80ff20"), outline=True)
    return base, icons, top, jp


def main():
    sq = Image.new("RGBA", (7 * 32 + 6, 32), hud.D["plate"])
    for k, name in enumerate(ORDER):
        x = 7 + k * 32
        square_frame(sq, x, 5, ACCENT[name])
        sq.alpha_composite(icon_image(name), (x + 3, 8))
    sq.resize((sq.width * 8, sq.height * 8), Image.NEAREST).save(hud.OUT / "class-icons-square-8x.png")
    for kind in ("square", "diamond"):
        img = hud.compose(hud_with("warrior", kind), 4)
        img.crop((540, 750, 1380, 1080)).save(hud.OUT / f"class-icon-hud-{kind}.png")
    sheet = Image.new("RGBA", (7 * 36 + 4, 36), hud.D["plate"])
    for k, name in enumerate(ORDER):
        cx = 20 + k * 36
        frame(sheet, cx, 18, ACCENT[name])
        sheet.alpha_composite(icon_image(name), (cx - 8, 10))
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(hud.OUT / "class-icons-8x.png")
    print(f"wrote {hud.OUT / 'class-icons-8x.png'}")


if __name__ == "__main__":
    main()
