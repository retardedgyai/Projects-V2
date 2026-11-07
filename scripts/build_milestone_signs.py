"""The signs that pop up on a milestone (冒険 Lv, スキル Lv, a dungeon's first clear), as title glyphs.

    python3 scripts/build_milestone_signs.py   # -> core-ui-pack assets/projects_milestone, build/milestone_signs.png

RuneForge throws a chunky "LEVEL UP!" in front of the camera with an up arrow and green gems flying off (藍 2026-11-07
「RuneForge みたいなのがあるといいね」; refs/runeforge/frames/05/burst/levelup-18.5s-*). Ours are the 手帳's dots: the
MaruMinya letters thickened, a gold face, a dark rim and a drop below, two lines like theirs. One art dot is four screen
pixels at 1080p at every GUI scale: the title draws its text four times the GUI size, so each GUI scale gets its own
glyph height (sign{2,3,4}; MilestoneSigns.kt picks by the player's scale).
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "server-minestom/src/main/resources/core-ui-pack/assets/projects_milestone"
FONT = ROOT / ".tools/core-menu/x12y12pxMaruMinya.ttf"
SCALES = (2, 3, 4)
FIRST = 0xE000

FACE_TOP, FACE_LOW = np.array([255, 236, 150]), np.array([236, 150, 44])
RIM = (40, 22, 10, 255)
DROP = (120, 60, 18, 255)
GREEN, GREEN_DARK, GREEN_LIGHT = (96, 196, 64, 255), (34, 92, 30, 255), (180, 240, 120, 255)
GOLD, GOLD_DARK = (255, 214, 92, 255), (150, 92, 20, 255)

# id -> (lines, emblem)
SIGNS = {
    "level": (["レベル", "アップ!"], "arrow"),
    "skill": (["スキル", "アップ!"], "arrow"),
    "clear": (["初踏破!"], "crest"),
}


def letters(text: str) -> np.ndarray:
    """The text in MaruMinya's own 12 px grid, one font pixel = 2 dots, thickened a dot so the strokes read big."""
    font = ImageFont.truetype(str(FONT), 12)
    w = int(font.getlength(text)) + 8
    img = Image.new("L", (w, 16)); d = ImageDraw.Draw(img); d.fontmode = "1"
    d.text((2, 1), text, font=font, fill=255)
    m = np.array(img) > 127
    rows, cols = np.where(np.any(m, axis=1))[0], np.where(np.any(m, axis=0))[0]
    m = m[rows[0]:rows[-1] + 1, cols[0]:cols[-1] + 1]          # the bounding box only: the gaps between letters stay
    m = np.kron(m, np.ones((2, 2), bool))
    thick = m.copy(); thick[:, 1:] |= m[:, :-1]; thick[1:, :] |= m[:-1, :]
    # Kana take a second dot (the strokes are few and far apart); kanji keep one, or their strokes run together.
    if not any("\u4e00" <= c <= "\u9fff" for c in text):
        m = thick.copy(); thick[:, 1:] |= m[:, :-1]
    return thick


def dilate(m: np.ndarray, r: int) -> np.ndarray:
    out = np.zeros((m.shape[0] + 2 * r, m.shape[1] + 2 * r), bool)
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if abs(dx) + abs(dy) <= r + 1:
                out[r + dy:r + dy + m.shape[0], r + dx:r + dx + m.shape[1]] |= m
    return out


def word_art(text: str) -> Image.Image:
    """Gold face with a vertical ramp, a 2-dot dark rim, and a 3-dot drop below (the sign's thickness)."""
    face = letters(text)
    r, drop = 2, 3
    rim = dilate(face, r)
    h, w = rim.shape
    img = np.zeros((h + drop, w, 4), np.uint8)
    for k in range(drop, 0, -1):                           # the drop: the rim shape pushed down
        img[k:k + h][rim] = DROP
    img[:h][rim] = RIM
    ys = np.arange(face.shape[0])[:, None] / max(1, face.shape[0] - 1)
    ramp = (FACE_TOP * (1 - ys[..., None]) + FACE_LOW * ys[..., None]).astype(np.uint8)
    ramp = np.broadcast_to(ramp, face.shape + (3,))
    sub = img[r:r + face.shape[0], r:r + face.shape[1]]
    sub[face, :3] = ramp[face]; sub[face, 3] = 255
    shine = face & ~np.roll(face, 1, axis=0)                 # a light top edge on every stroke
    sub[shine] = (255, 250, 220, 255)
    return Image.fromarray(img, "RGBA")


def arrow() -> Image.Image:
    """The up arrow, green with a dark rim, like RuneForge's."""
    img = Image.new("RGBA", (26, 24)); d = ImageDraw.Draw(img)
    d.polygon([(13, 0), (25, 12), (18, 12), (18, 23), (8, 23), (8, 12), (1, 12)], fill=GREEN_DARK)
    d.polygon([(13, 3), (22, 11), (16, 11), (16, 21), (10, 21), (10, 11), (4, 11)], fill=GREEN)
    d.line([(13, 3), (5, 10)], fill=GREEN_LIGHT); d.line([(11, 12), (11, 20)], fill=GREEN_LIGHT)
    return img


