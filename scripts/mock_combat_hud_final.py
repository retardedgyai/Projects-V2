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
    """Three stacked iron blades per side (the L3 wings): light row over a dark row, the top blade
    sweeping up a step every 3 px. Fixes for the chopped look: roots start under the number plaque,
    and each blade's last 2 px keep only the light row, so it ends in a point, not a flat cut."""
    D = hud.D
    root = C + side * 10
    fill = {}
    for dy, length in ((-3, 12), (0, 10), (3, 8)):
        for i in range(length):
            lift = i // 3 if dy < 0 else 0
            x, y = root + side * i, cy + dy - lift
            fill[(x, y)] = D["metal"][0] if dy < 0 else D["metal"][1]
            if i < length - 2:
                fill[(x, y + 1)] = D["metal"][2]
    edge = {(x + a, y + b) for (x, y) in fill for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))} - set(fill)
    for x, y in edge:
        hud.px(cv, x, y, D["out"])
    for (x, y), col in fill.items():
        hud.px(cv, x, y, col)


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


MAT = {  # light, mid, dark
    "steel": ("c4c5ce", "8d8e99", "5e5f6a"), "wood": ("c8945a", "8a5a2e", "5a3618"),
    "leaf": ("9ce86a", "4cb040", "2a6e2a"), "ice": ("e0f6ff", "7ac8f0", "3a7ac0"),
    "bronze": ("f0b060", "b07a2e", "6e4818"), "fire": ("fff0a0", "ff9a2a", "c8401a"),
    "iron": ("7a7888", "46444f", "26242c"), "violet": ("d0a0ff", "9a5af0", "54289c"),
    "gold": ("fff2b0", "f0c040", "a8781c"), "white": ("ffffff", "eee4c8", "c4b080"),
}


def paint(cv, fill):
    """fill: {(x, y): (material, tone 0..2)}; traces the dark outline around it, then paints."""
    edge = {(x + a, y + b) for (x, y) in fill for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))} - set(fill)
    for x, y in edge:
        hud.px(cv, x, y, hud.D["out"])
    for (x, y), (mat, tone) in fill.items():
        hud.px(cv, x, y, hud.rgba(MAT[mat][tone]))


def mirrored(fill_left):
    out = dict(fill_left)
    for (x, y), v in fill_left.items():
        out[(2 * C - x, y)] = v
    return out


