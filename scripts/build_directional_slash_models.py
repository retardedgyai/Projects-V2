"""Reveal one fixed painted stroke through native UV-clipped geometry.

The old flipbook changed the entire crescent's direction between frames. Keep
one existing PNG byte-identical instead; the head advances, then the tail clears.
No new raster art, texture editing, camera billboard or rotating display plate.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack'
# (tail, head) along the authored stroke, left to right. Peak at frame 4.
WINDOWS=((0,.14),(0,.32),(0,.55),(0,.8),(0,1),(.12,1),(.3,1),
         (.52,1),(.72,1),(.87,1),(.96,1),(1,1))
COLORS=dict(steel=0xeaf4ff,gold=0xffd87b,astral=0xdca6ff,ice=0x85e2ff,
            fire=0xffab52,venom=0xb5ff5a,life=0xadffcb,shadow=0xe1b5ff,
            hunter=0xe8ffa3,holy=0xffedb0,lightning=0xb9dfff)


def mask_at(alpha,frame):
    yy,xx=np.nonzero(alpha)
    lo,hi=int(xx.min()),int(xx.max())+1
    tail,head=WINDOWS[frame]
    start=lo+round((hi-lo)*tail); end=lo+round((hi-lo)*head)
    mask=alpha.copy(); mask[:,:start]=False; mask[:,end:]=False
    return mask


def elements(mask,reverse=False):
    """Horizontal opaque runs; each exposes only its original texels, on both sides."""
    h,w=mask.shape
    result=[]
    for y,row in enumerate(mask):
        edges=np.diff(np.r_[False,row,False].astype(int))
        for x0,x1 in zip(np.flatnonzero(edges==1),np.flatnonzero(edges==-1)):
            u0,u1=x0*16/w,x1*16/w
            gx0,gx1=(16-u1,16-u0) if reverse else (u0,u1)
            uv0,uv1=(u1,u0) if reverse else (u0,u1)
            v0,v1=y*16/h,(y+1)*16/h
            result.append({'from':[gx0,8,16-v1],'to':[gx1,8,16-v0],'shade':False,
                'faces':{'up':{'texture':'#0','uv':[uv0,v1,uv1,v0],'tintindex':0},
                         'down':{'texture':'#0','uv':[uv0,v0,uv1,v1],'tintindex':0}}})
    return result


def build(assets,write):
    image=Image.open(assets/'textures/combat_vfx/ribbon/slash_5.png').convert('RGBA')
    alpha=np.asarray(image)[:,:,3]>0
    assert image.size==(64,64) and alpha.any()
    for frame in range(len(WINDOWS)):
        for reverse in (False,True):
            name=f'combat_vfx/stroke/cut_{"reverse_" if reverse else ""}{frame}'
            write(assets/f'models/{name}.json',{'ambientocclusion':False,
                'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                'elements':elements(mask_at(alpha,frame),reverse)})
            for palette,color in COLORS.items():
                key=f'combat_vfx/stroke/cut_{"reverse_" if reverse else ""}{palette}_{frame}'
                write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':f'projects:{name}',
                    'tints':[{'type':'minecraft:constant','value':color}]}})


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
