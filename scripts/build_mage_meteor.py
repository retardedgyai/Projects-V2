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
PALETTE=[0xFFFFFF,0xC6B8C6,0x8D8398,0xFFF2C2,0xFFBE72,0xEF733D,0xA03F35,0x39313B]
SOURCE=PACK.parents[4]/'assets/combat-vfx/mage-v5/sources/meteor-basalt-v01.png'
EMBER_SOURCE=SOURCE.with_name('meteor-ember-v01.png')
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
    """R12 155.55s: a long rising-back trail behind the descending rock."""
    x,y,z=np.moveaxis(GRID,-1,0)
    volume=np.zeros(SHAPE,dtype=np.uint8)
    # Individual long/short strips advect rearward and detach. Their positions
    # are not tied to the silhouette of one growing torch-shaped volume.
    for i,(birth,cx,cz,length,width,drift) in enumerate((
        (-2,7.7,8.3,5.5,1.45,-.10),(1,9.1,8.6,7.2,1.1,.11),
        (4,6.8,7.6,4.6,1.3,-.15),(7,8.1,8.4,8.0,1.2,.03),
        (11,9.5,7.9,5.2,.95,.14),(15,7.2,8.6,6.4,1.3,-.08),
        (18,8.3,8.1,7.1,1.1,.05),(21,6.9,8.0,4.8,1.2,-.1))):
        age=frame-birth
        if not 0<=age<17:continue
        bottom=10.5+age*.9
        u=(y-bottom)/(length+age*.13)
        xx=x-cx-drift*age-.6*np.sin(u*3+i)
        zz=z-cz-.4*u*(1 if i%2 else -1)
        fade=max(.0,1-max(0.,age-9)/8)
        w=width*fade*np.maximum(0.,1-u*.85)
        metric=np.maximum(abs(xx),abs(zz)*1.4)
        solid=(u>=0)&(u<1)&(metric<w)&((abs(xx)+abs(zz))<w*1.5)
        shade=np.where(u>.6,6,np.where(metric>w*.60,5,4))
        shade=np.where((u<.2)&(age<10)&(metric<w*.6),3,shade)
        if age>11:shade=np.where(metric<w*.55,7,6)
        volume[solid]=(shade[solid]+1).astype(np.uint8)
    return skin(volume,uv)


