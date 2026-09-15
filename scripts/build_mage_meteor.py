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
BLAST_RESOLUTION=32
BLAST_STEP=np.array((24/BLAST_RESOLUTION,16/BLAST_RESOLUTION,24/16))
BLAST_ORIGIN=np.array((-4.,8.,-4.))
PALETTE=[0xFFFFFF,0xC6B8C6,0x8D8398,0xFFF2C2,0xFFBE72,0xEF733D,0xA03F35,0x39313B]
SOURCE=PACK.parents[4]/'assets/combat-vfx/mage-v5/sources/meteor-basalt-v01.png'
EMBER_SOURCE=SOURCE.with_name('meteor-ember-v01.png')
IMPACT_SOURCE=SOURCE.with_name('meteor-impact-atlas-v01.png')
BRIDGE_SOURCE=SOURCE.with_name('meteor-impact-bridge-v01.png')
WAKE_SOURCE=SOURCE.with_name('meteor-wake-v01.png')
LOBE_SOURCE=SOURCE.with_name('meteor-lobe-orthographic-v01.png')
PRESSURE_SOURCE=SOURCE.with_name('meteor-pressure-orthographic-v01.png')
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


@lru_cache(maxsize=1)
def flow_grid(clip):
    """Short contact flash only. Separating flame bodies have their own full art."""
    if clip!='meteor_front':raise ValueError('Moving lobes use lobe_views(), not cut-up flash art')
    atlas=impact_atlas();height,width=atlas.shape[:2];resolution=48
    drawing=0
    grid=np.zeros((resolution,resolution),dtype=np.int16)
    for x in range(resolution):
        for y in range(resolution):
            rgb=atlas[int((drawing//4+(y+.5)/resolution)*height/2),
                      int((drawing%4+(x+.5)/resolution)*width/4)].astype(int)
            if rgb[0]-rgb[2]<32:continue
            # Four coherent value groups replace tiny per-pixel tonal changes.
            grid[x,y]=3 if rgb[1]>=205 else 4 if rgb[1]>=140 else 5 if rgb[1]>=75 else 6
    # Remove disconnected sparks; they must not carry the blast's primary mass.
    unseen=set(zip(*np.nonzero(grid)));components=[]
    while unseen:
        seed=min(unseen);unseen.remove(seed);stack=[seed];component=[]
        while stack:
            x,y=stack.pop();component.append((x,y))
            for neighbour in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if neighbour in unseen:unseen.remove(neighbour);stack.append(neighbour)
        components.append(component)
    keep=np.zeros_like(grid)
    chosen=[max(components,key=len)]
    for component in chosen:
        for x,y in component:keep[x,y]=grid[x,y]
    grid=keep
    pivot=(24.,43.5)
    return grid,pivot


def flow_mesh(clip,state,uv):
    """Hot silhouettes keep identity; the final ash state loses filling.

    Translation/expansion/collapse is animated by native display transforms.
    The brief contact flash stays shallow; the moving lobes use closed volumes.
    """
    if clip!='meteor_front':return lobe_mesh(clip,state,uv)
    grid,(cx,cy)=flow_grid(clip);out=[]
    for x,y,x1,y1,ink in rectangles(grid):
        x0=8+(x-cx)*.36;xx1=8+(x1-cx)*.36
        y0=8+(cy-y1)*.54;yy1=8+(cy-y)*.54
        # The short contact front bows around the impact; it never separates.
        z=8+(((x+x1)/2-24)*.36)**2*.012
        depth=.18
        tint=ink if state==0 else {3:4,4:5,5:6,6:7}[ink] if state==1 else {3:1,4:2,5:7,6:7}[ink]
        e=box((x0,y0,z-depth),(xx1,yy1,z+depth),tint,uv)
        for n in ('east','west','down'):
            e['faces'][n]['tintindex']={3:4,4:5,5:6,6:6}.get(tint,tint)
        out.append(e)
    return out


@lru_cache(maxsize=2)
def lobe_views(source=LOBE_SOURCE):
    """Sample complete front/side artwork into native material cells, not a PNG edit.

    The two orthographic silhouettes define all three dimensions. Navy background
    has no geometry. A larger drawing of the whole blast is never partitioned.
    """
    atlas=impact_atlas(source);height,width=atlas.shape[:2];n=14
    result=[]
    for view in range(2):
        pixels=atlas[:,view*width//2:(view+1)*width//2].astype(int)
        warm=(pixels[:,:,0]-pixels[:,:,2]>60)&(pixels[:,:,0]>100)
        ys,xs=np.nonzero(warm)
        # Match the two authored vertical extents; retain each view's contour.
        x0,x1=xs.min(),xs.max()+1;y0,y1=ys.min(),ys.max()+1
        xx=np.minimum(x1-1,(x0+(np.arange(n)+.5)*(x1-x0)/n).astype(int))
        yy=np.minimum(y1-1,(y0+(np.arange(n)+.5)*(y1-y0)/n).astype(int))
        rgb=pixels[yy[:,None],xx[None,:]]
        mask=(rgb[:,:,0]-rgb[:,:,2]>60)&(rgb[:,:,0]>100)
        inks=np.where(rgb[:,:,1]>=205,3,np.where(rgb[:,:,1]>=140,4,np.where(rgb[:,:,1]>=75,5,6)))
        result.append(np.where(mask,inks,0).T[:,::-1].copy())
    return tuple(result)


@lru_cache(maxsize=2)
def lobe_volume(source=LOBE_SOURCE):
    """Artwork-defined visual hull with rounded, rather than rectangular, flanks."""
    front,side=lobe_views(source);n=front.shape[0]
    solid=np.zeros((n,n,n),dtype=bool)
    def intervals(row):
        positions=np.flatnonzero(row)
        return np.split(positions,np.flatnonzero(np.diff(positions)>1)+1) if len(positions) else []
    for y in range(n):
        for xs in intervals(front[:,y]):
            for zs in intervals(side[:,y]):
                u=(xs-(xs[0]+xs[-1])/2)/(len(xs)/2)
                v=(zs-(zs[0]+zs[-1])/2)/(len(zs)/2)
                # This only rounds the intersection; its width, leaning, curl,
                # open cleft and changing depth all come from the two drawings.
                inside=abs(u[:,None])**2.5+abs(v[None,:])**2.5<=1.12
                solid[xs[:,None],y,zs[None,:]]=inside
    # Logical-pixel sampling can strand one-cell tips. They are not independent
    # particles and must not float rigidly alongside this primary flame body.
    pending=set(zip(*np.nonzero(solid)));components=[]
    while pending:
        stack=[pending.pop()];component=[]
        while stack:
            x,y,z=stack.pop();component.append((x,y,z))
            for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                if p in pending:pending.remove(p);stack.append(p)
        components.append(component)
    keep=max(components,key=len)
    if len(keep)<solid.sum()*.98:raise ValueError(f'{source.name}: materially disconnected flame body')
    solid[:]=False
    for point in keep:solid[point]=True
    return solid


@lru_cache(maxsize=1)
def blast_volume():
    """R12's ground-rooted, open pressure arch, not a floating curled ball.

    The existing authored climax drawing defines the silhouette/material. Bend
    it into a deep shell and round its thickness; no raster edits or crossed
    cards. All regions initially belong to this ONE world-space pressure front.
    """
    atlas=impact_atlas();h,w=atlas.shape[:2];n=BLAST_RESOLUTION;depth=16
    art=atlas[:h//2,w//2:3*w//4].astype(int)
    warm=(art[:,:,0]-art[:,:,2]>60)&(art[:,:,0]>100)
    ys,xs=np.nonzero(warm)
    xx=np.minimum(xs.max(),(xs.min()+(np.arange(n)+.5)*(xs.max()+1-xs.min())/n).astype(int))
    yy=np.minimum(ys.max(),(ys.min()+(np.arange(n)+.5)*(ys.max()+1-ys.min())/n).astype(int))
    rgb=art[yy[:,None],xx[None,:]]
    mask=(rgb[:,:,0]-rgb[:,:,2]>60)&(rgb[:,:,0]>100)
    ink=np.where(rgb[:,:,1]>=205,3,np.where(rgb[:,:,1]>=140,4,np.where(rgb[:,:,1]>=75,5,6)))
    front=np.where(mask,ink,0).T[:,::-1].copy()
    x,y,z=np.indices((n,n,depth))
    across=(x+.5-n/2)/(n/2)
    centre=3.2+6.8*(1-across**2)+.6*np.sin(y*24/n*.35)
    thickness=1.3+.5*(front[:,:,None]==3)+.4*(front[:,:,None]==4)
    solid=(front[:,:,None]>0)&(abs(z+.5-centre)<thickness)
    # Isolated sparks in the drawing are not part of the main pressure front.
    pending=set(zip(*np.nonzero(solid)));components=[]
    while pending:
        stack=[pending.pop()];component=[]
        while stack:
            p=stack.pop();component.append(p)
            for axis in range(3):
                for sign in (-1,1):
                    q=list(p);q[axis]+=sign;q=tuple(q)
                    if q in pending:pending.remove(q);stack.append(q)
        components.append(component)
    solid[:]=False
    for p in max(components,key=len):solid[p]=True
    return solid,front


@lru_cache(maxsize=1)
def lobe_partitions():
    """Connected peeling regions in the actual three-dimensional pressure arch.

    Their union is the intact source shape. The later motion reveals actual
    internal faces, not rectangular cuts in a flat picture.
    """
    import heapq
    solid,front=blast_volume()
    points=list(zip(*np.nonzero(solid)))
    seeds=[min(points,key=lambda p:sum((a-b)**2 for a,b in zip(p,q)))
           for q in ((5*BLAST_RESOLUTION/24,5*BLAST_RESOLUTION/24,7),
                     (13*BLAST_RESOLUTION/24,17*BLAST_RESOLUTION/24,10),
                     (20*BLAST_RESOLUTION/24,5*BLAST_RESOLUTION/24,6))]
    labels=np.full(solid.shape,-1,dtype=np.int16);distance=np.full(solid.shape,np.inf);queue=[]
    for i,p in enumerate(seeds):
        labels[p]=i;distance[p]=0;heapq.heappush(queue,(0.,i,p))
    while queue:
        cost,label,(x,y,z)=heapq.heappop(queue)
        if cost!=distance[x,y,z] or labels[x,y,z]!=label:continue
        for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
            if not all(0<=v<solid.shape[i] for i,v in enumerate(p)) or not solid[p]:continue
            nx,ny,nz=p
            difference=abs(int(front[x,y])-int(front[nx,ny]))
            candidate=cost+1+difference*.2
            if candidate<distance[p]:
                labels[p]=label;distance[p]=candidate;heapq.heappush(queue,(candidate,label,p))
    return tuple(labels==i for i in range(3))


def lobe_centers():
    return [(np.argwhere(part).min(axis=0)+np.argwhere(part).max(axis=0)+1)/2*BLAST_STEP+BLAST_ORIGIN-8
            for part in lobe_partitions()]


def blast_surface(clip,uv_tuple,ash):
    member=int(clip.rsplit('_',1)[1]) if clip.startswith('meteor_break_') else 0
    solid=lobe_partitions()[member].copy();_,front=blast_volume()
    if ash:
        # Break the pressure rim into uneven cool remnants, leaving the open
        # centre open. Retained fragments inherit the original front/depth.
        x,y,z=np.indices(solid.shape)
        filled=front>0;padded=np.pad(filled,1);interior=filled.copy()
        for dx,dy in ((-1,0),(1,0),(0,-1),(0,1)):
            interior &= padded[1+dx:1+BLAST_RESOLUTION+dx,1+dy:1+BLAST_RESOLUTION+dy]
        rim=(filled&~interior)[:,:,None]
        ax=x*24/BLAST_RESOLUTION;ay=y*24/BLAST_RESOLUTION
        fragments=rim&((ax//5+ay//4)%4!=0)
        fragments|=((ax-5)**2+(ay-5)**2<4)|((ax-19)**2+(ay-7)**2<2)
        solid &= fragments
    step=BLAST_STEP;origin=BLAST_ORIGIN-lobe_centers()[member]
    out=[]
    for axis in range(3):
        other=[i for i in range(3) if i!=axis]
        for sign in (-1,1):
            neighbor=np.roll(solid,-sign,axis=axis)
            edge=[slice(None)]*3;edge[axis]=-1 if sign==1 else 0;neighbor[tuple(edge)]=False
            exposed=solid&~neighbor
            name=(('west','east'),('down','up'),('north','south'))[axis][sign==1]
            inks=np.broadcast_to(front[:,:,None],solid.shape)
            if axis==0 or (axis==1 and sign<0):inks=np.minimum(6,inks+1)
            material=np.where(exposed,inks,0)
            for layer in range(solid.shape[axis]):
                mask=np.take(material,layer,axis=axis)
                choices=(rectangles(mask),[(b,a,end,stop,c) for a,b,stop,end,c in rectangles(mask.T)])
                for a,b,stop,end,color in min(choices,key=len):
                    lo=origin.copy();hi=origin.copy()
                    lo[axis]+=step[axis]*(layer+(sign==1));hi[axis]=lo[axis]+.002
                    lo[other[0]]+=a*step[other[0]];hi[other[0]]+=stop*step[other[0]]
                    lo[other[1]]+=b*step[other[1]];hi[other[1]]+=end*step[other[1]]
                    if sign==1:lo[axis]-=.002;hi[axis]-=.002
                    e=box(lo,hi,int(color),list(uv_tuple));e['faces']={name:e['faces'][name]};out.append(e)
    return out


@lru_cache(maxsize=18)
def lobe_surface(clip,uv_tuple,ash=False):
    """One exposed closed skin, no crossed cards and no internal cube faces."""
    if clip=='meteor_flow_1' or clip.startswith('meteor_break_'):
        return blast_surface(clip,uv_tuple,ash)
    number=int(clip.rsplit('_',1)[1])
    source=PRESSURE_SOURCE if number in (0,2) else LOBE_SOURCE
    front,side=lobe_views(source);solid=lobe_volume(source);n=solid.shape[0]
    spans=((9.5,5.6,4.8),(7.4,11.,6.5),(8.4,4.8,3.8),(4.2,6.2,4.5))[number]
    # Two complete pressure tongues unfold below one large rolling body and
    # a smaller peeling one. They are not four scaled copies of the same curl.
    if number>=2:solid=solid[::-1].copy();front=front[::-1].copy()
    if number%2:solid=solid.transpose(2,1,0).copy();front,side=side,front
    out=[];step=np.array(spans)/n;origin=8-np.array(spans)/2
    if ash and number==3:
        # R12 155.99: the bright filling has burned away, leaving interrupted
        # dark curled rims, not the complete flame coloured grey. Read the
        # outline in BOTH authored views so no opaque back card fills the hole.
        def rim(view):
            filled=view>0;padded=np.pad(filled,1)
            interior=filled.copy()
            for dx,dy in ((-1,0),(1,0),(0,-1),(0,1)):
                interior&=padded[1+dx:1+dx+n,1+dy:1+dy+n]
            return filled&~interior
        remnant=solid&rim(front)[:,:,None]&rim(side).T[None,:,:]
        solid=remnant
    for axis in range(3):
        other=[i for i in range(3) if i!=axis]
        for sign in (-1,1):
            neighbor=np.roll(solid,-sign,axis=axis)
            edge=[slice(None)]*3;edge[axis]=-1 if sign==1 else 0;neighbor[tuple(edge)]=False
            exposed=solid&~neighbor
            name=(('west','east'),('down','up'),('north','south'))[axis][sign==1]
            # Each direction uses the corresponding authored value groups,
            # rather than painting every side a uniform dark extrusion colour.
            ink=np.broadcast_to((side.T[None,:,:] if axis==0 else front[:,:,None]),solid.shape)
            if axis==1:
                ink=np.minimum(np.broadcast_to(front[:,:,None],solid.shape),np.broadcast_to(side.T[None,:,:],solid.shape))
                if sign<0:ink=np.maximum(5,ink)
            material=np.where(exposed,ink,0)
            for layer in range(n):
                mask=np.take(material,layer,axis=axis)
                choices=(rectangles(mask),[(b,a,end,stop,c) for a,b,stop,end,c in rectangles(mask.T)])
                for a,b,stop,end,color in min(choices,key=len):
                    lo=origin.copy();hi=origin.copy()
                    lo[axis]+=step[axis]*(layer+(sign==1));hi[axis]=lo[axis]+.002
                    lo[other[0]]+=a*step[other[0]];hi[other[0]]+=stop*step[other[0]]
                    lo[other[1]]+=b*step[other[1]];hi[other[1]]+=end*step[other[1]]
                    if sign==1:lo[axis]-=.002;hi[axis]-=.002
                    e=box(lo,hi,int(color),list(uv_tuple));e['faces']={name:e['faces'][name]};out.append(e)
    return out


def lobe_mesh(clip,state,uv):
    # Hot -> cooling retains topology. Ash deliberately loses its filled core:
    # the surviving rims keep their original local coordinates while the native
    # displays continue separating them; it is not a new full-blast drawing.
    mapping=({3:3,4:4,5:5,6:6},{3:4,4:5,5:6,6:7},{3:1,4:2,5:7,6:7})[state]
    return [dict(e,faces={name:dict(f,tintindex=mapping[f['tintindex']]) for name,f in e['faces'].items()})
            for e in lobe_surface(clip,tuple(uv),ash=state==2)]


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
