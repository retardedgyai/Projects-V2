"""Approved direction J with one of the three C8 hypotheses applied (direction mocks, not pack output).

    python scripts/mock_combat_hud_j_plus.py

J is drawn by mock_combat_hud_final.design_j unchanged; each variant swaps one drawing helper:
    j           J as approved (warrior, class deco + XP rim + band + gold number)
    shape       one console: HP/MP, resource strip and hotbar share one charcoal outline with tapered
                top ends; buffs and boss lose their plates
    trim        every plate gets a 1px warm gold trim; the hotbar selection frame is gold
    gloss       HP / MP / boss gauges get a gloss band, darker lower half and a lit leading edge

Writes build/hud-preview/directions/jplus-<variant>-2x.png, jplus-sheet.png, jplus-closeups.png.
"""
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
import mock_combat_hud_final as fin  # noqa: E402
import mock_combat_hud_why as why  # noqa: E402

X0, Y0, C = hud.HB_X, hud.HB_Y, hud.C
DECO = ("classdeco", "xp", "band", "gold")
PY = Y0 - 5 - 13                       # top of J's HP / MP plates
ORIG = {k: getattr(hud, k) for k in ("step_plate", "ramp_gauge", "world", "IRON")}


def console(cv, x0, y0, x1, y1, cut=6):
    """Charcoal plate like step_plate, but only the top corners are cut, diagonally."""
    D = hud.D
    for j in range(y0, y1):
        d = max(0, cut - (j - y0))
        a, b = x0 + d, x1 - d
        hud.over(cv, (a, j, b, j + 1), D["out"])
        if j == y0 or b - a <= 2:
            continue
        hud.over(cv, (a + 1, j, b - 1, j + 1), D["plate"])
        if j == y0 + 1 or j - y0 <= cut:
            hud.px(cv, a + 1, j, D["hi"])
            hud.px(cv, b - 2, j, D["hi"])
        if j == y0 + 1:
            hud.over(cv, (a + 1, j, b - 1, j + 1), D["hi"])


def gold_step_plate(cv, box, cut=2):
    ORIG["step_plate"](cv, box, cut)
    x0, y0, x1, y1 = box
    if y1 - y0 < 6 or x1 - x0 < 6:
        return
    G = why.GOLD
    hud.over(cv, (x0 + cut + 1, y0 + 1, x1 - cut - 1, y0 + 2), G[3])
    hud.over(cv, (x0 + cut + 1, y1 - 2, x1 - cut - 1, y1 - 1), G[4])
    hud.over(cv, (x0 + 1, y0 + cut + 1, x0 + 2, y1 - cut - 1), G[3])
    hud.over(cv, (x1 - 2, y0 + cut + 1, x1 - 1, y1 - cut - 1), G[4])


def gloss_gauge(cv, x0, x1, y, h, frac, ramp, empty, grow="right", extra=0.0, trail=0.0):
    why.gauge(cv, x0, x1, y, h, frac, ramp, empty, extra=extra, trail=trail, heavy=True)


def build(variant):
    for k, v in ORIG.items():
        setattr(hud, k, v)
    if variant == "shape":
        def world():
            cv = ORIG["world"]()
            console(cv, X0 - 1, PY, X0 + 183, Y0 + 22)
            return cv

        def plate(cv, box, cut=2):
            if cut == 1:                     # the band across the level diamond stays
                ORIG["step_plate"](cv, box, cut)
        hud.world, hud.step_plate = world, plate
    elif variant == "trim":
        hud.step_plate = gold_step_plate
        hud.IRON = why.GOLD
    elif variant == "gloss":
        hud.ramp_gauge = gloss_gauge
    try:
        result, marks, geo = fin.design_j("warrior", DECO)
    finally:
        for k, v in ORIG.items():
            setattr(hud, k, v)
    problems = fin.review(marks, geo)
    for p in problems:
        print(f"PROBLEM ({variant}):", p)
    return result


LABELS = {
    "j": ("J（承認済み・実装済み）", "いまゲームに入っている形"),
    "shape": ("J＋形をまとめる", "HP/MP・帯・ホットバーを1枚の板に。上の両端を斜めに。バフとボスは板なし"),
    "trim": ("J＋金の縁取り", "全部の板の内側に細い金の線。選択枠も金。形と配置は同じ"),
    "gloss": ("J＋ゲージに光沢", "HP/MP/ボスのゲージに上の光・下の影・明るい先端。太さは同じ"),
}
ORDER = ("j", "shape", "trim", "gloss")


def main():
    full, close = {}, {}
    for v in ORDER:
        r = build(v)
        full[v] = hud.compose(r, 2)
        full[v].save(hud.OUT / f"jplus-{v}-2x.png")
        close[v] = hud.compose(r, 6).crop((6 * (C - 96), 6 * (PY - 32), 6 * (C + 96), 6 * 270))
    for name, imgs in (("jplus-sheet", full), ("jplus-closeups", close)):
        cells = [why.caption(imgs[v], *LABELS[v]) for v in ORDER]
        cw, ch = cells[0].size
        sheet = Image.new("RGBA", (cw * 2 + 16, ch * 2 + 16), (12, 12, 14, 255))
        for i, c in enumerate(cells):
            sheet.alpha_composite(c, ((i % 2) * (cw + 16), (i // 2) * (ch + 16)))
        sheet.convert("RGB").save(hud.OUT / f"{name}.png")
    print(f"wrote {hud.OUT}")


if __name__ == "__main__":
    main()
