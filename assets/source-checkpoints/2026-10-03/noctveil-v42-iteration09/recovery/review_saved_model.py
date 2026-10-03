"""Read the frozen editable source and use its existing preview helpers.

This recovery entry point does not re-run the historical authoring chain or QA.
All model inputs and outputs are relative to this snapshot; no GUI is launched.
"""
import argparse
import base64
import copy
import hashlib
import io
import json
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import numpy as np
from PIL import Image
import PIL
import mesh_preview
import preview_bbmodel


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true', help='Render three 320x240 poses with the existing renderer.')
    parser.add_argument('--editable-copy', action='store_true', help='Save an unchanged editable copy below recovery-output/.')
    args = parser.parse_args()
    root = HERE.parent
    delivery = root / 'outputs/v42_delivery_checkpoint'
    source = delivery / 'ProjectS_Noctveil_v42_review.bbmodel'
    original = source.read_bytes()
    model = json.loads(original)
    textures = []
    for index, (texture, filename) in enumerate(zip(model['textures'], ['noctveil_mantle.png', 'noctveil_anatomy.png'], strict=True)):
        if not texture['source'].startswith('data:image/png;base64,'):
            raise ValueError('Embedded PNG source required: no file-path fallback is allowed.')
        pixels = np.array(Image.open(io.BytesIO(base64.b64decode(texture['source'].split(',', 1)[1], validate=True))).convert('RGBA'))
        separate = np.array(Image.open(delivery / 'textures' / filename).convert('RGBA'))
        if not np.array_equal(pixels, separate):
            raise ValueError(f'Texture {index} differs from the delivered pixel source.')
        textures.append(pixels)
    rd = copy.deepcopy(model)
    offsets = np.cumsum([0] + [image.shape[0] for image in textures]).tolist()
    atlas = np.concatenate(textures, axis=0)
    for element in rd['elements']:
        for face in element['faces'].values():
            index = face.get('texture')
            if isinstance(index, int):
                if not 0 <= index < len(textures):
                    raise ValueError('Unknown texture index.')
                for uv in face['uv'].values():
                    uv[1] += offsets[index]
    checks = []
    for clip in model['animations']:
        for time in sorted(set([0.0, float(clip['length']) / 2, float(clip['length'])])):
            world = preview_bbmodel.transforms(model['outliner'][0], clip, time, np.eye(4), {})
            if set(world) != {element['uuid'] for element in model['elements']}:
                raise ValueError('The hierarchy does not cover every element.')
            triangles = list(mesh_preview.geometry(model, clip, time))
            vertices = np.concatenate([triangle[0] for triangle in triangles])
            if not np.isfinite(vertices).all():
                raise ValueError('Nonfinite world vertex.')
            checks.append({'animation': clip['name'], 'time': time, 'triangles': len(triangles), 'minimum': vertices.min(0).tolist(), 'maximum': vertices.max(0).tolist()})
    out = root / 'recovery-output'
    out.mkdir(exist_ok=True)
    rendered = []
    if args.preview:
        clip = model['animations'][0]
        times = [0.0, float(clip['length']) / 2, float(clip['length'])]
        points = np.concatenate([mesh_preview.project(np.concatenate([triangle[0] for triangle in mesh_preview.geometry(rd, clip, time)]), 'front') for time in times])
        lo, hi = points.min(0), points.max(0)
        lo[:2] -= 3
        hi[:2] += 3
        for index, time in enumerate(times):
            filename = f'pose-{index}.png'
            mesh_preview.render(rd, atlas, clip, time, 'front', (320, 240), [lo, hi]).save(out / filename)
            rendered.append({'file': filename, 'time': time, 'size': [320, 240], 'sha256': hashlib.sha256((out / filename).read_bytes()).hexdigest()})
    if args.editable_copy:
        (out / 'editable-copy.bbmodel').write_bytes(original)
    if source.read_bytes() != original:
        raise ValueError('Protected delivered model changed.')
    report = {'scope': 'Source loading, texture pixel identity, finite sampled poses, and existing lightweight renderer only; no collision/art/native/game pass.', 'model_sha256': hashlib.sha256(original).hexdigest(), 'embedded_texture_count': len(textures), 'separate_texture_pixels_match': True, 'element_count': len(model['elements']), 'animation_count': len(model['animations']), 'pose_checks': checks, 'preview': rendered, 'editable_copy_byte_exact': (out / 'editable-copy.bbmodel').read_bytes() == original if args.editable_copy else None, 'protected_source_unchanged': True, 'dependencies': {'python': sys.version.split()[0], 'numpy': np.__version__, 'Pillow': PIL.__version__}, 'native_or_game_runtime_launched': False}
    (out / 'recovery-check.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'poses': len(checks), 'preview_frames': len(rendered), 'model_sha256': report['model_sha256'], 'output': 'recovery-output/recovery-check.json'}))


if __name__ == '__main__':
    main()
