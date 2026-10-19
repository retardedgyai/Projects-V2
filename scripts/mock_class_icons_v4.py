"""Class icons v4: hand-placed in the "Samus RPG skill icons" idiom (direction mock).

    python scripts/mock_class_icons_v4.py

Reference (studied close up): mcmodels.net "Skill Icons - Samus RPG Classes Edition v.2" —
1-2 px calligraphic strokes with stair-step edges, mostly two tones (bright + dark, the dark one
on the stroke's lower side and in scattered pixels), a 1 px darker drop shadow on the warm brown
backing, no outline. Every pixel below is placed by hand (uppercase = first hue, lowercase =
second hue; H/h highlight, L/l light, M/m mid, D/d dark). Six classes; Starweaver removed.

Writes build/hud-preview/directions/class-icons-v4-8x.png and class-icon-v4-hud.png.
"""
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402

BACK, BACK_SHADOW = hud.rgba("4a3230"), hud.rgba("2c1c1c")
HUE = {  # H, L, M, D
    "crimson": ("#ffd0c4", "#ff2a2a", "#d0182a", "#8a0c1c"),
    "steel": ("#ffffff", "#dfe7f4", "#9aa8c4", "#5a6688"),
    "green": ("#e8ffc8", "#7ae84a", "#3eaa38", "#1e6a2a"),
    "fire": ("#fff6c0", "#ffb030", "#ff6a1c", "#a8240e"),
    "ice": ("#ffffff", "#bfe8ff", "#5aaef0", "#2a5ab0"),
    "violet": ("#f4e0ff", "#c08aff", "#8a4ce0", "#40208a"),
    "gold": ("#fffbe0", "#ffd85a", "#e0a020", "#8a560a"),
    "life": ("#ffffff", "#b8ffb0", "#5ad870", "#24804a"),
}

ICONS = {
    # 戦士: a greatsword cleave — one heavy crimson crescent and the steel edge that made it
    "warrior": (("crimson", "steel"), [
        "................",
        ".....MLLLL......",
        "....LHHHHLM.....",
        "...LHHMM..M.....",
        "..LHHM......h...",
        "..LHM......hl...",
        ".LHHM.....hlm...",
        ".LHM.....hlm....",
        ".LHM....hlm.....",
        ".LHHM..hlm......",
        "..LHM.hlm.......",
        "..MLHhlm........",
        "...MLHHM........",
        "....MLLLM.......",
        "......MM........",
        "................"]),
    # レンジャー: an arrow loosed upward-right, green wind peeling off its fletching
    "ranger": (("steel", "green"), [
        "................",
        "................",
        "..........LHH...",
        ".........LHHL...",
        "........LHMLM...",
        ".......LHM......",
        "......LHM.......",
        "..l..LHM........",
        ".lm.LHM...ll....",
        ".lm.HM...lm.....",
        "..lHM...lm......",
        "..hm.lllm.......",
        ".hlmmm..........",
        ".lm.............",
        "................",
        "................"]),
    # メイジ: a fireball with licking flames, a sliver of frost beneath
    "mage": (("fire", "ice"), [
        "................",
        ".......L........",
        "......LM...L....",
        "...L..LHM.LM....",
        "...LM.LHHMLM....",
        "....LLHHLLM.....",
        "...LLHHLLLLMD...",
        "..LLHHHLLLLMD...",
        "..LLHLLLLLMMD...",
        "...LLLLLLMMD....",
        "....MLLMMMD.....",
        ".h....DDDD...h..",
        "..lh........hm..",
        "....llllmmmmd...",
        "................",
        "................"]),
    # アサシン: two curved violet cuts crossing, a cold glint where they meet
    "assassin": (("violet", "steel"), [
        "................",
        "................",
        "...L........L...",
        "...LH......HL...",
        "....LHM..LHM....",
        ".....LHMLHM.....",
        "......LhhM......",
        ".....LMhhLM.....",
        "......LHHM......",
        ".....LHMLHM.....",
        "....LHM..LHM....",
        "...LHM....LHM...",
        "...LM......LM...",
        "....M......M....",
        "................",
        "................"]),
    # テンプラー: a gold-rimmed heater shield, steel face, gold cross
    "templar": (("gold", "steel"), [
        "................",
        "....LLLLLLLL....",
        "...LLLLLLLLLM...",
        "...LlllHHlllM...",
        "...LlhlHHlllM...",
        "...LHHHHHHHHD...",
        "...LllmHHmmmD...",
        "...LllmHHmmmD...",
        "....LlmHHmmD....",
        "....LmmHHmmD....",
        ".....LmHHmD.....",
        "......LMMD......",
        ".......DD.......",
        "................",
        "................",
        "................"]),
    # ヒーラー: a radiant cross of new growth, sparks of light around it
    "healer": (("life", "gold"), [
        "................",
        ".......LH.......",
        "...h...LH...h...",
        ".......LH.......",
        "....l..LH..l....",
        ".......LH.......",
        "..LLLLLHHHHHHM..",
        "..MMMMMHHMMMMD..",
        ".......LM.......",
        "....l..LM..l....",
        ".......LM.......",
        "...h...LM...h...",
        ".......MD.......",
        "................",
        "................",
        "................"]),
}
ACCENT = {"warrior": "#e3283e", "ranger": "#5ec64a", "mage": "#ff8a1c", "assassin": "#8a4cff",
          "templar": "#f4c84e", "healer": "#9cf07c"}
ORDER = ["warrior", "ranger", "mage", "assassin", "templar", "healer"]
TONE = {"H": 0, "L": 1, "M": 2, "D": 3}


def icon(name):
    (hue1, hue2), rows = ICONS[name]
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, len(row))
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            hue = hue1 if ch.isupper() else hue2
            img.putpixel((x, y), hud.rgba(HUE[hue][TONE[ch.upper()]]))
    return img


def diamond(cv, cx, cy, accent, r=13):
    """The G2 diamond; warm brown backing like the reference's tiles."""
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
        hud.over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), BACK)


def seat(cv, name, cx, cy):
    img = icon(name)
    x0, y0 = cx - 8, cy - 8
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] and not (x + 1 < 16 and y + 1 < 16 and img.getpixel((x + 1, y + 1))[3]):
                hud.px(cv, x0 + x + 1, y0 + y + 1, BACK_SHADOW)  # 1 px drop shadow on the backing
    cv.alpha_composite(img, (x0, y0))


def main():
    sheet = Image.new("RGBA", (6 * 34 + 4, 34), hud.D["plate"])
    for k, name in enumerate(ORDER):
        cx = 19 + k * 34
        diamond(sheet, cx, 17, ACCENT[name])
        seat(sheet, name, cx, 17)
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(hud.OUT / "class-icons-v4-8x.png")
    base, icons, top, jp = hud.design_f1(center=-1)
    cy = hud.HB_Y - 5 - 13 + 4
    diamond(base, hud.C, cy, ACCENT["warrior"])
    seat(base, "warrior", hud.C, cy)
    hud.mc_text(top, hud.C - hud.mc_width("12") // 2 + 1, cy + 9, "12", hud.rgba("80ff20"), outline=True)
    hud.compose((base, icons, top, jp), 4).crop((540, 720, 1380, 1080)).save(hud.OUT / "class-icon-v4-hud.png")
    print(f"wrote {hud.OUT / 'class-icons-v4-8x.png'}")


if __name__ == "__main__":
    main()
