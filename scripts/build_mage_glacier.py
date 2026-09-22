"""Concrete wave / Zero solid ice forms using the painted crystal material."""
import numpy as np
from functools import lru_cache
from build_mage_garden import SHAPE,GRID,crystal,skin,textured_faces

GLACIER_CLIPS={'frost_wave','zero_crown','zero_wing','zero_shelf','zero_floor'}


def frozen_domain(frame,uv):
    """Three advancing broken shelves join the corresponding pillar roots.

    Large gaps remain between the branches and around the owner, rather than
    an opaque circular rug or isolated debug lines. No whole-body reset.
    """
    x,y,z=np.moveaxis(GRID,-1,0);y=y-8
    volume=np.zeros(SHAPE,dtype=np.uint8)
    paths=(((8,8),(5.4,8.7),(2.0,9.8)),
           ((8.2,8),(10.3,10.5),(13.4,12.2)),
           ((8,7.7),(7.4,4.7),(6.8,2.2)))
    for branch,path in enumerate(paths):
        for segment,(a,b) in enumerate(zip(path,path[1:])):
            ax,az=a;bx,bz=b;dx,dz=bx-ax,bz-az
            u=np.clip(((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz),0,1)
            distance=np.sqrt((x-ax-u*dx)**2+(z-az-u*dz)**2)
            head=np.clip((frame-branch*2)/12,0,1)*2
            spread=.30+.85*u+.16*np.sin((x+z)*1.3+branch)
            decay=max(0.,(frame-35)/11)
            height=.3+u*.68+segment*.3
            crack=np.abs(z-(.5*x+branch*1.6+2))
            solid=(distance<spread*(1-decay))&(u+segment<head)&(y<height*(1-decay))&(y>=0)&(crack>.12+decay*.3)
            # A few tapered root slabs, not repeated floating tiles.
            ink=np.where(distance<spread*.65,3,1)
            ink=np.where((distance>spread*.85)&(x<8),4,ink)
            volume[solid]=(ink[solid]+1).astype(np.uint8)
    return textured_faces(skin(volume,uv),'garden_bed')


@lru_cache(maxsize=192)
def geometry(clip,frame,uv_tuple):
    uv=list(uv_tuple)
    frames=30 if clip=='frost_wave' else 48
    if frame>=frames-1:return []
    if clip=='zero_floor':return frozen_domain(frame,uv)
    # A wave spends its mass as it travels. Zero holds before breaking.
    clock=frame*47/29 if clip=='frost_wave' else frame
    decay=32+max(0,frame-6)*14/22 if clip=='frost_wave' else clock
    v=np.zeros(SHAPE,dtype=np.uint8)
    if clip=='zero_crown':
        # Three widely separated long needles, rather than an opaque pyramid.
        specs=((4,8,11,1.35,1.25,-.8,.6,0),
               (9,10,8.2,1.1,1.5,1.2,1.2,1),
               (13,6,5.4,1.5,1.1,2.1,-1.4,2))
    elif clip=='zero_wing':
        specs=((6,9,12,2.0,1.3,-3,-2,0),
               (11,7,8,1.2,1.0,2.8,1.2,1))
    elif clip=='zero_shelf':
        specs=((5,8,4.5,2.5,2,-3.1,-1.1,0),
               (9,9,6,1.2,1.4,2.7,2.5,1),
               (12,6,3.2,2.1,1.8,2,-2.3,2))
    else:
        # Low broad forward-breaking blades, not a standing garden translated.
        specs=((4.5,6,6,2.9,2,1,4.5,0),
               (9,8,9,2.3,1.8,-1.6,5,1),
               (13,6,4.8,1.7,1.6,.6,3.7,2))
    for i,(x,z,h,w,d,lx,lz,variant) in enumerate(specs):
        crystal(v,x,z,h,w,d,lx,lz,clock-i,variant,decay)
    return textured_faces(skin(v,uv),clip)


def mesh(clip,frame,uv):return geometry(clip,frame,tuple(uv))
