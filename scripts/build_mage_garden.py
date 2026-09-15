"""Garden-only solid ice sculpture; no extruded illustration or shared Mage rewrite.

Intersected crystal planes define volume and colour together. Exposed surfaces
are greedily merged into native Minecraft faces at an intentional pixel scale.
No hidden cube interiors, texture cards, random voxel noise or baked lighting.
"""
import math
from functools import lru_cache
import numpy as np
from build_mage_fire import box

GARDEN_CLIPS={'garden_spires','garden_fan','garden_bed','garden_spray','garden_beat','garden_charge'}
PALETTE=[0x233E73,0x285F99,0x278EBA,0x46BED4,0x83DBE5,0xC4F2F1]
STEP=.5
SHAPE=(40,48,36)
ORIGIN=np.array((-2.,8.,-1.))
GRID=np.stack(np.meshgrid(*(np.arange(n)*STEP+ORIGIN[i]+STEP/2 for i,n in enumerate(SHAPE)),indexing='ij'),axis=-1)


def crystal(volume,cx,cz,height,width,depth,lean_x,lean_z,age,variant=0,decay_age=None):
    if age<0:return
    # Grow the whole solid from its rooted base, not a horizontal image wipe.
    growth=min(1.,.13+age/6.)
    fall=max(0.,((age if decay_age is None else decay_age)-32)/14.)
    x,y,z=np.moveaxis(GRID,-1,0);y=y-8
    h=height*growth
    # Two genuinely detached oblique chunks, translating rather than shrinking.
    split=(y/height-.48-.22*(x-cx)/max(width,1.))
    upper=split>0
    shift=fall*fall
    x=x-np.where(upper,shift*(1.8 if lean_x>=0 else -1.8),0)
    z=z-np.where(upper,shift*lean_z*2,0)
    y=y+np.where(upper,shift*1.8,fall*.3)+fall**3*(height+3)
    u=y/max(h,.01)
    q=(x-cx-lean_x*u)/width
    r=(z-cz-lean_z*u)/depth
    # Long shaft, beveled cross-section, unequal chisel point. Not a pyramid.
    taper=np.minimum(.72+u*.52,np.maximum(0.,(1.035-u)/.57))
    taper=np.maximum(.015,taper)
    distances=np.stack((np.abs(q),np.abs(r),(np.abs(q)+np.abs(r))*.60))
    solid=(np.max(distances,axis=0)<taper)&(y>=0)&(u+.17*q-.06*r<1.)
    # A chipped shoulder interrupts the second blade's silhouette. The main
    # shaft has a long cutting edge rather than a cap on a regular gem column.
    if variant==1:solid&=~((u>.48)&(u<.63)&(q>taper*.3)&(r<-.08))
    if fall>0:
        crack=y/max(height,.01)-.48-.22*(x-cx)/max(width,1.)
        solid&=np.abs(crack)>.025+fall*.045
        if fall>.68:solid&=(u<1-(fall-.68)*1.5)
    # Palette belongs to the actual bevel planes. Wide quiet faces, one narrow
    # ice ridge and a dark root, never a flat illustration mapped onto a prism.
    ink=np.where(r<-.22,2,np.where(q<0,3,1))
    bevel=(np.abs(q)+np.abs(r))*.60>=np.maximum(np.abs(q),np.abs(r))-.025
    ink=np.where(bevel&(r<0)&(q<0),4,ink)
    ink=np.where(bevel&(r<0)&(q<0)&(u>.62),5,ink)
    ink=np.where(u>.96,5,ink)
    ink=np.where(u<.10,0,ink)
    # One oblique, localized stress seam, with a brief travelling highlight.
    seam=np.abs(u-.48-.22*q)
    if age>13:ink=np.where((seam<.018)&(r<0),1,ink)
    if 14<=age<=26:
        glint=(np.abs(u-(age-14)/12)<.035)&bevel&(q<0)&(r<0)
        ink=np.where(glint,5,ink)
    volume[solid]=(ink[solid]+1).astype(np.uint8)


def rectangles(source):
    mask=source.copy();out=[]
    for a in range(mask.shape[0]):
        b=0
        while b<mask.shape[1]:
            color=int(mask[a,b])
            if not color:b+=1;continue
            end=b+1
            while end<mask.shape[1] and mask[a,end]==color:end+=1
            stop=a+1
            while stop<mask.shape[0] and np.all(mask[stop,b:end]==color):stop+=1
            out.append((a,b,stop,end,color));mask[a:stop,b:end]=0;b=end
    return out


def skin(volume,uv):
    """Merge exposed same-material grid faces; all interior faces are absent."""
    out=[]
    for axis in range(3):
        other=[a for a in range(3) if a!=axis]
        for sign in (-1,1):
            neighbor=np.roll(volume,-sign,axis=axis)
            edge=[slice(None)]*3;edge[axis]=-1 if sign==1 else 0
            neighbor[tuple(edge)]=0
            exposed=np.where((volume>0)&(neighbor==0),volume,0)
            name=(('west','east'),('down','up'),('north','south'))[axis][sign==1]
            for layer in range(SHAPE[axis]):
                mask=np.take(exposed,layer,axis=axis)
                normal=rectangles(mask)
                alternate=[(b,a,end,stop,c) for a,b,stop,end,c in rectangles(mask.T)]
                for a,b,stop,end,color in min((normal,alternate),key=len):
                    lo=ORIGIN.copy();hi=ORIGIN.copy()
                    lo[axis]+=STEP*(layer+(sign==1));hi[axis]=lo[axis]+.002
                    lo[other[0]]+=a*STEP;hi[other[0]]+=stop*STEP
                    lo[other[1]]+=b*STEP;hi[other[1]]+=end*STEP
                    # Model faces point OUTWARD; thin backing stays inside.
                    if sign==1:lo[axis]-=.002;hi[axis]-=.002
                    e=box(lo,hi,color-1,uv);e['faces']={name:e['faces'][name]}
                    out.append(e)
    return out


