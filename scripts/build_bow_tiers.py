"""Original bow Tier silhouettes with attached cords and a nocked moving arrow."""
import hashlib
import json
import numpy as np
from PIL import Image, ImageDraw
from build_specialist_armament_pack import ROOT, SOURCE as FIRST, geometry, pose
from process_specialist_armament_art import SOURCE, extract
from process_armament_art import pixelize, projected_box
from build_pixel_armament_pack import definition, write_json
from preview_class_armaments import render_model, FONT

PIXELS=SOURCE/'processed-bow-tiers-v01'
OUT=ROOT/'.tools/bow-tier-pack'
PACK=OUT/'pack'
ASSETS=PACK/'assets/projects'
JOBS={
    2:{'source':'sources/bow-t2-v01.png',
       'sha256':'4c3d017fa8ea2ef0bcbe0712270c3567f8384d446641217032bc5e33ba7b8c89','cuts_y':[755,1134]},
    3:{'source':'sources/bow-t3-v01.png',
       'sha256':'9c23894a7fb9f057153fe3c0ab32e5c3f79546c8d9e244377476067880b86009','cuts_y':[788,1156]},
    4:{'source':'sources/bow-t4-v02.png',
       'sha256':'93be96cd330e81ec44c64773326184388209e423de02d70fadd94745394f3354','cuts_y':[772,1118]},
}


def tier_key(tier):
    if tier not in range(1,5): raise ValueError('Bow Tier must be 1..4')
    return 'bow' if tier==1 else f'bow_t{tier}'


def convert(tier):
    job=JOBS[tier]; path=SOURCE/job['source']
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    if digest!=job['sha256']: raise ValueError('Changed bow original: '+str(tier))
    with Image.open(path) as original:
        mode,size=original.mode,list(original.size)
        pixels,transform=pixelize(extract(original) if mode=='RGB' else original,92)
    palette=np.array(json.loads((FIRST/'manifest.json').read_text())['weapons']['bow']['palette'],dtype=np.uint8)
    opaque=pixels[:,:,3]>0
    distance=((pixels[opaque,:3].astype(float)[:,None,:]-palette[None,:,:])**2).sum(2)
    pixels[opaque,:3]=palette[distance.argmin(1)]
    upper,lower=[projected_box([0,y,0,y],transform)[1] for y in job['cuts_y']]
    yy,_=np.indices(opaque.shape)
    masks={'upper_limb':opaque&(yy<upper),'grip':opaque&(yy>=upper)&(yy<lower),
           'lower_limb':opaque&(yy>=lower)}
    textures={}
    for name,mask in masks.items():
        if not mask.any(): raise ValueError('Empty bow part: '+name)
        layer=np.zeros_like(pixels); layer[mask]=pixels[mask]; textures[name]=layer
    entry={'source':job['source'],'source_sha256':digest,'source_mode':mode,'source_size':size,
        **transform,'height':30.0,'palette':palette.tolist(),'cuts':[upper,lower],
        'parts':{name:int((array[:,:,3]>0).sum()) for name,array in textures.items()}}
    return pixels,textures,entry


def model_data(tier):
    pixels,textures,entry=convert(tier)
    base,parts,textures,anchors=geometry('bow',entry,textures)
    key=tier_key(tier)
    base['textures']={part:f'projects:item/weapons/pixel_{key}_{part}' for part in textures}
    base['textures']['particle']=base['textures']['upper_limb']
    return pixels,textures,entry,base,parts,anchors


def build():
    PIXELS.mkdir(parents=True,exist_ok=True)
    manifest={'runtime_applied':False,'status':'Tier art review, not reference-quality approval','tiers':{}}
    first=json.loads((FIRST/'manifest.json').read_text())['weapons']['bow']
    base,parts,textures,anchors=geometry('bow',first)
    loaded={1:(base,parts,textures,anchors)}
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS bow tiers / isolated review',
        'min_format':[88,0],'max_format':[88,0]}})
    for tier in JOBS:
        pixels,textures,entry,base,parts,anchors=model_data(tier)
        key=tier_key(tier); manifest['tiers'][str(tier)]=entry
        Image.fromarray(pixels).save(PIXELS/f'{key}.png')
        for part,array in textures.items():
            # Only the three painted original layers belong in source assets.
            # String/arrow are existing shared artwork, exported into this pack.
            if part in entry['parts']: Image.fromarray(array).save(PIXELS/f'{key}-{part}.png')
            target=ASSETS/f'textures/item/weapons/pixel_{key}_{part}.png'
            target.parent.mkdir(parents=True,exist_ok=True); Image.fromarray(array).save(target)
            write_json(target.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False}})
        for stage,count in (('rest',1),('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                suffix='' if stage=='rest' else f'_{stage}{frame:02}'
                write_json(ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json',pose('bow',base,parts,anchors,stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        loaded[tier]=(base,parts,textures,anchors)
    write_json(PIXELS/'manifest.json',manifest)
    sheet=Image.new('RGB',(1280,1140),'#1b1e23')
    for row,(stage,frame,yaw) in enumerate((('rest',0,0),('prepare',5,0),('release',2,-30))):
        for column,(tier,(base,parts,textures,anchors)) in enumerate(loaded.items()):
            suffix='' if stage=='rest' else f'_{stage}{frame:02}'
            model=pose('bow',base,parts,anchors,stage,frame) if tier==1 else json.loads((ASSETS/f'models/item/weapons/pixel_{tier_key(tier)}{suffix}.json').read_text())
            sheet.paste(render_model(model,textures,yaw=yaw,size=(320,350),scale=8.6),(column*320,row*380+30))
            ImageDraw.Draw(sheet).text((column*320+12,row*380+8),f'弓 T{tier} / {stage} {frame} / {yaw}°',font=FONT,fill='#dfd4c0')
    sheet.save(OUT/'tiers-review.png')
    print('Bow Tier originals / draw-release poses / attached strings exported; T1 unchanged; not running.')


if __name__=='__main__': build()
