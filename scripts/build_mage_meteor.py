"""R12/R09 meteor: textured falling mass, contact flash, then a ground wake.

Native resource-pack geometry, not a preview-only drawing. The bitmap remains
unchanged; UVs stay continuous across the rock's exposed stepped facets.
"""
from functools import lru_cache
import json
import math
import shutil
import numpy as np
from build_approved_dash_v3 import PACK, ink_uvs
from build_mage_fire import box
from build_mage_garden import GRID, SHAPE, skin, rectangles

METEOR_CLIPS={'meteor','eruption','meteor_ring'}
METEOR_FLOW_CLIPS={'meteor_front',*(f'meteor_flow_{i}' for i in range(4)),'meteor_break_1','meteor_break_2'}
PALETTE=[0xFFFFFF,0xC6B8C6,0x8D8398,0xFFF2C2,0xFFBE72,0xEF733D,0xA03F35,0x39313B]
SOURCE=PACK.parents[4]/'assets/combat-vfx/mage-v5/sources/meteor-basalt-v01.png'
EMBER_SOURCE=SOURCE.with_name('meteor-ember-v01.png')
IMPACT_SOURCE=SOURCE.with_name('meteor-impact-atlas-v01.png')
BRIDGE_SOURCE=SOURCE.with_name('meteor-impact-bridge-v01.png')
WAKE_SOURCE=SOURCE.with_name('meteor-wake-v01.png')
TEXTURE='projects:combat_vfx/mage_material/meteor_basalt_v01'


@lru_cache(maxsize=4)
def rock(uv):
    uv=list(uv)
    x,y,z=np.moveaxis(GRID,-1,0)
    # One chamfered, asymmetric body; no three overlapping spherical lobes.
    xx=x-8-(y-16)*.12; yy=y-16; zz=z-8
    shape=(abs(xx)<4.8)&(abs(yy)<5.2)&(abs(zz)<4.1)
    shape&=(abs(xx)+abs(zz)<7.0)&(abs(yy)+abs(xx)<8.1)&(abs(yy)+abs(zz)<7.5)
    # R12's falling end is thermally exposed, not a nearly black rock with a
    # single hairline. The lower broken bevel wraps across adjacent faces.
    # Unequal tongues of exposed heat climb two existing fault directions;
    # the lower surface must not read as a flat luminous cap.
    hot_edge=yy+.25*xx-.15*zz-1.9*np.exp(-((xx+.7)/1.2)**2)-1.1*np.exp(-((zz-1.4)/.8)**2)
    ink=np.where(hot_edge<-4.35,3,np.where(hot_edge<-3.65,4,np.where(hot_edge<-3.15,5,0)))
    volume=np.where(shape,ink+1,0).astype(np.uint8)
    out=skin(volume,uv)
    for e in out:
        e['from'][1]-=8; e['to'][1]-=8
        x0,y0,z0=e['from']; x1,y1,z1=e['to']
        for name,f in e['faces'].items():
            if f['tintindex']!=0:continue  # broad molten bevel retains its own ink
            # Material coordinates belong to the whole body, never restart at
            # every narrow meshing rectangle. Adjacent steps retain the fissure.
            u0,u1=(x0,x1) if name not in ('east','west') else (z0,z1)
            v0,v1=(y0,y1) if name not in ('up','down') else (z0,z1)
            if name in ('north','east'):u0,u1=16-u1,16-u0
            f.update(texture='#1',uv=[u0,16-v1,u1,16-v0],
                     tintindex=0 if name in ('up','north') else 1 if name in ('west','east') else 2)
    return out


def burning_wake(frame,uv):
    """R12 155.55: dense burning matter with a broad hot belt and a long wake."""
    x,y,z=np.moveaxis(GRID,-1,0)
    bottom=10.0
    length=6.0+min(frame,15)*1.06
    t=(y-bottom)/length
    xx=x-8-.22*np.sin(y*.65-frame*.24)
    zz=z-8-.18*np.sin(y*.46+frame*.20)
    width=4.25*(1-.18*np.clip(t,0,1))
    # Chamfered, ragged volume. Most of the rear keeps its breadth; this is not
    # a narrow triangular torch attached to an otherwise isolated rock.
    solid=(t>=0)&(t<1)&(abs(xx)<width)&(abs(zz)<width*.8)&(abs(xx)+abs(zz)<width*1.45)
    rear=length-.7-1.15*(.5+.5*np.sin(xx*1.8+zz*.7))
    solid&=(y-bottom<rear)
    # Small open clefts at the very rear, with motion along the fall axis.
    solid&=~((t>.73)&(np.sin(xx*2.4+frame*.16)> .80)&(zz<-.8))
    out=skin(np.where(solid,1,0).astype(np.uint8),uv)
    for e in out:
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        for name,f in e['faces'].items():
            u0,u1=(x0,x1) if name not in ('east','west') else (z0,z1)
            u0=max(0.,(u0-3)/10*16);u1=min(16.,(u1-3)/10*16)
            if name in ('up','down'):
                v0,v1=max(0.,(z0-3)/10*16),min(16.,(z1-3)/10*16)
            else:
                # Continuous material coordinates over all meshed facets.
                # A small drift transports the fire marks, without resetting
                # their texture on every narrow rectangle.
                shift=math.sin(frame*.24)*.45
                v0=max(0.,min(15.98,16-(y1-bottom)/length*16+shift))
                v1=max(v0+.01,min(16.,16-(y0-bottom)/length*16+shift))
            f.update(texture='#3',uv=[u0,v0,u1,v1],tintindex=0)
    return out


