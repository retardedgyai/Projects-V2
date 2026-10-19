"""Player puppet part models (2026-10-19): the player's own skin on item displays, without shaders.

Every skin pixel of a part is its own 1x1 face with its own tint index; the server sends the player's skin colours
as custom_model_data colours in the same order (after toxicity188/DynamicUV's idea, written from scratch here).
Base layer only for now: the overlay layer (hat, jacket, sleeves) would need one conditional model per pixel.

Writes (generated, git-ignored dirs):
  textures/combat_vfx/puppet/white.png                   one white texel all faces sample
  models/combat_vfx/puppet/<part>.json, items/combat_vfx/puppet/<part>.json
and server-minestom/src/main/resources/player-motions/puppet-layout.json (committed): for each puppet part, its
model and the skin pixel [x, y] behind every tint index, so the server samples colours in the model's order.

Part model space: the part's anchor sits at the model centre (8, 8, 8), which the item display puts at its
translation; 1 model px = 1 skin px (the display scales by 0.9375 like the player model). Faces follow Minecraft's
face/UV orientation (south = front, the character's right is -x).
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'server-minestom/src/main/resources/core-ui-pack/assets/projects'
LAYOUT = ROOT / 'server-minestom/src/main/resources/player-motions/puppet-layout.json'
TEXTURE = 'projects:combat_vfx/puppet/white'  # combat_vfx/ is in the item atlas (CoreCombatAtlasTest)

# model: (box from, box to) around the anchor (8,8,8), sides shown
MODELS = {
    'head': ((-4, 0, -4), (4, 8, 4), ('north', 'south', 'east', 'west', 'up', 'down')),
    'torso': ((-4, 0, -2), (4, 12, 2), ('north', 'south', 'east', 'west', 'up', 'down')),
    'limb_upper': ((-2, -6, -2), (2, 0, 2), ('north', 'south', 'east', 'west', 'up')),
    'limb_lower': ((-2, -6, -2), (2, 0, 2), ('north', 'south', 'east', 'west', 'down')),
}
# Skin boxes on a 64x64 skin (wide arms): uv origin, size (w, h, d)
SKIN = {'head': ((0, 0), (8, 8, 8)), 'torso': ((16, 16), (8, 12, 4)), 'arm_r': ((40, 16), (4, 12, 4)),
        'arm_l': ((32, 48), (4, 12, 4)), 'leg_r': ((0, 16), (4, 12, 4)), 'leg_l': ((16, 48), (4, 12, 4))}
# Puppet part: (model, skin box, rows of the box's sides shown, anchor offset from the bone pivot in px)
PARTS = {
    'head': ('head', 'head', (0, 8), (0, 0, 0)),
    'torso': ('torso', 'torso', (0, 12), (0, 0, 0)),
    'arm_r_up': ('limb_upper', 'arm_r', (0, 6), (-1, 2, 0)), 'arm_r_low': ('limb_lower', 'arm_r', (6, 12), (0, 0, 0)),
    'arm_l_up': ('limb_upper', 'arm_l', (0, 6), (1, 2, 0)), 'arm_l_low': ('limb_lower', 'arm_l', (6, 12), (0, 0, 0)),
    'leg_r_up': ('limb_upper', 'leg_r', (0, 6), (0, 0, 0)), 'leg_r_low': ('limb_lower', 'leg_r', (6, 12), (0, 0, 0)),
    'leg_l_up': ('limb_upper', 'leg_l', (0, 6), (0, 0, 0)), 'leg_l_low': ('limb_lower', 'leg_l', (6, 12), (0, 0, 0)),
}


def pixel_faces(lo, hi, sides):
    """Yield (side, column, row, from, to) for every 1x1 pixel quad, columns/rows as the skin texture reads them."""
    lx, ly, lz = lo; hx, hy, hz = hi
    for side in ('north', 'south', 'east', 'west', 'up', 'down'):
        if side not in sides: continue
        if side in ('south', 'north'):
            w, h = hx - lx, hy - ly
            for j in range(h):
                for i in range(w):
                    x = lx + i if side == 'south' else hx - i - 1
                    z = hz if side == 'south' else lz
                    yield side, i, j, (x, hy - j - 1, z), (x + 1, hy - j, z)
        elif side in ('east', 'west'):
            w, h = hz - lz, hy - ly
            for j in range(h):
                for i in range(w):
                    z = hz - i - 1 if side == 'east' else lz + i
                    x = hx if side == 'east' else lx
                    yield side, i, j, (x, hy - j - 1, z), (x, hy - j, z + 1)
        else:
            w, h = hx - lx, hz - lz
            for j in range(h):
                for i in range(w):
                    x = lx + i
                    z = lz + j if side == 'up' else hz - j - 1
                    y = hy if side == 'up' else ly
                    yield side, i, j, (x, y, z), (x + 1, y, z + 1)


def skin_pixel(box, rows, side, i, j):
    (u, v), (w, h, d) = SKIN[box]
    r0 = rows[0]
    if side == 'west': return u + i, v + d + r0 + j
    if side == 'south': return u + d + i, v + d + r0 + j
    if side == 'east': return u + d + w + i, v + d + r0 + j
    if side == 'north': return u + 2 * d + w + i, v + d + r0 + j
    if side == 'up': return u + d + i, v + j
    return u + d + w + i, v + j  # down


def build(assets=ASSETS, layout_path=LAYOUT):
    tex = assets / 'textures/combat_vfx/puppet/white.png'
    tex.parent.mkdir(parents=True, exist_ok=True)
    Image.new('RGBA', (1, 1), (255, 255, 255, 255)).save(tex)
    counts = {}
    for name, (lo, hi, sides) in MODELS.items():
        elements = []
        for k, (side, i, j, a, b) in enumerate(pixel_faces(lo, hi, sides)):
            elements.append({'from': [8 + a[0], 8 + a[1], 8 + a[2]], 'to': [8 + b[0], 8 + b[1], 8 + b[2]], 'shade': True,
                             'faces': {side: {'uv': [0, 0, 16, 16], 'texture': '#0', 'tintindex': k}}})
        counts[name] = len(elements)
        model = {'textures': {'0': TEXTURE, 'particle': TEXTURE}, 'elements': elements}
        item = {'model': {'type': 'minecraft:model', 'model': f'projects:combat_vfx/puppet/{name}',
                          'tints': [{'type': 'minecraft:custom_model_data', 'index': k, 'default': 0x9a9a9a}
                                    for k in range(len(elements))]}}
        for sub, value in (('models', model), ('items', item)):
            path = assets / f'{sub}/combat_vfx/puppet/{name}.json'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    parts = {}
    for part, (model, box, rows, anchor) in PARTS.items():
        lo, hi, sides = MODELS[model]
        parts[part] = {'model': f'projects:combat_vfx/puppet/{model}', 'anchor': list(anchor),
                       'pixels': [list(skin_pixel(box, rows, side, i, j)) for side, i, j, *_ in pixel_faces(lo, hi, sides)]}
        assert len(parts[part]['pixels']) == counts[model]
    layout_path.parent.mkdir(parents=True, exist_ok=True)
    layout_path.write_text(json.dumps({'comment': 'Generated by scripts/build_player_puppet.py: skin pixel per tint index.',
                                       'parts': parts}, separators=(',', ':')) + '\n', encoding='utf-8')
    return counts


if __name__ == '__main__':
    print('puppet parts:', build())
