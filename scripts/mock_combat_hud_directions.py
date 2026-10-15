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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("A-holo-lines", design_a), ("B-refined-mmo", design_b), ("C-minecraft-native", design_c), ("C2-wynn-structure", design_c2)):
        img = fn()
        for s in (2, 4):
            img.resize((W * s, H * s), Image.NEAREST).save(OUT / f"{name}-{s}x.png")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
