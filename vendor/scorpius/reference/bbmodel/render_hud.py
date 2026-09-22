#!/usr/bin/env python3
"""hudSmoke の実Componentをパックのbitmapフォントで描画する（シェーダー自体は実機確認が必要）。

./gradlew hudSmoke
python3 bbmodel/render_hud.py
"""
import json
import re
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps
from render_lore import FontSet, parse_color, runs

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'build/reports/hud'
FONTS = FontSet()
SCALE = 2
SHADER = ROOT / 'resourcepack_template/assets/minecraft/shaders/core/text.vsh'
DEFINES = {name: float(value) for name, value in re.findall(r'#define\s+(\w+)\s+([0-9.]+)', SHADER.read_text())}


def render(components, width, height, background=None, left_handed=False, offhand=None):
    canvas = Image.new('RGBA', (width * SCALE, height * SCALE), (39, 43, 48, 255))
    d = ImageDraw.Draw(canvas)
    for y in range(height * SCALE):
        tone = int(44 - 19 * y / (height * SCALE))
        d.line((0, y, width * SCALE, y), fill=(tone, tone + 4, tone + 8, 255))
    if background:
        canvas = ImageOps.fit(Image.open(background).convert('RGBA'), canvas.size)
        d = ImageDraw.Draw(canvas)
    if offhand:
        # HUDより先に描くことで、後から重なる外枠がアイテムを隠さないことも確認する。
        hud = ROOT / 'resourcepack/assets/minecraft/textures/gui/sprites/hud'
        side = 'right' if left_handed else 'left'
        frame_x = width // 2 + (91 if left_handed else -120)
        frame = Image.open(hud / f'hotbar_offhand_{side}.png').convert('RGBA')
        canvas.alpha_composite(frame.resize((29*SCALE, 24*SCALE), Image.Resampling.NEAREST),
                               (frame_x*SCALE, (height-23)*SCALE))
        icon = Image.open(offhand).convert('RGBA').resize((16*SCALE, 16*SCALE), Image.Resampling.NEAREST)
        canvas.alpha_composite(icon, ((width//2 + (101 if left_handed else -117))*SCALE, (height-19)*SCALE))
    missing = set()
    for component in components:
        x = 0
        origin = (0, 0)
        for text, style in runs(component, {'font': 'scorpius:hud', 'color': 'white', 'bold': False}):
            for char in text:
                cp = ord(char)
                if style['font'] == 'space:default':
                    if 0xD0000 <= cp <= 0xD2000:
                        x += cp - 0xD1000
                    elif 0xD4000 <= cp < 0xD5800:
                        marker = cp - 0xD4000
                        anchor = (marker // 256) % 8
                        dy = marker % 256 - 128
                        origin = (width if anchor in (1, 4, 7) else width // 2 if anchor in (2, 5) else 0,
                                  (height if 3 <= anchor <= 5 else height // 2 if anchor >= 6 else 0) + dy)
                        if marker // (256 * 8) == DEFINES['BOSS_LINE'] and anchor == 2 and width < DEFINES['BOSS_NARROW_WIDTH']:
                            origin = (origin[0], origin[1] + DEFINES['BOSS_NARROW_Y'])
                    elif not 0xD6000 <= cp < 0xD7800:
                        raise ValueError(f'Unknown space: {cp:X}')
                    continue
                glyph = FONTS.get(style['font']).get(char)
                if glyph is None:
                    missing.add((char, style['font']))
                    continue
                if glyph[0] == 'space':
                    x += glyph[1]
                    continue
                _, advance, cell, scale, ascent, h = glyph
                ink = cell.resize((max(1, round(cell.width * scale * SCALE)), round(h * SCALE)), Image.Resampling.NEAREST)
                color = parse_color(style['color']) or (255, 255, 255)
                r, g, b, a = ink.split()
                ink = Image.merge('RGBA', (r.point(lambda v: v * color[0] // 255),
                                          g.point(lambda v: v * color[1] // 255),
                                          b.point(lambda v: v * color[2] // 255), a))
                if style.get('shadow_color') != 0:
                    shadow = Image.new('RGBA', ink.size, (0, 0, 0, 0))
                    shadow.putalpha(ink.getchannel('A').point(lambda v: v * 3 // 4))
                    canvas.alpha_composite(shadow, (round((origin[0] + x + 1) * SCALE), round((origin[1] - ascent + 1) * SCALE)))
                canvas.alpha_composite(ink, (round((origin[0] + x) * SCALE), round((origin[1] - ascent) * SCALE)))
                if style.get('bold'):
                    canvas.alpha_composite(ink, (round((origin[0] + x + 1) * SCALE), round((origin[1] - ascent) * SCALE)))
                x += advance + int(bool(style.get('bold')))
        assert x == 0, f'Channel advances by {x}px'
    assert not missing, f'Missing glyphs: {missing}'
    # 全情報を併記する画面も出す。狭い画面の画像はHUD自体の較正用。
    if height >= 480:
        from render_lore import draw_line, line_advance
        sidebar = json.loads((OUT / 'sidebar.json').read_text())
        widths = [line_advance(row)[0] for row in sidebar]
        box_w = int(max(widths))
        x = width - box_w - 3
        top = height // 2 + (len(sidebar)-1)*9//3 - len(sidebar)*9
        panel = Image.new('RGBA', canvas.size)
        ImageDraw.Draw(panel).rectangle(((x-2)*SCALE, top*SCALE, (width-1)*SCALE,
                                        (top+len(sidebar)*9)*SCALE), fill=(0, 0, 0, 96))
        canvas.alpha_composite(panel)
        for i, row in enumerate(sidebar):
            dx = (box_w-widths[i])/2 if i == 0 else 0
            draw_line(canvas, x+dx, top+i*9+7, row, [])
    # ホットバーも配布物を用いる。中央HUDとの位置関係を見るための下端基準。
    hotbar = ROOT / 'resourcepack/assets/minecraft/textures/gui/sprites/hud/hotbar.png'
    if hotbar.exists():
        bar = Image.open(hotbar).convert('RGBA')
        bar = bar.resize((182 * SCALE, 22 * SCALE), Image.Resampling.NEAREST)
        canvas.alpha_composite(bar, ((width // 2 - 91) * SCALE, (height - 22) * SCALE))
    hud = ROOT / 'resourcepack/assets/minecraft/textures/gui/sprites/hud'
    for name in ('experience_bar_background', 'experience_bar_progress'):
        bar = Image.open(hud / (name + '.png')).convert('RGBA')
        if name.endswith('progress'):
            bar = bar.crop((0, 0, 72, 5))
        bar = bar.resize((bar.width * SCALE, bar.height * SCALE), Image.Resampling.NEAREST)
        canvas.alpha_composite(bar, ((width // 2 - 91) * SCALE, (height - 29) * SCALE))
    # サンプルのレベル15は、バニラの経験値バー中央の位置へ。
    from render_lore import draw_line
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        draw_line(canvas, width // 2 - 5 + dx, height - 28 + dy,
                  {'text': '15', 'font': 'scorpius:hud', 'color': '#000000'}, [])
    draw_line(canvas, width // 2 - 5, height - 28,
              {'text': '15', 'font': 'scorpius:hud', 'color': '#80ff20'}, [])
    selected = Image.open(hud / 'hotbar_selection.png').convert('RGBA')
    canvas.alpha_composite(selected.resize((24*SCALE, 23*SCALE), Image.Resampling.NEAREST),
                           ((width // 2 - 92)*SCALE, (height - 23)*SCALE))
    d.line(((width // 2 - 3) * SCALE, height // 2 * SCALE,
            (width // 2 + 3) * SCALE, height // 2 * SCALE), fill=(160, 162, 158, 255), width=SCALE)
    d.line((width // 2 * SCALE, (height // 2 - 3) * SCALE,
            width // 2 * SCALE, (height // 2 + 3) * SCALE), fill=(160, 162, 158, 255), width=SCALE)
    suffix = ('-scene' if background else '') + ('-left' if left_handed else '') + ('-offhand' if offhand else '')
    path = OUT / f'hud-{width}x{height}{suffix}.png'
    canvas.save(path)
    print(path.relative_to(ROOT))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--background', type=Path, help='UIを含まない背景画像。実機のスクリーンショットではなく合成プレビューを作る')
    parser.add_argument('--left-handed', action='store_true', help='左利きの実Componentを使う')
    parser.add_argument('--offhand', type=Path, help='位置確認用の16pxアイテム画像。3Dモデルの描画は実機で確認する')
    args = parser.parse_args()
    components = json.loads((OUT / ('components-left.json' if args.left_handed else 'components.json')).read_text())
    for size in ((640, 480), (640, 360), (320, 240)):
        render(components, *size, background=args.background, left_handed=args.left_handed, offhand=args.offhand)
