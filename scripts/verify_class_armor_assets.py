"""Structural tests, not a claim of visual parity with the references."""
import json
import math
from PIL import Image
from build_class_armor_assets import ASSETS,JOBS,SLOTS,armor_model,armor_texture
from verify_core_weapon_assets import point
import numpy as np


def verify():
    index=set((ASSETS.parents[1]/'index.txt').read_text().splitlines()); surfaces=set(); count=0
    def read(relative):
        assert 'assets/projects/'+relative in index,relative
        return json.loads((ASSETS/relative).read_text())
    for job in JOBS:
        for tier in range(1,5):
            stem=f'armor/{job}_t{tier}'
            equipment=read(f'equipment/{stem}.json')
            for layer,inner in (('humanoid',False),('humanoid_leggings',True)):
                assert equipment['layers'][layer]==[{'texture':f'projects:{stem}'}]
                relative=f'textures/entity/equipment/{layer}/{stem}.png'
                assert 'assets/projects/'+relative in index
                image=Image.open(ASSETS/relative).convert('RGBA')
                assert image.size==(64,32) and image.tobytes()==armor_texture(job,tier,inner).tobytes()
                assert set(np.array(image)[:,:,3].flat)<={0,255}
                if not inner: surfaces.add(image.tobytes())
            for slot in SLOTS:
                name=f'{stem}_{slot}'
                assert read(f'items/{name}.json')['model']['model']==f'projects:item/{name}'
                model=read(f'models/item/{name}.json'); assert model==armor_model(job,tier,slot)
                assert len(model['elements'])<=48
                for e in model['elements']:
                    assert all(-16<=a<b<=32 for a,b in zip(e['from'],e['to']))
                    for corner in range(8):
                        p=point([e['to'][j] if corner&(1<<j) else e['from'][j] for j in range(3)],e.get('rotation'))
                        assert all(math.isfinite(n) and -16<=n<=32 for n in p)
                    for face in e['faces'].values(): assert all(0<=v<=16 for v in face['uv'])
                if slot=='helmet':
                    assert model['display']['head']['scale']==[1.6]*3
                    assert model['display']['head']['translation']==[0,0,0]
                count+=1
    assert len(surfaces)==28,'Every class/tier must have its own painted surface'
    print(f'PASS: 28 worn sets, 56 exact native 64x32 textures, {count} item models, head transform, finite geometry, index and class variation.')


if __name__=='__main__': verify()
