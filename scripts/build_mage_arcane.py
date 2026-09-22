"""Mage's remaining native glass/rift/discharge vocabulary, not slash cards.

Designed flat colour planes on shaped volume. Lightning has an angular conducting
spine, blink is a closing/opening wound, ward an open convex shell. No braided
sines, uniformly spaced rings, or whole-texture spinning.
"""
import math
import numpy as np
from build_approved_dash_v3 import SIZE, polygon
from build_mage_fire import box
from build_mage_uv import painted

ARCANE_CLIPS={'conductor','arcane_forks','thunder_hit','discharge','rupture','fold_in','fold_out',
              'ward','ward_mote','arcane_charge'}
PALETTE=[0x382663,0x7855c4,0xa68aea,0x72d8e4,0xe8edff]
PALETTE += [0x615B88,0xAAA6CE,0xD4D3EE,0xF2F2FF,0xFFFFFF]
PAINTED_ARCANE={'fold_in','fold_out','ward','ward_mote','arcane_charge'}


def ribbon(out,points,width,ink,uv):
    # Quantized 3-D path with hard direction changes and a coherent core. Each
    # short rectangular segment is a native legal model element, not a sprite.
    for a,b in zip(points,points[1:]):
        a,b=np.asarray(a),np.asarray(b)
        count=max(1,math.ceil(np.max(abs(b-a))/.24))
        for j in range(count):
            p=a+(b-a)*(j+.5)/count
            w=width
            lo=p-np.array((w,w,.14));hi=p+np.array((w,w,.14))
            lo[2]=max(.05,lo[2]);hi[2]=min(15.95,hi[2])
            out.append(box(lo,hi,ink,uv,max(0,ink-2)))


def glass(grid,uv,frame,mode):
    out=[]
    # XY painted relief wraps over a low convex shell. Separate front/back and
    # cobalt side faces retain depth in real first/third person viewpoints.
    for row in range(SIZE):
        start=0
        while start<SIZE:
            ink=int(grid[row,start])-1;end=start+1
            while end<SIZE and int(grid[row,end])-1==ink:end+=1
            if ink>=0:
                x0=start/4;x1=end/4;y0=16-(row+1)/4;y1=16-row/4
                u=(x0+x1)/2-8
                z=8+max(0,1-(u/8)**2)*1.7
                depth=.25 if mode=='ward_mote' else .48+(ink==0)*.18
                out.append(box((x0,y0,z-depth/2),(x1,y1,z+depth/2),ink,uv,max(0,ink-1)))
            start=end
    return out


