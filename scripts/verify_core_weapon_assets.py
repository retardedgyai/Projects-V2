"""Verify exported geometry, pixel density, animation and packaging (not final visual quality)."""
import argparse
import json
import math
from pathlib import Path
from PIL import Image
from class_armament_geometry import ASSETS, KINDS, FRAMES, MATERIALS, authored_model, item_definition


def point(v, rotation):
    if not rotation: return v
    axis='xyz'.index(rotation['axis']); axes=[i for i in range(3) if i!=axis]
    a=math.radians(rotation['angle']); o=rotation['origin']; p=list(v)
    u,w=v[axes[0]]-o[axes[0]],v[axes[1]]-o[axes[1]]
    p[axes[0]]=o[axes[0]]+u*math.cos(a)-w*math.sin(a)
    p[axes[1]]=o[axes[1]]+u*math.sin(a)+w*math.cos(a)
    return p


def verify(jar=None):
    atlas=Image.open(ASSETS/'textures/item/weapons/materials.png').convert('RGB')
    assert atlas.size==(64,64)
    for i, palette in enumerate(MATERIALS.values()):
        colors=set(atlas.crop((i%4*16,i//4*16,i%4*16+16,i//4*16+16)).getdata())
        assert colors <= {tuple(bytes.fromhex(c)) for c in palette}
        assert len(colors)<=4
    pack=ASSETS.parents[1]
    index=set((pack/'index.txt').read_text().splitlines())
    def read(path):
        assert 'assets/projects/'+path in index, path
        return json.loads((ASSETS/path).read_text())
    count=0
    for kind in KINDS:
        for tier in range(1,5):
            name=f'{kind}_t{tier}'
            definition=read(f'items/weapons/{name}.json')
            assert definition==item_definition(name)
            assert definition['hand_animation_on_swap'] is False
            assert read(f'models/item/weapons/{name}.json')==authored_model(kind,tier)
            poses=[]
            for frame in range(FRAMES):
                data=read(f'models/item/weapons/{name}_frame{frame:02d}.json')
                assert data==authored_model(kind,tier,frame)
                assert 15<=len(data['elements'])<=96
                poses.append(json.dumps(data['elements'],sort_keys=True))
                for e in data['elements']:
                    assert all(math.isfinite(v) for v in e['from']+e['to'])
                    assert all(-16<=a<b<=32 for a,b in zip(e['from'],e['to'])), (name,e['name'])
                    assert set(e['faces'])=={'north','south','east','west','up','down'}
                    if r:=e.get('rotation'):
                        assert r['angle'] in (-45,-22.5,0,22.5,45)
                        assert r['axis'] in ('x','y','z') and not r['rescale']
                    for face in e['faces'].values():
                        assert face['texture']=='#atlas'
                        uv=face['uv']; assert all(0<=v<=16 for v in uv)
                        assert uv[0]<uv[2] and uv[1]<uv[3]
                        # Face cannot bleed into another 16px material tile.
                        assert int(uv[0]//4)==int((uv[2]-1e-7)//4)
                        assert int(uv[1]//4)==int((uv[3]-1e-7)//4)
                    gui=data['display']['gui']; a=math.radians(gui['rotation'][2])
                    for corner in range(8):
                        p=point([e['to'][j] if corner&(1<<j) else e['from'][j] for j in range(3)],e.get('rotation'))
                        assert all(-16<=v<=32 for v in p), (name,e['name'],p)
                        x,y=p[0]-8,p[1]-8
                        gx=(x*math.cos(a)-y*math.sin(a))*gui['scale'][0]+gui['translation'][0]
                        gy=(x*math.sin(a)+y*math.cos(a))*gui['scale'][1]+gui['translation'][1]
                        assert -8<=gx<=8 and -8<=gy<=8,(name,e['name'],gx,gy)
                count+=1
            assert len(set(poses))>=6,(name,'idle animation has insufficient distinct poses')
    assert not any(x.startswith(('assets/minecraft/items/','assets/minecraft/models/')) for x in index)
    print(f'PASS: {len(KINDS)*4} weapons / {count} exported frames; 64px atlas, max 4 shades per material, UV tiles, finite bounds, GUI sockets, index, changed geometry, no vanilla overrides.')
    if jar: print('No vanilla source textures are referenced; --vanilla-jar retained for command compatibility.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--vanilla-jar',type=Path)
    verify(parser.parse_args().vanilla_jar)
