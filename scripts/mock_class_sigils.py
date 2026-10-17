"""Draft class sigils for the centre diamond (direction mock, not pack output).

    python scripts/mock_class_sigils.py

Reference: Lost Ark's class emblems — one bold white silhouette, mirror-symmetric, a weapon grown
spiky with wings / horns / blades, readable by shape alone. Pixel version: each sigil is written
as its left half and mirrored; left of the axis is white, right is grey (light from the left), and
each class gets exactly one coloured element ('A'). Every row is checked against the diamond's
inner edge so nothing touches the rim.

Writes build/hud-preview/directions/class-sigils-8x.png and class-sigil-hud.png.
"""
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402

# Left halves, 8 columns (column 7 is the axis), 19 rows (row 9 is the diamond's centre row).
SIGILS = {
    "warrior": ("#e3283e", [  # winged greatsword, blade down, ruby in the guard
        "......##", ".......#", ".......#", "...#...#", "..##..##", ".###.###", "#######A",
        ".#######", "...#####", "......##", "......##", "......##", "......##", "......##",
        "......##", "......##", ".......#", ".......#", "........"]),
    "ranger": ("#5ec64a", [  # an arrow loosed upward, its fletching spread like wings
        ".......A", "......AA", ".....AAA", "....AAAA", ".......#", ".......#", ".......#",
        ".......#", ".......#", ".......#", "..#....#", ".##....#", ".###...#", "..###..#",
        "...###.#", "....####", ".....###", "......##", ".......#"]),
    "mage": ("#ff8a1c", [  # a burning orb held in claws on a staff
        ".......#", "......#A", ".....##A", "....####", "...##AAA", "..##AAAA", "..#AAAAA",
        "..#AAAAA", "...##AAA", "#...####", "##...###", ".##..#.#", "..####.#", ".......#",
        ".......#", "......##", ".......#", ".......#", "........"]),
    "starweaver": ("#9a5af0", [  # four-point star with a gold heart and two sparks
        ".......#", ".......#", ".......#", "......##", "..#...##", ".....###", "....####",
        "....####", "..####AA", "#####AAA", "..####AA", "....####", "....####", ".....###",
        "..#...##", "......##", ".......#", ".......#", ".......#"]),
    "assassin": ("#8a4cff", [  # dagger inside a crescent
        ".......#", "......##", ".......#", "...A...#", "..A..###", ".A....##", ".A....##",
        "A.....##", "A.....##", "A.....##", "A.....##", ".A....##", ".A.....#", "..A....#",
        "...A...#", "....AA.#", "........", "........", "........"]),
    "templar": ("#f4c84e", [  # crowned shield with a gold cross
        ".......#", ".......#", "......##", "...#...#", "..##..##", ".#######", ".#######",
        ".######A", ".######A", ".###AAAA", ".######A", ".######A", "..#####A", "..#####A",
        "...####A", "....####", ".....###", "......##", ".......#"]),
    "healer": ("#9cf07c", [  # haloed, winged cross
        "........", "......AA", ".....A.#", "....A..#", ".....A.#", "......AA", "..######",
        "##.....#", ".###...#", "..###..#", "....#..#", ".......#", ".......#", ".......#",
        ".......#", ".......#", ".......#", "......##", "........"]),
}
ORDER = ["warrior", "ranger", "mage", "starweaver", "assassin", "templar", "healer"]
WHITE, GREY = hud.rgba("f4f4f8"), hud.rgba("a6aab8")


def sigil(name):
    accent, half = SIGILS[name]
    assert len(half) == 19, name
    img = Image.new("RGBA", (15, 19))
    for r, row in enumerate(half):
        assert len(row) == 8, (name, r)
        avail = 10 - abs(r - 9)
        for c, ch in enumerate(row):
            if ch == ".":
                continue
            if 7 - c > avail:
                print(f"warning: {name} row {r} col {c} crosses the rim")
            for x, col in ((c, WHITE), (14 - c, GREY if c < 7 else WHITE)):
                img.putpixel((x, r), hud.rgba(accent) if ch == "A" else col)
    out = Image.new("RGBA", (17, 21))  # 1 px dark outline all round
    for y in range(19):
        for x in range(15):
            if img.getpixel((x, y))[3]:
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        if not (0 <= x + dx < 15 and 0 <= y + dy < 19 and img.getpixel((x + dx, y + dy))[3]):
                            out.putpixel((x + 1 + dx, y + 1 + dy), hud.D["out"])
    out.alpha_composite(img, (1, 1))
    return out


def diamond(cv, cx, cy, accent, r=13):
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


def main():
    sheet = Image.new("RGBA", (7 * 36 + 4, 36), hud.D["plate"])
    for k, name in enumerate(ORDER):
        cx = 20 + k * 36
        diamond(sheet, cx, 18, SIGILS[name][0])
        sheet.alpha_composite(sigil(name), (cx - 8, 18 - 10))
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(hud.OUT / "class-sigils-8x.png")
    base, icons, top, jp = hud.design_f1(center=-1)
    py = hud.HB_Y - 5 - 13
    diamond(base, hud.C, py + 4, SIGILS["warrior"][0])
    base.alpha_composite(sigil("warrior"), (hud.C - 8, py + 4 - 10))
    hud.mc_text(top, hud.C - hud.mc_width("12") // 2 + 1, py + 13, "12", hud.rgba("80ff20"), outline=True)
    hud.compose((base, icons, top, jp), 4).crop((540, 750, 1380, 1080)).save(hud.OUT / "class-sigil-hud.png")
    print(f"wrote {hud.OUT / 'class-sigils-8x.png'}")


if __name__ == "__main__":
    main()
