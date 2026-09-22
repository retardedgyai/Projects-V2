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
from build_mage_uv import painted

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


def palette():return source()[1]+[0x584D6C,0x85758B,0xAE8399,0xC99CAD,0xDFB1B9,0xEFD1CF,0xFFF2DE,0xFFFFFF]


def legacy_geometry(clip,frame,uv):
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


@lru_cache(maxsize=168)
def solid_geometry(clip,frame,uv_tuple):
    # Concrete volumes for the three fire phrases. Not the former extrusion
    # of a single illustration; all cameras see shaped mass and open clefts.
    from build_mage_garden import GRID,SHAPE,skin
    uv=list(uv_tuple)
    frames=48 if clip=='pyre' else 24
    if frame>=frames-1:return []
    x,y,z=np.moveaxis(GRID,-1,0)
    v=np.zeros(SHAPE,dtype=np.uint8)
    if clip in ('meteor','pyre'):
        t=frame/(frames-1)
        xx=x-8;yy=y-15;zz=z-8
        split=max(0.,t-.5)*2 if clip=='pyre' else 0
        # Three unequal hot lobes separate along their existing molten seams.
        for i,(cx,cy,cz,r) in enumerate(((-1.3,0,-.4,3.9),(2,1.1,.6,2.6),(.6,-2,1.8,2.5))):
            dx=xx-cx*(1+split*.7);dy=yy-cy*(1+split*.8);dz=zz-cz*(1+split)
            metric=np.maximum.reduce((np.abs(dx)*.75,np.abs(dy)*.85,np.abs(dz),
                                      (np.abs(dx)+np.abs(dy)+np.abs(dz))*.46))
            radius=r*(1-max(0.,t-.80)*4.8) if clip=='pyre' else r
            solid=metric<radius
            fault=np.abs(dx*.5+dy*.7-dz-.25)
            ink=np.where(fault<.5,7,np.where(dy>r*.25,6,np.where(dz<-.3,5,3)))
            if clip=='meteor':ink=np.where(fault<.65,7,np.where(dz<-.3,1,0))
            v[solid]=(ink[solid]+1).astype(np.uint8)
        out=skin(v,uv)
        for e in out:
            e['from'][1]-=7;e['to'][1]-=7
        if clip=='meteor':
            # Rock faces stay broad and dark; only molten faults are textured.
            result=[]
            for e in out:
                ink=next(iter(e['faces'].values()))['tintindex']
                result.extend(painted([e],'y',8) if ink>=5 else [e])
            return result
        return painted(out,'y',8)
    # Eruption and downward flare each have three differently timed tongues,
    # a lower pressure mass and detached hot fragments. Not five copies of one
    # repeated short explosion.
    for i,(cx,cz,h,w,lx,lz) in enumerate(((6.5,7.0,12,2.4,-3,-1),
                                       (10,9,8,1.8,2,1.8),(7,11,5.5,1.6,-1,2))):
        age=frame-i*1.5
        if not 0<=age<20:continue
        grow=min(1.,.22+age/3);fade=max(0.,(age-8)/12)
        u=(y-8)/max(.01,h*grow)
        xx=x-cx-lx*u-fade*lx*.6;zz=z-cz-lz*u
        width=w*np.maximum(0.,1-u)**.8*(1-fade*.75)
        solid=(u>=fade*.55)&(u<1)&(np.abs(xx)<width)&(np.abs(zz)<width*.65)&((abs(xx)+abs(zz))<width*1.3)
        ink=np.where(xx<-.2,5,np.where(zz<0,7,4))
        v[solid]=(ink[solid]+1).astype(np.uint8)
    out=skin(v,uv)
    if clip=='solar_flare':
        for e in out:
            e['from'][1],e['to'][1]=28-e['to'][1],28-e['from'][1]
            faces=e['faces']
            if 'up' in faces:faces['down']=faces.pop('up')
            elif 'down' in faces:faces['up']=faces.pop('down')
    return painted(out,'y',8)


def mesh(clip,frame,uv):
    return legacy_geometry(clip,frame,uv) if clip=='corona' else solid_geometry(clip,frame,tuple(uv))
