"""Export unchanged approved-direction models and a geometry-identical, event-tinted core to the isolated pack."""
from pathlib import Path
from PIL import Image
import json,hashlib,shutil
root=Path(__file__).resolve().parent.parent;lab=root/'assets/model-lab';new=lab/'infusion-v7'
pack=root/'server-minestom/src/main/resources/core-ui-pack'
def hashes(p):return {str(f.relative_to(p)):hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*') if f.is_file()}
protected={v:hashes(lab/('infusion-'+v)) for v in ['v3','v4','v5','v6']}
texture=new/'assets/projects/textures/infusion-v7';texture.mkdir(parents=True,exist_ok=True)
for source,target in [('seal_active','seal_charged'),('leak_active','leak_charged')]:
    Image.open(lab/f'infusion-v5/assets/projects/textures/infusion-v5/{source}.png').crop((0,192,16,208)).save(texture/(target+'.png'))
model=json.loads((lab/'infusion-v5/assets/projects/models/infusion-v5/core_active.json').read_text())
model['credit']='ProjectS v5 exact shape; committed-ritual tint only on emissive elements'
model['textures']['glow']='projects:infusion-v7/seal_charged'
model['textures']['leak']={'sprite':'projects:infusion-v7/leak_charged','force_translucent':True}
for e in model['elements']:
    if e.get('light_emission')==15:
        for f in e['faces'].values():f['tintindex']=0
mp=new/'assets/projects/models/infusion-v7/core_ritual.json';mp.parent.mkdir(parents=True,exist_ok=True)
mp.write_text(json.dumps(model,indent=2)+'\n',encoding='utf8')
ip=new/'assets/projects/items/infusion-v7/core_ritual.json';ip.parent.mkdir(parents=True,exist_ok=True)
ip.write_text(json.dumps({'model':{'type':'minecraft:model','model':'projects:infusion-v7/core_ritual',
  'tints':[{'type':'minecraft:custom_model_data','index':0,'default':0x383838}]}},indent=2)+'\n',encoding='utf8')
# Only scoped new IDs; do not replace old world models or global shaders. All are original ProjectS pixels.
paths=['infusion-v3/assets/projects/models/infusion-v3/jar.json','infusion-v3/assets/projects/items/infusion-v3/jar.json',
 'infusion-v4/assets/projects/models/infusion-v4/support.json','infusion-v4/assets/projects/items/infusion-v4/support.json']
for n in ['center','offering']:
    for kind in ['models','items']:paths.append(f'infusion-v6/assets/projects/{kind}/infusion-v6/{n}.json')
for p in paths:
    source=lab/p;dest=pack/Path(*Path(p).parts[1:]);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
for v,folder in [('v3','infusion-v3'),('v6','infusion-v6'),('v7','infusion-v7')]:
    for source in (lab/f'infusion-{v}/assets/projects/textures/{folder}').glob('*.png'):
        dest=pack/source.relative_to(lab/('infusion-'+v));dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
for name in ['sealed_carved','seal_well']:
    source=lab/f'infusion-v5/assets/projects/textures/infusion-v5/{name}.png';dest=pack/source.relative_to(lab/'infusion-v5')
    dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
for source in (new/'assets/projects').rglob('*.json'):
    dest=pack/source.relative_to(new);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
assert all(hashes(lab/('infusion-'+v))==h for v,h in protected.items())
out=root/'.tools/world-model-preview';out.mkdir(parents=True,exist_ok=True)
(out/'ritual-art-provenance.json').write_text(json.dumps({'protected':protected,'newFiles':hashes(new),'nativeGeometryUnchanged':True,
  'onlyTintedFaces':'emission=15; stone/metal untinted','packScope':'opt-in isolated world playground; no main/server deployment'},indent=2)+'\n',encoding='utf8')
print('Exported original dedicated ritual assets; unchanged v3-v6 masters protected')
