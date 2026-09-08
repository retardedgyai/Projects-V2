"""Three preserved mace originals, crossed head flanges and animated crystals."""
import hashlib
import json
import numpy as np
from PIL import Image, ImageDraw
from build_specialist_armament_pack import ROOT, SOURCE as FIRST, geometry, pose
from process_specialist_armament_art import SOURCE, extract
from process_armament_art import pixelize, projected_box
from build_pixel_armament_pack import definition, write_json
from preview_class_armaments import render_model, FONT

PIXELS=SOURCE/'processed-mace-tiers-v01'
OUT=ROOT/'.tools/mace-tier-pack'
PACK=OUT/'pack'
ASSETS=PACK/'assets/projects'
JOBS={
    2:{'source':'sources/mace-t2-v01.png',
       'sha256':'0d895550a700581bcc34050b21cf2c88d17965ada9c0b3acfdbd324e273764e3',
       'crystal_box':[340,423,454,546],'head_bottom_y':850,'grip_y':[920,1550]},
    3:{'source':'sources/mace-t3-v01.png',
       'sha256':'57345a97497244a18e101419266e4bdd3d6e67364539a221edeeeebd891e9071',
       'crystal_box':[419,242,553,390],'head_bottom_y':649,'grip_y':[738,1275]},
    4:{'source':'sources/mace-t4-v01.png',
       'sha256':'418a9b3a85fb78858dffd4605bd9fe9d3e8a5c0cda28e4ce3737c299f1c198a1',
       'crystal_box':[336,439,460,568],'head_bottom_y':854,'grip_y':[936,1560]},
}


def tier_key(tier):
    if tier not in range(1,5): raise ValueError('Mace Tier must be 1..4')
    return 'mace' if tier==1 else f'mace_t{tier}'


def convert(tier):
    job=JOBS[tier]; path=SOURCE/job['source']
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    if digest!=job['sha256']: raise ValueError('Changed mace original: '+str(tier))
    with Image.open(path) as original:
        mode,size=original.mode,list(original.size)
        # Native alpha is preserved if a future original actually supplies it.
        pixels,transform=pixelize(extract(original) if mode=='RGB' else original,84)
    palette=np.array(json.loads((FIRST/'manifest.json').read_text())['weapons']['mace']['palette'],dtype=np.uint8)
    mask=pixels[:,:,3]>0
    distances=((pixels[mask,:3].astype(float)[:,None,:]-palette[None,:,:])**2).sum(2)
    pixels[mask,:3]=palette[distances.argmin(1)]
    x0,y0,x1,y1=projected_box(job['crystal_box'],transform)
    selected=np.zeros(mask.shape,dtype=bool); selected[y0:y1,x0:x1]=mask[y0:y1,x0:x1]
    if selected.sum()<10: raise ValueError('Missing mace crystal')
    crystal=np.zeros_like(pixels); crystal[selected]=pixels[selected]
    body=pixels.copy(); body[selected]=0
    row=lambda y:projected_box([0,y,0,y],transform)[1]
    textures={'body':body,'crystal':crystal}
    entry={'source':job['source'],'source_sha256':digest,'source_mode':mode,'source_size':size,
        **transform,'height':round(84*30/92,6),'palette':palette.tolist(),
        'head_bottom':row(job['head_bottom_y']),'grip_rows':[row(y) for y in job['grip_y']],
        'crystal_box':[x0,y0,x1,y1],
        'parts':{name:int((array[:,:,3]>0).sum()) for name,array in textures.items()}}
    return pixels,textures,entry


def model_data(tier):
    pixels,textures,entry=convert(tier)
    base,parts,textures,anchors=geometry('mace',entry,textures)
    key=tier_key(tier)
    base['textures']={part:f'projects:item/weapons/pixel_{key}_{part}' for part in textures}
    base['textures']['particle']=base['textures']['body']
    return pixels,textures,entry,base,parts,anchors


def build():
    PIXELS.mkdir(parents=True,exist_ok=True)
    manifest={'runtime_applied':False,'status':'Tier art review, not reference-quality approval','tiers':{}}
    first=json.loads((FIRST/'manifest.json').read_text())['weapons']['mace']
    base,parts,textures,anchors=geometry('mace',first)
    loaded={1:(base,parts,textures,anchors)}
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS mace tiers / isolated review',
        'min_format':[88,0],'max_format':[88,0]}})
    for tier in JOBS:
        pixels,textures,entry,base,parts,anchors=model_data(tier)
        key=tier_key(tier); manifest['tiers'][str(tier)]=entry
        Image.fromarray(pixels).save(PIXELS/f'{key}.png')
        for part,array in textures.items():
            Image.fromarray(array).save(PIXELS/f'{key}-{part}.png')
            target=ASSETS/f'textures/item/weapons/pixel_{key}_{part}.png'
            target.parent.mkdir(parents=True,exist_ok=True); Image.fromarray(array).save(target)
            write_json(target.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False}})
        for stage,count in (('rest',1),('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                write_json(ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json',pose('mace',base,parts,anchors,stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        loaded[tier]=(base,parts,textures,anchors)
    write_json(PIXELS/'manifest.json',manifest)
    sheet=Image.new('RGB',(1280,1140),'#1b1e23')
    for row,(stage,frame,yaw) in enumerate((('rest',0,0),('prepare',5,45),('rest',0,90))):
        for column,(tier,(base,parts,textures,anchors)) in enumerate(loaded.items()):
            suffix='' if stage=='rest' else f'_{stage}{frame:02}'
            model=pose('mace',base,parts,anchors,stage,frame) if tier==1 else json.loads((ASSETS/f'models/item/weapons/pixel_{tier_key(tier)}{suffix}.json').read_text())
            sheet.paste(render_model(model,textures,yaw=yaw,size=(320,350),scale=9.2),(column*320,row*380+30))
            ImageDraw.Draw(sheet).text((column*320+12,row*380+8),f'メイス T{tier} / {stage} / {yaw}°',font=FONT,fill='#dfd4c0')
    sheet.save(OUT/'tiers-review.png')
    print('Mace Tier originals / crossed head geometry / 25 poses each exported; T1 unchanged; not running.')


if __name__=='__main__': build()
