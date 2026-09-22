"""Port the painted geometry behind the creator's reference into WSEE.

Both PNGs are unchanged ProjectS originals from 59d6d2e5. Preserve contour,
facet UVs and element Euler rotation. Three different sectors become a forward
chain. No synthetic palette, billboard frame swaps or raster repaint.
"""
import base64
import json
from pathlib import Path
import uuid
import reference_rime_geometry as ref
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'model-lab/art/ice-fang'
OUT = ROOT / 'model-lab/models/ice_fang.bbmodel'
NAMES = ('ice_fang', 'ice_fang_b', 'ice_fang_c')
ref.SOURCE = ART / 'rime-faces-v02.png'
ref.BRANCH_SOURCE = ART / 'rime-branches-v01.png'


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'projects:painted-ice/' + name))


def smooth(value):
    t = max(0., min(1., value))
    return t*t*(3-2*t)


def sections(variant):
    letter = 'abc'[variant]
    footing = ref.painted_branch(variant, (8,8,6), (8,9.4,13), 6.4, 0)
    return [('root', footing, (0,0,-1.5), (2.25,1.5,2.25), 0, 40),
            ('inner', ref.mesh('rime_inner_'+letter), (0,0,-1.8), (2.7,2.3,2.7), 0, 34),
            ('outer', ref.mesh('rime_outer_'+letter), (0,0,0), (2.7,3.1,2.7), 2, 28)]


def build(variant=0):
    name = NAMES[variant]
    elements, bones, animators = [], [], {}
    for part, mesh, offset, maximum, delay, life in sections(variant):
        children = []
        for index, source in enumerate(mesh):
            eid = uid(f'{name}/{part}/{index}')
            rotation = source['rotation']
            # Minecraft model center (8,8,8) -> planted Blockbench bone origin.
            element = dict(name=f'{part}_{index}', uuid=eid, type='cube', shade=False,
                           origin=[v-8 for v in rotation['origin']],
                           rotation=[rotation.get(axis, 0) for axis in 'xyz'],
                           **{'from': [v-8 for v in source['from']], 'to': [v-8 for v in source['to']]},
                           faces={face: dict(uv=value['uv'], texture=int(value['texture'][1:]))
                                  for face, value in source['faces'].items()})
            # WSEE requires all six UV entries. Missing sides belong only to
            # zero-depth contour faces; their geometric area is zero.
            for face in ('north','south','east','west','up','down'):
                element['faces'].setdefault(face, dict(uv=[0,0,0,0],texture=0))
            elements.append(element); children.append(eid)
        bid = uid(f'{name}/{part}')
        bones.append(dict(name=part, uuid=bid, origin=[0,0,0], children=children,
                          export=True, visibility=True))
        keys = []
        # Native bone scale, not full-size geometry sliding through the floor.
        for tick in range(41):
            local = tick-delay
            grow = smooth(local/(2 if part == 'root' else 3))
            close = smooth((local-((life-1)-(6 if part == 'root' else 8)))/(6 if part == 'root' else 8))
            y = (.018+.982*grow)*(1-close) if 0 <= local < life-1 else 0
            scale = [maximum[0], maximum[1]*y, maximum[2]] if y > 0 else [0,0,0]
            for channel, vector in (('scale', scale), ('position', list(offset))):
                keys.append(dict(channel=channel, time=tick/20, interpolation='linear',
                                 uuid=uid(f'{name}/{part}/{channel}/{tick}'), data_points=[dict(zip('xyz',vector))]))
        animators[bid] = dict(name=part, type='bone', keyframes=keys)
    textures = []
    for index, path in enumerate((ref.SOURCE, ref.BRANCH_SOURCE)):
        with Image.open(path) as im: width,height = im.size
        textures.append(dict(name=path.name, id=str(index), uuid=uid(path.name),
                             width=width, height=height, uv_width=16, uv_height=16,
                             source='data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode()))
    return dict(meta=dict(format_version='4.0', model_format='free', box_uv=False),
                name=name, resolution=dict(width=16,height=16), elements=elements,
                outliner=[dict(name='model', uuid=uid(name+'/model'), origin=[0,0,0], children=bones)],
                textures=textures, animations=[dict(name='erupt', uuid=uid(name+'/erupt'), loop='once',
                                                    override=True, length=2.0, snapping=20, animators=animators)])


if __name__ == '__main__':
    OUT.parent.mkdir(parents=True,exist_ok=True)
    for i,name in enumerate(NAMES):
        data = build(i)
        path = OUT.with_name(name+'.bbmodel')
        path.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
        print(f'{path}: {len(data["elements"])} elements / 3 moving bones / unchanged original paintings')
