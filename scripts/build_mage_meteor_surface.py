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
    from build_mage_meteor import impact_atlas, COOLING_SOURCE
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
    # A rupture membrane, torn ribbons, then free gray/charcoal chips. Cooling
    # is new topology, not the same hot arch with a dark tint or smaller scale.
    cooling=main and state>=2
    frame=state-2 if cooling else (2,3)[min(state,1)] if main else 0
    mask,bounds,_=drawing(frame,cooling);mask=mask.copy();h,w=mask.shape
    if main:
        mask &= region_labels(mask.shape)==member
        pivot=centers()[member].copy()
        if rear:pivot[2]*=-1
    else:
        pivot=np.zeros(3)
    atlas=impact_atlas(COOLING_SOURCE) if cooling else impact_atlas()
    ah,aw=atlas.shape[:2];x0,y0,x1,y1=bounds
    out=[]
    # Partition at bend changes before greedy meshing. Within each domain the
    # painted surface is exactly planar and neighboring UVs remain continuous.
    for k in range(len(KNOT_X)-1):
        left=round((KNOT_X[k]+12)/24*w);right=round((KNOT_X[k+1]+12)/24*w)
        local=mask[:,left:right]
        candidates=(rectangles(local.astype(int)),
                    [(b,a,d,c,ink) for a,b,c,d,ink in rectangles(local.T.astype(int))])
        slope=(KNOT_Z[k+1]-KNOT_Z[k])/(KNOT_X[k+1]-KNOT_X[k])
        if rear:slope*=-1
        angle=-math.atan(slope);cosine=math.cos(angle)
        for ya,xa,yb,xb,_ in min(candidates,key=len):
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
            e={'from':[center[0]-half,center[1]-hy,center[2]-.001],
               'to':[center[0]+half,center[1]+hy,center[2]+.001],
               'shade':False,
               'faces':{'south':{'texture':'#5' if cooling else '#2','uv':uv,'tintindex':0},
                        'north':{'texture':'#5' if cooling else '#2','uv':[uv[2],uv[1],uv[0],uv[3]],'tintindex':0}}}
            if angle:
                e['rotation']={'origin':center.tolist(),'axis':'y',
                               'angle':round(math.degrees(angle),4),'rescale':False}
            out.append(e)
    return out