@lru_cache(maxsize=4)
def impact_atlas(source=IMPACT_SOURCE):
    # Inspect only; the original RGB image is copied byte-for-byte. The image
    # tool supplied a painted checkerboard, NOT an alpha channel. Native faces
    # sample coloured flame pixels; achromatic backdrop has no geometry at all.
    from PIL import Image
    return np.asarray(Image.open(source).convert('RGB'))




def flow_mesh(clip,state,uv):
    # The former four-ink closed hull erased the actual drawing and exposed
    # thick brown side walls. Moving Meteor parts now retain source UV art.
    from build_mage_meteor_surface import surface
    return surface(clip,state)



def contact_wake(frame,uv):
    """The same burning wake tears apart over the first two contact ticks."""
    if frame not in (0,1):return []
    out=[]
    for e in burning_wake(22,uv):
        a,b=e['from'],e['to']
        for name,face in e['faces'].items():
            if name in ('up','down'):continue  # never cap the torn hot column
            axis=0 if name in ('north','south') else 2
            pieces=max(1,math.ceil((b[axis]-a[axis])/.5))
            for part in range(pieces):
                lo=part/pieces;hi=(part+1)/pieces
                aa=list(a);bb=list(b)
                aa[axis]=a[axis]+(b[axis]-a[axis])*lo
                bb[axis]=a[axis]+(b[axis]-a[axis])*hi
                cx=(aa[0]+bb[0])*.5;cz=(aa[2]+bb[2])*.5
                tear=.5+.5*math.sin(cx*3.11+cz*1.72)
                if frame==1 and tear>.60:continue
                low=max(a[1],10.0+frame*1.3)
                high=min(b[1],22.0-frame*.7-tear*4.2)
                if high<=low:continue
                sample=list(face['uv']);u0,v0,u1,v1=sample
                sample[0]=u0+(u1-u0)*lo;sample[2]=u0+(u1-u0)*hi
                sample[1]=v0+(v1-v0)*(b[1]-high)/(b[1]-a[1])
                sample[3]=v0+(v1-v0)*(b[1]-low)/(b[1]-a[1])
                width=2.4/4.2
                # The cooler upper streaks separate; the lower end does not
                # shrink as a rigid column. Horizontal heat is carried by the
                # contact wings immediately beneath these remaining streaks.
                spread=frame*.12
                dx=math.copysign(spread,cx-8);dz=math.copysign(spread,cz-8)
                out.append({'from':[8+(aa[0]-8)*width+dx,12.64+(low-10)*1.2-frame*.8,8+(aa[2]-8)*width+dz],
                            'to':[8+(bb[0]-8)*width+dx,12.64+(high-10)*1.2-frame*.8,8+(bb[2]-8)*width+dz],
                            'faces':{name:dict(face,uv=sample)}})
    return out


def floor_mesh(mask,uv,height=8.05):
    # A connected pixel contour; no independent dot placed every few degrees.
    out=[]; step=.25
    for x,z,x1,z1,ink in rectangles(mask):
        e=box((x*step,height,z*step),(x1*step,height+.04,z1*step),ink-1,uv)
        e['faces']={n:e['faces'][n] for n in ('up','down')}
        out.append(e)
    return out


