"""Success-only native pixel seal inside the approved bowl; never repaint original pedestal pixels."""
from pathlib import Path
import json,hashlib
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parent.parent;lab=root/'assets/model-lab/infusion-energy';pack=root/'server-minestom/src/main/resources/core-ui-pack';out=root/'.tools/world-model-preview'
protected={str(p.relative_to(root)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'assets/model-lab').rglob('*') if p.is_file() and 'infusion-v' in str(p)}
for n in ['ProjectS-Infusion-Ritual-Animation.gif','ProjectS-Infusion-Confluence-Low-High.gif']:
    p=out/n
    if p.exists():protected[str(p.relative_to(root)).replace('\\','/')]=hashlib.sha256(p.read_bytes()).hexdigest()
im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
d.line([(6,3),(9,3),(12,6),(12,9),(9,12),(6,12),(3,9),(3,6),(6,3)],fill=(161,104,209,235),width=1)
d.line([(7,5),(10,8),(7,11),(4,8),(7,5)],fill=(191,139,228,230),width=1)
for x,y in [(6,3),(12,9),(9,12),(3,6)]:d.point((x,y),fill=(213,177,237,245))
for x,y in [(7,3),(12,7),(8,12),(3,8)]:d.point((x,y),fill=(105,63,139,200))
tex='assets/projects/textures/infusion-energy/pedestal_seal.png';model='assets/projects/models/infusion-energy/pedestal_seal.json';item='assets/projects/items/infusion-energy/pedestal_seal.json'
asset={'credit':'ProjectS original success-only 16px seal; approved bowl geometry/pixels preserved','ambientocclusion':False,
 'textures':{'seal':'projects:infusion-energy/pedestal_seal'},
 'elements':[{'from':[3,12.04,3],'to':[13,12.05,13],'shade':False,'light_emission':15,
 'faces':{'up':{'texture':'#seal','uv':[3,3,13,13],'tintindex':0}}}]}
definition={'model':{'type':'minecraft:model','model':'projects:infusion-energy/pedestal_seal',
 'tints':[{'type':'minecraft:custom_model_data','index':0,'default':16777215}]}}
for destination in [lab,pack]:
    for p in [tex,model,item]:(destination/p).parent.mkdir(parents=True,exist_ok=True)
    im.save(destination/tex)
    (destination/model).write_text(json.dumps(asset,indent=2)+'\n',encoding='utf8')
    (destination/item).write_text(json.dumps(definition,indent=2)+'\n',encoding='utf8')
for p,h in protected.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
(out/'energy-art-preservation.json').write_text(json.dumps({'protected':protected,'successSealSHA256':hashlib.sha256((pack/tex).read_bytes()).hexdigest(),'originalModelChanged':False},indent=2)+'\n',encoding='utf8')
print(json.dumps({'protectedFiles':len(protected),'nativeSealPixels':16,'originalModelChanged':False}))
