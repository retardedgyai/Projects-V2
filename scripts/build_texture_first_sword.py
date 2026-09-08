"""One texture-led native sword prototype, not a replacement for production art.

The source RGBA is read and copied byte-for-byte, never resized/repainted. Broad
front/back faces preserve the painting; only alpha-boundary side spans provide
depth. No filled-pixel cubes, tile atlas, or invented surface materials.
"""
import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from preview_class_armaments import FONT, render_model

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets/class-armaments/texture-first'
OUT = ROOT / '.tools/texture-first-native'


def runs(values):
    """Half-open contiguous True spans (disconnected silhouettes stay separate)."""
    padded = np.pad(np.asarray(values, dtype=np.int8), (1, 1))
    starts = np.flatnonzero(np.diff(padded) == 1)
    ends = np.flatnonzero(np.diff(padded) == -1)
    return list(zip(starts.tolist(), ends.tolist()))


def compile_model(spec, alpha):
    h, w = alpha.shape
    rows = [part['rows'] for part in spec['parts']]
    if (not rows or rows[0][0] != spec['top_pixel'] or rows[-1][1] != spec['bottom_pixel']
            or any(a[1] != b[0] for a, b in zip(rows, rows[1:]))):
        raise ValueError('Parts must partition the source vertically without gaps or overlaps')
    if not 0 <= spec['top_pixel'] < spec['bottom_pixel'] <= h:
        raise ValueError('Invalid source extent')
    if any(not 0 < p['thickness'] <= 1 for p in spec['parts']):
        raise ValueError('This thin sword study must not become a thick block model')
    scale = spec['height'] / (spec['bottom_pixel'] - spec['top_pixel'])
    elements = []
    report = []

    def x(px): return round(8 + (px - spec['pivot_pixel_x']) * scale, 6)
    def y(py): return round((spec['bottom_pixel'] - py) * scale, 6)
    def uv(rect): return [round(rect[i] / (w if i % 2 == 0 else h) * 16, 7) for i in range(4)]
    def face(rect): return {'uv': uv(rect), 'texture': '#art'}

    for part in spec['parts']:
        start, end = part['rows']
        if not 0 <= start < end <= h: raise ValueError('Part rows outside source')
        mask = alpha[start:end] >= spec['alpha_cutoff']
        if not mask.any(): raise ValueError('Empty part')
        z0, z1 = 8 - part['thickness'] / 2, 8 + part['thickness'] / 2
        occupied_x = np.flatnonzero(mask.any(axis=0))
        left, right = int(occupied_x[0]), int(occupied_x[-1]) + 1
        # Same physical image on both sides. Vanilla's NORTH face has reversed
        # local U orientation (26.2 ItemModelGenerator.NORTH_FACE_UVS).
        elements.append({'name': part['name'] + ':painted faces',
            'from': [x(left), y(end), z0], 'to': [x(right), y(start), z1],
            'shade': False, 'faces': {
                'north': face([right, start, left, end]),
                'south': face([left, start, right, end])}})
        before = len(elements)
        # Side quads sample the neighboring opaque texel center: they cannot
        # acquire unrelated box material or smear a whole texture onto an edge.
        padded = np.pad(mask, ((1, 1), (1, 1)))
        neighbors = {'west': padded[1:-1, :-2], 'east': padded[1:-1, 2:],
                     'up': padded[:-2, 1:-1], 'down': padded[2:, 1:-1]}
        for direction, neighbor in neighbors.items():
            boundary = mask & ~neighbor
            horizontal = direction in ('up', 'down')
            lines = boundary if horizontal else boundary.T
            for fixed, line in enumerate(lines):
                for a, b in runs(line):
                    if horizontal:
                        py = start + fixed + (direction == 'down')
                        lower, upper = [x(a), y(py), z0], [x(b), y(py), z1]
                        sampled = [a, start + fixed + .5, b, start + fixed + .5]
                    else:
                        px = fixed + (direction == 'east')
                        lower, upper = [x(px), y(start + b), z0], [x(px), y(start + a), z1]
                        sampled = [fixed + .5, start + a, fixed + .5, start + b]
                    elements.append({'name': part['name'] + ':' + direction,
                        'from': lower, 'to': upper, 'shade': False,
                        'faces': {direction: face(sampled)}})
        report.append({'part': part['name'], 'thickness': part['thickness'],
                       'side_quads': len(elements) - before})

    model = {'credit': 'ProjectS texture-first geometry study; art not production-approved',
        'ambientocclusion': False, 'gui_light': 'front',
        'textures': {'art': spec['texture'], 'particle': spec['texture']},
        'elements': elements,
        'display': {
            'gui': {'rotation': [0, 0, -30], 'translation': [0, -3, 0], 'scale': [.42] * 3},
            'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, -1.5], 'scale': [.72] * 3},
            'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 2, 1], 'scale': [.75] * 3},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.3] * 3}}}
    return model, report


def build():
    spec = json.loads((SOURCE / 'sword-mesh-v01.json').read_text(encoding='utf-8'))
    image = Image.open(SOURCE / spec['source']).convert('RGBA')
    pixels = np.asarray(image)
    model, parts = compile_model(spec, pixels[:, :, 3])
    # Export to a separate, explicit prototype asset tree. Do not add it to
    # the server pack index or silently select it for a player's real weapon.
    assets = OUT / 'assets/projects'
    texture_path = assets / 'textures/item/weapons/texture_first_sword_study.png'
    model_path = assets / 'models/item/weapons/texture_first_sword_study.json'
    item_path = assets / 'items/weapons/texture_first_sword_study.json'
    for path in (texture_path, model_path, item_path): path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / spec['source'], texture_path)
    model_path.write_text(json.dumps(model, separators=(',', ':')) + '\n', encoding='utf-8')
    item_path.write_text(json.dumps({'model': {'type': 'minecraft:model',
        'model': 'projects:item/weapons/texture_first_sword_study'}}) + '\n', encoding='utf-8')
    texture_path.with_suffix('.png.mcmeta').write_text(
        '{"texture":{"blur":false,"clamp":false}}\n', encoding='utf-8')
    report = {'status': spec['status'], 'source_size': image.size, 'parts': parts,
        'elements': len(model['elements']), 'model_bytes': model_path.stat().st_size,
        'production_selected': False,
        'remaining': ['source art alpha fringe / inconsistent pixel grid',
                      'too many contour edges for a final low-resolution asset',
                      'no separate jewel texture or depth',
                      'no runtime or animation validation']}
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    # QA consumes the files that were actually exported.
    model = json.loads(model_path.read_text(encoding='utf-8'))
    pixels = np.asarray(Image.open(texture_path).convert('RGBA'))
    sheet = Image.new('RGB', (320 * 4, 460), '#1b1e23')
    for column, (yaw, label) in enumerate(((0, '正面'), (-35, '斜め'), (90, '側面'), (180, '背面'))):
        rendered = render_model(model, {'art': pixels}, yaw=yaw, size=(320, 420), scale=11)
        sheet.paste(rendered, (column * 320, 40))
        ImageDraw.Draw(sheet).text((column * 320 + 12, 15), label, font=FONT, fill='#ece3cd')
    ImageDraw.Draw(sheet).text((12, 440), '実JSON/実PNGの形状検証・未採用原稿・Minecraft画面ではありません', font=FONT, fill='#c2b9a9')
    sheet.save(OUT / 'views.png')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__': build()