@lru_cache(maxsize=72)
def geometry(clip,frame,uv_tuple):
    if frame>=23:return []
    uv=list(uv_tuple)
    if clip=='meteor':return rock(uv_tuple)+burning_wake(frame,uv)
    q=(np.arange(64)+.5)*.25-8
    x,z=np.meshgrid(q,q,indexing='ij'); r=np.hypot(x,z); a=np.arctan2(z,x)
    mask=np.zeros((64,64),dtype=np.uint8)
    if clip=='meteor_ring':
        # R12: the bright contact disappears first. Two thin uneven wakes
        # continue outward, then lose whole arcs instead of blinking at once.
        if frame>=18:return []
        t=frame/18
        for i,(speed,delay) in enumerate(((1.,0.),(.63,1.5))):
            age=frame-delay
            if age<0:continue
            radius=(1.5+5.7*(1-math.exp(-age/5)))*speed
            width=max(.07,.23*(1-t))
            contour=(abs(r-radius-.14*np.sin(a*3+i)-.08*np.sin(a*7))<width)
            if frame>4:
                phase=(a+math.pi+i*.8)%(2*math.pi)
                contour&=(phase>max(0.,(frame-4)/14)*5.8)
            # R12's leftover ground swirl cools to ash; it is not a persistent
            # bright red spell circle competing with the newly rising flames.
            ink=np.where(np.sin(a+i*2)>.3,4,5) if frame<3 else np.where(np.sin(a+i*2)>.3,2,7)
            mask[contour]=(ink[contour]+1).astype(np.uint8)
        return floor_mesh(mask,uv)
    # The early ground contact supports the raised pressure front. R12's
    # closer temporal review disproved the former floor-only interpretation.
    if frame<3:
        t=frame/3
        outer=(2.4+2.0*min(1.,frame/2))*(1-t*.45)
        lobes=.32*np.cos(a*5+.4)+.18*np.cos(a*3-1.3)
        edge=outer*(1+lobes)
        solid=r<edge
        if frame>2:solid&=r>(frame-2)*.75
        mask[solid]=5
        mask[solid&(r<edge-.4)]=4
        for angle,length in ((.2,7.1),(1.9,5.3),(3.3,6.4),(4.9,7.5)):
            along=x*math.cos(angle)+z*math.sin(angle)
            across=abs(-x*math.sin(angle)+z*math.cos(angle))
            tip=length*(.55+.45*min(1.,frame/2))
            ray=(along>max(0.,frame-2)*.6)&(along<tip)&(across<(.28*(1-along/tip)+.06)*(1-t))
            mask[ray]=4 if frame<4 else 5
    # The flash and all main flame masses belong to persistent displays.
    out=floor_mesh(mask,uv,8.12)+contact_wake(frame,uv)
    # Small detached debris follows a ballistic path and cools. Unequal sizes,
    # not six equally bright projectiles competing with the primary contact.
    for i,(angle,speed,size) in enumerate(((.4,.37,.38),(2.2,.29,.27),(3.8,.42,.2),(5.2,.32,.31))):
        age=frame-i*.35
        if age<1 or age>15-i:continue
        distance=.8+age*speed
        yy=8.3+age*.45-age*age*.025
        if yy<8.05:continue
        size*=max(.12,1-max(0.,age-7)/8)
        xx,zz=8+math.cos(angle)*distance,8+math.sin(angle)*distance
        e=box((xx-size,yy-size,zz-size),(xx+size,yy+size,zz+size),7,uv)
        e['faces']['up']['tintindex']=4 if age<5 else 6
        out.append(e)
    return out


def mesh(clip,frame,uv):return geometry(clip,frame,tuple(uv))


def build(assets,write):
    target=assets/'textures/combat_vfx/mage_material/meteor_basalt_v01.png'
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(SOURCE,target)
    shutil.copyfile(EMBER_SOURCE,target.with_name('meteor_ember_v01.png'))
    shutil.copyfile(IMPACT_SOURCE,target.with_name('meteor_impact_atlas_v01.png'))
    shutil.copyfile(BRIDGE_SOURCE,target.with_name('meteor_impact_bridge_v01.png'))
    shutil.copyfile(WAKE_SOURCE,target.with_name('meteor_wake_v01.png'))
    uv=ink_uvs(assets)[3]
    for clip in sorted(METEOR_CLIPS|METEOR_FLOW_CLIPS):
        for frame in range(3 if clip in METEOR_FLOW_CLIPS else 24):
            key=f'combat_vfx/mage_material/{clip}_{frame}'
            write(assets/f'models/{key}.json',{'ambientocclusion':False,
                'textures':{'0':'projects:combat_vfx/ribbon/slash_5','1':TEXTURE,
                            '2':'projects:combat_vfx/mage_material/meteor_impact_atlas_v01',
                            '3':'projects:combat_vfx/mage_material/meteor_wake_v01',
                            '4':'projects:combat_vfx/mage_material/meteor_impact_bridge_v01'},
                'elements':flow_mesh(clip,frame,uv) if clip in METEOR_FLOW_CLIPS else mesh(clip,frame,uv)})
            write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key,
                'tints':[{'type':'minecraft:constant','value':c} for c in PALETTE]}})


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
