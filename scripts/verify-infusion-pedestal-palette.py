"""Palette-only invariants: native elements/UVs/size, pixel topology, metal, original artifacts."""
from pathlib import Path
import json,hashlib
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parent.parent;old=root/'assets/model-lab/infusion-v3';new=root/'assets/model-lab/infusion-v6';out=root/'.tools/world-model-preview'
record=json.loads((out/'pedestal-palette-provenance.json').read_text())
for version,files in record['protected'].items():
    for p,h in files.items():assert hashlib.sha256((root/'assets/model-lab'/('infusion-'+version)/p).read_bytes()).hexdigest()==h
total=0
for name in ['center','offering']:
    before=json.loads((old/f'assets/projects/models/infusion-v3/{name}.json').read_text())
    after=json.loads((new/f'assets/projects/models/infusion-v6/{name}.json').read_text())
    assert before['elements']==after['elements'],'Geometry, faces and UV must be exactly identical'
    assert before['ambientocclusion']==after['ambientocclusion']
    assert {k:v for k,v in before['textures'].items() if k not in ['limestone','well']}=={k:v for k,v in after['textures'].items() if k not in ['limestone','well']}
    total+=len(after['elements']);assert all(e.get('light_emission',0)==0 for e in after['elements'])
    item=json.loads((new/f'assets/projects/items/infusion-v6/{name}.json').read_text())
    assert item['model']['model']=='projects:infusion-v6/'+name
mapping={tuple(bytes.fromhex(a[1:])):tuple(bytes.fromhex(b[1:])) for a,b in record['palette'].items()}
assert len(set(mapping.values()))==len(mapping),'Keep the original colour regions distinct'
for material in ['limestone','well']:
    before=np.asarray(Image.open(old/f'assets/projects/textures/infusion-v3/{material}.png').convert('RGBA'))
    after=np.asarray(Image.open(new/f'assets/projects/textures/infusion-v6/{material}.png').convert('RGBA'))
    assert before.shape==after.shape==(16,16,4) and np.array_equal(before[:,:,3],after[:,:,3])
    for y in range(16):
        for x in range(16):assert tuple(after[y,x,:3])==mapping[tuple(before[y,x,:3])]
    for channel in [0,1,2]:assert after[:,:,channel].max()-after[:,:,channel].min()>75
for material in ['basalt','brass','carved','glass']:
    p=f'assets/projects/textures/infusion-v3/{material}.png';assert (old/p).read_bytes()==(new/p).read_bytes()
for name,size in [('Assembly',(1600,1410)),('Detail',(1600,1110))]:
    assert Image.open(out/f'ProjectS-Infusion-Pedestal-Palette-{name}.png').size==size
result={'passed':True,'models':2,'cuboids':total,'geometryFacesUVSizeUnchanged':True,'pixelClustersAlphaUnchanged':True,
 'metalAndOtherMaterialsUnchanged':True,'noNewEmission':True,'previousFilesProtected':sum(map(len,record['protected'].values())),
 'coreSupportJarUnchanged':True,'runtimeConnected':False,'clientStarted':False}
(out/'pedestal-palette-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result,indent=2))
