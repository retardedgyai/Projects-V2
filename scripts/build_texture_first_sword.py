"""One texture-led native sword prototype, not a replacement for production art.

The source RGBA is read and copied byte-for-byte, never resized/repainted. Broad
front/back faces preserve the painting; only alpha-boundary side spans provide
depth. No filled-pixel cubes, tile atlas, or invented surface materials.
"""
import json
from copy import deepcopy
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


def rectangles(mask):
    """Merge equal horizontal spans vertically; front faces never fill holes."""
    active = {}
    result = []
    for row in range(mask.shape[0] + 1):
        spans = set(runs(mask[row])) if row < mask.shape[0] else set()
        for span in list(active):
            if span not in spans:
                result.append((span[0], active.pop(span), span[1], row))
        for span in spans: active.setdefault(span, row)
    return sorted(result)


def load_art(path):
    with Image.open(path) as image:
        if image.mode != 'RGBA':
            raise ValueError('Source must contain real RGBA transparency, not a painted checkerboard')
        pixels = np.asarray(image).copy()
    alpha = pixels[:, :, 3]
    if not np.any(alpha == 0) or not np.any(alpha >= 128):
        raise ValueError('Source must have both transparent background and visible artwork')
    return pixels


def jewel_geometry_mask(spec, pixels):
    """Select the painted jewel for geometry only. No raster colors are edited.

    This source-specific region and seed exclude the blade's red stripe. A
    connected painted-color region avoids adding a cuboid around the jewel.
    """
    jewel = spec['jewel']
    x0, y0, x1, y1 = jewel['pixel_bounds']
    r, g, b, a = (pixels[:, :, i].astype(float) for i in range(4))
    candidate = ((r > jewel['red_minimum']) & (r > g * jewel['red_over_green'])
        & (r > b * jewel['red_over_blue']) & (a >= spec['alpha_cutoff']))
    selected = np.zeros(candidate.shape, dtype=bool)
    sx, sy = jewel['seed_pixel']
    if not (x0 <= sx < x1 and y0 <= sy < y1 and candidate[sy, sx]):
        raise ValueError('Jewel seed is outside its painted region')
    pending = [(sx, sy)]
    selected[sy, sx] = True
    while pending:
        x, y = pending.pop()
        for nx, ny in ((x-1,y), (x+1,y), (x,y-1), (x,y+1)):
            if x0 <= nx < x1 and y0 <= ny < y1 and candidate[ny,nx] and not selected[ny,nx]:
                selected[ny,nx] = True
                pending.append((nx,ny))
    return selected


def compile_model(spec, alpha, *, boundary_frames=None):
    h, w = alpha.shape
    if boundary_frames is not None:
        if not len(boundary_frames) or any(frame.shape != alpha.shape for frame in boundary_frames):
            raise ValueError('Animation boundary frames must match the silhouette dimensions')
        if not np.array_equal(np.maximum.reduce(boundary_frames) >= spec['alpha_cutoff'], alpha >= spec['alpha_cutoff']):
            raise ValueError('Animation silhouette must be the union of its frames')
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
        painted_rects = ([(a, start+b, c, start+d) for a,b,c,d in rectangles(mask)]
            if part.get('trace_painted_faces') else [(left, start, right, end)])
        for a,b,c,d in painted_rects:
            elements.append({'name': part['name'] + ':painted faces',
                'from': [x(a), y(d), z0], 'to': [x(c), y(b), z1],
                'shade': False, 'faces': {
                    'north': face([c, b, a, d]),
                    'south': face([a, b, c, d])}})
        before = len(elements)
        # Side quads sample the neighboring opaque texel center: they cannot
        # acquire unrelated box material or smear a whole texture onto an edge.
        padded = np.pad(mask, ((1, 1), (1, 1)))
        neighbors = {'west': padded[1:-1, :-2], 'east': padded[1:-1, 2:],
                     'up': padded[:-2, 1:-1], 'down': padded[2:, 1:-1]}
        for direction, neighbor in neighbors.items():
            boundary = mask & ~neighbor
            if boundary_frames is not None:
                # Union of EACH FRAME'S boundaries, not boundary of the union.
                # A moving shard has edges inside its combined motion silhouette.
                # Omitting them makes it disappear edge-on while travelling.
                boundary = np.zeros_like(mask)
                for frame in boundary_frames:
                    current = frame[start:end] >= spec['alpha_cutoff']
                    padded_frame = np.pad(current, ((1, 1), (1, 1)))
                    adjacent = {'west': padded_frame[1:-1, :-2], 'east': padded_frame[1:-1, 2:],
                                'up': padded_frame[:-2, 1:-1], 'down': padded_frame[2:, 1:-1]}[direction]
                    boundary |= current & ~adjacent
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


