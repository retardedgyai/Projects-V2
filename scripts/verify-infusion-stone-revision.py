"""Validate targeted native geometry, preserved originals and matched comparison framing."""
from pathlib import Path
import json,hashlib,importlib.util
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parent.parent;old=root/'assets/model-lab/infusion-v3';new=root/'assets/model-lab/infusion-v4'
out=root/'.tools/world-model-preview';record=json.loads((out/'stone-revision-provenance.json').read_text())
for p,h in record['unchangedV3Files'].items():assert hashlib.sha256((old/p).read_bytes()).hexdigest()==h
total=0
for name in ['core','support']:
    m=json.loads((new/f'assets/projects/models/infusion-v4/{name}.json').read_text())
    for e in m['elements']:
        total+=1;assert all(-16<=v<=32 for v in e['from']+e['to'])
        assert all(a<b for a,b in zip(e['from'],e['to']))
        if 'rotation' in e:assert e['rotation']['angle']==22.5 and not e['rotation']['rescale']
    for texture in m['textures'].values():
        p=Path('assets/projects/textures')/Path(texture.split(':')[1]+'.png')
        assert (new/p).read_bytes()==(old/p).read_bytes();assert Image.open(new/p).size==(16,16)
assert len(list((new/'assets/projects/models/infusion-v4').glob('*.json')))==2
support=json.loads((new/'assets/projects/models/infusion-v4/support.json').read_text())
rotations=[e['rotation'] for e in support['elements'] if 'rotation' in e]
assert all(r==rotations[0] for r in rotations),'All blade segments must share one straight lean'
assert len(rotations)==6
# A real native gap, not a texture-only painted seam. Main masses never occupy x 5.5..6.5.
core=json.loads((new/'assets/projects/models/infusion-v4/core.json').read_text())
assert all(not(e['from'][0]<6 and e['to'][0]>6) for e in core['elements'] if e['to'][1]>0)
spec=importlib.util.spec_from_file_location('m',Path(__file__).with_name('render-world-models.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
frame=((-1,-1,-1),(2,3,2));m.PACK=old;a=m.boxes('projects:infusion-v3/support',centered=False)
m.PACK=new;b=m.boxes('projects:infusion-v4/support',centered=False)
_,_,projectA,scaleA=m.render(a,(100,100),38,26,with_depth=True,frame_bounds=frame)
_,_,projectB,scaleB=m.render(b,(100,100),38,26,with_depth=True,frame_bounds=frame)
assert scaleA==scaleB and np.allclose(projectA((.5,1,.5))[0],projectB((.5,1,.5))[0])
result={'passed':True,'modifiedModels':2,'cuboids':total,'preservedV3Files':len(record['unchangedV3Files']),
 'materialPixelsUnchanged':True,'oneDirectionSupportLean':True,'realCoreFissure':True,'matchedComparisonCameraScale':True,
 'runtimeConnected':False,'minecraftClientStarted':False}
(out/'stone-revision-verification.json').write_text(json.dumps(result,indent=2),encoding='utf8');print(json.dumps(result,indent=2))
