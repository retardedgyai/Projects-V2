"""Diamond skill icons (2026-10-20, Creator: redo the skill icons after black_cat_往昔之笛's pixel-art icon set).

Only the rules are taken from the reference (read pixel by pixel from the original), never a drawing:
- tiny: the diamond is 18 px across, so a symbol has about 14 px;
- the frame is a single 1 px line, yellow, orange on the lower-left, with a 1 px brown shadow under the lower edges;
- the ground is dark in the class colour, in stripes of two tones;
- the symbol is not a picture of an object but the motion itself (a streak, an arc, a burst), placed pixel by
  pixel by hand, brightening from its tail to its head;
- every icon of a class uses the same colours.

Each symbol below is hand-placed: {row: (first column, colours)}, ' ' leaves the ground. Colours 3..7 run from
dark to white-hot. 20x20 masters (the 18 px diamond plus its shadow); the interior is |x-9.5|+|y-9.5| <= 8.

    python3 scripts/build_diamond_skill_icons.py           # preview only: .tools/icons/warrior-diamond.png
    python3 scripts/build_diamond_skill_icons.py install   # write the masters and the manifest, then rebuild the pack

The hotbar HUD shows skill art at 16 px (build_combat_hud reduces the 32 px master by half). So install makes a
16 px version of each icon: the 20 px design's inner 16 px diamond, whose outer ring becomes the frame (the
shadow is dropped). It writes that to assets/core-ui/skills/warrior-diamond/ and its exact 2x copy as the 32 px
master in assets/core-ui/skills/, so the pixels in game are these pixels. 赤熱 has no HUD slot yet, so it only
gets its 16 px original.
The icons are their own frame, so the manifest marks them frameless: menus show them without the square class frame.
"""
import hashlib
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
N = 20
C = 9.5

SETS = {
    'warrior': {'0': (35, 9, 9), '1': (58, 15, 17), '3': (122, 18, 40), '4': (196, 30, 36), '5': (240, 72, 60),
                '6': (255, 170, 150), '7': (255, 236, 220)},
}
YELLOW, ORANGE, SHADOW = (249, 236, 107), (248, 159, 71), (154, 67, 67)

# ---------------------------------------------------------------- hand-placed symbols
DASH = ('rows', {  # 轟進: three streaks rushing right, the middle one the body
    5: (10, "3456"),
    6: (6, "3344566777"),
    7: (9, "334556"),
    8: (10, "3"),
    9: (3, "3344556667777"),
    10: (2, "33445566777777776"),
    11: (3, "3344455566666"),
    12: (8, "3"),
    13: (4, "33445566777"),
    14: (8, "334456"),
})
SLAM = ('cols', {  # 地割り: a beam falling straight down and bursting where it hits, the ground cracking under it
    2: (9, "34"),
    3: (9, "45"),
    4: (9, "45"),
    5: (8, "3556"),
    6: (8, "4567"),
    7: (8, "4567"),
    8: (7, "35677"),
    9: (7, "46777"),
    10: (4, "3  56777  3"),
    11: (3, "34 5677776 43"),
    12: (4, "45677777765"),
    13: (5, "3456776543"),
    14: (6, "3 4554 3"),
    15: (7, "3 33 3"),
    16: (8, "3  3"),
})
ULT = ('rows', {  # 鉄槌: a great block falling, speed lines above it, the shockwave flat along the ground
    3: (8, "3  3"),
    4: (7, "4 34 4"),
    5: (6, "4  45  4"),
    6: (5, "5677777765"),
    7: (5, "4566666654"),
    8: (5, "4555555554"),
    9: (5, "3444444443"),
    10: (5, "3333333333"),
    12: (4, "345677776543"),
    13: (6, "3 4554 3"),
})
RAMPAGE = ('cols', {  # 赤熱: a flame rising, white-hot at the heart
    2: (9, "5"),
    3: (9, "56"),
    4: (9, "456"),
    5: (8, "4566 4"),
    6: (7, "34567 5"),
    7: (6, "34567655"),
    8: (6, "34567765"),
    9: (5, "3456777654"),
    10: (5, "3456777754"),
    11: (5, "3456777654"),
    12: (5, "3445677543"),
    13: (6, "34456543"),
    14: (7, "334433"),
})

FRENZY = ('rows', {  # 狂乱: three claw cuts falling right to left, white at their heads, the last one the widest (the closing downswing)
    4: (11, "4"),
    5: (10, "54"),
    6: (9, "65  4"),
    7: (8, "76  54"),
    8: (7, "76  65  4"),
    9: (10, "76  54"),
    10: (9, "76  65"),
    11: (8, "76  76"),
    12: (11, "76"),
    13: (10, "76"),
    14: (9, "76"),
})
IMPACT = ('cols', {  # 衝撃: the blade driven straight down into the blow, a ring going out round it
    3: (9, "44"),
    4: (9, "55"),
    5: (9, "66"),
    6: (6, "566  665"),
    7: (5, "6   77   6"),
    8: (4, "6   6776   6"),
    9: (4, "7  677776  7"),
    10: (4, "7  677776  7"),
    11: (4, "6   6666   6"),
    12: (5, "6        6"),
    13: (6, "566  665"),
})
LEAP = ('rows', {  # 跳び叩き: one heavy arc up from the left and down onto the right, bursting where it lands
    5: (8, "777"),
    6: (7, "76 77"),
    7: (6, "66   76"),
    8: (6, "65    76"),
    9: (5, "66     76"),
    10: (5, "65     77 4"),
    11: (5, "55   4 77 5"),
    12: (5, "54   56776"),
    13: (6, "4    566"),
})
BRACE = ('cols', {  # 受け止め: a white shield bowed toward the blows, three blows driven into it and stopped there
    4: (9, "76"),
    5: (8, "76"),
    6: (7, "76"),
    7: (6, "76 7654"),
    8: (6, "76"),
    9: (5, "76"),
    10: (5, "76 7765543"),
    11: (6, "76"),
    12: (6, "76 7654"),
    13: (7, "76"),
    14: (8, "76"),
    15: (9, "76"),
})
LAUNCH = ('rows', {  # 斬り上げ: a heavy cut rising from the ground and curling over the top
    4: (9, "76"),
    5: (7, "777657"),
    6: (6, "776    6"),
    7: (6, "76     5"),
    8: (5, "66      4"),
    9: (5, "66"),
    10: (5, "65"),
    11: (5, "55"),
    12: (6, "54"),
    13: (7, "44"),
    14: (8, "43"),
})
AWAKEN = ('rows', {  # 獣の目覚め: everything around drawn in toward a white-hot heart
    3: (9, "33"),
    4: (9, "44"),
    5: (9, "55"),
    6: (6, "3  55  3"),
    7: (7, "456654"),
    8: (7, "567765"),
    9: (3, "34556777765543"),
    10: (3, "34556777765543"),
    11: (7, "567765"),
    12: (7, "456654"),
    13: (6, "3  55  3"),
    14: (9, "55"),
    15: (9, "44"),
    16: (9, "33"),
})

