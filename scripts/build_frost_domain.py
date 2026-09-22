"""Absolute Zero's cutout planes; no UI regeneration and no running-pack mutation.

The imagegen master is preserved. Authorized checker cleanup, 96px resampling and
four-ink quantization produce the floor; a small native pixel ribbon/snowflake
extends those same material planes. Every exported stage has binary alpha.
"""
import hashlib
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'assets/combat-vfx/frost-domain-source-v1.png'
SOURCE_SHA='b951e927436532d1553cd52f0351204900ab1fa12f8a5a74217ffb68ac9626bd'
INKS=np.array([(47,126,185),(66,178,222),(125,220,243),(214,253,255)],dtype=np.int16)


def floor_art():
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==SOURCE_SHA
    raw=np.array(Image.open(SOURCE).convert('RGBA'))
    rgb=raw[:,:,:3].astype(np.int16)
    # Generated checker is gray, artwork is cyan. Keep existing alpha when present.
    mask=(rgb[:,:,2]-rgb[:,:,0]>15)&(rgb[:,:,1]-rgb[:,:,0]>12)&(raw[:,:,3]>127)
    cleaned=raw.copy();cleaned[:,:,3]=np.where(mask,255,0)
    tile=np.array(Image.fromarray(cleaned).resize((96,96),Image.Resampling.NEAREST))
    colors=tile[:,:,:3].astype(np.int16)
    distances=((colors[:,:,None,:].astype(float)-INKS[None,None,:,:])**2).sum(axis=3)
    tile[:,:,:3]=INKS[distances.argmin(axis=2)]
    yy,xx=np.indices((96,96))
    # A four-pixel gutter and a circular footprint ensure radius-scaled bounds.
    tile[:,:,3]=np.where(((xx-47.5)**2+(yy-47.5)**2<=44**2)&(tile[:,:,3]>127),255,0)
    tile[tile[:,:,3]==0]=0
    return Image.fromarray(tile)


def band_art():
    # One broad curved/tapered strip, not a solid wall or smoke texture.
    image=Image.new('RGBA',(48,16));d=ImageDraw.Draw(image)
    d.polygon([(2,11),(10,7),(19,4),(31,4),(40,2),(45,1),(42,5),(32,8),(21,8),(12,10),(5,13)],fill=tuple(INKS[0])+(255,))
    d.polygon([(3,11),(11,7),(20,4),(31,4),(41,2),(38,5),(29,6),(20,6),(11,9)],fill=tuple(INKS[1])+(255,))
    d.line([(4,10),(11,7),(20,4),(31,4),(41,2)],fill=tuple(INKS[2])+(255,),width=1)
    return image


def flake_art():
    image=Image.new('RGBA',(24,24));d=ImageDraw.Draw(image)
    shadow=tuple(INKS[1])+(255,);light=tuple(INKS[3])+(255,)
    for angle in range(0,360,60):
        a=math.radians(angle)
        def point(r,side=0):
            return (round(11.5+math.cos(a)*r-math.sin(a)*side),round(11.5+math.sin(a)*r+math.cos(a)*side))
        d.line([point(0),point(9)],fill=shadow,width=2)
        d.line([point(0),point(9)],fill=light,width=1)
        for side in (-1,1): d.line([point(6),point(3,side*3)],fill=light,width=1)
    return image


def erode(image,stage):
    a=np.array(image)
    yy,xx=np.indices(a.shape[:2])
    # Stable two-pixel cluster breakup, never randomized between exports/frames.
    order=((xx//2)*3+(yy//2)*5)%8
    a[order<stage]=0
    return Image.fromarray(a)


def plane(rotation=None):
    e={'from':[0,8,0],'to':[16,8,16],'shade':False,
       'faces':{'up':{'texture':'#0','uv':[0,16,16,0]},'down':{'texture':'#0','uv':[0,0,16,16]}}}
    if rotation: e['rotation']={'origin':[8,8,8],'axis':'x','angle':rotation,'rescale':False}
    return e


def build_frost_domain(assets,write_json):
    for name,image in [('floor',floor_art()),('band',band_art()),('flake',flake_art())]:
        for stage in range(8):
            key=f'combat_vfx/frost/{name}_{stage}'
            path=assets/f'textures/{key}.png';path.parent.mkdir(parents=True,exist_ok=True)
            erode(image,stage).save(path)
            elements=[plane(-45),plane(45)] if name=='flake' else [plane()]
            write_json(assets/f'models/{key}.json',{'ambientocclusion':False,'textures':{'0':f'projects:{key}'},'elements':elements})
            write_json(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':f'projects:{key}'}})


if __name__=='__main__':
    pack=ROOT/'server-minestom/src/main/resources/core-ui-pack'
    def write_json(path,data):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')
    build_frost_domain(pack/'assets/projects',write_json)
    (pack/'index.txt').write_text('\n'.join(sorted(p.relative_to(pack).as_posix() for p in pack.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
    print('24 frost models + items + textures; index refreshed. No other art rebuilt.')