def pressure_burst(frame,uv):
    """R12 155.70 -> 155.84s: raised blast opens into separated curved pieces."""
    if frame>=14:return []
    x,y,z=np.moveaxis(GRID,-1,0); xx=x-8; zz=z-8; yy=y-8
    if frame>=3:
        # The pressure surface becomes separate masses, not the same dome with
        # deleted wedges. Each piece has its own flight and cooling lifetime.
        volume=np.zeros(SHAPE,dtype=np.uint8)
        fragments={}
        for i,(angle,height,size,speed,life) in enumerate(((.2,1.8,1.2,.42,10),
            (1.25,5.2,1.45,.31,11),(2.1,2.4,.85,.52,8),(3.2,1.7,1.3,.39,10),
            (4.25,4.6,1.05,.33,9),(5.4,3.6,1.5,.35,11),
            (.8,4.0,.55,.6,8),(2.6,5.5,.7,.46,9),(4.8,1.3,.6,.67,7))):
            age=frame-3
            if age>=life:continue
            radius=4.5+age*speed
            cy=height+age*.7-age*age*.035
            dx=xx-math.cos(angle)*radius; dz=zz-math.sin(angle)*radius; dy=yy-cy
            size*=max(.05,1-max(0.,age-4)/(life-4))
            solid=np.maximum.reduce((abs(dx),abs(dy)*1.2,abs(dz),
                (abs(dx)+abs(dy)+abs(dz))*.57))<size
            fragments[i]=(8+math.cos(angle)*radius,8+cy,8+math.sin(angle)*radius,size,age)
            volume[solid]=i+1
        out=skin(volume,uv)
        for e in out:
            for name,f in e['faces'].items():
                cx,cy,cz,size,age=fragments[f['tintindex']]
                axes=(2,1) if name in ('east','west') else (0,2) if name in ('up','down') else (0,1)
                center=(cx,cy,cz)
                u0,v0=[max(0.,min(16.,8+(e['from'][a]-center[a])/size*7)) for a in axes]
                u1,v1=[max(0.,min(16.,8+(e['to'][a]-center[a])/size*7)) for a in axes]
                if name in ('north','east'):u0,u1=u1,u0
                if age<4:
                    # A small fragment cannot carry the entire material sheet
                    # legibly. Keep a broad hot lobe, not a dense tiled pattern.
                    u0,u1=4+u0*.5,4+u1*.5
                    v0,v1=4+v0*.5,4+v1*.5
                f.update(texture='#2' if age<4 else '#1',uv=[u0,16-v1,u1,16-v0],
                    tintindex=0 if name in ('north','up') else 1 if name in ('east','west') else 2)
            e['from'][1]=8+(e['from'][1]-8)*1.7
            e['to'][1]=8+(e['to'][1]-8)*1.7
        return out
    angle=np.arctan2(zz,xx)
    radius=2.6+4.2*(1-math.exp(-frame/2.5))
    # A lobed pressure front with real height, not an upright reused slash.
    metric=np.sqrt(xx*xx+zz*zz+(yy*1.22)**2)
    edge=radius*(1+.10*np.cos(angle*3+.8)+.05*np.sin(angle*5))
    thickness=1.45
    solid=(metric<edge)&(yy>=0)&(metric>np.maximum(0.,edge-thickness))
    # R12's contact has two raised wings and an open center, not an opaque
    # dome covering the victim. The rear curved pressure front remains visible.
    solid&=~((abs(xx)<radius*.35)&(zz<radius*.2)&(yy>.5))
    # Broad pale contact first, peach interior and orange edge on expansion,
    # dark fragments last. Palette stages are not a long opaque white dome.
    shade=3
    out=skin(np.where(solid,shade+1,0).astype(np.uint8),uv)
    for e in out:
        e['from'][1]=8+(e['from'][1]-8)*1.7
        e['to'][1]=8+(e['to'][1]-8)*1.7
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
        t=frame/23
        for i,(speed,delay) in enumerate(((1.,0.),(.63,1.5))):
            age=frame-delay
            if age<0:continue
            radius=(1.5+5.7*(1-math.exp(-age/5)))*speed
            width=max(.09,.32*(1-t))
            contour=(abs(r-radius)<width)
            if frame>9:
                phase=(a+math.pi+i*.8)%(2*math.pi)
                contour&=(phase>max(0.,(frame-9)/14)*4.9)
            ink=np.where(np.sin(a+i*2)>.3,4,np.where(np.sin(a+i*2)>-.6,5,6))
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
    out=floor_mesh(mask,uv,8.12)+pressure_burst(frame,uv)
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
    uv=ink_uvs(assets)[3]
    for clip in sorted(METEOR_CLIPS):
        for frame in range(24):
            key=f'combat_vfx/mage_material/{clip}_{frame}'
            write(assets/f'models/{key}.json',{'ambientocclusion':False,
                'textures':{'0':'projects:combat_vfx/ribbon/slash_5','1':TEXTURE,
                            '2':'projects:combat_vfx/mage_material/meteor_ember_v01'},
                'elements':mesh(clip,frame,uv)})
            write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model','model':'projects:'+key,
                'tints':[{'type':'minecraft:constant','value':c} for c in PALETTE]}})


if __name__=='__main__':
    def write(path,value):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,separators=(',',':'))+'\n',encoding='utf-8')
    build(PACK/'assets/projects',write)
    (PACK/'index.txt').write_text('\n'.join(sorted(p.relative_to(PACK).as_posix()
        for p in PACK.rglob('*') if p.is_file() and p.name!='index.txt'))+'\n',encoding='utf-8')
