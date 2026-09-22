"""Tier-specific painted mage heads with their own branch roots and gem masks."""
import hashlib
import json
import numpy as np
from PIL import Image, ImageDraw
from build_greatsword_tiers import ROOT, SOURCE, FIRST
from process_armament_art import pixelize, projected_box
from build_pixel_armament_pack import geometry, pose, definition, write_json
from preview_class_armaments import render_model, FONT

PIXELS=SOURCE/'processed-staff-tiers-v01'
OUT=ROOT/'.tools/staff-tier-pack'
PACK=OUT/'pack'
ASSETS=PACK/'assets/projects'
JOBS={
    2:{'source':'sources/staff-t2-v01.png',
       'sha256':'7ed76a60098a05fce31201888ab276bb02d02ff516f30574531c429db687b315',
       'split_y':[730,1308],'jewel_box':[466,270,566,484],
       'fin_split_x':515,'fin_bottom_y':650},
    3:{'source':'sources/staff-t3-v01.png',
       'sha256':'8c59f27ba8e0535e6751038bfac1def907932d1abae5b5da21d29f69cdc475c9',
       'split_y':[734,1298],'jewel_box':[449,323,580,530],
       'fin_split_x':512,'fin_bottom_y':650},
    4:{'source':'sources/staff-t4-v02.png',
       'sha256':'d0e473d8412d6ff13b4c2acf98e2ba126f0961bd91f5dd1b9469aaac7e38c0e1',
       'split_y':[830,1374],'jewel_box':[406,315,535,547],
       'fin_split_x':470,'fin_bottom_y':750},
}


def tier_key(tier):
    if tier not in range(1,5): raise ValueError('Staff Tier must be 1..4')
    return 'staff' if tier==1 else f'staff_t{tier}'


def convert(tier):
    job=JOBS[tier]; path=SOURCE/job['source']
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    if digest!=job['sha256']: raise ValueError('Changed staff original: '+str(tier))
    with Image.open(path) as image:
        mode,size=image.mode,list(image.size)
        pixels,transform=pixelize(image,92)
    palette=np.array(json.loads((FIRST/'manifest.json').read_text())['palettes']['cyan'],dtype=np.uint8)
    mask=pixels[:,:,3]>0
    distance=((pixels[mask,:3].astype(float)[:,None,:]-palette[None,:,:])**2).sum(2)
    pixels[mask,:3]=palette[distance.argmin(1)]
    x0,y0,x1,y1=projected_box(job['jewel_box'],transform)
    selected=np.zeros(mask.shape,dtype=bool); selected[y0:y1,x0:x1]=mask[y0:y1,x0:x1]
    if selected.sum()<12: raise ValueError('Missing staff crystal')
    jewel=np.zeros_like(pixels); jewel[selected]=pixels[selected]
    body=pixels.copy(); body[selected]=0  # Include the gem's outline; leave no ghost.
    split=projected_box([job['fin_split_x'],0,job['fin_split_x'],0],transform)[0]
    bottom=projected_box([0,job['fin_bottom_y'],0,job['fin_bottom_y']],transform)[1]
    entry={'source':job['source'],'source_sha256':digest,'source_mode':mode,'source_size':size,
        **transform,'height':30.0,'palette':palette.tolist(),
        'rows':[2]+[projected_box([0,y,0,y],transform)[1] for y in job['split_y']]+[94],
        'jewel_box':[x0,y0,x1,y1],'jewel_pixels':int(selected.sum()),
        'fin_layout':{'split_x':split,'head_bottom':bottom}}
    return pixels,{'body':body,'jewel':jewel},entry


def model_data(tier):
    pixels,textures,entry=convert(tier)
    base,gem,_=geometry('staff',entry,textures)
    key=tier_key(tier)
    base['textures']={part:f'projects:item/weapons/pixel_{key}_{part}' for part in textures}
    base['textures']['particle']=base['textures']['body']
    return pixels,textures,entry,base,gem


def build():
    PIXELS.mkdir(parents=True,exist_ok=True)
    manifest={'runtime_applied':False,'status':'Tier art review, not reference-quality approval','tiers':{}}
    first_entry=json.loads((FIRST/'manifest.json').read_text())['weapons']['staff']
    base,gem,textures=geometry('staff',first_entry)
    loaded={1:(base,gem,textures)}
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS staff tiers / isolated review',
        'min_format':[88,0],'max_format':[88,0]}})
    for tier in JOBS:
        pixels,textures,entry,base,gem=model_data(tier)
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
                write_json(ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json',pose(base,gem,'staff',stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        loaded[tier]=(base,gem,textures)
    (PIXELS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    sheet=Image.new('RGB',(1280,1140),'#1b1e23')
    for row,(stage,frame,yaw) in enumerate((('rest',0,0),('prepare',5,0),('rest',0,180))):
        for column,(tier,(base,gem,textures)) in enumerate(loaded.items()):
            suffix='' if stage=='rest' else f'_{stage}{frame:02}'
            model=pose(base,gem,'staff',stage,frame) if tier==1 else json.loads((ASSETS/f'models/item/weapons/pixel_{tier_key(tier)}{suffix}.json').read_text())
            sheet.paste(render_model(model,textures,yaw=yaw,size=(320,350),scale=9.2),(320*column,380*row+30))
            ImageDraw.Draw(sheet).text((320*column+12,380*row+8),f'魔杖 T{tier} / {stage} / {yaw}°',font=FONT,fill='#dfd4c0')
    sheet.save(OUT/'tiers-review.png')
    print('Staff Tier originals and articulated native poses exported; T1 unchanged; not running.')


if __name__=='__main__': build()
