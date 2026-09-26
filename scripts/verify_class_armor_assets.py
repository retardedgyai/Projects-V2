"""Structural tests, not a claim of visual parity with the references."""
import json
import math
from PIL import Image
from build_class_armor_assets import ASSETS,JOBS,SLOTS,armor_model,armor_texture,icon_texture
from build_project_helmets import helmet_texture as project_helmet_texture
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
            face_relative=f'textures/item/armor/helmet_faces/{job}_t{tier}.png'
            assert 'assets/projects/'+face_relative in index
            helmet_image=Image.open(ASSETS/face_relative).convert('RGBA')
            assert helmet_image.size==(128,64)
            assert helmet_image.tobytes()==project_helmet_texture(job,tier).tobytes()
            for layer,inner in (('humanoid',False),('humanoid_leggings',True)):
                assert equipment['layers'][layer]==[{'texture':f'projects:{stem}'}]
                relative=f'textures/entity/equipment/{layer}/{stem}.png'
                assert 'assets/projects/'+relative in index
                image=Image.open(ASSETS/relative).convert('RGBA')
                expected_size=(64,32)
                assert image.size==expected_size and image.tobytes()==armor_texture(job,tier,inner).tobytes()
                item_relative=f'textures/item/{stem}_{"inner" if inner else "outer"}.png'
                assert 'assets/projects/'+item_relative in index
                assert Image.open(ASSETS/item_relative).convert('RGBA').tobytes()==image.tobytes()
                assert set(np.array(image)[:,:,3].flat)<={0,255}
                if not inner: surfaces.add(image.tobytes())
            for slot in SLOTS:
                name=f'{stem}_{slot}'
                selection=read(f'items/{name}.json')['model']
                assert selection['type']=='minecraft:select'
                assert selection['property']=='minecraft:display_context'
                assert selection['cases']==[{'when':['gui'],'model':{'type':'minecraft:model',
                    'model':f'projects:item/armor/icons/{job}_t{tier}_{slot}'}}]
                assert selection['fallback']=={'type':'minecraft:model','model':f'projects:item/{name}'}
                icon_model=read(f'models/item/armor/icons/{job}_t{tier}_{slot}.json')
                assert icon_model['parent']=='minecraft:item/generated'
                icon_relative=f'textures/item/armor/icons/{job}_t{tier}_{slot}.png'
                assert 'assets/projects/'+icon_relative in index
                icon=Image.open(ASSETS/icon_relative).convert('RGBA')
                assert icon.size==(16,16) and icon.tobytes()==icon_texture(job,tier,slot).tobytes()
                assert set(np.array(icon)[:,:,3].flat)=={0,255}
                model=read(f'models/item/{name}.json'); assert model==armor_model(job,tier,slot)
                assert model['textures']['outer']==f'projects:item/{stem}_outer'
                assert model['textures']['inner']==f'projects:item/{stem}_inner'
                assert model['textures']['helm']==f'projects:item/armor/helmet_faces/{job}_t{tier}'
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
                    assert all(face['texture']=='#helm' for element in model['elements']
                               for face in element['faces'].values()),'Helmet paint must not be replaced by body UVs'
                count+=1
    assert len(surfaces)==28,'Every class/tier must have its own painted surface'
    print(f'PASS: 28 worn sets, 56 equipment textures and item-atlas copies, {count} 3D models and UI icons, head transform, finite geometry, index and class variation.')


if __name__=='__main__': verify()
