"""Combat HUD direction J: F1 + the level diamond, with measured placement and a self-review.

    python scripts/mock_combat_hud_final.py

Every text is placed by its real ink bounding box (rendered first, then centred), and review()
measures the placed result: each number's ink centre against the visible part of its gauge, the
level against the diamond, and that nothing covers a hotbar slot's interior. Problems are printed;
the creator only sees an image after this passes.

Writes build/hud-preview/directions/J-final-2x.png, -4x.png and J-final-closeup-8x.png.
"""
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mock_combat_hud_directions as hud  # noqa: E402
from mock_class_icons_v4 import ACCENT  # noqa: E402
from mock_class_sigils import diamond  # noqa: E402  (charcoal inner, matches the plates)

H, W, C = hud.H, hud.W, hud.C
X0, Y0 = hud.HB_X, hud.HB_Y
WHITE = hud.rgba("ffffff")


def ink_box(img):
    a = img.getchannel("A").point(lambda v: 255 if v > 0 else 0)
    return a.getbbox()


def text_layer(s, colour=WHITE, shadow=True):
    tmp = Image.new("RGBA", (200, 20))
    hud.mc_text(tmp, 2, 2, s, colour, shadow=shadow)
    return tmp


def place_centred(cv, s, cx, cy, colour=WHITE, shadow=True, glyph_only_centre=True):
    """Centre the glyph ink (not the shadow) on (cx, cy) in GUI px; returns the ink box placed."""
    glyphs = text_layer(s, colour, shadow=False)
    gx0, gy0, gx1, gy1 = ink_box(glyphs)
    full = text_layer(s, colour, shadow)
    ox = round(cx - (gx0 + gx1 - 1) / 2)
    oy = round(cy - (gy0 + gy1 - 1) / 2)
    cv.alpha_composite(full, (ox, oy))
    return (gx0 + ox, gy0 + oy, gx1 + ox, gy1 + oy)


XP = .62  # progress toward the next level, for the XP-rim variant


