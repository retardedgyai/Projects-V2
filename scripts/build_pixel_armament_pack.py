"""Three texture-shaped weapons, isolated from the running server's resource pack.

Uses the user-approved processed PNGs. Native faces follow alpha contours, not
filled voxel cubes. Each gemstone is a separate texture/layer with its own depth;
the staff's negative space remains genuinely empty when its stone moves.
"""
import json
import math
import shutil
import zipfile
from copy import deepcopy
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from build_texture_first_sword import compile_model
from preview_class_armaments import render_model, FONT

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'assets/class-armaments/texture-first/processed-v01'
OUT=ROOT/'.tools/pixel-armament-pack'
PACK=OUT/'pack'
ASSETS=PACK/'assets/projects'
SHAPES={
    'greatsword':(('blade','guard','grip'),(.14,.5,.9),.45),
    'dagger':(('blade','guard','grip'),(.14,.45,.85),.4),
    'staff':(('fins','shaft','ferrule'),(.18,.7,.5),.65),
}


def geometry(key,entry):
    body=np.asarray(Image.open(SOURCE/f'{key}-body.png'))
    jewel=np.asarray(Image.open(SOURCE/f'{key}-jewel.png'))
    ys,xs=np.nonzero(body[:,:,3]>0)
    grip_x=xs[ys>entry['rows'][-2]]
    pivot=float(np.median(grip_x))+.5
    names,thickness,jewel_depth=SHAPES[key]
    spec={'height':entry['height'],'top_pixel':entry['rows'][0],
          'bottom_pixel':entry['rows'][-1],'pivot_pixel_x':pivot,'alpha_cutoff':128,
          'texture':f'projects:item/weapons/pixel_{key}_body',
          'parts':[{'name':name,'rows':entry['rows'][i:i+2],'thickness':depth}
                   for i,(name,depth) in enumerate(zip(names,thickness))]}
    base,_=compile_model(spec,body[:,:,3])
    gem_spec={**spec,'texture':f'projects:item/weapons/pixel_{key}_jewel',
        'parts':[{'name':'jewel','rows':[spec['top_pixel'],spec['bottom_pixel']],
                  'thickness':jewel_depth}]}
    gem,_=compile_model(gem_spec,jewel[:,:,3])
    # Disjoint texture bindings allow a real socket/empty gap, no duplicate stone.
    base['textures']={'body':spec['texture'],'jewel':gem_spec['texture'],'particle':spec['texture']}
    for e in base['elements']:
        for face in e['faces'].values(): face['texture']='#body'
    for e in gem['elements']:
        for face in e['faces'].values(): face['texture']='#jewel'
    for prefix in ('firstperson','thirdperson'):
        left=deepcopy(base['display'][f'{prefix}_righthand'])
        left['rotation'][1]*=-1; left['rotation'][2]*=-1
        base['display'][f'{prefix}_lefthand']=left
    base['display']['fixed']={'rotation':[0,180,0],'translation':[0,-3,0],'scale':[.45]*3}
    base['credit']='ProjectS texture-led pixel armament; isolated art review, no client mod'
    return base,gem,{'body':body,'jewel':jewel}


def pose(base,gem,key,stage='rest',frame=0):
    if stage not in ('rest','idle','prepare','release'): raise ValueError('Unknown stage')
    limit=12 if stage=='idle' else 6 if stage!='rest' else 1
    if frame not in range(limit): raise ValueError('Invalid frame')
    dy=0; dz=0 if key=='staff' else -.38
    if stage=='idle':
        phase=math.sin(frame*math.tau/12)
        if key=='staff': dy=.22*phase
        else: dz-=.06*(1-math.cos(frame*math.tau/12))
    elif stage=='prepare':
        dz-=.3*frame/5
        if key=='staff': dy=.35*frame/5
    elif stage=='release':
        dz-=.5*(1-frame/5)
        if key=='staff': dy=.35*(1-frame/5)
    model=deepcopy(base)
    for e in deepcopy(gem['elements']):
        for edge in ('from','to'):
            e[edge][1]=round(e[edge][1]+dy,6)
            e[edge][2]=round(e[edge][2]+dz,6)
        model['elements'].append(e)
    return model


