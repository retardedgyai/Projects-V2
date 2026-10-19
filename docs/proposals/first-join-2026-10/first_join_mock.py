"""First-join screen mocks (flow + 3 class-select directions). Not game assets.

Logical canvas 640x360, saved at 3x (1920x1080) with nearest-neighbour.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[3]
OUT = Path(__file__).parent
FONT = str(REPO / ".tools/core-menu/x12y12pxMaruMinya.ttf")  # the HUD/menu pixel font
WEAP = REPO / "server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/item/weapons/plain"
W, H, S = 640, 360, 3

F16 = ImageFont.truetype(FONT, 12)
F32 = ImageFont.truetype(FONT, 24)

INK = (232, 228, 216)
DIM = (138, 136, 130)
GOLD = (208, 179, 90)
PANEL = (13, 14, 18)
LINE = (44, 46, 54)

CLASSES = [
    # name, role, feel lines, weapon, resource, accent, (attack, guard, ease)
    ("戦士", "崩し屋・前衛", ["鉄の大剣で押しこみ、", "ボスの体勢を崩す。", "崩れた敵は全員の的。"], "plain_greatsword", "闘気", (214, 98, 58), (4, 3, 3)),
    ("アサシン", "印と出入り・近接", ["印をつけて飛びこみ、", "仕留めたら影で抜ける。", "速い手が好きな人へ。"], "plain_dagger", "機会", (156, 98, 214), (5, 1, 1)),
    ("テンプラー", "集めて守る・盾役", ["敵を一か所に集め、", "誓いで仲間を守る。", "倒れない前線。"], "plain_lance", "決意", (96, 144, 214), (2, 5, 3)),
    ("レンジャー", "距離をとる・遠隔", ["下がりながら射ち、", "罠で足を止める。", "当てる気持ちよさ。"], "plain_bow", "集中", (104, 180, 90), (4, 2, 3)),
    ("メイジ", "火と氷・範囲", ["火で焼き、氷で止める。", "術式を重ねて起爆。", "場を支配する魔術師。"], "plain_staff", "術式", (230, 150, 58), (5, 1, 2)),
    ("ヒーラー", "回復と裁き・支援", ["信仰を癒しに使うか、", "裁きの光に使うか。", "仲間の命を預かる。"], "plain_tome", "信仰", (232, 210, 122), (2, 3, 4)),
]
LOCKED = ("星織り師", "メイジLv20で解放")


def weapon(name, big_tome=True):
    im = Image.open(WEAP / f"{name}.png").convert("RGBA")
    im = im.crop(im.getbbox())
    if big_tome and im.height < 48:  # the tome is drawn at 32px; show it at 2x so it reads beside the others
        im = im.resize((im.width * 2, im.height * 2), Image.NEAREST)
    return im


def text(d, xy, s, fill=INK, font=F16, anchor="la", shadow=True):
    if shadow:
        d.text((xy[0] + 1, xy[1] + 1), s, fill=(0, 0, 0), font=font, anchor=anchor)
    d.text(xy, s, fill=fill, font=font, anchor=anchor)


def panel(d, box, fill=PANEL, line=LINE, accent=None):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=fill)
    d.rectangle(box, outline=line)
    d.line((x0 + 1, y0 + 1, x1 - 1, y0 + 1), fill=tuple(min(255, c + 18) for c in line))
    if accent:
        d.line((x0, y0, x1, y0), fill=accent)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def darken(im, k):
    return Image.blend(im, Image.new("RGB", im.size, (0, 0, 0)), k)


# ---------- the harbour backdrop (stand-in for the real in-game camera) ----------

def harbour(dawn=True):
    im = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(im)
    top, low = ((26, 34, 66), (226, 140, 92)) if dawn else ((40, 70, 120), (150, 190, 220))
    horizon = 190
    bands = 12
    for i in range(bands):  # banded sky, no smooth gradient
        y0 = i * horizon // bands
        d.rectangle((0, y0, W, (i + 1) * horizon // bands), fill=mix(top, low, (i / (bands - 1)) ** 1.6))
    d.rectangle((420, 150, 452, 182), fill=(252, 214, 150))  # square sun
    d.rectangle((424, 154, 448, 178), fill=(255, 236, 190))
    for x, y, w in [(60, 60, 70), (250, 40, 90), (500, 80, 60), (150, 110, 50)]:  # block clouds
        d.rectangle((x, y, x + w, y + 8), fill=mix(top, (255, 220, 200), 0.35))
        d.rectangle((x + 10, y - 6, x + w - 14, y), fill=mix(top, (255, 220, 200), 0.3))
    # far headland and lighthouse
    d.polygon([(0, 190), (0, 150), (40, 140), (90, 152), (130, 170), (170, 190)], fill=(40, 46, 58))
    d.rectangle((52, 100, 64, 142), fill=(200, 196, 186))
    for y in range(104, 142, 10):
        d.rectangle((52, y, 64, y + 4), fill=(170, 60, 50))
    d.rectangle((50, 94, 66, 100), fill=(60, 60, 66))
    d.rectangle((54, 88, 62, 94), fill=(255, 220, 120))
    # sea
    sea = [(34, 58, 92), (40, 70, 108), (48, 82, 120), (56, 92, 128)]
    for i, c in enumerate(sea):
        d.rectangle((0, horizon + i * 10, W, H), fill=c)
    for x in range(380, 500, 6):  # sun path glints
        for y in range(horizon + 4, 260, 8):
            if (x * 7 + y * 3) % 5 == 0:
                d.rectangle((x, y, x + 3, y), fill=(250, 200, 150))
    # ship
    d.polygon([(470, 214), (620, 214), (606, 238), (486, 238)], fill=(58, 38, 28))
    d.rectangle((486, 214, 606, 218), fill=(92, 62, 42))
    d.rectangle((540, 120, 543, 214), fill=(70, 50, 36))
    d.polygon([(546, 126), (600, 150), (546, 200)], fill=(220, 210, 190))
    d.polygon([(536, 132), (492, 180), (536, 196)], fill=(206, 196, 176))
    # dock
    d.rectangle((0, 262, W, H), fill=(92, 64, 44))
    for y in range(262, H, 8):
        d.line((0, y, W, y), fill=(70, 48, 32))
    for x in range(0, W, 40):
        off = (x // 40 % 2) * 4
        for y in range(262 + off, H, 16):
            d.line((x, y, x, y + 7), fill=(70, 48, 32))
    d.rectangle((0, 258, W, 262), fill=(120, 86, 58))
    for x in range(20, W, 120):
        d.rectangle((x, 236, x + 8, 262), fill=(78, 54, 36))
        d.rectangle((x - 1, 234, x + 9, 236), fill=(110, 80, 54))
    return im


def figure(im, cx, base, accent, wname, scale=3, lit=True):
    """Box-only player silhouette (Minecraft proportions) holding the class weapon."""
    d = ImageDraw.Draw(im)
    s = scale
    skin, hair, pants = (198, 150, 112), (52, 36, 28), (44, 44, 56)
    tunic = accent
    if not lit:
        skin, hair, pants, tunic = [mix(c, (20, 22, 30), 0.6) for c in (skin, hair, pants, accent)]
    leg_top = base - 12 * s
    body_top = leg_top - 12 * s
    head_top = body_top - 8 * s
    d.rectangle((cx - 4 * s, leg_top, cx - 1, base), fill=pants)
    d.rectangle((cx, leg_top, cx + 4 * s - 1, base), fill=mix(pants, (0, 0, 0), 0.2))
    d.rectangle((cx - 4 * s, body_top, cx + 4 * s - 1, leg_top), fill=tunic)
    d.rectangle((cx - 4 * s, leg_top - 2 * s, cx + 4 * s - 1, leg_top - s), fill=mix(tunic, (0, 0, 0), 0.45))
    d.rectangle((cx - 8 * s, body_top, cx - 4 * s - 1, leg_top), fill=mix(tunic, (0, 0, 0), 0.25))
    d.rectangle((cx + 4 * s, body_top, cx + 8 * s - 1, leg_top), fill=mix(tunic, (0, 0, 0), 0.25))
    d.rectangle((cx - 4 * s, head_top, cx + 4 * s - 1, body_top), fill=skin)
    d.rectangle((cx - 4 * s, head_top, cx + 4 * s - 1, head_top + 2 * s), fill=hair)
    w = weapon(wname, big_tome=False)
    if not lit:
        w = Image.composite(Image.new("RGBA", w.size, (24, 26, 34, 255)), Image.new("RGBA", w.size, (0, 0, 0, 0)), w.split()[3])
    hand = (cx + 6 * s, leg_top - s)
    im.paste(w, (hand[0] - w.width // 2, hand[1] - w.height + 12), w)


def bars(d, x, y, vals, accent, width=100):
    for i, (label, v) in enumerate(zip(["攻め", "守り", "扱いやすさ"], vals)):
        yy = y + i * 20
        text(d, (x, yy), label, DIM)
        bx = x + 72
        for k in range(5):
            c = accent if k < v else (40, 42, 50)
            d.rectangle((bx + k * 16, yy + 3, bx + k * 16 + 12, yy + 9), fill=c)


def button(d, box, label, accent=GOLD, primary=True):
    x0, y0, x1, y1 = box
    panel(d, box, fill=(24, 22, 16) if primary else (20, 21, 26), line=accent if primary else LINE)
    text(d, ((x0 + x1) // 2, (y0 + y1) // 2 + 1), label, accent if primary else INK, anchor="mm")


# ---------- A: dialog ----------

def option_a(sel=0):
    im = darken(harbour(), 0.72)
    d = ImageDraw.Draw(im)
    name, role, feel, wn, res, acc, vals = CLASSES[sel]
    text(d, (W // 2, 16), "職業をえらぶ", INK, anchor="mt")
    # left 40%: list + words
    lx = 24
    for i, c in enumerate(CLASSES):
        y = 48 + i * 24
        on = i == sel
        panel(d, (lx, y, lx + 216, y + 20), fill=(30, 30, 36) if on else (18, 19, 24), line=c[5] if on else LINE)
        d.rectangle((lx, y, lx + 3, y + 20), fill=c[5])
        text(d, (lx + 12, y + 4), c[0], INK if on else DIM)
        text(d, (lx + 208, y + 4), c[1].split("・")[0], c[5] if on else (70, 72, 80), anchor="ra")
    y = 48 + 6 * 24
    panel(d, (lx, y, lx + 216, y + 20), fill=(14, 14, 18))
    text(d, (lx + 12, y + 4), "？？？", (70, 72, 80))
    text(d, (lx + 208, y + 4), "Lv20で解放", (70, 72, 80), anchor="ra")
    # right 60%: showcase card
    rx0, rx1 = 260, 616
    panel(d, (rx0, 48, rx1, 306), accent=acc)
    glow = Image.new("RGB", (150, 210), mix(PANEL, acc, 0.16))
    im.paste(glow, (rx0 + 12, 60))
    d = ImageDraw.Draw(im)
    figure(im, rx0 + 82, 262, acc, wn, scale=3)
    d = ImageDraw.Draw(im)
    tx = rx0 + 176
    text(d, (tx, 60), name, INK, F32)
    text(d, (tx, 98), role, acc)
    d.line((tx, 120, rx1 - 14, 120), fill=LINE)
    for i, l in enumerate(feel):
        text(d, (tx, 128 + i * 20), l)
    bars(d, tx, 200, vals, acc)
    text(d, (tx, 266), f"力の源：{res}", DIM)
    text(d, (tx, 284), "最初の武器：地味な一本", DIM)
    button(d, (rx0, 316, rx0 + 220, 340), f"{name}ではじめる")
    text(d, (rx0 + 232, 320), "港でいつでも選び直せる", DIM)
    return im


# ---------- B: the dock scene ----------

def option_b(sel=0):
    im = harbour()
    pos = [60, 128, 196, 264, 332, 400]
    shade = Image.new("RGB", (W, H), (0, 0, 0))
    im = Image.blend(im, shade, 0.25)
    for i, c in enumerate(CLASSES):
        on = i == sel
        if on:
            dd = ImageDraw.Draw(im)
            for k, a in enumerate([0.10, 0.18, 0.26]):  # stepped light column, no smooth glow
                w = 30 - k * 8
                col = Image.new("RGB", (w * 2, 300), c[5])
                reg = im.crop((pos[i] - w, 0, pos[i] + w, 300))
                im.paste(Image.blend(reg, col, a), (pos[i] - w, 0))
        figure(im, pos[i], 300, c[5], c[3], scale=3, lit=on)
        d = ImageDraw.Draw(im)
        text(d, (pos[i], 312), c[0], INK if on else DIM, anchor="mt")
        if on:
            d.polygon([(pos[i] - 5, 162), (pos[i] + 5, 162), (pos[i], 168)], fill=c[5])
    d = ImageDraw.Draw(im)
    text(d, (16, 14), "桟橋に立つ六人から、ひとりを選ぶ", INK)
    text(d, (16, 334), "← → で選ぶ　クリックで決定", DIM)
    # right info panel (atlas style: opaque black, one gold accent)
    name, role, feel, wn, res, acc, vals = CLASSES[sel]
    px = 448
    panel(d, (px, 0, W - 1, H - 1), accent=GOLD)
    text(d, (px + 14, 14), name, INK, F32)
    text(d, (px + 14, 52), role, acc)
    d.line((px + 14, 74, W - 14, 74), fill=LINE)
    for i, l in enumerate(feel):
        text(d, (px + 14, 84 + i * 20), l)
    bars(d, px + 14, 156, vals, acc)
    text(d, (px + 14, 222), f"力の源：{res}", DIM)
    text(d, (px + 14, 242), "港でいつでも選び直せる", DIM)
    button(d, (px + 14, 312, W - 14, 340), "この職業ではじめる")
    return im


# ---------- C: seven banners ----------

def banner(im, box, c, on, locked=False):
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = box
    acc = c[5] if not locked else (80, 82, 92)
    cloth = mix(PANEL, acc, 0.22 if on else 0.08)
    d.rectangle((x0 - 4, y0 - 6, x1 + 4, y0 - 2), fill=(70, 60, 50))  # rod
    d.rectangle((x0 - 6, y0 - 7, x0 - 3, y0 - 1), fill=GOLD if on else (90, 80, 60))
    d.rectangle((x1 + 3, y0 - 7, x1 + 6, y0 - 1), fill=GOLD if on else (90, 80, 60))
    mid = (x0 + x1) // 2
    d.polygon([(x0, y0), (x1, y0), (x1, y1), (mid, y1 - 14), (x0, y1)], fill=cloth)
    d.line((x0, y0, x0, y1), fill=acc if on else LINE)
    d.line((x1, y0, x1, y1), fill=acc if on else LINE)
    d.line((x0, y1, mid, y1 - 14), fill=acc if on else LINE)
    d.line((mid, y1 - 14, x1, y1), fill=acc if on else LINE)
    d.rectangle((x0 + 4, y0 + 4, x1 - 4, y0 + 5), fill=acc)
    if locked:
        text(d, (mid, y0 + 70), "？", (90, 92, 100), F32, anchor="mm")
        text(d, (mid, y1 - 44), c[0], (90, 92, 100), anchor="mt")
        return
    w = weapon(c[3])
    if not on:
        w = Image.blend(Image.new("RGBA", w.size, (0, 0, 0, 0)), w, 1.0)
        a = w.split()[3]
        w = Image.merge("RGBA", [ch.point(lambda v: v * 6 // 10) for ch in w.split()[:3]] + [a])
    cy = y0 + 18 + (100 - w.height) // 2
    im.paste(w, (mid - w.width // 2, max(y0 + 10, cy)), w)
    d = ImageDraw.Draw(im)
    text(d, (mid, y1 - 52), c[0], INK if on else DIM, anchor="mt")
    text(d, (mid, y1 - 34), c[1].split("・")[-1], acc if on else (80, 82, 92), anchor="mt")


def option_c(sel=0):
    im = darken(harbour(), 0.62)
    d = ImageDraw.Draw(im)
    text(d, (W // 2, 12), "職業をえらぶ", INK, anchor="mt")
    bw, gap = 76, 8
    x = (W - (7 * bw + 6 * gap)) // 2
    for i in range(7):
        on = i == sel
        lift = 8 if on else 0
        box = (x, 48 - lift, x + bw, 214 - lift)
        if i < 6:
            banner(im, box, CLASSES[i], on)
        else:
            banner(im, box, LOCKED + (None, None, None, None), False, locked=True)
        x += bw + gap
    d = ImageDraw.Draw(im)
    name, role, feel, wn, res, acc, vals = CLASSES[sel]
    panel(d, (24, 236, W - 24, 344), accent=acc)
    text(d, (40, 246), name, INK, F32)
    text(d, (40, 284), role, acc)
    text(d, (40, 304), f"力の源：{res}", DIM)
    for i, l in enumerate(feel):
        text(d, (200, 248 + i * 20), l)
    bars(d, 392, 248, vals, acc)
    button(d, (392, 312, W - 40, 336), f"{name}ではじめる")
    text(d, (200, 318), "港でいつでも選び直せる", DIM)
    return im


# ---------- the flow ----------

def scene_arrival():
    im = harbour()
    d = ImageDraw.Draw(im)
    band = Image.new("RGB", (W, 96), (0, 0, 0))
    im.paste(Image.blend(im.crop((0, 120, W, 216)), band, 0.6), (0, 120))
    d = ImageDraw.Draw(im)
    text(d, (W // 2, 134), "PROJECT S", GOLD, F32, anchor="mt")
    text(d, (W // 2, 176), "海の向こうの未開の大陸。開拓港から、旅がはじまる。", INK, anchor="mt")
    text(d, (W // 2, 334), "クリックで進む", DIM, anchor="mt")
    return im


def scene_confirm(sel=0):
    im = darken(harbour(), 0.72)
    d = ImageDraw.Draw(im)
    name, role, feel, wn, res, acc, vals = CLASSES[sel]
    panel(d, (150, 40, 490, 320), accent=GOLD)
    text(d, (W // 2, 52), "この姿で港に降りる", INK, anchor="mt")
    figure(im, 230, 270, acc, wn, scale=3)
    d = ImageDraw.Draw(im)
    rows = [("名前", "あなたのMC名"), ("見た目", "今のスキン"), ("職業", name), ("武器", "地味な一本"), ("力の源", res)]
    for i, (k, v) in enumerate(rows):
        text(d, (300, 100 + i * 26), k, DIM)
        text(d, (370, 100 + i * 26), v, acc if k == "職業" else INK)
    button(d, (300, 282, 474, 306), "はじめる")
    button(d, (166, 282, 290, 306), "もどる", primary=False)
    return im


def scene_harbour():
    im = harbour(dawn=False)
    d = ImageDraw.Draw(im)
    d.rectangle((260, 200, 380, 262), fill=(150, 120, 90))  # stand-in for the 遠征受付 building
    d.polygon([(250, 200), (320, 168), (390, 200)], fill=(150, 60, 50))
    text(d, (320, 140), "遠征受付", GOLD, anchor="mt")
    d.polygon([(314, 118), (326, 118), (320, 128)], fill=GOLD)
    panel(d, (430, 12, 628, 74), accent=GOLD)
    text(d, (442, 20), "最初の目的", GOLD)
    text(d, (442, 44), "遠征受付で話を聞く")
    return im


def flow():
    thumbs = [scene_arrival(), option_c(0), scene_confirm(0), scene_harbour()]
    caps = [
        ("① 着く", ["船が開拓港に着く", "タイトルと世界の一言"]),
        ("② 職業をえらぶ", ["6職＋鍵つきの1枠", "（下の3案から見た目を選ぶ）"]),
        ("③ 確かめる", ["名前・見た目はMCのまま", "職業と最初の武器を確認"]),
        ("④ 港へ", ["船を降りると", "最初の目的が光る"]),
    ]
    tw, th = 300, 169
    sheet = Image.new("RGB", (4 * tw + 5 * 20, th + 120), (16, 17, 22))
    d = ImageDraw.Draw(sheet)
    for i, (im, (t, lines)) in enumerate(zip(thumbs, caps)):
        x = 20 + i * (tw + 20)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, 20))
        d.rectangle((x - 1, 19, x + tw, 20 + th), outline=LINE)
        text(d, (x, th + 32), t, GOLD)
        for k, l in enumerate(lines):
            text(d, (x, th + 56 + k * 20), l, INK)
        if i < 3:
            ax = x + tw + 4
            d.polygon([(ax, 20 + th // 2 - 6), (ax + 12, 20 + th // 2), (ax, 20 + th // 2 + 6)], fill=GOLD)
    return sheet


def save(im, name, scale=S):
    im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(OUT / name)


if __name__ == "__main__":
    save(option_a(0), "option-a-dialog.png")
    save(option_b(0), "option-b-dock.png")
    save(option_c(0), "option-c-banners.png")
    save(flow(), "flow.png", 2)
    print("ok")