def fins(cv, cy, side):
    """Three stacked iron blades off the diamond's side point, longest on top, stepped tips (deco 'fins')."""
    D = hud.D
    x0 = C + side * 13
    for dy, length in ((-3, 9), (0, 7), (3, 5)):
        for i in range(length):
            x = x0 + side * (i + 1)
            lift = i // 3 if dy < 0 else 0          # the top blade sweeps slightly upward
            y = cy + dy - lift
            hud.px(cv, x, y - 1, D["out"])
            hud.px(cv, x, y, D["metal"][0] if dy < 0 else D["metal"][1])
            hud.px(cv, x, y + 1, D["metal"][2] if i < length - 1 else D["out"])
            hud.px(cv, x, y + 2, D["out"])
        hud.px(cv, x0 + side * (length + 1), cy + dy - (length - 1) // 3 * (dy < 0), D["out"])


def crown(cv, cy, accent):
    """Three iron prongs on the top point, a class-colour gem on the middle one (deco 'crown')."""
    D = hud.D
    tip = cy - 14
    for x, h in ((C - 4, 2), (C, 4), (C + 4, 2)):
        for y in range(tip - h, tip + 1):
            hud.px(cv, x - 1, y, D["out"]); hud.px(cv, x + 1, y, D["out"])
            hud.px(cv, x, y, D["metal"][0] if y < tip - h + 2 else D["metal"][1])
        hud.px(cv, x, tip - h - 1, D["out"])
    hud.over(cv, (C - 5, tip, C + 6, tip + 2), D["out"])
    hud.over(cv, (C - 4, tip, C + 5, tip + 1), D["metal"][1])
    hud.gem(cv, C, tip - 5, (hud.rgba("000000"), hud.rgba(accent), hud.rgba("ffffff")), 1)


def xp_rim(cv, cy, accent, r, frac):
    """The class-colour band of the diamond fills clockwise from the top as XP grows (deco 'xp')."""
    import math
    lit, dim = hud.rgba(accent), tuple(v // 3 for v in hud.rgba(accent)[:3]) + (255,)
    for j in range(-r + 2, r - 1):
        w = r - 2 - abs(j)
        for x in range(C - w, C + w + 1):
            if abs(x - C) + abs(j) > r - 4:   # a 2 px band
                ang = (math.degrees(math.atan2(x - C, -j)) + 360) % 360
                hud.px(cv, x, cy + j, lit if ang <= 360 * frac else dim)


def design_j(job="warrior", deco=None):
    cv = hud.world()
    top = Image.new("RGBA", (W, H))
    icons, jp, marks = [], [], {}
    hud.step_plate(cv, (X0 - 1, Y0, X0 + 183, Y0 + 22))
    for i in range(9):
        hud.d_slot(cv, X0 + 2 + 20 * i, Y0 + 2)
    cv.alpha_composite(hud.tex("item/iron_sword"), (X0 + 3, Y0 + 3))
    cv.alpha_composite(hud.tex("item/potion"), (X0 + 3 + 20 * 7, Y0 + 3))
    for k, (name, cd, total, state) in enumerate(hud.SKILL_ROW, start=2):
        x, y = X0 + 3 + 20 * (k - 1), Y0 + 3
        icons.append((name, x, y, state))
        if state == "cd":
            marks[f"cd{k}"] = (place_centred(top, str(cd), x + 7.5, y + 7.5), (x, y, x + 16, y + 16))
    sx = X0 + 1
    for a, b, c, d, col in ((sx, Y0 + 1, sx + 20, Y0 + 2, hud.IRON[0]), (sx, Y0 + 1, sx + 1, Y0 + 21, hud.IRON[0]),
                            (sx, Y0 + 20, sx + 20, Y0 + 21, hud.IRON[2]), (sx + 19, Y0 + 1, sx + 20, Y0 + 21, hud.IRON[2])):
        hud.over(cv, (a, b, c, d), col)
    cv.alpha_composite(hud.tex("gui/sprites/hud/crosshair"), (C - 7, H // 2 - 7))
    # resource strip
    ry = Y0 - 5
    hud.step_plate(cv, (X0 - 1, ry, X0 + 183, ry + 6))
    hud._e_cells(cv, X0 + 2, X0 + 181, ry + 2)
    # HP / MP plates stop under the diamond; gauges stop 2 px short of its edge
    ph, r = 13, 13
    py = ry - ph
    cy = py + 6                                        # diamond centred on the plates' middle row
    edge = r + 1                                       # outline reaches C +- 14
    hud.step_plate(cv, (X0 - 1, py, C - 1, py + ph))       # plates run on under the diamond, so no
    hud.step_plate(cv, (C + 1, py, X0 + 183, py + ph))     # stepped corner shows at the joint
    gx0, gx1 = X0 + 13, C - edge - (11 if deco == "fins" else 2)
    hud.ramp_gauge(cv, gx0, gx1, py + 2, 9, hud.HP / hud.HP_MAX, hud.G["hp"], (34, 10, 14, 255),
                   trail=hud.HP_LOST / hud.HP_MAX)
    w = gx1 - gx0
    f = round(w * hud.HP / hud.HP_MAX)
    bw = round(w * hud.BARRIER / hud.HP_MAX)
    hud.over(cv, (gx0 + f, py + 2, gx0 + f + bw, py + 11), hud.rgba("e3a91c"))
    hud.over(cv, (gx0 + f, py + 2, gx0 + f + bw, py + 3), hud.rgba("ffe066"))
    for seg in range(40, hud.HP_MAX, 40):
        sxp = gx0 + round(w * seg / hud.HP_MAX)
        hud.over(cv, (sxp, py + 2, sxp + 1, py + 11), (0, 0, 0, 150))
    cv.alpha_composite(hud.tex("gui/sprites/hud/heart/container"), (X0 + 2, py + 2))
    cv.alpha_composite(hud.tex("gui/sprites/hud/heart/full"), (X0 + 2, py + 2))
    mx0, mx1 = C + edge + (11 if deco == "fins" else 2), X0 + 170
    hud.ramp_gauge(cv, mx0, mx1, py + 2, 9, hud.MP / hud.MP_MAX, hud.G["mp"], (10, 16, 36, 255))
    for j, row in enumerate(hud.MANA_ICON):
        for i, v in enumerate(row):
            if v == "1":
                hud.px(cv, X0 + 172 + i, py + 2 + j,
                       hud.rgba("000000") if i in (0, 8) or j in (0, 8) else hud.rgba("4aa8ff"))
    hud.px(cv, X0 + 175, py + 5, hud.rgba("cfeaff"))
    gauge_mid = py + 2 + 4                             # gauge rows py+2 .. py+10
    marks["hp"] = (place_centred(cv, f"{hud.HP} / {hud.HP_MAX}", (gx0 + gx1 - 1) / 2, gauge_mid),
                   (gx0, py + 2, gx1, py + 11))
    marks["mp"] = (place_centred(cv, f"{hud.MP} / {hud.MP_MAX}", (mx0 + mx1 - 1) / 2, gauge_mid),
                   (mx0, py + 2, mx1, py + 11))
    # level diamond, class colour on the inner rim, white level number
    if deco == "fins":
        fins(cv, cy, -1); fins(cv, cy, 1)
    diamond(cv, C, cy, ACCENT[job], r)
    if deco == "crown":
        crown(cv, cy, ACCENT[job])
        hud.gem(cv, C, cy + 10, (hud.rgba("000000"), hud.rgba(ACCENT[job]), hud.rgba("ffffff")), 1)
    if deco == "xp":
        xp_rim(cv, cy, ACCENT[job], r, XP)
        marks["lv"] = (place_centred(top, "12", C, cy), (C - r, cy - r, C + r + 1, cy + r + 1))
    else:
        marks["lv"] = (place_centred(top, "12", C, cy), (C - r, cy - r, C + r + 1, cy + r + 1))
    # buffs, aligned to the HP plate's left edge
    by = py - 15
    bx = X0 - 1
    for label, name, sec in hud.BUFFS:
        s = f"{sec}s"
        tw = 15 + hud.mc_width(s) + 3
        hud.step_plate(cv, (bx, by, bx + tw, by + 13))
        icons.append((name, bx + 2, by + 2, "buff10"))
        hud.over(cv, (bx + 2, by + 11, bx + 12, by + 12), hud.G["buff"])
        place_centred(cv, s, bx + 13 + (tw - 15) / 2, by + 6)
        bx += tw + 2
    nw = len(hud.NOTICE) * 8
    jp.append((hud.NOTICE, C - nw // 2, by - 14, hud.rgba("a8d0ff")))
    # boss plate
    bw2 = 230
    bx0 = C - bw2 // 2
    hud.step_plate(cv, (bx0, 2, bx0 + bw2, 26))
    hud.gem(cv, bx0 + 8, 9, hud.G["ruby"], 3)
    name = "裂け目の執行官"
    jp.append((name, bx0 + 16, 5, WHITE))
    place_centred(cv, "Lv.30", bx0 + 20 + len(name) * 8 + hud.mc_width("Lv.30") / 2, 8.5, hud.rgba("ffd77a"))
    pct = f"{hud.BOSS * 100 // hud.BOSS_MAX}%"
    place_centred(cv, pct, bx0 + bw2 - 5 - hud.mc_width(pct) / 2, 8.5)
    hud.ramp_gauge(cv, bx0 + 5, bx0 + bw2 - 5, 17, 5, hud.BOSS / hud.BOSS_MAX, hud.G["boss"], (36, 10, 8, 255),
                   trail=hud.BOSS_LOST / hud.BOSS_MAX)
    for q in (1, 2, 3):
        qx = bx0 + 5 + (bw2 - 10) * q // 4
        hud.over(cv, (qx, 17, qx + 1, 22), (0, 0, 0, 160))
    return (cv, icons, top, jp), marks, {"diamond_bottom": cy + r + 1, "slot_top": Y0 + 2}


def review(marks, geo):
    problems = []
    for key, (ink, box) in marks.items():
        ix = (ink[0] + ink[2] - 1) / 2
        iy = (ink[1] + ink[3] - 1) / 2
        bx = (box[0] + box[2] - 1) / 2
        by = (box[1] + box[3] - 1) / 2
        dx, dy = ix - bx, iy - by
        print(f"  {key:5s} ink centre off by dx={dx:+.1f} dy={dy:+.1f}")
        if abs(dx) > 1 or abs(dy) > 1:
            problems.append(f"{key} is off centre by ({dx:+.1f}, {dy:+.1f})")
        if ink[0] < box[0] or ink[2] > box[2]:
            problems.append(f"{key} spills out of its box")
    if geo["diamond_bottom"] > geo["slot_top"]:
        problems.append(f"diamond covers the hotbar slots by {geo['diamond_bottom'] - geo['slot_top']} px")
    return problems


def main():
    crops = []
    for tag, deco in (("J-final", None), ("K1-fins", "fins"), ("K2-crown", "crown"), ("K3-xp-rim", "xp")):
        print(tag)
        result, marks, geo = design_j(deco=deco)
        problems = review(marks, geo)
        for p in problems:
            print("PROBLEM:", p)
        for s in (2, 4):
            hud.compose(result, s).save(hud.OUT / f"{tag}-{s}x.png")
        big = hud.compose(result, 8)
        crop = big.crop((8 * (C - 60), 8 * (Y0 - 40), 8 * (C + 60), 8 * H))
        crop.save(hud.OUT / f"{tag}-closeup-8x.png")
        if deco:
            crops.append(crop)
        print("review:", "OK" if not problems else f"{len(problems)} problem(s)")
    sheet = Image.new("RGBA", (crops[0].width, sum(c.height for c in crops) + 16 * (len(crops) - 1)), (20, 20, 20, 255))
    y = 0
    for c in crops:
        sheet.paste(c, (0, y)); y += c.height + 16
    sheet.save(hud.OUT / "K1-K3.png")


if __name__ == "__main__":
    main()
