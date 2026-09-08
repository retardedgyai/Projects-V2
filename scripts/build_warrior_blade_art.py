"""Warrior-only, coarse native pixel surfaces. No bitmap resampling or borrowed art.

32 x 8 colour clusters are exported as legal, double-sided Minecraft model faces.
Shared flow art, the approved weapon and other classes are deliberately untouched.
"""
import json
from pathlib import Path
import numpy as np
from build_greatsword_sweep import geometry, ink_uvs, polygon

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'server-minestom/src/main/resources/core-ui-pack'
COLOURS = {
    'warsteel': (0x494653, 0xB9BCC4, 0xFFF7EE, 0xB73649),
    'warred': (0x622A3A, 0xDE6974, 0xFFF1EC, 0xBA3048),
}


def contour(layer):
    grid = np.zeros((64, 64), dtype=np.uint8)
    # Long torn tongues with intentional notches, not smooth parallel stripes.
    inner = [(2, 0), (5, 2), (3, 4), (6, 6), (4, 8), (5, 11), (2, 14), (3, 16)]
    polygon(grid, inner + [(16, 16), (16, 0)], 1)
    polygon(grid, [(7, 0), (9, 3), (7, 5), (10, 7), (8, 10), (9, 13), (7, 16), (16, 16), (16, 0)], 2)
    polygon(grid, [(13, 0), (14, 3), (13, 5), (14, 8), (13, 11), (14, 14), (13, 16), (16, 16), (16, 0)], 3)
    # A broken red inner vein; the cutting edge stays white, including finishers.
    polygon(grid, [(5, 3), (7, 4), (6, 8), (8, 10), (7, 12), (5, 8)], 4)
    for row in range(64):
        # A convex edge within each joint keeps the broad wake from reading as
        # eight straight rulers. The stepped contour is deliberate, never blurred.
        edge = 56 if row < 8 or row >= 56 else 60 if row < 20 or row >= 44 else 64
        grid[row, edge:] = 0
    if layer in ('tip', 'tail'):
        for row in range(64):
            t = (row + .5) / 64
            if layer == 'tail': t = 1 - t
            grid[row, :int(64 * t * t)] = 0
    # Coarse *geometry* coverage, not an antialiased texture or blur.
    # Longitudinal joints are shorter in world space than their radial width.
    # Fewer rows keep actual world texels comparable instead of 256 tiny teeth per cut.
    return np.repeat(np.repeat(grid[::8, ::2], 8, axis=0), 2, axis=1)


def build(assets, write):
    inks = ink_uvs(assets)
    for layer in ('cut', 'tip', 'tail'):
        elements = geometry(contour(layer), inks, curved=False, pigment=True)
        merged, last = [], {}
        for e in elements:
            key = (e['from'][0], e['to'][0], e['faces']['up']['tintindex'])
            prev = last.get(key)
            if prev is not None and prev['to'][2] == e['from'][2]:
                prev['to'][2] = e['to'][2]
            else:
                merged.append(e)
                last[key] = e
        key = f'combat_vfx/warrior_blade/{layer}'
        write(assets / f'models/{key}.json', {
            'ambientocclusion': False,
            'textures': {'0': 'projects:combat_vfx/ribbon/slash_5'}, 'elements': merged})
        for family, colours in COLOURS.items():
            write(assets / f'items/combat_vfx/warrior_blade/{family}_{layer}.json', {
                'model': {'type': 'minecraft:model', 'model': f'projects:{key}',
                          'tints': [{'type': 'minecraft:constant', 'value': c} for c in colours]}})
    # Keep existing hit-local shard geometry, remove the golden explosion tint.
    for frame in range(9):
        write(assets / f'items/combat_vfx/warrior_blade/impact_{frame}.json', {
            'model': {'type': 'minecraft:model', 'model': f'projects:combat_vfx/greatsword/impact_{frame}',
                      'tints': [{'type': 'minecraft:constant', 'value': c} for c in COLOURS['warred'][:3]]}})
    for shape in ('war_voice_band', 'war_rally_streamer'):
        for stage in range(8):
            suffix = f'_fade{stage}' if stage else ''
            source = assets / f'models/combat_vfx/{shape}_gold{suffix}.json'
            model = json.loads(source.read_text(encoding='utf-8'))
            model['textures'] = {'0': 'minecraft:block/white_concrete',
                                 '1': 'minecraft:block/red_concrete', '2': 'minecraft:block/black_concrete'}
            key = f'combat_vfx/{shape}_warred{suffix}'
            write(assets / f'models/{key}.json', model)
            write(assets / f'items/{key}.json', {'model': {'type': 'minecraft:model', 'model': f'projects:{key}'}})


if __name__ == '__main__':
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    build(PACK / 'assets/projects', write)
    (PACK / 'index.txt').write_text('\n'.join(sorted(str(p.relative_to(PACK)).replace('\\', '/')
        for p in PACK.rglob('*') if p.is_file() and p.name != 'index.txt')) + '\n', encoding='utf-8')