def wing_pixels(job, cy):
    """Left wing for each class (mirrored by the caller). Roots start under the number plaque."""
    f = {}
    root = C - 10

    def put(i, y, m, t):
        f[(root - i, y)] = (m, t)

    if job in ("warrior", "templar"):              # stacked blades; templar's top edge is gold
        for dy, length in ((-3, 12), (0, 10), (3, 8)):
            for i in range(length):
                y = cy + dy - (i // 3 if dy < 0 else 0)
                put(i, y, "gold" if job == "templar" else "steel", 0 if dy < 0 or job == "templar" else 1)
                if i < length - 2:
                    put(i, y + 1, "steel", 2 if job == "warrior" else 1)
                if job == "templar" and i < length - 4:
                    put(i, y + 2, "steel", 2)
    elif job == "ranger":                          # a wooden bough with three broad leaves
        for i in range(13):
            put(i, cy + (i // 6), "wood", 1)
            put(i, cy + 1 + (i // 6), "wood", 2)
        # a solid leaf lying along the bough and tilted up: light upper half, darker lower half
        leaf = [(1, 0, 1), (2, 0, 1), (3, 0, 1), (4, 0, 1), (2, -1, 0), (3, -1, 0), (4, -1, 0), (5, -1, 0),
                (4, -2, 0), (5, -2, 0), (6, -2, 0), (0, 1, 1), (1, 1, 2), (2, 1, 2)]
        for i0, up in ((0, True), (4, False), (8, True)):
            base = cy - 1 if up else cy + 2 + (i0 // 6)
            for dx, dy, tone in leaf:
                put(i0 + dx, base + (dy if up else -dy), "leaf", tone if up else 2 - tone if tone else 1)
    elif job == "mage":                            # three ice shards: straight lit top edge, thinning from below
        for dy, length, rise in ((-4, 11, 2), (0, 9, 0), (4, 6, -1)):
            for i in range(length):
                th = 3 if i < length * .45 else 2 if i < length * .8 else 1
                top = cy + dy - (rise * i) // max(1, length - 1)
                for t in range(th):
                    put(i, top + t, "ice", 0 if t == 0 else 1 if t == 1 else 2)
    elif job == "assassin":                        # two thin black-iron sickle blades curling upward
        for dy, length in ((-1, 13), (3, 9)):
            for i in range(length):
                y = cy + dy - (i * i) // 22
                put(i, y, "violet" if i > length - 4 else "iron", 0)
                if i < length - 3:
                    put(i, y + 1, "iron", 2)
    elif job == "healer":                          # four white-gold feathers fanning out
        for k, (dy, length, droop) in enumerate(((-4, 11, -1), (-1, 12, 0), (2, 10, 1), (5, 7, 2))):
            for i in range(length):
                y = cy + dy + (droop * i) // 6
                put(i, y, "white", 0)
                if i < length - 1:
                    put(i, y + 1, "white", 2 if k % 2 else 1)
    return f


def crown_pixels(job, cy, accent):
    tip = cy - 14
    f = {}

    def put(x, y, m, t):
        f[(x, y)] = (m, t)

    gem = None
    if job == "warrior":                           # three separate sword points on an iron band
        for x, h in ((C - 5, 3), (C, 5), (C + 5, 3)):
            for k in range(h):
                y = tip - 1 - k
                put(x, y, "steel", 0)
                if k < h - 1:
                    put(x + 1, y, "steel", 2)
        for x in range(C - 6, C + 8):
            put(x, tip, "steel", 1)
        gem = (C + 1, tip - 1)
    elif job == "ranger":                          # a forked twig with buds
        for k in range(5):
            put(C, tip - k, "wood", 1)
            put(C - 1 - k // 2, tip - 1 - k, "wood", 0)
            put(C + 1 + k // 2, tip - 1 - k, "wood", 2)
        put(C - 3, tip - 6, "leaf", 0); put(C + 3, tip - 6, "leaf", 1); put(C, tip - 5, "leaf", 0)
    elif job == "mage":                            # a bronze cradle holding a fire gem
        for x in range(C - 4, C + 5):
            put(x, tip, "bronze", 1)
        for x, y in ((C - 4, tip - 1), (C - 4, tip - 2), (C + 4, tip - 1), (C + 4, tip - 2), (C - 3, tip - 3), (C + 3, tip - 3)):
            put(x, y, "bronze", 0 if x < C else 2)
        for x, y, t in ((C, tip - 1, 2), (C - 1, tip - 2, 1), (C, tip - 2, 1), (C + 1, tip - 2, 2), (C - 1, tip - 3, 0),
                        (C, tip - 3, 0), (C + 1, tip - 3, 1), (C, tip - 4, 0), (C, tip - 5, 0)):
            put(x, y, "fire", t)
    elif job == "assassin":                        # two hooked horns
        for side in (-1, 1):
            for k, (dx, dy) in enumerate(((2, 0), (3, -1), (4, -2), (4, -3), (3, -4), (2, -5))):
                put(C + side * dx, tip + dy, "iron", 0 if k < 3 else 1)
        gem = (C, tip - 1)
    elif job == "templar":                         # a gold cross
        for y in range(tip - 6, tip + 1):
            put(C, y, "gold", 0); put(C + 1, y, "gold", 2)
        for x in range(C - 3, C + 5):
            put(x, tip - 4, "gold", 1)
    elif job == "healer":                          # a halo ring with a leaf
        for x in range(C - 5, C + 6):
            for y in (tip - 6, tip - 2):
                put(x, y, "gold", 0 if y == tip - 6 else 1)
        for y in (tip - 5, tip - 4, tip - 3):
            put(C - 6, y, "gold", 0); put(C + 6, y, "gold", 2)
        put(C, tip - 1, "leaf", 0); put(C + 1, tip, "leaf", 1)
    return f, gem


def class_deco(cv, cy, job, accent):
    paint(cv, mirrored(wing_pixels(job, cy)))
    return crown_pixels(job, cy, accent)


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
    deco = set(deco) if isinstance(deco, (set, tuple, list)) else {deco} if deco else set()
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
    gx0, gx1 = X0 + 13, C - edge - (10 if deco & {"fins", "classdeco"} else 2)
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
    mx0, mx1 = C + edge + (10 if deco & {"fins", "classdeco"} else 2), X0 + 170
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
    crown_fill = None
    if "classdeco" in deco:
        crown_fill, crown_gem = class_deco(cv, cy, job, ACCENT[job])
    elif "fins" in deco:
        fins(cv, cy, -1); fins(cv, cy, 1)
    diamond(cv, C, cy, ACCENT[job], r)
    if crown_fill is not None:
        paint(cv, crown_fill)
        if crown_gem:
            hud.gem(cv, crown_gem[0], crown_gem[1], (hud.rgba("000000"), hud.rgba(ACCENT[job]), hud.rgba("ffffff")), 1)
        hud.gem(cv, C, cy + 10, (hud.rgba("000000"), hud.rgba(ACCENT[job]), hud.rgba("ffffff")), 1)
    if "crown" in deco:
        crown(cv, cy, ACCENT[job])
        hud.gem(cv, C, cy + 10, (hud.rgba("000000"), hud.rgba(ACCENT[job]), hud.rgba("ffffff")), 1)
    if "xp" in deco:
        xp_rim(cv, cy, ACCENT[job], r, XP)
    num_col = hud.rgba("ffd77a") if "gold" in deco else WHITE
    ncy = cy
    if "inner" in deco:   # a thin iron diamond inside, framing the number
        for j in range(-7, 8):
            w = 7 - abs(j)
            hud.px(cv, C - w, cy + j, hud.D["metal"][1] if j <= 0 else hud.D["metal"][2])
            hud.px(cv, C + w, cy + j, hud.D["metal"][1] if j <= 0 else hud.D["metal"][2])
    if "inset" in deco:   # a dark recessed plate behind the number
        hud.over(cv, (C - 7, cy - 4, C + 8, cy + 6), hud.D["out"])
        hud.over(cv, (C - 6, cy - 3, C + 7, cy + 5), hud.rgba("0c0c10"))
        hud.over(cv, (C - 6, cy + 4, C + 7, cy + 5), hud.D["slot_hi"])
        ncy = cy + 1
    if "band" in deco:    # a small plaque across the diamond carrying the number
        hud.step_plate(cv, (C - 11, cy - 5, C + 12, cy + 6), cut=1)
        hud.over(cv, (C - 9, cy - 3, C + 10, cy + 4), hud.rgba("0c0c10"))
        for x in (C - 10, C + 10):
            hud.px(cv, x, cy, hud.D["metal"][0])
    if "label" in deco:
        hud.caps(top, C - 3, cy - 10, "LV", hud.rgba("c8c8d4"))
    marks["lv"] = (place_centred(top, "12", C, ncy, num_col), (C - r, cy - r, C + r + 1, cy + r + 1) if "inset" not in deco
                   else (C - 6, cy - 3, C + 7, cy + 5))
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
    jobs = ["warrior", "ranger", "mage", "assassin", "templar", "healer"]
    for job in jobs:
        tag, deco = f"N-{job}", ("classdeco", "xp", "band", "gold")
        print(tag)
        result, marks, geo = design_j(job=job, deco=deco)
        problems = review(marks, geo)
        for p in problems:
            print("PROBLEM:", p)
        for s in (2, 4):
            hud.compose(result, s).save(hud.OUT / f"{tag}-{s}x.png")
        big = hud.compose(result, 8)
        crop = big.crop((8 * (C - 40), 8 * (Y0 - 36), 8 * (C + 40), 8 * (Y0 + 4)))
        crop.save(hud.OUT / f"{tag}-closeup-8x.png")
        if deco:
            crops.append(crop)
        print("review:", "OK" if not problems else f"{len(problems)} problem(s)")
    cw, ch = crops[0].size
    sheet = Image.new("RGBA", (cw * 2 + 16, ch * 3 + 32), (20, 20, 20, 255))
    for k, c in enumerate(crops):
        sheet.paste(c, ((k % 2) * (cw + 16), (k // 2) * (ch + 16)))
    sheet.save(hud.OUT / "N-class-deco.png")


if __name__ == "__main__":
    main()
