"""Worn-layout QA from shipped helmet JSON + native armor textures, not Minecraft.

Neutral mannequin and standard inflated humanoid cubes approximate the player.
Uses real pixel UVs; does not claim to verify actual client posing/attachment.
"""
from copy import deepcopy
import json
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from build_class_armor_assets import ASSETS,JOBS
from preview_class_armaments import render_model,FONT

OUT=Path(__file__).resolve().parents[1]/'.tools/armor-review'
LABELS=('戦士','メイジ','レンジャー','アサシン','テンプラー','ヒーラー','星織り師')


def cube(name,lo,hi,tex,uv,size,inflate=0):
    u,v=uv; w,h,d=size
    rectangles={'west':(u,v+d,d,h),'north':(u+d,v+d,w,h),'east':(u+d+w,v+d,d,h),
        'south':(u+2*d+w,v+d,w,h),'up':(u+d,v,w,d),'down':(u+d+w,v,w,d)}
    return {'name':name,'from':[x-inflate for x in lo],'to':[x+inflate for x in hi],
        'faces':{face:{'texture':'#'+tex,'uv':[x/4,y/2,(x+width)/4,(y+height)/2]} for face,(x,y,width,height) in rectangles.items()}}


def worn(job,tier):
    helmet=json.loads((ASSETS/f'models/item/armor/{job}_t{tier}_helmet.json').read_text())
    elements=deepcopy(helmet['elements'])
    for e in elements:
        for key in ('from','to'): e[key][1]+=20
        if 'rotation' in e: e['rotation']['origin'][1]+=20
    textures={
        'atlas':np.array(Image.open(ASSETS/'textures/item/weapons/materials.png').convert('RGBA')),
        'outer':np.array(Image.open(ASSETS/f'textures/entity/equipment/humanoid/armor/{job}_t{tier}.png').convert('RGBA')),
        'inner':np.array(Image.open(ASSETS/f'textures/entity/equipment/humanoid_leggings/armor/{job}_t{tier}.png').convert('RGBA')),
        'skin':np.full((32,64,4),[145,131,116,255],dtype=np.uint8),
    }
    elements.append(cube('neutral head',[4,24,4],[12,32,12],'skin',(0,0),(8,8,8)))
    for name,lo,hi,uv,size in (
        ('body',[4,12,6],[12,24,10],(16,16),(8,12,4)),
        ('right arm',[0,12,6],[4,24,10],(40,16),(4,12,4)),
        ('left arm',[12,12,6],[16,24,10],(40,16),(4,12,4))):
        elements.append(cube(name+' skin',lo,hi,'skin',uv,size))
        elements.append(cube(name+' armor',lo,hi,'outer',uv,size,1))
    for x in (4,8):
        elements.append(cube('leggings',[x,0,6],[x+4,12,10],'inner',(0,16),(4,12,4),.5))
        elements.append(cube('boots',[x,0,6],[x+4,12,10],'outer',(0,16),(4,12,4),1))
    return {'elements':elements},textures


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    width,height=224,438
    sheet=Image.new('RGB',(width*7,height*4+30),'#171b20')
    ImageDraw.Draw(sheet).text((10,5),'防具の実データ＋標準人型の装着配置確認（Minecraft画面ではありません）',font=FONT,fill='#d5cec0')
    for tier in range(1,5):
        for i,job in enumerate(JOBS):
            model,textures=worn(job,tier); image=render_model(model,textures,size=(width,height),scale=8.4)
            ImageDraw.Draw(image).text((10,8),f'{LABELS[i]} / T{tier}',font=FONT,fill='#ece3cd')
            sheet.paste(image,(i*width,(tier-1)*height+30))
    sheet.save(OUT/'all-sets.png')
    print(OUT/'all-sets.png')


if __name__=='__main__': main()
