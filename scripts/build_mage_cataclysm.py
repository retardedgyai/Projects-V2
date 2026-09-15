"""Meteor and heavenly fire: painted rock, rupture, suspended molten core.

The source is read unchanged. Small source colour variations are reduced to broad
native material planes. Model geometry carries the breakup, never a spinning PNG.
"""
from functools import lru_cache
from pathlib import Path
import math
import numpy as np
from PIL import Image
from build_mage_fire import box

SOURCE=Path(__file__).resolve().parents[1]/'assets/combat-vfx/mage-v2/sources/meteor-eruption-v01.png'
CATACLYSM_CLIPS={'meteor','eruption','pyre','corona','solar_flare'}


@lru_cache(maxsize=1)
def source():
    art=np.asarray(Image.open(SOURCE).convert('RGB')).astype(int)
    x=np.arange(art.shape[1])[None,:]
    mask=((art[:,:,0]-art[:,:,2]>48)&(art[:,:,0]>85))|((art.max(axis=2)<155)&(x<art.shape[1]*.49))
    prototypes=np.array(((41,41,56),(86,83,99),(87,39,62),(150,26,60),
                         (229,39,35),(255,116,25),(255,179,57),(255,237,168)))
    groups=np.argmin(np.sum((art[:,:,None,:]-prototypes)**2,axis=3),axis=2)
    colours=[]
    for i in range(8):
        rgb=np.median(art[mask&(groups==i)],axis=0).astype(int)
        colours.append(int(rgb[0])<<16|int(rgb[1])<<8|int(rgb[2]))
    forms=[]
    for left,right in ((0,.49),(.52,1.)):
        a,b=int(left*art.shape[1]),int(right*art.shape[1]);ys,xs=np.nonzero(mask[:,a:b]);xs+=a
        lo,hi,top,bottom=xs.min(),xs.max()+1,ys.min(),ys.max()+1
        px=(lo+(np.arange(48)+.5)*(hi-lo)/48).astype(int)
        py=(top+(np.arange(48)+.5)*(bottom-top)/48).astype(int)
        forms.append(np.where(mask[py[:,None],px[None,:]],groups[py[:,None],px[None,:]]+1,0))
    return forms,colours


def palette():return source()[1]


def mesh(clip,frame,uv):
    frames=48 if clip in ('pyre','corona') else 24
    if frame>=frames-1:return []
    t=frame/(frames-1)
    if clip=='corona':
        # Heat under the floating core cracks into three unequal expanding runs.
        out=[]
        for i,(a,span) in enumerate(((.2,1.1),(2.45,.75),(4.6,.9))):
            for j in range(18):
                u=j/17
                if t>.7 and j%5 in (0,1):continue
                angle=a+u*span;r=2.2+3.2*(1-math.exp(-frame/5))+.2*math.sin(u*5)
                w=.17*max(0.,1-max(0.,(t-.65)/.35))
                if w<.015:continue
                x,z=8+math.sin(angle)*r,8+math.cos(angle)*r
                out.append(box((x-w,8,z-w),(x+w,8.10,z+w),5 if frame>5 else 7,uv))
        return out
    rock=clip in ('meteor','pyre')
    grid=source()[0][0 if rock else 1]
    out=[]
    for row in range(48):
        h=1-(row+.5)/48
        if not rock and h>min(1.,.32+frame/4):continue
        cells=[]
        for col in range(48):
            ink=int(grid[row,col])-1;u=(col+.5)/48
            if ink<0:cells.append(None);continue
            if rock:
                depth=round((3.0+4.5*max(0.,1-abs(u-.5)*2))*2)/2
                if clip=='pyre':
                    # Open the stone's hot fault lines, exposing one burning
                    # interior. Four pieces progressively separate, never reset.
                    if t>.4 and (col+row//2)%19<int((t-.4)*8):cells.append(None);continue
                    if t>.8 and (col*3+row)%23<int((t-.8)*50):cells.append(None);continue
                    ink=(4,5,3,4,5,6,6,7)[ink]
            else:
                depth=.9 if h>.35 else 2.3
                if t>.4 and h<(t-.4)*.8:cells.append(None);continue
                if t>.65 and (row+col//3)%17<int((t-.65)*25):cells.append(None);continue
            cells.append((ink,depth))
        col=0
        while col<48:
            cell=cells[col];end=col+1
            while end<48 and cells[end]==cell:end+=1
            if cell is not None:
                ink,depth=cell;u=(col+end)/96
                x0=1+col/48*14;x1=1+end/48*14
                y0=1+(47-row)/48*14;y1=y0+14/48
                z=8.0
                if clip=='pyre':
                    drift=max(0.,t-.22)*3.2
                    x0+=math.copysign(drift,u-.5);x1+=math.copysign(drift,u-.5)
                    y0+=math.copysign(drift*.6,h-.5);y1+=math.copysign(drift*.6,h-.5)
                elif not rock:
                    shift=max(0.,t-.18)*(1.4 if u<.5 else -1.1)*h
                    x0+=shift;x1+=shift
                    rise=t*(1.2 if u<.5 else 2.2)
                    y0+=7+rise;y1+=7+rise
                    if clip=='solar_flare':
                        y0,y1=32-y1,32-y0
                        x0,x1=8+(x0-8)*.52,8+(x1-8)*.52
                e=box((x0,y0,z-depth/2),(x1,y1,z+depth/2),ink,uv,max(0,ink-2))
                if rock:
                    e['faces']['east']['tintindex']=1 if clip=='meteor' else 5
                    e['faces']['west']['tintindex']=0 if clip=='meteor' else 4
                out.append(e)
            col=end
    return out
