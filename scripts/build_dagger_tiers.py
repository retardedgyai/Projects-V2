"""Three authored short-blade silhouettes; T1, gear IDs and gameplay stay intact."""
import json
from PIL import Image, ImageDraw
from build_greatsword_tiers import ROOT, SOURCE, FIRST, convert_original
from build_pixel_armament_pack import geometry, pose, definition, write_json
from preview_class_armaments import render_model, FONT

PIXELS=SOURCE/'processed-dagger-tiers-v01'
OUT=ROOT/'.tools/dagger-tier-pack'
PACK=OUT/'pack'
ASSETS=PACK/'assets/projects'
JOBS={
    2:{'source':'sources/dagger-t2-v01.png',
       'sha256':'b4501df939aad279a895bac7209387b7d07eebf9e73aeaffa670c7f12a2585f2',
       'split_y':[850,1072],'jewel_box':[460,900,560,1020]},
    3:{'source':'sources/dagger-t3-v01.png',
       'sha256':'11f8db470e4a848bd0442f5516e50dc862e4c6639bd5be464758c224737ef978',
       'split_y':[920,1110],'jewel_box':[432,920,555,1040]},
    4:{'source':'sources/dagger-t4-v01.png',
       'sha256':'c48d1da7cbc7dd80d03fe1b461a5ea4b4d54ec5020c00107c282bd377f202a90',
       'split_y':[890,1140],'jewel_box':[460,890,560,1015]},
}


def tier_key(tier):
    if tier not in range(1,5): raise ValueError('Dagger Tier must be 1..4')
    return 'dagger' if tier==1 else f'dagger_t{tier}'


def convert(tier): return convert_original(JOBS[tier],60,30*60/92)


def model_data(tier):
    pixels,textures,entry=convert(tier)
    base,gem,_=geometry('dagger',entry,textures)
    key=tier_key(tier)
    base['textures']={part:f'projects:item/weapons/pixel_{key}_{part}' for part in textures}
    base['textures']['particle']=base['textures']['body']
    return pixels,textures,entry,base,gem


def build():
    PIXELS.mkdir(parents=True,exist_ok=True)
    manifest={'runtime_applied':False,'status':'Tier art review, not reference-quality approval','tiers':{}}
    first_entry=json.loads((FIRST/'manifest.json').read_text())['weapons']['dagger']
    base,gem,textures=geometry('dagger',first_entry)
    loaded={1:(base,gem,textures)}
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS dagger tiers / isolated review',
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
                write_json(ASSETS/f'models/item/weapons/pixel_{key}{suffix}.json',pose(base,gem,'dagger',stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        loaded[tier]=(base,gem,textures)
    (PIXELS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    sheet=Image.new('RGB',(1120,810),'#1b1e23')
    for row,yaw in enumerate((0,-35,180)):
        for column,(tier,(base,gem,textures)) in enumerate(loaded.items()):
            model=pose(base,gem,'dagger') if tier==1 else json.loads((ASSETS/f'models/item/weapons/pixel_{tier_key(tier)}.json').read_text())
            sheet.paste(render_model(model,textures,yaw=yaw,size=(280,240),scale=9.2),(280*column,270*row+30))
            ImageDraw.Draw(sheet).text((280*column+12,270*row+8),f'短剣 T{tier} / {yaw}°',font=FONT,fill='#dfd4c0')
    sheet.save(OUT/'tiers-review.png')
    print('Dagger T2-T4: 3 originals / 75 native poses; T1 unchanged; not running.')


if __name__=='__main__': build()
