"""Why C6-C8 felt "close but something is off": three hypotheses, one mock each (not pack output).

    python scripts/mock_combat_hud_why.py

Every mock is C8 (the C2 structure in blackstone, iron and gems) with exactly one thing changed, so
the creator can say which change moves it toward AION2 / Lost Ark / Black Desert. Rules settled
after C8 are applied to all four panels alike: numbers in the vanilla font, Japanese drawn the way
vanilla draws it (no bold digits, no MaruMinya).

    base        C8 as it was, with the settled fonts
    silhouette  H1: one shaped console instead of separate boxes; boss / buffs / notice float bare
    material    H2: same boxes, smooth near-black base with warm gold trim instead of stone and iron
    gauge       H3: same boxes, gauges are the heaviest thing on screen (7 px, glossy, lit edge)

Writes build/hud-preview/directions/why-<variant>-2x.png, why-sheet.png and why-closeups.png.
"""
from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
from mock_combat_hud_directions import (  # noqa: E402
    BOSS, BOSS_LOST, BOSS_MAX, BUFFS, BARRIER, C, G, H, HB_X, HB_Y, HP, HP_LOST, HP_MAX, IRON, MP, MP_MAX,
    NOTICE, RAGE, SKILL_ROW, W, caps, gem, inset_slot, mc_text, mc_width, over, px, rgba, tex,
)

GOLD = (rgba("fff2b8"), rgba("e8c46a"), rgba("b8893a"), rgba("7a5420"), rgba("3a2810"))  # light..dark
HALVES = [2, 4, 6, 8, 10, 12, 13, 13, 13, 13, 13, 13, 13, 13, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]


def stone_rows(cv, x0, y0, x1, y1, cut=0):
    """Blackstone plate drawn row by row so the top corners can be cut diagonally (cut px)."""
    hud.STONE = hud.STONE or hud._stone_tile()
    for j in range(y0, y1):
        d = max(0, cut - (j - y0))
        a, b = x0 + d, x1 - d
        over(cv, (a, j, b, j + 1), G["out"])
        if j in (y0, y1 - 1) or b - a <= 2:
            continue
        row = hud.STONE.crop((0, (j - y0 - 1) % 16, 16, (j - y0 - 1) % 16 + 1))
        for tx in range(a + 1, b - 1, 16):
            cv.alpha_composite(row.crop((0, 0, min(16, b - 1 - tx), 1)), (tx, j))
        # bevel: light on the top / left edge (the diagonal counts as top), dark bottom / right
        over(cv, (a + 1, j, a + 2, j + 1), IRON[3])
        over(cv, (b - 2, j, b - 1, j + 1), IRON[3] if j - y0 <= cut else (0, 0, 0, 200))
        if j == y0 + 1:
            over(cv, (a + 1, j, b - 1, j + 1), IRON[3])
    over(cv, (x0 + 1, y1 - 2, x1 - 1, y1 - 1), (0, 0, 0, 200))


def smooth_plate(cv, box, bevel=True):
    """H2: no stone noise. Near-black, slightly see-through body, 1px warm gold trim inside the outline."""
    x0, y0, x1, y1 = box
    over(cv, (x0 + 1, y0, x1 - 1, y1), G["out"])
    over(cv, (x0, y0 + 1, x1, y1 - 1), G["out"])
    over(cv, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), (20, 18, 22, 232))
    if bevel:
        over(cv, (x0 + 1, y0 + 1, x1 - 1, y0 + 2), GOLD[3])
        over(cv, (x0 + 1, y0 + 1, x0 + 2, y1 - 1), GOLD[3])
        over(cv, (x0 + 1, y1 - 2, x1 - 1, y1 - 1), GOLD[4])
        over(cv, (x1 - 2, y0 + 1, x1 - 1, y1 - 1), GOLD[4])
        for cx, cy in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1)):
            px(cv, cx, cy, GOLD[1])


