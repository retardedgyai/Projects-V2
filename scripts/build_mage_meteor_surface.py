"""Paint-preserving Meteor surfaces, derived from the original R12 study art.

This is native model/UV authoring, not raster processing. The source bitmap is
never rewritten. Its baked checkerboard has NO corresponding model faces.
The curved front is a design experiment, not a claim about Wynn's internals.
"""
from functools import lru_cache
import math
import numpy as np
from build_mage_garden import rectangles

# Model-space polyline for one half of the pressure envelope. Its rear half
# mirrors depth, not a thick extrusion; the halves meet at the outer ends.
# Slopes use vanilla's supported 0 / 22.5 / 45 degree rotations.
KNOT_X=np.array((-12.,-8.,-4.,4.,8.,12.))
KNOT_Z=np.array((0.,4.,4.+4*math.tan(math.pi/8),4.+4*math.tan(math.pi/8),4.,0.))

# A material sample, not the banner picture/mesh. This existing texel really
# has RGBA (37,37,41,68); native UV sampling preserves its partial alpha.
VAPOR_UV=[24.2/48*16,61.2/80*16,24.8/48*16,61.8/80*16]


@lru_cache(maxsize=6)
def thermal_ink(state):
    """One registered flame drawing burns into its OWN rims and remnants.

    R12 155.864 -> 155.914 -> 155.964 retains the same curls while the orange
    filling recedes, leaving dark/red edges, then vapor. No cold-atlas swap.
    This authors model coverage/materials; it does not edit any bitmap.
    """
    mask,_,rgb=drawing(2)
    if state==0:return mask.astype(np.uint8)
    inside=mask.copy()
    for _ in range(6):
        p=np.pad(inside,1)
        inside=p[1:-1,1:-1]&p[:-2,1:-1]&p[2:,1:-1]&p[1:-1,:-2]&p[1:-1,2:]
    rim=mask&~inside
    h,w=mask.shape;y,x=np.mgrid[:h,:w];x=x/w;y=y/h
    # Unequal breaks along the authored curled edge, not evenly spaced sparks.
    fracture=np.sin(x*41+np.sin(y*23)*.9)+.7*np.sin(x*19-y*31)
    ink=np.zeros(mask.shape,dtype=np.uint8)
    if state in (1,2,3):
        # The hot interior is gone, not a large translucent panel. R12 leaves
        # a thin torn boundary; filling every old flame area exposed the planar
        # facets as broad gray wedges on a bright background.
        ink[rim]=3
        solid=rim&(fracture>(-.8,-.4,.1)[state-1])
        ink[solid]=2
        # Broad connected heat pockets belong to the three painted curls.
        # Their outer tongues lose heat first; the heavier roots persist.
        # A material clock per point lets hot paint, ember and empty interior
        # coexist within ONE display, instead of changing a whole region red
        # on one tick and charcoal on the next. This is native face coverage,
        # not a recolored bitmap or per-pixel random/dither dissolve.
        pockets=np.maximum.reduce([
            np.exp(-(((x-a)/sx)**2+((y-b)/sy)**2))
            for a,b,sx,sy in ((.18,.79,.17,.22),(.77,.71,.21,.24),(.56,.41,.17,.20))])
        # The painted yellow curls, not the Gaussian boundary, determine the
        # last hot shapes. A purely radial clock left three round red coins.
        painted_heat=np.clip((rgb[:,:,1]-65)/170,0,1)
        # Heat belongs to painted pixel clusters. Classifying every enlarged
        # source texel introduced tiny noisy material faces along smooth RGB
        # edges. Sample one native coverage cell per four source texels; keep
        # the original continuous RGB UVs and silhouette completely intact.
        size=4;ph=(-h)%size;pw=(-w)%size
        valid=np.pad(mask,((0,ph),(0,pw))).reshape((h+ph)//size,size,(w+pw)//size,size)
        weights=valid.sum(axis=(1,3))
        sampled=np.pad(painted_heat*mask,((0,ph),(0,pw))).reshape(valid.shape).sum(axis=(1,3))
        painted_heat=(sampled/np.maximum(1,weights)).repeat(size,axis=0).repeat(size,axis=1)[:h,:w]
        local_age=(.90,1.40,2.05)[state-1]-(.05+.55*pockets+painted_heat)
        # Wider than the largest sampled clock step (.65): every hot patch
        # must pass through ember at least once, never hot -> gray in one item
        # change. Display transforms remain interpolated by the client.
        ink[mask&(local_age<.75)]=4
        ink[mask&(local_age<0)]=1
    else:
        solid=rim&(fracture>(.55 if state==4 else 1.15))
        ink[solid]=2
        # Pale detached chips are separate from the translucent membrane.
        ink[solid&(np.sin(x*17-y*29)>.85)]=5
    return ink


@lru_cache(maxsize=12)
def drawing(frame,cooling=False):
    from build_mage_meteor import impact_atlas, COOLING_SOURCE
    atlas=impact_atlas(COOLING_SOURCE) if cooling else impact_atlas();h,w=atlas.shape[:2]
    columns=2 if cooling else 4
    x0=round(frame%columns*w/columns);x1=round((frame%columns+1)*w/columns)
    y0=round(frame//columns*h/2);y1=round((frame//columns+1)*h/2)
    rgb=atlas[y0:y1,x0:x1].astype(int)
    # The checkerboard is achromatic. Do NOT require bright red: that erased
    # the authored dark, cooling edges and left only bright orange specks.
    mask=((np.ptp(rgb,axis=2)<24)&(rgb.min(axis=2)>20) if cooling else
          (rgb[:,:,0]-rgb[:,:,2]>12)&(rgb[:,:,0]-rgb[:,:,1]>6)&(rgb[:,:,0]>20))
    # Native silhouette cells span two source texels (the painted pixels are
    # larger). Admit a cell only when ALL its texels belong to the artwork.
    # RGB remains untouched and is still sampled continuously by the UVs.
    hh,ww=(v//2*2 for v in mask.shape)
    cells=mask[:hh,:ww].reshape(hh//2,2,ww//2,2).all(axis=(1,3))
    mask[:]=False;mask[:hh,:ww]=cells.repeat(2,axis=0).repeat(2,axis=1)
    # Sub-logical-pixel islands in this enlarged source are edge noise, not
    # independently animated sparks. Keep actual clusters, including dark ones.
    pending=set(zip(*np.nonzero(mask)))
    while pending:
        stack=[pending.pop()];component=[]
        while stack:
            y,x=stack.pop();component.append((y,x))
            for p in ((y-1,x),(y+1,x),(y,x-1),(y,x+1)):
                if p in pending:pending.remove(p);stack.append(p)
        if len(component)<16:
            for p in component:mask[p]=False
    return mask,(x0,y0,x1,y1),rgb


def region_labels(shape):
    """Unequal flame regions, stable in artwork space throughout dissolution."""
    h,w=shape;y,x=np.mgrid[:h,:w];x=(x+.5)/w;y=(y+.5)/h
    seeds=((.20,.70),(.53,.27),(.83,.68))
    return np.argmin(np.stack([(x-a)**2+(y-b)**2 for a,b in seeds]),axis=0)


def point(u,v):
    x=-12+24*u
    base,top=vertical_bounds()
    return np.array((x,16*(base-v)/(base-top),np.interp(x,KNOT_X,KNOT_Z)))


@lru_cache(maxsize=1)
def vertical_bounds():
    mask,_,_=drawing(2);y,_=np.nonzero(mask)
    return (y.max()+1)/mask.shape[0],y.min()/mask.shape[0]


@lru_cache(maxsize=1)
def centers():
    mask,_,_=drawing(2);labels=region_labels(mask.shape);h,w=mask.shape
    result=[]
    for member in range(3):
        y,x=np.nonzero(mask&(labels==member))
        points=np.array([point(a/w,b/h) for a,b in zip(x,y)])
        result.append((points.min(axis=0)+points.max(axis=0))/2)
    return tuple(result)


def surface(clip,state):
    """Whole painted value groups on thin facets; no four-ink voxel hull.

    Three regions on each half follow persistent moving pivots.
    Later drawings remove hot filling rather than tinting a full orange wall
    brown. A short contact flash is separate from this pressure envelope.
    """
    from build_mage_meteor import impact_atlas
    from build_approved_dash_v3 import ink_uvs, PACK
    if clip=='meteor_flow_2':
        # A transverse, curved crest joins the two ground-rooted sides. Merely
        # mirroring another vertical front still vanished edge-on at the top.
        # This is one fixed structural part, NOT a spinning full-blast picture.
        # Re-express vertices/faces so every element rotation stays vanilla-legal.
        c=centers()[1];out=[]
        def turn(p):return [p[2]-c[0],p[1],16-p[0]+c[2]]
        for e in surface('meteor_break_1',state):
            a,b=turn(e['from']),turn(e['to'])
            item=dict(e,**{'from':[min(x,y) for x,y in zip(a,b)],
                           'to':[max(x,y) for x,y in zip(a,b)],
                           'faces':{'west':dict(e['faces']['north']),'east':dict(e['faces']['south'])}})
            if 'rotation' in e:item['rotation']=dict(e['rotation'],origin=turn(e['rotation']['origin']))
            out.append(item)
        return out
    rear=clip in ('meteor_flow_0','meteor_flow_2','meteor_flow_3')
    main=clip!='meteor_front'
    member=(('meteor_flow_0','meteor_flow_2','meteor_flow_3').index(clip) if rear else
            int(clip.rsplit('_',1)[1]) if clip.startswith('meteor_break_') else 0)
    # Every phase shares one artwork coordinate system. Exchanging separately
    # drawn hot/cold silhouettes made the whole envelope jump between pictures.
    frame=2 if main else 0
    mask,bounds,_=drawing(frame);mask=mask.copy();h,w=mask.shape
    inks=thermal_ink(state).copy() if main else mask.astype(np.uint8)
    if main:
        mask &= region_labels(mask.shape)==member
        inks[~mask]=0
        pivot=centers()[member].copy()
        if rear:pivot[2]*=-1
    else:
        pivot=np.zeros(3)
    atlas=impact_atlas()
    ah,aw=atlas.shape[:2];x0,y0,x1,y1=bounds
    white_uv=ink_uvs(PACK/'assets/projects')[3]
    out=[]
    # Partition at bend changes before greedy meshing. Within each domain the
    # painted surface is exactly planar and neighboring UVs remain continuous.
    for k in range(len(KNOT_X)-1):
        left=round((KNOT_X[k]+12)/24*w);right=round((KNOT_X[k+1]+12)/24*w)
        local=inks[:,left:right]
        candidates=(rectangles(local.astype(int)),
                    [(b,a,d,c,ink) for a,b,c,d,ink in rectangles(local.T.astype(int))])
        slope=(KNOT_Z[k+1]-KNOT_Z[k])/(KNOT_X[k+1]-KNOT_X[k])
        if rear:slope*=-1
        angle=-math.atan(slope);cosine=math.cos(angle)
        for ya,xa,yb,xb,ink in min(candidates,key=len):
            xa+=left;xb+=left
            # Snap the domain edge to its exact bend; adjacent domains share
            # this endpoint even when source dimensions aren't divisible by 4.
            def xx(pixel):
                return KNOT_X[k]+(pixel-left)/(right-left)*(KNOT_X[k+1]-KNOT_X[k])
            ax,bx=xx(xa),xx(xb);mx=(ax+bx)/2
            mz=KNOT_Z[k]*(-1 if rear else 1)+(mx-KNOT_X[k])*slope
            base,top=vertical_bounds()
            center=np.array((8+mx,8+16*(base-(ya+yb)/2/h)/(base-top),8+mz))-pivot
            half=(bx-ax)/cosine/2;hy=(yb-ya)/h*8/(base-top)
            # A tiny two-sided sheet, never a solid extrusion. Front and back
            # display the original picture; no dark side face can become a wall.
            uv=[(x0+xa+.02)/aw*16,(y0+ya+.02)/ah*16,
                (x0+xb-.02)/aw*16,(y0+yb-.02)/ah*16]
            texture='#2';tint=0
            if ink==2:texture='#0';uv=white_uv;tint=7
            elif ink==3:texture='#6';uv=VAPOR_UV;tint=0
            elif ink==4:tint=8
            elif ink==5:texture='#0';uv=white_uv;tint=1
            e={'from':[center[0]-half,center[1]-hy,center[2]-.001],
               'to':[center[0]+half,center[1]+hy,center[2]+.001],
               'shade':False,
               'faces':{'south':{'texture':texture,'uv':uv,'tintindex':tint},
                        'north':{'texture':texture,'uv':[uv[2],uv[1],uv[0],uv[3]],'tintindex':tint}}}
            if angle:
                e['rotation']={'origin':center.tolist(),'axis':'y',
                               'angle':round(math.degrees(angle),4),'rescale':False}
            out.append(e)
    return out
