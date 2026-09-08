"""One isolated sword with an animated, blade-attached pixel effect layer.

The existing (unapproved) body is retained to isolate the missing motion. This
does not certify material parity, replace game assets, or create other tiers.
Animation uses Vanilla texture metadata, not a mod or server particle packets.
"""
import hashlib
import json
import shutil
import zipfile
from copy import deepcopy

import numpy as np
from PIL import Image, ImageDraw

from build_pixel_armament_pack import ROOT, SOURCE, geometry, pose, write_json
from build_texture_first_sword import compile_model
from pixel_weapon_display import grip_pixels
from preview_class_armaments import FONT, render_model

OUT = ROOT / '.tools/blade-ember-study'
PACK = OUT / 'pack'
KEY = 'blade_ember_study'
FRAME_COUNT = 24
FRAME_TICKS = 1
WIDTH, HEIGHT = 64, 128
PAD_X, PAD_Y = 16, 4
# Original blade pixel row, edge, birth frame, life, outward/upward travel.
# Art-directed prototype values, NOT measured timing from the reference.
EMITTERS = ((2, 0, 0, 10, 0, 4), (18, -1, 4, 13, 5, 7),
            (29, 1, 10, 12, 4, 8), (43, -1, 15, 13, 5, 6),
            (53, 1, 19, 12, 4, 7))
RAMP = ((252, 87, 72, 255), (221, 47, 71, 255),
        (171, 30, 72, 255), (117, 28, 59, 255))


def source():
    entry = json.loads((SOURCE / 'manifest.json').read_text())['weapons']['greatsword']
    base, gem, textures = geometry('greatsword', entry)
    return entry, pose(base, gem, 'greatsword'), textures


