"""Composite exported CoreHudLayout glyphs with shipped bitmaps; not a game capture.

Uses actual font ascent, texture cells, component tint and exported cursor positions.
The empty background deliberately excludes the world, hotbar and vanilla overlays.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'


class HudRenderer:
    def __init__(self):
        # Sample at GUI scale 3 directly: the accepted Japanese atlas is 3x
        # source ink. Downsample-to-1 then upscale would destroy its strokes.
        self.raster_scale = 3
        self.fonts = {}
        self.textures = {}

    def glyph(self, font, code):
        if font not in ('projects:core_hud', 'projects:core_spacing', 'projects:warrior_hud_status'):
            raise ValueError('Unreviewed font: ' + font)
        if font not in self.fonts:
            self.fonts[font] = json.loads((ASSETS / ('font/' + font.split(':')[1] + '.json')).read_text())['providers']
        char = chr(code)
        for p in self.fonts[font]:
            if p['type'] == 'space' and char in p['advances']:
                return None, 0
            if p['type'] != 'bitmap':
                continue
            for row, chars in enumerate(p['chars']):
                if char not in chars:
                    continue
                key = p['file']
                if not key.startswith('projects:'):
                    raise ValueError(key)
                if key not in self.textures:
                    with Image.open(ASSETS / ('textures/' + key.split(':')[1])) as source:
                        self.textures[key] = source.convert('RGBA')
                atlas = self.textures[key]
                w, h = atlas.width // len(chars), atlas.height // len(p['chars'])
                col = chars.index(char)
                sprite = atlas.crop((col*w, row*h, (col+1)*w, (row+1)*h))
                width = round(w*p['height']/h)
                return sprite.resize((width*self.raster_scale, p['height']*self.raster_scale), Image.Resampling.NEAREST), p['ascent']
        raise ValueError(f'Missing glyph {font} U+{code:04X}')

    def render(self, snapshot):
        scale = self.raster_scale
        result = Image.new('RGBA', (240*scale, 108*scale), '#1c242b')
        report = {'state': snapshot['state'], 'glyphs': [], 'netAdvance': snapshot['netAdvance'],
                  'omitted': ['world', 'hotbar', 'vanilla overlays', 'client text backdrop'],
                  'source': 'actual CoreHudLayout component and resource-pack bitmap fonts'}
        for g in snapshot['glyphs']:
            picture, ascent = self.glyph(g['font'], g['code'])
            if picture is None:
                continue
            picture = ImageChops.multiply(picture, Image.new('RGBA', picture.size, f"#{g['color']:06x}"))
            x, y = (120+g['x'])*scale, (56-ascent)*scale
            bounds = picture.getchannel('A').getbbox()
            if bounds is not None:
                ink = (x+bounds[0], y+bounds[1], x+bounds[2], y+bounds[3])
                if ink[0]<0 or ink[1]<0 or ink[2]>result.width or ink[3]>result.height:
                    raise ValueError('Visible HUD ink outside review canvas: ' + str(ink))
                report['glyphs'].append({**g, 'inkBounds': tuple(v/scale for v in ink)})
            result.alpha_composite(picture, (x,y))
        return result, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshots', type=Path, nargs='+')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    renderer = HudRenderer()
    result = Image.new('RGBA', (720, len(args.snapshots)*352+32), '#111820')
    reports = []
    draw = ImageDraw.Draw(result)
    for index, path in enumerate(args.snapshots):
        snapshot = json.loads(path.read_text(encoding='utf-8-sig'))
        picture, report = renderer.render(snapshot)
        draw.text((8,index*352+6), snapshot['state'].upper(), fill='white')
        result.alpha_composite(picture.resize((720,324),Image.Resampling.NEAREST),(0,index*352+24))
        reports.append(report)
    draw.text((8,result.height-23),'ACTUAL COMPONENT + PACK / NOT A GAME CAPTURE',fill='#a9b9c7')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    result.save(args.output)
    args.output.with_suffix('.audit.json').write_text(json.dumps(reports,indent=2)+'\n')
    print(f'Rendered {len(reports)} HUD states: {args.output}')


if __name__ == '__main__':
    main()
