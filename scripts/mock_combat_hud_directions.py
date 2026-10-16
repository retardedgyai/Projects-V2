"""Throwaway direction mocks for the combat HUD rework (not the real pack output).

    python scripts/mock_combat_hud_directions.py

Draws the whole 480x270 GUI (GUI scale 4 at 1920x1080) for three directions and writes
build/hud-preview/directions/<name>-2x.png and -4x.png. Vanilla sprites and the ascii font are read
from the local 26.2 client jar for context only; nothing here goes into the pack.
"""
from pathlib import Path
import math
import os
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/hud-preview/directions"
JAR = zipfile.ZipFile(Path(os.environ["APPDATA"]) / ".minecraft/versions/26.2/26.2.jar")
SKILLS = ROOT / "assets/core-ui/skills"
MARU = ImageFont.truetype(str(ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"), 12)
W, H, C = 480, 270, 240
HB_X, HB_Y = C - 91, H - 22


def tex(path):
    with JAR.open(f"assets/minecraft/textures/{path}.png") as f:
        return Image.open(f).convert("RGBA")


ASCII = tex("font/ascii")


def rgba(hex_, a=255):
    hex_ = hex_.lstrip("#")
    return tuple(int(hex_[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def over(canvas, box, color):
    """Alpha-composite a filled rectangle; box is x0,y0,x1,y1 exclusive."""
    x0, y0, x1, y1 = box
    if x1 <= x0 or y1 <= y0:
        return
    canvas.alpha_composite(Image.new("RGBA", (x1 - x0, y1 - y0), color), (x0, y0))


def px(canvas, x, y, color):
    over(canvas, (x, y, x + 1, y + 1), color)


def tinted(image, color):
    out = Image.new("RGBA", image.size, color[:3] + (0,))
    alpha = image.getchannel("A").point(lambda v: v * color[3] // 255)
    out.putalpha(alpha)
    return out


# ---- text -------------------------------------------------------------------------------------

def _mc_glyph(ch):
    code = ord(ch)
    cell = ASCII.crop((code % 16 * 8, code // 16 * 8, code % 16 * 8 + 8, code // 16 * 8 + 8))
    if ch == " ":
        return cell, 4
    cols = [x for x in range(8) if any(cell.getpixel((x, y))[3] for y in range(8))]
    return cell, (max(cols) + 2) if cols else 4


def mc_width(s):
    return sum(_mc_glyph(c)[1] for c in s) - 1


def mc_text(canvas, x, y, s, color, shadow=True, outline=False):
    for ch in s:
        glyph, adv = _mc_glyph(ch)
        if outline:
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                canvas.alpha_composite(tinted(glyph, (0, 0, 0, 255)), (x + dx, y + dy))
        elif shadow:
            shade = tuple(v // 4 for v in color[:3]) + (color[3],)
            canvas.alpha_composite(tinted(glyph, shade), (x + 1, y + 1))
        canvas.alpha_composite(tinted(glyph, color), (x, y))
        x += adv
    return x


def jp_mask(s):
    box = MARU.getbbox(s)
    mask = Image.new("L", (box[2] + 2, 14))
    d = ImageDraw.Draw(mask)
    d.fontmode = "1"
    d.text((0, 0), s, font=MARU, fill=255)
    return mask


def jp_width(s):
    return jp_mask(s).getbbox()[2]


def jp_text(canvas, x, y, s, color, style="shadow"):
    mask = jp_mask(s)
    solid = lambda c: Image.merge("RGBA", [Image.new("L", mask.size, v) for v in c[:3]] + [mask.point(lambda v: v * c[3] // 255)])
    if style == "outline":
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
            canvas.alpha_composite(solid((0, 0, 0, 200)), (x + dx, y + dy))
    elif style == "shadow":
        canvas.alpha_composite(solid(tuple(v // 4 for v in color[:3]) + (color[3],)), (x + 1, y + 1))
    canvas.alpha_composite(solid(color), (x, y))


# ---- world ------------------------------------------------------------------------------------

def world():
    canvas = Image.new("RGBA", (W, H))
    horizon = 112
    grass = tex("block/grass_block_top")
    green = (121, 192, 90)
    sky_top, sky_low = (96, 140, 214), (178, 206, 238)
    for y in range(H):
        for x in range(W):
            if y < horizon:
                t = y / horizon
                c = tuple(int(a + (b - a) * t) for a, b in zip(sky_top, sky_low))
            else:
                z = 900 / (y - horizon + 0.5)
                u = int((x - C) * z / 180) % 16
                v = int(z * 2) % 16
                g = grass.getpixel((u, v))[0] / 255
                base = tuple(int(k * g) for k in green)
                fog = min(1.0, z / 120)
                c = tuple(int(a + (b - a) * fog) for a, b in zip(base, sky_low))
            canvas.putpixel((x, y), c + (255,))
    # distant hills
    for x in range(W):
        top = horizon - int(6 + 5 * math.sin(x / 37) + 3 * math.sin(x / 13 + 1))
        for y in range(top, horizon):
            canvas.putpixel((x, y), (126, 168, 120, 255))
    # a zombie standing in front (2x), stand-in for the boss
    z = tex("entity/zombie/zombie")
    fig = Image.new("RGBA", (16, 32))
    fig.paste(z.crop((8, 8, 16, 16)), (4, 0))
    fig.paste(z.crop((20, 20, 28, 32)), (4, 8))
    fig.paste(z.crop((44, 20, 48, 32)), (0, 8))
    fig.paste(z.crop((44, 20, 48, 32)).transpose(Image.FLIP_LEFT_RIGHT), (12, 8))
    fig.paste(z.crop((4, 20, 8, 32)), (4, 20))
    fig.paste(z.crop((4, 20, 8, 32)).transpose(Image.FLIP_LEFT_RIGHT), (8, 20))
    fig = fig.resize((48, 96), Image.NEAREST)
    canvas.alpha_composite(fig, (300, 60))
    return canvas


def hotbar(canvas, slot_icons=None):
    canvas.alpha_composite(tex("gui/sprites/hud/hotbar"), (HB_X, HB_Y))
    canvas.alpha_composite(tex("item/iron_sword"), (HB_X + 3, HB_Y + 3))
    canvas.alpha_composite(tex("item/potion"), (HB_X + 3 + 20 * 7, HB_Y + 3))
    for i, icon in (slot_icons or {}).items():
        canvas.alpha_composite(icon, (HB_X + 3 + 20 * i, HB_Y + 3))
    canvas.alpha_composite(tex("gui/sprites/hud/hotbar_selection"), (HB_X - 1, HB_Y - 1))
    canvas.alpha_composite(tex("gui/sprites/hud/crosshair"), (C - 7, H // 2 - 7))


# ---- shared HUD state -------------------------------------------------------------------------

HP, HP_MAX, HP_LOST = 132, 240, 28   # HP_LOST = just-taken chunk still fading
MP, MP_MAX = 46, 100
RAGE = 35                            # class resource 0..100
BARRIER = 48
BOSS, BOSS_MAX, BOSS_LOST = 12480, 20000, 900
# (icon, cd seconds remaining, cd total, state) for hotbar keys 2..6
SKILL_ROW = [("war_breach", 0, 8, "ready"), ("war_wound", 8, 12, "cd"), ("whirl", 0, 6, "ready"),
             ("war_counter", 3, 10, "cd"), ("war_cry", 0, 20, "nomana")]
BUFFS = [("砥石", "temp_mace", 96), ("回復", "heal_light", 7)]
NOTICE = "マナが足りません"


def icon(name, size=16):
    with Image.open(SKILLS / f"{name}.png") as f:
        img = f.convert("RGBA")
    if img.width == size:
        return img
    small = img.resize((size, size), Image.LANCZOS)
    small.putalpha(small.getchannel("A").point(lambda v: 255 if v >= 110 else 0))
    return small


def darken(img, k, tint=None):
    out = img.copy()
    pix = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pix[x, y]
            if tint:
                l = (r + g + b) / 3
                r, g, b = (int(l * t / 255) for t in tint)
            pix[x, y] = (int(r * k), int(g * k), int(b * k), a)
    return out


def sweep(canvas, x, y, size, frac, color):
    """Clockwise-from-top shadow over the remaining fraction of a cooldown."""
    cx = cy = (size - 1) / 2
    for j in range(size):
        for i in range(size):
            ang = (math.degrees(math.atan2(i - cx, -(j - cy))) + 360) % 360
            if ang >= 360 * (1 - frac):
                px(canvas, x + i, y + j, color)


def chamfer_box(canvas, box, fill, edge=None, cut=2):
    x0, y0, x1, y1 = box
    for j in range(y0, y1):
        d = max(0, cut - (j - y0), cut - (y1 - 1 - j))
        over(canvas, (x0 + d, j, x1 - d, j + 1), fill)
    if edge:
        for j in range(y0, y1):
            d = max(0, cut - (j - y0), cut - (y1 - 1 - j))
            if j in (y0, y1 - 1):
                over(canvas, (x0 + d, j, x1 - d, j + 1), edge)
            else:
                px(canvas, x0 + d, j, edge)
                px(canvas, x1 - 1 - d, j, edge)


def skill_x(key):  # hotbar key 2..6 -> slot left edge of its 20px cell
    return HB_X + 1 + 20 * (key - 1)


# ---- A: thin holographic lines ----------------------------------------------------------------

def design_a():
    cv = world()
    hotbar(cv)
    cyan, hp_c, mp_c, amber = rgba("6ff3ff"), rgba("ff6474"), rgba("5aa8ff"), rgba("ffc456")
    # crosshair arcs: HP left, MP right, rage ticks below
    cx, cy, r = C, H // 2, 19
    for deg10 in range(1150, 2451, 4):
        deg = deg10 / 10
        t = (deg - 115) / 130
        for rr in (r, r + 1):
            x = round(cx + rr * math.sin(math.radians(deg)))
            y = round(cy - rr * math.cos(math.radians(deg)))
            px(cv, x, y, rgba("ffffff", 40))
            if (1 - t) <= HP / HP_MAX:
                px(cv, x, y, hp_c[:3] + (200,))
            elif (1 - t) <= (HP + HP_LOST) / HP_MAX:
                px(cv, x, y, rgba("ffffff", 150))
            mx = round(cx - rr * math.sin(math.radians(deg)))
            px(cv, mx, y, rgba("ffffff", 40))
            if (1 - t) <= MP / MP_MAX:
                px(cv, mx, y, mp_c[:3] + (200,))
    for i in range(10):
        lit = i < RAGE // 10
        over(cv, (cx - 15 + i * 3, cy + 24, cx - 13 + i * 3, cy + 25), amber[:3] + (220,) if lit else rgba("ffffff", 45))
    # left of the skill row: numbers
    y = HB_Y - 21
    mc_text(cv, HB_X - 46, y, f"{HP}", hp_c)
    mc_text(cv, HB_X - 46 + mc_width(f"{HP}") + 2, y, f"/{HP_MAX}", rgba("ffffff", 150), shadow=False)
    mc_text(cv, HB_X - 46, y + 10, f"{MP}", mp_c)
    mc_text(cv, HB_X - 46 + mc_width(f"{MP}") + 2, y + 10, f"+{BARRIER}", rgba("e8f6ff", 170), shadow=False)
    over(cv, (HB_X - 50, y - 1, HB_X - 49, y + 19), cyan[:3] + (120,))
    mc_text(cv, HB_X - 46, y - 11, "LV12", rgba("ffffff", 170), shadow=False)
    # skills: bracketed cells above hotbar keys 2..6
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        x, y = skill_x(k), HB_Y - 21
        over(cv, (x, y, x + 18, y + 18), (8, 16, 26, 90))
        ic = icon(name)
        col = cyan
        if state == "cd":
            ic, col = darken(ic, .35), rgba("ffffff", 70)
        elif state == "nomana":
            ic, col = darken(ic, .7, tint=(110, 160, 255)), mp_c
        cv.alpha_composite(ic, (x + 1, y + 1))
        for (ax, ay, sx, sy) in ((x, y, 1, 1), (x + 17, y, -1, 1), (x, y + 17, 1, -1), (x + 17, y + 17, -1, -1)):
            for d in range(3):
                px(cv, ax + sx * d, ay, col)
                px(cv, ax, ay + sy * d, col)
        if state == "cd":
            over(cv, (x + 1, y + 17, x + 1 + int(16 * (1 - cd / total)), y + 18), cyan)
            s = str(cd)
            mc_text(cv, x + 9 - mc_width(s) // 2, y + 5, s, rgba("ffffff"))
    # buffs right of the skill row
    bx = skill_x(7) + 2
    for label, name, sec in BUFFS:
        ic = icon(name, 16).resize((8, 8), Image.NEAREST)
        cv.alpha_composite(ic, (bx, HB_Y - 18))
        mc_text(cv, bx + 10, HB_Y - 18, f"{sec}", rgba("e8f6ff", 200), shadow=False)
        bx += 26
    # notice
    w = jp_width(NOTICE)
    jp_text(cv, C - w // 2, HB_Y - 50, NOTICE, rgba("cfe9ff"), style="outline")
    over(cv, (C - w // 2 - 24, HB_Y - 44, C - w // 2 - 4, HB_Y - 43), cyan[:3] + (160,))
    over(cv, (C + w // 2 + 4, HB_Y - 44, C + w // 2 + 24, HB_Y - 43), cyan[:3] + (160,))
    # boss: hairline at top
    name = "裂け目の執行官"
    jp_text(cv, C - 100, 3, name, rgba("ffffff"), style="outline")
    pct = f"{BOSS * 100 // BOSS_MAX}%"
    mc_text(cv, C + 100 - mc_width(pct), 5, pct, rgba("ffffff", 220), shadow=False)
    over(cv, (C - 100, 18, C + 100, 20), rgba("ffffff", 40))
    over(cv, (C - 100, 18, C - 100 + 200 * (BOSS + BOSS_LOST) // BOSS_MAX, 20), rgba("ffffff", 170))
    over(cv, (C - 100, 18, C - 100 + 200 * BOSS // BOSS_MAX, 20), hp_c)
    for e in (C - 102, C + 101):
        over(cv, (e, 16, e + 1, 22), cyan[:3] + (180,))
    return cv


# ---- B: refined MMO console (AION2 / Lost Ark) ------------------------------------------------

def design_b():
    cv = world()
    hotbar(cv)
    panel, edge = (10, 14, 24, 190), rgba("9fb4d0", 110)
    hp_c, hp_d, mp_c, amber = rgba("e8414f"), rgba("6b1820"), rgba("3d8bff"), rgba("ffb43c")
    # player frame left of the hotbar
    fx, fy, fw = HB_X - 110, HB_Y - 6, 106
    chamfer_box(cv, (fx, fy, fx + fw, H - 1), panel, edge, cut=3)
    jp_text(cv, fx + 3, fy - 15, "戦士", rgba("ffffff"), style="outline")
    mc_text(cv, fx + 3 + jp_width("戦士") + 4, fy - 12, "Lv.12", rgba("ffd98a"))
    bx, bw = fx + 4, fw - 8
    over(cv, (bx, fy + 4, bx + bw, fy + 13), hp_d)
    over(cv, (bx, fy + 4, bx + bw * (HP + BARRIER + HP_LOST) // HP_MAX, fy + 13), rgba("ffe2c4"))
    over(cv, (bx, fy + 4, bx + bw * HP // HP_MAX, fy + 13), hp_c)
    over(cv, (bx, fy + 4, bx + bw * HP // HP_MAX, fy + 5), rgba("ff8c96"))
    hp_end = bx + bw * HP // HP_MAX
    over(cv, (hp_end, fy + 4, hp_end + bw * BARRIER // HP_MAX, fy + 13), rgba("dfe9f7"))
    s = f"{HP}/{HP_MAX}"
    mc_text(cv, bx + bw // 2 - mc_width(s) // 2, fy + 5, s, rgba("ffffff"), outline=True)
    over(cv, (bx, fy + 15, bx + bw, fy + 19), rgba("10224a"))
    over(cv, (bx, fy + 15, bx + bw * MP // MP_MAX, fy + 19), mp_c)
    for i in range(10):
        x0 = bx + i * (bw + 1) // 10
        over(cv, (x0, fy + 21, x0 + bw // 10 - 1, fy + 24), amber if i < RAGE // 10 else rgba("3a2a12"))
    # skills above hotbar keys 2..6
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        x, y = skill_x(k) - 1, HB_Y - 23
        e = rgba("ffffff", 200) if state == "ready" else rgba("9fb4d0", 110)
        if state == "nomana":
            e = mp_c
        chamfer_box(cv, (x, y, x + 20, y + 20), panel, e, cut=2)
        ic = icon(name)
        if state == "nomana":
            ic = darken(ic, .75, tint=(120, 160, 255))
        cv.alpha_composite(ic, (x + 2, y + 2))
        if state == "cd":
            sweep(cv, x + 2, y + 2, 16, cd / total, (0, 0, 0, 170))
            s = str(cd)
            mc_text(cv, x + 10 - mc_width(s) // 2, y + 6, s, rgba("ffffff"))
    # right of hotbar: dodge + buffs
    rx = HB_X + 182 + 4
    chamfer_box(cv, (rx, HB_Y - 6, rx + 20, HB_Y + 14), panel, edge, cut=2)
    cv.alpha_composite(icon("dash"), (rx + 2, HB_Y - 4))
    sweep(cv, rx + 2, HB_Y - 4, 16, .4, (0, 0, 0, 170))
    mc_text(cv, rx + 23, HB_Y + 2, "SHIFT", rgba("9fb4d0"), shadow=False)
    by = HB_Y - 23
    bxx = HB_X - 110
    for label, name, sec in BUFFS:
        chamfer_box(cv, (bxx, by - 14, bxx + 12, by - 2), panel, edge, cut=1)
        cv.alpha_composite(icon(name).resize((8, 8), Image.NEAREST), (bxx + 2, by - 12))
        mc_text(cv, bxx + 14, by - 12, f"{sec}s", rgba("dfe7f2"))
        bxx += 34
    # notice banner
    w = jp_width(NOTICE) + 60
    for i in range(w):
        a = int(170 * (1 - abs(i - w / 2) / (w / 2)) ** .6)
        over(cv, (C - w // 2 + i, HB_Y - 62, C - w // 2 + i + 1, HB_Y - 46), (6, 10, 22, a))
    jp_text(cv, C - jp_width(NOTICE) // 2, HB_Y - 60, NOTICE, rgba("9cc4ff"))
    # boss frame
    bw2 = 240
    x0 = C - bw2 // 2
    jp_text(cv, x0, 3, "裂け目の執行官", rgba("ffffff"), style="outline")
    mc_text(cv, x0 + jp_width("裂け目の執行官") + 5, 6, "Lv.30", rgba("ffd98a"))
    chamfer_box(cv, (x0 - 2, 17, x0 + bw2 + 2, 27), panel, edge, cut=3)
    over(cv, (x0 + 1, 19, x0 + bw2 - 1, 25), hp_d)
    over(cv, (x0 + 1, 19, x0 + 1 + (bw2 - 2) * (BOSS + BOSS_LOST) // BOSS_MAX, 25), rgba("ffe2c4"))
    over(cv, (x0 + 1, 19, x0 + 1 + (bw2 - 2) * BOSS // BOSS_MAX, 25), hp_c)
    over(cv, (x0 + 1, 19, x0 + 1 + (bw2 - 2) * BOSS // BOSS_MAX, 20), rgba("ff8c96"))
    over(cv, (C, 18, C + 1, 26), rgba("ffffff", 200))  # phase notch at 50 %
    pct = f"{BOSS * 100 // BOSS_MAX}%"
    mc_text(cv, x0 + bw2 - mc_width(pct), 6, pct, rgba("ffffff"))
    return cv


# ---- C: Minecraft-native (Wynncraft-like): skills live in the hotbar --------------------------

def design_c():
    cv = world()
    icons = {}
    cds = {}
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        ic = icon(name)
        if state == "nomana":
            ic = darken(ic, .45)
        icons[k - 1] = ic
        if state == "cd":
            cds[k - 1] = (cd, total)
    hotbar(cv, icons)
    for i, (cd, total) in cds.items():  # vanilla item-cooldown overlay + count-style seconds
        x, y = HB_X + 3 + 20 * i, HB_Y + 3
        h = round(16 * cd / total)
        over(cv, (x, y + 16 - h, x + 16, y + 16), (255, 255, 255, 127))
        s = str(cd)
        mc_text(cv, x + 17 - mc_width(s) - 1, y + 9, s, rgba("ffffff"))
    # HP / MP where hearts and hunger used to be
    y = HB_Y - 17
    for x0, val, mx, col, dark, txt in ((HB_X, HP, HP_MAX, rgba("d4202c"), rgba("3b0b0e"), f"❤ {HP}/{HP_MAX}"),
                                        (C + 1, MP, MP_MAX, rgba("2f6fe0"), rgba("0b1a3b"), f"{MP}/{MP_MAX} ✦")):
        w = 90
        over(cv, (x0, y, x0 + w, y + 10), rgba("000000", 255))
        over(cv, (x0 + 1, y + 1, x0 + w - 1, y + 9), dark)
        fill = (w - 2) * val // mx
        over(cv, (x0 + 1, y + 1, x0 + 1 + fill, y + 9), col)
        over(cv, (x0 + 1, y + 1, x0 + 1 + fill, y + 2), tuple(min(255, v + 70) for v in col[:3]) + (255,))
        if col == rgba("d4202c"):
            end = x0 + 1 + fill + (w - 2) * BARRIER // mx
            over(cv, (x0 + 1 + fill, y + 1, end, y + 9), rgba("e8b830"))
            over(cv, (end, y + 1, end + (w - 2) * HP_LOST // mx, y + 9), rgba("e8b0a0"))
        mark, txt = txt[0] if txt[0] == "❤" else txt[-1], txt.strip("❤✦ ")
        tx = x0 + w // 2 - (mc_width(txt) + 9) // 2
        heart = ["0110110", "1111111", "1111111", "0111110", "0011100", "0001000"]
        star = ["0001000", "0011100", "1111111", "0011100", "0110110", "0100010"]
        shape, mx0 = (heart, tx) if mark == "❤" else (star, tx + mc_width(txt) + 3)
        for j, row in enumerate(shape):
            for i, v in enumerate(row):
                if v == "1":
                    px(cv, mx0 + i + 1, y + 3 + j, rgba("000000", 160))
                    px(cv, mx0 + i, y + 2 + j, rgba("ffffff"))
        mc_text(cv, tx + (9 if mark == "❤" else 0), y + 1, txt, rgba("ffffff"), outline=True)
    # class resource in the xp-bar slot, level number like vanilla
    over(cv, (HB_X, HB_Y - 6, HB_X + 182, HB_Y - 1), rgba("000000"))
    over(cv, (HB_X + 1, HB_Y - 5, HB_X + 181, HB_Y - 2), rgba("3a2a12"))
    over(cv, (HB_X + 1, HB_Y - 5, HB_X + 1 + 180 * RAGE // 100, HB_Y - 2), rgba("ffaa22"))
    lv = "12"
    mc_text(cv, C - mc_width(lv) // 2, HB_Y - 29, lv, rgba("80ff20"), outline=True)
    # buffs: vanilla effect tiles, top right
    bg = tex("gui/sprites/hud/effect_background")
    ex = W - 25
    for label, name, sec in BUFFS:
        cv.alpha_composite(bg, (ex, 1))
        cv.alpha_composite(icon(name, 16).resize((18, 18), Image.NEAREST) if False else icon(name), (ex + 4, 5))
        s = f"{sec}"
        mc_text(cv, ex + 12 - mc_width(s) // 2, 27, s, rgba("ffffff"))
        ex -= 25
    # notice where the action bar text sits
    jp_text(cv, C - jp_width(NOTICE) // 2, HB_Y - 46, NOTICE, rgba("ff7070"))
    # boss: the vanilla red boss bar
    name = "裂け目の執行官"
    jp_text(cv, C - jp_width(name) // 2, 1, name, rgba("ffffff"))
    bg, prog = tex("gui/sprites/boss_bar/red_background"), tex("gui/sprites/boss_bar/red_progress")
    cv.alpha_composite(bg, (C - 91, 14))
    cv.alpha_composite(prog.crop((0, 0, 182 * BOSS // BOSS_MAX, 5)), (C - 91, 14))
    return cv


# ---- C2: C rebuilt on the Wynncraft 2.0 structure, gunmetal near-future material --------------
# Reference studied: Wynncraft 2.0 HUD (Modrinth "The Wynncraft Pack" gallery). One material for
# everything; HP/MP each in a bevelled plaque (number above a thin bar); a hex level badge between
# them; a segmented strip over a reskinned hotbar.

M = {"out": rgba("07090c"), "body": rgba("1f2730"), "hi": rgba("526170"), "lo": rgba("12171d"),
     "inset": rgba("0c1015"), "inset_hi": rgba("2c3540"), "cyan": rgba("63e6ff")}


def plaque(cv, box):
    x0, y0, x1, y1 = box
    over(cv, (x0 + 1, y0, x1 - 1, y1), M["out"])
    over(cv, (x0, y0 + 1, x1, y1 - 1), M["out"])
    over(cv, (x0 + 1, y0 + 1, x1 - 1, y1 - 1), M["body"])
    over(cv, (x0 + 2, y0 + 1, x1 - 2, y0 + 2), M["hi"])
    over(cv, (x0 + 1, y0 + 2, x0 + 2, y1 - 2), M["hi"][:3] + (120,))
    over(cv, (x0 + 2, y1 - 2, x1 - 2, y1 - 1), M["lo"])


def inset(cv, box):
    x0, y0, x1, y1 = box
    over(cv, (x0, y0, x1, y1), M["inset"])
    over(cv, (x0, y1 - 1, x1, y1), M["inset_hi"])
    over(cv, (x1 - 1, y0, x1, y1), M["inset_hi"])


def thin_bar(cv, x, y, w, frac, col, back, extra=0.0, extra_col=None, trail=0.0):
    inset(cv, (x - 1, y - 1, x + w + 1, y + 4))
    over(cv, (x, y, x + w, y + 3), back)
    f = round(w * frac)
    if trail:
        over(cv, (x, y, x + min(w, round(w * (frac + extra + trail))), y + 3), rgba("f3d6c8"))
    if extra:
        over(cv, (x + f, y, x + min(w, f + round(w * extra)), y + 3), extra_col)
    over(cv, (x, y, x + f, y + 3), col)
    over(cv, (x, y, x + f, y + 1), tuple(min(255, v + 80) for v in col[:3]) + (255,))


def mark(cv, x, y, rows, col):
    for j, row in enumerate(rows):
        for i, v in enumerate(row):
            if v == "1":
                px(cv, x + i + 1, y + j + 1, rgba("000000", 150))
                px(cv, x + i, y + j, col)


HEART = ["01010", "11111", "11111", "01110", "00100"]
DROP = ["00100", "01110", "11111", "11111", "01110"]


def hex_badge(cv, cx, top, text):
    rows = [2, 4, 6, 8, 10, 11, 11, 11, 11, 11, 11, 10, 8, 6, 4, 2]
    for j, half in enumerate(rows):
        over(cv, (cx - half - 1, top + j, cx + half + 1, top + j + 1), M["out"])
    for j, half in enumerate(rows[1:-1], start=1):
        hw = half - 1
        over(cv, (cx - hw, top + j, cx + hw, top + j + 1), M["body"] if j > 2 else M["hi"])
    for j, half in enumerate(rows[2:-2], start=2):
        hw = half - 3
        over(cv, (cx - hw, top + j, cx + hw, top + j + 1), M["inset"])
    over(cv, (cx - 5, top + 13, cx + 5, top + 14), M["cyan"][:3] + (200,))
    mc_text(cv, cx - mc_width(text) // 2, top + 4, text, rgba("e9fbff"))


def hotbar_skin(cv, icons, cds):
    x0, y0 = HB_X, HB_Y
    plaque(cv, (x0 - 1, y0, x0 + 183, y0 + 22))
    for i in range(9):
        sx = x0 + 2 + 20 * i
        inset(cv, (sx, y0 + 2, sx + 18, y0 + 20))
    cv.alpha_composite(tex("item/iron_sword"), (x0 + 3, y0 + 3))
    cv.alpha_composite(tex("item/potion"), (x0 + 3 + 20 * 7, y0 + 3))
    for i, ic in icons.items():
        cv.alpha_composite(ic, (x0 + 3 + 20 * i, y0 + 3))
    for i, (cd, total) in cds.items():
        x, y = x0 + 3 + 20 * i, y0 + 3
        h = round(16 * cd / total)
        over(cv, (x, y + 16 - h, x + 16, y + 16), (255, 255, 255, 127))
        s = str(cd)
        mc_text(cv, x + 17 - mc_width(s), y + 9, s, rgba("ffffff"))
    # selection: cyan frame around slot 1
    sx = x0 + 1
    for (a, b, c, d) in ((sx, y0 + 1, sx + 20, y0 + 2), (sx, y0 + 20, sx + 20, y0 + 21),
                         (sx, y0 + 1, sx + 1, y0 + 21), (sx + 19, y0 + 1, sx + 20, y0 + 21)):
        over(cv, (a, b, c, d), M["cyan"])
    cv.alpha_composite(tex("gui/sprites/hud/crosshair"), (C - 7, H // 2 - 7))


def design_c2():
    cv = world()
    icons, cds = {}, {}
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        ic = icon(name)
        icons[k - 1] = darken(ic, .45) if state == "nomana" else ic
        if state == "cd":
            cds[k - 1] = (cd, total)
    hotbar_skin(cv, icons, cds)
    # segmented class-resource strip spanning the hotbar
    sy = HB_Y - 6
    plaque(cv, (HB_X - 1, sy, HB_X + 183, sy + 6))
    for i in range(20):
        x = HB_X + 2 + i * 9
        over(cv, (x, sy + 2, x + 8, sy + 4), rgba("ffb545") if i < RAGE * 20 // 100 else rgba("3a2c16"))
    # HP / MP plaques
    py, pw = HB_Y - 26, 70
    for x0, val, mx, col, back, glyph, extra in (
            (HB_X - 1, HP, HP_MAX, rgba("e5414e"), rgba("3a1016"), HEART, BARRIER / HP_MAX),
            (HB_X + 183 - pw, MP, MP_MAX, rgba("3b8cff"), rgba("0e1a36"), DROP, 0)):
        plaque(cv, (x0, py, x0 + pw, py + 20))
        s = f"{val} / {mx}"
        mc_text(cv, x0 + pw // 2 - mc_width(s) // 2, py + 3, s, rgba("eef6ff"))
        thin_bar(cv, x0 + 10, py + 13, pw - 14, val / mx, col, back, extra, rgba("dfe9f7"),
                 trail=HP_LOST / HP_MAX if glyph is HEART else 0)
        mark(cv, x0 + 3, py + 12, glyph, col)
    # bridge + hex level badge
    over(cv, (HB_X - 1 + pw, py + 8, HB_X + 183 - pw, py + 11), M["out"])
    over(cv, (HB_X - 1 + pw, py + 9, HB_X + 183 - pw, py + 10), M["hi"])
    hex_badge(cv, C, py + 1, "12")
    # buffs: small plaques above the HP plaque
    bx = HB_X - 1
    for label, name, sec in BUFFS:
        plaque(cv, (bx, py - 15, bx + 30, py - 2))
        cv.alpha_composite(icon(name).resize((9, 9), Image.NEAREST), (bx + 2, py - 13))
        mc_text(cv, bx + 13, py - 12, f"{sec}", rgba("e9fbff"))
        bx += 32
    # notice
    jp_text(cv, C - jp_width(NOTICE) // 2, py - 30, NOTICE, rgba("8fc3ff"), style="outline")
    # boss plaque
    bw = 200
    x0 = C - bw // 2
    plaque(cv, (x0, 2, x0 + bw, 26))
    name = "裂け目の執行官"
    jp_text(cv, x0 + 5, 4, name, rgba("ffffff"))
    pct = f"{BOSS * 100 // BOSS_MAX}%"
    mc_text(cv, x0 + bw - 5 - mc_width(pct), 6, pct, rgba("e9fbff"))
    thin_bar(cv, x0 + 5, 19, bw - 10, BOSS / BOSS_MAX, rgba("e5414e"), rgba("3a1016"), trail=BOSS_LOST / BOSS_MAX)
    over(cv, (C, 18, C + 1, 23), rgba("ffffff", 200))
    return cv


# ---- C3: C2 layout in a PSO2 NGS-like material ---------------------------------------------------
# Reference studied: PSO2 NGS menus/HUD (official manual + player screenshots): deep navy glass
# panels, a cyan header strip with a small diamond, thin bright rims, gold for rank/level.

N = {"glass": (10, 22, 38, 215), "glass2": (16, 34, 56, 215), "rim": rgba("4fd8ff", 150),
     "rim_hi": rgba("b8f3ff"), "line": rgba("2a6b8a"), "gold": rgba("ffd36b"), "gold_d": rgba("8a5a12"),
     "text": rgba("eaf8ff"), "sub": rgba("8fb8cc")}


def hgrad(cv, box, c0, c1):
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        t = (x - x0) / max(1, x1 - x0 - 1)
        over(cv, (x, y0, x + 1, y1), tuple(int(a + (b - a) * t) for a, b in zip(c0, c1)))


def diamond(cv, cx, cy, col, r=2):
    for j in range(-r, r + 1):
        w = r - abs(j)
        over(cv, (cx - w, cy + j, cx + w + 1, cy + j + 1), col)


def glass(cv, box, header=0, cut=3):
    """Navy glass plate; top-left and bottom-right corners cut, bright top rim."""
    x0, y0, x1, y1 = box
    for j in range(y0, y1):
        d = cut - (j - y0) if j - y0 < cut else 0
        d2 = cut - (y1 - 1 - j) if y1 - 1 - j < cut else 0
        over(cv, (x0 + d, j, x1 - d2, j + 1), N["glass"])
        px(cv, x0 + d, j, N["rim"])
        px(cv, x1 - 1 - d2, j, N["rim"])
    over(cv, (x0 + cut, y0, x1, y0 + 1), N["rim_hi"])
    over(cv, (x0, y1 - 1, x1 - cut, y1), N["rim"])
    if header:
        hgrad(cv, (x0 + 1, y0 + 1, x1 - 1, y0 + 1 + header), (40, 150, 200, 200), (20, 60, 100, 40))


def gauge(cv, x, y, w, h, frac, c0, c1, back, ticks=10, extra=0.0, extra_col=None, trail=0.0):
    over(cv, (x - 1, y - 1, x + w + 1, y + h + 1), (0, 0, 0, 200))
    over(cv, (x, y, x + w, y + h), back)
    f = round(w * frac)
    if trail:
        over(cv, (x + f, y, x + min(w, f + round(w * (extra + trail))), y + h), rgba("fff1e0", 220))
    if extra:
        over(cv, (x + f, y, x + min(w, f + round(w * extra)), y + h), extra_col)
    hgrad(cv, (x, y, x + f, y + h), c0, c1)
    over(cv, (x, y, x + f, y + 1), (255, 255, 255, 110))
    if f:
        over(cv, (x + f - 1, y - 1, x + f, y + h + 1), (255, 255, 255, 230))  # bright head
    for i in range(1, ticks):
        tx = x + w * i // ticks
        over(cv, (tx, y + h - 1, tx + 1, y + h), (0, 0, 0, 120))


def design_c3():
    cv = world()
    # hotbar: glass tray; skill keys 2..6 get a cyan cap, item slots stay plain
    x0, y0 = HB_X, HB_Y
    glass(cv, (x0 - 2, y0, x0 + 184, y0 + 22), cut=3)
    for i in range(9):
        sx = x0 + 2 + 20 * i
        over(cv, (sx, y0 + 2, sx + 18, y0 + 20), N["glass2"])
        over(cv, (sx, y0 + 19, sx + 18, y0 + 20), N["line"])
        if 1 <= i <= 5:
            over(cv, (sx, y0 + 2, sx + 18, y0 + 3), N["rim"])
    cv.alpha_composite(tex("item/iron_sword"), (x0 + 3, y0 + 3))
    cv.alpha_composite(tex("item/potion"), (x0 + 3 + 20 * 7, y0 + 3))
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        i = k - 1
        ic = icon(name)
        x, y = x0 + 3 + 20 * i, y0 + 3
        cv.alpha_composite(darken(ic, .45) if state == "nomana" else ic, (x, y))
        if state == "cd":
            h = round(16 * cd / total)
            over(cv, (x, y + 16 - h, x + 16, y + 16), (255, 255, 255, 127))
            s = str(cd)
            mc_text(cv, x + 17 - mc_width(s), y + 9, s, rgba("ffffff"))
        if state == "nomana":
            over(cv, (x - 1, y + 16, x + 17, y + 17), rgba("3b8cff"))
    sx = x0 + 1
    for a, b, c, d in ((sx, y0 + 1, sx + 20, y0 + 2), (sx, y0 + 20, sx + 20, y0 + 21),
                       (sx, y0 + 1, sx + 1, y0 + 21), (sx + 19, y0 + 1, sx + 20, y0 + 21)):
        over(cv, (a, b, c, d), N["gold"])
    diamond(cv, sx + 10, y0, N["gold"], 1)
    cv.alpha_composite(tex("gui/sprites/hud/crosshair"), (C - 7, H // 2 - 7))
    # class resource strip: ember gradient segments
    sy = HB_Y - 6
    glass(cv, (HB_X - 2, sy, HB_X + 184, sy + 6), cut=2)
    lit = RAGE * 20 // 100
    for i in range(20):
        x = HB_X + 2 + i * 9
        if i < lit:
            hgrad(cv, (x, sy + 2, x + 8, sy + 4), (255, 120, 40, 255), (255, 214, 90, 255))
        else:
            over(cv, (x, sy + 2, x + 8, sy + 4), (70, 40, 20, 200))
    over(cv, (HB_X + 2 + lit * 9 - 1, sy + 1, HB_X + 2 + lit * 9, sy + 5), rgba("fff4c0"))
    # HP / MP glass plaques with a header strip
    py, pw = HB_Y - 27, 72
    for px0, label, val, mx, c0, c1, back, extra in (
            (HB_X - 2, "HP", HP, HP_MAX, (200, 30, 60, 255), (255, 110, 120, 255), (50, 10, 20, 230), BARRIER / HP_MAX),
            (HB_X + 184 - pw, "MP", MP, MP_MAX, (40, 90, 255, 255), (90, 200, 255, 255), (10, 20, 50, 230), 0)):
        glass(cv, (px0, py, px0 + pw, py + 21), header=10)
        mc_text(cv, px0 + 4, py + 2, label, rgba("bfe9ff"))
        s = str(val)
        x = px0 + pw - 4 - mc_width(f"/{mx}") - mc_width(s) - 1
        mc_text(cv, x, py + 2, s, N["text"])
        mc_text(cv, px0 + pw - 4 - mc_width(f"/{mx}"), py + 2, f"/{mx}", rgba("a9c8d8"))
        gauge(cv, px0 + 4, py + 12, pw - 8, 5, val / mx, c0, c1, back, extra=extra,
              extra_col=rgba("e8f2ff"), trail=HP_LOST / HP_MAX if label == "HP" else 0)
    # class tab above the HP plaque
    tab = "戦士"
    tw = jp_width(tab) + 14
    glass(cv, (HB_X - 2, py - 15, HB_X - 2 + tw, py - 1), cut=2)
    diamond(cv, HB_X + 3, py - 8, N["gold"], 2)
    jp_text(cv, HB_X + 8, py - 14, tab, N["text"])

    # buffs above the MP plaque: green buff rim + timer
    bx = HB_X + 184
    for label, name, sec in BUFFS:
        bx -= 30
        glass(cv, (bx, py - 15, bx + 28, py - 1), cut=2)
        over(cv, (bx + 2, py - 13, bx + 13, py - 3), rgba("6dffa8", 160))
        cv.alpha_composite(icon(name).resize((9, 9), Image.NEAREST), (bx + 3, py - 12))
        mc_text(cv, bx + 15, py - 11, f"{sec}", N["text"], shadow=False)
    # centre: hex with the class emblem, gold rim
    cx, top = C, py + 1
    rows = [2, 4, 6, 8, 10, 11, 11, 11, 11, 11, 11, 10, 8, 6, 4, 2]
    for j, half in enumerate(rows):
        over(cv, (cx - half - 1, top + j, cx + half + 1, top + j + 1), N["gold_d"])
    for j, half in enumerate(rows[1:-1], start=1):
        over(cv, (cx - half + 1, top + j, cx + half - 1, top + j + 1), N["gold"] if j < 3 else (10, 22, 38, 255))
    for j, half in enumerate(rows[3:-2], start=3):
        over(cv, (cx - half + 2, top + j, cx + half - 2, top + j + 1), (20, 40, 66, 255))
    mc_text(cv, cx - mc_width("12") // 2, top + 4, "12", N["gold"])
    for side in (-1, 1):
        a = cx + side * 13
        b = HB_X - 2 + pw if side < 0 else HB_X + 184 - pw
        lo, hi = min(a, b), max(a, b)
        over(cv, (lo, py + 9, hi, py + 10), N["rim"])
        diamond(cv, (lo + hi) // 2, py + 9, N["rim_hi"], 1)
    # notice: navy band fading at the ends, cyan top line, diamond marks
    nw = jp_width(NOTICE)
    w = nw + 70
    for i in range(w):
        a = int(200 * (1 - abs(i - w / 2) / (w / 2)) ** .7)
        over(cv, (C - w // 2 + i, py - 34, C - w // 2 + i + 1, py - 20), (10, 40, 70, a))
        over(cv, (C - w // 2 + i, py - 34, C - w // 2 + i + 1, py - 33), (80, 220, 255, a))
    diamond(cv, C - nw // 2 - 8, py - 27, N["rim_hi"])
    diamond(cv, C + nw // 2 + 7, py - 27, N["rim_hi"])
    jp_text(cv, C - nw // 2, py - 33, NOTICE, rgba("9fd0ff"))
    # boss: glass plate, red rank diamond, gold Lv, ticked gauge with phase notch
    bw = 220
    bx0 = C - bw // 2
    glass(cv, (bx0, 2, bx0 + bw, 28), header=13, cut=4)
    diamond(cv, bx0 + 8, 8, rgba("ff4060"), 3)
    name = "裂け目の執行官"
    jp_text(cv, bx0 + 15, 2, name, rgba("ffffff"))
    mc_text(cv, bx0 + 19 + jp_width(name), 5, "Lv30", N["gold"])
    pct = f"{BOSS * 100 // BOSS_MAX}%"
    mc_text(cv, bx0 + bw - 6 - mc_width(pct), 5, pct, N["text"])
    gauge(cv, bx0 + 6, 18, bw - 12, 5, BOSS / BOSS_MAX, (170, 20, 50, 255), (255, 90, 90, 255),
          (50, 10, 20, 230), ticks=20, trail=BOSS_LOST / BOSS_MAX)
    over(cv, (C, 16, C + 1, 25), N["gold"])
    return cv


# ---- C4: C2 layout in the AION2 / Lost Ark / Black Desert idiom --------------------------------
# References studied (real screenshots): AION2 (Famitsu play report) — silver crest with the level
# between thin HP/MP bars that run into it, dark slots with thin silver rims, boss bar in a silver
# frame with arrow ends and a medallion; Lost Ark (Icy Veins raid shot) — saturated icons, darkened
# cooldown with a big centred number, buff tiles with timers; Black Desert — diamond level badge
# anchoring the bars.

S = {"dark": rgba("0a0b0d"), "panel": (22, 24, 29, 225), "slot": (14, 15, 18, 235), "rim": rgba("8a929c"),
     "rim_hi": rgba("d6dce2"), "rim_lo": rgba("3d424a"), "text": rgba("f2f4f7"), "sub": rgba("9aa2ab")}
DIGIT3 = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
          "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001001010010",
          "8": "111101111101111", "9": "111101111001111"}


def tiny(cv, x, y, s, col):
    for ch in s:
        bits = DIGIT3[ch]
        for j in range(5):
            for i in range(3):
                if bits[j * 3 + i] == "1":
                    px(cv, x + i, y + j, col)
        x += 4


def arrow_bar(cv, x0, x1, y, h, frac, c0, c1, back, point="left", extra=0.0, trail=0.0):
    """Thin silver-rimmed bar; the outer end is an arrow point, the inner end runs into the crest."""
    total = h + 2
    mid = (total - 1) / 2
    w = x1 - x0
    for j in range(total):
        ind = int(abs(j - mid))
        a, b = (x0 + ind, x1) if point == "left" else (x0, x1 - ind)
        over(cv, (a, y + j, b, y + j + 1), S["dark"])
    fx0, fx1 = x0 + total // 2 + 1, x1 - 1
    if point == "right":
        fx0, fx1 = x0 + 1, x1 - total // 2 - 1
    fw = fx1 - fx0
    over(cv, (fx0, y + 1, fx1, y + h + 1), back)
    f = round(fw * frac)
    if point == "left":  # HP fills from the crest outward? no: from the outer end inward, like AION2
        start = fx0
    else:
        start = fx0
    if trail or extra:
        e = min(fw, f + round(fw * (extra + trail)))
        over(cv, (start, y + 1, start + e, y + h + 1), rgba("fff0e6", 220) if trail else rgba("e8eef5"))
    if extra:
        over(cv, (start + f, y + 1, start + min(fw, f + round(fw * extra)), y + h + 1), rgba("e8eef5"))
    hgrad(cv, (start, y + 1, start + f, y + h + 1), c0, c1)
    over(cv, (start, y + 1, start + f, y + 2), (255, 255, 255, 90))
    over(cv, (start, y + h, start + f, y + h + 1), (0, 0, 0, 70))
    over(cv, (fx0, y, fx1, y + 1), S["rim"])
    over(cv, (fx0, y + h + 1, fx1, y + h + 2), S["rim_lo"])


def crest(cv, cx, top, level):
    halves = [1, 3, 5, 7, 9, 11, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]
    for j, hw in enumerate(halves):
        over(cv, (cx - hw - 1, top + j, cx + hw + 1, top + j + 1), S["dark"])
    for j, hw in enumerate(halves[1:-1], start=1):
        hw -= 1
        hgrad(cv, (cx - hw, top + j, cx + hw, top + j + 1), (225, 230, 236, 255), (110, 118, 128, 255))
    for j, hw in enumerate(halves[4:-4], start=4):
        hw -= 4
        if hw > 0:
            over(cv, (cx - hw, top + j, cx + hw, top + j + 1), (24, 27, 32, 255))
    # small blade emblem + level
    over(cv, (cx, top + 5, cx + 1, top + 11), S["rim_hi"])
    over(cv, (cx - 2, top + 9, cx + 3, top + 10), S["rim_hi"])
    mc_text(cv, cx - mc_width(level) // 2 + 1, top + 12, level, S["text"])


def design_c4():
    cv = world()
    x0, y0 = HB_X, HB_Y
    # hotbar: charcoal tray, silver rim, dark slots with key numbers baked into the texture
    over(cv, (x0 - 1, y0, x0 + 183, y0 + 22), S["dark"])
    over(cv, (x0, y0 + 1, x0 + 182, y0 + 21), S["panel"])
    over(cv, (x0, y0 + 1, x0 + 182, y0 + 2), S["rim"])
    for i in range(9):
        sx = x0 + 2 + 20 * i
        over(cv, (sx, y0 + 2, sx + 18, y0 + 20), S["slot"])
        over(cv, (sx, y0 + 2, sx + 18, y0 + 3), S["rim_lo"])
        over(cv, (sx, y0 + 19, sx + 18, y0 + 20), S["rim_lo"])
    cv.alpha_composite(tex("item/iron_sword"), (x0 + 3, y0 + 3))
    cv.alpha_composite(tex("item/potion"), (x0 + 3 + 20 * 7, y0 + 3))
    for k, (name, cd, total, state) in enumerate(SKILL_ROW, start=2):
        x, y = x0 + 3 + 20 * (k - 1), y0 + 3
        ic = icon(name)
        if state == "cd":
            ic = darken(ic, .45)
        elif state == "nomana":
            ic = darken(ic, .55, tint=(110, 150, 255))
        cv.alpha_composite(ic, (x, y))
        if state == "cd":
            s = str(cd)
            mc_text(cv, x + 8 - mc_width(s) // 2 + 1, y + 4, s, S["text"], outline=True)
    for i in range(9):
        tiny(cv, x0 + 3 + 20 * i, y0 + 3, str(i + 1), (200, 206, 214, 200))
    sx = x0 + 1
    for a, b, c, d in ((sx, y0 + 1, sx + 20, y0 + 2), (sx, y0 + 20, sx + 20, y0 + 21),
                       (sx, y0 + 1, sx + 1, y0 + 21), (sx + 19, y0 + 1, sx + 20, y0 + 21)):
        over(cv, (a, b, c, d), S["rim_hi"])
    cv.alpha_composite(tex("gui/sprites/hud/crosshair"), (C - 7, H // 2 - 7))
    # class resource: ember line between silver hairlines right above the hotbar
    ry = y0 - 4
    over(cv, (x0 - 1, ry, x0 + 183, ry + 4), S["dark"])
    over(cv, (x0, ry + 1, x0 + 182, ry + 3), (60, 32, 14, 255))
    hgrad(cv, (x0, ry + 1, x0 + 182 * RAGE // 100, ry + 3), (220, 90, 20, 255), (255, 210, 90, 255))
    px(cv, x0 + 182 * RAGE // 100, ry + 1, rgba("fff6d0"))
    px(cv, x0 + 182 * RAGE // 100, ry + 2, rgba("fff6d0"))
    # HP / MP: arrow-ended bars running into the crest, number inside
    by, bh = y0 - 16, 9
    arrow_bar(cv, x0 - 1, C - 11, by, bh, HP / HP_MAX, (150, 20, 35, 255), (235, 60, 70, 255),
              (40, 10, 14, 255), "left", extra=BARRIER / HP_MAX, trail=HP_LOST / HP_MAX)
    arrow_bar(cv, C + 11, x0 + 183, by, bh, MP / MP_MAX, (20, 80, 200, 255), (60, 170, 255, 255),
              (10, 18, 40, 255), "right")
    s = f"{HP}/{HP_MAX}"
    mc_text(cv, (x0 + C - 11) // 2 - mc_width(s) // 2 + 3, by + 2, s, S["text"], outline=True)
    s = f"{MP}/{MP_MAX}"
    mc_text(cv, (C + 11 + x0 + 183) // 2 - mc_width(s) // 2 - 3, by + 2, s, S["text"], outline=True)
    crest(cv, C, by - 9, "12")
    # buffs: Lost Ark-style tiles above the HP bar, timers under
    bx = x0 + 4
    for label, name, sec in BUFFS:
        over(cv, (bx, by - 15, bx + 12, by - 3), rgba("2f8f4e"))
        over(cv, (bx + 1, by - 14, bx + 11, by - 4), S["slot"])
        cv.alpha_composite(icon(name, 10), (bx + 1, by - 14))
        tiny(cv, bx + 14, by - 11, str(sec), S["text"])
        bx += 14 + 4 * len(str(sec)) + 4
    # notice
    nw = jp_width(NOTICE)
    w = nw + 80
    for i in range(w):
        a = int(170 * (1 - abs(i - w / 2) / (w / 2)) ** .5)
        over(cv, (C - w // 2 + i, by - 40, C - w // 2 + i + 1, by - 25), (8, 9, 12, a))
    for side in (-1, 1):
        ex = C + side * (nw // 2 + 6)
        over(cv, (min(ex, ex + side * 18), by - 33, max(ex, ex + side * 18), by - 32), S["rim"])
        diamond(cv, ex, by - 33, S["rim_hi"], 1)
    jp_text(cv, C - nw // 2, by - 39, NOTICE, rgba("a8c4ff"))
    # boss: silver frame with arrow ends, gold gauge, medallion, Lv disc
    bw, top = 220, 16
    bx0 = C - bw // 2
    name = "裂け目の執行官"
    lv = "30"
    nx = C - (jp_width(name) + 16) // 2
    for j in range(-5, 6):  # Lv disc
        hw = int((30 - j * j) ** .5)
        over(cv, (nx + 5 - hw - 1, 8 + j, nx + 5 + hw + 2, 8 + j + 1), S["dark"])
        if abs(j) < 5:
            over(cv, (nx + 5 - hw + 1, 8 + j, nx + 5 + hw, 8 + j + 1), (60, 64, 72, 255))
    tiny(cv, nx + 2, 6, lv, S["text"])
    jp_text(cv, nx + 14, 1, name, S["text"], style="outline")
    pct = f"{BOSS * 100 // BOSS_MAX}%"
    mc_text(cv, bx0 + bw - 8 - mc_width(pct), 6, pct, S["text"], outline=True)
    for j in range(9):  # frame with arrow ends both sides
        ind = abs(j - 4)
        over(cv, (bx0 + ind, top + j, bx0 + bw - ind, top + j + 1), S["dark"])
    over(cv, (bx0 + 5, top + 1, bx0 + bw - 5, top + 2), S["rim_hi"])
    over(cv, (bx0 + 5, top + 7, bx0 + bw - 5, top + 8), S["rim"])
    gx0, gx1 = bx0 + 6, bx0 + bw - 6
    gw = gx1 - gx0
    over(cv, (gx0, top + 2, gx1, top + 7), (40, 30, 10, 255))
    over(cv, (gx0, top + 2, gx0 + gw * (BOSS + BOSS_LOST) // BOSS_MAX, top + 7), rgba("fff0d8"))
    hgrad(cv, (gx0, top + 2, gx0 + gw * BOSS // BOSS_MAX, top + 7), (200, 140, 20, 255), (255, 228, 90, 255))
    over(cv, (gx0, top + 2, gx0 + gw * BOSS // BOSS_MAX, top + 3), (255, 255, 255, 110))
    for q in (1, 2, 3):
        qx = gx0 + gw * q // 4
        over(cv, (qx, top + 2, qx + 1, top + 7), (0, 0, 0, 140))
    for side in (bx0 + 2, bx0 + bw - 3):
        diamond(cv, side, top + 4, S["rim_hi"], 2)
    for j in range(-4, 5):  # medallion with a red gem under the centre
        hw = 4 - abs(j)
        over(cv, (C - hw - 1, top + 9 + j + 4, C + hw + 2, top + 10 + j + 4), S["dark"])
    for j in range(-3, 4):
        hw = 3 - abs(j)
        over(cv, (C - hw, top + 13 + j, C + hw + 1, top + 14 + j), S["rim"])
    diamond(cv, C, top + 13, rgba("e8344a"), 1)
    return cv


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("A-holo-lines", design_a), ("B-refined-mmo", design_b), ("C-minecraft-native", design_c),
                     ("C2-wynn-structure", design_c2), ("C3-mmo-glass", design_c3), ("C4-aion-lostark", design_c4)):
        img = fn()
        for s in (2, 4):
            img.resize((W * s, H * s), Image.NEAREST).save(OUT / f"{name}-{s}x.png")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
