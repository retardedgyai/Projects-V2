"""Texture-first native flame mesh. Reads source art; never repaints the bitmap.

The generated source contains a baked checkerboard. Only warm painted pixels
become geometry, so no background card is ever shipped or drawn. Material colours
are sampled from its broad planes and applied to existing opaque ink texels.
"""
from functools import lru_cache
from pathlib import Path
import math
import numpy as np
from PIL import Image

SOURCE = Path(__file__).resolve().parents[1]/'assets/combat-vfx/mage-v2/sources/firebolt-v02.png'
FIRE_CLIPS = {'cinder', 'fire_stream', 'flame_hit', 'fire_charge'}


@lru_cache(maxsize=1)
def source():
    art = np.asarray(Image.open(SOURCE).convert('RGB')).astype(int)
    painted = (art[:,:,0]-art[:,:,2]>38) & (art[:,:,0]>85)
    ys,xs = np.nonzero(painted)
    left,right,top,bottom = xs.min(),xs.max()+1,ys.min(),ys.max()+1
    # Geometry sampling, not image resizing. A logical 64 x 28 silhouette retains
    # the large source clusters instead of tracing its high-resolution edge noise.
    px = np.minimum(right-1,(left+(np.arange(64)+.5)*(right-left)/64).astype(int))
    py = np.minimum(bottom-1,(top+(np.arange(28)+.5)*(bottom-top)/28).astype(int))
    rgb = art[py[:,None],px[None,:]]
    mask = painted[py[:,None],px[None,:]]
    groups = np.where(rgb[:,:,0]<180,0,np.where(rgb[:,:,1]<100,1,np.where(rgb[:,:,2]>140,3,2)))
    colours=[]
    for i in range(4):
        points=rgb[mask & (groups==i)]
        colour=np.median(points,axis=0).astype(int)
        colours.append(int(colour[0])<<16 | int(colour[1])<<8 | int(colour[2]))
    return np.where(mask,groups+1,0), colours


def palette():
    return source()[1]


def box(lo,hi,colour,uv,edge=None):
    faces={name:{'texture':'#0','uv':uv,'tintindex':colour,'shade':False}
           for name in ('up','down','north','south','east','west')}
    if edge is not None:
        faces['down']['tintindex']=edge
    return {'from':[round(float(v),5) for v in lo],'to':[round(float(v),5) for v in hi],
            'shade':False,'faces':faces}


def mesh(clip,frame,uv):
    count=18 if clip=='flame_hit' else 24
    if frame>=count-1:return []
    grid,_=source()
    t=frame/(count-1)
    elements=[]
    if clip=='fire_stream':
        # Three authored heat paths, each with different curvature, thickness,
        # arrival and detachment. This is a transient discharge, not a slow
        # projectile travelling after authoritative hits have already occurred.
        for strand,(width,phase,lifetime) in enumerate(((.82,0.,11),(.42,1.6,16),(.26,3.5,20))):
            for segment in range(40):
                u=(segment+.5)/40
                age=frame-u*2.2-strand*.6
                if age<0 or age>=lifetime:continue
                fade=(1-age/lifetime)**1.4
                if age>5 and segment%13 in (0,1,2):continue
                if age>9 and segment%13>7:continue
                y=8+math.sin(u*6+phase)*(.55+age*.035)+(strand-1)*.6
                x=8+math.sin(u*5+phase)*(.8+age*.065)
                w=width*fade*(.45+.55*math.sin(math.pi*u))
                colour=3 if age<3 and strand==0 else 2 if age<7 else 1
                elements.append(box((x-w,y-w,.10+segment/40*15.7),
                                    (x+w,y+w,.10+(segment+1)/40*15.7),colour,uv))
        return elements
    # The source is a side profile. Its longitudinal colour planes are sculpted
    # into thickness, not duplicated at right angles as intersecting PNGs.
    for row in range(28):
        y=(13.5-row)/28
        cells=[]
        for column in range(64):
            colour=int(grid[row,column])-1
            if colour<0:cells.append(None);continue
            u=(column+.5)/64
            if clip=='fire_charge':
                keep=u>.48 and abs(y)<(.22+.23*t)
                erosion=1.0
            elif clip=='flame_hit':
                # Compression opens along the original flow, then the three
                # unequal source lobes separate; never an empty-cast explosion.
                keep=u>max(0.,(t-.16)*1.12)
                erosion=max(0.,1-(t-.12)/.88)
            else:
                # The ignition core spends itself first; torn outer tongues
                # linger. The former left-to-right wipe left a stationary badge.
                lifetime=11 if u>.7 else 17 if y>0 else 22
                local_age=max(0.,frame-2*(1-u))
                keep=local_age<lifetime
                erosion=max(0.,1-local_age/lifetime)**.7
                # Two designed seams open after release; no random pixel noise.
                if t>.32 and .40+t*.13<u<.435+t*.13:keep=False
                if t>.56 and .67<u<.70:keep=False
            if not keep or erosion<=0:cells.append(None);continue
            depth=(.22+4.2*max(0.,1-((u-.78)/.31)**2)*max(0.,1-(y/.55)**2))
            depth=max(.15,round(depth*erosion*4)/4)
            cells.append((colour,depth))
        start=0
        while start<64:
            cell=cells[start];end=start+1
            while end<64 and cells[end]==cell:end+=1
            if cell is not None:
                colour,depth=cell
                u=(start+end)/128
                # Tail sections peel independently upward/outward, the hot head
                # stays on the accepted endpoint; no rotation of a complete card.
                peel=max(0.,t-.20)*(1-u)
                lobe=1 if y>0 else -1
                yy=8+y*12+peel*(3.1 if lobe>0 else -.9)+math.sin(u*5+frame*.12+lobe)*(.15+peel)
                xx=8+math.sin(u*4+frame*.10+lobe)*peel*1.3
                z0=.12+start/64*15.7;z1=.12+end/64*15.7
                if clip=='flame_hit':
                    xx+=math.copysign(peel*3.3,y)
                    yy+=y*t*5
                if clip=='fire_charge':
                    z0=8+(z0-12)*(.62+.25*t)
                    z1=8+(z1-12)*(.62+.25*t)
                e=box((xx-depth/2,yy-12/56,z0),(xx+depth/2,yy+12/56,z1),colour,uv,max(0,colour-1))
                # Back-facing cut surfaces show transmitted heat, not the dark
                # underside of the source repeated over the caster's entire view.
                e['faces']['north']['tintindex']=min(3,colour+1)
                elements.append(e)
            start=end
    return elements