def definition(key):
    model_key=f'projects:item/weapons/pixel_{key}'
    def model(suffix=''): return {'type':'minecraft:model','model':model_key+suffix}
    entries=[]
    for offset,stage,frames in ((0,'idle',12),(12,'prepare',6),(18,'release',6)):
        entries.extend({'threshold':offset+i,'model':model(f'_{stage}{i:02d}')} for i in range(frames))
    entries.append({'threshold':24,'model':model()})
    return {'hand_animation_on_swap':False,'model':{'type':'minecraft:select',
        'property':'minecraft:display_context','fallback':model(),
        'cases':[{'when':['firstperson_righthand','firstperson_lefthand',
                           'thirdperson_righthand','thirdperson_lefthand'],
                  'model':{'type':'minecraft:range_dispatch','property':'minecraft:custom_model_data',
                           'index':0,'fallback':model(),'entries':entries}}]}}


def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')


def build():
    manifest=json.loads((SOURCE/'manifest.json').read_text())
    # Same format as the existing Vanilla 26.2 pack, but no accepted UI/font assets.
    write_json(PACK/'pack.mcmeta',{'pack':{'description':'ProjectS pixel weapons / isolated review',
                                         'min_format':[88,0],'max_format':[88,0]}})
    sheet=Image.new('RGB',(320*4,380*3),'#1b1e23'); report={}; action_frames=[]
    loaded={}
    for row,(key,entry) in enumerate(manifest['weapons'].items()):
        base,gem,textures=geometry(key,entry)
        for part in ('body','jewel'):
            target=ASSETS/f'textures/item/weapons/pixel_{key}_{part}.png'
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(SOURCE/f'{key}-{part}.png',target)
            write_json(target.with_suffix('.png.mcmeta'),{'texture':{'blur':False,'clamp':False}})
        models_dir=ASSETS/'models/item/weapons'
        write_json(models_dir/f'pixel_{key}.json',pose(base,gem,key))
        for stage,count in (('idle',12),('prepare',6),('release',6)):
            for frame in range(count):
                write_json(models_dir/f'pixel_{key}_{stage}{frame:02d}.json',pose(base,gem,key,stage,frame))
        write_json(ASSETS/f'items/weapons/pixel_{key}.json',definition(key))
        saved=json.loads((models_dir/f'pixel_{key}.json').read_text())
        loaded[key]=textures
        report[key]={'elements':len(saved['elements']),'body_elements':len(base['elements']),
                     'jewel_elements':len(gem['elements']),'model_bytes':(models_dir/f'pixel_{key}.json').stat().st_size,
                     'texture_size':list(Image.open(SOURCE/f'{key}.png').size),'poses':24}
        for col,yaw in enumerate((0,-35,90,180)):
            rendered=render_model(saved,textures,yaw=yaw,size=(320,350),scale=9.2)
            sheet.paste(rendered,(col*320,row*380+30))
            ImageDraw.Draw(sheet).text((col*320+12,row*380+8),f'{key} / {yaw}°',font=FONT,fill='#dfd4c0')
    for stage in ('prepare','release'):
        for frame in range(6):
            strip=Image.new('RGB',(960,380),'#1b1e23')
            for col,key in enumerate(manifest['weapons']):
                saved=json.loads((ASSETS/f'models/item/weapons/pixel_{key}_{stage}{frame:02d}.json').read_text())
                strip.paste(render_model(saved,loaded[key],yaw=-35,size=(320,350),scale=9.2),(320*col,30))
                ImageDraw.Draw(strip).text((320*col+12,8),f'{key} / {stage} {frame}',font=FONT,fill='#dfd4c0')
            action_frames.append(strip)
    sheet.save(OUT/'model-review.png')
    action_frames[0].save(OUT/'actions.gif',save_all=True,append_images=action_frames[1:],duration=100,loop=0)
    with zipfile.ZipFile(OUT/'projects-pixel-armaments-review.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(PACK.rglob('*')):
            if path.is_file(): archive.write(path,path.relative_to(PACK).as_posix())
    write_json(OUT/'report.json',{'runtime_applied':False,'weapons':report})
    print(json.dumps(report))


if __name__=='__main__': build()