def bed(volume,age):
    x,y,z=np.moveaxis(GRID,-1,0);x=x-8;z=z-8;y=y-8
    spread=min(1.,.18+age/7.)
    fade=max(0.,(age-35)/12)
    # A broad, broken pressure shelf, with real thickness under the crystals.
    outline=[(-7,-3.5),(-2,-5.2),(3,-4.8),(7,-1.8),(5.6,2.9),(1.2,5.5),(-3.2,4.2),(-7.5,1.1)]
    margins=[]
    for (ax,az),(bx,bz) in zip(outline,outline[1:]+outline[:1]):
        dx,dz=bx-ax,bz-az
        margins.append((dx*(z-az*spread)-dz*(x-ax*spread))/math.hypot(dx,dz))
    boundary=np.min(margins,axis=0)
    edge_id=np.argmin(margins,axis=0)
    top=(np.where(z>.43*x+1,1.5,np.where(z<-1.05*x-2.1,1.05,.65))+.045*x)*(1-fade)
    crack=np.minimum(np.abs(z-.43*x-1),np.abs(z+1.05*x+2.1))
    solid=(boundary>=0)&(y<top)&(y>=0)&(crack>.13+fade*.7)
    solid&=~((boundary<1.5)&(fade>.3)&(z-x>3-fade*8))
    ink=np.where(y>.7,2,1)
    ink=np.where((boundary<.5)&(y>.4)&np.isin(edge_id,(0,3,6)),4,ink)
    ink=np.where(crack<.7,2,ink)
    volume[solid]=(ink[solid]+1).astype(np.uint8)


def pressure_beat(frame,uv):
    # Authored over the same cracks as the bed, not a floating symbol. One
    # short front travels into the root and climbs its exposed bevel.
    t=frame/23
    paths=[[(8,8.28,4),(7,8.35,7.55),(5.38,8.5,9.45),(4.7,25,9.6)],
           [(7,8.35,7.55),(9.5,8.4,10),(11,8.5,11.2)]]
    out=[]
    for branch,path in enumerate(paths):
        count=30 if branch==0 else 16
        for i in range(count):
            progress=i/(count-1)
            # Floor propagation occupies the first half; the bright thin root
            # vein follows, leaving the sculpture itself stationary.
            head=t*1.5-branch*.16
            trail=head-progress
            if not 0<=trail<.24:continue
            seg=min(len(path)-2,int(progress*(len(path)-1)))
            local=progress*(len(path)-1)-seg
            a=np.array(path[seg]);b=np.array(path[seg+1]);p=a+(b-a)*local
            step=(b-a)*(len(path)-1)/count
            width=.10 if trail>.12 else .16
            lo=np.minimum(p,p+step)-width;hi=np.maximum(p,p+step)+width
            out.append(box(lo,hi,5 if trail<.06 else 3 if trail<.14 else 2,uv))
    return out


def flecks(frame,uv,pulse=False):
    out=[]
    # Deliberate shard trajectories: outward launch, gravity, narrow chipped
    # silhouettes. Not vanilla dust or identical sparkles orbiting a ring.
    for i in range(11 if not pulse else 7):
        age=frame-(i%4 if pulse else 4+i%6)
        life=12 if pulse else 22
        if not 0<=age<life:continue
        a=i*2.39996+.3;u=age/life
        distance=(1.4+u*5.6)*(1 if pulse else .85)
        x=8+math.cos(a)*distance;z=8+math.sin(a)*distance
        y=8.5+math.sin(math.pi*u)*(2 if pulse else 4.4)
        w=(.32 if i%3 else .48)*(1-u*.75)
        e=box((x-w,y,z-w*.65),(x+w,y+w*2.4,z+w*.65),4 if i%3 else 5,uv,2)
        e['rotation']={'origin':[x,y,z],'axis':'z','angle':22.5 if i%2 else -45,'rescale':False}
        out.append(e)
    return out


@lru_cache(maxsize=160)
def geometry(clip,frame,uv_tuple):
    uv=list(uv_tuple)
    count=24 if clip in ('garden_beat','garden_charge') else 48
    if frame>=count-1:return []
    if clip=='garden_spray':return flecks(frame,uv)
    if clip=='garden_beat':return pressure_beat(frame,uv)
    v=np.zeros(SHAPE,dtype=np.uint8)
    if clip in ('garden_bed','garden_charge'):
        bed(v,frame if clip=='garden_bed' else frame*.22)
    elif clip=='garden_spires':
        crystal(v,7.1,8.0,20,2.4,2.1,-2.7,.8,frame)
        crystal(v,10,9.1,12,2.1,1.7,3.,1.9,frame-2,1,frame)
        crystal(v,5.4,6.8,7,1.5,1.5,-2.,-1.6,frame-4,2,frame)
    elif clip=='garden_fan':
        crystal(v,7,8,11,2.3,1.9,-4.2,-.6,frame)
        crystal(v,10,8.5,8,1.9,1.7,3.8,2,frame-2,1,frame)
        crystal(v,8,6.2,5,1.5,1.3,.5,-2.4,frame-3,2,frame)
    return skin(v,uv)


def mesh(clip,frame,uv):return geometry(clip,frame,tuple(uv))