# 2026-11-05 (Creator: line every skill icon up): the skill set after the first-person rework. 狂戦士の甲冑 burns
# the body, so it takes 赤熱's flame; 鉄槌 (retired 全力の一振り) keeps its drawing.
WARRIOR = [('dash', '轟進', DASH), ('slam', '地割り', SLAM), ('whirl', '狂乱', FRENZY),
           ('war_wound', '跳び叩き', LEAP), ('war_guard', '衝撃', IMPACT), ('war_cry', '受け止め', BRACE),
           ('war_breach', '斬り上げ', LAUNCH), ('war_banner', '狂戦士の甲冑', RAMPAGE), ('war_awaken', '獣の目覚め', AWAKEN),
           ('war_ult', '鉄槌', ULT), ('war_rampage', '赤熱', RAMPAGE)]


def r_of(x, y):
    return abs(x - C) + abs(y - C)


def icon(symbol, colours):
    stripes, spec = symbol
    s = {(x0 + i, y): ch for y, (x0, txt) in spec.items() for i, ch in enumerate(txt) if ch != ' '}
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            r = r_of(x, y)
            if r > 10: continue
            if r == 10:
                if y > C: img.putpixel((x, y), SHADOW + (255,))
                continue
            if r == 9:
                c = ORANGE if (y > C and x < C) else YELLOW
            else:
                ch = s.get((x, y)) or str((y if stripes == 'rows' else x) % 2)
                c = colours[ch]
            img.putpixel((x, y), c + (255,))
    return img


def preview(icons, out, scale=8):
    font = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', 22, index=0)
    small = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', 16, index=0)
    cell = N * scale + 24
    sheet = Image.new('RGB', (len(icons) * cell + 24, N * scale + 190), (0, 0, 0))
    d = ImageDraw.Draw(sheet)
    for k, (name, label, img) in enumerate(icons):
        x = 24 + k * cell
        big = img.resize((N * scale, N * scale), Image.NEAREST)
        sheet.paste(big, (x, 16), big)
        tw = d.textlength(label, font=font)
        d.text((x + (N * scale - tw) / 2, N * scale + 22), label, font=font, fill=(236, 226, 206))
    y = N * scale + 66
    d.text((24, y), 'ゲームに近い大きさ（2倍・3倍）', font=small, fill=(170, 164, 154))
    for k, (_, _, img) in enumerate(icons):
        for j, sc in enumerate((2, 3)):
            im = img.resize((N * sc, N * sc), Image.NEAREST)
            sheet.paste(im, (24 + k * cell + j * 56, y + 34 + (10 if sc == 2 else 0)), im)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


def hud16(img):
    small = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r = abs(x - 7.5) + abs(y - 7.5)
            if r > 8: continue
            c = (ORANGE if (y > 7.5 and x < 7.5) else YELLOW) + (255,) if r == 8 else img.getpixel((x + 2, y + 2))
            small.putpixel((x, y), c)
    return small


def install(icons):
    originals = ROOT / 'assets/core-ui/skills/warrior-diamond'
    originals.mkdir(parents=True, exist_ok=True)
    manifest_path = ROOT / 'assets/core-ui/skill-art-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    for name, _, img in icons:
        original = originals / f'{name}.png'
        small = hud16(img)
        small.save(original)
        if name not in manifest['skills']:
            continue
        master = ROOT / f'assets/core-ui/skills/{name}.png'
        small.resize((32, 32), Image.NEAREST).save(master)
        manifest['skills'][name] = {
            'source_sha256': hashlib.sha256(original.read_bytes()).hexdigest(),
            'master_sha256': hashlib.sha256(master.read_bytes()).hexdigest(), 'source_size': [16, 16],
            'master_size': [32, 32], 'preapproved': False,
            'source': f'assets/core-ui/skills/warrior-diamond/{name}.png',
            'authored_by': 'scripts/build_diamond_skill_icons.py', 'frameless': True}
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')


def main():
    icons = [(name, label, icon(sym, SETS['warrior'])) for name, label, sym in WARRIOR]
    if sys.argv[1:] == ['install']:
        install(icons)
    out_dir = ROOT / '.tools/icons'
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, _, img in icons: img.save(out_dir / f'{name}.png')
    preview(icons, out_dir / 'warrior-diamond.png')
    print('wrote', out_dir / 'warrior-diamond.png')


if __name__ == '__main__':
    main()