def jewel_model(spec, pixels):
    jewel_spec = deepcopy(spec)
    jewel_spec['parts'] = [{'name': 'jewel', 'rows': [spec['top_pixel'], spec['bottom_pixel']],
        'thickness': spec['jewel']['thickness'], 'trace_painted_faces': True}]
    # This array is a geometry selection mask, not a replacement image. Every
    # generated face still samples the byte-identical original RGBA texture.
    mask = jewel_geometry_mask(spec, pixels)
    return compile_model(jewel_spec, np.where(mask, 255, 0).astype(np.uint8))[0]


def posed_model(base, jewel, spec, stage='rest', frame=0):
    if stage not in ('rest','prepare','release') or not 0 <= frame < 6:
        raise ValueError('Unknown jewel study pose')
    motion = spec['jewel']
    amount = frame / 5
    travel = (motion['prepare_travel'] * amount if stage == 'prepare' else
              motion['release_travel'] * (1-amount) if stage == 'release' else 0)
    model = deepcopy(base)
    for element in deepcopy(jewel['elements']):
        for key in ('from','to'):
            element[key][2] = round(element[key][2] + motion['rest_z_offset'] - travel, 6)
        model['elements'].append(element)
    return model


def item_definition():
    """Native hand-only pose selection; matches the existing server pose channel.

    This study has no idle loop yet. Floats 0..11 select rest, 12..17 prepare,
    18..23 release. Unknown higher values must not latch the final action pose.
    No server registration or existing equipment is changed by this definition.
    """
    key = 'projects:item/weapons/texture_first_sword_study'
    rest = {'type': 'minecraft:model', 'model': key}
    entries = [{'threshold': 0, 'model': rest}]
    for offset, stage in ((12, 'prepare'), (18, 'release')):
        for frame in range(6):
            entries.append({'threshold': offset + frame,
                'model': {'type': 'minecraft:model', 'model': f'{key}_{stage}{frame:02d}'}})
    entries.append({'threshold': 24, 'model': rest})
    return {'hand_animation_on_swap': False, 'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': ['firstperson_righthand', 'firstperson_lefthand',
                           'thirdperson_righthand', 'thirdperson_lefthand'],
                   'model': {'type': 'minecraft:range_dispatch',
                       'property': 'minecraft:custom_model_data', 'index': 0,
                       'fallback': rest, 'entries': entries}}],
        'fallback': rest}}


def build():
    spec = json.loads((SOURCE / 'sword-mesh-v01.json').read_text(encoding='utf-8'))
    pixels = load_art(SOURCE / spec['source'])
    base, parts = compile_model(spec, pixels[:, :, 3])
    jewel = jewel_model(spec, pixels)
    model = posed_model(base, jewel, spec)
    # Export to a separate, explicit prototype asset tree. Do not add it to
    # the server pack index or silently select it for a player's real weapon.
    assets = OUT / 'assets/projects'
    texture_path = assets / 'textures/item/weapons/texture_first_sword_study.png'
    model_path = assets / 'models/item/weapons/texture_first_sword_study.json'
    item_path = assets / 'items/weapons/texture_first_sword_study.json'
    for path in (texture_path, model_path, item_path): path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / spec['source'], texture_path)
    model_path.write_text(json.dumps(model, separators=(',', ':')) + '\n', encoding='utf-8')
    item_path.write_text(json.dumps(item_definition(), separators=(',', ':')) + '\n', encoding='utf-8')
    texture_path.with_suffix('.png.mcmeta').write_text(
        '{"texture":{"blur":false,"clamp":false}}\n', encoding='utf-8')
    for stage in ('prepare','release'):
        for frame in range(6):
            pose_path = model_path.with_name(f'{model_path.stem}_{stage}{frame:02d}.json')
            pose_path.write_text(json.dumps(posed_model(base, jewel, spec, stage, frame),
                separators=(',', ':')) + '\n', encoding='utf-8')
    report = {'status': spec['status'], 'source_size': [pixels.shape[1], pixels.shape[0]], 'parts': parts,
        'elements': len(model['elements']), 'model_bytes': model_path.stat().st_size,
        'jewel_elements': len(jewel['elements']), 'action_poses': 12,
        'native_item_pose_selection': 'hand contexts, custom_model_data float0: 12..23',
        'production_selected': False,
        'remaining': ['source art alpha fringe / inconsistent pixel grid',
                      'too many contour edges for a final low-resolution asset',
                      'jewel back and underlying socket reuse front painting',
                      'no runtime or action timing validation']}
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
    frames = []
    for stage in ('prepare','release'):
        for frame in range(6):
            pose_path = model_path.with_name(f'{model_path.stem}_{stage}{frame:02d}.json')
            pose = json.loads(pose_path.read_text(encoding='utf-8'))
            rendered = render_model(pose, {'art': pixels}, yaw=-55, size=(360,420), scale=11)
            ImageDraw.Draw(rendered).text((10, 8), f'{stage} {frame} / 形状検証', font=FONT, fill='#ece3cd')
            frames.append(rendered)
    frames[0].save(OUT/'jewel-action.gif', save_all=True, append_images=frames[1:], duration=120, loop=0)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__': build()
