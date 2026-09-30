"""Validate authored native assets and exact rotation support before model review. No server/client launch."""
from pathlib import Path
import json,math,importlib.util
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parent.parent
lab=root/'assets/model-lab/infusion-v3'
models=list((lab/'assets/projects/models/infusion-v3').glob('*.json'))
assert len(models)==5
elements=0;rotated=0
for p in models:
    raw=json.loads(p.read_text())
    for name in raw['textures'].values():
        assert name.startswith('projects:infusion-v3/')
        img=Image.open(lab/'assets/projects/textures'/Path(name.split(':')[1]+'.png'))
        assert img.size==(16,16)
    for e in raw['elements']:
        elements+=1
        assert all(-16<=v<=32 for v in e['from']+e['to'])
        assert all(a<b for a,b in zip(e['from'],e['to']))
        assert len(e['faces'])==6
        if 'rotation' in e:
            rotated+=1;r=e['rotation'];assert r['angle'] in [-45,-22.5,0,22.5,45]
            assert r['axis'] in ['x','y','z'] and r['rescale'] is False
        for f in e['faces'].values():assert len(f['uv'])==4 and all(math.isfinite(x) for x in f['uv'])
    item=json.loads((lab/'assets/projects/items/infusion-v3'/p.name).read_text())
    assert item['model']['model']=='projects:infusion-v3/'+p.stem
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);m.PACK=lab
core=m.boxes('projects:infusion-v3/core',centered=False)[0]
matrix=core[4];offset=core[5]
assert np.allclose(matrix.T@matrix,np.eye(3))
assert np.allclose(matrix@np.array([.5,.5,.5])+offset,[.5,.5,.5])
assert not np.allclose(matrix,np.eye(3)), 'Native stone tilt must actually be rendered'
assert np.allclose(m.boxes('projects:infusion-v3/support',centered=False,yaw=90)[0][4]@np.array([.5,0,.5])+
                   m.boxes('projects:infusion-v3/support',centered=False,yaw=90)[0][5],[.5,0,.5])
out=root/'.tools/world-model-preview'
record={'passed':True,'models':len(models),'cuboids':elements,'rotatedCuboids':rotated,'textures':6,
        'texturePixels':[16,16],'actualElementRotationRendered':True,'runtimeConnected':False,'minecraftScreenshot':False}
(out/'stone-kit-verification.json').write_text(json.dumps(record,indent=2),encoding='utf8')
print(json.dumps(record,indent=2))
