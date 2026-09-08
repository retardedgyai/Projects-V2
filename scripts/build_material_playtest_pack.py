"""One-sword opt-in playable snapshot; no running process or installed RP writes.

Preserves every installed resource except the existing greatsword T1 item graph.
Regenerates the material candidate and all existing cosmetic pose-channel states.
"""
import io
import json
import zipfile
import numpy as np
from PIL import Image

from build_pixel_armament_pack import geometry, pose, definition
from build_blade_ember_study import animated_model, effect_frames, metadata
from process_sword_material_redraw import convert, guard_depth
from build_weapon_playtest_pack import ROOT, SERVER_JAR, installed_pack, digest

OUT = ROOT / '.tools/material-playtest-resources'
PACK = OUT / 'core-ui-pack'
KEY = 'material_greatsword'
MODEL_NAME = 'pixel_' + KEY
TARGET = 'assets/projects/items/weapons/greatsword_t1.json'


def encoded(value):
    return (json.dumps(value, separators=(',', ':')) + '\n').encode('utf-8')


def png(array):
    stream = io.BytesIO()
    Image.fromarray(array).save(stream, format='PNG')
    return stream.getvalue()


def candidate_resources():
    _, textures, entry = convert()
    base, gem, _ = geometry('greatsword', entry, textures)
    base = guard_depth(base, textures, entry)
    base['textures'] = {part: f'projects:item/weapons/{MODEL_NAME}_{part}' for part in textures}
    base['textures']['particle'] = base['textures']['body']
    frames = effect_frames(textures['body'], entry['ember_emitters'])
    files = {}
    texture_root = f'assets/projects/textures/item/weapons/{MODEL_NAME}'
    for part, pixels in textures.items():
        files[f'{texture_root}_{part}.png'] = png(pixels)
        files[f'{texture_root}_{part}.png.mcmeta'] = encoded({'texture': {'blur': False, 'clamp': False}})
    files[f'{texture_root}_embers.png'] = png(np.concatenate(frames, axis=0))
    files[f'{texture_root}_embers.png.mcmeta'] = encoded(metadata())
    for stage, count in (('rest', 1), ('idle', 12), ('prepare', 6), ('release', 6)):
        for frame in range(count):
            suffix = '' if stage == 'rest' else f'_{stage}{frame:02d}'
            model = animated_model(entry, pose(base, gem, 'greatsword', stage, frame), textures, frames)
            model['textures']['embers'] = f'projects:item/weapons/{MODEL_NAME}_embers'
            files[f'assets/projects/models/item/weapons/{MODEL_NAME}{suffix}.json'] = encoded(model)
    files[f'assets/projects/items/weapons/{MODEL_NAME}.json'] = encoded(definition(KEY))
    return files


def assemble(base, candidate):
    expected = {f'assets/projects/items/weapons/{MODEL_NAME}.json'}
    expected |= {f'assets/projects/textures/item/weapons/{MODEL_NAME}_{part}.png{suffix}'
                 for part in ('body', 'jewel', 'embers') for suffix in ('', '.mcmeta')}
    expected |= {f'assets/projects/models/item/weapons/{MODEL_NAME}{suffix}.json'
                 for suffix in [''] + [f'_{stage}{i:02d}'
                     for stage, count in (('idle', 12), ('prepare', 6), ('release', 6)) for i in range(count)]}
    if set(candidate) != expected:
        raise ValueError('Incomplete or out-of-scope material review resources')
    if expected & base.keys():
        raise ValueError('Candidate resource collides with installed assets')
    if TARGET not in base:
        raise ValueError('Installed server lacks greatsword T1 equipment model')
    files = {**base, **candidate}
    files[TARGET] = candidate[f'assets/projects/items/weapons/{MODEL_NAME}.json']
    return files


def build():
    if not SERVER_JAR.is_file():
        raise ValueError('InstallDist is required before creating a material review snapshot')
    before = digest(SERVER_JAR.read_bytes())
    base = installed_pack()
    candidate = candidate_resources()
    files = assemble(base, candidate)
    if digest(SERVER_JAR.read_bytes()) != before:
        raise ValueError('Installed server changed while building snapshot; retry after build finishes')
    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        target = PACK / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    (PACK / 'index.txt').write_text('\n'.join(sorted(files)) + '\n', encoding='utf-8')
    with zipfile.ZipFile(OUT / 'projects-material-playtest.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    report = {'status': 'opt-in single-sword material review, not art approval',
              'runtime_applied': False, 'quality_approved': False,
              'server_jar_sha256': before, 'replaced_item_definitions': [TARGET],
              'added_files': sorted(candidate), 'indexed_files': len(files),
              'unchanged_installed_files': len(base) - 1,
              'files_sha256': {name: digest(data) for name, data in sorted(files.items())}}
    (OUT / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'Material review: {len(files)} files, only greatsword T1 replaced; no runtime changes.')


if __name__ == '__main__':
    build()