def crest(cv, cx, top, level, metal):
    """C6's crest with the metal swappable and the level in the vanilla font."""
    for j, hw in enumerate(HALVES):
        over(cv, (cx - hw - 1, top + j, cx + hw + 1, top + j + 1), G["out"])
    for j, hw in enumerate(HALVES[1:-1], start=1):
        hw -= 1
        over(cv, (cx - hw, top + j, cx, top + j + 1), metal[1] if j < 6 else metal[2])
        over(cv, (cx, top + j, cx + hw, top + j + 1), metal[2] if j < 6 else metal[3])
        px(cv, cx - hw, top + j, metal[0])
    for j, hw in enumerate(HALVES[5:-5], start=5):
        hw -= 4
        if hw > 0:
            over(cv, (cx - hw, top + j, cx + hw, top + j + 1), G["out"] if j == 5 else (18, 18, 22, 255))
    gem(cv, cx, top + 3, G["ruby"], 2)
    mc_text(cv, cx - mc_width(level) // 2 + 1, top + 9, level, G["text"])


def gauge(cv, x0, x1, y, h, frac, ramp, empty, extra=0.0, trail=0.0, heavy=False):
    """C6's ramp gauge; heavy=True (H3) adds a gloss band, a darker lower half and a lit leading edge."""
    if not heavy:
        hud.ramp_gauge(cv, x0, x1, y, h, frac, ramp, empty, extra=extra, trail=trail)
        return
    dark, base, light = ramp
    inset_slot(cv, (x0 - 1, y - 1, x1 + 1, y + h + 1))
    over(cv, (x0, y, x1, y + h), empty)
    over(cv, (x0, y, x1, y + 1), tuple(min(255, v + 14) for v in empty[:3]) + (255,))
    w = x1 - x0
    fb = x0 + round(w * frac)
    if trail:
        over(cv, (fb, y, min(x1, fb + round(w * (extra + trail))), y + h), rgba("f6e2d2"))
    if extra:
        e = min(x1, fb + round(w * extra))
        over(cv, (fb, y, e, y + h), rgba("dcdcdc"))
        over(cv, (fb, y, e, y + 2), rgba("ffffff"))
    over(cv, (x0, y, fb, y + h), base)
    over(cv, (x0, y + h // 2 + 1, fb, y + h), dark)
    over(cv, (x0, y + 1, fb, y + 3), light)
    over(cv, (x0, y, fb, y + 1), tuple(min(255, v + 60) for v in light[:3]) + (255,))
    over(cv, (fb - 1, y, fb, y + h), rgba("ffffff", 220))   # lit leading edge


def build(variant):
    silhouette, material, heavy = variant == "silhouette", variant == "material", variant == "gauge"
    plate = smooth_plate if material else hud.stone_plate
    metal = GOLD if material else IRON
    cv = hud.world()
    top = Image.new("RGBA", (W, H))
    icons, jp = [], []
    x0, y0 = HB_X, HB_Y
    gh = 7 if heavy else 4                     # gauge height
    ph = 23 if heavy else 20                   # HP / MP plate height
    py = y0 - 4 - ph
    if silhouette:
        # one console: vitals, resource strip and hotbar share a single outline with tapered top ends
        stone_rows(cv, x0 - 1, py, x0 + 183, y0 + 22, cut=7)
        over(cv, (x0 + 1, y0 - 6, x0 + 181, y0 - 5), G["out"])           # seam above the strip
        over(cv, (x0 + 1, y0 - 5, x0 + 181, y0 - 4), IRON[3][:3] + (110,))
    else:
        plate(cv, (x0 - 1, y0, x0 + 183, y0 + 22))
    for i in range(9):
        inset_slot(cv, (x0 + 2 + 20 * i, y0 + 2, x0 + 20 + 20 * i, y0 + 20))
    cv.alpha_composite(tex("item/iron_sword"), (x0 + 3, y0 + 3))
    cv.alpha_composite(tex("item/potion"), (x0 + 3 + 20 * 7, y0 + 3))
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        x, y = x0 + 3 + 20 * (k - 1), y0 + 3
        icons.append((name, x, y, state))
        if state == "cd":
            s = str(cd)
            mc_text(top, x + 9 - mc_width(s) // 2, y + 4, s, G["text"])
    for i in range(9):
        caps(top, x0 + 3 + 20 * i, y0 + 3, str(i + 1), (190, 190, 190, 220))
    sx = x0 + 1
    for a, b, c, d, col in ((sx, y0 + 1, sx + 20, y0 + 2, metal[0]), (sx, y0 + 1, sx + 1, y0 + 21, metal[0]),
                            (sx, y0 + 20, sx + 20, y0 + 21, metal[2]), (sx + 19, y0 + 1, sx + 20, y0 + 21, metal[2])):
        over(cv, (a, b, c, d), col)
    cv.alpha_composite(tex("gui/sprites/hud/crosshair"), (C - 7, H // 2 - 7))
    # class resource strip
    ry = y0 - 5
    if not silhouette:
        plate(cv, (x0 - 1, ry, x0 + 183, ry + 6), bevel=False)
    lit = RAGE * 20 // 100
    for i in range(20):
        cx0 = x0 + 2 + i * 9
        if i < lit:
            over(cv, (cx0, ry + 2, cx0 + 8, ry + 4), G["rage"][1])
            over(cv, (cx0, ry + 2, cx0 + 8, ry + 3), G["rage"][2])
        else:
            over(cv, (cx0, ry + 2, cx0 + 8, ry + 4), (44, 30, 20, 255))
    # HP / MP
    if not silhouette:
        plate(cv, (x0 - 1, py, C - 9, py + ph))
        plate(cv, (C + 9, py, x0 + 183, py + ph))
    tx_l = x0 + (9 if silhouette else 3)       # clear the tapered corner
    mc_text(cv, tx_l, py + 3, "HP", G["label"])
    s = str(HP)
    mc_text(cv, tx_l + 14, py + 3, s, G["text"])
    mc_text(cv, tx_l + 14 + mc_width(s), py + 3, f"/{HP_MAX}", G["label"])
    gy = py + ph - 4 - gh
    gauge(cv, x0 + 3, C - 13, gy, gh, HP / HP_MAX, G["hp"], (34, 10, 14, 255),
          extra=BARRIER / HP_MAX, trail=HP_LOST / HP_MAX, heavy=heavy)
    s = str(MP)
    right = x0 + 180 - (6 if silhouette else 0)
    tx = right - (mc_width(s) + mc_width(f"/{MP_MAX}"))
    mc_text(cv, tx, py + 3, s, G["text"])
    mc_text(cv, tx + mc_width(s), py + 3, f"/{MP_MAX}", G["label"])
    mc_text(cv, C + 15, py + 3, "MP", G["label"])
    gauge(cv, C + 13, x0 + 179, gy, gh, MP / MP_MAX, G["mp"], (10, 16, 36, 255), heavy=heavy)
    crest(cv, C, py - 6, "12", metal)
    # buffs
    bx = x0 - 1 + (6 if silhouette else 0)
    for label, name, sec in BUFFS:
        s = str(sec)
        tw = 15 + mc_width(s) + 3
        if not silhouette:
            plate(cv, (bx, py - 14, bx + tw, py - 1))
        icons.append((name, bx + 2, py - 12, "buff10"))
        over(cv, (bx + 2, py - 3, bx + 12, py - 2), G["buff"])
        mc_text(cv, bx + 14, py - 11, s, G["text"])
        bx += tw + 2
    # notice
    nw = len(NOTICE) * 8
    if not silhouette:
        plate(cv, (C - nw // 2 - 8, py - 33, C + nw // 2 + 8, py - 17))
        gem(cv, C - nw // 2 - 4, py - 25, G["mp"], 1)
        gem(cv, C + nw // 2 + 3, py - 25, G["mp"], 1)
    jp.append((NOTICE, C - nw // 2, py - 29, rgba("9cc6ff")))
    # boss
    bw = 230
    bx0 = C - bw // 2
    bgh = 7 if heavy else 5
    if not silhouette:
        plate(cv, (bx0, 2, bx0 + bw, 23 + bgh))
    gem(cv, bx0 + 8, 9, G["ruby"], 3)
    name = "裂け目の執行官"
    jp.append((name, bx0 + 16, 5, G["text"]))
    mc_text(cv, bx0 + 20 + len(name) * 8, 5, "Lv.30", rgba("e8c46a"))
    pct = f"{BOSS * 100 // BOSS_MAX}%"
    mc_text(cv, bx0 + bw - 5 - mc_width(pct), 5, pct, G["text"])
    gauge(cv, bx0 + 5, bx0 + bw - 5, 17, bgh, BOSS / BOSS_MAX, G["boss"], (36, 10, 8, 255),
          trail=BOSS_LOST / BOSS_MAX, heavy=heavy)
    for q in (1, 2, 3):
        qx = bx0 + 5 + (bw - 10) * q // 4
        over(cv, (qx, 17, qx + 1, 17 + bgh), (0, 0, 0, 160))
    return cv, icons, top, jp


LABELS = {
    "base": ("C8（基準）", "C8 をそのまま。数字と日本語だけ決定済みのマイクラ標準に直した"),
    "silhouette": ("仮説1　箱が多い", "別々の箱6つ → 1枚の形にまとめる。ボス・バフ・通知は板なしで浮かせる"),
    "material": ("仮説2　素材が冷たい", "石の模様と灰色の鉄 → ほぼ黒の無地に金の細い縁取り。配置は同じ"),
    "gauge": ("仮説3　ゲージが細い", "4px の線 → 7px の光沢つき（明るい縁・上に光・下は濃い）。配置と素材は同じ"),
}
ORDER = ("base", "silhouette", "material", "gauge")
JPFONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"


def caption(img, title, sub):
    f1, f2 = ImageFont.truetype(JPFONT, 28), ImageFont.truetype(JPFONT, 20)
    out = Image.new("RGBA", (img.width, img.height + 84), (24, 24, 28, 255))
    d = ImageDraw.Draw(out)
    d.text((16, 8), title, font=f1, fill=(255, 255, 255))
    d.text((16, 48), sub, font=f2, fill=(190, 190, 196))
    out.alpha_composite(img, (0, 84))
    return out


def main():
    hud.OUT.mkdir(parents=True, exist_ok=True)
    full, close = {}, {}
    for v in ORDER:
        r = build(v)
        img2 = hud.compose(r, 2)
        img2.save(hud.OUT / f"why-{v}-2x.png")
        img4 = hud.compose(r, 4)
        close[v] = img4.crop((136 * 4, 186 * 4, 344 * 4, 270 * 4))
        full[v] = img2
    gap = 16
    cells = [caption(full[v], *LABELS[v]) for v in ORDER]
    cw, ch = cells[0].size
    sheet = Image.new("RGBA", (cw * 2 + gap, ch * 2 + gap), (12, 12, 14, 255))
    for i, c in enumerate(cells):
        sheet.alpha_composite(c, ((i % 2) * (cw + gap), (i // 2) * (ch + gap)))
    sheet.convert("RGB").save(hud.OUT / "why-sheet.png")
    cells = [caption(close[v], *LABELS[v]) for v in ORDER]
    cw, ch = cells[0].size
    sheet = Image.new("RGBA", (cw * 2 + gap, ch * 2 + gap), (12, 12, 14, 255))
    for i, c in enumerate(cells):
        sheet.alpha_composite(c, ((i % 2) * (cw + gap), (i // 2) * (ch + gap)))
    sheet.convert("RGB").save(hud.OUT / "why-closeups.png")
    print(f"wrote {hud.OUT}")


if __name__ == "__main__":
    main()