def geometry(clip,frame,uv):
    frames=18 if clip=='thunder_hit' else 28 if clip in ('discharge','rupture') else 30 if clip in ('ward','ward_mote') else 24
    if frame>=frames-1:return []
    t=frame/(frames-1)
    out=[]
    if clip=='arcane_forks':
        # Unequal branches at three depths stay on the accepted ray. Their
        # short lateral shapes remain readable when the spine is end-on.
        for i,(z,side,reach) in enumerate(((2.7,-1,2.5),(6.4,1,3.1),(11.2,-1,1.9))):
            age=frame-i*1.4
            if age<0 or age>10:continue
            f=1-age/11
            path=[(8,8,z),(8+side*reach*.42,8.9,z+.4),
                  (8+side*reach*.68,8.2,z+.8),(8+side*reach,9.5,z+1.3)]
            ribbon(out,path,.20*f,4 if age<2 else 3 if age<5 else 2,uv)
        return out
    if clip in ('conductor','discharge'):
        # One decisive spine and two short forks, not three equal woven strings.
        path=[(8,8,1),(8.6,8.2,3),(7.0,8.5,4.5),(9.3,8.1,7),
              (8.0,7.8,8.5),(9.0,8.4,11),(7.7,8.1,13),(8,8,15)]
        if frame>9:
            path=path[min(6,(frame-9)//2):]
        width=(.34 if clip=='conductor' else .58)*max(.08,1-t)
        # Distinct layers: dark violet outer cut, lavender body, narrow white
        # conductor. Centre stays legible instead of switching the whole wire
        # between a few flat colours as time passes.
        ribbon(out,path,width,1,uv)
        ribbon(out,[np.asarray(p)+[0,.02,-.025] for p in path],width*.58,2,uv)
        ribbon(out,[np.asarray(p)+[0,.04,-.05] for p in path],width*.19,4,uv)
        if frame<13:
            for branch in ([(7,8.5,4.5),(5.2,9,5.4),(5.9,9.1,6.5),(4.8,9,8)],
                           [(9.3,8.1,7),(11,7.3,9),(10.1,7.1,10.5)]):
                ribbon(out,branch,width*.57,2 if frame>5 else 3,uv)
        return out
    if clip=='thunder_hit':
        for i,(angle,length) in enumerate(((.2,5.7),(1.8,3.9),(3.1,4.5),(4.8,3.2))):
            if frame>10+i:continue
            d=np.array((math.cos(angle),math.sin(angle),0.))
            n=np.array((-d[1],d[0],0.))
            c=np.array((8.,8.,8.))+d*(t*3)
            ribbon(out,[c,c+d*length*.36+n*.4,c+d*length*.65-n*.2,c+d*length],
                   .35*(1-t),4 if frame<2 else 3 if frame<6 else 1,uv)
        return out
    if clip=='rupture':
        # The core is a tearing discharge, not opaque square floor tiles.
        for i,(a,length) in enumerate(((.3,6.3),(1.8,4.3),(3.7,5.5),(5.1,3.5))):
            if frame>14+i*2:continue
            d=np.array((math.sin(a),0.,math.cos(a)));n=np.array((d[2],0.,-d[0]))
            c=np.array((8.,8.5,8.))+d*t*2
            path=[c,c+d*length*.25+n*.4+np.array((0,1.,0)),
                  c+d*length*.65-n*.25,c+d*length]
            ribbon(out,path,.23*(1-t),4 if frame<3 else 3 if frame<7 else 2,uv)
        return out
    g=np.zeros((SIZE,SIZE),dtype=np.uint8)
    fade=max(0.,1-max(0.,(t-.63)/.37))
    if clip in ('fold_in','fold_out'):
        opening=(1-t)**2 if clip=='fold_in' else 1-(1-t)**3
        pieces=[[(3,3),(5,2),(7,5),(6,6),(5,4)],[(10,3),(12,5),(11,9),(10,8),(11,5)],
                [(10,11),(11,14),(7,13),(6,11),(8,12)],[(4,8),(5,11),(3,10),(2,7)]]
        for i,points in enumerate(pieces):
            pts=np.asarray(points,dtype=float)
            pts=8+(pts-8)*(.18+.82*opening)
            if frame>12+i*2:continue
            polygon(g,pts,2 if i%2 else 3)
            # One narrow hot edge on each independently moving broken lip.
            polygon(g,[pts[0],pts[1],pts[1]*.78+pts[-1]*.22,pts[0]*.80+pts[-1]*.20],5)
    elif clip in ('ward','ward_mote'):
        # Broken protective shell: no complete shield silhouette, emblem or
        # broad filled wall. Large negative spaces keep combat readable.
        pieces=[[(4,2.8),(8,1.9),(11.5,3.5),(10.5,5.5),(7.8,3.4),(4.3,4.0)],
                [(11.8,6.3),(13.2,7.8),(11.2,11.6),(9.8,10.5),(11.6,8.4)],
                [(4,8.2),(5.7,11.5),(8.3,13.4),(7.3,14.2),(3.3,11.2)]]
        for i,points in enumerate(pieces):
            pts=np.asarray(points,dtype=float)
            pts[:,1]+=.25*math.sin(frame*.13+i)
            polygon(g,pts,2 if i!=1 else 3)
            polygon(g,[pts[0],pts[1],pts[1]*.8+pts[-1]*.2,pts[0]*.7+pts[-1]*.3],5)
        if clip=='ward_mote':
            g[:]=0
            polygon(g,[(11.3,4.8),(11.5,5),(12.0,6.4),(11.8,6.2)],4)
            polygon(g,[(5.9,12.0),(6.5,12.9),(6.7,12.8),(6.1,11.8)],4)
    else:
        # A compact open shard gathering at the hand. Never a five-item orbit.
        polygon(g,[(4.2,11),(5.7,5),(8.3,2.3),(11.6,6.7),(9.8,12.6),(8.0,9.4),(9.3,6.2),(8.0,4.5),(6.9,7)],3)
        polygon(g,[(5.7,5),(8.3,2.3),(9.5,4.1),(8,3.5),(6.6,6.4)],5)
    if fade<1:
        for row in range(SIZE):
            for col in range(SIZE):
                if (row+col//3)%19<int((1-fade)*16):g[row,col]=0
    return glass(g,uv,frame,clip)


def mesh(clip,frame,uv):
    elements=geometry(clip,frame,uv)
    return painted(elements,'y',5) if clip in PAINTED_ARCANE else elements