def crest() -> Image.Image:
    """A gold eight-point star for a first clear."""
    img = Image.new("RGBA", (26, 24)); d = ImageDraw.Draw(img)
    c = (13, 12)
    pts = []
    for i in range(16):
        a = np.pi * i / 8 - np.pi / 2
        rr = 12 if i % 2 == 0 else 5
        pts.append((c[0] + rr * np.cos(a), c[1] + rr * np.sin(a)))
    d.polygon(pts, fill=GOLD_DARK)
    d.polygon([(c[0] + (x - c[0]) * .78, c[1] + (y - c[1]) * .78) for x, y in pts], fill=GOLD)
    d.rectangle((12, 10, 14, 14), fill=(255, 250, 220, 255))
    return img


def gem(color, dark, n=11) -> Image.Image:
    img = Image.new("RGBA", (n, n)); d = ImageDraw.Draw(img); c = n // 2
    d.polygon([(c, 0), (n - 1, c), (c, n - 1), (0, c)], fill=dark)
    d.polygon([(c, 2), (n - 3, c), (c, n - 3), (2, c)], fill=color)
    d.point([(c - 1, c - 2), (c - 2, c - 1)], fill=(255, 255, 255, 200))
    return img


def plate(w, h, top, low, rim) -> Image.Image:
    """The block behind the words (RuneForge's orange cube, flat): dark rim, a vertical ramp, a lit top row, corners cut."""
    img = Image.new("RGBA", (w, h)); d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=4, fill=rim)
    for y in range(2, h - 2):
        k = (y - 2) / max(1, h - 5)
        d.line([(2, y), (w - 3, y)], fill=tuple(round(top[i] + (low[i] - top[i]) * k) for i in range(3)) + (255,))
    d.line([(3, 2), (w - 4, 2)], fill=(255, 220, 160, 255))
    return img


def sign(lines, emblem) -> Image.Image:
    words = [word_art(t) for t in lines]
    top = arrow() if emblem == "arrow" else crest()
    w = max(max(x.width for x in words), top.width) + 30
    h = top.height - 4 + sum(x.height for x in words) + 2 * (len(words) - 1)
    h += (-h) % 12 + 12                                     # every GUI scale divides it (12 = 2, 3, 4)
    img = Image.new("RGBA", (w, h))
    y = (h - (top.height - 4 + sum(x.height for x in words) + 2 * (len(words) - 1))) // 2
    ww = max(x.width for x in words)
    back = plate(ww + 6, sum(x.height for x in words) + 2 * (len(words) - 1) + 4,
                 *((246, 150, 52), (196, 84, 30), (52, 20, 10, 255)) if emblem == "arrow" else ((232, 108, 150), (150, 44, 92), (44, 12, 30, 255)))
    img.alpha_composite(back, ((w - back.width) // 2, y + top.height - 4 - 2))
    img.alpha_composite(top, ((w - top.width) // 2, y)); y += top.height - 4
    for x in words:
        img.alpha_composite(x, ((w - x.width) // 2, y)); y += x.height + 2
    g = gem(GREEN, GREEN_DARK) if emblem == "arrow" else gem(GOLD, GOLD_DARK)
    small = gem(GREEN, GREEN_DARK, 7) if emblem == "arrow" else gem(GOLD, GOLD_DARK, 7)
    for gx, gy in ((1, h // 3), (w - 12, h // 5), (4, h - 16), (w - 13, h - 22)):
        img.alpha_composite(g, (gx, gy))
    for gx, gy in ((w // 2 - 24, 2), (w // 2 + 18, 6), (12, h // 2 + 6), (w - 9, h // 2)):
        img.alpha_composite(small, (gx, gy))
    return img


def main():
    (PACK / "textures/sign").mkdir(parents=True, exist_ok=True)
    (PACK / "font").mkdir(parents=True, exist_ok=True)
    arts = {}
    for i, (name, (lines, emblem)) in enumerate(SIGNS.items()):
        art = sign(lines, emblem)
        art.save(PACK / f"textures/sign/{name}.png")
        arts[name] = (art, chr(FIRST + i))
    for s in SCALES:
        providers = [{"type": "bitmap", "file": f"projects_milestone:sign/{name}.png", "height": art.height // s,
                      "ascent": art.height // s // 2, "chars": [ch]} for name, (art, ch) in arts.items()]
        (PACK / f"font/sign{s}.json").write_text(json.dumps({"providers": providers}))
    # A preview at 1080p and GUI scale 2 (title = 4 GUI px a glyph px = 8 screen px... i.e. one dot = 4 px).
    sheet = Image.new("RGBA", (sum(a.width * 4 + 40 for a, _ in arts.values()), max(a.height for a, _ in arts.values()) * 4 + 40), (40, 44, 52, 255))
    x = 20
    for art, _ in arts.values():
        sheet.alpha_composite(art.resize((art.width * 4, art.height * 4), Image.NEAREST), (x, 20)); x += art.width * 4 + 40
    (ROOT / "build").mkdir(exist_ok=True)
    sheet.save(ROOT / "build/milestone_signs.png")
    print("MILESTONE_SIGNS", {n: a.size for n, (a, _) in arts.items()})


if __name__ == "__main__":
    main()