def effect_frames(body):
    """Attached tongue -> detached shard -> smaller dark remnant -> empty.

    Pixel coordinates share the body's grid. There is no blur, smooth alpha
    fade, random cloud, lighting bloom, or invented high-resolution shading.
    """
    frames = []
    for frame in range(FRAME_COUNT):
        art = Image.new('RGBA', (WIDTH, HEIGHT))
        draw = ImageDraw.Draw(art)
        for row, side, born, life, outward, upward in EMITTERS:
            age = (frame - born) % FRAME_COUNT
            if age >= life:
                continue
            edge = np.flatnonzero(body[row, :, 3])
            x = int(edge[0] if side < 0 else edge[-1]) + PAD_X
            y = row + PAD_Y
            if side == 0:  # A short tip fragment separates along the blade.
                y -= 1 + round(upward * age / (life - 1))
                length = 2 if age < life - 3 else 1
                draw.line((x, y, x, max(0, y - length + 1)), fill=RAMP[min(3, age * 4 // life)])
                continue
            if age < 4:
                # Begin ON the edge, then grow an angular red tongue beyond it.
                length = age + 1
                draw.line((x, y, x + side, y - 1), fill=RAMP[1])
                draw.line((x + side, y - 1, x + side, y - length), fill=RAMP[0])
            else:
                u = (age - 4) / max(1, life - 5)
                x += side * (2 + round(outward * u))
                y -= 3 + round(upward * u)
                color = RAMP[min(3, 1 + int(u * 3))]
                length = 3 if u < .4 else 2 if u < .7 else 1
                draw.line((x, y, x, y - length + 1), fill=color)
                if u < .35:
                    draw.point((x - side, y + 1), fill=RAMP[2])
        frames.append(np.array(art))
    return frames


def animated_model(entry, body_model, textures, frames):
    """Trace the union of effect texels, while each frame supplies its alpha.

    Separate front/back surfaces sit just outside the thin blade. Side strips
    give the tiny shards thickness without filling them with voxel cubes.
    """
    scale = entry['height'] / (entry['rows'][-1] - entry['rows'][0])
    pivot, _ = grip_pixels('greatsword', entry, textures)
    union = np.maximum.reduce([f[:, :, 3] for f in frames])
    spec = {'height': HEIGHT * scale, 'top_pixel': 0, 'bottom_pixel': HEIGHT,
            'pivot_pixel_x': pivot + PAD_X, 'alpha_cutoff': 128,
            'texture': f'projects:item/weapons/{KEY}_embers',
            'parts': [{'name': 'blade_embers', 'rows': [0, HEIGHT],
                       'thickness': .20, 'trace_painted_faces': True}]}
    layer, _ = compile_model(spec, union)
    shift_y = (HEIGHT - entry['rows'][-1] - PAD_Y) * scale
    result = deepcopy(body_model)
    result['textures']['embers'] = spec['texture']
    for element in layer['elements']:
        for bound in ('from', 'to'):
            element[bound][1] = round(element[bound][1] - shift_y, 6)
        for face in element['faces'].values():
            face['texture'] = '#embers'
    result['elements'].extend(layer['elements'])
    result['credit'] = 'ProjectS single-sword blade motion study; material not approved'
    return result


def metadata():
    return {'texture': {'blur': False, 'clamp': False},
            'animation': {'width': WIDTH, 'height': HEIGHT,
                          'frametime': FRAME_TICKS, 'interpolate': False,
                          'frames': list(range(FRAME_COUNT))}}


def build():
    entry, original, textures = source()
    frames = effect_frames(textures['body'])
    model = animated_model(entry, original, textures, frames)
    assets = PACK / 'assets/projects'
    texdir = assets / 'textures/item/weapons'
    texdir.mkdir(parents=True, exist_ok=True)
    for part in ('body', 'jewel'):
        shutil.copyfile(SOURCE / f'greatsword-{part}.png', texdir / f'pixel_greatsword_{part}.png')
    atlas = Image.fromarray(np.concatenate(frames, axis=0))
    atlas.save(texdir / f'{KEY}_embers.png')
    write_json(texdir / f'{KEY}_embers.png.mcmeta', metadata())
    write_json(assets / f'models/item/weapons/{KEY}.json', model)
    write_json(assets / f'items/weapons/{KEY}.json', {
        'hand_animation_on_swap': False,
        'model': {'type': 'minecraft:model', 'model': f'projects:item/weapons/{KEY}'}})
    write_json(PACK / 'pack.mcmeta', {'pack': {'description': 'ProjectS blade ember study (unapproved)',
                                            'min_format': [88, 0], 'max_format': [88, 0]}})
    # Render from SAVED model, sprite sheet, and metadata, not synthetic VFX.
    saved = json.loads((assets / f'models/item/weapons/{KEY}.json').read_text())
    sheet = np.array(Image.open(texdir / f'{KEY}_embers.png'))
    timing = json.loads((texdir / f'{KEY}_embers.png.mcmeta').read_text())['animation']
    previews = []
    for i in timing['frames']:
        texture = sheet[i * HEIGHT:(i + 1) * HEIGHT]
        canvas = Image.new('RGB', (720, 430), '#1b1e23')
        for col, yaw in enumerate((0, -35, 90)):
            canvas.paste(render_model(saved, {**textures, 'embers': texture}, yaw=yaw,
                                      size=(240, 395), scale=10.7), (col * 240, 35))
            ImageDraw.Draw(canvas).text((col * 240 + 8, 9),
                ('正面', '斜め', '真横')[col] + ' / 刃の動き試作', font=FONT, fill='#ddd3c5')
        previews.append(canvas)
    previews[0].save(OUT / 'blade-motion.gif', save_all=True, append_images=previews[1:],
                     duration=timing['frametime'] * 50, loop=0, disposal=2)
    contact = Image.new('RGB', (720, 430 * 4))
    for row, i in enumerate((1, 6, 12, 19)):
        contact.paste(previews[i], (0, row * 430))
    contact.save(OUT / 'blade-motion-frames.png')
    with zipfile.ZipFile(OUT / 'projects-blade-motion-study.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(PACK.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(PACK).as_posix())
    report = {'runtime_applied': False, 'quality_approved': False,
              'scope': 'one greatsword only; existing body retained, no tier rollout',
              'frames': FRAME_COUNT, 'frame_ticks': FRAME_TICKS,
              'timing_source': 'prototype, not measured reference timing',
              'elements': len(model['elements']),
              'body_sha256': hashlib.sha256((SOURCE / 'greatsword-body.png').read_bytes()).hexdigest(),
              'preview': 'exported JSON and animated atlas orthographic render; not game footage'}
    write_json(OUT / 'report.json', report)
    print(json.dumps(report))


if __name__ == '__main__':
    build()
