"""Compile original editable armament geometry/pixel source to vanilla RP files."""
import json
from class_armament_geometry import ASSETS, KINDS, FRAMES, all_models, authored_model, item_definition, write_atlas
from class_armament_actions import ACTION_FRAMES, action_model


def build():
    write_atlas()
    files = {}
    for kind in KINDS:
        for tier in range(1,5):
            name=f'{kind}_t{tier}'
            files[f'items/weapons/{name}.json']=item_definition(name)
            files[f'models/item/weapons/{name}.json']=authored_model(kind,tier)
            for frame in range(FRAMES):
                files[f'models/item/weapons/{name}_frame{frame:02d}.json']=authored_model(kind,tier,frame)
            for stage in ('prepare','release'):
                for frame in range(ACTION_FRAMES):
                    files[f'models/item/weapons/{name}_{stage}{frame:02d}.json']=action_model(kind,tier,stage,frame)
    files['textures/item/weapons/materials.png.mcmeta']={'texture':{'blur':False,'clamp':False}}
    for relative, data in files.items():
        path=ASSETS/relative
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')
    # Index only: do not regenerate the unrelated, already accepted UI/font.
    pack=ASSETS.parents[1]
    paths=sorted(str(p.relative_to(pack)).replace('\\','/') for p in pack.rglob('*') if p.is_file() and p.name!='index.txt')
    (pack/'index.txt').write_text('\n'.join(paths)+'\n',encoding='utf-8')
    print(f'Built {len(KINDS)*4} armaments, {FRAMES} idle + {ACTION_FRAMES*2} action poses each, native 64px atlas; updated pack index only.')


if __name__=='__main__':
    build()
