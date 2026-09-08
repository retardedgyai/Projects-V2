"""Three original Tier silhouettes, same painted material and thin native geometry.

T1 remains the first greatsword. This changes art selection only, never gear stats.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from process_armament_art import pixelize, projected_box
from build_pixel_armament_pack import geometry, pose, definition, write_json, SOURCE as FIRST
from preview_class_armaments import render_model, FONT

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'assets/class-armaments/texture-first'
PIXELS=SOURCE/'processed-greatsword-tiers-v01'
OUT=ROOT/'.tools/greatsword-tier-pack'
PACK=OUT/'pack'
ASSETS=PACK/'assets/projects'
JOBS={
    2:{'source':'sources/greatsword-t2-v01.png',
       'sha256':'37b5f5bece8bd4df13e202ca6a3034c87736ed9d697739d9774b51945c1348a4',
       'split_y':[1320,1700], 'jewel_box':[314,1468,431,1618]},
    3:{'source':'sources/greatsword-t3-v01.png',
       'sha256':'566a23a37415629bdd8dee4fdb9fe4ddf2ac3cdfd2ac32afd074ea16275b3beb',
       'split_y':[1300,1640], 'jewel_box':[296,1412,433,1575]},
    4:{'source':'sources/greatsword-t4-v01.png',
       'sha256':'b46241aaad6698df2a461a2b74f50aa1422d7b98d99e588511e3c4db6cf8fc87',
       'split_y':[1050,1710], 'jewel_box':[277,1385,445,1575]},
}


def tier_key(tier):
    if tier not in range(1,5): raise ValueError('Greatsword Tier must be 1..4')
    return 'greatsword' if tier==1 else f'greatsword_t{tier}'


def convert(tier):
    job=JOBS[tier]; path=SOURCE/job['source']
    digest=hashlib.sha256(path.read_bytes()).hexdigest()
    if digest!=job['sha256']: raise ValueError('Changed Tier original: '+str(tier))
    with Image.open(path) as image:
        mode,size=image.mode,list(image.size)
        pixels,transform=pixelize(image,92)
    # Keep the existing first-tier material exactly; do not recolor other weapons.
    palette=np.array(json.loads((FIRST/'manifest.json').read_text())['palettes']['crimson'],dtype=np.uint8)
    mask=pixels[:,:,3]>0
    distance=((pixels[mask,:3].astype(float)[:,None,:]-palette[None,:,:])**2).sum(2)
    pixels[mask,:3]=palette[distance.argmin(1)]
    x0,y0,x1,y1=projected_box(job['jewel_box'],transform)
    rgb=pixels[:,:,:3].astype(int)
    selected=np.zeros(mask.shape,dtype=bool)
    selected[y0:y1,x0:x1]=(mask & (rgb[:,:,0]>rgb[:,:,1]*1.5) & (rgb[:,:,0]>rgb[:,:,2]*1.2))[y0:y1,x0:x1]
    if selected.sum()<6: raise ValueError('Missing drawn Tier gem: '+str(tier))
    jewel=np.zeros_like(pixels); jewel[selected]=pixels[selected]
    body=pixels.copy(); body[selected]=[48,35,54,255]
    entry={'source':job['source'],'source_sha256':digest,'source_mode':mode,'source_size':size,
        **transform,'height':30.0,'palette':palette.tolist(),
        'rows':[2]+[projected_box([0,y,0,y],transform)[1] for y in job['split_y']]+[94],
        'jewel_box':[x0,y0,x1,y1],'jewel_pixels':int(selected.sum())}
    return pixels,{'body':body,'jewel':jewel},entry


def model_data(tier):
    pixels,textures,entry=convert(tier)
    base,gem,_=geometry('greatsword',entry,textures)
    key=tier_key(tier)
    base['textures']={part:f'projects:item/weapons/pixel_{key}_{part}' for part in textures}
    base['textures']['particle']=base['textures']['body']
    return pixels,textures,entry,base,gem


def build():
    PIXELS.mkdir(parents=True,exist_ok=True)
    manifest={'runtime_applied':False,'status':'Tier art review, not reference-quality approval','tiers':{}}
    loaded={}
    first_entry=json.loads((FIRST/'manifest.json').read_text())['weapons']['greatsword']
    base,gem,textures=geometry('greatsword',first_entry)
    loaded[1]=(base,gem,textures)
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS greatsword tiers / isolated review',
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
                write_json(ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json',pose(base,gem,'greatsword',stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        loaded[tier]=(base,gem,textures)
    (PIXELS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    sheet=Image.new('RGB',(1280,1140),'#1b1e23')
    for row,yaw in enumerate((0,-35,180)):
        for column,(tier,(base,gem,textures)) in enumerate(loaded.items()):
            model=pose(base,gem,'greatsword') if tier==1 else json.loads((ASSETS/f'models/item/weapons/pixel_{tier_key(tier)}.json').read_text())
            sheet.paste(render_model(model,textures,yaw=yaw,size=(320,350),scale=9.2),(320*column,380*row+30))
            ImageDraw.Draw(sheet).text((320*column+12,380*row+8),f'大剣 T{tier} / {yaw}°',font=FONT,fill='#dfd4c0')
    sheet.save(OUT/'tiers-review.png')
    print('Greatsword T2-T4: 3 originals / 75 native poses; T1 art unchanged; not running.')


if __name__=='__main__': build()
