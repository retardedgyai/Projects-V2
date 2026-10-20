"""Warrior skill art: one recognisable object per skill, drawn by hand (2026-10-20, Creator: 「技ごとに一目で分かる物を
手で描くところから直して」).

The warrior's motif is red-hot iron. Every shape below is placed by hand (explicit points, no noise, no random):
  轟進 a riveted iron wedge, its leading edge white-hot          地割り the crack star of broken ground
  押し薙ぎ the after-image of the great blade                       地走り a ridge of iron fangs breaking the ground
  根張り an anchor sigil, iron stakes and chain                     鉄槌 the riveted war hammer and its stamp
  and the shared hit flame in four frames.
Flat tones only (outline, deep red, red, cream, white core; four irons). No gradients, no anti-aliasing.

    python3 scripts/warrior_vfx_art.py   # sheet .tools/apage/warrior-art.png
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]

K = (52, 8, 14)        # outline
D = (140, 16, 30)      # deep red
R = (226, 40, 46)      # red
C = (255, 214, 150)    # cream
W = (255, 250, 236)    # white core
I0, I1, I2, I3 = (24, 20, 24), (40, 34, 40), (62, 54, 62), (96, 88, 96)   # iron: shadow .. highlight
RIVET = (150, 140, 146)


def canvas(w, h):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def fill(d, pts, c): d.polygon(pts, fill=c + (255,))
def line(d, pts, c, w=1): d.line(pts, fill=c + (255,), width=w)
def px(d, x, y, c): d.point((x, y), fill=c + (255,))


def rivet(d, x, y):
    fill(d, [(x, y), (x + 1, y), (x + 1, y + 1), (x, y + 1)], RIVET)
    px(d, x + 1, y + 1, I0)


# ---------------------------------------------------------------- hit flame, 4 frames (32 x 40)
def hit_flame(frame):
    img, d = canvas(32, 40)
    if frame == 0:  # the flash: a low white burst
        fill(d, [(6, 39), (4, 32), (9, 27), (12, 30), (16, 20), (20, 30), (23, 26), (28, 32), (26, 39)], K)
        fill(d, [(8, 38), (7, 33), (10, 30), (13, 32), (16, 24), (19, 32), (22, 29), (25, 33), (24, 38)], C)
        fill(d, [(11, 38), (12, 34), (16, 28), (20, 34), (21, 38)], W)
        return img
    if frame == 3:  # dying: broken tongues, no core
        for pts in ([(5, 39), (4, 33), (8, 28), (9, 35), (11, 39)], [(13, 39), (14, 26), (17, 18), (18, 30), (19, 39)],
                    [(22, 39), (23, 31), (27, 24), (26, 34), (28, 39)]):
            fill(d, pts, K)
        fill(d, [(15, 38), (16, 27), (17, 24), (17, 38)], D)
        return img
    outer = [(4, 39), (2, 30), (5, 22), (3, 14), (8, 19), (10, 8), (13, 16), (16, 2), (19, 15), (22, 7), (24, 18),
             (29, 12), (27, 24), (30, 31), (28, 39)]
    red = [(6, 38), (5, 30), (7, 24), (6, 19), (9, 22), (11, 13), (13, 20), (16, 7), (18, 19), (21, 12), (22, 21),
           (26, 17), (25, 26), (27, 32), (26, 38)]
    cream = [(9, 38), (8, 31), (10, 26), (12, 24), (14, 26), (16, 13), (18, 25), (20, 22), (22, 27), (24, 32), (23, 38)]
    white = [(12, 38), (12, 32), (14, 29), (16, 22), (18, 29), (20, 32), (20, 38)]
    fill(d, outer, K); fill(d, red, R)
    if frame == 1:
        fill(d, cream, C); fill(d, white, W)
    else:  # frame 2 cools: cream shrinks to the base, the white is gone
        fill(d, [(9, 38), (9, 32), (12, 28), (16, 20), (19, 29), (23, 33), (23, 38)], C)
        line(d, [(16, 7), (16, 2)], K)
    return img


# ---------------------------------------------------------------- 轟進: the iron wedge (40 x 24), pointing right
def wedge():
    img, d = canvas(40, 24)
    fill(d, [(2, 2), (24, 2), (38, 12), (24, 21), (2, 21), (7, 12)], K)               # silhouette, notched back
    fill(d, [(4, 4), (23, 4), (35, 12), (23, 19), (4, 19), (9, 12)], I1)              # iron body
    fill(d, [(4, 4), (23, 4), (35, 12), (9, 12)], I2)                                  # lit upper bevel
    line(d, [(5, 5), (22, 5)], I3)                                                     # top highlight
    line(d, [(23, 4), (35, 12), (23, 19)], C, 1)                                       # hot leading edge
    line(d, [(25, 5), (36, 12), (25, 18)], W, 1)
    line(d, [(10, 12), (33, 12)], R)                                                   # molten seam along the ridge
    for x in (8, 14, 20):
        rivet(d, x, 7); rivet(d, x, 15)
    for y, x0 in ((6, 0), (12, 1), (17, 0)):                                           # heat streaks behind
        line(d, [(x0, y), (x0 + 3, y)], R)
    return img


# ---------------------------------------------------------------- 地割り: the crack star (48 x 48, on the ground)
CRACKS = [
    [(24, 24), (30, 20), (33, 13), (38, 10), (41, 3)],
    [(24, 24), (31, 26), (37, 24), (43, 28), (47, 27)],
    [(24, 24), (27, 31), (25, 37), (29, 42), (28, 47)],
    [(24, 24), (18, 29), (14, 27), (9, 33), (3, 35)],
    [(24, 24), (19, 19), (20, 12), (15, 8), (13, 1)],
    [(24, 24), (16, 22), (8, 20), (2, 14)],
]
FORKS = [[(33, 13), (36, 17)], [(37, 24), (39, 19)], [(25, 37), (21, 40)], [(14, 27), (12, 22)],
         [(20, 12), (25, 9)], [(8, 20), (6, 25)], [(43, 28), (44, 33)], [(9, 33), (10, 38)]]


def crack_star(cool=0):
    """cool 0 = just struck (white core), 1 = red, 2 = embers (deep red only)."""
    img, d = canvas(48, 48)
    rim, mid, core = ((K, R, W), (K, D, R), (K, D, None))[cool]
    for path in CRACKS: line(d, path, rim, 4)
    for path in FORKS: line(d, path, rim, 2)
    for path in CRACKS: line(d, path, mid, 2)
    for path in FORKS: px(d, *path[1], mid)
    if core:
        for path in CRACKS: line(d, path[:3], core, 1)
    # the crater: a broken octagon with chipped rim
    fill(d, [(19, 20), (23, 17), (28, 18), (31, 22), (30, 27), (26, 31), (21, 30), (17, 26)], K)
    fill(d, [(20, 21), (23, 19), (27, 19), (29, 22), (28, 27), (25, 29), (21, 28), (19, 25)], mid)
    if core: fill(d, [(22, 22), (26, 21), (27, 25), (24, 27), (21, 25)], C if cool == 0 else D)
    if cool == 0: fill(d, [(23, 23), (25, 23), (25, 25), (23, 25)], W)
    return img


# ---------------------------------------------------------------- 押し薙ぎ: the blade's after-image (64 x 28)
def sweep():
    img, d = canvas(64, 28)
    # a crescent: the outer arc is the blade's edge (white-hot), the inner arc fades into broken red
    outer = [(2, 26), (6, 17), (13, 10), (22, 5), (32, 3), (42, 4), (51, 8), (58, 14), (62, 22), (63, 27)]
    inner = [(56, 27), (52, 20), (45, 14), (37, 11), (29, 11), (21, 13), (14, 17), (8, 23), (5, 27)]
    fill(d, outer + inner, K)
    o2 = [(6, 25), (9, 18), (15, 12), (23, 7), (32, 5), (42, 6), (50, 10), (57, 16), (60, 23), (60, 26)]
    i2 = [(57, 26), (53, 19), (45, 13), (37, 12), (29, 12), (21, 14), (14, 18), (9, 24), (7, 26)]
    fill(d, o2 + i2, R)
    line(d, [(23, 6), (32, 4), (42, 5), (50, 9), (57, 15), (60, 22)], C, 2)          # the edge, hot toward the head
    line(d, [(36, 4), (42, 5), (50, 9), (57, 15), (61, 23)], W, 1)
    for x, y in ((8, 21), (11, 17), (15, 14), (19, 11)):                              # the tail breaks into chips
        fill(d, [(x - 3, y + 2), (x - 1, y + 2), (x - 1, y + 4), (x - 3, y + 4)], D)
    for x, y in ((24, 15), (31, 14), (40, 15), (47, 18)):                             # sparks thrown off the inside
        px(d, x, y, C); px(d, x + 1, y + 1, R)
    return img


# ---------------------------------------------------------------- 地走り: a ridge of iron fangs (64 x 20, side view)
FANGS = [(0, 9, 8), (7, 7, 13), (14, 8, 10), (21, 9, 15), (29, 8, 11), (36, 9, 16), (44, 8, 12), (51, 9, 14), (58, 6, 9)]


def fang_ridge():
    img, d = canvas(64, 20)
    for x, w, hgt in FANGS:                                       # x, base width, height
        base, tip = 19, 19 - hgt
        fill(d, [(x, base), (x + w // 2, tip), (x + w, base)], K)
        fill(d, [(x + 1, base), (x + w // 2, tip + 2), (x + w - 1, base)], I1)
        line(d, [(x + 1, base - 1), (x + w // 2, tip + 2)], I3)                     # lit left edge
        line(d, [(x + w // 2, tip + 2), (x + w - 1, base - 1)], I0)                 # shaded right edge
        line(d, [(x + w // 2, tip), (x + w // 2, tip + 2)], C)                      # red-hot point
        px(d, x + w // 2, tip + 3, R)
    fill(d, [(0, 17), (63, 17), (63, 19), (0, 19)], K)                             # the torn ground at the foot
    line(d, [(0, 18), (63, 18)], R)
    for x in (5, 19, 33, 47, 60): px(d, x, 18, C)
    return img


# ---------------------------------------------------------------- 根張り: anchor sigil (48 x 48), stake (8 x 32), chain link
def anchor_sigil(cool=0):
    img, d = canvas(48, 48)
    rim, mid = (K, R) if cool == 0 else (K, D)
    stakes = [(24, 3), (39, 9), (45, 24), (39, 39), (24, 45), (9, 39), (3, 24), (9, 9)]
    ring = stakes + stakes[:1]
    line(d, ring, rim, 3); line(d, ring, mid, 1)                                   # the chain between stakes
    for x, y in stakes:
        fill(d, [(x - 2, y - 2), (x + 2, y - 2), (x + 2, y + 2), (x - 2, y + 2)], rim)
        fill(d, [(x - 1, y - 1), (x + 1, y - 1), (x + 1, y + 1), (x - 1, y + 1)], C if cool == 0 else R)
    # the anchor: ring, shaft, stock, flukes
    line(d, [(24, 12), (24, 36)], rim, 4); line(d, [(24, 13), (24, 35)], mid, 2)
    d.ellipse((20, 8, 28, 16), outline=rim + (255,), width=3); d.ellipse((21, 9, 27, 15), outline=mid + (255,), width=1)
    line(d, [(17, 18), (31, 18)], rim, 4); line(d, [(18, 18), (30, 18)], mid, 2)
    line(d, [(13, 30), (16, 36), (24, 39), (32, 36), (35, 30)], rim, 4)
    line(d, [(14, 31), (17, 36), (24, 38), (31, 36), (34, 31)], mid, 2)
    fill(d, [(11, 28), (15, 27), (14, 32)], rim); fill(d, [(37, 28), (33, 27), (34, 32)], rim)    # fluke barbs
    if cool == 0: line(d, [(24, 20), (24, 33)], W, 1)
    return img


def stake():
    img, d = canvas(8, 32)
    fill(d, [(1, 0), (6, 0), (6, 24), (4, 31), (3, 31), (1, 24)], K)
    fill(d, [(2, 1), (5, 1), (5, 24), (4, 29), (3, 29), (2, 24)], I1)
    line(d, [(2, 1), (2, 24)], I3); line(d, [(5, 1), (5, 24)], I0)
    for y in (3, 12):                                                                 # iron bands
        line(d, [(1, y), (6, y)], I3); line(d, [(1, y + 1), (6, y + 1)], I0)
    line(d, [(3, 25), (3, 29)], C); line(d, [(4, 25), (4, 29)], R)                    # the hot point
    fill(d, [(1, 0), (6, 0), (6, 1), (1, 1)], C)                                      # struck head, glowing
    return img


def chain_link():
    img, d = canvas(12, 6)
    d.rounded_rectangle((0, 0, 11, 5), radius=2, outline=K + (255,), width=2)
    d.rounded_rectangle((1, 1, 10, 4), radius=1, outline=I2 + (255,), width=1)
    line(d, [(2, 1), (9, 1)], I3)
    return img


# ---------------------------------------------------------------- 鉄槌: hammer head side (32 x 16), face (16 x 16), stamp
def hammer_side():
    img, d = canvas(32, 16)
    fill(d, [(0, 0), (31, 0), (31, 15), (0, 15)], K)
    fill(d, [(1, 1), (30, 1), (30, 14), (1, 14)], I1)
    fill(d, [(1, 1), (30, 1), (30, 4), (1, 4)], I2); line(d, [(1, 1), (30, 1)], I3)
    fill(d, [(1, 12), (30, 12), (30, 14), (1, 14)], I0)
    fill(d, [(13, 1), (18, 1), (18, 14), (13, 14)], I2)                               # the collar around the haft
    line(d, [(13, 1), (13, 14)], I0); line(d, [(18, 1), (18, 14)], I0)
    for x in (3, 9, 22, 28):
        rivet(d, x, 3); rivet(d, x, 10)
    line(d, [(1, 7), (12, 7)], R); line(d, [(19, 8), (30, 8)], R)                     # molten seams
    px(d, 6, 7, C); px(d, 25, 8, C)
    line(d, [(0, 0), (0, 15)], C); line(d, [(31, 0), (31, 15)], C)                    # both faces red-hot at the rim
    return img


def hammer_face():
    img, d = canvas(16, 16)
    fill(d, [(0, 0), (15, 0), (15, 15), (0, 15)], K)
    fill(d, [(1, 1), (14, 1), (14, 14), (1, 14)], R)
    fill(d, [(3, 3), (12, 3), (12, 12), (3, 12)], C)
    line(d, [(3, 7), (12, 7)], W); line(d, [(7, 3), (7, 12)], W)                       # the stamp's cross
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)): rivet(d, x, y)
    return img


def hammer_stamp(cool=0):
    """The mark the hammer leaves: its square face pressed into the ground, cracks out of the corners."""
    img, d = canvas(48, 48)
    rim, mid, core = ((K, R, C), (K, D, R), (K, D, None))[cool]
    fill(d, [(13, 13), (35, 13), (35, 35), (13, 35)], rim)
    fill(d, [(15, 15), (33, 15), (33, 33), (15, 33)], mid)
    if core:
        line(d, [(16, 24), (32, 24)], core, 2); line(d, [(24, 16), (24, 32)], core, 2)
    for x, y in ((16, 16), (31, 16), (16, 31), (31, 31)):
        fill(d, [(x, y), (x + 1, y), (x + 1, y + 1), (x, y + 1)], rim)
    for path in ([(13, 13), (8, 9), (6, 3)], [(35, 13), (40, 8), (46, 7)], [(35, 35), (39, 41), (40, 47)],
                 [(13, 35), (7, 38), (1, 41)], [(24, 13), (25, 6)], [(35, 24), (43, 25)]):
        line(d, path, rim, 3); line(d, path, mid, 1)
    return img


ART = [('命中の炎（4コマ）', [hit_flame(k) for k in range(4)]), ('轟進：鉄の楔', [wedge()]),
       ('地割り：割れた地面の紋', [crack_star(k) for k in range(3)]), ('押し薙ぎ：大刃の残像', [sweep()]),
       ('地走り：鉄の牙の列', [fang_ridge()]), ('根張り：錨の紋・杭・鎖', [anchor_sigil(0), stake(), chain_link()]),
       ('鉄槌：大槌の側面・打つ面・打ち跡', [hammer_side(), hammer_face(), hammer_stamp(0), hammer_stamp(1)])]


def sheet(out, scale=6):
    font = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 22, index=0)
    rows = []
    for label, imgs in ART:
        h = max(i.height for i in imgs) * scale
        w = sum(i.width * scale + 24 for i in imgs)
        rows.append((label, imgs, w, h))
    W_ = max(r[2] for r in rows) + 48
    H_ = sum(r[3] + 60 for r in rows) + 24
    img = Image.new('RGB', (W_, H_), (34, 30, 34))
    d = ImageDraw.Draw(img)
    y = 16
    for label, imgs, w, h in rows:
        d.text((24, y), label, font=font, fill=(240, 232, 222))
        x = 24
        for i in imgs:
            big = i.resize((i.width * scale, i.height * scale), Image.NEAREST)
            # a grass patch behind each piece, to judge it on the ground it will sit on
            bg = Image.new('RGB', big.size, (92, 134, 56))
            img.paste(bg, (x, y + 36)); img.paste(big, (x, y + 36), big)
            x += big.width + 24
        y += h + 60
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


if __name__ == '__main__':
    sheet(ROOT / '.tools/apage/warrior-art.png')
    print('wrote .tools/apage/warrior-art.png')
